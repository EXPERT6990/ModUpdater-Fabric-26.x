// MODUPDATER MIXIN
package com.theexpert9.modupdater.mixin;

import com.theexpert9.modupdater.gui.CustomUpdateScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public class PauseScreenMixin extends Screen {
    
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        Button referenceButton = null;

        // 1. SAFELY find the button (Accounts for Multiplayer "Disconnect" buttons)
        for (var widget : this.children()) {
            if (widget instanceof Button button) {
                String text = button.getMessage().getString();
                if (text.equals("Mods") || text.equals("Save and Quit to Title") || text.equals("Disconnect")) {
                    referenceButton = button;
                    // If we find Mod Menu's "Mods" button, prioritize it and stop searching!
                    if (text.equals("Mods")) break;
                }
            }
        }

        // 2. PREVENT CRASH: If no known button is found, safely abort instead of crashing.
        if (referenceButton == null) return;

        class UpdaterButton extends Button {
            // Sprites for the standard vanilla button states
            private static final Identifier BUTTON_NORMAL = Identifier.tryBuild("minecraft", "widget/button");
            private static final Identifier BUTTON_HOVERED = Identifier.tryBuild("minecraft", "widget/button_highlighted");
            private static final Identifier BUTTON_DISABLED = Identifier.tryBuild("minecraft", "widget/button_disabled");
            
            // Your custom mod updater icon
           private final Identifier icon = Identifier.tryBuild("modupdater", "widget/updater_button");

            public UpdaterButton(int x, int y, int width, int height, Component message, OnPress onPress) {
                super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            }

            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                // 1. DETERMINE STATE & CHOOSE VANILLA SPRITE
                Identifier backgroundSprite = BUTTON_NORMAL;
                if (!this.active) {
                    backgroundSprite = BUTTON_DISABLED;
                } else if (this.isHoveredOrFocused()) {
                    backgroundSprite = BUTTON_HOVERED;
                }

                // 2. DRAW VANILLA BACKGROUND MANUALLY (Replaces the broken super call)
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, backgroundSprite, this.getX(), this.getY(), this.getWidth(), this.getHeight(), -1);

                // 3. DRAW ICON ON TOP
                if (icon != null) {
                    int iconSize = 18; // Perfectly sized to fit inside a 20x20 box
                    int iconX = this.getX() + (this.getWidth() - iconSize) / 2;
                    int iconY = this.getY() + (this.getHeight() - iconSize) / 2;
                    
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, iconSize, iconSize, -1);
                }
            }
        }

        // 5. Initialize the button with dynamic positioning
        Button updaterButton = new UpdaterButton(
                referenceButton.getX() + referenceButton.getWidth() + 4, // 4 pixels of padding
                referenceButton.getY(), 
                20, 20, 
                Component.literal("Mod Updater"), 
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new CustomUpdateScreen(this));
                    }
                }
        );

        updaterButton.setTooltip(Tooltip.create(Component.literal("Check for Mod Updates")));
        
        // 6. Only add it ONCE!
        this.addRenderableWidget(updaterButton);
    }
}

//MODUPDATER MIXIN
package com.theexpert9.modupdater.mixin;

import com.theexpert9.modupdater.gui.CustomUpdateScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        Button quitButton = null;

		for (var widget : this.children()) {
			if(widget instanceof Button button && button.getMessage().getString().equals("Mods")) {
				quitButton = button;
				break;
			}
			else if (widget instanceof Button button && button.getMessage().getString().equals("Quit Game")) {
				quitButton = button;
				break;
			}
    	}

        // 1. We create a clean Local Class that formally extends Button
        class UpdaterButton extends Button {
            private final Identifier icon = Identifier.tryBuild("modupdater", "widget/updater_button");
            private final Identifier iconHovered = Identifier.tryBuild("modupdater", "widget/updater_button_highlighted");

            public UpdaterButton(int x, int y, int width, int height, Component message, OnPress onPress) {
                // Because we extend Button, we safely have access to the protected DEFAULT_NARRATION
                super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            }

            // 2. We override extractContents instead of extractRenderState!
            // The superclass automatically draws the vanilla button background, then calls this method.
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                Identifier currentIcon = this.isHoveredOrFocused() ? iconHovered : icon;
                
                // 3. Draw our custom icon perfectly centered inside the vanilla button background
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, currentIcon, this.getX(), this.getY(), this.getWidth(), this.getHeight(), -1);
            }
        }

        // Initialize our new local class
        Button updaterButton = new UpdaterButton(quitButton.getX() + quitButton.getWidth() + 20 + 4, quitButton.getY(), 20, 20, Component.literal("Mod Updater"), button -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new CustomUpdateScreen(this));
            }
        });

        updaterButton.setTooltip(Tooltip.create(Component.literal("Check for Mod Updates")));
        
        this.addRenderableWidget(updaterButton);
    }
}
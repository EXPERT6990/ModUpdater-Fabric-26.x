package com.theexpert9.modupdater.mixin;

import com.theexpert9.modupdater.gui.CustomUpdateScreen;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier; // Swapped to Yarn mapping
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    // Swapped to Identifier.tryBuild()
    private static final WidgetSprites UPDATER_BUTTON_SPRITES = new WidgetSprites(
            Identifier.tryBuild("modupdater", "widget/updater_button"),
            Identifier.tryBuild("modupdater", "widget/updater_button_highlighted")
    );

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        int buttonWidth = 20;
        int buttonHeight = 20;
        int x = this.width / 2 + 104 * 2; 
        int y = this.height / 4 + 48 + 72 + 12;

        ImageButton updaterButton = new ImageButton(
                x, y, buttonWidth, buttonHeight,
                UPDATER_BUTTON_SPRITES,
                (button) -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new CustomUpdateScreen(this));
                    }
                },
                Component.literal("Mod Updater")
        );

        updaterButton.setTooltip(Tooltip.create(Component.literal("Check for Mod Updates")));
        
        this.addRenderableWidget(updaterButton);
    }
}
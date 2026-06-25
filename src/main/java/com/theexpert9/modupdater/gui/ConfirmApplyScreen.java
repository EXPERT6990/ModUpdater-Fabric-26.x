package com.theexpert9.modupdater.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ConfirmApplyScreen extends Screen {
    private final Screen parentScreen;
    private final List<String> pendingMods;
    private final Runnable onConfirm;

    public ConfirmApplyScreen(Screen parentScreen, List<String> pendingMods, Runnable onConfirm) {
        super(Component.literal("Confirm Apply Updates"));
        this.parentScreen = parentScreen;
        this.pendingMods = pendingMods;
        this.onConfirm = onConfirm; // The restart method passed from the main screen
    }

    @Override
    protected void init() {
        super.init();

        this.addRenderableWidget(Button.builder(Component.literal("Restart Game Now"), button -> {
            this.onConfirm.run();
        }).bounds(this.width / 2 - 105, this.height - 40, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
        }).bounds(this.width / 2 + 5, this.height - 40, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Draw a dark background panel
        int panelWidth = 300;
        int panelHeight = this.height - 80;
        int panelX = (this.width - panelWidth) / 2;
        graphics.fill(panelX, 20, panelX + panelWidth, 20 + panelHeight, 0xDD000000);

        graphics.centeredText(this.font, Component.literal("§lReady to Apply Updates"), this.width / 2, 35, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal("The following files are downloaded and ready:"), this.width / 2, 55, 0xFFAAAAAA);
        
        int y = 75;
        for (String mod : this.pendingMods) {
            graphics.centeredText(this.font, Component.literal("§a+ " + mod), this.width / 2, y, 0xFF55FF55);
            y += 15;
            if (y > this.height - 70) {
                graphics.centeredText(this.font, Component.literal("...and more"), this.width / 2, y, 0xFFAAAAAA);
                break;
            }
        }
    }
}
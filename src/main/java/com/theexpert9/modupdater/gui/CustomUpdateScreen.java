package com.theexpert9.modupdater.gui;

import net.minecraft.client.gui.render.GuiRenderer; // The brand new renderer!
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CustomUpdateScreen extends Screen {
    private final Screen parentScreen;

    public CustomUpdateScreen(Screen parent) {
        super(Component.literal("Mod Updater"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        this.addRenderableWidget(Button.builder(Component.literal("Check for Updates"), button -> {
            System.out.println("Starting high-speed backend hash check...");
            // Trigger your ModrinthClient backend here
        })
        .bounds(this.width / 2 - 100, this.height - 40, 200, 20)
        .build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
        })
        .bounds(10, 10, 50, 20)
        .build());
    }

    @Override
    public void render(GuiRenderer renderer, int mouseX, int mouseY, float partialTick) {
        // 1. Draw the background
        super.render(renderer, mouseX, mouseY, partialTick);

        // 2. Draw a modern, semi-transparent black panel in the center for our mod list
        int panelX = 40;
        int panelY = 40;
        int panelWidth = this.width - 80;
        int panelHeight = this.height - 90;
        
        // Fill a rectangle (X1, Y1, X2, Y2, Color in ARGB format)
        renderer.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x99000000);

        // 3. Draw a custom title with a shadow
        renderer.drawString(this.font, Component.literal("Available Updates"), panelX + 10, panelY + 10, 0xFFFFFF, true);

        // 4. Placeholder text for our updates
        renderer.drawString(this.font, Component.literal("Sodium: 0.8.12 -> 0.8.19"), panelX + 10, panelY + 30, 0xAAAAAA, false);
    }
}
// package com.theexpert9.modupdater.gui;

// import net.minecraft.client.gui.GuiGraphicsExtractor; // The new rendering class
// import net.minecraft.client.gui.components.Button;
// import net.minecraft.client.gui.screens.Screen;
// import net.minecraft.network.chat.Component;

// public class CustomUpdateScreen extends Screen {
//     private final Screen parentScreen;

//     public CustomUpdateScreen(Screen parent) {
//         super(Component.literal("Mod Updater"));
//         this.parentScreen = parent;
//     }

//     @Override
//     protected void init() {
//         super.init();

//         // Check for updates button
//         this.addRenderableWidget(Button.builder(Component.literal("Check for Updates"), button -> {
//             System.out.println("Starting high-speed backend hash check...");
//             // We will connect your ModrinthClient here later!
//         })
//         .bounds(this.width / 2 - 100, this.height - 40, 200, 20)
//         .build());

//         // Back button
//         this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
//             if (this.minecraft != null) {
//                 this.minecraft.setScreen(this.parentScreen);
//             }
//         })
//         .bounds(10, 10, 50, 20)
//         .build());
//     }

//     // Replaces the old render() method
//     @Override
//     public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
//         // 1. Draw the background state
//         super.extractRenderState(graphics, mouseX, mouseY, partialTick);

//         // 2. Draw a modern, semi-transparent black panel in the center for our mod list
//         int panelX = 40;
//         int panelY = 40;
//         int panelWidth = this.width - 80;
//         int panelHeight = this.height - 90;
        
//         // Fill a rectangle (X1, Y1, X2, Y2, Color in ARGB format)
//         graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x99000000);

//         // 3. Draw a custom title with a shadow
//         // Using the exact text() signature you found in the source code
//         graphics.text(this.font, Component.literal("Available Updates"), panelX + 10, panelY + 10, 0xFFFFFF, true);

//         // 4. Placeholder text for our updates (no shadow for list items looks cleaner)
//         graphics.text(this.font, Component.literal("Sodium: 0.8.12 -> 0.8.19"), panelX + 10, panelY + 30, 0xAAAAAA);
//     }
// }


package com.theexpert9.modupdater.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CustomUpdateScreen extends Screen {
    private final Screen parentScreen;
    private UpdateListWidget listWidget;

    public CustomUpdateScreen(Screen parent) {
        super(Component.literal("Mod Updater"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        int panelX = 40;
        int panelY = 40;
        int panelWidth = this.width - 80;
        int panelHeight = this.height - 90;

        // 1. Initialize the scrollable list
        // itemHeight is set to 26 pixels so the text and checkbox fit nicely
        this.listWidget = new UpdateListWidget(this.minecraft, panelWidth, panelHeight - 30, panelY + 25, 26);
        this.listWidget.setX(panelX); // Align to our custom panel
        this.addRenderableWidget(this.listWidget); // Register it with the screen

        // 2. Main Action Button (Injecting dummy data for UI testing)
        this.addRenderableWidget(Button.builder(Component.literal("Test Fake Data"), button -> {
            // Test how scrolling feels by injecting fake mods
            this.listWidget.addUpdate("Sodium", "0.8.12", "0.8.19");
            this.listWidget.addUpdate("Iris", "1.6.4", "1.6.8");
            this.listWidget.addUpdate("ModMenu", "9.0.0", "10.0.0");
            this.listWidget.addUpdate("Fabric API", "0.91.0", "0.92.0");
            this.listWidget.addUpdate("Cloth Config", "12.0", "13.1");
        })
        .bounds(this.width / 2 - 100, this.height - 40, 200, 20)
        .build());

        // 3. Back button
        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
        })
        .bounds(10, 10, 50, 20)
        .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Draw the main dirt background
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int panelX = 40;
        int panelY = 40;
        int panelWidth = this.width - 80;
        int panelHeight = this.height - 90;
        
        // Fill the semi-transparent black panel
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x99000000);

        // Draw the title
        graphics.text(this.font, Component.literal("Available Updates"), panelX + 10, panelY + 10, 0xFFFFFF, true);

        // Note: We don't need to manually tell the listWidget to render! 
        // Because we used addRenderableWidget() in init(), Minecraft draws the list over the panel automatically.
    }
}
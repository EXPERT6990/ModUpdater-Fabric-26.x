package com.theexpert9.modupdater.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

public class UpdateListEntry extends ObjectSelectionList.Entry<UpdateListEntry> {
    public final String modName;
    public final String updateText;
    public boolean selected = true;
    private final UpdateListWidget parent;

    public UpdateListEntry(UpdateListWidget parent, String modName, String oldVer, String newVer) {
        this.parent = parent;
        this.modName = modName;
        this.updateText = oldVer + " ➔ " + newVer;
    }

    // The signature for rendering a specific row inside the list
    @Override
    public void render(GuiGraphicsExtractor graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
        // 1. Draw Custom Checkbox Background
        int checkboxX = left + 5;
        int checkboxY = top + 5;
        graphics.fill(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF444444); // Dark Gray

        // 2. Draw the "Checked" state (A smaller green box inside)
        if (selected) {
            graphics.fill(checkboxX + 2, checkboxY + 2, checkboxX + 10, checkboxY + 10, 0xFF00FF00); // Green
        }

        // 3. Draw Mod Name and Version Text
        Minecraft client = Minecraft.getInstance();
        graphics.text(client.font, Component.literal(this.modName), left + 25, top + 2, 0xFFFFFF, false);
        graphics.text(client.font, Component.literal(this.updateText), left + 25, top + 13, 0xAAAAAA, false);
    }

    // Toggle the checkbox when the user clicks this specific row
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.selected = !this.selected;
        return true;
    }
}
package com.theexpert9.modupdater.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;

public class UpdateListWidget extends ObjectSelectionList<UpdateListEntry> {
    
    public UpdateListWidget(Minecraft client, int width, int height, int y0, int itemHeight) {
        super(client, width, height, y0, itemHeight);
    }

    // A helper method so our screen can easily add updates to this list
    public void addUpdate(String modName, String oldVer, String newVer) {
        this.addEntry(new UpdateListEntry(this, modName, oldVer, newVer));
    }

    // Locks the scrollbar strictly to the right edge of our custom panel
    @Override
    protected int scrollBarX() {
        return this.getX() + this.width - 6;
    }

    @Override
    public int getRowWidth() {
        return this.width - 18; // Keep items from overlapping the scrollbar
    }
}
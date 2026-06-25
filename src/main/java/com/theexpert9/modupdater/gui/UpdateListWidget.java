package com.theexpert9.modupdater.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import java.util.List;

public class UpdateListWidget extends ObjectSelectionList<UpdateListEntry> {
    
    public UpdateListWidget(Minecraft client, int width, int height, int y0, int itemHeight) {
        super(client, width, height, y0, itemHeight);
    }

    public void addRealUpdate(String projectId, String modName, /*String author, String description, String changelog, */ String oldFile, String newFile, String url, String oldVer, String newVer, boolean isDownloaded) {
        UpdateListEntry entry = new UpdateListEntry(this, projectId, modName,/*  author, description, changelog,*/ oldFile, newFile, url, oldVer, newVer);
        entry.isDownloaded = isDownloaded;
        if (isDownloaded) {
            entry.selected = false; // Force deselect so it isn't in the download queue
        }
        this.addEntry(entry);
    }


    // Helper for the Select All / None buttons
    public void setAllSelected(boolean state) {
        for (UpdateListEntry entry : this.children()) {
            entry.selected = state;
        }
    }

    // Gets only the mods that have a green checkmark
    public List<UpdateListEntry> getCheckedEntries() {
        return this.children().stream().filter(e -> e.selected).toList();
    }

    @Override
    protected int scrollBarX() {
        return this.getX() + this.width - 6;
    }

    @Override
    public int getRowWidth() {
        return this.width - 18; 
    }
}
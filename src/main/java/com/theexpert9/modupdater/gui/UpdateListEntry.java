
package com.theexpert9.modupdater.gui;

import com.theexpert9.modupdater.util.DownloadManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;

public class UpdateListEntry extends ObjectSelectionList.Entry<UpdateListEntry> {
    public boolean isDownloaded = false;

    public final String projectId;
    public final String modName;
    public final String oldFilename;
    public final String newFilename;
    public final String downloadUrl;
    public final String oldVer;
    public final String newVer;
    
    public boolean selected = true; 
    private final UpdateListWidget parent;

    public UpdateListEntry(UpdateListWidget parent, String projectId, String modName, String oldFilename, String newFilename, String downloadUrl, String oldVer, String newVer) {
        this.parent = parent;
        this.projectId = projectId;
        this.modName = modName;
        this.oldFilename = oldFilename;
        this.newFilename = newFilename;
        this.downloadUrl = downloadUrl;
        this.oldVer = oldVer;
        this.newVer = newVer;
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean isHovered, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        // 3. Draw Checkbox OR Downloaded Status
        int checkboxX = x + this.parent.getRowWidth() - 20;
        int checkboxY = y + 5;

        int currentState = neelesh.easy_install.util.GlobalDownloadTracker.getState(this.projectId);
        Minecraft client = Minecraft.getInstance();

        // 1. ALWAYS draw the Mod Name at the very top of the slot
        graphics.text(client.font, Component.literal("§f§l" + this.modName), x + 25, y + 2, 0xFFFFFFFF, false);

        // 2. ALWAYS draw the Version Transition underneath the Mod Name so users can track the updates
        Component versionDisplay = Component.literal("§c§l" + this.oldVer + " §f§l➔ §a§l" + this.newVer);
        graphics.text(client.font, versionDisplay, x + 25, y + 13, 0xFFFFFFFF, false);

        // 3. CONDITIONALLY handle what to draw in the Checkbox column space
        if (currentState == 1) {
            // STATE: Actively Downloading -> Replace checkbox area with current live progress percentage
            float currentProgress = neelesh.easy_install.util.GlobalDownloadTracker.getProgress(this.projectId) * 100.0f;
            graphics.text(client.font, Component.literal(String.format("§e§l%d%%", (int) currentProgress)), checkboxX - 5, checkboxY + 2, 0xFFFFFFFF, false);
            
        } else if (this.isDownloaded || (Files.exists(DownloadManager.getPendingUpdatesDir().resolve(this.newFilename)) && Files.isRegularFile(DownloadManager.getPendingUpdatesDir().resolve(this.newFilename))) || currentState == 2) {
            // STATE: Completed/Queued -> Replace checkbox area with bold green QUEUED label!
            graphics.text(client.font, Component.literal("§a§lQUEUED"), checkboxX - 35, checkboxY + 2, 0xFFFFFFFF, false);
            
        } else {
            // STATE: Idle -> Draw the standard clickable interactive check box
            graphics.fill(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF555555);
            graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + 11, checkboxY + 11, 0xFF1A1A1A);

            if (selected) {
                // Draw internal green filled slot marker if row is selected
                graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + 9, checkboxY + 9, 0xFF00FF00); 
            }
        }

        // int currentState = neelesh.easy_install.util.GlobalDownloadTracker.getState(this.projectId);

        // // 3. Draw Mod Name (Bold Bright White) and Version Transitions (Red -> Green)
        // Minecraft client = Minecraft.getInstance();
        // if (this.isDownloaded || currentState == 2) {
        //     // Draw a strike-through style or text indicating it's done
        //     graphics.text(client.font, Component.literal("§a§lQUEUED"), checkboxX - 35, checkboxY, 0xFFFFFFFF, false);
        // } else if (currentState == 1) {
        //     // 1. If currently installing, override version display with real-time percentage
        //     float currentProgress = neelesh.easy_install.util.GlobalDownloadTracker.getProgress(this.projectId)
        //             * 100.0f;
        //     graphics.text(client.font,
        //             Component.literal(String.format("§e§l(%d%%)", (int) currentProgress)), checkboxX - 35,
        //             checkboxY, 0xFFFFFFFF, false);
        // } else {
        //     graphics.fill(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF555555);
        //     graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + 11, checkboxY + 11, 0xFF1A1A1A);

        //     if (selected) {
        //         graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + 9, checkboxY + 9, 0xFF00FF00); 
        //     }
        // }
        // // §f = White, §l = Bold
        // graphics.text(client.font, Component.literal("§f§l" + this.modName), x + 25, y + 2, 0xFFFFFFFF, false);
        
        // // §c = Red, §f = White, §a = Green, §l = Bold
        // Component versionDisplay;
        // versionDisplay = Component.literal("§c§l" + this.oldVer + " §f§l➔ §a§l" + this.newVer);
        // graphics.text(client.font, versionDisplay, x + 25, y + 13, 0xFFFFFFFF, false);
}


    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.parent.setSelected(this);
        
        int checkboxX = this.getX() + this.parent.getRowWidth() - 20;
        if (event.x() >= checkboxX && event.x() <= checkboxX + 12) {
            if (!this.isDownloaded) { // ONLY toggle if not already downloaded!
                this.selected = !this.selected;
            }
        }
        return true;
    }

    @Override
    public Component getNarration() {
        return Component.literal("Update available for " + this.modName);
    }
}
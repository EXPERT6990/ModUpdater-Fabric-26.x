
package com.theexpert9.modupdater.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

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

        // 1. Draw Upgraded Solid Checkbox with a Crisp Outer Border Accent
        // int checkboxX = x + 5;
        // int checkboxY = y + 5;
        // graphics.fill(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF555555); // Outer border shell
        // graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + 11, checkboxY + 11, 0xFF1A1A1A); // Dark solid inner container

        // // 2. Draw the "Checked" inner solid core
        // if (selected) {
        //     graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + 9, checkboxY + 9, 0xFF00FF00); // High-contrast solid green core
        // }

        // 3. Draw Checkbox OR Downloaded Status
        int checkboxX = x + this.parent.getRowWidth() - 20;
        int checkboxY = y + 5;
        
        // 3. Draw Mod Name (Bold Bright White) and Version Transitions (Red -> Green)
        Minecraft client = Minecraft.getInstance();

        if (this.isDownloaded) {
            // Draw a strike-through style or text indicating it's done
            graphics.text(client.font, Component.literal("§a§lQUEUED"), checkboxX - 35, checkboxY, 0xFFFFFFFF, false);
        } else {
            graphics.fill(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF555555);
            graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + 11, checkboxY + 11, 0xFF1A1A1A);

            if (selected) {
                graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + 9, checkboxY + 9, 0xFF00FF00); 
            }
        }

        
        
        // §f = White, §l = Bold
        graphics.text(client.font, Component.literal("§f§l" + this.modName), x + 25, y + 2, 0xFFFFFFFF, false);
        
        // §c = Red, §f = White, §a = Green, §l = Bold
        String versionFormatting = "§c§l" + this.oldVer + " §f§l➔ §a§l" + this.newVer;
        graphics.text(client.font, Component.literal(versionFormatting), x + 25, y + 13, 0xFFFFFFFF, false);
    }

    // @Override
    // public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    //     this.parent.setSelected(this);
        
    //     if (event.x() - this.getX() < 20) {
    //         this.selected = !this.selected;
    //     }
    //     return true;
    // }

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
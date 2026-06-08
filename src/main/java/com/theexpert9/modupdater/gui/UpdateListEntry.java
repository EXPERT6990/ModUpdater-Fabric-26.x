// package com.theexpert9.modupdater.gui;

// import net.minecraft.client.Minecraft;
// import net.minecraft.client.gui.GuiGraphicsExtractor;
// import net.minecraft.client.gui.components.ObjectSelectionList;
// import net.minecraft.client.input.MouseButtonEvent;
// import net.minecraft.network.chat.Component;

// public class UpdateListEntry extends ObjectSelectionList.Entry<UpdateListEntry> {
//     public final String projectId;
//     public final String modName;
//     public final String oldFilename;
//     public final String newFilename;
//     public final String downloadUrl;
//     public final String oldVer;
//     public final String newVer;
    
//     public boolean selected = true; 
//     private final UpdateListWidget parent;

//     public UpdateListEntry(UpdateListWidget parent, String projectId, String modName, String oldFilename, String newFilename, String downloadUrl, String oldVer, String newVer) {
//         this.parent = parent;
//         this.projectId = projectId;
//         this.modName = modName;
//         this.oldFilename = oldFilename;
//         this.newFilename = newFilename;
//         this.downloadUrl = downloadUrl;
//         this.oldVer = oldVer;
//         this.newVer = newVer;
//     }

//     @Override
//     public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean isHovered, float partialTick) {
//         int x = this.getX();
//         int y = this.getY();

//         // 1. Draw Upgraded Solid Checkbox with a Crisp Outer Border Accent
//         int checkboxX = x + 5;
//         int checkboxY = y + 5;
//         graphics.fill(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF555555); // Outer border shell
//         graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + 11, checkboxY + 11, 0xFF1A1A1A); // Dark solid inner container

//         // 2. Draw the "Checked" inner solid core
//         if (selected) {
//             graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + 9, checkboxY + 9, 0xFF00FF00); // High-contrast solid green core
//         }

//         // 3. Draw Mod Name (Bold Bright White) and Version Transitions (Red -> Green)
//         Minecraft client = Minecraft.getInstance();
        
//         // §f = White, §l = Bold
//         graphics.text(client.font, Component.literal("§f§l" + this.modName), x + 25, y + 2, 0xFFFFFFFF, false);
        
//         // §c = Red, §f = White, §a = Green, §l = Bold
//         String versionFormatting = "§c§l" + this.oldVer + " §f§l➔ §a§l" + this.newVer;
//         graphics.text(client.font, Component.literal(versionFormatting), x + 25, y + 13, 0xFFFFFFFF, false);
//     }

//     @Override
//     public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
//         this.parent.setSelected(this);
        
//         if (event.x() - this.getX() < 20) {
//             this.selected = !this.selected;
//         }
//         return true;
//     }

//     @Override
//     public Component getNarration() {
//         return Component.literal("Update available for " + this.modName);
//     }
// }


package com.theexpert9.modupdater.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class UpdateListEntry extends ObjectSelectionList.Entry<UpdateListEntry> {
    public final String projectId;
    public final String modName;
    public final String author;
    public final String description;
    public final String changelog;
    public final String oldFilename;
    public final String newFilename;
    public final String downloadUrl;
    public final String oldVer;
    public final String newVer;
    
    public boolean selected = true; 
    private final UpdateListWidget parent;

    // A sleek vanilla placeholder icon until dynamic JAR extraction is implemented
    private static final Identifier DEFAULT_ICON = Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");

    public UpdateListEntry(UpdateListWidget parent, String projectId, String modName, String author, String description, String changelog, String oldFilename, String newFilename, String downloadUrl, String oldVer, String newVer) {
        this.parent = parent;
        this.projectId = projectId;
        this.modName = modName;
        this.author = author;
        this.description = description;
        this.changelog = changelog;
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
        Minecraft client = Minecraft.getInstance();

        // 1. Draw Mod Icon (Left aligned)
        graphics.blit(DEFAULT_ICON, x + 2, y + 2, 0, 0, 22, 22, 22, 22);

        // 2. Draw Mod Name and Clean Versions
        graphics.text(client.font, Component.literal("§f§l" + this.modName), x + 30, y + 2, 0xFFFFFFFF, false);
        String versionFormatting = "§c§l" + this.oldVer + " §f§l➔ §a§l" + this.newVer;
        graphics.text(client.font, Component.literal(versionFormatting), x + 30, y + 14, 0xFFFFFFFF, false);

        // 3. Draw Checkbox (Anchored to the far right!)
        int checkboxX = x + this.parent.getRowWidth() - 20;
        int checkboxY = y + 5;
        
        graphics.fill(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF555555);
        graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + 11, checkboxY + 11, 0xFF1A1A1A);

        if (selected) {
            graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + 9, checkboxY + 9, 0xFF00FF00); 
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.parent.setSelected(this);
        
        // Update click detection to the far right side
        int checkboxX = this.getX() + this.parent.getRowWidth() - 20;
        if (event.x() >= checkboxX && event.x() <= checkboxX + 12) {
            this.selected = !this.selected;
        }
        return true;
    }

    @Override
    public Component getNarration() {
        return Component.literal("Update available for " + this.modName);
    }
}
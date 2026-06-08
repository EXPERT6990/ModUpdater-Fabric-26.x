// package com.theexpert9.modupdater.gui;

// import com.theexpert9.modupdater.api.ModrinthClient;
// import com.theexpert9.modupdater.util.ConfigManager;
// import com.theexpert9.modupdater.util.DownloadManager;
// import com.theexpert9.modupdater.util.StatusWriter;
// import net.fabricmc.loader.api.FabricLoader;
// import net.minecraft.client.gui.GuiGraphicsExtractor;
// import net.minecraft.client.gui.components.Button;
// import net.minecraft.client.gui.screens.Screen;
// import net.minecraft.network.chat.Component;

// import java.io.InputStream;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.StandardCopyOption;
// import java.security.MessageDigest;
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.atomic.AtomicInteger;
// import java.util.stream.Stream;

// public class CustomUpdateScreen extends Screen {
//     private final Screen parentScreen;
//     private UpdateListWidget listWidget;
    
//     private boolean isScanning = true;
//     private boolean isDownloading = false;
//     private boolean readyToApply = false;
//     private String statusMessage = "Scanning hashes in background... Please wait.";
//     private Button applyButton;

//     public CustomUpdateScreen(Screen parent) {
//         super(Component.literal("Mod Updater"));
//         this.parentScreen = parent;
//     }

//     @Override
//     protected void init() {
//         super.init();

//         int panelX = 40;
//         int panelY = 40;
//         int panelWidth = this.width - 80;
//         int panelHeight = this.height - 90;
//         int listWidth = (int) (panelWidth * 0.60);

//         this.listWidget = new UpdateListWidget(this.minecraft, listWidth, panelHeight - 30, panelY + 25, 26);
//         this.listWidget.setX(panelX);
//         this.addRenderableWidget(this.listWidget);

//         int buttonY = panelY + panelHeight + 5;

//         this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
//             if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
//         }).bounds(10, 10, 50, 20).build());

//         this.addRenderableWidget(Button.builder(Component.literal("Select All"), button -> {
//             this.listWidget.setAllSelected(true);
//         }).bounds(panelX, buttonY, 75, 20).build());

//         this.addRenderableWidget(Button.builder(Component.literal("Deselect All"), button -> {
//             this.listWidget.setAllSelected(false);
//         }).bounds(panelX + 80, buttonY, 80, 20).build());

//         // Download Button
//         this.addRenderableWidget(Button.builder(Component.literal("Download Selected"), button -> {
//             if (!this.isScanning && !this.isDownloading && !this.readyToApply) startDownload();
//         }).bounds(panelX + panelWidth - 235, buttonY, 120, 20).build());

//         // 1. Brand New Independent Manual Apply Changes Button
//         this.applyButton = Button.builder(Component.literal("Apply Changes"), button -> {
//             if (this.readyToApply) applyAndRestart();
//         }).bounds(panelX + panelWidth - 110, buttonY, 110, 20).build();
        
//         this.applyButton.active = false; // Kept disabled until data downloads successfully
//         this.addRenderableWidget(this.applyButton);

//         runBackendScan();
//     }

//     private void runBackendScan() {
//         // (Unchanged Hash Scanning and Bulk API lookup logic stays completely intact)
//         CompletableFuture.runAsync(() -> {
//             Map<String, String> hashToFilename = new HashMap<>();
//             ConfigManager.ConfigData config = ConfigManager.getConfig();

//             try (Stream<Path> stream = Files.list(FabricLoader.getInstance().getGameDir().resolve("mods"))) {
//                 stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
//                     String filename = path.getFileName().toString();
//                     if (!filename.startsWith("fabric-") && !filename.equals("updater.jar")) {
//                         if (config.autoCheckMode == ConfigManager.AutoCheckMode.ALL || 
//                            (config.autoCheckMode == ConfigManager.AutoCheckMode.MANUAL && !config.ignoredMods.contains(filename))) {
//                             try { hashToFilename.put(getFileHash(path), filename); } catch (Exception ignored) {}
//                         }
//                     }
//                 });
//             } catch (Exception ignored) {}

//             if (hashToFilename.isEmpty()) {
//                 updateStatus("No monitored mods found.");
//                 return;
//             }

//             Map<String, ModrinthClient.ModVersion> apiResponse = ModrinthClient.checkBulkUpdates(new ArrayList<>(hashToFilename.keySet()), "26.1.2").join();
            
//             this.minecraft.execute(() -> {
//                 int count = 0;
//                 for (Map.Entry<String, ModrinthClient.ModVersion> entry : apiResponse.entrySet()) {
//                     String oldHash = entry.getKey();
//                     ModrinthClient.ModVersion newVer = entry.getValue();
//                     String oldFilename = hashToFilename.get(oldHash);

//                     if (newVer.files() == null || newVer.files().isEmpty()) continue;
//                     ModrinthClient.ModFile primaryFile = newVer.files().stream().filter(ModrinthClient.ModFile::primary).findFirst().orElse(newVer.files().get(0));
//                     String newHash = primaryFile.hashes().get("sha1");

//                     if (newHash != null && !oldHash.equals(newHash)) {
//                         count++;
//                         // Splitting and cleaning up versions into simple parameters
//                         String cleanOldVer = "v1.0.0"; 
//                         String cleanNewVer = newVer.version_number();
                        
//                         this.listWidget.addRealUpdate(newVer.project_id(), oldFilename.replace(".jar", ""), oldFilename, primaryFile.filename(), primaryFile.url(), cleanOldVer, cleanNewVer);
//                     }
//                 }
//                 updateStatus(count == 0 ? "All mods up to date!" : "Found " + count + " available updates.");
//             });
//         });
//     }

//     private void startDownload() {
//         List<UpdateListEntry> toDownload = this.listWidget.getCheckedEntries();
//         if (toDownload.isEmpty()) return;

//         this.isDownloading = true;
//         AtomicInteger completedCount = new AtomicInteger(0);
//         int total = toDownload.size();

//         for (UpdateListEntry update : toDownload) {
//             DownloadManager.downloadMod(update.downloadUrl, update.newFilename, (percent, speedMBps) -> {
//                 updateStatus(String.format("Downloading %s... %.0f%% (%.1f MB/s)", update.newFilename, percent, speedMBps));
//             }).thenAccept(path -> {
//                 StatusWriter.appendUpdate(update.oldFilename, update.newFilename);
//                 if (completedCount.incrementAndGet() >= total) {
//                     this.minecraft.execute(() -> {
//                         this.isDownloading = false;
//                         this.readyToApply = true;
//                         this.applyButton.active = true; // Activating button now!
//                         updateStatus("Downloads Complete! Click 'Apply Changes' to complete installation.");
//                     });
//                 }
//             });
//         }
//     }

//     private void applyAndRestart() {
//         try {
//             Path pendingDir = DownloadManager.getPendingUpdatesDir();
//             Path updaterPath = pendingDir.resolve("updater.jar");
//             try (InputStream is = CustomUpdateScreen.class.getResourceAsStream("/assets/modupdater/updater.jar")) {
//                 if (is != null) Files.copy(is, updaterPath, StandardCopyOption.REPLACE_EXISTING);
//                 else return;
//             }
//             long pid = ProcessHandle.current().pid();
//             String modsPath = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().toString();
//             String javaPath = ProcessHandle.current().info().command().orElse("java");
//             Runtime.getRuntime().exec(new String[]{javaPath, "-jar", updaterPath.toAbsolutePath().toString(), String.valueOf(pid), modsPath});
//             this.minecraft.stop();
//         } catch (Exception e) { e.printStackTrace(); }
//     }

//     private void updateStatus(String msg) {
//         if (this.minecraft != null) {
//             this.minecraft.execute(() -> {
//                 this.statusMessage = msg;
//                 this.isScanning = false;
//             });
//         }
//     }

//     private String getFileHash(Path path) throws Exception {
//         MessageDigest digest = MessageDigest.getInstance("SHA-1");
//         try (InputStream is = Files.newInputStream(path)) {
//             byte[] buffer = new byte[8192];
//             int read;
//             while ((read = is.read(buffer)) > 0) digest.update(buffer, 0, read);
//         }
//         StringBuilder hexString = new StringBuilder();
//         for (byte b : digest.digest()) hexString.append(String.format("%02x", b & 0xFF));
//         return hexString.toString();
//     }

//     @Override
//     public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
//         super.extractRenderState(graphics, mouseX, mouseY, partialTick);

//         int panelX = 40;
//         int panelY = 40;
//         int panelWidth = this.width - 80;
//         int panelHeight = this.height - 90;
//         int listWidth = (int) (panelWidth * 0.60);
//         int sidePanelX = panelX + listWidth;

//         graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x99000000);
//         graphics.fill(sidePanelX, panelY, sidePanelX + 1, panelY + panelHeight, 0x55FFFFFF);

//         graphics.text(this.font, Component.literal("Mod Updater"), panelX + 10, panelY + 10, 0xFFFFFFFF, true);
//         graphics.text(this.font, Component.literal(this.statusMessage), panelX + 100, panelY + 10, 0xFFFFAA00, false);

//         UpdateListEntry viewedEntry = this.listWidget.getSelected();
//         if (viewedEntry != null) {
//             graphics.text(this.font, Component.literal("Selected Mod Details"), sidePanelX + 10, panelY + 10, 0xFFFFAA00, false);
//             graphics.text(this.font, Component.literal("§f§l" + viewedEntry.modName), sidePanelX + 10, panelY + 30, 0xFFFFFFFF, false);
//             graphics.text(this.font, Component.literal("File: " + viewedEntry.newFilename), sidePanelX + 10, panelY + 45, 0xFFAAAAAA, false);
            
//             graphics.text(this.font, Component.literal("[ Mod Icon Area ]"), sidePanelX + 10, panelY + 80, 0xFF555555, false);
//             graphics.text(this.font, Component.literal("Changelog data coming soon..."), sidePanelX + 10, panelY + 100, 0xFF555555, false);
//         } else {
//             graphics.text(this.font, Component.literal("Select a mod to view details."), sidePanelX + 10, panelY + 10, 0xFFAAAAAA, false);
//         }
//     }
// }

package com.theexpert9.modupdater.gui;

import com.theexpert9.modupdater.api.ModrinthClient;
import com.theexpert9.modupdater.util.ConfigManager;
import com.theexpert9.modupdater.util.DownloadManager;
import com.theexpert9.modupdater.util.StatusWriter;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomUpdateScreen extends Screen {
    private final Screen parentScreen;
    private UpdateListWidget listWidget;
    
    private boolean isScanning = true;
    private boolean isDownloading = false;
    private boolean readyToApply = false;
    private String statusMessage = "Scanning hashes in background... Please wait.";
    private Button applyButton;

    private static final Identifier DEFAULT_ICON = Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");

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
        int listWidth = (int) (panelWidth * 0.60);

        this.listWidget = new UpdateListWidget(this.minecraft, listWidth, panelHeight - 30, panelY + 25, 28); // Slightly taller items
        this.listWidget.setX(panelX);
        this.addRenderableWidget(this.listWidget);

        int buttonY = panelY + panelHeight + 5;

        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
        }).bounds(10, 10, 50, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Select All"), button -> {
            this.listWidget.setAllSelected(true);
        }).bounds(panelX, buttonY, 75, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Deselect All"), button -> {
            this.listWidget.setAllSelected(false);
        }).bounds(panelX + 80, buttonY, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Download Selected"), button -> {
            if (!this.isScanning && !this.isDownloading && !this.readyToApply) startDownload();
        }).bounds(panelX + panelWidth - 235, buttonY, 120, 20).build());

        this.applyButton = Button.builder(Component.literal("Apply Changes"), button -> {
            if (this.readyToApply) applyAndRestart();
        }).bounds(panelX + panelWidth - 110, buttonY, 110, 20).build();
        
        this.applyButton.active = false; 
        this.addRenderableWidget(this.applyButton);

        runBackendScan();
    }

    private void runBackendScan() {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, ModContainer> hashToMod = new HashMap<>();
                ConfigManager.ConfigData config = ConfigManager.getConfig();

                for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                    String id = mod.getMetadata().getId();
                    if (id.equals("fabricloader") || id.equals("java") || id.equals("minecraft")) continue;

                    mod.getOrigin().getPaths().forEach(path -> {
                        if (path.toString().endsWith(".jar") && !path.getFileName().toString().equals("updater.jar")) {
                            if (config.autoCheckMode == ConfigManager.AutoCheckMode.ALL || 
                               (config.autoCheckMode == ConfigManager.AutoCheckMode.MANUAL && !config.ignoredMods.contains(path.getFileName().toString()))) {
                                try { hashToMod.put(getFileHash(path), mod); } catch (Exception ignored) {}
                            }
                        }
                    });
                }

                if (hashToMod.isEmpty()) {
                    updateStatus("No monitored mods found.");
                    return;
                }

                // 1. DYNAMICALLY fetch the actual Minecraft version from Fabric (e.g., "1.21")
                String mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse("1.21");

                // 2. Pass the real version to Modrinth
                Map<String, ModrinthClient.ModVersion> apiResponse = ModrinthClient.checkBulkUpdates(new ArrayList<>(hashToMod.keySet()), mcVersion).join();
                
                this.minecraft.execute(() -> {
                    int count = 0;
                    for (Map.Entry<String, ModrinthClient.ModVersion> entry : apiResponse.entrySet()) {
                        String oldHash = entry.getKey();
                        ModrinthClient.ModVersion newVer = entry.getValue();
                        ModContainer localMod = hashToMod.get(oldHash);

                        if (newVer.files() == null || newVer.files().isEmpty() || localMod == null) continue;
                        ModrinthClient.ModFile primaryFile = newVer.files().stream().filter(ModrinthClient.ModFile::primary).findFirst().orElse(newVer.files().get(0));
                        String newHash = primaryFile.hashes().get("sha1");

                        if (newHash != null && !oldHash.equals(newHash)) {
                            count++;
                            
                            ModMetadata meta = localMod.getMetadata();
                            String realName = meta.getName();
                            String desc = meta.getDescription().isEmpty() ? "No description provided." : meta.getDescription();
                            
                            String author = "Unknown";
                            if (!meta.getAuthors().isEmpty()) author = meta.getAuthors().iterator().next().getName();

                            String cleanOldVer = meta.getVersion().getFriendlyString().split("\\+")[0];
                            String cleanNewVer = newVer.version_number().split("\\+")[0];
                            
                            String changelog = newVer.changelog() != null && !newVer.changelog().isEmpty() 
                                    ? newVer.changelog().replaceAll("(?m)^[#*\\->]\\s*", "") 
                                    : "No changelog provided for this update.";

                            String oldFilename = "unknown.jar";
                            for (Path p : localMod.getOrigin().getPaths()) {
                                if (p.toString().endsWith(".jar")) oldFilename = p.getFileName().toString();
                            }

                            this.listWidget.addRealUpdate(newVer.project_id(), realName, author, desc, changelog, oldFilename, primaryFile.filename(), primaryFile.url(), cleanOldVer, cleanNewVer);
                        }
                    }
                    updateStatus(count == 0 ? "All mods up to date!" : "Found " + count + " available updates.");
                });

            } catch (Exception e) {
                // 3. SAFETY NET: If the API fails, catch it and show it on the UI!
                e.printStackTrace(); // Print to your console for debugging
                updateStatus("§cError scanning updates: " + e.getMessage());
            }
        });
    }
    
    private void startDownload() { /* Keep your existing code! */ }
    private void applyAndRestart() { /* Keep your existing code! */ }
    private void updateStatus(String msg) { /* Keep your existing code! */ }
    private String getFileHash(Path path) throws Exception { /* Keep your existing code! */ return ""; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int panelX = 40;
        int panelY = 40;
        int panelWidth = this.width - 80;
        int panelHeight = this.height - 90;
        int listWidth = (int) (panelWidth * 0.60);
        int sidePanelX = panelX + listWidth;
        int sidePanelWidth = panelWidth - listWidth;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x99000000);
        graphics.fill(sidePanelX, panelY, sidePanelX + 1, panelY + panelHeight, 0x55FFFFFF);

        graphics.text(this.font, Component.literal("Mod Updater"), panelX + 10, panelY + 10, 0xFFFFFFFF, true);
        graphics.text(this.font, Component.literal(this.statusMessage), panelX + 100, panelY + 10, 0xFFFFAA00, false);

        // --- THE RICH SIDE PANEL ---
        UpdateListEntry viewedEntry = this.listWidget.getSelected();
        if (viewedEntry != null) {
            // 1. Icon (32x32 size)
            graphics.blit(DEFAULT_ICON, sidePanelX + 10, panelY + 10, 0, 0, 32, 32, 32, 32);

            // 2. Mod Name, Version, Author to the right of the Icon
            int textX = sidePanelX + 48;
            graphics.text(this.font, Component.literal("§f§l" + viewedEntry.modName), textX, panelY + 10, 0xFFFFFFFF, false);
            graphics.text(this.font, Component.literal("Version: " + viewedEntry.newVer), textX, panelY + 22, 0xFFAAAAAA, false);
            graphics.text(this.font, Component.literal("Author: §b" + viewedEntry.author), textX, panelY + 34, 0xFFAAAAAA, false);

            // 3. Word-Wrapped Description
            int currentY = panelY + 55;
            graphics.text(this.font, Component.literal("§eDescription:"), sidePanelX + 10, currentY, 0xFFFFFFFF, false);
            currentY += 12;
            
            // Magic word-wrapping native to Minecraft!
            for (FormattedCharSequence line : this.font.split(Component.literal(viewedEntry.description), sidePanelWidth - 20)) {
                graphics.text(this.font, line, sidePanelX + 10, currentY, 0xFFAAAAAA, false);
                currentY += 10;
                if (currentY > panelY + 120) break; // Prevent description from taking up the whole screen
            }

            // 4. Word-Wrapped Changelog
            currentY += 10;
            graphics.text(this.font, Component.literal("§eChangelog:"), sidePanelX + 10, currentY, 0xFFFFFFFF, false);
            currentY += 12;

            for (FormattedCharSequence line : this.font.split(Component.literal(viewedEntry.changelog), sidePanelWidth - 20)) {
                graphics.text(this.font, line, sidePanelX + 10, currentY, 0xFFFFFFFF, false); // Brighter white for changelog
                currentY += 10;
                if (currentY > panelY + panelHeight - 20) break; // Clip text before it hits the bottom buttons
            }

        } else {
            graphics.text(this.font, Component.literal("Select an update to view details."), sidePanelX + 10, panelY + 10, 0xFFAAAAAA, false);
        }
    }
}
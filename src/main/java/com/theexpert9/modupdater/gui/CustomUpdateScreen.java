
package com.theexpert9.modupdater.gui;

import com.theexpert9.modupdater.api.ModrinthClient;
import com.theexpert9.modupdater.util.ConfigManager;
import com.theexpert9.modupdater.util.DownloadManager;
import com.theexpert9.modupdater.util.StatusWriter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
import java.util.stream.Stream;

public class CustomUpdateScreen extends Screen {
    private final Screen parentScreen;
    private UpdateListWidget listWidget;
    
    private boolean isScanning = true;
    private boolean isDownloading = false;
    private boolean readyToApply = false;
    private String statusMessage = "Scanning hashes in background... Please wait.";
    private Button applyButton;

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

        this.listWidget = new UpdateListWidget(this.minecraft, listWidth, panelHeight - 30, panelY + 25, 26);
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

        // Download Button
        this.addRenderableWidget(Button.builder(Component.literal("Download Selected"), button -> {
            if (!this.isScanning && !this.isDownloading ) startDownload();
        }).bounds(panelX + panelWidth - 235, buttonY, 120, 20).build());

        // Inside init(): Look for waiting files immediately!
        List<String> pendingFiles = getPendingDownloadedFiles();
        if (!pendingFiles.isEmpty()) {
            this.readyToApply = true;
        }

        this.applyButton = Button.builder(Component.literal("Apply Changes"), button -> {
            if (this.readyToApply) {
                // Open the new Confirm Screen instead of instantly restarting!
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new ConfirmApplyScreen(this, getPendingDownloadedFiles(), this::applyAndRestart));
                }
            }
        }).bounds(panelX + panelWidth - 110, buttonY, 110, 20).build();
        
        this.applyButton.active = this.readyToApply; 
        this.addRenderableWidget(this.applyButton);

        runBackendScan();
    }

    private void runBackendScan() {
        // (Unchanged Hash Scanning and Bulk API lookup logic stays completely intact)
        CompletableFuture.runAsync(() -> {
            Map<String, String> hashToFilename = new HashMap<>();
            ConfigManager.ConfigData config = ConfigManager.getConfig();

            try (Stream<Path> stream = Files.list(FabricLoader.getInstance().getGameDir().resolve("mods"))) {
                stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
                    String filename = path.getFileName().toString();
                    if (!filename.startsWith("fabric-") && !filename.equals("updater.jar")) {
                        if (config.autoCheckMode == ConfigManager.AutoCheckMode.ALL || 
                           (config.autoCheckMode == ConfigManager.AutoCheckMode.MANUAL && !config.ignoredMods.contains(filename))) {
                            try { hashToFilename.put(getFileHash(path), filename); } catch (Exception ignored) {}
                        }
                    }
                });
            } catch (Exception ignored) {}

            if (hashToFilename.isEmpty()) {
                updateStatus("No monitored mods found.");
                return;
            }

            Map<String, ModrinthClient.ModVersion> apiResponse = ModrinthClient.checkBulkUpdates(new ArrayList<>(hashToFilename.keySet()), "26.1.2").join();
            
            this.minecraft.execute(() -> {
                int count = 0;
                for (Map.Entry<String, ModrinthClient.ModVersion> entry : apiResponse.entrySet()) {
                    String oldHash = entry.getKey();
                    ModrinthClient.ModVersion newVer = entry.getValue();
                    String oldFilename = hashToFilename.get(oldHash);

                    if (newVer.files() == null || newVer.files().isEmpty()) continue;
                    ModrinthClient.ModFile primaryFile = newVer.files().stream().filter(ModrinthClient.ModFile::primary).findFirst().orElse(newVer.files().get(0));
                    String newHash = primaryFile.hashes().get("sha1");

                    if (newHash != null && !oldHash.equals(newHash)) {
                        count++;
                        // Splitting and cleaning up versions into simple parameters
                        String cleanOldVer = "Old"; 
                        String cleanNewVer = newVer.version_number();
                        
                        // Check if this EXACT new file is already sitting in the pending folder
                        boolean alreadyDownloaded = getPendingDownloadedFiles().contains(primaryFile.filename());

                        // Pass the boolean as the final parameter!
                        this.listWidget.addRealUpdate(newVer.project_id(), oldFilename.replace(".jar", ""),/*  realName, author, desc, changelog, */oldFilename, primaryFile.filename(), primaryFile.url(), cleanOldVer, cleanNewVer, alreadyDownloaded);
                    }
                }
                updateStatus(count == 0 ? "All mods up to date!" : "Found " + count + " available updates.");
            });
        });
    }

    private void startDownload() {
        List<UpdateListEntry> toDownload = this.listWidget.getCheckedEntries();
        if (toDownload.isEmpty()) return;

        this.isDownloading = true;
        AtomicInteger completedCount = new AtomicInteger(0);
        int total = toDownload.size();

        for (UpdateListEntry update : toDownload) {
            DownloadManager.downloadMod(update.downloadUrl, update.newFilename, (percent, speedMBps) -> {
                updateStatus(String.format("Downloading %s... %.0f%% (%.1f MB/s)", update.newFilename, percent, speedMBps));
            }).thenAccept(path -> {
                StatusWriter.appendUpdate(update.oldFilename, update.newFilename);
                if (completedCount.incrementAndGet() >= total) {
                    this.minecraft.execute(() -> {
                        this.isDownloading = false;
                        this.readyToApply = true;
                        this.applyButton.active = true; // Activating button now!
                        updateStatus("Downloads Complete! Click 'Apply Changes' to complete installation.");
                    });
                }
            });
        }
    }

    private void applyAndRestart() {
        try {
            Path pendingDir = DownloadManager.getPendingUpdatesDir();
            Path updaterPath = pendingDir.resolve("updater.jar");
            try (InputStream is = CustomUpdateScreen.class.getResourceAsStream("/assets/modupdater/updater.jar")) {
                if (is != null) Files.copy(is, updaterPath, StandardCopyOption.REPLACE_EXISTING);
                else return;
            }
            long pid = ProcessHandle.current().pid();
            String modsPath = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().toString();
            String javaPath = ProcessHandle.current().info().command().orElse("java");
            Runtime.getRuntime().exec(new String[]{javaPath, "-jar", updaterPath.toAbsolutePath().toString(), String.valueOf(pid), modsPath});
            this.minecraft.stop();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateStatus(String msg) {
        if (this.minecraft != null) {
            this.minecraft.execute(() -> {
                this.statusMessage = msg;
                this.isScanning = false;
            });
        }
    }

    private String getFileHash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest.digest()) hexString.append(String.format("%02x", b & 0xFF));
        return hexString.toString();
    }

    // Helper to see exactly what is waiting in the pending folder
    private List<String> getPendingDownloadedFiles() {
        List<String> pending = new ArrayList<>();
        try {
            Path pendingDir = DownloadManager.getPendingUpdatesDir();
            if (Files.exists(pendingDir)) {
                try (Stream<Path> stream = Files.list(pendingDir)) {
                    stream.forEach(p -> {
                        String name = p.getFileName().toString();
                        if (name.endsWith(".jar") && !name.equals("updater.jar")) {
                            pending.add(name);
                        }
                    });
                }
            }
        } catch (Exception ignored) {}
        return pending;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int panelX = 40;
        int panelY = 40;
        int panelWidth = this.width - 80;
        int panelHeight = this.height - 90;
        int listWidth = (int) (panelWidth * 0.60);
        int sidePanelX = panelX + listWidth;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x99000000);
        graphics.fill(sidePanelX, panelY, sidePanelX + 1, panelY + panelHeight, 0x55FFFFFF);

        graphics.text(this.font, Component.literal("Mod Updater"), panelX + 10, panelY + 10, 0xFFFFFFFF, true);
        graphics.text(this.font, Component.literal(this.statusMessage), panelX + 100, panelY + 10, 0xFFFFAA00, false);

        UpdateListEntry viewedEntry = this.listWidget.getSelected();
        if (viewedEntry != null) {
            graphics.text(this.font, Component.literal("Selected Mod Details"), sidePanelX + 10, panelY + 10, 0xFFFFAA00, false);
            graphics.text(this.font, Component.literal("§f§l" + viewedEntry.modName), sidePanelX + 10, panelY + 30, 0xFFFFFFFF, false);
            graphics.text(this.font, Component.literal("File: " + viewedEntry.newFilename), sidePanelX + 10, panelY + 45, 0xFFAAAAAA, false);
            
            graphics.text(this.font, Component.literal("[ Mod Icon Area ]"), sidePanelX + 10, panelY + 80, 0xFF555555, false);
            graphics.text(this.font, Component.literal("Changelog data coming soon..."), sidePanelX + 10, panelY + 100, 0xFF555555, false);
        } else {
            graphics.text(this.font, Component.literal("Select a mod to view details."), sidePanelX + 10, panelY + 10, 0xFFAAAAAA, false);
        }
    }
}
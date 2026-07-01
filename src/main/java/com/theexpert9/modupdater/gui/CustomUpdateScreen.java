
package com.theexpert9.modupdater.gui;

//import com.theexpert9.modupdater.api.ModrinthClient;
import com.theexpert9.modupdater.util.ConfigManager;
import com.theexpert9.modupdater.util.DownloadManager;
import com.theexpert9.modupdater.util.StatusWriter;

import com.theexpert9.modupdater.util.UpdateManager;

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
    private String statusMessage = "Loading UI...";
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

        this.listWidget = new UpdateListWidget(this.minecraft, listWidth, panelHeight - 30, panelY + 25, 28);
        this.listWidget.setX(panelX);
        this.addRenderableWidget(this.listWidget);

        int buttonY = panelY + panelHeight + 5;

        // 1. Check for previously downloaded mods (State Persistence)
        List<String> pendingFiles = getPendingDownloadedFiles();
        if (!pendingFiles.isEmpty())
            this.readyToApply = true;

        // --- BUTTONS ---
        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            if (this.minecraft != null)
                this.minecraft.gui.setScreen(this.parentScreen);
        }).bounds(10, 10, 50, 20).build());

        // BRAND NEW: The Manual Refresh Button
        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> {
            if (!this.isDownloading) {
                this.statusMessage = "Refreshing updates from Modrinth...";
                this.listWidget.clearUpdates();

                // Guarantee the UI thread picks up the result, even if it fails!
                UpdateManager.forceRefresh().whenComplete((result, error) -> {
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            if (error != null) {
                                this.statusMessage = "§cRefresh failed. Check console.";
                            } else {
                                loadUpdatesFromCache();
                            }
                        });
                    }
                });
            }
        }).bounds(panelX + panelWidth - 75, panelY - 25, 75, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Select All"), button -> {
            this.listWidget.setAllSelected(true);
        }).bounds(panelX, buttonY, 75, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Deselect All"), button -> {
            this.listWidget.setAllSelected(false);
        }).bounds(panelX + 80, buttonY, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Download Selected"), button -> {
            if (!this.isDownloading)
                startDownload();
        }).bounds(panelX + panelWidth - 235, buttonY, 120, 20).build());

        // this.applyButton = Button.builder(Component.literal("Apply Changes"), button -> {
        //     if (this.readyToApply && this.minecraft != null) {
        //         this.minecraft.gui
        //                 .setScreen(new ConfirmApplyScreen(this, getPendingDownloadedFiles(), this::applyAndRestart));
        //     }
        // }).bounds(panelX + panelWidth - 110, buttonY, 110, 20).build();
        boolean downloadingGlobally = isAnyDownloadActive();

        this.applyButton = Button.builder(Component.literal("Apply Changes"), button -> {
            if (this.readyToApply && !isAnyDownloadActive() && this.minecraft != null) {
                this.minecraft.gui
                        .setScreen(new ConfirmApplyScreen(this, getPendingDownloadedFiles(), this::applyAndRestart));
            }
        }).bounds(panelX + panelWidth - 110, buttonY, 110, 20).build();

        // Disable the button if no files are ready OR if a download is currently active
        this.applyButton.active = this.readyToApply && !downloadingGlobally;

        // Add the dynamic descriptive hover tooltip
        if (downloadingGlobally) {
            this.applyButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§cCannot apply changes while downloads are active!")));
        } else if (!this.readyToApply) {
            this.applyButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("No updates have been downloaded yet.")));
        }

        this.applyButton.active = this.readyToApply;
        this.addRenderableWidget(this.applyButton);

        // 2. Instead of scanning, instantly load what is already in memory!
        loadUpdatesFromCache();
    }

    // --- INSTANT CACHE LOADER (Bulletproof Version) ---
    private void loadUpdatesFromCache() {
        // this.listWidget.children().clear();
        this.listWidget.clearUpdates();

        if (UpdateManager.AVAILABLE_UPDATES.isEmpty()) {
            this.statusMessage = UpdateManager.currentStatus;
            return;
        }

        List<String> pendingFiles = getPendingDownloadedFiles();
        int count = 0;

        for (UpdateManager.CachedUpdate cache : UpdateManager.AVAILABLE_UPDATES.values()) {
            try {
                var meta = cache.localMod().getMetadata();
                String realName = meta.getName() != null ? meta.getName() : "Unknown Mod";

                // Safety: Null check the versions before splitting
                String cleanOldVer = meta.getVersion() != null ? meta.getVersion().getFriendlyString().split("\\+")[0]
                        : "v?";
                String cleanNewVer = cache.newVersion().version_number() != null
                        ? cache.newVersion().version_number().split("\\+")[0]
                        : "v?";

                String oldFilename = "unknown.jar";
                // Safety: Only try to read paths if it's an actual physical file
                if (cache.localMod().getOrigin().getKind() == net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH) {
                    for (Path p : cache.localMod().getOrigin().getPaths()) {
                        if (p != null && p.getFileName() != null && p.getFileName().toString().endsWith(".jar")) {
                            oldFilename = p.getFileName().toString();
                        }
                    }
                }

                boolean alreadyDownloaded = pendingFiles.contains(cache.primaryFilename());

                this.listWidget.addRealUpdate(cache.newVersion().project_id(), realName, oldFilename,
                        cache.primaryFilename(), cache.downloadUrl(), cleanOldVer, cleanNewVer, alreadyDownloaded);
                count++;

            } catch (Exception e) {
                // If ONE mod breaks, catch it so the other 24 still load!
                System.err.println("Failed to render update for: " + cache.localMod().getMetadata().getId());
                e.printStackTrace();
            }
        }

        this.statusMessage = "Displaying " + count + " available updates.";
    }

    // private void startDownload() {
    //     List<UpdateListEntry> toDownload = this.listWidget.getCheckedEntries();
    //     if (toDownload.isEmpty()) return;

    //     this.isDownloading = true;
    //     AtomicInteger completedCount = new AtomicInteger(0);
    //     int total = toDownload.size();

    //     for (UpdateListEntry update : toDownload) {
    //         DownloadManager.downloadMod(update.downloadUrl, update.newFilename, (percent, speedMBps) -> {
    //             updateStatus(String.format("Downloading %s... %.0f%% (%.1f MB/s)", update.newFilename, percent, speedMBps));
    //         }).thenAccept(path -> {
    //             StatusWriter.appendUpdate(update.oldFilename, update.newFilename);
    //             if (completedCount.incrementAndGet() >= total) {
    //                 this.minecraft.execute(() -> {
    //                     this.isDownloading = false;
    //                     this.readyToApply = true;
    //                     this.applyButton.active = true; // Activating button now!
    //                     updateStatus("Downloads Complete! Click 'Apply Changes' to complete installation.");
    //                 });
    //             }
    //         });
    //     }
    // }

    // private void startDownload() {
    //     List<UpdateListEntry> toDownload = this.listWidget.getCheckedEntries();
    //     if (toDownload.isEmpty())
    //         return;

    //     this.isDownloading = true;
    //     this.applyButton.active = false; // 1. CRITICAL SAFEGUARD: Prevent restarts mid-download!

    //     AtomicInteger completedCount = new AtomicInteger(0);
    //     int total = toDownload.size();

    //     for (UpdateListEntry update : toDownload) {
    //         // Tell our global tracker this specific mod ID has started processing
    //         neelesh.easy_install.util.GlobalDownloadTracker.setState(update.projectId, 1);

    //         DownloadManager.downloadMod(update.downloadUrl, update.newFilename, (percent, speedMBps) -> {
    //             // Pipe percent directly down to the global tracker module for real-time tracking
    //             neelesh.easy_install.util.GlobalDownloadTracker.setProgress(update.projectId,
    //                     (float) (percent / 100.0));

    //             updateStatus("Downloading Updates");
    //         }).thenAccept(path -> {
    //             StatusWriter.appendUpdate(update.oldFilename, update.newFilename);

    //             // Mark as fully finished and completed inside memory
    //             neelesh.easy_install.util.GlobalDownloadTracker.setState(update.projectId, 2);

    //             if (completedCount.incrementAndGet() >= total) {
    //                 this.minecraft.execute(() -> {
    //                     this.isDownloading = false;
    //                     this.readyToApply = true;
    //                     this.applyButton.active = true; // Re-activate the apply button once ALL are done
    //                     updateStatus("Downloads Complete! Click 'Apply Changes' to complete installation.");
    //                 });
    //             }
    //         });
    //     }
    // }

    private void startDownload() {
        List<UpdateListEntry> toDownload = this.listWidget.getCheckedEntries();
        if (toDownload.isEmpty()) return;

        this.isDownloading = true;
        this.applyButton.active = false; // Immediately disable the apply button!
        
        AtomicInteger completedCount = new AtomicInteger(0);
        int total = toDownload.size();

        for (UpdateListEntry update : toDownload) {
            // Mark the unique mod ID as actively downloading inside our memory tracker
            neelesh.easy_install.util.GlobalDownloadTracker.setState(update.projectId, 1);
            
            // Append a temporary file extension name to mask partial network files from the path validator
            String tempFilename = update.newFilename + ".tmp";

            DownloadManager.downloadMod(update.downloadUrl, tempFilename, (percent, speedMBps) -> {
                neelesh.easy_install.util.GlobalDownloadTracker.setProgress(update.projectId, (float)(percent / 100.0));
                updateStatus(String.format("Downloading...."));
            }).thenAccept(path -> {
                try {
                    // Rename the verified file container back to a stable .jar once completely downloaded
                    Path finalPath = path.getParent().resolve(update.newFilename);
                    java.nio.file.Files.move(path, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    
                    StatusWriter.appendUpdate(update.oldFilename, update.newFilename);
                    neelesh.easy_install.util.GlobalDownloadTracker.setState(update.projectId, 2); // Completed
                } catch (Exception e) {
                    System.err.println("Failed to finalize file rename operation for: " + update.newFilename);
                    e.printStackTrace();
                }

                if (completedCount.incrementAndGet() >= total) {
                    this.minecraft.execute(() -> {
                        this.isDownloading = false;
                        this.readyToApply = !getPendingDownloadedFiles().isEmpty();
                        this.applyButton.active = this.readyToApply; 
                        updateStatus("Downloads Complete! Click 'Apply Changes' to complete installation.");
                    });
                }
            });
        }
    }
    
    
    private boolean isAnyDownloadActive() {
        // If the local screen says it's downloading, trust it immediately
        if (this.isDownloading)
            return true;

        // Scan all available cached updates to check their thread states in the global tracker
        for (String projectId : UpdateManager.AVAILABLE_UPDATES.keySet()) {
            if (neelesh.easy_install.util.GlobalDownloadTracker.getState(projectId) == 1) {
                return true;
            }
        }
        return false;
    }

    private void applyAndRestart() {
        try {
            Path pendingDir = DownloadManager.getPendingUpdatesDir();
            Path updaterPath = pendingDir.resolve("updater.jar");
            try (InputStream is = CustomUpdateScreen.class.getResourceAsStream("/assets/modupdater/updater.jar")) {
                if (is != null)
                    Files.copy(is, updaterPath, StandardCopyOption.REPLACE_EXISTING);
                else
                    return;
            }
            long pid = ProcessHandle.current().pid();
            String pendingPath = pendingDir.toAbsolutePath().toString();
            String modsPath = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().toString();
            String javaPath = ProcessHandle.current().info().command().orElse("java");
            Runtime.getRuntime().exec(new String[] { javaPath, "-jar", updaterPath.toAbsolutePath().toString(),
                    String.valueOf(pid), modsPath, pendingPath });
            this.minecraft.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateStatus(String msg) {
        if (this.minecraft != null) {
            this.minecraft.execute(() -> {
                this.statusMessage = msg;
                this.isScanning = false;
            });
        }
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
                        
                        // Strict validation: Skip incomplete temp buffers entirely
                        if (name.endsWith(".jar") && !name.equals("updater.jar")) {
                            
                            // Cross-check across memory maps to confirm this ID isn't downloading anywhere
                            boolean isStillStreaming = false;
                            for (UpdateManager.CachedUpdate cache : UpdateManager.AVAILABLE_UPDATES.values()) {
                                if (cache.primaryFilename().equals(name)) {
                                    if (neelesh.easy_install.util.GlobalDownloadTracker.getState(cache.newVersion().project_id()) == 1) {
                                        isStillStreaming = true;
                                        break;
                                    }
                                }
                            }
                            
                            if (!isStillStreaming) {
                                pending.add(name);
                            }
                        }
                    });
                }
            }
        } catch (Exception ignored) {}
        return pending;
    }

    // private List<String> getPendingDownloadedFiles() {
    //     List<String> pending = new ArrayList<>();
    //     try {
    //         Path pendingDir = DownloadManager.getPendingUpdatesDir();
    //         if (Files.exists(pendingDir)) {
    //             try (Stream<Path> stream = Files.list(pendingDir)) {
    //                 stream.forEach(p -> {
    //                     String name = p.getFileName().toString();
    //                     if (name.endsWith(".jar") && !name.equals("updater.jar")) {
    //                         pending.add(name);
    //                     }
    //                 });
    //             }
    //         }
    //     } catch (Exception ignored) {
    //     }
    //     return pending;
    // }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int panelX = 40;
        int panelY = 40;
        int panelWidth = this.width - 80;
        int panelHeight = this.height - 90;
        int listWidth = (int) (panelWidth * 0.60);
        int sidePanelX = panelX + listWidth;

        // Live status updating! If the manager is scanning, show its live status. Otherwise, show our local message.
        String displayStatus = UpdateManager.AVAILABLE_UPDATES.isEmpty() ? UpdateManager.currentStatus
                : this.statusMessage;
        graphics.text(this.font, Component.literal(displayStatus), panelX + 100, panelY + 10, 0xFFFFAA00, false);

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x99000000);
        graphics.fill(sidePanelX, panelY, sidePanelX + 1, panelY + panelHeight, 0x55FFFFFF);

        graphics.text(this.font, Component.literal("Mod Updater"), panelX + 10, panelY + 10, 0xFFFFFFFF, true);
        //graphics.text(this.font, Component.literal(this.statusMessage), panelX + 100, panelY + 10, 0xFFFFAA00, false);

        UpdateListEntry viewedEntry = this.listWidget.getSelected();
        if (viewedEntry != null) {
            graphics.text(this.font, Component.literal("Selected Mod Details"), sidePanelX + 10, panelY + 10,
                    0xFFFFAA00, false);
            graphics.text(this.font, Component.literal("§f§l" + viewedEntry.modName), sidePanelX + 10, panelY + 30,
                    0xFFFFFFFF, false);
            graphics.text(this.font, Component.literal("File: " + viewedEntry.newFilename), sidePanelX + 10,
                    panelY + 45, 0xFFAAAAAA, false);

            graphics.text(this.font, Component.literal("[ Mod Icon Area ]"), sidePanelX + 10, panelY + 80, 0xFF555555,
                    false);
            graphics.text(this.font, Component.literal("Changelog data coming soon..."), sidePanelX + 10, panelY + 100,
                    0xFF555555, false);
        } else {
            graphics.text(this.font, Component.literal("Select a mod to view details."), sidePanelX + 10, panelY + 10,
                    0xFFAAAAAA, false);
        }
    }
}
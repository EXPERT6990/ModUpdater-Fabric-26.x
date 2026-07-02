package com.theexpert9.modupdater.gui;

import com.theexpert9.modupdater.util.ConfigManager;
import com.theexpert9.modupdater.util.DownloadManager;
import com.theexpert9.modupdater.util.StatusWriter;
import com.theexpert9.modupdater.util.UpdateManager;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class UpdateScreen {

    private record PendingUIUpdate(String projectId, String modName, String oldFilename, String newVersion, String downloadUrl, String newFilename, boolean isDownloaded) {}
    private static final Map<String, Boolean> selectedMods = new HashMap<>();

    // Upgraded tracking method to perfectly filter out working .tmp files
    private static List<String> getPendingDownloadedFiles() {
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

    public static int getAvailableUpdateCountSilent() {
        return UpdateManager.AVAILABLE_UPDATES.size();
    }

    public static Screen create(Screen parent) {
        List<String> pendingFiles = getPendingDownloadedFiles();
        List<PendingUIUpdate> availableUpdates = new ArrayList<>();

        for (UpdateManager.CachedUpdate cache : UpdateManager.AVAILABLE_UPDATES.values()) {
            boolean isDownloaded = pendingFiles.contains(cache.primaryFilename());
            
            String oldFilename = "unknown.jar";
            if (cache.localMod().getOrigin().getKind() == net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH) {
                for (Path p : cache.localMod().getOrigin().getPaths()) {
                    if (p != null && p.getFileName() != null && p.getFileName().toString().endsWith(".jar")) {
                        oldFilename = p.getFileName().toString();
                    }
                }
            }

            availableUpdates.add(new PendingUIUpdate(
                    cache.newVersion().project_id(),
                    cache.localMod().getMetadata().getName(),
                    oldFilename,
                    cache.newVersion().version_number(),
                    cache.downloadUrl(),
                    cache.primaryFilename(),
                    isDownloaded
            ));
        }

        // Global download state check
        boolean downloadingGlobally = false;
        for (String projectId : UpdateManager.AVAILABLE_UPDATES.keySet()) {
            if (neelesh.easy_install.util.GlobalDownloadTracker.getState(projectId) == 1) {
                downloadingGlobally = true;
                break;
            }
        }

        String btnText = availableUpdates.isEmpty() ? "No updates available (Click to Refresh)" : "Download Selected Mods";
        String nextState = availableUpdates.isEmpty() ? "idle" : "ready";
        
        if (downloadingGlobally) {
            btnText = "Downloads Processing in Background...";
            nextState = "locked";
        } else if (!availableUpdates.isEmpty() && availableUpdates.stream().allMatch(PendingUIUpdate::isDownloaded)) {
            btnText = "Apply Changes & Restart";
            nextState = "done";
        }

        return buildScreen(parent, availableUpdates, nextState, btnText);
    }

    private static Screen buildScreen(Screen parent, List<PendingUIUpdate> availableUpdates, String state, String statusText) {
        ConfigCategory.Builder updatesCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Updates"));

        // Main action button configuration row
        updatesCategory.option(ButtonOption.createBuilder()
                .name(Component.literal(statusText))
                .available(!state.equals("locked")) // Disable if active downloads are processing
                .action((screen, buttonOption) -> {
                    if (state.equals("idle")) {
                        UpdateManager.forceRefresh().whenComplete((res, err) -> {
                            Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(create(parent)));
                        });
                    }
                    else if (state.equals("ready") && availableUpdates.stream().anyMatch(u -> selectedMods.getOrDefault(u.projectId(), false))) {
                        buttonOption.setAvailable(false); // Instantly gray it out upon click
                        downloadSelectedMods(parent, availableUpdates);
                    }
                    else if (state.equals("done")) applyAndRestart();
                }).build());
            
        // --- SECURED DEDICATED APPLY BUTTON ---
        boolean hasWaitingFiles = availableUpdates.stream().anyMatch(PendingUIUpdate::isDownloaded);
        boolean downloadingGlobally = false;
        for (String projectId : UpdateManager.AVAILABLE_UPDATES.keySet()) {
            if (neelesh.easy_install.util.GlobalDownloadTracker.getState(projectId) == 1) {
                downloadingGlobally = true;
                break;
            }
        }

        updatesCategory.option(ButtonOption.createBuilder()
            .name(Component.literal("🚀 Apply Downloaded Mods & Restart"))
            .description(downloadingGlobally 
                ? OptionDescription.of(Component.literal("§cDisabled: Downloads are currently running in the background."))
                : OptionDescription.of(Component.literal("Click to verify and apply staged updates.")))
            .available(!downloadingGlobally && hasWaitingFiles) 
            .action((screen, buttonOption) -> applyAndRestart())
            .build());
        // ------------------------------------

        for (PendingUIUpdate update : availableUpdates) {
            selectedMods.putIfAbsent(update.projectId(), !update.isDownloaded()); 
            
            int globalState = neelesh.easy_install.util.GlobalDownloadTracker.getState(update.projectId());

            // BRAND NEW: If tracker says done but file isn't in pendingFiles list, self-heal!
            if (globalState == 2 && !update.isDownloaded()) {
                neelesh.easy_install.util.GlobalDownloadTracker.setState(update.projectId(), 0);
                globalState = 0;
            }

            boolean isQueued = update.isDownloaded() || globalState == 2;

            String label = isQueued 
                ? "§a[QUEUED] " + update.modName() 
                : "📦 " + update.modName() + " (" + update.oldFilename() + " ➔ " + update.newFilename() + ")";

            var optionBuilder = Option.<Boolean>createBuilder()
                    .name(Component.literal(label))
                    .binding(true, () -> selectedMods.get(update.projectId()), val -> {
                        if (!isQueued) selectedMods.put(update.projectId(), val);
                    })
                    .controller(TickBoxControllerBuilder::create);

            if (isQueued || globalState == 1) {
                optionBuilder.available(false); // Lock the checkmarks if processing or queued
            }

            updatesCategory.option(optionBuilder.build());
        }

        // --- REST OF CATEGORIES REMAIN INTACT ---
        ConfigCategory settingsCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Settings"))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Enable Notifications"))
                        .binding(true, () -> ConfigManager.getConfig().showNotifications, val -> ConfigManager.getConfig().showNotifications = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(Option.<ConfigManager.AutoCheckMode>createBuilder()
                        .name(Component.literal("Auto Check Mode"))
                        .binding(ConfigManager.AutoCheckMode.ALL, () -> ConfigManager.getConfig().autoCheckMode, val -> ConfigManager.getConfig().autoCheckMode = val)
                        .controller(opt -> EnumControllerBuilder.create(opt).enumClass(ConfigManager.AutoCheckMode.class))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Component.literal("Background Scan Interval (Minutes)"))
                        .binding(15, () -> ConfigManager.getConfig().scanIntervalMinutes, val -> ConfigManager.getConfig().scanIntervalMinutes = val)
                        .controller(dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder::create)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Component.literal("API Batch Size"))
                        .binding(50, () -> ConfigManager.getConfig().apiBatchSize, val -> ConfigManager.getConfig().apiBatchSize = val)
                        .controller(dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder::create)
                        .build())
                .build();

        ConfigCategory.Builder monitoredMods = ConfigCategory.createBuilder()
                .name(Component.literal("Monitored Mods"));

        try (Stream<Path> stream = Files.list(FabricLoader.getInstance().getGameDir().resolve("mods"))) {
            stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
                String filename = path.getFileName().toString();
                if (!filename.startsWith("fabric-") && !filename.equals("updater.jar")) {
                    monitoredMods.option(Option.<Boolean>createBuilder()
                            .name(Component.literal(filename))
                            .binding(true,
                                    () -> !ConfigManager.getConfig().ignoredMods.contains(filename),
                                    val -> {
                                        if (val) ConfigManager.getConfig().ignoredMods.remove(filename);
                                        else if (!ConfigManager.getConfig().ignoredMods.contains(filename)) {
                                            ConfigManager.getConfig().ignoredMods.add(filename);
                                        }
                                    })
                            .controller(TickBoxControllerBuilder::create)
                            .build());
                }
            });
        } catch (Exception ignored) {}

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Mod Updater"))
                .category(updatesCategory.build())
                .category(settingsCategory)
                .category(monitoredMods.build())
                .save(ConfigManager::save)
                .build()
                .generateScreen(parent);
    }

    private static void downloadSelectedMods(Screen parent, List<PendingUIUpdate> availableUpdates) {
        List<PendingUIUpdate> toDownload = availableUpdates.stream()
                .filter(u -> selectedMods.getOrDefault(u.projectId(), false) && !u.isDownloaded())
                .toList();

        if (toDownload.isEmpty()) return;

        AtomicInteger completedCount = new AtomicInteger(0);
        int total = toDownload.size();

        for (PendingUIUpdate update : toDownload) {
            neelesh.easy_install.util.GlobalDownloadTracker.setState(update.projectId(), 1);
            String tempFilename = update.newFilename() + ".tmp";

            DownloadManager.downloadMod(update.downloadUrl(), tempFilename, (percent, speedMBps) -> {
                // Keep progress updated silently without triggering YACL screen rebuilds!
                neelesh.easy_install.util.GlobalDownloadTracker.setProgress(update.projectId(), (float)(percent / 100.0));
            }).thenAccept(path -> {
                try {
                    Path finalPath = path.getParent().resolve(update.newFilename());
                    java.nio.file.Files.move(path, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    
                    StatusWriter.appendUpdate(update.oldFilename(), update.newFilename());
                    neelesh.easy_install.util.GlobalDownloadTracker.setState(update.projectId(), 2);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (completedCount.incrementAndGet() >= total) {
                    Minecraft.getInstance().execute(() -> {
                        // Rebuild the final screen state once all downloads finish
                        Minecraft.getInstance().gui.setScreen(create(parent));
                    });
                }
            });
        }
    }

    private static void applyAndRestart() {
        try {
            Path pendingDir = DownloadManager.getPendingUpdatesDir();
            Path updaterPath = pendingDir.resolve("updater.jar");

            try (InputStream is = UpdateScreen.class.getResourceAsStream("/assets/modupdater/updater.jar")) {
                if (is != null) Files.copy(is, updaterPath, StandardCopyOption.REPLACE_EXISTING);
                else return;
            }

            long pid = ProcessHandle.current().pid();
            String modsPath = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().toString();
            String javaBinaryPath = ProcessHandle.current().info().command().orElse("java");

            Runtime.getRuntime().exec(new String[]{
                    javaBinaryPath, "-jar", updaterPath.toAbsolutePath().toString(), String.valueOf(pid), modsPath
            });

            Minecraft.getInstance().stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
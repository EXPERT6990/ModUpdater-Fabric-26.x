package com.theexpert9.modupdater.gui;

import com.theexpert9.modupdater.util.ConfigManager;
import com.theexpert9.modupdater.util.DownloadManager;
import com.theexpert9.modupdater.util.StatusWriter;
import com.theexpert9.modupdater.util.UpdateManager;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
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

    // Upgraded record to hold modName and isDownloaded status
    private record PendingUIUpdate(String projectId, String modName, String oldFilename, String newVersion, String downloadUrl, String newFilename, boolean isDownloaded) {}
    private static final Map<String, Boolean> selectedMods = new HashMap<>();

    // Helper to check what is already sitting in the pending folder
    private static List<String> getPendingDownloadedFiles() {
        List<String> pending = new ArrayList<>();
        try {
            Path pendingDir = DownloadManager.getPendingUpdatesDir();
            if (Files.exists(pendingDir)) {
                try (Stream<Path> stream = Files.list(pendingDir)) {
                    stream.forEach(p -> pending.add(p.getFileName().toString()));
                }
            }
        } catch (Exception ignored) {}
        return pending;
    }

    // Re-added instant method for your Title Screen to read!
    public static int getAvailableUpdateCountSilent() {
        return UpdateManager.AVAILABLE_UPDATES.size();
    }

    public static Screen create(Screen parent) {
        List<String> pendingFiles = getPendingDownloadedFiles();
        List<PendingUIUpdate> availableUpdates = new ArrayList<>();

        // 1. INSTANTLY load updates from the background manager's memory!
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

        // 2. Determine the button state based on the cache
        String btnText = availableUpdates.isEmpty() ? "No updates available (Click to Refresh)" : "Download Selected Mods";
        String nextState = availableUpdates.isEmpty() ? "idle" : "ready";
        
        // If updates exist AND they are all already downloaded, wake up the Apply button!
        if (!availableUpdates.isEmpty() && availableUpdates.stream().allMatch(PendingUIUpdate::isDownloaded)) {
            btnText = "Apply Changes & Restart";
            nextState = "done";
        }

        return buildScreen(parent, availableUpdates, nextState, btnText);
    }

    private static Screen buildScreen(Screen parent, List<PendingUIUpdate> availableUpdates, String state, String statusText) {
        // --- TAB 1: UPDATES ---
        ConfigCategory.Builder updatesCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Updates"));

        updatesCategory.option(ButtonOption.createBuilder()
                .name(Component.literal(statusText))
                .action((screen, buttonOption) -> {
                    if (state.equals("idle")) {
                        // Trigger a manual background refresh if they click when idle!
                        // buttonOption.getOptionUI().setLocked(true);
                        UpdateManager.forceRefresh().whenComplete((res, err) -> {
                            Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(create(parent)));
                        });
                    }
                    else if (state.equals("ready")) downloadSelectedMods(parent, availableUpdates);
                    else if (state.equals("done")) applyAndRestart();
                }).build());
            
        // --- NEW: DEDICATED APPLY BUTTON ---
        // If there is AT LEAST ONE downloaded mod waiting, show the Apply button!
        boolean hasWaitingFiles = availableUpdates.stream().anyMatch(PendingUIUpdate::isDownloaded);
        if (hasWaitingFiles && !state.equals("done")) {
            updatesCategory.option(ButtonOption.createBuilder()
                    .name(Component.literal("🚀 Apply Downloaded Mods & Restart"))
                    .description(dev.isxander.yacl3.api.OptionDescription.of(Component.literal("You have updates waiting to be installed!")))
                    .action((screen, buttonOption) -> applyAndRestart())
                    .build());
        }
        // ------------------------------------

        for (PendingUIUpdate update : availableUpdates) {
            // Default to checked ONLY if it isn't already downloaded
            selectedMods.putIfAbsent(update.projectId(), !update.isDownloaded()); 
            
            String label = update.isDownloaded() 
                ? "§a[QUEUED] " + update.modName() 
                : "📦 " + update.modName() + " (" + update.oldFilename() + " ➔ " + update.newFilename() + ")";

            var optionBuilder = Option.<Boolean>createBuilder()
                    .name(Component.literal(label))
                    .binding(true, () -> selectedMods.get(update.projectId()), val -> {
                        if (!update.isDownloaded()) selectedMods.put(update.projectId(), val);
                    })
                    .controller(TickBoxControllerBuilder::create);

            // Visually disable the YACL tickbox if it is already queued!
            if (update.isDownloaded()) {
                optionBuilder.available(false);
            }

            updatesCategory.option(optionBuilder.build());
        }

        ConfigCategory settingsCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Settings"))
                
                // 1. Notifications Toggle
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Enable Notifications"))
                        .binding(true, () -> ConfigManager.getConfig().showNotifications, val -> ConfigManager.getConfig().showNotifications = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                        
                // 2. Auto Check Mode
                .option(Option.<ConfigManager.AutoCheckMode>createBuilder()
                        .name(Component.literal("Auto Check Mode"))
                        .binding(ConfigManager.AutoCheckMode.ALL, () -> ConfigManager.getConfig().autoCheckMode, val -> ConfigManager.getConfig().autoCheckMode = val)
                        .controller(opt -> EnumControllerBuilder.create(opt).enumClass(ConfigManager.AutoCheckMode.class))
                        .build())
                        
                // 3. NEW: Background Scan Interval (Integer Input)
                .option(Option.<Integer>createBuilder()
                        .name(Component.literal("Background Scan Interval (Minutes)"))
                        .description(dev.isxander.yacl3.api.OptionDescription.of(Component.literal("How often the mod checks for updates in the background. Requires game restart.")))
                        .binding(15, () -> ConfigManager.getConfig().scanIntervalMinutes, val -> ConfigManager.getConfig().scanIntervalMinutes = val)
                        .controller(dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder::create)
                        .build())
                        
                // 4. NEW: API Batch Size (Integer Input)
                .option(Option.<Integer>createBuilder()
                        .name(Component.literal("API Batch Size"))
                        .description(dev.isxander.yacl3.api.OptionDescription.of(Component.literal("How many mods to check at once. Lower this if you experience network timeouts or rate limits.")))
                        .binding(50, () -> ConfigManager.getConfig().apiBatchSize, val -> ConfigManager.getConfig().apiBatchSize = val)
                        .controller(dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder::create)
                        .build())
                        
                .build();
        // --- TAB 3: MONITORED MODS ---
        ConfigCategory.Builder monitoredMods = ConfigCategory.createBuilder()
                .name(Component.literal("Monitored Mods"))
                .tooltip(Component.literal("Uncheck a mod to ignore it during MANUAL scans."));

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
            DownloadManager.downloadMod(update.downloadUrl(), update.newFilename(), (percent, speedMBps) -> {
                Minecraft.getInstance().execute(() -> {
                    String progress = String.format("Downloading %s... %.0f%% (%.1f MB/s)", update.newFilename(), percent, speedMBps);
                    Minecraft.getInstance().gui.setScreen(buildScreen(parent, availableUpdates, "downloading", progress));
                });
            }).thenAccept(path -> {
                StatusWriter.appendUpdate(update.oldFilename(), update.newFilename());
                if (completedCount.incrementAndGet() >= total) {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().gui.setScreen(buildScreen(parent, availableUpdates, "done", "Downloads Complete - RESTART GAME"));
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
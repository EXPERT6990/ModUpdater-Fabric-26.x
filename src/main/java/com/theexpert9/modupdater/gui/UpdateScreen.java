// package com.theexpert9.modupdater.gui;

// import com.theexpert9.modupdater.api.ModrinthClient;
// import com.theexpert9.modupdater.util.ConfigManager;
// import com.theexpert9.modupdater.util.DownloadManager;
// import com.theexpert9.modupdater.util.StatusWriter;
// import dev.isxander.yacl3.api.ButtonOption;
// import dev.isxander.yacl3.api.ConfigCategory;
// import dev.isxander.yacl3.api.Option;
// import dev.isxander.yacl3.api.YetAnotherConfigLib;
// import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
// import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
// import net.fabricmc.loader.api.FabricLoader;
// import net.minecraft.client.Minecraft;
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

// public class UpdateScreen {

//     private record PendingUIUpdate(String projectId, String oldFilename, String newVersion, String downloadUrl, String newFilename) {}
//     private static final Map<String, Boolean> selectedMods = new HashMap<>();

//     public static Screen create(Screen parent) {
//         return buildScreen(parent, new ArrayList<>(), "idle", "Check for Updates");
//     }

//     private static Screen buildScreen(Screen parent, List<PendingUIUpdate> availableUpdates, String state, String statusText) {
//         // --- TAB 1: UPDATES ---
//         ConfigCategory.Builder updatesCategory = ConfigCategory.createBuilder()
//                 .name(Component.literal("Updates"));

//         updatesCategory.option(ButtonOption.createBuilder()
//                 .name(Component.literal(statusText))
//                 .action((screen, buttonOption) -> {
//                     if (state.equals("idle")) checkForUpdates(parent);
//                     else if (state.equals("ready")) downloadSelectedMods(parent, availableUpdates);
//                     else if (state.equals("done")) applyAndRestart();
//                 }).build());

//         for (PendingUIUpdate update : availableUpdates) {
//             selectedMods.putIfAbsent(update.projectId(), true);
//             updatesCategory.option(Option.<Boolean>createBuilder()
//                     .name(Component.literal("📦 " + update.oldFilename() + " ➔ " + update.newFilename()))
//                     .binding(true, () -> selectedMods.get(update.projectId()), val -> selectedMods.put(update.projectId(), val))
//                     .controller(TickBoxControllerBuilder::create)
//                     .build());
//         }

//         // --- TAB 2: SETTINGS ---
//         ConfigCategory settingsCategory = ConfigCategory.createBuilder()
//                 .name(Component.literal("Settings"))
//                 .option(Option.<Boolean>createBuilder()
//                         .name(Component.literal("Enable Startup Notification"))
//                         .binding(true, () -> ConfigManager.getConfig().enableNotifications, val -> ConfigManager.getConfig().enableNotifications = val)
//                         .controller(TickBoxControllerBuilder::create)
//                         .build())
//                 .option(Option.<ConfigManager.AutoCheckMode>createBuilder()
//                         .name(Component.literal("Auto Check Mode"))
//                         .binding(ConfigManager.AutoCheckMode.ALL, () -> ConfigManager.getConfig().autoCheckMode, val -> ConfigManager.getConfig().autoCheckMode = val)
//                         .controller(opt -> EnumControllerBuilder.create(opt).enumClass(ConfigManager.AutoCheckMode.class))
//                         .build())
//                 .build();

//         // --- TAB 3: MONITORED MODS ---
//         ConfigCategory.Builder monitoredMods = ConfigCategory.createBuilder()
//                 .name(Component.literal("Monitored Mods"))
//                 .tooltip(Component.literal("Uncheck a mod to ignore it during MANUAL scans."));

//         try (Stream<Path> stream = Files.list(FabricLoader.getInstance().getGameDir().resolve("mods"))) {
//             stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
//                 String filename = path.getFileName().toString();
//                 if (!filename.startsWith("fabric-") && !filename.equals("updater.jar")) {
//                     monitoredMods.option(Option.<Boolean>createBuilder()
//                             .name(Component.literal(filename))
//                             // Default to TRUE (enabled) unless it is in the ignoredMods list
//                             .binding(true,
//                                     () -> !ConfigManager.getConfig().ignoredMods.contains(filename),
//                                     val -> {
//                                         if (val) ConfigManager.getConfig().ignoredMods.remove(filename);
//                                         else if (!ConfigManager.getConfig().ignoredMods.contains(filename)) {
//                                             ConfigManager.getConfig().ignoredMods.add(filename);
//                                         }
//                                     })
//                             .controller(TickBoxControllerBuilder::create)
//                             .build());
//                 }
//             });
//         } catch (Exception ignored) {}

//         return YetAnotherConfigLib.createBuilder()
//                 .title(Component.literal("Mod Updater"))
//                 .category(updatesCategory.build())
//                 .category(settingsCategory)
//                 .category(monitoredMods.build())
//                 .save(ConfigManager::save)
//                 .build()
//                 .generateScreen(parent);
//     }

//     public static int getAvailableUpdateCountSilent() {
//         try {
//             Map<String, String> hashToFilename = new HashMap<>();
//             ConfigManager.ConfigData config = ConfigManager.getConfig();

//             try (Stream<Path> stream = Files.list(FabricLoader.getInstance().getGameDir().resolve("mods"))) {
//                 stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
//                     String filename = path.getFileName().toString();
//                     if (!filename.startsWith("fabric-") && !filename.equals("updater.jar")) {
//                         // Include if ALL, or if MANUAL and NOT ignored
//                         if (config.autoCheckMode == ConfigManager.AutoCheckMode.ALL || 
//                            (config.autoCheckMode == ConfigManager.AutoCheckMode.MANUAL && !config.ignoredMods.contains(filename))) {
//                             try { hashToFilename.put(getFileHash(path), filename); } catch (Exception ignored) {}
//                         }
//                     }
//                 });
//             }

//             if (hashToFilename.isEmpty()) return 0;

//             Map<String, ModrinthClient.ModVersion> apiResponse = ModrinthClient.checkBulkUpdates(
//                     new ArrayList<>(hashToFilename.keySet()), "26.1.2"
//             ).join();

//             int count = 0;
//             for (Map.Entry<String, ModrinthClient.ModVersion> entry : apiResponse.entrySet()) {
//                 String oldHash = entry.getKey();
//                 List<ModrinthClient.ModFile> files = entry.getValue().files();
//                 if (files == null || files.isEmpty()) continue;

//                 ModrinthClient.ModFile primaryFile = files.stream().filter(ModrinthClient.ModFile::primary).findFirst().orElse(files.get(0));
//                 String newHash = primaryFile.hashes().get("sha1");

//                 if (newHash != null && !oldHash.equals(newHash)) count++;
//             }
//             return count;
//         } catch (Exception e) { return 0; }
//     }

//     private static void checkForUpdates(Screen parent) {
//         selectedMods.clear();
//         Minecraft.getInstance().setScreen(buildScreen(parent, new ArrayList<>(), "checking", "Checking hashes... Please Wait"));

//         CompletableFuture.supplyAsync(() -> {
//             List<PendingUIUpdate> updates = new ArrayList<>();
//             Map<String, String> hashToFilename = new HashMap<>();
//             ConfigManager.ConfigData config = ConfigManager.getConfig();

//             try (Stream<Path> stream = Files.list(FabricLoader.getInstance().getGameDir().resolve("mods"))) {
//                 stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
//                     String filename = path.getFileName().toString();
//                     if (!filename.startsWith("fabric-") && !filename.equals("updater.jar")) {
//                         // Manual button click checks ALL, or MANUAL if not ignored
//                         if (config.autoCheckMode == ConfigManager.AutoCheckMode.ALL || 
//                             config.autoCheckMode == ConfigManager.AutoCheckMode.NONE ||
//                            (config.autoCheckMode == ConfigManager.AutoCheckMode.MANUAL && !config.ignoredMods.contains(filename))) { 
//                             try { hashToFilename.put(getFileHash(path), filename); } catch (Exception ignored) {}
//                         }
//                     }
//                 });
//             } catch (Exception e) { return updates; }

//             if (hashToFilename.isEmpty()) return updates;

//             Map<String, ModrinthClient.ModVersion> apiResponse = ModrinthClient.checkBulkUpdates(new ArrayList<>(hashToFilename.keySet()), "26.1.2").join();

//             for (Map.Entry<String, ModrinthClient.ModVersion> entry : apiResponse.entrySet()) {
//                 String oldHash = entry.getKey();
//                 ModrinthClient.ModVersion newVersionData = entry.getValue();
//                 String oldFilename = hashToFilename.get(oldHash);

//                 if (newVersionData.files() == null || newVersionData.files().isEmpty()) continue;

//                 ModrinthClient.ModFile primaryFile = newVersionData.files().stream().filter(ModrinthClient.ModFile::primary).findFirst().orElse(newVersionData.files().get(0));
//                 String newHash = primaryFile.hashes().get("sha1");

//                 if (newHash != null && !oldHash.equals(newHash)) {
//                     updates.add(new PendingUIUpdate(newVersionData.project_id(), oldFilename, newVersionData.version_number(), primaryFile.url(), primaryFile.filename()));
//                 }
//             }
//             return updates;
//         }).thenAccept(updates -> {
//             Minecraft.getInstance().execute(() -> {
//                 String btnText = updates.isEmpty() ? "All monitored mods up to date!" : "Download Selected Mods";
//                 String nextState = updates.isEmpty() ? "idle" : "ready";
//                 Minecraft.getInstance().setScreen(buildScreen(parent, updates, nextState, btnText));
//             });
//         });
//     }

//     private static void downloadSelectedMods(Screen parent, List<PendingUIUpdate> availableUpdates) {
//         List<PendingUIUpdate> toDownload = availableUpdates.stream()
//                 .filter(u -> selectedMods.getOrDefault(u.projectId(), false))
//                 .toList();

//         if (toDownload.isEmpty()) return;

//         AtomicInteger completedCount = new AtomicInteger(0);
//         int total = toDownload.size();

//         for (PendingUIUpdate update : toDownload) {
//             DownloadManager.downloadMod(update.downloadUrl(), update.newFilename(), (percent, speedMBps) -> {
//                 Minecraft.getInstance().execute(() -> {
//                     String progress = String.format("Downloading %s... %.0f%% (%.1f MB/s)", update.newFilename(), percent, speedMBps);
//                     Minecraft.getInstance().setScreen(buildScreen(parent, availableUpdates, "downloading", progress));
//                 });
//             }).thenAccept(path -> {
//                 StatusWriter.appendUpdate(update.oldFilename(), update.newFilename());
//                 if (completedCount.incrementAndGet() >= total) {
//                     Minecraft.getInstance().execute(() -> {
//                         Minecraft.getInstance().setScreen(buildScreen(parent, availableUpdates, "done", "Downloads Complete - RESTART GAME"));
//                     });
//                 }
//             });
//         }
//     }

//     private static void applyAndRestart() {
//         try {
//             Path pendingDir = DownloadManager.getPendingUpdatesDir();
//             Path updaterPath = pendingDir.resolve("updater.jar");

//             try (InputStream is = UpdateScreen.class.getResourceAsStream("/assets/modupdater/updater.jar")) {
//                 if (is != null) Files.copy(is, updaterPath, StandardCopyOption.REPLACE_EXISTING);
//                 else return;
//             }

//             long pid = ProcessHandle.current().pid();
//             String modsPath = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().toString();
//             String javaBinaryPath = ProcessHandle.current().info().command().orElse("java");

//             Runtime.getRuntime().exec(new String[]{
//                     javaBinaryPath, "-jar", updaterPath.toAbsolutePath().toString(), String.valueOf(pid), modsPath
//             });

//             Minecraft.getInstance().stop();
//         } catch (Exception e) {
//             e.printStackTrace();
//         }
//     }

//     // High-speed SHA-1 hashing algorithm using bitwise masking
//     private static String getFileHash(Path path) throws Exception {
//         MessageDigest digest = MessageDigest.getInstance("SHA-1");
//         try (InputStream is = Files.newInputStream(path)) {
//             byte[] buffer = new byte[8192];
//             int read;
//             while ((read = is.read(buffer)) > 0) {
//                 digest.update(buffer, 0, read);
//             }
//         }
//         StringBuilder hexString = new StringBuilder();
//         for (byte b : digest.digest()) {
//             // Apply & 0xFF to prevent Java from keeping the negative sign on signed bytes
//             hexString.append(String.format("%02x", b & 0xFF)); 
//         }
//         return hexString.toString();
//     }
// }


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
                            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(create(parent)));
                        });
                    }
                    else if (state.equals("ready")) downloadSelectedMods(parent, availableUpdates);
                    else if (state.equals("done")) applyAndRestart();
                }).build());

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

        // --- TAB 2: SETTINGS ---
        ConfigCategory settingsCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Settings"))
                // Keep your existing settings UI hooks
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
                    Minecraft.getInstance().setScreen(buildScreen(parent, availableUpdates, "downloading", progress));
                });
            }).thenAccept(path -> {
                StatusWriter.appendUpdate(update.oldFilename(), update.newFilename());
                if (completedCount.incrementAndGet() >= total) {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(buildScreen(parent, availableUpdates, "done", "Downloads Complete - RESTART GAME"));
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
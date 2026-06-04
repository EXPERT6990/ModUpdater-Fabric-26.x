// package com.theexpert9.modupdater.gui;

// import com.theexpert9.modupdater.api.ModrinthClient;
// import com.theexpert9.modupdater.util.DownloadManager;
// import com.theexpert9.modupdater.util.StatusWriter;
// import dev.isxander.yacl3.api.ButtonOption;
// import dev.isxander.yacl3.api.ConfigCategory;
// import dev.isxander.yacl3.api.Option;
// import dev.isxander.yacl3.api.YetAnotherConfigLib;
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

//     // Notice we now track oldFilename directly for 100% accurate file swapping
//     private record PendingUIUpdate(String projectId, String oldFilename, String newVersion, String downloadUrl, String newFilename) {}

//     private static final Map<String, Boolean> selectedMods = new HashMap<>();

//     public static Screen create(Screen parent) {
//         return buildScreen(parent, new ArrayList<>(), "idle", "Check for Updates");
//     }

//     private static Screen buildScreen(Screen parent, List<PendingUIUpdate> availableUpdates, String state, String statusText) {
//         ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
//                 .name(Component.literal("Available Updates"))
//                 .tooltip(Component.literal("Check Modrinth for updates."));

//         categoryBuilder.option(ButtonOption.createBuilder()
//                 .name(Component.literal(statusText))
//                 .action((screen, buttonOption) -> {
//                     if (state.equals("idle")) checkForUpdates(parent);
//                     else if (state.equals("ready")) downloadSelectedMods(parent, availableUpdates);
//                     else if (state.equals("done")) applyAndRestart();
//                 }).build());

//         if (state.equals("ready") && !availableUpdates.isEmpty()) {
//             categoryBuilder.option(ButtonOption.createBuilder()
//                     .name(Component.literal("Select All / Deselect All"))
//                     .action((screen, buttonOption) -> {
//                         boolean anyFalse = selectedMods.containsValue(false);
//                         for (String key : selectedMods.keySet()) selectedMods.put(key, anyFalse);
//                         Minecraft.getInstance().setScreen(buildScreen(parent, availableUpdates, state, statusText));
//                     }).build());
//         }

//         for (PendingUIUpdate update : availableUpdates) {
//             selectedMods.putIfAbsent(update.projectId(), true);
//             categoryBuilder.option(Option.<Boolean>createBuilder()
//                     .name(Component.literal("📦 " + update.oldFilename() + " ➔ " + update.newFilename()))
//                     .binding(true, () -> selectedMods.get(update.projectId()), val -> selectedMods.put(update.projectId(), val))
//                     .controller(TickBoxControllerBuilder::create)
//                     .build());
//         }

//         return YetAnotherConfigLib.createBuilder()
//                 .title(Component.literal("Mod Updater"))
//                 .category(categoryBuilder.build())
//                 .build()
//                 .generateScreen(parent);
//     }

//     private static void checkForUpdates(Screen parent) {
//         selectedMods.clear();
//         Minecraft.getInstance().setScreen(buildScreen(parent, new ArrayList<>(), "checking", "Checking hashes... Please Wait"));

//         CompletableFuture.supplyAsync(() -> {
//             List<PendingUIUpdate> updates = new ArrayList<>();
//             Map<String, String> hashToFilename = new HashMap<>();
//             Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");

//             // 1. Generate SHA-1 hashes for all local .jar files
//             try (Stream<Path> stream = Files.list(modsDir)) {
//                 stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
//                     try {
//                         String filename = path.getFileName().toString();
//                         // Ignore the updater itself and fabric api fragments
//                         if (!filename.startsWith("fabric-") && !filename.equals("updater.jar")) {
//                             hashToFilename.put(getFileHash(path), filename);
//                         }
//                     } catch (Exception ignored) {}
//                 });
//             } catch (Exception e) {
//                 return updates;
//             }

//             if (hashToFilename.isEmpty()) return updates;

//             // 2. Perform the 1-second bulk API query
//             Map<String, ModrinthClient.ModVersion> apiResponse = ModrinthClient.checkBulkUpdates(
//                     new ArrayList<>(hashToFilename.keySet()), "26.1.2"
//             ).join();

//             // 3. Process the response
//             for (Map.Entry<String, ModrinthClient.ModVersion> entry : apiResponse.entrySet()) {
//                 String oldHash = entry.getKey();
//                 ModrinthClient.ModVersion newVersionData = entry.getValue();
//                 String oldFilename = hashToFilename.get(oldHash);

//                 if (newVersionData.files() == null || newVersionData.files().isEmpty()) continue;

//                 ModrinthClient.ModFile primaryFile = newVersionData.files().stream()
//                         .filter(ModrinthClient.ModFile::primary)
//                         .findFirst()
//                         .orElse(newVersionData.files().get(0));

//                 String newHash = primaryFile.hashes().get("sha1");

//                 // If the cryptographic hash is different, it is a guaranteed update
//                 if (newHash != null && !oldHash.equals(newHash)) {
//                     updates.add(new PendingUIUpdate(
//                             newVersionData.project_id(),
//                             oldFilename,
//                             newVersionData.version_number(),
//                             primaryFile.url(),
//                             primaryFile.filename()
//                     ));
//                 }
//             }
//             return updates;
            
//         }).thenAccept(updates -> {
//             Minecraft.getInstance().execute(() -> {
//                 String btnText = updates.isEmpty() ? "All mods up to date!" : "Download Selected Mods";
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
//                 // Pass the exact old filename to the JSON map
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

//             // Extract the bootstrapper
//             try (InputStream is = UpdateScreen.class.getResourceAsStream("/assets/modupdater/updater.jar")) {
//                 if (is != null) Files.copy(is, updaterPath, StandardCopyOption.REPLACE_EXISTING);
//                 else {
//                     System.err.println("Fatal: Could not find updater.jar in resources.");
//                     return;
//                 }
//             }

//             long pid = ProcessHandle.current().pid();
//             String modsPath = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().toString();

//             // CROSS-PLATFORM FIX: 
//             // Ask the OS for the exact absolute path of the Java binary currently running Minecraft.
//             // If it can't find it (very rare), fallback to the standard "java" command.
//             String javaBinaryPath = ProcessHandle.current()
//                     .info()
//                     .command()
//                     .orElse("java");

//             // Launch the bootstrapper using the isolated launcher's Java runtime
//             Runtime.getRuntime().exec(new String[]{
//                     javaBinaryPath, 
//                     "-jar", 
//                     updaterPath.toAbsolutePath().toString(), 
//                     String.valueOf(pid), 
//                     modsPath
//             });

//             Minecraft.getInstance().stop();
//         } catch (Exception e) {
//             e.printStackTrace();
//         }
//     }

//     // High-speed SHA-1 hashing algorithm using an 8KB buffer
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
//             hexString.append(String.format("%02x", b));
//         }
//         return hexString.toString();
//     }
// }

package com.theexpert9.modupdater.gui;

import com.theexpert9.modupdater.api.ModrinthClient;
import com.theexpert9.modupdater.util.ConfigManager;
import com.theexpert9.modupdater.util.DownloadManager;
import com.theexpert9.modupdater.util.StatusWriter;
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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class UpdateScreen {

    private record PendingUIUpdate(String projectId, String oldFilename, String newVersion, String downloadUrl, String newFilename) {}
    private static final Map<String, Boolean> selectedMods = new HashMap<>();

    public static Screen create(Screen parent) {
        return buildScreen(parent, new ArrayList<>(), "idle", "Check for Updates");
    }

    private static Screen buildScreen(Screen parent, List<PendingUIUpdate> availableUpdates, String state, String statusText) {
        // --- TAB 1: UPDATES ---
        ConfigCategory.Builder updatesCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Updates"));

        updatesCategory.option(ButtonOption.createBuilder()
                .name(Component.literal(statusText))
                .action((screen, buttonOption) -> {
                    if (state.equals("idle")) checkForUpdates(parent);
                    else if (state.equals("ready")) downloadSelectedMods(parent, availableUpdates);
                    else if (state.equals("done")) applyAndRestart();
                }).build());

        for (PendingUIUpdate update : availableUpdates) {
            selectedMods.putIfAbsent(update.projectId(), true);
            updatesCategory.option(Option.<Boolean>createBuilder()
                    .name(Component.literal("📦 " + update.oldFilename() + " ➔ " + update.newFilename()))
                    .binding(true, () -> selectedMods.get(update.projectId()), val -> selectedMods.put(update.projectId(), val))
                    .controller(TickBoxControllerBuilder::create)
                    .build());
        }

        // --- TAB 2: SETTINGS ---
        ConfigCategory settingsCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Settings"))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Enable Startup Notification"))
                        .binding(true, () -> ConfigManager.getConfig().enableNotifications, val -> ConfigManager.getConfig().enableNotifications = val)
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
                            // Default to TRUE (enabled) unless it is in the ignoredMods list
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

    public static int getAvailableUpdateCountSilent() {
        try {
            Map<String, String> hashToFilename = new HashMap<>();
            ConfigManager.ConfigData config = ConfigManager.getConfig();

            try (Stream<Path> stream = Files.list(FabricLoader.getInstance().getGameDir().resolve("mods"))) {
                stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
                    String filename = path.getFileName().toString();
                    if (!filename.startsWith("fabric-") && !filename.equals("updater.jar")) {
                        // Include if ALL, or if MANUAL and NOT ignored
                        if (config.autoCheckMode == ConfigManager.AutoCheckMode.ALL || 
                           (config.autoCheckMode == ConfigManager.AutoCheckMode.MANUAL && !config.ignoredMods.contains(filename))) {
                            try { hashToFilename.put(getFileHash(path), filename); } catch (Exception ignored) {}
                        }
                    }
                });
            }

            if (hashToFilename.isEmpty()) return 0;

            Map<String, ModrinthClient.ModVersion> apiResponse = ModrinthClient.checkBulkUpdates(
                    new ArrayList<>(hashToFilename.keySet()), "26.1.2"
            ).join();

            int count = 0;
            for (Map.Entry<String, ModrinthClient.ModVersion> entry : apiResponse.entrySet()) {
                String oldHash = entry.getKey();
                List<ModrinthClient.ModFile> files = entry.getValue().files();
                if (files == null || files.isEmpty()) continue;

                ModrinthClient.ModFile primaryFile = files.stream().filter(ModrinthClient.ModFile::primary).findFirst().orElse(files.get(0));
                String newHash = primaryFile.hashes().get("sha1");

                if (newHash != null && !oldHash.equals(newHash)) count++;
            }
            return count;
        } catch (Exception e) { return 0; }
    }

    private static void checkForUpdates(Screen parent) {
        selectedMods.clear();
        Minecraft.getInstance().setScreen(buildScreen(parent, new ArrayList<>(), "checking", "Checking hashes... Please Wait"));

        CompletableFuture.supplyAsync(() -> {
            List<PendingUIUpdate> updates = new ArrayList<>();
            Map<String, String> hashToFilename = new HashMap<>();
            ConfigManager.ConfigData config = ConfigManager.getConfig();

            try (Stream<Path> stream = Files.list(FabricLoader.getInstance().getGameDir().resolve("mods"))) {
                stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
                    String filename = path.getFileName().toString();
                    if (!filename.startsWith("fabric-") && !filename.equals("updater.jar")) {
                        // Manual button click checks ALL, or MANUAL if not ignored
                        if (config.autoCheckMode == ConfigManager.AutoCheckMode.ALL || 
                            config.autoCheckMode == ConfigManager.AutoCheckMode.NONE ||
                           (config.autoCheckMode == ConfigManager.AutoCheckMode.MANUAL && !config.ignoredMods.contains(filename))) { 
                            try { hashToFilename.put(getFileHash(path), filename); } catch (Exception ignored) {}
                        }
                    }
                });
            } catch (Exception e) { return updates; }

            if (hashToFilename.isEmpty()) return updates;

            Map<String, ModrinthClient.ModVersion> apiResponse = ModrinthClient.checkBulkUpdates(new ArrayList<>(hashToFilename.keySet()), "26.1.2").join();

            for (Map.Entry<String, ModrinthClient.ModVersion> entry : apiResponse.entrySet()) {
                String oldHash = entry.getKey();
                ModrinthClient.ModVersion newVersionData = entry.getValue();
                String oldFilename = hashToFilename.get(oldHash);

                if (newVersionData.files() == null || newVersionData.files().isEmpty()) continue;

                ModrinthClient.ModFile primaryFile = newVersionData.files().stream().filter(ModrinthClient.ModFile::primary).findFirst().orElse(newVersionData.files().get(0));
                String newHash = primaryFile.hashes().get("sha1");

                if (newHash != null && !oldHash.equals(newHash)) {
                    updates.add(new PendingUIUpdate(newVersionData.project_id(), oldFilename, newVersionData.version_number(), primaryFile.url(), primaryFile.filename()));
                }
            }
            return updates;
        }).thenAccept(updates -> {
            Minecraft.getInstance().execute(() -> {
                String btnText = updates.isEmpty() ? "All monitored mods up to date!" : "Download Selected Mods";
                String nextState = updates.isEmpty() ? "idle" : "ready";
                Minecraft.getInstance().setScreen(buildScreen(parent, updates, nextState, btnText));
            });
        });
    }

    private static void downloadSelectedMods(Screen parent, List<PendingUIUpdate> availableUpdates) {
        List<PendingUIUpdate> toDownload = availableUpdates.stream()
                .filter(u -> selectedMods.getOrDefault(u.projectId(), false))
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

    // High-speed SHA-1 hashing algorithm using bitwise masking
    private static String getFileHash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest.digest()) {
            // Apply & 0xFF to prevent Java from keeping the negative sign on signed bytes
            hexString.append(String.format("%02x", b & 0xFF)); 
        }
        return hexString.toString();
    }
}
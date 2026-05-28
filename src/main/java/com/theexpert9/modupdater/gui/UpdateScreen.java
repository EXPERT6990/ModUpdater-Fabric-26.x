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
// import net.fabricmc.loader.api.ModContainer;
// import net.minecraft.client.Minecraft;
// import net.minecraft.client.gui.screens.Screen;
// import net.minecraft.network.chat.Component;
// import java.io.InputStream;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.StandardCopyOption;
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.atomic.AtomicInteger;

// public class UpdateScreen {

//     private record PendingUIUpdate(String modId, String oldVersion, String newVersion, String downloadUrl, String newFilename) {}

//     // Holds the checkbox states
//     private static final Map<String, Boolean> selectedMods = new HashMap<>();

//     public static Screen create(Screen parent) {
//         return buildScreen(parent, new ArrayList<>(), "idle", "Check for Updates");
//     }

//     private static Screen buildScreen(Screen parent, List<PendingUIUpdate> availableUpdates, String state, String statusText) {
//         ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
//                 .name(Component.literal("Available Updates"))
//                 .tooltip(Component.literal("Check Modrinth for updates."));

//         // MAIN ACTION BUTTON
//         categoryBuilder.option(ButtonOption.createBuilder()
//                 .name(Component.literal(statusText))
//                 .action((screen, buttonOption) -> {
//                     if (state.equals("idle")) {
//                         checkForUpdates(parent);
//                     } else if (state.equals("ready")) {
//                         downloadSelectedMods(parent, availableUpdates);
//                     } else if (state.equals("done")) {
//                         applyAndRestart();
//                     }
//                 })
//                 .build());

//         // BATCH SELECT BUTTONS (Only show when ready)
//         if (state.equals("ready") && !availableUpdates.isEmpty()) {
//             categoryBuilder.option(ButtonOption.createBuilder()
//                     .name(Component.literal("Select All / Deselect All"))
//                     .action((screen, buttonOption) -> {
//                         boolean anyFalse = selectedMods.containsValue(false);
//                         for (String key : selectedMods.keySet()) {
//                             selectedMods.put(key, anyFalse);
//                         }
//                         // Refresh UI to show checked boxes
//                         Minecraft.getInstance().setScreen(buildScreen(parent, availableUpdates, state, statusText));
//                     })
//                     .build());
//         }

//         // GENERATE CHECKBOXES FOR EACH MOD
//         for (PendingUIUpdate update : availableUpdates) {
//             // Default to true if not in map
//             selectedMods.putIfAbsent(update.modId(), true);

//             categoryBuilder.option(Option.<Boolean>createBuilder()
//                     .name(Component.literal("📦 " + update.modId() + " (" + update.oldVersion() + " ➔ " + update.newVersion() + ")"))
//                     .binding(
//                             true,
//                             () -> selectedMods.get(update.modId()),
//                             val -> selectedMods.put(update.modId(), val)
//                     )
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
//         Minecraft.getInstance().setScreen(buildScreen(parent, new ArrayList<>(), "checking", "Checking... Please Wait"));

//         CompletableFuture.supplyAsync(() -> {
//             List<PendingUIUpdate> updates = new ArrayList<>();
//             String gameVersion = "26.1.2";

//             for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
//                 String modId = mod.getMetadata().getId();
//                 String currentVersion = mod.getMetadata().getVersion().getFriendlyString();

//                  if (modId.equals("minecraft") || modId.equals("java") || modId.startsWith("fabric")) continue;

//                 try {
//                     ModrinthClient.ModVersion[] versions = ModrinthClient.getLatestVersions(modId, gameVersion).join();

//                     // Strip build metadata (everything after the '+') for an accurate comparison
//                     String cleanCurrent = currentVersion.split("\\+")[0];
//                     String cleanLatest = versions[0].version_number().split("\\+")[0];

//                     if (versions != null && versions.length > 0 && !cleanCurrent.equals(cleanLatest)) {
//                         ModrinthClient.ModFile primaryFile = versions[0].files().get(0);
//                         updates.add(new PendingUIUpdate(modId, currentVersion, versions[0].version_number(), primaryFile.url(), primaryFile.filename()));
//                     }
//                 } catch (Exception ignored) {}
//             }
//             return updates;
//         }).thenAccept(updates -> {
//             Minecraft.getInstance().execute(() -> {
//                 String btnText = updates.isEmpty() ? "No updates found." : "Download Selected Mods";
//                 String nextState = updates.isEmpty() ? "idle" : "ready";
//                 Minecraft.getInstance().setScreen(buildScreen(parent, updates, nextState, btnText));
//             });
//         });
//     }

//     private static void downloadSelectedMods(Screen parent, List<PendingUIUpdate> availableUpdates) {
//         List<PendingUIUpdate> toDownload = availableUpdates.stream()
//                 .filter(u -> selectedMods.getOrDefault(u.modId(), false))
//                 .toList();

//         if (toDownload.isEmpty()) return;

//         AtomicInteger completedCount = new AtomicInteger(0);
//         int total = toDownload.size();

//         for (PendingUIUpdate update : toDownload) {
//             DownloadManager.downloadMod(update.downloadUrl(), update.newFilename(), (percent, speedMBps) -> {
//                 // Update the UI text dynamically on the main thread
//                 Minecraft.getInstance().execute(() -> {
//                     String progress = String.format("Downloading %s... %.0f%% (%.1f MB/s)", update.modId(), percent, speedMBps);
//                     Minecraft.getInstance().setScreen(buildScreen(parent, availableUpdates, "downloading", progress));
//                 });
//             }).thenAccept(path -> {
//                 StatusWriter.appendUpdate(update.modId() + "-" + update.oldVersion() + ".jar", update.newFilename());
                
//                 if (completedCount.incrementAndGet() >= total) {
//                     Minecraft.getInstance().execute(() -> {
//                         Minecraft.getInstance().setScreen(buildScreen(parent, availableUpdates, "done", "Downloads Complete - RESTART GAME"));
//                     });
//                 }
//             });
//         }
//     }

//     // private static void applyAndRestart() {
//     //     try {
//     //         long pid = ProcessHandle.current().pid();
//     //         String modsPath = FabricLoader.getInstance().getGameDir().resolve("mods").toString();

//     //         // Run bootstrapper
//     //         Runtime.getRuntime().exec(new String[]{
//     //                 "java", "-jar", "mods/.pending_updates/updater.jar", String.valueOf(pid), modsPath
//     //         });

//     //         // Gracefully stop Minecraft
//     //         Minecraft.getInstance().stop();
//     //     } catch (Exception e) {
//     //         e.printStackTrace();
//     //     }
//     // }
//     private static void applyAndRestart() {
//     try {
//         Path pendingDir = DownloadManager.getPendingUpdatesDir();
//         Path updaterPath = pendingDir.resolve("updater.jar");

//         // Extract the updater.jar from the mod's internal resources
//         try (InputStream is = UpdateScreen.class.getResourceAsStream("/assets/modupdater/updater.jar")) {
//             if (is != null) {
//                 Files.copy(is, updaterPath, StandardCopyOption.REPLACE_EXISTING);
//             } else {
//                 System.err.println("Fatal: Could not find updater.jar in resources.");
//                 return;
//             }
//         }

//         long pid = ProcessHandle.current().pid();
//         String modsPath = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().toString();

//         // Launch the extracted jar using absolute paths
//         Runtime.getRuntime().exec(new String[]{
//                 "java", "-jar", updaterPath.toAbsolutePath().toString(), String.valueOf(pid), modsPath
//         });

//         Minecraft.getInstance().stop();
//     } catch (Exception e) {
//         e.printStackTrace();
//     }
// }
// }


package com.theexpert9.modupdater.gui;

import com.theexpert9.modupdater.api.ModrinthClient;
import com.theexpert9.modupdater.util.DownloadManager;
import com.theexpert9.modupdater.util.StatusWriter;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
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

    // Notice we now track oldFilename directly for 100% accurate file swapping
    private record PendingUIUpdate(String projectId, String oldFilename, String newVersion, String downloadUrl, String newFilename) {}

    private static final Map<String, Boolean> selectedMods = new HashMap<>();

    public static Screen create(Screen parent) {
        return buildScreen(parent, new ArrayList<>(), "idle", "Check for Updates");
    }

    private static Screen buildScreen(Screen parent, List<PendingUIUpdate> availableUpdates, String state, String statusText) {
        ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
                .name(Component.literal("Available Updates"))
                .tooltip(Component.literal("Check Modrinth for updates."));

        categoryBuilder.option(ButtonOption.createBuilder()
                .name(Component.literal(statusText))
                .action((screen, buttonOption) -> {
                    if (state.equals("idle")) checkForUpdates(parent);
                    else if (state.equals("ready")) downloadSelectedMods(parent, availableUpdates);
                    else if (state.equals("done")) applyAndRestart();
                }).build());

        if (state.equals("ready") && !availableUpdates.isEmpty()) {
            categoryBuilder.option(ButtonOption.createBuilder()
                    .name(Component.literal("Select All / Deselect All"))
                    .action((screen, buttonOption) -> {
                        boolean anyFalse = selectedMods.containsValue(false);
                        for (String key : selectedMods.keySet()) selectedMods.put(key, anyFalse);
                        Minecraft.getInstance().setScreen(buildScreen(parent, availableUpdates, state, statusText));
                    }).build());
        }

        for (PendingUIUpdate update : availableUpdates) {
            selectedMods.putIfAbsent(update.projectId(), true);
            categoryBuilder.option(Option.<Boolean>createBuilder()
                    .name(Component.literal("📦 " + update.oldFilename() + " ➔ " + update.newFilename()))
                    .binding(true, () -> selectedMods.get(update.projectId()), val -> selectedMods.put(update.projectId(), val))
                    .controller(TickBoxControllerBuilder::create)
                    .build());
        }

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Mod Updater"))
                .category(categoryBuilder.build())
                .build()
                .generateScreen(parent);
    }

    private static void checkForUpdates(Screen parent) {
        selectedMods.clear();
        Minecraft.getInstance().setScreen(buildScreen(parent, new ArrayList<>(), "checking", "Checking hashes... Please Wait"));

        CompletableFuture.supplyAsync(() -> {
            List<PendingUIUpdate> updates = new ArrayList<>();
            Map<String, String> hashToFilename = new HashMap<>();
            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");

            // 1. Generate SHA-1 hashes for all local .jar files
            try (Stream<Path> stream = Files.list(modsDir)) {
                stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
                    try {
                        String filename = path.getFileName().toString();
                        // Ignore the updater itself and fabric api fragments
                        if (!filename.startsWith("fabric-") && !filename.equals("updater.jar")) {
                            hashToFilename.put(getFileHash(path), filename);
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                return updates;
            }

            if (hashToFilename.isEmpty()) return updates;

            // 2. Perform the 1-second bulk API query
            Map<String, ModrinthClient.ModVersion> apiResponse = ModrinthClient.checkBulkUpdates(
                    new ArrayList<>(hashToFilename.keySet()), "26.1.2"
            ).join();

            // 3. Process the response
            for (Map.Entry<String, ModrinthClient.ModVersion> entry : apiResponse.entrySet()) {
                String oldHash = entry.getKey();
                ModrinthClient.ModVersion newVersionData = entry.getValue();
                String oldFilename = hashToFilename.get(oldHash);

                if (newVersionData.files() == null || newVersionData.files().isEmpty()) continue;

                ModrinthClient.ModFile primaryFile = newVersionData.files().stream()
                        .filter(ModrinthClient.ModFile::primary)
                        .findFirst()
                        .orElse(newVersionData.files().get(0));

                String newHash = primaryFile.hashes().get("sha1");

                // If the cryptographic hash is different, it is a guaranteed update
                if (newHash != null && !oldHash.equals(newHash)) {
                    updates.add(new PendingUIUpdate(
                            newVersionData.project_id(),
                            oldFilename,
                            newVersionData.version_number(),
                            primaryFile.url(),
                            primaryFile.filename()
                    ));
                }
            }
            return updates;
            
        }).thenAccept(updates -> {
            Minecraft.getInstance().execute(() -> {
                String btnText = updates.isEmpty() ? "All mods up to date!" : "Download Selected Mods";
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
                // Pass the exact old filename to the JSON map
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

            Runtime.getRuntime().exec(new String[]{
                    "java", "-jar", updaterPath.toAbsolutePath().toString(), String.valueOf(pid), modsPath
            });

            Minecraft.getInstance().stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // High-speed SHA-1 hashing algorithm using an 8KB buffer
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
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
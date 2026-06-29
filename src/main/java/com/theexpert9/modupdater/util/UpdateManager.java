package com.theexpert9.modupdater.util;

import com.theexpert9.modupdater.api.ModrinthClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
// import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.Minecraft;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
// import java.util.stream.Collectors;

public class UpdateManager {
    // 1. Core State Memory (Instant access for the UI)
    public static final Map<String, CachedUpdate> AVAILABLE_UPDATES = new ConcurrentHashMap<>();
    private static final Set<String> IGNORED_HASHES = ConcurrentHashMap.newKeySet(); // The Negative Cache
    
    // 2. Threading & Timers
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static volatile boolean isScanning = false;
    public static volatile String currentStatus = "Waiting to scan...";

    // A simple record to hold everything the UI needs instantly
    public record CachedUpdate(ModContainer localMod, ModrinthClient.ModVersion newVersion, String primaryFilename, String downloadUrl) {}

    // Called ONCE when the game boots (inside your Mod Initializer)
    public static void startPeriodicScanner() {
        // Read your config for the interval (Default: 15 mins)
        int intervalMins = ConfigManager.getConfig().scanIntervalMinutes;

        SCHEDULER.scheduleAtFixedRate(() -> performScan(false), 0, intervalMins, TimeUnit.MINUTES);
    }
    
    public static void shutdown() {
        if (SCHEDULER != null && !SCHEDULER.isShutdown()) SCHEDULER.shutdown();
    }

    // Called by the UI's "Refresh" button
    public static CompletableFuture<Void> forceRefresh() {
        return CompletableFuture.runAsync(() -> performScan(true));
    }

    private static void performScan(boolean isManual) {
        if (isScanning) return;
        isScanning = true;
        
        try {
            updateStatus("Preparing mod list...");
            Map<String, ModContainer> hashToMod = new HashMap<>();
            
            // 1. Gather Mods & Hash them ONCE in the background
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                String id = mod.getMetadata().getId();
                if (id.equals("fabricloader") || id.equals("java") || id.equals("minecraft")) continue;

                // --- NEW FIX: Skip embedded "Nested" library mods! ---
                if (mod.getOrigin().getKind() != net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH) {
                    continue; 
                }
                // -----------------------------------------------------

                for (Path path : mod.getOrigin().getPaths()) {
                    if (path.toString().endsWith(".jar") && !path.getFileName().toString().equals("updater.jar")) {
                        try {
                            String hash = getFileHash(path);
                            // Only add if it's not in the Negative Cache!
                            if (!IGNORED_HASHES.contains(hash)) {
                                hashToMod.put(hash, mod);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (hashToMod.isEmpty()) {
                updateStatus("No monitored mods found.");
                return;
            }

            // 2. The Batching Engine (Rate Limit Protection)
            String mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("26.1"); // Default to 1.26.1 if not found

            List<String> allHashes = new ArrayList<>(hashToMod.keySet());
            int batchSize = 50; // Check 50 mods at a time
            Map<String, ModrinthClient.ModVersion> allApiResults = new HashMap<>();

            for (int i = 0; i < allHashes.size(); i += batchSize) {
                // WRAPPED IN 'new ArrayList<>()' TO PREVENT THE JSON CRASH!
                List<String> batch = new ArrayList<>(allHashes.subList(i, Math.min(i + batchSize, allHashes.size())));
                
                updateStatus("Checking updates (Batch " + ((i / batchSize) + 1) + ")...");
                
                Map<String, ModrinthClient.ModVersion> batchResult = ModrinthClient.checkBulkUpdates(batch, mcVersion).join();
                if (batchResult != null) allApiResults.putAll(batchResult);

                if (i + batchSize < allHashes.size()) Thread.sleep(600); 
            }

            // 3. Process Results & Build In-Memory UI Cache
            AVAILABLE_UPDATES.clear();
            
            for (String hash : allHashes) {
                if (!allApiResults.containsKey(hash)) {
                    // This mod isn't on Modrinth! Add to Negative Cache so we NEVER hash/check it again.
                    IGNORED_HASHES.add(hash);
                } else {
                    ModrinthClient.ModVersion newVer = allApiResults.get(hash);
                    ModContainer localMod = hashToMod.get(hash);
                    
                    if (newVer.files() == null || newVer.files().isEmpty()) continue;
                    ModrinthClient.ModFile primaryFile = newVer.files().stream().filter(ModrinthClient.ModFile::primary).findFirst().orElse(newVer.files().get(0));
                    
                    String newHash = primaryFile.hashes().get("sha1");
                    if (newHash != null && !hash.equals(newHash)) {
                        // We found an update! Store it cleanly for the UI.
                        AVAILABLE_UPDATES.put(localMod.getMetadata().getId(), new CachedUpdate(localMod, newVer, primaryFile.filename(), primaryFile.url()));
                    }
                }
            }

            updateStatus("Found " + AVAILABLE_UPDATES.size() + " updates.");

            // Done ** TODO (Optional): Send a push notification/Toast to the player here if updates > 0 and isManual == false
            // Trigger a Minecraft Toast Notification if we found updates during a background scan!
            if (!isManual && !AVAILABLE_UPDATES.isEmpty()) {
                Minecraft client = Minecraft.getInstance();
                if (client != null) {
                    client.execute(() -> {
                        net.minecraft.client.gui.components.toasts.SystemToast.add(
                                client.gui.toastManager(),
                                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                net.minecraft.network.chat.Component.literal("Mod Updates Available"),
                                net.minecraft.network.chat.Component.literal("Found " + AVAILABLE_UPDATES.size() + " updates to install!")
                        );
                    });
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("§cScan failed: " + e.getMessage());
        } finally {
            isScanning = false;
        }
    }

    private static void updateStatus(String msg) {
        currentStatus = msg;
    }

    private static String getFileHash(Path path) throws Exception {
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
}
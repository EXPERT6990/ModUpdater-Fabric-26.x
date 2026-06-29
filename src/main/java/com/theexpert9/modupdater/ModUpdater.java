package com.theexpert9.modupdater;

import com.mojang.blaze3d.platform.InputConstants;
import com.theexpert9.modupdater.gui.UpdateScreen;
import com.theexpert9.modupdater.util.ConfigManager;
import com.theexpert9.modupdater.util.DownloadManager;
import com.theexpert9.modupdater.util.UpdateManager;
import com.theexpert9.modupdater.gui.CustomUpdateScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class ModUpdater implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("modupdater");

    public static final KeyMapping.Category UPDATER_CATEGORY = KeyMapping.Category.register(
            Identifier.tryBuild("modupdater", "general")
    );

    @Override
    public void onInitializeClient() {
        //Runtime.getRuntime().addShutdownHook(new Thread(DownloadManager::shutdown));
        LOGGER.info("ModUpdater initialized!");
        UpdateManager.startPeriodicScanner();
        ConfigManager.load(); // Load user settings

        // 1. Register the KeyBinding (Default: 'U' key)
        KeyMapping openUpdaterKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "Update Screen", 
                org.lwjgl.glfw.GLFW.GLFW_KEY_U, // Default key
                UPDATER_CATEGORY
            )
        );

        // 2. Listen for the key press in the Client Tick Event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openUpdaterKey.consumeClick()) {
                // If they are not already in a menu, open the Updater!
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new com.theexpert9.modupdater.gui.CustomUpdateScreen(null));
                }
            }
        });
        
        KeyMapping openUpdateScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "ModUpdaterFabric Settings",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F12,
                UPDATER_CATEGORY
        ));

        // Background Check on Game Launch
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ConfigManager.ConfigData config = ConfigManager.getConfig();
            
            // Skip checking entirely if set to NONE
            if (config.autoCheckMode == ConfigManager.AutoCheckMode.NONE) return;

            CompletableFuture.runAsync(() -> {
                int updates = UpdateScreen.getAvailableUpdateCountSilent();
                if (updates > 0 && config.enableNotifications) {
                    client.execute(() -> {
                        Component title = Component.literal("Mod Updater");
                        // Dynamically fetches whatever key the user bound it to (default 'U')
                        String keyName = openUpdateScreenKey.getTranslatedKeyMessage().getString();
                        Component message = Component.literal(updates + " updates available! Press [" + keyName + "] to view.");
                        
                        client.gui.toastManager().addToast(new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message));
                    });
                }
            });
        });

        // ClientTickEvents.END_CLIENT_TICK.register(client -> {
        //     while (openUpdateScreenKey.consumeClick()) {
        //         if (client.gui.screen() == null) {
        //             if (FabricLoader.getInstance().isModLoaded("yet-another-config-lib_v3")
        //                     && FabricLoader.getInstance().isModLoaded("modmenu")) {
        //                 client.gui.setScreen(UpdateScreen.create(null));
        //             }
        //             else {
        //                 client.gui.setScreen(new CustomUpdateScreen(null));
        //             }
        //         }
        //     }
        // });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openUpdateScreenKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    boolean hasYacl = FabricLoader.getInstance().isModLoaded("yet-another-config-lib_v3");
                    boolean hasModMenu = FabricLoader.getInstance().isModLoaded("modmenu");

                    if (hasYacl && hasModMenu) {
                        // SAFELY route through the router to isolate YACL imports!
                        client.gui.setScreen(com.theexpert9.modupdater.util.YaclScreenRouter.createYaclScreen(null));
                    } else {
                        // Safe native fallback UI
                        client.gui.setScreen(new CustomUpdateScreen(null));
                    }
                }
            }
        });

         ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            // 1. Kill the active download workers
            DownloadManager.shutdown();
            
             // 2. Kill the 15-minute background update timer
            UpdateManager.shutdown();
        });
    }
    
}
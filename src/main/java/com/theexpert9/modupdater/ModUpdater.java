package com.theexpert9.modupdater;

import com.mojang.blaze3d.platform.InputConstants;
import com.theexpert9.modupdater.gui.UpdateScreen;
import com.theexpert9.modupdater.util.ConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
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
        LOGGER.info("ModUpdater initialized!");
        //UpdateManager.startPeriodicScanner();
        ConfigManager.load(); // Load user settings

        KeyMapping openUpdateScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.modupdater.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
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
                        
                        client.getToastManager().addToast(new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message));
                    });
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openUpdateScreenKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(UpdateScreen.create(null));
                }
            }
        });
    }

}
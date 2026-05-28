// package com.theexpert9.modupdater;

// import com.mojang.blaze3d.platform.InputConstants;
// import com.theexpert9.modupdater.gui.UpdateScreen;
// import net.fabricmc.api.ClientModInitializer;
// import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
// import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
// import net.minecraft.client.KeyMapping;
// import org.lwjgl.glfw.GLFW;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// public class ModUpdater implements ClientModInitializer {
//     public static final Logger LOGGER = LoggerFactory.getLogger("modupdater");

//     @Override
//     public void onInitializeClient() {
//         LOGGER.info("ModUpdater initialized!");

//         // Register the keybind (Default: 'U')
//         KeyMapping openUpdateScreenKey = KeyMappingHelper.registerKeyBinding(new KeyMapping(
//                 "key.modupdater.open",
//                 InputConstants.Type.KEYSYM,
//                 GLFW.GLFW_KEY_U,
//                 "category.modupdater.general"
//         ));

//         // Listen for the key press every client tick
//         ClientTickEvents.END_CLIENT_TICK.register(client -> {
//             while (openUpdateScreenKey.consumeClick()) {
//                 if (client.screen == null) { // Only open if no other menu is open
//                     client.setScreen(UpdateScreen.create(null));
//                 }
//             }
//         });
//     }
// }


package com.theexpert9.modupdater;

import com.mojang.blaze3d.platform.InputConstants;
import com.theexpert9.modupdater.gui.UpdateScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModUpdater implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("modupdater");

    // 1. Register the strongly-typed Category object
    public static final KeyMapping.Category UPDATER_CATEGORY = KeyMapping.Category.register(
            Identifier.tryBuild("modupdater", "general")
    );


    @Override
    public void onInitializeClient() {
        LOGGER.info("ModUpdater initialized!");

        // 2. Pass the Category object instead of the raw String
        KeyMapping openUpdateScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.modupdater.open", // Translation key for the keybind
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                UPDATER_CATEGORY // Pass the object here
        ));

        // Listen for the key press every client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openUpdateScreenKey.consumeClick()) {
                if (client.screen == null) { 
                    client.setScreen(UpdateScreen.create(null));
                }
            }
        });
    }
}
package com.theexpert9.modupdater.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
// import net.minecraft.client.gui.screens.Screen;
import net.fabricmc.loader.api.FabricLoader;
import com.theexpert9.modupdater.util.YaclScreenRouter;
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // When the user clicks the "Config" button in Mod Menu, it launches our YACL screen
        // return UpdateScreen::create;

        boolean hasYacl = FabricLoader.getInstance().isModLoaded("yet-another-config-lib_v3");

        if (hasYacl) {
            // Only touches the isolated class if YACL is present
            return parent -> YaclScreenRouter.createYaclScreen(parent);
        } else {
            // If YACL is missing, load your pure Java fallback UI
            return parent -> new CustomUpdateScreen(parent);
        }
    }
}
package com.theexpert9.modupdater.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // When the user clicks the "Config" button in Mod Menu, it launches our YACL screen
        return UpdateScreen::create;
    }
}
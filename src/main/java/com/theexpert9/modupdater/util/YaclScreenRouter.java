package com.theexpert9.modupdater.util;

import com.theexpert9.modupdater.gui.UpdateScreen;

import net.minecraft.client.gui.screens.Screen;

public class YaclScreenRouter {
    // Keep ALL YACL imports and code strictly inside this class!
    public static Screen createYaclScreen(Screen parent) {
        return UpdateScreen.create(parent); // Your existing YACL builder
    }
}

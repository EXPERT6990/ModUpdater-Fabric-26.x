package com.theexpert9.modupdater.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("modupdater.json");

    // The data structure saved to modupdater.json
    public static class ConfigData {
        public boolean enableNotifications = true;
        public AutoCheckMode autoCheckMode = AutoCheckMode.ALL;
        public List<String> selectedMods = new ArrayList<>();
    }

    public enum AutoCheckMode {
        ALL, NONE, MANUAL
    }

    private static ConfigData config = new ConfigData();

    public static ConfigData getConfig() { return config; }

    public static void load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, ConfigData.class);
                if (config.selectedMods == null) config.selectedMods = new ArrayList<>();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        save(); // Ensure the file is created on first launch
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
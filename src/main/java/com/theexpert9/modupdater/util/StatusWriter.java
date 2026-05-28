 
package com.theexpert9.modupdater.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StatusWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Data structures that match the JSON format
    public static class UpdateRecord {
        public String oldFile;
        public String newFile;

        public UpdateRecord(String oldFile, String newFile) {
            this.oldFile = oldFile;
            this.newFile = newFile;
        }
    }

    public static class UpdateState {
        public List<UpdateRecord> updates = new ArrayList<>();
        public List<String> newDependencies = new ArrayList<>();
    }

    private static Path getStatusFile() {
        return DownloadManager.getPendingUpdatesDir().resolve("update_status.json");
    }

    /**
     * Reads the current JSON state from disk, or creates an empty one if missing.
     */
    private static UpdateState readState() {
        Path statusFile = getStatusFile();
        if (!Files.exists(statusFile)) {
            return new UpdateState();
        }
        
        try (Reader reader = Files.newBufferedReader(statusFile)) {
            UpdateState state = GSON.fromJson(reader, UpdateState.class);
            return state != null ? state : new UpdateState();
        } catch (Exception e) {
            e.printStackTrace();
            return new UpdateState();
        }
    }

    /**
     * Safely appends an update record to the JSON file. 
     * Called ONLY after a successful download.
     */
    public static synchronized void appendUpdate(String oldFilename, String newFilename) {
        UpdateState state = readState();
        
        // Remove existing entry for the same mod to prevent duplicates
        state.updates.removeIf(record -> record.oldFile.equals(oldFilename));
        state.updates.add(new UpdateRecord(oldFilename, newFilename));
        
        writeState(state);
    }

    /**
     * Safely appends a new dependency to the JSON file.
     * Called ONLY after a successful download.
     */
    public static synchronized void appendDependency(String newFilename) {
        UpdateState state = readState();
        
        if (!state.newDependencies.contains(newFilename)) {
            state.newDependencies.add(newFilename);
            writeState(state);
        }
    }

    private static void writeState(UpdateState state) {
        try (Writer writer = Files.newBufferedWriter(getStatusFile())) {
            GSON.toJson(state, writer);
        } catch (Exception e) {
            System.err.println("Failed to write to update_status.json");
            e.printStackTrace();
        }
    }
}
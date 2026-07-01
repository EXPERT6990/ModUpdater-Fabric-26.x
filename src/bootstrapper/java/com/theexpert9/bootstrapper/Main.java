package com.theexpert9.bootstrapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) return;

        long pid = Long.parseLong(args[0]);
        Path modsDir = Paths.get(args[1]);
        Path pendingDir = Paths.get(args[2]);
        Path statusFile = pendingDir.resolve("update_status.json");

        Optional<ProcessHandle> mcProcess = ProcessHandle.of(pid);
        mcProcess.ifPresent(processHandle -> processHandle.onExit().join());

        try {
            if (Files.exists(statusFile)) {
                String json = Files.readString(statusFile);
                
                // Extract oldFile and newFile using Regex
                Pattern pattern = Pattern.compile("\"oldFile\":\\s*\"([^\"]+)\",\\s*\"newFile\":\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(json);

                while (matcher.find()) {
                    String oldFile = matcher.group(1);
                    String newFile = matcher.group(2);

                    Path oldFilePath = modsDir.resolve(oldFile);
                    Path newFilePath = pendingDir.resolve(newFile);

                    if (Files.exists(newFilePath)) {
                        Files.deleteIfExists(oldFilePath);
                        Files.move(newFilePath, modsDir.resolve(newFile), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            
            // Clean up the .pending_updates folder
            deleteDirectory(pendingDir.toFile());

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    private static void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }
}
// package com.theexpert9.bootstrapper;

// import java.io.File;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.nio.file.StandardCopyOption;
// import java.util.Optional;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;

// public class Main {
//     public static void main(String[] args) {
//         if (args.length < 2) return;

//         long pid = Long.parseLong(args[0]);
//         Path modsDir = Paths.get(args[1]);
//         Path pendingDir = Paths.get(args[2]);
//         Path statusFile = pendingDir.resolve("update_status.json");

//         Optional<ProcessHandle> mcProcess = ProcessHandle.of(pid);
//         mcProcess.ifPresent(processHandle -> processHandle.onExit().join());

//         try {
//             if (Files.exists(statusFile)) {
//                 String json = Files.readString(statusFile);
                
//                 // Extract oldFile and newFile using Regex
//                 Pattern pattern = Pattern.compile("\"oldFile\":\\s*\"([^\"]+)\",\\s*\"newFile\":\\s*\"([^\"]+)\"");
//                 Matcher matcher = pattern.matcher(json);

//                 while (matcher.find()) {
//                     String oldFile = matcher.group(1);
//                     String newFile = matcher.group(2);

//                     Path oldFilePath = modsDir.resolve(oldFile);
//                     Path newFilePath = pendingDir.resolve(newFile);

//                     if (Files.exists(newFilePath)) {
//                         Files.deleteIfExists(oldFilePath);
//                         Files.move(newFilePath, modsDir.resolve(newFile), StandardCopyOption.REPLACE_EXISTING);
//                     }
//                 }
//             }
            
//             // Clean up the .pending_updates folder
//             deleteDirectory(pendingDir.toFile());

//         } catch (Exception e) {
//             e.printStackTrace();
//         }
//         System.exit(0);
//     }

//     private static void deleteDirectory(File directoryToBeDeleted) {
//         File[] allContents = directoryToBeDeleted.listFiles();
//         if (allContents != null) {
//             for (File file : allContents) {
//                 deleteDirectory(file);
//             }
//         }
//         directoryToBeDeleted.delete();
//     }
// }

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
            // 1. Process Mod Updates
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
            Files.deleteIfExists(statusFile);
            // 2. Process Brand New Mods (Easy-Install)
            MovedNewMods(modsDir);

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    private static void MovedNewMods(Path modsDir) {
        try {
            // Traverse up from /mods/ to /.minecraft/, then into config/modupdater/downloads/
            Path configDir = modsDir.getParent().resolve("config");
            Path downloadsDir = configDir.resolve("modupdater").resolve("downloads");
            Path downloadsJson = downloadsDir.resolve("download.json");

            if (Files.exists(downloadsJson)) {
                String json = Files.readString(downloadsJson);

                // Regex extracts any full file path ending in .jar from the JSON array
                Pattern pattern = Pattern.compile("\"([^\"]+\\.jar)\"");
                Matcher matcher = pattern.matcher(json);

                while (matcher.find()) {
                    String filePathString = matcher.group(1);
                    Path sourcePath = Paths.get(filePathString);

                    if (Files.exists(sourcePath)) {
                        // Extract just the file name (e.g. "sodium-fabric-0.9.1.jar") and map it to the mods folder
                        Path targetPath = modsDir.resolve(sourcePath.getFileName());
                        
                        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("Moved new mod: " + sourcePath.getFileName());
                    }
                }
                
                // Clean up the downloads.json file after processing
                Files.deleteIfExists(downloadsJson);
            }
            
            // Clean up the downloads directory 
            deleteDirectory(downloadsDir.toFile());

        } catch (Exception e) {
            System.err.println("Failed to move new Easy-Install mods.");
            e.printStackTrace();
        }
    }

    private static void deleteDirectory(File directoryToBeDeleted) {
        // Added a quick safety check to ensure it doesn't crash if the directory is already gone
        if (!directoryToBeDeleted.exists()) return; 
        
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }
}
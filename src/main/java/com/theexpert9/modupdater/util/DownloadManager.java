 
package com.theexpert9.modupdater.util;

import net.fabricmc.loader.api.FabricLoader;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
// import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadManager {
    // Strictly limits concurrent downloads to 5 threads as requested
    private static final ExecutorService DOWNLOAD_POOL = Executors.newFixedThreadPool(5);


    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .executor(DOWNLOAD_POOL)
            .build();
    public static void shutdown() {
        if (DOWNLOAD_POOL != null && !DOWNLOAD_POOL.isShutdown()) {
            DOWNLOAD_POOL.shutdownNow();
        }
        if (HTTP_CLIENT != null) {
            HTTP_CLIENT.close();
        }
    }

    /**
     * Locates or creates the mods/.pending_updates/ directory.
     */
    public static Path getPendingUpdatesDir() {
        // Fabric safely resolves the game directory regardless of OS
        //Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        Path pendingDir = FabricLoader.getInstance().getConfigDir()
            .resolve("modupdater")
            .resolve("pending_updates");
        
        try {
            if (!Files.exists(pendingDir)) {
                Files.createDirectories(pendingDir);
            }
        } catch (Exception e) {
            System.err.println("Failed to create pending_updates directory!");
            e.printStackTrace();
        }
        return pendingDir;
    }

    /**
     * Downloads a file asynchronously.
     * @param url The direct download URL
     * @param filename The exact filename to save it as
     * @return A CompletableFuture tracking the download state
     */
   // Add this interface to the top of DownloadManager.java
    public interface DownloadProgressListener {
        void onProgress(double percent, double speedMBps);
    }

    public static CompletableFuture<Path> downloadMod(String url, String filename, DownloadProgressListener listener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path targetFile = getPendingUpdatesDir().resolve(filename);
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

                // Get the input stream instead of saving directly to file
                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() == 200) {
                    long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    
                    try (InputStream is = response.body(); 
                         var os = Files.newOutputStream(targetFile)) {
                        
                        byte[] buffer = new byte[8192]; // 8KB buffer
                        long downloadedBytes = 0;
                        int bytesRead;
                        long startTime = System.currentTimeMillis();

                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                            downloadedBytes += bytesRead;

                            // Calculate metrics
                            if (listener != null) {
                                long timeElapsed = System.currentTimeMillis() - startTime;
                                double speedMBps = 0.0;
                                if (timeElapsed > 0) {
                                    // (Bytes -> MB) / (Millis -> Seconds)
                                    speedMBps = (downloadedBytes / 1048576.0) / (timeElapsed / 1000.0);
                                }
                                
                                double percent = totalBytes > 0 ? ((double) downloadedBytes / totalBytes) * 100 : -1;
                                listener.onProgress(percent, speedMBps);
                            }
                        }
                    }
                    return targetFile;
                } else {
                    throw new RuntimeException("Download failed. HTTP " + response.statusCode());
                }
            } catch (Exception e) {
                throw new RuntimeException("Network error while downloading " + filename, e);
            }
        }, DOWNLOAD_POOL);
    }
}
package com.theexpert9.modupdater.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ModrinthClient {
    private static final String USER_AGENT = "ModUpdater/1.0.0 (https://github.com/TheExpert9/ModUpdater)";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    // // --- DATA RECORDS ---
    // public record ModFile(String url, String filename, boolean primary, Map<String, String> hashes) {}
    
    // --- DATA RECORDS ---
    public record ModFile(String url, String filename, boolean primary, Map<String, String> hashes) {}
    
    // Added 'changelog' to the record!
    public record ModVersion(String id, String project_id, String version_number, String changelog, List<ModFile> files, List<ModDependency> dependencies) {}

    // Restored ModDependency for the DependencyResolver
    public record ModDependency(String project_id, String version_id, String dependency_type) {}
    
    // Updated to include the dependencies list again
    //public record ModVersion(String id, String project_id, String version_number, List<ModFile> files, List<ModDependency> dependencies) {}
    
    // Data record for formatting the bulk POST request
    public record UpdateRequestBody(List<String> hashes, String algorithm, List<String> loaders, List<String> game_versions) {}

    // --- API METHODS ---

    /**
     * 1. BULK ENDPOINT: Sends a single POST request to check all local hashes simultaneously.
     * Extremely fast, used for checking already installed mods.
     */
    public static CompletableFuture<Map<String, ModVersion>> checkBulkUpdates(List<String> hashes, String gameVersion) {
        String url = "https://api.modrinth.com/v2/version_files/update";
        
        UpdateRequestBody body = new UpdateRequestBody(
            hashes, 
            "sha1", 
            List.of("fabric"), 
            List.of(gameVersion)
        );
        
        String jsonBody = GSON.toJson(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        java.lang.reflect.Type mapType = new TypeToken<Map<String, ModVersion>>(){}.getType();
                        return GSON.fromJson(response.body(), mapType);
                    } else {
                        System.err.println("Modrinth API Bulk Error: " + response.statusCode());
                        return Map.of();
                    }
                });
    }

    /**
     * 2. INDIVIDUAL ENDPOINT: Fetches the newest version for a specific project ID.
     * Used by DependencyResolver to fetch brand new dependencies that aren't installed yet.
     */
    public static CompletableFuture<ModVersion[]> getLatestVersions(String projectId, String gameVersion) {
        String url = String.format(
            "https://api.modrinth.com/v2/project/%s/version?loaders=%%5B%%22fabric%%22%%5D&game_versions=%%5B%%22%s%%22%%5D",
            projectId, gameVersion
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return GSON.fromJson(response.body(), ModVersion[].class);
                    } else {
                        System.err.println("Modrinth API Individual Error: " + response.statusCode());
                        return new ModVersion[0];
                    }
                });
    }
}
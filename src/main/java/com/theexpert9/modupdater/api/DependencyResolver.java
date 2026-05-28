 
package com.theexpert9.modupdater.api;

import net.fabricmc.loader.api.FabricLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DependencyResolver {
    // Thread-safe set to prevent infinite loops if two mods depend on each other
    private final Set<String> processedProjects = ConcurrentHashMap.newKeySet();
    
    /**
     * Filters through a mod's dependencies and queues downloads for missing required mods.
     * * @param dependencies The list of dependencies from the Modrinth API
     * @param gameVersion The current Minecraft version
     * @return A CompletableFuture containing a list of required ModVersion objects to download
     */
    public CompletableFuture<List<ModrinthClient.ModVersion>> resolveRequired(
            List<ModrinthClient.ModDependency> dependencies, 
            String gameVersion) {

        List<CompletableFuture<ModrinthClient.ModVersion>> futures = new ArrayList<>();

        for (ModrinthClient.ModDependency dep : dependencies) {
            // We only care about hard requirements, not optional or incompatible ones
            if (!"required".equals(dep.dependency_type())) {
                continue;
            }

            // Skip if we've already processed this dependency in this session
            if (!processedProjects.add(dep.project_id())) {
                continue;
            }

            // In a real scenario, you should also check FabricLoader.getInstance().isModLoaded()
            // to see if the dependency is already installed locally. For now, we fetch it.
            CompletableFuture<ModrinthClient.ModVersion> future = ModrinthClient.getLatestVersions(dep.project_id(), gameVersion)
                    .thenApply(versions -> {
                        if (versions.length > 0) {
                            // Return the newest compatible version (index 0 is always latest)
                            return versions[0];
                        }
                        return null;
                    });

            futures.add(future);
        }

        // Wait for all HTTP requests to finish and filter out any null results
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(version -> version != null)
                        .toList()
                );
    }
    
    public void resetCache() {
        processedProjects.clear();
    }
}
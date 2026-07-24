package neelesh.easy_install.util;

import java.util.concurrent.ConcurrentHashMap;

public class GlobalDownloadTracker {
    private static final ConcurrentHashMap<String, Integer> downloadStates = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Float> downloadProgress = new ConcurrentHashMap<>();
    private static boolean isReadyToApply = false;
    private static int Total = 0;

    public static void setState(String identifier, int state) {
        downloadStates.put(identifier, state);
    }

    public static int getState(String identifier) {
        return downloadStates.getOrDefault(identifier, 0);
    }

    public static void setProgress(String identifier, float progress) {
        downloadProgress.put(identifier, progress);
    }

    public static float getProgress(String identifier) {
        return downloadProgress.getOrDefault(identifier, 0.0f);
    }
    
    public static boolean isInstalling(String projectSlug) {
        return getState(projectSlug) == 1;
    }
    
    public static boolean isInstalled(String projectSlug) {
        return getState(projectSlug) == 2;
    }

    public static boolean IsReadyToApply() {
        return isReadyToApply = Total <= 0;
    }

    public static void add() {
        ++Total;
    }

    public static void remove(){
        --Total;
    }
    
    public static int get(){
        return Total;
    }
}

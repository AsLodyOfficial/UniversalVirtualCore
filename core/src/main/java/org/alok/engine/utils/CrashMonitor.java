package org.alok.engine.utils;

/**
 * Safe replacement for CrashMonitor.
 */
public class CrashMonitor {
    public CrashMonitor() {}

    public static void initialize() {
        // Safe no-op
    }

    public static void handleCrash(String tag, Thread thread, Throwable throwable) {
        // Safe no-op
    }

    public static String getCrashStats() {
        return "Safe";
    }

    public static String getStatus() {
        return "Safe";
    }

    public static void clearCrashHistory() {}
}

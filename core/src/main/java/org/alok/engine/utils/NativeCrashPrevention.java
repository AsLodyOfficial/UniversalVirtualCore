package org.alok.engine.utils;

/**
 * Safe replacement for NativeCrashPrevention.
 * Avoids registering preemptive signal handlers and ThreadGroup loops at class loading time.
 */
public class NativeCrashPrevention {
    public NativeCrashPrevention() {}

    public static void initialize() {
        // Safe no-op
    }

    public static void clearCache() {}

    public static String getPreventionStats() {
        return "Native Prevention Stats - Safe Mode";
    }

    public static String getStatus() {
        return "Native Crash Prevention Status: Safe";
    }
}

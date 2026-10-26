package org.alok.engine.utils;

/**
 * Safe replacement for SocialMediaAppCrashPrevention.
 */
public class SocialMediaAppCrashPrevention {
    public SocialMediaAppCrashPrevention() {}

    public static void initialize() {
        // Safe no-op
    }

    public static boolean isSocialMediaApp() {
        return false;
    }

    public static void applyCrashPrevention(String pkg) {
        // Safe no-op
    }

    public static String getCrashPreventionStatus() {
        return "Safe";
    }
}

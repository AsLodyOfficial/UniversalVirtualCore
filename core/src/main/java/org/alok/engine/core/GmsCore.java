package org.alok.engine.core;

import android.content.pm.PackageManager;
import android.util.Log;

import org.alok.engine.Alok;
import org.alok.engine.entity.pm.InstallResult;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resilient Google Mobile Services (GMS / GSF / Play Store / Play Games) manager for Alok Engine.
 * Overrides alok-stubs.jar's GmsCore to fix the rollback bug where a single missing/optional
 * Google package caused uninstallGApps() to wipe out GMS, GSF, Play Store, and Play Games.
 */
public class GmsCore {
    private static final String TAG = "GmsCore";
    private static final Set<String> GOOGLE_APP = new LinkedHashSet<>();
    private static final Set<String> GOOGLE_SERVICE = new LinkedHashSet<>();
    private static final Set<String> ESSENTIAL_GMS = new LinkedHashSet<>();

    public static final String GMS_PKG = "com.google.android.gms";
    public static final String GSF_PKG = "com.google.android.gsf";
    public static final String VENDING_PKG = "com.android.vending";
    public static final String PLAY_GAMES_PKG = "com.google.android.play.games";

    static {
        ESSENTIAL_GMS.add(GMS_PKG);
        ESSENTIAL_GMS.add(GSF_PKG);
        ESSENTIAL_GMS.add(VENDING_PKG);
        ESSENTIAL_GMS.add(PLAY_GAMES_PKG);

        GOOGLE_APP.add(VENDING_PKG);
        GOOGLE_APP.add(PLAY_GAMES_PKG);
        GOOGLE_APP.add("com.google.android.wearable.app");
        GOOGLE_APP.add("com.google.android.wearable.app.cn");

        GOOGLE_SERVICE.add(GMS_PKG);
        GOOGLE_SERVICE.add(GSF_PKG);
        GOOGLE_SERVICE.add("com.google.android.gsf.login");
        GOOGLE_SERVICE.add("com.google.android.backuptransport");
        GOOGLE_SERVICE.add("com.google.android.backup");
        GOOGLE_SERVICE.add("com.google.android.configupdater");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.contacts");
        GOOGLE_SERVICE.add("com.google.android.feedback");
        GOOGLE_SERVICE.add("com.google.android.onetimeinitializer");
        GOOGLE_SERVICE.add("com.google.android.partnersetup");
        GOOGLE_SERVICE.add("com.google.android.setupwizard");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.calendar");
    }

    public GmsCore() {
    }

    public static boolean isGoogleService(String packageName) {
        return packageName != null && GOOGLE_SERVICE.contains(packageName);
    }

    public static boolean isGoogleAppOrService(String packageName) {
        return packageName != null && (GOOGLE_APP.contains(packageName) || GOOGLE_SERVICE.contains(packageName));
    }

    private static InstallResult installPackages(Set<String> list, int userId) {
        Alok alok = Alok.get();
        InstallResult finalResult = new InstallResult();
        finalResult.success = true;
        for (String packageName : list) {
            try {
                if (alok.isInstalled(packageName, userId)) {
                    continue;
                }
                android.content.Context ctx = Alok.getContext();
                PackageManager pm = ctx != null ? ctx.getPackageManager() : Alok.getPackageManager();
                if (pm == null) continue;
                try {
                    pm.getApplicationInfo(packageName, 0);
                } catch (PackageManager.NameNotFoundException ignored) {
                    continue;
                }
                InstallResult res = alok.installPackageAsUser(packageName, userId);
                if (res != null && !res.success) {
                    Log.w(TAG, "Optional/GMS package install note for " + packageName + ": " + res.msg);
                } else {
                    Log.i(TAG, "Successfully installed Google component: " + packageName + " (userId=" + userId + ")");
                }
            } catch (Throwable t) {
                Log.w(TAG, "Skipping GMS package " + packageName + ": " + t.getMessage());
            }
        }
        return finalResult;
    }

    private static void uninstallPackages(Set<String> list, int userId) {
        Alok alok = Alok.get();
        for (String packageName : list) {
            try {
                alok.uninstallPackageAsUser(packageName, userId);
            } catch (Throwable ignored) {
            }
        }
    }

    public static InstallResult installGApps(int userId) {
        Set<String> googleApps = new LinkedHashSet<>();
        googleApps.addAll(ESSENTIAL_GMS);
        googleApps.addAll(GOOGLE_SERVICE);
        googleApps.addAll(GOOGLE_APP);
        return installPackages(googleApps, userId);
    }

    public static void ensureEssentialGmsInstalled(int userId) {
        try {
            if (!isSupportGms()) return;
            installPackages(ESSENTIAL_GMS, userId);
        } catch (Throwable t) {
            Log.w(TAG, "ensureEssentialGmsInstalled note: " + t.getMessage());
        }
    }

    /**
     * Safe no-op: GMS, Play Store, and Play Games should remain registered once installed
     * to support both in-container guest apps and in-game Google Play sign-ins.
     */
    public static void cleanupClonedGmsIfPresent(int userId) {
        // Preserved: Do not uninstall GMS/GSF/Play Store
    }

    public static void uninstallGApps(int userId) {
        uninstallPackages(GOOGLE_SERVICE, userId);
        uninstallPackages(GOOGLE_APP, userId);
    }

    public static void remove(String packageName) {
        GOOGLE_SERVICE.remove(packageName);
        GOOGLE_APP.remove(packageName);
    }

    public static boolean isSupportGms() {
        try {
            return Alok.getPackageManager().getApplicationInfo(GMS_PKG, 0).enabled;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isInstalledGoogleService(int userId) {
        try {
            return Alok.get().isInstalled(GMS_PKG, userId);
        } catch (Throwable ignored) {
            return false;
        }
    }
}

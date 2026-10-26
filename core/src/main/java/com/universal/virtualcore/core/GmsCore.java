package com.universal.virtualcore.core;

import org.alok.engine.entity.pm.InstallResult;

/**
 * Google Mobile Services (GMS / GSF / Play Store / Play Games) virtualization manager for Alok Core.
 */
public class GmsCore {
    public static final String GMS_PKG = org.alok.engine.core.GmsCore.GMS_PKG;
    public static final String GSF_PKG = org.alok.engine.core.GmsCore.GSF_PKG;
    public static final String VENDING_PKG = org.alok.engine.core.GmsCore.VENDING_PKG;
    public static final String PLAY_GAMES_PKG = org.alok.engine.core.GmsCore.PLAY_GAMES_PKG;

    public static boolean isGoogleAppOrService(String packageName) {
        return org.alok.engine.core.GmsCore.isGoogleAppOrService(packageName);
    }

    public static InstallResult installGApps(int userId) {
        return org.alok.engine.core.GmsCore.installGApps(userId);
    }

    public static void ensureEssentialGmsInstalled(int userId) {
        org.alok.engine.core.GmsCore.ensureEssentialGmsInstalled(userId);
    }

    public static void cleanupClonedGmsIfPresent(int userId) {
        org.alok.engine.core.GmsCore.cleanupClonedGmsIfPresent(userId);
    }

    public static void uninstallGApps(int userId) {
        org.alok.engine.core.GmsCore.uninstallGApps(userId);
    }

    public static void remove(String packageName) {
        org.alok.engine.core.GmsCore.remove(packageName);
    }

    public static boolean isSupportGms() {
        return org.alok.engine.core.GmsCore.isSupportGms();
    }

    public static boolean isInstalledGoogleService(int userId) {
        return org.alok.engine.core.GmsCore.isInstalledGoogleService(userId);
    }
}


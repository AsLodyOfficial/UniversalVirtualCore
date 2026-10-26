package com.universal.virtualcore.env;

import android.content.Context;
import com.universal.virtualcore.native_core.NativeBridge;
import java.io.File;

public class VEnvironment {
    private static File sHostDataDir;

    public static void init(Context hostContext) {
        sHostDataDir = new File(hostContext.getFilesDir(), "virtual_root");
        if (!sHostDataDir.exists()) {
            sHostDataDir.mkdirs();
        }
    }

    public static File getHostVirtualRoot() {
        return sHostDataDir;
    }

    public static File getUserDirectory(int userId) {
        File userDir = new File(sHostDataDir, "user/" + userId);
        if (!userDir.exists()) {
            userDir.mkdirs();
        }
        return userDir;
    }

    public static File getDataAppDirectory() {
        File appDir = new File(sHostDataDir, "app");
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        return appDir;
    }

    public static File getPackageDataDirectory(int userId, String packageName) {
        File dir = new File(getUserDirectory(userId), packageName);
        if (!dir.exists()) {
            dir.mkdirs();
            NativeBridge.initUserStorage(userId, packageName, sHostDataDir.getAbsolutePath());
        }
        return dir;
    }

    public static File getPackageLibDirectory(int userId, String packageName) {
        File libDir = new File(getPackageDataDirectory(userId, packageName), "lib");
        if (!libDir.exists()) {
            libDir.mkdirs();
        }
        return libDir;
    }
}

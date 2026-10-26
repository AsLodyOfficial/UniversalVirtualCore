package com.universal.virtualcore.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.universal.virtualcore.package_mgr.PackageCache;
import com.universal.virtualcore.package_mgr.model.ComponentInfo;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;
import java.util.Collection;

public class VPackageManagerService {
    private static final VPackageManagerService sInstance = new VPackageManagerService();

    public static VPackageManagerService get() {
        return sInstance;
    }

    public VirtualPackage getPackageInfo(String packageName) {
        PackageCache.ensureLoaded(null);
        return PackageCache.get(packageName);
    }

    public boolean isPackageInstalled(String packageName) {
        PackageCache.ensureLoaded(null);
        return PackageCache.contains(packageName);
    }

    public Collection<VirtualPackage> getInstalledPackages() {
        PackageCache.ensureLoaded(null);
        return PackageCache.getAll();
    }

    public Intent getLaunchIntentForPackage(Context context, String packageName) {
        VirtualPackage vPkg = PackageCache.get(packageName);
        if (vPkg != null && vPkg.activities != null && !vPkg.activities.isEmpty()) {
            String mainActivity = null;
            for (ComponentInfo info : vPkg.activities) {
                if (info.name != null && (info.name.contains("Main") || info.name.contains("Launch") || info.name.contains("Activity") || info.name.contains("Splash"))) {
                    mainActivity = info.name;
                    break;
                }
            }
            if (mainActivity == null) {
                mainActivity = vPkg.activities.get(0).name;
            }

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(packageName, mainActivity));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }

        if (context != null) {
            try {
                PackageManager pm = context.getPackageManager();
                Intent hostLaunch = pm.getLaunchIntentForPackage(packageName);
                if (hostLaunch != null) {
                    return hostLaunch;
                }
            } catch (Throwable ignored) {}
        }

        Intent fallback = new Intent(Intent.ACTION_MAIN);
        fallback.setPackage(packageName);
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return fallback;
    }
}
package com.universal.virtualcore.server;

import android.content.Context;
import com.universal.virtualcore.package_mgr.PackageCache;
import com.universal.virtualcore.package_mgr.PackageInstaller;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;
import java.io.File;

public class VAppManagerService {
    private static final VAppManagerService sInstance = new VAppManagerService();

    public static VAppManagerService get() {
        return sInstance;
    }

    public VirtualPackage installPackage(Context context, File apkFile) {
        return PackageInstaller.installPackage(context, apkFile);
    }

    public boolean uninstallPackage(String packageName) {
        if (PackageCache.contains(packageName)) {
            PackageCache.remove(packageName);
            return true;
        }
        return false;
    }
}

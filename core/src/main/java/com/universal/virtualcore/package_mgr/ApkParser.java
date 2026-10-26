package com.universal.virtualcore.package_mgr;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.universal.virtualcore.package_mgr.model.ComponentInfo;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;
import java.io.File;

public class ApkParser {
    public static VirtualPackage parsePackage(Context context, File apkFile) {
        VirtualPackage vPkg = new VirtualPackage();
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(),
                    PackageManager.GET_ACTIVITIES |
                    PackageManager.GET_SERVICES |
                    PackageManager.GET_RECEIVERS |
                    PackageManager.GET_PROVIDERS |
                    PackageManager.GET_PERMISSIONS |
                    PackageManager.GET_META_DATA |
                    PackageManager.GET_SIGNING_CERTIFICATES);

            if (info != null) {
                vPkg.packageName = info.packageName;
                vPkg.versionCode = info.versionCode;
                vPkg.versionName = info.versionName;
                vPkg.sourcePath = apkFile.getAbsolutePath();

                if (info.applicationInfo != null) {
                    vPkg.applicationClassName = info.applicationInfo.className;
                    vPkg.targetSdkVersion = info.applicationInfo.targetSdkVersion;
                    vPkg.minSdkVersion = info.applicationInfo.minSdkVersion;
                }

                if (info.activities != null) {
                    for (android.content.pm.ActivityInfo a : info.activities) {
                        ComponentInfo c = new ComponentInfo();
                        c.name = a.name;
                        c.packageName = a.packageName;
                        c.processName = a.processName;
                        c.exported = a.exported;
                        c.permission = a.permission;
                        c.launchMode = a.launchMode;
                        vPkg.activities.add(c);
                    }
                }

                if (info.services != null) {
                    for (android.content.pm.ServiceInfo s : info.services) {
                        ComponentInfo c = new ComponentInfo();
                        c.name = s.name;
                        c.packageName = s.packageName;
                        c.processName = s.processName;
                        c.exported = s.exported;
                        c.permission = s.permission;
                        vPkg.services.add(c);
                    }
                }

                if (info.providers != null) {
                    for (android.content.pm.ProviderInfo p : info.providers) {
                        ComponentInfo c = new ComponentInfo();
                        c.name = p.name;
                        c.packageName = p.packageName;
                        c.processName = p.processName;
                        c.exported = p.exported;
                        c.authority = p.authority;
                        vPkg.providers.add(c);
                    }
                }

                if (info.requestedPermissions != null) {
                    for (String perm : info.requestedPermissions) {
                        vPkg.requestedPermissions.add(perm);
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return vPkg;
    }
}

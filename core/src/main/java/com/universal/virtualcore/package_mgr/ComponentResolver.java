package com.universal.virtualcore.package_mgr;

import android.content.Intent;
import com.universal.virtualcore.package_mgr.model.ComponentInfo;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;

public class ComponentResolver {
    public static ComponentInfo resolveActivity(Intent intent) {
        if (intent.getComponent() != null) {
            String pkg = intent.getComponent().getPackageName();
            String cls = intent.getComponent().getClassName();
            VirtualPackage vPkg = PackageCache.get(pkg);
            if (vPkg != null) {
                for (ComponentInfo a : vPkg.activities) {
                    if (a.name.equals(cls)) return a;
                }
            }
        }
        return null;
    }

    public static ComponentInfo resolveService(Intent intent) {
        if (intent.getComponent() != null) {
            String pkg = intent.getComponent().getPackageName();
            String cls = intent.getComponent().getClassName();
            VirtualPackage vPkg = PackageCache.get(pkg);
            if (vPkg != null) {
                for (ComponentInfo s : vPkg.services) {
                    if (s.name.equals(cls)) return s;
                }
            }
        }
        return null;
    }
}

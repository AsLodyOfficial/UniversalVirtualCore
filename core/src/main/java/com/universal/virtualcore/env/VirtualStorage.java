package com.universal.virtualcore.env;

import com.universal.virtualcore.native_core.PathVirtualizer;
import java.io.File;

public class VirtualStorage {
    public static void setupUserStorage(int userId, String packageName) {
        File realDataDir = VEnvironment.getPackageDataDirectory(userId, packageName);
        String virtualPath = "/data/user/" + userId + "/" + packageName;
        String legacyVirtualPath = "/data/data/" + packageName;

        PathVirtualizer.mapPath(virtualPath, realDataDir.getAbsolutePath());
        if (userId == 0) {
            PathVirtualizer.mapPath(legacyVirtualPath, realDataDir.getAbsolutePath());
        }
    }
}

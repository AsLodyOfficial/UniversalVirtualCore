package com.universal.virtualcore.client;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import dalvik.system.DexClassLoader;

public class LoadedApkManager {
    private static final Map<String, ClassLoader> sClassLoaders = new ConcurrentHashMap<>();

    public static ClassLoader getOrCreateClassLoader(String packageName, String apkPath, String libPath, ClassLoader parent) {
        ClassLoader loader = sClassLoaders.get(packageName);
        if (loader == null) {
            File optDir = new File(apkPath).getParentFile();
            loader = new DexClassLoader(apkPath, optDir != null ? optDir.getAbsolutePath() : null, libPath, parent);
            sClassLoaders.put(packageName, loader);
        }
        return loader;
    }
}

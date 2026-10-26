package com.universal.virtualcore.package_mgr;

import android.content.Context;
import android.util.Log;

import com.universal.virtualcore.env.VEnvironment;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PackageCache {
    private static final String TAG = "PackageCache";
    private static final Map<String, VirtualPackage> sPackages = new ConcurrentHashMap<>();
    private static volatile boolean sLoaded = false;
    private static Context sHostContext;

    public static Context getHostContext() {
        return sHostContext != null ? sHostContext : com.universal.virtualcore.VirtualCore.get().getContext();
    }

    public static synchronized void ensureLoaded(Context context) {
        if (context != null) {
            sHostContext = context.getApplicationContext();
        }
        if (sLoaded) return;
        sLoaded = true;

        File root = VEnvironment.getHostVirtualRoot();
        if (root == null && context != null) {
            VEnvironment.init(context);
            root = VEnvironment.getHostVirtualRoot();
        }

        if (root != null) {
            File dbFile = new File(root, "packages_db.dat");
            if (dbFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dbFile))) {
                    @SuppressWarnings("unchecked")
                    Map<String, VirtualPackage> diskMap = (Map<String, VirtualPackage>) ois.readObject();
                    if (diskMap != null) {
                        sPackages.putAll(diskMap);
                        Log.i(TAG, "Restored " + sPackages.size() + " packages from persistent cache.");
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to restore packages_db.dat: " + t.getMessage());
                }
            }

            // Auto-scan installed apps directory on disk
            File appDir = VEnvironment.getDataAppDirectory();
            if (appDir != null && appDir.exists()) {
                File[] pkgDirs = appDir.listFiles();
                if (pkgDirs != null) {
                    for (File pDir : pkgDirs) {
                        if (pDir.isDirectory()) {
                            String pkgName = pDir.getName();
                            if (!sPackages.containsKey(pkgName)) {
                                File baseApk = new File(pDir, "base.apk");
                                if (baseApk.exists()) {
                                    VirtualPackage restored = ApkParser.parsePackage(context, baseApk);
                                    if (restored != null && restored.packageName != null) {
                                        sPackages.put(restored.packageName, restored);
                                        Log.i(TAG, "Recovered package from disk: " + restored.packageName);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static synchronized void put(VirtualPackage pkg) {
        if (pkg != null && pkg.packageName != null) {
            sPackages.put(pkg.packageName, pkg);
            saveToDisk();
        }
    }

    public static VirtualPackage get(String packageName) {
        if (packageName == null) return null;
        return sPackages.get(packageName);
    }

    public static boolean contains(String packageName) {
        if (packageName == null) return false;
        return sPackages.containsKey(packageName);
    }

    public static Collection<VirtualPackage> getAll() {
        return sPackages.values();
    }

    public static synchronized void remove(String packageName) {
        if (packageName != null) {
            sPackages.remove(packageName);
            saveToDisk();
            // Remove files from disk
            try {
                File appDir = new File(VEnvironment.getDataAppDirectory(), packageName);
                if (appDir.exists()) {
                    deleteRecursive(appDir);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void saveToDisk() {
        File root = VEnvironment.getHostVirtualRoot();
        if (root == null) return;
        File dbFile = new File(root, "packages_db.dat");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(dbFile))) {
            oos.writeObject(new ConcurrentHashMap<>(sPackages));
            oos.flush();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to save packages_db.dat: " + t.getMessage());
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}

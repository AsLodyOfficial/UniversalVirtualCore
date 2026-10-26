package com.universal.virtualcore.package_mgr;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.alok.engine.Alok;
import org.alok.engine.core.IOCore;
import org.alok.engine.core.NativeCore;
import org.alok.engine.core.env.BEnvironment;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

public class ObbVirtualizer {
    private static final String TAG = "ObbVirtualizer";

    public static void mountObb(String packageName, int userId) {
        if (packageName == null || packageName.isEmpty() || packageName.equals(Alok.getHostPkg())) {
            return;
        }
        try {
            Context ctx = Alok.getContext();
            Log.i(TAG, "mountObb starting for pkg=" + packageName + ", userId=" + userId);

            // 1. Collect candidate directories where OBB files might exist
            List<File> candidates = new ArrayList<>();
            candidates.add(new File("/storage/emulated/0/Android/obb/" + packageName));
            candidates.add(new File("/sdcard/Android/obb/" + packageName));
            try {
                File extDir = Environment.getExternalStorageDirectory();
                if (extDir != null) {
                    candidates.add(new File(extDir, "Android/obb/" + packageName));
                }
            } catch (Throwable ignored) {}
            if (userId != 0) {
                candidates.add(new File("/storage/emulated/" + userId + "/Android/obb/" + packageName));
            }
            if (ctx != null) {
                try {
                    File hostExt = ctx.getExternalFilesDir(null);
                    if (hostExt != null) {
                        candidates.add(new File(hostExt, "Android/obb/" + packageName));
                        candidates.add(new File(hostExt, "obb/" + packageName));
                    }
                } catch (Throwable ignored) {}
                try {
                    File hostObb = ctx.getObbDir();
                    if (hostObb != null) {
                        candidates.add(new File(hostObb, packageName));
                        candidates.add(hostObb);
                    }
                } catch (Throwable ignored) {}
            }
            try {
                File vExt = BEnvironment.getExternalUserDir(userId);
                if (vExt != null) {
                    candidates.add(new File(vExt, "Android/obb/" + packageName));
                }
            } catch (Throwable ignored) {}

            // 2. Search for the candidate directory containing .obb files
            File targetObbDir = null;
            FilenameFilter obbFilter = (dir, name) -> name != null && name.toLowerCase().endsWith(".obb");

            for (File candidate : candidates) {
                if (candidate != null && candidate.exists() && candidate.isDirectory()) {
                    File[] obbFiles = candidate.listFiles(obbFilter);
                    if (obbFiles != null && obbFiles.length > 0) {
                        targetObbDir = candidate;
                        Log.i(TAG, "Found " + obbFiles.length + " OBB files in candidate: " + candidate.getAbsolutePath());
                        break;
                    }
                }
            }

            // Fallback: if no directory currently has .obb files, default to primary storage
            if (targetObbDir == null) {
                targetObbDir = new File("/storage/emulated/0/Android/obb/" + packageName);
            }
            if (!targetObbDir.exists()) {
                targetObbDir.mkdirs();
            }

            String targetPath = targetObbDir.getAbsolutePath();
            String virtualPathUser = "/storage/emulated/" + userId + "/Android/obb/" + packageName;
            String virtualPath0 = "/storage/emulated/0/Android/obb/" + packageName;
            String legacyPath = "/sdcard/Android/obb/" + packageName;

            // 3. Register IO rules in IOCore and NativeCore (libalok.so)
            IOCore ioCore = IOCore.get();

            // Whitelist OBB directory so NativeCore / IOCore never redirects it into the void
            ioCore.addBlackRedirect(targetPath);
            ioCore.addBlackRedirect("/storage/emulated/0/Android/obb");
            ioCore.addBlackRedirect("/sdcard/Android/obb");
            ioCore.addBlackRedirect("/storage/emulated/" + userId + "/Android/obb");

            // Redirect virtual game lookups to the resolved directory
            ioCore.addRedirect(virtualPathUser, targetPath);
            ioCore.addRedirect(virtualPath0, targetPath);
            ioCore.addRedirect(legacyPath, targetPath);

            NativeCore.addIORule(virtualPathUser, targetPath);
            NativeCore.addIORule(virtualPath0, targetPath);
            NativeCore.addIORule(legacyPath, targetPath);
            NativeCore.addIORule("/storage/emulated/0/Android/obb", "/storage/emulated/0/Android/obb");
            NativeCore.addIORule("/sdcard/Android/obb", "/storage/emulated/0/Android/obb");
            NativeCore.enableIO();

            // 4. Ensure files are also mirrored or symlinked into the virtual container dir
            try {
                File vExtDir = BEnvironment.getExternalUserDir(userId);
                if (vExtDir != null) {
                    File vObbDir = new File(vExtDir, "Android/obb/" + packageName);
                    if (!vObbDir.exists()) {
                        vObbDir.mkdirs();
                    }
                    if (!vObbDir.getAbsolutePath().equals(targetPath)) {
                        File[] files = targetObbDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                File dest = new File(vObbDir, f.getName());
                                if (!dest.exists()) {
                                    try {
                                        android.system.Os.symlink(f.getAbsolutePath(), dest.getAbsolutePath());
                                    } catch (Throwable t) {
                                        try {
                                            android.system.Os.link(f.getAbsolutePath(), dest.getAbsolutePath());
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Virtual OBB link note: " + t.getMessage());
            }

            Log.i(TAG, "Successfully mounted OBB for " + packageName + " -> " + targetPath);
        } catch (Throwable t) {
            Log.e(TAG, "Error mounting OBB: " + t.getMessage(), t);
        }
    }
}

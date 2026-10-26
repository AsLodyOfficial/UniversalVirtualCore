package com.universal.virtualcore.package_mgr;

import android.content.Context;
import android.util.Log;
import com.universal.virtualcore.env.VEnvironment;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SplitApkInstaller {
    private static final String TAG = "SplitApkInstaller";

    public static VirtualPackage installSplitApks(Context context, List<File> apkFiles) {
        if (apkFiles == null || apkFiles.isEmpty()) return null;
        PackageCache.ensureLoaded(context);

        File baseApk = null;
        List<File> splitApks = new ArrayList<>();

        for (File f : apkFiles) {
            if (f.getName().equalsIgnoreCase("base.apk") || baseApk == null) {
                baseApk = f;
            } else {
                splitApks.add(f);
            }
        }

        if (baseApk == null) return null;

        VirtualPackage vPkg = ApkParser.parsePackage(context, baseApk);
        if (vPkg != null && vPkg.packageName != null) {
            File targetDir = new File(VEnvironment.getDataAppDirectory(), vPkg.packageName);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            } else {
                targetDir.setWritable(true);
            }

            File targetBase = new File(targetDir, "base.apk");
            copyFile(baseApk, targetBase);
            try {
                if (targetBase.canWrite()) {
                    targetBase.setReadable(true, false);
                    targetBase.setWritable(false, false);
                    targetBase.setReadOnly();
                }
            } catch (Throwable ignored) {}
            vPkg.sourcePath = targetBase.getAbsolutePath();

            if (!splitApks.isEmpty()) {
                vPkg.isSplitApk = true;
                for (int i = 0; i < splitApks.size(); i++) {
                    File split = splitApks.get(i);
                    File targetSplit = new File(targetDir, "split_" + i + ".apk");
                    copyFile(split, targetSplit);
                    try {
                        if (targetSplit.canWrite()) {
                            targetSplit.setReadable(true, false);
                            targetSplit.setWritable(false, false);
                            targetSplit.setReadOnly();
                        }
                    } catch (Throwable ignored) {}
                    vPkg.splitPaths.add(targetSplit.getAbsolutePath());
                }
            }

            PackageCache.put(vPkg);
            Log.i(TAG, "Installed split APK package: " + vPkg.packageName + " (splits: " + vPkg.splitPaths.size() + ")");
            return vPkg;
        }
        return null;
    }

    private static void copyFile(File src, File dst) {
        PackageInstaller.copyFile(src, dst);
    }
}

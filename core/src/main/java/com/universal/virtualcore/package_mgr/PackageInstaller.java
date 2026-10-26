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

public class PackageInstaller {
    private static final String TAG = "PackageInstaller";

    public static VirtualPackage installPackage(Context context, File sourceApk) {
        PackageCache.ensureLoaded(context);
        VirtualPackage vPkg = ApkParser.parsePackage(context, sourceApk);
        if (vPkg != null && vPkg.packageName != null) {
            File targetDir = new File(VEnvironment.getDataAppDirectory(), vPkg.packageName);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            } else {
                targetDir.setWritable(true);
            }

            File targetApk = new File(targetDir, "base.apk");
            copyFile(sourceApk, targetApk);

            try {
                if (targetApk.canWrite()) {
                    targetApk.setReadable(true, false);
                    targetApk.setWritable(false, false);
                    targetApk.setReadOnly();
                }
            } catch (Throwable ignored) {}

            vPkg.sourcePath = targetApk.getAbsolutePath();
            PackageCache.put(vPkg);
            Log.i(TAG, "Installed package: " + vPkg.packageName + " -> " + vPkg.sourcePath);
            return vPkg;
        }
        return null;
    }

    public static void copyFile(File src, File dst) {
        try {
            if (dst.exists()) {
                dst.setWritable(true);
                dst.delete();
            }
            if (dst.getParentFile() != null && !dst.getParentFile().exists()) {
                dst.getParentFile().mkdirs();
            } else if (dst.getParentFile() != null) {
                dst.getParentFile().setWritable(true);
            }

            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[32768];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "copyFile failed: " + e.getMessage(), e);
        }
    }
}

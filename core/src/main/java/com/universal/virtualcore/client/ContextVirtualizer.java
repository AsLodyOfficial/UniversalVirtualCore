package com.universal.virtualcore.client;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.WindowManager;

import com.universal.virtualcore.VirtualCore;
import com.universal.virtualcore.package_mgr.PackageCache;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;

import java.io.File;
import java.lang.reflect.Field;

public class ContextVirtualizer {
    private static final String TAG = "ContextVirtualizer";

    public static void virtualizeActivity(Activity activity) {
        if (activity == null) return;

        try {
            Intent intent = activity.getIntent();
            String guestPkg = null;
            int userId = 0;
            if (intent != null) {
                guestPkg = intent.getStringExtra("_v_pkg_");
                userId = intent.getIntExtra("_v_user_", 0);
            }

            if (guestPkg == null) {
                // Check if class belongs to a registered package
                String className = activity.getClass().getName();
                for (VirtualPackage vp : PackageCache.getAll()) {
                    if (className.startsWith(vp.packageName) || (vp.sourcePath != null && className.contains("epicgames") || className.contains("unity3d") || className.contains("dts.freefire"))) {
                        guestPkg = vp.packageName;
                        break;
                    }
                }
            }

            if (guestPkg == null) return;

            VirtualPackage vPkg = PackageCache.get(guestPkg);
            String apkPath = vPkg != null ? vPkg.sourcePath : null;
            if (apkPath == null || !new File(apkPath).exists()) return;

            // 1. Create Guest Resources & AssetManager
            AssetManager guestAssets = VirtualContextWrapper.createGuestAssetManager(apkPath);
            Resources guestResources = VirtualContextWrapper.createGuestResources(activity, apkPath);

            // 2. Create VirtualContextWrapper
            VirtualContextWrapper virtualContext = new VirtualContextWrapper(
                    activity.getBaseContext(),
                    guestPkg,
                    userId,
                    activity.getClassLoader(),
                    guestResources,
                    guestAssets,
                    apkPath
            );

            // 3. Swap mBase in ContextWrapper
            setField(ContextWrapper.class, activity, "mBase", virtualContext);

            // 4. Swap mResources & mTheme in ContextThemeWrapper
            try {
                setField(ContextThemeWrapper.class, activity, "mResources", guestResources);
            } catch (Throwable ignored) {}

            // 5. Apply Theme from Manifest
            try {
                PackageInfo pi = activity.getPackageManager().getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
                if (pi != null && pi.activities != null) {
                    for (ActivityInfo ai : pi.activities) {
                        if (activity.getClass().getName().equals(ai.name) && ai.theme != 0) {
                            activity.setTheme(ai.theme);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 6. Setup 3D Graphics & Game Window Acceleration
            if (activity.getWindow() != null) {
                activity.getWindow().setFormat(PixelFormat.RGBA_8888);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                }

                try {
                    int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                    activity.getWindow().getDecorView().setSystemUiVisibility(flags);
                } catch (Throwable ignored) {}
            }

            Log.i(TAG, "Successfully virtualized Activity: " + activity.getClass().getName() + " for " + guestPkg);
        } catch (Throwable t) {
            Log.e(TAG, "virtualizeActivity error: " + t.getMessage(), t);
        }
    }

    private static void setField(Class<?> clazz, Object target, String fieldName, Object value) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Throwable ignored) {
            try {
                Field f = clazz.getField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Throwable ignored2) {}
        }
    }
}


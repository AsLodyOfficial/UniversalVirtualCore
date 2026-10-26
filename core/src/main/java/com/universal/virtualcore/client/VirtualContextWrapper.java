package com.universal.virtualcore.client;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;

import com.universal.virtualcore.env.VEnvironment;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;

import java.io.File;
import java.lang.reflect.Method;

public class VirtualContextWrapper extends ContextWrapper {
    private static final String TAG = "VirtualContext";

    private final String mGuestPackageName;
    private final int mUserId;
    private final ClassLoader mClassLoader;
    private final Resources mResources;
    private final AssetManager mAssetManager;
    private final ApplicationInfo mApplicationInfo;
    private final File mDataDir;
    private final File mFilesDir;
    private final File mCacheDir;
    private final File mLibDir;
    private final String mApkPath;
    private LayoutInflater mInflater;

    public VirtualContextWrapper(Context base, String guestPackageName, int userId,
                                 ClassLoader classLoader, Resources resources,
                                 AssetManager assetManager, String apkPath) {
        super(base);
        this.mGuestPackageName = guestPackageName;
        this.mUserId = userId;
        this.mClassLoader = classLoader;
        this.mResources = resources;
        this.mAssetManager = assetManager;
        this.mApkPath = apkPath;

        this.mDataDir = VEnvironment.getPackageDataDirectory(userId, guestPackageName);
        this.mFilesDir = new File(mDataDir, "files");
        this.mCacheDir = new File(mDataDir, "cache");
        this.mLibDir = VEnvironment.getPackageLibDirectory(userId, guestPackageName);

        if (!mFilesDir.exists()) mFilesDir.mkdirs();
        if (!mCacheDir.exists()) mCacheDir.mkdirs();
        if (!mLibDir.exists()) mLibDir.mkdirs();

        this.mApplicationInfo = new ApplicationInfo();
        this.mApplicationInfo.packageName = guestPackageName;
        this.mApplicationInfo.dataDir = mDataDir.getAbsolutePath();
        this.mApplicationInfo.sourceDir = apkPath;
        this.mApplicationInfo.publicSourceDir = apkPath;
        this.mApplicationInfo.nativeLibraryDir = mLibDir.getAbsolutePath();
        this.mApplicationInfo.uid = 10000 + userId;
        this.mApplicationInfo.targetSdkVersion = 34;
        this.mApplicationInfo.flags = ApplicationInfo.FLAG_ALLOW_BACKUP | ApplicationInfo.FLAG_SUPPORTS_RTL | ApplicationInfo.FLAG_HAS_CODE;
    }

    @Override
    public String getPackageName() {
        return mGuestPackageName;
    }

    @Override
    public String getOpPackageName() {
        return mGuestPackageName;
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return mApplicationInfo;
    }

    @Override
    public ClassLoader getClassLoader() {
        return mClassLoader != null ? mClassLoader : super.getClassLoader();
    }

    @Override
    public Resources getResources() {
        return mResources != null ? mResources : super.getResources();
    }

    @Override
    public AssetManager getAssets() {
        return mAssetManager != null ? mAssetManager : super.getAssets();
    }

    @Override
    public File getDataDir() {
        return mDataDir;
    }

    @Override
    public File getFilesDir() {
        return mFilesDir;
    }

    @Override
    public File getCacheDir() {
        return mCacheDir;
    }

    @Override
    public File getCodeCacheDir() {
        File codeCache = new File(mDataDir, "code_cache");
        if (!codeCache.exists()) codeCache.mkdirs();
        return codeCache;
    }

    @Override
    public File getObbDir() {
        File obb = new File("/storage/emulated/0/Android/obb/" + mGuestPackageName);
        if (!obb.exists()) obb.mkdirs();
        return obb;
    }

    @Override
    public File[] getObbDirs() {
        return new File[] { getObbDir() };
    }

    @Override
    public File getExternalFilesDir(String type) {
        File ext = new File("/storage/emulated/0/Android/data/" + mGuestPackageName + "/files" + (type != null ? "/" + type : ""));
        if (!ext.exists()) ext.mkdirs();
        return ext;
    }

    @Override
    public File[] getExternalFilesDirs(String type) {
        return new File[] { getExternalFilesDir(type) };
    }

    @Override
    public File getExternalCacheDir() {
        File ext = new File("/storage/emulated/0/Android/data/" + mGuestPackageName + "/cache");
        if (!ext.exists()) ext.mkdirs();
        return ext;
    }

    @Override
    public File[] getExternalCacheDirs() {
        return new File[] { getExternalCacheDir() };
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        File spDir = new File(mDataDir, "shared_prefs");
        if (!spDir.exists()) spDir.mkdirs();
        return super.getSharedPreferences(mGuestPackageName + "_" + name, mode);
    }

    @Override
    public File getDatabasePath(String name) {
        File dbDir = new File(mDataDir, "databases");
        if (!dbDir.exists()) dbDir.mkdirs();
        return new File(dbDir, name);
    }

    @Override
    public void startActivity(Intent intent) {
        startActivity(intent, null);
    }

    @Override
    public void startActivity(Intent intent, Bundle options) {
        if (intent == null) return;
        try {
            ComponentName cn = intent.getComponent();
            String targetPkg = intent.getPackage();
            if (targetPkg == null && cn != null) {
                targetPkg = cn.getPackageName();
            }

            // If intent targets this guest package or is implicit within guest
            if (targetPkg == null || targetPkg.equals(mGuestPackageName)) {
                Intent stubIntent = new Intent();
                stubIntent.setClassName(getBaseContext().getPackageName(), "com.rukvirtual.ContainerStubActivity");
                stubIntent.putExtra("_v_pkg_", mGuestPackageName);
                stubIntent.putExtra("_v_user_", mUserId);
                if (cn != null) {
                    stubIntent.putExtra("_v_activity_", cn.getClassName());
                }
                stubIntent.putExtra("_v_intent_", intent);
                stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getBaseContext().startActivity(stubIntent, options);
                return;
            }

            // Other intents
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getBaseContext().startActivity(intent, options);
        } catch (Throwable t) {
            Log.e(TAG, "startActivity failed: " + t.getMessage(), t);
        }
    }

    @Override
    public Object getSystemService(String name) {
        if (LAYOUT_INFLATER_SERVICE.equals(name)) {
            if (mInflater == null) {
                LayoutInflater baseInflater = (LayoutInflater) super.getSystemService(name);
                if (baseInflater != null) {
                    mInflater = baseInflater.cloneInContext(this);
                }
            }
            return mInflater;
        }
        return super.getSystemService(name);
    }

    public static Resources createGuestResources(Context context, String apkPath) {
        try {
            AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            addAssetPath.invoke(assets, apkPath);

            Resources hostRes = context.getResources();
            return new Resources(assets, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to create guest Resources for " + apkPath + ": " + t.getMessage());
            return context.getResources();
        }
    }

    public static AssetManager createGuestAssetManager(String apkPath) {
        try {
            AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            addAssetPath.invoke(assets, apkPath);
            return assets;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to create guest AssetManager: " + t.getMessage());
            return null;
        }
    }
}


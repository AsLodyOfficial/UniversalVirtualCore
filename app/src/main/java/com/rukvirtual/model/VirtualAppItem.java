package com.rukvirtual.model;

import android.graphics.drawable.Drawable;

public class VirtualAppItem {
    private String packageName;
    private String appName;
    private String versionName;
    private int userId;
    private Drawable icon;
    private String apkPath;

    public VirtualAppItem(String packageName, String appName, String versionName, int userId, Drawable icon, String apkPath) {
        this.packageName = packageName;
        this.appName = appName;
        this.versionName = versionName;
        this.userId = userId;
        this.icon = icon;
        this.apkPath = apkPath;
    }

    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public String getVersionName() { return versionName; }
    public int getUserId() { return userId; }
    public Drawable getIcon() { return icon; }
    public String getApkPath() { return apkPath; }
}
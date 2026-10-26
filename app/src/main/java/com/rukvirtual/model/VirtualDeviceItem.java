package com.rukvirtual.model;

public class VirtualDeviceItem {
    private int userId;
    private String name;
    private String androidVersion;
    private String abi;
    private boolean isRunning;
    private int appCount;

    public VirtualDeviceItem(int userId, String name, String androidVersion, String abi, boolean isRunning, int appCount) {
        this.userId = userId;
        this.name = name;
        this.androidVersion = androidVersion;
        this.abi = abi;
        this.isRunning = isRunning;
        this.appCount = appCount;
    }

    public int getUserId() { return userId; }
    public String getName() { return name; }
    public String getAndroidVersion() { return androidVersion; }
    public String getAbi() { return abi; }
    public boolean isRunning() { return isRunning; }
    public void setRunning(boolean running) { isRunning = running; }
    public int getAppCount() { return appCount; }
    public void setAppCount(int appCount) { this.appCount = appCount; }
}
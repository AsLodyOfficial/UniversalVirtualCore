package com.universal.virtualcore.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import com.universal.virtualcore.package_mgr.ComponentResolver;
import com.universal.virtualcore.package_mgr.model.ComponentInfo;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VActivityManagerService {
    private static final VActivityManagerService sInstance = new VActivityManagerService();

    public static VActivityManagerService get() {
        return sInstance;
    }

    public static class ProcessRecord {
        public int virtualUid;
        public String packageName;
        public String processName;
        public int pid;
    }

    public static class ActivityRecord {
        public ComponentName component;
        public Intent intent;
        public String processName;
        public int userId;
    }

    public static class VirtualTask {
        public int taskId;
        public int userId;
        public String packageName;
        public List<ActivityRecord> activities = new ArrayList<>();
    }

    private final Map<Integer, VirtualTask> mTasks = new ConcurrentHashMap<>();
    private final Map<String, ProcessRecord> mProcesses = new ConcurrentHashMap<>();
    private int mNextTaskId = 1;

    public int startActivity(Context context, int userId, Intent intent) {
        if (intent == null) return -1;

        String pkgName = intent.getPackage();
        if (pkgName == null && intent.getComponent() != null) {
            pkgName = intent.getComponent().getPackageName();
        }

        VirtualTask task = new VirtualTask();
        task.taskId = mNextTaskId++;
        task.userId = userId;
        task.packageName = pkgName != null ? pkgName : "virtual.app";

        ActivityRecord record = new ActivityRecord();
        record.component = intent.getComponent();
        record.intent = intent;
        record.userId = userId;

        task.activities.add(record);
        mTasks.put(task.taskId, task);

        // 1. Proactively ensure package & dependencies are installed in Alok engine
        if (pkgName != null) {
            try {
                com.universal.virtualcore.core.AlokDexInjector.ensureInstalledBeforeLaunch(pkgName, userId);
                com.universal.virtualcore.native_core.NativeBridge.applyGuestHostDataRedirect(pkgName);
                if (context != null) {
                    com.universal.virtualcore.cloaking.ContainerCloaker.applyCloaking(context, pkgName);
                }
            } catch (Throwable ignored) {}
        }

        // 2. Launch inside real Alok virtual container (ProxyActivity$P0)
        try {
            if (intent.getPackage() == null && intent.getComponent() != null) {
                intent.setPackage(intent.getComponent().getPackageName());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            org.alok.engine.Alok.getBActivityManager().startActivity(intent, userId);
            android.util.Log.i("VActivityManagerService", "Launched " + pkgName + " via Alok BActivityManager in user #" + userId);
            return 0;
        } catch (Throwable t) {
            android.util.Log.e("VActivityManagerService", "BActivityManager.startActivity error: " + t.getMessage(), t);
            try {
                org.alok.engine.Alok.get().startActivity(intent, userId);
                return 0;
            } catch (Throwable t2) {
                android.util.Log.e("VActivityManagerService", "Alok.startActivity fallback error: " + t2.getMessage(), t2);
                return -1;
            }
        }
    }

    public VirtualTask getTask(int taskId) {
        return mTasks.get(taskId);
    }
}
package com.universal.virtualcore.hook;

import android.util.Log;

public class HookManager {
    private static final String TAG = "HookManager";

    @SuppressWarnings("unchecked")
    public static void installHooks() {
        HiddenApiCompatibility.unseal();
        // The Alok Virtual Engine (org.alok.engine.Alok) already installs and natively manages
        // all system hooks (ActivityThread.mInstrumentation, ServiceManager, IActivityManager,
        // IPackageManager, INotificationManager, etc.) with full compatibility for Android 5.0 - 15.
        // Overwriting mInstrumentation or sCache with custom proxies causes hook collision
        // and app launch failures. System hooking is therefore delegated directly to Alok Engine.
        Log.i(TAG, "Virtual system hooks active via Alok engine.");
    }
}

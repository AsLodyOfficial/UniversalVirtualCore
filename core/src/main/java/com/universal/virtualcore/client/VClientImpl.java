package com.universal.virtualcore.client;

import android.app.Application;
import android.content.Context;
import com.universal.virtualcore.hook.HookManager;

public class VClientImpl {
    private static final VClientImpl sInstance = new VClientImpl();
    private Context mHostContext;
    private Application mVirtualApp;

    public static VClientImpl get() {
        return sInstance;
    }

    public void init(Context hostContext) {
        this.mHostContext = hostContext;
        HookManager.installHooks();
    }

    public Context getHostContext() {
        return mHostContext;
    }

    public void setVirtualApplication(Application app) {
        this.mVirtualApp = app;
    }

    public Application getVirtualApplication() {
        return mVirtualApp;
    }
}

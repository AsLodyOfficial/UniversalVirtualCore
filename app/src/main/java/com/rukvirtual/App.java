package com.rukvirtual;

import android.app.Application;
import android.util.Log;
import com.universal.virtualcore.VirtualCore;
import com.universal.virtualcore.license.LicenseCallback;
import com.universal.virtualcore.license.LicenseResult;

import android.content.Context;
import com.universal.virtualcore.core.NomadDexInjector;

public class App extends Application {
    private static final String TAG = "RukVirtualApp";
    public static final String DEFAULT_LICENSE_KEY = "RUK-TZR1-8MD3-PP4M";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        NomadDexInjector.injectAndAttach(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NomadDexInjector.onCreate();
        initVirtualCore();
    }

    private void initVirtualCore() {
        try {
            Log.i(TAG, "Initializing Universal Virtual Core for RukVirtual...");
            VirtualCore.get().initialize(this, DEFAULT_LICENSE_KEY, new LicenseCallback() {
                @Override
                public void onSuccess(LicenseResult result) {
                    Log.i(TAG, "VirtualCore license handshake success: " + result.message);
                }

                @Override
                public void onFailure(int errorCode, String message) {
                    Log.w(TAG, "VirtualCore license handshake note [" + errorCode + "]: " + message);
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "VirtualCore startup handled safely: " + t.getMessage(), t);
        }
    }
}
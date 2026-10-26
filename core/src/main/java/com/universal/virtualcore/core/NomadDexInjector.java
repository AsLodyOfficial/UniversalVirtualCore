package com.universal.virtualcore.core;

import android.content.Context;

/**
 * Legacy compatibility forwarder to {@link AlokDexInjector}.
 */
public class NomadDexInjector {
    public static void injectAndAttach(Context context) {
        AlokDexInjector.injectAndAttach(context);
    }

    public static void onCreate() {
        AlokDexInjector.onCreate();
    }
}

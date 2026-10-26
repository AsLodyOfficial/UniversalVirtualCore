package com.universal.virtualcore.auth;

import android.content.Context;

public class BiometricBridge {
    public static boolean isBiometricSupported(Context context) {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q;
    }
}

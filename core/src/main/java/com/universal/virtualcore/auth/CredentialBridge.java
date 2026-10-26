package com.universal.virtualcore.auth;

import android.content.Context;

public class CredentialBridge {
    public static boolean isCredentialManagerAvailable(Context context) {
        return android.os.Build.VERSION.SDK_INT >= 34;
    }
}

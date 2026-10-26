package com.universal.virtualcore.hook;

import android.os.Build;
import android.util.Log;
import java.lang.reflect.Method;

public class HiddenApiCompatibility {
    private static final String TAG = "HiddenApiCompat";
    private static boolean sUnsealed = false;

    public static boolean unseal() {
        if (sUnsealed) return true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            sUnsealed = true;
            return true;
        }

        try {
            // Safe reflection using Class.forName metadata bypass
            Method getRuntime = Class.forName("dalvik.system.VMRuntime").getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object vmRuntime = getRuntime.invoke(null);

            Method setHiddenApiExemptions = Class.forName("dalvik.system.VMRuntime")
                    .getDeclaredMethod("setHiddenApiExemptions", String[].class);
            setHiddenApiExemptions.setAccessible(true);
            setHiddenApiExemptions.invoke(vmRuntime, (Object) new String[]{"L"});

            sUnsealed = true;
            Log.i(TAG, "Hidden API restrictions exempted successfully for API " + Build.VERSION.SDK_INT);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Graceful fallback: Hidden API unseal failed: " + t.getMessage());
            return false;
        }
    }
}

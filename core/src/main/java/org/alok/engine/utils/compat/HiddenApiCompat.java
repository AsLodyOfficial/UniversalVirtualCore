package org.alok.engine.utils.compat;

import android.content.Context;
import android.util.Log;

import org.alok.engine.core.NativeCore;

import me.weishu.reflection.Reflection;

/**
 * Safe replacement for HiddenApiCompat on Android 9 through Android 16 (API 36).
 * Uses FreeReflection (boot-classloader VMRuntime.setHiddenApiExemptions({"L"}))
 * and NativeCore.disableHiddenApi(), avoiding the unsafe Java heap scanning in
 * LSPosed HiddenApiBypass that caused HeapTaskDaemon SIGSEGV crashes.
 */
public class HiddenApiCompat {
    private static final String TAG = "HiddenApiCompat";
    private static volatile boolean sExempted = false;

    public HiddenApiCompat() {}

    public static boolean bypassHiddenApi(Context context) {
        if (sExempted) return true;
        try {
            int unsealRes = Reflection.unseal(context);
            Log.d(TAG, "Reflection.unseal result: " + unsealRes);
        } catch (Throwable t) {
            Log.w(TAG, "Reflection.unseal notice: " + t.getMessage());
        }
        try {
            boolean nativeResult = NativeCore.disableHiddenApi();
            Log.d(TAG, "NativeCore.disableHiddenApi: " + nativeResult);
        } catch (Throwable t) {
            Log.w(TAG, "NativeCore.disableHiddenApi notice: " + t.getMessage());
        }
        sExempted = true;
        return true;
    }
}

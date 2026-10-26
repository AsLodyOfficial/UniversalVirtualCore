package com.universal.virtualcore.native_core;

import android.util.Log;

public class NativeHookEngine {
    private static final String TAG = "NativeHookEngine";

    public static long findSymbol(String libraryName, String symbolName) {
        if (!NativeBridge.isLoaded()) return 0;
        try {
            return nativeFindSymbol(libraryName, symbolName);
        } catch (Throwable t) {
            Log.e(TAG, "findSymbol error: " + t.getMessage());
            return 0;
        }
    }

    public static boolean patchMemory(long address, byte[] newBytes) {
        if (!NativeBridge.isLoaded() || address == 0 || newBytes == null) return false;
        try {
            return nativePatchMemory(address, newBytes);
        } catch (Throwable t) {
            Log.e(TAG, "patchMemory error: " + t.getMessage());
            return false;
        }
    }

    private static native long nativeFindSymbol(String libName, String symName);
    private static native boolean nativePatchMemory(long address, byte[] bytes);
}

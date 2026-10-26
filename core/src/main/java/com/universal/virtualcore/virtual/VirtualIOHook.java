package com.universal.virtualcore.virtual;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.alok.engine.Alok;
import org.alok.engine.core.IOCore;
import org.alok.engine.core.NativeCore;
import org.alok.engine.core.env.BEnvironment;

import java.io.File;

/**
 * Intelligent I/O hook for container path redirection.
 * Specifically prevents OBB paths (e.g. BGMI com.pubg.imobile main.*.obb) from being
 * redirected to nonexistent virtual directories by IOCore and NativeCore.
 */
public class VirtualIOHook {
    private static final String TAG = "VirtualIOHook";

    /**
     * Intercepts IOCore.redirectPath(String path) at entry.
     * Returns resolved path if handled, or null to proceed with standard IOCore rules.
     */
    public static String redirectPath(Object ioCoreObj, String path) {
        if (TextUtils.isEmpty(path)) {
            return path;
        }

        // 1. Prevent recursion or double redirection
        if (path.contains("/alok/")) {
            return path;
        }

        // 2. Intelligent handling for Android/obb paths (e.g. BGMI Unreal Engine 4 OBB pak files)
        if (path.contains("/Android/obb/") || path.endsWith(".obb")) {
            // If the exact requested path exists on the filesystem, do NOT redirect it away
            File directFile = new File(path);
            if (directFile.exists()) {
                return path;
            }

            int idx = path.indexOf("/Android/obb/");
            if (idx >= 0) {
                String subPath = path.substring(idx); // "/Android/obb/com.pubg.imobile/..."

                // Candidate 1: Real external storage (/storage/emulated/0/Android/obb/...)
                File realFile = new File("/storage/emulated/0" + subPath);
                if (realFile.exists()) {
                    return realFile.getAbsolutePath();
                }

                // Candidate 2: /sdcard
                File sdcardFile = new File("/sdcard" + subPath);
                if (sdcardFile.exists()) {
                    return sdcardFile.getAbsolutePath();
                }

                // Candidate 3: Host app external files dir
                try {
                    Context ctx = Alok.getContext();
                    if (ctx != null) {
                        File hostExt = ctx.getExternalFilesDir(null);
                        if (hostExt != null) {
                            File hostExtFile = new File(hostExt, subPath);
                            if (hostExtFile.exists()) {
                                return hostExtFile.getAbsolutePath();
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                // Candidate 4: Virtual container storage dir
                try {
                    File vExt = BEnvironment.getExternalUserDir(Alok.getUserId());
                    if (vExt != null) {
                        File vFile = new File(vExt, subPath);
                        if (vFile.exists()) {
                            return vFile.getAbsolutePath();
                        }
                    }
                } catch (Throwable ignored) {}

                // Fallback: Return the standard /storage/emulated/0 path rather than
                // redirecting to /Android/data/<hostPkg>/files/alok/ where it definitely does not exist
                return "/storage/emulated/0" + subPath;
            }
        }

        // Return null to let standard IOCore TrieTree rules process the path
        return null;
    }

    /**
     * Intercepts IOCore.enableRedirect(Context) at completion (before RETURN).
     * Ensures OBB directories are whitelisted in sBlackTree and native IO rules.
     */
    public static void onEnableRedirect(Object ioCoreObj, Context context) {
        onSetupRules(ioCoreObj, context);
    }

    public static void onSetupRules(Object ioCoreObj, Context context) {
        try {
            if (!(ioCoreObj instanceof IOCore)) return;
            IOCore ioCore = (IOCore) ioCoreObj;
            int userId = Alok.getUserId();

            // Whitelist OBB directory prefixes in sBlackTree so Java/JNI never redirects them
            ioCore.addBlackRedirect("/sdcard/Android/obb");
            ioCore.addBlackRedirect("/storage/emulated/0/Android/obb");
            ioCore.addBlackRedirect("/storage/emulated/" + userId + "/Android/obb");
            ioCore.addBlackRedirect(String.format("/storage/emulated/%d/Android/obb", userId));

            // Native rules in libalok.so
            NativeCore.addIORule("/storage/emulated/0/Android/obb", "/storage/emulated/0/Android/obb");
            NativeCore.addIORule("/sdcard/Android/obb", "/storage/emulated/0/Android/obb");
            if (userId != 0) {
                NativeCore.addIORule("/storage/emulated/" + userId + "/Android/obb", "/storage/emulated/0/Android/obb");
            }
            NativeCore.enableIO();

            Log.i(TAG, "VirtualIOHook.onEnableRedirect: successfully whitelisted OBB paths for userId=" + userId);
        } catch (Throwable t) {
            Log.w(TAG, "VirtualIOHook.onEnableRedirect note: " + t.getMessage());
        }
    }
}

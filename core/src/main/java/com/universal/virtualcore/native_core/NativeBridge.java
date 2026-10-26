package com.universal.virtualcore.native_core;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.alok.engine.Alok;
import org.alok.engine.core.NativeCore;

public class NativeBridge {
    private static final String TAG = "NativeBridge";
    private static boolean sLoaded = false;

    // 11-Layer Anti-Cheat / Library Shielding Bitmasks (matching block_anogs.hpp)
    public static final int NUKE_LAYER_1_FILE_LOCK    = 1 << 0;
    public static final int NUKE_LAYER_2_FD_EXHAUST   = 1 << 1;
    public static final int NUKE_LAYER_3_MLOCK        = 1 << 2;
    public static final int NUKE_LAYER_4_ELF_DESTROY  = 1 << 3;
    public static final int NUKE_LAYER_5_SHDR_HIDE    = 1 << 4;
    public static final int NUKE_LAYER_6_GUARD_PAGE   = 1 << 5;
    public static final int NUKE_LAYER_7_LINKMAP      = 1 << 6;
    public static final int NUKE_LAYER_8_ANTIDEBUG    = 1 << 7;
    public static final int NUKE_LAYER_9_SIGNALS      = 1 << 8;
    public static final int NUKE_LAYER_10_SECCOMP     = 1 << 9;
    public static final int NUKE_LAYER_11_WATCHDOG    = 1 << 10;
    public static final int NUKE_HOLD_FILE_READ       = 1 << 11;

    public static final int NUKE_LAYERS_README_11 =
            NUKE_LAYER_1_FILE_LOCK | NUKE_LAYER_2_FD_EXHAUST | NUKE_LAYER_3_MLOCK
                    | NUKE_LAYER_4_ELF_DESTROY | NUKE_LAYER_5_SHDR_HIDE | NUKE_LAYER_6_GUARD_PAGE
                    | NUKE_LAYER_7_LINKMAP | NUKE_LAYER_8_ANTIDEBUG | NUKE_LAYER_9_SIGNALS
                    | NUKE_LAYER_10_SECCOMP | NUKE_LAYER_11_WATCHDOG;

    public static final int NUKE_LAYERS_ALL = NUKE_LAYERS_README_11 | NUKE_HOLD_FILE_READ;

    /**
     * Safe preset for guest apps/games: excludes L2 (FD exhaustion) and L10 (seccomp EPERM)
     * so game resource loading and socket creation continue smoothly.
     */
    public static final int NUKE_LAYERS_GUEST_STABLE =
            NUKE_LAYERS_README_11 & ~(NUKE_LAYER_2_FD_EXHAUST | NUKE_LAYER_10_SECCOMP);

    /**
     * Minimal safe preset: mlock + section header hiding + guard page + link_map enumeration.
     */
    public static final int NUKE_LAYERS_GUEST_ULTRA_SOFT =
            NUKE_LAYER_3_MLOCK | NUKE_LAYER_5_SHDR_HIDE | NUKE_LAYER_6_GUARD_PAGE | NUKE_LAYER_7_LINKMAP;

    /**
     * Long-play preset: avoids L1 (chmod 0000), L4 (ELF header zero), L8 (RLIMIT_NPROC=0), L11 (watchdog).
     */
    public static final int NUKE_LAYERS_GUEST_LONG_PLAY =
            NUKE_LAYERS_GUEST_ULTRA_SOFT | NUKE_LAYER_9_SIGNALS;

    /**
     * Long-play preset without L9 signal handlers (for Unity/IL2CPP/Mono games).
     */
    public static final int NUKE_LAYERS_GUEST_LONG_PLAY_NO_L9 = NUKE_LAYERS_GUEST_ULTRA_SOFT;

    static {
        try {
            System.loadLibrary("vcore");
            sLoaded = true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load libvcore.so: " + t.getMessage());
        }
    }

    public static boolean isLoaded() {
        return sLoaded;
    }

    public static void init() {
        if (sLoaded) {
            nativeInit(Build.VERSION.SDK_INT);
        }
    }

    public static void initSeccomp() {
        if (sLoaded) {
            nativeInitSeccomp();
        }
    }

    public static void nukeTargetLibrary(String libName) {
        nukeTargetLibrary(libName, NUKE_LAYERS_GUEST_LONG_PLAY);
    }

    public static void nukeTargetLibrary(String libName, int layerMask, boolean holdFileRead) {
        int m = layerMask < 0 ? NUKE_LAYERS_ALL : layerMask;
        if (holdFileRead) {
            m |= NUKE_HOLD_FILE_READ;
        } else {
            m &= ~NUKE_HOLD_FILE_READ;
        }
        nukeTargetLibrary(libName, m);
    }

    public static void nukeTargetLibrary(String libName, int layerMask) {
        if (sLoaded && !TextUtils.isEmpty(libName)) {
            nativeNukeTargetLibrary(libName, layerMask);
        }
    }

    private static final java.util.Set<String> sAppliedGuestRedirects =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /**
     * Registers host -> guest path mapping in libvcore's MapsVirtualizer/PathMapper so memory map
     * scans see the guest package name, WITHOUT adding a destructive NativeCore.addIORule that
     * would redirect the virtual container's own /data/user/0/<host>/alok/... storage to an
     * inaccessible host /data/user/0/<guest>/ directory.
     */
    public static void applyGuestHostDataRedirect(String guestPackageName) {
        // Safe no-op: Do NOT call addPathRule(hostPrefix, guestPrefix), which would redirect
        // the host's own internal storage (/data/data/<host>/) to an inaccessible guest directory,
        // breaking all file and database access in the container and causing black screens.
    }

    public static long getPageSize() {
        return sLoaded ? nativeGetPageSize() : 4096;
    }

    public static boolean is16KbPage() {
        return sLoaded && nativeIs16KbPage();
    }

    public static void addPathRule(String vPrefix, String hostTarget) {
        if (sLoaded) {
            nativeAddPathRule(vPrefix, hostTarget);
        }
    }

    public static String resolvePath(String origPath) {
        return sLoaded ? nativeResolvePath(origPath) : origPath;
    }

    public static boolean initUserStorage(int userId, String pkgName, String basePath) {
        return sLoaded && nativeInitUserStorage(userId, pkgName, basePath);
    }

    public static String getPrimaryAbi() {
        return sLoaded ? nativeGetPrimaryAbi() : "arm64-v8a";
    }

    public static void setupCloaking(String guestPkg, String hostPkg) {
        if (sLoaded) {
            nativeSetupCloaking(guestPkg, hostPkg);
        }
        applyGuestHostDataRedirect(guestPkg);
    }

    public static String filterMaps(String rawMaps) {
        return sLoaded ? nativeFilterMaps(rawMaps) : rawMaps;
    }

    public static void initDiagnostics(String logDir) {
        if (sLoaded) {
            nativeInitDiagnostics(logDir);
        }
    }

    public static void startDiagnostics() {
        if (sLoaded) {
            nativeStartDiagnostics();
        }
    }

    public static void stopDiagnostics() {
        if (sLoaded) {
            nativeStopDiagnostics();
        }
    }

    public static void updateDiagnosticsFrame(float dt) {
        if (sLoaded) {
            nativeUpdateDiagnosticsFrame(dt);
        }
    }

    public static String getDiagnosticReport(String appName, int activeContainers) {
        return sLoaded ? nativeGetDiagnosticReport(appName, activeContainers) : "{}";
    }

    public static long getMemoryRssKb() {
        return sLoaded ? nativeGetMemoryRssKb() : 0;
    }

    public static long getMemoryPeakKb() {
        return sLoaded ? nativeGetMemoryPeakKb() : 0;
    }

    // Native declarations
    private static native void nativeInit(int apiLevel);
    private static native void nativeInitSeccomp();
    private static native void nativeNukeTargetLibrary(String libName, int layerMask);
    private static native long nativeGetPageSize();
    private static native boolean nativeIs16KbPage();
    private static native void nativeAddPathRule(String vPrefix, String hostTarget);
    private static native String nativeResolvePath(String origPath);
    private static native boolean nativeInitUserStorage(int userId, String pkgName, String basePath);
    private static native String nativeGetPrimaryAbi();
    private static native void nativeSetupCloaking(String guestPkg, String hostPkg);
    private static native String nativeFilterMaps(String rawMaps);
    private static native void nativeInitDiagnostics(String logDir);
    private static native void nativeStartDiagnostics();
    private static native void nativeStopDiagnostics();
    private static native void nativeUpdateDiagnosticsFrame(float dt);
    private static native String nativeGetDiagnosticReport(String appName, int activeContainers);
    private static native long nativeGetMemoryRssKb();
    private static native long nativeGetMemoryPeakKb();
}
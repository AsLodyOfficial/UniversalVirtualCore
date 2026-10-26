package com.universal.virtualcore.core;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.universal.virtualcore.cloaking.ContainerCloaker;

import org.alok.engine.Alok;
import org.alok.engine.app.BActivityThread;
import org.alok.engine.app.configuration.AppLifecycleCallback;
import org.alok.engine.app.configuration.ClientConfiguration;
import org.alok.engine.core.system.DaemonService;
import org.alok.engine.core.system.pm.BPackage;
import org.alok.engine.proxy.ProxyManifest;
import org.alok.engine.utils.provider.ProviderCall;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dalvik.system.DexClassLoader;

public class AlokDexInjector {
    private static final String TAG = "AlokDexInjector";
    private static final ScheduledExecutorService sAsyncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Alok-KeepAlive");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean sInjected = false;
    private static volatile boolean sCreated = false;
    private static volatile Context sHostContext;
    private static final AtomicBoolean sDaemonBound = new AtomicBoolean(false);
    private static final AtomicBoolean sKeepAliveScheduled = new AtomicBoolean(false);

    private static final ServiceConnection sDaemonConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            sDaemonBound.set(true);
            Log.i(TAG, "Bound to DaemonService in :server (BIND_IMPORTANT active — freezer immunity enabled)");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sDaemonBound.set(false);
            Log.w(TAG, "DaemonService disconnected; re-binding to keep :server unfrozen...");
            if (sHostContext != null) {
                keepServerAlive(sHostContext);
            }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            sDaemonBound.set(false);
            if (sHostContext != null) {
                keepServerAlive(sHostContext);
            }
        }
    };

    /**
     * Ultra-fast attachBaseContext phase (<10ms).
     * Extracts support jars, checks class loader, loads native engine, and attaches Alok to base context.
     * Does NOT block the UI thread with IPC calls or Thread.sleep loops.
     */
    public static synchronized void injectAndAttach(Context context) {
        if (sInjected) return;
        try {
            sHostContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            Log.i(TAG, "Initializing Alok virtual engine attachBaseContext phase...");

            // 1. Pre-extract empty.jar -> alok/cache/empty.apk and junit.jar -> alok/cache/junit.apk
            //    with Android 14+ (API 34+) read-only enforcement so DexFile(emptyJar) never throws SecurityException
            try {
                File cacheDir = context.getCacheDir();
                File dataRoot = (cacheDir != null && cacheDir.getParentFile() != null)
                        ? cacheDir.getParentFile()
                        : (context.getFilesDir() != null ? context.getFilesDir().getParentFile() : null);
                if (dataRoot != null) {
                    File alokCacheDir = new File(new File(dataRoot, "alok"), "cache");
                    if (!alokCacheDir.exists()) {
                        alokCacheDir.mkdirs();
                    }
                    File emptyApk = new File(alokCacheDir, "empty.apk");
                    File junitApk = new File(alokCacheDir, "junit.apk");
                    extractAssetReadOnly(context, "empty.jar", emptyApk);
                    extractAssetReadOnly(context, "junit.jar", junitApk);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Jar cache pre-extraction notice: " + t.getMessage());
            }

            // 2. Check if Alok engine classes are already present in ClassLoader (unified classes.jar mode)
            boolean alokAlreadyLoaded = false;
            try {
                Class.forName("org.alok.engine.Alok");
                alokAlreadyLoaded = true;
                Log.i(TAG, "Alok engine classes already present in ClassLoader - unified mode.");
            } catch (ClassNotFoundException ignored) {}

            if (!alokAlreadyLoaded) {
                // Fallback: extract and inject alok_core.dex from assets
                File dexDir = new File(context.getFilesDir(), "vcore_dex");
                if (!dexDir.exists()) dexDir.mkdirs();
                File optDir = new File(context.getCodeCacheDir(), "vcore_opt");
                if (!optDir.exists()) optDir.mkdirs();

                File targetDex = new File(dexDir, "alok_core.dex");
                extractAssetReadOnly(context, "alok_core.dex", targetDex);

                // 3. Inject into host PathClassLoader
                injectDex(context.getClassLoader(), targetDex, optDir, context.getApplicationInfo().nativeLibraryDir);
            }

            // 4. Load native Alok engine library (libalok.so)
            try {
                System.loadLibrary("alok");
                Log.i(TAG, "Loaded libalok.so native engine successfully.");
            } catch (Throwable t) {
                Log.w(TAG, "System.loadLibrary(alok) note: " + t.getMessage());
            }

            // 5. Initialize close code and attach Alok engine with ClientConfiguration (Vanta parity)
            try {
                Alok.get().closeCodeInit();
            } catch (Throwable t) {
                Log.w(TAG, "Alok.closeCodeInit() note: " + t.getMessage());
            }

            try {
                Alok.get().doAttachBaseContext(context, new ClientConfiguration() {
                    @Override
                    public String getHostPackageName() {
                        return context.getPackageName();
                    }

                    @Override
                    public String getLogSenderChatId() {
                        return "";
                    }

                    @Override
                    public boolean isDisableFlagSecure() {
                        return true;
                    }

                    @Override
                    public boolean isEnableDaemonService() {
                        return true;
                    }

                    @Override
                    public boolean isEnableLauncherActivity() {
                        return false;
                    }

                    @Override
                    public boolean isUseVpnNetwork() {
                        return false;
                    }

                    @Override
                    public boolean requestInstallPackage(File file, int userId) {
                        return true;
                    }
                });

                Alok.get().addAppLifecycleCallback(new AppLifecycleCallback() {
                    @Override
                    public void beforeCreateApplication(String packageName, String processName, Context appContext, int userId) {
                        try {
                            if (appContext != null) {
                                injectLegacyAndSplitDex(appContext.getClassLoader(), appContext.getApplicationInfo());
                                ContainerCloaker.applyCloaking(appContext, packageName);
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "beforeCreateApplication hook note: " + t.getMessage());
                        }
                    }

                    @Override
                    public void beforeApplicationOnCreate(String packageName, String processName, Application application, int userId) {
                        try {
                            if (application != null) {
                                injectLegacyAndSplitDex(application.getClassLoader(), application.getApplicationInfo());
                                ContainerCloaker.applyCloaking(application, packageName);
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "beforeApplicationOnCreate hook note: " + t.getMessage());
                        }
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "Alok.doAttachBaseContext note: " + t.getMessage(), t);
            }

            sInjected = true;
            Log.i(TAG, "Alok virtual engine attached successfully (<10ms).");
        } catch (Throwable t) {
            Log.e(TAG, "injectAndAttach error: " + t.getMessage(), t);
        }
    }

    /**
     * Prevents Android 12–16 Cached App Freezer from ever freezing the :server process
     * while a guest app (:p0) or host process is running.
     * Binds DaemonService with BIND_AUTO_CREATE | BIND_IMPORTANT | BIND_ABOVE_CLIENT so :server
     * inherits the foreground OOM adj of the visible game process (:p0).
     */
    public static void keepServerAlive(Context context) {
        final Context ctx = context != null
                ? (context.getApplicationContext() != null ? context.getApplicationContext() : context)
                : (sHostContext != null ? sHostContext : Alok.getContext());
        if (ctx == null) return;
        sHostContext = ctx;

        sAsyncExecutor.execute(() -> {
            try {
                Intent intent = new Intent(ctx, DaemonService.class);
                try {
                    ctx.startService(intent);
                } catch (Throwable ignored) {
                    // On Android 12+ background startService may throw if not yet in foreground;
                    // bindService with BIND_AUTO_CREATE below always works without restriction.
                }
                int flags = Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT | Context.BIND_ABOVE_CLIENT;
                ctx.bindService(intent, sDaemonConnection, flags);
            } catch (Throwable t) {
                Log.w(TAG, "keepServerAlive bind note: " + t.getMessage());
            }
        });

        if (sKeepAliveScheduled.compareAndSet(false, true)) {
            sAsyncExecutor.scheduleAtFixedRate(() -> {
                try {
                    if (!sDaemonBound.get() && sHostContext != null) {
                        Intent intent = new Intent(sHostContext, DaemonService.class);
                        int flags = Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT | Context.BIND_ABOVE_CLIENT;
                        sHostContext.bindService(intent, sDaemonConnection, flags);
                    }
                    // Lightweight binder ping to SystemCallProvider to keep :server unfrozen
                    Bundle arg = new Bundle();
                    arg.putString("_B_|_server_name_", "package_manager");
                    ProviderCall.callSafely(ProxyManifest.getBindProvider(), "VM", null, arg);
                } catch (Throwable ignored) {
                }
            }, 15, 20, TimeUnit.SECONDS);
        }
    }

    /**
     * Synchronously wakes :server if it was frozen by the OS Cached App Freezer.
     */
    public static void wakeServerNow() {
        try {
            Context ctx = sHostContext != null ? sHostContext : Alok.getContext();
            if (ctx != null) {
                try {
                    Intent intent = new Intent(ctx, DaemonService.class);
                    int flags = Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT | Context.BIND_ABOVE_CLIENT;
                    ctx.bindService(intent, sDaemonConnection, flags);
                } catch (Throwable ignored) {}
            }
            Bundle arg = new Bundle();
            arg.putString("_B_|_server_name_", "package_manager");
            ProviderCall.callSafely(ProxyManifest.getBindProvider(), "VM", null, arg);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Schedules the onCreate() initialization of Alok engine asynchronously.
     * This avoids blocking the main thread during attachBaseContext / Application startup,
     * completely eliminating black screens and watchdog ANRs.
     */
    public static void scheduleAsyncCreate(Context context) {
        if (context != null && sHostContext == null) {
            sHostContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        }
        keepServerAlive(sHostContext);
        if (sCreated) return;
        sAsyncExecutor.submit(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            doCreateInternal();
        });
    }

    /**
     * Safe engine creation hook. Never blocks the main UI thread.
     */
    public static void onCreate() {
        if (sCreated) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sAsyncExecutor.submit(AlokDexInjector::doCreateInternal);
        } else {
            doCreateInternal();
        }
    }

    private static synchronized void doCreateInternal() {
        if (sCreated) return;
        try {
            Log.i(TAG, "Executing Alok engine doCreate()...");
            keepServerAlive(sHostContext);
            Alok.get().doCreate();
            try {
                Alok.getBPackageManager().getService();
                Alok.getBActivityManager().getService();
            } catch (Throwable ignored) {}
            sCreated = true;
            Log.i(TAG, "Alok engine doCreate() completed successfully. Services ready!");
        } catch (Throwable t) {
            Log.w(TAG, "Alok doCreate() note: " + t.getMessage(), t);
        }
    }

    private static final ThreadLocal<Boolean> sAutoInstalling = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> sBypassPmHook = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> sInRepair = new ThreadLocal<>();

    public static PackageInfo getHostPackageInfoUnchecked(String packageName, int flags) {
        if (packageName == null || packageName.isEmpty()) return null;
        boolean prev = Boolean.TRUE.equals(sBypassPmHook.get());
        sBypassPmHook.set(Boolean.TRUE);
        try {
            Context ctx = sHostContext != null ? sHostContext : Alok.getContext();
            if (ctx != null && ctx.getPackageManager() != null) {
                return ctx.getPackageManager().getPackageInfo(packageName, flags);
            }
            return Alok.getPackageManager().getPackageInfo(packageName, flags);
        } catch (Throwable ignored) {
            return null;
        } finally {
            sBypassPmHook.set(prev ? Boolean.TRUE : Boolean.FALSE);
        }
    }

    public static ApplicationInfo getHostApplicationInfoUnchecked(String packageName, int flags) {
        if (packageName == null || packageName.isEmpty()) return null;
        boolean prev = Boolean.TRUE.equals(sBypassPmHook.get());
        sBypassPmHook.set(Boolean.TRUE);
        try {
            Context ctx = sHostContext != null ? sHostContext : Alok.getContext();
            if (ctx != null && ctx.getPackageManager() != null) {
                return ctx.getPackageManager().getApplicationInfo(packageName, flags);
            }
            return Alok.getPackageManager().getApplicationInfo(packageName, flags);
        } catch (Throwable ignored) {
            return null;
        } finally {
            sBypassPmHook.set(prev ? Boolean.TRUE : Boolean.FALSE);
        }
    }

    /**
     * Replaces IPackageManagerProxy$GetPackageInfo.hook(Object, Method, Object[]).
     * Crucial fix: never redirect :server or main process PackageManager calls back into BPackageManager!
     */
    public static Object hookGetPackageInfo(Object who, Method method, Object[] args) throws Throwable {
        if (Boolean.TRUE.equals(sBypassPmHook.get()) || Alok.get().isServerProcess() || Alok.get().isMainProcess()) {
            try {
                return method.invoke(who, args);
            } catch ( java.lang.reflect.InvocationTargetException e) {
                if (e.getTargetException() != null) throw e.getTargetException();
                throw e;
            }
        }
        try {
            String pkg = (args != null && args.length > 0 && args[0] instanceof String) ? (String) args[0] : null;
            int flags = (args != null && args.length > 1) ? org.alok.engine.utils.MethodParameterUtils.toInt(args[1]) : 0;
            if (pkg != null) {
                PackageInfo pi = Alok.getBPackageManager().getPackageInfo(pkg, flags, Alok.getUserId());
                if (pi != null) {
                    return pi;
                }
            }
            return SocialLoginManager.invokeHostPmIfAllowed(method, who, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Replaces IPackageManagerProxy$GetApplicationInfo.hook(Object, Method, Object[]).
     * Crucial fix: never redirect :server or main process PackageManager calls back into BPackageManager!
     */
    public static Object hookGetApplicationInfo(Object who, Method method, Object[] args) throws Throwable {
        if (Boolean.TRUE.equals(sBypassPmHook.get()) || Alok.get().isServerProcess() || Alok.get().isMainProcess()) {
            try {
                return method.invoke(who, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getTargetException() != null) throw e.getTargetException();
                throw e;
            }
        }
        try {
            String pkg = (args != null && args.length > 0 && args[0] instanceof String) ? (String) args[0] : null;
            int flags = (args != null && args.length > 1) ? org.alok.engine.utils.MethodParameterUtils.toInt(args[1]) : 0;
            if (pkg != null) {
                ApplicationInfo ai = Alok.getBPackageManager().getApplicationInfo(pkg, flags, Alok.getUserId());
                if (ai != null) {
                    return ai;
                }
            }
            return SocialLoginManager.invokeHostPmIfAllowed(method, who, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Ensures a target package is installed inside BPackageManagerService before launching it.
     * Automatically installs the full Google Mobile Services suite (GMS, GSF, Play Store, Play Games)
     * matching Vanta parity so Google Sign-In, Play Store, and games run smoothly without black screens.
     */
    public static void ensureInstalledBeforeLaunch(String pkg, int userId) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(Alok.getHostPkg())) return;
        if (Boolean.TRUE.equals(sAutoInstalling.get())) return;
        sAutoInstalling.set(Boolean.TRUE);
        try {
            wakeServerNow();
            if (org.alok.engine.core.GmsCore.isSupportGms() && org.alok.engine.core.GmsCore.isGoogleAppOrService(pkg)) {
                try {
                    org.alok.engine.core.GmsCore.ensureEssentialGmsInstalled(userId);
                } catch (Throwable t) {
                    Log.w(TAG, "GmsCore.ensureEssentialGmsInstalled note: " + t.getMessage());
                }
            }
            // Ensure target package itself is installed and its APK path is fresh (handles host app reinstall/update)
            autoInstallIfHostHas(pkg, userId);

            // Pre-seed storage and cache directories before launch (resolves HDmpve and UE4 logs)
            precreateGuestDirs(pkg, userId);

            // Sync host Google accounts so Play Store / GMS immediately detect them
            try {
                Context hCtx = sHostContext != null ? sHostContext : Alok.getContext();
                com.universal.virtualcore.auth.VirtualAccountHook.syncHostAccounts(hCtx);
            } catch (Throwable ignored) {}

            // Mount OBB directories in IOCore and NativeCore (libalok.so) so Unreal Engine 4 / games can read OBB pak files
            com.universal.virtualcore.package_mgr.ObbVirtualizer.mountObb(pkg, userId);
        } catch (Throwable t) {
            Log.w(TAG, "ensureInstalledBeforeLaunch notice for " + pkg + ": " + t.getMessage());
        } finally {
            sAutoInstalling.set(Boolean.FALSE);
        }
    }

    /**
     * Hooked into BActivityThread.handleBindApplication(String, String) entry.
     * Ensures package paths are healed if reinstalled on host, pre-seeds dirs, and injects legacy HTTP classes.
     */
    public static void onBeforeBindApplication(String packageName, String processName) {
        try {
            Log.e("VCoreDiag", "onBeforeBindApplication START pkg=" + packageName + " proc=" + processName);
            int userId = 0;
            try {
                userId = BActivityThread.getUserId();
            } catch (Throwable ignored) {}
            ensureInstalledBeforeLaunch(packageName, userId);
            Context ctx = Alok.getContext();
            if (ctx != null) {
                injectLegacyAndSplitDex(ctx.getClassLoader(), null);
                try {
                    com.universal.virtualcore.auth.VirtualAccountHook.syncHostAccounts(ctx);
                } catch (Throwable ignored) {}
            }
            // Ensure OBB is mounted inside the child game process itself before native libraries load
            com.universal.virtualcore.package_mgr.ObbVirtualizer.mountObb(packageName, userId);
            Log.e("VCoreDiag", "onBeforeBindApplication DONE pkg=" + packageName);
        } catch (Throwable t) {
            Log.w(TAG, "onBeforeBindApplication note for " + packageName + ": " + t.getMessage());
        }
    }

    public static boolean autoInstallIfHostHas(String packageName, int userId) {
        if (packageName == null || packageName.isEmpty() || packageName.equals(Alok.getHostPkg())) {
            return false;
        }
        try {
            PackageInfo hostPi = getHostPackageInfoUnchecked(packageName, 0);
            ApplicationInfo hostAppInfo = hostPi != null ? hostPi.applicationInfo : null;

            if (Alok.getBPackageManager().isInstalled(packageName, userId)) {
                boolean needsRefresh = false;
                try {
                    ApplicationInfo vAppInfo = Alok.getBPackageManager().getApplicationInfo(packageName, 0, userId);
                    if (vAppInfo == null || vAppInfo.sourceDir == null || !new File(vAppInfo.sourceDir).exists()) {
                        needsRefresh = true;
                        Log.w("VCoreDiag", "Detected missing/deleted APK path for installed package " + packageName +
                                " (vSourceDir=" + (vAppInfo != null ? vAppInfo.sourceDir : "null") + ") — refreshing!");
                    } else if (hostAppInfo != null && hostAppInfo.sourceDir != null &&
                            vAppInfo.sourceDir.startsWith("/data/app/") &&
                            !vAppInfo.sourceDir.equals(hostAppInfo.sourceDir)) {
                        needsRefresh = true;
                        Log.w("VCoreDiag", "Detected host reinstall/update for " + packageName +
                                ": old=" + vAppInfo.sourceDir + " -> new=" + hostAppInfo.sourceDir + " — refreshing!");
                    }
                } catch (Throwable t) {
                    needsRefresh = true;
                    Log.w("VCoreDiag", "getApplicationInfo threw for " + packageName + " (" + t.getMessage() + ") — refreshing!");
                }
                if (!needsRefresh) {
                    return true;
                }
            }

            // 1. Try installing/refreshing from host PackageManager if present
            if (hostAppInfo != null) {
                try {
                    Log.i("VCoreDiag", "Installing/refreshing host package in virtual container: " + packageName +
                            " (sourceDir=" + hostAppInfo.sourceDir + ", userId=" + userId + ")");
                    Alok.get().installPackageAsUser(packageName, userId);
                    if (Alok.getBPackageManager().isInstalled(packageName, userId)) {
                        return true;
                    }
                } catch (Throwable t) {
                    Log.w("VCoreDiag", "installPackageAsUser(host) note for " + packageName + ": " + t.getMessage());
                }
            }
            // 2. Fallback: try installing from PackageCache if standalone APK was registered
            try {
                com.universal.virtualcore.package_mgr.model.VirtualPackage vp =
                        com.universal.virtualcore.package_mgr.PackageCache.get(packageName);
                if (vp != null && vp.sourcePath != null) {
                    File apkFile = new File(vp.sourcePath);
                    if (apkFile.exists()) {
                        Log.i("VCoreDiag", "Installing/refreshing cached APK into virtual container: " + packageName + " (userId=" + userId + ")");
                        Alok.get().installPackageAsUser(apkFile, userId);
                        return Alok.getBPackageManager().isInstalled(packageName, userId);
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Log.w(TAG, "autoInstallIfHostHas notice for " + packageName + ": " + t.getMessage());
        }
        return false;
    }

    /**
     * Hooked into PackageManagerCompat.generateApplicationInfo(BPackage, int, BPackageUserState, int) at entry.
     * Heals stale BPackage.baseCodePath / splitCodePaths / applicationInfo when a host app (like BGMI)
     * is reinstalled or updated on the phone, preventing NullPointerException in getPackageArchiveInfo().
     */
    public static void repairBPackageIfNeeded(BPackage bPackage) {
        if (bPackage == null || bPackage.packageName == null) return;
        if (Boolean.TRUE.equals(sInRepair.get())) return;
        sInRepair.set(Boolean.TRUE);
        try {
            String pkgName = bPackage.packageName;
            String baseCodePath = bPackage.baseCodePath;
            boolean baseMissing = (baseCodePath == null || baseCodePath.isEmpty() || !new File(baseCodePath).exists());
            boolean isSystemInstall = (bPackage.installOption != null && bPackage.installOption.isFlag(1));

            PackageInfo hostPi = null;
            if (baseMissing || isSystemInstall || bPackage.applicationInfo == null) {
                hostPi = getHostPackageInfoUnchecked(pkgName, 0);
            }

            if (hostPi != null && hostPi.applicationInfo != null && hostPi.applicationInfo.sourceDir != null) {
                ApplicationInfo hostAi = hostPi.applicationInfo;
                if (baseMissing || (isSystemInstall && !hostAi.sourceDir.equals(baseCodePath))) {
                    Log.w("VCoreDiag", "repairBPackageIfNeeded: healing stale APK path for " + pkgName +
                            " from " + baseCodePath + " -> " + hostAi.sourceDir);
                    bPackage.baseCodePath = hostAi.sourceDir;
                    bPackage.splitCodePaths = hostAi.splitSourceDirs;
                    bPackage.applicationInfo = new ApplicationInfo(hostAi);
                } else if (bPackage.applicationInfo == null) {
                    bPackage.applicationInfo = new ApplicationInfo(hostAi);
                }
            } else if (baseMissing && bPackage.applicationInfo == null) {
                // Prevent NullPointerException in PackageManagerCompat.generateApplicationInfo line 41
                // when getPackageArchiveInfo(baseCodePath, 0) returns null for a deleted APK
                Log.w("VCoreDiag", "repairBPackageIfNeeded: creating fallback ApplicationInfo for missing APK " + pkgName);
                ApplicationInfo fallback = new ApplicationInfo();
                fallback.packageName = pkgName;
                fallback.processName = pkgName;
                fallback.sourceDir = baseCodePath != null ? baseCodePath : "";
                fallback.publicSourceDir = fallback.sourceDir;
                fallback.targetSdkVersion = 34;
                bPackage.applicationInfo = fallback;
            }
        } catch (Throwable t) {
            Log.w("VCoreDiag", "repairBPackageIfNeeded error: " + t.getMessage());
        } finally {
            sInRepair.set(Boolean.FALSE);
        }
    }

    /**
     * Hooked into PackageManagerCompat.generateApplicationInfo(BPackage, int, BPackageUserState, int) before return.
     * Ensures nativeLibraryDir points to a valid directory containing native libraries (falling back to the host's
     * nativeLibraryDir when the virtual lib dir is empty or null), and ensures splitSourceDirs and sharedLibraryFiles
     * are populated.
     */
    public static ApplicationInfo postFixGeneratedAppInfo(ApplicationInfo appInfo, BPackage bPackage) {
        if (appInfo == null) return null;
        if (Boolean.TRUE.equals(sInRepair.get())) return appInfo;
        sInRepair.set(Boolean.TRUE);
        try {
            String pkgName = appInfo.packageName != null ? appInfo.packageName : (bPackage != null ? bPackage.packageName : null);
            if (pkgName == null) return appInfo;

            boolean needHostLookup = false;
            if (appInfo.nativeLibraryDir == null || appInfo.nativeLibraryDir.isEmpty()) {
                needHostLookup = true;
            } else {
                File libDir = new File(appInfo.nativeLibraryDir);
                String[] files = libDir.exists() ? libDir.list() : null;
                if (files == null || files.length == 0) {
                    needHostLookup = true;
                }
            }

            if (needHostLookup || appInfo.splitSourceDirs == null) {
                ApplicationInfo hostAi = getHostApplicationInfoUnchecked(pkgName, 0);
                if (hostAi != null) {
                    if (needHostLookup && hostAi.nativeLibraryDir != null && !hostAi.nativeLibraryDir.isEmpty()) {
                        appInfo.nativeLibraryDir = hostAi.nativeLibraryDir;
                    }
                    if (appInfo.splitSourceDirs == null && hostAi.splitSourceDirs != null) {
                        appInfo.splitSourceDirs = hostAi.splitSourceDirs;
                        appInfo.splitPublicSourceDirs = hostAi.splitPublicSourceDirs != null
                                ? hostAi.splitPublicSourceDirs
                                : hostAi.splitSourceDirs;
                    }
                    if ((appInfo.sourceDir == null || !new File(appInfo.sourceDir).exists()) && hostAi.sourceDir != null) {
                        appInfo.sourceDir = hostAi.sourceDir;
                        appInfo.publicSourceDir = hostAi.publicSourceDir != null ? hostAi.publicSourceDir : hostAi.sourceDir;
                    }
                }
            }

            // Ensure org.apache.http.legacy.jar is present in sharedLibraryFiles
            String legacyHttpJar = "/system/framework/org.apache.http.legacy.jar";
            if (new File(legacyHttpJar).exists()) {
                if (appInfo.sharedLibraryFiles == null || appInfo.sharedLibraryFiles.length == 0) {
                    appInfo.sharedLibraryFiles = new String[]{legacyHttpJar};
                } else {
                    boolean found = false;
                    for (String s : appInfo.sharedLibraryFiles) {
                        if (legacyHttpJar.equals(s)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        String[] next = Arrays.copyOf(appInfo.sharedLibraryFiles, appInfo.sharedLibraryFiles.length + 1);
                        next[appInfo.sharedLibraryFiles.length] = legacyHttpJar;
                        appInfo.sharedLibraryFiles = next;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w("VCoreDiag", "postFixGeneratedAppInfo note: " + t.getMessage());
        } finally {
            sInRepair.set(Boolean.FALSE);
        }
        return appInfo;
    }

    private static final Set<String> sInjectedClassLoaderPaths = Collections.synchronizedSet(new HashSet<>());

    /**
     * Injects /system/framework/org.apache.http.legacy.jar, sharedLibraryFiles, splitSourceDirs, and nativeLibraryDir
     * into the guest ClassLoader and system/host ClassLoaders.
     * Fixes NoClassDefFoundError: Lorg/apache/http/ProtocolVersion; in BGMI (Tencent iMSDK Volley) on Android 16,
     * and ensures split APK dex/native libraries are always resolvable.
     */
    public static void injectLegacyAndSplitDex(ClassLoader classLoader, ApplicationInfo appInfo) {
        try {
            List<String> dexPaths = new ArrayList<>();
            String[] legacyJars = new String[]{
                    "/system/framework/org.apache.http.legacy.jar",
                    "/system/framework/org.apache.http.legacy.boot.jar"
            };
            for (String jar : legacyJars) {
                if (new File(jar).exists()) {
                    dexPaths.add(jar);
                }
            }
            if (appInfo != null) {
                if (appInfo.sharedLibraryFiles != null) {
                    for (String lib : appInfo.sharedLibraryFiles) {
                        if (lib != null && new File(lib).exists() && !dexPaths.contains(lib)) {
                            dexPaths.add(lib);
                        }
                    }
                }
                if (appInfo.splitSourceDirs != null) {
                    for (String split : appInfo.splitSourceDirs) {
                        if (split != null && new File(split).exists() && !dexPaths.contains(split)) {
                            dexPaths.add(split);
                        }
                    }
                }
            }

            List<ClassLoader> loaders = new ArrayList<>();
            if (classLoader != null) loaders.add(classLoader);
            try {
                Context hostCtx = Alok.getContext();
                if (hostCtx != null && hostCtx.getClassLoader() != null && !loaders.contains(hostCtx.getClassLoader())) {
                    loaders.add(hostCtx.getClassLoader());
                }
            } catch (Throwable ignored) {}
            try {
                ClassLoader sysCl = ClassLoader.getSystemClassLoader();
                if (sysCl != null && !loaders.contains(sysCl)) {
                    loaders.add(sysCl);
                }
            } catch (Throwable ignored) {}

            Method addDexPathMethod = null;
            Method addNativePathMethod = null;
            try {
                Class<?> baseDexCl = Class.forName("dalvik.system.BaseDexClassLoader");
                addDexPathMethod = baseDexCl.getDeclaredMethod("addDexPath", String.class);
                addDexPathMethod.setAccessible(true);
                addNativePathMethod = baseDexCl.getDeclaredMethod("addNativePath", Collection.class);
                addNativePathMethod.setAccessible(true);
            } catch (Throwable ignored) {}

            for (ClassLoader cl : loaders) {
                if (cl == null) continue;
                int clId = System.identityHashCode(cl);
                for (String path : dexPaths) {
                    // Only inject app splits into the app's own ClassLoader, whereas legacy framework jars go into all
                    if (!path.startsWith("/system/framework/") && cl != classLoader) {
                        continue;
                    }
                    String key = clId + ":" + path;
                    if (!sInjectedClassLoaderPaths.add(key)) {
                        continue;
                    }
                    try {
                        if (addDexPathMethod != null && isBaseDexClassLoader(cl)) {
                            addDexPathMethod.invoke(cl, path);
                            Log.i("VCoreDiag", "addDexPath succeeded for " + path + " on " + cl.getClass().getSimpleName());
                        }
                    } catch (Throwable t) {
                        Log.w("VCoreDiag", "addDexPath fallback for " + path + ": " + t.getMessage());
                    }
                }
            }

            // Also ensure nativeLibraryDir and APK!/lib/arm64-v8a paths are registered in guest ClassLoader
            if (classLoader != null && appInfo != null && addNativePathMethod != null && isBaseDexClassLoader(classLoader)) {
                List<String> nativePaths = new ArrayList<>();
                if (appInfo.nativeLibraryDir != null && new File(appInfo.nativeLibraryDir).exists()) {
                    nativePaths.add(appInfo.nativeLibraryDir);
                }
                if (appInfo.packageName != null) {
                    ApplicationInfo hostAi = getHostApplicationInfoUnchecked(appInfo.packageName, 0);
                    if (hostAi != null && hostAi.nativeLibraryDir != null &&
                            new File(hostAi.nativeLibraryDir).exists() &&
                            !nativePaths.contains(hostAi.nativeLibraryDir)) {
                        nativePaths.add(hostAi.nativeLibraryDir);
                    }
                }
                if (appInfo.sourceDir != null && new File(appInfo.sourceDir).exists()) {
                    nativePaths.add(appInfo.sourceDir + "!/lib/arm64-v8a");
                }
                if (appInfo.splitSourceDirs != null) {
                    for (String split : appInfo.splitSourceDirs) {
                        if (split != null && new File(split).exists()) {
                            nativePaths.add(split + "!/lib/arm64-v8a");
                        }
                    }
                }
                List<String> toAdd = new ArrayList<>();
                int clId = System.identityHashCode(classLoader);
                for (String np : nativePaths) {
                    if (sInjectedClassLoaderPaths.add(clId + ":native:" + np)) {
                        toAdd.add(np);
                    }
                }
                if (!toAdd.isEmpty()) {
                    try {
                        addNativePathMethod.invoke(classLoader, toAdd);
                        Log.i("VCoreDiag", "Added native library paths to ClassLoader for " + appInfo.packageName + ": " + toAdd);
                    } catch (Throwable t) {
                        Log.w("VCoreDiag", "addNativePath note: " + t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            Log.w("VCoreDiag", "injectLegacyAndSplitDex error: " + t.getMessage());
        }
    }

    private static boolean isBaseDexClassLoader(ClassLoader cl) {
        try {
            return Class.forName("dalvik.system.BaseDexClassLoader").isInstance(cl);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void precreateGuestDirs(String pkg, int userId) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(Alok.getHostPkg())) return;
        try {
            Context ctx = sHostContext != null ? sHostContext : Alok.getContext();
            String hostPkg = Alok.getHostPkg();
            if (hostPkg == null || hostPkg.isEmpty()) {
                if (ctx != null) hostPkg = ctx.getPackageName();
            }

            List<File> dirsToCreate = new ArrayList<>();

            // 1. Virtual internal data paths
            // Path: /data/user/0/<hostPkg>/alok/data/user/0/<pkg>/
            if (ctx != null && ctx.getFilesDir() != null && ctx.getFilesDir().getParentFile() != null) {
                File appInternalRoot = ctx.getFilesDir().getParentFile();
                File alokDataRoot = new File(new File(new File(appInternalRoot, "alok"), "data"), "user");
                File userRoot = new File(alokDataRoot, String.valueOf(userId));
                File pkgDir = new File(userRoot, pkg);
                dirsToCreate.add(new File(pkgDir, "files"));
                dirsToCreate.add(new File(pkgDir, "files/logs"));
                dirsToCreate.add(new File(pkgDir, "files/xlog"));
                dirsToCreate.add(new File(pkgDir, "cache"));
                dirsToCreate.add(new File(pkgDir, "code_cache"));
                dirsToCreate.add(new File(pkgDir, "databases"));
                dirsToCreate.add(new File(pkgDir, "shared_prefs"));
            }

            // 2. Exact HDmpve anti-cheat virtual external path requested:
            // Path: /storage/emulated/0/Android/data/<hostPkg>/files/alok/storage/emulated/0/Android/data/<pkg>/cache
            File extRoot = Environment.getExternalStorageDirectory();
            if (extRoot == null) {
                extRoot = new File("/storage/emulated/0");
            }
            if (hostPkg != null) {
                File hostExtAlok = new File(new File(new File(new File(extRoot, "Android"), "data"), hostPkg), "files/alok/storage/emulated/0/Android/data/" + pkg);
                dirsToCreate.add(new File(hostExtAlok, "cache"));
                dirsToCreate.add(new File(hostExtAlok, "files"));
                dirsToCreate.add(new File(hostExtAlok, "files/logs"));
                dirsToCreate.add(new File(hostExtAlok, "files/UE4Game"));
                dirsToCreate.add(new File(hostExtAlok, "files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Logs"));
            }

            // 3. Real external storage paths for guest app & games (BGMI UE4Game / OBB)
            File guestExt = new File(new File(new File(extRoot, "Android"), "data"), pkg);
            dirsToCreate.add(new File(guestExt, "cache"));
            dirsToCreate.add(new File(guestExt, "files"));
            dirsToCreate.add(new File(guestExt, "files/logs"));
            dirsToCreate.add(new File(guestExt, "files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Logs"));
            dirsToCreate.add(new File(new File(new File(extRoot, "Android"), "obb"), pkg));

            for (File dir : dirsToCreate) {
                try {
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    if (dir.canWrite()) {
                        dir.setReadable(true, false);
                        dir.setWritable(true, false);
                        dir.setExecutable(true, false);
                    }
                } catch (Throwable ignored) {}
            }
            Log.i(TAG, "Pre-seeded directories for " + pkg + " (HDmpve & UE4Game ready)");
        } catch (Throwable t) {
            Log.w(TAG, "precreateGuestDirs notice for " + pkg + ": " + t.getMessage());
        }
    }

    public static boolean isCreated() {
        return sCreated;
    }

    public static void extractAssetReadOnly(Context context, String assetName, File outFile) {
        try (InputStream is = context.getAssets().open(assetName)) {
            int assetLen = is.available();
            if (outFile.exists() && outFile.length() == assetLen && assetLen > 0) {
                if (outFile.canWrite()) {
                    outFile.setReadable(true, false);
                    outFile.setReadOnly();
                }
                return;
            }
            if (outFile.exists()) {
                outFile.setWritable(true, true);
                outFile.delete();
            }
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buf = new byte[65536];
                int len;
                while ((len = is.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
                fos.flush();
            }
            if (outFile.canWrite()) {
                outFile.setReadable(true, false);
                outFile.setReadOnly();
            }
            Log.i(TAG, "Extracted read-only " + assetName + " -> " + outFile.getAbsolutePath() + " (" + outFile.length() + " bytes)");
        } catch (Throwable t) {
            Log.w(TAG, "extractAssetReadOnly notice for " + assetName + ": " + t.getMessage());
        }
    }

    private static void injectDex(ClassLoader classLoader, File dexFile, File optDir, String nativeLibDir) {
        try {
            if (dexFile.canWrite()) {
                dexFile.setReadOnly();
            }

            DexClassLoader dexClassLoader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    optDir.getAbsolutePath(),
                    nativeLibDir,
                    classLoader
            );

            Field pathListField = findField(classLoader, "pathList");
            Object hostPathList = pathListField.get(classLoader);

            Field dexPathListField = findField(dexClassLoader, "pathList");
            Object dexPathList = dexPathListField.get(dexClassLoader);

            Field dexElementsField = findField(hostPathList, "dexElements");
            Object[] hostElements = (Object[]) dexElementsField.get(hostPathList);

            Field extraElementsField = findField(dexPathList, "dexElements");
            Object[] extraElements = (Object[]) extraElementsField.get(dexPathList);

            if (extraElements != null && hostElements != null) {
                Object[] combined = (Object[]) Array.newInstance(
                        hostElements.getClass().getComponentType(),
                        hostElements.length + extraElements.length
                );
                System.arraycopy(extraElements, 0, combined, 0, extraElements.length);
                System.arraycopy(hostElements, 0, combined, extraElements.length, hostElements.length);
                dexElementsField.set(hostPathList, combined);
                Log.i(TAG, "Successfully injected " + extraElements.length + " Alok dex elements into PathClassLoader!");
            }
        } catch (Throwable t) {
            Log.e(TAG, "injectDex error: " + t.getMessage(), t);
        }
    }

    private static Field findField(Object instance, String name) throws NoSuchFieldException {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }
}

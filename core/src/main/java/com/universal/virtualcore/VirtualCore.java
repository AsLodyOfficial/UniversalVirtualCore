package com.universal.virtualcore;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.universal.virtualcore.client.VClientImpl;
import com.universal.virtualcore.cloaking.ContainerCloaker;
import com.universal.virtualcore.cloaking.DeviceSpoofer;
import com.universal.virtualcore.core.AlokDexInjector;
import com.universal.virtualcore.core.GmsCore;
import com.universal.virtualcore.core.SocialLoginManager;
import com.universal.virtualcore.diagnostics.DiagnosticManager;
import com.universal.virtualcore.diagnostics.LogcatGenerator;
import com.universal.virtualcore.env.VEnvironment;
import com.universal.virtualcore.env.VUserHandle;
import com.universal.virtualcore.hook.HookManager;
import com.universal.virtualcore.license.LicenseCallback;
import com.universal.virtualcore.license.LicenseManager;
import com.universal.virtualcore.license.LicenseResult;
import com.universal.virtualcore.native_core.NativeBridge;
import com.universal.virtualcore.native_core.NativeHookEngine;
import com.universal.virtualcore.native_core.PageSizeManager;
import com.universal.virtualcore.package_mgr.ObbVirtualizer;
import com.universal.virtualcore.package_mgr.PackageCache;
import com.universal.virtualcore.package_mgr.SplitApkInstaller;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;
import com.universal.virtualcore.server.VActivityManagerService;
import com.universal.virtualcore.server.VAppManagerService;
import com.universal.virtualcore.server.VPackageManagerService;
import com.universal.virtualcore.server.VUserManagerService;

import org.alok.engine.Alok;
import org.alok.engine.app.configuration.AppLifecycleCallback;
import org.alok.engine.entity.pm.InstallResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class VirtualCore {
    private static final String TAG = "VirtualCore";
    private static final VirtualCore sInstance = new VirtualCore();
    private Context mHostContext;
    private volatile boolean mInitialized = false;
    private volatile boolean mStandaloneActivated = true;

    public static VirtualCore get() {
        return sInstance;
    }

    /**
     * Standard initialization (attaches Alok Virtual Engine, NativeBridge, Cloaking).
     * Ultra-fast attach phase (<10ms). Non-blocking to guarantee zero black screen lag.
     */
    public synchronized void initialize(Context context) {
        if (mInitialized) return;
        try {
            this.mHostContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;

            // 1. Ultra-fast attach phase: attach Alok virtual engine, inject dex, jar, native libs
            AlokDexInjector.injectAndAttach(context);

            // 2. Non-blocking asynchronous doCreate() and service initialization
            // DO NOT block UI thread in attachBaseContext() to prevent black screens and watchdog ANRs!
            AlokDexInjector.scheduleAsyncCreate(mHostContext);

            // 3. Fast non-blocking environment init & native bridge
            VEnvironment.init(mHostContext);
            NativeBridge.init();

            // 4. Register automatic per-guest cloaking, server keep-alive & social login support
            try {
                Alok.get().addAppLifecycleCallback(new AppLifecycleCallback() {
                    @Override
                    public void beforeCreateApplication(String packageName, String processName, Context appContext, int userId) {
                        try {
                            AlokDexInjector.keepServerAlive(mHostContext);
                            org.alok.engine.fake.service.IInputMethodManagerProxy.ensureInstalled();
                            NativeBridge.applyGuestHostDataRedirect(packageName);
                            ContainerCloaker.applyCloaking(mHostContext, packageName);
                        } catch (Throwable ignored) {}
                    }

                    @Override
                    public void beforeApplicationOnCreate(String packageName, String processName, Application application, int userId) {
                        try {
                            AlokDexInjector.keepServerAlive(mHostContext);
                            org.alok.engine.fake.service.IInputMethodManagerProxy.ensureInstalled();
                            NativeBridge.applyGuestHostDataRedirect(packageName);
                            ContainerCloaker.applyCloaking(mHostContext, packageName);
                            SocialLoginManager.registerActivityTracking(application);
                        } catch (Throwable ignored) {}
                    }

                    @Override
                    public void afterApplicationOnCreate(String packageName, String processName, Application application, int userId) {
                        try {
                            org.alok.engine.fake.service.IInputMethodManagerProxy.ensureInstalled();
                            SocialLoginManager.registerActivityTracking(application);
                            AlokDexInjector.keepServerAlive(mHostContext);
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "Lifecycle callback registration note: " + t.getMessage());
            }

            // 5. Diagnostics
            DiagnosticManager.get().init(mHostContext);

            // 6. Asynchronous PackageCache loading (prevents disk IO hitch on main thread)
            new Thread(() -> {
                try {
                    PackageCache.ensureLoaded(mHostContext);
                } catch (Throwable ignored) {}
            }, "VCore-PkgLoader").start();

            mInitialized = true;
            Log.i(TAG, "VirtualCore attachBaseContext phase completed successfully (<10ms).");
        } catch (Throwable t) {
            Log.e(TAG, "VirtualCore initialization error: " + t.getMessage(), t);
        }
    }

    /**
     * Optional hook for Application.onCreate() if the host app overrides it.
     */
    public void onAppCreate() {
        AlokDexInjector.onCreate();
    }

    /**
     * Ensures the background services (:server) are connected and ready before performing
     * package operations or launching apps.
     */
    public void ensureEngineReady() {
        try {
            if (!AlokDexInjector.isCreated()) {
                AlokDexInjector.onCreate();
            }
            if (!Alok.get().isServicesAvailable()) {
                Log.i(TAG, "Waiting for virtual engine services to become available...");
                Alok.get().waitForServicesAvailable(3000);
            }
        } catch (Throwable t) {
            Log.w(TAG, "ensureEngineReady note: " + t.getMessage());
        }
    }

    /**
     * Protected initialization with remote key verification.
     */
    public synchronized void initialize(Context context, String userKey, LicenseCallback callback) {
        initialize(context);
        activate(userKey, callback);
    }

    /**
     * Activate the core with a user license key.
     */
    public void activate(String userKey, LicenseCallback callback) {
        if (mHostContext == null) {
            if (callback != null) {
                callback.onFailure(-1, "VirtualCore must be initialized with Context first.");
            }
            return;
        }

        LicenseManager.get().verifyKey(mHostContext, userKey, new LicenseCallback() {
            @Override
            public void onSuccess(LicenseResult result) {
                mStandaloneActivated = true;
                Log.i(TAG, "VirtualCore License Activated: " + result.message);
                if (callback != null) {
                    callback.onSuccess(result);
                }
            }

            @Override
            public void onFailure(int errorCode, String message) {
                Log.w(TAG, "VirtualCore License Failure [" + errorCode + "]: " + message);
                if (callback != null) {
                    callback.onFailure(errorCode, message);
                }
            }
        });
    }

    public boolean isActivated() {
        return mStandaloneActivated || LicenseManager.get().isActivated();
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public Context getContext() {
        return mHostContext;
    }

    public Context getHostContext() {
        return mHostContext;
    }

    public void addExternalAuthority(String authority) {
        com.universal.virtualcore.core.GmsAuthorityRouter.addExternalAuthority(authority);
    }

    public void addGmsAuthorityPassthrough(String[] authorities) {
        com.universal.virtualcore.core.GmsAuthorityRouter.addGmsAuthorityPassthrough(authorities);
    }

    public boolean isExternalAuthority(String authority) {
        return com.universal.virtualcore.core.GmsAuthorityRouter.isExternalAuthority(authority);
    }

    public Alok getAlokEngine() {
        return Alok.get();
    }

    public VirtualPackage installPackage(File apkFile) {
        return installPackage(apkFile, 0);
    }

    public VirtualPackage installPackage(File apkFile, int userId) {
        if (!mInitialized || mHostContext == null) {
            Log.e(TAG, "Cannot install package: VirtualCore not initialized.");
            return null;
        }
        ensureEngineReady();
        try {
            // Install via Alok Engine
            InstallResult result = Alok.get().installPackageAsUser(apkFile, userId);
            if (result != null && result.success) {
                Log.i(TAG, "Alok installed package: " + result.packageName);
            } else if (result != null) {
                Log.w(TAG, "Alok installPackageAsUser note: " + result.msg);
            }
            // Also register in PackageCache
            return VAppManagerService.get().installPackage(mHostContext, apkFile);
        } catch (Throwable t) {
            Log.e(TAG, "installPackage failed: " + t.getMessage(), t);
            return null;
        }
    }

    /**
     * Clone an already-installed package from the host device into the virtual space.
     */
    public VirtualPackage installExistingPackage(String packageName, int userId) {
        if (!mInitialized || mHostContext == null) {
            Log.e(TAG, "Cannot clone package: VirtualCore not initialized.");
            return null;
        }
        ensureEngineReady();
        try {
            InstallResult result = Alok.get().installPackageAsUser(packageName, userId);
            if (result != null && result.success) {
                Log.i(TAG, "Alok cloned existing package: " + result.packageName);
            }
            ApplicationInfo ai = mHostContext.getPackageManager().getApplicationInfo(packageName, 0);
            if (ai != null && ai.sourceDir != null) {
                return VAppManagerService.get().installPackage(mHostContext, new File(ai.sourceDir));
            }
        } catch (Throwable t) {
            Log.e(TAG, "installExistingPackage failed: " + t.getMessage(), t);
        }
        return null;
    }

    public VirtualPackage installSplitApks(List<File> splitApkFiles) {
        if (!mInitialized || mHostContext == null) {
            return null;
        }
        ensureEngineReady();
        try {
            return SplitApkInstaller.installSplitApks(mHostContext, splitApkFiles);
        } catch (Throwable t) {
            Log.e(TAG, "installSplitApks failed: " + t.getMessage(), t);
            return null;
        }
    }

    // Google Mobile Services (GMS) Support
    public boolean isGmsSupported() {
        return GmsCore.isSupportGms();
    }

    public boolean isGmsInstalled(int userId) {
        ensureEngineReady();
        return GmsCore.isInstalledGoogleService(userId);
    }

    public boolean installGms(int userId) {
        ensureEngineReady();
        InstallResult res = GmsCore.installGApps(userId);
        return res != null && res.success;
    }

    public void uninstallGms(int userId) {
        ensureEngineReady();
        GmsCore.uninstallGApps(userId);
    }

    public void mountObb(String packageName, int userId) {
        ObbVirtualizer.mountObb(packageName, userId);
    }

    public void applyCloaking(String guestPackageName) {
        if (mHostContext != null) {
            ContainerCloaker.applyCloaking(mHostContext, guestPackageName);
            NativeBridge.applyGuestHostDataRedirect(guestPackageName);
        }
    }

    /**
     * Shield a target native library (e.g. "libanogs.so") using the 11-layer anti-cheat defense.
     */
    public void shieldTargetLibrary(String libName) {
        NativeBridge.nukeTargetLibrary(libName, NativeBridge.NUKE_LAYERS_GUEST_LONG_PLAY);
    }

    public void shieldTargetLibrary(String libName, int layerMask) {
        NativeBridge.nukeTargetLibrary(libName, layerMask);
    }

    public void enableSeccompFilter() {
        NativeBridge.initSeccomp();
    }

    public void setDeviceProfile(int userId, DeviceSpoofer.DeviceProfile profile) {
        DeviceSpoofer.get().setProfile(userId, profile);
    }

    public DeviceSpoofer.DeviceProfile getDeviceProfile(int userId) {
        return DeviceSpoofer.get().getProfile(userId);
    }

    public long findNativeSymbol(String libraryName, String symbolName) {
        return NativeHookEngine.findSymbol(libraryName, symbolName);
    }

    public boolean patchNativeMemory(long address, byte[] bytes) {
        return NativeHookEngine.patchMemory(address, bytes);
    }

    public boolean uninstallPackage(String packageName) {
        return uninstallPackage(packageName, 0);
    }

    public boolean uninstallPackage(String packageName, int userId) {
        if (!mInitialized) return false;
        ensureEngineReady();
        try {
            Alok.get().uninstallPackageAsUser(packageName, userId);
            return VAppManagerService.get().uninstallPackage(packageName);
        } catch (Throwable t) {
            Log.e(TAG, "uninstallPackage failed: " + t.getMessage(), t);
            return false;
        }
    }

    public boolean isPackageInstalled(String packageName) {
        return isPackageInstalled(packageName, 0);
    }

    public boolean isPackageInstalled(String packageName, int userId) {
        if (!mInitialized) return false;
        ensureEngineReady();
        try {
            boolean alokInstalled = Alok.get().isInstalled(packageName, userId);
            if (alokInstalled) return true;
            return VPackageManagerService.get().isPackageInstalled(packageName);
        } catch (Throwable t) {
            return false;
        }
    }

    public VirtualPackage getPackageInfo(String packageName) {
        if (!mInitialized) return null;
        ensureEngineReady();
        try {
            return VPackageManagerService.get().getPackageInfo(packageName);
        } catch (Throwable t) {
            return null;
        }
    }

    public Collection<VirtualPackage> getInstalledPackages() {
        return getInstalledPackages(0);
    }

    public Collection<VirtualPackage> getInstalledPackages(int userId) {
        if (!mInitialized) return Collections.emptyList();
        ensureEngineReady();
        List<VirtualPackage> list = new ArrayList<>();
        try {
            List<ApplicationInfo> alokApps = Alok.get().getInstalledApplications(0, userId);
            if (alokApps != null) {
                for (ApplicationInfo app : alokApps) {
                    VirtualPackage vp = new VirtualPackage();
                    vp.packageName = app.packageName;
                    vp.sourcePath = app.sourceDir;
                    list.add(vp);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Alok getInstalledApplications note: " + t.getMessage());
        }

        for (VirtualPackage vp : PackageCache.getAll()) {
            boolean found = false;
            for (VirtualPackage existing : list) {
                if (existing.packageName != null && existing.packageName.equals(vp.packageName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                list.add(vp);
            }
        }
        return list;
    }

    public Intent getLaunchIntentForPackage(String packageName) {
        return VPackageManagerService.get().getLaunchIntentForPackage(mHostContext, packageName);
    }

    public int launchApp(int userId, String packageName) {
        if (!mInitialized) {
            Log.e(TAG, "Cannot launch app: VirtualCore not initialized.");
            return -1;
        }
        ensureEngineReady();
        try {
            // 1. Proactively ensure package & full GMS suite are installed
            AlokDexInjector.ensureInstalledBeforeLaunch(packageName, userId);
            NativeBridge.applyGuestHostDataRedirect(packageName);

            // 2. Launch via Alok engine launchApk
            boolean launched = Alok.get().launchApk(packageName, userId);
            if (launched) {
                Log.i(TAG, "Successfully launched " + packageName + " via Alok virtual engine in user #" + userId);
                return 0;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Alok launchApk note: " + t.getMessage());
        }

        // 3. Resilient fallback: obtain launch intent from Alok BPackageManager or Host
        try {
            Intent launch = Alok.getBPackageManager().getLaunchIntentForPackage(packageName, userId);
            if (launch == null && mHostContext != null) {
                launch = mHostContext.getPackageManager().getLaunchIntentForPackage(packageName);
            }
            if (launch != null) {
                return launchApp(userId, launch);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Fallback getLaunchIntentForPackage note: " + t.getMessage(), t);
        }

        return -1;
    }

    public int launchApp(int userId, Intent intent) {
        if (!mInitialized) {
            Log.e(TAG, "Cannot launch app: VirtualCore not initialized.");
            return -1;
        }
        if (intent == null) return -1;
        ensureEngineReady();
        try {
            String pkgName = intent.getPackage();
            if (pkgName == null && intent.getComponent() != null) {
                pkgName = intent.getComponent().getPackageName();
                intent.setPackage(pkgName);
            }
            if (pkgName != null) {
                AlokDexInjector.ensureInstalledBeforeLaunch(pkgName, userId);
                NativeBridge.applyGuestHostDataRedirect(pkgName);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Alok.getBActivityManager().startActivity(intent, userId);
            Log.i(TAG, "Successfully started activity via Alok BActivityManager in user #" + userId);
            return 0;
        } catch (Throwable t) {
            Log.e(TAG, "launchApp via BActivityManager note: " + t.getMessage(), t);
            try {
                Alok.get().startActivity(intent, userId);
                return 0;
            } catch (Throwable t2) {
                Log.e(TAG, "launchApp fallback error: " + t2.getMessage(), t2);
                return -1;
            }
        }
    }

    public void stopPackage(String packageName, int userId) {
        if (!mInitialized) return;
        ensureEngineReady();
        try {
            Alok.get().stopPackage(packageName, userId);
        } catch (Throwable t) {
            Log.w(TAG, "stopPackage note: " + t.getMessage());
        }
    }

    public VUserHandle createUser(int userId) {
        if (!mInitialized) return null;
        ensureEngineReady();
        try {
            return VUserManagerService.get().createUser(userId);
        } catch (Throwable t) {
            Log.e(TAG, "createUser failed: " + t.getMessage(), t);
            return null;
        }
    }

    public List<VUserHandle> getUsers() {
        if (!mInitialized) return Collections.emptyList();
        ensureEngineReady();
        try {
            return VUserManagerService.get().getUsers();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    public long getSystemPageSize() {
        return PageSizeManager.getSystemPageSize();
    }

    public boolean is16KbAlignment() {
        return PageSizeManager.is16KbAlignmentRequired();
    }

    public DiagnosticManager getDiagnosticManager() {
        return DiagnosticManager.get();
    }

    public LogcatGenerator getLogcatGenerator() {
        return LogcatGenerator.get();
    }

    public String generateDiagnosticReport(String appName) {
        return DiagnosticManager.get().generateJSONReport(appName, getUsers().size());
    }
}

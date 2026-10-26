package com.universal.virtualcore.client;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.universal.virtualcore.VirtualCore;
import com.universal.virtualcore.env.VEnvironment;
import com.universal.virtualcore.package_mgr.PackageCache;
import com.universal.virtualcore.package_mgr.model.ComponentInfo;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

public class VInstrumentation extends Instrumentation {
    private static final String TAG = "VInstrumentation";
    private final Instrumentation mBase;

    public VInstrumentation(Instrumentation base) {
        this.mBase = base;
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (intent != null && (className.contains("StubActivity") || className.contains("ContainerStubActivity"))) {
            String guestPkg = intent.getStringExtra("_v_pkg_");
            String guestAct = intent.getStringExtra("_v_activity_");
            int userId = intent.getIntExtra("_v_user_", 0);

            if (guestPkg != null) {
                VirtualPackage vPkg = PackageCache.get(guestPkg);
                String apkPath = vPkg != null ? vPkg.sourcePath : null;

                if (apkPath == null || !new File(apkPath).exists()) {
                    try {
                        if (VirtualCore.get().getContext() != null) {
                            ApplicationInfo ai = VirtualCore.get().getContext().getPackageManager().getApplicationInfo(guestPkg, 0);
                            apkPath = ai.sourceDir;
                        }
                    } catch (Throwable ignored) {}
                }

                if (apkPath != null && new File(apkPath).exists()) {
                    File apkFile = new File(apkPath);
                    try {
                        if (apkFile.canWrite()) {
                            apkFile.setReadable(true, false);
                            apkFile.setReadOnly();
                        }
                    } catch (Throwable ignored) {}
                    File libDir = VEnvironment.getPackageLibDirectory(userId, guestPkg);
                    extractNativeLibraries(apkFile, libDir);

                    File optDir = new File(VEnvironment.getHostVirtualRoot(), "dexopt_" + guestPkg);
                    if (!optDir.exists()) optDir.mkdirs();

                    DexClassLoader dexLoader = new DexClassLoader(
                            apkFile.getAbsolutePath(),
                            optDir.getAbsolutePath(),
                            libDir.getAbsolutePath(),
                            cl
                    );

                    if (guestAct == null && vPkg != null && vPkg.activities != null) {
                        for (ComponentInfo info : vPkg.activities) {
                            if (info.name != null && (info.name.contains("Splash") || info.name.contains("Main") || info.name.contains("Game") || info.name.contains("Launch"))) {
                                guestAct = info.name;
                                break;
                            }
                        }
                    }

                    if (guestAct == null && vPkg != null && vPkg.activities != null && !vPkg.activities.isEmpty()) {
                        guestAct = vPkg.activities.get(0).name;
                    }

                    if (guestAct != null) {
                        try {
                            Class<?> actClass = dexLoader.loadClass(guestAct);
                            Activity realGuestActivity = (Activity) actClass.getDeclaredConstructor().newInstance();
                            Log.i(TAG, "Successfully instantiated real guest Activity via VInstrumentation: " + guestAct);
                            return realGuestActivity;
                        } catch (Throwable t) {
                            Log.e(TAG, "newActivity instantiation error for " + guestAct + ": " + t.getMessage(), t);
                        }
                    }
                }
            }
        }

        if (mBase != null) {
            return mBase.newActivity(cl, className, intent);
        }
        return super.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        if (activity != null) {
            ContextVirtualizer.virtualizeActivity(activity);
        }
        if (mBase != null) {
            mBase.callActivityOnCreate(activity, icicle);
        } else {
            super.callActivityOnCreate(activity, icicle);
        }
    }

    public Object execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity targetAct,
            Intent intent, int requestCode, Bundle options) {
        Intent targetIntent = rewriteIntentForVirtualization(who, intent);
        try {
            Method m = Instrumentation.class.getDeclaredMethod("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class);
            m.setAccessible(true);
            return m.invoke(mBase != null ? mBase : new Instrumentation(), who, contextThread, token, targetAct, targetIntent, requestCode, options);
        } catch (Throwable t) {
            return null;
        }
    }

    public Object execStartActivity(
            Context who, IBinder contextThread, IBinder token, String resultWho,
            Intent intent, int requestCode, Bundle options) {
        Intent targetIntent = rewriteIntentForVirtualization(who, intent);
        try {
            Method m = Instrumentation.class.getDeclaredMethod("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, String.class,
                    Intent.class, int.class, Bundle.class);
            m.setAccessible(true);
            return m.invoke(mBase != null ? mBase : new Instrumentation(), who, contextThread, token, resultWho, targetIntent, requestCode, options);
        } catch (Throwable t) {
            return null;
        }
    }

    private Intent rewriteIntentForVirtualization(Context context, Intent intent) {
        if (intent == null) return null;
        try {
            ComponentName cn = intent.getComponent();
            String targetPkg = intent.getPackage();
            if (targetPkg == null && cn != null) targetPkg = cn.getPackageName();

            if (targetPkg != null && PackageCache.contains(targetPkg)) {
                Intent stubIntent = new Intent();
                String hostPkg = context != null ? context.getPackageName() : "com.rukvirtual";
                stubIntent.setClassName(hostPkg, "com.universal.virtualcore.stub.StubActivity$P0");
                stubIntent.putExtra("_v_pkg_", targetPkg);
                stubIntent.putExtra("_v_user_", 0);
                if (cn != null) {
                    stubIntent.putExtra("_v_activity_", cn.getClassName());
                }
                stubIntent.putExtra("_v_intent_", intent);
                stubIntent.addFlags(intent.getFlags());
                return stubIntent;
            }
        } catch (Throwable ignored) {}
        return intent;
    }

    private void extractNativeLibraries(File apkFile, File libDir) {
        try (ZipFile zip = new ZipFile(apkFile)) {
            if (!libDir.exists()) libDir.mkdirs();
            libDir.setReadable(true, false);
            libDir.setExecutable(true, false);

            String selectedAbi = null;
            for (String abi : Build.SUPPORTED_ABIS) {
                String prefix = "lib/" + abi + "/";
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.getName().startsWith(prefix) && e.getName().endsWith(".so")) {
                        selectedAbi = abi;
                        break;
                    }
                }
                if (selectedAbi != null) break;
            }

            if (selectedAbi == null) {
                selectedAbi = Build.SUPPORTED_ABIS[0];
            }

            String abiPrefix = "lib/" + selectedAbi + "/";
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(abiPrefix) && name.endsWith(".so")) {
                    String soName = new File(name).getName();
                    File dst = new File(libDir, soName);
                    if (!dst.exists() || dst.length() != entry.getSize()) {
                        try (InputStream is = zip.getInputStream(entry); FileOutputStream fos = new FileOutputStream(dst)) {
                            byte[] buf = new byte[32768];
                            int len;
                            while ((len = is.read(buf)) > 0) {
                                fos.write(buf, 0, len);
                            }
                            fos.flush();
                        }
                    }
                    try {
                        dst.setReadable(true, false);
                        dst.setExecutable(true, false);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "extractNativeLibraries notice: " + t.getMessage());
        }
    }
}


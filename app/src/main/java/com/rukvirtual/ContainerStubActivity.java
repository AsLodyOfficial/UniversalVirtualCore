package com.rukvirtual;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.universal.virtualcore.VirtualCore;
import com.universal.virtualcore.client.VirtualContextWrapper;
import com.universal.virtualcore.env.VEnvironment;
import com.universal.virtualcore.native_core.NativeBridge;
import com.universal.virtualcore.package_mgr.model.ComponentInfo;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

public class ContainerStubActivity extends Activity {
    private static final String TAG = "ContainerStubActivity";

    private String packageName;
    private int userId = 0;
    private String activityClassName;
    private VirtualContextWrapper virtualContext;
    private Activity guestActivity;
    private Application guestApplication;
    private DexClassLoader dexLoader;
    private Resources guestResources;
    private AssetManager guestAssetManager;
    private FrameLayout rootContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // High-Performance 3D Graphics & Game Engine Window Setup
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFormat(PixelFormat.RGBA_8888);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        super.onCreate(savedInstanceState);

        applyImmersiveMode();

        rootContainer = new FrameLayout(this);
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootContainer);

        Intent intent = getIntent();
        if (intent != null) {
            packageName = intent.getStringExtra("_v_pkg_");
            userId = intent.getIntExtra("_v_user_", 0);
            activityClassName = intent.getStringExtra("_v_activity_");
        }

        if (packageName == null) {
            Toast.makeText(this, "No virtual package specified to run", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize VirtualCore inside this sandbox process
        VirtualCore.get().initialize(this);

        bootGuestInContainer(savedInstanceState);
    }

    private void applyImmersiveMode() {
        try {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable ignored) {}
    }

    private void bootGuestInContainer(Bundle savedInstanceState) {
        try {
            // 1. Resolve guest APK path
            VirtualPackage vPkg = VirtualCore.get().getPackageInfo(packageName);
            String apkPath = vPkg != null ? vPkg.sourcePath : null;
            String hostNativeLibDir = null;

            try {
                ApplicationInfo hostAi = getPackageManager().getApplicationInfo(packageName, 0);
                if (apkPath == null || !new File(apkPath).exists()) {
                    apkPath = hostAi.sourceDir;
                }
                hostNativeLibDir = hostAi.nativeLibraryDir;
            } catch (Throwable ignored) {}

            if (apkPath == null || !new File(apkPath).exists()) {
                Toast.makeText(this, "Could not locate APK for " + packageName, Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            File apkFile = new File(apkPath);

            // 2. Android 14+ ART Strict Security: make file strictly read-only
            try {
                apkFile.setReadable(true, false);
                apkFile.setWritable(false, false);
                apkFile.setReadOnly();
                if (apkFile.getParentFile() != null) {
                    apkFile.getParentFile().setReadable(true, false);
                    apkFile.getParentFile().setWritable(false, false);
                    apkFile.getParentFile().setReadOnly();
                }
            } catch (Throwable ignored) {}

            // 3. Prepare sandbox directories & native library extraction
            File libDir = VEnvironment.getPackageLibDirectory(userId, packageName);
            extractNativeLibraries(apkFile, libDir);

            File optDir = new File(getCacheDir(), "dexopt_" + packageName);
            if (!optDir.exists()) optDir.mkdirs();

            // 4. Build combined librarySearchPath
            StringBuilder libPathBuilder = new StringBuilder();
            libPathBuilder.append(libDir.getAbsolutePath());
            if (hostNativeLibDir != null && new File(hostNativeLibDir).exists()) {
                libPathBuilder.append(File.pathSeparator).append(hostNativeLibDir);
            }

            // 5. Create isolated DexClassLoader
            dexLoader = new DexClassLoader(
                    apkFile.getAbsolutePath(),
                    optDir.getAbsolutePath(),
                    libPathBuilder.toString(),
                    getClassLoader()
            );

            // Inject native lib directories into DexClassLoader PathList
            injectNativeLibraryDirs(dexLoader, libDir, hostNativeLibDir != null ? new File(hostNativeLibDir) : null);

            // 6. Create Guest AssetManager & Resources
            guestAssetManager = VirtualContextWrapper.createGuestAssetManager(apkFile.getAbsolutePath());
            guestResources = VirtualContextWrapper.createGuestResources(this, apkFile.getAbsolutePath());

            // 7. Create VirtualContextWrapper
            virtualContext = new VirtualContextWrapper(
                    getBaseContext(),
                    packageName,
                    userId,
                    dexLoader,
                    guestResources,
                    guestAssetManager,
                    apkFile.getAbsolutePath()
            );

            // 8. Initialize System Service Hooks & Native Bridge
            try {
                com.universal.virtualcore.hook.HookManager.installHooks();
                com.universal.virtualcore.hook.PackageManagerHook.hook(null);
                NativeBridge.init();
                NativeBridge.setupCloaking(packageName, getPackageName());
                NativeBridge.initUserStorage(userId, packageName, VEnvironment.getPackageDataDirectory(userId, packageName).getAbsolutePath());
            } catch (Throwable ignored) {}

            // 9. Initialize guest Application class if declared
            String appClassName = vPkg != null ? vPkg.applicationClassName : null;
            if (appClassName == null) {
                try {
                    PackageInfo pi = getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
                    if (pi != null && pi.applicationInfo != null) {
                        appClassName = pi.applicationInfo.className;
                    }
                } catch (Throwable ignored) {}
            }

            if (appClassName != null && !appClassName.equals("android.app.Application")) {
                try {
                    Class<?> appClass = dexLoader.loadClass(appClassName);
                    guestApplication = (Application) appClass.getDeclaredConstructor().newInstance();
                    Method attachMethod = Application.class.getDeclaredMethod("attach", Context.class);
                    attachMethod.setAccessible(true);
                    attachMethod.invoke(guestApplication, virtualContext);
                    guestApplication.onCreate();
                    Log.i(TAG, "Booted guest Application: " + appClassName);
                } catch (Throwable t) {
                    Log.w(TAG, "Guest Application initialization warning: " + t.getMessage());
                }
            }

            // 10. Resolve Launch Activity Class
            if (activityClassName == null) {
                // Priority resolution for games (Unreal Engine / Unity / Free Fire / PUBG)
                if (vPkg != null && vPkg.activities != null) {
                    for (ComponentInfo info : vPkg.activities) {
                        if (info.name != null && (info.name.contains("Splash") || info.name.contains("Main") || info.name.contains("Game") || info.name.contains("Launch"))) {
                            activityClassName = info.name;
                            break;
                        }
                    }
                }

                if (activityClassName == null) {
                    try {
                        Intent hostLaunch = getPackageManager().getLaunchIntentForPackage(packageName);
                        if (hostLaunch != null && hostLaunch.getComponent() != null) {
                            activityClassName = hostLaunch.getComponent().getClassName();
                        }
                    } catch (Throwable ignored) {}
                }

                if (activityClassName == null && vPkg != null && vPkg.activities != null && !vPkg.activities.isEmpty()) {
                    activityClassName = vPkg.activities.get(0).name;
                }
            }

            if (activityClassName == null) {
                activityClassName = "com.epicgames.ue4.SplashActivity"; // Default game fallback
            }

            // 11. Apply guest activity theme if available
            try {
                PackageInfo pi = getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
                if (pi != null && pi.activities != null) {
                    for (ActivityInfo ai : pi.activities) {
                        if (activityClassName.equals(ai.name) && ai.theme != 0) {
                            setTheme(ai.theme);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 12. Instantiate & attach Guest Activity inside Container
            launchGuestActivity(activityClassName, savedInstanceState);

            Log.i(TAG, "Successfully booted " + packageName + " (" + activityClassName + ") inside Virtual Container :p0");
            Toast.makeText(this, "Running " + packageName + " in Virtual Core", Toast.LENGTH_SHORT).show();

        } catch (Throwable t) {
            Log.e(TAG, "Fatal error booting guest in container: " + t.getMessage(), t);
            Toast.makeText(this, "Container Execution Notice: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void launchGuestActivity(String className, Bundle savedInstanceState) {
        try {
            Activity oldActivity = this.guestActivity;
            if (oldActivity != null) {
                try {
                    Method mPause = Activity.class.getDeclaredMethod("onPause");
                    mPause.setAccessible(true);
                    mPause.invoke(oldActivity);
                } catch (Throwable ignored) {}
                try {
                    Method mStop = Activity.class.getDeclaredMethod("onStop");
                    mStop.setAccessible(true);
                    mStop.invoke(oldActivity);
                } catch (Throwable ignored) {}
                try {
                    Method mDestroy = Activity.class.getDeclaredMethod("onDestroy");
                    mDestroy.setAccessible(true);
                    mDestroy.invoke(oldActivity);
                } catch (Throwable ignored) {}
            }

            Class<?> actClass = dexLoader.loadClass(className);
            Activity newGuestActivity = (Activity) actClass.getDeclaredConstructor().newInstance();

            this.guestActivity = newGuestActivity;
            this.activityClassName = className;

            attachGuestActivity(newGuestActivity, savedInstanceState);

            // Full Lifecycle Transition to start OpenGL/Vulkan rendering loop
            try {
                Method mStart = Activity.class.getDeclaredMethod("onStart");
                mStart.setAccessible(true);
                mStart.invoke(newGuestActivity);
            } catch (Throwable ignored) {}

            try {
                Method mResume = Activity.class.getDeclaredMethod("onResume");
                mResume.setAccessible(true);
                mResume.invoke(newGuestActivity);
            } catch (Throwable ignored) {}

            try {
                newGuestActivity.onWindowFocusChanged(true);
            } catch (Throwable ignored) {}

            applyImmersiveMode();

            Log.i(TAG, "Launched & resumed guest activity: " + className);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to launch guest activity " + className + ": " + t.getMessage(), t);
        }
    }

    private void attachGuestActivity(Activity target, Bundle savedInstanceState) {
        try {
            // Attach ContextThemeWrapper & BaseContext
            Method attachBaseContext = Context.class.getDeclaredMethod("attachBaseContext", Context.class);
            attachBaseContext.setAccessible(true);
            attachBaseContext.invoke(target, virtualContext);

            // Reflection to wire internal Activity fields
            setField(Activity.class, target, "mApplication", guestApplication != null ? guestApplication : getApplication());
            setField(Activity.class, target, "mComponent", new ComponentName(packageName, activityClassName != null ? activityClassName : target.getClass().getName()));
            setField(Activity.class, target, "mIntent", getIntent());
            setField(Activity.class, target, "mWindow", getWindow());
            setField(Activity.class, target, "mWindowManager", getWindowManager());
            setField(Activity.class, target, "mVisibleFromClient", true);
            setField(Activity.class, target, "mWindowAdded", true);

            // Wire Instrumentation proxy to intercept game sub-activity transitions (Splash -> GameActivity)
            Instrumentation containerInstrumentation = new Instrumentation() {
                public Object execStartActivity(
                        Context who, IBinder contextThread, IBinder token, Activity targetAct,
                        Intent intent, int requestCode, Bundle options) {
                    if (intent != null) {
                        ComponentName cn = intent.getComponent();
                        String targetPkg = intent.getPackage();
                        if (targetPkg == null && cn != null) targetPkg = cn.getPackageName();

                        if (targetPkg == null || targetPkg.equals(packageName)) {
                            final String nextActivity = cn != null ? cn.getClassName() : null;
                            if (nextActivity != null) {
                                runOnUiThread(() -> launchGuestActivity(nextActivity, null));
                                return null;
                            }
                        }
                    }
                    try {
                        Method m = Instrumentation.class.getDeclaredMethod("execStartActivity",
                                Context.class, IBinder.class, IBinder.class, Activity.class,
                                Intent.class, int.class, Bundle.class);
                        m.setAccessible(true);
                        return m.invoke(new Instrumentation(), who, contextThread, token, targetAct, intent, requestCode, options);
                    } catch (Throwable ignored) {
                        return null;
                    }
                }

                public Object execStartActivity(
                        Context who, IBinder contextThread, IBinder token, String resultWho,
                        Intent intent, int requestCode, Bundle options) {
                    if (intent != null) {
                        ComponentName cn = intent.getComponent();
                        String targetPkg = intent.getPackage();
                        if (targetPkg == null && cn != null) targetPkg = cn.getPackageName();

                        if (targetPkg == null || targetPkg.equals(packageName)) {
                            final String nextActivity = cn != null ? cn.getClassName() : null;
                            if (nextActivity != null) {
                                runOnUiThread(() -> launchGuestActivity(nextActivity, null));
                                return null;
                            }
                        }
                    }
                    try {
                        Method m = Instrumentation.class.getDeclaredMethod("execStartActivity",
                                Context.class, IBinder.class, IBinder.class, String.class,
                                Intent.class, int.class, Bundle.class);
                        m.setAccessible(true);
                        return m.invoke(new Instrumentation(), who, contextThread, token, resultWho, intent, requestCode, options);
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
            };
            setField(Activity.class, target, "mInstrumentation", containerInstrumentation);

            // Invoke guest Activity.onCreate()
            Method onCreateMethod = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            onCreateMethod.setAccessible(true);
            onCreateMethod.invoke(target, savedInstanceState);

        } catch (Throwable t) {
            Log.w(TAG, "attachGuestActivity error: " + t.getMessage(), t);
            try {
                Method onCreateMethod = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
                onCreateMethod.setAccessible(true);
                onCreateMethod.invoke(target, savedInstanceState);
            } catch (Throwable ignored) {}
        }
    }

    private void injectNativeLibraryDirs(ClassLoader classLoader, File... dirs) {
        try {
            Field pathListField = findField(classLoader.getClass(), "pathList");
            if (pathListField == null) return;
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(classLoader);
            if (pathList == null) return;

            Field nativeDirsField = findField(pathList.getClass(), "nativeLibraryDirectories");
            if (nativeDirsField != null) {
                nativeDirsField.setAccessible(true);
                List<File> nativeDirs = (List<File>) nativeDirsField.get(pathList);
                if (nativeDirs == null) {
                    nativeDirs = new ArrayList<>();
                }
                for (File dir : dirs) {
                    if (dir != null && dir.exists() && !nativeDirs.contains(dir)) {
                        nativeDirs.add(0, dir);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "injectNativeLibraryDirs: " + t.getMessage());
        }
    }

    private Field findField(Class<?> clazz, String name) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            try {
                return curr.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                curr = curr.getSuperclass();
            }
        }
        return null;
    }

    private void setField(Class<?> clazz, Object target, String fieldName, Object value) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Throwable ignored) {}
    }

    @Override
    public Resources getResources() {
        return guestResources != null ? guestResources : super.getResources();
    }

    @Override
    public AssetManager getAssets() {
        return guestAssetManager != null ? guestAssetManager : super.getAssets();
    }

    @Override
    public ClassLoader getClassLoader() {
        return dexLoader != null ? dexLoader : super.getClassLoader();
    }

    @Override
    public String getPackageName() {
        return packageName != null ? packageName : super.getPackageName();
    }

    // Lifecycle Forwarding to Guest Activity
    @Override
    protected void onStart() {
        super.onStart();
        applyImmersiveMode();
        if (guestActivity != null) {
            try {
                Method m = Activity.class.getDeclaredMethod("onStart");
                m.setAccessible(true);
                m.invoke(guestActivity);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
        if (guestActivity != null) {
            try {
                Method m = Activity.class.getDeclaredMethod("onResume");
                m.setAccessible(true);
                m.invoke(guestActivity);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onPause() {
        if (guestActivity != null) {
            try {
                Method m = Activity.class.getDeclaredMethod("onPause");
                m.setAccessible(true);
                m.invoke(guestActivity);
            } catch (Throwable ignored) {}
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (guestActivity != null) {
            try {
                Method m = Activity.class.getDeclaredMethod("onStop");
                m.setAccessible(true);
                m.invoke(guestActivity);
            } catch (Throwable ignored) {}
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (guestActivity != null) {
            try {
                Method m = Activity.class.getDeclaredMethod("onDestroy");
                m.setAccessible(true);
                m.invoke(guestActivity);
            } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
        if (guestActivity != null) {
            guestActivity.onWindowFocusChanged(hasFocus);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (guestActivity != null) {
            try {
                return guestActivity.dispatchTouchEvent(ev);
            } catch (Throwable ignored) {}
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (guestActivity != null) {
            try {
                return guestActivity.dispatchKeyEvent(event);
            } catch (Throwable ignored) {}
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (guestActivity != null) {
            try {
                guestActivity.onBackPressed();
                return;
            } catch (Throwable ignored) {}
        }
        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (guestActivity != null) {
            guestActivity.onConfigurationChanged(newConfig);
        }
    }

    private void extractNativeLibraries(File apkFile, File libDir) {
        try (ZipFile zip = new ZipFile(apkFile)) {
            // Find best matching ABI from supported ABIs
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

            if (!libDir.exists()) libDir.mkdirs();
            libDir.setReadable(true, false);
            libDir.setExecutable(true, false);

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
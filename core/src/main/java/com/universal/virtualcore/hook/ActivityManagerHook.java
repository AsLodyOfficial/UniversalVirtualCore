package com.universal.virtualcore.hook;

import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.universal.virtualcore.package_mgr.PackageCache;

import org.alok.engine.Alok;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class ActivityManagerHook implements InvocationHandler {
    private static final String TAG = "ActivityManagerHook";
    private final Object mBase;

    public ActivityManagerHook(Object base) {
        this.mBase = base;
    }

    public static void hook(Map<String, IBinder> cache) {
        try {
            Object singleton;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Class<?> amClass = Class.forName("android.app.ActivityManager");
                Field singletonField = amClass.getDeclaredField("IActivityManagerSingleton");
                singletonField.setAccessible(true);
                singleton = singletonField.get(null);
            } else {
                Class<?> amNativeClass = Class.forName("android.app.ActivityManagerNative");
                Field gDefaultField = amNativeClass.getDeclaredField("gDefault");
                gDefaultField.setAccessible(true);
                singleton = gDefaultField.get(null);
            }
            if (singleton == null) return;

            Class<?> singletonClass = Class.forName("android.util.Singleton");
            Field mInstanceField = singletonClass.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);
            Object mInstance = mInstanceField.get(singleton);
            if (mInstance == null) {
                Method getMethod = singletonClass.getDeclaredMethod("get");
                getMethod.setAccessible(true);
                mInstance = getMethod.invoke(singleton);
            }

            if (mInstance != null && !Proxy.isProxyClass(mInstance.getClass())) {
                Class<?> iAmInterface = Class.forName("android.app.IActivityManager");
                Object proxy = Proxy.newProxyInstance(
                        mInstance.getClass().getClassLoader(),
                        new Class<?>[]{iAmInterface},
                        new ActivityManagerHook(mInstance)
                );
                mInstanceField.set(singleton, proxy);
                Log.i(TAG, "ActivityManager IActivityManagerSingleton hook registered.");
            }
        } catch (Throwable t) {
            Log.d(TAG, "ActivityManagerHook notice: " + t.getMessage());
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            if (args != null) {
                String hostPkg = Alok.getHostPkg();
                if (hostPkg != null && !hostPkg.isEmpty()) {
                    String name = method.getName();
                    if ("getIntentSender".equals(name) || "getIntentSenderWithFeature".equals(name)
                            || "getHistoricalProcessExitReasons".equals(name)
                            || "checkPermission".equals(name)) {
                        for (int i = 0; i < args.length; i++) {
                            if (args[i] instanceof String && PackageCache.contains((String) args[i])) {
                                args[i] = hostPkg;
                            }
                        }
                    }
                }
            }
            return method.invoke(mBase, args);
        } catch (Throwable t) {
            if (t instanceof java.lang.reflect.InvocationTargetException) {
                throw ((java.lang.reflect.InvocationTargetException) t).getTargetException();
            }
            throw t;
        }
    }
}

package com.universal.virtualcore.hook;

import android.app.NotificationManager;
import android.os.IBinder;
import android.util.Log;

import com.universal.virtualcore.package_mgr.PackageCache;

import org.alok.engine.Alok;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class NotificationManagerHook implements InvocationHandler {
    private static final String TAG = "NotificationManagerHook";
    private final Object mBase;

    public NotificationManagerHook(Object base) {
        this.mBase = base;
    }

    public static void hook(Map<String, IBinder> cache) {
        try {
            Method getServiceMethod = NotificationManager.class.getDeclaredMethod("getService");
            getServiceMethod.setAccessible(true);
            Object sService = getServiceMethod.invoke(null);
            if (sService != null && !Proxy.isProxyClass(sService.getClass())) {
                Class<?> iNmInterface = Class.forName("android.app.INotificationManager");
                Object proxy = Proxy.newProxyInstance(
                        sService.getClass().getClassLoader(),
                        new Class<?>[]{iNmInterface},
                        new NotificationManagerHook(sService)
                );
                Field sServiceField = NotificationManager.class.getDeclaredField("sService");
                sServiceField.setAccessible(true);
                sServiceField.set(null, proxy);
                Log.i(TAG, "INotificationManager hook registered.");
            }
        } catch (Throwable t) {
            Log.d(TAG, "NotificationManagerHook notice: " + t.getMessage());
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            if (args != null) {
                String hostPkg = Alok.getHostPkg();
                if (hostPkg != null && !hostPkg.isEmpty()) {
                    for (int i = 0; i < args.length; i++) {
                        if (args[i] instanceof String && PackageCache.contains((String) args[i])) {
                            args[i] = hostPkg;
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

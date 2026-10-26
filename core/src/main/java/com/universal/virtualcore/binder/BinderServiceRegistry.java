package com.universal.virtualcore.binder;

import android.os.IBinder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BinderServiceRegistry {
    private static final Map<String, IBinder> sServices = new ConcurrentHashMap<>();

    public static void register(String name, IBinder binder) {
        sServices.put(name, binder);
    }

    public static IBinder getService(String name) {
        return sServices.get(name);
    }

    public static boolean hasService(String name) {
        return sServices.containsKey(name);
    }
}

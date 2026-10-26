package com.universal.virtualcore.hook;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.universal.virtualcore.core.GmsCore;
import com.universal.virtualcore.package_mgr.PackageCache;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;

import org.alok.engine.Alok;
import org.alok.engine.app.BActivityThread;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class PackageManagerHook implements InvocationHandler {
    private static final String TAG = "PackageManagerHook";
    private final Object mBase;

    public PackageManagerHook(Object base) {
        this.mBase = base;
    }

    public static void hook(Map<String, IBinder> cache) {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object currentActivityThread = currentActivityThreadMethod.invoke(null);

            Method getPackageManagerMethod = activityThreadClass.getDeclaredMethod("getPackageManager");
            getPackageManagerMethod.setAccessible(true);
            Object sPackageManager = getPackageManagerMethod.invoke(null);

            if (sPackageManager != null && !Proxy.isProxyClass(sPackageManager.getClass())) {
                Class<?> iPackageManagerInterface = Class.forName("android.content.pm.IPackageManager");
                Object proxy = Proxy.newProxyInstance(
                        sPackageManager.getClass().getClassLoader(),
                        new Class<?>[]{iPackageManagerInterface},
                        new PackageManagerHook(sPackageManager)
                );

                // 1. Replace ActivityThread.sPackageManager
                Field sPackageManagerField = activityThreadClass.getDeclaredField("sPackageManager");
                sPackageManagerField.setAccessible(true);
                sPackageManagerField.set(currentActivityThread, proxy);
                sPackageManagerField.set(null, proxy);

                // 2. Also replace ApplicationPackageManager.mPM on ActivityThread's systemContext
                try {
                    Method getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext");
                    getSystemContextMethod.setAccessible(true);
                    Object systemContext = getSystemContextMethod.invoke(currentActivityThread);
                    if (systemContext != null) {
                        Method getPmMethod = systemContext.getClass().getMethod("getPackageManager");
                        Object appPm = getPmMethod.invoke(systemContext);
                        if (appPm != null) {
                            Field mPmField = Class.forName("android.app.ApplicationPackageManager").getDeclaredField("mPM");
                            mPmField.setAccessible(true);
                            mPmField.set(appPm, proxy);
                        }
                    }
                } catch (Throwable ignored) {}

                Log.i(TAG, "Successfully installed IPackageManager hook in ActivityThread & ApplicationPackageManager");
            }
        } catch (Throwable t) {
            Log.w(TAG, "hook IPackageManager notice: " + t.getMessage());
        }
    }

    private static int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int getCurrentVirtualUserId() {
        try {
            return BActivityThread.getUserId();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        try {
            if ("getInstallerPackageName".equals(methodName)) {
                // Spoof Google Play Store installer for guest integrity checks
                return GmsCore.VENDING_PKG;
            } else if ("setComponentEnabledSetting".equals(methodName)
                    || "addOnPermissionsChangeListener".equals(methodName)
                    || "removeOnPermissionsChangeListener".equals(methodName)) {
                return 0;
            } else if ("getComponentEnabledSetting".equals(methodName) && args != null && args.length > 0 && args[0] instanceof ComponentName) {
                ComponentName cn = (ComponentName) args[0];
                String pkg = cn.getPackageName();
                if (PackageCache.contains(pkg)) {
                    return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                }
                try {
                    ApplicationInfo ai = Alok.getBPackageManager().getApplicationInfo(pkg, 0, getCurrentVirtualUserId());
                    if (ai != null) {
                        return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                    }
                } catch (Throwable ignored) {}
            } else if ("canRequestPackageInstalls".equals(methodName) || "getPackageUid".equals(methodName)) {
                if (args != null && args.length > 0 && args[0] instanceof String) {
                    String hostPkg = Alok.getHostPkg();
                    if (hostPkg != null && !hostPkg.isEmpty()) {
                        args[0] = hostPkg;
                    }
                }
            } else if ("getPackagesForUid".equals(methodName) && args != null && args.length > 0 && args[0] instanceof Integer) {
                int uid = (Integer) args[0];
                try {
                    if (uid == Alok.getHostUid()) {
                        uid = BActivityThread.getBUid();
                    }
                    String[] pkgs = Alok.getBPackageManager().getPackagesForUid(uid);
                    if (pkgs != null && pkgs.length > 0) {
                        return pkgs;
                    }
                } catch (Throwable ignored) {}
            } else if ("resolveIntent".equals(methodName) && args != null && args.length >= 3 && args[0] instanceof Intent) {
                try {
                    Intent intent = (Intent) args[0];
                    String resolvedType = (String) args[1];
                    int flags = toInt(args[2]);
                    ResolveInfo ri = Alok.getBPackageManager().resolveIntent(intent, resolvedType, flags, getCurrentVirtualUserId());
                    if (ri != null) return ri;
                } catch (Throwable ignored) {}
            } else if ("resolveService".equals(methodName) && args != null && args.length >= 3 && args[0] instanceof Intent) {
                try {
                    Intent intent = (Intent) args[0];
                    String resolvedType = (String) args[1];
                    int flags = toInt(args[2]);
                    ResolveInfo ri = Alok.getBPackageManager().resolveService(intent, flags, resolvedType, getCurrentVirtualUserId());
                    if (ri != null) return ri;
                } catch (Throwable ignored) {}
            } else if ("resolveContentProvider".equals(methodName) && args != null && args.length >= 2 && args[0] instanceof String) {
                try {
                    String authority = (String) args[0];
                    int flags = toInt(args[1]);
                    ProviderInfo pi = Alok.getBPackageManager().resolveContentProvider(authority, flags, getCurrentVirtualUserId());
                    if (pi != null) return pi;
                } catch (Throwable ignored) {}
            } else if ("getProviderInfo".equals(methodName) && args != null && args.length >= 2 && args[0] instanceof ComponentName) {
                try {
                    ComponentName cn = (ComponentName) args[0];
                    int flags = toInt(args[1]);
                    ProviderInfo pi = Alok.getBPackageManager().getProviderInfo(cn, flags, getCurrentVirtualUserId());
                    if (pi != null) return pi;
                } catch (Throwable ignored) {}
            } else if ("getReceiverInfo".equals(methodName) && args != null && args.length >= 2 && args[0] instanceof ComponentName) {
                try {
                    ComponentName cn = (ComponentName) args[0];
                    int flags = toInt(args[1]);
                    ActivityInfo ai = Alok.getBPackageManager().getReceiverInfo(cn, flags, getCurrentVirtualUserId());
                    if (ai != null) return ai;
                } catch (Throwable ignored) {}
            } else if ("getServiceInfo".equals(methodName) && args != null && args.length >= 2 && args[0] instanceof ComponentName) {
                try {
                    ComponentName cn = (ComponentName) args[0];
                    int flags = toInt(args[1]);
                    ServiceInfo si = Alok.getBPackageManager().getServiceInfo(cn, flags, getCurrentVirtualUserId());
                    if (si != null) return si;
                } catch (Throwable ignored) {}
            } else if ("getActivityInfo".equals(methodName) && args != null && args.length > 0) {
                ComponentName component = null;
                int flags = args.length >= 2 ? toInt(args[1]) : 0;
                for (Object arg : args) {
                    if (arg instanceof ComponentName) {
                        component = (ComponentName) arg;
                        break;
                    }
                }
                if (component != null) {
                    try {
                        ActivityInfo alokAi = Alok.getBPackageManager().getActivityInfo(component, flags, getCurrentVirtualUserId());
                        if (alokAi != null) return alokAi;
                    } catch (Throwable ignored) {}

                    String pkg = component.getPackageName();
                    VirtualPackage vPkg = PackageCache.get(pkg);
                    if (vPkg != null && vPkg.sourcePath != null) {
                        try {
                            PackageInfo pi = PackageCache.getHostContext() != null ?
                                    PackageCache.getHostContext().getPackageManager().getPackageArchiveInfo(
                                            vPkg.sourcePath,
                                            PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA
                                    ) : null;
                            if (pi != null && pi.activities != null) {
                                for (ActivityInfo ai : pi.activities) {
                                    if (component.getClassName().equals(ai.name)) {
                                        if (ai.applicationInfo == null) {
                                            ai.applicationInfo = pi.applicationInfo;
                                        }
                                        return ai;
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}

                        ActivityInfo fallback = new ActivityInfo();
                        fallback.name = component.getClassName();
                        fallback.packageName = pkg;
                        fallback.metaData = new Bundle();
                        fallback.metaData.putString("android.app.lib_name", "UE4");
                        return fallback;
                    }
                }
            } else if ("getApplicationInfo".equals(methodName) && args != null && args.length > 0 && args[0] instanceof String) {
                String pkg = (String) args[0];
                int flags = args.length >= 2 ? toInt(args[1]) : 0;
                if (GmsCore.isGoogleAppOrService(pkg)) {
                    return method.invoke(mBase, args);
                }
                try {
                    ApplicationInfo alokAi = Alok.getBPackageManager().getApplicationInfo(pkg, flags, getCurrentVirtualUserId());
                    if (alokAi != null) {
                        alokAi.flags |= ApplicationInfo.FLAG_EXTERNAL_STORAGE;
                        return alokAi;
                    }
                } catch (Throwable ignored) {}

                VirtualPackage vPkg = PackageCache.get(pkg);
                if (vPkg != null && vPkg.sourcePath != null) {
                    try {
                        PackageInfo pi = PackageCache.getHostContext() != null ?
                                PackageCache.getHostContext().getPackageManager().getPackageArchiveInfo(
                                        vPkg.sourcePath,
                                        PackageManager.GET_META_DATA
                                ) : null;
                        if (pi != null && pi.applicationInfo != null) {
                            pi.applicationInfo.flags |= ApplicationInfo.FLAG_EXTERNAL_STORAGE;
                            return pi.applicationInfo;
                        }
                    } catch (Throwable ignored) {}
                }
            } else if ("getPackageInfo".equals(methodName) && args != null && args.length > 0 && args[0] instanceof String) {
                String pkg = (String) args[0];
                int flags = args.length >= 2 ? toInt(args[1]) : 0;
                if (GmsCore.isGoogleAppOrService(pkg)) {
                    return method.invoke(mBase, args);
                }
                try {
                    PackageInfo alokPi = Alok.getBPackageManager().getPackageInfo(pkg, flags, getCurrentVirtualUserId());
                    if (alokPi != null) {
                        return alokPi;
                    }
                } catch (Throwable ignored) {}

                VirtualPackage vPkg = PackageCache.get(pkg);
                if (vPkg != null && vPkg.sourcePath != null) {
                    try {
                        PackageInfo pi = PackageCache.getHostContext() != null ?
                                PackageCache.getHostContext().getPackageManager().getPackageArchiveInfo(
                                        vPkg.sourcePath,
                                        PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_META_DATA
                                ) : null;
                        if (pi != null) return pi;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "PackageManagerHook interception warning for " + methodName + ": " + t.getMessage());
        }

        try {
            return method.invoke(mBase, args);
        } catch (Throwable t) {
            if (t instanceof java.lang.reflect.InvocationTargetException) {
                throw ((java.lang.reflect.InvocationTargetException) t).getTargetException();
            }
            throw t;
        }
    }
}

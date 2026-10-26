package org.alok.engine.fake.frameworks;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.util.Log;

import com.universal.virtualcore.core.AlokDexInjector;

import org.alok.engine.Alok;
import org.alok.engine.utils.Reflector;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed BlackManager base class for all virtual framework managers (BPackageManager, BActivityManager, etc.).
 * Fixes:
 * 1) isServiceHealthy() returning false before lazy mService initialization.
 * 2) Android 12-16 Cached App Freezer freezing :server ("Transaction failed because process frozen"),
 *    which previously caused DeadObjectException in BPackageManager.getApplicationInfo() and returned
 *    a broken fallback ApplicationInfo with sourceDir = null, crashing the guest game after ~60-90s.
 * 3) Transparently wakes :server, reconnects the Binder stub, retries the RPC, and caches
 *    ApplicationInfo / PackageInfo in memory so getApplicationInfo() never fails.
 */
public abstract class BlackManager<Service extends IInterface> {
    public static final String TAG = "BlackManager";

    private Service mService;
    private Service mRawService;
    private final AtomicBoolean mServiceCreationFailed = new AtomicBoolean(false);
    private long mLastRetryTime = 0L;
    private long mLastServiceCreationTime = 0L;

    private static final long RETRY_TIMEOUT_MS = 50L;
    private static final long MIN_SERVICE_CREATION_INTERVAL_MS = 0L;
    private static final AtomicInteger globalServiceFailureCount = new AtomicInteger(0);
    private static final long GLOBAL_FAILURE_RESET_INTERVAL_MS = 5000L;
    private static long lastGlobalFailureReset = 0L;

    // In-process cache of valid ApplicationInfo and PackageInfo objects so even if :server is
    // transiently frozen or restarting, the game never gets a null/broken ApplicationInfo.
    private static final Map<String, ApplicationInfo> sAppInfoCache = new ConcurrentHashMap<>();
    private static final Map<String, PackageInfo> sPkgInfoCache = new ConcurrentHashMap<>();

    public BlackManager() {
    }

    protected abstract String getServiceName();

    public Service getService() {
        if (this.mService != null && this.mRawService != null) {
            try {
                IBinder binder = this.mRawService.asBinder();
                if (binder != null && binder.isBinderAlive() && binder.pingBinder()) {
                    return this.mService;
                }
            } catch (Throwable ignored) {
            }
            this.mService = null;
            this.mRawService = null;
        }

        if (this.mServiceCreationFailed.get()) {
            long now = System.currentTimeMillis();
            if (now - this.mLastRetryTime < RETRY_TIMEOUT_MS) {
                return null;
            }
            this.mServiceCreationFailed.set(false);
        }

        return createOrReconnectService();
    }

    private synchronized Service createOrReconnectService() {
        try {
            long now = System.currentTimeMillis();
            IBinder binder = Alok.get().getService(getServiceName());
            if (binder == null || !binder.isBinderAlive()) {
                // Wake :server process immediately and retry fetching binder
                AlokDexInjector.wakeServerNow();
                binder = Alok.get().getService(getServiceName());
            }
            if (binder == null) {
                Log.w(TAG, "Failed to get binder for service: " + getServiceName());
                markServiceCreationFailed();
                return null;
            }
            if (!binder.isBinderAlive()) {
                Log.w(TAG, "Binder is not alive for service: " + getServiceName());
                markServiceCreationFailed();
                return null;
            }

            Class<Service> ifaceClass = getTClass();
            String stubClassName = ifaceClass.getName() + "$Stub";
            @SuppressWarnings("unchecked")
            final Service rawInstance = (Service) Reflector.on(stubClassName)
                    .method("asInterface", IBinder.class)
                    .call(binder);

            if (rawInstance == null) {
                Log.w(TAG, "Failed to create service instance for: " + getServiceName());
                markServiceCreationFailed();
                return null;
            }

            try {
                if (!rawInstance.asBinder().isBinderAlive()) {
                    Log.w(TAG, "Service binder is not alive after creation: " + getServiceName());
                    markServiceCreationFailed();
                    return null;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error checking service binder health: " + getServiceName(), e);
                markServiceCreationFailed();
                return null;
            }

            try {
                rawInstance.asBinder().linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        try {
                            rawInstance.asBinder().unlinkToDeath(this, 0);
                        } catch (Throwable ignored) {
                        }
                        if (BlackManager.this.mRawService == rawInstance) {
                            BlackManager.this.mRawService = null;
                            BlackManager.this.mService = null;
                        }
                    }
                }, 0);
            } catch (Exception e) {
                Log.w(TAG, "Error linking death recipient for " + getServiceName(), e);
            }

            this.mRawService = rawInstance;
            this.mService = wrapWithResilientProxy(ifaceClass, rawInstance);
            this.mServiceCreationFailed.set(false);
            this.mLastServiceCreationTime = now;
            return this.mService;
        } catch (Throwable t) {
            Log.e(TAG, "Error creating service for " + getServiceName(), t);
            markServiceCreationFailed();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Service wrapWithResilientProxy(final Class<Service> ifaceClass, final Service initialDelegate) {
        return (Service) Proxy.newProxyInstance(
                ifaceClass.getClassLoader(),
                new Class<?>[]{ifaceClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String methodName = method.getName();
                        Service current = BlackManager.this.mRawService != null
                                ? BlackManager.this.mRawService
                                : initialDelegate;

                        try {
                            Object result = method.invoke(current, args);
                            cacheResultIfApplicable(methodName, args, result);
                            if (result == null) {
                                Object fallback = getFallbackResultIfApplicable(methodName, args);
                                if (fallback != null) {
                                    return fallback;
                                }
                            }
                            return result;
                        } catch (InvocationTargetException ite) {
                            Throwable cause = ite.getTargetException();
                            if (cause instanceof DeadObjectException || cause instanceof RemoteException) {
                                Log.w(TAG, "Binder transaction failed in " + getServiceName() + "." + methodName
                                        + " (" + cause.getMessage() + ") - waking :server and retrying...");
                                // Wake :server from Android 12-16 Cached App Freezer and reconnect
                                AlokDexInjector.wakeServerNow();
                                BlackManager.this.mRawService = null;
                                BlackManager.this.mService = null;
                                Service reconnected = createOrReconnectService();
                                Service retryTarget = BlackManager.this.mRawService != null
                                        ? BlackManager.this.mRawService
                                        : reconnected;
                                if (retryTarget != null) {
                                    try {
                                        Object retryResult = method.invoke(retryTarget, args);
                                        cacheResultIfApplicable(methodName, args, retryResult);
                                        if (retryResult != null) {
                                            return retryResult;
                                        }
                                    } catch (InvocationTargetException retryIte) {
                                        cause = retryIte.getTargetException();
                                    }
                                }
                                Object fallback = getFallbackResultIfApplicable(methodName, args);
                                if (fallback != null) {
                                    Log.i(TAG, "Recovered " + methodName + " from local cache/fallback after RemoteException");
                                    return fallback;
                                }
                            }
                            throw cause != null ? cause : ite;
                        }
                    }
                });
    }

    private static void cacheResultIfApplicable(String methodName, Object[] args, Object result) {
        try {
            if ("getApplicationInfo".equals(methodName) && result instanceof ApplicationInfo && args != null && args.length >= 1 && args[0] instanceof String) {
                ApplicationInfo ai = (ApplicationInfo) result;
                if (ai.sourceDir != null && !ai.sourceDir.isEmpty()) {
                    sAppInfoCache.put((String) args[0], new ApplicationInfo(ai));
                }
            } else if ("getPackageInfo".equals(methodName) && result instanceof PackageInfo && args != null && args.length >= 1 && args[0] instanceof String) {
                PackageInfo pi = (PackageInfo) result;
                if (pi.applicationInfo != null && pi.applicationInfo.sourceDir != null) {
                    sPkgInfoCache.put((String) args[0], pi);
                    sAppInfoCache.put((String) args[0], new ApplicationInfo(pi.applicationInfo));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object getFallbackResultIfApplicable(String methodName, Object[] args) {
        try {
            if (args == null || args.length < 1 || !(args[0] instanceof String)) {
                return null;
            }
            String pkg = (String) args[0];
            if ("getApplicationInfo".equals(methodName)) {
                ApplicationInfo cached = sAppInfoCache.get(pkg);
                if (cached != null && cached.sourceDir != null) {
                    return new ApplicationInfo(cached);
                }
                int flags = (args.length >= 2 && args[1] instanceof Integer) ? (Integer) args[1] : 0;
                ApplicationInfo hostAi = buildCompleteApplicationInfoFromHost(pkg, flags);
                if (hostAi != null) {
                    sAppInfoCache.put(pkg, new ApplicationInfo(hostAi));
                    return hostAi;
                }
            } else if ("getPackageInfo".equals(methodName)) {
                PackageInfo cached = sPkgInfoCache.get(pkg);
                if (cached != null) {
                    return cached;
                }
                int flags = (args.length >= 2 && args[1] instanceof Integer) ? (Integer) args[1] : 0;
                if (Alok.getPackageManager() != null) {
                    PackageInfo hostPi = Alok.getPackageManager().getPackageInfo(pkg, flags);
                    if (hostPi != null) {
                        if (hostPi.applicationInfo != null) {
                            populateVirtualPaths(pkg, hostPi.applicationInfo);
                        }
                        sPkgInfoCache.put(pkg, hostPi);
                        return hostPi;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ApplicationInfo buildCompleteApplicationInfoFromHost(String pkg, int flags) {
        try {
            if (Alok.getPackageManager() != null) {
                ApplicationInfo ai = Alok.getPackageManager().getApplicationInfo(pkg, flags);
                if (ai != null) {
                    populateVirtualPaths(pkg, ai);
                    return ai;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void populateVirtualPaths(String pkg, ApplicationInfo ai) {
        try {
            Context ctx = Alok.getContext();
            if (ctx == null) return;
            File dataParent = ctx.getFilesDir().getParentFile();
            File vAppDir = new File(dataParent, "alok/data/app/" + pkg);
            File vBaseApk = new File(vAppDir, "base.apk");
            if (vBaseApk.exists() && vBaseApk.length() > 0) {
                ai.sourceDir = vBaseApk.getAbsolutePath();
                ai.publicSourceDir = vBaseApk.getAbsolutePath();
            }
            File vLibDir = new File(vAppDir, "lib");
            if (vLibDir.exists()) {
                ai.nativeLibraryDir = vLibDir.getAbsolutePath();
            }
            File vDataDir = new File(dataParent, "alok/data/user/0/" + pkg);
            if (vDataDir.exists()) {
                ai.dataDir = vDataDir.getAbsolutePath();
                ai.deviceProtectedDataDir = vDataDir.getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
    }

    private void markServiceCreationFailed() {
        this.mServiceCreationFailed.set(true);
        this.mLastRetryTime = System.currentTimeMillis();
    }

    public void clearServiceCache() {
        this.mService = null;
        this.mRawService = null;
        this.mServiceCreationFailed.set(false);
        Log.d(TAG, "Cleared service cache for " + getServiceName());
    }

    public boolean isServiceHealthy() {
        try {
            Service service = this.mService;
            if (service == null) {
                service = getService();
            }
            if (service == null) {
                return false;
            }
            Service raw = this.mRawService != null ? this.mRawService : service;
            IBinder binder = raw.asBinder();
            if (binder != null && binder.isBinderAlive() && binder.pingBinder()) {
                return true;
            }
            // Attempt immediate wake & reconnect before reporting unhealthy
            AlokDexInjector.wakeServerNow();
            this.mService = null;
            this.mRawService = null;
            service = getService();
            return service != null;
        } catch (Exception e) {
            Log.w(TAG, "Service health check failed for " + getServiceName(), e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Class<Service> getTClass() {
        Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType) {
            Type[] params = ((ParameterizedType) superclass).getActualTypeArguments();
            if (params != null && params.length > 0 && params[0] instanceof Class) {
                return (Class<Service>) params[0];
            }
        }
        throw new IllegalStateException("Cannot resolve generic service type for " + getClass().getName());
    }
}


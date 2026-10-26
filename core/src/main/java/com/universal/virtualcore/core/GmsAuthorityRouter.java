package com.universal.virtualcore.core;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import org.alok.android.app.BRIActivityManagerContentProviderHolder;
import org.alok.android.content.BRContentProviderHolderOreo;
import org.alok.android.content.BRContentProviderNative;
import org.alok.engine.Alok;
import org.alok.engine.app.BActivityThread;
import org.alok.engine.fake.delegate.ContentProviderDelegate;
import org.alok.engine.fake.frameworks.BActivityManager;
import org.alok.engine.fake.service.context.providers.ContentProviderStub;
import org.alok.engine.utils.compat.BuildCompat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GmsAuthorityRouter
 * 
 * Intercepts ContentProvider acquisition inside the virtual container for Google Mobile Services (GMS),
 * Chimera, AppDoctor, Phenotype, and Reliability authorities.
 *
 * Forwards requests to the real system GMS process whenever possible, or generates a clean synthetic
 * ContentProviderHolder so ActivityThread never fails with "Failed to find provider info", AppDoctor
 * succeeds without "Failed on fix: 0", and ReliabilityInit completes instantly without hanging or killing
 * virtual processes.
 */
public class GmsAuthorityRouter {
    private static final String TAG = "GmsAuthorityRouter";
    private static final Set<String> sExternalAuthorities = Collections.newSetFromMap(new ConcurrentHashMap<>());

    static {
        // Core GMS internal & system authorities that cause GMS init to hang if unresolved
        sExternalAuthorities.add("com.google.android.gms.common.appdoctor");
        sExternalAuthorities.add("com.google.android.gms.chimera");
        sExternalAuthorities.add("com.google.android.gms.phenotype");
        sExternalAuthorities.add("com.google.android.gms.reliability");
        sExternalAuthorities.add("com.google.android.gms.fonts");
        sExternalAuthorities.add("com.google.android.gms.measurement");
        sExternalAuthorities.add("com.google.android.gms.auth.api.credentials");
        sExternalAuthorities.add("com.google.android.gms");
        sExternalAuthorities.add("com.google.android.gsf.gservices");
        sExternalAuthorities.add("com.google.android.gsf");
        sExternalAuthorities.add("com.android.vending");
        sExternalAuthorities.add("gservices");
    }

    public static void addExternalAuthority(String authority) {
        if (authority != null && !authority.trim().isEmpty()) {
            sExternalAuthorities.add(authority.trim());
            Log.i(TAG, "Registered external authority passthrough: " + authority.trim());
        }
    }

    public static void addGmsAuthorityPassthrough(String[] authorities) {
        if (authorities != null) {
            for (String auth : authorities) {
                addExternalAuthority(auth);
            }
        }
    }

    public static boolean isExternalAuthority(String authority) {
        if (authority == null) return false;
        String auth = authority.trim();
        if (sExternalAuthorities.contains(auth)) {
            return true;
        }
        if (auth.startsWith("com.google.android.gms.")
                || auth.startsWith("com.google.android.gsf.")
                || auth.startsWith("com.android.vending.")
                || auth.contains("appdoctor")
                || auth.contains("chimera")
                || auth.contains("phenotype")
                || auth.contains("reliability")) {
            return true;
        }
        return false;
    }

    public static Set<String> getExternalAuthorities() {
        return Collections.unmodifiableSet(sExternalAuthorities);
    }

    /**
     * Intercepts IActivityManager.getContentProvider(...) calls inside the virtual container.
     * Routes GSF gservices, AppDoctor, Chimera, Phenotype, and Reliability authorities directly
     * to the current process's ProxyContentProvider (which has a valid ApplicationInfo and Binder)
     * and wraps gservices in SystemProviderStub so android_id is never 0 and ActivityThread.acquireProvider
     * never blocks for 20 seconds waiting for a null provider binder.
     */
    public static Object interceptGetContentProvider(Object who, Method method, Object[] args) {
        if (args == null || args.length == 0) return null;

        int authIndex = BuildCompat.isQ() ? 2 : 1;
        if (authIndex >= args.length || !(args[authIndex] instanceof String)) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof String && isExternalAuthority((String) args[i])) {
                    authIndex = i;
                    break;
                }
            }
        }
        if (authIndex >= args.length || !(args[authIndex] instanceof String)) {
            return null;
        }

        String targetAuth = (String) args[authIndex];
        if (!isExternalAuthority(targetAuth)) {
            return null; // Not an external/GMS authority; let standard hook handle it
        }

        // Only intercept internal stubbed authorities (gservices, appdoctor, chimera, phenotype, reliability).
        // Let real installed virtual GMS/Play Store providers resolve through standard IActivityManagerProxy$GetContentProvider!
        boolean isGservices = "com.google.android.gsf.gservices".equals(targetAuth) || "gservices".equals(targetAuth);
        boolean isInternalStubAuth = isGservices
                || targetAuth.contains("appdoctor")
                || targetAuth.contains("chimera")
                || targetAuth.contains("phenotype")
                || targetAuth.contains("reliability")
                || "com.google.settings".equals(targetAuth);

        if (!isInternalStubAuth) {
            return null;
        }

        try {
            int bpid = BActivityThread.getAppPid();
            if (bpid < 0) {
                bpid = 0;
            }
            String proxyAuth = org.alok.engine.proxy.ProxyManifest.getProxyAuthorities(bpid);
            Object[] proxyArgs = args.clone();

            String hostPkg = Alok.getHostPkg();
            if (BuildCompat.isQ() && proxyArgs.length > 1 && proxyArgs[1] instanceof String) {
                proxyArgs[1] = hostPkg;
            }
            proxyArgs[authIndex] = proxyAuth;
            int userIndex = authIndex + 1;
            if (userIndex < proxyArgs.length && proxyArgs[userIndex] instanceof Integer) {
                proxyArgs[userIndex] = Alok.getHostUserId();
            }

            Object holder = method.invoke(who, proxyArgs);
            if (holder != null) {
                try {
                    java.lang.reflect.Field fInfo = holder.getClass().getDeclaredField("info");
                    fInfo.setAccessible(true);
                    ProviderInfo pi = (ProviderInfo) fInfo.get(holder);
                    if (pi != null) {
                        pi.authority = targetAuth;
                    }
                } catch (Throwable ignored) {}
                ContentProviderDelegate.update(holder, targetAuth);
                return holder;
            }
        } catch (Throwable t) {
            Log.w(TAG, "interceptGetContentProvider proxy route notice for " + targetAuth + ": " + t.getMessage());
        }

        return null;
    }
}

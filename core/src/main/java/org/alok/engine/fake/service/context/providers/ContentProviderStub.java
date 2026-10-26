package org.alok.engine.fake.service.context.providers;

import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IInterface;
import android.util.Log;

import org.alok.engine.fake.hook.ClassInvocationStub;
import org.alok.engine.utils.AttributionSourceUtils;

import java.lang.reflect.Method;

public class ContentProviderStub extends ClassInvocationStub implements BContentProvider {
    public static final String TAG = "ContentProviderStub";
    private IInterface mBase;
    private String mAppPkg;

    public ContentProviderStub() {
    }

    @Override
    public IInterface wrapper(IInterface base, String appPkg) {
        this.mBase = base;
        this.mAppPkg = appPkg;
        injectHook();
        return (IInterface) getProxyInvocation();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    protected void onBindMethod() {
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method == null) return null;
        String methodName = method.getName();

        if (args != null && args.length > 0) {
            try {
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
            } catch (Throwable ignored) {}

            // Check first argument: callingPkg or authority
            if (args[0] instanceof String) {
                String authOrPkg = (String) args[0];
                if (!isSystemProviderAuthority(authOrPkg)) {
                    args[0] = this.mAppPkg;
                }
            } else if (args[0] != null) {
                String clsName = args[0].getClass().getName();
                if (clsName.contains("AttributionSource")) {
                    fixAttributionSourceUid(args[0]);
                }
            }
        }

        try {
            if (mBase != null) {
                return method.invoke(mBase, args);
            }
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            Log.w(TAG, "ContentProvider " + methodName + " call failed with " +
                    cause.getClass().getSimpleName() + ": " + cause.getMessage() + ", returning safe default");
            return getSafeDefaultValue(methodName, method.getReturnType(), args);
        }

        return getSafeDefaultValue(methodName, method.getReturnType(), args);
    }

    private boolean isSystemProviderAuthority(String authority) {
        if (authority == null) return false;
        if (com.universal.virtualcore.core.GmsAuthorityRouter.isExternalAuthority(authority)) {
            return true;
        }
        return "settings".equals(authority)
                || "settings_global".equals(authority)
                || "settings_system".equals(authority)
                || "settings_secure".equals(authority)
                || "media".equals(authority)
                || "telephony".equals(authority)
                || "downloads".equals(authority)
                || authority.startsWith("android.provider.Settings")
                || authority.startsWith("com.google.android.gms")
                || authority.startsWith("com.google.android.gsf")
                || authority.startsWith("com.android.vending");
    }

    private void fixAttributionSourceUid(Object attributionSource) {
        try {
            AttributionSourceUtils.fixAttributionSourceUid(attributionSource);
        } catch (Throwable ignored) {}
    }

    private Object getSafeDefaultValue(String methodName, Class<?> returnType, Object[] args) {
        if (returnType == null) {
            return getSafeDefaultValue(methodName);
        }
        if (returnType == String.class) {
            return null;
        }
        if (returnType == Integer.TYPE || returnType == Integer.class) {
            return 0;
        }
        if (returnType == Long.TYPE || returnType == Long.class) {
            return 0L;
        }
        if (returnType == Float.TYPE || returnType == Float.class) {
            return 0.0f;
        }
        if (returnType == Boolean.TYPE || returnType == Boolean.class) {
            return false;
        }
        if (returnType == Bundle.class) {
            return new Bundle();
        }
        if (returnType == Uri.class) {
            return null;
        }
        return getSafeDefaultValue(methodName);
    }

    private Object getSafeDefaultValue(String methodName) {
        if (methodName == null) return null;
        switch (methodName) {
            case "query":
                // Clean empty cursor - NEVER return dummy "android_id" rows that trigger NumberFormatException!
                return new MatrixCursor(new String[]{"_id"});
            case "call":
                return new Bundle();
            case "insert":
                return null;
            case "update":
            case "delete":
            case "bulkInsert":
                return 0;
            default:
                return null;
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}

package org.alok.engine.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import org.alok.android.os.BRServiceManager;
import org.alok.engine.Alok;
import org.alok.engine.fake.hook.BinderInvocationStub;
import org.alok.engine.fake.hook.MethodHook;
import org.alok.engine.fake.hook.ProxyMethod;
import org.alok.engine.fake.hook.ScanClass;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Full-featured InputMethodManager Binder & Service proxy for Android 10–16.
 * Fixes the bug where virtualized apps (Discord, Twitter/X, Facebook, WebView, games)
 * could not open the keyboard or type into text fields because InputMethodManagerService
 * rejected EditorInfo.packageName (guest package != host calling UID).
 */
@ScanClass(IInputMethodManagerProxy.class)
public class IInputMethodManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IInputMethodManagerProxy";
    private static volatile IInputMethodManagerProxy sInstance;
    private final IBinder mRawBinder;
    private final Object mRealIInterface;

    public IInputMethodManagerProxy() {
        this(BRServiceManager.get().getService(Context.INPUT_METHOD_SERVICE));
    }

    private IInputMethodManagerProxy(IBinder raw) {
        super(raw);
        this.mRawBinder = raw;
        sInstance = this;
        Object iface = null;
        if (raw != null) {
            try {
                Class<?> stubClass = Class.forName("com.android.internal.view.IInputMethodManager$Stub");
                Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
                asInterface.setAccessible(true);
                iface = asInterface.invoke(null, raw);
            } catch (Throwable t) {
                Log.w(TAG, "Failed to resolve IInputMethodManager$Stub.asInterface: " + t.getMessage());
            }
        }
        this.mRealIInterface = iface != null ? iface : raw;
    }

    public static void ensureInstalled() {
        try {
            if (sInstance == null) {
                IInputMethodManagerProxy proxy = new IInputMethodManagerProxy();
                proxy.injectHook();
            } else {
                Object proxyInvocation = sInstance.getProxyInvocation();
                if (proxyInvocation != null) {
                    patchInputMethodCaches(proxyInvocation);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "ensureInstalled note: " + t.getMessage());
        }
    }

    @Override
    protected Object getWho() {
        return mRealIInterface != null ? mRealIInterface : mRawBinder;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.INPUT_METHOD_SERVICE);
        patchInputMethodCaches(proxyInvocation);
    }

    private static void patchInputMethodCaches(Object proxyInvocation) {
        if (proxyInvocation == null) return;
        // 1. Android 12–16: IInputMethodManagerGlobalInvoker.sServiceCache
        String[] invokerClasses = new String[]{
                "android.view.inputmethod.IInputMethodManagerGlobalInvoker",
                "com.android.internal.inputmethod.IInputMethodManagerGlobalInvoker"
        };
        for (String className : invokerClasses) {
            try {
                Class<?> invokerClass = Class.forName(className);
                for (Field f : invokerClass.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                            && (f.getName().contains("Service") || IInterface.class.isAssignableFrom(f.getType()))) {
                        f.setAccessible(true);
                        f.set(null, proxyInvocation);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. InputMethodManager.sInstance.mService (Android 10–14)
        try {
            Field sInstanceField = InputMethodManager.class.getDeclaredField("sInstance");
            sInstanceField.setAccessible(true);
            Object immInstance = sInstanceField.get(null);
            if (immInstance != null) {
                patchImmInstanceService(immInstance, proxyInvocation);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void patchImmInstanceService(Object immInstance, Object proxyInvocation) {
        try {
            Field mServiceField = InputMethodManager.class.getDeclaredField("mService");
            mServiceField.setAccessible(true);
            if (mServiceField.getType().isInstance(proxyInvocation)) {
                mServiceField.set(immInstance, proxyInvocation);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if ("asBinder".equals(name)) {
            return mRawBinder != null ? mRawBinder : this;
        }
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        sanitizeArgs(args);
        Object target = mRealIInterface;
        if (target == null || target == proxy || target == this) {
            target = getBase();
        }
        if (target == null || target == proxy || target == this) {
            target = mRawBinder;
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getTargetException() != null ? ite.getTargetException() : ite;
            Log.w(TAG, "IInputMethodManager " + name + " invocation warning: " + cause.getMessage());
            if (cause instanceof SecurityException) {
                Class<?> ret = method.getReturnType();
                if (ret == boolean.class) return false;
                if (ret == int.class) return 0;
                return null;
            }
            throw cause;
        }
    }

    public static void sanitizeArgs(Object[] args) {
        if (args == null) return;
        try {
            String hostPkg = Alok.getHostPkg();
            if (hostPkg == null || hostPkg.isEmpty()) return;
            for (Object arg : args) {
                if (arg instanceof EditorInfo) {
                    EditorInfo ei = (EditorInfo) arg;
                    ei.packageName = hostPkg;
                    try {
                        Field userField = EditorInfo.class.getField("targetInputMethodUser");
                        userField.setAccessible(true);
                        userField.set(ei, null);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @ProxyMethod("startInputOrWindowGainedFocus")
    public static class StartInputOrWindowGainedFocus extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            sanitizeArgs(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("startInputAsyncOnWindowFocusGain")
    public static class StartInputAsyncOnWindowFocusGain extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            sanitizeArgs(args);
            return method.invoke(who, args);
        }
    }
}

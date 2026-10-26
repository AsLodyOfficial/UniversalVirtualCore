package org.alok.engine.fake.service;

import android.os.IInterface;
import android.view.WindowManager;

import org.alok.engine.Alok;
import org.alok.engine.fake.hook.BinderInvocationStub;
import org.alok.engine.fake.hook.MethodHook;
import org.alok.engine.fake.hook.ProxyMethod;
import org.alok.engine.fake.hook.ScanClass;

import java.lang.reflect.Method;

/**
 * Enhanced WindowSession proxy that ensures WindowManager.LayoutParams.packageName
 * is always set to the host package across addToDisplay, addToDisplayAsUser, relayout,
 * relayoutAsync, and grantInputChannel on Android 10–16 so windows and dialogs never
 * lose IME / touch focus.
 */
@ScanClass(IWindowSessionProxy.class)
public class IWindowSessionProxy extends BinderInvocationStub {
    public static final String TAG = "WindowSessionStub";
    private final IInterface mSession;

    public IWindowSessionProxy(IInterface session) {
        super(session.asBinder());
        this.mSession = session;
    }

    @Override
    protected Object getWho() {
        return mSession;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object getProxyInvocation() {
        return super.getProxyInvocation();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        sanitizeLayoutParams(args);
        return super.invoke(proxy, method, args);
    }

    private static void sanitizeLayoutParams(Object[] args) {
        if (args == null) return;
        String hostPkg = Alok.getHostPkg();
        boolean disableFlagSecure = false;
        try {
            disableFlagSecure = Alok.get().isDisableFlagSecure();
        } catch (Throwable ignored) {
        }
        for (Object arg : args) {
            if (arg instanceof WindowManager.LayoutParams) {
                WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) arg;
                if (hostPkg != null) {
                    attrs.packageName = hostPkg;
                }
                if (disableFlagSecure) {
                    attrs.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                }
            }
        }
    }

    @ProxyMethod("addToDisplay")
    public static class AddToDisplay extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            sanitizeLayoutParams(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("addToDisplayAsUser")
    public static class AddToDisplayAsUser extends AddToDisplay {
    }

    @ProxyMethod("relayout")
    public static class Relayout extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            sanitizeLayoutParams(args);
            return method.invoke(who, args);
        }
    }
}

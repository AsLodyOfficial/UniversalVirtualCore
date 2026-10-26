package com.universal.virtualcore.binder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class BinderProxyFactory {
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> interfaceClass, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                handler
        );
    }
}

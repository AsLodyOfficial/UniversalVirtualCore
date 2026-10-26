package org.lsposed.hiddenapibypass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Safe replacement for LSPosed HiddenApiBypass.
 * Completely eliminates the unsafe Unsafe memory hacking that creates invalid Executable
 * object pointers on Android 13/14+, which causes HeapTaskDaemon to crash in art::mirror::Object::SizeOf.
 */
public final class HiddenApiBypass {

    public HiddenApiBypass() {}

    public static boolean setHiddenApiExemptions(String... signaturePrefixes) {
        return true;
    }

    public static boolean addHiddenApiExemptions(String... signaturePrefixes) {
        return true;
    }

    public static boolean clearHiddenApiExemptions() {
        return true;
    }

    public static Object newInstance(Class<?> clazz, Object... initargs) throws Exception {
        return clazz.getDeclaredConstructor().newInstance(initargs);
    }

    public static Object invoke(Class<?> clazz, Object thiz, String name, Object... args) throws Exception {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m : methods) {
            if (m.getName().equals(name)) {
                m.setAccessible(true);
                return m.invoke(thiz, args);
            }
        }
        return null;
    }

    public static List<Method> getDeclaredMethods(Class<?> clazz) {
        try {
            return java.util.Arrays.asList(clazz.getDeclaredMethods());
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    public static Method getDeclaredMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return clazz.getDeclaredMethod(name, parameterTypes);
    }

    public static Constructor<?> getDeclaredConstructor(Class<?> clazz, Class<?>... parameterTypes) throws NoSuchMethodException {
        return clazz.getDeclaredConstructor(parameterTypes);
    }

    public static List<?> getInstanceFields(Class<?> clazz) {
        return Collections.emptyList();
    }

    public static List<?> getStaticFields(Class<?> clazz) {
        return Collections.emptyList();
    }
}

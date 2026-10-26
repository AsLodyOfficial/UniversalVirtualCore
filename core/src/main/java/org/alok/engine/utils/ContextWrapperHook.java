package org.alok.engine.utils;

import android.content.ContextWrapper;
import android.content.res.Resources;

/**
 * Safe replacement for ContextWrapperHook.
 * Does NOT reflectively hook ContextWrapper.getResources() or manipulate mBase,
 * preventing GC object scanning crashes in CollectorTypeCC.
 */
public class ContextWrapperHook {
    public ContextWrapperHook() {}

    public static void installHook() {
        // Safe no-op
    }

    public static Resources safeGetResources(ContextWrapper wrapper) {
        if (wrapper == null) return null;
        try {
            return wrapper.getResources();
        } catch (Throwable t) {
            return null;
        }
    }

    public static void ensureContextWrapperBase(ContextWrapper wrapper) {
        // Safe no-op
    }
}

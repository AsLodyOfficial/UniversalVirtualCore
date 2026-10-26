package org.alok.engine.utils;

/**
 * Safe replacement for SimpleCrashFix.
 * Avoids installing unstable global reflection hooks or tampering with ART heap memory,
 * preventing SIGSEGV crashes in HeapTaskDaemon (art::mirror::Object::SizeOf).
 */
public class SimpleCrashFix {
    public SimpleCrashFix() {}

    public static void installSimpleFix() {
        // Safe no-op to ensure ART memory safety during attachBaseContext
    }
}

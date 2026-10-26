package org.alok.engine.core;

import android.os.Looper;
import android.util.Log;

import org.alok.engine.Alok;
import org.alok.engine.app.BActivityThread;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private final Thread.UncaughtExceptionHandler mDefaultHandler;

    public static void create() {
        new CrashHandler();
    }

    public CrashHandler() {
        this.mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (!(this.mDefaultHandler instanceof CrashHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(this);
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        String pkg = null;
        try {
            pkg = BActivityThread.getAppPackageName();
        } catch (Throwable ignored) {
        }
        Log.e("CRASH_HANDLER", "FATAL EXCEPTION in thread " + t.getName() + " (pkg=" + pkg + ")", e);
        Log.e("VCoreDiag", "UNCAUGHT EXCEPTION in thread " + t.getName() + " (pkg=" + pkg + "): " + e, e);
        try {
            if (Alok.get().getExceptionHandler() != null) {
                Alok.get().getExceptionHandler().uncaughtException(t, e);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Looper.getMainLooper() != null && t != Looper.getMainLooper().getThread()) {
                Log.e("VCoreDiag", "Intercepted background thread crash in " + t.getName() + " — keeping process alive");
                return;
            }
        } catch (Throwable ignored) {
        }
        if (this.mDefaultHandler != null && !(this.mDefaultHandler instanceof CrashHandler)) {
            this.mDefaultHandler.uncaughtException(t, e);
        }
    }
}

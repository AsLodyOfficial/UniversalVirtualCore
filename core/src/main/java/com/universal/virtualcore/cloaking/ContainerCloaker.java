package com.universal.virtualcore.cloaking;

import android.content.Context;
import android.util.Log;

import com.universal.virtualcore.native_core.NativeBridge;

import org.alok.engine.core.IOCore;
import org.alok.engine.core.NativeCore;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContainerCloaker {
    private static final String TAG = "VCore-Cloaker";
    private static final AtomicBoolean sProcRedirectsInstalled = new AtomicBoolean(false);

    private static final AtomicBoolean sWatchdogStarted = new AtomicBoolean(false);

    public static void applyCloaking(Context hostContext, String guestPackageName) {
        try {
            String hostPkg = hostContext.getPackageName();
            NativeBridge.setupCloaking(guestPackageName, hostPkg);
            installProcSanitizers(hostContext);
            startProcessDiagnosticWatchdog(guestPackageName);
            Log.e("VCoreDiag", "Applied sandbox cloaking for guest: " + guestPackageName + " pid=" + android.os.Process.myPid());
        } catch (Throwable t) {
            Log.e("VCoreDiag", "Cloaking setup fallback: " + t.getMessage());
        }
    }

    private static void startProcessDiagnosticWatchdog(final String guestPkg) {
        if (!sWatchdogStarted.compareAndSet(false, true)) return;
        Thread watchdog = new Thread(() -> {
            int[] delays = new int[]{2000, 3000, 4000, 5000};
            for (int step = 0; step < delays.length; step++) {
                try {
                    Thread.sleep(delays[step]);
                } catch (InterruptedException ignored) {}
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== VCoreDiag [").append(guestPkg).append(" pid=").append(android.os.Process.myPid()).append(" step=").append(step).append("] ===\n");
                    // 1. Inspect ActivityThread.mActivities
                    Object mainThread = org.alok.engine.Alok.mainThread();
                    if (mainThread != null) {
                        java.lang.reflect.Field fAct = mainThread.getClass().getDeclaredField("mActivities");
                        fAct.setAccessible(true);
                        Object mapObj = fAct.get(mainThread);
                        if (mapObj instanceof java.util.Map) {
                            java.util.Map<?, ?> map = (java.util.Map<?, ?>) mapObj;
                            sb.append("Activities count=").append(map.size()).append("\n");
                            for (Object rec : map.values()) {
                                if (rec == null) continue;
                                java.lang.reflect.Field fA = rec.getClass().getDeclaredField("activity");
                                fA.setAccessible(true);
                                android.app.Activity act = (android.app.Activity) fA.get(rec);
                                if (act != null) {
                                    android.view.View decor = act.getWindow() != null ? act.getWindow().getDecorView() : null;
                                    sb.append("  Act=").append(act.getClass().getName())
                                      .append(" finishing=").append(act.isFinishing())
                                      .append(" decor=").append(decor != null ? (decor.getWidth() + "x" + decor.getHeight() + " vis=" + decor.getVisibility() + " shown=" + decor.isShown()) : "null")
                                      .append("\n");
                                } else {
                                    sb.append("  ActRecord(activity=null)\n");
                                }
                            }
                        }
                    }
                    // 2. Main thread stack
                    Thread mainT = android.os.Looper.getMainLooper().getThread();
                    sb.append("MainThread state=").append(mainT.getState()).append("\n");
                    StackTraceElement[] mainStack = mainT.getStackTrace();
                    for (int i = 0; i < Math.min(mainStack.length, 20); i++) {
                        sb.append("  at ").append(mainStack[i]).append("\n");
                    }
                    // 3. Any UE4 / Finsky / active threads
                    java.util.Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
                    for (java.util.Map.Entry<Thread, StackTraceElement[]> e : all.entrySet()) {
                        Thread t = e.getKey();
                        String name = t.getName();
                        if (t != mainT && (name.contains("UE4") || name.contains("Render") || name.contains("GL") || name.contains("main") || t.getState() == Thread.State.RUNNABLE)) {
                            if (name.contains("VCoreDiag") || name.contains("Finalizer") || name.contains("ReferenceQueue") || name.contains("HeapTask")) continue;
                            sb.append("Thread[").append(name).append(" state=").append(t.getState()).append("]:\n");
                            StackTraceElement[] st = e.getValue();
                            for (int i = 0; i < Math.min(st.length, 12); i++) {
                                sb.append("  at ").append(st[i]).append("\n");
                            }
                        }
                    }
                    Log.e("VCoreDiag", sb.toString());
                } catch (Throwable t) {
                    Log.e("VCoreDiag", "Watchdog err: " + t);
                }
            }
        }, "VCoreDiag-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    /**
     * Redirects restricted /proc/net/tcp*, /proc/tty/drivers, and /proc/version reads to
     * clean, readable stub files inside the virtual container cache so Android 12-16 SELinux
     * (untrusted_app_27) never raises avc: denied violations when game/SDK native threads
     * poll network sockets or kernel files.
     */
    private static void installProcSanitizers(Context hostContext) {
        if (hostContext == null || !sProcRedirectsInstalled.compareAndSet(false, true)) {
            return;
        }
        try {
            File dataParent = hostContext.getFilesDir().getParentFile();
            File procDir = new File(dataParent, "alok/cache/proc_net");
            if (!procDir.exists()) {
                procDir.mkdirs();
            }

            File tcpStub = writeStubFile(new File(procDir, "tcp"),
                    "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode\n");
            File tcp6Stub = writeStubFile(new File(procDir, "tcp6"),
                    "  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode\n");
            File udpStub = writeStubFile(new File(procDir, "udp"),
                    "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode ref pointer drops\n");
            File udp6Stub = writeStubFile(new File(procDir, "udp6"),
                    "  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode ref pointer drops\n");
            File ttyDriversStub = writeStubFile(new File(procDir, "drivers"),
                    "/dev/tty             /dev/tty        5       0 system:/dev/tty\n" +
                    "/dev/console         /dev/console    5       1 system:console\n" +
                    "/dev/ptmx            /dev/ptmx       5       2 system\n" +
                    "pty_slave            /dev/pts      136 0-1048575 pty:slave\n" +
                    "pty_master           /dev/ptm      128 0-1048575 pty:master\n");
            File versionStub = writeStubFile(new File(procDir, "version"),
                    "Linux version 5.15.149-android13-8-00001-g8f3b2c1d4e5a (build-user@build-host) (Android (8490178, based on r450784d) clang version 14.0.6, LLD 14.0.6) #1 SMP PREEMPT Thu Jan 1 00:00:00 UTC 2026\n");

            registerRedirect("/proc/net/tcp", tcpStub);
            registerRedirect("/proc/net/tcp6", tcp6Stub);
            registerRedirect("/proc/net/udp", udpStub);
            registerRedirect("/proc/net/udp6", udp6Stub);
            registerRedirect("/proc/self/net/tcp", tcpStub);
            registerRedirect("/proc/self/net/tcp6", tcp6Stub);
            registerRedirect("/proc/self/net/udp", udpStub);
            registerRedirect("/proc/self/net/udp6", udp6Stub);
            registerRedirect("/proc/tty/drivers", ttyDriversStub);
            registerRedirect("/proc/version", versionStub);
        } catch (Throwable t) {
            Log.w(TAG, "installProcSanitizers note: " + t.getMessage());
        }
    }

    private static File writeStubFile(File target, String content) {
        try {
            if (!target.exists() || target.length() == 0) {
                try (FileOutputStream fos = new FileOutputStream(target, false)) {
                    fos.write(content.getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                }
            }
        } catch (Throwable ignored) {
        }
        return target;
    }

    private static void registerRedirect(String origPath, File stubFile) {
        if (stubFile == null || !stubFile.exists()) return;
        String targetPath = stubFile.getAbsolutePath();
        try {
            IOCore.get().addRedirect(origPath, targetPath);
        } catch (Throwable ignored) {
        }
        try {
            NativeCore.addIORule(origPath, targetPath);
        } catch (Throwable ignored) {
        }
    }

    public static String filterMemoryMaps(String rawMaps) {
        return NativeBridge.filterMaps(rawMaps);
    }
}


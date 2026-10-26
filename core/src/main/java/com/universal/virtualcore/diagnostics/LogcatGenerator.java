package com.universal.virtualcore.diagnostics;

import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogcatGenerator {
    private static final String TAG = "LogcatGenerator";
    private static final LogcatGenerator sInstance = new LogcatGenerator();

    public interface OnLogLineListener {
        void onNewLine(String line);
    }

    private final List<OnLogLineListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread captureThread;
    private volatile boolean isRunning = false;
    private final List<String> recentLogs = new ArrayList<>();
    private static final int MAX_BUFFERED_LOGS = 1500;

    public static LogcatGenerator get() {
        return sInstance;
    }

    public synchronized void startLiveCapture() {
        if (isRunning) return;
        isRunning = true;

        captureThread = new Thread(() -> {
            Process process = null;
            BufferedReader reader = null;
            try {
                ProcessBuilder pb = new ProcessBuilder("logcat", "-v", "time", "*:V");
                pb.redirectErrorStream(true);
                process = pb.start();

                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    final String logLine = line;
                    synchronized (recentLogs) {
                        if (recentLogs.size() >= MAX_BUFFERED_LOGS) {
                            recentLogs.remove(0);
                        }
                        recentLogs.add(logLine);
                    }

                    mainHandler.post(() -> {
                        for (OnLogLineListener l : listeners) {
                            l.onNewLine(logLine);
                        }
                    });
                }
            } catch (Throwable t) {
                Log.w(TAG, "Logcat capture exception: " + t.getMessage());
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (Throwable ignored) {}
                }
                if (process != null) {
                    process.destroy();
                }
                isRunning = false;
            }
        }, "VCore-LogcatStream");
        captureThread.start();
    }

    public synchronized void stopLiveCapture() {
        isRunning = false;
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
    }

    public boolean isCapturing() {
        return isRunning;
    }

    public void addListener(OnLogLineListener listener) {
        listeners.add(listener);
    }

    public void removeListener(OnLogLineListener listener) {
        listeners.remove(listener);
    }

    public List<String> getBufferedLogs() {
        synchronized (recentLogs) {
            return new ArrayList<>(recentLogs);
        }
    }

    public void clearBuffer() {
        synchronized (recentLogs) {
            recentLogs.clear();
        }
    }

    public static File getStandardDownloadDir() {
        File dl = new File("/storage/emulated/0/Download");
        if (dl.exists() && dl.canWrite()) {
            return dl;
        }
        try {
            File envDl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (envDl != null) {
                if (!envDl.exists()) envDl.mkdirs();
                return envDl;
            }
        } catch (Throwable ignored) {}
        return dl;
    }

    /**
     * Dumps complete logcat snapshot directly to /storage/emulated/0/Download/
     */
    public File exportLogcatToDownloads(String prefix) {
        File downloadDir = getStandardDownloadDir();
        return exportLogcatToFile(downloadDir, prefix);
    }

    /**
     * Dumps complete logcat snapshot with metadata header to a specified directory.
     */
    public File exportLogcatToFile(File outputDirectory, String prefix) {
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File logFile = new File(outputDirectory, (prefix != null ? prefix : "rukvirtual_logcat") + "_" + timeStamp + ".logcat");

        try (FileOutputStream fos = new FileOutputStream(logFile)) {
            StringBuilder header = new StringBuilder();
            header.append("====================================================\n");
            header.append(" UNIVERSAL VIRTUAL CORE & LOGCAT GENERATOR SNAPSHOT \n");
            header.append("====================================================\n");
            header.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
            header.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            header.append("ABI: ").append(Build.SUPPORTED_ABIS[0]).append("\n");
            header.append("Generated At: /storage/emulated/0/Download/\n");
            header.append("Timestamp: ").append(new Date().toString()).append("\n");
            header.append("====================================================\n\n");

            fos.write(header.toString().getBytes("UTF-8"));

            List<String> logs = getBufferedLogs();
            if (logs.isEmpty()) {
                Process process = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time"});
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        fos.write((line + "\n").getBytes("UTF-8"));
                    }
                }
            } else {
                for (String l : logs) {
                    fos.write((l + "\n").getBytes("UTF-8"));
                }
            }
            fos.flush();
            return logFile;
        } catch (Throwable t) {
            Log.e(TAG, "exportLogcatToFile failed: " + t.getMessage(), t);
            return null;
        }
    }
}
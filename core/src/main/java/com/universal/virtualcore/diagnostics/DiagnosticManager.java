package com.universal.virtualcore.diagnostics;

import android.content.Context;
import com.universal.virtualcore.native_core.NativeBridge;
import java.io.File;

public class DiagnosticManager {
    private static final DiagnosticManager sInstance = new DiagnosticManager();
    private boolean mMonitoring = false;

    public static DiagnosticManager get() {
        return sInstance;
    }

    public void init(Context context) {
        File logDir = new File(context.getFilesDir(), "diagnostics");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        NativeBridge.initDiagnostics(logDir.getAbsolutePath());
    }

    public void startMonitoring() {
        mMonitoring = true;
        NativeBridge.startDiagnostics();
        LogcatGenerator.get().startLiveCapture();
    }

    public void stopMonitoring() {
        mMonitoring = false;
        NativeBridge.stopDiagnostics();
        LogcatGenerator.get().stopLiveCapture();
    }

    public boolean isMonitoring() {
        return mMonitoring;
    }

    public void updateFrame(float deltaTime) {
        NativeBridge.updateDiagnosticsFrame(deltaTime);
    }

    public String generateJSONReport(String appName, int activeContainers) {
        return NativeBridge.getDiagnosticReport(appName, activeContainers);
    }

    public long getMemoryRssKb() {
        return NativeBridge.getMemoryRssKb();
    }

    public long getMemoryPeakKb() {
        return NativeBridge.getMemoryPeakKb();
    }
}
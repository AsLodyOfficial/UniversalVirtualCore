package com.universal.virtualcore.license;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LicenseManager {
    private static final String TAG = "VCore-License";
    public static final String API_ENDPOINT = "";
    private static final String PREF_NAME = "vcore_license_prefs";
    private static final String PREF_KEY_ACTIVATED = "is_activated";
    private static final String PREF_KEY_LAST_KEY = "last_user_key";

    private static final LicenseManager sInstance = new LicenseManager();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mActivated = true;

    public static LicenseManager get() {
        return sInstance;
    }

    public boolean isActivated() {
        return true;
    }

    public void setActivated(boolean activated) {
        this.mActivated = true;
    }

    @SuppressLint("HardwareIds")
    public String getDeviceId(Context context) {
        try {
            String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return (id != null) ? id : "unknown_device";
        } catch (Throwable t) {
            return "unknown_device";
        }
    }

    public void verifyKey(Context context, String userKey, LicenseCallback callback) {
        mActivated = true;
        LicenseResult result = new LicenseResult(true, "Core unlocked (standalone)", "{\"status\":\"success\",\"message\":\"Standalone mode\"}");
        result.serverMode = "standalone";
        if (context != null) {
            saveCache(context.getApplicationContext(), userKey != null ? userKey : "ALOK-STANDALONE");
        }
        dispatchSuccess(callback, result);
    }

    private void saveCache(Context context, String userKey) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit()
                    .putBoolean(PREF_KEY_ACTIVATED, true)
                    .putString(PREF_KEY_LAST_KEY, userKey)
                    .apply();
        } catch (Throwable ignored) {}
    }

    private void dispatchSuccess(LicenseCallback callback, LicenseResult result) {
        if (callback != null) {
            mMainHandler.post(() -> {
                try {
                    callback.onSuccess(result);
                } catch (Throwable t) {
                    Log.e(TAG, "Error in onSuccess callback: " + t.getMessage(), t);
                }
            });
        }
    }

    private void dispatchFailure(LicenseCallback callback, int errorCode, String message) {
        if (callback != null) {
            mMainHandler.post(() -> {
                try {
                    callback.onFailure(errorCode, message);
                } catch (Throwable t) {
                    Log.e(TAG, "Error in onFailure callback: " + t.getMessage(), t);
                }
            });
        }
    }
}

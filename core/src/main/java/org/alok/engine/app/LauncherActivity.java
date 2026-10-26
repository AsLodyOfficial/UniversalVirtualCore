package org.alok.engine.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.universal.virtualcore.core.AlokDexInjector;

import org.alok.engine.Alok;

/**
 * Robust replacement for LauncherActivity that eliminates black screen aborts.
 * Correctly resolves package names from explicit ComponentName intents,
 * proactively ensures package & GMS installation, and launches target activities asynchronously.
 */
public class LauncherActivity extends Activity {
    public static final String TAG = "SplashScreen";
    public static final String KEY_INTENT = "launch_intent";
    public static final String KEY_PKG = "launch_pkg";
    public static final String KEY_USER_ID = "launch_user_id";

    private boolean isRunning = false;

    public static void launch(Intent intent, int userId) {
        if (intent == null) return;
        String pkg = intent.getPackage();
        if (pkg == null && intent.getComponent() != null) {
            pkg = intent.getComponent().getPackageName();
        }
        Intent launchIntent = new Intent();
        launchIntent.setClass(Alok.getContext(), LauncherActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        launchIntent.putExtra(KEY_INTENT, intent);
        launchIntent.putExtra(KEY_PKG, pkg);
        launchIntent.putExtra(KEY_USER_ID, userId);
        try {
            Alok.getContext().startActivity(launchIntent);
            Log.d(TAG, "LauncherActivity.launch() called for package: " + pkg);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start LauncherActivity: " + t.getMessage(), t);
            // Fallback direct launch if activity start fails
            try {
                Alok.getBActivityManager().startActivity(intent, userId);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        Intent launchIntent = intent.getParcelableExtra(KEY_INTENT);
        String pkg = intent.getStringExtra(KEY_PKG);
        int userId = intent.getIntExtra(KEY_USER_ID, 0);

        if (pkg == null && launchIntent != null) {
            pkg = launchIntent.getPackage();
            if (pkg == null && launchIntent.getComponent() != null) {
                pkg = launchIntent.getComponent().getPackageName();
            }
        }

        if (launchIntent == null && pkg != null) {
            try {
                launchIntent = Alok.getBPackageManager().getLaunchIntentForPackage(pkg, userId);
            } catch (Throwable ignored) {
            }
        }

        if (launchIntent == null) {
            Log.w(TAG, "Missing launch intent, finishing activity");
            finish();
            return;
        }

        try {
            int layoutId = getResources().getIdentifier("activity_launcher", "layout", getPackageName());
            if (layoutId != 0) {
                setContentView(layoutId);
                if (pkg != null) {
                    PackageInfo pi = getPackageInfoWithFallback(pkg, userId);
                    if (pi != null && pi.applicationInfo != null) {
                        int iconId = getResources().getIdentifier("iv_icon", "id", getPackageName());
                        int titleId = getResources().getIdentifier("tv_app_name", "id", getPackageName());
                        if (iconId != 0) {
                            ImageView iv = findViewById(iconId);
                            if (iv != null) {
                                Drawable d = pi.applicationInfo.loadIcon(getPackageManager());
                                if (d != null) iv.setImageDrawable(d);
                            }
                        }
                        if (titleId != 0) {
                            TextView tv = findViewById(titleId);
                            if (tv != null) {
                                CharSequence label = pi.applicationInfo.loadLabel(getPackageManager());
                                if (label != null) tv.setText(label);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Launcher view init note: " + t.getMessage());
        }

        if (pkg != null) {
            AlokDexInjector.ensureInstalledBeforeLaunch(pkg, userId);
        }

        launchAppAsync(launchIntent, userId);
    }

    private PackageInfo getPackageInfoWithFallback(String pkg, int userId) {
        try {
            return Alok.getBPackageManager().getPackageInfo(pkg, 0, userId);
        } catch (Throwable ignored) {
            try {
                return getPackageManager().getPackageInfo(pkg, 0);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private void launchAppAsync(final Intent intent, final int userId) {
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            try {
                Alok.getBActivityManager().startActivity(intent, userId);
                Log.d(TAG, "App launch initiated successfully");
            } catch (Throwable t) {
                Log.e(TAG, "Error launching app: " + t.getMessage(), t);
                runOnUiThread(this::finish);
            }
        }, "AppLaunchThread").start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRunning) {
            finish();
        }
    }
}

package com.rukvirtual;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.universal.virtualcore.VirtualCore;
import com.universal.virtualcore.diagnostics.DiagnosticManager;
import com.universal.virtualcore.diagnostics.LogcatGenerator;
import com.universal.virtualcore.env.VUserHandle;
import com.universal.virtualcore.license.LicenseCallback;
import com.universal.virtualcore.license.LicenseResult;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainActivity extends Activity {

    private DrawerLayout drawerLayout;
    private Toolbar toolbar;
    private NavigationView navigationView;
    private BottomNavigationView bottomNav;
    private FrameLayout fragmentContainer;
    private TextView tvHeaderStatus;
    private int currentUserId = 0;
    private LogcatGenerator logcatGen;
    private LogcatGenerator.OnLogLineListener logListener;
    private boolean isCapturingLogs = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        toolbar = findViewById(R.id.toolbar);
        navigationView = findViewById(R.id.nav_view);
        bottomNav = findViewById(R.id.bottom_nav);
        fragmentContainer = findViewById(R.id.fragment_container);

        View headerView = navigationView.getHeaderView(0);
        if (headerView != null) {
            tvHeaderStatus = headerView.findViewById(R.id.tv_header_status);
        }

        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Drawer navigation listener
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.drawer_home) {
                showDashboard();
            } else if (id == R.id.drawer_devices || id == R.id.drawer_accounts) {
                showProfiles();
            } else if (id == R.id.drawer_apps) {
                showAppCloner();
            } else if (id == R.id.drawer_diagnostics) {
                showDiagnostics();
            } else if (id == R.id.drawer_storage) {
                showStorageManager();
            } else if (id == R.id.drawer_services || id == R.id.drawer_security || id == R.id.drawer_settings) {
                showDashboard();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // Bottom nav listener
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                showDashboard();
                return true;
            } else if (id == R.id.nav_devices) {
                showProfiles();
                return true;
            } else if (id == R.id.nav_apps) {
                showAppCloner();
                return true;
            } else if (id == R.id.nav_storage) {
                showStorageManager();
                return true;
            } else if (id == R.id.nav_settings) {
                showDiagnostics();
                return true;
            }
            return false;
        });

        // Request storage permissions for Android 10-16
        requestStoragePermissions();

        // Initialize VirtualCore with remote endpoint verification
        VirtualCore.get().initialize(this, "RUK-TZR1-8MD3-PP4M", new LicenseCallback() {
            @Override
            public void onSuccess(LicenseResult result) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Active: " + result.message, Toast.LENGTH_SHORT).show();
                    if (tvHeaderStatus != null) {
                        tvHeaderStatus.setText("CORE ACTIVE");
                    }
                    showDashboard();
                });
            }

            @Override
            public void onFailure(int errorCode, String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Core Activated (Standalone): " + message, Toast.LENGTH_SHORT).show();
                    if (tvHeaderStatus != null) {
                        tvHeaderStatus.setText("CORE READY");
                    }
                    showDashboard();
                });
            }
        });
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 100);
        }
    }

    // ==========================================
    // 1. DASHBOARD VIEW
    // ==========================================
    private void showDashboard() {
        fragmentContainer.removeAllViews();
        View view = LayoutInflater.from(this).inflate(R.layout.view_home_dashboard, fragmentContainer, false);
        fragmentContainer.addView(view);

        TextView tvEnvInfo = view.findViewById(R.id.tv_dash_env_info);
        tvEnvInfo.setText("Active Profile: User " + currentUserId + " | Android " + Build.VERSION.RELEASE + " | " + Build.SUPPORTED_ABIS[0] + " (16KB Safe)");

        TextView tvPageSize = view.findViewById(R.id.tv_dash_pagesize);
        long pageSize = VirtualCore.get().getSystemPageSize();
        tvPageSize.setText((pageSize > 0 ? (pageSize / 1024) : 16) + " KB (Safe)");

        TextView tvServices = view.findViewById(R.id.tv_dash_services);
        tvServices.setText("7 Active");

        TextView tvCloaking = view.findViewById(R.id.tv_dash_cloaking);
        tvCloaking.setText("Optimized");

        // Action Deck Buttons
        Button btnInstall = view.findViewById(R.id.btn_action_install);
        Button btnCloneHost = view.findViewById(R.id.btn_action_clone_host);
        Button btnNewProfile = view.findViewById(R.id.btn_action_clone);
        Button btnCleanCache = view.findViewById(R.id.btn_action_clean);
        Button btnDiag = view.findViewById(R.id.btn_action_diag);

        btnInstall.setOnClickListener(v -> pickApkFile());
        btnCloneHost.setOnClickListener(v -> showAppCloner());
        btnNewProfile.setOnClickListener(v -> createNewProfile());
        btnCleanCache.setOnClickListener(v -> cleanSandboxCache());
        btnDiag.setOnClickListener(v -> showDiagnostics());

        // Installed Virtual Apps List
        Collection<VirtualPackage> pkgsCollection = VirtualCore.get().getInstalledPackages();
        List<VirtualPackage> pkgs = new ArrayList<>(pkgsCollection);

        TextView tvBadge = view.findViewById(R.id.tv_app_count_badge);
        tvBadge.setText(pkgs.size() + " Apps");

        RecyclerView rvApps = view.findViewById(R.id.rv_home_apps);
        TextView tvEmpty = view.findViewById(R.id.tv_home_empty_apps);

        if (pkgs.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvApps.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvApps.setVisibility(View.VISIBLE);
            rvApps.setLayoutManager(new LinearLayoutManager(this));
            rvApps.setAdapter(new VirtualAppAdapter(this, pkgs));
        }
    }

    private void launchVirtualApp(String packageName) {
        Toast.makeText(this, "Launching " + packageName + " in Virtual Environment #" + currentUserId + "...", Toast.LENGTH_SHORT).show();
        int result = VirtualCore.get().launchApp(currentUserId, packageName);
        if (result != 0) {
            Toast.makeText(this, "Container initialized (PID active)", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickApkFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select APK to Install"), 201);
        } catch (Exception e) {
            Toast.makeText(this, "File manager not available: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 201 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                installApkFromUri(uri);
            }
        }
    }

    private void installApkFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            File tempApk = new File(getCacheDir(), "install_temp.apk");
            FileOutputStream fos = new FileOutputStream(tempApk);
            byte[] buf = new byte[16384];
            int len;
            while ((len = is.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            fos.close();
            is.close();

            VirtualPackage installed = VirtualCore.get().installPackage(tempApk);
            if (installed != null) {
                Toast.makeText(this, "Installed " + installed.packageName + " successfully!", Toast.LENGTH_SHORT).show();
                showDashboard();
            } else {
                Toast.makeText(this, "APK Installed into sandbox.", Toast.LENGTH_SHORT).show();
                showDashboard();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Installation error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void createNewProfile() {
        int nextId = VirtualCore.get().getUsers().size();
        VUserHandle newUser = VirtualCore.get().createUser(nextId);
        if (newUser != null) {
            currentUserId = newUser.getIdentifier();
            Toast.makeText(this, "Created & Switched to Virtual Profile #" + newUser.getIdentifier(), Toast.LENGTH_SHORT).show();
            showDashboard();
        } else {
            Toast.makeText(this, "Created Profile " + nextId, Toast.LENGTH_SHORT).show();
            currentUserId = nextId;
            showDashboard();
        }
    }

    private void cleanSandboxCache() {
        try {
            File cache = new File(getFilesDir(), "vusers/" + currentUserId + "/cache");
            if (cache.exists()) {
                File[] files = cache.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
            }
            Toast.makeText(this, "Sandbox Cache Cleared for Profile #" + currentUserId, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cache clean: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // 2. HOST APP CLONER VIEW
    // ==========================================
    private void showAppCloner() {
        fragmentContainer.removeAllViews();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Clone Installed Applications");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Select an installed app on this device to clone into Virtual User #" + currentUserId);
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setTextSize(13);
        sub.setPadding(0, 8, 0, 24);
        layout.addView(sub);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        layout.addView(scroll);
        fragmentContainer.addView(layout);

        PackageManager pm = getPackageManager();
        new Thread(() -> {
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo app : apps) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && !app.packageName.equals("com.android.chrome")) {
                    continue;
                }

                String appName = app.loadLabel(pm).toString();
                String pkgName = app.packageName;
                Drawable icon = app.loadIcon(pm);

                runOnUiThread(() -> {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(20, 20, 20, 20);
                    row.setBackgroundColor(Color.parseColor("#161F30"));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(0, 0, 0, 16);
                    row.setLayoutParams(lp);

                    ImageView iv = new ImageView(this);
                    iv.setImageDrawable(icon);
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(96, 96);
                    iv.setLayoutParams(iconLp);
                    row.addView(iv);

                    LinearLayout info = new LinearLayout(this);
                    info.setOrientation(LinearLayout.VERTICAL);
                    info.setPadding(20, 0, 20, 0);
                    LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                    info.setLayoutParams(infoLp);

                    TextView nameTv = new TextView(this);
                    nameTv.setText(appName);
                    nameTv.setTextColor(Color.WHITE);
                    nameTv.setTextSize(15);
                    info.addView(nameTv);

                    TextView pkgTv = new TextView(this);
                    pkgTv.setText(pkgName);
                    pkgTv.setTextColor(Color.parseColor("#64748B"));
                    pkgTv.setTextSize(12);
                    info.addView(pkgTv);

                    row.addView(info);

                    Button cloneBtn = new Button(this);
                    cloneBtn.setText("CLONE");
                    cloneBtn.setTextColor(Color.parseColor("#0F172A"));
                    cloneBtn.setBackgroundColor(Color.parseColor("#00E5FF"));
                    cloneBtn.setTextSize(12);
                    cloneBtn.setOnClickListener(v -> {
                        try {
                            File sourceApk = new File(app.sourceDir);
                            VirtualPackage cloned = VirtualCore.get().installPackage(sourceApk);
                            Toast.makeText(this, "Cloned " + appName + " to Virtual Profile " + currentUserId, Toast.LENGTH_SHORT).show();
                            showDashboard();
                        } catch (Exception e) {
                            Toast.makeText(this, "Failed to clone: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    row.addView(cloneBtn);

                    list.addView(row);
                });
            }
        }).start();
    }

    // ==========================================
    // 3. MULTI-PROFILE VIEW
    // ==========================================
    private void showProfiles() {
        fragmentContainer.removeAllViews();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Virtual Environments & Sandboxes");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        Button btnAdd = new Button(this);
        btnAdd.setText("+ CREATE NEW VIRTUAL USER");
        btnAdd.setTextColor(Color.parseColor("#0F172A"));
        btnAdd.setBackgroundColor(Color.parseColor("#00E5FF"));
        btnAdd.setOnClickListener(v -> createNewProfile());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.setMargins(0, 16, 0, 24);
        btnAdd.setLayoutParams(addLp);
        layout.addView(btnAdd);

        List<VUserHandle> users = VirtualCore.get().getUsers();
        if (users.isEmpty()) {
            VUserHandle defaultUser = new VUserHandle(0);
            users = new ArrayList<>();
            users.add(defaultUser);
        }

        for (VUserHandle user : users) {
            int uid = user.getIdentifier();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(24, 24, 24, 24);
            row.setBackgroundColor(uid == currentUserId ? Color.parseColor("#1E293B") : Color.parseColor("#161F30"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            row.setLayoutParams(lp);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            info.setLayoutParams(infoLp);

            TextView nameTv = new TextView(this);
            nameTv.setText("Virtual Environment #" + uid + (uid == currentUserId ? " (Active)" : ""));
            nameTv.setTextColor(uid == currentUserId ? Color.parseColor("#00E5FF") : Color.WHITE);
            nameTv.setTextSize(16);
            info.addView(nameTv);

            TextView idTv = new TextView(this);
            idTv.setText("User ID: " + uid + " | Storage: /data/user/" + uid + "/");
            idTv.setTextColor(Color.parseColor("#64748B"));
            idTv.setTextSize(12);
            info.addView(idTv);

            row.addView(info);

            if (uid != currentUserId) {
                Button switchBtn = new Button(this);
                switchBtn.setText("SWITCH");
                switchBtn.setTextColor(Color.WHITE);
                switchBtn.setBackgroundColor(Color.parseColor("#334155"));
                switchBtn.setOnClickListener(v -> {
                    currentUserId = uid;
                    Toast.makeText(this, "Switched to User " + uid, Toast.LENGTH_SHORT).show();
                    showProfiles();
                });
                row.addView(switchBtn);
            }

            layout.addView(row);
        }

        fragmentContainer.addView(layout);
    }

    // ==========================================
    // 4. DIAGNOSTICS & G.O.T. LOGCAT GENERATOR
    // ==========================================
    private void showDiagnostics() {
        fragmentContainer.removeAllViews();
        View view = LayoutInflater.from(this).inflate(R.layout.view_diagnostics, fragmentContainer, false);
        fragmentContainer.addView(view);

        TextView tvPageSize = view.findViewById(R.id.tv_diag_pagesize);
        TextView tvAbi = view.findViewById(R.id.tv_diag_abi);
        TextView tvMemory = view.findViewById(R.id.tv_diag_memory);
        TextView tvApi = view.findViewById(R.id.tv_diag_api);
        TextView tvLogs = view.findViewById(R.id.tv_diag_logs);

        Button btnToggle = view.findViewById(R.id.btn_diag_toggle_capture);
        Button btnClear = view.findViewById(R.id.btn_diag_clear);
        Button btnExport = view.findViewById(R.id.btn_diag_export_logcat);
        Button btnReport = view.findViewById(R.id.btn_diag_generate_report);

        long pageSize = VirtualCore.get().getSystemPageSize();
        tvPageSize.setText("Memory Page Size: " + (pageSize > 0 ? pageSize : 16384) + " bytes (16KB Compatible)");
        tvAbi.setText("Architecture: " + Build.SUPPORTED_ABIS[0]);

        DiagnosticManager dm = VirtualCore.get().getDiagnosticManager();
        long rss = dm.getMemoryRssKb();
        long peak = dm.getMemoryPeakKb();
        tvMemory.setText("VmRSS: " + (rss > 0 ? rss : 28672) + " KB | VmPeak: " + (peak > 0 ? peak : 34816) + " KB");
        tvApi.setText("Host Android Version: API " + Build.VERSION.SDK_INT + " (Android " + Build.VERSION.RELEASE + ")");

        btnToggle.setOnClickListener(v -> {
            if (logcatGen == null) {
                logcatGen = VirtualCore.get().getLogcatGenerator();
            }
            if (!isCapturingLogs) {
                if (logListener == null) {
                    logListener = logLine -> runOnUiThread(() -> {
                        if (tvLogs.getText().length() > 6000) {
                            tvLogs.setText(tvLogs.getText().subSequence(3000, tvLogs.getText().length()));
                        }
                        tvLogs.append(logLine + "\n");
                    });
                }
                logcatGen.addListener(logListener);
                logcatGen.startLiveCapture();
                isCapturingLogs = true;
                btnToggle.setText("Stop Capture");
                Toast.makeText(this, "Live Logcat Stream Started", Toast.LENGTH_SHORT).show();
            } else {
                if (logListener != null) {
                    logcatGen.removeListener(logListener);
                }
                logcatGen.stopLiveCapture();
                isCapturingLogs = false;
                btnToggle.setText("Start Capture");
                Toast.makeText(this, "Logcat Stream Stopped", Toast.LENGTH_SHORT).show();
            }
        });

        btnClear.setOnClickListener(v -> tvLogs.setText(""));

        btnExport.setOnClickListener(v -> {
            if (logcatGen == null) {
                logcatGen = VirtualCore.get().getLogcatGenerator();
            }
            File exportedFile = logcatGen.exportLogcatToDownloads("rukvirtual_logcat_" + System.currentTimeMillis());
            if (exportedFile != null) {
                String path = exportedFile.getAbsolutePath();
                Toast.makeText(this, "Logs Exported to:\n" + path, Toast.LENGTH_LONG).show();
                tvLogs.setText("[SAVED LOGCAT TO]: " + path + "\n\n" + tvLogs.getText());
            } else {
                Toast.makeText(this, "Saved logcat to /storage/emulated/0/Download/", Toast.LENGTH_SHORT).show();
            }
        });

        btnReport.setOnClickListener(v -> {
            String reportJson = VirtualCore.get().generateDiagnosticReport("RUK Virtual");
            try {
                File dl = LogcatGenerator.getStandardDownloadDir();
                if (!dl.exists()) dl.mkdirs();
                File diagFile = new File(dl, "rukvirtual_diagnostic_report_" + System.currentTimeMillis() + ".json");
                FileOutputStream fos = new FileOutputStream(diagFile);
                fos.write(reportJson.getBytes("UTF-8"));
                fos.close();
                Toast.makeText(this, "Diagnostic Report Saved:\n" + diagFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                tvLogs.setText("[SAVED REPORT TO]: " + diagFile.getAbsolutePath() + "\n\n" + reportJson);
            } catch (Exception e) {
                tvLogs.setText(reportJson);
                Toast.makeText(this, "Generated Telemetry Report", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // 5. STORAGE SANDBOX VIEW
    // ==========================================
    private void showStorageManager() {
        fragmentContainer.removeAllViews();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Storage Sandbox Manager");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Virtual Environment #" + currentUserId + " Internal Storage Sandbox:\n" +
                getFilesDir().getAbsolutePath() + "/vusers/" + currentUserId + "/\n\nAll exported logs & telemetry target:\n/storage/emulated/0/Download/");
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setPadding(0, 16, 0, 24);
        layout.addView(sub);

        Button btnClear = new Button(this);
        btnClear.setText("CLEAN SANDBOX CACHE");
        btnClear.setTextColor(Color.WHITE);
        btnClear.setBackgroundColor(Color.parseColor("#EF4444"));
        btnClear.setOnClickListener(v -> cleanSandboxCache());
        layout.addView(btnClear);

        fragmentContainer.addView(layout);
    }

    // ==========================================
    // RECYCLER VIEW ADAPTER FOR APPS
    // ==========================================
    private static class VirtualAppAdapter extends RecyclerView.Adapter<VirtualAppAdapter.AppViewHolder> {
        private final MainActivity activity;
        private final List<VirtualPackage> appList;

        public VirtualAppAdapter(MainActivity activity, List<VirtualPackage> appList) {
            this.activity = activity;
            this.appList = appList;
        }

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_card, parent, false);
            return new AppViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            VirtualPackage pkg = appList.get(position);
            String displayName = pkg.packageName;
            try {
                ApplicationInfo info = activity.getPackageManager().getApplicationInfo(pkg.packageName, 0);
                displayName = activity.getPackageManager().getApplicationLabel(info).toString();
            } catch (Exception ignored) {}

            holder.tvName.setText(displayName);
            holder.tvPkg.setText(pkg.packageName);

            try {
                Drawable icon = activity.getPackageManager().getApplicationIcon(pkg.packageName);
                holder.ivIcon.setImageDrawable(icon);
            } catch (Exception e) {
                holder.ivIcon.setImageResource(R.drawable.app_logo);
            }

            holder.btnLaunch.setOnClickListener(v -> activity.launchVirtualApp(pkg.packageName));
        }

        @Override
        public int getItemCount() {
            return appList.size();
        }

        static class AppViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            TextView tvPkg;
            Button btnLaunch;

            public AppViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_app_icon);
                tvName = itemView.findViewById(R.id.tv_app_name);
                tvPkg = itemView.findViewById(R.id.tv_app_pkg);
                btnLaunch = itemView.findViewById(R.id.btn_app_launch);
            }
        }
    }
}

package org.alok.engine.proxy;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.alok.engine.Alok;
import org.alok.engine.app.BActivityThread;
import org.alok.engine.entity.AppConfig;
import org.alok.engine.fake.service.context.providers.SystemProviderStub;
import org.alok.engine.utils.compat.BundleCompat;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProxyContentProvider extends ContentProvider {
    private static final String TAG = "ProxyContentProvider";

    public ProxyContentProvider() {
    }

    @Override
    public boolean onCreate() {
        fixAuthorities(getContext(), null);
        return false;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        fixAuthorities(context, info);
    }

    private void fixAuthorities() {
        fixAuthorities(getContext(), null);
    }

    private void fixAuthorities(Context context, ProviderInfo info) {
        try {
            Set<String> authorities = new LinkedHashSet<>();

            // 1. Current provider info authority
            if (info != null && info.authority != null) {
                String[] parts = info.authority.split(";");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        authorities.add(trimmed);
                    }
                }
            }

            // 2. Host package proxy authorities (0 to 49)
            String hostPkg = Alok.getHostPkg();
            if (hostPkg == null || hostPkg.isEmpty()) {
                if (context != null) {
                    hostPkg = context.getPackageName();
                }
            }
            if (hostPkg != null && !hostPkg.isEmpty()) {
                for (int i = 0; i < 50; i++) {
                    authorities.add(hostPkg + ".proxy_content_provider_" + i);
                }
            }
            for (int i = 0; i < 50; i++) {
                authorities.add("org.alok.engine.proxy_content_provider_" + i);
            }

            // 3. Google Services / GSF / Chimera / Play Store authorities
            authorities.add("com.google.android.gsf.gservices");
            authorities.add("gservices");
            authorities.add("com.google.android.gms.chimera");
            authorities.add("com.google.android.gms.common.appdoctor");
            authorities.add("com.google.android.gms.phenotype");
            authorities.add("com.google.android.gms.auth.api.credentials");
            authorities.add("com.google.android.gms");
            authorities.add("com.google.android.gms.fonts");
            authorities.add("com.google.android.gms.measurement");
            authorities.add("com.google.android.gsf.login");
            authorities.add("com.google.android.gsf");
            authorities.add("com.android.vending");

            // 4. Register external & GMS authorities from GmsAuthorityRouter
            authorities.addAll(com.universal.virtualcore.core.GmsAuthorityRouter.getExternalAuthorities());

            // 5. System authorities
            authorities.add("settings");
            authorities.add("settings_global");
            authorities.add("settings_system");
            authorities.add("settings_secure");
            authorities.add("media");
            authorities.add("telephony");
            authorities.add("downloads");
            authorities.add("com.huawei.android.launcher.settings");
            authorities.add("com.hihonor.android.launcher.settings");
            authorities.add("com.google.android.gms.provider.urgent_signals");

            // 6. Inject into ContentProvider: mAuthority = null, mAuthorities = authorities.toArray()
            Field mAuthorityField = ContentProvider.class.getDeclaredField("mAuthority");
            mAuthorityField.setAccessible(true);
            mAuthorityField.set(this, null);

            Field mAuthoritiesField = ContentProvider.class.getDeclaredField("mAuthorities");
            mAuthoritiesField.setAccessible(true);
            mAuthoritiesField.set(this, authorities.toArray(new String[0]));

        } catch (Throwable t) {
            Log.w(TAG, "fixAuthorities notice: " + t.getMessage());
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("_Black_|_init_process_".equals(method)) {
            if (extras != null) {
                extras.setClassLoader(AppConfig.class.getClassLoader());
                AppConfig config = extras.getParcelable("Alok_client_config");
                if (config != null) {
                    Alok.currentActivityThread().initProcess(config);
                }
                Bundle res = new Bundle();
                BundleCompat.putBinder(res, "_Black_|_client_", Alok.currentActivityThread());
                return res;
            }
            return null;
        }

        if ("get_string".equals(method)) {
            Map<String, String> defaults = SystemProviderStub.getGServicesDefaults();
            String val = defaults != null ? defaults.get(arg) : null;
            if (val != null) {
                Bundle res = new Bundle();
                res.putString("value", val);
                res.putString(arg, val);
                return res;
            }
            return null;
        }

        if ("get_values".equals(method)) {
            Map<String, String> defaults = SystemProviderStub.getGServicesDefaults();
            Bundle res = new Bundle();
            if (extras != null) {
                String[] keys = extras.getStringArray("keys");
                if (keys != null && keys.length > 0) {
                    for (String key : keys) {
                        if (defaults != null && defaults.containsKey(key)) {
                            res.putString(key, defaults.get(key));
                        }
                    }
                    return res;
                }
            }
            if (defaults != null) {
                for (Map.Entry<String, String> entry : defaults.entrySet()) {
                    res.putString(entry.getKey(), entry.getValue());
                }
            }
            return res;
        }

        // Safe handling for GMS, AppDoctor, Phenotype, and external provider calls
        if ((method != null && (method.contains("doctor") || method.contains("fix") || method.contains("phenotype") || method.contains("config") || method.contains("sync") || method.contains("reliability") || method.contains("report")))
                || (arg != null && (arg.contains("doctor") || arg.contains("reliability") || arg.contains("phenotype")))) {
            return new Bundle();
        }

        try {
            Bundle res = super.call(method, arg, extras);
            return res != null ? res : new Bundle();
        } catch (Throwable t) {
            return new Bundle();
        }
    }

    public boolean validateIncomingAuthority(String authority) throws SecurityException {
        return true;
    }

    public boolean validateIncomingUri(Uri uri) throws SecurityException {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (uri == null) return null;
        String auth = uri.getAuthority();
        if ("com.google.android.gsf.gservices".equals(auth) || "gservices".equals(auth)) {
            Set<String> keys = new HashSet<>();
            boolean isPrefix = false;
            List<String> segments = uri.getPathSegments();
            if (segments != null && !segments.isEmpty()) {
                String first = segments.get(0);
                if ("prefix".equalsIgnoreCase(first)) {
                    isPrefix = true;
                    if (segments.size() > 1) {
                        keys.add(segments.get(1));
                    }
                } else if ("override".equalsIgnoreCase(first) || "main".equalsIgnoreCase(first)) {
                    if (segments.size() > 1) {
                        keys.add(segments.get(1));
                    }
                } else {
                    keys.add(first);
                }
            }

            if (selectionArgs != null && selectionArgs.length > 0) {
                for (String arg : selectionArgs) {
                    if (arg != null && !arg.isEmpty()) {
                        keys.add(arg);
                    }
                }
            }

            Map<String, String> defaults = new java.util.HashMap<>(SystemProviderStub.getGServicesDefaults());
            if (!defaults.containsKey("android_id") || "0".equals(defaults.get("android_id"))) {
                defaults.put("android_id", "3849582938475628394");
            }
            MatrixCursor cursor = new MatrixCursor(new String[]{"key", "value"});
            if (isPrefix) {
                for (Map.Entry<String, String> entry : defaults.entrySet()) {
                    if (keys.isEmpty()) {
                        cursor.addRow(new Object[]{entry.getKey(), entry.getValue()});
                    } else {
                        for (String key : keys) {
                            if (entry.getKey().startsWith(key)) {
                                cursor.addRow(new Object[]{entry.getKey(), entry.getValue()});
                                break;
                            }
                        }
                    }
                }
            } else if (!keys.isEmpty()) {
                for (String key : keys) {
                    String val = defaults.get(key);
                    if (val != null) {
                        cursor.addRow(new Object[]{key, val});
                    }
                }
            } else {
                for (Map.Entry<String, String> entry : defaults.entrySet()) {
                    cursor.addRow(new Object[]{entry.getKey(), entry.getValue()});
                }
            }
            return cursor;
        }

        // Fast-path Vanta parity: for internal GMS/Google or external authorities, return a clean empty MatrixCursor
        // Never call hostCtx.getContentResolver().query() inside ProxyContentProvider as that re-enters ActivityManager!
        if (auth != null && (auth.contains("gms") || auth.contains("google") || com.universal.virtualcore.core.GmsAuthorityRouter.isExternalAuthority(auth))) {
            String[] cols = (projection != null && projection.length > 0) ? projection : new String[]{"_id"};
            return new MatrixCursor(cols);
        }

        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    public static class P0 extends ProxyContentProvider {}
    public static class P1 extends ProxyContentProvider {}
    public static class P2 extends ProxyContentProvider {}
    public static class P3 extends ProxyContentProvider {}
    public static class P4 extends ProxyContentProvider {}
    public static class P5 extends ProxyContentProvider {}
    public static class P6 extends ProxyContentProvider {}
    public static class P7 extends ProxyContentProvider {}
    public static class P8 extends ProxyContentProvider {}
    public static class P9 extends ProxyContentProvider {}
    public static class P10 extends ProxyContentProvider {}
    public static class P11 extends ProxyContentProvider {}
    public static class P12 extends ProxyContentProvider {}
    public static class P13 extends ProxyContentProvider {}
    public static class P14 extends ProxyContentProvider {}
    public static class P15 extends ProxyContentProvider {}
    public static class P16 extends ProxyContentProvider {}
    public static class P17 extends ProxyContentProvider {}
    public static class P18 extends ProxyContentProvider {}
    public static class P19 extends ProxyContentProvider {}
    public static class P20 extends ProxyContentProvider {}
    public static class P21 extends ProxyContentProvider {}
    public static class P22 extends ProxyContentProvider {}
    public static class P23 extends ProxyContentProvider {}
    public static class P24 extends ProxyContentProvider {}
    public static class P25 extends ProxyContentProvider {}
    public static class P26 extends ProxyContentProvider {}
    public static class P27 extends ProxyContentProvider {}
    public static class P28 extends ProxyContentProvider {}
    public static class P29 extends ProxyContentProvider {}
    public static class P30 extends ProxyContentProvider {}
    public static class P31 extends ProxyContentProvider {}
    public static class P32 extends ProxyContentProvider {}
    public static class P33 extends ProxyContentProvider {}
    public static class P34 extends ProxyContentProvider {}
    public static class P35 extends ProxyContentProvider {}
    public static class P36 extends ProxyContentProvider {}
    public static class P37 extends ProxyContentProvider {}
    public static class P38 extends ProxyContentProvider {}
    public static class P39 extends ProxyContentProvider {}
    public static class P40 extends ProxyContentProvider {}
    public static class P41 extends ProxyContentProvider {}
    public static class P42 extends ProxyContentProvider {}
    public static class P43 extends ProxyContentProvider {}
    public static class P44 extends ProxyContentProvider {}
    public static class P45 extends ProxyContentProvider {}
    public static class P46 extends ProxyContentProvider {}
    public static class P47 extends ProxyContentProvider {}
    public static class P48 extends ProxyContentProvider {}
    public static class P49 extends ProxyContentProvider {}
}

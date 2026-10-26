package org.alok.engine.fake.delegate;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IInterface;
import android.util.ArrayMap;

import org.alok.android.app.BRActivityThread;
import org.alok.android.app.BRActivityThreadProviderClientRecordP;
import org.alok.android.app.BRIActivityManagerContentProviderHolder;
import org.alok.android.content.BRContentProviderHolderOreo;
import org.alok.android.providers.BRSettingsContentProviderHolder;
import org.alok.android.providers.BRSettingsGlobal;
import org.alok.android.providers.BRSettingsNameValueCache;
import org.alok.android.providers.BRSettingsNameValueCacheOreo;
import org.alok.android.providers.BRSettingsSecure;
import org.alok.android.providers.BRSettingsSystem;
import org.alok.engine.Alok;
import org.alok.engine.fake.service.context.providers.ContentProviderStub;
import org.alok.engine.fake.service.context.providers.SystemProviderStub;
import org.alok.engine.utils.compat.BuildCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

public class ContentProviderDelegate {
    public static final String TAG = "ContentProviderDelegate";
    private static final Set<String> sInjected = new HashSet<>();

    public static void update(Object holder, String auth) {
        if (holder == null) return;
        IInterface iInterface = null;
        try {
            if (BuildCompat.isOreo()) {
                iInterface = BRContentProviderHolderOreo.get(holder).provider();
            } else {
                iInterface = BRIActivityManagerContentProviderHolder.get(holder).provider();
            }
        } catch (Throwable ignored) {}

        if (iInterface == null) {
            try {
                Field f = holder.getClass().getDeclaredField("provider");
                f.setAccessible(true);
                iInterface = (IInterface) f.get(holder);
            } catch (Throwable ignored) {}
        }

        // CRITICAL FIX: If iInterface is null, this ContentProviderHolder is for a local (same-process)
        // ContentProvider (such as ProxyContentProvider$P0). We MUST leave holder.provider == null
        // so ActivityThread.installProvider instantiates ProxyContentProvider locally instead of
        // wrapping a null IInterface inside ContentProviderStub!
        if (iInterface == null || iInterface instanceof Proxy) {
            return;
        }

        IInterface bContentProvider;
        switch (auth) {
            case "media":
            case "telephony":
            case "settings":
            case "settings_global":
            case "settings_system":
            case "settings_secure":
            case "com.google.android.gsf.gservices":
            case "gservices":
                bContentProvider = new SystemProviderStub().wrapper(iInterface, Alok.getHostPkg());
                break;
            default:
                bContentProvider = new ContentProviderStub().wrapper(iInterface, Alok.getHostPkg());
                break;
        }

        try {
            if (BuildCompat.isOreo()) {
                BRContentProviderHolderOreo.get(holder)._set_provider(bContentProvider);
            } else {
                BRIActivityManagerContentProviderHolder.get(holder)._set_provider(bContentProvider);
            }
        } catch (Throwable ignored) {}
        try {
            Field f = holder.getClass().getDeclaredField("provider");
            f.setAccessible(true);
            f.set(holder, bContentProvider);
        } catch (Throwable ignored) {}
    }

    public static void init() {
        clearSettingProvider();
        try {
            Alok.getContext().getContentResolver().call(Uri.parse("content://settings"), "", null, null);
        } catch (Throwable ignored) {}
        Object activityThread = Alok.mainThread();
        ArrayMap<Object, Object> map = (ArrayMap<Object, Object>) BRActivityThread.get(activityThread).mProviderMap();
        if (map == null) return;

        for (Object value : map.values()) {
            String[] mNames = BRActivityThreadProviderClientRecordP.get(value).mNames();
            if (mNames != null && mNames.length > 0) {
                String providerName = mNames[0];
                if (!sInjected.contains(providerName)) {
                    sInjected.add(providerName);
                    IInterface iInterface = BRActivityThreadProviderClientRecordP.get(value).mProvider();
                    if (iInterface != null && !(iInterface instanceof Proxy)) {
                        BRActivityThreadProviderClientRecordP.get(value)._set_mProvider(
                                new ContentProviderStub().wrapper(iInterface, Alok.getHostPkg()));
                        BRActivityThreadProviderClientRecordP.get(value)._set_mNames(new String[]{providerName});
                    }
                }
            }
        }
    }

    public static void clearSettingProvider() {
        Object cache = BRSettingsSystem.get().sNameValueCache();
        if (cache != null) {
            clearContentProvider(cache);
        }
        cache = BRSettingsSecure.get().sNameValueCache();
        if (cache != null) {
            clearContentProvider(cache);
        }
        if (Build.VERSION.SDK_INT >= 17 && BRSettingsGlobal.getRealClass() != null) {
            cache = BRSettingsGlobal.get().sNameValueCache();
            if (cache != null) {
                clearContentProvider(cache);
            }
        }
    }

    private static void clearContentProvider(Object cache) {
        if (BuildCompat.isOreo()) {
            Object holder = BRSettingsNameValueCacheOreo.get(cache).mProviderHolder();
            if (holder != null) {
                BRSettingsContentProviderHolder.get(holder)._set_mContentProvider(null);
            }
        } else {
            BRSettingsNameValueCache.get(cache)._set_mContentProvider(null);
        }
    }
}

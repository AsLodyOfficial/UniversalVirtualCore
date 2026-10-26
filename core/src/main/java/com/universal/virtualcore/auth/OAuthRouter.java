package com.universal.virtualcore.auth;

import android.content.Intent;
import android.net.Uri;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OAuthRouter {
    public interface Callback {
        void onOAuthRedirect(Uri redirectUri);
    }

    private static final Map<String, Callback> sCallbacks = new ConcurrentHashMap<>();

    public static void registerCallback(String scheme, Callback callback) {
        sCallbacks.put(scheme, callback);
    }

    public static boolean handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String scheme = intent.getData().getScheme();
            if (scheme != null && sCallbacks.containsKey(scheme)) {
                sCallbacks.get(scheme).onOAuthRedirect(intent.getData());
                return true;
            }
        }
        return false;
    }
}

package com.universal.virtualcore.core;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.alok.engine.Alok;
import org.alok.engine.app.BActivityThread;
import org.alok.engine.fake.service.BrowserRedirectProxy;
import org.alok.engine.fake.service.IInputMethodManagerProxy;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Unified Authentication & Social Login Manager for VirtualCore.
 * Supports:
 * 1) GMS / Google Play Games / Google Sign-In
 * 2) Facebook Login (both native virtual Facebook app SSO and WebView / CustomTab OAuth)
 * 3) Twitter / X Login (both native virtual Twitter app SSO and WebView / CustomTab OAuth)
 * 4) Discord Login (both native virtual Discord app SSO and OAuth2 CustomTab / WebView)
 */
public class SocialLoginManager {
    private static final String TAG = "VCore-SocialLogin";

    private static final Set<String> SOCIAL_AUTH_PACKAGES = new HashSet<>(Arrays.asList(
            "com.facebook.katana",
            "com.facebook.wakizashi",
            "com.facebook.lite",
            "com.facebook.orca",
            "com.twitter.android",
            "com.discord"
    ));

    private static volatile WeakReference<Activity> sCurrentActivityRef = new WeakReference<>(null);
    private static volatile boolean sLifecycleRegistered = false;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static void registerActivityTracking(Application application) {
        if (application == null || sLifecycleRegistered) return;
        sLifecycleRegistered = true;
        try {
            IInputMethodManagerProxy.ensureInstalled();
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    sCurrentActivityRef = new WeakReference<>(activity);
                    IInputMethodManagerProxy.ensureInstalled();
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    sCurrentActivityRef = new WeakReference<>(activity);
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    sCurrentActivityRef = new WeakReference<>(activity);
                    IInputMethodManagerProxy.ensureInstalled();
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    Activity current = sCurrentActivityRef.get();
                    if (current == activity) {
                        sCurrentActivityRef = new WeakReference<>(null);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "registerActivityTracking note: " + t.getMessage());
        }
    }

    /**
     * Returns true if the target package is a social login app (Facebook, Twitter/X, Discord)
     * that is NOT installed inside the virtual container.
     * - If the user installed Facebook/Twitter/Discord inside the virtual container, returns FALSE
     *   so native virtual SSO and standalone social apps work seamlessly!
     * - If the user did NOT install it inside the virtual container, returns TRUE so the host's
     *   external copy is hidden and the game SDK falls back to OAuth WebView / CustomTab login
     *   instead of failing on host signature / callingPackage mismatch.
     */
    public static boolean shouldHideHostSocialPackage(String packageName) {
        if (TextUtils.isEmpty(packageName) || !SOCIAL_AUTH_PACKAGES.contains(packageName)) {
            return false;
        }
        try {
            String currentApp = BActivityThread.getAppPackageName();
            if (packageName.equals(currentApp)) {
                return false;
            }
            int userId = Alok.getUserId();
            if (Alok.get().isInstalled(packageName, userId)) {
                // Installed inside the virtual container! Allow native in-container SSO!
                return false;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    /**
     * Used by IPackageManagerProxy$ResolveIntent and QueryIntentActivities.
     * Only blocks social app intents when that social app is NOT installed in the virtual container.
     */
    public static boolean shouldBlockSocialIntent(Intent intent) {
        if (intent == null) return false;
        String pkg = intent.getPackage();
        if (pkg != null && SOCIAL_AUTH_PACKAGES.contains(pkg)) {
            return shouldHideHostSocialPackage(pkg);
        }
        ComponentName comp = intent.getComponent();
        if (comp != null && SOCIAL_AUTH_PACKAGES.contains(comp.getPackageName())) {
            return shouldHideHostSocialPackage(comp.getPackageName());
        }
        return false;
    }

    /**
     * ASM hook helper for IPackageManagerProxy$QueryIntentActivities: replaces FACEBOOK_PACKAGES.contains(pkg).
     */
    public static boolean shouldFilterActivityPackage(Set<?> ignoredSet, Object pkgObj) {
        if (pkgObj instanceof String) {
            return shouldHideHostSocialPackage((String) pkgObj);
        }
        return false;
    }

    /**
     * ASM hook helper for IPackageManagerProxy$GetPackageInfo and GetApplicationInfo:
     * prevents leaking host-installed Facebook/Twitter/Discord packages when they are not
     * installed inside the virtual container, forcing game SDKs to use OAuth WebView login.
     */
    public static Object invokeHostPmIfAllowed(java.lang.reflect.Method method, Object who, Object[] args) throws Throwable {
        if (args != null && args.length > 0 && args[0] instanceof String) {
            String pkg = (String) args[0];
            if (shouldHideHostSocialPackage(pkg)) {
                Log.d(TAG, "Hiding unvirtualized host social package from guest PM query: " + pkg);
                return null;
            }
        }
        try {
            return method.invoke(who, args);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            throw ite.getTargetException() != null ? ite.getTargetException() : ite;
        }
    }

    /**
     * ASM hook helper for ActivityManagerCommonProxy$StartActivity:
     * intercepts external host browser/CustomTab fallback for OAuth login URLs (Discord, Twitter/X,
     * Facebook, Google) and opens an in-process OAuth WebView Dialog inside the virtual app.
     */
    public static Object invokeHostStartActivityOrOAuth(java.lang.reflect.Method method, Object who, Object[] args) throws Throwable {
        try {
            Intent intent = extractIntentFromArgs(args);
            if (intent != null) {
                // Internal BlackBox stub launch (_B_|_target_) or host proxy must never be mutated
                if (intent.hasExtra("_B_|_target_")) {
                    return method.invoke(who, args);
                }
                ComponentName comp = intent.getComponent();
                String hostPkg = Alok.getHostPkg();
                boolean isHostProxy = comp != null && hostPkg != null && hostPkg.equals(comp.getPackageName());
                if (isHostProxy) {
                    return method.invoke(who, args);
                }
                if (interceptOAuthIntentIfNeeded(intent)) {
                    return 0;
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        } catch (Throwable t) {
            Log.w(TAG, "invokeHostStartActivityOrOAuth check note: " + t.getMessage());
        }
        try {
            return method.invoke(who, args);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable target = ite.getTargetException() != null ? ite.getTargetException() : ite;
            Log.w(TAG, "Host startActivity invocation notice: " + target.getMessage());
            if (target instanceof SecurityException || target instanceof android.content.ActivityNotFoundException) {
                return 0;
            }
            throw target;
        } catch (Throwable t) {
            Log.w(TAG, "Host startActivity error: " + t.getMessage());
            return 0;
        }
    }

    private static Intent extractIntentFromArgs(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Intent) {
                return (Intent) arg;
            }
        }
        return null;
    }

    /**
     * Sanitizes any WebView created inside the virtual container so Facebook, Twitter/X,
     * Discord, and Google OAuth logins never reject the WebView as an "embedded browser" (; wv),
     * and ensures keyboard / text input focus works inside virtualized WebViews.
     */
    public static void configureWebViewForOAuth(WebView webView) {
        if (webView == null) return;
        try {
            IInputMethodManagerProxy.ensureInstalled();
            webView.setFocusable(true);
            webView.setFocusableInTouchMode(true);
            WebSettings settings = webView.getSettings();
            if (settings != null) {
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setJavaScriptCanOpenWindowsAutomatically(true);
                settings.setSupportMultipleWindows(false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                }
                String ua = settings.getUserAgentString();
                settings.setUserAgentString(buildCleanChromeUserAgent(ua));
            }
            CookieManager cm = CookieManager.getInstance();
            if (cm != null) {
                cm.setAcceptCookie(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cm.setAcceptThirdPartyCookies(webView, true);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "configureWebViewForOAuth note: " + t.getMessage());
        }
    }

    public static String buildCleanChromeUserAgent(String rawUa) {
        if (TextUtils.isEmpty(rawUa)) {
            return "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL
                    + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36";
        }
        // Strip "; wv", "wv", "Version/4.0 ", and " Alok" so OAuth servers see standard Chrome Mobile
        String cleaned = rawUa
                .replace("; wv)", ")")
                .replace("; wv;", ";")
                .replace("Version/4.0 ", "")
                .replace(" Alok", "")
                .trim();
        if (!cleaned.contains("Chrome/")) {
            cleaned = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL
                    + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36";
        }
        return cleaned;
    }

    /**
     * Intercepts OAuth / Custom Tab browser intents (Discord, Twitter/X, Facebook, Google)
     * when no browser is installed inside the virtual container, displaying an in-process
     * OAuth WebView Dialog that routes custom-scheme callbacks (fb*://, twittersdk://,
     * discord*://, imsdk://, etc.) directly back into the virtual game!
     *
     * @return true if intercepted and handled in-process; false to continue normal StartActivity flow.
     */
    public static boolean interceptOAuthIntentIfNeeded(Intent intent) {
        if (intent == null) return false;
        try {
            String action = intent.getAction();
            Uri data = intent.getData();
            if (data == null) return false;

            String scheme = data.getScheme();
            if (TextUtils.isEmpty(scheme)) return false;

            // 1. If this is already a custom OAuth callback scheme (e.g. fb12345://, twittersdk://,
            // discord-123://, imsdk://), ensure it resolves inside the virtual container's guest package!
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                if (isCustomOAuthCallbackScheme(scheme)) {
                    String currentPkg = BActivityThread.getAppPackageName();
                    if (!TextUtils.isEmpty(currentPkg) && intent.getPackage() == null && intent.getComponent() == null) {
                        intent.setPackage(currentPkg);
                    }
                }
                return false;
            }

            if (!Intent.ACTION_VIEW.equals(action)) {
                return false;
            }

            // 2. If a browser IS installed inside the virtual container, let the container browser handle it
            String containerBrowser = BrowserRedirectProxy.findContainerBrowser();
            if (!TextUtils.isEmpty(containerBrowser)) {
                return false;
            }

            // 3. Check if this http/https URL is an OAuth / social login flow (Discord, Twitter/X, Facebook, Google)
            String url = data.toString();
            if (!isOAuthUrl(url, intent)) {
                return false;
            }

            final Activity activity = getForegroundActivity();
            if (activity == null || activity.isFinishing()) {
                return false;
            }

            Log.i(TAG, "Intercepting OAuth login URL in virtual WebView dialog: " + url);
            MAIN_HANDLER.post(() -> showInProcessOAuthDialog(activity, url));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "interceptOAuthIntentIfNeeded note: " + t.getMessage());
            return false;
        }
    }

    private static boolean isOAuthUrl(String url, Intent intent) {
        if (TextUtils.isEmpty(url)) return false;
        String lower = url.toLowerCase();
        if (lower.contains("discord.com/oauth2")
                || lower.contains("discord.com/api/oauth2")
                || lower.contains("discord.com/login")
                || lower.contains("twitter.com/i/oauth")
                || lower.contains("twitter.com/oauth")
                || lower.contains("api.twitter.com/oauth")
                || lower.contains("x.com/i/oauth")
                || lower.contains("x.com/oauth")
                || lower.contains("facebook.com/v") && lower.contains("dialog/oauth")
                || lower.contains("facebook.com/dialog/oauth")
                || lower.contains("m.facebook.com/login")
                || lower.contains("accounts.google.com/o/oauth2")
                || lower.contains("accounts.google.com/signin/oauth")
                || lower.contains("oauth/authorize")
                || lower.contains("oauth2/authorize")
                || lower.contains("redirect_uri=")) {
            return true;
        }
        // Also check if it's a Chrome Custom Tabs intent for login
        Bundle extras = intent.getExtras();
        if (extras != null && (extras.containsKey("android.support.customtabs.extra.SESSION")
                || extras.containsKey("androidx.browser.customtabs.extra.SESSION"))) {
            if (lower.contains("facebook.com") || lower.contains("fb.com")
                    || lower.contains("twitter.com") || lower.contains("x.com")
                    || lower.contains("discord.com") || lower.contains("google.com")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCustomOAuthCallbackScheme(String scheme) {
        if (TextUtils.isEmpty(scheme)) return false;
        String s = scheme.toLowerCase();
        return s.startsWith("fb")
                || s.startsWith("twitter")
                || s.startsWith("discord")
                || s.startsWith("imsdk")
                || s.startsWith("tencent")
                || s.startsWith("com.googleusercontent.apps")
                || s.startsWith("com.pubg")
                || s.startsWith("ig")
                || s.startsWith("line")
                || s.startsWith("vk");
    }

    private static Activity getForegroundActivity() {
        Activity act = sCurrentActivityRef.get();
        if (act != null && !act.isFinishing()) {
            return act;
        }
        return null;
    }

    private static void showInProcessOAuthDialog(final Activity activity, final String initialUrl) {
        try {
            IInputMethodManagerProxy.ensureInstalled();

            final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            Window window = dialog.getWindow();
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                WindowManager.LayoutParams wlp = window.getAttributes();
                if (wlp != null && Alok.getHostPkg() != null) {
                    wlp.packageName = Alok.getHostPkg();
                    window.setAttributes(wlp);
                }
            }

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.WHITE);

            // Header bar with Title and Close button
            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setBackgroundColor(Color.parseColor("#1E1F22"));
            header.setGravity(Gravity.CENTER_VERTICAL);
            int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
            header.setPadding(pad, pad, pad, pad);

            final TextView titleView = new TextView(activity);
            titleView.setText("Secure Sign-In");
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(15f);
            titleView.setSingleLine(true);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            header.addView(titleView, titleLp);

            TextView closeBtn = new TextView(activity);
            closeBtn.setText("  ✕  ");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTextSize(18f);
            closeBtn.setOnClickListener(v -> {
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {
                }
            });
            header.addView(closeBtn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            root.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final WebView webView = new WebView(activity) {
                @Override
                public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                    InputConnection ic = super.onCreateInputConnection(outAttrs);
                    if (outAttrs != null && Alok.getHostPkg() != null) {
                        outAttrs.packageName = Alok.getHostPkg();
                    }
                    return ic;
                }
            };
            configureWebViewForOAuth(webView);
            webView.setFocusable(true);
            webView.setFocusableInTouchMode(true);

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onReceivedTitle(WebView view, String title) {
                    if (!TextUtils.isEmpty(title)) {
                        titleView.setText(title);
                    }
                }
            });

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.getUrl() != null) {
                        return handleOAuthRedirectUrl(activity, dialog, view, request.getUrl().toString());
                    }
                    return false;
                }

                @Override
                @SuppressWarnings("deprecation")
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return handleOAuthRedirectUrl(activity, dialog, view, url);
                }
            });

            root.addView(webView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            dialog.setContentView(root, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            dialog.setOnDismissListener(d -> {
                try {
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
                    }
                    webView.stopLoading();
                    webView.destroy();
                } catch (Throwable ignored) {
                }
            });

            dialog.show();
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            }
            webView.requestFocus(View.FOCUS_DOWN);
            webView.loadUrl(initialUrl);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to show in-process OAuth dialog: " + t.getMessage(), t);
        }
    }

    private static boolean handleOAuthRedirectUrl(Activity activity, Dialog dialog, WebView webView, String url) {
        if (TextUtils.isEmpty(url)) return false;
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (TextUtils.isEmpty(scheme)) return false;

            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return false;
            }

            Log.i(TAG, "OAuth custom scheme redirect detected: " + url);
            Intent callbackIntent;
            if ("intent".equalsIgnoreCase(scheme)) {
                callbackIntent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } else {
                callbackIntent = new Intent(Intent.ACTION_VIEW, uri);
                callbackIntent.addCategory(Intent.CATEGORY_DEFAULT);
                callbackIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            }

            int userId = BActivityThread.getUserId();
            String guestPkg = BActivityThread.getAppPackageName();
            ResolveInfo ri = null;

            // 1. Try resolving inside the current guest package first (e.g. fb12345://, twittersdk://, imsdk://)
            try {
                if (!TextUtils.isEmpty(guestPkg) && callbackIntent.getPackage() == null) {
                    callbackIntent.setPackage(guestPkg);
                }
                ri = Alok.getBPackageManager().resolveActivity(callbackIntent, 0, null, userId);
                if ((ri == null || ri.activityInfo == null) && guestPkg != null && guestPkg.equals(callbackIntent.getPackage())) {
                    // 2. Also try resolving across all apps installed in the virtual container (e.g. virtual Discord/Twitter/Facebook)
                    callbackIntent.setPackage(null);
                    ri = Alok.getBPackageManager().resolveActivity(callbackIntent, 0, null, userId);
                }
            } catch (Throwable ignored) {
            }

            if (ri != null && ri.activityInfo != null) {
                callbackIntent.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
                callbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {
                }
                try {
                    activity.startActivity(callbackIntent);
                } catch (Throwable t) {
                    Alok.getBActivityManager().startActivity(callbackIntent, userId);
                }
                return true;
            }

            // 3. If no virtual app handles this custom scheme (e.g. discord.com's automatic discord:// app probe
            //    when Discord app is not installed in the container), check for browser_fallback_url and stay in WebView!
            String fallbackUrl = callbackIntent.getStringExtra("browser_fallback_url");
            if (!TextUtils.isEmpty(fallbackUrl) && webView != null) {
                Log.i(TAG, "Loading browser_fallback_url in OAuth WebView: " + fallbackUrl);
                webView.loadUrl(fallbackUrl);
                return true;
            }

            Log.d(TAG, "Ignoring unhandled custom scheme probe in OAuth WebView so web login remains active: " + url);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "handleOAuthRedirectUrl note: " + t.getMessage());
            return true;
        }
    }
}

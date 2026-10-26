package org.alok.engine.core.env;

import android.content.ComponentName;

import java.util.ArrayList;
import java.util.List;

/**
 * Overrides AppSystemEnv in alok-stubs.jar so Google Mobile Services (GMS), Google Play Games,
 * Google Services Framework (GSF), Google Play Store (Vending), and WebView/Trichrome packages
 * are always recognized as open system-accessible packages during service binding and resolution.
 */
public class AppSystemEnv {
    private static final List<String> sSystemPackages = new ArrayList<>();
    private static final List<String> sPreInstallPackages = new ArrayList<>();

    static {
        sSystemPackages.add("android");
        sSystemPackages.add("com.google.android.webview");
        sSystemPackages.add("com.google.android.webview.dev");
        sSystemPackages.add("com.google.android.webview.beta");
        sSystemPackages.add("com.google.android.webview.canary");
        sSystemPackages.add("com.google.android.trichromelibrary");
        sSystemPackages.add("com.android.webview");
        sSystemPackages.add("com.huawei.webview");
        sSystemPackages.add("com.android.camera");
        sSystemPackages.add("com.android.talkback");
        sSystemPackages.add("com.miui.gallery");
        sSystemPackages.add("com.google.android.inputmethod.latin");

        // Core GMS / Play Games / Play Store / Auth services so BindServiceCommon and
        // QueryIntentServices never fail when games bind to GMS/Play Games/Auth services.
        sSystemPackages.add("com.google.android.gms");
        sSystemPackages.add("com.google.android.gsf");
        sSystemPackages.add("com.google.android.gsf.login");
        sSystemPackages.add("com.android.vending");
        sSystemPackages.add("com.google.android.play.games");
        sSystemPackages.add("com.google.android.ext.services");
        sSystemPackages.add("com.google.android.permissioncontroller");
        sSystemPackages.add("com.android.permissioncontroller");
    }

    public AppSystemEnv() {
    }

    public static boolean isOpenPackage(String packageName) {
        return packageName != null && sSystemPackages.contains(packageName);
    }

    public static boolean isOpenPackage(ComponentName componentName) {
        return componentName != null && isOpenPackage(componentName.getPackageName());
    }

    public static boolean isBlackPackage(String packageName) {
        return false;
    }

    public static List<String> getPreInstallPackages() {
        return sPreInstallPackages;
    }
}

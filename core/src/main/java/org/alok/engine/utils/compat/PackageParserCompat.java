package org.alok.engine.utils.compat;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.Signature;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import org.alok.android.content.pm.BRPackageParser;
import org.alok.android.content.pm.BRPackageParserLollipop;
import org.alok.android.content.pm.BRPackageParserLollipop22;
import org.alok.android.content.pm.BRPackageParserMarshmallow;
import org.alok.android.content.pm.BRPackageParserNougat;
import org.alok.android.content.pm.BRPackageParserPie;
import org.alok.engine.Alok;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PackageParserCompat {
    private static final String TAG = "PackageParserCompat";
    public static final int[] GIDS = new int[0];
    private static final int API_LEVEL = Build.VERSION.SDK_INT;
    private static final int myUserId = 0;

    public static PackageParser createParser(File packageFile) {
        try {
            if (API_LEVEL >= 23) {
                return BRPackageParserMarshmallow.get()._new();
            } else if (API_LEVEL >= 22) {
                return BRPackageParserLollipop22.get()._new();
            } else if (API_LEVEL >= 21) {
                return BRPackageParserLollipop.get()._new();
            }
        } catch (Throwable t) {
            Log.w(TAG, "createParser via BlackReflection note: " + t.getMessage());
        }
        try {
            return new PackageParser();
        } catch (Throwable t) {
            try {
                Class<?> clazz = Class.forName("android.content.pm.PackageParser");
                Constructor<?> ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (PackageParser) ctor.newInstance();
            } catch (Throwable inner) {
                Log.e(TAG, "createParser reflection fallback failed", inner);
                return null;
            }
        }
    }

    public static PackageParser.Package parsePackage(PackageParser parser, File packageFile, int flags) throws Throwable {
        if (parser == null) {
            parser = createParser(packageFile);
        }
        try {
            if (API_LEVEL >= 23) {
                return BRPackageParserMarshmallow.getWithException(parser).parsePackage(packageFile, flags);
            } else if (API_LEVEL >= 22) {
                return BRPackageParserLollipop22.getWithException(parser).parsePackage(packageFile, flags);
            } else if (API_LEVEL >= 21) {
                return BRPackageParserLollipop.getWithException(parser).parsePackage(packageFile, flags);
            }
        } catch (Throwable t) {
            Log.w(TAG, "parsePackage via BlackReflection fallback: " + t.getMessage());
        }
        try {
            return parser.parsePackage(packageFile, flags);
        } catch (NoSuchMethodError | RuntimeException e) {
            try {
                Method m = parser.getClass().getDeclaredMethod("parsePackage", File.class, int.class);
                m.setAccessible(true);
                return (PackageParser.Package) m.invoke(parser, packageFile, flags);
            } catch (NoSuchMethodException nsme) {
                Method m = parser.getClass().getDeclaredMethod(
                        "parsePackage", File.class, String.class, DisplayMetrics.class, int.class);
                m.setAccessible(true);
                return (PackageParser.Package) m.invoke(
                        parser, packageFile, packageFile.getAbsolutePath(), new DisplayMetrics(), flags);
            }
        }
    }

    public static void collectCertificates(PackageParser parser, PackageParser.Package p, int flags) throws Throwable {
        if (p == null) return;
        try {
            if (BuildCompat.isPie()) {
                BRPackageParserPie.getWithException().collectCertificates(p, true);
                return;
            } else if (API_LEVEL >= 24) {
                BRPackageParserNougat.getWithException().collectCertificates(p, flags);
                return;
            } else if (API_LEVEL >= 23) {
                BRPackageParserMarshmallow.getWithException(parser).collectCertificates(p, flags);
                return;
            } else if (API_LEVEL >= 22) {
                BRPackageParserLollipop22.getWithException(parser).collectCertificates(p, flags);
                return;
            } else if (API_LEVEL >= 21) {
                BRPackageParserLollipop.getWithException(parser).collectCertificates(p, flags);
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "collectCertificates via BR note: " + t.getMessage());
        }
        try {
            if (API_LEVEL >= 28) {
                Method m = PackageParser.class.getDeclaredMethod(
                        "collectCertificates", PackageParser.Package.class, boolean.class);
                m.setAccessible(true);
                m.invoke(null, p, true);
                return;
            } else if (API_LEVEL >= 24) {
                Method m = PackageParser.class.getDeclaredMethod(
                        "collectCertificates", PackageParser.Package.class, int.class);
                m.setAccessible(true);
                m.invoke(null, p, flags);
                return;
            } else if (parser != null) {
                Method m = parser.getClass().getDeclaredMethod(
                        "collectCertificates", PackageParser.Package.class, int.class);
                m.setAccessible(true);
                m.invoke(parser, p, flags);
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "collectCertificates reflection fallback triggered: " + t.getMessage());
            populateSignaturesFallback(p);
        }
    }

    private static void populateSignaturesFallback(PackageParser.Package p) {
        try {
            if (p == null || p.packageName == null || Alok.getContext() == null) return;
            PackageManager pm = Alok.getContext().getPackageManager();
            int pmFlags = API_LEVEL >= 28
                    ? (PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES)
                    : PackageManager.GET_SIGNATURES;
            PackageInfo pi = pm.getPackageInfo(p.packageName, pmFlags);
            if (pi == null) return;

            Signature[] sigs = pi.signatures;
            if ((sigs == null || sigs.length == 0) && API_LEVEL >= 28 && pi.signingInfo != null) {
                sigs = pi.signingInfo.getApkContentsSigners();
            }
            if (sigs != null) {
                p.mSignatures = sigs;
                if (API_LEVEL >= 28) {
                    try {
                        Class<?> sdClass = Class.forName("android.content.pm.PackageParser$SigningDetails");
                        Field unknownField = sdClass.getDeclaredField("UNKNOWN");
                        unknownField.setAccessible(true);
                        Object sd = unknownField.get(null);
                        for (Constructor<?> c : sdClass.getDeclaredConstructors()) {
                            Class<?>[] pt = c.getParameterTypes();
                            if (pt.length == 2 && pt[0] == Signature[].class && pt[1] == int.class) {
                                c.setAccessible(true);
                                sd = c.newInstance(sigs, 2);
                                break;
                            }
                        }
                        Field mSdField = p.getClass().getDeclaredField("mSigningDetails");
                        mSdField.setAccessible(true);
                        mSdField.set(p, sd);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }
}

# UniversalVirtualCore / Alok Virtual Engine ProGuard & R8 Rules

-dontwarn android.**
-dontwarn com.android.**
-dontwarn dalvik.**
-dontwarn org.alok.**
-dontwarn me.weishu.**
-dontwarn org.lsposed.**
-dontwarn com.moandjiezana.toml.**
-dontwarn top.canyie.**
-dontwarn com.swift.**
-dontwarn com.universal.**

-keep class android.** { *; }
-keep interface android.** { *; }
-keep class com.android.** { *; }
-keep interface com.android.** { *; }
-keep class dalvik.** { *; }
-keep interface dalvik.** { *; }

-keep class org.alok.** { *; }
-keep interface org.alok.** { *; }
-keep class me.weishu.** { *; }
-keep class org.lsposed.** { *; }
-keep class com.moandjiezana.toml.** { *; }
-keep class top.canyie.** { *; }
-keep class com.swift.** { *; }
-keep class com.universal.** { *; }
-keep interface com.universal.** { *; }

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

package com.universal.virtualcore.binder;

public class ServiceInterfaceResolver {
    public static String resolveInterface(String serviceName) {
        switch (serviceName) {
            case "activity":
                return "android.app.IActivityManager";
            case "package":
                return "android.content.pm.IPackageManager";
            case "notification":
                return "android.app.INotificationManager";
            default:
                return null;
        }
    }
}

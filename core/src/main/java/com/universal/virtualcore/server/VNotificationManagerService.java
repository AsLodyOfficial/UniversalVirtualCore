package com.universal.virtualcore.server;

import android.app.Notification;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VNotificationManagerService {
    private static final VNotificationManagerService sInstance = new VNotificationManagerService();
    private final Map<String, Notification> mNotifications = new ConcurrentHashMap<>();

    public static VNotificationManagerService get() {
        return sInstance;
    }

    public void enqueueNotification(String tag, int id, Notification notification) {
        mNotifications.put(tag + "@" + id, notification);
    }

    public void cancelNotification(String tag, int id) {
        mNotifications.remove(tag + "@" + id);
    }
}

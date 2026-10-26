package com.universal.virtualcore.cloaking;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceSpoofer implements Serializable {
    private static final DeviceSpoofer sInstance = new DeviceSpoofer();

    public static class DeviceProfile implements Serializable {
        public String androidId = "a1b2c3d4e5f67890";
        public String imei = "860123456789012";
        public String macAddress = "02:00:00:00:00:00";
        public String model = "Pixel 8 Pro";
        public String manufacturer = "Google";
        public String brand = "google";
        public String device = "husky";
        public String product = "husky";
        public String fingerprint = "google/husky/husky:14/UQ1A.240205.004/11269730:user/release-keys";
    }

    private final Map<Integer, DeviceProfile> mProfiles = new ConcurrentHashMap<>();

    public static DeviceSpoofer get() {
        return sInstance;
    }

    public void setProfile(int userId, DeviceProfile profile) {
        if (profile != null) {
            mProfiles.put(userId, profile);
        }
    }

    public DeviceProfile getProfile(int userId) {
        return mProfiles.computeIfAbsent(userId, k -> new DeviceProfile());
    }
}

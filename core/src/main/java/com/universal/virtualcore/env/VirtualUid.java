package com.universal.virtualcore.env;

public class VirtualUid {
    public static final int BASE_VIRTUAL_UID = 10000;
    public static final int PER_USER_RANGE = 100000;

    public static int getVirtualUid(int userId, int appId) {
        return (userId * PER_USER_RANGE) + BASE_VIRTUAL_UID + (appId % BASE_VIRTUAL_UID);
    }

    public static int getUserId(int virtualUid) {
        return virtualUid / PER_USER_RANGE;
    }

    public static int getAppId(int virtualUid) {
        return virtualUid % PER_USER_RANGE;
    }
}

package com.universal.virtualcore.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VContentManagerService {
    private static final VContentManagerService sInstance = new VContentManagerService();
    private final Map<String, String> mAuthorityMap = new ConcurrentHashMap<>();

    public static VContentManagerService get() {
        return sInstance;
    }

    public void registerAuthority(String authority, String providerClass) {
        mAuthorityMap.put(authority, providerClass);
    }

    public String resolveProvider(String authority) {
        return mAuthorityMap.get(authority);
    }
}

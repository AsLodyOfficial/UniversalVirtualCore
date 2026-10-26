package com.universal.virtualcore.server;

import com.universal.virtualcore.env.VEnvironment;
import com.universal.virtualcore.env.VUserHandle;
import com.universal.virtualcore.env.VirtualStorage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class VUserManagerService {
    private static final VUserManagerService sInstance = new VUserManagerService();
    private final Set<Integer> mUsers = new CopyOnWriteArraySet<>();

    public static VUserManagerService get() {
        return sInstance;
    }

    public VUserManagerService() {
        mUsers.add(0); // Default user
    }

    public VUserHandle createUser(int userId) {
        mUsers.add(userId);
        File userDir = VEnvironment.getUserDirectory(userId);
        if (!userDir.exists()) {
            userDir.mkdirs();
        }
        return new VUserHandle(userId);
    }

    public List<VUserHandle> getUsers() {
        List<VUserHandle> list = new ArrayList<>();
        for (int u : mUsers) {
            list.add(new VUserHandle(u));
        }
        return list;
    }

    public void initializeAppForUser(int userId, String packageName) {
        VirtualStorage.setupUserStorage(userId, packageName);
    }
}

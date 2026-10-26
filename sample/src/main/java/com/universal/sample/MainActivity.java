package com.universal.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.universal.virtualcore.VirtualCore;
import com.universal.virtualcore.env.VUserHandle;
import com.universal.virtualcore.package_mgr.model.VirtualPackage;
import java.io.File;

public class MainActivity extends Activity {
    private static final String TAG = "SampleApp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Initialize Universal Virtual Core
        VirtualCore.get().initialize(this);
        Log.i(TAG, "VirtualCore Initialized. Page size: " + VirtualCore.get().getSystemPageSize() + " bytes");

        // 2. Multi-instance / Virtual user creation
        VUserHandle user0 = VirtualCore.get().createUser(0);
        VUserHandle user1 = VirtualCore.get().createUser(1);
        Log.i(TAG, "Created virtual users: " + user0 + ", " + user1);

        // 3. Example install & launch
        File apkFile = new File(getFilesDir(), "target_app.apk");
        if (apkFile.exists()) {
            VirtualPackage vPkg = VirtualCore.get().installPackage(apkFile);
            Log.i(TAG, "Installed virtual app: " + vPkg.packageName);

            Intent launchIntent = new Intent();
            launchIntent.setClassName(vPkg.packageName, vPkg.activities.get(0).name);
            VirtualCore.get().launchApp(user0.getIdentifier(), launchIntent);
        }
    }
}

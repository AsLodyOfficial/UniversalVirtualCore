package com.universal.virtualcore.auth;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class CustomTabsBridge {
    public static void openCustomTab(Context context, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}

package com.universal.virtualcore.license;

public interface LicenseCallback {
    void onSuccess(LicenseResult result);
    void onFailure(int errorCode, String message);
}

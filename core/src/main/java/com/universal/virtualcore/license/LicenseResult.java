package com.universal.virtualcore.license;

import java.io.Serializable;

public class LicenseResult implements Serializable {
    public boolean active;
    public String message;
    public String serverMode;
    public String client;
    public String version;
    public String rawResponse;

    public LicenseResult(boolean active, String message, String rawResponse) {
        this.active = active;
        this.message = message;
        this.rawResponse = rawResponse;
    }
}

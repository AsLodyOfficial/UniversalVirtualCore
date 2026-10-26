package com.universal.virtualcore.package_mgr.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VirtualPackage implements Serializable {
    public String packageName;
    public int versionCode;
    public String versionName;
    public String sourcePath;
    public String dataPath;
    public String nativeLibPath;
    public String primaryAbi = "arm64-v8a";
    public String applicationClassName;
    public int targetSdkVersion = 34;
    public int minSdkVersion = 24;
    public byte[] signatureCert;

    public List<ComponentInfo> activities = new ArrayList<>();
    public List<ComponentInfo> services = new ArrayList<>();
    public List<ComponentInfo> receivers = new ArrayList<>();
    public List<ComponentInfo> providers = new ArrayList<>();
    public List<String> requestedPermissions = new ArrayList<>();

    public boolean isSplitApk = false;
    public List<String> splitPaths = new ArrayList<>();
}

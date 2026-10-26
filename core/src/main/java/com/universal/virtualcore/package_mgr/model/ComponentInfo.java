package com.universal.virtualcore.package_mgr.model;

import java.io.Serializable;

public class ComponentInfo implements Serializable {
    public String name;
    public String packageName;
    public String processName;
    public boolean exported;
    public String permission;
    public int launchMode;
    public String authority; // for ContentProvider
}

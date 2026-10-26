package com.universal.virtualcore.native_core;

public class PathVirtualizer {
    public static void mapPath(String virtualPath, String realPath) {
        NativeBridge.addPathRule(virtualPath, realPath);
    }

    public static String getRealPath(String virtualPath) {
        return NativeBridge.resolvePath(virtualPath);
    }
}

package com.universal.virtualcore.native_core;

public class PageSizeManager {
    public static long getSystemPageSize() {
        return NativeBridge.getPageSize();
    }

    public static boolean is16KbAlignmentRequired() {
        return NativeBridge.is16KbPage();
    }

    public static long alignToPage(long address, long pageSize) {
        return (address + pageSize - 1) & ~(pageSize - 1);
    }
}

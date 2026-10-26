package com.universal.virtualcore.env;

import java.io.Serializable;
import java.util.Objects;

public class VUserHandle implements Serializable {
    public static final VUserHandle SYSTEM = new VUserHandle(0);
    private final int mHandle;

    public VUserHandle(int handle) {
        this.mHandle = handle;
    }

    public int getIdentifier() {
        return mHandle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VUserHandle that = (VUserHandle) o;
        return mHandle == that.mHandle;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mHandle);
    }

    @Override
    public String toString() {
        return "UserHandle{" + mHandle + "}";
    }
}

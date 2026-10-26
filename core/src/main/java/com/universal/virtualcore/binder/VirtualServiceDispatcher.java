package com.universal.virtualcore.binder;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

public class VirtualServiceDispatcher {
    public static boolean dispatchTransaction(IBinder target, int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (target != null) {
            return target.transact(code, data, reply, flags);
        }
        return false;
    }
}

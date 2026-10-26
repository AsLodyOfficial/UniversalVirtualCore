package org.alok.engine.proxy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Nullable;
import org.alok.engine.Alok;
import org.alok.engine.fake.hook.HookManager;
import org.alok.engine.fake.service.HCallbackProxy;
import org.alok.engine.proxy.record.ProxyActivityRecord;

public class ProxyActivity extends Activity {
   public static final String TAG = "ProxyActivity";
   private static final int RELAY_REQUEST_CODE = 5139812;

   @Override
   protected void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      Log.e("VCoreDiag", "ProxyActivity.onCreate CALLED! class=" + getClass().getName() + " intent=" + getIntent());
      try {
         HookManager.get().checkEnv(HCallbackProxy.class);
      } catch (Throwable ignored) {}

      ProxyActivityRecord record = null;
      try {
         record = ProxyActivityRecord.create(this.getIntent());
      } catch (Throwable t) {
         Log.w(TAG, "Failed to parse ProxyActivityRecord: " + t.getMessage());
      }

      if (record != null && record.mTarget != null) {
         try {
            if (Alok.getApplication() != null) {
               record.mTarget.setExtrasClassLoader(Alok.getApplication().getClassLoader());
            }
            this.startActivityForResult(record.mTarget, RELAY_REQUEST_CODE);
            return;
         } catch (Throwable t) {
            Log.e(TAG, "Failed to start target from ProxyActivity: " + t.getMessage(), t);
         }
      }

      this.finish();
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      if (requestCode == RELAY_REQUEST_CODE) {
         this.setResult(resultCode, data);
         this.finish();
      } else {
         super.onActivityResult(requestCode, resultCode, data);
      }
   }

   public static class P0 extends ProxyActivity {}
   public static class P1 extends ProxyActivity {}
   public static class P2 extends ProxyActivity {}
   public static class P3 extends ProxyActivity {}
   public static class P4 extends ProxyActivity {}
   public static class P5 extends ProxyActivity {}
   public static class P6 extends ProxyActivity {}
   public static class P7 extends ProxyActivity {}
   public static class P8 extends ProxyActivity {}
   public static class P9 extends ProxyActivity {}
   public static class P10 extends ProxyActivity {}
   public static class P11 extends ProxyActivity {}
   public static class P12 extends ProxyActivity {}
   public static class P13 extends ProxyActivity {}
   public static class P14 extends ProxyActivity {}
   public static class P15 extends ProxyActivity {}
   public static class P16 extends ProxyActivity {}
   public static class P17 extends ProxyActivity {}
   public static class P18 extends ProxyActivity {}
   public static class P19 extends ProxyActivity {}
   public static class P20 extends ProxyActivity {}
   public static class P21 extends ProxyActivity {}
   public static class P22 extends ProxyActivity {}
   public static class P23 extends ProxyActivity {}
   public static class P24 extends ProxyActivity {}
   public static class P25 extends ProxyActivity {}
   public static class P26 extends ProxyActivity {}
   public static class P27 extends ProxyActivity {}
   public static class P28 extends ProxyActivity {}
   public static class P29 extends ProxyActivity {}
   public static class P30 extends ProxyActivity {}
   public static class P31 extends ProxyActivity {}
   public static class P32 extends ProxyActivity {}
   public static class P33 extends ProxyActivity {}
   public static class P34 extends ProxyActivity {}
   public static class P35 extends ProxyActivity {}
   public static class P36 extends ProxyActivity {}
   public static class P37 extends ProxyActivity {}
   public static class P38 extends ProxyActivity {}
   public static class P39 extends ProxyActivity {}
   public static class P40 extends ProxyActivity {}
   public static class P41 extends ProxyActivity {}
   public static class P42 extends ProxyActivity {}
   public static class P43 extends ProxyActivity {}
   public static class P44 extends ProxyActivity {}
   public static class P45 extends ProxyActivity {}
   public static class P46 extends ProxyActivity {}
   public static class P47 extends ProxyActivity {}
   public static class P48 extends ProxyActivity {}
   public static class P49 extends ProxyActivity {}
}

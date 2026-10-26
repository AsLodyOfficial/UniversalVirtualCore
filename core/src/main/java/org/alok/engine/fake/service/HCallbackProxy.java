package org.alok.engine.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import androidx.annotation.NonNull;
import org.alok.engine.Alok;
import org.alok.engine.app.BActivityThread;
import org.alok.engine.entity.AppConfig;
import org.alok.engine.fake.hook.IInjectHook;
import org.alok.engine.proxy.ProxyManifest;
import org.alok.engine.proxy.record.ProxyActivityRecord;
import org.alok.engine.utils.Slog;
import org.alok.engine.utils.compat.BuildCompat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.alok.android.app.ActivityThreadActivityClientRecordContext;
import org.alok.android.app.BRActivityClient;
import org.alok.android.app.BRActivityClientActivityClientControllerSingleton;
import org.alok.android.app.BRActivityManagerNative;
import org.alok.android.app.BRActivityThread;
import org.alok.android.app.BRActivityThreadActivityClientRecord;
import org.alok.android.app.BRActivityThreadCreateServiceData;
import org.alok.android.app.BRActivityThreadH;
import org.alok.android.app.BRIActivityManager;
import org.alok.android.app.servertransaction.BRClientTransaction;
import org.alok.android.app.servertransaction.BRLaunchActivityItem;
import org.alok.android.app.servertransaction.LaunchActivityItemContext;
import org.alok.android.os.BRHandler;

public class HCallbackProxy implements IInjectHook, Handler.Callback {
   public static final String TAG = "HCallbackStub";
   private Handler.Callback mOtherCallback;
   private AtomicBoolean mBeing = new AtomicBoolean(false);
   private final java.util.Set<String> mBoundAttempted = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

   private Handler.Callback getHCallback() {
      return BRHandler.get(this.getH()).mCallback();
   }

   private Handler getH() {
      Object currentActivityThread = Alok.mainThread();
      return BRActivityThread.get(currentActivityThread).mH();
   }

   public void injectHook() {
      this.mOtherCallback = this.getHCallback();
      if (this.mOtherCallback != null && (this.mOtherCallback == this || this.mOtherCallback.getClass().getName().equals(this.getClass().getName()))) {
         this.mOtherCallback = null;
      }

      BRHandler.get(this.getH())._set_mCallback(this);
   }

   public boolean isBadEnv() {
      Handler.Callback hCallback = this.getHCallback();
      return hCallback != null && hCallback != this;
   }

   public boolean handleMessage(@NonNull Message msg) {
      if (this.mBeing.getAndSet(true)) {
         return false;
      } else {
         try {
            if (BuildCompat.isPie()) {
               if (msg.what == BRActivityThreadH.get().EXECUTE_TRANSACTION() && this.handleLaunchActivity(msg.obj)) {
                  this.getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                  return true;
               }
            } else if (msg.what == BRActivityThreadH.get().LAUNCH_ACTIVITY() && this.handleLaunchActivity(msg.obj)) {
               this.getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
               return true;
            }

            if (msg.what == BRActivityThreadH.get().CREATE_SERVICE()) {
               return this.handleCreateService(msg.obj);
            } else if (this.mOtherCallback != null) {
               return this.mOtherCallback.handleMessage(msg);
            } else {
               return false;
            }
         } finally {
            this.mBeing.set(false);
         }
      }
   }

   private Object getLaunchActivityItem(Object clientTransaction) {
      if (clientTransaction == null) return null;

      // 1. Try standard BlackReflection path (Android 9 to 13)
      try {
         List<Object> mActivityCallbacks = BRClientTransaction.get(clientTransaction).mActivityCallbacks();
         if (mActivityCallbacks != null) {
            for (Object obj : mActivityCallbacks) {
               if (obj != null && (BRLaunchActivityItem.getRealClass().getName().equals(obj.getClass().getCanonicalName())
                       || obj.getClass().getName().contains("LaunchActivityItem"))) {
                  return obj;
               }
            }
         }
      } catch (Throwable ignored) {}

      // 2. Android 14 / UpsideDownCake fallback: check mTransactionItems or mActivityCallbacks via reflection
      try {
         Field f = null;
         try {
            f = clientTransaction.getClass().getDeclaredField("mTransactionItems");
         } catch (NoSuchFieldException e) {
            try {
               f = clientTransaction.getClass().getDeclaredField("mActivityCallbacks");
            } catch (Throwable ignored) {}
         }

         if (f != null) {
            f.setAccessible(true);
            List<?> items = (List<?>) f.get(clientTransaction);
            if (items != null) {
               for (Object obj : items) {
                  if (obj != null && obj.getClass().getName().contains("LaunchActivityItem")) {
                     return obj;
                  }
               }
            }
         }
      } catch (Throwable ignored) {}

      return null;
   }

   private boolean handleLaunchActivity(Object client) {
      Object r;
      if (BuildCompat.isPie()) {
         r = this.getLaunchActivityItem(client);
      } else {
         r = client;
      }

      if (r == null) {
         return false;
      } else {
         Intent intent = null;
         IBinder token = null;

         if (BuildCompat.isPie()) {
            try {
               intent = BRLaunchActivityItem.get(r).mIntent();
            } catch (Throwable ignored) {}
            if (intent == null) {
               try {
                  Field f = r.getClass().getDeclaredField("mIntent");
                  f.setAccessible(true);
                  intent = (Intent) f.get(r);
               } catch (Throwable ignored) {}
            }

            try {
               token = BRClientTransaction.get(client).mActivityToken();
            } catch (Throwable ignored) {}
            if (token == null) {
               try {
                  Method m = client.getClass().getMethod("getActivityToken");
                  token = (IBinder) m.invoke(client);
               } catch (Throwable ignored) {}
            }
            if (token == null) {
               try {
                  Method m = r.getClass().getMethod("getActivityToken");
                  token = (IBinder) m.invoke(r);
               } catch (Throwable ignored) {}
            }
            if (token == null) {
               try {
                  Field f = r.getClass().getDeclaredField("mActivityToken");
                  f.setAccessible(true);
                  token = (IBinder) f.get(r);
               } catch (Throwable ignored) {}
            }
         } else {
            ActivityThreadActivityClientRecordContext clientRecordContext = BRActivityThreadActivityClientRecord.get(r);
            intent = clientRecordContext.intent();
            token = clientRecordContext.token();
         }

         if (intent == null) {
            return false;
         } else {
            ProxyActivityRecord stubRecord = ProxyActivityRecord.create(intent);
            if (stubRecord == null || stubRecord.mActivityInfo == null) {
               return false;
            }
            ActivityInfo activityInfo = stubRecord.mActivityInfo;
            if (activityInfo != null) {
               if (BActivityThread.getAppConfig() == null) {
                  try {
                     AppConfig appConfig = Alok.getBActivityManager().initProcess(activityInfo.packageName, activityInfo.processName, stubRecord.mUserId);
                     if (appConfig != null) {
                        BActivityThread.currentActivityThread().initProcess(appConfig);
                     }
                  } catch (Throwable t) {
                     Slog.e("HCallbackStub", "Failed to initProcess on null appConfig: " + t.getMessage());
                  }
               }

               if (activityInfo.targetActivity != null && stubRecord.mTarget != null) {
                  stubRecord.mTarget.setClassName(activityInfo.packageName, activityInfo.targetActivity);
               }

               if (!BActivityThread.currentActivityThread().isInit()) {
                  if (!mBoundAttempted.contains(activityInfo.packageName)) {
                     mBoundAttempted.add(activityInfo.packageName);
                     android.util.Log.i("VCoreDiag", "HCallbackProxy bindApplication first attempt for: " + activityInfo.packageName + " proc=" + activityInfo.processName);
                     BActivityThread.currentActivityThread().bindApplication(activityInfo.packageName, activityInfo.processName);
                     if (!BActivityThread.currentActivityThread().isInit()) {
                        android.util.Log.w("VCoreDiag", "isInit still false after synchronous bindApplication, postponing message once");
                        return true;
                     }
                  } else {
                     android.util.Log.w("VCoreDiag", "bindApplication already attempted for " + activityInfo.packageName + ", proceeding to launch to avoid infinite queue loop");
                  }
               }

               try {
                  if (token != null) {
                     int taskId = BRIActivityManager.get(BRActivityManagerNative.get().getDefault()).getTaskForActivity(token, false);
                     Alok.getBActivityManager().onActivityCreated(taskId, token, stubRecord.mActivityRecord);
                  }
               } catch (Throwable ignored) {}

               if (BuildCompat.isPie()) {
                  try {
                     LaunchActivityItemContext launchActivityItemContext = BRLaunchActivityItem.get(r);
                     if (launchActivityItemContext != null) {
                        launchActivityItemContext._set_mIntent(stubRecord.mTarget);
                        launchActivityItemContext._set_mInfo(activityInfo);
                     }
                  } catch (Throwable ignored) {}

                  // Always apply direct reflection for Android 14-16 (BlackReflection silently swallows exceptions)
                  try {
                     Field fIntent = r.getClass().getDeclaredField("mIntent");
                     fIntent.setAccessible(true);
                     fIntent.set(r, stubRecord.mTarget);
                  } catch (Throwable t) {
                     android.util.Log.e("VCoreDiag", "Failed setting LaunchActivityItem.mIntent: " + t);
                  }
                  try {
                     Field fInfo = r.getClass().getDeclaredField("mInfo");
                     fInfo.setAccessible(true);
                     fInfo.set(r, activityInfo);
                  } catch (Throwable t) {
                     android.util.Log.e("VCoreDiag", "Failed setting LaunchActivityItem.mInfo: " + t);
                  }
                  android.util.Log.e("VCoreDiag", "HCallbackProxy swapped LaunchActivityItem -> " + stubRecord.mTarget + " info=" + activityInfo.name);
               }

               if (BuildCompat.isS()) {
                  if (token != null) {
                     try {
                        Object mainThread = Alok.mainThread();
                        Object record = null;
                        try {
                           record = BRActivityThread.get(mainThread).getLaunchingActivity(token);
                        } catch (Throwable ignored) {}
                        if (record == null && mainThread != null) {
                           try {
                              Method m = mainThread.getClass().getDeclaredMethod("getLaunchingActivity", IBinder.class);
                              m.setAccessible(true);
                              record = m.invoke(mainThread, token);
                           } catch (Throwable ignored) {}
                        }
                        if (record == null && mainThread != null) {
                           try {
                              Field fActivities = mainThread.getClass().getDeclaredField("mActivities");
                              fActivities.setAccessible(true);
                              Object map = fActivities.get(mainThread);
                              if (map instanceof java.util.Map) {
                                 record = ((java.util.Map<?, ?>) map).get(token);
                              }
                           } catch (Throwable ignored) {}
                        }
                        if (record != null) {
                           try {
                              ActivityThreadActivityClientRecordContext clientRecordContext = BRActivityThreadActivityClientRecord.get(record);
                              clientRecordContext._set_intent(stubRecord.mTarget);
                              clientRecordContext._set_activityInfo(activityInfo);
                              clientRecordContext._set_packageInfo(BActivityThread.currentActivityThread().getPackageInfo());
                           } catch (Throwable ignored) {}
                           try {
                              Field fIntent = record.getClass().getDeclaredField("intent");
                              fIntent.setAccessible(true);
                              fIntent.set(record, stubRecord.mTarget);
                           } catch (Throwable ignored) {}
                           try {
                              Field fInfo = record.getClass().getDeclaredField("activityInfo");
                              fInfo.setAccessible(true);
                              fInfo.set(record, activityInfo);
                           } catch (Throwable ignored) {}
                           try {
                              Field fPkg = record.getClass().getDeclaredField("packageInfo");
                              fPkg.setAccessible(true);
                              fPkg.set(record, BActivityThread.currentActivityThread().getPackageInfo());
                           } catch (Throwable ignored) {}
                        }
                     } catch (Throwable t) {
                        Slog.w("HCallbackStub", "Failed setting launching activity record on S: " + t.getMessage());
                     }
                  }

                  this.checkActivityClient();
               } else if (!BuildCompat.isPie()) {
                  ActivityThreadActivityClientRecordContext clientRecordContext = BRActivityThreadActivityClientRecord.get(r);
                  clientRecordContext._set_intent(stubRecord.mTarget);
                  clientRecordContext._set_activityInfo(activityInfo);
               }
            }

            return false;
         }
      }
   }

   private boolean handleCreateService(Object data) {
      if (BActivityThread.getAppConfig() != null) {
         String appPackageName = BActivityThread.getAppPackageName();
         assert appPackageName != null;

         ServiceInfo serviceInfo = BRActivityThreadCreateServiceData.get(data).info();
         if (!serviceInfo.name.equals(ProxyManifest.getProxyService(BActivityThread.getAppPid())) && !serviceInfo.name.equals(ProxyManifest.getProxyJobService(BActivityThread.getAppPid()))) {
            Slog.d("HCallbackStub", "handleCreateService: " + data);
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(appPackageName, serviceInfo.name));
            Alok.getBActivityManager().startService(intent, (String)null, false, BActivityThread.getUserId());
            return true;
         }
      }

      return false;
   }

   private void checkActivityClient() {
      try {
         Object activityClientController = BRActivityClient.get().getActivityClientController();
         if (!(activityClientController instanceof Proxy)) {
            IActivityClientProxy iActivityClientProxy = new IActivityClientProxy(activityClientController);
            iActivityClientProxy.onlyProxy(true);
            iActivityClientProxy.injectHook();
            Object instance = BRActivityClient.get().getInstance();
            Object o = BRActivityClient.get(instance).INTERFACE_SINGLETON();
            BRActivityClientActivityClientControllerSingleton.get(o)._set_mKnownInstance(iActivityClientProxy.getProxyInvocation());
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }
}

package com.universal.virtualcore.auth;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.accounts.IAccountManagerResponse;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import org.alok.engine.Alok;
import org.alok.engine.fake.frameworks.BAccountManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Intercepts AccountManager IPC calls from virtual apps (such as Google Play Store)
 * to resolve the broken authenticator binding inside the virtual container.
 *
 * Exposes host Google accounts to the virtual space, provides the system Settings
 * Add Account intent (without FLAG_ACTIVITY_NEW_TASK) so Play Store's "Sign in"
 * button responds properly, and satisfies all IAccountManager queries.
 */
public class VirtualAccountHook {
    private static final String TAG = "VirtualAccountHook";
    private static final List<Account> sCachedGoogleAccounts = Collections.synchronizedList(new ArrayList<>());

    /**
     * Synchronizes existing Google accounts from the host device into the container.
     */
    public static void syncHostAccounts(Context context) {
        if (context == null) {
            context = Alok.getContext();
        }
        if (context == null) return;
        try {
            AccountManager am = AccountManager.get(context);
            Account[] accounts = am.getAccountsByType("com.google");
            if (accounts != null && accounts.length > 0) {
                sCachedGoogleAccounts.clear();
                for (Account acc : accounts) {
                    sCachedGoogleAccounts.add(acc);
                    try {
                        BAccountManager.get().addAccountExplicitly(acc, null, null);
                    } catch (Throwable ignored) {}
                }
                Log.i(TAG, "syncHostAccounts: synced " + accounts.length + " Google accounts from host");
            }
        } catch (Throwable t) {
            Log.w(TAG, "syncHostAccounts note: " + t.getMessage());
        }
    }

    /**
     * Retrieves all known Google accounts (from cache, host AccountManager, or BAccountManager).
     */
    public static Account[] getGoogleAccounts() {
        try {
            Context ctx = Alok.getContext();
            if (ctx != null) {
                syncHostAccounts(ctx);
            }
            if (!sCachedGoogleAccounts.isEmpty()) {
                return sCachedGoogleAccounts.toArray(new Account[0]);
            }
            Account[] vAccounts = BAccountManager.get().getAccountsAsUser("com.google");
            if (vAccounts != null && vAccounts.length > 0) {
                return vAccounts;
            }
        } catch (Throwable t) {
            Log.w(TAG, "getGoogleAccounts note: " + t.getMessage());
        }
        return new Account[0];
    }

    /**
     * Intercepts IAccountManagerProxy$addAccount and IAccountManagerProxy$addAccountAsUser.
     * Called when the user clicks the "Sign in" button in Google Play Store.
     */
    public static Object handleAddAccount(Object who, Method method, Object[] args) {
        try {
            if (args == null || args.length < 6) {
                return 0;
            }
            IAccountManagerResponse response = (IAccountManagerResponse) args[0];
            String accountType = (String) args[1];
            String authTokenType = (String) args[2];
            String[] requiredFeatures = (String[]) args[3];
            boolean expectActivityLaunch = args[4] != null && ((Boolean) args[4]);
            Bundle options = args[5] != null ? (Bundle) args[5] : new Bundle();

            Log.i(TAG, "handleAddAccount intercepted for accountType=" + accountType);

            if ("com.google".equals(accountType)) {
                // Ensure GMS is installed in virtual space before launching auth activity
                try {
                    com.universal.virtualcore.core.AlokDexInjector.ensureInstalledBeforeLaunch("com.google.android.gms", 0);
                } catch (Throwable t) {
                    Log.w(TAG, "GMS pre-install check: " + t.getMessage());
                }

                Intent addAccountIntent = createGoogleAddAccountIntent(options, authTokenType, requiredFeatures);
                Bundle result = new Bundle();
                result.putParcelable(AccountManager.KEY_INTENT, addAccountIntent);

                if (response != null) {
                    try {
                        response.onResult(result);
                        Log.i(TAG, "Successfully returned GMS AccountIntroActivity KEY_INTENT to AccountManager response");
                        return 0;
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to call response.onResult: " + t.getMessage(), t);
                    }
                }
                return 0;
            }

            // Normal container authenticator dispatch for non-Google account types
            BAccountManager.get().addAccount(response, accountType, authTokenType, requiredFeatures, expectActivityLaunch, options);
        } catch (Throwable t) {
            Log.e(TAG, "Error in handleAddAccount: " + t.getMessage(), t);
        }
        return 0;
    }

    /**
     * Creates an in-container Intent targeting Google Play Services (GMS) AccountIntroActivity.
     * Matches Vanta's BAccountManagerService buildGoogleAddAccountIntent implementation.
     * Keeps Google login strictly inside the virtual container.
     */
    public static Intent createGoogleAddAccountIntent(Bundle options, String authTokenType, String[] requiredFeatures) {
        Intent intent = new Intent();
        intent.setComponent(new android.content.ComponentName("com.google.android.gms", "com.google.android.gms.auth.uiflows.addaccount.AccountIntroActivity"));
        intent.setPackage("com.google.android.gms");
        intent.putExtra("accountType", "com.google");
        if (authTokenType != null) {
            intent.putExtra("authTokenType", authTokenType);
        }
        if (requiredFeatures != null) {
            intent.putExtra("requiredFeatures", requiredFeatures);
        }
        if (options != null) {
            intent.putExtras(options);
        }
        return intent;
    }

    /**
     * Intercepts IAccountManagerProxy$getAccountsByType.hook(who, method, args)
     */
    public static Object handleGetAccountsByType(Object who, Method method, Object[] args) {
        try {
            String type = (args != null && args.length > 0 && args[0] instanceof String) ? (String) args[0] : null;
            if ("com.google".equals(type)) {
                Account[] accounts = getGoogleAccounts();
                Log.i(TAG, "handleGetAccountsByType(com.google) returning " + accounts.length + " accounts");
                return accounts;
            }
            return BAccountManager.get().getAccountsAsUser(type);
        } catch (Throwable t) {
            Log.e(TAG, "Error in handleGetAccountsByType: " + t.getMessage(), t);
            return new Account[0];
        }
    }

    /**
     * Intercepts IAccountManagerProxy$getAccountsAsUser.hook(who, method, args)
     */
    public static Object handleGetAccountsAsUser(Object who, Method method, Object[] args) {
        return handleGetAccountsByType(who, method, args);
    }

    /**
     * Intercepts IAccountManagerProxy$getAccounts.hook(who, method, args)
     */
    public static Object handleGetAccounts(Object who, Method method, Object[] args) {
        return handleGetAccountsByType(who, method, args);
    }

    /**
     * Intercepts IAccountManagerProxy$getAccountsByTypeForPackage.hook(who, method, args)
     */
    public static Object handleGetAccountsByTypeForPackage(Object who, Method method, Object[] args) {
        try {
            String type = (args != null && args.length > 0 && args[0] instanceof String) ? (String) args[0] : null;
            String pkg = (args != null && args.length > 1 && args[1] instanceof String) ? (String) args[1] : null;
            if ("com.google".equals(type)) {
                Account[] accounts = getGoogleAccounts();
                Log.i(TAG, "handleGetAccountsByTypeForPackage(com.google, " + pkg + ") returning " + accounts.length + " accounts");
                return accounts;
            }
            return BAccountManager.get().getAccountsByTypeForPackage(type, pkg);
        } catch (Throwable t) {
            Log.e(TAG, "Error in handleGetAccountsByTypeForPackage: " + t.getMessage(), t);
            return new Account[0];
        }
    }

    /**
     * Intercepts IAccountManagerProxy$getAccountsByFeatures.hook(who, method, args)
     */
    public static Object handleGetAccountsByFeatures(Object who, Method method, Object[] args) {
        try {
            if (args == null || args.length < 3) return 0;
            IAccountManagerResponse response = (IAccountManagerResponse) args[0];
            String accountType = (String) args[1];
            String[] features = (String[]) args[2];
            if ("com.google".equals(accountType)) {
                Account[] accounts = getGoogleAccounts();
                Bundle result = new Bundle();
                result.putParcelableArray(AccountManager.KEY_ACCOUNTS, accounts);
                if (response != null) {
                    try {
                        response.onResult(result);
                        Log.i(TAG, "handleGetAccountsByFeatures(com.google) returned " + accounts.length + " accounts");
                        return 0;
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to call response.onResult: " + t.getMessage());
                    }
                }
                return 0;
            }
            BAccountManager.get().getAccountsByFeatures(response, accountType, features);
        } catch (Throwable t) {
            Log.e(TAG, "Error in handleGetAccountsByFeatures: " + t.getMessage(), t);
        }
        return 0;
    }

    /**
     * Intercepts IAccountManagerProxy$getAccountByTypeAndFeatures.hook(who, method, args)
     */
    public static Object handleGetAccountByTypeAndFeatures(Object who, Method method, Object[] args) {
        try {
            if (args == null || args.length < 3) {
                return 0;
            }
            IAccountManagerResponse response = (IAccountManagerResponse) args[0];
            String accountType = (String) args[1];
            String[] features = (String[]) args[2];

            Log.i(TAG, "handleGetAccountByTypeAndFeatures intercepted for accountType=" + accountType);

            if ("com.google".equals(accountType)) {
                Account[] accounts = getGoogleAccounts();
                Bundle result = new Bundle();
                if (accounts != null && accounts.length > 0) {
                    result.putString(AccountManager.KEY_ACCOUNT_NAME, accounts[0].name);
                    result.putString(AccountManager.KEY_ACCOUNT_TYPE, accounts[0].type);
                    result.putParcelableArray("accounts", accounts);
                } else {
                    result.putString(AccountManager.KEY_ACCOUNT_NAME, null);
                    result.putString(AccountManager.KEY_ACCOUNT_TYPE, null);
                    result.putParcelableArray("accounts", new Account[0]);
                }
                if (response != null) {
                    try {
                        response.onResult(result);
                        Log.i(TAG, "Returned Google accounts result bundle (count=" + (accounts != null ? accounts.length : 0) + ")");
                        return 0;
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to call response.onResult: " + t.getMessage());
                    }
                }
                return 0;
            }

            BAccountManager.get().getAccountByTypeAndFeatures(response, accountType, features);
        } catch (Throwable t) {
            Log.e(TAG, "Error in handleGetAccountByTypeAndFeatures: " + t.getMessage(), t);
        }
        return 0;
    }

    /**
     * Intercepts IAccountManagerProxy$hasFeatures.hook(who, method, args)
     */
    public static Object handleHasFeatures(Object who, Method method, Object[] args) {
        try {
            if (args == null || args.length < 3) return 0;
            IAccountManagerResponse response = (IAccountManagerResponse) args[0];
            Account account = (Account) args[1];
            boolean exists = false;
            if (account != null && "com.google".equals(account.type)) {
                Account[] accounts = getGoogleAccounts();
                for (Account a : accounts) {
                    if (a.name != null && a.name.equals(account.name)) {
                        exists = true;
                        break;
                    }
                }
            }
            Bundle result = new Bundle();
            result.putBoolean("booleanResult", exists);
            if (response != null) {
                response.onResult(result);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in handleHasFeatures: " + t.getMessage(), t);
        }
        return 0;
    }

    /**
     * Intercepts IAccountManagerProxy$getAuthToken.hook(who, method, args)
     */
    public static Object handleGetAuthToken(Object who, Method method, Object[] args) {
        try {
            if (args == null || args.length < 6) return 0;
            IAccountManagerResponse response = (IAccountManagerResponse) args[0];
            Account account = (Account) args[1];
            String authTokenType = (String) args[2];
            boolean notifyOnAuthFailure = args[3] != null && ((Boolean) args[3]);
            boolean expectActivityLaunch = args[4] != null && ((Boolean) args[4]);
            Bundle loginOptions = args[5] != null ? (Bundle) args[5] : new Bundle();

            if (account != null && "com.google".equals(account.type)) {
                try {
                    Context ctx = Alok.getContext();
                    if (ctx != null) {
                        AccountManager hostAm = AccountManager.get(ctx);
                        Bundle tokenBundle = hostAm.getAuthToken(account, authTokenType, loginOptions, false, null, null).getResult();
                        if (tokenBundle != null && response != null) {
                            response.onResult(tokenBundle);
                            Log.i(TAG, "handleGetAuthToken: retrieved token from host AccountManager for " + account.name);
                            return 0;
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "handleGetAuthToken host lookup note: " + t.getMessage());
                }

                // Fallback: If cannot fetch direct token, return addAccountIntent
                if (response != null) {
                    try {
                        Intent addAccountIntent = createGoogleAddAccountIntent(loginOptions, authTokenType, null);
                        Bundle result = new Bundle();
                        result.putParcelable(AccountManager.KEY_INTENT, addAccountIntent);
                        response.onResult(result);
                        Log.i(TAG, "handleGetAuthToken: returned KEY_INTENT fallback for " + account.name);
                        return 0;
                    } catch (Throwable t) {
                        Log.e(TAG, "handleGetAuthToken response note: " + t.getMessage());
                    }
                }
                return 0;
            }

            BAccountManager.get().getAuthToken(response, account, authTokenType, notifyOnAuthFailure, expectActivityLaunch, loginOptions);
        } catch (Throwable t) {
            Log.e(TAG, "Error in handleGetAuthToken: " + t.getMessage(), t);
        }
        return 0;
    }

    /**
     * Intercepts IAccountManagerProxy$getAuthenticatorTypes.hook(who, method, args)
     * Ensures "com.google" is reported as an available authenticator.
     */
    public static Object handleGetAuthenticatorTypes(Object who, Method method, Object[] args) {
        try {
            AuthenticatorDescription[] types = BAccountManager.get().getAuthenticatorTypes();
            boolean hasGoogle = false;
            if (types != null) {
                for (AuthenticatorDescription desc : types) {
                    if ("com.google".equals(desc.type)) {
                        hasGoogle = true;
                        break;
                    }
                }
            }
            if (!hasGoogle) {
                AuthenticatorDescription googleDesc = AuthenticatorDescription.newKey("com.google");
                int len = types != null ? types.length : 0;
                AuthenticatorDescription[] augmented = new AuthenticatorDescription[len + 1];
                if (types != null && len > 0) {
                    System.arraycopy(types, 0, augmented, 0, len);
                }
                augmented[len] = googleDesc;
                return augmented;
            }
            return types;
        } catch (Throwable t) {
            Log.w(TAG, "handleGetAuthenticatorTypes note: " + t.getMessage());
            return new AuthenticatorDescription[]{AuthenticatorDescription.newKey("com.google")};
        }
    }
}

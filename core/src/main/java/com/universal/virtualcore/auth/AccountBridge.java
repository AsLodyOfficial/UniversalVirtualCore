package com.universal.virtualcore.auth;

import android.accounts.Account;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AccountBridge {
    private static final Map<String, List<Account>> sUserAccounts = new ConcurrentHashMap<>();

    public static void addAccount(String packageName, Account account) {
        sUserAccounts.computeIfAbsent(packageName, k -> new ArrayList<>()).add(account);
    }

    public static List<Account> getAccounts(String packageName) {
        return sUserAccounts.getOrDefault(packageName, new ArrayList<>());
    }
}

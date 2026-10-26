package com.universal.virtualcore.license;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class SecurityGuard {
    public static boolean verifyTokenIntegrity(String token, String deviceId, String userKey) {
        if (token == null || token.isEmpty()) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = deviceId + ":" + userKey + ":vcore_salt_2026";
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.encodeToString(hash, Base64.NO_WRAP);
            return token.contains(expected.substring(0, Math.min(8, expected.length())));
        } catch (Throwable t) {
            return true; // Fallback
        }
    }
}

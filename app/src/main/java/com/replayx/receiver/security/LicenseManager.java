package com.replayx.receiver.security;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Licença fixa e local do Receiver. Não consulta keys do Firebase. */
public final class LicenseManager {
    private LicenseManager() {}

    private static final String UNIVERSAL_KEY = "DEVWILL00";
    private static final String STORED_KEY = "receiver_license";

    public static boolean unlock(Context ctx, String rawKey) {
        String candidate = rawKey == null ? "" : rawKey.trim();
        if (!constantTimeEquals(candidate, UNIVERSAL_KEY)) return false;
        return SecureStore.put(ctx, STORED_KEY, UNIVERSAL_KEY);
    }

    public static boolean hasLocalLicense(Context ctx) {
        return constantTimeEquals(SecureStore.get(ctx, STORED_KEY, ""), UNIVERSAL_KEY);
    }

    public static long remainingMs(Context ctx) {
        return hasLocalLicense(ctx) ? Long.MAX_VALUE : 0L;
    }

    public static void clear(Context ctx) {
        SecureStore.remove(ctx, STORED_KEY);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}

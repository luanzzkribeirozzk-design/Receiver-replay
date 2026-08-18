package com.replayx.receiver.security;

import android.content.Context;

/** Barreira comum para impedir que apenas pular a LoginActivity libere o Receiver. */
public final class SecurityGate {
    private SecurityGate() {}

    public static boolean allow(Context context) {
        try {
            return IntegrityCheck.isValid(context)
                    && LicenseManager.hasLocalLicense(context)
                    && !LicenseManager.savedKey(context).trim().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }
}

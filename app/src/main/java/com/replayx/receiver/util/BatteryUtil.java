package com.replayx.receiver.util;

import android.content.Context;
import android.os.BatteryManager;

public final class BatteryUtil {
    private BatteryUtil() {}

    public static int percent(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception e) {
            return -1;
        }
    }
}

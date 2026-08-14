package com.replayx.receiver.util;

import android.content.Context;
import org.json.JSONObject;

/**
 * Lado do Receptor: consome o código gerado pelo Enviador, uso único.
 */
public final class PairingManager {
    private PairingManager() {}

    public static final String PREFS = "replayx_recv_prefs";

    /** Tenta parear com o código informado. Devolve mensagem de status. */
    public static String pairWithCode(Context ctx, String code) {
        try {
            code = code.trim().toUpperCase();
            if (code.isEmpty()) return "ERR_CODIGO_VAZIO";

            JSONObject fields = Fs.getDoc("pair_codes/" + code);
            if (fields == null) return "ERR_CODIGO_INVALIDO";

            boolean used = Fs.getBool(fields, "used", false);
            if (used) return "ERR_CODIGO_JA_USADO";

            Long expiresAt = Fs.getTsSec(fields, "expiresAt");
            long now = System.currentTimeMillis() / 1000L;
            if (expiresAt != null && now > expiresAt) return "ERR_CODIGO_EXPIRADO";

            String senderId = Fs.getStr(fields, "senderId", "");
            if (senderId.isEmpty()) return "ERR_CODIGO_INVALIDO";

            String myId = DeviceId.get(ctx);
            int battery = BatteryUtil.percent(ctx);

            JSONObject pairFields = new JSONObject();
            pairFields.put("status", Fs.str("connected"));
            pairFields.put("receiverId", Fs.str(myId));
            pairFields.put("receiverModel", Fs.str(DeviceId.model()));
            pairFields.put("receiverBattery", Fs.num(battery));
            pairFields.put("pairedAt", Fs.ts(now));
            boolean ok1 = Fs.patchDoc("pairings/" + senderId, pairFields);
            if (!ok1) return "ERR_FALHA_PAREAR";

            JSONObject usedFields = new JSONObject();
            usedFields.put("used", Fs.bool(true));
            Fs.patchDoc("pair_codes/" + code, usedFields, "updateMask.fieldPaths=used");

            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("senderId", senderId).apply();

            return "OK";
        } catch (Exception e) {
            return "ERR: " + e.getMessage();
        }
    }

    public static String getPairedSenderId(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("senderId", "");
    }

    public static void unpairLocal(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("senderId").apply();
    }

    /** Atualiza a bateria periodicamente no doc de pairing (opcional, chamado no resume). */
    public static void refreshBattery(Context ctx) {
        try {
            String senderId = getPairedSenderId(ctx);
            if (senderId.isEmpty()) return;
            JSONObject f = new JSONObject();
            f.put("receiverBattery", Fs.num(BatteryUtil.percent(ctx)));
            Fs.patchDoc("pairings/" + senderId, f, "updateMask.fieldPaths=receiverBattery");
        } catch (Exception ignored) {}
    }
}

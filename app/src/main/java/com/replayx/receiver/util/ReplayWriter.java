package com.replayx.receiver.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Escreve o replay recebido dentro da pasta do Free Fire escolhido (MAX ou
 * Normal) no celular, via Shizuku — mesma lógica de reescrita do JSON
 * (Version/GameVersion/AppId) que o Combo Replay já usa.
 */
public final class ReplayWriter {
    private ReplayWriter() {}

    public static final String FFM_PKG = "com.dts.freefiremax";
    public static final String FFN_PKG = "com.dts.freefireth";

    private static String installedVersion(Context ctx, String pkg) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, 0);
            String v = pi.versionName;
            return (v == null || v.trim().isEmpty()) ? null : v.trim();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** @return "COPIADO_OK" em sucesso, ou uma mensagem de erro. */
    public static String writeToGame(Context ctx, byte[] binData, byte[] jsonData,
                                      String binName, String jsonName, String targetPkg) {
        try {
            // Grava num arquivo temporário local antes de mandar pra pasta do jogo via shell
            File tmpBin = new File(ctx.getCacheDir(), "recv_" + System.currentTimeMillis() + ".bin");
            try (FileOutputStream fos = new FileOutputStream(tmpBin)) { fos.write(binData); }

            File tmpJson = null;
            if (jsonData != null && jsonData.length > 0) {
                tmpJson = new File(ctx.getCacheDir(), "recv_" + System.currentTimeMillis() + ".json");
                try (FileOutputStream fos = new FileOutputStream(tmpJson)) { fos.write(jsonData); }
            }

            String version = installedVersion(ctx, targetPkg);
            if (version == null) version = targetPkg.equals(FFM_PKG) ? "2.126.1" : "1.129.1";
            String toId = targetPkg;

            if (binName == null || binName.trim().isEmpty()) binName = "replay.bin";
            if (jsonName == null) jsonName = "";

            StringBuilder cmd = new StringBuilder();
            cmd.append("DST=''; ");
            cmd.append("for P in ");
            cmd.append("'/storage/emulated/0/Android/data/").append(targetPkg).append("/files/MReplays' ");
            cmd.append("'/sdcard/Android/data/").append(targetPkg).append("/files/MReplays' ");
            cmd.append("'/data/media/0/Android/data/").append(targetPkg).append("/files/MReplays' ");
            cmd.append("'/mnt/user/0/").append(targetPkg).append("/files/MReplays' ");
            cmd.append("; do DST=\"$P\" && break; done; ");
            cmd.append("mkdir -p \"$DST\"; ");
            cmd.append("rm -f \"$DST\"/*.bin \"$DST\"/*.json 2>/dev/null; ");
            cmd.append("cp -f \"").append(tmpBin.getAbsolutePath()).append("\" \"$DST/").append(binName).append("\" || { echo CP_BIN_FAIL; exit 0; }; ");
            cmd.append("chmod 666 \"$DST/").append(binName).append("\" 2>/dev/null; ");

            if (tmpJson != null) {
                cmd.append("cp -f \"").append(tmpJson.getAbsolutePath()).append("\" \"$DST/").append(jsonName).append("\" || { echo CP_JSON_FAIL; exit 0; }; ");
                cmd.append("chmod 666 \"$DST/").append(jsonName).append("\" 2>/dev/null; ");
                cmd.append("sed -i 's/\"Version\":\"[^\"]*\"/\"Version\":\"").append(version).append("\"/g' \"$DST/").append(jsonName).append("\" 2>/dev/null; ");
                cmd.append("sed -i 's/\"GameVersion\":\"[^\"]*\"/\"GameVersion\":\"").append(version).append("\"/g' \"$DST/").append(jsonName).append("\" 2>/dev/null; ");
                cmd.append("sed -i 's/\"AppId\":\"[^\"]*\"/\"AppId\":\"").append(toId).append("\"/g' \"$DST/").append(jsonName).append("\" 2>/dev/null; ");
            }

            cmd.append("am force-stop ").append(targetPkg).append(" 2>/dev/null; ");
            cmd.append("cmd media scan-file \"$DST/").append(binName).append("\" 2>/dev/null; ");
            cmd.append("echo COPIADO_OK");

            String result = RootShell.run(cmd.toString());

            tmpBin.delete();
            if (tmpJson != null) tmpJson.delete();

            return result;
        } catch (Exception e) {
            return "ERR: " + e.getMessage();
        }
    }
}

package com.replayx.receiver.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Grava um par de replay (.bin + .json) na variante correta do Free Fire. */
public final class ReplayWriter {
    private ReplayWriter() {}

    public static final String FFM_PKG = "com.dts.freefiremax";
    public static final String FFN_PKG = "com.dts.freefireth";

    public interface Log {
        void onLog(String msg);
    }

    private static final String[] BASES = {
        "/storage/emulated/0",
        "/sdcard",
        "/data/media/0",
        "/mnt/user/0",
        "/storage/self/primary"
    };

    private static final String[] SUBDIRS = {
        "files/MReplays",
        "files/Replays"
    };

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

    private static String fileName(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        return new File(value.trim()).getName();
    }

    private static boolean validName(String name, String extension) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")) return false;
        return name.toLowerCase(Locale.US).endsWith(extension);
    }

    private static boolean sameStem(String binName, String jsonName) {
        String binStem = binName.substring(0, binName.length() - 4);
        String jsonStem = jsonName.substring(0, jsonName.length() - 5);
        return !binStem.isEmpty() && binStem.equals(jsonStem);
    }

    /**
     * Prefere a pasta que já contém arquivos criados pelo Free Fire. Se ainda
     * estiver vazia, usa uma pasta existente; só cria MReplays como último caso.
     */
    private static String chooseDestination(String targetPkg, Log log) {
        String firstExisting = null;
        for (String base : BASES) {
            for (String subdir : SUBDIRS) {
                String dir = base + "/Android/data/" + targetPkg + "/" + subdir;
                String exists = RootShell.run("[ -d \"" + dir + "\" ] && echo EXISTE || echo NAO_EXISTE");
                log.onLog("[..] verificando pasta " + dir + " -> " + (exists == null ? "SEM_RESPOSTA" : exists.trim()));
                if (exists == null || !exists.contains("EXISTE")) continue;
                if (firstExisting == null) firstExisting = dir;

                String bins = RootShell.run("ls -1 \"" + dir + "\"/*.bin 2>/dev/null | head -n 1");
                String jsons = RootShell.run("ls -1 \"" + dir + "\"/*.json 2>/dev/null | head -n 1");
                if (bins != null && !bins.trim().isEmpty() && jsons != null && !jsons.trim().isEmpty()) {
                    log.onLog("[OK] pasta com par de replay existente: " + dir);
                    return dir;
                }
            }
        }
        if (firstExisting != null) {
            log.onLog("[OK] usando pasta existente ainda vazia: " + firstExisting);
            return firstExisting;
        }

        for (String base : BASES) {
            String dir = base + "/Android/data/" + targetPkg + "/files/MReplays";
            String created = RootShell.run("mkdir -p \"" + dir + "\" 2>/dev/null && [ -d \"" + dir + "\" ] && echo EXISTE || echo NAO_EXISTE");
            if (created != null && created.contains("EXISTE")) {
                log.onLog("[OK] pasta criada: " + dir);
                return dir;
            }
        }
        return null;
    }

    private static byte[] normalizeJson(Context ctx, byte[] jsonData, String targetPkg, Log log) {
        try {
            String text = new String(jsonData, StandardCharsets.UTF_8);
            if (text.length() > 0 && text.charAt(0) == '\ufeff') text = text.substring(1);
            JSONObject metadata = new JSONObject(text.trim());
            String version = installedVersion(ctx, targetPkg);
            if (version != null) {
                if (metadata.has("Version")) metadata.put("Version", version);
                if (metadata.has("GameVersion")) metadata.put("GameVersion", version);
            } else {
                log.onLog("[AVISO] versão do pacote não detectada; JSON original será preservado");
            }
            if (metadata.has("AppId")) metadata.put("AppId", targetPkg);
            log.onLog("[OK] JSON validado para " + targetPkg + " versão " + version);
            return metadata.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.onLog("[ERR] JSON_INVALIDO — não foi possível interpretar os metadados do replay");
            return null;
        }
    }

    /** @return COPIADO_OK somente quando .bin e .json existem e têm tamanho maior que zero. */
    public static String writeToGame(Context ctx, byte[] binData, byte[] jsonData,
                                      String binName, String jsonName, String targetPkg, Log log) {
        File tmpBin = null;
        File tmpJson = null;
        try {
            if (!FFM_PKG.equals(targetPkg) && !FFN_PKG.equals(targetPkg)) {
                return "ERR: VARIANTE_FREE_FIRE_INVALIDA";
            }
            if (binData == null || binData.length == 0) {
                return "ERR: BIN_VAZIO_OU_INVALIDO";
            }
            if (jsonData == null || jsonData.length == 0) {
                return "ERR: JSON_AUSENTE_OU_VAZIO — o jogo exige o par .bin + .json";
            }

            String safeBinName = fileName(binName);
            String safeJsonName = fileName(jsonName);
            if (!validName(safeBinName, ".bin") || !validName(safeJsonName, ".json")) {
                return "ERR: NOMES_DE_REPLAY_INVALIDOS";
            }
            if (!sameStem(safeBinName, safeJsonName)) {
                return "ERR: BIN_JSON_COM_NOMES_DIFERENTES";
            }
            if (installedVersion(ctx, targetPkg) == null) {
                log.onLog("[AVISO] pacote não visível ao Android; tentando a pasta da variante mesmo assim");
            }

            byte[] finalJson = normalizeJson(ctx, jsonData, targetPkg, log);
            if (finalJson == null || finalJson.length == 0) {
                return "ERR: JSON_INVALIDO_OU_INCOMPATIVEL";
            }

            File tmpDir = ctx.getExternalFilesDir("replay_stage");
            if (tmpDir == null) tmpDir = ctx.getCacheDir();
            if (!tmpDir.exists() && !tmpDir.mkdirs()) {
                return "ERR: NAO_FOI_POSSIVEL_CRIAR_AREA_TEMPORARIA";
            }
            String stamp = String.valueOf(System.currentTimeMillis());
            tmpBin = new File(tmpDir, "rx_stage_" + stamp + ".bin");
            tmpJson = new File(tmpDir, "rx_stage_" + stamp + ".json");
            try (FileOutputStream fos = new FileOutputStream(tmpBin)) { fos.write(binData); }
            try (FileOutputStream fos = new FileOutputStream(tmpJson)) { fos.write(finalJson); }

            String dst = chooseDestination(targetPkg, log);
            if (dst == null) {
                return "ERR: NAO_FOI_POSSIVEL_LOCALIZAR_PASTA_DO_FREE_FIRE";
            }

            String binDst = dst + "/" + safeBinName;
            String jsonDst = dst + "/" + safeJsonName;
            StringBuilder cmd = new StringBuilder();
            cmd.append("am force-stop ").append(targetPkg).append(" 2>/dev/null || true; ");
            cmd.append("cp -f \"").append(tmpBin.getAbsolutePath()).append("\" \"").append(binDst).append("\"; ");
            cmd.append("cp -f \"").append(tmpJson.getAbsolutePath()).append("\" \"").append(jsonDst).append("\"; ");
            cmd.append("chmod 666 \"").append(binDst).append("\" \"").append(jsonDst).append("\" 2>/dev/null || true; ");
            cmd.append("restorecon -F \"").append(binDst).append("\" 2>/dev/null || true; ");
            cmd.append("restorecon -F \"").append(jsonDst).append("\" 2>/dev/null || true; ");
            cmd.append("restorecon -F \"").append(dst).append("\" 2>/dev/null || true; ");
            cmd.append("sync; ");
            cmd.append("BSZ=$(wc -c < \"").append(binDst).append("\" 2>/dev/null); ");
            cmd.append("JSZ=$(wc -c < \"").append(jsonDst).append("\" 2>/dev/null); ");
            cmd.append("if [ -f \"").append(binDst).append("\" ] && [ \"$BSZ\" -gt 0 ] && [ -f \"").append(jsonDst).append("\" ] && [ \"$JSZ\" -gt 0 ]; then echo COPIADO_OK; else echo CP_VERIFY_FAIL; fi");

            log.onLog("[..] copiando par para " + targetPkg + " em " + dst);
            String result = RootShell.run(cmd.toString());
            log.onLog("[..] resultado shell: " + result);
            return result == null ? "ERR: SEM_RESPOSTA_DO_SHELL" : result.trim();
        } catch (Exception e) {
            return "ERR: " + e.getMessage();
        } finally {
            if (tmpBin != null) tmpBin.delete();
            if (tmpJson != null) tmpJson.delete();
        }
    }
}

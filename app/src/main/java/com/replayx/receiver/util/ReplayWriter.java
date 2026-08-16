package com.replayx.receiver.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Escreve o replay recebido dentro da pasta do Free Fire escolhido (MAX ou
 * Normal) no celular, via Shizuku/root — reescreve Version/GameVersion/AppId
 * no JSON pra ficar compatível com a versão instalada e o jogo de destino.
 */
public final class ReplayWriter {
    private ReplayWriter() {}

    public static final String FFM_PKG = "com.dts.freefiremax";
    public static final String FFN_PKG = "com.dts.freefireth";

    public interface Log {
        void onLog(String msg);
    }

    private static String installedVersion(Context ctx, String pkg) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, 0);
            String v = pi.versionName;
            return (v == null || v.trim().isEmpty()) ? null : v.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static final String[] BASES = {
        "/storage/emulated/0",
        "/sdcard",
        "/data/media/0",
        "/mnt/user/0",
        "/storage/self/primary"
    };

    /** @return "COPIADO_OK" em sucesso (confirmado de verdade), ou uma mensagem de erro. */
    public static String writeToGame(Context ctx, byte[] binData, byte[] jsonData,
                                      String binName, String jsonName, String targetPkg, Log log) {
        File tmpBin = null;
        File tmpJson = null;
        try {
            // IMPORTANTE: grava na pasta EXTERNA do próprio app (getExternalFilesDir),
            // não no cache interno — Shizuku (sem root) não consegue ler o cache
            // interno de outro processo, só a área externa (mesma área onde o
            // Free Fire guarda os replays dele).
            File tmpDir = ctx.getExternalFilesDir(null);
            if (tmpDir == null) tmpDir = ctx.getCacheDir();
            tmpDir.mkdirs();

            tmpBin = new File(tmpDir, "recv_" + System.currentTimeMillis() + ".bin");
            try (FileOutputStream fos = new FileOutputStream(tmpBin)) { fos.write(binData); }

            if (jsonData != null && jsonData.length > 0) {
                tmpJson = new File(tmpDir, "recv_" + System.currentTimeMillis() + ".json");
                try (FileOutputStream fos = new FileOutputStream(tmpJson)) { fos.write(jsonData); }
            }

            String version = installedVersion(ctx, targetPkg);
            if (version == null) version = targetPkg.equals(FFM_PKG) ? "2.126.1" : "1.129.1";

            if (binName == null || binName.trim().isEmpty()) binName = "replay.bin";
            if (jsonName == null) jsonName = "";

            // Acha a pasta de destino de verdade (testa cada candidato, igual o leitor do Enviador)
            String dst = null;
            for (String base : BASES) {
                String dir = base + "/Android/data/" + targetPkg + "/files/MReplays";
                String r = RootShell.run("mkdir -p \"" + dir + "\" 2>/dev/null && [ -d \"" + dir + "\" ] && echo EXISTE || echo NAO_EXISTE");
                log.onLog("[..] testando destino " + dir + " -> " + (r == null ? "SEM_RESPOSTA" : r.trim()));
                if (r != null && r.contains("EXISTE")) { dst = dir; break; }
            }
            if (dst == null) {
                return "ERR: NAO_FOI_POSSIVEL_CRIAR_PASTA_DESTINO (Free Fire " + targetPkg + " instalado?)";
            }

            StringBuilder cmd = new StringBuilder();
            cmd.append("rm -f \"").append(dst).append("\"/*.bin \"").append(dst).append("\"/*.json 2>/dev/null; ");
            cmd.append("cp -f \"").append(tmpBin.getAbsolutePath()).append("\" \"").append(dst).append("/").append(binName).append("\"; ");
            cmd.append("chmod 666 \"").append(dst).append("/").append(binName).append("\" 2>/dev/null; ");

            if (tmpJson != null) {
                cmd.append("cp -f \"").append(tmpJson.getAbsolutePath()).append("\" \"").append(dst).append("/").append(jsonName).append("\"; ");
                cmd.append("chmod 666 \"").append(dst).append("/").append(jsonName).append("\" 2>/dev/null; ");
                cmd.append("sed -i 's/\"Version\":\"[^\"]*\"/\"Version\":\"").append(version).append("\"/g' \"").append(dst).append("/").append(jsonName).append("\" 2>/dev/null; ");
                cmd.append("sed -i 's/\"GameVersion\":\"[^\"]*\"/\"GameVersion\":\"").append(version).append("\"/g' \"").append(dst).append("/").append(jsonName).append("\" 2>/dev/null; ");
                cmd.append("sed -i 's/\"AppId\":\"[^\"]*\"/\"AppId\":\"").append(targetPkg).append("\"/g' \"").append(dst).append("/").append(jsonName).append("\" 2>/dev/null; ");
            }

            // Corrige o rotulo de seguranca (SELinux) do arquivo pra ele ficar
            // igual aos arquivos que o proprio Free Fire cria — sem isso o jogo
            // pode nao reconhecer o arquivo como valido e apagar ele sozinho ao
            // escanear a pasta.
            cmd.append("restorecon -F \"").append(dst).append("/").append(binName).append("\" 2>/dev/null; ");
            if (tmpJson != null) {
                cmd.append("restorecon -F \"").append(dst).append("/").append(jsonName).append("\" 2>/dev/null; ");
            }
            cmd.append("restorecon -F \"").append(dst).append("\" 2>/dev/null; ");

            cmd.append("am force-stop ").append(targetPkg).append(" 2>/dev/null; ");

            // Confirma de VERDADE que o arquivo apareceu no destino, com tamanho > 0,
            // em vez de confiar cegamente que o cp não deu erro.
            cmd.append("SZ=$(wc -c < \"").append(dst).append("/").append(binName).append("\" 2>/dev/null); ");
            cmd.append("if [ -f \"").append(dst).append("/").append(binName).append("\" ] && [ \"$SZ\" -gt 0 ]; then echo COPIADO_OK; else echo CP_VERIFY_FAIL; fi");

            log.onLog("[..] copiando pra " + dst);
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

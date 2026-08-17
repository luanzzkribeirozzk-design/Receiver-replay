package com.replayx.receiver.util;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

/** Busca transferências pendentes pra esse aparelho e baixa/remonta os arquivos. */
public final class TransferDownloader {
    private TransferDownloader() {}

    public static class Pending {
        public String transferId;
        public String sourcePkg;
        public String sourceVersion;
        public String replayVersion;
        public String binName;
        public String jsonName;
        public int totalChunksBin;
        public int totalChunksJson;
    }

    /** Devolve a transferência pendente mais recente pra esse Receptor, ou null. */
    public static Pending findPending(Context ctx) {
        try {
            String myId = DeviceId.get(ctx);
            JSONArray results = Fs.query("transfers", "receiverId", myId, 20);
            Pending best = null;
            long bestTs = -1;
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                if (!item.has("document")) continue;
                JSONObject doc = item.getJSONObject("document");
                JSONObject fields = doc.getJSONObject("fields");
                String status = Fs.getStr(fields, "status", "");
                if (!"pending".equals(status)) continue;
                Long createdAt = Fs.getTsSec(fields, "createdAt");
                long ts = createdAt != null ? createdAt : 0L;
                if (ts > bestTs) {
                    bestTs = ts;
                    Pending p = new Pending();
                    p.transferId = Fs.docIdFromName(doc.getString("name"));
                    p.sourcePkg = Fs.getStr(fields, "sourcePkg", "");
                    p.sourceVersion = Fs.getStr(fields, "sourceVersion", "");
                    p.replayVersion = Fs.getStr(fields, "replayVersion", "");
                    p.binName = Fs.getStr(fields, "binName", "replay.bin");
                    p.jsonName = Fs.getStr(fields, "jsonName", "");
                    p.totalChunksBin = (int) Fs.getLong(fields, "totalChunksBin", 0);
                    p.totalChunksJson = (int) Fs.getLong(fields, "totalChunksJson", 0);
                    best = p;
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    public static class Downloaded {
        public byte[] binData;
        public byte[] jsonData;
    }

    public static Downloaded download(Pending pend, TransferUploaderLog log) {
        try {
            StringBuilder binB64 = new StringBuilder();
            for (int i = 0; i < pend.totalChunksBin; i++) {
                JSONObject f = Fs.getDoc("transfers/" + pend.transferId + "/chunks_bin/c" + i);
                if (f == null) throw new Exception("FALHA_BAIXAR_BIN_" + i);
                binB64.append(Fs.getStr(f, "data", ""));
                if (log != null) log.onLog("[..] baixando replay " + (i + 1) + "/" + pend.totalChunksBin);
            }
            StringBuilder jsonB64 = new StringBuilder();
            for (int i = 0; i < pend.totalChunksJson; i++) {
                JSONObject f = Fs.getDoc("transfers/" + pend.transferId + "/chunks_json/c" + i);
                if (f == null) continue;
                jsonB64.append(Fs.getStr(f, "data", ""));
            }
            Downloaded d = new Downloaded();
            d.binData = Base64.decode(binB64.toString(), Base64.NO_WRAP);
            d.jsonData = jsonB64.length() > 0 ? Base64.decode(jsonB64.toString(), Base64.NO_WRAP) : null;
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    public static void markCopied(String transferId) {
        try {
            JSONObject f = new JSONObject().put("status", Fs.str("copied"));
            Fs.patchDoc("transfers/" + transferId, f, "updateMask.fieldPaths=status");
        } catch (Exception ignored) {}
    }

    public static void markDismissed(String transferId) {
        try {
            JSONObject f = new JSONObject().put("status", Fs.str("dismissed"));
            Fs.patchDoc("transfers/" + transferId, f, "updateMask.fieldPaths=status");
        } catch (Exception ignored) {}
    }

    public interface TransferUploaderLog {
        void onLog(String msg);
    }
}

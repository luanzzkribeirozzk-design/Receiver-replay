package com.replayx.receiver.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.replayx.receiver.R
import com.replayx.receiver.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvShellStatus: android.widget.TextView
    private lateinit var tvLog: android.widget.TextView
    private lateinit var scrollLog: android.widget.ScrollView
    private lateinit var overlayAguarde: View
    private lateinit var tvAguarde: android.widget.TextView

    private lateinit var secPermissao: View
    private lateinit var secParear: View
    private lateinit var secReplays: View

    private lateinit var etCodigo: android.widget.EditText
    private lateinit var boxPareado: View
    private lateinit var tvSemReplay: android.widget.TextView

    private val SHIZUKU_CODE = 3001
    private val binderReceived = Shizuku.OnBinderReceivedListener { checarAcesso() }
    private val binderDead = Shizuku.OnBinderDeadListener { checarAcesso() }
    private var dialogAberto = false

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)

        if (!com.replayx.receiver.security.IntegrityCheck.isValid(this)) {
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        tvShellStatus = findViewById(R.id.tvShellStatus)
        tvLog = findViewById(R.id.tvLog)
        scrollLog = findViewById(R.id.scrollLog)
        overlayAguarde = findViewById(R.id.overlayAguarde)
        tvAguarde = findViewById(R.id.tvAguarde)

        secPermissao = findViewById(R.id.secPermissao)
        secParear = findViewById(R.id.secParear)
        secReplays = findViewById(R.id.secReplays)

        etCodigo = findViewById(R.id.etCodigo)
        boxPareado = findViewById(R.id.boxPareado)
        tvSemReplay = findViewById(R.id.tvSemReplay)

        findViewById<View>(R.id.tabPermissao).setOnClickListener { showTab(0) }
        findViewById<View>(R.id.tabParear).setOnClickListener { showTab(1) }
        findViewById<View>(R.id.tabReplays).setOnClickListener { showTab(2) }

        findViewById<View>(R.id.btnAbrirShizuku).setOnClickListener { abrirShizuku() }
        findViewById<View>(R.id.btnParear).setOnClickListener { parear() }
        findViewById<View>(R.id.btnDesparearRecv).setOnClickListener { desparear() }
        findViewById<View>(R.id.btnVerificarReplay).setOnClickListener { verificarReplayPendente(manual = true) }

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
        } catch (ignored: Exception) {}

        showTab(if (PairingManager.getPairedSenderId(this).isEmpty()) 1 else 2)
        atualizarBoxPareado()
        checarAcesso()
    }

    override fun onResume() {
        super.onResume()
        PairingManager.refreshBattery(this)
        // Confere sozinho se tem replay esperando assim que o app abre/volta ao primeiro plano
        verificarReplayPendente(manual = false)
    }

    private fun showTab(i: Int) {
        secPermissao.visibility = if (i == 0) View.VISIBLE else View.GONE
        secParear.visibility = if (i == 1) View.VISIBLE else View.GONE
        secReplays.visibility = if (i == 2) View.VISIBLE else View.GONE
    }

    private fun checarAcesso() {
        lifecycleScope.launch {
            val root = withContext(Dispatchers.IO) { RootShell.hasRoot() }
            val shizuku = withContext(Dispatchers.IO) { RootShell.hasShizuku() }
            runOnUiThread {
                when {
                    root -> { tvShellStatus.text = "● ACESSO ROOT ATIVO"; tvShellStatus.setTextColor(0xFF33CC55.toInt()) }
                    shizuku -> { tvShellStatus.text = "● SHIZUKU ATIVO"; tvShellStatus.setTextColor(0xFF33CC55.toInt()) }
                    else -> { tvShellStatus.text = "● SEM ACESSO (root/Shizuku)"; tvShellStatus.setTextColor(0xFFFF4444.toInt()) }
                }
            }
        }
    }

    private fun abrirShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) startActivity(intent)
                else log("[ERR] Instale o app Shizuku primeiro")
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_CODE)
            } else {
                log("[OK] Shizuku já está ativo e permitido")
            }
        } catch (e: Exception) {
            log("[ERR] " + e.message)
        }
    }

    private fun parear() {
        val code = etCodigo.text.toString().trim()
        if (code.isEmpty()) { log("[ERR] Digite o código"); return }
        lifecycleScope.launch {
            log("[..] pareando com código $code...")
            val result = withContext(Dispatchers.IO) { PairingManager.pairWithCode(this@MainActivity, code) }
            if (result == "OK") {
                log("[OK] pareado com sucesso")
                atualizarBoxPareado()
                showTab(2)
                verificarReplayPendente(manual = false)
            } else {
                log("[ERR] $result")
            }
        }
    }

    private fun atualizarBoxPareado() {
        val paired = PairingManager.getPairedSenderId(this).isNotEmpty()
        boxPareado.visibility = if (paired) View.VISIBLE else View.GONE
    }

    private fun desparear() {
        PairingManager.unpairLocal(this)
        atualizarBoxPareado()
        log("[OK] despareado")
    }

    private fun verificarReplayPendente(manual: Boolean) {
        val senderId = PairingManager.getPairedSenderId(this)
        if (senderId.isEmpty()) {
            if (manual) log("[ERR] Pareie um dispositivo primeiro")
            return
        }
        if (dialogAberto) return
        lifecycleScope.launch {
            val pend = withContext(Dispatchers.IO) { TransferDownloader.findPending(this@MainActivity) }
            if (pend == null) {
                if (manual) {
                    tvSemReplay.visibility = View.VISIBLE
                    log("[..] nenhum replay pendente")
                }
                return@launch
            }
            tvSemReplay.visibility = View.GONE
            perguntarCopiar(pend)
        }
    }

    private fun perguntarCopiar(pend: TransferDownloader.Pending) {
        dialogAberto = true
        AlertDialog.Builder(this)
            .setTitle("Replay recebido")
            .setMessage("Chegou um replay novo do PC. Copiar replay?")
            .setCancelable(false)
            .setPositiveButton("Copiar replay") { _, _ -> perguntarJogo(pend) }
            .setNegativeButton("Não") { _, _ -> dialogAberto = false }
            .show()
    }

    private fun perguntarJogo(pend: TransferDownloader.Pending) {
        AlertDialog.Builder(this)
            .setTitle("Copiar para qual jogo?")
            .setItems(arrayOf("FF MAX", "FF Normal")) { _, which ->
                val targetPkg = if (which == 0) ReplayWriter.FFM_PKG else ReplayWriter.FFN_PKG
                dialogAberto = false
                processarCopia(pend, targetPkg)
            }
            .setOnCancelListener { dialogAberto = false }
            .show()
    }

    private fun processarCopia(pend: TransferDownloader.Pending, targetPkg: String) {
        showTab(2)
        overlayAguarde.visibility = View.VISIBLE
        tvAguarde.text = "AGUARDE, COPIANDO REPLAY..."
        val startMs = System.currentTimeMillis()
        lifecycleScope.launch {
            log("--------------------------------")
            log("[SYS] >> Baixando replay recebido")
            val down = withContext(Dispatchers.IO) {
                TransferDownloader.download(pend) { msg -> lifecycleScope.launch(Dispatchers.Main) { log(msg) } }
            }
            if (down == null) {
                log("[ERR] FALHA_AO_BAIXAR_REPLAY")
                overlayAguarde.visibility = View.GONE
                return@launch
            }
            log("[OK] replay baixado, copiando pro jogo...")
            val result = withContext(Dispatchers.IO) {
                ReplayWriter.writeToGame(this@MainActivity, down.binData, down.jsonData, pend.binName, pend.jsonName, targetPkg)
            }
            val ok = result.contains("COPIADO_OK")
            if (ok) {
                withContext(Dispatchers.IO) { TransferDownloader.markCopied(pend.transferId) }
            }
            val elapsed = (System.currentTimeMillis() - startMs) / 1000.0
            log(if (ok) "[OK] Replay copiado com sucesso" else "[ERR] $result")
            log("Concluído em %.1fs".format(elapsed))
            log("--------------------------------")
            overlayAguarde.visibility = View.GONE
        }
    }

    private fun log(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val cur = tvLog.text.toString()
        val sep = System.lineSeparator()
        tvLog.text = if (cur.isEmpty()) "[$t] $msg" else "$cur$sep[$t] $msg"
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
        } catch (ignored: Exception) {}
    }
}

package com.replayx.receiver.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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

    private lateinit var tabPermissao: android.widget.Button
    private lateinit var tabParear: android.widget.Button
    private lateinit var tabReplays: android.widget.Button

    private lateinit var etCodigo: android.widget.EditText
    private lateinit var boxPareado: View
    private lateinit var tvSemReplay: android.widget.TextView

    private val SHIZUKU_CODE = 3001
    private val STORAGE_CODE = 3002
    private val binderReceived = Shizuku.OnBinderReceivedListener { checarAcesso() }
    private val binderDead = Shizuku.OnBinderDeadListener { checarAcesso() }
    private var dialogAberto = false

    override fun onCreate(savedInstanceState: Bundle?) {
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

        tabPermissao = findViewById(R.id.tabPermissao)
        tabParear = findViewById(R.id.tabParear)
        tabReplays = findViewById(R.id.tabReplays)

        etCodigo = findViewById(R.id.etCodigo)
        boxPareado = findViewById(R.id.boxPareado)
        tvSemReplay = findViewById(R.id.tvSemReplay)

        tabPermissao.setOnClickListener { showTab(0) }
        tabParear.setOnClickListener { showTab(1) }
        tabReplays.setOnClickListener { showTab(2) }

        findViewById<View>(R.id.btnAbrirShizuku).setOnClickListener { abrirShizuku() }
        findViewById<View>(R.id.btnSolicitarArquivos).setOnClickListener { solicitarArquivos() }
        findViewById<View>(R.id.btnParear).setOnClickListener { parear() }
        findViewById<View>(R.id.btnDesparearRecv).setOnClickListener { desparear() }
        findViewById<View>(R.id.btnVerificarReplay).setOnClickListener { verificarReplayPendente(manual = true) }
        findViewById<View>(R.id.btnLimparLogs).setOnClickListener { tvLog.text = "" }
        findViewById<View>(R.id.btnCopiarLogs).setOnClickListener { copiarLogs() }
        findViewById<View>(R.id.tvTitulo).setOnLongClickListener { rodarDiagnostico(); true }

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
        verificarReplayPendente(manual = false)
    }

    private fun showTab(i: Int) {
        val secs = listOf(secPermissao, secParear, secReplays)
        secs.forEachIndexed { idx, v ->
            if (idx == i) {
                v.visibility = View.VISIBLE
                v.alpha = 0f
                v.animate().alpha(1f).setDuration(280).start()
            } else {
                v.visibility = View.GONE
            }
        }

        val tabs = listOf(tabPermissao, tabParear, tabReplays)
        tabs.forEachIndexed { idx, btn ->
            if (idx == i) {
                btn.setBackgroundResource(R.drawable.ios_tab_selected)
                btn.setTextColor(0xFFFFFFFF.toInt())
            } else {
                btn.setBackgroundColor(0x00000000)
                btn.setTextColor(0xFF8E8E93.toInt())
            }
        }
    }

    private fun checarAcesso() {
        lifecycleScope.launch {
            val root = withContext(Dispatchers.IO) { RootShell.hasRoot() }
            val shizuku = withContext(Dispatchers.IO) { RootShell.hasShizuku() }
            runOnUiThread {
                when {
                    root -> { tvShellStatus.text = "● Acesso root ativo"; tvShellStatus.setTextColor(0xFF34C759.toInt()) }
                    shizuku -> { tvShellStatus.text = "● Shizuku ativo"; tvShellStatus.setTextColor(0xFF34C759.toInt()) }
                    else -> { tvShellStatus.text = "● Sem acesso (root/Shizuku)"; tvShellStatus.setTextColor(0xFFFF453A.toInt()) }
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

    private fun solicitarArquivos() {
        if (Build.VERSION.SDK_INT <= 32) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_CODE
            )
        } else {
            log("[OK] Nesse Android a permissão de arquivos é controlada por root/Shizuku, não precisa de permissão separada")
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
        val origem = when (pend.sourcePkg) {
            ReplayWriter.FFM_PKG -> "Free Fire MAX"
            ReplayWriter.FFN_PKG -> "Free Fire Normal"
            else -> "variante não identificada"
        }
        AlertDialog.Builder(this)
            .setTitle("Replay recebido")
            .setMessage("Chegou um replay novo do PC. Origem detectada: $origem. Copiar replay?")
            .setCancelable(false)
            .setPositiveButton("Copiar replay") { _, _ -> perguntarJogo(pend) }
            .setNegativeButton("Não") { _, _ ->
                dialogAberto = false
                lifecycleScope.launch(Dispatchers.IO) { TransferDownloader.markDismissed(pend.transferId) }
            }
            .show()
    }

    private fun perguntarJogo(pend: TransferDownloader.Pending) {
        val origem = when (pend.sourcePkg) {
            ReplayWriter.FFM_PKG -> "Origem detectada: Free Fire MAX. Você ainda pode escolher outra opção."
            ReplayWriter.FFN_PKG -> "Origem detectada: Free Fire Normal. Você ainda pode escolher outra opção."
            else -> "A origem não foi identificada. Escolha onde deseja copiar."
        }
        AlertDialog.Builder(this)
            .setTitle("Onde copiar o replay?")
            .setMessage(origem)
            .setItems(arrayOf("Free Fire MAX", "Free Fire Normal", "Copiar para os dois")) { _, which ->
                val targets = when (which) {
                    0 -> listOf(ReplayWriter.FFM_PKG)
                    1 -> listOf(ReplayWriter.FFN_PKG)
                    else -> listOf(ReplayWriter.FFM_PKG, ReplayWriter.FFN_PKG)
                }
                dialogAberto = false
                processarCopias(pend, targets)
            }
            .setOnCancelListener { dialogAberto = false }
            .show()
    }

    private fun processarCopias(pend: TransferDownloader.Pending, targets: List<String>) {
        showTab(2)
        overlayAguarde.visibility = View.VISIBLE
        tvAguarde.text = "Copiando replay…"
        val startMs = System.currentTimeMillis()
        lifecycleScope.launch {
            log("--------------------------------")
            log("[SYS] >> Baixando replay recebido")
            val down = withContext(Dispatchers.IO) {
                TransferDownloader.download(pend) { msg -> lifecycleScope.launch(Dispatchers.Main) { log(msg) } }
            }
            if (down == null) {
                log("[ERR] Falha ao baixar replay")
                overlayAguarde.visibility = View.GONE
                return@launch
            }
            log("[OK] replay baixado; destinos selecionados: ${targets.size}")
            var copied = 0
            for (targetPkg in targets) {
                val label = if (targetPkg == ReplayWriter.FFM_PKG) "Free Fire MAX" else "Free Fire Normal"
                log("[..] tentando copiar para $label")
                val result = withContext(Dispatchers.IO) {
                    ReplayWriter.writeToGame(this@MainActivity, down.binData, down.jsonData, pend.binName, pend.jsonName, targetPkg) { msg ->
                        lifecycleScope.launch(Dispatchers.Main) { log(msg) }
                    }
                }
                if (result.contains("COPIADO_OK")) {
                    copied++
                    log("[OK] replay copiado para $label")
                } else {
                    log("[ERR] $label: $result")
                }
            }
            if (copied > 0) {
                withContext(Dispatchers.IO) { TransferDownloader.markCopied(pend.transferId) }
                log("[OK] concluído: $copied/${targets.size} destino(s) copiado(s)")
            } else {
                log("[ERR] nenhum destino foi copiado; a transferência continua pendente para tentar novamente")
            }
            val elapsed = (System.currentTimeMillis() - startMs) / 1000.0
            log("Concluído em %.1fs".format(elapsed))
            log("--------------------------------")
            overlayAguarde.visibility = View.GONE
        }
    }

    private fun copiarLogs() {
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("logs", tvLog.text.toString()))
        android.widget.Toast.makeText(this, "Logs copiados", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun rodarDiagnostico() {
        log("[SYS] >> Rodando diagnóstico (toque longo detectado)...")
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                DiagDump.run { msg -> lifecycleScope.launch(Dispatchers.Main) { log(msg) } }
            }
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

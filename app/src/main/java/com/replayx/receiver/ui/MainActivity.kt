package com.replayx.receiver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
    private val CAMERA_CODE = 3003
    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            log("Aguardando")
        } else {
            val code = contents.trim().replace(Regex("[^A-Za-z0-9]"), "").uppercase(Locale.ROOT).take(6)
            if (code.length != 6) {
                log("[ERR] QR Code inválido")
                android.widget.Toast.makeText(this, "QR Code do ReplayX inválido", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                etCodigo.setText(code)
                etCodigo.setSelection(etCodigo.text.length)
                log("Pareado")
                parear()
            }
        }
    }
    private val binderReceived = Shizuku.OnBinderReceivedListener { checarAcesso() }
    private val binderDead = Shizuku.OnBinderDeadListener { checarAcesso() }
    private var dialogAberto = false
    private var licenseTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!com.replayx.receiver.security.SecurityGate.allow(this)) {
            redirectToLogin()
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

        findViewById<View>(R.id.btnAbrirShizuku).setOnClickListener { selecionarAcesso() }
        findViewById<View>(R.id.btnSolicitarArquivos).setOnClickListener { solicitarArquivos() }
        findViewById<View>(R.id.btnColarCodigo).setOnClickListener { colarCodigo() }
        findViewById<View>(R.id.btnLerQr).setOnClickListener { lerQrCode() }
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
        atualizarPainelLicenca()
        startLicenseTimer()
    }

    override fun onResume() {
        super.onResume()
        if (!com.replayx.receiver.security.SecurityGate.allow(this)) {
            redirectToLogin()
            return
        }
        PairingManager.refreshBattery(this)
        verificarReplayPendente(manual = false)
        startLicenseTimer()
    }

    private fun startLicenseTimer() {
        val timerView = findViewById<android.widget.TextView>(R.id.tvLicenseTimer)
        val remaining = com.replayx.receiver.security.LicenseManager.remainingMs(this)
        licenseTimer?.cancel()
        if (remaining == Long.MAX_VALUE) {
            atualizarPainelLicenca()
            return
        }
        licenseTimer = object : CountDownTimer(remaining.coerceAtLeast(0L), 1000L) {
            override fun onTick(ms: Long) {
                timerView.text = "Validade: ${formatLicenseTime(ms)}"
                timerView.setTextColor(when {
                    ms < 86400000L -> 0xFFFF453A.toInt()
                    ms < 259200000L -> 0xFFFFD60A.toInt()
                    else -> 0xFF34C759.toInt()
                })
            }

            override fun onFinish() {
                com.replayx.receiver.security.LicenseManager.clear(this@MainActivity)
                val intent = Intent(this@MainActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }.start()
    }

    private fun formatLicenseTime(ms: Long): String {
        val seconds = ms / 1000L
        val days = seconds / 86400L
        val hours = (seconds % 86400L) / 3600L
        val minutes = (seconds % 3600L) / 60L
        val secs = seconds % 60L
        return String.format(Locale.ROOT, "%02dd %02dh %02dm %02ds", days, hours, minutes, secs)
    }

    private fun atualizarPainelLicenca() {
        val count = com.replayx.receiver.security.LicenseManager.savedDeviceCount(this)
        findViewById<android.widget.TextView>(R.id.tvLicenseTimer).text = "Validade: permanente"
        findViewById<android.widget.TextView>(R.id.tvLicenseUser).text = "Dispositivos: $count/2"
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

    private var modoAcesso = "AUTO"

    private fun selecionarAcesso() {
        val opcoes = arrayOf("ADB (via PC)", "Shizuku", "Root")
        val selecionado = when (modoAcesso) { "ADB" -> 0; "SHIZUKU" -> 1; "ROOT" -> 2; else -> -1 }
        android.app.AlertDialog.Builder(this)
            .setTitle("Método de acesso")
            .setSingleChoiceItems(opcoes, selecionado) { dialog, which ->
                modoAcesso = when (which) { 0 -> "ADB"; 1 -> "SHIZUKU"; else -> "ROOT" }
                dialog.dismiss()
                when (modoAcesso) {
                    "ADB" -> {
                        log("Aguardando ADB pelo PC")
                        android.widget.Toast.makeText(this, "ADB é conectado pelo PC", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    "SHIZUKU" -> { abrirShizuku(); checarAcesso() }
                    else -> checarAcesso()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checarAcesso() {
        lifecycleScope.launch {
            val root = withContext(Dispatchers.IO) { RootShell.hasRoot() }
            val shizuku = withContext(Dispatchers.IO) { RootShell.hasShizuku() }
            runOnUiThread {
                when (modoAcesso) {
                    "ADB" -> { tvShellStatus.text = "● ADB selecionado"; tvShellStatus.setTextColor(0xFFFFD60A.toInt()) }
                    "SHIZUKU" -> {
                        tvShellStatus.text = if (shizuku) "● Shizuku ativo" else "● Shizuku aguardando"
                        tvShellStatus.setTextColor(if (shizuku) 0xFF34C759.toInt() else 0xFFFFD60A.toInt())
                        log(if (shizuku) "Concluído" else "Aguardando Shizuku")
                    }
                    "ROOT" -> {
                        tvShellStatus.text = if (root) "● Root ativo" else "● Root aguardando"
                        tvShellStatus.setTextColor(if (root) 0xFF34C759.toInt() else 0xFFFFD60A.toInt())
                        log(if (root) "Concluído" else "Aguardando Root")
                    }
                    else -> {
                        tvShellStatus.text = when { root -> "● Root ativo"; shizuku -> "● Shizuku ativo"; else -> "● Escolha um acesso" }
                        tvShellStatus.setTextColor(if (root || shizuku) 0xFF34C759.toInt() else 0xFFFFD60A.toInt())
                    }
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

    private fun lerQrCode() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_CODE)
            return
        }
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Aponte a câmera para o QR Code do Sender")
            setBeepEnabled(false)
            setCameraId(0)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        }
        qrLauncher.launch(options)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_CODE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            lerQrCode()
        } else if (requestCode == CAMERA_CODE) {
            log("[ERR] Permissão da câmera negada")
        }
    }

    private fun colarCodigo() {
        try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip
            val raw = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
            val code = raw.trim().replace(Regex("[^A-Za-z0-9]"), "").uppercase(java.util.Locale.ROOT).take(6)
            if (code.isEmpty()) {
                log("[ERR] A área de transferência não contém um código válido")
                android.widget.Toast.makeText(this, "Nenhum código válido na área de transferência", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            etCodigo.setText(code)
            etCodigo.setSelection(etCodigo.text.length)
            log("[OK] código colado: $code")
            android.widget.Toast.makeText(this, "Código colado", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            log("[ERR] Falha ao colar código: ${e.message}")
        }
    }

    private fun parear() {
        if (!com.replayx.receiver.security.SecurityGate.allow(this)) {
            redirectToLogin()
            return
        }
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
        if (!com.replayx.receiver.security.SecurityGate.allow(this)) {
            redirectToLogin()
            return
        }
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
        val dialog = AlertDialog.Builder(this)
            .setTitle("Onde copiar o replay?")
            .setMessage(origem)
            .setPositiveButton("Free Fire MAX", null)
            .setNegativeButton("Free Fire Normal", null)
            .setNeutralButton("Copiar para os dois", null)
            .setOnCancelListener { dialogAberto = false }
            .create()

        dialog.setOnShowListener {
            val dark = 0xFF111111.toInt()
            dialog.window?.setBackgroundDrawableResource(android.R.color.white)
            dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextColor(dark)
            val titleId = resources.getIdentifier("alertTitle", "id", "android")
            dialog.findViewById<android.widget.TextView>(titleId)?.setTextColor(dark)

            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).apply {
                setTextColor(dark)
                setOnClickListener {
                    dialog.dismiss()
                    dialogAberto = false
                    processarCopias(pend, listOf(ReplayWriter.FFM_PKG))
                }
            }
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).apply {
                setTextColor(dark)
                setOnClickListener {
                    dialog.dismiss()
                    dialogAberto = false
                    processarCopias(pend, listOf(ReplayWriter.FFN_PKG))
                }
            }
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).apply {
                setTextColor(dark)
                setOnClickListener {
                    dialog.dismiss()
                    dialogAberto = false
                    processarCopias(pend, listOf(ReplayWriter.FFM_PKG, ReplayWriter.FFN_PKG))
                }
            }
        }
        dialog.show()
    }

    private fun processarCopias(pend: TransferDownloader.Pending, targets: List<String>) {
        if (!com.replayx.receiver.security.SecurityGate.allow(this)) {
            redirectToLogin()
            return
        }
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
                    ReplayWriter.writeToGame(this@MainActivity, down.binData, down.jsonData, pend.binName, pend.jsonName, targetPkg,
                        pend.sourceVersion, pend.replayVersion) { msg ->
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

    private fun redirectToLogin() {
        licenseTimer?.cancel()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
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
        val estado = when {
            msg.contains("ERR", ignoreCase = true) || msg.contains("falha", ignoreCase = true) || msg.contains("erro", ignoreCase = true) -> "Erro"
            msg.contains("baix", ignoreCase = true) || msg.contains("copi", ignoreCase = true) -> "Copiando"
            msg.contains("paread", ignoreCase = true) || msg.contains("conect", ignoreCase = true) -> "Pareado"
            msg.contains("aguard", ignoreCase = true) || msg.contains("baixando", ignoreCase = true) -> "Aguardando"
            else -> "Concluído"
        }
        tvLog.text = estado
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        licenseTimer?.cancel()
        super.onDestroy()
        try {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
        } catch (ignored: Exception) {}
    }
}

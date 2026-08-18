package com.replayx.receiver.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.replayx.receiver.R
import com.replayx.receiver.security.IntegrityCheck
import com.replayx.receiver.security.LicenseManager

class LoginActivity : AppCompatActivity() {
    private lateinit var etKey: EditText
    private lateinit var btnLogin: android.widget.Button
    private lateinit var tvError: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var splash: View
    private lateinit var switchRemember: SwitchMaterial
    private lateinit var switchHide: SwitchMaterial
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (!IntegrityCheck.isValid(this)) {
            finish()
            return
        }
        setContentView(R.layout.activity_login)
        etKey = findViewById(R.id.etKey)
        btnLogin = findViewById(R.id.btnLogin)
        tvError = findViewById(R.id.tvError)
        progressBar = findViewById(R.id.progressBar)
        splash = findViewById(R.id.splashScreen)
        switchRemember = findViewById(R.id.switchRemember)
        switchHide = findViewById(R.id.switchHideStreamLogin)

        switchRemember.isChecked = true
        switchRemember.isEnabled = false
        switchHide.setOnCheckedChangeListener { _, checked -> applySecureWindow(checked) }
        btnLogin.setOnClickListener { login() }
        etKey.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { login(); true } else false
        }

        if (LicenseManager.hasLocalLicense(this)) {
            goMain()
        } else {
            splash.animate().alpha(0f).setDuration(220).withEndAction { splash.visibility = View.GONE }.start()
            setLoading(false)
        }
    }

    private fun login() {
        val key = etKey.text.toString().trim()
        setLoading(true)
        setStatus("Validando acesso...", 0xFFFFD60A.toInt())
        if (LicenseManager.unlock(this, key)) {
            setStatus("Acesso liberado", 0xFF34C759.toInt())
            goMain()
        } else {
            setLoading(false)
            setStatus("Chave inválida", 0xFFFF5555.toInt())
        }
    }

    private fun goMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun applySecureWindow(active: Boolean) {
        if (active) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
    }

    private fun setStatus(text: String, color: Int) {
        tvError.text = text
        tvError.setTextColor(color)
        tvError.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacksAndMessages(null)
    }
}

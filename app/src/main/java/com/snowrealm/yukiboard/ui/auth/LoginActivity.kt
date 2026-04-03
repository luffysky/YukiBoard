package com.snowrealm.yukiboard.ui.auth

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SnowRealm 帳號登入頁面。
 *
 * 三種登入方式：Email / LINE Login / Google
 * 登入成功 → finish + setResult(RESULT_OK)
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var authClient: SnowRealmAuthClient
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var statusText: TextView
    private lateinit var loginBtn: Button
    private lateinit var registerBtn: Button
    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authClient = SnowRealmAuthClient(this)

        val root = buildUI()
        setContentView(root)

        if (authClient.isLoggedIn) {
            statusText.text = "已登入: ${authClient.currentDisplayName ?: authClient.currentEmail}"
            loginBtn.text = "登出"
            loginBtn.setOnClickListener { authClient.logout(); finish() }
        }
    }

    private fun buildUI(): View {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad * 2, pad * 3, pad * 2, pad * 2)
        }

        // 標題
        root.addView(TextView(this).apply {
            text = "❄️ SnowRealm 帳號"
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad)
        })

        statusText = TextView(this).apply {
            textSize = 13f; setTextColor(0xFFBBBBBB.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(statusText)

        // 名稱（註冊模式才顯示）
        nameInput = EditText(this).apply {
            hint = "顯示名稱"; visibility = View.GONE
            setTextColor(Color.WHITE); setHintTextColor(0xFF888888.toInt())
        }
        root.addView(nameInput)

        emailInput = EditText(this).apply {
            hint = "Email"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(Color.WHITE); setHintTextColor(0xFF888888.toInt())
        }
        root.addView(emailInput)

        passwordInput = EditText(this).apply {
            hint = "密碼"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE); setHintTextColor(0xFF888888.toInt())
        }
        root.addView(passwordInput)

        // 登入按鈕
        loginBtn = Button(this).apply {
            text = "登入"
            setOnClickListener { doLogin() }
        }
        root.addView(loginBtn)

        // 註冊切換
        registerBtn = Button(this).apply {
            text = "沒有帳號？註冊"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(0xFF80D8FF.toInt())
            setOnClickListener { toggleRegisterMode() }
        }
        root.addView(registerBtn)

        // OAuth 按鈕
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()
            ).apply { topMargin = pad; bottomMargin = pad }
            setBackgroundColor(0xFF333333.toInt())
        })

        root.addView(Button(this).apply {
            text = "LINE 登入"
            setOnClickListener { statusText.text = "LINE Login — 即將支援" }
        })

        root.addView(Button(this).apply {
            text = "Google 登入"
            setOnClickListener { statusText.text = "Google Login — 即將支援" }
        })

        // 跳過
        root.addView(Button(this).apply {
            text = "先跳過"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(0xFF888888.toInt())
            setOnClickListener { finish() }
        })

        root.setBackgroundColor(0xFF1A1A2E.toInt())
        scroll.addView(root)
        return scroll
    }

    private fun toggleRegisterMode() {
        isRegisterMode = !isRegisterMode
        nameInput.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
        loginBtn.text = if (isRegisterMode) "註冊" else "登入"
        registerBtn.text = if (isRegisterMode) "已有帳號？登入" else "沒有帳號？註冊"
    }

    private fun doLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (email.isBlank() || password.isBlank()) {
            statusText.text = "請填寫 Email 和密碼"
            return
        }

        loginBtn.isEnabled = false
        statusText.text = "處理中..."

        CoroutineScope(Dispatchers.Main).launch {
            val result = if (isRegisterMode) {
                val name = nameInput.text.toString().trim().ifBlank { email.substringBefore("@") }
                authClient.register(email, password, name)
            } else {
                authClient.loginWithEmail(email, password)
            }

            withContext(Dispatchers.Main) {
                loginBtn.isEnabled = true
                when (result) {
                    is SnowRealmAuthClient.AuthResult.Success -> {
                        statusText.text = "歡迎 ${result.user.displayName}！"
                        setResult(RESULT_OK)
                        finish()
                    }
                    is SnowRealmAuthClient.AuthResult.Error -> {
                        statusText.text = result.message
                    }
                }
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
    }
}

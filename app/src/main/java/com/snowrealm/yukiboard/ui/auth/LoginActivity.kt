package com.snowrealm.yukiboard.ui.auth

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import kotlinx.coroutines.*

/**
 * SnowRealm 帳號登入頁面 — 液態玻璃風格。
 *
 * Email 登入 / 註冊（含驗證碼）/ LINE OAuth / Google OAuth（WebView）
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var authClient: SnowRealmAuthClient
    private lateinit var statusText: TextView
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var sendCodeBtn: View
    private lateinit var loginBtn: View
    private lateinit var registerToggle: TextView
    private var isRegisterMode = false
    private var pendingCodeId: String? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val baseUrl get() = BuildConfig.INSIGHT_API_BASE_URL.trimEnd('/')

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authClient = SnowRealmAuthClient(this)

        // 全螢幕沉浸
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // 恢復被系統回收前的狀態
        if (savedInstanceState != null) {
            isRegisterMode = savedInstanceState.getBoolean("isRegisterMode", false)
            pendingCodeId = savedInstanceState.getString("pendingCodeId")
        }

        setContentView(buildUI())

        // 恢復輸入內容
        if (savedInstanceState != null) {
            emailInput.setText(savedInstanceState.getString("email", ""))
            passwordInput.setText(savedInstanceState.getString("password", ""))
            nameInput.setText(savedInstanceState.getString("name", ""))
            codeInput.setText(savedInstanceState.getString("code", ""))
            if (isRegisterMode) {
                toggleRegisterMode() // 切回註冊模式 UI
                if (pendingCodeId != null) {
                    statusText.setTextColor(0xFF40E0D0.toInt())
                    statusText.text = "請填入驗證碼"
                }
            }
        }

        if (authClient.isLoggedIn) {
            statusText.text = "已登入：${authClient.currentDisplayName ?: authClient.currentEmail}"
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isRegisterMode", isRegisterMode)
        outState.putString("pendingCodeId", pendingCodeId)
        if (::emailInput.isInitialized) outState.putString("email", emailInput.text.toString())
        if (::passwordInput.isInitialized) outState.putString("password", passwordInput.text.toString())
        if (::nameInput.isInitialized) outState.putString("name", nameInput.text.toString())
        if (::codeInput.isInitialized) outState.putString("code", codeInput.text.toString())
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // OAuth callback 成功回來
        if (intent?.getBooleanExtra("oauth_success", false) == true) {
            val auth = SnowRealmAuthClient(this)
            if (auth.isLoggedIn) {
                statusText.setTextColor(0xFF40E0D0.toInt())
                statusText.text = "歡迎 ${auth.currentDisplayName ?: auth.currentEmail}！"
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
    }

    // =========================================================================
    // UI 建構 — 液態玻璃風格
    // =========================================================================

    private fun buildUI(): View {
        val d = resources.displayMetrics.density
        val pad = (24 * d).toInt()

        val frame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        frame.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF0A0E1A.toInt(), 0xFF121836.toInt(), 0xFF0D1B2A.toInt())
        )

        frame.addView(createGlowOrb(d, 120, 0x3000B4D8.toInt(), -40, -20))
        frame.addView(createGlowOrb(d, 180, 0x2040E0D0.toInt(), 160, 500))
        frame.addView(createGlowOrb(d, 100, 0x20C084FC.toInt(), 220, 120))

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, (80 * d).toInt(), 0, (40 * d).toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, 0, pad, pad)
        }

        // 雪花 icon + 標題
        root.addView(TextView(this).apply {
            text = "❄"; textSize = 48f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * d).toInt())
        })
        root.addView(TextView(this).apply {
            text = "SnowRealm"; textSize = 28f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, 0, 0, (4 * d).toInt())
        })
        root.addView(TextView(this).apply {
            text = "帳號登入"; textSize = 14f; setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER; setPadding(0, 0, 0, (24 * d).toInt())
        })

        // 狀態文字
        statusText = TextView(this).apply {
            textSize = 13f; setTextColor(0xFFFF6B6B.toInt())
            gravity = Gravity.CENTER; setPadding(0, 0, 0, (12 * d).toInt())
        }
        root.addView(statusText)

        // ── 玻璃卡片：Email 登入 ──
        val glassCard = createGlassCard(d)
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        }

        // 名稱（註冊模式）
        nameInput = createGlassInput("顯示名稱", InputType.TYPE_CLASS_TEXT, d).apply {
            visibility = View.GONE
        }
        cardContent.addView(nameInput)

        // Email
        emailInput = createGlassInput("Email / 帳號", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, d)
        cardContent.addView(emailInput)

        // 密碼
        passwordInput = createGlassInput("密碼",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, d)
        cardContent.addView(passwordInput)

        // 驗證碼（註冊模式）
        val codeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
        }
        codeInput = createGlassInput("驗證碼", InputType.TYPE_CLASS_NUMBER, d).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * d).toInt()
            }
        }
        codeRow.addView(codeInput)
        sendCodeBtn = createGlassButton("發送驗證碼", 0xFF6C63FF.toInt(), d) { doSendCode() }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * d).toInt()
            }
            setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt())
        }
        codeRow.addView(sendCodeBtn)
        cardContent.addView(codeRow)

        // 登入按鈕
        loginBtn = createGlassButton("登入", 0xFF00B4D8.toInt(), d) { doLogin() }
        cardContent.addView(loginBtn)

        // 註冊切換
        registerToggle = TextView(this).apply {
            text = "沒有帳號？註冊"; textSize = 13f
            setTextColor(0xFF80D8FF.toInt()); gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, 0)
            setOnClickListener { toggleRegisterMode() }
        }
        cardContent.addView(registerToggle)

        glassCard.addView(cardContent)
        root.addView(glassCard)

        // 分隔線
        root.addView(createDividerRow(d))

        // ── 玻璃卡片：OAuth ──
        val oauthCard = createGlassCard(d)
        val oauthContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (16 * d).toInt(), (20 * d).toInt(), (16 * d).toInt())
        }
        oauthContent.addView(createGlassButton("LINE 登入", 0xFF06C755.toInt(), d) {
            startLineLogin()
        })
        oauthContent.addView(createGlassButton("Google 登入", 0xFF4285F4.toInt(), d) {
            openOAuthCustomTab("$baseUrl/api/auth/google?mobile_callback=yukiboard://auth/callback")
        })
        oauthCard.addView(oauthContent)
        root.addView(oauthCard)

        // ── 登出按鈕（已登入時顯示）──
        val logoutBtn = createGlassButton("登出", 0xFFE53935.toInt(), d) {
            authClient.logout()
            statusText.setTextColor(0xFF40E0D0.toInt())
            statusText.text = "已登出"
            recreate()
        }
        logoutBtn.visibility = if (authClient.isLoggedIn) View.VISIBLE else View.GONE
        root.addView(logoutBtn)

        // 跳過
        root.addView(TextView(this).apply {
            text = "先跳過"; textSize = 14f; setTextColor(0x66FFFFFF.toInt())
            gravity = Gravity.CENTER; setPadding(0, (20 * d).toInt(), 0, 0)
            setOnClickListener { finish() }
        })

        scroll.addView(root)
        frame.addView(scroll)

        return frame
    }

    // =========================================================================
    // 玻璃 UI 元件工廠
    // =========================================================================

    private fun createGlassCard(d: Float): FrameLayout {
        val card = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * d).toInt() }
        }
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 20 * d
            setStroke((1.5f * d).toInt(), 0x30FFFFFF.toInt())
        }
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 20 * d
            setColor(0x1AFFFFFF.toInt())
        }
        card.background = LayerDrawable(arrayOf(fill, border))
        card.clipToOutline = true
        card.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20 * d)
            }
        }
        return card
    }

    private fun createGlassInput(hint: String, type: Int, d: Float): EditText {
        return EditText(this).apply {
            this.hint = hint; this.inputType = type; textSize = 15f
            setTextColor(Color.WHITE); setHintTextColor(0x66FFFFFF.toInt())
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 12 * d
                setColor(0x15FFFFFF.toInt()); setStroke((1 * d).toInt(), 0x20FFFFFF.toInt())
            }
        }
    }

    private fun createGlassButton(text: String, accentColor: Int, d: Float, onClick: () -> Unit): View {
        return TextView(this).apply {
            this.text = text; textSize = 15f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, (14 * d).toInt(), 0, (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * d).toInt() }
            val baseAlpha = (accentColor and 0x00FFFFFF) or 0x40000000
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 14 * d
                setColor(baseAlpha)
                setStroke((1 * d).toInt(), (accentColor and 0x00FFFFFF) or 0x60000000)
            }
            elevation = 4 * d
            setOnClickListener { onClick() }
        }
    }

    private fun createGlowOrb(d: Float, sizeDp: Int, color: Int, xDp: Int, yDp: Int): View {
        return View(this).apply {
            val s = (sizeDp * d).toInt()
            layoutParams = FrameLayout.LayoutParams(s, s).apply {
                leftMargin = (xDp * d).toInt(); topMargin = (yDp * d).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(color)
            }
        }
    }

    private fun createDividerRow(d: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            val line = { View(this@LoginActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, (1 * d).toInt(), 1f)
                setBackgroundColor(0x30FFFFFF.toInt())
            }}
            addView(line())
            addView(TextView(this@LoginActivity).apply {
                text = "  或  "; textSize = 12f; setTextColor(0x66FFFFFF.toInt())
            })
            addView(line())
        }
    }

    // =========================================================================
    // Email 登入 / 註冊
    // =========================================================================

    private fun toggleRegisterMode() {
        isRegisterMode = !isRegisterMode
        nameInput.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
        // codeRow is nameInput's parent's child — find it
        val cardContent = nameInput.parent as LinearLayout
        // codeRow is at index 4 (nameInput=0, email=1, password=2, codeRow=3)
        val codeRow = cardContent.getChildAt(3)
        codeRow.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
        (loginBtn as TextView).text = if (isRegisterMode) "註冊" else "登入"
        registerToggle.text = if (isRegisterMode) "已有帳號？登入" else "沒有帳號？註冊"
        pendingCodeId = null
        statusText.text = ""
    }

    /** 發送驗證碼 */
    private fun doSendCode() {
        val email = emailInput.text.toString().trim()
        if (email.isBlank()) {
            statusText.setTextColor(0xFFFF6B6B.toInt())
            statusText.text = "請先填寫 Email"
            return
        }

        sendCodeBtn.isEnabled = false
        statusText.setTextColor(0x99FFFFFF.toInt())
        statusText.text = "發送驗證碼中..."

        activityScope.launch {
            val result = authClient.sendVerificationCode(email)
            withContext(Dispatchers.Main) {
                sendCodeBtn.isEnabled = true
                when (result) {
                    is SnowRealmAuthClient.SendCodeResult.Success -> {
                        pendingCodeId = result.codeId
                        statusText.setTextColor(0xFF40E0D0.toInt())
                        statusText.text = "驗證碼已發送到 $email"
                    }
                    is SnowRealmAuthClient.SendCodeResult.Error -> {
                        statusText.setTextColor(0xFFFF6B6B.toInt())
                        statusText.text = result.message
                    }
                }
            }
        }
    }

    private fun doLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (email.isBlank() || password.isBlank()) {
            statusText.text = "請填寫帳號和密碼"; return
        }

        if (isRegisterMode) {
            val code = codeInput.text.toString().trim()
            if (pendingCodeId.isNullOrBlank() || code.isBlank()) {
                statusText.setTextColor(0xFFFF6B6B.toInt())
                statusText.text = "請先發送並填入驗證碼"; return
            }
        }

        loginBtn.isEnabled = false
        statusText.setTextColor(0x99FFFFFF.toInt())
        statusText.text = "處理中..."

        activityScope.launch {
            val result = if (isRegisterMode) {
                val name = nameInput.text.toString().trim().ifBlank { email.substringBefore("@") }
                val code = codeInput.text.toString().trim()
                val codeId = pendingCodeId ?: run {
                    statusText.setTextColor(0xFFFF6B6B.toInt())
                    statusText.text = "請先完成邀請碼驗證"
                    loginBtn.isEnabled = true
                    return@launch
                }
                authClient.register(email, password, name, codeId, code)
            } else {
                authClient.loginWithEmail(email, password)
            }

            withContext(Dispatchers.Main) {
                loginBtn.isEnabled = true
                when (result) {
                    is SnowRealmAuthClient.AuthResult.Success -> {
                        statusText.setTextColor(0xFF40E0D0.toInt())
                        statusText.text = "歡迎 ${result.user.displayName}！"
                        setResult(RESULT_OK)
                        finish()
                    }
                    is SnowRealmAuthClient.AuthResult.Error -> {
                        statusText.setTextColor(0xFFFF6B6B.toInt())
                        statusText.text = result.message
                    }
                }
            }
        }
    }

    // =========================================================================
    // Chrome Custom Tab OAuth（LINE / Google）
    // =========================================================================

    /**
     * LINE 登入：用 Intent.ACTION_VIEW 打開 LINE OAuth URL。
     * 這樣 Android 可以偵測 LINE App 並直接跳轉一鍵授權，
     * 不像 Custom Tab 的 302 redirect 不會觸發 App Link。
     */
    private fun startLineLogin() {
        statusText.setTextColor(0x99FFFFFF.toInt())
        statusText.text = "正在連接 LINE..."

        activityScope.launch {
            try {
                val apiUrl = "$baseUrl/api/auth/line?mobile_callback=yukiboard://auth/callback"
                val lineUrl = withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder().url(apiUrl).get().build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: return@withContext null
                    // Insight 回傳 JSON { url, state }
                    val json = org.json.JSONObject(body)
                    json.optString("url").ifBlank { null }
                }

                if (lineUrl != null) {
                    // 用 ACTION_VIEW 打開，讓系統決定用 LINE App 或瀏覽器
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lineUrl))
                    startActivity(intent)
                    statusText.text = ""
                } else {
                    statusText.setTextColor(0xFFFF6B6B.toInt())
                    statusText.text = "LINE 登入設定未完成"
                }
            } catch (e: Exception) {
                statusText.setTextColor(0xFFFF6B6B.toInt())
                statusText.text = "連線失敗: ${e.message?.take(50)}"
            }
        }
    }

    private fun openOAuthCustomTab(url: String) {
        val customTabIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabIntent.launchUrl(this, Uri.parse(url))
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
    }
}

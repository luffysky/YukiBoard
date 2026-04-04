package com.snowrealm.yukiboard.ui.auth

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SnowRealm 帳號登入頁面 — 液態玻璃風格。
 *
 * Email 登入 / LINE OAuth / Google OAuth（WebView）
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var authClient: SnowRealmAuthClient
    private lateinit var statusText: TextView
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var loginBtn: View
    private lateinit var registerToggle: TextView
    private lateinit var webViewContainer: FrameLayout
    private var webView: WebView? = null
    private var isRegisterMode = false

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

        setContentView(buildUI())

        if (authClient.isLoggedIn) {
            statusText.text = "已登入：${authClient.currentDisplayName ?: authClient.currentEmail}"
        }
    }

    override fun onBackPressed() {
        if (webView != null && webViewContainer.visibility == View.VISIBLE) {
            dismissWebView()
            return
        }
        super.onBackPressed()
    }

    // =========================================================================
    // UI 建構 — 液態玻璃風格
    // =========================================================================

    private fun buildUI(): View {
        val d = resources.displayMetrics.density
        val pad = (24 * d).toInt()

        // 最外層 FrameLayout（背景 + 玻璃卡片 + WebView 層）
        val frame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── 漸層背景 ──
        frame.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF0A0E1A.toInt(), 0xFF121836.toInt(), 0xFF0D1B2A.toInt())
        )

        // ── 裝飾光暈 ──
        frame.addView(createGlowOrb(d, 120, 0x3000B4D8.toInt(), -40, -20))
        frame.addView(createGlowOrb(d, 180, 0x2040E0D0.toInt(), 160, 500))
        frame.addView(createGlowOrb(d, 100, 0x20C084FC.toInt(), 220, 120))

        // ── 捲動容器 ──
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

        // ── 雪花 icon + 標題 ──
        root.addView(TextView(this).apply {
            text = "❄"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * d).toInt())
        })
        root.addView(TextView(this).apply {
            text = "SnowRealm"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, 0, 0, (4 * d).toInt())
        })
        root.addView(TextView(this).apply {
            text = "帳號登入"
            textSize = 14f
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (24 * d).toInt())
        })

        // ── 狀態文字 ──
        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFFF6B6B.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (12 * d).toInt())
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

        // 登入按鈕
        loginBtn = createGlassButton("登入", 0xFF00B4D8.toInt(), d) { doLogin() }
        cardContent.addView(loginBtn)

        // 註冊切換
        registerToggle = TextView(this).apply {
            text = "沒有帳號？註冊"
            textSize = 13f
            setTextColor(0xFF80D8FF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, 0)
            setOnClickListener { toggleRegisterMode() }
        }
        cardContent.addView(registerToggle)

        glassCard.addView(cardContent)
        root.addView(glassCard)

        // ── 分隔線 ──
        root.addView(createDividerRow(d))

        // ── 玻璃卡片：OAuth ──
        val oauthCard = createGlassCard(d)
        val oauthContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (16 * d).toInt(), (20 * d).toInt(), (16 * d).toInt())
        }

        oauthContent.addView(createGlassButton("LINE 登入", 0xFF06C755.toInt(), d) {
            openOAuthWebView("$baseUrl/api/auth/line")
        })
        oauthContent.addView(createGlassButton("Google 登入", 0xFF4285F4.toInt(), d) {
            openOAuthWebView("$baseUrl/api/auth/google")
        })

        oauthCard.addView(oauthContent)
        root.addView(oauthCard)

        // ── 跳過 ──
        root.addView(TextView(this).apply {
            text = "先跳過"
            textSize = 14f
            setTextColor(0x66FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (20 * d).toInt(), 0, 0)
            setOnClickListener { finish() }
        })

        scroll.addView(root)
        frame.addView(scroll)

        // ── WebView 層（OAuth 用，預設隱藏）──
        webViewContainer = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(0xF0000000.toInt())
        }
        // 關閉按鈕
        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val s = (44 * d).toInt()
            layoutParams = FrameLayout.LayoutParams(s, s).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (40 * d).toInt()
                rightMargin = (12 * d).toInt()
            }
            setBackgroundResource(android.R.color.transparent)
            elevation = 10f
            setOnClickListener { dismissWebView() }
        }
        webViewContainer.addView(closeBtn)
        frame.addView(webViewContainer)

        return frame
    }

    // =========================================================================
    // 玻璃 UI 元件工廠
    // =========================================================================

    /** 液態玻璃卡片：半透明 + 圓角 + 漸層邊框 */
    private fun createGlassCard(d: Float): FrameLayout {
        val card = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * d).toInt()
            }
        }

        // 邊框層（漸層描邊）
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20 * d
            setStroke((1.5f * d).toInt(), 0x30FFFFFF.toInt())
        }

        // 填充層（半透明玻璃）
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20 * d
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

    /** 液態玻璃輸入框 */
    private fun createGlassInput(hint: String, type: Int, d: Float): EditText {
        return EditText(this).apply {
            this.hint = hint
            this.inputType = type
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(0x66FFFFFF.toInt())
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * d).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * d
                setColor(0x15FFFFFF.toInt())
                setStroke((1 * d).toInt(), 0x20FFFFFF.toInt())
            }
        }
    }

    /** 液態玻璃按鈕 */
    private fun createGlassButton(text: String, accentColor: Int, d: Float, onClick: () -> Unit): View {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (14 * d).toInt(), 0, (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * d).toInt()
            }
            // 半透明帶色按鈕
            val baseAlpha = (accentColor and 0x00FFFFFF) or 0x40000000
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14 * d
                setColor(baseAlpha)
                setStroke((1 * d).toInt(), (accentColor and 0x00FFFFFF) or 0x60000000)
            }
            elevation = 4 * d
            setOnClickListener { onClick() }
        }
    }

    /** 裝飾光暈圓 */
    private fun createGlowOrb(d: Float, sizeDp: Int, color: Int, xDp: Int, yDp: Int): View {
        return View(this).apply {
            val s = (sizeDp * d).toInt()
            layoutParams = FrameLayout.LayoutParams(s, s).apply {
                leftMargin = (xDp * d).toInt()
                topMargin = (yDp * d).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
    }

    /** 分隔線 + 文字 */
    private fun createDividerRow(d: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())

            val line = { View(this@LoginActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, (1 * d).toInt(), 1f)
                setBackgroundColor(0x30FFFFFF.toInt())
            }}
            addView(line())
            addView(TextView(this@LoginActivity).apply {
                text = "  或  "
                textSize = 12f
                setTextColor(0x66FFFFFF.toInt())
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
        (loginBtn as TextView).text = if (isRegisterMode) "註冊" else "登入"
        registerToggle.text = if (isRegisterMode) "已有帳號？登入" else "沒有帳號？註冊"
    }

    private fun doLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (email.isBlank() || password.isBlank()) {
            statusText.text = "請填寫帳號和密碼"
            return
        }

        loginBtn.isEnabled = false
        statusText.setTextColor(0x99FFFFFF.toInt())
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
    // WebView OAuth（LINE / Google）
    // =========================================================================

    private fun openOAuthWebView(url: String) {
        // 清除舊 cookie 確保乾淨登入
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            removeAllCookies(null)
        }

        val wv = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = (36 * resources.displayMetrics.density).toInt()
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString + " YukiBoard/1.0"

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val loadUrl = request.url.toString()
                    // OAuth 完成後會重定向到 /insight-admin/ — 攔截並讀 cookie
                    if (loadUrl.contains("/insight-admin/") || loadUrl.contains("/login?")) {
                        extractSessionAndFinish()
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    // 也在頁面載完時檢查（某些 redirect 不走 shouldOverrideUrlLoading）
                    if (url.contains("/insight-admin/")) {
                        extractSessionAndFinish()
                    }
                }
            }
        }

        webView = wv
        webViewContainer.addView(wv, 0)
        webViewContainer.visibility = View.VISIBLE
        wv.loadUrl(url)
    }

    private fun extractSessionAndFinish() {
        val cookies = CookieManager.getInstance().getCookie(baseUrl) ?: ""
        val token = cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("admin_session=") }
            ?.substringAfter("admin_session=")

        if (!token.isNullOrBlank()) {
            // 用 token 取使用者資料
            CoroutineScope(Dispatchers.Main).launch {
                authClient.saveTokenDirectly(token)
                val user = authClient.fetchMe()
                withContext(Dispatchers.Main) {
                    dismissWebView()
                    if (user != null) {
                        statusText.setTextColor(0xFF40E0D0.toInt())
                        statusText.text = "歡迎 ${user.displayName}！"
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // token 拿到了但 /me 失敗，還是算登入成功
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }
        } else {
            dismissWebView()
            statusText.setTextColor(0xFFFF6B6B.toInt())
            statusText.text = "登入失敗，請重試"
        }
    }

    private fun dismissWebView() {
        webView?.let {
            webViewContainer.removeView(it)
            it.destroy()
        }
        webView = null
        webViewContainer.visibility = View.GONE
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
    }
}

package com.snowrealm.yukiboard.ui.auth

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
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
 * SnowRealm 帳號登入頁面。
 *
 * 直接導向 Insight 登入/註冊頁面（Chrome Custom Tab），
 * 所有帳號操作（email / LINE / Google）都在 Insight 網頁端完成。
 * 登入成功後透過 yukiboard://auth/callback?token=xxx 回到 App。
 *
 * YukiBoard 登入後 token 獨立儲存，Insight 登出不影響 YukiBoard。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var authClient: SnowRealmAuthClient
    private val baseUrl get() = BuildConfig.INSIGHT_API_BASE_URL.trimEnd('/')
    private val callbackUrl = "yukiboard://auth/callback"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authClient = SnowRealmAuthClient.getInstance(this)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContentView(buildUI())
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("oauth_success", false) == true) {
            if (authClient.isLoggedIn) {
                setResult(RESULT_OK)
                // 重新建構 UI 顯示已登入狀態
                recreate()
            }
        }
    }

    // =========================================================================
    // UI
    // =========================================================================

    private fun buildUI(): View {
        val d = resources.displayMetrics.density

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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((32 * d).toInt(), (100 * d).toInt(), (32 * d).toInt(), (40 * d).toInt())
        }

        // 雪花 + 標題
        root.addView(TextView(this).apply {
            text = "❄"; textSize = 56f; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "SnowRealm"; textSize = 28f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, 0, 0, (8 * d).toInt())
        })

        if (authClient.isLoggedIn) {
            // ── 已登入狀態 ──
            val user = authClient.currentUser
            val planText = when (user?.plan) {
                SnowRealmAuthClient.UserPlan.PLUS -> "Plus"
                SnowRealmAuthClient.UserPlan.PRO -> "Pro"
                SnowRealmAuthClient.UserPlan.ENTERPRISE -> "Enterprise"
                else -> "Free"
            }

            val nameOrEmail = user?.displayName?.ifBlank { null }
                ?: authClient.currentEmail?.ifBlank { null }
                ?: "已登入"
            val roleText = when (user?.role) {
                "super_admin" -> "Super Admin"
                "admin" -> "Admin"
                "editor" -> "Editor"
                "viewer" -> "Viewer"
                else -> null
            }

            root.addView(TextView(this).apply {
                text = nameOrEmail; textSize = 22f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
                setPadding(0, (20 * d).toInt(), 0, (4 * d).toInt())
            })
            // 方案 + 特權
            val badgeText = if (roleText != null) "$planText · $roleText" else "$planText 方案"
            root.addView(TextView(this).apply {
                text = badgeText; textSize = 13f; setTextColor(0xFF40E0D0.toInt())
                gravity = Gravity.CENTER; setPadding(0, 0, 0, (24 * d).toInt())
            })

            // 登出按鈕
            root.addView(glassButton("登出", 0xFFE53935.toInt(), d) {
                authClient.logout()
                recreate()
            })
        } else {
            // ── 未登入：登入 / 註冊按鈕 ──
            root.addView(TextView(this).apply {
                text = "登入 Insight 帳號\n解鎖 AI 功能、主題同步、Z幣"
                textSize = 14f; setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, (16 * d).toInt(), 0, (32 * d).toInt())
            })

            root.addView(glassButton("登入", 0xFF00B4D8.toInt(), d) {
                openInsightPage("/login")
            })
            root.addView(glassButton("免費註冊", 0xFF6C63FF.toInt(), d) {
                openInsightPage("/register")
            })
        }

        // 跳過
        root.addView(TextView(this).apply {
            text = "先跳過"; textSize = 14f; setTextColor(0x44FFFFFF.toInt())
            gravity = Gravity.CENTER; setPadding(0, (24 * d).toInt(), 0, 0)
            setOnClickListener { finish() }
        })

        frame.addView(root)
        return frame
    }

    // =========================================================================
    // 導向 Insight 頁面
    // =========================================================================

    private fun openInsightPage(path: String) {
        val url = "$baseUrl$path?mobile_callback=${Uri.encode(callbackUrl)}"
        val customTab = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTab.launchUrl(this, Uri.parse(url))
    }

    // =========================================================================
    // 工具
    // =========================================================================

    private fun glassButton(text: String, color: Int, d: Float, onClick: () -> Unit): View {
        return TextView(this).apply {
            this.text = text; textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, (16 * d).toInt(), 0, (16 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * d).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 16 * d
                setColor((color and 0x00FFFFFF) or 0x50000000)
                setStroke((1 * d).toInt(), (color and 0x00FFFFFF) or 0x60000000)
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

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
    }
}

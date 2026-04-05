package com.snowrealm.yukiboard.ui.settings

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * API Key 申請教學頁面。
 *
 * 列出 5 個 AI 平台的申請步驟 + 免費方案說明 + 一鍵跳轉申請頁面。
 * 雪凜推薦語氣呈現（親切引導）。
 */
class ApiGuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF1A2035.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(80))
        }

        // 標題
        root.addView(TextView(this).apply {
            text = "AI 服務申請教學"
            textSize = 22f
            setTextColor(0xFFE8F4FF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, dp(8), dp(4))
        })
        root.addView(TextView(this).apply {
            text = "雪凜小提醒：選一個你喜歡的 AI 服務，按照步驟申請 API Key，\n貼回設定頁就能開始使用 AI 功能囉！"
            textSize = 13f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(8), 0, dp(8), dp(16))
        })

        // ── Gemini（推薦！免費）
        root.addView(providerCard(
            name = "Google Gemini",
            tag = "推薦！免費額度最多",
            color = 0xFF4285F4.toInt(),
            freeInfo = "gemini-2.0-flash 免費：每分鐘 15 次、每天無上限",
            steps = listOf(
                "1. 前往 Google AI Studio",
                "2. 用 Google 帳號登入",
                "3. 點左側「Get API key」",
                "4. 建立新的 API Key",
                "5. 複製 Key，貼到 YukiBoard 設定頁"
            ),
            url = "https://aistudio.google.com/apikey"
        ))

        // ── Groq（免費 + 超快）
        root.addView(providerCard(
            name = "Groq",
            tag = "免費 + 超快推論",
            color = 0xFFFF6600.toInt(),
            freeInfo = "免費：每分鐘 30 次、每天 14,400 次（Llama 3.3 70B）",
            steps = listOf(
                "1. 前往 Groq Console",
                "2. 用 Google 或 GitHub 帳號註冊",
                "3. 進入 API Keys 頁面",
                "4. 建立新的 API Key",
                "5. 複製 Key，貼到 YukiBoard 設定頁"
            ),
            url = "https://console.groq.com/keys"
        ))

        // ── OpenAI
        root.addView(providerCard(
            name = "OpenAI",
            tag = "GPT-4o 系列",
            color = 0xFF10A37F.toInt(),
            freeInfo = "新帳號有 $5 免費額度（3 個月內用完）",
            steps = listOf(
                "1. 前往 OpenAI Platform",
                "2. 註冊/登入帳號",
                "3. 進入 API Keys 頁面",
                "4. 建立新的 Secret Key",
                "5. 複製 Key，貼到 YukiBoard 設定頁"
            ),
            url = "https://platform.openai.com/api-keys"
        ))

        // ── Anthropic
        root.addView(providerCard(
            name = "Anthropic",
            tag = "Claude 系列",
            color = 0xFFD97706.toInt(),
            freeInfo = "新帳號有 $5 免費額度",
            steps = listOf(
                "1. 前往 Anthropic Console",
                "2. 註冊/登入帳號",
                "3. 進入 API Keys 頁面",
                "4. 建立新的 API Key",
                "5. 複製 Key，貼到 YukiBoard 設定頁"
            ),
            url = "https://console.anthropic.com/settings/keys"
        ))

        // ── SnowRealm（邀請碼）
        root.addView(providerCard(
            name = "SnowRealm Proxy",
            tag = "邀請碼免費方案",
            color = 0xFF6C5CE7.toInt(),
            freeInfo = "使用邀請碼，免費使用（有每日 Token 限額）",
            steps = listOf(
                "1. 取得邀請碼（社群/朋友分享）",
                "2. 進入 YukiBoard 設定 → AI 設定",
                "3. 輸入邀請碼啟用",
                "4. 不需要 API Key，自動連線 SnowRealm 伺服器"
            ),
            url = null
        ))

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun providerCard(
        name: String,
        tag: String,
        color: Int,
        freeInfo: String,
        steps: List<String>,
        url: String?
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF0D1526.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(1, 0xFF2A3A5A.toInt())
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(12)) }

            // 標題行
            addView(LinearLayout(this@ApiGuideActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(this@ApiGuideActivity).apply {
                    text = name
                    textSize = 17f
                    setTextColor(0xFFE8F4FF.toInt())
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(this@ApiGuideActivity).apply {
                    text = tag
                    textSize = 11f
                    setTextColor(color)
                    setPadding(dp(8), 0, 0, 0)
                })
            })

            // 免費方案
            addView(TextView(this@ApiGuideActivity).apply {
                text = freeInfo
                textSize = 12f
                setTextColor(0xFF66CC88.toInt())
                setPadding(0, dp(4), 0, dp(8))
            })

            // 步驟
            for (step in steps) {
                addView(TextView(this@ApiGuideActivity).apply {
                    text = step
                    textSize = 13f
                    setTextColor(0xFF8899BB.toInt())
                    setPadding(dp(4), dp(2), 0, dp(2))
                })
            }

            // 申請按鈕
            if (url != null) {
                addView(Button(this@ApiGuideActivity).apply {
                    text = "前往申請"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 13f
                    background = GradientDrawable().apply {
                        setColor(color)
                        cornerRadius = dp(8).toFloat()
                    }
                    setPadding(dp(16), dp(6), dp(16), dp(6))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dp(8), 0, 0) }
                    setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                })
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

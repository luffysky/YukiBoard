package com.snowrealm.yukiboard.ui.panel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.*
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.yuki.SecretMessageManager

/**
 * 悄悄話解密面板 — 密碼制 AES-256-GCM 加解密。
 *
 * UI 結構：
 * - 🔐 標題 + 說明
 * - 密碼輸入框（遮罩 + 顯示切換）
 * - 加密訊息貼上區（多行）
 * - 解密 / 加密 按鈕
 * - 結果顯示區 + 複製明文按鈕
 */
class DecryptPanelView(context: Context) : ScrollView(context) {

    private val density = resources.displayMetrics.density
    private val secretManager = SecretMessageManager()

    var theme: KeyboardTheme? = null
        set(value) { field = value; applyTheme() }

    /** 加密後回呼（送出加密訊息） */
    var onEncryptedResult: ((String) -> Unit)? = null

    private val root: LinearLayout
    private val passwordInput: EditText
    private val messageInput: EditText
    private val resultText: TextView
    private val resultContainer: LinearLayout
    private val statusText: TextView

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false

        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // ── 標題 ──
        root.addView(TextView(context).apply {
            text = "🔐 悄悄話加解密"
            textSize = 16f
            setTextColor(0xFFE8F4FF.toInt())
            setPadding(0, 0, 0, dp(2))
        })
        root.addView(TextView(context).apply {
            text = "使用約定密碼加密/解密訊息，只有知道密碼的人才能讀取"
            textSize = 11f
            setTextColor(0xFF667788.toInt())
            setPadding(0, 0, 0, dp(8))
        })

        // ── 密碼輸入 ──
        root.addView(TextView(context).apply {
            text = "密碼"
            textSize = 12f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(0, 0, 0, dp(2))
        })
        passwordInput = EditText(context).apply {
            hint = "輸入約定密碼"
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
            setHintTextColor(0xFF556677.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            background = makeRoundBg(0xFF1A2A3A.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(passwordInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // ── 訊息輸入 ──
        root.addView(TextView(context).apply {
            text = "訊息"
            textSize = 12f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(0, 0, 0, dp(2))
        })
        messageInput = EditText(context).apply {
            hint = "貼上加密訊息，或輸入要加密的明文"
            textSize = 13f
            setTextColor(0xFFE8F4FF.toInt())
            setHintTextColor(0xFF556677.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 5
            background = makeRoundBg(0xFF1A2A3A.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(8))
            gravity = Gravity.TOP or Gravity.START
        }
        root.addView(messageInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // ── 按鈕列 ──
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnRow.addView(makeButton("🔓 解密") { doDecrypt() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
        })
        btnRow.addView(makeButton("🔒 加密") { doEncrypt() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
            }
        })
        root.addView(btnRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        // ── 貼上密文快捷 ──
        root.addView(makeButton("📋 從剪貼簿貼上") {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.primaryClip?.getItemAt(0)?.text?.toString()
            if (!clip.isNullOrEmpty()) {
                messageInput.setText(clip)
            } else {
                statusText.text = "剪貼簿是空的"
                statusText.setTextColor(0xFFFF8888.toInt())
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })

        // ── 狀態文字 ──
        statusText = TextView(context).apply {
            textSize = 11f
            setTextColor(0xFF667788.toInt())
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(statusText)

        // ── 結果區 ──
        resultContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundBg(0xFF0A1520.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = GONE
        }
        resultContainer.addView(TextView(context).apply {
            text = "結果"
            textSize = 11f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(0, 0, 0, dp(4))
        })
        resultText = TextView(context).apply {
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
            setTextIsSelectable(true)
        }
        resultContainer.addView(resultText)

        val copyBtn = makeButton("📋 複製結果") {
            val text = resultText.text.toString()
            if (text.isNotEmpty()) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("yuki_decrypt", text))
                statusText.text = "✅ 已複製到剪貼簿"
                statusText.setTextColor(0xFF88FF88.toInt())
            }
        }
        copyBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) }
        resultContainer.addView(copyBtn)

        // 送出加密訊息按鈕
        val sendBtn = makeButton("📤 送出加密訊息") {
            val text = resultText.text.toString()
            if (text.startsWith(SecretMessageManager.NEW_PREFIX)) {
                onEncryptedResult?.invoke(text)
                statusText.text = "✅ 已送出加密訊息"
                statusText.setTextColor(0xFF88FF88.toInt())
            }
        }
        sendBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) }
        sendBtn.tag = "send_btn"
        resultContainer.addView(sendBtn)

        root.addView(resultContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        applyTheme()
    }

    private fun doDecrypt() {
        val password = passwordInput.text.toString()
        val message = messageInput.text.toString().trim()

        if (password.isEmpty()) {
            showStatus("請輸入密碼", true)
            return
        }
        if (message.isEmpty()) {
            showStatus("請貼上加密訊息", true)
            return
        }

        if (!secretManager.isEncryptedMessage(message)) {
            showStatus("這不是 YukiBoard 加密訊息（需以 [YUKI🔐] 或 [YB🔐] 開頭）", true)
            return
        }

        val result = secretManager.decryptWithPassword(message, password)
        if (result != null) {
            resultText.text = result
            resultContainer.visibility = VISIBLE
            resultContainer.findViewWithTag<TextView>("send_btn")?.visibility = GONE
            showStatus("✅ 解密成功", false)
        } else {
            resultContainer.visibility = GONE
            showStatus("❌ 密碼錯誤，無法解密", true)
        }
    }

    private fun doEncrypt() {
        val password = passwordInput.text.toString()
        val message = messageInput.text.toString().trim()

        if (password.isEmpty()) {
            showStatus("請設定密碼", true)
            return
        }
        if (password.length < 4) {
            showStatus("密碼至少 4 個字元", true)
            return
        }
        if (message.isEmpty()) {
            showStatus("請輸入要加密的訊息", true)
            return
        }

        val encrypted = secretManager.encryptWithPassword(message, password)
        resultText.text = encrypted
        resultContainer.visibility = VISIBLE
        resultContainer.findViewWithTag<TextView>("send_btn")?.visibility = VISIBLE
        showStatus("✅ 加密成功，可複製或直接送出", false)
    }

    private fun showStatus(msg: String, isError: Boolean) {
        statusText.text = msg
        statusText.setTextColor(if (isError) 0xFFFF8888.toInt() else 0xFF88FF88.toInt())
    }

    private fun applyTheme() {
        val bg = theme?.panelBackgroundColorInt ?: 0xFF0D1B2A.toInt()
        setBackgroundColor(bg)
    }

    private fun dp(v: Int): Int = (v * density).toInt()

    private fun makeRoundBg(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(8).toFloat()
        setStroke(1, 0xFF334455.toInt())
    }

    private fun makeButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(0xFFE8F4FF.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            setColor(0xFF1E3A5F.toInt())
            cornerRadius = dp(8).toFloat()
        }
        setOnClickListener { onClick() }
    }
}

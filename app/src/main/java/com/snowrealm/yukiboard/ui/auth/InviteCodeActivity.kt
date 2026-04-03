package com.snowrealm.yukiboard.ui.auth

import android.animation.ObjectAnimator
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.snowrealm.yukiboard.auth.InviteCodeManager

class InviteCodeActivity : AppCompatActivity() {

    private lateinit var inviteManager: InviteCodeManager
    private lateinit var codeInput: EditText
    private lateinit var resultText: TextView
    private lateinit var lottieView: LottieAnimationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inviteManager = InviteCodeManager(this)

        // If already activated, finish immediately
        if (inviteManager.isActivated()) {
            finish()
            return
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF0F1F3D.toInt())
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(48), dp(32), dp(32))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // ── Lottie 角色動畫 ──
        lottieView = LottieAnimationView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(24)
            }
            setAnimation("lottie/yuki_idle.json")
            repeatCount = LottieDrawable.INFINITE
            playAnimation()
        }
        root.addView(lottieView)

        // ── 標題 ──
        root.addView(TextView(this).apply {
            text = "\u8F38\u5165\u9080\u8ACB\u78BC" // 輸入邀請碼
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })

        // ── 副標題 ──
        root.addView(TextView(this).apply {
            text = "\u89E3\u9396 YukiBoard AI \u529F\u80FD" // 解鎖 YukiBoard AI 功能
            textSize = 14f
            setTextColor(0xFF6688AA.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        })

        // ── 邀請碼輸入框 ──
        val inputBackground = GradientDrawable().apply {
            setColor(0xFF253050.toInt())
            cornerRadius = dp(8).toFloat()
        }

        codeInput = EditText(this).apply {
            hint = "YUKI-XXXXXXXX"
            setHintTextColor(0xFF6688AA.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            background = inputBackground
            setPadding(dp(16), dp(12), dp(16), dp(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            isSingleLine = true
            filters = arrayOf(InputFilter.AllCaps())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        root.addView(codeInput)

        // ── 啟用按鈕 ──
        val buttonBackground = GradientDrawable().apply {
            setColor(0xFF4A90E2.toInt())
            cornerRadius = dp(8).toFloat()
        }

        root.addView(Button(this).apply {
            text = "\u555F\u7528" // 啟用
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            background = buttonBackground
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { bottomMargin = dp(16) }
            setOnClickListener { onActivateClicked() }
        })

        // ── 結果文字 ──
        resultText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(24) }
        }
        root.addView(resultText)

        // ── 跳過按鈕 ──
        root.addView(TextView(this).apply {
            text = "\u8DF3\u904E\uFF0C\u4F7F\u7528\u81EA\u5DF1\u7684 API Key \u2192" // 跳過，使用自己的 API Key →
            textSize = 13f
            setTextColor(0xFF7FD8FF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { onSkipClicked() }
        })

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun onActivateClicked() {
        val code = codeInput.text.toString().trim()
        if (code.isEmpty()) {
            showError("\u8ACB\u8F38\u5165\u9080\u8ACB\u78BC") // 請輸入邀請碼
            return
        }

        val codeType = inviteManager.validateCode(code)
        if (codeType != null) {
            inviteManager.activateCode(code, codeType)
            showSuccess(codeType)
        } else {
            showError("\u9080\u8ACB\u78BC\u7121\u6548\u6216\u5DF2\u904E\u671F") // 邀請碼無效或已過期
            shakeEditText()
        }
    }

    private fun showSuccess(type: com.snowrealm.yukiboard.auth.InviteCodeType) {
        resultText.visibility = View.VISIBLE
        resultText.setTextColor(0xFF4ADE80.toInt())

        val limitText = if (type.dailyTokenLimit == Int.MAX_VALUE) {
            "\u7121\u9650" // 無限
        } else {
            "${type.dailyTokenLimit}"
        }
        resultText.text = "AI \u529F\u80FD\u5DF2\u89E3\u9396\uFF01\n\u6BCF\u65E5 Token \u984D\u5EA6\uFF1A$limitText"
        // AI 功能已解鎖！\n每日 Token 額度：{limit}

        // Switch Lottie to happy animation
        lottieView.setAnimation("lottie/yuki_happy.json")
        lottieView.repeatCount = 0
        lottieView.playAnimation()

        // Auto-close after 2 seconds
        codeInput.isEnabled = false
        lottieView.postDelayed({ finish() }, 2000)
    }

    private fun showError(message: String) {
        resultText.visibility = View.VISIBLE
        resultText.setTextColor(0xFFEF4444.toInt())
        resultText.text = message
    }

    private fun shakeEditText() {
        ObjectAnimator.ofFloat(codeInput, "translationX", 0f, -10f, 10f, -10f, 10f, -5f, 5f, 0f)
            .apply {
                duration = 400
                start()
            }
    }

    private fun onSkipClicked() {
        // TODO: launch AISettingsActivity when it exists
        finish()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

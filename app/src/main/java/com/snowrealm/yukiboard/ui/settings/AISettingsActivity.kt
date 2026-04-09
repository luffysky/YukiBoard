package com.snowrealm.yukiboard.ui.settings

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.snowrealm.yukiboard.auth.InviteCodeManager
import com.snowrealm.yukiboard.claw.provider.AIProviderManager
import com.snowrealm.yukiboard.claw.provider.AIProviderType
import com.snowrealm.yukiboard.ui.auth.InviteCodeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AISettingsActivity : AppCompatActivity() {

    private lateinit var inviteCodeManager: InviteCodeManager
    private lateinit var aiProviderManager: AIProviderManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inviteCodeManager = InviteCodeManager(this)
        aiProviderManager = AIProviderManager.getInstance(this)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF0F1F3D.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }

        // ── 標題 ──
        root.addView(TextView(this).apply {
            text = "AI 設定"
            textSize = 24f
            setTextColor(0xFFE8F4FF.toInt())
            setPadding(0, 0, 0, dp(24))
        })

        // ══════════════════════════════════════════════════════════════
        // Section 1: AI Activation Status
        // ══════════════════════════════════════════════════════════════

        root.addView(sectionTitle("AI 功能"))

        if (inviteCodeManager.isActivated()) {
            root.addView(TextView(this).apply {
                text = "✅ AI 功能已啟用"
                textSize = 15f
                setTextColor(0xFF4ADE80.toInt())
                setPadding(dp(8), dp(8), 0, dp(4))
            })

            val remaining = inviteCodeManager.getRemainingTokens()
            val limit = inviteCodeManager.getDailyTokenLimit()
            val tokenText = if (limit == Int.MAX_VALUE) {
                "今日剩餘：無限制"
            } else {
                "今日剩餘：$remaining / $limit tokens"
            }
            root.addView(TextView(this).apply {
                text = tokenText
                textSize = 13f
                setTextColor(0xFFB0D4FF.toInt())
                setPadding(dp(8), dp(4), 0, dp(12))
            })
        } else {
            root.addView(TextView(this).apply {
                text = "輸入邀請碼解鎖 AI 功能"
                textSize = 14f
                setTextColor(0xFFB0D4FF.toInt())
                setPadding(dp(8), dp(8), 0, dp(8))
            })

            root.addView(Button(this).apply {
                text = "輸入邀請碼"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                background = GradientDrawable().apply {
                    setColor(0xFF4A90E2.toInt())
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(16), dp(10), dp(16), dp(10))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(8), dp(4), 0, dp(12)) }
                layoutParams = lp
                setOnClickListener {
                    startActivity(Intent(this@AISettingsActivity, InviteCodeActivity::class.java))
                }
            })
        }

        root.addView(divider())

        // ══════════════════════════════════════════════════════════════
        // Section 2: AI Provider Selection
        // ══════════════════════════════════════════════════════════════

        root.addView(sectionTitle("AI 供應商"))

        root.addView(TextView(this).apply {
            text = "邀請碼啟用後不需要填寫，或填入自己的 Key 取得更多用量"
            textSize = 14f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(8), dp(4), 0, dp(12))
        })

        val currentProvider = aiProviderManager.getSelectedProviderType()

        val providerGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(8), dp(4), 0, dp(8))
        }

        val providers = listOf(
            Triple(AIProviderType.GEMINI, "Google Gemini（免費推薦）", true),
            Triple(AIProviderType.GROQ, "Groq（免費超快）", true),
            Triple(AIProviderType.GEMINI_NANO, "Gemini Nano（本地離線）", true),
            Triple(AIProviderType.OPENAI, "OpenAI", true),
            Triple(AIProviderType.ANTHROPIC, "Anthropic", true),
            Triple(AIProviderType.SNOWREALM, "SnowRealm（邀請碼）", true)
        )

        providers.forEach { (type, label, enabled) ->
            providerGroup.addView(RadioButton(this).apply {
                id = type.ordinal + 100
                text = label
                setTextColor(if (enabled) 0xFFB0D4FF.toInt() else 0xFF556677.toInt())
                isChecked = type == currentProvider
                isEnabled = enabled
            })
        }

        root.addView(providerGroup)

        // API Key input
        val apiKeyInput = EditText(this).apply {
            hint = "API Key"
            setHintTextColor(0xFF6688AA.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            background = GradientDrawable().apply {
                setColor(0xFF253050.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) }
            layoutParams = lp

            // Load existing key
            val existingKey = aiProviderManager.getApiKey(currentProvider)
            if (!existingKey.isNullOrBlank()) {
                setText(existingKey)
            }
        }
        root.addView(apiKeyInput)

        // Model selection
        root.addView(TextView(this).apply {
            text = "模型選擇"
            textSize = 13f
            setTextColor(0xFFB0D4FF.toInt())
            setPadding(dp(8), dp(12), 0, dp(4))
        })

        val modelGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(8), dp(4), 0, dp(8))
        }

        fun updateModelOptions(type: AIProviderType) {
            modelGroup.removeAllViews()
            val models = when (type) {
                AIProviderType.GEMINI -> listOf(
                    "gemini-2.0-flash" to "Gemini 2.0 Flash (推薦/免費)",
                    "gemini-2.0-flash-lite" to "Gemini 2.0 Flash Lite",
                    "gemini-1.5-flash" to "Gemini 1.5 Flash"
                )
                AIProviderType.GROQ -> listOf(
                    "llama-3.3-70b-versatile" to "Llama 3.3 70B (推薦)",
                    "llama-3.1-8b-instant" to "Llama 3.1 8B (超快)",
                    "gemma2-9b-it" to "Gemma 2 9B",
                    "mixtral-8x7b-32768" to "Mixtral 8x7B"
                )
                AIProviderType.OPENAI -> listOf(
                    "gpt-4o-mini" to "gpt-4o-mini (推薦)",
                    "gpt-4o" to "gpt-4o"
                )
                AIProviderType.ANTHROPIC -> listOf(
                    "claude-haiku" to "claude-haiku (推薦)",
                    "claude-sonnet" to "claude-sonnet"
                )
                AIProviderType.GEMINI_NANO -> listOf(
                    "gemini-nano" to "Gemini Nano (本地離線)"
                )
                AIProviderType.SNOWREALM -> listOf(
                    "snowrealm-default" to "SnowRealm Default"
                )
            }
            val currentModel = aiProviderManager.getSelectedModel(type)
            models.forEachIndexed { index, (modelId, displayName) ->
                modelGroup.addView(RadioButton(this@AISettingsActivity).apply {
                    id = 200 + index
                    text = displayName
                    tag = modelId
                    setTextColor(0xFFB0D4FF.toInt())
                    isChecked = modelId == currentModel || (index == 0 && models.none { it.first == currentModel })
                })
            }
            modelGroup.setOnCheckedChangeListener { group, checkedId ->
                val rb = group.findViewById<RadioButton>(checkedId)
                val modelId = rb?.tag as? String ?: return@setOnCheckedChangeListener
                aiProviderManager.setSelectedModel(type, modelId)
            }
        }

        updateModelOptions(currentProvider)
        root.addView(modelGroup)

        // Provider change listener
        providerGroup.setOnCheckedChangeListener { _, checkedId ->
            val type = providers.firstOrNull { it.first.ordinal + 100 == checkedId }?.first
                ?: return@setOnCheckedChangeListener
            aiProviderManager.setSelectedProvider(type)

            // Update API key field
            val existingKey = aiProviderManager.getApiKey(type)
            apiKeyInput.setText(existingKey ?: "")

            // Update model options
            updateModelOptions(type)
        }

        // Test connection button
        root.addView(Button(this).apply {
            text = "測試連線"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            background = GradientDrawable().apply {
                setColor(0xFF4A90E2.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), dp(8), 0, dp(12)) }
            layoutParams = lp
            setOnClickListener {
                val key = apiKeyInput.text.toString().trim()
                if (key.isBlank()) {
                    Toast.makeText(this@AISettingsActivity, "請先輸入 API Key", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val selectedType = aiProviderManager.getSelectedProviderType()
                aiProviderManager.saveApiKey(selectedType, key)

                isEnabled = false
                text = "測試中..."

                lifecycleScope.launch {
                    val success = try {
                        withContext(Dispatchers.IO) {
                            val provider = aiProviderManager.getProvider(selectedType)
                            provider.validateKey(key)
                        }
                    } catch (_: Exception) {
                        false
                    }
                    isEnabled = true
                    text = "測試連線"
                    if (success) {
                        Toast.makeText(this@AISettingsActivity, "✅ 連線成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@AISettingsActivity, "❌ 連線失敗，請檢查 API Key", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        root.addView(divider())

        // ══════════════════════════════════════════════════════════════
        // Section 3: Usage
        // ══════════════════════════════════════════════════════════════

        root.addView(sectionTitle("用量"))

        if (inviteCodeManager.isActivated()) {
            val used = inviteCodeManager.getDailyTokenUsed()
            val limit = inviteCodeManager.getDailyTokenLimit()
            val usageText = if (limit == Int.MAX_VALUE) {
                "今日已使用：$used tokens（無限制）"
            } else {
                "今日已使用：$used / $limit tokens"
            }
            root.addView(TextView(this).apply {
                text = usageText
                textSize = 14f
                setTextColor(0xFFB0D4FF.toInt())
                setPadding(dp(8), dp(8), 0, dp(12))
            })
        } else {
            root.addView(TextView(this).apply {
                text = "尚未啟用邀請碼"
                textSize = 14f
                setTextColor(0xFF6688AA.toInt())
                setPadding(dp(8), dp(8), 0, dp(12))
            })
        }

        // ── Security notice ──
        // API Key 申請教學按鈕
        root.addView(Button(this).apply {
            text = "不知道怎麼申請 API Key？看教學"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF4A5A77.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(4), dp(16), dp(4), dp(4)) }
            setOnClickListener {
                startActivity(android.content.Intent(this@AISettingsActivity, ApiGuideActivity::class.java))
            }
        })

        root.addView(TextView(this).apply {
            text = "\uD83D\uDD12 您的 API Key 使用 Android 系統加密儲存於本機"
            textSize = 12f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(8), dp(24), dp(8), dp(16))
            gravity = Gravity.CENTER
        })

        scrollView.addView(root)
        setContentView(scrollView)
    }

    // ── UI helpers ──

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setTextColor(0xFF7FD8FF.toInt())
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        ).apply { setMargins(0, dp(12), 0, dp(4)) }
        setBackgroundColor(0xFF2A3555.toInt())
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

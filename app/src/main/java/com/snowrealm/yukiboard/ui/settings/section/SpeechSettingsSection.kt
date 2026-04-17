package com.snowrealm.yukiboard.ui.settings.section

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.snowrealm.yukiboard.app.net.AppHttpClient
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import com.snowrealm.yukiboard.ui.settings.YukiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 語音辨識設定 section（引擎選擇 / 語言 / 靜音靈敏度 / 連線測試）。
 *
 * 從 SpeechSettingsHelper 轉為正式 section，獨立卡片，不再嵌入 InputBehaviorSection。
 */
class SpeechSettingsSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String,
    private val scope: CoroutineScope,
    private val openAiApiKeyProvider: () -> String? = { null }
) {

    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("語音辨識"), null, null, children) {
            children += buildEngineSelector(context)
            children += buildServerUrlAndTest(context)
            children += buildLanguageSelector(context)
            children += buildSilenceSensitivitySelector(context)
        }
    }

    // ── 引擎選擇 ──

    private fun buildEngineSelector(context: Context): LinearLayout {
        val options = arrayOf("Android 內建", "OpenAI Whisper", "Faster-Whisper（自架・免費）")

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textContainer.addView(TextView(context).apply {
            text = tr("語音辨識引擎")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
        })
        textContainer.addView(TextView(context).apply {
            text = tr("Whisper 需 API Key / Faster-Whisper 自架免費")
            textSize = 11f
            setTextColor(0xFF6688AA.toInt())
        })
        row.addView(textContainer)

        row.addView(buildDropdownButton(context, options, prefs.speechEngineType.coerceIn(0, options.lastIndex), tr("選擇語音辨識引擎")) { which ->
            prefs.speechEngineType = which
        })
        return row
    }

    // ── Server URL + 連線測試 ──

    private fun buildServerUrlAndTest(context: Context): LinearLayout {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), 0, dp(context, 8), dp(context, 6))
        }

        // Faster-Whisper Server URL
        outer.addView(TextView(context).apply {
            text = "Faster-Whisper Server URL"
            textSize = 12f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 2))
        })
        val urlInput = EditText(context).apply {
            setText(prefs.fasterWhisperServerUrl)
            hint = "https://stt.snowrealm.pet"
            textSize = 13f
            setTextColor(0xFFE8F4FF.toInt())
            setHintTextColor(0xFF3A5580.toInt())
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
            background = GradientDrawable().apply {
                setColor(0xFF0D1628.toInt())
                cornerRadius = dp(context, 6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) prefs.fasterWhisperServerUrl = text.toString().trim()
            }
        }
        outer.addView(urlInput)

        // 測試結果
        val testStatus = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), 0)
        }

        // 測試按鈕列
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 6), 0, 0)
        }
        btnRow.addView(buildTestLink(context, tr("測試 Whisper 連線")) {
            testStatus.text = tr("測試中...")
            testStatus.setTextColor(0xFFFFCC00.toInt())
            testWhisperConnection(testStatus)
        })
        btnRow.addView(buildTestLink(context, tr("測試 Faster-Whisper")) {
            val url = urlInput.text.toString().trim()
            if (url.isBlank()) {
                testStatus.text = tr("✕ 請先輸入 Server URL")
                testStatus.setTextColor(0xFFFF6B6B.toInt())
                return@buildTestLink
            }
            prefs.fasterWhisperServerUrl = url
            testStatus.text = tr("測試中...")
            testStatus.setTextColor(0xFFFFCC00.toInt())
            testFasterWhisperConnection(url, testStatus)
        })
        outer.addView(btnRow)
        outer.addView(testStatus)
        return outer
    }

    // ── 語言選擇 ──

    private fun buildLanguageSelector(context: Context): LinearLayout {
        val langLabels = arrayOf(
            "自動（依輸入模式）", "混合語言（自動偵測）",
            "中文（繁體）", "英文", "日文", "韓文"
        )
        val langValues = arrayOf("auto", "mixed", "zh-TW", "en-US", "ja-JP", "ko-KR")
        val currentIdx = langValues.indexOf(prefs.speechLanguage).coerceAtLeast(0)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(context).apply {
            text = tr("語音辨識語言")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
        })
        textCol.addView(TextView(context).apply {
            text = tr("混合語言 = Whisper 自動偵測（中英日韓混說）")
            textSize = 11f
            setTextColor(0xFF6688AA.toInt())
        })
        row.addView(textCol)

        row.addView(buildDropdownButton(context, langLabels, currentIdx, tr("選擇語音辨識語言")) { which ->
            prefs.speechLanguage = langValues[which]
        })
        return row
    }

    // ── 靜音靈敏度 ──

    private fun buildSilenceSensitivitySelector(context: Context): LinearLayout {
        val labels = arrayOf("高靈敏（安靜環境）", "標準（預設）", "低靈敏（嘈雜環境）")
        val currentIdx = prefs.voiceSilenceSensitivity.coerceIn(0, 2)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(context).apply {
            text = tr("靜音偵測靈敏度")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
        })
        textCol.addView(TextView(context).apply {
            text = tr("嘈雜環境選「低」避免被截斷")
            textSize = 11f
            setTextColor(0xFF6688AA.toInt())
        })
        row.addView(textCol)

        row.addView(buildDropdownButton(context, labels, currentIdx, tr("選擇靜音偵測靈敏度")) { which ->
            prefs.voiceSilenceSensitivity = which
        })
        return row
    }

    // ── 共用 UI 元件 ──

    private fun buildDropdownButton(
        context: Context,
        options: Array<String>,
        initialIndex: Int,
        dialogTitle: String,
        onSelected: (Int) -> Unit
    ): TextView {
        return TextView(context).apply {
            text = options[initialIndex]
            textSize = 13f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            background = GradientDrawable().apply {
                setColor(0xFF1A2744.toInt())
                cornerRadius = dp(context, 6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
            setOnClickListener {
                val currentText = (it as TextView).text.toString()
                val currentIdx = options.indexOf(currentText).coerceAtLeast(0)
                AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(dialogTitle)
                    .setSingleChoiceItems(options, currentIdx) { dialog, which ->
                        onSelected(which)
                        text = options[which]
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun buildTestLink(context: Context, label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF66AAFF.toInt())
            setPadding(dp(context, 4), dp(context, 6), dp(context, 12), dp(context, 4))
            setOnClickListener { onClick() }
        }
    }

    // ── 連線測試 ──

    private fun testWhisperConnection(statusView: TextView) {
        val buildKey = com.snowrealm.yukiboard.BuildConfig.OPENAI_API_KEY
        val apiKey = buildKey.takeIf { it.isNotBlank() }
            ?: openAiApiKeyProvider()

        if (apiKey.isNullOrBlank()) {
            statusView.text = tr("✗ 沒有 OpenAI API Key — 請先到 AI 設定填入")
            statusView.setTextColor(0xFFFF6666.toInt())
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val silenceWav = generateSilenceWav(0.5)
                val body = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart(
                        "file", "test.wav",
                        silenceWav.toRequestBody("audio/wav".toMediaType())
                    )
                    .addFormDataPart("model", "whisper-1")
                    .addFormDataPart("language", "en")
                    .addFormDataPart("response_format", "json")
                    .build()

                val request = okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/audio/transcriptions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                val client = AppHttpClient.newBuilder(AppHttpClient.Profile.STANDARD).build()

                val response = client.newCall(request).execute()
                val code = response.code
                val responseBody = response.body?.string() ?: ""
                response.close()

                withContext(Dispatchers.Main) {
                    when (code) {
                        200 -> {
                            statusView.text = tr("✓ Whisper API 連線成功！可正常使用")
                            statusView.setTextColor(0xFF66CC88.toInt())
                        }
                        401 -> {
                            statusView.text = tr("✗ API Key 無效（401 Unauthorized）")
                            statusView.setTextColor(0xFFFF6666.toInt())
                        }
                        429 -> {
                            statusView.text = tr("✗ 額度用完或速率限制（429 Rate Limit）")
                            statusView.setTextColor(0xFFFF9933.toInt())
                        }
                        else -> {
                            val msg = try {
                                org.json.JSONObject(responseBody).optString("error", responseBody.take(100))
                            } catch (_: Exception) { responseBody.take(100) }
                            statusView.text = tr("✗ 錯誤 $code: $msg")
                            statusView.setTextColor(0xFFFF6666.toInt())
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusView.text = tr("✗ 連線失敗: ${e.message?.take(80)}")
                    statusView.setTextColor(0xFFFF6666.toInt())
                }
            }
        }
    }

    private fun testFasterWhisperConnection(serverUrl: String, statusView: TextView) {
        scope.launch(Dispatchers.IO) {
            try {
                val client = AppHttpClient.newBuilder(AppHttpClient.Profile.SHORT).build()
                val url = serverUrl.trimEnd('/') + "/health"
                val request = okhttp3.Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        statusView.text = tr("✓ Faster-Whisper 連線成功！$body")
                        statusView.setTextColor(0xFF66FF66.toInt())
                    } else {
                        statusView.text = "✕ HTTP ${response.code}: ${body.take(100)}"
                        statusView.setTextColor(0xFFFF6B6B.toInt())
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusView.text = tr("✕ 連線失敗: ${e.message}")
                    statusView.setTextColor(0xFFFF6B6B.toInt())
                }
            }
        }
    }

    // ── WAV 工具 ──

    private fun generateSilenceWav(durationSec: Double): ByteArray {
        val sampleRate = 16000
        val numSamples = (sampleRate * durationSec).toInt()
        val pcmData = ByteArray(numSamples * 2)

        val out = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(out).apply {
            writeBytes("RIFF")
            writeLE32(pcmData.size + 36)
            writeBytes("WAVE")
            writeBytes("fmt ")
            writeLE32(16)
            writeLE16(1)
            writeLE16(1)
            writeLE32(sampleRate)
            writeLE32(sampleRate * 2)
            writeLE16(2)
            writeLE16(16)
            writeBytes("data")
            writeLE32(pcmData.size)
            write(pcmData)
            flush()
        }
        return out.toByteArray()
    }

    private fun java.io.DataOutputStream.writeLE32(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }

    private fun java.io.DataOutputStream.writeLE16(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
    }
}

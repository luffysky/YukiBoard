package com.snowrealm.yukiboard.ui.settings.section

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * TTS 語音輸出 section（引擎選擇 / API Key / 語音音色 / 功能開關 / 語速 / 測試）。
 *
 * 從 SettingsActivity ttsSettingsSection() 抽出。
 */
class TtsSettingsSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String,
    private val onTestGoogleTts: (testText: String, keyInput: EditText, resetBtn: () -> Unit, setBtnText: (String) -> Unit) -> Unit
) {

    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("語音輸出 (TTS)"), null, null, children) {
            children += buildContent(context)
        }
    }

    private fun buildContent(context: Context): LinearLayout {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
        }

        // TTS 引擎選擇
        val engineOptions = arrayOf("Android 內建 (免費/離線)", "Google Cloud TTS (高品質)")
        val engineRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val engineText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        engineText.addView(TextView(context).apply {
            text = tr("TTS 引擎")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
        })
        engineText.addView(TextView(context).apply {
            text = tr("Google Cloud 需要 API Key")
            textSize = 11f
            setTextColor(0xFF6688AA.toInt())
        })
        engineRow.addView(engineText)

        val engineBtn = TextView(context).apply {
            text = engineOptions[prefs.ttsEngineType.coerceIn(0, engineOptions.lastIndex)]
            textSize = 12f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            background = GradientDrawable().apply {
                setColor(0xFF1A2744.toInt())
                cornerRadius = dp(context, 6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
            setOnClickListener {
                AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(tr("選擇 TTS 引擎"))
                    .setSingleChoiceItems(engineOptions, prefs.ttsEngineType) { dialog, which ->
                        prefs.ttsEngineType = which
                        text = engineOptions[which]
                        dialog.dismiss()
                    }
                    .show()
            }
        }
        engineRow.addView(engineBtn)
        outer.addView(engineRow)

        // Google Cloud TTS API Key
        val keyRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 8), 0, 0)
        }
        keyRow.addView(TextView(context).apply {
            text = "Google Cloud API Key"
            textSize = 12f
            setTextColor(0xFF8899BB.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
        })
        val keyInput = EditText(context).apply {
            setText(prefs.googleCloudTtsApiKey)
            textSize = 12f
            setTextColor(0xFFCCDDEE.toInt())
            setHintTextColor(0xFF445566.toInt())
            hint = "輸入 API Key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
            background = GradientDrawable().apply {
                setColor(0xFF0D1525.toInt())
                cornerRadius = dp(context, 4).toFloat()
                setStroke(1, 0xFF334466.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) prefs.googleCloudTtsApiKey = text.toString().trim()
            }
        }
        keyRow.addView(keyInput)
        outer.addView(keyRow)

        // 分隔線
        outer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)
            ).apply { setMargins(0, dp(context, 10), 0, dp(context, 6)) }
            setBackgroundColor(0xFF223344.toInt())
        })

        // 語音音色選擇
        outer.addView(buildVoiceSelector(context))

        // 功能開關
        outer.addView(SettingsUiHelper.switchRow(context, tr("翻譯朗讀"), tr("翻譯完成後自動朗讀結果"), prefs.ttsTranslateRead) { prefs.ttsTranslateRead = it })
        outer.addView(SettingsUiHelper.switchRow(context, tr("Yuki 語音"), tr("雪凜對話泡泡同步語音"), prefs.ttsYukiVoice) { prefs.ttsYukiVoice = it })
        outer.addView(SettingsUiHelper.switchRow(context, tr("候選字朗讀"), tr("無障礙：選字時唸出候選字"), prefs.ttsCandidateRead) { prefs.ttsCandidateRead = it })

        // 語速調整
        outer.addView(buildSpeedSlider(context))

        // TTS 測試
        val testInput = EditText(context).apply {
            setText("你好，我是雪凜。YukiBoard 語音輸出測試成功！")
            textSize = 13f
            setTextColor(0xFFE0E0E0.toInt())
            setHintTextColor(0xFF888888.toInt())
            hint = "輸入要測試的文字..."
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
            minLines = 1
            maxLines = 3
            background = GradientDrawable().apply {
                setColor(0xFF0D1B2A.toInt())
                cornerRadius = dp(context, 6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
        }
        outer.addView(testInput)

        val testBtn = TextView(context).apply {
            text = tr("▶ 測試語音輸出")
            textSize = 13f
            setTextColor(0xFF66AAFF.toInt())
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(0xFF1A2744.toInt())
                cornerRadius = dp(context, 6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
        }
        testBtn.setOnClickListener { btn ->
            val testText = testInput.text.toString().trim()
            if (testText.isBlank()) {
                Toast.makeText(context, "請輸入要測試的文字", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.googleCloudTtsApiKey = keyInput.text.toString().trim()
            btn.isEnabled = false
            (btn as? TextView)?.text = tr("⏳ 初始化中...")

            val mainHandler = Handler(Looper.getMainLooper())
            val resetBtn = {
                btn.isEnabled = true
                (btn as? TextView)?.text = tr("▶ 測試語音輸出")
            }

            when (prefs.ttsEngineType) {
                1 -> {
                    onTestGoogleTts(testText, keyInput, resetBtn) { label ->
                        (btn as? TextView)?.text = label
                    }
                }
                else -> {
                    // Android TextToSpeech
                    var rawTts: TextToSpeech? = null
                    rawTts = TextToSpeech(context.applicationContext) { status ->
                        mainHandler.post {
                            val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                            val vol = audioMgr.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            val maxVol = audioMgr.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                            val defaultEngine = rawTts?.defaultEngine ?: "unknown"

                            if (status != TextToSpeech.SUCCESS) {
                                Toast.makeText(context, "TTS 初始化失敗 status=$status engine=$defaultEngine", Toast.LENGTH_LONG).show()
                                rawTts?.shutdown()
                                resetBtn()
                                return@post
                            }

                            val selectedVoiceName = prefs.ttsVoiceName
                            val voiceApplied = if (selectedVoiceName.isNotEmpty()) {
                                val voice = rawTts?.voices?.firstOrNull { it.name == selectedVoiceName }
                                if (voice != null) { rawTts?.voice = voice; true } else false
                            } else false
                            if (!voiceApplied) {
                                rawTts?.setLanguage(java.util.Locale.TAIWAN)
                            }
                            val voiceLabel = if (voiceApplied) selectedVoiceName.substringAfterLast("#").take(20) else "default"
                            val info = "engine=$defaultEngine voice=$voiceLabel vol=$vol/$maxVol"
                            Toast.makeText(context, "TTS: $info", Toast.LENGTH_LONG).show()

                            if (vol == 0) {
                                audioMgr.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxVol / 2, android.media.AudioManager.FLAG_SHOW_UI)
                            }

                            (btn as? TextView)?.text = tr("🔊 播放中... ($info)")
                            rawTts?.setSpeechRate(prefs.ttsSpeed)
                            rawTts?.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "test_utterance")

                            mainHandler.postDelayed({
                                rawTts?.shutdown()
                                resetBtn()
                            }, 5000)
                        }
                    }
                }
            }
        }
        outer.addView(testBtn)

        return outer
    }

    private fun buildVoiceSelector(context: Context): LinearLayout {
        val voiceRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 4), 0, dp(context, 4))
        }
        val voiceTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
        }
        voiceTextCol.addView(TextView(context).apply {
            text = tr("語音音色")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
        })
        voiceTextCol.addView(TextView(context).apply {
            text = tr("選擇 TTS 語音")
            textSize = 10f
            setTextColor(0xFF667788.toInt())
        })
        voiceRow.addView(voiceTextCol)

        val currentVoiceName = prefs.ttsVoiceName
        val voiceBtn = TextView(context).apply {
            text = if (currentVoiceName.isEmpty()) "系統預設" else currentVoiceName.substringAfterLast("#").take(30)
            textSize = 12f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            background = GradientDrawable().apply {
                setColor(0xFF1A2A3A.toInt())
                cornerRadius = dp(context, 6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
        }
        voiceBtn.setOnClickListener { btn ->
            val handler = Handler(Looper.getMainLooper())
            val tmpTts = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    handler.post { Toast.makeText(context, "TTS 初始化失敗", Toast.LENGTH_SHORT).show() }
                    return@TextToSpeech
                }
                handler.post {
                    val voices = try {
                        (btn.tag as? TextToSpeech)?.voices?.toList() ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    if (voices.isEmpty()) {
                        Toast.makeText(context, "無可用語音", Toast.LENGTH_SHORT).show()
                        (btn.tag as? TextToSpeech)?.shutdown()
                        btn.tag = null
                        return@post
                    }

                    val sorted = voices.sortedWith(compareBy<Voice> { it.locale.language }
                        .thenBy { it.locale.country }
                        .thenByDescending { it.quality }
                        .thenBy { it.name })

                    fun voiceLabel(v: Voice): String {
                        val lang = v.locale.displayLanguage
                        val country = v.locale.displayCountry.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
                        val q = if (v.quality >= 400) " ★" else if (v.quality >= 300) " ☆" else ""
                        val net = if (v.isNetworkConnectionRequired) " ☁" else ""
                        return "$lang$country$q$net — ${v.name}"
                    }

                    val dialogLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(context, 16), dp(context, 12), dp(context, 16), 0)
                    }

                    val searchBox = EditText(context).apply {
                        hint = "搜尋語言或音色名稱…"
                        textSize = 14f
                        setTextColor(0xFFE8F4FF.toInt())
                        setHintTextColor(0xFF556677.toInt())
                        background = GradientDrawable().apply {
                            setColor(0xFF1A2A3A.toInt())
                            cornerRadius = dp(context, 8).toFloat()
                            setStroke(1, 0xFF334455.toInt())
                        }
                        setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
                        inputType = InputType.TYPE_CLASS_TEXT
                        isSingleLine = true
                    }
                    dialogLayout.addView(searchBox, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(context, 8) })

                    val listView = ListView(context)
                    dialogLayout.addView(listView, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 360)
                    ))

                    val allItems = listOf<Pair<String, Voice?>>(
                        "🔄 系統預設" to null
                    ) + sorted.map { voiceLabel(it) to it }

                    var filteredItems = allItems.toMutableList()
                    val adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_list_item_single_choice,
                        filteredItems.map { it.first }.toMutableList()
                    )
                    listView.adapter = adapter
                    listView.choiceMode = ListView.CHOICE_MODE_SINGLE

                    val currentIdx = if (prefs.ttsVoiceName.isEmpty()) 0
                    else allItems.indexOfFirst { it.second?.name == prefs.ttsVoiceName }.let { if (it >= 0) it else 0 }
                    listView.setItemChecked(currentIdx, true)
                    listView.setSelection(currentIdx)

                    val dlg = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(tr("選擇語音音色 (${sorted.size} 種)"))
                        .setView(dialogLayout)
                        .setOnDismissListener {
                            handler.postDelayed({
                                (btn.tag as? TextToSpeech)?.shutdown()
                                btn.tag = null
                            }, 3000)
                        }
                        .show()

                    searchBox.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            val q = s?.toString()?.trim()?.lowercase() ?: ""
                            filteredItems = if (q.isEmpty()) allItems.toMutableList()
                            else allItems.filter { it.first.lowercase().contains(q) }.toMutableList()
                            adapter.clear()
                            adapter.addAll(filteredItems.map { it.first })
                            adapter.notifyDataSetChanged()
                        }
                    })

                    listView.setOnItemClickListener { _, _, position, _ ->
                        val item = filteredItems.getOrNull(position) ?: return@setOnItemClickListener
                        if (item.second == null) {
                            prefs.ttsVoiceName = ""
                            (btn as? TextView)?.text = tr("系統預設")
                        } else {
                            val selected = item.second ?: return@setOnItemClickListener
                            prefs.ttsVoiceName = selected.name
                            (btn as? TextView)?.text = item.first.take(35)

                            val tmpEngine = btn.tag as? TextToSpeech
                            if (tmpEngine != null) {
                                tmpEngine.voice = selected
                                tmpEngine.setSpeechRate(prefs.ttsSpeed)
                                tmpEngine.speak("你好，我是雪凜", TextToSpeech.QUEUE_FLUSH, null, "preview")
                            }
                        }
                        dlg.dismiss()
                    }
                }
            }
            btn.tag = tmpTts
        }
        voiceRow.addView(voiceBtn)
        return voiceRow
    }

    private fun buildSpeedSlider(context: Context): LinearLayout {
        val speedRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 6), 0, dp(context, 4))
        }
        speedRow.addView(TextView(context).apply {
            text = tr("語速")
            textSize = 13f
            setTextColor(0xFFAABBCC.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val speedLabel = TextView(context).apply {
            text = "%.1fx".format(prefs.ttsSpeed)
            textSize = 12f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
        }
        val speedBar = SeekBar(context).apply {
            max = 30
            progress = ((prefs.ttsSpeed - 0.5f) / 0.05f).toInt().coerceIn(0, 30)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val speed = 0.5f + progress * 0.05f
                    speedLabel.text = "%.1fx".format(speed)
                    if (fromUser) prefs.ttsSpeed = speed
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        speedRow.addView(speedBar)
        speedRow.addView(speedLabel)
        return speedRow
    }
}

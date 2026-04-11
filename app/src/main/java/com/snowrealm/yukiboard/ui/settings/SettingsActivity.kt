package com.snowrealm.yukiboard.ui.settings

import android.content.Intent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.theme.ThemeManager
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PICK_BG_IMAGE = 9001
        private const val REQUEST_PICK_BG_VIDEO = 9002
        private const val RC_MEDIA_PERMISSION = 9003
    }

    private var pendingMediaAction: (() -> Unit)? = null

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRestarting = false
    private lateinit var themeManager: ThemeManager
    private lateinit var prefs: YukiPreferences
    private var currentLang = SettingsStrings.Lang.ZH

    /** 多語言翻譯捷徑 */
    private fun tr(zh: String) = SettingsStrings.tr(zh, currentLang)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            onCreateInternal()
        } catch (e: Throwable) {
            val msg = e.stackTraceToString().take(3000)
            android.util.Log.e("SettingsActivity", "CRASH in onCreate", e)
            val errView = TextView(this).apply {
                text = tr("YukiBoard 啟動錯誤：\n\n$msg\n\n請截圖回報")
                textSize = 11f
                setTextColor(0xFFFF5555.toInt())
                setBackgroundColor(0xFF111111.toInt())
                setPadding(32, 64, 32, 32)
                setTextIsSelectable(true)
            }
            setContentView(ScrollView(this).apply { addView(errView) })
        }
    }

    private fun onCreateInternal() {
        themeManager = ThemeManager.getInstance(this)
        prefs = YukiPreferences(this)
        currentLang = SettingsStrings.Lang.fromCode(prefs.settingsLanguage)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF1A2035.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
        }

        // ── 標題列（含語言選單）──
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        titleRow.addView(TextView(this).apply {
            text = tr("YukiBoard 設定")
            textSize = 24f
            setTextColor(0xFFE8F4FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        // 語言下拉選單按鈕
        titleRow.addView(Button(this).apply {
            text = currentLang.displayName
            setTextColor(0xFFE8F4FF.toInt())
            textSize = 12f
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            background = GradientDrawable().apply {
                setColor(0xFF2A3A5C.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            setOnClickListener { btn ->
                val popup = android.widget.PopupMenu(this@SettingsActivity, btn)
                SettingsStrings.Lang.entries.forEachIndexed { i, lang ->
                    popup.menu.add(0, i, i, lang.displayName)
                }
                popup.setOnMenuItemClickListener { item ->
                    val selectedLang = SettingsStrings.Lang.entries[item.itemId]
                    prefs.settingsLanguage = selectedLang.code
                    currentLang = selectedLang
                    // 重建 Activity 以套用新語言
                    isRestarting = true
                    recreate()
                    true
                }
                popup.show()
            }
        })
        root.addView(titleRow)

        // ── 按鈕列（獨立一行，不擠壓標題）──
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(16))
        }
        btnRow.addView(Button(this).apply {
            text = tr("功能說明")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            background = GradientDrawable().apply {
                setColor(0xFF2A3A5C.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, HelpActivity::class.java))
            }
        })
        btnRow.addView(Button(this).apply {
            text = tr("Z幣錢包")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            background = GradientDrawable().apply {
                setColor(0xFFD4A017.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, com.snowrealm.yukiboard.ui.currency.ZCoinWalletActivity::class.java))
            }
        })
        btnRow.addView(Button(this).apply {
            val auth = com.snowrealm.yukiboard.auth.SnowRealmAuthClient(this@SettingsActivity)
            text = if (auth.isLoggedIn) auth.currentDisplayName ?: "帳號" else "登入"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            background = GradientDrawable().apply {
                setColor(if (auth.isLoggedIn) 0xFF1B6B3A.toInt() else 0xFF4A90E2.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, com.snowrealm.yukiboard.ui.auth.LoginActivity::class.java))
            }
        })
        root.addView(btnRow)

        // ═══════════════════════════════════════════════════════
        // 1. AI 功能
        // ═══════════════════════════════════════════════════════
        val aiChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("AI 功能"), prefs.aiEnabled, { prefs.aiEnabled = it }, aiChildren) {
            aiChildren += switchRow(tr("AI 補字預測"), tr("智慧預測下一個詞"), prefs.aiPrediction) { prefs.aiPrediction = it }
            aiChildren += switchRow(tr("雪凜寫手"), tr("幫你寫、回覆、換說法"), prefs.aiWriter) { prefs.aiWriter = it }
            aiChildren += switchRow(tr("即時翻譯"), tr("中英日等多語言翻譯"), prefs.aiTranslate) { prefs.aiTranslate = it }
            aiChildren += switchRow(tr("情聖月老"), tr("讓訊息更有魅力"), prefs.aiCupid) { prefs.aiCupid = it }
            aiChildren += switchRow(tr("AI 語音 (Whisper)"), tr("OpenAI 語音辨識"), prefs.aiWhisper) { prefs.aiWhisper = it }
            aiChildren += switchRow(tr("文法糾錯"), tr("一鍵修正錯字和文法"), prefs.aiGrammarCheck) { prefs.aiGrammarCheck = it }
            aiChildren += switchRow(tr("語氣調整"), tr("正式/casual/可愛/專業"), prefs.aiToneAdjust) { prefs.aiToneAdjust = it }
            aiChildren += switchRow(tr("摘要精簡"), tr("長文縮成一句話"), prefs.aiSummarize) { prefs.aiSummarize = it }
            aiChildren += switchRow(tr("迷因生成"), tr("產生流行語和梗圖文字"), prefs.aiMemeText) { prefs.aiMemeText = it }
            aiChildren += switchRow(tr("Emoji 翻譯"), tr("文字與 Emoji 互轉"), prefs.aiEmojiTranslate) { prefs.aiEmojiTranslate = it }
            aiChildren += switchRow(tr("快速回覆"), tr("建議整句回覆"), prefs.aiQuickReply) { prefs.aiQuickReply = it }
            aiChildren += switchRow(tr("程式碼補全"), tr("開發者模式中 AI 補完"), prefs.aiCodeComplete) { prefs.aiCodeComplete = it }
            aiChildren += switchRow(tr("正則產生器"), tr("描述需求產生 Regex"), prefs.aiRegexGen) { prefs.aiRegexGen = it }
            aiChildren += switchRow(tr("Commit 訊息"), tr("貼上 diff 產生 commit message"), prefs.aiCommitMessage) { prefs.aiCommitMessage = it }
            aiChildren += switchRow(tr("雪凜聊天"), tr("跟雪凜對話，幫你想怎麼說"), prefs.aiYukiChat) { prefs.aiYukiChat = it }
            aiChildren += switchRow(tr("心情日記"), tr("被動偵測打字情緒"), prefs.aiMoodDiary) { prefs.aiMoodDiary = it }
            aiChildren += switchRow(tr("天氣助理"), tr("天氣資訊與穿衣建議"), prefs.aiWeather) { prefs.aiWeather = it }
            aiChildren += switchRow(tr("防詐偵測"), tr("分析對話內容偵測可疑模式"), prefs.aiFraudDetect) { prefs.aiFraudDetect = it }
            aiChildren += switchRow(tr("被動防詐掃描"), tr("打字時自動掃描上下文"), prefs.aiFraudPassiveScan) { prefs.aiFraudPassiveScan = it }
            aiChildren += switchRow(tr("連結風險掃描"), tr("自動偵測可疑網址"), prefs.aiFraudUrlScan) { prefs.aiFraudUrlScan = it }

            // 防詐模型更新按鈕
            run {
                val modelManager = com.snowrealm.yukiboard.security.FraudModelManager(this)
                val modelStatusText = TextView(this).apply {
                    text = modelManager.getStatusSummary()
                    textSize = 11f
                    setTextColor(0xFF6688AA.toInt())
                    setPadding(dp(8), dp(2), dp(8), dp(4))
                }
                aiChildren += modelStatusText

                aiChildren += Button(this).apply {
                    text = tr("防詐模型更新")
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 13f
                    background = GradientDrawable().apply {
                        setColor(0xFFE25C4A.toInt())
                        cornerRadius = dp(8).toFloat()
                    }
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(dp(8), dp(4), 0, dp(4)) }
                    setOnClickListener {
                        isEnabled = false
                        text = tr("檢查中...")
                        val supabaseUrl = com.snowrealm.yukiboard.BuildConfig.YUKIBOARD_SUPABASE_URL
                        val supabaseKey = com.snowrealm.yukiboard.BuildConfig.YUKIBOARD_SUPABASE_ANON_KEY
                        val appVersionCode = com.snowrealm.yukiboard.BuildConfig.VERSION_CODE
                        activityScope.launch {
                            try {
                                modelManager.manualUpdate(
                                    supabaseUrl = supabaseUrl,
                                    supabaseAnonKey = supabaseKey,
                                    appVersionCode = appVersionCode,
                                    callback = object : com.snowrealm.yukiboard.security.FraudModelManager.DownloadCallback {
                                        override fun onProgress(percent: Int) {
                                            launch(Dispatchers.Main) {
                                                text = tr("更新中 $percent%...")
                                            }
                                        }
                                        override fun onComplete(success: Boolean, message: String) {
                                            launch(Dispatchers.Main) {
                                                isEnabled = true
                                                text = tr("防詐模型更新")
                                                modelStatusText.text = modelManager.getStatusSummary()
                                                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                            catch (e: Exception) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    isEnabled = true
                                    text = tr("檢查失敗")
                                }
                            }
                        }
                    }
                }
            }

            // AI 設定按鈕
            aiChildren += Button(this).apply {
                text = tr("AI 設定 / Provider")
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                background = GradientDrawable().apply {
                    setColor(0xFF4A90E2.toInt())
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(8), dp(8), 0, dp(4)) }
                setOnClickListener {
                    startActivity(Intent(this@SettingsActivity, AISettingsActivity::class.java))
                }
            }
        })

        // ═══════════════════════════════════════════════════════
        // 2. 雪凜行為
        // ═══════════════════════════════════════════════════════
        val yukiChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("雪凜行為"), prefs.yukiSarcasticComments, null, yukiChildren) {
            yukiChildren += switchRow(tr("微吐槽"), tr("雪凜偶爾的有趣短評"), prefs.yukiSarcasticComments) { prefs.yukiSarcasticComments = it }
            yukiChildren += switchRow(tr("情緒冷靜期"), tr("偵測激烈文字時暫停提醒"), prefs.yukiEmotionalCooldown) { prefs.yukiEmotionalCooldown = it }
            yukiChildren += switchRow(tr("心情日記被動偵測"), tr("自動分析打字情緒"), prefs.yukiMoodDiaryPassive) { prefs.yukiMoodDiaryPassive = it }
            yukiChildren += switchRow(tr("天氣主動提醒"), tr("天氣變化時雪凜提醒"), prefs.yukiWeatherAlert) { prefs.yukiWeatherAlert = it }
            yukiChildren += switchRow(tr("深夜關心"), tr("深夜打字時的溫馨提醒"), prefs.yukiLateNightComment) { prefs.yukiLateNightComment = it }
        })

        // ═══════════════════════════════════════════════════════
        // 3. 感測器
        // ═══════════════════════════════════════════════════════
        val sensorChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("感測器"), null, null, sensorChildren) {
            sensorChildren += switchRow(tr("搖手機復原"), tr("搖晃手機觸發 Ctrl+Z"), prefs.shakeToUndoEnabled) { prefs.shakeToUndoEnabled = it }
            sensorChildren += switchRow(tr("陀螺儀游標"), tr("長按空白鍵 + 傾斜控制游標"), prefs.gyroscopeCursorEnabled) { prefs.gyroscopeCursorEnabled = it }

            // 陀螺儀靈敏度滑桿
            val sensitivityLabel = TextView(this).apply {
                text = tr("靈敏度: %.1f").format(prefs.gyroscopeSensitivity)
                textSize = 12f
                setTextColor(0xFF7FD8FF.toInt())
                setPadding(dp(12), dp(4), 0, dp(4))
            }
            sensorChildren += sensitivityLabel
            sensorChildren += SeekBar(this).apply {
                max = 25
                progress = ((prefs.gyroscopeSensitivity - 0.5f) * 10).toInt()
                setPadding(dp(12), dp(4), dp(12), dp(4))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        val v = 0.5f + p * 0.1f
                        prefs.gyroscopeSensitivity = v
                        sensitivityLabel.text = tr("靈敏度: %.1f").format(v)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }

            sensorChildren += switchRow(tr("App 自動切換模式"), tr("偵測前景 App 自動切換模式"), prefs.appAutoDetectEnabled) { prefs.appAutoDetectEnabled = it }
        })

        // ═══════════════════════════════════════════════════════
        // 4. 動畫特效
        // ═══════════════════════════════════════════════════════
        val animChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("動畫特效"), null, null, animChildren) {
            animChildren += switchRow(tr("流光動畫"), tr("OpenGL ES 流光覆蓋效果"), prefs.flowLightEnabled) { prefs.flowLightEnabled = it }
            animChildren += switchRow(tr("雪凜角色"), tr("顯示雪凜浮動角色（Lottie / VRM）"), prefs.characterAnimEnabled) { prefs.characterAnimEnabled = it }
            animChildren += switchRow(tr("VRM 3D 角色"), tr("3D VRM 模型取代 Lottie 角色"), prefs.vrmCharacterEnabled) { prefs.vrmCharacterEnabled = it }
            animChildren += switchRow(tr("按鍵粒子效果"), tr("打字時的粒子動畫"), prefs.keyParticleEffect) { prefs.keyParticleEffect = it }
            animChildren += switchRow(tr("按鍵震動"), tr("按鍵時的觸覺回饋"), prefs.keyPressVibration) { prefs.keyPressVibration = it }
            animChildren += switchRow(tr("按鍵音效"), tr("按鍵時的聲音回饋"), prefs.keySoundEnabled) { prefs.keySoundEnabled = it }

            // 鍵盤動畫主題選擇
            val kbAnimOptions = arrayOf("無", "星空 Starfield", "海洋 Ocean", "餘燼 Ember", "矩陣 Matrix", "冰霜 Frost", "熔岩 Lava", "彩虹 Rainbow", "雷電 Thunder")
            val kbAnimValues = arrayOf("none", "starfield", "ocean", "ember", "matrix", "frost", "lava", "rainbow", "thunder")
            val kbAnimRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            kbAnimRow.addView(TextView(this).apply {
                text = tr("動畫主題")
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val kbAnimBtn = TextView(this).apply {
                val curIdx = kbAnimValues.indexOf(prefs.toolbarAnimStyle).coerceAtLeast(0)
                text = kbAnimOptions[curIdx]
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                setOnClickListener { btn ->
                    val cur = kbAnimValues.indexOf(prefs.toolbarAnimStyle).coerceAtLeast(0)
                    android.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle(tr("選擇動畫主題"))
                        .setSingleChoiceItems(kbAnimOptions, cur) { dialog, which ->
                            prefs.toolbarAnimStyle = kbAnimValues[which]
                            (btn as TextView).text = kbAnimOptions[which]
                            dialog.dismiss()
                        }
                        .setNegativeButton(tr("取消"), null)
                        .show()
                }
            }
            kbAnimRow.addView(kbAnimBtn)
            animChildren += kbAnimRow

            // 按鍵音色選擇
            val styleOptions = arrayOf("鋼琴 (Piano)", "木琴 (Marimba)", "柔和 (Soft)", "吉他 (Guitar)", "音樂盒 (Music Box)", "豎琴 (Harp)", "風琴 (Organ)", "鼓組 (Drum)", "電子鼓 (E-Drum)", "Beatbox")
            val styleValues = arrayOf("piano", "marimba", "soft", "guitar", "music_box", "harp", "organ", "drum", "edrum", "beatbox")
            val styleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            val styleLabel = TextView(this).apply {
                text = tr("音色風格")
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val styleBtn = TextView(this).apply {
                val currentIdx = styleValues.indexOf(prefs.keySoundStyle).coerceAtLeast(0)
                text = styleOptions[currentIdx]
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                setOnClickListener {
                    val cur = styleValues.indexOf(prefs.keySoundStyle).coerceAtLeast(0)
                    android.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle(tr("選擇音色風格"))
                        .setSingleChoiceItems(styleOptions, cur) { dialog, which ->
                            prefs.keySoundStyle = styleValues[which]
                            (it as TextView).text = styleOptions[which]
                            dialog.dismiss()
                        }
                        .setNegativeButton(tr("取消"), null)
                        .show()
                }
            }
            styleRow.addView(styleLabel)
            styleRow.addView(styleBtn)
            animChildren += styleRow

            // 按鍵音量滑桿
            val volRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(8))
            }
            volRow.addView(TextView(this).apply {
                text = tr("音量：${(prefs.keySoundVolume * 100).toInt()}%")
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                tag = "volLabel"
            })
            volRow.addView(SeekBar(this).apply {
                max = 100
                progress = (prefs.keySoundVolume * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) {
                            prefs.keySoundVolume = p / 100f
                            volRow.findViewWithTag<TextView>("volLabel")?.text = tr("音量：${p}%")
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
            animChildren += volRow

            // 音階對照表（點擊展開/收合）
            val noteMapContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(8))
            }
            val noteMapContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackgroundColor(0xFF1E1E2E.toInt())
            }
            val noteMapToggle = TextView(this).apply {
                text = tr("▶ 音階對照表")
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener {
                    if (noteMapContent.visibility == View.GONE) {
                        noteMapContent.visibility = View.VISIBLE
                        (it as TextView).text = tr("▼ 音階對照表")
                    } else {
                        noteMapContent.visibility = View.GONE
                        (it as TextView).text = tr("▶ 音階對照表")
                    }
                }
            }
            // 對照表內容
            val noteLines = listOf(
                "── 數字列（高音）──",
                "1���G5  2→G#5  3→A5  4���A#5  5→B5",
                "6→C6  7→C#6  8→D6  9→D#6  0→E6",
                "",
                "── Q 列（中高音）──",
                "Q→A4  W→A#4  E→B4  R→C5  T→C#5",
                "Y→D5  U→D#5  I→E5  O→F5  P→F#5",
                "",
                "── A 列（中低音）──",
                "A→C4  S→C#4  D→D4  F→D#4  G→E4",
                "H→F4  J→F#4  K→G4  L→G#4",
                "",
                "── Z 列（低音）──",
                "Z→F3  X→F#3  C→G3  V→G#3",
                "B���A3  N→A#3  M→B3",
                "",
                "── 功能鍵 ──",
                "空白→C4  ⌫→D#3  換行→E3",
                "",
                "完整 F3→E6（3 個八度・36 鍵不重複）",
                "♯ = 黑鍵（半音）  其餘 = 白鍵"
            )
            noteLines.forEach { line ->
                noteMapContent.addView(TextView(this).apply {
                    text = line
                    setTextColor(if (line.startsWith("──") || line.startsWith("♯")) 0xFFFFD54F.toInt() else 0xFFCCCCCC.toInt())
                    textSize = if (line.startsWith("──")) 13f else 12f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding(0, if (line.isEmpty()) dp(2) else dp(1), 0, dp(1))
                })
            }
            noteMapContainer.addView(noteMapToggle)
            noteMapContainer.addView(noteMapContent)
            animChildren += noteMapContainer
        })

        // ═══════════════════════════════════════════════════════
        // 5. 遊戲
        // ═══════════════════════════════════════════════════════
        val gameChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("遊戲"), prefs.gamesEnabled, { prefs.gamesEnabled = it }, gameChildren) {
            gameChildren += switchRow(tr("背景音樂"), tr("遊戲背景音樂"), prefs.gameBackgroundMusic) { prefs.gameBackgroundMusic = it }
            gameChildren += switchRow(tr("音效"), tr("遊戲音效"), prefs.gameSoundEffects) { prefs.gameSoundEffects = it }
        })

        // ═══════════════════════════════════════════════════════
        // 5.5 剪貼簿
        // ═══════════════════════════════════════════════════════
        val clipboardChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("剪貼簿"), null, null, clipboardChildren) {
            clipboardChildren += switchRow(tr("加密功能"), tr("允許加密剪貼簿項目（AES-256）"), prefs.clipboardEncryptionEnabled) { prefs.clipboardEncryptionEnabled = it }
            clipboardChildren += switchRow(tr("敏感偵測"), tr("自動偵測信用卡、身分證等敏感內容"), prefs.clipboardAutoDetectSensitive) { prefs.clipboardAutoDetectSensitive = it }
            clipboardChildren += switchRow(tr("跨裝置同步"), tr("透過 Insight 同步剪貼簿（需登入）"), prefs.clipboardSyncEnabled) { prefs.clipboardSyncEnabled = it }
        })

        // ═══════════════════════════════════════════════════════
        // 6. 輸入行為 + 外觀
        // ═══════════════════════════════════════════════════════
        val inputChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("輸入行為"), null, null, inputChildren) {
            inputChildren += switchRow(tr("智慧中英切換"), tr("自動判斷注音/英文意圖（免手動切換）"), prefs.autoInputMode) { prefs.autoInputMode = it }
            inputChildren += switchRow(tr("滑動輸入"), tr("滑動手勢輸入"), prefs.swipeInput) { prefs.swipeInput = it }
            inputChildren += switchRow(tr("滑動軌跡特效"), tr("滑動時顯示紫色光暈路徑"), prefs.swipeTrailEnabled) { prefs.swipeTrailEnabled = it }
            inputChildren += switchRow(tr("顯示觸控面積"), tr("打字時顯示手指接觸範圍，方便確認是否按到"), prefs.showTouchArea) { prefs.showTouchArea = it }
            inputChildren += switchRow(tr("自動更正"), tr("英文自動更正"), prefs.autoCorrect) { prefs.autoCorrect = it }
            inputChildren += switchRow(tr("RIME 詞庫"), tr("啟用 RIME 大型注音詞庫（更準確的候選字）"), prefs.rimeDictEnabled) { prefs.rimeDictEnabled = it }
            inputChildren += switchRow(tr("滑動自我學習"), tr("記住你選過的滑動候選，越用越準"), prefs.swipeSelfLearn) { prefs.swipeSelfLearn = it }
            inputChildren += switchRow(tr("糾錯自我學習"), tr("記住你接受/拒絕的校正，越改越聰明"), prefs.correctionSelfLearn) { prefs.correctionSelfLearn = it }
            inputChildren += switchRow(tr("Snippet 展開"), tr("開發者模式：縮寫 + Tab 展開"), prefs.snippetEnabled) { prefs.snippetEnabled = it }

            inputChildren += switchRow(tr("私房話模式"), tr("AES-256 加密訊息（Enter 送出時加密）"), prefs.secretMessageMode) { prefs.secretMessageMode = it }
            // 悄悄話密碼設定
            val pwdRow = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            pwdRow.addView(TextView(this@SettingsActivity).apply {
                text = tr("悄悄話密碼")
                textSize = 14f
                setTextColor(0xFFE8F4FF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
            })
            val pwdBtn = TextView(this@SettingsActivity).apply {
                text = if (prefs.secretMessagePassword.isEmpty()) "未設定（使用預設金鑰）" else "已設定 ●●●●"
                textSize = 12f
                setTextColor(0xFF9ECFFF.toInt())
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF1A2A3A.toInt())
                    cornerRadius = dp(6).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
            }
            pwdBtn.setOnClickListener {
                val input = android.widget.EditText(this@SettingsActivity).apply {
                    hint = "輸入新密碼（留空=使用預設金鑰）"
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setText(prefs.secretMessagePassword)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
                android.app.AlertDialog.Builder(this@SettingsActivity, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(tr("設定悄悄話密碼"))
                    .setMessage("雙方需要使用相同密碼才能加解密。\n留空則使用舊版預設金鑰。")
                    .setView(input)
                    .setPositiveButton(tr("確定")) { _, _ ->
                        prefs.secretMessagePassword = input.text.toString()
                        (it as TextView).text = if (prefs.secretMessagePassword.isEmpty()) "未設定（使用預設金鑰）" else "已設定 ●●●●"
                    }
                    .setNegativeButton(tr("取消"), null)
                    .show()
            }
            pwdRow.addView(pwdBtn)
            inputChildren += pwdRow
            inputChildren += switchRow(tr("語氣偵測"), tr("偵測打字語氣"), prefs.toneDetection) { prefs.toneDetection = it }
            inputChildren += switchRow(tr("懸浮面板"), tr("啟用懸浮面板功能"), prefs.floatingPanelEnabled) { prefs.floatingPanelEnabled = it }
            inputChildren += switchRow(tr("遠端鍵盤"), tr("藍牙/WiFi 遠端控制"), prefs.remoteKeyboardEnabled) { prefs.remoteKeyboardEnabled = it }

            // 語音引擎選擇
            inputChildren += speechEngineSelector()
            inputChildren += speechLanguageSelector()
            inputChildren += voiceSilenceSensitivitySelector()
            inputChildren += switchRow(tr("語音後處理"), tr("AI 自動清除口語贅詞（嗯、那個…）"), prefs.voicePostProcessEnabled) { prefs.voicePostProcessEnabled = it }
        })

        // ═══════════════════════════════════════════════════════
        // 功能列項目
        // ═══════════════════════════════════════════════════════
        val toolbarChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("功能列"), null, null, toolbarChildren) {
            toolbarChildren += switchRow("🐾 Claw AI", tr("AI 對話助手"), prefs.toolbarClaw) { prefs.toolbarClaw = it }
            toolbarChildren += switchRow("🎤 ${tr("語音")}", tr("語音輸入"), prefs.toolbarVoice) { prefs.toolbarVoice = it }
            toolbarChildren += switchRow("🌐 ${tr("翻譯")}", tr("即時翻譯面板"), prefs.toolbarTranslate) { prefs.toolbarTranslate = it }
            toolbarChildren += switchRow("✍ ${tr("手寫")}", tr("手寫辨識輸入"), prefs.toolbarHandwriting) { prefs.toolbarHandwriting = it }
            toolbarChildren += switchRow("📚 ${tr("字典")}", tr("字典查詢面板"), prefs.toolbarDictionary) { prefs.toolbarDictionary = it }
            toolbarChildren += switchRow("🎓 ${tr("學語言")}", tr("語言學習面板"), prefs.toolbarLangLearn) { prefs.toolbarLangLearn = it }
            toolbarChildren += switchRow("😀 ${tr("表情")}", tr("Emoji 表情面板"), prefs.toolbarEmoji) { prefs.toolbarEmoji = it }
            toolbarChildren += switchRow("顔 ${tr("顔文字")}", tr("日式顔文字面板"), prefs.toolbarKaomoji) { prefs.toolbarKaomoji = it }
            toolbarChildren += switchRow("🖼 ${tr("貼圖")}", tr("貼圖面板"), prefs.toolbarSticker) { prefs.toolbarSticker = it }
            toolbarChildren += switchRow("GIF", tr("GIF 搜尋面板"), prefs.toolbarGif) { prefs.toolbarGif = it }
            toolbarChildren += switchRow("📋 ${tr("剪貼簿")}", tr("剪貼簿歷史面板"), prefs.toolbarClipboard) { prefs.toolbarClipboard = it }
            toolbarChildren += switchRow("🧮 ${tr("計算機")}", tr("快速計算機"), prefs.toolbarCalculator) { prefs.toolbarCalculator = it }
            toolbarChildren += switchRow("💬 ${tr("常用語")}", tr("常用語快貼"), prefs.toolbarPhrase) { prefs.toolbarPhrase = it }
            toolbarChildren += switchRow("📖 ${tr("詞庫")}", tr("使用者自訂詞庫"), prefs.toolbarUserDict) { prefs.toolbarUserDict = it }
            toolbarChildren += switchRow("🔐 ${tr("解密")}", tr("私房話解密面板"), prefs.toolbarDecrypt) { prefs.toolbarDecrypt = it }
            toolbarChildren += switchRow("🎯 ${tr("色碼")}", tr("色碼選取器"), prefs.toolbarColorPicker) { prefs.toolbarColorPicker = it }
            toolbarChildren += switchRow("📏 ${tr("換算")}", tr("單位換算工具"), prefs.toolbarUnitConverter) { prefs.toolbarUnitConverter = it }
            toolbarChildren += switchRow("🎹 ${tr("音樂")}", tr("音樂鍵盤"), prefs.toolbarMusic) { prefs.toolbarMusic = it }
            toolbarChildren += switchRow("🔗 ${tr("遠端")}", tr("遠端鍵盤連線"), prefs.toolbarRemote) { prefs.toolbarRemote = it }
            toolbarChildren += switchRow("🎨 ${tr("主題")}", tr("主題瀏覽切換"), prefs.toolbarTheme) { prefs.toolbarTheme = it }
        })

        // ═══════════════════════════════════════════════════════
        // 6.5 TTS 語音輸出
        // ═══════════════════════════════════════════════════════
        val ttsChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("語音輸出 (TTS)"), null, null, ttsChildren) {
            ttsChildren += ttsSettingsSection()
        })

        // ═══════════════════════════════════════════════════════
        // 6.6 輸入法選擇
        // ═══════════════════════════════════════════════════════
        root.addView(buildInputMethodCard())

        // ═══════════════════════════════════════════════════════
        // 6.7 Snippet 管理
        // ═══════════════════════════════════════════════════════
        root.addView(buildSnippetCard())

        // ═══════════════════════════════════════════════════════
        // 7. 智慧輸入
        // ═══════════════════════════════════════════════════════
        val smartChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("智慧輸入"), null, null, smartChildren) {
            smartChildren += switchRow(tr("智慧糾錯"), tr("AI 偵測打錯字並自動修正"), prefs.smartCorrection) { prefs.smartCorrection = it }
            smartChildren += switchRow(tr("自動學詞"), tr("常用字彙自動加入字典"), prefs.adaptiveDict) { prefs.adaptiveDict = it }
            smartChildren += switchRow(tr("語句預測"), tr("預測下一個可能的詞或片語"), prefs.phrasePredict) { prefs.phrasePredict = it }
            smartChildren += switchRow(tr("習慣追蹤"), tr("學習打字習慣提升預測精準度"), prefs.habitTracking) { prefs.habitTracking = it }

            // 候選字來源顏色說明
            smartChildren += buildCandidateColorLegend()
        })

        // ═══════════════════════════════════════════════════════
        // Insight 整合（登入後才顯示完整選項）
        // ═══════════════════════════════════════════════════════
        val insightAuth = com.snowrealm.yukiboard.auth.SnowRealmAuthClient.getInstance(this)
        val insightChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("Insight 整合"), null, null, insightChildren) {
            // Plan badge
            if (insightAuth.isLoggedIn) {
                val user = insightAuth.currentUser
                val planText = when (user?.plan) {
                    com.snowrealm.yukiboard.auth.SnowRealmAuthClient.UserPlan.PLUS -> "Plus ⭐"
                    com.snowrealm.yukiboard.auth.SnowRealmAuthClient.UserPlan.PRO -> "Pro 🔥"
                    com.snowrealm.yukiboard.auth.SnowRealmAuthClient.UserPlan.ENTERPRISE -> "Enterprise 🏢"
                    else -> "Free"
                }
                insightChildren += TextView(this).apply {
                    text = tr("訂閱等級：$planText")
                    textSize = 14f
                    setTextColor(0xFF40E0D0.toInt())
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                }
            }
            insightChildren += switchRow(tr("情境問卷"), tr("打字時觸發問卷，回答得 Z 幣"), prefs.insightSurveyEnabled) { prefs.insightSurveyEnabled = it }
            insightChildren += switchRow(tr("主題雲端同步"), tr("自動同步在 Insight 購買的主題"), prefs.insightThemeSyncEnabled) { prefs.insightThemeSyncEnabled = it }
            insightChildren += switchRow(tr("設定備份"), tr("登入後自動備份鍵盤設定"), prefs.insightSettingsBackup) { prefs.insightSettingsBackup = it }
        })

        // ═══════════════════════════════════════════════════════
        // 主題 / 外觀 / 語言 / 背景（獨立卡片）
        // ═══════════════════════════════════════════════════════
        root.addView(buildThemeCard())
        root.addView(buildBackgroundCard())
        root.addView(buildAppearanceCard())
        root.addView(buildLanguageCard())
        root.addView(buildLanguagePackCard())

        // ═══════════════════════════════════════════════════════
        // 法律與隱私
        // ═══════════════════════════════════════════════════════
        val legalChildren = mutableListOf<View>()
        root.addView(cardGroup(tr("法律與隱私"), null, null, legalChildren) {
            legalChildren += Button(this).apply {
                text = tr("隱私權政策")
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                background = null
                setOnClickListener { showLegalWebView("privacy_policy.html", "隱私權政策") }
            }
            legalChildren += Button(this).apply {
                text = tr("服務條款")
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                background = null
                setOnClickListener { showLegalWebView("terms_of_service.html", "服務條款") }
            }
            legalChildren += Button(this).apply {
                text = tr("開源授權")
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                background = null
                setOnClickListener { showLegalWebView("open_source.html", "開源授權") }
            }
        })

        // ═══════════════════════════════════════════════════════
        // 輸入法測試
        // ═══════════════════════════════════════════════════════
        root.addView(Button(this).apply {
            text = tr("輸入法完整測試")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            background = GradientDrawable().apply {
                setColor(0xFF2E7D32.toInt())
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(4), dp(16), dp(4), dp(8)) }
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, InputTestActivity::class.java))
            }
        })

        scrollView.addView(root)
        setContentView(scrollView)
    }

    // ─────────────────────────────────────────────────────────
    // CardGroup 建構器
    // ─────────────────────────────────────────────────────────

    /**
     * 建立一個群組卡片。
     * @param title 群組標題
     * @param masterValue 主開關初始值（null = 無主開關）
     * @param masterSetter 主開關 setter（null = 無主開關）
     * @param childViews 子項目列表，由 builder 填充
     * @param builder 在 builder 裡把子 View 加入 childViews
     */
    private fun cardGroup(
        title: String,
        masterValue: Boolean?,
        masterSetter: ((Boolean) -> Unit)?,
        childViews: MutableList<View>,
        builder: () -> Unit
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1E2D4A.toInt())
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(12)) }
        }

        // 群組標題列
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), 0, dp(4), dp(8))
        }

        titleRow.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(0xFF7FD8FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // 主開關
        if (masterValue != null && masterSetter != null) {
            @Suppress("UseSwitchCompatOrMaterialCode")
            titleRow.addView(Switch(this).apply {
                isChecked = masterValue
                setOnCheckedChangeListener { _, checked ->
                    masterSetter(checked)
                    childViews.forEach {
                        it.isEnabled = checked
                        it.alpha = if (checked) 1.0f else 0.4f
                    }
                }
            })
        }

        card.addView(titleRow)

        // 分隔線
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { setMargins(0, 0, 0, dp(4)) }
            setBackgroundColor(0xFF2A3555.toInt())
        })

        // 建構子項目
        builder()

        // 加入子項目
        childViews.forEach { child ->
            card.addView(child)
            if (masterValue != null) {
                child.isEnabled = masterValue
                child.alpha = if (masterValue) 1.0f else 0.4f
            }
        }

        return card
    }

    // ─────────────────────────────────────────────────────────
    // 主題卡片
    // ─────────────────────────────────────────────────────────

    private fun buildThemeCard(): LinearLayout {
        val card = simpleCard("主題 / Theme")

        val themeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(8), dp(4), 0, dp(8))
        }

        val themes = themeManager.availableThemes
        themes.forEach { theme ->
            themeGroup.addView(RadioButton(this).apply {
                id = theme.id.hashCode()
                text = theme.displayName
                setTextColor(0xFFB0D4FF.toInt())
                isChecked = theme.id == themeManager.currentTheme.id
            })
        }
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            themes.firstOrNull { it.id.hashCode() == checkedId }?.let {
                themeManager.currentTheme = it
            }
        }
        card.addView(themeGroup)
        return card
    }

    // ─────────────────────────────────────────────────────────
    // 背景卡片
    // ─────────────────────────────────────────────────────────

    // 預覽用的 ImageView / TextureView（需在 onActivityResult 更新）
    private var bgPreviewImage: android.widget.ImageView? = null
    private var bgPreviewOverlay: View? = null
    private var bgPreviewLabel: TextView? = null
    private var bgPreviewContainer: FrameLayout? = null
    /** 背景預覽 Canvas View（與鍵盤預覽共用 fullPreviewBgBitmap + 同一套 Matrix 邏輯） */
    private var bgPreviewView: View? = null
    private var bgVideoPlayer: android.media.MediaPlayer? = null
    private var bgVideoTexture: android.view.TextureView? = null
    // 背景同步用的 UI 元件引用
    private var bgOverlaySeekBar: SeekBar? = null
    private var bgOverlayLabel: TextView? = null
    private var bgBtnCrop: android.widget.Button? = null
    private var bgBtnFit: android.widget.Button? = null
    private var bgRotBtns = mutableListOf<android.widget.Button>()
    private var bgOffsetXSeekBar: SeekBar? = null
    private var bgOffsetXLabel: TextView? = null
    private var bgOffsetYSeekBar: SeekBar? = null
    private var bgOffsetYLabel: TextView? = null
    private var bgSyncReceiver: android.content.BroadcastReceiver? = null
    private var bgVideoWidth = 0
    private var bgVideoHeight = 0
    private var fullPreviewView: View? = null
    private var fullPreviewBgBitmap: android.graphics.Bitmap? = null

    private fun buildBackgroundCard(): LinearLayout {
        val card = simpleCard("鍵盤背景 / Background")

        // ── 預覽區 ──
        val previewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * prefs.keyboardHeightRatio).toInt().coerceIn(dp(100), dp(300))
            ).apply { setMargins(0, 0, 0, dp(8)) }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF111827.toInt())
                cornerRadius = dp(8).toFloat()
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(8).toFloat())
                }
            }
        }
        bgPreviewContainer = previewContainer

        // Canvas 背景預覽 — 邏輯與 buildFullKeyboardPreview onDraw 的背景部分完全相同（不畫按鍵）
        val canvasPreview = object : View(this@SettingsActivity) {
            private val bgMatrix = android.graphics.Matrix()
            private val overlayPaint = android.graphics.Paint()

            init {
                // 必須設定 setWillNotDraw(false)，否則沒有 background 的 View 不會觸發 onDraw
                setWillNotDraw(false)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0 || h <= 0) return

                val bmp = fullPreviewBgBitmap ?: return
                // ─── 與 buildFullKeyboardPreview 完全相同的 Matrix ───
                bgMatrix.reset()
                if (prefs.bgScaleMode == 1) {
                    bgMatrix.setScale(w / bmp.width, h / bmp.height)
                } else {
                    val scale = maxOf(w / bmp.width, h / bmp.height)
                    val sw = bmp.width * scale
                    val sh = bmp.height * scale
                    val maxDx = (sw - w).coerceAtLeast(0f)
                    val maxDy = (sh - h).coerceAtLeast(0f)
                    bgMatrix.setScale(scale, scale)
                    bgMatrix.postTranslate(-maxDx * prefs.bgOffsetX, -maxDy * prefs.bgOffsetY)
                }
                if (prefs.bgRotation != 0) {
                    bgMatrix.postRotate(prefs.bgRotation.toFloat(), w / 2f, h / 2f)
                }
                canvas.drawBitmap(bmp, bgMatrix, null)

                // 遮罩
                overlayPaint.color = android.graphics.Color.argb(prefs.bgOverlayAlpha, 0, 0, 0)
                overlayPaint.style = android.graphics.Paint.Style.FILL
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bgPreviewView = canvasPreview
        previewContainer.addView(canvasPreview)

        // 保留 ImageView 給影片第一幀備用（影片預覽仍用 TextureView）
        val previewImg = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.MATRIX
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        bgPreviewImage = previewImg
        previewContainer.addView(previewImg)

        // 遮罩覆蓋（用於影片預覽時）
        val overlayView = View(this).apply {
            setBackgroundColor(android.graphics.Color.argb(prefs.bgOverlayAlpha, 0, 0, 0))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bgPreviewOverlay = overlayView
        previewContainer.addView(overlayView)

        // 無圖提示文字
        val label = TextView(this).apply {
            text = tr("尚未設定背景")
            setTextColor(0xFF6688AA.toInt())
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bgPreviewLabel = label
        previewContainer.addView(label)

        // 載入現有背景預覽
        refreshBgPreview()

        card.addView(previewContainer)

        // ── 按鈕列 ──
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }

        row.addView(styledButton("選圖片", 0xFF2A3A5C.toInt(), 0xFFE8F4FF.toInt()) {
            requestMediaPermissionThen("image") {
                val intent = Intent(Intent.ACTION_PICK).apply {
                    data = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_PICK_BG_IMAGE)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) }
        })

        row.addView(styledButton("選影片", 0xFF2A3A5C.toInt(), 0xFFE8F4FF.toInt()) {
            requestMediaPermissionThen("video") {
                val intent = Intent(Intent.ACTION_PICK).apply {
                    data = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_PICK_BG_VIDEO)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginStart = dp(4); marginEnd = dp(4)
            }
        })

        row.addView(styledButton("清除", 0xFF1E2D4A.toInt(), 0xFFFF6B6B.toInt()) {
            stopVideoPreview()
            val dir = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getBackgroundDir(this)
            dir.listFiles()?.forEach { it.delete() }
            prefs.useCustomBackground = false
            bgPreviewImage?.setImageBitmap(null)
            bgPreviewView?.visibility = View.GONE
            bgPreviewLabel?.text = tr("尚未設定背景")
            bgPreviewLabel?.visibility = View.VISIBLE
            fullPreviewBgBitmap = null
            fullPreviewView?.invalidate()
            bgPreviewView?.invalidate()
            sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
            Toast.makeText(this, "已清除背景", Toast.LENGTH_SHORT).show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 0.7f).apply { marginStart = dp(4) }
        })

        card.addView(row)

        // ── 遮罩透明度滑桿 ──
        val alphaLabel = TextView(this).apply {
            text = tr("遮罩深度: %d%%").format((prefs.bgOverlayAlpha * 100) / 255)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(8), 0, dp(4))
        }
        bgOverlayLabel = alphaLabel
        card.addView(alphaLabel)

        card.addView(SeekBar(this).apply {
            max = 255
            progress = prefs.bgOverlayAlpha
            setPadding(dp(12), dp(4), dp(12), dp(8))
            bgOverlaySeekBar = this
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.bgOverlayAlpha = p
                    alphaLabel.text = tr("遮罩深度: %d%%").format((p * 100) / 255)
                    bgPreviewOverlay?.setBackgroundColor(android.graphics.Color.argb(p, 0, 0, 0))
                    fullPreviewView?.invalidate()
                    bgPreviewView?.invalidate()
                    sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 顯示比例切換 ──
        val scaleModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        val scaleModeLabel = TextView(this).apply {
            text = tr("顯示比例:")
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        scaleModeRow.addView(scaleModeLabel)

        val btnCrop = android.widget.Button(this).apply {
            text = tr("保持比例")
            textSize = 11f
            setTextColor(if (prefs.bgScaleMode == 0) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            setBackgroundColor(if (prefs.bgScaleMode == 0) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(8) }
        }
        val btnFit = android.widget.Button(this).apply {
            text = tr("整張塞入")
            textSize = 11f
            setTextColor(if (prefs.bgScaleMode == 1) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            setBackgroundColor(if (prefs.bgScaleMode == 1) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(4) }
        }
        bgBtnCrop = btnCrop
        bgBtnFit = btnFit
        btnCrop.setOnClickListener {
            prefs.bgScaleMode = 0
            btnCrop.setTextColor(0xFF111827.toInt()); btnCrop.setBackgroundColor(0xFF7FD8FF.toInt())
            btnFit.setTextColor(0xFFE8F4FF.toInt()); btnFit.setBackgroundColor(0xFF2A3A5C.toInt())
            updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
            sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
        }
        btnFit.setOnClickListener {
            prefs.bgScaleMode = 1
            btnFit.setTextColor(0xFF111827.toInt()); btnFit.setBackgroundColor(0xFF7FD8FF.toInt())
            btnCrop.setTextColor(0xFFE8F4FF.toInt()); btnCrop.setBackgroundColor(0xFF2A3A5C.toInt())
            updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
            sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
        }
        scaleModeRow.addView(btnCrop)
        scaleModeRow.addView(btnFit)
        card.addView(scaleModeRow)

        // ── 影片旋轉 ──
        bgRotBtns.clear()
        val rotationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        rotationRow.addView(TextView(this).apply {
            text = tr("影片旋轉:")
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        })
        listOf(0 to "0°", 90 to "90°", 180 to "180°", 270 to "270°").forEach { (deg, label) ->
            val btn = android.widget.Button(this).apply {
                text = label
                textSize = 11f
                setPadding(dp(6), 0, dp(6), 0)
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(4) }
                setOnClickListener {
                    prefs.bgRotation = deg
                    bgRotBtns.forEachIndexed { i, b ->
                        val d = listOf(0, 90, 180, 270)[i]
                        b.setTextColor(if (prefs.bgRotation == d) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
                        b.setBackgroundColor(if (prefs.bgRotation == d) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
                    }
                    updateVideoPreviewTransform()
                    fullPreviewView?.invalidate()
                    sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
                }
            }
            val selected = prefs.bgRotation == deg
            btn.setTextColor(if (selected) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            btn.setBackgroundColor(if (selected) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
            bgRotBtns.add(btn)
            rotationRow.addView(btn)
        }
        card.addView(rotationRow)

        // ── 背景位置 X 拉桿 ──
        val xLabel = TextView(this).apply {
            text = tr("水平位置 X: %d%%").format((prefs.bgOffsetX * 100).toInt())
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(8), 0, dp(4))
        }
        bgOffsetXLabel = xLabel
        card.addView(xLabel)
        card.addView(SeekBar(this).apply {
            max = 100
            progress = (prefs.bgOffsetX * 100).toInt()
            setPadding(dp(12), dp(4), dp(12), dp(4))
            bgOffsetXSeekBar = this
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.bgOffsetX = p / 100f
                    xLabel.text = tr("水平位置 X: %d%%").format(p)
                    updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
                }
            })
        })

        // ── 背景位置 Y 拉桿 ──
        val yLabel = TextView(this).apply {
            text = tr("垂直位置 Y: %d%%").format((prefs.bgOffsetY * 100).toInt())
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(4), 0, dp(4))
        }
        bgOffsetYLabel = yLabel
        card.addView(yLabel)
        card.addView(SeekBar(this).apply {
            max = 100
            progress = (prefs.bgOffsetY * 100).toInt()
            setPadding(dp(12), dp(4), dp(12), dp(8))
            bgOffsetYSeekBar = this
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.bgOffsetY = p / 100f
                    yLabel.text = tr("垂直位置 Y: %d%%").format(p)
                    updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
                }
            })
        })

        // ── 鍵盤高度拉桿 ──
        val heightPct = ((prefs.keyboardHeightRatio - 0.25f) / 0.30f * 100).toInt().coerceIn(0, 100)
        val kbHeightLabel = TextView(this).apply {
            text = tr("鍵盤高度: %d%%").format(heightPct)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(8), 0, dp(4))
        }
        card.addView(kbHeightLabel)
        card.addView(SeekBar(this).apply {
            max = 100   // 0=25%, 100=55%
            progress = heightPct
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.keyboardHeightRatio = 0.25f + p / 100f * 0.30f
                    kbHeightLabel.text = tr("鍵盤高度: %d%%").format(p)
                    updateFullPreviewHeight()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 按鍵大小拉桿 ──
        val keySizeLabel = TextView(this).apply {
            text = tr("按鍵大小: %d%%").format(prefs.keyScalePercent)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(8), 0, dp(4))
        }
        card.addView(keySizeLabel)
        card.addView(SeekBar(this).apply {
            max = 60   // 70~130 → offset 0~60
            progress = prefs.keyScalePercent - 70
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.keyScalePercent = p + 70
                    keySizeLabel.text = tr("按鍵大小: %d%%").format(p + 70)
                    fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 按鍵透明度拉桿 ──
        val keyOpLabel = TextView(this).apply {
            text = tr("按鍵不透明度: %d%%").format((prefs.keyOpacity * 100) / 255)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(4), 0, dp(4))
        }
        card.addView(keyOpLabel)
        card.addView(SeekBar(this).apply {
            max = 255
            progress = prefs.keyOpacity
            setPadding(dp(12), dp(4), dp(12), dp(8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.keyOpacity = p
                    keyOpLabel.text = tr("按鍵不透明度: %d%%").format((p * 100) / 255)
                    fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 文字透明度拉桿 ──
        val keyTextOpLabel = TextView(this).apply {
            text = tr("文字不透明度: %d%%").format((prefs.keyTextOpacity * 100) / 255)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(4), 0, dp(4))
        }
        card.addView(keyTextOpLabel)
        card.addView(SeekBar(this).apply {
            max = 255
            progress = prefs.keyTextOpacity
            setPadding(dp(12), dp(4), dp(12), dp(8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.keyTextOpacity = p
                    keyTextOpLabel.text = tr("文字不透明度: %d%%").format((p * 100) / 255)
                    fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── ��盤完整預覽（背景 + 遮罩 + 按鍵） ──
        val previewTitle = TextView(this).apply {
            text = tr("鍵盤預覽")
            textSize = 13f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(12), 0, dp(4))
        }
        card.addView(previewTitle)

        val kbPreview = buildFullKeyboardPreview()
        fullPreviewView = kbPreview
        card.addView(kbPreview)

        // ── 透明鍵盤 ──
        card.addView(switchRow(tr("透明鍵盤"), tr("背景完全透視到底層 App"), prefs.transparentKeyboard) {
            prefs.transparentKeyboard = it
        })

        // 透明度滑桿
        val tAlphaLabel = TextView(this).apply {
            text = tr("透明度: %d%%").format((prefs.transparentKeyboardAlpha * 100) / 255)
            textSize = 11f
            setTextColor(0xFF8899AA.toInt())
            setPadding(dp(12), dp(4), 0, 0)
        }
        card.addView(tAlphaLabel)
        card.addView(SeekBar(this).apply {
            max = 255
            progress = prefs.transparentKeyboardAlpha
            setPadding(dp(12), dp(4), dp(12), dp(8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.transparentKeyboardAlpha = p
                    tAlphaLabel.text = tr("透明度: %d%%").format((p * 100) / 255)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 字體顏色 ──
        card.addView(buildKeyLabelColorPicker())

        return card
    }

    private fun startVideoPreview(filePath: String) {
        val container = bgPreviewContainer ?: return
        val tv = android.view.TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bgVideoTexture = tv

        tv.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                // 全部在 Main thread，不切 IO — setDataSource 對本地檔案是同步且快速的
                try {
                    val mp = android.media.MediaPlayer().apply {
                        setDataSource(filePath)
                        setSurface(android.view.Surface(st))
                        isLooping = true
                        setVolume(0f, 0f)
                        setOnErrorListener { _, what, extra ->
                            android.util.Log.e("BgPreview", "MediaPlayer error: what=$what extra=$extra")
                            bgPreviewLabel?.text = tr("影片播放失敗")
                            bgPreviewLabel?.visibility = View.VISIBLE
                            true
                        }
                        setOnPreparedListener { player ->
                            bgVideoWidth = player.videoWidth
                            bgVideoHeight = player.videoHeight
                            updateVideoPreviewTransform()
                            player.start()
                        }
                        prepareAsync()
                    }
                    bgVideoPlayer = mp
                } catch (e: Exception) {
                    bgPreviewLabel?.text = tr("影片載入失敗")
                    bgPreviewLabel?.visibility = View.VISIBLE
                }
            }
            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                updateVideoPreviewTransform()
            }
            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                val mp = bgVideoPlayer
                bgVideoPlayer = null
                try { mp?.stop() } catch (_: Exception) { }
                try { mp?.release() } catch (_: Exception) { }
                return true
            }
            override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
        }

        // 插入到 ImageView 後面，overlay 前面（index 1）
        val overlayIdx = container.indexOfChild(bgPreviewOverlay)
        container.addView(tv, if (overlayIdx > 0) overlayIdx else 1)
    }

    /** 影片無法播放時，提取第一幀作為靜態預覽 */
    private fun showVideoFirstFrame(filePath: String) {
        activityScope.launch(Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                val frame = retriever.getFrameAtTime(0)
                retriever.release()
                withContext(Dispatchers.Main) {
                    if (frame != null) {
                        fullPreviewBgBitmap = frame
                        bgPreviewView?.visibility = View.VISIBLE
                        bgPreviewView?.invalidate()
                        fullPreviewView?.invalidate()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun stopVideoPreview() {
        val mp = bgVideoPlayer ?: run {
            // 沒有 player，只清 texture
            bgVideoTexture?.let { bgPreviewContainer?.removeView(it) }
            bgVideoTexture = null
            return
        }
        bgVideoPlayer = null
        try { mp.stop() } catch (_: Exception) { }
        try { mp.release() } catch (_: Exception) { }
        bgVideoTexture?.let { bgPreviewContainer?.removeView(it) }
        bgVideoTexture = null
    }

    /** 背景預覽現在是 Canvas View，Matrix 邏輯在 onDraw 內。
     *  只需 invalidate 觸發重繪即可。 */
    private fun updateBgPreviewMatrix() {
        bgPreviewView?.invalidate()
    }

    /** 根據 prefs 的 bgScaleMode + bgOffsetX/Y 更新影片預覽 TextureView 的 transform */
    private fun updateVideoPreviewTransform() {
        val tv = bgVideoTexture ?: return
        val vw = tv.width.toFloat()
        val vh = tv.height.toFloat()
        if (vw <= 0 || vh <= 0 || bgVideoWidth <= 0 || bgVideoHeight <= 0) return

        val vW = bgVideoWidth.toFloat()
        val vH = bgVideoHeight.toFloat()
        val cx = vw / 2f
        val cy = vh / 2f
        val rotation = prefs.bgRotation
        val isRotated = rotation == 90 || rotation == 270
        val effectiveW = if (isRotated) vH else vW
        val effectiveH = if (isRotated) vW else vH

        val matrix = android.graphics.Matrix()

        if (prefs.bgScaleMode == 1) {
            // 整張塞入
            matrix.setTranslate(cx - vW / 2f, cy - vH / 2f)
            if (rotation != 0) matrix.postRotate(rotation.toFloat(), cx, cy)
            matrix.postScale(vw / effectiveW, vh / effectiveH, cx, cy)
        } else {
            // center-crop：填滿預��
            val scale = maxOf(vw / effectiveW, vh / effectiveH)
            val scaledW = effectiveW * scale
            val scaledH = effectiveH * scale
            val maxDx = (scaledW - vw).coerceAtLeast(0f)
            val maxDy = (scaledH - vh).coerceAtLeast(0f)

            matrix.setTranslate(cx - vW / 2f, cy - vH / 2f)
            if (rotation != 0) matrix.postRotate(rotation.toFloat(), cx, cy)
            matrix.postScale(scale, scale, cx, cy)
            matrix.postTranslate(-maxDx * prefs.bgOffsetX, -maxDy * prefs.bgOffsetY)
        }

        tv.setTransform(matrix)
        tv.invalidate()
    }

    private fun refreshBgPreview() {
        val savedBg = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getSavedBackground(this)
        if (savedBg != null && !com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.isVideoFile(savedBg)) {
            // 圖片預覽 — Canvas View 自帶遮罩，不需要外部 overlayView
            bgPreviewView?.visibility = View.VISIBLE
            bgPreviewOverlay?.visibility = View.GONE
            bgPreviewLabel?.visibility = View.GONE
        } else if (savedBg != null) {
            // 影片預覽：TextureView 播放，需要外部 overlayView
            stopVideoPreview()
            bgPreviewView?.visibility = View.GONE
            bgPreviewOverlay?.visibility = View.VISIBLE
            bgPreviewLabel?.visibility = View.GONE
            startVideoPreview(savedBg.absolutePath)
        } else {
            bgPreviewView?.visibility = View.GONE
            bgPreviewOverlay?.visibility = View.GONE
            bgPreviewLabel?.text = tr("尚未設定背景")
            bgPreviewLabel?.visibility = View.VISIBLE
        }
        // 同步更新鍵盤完整預覽的背景（也會被 Canvas 背景預覽共用）
        reloadFullPreviewBitmap()
        fullPreviewView?.invalidate()
        bgPreviewView?.invalidate()
    }

    // ─────────────────────────────────────────────────────────
    // 鍵盤完整預覽（背景 + 遮罩 + 按鍵）
    // ─────────────────────────────────────────────────────────

    /** 根據 keyboardHeightRatio 更新完整預覽 + 背景預覽的高度 */
    private fun updateFullPreviewHeight() {
        val screenH = resources.displayMetrics.heightPixels
        val newH = (screenH * prefs.keyboardHeightRatio).toInt().coerceIn(dp(100), dp(300))
        fullPreviewView?.let { v ->
            v.layoutParams = v.layoutParams?.apply { height = newH }
            v.requestLayout(); v.invalidate()
        }
        bgPreviewContainer?.let { c ->
            c.layoutParams = c.layoutParams?.apply { height = newH }
            c.requestLayout()
            updateBgPreviewMatrix()
        }
    }

    /** 重新載入完整預覽的背景 bitmap（圖片或影片第一幀） */
    private fun reloadFullPreviewBitmap() {
        val savedBg = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getSavedBackground(this)

        if (savedBg == null) {
            fullPreviewBgBitmap = null
            fullPreviewView?.invalidate()
            return
        }

        activityScope.launch(Dispatchers.IO) {
            val bmp = try {
                if (com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.isVideoFile(savedBg)) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(savedBg.absolutePath)
                        retriever.getFrameAtTime(
                            0,
                            android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                    } finally {
                        retriever.release()
                    }
                } else {
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeFile(savedBg.absolutePath, opts)
                }
            } catch (_: Exception) { null }

            withContext(Dispatchers.Main) {
                fullPreviewBgBitmap = bmp
                fullPreviewView?.invalidate()
                bgPreviewView?.invalidate()
            }
        }
    }

    private fun buildFullKeyboardPreview(): View {
        val theme = themeManager.currentTheme
        reloadFullPreviewBitmap()

        val screenH = resources.displayMetrics.heightPixels
        val previewHeight = (screenH * prefs.keyboardHeightRatio).toInt().coerceIn(dp(100), dp(300))
        val dens = resources.displayMetrics.density

        return object : View(this@SettingsActivity) {
            private val keyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = android.graphics.Paint.Align.CENTER
            }
            private val bgMatrix = android.graphics.Matrix()

            init {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, previewHeight
                ).apply { setMargins(dp(12), dp(4), dp(12), dp(12)) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(theme.backgroundColorInt)
                    cornerRadius = dp(8).toFloat()
                }
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(8).toFloat())
                    }
                }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0 || h <= 0) return

                // 1. 背景圖片
                val bmp = fullPreviewBgBitmap
                if (bmp != null && prefs.useCustomBackground) {
                    bgMatrix.reset()
                    if (prefs.bgScaleMode == 1) {
                        bgMatrix.setScale(w / bmp.width, h / bmp.height)
                    } else {
                        val scale = maxOf(w / bmp.width, h / bmp.height)
                        val sw = bmp.width * scale
                        val sh = bmp.height * scale
                        val maxDx = (sw - w).coerceAtLeast(0f)
                        val maxDy = (sh - h).coerceAtLeast(0f)
                        bgMatrix.setScale(scale, scale)
                        bgMatrix.postTranslate(-maxDx * prefs.bgOffsetX, -maxDy * prefs.bgOffsetY)
                    }
                    // 旋轉（以 View 中心）
                    if (prefs.bgRotation != 0) {
                        bgMatrix.postRotate(prefs.bgRotation.toFloat(), w / 2f, h / 2f)
                    }
                    canvas.drawBitmap(bmp, bgMatrix, null)

                    // 2. 遮罩
                    keyPaint.color = android.graphics.Color.argb(prefs.bgOverlayAlpha, 0, 0, 0)
                    keyPaint.style = android.graphics.Paint.Style.FILL
                    canvas.drawRect(0f, 0f, w, h, keyPaint)
                }

                // 3. 繪製迷你鍵盤按鍵
                val rows = com.snowrealm.yukiboard.ui.keyboard.KeyboardState.defaultQwertyRows()
                val hasBg = bmp != null && prefs.useCustomBackground
                val scalePct = prefs.keyScalePercent
                val keyAlpha = prefs.keyOpacity
                val rowH = h / rows.size
                val yOffset = 0f
                val scaleInv = (200 - scalePct) / 100f  // 130→0.7, 100→1.0, 70→1.3
                val padH = 2f * dens * scaleInv
                val padV = 2f * dens * scaleInv
                val cr = theme.keyCornerRadiusDp * dens * 0.6f
                val fontSize = 10f * dens * scalePct / 100f
                textPaint.textSize = fontSize
                val baseLabelColor = if (prefs.customKeyLabelColor != 0) prefs.customKeyLabelColor
                    else theme.keyLabelColorInt
                val textAlpha = prefs.keyTextOpacity
                textPaint.color = if (textAlpha < 255) {
                    val a = ((baseLabelColor ushr 24) * textAlpha / 255).coerceIn(0, 255)
                    (a shl 24) or (baseLabelColor and 0x00FFFFFF)
                } else baseLabelColor

                val totalUnits = rows.maxOf { row -> row.sumOf { it.widthWeight.toDouble() }.toFloat() }

                rows.forEachIndexed { rowIdx, row ->
                    val top = yOffset + rowIdx * rowH
                    var x = 0f
                    val unitW = w / totalUnits

                    row.forEach { key ->
                        val keyW = unitW * key.widthWeight
                        val rect = android.graphics.RectF(
                            x + padH, top + padV,
                            x + keyW - padH, top + rowH - padV
                        )

                        if (hasBg) {
                            // 玻璃模式：透明底 + 細框線
                            keyPaint.style = android.graphics.Paint.Style.STROKE
                            keyPaint.strokeWidth = 0.5f * dens
                            keyPaint.color = android.graphics.Color.argb((keyAlpha * 0.3f).toInt().coerceIn(0, 255), 255, 255, 255)
                            canvas.drawRoundRect(rect, cr, cr, keyPaint)
                        } else {
                            // 標準模式
                            val baseColor = if (key.isSpecial) theme.specialKeyColorInt else theme.keyColorInt
                            val a = ((baseColor ushr 24) * keyAlpha / 255).coerceIn(0, 255)
                            keyPaint.color = (a shl 24) or (baseColor and 0x00FFFFFF)
                            keyPaint.style = android.graphics.Paint.Style.FILL
                            canvas.drawRoundRect(rect, cr, cr, keyPaint)
                        }

                        // 標籤
                        val label = key.label
                        if (label.isNotEmpty() && label.length <= 3) {
                            val textY = rect.top + (rect.height() + fontSize) / 2f - textPaint.descent()
                            canvas.drawText(label, rect.centerX(), textY, textPaint)
                        }
                        x += keyW
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 字體顏色選擇器
    // ─────────────────────────────────────────────────────────

    private fun buildKeyLabelColorPicker(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }

        container.addView(TextView(this).apply {
            text = tr("按鍵字體顏色")
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(4), 0, 0, dp(8))
        })

        val colors = listOf(
            0 to "跟隨主題",
            0xFFFFFFFF.toInt() to "白",
            0xFF000000.toInt() to "黑",
            0xFF7FD8FF.toInt() to "冰藍",
            0xFFFF6B9D.toInt() to "粉",
            0xFFFFD700.toInt() to "金",
            0xFF5EEAD4.toInt() to "薄荷",
            0xFFA78BFA.toInt() to "紫",
            0xFFFF6B6B.toInt() to "紅",
            0xFF4ADE80.toInt() to "綠",
            0xFFFF9500.toInt() to "橘"
        )

        val currentColor = prefs.customKeyLabelColor

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }

        // 用 ScrollView 包住讓色盤可滾動
        val scrollH = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        // 記住所有色塊 View 方便更新選中狀態
        val colorViews = mutableListOf<View>()

        colors.forEach { (color, label) ->
            val isSelected = color == currentColor
            val circleSize = dp(36)
            val margin = dp(4)

            val wrapper = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(circleSize + margin * 2, circleSize + dp(18)).apply {
                    setMargins(margin, 0, margin, 0)
                }
            }

            val circle = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(circleSize, circleSize)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    if (color == 0) {
                        // 跟隨主題：彩虹漸層圈
                        setColors(intArrayOf(
                            0xFFFF6B6B.toInt(), 0xFFFFD700.toInt(), 0xFF4ADE80.toInt(),
                            0xFF7FD8FF.toInt(), 0xFFA78BFA.toInt(), 0xFFFF6B9D.toInt()
                        ))
                        gradientType = android.graphics.drawable.GradientDrawable.SWEEP_GRADIENT
                    } else {
                        setColor(color)
                    }
                    if (color == 0xFFFFFFFF.toInt() || color == 0) {
                        setStroke(dp(1), 0xFF555555.toInt())
                    }
                    if (isSelected) {
                        setStroke(dp(2), 0xFF4A90E2.toInt())
                    }
                }
            }
            wrapper.addView(circle)
            colorViews.add(circle)

            // 色名
            wrapper.addView(TextView(this).apply {
                text = label
                textSize = 9f
                setTextColor(0xFF8899AA.toInt())
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = circleSize + dp(2) }
            })

            wrapper.setOnClickListener {
                prefs.customKeyLabelColor = color
                // 更新所有選中狀態
                colorViews.forEachIndexed { i, v ->
                    val c = colors[i].first
                    val sel = c == color
                    v.background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        if (c == 0) {
                            setColors(intArrayOf(
                                0xFFFF6B6B.toInt(), 0xFFFFD700.toInt(), 0xFF4ADE80.toInt(),
                                0xFF7FD8FF.toInt(), 0xFFA78BFA.toInt(), 0xFFFF6B9D.toInt()
                            ))
                            gradientType = android.graphics.drawable.GradientDrawable.SWEEP_GRADIENT
                        } else {
                            setColor(c)
                        }
                        if (c == 0xFFFFFFFF.toInt() || c == 0) {
                            setStroke(dp(1), 0xFF555555.toInt())
                        }
                        if (sel) {
                            setStroke(dp(2), 0xFF4A90E2.toInt())
                        }
                    }
                }
                fullPreviewView?.invalidate()
                Toast.makeText(this, if (color == 0) "字體顏色跟隨主題" else "已更改字體顏色", Toast.LENGTH_SHORT).show()
            }

            colorRow.addView(wrapper)
        }

        scrollH.addView(colorRow)
        container.addView(scrollH)

        // 自訂顏色按鈕
        container.addView(TextView(this).apply {
            text = tr("＋ 自訂顏色...")
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(4))
            setOnClickListener {
                showCustomColorPicker { pickedColor ->
                    prefs.customKeyLabelColor = pickedColor
                    // 更新所有色塊（取消選中）
                    colorViews.forEachIndexed { i, v ->
                        val c = colors[i].first
                        v.background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            if (c == 0) {
                                setColors(intArrayOf(
                                    0xFFFF6B6B.toInt(), 0xFFFFD700.toInt(), 0xFF4ADE80.toInt(),
                                    0xFF7FD8FF.toInt(), 0xFFA78BFA.toInt(), 0xFFFF6B9D.toInt()
                                ))
                                gradientType = android.graphics.drawable.GradientDrawable.SWEEP_GRADIENT
                            } else {
                                setColor(c)
                            }
                            if (c == 0xFFFFFFFF.toInt() || c == 0) {
                                setStroke(dp(1), 0xFF555555.toInt())
                            }
                        }
                    }
                    fullPreviewView?.invalidate()
                    Toast.makeText(this@SettingsActivity, "已設定自訂顏色", Toast.LENGTH_SHORT).show()
                }
            }
        })

        return container
    }

    private fun showCustomColorPicker(onColorPicked: (Int) -> Unit) {
        val currentColor = prefs.customKeyLabelColor.let { if (it == 0) 0xFFFFFFFF.toInt() else it }

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        // 顏色預覽
        val previewView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
                bottomMargin = dp(12)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(currentColor)
            }
        }
        dialogLayout.addView(previewView)

        var r = android.graphics.Color.red(currentColor)
        var g = android.graphics.Color.green(currentColor)
        var b = android.graphics.Color.blue(currentColor)

        fun updatePreview() {
            val c = android.graphics.Color.rgb(r, g, b)
            previewView.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(c or 0xFF000000.toInt())
            }
        }

        // Hex 輸入
        val hexInput = android.widget.EditText(this).apply {
            hint = "#RRGGBB"
            setText(String.format("#%02X%02X%02X", r, g, b))
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(7))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        dialogLayout.addView(hexInput)

        fun makeSlider(label: String, initial: Int, onChanged: (Int) -> Unit): LinearLayout {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(0xFFCCCCCC.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val valueText = TextView(this).apply {
                text = "$initial"
                textSize = 12f
                setTextColor(0xFF7FD8FF.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = android.view.Gravity.END
            }
            val sb = android.widget.SeekBar(this).apply {
                max = 255
                progress = initial
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        valueText.text = "$progress"
                        onChanged(progress)
                        updatePreview()
                        hexInput.setText(String.format("#%02X%02X%02X", r, g, b))
                    }
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                })
            }
            row.addView(sb)
            row.addView(valueText)
            return row
        }

        val rSlider = makeSlider("R", r) { r = it }
        val gSlider = makeSlider("G", g) { g = it }
        val bSlider = makeSlider("B", b) { b = it }
        dialogLayout.addView(rSlider)
        dialogLayout.addView(gSlider)
        dialogLayout.addView(bSlider)

        // Hex → RGB 同步
        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hex = s?.toString()?.removePrefix("#") ?: return
                if (hex.length == 6) {
                    try {
                        val parsed = android.graphics.Color.parseColor("#$hex")
                        r = android.graphics.Color.red(parsed)
                        g = android.graphics.Color.green(parsed)
                        b = android.graphics.Color.blue(parsed)
                        rSlider.findViewById<android.widget.SeekBar>(0)
                        // 直接更新 seekbar（避免遞迴死循環，用 tag 標記）
                        updatePreview()
                    } catch (_: Exception) {}
                }
            }
        })

        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(tr("自訂字體顏色"))
            .setView(dialogLayout)
            .setPositiveButton(tr("確定")) { _, _ ->
                val color = android.graphics.Color.rgb(r, g, b) or 0xFF000000.toInt()
                onColorPicked(color)
            }
            .setNegativeButton(tr("取消"), null)
            .show()
    }

    // ─────────────────────────────────────────────────────────
    // 外觀模式卡片
    // ─────────────────────────────────────────────────────────

    private fun buildAppearanceCard(): LinearLayout {
        val card = simpleCard("外觀模式 / Appearance")

        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(8), dp(4), 0, dp(8))
        }

        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val modes = listOf(
            Triple(1, "跟隨系統 / Follow System", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
            Triple(2, "深色 / Dark", AppCompatDelegate.MODE_NIGHT_YES),
            Triple(3, "淺色 / Light", AppCompatDelegate.MODE_NIGHT_NO)
        )

        modes.forEach { (id, label, mode) ->
            group.addView(RadioButton(this).apply {
                this.id = id
                text = label
                setTextColor(0xFFB0D4FF.toInt())
                isChecked = currentMode == mode
            })
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            modes.firstOrNull { it.first == checkedId }?.let {
                AppCompatDelegate.setDefaultNightMode(it.third)
            }
        }

        card.addView(group)
        return card
    }

    // ─────────────────────────────────────────────────────────
    // 語言卡片
    // ─────────────────────────────────────────────────────────

    private fun buildLanguageCard(): LinearLayout {
        val card = simpleCard("語言 / Language")

        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(8), dp(4), 0, dp(8))
        }

        val currentLang = resources.configuration.locales[0].language
        val langs = listOf(
            Triple(10, "繁體中文", "zh"),
            Triple(11, "English", "en")
        )

        var isInitializing = true
        langs.forEach { (id, label, lang) ->
            group.addView(RadioButton(this).apply {
                this.id = id
                text = label
                setTextColor(0xFFB0D4FF.toInt())
                isChecked = currentLang == lang
            })
        }
        isInitializing = false
        group.setOnCheckedChangeListener { _, checkedId ->
            if (isInitializing) return@setOnCheckedChangeListener
            langs.firstOrNull { it.first == checkedId }?.let { (_, _, lang) ->
                if (!isRestarting) {
                    isRestarting = true
                    val locale = Locale(lang)
                    Locale.setDefault(locale)
                    val config = Configuration(resources.configuration)
                    config.setLocale(locale)
                    @Suppress("DEPRECATION")
                    resources.updateConfiguration(config, resources.displayMetrics)
                    // 用 Intent 重啟取代 recreate()，避免 Android 13+ 多次 configuration change
                    val restart = intent
                    finish()
                    startActivity(restart)
                }
            }
        }

        card.addView(group)
        return card
    }

    // ─────────────────────────────────────────────────────────
    // 語言套件管理卡片
    // ─────────────────────────────────────────────────────────

    private fun buildLanguagePackCard(): LinearLayout {
        val card = simpleCard(tr("語言套件 / Language Packs"))
        val langPackMgr = com.snowrealm.yukiboard.input.langpack.LanguagePackManager.getInstance(this)
        val allPacks = com.snowrealm.yukiboard.input.langpack.LanguagePack.ALL
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), dp(8))
        }

        fun refreshContent() {
            contentLayout.removeAllViews()

            // ── 已啟用的輸入法 ──
            val enabledTitle = TextView(this).apply {
                text = tr("已啟用的輸入法")
                setTextColor(0xFF90CAF9.toInt())
                textSize = 13f
                setPadding(dp(8), dp(8), 0, dp(4))
            }
            contentLayout.addView(enabledTitle)

            val enabledIds = langPackMgr.getEnabledInputMethods().map { it.id }.toSet()
            for (pack in allPacks) {
                if (!langPackMgr.isInstalled(pack.id)) continue

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                }

                val cb = android.widget.CheckBox(this).apply {
                    isChecked = pack.id in enabledIds
                    buttonTintList = android.content.res.ColorStateList.valueOf(0xFF64B5F6.toInt())
                    setOnCheckedChangeListener { _, checked ->
                        langPackMgr.toggleInputMethod(pack.id, checked)
                    }
                }
                row.addView(cb)

                val label = TextView(this).apply {
                    text = "${pack.icon} ${pack.displayName}"
                    setTextColor(0xFFE0E8F0.toInt())
                    textSize = 14f
                    setPadding(dp(8), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(label)

                // 內建語言不顯示刪除按鈕
                if (!langPackMgr.isBuiltin(pack.id)) {
                    val delBtn = TextView(this).apply {
                        text = tr("移除")
                        setTextColor(0xFFEF5350.toInt())
                        textSize = 12f
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                        setOnClickListener {
                            langPackMgr.uninstall(pack.id)
                            langPackMgr.toggleInputMethod(pack.id, false)
                            refreshContent()
                        }
                    }
                    row.addView(delBtn)
                }

                contentLayout.addView(row)
            }

            // ── 可下載的語言套件 ──
            val available = langPackMgr.getAvailablePacks()
            if (available.isNotEmpty()) {
                val downloadTitle = TextView(this).apply {
                    text = tr("可下載的語言套件")
                    setTextColor(0xFF90CAF9.toInt())
                    textSize = 13f
                    setPadding(dp(8), dp(16), 0, dp(4))
                }
                contentLayout.addView(downloadTitle)

                for (pack in available) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(6), dp(8), dp(6))
                    }

                    val info = TextView(this).apply {
                        text = "${pack.icon} ${pack.displayName} (${pack.nativeName})"
                        setTextColor(0xFFB0BEC5.toInt())
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    row.addView(info)

                    val sizeInfo = TextView(this).apply {
                        text = "${pack.dictWordCount / 1000}K 詞"
                        setTextColor(0xFF78909C.toInt())
                        textSize = 11f
                        setPadding(0, 0, dp(8), 0)
                    }
                    row.addView(sizeInfo)

                    val dlBtn = TextView(this).apply {
                        text = tr("下載")
                        setTextColor(0xFF42A5F5.toInt())
                        textSize = 13f
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setStroke(dp(1), 0xFF42A5F5.toInt())
                            cornerRadius = dp(6).toFloat()
                        }
                        setOnClickListener { btn ->
                            btn.isEnabled = false
                            (btn as TextView).text = tr("下載中…")
                            lifecycleScope.launch {
                                val result = langPackMgr.downloadAndInstall(pack.id)
                                if (result.success) {
                                    langPackMgr.toggleInputMethod(pack.id, true)
                                }
                                android.widget.Toast.makeText(
                                    this@SettingsActivity,
                                    result.message,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                refreshContent()
                            }
                        }
                    }
                    row.addView(dlBtn)

                    contentLayout.addView(row)
                }
            }
        }

        refreshContent()
        card.addView(contentLayout)
        return card
    }

    // ─────────────────────────────────────────────────────────
    // UI 工具方法
    // ─────────────────────────────────────────────────────────

    private fun simpleCard(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1E2D4A.toInt())
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(12)) }

            addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 16f
                setTextColor(0xFF7FD8FF.toInt())
                setPadding(dp(4), 0, 0, dp(8))
            })

            addView(View(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { setMargins(0, 0, 0, dp(4)) }
                setBackgroundColor(0xFF2A3555.toInt())
            })
        }
    }

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun switchRow(
        title: String,
        subtitle: String,
        initialValue: Boolean,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))

            val textContainer = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textContainer.addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 14f
                setTextColor(0xFFE8F4FF.toInt())
            })
            textContainer.addView(TextView(this@SettingsActivity).apply {
                text = subtitle
                textSize = 11f
                setTextColor(0xFF6688AA.toInt())
            })
            addView(textContainer)

            addView(Switch(this@SettingsActivity).apply {
                isChecked = initialValue
                setOnCheckedChangeListener { _, checked -> onChanged(checked) }
            })
        }
    }

    private fun styledButton(
        label: String,
        bgColor: Int,
        textColor: Int,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            setTextColor(textColor)
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    /** 語音引擎選擇器：Android 內建 / OpenAI Whisper / Faster-Whisper + 連線測試 */
    private fun speechEngineSelector(): LinearLayout {
        val options = arrayOf("Android 內建", "OpenAI Whisper", "Faster-Whisper（自架・免費）")
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textContainer.addView(TextView(this).apply {
            text = tr("語音辨識引擎")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
        })
        textContainer.addView(TextView(this).apply {
            text = tr("Whisper 需 API Key / Faster-Whisper 自架免費")
            textSize = 11f
            setTextColor(0xFF6688AA.toInt())
        })
        row.addView(textContainer)

        val btnLabel = TextView(this).apply {
            text = options[prefs.speechEngineType.coerceIn(0, options.lastIndex)]
            textSize = 13f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2744.toInt())
                cornerRadius = dp(6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
            setOnClickListener {
                android.app.AlertDialog.Builder(this@SettingsActivity, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(tr("選擇語音辨識引擎"))
                    .setSingleChoiceItems(options, prefs.speechEngineType.coerceIn(0, options.lastIndex)) { dialog, which ->
                        prefs.speechEngineType = which
                        text = options[which]
                        dialog.dismiss()
                    }
                    .show()
            }
        }
        row.addView(btnLabel)
        outer.addView(row)

        // Faster-Whisper Server URL 輸入欄
        outer.addView(TextView(this).apply {
            text = "Faster-Whisper Server URL"
            textSize = 12f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(4), dp(10), dp(4), dp(2))
        })
        val urlBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF0D1628.toInt())
            cornerRadius = dp(6).toFloat()
            setStroke(1, 0xFF3A5580.toInt())
        }
        val urlInput = android.widget.EditText(this)
        urlInput.setText(prefs.fasterWhisperServerUrl)
        urlInput.hint = "https://stt.snowrealm.pet"
        urlInput.textSize = 13f
        urlInput.setTextColor(0xFFE8F4FF.toInt())
        urlInput.setHintTextColor(0xFF3A5580.toInt())
        urlInput.setPadding(dp(10), dp(8), dp(10), dp(8))
        urlInput.background = urlBg
        urlInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        urlInput.setSingleLine()
        urlInput.setOnFocusChangeListener { _: android.view.View, hasFocus: Boolean ->
            if (!hasFocus) prefs.fasterWhisperServerUrl = urlInput.text.toString().trim()
        }
        outer.addView(urlInput)

        // 測試連線按鈕（兩個：Whisper + Faster-Whisper）
        val testStatus = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(4), dp(4), dp(4), 0)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        btnRow.addView(TextView(this).apply {
            text = tr("測試 Whisper 連線")
            textSize = 12f
            setTextColor(0xFF66AAFF.toInt())
            setPadding(dp(4), dp(6), dp(12), dp(4))
            setOnClickListener {
                testStatus.text = tr("測試中...")
                testStatus.setTextColor(0xFFFFCC00.toInt())
                testWhisperConnection(testStatus)
            }
        })
        btnRow.addView(TextView(this).apply {
            text = tr("測試 Faster-Whisper")
            textSize = 12f
            setTextColor(0xFF66AAFF.toInt())
            setPadding(dp(4), dp(6), dp(4), dp(4))
            setOnClickListener { _ ->
                val url = urlInput.text.toString().trim()
                if (url.isBlank()) {
                    testStatus.text = tr("✕ 請先輸入 Server URL")
                    testStatus.setTextColor(0xFFFF6B6B.toInt())
                    return@setOnClickListener
                }
                prefs.fasterWhisperServerUrl = url
                testStatus.text = tr("測試中...")
                testStatus.setTextColor(0xFFFFCC00.toInt())
                testFasterWhisperConnection(url, testStatus)
            }
        })
        outer.addView(btnRow)
        outer.addView(testStatus)

        return outer
    }

    /** 語音辨識語言選擇器 */
    private fun speechLanguageSelector(): LinearLayout {
        val langLabels = arrayOf(
            "自動（依輸入模式）",
            "混合語言（自動偵測）",
            "中文（繁體）",
            "英文",
            "日文",
            "韓文"
        )
        val langValues = arrayOf("auto", "mixed", "zh-TW", "en-US", "ja-JP", "ko-KR")
        val currentIdx = langValues.indexOf(prefs.speechLanguage).coerceAtLeast(0)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = tr("語音辨識語言")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
        })
        textCol.addView(TextView(this).apply {
            text = tr("混合語言 = Whisper 自動偵測（中英日韓混說）")
            textSize = 11f
            setTextColor(0xFF6688AA.toInt())
        })
        row.addView(textCol)

        val btnLabel = TextView(this).apply {
            text = langLabels[currentIdx]
            textSize = 13f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2744.toInt())
                cornerRadius = dp(6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
            setOnClickListener {
                val cur = langValues.indexOf(prefs.speechLanguage).coerceAtLeast(0)
                android.app.AlertDialog.Builder(this@SettingsActivity, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(tr("選擇語音辨識語言"))
                    .setSingleChoiceItems(langLabels, cur) { dialog, which ->
                        prefs.speechLanguage = langValues[which]
                        (it as TextView).text = langLabels[which]
                        dialog.dismiss()
                    }
                    .show()
            }
        }
        row.addView(btnLabel)
        return row
    }

    /** 語音靜音偵測靈敏度選擇器 */
    private fun voiceSilenceSensitivitySelector(): LinearLayout {
        val labels = arrayOf("高靈敏（安靜環境）", "標準（預設）", "低靈敏（嘈雜環境）")
        val currentIdx = prefs.voiceSilenceSensitivity.coerceIn(0, 2)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = tr("靜音偵測靈敏度")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
        })
        textCol.addView(TextView(this).apply {
            text = tr("嘈雜環境選「低」避免被截斷")
            textSize = 11f
            setTextColor(0xFF6688AA.toInt())
        })
        row.addView(textCol)

        val btnLabel = TextView(this).apply {
            text = labels[currentIdx]
            textSize = 13f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2744.toInt())
                cornerRadius = dp(6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
            setOnClickListener {
                val cur = prefs.voiceSilenceSensitivity.coerceIn(0, 2)
                android.app.AlertDialog.Builder(this@SettingsActivity, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(tr("選擇靜音偵測靈敏度"))
                    .setSingleChoiceItems(labels, cur) { dialog, which ->
                        prefs.voiceSilenceSensitivity = which
                        (it as TextView).text = labels[which]
                        dialog.dismiss()
                    }
                    .show()
            }
        }
        row.addView(btnLabel)
        return row
    }

    private fun testFasterWhisperConnection(serverUrl: String, statusView: TextView) {
        activityScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val url = serverUrl.trimEnd('/') + "/health"
                val request = okhttp3.Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (response.isSuccessful) {
                        statusView.text = tr("✓ Faster-Whisper 連線成功！$body")
                        statusView.setTextColor(0xFF66FF66.toInt())
                    } else {
                        statusView.text = "✕ HTTP ${response.code}: ${body.take(100)}"
                        statusView.setTextColor(0xFFFF6B6B.toInt())
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    statusView.text = tr("✕ 連線失敗: ${e.message}")
                    statusView.setTextColor(0xFFFF6B6B.toInt())
                }
            }
        }
    }

    /** 用內嵌 WebView 顯示 assets/legal/ 下的 HTML 法律文件 */
    private fun showLegalWebView(fileName: String, title: String) {
        val webView = android.webkit.WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setBackgroundColor(0xFF0F1923.toInt())
            loadUrl("file:///android_asset/legal/$fileName")
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(webView)
            .setPositiveButton("關閉") { d, _ -> d.dismiss() }
            .show()
            .apply {
                // 設定 Dialog 較大尺寸以便閱讀
                window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.95).toInt(),
                    (resources.displayMetrics.heightPixels * 0.85).toInt()
                )
            }
    }

    private fun testWhisperConnection(statusView: TextView) {
        val providerManager = com.snowrealm.yukiboard.claw.provider.AIProviderManager.getInstance(applicationContext)
        val buildKey = com.snowrealm.yukiboard.BuildConfig.OPENAI_API_KEY
        val apiKey = buildKey.takeIf { it.isNotBlank() }
            ?: providerManager.getApiKey(com.snowrealm.yukiboard.claw.provider.AIProviderType.OPENAI)

        if (apiKey.isNullOrBlank()) {
            statusView.text = tr("✗ 沒有 OpenAI API Key — 請先到 AI 設定填入")
            statusView.setTextColor(0xFFFF6666.toInt())
            return
        }

        // 送一段極短的靜音 WAV 測試 Whisper API 是否可達
        Thread {
            try {
                val silenceWav = generateSilenceWav(0.5) // 0.5 秒靜音
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

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code
                val responseBody = response.body?.string() ?: ""
                response.close()

                runOnUiThread {
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
                runOnUiThread {
                    statusView.text = tr("✗ 連線失敗: ${e.message?.take(80)}")
                    statusView.setTextColor(0xFFFF6666.toInt())
                }
            }
        }.start()
    }

    /** 產生極短靜音 WAV 用於測試 API 連線 */
    private fun generateSilenceWav(durationSec: Double): ByteArray {
        val sampleRate = 16000
        val numSamples = (sampleRate * durationSec).toInt()
        val pcmData = ByteArray(numSamples * 2) // 16-bit = 2 bytes per sample, 全 0 = 靜音

        val out = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(out).apply {
            // RIFF header
            writeBytes("RIFF")
            writeLE32(pcmData.size + 36)
            writeBytes("WAVE")
            writeBytes("fmt ")
            writeLE32(16)
            writeLE16(1)  // PCM
            writeLE16(1)  // mono
            writeLE32(sampleRate)
            writeLE32(sampleRate * 2) // byte rate
            writeLE16(2)  // block align
            writeLE16(16) // bits per sample
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


    /** TTS 語音輸出設定區塊 */
    private fun ttsSettingsSection(): LinearLayout {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        // TTS 引擎選擇
        val engineOptions = arrayOf("Android 內建 (免費/離線)", "Google Cloud TTS (高品質)")
        val engineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val engineText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        engineText.addView(TextView(this).apply {
            text = tr("TTS 引擎")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
        })
        engineText.addView(TextView(this).apply {
            text = tr("Google Cloud 需要 API Key")
            textSize = 11f
            setTextColor(0xFF6688AA.toInt())
        })
        engineRow.addView(engineText)

        val engineBtn = TextView(this).apply {
            text = engineOptions[prefs.ttsEngineType.coerceIn(0, engineOptions.lastIndex)]
            textSize = 12f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2744.toInt())
                cornerRadius = dp(6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
            setOnClickListener {
                android.app.AlertDialog.Builder(this@SettingsActivity, android.R.style.Theme_Material_Dialog_Alert)
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
        val keyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        keyRow.addView(TextView(this).apply {
            text = "Google Cloud API Key"
            textSize = 12f
            setTextColor(0xFF8899BB.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
        })
        val keyInput = android.widget.EditText(this).apply {
            setText(prefs.googleCloudTtsApiKey)
            textSize = 12f
            setTextColor(0xFFCCDDEE.toInt())
            setHintTextColor(0xFF445566.toInt())
            hint = "輸入 API Key"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF0D1525.toInt())
                cornerRadius = dp(4).toFloat()
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
        outer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { setMargins(0, dp(10), 0, dp(6)) }
            setBackgroundColor(0xFF223344.toInt())
        })

        // 語音選擇（列出所有裝置可用語音）
        val voiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val voiceTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
        }
        voiceTextCol.addView(TextView(this).apply {
            text = tr("語音音色")
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
        })
        voiceTextCol.addView(TextView(this).apply {
            text = tr("選擇 TTS 語音")
            textSize = 10f
            setTextColor(0xFF667788.toInt())
        })
        voiceRow.addView(voiceTextCol)

        val currentVoiceName = prefs.ttsVoiceName
        val voiceBtn = TextView(this).apply {
            text = if (currentVoiceName.isEmpty()) "系統預設" else currentVoiceName.substringAfterLast("#").take(30)
            textSize = 12f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2A3A.toInt())
                cornerRadius = dp(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
        }
        voiceBtn.setOnClickListener { btn ->
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            // 初始化一個臨時 TTS 引擎來列出語音
            val tmpTts = android.speech.tts.TextToSpeech(this@SettingsActivity) { status ->
                if (status != android.speech.tts.TextToSpeech.SUCCESS) {
                    handler.post { android.widget.Toast.makeText(this@SettingsActivity, "TTS 初始化失敗", android.widget.Toast.LENGTH_SHORT).show() }
                    return@TextToSpeech
                }
                handler.post {
                    val voices = try {
                        (btn.tag as? android.speech.tts.TextToSpeech)?.voices?.toList() ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    if (voices.isEmpty()) {
                        android.widget.Toast.makeText(this@SettingsActivity, "無可用語音", android.widget.Toast.LENGTH_SHORT).show()
                        (btn.tag as? android.speech.tts.TextToSpeech)?.shutdown()
                        btn.tag = null
                        return@post
                    }

                    // 按語言分組排序
                    val sorted = voices.sortedWith(compareBy<android.speech.tts.Voice> { it.locale.language }
                        .thenBy { it.locale.country }
                        .thenByDescending { it.quality }
                        .thenBy { it.name })

                    // 建立顯示名稱對應
                    fun voiceLabel(v: android.speech.tts.Voice): String {
                        val lang = v.locale.displayLanguage
                        val country = v.locale.displayCountry.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
                        val q = if (v.quality >= 400) " ★" else if (v.quality >= 300) " ☆" else ""
                        val net = if (v.isNetworkConnectionRequired) " ☁" else ""
                        return "$lang$country$q$net — ${v.name}"
                    }

                    // 自訂 Dialog：搜索框 + 列表
                    val dialogLayout = LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(16), dp(12), dp(16), 0)
                    }

                    val searchBox = android.widget.EditText(this@SettingsActivity).apply {
                        hint = "搜尋語言或音色名稱…"
                        textSize = 14f
                        setTextColor(0xFFE8F4FF.toInt())
                        setHintTextColor(0xFF556677.toInt())
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(0xFF1A2A3A.toInt())
                            cornerRadius = dp(8).toFloat()
                            setStroke(1, 0xFF334455.toInt())
                        }
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        inputType = android.text.InputType.TYPE_CLASS_TEXT
                        isSingleLine = true
                    }
                    dialogLayout.addView(searchBox, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) })

                    val listView = android.widget.ListView(this@SettingsActivity)
                    dialogLayout.addView(listView, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(360)
                    ))

                    // 可過濾的列表資料
                    val allItems = listOf<Pair<String, android.speech.tts.Voice?>>(
                        "🔄 系統預設" to null
                    ) + sorted.map { voiceLabel(it) to it }

                    var filteredItems = allItems.toMutableList()
                    val adapter = android.widget.ArrayAdapter(
                        this@SettingsActivity,
                        android.R.layout.simple_list_item_single_choice,
                        filteredItems.map { it.first }.toMutableList()
                    )
                    listView.adapter = adapter
                    listView.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE

                    // 選中目前的語音
                    val currentIdx = if (prefs.ttsVoiceName.isEmpty()) 0
                        else allItems.indexOfFirst { it.second?.name == prefs.ttsVoiceName }.let { if (it >= 0) it else 0 }
                    listView.setItemChecked(currentIdx, true)
                    listView.setSelection(currentIdx)

                    val dlg = android.app.AlertDialog.Builder(this@SettingsActivity, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(tr("選擇語音音色 (${sorted.size} 種)"))
                        .setView(dialogLayout)
                        .setOnDismissListener {
                            handler.postDelayed({
                                (btn.tag as? android.speech.tts.TextToSpeech)?.shutdown()
                                btn.tag = null
                            }, 3000)
                        }
                        .show()

                    // 搜索過濾
                    searchBox.addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            val q = s?.toString()?.trim()?.lowercase() ?: ""
                            filteredItems = if (q.isEmpty()) allItems.toMutableList()
                                else allItems.filter { it.first.lowercase().contains(q) }.toMutableList()
                            adapter.clear()
                            adapter.addAll(filteredItems.map { it.first })
                            adapter.notifyDataSetChanged()
                        }
                    })

                    // 點選項目
                    listView.setOnItemClickListener { _, _, position, _ ->
                        val item = filteredItems.getOrNull(position) ?: return@setOnItemClickListener
                        if (item.second == null) {
                            prefs.ttsVoiceName = ""
                            (btn as? TextView)?.text = tr("系統預設")
                        } else {
                            val selected = item.second ?: return@setOnItemClickListener
                            prefs.ttsVoiceName = selected.name
                            (btn as? TextView)?.text = item.first.take(35)

                            // 試聽選中的語音
                            val tmpEngine = btn.tag as? android.speech.tts.TextToSpeech
                            if (tmpEngine != null) {
                                tmpEngine.voice = selected
                                tmpEngine.setSpeechRate(prefs.ttsSpeed)
                                tmpEngine.speak("你好，我是雪凜", android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "preview")
                            }
                        }
                        dlg.dismiss()
                    }
                }
            }
            btn.tag = tmpTts
        }
        voiceRow.addView(voiceBtn)
        outer.addView(voiceRow)

        // 功能開關
        outer.addView(switchRow(tr("翻譯朗讀"), tr("翻譯完成後自動朗讀結果"), prefs.ttsTranslateRead) { prefs.ttsTranslateRead = it })
        outer.addView(switchRow(tr("Yuki 語音"), tr("雪凜對話泡泡同步語音"), prefs.ttsYukiVoice) { prefs.ttsYukiVoice = it })
        outer.addView(switchRow(tr("候選字朗讀"), tr("無障礙：選字時唸出候選字"), prefs.ttsCandidateRead) { prefs.ttsCandidateRead = it })

        // 語速調整
        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(4))
        }
        speedRow.addView(TextView(this).apply {
            text = tr("語速")
            textSize = 13f
            setTextColor(0xFFAABBCC.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val speedLabel = TextView(this).apply {
            text = "%.1fx".format(prefs.ttsSpeed)
            textSize = 12f
            setTextColor(0xFF9ECFFF.toInt())
            setPadding(dp(8), 0, dp(8), 0)
        }
        val speedBar = android.widget.SeekBar(this).apply {
            max = 30 // 0.5 ~ 2.0, step 0.05 → 30 steps
            progress = ((prefs.ttsSpeed - 0.5f) / 0.05f).toInt().coerceIn(0, 30)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val speed = 0.5f + progress * 0.05f
                    speedLabel.text = "%.1fx".format(speed)
                    if (fromUser) prefs.ttsSpeed = speed
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        speedRow.addView(speedBar)
        speedRow.addView(speedLabel)
        outer.addView(speedRow)

        // TTS 測試文字輸入
        val testInput = android.widget.EditText(this).apply {
            setText("你好，我是雪凜。YukiBoard 語音輸出測試成功！")
            textSize = 13f
            setTextColor(0xFFE0E0E0.toInt())
            setHintTextColor(0xFF888888.toInt())
            hint = "輸入要測試的文字..."
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minLines = 1
            maxLines = 3
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF0D1B2A.toInt())
                cornerRadius = dp(6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
        }
        outer.addView(testInput)

        // TTS 測試按鈕
        val testBtn = TextView(this).apply {
            text = tr("▶ 測試語音輸出")
            textSize = 13f
            setTextColor(0xFF66AAFF.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2744.toInt())
                cornerRadius = dp(6).toFloat()
                setStroke(1, 0xFF3A5580.toInt())
            }
            setOnClickListener { btn ->
                val testText = testInput.text.toString().trim()
                if (testText.isBlank()) {
                    android.widget.Toast.makeText(this@SettingsActivity, "請輸入要測試的文字", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 先存 EditText 當前值（避免焦點未離開時 prefs 還沒更新）
                prefs.googleCloudTtsApiKey = keyInput.text.toString().trim()
                btn.isEnabled = false
                (btn as? TextView)?.text = tr("⏳ 初始化中...")

                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                val resetBtn = {
                    btn.isEnabled = true
                    (btn as? TextView)?.text = tr("▶ 測試語音輸出")
                }

                when (prefs.ttsEngineType) {
                    1 -> {
                        val key = prefs.googleCloudTtsApiKey
                        if (key.isBlank()) {
                            android.widget.Toast.makeText(this@SettingsActivity, "請先填入 Google Cloud API Key", android.widget.Toast.LENGTH_SHORT).show()
                            resetBtn()
                            return@setOnClickListener
                        }
                        (btn as? TextView)?.text = tr("🔊 呼叫 Google Cloud...")
                        android.widget.Toast.makeText(this@SettingsActivity, "API Key: ${key.take(10)}... 呼叫中", android.widget.Toast.LENGTH_SHORT).show()
                        val engine = com.snowrealm.yukiboard.speech.GoogleCloudTtsEngine(key)
                        engine.speak(testText, null, prefs.ttsSpeed) {
                            mainHandler.post {
                                val err = engine.lastError
                                if (err != null) {
                                    android.widget.Toast.makeText(this@SettingsActivity,
                                        "Google TTS 失敗：$err", android.widget.Toast.LENGTH_LONG).show()
                                    (btn as? TextView)?.text = "❌ $err"
                                }
                                engine.destroy()
                                // 5 秒後重置按鈕（讓使用者看到錯誤）
                                mainHandler.postDelayed({ resetBtn() }, if (err != null) 8000L else 500L)
                            }
                        }
                    }
                    else -> {
                        // 直接用 Android TextToSpeech API（最簡單，排除包裝問題）
                        var rawTts: android.speech.tts.TextToSpeech? = null
                        rawTts = android.speech.tts.TextToSpeech(applicationContext) { status ->
                            mainHandler.post {
                                val audioMgr = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                                val vol = audioMgr.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                val maxVol = audioMgr.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                val defaultEngine = rawTts?.defaultEngine ?: "unknown"

                                if (status != android.speech.tts.TextToSpeech.SUCCESS) {
                                    android.widget.Toast.makeText(this@SettingsActivity,
                                        "TTS 初始化失敗 status=$status engine=$defaultEngine", android.widget.Toast.LENGTH_LONG).show()
                                    rawTts?.shutdown()
                                    resetBtn()
                                    return@post
                                }

                                // 套用使用者選的語音音色
                                val selectedVoiceName = prefs.ttsVoiceName
                                val voiceApplied = if (selectedVoiceName.isNotEmpty()) {
                                    val voice = rawTts?.voices?.firstOrNull { it.name == selectedVoiceName }
                                    if (voice != null) {
                                        rawTts?.voice = voice
                                        true
                                    } else false
                                } else false
                                // 沒選語音或找不到時 fallback 設定語言
                                if (!voiceApplied) {
                                    rawTts?.setLanguage(java.util.Locale.TAIWAN)
                                }
                                val voiceLabel = if (voiceApplied) selectedVoiceName.substringAfterLast("#").take(20) else "default"
                                val info = "engine=$defaultEngine voice=$voiceLabel vol=$vol/$maxVol"
                                android.widget.Toast.makeText(this@SettingsActivity, "TTS: $info", android.widget.Toast.LENGTH_LONG).show()

                                if (vol == 0) {
                                    // 自動把媒體音量調到一半
                                    audioMgr.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxVol / 2, android.media.AudioManager.FLAG_SHOW_UI)
                                }

                                (btn as? TextView)?.text = tr("🔊 播放中... ($info)")
                                rawTts?.setSpeechRate(prefs.ttsSpeed)
                                val speakResult = rawTts?.speak(testText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "test_utterance")
                                android.util.Log.i("TTS_TEST", "speakResult=$speakResult $info")

                                // 3秒後自動重置按鈕
                                mainHandler.postDelayed({
                                    rawTts?.shutdown()
                                    resetBtn()
                                }, 5000)
                            }
                        }
                    }
                }
            }
        }
        outer.addView(testBtn)

        return outer
    }

    private fun buildCandidateColorLegend(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val title = TextView(this).apply {
            text = tr("候選字顏色說明")
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(0, 0, 0, dp(6))
        }
        outer.addView(title)

        val legends = listOf(
            0xFFCCCCCC.toInt() to "白/灰 — 字典匹配（一般候選）",
            0xFFFF9933.toInt() to "橙色 — 智慧糾錯（拼字修正）",
            0xFF66CC88.toInt() to "綠色 — 自適應學習（常用詞）",
            0xFF66AAFF.toInt() to "藍�� — 語句預測（bigram/trigram）",
            0xFFFFD700.toInt() to "金色 — 使用者詞庫（自訂/學習詞）",
            0xFFBB88FF.toInt() to "紫色 — 整句預測",
            0xFFE8B84D.toInt() to "金色 ✨ — AI 候選（Claw AI 生成）"
        )
        for ((color, desc) in legends) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                    setMargins(0, 0, dp(8), 0)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                }
            }
            row.addView(dot)
            row.addView(TextView(this).apply {
                text = desc
                textSize = 11f
                setTextColor(0xFFAABBCC.toInt())
            })
            outer.addView(row)
        }
        return outer
    }

    // ─────────────────────────────────────────────────────────
    // 輸入法選擇卡片
    // ─────────────────────────────────────────────────────────

    private fun buildInputMethodCard(): LinearLayout {
        val card = simpleCard("輸入法選擇")

        val allModes = listOf(
            com.snowrealm.yukiboard.input.router.InputMode.ENGLISH,
            com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO,
            com.snowrealm.yukiboard.input.router.InputMode.PINYIN,
            com.snowrealm.yukiboard.input.router.InputMode.CANGJIE,
            com.snowrealm.yukiboard.input.router.InputMode.SPEED_CANGJIE,
            com.snowrealm.yukiboard.input.router.InputMode.JAPANESE,
            com.snowrealm.yukiboard.input.router.InputMode.KOREAN,
        )

        val saved = prefs.enabledInputModes
        val enabledSet = if (saved.isBlank()) {
            allModes.map { it.name }.toMutableSet()
        } else {
            saved.split(",").filter { it.isNotBlank() }.toMutableSet()
        }

        card.addView(TextView(this).apply {
            text = tr("勾選要啟用的輸入法（切換語言時循環）")
            textSize = 12f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(8))
        })

        for (mode in allModes) {
            val cb = android.widget.CheckBox(this).apply {
                text = "${mode.shortLabel}  ${mode.displayName}"
                setTextColor(0xFFE8F4FF.toInt())
                textSize = 14f
                isChecked = mode.name in enabledSet
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) enabledSet.add(mode.name) else enabledSet.remove(mode.name)
                    if (enabledSet.isEmpty()) {
                        enabledSet.add(com.snowrealm.yukiboard.input.router.InputMode.ENGLISH.name)
                        isChecked = true
                        Toast.makeText(this@SettingsActivity, "至少須啟用一個輸入法", Toast.LENGTH_SHORT).show()
                    }
                    prefs.enabledInputModes = enabledSet.joinToString(",")
                }
            }
            card.addView(cb)
        }

        return card
    }

    // ─────────────────────────────────────────────────────────
    // Snippet 管理卡片
    // ─────────────────────────────────────────────────────────

    private fun buildSnippetCard(): LinearLayout {
        val card = simpleCard("Snippet 管理（開發者模式）")
        val snippetMgr = com.snowrealm.yukiboard.input.snippet.SnippetManager(this)

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun refreshSnippetList() {
            listContainer.removeAllViews()

            // 自訂 snippet
            val custom = snippetMgr.getCustomSnippets()
            if (custom.isNotEmpty()) {
                listContainer.addView(TextView(this).apply {
                    text = tr("自訂 Snippet")
                    textSize = 13f
                    setTextColor(0xFF7FD8FF.toInt())
                    setPadding(dp(4), dp(8), dp(4), dp(4))
                })
                for ((abbr, expansion) in custom) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(dp(4), dp(4), dp(4), dp(4))
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    row.addView(TextView(this).apply {
                        text = "$abbr → ${expansion.take(40)}"
                        setTextColor(0xFFE8F4FF.toInt())
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    row.addView(TextView(this).apply {
                        text = "✕"
                        setTextColor(0xFFFF6B6B.toInt())
                        textSize = 16f
                        setPadding(dp(8), 0, dp(4), 0)
                        setOnClickListener {
                            snippetMgr.removeCustomSnippet(abbr)
                            refreshSnippetList()
                        }
                    })
                    listContainer.addView(row)
                }
            }

            // 內建 snippet 列表（摺疊顯示）
            val builtIn = snippetMgr.getBuiltInAbbreviations()
            val builtInContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            val toggleLabel = TextView(this).apply {
                text = tr("▸ 內建 Snippet (${builtIn.size} 個) — 點擊展開")
                textSize = 13f
                setTextColor(0xFF6688AA.toInt())
                setPadding(dp(4), dp(8), dp(4), dp(4))
                setOnClickListener {
                    if (builtInContainer.visibility == View.GONE) {
                        builtInContainer.visibility = View.VISIBLE
                        text = tr("▾ 內建 Snippet (${builtIn.size} 個) — 點擊收合")
                    } else {
                        builtInContainer.visibility = View.GONE
                        text = tr("▸ 內建 Snippet (${builtIn.size} 個) — 點擊展開")
                    }
                }
            }
            for (abbr in builtIn) {
                val result = snippetMgr.expand(abbr)
                builtInContainer.addView(TextView(this).apply {
                    text = "$abbr → ${result?.expansion?.replace("\n", "↵")?.take(50) ?: ""}"
                    setTextColor(0xFF8899AA.toInt())
                    textSize = 12f
                    setPadding(dp(12), dp(2), dp(4), dp(2))
                })
            }
            listContainer.addView(toggleLabel)
            listContainer.addView(builtInContainer)
        }

        refreshSnippetList()

        // 新增按鈕
        card.addView(Button(this).apply {
            text = tr("+ 新增 Snippet")
            setTextColor(0xFF7FD8FF.toInt())
            setBackgroundColor(0xFF1A3A5C.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(4)) }
            setOnClickListener {
                val dialogView = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(16), dp(20), dp(8))
                }
                val abbrInput = android.widget.EditText(this@SettingsActivity).apply {
                    hint = "縮寫（例如：log）"
                    setHintTextColor(0x88FFFFFF.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFF1A3A5C.toInt())
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                val expansionInput = android.widget.EditText(this@SettingsActivity).apply {
                    hint = "展開文字（例如：console.log()）"
                    setHintTextColor(0x88FFFFFF.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFF1A3A5C.toInt())
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    minLines = 2
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                }
                dialogView.addView(abbrInput)
                dialogView.addView(expansionInput)

                android.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(tr("新增 Snippet"))
                    .setView(dialogView)
                    .setPositiveButton(tr("新增")) { _, _ ->
                        val abbr = abbrInput.text.toString().trim()
                        val expansion = expansionInput.text.toString()
                        if (abbr.isNotBlank() && expansion.isNotBlank()) {
                            snippetMgr.addCustomSnippet(abbr, expansion)
                            refreshSnippetList()
                        }
                    }
                    .setNegativeButton(tr("取消"), null)
                    .show()
            }
        })

        card.addView(listContainer)
        return card
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun requestMediaPermissionThen(type: String, action: () -> Unit) {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (type == "video") android.Manifest.permission.READ_MEDIA_VIDEO
            else android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingMediaAction = action
            requestPermissions(arrayOf(permission), RC_MEDIA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_MEDIA_PERMISSION) {
            if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingMediaAction?.invoke()
            } else {
                Toast.makeText(this, "需要相冊存取權限", Toast.LENGTH_SHORT).show()
            }
            pendingMediaAction = null
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        stopVideoPreview()
        val bgDir = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getBackgroundDir(this)
        bgDir.listFiles()?.forEach { it.delete() }

        val mime = contentResolver.getType(uri) ?: ""
        val ext = when {
            mime.contains("webm")           -> "webm"
            mime.contains("3gp")            -> "3gp"
            mime.contains("x-matroska")     -> "mkv"
            mime.contains("quicktime")      -> "mov"
            mime.contains("x-msvideo")      -> "avi"
            mime.contains("mp2t")           -> "ts"
            mime.contains("video")          -> "mp4"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("webp")           -> "webp"
            mime.contains("gif")            -> "gif"
            mime.contains("bmp")            -> "bmp"
            mime.contains("heic") || mime.contains("heif") -> "heic"
            mime.contains("avif")           -> "avif"
            mime.contains("png")            -> "png"
            else -> if (requestCode == REQUEST_PICK_BG_VIDEO) "mp4" else "jpg"
        }

        val dest = java.io.File(bgDir, "keyboard_bg.$ext")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            prefs.useCustomBackground = true
            refreshBgPreview()
            // 通知 IME 即時更新背景
            sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
            Toast.makeText(this, "背景已設定", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "背景設定失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        bgSyncReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                refreshBgPreview()
                syncBgSliders()
            }
        }
        val filter = android.content.IntentFilter("com.snowrealm.yukiboard.ACTION_BG_UPDATED")
        registerReceiver(bgSyncReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        bgSyncReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        bgSyncReceiver = null
    }

    override fun onPause() {
        super.onPause()
        bgVideoPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        bgVideoPlayer?.start()
    }

    override fun onDestroy() {
        stopVideoPreview()
        super.onDestroy()
        activityScope.cancel()
    }

    private fun syncBgSliders() {
        bgOverlaySeekBar?.progress = prefs.bgOverlayAlpha
        bgOverlayLabel?.text = tr("遮罩深度: %d%%").format((prefs.bgOverlayAlpha * 100) / 255)
        bgPreviewOverlay?.setBackgroundColor(
            android.graphics.Color.argb(prefs.bgOverlayAlpha, 0, 0, 0)
        )
        bgBtnCrop?.let { btn ->
            val selected = prefs.bgScaleMode == 0
            btn.setTextColor(if (selected) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            btn.setBackgroundColor(if (selected) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
        }
        bgBtnFit?.let { btn ->
            val selected = prefs.bgScaleMode == 1
            btn.setTextColor(if (selected) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            btn.setBackgroundColor(if (selected) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
        }
        bgOffsetXSeekBar?.progress = (prefs.bgOffsetX * 100).toInt()
        bgOffsetXLabel?.text = tr("水平位置 X: %d%%").format((prefs.bgOffsetX * 100).toInt())
        bgOffsetYSeekBar?.progress = (prefs.bgOffsetY * 100).toInt()
        bgOffsetYLabel?.text = tr("垂直位置 Y: %d%%").format((prefs.bgOffsetY * 100).toInt())
        bgRotBtns.forEachIndexed { i, btn ->
            val d = listOf(0, 90, 180, 270)[i]
            val selected = prefs.bgRotation == d
            btn.setTextColor(if (selected) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            btn.setBackgroundColor(if (selected) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
        }
        updateBgPreviewMatrix()
        updateVideoPreviewTransform()
        fullPreviewView?.invalidate()
    }
}

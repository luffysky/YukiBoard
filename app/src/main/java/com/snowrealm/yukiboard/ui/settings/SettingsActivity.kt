package com.snowrealm.yukiboard.ui.settings

import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.theme.ThemeManager
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PICK_BG_IMAGE = 9001
        private const val REQUEST_PICK_BG_VIDEO = 9002
    }

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRestarting = false
    private lateinit var themeManager: ThemeManager
    private lateinit var prefs: YukiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeManager = ThemeManager.getInstance(this)
        prefs = YukiPreferences(this)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF1A2035.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
        }

        // ── 標題 + 教學按鈕 ──
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(16))
        }
        titleRow.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 24f
            setTextColor(0xFFE8F4FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(Button(this).apply {
            text = "功能說明"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
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
        titleRow.addView(Button(this).apply {
            val auth = com.snowrealm.yukiboard.auth.SnowRealmAuthClient(this@SettingsActivity)
            text = if (auth.isLoggedIn) auth.currentDisplayName ?: "帳號" else "登入"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(if (auth.isLoggedIn) 0xFF1B6B3A.toInt() else 0xFF4A90E2.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, com.snowrealm.yukiboard.ui.auth.LoginActivity::class.java))
            }
        })
        root.addView(titleRow)

        // ═══════════════════════════════════════════════════════
        // 1. AI 功能
        // ═══════════════════════════════════════════════════════
        val aiChildren = mutableListOf<View>()
        root.addView(cardGroup("AI 功能", prefs.aiEnabled, { prefs.aiEnabled = it }, aiChildren) {
            aiChildren += switchRow("AI 補字預測", "智慧預測下一個詞", prefs.aiPrediction) { prefs.aiPrediction = it }
            aiChildren += switchRow("雪凜寫手", "幫你寫、回覆、換說法", prefs.aiWriter) { prefs.aiWriter = it }
            aiChildren += switchRow("即時翻譯", "中英日等多語言翻譯", prefs.aiTranslate) { prefs.aiTranslate = it }
            aiChildren += switchRow("情聖月老", "讓訊息更有魅力", prefs.aiCupid) { prefs.aiCupid = it }
            aiChildren += switchRow("AI 語音 (Whisper)", "OpenAI 語音辨識", prefs.aiWhisper) { prefs.aiWhisper = it }
            aiChildren += switchRow("文法糾錯", "一鍵修正錯字和文法", prefs.aiGrammarCheck) { prefs.aiGrammarCheck = it }
            aiChildren += switchRow("語氣調整", "正式/casual/可愛/專業", prefs.aiToneAdjust) { prefs.aiToneAdjust = it }
            aiChildren += switchRow("摘要精簡", "長文縮成一句話", prefs.aiSummarize) { prefs.aiSummarize = it }
            aiChildren += switchRow("迷因生成", "產生流行語和梗圖文字", prefs.aiMemeText) { prefs.aiMemeText = it }
            aiChildren += switchRow("Emoji 翻譯", "文字與 Emoji 互轉", prefs.aiEmojiTranslate) { prefs.aiEmojiTranslate = it }
            aiChildren += switchRow("快速回覆", "建議整句回覆", prefs.aiQuickReply) { prefs.aiQuickReply = it }
            aiChildren += switchRow("程式碼補全", "開發者模式中 AI 補完", prefs.aiCodeComplete) { prefs.aiCodeComplete = it }
            aiChildren += switchRow("正則產生器", "描述需求產生 Regex", prefs.aiRegexGen) { prefs.aiRegexGen = it }
            aiChildren += switchRow("Commit 訊息", "貼上 diff 產生 commit message", prefs.aiCommitMessage) { prefs.aiCommitMessage = it }
            aiChildren += switchRow("雪凜聊天", "跟雪凜對話，幫你想怎麼說", prefs.aiYukiChat) { prefs.aiYukiChat = it }
            aiChildren += switchRow("心情日記", "被動偵測打字情緒", prefs.aiMoodDiary) { prefs.aiMoodDiary = it }
            aiChildren += switchRow("天氣助理", "天氣資訊與穿衣建議", prefs.aiWeather) { prefs.aiWeather = it }

            // AI 設定按鈕
            aiChildren += Button(this).apply {
                text = "AI 設定 / Provider"
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
        root.addView(cardGroup("雪凜行為", prefs.yukiSarcasticComments, null, yukiChildren) {
            yukiChildren += switchRow("微吐槽", "雪凜偶爾的有趣短評", prefs.yukiSarcasticComments) { prefs.yukiSarcasticComments = it }
            yukiChildren += switchRow("情緒冷靜期", "偵測激烈文字時暫停提醒", prefs.yukiEmotionalCooldown) { prefs.yukiEmotionalCooldown = it }
            yukiChildren += switchRow("心情日記被動偵測", "自動分析打字情緒", prefs.yukiMoodDiaryPassive) { prefs.yukiMoodDiaryPassive = it }
            yukiChildren += switchRow("天氣主動提醒", "天氣變化時雪凜提醒", prefs.yukiWeatherAlert) { prefs.yukiWeatherAlert = it }
            yukiChildren += switchRow("深夜關心", "深夜打字時的溫馨提醒", prefs.yukiLateNightComment) { prefs.yukiLateNightComment = it }
        })

        // ═══════════════════════════════════════════════════════
        // 3. 感測器
        // ═══════════════════════════════════════════════════════
        val sensorChildren = mutableListOf<View>()
        root.addView(cardGroup("感測器", null, null, sensorChildren) {
            sensorChildren += switchRow("搖手機復原", "搖晃手機觸發 Ctrl+Z", prefs.shakeToUndoEnabled) { prefs.shakeToUndoEnabled = it }
            sensorChildren += switchRow("陀螺儀游標", "長按空白鍵 + 傾斜控制游標", prefs.gyroscopeCursorEnabled) { prefs.gyroscopeCursorEnabled = it }

            // 陀螺儀靈敏度滑桿
            val sensitivityLabel = TextView(this).apply {
                text = "靈敏度: %.1f".format(prefs.gyroscopeSensitivity)
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
                        sensitivityLabel.text = "靈敏度: %.1f".format(v)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }

            sensorChildren += switchRow("App 自動切換模式", "偵測前景 App 自動切換模式", prefs.appAutoDetectEnabled) { prefs.appAutoDetectEnabled = it }
        })

        // ═══════════════════════════════════════════════════════
        // 4. 動畫特效
        // ═══════════════════════════════════════════════════════
        val animChildren = mutableListOf<View>()
        root.addView(cardGroup("動畫特效", null, null, animChildren) {
            animChildren += switchRow("流光動畫", "OpenGL ES 流光覆蓋效果", prefs.flowLightEnabled) { prefs.flowLightEnabled = it }
            animChildren += switchRow("角色動畫", "雪凜 Lottie 浮動角色", prefs.characterAnimEnabled) { prefs.characterAnimEnabled = it }
            animChildren += switchRow("按鍵粒子效果", "打字時的粒子動畫", prefs.keyParticleEffect) { prefs.keyParticleEffect = it }
            animChildren += switchRow("按鍵震動", "按鍵時的觸覺回饋", prefs.keyPressVibration) { prefs.keyPressVibration = it }
            animChildren += switchRow("按鍵音效", "按鍵時的聲音回饋", prefs.keySoundEnabled) { prefs.keySoundEnabled = it }
        })

        // ═══════════════════════════════════════════════════════
        // 5. 遊戲
        // ═══════════════════════════════════════════════════════
        val gameChildren = mutableListOf<View>()
        root.addView(cardGroup("遊戲", prefs.gamesEnabled, { prefs.gamesEnabled = it }, gameChildren) {
            gameChildren += switchRow("背景音樂", "遊戲背景音樂", prefs.gameBackgroundMusic) { prefs.gameBackgroundMusic = it }
            gameChildren += switchRow("音效", "遊戲音效", prefs.gameSoundEffects) { prefs.gameSoundEffects = it }
        })

        // ═══════════════════════════════════════════════════════
        // 6. 輸入行為 + 外觀
        // ═══════════════════════════════════════════════════════
        val inputChildren = mutableListOf<View>()
        root.addView(cardGroup("輸入行為", null, null, inputChildren) {
            inputChildren += switchRow("滑動輸入", "滑動手勢輸入", prefs.swipeInput) { prefs.swipeInput = it }
            inputChildren += switchRow("滑動軌跡特效", "滑動時顯示紫色光暈路徑", prefs.swipeTrailEnabled) { prefs.swipeTrailEnabled = it }
            inputChildren += switchRow("自動更正", "英文自動更正", prefs.autoCorrect) { prefs.autoCorrect = it }
            inputChildren += switchRow("Snippet 展開", "開發者模式：縮寫 + Tab 展開", prefs.snippetEnabled) { prefs.snippetEnabled = it }
            inputChildren += switchRow("直打注音", "注音符號直接輸出不轉漢字", prefs.rawBopomofoMode) { prefs.rawBopomofoMode = it }
            inputChildren += switchRow("私房話模式", "AES 加密訊息（Enter 送出時加密）", prefs.secretMessageMode) { prefs.secretMessageMode = it }
            inputChildren += switchRow("語氣偵測", "偵測打字語氣", prefs.toneDetection) { prefs.toneDetection = it }
            inputChildren += switchRow("懸浮面板", "啟用懸浮面板功能", prefs.floatingPanelEnabled) { prefs.floatingPanelEnabled = it }
            inputChildren += switchRow("遠端鍵盤", "藍牙/WiFi 遠端控制", prefs.remoteKeyboardEnabled) { prefs.remoteKeyboardEnabled = it }

            // 語音引擎選擇
            inputChildren += speechEngineSelector()
        })

        // ═══════════════════════════════════════════════════════
        // 7. 智慧輸入
        // ═══════════════════════════════════════════════════════
        val smartChildren = mutableListOf<View>()
        root.addView(cardGroup("智慧輸入", null, null, smartChildren) {
            smartChildren += switchRow("智慧糾錯", "AI 偵測打錯字並自動修正", prefs.smartCorrection) { prefs.smartCorrection = it }
            smartChildren += switchRow("自動學詞", "常用字彙自動加入字典", prefs.adaptiveDict) { prefs.adaptiveDict = it }
            smartChildren += switchRow("語句預測", "預測下一個可能的詞或片語", prefs.phrasePredict) { prefs.phrasePredict = it }
            smartChildren += switchRow("習慣追蹤", "學習打字習慣提升預測精準度", prefs.habitTracking) { prefs.habitTracking = it }
        })

        // ═══════════════════════════════════════════════════════
        // 主題 / 外觀 / 語言 / 背景（獨立卡片）
        // ═══════════════════════════════════════════════════════
        root.addView(buildThemeCard())
        root.addView(buildBackgroundCard())
        root.addView(buildAppearanceCard())
        root.addView(buildLanguageCard())
        root.addView(buildUserDictCard())

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
    private var bgVideoPlayer: android.media.MediaPlayer? = null
    private var bgVideoTexture: android.view.TextureView? = null
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

        val previewImg = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.MATRIX
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bgPreviewImage = previewImg
        bgPreviewContainer = previewContainer
        previewContainer.addView(previewImg)

        // 遮罩覆蓋（模擬鍵盤上的效果）
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
            text = "尚未設定背景"
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
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_PICK_BG_IMAGE)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) }
        })

        row.addView(styledButton("選影片", 0xFF2A3A5C.toInt(), 0xFFE8F4FF.toInt()) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "video/*"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_PICK_BG_VIDEO)
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
            bgPreviewLabel?.text = "尚未設定背景"
            bgPreviewLabel?.visibility = View.VISIBLE
            fullPreviewBgBitmap = null
            fullPreviewView?.invalidate()
            Toast.makeText(this, "已清除背景", Toast.LENGTH_SHORT).show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 0.7f).apply { marginStart = dp(4) }
        })

        card.addView(row)

        // ── 遮罩透明度滑桿 ──
        val alphaLabel = TextView(this).apply {
            text = "遮罩深度: %d%%".format((prefs.bgOverlayAlpha * 100) / 255)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(8), 0, dp(4))
        }
        card.addView(alphaLabel)

        card.addView(SeekBar(this).apply {
            max = 255
            progress = prefs.bgOverlayAlpha
            setPadding(dp(12), dp(4), dp(12), dp(8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.bgOverlayAlpha = p
                    alphaLabel.text = "遮罩深度: %d%%".format((p * 100) / 255)
                    bgPreviewOverlay?.setBackgroundColor(android.graphics.Color.argb(p, 0, 0, 0))
                    fullPreviewView?.invalidate()
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
            text = "顯示比例:"
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        scaleModeRow.addView(scaleModeLabel)

        val btnCrop = android.widget.Button(this).apply {
            text = "保持比例"
            textSize = 11f
            setTextColor(if (prefs.bgScaleMode == 0) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            setBackgroundColor(if (prefs.bgScaleMode == 0) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(8) }
        }
        val btnFit = android.widget.Button(this).apply {
            text = "整張塞入"
            textSize = 11f
            setTextColor(if (prefs.bgScaleMode == 1) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            setBackgroundColor(if (prefs.bgScaleMode == 1) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(4) }
        }
        btnCrop.setOnClickListener {
            prefs.bgScaleMode = 0
            btnCrop.setTextColor(0xFF111827.toInt()); btnCrop.setBackgroundColor(0xFF7FD8FF.toInt())
            btnFit.setTextColor(0xFFE8F4FF.toInt()); btnFit.setBackgroundColor(0xFF2A3A5C.toInt())
            updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
        }
        btnFit.setOnClickListener {
            prefs.bgScaleMode = 1
            btnFit.setTextColor(0xFF111827.toInt()); btnFit.setBackgroundColor(0xFF7FD8FF.toInt())
            btnCrop.setTextColor(0xFFE8F4FF.toInt()); btnCrop.setBackgroundColor(0xFF2A3A5C.toInt())
            updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
        }
        scaleModeRow.addView(btnCrop)
        scaleModeRow.addView(btnFit)
        card.addView(scaleModeRow)

        // ── 背景位置 X 拉桿 ──
        val xLabel = TextView(this).apply {
            text = "水平位置 X: %d%%".format((prefs.bgOffsetX * 100).toInt())
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(8), 0, dp(4))
        }
        card.addView(xLabel)
        card.addView(SeekBar(this).apply {
            max = 100
            progress = (prefs.bgOffsetX * 100).toInt()
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.bgOffsetX = p / 100f
                    xLabel.text = "水平位置 X: %d%%".format(p)
                    updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 背景位置 Y 拉桿 ──
        val yLabel = TextView(this).apply {
            text = "垂直位置 Y: %d%%".format((prefs.bgOffsetY * 100).toInt())
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(4), 0, dp(4))
        }
        card.addView(yLabel)
        card.addView(SeekBar(this).apply {
            max = 100
            progress = (prefs.bgOffsetY * 100).toInt()
            setPadding(dp(12), dp(4), dp(12), dp(8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.bgOffsetY = p / 100f
                    yLabel.text = "垂直位置 Y: %d%%".format(p)
                    updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 鍵盤高度拉桿 ──
        val heightPct = ((prefs.keyboardHeightRatio - 0.25f) / 0.30f * 100).toInt().coerceIn(0, 100)
        val kbHeightLabel = TextView(this).apply {
            text = "鍵盤高度: %d%%".format(heightPct)
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
                    kbHeightLabel.text = "鍵盤高度: %d%%".format(p)
                    updateFullPreviewHeight()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 按鍵大小拉桿 ──
        val keySizeLabel = TextView(this).apply {
            text = "按鍵大小: %d%%".format(prefs.keyScalePercent)
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
                    keySizeLabel.text = "按鍵大小: %d%%".format(p + 70)
                    fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 按鍵透明度拉桿 ──
        val keyOpLabel = TextView(this).apply {
            text = "按鍵不透明度: %d%%".format((prefs.keyOpacity * 100) / 255)
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
                    keyOpLabel.text = "按鍵不透明度: %d%%".format((p * 100) / 255)
                    fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 鍵盤完整預覽（背景 + 遮罩 + 按鍵） ──
        val previewTitle = TextView(this).apply {
            text = "鍵盤預覽"
            textSize = 13f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(12), dp(12), 0, dp(4))
        }
        card.addView(previewTitle)

        val kbPreview = buildFullKeyboardPreview()
        fullPreviewView = kbPreview
        card.addView(kbPreview)

        // ── 透明鍵盤 ──
        card.addView(switchRow("透明鍵盤", "背景完全透視到底層 App", prefs.transparentKeyboard) {
            prefs.transparentKeyboard = it
        })

        // 透明度滑桿
        val tAlphaLabel = TextView(this).apply {
            text = "透明度: %d%%".format((prefs.transparentKeyboardAlpha * 100) / 255)
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
                    tAlphaLabel.text = "透明度: %d%%".format((p * 100) / 255)
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
                try {
                    val mp = android.media.MediaPlayer().apply {
                        setDataSource(filePath)
                        setSurface(android.view.Surface(st))
                        isLooping = true
                        setVolume(0f, 0f)
                        prepareAsync()
                        setOnPreparedListener { player ->
                            bgVideoWidth = player.videoWidth
                            bgVideoHeight = player.videoHeight
                            updateVideoPreviewTransform()
                            player.start()
                        }
                    }
                    bgVideoPlayer = mp
                } catch (e: Exception) {
                    bgPreviewLabel?.text = "🎬 影片播放失敗"
                    bgPreviewLabel?.visibility = View.VISIBLE
                }
            }
            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                updateVideoPreviewTransform()
            }
            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                // 僅釋放 MediaPlayer，不移除 View（避免 stopVideoPreview 重入）
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

    /** 根據 prefs 的 bgOffsetX/Y + bgScaleMode 更新預覽圖的 Matrix */
    private fun updateBgPreviewMatrix() {
        val iv = bgPreviewImage ?: return
        val drawable = iv.drawable ?: return
        val bmpW = drawable.intrinsicWidth.toFloat()
        val bmpH = drawable.intrinsicHeight.toFloat()
        if (bmpW <= 0 || bmpH <= 0) return

        iv.post {
            val vw = iv.width.toFloat()
            val vh = iv.height.toFloat()
            if (vw <= 0 || vh <= 0) return@post

            val matrix = android.graphics.Matrix()
            if (prefs.bgScaleMode == 1) {
                // 整張塞入（拉伸填滿）
                matrix.setScale(vw / bmpW, vh / bmpH)
            } else {
                // 保持比例（裁切）
                val scale = maxOf(vw / bmpW, vh / bmpH)
                val scaledW = bmpW * scale
                val scaledH = bmpH * scale
                val maxDx = (scaledW - vw).coerceAtLeast(0f)
                val maxDy = (scaledH - vh).coerceAtLeast(0f)
                val dx = -maxDx * prefs.bgOffsetX
                val dy = -maxDy * prefs.bgOffsetY
                matrix.setScale(scale, scale)
                matrix.postTranslate(dx, dy)
            }
            iv.imageMatrix = matrix
        }
    }

    /** 根據 prefs 的 bgScaleMode + bgOffsetX/Y 更新影片預覽 TextureView 的 transform */
    private fun updateVideoPreviewTransform() {
        val tv = bgVideoTexture ?: return
        val vw = tv.width.toFloat()
        val vh = tv.height.toFloat()
        if (vw <= 0 || vh <= 0 || bgVideoWidth <= 0 || bgVideoHeight <= 0) return

        val matrix = android.graphics.Matrix()
        if (prefs.bgScaleMode == 1) {
            matrix.setScale(vw / bgVideoWidth, vh / bgVideoHeight)
        } else {
            val scale = maxOf(vw / bgVideoWidth, vh / bgVideoHeight)
            val scaledW = bgVideoWidth * scale
            val scaledH = bgVideoHeight * scale
            val maxDx = (scaledW - vw).coerceAtLeast(0f)
            val maxDy = (scaledH - vh).coerceAtLeast(0f)
            matrix.setScale(scale, scale)
            matrix.postTranslate(-maxDx * prefs.bgOffsetX, -maxDy * prefs.bgOffsetY)
        }
        tv.setTransform(matrix)
        tv.invalidate()
    }

    private fun refreshBgPreview() {
        val savedBg = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getSavedBackground(this)
        if (savedBg != null && !com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.isVideoFile(savedBg)) {
            // 圖片預覽
            activityScope.launch(Dispatchers.IO) {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = android.graphics.BitmapFactory.decodeFile(savedBg.absolutePath, opts)
                withContext(Dispatchers.Main) {
                    bgPreviewImage?.setImageBitmap(bmp)
                    bgPreviewLabel?.visibility = View.GONE
                    updateBgPreviewMatrix()
                }
            }
        } else if (savedBg != null) {
            // 影片預覽：TextureView 播放
            stopVideoPreview()
            bgPreviewImage?.setImageBitmap(null)
            bgPreviewLabel?.visibility = View.GONE
            startVideoPreview(savedBg.absolutePath)
        } else {
            bgPreviewImage?.setImageBitmap(null)
            bgPreviewLabel?.text = "尚未設定背景"
            bgPreviewLabel?.visibility = View.VISIBLE
        }
        // 同步更新鍵盤完整預覽的背景
        reloadFullPreviewBitmap()
        fullPreviewView?.invalidate()
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
        fullPreviewBgBitmap = if (savedBg != null) {
            if (com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.isVideoFile(savedBg)) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(savedBg.absolutePath)
                    val frame = retriever.getFrameAtTime(0)
                    retriever.release()
                    frame
                } catch (_: Exception) { null }
            } else {
                try {
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeFile(savedBg.absolutePath, opts)
                } catch (_: Exception) { null }
            }
        } else null
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
                textPaint.color = if (prefs.customKeyLabelColor != 0) prefs.customKeyLabelColor
                    else theme.keyLabelColorInt

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
            text = "按鍵字體顏色"
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
            text = "＋ 自訂顏色..."
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
            .setTitle("自訂字體顏色")
            .setView(dialogLayout)
            .setPositiveButton("確定") { _, _ ->
                val color = android.graphics.Color.rgb(r, g, b) or 0xFF000000.toInt()
                onColorPicked(color)
            }
            .setNegativeButton("取消", null)
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
    // 自訂詞彙卡片
    // ─────────────────────────────────────────────────────────

    private fun buildUserDictCard(): LinearLayout {
        val card = simpleCard("自訂詞彙")
        val userDict = com.snowrealm.yukiboard.input.dictionary.UserDictionary(this)

        // 新增按鈕
        val addBtn = android.widget.Button(this).apply {
            text = "+ 新增詞彙"
            setTextColor(0xFF7FD8FF.toInt())
            setBackgroundColor(0xFF1A3A5C.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }

        // 詞彙列表容器
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun refreshList() {
            listContainer.removeAllViews()
            val manualWords = userDict.getAllManual()
            if (manualWords.isEmpty()) {
                listContainer.addView(android.widget.TextView(this).apply {
                    text = "尚無自訂詞彙"
                    setTextColor(0x88FFFFFF.toInt())
                    textSize = 13f
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                })
            } else {
                for ((word, bopo) in manualWords) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(dp(4), dp(6), dp(4), dp(6))
                    }
                    row.addView(android.widget.TextView(this).apply {
                        text = "$word  $bopo"
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    val delBtn = android.widget.TextView(this).apply {
                        text = "\u2715"
                        setTextColor(0xFFFF6B6B.toInt())
                        textSize = 16f
                        setPadding(dp(8), 0, dp(4), 0)
                    }
                    delBtn.setOnClickListener {
                        userDict.removeManual(word)
                        refreshList()
                    }
                    row.addView(delBtn)
                    listContainer.addView(row)
                }
            }
        }

        addBtn.setOnClickListener {
            val dialogView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(8))
            }
            val wordInput = android.widget.EditText(this).apply {
                hint = "詞彙（例如：迷客夏）"
                setHintTextColor(0x88FFFFFF.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF1A3A5C.toInt())
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val bopoInput = android.widget.EditText(this).apply {
                hint = "注音（例如：ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ）"
                setHintTextColor(0x88FFFFFF.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF1A3A5C.toInt())
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            val hint = android.widget.TextView(this).apply {
                text = "注音各音節間用空格分隔"
                setTextColor(0x88FFFFFF.toInt())
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            dialogView.addView(wordInput)
            dialogView.addView(bopoInput)
            dialogView.addView(hint)

            android.app.AlertDialog.Builder(this)
                .setTitle("新增自訂詞彙")
                .setView(dialogView)
                .setPositiveButton("新增") { _, _ ->
                    val word = wordInput.text.toString().trim()
                    val bopo = bopoInput.text.toString().trim()
                    if (word.isNotEmpty() && bopo.isNotEmpty()) {
                        userDict.addManual(word, bopo)
                        refreshList()
                        android.widget.Toast.makeText(this, "已新增「$word」", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(this, "詞彙和注音不能空白", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        card.addView(addBtn)
        card.addView(listContainer)
        refreshList()
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

    /** 語音引擎選擇器：Android 內建 / OpenAI Whisper */
    private fun speechEngineSelector(): LinearLayout {
        val options = arrayOf("Android 內建", "OpenAI Whisper")
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            gravity = android.view.Gravity.CENTER_VERTICAL

            val textContainer = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textContainer.addView(TextView(this@SettingsActivity).apply {
                text = "語音辨識引擎"
                textSize = 14f
                setTextColor(0xFFE8F4FF.toInt())
            })
            textContainer.addView(TextView(this@SettingsActivity).apply {
                text = "Whisper 需要 OpenAI API Key"
                textSize = 11f
                setTextColor(0xFF6688AA.toInt())
            })
            addView(textContainer)

            val btnLabel = TextView(this@SettingsActivity).apply {
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
                        .setTitle("選擇語音辨識引擎")
                        .setSingleChoiceItems(options, prefs.speechEngineType) { dialog, which ->
                            prefs.speechEngineType = which
                            text = options[which]
                            dialog.dismiss()
                        }
                        .show()
                }
            }
            addView(btnLabel)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        stopVideoPreview()
        val bgDir = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getBackgroundDir(this)
        bgDir.listFiles()?.forEach { it.delete() }

        val ext = when (requestCode) {
            REQUEST_PICK_BG_IMAGE -> {
                val mime = contentResolver.getType(uri) ?: "image/png"
                when {
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                    mime.contains("webp") -> "webp"
                    else -> "png"
                }
            }
            REQUEST_PICK_BG_VIDEO -> {
                val mime = contentResolver.getType(uri) ?: "video/mp4"
                when {
                    mime.contains("webm") -> "webm"
                    mime.contains("3gp") -> "3gp"
                    else -> "mp4"
                }
            }
            else -> return
        }

        val dest = java.io.File(bgDir, "keyboard_bg.$ext")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            prefs.useCustomBackground = true
            refreshBgPreview()
            Toast.makeText(this, "背景已設定，重新開啟鍵盤生效", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "背景設定失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        stopVideoPreview()
        super.onDestroy()
        activityScope.cancel()
    }
}

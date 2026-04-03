package com.snowrealm.yukiboard.ui.settings

import android.content.Intent
import android.content.res.Configuration
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
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, HelpActivity::class.java))
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
            inputChildren += switchRow("自動更正", "英文自動更正", prefs.autoCorrect) { prefs.autoCorrect = it }
            inputChildren += switchRow("Snippet 展開", "開發者模式：縮寫 + Tab 展開", prefs.snippetEnabled) { prefs.snippetEnabled = it }
            inputChildren += switchRow("私房話模式", "AES 加密訊息", prefs.secretMessageMode) { prefs.secretMessageMode = it }
            inputChildren += switchRow("語氣偵測", "偵測打字語氣", prefs.toneDetection) { prefs.toneDetection = it }
            inputChildren += switchRow("懸浮面板", "啟用懸浮面板功能", prefs.floatingPanelEnabled) { prefs.floatingPanelEnabled = it }
            inputChildren += switchRow("遠端鍵盤", "藍牙/WiFi 遠端控制", prefs.remoteKeyboardEnabled) { prefs.remoteKeyboardEnabled = it }
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

    // 預覽用的 ImageView（需在 onActivityResult 更新）
    private var bgPreviewImage: android.widget.ImageView? = null
    private var bgPreviewOverlay: View? = null
    private var bgPreviewLabel: TextView? = null

    private fun buildBackgroundCard(): LinearLayout {
        val card = simpleCard("鍵盤背景 / Background")

        // ── 預覽區 ──
        val previewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(140)
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
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bgPreviewImage = previewImg
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
            val dir = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getBackgroundDir(this)
            dir.listFiles()?.forEach { it.delete() }
            prefs.useCustomBackground = false
            bgPreviewImage?.setImageBitmap(null)
            bgPreviewLabel?.visibility = View.VISIBLE
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
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 字體顏色 ──
        card.addView(buildKeyLabelColorPicker())

        return card
    }

    private fun refreshBgPreview() {
        val savedBg = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getSavedBackground(this)
        if (savedBg != null && !com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.isVideoFile(savedBg)) {
            // 圖片預覽
            CoroutineScope(Dispatchers.IO).launch {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = android.graphics.BitmapFactory.decodeFile(savedBg.absolutePath, opts)
                withContext(Dispatchers.Main) {
                    bgPreviewImage?.setImageBitmap(bmp)
                    bgPreviewLabel?.visibility = View.GONE
                }
            }
        } else if (savedBg != null) {
            // 影片預覽：取第一幀
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(savedBg.absolutePath)
                    val frame = retriever.getFrameAtTime(0)
                    retriever.release()
                    withContext(Dispatchers.Main) {
                        bgPreviewImage?.setImageBitmap(frame)
                        bgPreviewLabel?.text = "🎬 影片背景"
                        bgPreviewLabel?.visibility = View.VISIBLE
                        bgPreviewLabel?.setBackgroundColor(0x55000000)
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        bgPreviewLabel?.text = "🎬 影片背景（無法預覽）"
                        bgPreviewLabel?.visibility = View.VISIBLE
                    }
                }
            }
        } else {
            bgPreviewImage?.setImageBitmap(null)
            bgPreviewLabel?.text = "尚未設定背景"
            bgPreviewLabel?.visibility = View.VISIBLE
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
                Toast.makeText(this, if (color == 0) "字體顏色跟隨主題" else "已更改字體顏色", Toast.LENGTH_SHORT).show()
            }

            colorRow.addView(wrapper)
        }

        scrollH.addView(colorRow)
        container.addView(scrollH)

        return container
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

        langs.forEach { (id, label, lang) ->
            group.addView(RadioButton(this).apply {
                this.id = id
                text = label
                setTextColor(0xFFB0D4FF.toInt())
                isChecked = currentLang == lang
            })
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            langs.firstOrNull { it.first == checkedId }?.let { (_, _, lang) ->
                val locale = Locale(lang)
                Locale.setDefault(locale)
                val config = Configuration(resources.configuration)
                config.setLocale(locale)
                @Suppress("DEPRECATION")
                resources.updateConfiguration(config, resources.displayMetrics)
                recreate()
            }
        }

        card.addView(group)
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return

        val uri = data.data!!
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
}

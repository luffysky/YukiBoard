package com.snowrealm.yukiboard.ui.settings

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
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
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }

        // ── 標題 ──
        root.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 24f
            setTextColor(0xFFE8F4FF.toInt())
            setPadding(0, 0, 0, dp(24))
        })

        // ══════════════════════════════════════════════════════════════
        // 感測器功能
        // ══════════════════════════════════════════════════════════════

        root.addView(sectionTitle("感測器 / Sensors"))

        // 搖手機復原
        root.addView(switchRow("搖手機復原 / Shake to Undo",
            "搖晃手機觸發復原（Ctrl+Z）",
            prefs.shakeToUndoEnabled
        ) { prefs.shakeToUndoEnabled = it })

        // 陀螺儀游標
        root.addView(switchRow("陀螺儀游標 / Gyro Cursor",
            "長按空白鍵 + 傾斜手機控制游標",
            prefs.gyroscopeCursorEnabled
        ) { prefs.gyroscopeCursorEnabled = it })

        // 陀螺儀靈敏度
        root.addView(TextView(this).apply {
            text = "陀螺儀靈敏度 / Gyro Sensitivity"
            textSize = 13f
            setTextColor(0xFFB0D4FF.toInt())
            setPadding(dp(8), dp(8), 0, dp(4))
        })

        val sensitivityLabel = TextView(this).apply {
            text = "%.1f".format(prefs.gyroscopeSensitivity)
            textSize = 13f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(8), 0, 0, dp(8))
        }

        root.addView(SeekBar(this).apply {
            max = 25  // 0.5 ~ 3.0 → 0~25 steps (step = 0.1)
            progress = ((prefs.gyroscopeSensitivity - 0.5f) * 10).toInt()
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = 0.5f + progress * 0.1f
                    prefs.gyroscopeSensitivity = value
                    sensitivityLabel.text = "%.1f".format(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        root.addView(sensitivityLabel)

        // App 自動偵測
        root.addView(switchRow("App 自動切換模式 / Auto Mode Switch",
            "偵測前景 App 自動切換設計者/開發者模式",
            prefs.appAutoDetectEnabled
        ) { prefs.appAutoDetectEnabled = it })

        // 震動回饋
        root.addView(switchRow("震動回饋 / Haptic Feedback",
            "按鍵和模式切換時的震動回饋",
            prefs.hapticFeedbackEnabled
        ) { prefs.hapticFeedbackEnabled = it })

        // Snippet
        root.addView(switchRow("Snippet 展開 / Snippets",
            "開發者模式：輸入縮寫 + Tab 自動展開",
            prefs.snippetEnabled
        ) { prefs.snippetEnabled = it })

        root.addView(divider())

        // ══════════════════════════════════════════════════════════════
        // 主題切換
        // ══════════════════════════════════════════════════════════════

        root.addView(sectionTitle("主題 / Theme"))

        val themeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(8), dp(4), 0, dp(16))
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

        root.addView(themeGroup)

        // ── 自訂背景 ──
        root.addView(sectionTitle("鍵盤背景 / Keyboard Background"))

        val bgLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), dp(16))
        }

        val pickImageBtn = Button(this).apply {
            text = "選圖片"
            setTextColor(0xFFE8F4FF.toInt())
            background = GradientDrawable().apply {
                setColor(0xFF2A3A5C.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, REQUEST_PICK_BG_IMAGE)
            }
        }
        bgLayout.addView(pickImageBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(4)
        })

        val pickVideoBtn = Button(this).apply {
            text = "選影片"
            setTextColor(0xFFE8F4FF.toInt())
            background = GradientDrawable().apply {
                setColor(0xFF2A3A5C.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "video/*"
                }
                startActivityForResult(intent, REQUEST_PICK_BG_VIDEO)
            }
        }
        bgLayout.addView(pickVideoBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })

        val clearBgBtn = Button(this).apply {
            text = "清除"
            setTextColor(0xFFFF6B6B.toInt())
            background = GradientDrawable().apply {
                setColor(0xFF1E2D4A.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener {
                val dir = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getBackgroundDir(this@SettingsActivity)
                dir.listFiles()?.forEach { it.delete() }
                android.widget.Toast.makeText(this@SettingsActivity, "已清除背景", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        bgLayout.addView(clearBgBtn, LinearLayout.LayoutParams(0, dp(44), 0.7f).apply {
            marginStart = dp(4)
        })

        root.addView(bgLayout)

        // ── 深色/淺色模式 ──
        root.addView(sectionTitle("外觀模式 / Appearance"))

        val modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(8), dp(4), 0, dp(16))
        }

        val currentMode = AppCompatDelegate.getDefaultNightMode()

        val modes = listOf(
            Triple(1, "跟隨系統 / Follow System", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
            Triple(2, "深色 / Dark", AppCompatDelegate.MODE_NIGHT_YES),
            Triple(3, "淺色 / Light", AppCompatDelegate.MODE_NIGHT_NO)
        )

        modes.forEach { (id, label, mode) ->
            modeGroup.addView(RadioButton(this).apply {
                this.id = id
                text = label
                setTextColor(0xFFB0D4FF.toInt())
                isChecked = currentMode == mode
            })
        }

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            modes.firstOrNull { it.first == checkedId }?.let {
                AppCompatDelegate.setDefaultNightMode(it.third)
            }
        }

        root.addView(modeGroup)

        // ── 語言設定 ──
        root.addView(sectionTitle("語言 / Language"))

        val langGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(8), dp(4), 0, dp(16))
        }

        val currentLang = resources.configuration.locales[0].language

        val langs = listOf(
            Triple(10, "繁體中文", "zh"),
            Triple(11, "English", "en")
        )

        langs.forEach { (id, label, lang) ->
            langGroup.addView(RadioButton(this).apply {
                this.id = id
                text = label
                setTextColor(0xFFB0D4FF.toInt())
                isChecked = currentLang == lang
            })
        }

        langGroup.setOnCheckedChangeListener { _, checkedId ->
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

        root.addView(langGroup)

        root.addView(divider())

        // ══════════════════════════════════════════════════════════════
        // AI / Claw
        // ══════════════════════════════════════════════════════════════

        root.addView(sectionTitle("AI / Claw"))

        root.addView(Button(this).apply {
            text = "AI 設定"
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
            ).apply { setMargins(dp(8), dp(8), 0, dp(16)) }
            layoutParams = lp
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, AISettingsActivity::class.java))
            }
        })

        scrollView.addView(root)
        setContentView(scrollView)
    }

    // ── UI 工具 ──

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(0xFF7FD8FF.toInt())
        setPadding(0, dp(8), 0, dp(4))
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
            setPadding(dp(8), dp(8), dp(8), dp(8))

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

    private fun divider(): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        ).apply { setMargins(0, dp(12), 0, dp(4)) }
        setBackgroundColor(0xFF2A3555.toInt())
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return

        val uri = data.data!!
        val bgDir = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getBackgroundDir(this)
        // 清除舊背景
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
            android.widget.Toast.makeText(this, "背景已設定，重新開啟鍵盤生效", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "背景設定失敗: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

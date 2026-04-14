package com.snowrealm.yukiboard.ui.settings.section

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 動畫特效 section（流光 / 角色 / VRM / 粒子 / 震動 / 音效 / 動畫主題 / 音色 / 音量 / 音階表）。
 *
 * 從 SettingsActivity section 4（原 lines 337-509）抽出。
 */
class AnimationSettingsSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String
) {

    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("動畫特效"), null, null, children) {
            children += SettingsUiHelper.switchRow(context, tr("流光動畫"), tr("OpenGL ES 流光覆蓋效果"), prefs.flowLightEnabled) { prefs.flowLightEnabled = it }
            children += SettingsUiHelper.switchRow(context, tr("雪凜角色"), tr("顯示雪凜浮動角色（Lottie / VRM）"), prefs.characterAnimEnabled) { prefs.characterAnimEnabled = it }
            children += SettingsUiHelper.switchRow(context, tr("VRM 3D 角色"), tr("3D VRM 模型取代 Lottie 角色"), prefs.vrmCharacterEnabled) { prefs.vrmCharacterEnabled = it }
            children += SettingsUiHelper.switchRow(context, tr("按鍵粒子效果"), tr("打字時的粒子動畫"), prefs.keyParticleEffect) { prefs.keyParticleEffect = it }
            children += SettingsUiHelper.switchRow(context, tr("按鍵震動"), tr("按鍵時的觸覺回饋"), prefs.keyPressVibration) { prefs.keyPressVibration = it }
            children += SettingsUiHelper.switchRow(context, tr("按鍵音效"), tr("按鍵時的聲音回饋"), prefs.keySoundEnabled) { prefs.keySoundEnabled = it }

            children += buildAnimThemeSelector(context)
            children += buildSoundStyleSelector(context)
            children += buildVolumeSlider(context)
            children += buildNoteMap(context)
        }
    }

    private fun buildAnimThemeSelector(context: Context): LinearLayout {
        val options = arrayOf("無", "星空 Starfield", "海洋 Ocean", "餘燼 Ember", "矩陣 Matrix", "冰霜 Frost", "熔岩 Lava", "彩虹 Rainbow", "雷電 Thunder")
        val values = arrayOf("none", "starfield", "ocean", "ember", "matrix", "frost", "lava", "rainbow", "thunder")
        return selectorRow(context, tr("動畫主題"), options, values, prefs.toolbarAnimStyle) { prefs.toolbarAnimStyle = it }
    }

    private fun buildSoundStyleSelector(context: Context): LinearLayout {
        val options = arrayOf("鋼琴 (Piano)", "木琴 (Marimba)", "柔和 (Soft)", "吉他 (Guitar)", "音樂盒 (Music Box)", "豎琴 (Harp)", "風琴 (Organ)", "鼓組 (Drum)", "電子鼓 (E-Drum)", "Beatbox")
        val values = arrayOf("piano", "marimba", "soft", "guitar", "music_box", "harp", "organ", "drum", "edrum", "beatbox")
        return selectorRow(context, tr("音色風格"), options, values, prefs.keySoundStyle) { prefs.keySoundStyle = it }
    }

    private fun selectorRow(
        context: Context,
        title: String,
        options: Array<String>,
        values: Array<String>,
        currentValue: String,
        onSelected: (String) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 8), 0, dp(context, 8))
        }
        row.addView(TextView(context).apply {
            text = title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val btn = TextView(context).apply {
            val curIdx = values.indexOf(currentValue).coerceAtLeast(0)
            text = options[curIdx]
            setTextColor(0xFF90CAF9.toInt())
            textSize = 14f
            setOnClickListener { v ->
                val cur = values.indexOf(currentValue).coerceAtLeast(0)
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setSingleChoiceItems(options, cur) { dialog, which ->
                        onSelected(values[which])
                        (v as TextView).text = options[which]
                        dialog.dismiss()
                    }
                    .setNegativeButton(tr("取消"), null)
                    .show()
            }
        }
        row.addView(btn)
        return row
    }

    private fun buildVolumeSlider(context: Context): LinearLayout {
        val volRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 4), 0, dp(context, 8))
        }
        val volLabel = TextView(context).apply {
            text = tr("音量：${(prefs.keySoundVolume * 100).toInt()}%")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            tag = "volLabel"
        }
        volRow.addView(volLabel)
        volRow.addView(SeekBar(context).apply {
            max = 100
            progress = (prefs.keySoundVolume * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        prefs.keySoundVolume = p / 100f
                        volLabel.text = tr("音量：${p}%")
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        return volRow
    }

    private fun buildNoteMap(context: Context): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 4), 0, dp(context, 8))
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            setBackgroundColor(0xFF1E1E2E.toInt())
        }
        val toggle = TextView(context).apply {
            text = tr("\u25B6 音階對照表")
            setTextColor(0xFF90CAF9.toInt())
            textSize = 14f
            setPadding(0, dp(context, 4), 0, dp(context, 4))
            setOnClickListener {
                if (content.visibility == View.GONE) {
                    content.visibility = View.VISIBLE
                    (it as TextView).text = tr("\u25BC 音階對照表")
                } else {
                    content.visibility = View.GONE
                    (it as TextView).text = tr("\u25B6 音階對照表")
                }
            }
        }
        val noteLines = listOf(
            "\u2500\u2500 數字列（高音）\u2500\u2500",
            "1\u2192G5  2\u2192G#5  3\u2192A5  4\u2192A#5  5\u2192B5",
            "6\u2192C6  7\u2192C#6  8\u2192D6  9\u2192D#6  0\u2192E6",
            "",
            "\u2500\u2500 Q 列（中高音）\u2500\u2500",
            "Q\u2192A4  W\u2192A#4  E\u2192B4  R\u2192C5  T\u2192C#5",
            "Y\u2192D5  U\u2192D#5  I\u2192E5  O\u2192F5  P\u2192F#5",
            "",
            "\u2500\u2500 A 列（中低音）\u2500\u2500",
            "A\u2192C4  S\u2192C#4  D\u2192D4  F\u2192D#4  G\u2192E4",
            "H\u2192F4  J\u2192F#4  K\u2192G4  L\u2192G#4",
            "",
            "\u2500\u2500 Z 列（低音）\u2500\u2500",
            "Z\u2192F3  X\u2192F#3  C\u2192G3  V\u2192G#3",
            "B\u2192A3  N\u2192A#3  M\u2192B3",
            "",
            "\u2500\u2500 功能鍵 \u2500\u2500",
            "空白\u2192C4  \u232B\u2192D#3  換行\u2192E3",
            "",
            "完整 F3\u2192E6（3 個八度\u30FB36 鍵不重複）",
            "\u266F = 黑鍵（半音）  其餘 = 白鍵"
        )
        noteLines.forEach { line ->
            content.addView(TextView(context).apply {
                text = line
                setTextColor(if (line.startsWith("\u2500") || line.startsWith("\u266F")) 0xFFFFD54F.toInt() else 0xFFCCCCCC.toInt())
                textSize = if (line.startsWith("\u2500")) 13f else 12f
                typeface = Typeface.MONOSPACE
                setPadding(0, if (line.isEmpty()) dp(context, 2) else dp(context, 1), 0, dp(context, 1))
            })
        }
        container.addView(toggle)
        container.addView(content)
        return container
    }
}

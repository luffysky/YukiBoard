package com.snowrealm.yukiboard.ui.settings

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * 完整輸入法測試頁面。
 *
 * 提供多種輸入框供使用者測試 YukiBoard 各功能：
 * - 一般文字輸入（中/英混打）
 * - 密碼欄位
 * - Email 欄位
 * - 網址欄位
 * - 數字欄位
 * - 多行文字（長文輸入）
 * - 搜尋框
 * - 電話號碼
 */
class InputTestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF1A2035.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(120))
        }

        // 標題
        root.addView(TextView(this).apply {
            text = "YukiBoard 輸入法測試"
            textSize = 22f
            setTextColor(0xFFE8F4FF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, dp(8), dp(4))
        })
        root.addView(TextView(this).apply {
            text = "在下方各欄位測試輸入功能，驗證中英切換、候選字、滑動輸入、語音等。"
            textSize = 13f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(8), 0, dp(8), dp(16))
        })

        // ── 測試欄位 ──

        root.addView(sectionTitle("一般文字"))
        root.addView(testField(
            "中英混打測試",
            "試試打中文、English、混打 hello 你好",
            InputType.TYPE_CLASS_TEXT
        ))

        root.addView(sectionTitle("滑動輸入"))
        root.addView(testField(
            "英文滑動",
            "在鍵盤上滑動拼出英文單字",
            InputType.TYPE_CLASS_TEXT
        ))

        root.addView(sectionTitle("語音輸入"))
        root.addView(testField(
            "語音辨識",
            "點工具列麥克風圖示開始語音輸入",
            InputType.TYPE_CLASS_TEXT
        ))

        root.addView(sectionTitle("自動更正 / 候選字"))
        root.addView(testField(
            "打錯字測試",
            "故意打錯看候選列有沒有修正建議（例：teh → the）",
            InputType.TYPE_CLASS_TEXT
        ))

        root.addView(sectionTitle("密碼欄位"))
        root.addView(testField(
            "密碼",
            "測試密碼模式鍵盤",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        ))

        root.addView(sectionTitle("Email"))
        root.addView(testField(
            "Email 地址",
            "test@example.com",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        ))

        root.addView(sectionTitle("網址"))
        root.addView(testField(
            "URL",
            "https://snowrealm.com",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        ))

        root.addView(sectionTitle("數字"))
        root.addView(testField(
            "數字輸入",
            "12345",
            InputType.TYPE_CLASS_NUMBER
        ))

        root.addView(sectionTitle("電話"))
        root.addView(testField(
            "電話號碼",
            "0912-345-678",
            InputType.TYPE_CLASS_PHONE
        ))

        root.addView(sectionTitle("搜尋"))
        root.addView(testField(
            "搜尋框",
            "測試搜尋鍵盤（Enter 變搜尋）",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER,
            imeAction = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        ))

        root.addView(sectionTitle("多行長文"))
        root.addView(testField(
            "多行文字",
            "這裡可以打很多行...\n測試 Enter 換行、長文滾動",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            minLines = 5
        ))

        root.addView(sectionTitle("Snippet 測試（開發者模式）"))
        root.addView(testField(
            "Snippet 展開",
            "切換到開發者模式，輸入 fn 按 Tab 試試",
            InputType.TYPE_CLASS_TEXT
        ))

        root.addView(sectionTitle("Emoji / 顏文字"))
        root.addView(testField(
            "Emoji 測試",
            "點工具列開啟 Emoji / 顏文字面板",
            InputType.TYPE_CLASS_TEXT
        ))

        // 清除全部按鈕
        root.addView(Button(this).apply {
            text = "清除所有欄位"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            background = GradientDrawable().apply {
                setColor(0xFF8B0000.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(4), dp(24), dp(4), dp(8)) }
            setOnClickListener {
                clearAllFields(root)
            }
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(0xFF9ECFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(16), dp(8), dp(4))
        }
    }

    private fun testField(
        label: String,
        hint: String,
        inputType: Int,
        minLines: Int = 1,
        imeAction: Int = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))

            addView(TextView(this@InputTestActivity).apply {
                this.text = label
                textSize = 12f
                setTextColor(0xFF6688AA.toInt())
                setPadding(dp(4), 0, 0, dp(2))
            })

            addView(EditText(this@InputTestActivity).apply {
                this.hint = hint
                this.inputType = inputType
                this.minLines = minLines
                if (minLines > 1) {
                    gravity = Gravity.TOP
                    isSingleLine = false
                }
                this.imeOptions = imeAction
                textSize = 15f
                setTextColor(0xFFE8F4FF.toInt())
                setHintTextColor(0xFF4A5A77.toInt())
                background = GradientDrawable().apply {
                    setColor(0xFF0D1526.toInt())
                    cornerRadius = dp(8).toFloat()
                    setStroke(1, 0xFF2A3A5A.toInt())
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp(4)) }
            })
        }
    }

    private fun clearAllFields(root: LinearLayout) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is LinearLayout) {
                for (j in 0 until child.childCount) {
                    val inner = child.getChildAt(j)
                    if (inner is EditText) {
                        inner.text.clear()
                    }
                }
            }
        }
        Toast.makeText(this, "已清除所有欄位", Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

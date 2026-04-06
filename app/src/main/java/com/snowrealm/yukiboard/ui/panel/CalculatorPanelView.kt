package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 計算機面板 — 基礎四則 + 百分比 + 插入結果到輸入框。
 *
 * 佈局：頂部顯示欄 + 5×4 按鈕網格。
 * 長按 = 鍵 → 插入結果到輸入框。
 */
class CalculatorPanelView(context: Context) : View(context) {

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 計算結果插入到輸入框的回呼 */
    var onInsertResult: ((String) -> Unit)? = null

    private val density = resources.displayMetrics.density

    // 計算狀態
    private var display = "0"
    private var operand1: Double? = null
    private var operator: String? = null
    private var waitingForOperand2 = false
    private var hasDecimal = false

    // 按鈕定義
    private data class CalcButton(
        val label: String,
        val type: ButtonType,
        val rect: RectF = RectF()
    )

    private enum class ButtonType { NUMBER, OPERATOR, FUNCTION, EQUALS, INSERT }

    private val buttons = listOf(
        // Row 1
        CalcButton("C", ButtonType.FUNCTION),
        CalcButton("±", ButtonType.FUNCTION),
        CalcButton("%", ButtonType.FUNCTION),
        CalcButton("÷", ButtonType.OPERATOR),
        // Row 2
        CalcButton("7", ButtonType.NUMBER),
        CalcButton("8", ButtonType.NUMBER),
        CalcButton("9", ButtonType.NUMBER),
        CalcButton("×", ButtonType.OPERATOR),
        // Row 3
        CalcButton("4", ButtonType.NUMBER),
        CalcButton("5", ButtonType.NUMBER),
        CalcButton("6", ButtonType.NUMBER),
        CalcButton("−", ButtonType.OPERATOR),
        // Row 4
        CalcButton("1", ButtonType.NUMBER),
        CalcButton("2", ButtonType.NUMBER),
        CalcButton("3", ButtonType.NUMBER),
        CalcButton("+", ButtonType.OPERATOR),
        // Row 5
        CalcButton("0", ButtonType.NUMBER),
        CalcButton(".", ButtonType.NUMBER),
        CalcButton("↩", ButtonType.INSERT),
        CalcButton("=", ButtonType.EQUALS)
    )

    private val cols = 4
    private val rows = 5

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val displayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }

    private var pressedIndex = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let { if (it > 0) it else resources.displayMetrics.widthPixels }
        setMeasuredDimension(w, (220 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val displayH = 40f * density
        val gap = 3f * density
        val btnAreaH = h - displayH
        val btnW = (w - gap * (cols + 1)) / cols
        val btnH = (btnAreaH - gap * (rows + 1)) / rows

        // 背景
        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 顯示欄
        val displayRect = RectF(gap, gap, w - gap, displayH - gap)
        bgPaint.color = (theme.backgroundColorInt and 0x00FFFFFF) or 0xDD000000.toInt()
        canvas.drawRoundRect(displayRect, 8f * density, 8f * density, bgPaint)

        displayPaint.color = theme.keyLabelColorInt
        displayPaint.textSize = 22f * density
        val displayY = displayRect.centerY() + displayPaint.textSize * 0.35f
        canvas.drawText(display, w - gap * 3, displayY, displayPaint)

        // 小字顯示運算式
        if (operand1 != null && operator != null) {
            displayPaint.textSize = 12f * density
            displayPaint.alpha = 150
            val expr = formatNumber(operand1!!) + " $operator"
            canvas.drawText(expr, w - gap * 3, displayRect.top + 14f * density, displayPaint)
            displayPaint.alpha = 255
        }

        // 按鈕
        textPaint.textSize = 18f * density
        buttons.forEachIndexed { i, btn ->
            val row = i / cols
            val col = i % cols
            val x = gap + col * (btnW + gap)
            val y = displayH + gap + row * (btnH + gap)
            btn.rect.set(x, y, x + btnW, y + btnH)

            // 按鈕底色
            val btnColor = when (btn.type) {
                ButtonType.OPERATOR, ButtonType.EQUALS -> 0xFFE8B84D.toInt()
                ButtonType.FUNCTION -> 0xFF555555.toInt()
                ButtonType.INSERT -> 0xFF4CAF50.toInt()
                ButtonType.NUMBER -> (theme.keyColorInt and 0x00FFFFFF) or (0xCC shl 24)
            }
            btnPaint.color = btnColor
            if (i == pressedIndex) btnPaint.alpha = 200
            canvas.drawRoundRect(btn.rect, 6f * density, 6f * density, btnPaint)
            btnPaint.alpha = 255

            // 文字
            textPaint.color = if (btn.type == ButtonType.OPERATOR || btn.type == ButtonType.EQUALS)
                0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
            if (btn.type == ButtonType.INSERT) textPaint.color = 0xFFFFFFFF.toInt()
            val textY = btn.rect.centerY() + textPaint.textSize * 0.35f
            canvas.drawText(btn.label, btn.rect.centerX(), textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = hitTest(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pressedIndex >= 0) {
                    handleButton(buttons[pressedIndex])
                    performClick()
                }
                pressedIndex = -1
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hitTest(x: Float, y: Float): Int {
        buttons.forEachIndexed { i, btn ->
            if (btn.rect.contains(x, y)) return i
        }
        return -1
    }

    private fun handleButton(btn: CalcButton) {
        when (btn.type) {
            ButtonType.NUMBER -> handleNumber(btn.label)
            ButtonType.OPERATOR -> handleOperator(btn.label)
            ButtonType.FUNCTION -> handleFunction(btn.label)
            ButtonType.EQUALS -> calculate()
            ButtonType.INSERT -> onInsertResult?.invoke(display)
        }
        invalidate()
    }

    private fun handleNumber(label: String) {
        if (label == ".") {
            if (hasDecimal) return
            hasDecimal = true
            if (waitingForOperand2) {
                display = "0."
                waitingForOperand2 = false
            } else {
                display += "."
            }
            return
        }

        if (waitingForOperand2 || display == "0") {
            display = label
            waitingForOperand2 = false
            hasDecimal = false
        } else {
            if (display.length < 15) display += label
        }
    }

    private fun handleOperator(op: String) {
        if (operand1 != null && !waitingForOperand2) {
            calculate()
        }
        operand1 = display.toDoubleOrNull()
        operator = op
        waitingForOperand2 = true
        hasDecimal = false
    }

    private fun handleFunction(label: String) {
        when (label) {
            "C" -> {
                display = "0"
                operand1 = null
                operator = null
                waitingForOperand2 = false
                hasDecimal = false
            }
            "±" -> {
                val v = display.toDoubleOrNull() ?: return
                display = formatNumber(-v)
            }
            "%" -> {
                val v = display.toDoubleOrNull() ?: return
                display = formatNumber(v / 100.0)
            }
        }
    }

    private fun calculate() {
        val op1 = operand1 ?: return
        val op2 = display.toDoubleOrNull() ?: return
        val op = operator ?: return

        val result = when (op) {
            "+" -> op1 + op2
            "−" -> op1 - op2
            "×" -> op1 * op2
            "÷" -> if (op2 != 0.0) op1 / op2 else Double.NaN
            else -> return
        }

        display = if (result.isNaN() || result.isInfinite()) "Error" else formatNumber(result)
        operand1 = null
        operator = null
        waitingForOperand2 = false
        hasDecimal = display.contains(".")
    }

    private fun formatNumber(v: Double): String {
        return if (v == v.toLong().toDouble() && !v.isInfinite() && !v.isNaN()) {
            v.toLong().toString()
        } else {
            val s = "%.10f".format(v).trimEnd('0').trimEnd('.')
            if (s.length > 15) "%.6E".format(v) else s
        }
    }
}

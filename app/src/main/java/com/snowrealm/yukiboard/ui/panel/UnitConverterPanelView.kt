package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 單位換算面板 — 長度/重量/溫度/面積 四大分類。
 *
 * 佈局：分類標籤列 + 數值顯示 + 單位選擇網格 + 數字鍵盤。
 * 點選單位切換「來源/目標」，結果即時顯示。
 */
class UnitConverterPanelView(context: Context) : View(context) {

    init { setWillNotDraw(false) }

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var onInsertResult: ((String) -> Unit)? = null

    private val density = resources.displayMetrics.density

    // 分類
    private enum class Category(val label: String) {
        LENGTH("長度"), WEIGHT("重量"), TEMPERATURE("溫度"), AREA("面積")
    }

    private var selectedCategory = Category.LENGTH

    // 單位定義 (id, display, toBase factor)  — base: m, kg, celsius, m²
    private data class UnitDef(val id: String, val display: String, val toBase: Double, val fromBase: Double = 1.0 / toBase)

    private val lengthUnits = listOf(
        UnitDef("mm", "mm", 0.001),
        UnitDef("cm", "cm", 0.01),
        UnitDef("m", "m", 1.0),
        UnitDef("km", "km", 1000.0),
        UnitDef("in", "in", 0.0254),
        UnitDef("ft", "ft", 0.3048),
        UnitDef("yd", "yd", 0.9144),
        UnitDef("mi", "mi", 1609.344)
    )

    private val weightUnits = listOf(
        UnitDef("mg", "mg", 0.000001),
        UnitDef("g", "g", 0.001),
        UnitDef("kg", "kg", 1.0),
        UnitDef("t", "t", 1000.0),
        UnitDef("oz", "oz", 0.0283495),
        UnitDef("lb", "lb", 0.453592),
        UnitDef("台斤", "台斤", 0.6),
        UnitDef("兩", "兩", 0.0375)
    )

    private val temperatureUnits = listOf(
        UnitDef("°C", "°C", 1.0),
        UnitDef("°F", "°F", 1.0),
        UnitDef("K", "K", 1.0)
    )

    private val areaUnits = listOf(
        UnitDef("cm²", "cm²", 0.0001),
        UnitDef("m²", "m²", 1.0),
        UnitDef("km²", "km²", 1_000_000.0),
        UnitDef("坪", "坪", 3.305785),
        UnitDef("ha", "ha", 10_000.0),
        UnitDef("ft²", "ft²", 0.092903),
        UnitDef("ac", "ac", 4046.86),
        UnitDef("甲", "甲", 9699.17)
    )

    private fun currentUnits(): List<UnitDef> = when (selectedCategory) {
        Category.LENGTH -> lengthUnits
        Category.WEIGHT -> weightUnits
        Category.TEMPERATURE -> temperatureUnits
        Category.AREA -> areaUnits
    }

    // 選擇狀態
    private var fromIndex = 0
    private var toIndex = 1
    private var inputValue = "1"

    // UI 區域
    private val categoryRects = mutableListOf<Pair<RectF, Category>>()
    private val unitRects = mutableListOf<Pair<RectF, Int>>()
    private val numpadRects = mutableListOf<Pair<RectF, String>>()
    private var insertRect = RectF()
    private var swapRect = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var pressedKey: String? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let { if (it > 0) it else resources.displayMetrics.widthPixels }
        setMeasuredDimension(w, (260 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 8f * density
        val catH = 28f * density
        val displayH = 44f * density
        val unitRowH = 28f * density

        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // ── 分類標籤列 ──
        categoryRects.clear()
        val catW = (w - pad * 2 - 4f * density * 3) / 4f
        var cx = pad
        Category.entries.forEach { cat ->
            val rect = RectF(cx, pad, cx + catW, pad + catH)
            categoryRects.add(rect to cat)
            val isActive = cat == selectedCategory
            bgPaint.color = if (isActive) 0xFF4A90E2.toInt() else (theme.keyColorInt and 0x00FFFFFF) or (0x80 shl 24)
            canvas.drawRoundRect(rect, 6f * density, 6f * density, bgPaint)
            textPaint.textSize = 11f * density
            textPaint.color = if (isActive) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
            canvas.drawText(cat.label, rect.centerX(), rect.centerY() + 4f * density, textPaint)
            cx += catW + 4f * density
        }

        // ── 數值顯示區 ──
        val dispTop = pad + catH + pad * 0.5f
        val units = currentUnits()
        val fromUnit = units.getOrNull(fromIndex) ?: units[0]
        val toUnit = units.getOrNull(toIndex) ?: units.getOrElse(1) { units[0] }
        val result = convert(inputValue.toDoubleOrNull() ?: 0.0, fromUnit, toUnit)

        // 上方：來源
        val srcRect = RectF(pad, dispTop, w * 0.75f, dispTop + displayH * 0.45f)
        textPaint.textSize = 13f * density
        textPaint.color = theme.keyLabelColorInt
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("$inputValue ${fromUnit.display}", pad + 4f * density, srcRect.centerY() + 4f * density, textPaint)

        // 交換按鈕
        swapRect.set(w * 0.78f, dispTop, w - pad, dispTop + displayH * 0.45f)
        bgPaint.color = (theme.keyColorInt and 0x00FFFFFF) or (0x80 shl 24)
        canvas.drawRoundRect(swapRect, 4f * density, 4f * density, bgPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 14f * density
        textPaint.color = 0xFF7FD8FF.toInt()
        canvas.drawText("⇅", swapRect.centerX(), swapRect.centerY() + 5f * density, textPaint)

        // 下方：結果
        val dstRect = RectF(pad, dispTop + displayH * 0.5f, w - pad, dispTop + displayH)
        textPaint.textSize = 16f * density
        textPaint.color = 0xFF4ADE80.toInt()
        textPaint.textAlign = Paint.Align.LEFT
        val resultStr = formatResult(result)
        canvas.drawText("= $resultStr ${toUnit.display}", pad + 4f * density, dstRect.centerY() + 6f * density, textPaint)

        textPaint.textAlign = Paint.Align.CENTER

        // ── 單位選擇列（兩行） ──
        val unitTop = dispTop + displayH + pad * 0.5f
        unitRects.clear()
        val cols = 4
        val unitGap = 3f * density
        val unitW = (w - pad * 2 - unitGap * (cols - 1)) / cols
        units.forEachIndexed { i, unit ->
            val row = i / cols
            val col = i % cols
            val ux = pad + col * (unitW + unitGap)
            val uy = unitTop + row * (unitRowH + unitGap)
            val rect = RectF(ux, uy, ux + unitW, uy + unitRowH)
            unitRects.add(rect to i)

            val isFrom = i == fromIndex
            val isTo = i == toIndex
            bgPaint.color = when {
                isFrom -> 0xFF4A90E2.toInt()
                isTo -> 0xFF4CAF50.toInt()
                else -> (theme.keyColorInt and 0x00FFFFFF) or (0x80 shl 24)
            }
            canvas.drawRoundRect(rect, 4f * density, 4f * density, bgPaint)
            textPaint.textSize = 10f * density
            textPaint.color = if (isFrom || isTo) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
            canvas.drawText(unit.display, rect.centerX(), rect.centerY() + 3.5f * density, textPaint)
        }

        // ── 數字鍵盤（底部三行四列） ──
        val unitRows = (units.size + cols - 1) / cols
        val numTop = unitTop + unitRows * (unitRowH + unitGap) + pad * 0.5f
        val numH = (h - numTop - pad) / 3f - unitGap
        val numKeys = listOf(
            "7", "8", "9", "⌫",
            "4", "5", "6", ".",
            "1", "2", "3", "↩"
        )
        // Add "0" key spanning
        val numCols = 4
        numpadRects.clear()
        numKeys.forEachIndexed { i, key ->
            val row = i / numCols
            val col = i % numCols
            val nx = pad + col * (unitW + unitGap)
            val ny = numTop + row * (numH + unitGap)
            val rect = RectF(nx, ny, nx + unitW, ny + numH)
            numpadRects.add(rect to key)

            val isSpecial = key == "⌫" || key == "↩"
            bgPaint.color = when {
                key == "↩" -> 0xFF4CAF50.toInt()
                key == "⌫" -> 0xFF555555.toInt()
                key == pressedKey -> 0xFF4A90E2.toInt()
                else -> (theme.keyColorInt and 0x00FFFFFF) or (0xCC shl 24)
            }
            canvas.drawRoundRect(rect, 4f * density, 4f * density, bgPaint)
            textPaint.textSize = if (isSpecial) 14f * density else 16f * density
            textPaint.color = if (key == "↩") 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
            canvas.drawText(key, rect.centerX(), rect.centerY() + textPaint.textSize * 0.35f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 分類
                categoryRects.firstOrNull { it.first.contains(x, y) }?.let { (_, cat) ->
                    selectedCategory = cat
                    fromIndex = 0; toIndex = 1; inputValue = "1"
                    invalidate()
                    return true
                }
                // 單位
                unitRects.firstOrNull { it.first.contains(x, y) }?.let { (_, idx) ->
                    when {
                        idx == fromIndex -> { /* 再次點擊來源無動作 */ }
                        idx == toIndex -> { val tmp = fromIndex; fromIndex = toIndex; toIndex = tmp }
                        else -> toIndex = idx
                    }
                    invalidate()
                    return true
                }
                // 交換
                if (swapRect.contains(x, y)) {
                    val tmp = fromIndex; fromIndex = toIndex; toIndex = tmp
                    invalidate()
                    return true
                }
                // 數字鍵
                numpadRects.firstOrNull { it.first.contains(x, y) }?.let { (_, key) ->
                    pressedKey = key
                    invalidate()
                    return true
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                numpadRects.firstOrNull { it.first.contains(x, y) }?.let { (_, key) ->
                    handleNumKey(key)
                    performClick()
                }
                pressedKey = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleNumKey(key: String) {
        when (key) {
            "⌫" -> {
                if (inputValue.length > 1) inputValue = inputValue.dropLast(1)
                else inputValue = "0"
            }
            "." -> {
                if (!inputValue.contains(".")) inputValue += "."
            }
            "↩" -> {
                val units = currentUnits()
                val toUnit = units.getOrNull(toIndex) ?: return
                val fromUnit = units.getOrNull(fromIndex) ?: return
                val result = convert(inputValue.toDoubleOrNull() ?: 0.0, fromUnit, toUnit)
                onInsertResult?.invoke(formatResult(result))
            }
            else -> {
                if (inputValue == "0") inputValue = key
                else if (inputValue.length < 12) inputValue += key
            }
        }
    }

    private fun convert(value: Double, from: UnitDef, to: UnitDef): Double {
        // 溫度特殊處理
        if (selectedCategory == Category.TEMPERATURE) {
            val celsius = when (from.id) {
                "°C" -> value
                "°F" -> (value - 32.0) * 5.0 / 9.0
                "K" -> value - 273.15
                else -> value
            }
            return when (to.id) {
                "°C" -> celsius
                "°F" -> celsius * 9.0 / 5.0 + 32.0
                "K" -> celsius + 273.15
                else -> celsius
            }
        }
        // 線性單位：value * from.toBase / to.toBase
        val baseValue = value * from.toBase
        return baseValue / to.toBase
    }

    private fun formatResult(v: Double): String {
        if (v == 0.0) return "0"
        if (v.isNaN() || v.isInfinite()) return "Error"
        return if (v == v.toLong().toDouble() && kotlin.math.abs(v) < 1e15) {
            v.toLong().toString()
        } else {
            val s = "%.8f".format(v).trimEnd('0').trimEnd('.')
            if (s.length > 15) "%.4E".format(v) else s
        }
    }
}

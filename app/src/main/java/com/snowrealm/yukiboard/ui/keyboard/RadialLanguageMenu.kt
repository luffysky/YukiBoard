package com.snowrealm.yukiboard.ui.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.snowrealm.yukiboard.input.langpack.LanguagePack
import com.snowrealm.yukiboard.input.langpack.LanguagePackManager
import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 環形語言選單 — 長按「切換」鍵時彈出，8 個扇區對應 8 種輸入法 + Emoji。
 *
 * 設計：
 * - 8 扇區均分 360 度（每個 45 度）
 * - 從頂部開始順時針排列
 * - 選中高亮 + 當前語言指示
 * - 手指滑到扇區後放開即選中
 * - 0.2 秒 OvershootInterpolator 展開動畫
 */
class RadialLanguageMenu(
    context: Context,
    private val theme: KeyboardTheme,
    private val currentInputMode: InputMode,
    private val isEmojiActive: Boolean = false,
    private val onLanguageSelected: (LanguageOption) -> Unit
) : View(context) {

    /** 語言選項（含 Emoji 作為特殊鍵盤） */
    sealed class LanguageOption {
        data class Language(val mode: InputMode) : LanguageOption()
        object Emoji : LanguageOption()
    }

    private val density = context.resources.displayMetrics.density
    private val menuRadius = 110f * density
    private val innerRadius = 30f * density
    private val viewSize = (menuRadius * 2 + 50 * density).toInt()

    private var animScale = 0f
    private val expandAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 220
        interpolator = OvershootInterpolator(1.1f)
        addUpdateListener {
            animScale = it.animatedValue as Float
            invalidate()
        }
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 16f * density
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.backgroundColorInt
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
    }

    data class SectorItem(
        val option: LanguageOption,
        val icon: String,
        val label: String
    )

    /**
     * 動態建構語言選項：
     * - 自動（固定）
     * - 從 LanguagePackManager 已啟用的輸入法列表取得
     * - Emoji（固定）
     */
    private val items: List<SectorItem> = buildItems(context)

    private fun buildItems(ctx: Context): List<SectorItem> {
        val result = mutableListOf<SectorItem>()
        // 固定：自動模式
        result.add(SectorItem(LanguageOption.Language(InputMode.AUTO), "⚡", "自動"))

        // 從 LanguagePackManager 取得已啟用的輸入法
        val mgr = LanguagePackManager.getInstance(ctx)
        val enabled = mgr.getEnabledInputMethods()
        for (pack in enabled) {
            val mode = try {
                InputMode.valueOf(pack.inputModeName)
            } catch (_: Exception) { continue }
            result.add(SectorItem(LanguageOption.Language(mode), pack.icon, pack.displayName))
        }

        // 固定：Emoji
        result.add(SectorItem(LanguageOption.Emoji, "😀", "Emoji"))
        return result
    }

    private val sectorCount = items.size
    private val sweepAngle = 360f / sectorCount
    // 從頂部（-90度）開始順時針
    private val startAngles = FloatArray(sectorCount) { i -> -90f - sweepAngle / 2f + i * sweepAngle }

    private var hoveredIndex = -1
    private val cx get() = width / 2f
    private val cy get() = height / 2f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        expandAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        expandAnimator.cancel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewSize, viewSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (animScale <= 0f) return

        val save = canvas.save()
        canvas.scale(animScale, animScale, cx, cy)
        val alphaFactor = animScale.coerceIn(0f, 1f)

        // 半透明背景圓
        bgPaint.color = (theme.backgroundColorInt and 0x00FFFFFF) or
                ((0xDD * alphaFactor).toInt().coerceIn(0, 255) shl 24)
        canvas.drawCircle(cx, cy, menuRadius, bgPaint)

        val oval = RectF(cx - menuRadius, cy - menuRadius, cx + menuRadius, cy + menuRadius)

        items.forEachIndexed { i, item ->
            val isHovered = hoveredIndex == i
            val isCurrent = when (val opt = item.option) {
                is LanguageOption.Language -> opt.mode == currentInputMode && !isEmojiActive
                is LanguageOption.Emoji -> isEmojiActive
            }

            // 扇區填色
            sectorPaint.color = when {
                isHovered -> theme.candidateSelectedColorInt
                isCurrent -> (theme.candidateSelectedColorInt and 0x00FFFFFF) or 0x55000000
                else -> (theme.keyColorInt and 0x00FFFFFF) or 0x99000000.toInt()
            }
            sectorPaint.style = Paint.Style.FILL
            canvas.drawArc(oval, startAngles[i], sweepAngle, true, sectorPaint)

            // 分隔線
            val angle = Math.toRadians(startAngles[i].toDouble())
            canvas.drawLine(
                cx + innerRadius * cos(angle).toFloat(),
                cy + innerRadius * sin(angle).toFloat(),
                cx + menuRadius * cos(angle).toFloat(),
                cy + menuRadius * sin(angle).toFloat(),
                dividerPaint
            )

            // 圖示 + 標籤
            val midAngle = Math.toRadians((startAngles[i] + sweepAngle / 2).toDouble())
            val iconR = (innerRadius + menuRadius) * 0.52f
            val lx = cx + iconR * cos(midAngle).toFloat()
            val ly = cy + iconR * sin(midAngle).toFloat()

            iconPaint.color = when {
                isHovered -> theme.keyLabelColorInt
                isCurrent -> theme.candidateSelectedColorInt
                else -> theme.composingTextColorInt
            }
            canvas.drawText(item.icon, lx, ly - 2f * density, iconPaint)

            textPaint.color = when {
                isHovered -> theme.keyLabelColorInt
                isCurrent -> theme.candidateSelectedColorInt
                else -> theme.candidateTextColorInt
            }
            canvas.drawText(item.label, lx, ly + 12f * density, textPaint)
        }

        // 中心圓
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawCircle(cx, cy, innerRadius, bgPaint)
        textPaint.textSize = 8f * density
        textPaint.color = theme.composingTextColorInt
        canvas.drawText("語言", cx, cy + 3f * density, textPaint)
        textPaint.textSize = 10f * density

        canvas.restoreToCount(save)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.x - cx
        val dy = event.y - cy
        val dist = hypot(dx, dy)

        when (event.action) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                hoveredIndex = if (dist > innerRadius && dist < menuRadius + 20 * density) {
                    getSectorIndex(dx, dy)
                } else -1
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (hoveredIndex in items.indices) {
                    onLanguageSelected(items[hoveredIndex].option)
                }
                hoveredIndex = -1
            }
        }
        return true
    }

    private fun getSectorIndex(dx: Float, dy: Float): Int {
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        for (i in items.indices) {
            var start = startAngles[i]
            var end = start + sweepAngle

            // 正規化到 -180..180 區間進行比較
            fun normalize(a: Float): Float {
                var v = a
                while (v < -180) v += 360f
                while (v > 180) v -= 360f
                return v
            }

            val normAngle = normalize(angle)
            val normStart = normalize(start)
            val normEnd = normalize(end)

            if (normStart < normEnd) {
                if (normAngle >= normStart && normAngle < normEnd) return i
            } else {
                // 跨越 -180/180 邊界
                if (normAngle >= normStart || normAngle < normEnd) return i
            }
        }
        return -1
    }
}

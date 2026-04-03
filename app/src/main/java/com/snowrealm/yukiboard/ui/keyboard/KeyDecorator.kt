package com.snowrealm.yukiboard.ui.keyboard

import android.graphics.*
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 主題化按鍵裝飾器 — 液態玻璃底層 + 每主題獨特視覺風格。
 *
 * 在 KeyboardView.drawKey() 中，先畫按鍵底色，再呼叫 decorate() 疊加裝飾，最後畫文字。
 */
class KeyDecorator(private val density: Float) {

    private val decorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 預算好的隨機種子（每次 layout 重算，避免每幀閃爍）
    private var seedMap = mutableMapOf<Int, Long>()

    fun resetSeeds() { seedMap.clear() }

    /**
     * 在按鍵上繪製液態玻璃 + 主題裝飾。
     */
    fun decorate(
        canvas: Canvas,
        rect: RectF,
        theme: KeyboardTheme,
        keyIndex: Int,
        isSpecial: Boolean,
        isPressed: Boolean,
        cr: Float
    ) {
        val seed = seedMap.getOrPut(keyIndex) { Random.nextLong() }
        val rng = Random(seed)

        // ── 液態玻璃效果（所有主題共用）──
        drawLiquidGlass(canvas, rect, theme, isSpecial, isPressed, cr)

        // ── 主題專屬裝飾 ──
        when (theme.id) {
            "snow_dark" -> decorateSnowDark(canvas, rect, theme, isSpecial, cr, rng)
            "snow_light" -> decorateSnowLight(canvas, rect, theme, isSpecial, cr, rng)
            "deep_space" -> decorateDeepSpace(canvas, rect, theme, isSpecial, cr, rng)
            "sakura" -> decorateSakura(canvas, rect, theme, isSpecial, cr, rng)
            "aurora" -> decorateAurora(canvas, rect, theme, isSpecial, cr, rng)
            "cyberpunk" -> decorateCyberpunk(canvas, rect, theme, isSpecial, cr, rng)
            "matcha" -> decorateMatcha(canvas, rect, theme, isSpecial, cr, rng)
            "midnight_gold" -> decorateMidnightGold(canvas, rect, theme, isSpecial, cr, rng)
        }
    }

    // ══════════════════════════════════════════════════════════
    // 液態玻璃效果 — iOS-style liquid glass
    // ══════════════════════════════════════════════════════════

    private fun drawLiquidGlass(
        canvas: Canvas,
        rect: RectF,
        theme: KeyboardTheme,
        isSpecial: Boolean,
        isPressed: Boolean,
        cr: Float
    ) {
        val isDark = theme.isDark

        // 1) 上半部高光漸層（玻璃反射）
        val highlightAlpha = if (isPressed) 0x08 else if (isSpecial) 0x12 else 0x18
        val highlightColor = if (isDark) Color.WHITE else Color.WHITE
        glassPaint.shader = LinearGradient(
            rect.left, rect.top,
            rect.left, rect.top + rect.height() * 0.55f,
            (highlightColor and 0x00FFFFFF) or (highlightAlpha shl 24),
            (highlightColor and 0x00FFFFFF) or 0x00000000,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, glassPaint)
        glassPaint.shader = null

        // 2) 底部微弱陰影漸層（玻璃厚度感）
        val shadowAlpha = if (isDark) 0x15 else 0x0A
        glassPaint.shader = LinearGradient(
            rect.left, rect.bottom - rect.height() * 0.3f,
            rect.left, rect.bottom,
            0x00000000,
            (Color.BLACK and 0x00FFFFFF) or (shadowAlpha shl 24),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, glassPaint)
        glassPaint.shader = null

        // 3) 上緣高光線（玻璃邊緣反射弧線）
        val edgeAlpha = if (isDark) 0x30 else 0x20
        glassPaint.style = Paint.Style.STROKE
        glassPaint.strokeWidth = 0.5f * density
        glassPaint.shader = LinearGradient(
            rect.left + rect.width() * 0.2f, rect.top,
            rect.left + rect.width() * 0.8f, rect.top,
            0x00FFFFFF,
            (Color.WHITE and 0x00FFFFFF) or (edgeAlpha shl 24),
            Shader.TileMode.CLAMP
        )
        // 只畫上半弧
        val edgeRect = RectF(
            rect.left + 2 * density, rect.top + 0.5f * density,
            rect.right - 2 * density, rect.top + cr * 2
        )
        canvas.drawArc(edgeRect, 180f, 180f, false, glassPaint)
        glassPaint.shader = null
        glassPaint.style = Paint.Style.FILL

        // 4) 內邊緣柔光（液態感的圓弧內反射）
        if (!isPressed) {
            val innerGlow = if (isDark) 0x0CFFFFFF.toInt() else 0x08FFFFFF
            glassPaint.style = Paint.Style.STROKE
            glassPaint.strokeWidth = 1f * density
            glassPaint.color = innerGlow
            val inset = 1f * density
            val innerRect = RectF(
                rect.left + inset, rect.top + inset,
                rect.right - inset, rect.bottom - inset
            )
            canvas.drawRoundRect(innerRect, cr - inset * 0.5f, cr - inset * 0.5f, glassPaint)
            glassPaint.style = Paint.Style.FILL
        }

        // 5) 按壓時的液態波紋效果（pressed ripple tint）
        if (isPressed) {
            val rippleTint = if (isDark) 0x18FFFFFF.toInt() else 0x10000000
            glassPaint.color = rippleTint
            canvas.drawRoundRect(rect, cr, cr, glassPaint)
        }
    }

    // ── 雪域深藍：冰霜漸層 + 雪花 ──

    private fun decorateSnowDark(canvas: Canvas, rect: RectF, theme: KeyboardTheme, isSpecial: Boolean, cr: Float, rng: Random) {
        gradientPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            0x10E8F4FF.toInt(), 0x00E8F4FF.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, gradientPaint)
        gradientPaint.shader = null

        if (isSpecial) {
            drawSnowflakes(canvas, rect, rng, 3, 0x30E8F4FF.toInt())
        } else {
            drawSnowflakes(canvas, rect, rng, 1, 0x15E8F4FF.toInt())
        }
    }

    // ── 雪域白晝：柔和光暈 + 淡藍雪花 ──

    private fun decorateSnowLight(canvas: Canvas, rect: RectF, theme: KeyboardTheme, isSpecial: Boolean, cr: Float, rng: Random) {
        gradientPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            0x0CFFFFFF, 0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, gradientPaint)
        gradientPaint.shader = null

        if (isSpecial) {
            drawSnowflakes(canvas, rect, rng, 2, 0x204A90E2.toInt())
        }
    }

    // ── 深空紫：星點 + 星雲漸層 ──

    private fun decorateDeepSpace(canvas: Canvas, rect: RectF, theme: KeyboardTheme, isSpecial: Boolean, cr: Float, rng: Random) {
        val starCount = if (isSpecial) 5 else 2
        decorPaint.style = Paint.Style.FILL
        for (i in 0 until starCount) {
            val sx = rect.left + rng.nextFloat() * rect.width()
            val sy = rect.top + rng.nextFloat() * rect.height()
            val size = (0.5f + rng.nextFloat() * 1.2f) * density
            decorPaint.color = 0x50D8D0F0.toInt()
            canvas.drawCircle(sx, sy, size, decorPaint)
        }

        if (isSpecial) {
            gradientPaint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                0x207C3AED.toInt(), 0x05D8D0F0.toInt(),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, cr, cr, gradientPaint)
            gradientPaint.shader = null
        }
    }

    // ── 櫻花：花瓣圓點 + 粉漸層 ──

    private fun decorateSakura(canvas: Canvas, rect: RectF, theme: KeyboardTheme, isSpecial: Boolean, cr: Float, rng: Random) {
        if (isSpecial) {
            gradientPaint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                0x18EC4899.toInt(), 0x08FFC0CB.toInt(),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, cr, cr, gradientPaint)
            gradientPaint.shader = null
        }

        val petalCount = if (isSpecial) 4 else 1
        decorPaint.style = Paint.Style.FILL
        for (i in 0 until petalCount) {
            val px = rect.left + rng.nextFloat() * rect.width()
            val py = rect.top + rng.nextFloat() * rect.height()
            val size = (1f + rng.nextFloat() * 1.5f) * density
            decorPaint.color = 0x25EC4899.toInt()
            canvas.drawCircle(px, py, size, decorPaint)
        }
    }

    // ── 極光：綠光掃掠 + 極光條紋 ──

    private fun decorateAurora(canvas: Canvas, rect: RectF, theme: KeyboardTheme, isSpecial: Boolean, cr: Float, rng: Random) {
        gradientPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            0x0000D4AA.toInt(), 0x1500D4AA.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, gradientPaint)
        gradientPaint.shader = null

        if (isSpecial) {
            decorPaint.style = Paint.Style.STROKE
            decorPaint.strokeWidth = 0.5f * density
            for (i in 0 until 3) {
                val y = rect.top + rect.height() * (0.2f + i * 0.3f)
                decorPaint.color = 0x1800D4AA.toInt()
                canvas.drawLine(rect.left + 4 * density, y, rect.right - 4 * density, y, decorPaint)
            }
            decorPaint.style = Paint.Style.FILL
        }
    }

    // ── 霓虹都市：發光邊框 + 掃描線紋理 ──

    private fun decorateCyberpunk(canvas: Canvas, rect: RectF, theme: KeyboardTheme, isSpecial: Boolean, cr: Float, rng: Random) {
        decorPaint.style = Paint.Style.STROKE
        decorPaint.strokeWidth = 0.3f * density
        decorPaint.color = 0x0800F5FF.toInt()
        canvas.save()
        canvas.clipRect(rect)
        var y = rect.top
        while (y < rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y, decorPaint)
            y += 3f * density
        }
        canvas.restore()
        decorPaint.style = Paint.Style.FILL

        if (isSpecial) {
            decorPaint.style = Paint.Style.STROKE
            decorPaint.strokeWidth = 1f * density
            decorPaint.color = 0x3000F5FF.toInt()
            val inner = RectF(rect.left + 1 * density, rect.top + 1 * density, rect.right - 1 * density, rect.bottom - 1 * density)
            canvas.drawRoundRect(inner, cr, cr, decorPaint)
            decorPaint.style = Paint.Style.FILL
        }
    }

    // ── 抹茶：和風波紋 + 漸層 ──

    private fun decorateMatcha(canvas: Canvas, rect: RectF, theme: KeyboardTheme, isSpecial: Boolean, cr: Float, rng: Random) {
        gradientPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            0x08FFFFFF, 0x084CAF50.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, gradientPaint)
        gradientPaint.shader = null

        if (isSpecial) {
            decorPaint.style = Paint.Style.STROKE
            decorPaint.strokeWidth = 0.5f * density
            decorPaint.color = 0x184CAF50.toInt()
            for (i in 0 until 3) {
                val cx = rect.left + rect.width() * (0.2f + i * 0.3f)
                val cy = rect.bottom - 2 * density
                canvas.drawArc(
                    cx - 6 * density, cy - 6 * density,
                    cx + 6 * density, cy + 6 * density,
                    200f, 140f, false, decorPaint
                )
            }
            decorPaint.style = Paint.Style.FILL
        }
    }

    // ── 午夜鎏金：金角裝飾 + 金箔漸層 ──

    private fun decorateMidnightGold(canvas: Canvas, rect: RectF, theme: KeyboardTheme, isSpecial: Boolean, cr: Float, rng: Random) {
        gradientPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.top + rect.height() * 0.3f,
            0x12FFD700.toInt(), 0x00FFD700.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, gradientPaint)
        gradientPaint.shader = null

        if (isSpecial) {
            gradientPaint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                0x20FFD700.toInt(), 0x08FFA500.toInt(),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, cr, cr, gradientPaint)
            gradientPaint.shader = null

            decorPaint.color = 0x35FFD700.toInt()
            val m = 4f * density
            val dotR = 1.2f * density
            canvas.drawCircle(rect.left + m, rect.top + m, dotR, decorPaint)
            canvas.drawCircle(rect.right - m, rect.top + m, dotR, decorPaint)
            canvas.drawCircle(rect.left + m, rect.bottom - m, dotR, decorPaint)
            canvas.drawCircle(rect.right - m, rect.bottom - m, dotR, decorPaint)
        }
    }

    // ── 輔助方法 ──

    private fun drawSnowflakes(canvas: Canvas, rect: RectF, rng: Random, count: Int, color: Int) {
        decorPaint.style = Paint.Style.STROKE
        decorPaint.strokeWidth = 0.5f * density
        decorPaint.color = color

        for (i in 0 until count) {
            val cx = rect.left + rect.width() * (0.15f + rng.nextFloat() * 0.7f)
            val cy = rect.top + rect.height() * (0.15f + rng.nextFloat() * 0.7f)
            val r = (1.5f + rng.nextFloat() * 2f) * density
            for (a in 0 until 6) {
                val angle = a * 60f * Math.PI.toFloat() / 180f
                val ex = cx + r * cos(angle)
                val ey = cy + r * sin(angle)
                canvas.drawLine(cx, cy, ex, ey, decorPaint)
                if (r > 2f * density) {
                    val mx = cx + r * 0.5f * cos(angle)
                    val my = cy + r * 0.5f * sin(angle)
                    val branchAngle = angle + 0.5f
                    val br = r * 0.3f
                    canvas.drawLine(mx, my, mx + br * cos(branchAngle), my + br * sin(branchAngle), decorPaint)
                }
            }
        }
        decorPaint.style = Paint.Style.FILL
    }
}

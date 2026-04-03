package com.snowrealm.yukiboard.animation

import android.content.Context
import android.graphics.*
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 鍵盤動態粒子背景 — 每個主題有獨特的飄落/漂浮粒子。
 *
 * 輕量 Canvas 實作，透明覆蓋在鍵盤背景上。
 * - snow_dark / snow_light: 雪花飄落
 * - sakura: 櫻花花瓣飄落
 * - deep_space: 星塵漂浮
 * - aurora: 極光光帶漂浮
 * - cyberpunk: 數位雨 / 霓虹粒子
 * - matcha: 茶葉漂浮
 * - midnight_gold: 金箔飄落
 */
class KeyboardParticleView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = mutableListOf<Particle>()
    private var themeId: String = "snow_dark"
    private var running = false
    private val rng = Random(System.nanoTime())

    // 粒子數量上限（效能控制）
    private val maxParticles = 35

    data class Particle(
        var x: Float,
        var y: Float,
        var size: Float,
        var speedX: Float,
        var speedY: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var alpha: Int,
        var color: Int,
        var shape: Shape,
        var life: Float = 1f,
        var decay: Float = 0f
    )

    enum class Shape {
        SNOWFLAKE, PETAL, STAR, LINE, CIRCLE, LEAF, DIAMOND
    }

    fun setTheme(theme: KeyboardTheme) {
        themeId = theme.id
        particles.clear()
    }

    fun start() {
        running = true
        invalidate()
    }

    fun stop() {
        running = false
    }

    override fun onDraw(canvas: Canvas) {
        if (!running || width == 0 || height == 0) return

        // 補充粒子
        while (particles.size < maxParticles) {
            particles.add(spawnParticle())
        }

        // 更新 + 繪製
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            updateParticle(p)
            if (p.y > height + 20 || p.x < -20 || p.x > width + 20 || p.life <= 0f) {
                iter.remove()
            } else {
                drawParticle(canvas, p)
            }
        }

        // 16ms ~ 60fps
        postInvalidateOnAnimation()
    }

    private fun spawnParticle(): Particle {
        val w = width.toFloat()
        val h = height.toFloat()

        return when (themeId) {
            "snow_dark", "snow_light" -> Particle(
                x = rng.nextFloat() * w,
                y = -rng.nextFloat() * h * 0.3f,
                size = (2f + rng.nextFloat() * 3f) * density,
                speedX = (-0.3f + rng.nextFloat() * 0.6f) * density,
                speedY = (0.5f + rng.nextFloat() * 1.2f) * density,
                rotation = rng.nextFloat() * 360f,
                rotationSpeed = (-1f + rng.nextFloat() * 2f),
                alpha = 80 + rng.nextInt(100),
                color = if (themeId == "snow_dark") 0xE8F4FF else 0x8AADD8,
                shape = Shape.SNOWFLAKE
            )
            "sakura" -> Particle(
                x = rng.nextFloat() * w,
                y = -rng.nextFloat() * h * 0.2f,
                size = (3f + rng.nextFloat() * 4f) * density,
                speedX = (0.2f + rng.nextFloat() * 0.8f) * density,
                speedY = (0.6f + rng.nextFloat() * 1f) * density,
                rotation = rng.nextFloat() * 360f,
                rotationSpeed = (1f + rng.nextFloat() * 3f),
                alpha = 100 + rng.nextInt(80),
                color = if (rng.nextFloat() > 0.3f) 0xFFB7C5 else 0xEC4899.toInt(),
                shape = Shape.PETAL
            )
            "deep_space" -> Particle(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                size = (0.5f + rng.nextFloat() * 2f) * density,
                speedX = (-0.1f + rng.nextFloat() * 0.2f) * density,
                speedY = (0.05f + rng.nextFloat() * 0.15f) * density,
                rotation = 0f,
                rotationSpeed = 0f,
                alpha = 60 + rng.nextInt(140),
                color = when (rng.nextInt(3)) { 0 -> 0xD8D0F0; 1 -> 0x7C3AED; else -> 0xFFFFFF },
                shape = Shape.STAR,
                life = 1f,
                decay = 0.002f + rng.nextFloat() * 0.003f
            )
            "aurora" -> Particle(
                x = -20f,
                y = rng.nextFloat() * h * 0.6f,
                size = (8f + rng.nextFloat() * 15f) * density,
                speedX = (0.3f + rng.nextFloat() * 0.5f) * density,
                speedY = (-0.1f + rng.nextFloat() * 0.2f) * density,
                rotation = 0f,
                rotationSpeed = 0f,
                alpha = 20 + rng.nextInt(30),
                color = when (rng.nextInt(3)) { 0 -> 0x00D4AA; 1 -> 0x00E5FF; else -> 0x7CFFCB },
                shape = Shape.LINE
            )
            "cyberpunk" -> Particle(
                x = rng.nextFloat() * w,
                y = -rng.nextFloat() * h * 0.1f,
                size = (1f + rng.nextFloat() * 2f) * density,
                speedX = 0f,
                speedY = (2f + rng.nextFloat() * 4f) * density,
                rotation = 0f,
                rotationSpeed = 0f,
                alpha = 60 + rng.nextInt(100),
                color = if (rng.nextFloat() > 0.5f) 0x00F5FF else 0xFF00FF.toInt(),
                shape = Shape.LINE
            )
            "matcha" -> Particle(
                x = rng.nextFloat() * w,
                y = -rng.nextFloat() * h * 0.2f,
                size = (2f + rng.nextFloat() * 3f) * density,
                speedX = (-0.2f + rng.nextFloat() * 0.4f) * density,
                speedY = (0.3f + rng.nextFloat() * 0.7f) * density,
                rotation = rng.nextFloat() * 360f,
                rotationSpeed = (0.5f + rng.nextFloat() * 2f),
                alpha = 60 + rng.nextInt(80),
                color = if (rng.nextFloat() > 0.4f) 0x4CAF50 else 0x8BC34A.toInt(),
                shape = Shape.LEAF
            )
            "midnight_gold" -> Particle(
                x = rng.nextFloat() * w,
                y = -rng.nextFloat() * h * 0.2f,
                size = (1.5f + rng.nextFloat() * 2.5f) * density,
                speedX = (-0.2f + rng.nextFloat() * 0.4f) * density,
                speedY = (0.4f + rng.nextFloat() * 0.8f) * density,
                rotation = rng.nextFloat() * 360f,
                rotationSpeed = (1f + rng.nextFloat() * 3f),
                alpha = 80 + rng.nextInt(100),
                color = if (rng.nextFloat() > 0.3f) 0xFFD700 else 0xFFA500.toInt(),
                shape = Shape.DIAMOND
            )
            else -> Particle(
                x = rng.nextFloat() * w, y = -10f,
                size = 2f * density, speedX = 0f, speedY = 1f * density,
                rotation = 0f, rotationSpeed = 0f, alpha = 80,
                color = 0xFFFFFF, shape = Shape.CIRCLE
            )
        }
    }

    private fun updateParticle(p: Particle) {
        p.x += p.speedX
        p.y += p.speedY
        p.rotation += p.rotationSpeed
        if (p.decay > 0f) {
            p.life -= p.decay
            p.alpha = (p.alpha * p.life).toInt().coerceIn(0, 255)
        }
        // 雪花/花瓣左右搖擺
        if (p.shape == Shape.SNOWFLAKE || p.shape == Shape.PETAL || p.shape == Shape.LEAF) {
            p.speedX += (-0.02f + rng.nextFloat() * 0.04f) * density
            p.speedX = p.speedX.coerceIn(-1.5f * density, 1.5f * density)
        }
    }

    private fun drawParticle(canvas: Canvas, p: Particle) {
        paint.color = (p.color and 0x00FFFFFF) or ((p.alpha and 0xFF) shl 24)

        canvas.save()
        canvas.translate(p.x, p.y)
        canvas.rotate(p.rotation)

        when (p.shape) {
            Shape.SNOWFLAKE -> drawSnowflake(canvas, p.size)
            Shape.PETAL -> drawPetal(canvas, p.size)
            Shape.STAR -> drawStar(canvas, p.size)
            Shape.LINE -> drawGlowLine(canvas, p.size)
            Shape.CIRCLE -> canvas.drawCircle(0f, 0f, p.size, paint)
            Shape.LEAF -> drawLeaf(canvas, p.size)
            Shape.DIAMOND -> drawDiamond(canvas, p.size)
        }

        canvas.restore()
    }

    private fun drawSnowflake(canvas: Canvas, size: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * density
        for (i in 0 until 6) {
            val angle = i * 60f * Math.PI.toFloat() / 180f
            val ex = size * cos(angle)
            val ey = size * sin(angle)
            canvas.drawLine(0f, 0f, ex, ey, paint)
            // 小分支
            val mx = size * 0.55f * cos(angle)
            val my = size * 0.55f * sin(angle)
            val ba = angle + 0.6f
            val br = size * 0.35f
            canvas.drawLine(mx, my, mx + br * cos(ba), my + br * sin(ba), paint)
            val ba2 = angle - 0.6f
            canvas.drawLine(mx, my, mx + br * cos(ba2), my + br * sin(ba2), paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawPetal(canvas: Canvas, size: Float) {
        paint.style = Paint.Style.FILL
        val path = Path()
        // 櫻花花瓣形狀：橢圓 + 尖端
        path.moveTo(0f, -size * 0.5f)
        path.cubicTo(size * 0.6f, -size * 0.3f, size * 0.6f, size * 0.4f, 0f, size * 0.6f)
        path.cubicTo(-size * 0.6f, size * 0.4f, -size * 0.6f, -size * 0.3f, 0f, -size * 0.5f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawStar(canvas: Canvas, size: Float) {
        paint.style = Paint.Style.FILL
        // 十字閃光
        canvas.drawCircle(0f, 0f, size * 0.6f, paint)
        paint.strokeWidth = 0.5f * density
        paint.style = Paint.Style.STROKE
        canvas.drawLine(-size, 0f, size, 0f, paint)
        canvas.drawLine(0f, -size, 0f, size, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawGlowLine(canvas: Canvas, size: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.3f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(0f, -size, 0f, size, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawLeaf(canvas: Canvas, size: Float) {
        paint.style = Paint.Style.FILL
        val path = Path()
        path.moveTo(0f, -size)
        path.cubicTo(size * 0.5f, -size * 0.3f, size * 0.3f, size * 0.5f, 0f, size)
        path.cubicTo(-size * 0.3f, size * 0.5f, -size * 0.5f, -size * 0.3f, 0f, -size)
        path.close()
        canvas.drawPath(path, paint)
        // 葉脈
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.3f * density
        canvas.drawLine(0f, -size * 0.8f, 0f, size * 0.8f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawDiamond(canvas: Canvas, size: Float) {
        paint.style = Paint.Style.FILL
        val path = Path()
        path.moveTo(0f, -size)
        path.lineTo(size * 0.5f, 0f)
        path.lineTo(0f, size)
        path.lineTo(-size * 0.5f, 0f)
        path.close()
        canvas.drawPath(path, paint)
    }
}

package com.snowrealm.yukiboard.ui.panel

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

/**
 * 工具列動畫背景渲染器 — 8 種動態主題效果。
 *
 * 每種效果在 Canvas 上繪製動畫背景，呼叫 [draw] 後需持續 invalidate。
 */
class ToolbarAnimRenderer(private val density: Float) {

    enum class Style(val id: String, val displayName: String) {
        NONE("none", "無"),
        STARFIELD("starfield", "星空 Starfield"),
        OCEAN("ocean", "海洋 Ocean"),
        EMBER("ember", "餘燼 Ember"),
        MATRIX("matrix", "矩陣 Matrix"),
        FROST("frost", "冰霜 Frost"),
        LAVA("lava", "熔岩 Lava"),
        RAINBOW("rainbow", "彩虹 Rainbow"),
        THUNDER("thunder", "雷電 Thunder");

        companion object {
            fun fromId(id: String): Style = entries.find { it.id == id } ?: NONE
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // ── Starfield ──
    private data class Star(var x: Float, var y: Float, var z: Float, var brightness: Float)
    private val stars = mutableListOf<Star>()
    private data class Meteor(var x: Float, var y: Float, var angle: Float, var speed: Float, var life: Float, var maxLife: Float)
    private var meteor: Meteor? = null
    private var lastMeteorTime = 0L

    // ── Ocean ──
    // uses time-based sine waves, no state needed

    // ── Ember ──
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
                                var size: Float, var life: Float, var maxLife: Float, var color: Int)
    private val embers = mutableListOf<Particle>()

    // ── Matrix 3D ──
    private data class MatrixColumn(var x: Float, var depth: Float, var chars: CharArray,
                                    var offset: Float, var speed: Float, var charSize: Float)
    private val matrixColumns = mutableListOf<MatrixColumn>()
    private val matrixChars = "01アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン".toCharArray()

    // ── Frost ──
    private data class Crystal(var x: Float, var y: Float, var size: Float, var rotation: Float,
                               var rotSpeed: Float, var alpha: Float, var pulsePhase: Float)
    private val crystals = mutableListOf<Crystal>()

    // ── Lava ──
    private data class Bubble(var x: Float, var y: Float, var size: Float, var speed: Float,
                              var wobblePhase: Float, var alpha: Float)
    private val bubbles = mutableListOf<Bubble>()

    // ── Rainbow ──
    // uses time-based HSV cycling, no state needed

    // ── Thunder ──
    private var lightningAlpha = 0f
    private var lightningX = 0f
    private var nextLightningTime = 0L
    private val lightningPath = Path()

    private var initialized = false
    private var lastW = 0
    private var lastH = 0

    /**
     * 繪製動畫背景到 [canvas]。回傳 true 表示需要持續 invalidate 動畫。
     */
    fun draw(canvas: Canvas, w: Int, h: Int, style: Style, timeMs: Long): Boolean {
        if (style == Style.NONE || w <= 0 || h <= 0) return false
        if (!initialized || lastW != w || lastH != h) {
            initParticles(w, h, style)
            lastW = w; lastH = h; initialized = true
        }

        return when (style) {
            Style.NONE -> false
            Style.STARFIELD -> drawStarfield(canvas, w, h, timeMs)
            Style.OCEAN -> drawOcean(canvas, w, h, timeMs)
            Style.EMBER -> drawEmber(canvas, w, h, timeMs)
            Style.MATRIX -> drawMatrix(canvas, w, h, timeMs)
            Style.FROST -> drawFrost(canvas, w, h, timeMs)
            Style.LAVA -> drawLava(canvas, w, h, timeMs)
            Style.RAINBOW -> drawRainbow(canvas, w, h, timeMs)
            Style.THUNDER -> drawThunder(canvas, w, h, timeMs)
        }
    }

    fun resetStyle() {
        initialized = false
        stars.clear(); embers.clear(); matrixColumns.clear()
        crystals.clear(); bubbles.clear()
        meteor = null; lightningAlpha = 0f
    }

    // ═══════════════════════════════════════════════════════════
    // Init
    // ═══════════════════════════════════════════════════════════

    private fun initParticles(w: Int, h: Int, style: Style) {
        val wf = w.toFloat(); val hf = h.toFloat()
        stars.clear(); embers.clear(); matrixColumns.clear()
        crystals.clear(); bubbles.clear()

        when (style) {
            Style.STARFIELD -> {
                repeat(60) {
                    stars.add(Star(
                        x = Random.nextFloat() * wf,
                        y = Random.nextFloat() * hf,
                        z = Random.nextFloat(), // 0=far, 1=near
                        brightness = Random.nextFloat()
                    ))
                }
            }
            Style.EMBER -> {
                repeat(30) { spawnEmber(wf, hf) }
            }
            Style.MATRIX -> {
                val colCount = (w / (10 * density)).toInt().coerceIn(8, 40)
                val spacing = wf / colCount
                repeat(colCount) { i ->
                    val depth = Random.nextFloat() // 0=far, 1=near
                    val charCount = Random.nextInt(3, 8)
                    matrixColumns.add(MatrixColumn(
                        x = spacing * i + spacing * 0.5f,
                        depth = depth,
                        chars = CharArray(charCount) { matrixChars.random() },
                        offset = Random.nextFloat() * hf * 2f,
                        speed = (0.3f + depth * 1.5f) * density,
                        charSize = (6f + depth * 8f) * density
                    ))
                }
            }
            Style.FROST -> {
                repeat(15) {
                    crystals.add(Crystal(
                        x = Random.nextFloat() * wf,
                        y = Random.nextFloat() * hf,
                        size = (3f + Random.nextFloat() * 6f) * density,
                        rotation = Random.nextFloat() * 360f,
                        rotSpeed = (Random.nextFloat() - 0.5f) * 30f,
                        alpha = 0.3f + Random.nextFloat() * 0.5f,
                        pulsePhase = Random.nextFloat() * PI2
                    ))
                }
            }
            Style.LAVA -> {
                repeat(20) { spawnBubble(wf, hf) }
            }
            else -> {}
        }
    }

    private fun spawnEmber(w: Float, h: Float) {
        embers.add(Particle(
            x = Random.nextFloat() * w,
            y = h + Random.nextFloat() * 10f * density,
            vx = (Random.nextFloat() - 0.5f) * 0.8f * density,
            vy = -(0.5f + Random.nextFloat() * 1.5f) * density,
            size = (1.5f + Random.nextFloat() * 3f) * density,
            life = 0f,
            maxLife = 60f + Random.nextFloat() * 80f,
            color = when (Random.nextInt(4)) {
                0 -> 0xFFFF6B35.toInt()  // 橙
                1 -> 0xFFFF4500.toInt()  // 紅橙
                2 -> 0xFFFFD700.toInt()  // 金
                else -> 0xFFFF8C00.toInt() // 暗橙
            }
        ))
    }

    private fun spawnBubble(w: Float, h: Float) {
        bubbles.add(Bubble(
            x = Random.nextFloat() * w,
            y = h + Random.nextFloat() * h * 0.3f,
            size = (2f + Random.nextFloat() * 5f) * density,
            speed = (0.3f + Random.nextFloat() * 0.8f) * density,
            wobblePhase = Random.nextFloat() * PI2,
            alpha = 0.4f + Random.nextFloat() * 0.4f
        ))
    }

    // ═══════════════════════════════════════════════════════════
    // 1. Starfield — 星空粒子 + 流星
    // ═══════════════════════════════════════════════════════════

    private fun drawStarfield(canvas: Canvas, w: Int, h: Int, timeMs: Long): Boolean {
        val t = timeMs / 1000f

        // 背景：深空漸層
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            0xFF0A0A2E.toInt(), 0xFF1A1040.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // 星星閃爍
        stars.forEach { star ->
            val twinkle = (sin((t * 2f + star.x * 0.01f).toDouble()) * 0.3f + 0.7f).toFloat()
            val size = (1f + star.z * 2.5f) * density
            val alpha = (twinkle * star.brightness * 200).toInt().coerceIn(30, 220)

            // 顏色依深度：遠=暗藍，近=亮白
            val r = (180 + star.z * 75).toInt().coerceAtMost(255)
            val g = (180 + star.z * 75).toInt().coerceAtMost(255)
            val b = (200 + star.z * 55).toInt().coerceAtMost(255)
            paint.color = Color.argb(alpha, r, g, b)

            canvas.drawCircle(star.x, star.y, size, paint)

            // 大星加十字芒
            if (star.z > 0.7f) {
                paint.alpha = alpha / 3
                paint.strokeWidth = 0.5f * density
                paint.style = Paint.Style.STROKE
                val len = size * 2.5f
                canvas.drawLine(star.x - len, star.y, star.x + len, star.y, paint)
                canvas.drawLine(star.x, star.y - len, star.x, star.y + len, paint)
                paint.style = Paint.Style.FILL
            }

            // 緩慢漂移
            star.brightness = (sin((t * 1.5f + star.y * 0.02f).toDouble()) * 0.4f + 0.6f).toFloat()
        }

        // 流星
        if (timeMs - lastMeteorTime > 3000 + Random.nextLong(4000)) {
            lastMeteorTime = timeMs
            meteor = Meteor(
                x = Random.nextFloat() * w * 0.8f + w * 0.1f,
                y = 0f,
                angle = 20f + Random.nextFloat() * 30f,
                speed = (3f + Random.nextFloat() * 4f) * density,
                life = 0f,
                maxLife = 25f
            )
        }
        meteor?.let { m ->
            m.life += 1f
            val rad = Math.toRadians(m.angle.toDouble())
            m.x += (cos(rad) * m.speed).toFloat()
            m.y += (sin(rad) * m.speed).toFloat()
            val progress = m.life / m.maxLife
            val alpha = ((1f - progress) * 200).toInt().coerceIn(0, 200)
            paint.color = Color.argb(alpha, 255, 255, 240)
            val tailLen = 15f * density
            val tailX = m.x - (cos(rad) * tailLen).toFloat()
            val tailY = m.y - (sin(rad) * tailLen).toFloat()
            paint.shader = LinearGradient(tailX, tailY, m.x, m.y,
                Color.argb(0, 255, 255, 240), Color.argb(alpha, 255, 255, 240),
                Shader.TileMode.CLAMP)
            paint.strokeWidth = 2f * density
            paint.style = Paint.Style.STROKE
            canvas.drawLine(tailX, tailY, m.x, m.y, paint)
            paint.style = Paint.Style.FILL
            paint.shader = null
            canvas.drawCircle(m.x, m.y, 1.5f * density, paint)
            if (m.life >= m.maxLife) meteor = null
        }

        return true
    }

    // ═══════════════════════════════════════════════════════════
    // 2. Ocean — 多層海浪
    // ═══════════════════════════════════════════════════════════

    private fun drawOcean(canvas: Canvas, w: Int, h: Int, timeMs: Long): Boolean {
        val t = timeMs / 1000f
        val wf = w.toFloat(); val hf = h.toFloat()

        // 深海漸層底
        paint.shader = LinearGradient(0f, 0f, 0f, hf,
            0xFF001830.toInt(), 0xFF003060.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = null

        // 三層波浪
        val layers = arrayOf(
            Triple(0xFF004080.toInt(), 0.7f, 0.8f),  // 後層：深，慢
            Triple(0xFF0060A0.toInt(), 1.0f, 1.2f),  // 中層
            Triple(0xFF0080C0.toInt(), 1.5f, 1.6f),  // 前層：淺，快
        )
        val path = Path()
        layers.forEach { (color, speed, amp) ->
            path.reset()
            path.moveTo(0f, hf)
            val waveH = hf * 0.15f * amp
            val baseY = hf * 0.45f
            var px = 0f
            while (px <= wf) {
                val wave1 = sin((px / wf * 4f + t * speed).toDouble()) * waveH
                val wave2 = sin((px / wf * 7f - t * speed * 0.7f).toDouble()) * waveH * 0.3f
                val y = baseY + wave1.toFloat() + wave2.toFloat()
                path.lineTo(px, y)
                px += 2f
            }
            path.lineTo(wf, hf)
            path.close()
            paint.color = color
            paint.alpha = 120
            canvas.drawPath(path, paint)
        }

        // 水面光點
        repeat(5) { i ->
            val sparkX = (t * 30f * (i + 1) * 0.3f) % wf
            val sparkY = hf * 0.35f + sin((t * 2f + i).toDouble()).toFloat() * hf * 0.1f
            val sparkAlpha = (sin((t * 3f + i * 1.5f).toDouble()) * 80 + 80).toInt().coerceIn(0, 160)
            paint.color = Color.argb(sparkAlpha, 160, 220, 255)
            canvas.drawCircle(sparkX, sparkY, 1.5f * density, paint)
        }

        return true
    }

    // ═══════════════════════════════════════════════════════════
    // 3. Ember — 上升火焰粒子
    // ═══════════════════════════════════════════════════════════

    private fun drawEmber(canvas: Canvas, w: Int, h: Int, timeMs: Long): Boolean {
        val wf = w.toFloat(); val hf = h.toFloat()

        // 暗紅底
        paint.shader = LinearGradient(0f, 0f, 0f, hf,
            0xFF1A0A00.toInt(), 0xFF2A1000.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = null

        // 底部燃燒光暈
        val glowY = hf * 0.9f
        paint.shader = RadialGradient(wf / 2f, hf + 5 * density, hf * 0.8f,
            0x40FF4500.toInt(), 0x00FF4500.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = null

        // 更新粒子
        val iter = embers.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life += 1f
            p.x += p.vx
            p.y += p.vy
            p.vy *= 0.99f // 減速
            p.vx += (Random.nextFloat() - 0.5f) * 0.2f * density // 飄動

            val progress = p.life / p.maxLife
            if (progress >= 1f) {
                iter.remove()
                spawnEmber(wf, hf)
                continue
            }

            val alpha = ((1f - progress) * 200).toInt().coerceIn(0, 200)
            val r = Color.red(p.color)
            val g = (Color.green(p.color) * (1f - progress * 0.5f)).toInt()
            val b = Color.blue(p.color)
            paint.color = Color.argb(alpha, r, g, b)
            canvas.drawCircle(p.x, p.y, p.size * (1f - progress * 0.3f), paint)

            // 發光暈
            paint.alpha = alpha / 4
            canvas.drawCircle(p.x, p.y, p.size * 2.5f, paint)
        }

        return true
    }

    // ═══════════════════════════════════════════════════════════
    // 4. Matrix 3D — 駭客帝國立體字元雨
    // ═══════════════════════════════════════════════════════════

    private fun drawMatrix(canvas: Canvas, w: Int, h: Int, timeMs: Long): Boolean {
        val wf = w.toFloat(); val hf = h.toFloat()

        // 純黑底
        canvas.drawColor(0xFF050505.toInt())

        // 透視匯聚點
        val vanishX = wf / 2f
        val vanishY = -hf * 0.3f // 上方消失點

        matrixColumns.forEach { col ->
            val depth = col.depth // 0=far, 1=near

            // 3D 透視：近處 x 偏離中心更多，遠處 x 趨向消失點
            val perspFactor = 0.3f + depth * 0.7f  // 0.3~1.0
            val drawX = vanishX + (col.x - vanishX) * perspFactor

            // 字元大小隨深度變化
            textPaint.textSize = col.charSize
            textPaint.textAlign = Paint.Align.CENTER

            // 移動 offset
            col.offset += col.speed
            val totalCharH = col.charSize * col.chars.size
            if (col.offset > hf + totalCharH) {
                col.offset = -totalCharH
                // 隨機更換字元
                for (i in col.chars.indices) {
                    if (Random.nextFloat() < 0.3f) col.chars[i] = matrixChars.random()
                }
            }

            col.chars.forEachIndexed { idx, ch ->
                val cy = col.offset + idx * col.charSize

                if (cy < -col.charSize || cy > hf + col.charSize) return@forEachIndexed

                // 亮度：近=亮、遠=暗、頭部最亮
                val headGlow = if (idx == col.chars.size - 1) 1.5f else 1f
                val baseAlpha = (50 + depth * 180).toInt()
                val fadeFromTop = (1f - idx.toFloat() / col.chars.size * 0.6f)
                val alpha = (baseAlpha * fadeFromTop * headGlow).toInt().coerceIn(20, 255)

                // 顏色：經典綠，頭部白綠
                if (idx == col.chars.size - 1 && depth > 0.5f) {
                    textPaint.color = Color.argb(alpha, 180, 255, 180)
                } else {
                    val g = (80 + depth * 175).toInt().coerceAtMost(255)
                    textPaint.color = Color.argb(alpha, 0, g, 0)
                }

                canvas.drawText(ch.toString(), drawX, cy, textPaint)
            }

            // 隨機閃爍：偶爾某字元變亮
            if (Random.nextFloat() < 0.02f && col.chars.isNotEmpty()) {
                val flickerIdx = Random.nextInt(col.chars.size)
                col.chars[flickerIdx] = matrixChars.random()
            }
        }

        // 頂部霧化 + 底部霧化（增加深度感）
        paint.shader = LinearGradient(0f, 0f, 0f, hf * 0.2f,
            0xCC050505.toInt(), 0x00050505, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf * 0.2f, paint)
        paint.shader = LinearGradient(0f, hf * 0.8f, 0f, hf,
            0x00050505, 0x99050505.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, hf * 0.8f, wf, hf, paint)
        paint.shader = null

        return true
    }

    // ═══════════════════════════════════════════════════════════
    // 5. Frost — 冰晶 + 寒霧
    // ═══════════════════════════════════════════════════════════

    private fun drawFrost(canvas: Canvas, w: Int, h: Int, timeMs: Long): Boolean {
        val t = timeMs / 1000f
        val wf = w.toFloat(); val hf = h.toFloat()

        // 冰藍漸層底
        paint.shader = LinearGradient(0f, 0f, wf, hf,
            0xFF0A1828.toInt(), 0xFF102840.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = null

        // 寒霧飄動
        val fogAlpha = (sin((t * 0.5f).toDouble()) * 20 + 30).toInt().coerceIn(10, 50)
        paint.shader = LinearGradient(0f, 0f, wf, 0f,
            Color.argb(fogAlpha, 150, 200, 230),
            Color.argb(fogAlpha / 2, 100, 180, 220),
            Shader.TileMode.MIRROR)
        canvas.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = null

        // 冰晶（六邊形）
        crystals.forEach { c ->
            val pulse = (sin((t * 1.5f + c.pulsePhase).toDouble()) * 0.2f + 0.8f).toFloat()
            val alpha = (c.alpha * pulse * 200).toInt().coerceIn(20, 180)

            canvas.save()
            canvas.translate(c.x, c.y)
            canvas.rotate(c.rotation + t * c.rotSpeed)

            // 畫六邊形
            pathPaint.color = Color.argb(alpha, 160, 220, 255)
            pathPaint.strokeWidth = 1f * density
            val path = Path()
            for (i in 0..5) {
                val angle = Math.toRadians((60.0 * i) - 30.0)
                val px = (cos(angle) * c.size).toFloat()
                val py = (sin(angle) * c.size).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            canvas.drawPath(path, pathPaint)

            // 內部十字線
            pathPaint.alpha = alpha / 2
            pathPaint.strokeWidth = 0.5f * density
            for (i in 0..2) {
                val angle = Math.toRadians(60.0 * i)
                val px = (cos(angle) * c.size * 0.8f).toFloat()
                val py = (sin(angle) * c.size * 0.8f).toFloat()
                canvas.drawLine(-px, -py, px, py, pathPaint)
            }

            canvas.restore()

            // 緩慢飄移
            c.x += sin((t * 0.3f + c.pulsePhase).toDouble()).toFloat() * 0.15f * density
            if (c.x < -c.size * 2) c.x = wf + c.size
            if (c.x > wf + c.size * 2) c.x = -c.size
        }

        return true
    }

    // ═══════════════════════════════════════════════════════════
    // 6. Lava — 熔岩流動 + 氣泡
    // ═══════════════════════════════════════════════════════════

    private fun drawLava(canvas: Canvas, w: Int, h: Int, timeMs: Long): Boolean {
        val t = timeMs / 1000f
        val wf = w.toFloat(); val hf = h.toFloat()

        // 暗紅底
        canvas.drawColor(0xFF1A0500.toInt())

        // 熔岩流動漸層（用 sin 波模擬流動）
        val lavaPath = Path()
        lavaPath.moveTo(0f, hf)
        var px = 0f
        while (px <= wf) {
            val wave = sin((px / wf * 3f + t * 0.5f).toDouble()) * hf * 0.15f +
                       sin((px / wf * 6f - t * 0.3f).toDouble()) * hf * 0.05f
            lavaPath.lineTo(px, hf * 0.4f + wave.toFloat())
            px += 2f
        }
        lavaPath.lineTo(wf, hf); lavaPath.close()
        paint.shader = LinearGradient(0f, hf * 0.3f, 0f, hf,
            0xCCFF4500.toInt(), 0xFFCC2200.toInt(), Shader.TileMode.CLAMP)
        canvas.drawPath(lavaPath, paint)
        paint.shader = null

        // 上層熱浪光暈
        paint.shader = LinearGradient(0f, 0f, 0f, hf * 0.5f,
            0x00000000, 0x30FF6600.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf * 0.5f, paint)
        paint.shader = null

        // 氣泡上升
        val bIter = bubbles.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            b.y -= b.speed
            b.x += sin((t * 2f + b.wobblePhase).toDouble()).toFloat() * 0.3f * density

            if (b.y < -b.size * 2) {
                bIter.remove()
                spawnBubble(wf, hf)
                continue
            }

            val alpha = (b.alpha * 180).toInt().coerceIn(20, 180)
            // 橙紅外圈
            paint.color = Color.argb(alpha / 2, 255, 100, 0)
            canvas.drawCircle(b.x, b.y, b.size * 1.5f, paint)
            // 亮黃核心
            paint.color = Color.argb(alpha, 255, 200, 50)
            canvas.drawCircle(b.x, b.y, b.size * 0.7f, paint)
        }

        return true
    }

    // ═══════════════════════════════════════════════════════════
    // 7. Rainbow — 流動彩虹漸層
    // ═══════════════════════════════════════════════════════════

    private fun drawRainbow(canvas: Canvas, w: Int, h: Int, timeMs: Long): Boolean {
        val t = timeMs / 1000f
        val wf = w.toFloat(); val hf = h.toFloat()

        // 深底色
        canvas.drawColor(0xFF0A0A14.toInt())

        // 流動彩虹帶（8 段顏色）
        val bandCount = 8
        val bandH = hf / bandCount
        val hsv = FloatArray(3)

        for (i in 0 until bandCount) {
            val hue = ((t * 30f + i * 45f) % 360f)
            hsv[0] = hue; hsv[1] = 0.8f; hsv[2] = 0.6f
            val c1 = Color.HSVToColor(80, hsv)
            hsv[0] = ((hue + 30f) % 360f)
            val c2 = Color.HSVToColor(80, hsv)

            val yOffset = sin((t * 1.5f + i * 0.5f).toDouble()).toFloat() * bandH * 0.3f
            val y1 = i * bandH + yOffset
            val y2 = y1 + bandH * 1.3f

            paint.shader = LinearGradient(0f, y1, wf, y2, c1, c2, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, y1, wf, y2, paint)
        }
        paint.shader = null

        // 頂部光暈
        val glowHue = (t * 60f) % 360f
        hsv[0] = glowHue; hsv[1] = 0.7f; hsv[2] = 0.8f
        val glowColor = Color.HSVToColor(40, hsv)
        paint.shader = RadialGradient(wf / 2f, hf / 2f, wf * 0.5f,
            glowColor, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = null

        return true
    }

    // ═══════════════════════════════════════════════════════════
    // 8. Thunder — 紫電 + 閃電
    // ═══════════════════════════════════════════════════════════

    private fun drawThunder(canvas: Canvas, w: Int, h: Int, timeMs: Long): Boolean {
        val t = timeMs / 1000f
        val wf = w.toFloat(); val hf = h.toFloat()

        // 暗紫底
        paint.shader = LinearGradient(0f, 0f, 0f, hf,
            0xFF0A0520.toInt(), 0xFF150A30.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = null

        // 環境電弧光（淡紫波動）
        val ambientAlpha = (sin((t * 2f).toDouble()) * 15 + 20).toInt().coerceIn(5, 35)
        paint.color = Color.argb(ambientAlpha, 120, 80, 200)
        canvas.drawRect(0f, 0f, wf, hf, paint)

        // 閃電觸發
        if (timeMs > nextLightningTime) {
            nextLightningTime = timeMs + 1500 + Random.nextLong(3500)
            lightningAlpha = 1f
            lightningX = Random.nextFloat() * wf * 0.6f + wf * 0.2f

            // 生成鋸齒閃電路徑
            lightningPath.reset()
            lightningPath.moveTo(lightningX, 0f)
            var ly = 0f
            var lx = lightningX
            while (ly < hf) {
                ly += (2f + Random.nextFloat() * 4f) * density
                lx += (Random.nextFloat() - 0.5f) * 8f * density
                lightningPath.lineTo(lx, ly)

                // 分支
                if (Random.nextFloat() < 0.25f) {
                    val branchLen = (5f + Random.nextFloat() * 10f) * density
                    val branchAngle = (Random.nextFloat() - 0.5f) * 1.2f
                    val bx = lx + cos(branchAngle.toDouble()).toFloat() * branchLen
                    val by = ly + sin(branchAngle.toDouble()).toFloat() * branchLen * 0.5f + branchLen * 0.5f
                    // 用額外的線段畫分支（存在 path 裡）
                    lightningPath.moveTo(lx, ly)
                    lightningPath.lineTo(bx, by)
                    lightningPath.moveTo(lx, ly)
                }
            }
        }

        // 繪製閃電
        if (lightningAlpha > 0.01f) {
            // 全畫面閃光
            val flashAlpha = (lightningAlpha * 60).toInt().coerceIn(0, 60)
            paint.color = Color.argb(flashAlpha, 200, 180, 255)
            canvas.drawRect(0f, 0f, wf, hf, paint)

            // 閃電主線
            pathPaint.color = Color.argb((lightningAlpha * 220).toInt(), 200, 180, 255)
            pathPaint.strokeWidth = 2f * density * lightningAlpha
            pathPaint.style = Paint.Style.STROKE
            canvas.drawPath(lightningPath, pathPaint)

            // 閃電光暈
            pathPaint.color = Color.argb((lightningAlpha * 80).toInt(), 160, 120, 255)
            pathPaint.strokeWidth = 5f * density * lightningAlpha
            canvas.drawPath(lightningPath, pathPaint)

            lightningAlpha *= 0.88f // 快速衰減
        }

        // 底部電弧粒子
        repeat(3) { i ->
            val sparkX = (sin((t * 3f + i * 2.1f).toDouble()) * wf * 0.3f + wf * 0.5f).toFloat()
            val sparkY = hf * (0.6f + Random.nextFloat() * 0.3f)
            val sparkAlpha = (sin((t * 5f + i).toDouble()) * 60 + 60).toInt().coerceIn(10, 120)
            paint.color = Color.argb(sparkAlpha, 180, 140, 255)
            canvas.drawCircle(sparkX, sparkY, (1f + Random.nextFloat()) * density, paint)
        }

        return true
    }

    companion object {
        private const val PI2 = (Math.PI * 2).toFloat()
    }
}

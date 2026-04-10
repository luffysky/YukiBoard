package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.service.KeyboardSoundEngine
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlin.math.*

/**
 * 音樂面板 — 鋼琴鍵盤 + 曲庫 + 樂器選擇。
 *
 * 兩個 Tab：
 *  1. 彈奏（Piano UI，多指觸控，不輸入文字）
 *  2. 曲庫（歌曲列表，自動彈奏 + 鍵盤高亮）
 *
 * 特色：
 *  - 真實鋼琴鍵排列（白鍵+黑鍵）
 *  - 可左右滑動切換八度
 *  - 頂部樂器選擇器
 *  - 彈奏時按鍵動畫（波紋 + 發光）
 *  - 曲庫自動播放，鍵盤同步亮燈
 *  - 錄音回放功能
 */
class MusicPanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun dpf(v: Float) = v * density

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var soundEngine: KeyboardSoundEngine? = null
    var onImportMidi: (() -> Unit)? = null

    /** 從外部匯入的曲目 */
    private val importedSongs = mutableListOf<Song>()

    fun addImportedSong(song: Song) {
        importedSongs.add(0, song)
        invalidate()
    }

    private fun allSongs(): List<Song> = importedSongs + songLibrary

    // ── Tab ──
    private enum class Tab { PLAY, LIBRARY }
    private var currentTab = Tab.PLAY

    // ── 樂器 ──
    private val instruments = arrayOf(
        "鋼琴" to KeyboardSoundEngine.ToneStyle.PIANO,
        "音樂盒" to KeyboardSoundEngine.ToneStyle.MUSIC_BOX,
        "吉他" to KeyboardSoundEngine.ToneStyle.GUITAR,
        "豎琴" to KeyboardSoundEngine.ToneStyle.HARP,
        "風琴" to KeyboardSoundEngine.ToneStyle.ORGAN,
        "木琴" to KeyboardSoundEngine.ToneStyle.MARIMBA,
        "柔和" to KeyboardSoundEngine.ToneStyle.SOFT,
    )
    private var selectedInstrument = 0
    private val currentStyle get() = instruments[selectedInstrument].second

    // ── 鋼琴鍵盤 ──
    // 一個八度 = 7 白鍵 + 5 黑鍵
    // 顯示 2 個八度（14 白鍵），可左右滑動換八度
    private var baseOctave = 3  // C3 開始（比中央C低一個八度，涵蓋更多低音）
    private val visibleOctaves = 3
    private val whiteKeysVisible get() = visibleOctaves * 7

    // 每個白鍵的音符偏移（相對 C 的半音數）
    private val whiteKeyOffsets = intArrayOf(0, 2, 4, 5, 7, 9, 11) // C D E F G A B
    // 黑鍵：在白鍵之間（C#=1, D#=3, F#=6, G#=8, A#=10）
    // 黑鍵相對位置：在第 0,1,3,4,5 個白鍵右上方
    private val blackKeyPositions = intArrayOf(0, 1, 3, 4, 5) // 在這幾個白鍵右邊有黑鍵
    private val blackKeyOffsets = intArrayOf(1, 3, 6, 8, 10)

    // 觸控追蹤（多指）
    private data class TouchedKey(val semitone: Int, val isBlack: Boolean, val pointerId: Int)
    private val touchedKeys = mutableMapOf<Int, TouchedKey>() // pointerId → key

    // 按鍵動畫
    private data class KeyGlow(val semitone: Int, var alpha: Float, val startTime: Long)
    private val keyGlows = mutableListOf<KeyGlow>()

    // ── 曲庫 ──
    data class Song(val name: String, val artist: String, val bpm: Int, val notes: List<SongNote>)
    data class SongNote(val semitone: Int, val startMs: Long, val durationMs: Long)

    private val songLibrary = buildSongLibrary()
    private var playingSong: Song? = null
    private var songStartTime = 0L
    private var songNoteIndex = 0
    private val songHandler = Handler(Looper.getMainLooper())
    private var libraryScrollY = 0f
    private var libraryDragStartY = 0f
    private var libraryDragging = false
    private var libraryTouchDownY = 0f

    // ── 錄音 ──
    private var isRecording = false
    private var recordStartTime = 0L
    private val recordedNotes = mutableListOf<SongNote>()
    private var isPlayingRecording = false

    // ── 繪製 ──
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 佈局區域
    private var tabBarH = 0f
    private var instrumentBarH = 0f
    private var controlBarH = 0f
    private var pianoTop = 0f
    private var pianoH = 0f

    // 八度滑動
    private var octaveScrollX = 0f
    private var octaveScrollStartX = 0f
    private var isOctaveScrolling = false
    private var octaveTouchDownX = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let { if (it > 0) it else resources.displayMetrics.widthPixels }
        val screenH = resources.displayMetrics.heightPixels
        val isLandscape = resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        // 橫向時使用固定 dp 避免太高擠壓應用，直向時用比例
        val h = if (isLandscape) dp(180).coerceAtMost((screenH * 0.55f).toInt())
                else (screenH * 0.38f).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 背景
        bgPaint.color = theme.effectivePanelBackground()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        tabBarH = dpf(36f)
        instrumentBarH = dpf(32f)
        controlBarH = dpf(28f)

        // 1) Tab 列
        drawTabBar(canvas, w)

        // 2) 根據 Tab 畫不同內容
        when (currentTab) {
            Tab.PLAY -> {
                drawInstrumentBar(canvas, w)
                drawControlBar(canvas, w)
                pianoTop = tabBarH + instrumentBarH + controlBarH
                pianoH = h - pianoTop
                drawPianoKeys(canvas, w, pianoTop, pianoH)
            }
            Tab.LIBRARY -> {
                drawLibrary(canvas, w, h)
            }
        }

        // 動畫持續
        if (keyGlows.isNotEmpty() || playingSong != null || isPlayingRecording) {
            postInvalidateOnAnimation()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Tab 列
    // ═══════════════════════════════════════════════════════════

    private fun drawTabBar(canvas: Canvas, w: Float) {
        bgPaint.color = darken(theme.effectivePanelBackground(), 0.85f)
        canvas.drawRect(0f, 0f, w, tabBarH, bgPaint)

        val tabs = arrayOf("🎹 彈奏", "📚 曲庫")
        val tabW = w / tabs.size
        tabs.forEachIndexed { i, label ->
            val isActive = (i == 0 && currentTab == Tab.PLAY) || (i == 1 && currentTab == Tab.LIBRARY)
            textPaint.textSize = dpf(13f)
            textPaint.color = if (isActive) theme.effectiveAccentColor() else theme.effectiveToolbarIconColor()
            textPaint.isFakeBoldText = isActive
            canvas.drawText(label, tabW * i + tabW / 2, tabBarH / 2 + textPaint.textSize * 0.35f, textPaint)
            textPaint.isFakeBoldText = false

            // 底部指示線
            if (isActive) {
                tabPaint.color = theme.effectiveAccentColor()
                val lineW = tabW * 0.5f
                val lineX = tabW * i + (tabW - lineW) / 2
                canvas.drawRoundRect(lineX, tabBarH - dpf(2.5f), lineX + lineW, tabBarH, dpf(1.5f), dpf(1.5f), tabPaint)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 樂器選擇列
    // ═══════════════════════════════════════════════════════════

    private fun drawInstrumentBar(canvas: Canvas, w: Float) {
        val y = tabBarH
        bgPaint.color = darken(theme.effectivePanelBackground(), 0.9f)
        canvas.drawRect(0f, y, w, y + instrumentBarH, bgPaint)

        val btnW = w / instruments.size
        instruments.forEachIndexed { i, (name, _) ->
            val isActive = i == selectedInstrument
            textPaint.textSize = dpf(11f)
            textPaint.color = if (isActive) theme.effectiveAccentColor() else (theme.effectiveToolbarIconColor() and 0x00FFFFFF or 0x99000000.toInt())

            if (isActive) {
                tabPaint.color = (theme.effectiveAccentColor() and 0x00FFFFFF) or 0x25000000
                val cx = btnW * i + btnW / 2
                canvas.drawRoundRect(cx - btnW * 0.45f, y + dpf(3f), cx + btnW * 0.45f, y + instrumentBarH - dpf(3f),
                    dpf(10f), dpf(10f), tabPaint)
            }

            canvas.drawText(name, btnW * i + btnW / 2, y + instrumentBarH / 2 + textPaint.textSize * 0.35f, textPaint)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 控制列（八度 / 錄音）
    // ═══════════════════════════════════════════════════════════

    private fun drawControlBar(canvas: Canvas, w: Float) {
        val y = tabBarH + instrumentBarH
        bgPaint.color = darken(theme.effectivePanelBackground(), 0.95f)
        canvas.drawRect(0f, y, w, y + controlBarH, bgPaint)

        textPaint.textSize = dpf(11f)

        // 左：八度 -
        textPaint.color = if (baseOctave > 0) theme.effectiveToolbarIconColor() else (theme.effectiveToolbarIconColor() and 0x00FFFFFF or 0x40000000)
        canvas.drawText("◀", dpf(20f), y + controlBarH / 2 + textPaint.textSize * 0.35f, textPaint)

        // 中：顯示八度
        textPaint.color = theme.effectiveToolbarIconColor()
        val octaveLabel = "C${baseOctave} - B${baseOctave + visibleOctaves - 1}"
        canvas.drawText(octaveLabel, w * 0.3f, y + controlBarH / 2 + textPaint.textSize * 0.35f, textPaint)

        // 右：八度 +
        textPaint.color = if (baseOctave + visibleOctaves < 8) theme.effectiveToolbarIconColor() else (theme.effectiveToolbarIconColor() and 0x00FFFFFF or 0x40000000)
        canvas.drawText("▶", dpf(60f) + textPaint.measureText(octaveLabel), y + controlBarH / 2 + textPaint.textSize * 0.35f, textPaint)

        // 錄音按鈕
        val recX = w - dpf(60f)
        val recColor = if (isRecording) 0xFFFF4444.toInt() else theme.effectiveToolbarIconColor()
        textPaint.color = recColor
        val recLabel = if (isRecording) "⏹ 停止" else "⏺ 錄音"
        canvas.drawText(recLabel, recX, y + controlBarH / 2 + textPaint.textSize * 0.35f, textPaint)

        // 回放按鈕（有錄音時）
        if (recordedNotes.isNotEmpty() && !isRecording) {
            val playX = w - dpf(120f)
            textPaint.color = if (isPlayingRecording) 0xFF4CAF50.toInt() else theme.effectiveToolbarIconColor()
            val playLabel = if (isPlayingRecording) "⏹" else "▶ 回放"
            canvas.drawText(playLabel, playX, y + controlBarH / 2 + textPaint.textSize * 0.35f, textPaint)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 鋼琴鍵盤繪製
    // ═══════════════════════════════════════════════════════════

    private fun drawPianoKeys(canvas: Canvas, w: Float, top: Float, h: Float) {
        val whiteKeyW = w / whiteKeysVisible
        val blackKeyW = whiteKeyW * 0.6f
        val blackKeyH = h * 0.58f
        val now = SystemClock.uptimeMillis()

        // 清理過期的 glow
        keyGlows.removeAll { now - it.startTime > 600 }

        // 畫白鍵
        for (i in 0 until whiteKeysVisible) {
            val octIdx = i / 7
            val noteIdx = i % 7
            val octave = baseOctave + octIdx
            val semitone = (octave - 4) * 12 + whiteKeyOffsets[noteIdx] // A4 = 0 → C4 = -9
            // 修正：以 C 為基準，C4 = semitone -9 relative to A4
            val semiRelA4 = (octave * 12 + whiteKeyOffsets[noteIdx]) - (4 * 12 + 9) // C0=0, A4=57

            val x = i * whiteKeyW
            val isPressed = touchedKeys.values.any { it.semitone == semiRelA4 && !it.isBlack }
            val isSongHighlight = isHighlighted(semiRelA4)

            // 白鍵底色
            keyPaint.color = when {
                isPressed -> blendColor(0xFFFFFFFF.toInt(), theme.effectiveAccentColor(), 0.3f)
                isSongHighlight -> blendColor(0xFFFFFFFF.toInt(), 0xFF4CAF50.toInt(), 0.25f)
                else -> 0xFFFAFAFA.toInt()
            }
            val keyRect = RectF(x + 1f, top, x + whiteKeyW - 1f, top + h - 1f)
            canvas.drawRoundRect(keyRect, dpf(2f), dpf(4f), keyPaint)

            // 白鍵邊框
            keyPaint.color = 0xFFCCCCCC.toInt()
            keyPaint.style = Paint.Style.STROKE
            keyPaint.strokeWidth = 1f
            canvas.drawRoundRect(keyRect, dpf(2f), dpf(4f), keyPaint)
            keyPaint.style = Paint.Style.FILL

            // 發光動畫
            val glow = keyGlows.find { it.semitone == semiRelA4 }
            if (glow != null) {
                val age = (now - glow.startTime) / 600f
                val glowAlpha = ((1f - age) * 80).toInt().coerceIn(0, 80)
                glowPaint.color = (theme.effectiveAccentColor() and 0x00FFFFFF) or (glowAlpha shl 24)
                canvas.drawRoundRect(keyRect, dpf(2f), dpf(4f), glowPaint)
            }

            // 音名標記（最下面）
            val noteName = arrayOf("C", "D", "E", "F", "G", "A", "B")[noteIdx]
            if (noteIdx == 0) { // 只標 C
                textPaint.textSize = dpf(9f)
                textPaint.color = 0xFF999999.toInt()
                canvas.drawText("$noteName$octave", x + whiteKeyW / 2, top + h - dpf(6f), textPaint)
            }
        }

        // 畫黑鍵
        for (i in 0 until whiteKeysVisible) {
            val octIdx = i / 7
            val noteIdx = i % 7

            // 檢查這個白鍵右邊是否有黑鍵
            val bkIdx = blackKeyPositions.indexOf(noteIdx)
            if (bkIdx < 0) continue

            val octave = baseOctave + octIdx
            val semiRelA4 = (octave * 12 + blackKeyOffsets[bkIdx]) - (4 * 12 + 9)

            val x = (i + 1) * whiteKeyW - blackKeyW / 2
            val isPressed = touchedKeys.values.any { it.semitone == semiRelA4 && it.isBlack }
            val isSongHighlight = isHighlighted(semiRelA4)

            // 黑鍵陰影
            keyPaint.color = 0x33000000
            canvas.drawRoundRect(x - 1f, top, x + blackKeyW + 1f, top + blackKeyH + dpf(2f),
                dpf(2f), dpf(3f), keyPaint)

            // 黑鍵主體
            keyPaint.color = when {
                isPressed -> blendColor(0xFF1A1A1A.toInt(), theme.effectiveAccentColor(), 0.4f)
                isSongHighlight -> blendColor(0xFF1A1A1A.toInt(), 0xFF4CAF50.toInt(), 0.3f)
                else -> 0xFF1A1A1A.toInt()
            }
            val bkRect = RectF(x, top, x + blackKeyW, top + blackKeyH)
            canvas.drawRoundRect(bkRect, dpf(2f), dpf(3f), keyPaint)

            // 黑鍵高光
            keyPaint.color = 0x15FFFFFF
            canvas.drawRoundRect(x + dpf(2f), top + dpf(1f), x + blackKeyW - dpf(2f), top + blackKeyH * 0.7f,
                dpf(1f), dpf(1f), keyPaint)

            // 發光動畫
            val glow = keyGlows.find { it.semitone == semiRelA4 }
            if (glow != null) {
                val age = (now - glow.startTime) / 600f
                val glowAlpha = ((1f - age) * 100).toInt().coerceIn(0, 100)
                glowPaint.color = (theme.effectiveAccentColor() and 0x00FFFFFF) or (glowAlpha shl 24)
                canvas.drawRoundRect(bkRect, dpf(2f), dpf(3f), glowPaint)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 曲庫列表
    // ═══════════════════════════════════════════════════════════

    private fun drawLibrary(canvas: Canvas, w: Float, h: Float) {
        val listTop = tabBarH
        val listH = h - listTop

        // 標題 + 匯入按鈕
        bgPaint.color = darken(theme.effectivePanelBackground(), 0.9f)
        canvas.drawRect(0f, listTop, w, listTop + dpf(28f), bgPaint)
        textPaint.textSize = dpf(12f)
        textPaint.color = theme.effectiveToolbarIconColor()
        canvas.drawText("選擇歌曲自動彈奏", w * 0.4f, listTop + dpf(18f), textPaint)
        // 匯入 MIDI 按鈕
        textPaint.color = theme.effectiveAccentColor()
        textPaint.textSize = dpf(11f)
        canvas.drawText("📂 匯入MIDI", w - dpf(50f), listTop + dpf(18f), textPaint)

        // 歌曲列表
        val itemH = dpf(52f)
        val contentTop = listTop + dpf(28f)

        canvas.save()
        canvas.clipRect(0f, contentTop, w, h)
        canvas.translate(0f, -libraryScrollY)

        allSongs().forEachIndexed { i, song ->
            val y = contentTop + i * itemH
            val isPlaying = playingSong == song

            // 卡片背景
            bgPaint.color = if (isPlaying) (theme.effectiveAccentColor() and 0x00FFFFFF or 0x20000000)
                           else if (i % 2 == 0) darken(theme.effectivePanelBackground(), 0.95f)
                           else theme.effectivePanelBackground()
            canvas.drawRoundRect(dpf(8f), y + dpf(2f), w - dpf(8f), y + itemH - dpf(2f),
                dpf(6f), dpf(6f), bgPaint)

            // 播放圖示
            textPaint.textSize = dpf(18f)
            textPaint.color = if (isPlaying) 0xFF4CAF50.toInt() else theme.effectiveAccentColor()
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(if (isPlaying) "⏹" else "▶", dpf(30f), y + itemH / 2 + dpf(7f), textPaint)

            // 歌名
            textPaint.textSize = dpf(13f)
            textPaint.color = if (isPlaying) theme.effectiveAccentColor() else theme.effectiveToolbarIconColor()
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(song.name, dpf(52f), y + dpf(20f), textPaint)

            // 藝人 + BPM
            textPaint.textSize = dpf(10f)
            textPaint.color = (theme.effectiveToolbarIconColor() and 0x00FFFFFF) or 0x80000000.toInt()
            canvas.drawText("${song.artist}  ♩=${song.bpm}", dpf(52f), y + dpf(36f), textPaint)

            // 音符數
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = dpf(10f)
            textPaint.color = (theme.effectiveToolbarIconColor() and 0x00FFFFFF) or 0x60000000
            canvas.drawText("${song.notes.size} 音符", w - dpf(16f), y + dpf(28f), textPaint)
        }

        canvas.restore()
        textPaint.textAlign = Paint.Align.CENTER // reset
    }

    // ═══════════════════════════════════════════════════════════
    // 觸控
    // ═══════════════════════════════════════════════════════════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val px = event.getX(idx)
                val py = event.getY(idx)
                val pid = event.getPointerId(idx)

                // Tab 列點擊
                if (py < tabBarH) {
                    val tabW = width / 2f
                    currentTab = if (px < tabW) Tab.PLAY else Tab.LIBRARY
                    invalidate()
                    return true
                }

                when (currentTab) {
                    Tab.PLAY -> {
                        // 樂器列
                        if (py in tabBarH..(tabBarH + instrumentBarH)) {
                            val btnW = width.toFloat() / instruments.size
                            val idx2 = (px / btnW).toInt().coerceIn(0, instruments.size - 1)
                            selectedInstrument = idx2
                            invalidate()
                            return true
                        }
                        // 控制列
                        if (py in (tabBarH + instrumentBarH)..(tabBarH + instrumentBarH + controlBarH)) {
                            handleControlTap(px, py)
                            return true
                        }
                        // 鋼琴區
                        if (py > pianoTop) {
                            handlePianoDown(px, py, pid)
                        }
                    }
                    Tab.LIBRARY -> {
                        libraryTouchDownY = py
                        libraryDragging = false
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                when (currentTab) {
                    Tab.PLAY -> {
                        // 多指追蹤：更新每個指頭對應的琴鍵
                        for (i in 0 until event.pointerCount) {
                            val pid = event.getPointerId(i)
                            val px = event.getX(i)
                            val py = event.getY(i)
                            if (py > pianoTop) {
                                val key = hitTestKey(px, py)
                                val existing = touchedKeys[pid]
                                if (key != null && (existing == null || existing.semitone != key.semitone)) {
                                    touchedKeys[pid] = key
                                    playNote(key.semitone)
                                }
                            }
                        }
                        invalidate()
                    }
                    Tab.LIBRARY -> {
                        val dy = event.y - libraryTouchDownY
                        if (abs(dy) > dpf(8f)) {
                            libraryDragging = true
                            libraryScrollY = (libraryScrollY - dy).coerceAtLeast(0f)
                            libraryTouchDownY = event.y
                            invalidate()
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                touchedKeys.remove(pid)
                invalidate()

                // 曲庫點擊
                if (currentTab == Tab.LIBRARY && !libraryDragging) {
                    handleLibraryTap(event.getX(idx), event.getY(idx))
                }
                libraryDragging = false
            }

            MotionEvent.ACTION_CANCEL -> {
                touchedKeys.clear()
                libraryDragging = false
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ═══════════════════════════════════════════════════════════
    // 鍵盤互動
    // ═══════════════════════════════════════════════════════════

    private fun handlePianoDown(px: Float, py: Float, pointerId: Int) {
        val key = hitTestKey(px, py) ?: return
        touchedKeys[pointerId] = key
        playNote(key.semitone)
        invalidate()
    }

    private fun hitTestKey(px: Float, py: Float): TouchedKey? {
        val whiteKeyW = width.toFloat() / whiteKeysVisible
        val blackKeyW = whiteKeyW * 0.6f
        val blackKeyH = pianoH * 0.58f

        // 先檢查黑鍵（在上層）
        if (py < pianoTop + blackKeyH) {
            for (i in 0 until whiteKeysVisible) {
                val noteIdx = i % 7
                val bkIdx = blackKeyPositions.indexOf(noteIdx)
                if (bkIdx < 0) continue

                val octave = baseOctave + i / 7
                val bkX = (i + 1) * whiteKeyW - blackKeyW / 2
                if (px >= bkX && px <= bkX + blackKeyW) {
                    val semiRelA4 = (octave * 12 + blackKeyOffsets[bkIdx]) - (4 * 12 + 9)
                    return TouchedKey(semiRelA4, true, -1)
                }
            }
        }

        // 白鍵
        val whiteIdx = (px / whiteKeyW).toInt().coerceIn(0, whiteKeysVisible - 1)
        val octave = baseOctave + whiteIdx / 7
        val noteIdx = whiteIdx % 7
        val semiRelA4 = (octave * 12 + whiteKeyOffsets[noteIdx]) - (4 * 12 + 9)
        return TouchedKey(semiRelA4, false, -1)
    }

    private fun playNote(semitone: Int) {
        val freq = 440f * 2f.pow(semitone / 12f)
        soundEngine?.playFreq(freq, 350, currentStyle)
        keyGlows.add(KeyGlow(semitone, 1f, SystemClock.uptimeMillis()))

        // 錄音
        if (isRecording) {
            val elapsed = SystemClock.uptimeMillis() - recordStartTime
            recordedNotes.add(SongNote(semitone, elapsed, 300))
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 控制列
    // ═══════════════════════════════════════════════════════════

    private fun handleControlTap(px: Float, py: Float) {
        val w = width.toFloat()
        // 八度 ◀
        if (px < dpf(40f)) {
            if (baseOctave > 0) { baseOctave--; invalidate() }
            return
        }
        // 八度 ▶（約在 octave label 右邊）
        if (px in dpf(40f)..dpf(120f)) {
            // 對照表區域的右邊
        }
        if (px > dpf(80f) && px < dpf(140f)) {
            if (baseOctave + visibleOctaves < 8) { baseOctave++; invalidate() }
            return
        }

        // 錄音
        if (px > w - dpf(90f) && px < w - dpf(30f)) {
            if (isRecording) {
                isRecording = false
            } else {
                isRecording = true
                recordStartTime = SystemClock.uptimeMillis()
                recordedNotes.clear()
            }
            invalidate()
            return
        }

        // 回放
        if (px > w - dpf(150f) && px < w - dpf(90f) && recordedNotes.isNotEmpty()) {
            if (isPlayingRecording) {
                stopPlayback()
            } else {
                playRecording()
            }
            return
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 曲庫
    // ═══════════════════════════════════════════════════════════

    private fun handleLibraryTap(px: Float, py: Float) {
        // 匯入按鈕（標題列右邊）
        if (py < tabBarH + dpf(28f) && px > width * 0.65f) {
            onImportMidi?.invoke()
            return
        }
        val contentTop = tabBarH + dpf(28f)
        val itemH = dpf(52f)
        val adjustedY = py + libraryScrollY - contentTop
        if (adjustedY < 0) return
        val idx = (adjustedY / itemH).toInt()
        val songs = allSongs()
        if (idx < 0 || idx >= songs.size) return

        val song = songs[idx]
        if (playingSong == song) {
            stopSong()
        } else {
            playSong(song)
        }
    }

    private fun playSong(song: Song) {
        stopSong()
        playingSong = song
        songStartTime = SystemClock.uptimeMillis()
        songNoteIndex = 0
        scheduleSongNote()
        invalidate()
    }

    private fun scheduleSongNote() {
        val song = playingSong ?: return
        if (songNoteIndex >= song.notes.size) {
            // 歌曲結束
            songHandler.postDelayed({ stopSong() }, 500)
            return
        }
        val note = song.notes[songNoteIndex]
        val elapsed = SystemClock.uptimeMillis() - songStartTime
        val delay = (note.startMs - elapsed).coerceAtLeast(0)

        songHandler.postDelayed({
            if (!isAttachedToWindow || playingSong != song) return@postDelayed
            playNote(note.semitone)
            songNoteIndex++
            scheduleSongNote()
        }, delay)
    }

    private fun stopSong() {
        playingSong = null
        songHandler.removeCallbacksAndMessages(null)
        invalidate()
    }

    private fun playRecording() {
        if (recordedNotes.isEmpty()) return
        isPlayingRecording = true
        val startTime = SystemClock.uptimeMillis()
        recordedNotes.forEachIndexed { _, note ->
            songHandler.postDelayed({
                if (!isAttachedToWindow || !isPlayingRecording) return@postDelayed
                playNote(note.semitone)
            }, note.startMs)
        }
        val totalDur = recordedNotes.maxOf { it.startMs + it.durationMs }
        songHandler.postDelayed({
            isPlayingRecording = false
            invalidate()
        }, totalDur + 100)
        invalidate()
    }

    private fun stopPlayback() {
        isPlayingRecording = false
        songHandler.removeCallbacksAndMessages(null)
        invalidate()
    }

    // 曲庫歌曲自動彈奏高亮
    private val highlightedNotes = mutableSetOf<Int>()
    private fun isHighlighted(semitone: Int): Boolean {
        val song = playingSong ?: return false
        val elapsed = SystemClock.uptimeMillis() - songStartTime
        return song.notes.any { it.semitone == semitone && elapsed >= it.startMs && elapsed <= it.startMs + it.durationMs + 200 }
    }

    // ═══════════════════════════════════════════════════════════
    // 內建曲庫
    // ═══════════════════════════════════════════════════════════

    private fun buildSongLibrary(): List<Song> {
        // semitone 相對 A4(440Hz)=0：C4=-9, D4=-7, E4=-5, F4=-4, G4=-2, A4=0, B4=2
        // C5=3, D5=5, E5=7, F5=8, G5=10, A5=12, B5=14
        // Eb4=-6, Bb4=1, F#4=-3, Ab4=-1, Gb5=9
        val c4=-9; val d4=-7; val eb4=-6; val e4=-5; val f4=-4; val fs4=-3
        val g4=-2; val ab4=-1; val a4=0; val bb4=1; val b4=2
        val c5=3; val d5=5; val eb5=6; val e5=7; val f5=8
        val g5=10; val a5=12

        fun n(semi: Int, beats: Float) = semi to beats

        fun seq(bpm: Int, notes: List<Pair<Int, Float>>): List<SongNote> {
            val beatMs = 60000L / bpm
            var t = 0L
            return notes.map { (semi, beats) ->
                val dur = (beatMs * beats * 0.9f).toLong()  // 留 10% 間隔避免黏音
                val gap = (beatMs * beats).toLong()
                SongNote(semi, t, dur).also { t += gap }
            }
        }

        return listOf(
            // 1. 小星星（完整）
            Song("小星星", "Mozart", 110, seq(110, listOf(
                n(c4,1f), n(c4,1f), n(g4,1f), n(g4,1f), n(a4,1f), n(a4,1f), n(g4,2f),
                n(f4,1f), n(f4,1f), n(e4,1f), n(e4,1f), n(d4,1f), n(d4,1f), n(c4,2f),
                n(g4,1f), n(g4,1f), n(f4,1f), n(f4,1f), n(e4,1f), n(e4,1f), n(d4,2f),
                n(g4,1f), n(g4,1f), n(f4,1f), n(f4,1f), n(e4,1f), n(e4,1f), n(d4,2f),
                n(c4,1f), n(c4,1f), n(g4,1f), n(g4,1f), n(a4,1f), n(a4,1f), n(g4,2f),
                n(f4,1f), n(f4,1f), n(e4,1f), n(e4,1f), n(d4,1f), n(d4,1f), n(c4,2f),
            ))),
            // 2. 生日快樂（完整，3/4拍）
            Song("生日快樂", "Traditional", 140, seq(140, listOf(
                n(g4,0.5f), n(g4,0.5f), n(a4,1f), n(g4,1f), n(c5,1f), n(b4,2f),
                n(g4,0.5f), n(g4,0.5f), n(a4,1f), n(g4,1f), n(d5,1f), n(c5,2f),
                n(g4,0.5f), n(g4,0.5f), n(g5,1f), n(e5,1f), n(c5,1f), n(b4,1f), n(a4,2f),
                n(f5,0.5f), n(f5,0.5f), n(e5,1f), n(c5,1f), n(d5,1f), n(c5,2f),
            ))),
            // 3. 小蜜蜂（完整）
            Song("小蜜蜂", "German Folk", 120, seq(120, listOf(
                n(e5,1f), n(d5,1f), n(c5,1f), n(d5,1f), n(e5,1f), n(e5,1f), n(e5,2f),
                n(d5,1f), n(d5,1f), n(d5,2f), n(e5,1f), n(g5,1f), n(g5,2f),
                n(e5,1f), n(d5,1f), n(c5,1f), n(d5,1f), n(e5,1f), n(e5,1f), n(e5,1f), n(e5,1f),
                n(d5,1f), n(d5,1f), n(e5,1f), n(d5,1f), n(c5,4f),
            ))),
            // 4. 瑪莉有隻小綿羊（完整）
            Song("瑪莉有隻小綿羊", "Traditional", 130, seq(130, listOf(
                n(e5,1f), n(d5,1f), n(c5,1f), n(d5,1f), n(e5,1f), n(e5,1f), n(e5,2f),
                n(d5,1f), n(d5,1f), n(d5,2f), n(e5,1f), n(e5,1f), n(e5,2f),
                n(e5,1f), n(d5,1f), n(c5,1f), n(d5,1f), n(e5,1f), n(e5,1f), n(e5,1f), n(e5,1f),
                n(d5,1f), n(d5,1f), n(e5,1f), n(d5,1f), n(c5,4f),
            ))),
            // 5. 歡樂頌（完整兩段）
            Song("歡樂頌", "Beethoven", 120, seq(120, listOf(
                n(e5,1f), n(e5,1f), n(f5,1f), n(g5,1f), n(g5,1f), n(f5,1f), n(e5,1f), n(d5,1f),
                n(c5,1f), n(c5,1f), n(d5,1f), n(e5,1f), n(e5,1.5f), n(d5,0.5f), n(d5,2f),
                n(e5,1f), n(e5,1f), n(f5,1f), n(g5,1f), n(g5,1f), n(f5,1f), n(e5,1f), n(d5,1f),
                n(c5,1f), n(c5,1f), n(d5,1f), n(e5,1f), n(d5,1.5f), n(c5,0.5f), n(c5,2f),
                n(d5,1f), n(d5,1f), n(e5,1f), n(c5,1f), n(d5,1f), n(e5,0.5f), n(f5,0.5f), n(e5,1f), n(c5,1f),
                n(d5,1f), n(e5,0.5f), n(f5,0.5f), n(e5,1f), n(d5,1f), n(c5,1f), n(d5,1f), n(g4,2f),
                n(e5,1f), n(e5,1f), n(f5,1f), n(g5,1f), n(g5,1f), n(f5,1f), n(e5,1f), n(d5,1f),
                n(c5,1f), n(c5,1f), n(d5,1f), n(e5,1f), n(d5,1.5f), n(c5,0.5f), n(c5,2f),
            ))),
            // 6. 天空之城（正確旋律，兩段）
            Song("天空之城", "久石讓", 80, seq(80, listOf(
                n(a4,0.5f), n(b4,0.5f),
                n(c5,1.5f), n(b4,0.5f), n(c5,1f), n(e5,1f),
                n(b4,3f), n(e4,0.5f), n(e4,0.5f),
                n(a4,1.5f), n(g4,0.5f), n(a4,1f), n(c5,1f),
                n(g4,3f), n(e4,0.5f), n(e4,0.5f),
                n(f4,1.5f), n(e4,0.5f), n(f4,1f), n(c5,1f),
                n(e4,3f), n(c4,0.5f), n(c4,0.5f),
                n(d4,1.5f), n(c4,0.5f), n(d4,1f), n(b4,1f),
                n(c4,3f),
                n(a4,0.5f), n(b4,0.5f),
                n(c5,1.5f), n(b4,0.5f), n(c5,1f), n(e5,1f),
                n(b4,3f), n(e4,0.5f), n(e4,0.5f),
                n(a4,1.5f), n(g4,0.5f), n(a4,1f), n(c5,1f),
                n(g4,3f),
            ))),
            // 7. 夢中的婚禮（Für Elise 主題）
            Song("乖乖聽話", "Beethoven", 72, seq(72, listOf(
                n(e5,0.5f), n(eb5,0.5f), n(e5,0.5f), n(eb5,0.5f), n(e5,0.5f), n(b4,0.5f), n(d5,0.5f), n(c5,0.5f),
                n(a4,1.5f), n(c4,0.5f), n(e4,0.5f), n(a4,0.5f),
                n(b4,1.5f), n(e4,0.5f), n(ab4,0.5f), n(b4,0.5f),
                n(c5,1.5f), n(e4,0.5f), n(e5,0.5f), n(eb5,0.5f),
                n(e5,0.5f), n(eb5,0.5f), n(e5,0.5f), n(b4,0.5f), n(d5,0.5f), n(c5,0.5f),
                n(a4,1.5f), n(c4,0.5f), n(e4,0.5f), n(a4,0.5f),
                n(b4,1.5f), n(e4,0.5f), n(c5,0.5f), n(b4,0.5f),
                n(a4,3f),
            ))),
            // 8. 卡農（Pachelbel 正確旋律）
            Song("卡農", "Pachelbel", 68, seq(68, listOf(
                n(e5,1f), n(d5,1f), n(c5,1f), n(b4,1f),
                n(a4,1f), n(g4,1f), n(a4,1f), n(b4,1f),
                n(c5,1f), n(b4,1f), n(a4,1f), n(g4,1f),
                n(f4,1f), n(e4,1f), n(f4,1f), n(g4,1f),
                n(a4,0.5f), n(g4,0.5f), n(a4,0.5f), n(b4,0.5f), n(c5,0.5f), n(b4,0.5f), n(c5,0.5f), n(d5,0.5f),
                n(e5,0.5f), n(d5,0.5f), n(e5,0.5f), n(c5,0.5f), n(d5,0.5f), n(c5,0.5f), n(d5,0.5f), n(b4,0.5f),
                n(c5,0.5f), n(b4,0.5f), n(c5,0.5f), n(a4,0.5f), n(b4,0.5f), n(a4,0.5f), n(b4,0.5f), n(g4,0.5f),
                n(a4,0.5f), n(g4,0.5f), n(a4,0.5f), n(f4,0.5f), n(g4,0.5f), n(f4,0.5f), n(g4,0.5f), n(a4,0.5f),
            ))),
            // 9. 兩隻老虎
            Song("兩隻老虎", "French Folk", 130, seq(130, listOf(
                n(c5,1f), n(d5,1f), n(e5,1f), n(c5,1f),
                n(c5,1f), n(d5,1f), n(e5,1f), n(c5,1f),
                n(e5,1f), n(f5,1f), n(g5,2f),
                n(e5,1f), n(f5,1f), n(g5,2f),
                n(g5,0.5f), n(a5,0.5f), n(g5,0.5f), n(f5,0.5f), n(e5,1f), n(c5,1f),
                n(g5,0.5f), n(a5,0.5f), n(g5,0.5f), n(f5,0.5f), n(e5,1f), n(c5,1f),
                n(c5,1f), n(g4,1f), n(c5,2f),
                n(c5,1f), n(g4,1f), n(c5,2f),
            ))),
            // 10. 小步舞曲 G 大調
            Song("小步舞曲", "Bach", 120, seq(120, listOf(
                n(d5,1f), n(g4,0.5f), n(a4,0.5f), n(b4,0.5f), n(c5,0.5f),
                n(d5,1f), n(g4,1f), n(g4,1f),
                n(e5,1f), n(c5,0.5f), n(d5,0.5f), n(e5,0.5f), n(f5,0.5f),
                n(g5,1f), n(g4,1f), n(g4,1f),
                n(c5,1f), n(d5,0.5f), n(c5,0.5f), n(b4,0.5f), n(a4,0.5f),
                n(b4,1f), n(c5,0.5f), n(b4,0.5f), n(a4,0.5f), n(g4,0.5f),
                n(a4,1f), n(b4,0.5f), n(a4,0.5f), n(g4,0.5f), n(fs4,0.5f),
                n(g4,3f),
            ))),
        )
    }

    // ═══════════════════════════════════════════════════════════
    // 工具
    // ═══════════════════════════════════════════════════════════

    private fun darken(color: Int, factor: Float): Int {
        val r = ((color shr 16 and 0xFF) * factor).toInt()
        val g = ((color shr 8 and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    private fun blendColor(c1: Int, c2: Int, ratio: Float): Int {
        val r = ((Color.red(c1) * (1 - ratio) + Color.red(c2) * ratio)).toInt()
        val g = ((Color.green(c1) * (1 - ratio) + Color.green(c2) * ratio)).toInt()
        val b = ((Color.blue(c1) * (1 - ratio) + Color.blue(c2) * ratio)).toInt()
        val a = ((Color.alpha(c1) * (1 - ratio) + Color.alpha(c2) * ratio)).toInt()
        return Color.argb(a, r, g, b)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }

    fun destroy() {
        stopSong()
        stopPlayback()
        songHandler.removeCallbacksAndMessages(null)
    }
}

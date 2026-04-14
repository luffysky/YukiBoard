package com.snowrealm.yukiboard.ui.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.snowrealm.yukiboard.input.router.CandidateSource

/**
 * KeyboardDrawDelegate — 從 KeyboardView 抽出的所有 draw* 方法。
 *
 * 使用方式：由 KeyboardView 建立並持有，在 onDraw 中委託呼叫。
 * 所有方法接收 Canvas 作為第一參數，並透過 view 存取 KeyboardView 的 internal 欄位。
 */
class KeyboardDrawDelegate(private val view: KeyboardView) {

    // ── Local caches for gradient background ──────────────────────────────────
    private var bgGradient: LinearGradient? = null
    private var bgGradientWidth = 0
    private var bgGradientHeight = 0
    private val bgPaint = Paint()

    // ── Alt-popup Paints (mirrored from KeyboardView) ─────────────────────────
    private val altBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val altCellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val altTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawBackground
    // ─────────────────────────────────────────────────────────────────────────

    fun drawBackground(canvas: Canvas) {
        if (view.hasCustomBackground) {
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            val alpha = view.bgOverlayAlpha.coerceIn(0, 255)
            canvas.drawColor(android.graphics.Color.argb(alpha, 0, 0, 0))
            return
        }

        // 動畫主題背景（星空/海洋/矩陣等）
        if (view.animStyle != com.snowrealm.yukiboard.ui.panel.ToolbarAnimRenderer.Style.NONE) {
            view.animRenderer?.draw(canvas, view.width, view.height, view.animStyle, android.os.SystemClock.uptimeMillis())
            return
        }

        val startColor = view.theme.backgroundGradientStartInt
        val endColor = view.theme.backgroundGradientEndInt
        if (startColor != null && endColor != null) {
            if (bgGradient == null || bgGradientWidth != view.width || bgGradientHeight != view.height) {
                bgGradientWidth = view.width
                bgGradientHeight = view.height
                bgGradient = LinearGradient(
                    0f, 0f, 0f, view.height.toFloat(),
                    startColor, endColor,
                    Shader.TileMode.CLAMP
                )
            }
            bgPaint.shader = bgGradient
            canvas.drawRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), bgPaint)
            bgPaint.shader = null
        } else {
            canvas.drawColor(view.theme.backgroundColorInt)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawCandidateBar
    // ─────────────────────────────────────────────────────────────────────────

    fun drawCandidateBar(canvas: Canvas) {
        val barRect = RectF(0f, 0f, view.width.toFloat(), view.candidateBarHeight)
        view.keyPaint.color = if (view.hasCustomBackground) 0x22000000 else view.theme.candidateBarColorInt
        canvas.drawRect(barRect, view.keyPaint)

        view.hasExpandArrow = false

        // 模式標示
        if (view.currentMode != KeyboardMode.NORMAL) {
            val modeTag = "${view.currentMode.icon} ${view.currentMode.displayName}"
            view.altHintPaint.color = view.theme.candidateSelectedColorInt
            view.altHintPaint.textSize = 10f * view.density
            view.altHintPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(modeTag, view.width.toFloat() - 8f * view.density, view.candidateBarHeight * 0.65f, view.altHintPaint)
            view.altHintPaint.textSize = 10f * view.density
        }

        // 模式切換提示
        val toast = view.modeToastText
        if (toast != null && System.currentTimeMillis() < view.modeToastEndTime) {
            view.candidatePaint.color = view.theme.candidateSelectedColorInt
            view.candidatePaint.textSize = 16f * view.density
            canvas.drawText(toast, view.width / 2f, view.candidateBarHeight * 0.65f, view.candidatePaint)
            view.candidatePaint.textSize = 15f * view.density
            return
        }

        // 語音辨識中指示
        val vLocale = view.voiceListeningLocale
        if (vLocale != null) {
            val langName = when {
                vLocale == "auto" -> "多語言"
                vLocale.startsWith("zh-TW") -> "中文(繁體)"
                vLocale.startsWith("zh-CN") -> "中文(簡體)"
                vLocale.startsWith("zh-HK") -> "粵語"
                vLocale.startsWith("ja") -> "日本語"
                vLocale.startsWith("ko") -> "한국어"
                vLocale.startsWith("es") -> "Español"
                vLocale.startsWith("fr") -> "Français"
                vLocale.startsWith("de") -> "Deutsch"
                vLocale.startsWith("pt") -> "Português"
                else -> "English"
            }
            val dots = ".".repeat(((System.currentTimeMillis() / 500) % 4).toInt())
            view.candidatePaint.color = view.theme.candidateSelectedColorInt
            view.candidatePaint.textSize = 15f * view.density
            canvas.drawText("🎤 $langName 辨識中$dots", view.width / 2f, view.candidateBarHeight * 0.65f, view.candidatePaint)
            view.postInvalidateDelayed(500)
            return
        }

        // composing text（左側固定區域）
        var fixedLeft = 4f * view.density
        if (view.composingText.isNotEmpty()) {
            view.composingPaint.color = view.theme.composingTextColorInt
            val displayText = "[${view.composingText}]"
            view.composingPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(displayText, fixedLeft, view.candidateBarHeight * 0.65f, view.composingPaint)
            fixedLeft += view.composingPaint.measureText(displayText) + 8f * view.density
        }

        // AUTO 模式提示（候選字為空且無組字時顯示）
        if (view.candidates.isEmpty() && view.composingText.isEmpty()
            && view.currentInputMode == com.snowrealm.yukiboard.input.router.InputMode.AUTO) {
            view.altHintPaint.color = (view.theme.candidateTextColorInt and 0x00FFFFFF) or 0x66000000
            view.altHintPaint.textSize = 12f * view.density
            view.altHintPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("已啟動智慧輸入法", fixedLeft, view.candidateBarHeight * 0.65f, view.altHintPaint)
            view.altHintPaint.textSize = 10f * view.density
            return
        }

        // 候選字區域（橫向捲動）
        if (view.candidates.isEmpty()) return

        val candPadH = 16f * view.density
        val candGap = 6f * view.density
        view.candidatePaint.textAlign = Paint.Align.CENTER
        view.candidatePaint.textSize = 16f * view.density

        // 計算每個候選字寬度並快取（供 touch 用，含前綴）
        val candWidths = view.candidates.mapIndexed { i, text ->
            val display = when {
                view.isSwipeCandidateMode && i == 0 -> "≈$text"
                else -> text
            }
            view.candidatePaint.measureText(display) + candPadH * 2
        }
        view.candWidthsCache = candWidths
        view.candFixedLeftCache = fixedLeft
        view.candGapCache = candGap

        val totalCandWidth = candWidths.sum() + candGap * (view.candidates.size - 1).coerceAtLeast(0)

        // 右側預留展開箭頭空間（候選 > 5 個時顯示）
        val showArrow = view.allCandidates.size > 5
        view.hasExpandArrow = showArrow
        val arrowSpace = if (showArrow) view.expandArrowWidth else 0f
        val scrollAreaWidth = view.width.toFloat() - fixedLeft - arrowSpace
        val maxScroll = (totalCandWidth - scrollAreaWidth).coerceAtLeast(0f)
        view.candidateScrollX = view.candidateScrollX.coerceIn(0f, maxScroll)

        // clip 候選字區域（不含箭頭區域）
        canvas.save()
        canvas.clipRect(fixedLeft, 0f, view.width.toFloat() - arrowSpace, view.candidateBarHeight)

        var drawX = fixedLeft - view.candidateScrollX
        view.candidates.forEachIndexed { i, text ->
            val cw = candWidths[i]
            val left = drawX
            val right = drawX + cw
            val cx = left + cw / 2f

            if (right > fixedLeft && left < view.width.toFloat() - arrowSpace) {
                // 候選字來源色彩（滑動模式統一淡紫色）
                val isHistory = view.candidateSourceMap[text] == CandidateSource.HISTORY
                val sourceColor = if (view.isSwipeCandidateMode) 0xFFB39DDB.toInt()
                else when (view.candidateSourceMap[text]) {
                    CandidateSource.CORRECTION -> 0xFFFF9933.toInt()  // 橙色：糾錯
                    CandidateSource.ADAPTIVE   -> 0xFF66CC88.toInt()  // 綠色：自適應
                    CandidateSource.PREDICTION -> 0xFF66AAFF.toInt()  // 藍色：預測
                    CandidateSource.USER_DICT  -> 0xFFFFD700.toInt()  // 金色：使用者詞庫
                    CandidateSource.SENTENCE   -> 0xFFBB88FF.toInt()  // 紫色：整句
                    CandidateSource.AI         -> 0xFFE8B84D.toInt()  // 金色：AI ✨
                    CandidateSource.HISTORY    -> 0 // 歷史候選用淡化主題色
                    else -> 0 // DICTIONARY 或無來源 → 用主題預設色
                }

                if (isHistory) {
                    // 歷史候選字：淡化顯示（alpha 50%）
                    view.candidatePaint.color = (view.theme.candidateTextColorInt and 0x00FFFFFF) or 0x80000000.toInt()
                } else if (i == 0 && view.candidates.size > 1) {
                    view.keyPaint.color = if (sourceColor != 0) sourceColor else view.theme.candidateSelectedColorInt
                    view.keyPaint.alpha = 40
                    val pill = RectF(left + 2 * view.density, 4 * view.density, right - 2 * view.density, view.candidateBarHeight - 4 * view.density)
                    canvas.drawRoundRect(pill, 6 * view.density, 6 * view.density, view.keyPaint)
                    view.keyPaint.alpha = 255
                    view.candidatePaint.color = if (sourceColor != 0) sourceColor else view.theme.candidateSelectedColorInt
                } else {
                    view.candidatePaint.color = if (sourceColor != 0) sourceColor else view.theme.candidateTextColorInt
                }
                // AI 候選字用加深底色 + 右上角小圓點標記，不加 ✨ 前綴
                if (view.candidateSourceMap[text] == CandidateSource.AI) {
                    view.keyPaint.color = 0xFFE8B84D.toInt()
                    view.keyPaint.alpha = 70
                    val pill = RectF(left + 2 * view.density, 4 * view.density, right - 2 * view.density, view.candidateBarHeight - 4 * view.density)
                    canvas.drawRoundRect(pill, 6 * view.density, 6 * view.density, view.keyPaint)
                    view.keyPaint.alpha = 255
                    // 右上角小圓點
                    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFE8B84D.toInt()
                        style = Paint.Style.FILL
                    }
                    canvas.drawCircle(right - 6f * view.density, 6f * view.density, 3f * view.density, dotPaint)
                }
                val displayText = when {
                    view.isSwipeCandidateMode && i == 0 -> "≈$text"
                    else -> text
                }
                canvas.drawText(displayText, cx, view.candidateBarHeight * 0.65f, view.candidatePaint)

                // 底部來源指示條（非字典來源才顯示）
                if (sourceColor != 0 && view.candidateSourceMap[text] != CandidateSource.AI) {
                    view.keyPaint.color = sourceColor
                    view.keyPaint.alpha = 180
                    val indicatorH = 2f * view.density
                    val indicatorW = (cw * 0.5f).coerceAtMost(20f * view.density)
                    canvas.drawRoundRect(
                        cx - indicatorW / 2f, view.candidateBarHeight - indicatorH - 1f * view.density,
                        cx + indicatorW / 2f, view.candidateBarHeight - 1f * view.density,
                        indicatorH, indicatorH, view.keyPaint
                    )
                    view.keyPaint.alpha = 255
                }

                if (i > 0) {
                    view.dividerPaint.color = (view.theme.candidateTextColorInt and 0x00FFFFFF) or 0x20000000
                    canvas.drawLine(left, 8 * view.density, left, view.candidateBarHeight - 8 * view.density, view.dividerPaint)
                }
            }

            drawX = right + candGap
        }

        // 候選列末尾推薦貼圖提示
        val stickerEmoji = view.recommendedStickerEmoji
        if (stickerEmoji != null && view.candidates.isNotEmpty()) {
            val hintW = 36f * view.density
            val hintLeft = drawX + 4f * view.density
            val hintRight = hintLeft + hintW
            val hintTop = 4f * view.density
            val hintBot = view.candidateBarHeight - 4f * view.density
            val hintRect = RectF(hintLeft, hintTop, hintRight, hintBot)
            // 柔和膠囊背景
            view.keyPaint.color = 0x20FF9800.toInt()
            canvas.drawRoundRect(hintRect, 8f * view.density, 8f * view.density, view.keyPaint)
            // 貼圖 emoji
            view.candidatePaint.textSize = 16f * view.density
            view.candidatePaint.color = 0xFFFFFFFF.toInt()
            view.candidatePaint.textAlign = Paint.Align.CENTER
            canvas.drawText("🖼", hintRect.centerX(), hintRect.centerY() + 6f * view.density, view.candidatePaint)
            view.candidatePaint.textSize = 15f * view.density
            // 記錄位置供觸控用（需 scroll 偏移修正）
            view.stickerHintRect = RectF(hintLeft + view.candidateScrollX, hintTop, hintRight + view.candidateScrollX, hintBot)
        } else {
            view.stickerHintRect = null
        }

        canvas.restore()

        // 右側漸層遮罩
        if (maxScroll > 0f) {
            val fadeW = 20f * view.density
            val fadeRight = view.width.toFloat() - arrowSpace
            val fadeLeft = fadeRight - fadeW
            val fadePaint = Paint()
            fadePaint.shader = android.graphics.LinearGradient(
                fadeLeft, 0f, fadeRight, 0f,
                0x00000000, (view.theme.candidateBarColorInt and 0x00FFFFFF) or 0xCC000000.toInt(),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(fadeLeft, 0f, fadeRight, view.candidateBarHeight, fadePaint)
        }

        // 展開箭頭 ▼
        if (showArrow) {
            val arrowLeft = view.width.toFloat() - arrowSpace
            // 分隔線
            view.dividerPaint.color = (view.theme.candidateTextColorInt and 0x00FFFFFF) or 0x30000000
            canvas.drawLine(arrowLeft, 6 * view.density, arrowLeft, view.candidateBarHeight - 6 * view.density, view.dividerPaint)
            // 箭頭
            view.candidatePaint.textAlign = Paint.Align.CENTER
            view.candidatePaint.textSize = 14f * view.density
            view.candidatePaint.color = view.theme.candidateTextColorInt
            val arrowText = if (view.isCandidateExpanded) "▲" else "▼"
            canvas.drawText(arrowText, arrowLeft + arrowSpace / 2f, view.candidateBarHeight * 0.65f, view.candidatePaint)
            view.candidatePaint.textSize = 15f * view.density
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawCandidateMagnifier
    // ─────────────────────────────────────────────────────────────────────────

    fun drawCandidateMagnifier(canvas: Canvas, index: Int) {
        val text = view.candidates.getOrNull(index) ?: return
        val bubbleW = 80f * view.density
        val bubbleH = 48f * view.density
        val cornerR = 10f * view.density

        var accum = view.candFixedLeftCache - view.candidateScrollX
        for (i in 0 until index) accum += (view.candWidthsCache.getOrNull(i) ?: 0f) + view.candGapCache
        val candCx = accum + (view.candWidthsCache.getOrNull(index) ?: 0f) / 2f
        val bubbleLeft = (candCx - bubbleW / 2f).coerceIn(0f, view.width - bubbleW)
        val bubbleTop = -bubbleH - 8f * view.density

        val bgPaintM = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = view.theme.keyColorInt }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = view.theme.candidateSelectedColorInt
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * view.density
        }
        val rect = RectF(bubbleLeft, bubbleTop, bubbleLeft + bubbleW, bubbleTop + bubbleH)
        canvas.drawRoundRect(rect, cornerR, cornerR, bgPaintM)
        canvas.drawRoundRect(rect, cornerR, cornerR, borderPaint)

        val magnifyPaint = Paint(view.candidatePaint).apply {
            textSize = 22f * view.density
            textAlign = Paint.Align.CENTER
            color = view.theme.candidateSelectedColorInt
            isFakeBoldText = true
        }
        canvas.drawText(text, rect.centerX(), rect.centerY() + magnifyPaint.textSize * 0.35f, magnifyPaint)

        val tail = Path()
        tail.moveTo(rect.centerX() - 6f * view.density, rect.bottom)
        tail.lineTo(rect.centerX(), rect.bottom + 6f * view.density)
        tail.lineTo(rect.centerX() + 6f * view.density, rect.bottom)
        tail.close()
        canvas.drawPath(tail, bgPaintM)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawExpandedCandidatePanel
    // ─────────────────────────────────────────────────────────────────────────

    fun drawExpandedCandidatePanel(canvas: Canvas) {
        val panelTop = view.candidateBarHeight
        val panelBottom = view.height.toFloat()
        val panelHeight = panelBottom - panelTop

        // 半透明背景覆蓋整個鍵盤區域
        view.keyPaint.color = (view.theme.backgroundColorInt and 0x00FFFFFF) or 0xEE000000.toInt()
        canvas.drawRect(0f, panelTop, view.width.toFloat(), panelBottom, view.keyPaint)

        val cellPadding = 4f * view.density
        val cellWidth = view.width.toFloat() / KeyboardView.EXPAND_GRID_COLS
        val cellHeight = 42f * view.density
        val contentPadding = 8f * view.density

        // 計算總內容高度
        val totalRows = (view.allCandidates.size + KeyboardView.EXPAND_GRID_COLS - 1) / KeyboardView.EXPAND_GRID_COLS
        val totalContentHeight = totalRows * cellHeight + contentPadding * 2
        val maxScroll = maxOf(0f, totalContentHeight - panelHeight)
        view.expandScrollOffset = view.expandScrollOffset.coerceIn(0f, maxScroll)

        // 裁剪繪製區域
        canvas.save()
        canvas.clipRect(0f, panelTop, view.width.toFloat(), panelBottom)

        val startY = panelTop + contentPadding - view.expandScrollOffset

        view.allCandidates.forEachIndexed { i, text ->
            val col = i % KeyboardView.EXPAND_GRID_COLS
            val row = i / KeyboardView.EXPAND_GRID_COLS
            val left = col * cellWidth + cellPadding
            val top = startY + row * cellHeight + cellPadding
            val right = (col + 1) * cellWidth - cellPadding
            val bottom = top + cellHeight - 2 * cellPadding

            // 只繪製可見的候選字
            if (bottom < panelTop || top > panelBottom) return@forEachIndexed

            // 候選字背景
            view.keyPaint.color = view.theme.keyColorInt
            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 6 * view.density, 6 * view.density, view.keyPaint)

            // 候選字文字（來源色彩）
            val srcColor = when (view.candidateSourceMap[text]) {
                CandidateSource.CORRECTION -> 0xFFFF9933.toInt()
                CandidateSource.ADAPTIVE   -> 0xFF66CC88.toInt()
                CandidateSource.PREDICTION -> 0xFF66AAFF.toInt()
                CandidateSource.USER_DICT  -> 0xFFFFD700.toInt()
                CandidateSource.SENTENCE   -> 0xFFBB88FF.toInt()
                CandidateSource.HISTORY    -> (view.theme.candidateTextColorInt and 0x00FFFFFF) or 0x80000000.toInt()
                else -> view.theme.candidateTextColorInt
            }
            view.candidatePaint.color = srcColor
            canvas.drawText(text, left + (right - left) / 2f, top + (bottom - top) * 0.65f, view.candidatePaint)
        }

        // 捲動指示條（若可捲動）
        if (maxScroll > 0f) {
            val scrollBarWidth = 4f * view.density
            val scrollBarHeight = panelHeight * (panelHeight / totalContentHeight)
            val scrollBarY = panelTop + (view.expandScrollOffset / maxScroll) * (panelHeight - scrollBarHeight)
            view.keyPaint.color = (view.theme.candidateTextColorInt and 0x00FFFFFF) or 0x66000000
            canvas.drawRoundRect(
                RectF(view.width - scrollBarWidth - 2 * view.density, scrollBarY,
                      view.width - 2 * view.density, scrollBarY + scrollBarHeight),
                scrollBarWidth / 2, scrollBarWidth / 2, view.keyPaint
            )
        }

        canvas.restore()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawDivider
    // ─────────────────────────────────────────────────────────────────────────

    fun drawDivider(canvas: Canvas, x: Float) {
        val effectiveLabelColorGlobal = if (view.customKeyLabelColor != 0) view.customKeyLabelColor else view.theme.keyLabelColorInt
        view.dividerPaint.color = (effectiveLabelColorGlobal and 0x00FFFFFF) or 0x33000000
        canvas.drawLine(x, view.candidateBarHeight, x, view.height.toFloat(), view.dividerPaint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawKeysInRegion
    // ─────────────────────────────────────────────────────────────────────────

    fun drawKeysInRegion(
        canvas: Canvas,
        rows: List<List<KeyModel>>,
        left: Float,
        right: Float
    ) {
        val regionWidth = right - left
        val shouldUpper = view.isShifted || view.isCapsLock
        val rh = view.rowHeight

        // 側邊面板用較小字體
        val isSide = (left > 0f)
        val useLabelPaint = if (isSide) view.sideLabelPaint else view.labelPaint
        if (isSide) {
            view.sideLabelPaint.textSize = 12f * view.density
        } else {
            view.labelPaint.textSize = (if (view.theme.keyFontSizeSp > 0) view.theme.keyFontSizeSp else 18f) * view.density
        }

        // 以最大 widthWeight 總和為基準，計算統一的 unitWidth
        val maxRowUnits = rows.maxOfOrNull { row ->
            row.sumOf { it.widthWeight.toDouble() }.toFloat()
        } ?: 10f
        val unitWidth = regionWidth / maxRowUnits

        rows.forEachIndexed { rowIndex, row ->
            val top = view.candidateBarHeight + rowIndex * rh

            // 每排用自己的 totalUnits 置中排列
            val rowTotalUnits = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val rowActualWidth = rowTotalUnits * unitWidth
            val rowOffset = (regionWidth - rowActualWidth) / 2f
            var x = left + rowOffset

            // keyScalePercent: 100=正常，70=按鍵縮小（padding 加大），130=按鍵放大（padding 縮小）
            val scaleInv = (200 - view.keyScalePercent) / 100f  // 130→0.7, 100→1.0, 70→1.3
            val padH = view.keyPaddingH * scaleInv
            val padV = view.keyPaddingV * scaleInv

            row.forEach { key ->
                val keyWidth = unitWidth * key.widthWeight
                // 第一排從候選列底部開始，避免跟候選列觸控區重疊
                val touchTop = when (rowIndex) {
                    0 -> view.candidateBarHeight
                    else -> top - rh * 0.3f
                }
                val touchRect = RectF(x, touchTop, x + keyWidth, top + rh)

                // 個別按鍵尺寸倍率
                val keyId = if (key.code != 0) key.code.toString() else key.label
                val (pkW, pkH) = view.perKeySizes[keyId] ?: (1f to 1f)
                val adjPadH = padH + (keyWidth * (1f - pkW) / 2f).coerceAtLeast(0f)
                val adjPadV = padV + (rh * (1f - pkH) / 2f).coerceAtLeast(0f)

                val rect = RectF(
                    x + adjPadH,
                    top + adjPadV,
                    x + keyWidth - adjPadH,
                    top + rh - adjPadV
                )
                view.keyEntries.add(KeyboardView.KeyEntry(touchRect, rect, key))

                // 按鍵背景
                val isPressed = view.pressedKey == key
                val isShiftKey = key.code == KeyboardView.KEYCODE_SHIFT
                val isCtrlKey = key.code == KeyboardView.KEYCODE_CTRL
                val baseKeyColor = when {
                    isPressed -> view.theme.keyPressedColorInt
                    isShiftKey && (view.isShifted || view.isCapsLock) -> view.theme.keyPressedColorInt
                    isCtrlKey && view.isCtrlActive -> view.theme.candidateSelectedColorInt
                    key.isSpecial -> view.theme.specialKeyColorInt
                    else -> view.theme.keyColorInt
                }
                // 套用按鍵不透明度
                val rawKeyColor = if (view.keyOpacity < 255) {
                    val a = ((baseKeyColor ushr 24) * view.keyOpacity / 255).coerceIn(0, 255)
                    (a shl 24) or (baseKeyColor and 0x00FFFFFF)
                } else baseKeyColor
                val cr = view.theme.keyCornerRadiusDp * view.density

                if (view.hasCustomBackground) {
                    // 自訂背景模式：不畫實底，只畫淡淡的玻璃折射框線
                    if (isPressed) {
                        // 按下時顯示微弱高亮
                        view.keyPaint.style = Paint.Style.FILL
                        view.keyPaint.color = 0x33FFFFFF
                        canvas.drawRoundRect(rect, cr, cr, view.keyPaint)
                    }
                    // 液態玻璃折射框線（淡白色 + 淡亮頂邊）
                    view.keyPaint.style = Paint.Style.STROKE
                    view.keyPaint.strokeWidth = 0.5f * view.density
                    view.keyPaint.color = 0x30FFFFFF
                    canvas.drawRoundRect(rect, cr, cr, view.keyPaint)
                    // 頂部亮邊（模擬折射高光）
                    val topLine = RectF(rect.left + 2 * view.density, rect.top, rect.right - 2 * view.density, rect.top + 0.5f * view.density)
                    view.keyPaint.color = 0x40FFFFFF
                    view.keyPaint.style = Paint.Style.FILL
                    canvas.drawRoundRect(topLine, 1f * view.density, 1f * view.density, view.keyPaint)
                    view.keyPaint.style = Paint.Style.FILL
                } else {
                    view.keyPaint.color = rawKeyColor
                    view.keyPaint.style = Paint.Style.FILL

                    // 按鍵陰影（淺色主題才明顯）
                    if (view.theme.keyShadowRadiusDp > 0f && view.theme.keyShadowColorInt != 0) {
                        view.keyPaint.setShadowLayer(
                            view.theme.keyShadowRadiusDp * view.density,
                            0f, 1f * view.density,
                            view.theme.keyShadowColorInt
                        )
                    }
                    canvas.drawRoundRect(rect, cr, cr, view.keyPaint)
                    view.keyPaint.clearShadowLayer()

                    // 按鍵邊框
                    if (view.theme.keyBorderWidthDp > 0 && view.theme.keyBorderColorInt != 0) {
                        view.keyPaint.style = Paint.Style.STROKE
                        view.keyPaint.strokeWidth = view.theme.keyBorderWidthDp * view.density
                        view.keyPaint.color = view.theme.keyBorderColorInt
                        canvas.drawRoundRect(rect, cr, cr, view.keyPaint)
                        view.keyPaint.style = Paint.Style.FILL
                    }
                }

                // 液態玻璃 + 主題裝飾
                view.keyDecorator.decorate(canvas, rect, view.theme, rowIndex * 100 + row.indexOf(key), key.isSpecial, isPressed, cr)

                // Label
                val ctrlHint = if (view.isCtrlActive && !key.isSpecial) view.resolveCtrlCombo(key.label) else null
                val displayLabel = when {
                    isCtrlKey -> if (view.isCtrlActive) "Ctrl ✓" else "Ctrl"
                    isShiftKey && view.isCapsLock -> "⇪"
                    isShiftKey && view.isShifted -> "⇧"
                    ctrlHint != null -> "^${key.label.uppercase()}"
                    // ABC 按鈕依 inputMode 動態顯示
                    key.code == KeyboardView.KEYCODE_SWITCH_ABC -> when (view.currentInputMode) {
                        com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO -> "注音"
                        com.snowrealm.yukiboard.input.router.InputMode.JAPANESE -> "あ"
                        com.snowrealm.yukiboard.input.router.InputMode.KOREAN -> "가"
                        com.snowrealm.yukiboard.input.router.InputMode.PINYIN -> "拼音"
                        com.snowrealm.yukiboard.input.router.InputMode.CANGJIE,
                        com.snowrealm.yukiboard.input.router.InputMode.SPEED_CANGJIE -> "倉頡"
                        com.snowrealm.yukiboard.input.router.InputMode.AUTO -> "自動"
                        else -> "ABC"
                    }
                    // 空白鍵：AUTO 模式顯示「自動」標示
                    key.code == KeyboardView.KEYCODE_SPACE && view.currentInputMode == com.snowrealm.yukiboard.input.router.InputMode.AUTO -> "自動"
                    shouldUpper && !key.isSpecial && key.label.length == 1 -> key.label.uppercase()
                    else -> key.label
                }

                val baseLabelColor = if (view.customKeyLabelColor != 0) view.customKeyLabelColor else view.theme.keyLabelColorInt
                val effectiveLabelColor = if (view.keyTextOpacity < 255) {
                    val a = ((baseLabelColor ushr 24) * view.keyTextOpacity / 255).coerceIn(0, 255)
                    (a shl 24) or (baseLabelColor and 0x00FFFFFF)
                } else baseLabelColor
                useLabelPaint.color = if (ctrlHint != null) view.theme.candidateSelectedColorInt else effectiveLabelColor
                val textY = rect.top + (rect.height() + useLabelPaint.textSize) / 2f - useLabelPaint.descent()
                val labelWidth = useLabelPaint.measureText(displayLabel)
                if (labelWidth > rect.width() - 2 * view.density) {
                    // 文字太寬：縮小字號直到能放下
                    val origSize = useLabelPaint.textSize
                    val scale = (rect.width() - 2 * view.density) / labelWidth
                    useLabelPaint.textSize = origSize * scale.coerceAtLeast(0.5f)
                    val scaledTextY = rect.top + (rect.height() + useLabelPaint.textSize) / 2f - useLabelPaint.descent()
                    canvas.drawText(displayLabel, rect.centerX(), scaledTextY, useLabelPaint)
                    useLabelPaint.textSize = origSize
                } else {
                    canvas.drawText(displayLabel, rect.centerX(), textY, useLabelPaint)
                }

                // Alt hint：右上角顯示第一個長按字元（縮小而非裁切）
                if (key.altChars.isNotEmpty()) {
                    view.altHintPaint.color = (effectiveLabelColor and 0x00FFFFFF) or 0x80000000.toInt()
                    val hint = key.altChars.first()
                    val hintWidth = view.altHintPaint.measureText(hint)
                    val availW = rect.width() - 4f * view.density
                    if (hintWidth <= availW) {
                        canvas.drawText(hint, rect.right - 3f * view.density, rect.top + view.altHintPaint.textSize + 1f * view.density, view.altHintPaint)
                    } else {
                        val origSize = view.altHintPaint.textSize
                        view.altHintPaint.textSize = origSize * (availW / hintWidth).coerceAtLeast(0.5f)
                        canvas.drawText(hint, rect.right - 3f * view.density, rect.top + view.altHintPaint.textSize + 1f * view.density, view.altHintPaint)
                        view.altHintPaint.textSize = origSize
                    }
                }

                x += keyWidth
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawOneHandMargin
    // ─────────────────────────────────────────────────────────────────────────

    fun drawOneHandMargin(canvas: Canvas, kbLeft: Float, kbRight: Float, marginWidth: Float) {
        val isLeft = view.oneHandSide == KeyboardView.OneHandSide.LEFT
        val marginLeft = if (isLeft) kbRight else 0f
        val marginRight = if (isLeft) view.width.toFloat() else kbLeft

        // 邊欄底色（半透明深色）
        val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        marginPaint.color = (view.theme.backgroundColorInt and 0x00FFFFFF) or (0x60 shl 24)
        canvas.drawRect(marginLeft, view.candidateBarHeight, marginRight, view.height.toFloat(), marginPaint)

        val cx = (marginLeft + marginRight) / 2f
        val btnW = marginWidth * 0.7f
        val btnH = 36f * view.density
        val gap = 12f * view.density

        // 切換方向按鈕（箭頭）
        val switchY = view.candidateBarHeight + (view.height - view.candidateBarHeight) * 0.35f
        view.oneHandBtnRect.set(cx - btnW / 2f, switchY - btnH / 2f, cx + btnW / 2f, switchY + btnH / 2f)
        marginPaint.color = (view.theme.keyColorInt and 0x00FFFFFF) or (0xCC shl 24)
        canvas.drawRoundRect(view.oneHandBtnRect, 8f * view.density, 8f * view.density, marginPaint)
        val arrow = if (isLeft) "⟩" else "⟨"  // 指向另一側
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 20f * view.density
            color = 0xFFB0D4FF.toInt()
        }
        canvas.drawText(arrow, cx, switchY + 7f * view.density, arrowPaint)

        // 還原全寬按鈕
        val expandY = switchY + btnH / 2f + gap + btnH / 2f
        view.oneHandExpandRect.set(cx - btnW / 2f, expandY - btnH / 2f, cx + btnW / 2f, expandY + btnH / 2f)
        marginPaint.color = (view.theme.keyColorInt and 0x00FFFFFF) or (0x80 shl 24)
        canvas.drawRoundRect(view.oneHandExpandRect, 8f * view.density, 8f * view.density, marginPaint)
        arrowPaint.textSize = 14f * view.density
        arrowPaint.color = 0xFF8899AA.toInt()
        canvas.drawText("⟷", cx, expandY + 5f * view.density, arrowPaint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawAltPopupOverlay
    // ─────────────────────────────────────────────────────────────────────────

    fun drawAltPopupOverlay(canvas: Canvas) {
        if (!view.altPopupActive) return

        // 陰影背景
        altBgPaint.color = view.theme.candidateBarColorInt
        altBgPaint.setShadowLayer(12f * view.density, 0f, 3f * view.density, 0x66000000)
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, altBgPaint)
        canvas.drawRoundRect(view.altPopupBgRect, 10f * view.density, 10f * view.density, altBgPaint)
        altBgPaint.clearShadowLayer()

        view.altPopupChars.forEachIndexed { i, ch ->
            val rect = view.altPopupRects[i]
            val isHovered = i == view.altPopupHoveredIndex

            // 格子背景
            altCellPaint.color = if (isHovered) view.theme.candidateSelectedColorInt else view.theme.keyColorInt
            canvas.drawRoundRect(rect, 8f * view.density, 8f * view.density, altCellPaint)

            // 文字
            altTextPaint.textSize = 20f * view.density
            altTextPaint.color = if (isHovered) view.theme.keyLabelColorInt else view.theme.candidateTextColorInt
            altTextPaint.isFakeBoldText = isHovered
            val textY = rect.centerY() + 7f * view.density
            canvas.drawText(ch, rect.centerX(), textY, altTextPaint)
        }

        // 三角指示器（指向原始按鍵）
        if (view.altPopupHoveredIndex >= 0) {
            val hRect = view.altPopupRects[view.altPopupHoveredIndex]
            val triSize = 6f * view.density
            val path = android.graphics.Path().apply {
                moveTo(hRect.centerX() - triSize, view.altPopupBgRect.bottom)
                lineTo(hRect.centerX() + triSize, view.altPopupBgRect.bottom)
                lineTo(hRect.centerX(), view.altPopupBgRect.bottom + triSize)
                close()
            }
            altCellPaint.color = view.theme.candidateSelectedColorInt
            canvas.drawPath(path, altCellPaint)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawSwipeTrail
    // ─────────────────────────────────────────────────────────────────────────

    fun drawSwipeTrail(canvas: Canvas) {
        if (view.swipePoints.isEmpty()) return
        val alpha = (view.swipeAlpha * 255).toInt().coerceIn(0, 255)
        if (alpha == 0) return

        // 構建平滑貝茲路徑
        view.swipePath.reset()
        if (view.swipePoints.size == 1) {
            val p = view.swipePoints[0]
            view.swipeDotPaint.color = view.swipeColor
            view.swipeDotPaint.alpha = alpha
            canvas.drawCircle(p.x, p.y, 10f * view.density, view.swipeDotPaint)
            return
        }

        view.swipePath.moveTo(view.swipePoints[0].x, view.swipePoints[0].y)
        for (i in 1 until view.swipePoints.size - 1) {
            val curr = view.swipePoints[i]
            val next = view.swipePoints[i + 1]
            val midX = (curr.x + next.x) / 2f
            val midY = (curr.y + next.y) / 2f
            view.swipePath.quadTo(curr.x, curr.y, midX, midY)
        }
        view.swipePath.lineTo(view.swipePoints.last().x, view.swipePoints.last().y)

        // 外層發光（粗線 + 半透明）
        view.swipeGlowPaint.color = view.swipeColor
        view.swipeGlowPaint.alpha = (alpha * 0.25f).toInt()
        canvas.drawPath(view.swipePath, view.swipeGlowPaint)

        // 內層清晰路徑
        view.swipeLinePaint.color = view.swipeColor
        view.swipeLinePaint.alpha = alpha
        canvas.drawPath(view.swipePath, view.swipeLinePaint)

        // 起點光圈
        val start = view.swipePoints[0]
        view.swipeDotPaint.color = view.swipeColor
        view.swipeDotPaint.alpha = (alpha * 0.4f).toInt()
        canvas.drawCircle(start.x, start.y, 14f * view.density, view.swipeDotPaint)
        view.swipeDotPaint.alpha = (alpha * 0.9f).toInt()
        canvas.drawCircle(start.x, start.y, 6f * view.density, view.swipeDotPaint)

        // 按鍵高亮
        val highlightKey = view.swipeHighlightKey
        if (highlightKey != null && view.swipeHighlightAlpha > 0f) {
            val keyEntry = view.keyEntries.firstOrNull { it.key == highlightKey }
            if (keyEntry != null) {
                view.swipeKeyHighlightPaint.color = view.swipeColor
                view.swipeKeyHighlightPaint.alpha = (view.swipeHighlightAlpha * 255).toInt()
                canvas.drawRoundRect(keyEntry.visualRect, 8f * view.density, 8f * view.density, view.swipeKeyHighlightPaint)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawTouchArea
    // ─────────────────────────────────────────────────────────────────────────

    fun drawTouchArea(canvas: Canvas) {
        // touchAreaSize 是 0.0~1.0 的正規化值，映射到畫面上的半徑
        val minRadius = 12f * view.density
        val maxRadius = 60f * view.density
        val radius = minRadius + view.touchAreaSize * (maxRadius - minRadius)

        // 壓力值決定顏色（輕→藍、中→綠、重→紅）
        val color = when {
            view.touchAreaPressure < 0.3f -> 0xFF42A5F5.toInt()  // 淺藍
            view.touchAreaPressure < 0.7f -> 0xFF66BB6A.toInt()  // 綠
            else -> 0xFFEF5350.toInt()                            // 紅
        }

        // 半透明填充
        view.touchAreaFillPaint.color = color
        view.touchAreaFillPaint.alpha = 40
        canvas.drawCircle(view.touchAreaX, view.touchAreaY, radius, view.touchAreaFillPaint)

        // 邊框
        view.touchAreaPaint.color = color
        view.touchAreaPaint.alpha = 180
        view.touchAreaPaint.strokeWidth = 2f * view.density
        canvas.drawCircle(view.touchAreaX, view.touchAreaY, radius, view.touchAreaPaint)

        // 中心十字
        val crossSize = 4f * view.density
        view.touchAreaPaint.alpha = 220
        view.touchAreaPaint.strokeWidth = 1.5f * view.density
        canvas.drawLine(view.touchAreaX - crossSize, view.touchAreaY, view.touchAreaX + crossSize, view.touchAreaY, view.touchAreaPaint)
        canvas.drawLine(view.touchAreaX, view.touchAreaY - crossSize, view.touchAreaX, view.touchAreaY + crossSize, view.touchAreaPaint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // drawCaseMenu
    // ─────────────────────────────────────────────────────────────────────────

    fun drawCaseMenu(canvas: Canvas) {
        val itemH = 36f * view.density
        val itemW = 96f * view.density
        val pad = 8f * view.density
        val totalH = itemH * view.caseMenuItems.size + pad * 2
        val menuLeft = (view.caseMenuAnchor.centerX() - itemW / 2f).coerceIn(0f, view.width - itemW)
        val menuTop = view.caseMenuAnchor.top - totalH - 4f * view.density
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = view.theme.keyColorInt
            setShadowLayer(8f * view.density, 0f, 2f * view.density, 0x44000000)
        }
        canvas.drawRoundRect(
            RectF(menuLeft - pad, menuTop - pad, menuLeft + itemW + pad, menuTop + totalH + pad),
            12f * view.density, 12f * view.density, bgPaint
        )
        view.caseMenuItems.forEachIndexed { i, (label, _) ->
            val itemTop = menuTop + pad + i * itemH
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f * view.density
                color = view.theme.keyLabelColorInt
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(label, menuLeft + itemW / 2f, itemTop + itemH / 2f + tp.textSize * 0.35f, tp)
        }
    }
}

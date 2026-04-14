package com.snowrealm.yukiboard.ui.keyboard

import android.graphics.PointF
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.widget.PopupWindow

/**
 * KeyboardView 觸控處理委派。
 *
 * 負責 onTouchEvent 的全部邏輯分支（ACTION_DOWN / MOVE / UP / CANCEL）、
 * 滑動輸入路徑管理、環形選單彈出、alt popup 操作等。
 *
 * 透過 `view` 參照存取 KeyboardView 的 internal 欄位，避免大量參數傳遞。
 */
internal class KeyboardTouchDelegate(private val view: KeyboardView) {

    // ── ACTION_DOWN ──────────────────────────────────────────────

    fun handleTouchDown(x: Float, y: Float, event: MotionEvent): Boolean {
        // 候選列觸控（含展開箭頭）
        val candidateHitZone = view.candidateBarHeight
        if (y < candidateHitZone && y >= 0 && view.candidates.isNotEmpty()) {
            view.candidateLastTouchX = x
            view.candidateDownX = x
            view.candidateIsDragging = false
            view.candidateTouchActive = true
            view.candidateLongPressX = x
            view.handler.postDelayed(view.candidateLongPressRunnable, view.CANDIDATE_LONG_PRESS_DELAY)
            return true
        }
        view.candidateTouchActive = false
        // 展開面板：記錄觸控起始位置
        if (view.isCandidateExpanded && y > candidateHitZone) {
            view.expandTouchStartY = y
            view.expandLastTouchY = y
            view.expandDownX = x
            view.expandDownY = y
            view.expandIsScrolling = false
            return true
        }
        view.isLongPressTriggered = false
        view.isSwipeGesture = false
        view.swipeKeySequence.clear()
        view.confirmedSwipeKeys.clear()
        view.lastConfirmedKey = null
        view.swipeVelocityPoints.clear()
        view.swipeDownX = x
        view.swipeDownY = y
        var hit = view.keyEntries.firstOrNull { it.touchRect.contains(x, y) }
        // 精確命中失敗時，找最近的按鍵（擴大觸控範圍）
        if (hit == null && y >= candidateHitZone) {
            val expandPx = 16f * view.density
            hit = view.keyEntries.firstOrNull {
                val r = it.touchRect
                x >= r.left - expandPx && x <= r.right + expandPx &&
                y >= r.top - expandPx && y <= r.bottom + expandPx
            }
        }
        if (hit != null) {
            view.pressedKey = hit.key
            view.longPressKey = hit.key
            view.longPressRect = hit.touchRect
            view.handler.postDelayed(view.longPressRunnable, KeyboardView.LONG_PRESS_DELAY)
            if (view.hapticEnabled) view.vibrateShort()
            view.invalidate()
            // 記錄滑動起始按鍵
            if (view.swipeInputEnabled && !hit.key.isSpecial) {
                view.swipeKeySequence.add(hit.key)
                view.confirmedSwipeKeys.add(hit.key)
                view.lastConfirmedKey = hit.key
            }
        }
        // 滑動路徑起點
        if (y >= candidateHitZone) {
            onSwipeStart(x, y)
        }
        return true
    }

    // ── ACTION_MOVE ──────────────────────────────────────────────

    fun handleTouchMove(x: Float, y: Float, event: MotionEvent): Boolean {
        // Alt popup 滑動選字
        if (view.altPopupActive) {
            updateAltPopupHover(x, y)
            return true
        }
        // Radial menu 觸控轉發
        val radial = view.activeRadialView
        if (radial != null) {
            val localX = x - view.radialPopupOffsetX
            val localY = y - view.radialPopupOffsetY
            val translated = MotionEvent.obtain(
                event.downTime, event.eventTime, MotionEvent.ACTION_MOVE,
                localX, localY, event.metaState
            )
            radial.dispatchTouchEvent(translated)
            translated.recycle()
            return true
        }
        // 候選列橫向捲動
        if (view.candidateTouchActive && view.candidates.isNotEmpty()) {
            view.handler.removeCallbacks(view.candidateLongPressRunnable)
            val dx = view.candidateLastTouchX - x
            if (!view.candidateIsDragging && Math.abs(dx) > 16 * view.density) {
                view.candidateIsDragging = true
            }
            if (view.candidateIsDragging) {
                view.candidateScrollX += dx
                view.candidateLastTouchX = x
                view.invalidate()
            }
            return true
        }
        // 展開面板：滑動捲動
        if (view.isCandidateExpanded && y > view.candidateBarHeight) {
            val dy = view.expandLastTouchY - y
            if (!view.expandIsScrolling && Math.abs(y - view.expandTouchStartY) > 8 * view.density) {
                view.expandIsScrolling = true
            }
            if (view.expandIsScrolling) {
                view.expandScrollOffset += dy
                view.expandLastTouchY = y
                view.invalidate()
            }
            return true
        }
        val currentRect = view.longPressRect
        if (currentRect != null) {
            val moveTolerance = 8f * view.density
            val inTolerance = x >= currentRect.left - moveTolerance &&
                x <= currentRect.right + moveTolerance &&
                y >= currentRect.top - moveTolerance &&
                y <= currentRect.bottom + moveTolerance
            if (!inTolerance) {
                view.handler.removeCallbacks(view.longPressRunnable)
                view.handler.removeCallbacks(view.deleteRepeatRunnable)
                view.pressedKey = null
                view.invalidate()
            }
        }
        // 滑動輸入：追蹤經過的按鍵
        if (view.swipeInputEnabled && !view.isLongPressTriggered && y >= view.candidateBarHeight) {
            var moveHit = view.keyEntries.firstOrNull { it.touchRect.contains(x, y) }
            if (moveHit == null) {
                val ep = 6f * view.density
                moveHit = view.keyEntries.firstOrNull {
                    val r = it.touchRect
                    x >= r.left - ep && x <= r.right + ep && y >= r.top - ep && y <= r.bottom + ep
                }
            }
            if (moveHit != null && !moveHit.key.isSpecial) {
                if (view.swipeKeySequence.isEmpty() && view.confirmedSwipeKeys.isEmpty()) {
                    view.swipeKeySequence.add(moveHit.key)
                    view.confirmedSwipeKeys.add(moveHit.key)
                    view.lastConfirmedKey = moveHit.key
                }
                val lastKey = view.swipeKeySequence.lastOrNull()
                if (lastKey == null || lastKey.label != moveHit.key.label) {
                    view.swipeKeySequence.add(moveHit.key)
                }
            }
            val dist = Math.hypot((x - view.swipeDownX).toDouble(), (y - view.swipeDownY).toDouble())
            if (!view.isSwipeGesture && dist > view.SWIPE_DISTANCE_THRESHOLD && view.swipeKeySequence.size >= 2) {
                view.isSwipeGesture = true
                view.handler.removeCallbacks(view.longPressRunnable)
            }
        }
        // 更新滑動路徑
        if (y >= view.candidateBarHeight) {
            onSwipeMove(x, y)
        }
        return true
    }

    // ── ACTION_UP ────────────────────────────────────────────────

    fun handleTouchUp(x: Float, y: Float, event: MotionEvent): Boolean {
        view.handler.removeCallbacks(view.longPressRunnable)
        view.handler.removeCallbacks(view.deleteRepeatRunnable)
        view.pressedKey = null
        view.isLongPressTriggered = false
        if (view.isSpaceLongPressed) {
            view.isSpaceLongPressed = false
            view.onSpaceLongPressListener?.invoke(false)
        }
        view.invalidate()

        // 大小寫選單
        if (view.caseMenuVisible) {
            val itemH = 36f * view.density; val itemW = 96f * view.density; val pad = 8f * view.density
            val totalH = itemH * view.caseMenuItems.size + pad * 2
            val menuLeft = (view.caseMenuAnchor.centerX() - itemW / 2f).coerceIn(0f, view.width - itemW)
            val menuTop = view.caseMenuAnchor.top - totalH - 4f * view.density
            view.caseMenuItems.forEachIndexed { i, (_, type) ->
                val itemTop = menuTop + pad + i * itemH
                if (RectF(menuLeft, itemTop, menuLeft + itemW, itemTop + itemH).contains(x, y)) {
                    view.onCaseConvert?.invoke(type)
                    view.caseMenuVisible = false; view.isLongPressTriggered = false
                    view.invalidate(); view.performClick(); return true
                }
            }
            view.caseMenuVisible = false; view.isLongPressTriggered = false; view.invalidate(); return true
        }

        // Alt popup 放開 → 選字
        if (view.altPopupActive) {
            view.commitAltPopupSelection()
            view.isLongPressTriggered = false
            view.performClick()
            return true
        }
        // Radial menu 放開 → 轉發 UP 給 menu，然後關閉
        val radial = view.activeRadialView
        if (radial != null) {
            val localX = x - view.radialPopupOffsetX
            val localY = y - view.radialPopupOffsetY
            val translated = MotionEvent.obtain(
                event.downTime, event.eventTime, MotionEvent.ACTION_UP,
                localX, localY, event.metaState
            )
            radial.dispatchTouchEvent(translated)
            translated.recycle()
            view.activeRadialView = null
            view.dismissPopup()
            view.isLongPressTriggered = false
            view.performClick()
            return true
        }

        if (view.isLongPressTriggered) {
            view.isLongPressTriggered = false
            onSwipeEnd()
            return true
        }

        // 滑動輸入：滑動結束，送出停頓確認的按鍵序列
        if (view.isSwipeGesture && view.confirmedSwipeKeys.size >= 2) {
            view.onSwipeInputListener?.invoke(view.confirmedSwipeKeys.toList())
            view.confirmedSwipeKeys.clear()
            view.lastConfirmedKey = null
            view.swipeKeySequence.clear()
            view.isSwipeGesture = false
            onSwipeEnd()
            view.performClick()
            return true
        }
        view.confirmedSwipeKeys.clear()
        view.swipeKeySequence.clear()
        view.isSwipeGesture = false

        // 非滑動手勢：標記當前按鍵
        val hitForConfirm = view.keyEntries.firstOrNull { it.touchRect.contains(x, y) }?.key
        if (hitForConfirm != null) view.lastConfirmedKey = hitForConfirm

        // 展開面板觸控選字
        if (view.isCandidateExpanded && y > view.candidateBarHeight && view.allCandidates.isNotEmpty()) {
            if (view.expandIsScrolling) {
                view.expandIsScrolling = false
                view.performClick()
                return true
            }
            val cellWidth = view.width.toFloat() / KeyboardView.EXPAND_GRID_COLS
            val cellHeight = 42f * view.density
            val contentPadding = 8f * view.density
            val startY = view.candidateBarHeight + contentPadding - view.expandScrollOffset
            val selectX = x
            val selectY = y
            val col = (selectX / cellWidth).toInt().coerceIn(0, KeyboardView.EXPAND_GRID_COLS - 1)
            val row = ((selectY - startY) / cellHeight).toInt()
            if (row >= 0) {
                val index = row * KeyboardView.EXPAND_GRID_COLS + col
                if (index in view.allCandidates.indices) {
                    view.onCandidateSpeakListener?.invoke(view.allCandidates[index])
                    view.onCandidateSelectListener?.invoke(index)
                    view.isCandidateExpanded = false
                    view.expandScrollOffset = 0f
                    view.invalidate()
                    view.performClick()
                    return true
                }
            }
            view.isCandidateExpanded = false
            view.expandScrollOffset = 0f
            view.invalidate()
            view.performClick()
            return true
        }

        // 候選列觸控選字
        if (view.candidateTouchActive && view.candidates.isNotEmpty()) {
            view.handler.removeCallbacks(view.candidateLongPressRunnable)
            view.candidateTouchActive = false

            if (view.candidateLongPressActive) {
                val lpIdx = view.candidateLongPressIndex
                view.candidateLongPressActive = false
                view.candidateLongPressIndex = -1
                view.invalidate()
                if (lpIdx >= 0 && lpIdx < view.candidates.size) {
                    view.onCandidateSpeakListener?.invoke(view.candidates[lpIdx])
                    view.onCandidateSelectListener?.invoke(lpIdx)
                }
                view.performClick()
                return true
            }

            val totalDragX = Math.abs(x - view.candidateDownX)

            // 展開箭頭
            if (!view.candidateIsDragging && view.hasExpandArrow && view.candidateDownX >= view.width.toFloat() - view.expandArrowWidth) {
                view.isCandidateExpanded = !view.isCandidateExpanded
                view.expandScrollOffset = 0f
                view.invalidate()
                view.performClick()
                return true
            }

            // 大幅水平滑動只當捲動
            if (view.candidateIsDragging && totalDragX > 80 * view.density) {
                view.candidateIsDragging = false
                view.performClick()
                return true
            }
            view.candidateIsDragging = false

            // 用 DOWN 位置選字
            val idx = view.hitTestCandidateIndex(if (view.candidateIsDragging) x else view.candidateDownX)
            if (idx >= 0 && idx < view.candidates.size) {
                view.onCandidateSpeakListener?.invoke(view.candidates[idx])
                view.onCandidateSelectListener?.invoke(idx)
                view.performClick()
                return true
            }
            // 貼圖推薦提示觸控
            val hintR = view.stickerHintRect
            if (hintR != null && hintR.contains(x + view.candidateScrollX, y)) {
                view.onStickerHintClick?.invoke()
                view.performClick()
                return true
            }
            view.performClick()
            return true
        }

        // 單手模式邊欄按鈕
        if (view.oneHandSide != KeyboardView.OneHandSide.NONE) {
            if (view.oneHandBtnRect.contains(x, y)) {
                val newSide = if (view.oneHandSide == KeyboardView.OneHandSide.LEFT) KeyboardView.OneHandSide.RIGHT else KeyboardView.OneHandSide.LEFT
                view.oneHandSide = newSide
                view.onOneHandChanged?.invoke(newSide)
                view.performClick()
                return true
            }
            if (view.oneHandExpandRect.contains(x, y)) {
                view.oneHandSide = KeyboardView.OneHandSide.NONE
                view.onOneHandChanged?.invoke(KeyboardView.OneHandSide.NONE)
                view.performClick()
                return true
            }
        }

        var hit = view.keyEntries.firstOrNull { it.touchRect.contains(x, y) }
        if (hit == null && y >= view.candidateBarHeight) {
            val expandPx = 16f * view.density
            hit = view.keyEntries.firstOrNull {
                val r = it.touchRect
                x >= r.left - expandPx && x <= r.right + expandPx &&
                y >= r.top - expandPx && y <= r.bottom + expandPx
            }
        }
        if (hit != null) {
            val key = hit.key
            when {
                key.code == KeyboardView.KEYCODE_BACK_NORMAL -> view.switchMode(KeyboardMode.NORMAL)
                key.code == KeyboardView.KEYCODE_SWITCH_NUMBERS -> view.switchSubLayout(KeyboardView.SubLayout.NUMBERS)
                key.code == KeyboardView.KEYCODE_SWITCH_SYMBOLS -> view.switchSubLayout(KeyboardView.SubLayout.SYMBOLS)
                key.code == KeyboardView.KEYCODE_SWITCH_ABC -> view.switchSubLayout(KeyboardView.SubLayout.ABC)
                key.code == KeyboardView.KEYCODE_SWITCH_NUMPAD -> view.switchSubLayout(KeyboardView.SubLayout.NUMPAD)
                key.code == KeyboardView.KEYCODE_SHIFT -> view.toggleShift()
                key.code == KeyboardView.KEYCODE_CTRL -> view.toggleCtrl()
                view.isCtrlActive && !key.isSpecial -> {
                    val ctrlCode = view.resolveCtrlCombo(key.label)
                    if (ctrlCode != null) {
                        view.onKeyPressListener?.invoke(ctrlCode, key.label)
                    }
                    view.isCtrlActive = false
                    view.invalidate()
                }
                else -> {
                    val shouldUpper = (view.isShifted || view.isCapsLock) && !key.isSpecial && key.label.length == 1
                    val output = if (shouldUpper) key.label.uppercase() else key.label
                    view.onKeyPressListener?.invoke(key.code, output)
                    if (!key.isSpecial) view.autoUnshift()
                }
            }
            view.performClick()
        }
        // 滑動路徑結束
        onSwipeEnd()
        view.lastConfirmedKey = null
        return true
    }

    // ── ACTION_CANCEL ────────────────────────────────────────────

    fun handleTouchCancel(event: MotionEvent): Boolean {
        view.handler.removeCallbacks(view.longPressRunnable)
        view.handler.removeCallbacks(view.deleteRepeatRunnable)
        view.handler.removeCallbacksAndMessages(null)
        view.pressedKey = null
        view.isLongPressTriggered = false
        view.candidateTouchActive = false
        view.candidateLongPressActive = false
        view.candidateLongPressIndex = -1
        view.caseMenuVisible = false
        if (view.altPopupActive) view.dismissAltPopup()
        view.activeRadialView = null
        if (view.isSpaceLongPressed) {
            view.isSpaceLongPressed = false
            view.onSpaceLongPressListener?.invoke(false)
        }
        onSwipeEnd()
        view.invalidate()
        return true
    }

    // ── 滑動路徑 ─────────────────────────────────────────────────

    fun onSwipeStart(x: Float, y: Float) {
        if (!view.swipeTrailEnabled) return
        view.swipeFadeAnimator?.cancel()
        view.swipePoints.clear()
        view.swipePoints.add(PointF(x, y))
        view.isSwipingActive = true
        view.swipeAlpha = 1f
        val startKey = view.keyEntries.firstOrNull { it.touchRect.contains(x, y) }?.key
        if (startKey != null) flashKey(startKey)
        view.invalidate()
    }

    fun onSwipeMove(x: Float, y: Float) {
        if (!view.isSwipingActive) return

        val now = System.currentTimeMillis()
        val newPoint = PointF(x, y)

        view.swipeVelocityPoints.addLast(now to newPoint)
        while (view.swipeVelocityPoints.isNotEmpty() &&
            now - view.swipeVelocityPoints.first().first > view.VELOCITY_WINDOW_MS) {
            view.swipeVelocityPoints.removeFirst()
        }

        val currentVelocity = if (view.swipeVelocityPoints.size >= 2) {
            val oldest = view.swipeVelocityPoints.first()
            val newest = view.swipeVelocityPoints.last()
            val dt = (newest.first - oldest.first).toFloat() / 1000f
            val dx = newest.second.x - oldest.second.x
            val dy = newest.second.y - oldest.second.y
            val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dt > 0) dist / dt else Float.MAX_VALUE
        } else Float.MAX_VALUE

        val last = view.swipePoints.lastOrNull()
        if (last == null || Math.hypot((x - last.x).toDouble(), (y - last.y).toDouble()) > 4.0 * view.density) {
            view.swipePoints.add(newPoint)
            view.invalidate()
        }

        val currentKey = view.keyEntries.firstOrNull { it.touchRect.contains(x, y) }?.key ?: return
        if (currentKey.isSpecial) return

        if (currentKey != view.lastConfirmedKey) {
            if (currentVelocity < view.DWELL_VELOCITY_THRESHOLD) {
                confirmSwipeKey(currentKey)
            } else {
                view.swipeDwellHandler.removeCallbacks(view.swipeDwellRunnable)
                if (currentKey != view.swipeDwellKey) {
                    view.swipeDwellKey = currentKey
                    view.swipeDwellStartMs = System.currentTimeMillis()
                    view.swipeDwellHandler.postDelayed(view.swipeDwellRunnable, view.swipeDwellThresholdMs)
                }
            }
        }
    }

    fun onSwipeEnd() {
        if (!view.isSwipingActive && view.swipePoints.isEmpty()) return
        view.isSwipingActive = false
        view.swipeDwellHandler.removeCallbacks(view.swipeDwellRunnable)
        view.swipeDwellKey = null

        val lastPoint = view.swipePoints.lastOrNull()
        if (lastPoint != null) {
            val lastKey = view.keyEntries.firstOrNull {
                it.touchRect.contains(lastPoint.x, lastPoint.y)
            }?.key
            if (lastKey != null && !lastKey.isSpecial && lastKey != view.lastConfirmedKey) {
                confirmSwipeKey(lastKey)
            }
        }
        view.swipeFadeAnimator?.cancel()
        view.swipeFadeAnimator = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 400
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { anim ->
                view.swipeAlpha = anim.animatedValue as Float
                view.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.swipePoints.clear()
                    view.invalidate()
                }
            })
            start()
        }
    }

    fun confirmSwipeKey(key: KeyModel) {
        if (key == view.lastConfirmedKey) return
        view.lastConfirmedKey = key
        view.confirmedSwipeKeys.add(key)
        flashKey(key)
        if (view.hapticEnabled) view.vibrateShort()
        view.onSwipeSyllableConfirmed?.invoke(key.label)
    }

    fun flashKey(key: KeyModel) {
        view.swipeHighlightKey = key
        view.swipeHighlightAlpha = 0.6f
        view.highlightFadeAnimator?.cancel()
        view.highlightFadeAnimator = android.animation.ValueAnimator.ofFloat(0.6f, 0f).apply {
            duration = 200
            addUpdateListener { anim ->
                view.swipeHighlightAlpha = anim.animatedValue as Float
                view.invalidate()
            }
            start()
        }
    }

    fun onSwipeDwellConfirmed(key: KeyModel) {
        view.swipeHighlightKey = key
        view.swipeHighlightAlpha = 1.0f
        view.highlightFadeAnimator?.cancel()
        view.highlightFadeAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 0f).apply {
            duration = 350
            addUpdateListener { anim ->
                view.swipeHighlightAlpha = anim.animatedValue as Float
                view.invalidate()
            }
            start()
        }
        if (view.hapticEnabled) view.vibrateShort()
        view.onSwipeSyllableConfirmed?.invoke(key.label)
        view.swipeDwellKey = null
    }

    // ── Radial Menu ──────────────────────────────────────────────

    fun showRadialModeMenu(anchorRect: RectF) {
        view.dismissPopup()

        val menu = RadialModeMenu(view.context, view.theme, view.currentMode, onModeSelected = { selectedMode ->
            view.dismissPopup()
            view.activeRadialView = null
            if (selectedMode != view.currentMode) {
                view.switchMode(selectedMode)
            }
        }, onFloatingToggle = {
            view.dismissPopup()
            view.activeRadialView = null
            view.onFloatingToggleListener?.invoke()
        })

        val menuSize = (180 * view.density + 40 * view.density).toInt()
        val popup = PopupWindow(
            menu,
            menuSize,
            menuSize,
            false
        ).apply {
            isOutsideTouchable = false
            isTouchable = false
            setBackgroundDrawable(null)
            setOnDismissListener {
                view.popupWindow = null
                view.activeRadialView = null
            }
        }

        val xOff = (anchorRect.centerX() - (110 * view.density)).toInt().coerceAtLeast(0)
        val yOff = -(view.height - anchorRect.top.toInt() + (200 * view.density).toInt())
        popup.showAtLocation(view, Gravity.BOTTOM or Gravity.START, xOff, -yOff)
        view.popupWindow = popup

        view.activeRadialView = menu
        val viewLoc = IntArray(2)
        view.getLocationOnScreen(viewLoc)
        menu.post {
            val menuLoc = IntArray(2)
            menu.getLocationOnScreen(menuLoc)
            view.radialPopupOffsetX = (menuLoc[0] - viewLoc[0]).toFloat()
            view.radialPopupOffsetY = (menuLoc[1] - viewLoc[1]).toFloat()
        }
    }

    fun showRadialLanguageMenu(anchorRect: RectF) {
        view.dismissPopup()

        val menu = RadialLanguageMenu(view.context, view.theme, view.currentInputMode) { selectedOption ->
            view.dismissPopup()
            view.activeRadialView = null
            view.onLanguageSelectedListener?.invoke(selectedOption)
        }

        val menuSize = (220 * view.density + 50 * view.density).toInt()
        val popup = PopupWindow(
            menu,
            menuSize,
            menuSize,
            false
        ).apply {
            isOutsideTouchable = false
            isTouchable = false
            setBackgroundDrawable(null)
            setOnDismissListener {
                view.popupWindow = null
                view.activeRadialView = null
            }
        }

        val xOff = (anchorRect.centerX() - menuSize / 2f).toInt().coerceAtLeast(0)
        val yOff = -(view.height - anchorRect.top.toInt() + (menuSize / 2f + 20 * view.density).toInt())
        popup.showAtLocation(view, Gravity.BOTTOM or Gravity.START, xOff, -yOff)
        view.popupWindow = popup

        view.activeRadialView = menu
        val viewLoc = IntArray(2)
        view.getLocationOnScreen(viewLoc)
        menu.post {
            val menuLoc = IntArray(2)
            menu.getLocationOnScreen(menuLoc)
            view.radialPopupOffsetX = (menuLoc[0] - viewLoc[0]).toFloat()
            view.radialPopupOffsetY = (menuLoc[1] - viewLoc[1]).toFloat()
        }
    }

    // ── Alt Popup ────────────────────────────────────────────────

    fun showAltPopup(key: KeyModel, anchorRect: RectF) {
        view.dismissPopup()

        val allChars = listOf(key.label) + key.altChars
        view.altPopupChars = allChars
        view.altPopupOriginKey = key
        view.altPopupActive = true

        val cellW = 42f * view.density
        val cellH = 50f * view.density
        val padding = 6f * view.density
        val gap = 2f * view.density
        val totalW = allChars.size * cellW + (allChars.size - 1) * gap + padding * 2

        val startX = (anchorRect.centerX() - totalW / 2f).coerceIn(0f, view.width - totalW)
        val startY = (anchorRect.top - cellH - padding * 2 - 4f * view.density).coerceAtLeast(0f)

        view.altPopupBgRect = RectF(startX, startY, startX + totalW, startY + cellH + padding * 2)

        view.altPopupRects = allChars.mapIndexed { i, _ ->
            RectF(
                startX + padding + i * (cellW + gap),
                startY + padding,
                startX + padding + i * (cellW + gap) + cellW,
                startY + padding + cellH
            )
        }

        view.altPopupHoveredIndex = 0
        view.invalidate()
    }

    fun updateAltPopupHover(x: Float, y: Float) {
        val extendedRect = RectF(
            view.altPopupBgRect.left - 20f * view.density,
            view.altPopupBgRect.top - 10f * view.density,
            view.altPopupBgRect.right + 20f * view.density,
            view.altPopupBgRect.bottom + 80f * view.density
        )
        if (!extendedRect.contains(x, y)) {
            view.altPopupHoveredIndex = -1
            view.invalidate()
            return
        }

        var bestIdx = -1
        var bestDist = Float.MAX_VALUE
        view.altPopupRects.forEachIndexed { i, rect ->
            val cx = rect.centerX()
            val dist = Math.abs(x - cx)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }
        if (bestIdx >= 0 && bestDist > 40f * view.density) {
            bestIdx = -1
        }
        if (view.altPopupHoveredIndex != bestIdx) {
            view.altPopupHoveredIndex = bestIdx
            if (bestIdx >= 0 && view.hapticEnabled) view.vibrateShort()
            view.invalidate()
        }
    }
}

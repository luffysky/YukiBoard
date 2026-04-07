package com.snowrealm.yukiboard.ui.panel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.yuki.SecretMessageManager

/**
 * 悄悄話解密面板 — Canvas 繪製版。
 *
 * 因為 IME inputView 內的 EditText 無法正常獲取焦點，
 * 改用 Canvas 繪製 UI + 點擊按鈕彈出 Dialog 輸入。
 *
 * 流程：
 * 1. 點「設定密碼」→ 彈出對話框輸入密碼
 * 2. 點「貼上密文」→ 從剪貼簿自動貼上
 * 3. 點「解密」/「加密」→ 顯示結果
 * 4. 點「複製結果」→ 複製到剪貼簿
 */
class DecryptPanelView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val secretManager = SecretMessageManager()

    var theme: KeyboardTheme? = null
        set(value) { field = value; invalidate() }

    /** 加密後回呼（送出加密訊息到輸入框） */
    var onEncryptedResult: ((String) -> Unit)? = null

    /** 需要彈出輸入對話框的回呼（由 Service 實作，因為 IME 內部無法直接彈 Dialog） */
    var onShowInputDialog: ((title: String, hint: String, currentValue: String, isPassword: Boolean, onResult: (String) -> Unit) -> Unit)? = null

    // ── 狀態 ──
    private var password = ""
    private var cipherText = ""
    private var resultText = ""
    private var statusMsg = ""
    private var statusIsError = false
    private var isEncryptResult = false  // true=加密結果 false=解密結果

    // ── canvas 輸入模式 ──
    private var isInputMode = false
    private var activeField = ""  // "password" or "cipher"
    private val inputBuffer = StringBuilder()
    private var composingHint = ""

    // ── Paints ──
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val descPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // ── 按鈕區域 ──
    private data class BtnArea(val rect: RectF, val id: String)
    private val buttons = mutableListOf<BtnArea>()
    private var pressedBtn: String? = null

    // ── 捲動 ──
    private var scrollY = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var contentHeight = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buttons.clear()

        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 14f * density
        var y = -scrollY + 10f * density

        // 背景
        val panelBg = theme?.panelBackgroundColorInt ?: 0xFF0D1B2A.toInt()
        bgPaint.color = panelBg
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 標題
        titlePaint.textSize = 16f * density
        titlePaint.color = 0xFFE8F4FF.toInt()
        titlePaint.isFakeBoldText = true
        canvas.drawText("🔐 悄悄話加解密", pad, y + titlePaint.textSize, titlePaint)
        y += titlePaint.textSize + 4f * density

        descPaint.textSize = 11f * density
        descPaint.color = 0xFF667788.toInt()
        canvas.drawText("使用約定密碼加密/解密訊息", pad, y + descPaint.textSize, descPaint)
        y += descPaint.textSize + 12f * density

        // ── 密碼欄 ──
        y = drawFieldCard(canvas, y, pad, w, "密碼",
            if (password.isEmpty()) "點擊設定密碼" else "●".repeat(password.length.coerceAtMost(12)),
            "password", if (password.isEmpty()) 0xFF556677.toInt() else 0xFFE8F4FF.toInt())

        // ── 密文欄 ──
        y = drawFieldCard(canvas, y, pad, w, "加密訊息",
            if (cipherText.isEmpty()) "點擊貼上或輸入" else cipherText.take(60) + if (cipherText.length > 60) "..." else "",
            "cipher", if (cipherText.isEmpty()) 0xFF556677.toInt() else 0xFFE8F4FF.toInt())

        // ── 按鈕列 ──
        val btnH = 36f * density
        val btnGap = 8f * density
        val btnW = (w - pad * 2 - btnGap * 2) / 3f

        y += 4f * density
        drawButton(canvas, RectF(pad, y, pad + btnW, y + btnH), "🔓 解密", "decrypt", 0xFF1E5A3A.toInt())
        drawButton(canvas, RectF(pad + btnW + btnGap, y, pad + btnW * 2 + btnGap, y + btnH), "🔒 加密", "encrypt", 0xFF1E3A5F.toInt())
        drawButton(canvas, RectF(pad + btnW * 2 + btnGap * 2, y, w - pad, y + btnH), "📋 貼上", "paste", 0xFF2A3A4A.toInt())
        y += btnH + 8f * density

        // ── 狀態 ──
        if (statusMsg.isNotEmpty()) {
            statusPaint.textSize = 12f * density
            statusPaint.color = if (statusIsError) 0xFFFF8888.toInt() else 0xFF88FF88.toInt()
            canvas.drawText(statusMsg, pad, y + statusPaint.textSize, statusPaint)
            y += statusPaint.textSize + 6f * density
        }

        // ── 結果區 ──
        if (resultText.isNotEmpty()) {
            val resultCardTop = y
            val resultPad = 10f * density

            resultPaint.textSize = 12f * density
            resultPaint.color = 0xFF9ECFFF.toInt()

            // 結果標題
            val resultLabel = if (isEncryptResult) "加密結果" else "解密結果"
            y += resultPad

            // 計算文字行數
            val maxLineW = w - pad * 2 - resultPad * 2
            val lines = wrapText(resultText, resultPaint, maxLineW)
            val lineH = resultPaint.textSize * 1.4f
            val resultCardH = resultPad * 2 + resultPaint.textSize + 4f * density + lines.size * lineH + 8f * density + btnH

            // 結果卡片背景
            cardPaint.color = 0xFF0A1520.toInt()
            canvas.drawRoundRect(RectF(pad, resultCardTop, w - pad, resultCardTop + resultCardH), 8f * density, 8f * density, cardPaint)

            canvas.drawText(resultLabel, pad + resultPad, y + resultPaint.textSize, resultPaint)
            y += resultPaint.textSize + 4f * density

            // 結果文字
            resultPaint.color = 0xFFE8F4FF.toInt()
            resultPaint.textSize = 13f * density
            for (line in lines) {
                canvas.drawText(line, pad + resultPad, y + resultPaint.textSize, resultPaint)
                y += lineH
            }
            y += 4f * density

            // 複製按鈕 + 送出按鈕
            val copyBtnW = if (isEncryptResult) (w - pad * 2 - resultPad * 2 - btnGap) / 2f else w - pad * 2 - resultPad * 2
            drawButton(canvas, RectF(pad + resultPad, y, pad + resultPad + copyBtnW, y + btnH), "📋 複製", "copy_result", 0xFF2A4A3A.toInt())
            if (isEncryptResult) {
                drawButton(canvas, RectF(pad + resultPad + copyBtnW + btnGap, y, w - pad - resultPad, y + btnH), "📤 送出", "send", 0xFF3A4A2A.toInt())
            }
            y += btnH + resultPad
        }

        y += 20f * density
        contentHeight = y + scrollY
    }

    private fun drawFieldCard(canvas: Canvas, startY: Float, pad: Float, w: Float,
                              label: String, value: String, btnId: String, valueColor: Int): Float {
        val cardH = 52f * density
        val rect = RectF(pad, startY, w - pad, startY + cardH)
        val isFocused = isInputMode && activeField == btnId

        cardPaint.color = if (isFocused) 0xFF1E3A5A.toInt() else 0xFF1A2A3A.toInt()
        canvas.drawRoundRect(rect, 8f * density, 8f * density, cardPaint)

        // 邊框（聚焦時高亮）
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (isFocused) 1.5f * density else 1f * density
            color = if (isFocused) 0xFF9ECFFF.toInt() else 0xFF334455.toInt()
        }
        canvas.drawRoundRect(rect, 8f * density, 8f * density, borderPaint)

        // 標籤
        fieldPaint.textSize = 10f * density
        fieldPaint.color = 0xFF9ECFFF.toInt()
        canvas.drawText(label, pad + 12f * density, startY + 16f * density, fieldPaint)

        // 值（聚焦時顯示 inputBuffer）
        fieldPaint.textSize = 14f * density
        if (isFocused) {
            val displayText = if (btnId == "password") {
                if (inputBuffer.isEmpty()) "輸入密碼..." else "●".repeat(inputBuffer.length)
            } else {
                val buf = if (composingHint.isNotEmpty()) inputBuffer.toString() + composingHint else inputBuffer.toString()
                if (buf.isEmpty()) "輸入文字..." else buf.take(60)
            }
            fieldPaint.color = if (inputBuffer.isEmpty()) 0xFF556677.toInt() else 0xFFE8F4FF.toInt()
            canvas.drawText(displayText, pad + 12f * density, startY + 38f * density, fieldPaint)
        } else {
            fieldPaint.color = valueColor
            canvas.drawText(value, pad + 12f * density, startY + 38f * density, fieldPaint)
        }

        buttons.add(BtnArea(rect, btnId))
        return startY + cardH + 8f * density
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, id: String, color: Int) {
        val c = if (pressedBtn == id) brighten(color, 30) else color
        cardPaint.color = c
        canvas.drawRoundRect(rect, 8f * density, 8f * density, cardPaint)

        btnTextPaint.textSize = 13f * density
        btnTextPaint.color = 0xFFE8F4FF.toInt()
        canvas.drawText(label, rect.centerX(), rect.centerY() + btnTextPaint.textSize * 0.35f, btnTextPaint)

        buttons.add(BtnArea(rect, id))
    }

    private fun brighten(color: Int, amount: Int): Int {
        val r = ((color shr 16) and 0xFF) + amount
        val g = ((color shr 8) and 0xFF) + amount
        val b = (color and 0xFF) + amount
        return (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, maxWidth, null)
            if (count <= 0) break
            lines.add(remaining.substring(0, count))
            remaining = remaining.substring(count)
            if (lines.size >= 6) {
                if (remaining.isNotEmpty()) lines[lines.size - 1] = lines.last() + "..."
                break
            }
        }
        return lines.ifEmpty { listOf(text.take(30)) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isDragging = false
                pressedBtn = buttons.firstOrNull { it.rect.contains(event.x, event.y + scrollY) }?.id
                    ?: buttons.firstOrNull { it.rect.contains(event.x, event.y) }?.id
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - event.y
                if (Math.abs(dy) > 8f * density) isDragging = true
                if (isDragging) {
                    scrollY = (scrollY + dy).coerceIn(0f, (contentHeight - height).coerceAtLeast(0f))
                    lastTouchY = event.y
                    pressedBtn = null
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && pressedBtn != null) {
                    handleClick(pressedBtn!!)
                }
                pressedBtn = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleClick(id: String) {
        when (id) {
            "password" -> {
                // 進入密碼輸入模式
                isInputMode = true
                activeField = "password"
                inputBuffer.clear()
                if (password.isNotEmpty()) inputBuffer.append(password)
                composingHint = ""
                invalidate()
            }
            "cipher" -> {
                // 先嘗試從剪貼簿貼上密文
                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?.primaryClip?.getItemAt(0)?.text?.toString()
                if (!clip.isNullOrEmpty() && secretManager.isEncryptedMessage(clip)) {
                    cipherText = clip
                    setStatus("已從剪貼簿貼上加密訊息", false)
                    invalidate()
                } else {
                    // 進入密文輸入模式
                    isInputMode = true
                    activeField = "cipher"
                    inputBuffer.clear()
                    if (cipherText.isNotEmpty()) inputBuffer.append(cipherText)
                    composingHint = ""
                    invalidate()
                }
            }
            "paste" -> {
                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?.primaryClip?.getItemAt(0)?.text?.toString()
                if (!clip.isNullOrEmpty()) {
                    cipherText = clip
                    setStatus("已貼上", false)
                } else {
                    setStatus("剪貼簿是空的", true)
                }
                invalidate()
            }
            "decrypt" -> doDecrypt()
            "encrypt" -> doEncrypt()
            "copy_result" -> {
                if (resultText.isNotEmpty()) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("yuki_decrypt", resultText))
                    setStatus("✅ 已複製到剪貼簿", false)
                }
            }
            "send" -> {
                if (resultText.isNotEmpty() && isEncryptResult) {
                    onEncryptedResult?.invoke(resultText)
                    setStatus("✅ 已送出加密訊息", false)
                }
            }
        }
    }

    private fun doDecrypt() {
        if (password.isEmpty()) { setStatus("請先設定密碼", true); return }
        if (cipherText.isEmpty()) { setStatus("請先貼上加密訊息", true); return }
        if (!secretManager.isEncryptedMessage(cipherText)) {
            setStatus("格式錯誤（需以 [YUKI🔐] 開頭）", true); return
        }

        val result = secretManager.decryptWithPassword(cipherText, password)
        if (result != null) {
            resultText = result
            isEncryptResult = false
            setStatus("✅ 解密成功", false)
        } else {
            resultText = ""
            setStatus("❌ 密碼錯誤，無法解密", true)
        }
        invalidate()
    }

    private fun doEncrypt() {
        if (password.isEmpty()) { setStatus("請先設定密碼", true); return }
        if (password.length < 4) { setStatus("密碼至少 4 個字元", true); return }
        if (cipherText.isEmpty()) { setStatus("請先輸入要加密的訊息", true); return }

        resultText = secretManager.encryptWithPassword(cipherText, password)
        isEncryptResult = true
        setStatus("✅ 加密成功", false)
        invalidate()
    }

    private fun setStatus(msg: String, error: Boolean) {
        statusMsg = msg
        statusIsError = error
        invalidate()
    }

    // =========================================================================
    // 鍵盤輸入 API（canvas inputMode）
    // =========================================================================

    val isInInputMode: Boolean get() = isInputMode

    private fun commitFieldValue() {
        when (activeField) {
            "password" -> password = inputBuffer.toString()
            "cipher" -> cipherText = inputBuffer.toString()
        }
    }

    fun onCharInput(text: String) {
        if (!isInputMode) return
        inputBuffer.append(text)
        commitFieldValue()
        invalidate()
    }

    fun onDeleteInput() {
        if (!isInputMode || inputBuffer.isEmpty()) return
        inputBuffer.deleteCharAt(inputBuffer.lastIndex)
        commitFieldValue()
        invalidate()
    }

    fun onSpaceInput() {
        if (!isInputMode) return
        inputBuffer.append(' ')
        commitFieldValue()
        invalidate()
    }

    fun onEnterInput() {
        if (!isInputMode) return
        commitFieldValue()
        isInputMode = false
        composingHint = ""
        invalidate()
    }

    fun exitInputMode() {
        if (isInputMode) commitFieldValue()
        isInputMode = false
        composingHint = ""
        invalidate()
    }

    fun onComposedTextInput(text: String) {
        if (!isInputMode) return
        inputBuffer.append(text)
        composingHint = ""
        commitFieldValue()
        invalidate()
    }

    fun updateComposingHint(hint: String) {
        composingHint = hint
        invalidate()
    }
}

package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.remote.ConnectionState
import com.snowrealm.yukiboard.remote.RemoteType
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 遠端鍵盤控制面板 — 選擇藍牙/WiFi 模式，顯示連線狀態。
 *
 * ┌──────────────────────────────────────────┐
 * │  🔗 遠端鍵盤                              │
 * │                                          │
 * │  ┌─────────────┐  ┌─────────────┐        │
 * │  │  🔵 藍牙     │  │  📶 WiFi     │        │
 * │  │  HID 鍵盤   │  │  WebSocket  │        │
 * │  │             │  │             │        │
 * │  │  [啟動]     │  │  [啟動]     │        │
 * │  └─────────────┘  └─────────────┘        │
 * │                                          │
 * │  狀態: 未連線                              │
 * │  裝置: —                                  │
 * │  WiFi URL: —                              │
 * └──────────────────────────────────────────┘
 */
class RemoteKeyboardPanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 14f * density
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 11f * density
    }
    private val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 11f * density
    }

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    // 狀態
    var btState: ConnectionState = ConnectionState.IDLE
        set(value) { field = value; invalidate() }
    var wifiState: ConnectionState = ConnectionState.IDLE
        set(value) { field = value; invalidate() }
    var connectedDeviceName: String = ""
        set(value) { field = value; invalidate() }
    var wifiUrl: String = ""
        set(value) { field = value; invalidate() }

    // 回呼
    var onBluetoothToggle: (() -> Unit)? = null
    var onWifiToggle: (() -> Unit)? = null

    private var btCardRect = RectF()
    private var wifiCardRect = RectF()
    private var btBtnRect = RectF()
    private var wifiBtnRect = RectF()

    private val cornerR = 10f * density
    private val headerH = 28f * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (200 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        bgPaint.color = theme.effectivePanelBackground()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 標題
        titlePaint.color = theme.candidateTextColorInt
        canvas.drawText("🔗 遠端鍵盤", 12f * density, headerH * 0.75f, titlePaint)

        // 兩張卡片
        val cardW = (width - 24 * density) / 2f
        val cardH = 100f * density
        val cardY = headerH + 8 * density
        val gap = 8f * density

        // ── 藍牙卡片 ──
        btCardRect = RectF(8 * density, cardY, 8 * density + cardW, cardY + cardH)
        drawModeCard(canvas, btCardRect, "🔵", "藍牙 HID", "模擬藍牙鍵盤", btState)

        // ── WiFi 卡片 ──
        wifiCardRect = RectF(8 * density + cardW + gap, cardY, width - 8 * density, cardY + cardH)
        drawModeCard(canvas, wifiCardRect, "📶", "WiFi", "WebSocket Server", wifiState)

        // 底部狀態列
        val statusY = cardY + cardH + 12 * density
        statusPaint.color = theme.candidateTextColorInt

        val stateText = when {
            btState == ConnectionState.CONNECTED -> "藍牙已連線"
            wifiState == ConnectionState.CONNECTED -> "WiFi 已連線"
            wifiState == ConnectionState.LISTENING -> "WiFi 等待連線中..."
            btState == ConnectionState.LISTENING -> "藍牙等待配對中..."
            btState == ConnectionState.CONNECTING || wifiState == ConnectionState.CONNECTING -> "連線中..."
            btState == ConnectionState.ERROR || wifiState == ConnectionState.ERROR -> "連線錯誤"
            else -> "未啟動"
        }
        canvas.drawText("狀態: $stateText", 12 * density, statusY, statusPaint)

        if (connectedDeviceName.isNotEmpty()) {
            canvas.drawText("裝置: $connectedDeviceName", 12 * density, statusY + 16 * density, statusPaint)
        }

        if (wifiUrl.isNotEmpty() && (wifiState == ConnectionState.LISTENING || wifiState == ConnectionState.CONNECTED)) {
            urlPaint.color = theme.candidateSelectedColorInt
            canvas.drawText("URL: $wifiUrl", 12 * density, statusY + 32 * density, urlPaint)
        }
    }

    private fun drawModeCard(canvas: Canvas, rect: RectF, icon: String, title: String, subtitle: String, state: ConnectionState) {
        // 卡片背景
        val isActive = state == ConnectionState.CONNECTED || state == ConnectionState.LISTENING
        cardPaint.color = if (isActive) {
            (theme.candidateSelectedColorInt and 0x00FFFFFF) or 0x25000000
        } else {
            theme.keyColorInt
        }
        canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

        // 連線狀態邊框
        if (isActive) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
                color = when (state) {
                    ConnectionState.CONNECTED -> 0xFF4CAF50.toInt()
                    ConnectionState.LISTENING -> 0xFFFFA726.toInt()
                    else -> theme.candidateSelectedColorInt
                }
            }
            canvas.drawRoundRect(rect, cornerR, cornerR, borderPaint)
        }

        // 圖示
        iconPaint.color = theme.keyLabelColorInt
        canvas.drawText(icon, rect.centerX(), rect.top + 30 * density, iconPaint)

        // 標題
        labelPaint.color = theme.keyLabelColorInt
        canvas.drawText(title, rect.centerX(), rect.top + 48 * density, labelPaint)

        // 副標題
        labelPaint.color = theme.candidateTextColorInt
        labelPaint.alpha = 150
        canvas.drawText(subtitle, rect.centerX(), rect.top + 62 * density, labelPaint)
        labelPaint.alpha = 255

        // 按鈕
        val btnW = 60f * density
        val btnH = 24f * density
        val btnRect = RectF(
            rect.centerX() - btnW / 2, rect.bottom - btnH - 8 * density,
            rect.centerX() + btnW / 2, rect.bottom - 8 * density
        )

        // 根據卡片位置記錄按鈕
        if (rect == btCardRect) btBtnRect = btnRect
        else wifiBtnRect = btnRect

        val isRunning = state != ConnectionState.IDLE && state != ConnectionState.DISCONNECTED && state != ConnectionState.ERROR
        cardPaint.color = if (isRunning) 0xFFC62828.toInt() else theme.candidateSelectedColorInt
        canvas.drawRoundRect(btnRect, btnH / 2, btnH / 2, cardPaint)

        btnPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText(
            if (isRunning) "停止" else "啟動",
            btnRect.centerX(), btnRect.centerY() + btnPaint.textSize / 3f, btnPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            if (btBtnRect.contains(event.x, event.y)) {
                onBluetoothToggle?.invoke()
                performClick()
                return true
            }
            if (wifiBtnRect.contains(event.x, event.y)) {
                onWifiToggle?.invoke()
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

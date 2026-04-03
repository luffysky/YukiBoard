package com.snowrealm.yukiboard.weather

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.snowrealm.yukiboard.auth.InviteCodeManager
import com.snowrealm.yukiboard.ui.auth.InviteCodeActivity
import com.snowrealm.yukiboard.ui.floating.FloatingPanel
import com.snowrealm.yukiboard.ui.floating.FloatingPanelConfig
import kotlinx.coroutines.*

/**
 * 天氣懸浮面板 — 位置 + 溫濕度 + 6 小時預報 + 雪凜短句。
 */
class WeatherFloatingPanel(
    context: Context,
    private val weatherManager: WeatherManager
) : FloatingPanel(context, FloatingPanelConfig(
    defaultWidthDp = 280,
    minWidthDp = 240,
    maxWidthDp = 360,
    defaultHeightDp = 200,
    minHeightDp = 160
)) {
    override val panelId = "weather"
    override val panelTitle = "🌤️ 天氣"

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val inviteManager = InviteCodeManager(context)

    private var cityText: TextView? = null
    private var tempText: TextView? = null
    private var conditionText: TextView? = null
    private var detailText: TextView? = null
    private var yukiCommentText: TextView? = null
    private var forecastContainer: LinearLayout? = null
    private var outfitText: TextView? = null

    override fun createContentView(inflater: LayoutInflater): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }

        // 城市 + 溫度
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        cityText = makeText(14f, 0xFFE8F4FF.toInt(), bold = true)
        tempText = makeText(28f, 0xFFFFFFFF.toInt(), bold = true)
        topRow.addView(cityText, lp(0, weight = 1f))
        topRow.addView(tempText)
        root.addView(topRow)

        // 天氣狀態 + 濕度/風速
        conditionText = makeText(13f, 0xFFB0D4FF.toInt())
        root.addView(conditionText)

        detailText = makeText(11f, 0xFF7FA8CC.toInt())
        root.addView(detailText)

        // 雪凜短句
        yukiCommentText = makeText(12f, 0xFF7FD8FF.toInt()).apply {
            setPadding(0, dp(6), 0, dp(4))
        }
        root.addView(yukiCommentText)

        // 6 小時預報（橫向捲動）
        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        forecastContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }
        scrollView.addView(forecastContainer)
        root.addView(scrollView)

        // 穿衣建議（需解鎖）
        outfitText = makeText(12f, 0xFFB0D4FF.toInt()).apply {
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(outfitText)

        return root
    }

    override fun onShown() {
        loadWeather()
    }

    override fun onDismissed() {
        scope.cancel()
    }

    private fun loadWeather() {
        scope.launch {
            val data = weatherManager.fetchWeather()
            if (data != null) bindWeatherData(data)
            else showNoData()
        }
    }

    private fun bindWeatherData(data: WeatherData) {
        val current = data.current

        cityText?.text = data.cityName
        tempText?.text = "${current.temperatureCelsius}°"
        conditionText?.text = "${current.condition.icon} ${current.description}"
        detailText?.text = "體感 ${current.feelsLikeCelsius}° | " +
            "濕度 ${current.humidity}% | 風速 ${current.windSpeedKmh}km/h"
        yukiCommentText?.text = "❄ ${weatherManager.getYukiComment(current)}"

        // 預報
        forecastContainer?.removeAllViews()
        data.hourlyForecast.forEach { snap ->
            forecastContainer?.addView(buildForecastItem(snap))
        }

        // 穿衣建議
        bindOutfitAdvice(data)
    }

    private fun bindOutfitAdvice(data: WeatherData) {
        val isUnlocked = inviteManager.isActivated()
        if (isUnlocked) {
            val phrases = weatherManager.getQuickPhrases(data.current)
            outfitText?.text = "👗 ${phrases.firstOrNull() ?: ""}"
        } else {
            outfitText?.text = "🔒 穿衣建議（輸入邀請碼解鎖）"
            outfitText?.setOnClickListener {
                val intent = Intent(context, InviteCodeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    private fun buildForecastItem(snap: WeatherSnapshot): LinearLayout {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val hour = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(snap.timestampMillis))

        col.addView(makeText(10f, 0xFF7FA8CC.toInt()).apply { text = hour })
        col.addView(makeText(16f, 0xFFFFFFFF.toInt()).apply { text = snap.condition.icon })
        col.addView(makeText(11f, 0xFFE8F4FF.toInt()).apply { text = "${snap.temperatureCelsius}°" })

        return col
    }

    private fun showNoData() {
        cityText?.text = "無法取得天氣"
        tempText?.text = "--°"
        conditionText?.text = "請確認定位權限和 API Key"
        detailText?.text = ""
        yukiCommentText?.text = "❄ 天氣資訊暫時無法使用"
    }

    private fun makeText(
        sizeSp: Float,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(context).apply {
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun lp(width: Int = LinearLayout.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            if (weight > 0f) this.weight = weight
        }

    private fun dp(value: Int): Int = dpToPx(value)
}

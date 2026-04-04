package com.snowrealm.yukiboard.claw.skills

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider
import com.snowrealm.yukiboard.weather.WeatherManager

class WeatherSkill(private val context: Context, private val provider: AIProvider? = null) : ClawSkill {
    override val id = "weather"
    override val name = "天氣助理"
    override val description = "自動取得天氣資訊與穿衣建議"
    override val tokensPerCall = 300

    private val weatherManager = WeatherManager(context)

    override suspend fun execute(input: ClawInput): ClawOutput {
        // 自動取得天氣（GPS 定位 + OpenWeatherMap）
        val weatherData = weatherManager.fetchWeather()

        if (weatherData == null) {
            // 無法取得天氣（無權限或無 API Key）
            return ClawOutput(
                listOf("無法取得天氣資訊，請確認已授權定位權限並設定 OpenWeatherMap API Key"),
                OutputType.REPLY, 0
            )
        }

        val snapshot = weatherData.current
        val city = weatherData.cityName

        // 無 AI Provider：用本地短句回應（0 token）
        if (provider == null) {
            val yukiComment = weatherManager.getYukiComment(snapshot)
            val quickPhrases = weatherManager.getQuickPhrases(snapshot)
            val header = "📍 $city  ${snapshot.temperatureCelsius}°C  ${snapshot.description}"
            return ClawOutput(
                listOf(header, yukiComment) + quickPhrases,
                OutputType.REPLY, 0
            )
        }

        // 有 AI Provider：生成更豐富的穿衣建議
        val weatherInfo = buildString {
            appendLine("城市: $city")
            appendLine("氣溫: ${snapshot.temperatureCelsius}°C（體感 ${snapshot.feelsLikeCelsius}°C）")
            appendLine("天氣: ${snapshot.description}")
            appendLine("濕度: ${snapshot.humidity}%")
            appendLine("風速: ${snapshot.windSpeedKmh} km/h")
            // 加入未來預報
            val forecasts = weatherData.hourlyForecast
            if (forecasts.isNotEmpty()) {
                appendLine("未來預報:")
                forecasts.forEachIndexed { i, f ->
                    appendLine("  ${(i + 1) * 3}h後: ${f.temperatureCelsius}°C ${f.description}")
                }
            }
        }

        val promptTemplate = try {
            context.resources.openRawResource(R.raw.claw_weather_outfit_prompt)
                .bufferedReader().readText()
        } catch (_: Exception) {
            "根據以下天氣資訊給出穿衣建議和出門提醒，100字以內，雪凜活潑風格。\n天氣資訊：{input}"
        }
        val prompt = promptTemplate
            .replace("{input}", weatherInfo)
            .replace("{city}", city)
            .replace("{temp}", snapshot.temperatureCelsius.toString())
            .replace("{condition}", snapshot.description)
            .replace("{humidity}", snapshot.humidity.toString())
            .replace("{wind}", snapshot.windSpeedKmh.toString())

        val systemPrompt = "你是雪凜天氣助理。活潑可愛，用繁體中文回答，100字以內。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        val header = "📍 $city  ${snapshot.temperatureCelsius}°C  ${snapshot.description}"
        return ClawOutput(
            listOf(header, result.toString().trim()),
            OutputType.REPLY, tokensPerCall
        )
    }
}

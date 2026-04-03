package com.snowrealm.yukiboard.weather

/**
 * 天氣狀態 enum — 對應 OpenWeatherMap 主要天氣分類。
 */
enum class WeatherCondition(val icon: String, val label: String) {
    SUNNY("☀️", "晴天"),
    CLOUDY("☁️", "多雲"),
    RAINY("🌧️", "下雨"),
    STORMY("⛈️", "暴風雨"),
    SNOWY("🌨️", "下雪"),
    FOGGY("🌫️", "起霧"),
    WINDY("💨", "大風");

    companion object {
        /** 從 OpenWeatherMap weather.main 字串對應 */
        fun fromOwmMain(main: String): WeatherCondition = when (main.lowercase()) {
            "clear" -> SUNNY
            "clouds" -> CLOUDY
            "rain", "drizzle" -> RAINY
            "thunderstorm" -> STORMY
            "snow" -> SNOWY
            "mist", "fog", "haze", "smoke" -> FOGGY
            else -> CLOUDY
        }
    }
}

/**
 * 單一時間點天氣資料。
 */
data class WeatherSnapshot(
    val temperatureCelsius: Int,
    val feelsLikeCelsius: Int,
    val condition: WeatherCondition,
    val humidity: Int,
    val windSpeedKmh: Int,
    val description: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * 完整天氣資料（含未來 6 小時預報）。
 */
data class WeatherData(
    val cityName: String,
    val current: WeatherSnapshot,
    val hourlyForecast: List<WeatherSnapshot> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis()
) {
    fun isExpired(maxAgeMillis: Long = 30 * 60 * 1000L): Boolean {
        return System.currentTimeMillis() - fetchedAt > maxAgeMillis
    }
}

package com.snowrealm.yukiboard.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.snowrealm.yukiboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * 天氣資料管理 — OpenWeatherMap API + 30 分鐘快取。
 *
 * 職責：定位 → 取 API → 解析 → 快取。
 * UI 層透過 [fetchWeather] 取得 [WeatherData]。
 */
class WeatherManager(private val context: Context) {

    private val apiKey: String = BuildConfig.OPENWEATHER_API_KEY

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedData: WeatherData? = null

    /** 取得天氣（有快取就用快取） */
    suspend fun fetchWeather(): WeatherData? {
        cachedData?.let { if (!it.isExpired()) return it }

        val location = getLastLocation() ?: return cachedData
        return fetchFromApi(location.latitude, location.longitude)
    }

    /** 強制重新抓取 */
    suspend fun refreshWeather(): WeatherData? {
        val location = getLastLocation() ?: return cachedData
        return fetchFromApi(location.latitude, location.longitude)
    }

    fun getCachedWeather(): WeatherData? = cachedData

    // ─────────────── 定位 ───────────────

    private suspend fun getLastLocation(): Location? {
        if (!hasLocationPermission()) return null

        return suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            try {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc -> cont.resume(loc) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (_: SecurityException) {
                cont.resume(null)
            }

            cont.invokeOnCancellation { cts.cancel() }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ─────────────── API ───────────────

    private suspend fun fetchFromApi(lat: Double, lon: Double): WeatherData? {
        if (apiKey.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val current = fetchCurrentWeather(lat, lon) ?: return@withContext null
                val hourly = fetchHourlyForecast(lat, lon)
                val data = WeatherData(
                    cityName = current.first,
                    current = current.second,
                    hourlyForecast = hourly
                )
                cachedData = data
                data
            } catch (_: Exception) {
                cachedData
            }
        }
    }

    private fun fetchCurrentWeather(lat: Double, lon: Double): Pair<String, WeatherSnapshot>? {
        val url = "https://api.openweathermap.org/data/2.5/weather" +
            "?lat=$lat&lon=$lon&appid=$apiKey&units=metric&lang=zh_tw"

        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) return null

        val json = Json.parseToJsonElement(response.body?.string() ?: return null).jsonObject
        val city = json["name"]?.jsonPrimitive?.content ?: "未知"
        val main = json["main"]?.jsonObject
        val weather = json["weather"]?.jsonArray?.firstOrNull()?.jsonObject
        val wind = json["wind"]?.jsonObject

        val snapshot = WeatherSnapshot(
            temperatureCelsius = main?.get("temp")?.jsonPrimitive?.double?.toInt() ?: 0,
            feelsLikeCelsius = main?.get("feels_like")?.jsonPrimitive?.double?.toInt() ?: 0,
            condition = WeatherCondition.fromOwmMain(
                weather?.get("main")?.jsonPrimitive?.content ?: "Clouds"
            ),
            humidity = main?.get("humidity")?.jsonPrimitive?.int ?: 0,
            windSpeedKmh = ((wind?.get("speed")?.jsonPrimitive?.double ?: 0.0) * 3.6).toInt(),
            description = weather?.get("description")?.jsonPrimitive?.content ?: ""
        )
        return city to snapshot
    }

    private fun fetchHourlyForecast(lat: Double, lon: Double): List<WeatherSnapshot> {
        val url = "https://api.openweathermap.org/data/2.5/forecast" +
            "?lat=$lat&lon=$lon&appid=$apiKey&units=metric&lang=zh_tw&cnt=6"

        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) return emptyList()

        val json = Json.parseToJsonElement(response.body?.string() ?: return emptyList()).jsonObject
        val list = json["list"]?.jsonArray ?: return emptyList()

        return list.mapNotNull { item ->
            val obj = item.jsonObject
            val main = obj["main"]?.jsonObject ?: return@mapNotNull null
            val weather = obj["weather"]?.jsonArray?.firstOrNull()?.jsonObject
            val wind = obj["wind"]?.jsonObject
            val dt = obj["dt"]?.jsonPrimitive?.long ?: 0L

            WeatherSnapshot(
                temperatureCelsius = main["temp"]?.jsonPrimitive?.double?.toInt() ?: 0,
                feelsLikeCelsius = main["feels_like"]?.jsonPrimitive?.double?.toInt() ?: 0,
                condition = WeatherCondition.fromOwmMain(
                    weather?.get("main")?.jsonPrimitive?.content ?: "Clouds"
                ),
                humidity = main["humidity"]?.jsonPrimitive?.int ?: 0,
                windSpeedKmh = ((wind?.get("speed")?.jsonPrimitive?.double ?: 0.0) * 3.6).toInt(),
                description = weather?.get("description")?.jsonPrimitive?.content ?: "",
                timestampMillis = dt * 1000L
            )
        }
    }

    // ─────────────── 雪凜天氣短句（0 token） ───────────────

    fun getYukiComment(snapshot: WeatherSnapshot): String {
        val temp = snapshot.temperatureCelsius
        val cond = snapshot.condition

        val tempComment = when {
            temp < 5 -> "凍死啦！不要出門 🥶"
            temp < 10 -> "超冷的，穿厚一點！"
            temp < 15 -> "有點涼，外套帶著 🧥"
            temp > 38 -> "要融化了...多喝水 🫠"
            temp > 33 -> "好熱！防曬做好 🥵"
            temp > 28 -> "有點熱，注意補水 ☀️"
            else -> "溫度剛好～"
        }

        val condComment = when (cond) {
            WeatherCondition.RAINY -> "記得帶傘！🌧️"
            WeatherCondition.STORMY -> "暴風雨...待在室內吧 ⛈️"
            WeatherCondition.SNOWY -> "下雪了！好浪漫 ✨"
            WeatherCondition.FOGGY -> "起霧了，開車小心 🌫️"
            WeatherCondition.WINDY -> "風好大，頭髮會亂 💨"
            WeatherCondition.SUNNY -> "天氣超好！出去走走 ☀️"
            WeatherCondition.CLOUDY -> "陰天，適合待在家 ☁️"
        }

        return "$tempComment $condComment"
    }

    fun getQuickPhrases(snapshot: WeatherSnapshot): List<String> {
        val phrases = mutableListOf<String>()
        val temp = snapshot.temperatureCelsius

        // 穿衣建議
        when {
            temp < 10 -> phrases.add("今天超冷，穿羽絨外套")
            temp < 18 -> phrases.add("今天有點涼，帶件外套")
            temp > 30 -> phrases.add("今天好熱，穿短袖就好")
        }

        // 天氣相關
        when (snapshot.condition) {
            WeatherCondition.RAINY, WeatherCondition.STORMY ->
                phrases.add("今天會下雨，記得帶傘")
            WeatherCondition.SUNNY ->
                phrases.add("今天天氣很好")
            else -> {}
        }

        // 通用
        phrases.add("${snapshot.temperatureCelsius}°C ${snapshot.condition.label}")

        return phrases
    }
}

package com.snowrealm.yukiboard.gif

import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.ui.panel.GifPanelView.GifItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Giphy API 客戶端 — trending + search。
 *
 * API Key 從 BuildConfig.GIPHY_API_KEY 讀取（.env 注入）。
 * 回傳 [GifItem] 列表供 GifPanelView 使用。
 */
class GiphyApiClient {

    private val apiKey: String = BuildConfig.GIPHY_API_KEY
    private val baseUrl = "https://api.giphy.com/v1/gifs"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 是否有有效的 API Key */
    fun isAvailable(): Boolean = apiKey.isNotBlank()

    /** 取得趨勢 GIF */
    suspend fun fetchTrending(limit: Int = 20, offset: Int = 0): List<GifItem> {
        if (!isAvailable()) return emptyList()
        val url = "$baseUrl/trending?api_key=$apiKey&limit=$limit&offset=$offset&rating=g"
        return fetchAndParse(url)
    }

    /** 搜尋 GIF */
    suspend fun search(query: String, limit: Int = 20, offset: Int = 0): List<GifItem> {
        if (!isAvailable() || query.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/search?api_key=$apiKey&q=$encoded&limit=$limit&offset=$offset&rating=g&lang=zh-TW"
        return fetchAndParse(url)
    }

    private suspend fun fetchAndParse(url: String): List<GifItem> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = Json.parseToJsonElement(body).jsonObject
            val dataArray = json["data"]?.jsonArray ?: return@withContext emptyList()

            dataArray.mapNotNull { parseGifItem(it.jsonObject) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseGifItem(obj: JsonObject): GifItem? {
        val id = obj["id"]?.jsonPrimitive?.content ?: return null
        val title = obj["title"]?.jsonPrimitive?.content ?: ""
        val images = obj["images"]?.jsonObject ?: return null

        val previewUrl = images["fixed_width_small"]?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
            ?: images["preview_gif"]?.jsonObject
                ?.get("url")?.jsonPrimitive?.content
            ?: return null

        val fullUrl = images["original"]?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
            ?: images["fixed_width"]?.jsonObject
                ?.get("url")?.jsonPrimitive?.content
            ?: previewUrl

        return GifItem(
            id = id,
            title = title,
            previewUrl = previewUrl,
            fullUrl = fullUrl
        )
    }
}

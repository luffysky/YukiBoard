package com.snowrealm.yukiboard.sticker

import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.app.net.AppHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Insight Engine 公開貼圖 API 客戶端。
 *
 * 呼叫 `GET /api/public/stickers?type=sticker` 取得後台上架的貼圖包，
 * 使用 `X-Api-Key` 驗證（與 Claw AI 共用 PLATFORM_API_KEY）。
 */
class InsightStickerApi {

    private val client = AppHttpClient.newBuilder(AppHttpClient.Profile.STANDARD).build()

    private val baseUrl = BuildConfig.INSIGHT_API_BASE_URL.trimEnd('/')

    /**
     * 拉取所有啟用中的貼圖包（含每張貼圖 URL）。
     * @param type "sticker" 或 "emoji"
     */
    suspend fun fetchStickerPacks(type: String = "sticker"): List<RemoteStickerPack> =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.PLATFORM_API_KEY
            if (apiKey.isBlank()) return@withContext emptyList()

            val request = Request.Builder()
                .url("$baseUrl/api/public/stickers?type=$type")
                .header("X-Api-Key", apiKey)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            parsePacks(body)
        }

    private fun parsePacks(json: String): List<RemoteStickerPack> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("packs") ?: return emptyList()
        val packs = mutableListOf<RemoteStickerPack>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val stickersArr = obj.optJSONArray("stickers")
            val stickers = mutableListOf<RemoteSticker>()
            if (stickersArr != null) {
                for (j in 0 until stickersArr.length()) {
                    val s = stickersArr.getJSONObject(j)
                    stickers.add(RemoteSticker(
                        id = s.getString("id"),
                        url = s.getString("url")
                    ))
                }
            }
            packs.add(RemoteStickerPack(
                id = obj.getString("id"),
                name = obj.getString("name"),
                coverUrl = obj.optString("coverUrl", ""),
                columnsPerRow = obj.optInt("columnsPerRow", 5),
                stickers = stickers
            ))
        }
        return packs
    }
}

/** 遠端貼圖包 */
data class RemoteStickerPack(
    val id: String,
    val name: String,
    val coverUrl: String,
    val columnsPerRow: Int,
    val stickers: List<RemoteSticker>
)

/** 單張遠端貼圖 */
data class RemoteSticker(
    val id: String,
    val url: String
)

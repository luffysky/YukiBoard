package com.snowrealm.yukiboard.sticker

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * 貼圖資料來源 — 協調 API 同步 + 本地快取。
 *
 * - 啟動時讀本地 JSON 快取，立即提供上次的貼圖包
 * - 背景呼叫 Insight API 同步最新資料，更新後通知 UI
 * - 圖片由 [StickerImageCache] 管理（記憶體 + 磁碟兩級快取）
 */
class StickerRepository(context: Context) {

    private val api = InsightStickerApi()
    val imageCache = StickerImageCache(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sticker_repo", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 目前的貼圖包（含遠端 + 預設合併） */
    var packs: List<RemoteStickerPack> = emptyList()
        private set

    /** 資料更新回呼 */
    var onPacksUpdated: (() -> Unit)? = null

    init {
        // 先載入本地快取，若無則使用內建預設包
        val cached = loadCachedPacks()
        packs = cached.ifEmpty { defaultPacks() }
    }

    /** 背景同步（IME 啟動時呼叫） */
    fun syncInBackground() {
        scope.launch {
            try {
                val remote = withContext(Dispatchers.IO) { api.fetchStickerPacks("sticker") }
                if (remote.isNotEmpty()) {
                    packs = remote
                    saveCachedPacks(remote)
                    // 預載封面圖
                    launch(Dispatchers.IO) {
                        try {
                            remote.forEach { pack ->
                                if (pack.coverUrl.isNotEmpty()) {
                                    imageCache.getBitmap(pack.coverUrl)
                                }
                                // 預載前幾張貼圖
                                pack.stickers.take(8).forEach { imageCache.getBitmap(it.url) }
                            }
                        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                        catch (e: Exception) { android.util.Log.w("Sticker", "Prefetch failed: ${e.message}") }
                    }
                    onPacksUpdated?.invoke()
                }
            } catch (_: Exception) {
                // 網路失敗靜默，繼續使用本地快取
            }
        }
    }

    /** 取得貼圖圖片（非同步，返回快取中的 Bitmap 或 null） */
    suspend fun getStickerBitmap(url: String): Bitmap? = imageCache.getBitmap(url)

    fun destroy() {
        scope.cancel()
        onPacksUpdated = null
    }

    // ── 本地 JSON 快取 ──

    private fun saveCachedPacks(packs: List<RemoteStickerPack>) {
        val arr = JSONArray()
        for (pack in packs) {
            val obj = JSONObject().apply {
                put("id", pack.id)
                put("name", pack.name)
                put("coverUrl", pack.coverUrl)
                put("columnsPerRow", pack.columnsPerRow)
                val stickers = JSONArray()
                pack.stickers.forEach { s ->
                    stickers.put(JSONObject().apply {
                        put("id", s.id)
                        put("url", s.url)
                    })
                }
                put("stickers", stickers)
            }
            arr.put(obj)
        }
        prefs.edit().putString("packs_json", arr.toString()).apply()
    }

    private fun loadCachedPacks(): List<RemoteStickerPack> {
        val json = prefs.getString("packs_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val result = mutableListOf<RemoteStickerPack>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val stickersArr = obj.optJSONArray("stickers")
                val stickers = mutableListOf<RemoteSticker>()
                if (stickersArr != null) {
                    for (j in 0 until stickersArr.length()) {
                        val s = stickersArr.getJSONObject(j)
                        stickers.add(RemoteSticker(s.getString("id"), s.getString("url")))
                    }
                }
                result.add(RemoteStickerPack(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    coverUrl = obj.optString("coverUrl", ""),
                    columnsPerRow = obj.optInt("columnsPerRow", 5),
                    stickers = stickers
                ))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 內建預設貼圖包（無網路時使用，URL 為空表示本地佔位） */
    private fun defaultPacks(): List<RemoteStickerPack> = listOf(
        RemoteStickerPack(
            id = "yuki", name = "雪凜", coverUrl = "", columnsPerRow = 4,
            stickers = listOf(
                RemoteSticker("yuki_hello", "local:你好"),
                RemoteSticker("yuki_thanks", "local:謝謝"),
                RemoteSticker("yuki_happy", "local:開心"),
                RemoteSticker("yuki_sad", "local:難過"),
                RemoteSticker("yuki_angry", "local:生氣"),
                RemoteSticker("yuki_love", "local:愛心"),
                RemoteSticker("yuki_sleep", "local:睡覺"),
                RemoteSticker("yuki_cry", "local:哭哭"),
                RemoteSticker("yuki_laugh", "local:大笑"),
                RemoteSticker("yuki_shock", "local:驚嚇"),
                RemoteSticker("yuki_wink", "local:眨眼"),
                RemoteSticker("yuki_cool", "local:酷")
            )
        ),
        RemoteStickerPack(
            id = "snow_animals", name = "雪地動物", coverUrl = "", columnsPerRow = 4,
            stickers = listOf(
                RemoteSticker("polar_hi", "local:嗨"),
                RemoteSticker("polar_ok", "local:OK"),
                RemoteSticker("penguin_dance", "local:跳舞"),
                RemoteSticker("penguin_wave", "local:掰掰"),
                RemoteSticker("fox_smile", "local:微笑"),
                RemoteSticker("fox_sleep", "local:睡覺"),
                RemoteSticker("rabbit_hop", "local:蹦蹦跳"),
                RemoteSticker("rabbit_eat", "local:吃東西"),
                RemoteSticker("owl_think", "local:思考")
            )
        )
    )
}

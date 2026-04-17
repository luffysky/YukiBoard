package com.snowrealm.yukiboard.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.snowrealm.yukiboard.app.net.AppHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 貼圖圖片快取：Memory LruCache + 磁碟快取。
 *
 * 流程：記憶體 → 磁碟 → 網路下載 → 寫入兩級快取。
 * 圖片統一縮放到 [targetSize] px 正方形，節省記憶體。
 */
class StickerImageCache(context: Context, private val targetSize: Int = 192) {

    private val diskCacheDir = File(context.cacheDir, "stickers").also { it.mkdirs() }

    // 記憶體快取：最多 8 MB
    private val memCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val client = AppHttpClient.newBuilder(AppHttpClient.Profile.STANDARD).build()

    /**
     * 取得貼圖 Bitmap（快取優先）。
     * @return Bitmap 或 null（下載失敗）
     */
    suspend fun getBitmap(url: String): Bitmap? {
        val key = urlToKey(url)

        // 1. 記憶體快取
        memCache.get(key)?.let { return it }

        // 2. 磁碟快取
        val diskFile = File(diskCacheDir, key)
        if (diskFile.exists()) {
            val bmp = decodeSized(diskFile)
            if (bmp != null) {
                memCache.put(key, bmp)
                return bmp
            }
        }

        // 3. 網路下載
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val bytes = response.body?.bytes() ?: return@withContext null

                // 寫入磁碟
                FileOutputStream(diskFile).use { it.write(bytes) }
                trimDiskCacheIfNeeded()

                // 解碼縮放
                val bmp = decodeSized(bytes)
                if (bmp != null) memCache.put(key, bmp)
                bmp
            } catch (_: Exception) {
                null
            }
        }
    }

    /** 預載一組 URL（背景批次下載） */
    suspend fun preload(urls: List<String>) {
        urls.forEach { getBitmap(it) }
    }

    private val maxDiskBytes = 50L * 1024 * 1024 // 50MB

    private fun trimDiskCacheIfNeeded() {
        val files = diskCacheDir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxDiskBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxDiskBytes * 8 / 10) return
            total -= file.length()
            file.delete()
        }
    }

    /** 清除所有快取 */
    fun clearAll() {
        memCache.evictAll()
        diskCacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun decodeSized(file: File): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        opts.inSampleSize = calcInSampleSize(opts.outWidth, opts.outHeight)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun decodeSized(bytes: ByteArray): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        opts.inSampleSize = calcInSampleSize(opts.outWidth, opts.outHeight)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun calcInSampleSize(w: Int, h: Int): Int {
        var inSampleSize = 1
        val maxDim = maxOf(w, h)
        while (maxDim / inSampleSize > targetSize * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun urlToKey(url: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        // 保留副檔名方便除錯
        val ext = url.substringAfterLast('.', "webp").take(4)
        return "$hash.$ext"
    }
}

package com.snowrealm.yukiboard.gif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * GIF 預覽圖快取 — 記憶體 + 磁碟兩級。
 *
 * 注意：此快取存靜態預覽幀（Bitmap），不存完整 GIF 動畫。
 * 完整 GIF 播放由 GifPanelView 用 URL 直接載入。
 */
class GifImageCache(context: Context) {

    private val diskDir = File(context.cacheDir, "gif_preview").also { it.mkdirs() }
    private val targetSize = 200

    private val memCache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getPreviewBitmap(url: String): Bitmap? {
        val key = urlToKey(url)

        memCache.get(key)?.let { return it }

        val diskFile = File(diskDir, key)
        if (diskFile.exists()) {
            val bmp = decodeSized(diskFile)
            if (bmp != null) { memCache.put(key, bmp); return bmp }
        }

        return withContext(Dispatchers.IO) {
            try {
                val bytes = client.newCall(Request.Builder().url(url).build())
                    .execute().body?.bytes() ?: return@withContext null
                FileOutputStream(diskFile).use { it.write(bytes) }
                trimDiskCacheIfNeeded()
                val bmp = decodeSized(bytes)
                if (bmp != null) memCache.put(key, bmp)
                bmp
            } catch (_: Exception) { null }
        }
    }

    private val maxDiskBytes = 30L * 1024 * 1024 // 30MB

    private fun trimDiskCacheIfNeeded() {
        val files = diskDir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxDiskBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxDiskBytes * 8 / 10) return
            total -= file.length()
            file.delete()
        }
    }

    fun clearAll() {
        memCache.evictAll()
        diskDir.listFiles()?.forEach { it.delete() }
    }

    private fun decodeSized(file: File): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        opts.inSampleSize = calcSample(opts.outWidth, opts.outHeight)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun decodeSized(bytes: ByteArray): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        opts.inSampleSize = calcSample(opts.outWidth, opts.outHeight)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun calcSample(w: Int, h: Int): Int {
        var s = 1
        while (maxOf(w, h) / s > targetSize * 2) s *= 2
        return s
    }

    private fun urlToKey(url: String): String {
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return "$hash.gif"
    }
}

package com.snowrealm.yukiboard.app.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * 圖片/GIF/Sticker commitContent 邏輯 — 從 YukiBoardIMEService.commitImageContent() 抽出。
 *
 * 負責：下載圖片 → 快取 → commitContent → fallback 剪貼簿 → fallback URL 文字。
 */
class ImeImageCommitter(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    fun commit(
        url: String,
        editorInfoProvider: () -> EditorInfo?,
        inputConnectionProvider: () -> InputConnection?,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val stickerDir = File(context.cacheDir, "stickers").also { it.mkdirs() }
                val urlPath = try { URL(url).path } catch (_: Exception) { url }
                var ext = when {
                    urlPath.contains(".gif", true) -> ".gif"
                    urlPath.contains(".webp", true) -> ".webp"
                    urlPath.contains(".png", true) -> ".png"
                    urlPath.contains(".jpg", true) || urlPath.contains(".jpeg", true) -> ".jpg"
                    else -> ""
                }

                val tempFileName = "sticker_${url.hashCode()}"
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val cachedFile = stickerDir.listFiles()?.firstOrNull {
                    it.name.startsWith(tempFileName) && it.length() > 0
                }
                val file: File

                if (cachedFile != null) {
                    file = cachedFile
                    ext = ".${cachedFile.extension}"
                } else {
                    val request = okhttp3.Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

                    val contentType = response.header("Content-Type") ?: ""
                    if (ext.isEmpty()) {
                        ext = when {
                            contentType.contains("png") -> ".png"
                            contentType.contains("webp") -> ".webp"
                            contentType.contains("gif") -> ".gif"
                            contentType.contains("jpeg") || contentType.contains("jpg") -> ".jpg"
                            else -> ".png"
                        }
                    }

                    file = File(stickerDir, "$tempFileName$ext")
                    response.body?.byteStream()?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw Exception("Empty response body")
                }

                val mimeType = when (file.extension.lowercase()) {
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "jpg", "jpeg" -> "image/jpeg"
                    else -> "image/png"
                }

                val authority = "${context.packageName}.fileprovider"
                val contentUri = FileProvider.getUriForFile(context, authority, file)
                val editorInfo = editorInfoProvider() ?: return@launch
                val ic = inputConnectionProvider() ?: return@launch

                val inputContentInfo = InputContentInfoCompat(
                    contentUri,
                    ClipDescription("sticker", arrayOf(mimeType)),
                    null
                )
                val flag = if (android.os.Build.VERSION.SDK_INT >= 25)
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                else 0

                val committed = try {
                    InputConnectionCompat.commitContent(ic, editorInfo, inputContentInfo, flag, null)
                } catch (_: Exception) { false }

                if (!committed) {
                    launch(Dispatchers.Main) {
                        try {
                            val clip = ClipData.newUri(context.contentResolver, "sticker", contentUri)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(clip)
                            Toast.makeText(context, "GIF 已複製，請長按貼上", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            ic.commitText(url, 1)
                        }
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "GIF 下載失敗，已貼上連結", Toast.LENGTH_SHORT).show()
                    inputConnectionProvider()?.commitText(url, 1)
                }
            }
        }
    }
}

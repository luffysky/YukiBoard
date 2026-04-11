package com.snowrealm.yukiboard.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView

/**
 * 透明中介 Activity — IME Service 無法接收 onActivityResult，
 * 因此由此 Activity 代理檔案選擇器，選取後儲存背景並通知 IME 重載。
 *
 * launch extra:
 *   "pick_type" → "image" | "video"
 */
class BackgroundPickerActivity : Activity() {

    companion object {
        const val EXTRA_PICK_TYPE = "pick_type"
        private const val RC_PICK = 7001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = intent.getStringExtra(EXTRA_PICK_TYPE) ?: "image"

        // 優先用系統內建相簿（ACTION_PICK + MediaStore）
        val pickIntent = Intent(Intent.ACTION_PICK).apply {
            data = if (type == "video") {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        try {
            @Suppress("DEPRECATION")
            startActivityForResult(pickIntent, RC_PICK)
        } catch (_: Exception) {
            // fallback：ACTION_GET_CONTENT
            val mimeType = if (type == "video") "video/*" else "image/*"
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        this.type = mimeType
                        addCategory(Intent.CATEGORY_OPENABLE)
                    },
                    RC_PICK
                )
            } catch (_: Exception) {
                Toast.makeText(this, "無法開啟相簿", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_PICK || resultCode != RESULT_OK) {
            finish()
            return
        }

        val uri = data?.data
        if (uri == null) {
            finish()
            return
        }

        saveBackground(uri)
        finish()
    }

    private fun saveBackground(uri: Uri) {
        val prefs = YukiPreferences(this)
        val bgDir = KeyboardBackgroundView.getBackgroundDir(this)
        bgDir.listFiles()?.forEach { it.delete() }

        val pickType = intent.getStringExtra(EXTRA_PICK_TYPE) ?: "image"
        val ext = if (pickType == "video") {
            val mime = contentResolver.getType(uri) ?: "video/mp4"
            when {
                mime.contains("webm") -> "webm"
                mime.contains("3gp") -> "3gp"
                else -> "mp4"
            }
        } else {
            val mime = contentResolver.getType(uri) ?: "image/png"
            when {
                mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                mime.contains("webp") -> "webp"
                else -> "png"
            }
        }

        val dest = java.io.File(bgDir, "keyboard_bg.$ext")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            // 驗證複製成功
            if (!dest.exists() || dest.length() == 0L) {
                Toast.makeText(this, "背景檔案複製失敗", Toast.LENGTH_SHORT).show()
                return
            }
            android.util.Log.i("BgPicker", "Background saved: ${dest.absolutePath} (${dest.length() / 1024}KB)")
            prefs.useCustomBackground = true

            val intent = android.content.Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED")
            intent.setPackage(packageName)
            sendBroadcast(intent)

            if (pickType == "video") {
                validateVideoFile(dest)
            } else {
                Toast.makeText(this, "背景已設定", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "背景設定失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateVideoFile(file: java.io.File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0

            val warnings = mutableListOf<String>()
            if (w > 1280 || h > 720) {
                warnings.add("解析度較高（${w}x${h}），建議 720p 以下")
            }
            if (durationMs > 60_000) {
                warnings.add("影片超過 60 秒，建議使用較短的循環片段")
            }

            if (warnings.isNotEmpty()) {
                Toast.makeText(this, "影片背景已設定\n⚠ ${warnings.joinToString("\n⚠ ")}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "影片背景已設定", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "影片背景已設定", Toast.LENGTH_SHORT).show()
        } finally {
            retriever.release()
        }
    }
}

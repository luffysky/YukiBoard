package com.snowrealm.yukiboard.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
        val mimeType = if (type == "video") "video/*" else "image/*"

        val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = mimeType
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(pickIntent, RC_PICK)
        } catch (_: Exception) {
            Toast.makeText(this, "無法開啟檔案選擇器", Toast.LENGTH_SHORT).show()
            finish()
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
            prefs.useCustomBackground = true
            Toast.makeText(this, "背景已設定", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "背景設定失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}

package com.snowrealm.yukiboard.ui.keyboard

import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.*
import java.io.File

/**
 * 鍵盤自訂背景容器 — 支援圖片 + 影片背景。
 *
 * 圖片：ImageView 全覆蓋 + 半透明黑色遮罩（保持可讀性）
 * 影片：TextureView + MediaPlayer 循環播放 + 遮罩
 *
 * 背景檔案存放在 app 內部儲存 `backgrounds/` 目錄。
 */
class KeyboardBackgroundView(context: Context) : FrameLayout(context) {

    private var imageView: ImageView? = null
    private var textureView: TextureView? = null
    private var mediaPlayer: MediaPlayer? = null
    private var overlayView: View? = null

    // 遮罩透明度（0~255），預設 120 讓文字可讀
    var overlayAlpha: Int = 120
        set(value) {
            field = value.coerceIn(0, 255)
            overlayView?.setBackgroundColor(Color.argb(field, 0, 0, 0))
        }

    enum class BackgroundType { NONE, IMAGE, VIDEO }

    private var currentType = BackgroundType.NONE

    /**
     * 設定圖片背景。
     * @param file 圖片檔案路徑
     */
    fun setImageBackground(file: File) {
        clearBackground()
        currentType = BackgroundType.IMAGE

        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // 非同步解碼大圖
        CoroutineScope(Dispatchers.IO).launch {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
            withContext(Dispatchers.Main) {
                iv.setImageBitmap(bmp)
            }
        }

        addView(iv)
        imageView = iv
        addOverlay()
    }

    /**
     * 設定圖片背景（從 URI）。
     */
    fun setImageBackground(uri: Uri) {
        clearBackground()
        currentType = BackgroundType.IMAGE

        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(input)
                input?.close()
                withContext(Dispatchers.Main) {
                    iv.setImageBitmap(bmp)
                }
            } catch (_: Exception) { }
        }

        addView(iv)
        imageView = iv
        addOverlay()
    }

    /**
     * 設定影片背景。
     * @param file 影片檔案路徑
     */
    fun setVideoBackground(file: File) {
        setVideoBackground(Uri.fromFile(file))
    }

    /**
     * 設定影片背景（從 URI）。
     */
    fun setVideoBackground(uri: Uri) {
        clearBackground()
        currentType = BackgroundType.VIDEO

        val tv = TextureView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                try {
                    val mp = MediaPlayer().apply {
                        setDataSource(context, uri)
                        setSurface(Surface(st))
                        isLooping = true
                        setVolume(0f, 0f) // 靜音
                        prepareAsync()
                        setOnPreparedListener { it.start() }
                    }
                    mediaPlayer = mp
                } catch (_: Exception) { }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                releaseMediaPlayer()
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        addView(tv)
        textureView = tv
        addOverlay()
    }

    private fun addOverlay() {
        val ov = View(context).apply {
            setBackgroundColor(Color.argb(overlayAlpha, 0, 0, 0))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(ov)
        overlayView = ov
    }

    fun clearBackground() {
        currentType = BackgroundType.NONE
        releaseMediaPlayer()
        removeAllViews()
        imageView = null
        textureView = null
        overlayView = null
    }

    fun hasBackground(): Boolean = currentType != BackgroundType.NONE

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
    }

    fun destroy() {
        clearBackground()
    }

    companion object {
        /** 背景檔案儲存目錄 */
        fun getBackgroundDir(context: Context): File {
            val dir = File(context.filesDir, "backgrounds")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        /** 取得已儲存的背景檔（null = 無自訂背景） */
        fun getSavedBackground(context: Context): File? {
            val dir = getBackgroundDir(context)
            // 優先影片，其次圖片
            val video = dir.listFiles()?.firstOrNull {
                it.extension.lowercase() in listOf("mp4", "webm", "3gp")
            }
            if (video != null) return video

            val image = dir.listFiles()?.firstOrNull {
                it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
            }
            return image
        }

        fun isVideoFile(file: File): Boolean {
            return file.extension.lowercase() in listOf("mp4", "webm", "3gp")
        }
    }
}

package com.snowrealm.yukiboard.ui.keyboard

import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.net.Uri
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
 * 圖片：ImageView + Matrix 支援雙指縮放 + 半透明黑色遮罩
 * 影片：TextureView + MediaPlayer 循環播放 + 遮罩
 *
 * 背景檔案存放在 app 內部儲存 `backgrounds/` 目錄。
 */
class KeyboardBackgroundView(context: Context) : FrameLayout(context) {

    private var imageView: ImageView? = null
    private var textureView: TextureView? = null
    private var mediaPlayer: MediaPlayer? = null
    private var overlayView: View? = null
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 遮罩透明度（0~255），預設 120 讓文字可讀
    var overlayAlpha: Int = 120
        set(value) {
            field = value.coerceIn(0, 255)
            overlayView?.setBackgroundColor(Color.argb(field, 0, 0, 0))
        }

    enum class BackgroundType { NONE, IMAGE, VIDEO }

    private var currentType = BackgroundType.NONE

    // ── 雙指縮放相關 ──
    private var imageMatrix = Matrix()
    private var savedMatrix = Matrix()
    private var scaleFactor = 1.0f
    private val minScale = 0.5f
    private val maxScale = 3.0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val iv = imageView ?: return false
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(minScale, maxScale)

            imageMatrix.set(savedMatrix)
            imageMatrix.postScale(
                detector.scaleFactor, detector.scaleFactor,
                detector.focusX, detector.focusY
            )
            iv.imageMatrix = imageMatrix
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            savedMatrix.set(imageMatrix)
            return true
        }
    })

    // TODO: 拖曳平移（pan drag）— 下一階段實作
    // TODO: 設定頁面裁切框 — 下一階段實作

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 雙指時攔截給縮放處理
        if (ev.pointerCount >= 2 && currentType == BackgroundType.IMAGE) return true
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (currentType == BackgroundType.IMAGE && imageView != null) {
            scaleDetector.onTouchEvent(event)
            if (event.pointerCount >= 2) return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * 設定圖片背景。
     * @param file 圖片檔案路徑
     */
    fun setImageBackground(file: File) {
        clearBackground()
        currentType = BackgroundType.IMAGE

        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.MATRIX
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        bgScope.launch(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
            withContext(Dispatchers.Main) {
                if (bmp != null) {
                    iv.setImageBitmap(bmp)
                    initCenterCropMatrix(iv, bmp.width, bmp.height)
                }
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
            scaleType = ImageView.ScaleType.MATRIX
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        bgScope.launch(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(input)
                input?.close()
                withContext(Dispatchers.Main) {
                    if (bmp != null) {
                        iv.setImageBitmap(bmp)
                        initCenterCropMatrix(iv, bmp.width, bmp.height)
                    }
                }
            } catch (_: Exception) { }
        }

        addView(iv)
        imageView = iv
        addOverlay()
    }

    /**
     * 初始化 matrix 實現 center-crop 效果（後續可雙指縮放）。
     */
    private fun initCenterCropMatrix(iv: ImageView, bmpW: Int, bmpH: Int) {
        iv.post {
            val vw = iv.width.toFloat()
            val vh = iv.height.toFloat()
            if (vw <= 0 || vh <= 0) return@post

            val scale = maxOf(vw / bmpW, vh / bmpH)
            val dx = (vw - bmpW * scale) / 2f
            val dy = (vh - bmpH * scale) / 2f

            imageMatrix.reset()
            imageMatrix.setScale(scale, scale)
            imageMatrix.postTranslate(dx, dy)
            savedMatrix.set(imageMatrix)
            scaleFactor = 1.0f
            iv.imageMatrix = imageMatrix
        }
    }

    /**
     * 設定影片背景。
     * @param file 影片檔案路徑
     */
    fun setVideoBackground(file: File) {
        clearBackground()
        currentType = BackgroundType.VIDEO

        val filePath = file.absolutePath

        val tv = TextureView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                try {
                    val mp = MediaPlayer().apply {
                        setDataSource(filePath)
                        setSurface(Surface(st))
                        isLooping = true
                        setVolume(0f, 0f)
                        prepareAsync()
                        setOnPreparedListener { it.start() }
                    }
                    mediaPlayer = mp
                } catch (e: Exception) {
                    android.util.Log.e("KeyboardBgView", "Video playback failed: $filePath", e)
                }
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
                        setVolume(0f, 0f)
                        prepareAsync()
                        setOnPreparedListener { it.start() }
                    }
                    mediaPlayer = mp
                } catch (e: Exception) {
                    android.util.Log.e("KeyboardBgView", "Video URI playback failed", e)
                }
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

    /**
     * 從 prefs 重新載入背景（IME 每次 onStartInputView 呼叫）。
     * 回傳是否有自訂背景。
     */
    fun reloadFromStorage(): Boolean {
        val savedBg = getSavedBackground(context) ?: run {
            if (currentType != BackgroundType.NONE) clearBackground()
            return false
        }

        // 已經載入同類型就不重複載入（影片重新載入會閃）
        if (currentType == BackgroundType.VIDEO && isVideoFile(savedBg) && mediaPlayer != null) {
            return true
        }
        if (currentType == BackgroundType.IMAGE && !isVideoFile(savedBg) && imageView != null) {
            return true
        }

        if (isVideoFile(savedBg)) {
            setVideoBackground(savedBg)
        } else {
            setImageBackground(savedBg)
        }
        return true
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
        bgScope.cancel()
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

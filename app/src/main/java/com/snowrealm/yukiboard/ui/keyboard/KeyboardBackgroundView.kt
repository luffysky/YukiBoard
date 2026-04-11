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

    // 背景位置偏移（0.0~1.0，0.5=置中）
    var offsetX: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f); applyOffset(); applyVideoTransform() }
    var offsetY: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f); applyOffset(); applyVideoTransform() }

    // 0=保持比例(center-crop) 1=整張塞入(stretch)
    var scaleMode: Int = 0
        set(value) { field = value; applyOffset(); applyVideoTransform() }

    private var bmpWidth = 0
    private var bmpHeight = 0
    private var videoWidth = 0
    private var videoHeight = 0

    enum class BackgroundType { NONE, IMAGE, VIDEO }

    private var currentType = BackgroundType.NONE
    private var currentLoadedPath: String? = null
    private var currentLoadedLastModified: Long = 0L

    // 旋轉角度（0, 90, 180, 270）
    var bgRotation: Int = 0
        set(value) {
            field = value
            applyVideoTransform()
            applyOffset()
        }

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
        currentLoadedPath = file.absolutePath
        currentLoadedLastModified = file.lastModified()

        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.MATRIX
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        bgScope.launch(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
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
     * 初始化 matrix 並根據 offsetX/offsetY 定位。
     * offsetX/offsetY = 0.5 等同 center-crop。
     */
    private fun initCenterCropMatrix(iv: ImageView, bmpW: Int, bmpH: Int) {
        bmpWidth = bmpW; bmpHeight = bmpH
        iv.post {
            applyOffset()
        }
    }

    /**
     * 與 buildFullKeyboardPreview onDraw 完全相同的 Matrix。
     * ImageView(MATRIX, MATCH_PARENT) 自動 clip，不需要特殊處理。
     */
    private fun applyOffset() {
        val iv = imageView ?: return
        val vw = iv.width.toFloat()
        val vh = iv.height.toFloat()

        if (vw <= 0 || vh <= 0 || bmpWidth <= 0 || bmpHeight <= 0) {
            iv.viewTreeObserver.addOnGlobalLayoutListener(object :
                android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    iv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    applyOffset()
                }
            })
            return
        }

        imageMatrix.reset()
        val bw = bmpWidth.toFloat()
        val bh = bmpHeight.toFloat()

        if (scaleMode == 1) {
            imageMatrix.setScale(vw / bw, vh / bh)
        } else {
            val scale = maxOf(vw / bw, vh / bh)
            val scaledW = bw * scale
            val scaledH = bh * scale
            val maxDx = (scaledW - vw).coerceAtLeast(0f)
            val maxDy = (scaledH - vh).coerceAtLeast(0f)
            imageMatrix.setScale(scale, scale)
            val tx = (-maxDx * (1.0f - offsetX)).coerceIn(-maxDx, 0f)
            val ty = (-maxDy * (1.0f - offsetY)).coerceIn(-maxDy, 0f)
            imageMatrix.postTranslate(tx, ty)
        }
        if (bgRotation != 0) {
            imageMatrix.postRotate(bgRotation.toFloat(), vw / 2f, vh / 2f)
        }

        savedMatrix.set(imageMatrix)
        iv.imageMatrix = imageMatrix
    }

    /** 影片背景的 transform（center-crop + rotation + offset）。
     *  TextureView 預設 stretch 影片到 View 大小，Matrix 要先還原再 crop。
     */
    private fun applyVideoTransform() {
        val tv = textureView ?: return
        val vw = tv.width.toFloat()
        val vh = tv.height.toFloat()
        if (vw <= 0 || vh <= 0 || videoWidth <= 0 || videoHeight <= 0) return

        val vW = videoWidth.toFloat()
        val vH = videoHeight.toFloat()
        val cx = vw / 2f
        val cy = vh / 2f

        val isRotated = bgRotation == 90 || bgRotation == 270

        val matrix = Matrix()

        if (scaleMode == 1) {
            // 整張塞入 — TextureView 預設就是 stretch，只需處理旋轉
            if (isRotated) {
                matrix.postTranslate(-cx, -cy)
                matrix.postRotate(bgRotation.toFloat())
                matrix.postScale(vw / vh, vh / vw)
                matrix.postTranslate(cx, cy)
            } else if (bgRotation == 180) {
                matrix.postRotate(180f, cx, cy)
            }
        } else {
            // center-crop
            val scaleX = vW / vw
            val scaleY = vH / vh
            val effW = if (isRotated) vH else vW
            val effH = if (isRotated) vW else vH
            val cropScale = maxOf(vw / effW, vh / effH)
            val finalSx = scaleX * cropScale
            val finalSy = scaleY * cropScale

            matrix.setScale(finalSx, finalSy, cx, cy)
            if (bgRotation != 0) {
                matrix.postRotate(bgRotation.toFloat(), cx, cy)
            }

            val scaledW = effW * cropScale
            val scaledH = effH * cropScale
            val maxDx = (scaledW - vw).coerceAtLeast(0f)
            val maxDy = (scaledH - vh).coerceAtLeast(0f)
            val tx = (-maxDx * (1.0f - offsetX)).coerceIn(-maxDx, 0f)
            val ty = (-maxDy * (1.0f - offsetY)).coerceIn(-maxDy, 0f)
            matrix.postTranslate(tx, ty)
        }

        tv.setTransform(matrix)
    }

    /**
     * 設定影片背景。
     * @param file 影片檔案路徑
     */
    fun setVideoBackground(file: File) {
        clearBackground()
        currentType = BackgroundType.VIDEO
        currentLoadedPath = file.absolutePath
        currentLoadedLastModified = file.lastModified()

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
                        setOnPreparedListener { player ->
                            this@KeyboardBackgroundView.videoWidth = player.videoWidth
                            this@KeyboardBackgroundView.videoHeight = player.videoHeight
                            applyVideoTransform()
                            player.start()
                        }
                        prepareAsync()
                    }
                    mediaPlayer = mp
                } catch (e: Exception) {
                    android.util.Log.e("KeyboardBgView", "Video playback failed: $filePath", e)
                }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                applyVideoTransform()
            }
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
                    val mp = MediaPlayer()
                    mp.setDataSource(context, uri)
                    mp.setSurface(Surface(st))
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    mp.setOnErrorListener { _, what, extra ->
                        android.util.Log.e("KeyboardBgView", "MediaPlayer error: what=$what extra=$extra")
                        releaseMediaPlayer()
                        true
                    }
                    mp.setOnPreparedListener { player ->
                        this@KeyboardBackgroundView.videoWidth = player.videoWidth
                        this@KeyboardBackgroundView.videoHeight = player.videoHeight
                        applyVideoTransform()
                        player.start()
                    }
                    mp.prepareAsync()
                    mediaPlayer = mp
                } catch (e: Exception) {
                    android.util.Log.e("KeyboardBgView", "Video URI playback failed", e)
                }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                applyVideoTransform()
            }
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

        // 同一檔案才跳過重載（比對路徑 + lastModified）
        if (currentType == BackgroundType.VIDEO && isVideoFile(savedBg)
            && currentLoadedPath == savedBg.absolutePath
            && currentLoadedLastModified == savedBg.lastModified()
            && mediaPlayer != null) {
            applyVideoTransform()
            return true
        }
        if (currentType == BackgroundType.IMAGE && isImageFile(savedBg) && imageView != null
            && currentLoadedPath == savedBg.absolutePath
            && currentLoadedLastModified == savedBg.lastModified()) {
            applyOffset()
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
        currentLoadedPath = null
        currentLoadedLastModified = 0L
        releaseMediaPlayer()
        removeAllViews()
        imageView = null
        textureView = null
        overlayView = null
    }

    fun hasBackground(): Boolean = currentType != BackgroundType.NONE

    private fun releaseMediaPlayer() {
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        try { mp.stop() } catch (_: Exception) { }
        try { mp.release() } catch (_: Exception) { }
    }

    fun pause() {
        // SurfaceTexture 由系統管理（return true），不需要手動暫停
    }

    fun resume() {
        // SurfaceTexture 重建由 onSurfaceTextureAvailable 自動處理
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
            val video = dir.listFiles()?.firstOrNull { isVideoFile(it) }
            if (video != null) return video
            return dir.listFiles()?.firstOrNull { isImageFile(it) }
        }

        fun isVideoFile(file: File): Boolean {
            return file.extension.lowercase() in listOf(
                "mp4", "m4v", "webm", "3gp", "mkv", "mov", "avi", "ts", "flv"
            )
        }

        fun isImageFile(file: File): Boolean {
            return file.extension.lowercase() in listOf(
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif"
            )
        }
    }
}

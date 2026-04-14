package com.snowrealm.yukiboard.ui.settings.section

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.snowrealm.yukiboard.theme.ThemeManager
import com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView
import com.snowrealm.yukiboard.ui.keyboard.KeyboardState
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 鍵盤背景設定 section。
 *
 * 包含：背景圖片/影片選擇、遮罩深度、顯示比例、旋轉、位置偏移、
 * 鍵盤高度/按鍵大小/透明度、完整鍵盤預覽、透明鍵盤開關、字體顏色選擇器。
 *
 * 從 SettingsActivity 的 buildBackgroundCard() 及所有相關方法與欄位抽出。
 */
class BackgroundSettingsSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String,
    private val themeManager: ThemeManager,
    private val scope: CoroutineScope,
    private val onPickImage: () -> Unit,
    private val onPickVideo: () -> Unit
) {
    // ── 預覽用的 View 引用 ──
    private var bgPreviewImage: ImageView? = null
    private var bgPreviewOverlay: View? = null
    private var bgPreviewLabel: TextView? = null
    private var bgPreviewContainer: FrameLayout? = null
    private var bgPreviewView: View? = null
    private var bgVideoPlayer: MediaPlayer? = null
    private var bgVideoTexture: TextureView? = null

    // 背景同步用的 UI 元件引用
    private var bgOverlaySeekBar: SeekBar? = null
    private var bgOverlayLabel: TextView? = null
    private var bgBtnCrop: Button? = null
    private var bgBtnFit: Button? = null
    private var bgRotBtns = mutableListOf<Button>()
    private var bgOffsetXSeekBar: SeekBar? = null
    private var bgOffsetXLabel: TextView? = null
    private var bgOffsetYSeekBar: SeekBar? = null
    private var bgOffsetYLabel: TextView? = null
    private var bgSyncReceiver: BroadcastReceiver? = null
    private var bgVideoWidth = 0
    private var bgVideoHeight = 0
    private var fullPreviewView: View? = null
    private var fullPreviewBgBitmap: Bitmap? = null

    private lateinit var ctx: Context

    fun buildCard(context: Context): LinearLayout {
        ctx = context
        val card = SettingsUiHelper.simpleCard(context, tr("鍵盤背景 / Background"))

        // ── 預覽區 ──
        val previewContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * prefs.keyboardHeightRatio).toInt().coerceIn(dp(context, 100), dp(context, 300))
            ).apply { setMargins(0, 0, 0, dp(context, 8)) }
            background = GradientDrawable().apply {
                setColor(0xFF111827.toInt())
                cornerRadius = dp(context, 8).toFloat()
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(context, 8).toFloat())
                }
            }
        }
        bgPreviewContainer = previewContainer

        // Canvas 背景預覽
        val canvasPreview = object : View(context) {
            private val bgMatrix = Matrix()
            private val overlayPaint = Paint()

            init { setWillNotDraw(false) }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0 || h <= 0) return

                val bmp = fullPreviewBgBitmap ?: return
                bgMatrix.reset()
                if (prefs.bgScaleMode == 1) {
                    bgMatrix.setScale(w / bmp.width, h / bmp.height)
                } else {
                    val scale = maxOf(w / bmp.width, h / bmp.height)
                    val sw = bmp.width * scale
                    val sh = bmp.height * scale
                    val maxDx = (sw - w).coerceAtLeast(0f)
                    val maxDy = (sh - h).coerceAtLeast(0f)
                    bgMatrix.setScale(scale, scale)
                    val tx = (-maxDx * prefs.bgOffsetX).coerceIn(-maxDx, 0f)
                    val ty = (-maxDy * prefs.bgOffsetY).coerceIn(-maxDy, 0f)
                    bgMatrix.postTranslate(tx, ty)
                }
                if (prefs.bgRotation != 0) {
                    bgMatrix.postRotate(prefs.bgRotation.toFloat(), w / 2f, h / 2f)
                }
                canvas.drawBitmap(bmp, bgMatrix, null)

                overlayPaint.color = Color.argb(prefs.bgOverlayAlpha, 0, 0, 0)
                overlayPaint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bgPreviewView = canvasPreview
        previewContainer.addView(canvasPreview)

        // ImageView 給影片第一幀備用
        val previewImg = ImageView(context).apply {
            scaleType = ImageView.ScaleType.MATRIX
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        bgPreviewImage = previewImg
        previewContainer.addView(previewImg)

        // 遮罩覆蓋
        val overlayView = View(context).apply {
            setBackgroundColor(Color.argb(prefs.bgOverlayAlpha, 0, 0, 0))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bgPreviewOverlay = overlayView
        previewContainer.addView(overlayView)

        // 無圖提示文字
        val label = TextView(context).apply {
            text = tr("尚未設定背景")
            setTextColor(0xFF6688AA.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bgPreviewLabel = label
        previewContainer.addView(label)

        refreshBgPreview()
        card.addView(previewContainer)

        // ── 按鈕列 ──
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 8))
        }

        row.addView(SettingsUiHelper.styledButton(context, tr("選圖片"), 0xFF2A3A5C.toInt(), 0xFFE8F4FF.toInt()) {
            onPickImage()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(context, 40), 1f).apply { marginEnd = dp(context, 4) }
        })

        row.addView(SettingsUiHelper.styledButton(context, tr("選影片"), 0xFF2A3A5C.toInt(), 0xFFE8F4FF.toInt()) {
            onPickVideo()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(context, 40), 1f).apply {
                marginStart = dp(context, 4); marginEnd = dp(context, 4)
            }
        })

        row.addView(SettingsUiHelper.styledButton(context, tr("清除"), 0xFF1E2D4A.toInt(), 0xFFFF6B6B.toInt()) {
            stopVideoPreview()
            val dir = KeyboardBackgroundView.getBackgroundDir(context)
            dir.listFiles()?.forEach { it.delete() }
            prefs.useCustomBackground = false
            bgPreviewImage?.setImageBitmap(null)
            bgPreviewView?.visibility = View.GONE
            bgPreviewLabel?.text = tr("尚未設定背景")
            bgPreviewLabel?.visibility = View.VISIBLE
            fullPreviewBgBitmap = null
            fullPreviewView?.invalidate()
            bgPreviewView?.invalidate()
            context.sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(context.packageName))
            Toast.makeText(context, tr("已清除背景"), Toast.LENGTH_SHORT).show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(context, 40), 0.7f).apply { marginStart = dp(context, 4) }
        })

        card.addView(row)

        // ── 遮罩透明度滑桿 ──
        val alphaLabel = TextView(context).apply {
            text = tr("遮罩深度: %d%%").format((prefs.bgOverlayAlpha * 100) / 255)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 12), dp(context, 8), 0, dp(context, 4))
        }
        bgOverlayLabel = alphaLabel
        card.addView(alphaLabel)

        card.addView(SeekBar(context).apply {
            max = 255
            progress = prefs.bgOverlayAlpha
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 8))
            bgOverlaySeekBar = this
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.bgOverlayAlpha = p
                    alphaLabel.text = tr("遮罩深度: %d%%").format((p * 100) / 255)
                    bgPreviewOverlay?.setBackgroundColor(Color.argb(p, 0, 0, 0))
                    fullPreviewView?.invalidate()
                    bgPreviewView?.invalidate()
                    context.sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(context.packageName))
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 顯示比例切換 ──
        val scaleModeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 4))
        }
        scaleModeRow.addView(TextView(context).apply {
            text = tr("顯示比例:")
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            gravity = Gravity.CENTER_VERTICAL
        })

        val btnCrop = Button(context).apply {
            text = tr("保持比例")
            textSize = 11f
            setTextColor(if (prefs.bgScaleMode == 0) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            setBackgroundColor(if (prefs.bgScaleMode == 0) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(context, 32), 1f).apply { marginStart = dp(context, 8) }
        }
        val btnFit = Button(context).apply {
            text = tr("整張塞入")
            textSize = 11f
            setTextColor(if (prefs.bgScaleMode == 1) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            setBackgroundColor(if (prefs.bgScaleMode == 1) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(context, 32), 1f).apply { marginStart = dp(context, 4) }
        }
        bgBtnCrop = btnCrop
        bgBtnFit = btnFit
        btnCrop.setOnClickListener {
            prefs.bgScaleMode = 0
            btnCrop.setTextColor(0xFF111827.toInt()); btnCrop.setBackgroundColor(0xFF7FD8FF.toInt())
            btnFit.setTextColor(0xFFE8F4FF.toInt()); btnFit.setBackgroundColor(0xFF2A3A5C.toInt())
            updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
            context.sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(context.packageName))
        }
        btnFit.setOnClickListener {
            prefs.bgScaleMode = 1
            btnFit.setTextColor(0xFF111827.toInt()); btnFit.setBackgroundColor(0xFF7FD8FF.toInt())
            btnCrop.setTextColor(0xFFE8F4FF.toInt()); btnCrop.setBackgroundColor(0xFF2A3A5C.toInt())
            updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
            context.sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(context.packageName))
        }
        scaleModeRow.addView(btnCrop)
        scaleModeRow.addView(btnFit)
        card.addView(scaleModeRow)

        // ── 影片旋轉 ──
        bgRotBtns.clear()
        val rotationRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 4))
        }
        rotationRow.addView(TextView(context).apply {
            text = tr("影片旋轉:")
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        })
        listOf(0 to "0°", 90 to "90°", 180 to "180°", 270 to "270°").forEach { (deg, degLabel) ->
            val btn = Button(context).apply {
                text = degLabel
                textSize = 11f
                setPadding(dp(context, 6), 0, dp(context, 6), 0)
                layoutParams = LinearLayout.LayoutParams(0, dp(context, 32), 1f).apply { marginStart = dp(context, 4) }
                setOnClickListener {
                    prefs.bgRotation = deg
                    bgRotBtns.forEachIndexed { i, b ->
                        val d = listOf(0, 90, 180, 270)[i]
                        b.setTextColor(if (prefs.bgRotation == d) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
                        b.setBackgroundColor(if (prefs.bgRotation == d) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
                    }
                    updateVideoPreviewTransform()
                    fullPreviewView?.invalidate()
                    context.sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(context.packageName))
                }
            }
            val selected = prefs.bgRotation == deg
            btn.setTextColor(if (selected) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            btn.setBackgroundColor(if (selected) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
            bgRotBtns.add(btn)
            rotationRow.addView(btn)
        }
        card.addView(rotationRow)

        // ── 背景位置 X 拉桿 ──
        val xLabel = TextView(context).apply {
            text = tr("水平位置 X: %d%%").format((prefs.bgOffsetX * 100).toInt())
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 12), dp(context, 8), 0, dp(context, 4))
        }
        bgOffsetXLabel = xLabel
        card.addView(xLabel)
        card.addView(SeekBar(context).apply {
            max = 100
            progress = (prefs.bgOffsetX * 100).toInt()
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4))
            bgOffsetXSeekBar = this
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.bgOffsetX = p / 100f
                    xLabel.text = tr("水平位置 X: %d%%").format(p)
                    updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    context.sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(context.packageName))
                }
            })
        })

        // ── 背景位置 Y 拉桿 ──
        val yLabel = TextView(context).apply {
            text = tr("垂直位置 Y: %d%%").format((prefs.bgOffsetY * 100).toInt())
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 12), dp(context, 4), 0, dp(context, 4))
        }
        bgOffsetYLabel = yLabel
        card.addView(yLabel)
        card.addView(SeekBar(context).apply {
            max = 100
            progress = (prefs.bgOffsetY * 100).toInt()
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 8))
            bgOffsetYSeekBar = this
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.bgOffsetY = p / 100f
                    yLabel.text = tr("垂直位置 Y: %d%%").format(p)
                    updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    context.sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(context.packageName))
                }
            })
        })

        // ── 鍵盤高度拉桿 ──
        val heightPct = ((prefs.keyboardHeightRatio - 0.25f) / 0.30f * 100).toInt().coerceIn(0, 100)
        val kbHeightLabel = TextView(context).apply {
            text = tr("鍵盤高度: %d%%").format(heightPct)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 12), dp(context, 8), 0, dp(context, 4))
        }
        card.addView(kbHeightLabel)
        card.addView(SeekBar(context).apply {
            max = 100
            progress = heightPct
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.keyboardHeightRatio = 0.25f + p / 100f * 0.30f
                    kbHeightLabel.text = tr("鍵盤高度: %d%%").format(p)
                    updateFullPreviewHeight()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 按鍵大小拉桿 ──
        val keySizeLabel = TextView(context).apply {
            text = tr("按鍵大小: %d%%").format(prefs.keyScalePercent)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 12), dp(context, 8), 0, dp(context, 4))
        }
        card.addView(keySizeLabel)
        card.addView(SeekBar(context).apply {
            max = 60
            progress = prefs.keyScalePercent - 70
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.keyScalePercent = p + 70
                    keySizeLabel.text = tr("按鍵大小: %d%%").format(p + 70)
                    fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 按鍵透明度拉桿 ──
        val keyOpLabel = TextView(context).apply {
            text = tr("按鍵不透明度: %d%%").format((prefs.keyOpacity * 100) / 255)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 12), dp(context, 4), 0, dp(context, 4))
        }
        card.addView(keyOpLabel)
        card.addView(SeekBar(context).apply {
            max = 255
            progress = prefs.keyOpacity
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.keyOpacity = p
                    keyOpLabel.text = tr("按鍵不透明度: %d%%").format((p * 100) / 255)
                    fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 文字透明度拉桿 ──
        val keyTextOpLabel = TextView(context).apply {
            text = tr("文字不透明度: %d%%").format((prefs.keyTextOpacity * 100) / 255)
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 12), dp(context, 4), 0, dp(context, 4))
        }
        card.addView(keyTextOpLabel)
        card.addView(SeekBar(context).apply {
            max = 255
            progress = prefs.keyTextOpacity
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.keyTextOpacity = p
                    keyTextOpLabel.text = tr("文字不透明度: %d%%").format((p * 100) / 255)
                    fullPreviewView?.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 鍵盤完整預覽 ──
        card.addView(TextView(context).apply {
            text = tr("鍵盤預覽")
            textSize = 13f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 12), dp(context, 12), 0, dp(context, 4))
        })

        val kbPreview = buildFullKeyboardPreview(context)
        fullPreviewView = kbPreview
        card.addView(kbPreview)

        // ── 透明鍵盤 ──
        card.addView(SettingsUiHelper.switchRow(context, tr("透明鍵盤"), tr("背景完全透視到底層 App"), prefs.transparentKeyboard) {
            prefs.transparentKeyboard = it
        })

        val tAlphaLabel = TextView(context).apply {
            text = tr("透明度: %d%%").format((prefs.transparentKeyboardAlpha * 100) / 255)
            textSize = 11f
            setTextColor(0xFF8899AA.toInt())
            setPadding(dp(context, 12), dp(context, 4), 0, 0)
        }
        card.addView(tAlphaLabel)
        card.addView(SeekBar(context).apply {
            max = 255
            progress = prefs.transparentKeyboardAlpha
            setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.transparentKeyboardAlpha = p
                    tAlphaLabel.text = tr("透明度: %d%%").format((p * 100) / 255)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // ── 字體顏色 ──
        card.addView(buildKeyLabelColorPicker(context))

        return card
    }

    // ─────────────────────────────────────────────────────────
    // 影片預覽
    // ─────────────────────────────────────────────────────────

    private fun startVideoPreview(filePath: String) {
        val context = ctx
        val container = bgPreviewContainer ?: return
        val tv = TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bgVideoTexture = tv

        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                try {
                    val mp = MediaPlayer().apply {
                        setDataSource(filePath)
                        setSurface(Surface(st))
                        isLooping = true
                        setVolume(0f, 0f)
                        setOnErrorListener { _, what, extra ->
                            android.util.Log.e("BgPreview", "MediaPlayer error: what=$what extra=$extra")
                            bgPreviewLabel?.text = tr("影片播放失敗")
                            bgPreviewLabel?.visibility = View.VISIBLE
                            true
                        }
                        setOnPreparedListener { player ->
                            bgVideoWidth = player.videoWidth
                            bgVideoHeight = player.videoHeight
                            tv.post { updateVideoPreviewTransform() }
                            player.start()
                        }
                        prepareAsync()
                    }
                    bgVideoPlayer = mp
                } catch (e: Exception) {
                    bgPreviewLabel?.text = tr("影片載入失敗")
                    bgPreviewLabel?.visibility = View.VISIBLE
                }
            }
            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                updateVideoPreviewTransform()
            }
            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                val mp = bgVideoPlayer
                bgVideoPlayer = null
                try { mp?.stop() } catch (_: Exception) { }
                try { mp?.release() } catch (_: Exception) { }
                return true
            }
            override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
        }

        val overlayIdx = container.indexOfChild(bgPreviewOverlay)
        container.addView(tv, if (overlayIdx > 0) overlayIdx else 1)
    }

    fun stopVideoPreview() {
        val mp = bgVideoPlayer ?: run {
            bgVideoTexture?.let { bgPreviewContainer?.removeView(it) }
            bgVideoTexture = null
            return
        }
        bgVideoPlayer = null
        try { mp.stop() } catch (_: Exception) { }
        try { mp.release() } catch (_: Exception) { }
        bgVideoTexture?.let { bgPreviewContainer?.removeView(it) }
        bgVideoTexture = null
    }

    private fun updateBgPreviewMatrix() {
        bgPreviewView?.invalidate()
    }

    private fun updateVideoPreviewTransform() {
        val tv = bgVideoTexture ?: return
        val vw = tv.width.toFloat()
        val vh = tv.height.toFloat()
        if (vw <= 0 || vh <= 0 || bgVideoWidth <= 0 || bgVideoHeight <= 0) return

        val vW = bgVideoWidth.toFloat()
        val vH = bgVideoHeight.toFloat()
        val cx = vw / 2f
        val cy = vh / 2f
        val rotation = prefs.bgRotation
        val isRotated = rotation == 90 || rotation == 270

        val matrix = Matrix()

        if (prefs.bgScaleMode == 1) {
            if (isRotated) {
                matrix.postTranslate(-cx, -cy)
                matrix.postRotate(rotation.toFloat())
                matrix.postScale(vw / vh, vh / vw)
                matrix.postTranslate(cx, cy)
            } else if (rotation == 180) {
                matrix.postRotate(180f, cx, cy)
            }
        } else {
            val scaleX = vW / vw
            val scaleY = vH / vh
            val effW = if (isRotated) vH else vW
            val effH = if (isRotated) vW else vH
            val cropScale = maxOf(vw / effW, vh / effH)
            val finalSx = scaleX * cropScale
            val finalSy = scaleY * cropScale

            matrix.setScale(finalSx, finalSy, cx, cy)
            if (rotation != 0) {
                matrix.postRotate(rotation.toFloat(), cx, cy)
            }

            val scaledW = effW * cropScale
            val scaledH = effH * cropScale
            val maxDx = (scaledW - vw).coerceAtLeast(0f)
            val maxDy = (scaledH - vh).coerceAtLeast(0f)
            val tx = (-maxDx * prefs.bgOffsetX).coerceIn(-maxDx, 0f)
            val ty = (-maxDy * prefs.bgOffsetY).coerceIn(-maxDy, 0f)
            matrix.postTranslate(tx, ty)
        }

        tv.setTransform(matrix)
        tv.invalidate()
    }

    private fun refreshBgPreview() {
        val savedBg = KeyboardBackgroundView.getSavedBackground(ctx)
        if (savedBg != null && !KeyboardBackgroundView.isVideoFile(savedBg)) {
            bgPreviewView?.visibility = View.VISIBLE
            bgPreviewOverlay?.visibility = View.GONE
            bgPreviewLabel?.visibility = View.GONE
        } else if (savedBg != null) {
            stopVideoPreview()
            bgPreviewView?.visibility = View.GONE
            bgPreviewOverlay?.visibility = View.VISIBLE
            bgPreviewLabel?.visibility = View.GONE
            startVideoPreview(savedBg.absolutePath)
        } else {
            bgPreviewView?.visibility = View.GONE
            bgPreviewOverlay?.visibility = View.GONE
            bgPreviewLabel?.text = tr("尚未設定背景")
            bgPreviewLabel?.visibility = View.VISIBLE
        }
        reloadFullPreviewBitmap()
        fullPreviewView?.invalidate()
        bgPreviewView?.invalidate()
    }

    // ─────────────────────────────────────────────────────────
    // 鍵盤完整預覽
    // ─────────────────────────────────────────────────────────

    private fun updateFullPreviewHeight() {
        val screenH = ctx.resources.displayMetrics.heightPixels
        val newH = (screenH * prefs.keyboardHeightRatio).toInt().coerceIn(dp(ctx, 100), dp(ctx, 300))
        fullPreviewView?.let { v ->
            v.layoutParams = v.layoutParams?.apply { height = newH }
            v.requestLayout(); v.invalidate()
        }
        bgPreviewContainer?.let { c ->
            c.layoutParams = c.layoutParams?.apply { height = newH }
            c.requestLayout()
            updateBgPreviewMatrix()
        }
    }

    private fun reloadFullPreviewBitmap() {
        val savedBg = KeyboardBackgroundView.getSavedBackground(ctx)

        if (savedBg == null) {
            fullPreviewBgBitmap = null
            fullPreviewView?.invalidate()
            return
        }

        scope.launch(Dispatchers.IO) {
            val bmp = try {
                if (KeyboardBackgroundView.isVideoFile(savedBg)) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(savedBg.absolutePath)
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                } else {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(savedBg.absolutePath, opts)
                }
            } catch (_: Exception) { null }

            withContext(Dispatchers.Main) {
                fullPreviewBgBitmap = bmp
                fullPreviewView?.invalidate()
                bgPreviewView?.invalidate()
            }
        }
    }

    private fun buildFullKeyboardPreview(context: Context): View {
        val theme = themeManager.currentTheme
        reloadFullPreviewBitmap()

        val screenH = context.resources.displayMetrics.heightPixels
        val previewHeight = (screenH * prefs.keyboardHeightRatio).toInt().coerceIn(dp(context, 100), dp(context, 300))
        val dens = context.resources.displayMetrics.density

        return object : View(context) {
            private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
            }
            private val bgMatrix = Matrix()

            init {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, previewHeight
                ).apply { setMargins(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 12)) }
                background = GradientDrawable().apply {
                    setColor(theme.backgroundColorInt)
                    cornerRadius = dp(context, 8).toFloat()
                }
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(context, 8).toFloat())
                    }
                }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0 || h <= 0) return

                // 1. 背景圖片
                val bmp = fullPreviewBgBitmap
                if (bmp != null && prefs.useCustomBackground) {
                    bgMatrix.reset()
                    if (prefs.bgScaleMode == 1) {
                        bgMatrix.setScale(w / bmp.width, h / bmp.height)
                    } else {
                        val scale = maxOf(w / bmp.width, h / bmp.height)
                        val sw = bmp.width * scale
                        val sh = bmp.height * scale
                        val maxDx = (sw - w).coerceAtLeast(0f)
                        val maxDy = (sh - h).coerceAtLeast(0f)
                        bgMatrix.setScale(scale, scale)
                        val tx = (-maxDx * prefs.bgOffsetX).coerceIn(-maxDx, 0f)
                        val ty = (-maxDy * prefs.bgOffsetY).coerceIn(-maxDy, 0f)
                        bgMatrix.postTranslate(tx, ty)
                    }
                    if (prefs.bgRotation != 0) {
                        bgMatrix.postRotate(prefs.bgRotation.toFloat(), w / 2f, h / 2f)
                    }
                    canvas.drawBitmap(bmp, bgMatrix, null)

                    // 2. 遮罩
                    keyPaint.color = Color.argb(prefs.bgOverlayAlpha, 0, 0, 0)
                    keyPaint.style = Paint.Style.FILL
                    canvas.drawRect(0f, 0f, w, h, keyPaint)
                }

                // 3. 繪製迷你鍵盤按鍵
                val rows = KeyboardState.defaultQwertyRows()
                val hasBg = bmp != null && prefs.useCustomBackground
                val scalePct = prefs.keyScalePercent
                val keyAlpha = prefs.keyOpacity
                val rowH = h / rows.size
                val scaleInv = (200 - scalePct) / 100f
                val padH = 2f * dens * scaleInv
                val padV = 2f * dens * scaleInv
                val cr = theme.keyCornerRadiusDp * dens * 0.6f
                val fontSize = 10f * dens * scalePct / 100f
                textPaint.textSize = fontSize
                val baseLabelColor = if (prefs.customKeyLabelColor != 0) prefs.customKeyLabelColor
                    else theme.keyLabelColorInt
                val textAlpha = prefs.keyTextOpacity
                textPaint.color = if (textAlpha < 255) {
                    val a = ((baseLabelColor ushr 24) * textAlpha / 255).coerceIn(0, 255)
                    (a shl 24) or (baseLabelColor and 0x00FFFFFF)
                } else baseLabelColor

                val totalUnits = rows.maxOf { row -> row.sumOf { it.widthWeight.toDouble() }.toFloat() }

                rows.forEachIndexed { rowIdx, row ->
                    val top = rowIdx * rowH
                    var x = 0f
                    val unitW = w / totalUnits

                    row.forEach { key ->
                        val keyW = unitW * key.widthWeight
                        val rect = RectF(
                            x + padH, top + padV,
                            x + keyW - padH, top + rowH - padV
                        )

                        if (hasBg) {
                            keyPaint.style = Paint.Style.STROKE
                            keyPaint.strokeWidth = 0.5f * dens
                            keyPaint.color = Color.argb((keyAlpha * 0.3f).toInt().coerceIn(0, 255), 255, 255, 255)
                            canvas.drawRoundRect(rect, cr, cr, keyPaint)
                        } else {
                            val baseColor = if (key.isSpecial) theme.specialKeyColorInt else theme.keyColorInt
                            val a = ((baseColor ushr 24) * keyAlpha / 255).coerceIn(0, 255)
                            keyPaint.color = (a shl 24) or (baseColor and 0x00FFFFFF)
                            keyPaint.style = Paint.Style.FILL
                            canvas.drawRoundRect(rect, cr, cr, keyPaint)
                        }

                        val lbl = key.label
                        if (lbl.isNotEmpty() && lbl.length <= 3) {
                            val textY = rect.top + (rect.height() + fontSize) / 2f - textPaint.descent()
                            canvas.drawText(lbl, rect.centerX(), textY, textPaint)
                        }
                        x += keyW
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 字體顏色選擇器
    // ─────────────────────────────────────────────────────────

    private fun buildKeyLabelColorPicker(context: Context): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 4))
        }

        container.addView(TextView(context).apply {
            text = tr("按鍵字體顏色")
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 4), 0, 0, dp(context, 8))
        })

        val colors = listOf(
            0 to tr("跟隨主題"),
            0xFFFFFFFF.toInt() to tr("白"),
            0xFF000000.toInt() to tr("黑"),
            0xFF7FD8FF.toInt() to tr("冰藍"),
            0xFFFF6B9D.toInt() to tr("粉"),
            0xFFFFD700.toInt() to tr("金"),
            0xFF5EEAD4.toInt() to tr("薄荷"),
            0xFFA78BFA.toInt() to tr("紫"),
            0xFFFF6B6B.toInt() to tr("紅"),
            0xFF4ADE80.toInt() to tr("綠"),
            0xFFFF9500.toInt() to tr("橘")
        )

        val currentColor = prefs.customKeyLabelColor

        val colorRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(context, 4))
        }

        val scrollH = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }

        val colorViews = mutableListOf<View>()

        colors.forEach { (color, clabel) ->
            val isSelected = color == currentColor
            val circleSize = dp(context, 36)
            val margin = dp(context, 4)

            val wrapper = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(circleSize + margin * 2, circleSize + dp(context, 18)).apply {
                    setMargins(margin, 0, margin, 0)
                }
            }

            val circle = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(circleSize, circleSize)
                background = buildColorCircle(color, isSelected)
            }
            wrapper.addView(circle)
            colorViews.add(circle)

            wrapper.addView(TextView(context).apply {
                text = clabel
                textSize = 9f
                setTextColor(0xFF8899AA.toInt())
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = circleSize + dp(context, 2) }
            })

            wrapper.setOnClickListener {
                prefs.customKeyLabelColor = color
                colorViews.forEachIndexed { i, v ->
                    v.background = buildColorCircle(colors[i].first, colors[i].first == color)
                }
                fullPreviewView?.invalidate()
                Toast.makeText(context, if (color == 0) tr("字體顏色跟隨主題") else tr("已更改字體顏色"), Toast.LENGTH_SHORT).show()
            }

            colorRow.addView(wrapper)
        }

        scrollH.addView(colorRow)
        container.addView(scrollH)

        // 自訂顏色按鈕
        container.addView(TextView(context).apply {
            text = tr("＋ 自訂顏色...")
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 4))
            setOnClickListener {
                showCustomColorPicker(context, colors, colorViews) { pickedColor ->
                    prefs.customKeyLabelColor = pickedColor
                    colorViews.forEachIndexed { i, v ->
                        v.background = buildColorCircle(colors[i].first, false)
                    }
                    fullPreviewView?.invalidate()
                    Toast.makeText(context, tr("已設定自訂顏色"), Toast.LENGTH_SHORT).show()
                }
            }
        })

        return container
    }

    private fun buildColorCircle(color: Int, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (color == 0) {
                setColors(intArrayOf(
                    0xFFFF6B6B.toInt(), 0xFFFFD700.toInt(), 0xFF4ADE80.toInt(),
                    0xFF7FD8FF.toInt(), 0xFFA78BFA.toInt(), 0xFFFF6B9D.toInt()
                ))
                gradientType = GradientDrawable.SWEEP_GRADIENT
            } else {
                setColor(color)
            }
            if (color == 0xFFFFFFFF.toInt() || color == 0) {
                setStroke(dp(ctx, 1), 0xFF555555.toInt())
            }
            if (selected) {
                setStroke(dp(ctx, 2), 0xFF4A90E2.toInt())
            }
        }
    }

    private fun showCustomColorPicker(
        context: Context,
        colors: List<Pair<Int, String>>,
        colorViews: List<View>,
        onColorPicked: (Int) -> Unit
    ) {
        val currentColor = prefs.customKeyLabelColor.let { if (it == 0) 0xFFFFFFFF.toInt() else it }

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 8))
        }

        val previewView = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 48)).apply {
                bottomMargin = dp(context, 12)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 8).toFloat()
                setColor(currentColor)
            }
        }
        dialogLayout.addView(previewView)

        var r = Color.red(currentColor)
        var g = Color.green(currentColor)
        var b = Color.blue(currentColor)

        fun updatePreview() {
            val c = Color.rgb(r, g, b)
            previewView.background = GradientDrawable().apply {
                cornerRadius = dp(context, 8).toFloat()
                setColor(c or 0xFF000000.toInt())
            }
        }

        val hexInput = android.widget.EditText(context).apply {
            hint = "#RRGGBB"
            setText(String.format("#%02X%02X%02X", r, g, b))
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(7))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(context, 8)
            }
        }
        dialogLayout.addView(hexInput)

        fun makeSlider(sliderLabel: String, initial: Int, onChanged: (Int) -> Unit): LinearLayout {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(context, 4), 0, dp(context, 4))
            }
            row.addView(TextView(context).apply {
                text = sliderLabel
                textSize = 13f
                setTextColor(0xFFCCCCCC.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(context, 24), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val valueText = TextView(context).apply {
                text = "$initial"
                textSize = 12f
                setTextColor(0xFF7FD8FF.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(context, 32), LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.END
            }
            val sb = SeekBar(context).apply {
                max = 255
                progress = initial
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        valueText.text = "$progress"
                        onChanged(progress)
                        updatePreview()
                        hexInput.setText(String.format("#%02X%02X%02X", r, g, b))
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            row.addView(sb)
            row.addView(valueText)
            return row
        }

        val rSlider = makeSlider("R", r) { r = it }
        val gSlider = makeSlider("G", g) { g = it }
        val bSlider = makeSlider("B", b) { b = it }
        dialogLayout.addView(rSlider)
        dialogLayout.addView(gSlider)
        dialogLayout.addView(bSlider)

        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hex = s?.toString()?.removePrefix("#") ?: return
                if (hex.length == 6) {
                    try {
                        val parsed = Color.parseColor("#$hex")
                        r = Color.red(parsed)
                        g = Color.green(parsed)
                        b = Color.blue(parsed)
                        updatePreview()
                    } catch (_: Exception) {}
                }
            }
        })

        android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(tr("自訂字體顏色"))
            .setView(dialogLayout)
            .setPositiveButton(tr("確定")) { _, _ ->
                val color = Color.rgb(r, g, b) or 0xFF000000.toInt()
                onColorPicked(color)
            }
            .setNegativeButton(tr("取消"), null)
            .show()
    }

    // ─────────────────────────────────────────────────────────
    // 背景選取結果處理（由 Activity.onActivityResult 呼叫）
    // ─────────────────────────────────────────────────────────

    fun handleBgPickResult(context: Context, requestCode: Int, uri: Uri) {
        stopVideoPreview()
        val bgDir = KeyboardBackgroundView.getBackgroundDir(context)
        bgDir.listFiles()?.forEach { it.delete() }

        val mime = context.contentResolver.getType(uri) ?: ""
        val ext = when {
            mime.contains("webm")           -> "webm"
            mime.contains("3gp")            -> "3gp"
            mime.contains("x-matroska")     -> "mkv"
            mime.contains("quicktime")      -> "mov"
            mime.contains("x-msvideo")      -> "avi"
            mime.contains("mp2t")           -> "ts"
            mime.contains("video")          -> "mp4"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("webp")           -> "webp"
            mime.contains("gif")            -> "gif"
            mime.contains("bmp")            -> "bmp"
            mime.contains("heic") || mime.contains("heif") -> "heic"
            mime.contains("avif")           -> "avif"
            mime.contains("png")            -> "png"
            else -> if (requestCode == REQUEST_PICK_BG_VIDEO) "mp4" else "jpg"
        }

        val dest = java.io.File(bgDir, "keyboard_bg.$ext")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            prefs.useCustomBackground = true
            refreshBgPreview()
            context.sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(context.packageName))
            Toast.makeText(context, tr("背景已設定"), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, tr("背景設定失敗: ${e.message}"), Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────
    // Lifecycle hooks（由 Activity 呼叫）
    // ─────────────────────────────────────────────────────────

    fun onPause() {
        bgVideoPlayer?.pause()
    }

    fun onResume() {
        bgVideoPlayer?.start()
    }

    fun onStart(activity: Activity) {
        bgSyncReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshBgPreview()
                syncBgSliders()
            }
        }
        val filter = IntentFilter("com.snowrealm.yukiboard.ACTION_BG_UPDATED")
        activity.registerReceiver(bgSyncReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    fun onStop(activity: Activity) {
        bgSyncReceiver?.let {
            try { activity.unregisterReceiver(it) } catch (_: Exception) {}
        }
        bgSyncReceiver = null
    }

    fun onDestroy() {
        stopVideoPreview()
    }

    private fun syncBgSliders() {
        bgOverlaySeekBar?.progress = prefs.bgOverlayAlpha
        bgOverlayLabel?.text = tr("遮罩深度: %d%%").format((prefs.bgOverlayAlpha * 100) / 255)
        bgPreviewOverlay?.setBackgroundColor(Color.argb(prefs.bgOverlayAlpha, 0, 0, 0))
        bgBtnCrop?.let { btn ->
            val selected = prefs.bgScaleMode == 0
            btn.setTextColor(if (selected) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            btn.setBackgroundColor(if (selected) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
        }
        bgBtnFit?.let { btn ->
            val selected = prefs.bgScaleMode == 1
            btn.setTextColor(if (selected) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            btn.setBackgroundColor(if (selected) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
        }
        bgOffsetXSeekBar?.progress = (prefs.bgOffsetX * 100).toInt()
        bgOffsetXLabel?.text = tr("水平位置 X: %d%%").format((prefs.bgOffsetX * 100).toInt())
        bgOffsetYSeekBar?.progress = (prefs.bgOffsetY * 100).toInt()
        bgOffsetYLabel?.text = tr("垂直位置 Y: %d%%").format((prefs.bgOffsetY * 100).toInt())
        bgRotBtns.forEachIndexed { i, btn ->
            val d = listOf(0, 90, 180, 270)[i]
            val selected = prefs.bgRotation == d
            btn.setTextColor(if (selected) 0xFF111827.toInt() else 0xFFE8F4FF.toInt())
            btn.setBackgroundColor(if (selected) 0xFF7FD8FF.toInt() else 0xFF2A3A5C.toInt())
        }
        updateBgPreviewMatrix()
        updateVideoPreviewTransform()
        fullPreviewView?.invalidate()
    }

    companion object {
        const val REQUEST_PICK_BG_IMAGE = 9001
        const val REQUEST_PICK_BG_VIDEO = 9002
    }
}

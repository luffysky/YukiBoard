# YukiBoard — AI 補字改版 + VRM 修復 + Google TTS 音色選擇

---

## 問題一：AI 補字體驗改版

### 現況與問題

現在 AI 補字是：
- 每次打字 600ms debounce 自動觸發
- 結果靜默附加在候選列末尾（黃色 ✨ 標記）
- 使用者不確定能不能點、點了有沒有用

### 改成的樣子

```
使用者打字中...
    ↓
候選列末尾出現黃色 AI 建議詞（明確底色）
    ↓
使用者點了就 commit（跟一般候選字一樣）
    ↓
沒點就忽略，不影響輸入流程
```

核心原則：**AI 補字就是候選字，不需要特殊對待，點了就是了。**

---

### 修改 `ui/keyboard/KeyboardView.kt`

**Step 1：移除 ✨ 前綴，candWidths 重算**

找到兩處 `"✨$text"` 都改成 `text`：

```kotlin
// 第一處：candWidths 計算（約 line 950）
val candWidths = candidates.mapIndexed { i, text ->
    val display = when {
        isSwipeCandidateMode && i == 0 -> "≈$text"
        else -> text   // ← 移除 "✨$text"
    }
    candidatePaint.measureText(display) + candPadH * 2
}

// 第二處：drawText 繪製（約 line 1006）
val displayText = when {
    isSwipeCandidateMode && i == 0 -> "≈$text"
    else -> text   // ← 移除 "✨$text"
}
canvas.drawText(displayText, cx, candidateBarHeight * 0.65f, candidatePaint)
```

**Step 2：AI 候選字改為明顯可點的樣式**

找到 AI 的顏色區塊，把 alpha 40 改成 80，並加右上角小圓點標記：

```kotlin
// 找到 CandidateSource.AI 的繪製區段
if (i == 0 && candidates.size > 1) {
    // 第一個候選字（已有底色）
    keyPaint.color = if (sourceColor != 0) sourceColor else theme.candidateSelectedColorInt
    keyPaint.alpha = 40
    ...
} else {
    // 其他候選字的 AI 底色加深（讓使用者知道可以點）
    if (candidateSourceMap[text] == CandidateSource.AI) {
        keyPaint.color = 0xFFE8B84D.toInt()
        keyPaint.alpha = 80   // 原本 40 太淡，改 80
        val pill = RectF(
            left + 2 * density, 4 * density,
            right - 2 * density, candidateBarHeight - 4 * density
        )
        canvas.drawRoundRect(pill, 6 * density, 6 * density, keyPaint)
        keyPaint.alpha = 255

        // AI 小標：右上角畫小圓點（不影響 touch area）
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8B84D.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(right - 5f * density, 5f * density, 3f * density, dotPaint)
    }
    candidatePaint.color = if (sourceColor != 0) sourceColor else theme.candidateTextColorInt
}
```

**Step 3：加「AI」小標文字（可選）**

如果想讓使用者更清楚，在 AI 候選字旁邊加小字 "AI"：

```kotlin
// 在 AI 候選字繪製完 displayText 之後加：
if (candidateSourceMap[text] == CandidateSource.AI) {
    val aiLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8B84D.toInt()
        textSize = 8f * density
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("AI", right - 3f * density, candidateBarHeight * 0.25f, aiLabelPaint)
}
```

---

### 修改 `service/YukiBoardIMEService.kt`

**AI 補字只在有意義的時機觸發（不是每次打字都跑）：**

找到 `requestAICandidates()` 函式，在開頭加條件：

```kotlin
private fun requestAICandidates() {
    aiCandidateJob?.cancel()
    val client = clawClient ?: return
    if (!client.isAvailable() || !prefs.aiPrediction) return

    val composing = inputRouter.composingText
    val context = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: ""

    // ── 新增：觸發條件收緊，避免亂打 ──
    // 1. 注音輸入中不觸發（composing 有注音符號時）
    if (composing.any { it in 'ㄅ'..'ㄩ' || it in "ˉˊˇˋ˙" }) return
    // 2. 上下文太短不觸發
    if (composing.isEmpty() && context.length < 3) return
    // 3. 英文模式且字串很短不觸發
    if (inputRouter.inputMode == InputMode.ENGLISH && composing.length < 2 && context.length < 5) return

    // 以下不變...
    aiCandidateJob = serviceScope.launch {
        ...
    }
}
```

---

## 問題二：雪凜 VRM 不出來

### 根本原因

**SurfaceView 在 IME window 裡有嚴重限制。**

Android 的輸入法 window 類型是 `TYPE_INPUT_METHOD`，
在這個 window 裡 `SurfaceView` 的 `getDisplay()` 在某些裝置和 Android 版本會回傳 null，
導致 `DisplayHelper.attach(renderer, null)` 靜默失敗，畫面一片空白。

```kotlin
// VrmCharacterView.kt 裡
displayHelper?.attach(r, this@VrmCharacterView.display)
//                        ↑ 這個 display 在 IME 裡可能是 null
```

此外 `SurfaceView.setZOrderOnTop(true)` 在 IME 裡可能被系統覆蓋。

### 解法：IME 裡用 TextureView 而不是 SurfaceView

`TextureView` 沒有獨立的 Surface，直接走 View 的硬體加速，不依賴 `getDisplay()`，在 IME 裡可以正常顯示。

---

### 修改 `animation/VrmCharacterView.kt`

**Step 1：把繼承從 `SurfaceView` 改成 `TextureView`**

```kotlin
// 原本：
class VrmCharacterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr) {

// 改為：
class VrmCharacterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {
```

**Step 2：移除 `SurfaceHolder` 相關，改用 `SurfaceTextureListener`**

```kotlin
init {
    isClickable = false
    isFocusable = false
    isOpaque = false           // TextureView 預設透明
    surfaceTextureListener = this   // ← 設定 listener
    // 移除：setZOrderOnTop(true)
    // 移除：holder.setFormat(PixelFormat.TRANSLUCENT)
    initFilament()
}
```

**Step 3：移除 `UiHelper`，改用 `SurfaceTextureListener` 管理 SwapChain**

```kotlin
// 移除 UiHelper 和 DisplayHelper，改用：

override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
    val eng = engine ?: return
    val nativeSurface = android.view.Surface(surface)
    swapChain?.let { eng.destroySwapChain(it) }
    swapChain = eng.createSwapChain(
        nativeSurface,
        SwapChainFlags.CONFIG_TRANSPARENT
    )
    updateViewport(width, height)
    startRendering()
}

override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    updateViewport(width, height)
}

override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
    stopRendering()
    swapChain?.let {
        engine?.destroySwapChain(it)
        swapChain = null
    }
    return true
}

override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
```

**Step 4：`render()` 函式不再需要 `uiHelper`**

找到 `render()` 函式，移除 `uiHelper?.isReadyToRender` 的判斷，
改成直接檢查 `swapChain != null`：

```kotlin
private fun render() {
    val eng = engine ?: return
    val rend = renderer ?: return
    val sc = swapChain ?: return   // 改為直接檢查
    val v = view ?: return

    if (rend.beginFrame(sc, System.nanoTime())) {
        rend.render(v)
        rend.endFrame(eng)
    }
}
```

**Step 5：加空白 IndirectLight cubemap 防止黑模型**

當 IndirectLight 沒有 cubemap 時，模型可能全黑。
加一個 1x1 的空白 cubemap：

```kotlin
// 在 initFilament() 的 IndirectLight 建立改為：
val ibl = IndirectLight.Builder()
    .intensity(50000f)   // 提高強度
    .build(eng)
scene?.indirectLight = ibl

// 加補充環境光（不用 cubemap 也能看到模型）
val ambientEntity = EntityManager.get().create()
LightManager.Builder(LightManager.Type.POINT)
    .color(0.9f, 0.9f, 1.0f)
    .intensity(200000f)
    .position(0f, 2f, 2f)
    .falloff(10f)
    .build(eng, ambientEntity)
scene?.addEntity(ambientEntity)
```

**Step 6：loadModelFromAsset 加錯誤回呼**

```kotlin
fun loadModelFromAsset(assetPath: String, onError: ((String) -> Unit)? = null) {
    scope.launch(Dispatchers.IO) {
        try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            withContext(Dispatchers.Main) {
                loadModelBytes(bytes)
                Log.i("VrmCharacterView", "Model loaded: ${bytes.size} bytes")
            }
        } catch (e: Exception) {
            Log.e("VrmCharacterView", "Failed to load VRM: $assetPath", e)
            withContext(Dispatchers.Main) {
                onError?.invoke(e.message ?: "load failed")
            }
        }
    }
}
```

### 修改 `ui/keyboard/CharacterView.kt`

**在 VRM 載入失敗時顯示 Log + fallback：**

```kotlin
fun initVrmMode(assetPath: String = VRM_ASSET_PATH) {
    try {
        com.google.android.filament.utils.Utils.init()
        cleanupCurrentMode()
        renderMode = RenderMode.VRM

        val vv = VrmCharacterView(context).apply {
            isClickable = false
            isFocusable = false
        }
        addView(vv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        vrmView = vv

        val anim = VrmCharacterAnimation(vv)
        vrmAnimation = anim
        characterAnimation = anim

        // 加錯誤回呼
        vv.loadModelFromAsset(assetPath) { error ->
            Log.e("CharacterView", "VRM load error: $error, falling back to Lottie")
            post {  // 回到 Main thread
                vrmView?.let { removeView(it) }
                vrmView = null
                vrmAnimation = null
                characterAnimation = null
                initLottieMode()
            }
        }

        vv.postDelayed({ playIdle() }, 2000)
    } catch (e: Throwable) {
        Log.e("CharacterView", "VRM init failed: ${e.javaClass.simpleName}: ${e.message}")
        vrmView?.let { removeView(it) }
        vrmView = null
        vrmAnimation = null
        characterAnimation = null
        initLottieMode()
    }
}
```

---

## 問題三：Google TTS 音色選擇

### 台灣中文可用的 Google Cloud TTS 音色

```
語言：cmn-TW（台灣中文）

WaveNet（高品質）：
  cmn-TW-Wavenet-A  → 女聲
  cmn-TW-Wavenet-B  → 男聲
  cmn-TW-Wavenet-C  → 女聲（較年輕）
  cmn-TW-Wavenet-D  → 男聲（較低沉）

Neural2（更自然）：
  cmn-TW-Neural2-A  → 女聲
  cmn-TW-Neural2-B  → 男聲
  cmn-TW-Neural2-C  → 女聲
  cmn-TW-Neural2-D  → 男聲

Standard（免費、較機械）：
  cmn-TW-Standard-A → 女聲
  cmn-TW-Standard-B → 男聲
  cmn-TW-Standard-C → 女聲
  cmn-TW-Standard-D → 男聲

英文可用：
  en-US-Wavenet-A/B/C/D/E/F/G/H/I/J
  en-US-Neural2-A ~ J
  en-US-Studio-O（最高品質，Studio 版）
```

### 修改 `speech/GoogleCloudTtsEngine.kt`

**把 `voiceName` 改為可從設定讀取：**

```kotlin
// 原本：固定 "zh-TW-Wavenet-A"
class GoogleCloudTtsEngine(
    private val apiKey: String,
    private val voiceName: String = "cmn-TW-Wavenet-A",  // 可被設定覆蓋
    private val audioEncoding: String = "LINEAR16"
) : SpeechOutputEngine {
```

**`pickVoice()` 改為支援使用者選擇的音色：**

```kotlin
private fun pickVoice(langCode: String): Pair<String, String> {
    // 如果 voiceName 是完整 voice name（包含語言碼），直接用
    if (voiceName.contains("-") && voiceName.length > 8) {
        val langFromVoice = voiceName.substringBeforeLast("-").substringBeforeLast("-")
        return langFromVoice to voiceName
    }

    // fallback：根據語言自動選
    val lang = langCode.lowercase()
    return when {
        lang.startsWith("cmn-tw") || lang.startsWith("zh-tw") ->
            "cmn-TW" to "cmn-TW-Wavenet-A"
        lang.startsWith("cmn") || lang.startsWith("zh") ->
            "cmn-CN" to "cmn-CN-Wavenet-A"
        lang.startsWith("en") ->
            "en-US" to "en-US-Wavenet-D"
        lang.startsWith("ja") ->
            "ja-JP" to "ja-JP-Wavenet-A"
        lang.startsWith("ko") ->
            "ko-KR" to "ko-KR-Wavenet-A"
        else -> "cmn-TW" to "cmn-TW-Wavenet-A"
    }
}
```

### 修改 `ui/settings/YukiPreferences.kt`

新增音色偏好：

```kotlin
var googleTtsVoiceName: String
    get() = prefs.getString("google_tts_voice", "cmn-TW-Wavenet-A") ?: "cmn-TW-Wavenet-A"
    set(value) = prefs.edit().putString("google_tts_voice", value).apply()
```

### 修改 `ui/settings/SettingsActivity.kt`

在 TTS 設定區加音色選擇器（在 Google TTS API Key 輸入框下方）：

```kotlin
// 在 ttsSettingsSection() 的 Google TTS 設定區加：

// 只在選了 Google TTS 時顯示
if (prefs.ttsEngineType == 1) {

    val voiceOptions = arrayOf(
        // 台灣中文
        "cmn-TW-Wavenet-A（女聲）",
        "cmn-TW-Wavenet-B（男聲）",
        "cmn-TW-Wavenet-C（女聲2）",
        "cmn-TW-Wavenet-D（男聲2）",
        "cmn-TW-Neural2-A（女聲·自然）",
        "cmn-TW-Neural2-B（男聲·自然）",
        "cmn-TW-Neural2-C（女聲2·自然）",
        "cmn-TW-Neural2-D（男聲2·自然）",
        "cmn-TW-Standard-A（女聲·標準）",
        "cmn-TW-Standard-B（男聲·標準）",
        // 英文
        "en-US-Wavenet-D（英文·男聲）",
        "en-US-Wavenet-F（英文·女聲）",
        "en-US-Neural2-H（英文·女聲·自然）",
        "en-US-Neural2-J（英文·男聲·自然）",
    )
    val voiceValues = arrayOf(
        "cmn-TW-Wavenet-A",
        "cmn-TW-Wavenet-B",
        "cmn-TW-Wavenet-C",
        "cmn-TW-Wavenet-D",
        "cmn-TW-Neural2-A",
        "cmn-TW-Neural2-B",
        "cmn-TW-Neural2-C",
        "cmn-TW-Neural2-D",
        "cmn-TW-Standard-A",
        "cmn-TW-Standard-B",
        "en-US-Wavenet-D",
        "en-US-Wavenet-F",
        "en-US-Neural2-H",
        "en-US-Neural2-J",
    )

    val currentVoice = prefs.googleTtsVoiceName
    val currentIdx = voiceValues.indexOf(currentVoice).takeIf { it >= 0 } ?: 0
    val currentLabel = voiceOptions[currentIdx]

    val voiceRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, 0)
    }
    voiceRow.addView(TextView(this).apply {
        text = "語音音色"
        textSize = 12f
        setTextColor(0xFF8899BB.toInt())
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
    })

    val voiceBtn = TextView(this).apply {
        text = currentLabel
        textSize = 12f
        setTextColor(0xFF9ECFFF.toInt())
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF1A2744.toInt())
            cornerRadius = dp(6).toFloat()
            setStroke(1, 0xFF3A5580.toInt())
        }
        setOnClickListener {
            android.app.AlertDialog.Builder(
                this@SettingsActivity,
                android.R.style.Theme_Material_Dialog_Alert
            )
                .setTitle("選擇語音音色")
                .setSingleChoiceItems(
                    voiceOptions,
                    voiceValues.indexOf(prefs.googleTtsVoiceName).takeIf { it >= 0 } ?: 0
                ) { dialog, which ->
                    prefs.googleTtsVoiceName = voiceValues[which]
                    text = voiceOptions[which]
                    dialog.dismiss()
                }
                .show()
        }
    }
    voiceRow.addView(voiceBtn)
    outer.addView(voiceRow)
}
```

### 修改 `service/YukiBoardIMEService.kt`

建立 TTS engine 時帶入使用者選擇的音色：

```kotlin
// 找到 ttsEngine 初始化的地方（約 line 1215）
ttsEngine = when (prefs.ttsEngineType) {
    1 -> {
        val key = prefs.googleCloudTtsApiKey
        if (key.isBlank()) null
        else com.snowrealm.yukiboard.speech.GoogleCloudTtsEngine(
            apiKey = key,
            voiceName = prefs.googleTtsVoiceName  // ← 加這個
        )
    }
    else -> com.snowrealm.yukiboard.speech.AndroidTtsEngine(applicationContext)
}
```

---

## 執行順序

```
1. AI 補字改版：
   KeyboardView.kt → 移除 ✨，加深底色，加 AI 小標
   YukiBoardIMEService.kt → requestAICandidates 觸發條件收緊

2. VRM 修復：
   VrmCharacterView.kt → SurfaceView 改 TextureView
   CharacterView.kt → loadModelFromAsset 加錯誤回呼 + fallback

3. Google TTS 音色：
   YukiPreferences.kt → 加 googleTtsVoiceName
   GoogleCloudTtsEngine.kt → pickVoice 支援完整 voice name
   SettingsActivity.kt → 音色選擇 UI
   YukiBoardIMEService.kt → 建立 TTS engine 傳入音色

Build + 測試
```

---

## 驗證清單

```
AI 補字：
□ 候選列末尾出現黃色底色但無 ✨ 前綴的詞
□ 點那個詞 → 輸入框出現該詞
□ 注音輸入中不觸發 AI 補字
□ 右上角有小圓點標記

VRM：
□ 設定開啟 VRM 角色後，雪凜 3D 模型出現在鍵盤左上
□ 不出現時自動 fallback 到 Lottie（不閃退）
□ Log 有 "Model loaded: XXXXX bytes" 訊息

Google TTS 音色：
□ 設定頁 → 語音輸出 → Google Cloud TTS → 出現音色選擇
□ 選 cmn-TW-Neural2-A → 測試按鈕 → 女聲自然語調
□ 選 cmn-TW-Wavenet-B → 測試按鈕 → 男聲
□ 下次開啟設定音色設定保留
```

# YukiBoard 背景設定同步修復

---

## 缺口一：設定頁改滑桿沒有即時通知鍵盤

### `ui/settings/SettingsActivity.kt`

**遮罩深度 SeekBar** — `onProgressChanged` 加一行廣播：

```kotlin
override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
    prefs.bgOverlayAlpha = p
    alphaLabel.text = tr("遮罩深度: %d%%").format((p * 100) / 255)
    bgPreviewOverlay?.setBackgroundColor(android.graphics.Color.argb(p, 0, 0, 0))
    fullPreviewView?.invalidate()
    // ← 新增：即時通知鍵盤
    sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
}
```

**保持比例 / 整張塞入按鈕** — 各自加廣播：

```kotlin
btnCrop.setOnClickListener {
    prefs.bgScaleMode = 0
    // ... 顏色設定不變 ...
    updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
    // ← 新增
    sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
}
btnFit.setOnClickListener {
    prefs.bgScaleMode = 1
    // ... 顏色設定不變 ...
    updateBgPreviewMatrix(); updateVideoPreviewTransform(); fullPreviewView?.invalidate()
    // ← 新增
    sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
}
```

**X 位置 SeekBar** — `onStopTrackingTouch`（拖完才發，不要每個 px 都發）：

```kotlin
override fun onStopTrackingTouch(sb: SeekBar?) {
    // ← 新增：拖完才通知，避免頻繁廣播
    sendBroadcast(Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
}
```

**Y 位置 SeekBar** — 同上，`onStopTrackingTouch` 加廣播。

---

## 缺口二：設定頁沒有監聽功能列主題面板的改動

### `ui/settings/SettingsActivity.kt`

在 `onCreate` 或 `onStart` 裡註冊 BG_UPDATED 廣播接收器，收到後重新整理預覽：

**加成員變數：**

```kotlin
private var bgSyncReceiver: android.content.BroadcastReceiver? = null
```

**`onStart()` 裡註冊：**

```kotlin
override fun onStart() {
    super.onStart()
    bgSyncReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            // 功能列主題面板改了背景設定，同步更新設定頁的預覽和滑桿
            refreshBgPreview()
            syncBgSliders()   // ← 見下方新函式
        }
    }
    val filter = android.content.IntentFilter("com.snowrealm.yukiboard.ACTION_BG_UPDATED")
    registerReceiver(bgSyncReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
}
```

**`onStop()` 裡取消註冊：**

```kotlin
override fun onStop() {
    super.onStop()
    bgSyncReceiver?.let {
        try { unregisterReceiver(it) } catch (_: Exception) {}
    }
    bgSyncReceiver = null
}
```

**新增 `syncBgSliders()` 函式** — 把 prefs 的值同步到 UI 元件：

這個函式要更新：
- 遮罩深度 SeekBar 的 progress → `prefs.bgOverlayAlpha`
- 遮罩深度 label 文字
- 保持比例/整張塞入 按鈕的選取狀態
- X/Y 位置 SeekBar 的 progress
- X/Y 位置 label 文字
- `bgPreviewOverlay` 的顏色

```kotlin
private fun syncBgSliders() {
    // 遮罩深度
    bgOverlaySeekBar?.progress = prefs.bgOverlayAlpha
    bgOverlayLabel?.text = tr("遮罩深度: %d%%").format((prefs.bgOverlayAlpha * 100) / 255)
    bgPreviewOverlay?.setBackgroundColor(
        android.graphics.Color.argb(prefs.bgOverlayAlpha, 0, 0, 0)
    )
    // 比例模式按鈕
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
    // X/Y 位置
    bgOffsetXSeekBar?.progress = (prefs.bgOffsetX * 100).toInt()
    bgOffsetXLabel?.text = tr("水平位置 X: %d%%").format((prefs.bgOffsetX * 100).toInt())
    bgOffsetYSeekBar?.progress = (prefs.bgOffsetY * 100).toInt()
    bgOffsetYLabel?.text = tr("垂直位置 Y: %d%%").format((prefs.bgOffsetY * 100).toInt())
    // 預覽
    updateBgPreviewMatrix()
    updateVideoPreviewTransform()
    fullPreviewView?.invalidate()
}
```

**同時把現在是區域變數的 UI 元件改為成員變數**，讓 `syncBgSliders()` 能存取：

```kotlin
// 在 class 頂部加成員變數
private var bgOverlaySeekBar: SeekBar? = null
private var bgOverlayLabel: TextView? = null
private var bgBtnCrop: android.widget.Button? = null
private var bgBtnFit: android.widget.Button? = null
private var bgOffsetXSeekBar: SeekBar? = null
private var bgOffsetXLabel: TextView? = null
private var bgOffsetYSeekBar: SeekBar? = null
private var bgOffsetYLabel: TextView? = null
```

在 `buildBackgroundCard()` 裡建立這些 UI 元件時，把它們指派給成員變數：

```kotlin
// 建立遮罩深度 SeekBar 後：
bgOverlaySeekBar = seekBar  // 假設局部變數叫 seekBar，賦值給成員
bgOverlayLabel = alphaLabel

// 建立比例按鈕後：
bgBtnCrop = btnCrop
bgBtnFit = btnFit

// 建立 X 位置 SeekBar 後：
bgOffsetXSeekBar = seekBar  // X 的 seekbar
bgOffsetXLabel = xLabel

// 建立 Y 位置 SeekBar 後：
bgOffsetYSeekBar = seekBar  // Y 的 seekbar
bgOffsetYLabel = yLabel
```

---

## 執行順序

```
1. SettingsActivity.kt：
   a. class 頂部加 8 個成員變數 + bgSyncReceiver
   b. buildBackgroundCard() 裡把局部 UI 元件指派給成員變數
   c. 遮罩 SeekBar.onProgressChanged 加廣播
   d. btnCrop/btnFit click 加廣播
   e. X/Y SeekBar.onStopTrackingTouch 加廣播
   f. 新增 syncBgSliders()
   g. 新增 onStart() 註冊廣播
   h. 新增 onStop() 取消廣播
```

---

## 驗證

```
設定頁 → 鍵盤即時同步：
□ 拖動遮罩深度 → 鍵盤背景遮罩即時變化
□ 按保持比例/塞入 → 鍵盤背景比例即時切換
□ 拖動 X/Y 位置（放開後）→ 鍵盤背景位置更新

功能列 → 設定頁即時同步：
□ 在功能列主題面板拖遮罩滑桿 → 設定頁預覽和滑桿同步更新
□ 在功能列選影片/圖片 → 設定頁預覽顯示新背景
□ 在功能列清除背景 → 設定頁預覽變空白
□ 在功能列改 X/Y 位置 → 設定頁滑桿位置同步
```

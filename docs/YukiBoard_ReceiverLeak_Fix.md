# YukiBoard 反覆亂掉的根本原因修復

## 根本原因

`onCreateInputView()` 在三個地方被重複呼叫：
1. `keyboardView.setOnFloatingToggleListener`（切換浮動模式）
2. `p.onKeyboardHeightChanged`（調整鍵盤高度）
3. `wrapper.onToggleDock`（浮動模式退出）

每次呼叫 `onCreateInputView()` 都會在末尾執行：
```kotlin
registerReceiver(bgUpdateReceiver, bgFilter, ...)
```

但呼叫前**沒有先 unregister 舊的 receiver**。

結果：
- 第一次建立：1 個 receiver
- 調整高度一次：2 個 receiver
- 切換浮動一次：3 個 receiver
- ...越來越多

當背景被刪除、發出 `BG_UPDATED` 廣播時，**所有累積的 receiver 同時收到**，每個都去操作 `backgroundView` 和 `keyboardView`，其中舊的 receiver 持有的是舊的 `keyboardView` instance，新舊狀態互相覆蓋 → 行為開始亂。

刪背景後亂得特別明顯，是因為刪背景一定會發廣播，而且把 `hasCustomBackground` 改成 false，多個 receiver 競爭把這個值設來設去。

---

## 修法

### `service/YukiBoardIMEService.kt`

在 `onCreateInputView()` 的**最開頭**加一行，確保每次重建前先清掉舊的 receiver：

```kotlin
override fun onCreateInputView(): View {
    // ← 新增：重建前先清掉舊的 receiver，避免多次 register 累積
    bgUpdateReceiver?.let {
        try { unregisterReceiver(it) } catch (_: Exception) {}
    }
    bgUpdateReceiver = null

    // 確保 IME window 啟用硬體加速
    window?.window?.let { w ->
        @Suppress("DEPRECATION")
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    }
    // ... 其餘不變
}
```

就這一個改動。

---

## 為什麼這樣就夠

`onDestroy` 裡已經有正確的 unregister：
```kotlin
bgUpdateReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
bgUpdateReceiver = null
```

但 `onDestroy` 只在 Service 被殺掉時呼叫，正常使用時不會呼叫。每次 `setInputView(onCreateInputView())` 重建 View 時，`onDestroy` 不會被呼叫，所以舊的 receiver 沒有被清掉。

在 `onCreateInputView()` 開頭加 unregister，就能確保每次重建前都清掉上一個，永遠只有一個 receiver 在跑。

---

## 驗證

```
□ 設定背景圖片 → 正常顯示
□ 調整鍵盤高度（觸發 onCreateInputView 重建）→ 背景仍正常
□ 切換浮動模式（觸發 onCreateInputView 重建）→ 背景仍正常
□ 刪除背景 → 背景正確消失，鍵盤恢復正常，不亂掉
□ 重複上述操作 10 次 → 始終正常，不累積
```

# YukiBoard 兩個緊急修復

---

## 問題一：工具列背景透明

### 根本原因

`ToolbarView extends View`，Android 規定：**自訂 View 若沒有設定 background drawable，`willNotDraw` 預設為 `true`，`onDraw()` 不會被呼叫**。

所以不管 `onDraw` 裡的 `bgPaint.drawRect` 有多正確，Android 根本沒有呼叫它。

### 修改 `ui/panel/ToolbarView.kt`

在 class 頂部，所有 `private val` 宣告之後，加一個 `init` block：

```kotlin
class ToolbarView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val barHeight = (40 * density).toInt()

    // ← 在這裡加 init block
    init {
        // 告訴 Android 這個 View 需要 onDraw（沒有 background drawable 時預設不繪製）
        setWillNotDraw(false)
    }

    // ... 其餘不變
```

同時移除 `onDraw` 裡的 `super.onDraw(canvas)`（在硬體加速 + clipChildren=false 下可能穿透）：

```kotlin
override fun onDraw(canvas: Canvas) {
    // super.onDraw(canvas)  ← 刪掉這行

    // 背景
    if (isTransparent) {
        bgPaint.color = 0x88000000.toInt()
    } else {
        bgPaint.color = theme.effectiveToolbarColor()
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    // ... 其餘不變
```

---

## 問題二：候選字按了輸出兩次（像這樣像這樣）

### 根本原因

`applyInputResult` 的 `CommitText` 分支：

```kotlin
ic.beginBatchEdit()
ic.finishComposingText()   // ← 問題在這！
ic.commitText(result.text, 1)
ic.endBatchEdit()
```

**`finishComposingText()` 在 LINE 等 App 裡，會把 composing buffer 裡的文字先 commit 一次**。
接著 `commitText(result.text, 1)` 再 commit 一次。

注音輸入的流程是：
1. 打注音 → `setComposingText("這樣", 1)` → 輸入框顯示「這樣」（composing 狀態）
2. 使用者點候選字 → `CommitText("這樣")`
3. `finishComposingText()` → LINE 把 composing 的「這樣」確認 commit
4. `commitText("這樣", 1)` → 再 commit 一次
5. 結果：「這樣這樣」

### 修改 `service/YukiBoardIMEService.kt` — `applyInputResult()`

找到這段（約 line 3045）：

```kotlin
is InputResult.CommitText -> {
    // ...情緒冷靜期...

    ic.beginBatchEdit()
    ic.finishComposingText()   // ← 改掉
    ic.commitText(result.text, 1)
    ic.endBatchEdit()
```

**改為：**

```kotlin
is InputResult.CommitText -> {
    // ...情緒冷靜期...

    ic.beginBatchEdit()
    ic.setComposingText("", 1)   // ← 改：清空 composing 但不 commit，游標移到後面
    ic.commitText(result.text, 1)
    ic.endBatchEdit()
```

### 說明

`setComposingText("", 1)` 的效果：
- 把 composing buffer 設成空字串
- 不會把舊的 composing 文字 commit
- 游標移到正確位置

`finishComposingText()` 的效果（問題所在）：
- 把現有 composing 文字「確定」（某些 App 會 commit 它）
- 然後 `commitText` 又 commit 一次 → 重複

---

## 執行順序

```
1. ui/panel/ToolbarView.kt：
   a. class 頂部加 init { setWillNotDraw(false) }
   b. onDraw 第一行刪掉 super.onDraw(canvas)

2. service/YukiBoardIMEService.kt：
   applyInputResult 裡把 finishComposingText() 改成 setComposingText("", 1)

Build → 測試：
□ 工具列有背景色（不透明）
□ 點候選字只輸出一次
□ 注音模式選字正常
```

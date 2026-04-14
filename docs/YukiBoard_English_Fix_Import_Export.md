# YukiBoard 英文重複輸出修復 + 詞庫匯入匯出

---

## Bug 1：英文輸入重複（hellohello）

### 根本原因

打字時 composing 透過 `setComposingText("hello", 1)` 設定。
按空白時 `applyInputResult` 做：

```kotlin
ic.setComposingText("", 0)   // ← 這行把 composing "hello" commit 成普通文字
ic.commitText("hello ", 1)   // ← 又寫入一次，變成 "hellohello "
```

`setComposingText("", 0)` 的行為：
- 清除 composing 標記
- 但 composing 內容（hello）已經變成 committed text 留在文字區
- 接著 `commitText("hello ")` 又插入一次

### 修法

**`service/YukiBoardIMEService.kt` — `applyInputResult()` 的 `CommitText` 分支**

```kotlin
// 原本：
ic.beginBatchEdit()
ic.setComposingText("", 0)  // ← 問題所在
ic.commitText(result.text, 1)
ic.endBatchEdit()

// 改為：
ic.beginBatchEdit()
ic.finishComposingText()    // ← 只取消 composing 標記，不 commit 內容
                             //    然後用 deleteSurroundingText 刪掉 composing 字元
                             //    再 commitText 寫入正確內容
// 但 finishComposingText 也會 commit... 正確做法如下：
ic.endBatchEdit()
```

**實際上正確的做法是用 `commitText` 直接取代 composing，不需要先清除：**

Android 的 `commitText` 會自動替換當前的 composing region。
所以只需要：

```kotlin
ic.beginBatchEdit()
ic.commitText(result.text, 1)  // 直接 commit，自動替換掉 composing region
ic.endBatchEdit()
```

不需要先呼叫 `setComposingText("", 0)`，`commitText` 本身就會清掉 composing 並寫入新文字。

**找到這段並修改：**

位置：`applyInputResult()` 函式裡 `is InputResult.CommitText` 分支

```kotlin
is InputResult.CommitText -> {
    // ... 情緒冷靜期檢查（保留不動）...

    ic.beginBatchEdit()
    // 刪掉這行：ic.setComposingText("", 0)
    ic.commitText(result.text, 1)   // commitText 自動替換 composing region
    ic.endBatchEdit()

    // ... 其餘不動 ...
}
```

---

## 詞庫匯入匯出

`UserDictionary` 已有 `exportToString()` 和 `importFromString()` 方法，只差 UI 入口。

### `ui/settings/SettingsActivity.kt` — 個人詞庫面板加匯入匯出按鈕

找到個人詞庫相關的卡片（`UserDictionaryPanelView` 或詞庫設定區），加兩個按鈕：

```kotlin
// ── 匯出按鈕 ──
card.addView(styledButton("匯出詞庫", 0xFF2A3A5C.toInt(), 0xFFE8F4FF.toInt()) {
    val dict = com.snowrealm.yukiboard.input.dictionary.UserDictionary(this)
    val data = dict.exportToString()
    if (data.isBlank()) {
        Toast.makeText(this, "詞庫是空的", Toast.LENGTH_SHORT).show()
        return@styledButton
    }
    // 分享到其他 app（備忘錄、雲端硬碟等）
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "YukiBoard 個人詞庫")
        putExtra(android.content.Intent.EXTRA_TEXT, data)
    }
    startActivity(android.content.Intent.createChooser(intent, "匯出詞庫"))
}.apply {
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
    ).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) }
})

// ── 匯入按鈕 ──
card.addView(styledButton("匯入詞庫", 0xFF2A3A5C.toInt(), 0xFFE8F4FF.toInt()) {
    // 開啟文字檔選擇器
    val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
        type = "text/plain"
        addCategory(android.content.Intent.CATEGORY_OPENABLE)
    }
    try {
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_IMPORT_DICT)
    } catch (_: Exception) {
        Toast.makeText(this, "無法開啟檔案選擇器", Toast.LENGTH_SHORT).show()
    }
}.apply {
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
    ).apply { setMargins(dp(12), dp(4), dp(12), dp(8)) }
})
```

**在 companion object 加常數：**

```kotlin
private const val REQUEST_IMPORT_DICT = 9010
```

**在 `onActivityResult` 加匯入處理：**

```kotlin
REQUEST_IMPORT_DICT -> {
    if (resultCode != RESULT_OK) return
    val uri = data?.data ?: return
    try {
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
        val dict = com.snowrealm.yukiboard.input.dictionary.UserDictionary(this)
        val count = dict.importFromString(text)
        Toast.makeText(this, "成功匯入 $count 個詞", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(this, "匯入失敗：${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

---

## 執行順序

```
1. YukiBoardIMEService.applyInputResult()
   CommitText 分支：刪掉 ic.setComposingText("", 0) 那行
   → Build + 測試：打 hello 按空白，只出現一次

2. SettingsActivity
   個人詞庫區加匯出/匯入按鈕
   companion object 加 REQUEST_IMPORT_DICT = 9010
   onActivityResult 加 REQUEST_IMPORT_DICT 處理

Build → 測試
```

---

## 驗證

```
英文重複修復：
□ 打 hello 按空白 → 只出現 "hello "（不重複）
□ 打 hello 選候選字 → 只出現一次
□ AUTO 模式英文輸入 → 不重複
□ 注音/日文輸入不受影響

詞庫匯出：
□ 設定頁「個人詞庫」有「匯出詞庫」按鈕
□ 點匯出 → 分享選單，可選備忘錄/雲端硬碟
□ 詞庫是空的時顯示提示

詞庫匯入：
□ 設定頁有「匯入詞庫」按鈕
□ 點匯入 → 開啟文字檔選擇器
□ 選 TSV 格式文字檔 → 成功匯入，顯示匯入筆數
□ 格式錯誤 → 顯示錯誤訊息
```

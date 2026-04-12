# YukiBoard 候選字修復 + 自訂詞彙 任務包

> 給 Claude Code 執行，依序完成兩個批次。
> 基於最新版 YukiBoard-main（已確認其他 bug 都修好）

---

## 目前狀況確認

✅ 選字 hitTestCandidateIndex 已修（logicalX 正確）
✅ 設定頁 isRestarting + finish/startActivity 已修
✅ ClawClient getSkill() 工廠方法已修
✅ 所有 !! 強制非空已清除

❌ 待修：lookupMultiSyllable fallback 只顯示最後音節
❌ 待做：UserDictionary 加注音欄位 + 自訂詞彙 UI

---

## 第一批 — 修復 lookupMultiSyllable fallback

### 問題說明

打 `ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ`（迷客夏）時：
- phraseDict 查不到 `ㄇㄎㄒ` 對應詞組
- fallback 只加最後音節 `ㄒㄧㄚˋ` 的單字
- 候選列只顯示「夏、下、嚇」，前兩個音節完全消失

### 修復位置

**檔案：** `input/engine/ChewingInputEngine.kt`

找到 `lookupMultiSyllable()` 裡的第 4 步（最後備選）：

```kotlin
// ── 4. 最後一個音節的單字候選（backup） ──
val lastSyllable = syllables.last()
val lastChars = lookupCandidates(lastSyllable)
results.addAll(lastChars.take(20))

return results.toList()
```

**改成：**

```kotlin
// ── 4. 每個音節都加單字候選（backup） ──
// 查不到詞組時，至少讓每個音節的字都顯示出來
// 讓用戶可以逐字選，而不是只看到最後一個音節
if (results.isEmpty()) {
    // 完全查不到詞組：每個音節都加，第一個音節排最前
    syllables.forEachIndexed { i, syllable ->
        val chars = lookupCandidates(syllable)
        results.addAll(chars.take(if (i == 0) 10 else 6))
    }
} else {
    // 有部分結果：只補最後音節防止候選太少
    val lastSyllable = syllables.last()
    val lastChars = lookupCandidates(lastSyllable)
    results.addAll(lastChars.take(10))
}

return results.toList()
```

### 驗證方式

打 `ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ` 後：
- 候選列應出現「迷、謎、彌」（第一音節）+ 「客、刻、課」（第二音節）+ 「夏、下、嚇」（第三音節）
- 不再只顯示最後音節的字

---

## 第二批 — UserDictionary 加注音 + 自訂詞彙 UI

### 2-A 升級 UserDictionary.kt

**檔案：** `input/dictionary/UserDictionary.kt`

現有的 `UserDictionary` 只存詞（word），沒有存對應注音（bopomofo），無法讓用戶手動新增「迷客夏 → ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ」。

**完整替換 UserDictionary.kt 內容：**

```kotlin
package com.snowrealm.yukiboard.input.dictionary

import android.content.Context
import android.content.SharedPreferences

/**
 * 使用者自訂詞庫。
 *
 * 支援兩種模式：
 * 1. 自動學習：使用者輸入提交後自動記錄（僅記詞，無注音）
 * 2. 手動新增：使用者在設定頁手動加詞 + 注音（供注音輸入查詢）
 *
 * 儲存格式：
 * - word_頻率：word → frequency（自動學習）
 * - manual_詞：word → bopomofo（手動新增，有注音）
 */
class UserDictionary(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_user_dict", Context.MODE_PRIVATE)

    // 自動學習詞庫：word → frequency
    private val learnedWords = mutableMapOf<String, Int>()

    // 手動新增詞庫：word → bopomofo
    private val manualWords = mutableMapOf<String, String>()

    // 反向索引：bopomofo → List<word>（供注音查詢）
    private val bopomofIndex = mutableMapOf<String, MutableList<String>>()

    init {
        loadAll()
    }

    // ─────────────────────────────────────────
    // 自動學習（原有功能，保持相容）
    // ─────────────────────────────────────────

    /** 自動學習：使用者輸入提交後呼叫 */
    fun learn(word: String) {
        if (word.length < 2) return
        val lower = word.lowercase()
        val freq = (learnedWords[lower] ?: 0) + 1
        learnedWords[lower] = freq
        prefs.edit().putInt("word_$lower", freq).apply()
        updateLearnedWordList()
    }

    /** 移除自動學習的詞 */
    fun forget(word: String) {
        val lower = word.lowercase()
        learnedWords.remove(lower)
        prefs.edit().remove("word_$lower").apply()
        updateLearnedWordList()
    }

    /** 前綴匹配查詢（英文自動補字用） */
    fun suggest(prefix: String, maxResults: Int = 5): List<String> {
        if (prefix.length < 2) return emptyList()
        val lower = prefix.lowercase()
        return learnedWords.entries
            .filter { it.key.startsWith(lower) }
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }

    /** 取得所有自動學習詞彙（按頻率排序） */
    fun getAll(): List<Pair<String, Int>> =
        learnedWords.entries.sortedByDescending { it.value }.map { it.key to it.value }

    fun contains(word: String): Boolean = learnedWords.containsKey(word.lowercase())

    val size: Int get() = learnedWords.size

    // ─────────────────────────────────────────
    // 手動新增詞彙（新功能）
    // ─────────────────────────────────────────

    /**
     * 手動新增詞彙（帶注音）。
     * 例如：addManual("迷客夏", "ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ")
     */
    fun addManual(word: String, bopomofo: String) {
        if (word.isBlank() || bopomofo.isBlank()) return
        manualWords[word] = bopomofo
        // 更新反向索引
        bopomofIndex.getOrPut(bopomofo) { mutableListOf() }.let {
            if (word !in it) it.add(word)
        }
        saveManualWords()
    }

    /**
     * 刪除手動新增詞彙。
     */
    fun removeManual(word: String) {
        val bopomofo = manualWords.remove(word) ?: return
        bopomofIndex[bopomofo]?.remove(word)
        if (bopomofIndex[bopomofo]?.isEmpty() == true) {
            bopomofIndex.remove(bopomofo)
        }
        saveManualWords()
    }

    /**
     * 根據注音查詢手動新增的詞彙。
     * 支援精確匹配和無聲調模糊匹配。
     *
     * @param bopomofo 注音字串，例如 "ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ"
     */
    fun queryByBopomofo(bopomofo: String): List<String> {
        val results = mutableListOf<String>()

        // 精確匹配
        bopomofIndex[bopomofo]?.let { results.addAll(it) }

        // 無聲調模糊匹配
        if (results.isEmpty()) {
            val stripped = bopomofo.replace(Regex("[ˉˊˇˋ˙]"), "").trim()
            for ((bopo, words) in bopomofIndex) {
                val bStripped = bopo.replace(Regex("[ˉˊˇˋ˙]"), "").trim()
                if (bStripped == stripped) results.addAll(words)
            }
        }

        return results.distinct()
    }

    /** 取得所有手動新增詞彙（word → bopomofo） */
    fun getAllManual(): List<Pair<String, String>> =
        manualWords.entries.map { it.key to it.value }

    /** 清空全部 */
    fun clear() {
        learnedWords.clear()
        manualWords.clear()
        bopomofIndex.clear()
        prefs.edit().clear().apply()
    }

    // ─────────────────────────────────────────
    // 私有方法
    // ─────────────────────────────────────────

    private fun loadAll() {
        // 載入自動學習詞
        learnedWords.clear()
        val wordList = prefs.getString("_word_list", null)?.split(",") ?: emptyList()
        for (word in wordList) {
            if (word.isBlank()) continue
            val freq = prefs.getInt("word_$word", 0)
            if (freq > 0) learnedWords[word] = freq
        }

        // 載入手動新增詞
        manualWords.clear()
        bopomofIndex.clear()
        val manualList = prefs.getString("_manual_list", null)?.split("||") ?: emptyList()
        for (entry in manualList) {
            if (entry.isBlank()) continue
            val parts = entry.split("\t")
            if (parts.size == 2) {
                val word = parts[0]
                val bopo = parts[1]
                manualWords[word] = bopo
                bopomofIndex.getOrPut(bopo) { mutableListOf() }.add(word)
            }
        }
    }

    private fun updateLearnedWordList() {
        prefs.edit().putString("_word_list", learnedWords.keys.joinToString(",")).apply()
    }

    private fun saveManualWords() {
        val encoded = manualWords.entries.joinToString("||") { "${it.key}\t${it.value}" }
        prefs.edit().putString("_manual_list", encoded).apply()
    }
}
```

### 2-B 更新 ChewingInputEngine 整合 UserDictionary

**檔案：** `input/engine/ChewingInputEngine.kt`

**目標：** `lookupCandidates()` 和 `lookupMultiSyllable()` 查詢時，加入 UserDictionary 的手動詞彙。

**步驟一：** 在 ChewingInputEngine 的 constructor 加入 UserDictionary 參數：

```kotlin
// 原本
class ChewingInputEngine(private val context: Context) : InputEngine {

// 改為
class ChewingInputEngine(
    private val context: Context,
    private val userDict: com.snowrealm.yukiboard.input.dictionary.UserDictionary? = null
) : InputEngine {
```

**步驟二：** 在 `lookupMultiSyllable()` 的第 1 步（聲母簡拼查詢）之前，先查 UserDictionary：

```kotlin
private fun lookupMultiSyllable(syllables: List<String>): List<String> {
    val results = linkedSetOf<String>()

    // ── 0. 先查 UserDictionary 手動詞彙（最優先）──
    val fullBopomofo = syllables.joinToString(" ")
    userDict?.queryByBopomofo(fullBopomofo)?.let {
        results.addAll(it)
    }
    // 無聲調版也查一次
    val strippedBopo = syllables.joinToString(" ") { it.trimEnd { c -> c in toneMarks } }
    if (strippedBopo != fullBopomofo) {
        userDict?.queryByBopomofo(strippedBopo)?.let { results.addAll(it) }
    }

    // ── 1. 聲母簡拼查詢 ──（以下原本的邏輯不變）
    ...
```

**步驟三：** 確認 InputRouter 建立 ChewingInputEngine 時有傳入 userDict：

找到 `InputRouter.kt` 建立 ChewingInputEngine 的地方：
```kotlin
// 改為傳入 userDict
private val chewingEngine = ChewingInputEngine(
    context,
    userDict = UserDictionary(context)
)
```

### 2-C 新增自訂詞彙 UI

**檔案：** `ui/settings/SettingsActivity.kt`

在 `buildContent()` 裡，找到「輸入行為」卡片區塊（或加在最後），新增「自訂詞彙」卡片：

```kotlin
scrollView.addView(buildUserDictCard())
```

新增方法 `buildUserDictCard()`：

```kotlin
private fun buildUserDictCard(): LinearLayout {
    val card = simpleCard("自訂詞彙")
    val userDict = com.snowrealm.yukiboard.input.dictionary.UserDictionary(this)

    // 新增按鈕
    val addBtn = Button(this).apply {
        text = "+ 新增詞彙"
        setTextColor(0xFF7FD8FF.toInt())
        setBackgroundColor(0xFF1A3A5C.toInt())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(8)) }
    }

    // 詞彙列表容器
    val listContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun refreshList() {
        listContainer.removeAllViews()
        val manualWords = userDict.getAllManual()
        if (manualWords.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "尚無自訂詞彙"
                setTextColor(0x88FFFFFF.toInt())
                textSize = 13f
                setPadding(dp(4), dp(4), dp(4), dp(4))
            })
        } else {
            for ((word, bopo) in manualWords) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                }
                row.addView(TextView(this).apply {
                    text = "$word  $bopo"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                val delBtn = TextView(this).apply {
                    text = "✕"
                    setTextColor(0xFFFF6B6B.toInt())
                    textSize = 16f
                    setPadding(dp(8), 0, dp(4), 0)
                }
                delBtn.setOnClickListener {
                    userDict.removeManual(word)
                    refreshList()
                }
                row.addView(delBtn)
                listContainer.addView(row)
            }
        }
    }

    addBtn.setOnClickListener {
        // 彈出新增對話框
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        val wordInput = android.widget.EditText(this).apply {
            hint = "詞彙（例如：迷客夏）"
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1A3A5C.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val bopoInput = android.widget.EditText(this).apply {
            hint = "注音（例如：ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ）"
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1A3A5C.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        val hint = TextView(this).apply {
            text = "注音各音節間用空格分隔"
            setTextColor(0x88FFFFFF.toInt())
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        dialogView.addView(wordInput)
        dialogView.addView(bopoInput)
        dialogView.addView(hint)

        android.app.AlertDialog.Builder(this)
            .setTitle("新增自訂詞彙")
            .setView(dialogView)
            .setPositiveButton("新增") { _, _ ->
                val word = wordInput.text.toString().trim()
                val bopo = bopoInput.text.toString().trim()
                if (word.isNotEmpty() && bopo.isNotEmpty()) {
                    userDict.addManual(word, bopo)
                    refreshList()
                    android.widget.Toast.makeText(this, "已新增「$word」", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(this, "詞彙和注音不能空白", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    card.addView(addBtn)
    card.addView(listContainer)
    refreshList()
    return card
}
```

---

## 執行順序

```
第一批：修改 ChewingInputEngine.lookupMultiSyllable() fallback
  → 確認打多音節注音時每個音節的字都有顯示

第二批 2-A：替換 UserDictionary.kt（加注音欄位）
第二批 2-B：更新 ChewingInputEngine constructor 接收 userDict
            更新 lookupMultiSyllable 第 0 步查 userDict
            更新 InputRouter 建立 ChewingInputEngine 時傳入 userDict
第二批 2-C：SettingsActivity 加 buildUserDictCard()

Build 驗證：確認 BUILD SUCCESSFUL
```

---

## 效果驗證

完成後測試：

1. 打 `ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ` → 候選列出現「迷、謎」+「客、刻」+「夏、下」
2. 到設定頁「自訂詞彙」→ 點「+ 新增詞彙」→ 輸入「迷客夏」+「ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ」→ 新增
3. 再打 `ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ` → 「迷客夏」排在候選列最前面

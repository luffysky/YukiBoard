package com.snowrealm.yukiboard.input.dictionary

import android.content.Context
import android.content.SharedPreferences

/**
 * 使用者自訂詞庫。
 *
 * 支援兩種模式：
 * 1. 自動學習：使用者輸入提交後自動記錄（僅記詞，無注音）
 * 2. 手動新增：使用者在設定頁手動加詞 + 注音 + 分類（供注音輸入查詢）
 *
 * 儲存格式：
 * - word_頻率：word → frequency（自動學習）
 * - manual_詞：word|bopomofo|category（手動新增，有注音 + 分類）
 *
 * 分類：一般, 人名, 地名, 專有名詞, 工作用語, 網路用語, 自訂
 *
 * ### S8-2：Shared-state / reload 策略（2026-04-18）
 *
 * 歷史問題：InputRouter / USER_DICT panel / SettingsActivity / CloudSyncManager 各自
 * `new UserDictionary(context)`，且類別只在 init 時 `loadAll()`，任一 instance 寫入
 * 不會反映到其他 instance 的記憶體 map，導致「匯入成功但輸入建議沒立刻更新」等不一致。
 *
 * 修補：所有 mutator 改走 [editWithVersionBump]，在同一個 `prefs.edit()` 交易內
 * 連同 `_dict_version` 一起 put，保證版本 bump 與資料寫入同批 apply。
 * `_dict_version` 採嚴格單調遞增 sequence（`max(currentPrefVersion, lastKnownVersion) + 1`），
 * 不使用時間戳，避免同毫秒連寫（例如 `updateManual` 的 remove→add）產生相同版本號
 * 讓其他 instance 第二次跳過 reload 的 race。
 * 每個 instance 在 init 時註冊 [OnSharedPreferenceChangeListener] 監聽 `_dict_version`：
 * 偵測到版本與自己記錄的不同 → 呼叫 `loadAll()` 重新載入 in-memory map。
 *
 * 此設計不引入 singleton / `getInstance()`（符合 CLAUDE.md 邊界規範），
 * callsite 端不需改動；任何長生命週期 instance（如 InputRouter 持有的）可選擇呼叫
 * [release] 在拆解時解除 listener，避免不必要的 WeakHashMap entry 保留。
 */
class UserDictionary(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_user_dict", Context.MODE_PRIVATE)

    // 自動學習詞庫：word → frequency
    private val learnedWords = mutableMapOf<String, Int>()

    // 手動新增詞庫：word → ManualEntry(bopomofo, category)
    data class ManualEntry(val bopomofo: String, val category: String = DEFAULT_CATEGORY)

    private val manualWords = mutableMapOf<String, ManualEntry>()

    // 反向索引：bopomofo → List<word>（供注音查詢）
    private val bopomofIndex = mutableMapOf<String, MutableList<String>>()

    // S8-2：本 instance 最後一次已知的 _dict_version，用來區分「自己寫的」與「別人寫的」
    @Volatile
    private var lastKnownVersion: Long = 0L

    // S8-2：_dict_version 變更 listener，跨 instance reload 入口
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == VERSION_KEY) {
            val newVersion = prefs.getLong(VERSION_KEY, 0L)
            if (newVersion != lastKnownVersion) {
                lastKnownVersion = newVersion
                loadAll()
            }
        }
    }

    companion object {
        const val DEFAULT_CATEGORY = "一般"
        val BUILT_IN_CATEGORIES = listOf("一般", "人名", "地名", "專有名詞", "工作用語", "網路用語", "自訂")
        private const val VERSION_KEY = "_dict_version"

        /**
         * S8-2 v3：process-wide 版本號產生鎖，保證跨 instance 的 `editWithVersionBump`
         * 序列化執行，任兩次寫入的版本號嚴格唯一且遞增。
         *
         * v2 僅靠 `max(currentPrefVersion, lastKnownVersion) + 1` 無法處理真正的跨
         * instance 並發寫：兩個 instance 若同時進入寫入路徑，都可能讀到相同 prefs 值
         * 並各自算出同一個 `N+1`，導致其他 observer 第二次 listener 因重號跳過 reload。
         *
         * v3 把版本號計算 + prefs 讀取整段納入 [versionLock] 保護的 critical section。
         * YukiBoard 為單 process app（IME service / Activity / CloudSync 同 process），
         * process-wide lock 即涵蓋所有實際 callsite。
         */
        private val versionLock = Any()
    }

    init {
        loadAll()
        lastKnownVersion = prefs.getLong(VERSION_KEY, 0L)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
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
        editWithVersionBump {
            putInt("word_$lower", freq)
            putString("_word_list", learnedWords.keys.joinToString(","))
        }
    }

    /** 移除自動學習的詞 */
    fun forget(word: String) {
        val lower = word.lowercase()
        learnedWords.remove(lower)
        editWithVersionBump {
            remove("word_$lower")
            putString("_word_list", learnedWords.keys.joinToString(","))
        }
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
     * 手動新增詞彙（帶注音 + 分類）。
     * 例如：addManual("迷客夏", "ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ", "專有名詞")
     */
    fun addManual(word: String, bopomofo: String, category: String = DEFAULT_CATEGORY) {
        if (word.isBlank() || bopomofo.isBlank()) return
        manualWords[word] = ManualEntry(bopomofo, category)
        // 更新反向索引
        bopomofIndex.getOrPut(bopomofo) { mutableListOf() }.let {
            if (word !in it) it.add(word)
        }
        writeManualWords()
    }

    /**
     * 刪除手動新增詞彙。
     */
    fun removeManual(word: String) {
        val entry = manualWords.remove(word) ?: return
        bopomofIndex[entry.bopomofo]?.remove(word)
        if (bopomofIndex[entry.bopomofo]?.isEmpty() == true) {
            bopomofIndex.remove(entry.bopomofo)
        }
        writeManualWords()
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

    /** 取得所有手動新增詞彙（word, bopomofo, category） */
    fun getAllManual(): List<Pair<String, String>> =
        manualWords.entries.map { it.key to it.value.bopomofo }

    /** 取得所有手動新增詞彙（含分類） */
    fun getAllManualWithCategory(): List<Triple<String, String, String>> =
        manualWords.entries.map { Triple(it.key, it.value.bopomofo, it.value.category) }

    /** 依分類查詢 */
    fun queryByCategory(category: String): List<Triple<String, String, String>> {
        if (category == "全部") return getAllManualWithCategory()
        return manualWords.entries
            .filter { it.value.category == category }
            .map { Triple(it.key, it.value.bopomofo, it.value.category) }
    }

    /** 取得所有使用中的分類（含內建） */
    fun getAllCategories(): List<String> {
        val used = manualWords.values.map { it.category }.toSet()
        return (BUILT_IN_CATEGORIES + used).distinct()
    }

    /**
     * 更新手動新增詞彙。
     * @param oldWord 原本的詞
     * @param newWord 新的詞
     * @param newBopomofo 新的注音
     * @param newCategory 新的分類
     */
    fun updateManual(oldWord: String, newWord: String, newBopomofo: String, newCategory: String = DEFAULT_CATEGORY) {
        removeManual(oldWord)
        addManual(newWord, newBopomofo, newCategory)
    }

    /**
     * 匯出手動詞庫為 TSV 格式字串（word\tbopomofo\tcategory 每行一筆）。
     */
    fun exportToString(): String =
        manualWords.entries.joinToString("\n") { "${it.key}\t${it.value.bopomofo}\t${it.value.category}" }

    /**
     * 從 TSV 格式字串匯入手動詞彙。
     * 支援舊格式 (word\tbopomofo) 及新格式 (word\tbopomofo\tcategory)。
     * @return 成功匯入的筆數
     *
     * S8-2：批次匯入改為 in-memory 先全量更新，最後一次 writeManualWords 收斂一次版本 bump，
     * 避免匯入 N 筆觸發 N 次 listener reload。
     */
    fun importFromString(data: String): Int {
        var count = 0
        for (line in data.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val parts = trimmed.split("\t")
            if (parts.size >= 2) {
                val word = parts[0].trim()
                val bopo = parts[1].trim()
                val cat = if (parts.size >= 3) parts[2].trim() else DEFAULT_CATEGORY
                if (word.isNotBlank() && bopo.isNotBlank()) {
                    manualWords[word] = ManualEntry(bopo, cat)
                    bopomofIndex.getOrPut(bopo) { mutableListOf() }.let {
                        if (word !in it) it.add(word)
                    }
                    count++
                }
            }
        }
        if (count > 0) writeManualWords()
        return count
    }

    /** 清空全部 */
    fun clear() {
        learnedWords.clear()
        manualWords.clear()
        bopomofIndex.clear()
        // editor.clear() 會把 _dict_version 也清掉，這裡靠 editWithVersionBump 的 putLong 再寫回新版本
        editWithVersionBump { clear() }
    }

    // ─────────────────────────────────────────
    // S8-2：對外 reload / release 入口
    // ─────────────────────────────────────────

    /**
     * 手動觸發重新載入 in-memory map（例如 callsite 明確知道背景變動時）。
     * 一般情況不需要呼叫，prefsListener 會在 version bump 時自動觸發。
     */
    fun reload() {
        loadAll()
        lastKnownVersion = prefs.getLong(VERSION_KEY, 0L)
    }

    /**
     * 釋放 SharedPreferences listener。長生命週期 instance（如 InputRouter 持有的）
     * 可在拆解時呼叫，避免 WeakHashMap entry 保留到 GC 才清掉。
     */
    fun release() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
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

        // 載入手動新增詞（支援新格式 word\tbopo\tcategory 和舊格式 word\tbopo）
        manualWords.clear()
        bopomofIndex.clear()
        val manualList = prefs.getString("_manual_list", null)?.split("||") ?: emptyList()
        for (entry in manualList) {
            if (entry.isBlank()) continue
            val parts = entry.split("\t")
            if (parts.size >= 2) {
                val word = parts[0]
                val bopo = parts[1]
                val cat = if (parts.size >= 3) parts[2] else DEFAULT_CATEGORY
                manualWords[word] = ManualEntry(bopo, cat)
                bopomofIndex.getOrPut(bopo) { mutableListOf() }.add(word)
            }
        }
    }

    private fun writeManualWords() {
        val encoded = manualWords.entries.joinToString("||") { "${it.key}\t${it.value.bopomofo}\t${it.value.category}" }
        editWithVersionBump {
            putString("_manual_list", encoded)
        }
    }

    /**
     * S8-2：集中所有 prefs 寫入管道，保證資料寫入與 _dict_version bump 在同一個
     * apply() 交易中發佈，避免其他 instance 在版本 bump 前先看到部分資料。
     * 同步更新 [lastKnownVersion]，讓 listener 知道「這是自己寫的」不重複 reload。
     *
     * 版本號策略：v3 — 進入 [versionLock] critical section 內取
     * `max(currentPrefVersion, lastKnownVersion) + 1`，保證跨 instance 並發寫入
     * 時仍嚴格唯一且遞增。lock 內同步完成 `prefs.getLong` 讀取、版本號計算、
     * `lastKnownVersion` 更新、editor 建立與 `apply()`，杜絕兩個 instance 讀到
     * 相同 currentPrefVersion 同時算出 N+1 的 race。
     */
    private fun editWithVersionBump(block: SharedPreferences.Editor.() -> Unit) {
        synchronized(versionLock) {
            val currentPrefVersion = prefs.getLong(VERSION_KEY, 0L)
            val version = maxOf(currentPrefVersion, lastKnownVersion) + 1
            lastKnownVersion = version
            val editor = prefs.edit()
            editor.block()
            editor.putLong(VERSION_KEY, version)
            editor.apply()
        }
    }
}

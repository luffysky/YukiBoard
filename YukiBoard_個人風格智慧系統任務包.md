# YukiBoard 個人風格智慧系統 — 產品規格 + 任務包

> 融合「個人詞庫」、「語氣學習」、「隱形遊戲化」、「情緒貼圖」四個系統
> 完全隱形：使用者「不知道在玩」，但會上癮
> 基於現有 TypingHabitTracker + PhrasePredictor + AIPredictionSkill 擴充

---

## 整體架構概念

```
使用者打字
    ↓
StyleEngine（核心）
    ├── PersonalDictionary（個人詞庫）← 自動學 + 手動編輯
    ├── StyleProfiler（語氣學習）← 記錄用詞/句型/語氣
    ├── ContextAwarePredictor（對象感知）← 不同人不同風格
    └── EmotionStickerEngine（情緒貼圖）← 自動推薦

輸出到鍵盤 UI（完全隱形）：
    ├── 候選字列：詞組預測 + 雪凜補句（淡紫色）
    ├── 貼圖浮出：輸入「好累」自動彈出3張貼圖
    └── 隱形積分：命中預測 → 連擊動畫（不顯示分數）
```

---

## 第一批 — PersonalDictionary（個人詞庫核心）

### 說明

現有 `UserDictionary` 只有英文前綴匹配，`TypingHabitTracker` 只記詞頻但沒有 AI 判斷。
需要一個新的 `PersonalDictionary`，整合兩者並加入 AI 自動判斷邏輯。

### 新增 `input/smart/PersonalDictionary.kt`

```kotlin
package com.snowrealm.yukiboard.input.smart

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 個人詞庫 — 自動學習 + 手動編輯。
 *
 * AI 判斷邏輯：
 * - 7天內使用 >= 3 次 → 自動進入「常用詞」
 * - 30天未使用 → 自動降級
 * - 使用者可手動釘選（不會降級）或刪除
 *
 * 三層結構：
 * 1. pinned：使用者手動釘選，永久置頂
 * 2. learned：AI 自動判斷常用，出現在候選列
 * 3. candidate：觀察中，尚未升為常用
 */
class PersonalDictionary(private val context: Context) {

    data class PersonalWord(
        val word: String,
        val bopomofo: String = "",       // 注音（手動新增時填）
        val frequency: Int = 0,          // 總使用次數
        val recentCount: Int = 0,        // 7天內使用次數
        val lastUsedMs: Long = 0,        // 最後使用時間
        val isPinned: Boolean = false,   // 手動釘選
        val addedMs: Long = System.currentTimeMillis()
    )

    enum class WordTier { PINNED, LEARNED, CANDIDATE }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_personal_dict", Context.MODE_PRIVATE)

    private val words = mutableMapOf<String, PersonalWord>()

    init { load() }

    // ── 自動學習 ──────────────────────────────────────────

    /**
     * 記錄使用者提交了某個詞。
     * 每次提交都更新頻率，AI 判斷邏輯在 [getTier] 裡。
     */
    fun onWordCommitted(word: String, bopomofo: String = "") {
        if (word.length < 2) return
        val now = System.currentTimeMillis()
        val existing = words[word]
        words[word] = PersonalWord(
            word = word,
            bopomofo = bopomofo.ifEmpty { existing?.bopomofo ?: "" },
            frequency = (existing?.frequency ?: 0) + 1,
            recentCount = calcRecentCount(existing, now),
            lastUsedMs = now,
            isPinned = existing?.isPinned ?: false,
            addedMs = existing?.addedMs ?: now
        )
        saveAsync()
    }

    /**
     * AI 判斷：這個詞屬於哪一層？
     * - PINNED：使用者手動釘選
     * - LEARNED：7天內用 >= 3次，或總頻率 >= 10
     * - CANDIDATE：觀察中
     */
    fun getTier(word: String): WordTier {
        val w = words[word] ?: return WordTier.CANDIDATE
        return when {
            w.isPinned -> WordTier.PINNED
            w.recentCount >= 3 || w.frequency >= 10 -> WordTier.LEARNED
            else -> WordTier.CANDIDATE
        }
    }

    /** 取得所有「常用詞」（PINNED + LEARNED），供設定頁顯示 */
    fun getPersonalWords(): List<PersonalWord> {
        cleanStale()
        return words.values
            .filter { getTier(it.word) != WordTier.CANDIDATE }
            .sortedWith(compareByDescending<PersonalWord> { it.isPinned }
                .thenByDescending { it.recentCount }
                .thenByDescending { it.frequency })
    }

    /** 取得「觀察中」的詞（給設定頁「潛力詞」區塊） */
    fun getCandidateWords(): List<PersonalWord> =
        words.values.filter { getTier(it.word) == WordTier.CANDIDATE }
            .sortedByDescending { it.recentCount }
            .take(20)

    /** 根據前綴建議個人化詞彙 */
    fun suggest(prefix: String, limit: Int = 5): List<String> {
        if (prefix.isEmpty()) return getPersonalWords().take(limit).map { it.word }
        return words.values
            .filter { it.word.startsWith(prefix) && getTier(it.word) != WordTier.CANDIDATE }
            .sortedWith(compareByDescending<PersonalWord> { it.isPinned }
                .thenByDescending { it.recentCount })
            .take(limit)
            .map { it.word }
    }

    /** 根據注音查詢個人詞彙（給注音輸入法用） */
    fun queryByBopomofo(bopomofo: String): List<String> {
        val stripped = bopomofo.replace(Regex("[ˉˊˇˋ˙]"), "").trim()
        return words.values
            .filter { w ->
                if (w.bopomofo.isEmpty()) return@filter false
                val wStripped = w.bopomofo.replace(Regex("[ˉˊˇˋ˙]"), "").trim()
                wStripped == stripped || w.bopomofo == bopomofo
            }
            .filter { getTier(it.word) != WordTier.CANDIDATE }
            .sortedByDescending { it.frequency }
            .map { it.word }
    }

    // ── 手動編輯 ──────────────────────────────────────────

    /** 手動新增（帶注音），直接進入 PINNED */
    fun addManual(word: String, bopomofo: String = "") {
        val now = System.currentTimeMillis()
        words[word] = PersonalWord(
            word = word,
            bopomofo = bopomofo,
            frequency = words[word]?.frequency ?: 1,
            recentCount = 3,  // 直接達到 LEARNED 門檻
            lastUsedMs = now,
            isPinned = true,
            addedMs = now
        )
        saveAsync()
    }

    /** 釘選/取消釘選 */
    fun setPinned(word: String, pinned: Boolean) {
        words[word]?.let { words[word] = it.copy(isPinned = pinned) }
        saveAsync()
    }

    /** 刪除 */
    fun remove(word: String) {
        words.remove(word)
        saveAsync()
    }

    // ── 內部 ──────────────────────────────────────────────

    private fun calcRecentCount(existing: PersonalWord?, now: Long): Int {
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        if (existing == null) return 1
        return if (now - existing.lastUsedMs < sevenDays) {
            existing.recentCount + 1
        } else {
            1  // 超過7天重新計算
        }
    }

    private fun cleanStale() {
        val thirtyDays = 30L * 24 * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        words.entries.removeIf { (_, w) ->
            !w.isPinned &&
            now - w.lastUsedMs > thirtyDays &&
            w.frequency < 10
        }
    }

    private fun saveAsync() {
        val arr = JSONArray()
        words.values.forEach { w ->
            arr.put(JSONObject().apply {
                put("word", w.word)
                put("bopo", w.bopomofo)
                put("freq", w.frequency)
                put("recent", w.recentCount)
                put("last", w.lastUsedMs)
                put("pinned", w.isPinned)
                put("added", w.addedMs)
            })
        }
        prefs.edit().putString("words_v2", arr.toString()).apply()
    }

    private fun load() {
        val json = prefs.getString("words_v2", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val w = PersonalWord(
                    word = obj.getString("word"),
                    bopomofo = obj.optString("bopo", ""),
                    frequency = obj.optInt("freq", 0),
                    recentCount = obj.optInt("recent", 0),
                    lastUsedMs = obj.optLong("last", 0),
                    isPinned = obj.optBoolean("pinned", false),
                    addedMs = obj.optLong("added", 0)
                )
                words[w.word] = w
            }
        } catch (_: Exception) {}
    }
}
```

### 整合到現有 SmartInputManager

在 `input/smart/SmartInputManager.kt` 加入：

```kotlin
// 在 constructor 加
val personalDict = PersonalDictionary(context)

// 在 onWordCommitted() 裡加
personalDict.onWordCommitted(word, bopomofo)

// 在 getCandidates() 裡，候選字列最前面加個人詞彙
val personal = personalDict.suggest(prefix, 3)
// personal 排最前面
```

---

## 第二批 — StyleProfiler（語氣學習，7天開始像你）

### 新增 `input/smart/StyleProfiler.kt`

```kotlin
package com.snowrealm.yukiboard.input.smart

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * 語氣風格學習器。
 *
 * 記錄：
 * - 常用句型結尾（哈哈、喔、啊、喲、啦、囉...）
 * - 語氣偏向（輕鬆/認真/搞笑/溫柔）
 * - 對不同 App 的語氣偏好
 * - 常用詞組（2-4字短語）
 *
 * 供 AI 補句使用：讓雪凜的補句「像你講的話」
 */
class StyleProfiler(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_style", Context.MODE_PRIVATE)

    // 句尾語氣詞頻
    private val endingParticles = mutableMapOf<String, Int>()
    // 常用短語（2-4字）
    private val shortPhrases = mutableMapOf<String, Int>()
    // App → 語氣偏向分數（-10輕鬆 ~ +10正式）
    private val appTone = mutableMapOf<String, Float>()
    // 提交次數（7天統計用）
    private var totalCommits7d = 0

    // 語氣指標的語氣詞
    private val casualMarkers = setOf("哈哈","哈","嗯","啊","喔","唉","欸","呦","喲","喔喔","ㄟ","欸欸")
    private val formalMarkers = setOf("您好","請問","謝謝","感謝","敬請","煩請","祝您","敬祝")

    init { load() }

    /** 記錄一句話（commit 時呼叫） */
    fun onSentenceCommitted(sentence: String, appPackage: String? = null) {
        // 抓句尾語氣詞
        val trimmed = sentence.trim()
        for (particle in casualMarkers + formalMarkers) {
            if (trimmed.endsWith(particle)) {
                endingParticles[particle] = (endingParticles[particle] ?: 0) + 1
            }
        }

        // 抓 2-4 字短語
        if (trimmed.length in 2..4) {
            shortPhrases[trimmed] = (shortPhrases[trimmed] ?: 0) + 1
        }

        // 更新 App 語氣分數
        if (appPackage != null) {
            val isCasual = casualMarkers.any { trimmed.contains(it) }
            val isFormal = formalMarkers.any { trimmed.contains(it) }
            val delta = when {
                isCasual -> -0.3f
                isFormal -> +0.5f
                else -> 0f
            }
            appTone[appPackage] = ((appTone[appPackage] ?: 0f) + delta).coerceIn(-10f, 10f)
        }

        totalCommits7d++
        saveAsync()
    }

    /**
     * 取得語氣描述（供 AI Prompt 使用）。
     * 例如：「偏輕鬆，愛用哈哈、喔，常用短句」
     */
    fun getStyleDescription(appPackage: String? = null): String {
        val topParticles = endingParticles.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        val toneScore = appPackage?.let { appTone[it] } ?: appTone.values.average().toFloat()
        val toneDesc = when {
            toneScore < -3f -> "很輕鬆隨意"
            toneScore < 0f -> "偏輕鬆"
            toneScore < 3f -> "中性"
            else -> "偏正式"
        }

        val topPhrases = shortPhrases.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        return buildString {
            append("語氣：$toneDesc")
            if (topParticles.isNotEmpty()) append("，愛用「${topParticles.joinToString("」「")}」")
            if (topPhrases.isNotEmpty()) append("，常用短語「${topPhrases.joinToString("」「")}」")
        }
    }

    /** 對某 App 偏正式還是輕鬆（-1~+1） */
    fun getToneForApp(appPackage: String): Float =
        (appTone[appPackage] ?: 0f).coerceIn(-10f, 10f) / 10f

    /** 資料量夠了嗎（7天內至少50次提交才開始影響補字） */
    fun isProfileReady(): Boolean = totalCommits7d >= 50

    private fun saveAsync() {
        val obj = JSONObject().apply {
            put("commits7d", totalCommits7d)
            val ep = JSONObject(); endingParticles.forEach { (k, v) -> ep.put(k, v) }
            put("endingParticles", ep)
            val sp = JSONObject(); shortPhrases.forEach { (k, v) -> sp.put(k, v) }
            put("shortPhrases", sp)
            val at = JSONObject(); appTone.forEach { (k, v) -> at.put(k, v) }
            put("appTone", at)
        }
        prefs.edit().putString("style_v1", obj.toString()).apply()
    }

    private fun load() {
        val json = prefs.getString("style_v1", null) ?: return
        try {
            val obj = JSONObject(json)
            totalCommits7d = obj.optInt("commits7d", 0)
            val ep = obj.optJSONObject("endingParticles")
            ep?.keys()?.forEach { k -> endingParticles[k] = ep.getInt(k) }
            val sp = obj.optJSONObject("shortPhrases")
            sp?.keys()?.forEach { k -> shortPhrases[k] = sp.getInt(k) }
            val at = obj.optJSONObject("appTone")
            at?.keys()?.forEach { k -> appTone[k] = at.getDouble(k).toFloat() }
        } catch (_: Exception) {}
    }
}
```

### 升級 AIPredictionSkill 融入語氣學習

更新 `claw/skills/AIPredictionSkill.kt`：

```kotlin
class AIPredictionSkill(
    private val provider: AIProvider,
    private val styleProfiler: StyleProfiler? = null   // 新增
) : ClawSkill {
    override val id = "ai_prediction"
    override val name = "AI 補字"
    override val description = "智慧預測下一個詞"
    override val tokensPerCall = 200

    override suspend fun execute(input: ClawInput): ClawOutput {
        // 取得語氣描述
        val styleHint = if (styleProfiler?.isProfileReady() == true) {
            "\n使用者語氣風格：${styleProfiler.getStyleDescription(input.appPackage)}"
        } else ""

        val prompt = buildString {
            append("根據以下輸入，預測最可能的 5 個後續詞或短語。")
            append("只輸出詞語，每行一個，不要編號不要解釋。")
            if (styleHint.isNotEmpty()) append(styleHint)
            append("\n\n輸入：${input.text}")
            append("\n上下文：${input.context}")
        }

        val systemPrompt = buildString {
            append("你是繁體中文輸入法的智慧預測引擎。")
            append("回應簡短精準，符合使用者的說話風格。")
        }

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        val suggestions = result.toString().trim().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(5)

        return ClawOutput(suggestions, OutputType.PREDICTION, tokensPerCall)
    }
}
```

---

## 第三批 — 隱形遊戲化（連擊系統）

### 新增 `input/smart/ComboTracker.kt`

```kotlin
package com.snowrealm.yukiboard.input.smart

/**
 * 隱形連擊追蹤器。
 *
 * 使用者「不知道在玩」，但內部記錄連擊數，
 * 達到一定連擊時觸發視覺回饋（粒子效果 / 雪凜動畫）。
 *
 * 連擊條件：
 * - 選中 AI 預測的詞 → +1 combo
 * - 語氣詞命中（用了自己常用的句尾） → +1 combo
 * - 連續流暢打字（無停頓 > 2秒） → 維持 combo
 * - 停頓 > 3秒 或 按刪除 → combo 重置
 */
class ComboTracker {

    var combo = 0
        private set
    private var lastEventMs = 0L

    /** 命中 AI 預測 → 加連擊 */
    fun onAIPredictionHit() = increment("ai")

    /** 命中個人常用詞 → 加連擊 */
    fun onPersonalWordHit() = increment("personal")

    /** 語氣詞命中 → 加連擊 */
    fun onStyleHit() = increment("style")

    /** 按了刪除 or 停頓太久 → 重置 */
    fun onBreak() {
        combo = 0
        lastEventMs = 0L
    }

    /** 取得當前連擊等級（供 UI 決定動畫強度） */
    fun getLevel(): ComboLevel = when {
        combo >= 20 -> ComboLevel.MAX
        combo >= 10 -> ComboLevel.HIGH
        combo >= 5  -> ComboLevel.MED
        combo >= 3  -> ComboLevel.LOW
        else        -> ComboLevel.NONE
    }

    enum class ComboLevel { NONE, LOW, MED, HIGH, MAX }

    // callback，達到特定連擊時觸發
    var onComboReached: ((combo: Int, level: ComboLevel) -> Unit)? = null

    private fun increment(type: String) {
        val now = System.currentTimeMillis()
        if (lastEventMs > 0 && now - lastEventMs > 3000) {
            combo = 0  // 超過3秒停頓重置
        }
        combo++
        lastEventMs = now
        val level = getLevel()
        if (level != ComboLevel.NONE) {
            onComboReached?.invoke(combo, level)
        }
    }
}
```

### 整合到 KeyboardView（連擊動畫）

在 `KeyboardView.kt` 加入：

```kotlin
// 連擊觸發時的視覺回饋
fun showComboEffect(level: ComboTracker.ComboLevel) {
    when (level) {
        ComboTracker.ComboLevel.LOW  -> showParticles(count = 3, color = 0xFFB39DDB.toInt())
        ComboTracker.ComboLevel.MED  -> showParticles(count = 6, color = 0xFF7C4DFF.toInt())
        ComboTracker.ComboLevel.HIGH -> {
            showParticles(count = 12, color = 0xFF7C4DFF.toInt())
            // 通知雪凜角色做開心動作
            onComboHighListener?.invoke()
        }
        ComboTracker.ComboLevel.MAX  -> {
            showParticles(count = 20, color = 0xFFFFD700.toInt())
            onComboMaxListener?.invoke()
        }
        else -> {}
    }
}

var onComboHighListener: (() -> Unit)? = null
var onComboMaxListener: (() -> Unit)? = null
```

---

## 第四批 — EmotionStickerEngine（情緒自動貼圖）

### 新增 `sticker/EmotionStickerEngine.kt`

```kotlin
package com.snowrealm.yukiboard.sticker

import android.content.Context
import android.content.SharedPreferences

/**
 * 情緒貼圖推薦引擎。
 *
 * 功能：
 * 1. 分析輸入框文字 → 判斷情緒 → 推薦對應貼圖
 * 2. 記錄使用者選了哪些貼圖 → 個人化排序
 * 3. 對象感知：對不同聊天對象推薦不同風格
 */
class EmotionStickerEngine(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("emotion_sticker", Context.MODE_PRIVATE)

    // 貼圖使用次數記錄（stickerId → count）
    private val usageCount = mutableMapOf<String, Int>()
    // 對象偏好（contactHash → List<stickerId>）
    private val contactPrefs = mutableMapOf<String, MutableList<String>>()

    init { load() }

    // ── 情緒偵測（關鍵字規則，輕量） ──────────────────────────

    /**
     * 分析輸入文字，回傳情緒標籤。
     * 不需要 AI，純關鍵字規則，0ms latency。
     */
    fun detectEmotion(text: String): Emotion {
        val t = text.trim()
        return when {
            // 大笑
            t.contains(Regex("哈哈|哈哈哈|LOL|lol|笑死|笑到|XD|xd|😂|🤣")) -> Emotion.LAUGH
            // 開心
            t.contains(Regex("開心|好棒|耶|讚|太好了|棒棒|好好喔|YA|ya|😊|🎉")) -> Emotion.HAPPY
            // 累/哀
            t.contains(Regex("好累|累死|累了|躺平|QQ|嗚嗚|哭|難過|慘了|😭|😢")) -> Emotion.SAD
            // 憤怒
            t.contains(Regex("幹|靠|煩死|超煩|爛透|討厭|怒|😤|🤬|傻眼")) -> Emotion.ANGRY
            // 愛心
            t.contains(Regex("愛你|love|喜歡你|❤|💕|🥰|好喜歡")) -> Emotion.LOVE
            // 驚訝
            t.contains(Regex("蛤|什麼|認真|傻眼|不會吧|😱|😳|震驚")) -> Emotion.SURPRISE
            // 沒事
            else -> Emotion.NEUTRAL
        }
    }

    enum class Emotion {
        NEUTRAL, LAUGH, HAPPY, SAD, ANGRY, LOVE, SURPRISE
    }

    /**
     * 根據情緒和對象，推薦最適合的3張貼圖 ID。
     *
     * @param emotion 偵測到的情緒
     * @param contactHash 聊天對象的 hash（隱私保護，不存明文）
     * @param allStickers 所有可用貼圖
     */
    fun recommend(
        emotion: Emotion,
        contactHash: String = "",
        allStickers: List<RemoteSticker>,
        limit: Int = 3
    ): List<RemoteSticker> {
        // 1. 先從對象偏好找
        val contactFavs = contactHash.takeIf { it.isNotEmpty() }
            ?.let { contactPrefs[it] }
            ?.mapNotNull { id -> allStickers.firstOrNull { it.id == id } }
            ?: emptyList()

        // 2. 再從個人使用頻率排序
        val sorted = allStickers.sortedByDescending { usageCount[it.id] ?: 0 }

        // 3. 合併：對象偏好優先，再補個人常用
        val result = (contactFavs + sorted).distinctBy { it.id }.take(limit)

        return result.ifEmpty {
            // fallback：回傳前3張
            allStickers.take(limit)
        }
    }

    /** 記錄使用者選了哪張貼圖（個人化學習） */
    fun onStickerUsed(stickerId: String, contactHash: String = "") {
        usageCount[stickerId] = (usageCount[stickerId] ?: 0) + 1

        if (contactHash.isNotEmpty()) {
            val list = contactPrefs.getOrPut(contactHash) { mutableListOf() }
            list.remove(stickerId)
            list.add(0, stickerId)  // 最近用的排最前
            if (list.size > 10) list.removeAt(list.lastIndex)
        }

        save()
    }

    private fun save() {
        val usage = usageCount.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit().putString("usage", usage).apply()
    }

    private fun load() {
        val usage = prefs.getString("usage", null) ?: return
        usage.split(",").forEach { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) usageCount[parts[0]] = parts[1].toIntOrNull() ?: 0
        }
    }
}
```

### 整合到 KeyboardView（貼圖自動浮出）

在 `KeyboardView.kt` 加入情緒貼圖浮出邏輯：

```kotlin
// 新增屬性
private var emotionStickerBar: View? = null
var onEmotionStickerDetected: ((emotion: EmotionStickerEngine.Emotion) -> Unit)? = null

// 當 composingText 更新後，或輸入框前文更新後，呼叫此方法
fun checkEmotionSticker(inputText: String) {
    val emotion = emotionEngine.detectEmotion(inputText)
    if (emotion != EmotionStickerEngine.Emotion.NEUTRAL) {
        onEmotionStickerDetected?.invoke(emotion)
    }
}
```

在 `YukiBoardIMEService.kt` 裡，當 `getTextBeforeCursor` 改變時觸發情緒偵測：

```kotlin
// 在 onKeyPress 後，每次文字改變都呼叫
keyboardView.checkEmotionSticker(
    ic.getTextBeforeCursor(30, 0)?.toString() ?: ""
)
```

### 升級 StickerPanelView（貼圖浮出 UI）

在 `ui/panel/StickerPanelView.kt` 加入情緒貼圖快速列：

```kotlin
/**
 * 顯示情緒推薦貼圖列（3張，在候選字列上方或候選字列右側）
 * 使用者點擊 → 直接送出，不用打開貼圖面板
 */
fun showEmotionBar(stickers: List<RemoteSticker>) {
    // 在鍵盤上方顯示3張貼圖縮圖
    // 每個縮圖點擊 → onStickerSelected?.invoke(sticker)
    // 5秒後自動消失
}

fun hideEmotionBar() { /* 隱藏情緒貼圖列 */ }
```

---

## 第五批 — 個人詞庫設定頁 UI

### 在 SettingsActivity 加入「個人詞庫」卡片

```kotlin
private fun buildPersonalDictCard(): LinearLayout {
    val card = simpleCard("個人詞庫 ✨")
    val personalDict = PersonalDictionary(this)

    // 分三區：常用詞、觀察中、手動新增

    // 1. 頂部說明
    card.addView(TextView(this).apply {
        text = "AI 自動記錄你常用的詞，7天內用3次以上就會加入"
        setTextColor(0x88FFFFFF.toInt())
        textSize = 12f
        setPadding(dp(4), 0, dp(4), dp(8))
    })

    // 2. 常用詞列表（可釘選/刪除）
    val learnedSection = buildSection("常用詞", personalDict.getPersonalWords()) { word ->
        // 長按 → 釘選 or 刪除選單
    }
    card.addView(learnedSection)

    // 3. 觀察中（小字顯示）
    val candidateWords = personalDict.getCandidateWords()
    if (candidateWords.isNotEmpty()) {
        card.addView(TextView(this).apply {
            text = "觀察中（再用幾次就會加入）"
            setTextColor(0x66FFFFFF.toInt())
            textSize = 11f
            setPadding(dp(4), dp(8), dp(4), dp(4))
        })
        val candidateFlow = buildWordChips(candidateWords.map { it.word })
        card.addView(candidateFlow)
    }

    // 4. 手動新增按鈕
    val addBtn = Button(this).apply {
        text = "+ 手動新增"
        setTextColor(0xFF7FD8FF.toInt())
        setBackgroundColor(0xFF1A3A5C.toInt())
    }
    addBtn.setOnClickListener {
        showAddWordDialog(personalDict)
    }
    card.addView(addBtn)

    return card
}

private fun showAddWordDialog(dict: PersonalDictionary) {
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(8))
    }
    val wordInput = android.widget.EditText(this).apply {
        hint = "詞彙（例如：迷客夏）"
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(0x88FFFFFF.toInt())
    }
    val bopoInput = android.widget.EditText(this).apply {
        hint = "注音（選填：ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ）"
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(0x88FFFFFF.toInt())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
    }
    layout.addView(wordInput)
    layout.addView(bopoInput)

    android.app.AlertDialog.Builder(this)
        .setTitle("新增個人詞彙")
        .setView(layout)
        .setPositiveButton("新增") { _, _ ->
            val word = wordInput.text.toString().trim()
            val bopo = bopoInput.text.toString().trim()
            if (word.isNotEmpty()) {
                dict.addManual(word, bopo)
                android.widget.Toast.makeText(
                    this, "已新增「$word」到個人詞庫", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        .setNegativeButton("取消", null)
        .show()
}
```

---

## 整合清單（YukiBoardIMEService.kt）

在 `onCreate()` 加入：
```kotlin
val personalDict = PersonalDictionary(applicationContext)
val styleProfiler = StyleProfiler(applicationContext)
val comboTracker = ComboTracker()
val emotionEngine = EmotionStickerEngine(applicationContext)
```

在每次 word commit 時：
```kotlin
personalDict.onWordCommitted(word, bopomofo)
styleProfiler.onSentenceCommitted(sentence, currentAppPackage)
phrasePredictor.onWordCommitted(word)
habitTracker.recordCommit(word, currentAppPackage)
```

ComboTracker 連接到 KeyboardView：
```kotlin
comboTracker.onComboReached = { combo, level ->
    keyboardView.showComboEffect(level)
}
```

文字改變時觸發情緒貼圖：
```kotlin
// 每次輸入後
val recentText = ic.getTextBeforeCursor(30, 0)?.toString() ?: ""
val emotion = emotionEngine.detectEmotion(recentText)
if (emotion != EmotionStickerEngine.Emotion.NEUTRAL) {
    val recommended = emotionEngine.recommend(emotion, "", stickerRepo.packs.flatMap { it.stickers })
    if (recommended.isNotEmpty()) {
        stickerPanelView.showEmotionBar(recommended)
    }
}
```

---

## 執行順序

```
第一批：PersonalDictionary.kt → SmartInputManager 整合
第二批：StyleProfiler.kt → AIPredictionSkill 整合
第三批：ComboTracker.kt → KeyboardView 連擊動畫
第四批：EmotionStickerEngine.kt → StickerPanelView 情緒列
第五批：SettingsActivity 個人詞庫 UI
```

---

## 效果預期

| 功能 | 使用者感受 |
|------|----------|
| 個人詞庫 | 「怎麼鍵盤越來越懂我」|
| 語氣學習 | 「AI 補的句子就是我會說的話」|
| 連擊系統 | 「打字越來越順，停不下來」（隱形上癮）|
| 情緒貼圖 | 「剛好在我打好累的時候彈出貼圖」|
| 對象感知 | （下一版）「傳給媽媽和傳給朋友的貼圖自動不一樣」|

---

## 第六批 — AI 補字 Token 控制 + 免費補字路徑

### 問題

不加控制的話：
```
用戶每天打字30分鐘
→ 停頓600ms觸發一次
→ 每分鐘約5次 × 30分鐘 = 150次/天
→ 150 × 200 tokens = 30,000 tokens/天/用戶
→ 1000個日活 × $0.0075 = 每月 $225（只是補字一個功能）
```

**用戶越多虧越多，必須控制。**

---

### 6-A 智慧觸發控制器

新增 `claw/AIPredictionGate.kt`：

```kotlin
package com.snowrealm.yukiboard.claw

import com.snowrealm.yukiboard.input.smart.PhrasePredictor

/**
 * AI 補字觸發守門員。
 *
 * 只有在「本地預測不夠好」且「使用者真的需要」時才呼叫 AI，
 * 把每天觸發次數從 150 次壓到 15-25 次，降低 6-10 倍費用。
 *
 * 五道關卡，全過才觸發 AI：
 * 1. 停頓夠久（800ms，不是600ms）
 * 2. 前文夠長（5個字以上才有意義）
 * 3. 不在打字中（composingText 為空）
 * 4. 距上次 AI 呼叫 > 10 秒（rate limit）
 * 5. 本地 PhrasePredictor 信心不夠（< 0.6）
 */
class AIPredictionGate(
    private val phrasePredictor: PhrasePredictor
) {
    private var lastAiCallMs = 0L

    data class GateResult(
        val shouldCall: Boolean,
        val reason: String  // debug 用
    )

    fun check(
        composingText: String,
        textBeforeCursor: String,
        msSinceLastKeypress: Long
    ): GateResult {

        // 關卡 1：停頓 >= 800ms
        if (msSinceLastKeypress < 800) {
            return GateResult(false, "too_soon")
        }

        // 關卡 2：前文 >= 5 個字
        if (textBeforeCursor.length < 5) {
            return GateResult(false, "context_too_short")
        }

        // 關卡 3：不在打注音中（composingText 非空表示還在打字）
        if (composingText.isNotEmpty()) {
            return GateResult(false, "composing")
        }

        // 關卡 4：距上次 AI 呼叫 >= 10 秒
        val now = System.currentTimeMillis()
        if (now - lastAiCallMs < 10_000) {
            return GateResult(false, "rate_limited")
        }

        // 關卡 5：本地預測信心不夠（本地能解決的不給 AI）
        val localConfidence = phrasePredictor.getTopConfidence()
        if (localConfidence >= 0.6f) {
            return GateResult(false, "local_confident")
        }

        return GateResult(true, "passed")
    }

    /** 呼叫 AI 後記錄時間 */
    fun recordAiCall() {
        lastAiCallMs = System.currentTimeMillis()
    }
}
```

在 `PhrasePredictor.kt` 加入 `getTopConfidence()`：

```kotlin
/**
 * 目前預測的最高信心分數（0~1）。
 * 用於決定是否需要呼叫 AI 補充。
 */
fun getTopConfidence(): Float {
    val predictions = predict(1)
    if (predictions.isEmpty()) return 0f
    val top = predictions.first()
    // 用相對頻率估算信心：出現10次以上算高信心
    return (top.score / 10f).coerceIn(0f, 1f)
}
```

在 `YukiBoardIMEService.kt` 的 AI 補字觸發改為：

```kotlin
// 原本：
private fun scheduleAIPrediction(text: String) {
    aiPredictJob?.cancel()
    aiPredictJob = serviceScope.launch {
        delay(600)
        // 直接呼叫 AI...
    }
}

// 改為：
private var lastKeypressMs = 0L

private fun scheduleAIPrediction(composing: String, textBefore: String) {
    if (!prefs.aiPredictionEnabled) return
    if (clawClient?.isAvailable() != true) return

    aiPredictJob?.cancel()
    lastKeypressMs = System.currentTimeMillis()

    aiPredictJob = serviceScope.launch {
        delay(800)  // 等800ms

        val elapsed = System.currentTimeMillis() - lastKeypressMs
        val gate = aiGate.check(
            composingText = composing,
            textBeforeCursor = textBefore,
            msSinceLastKeypress = elapsed
        )

        if (!gate.shouldCall) return@launch

        // 通過所有關卡，呼叫 AI
        aiGate.recordAiCall()
        val result = clawClient!!.executeSkill(
            "ai_prediction",
            ClawInput(
                text = textBefore.takeLast(50),
                context = textBefore,
                appPackage = currentAppPackage ?: ""
            )
        )
        withContext(Dispatchers.Main) {
            keyboardView.updateAiSuggestions(result.suggestions)
        }
    }
}
```

---

### 6-B 分層 AI Provider（免費補字路徑）

新增 `claw/provider/GeminiFlashProvider.kt`：

```kotlin
package com.snowrealm.yukiboard.claw.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Google Gemini Flash Provider。
 *
 * 免費方案：每天 1,500 次請求，每分鐘 15 次
 * 速度：< 1 秒（比 Claude/GPT 快）
 * 品質：比 Haiku 稍弱，但補字夠用
 *
 * 免費 API Key 申請：https://aistudio.google.com/
 * 不需要信用卡，台灣可用
 *
 * 用途：AI 補字專用（低成本、高頻觸發場景）
 * 其他 Skill（WriterSkill、YukiChatSkill）仍用 Anthropic/OpenAI
 */
class GeminiFlashProvider(private val apiKey: String) : AIProvider {

    override val id = "gemini_flash"
    override val name = "Gemini Flash（免費）"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"
    private val model = "gemini-2.0-flash"  // 最新免費模型

    override fun complete(prompt: String, systemPrompt: String): Flow<String> = flow {
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
            if (systemPrompt.isNotEmpty()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemPrompt))
                    })
                })
            }
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 200)   // 補字只需要短輸出
                put("temperature", 0.7)
            })
        }

        val request = Request.Builder()
            .url("$baseUrl/$model:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        val responseBody = response.body?.string() ?: return@flow
        try {
            val json = JSONObject(responseBody)
            val text = json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            emit(text)
        } catch (_: Exception) {}
    }

    override suspend fun validateKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(
                    JSONObject().apply {
                        put("contents", JSONArray().put(
                            JSONObject().apply {
                                put("parts", JSONArray().put(
                                    JSONObject().put("text", "Hi")
                                ))
                            }
                        ))
                    }.toString().toRequestBody("application/json".toMediaType())
                )
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) { false }
    }
}
```

在 `AIProviderManager.kt` 加入 Gemini Flash：

```kotlin
// 在 AIProviderType 加
GEMINI_FLASH

// 在 getProvider() 加
AIProviderType.GEMINI_FLASH -> {
    val key = getApiKey(AIProviderType.GEMINI_FLASH) ?: ""
    GeminiFlashProvider(key)
}
```

在 `AISettingsActivity.kt` 加入 Gemini Flash 選項：

```kotlin
Triple(AIProviderType.GEMINI_FLASH, "Gemini Flash（免費，補字專用）", true)
```

---

### 6-C 雙軌 Provider 策略

**概念：** 補字用便宜/免費的，其他 Skill 用強的。

在 `ClawClient.kt` 的 `getSkill()` 加入補字專用 Provider：

```kotlin
private fun getSkill(skillId: String): ClawSkill? {
    // 補字 Skill 優先用 Gemini Flash（免費）
    val predictionProvider = if (
        skillId == "ai_prediction" &&
        providerManager.hasValidConfig(AIProviderType.GEMINI_FLASH)
    ) {
        providerManager.getProvider(AIProviderType.GEMINI_FLASH)
    } else {
        providerManager.getCurrentProvider()
    }

    return when (skillId) {
        "ai_prediction" -> AIPredictionSkill(predictionProvider, styleProfiler)
        // 其他 Skill 用主要 Provider（Claude / OpenAI）
        "writer"        -> WriterSkill(appContext, providerManager.getCurrentProvider())
        // ... 其他不變
        else -> null
    }
}
```

---

### 6-D 免費補字的長期路線圖

```
現在（0成本）：
Gemini Flash 免費方案
→ 每天 1,500 次/用戶
→ 夠用（觸發控制後每天 15-25 次）
→ 完全免費，不需信用卡

3-6個月後（有用戶基礎）：
加入本地端補字模型
→ Gemini Nano（Pixel 9/Samsung S25 內建）
→ 零 API 費用，完全離線
→ 隱私保護
→ 但只有旗艦機有

1年後（自建護城河）：
收集用戶打字資料（需明確同意）
→ 微調輕量補字模型（1B~3B 參數）
→ 部署在自己伺服器
→ 成本固定不隨用戶數線性增長
→ 這才是真正的護城河

設定頁開關（給用戶選擇）：
[ ] 使用 AI 補字（需 API Key 或免費方案）
[x] 僅本地預測（完全離線，零費用）
[x] Gemini Flash 免費（推薦新用戶）
[ ] 自帶 API Key（無限制）
```

---

### 6-E YukiPreferences 新增開關

在 `YukiPreferences.kt` 加入：

```kotlin
var aiPredictionEnabled: Boolean by BoolPref("ai_prediction_enabled", true)
var aiPredictionProvider: String by StringPref("ai_prediction_provider", "gemini_flash")
// 每日 AI 呼叫次數上限（-1 = 無限）
var aiDailyLimit: Int by IntPref("ai_daily_limit", 50)
```

在 `TokenBudgetManager.kt` 加入每日呼叫次數追蹤：

```kotlin
/** 今天 AI 補字已呼叫幾次 */
fun getPredictionCallsToday(): Int {
    resetDailyIfNeeded()
    return prefs.getInt("prediction_calls_today", 0)
}

fun incrementPredictionCalls() {
    resetDailyIfNeeded()
    val current = prefs.getInt("prediction_calls_today", 0)
    prefs.edit().putInt("prediction_calls_today", current + 1).apply()
}

/** 是否超過今日上限 */
fun isPredictionLimitReached(limit: Int): Boolean {
    if (limit == -1) return false
    return getPredictionCallsToday() >= limit
}
```

---

## 更新後的完整執行順序

```
第一批：PersonalDictionary.kt → SmartInputManager 整合
第二批：StyleProfiler.kt → AIPredictionSkill 整合（加入 styleProfiler 參數）
第三批：ComboTracker.kt → KeyboardView 連擊動畫
第四批：EmotionStickerEngine.kt → StickerPanelView 情緒列
第五批：SettingsActivity 個人詞庫 UI
第六批：
  6-A：AIPredictionGate.kt（觸發控制，5道關卡）
       PhrasePredictor 加 getTopConfidence()
       YukiBoardIMEService 更新 scheduleAIPrediction()
  6-B：GeminiFlashProvider.kt（免費補字 Provider）
       AIProviderManager 加 GEMINI_FLASH 類型
       AISettingsActivity 加 Gemini Flash 選項
  6-C：ClawClient 雙軌 Provider（補字用 Gemini，其他用主 Provider）
  6-D：（無需實作，路線圖記錄）
  6-E：YukiPreferences 加 AI 補字開關
       TokenBudgetManager 加每日呼叫次數追蹤
```

---

## Token 費用對比（加控制後）

| 情境 | 每天觸發 | 每月費用/用戶 | 說明 |
|------|---------|------------|------|
| 無控制（原始） | ~150次 | ~$0.22 | 1000用戶=$220/月 |
| 加觸發控制 | ~20次 | ~$0.03 | 降低87% |
| 改用 Gemini Flash | ~20次 | $0 | 免費方案 1500次/天 |
| 本地模型（未來） | 無限 | $0 | 完全離線 |

**結論：現在就用 Gemini Flash + 觸發控制，AI 補字費用直接歸零。**

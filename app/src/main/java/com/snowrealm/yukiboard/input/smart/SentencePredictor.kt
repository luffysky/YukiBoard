package com.snowrealm.yukiboard.input.smart

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.*

/**
 * ���句上下文預測引擎 — 根據輸入框前文的句子，預測使用者可能要回覆的整句。
 *
 * 三層策略：
 * 1. 使用者學習句型：記錄使用者歷史「前一句→回覆句」模式
 * 2. 內建常用回覆模板：中/英/日/韓 常見對話模式（問候/感謝/道歉/問題等）
 * 3. 句子片段接續：根據目前正在輸入的句子開頭，預測完整句子
 *
 * 與 PhrasePredictor（word→word bigram/trigram）互補：
 * - PhrasePredictor：字詞級別，逐詞預測
 * - SentencePredictor：句子級別，整句預測
 */
class SentencePredictor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_sentence_predict", Context.MODE_PRIVATE)

    // 學習的句子 bigram：前句 → { 回覆句 → 次數 }
    private val sentenceBigrams = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Int>>()

    // 句子片段接續：句首 → { 完整句 → 次數 }
    private val sentencePrefixes = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Int>>()

    // 最近提交的句子（用於學習 bigram）
    private var lastCommittedSentence: String = ""

    // 上一次從輸入框讀到的對方句子（用於 pattern matching）
    private var lastContextSentence: String = ""

    init { loadAll() }

    // ─────────────────────────────────────────
    // 學習
    // ────────��─────────────────────��──────────

    /**
     * 使用者提交一段文字時呼叫。
     * 學習「前句→回覆」模式 + 「句首→完整句」模式。
     */
    fun onSentenceCommitted(sentence: String) {
        val trimmed = sentence.trim()
        if (trimmed.length < 2) return

        // 學習 sentence bigram（對方說的→我回的）
        if (lastContextSentence.isNotEmpty()) {
            val key = normalizeSentence(lastContextSentence)
            if (key.isNotEmpty()) {
                incrementMap(sentenceBigrams, key, trimmed)
            }
        }

        // 學習句子片段接續（句首→完整句）
        val prefixLen = minOf(trimmed.length, 6)
        if (trimmed.length >= 4) {
            for (len in 2..prefixLen) {
                val prefix = trimmed.substring(0, len)
                incrementMap(sentencePrefixes, prefix, trimmed)
            }
        }

        lastCommittedSentence = trimmed
    }

    /**
     * 更新輸入框上下文句子（對方/前文的最後一句）。
     */
    fun updateContextSentence(contextText: String) {
        val sentence = extractLastSentence(contextText)
        if (sentence.isNotEmpty()) {
            lastContextSentence = sentence
        }
    }

    // ─────��────────────────────��──────────────
    // 預測
    // ���────────────────��───────────────────────

    /**
     * 根據輸入框上下文，預測整句回覆。
     *
     * @param contextText 輸入框游標前的文字（getTextBeforeCursor）
     * @param currentInput 目前正在輸入的文字（composing 或已輸入的部分）
     * @param maxResults 最多回傳幾句
     * @return 整句預測列表，按信心排序
     */
    fun predictSentences(
        contextText: String,
        currentInput: String = "",
        maxResults: Int = 5
    ): List<SentencePrediction> {
        val results = mutableMapOf<String, Float>()

        // ── 1. 學習的句子 bigram：對方說→我回 ──
        val lastSentence = extractLastSentence(contextText)
        if (lastSentence.isNotEmpty()) {
            val key = normalizeSentence(lastSentence)
            sentenceBigrams[key]?.let { map ->
                val total = map.values.sum().toFloat()
                for ((reply, count) in map) {
                    val score = (count / total) * LEARNED_WEIGHT
                    results[reply] = (results[reply] ?: 0f) + score
                }
            }
        }

        // ── 2. 內建常用回覆模板 ──
        val builtinReplies = matchBuiltinPatterns(lastSentence)
        for ((i, reply) in builtinReplies.withIndex()) {
            val score = BUILTIN_WEIGHT * (1f - i * 0.1f)
            results[reply] = (results[reply] ?: 0f) + score
        }

        // ── 3. 句子片段接續（使用者正在打的句首→整句） ──
        if (currentInput.length >= 2) {
            sentencePrefixes[currentInput]?.let { map ->
                val total = map.values.sum().toFloat()
                for ((sentence, count) in map) {
                    if (sentence != currentInput) { // 排除完全相同的
                        val score = (count / total) * PREFIX_WEIGHT
                        results[sentence] = (results[sentence] ?: 0f) + score
                    }
                }
            }
        }

        // ── 4. 如果有 currentInput，過濾只留匹配前綴的 ──
        val filtered = if (currentInput.length >= 2) {
            results.filter { (sentence, _) -> sentence.startsWith(currentInput) }
        } else {
            results
        }

        return filtered.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { SentencePrediction(it.key, it.value) }
    }

    // ─────────────────────────────────────────
    // 句子提取
    // ────��────────────────────────────────────

    /**
     * 從上下文文字中提取最後一個完整句子。
     * 支援中/英/日/韓 標點斷句。
     */
    fun extractLastSentence(text: String): String {
        if (text.isBlank()) return ""
        val trimmed = text.trimEnd()

        // 句末標點
        val sentenceEnders = charArrayOf(
            '。', '！', '？', '…', '~', '～',        // 中文
            '.', '!', '?',                            // 英文
            '。', '！', '？',                          // 日文全形
            '\n', '\r'                                 // 換行
        )

        // 從尾端往前找句末標點，取最後一句
        val endIdx = trimmed.length
        var startIdx = endIdx

        // 跳過尾端的句末標點
        while (startIdx > 0 && trimmed[startIdx - 1] in sentenceEnders) {
            startIdx--
        }

        // 繼續往前找上一個句末標點
        val contentEnd = startIdx
        while (startIdx > 0 && trimmed[startIdx - 1] !in sentenceEnders) {
            startIdx--
        }

        val sentence = trimmed.substring(startIdx, contentEnd).trim()
        return if (sentence.length >= 2) sentence else ""
    }

    /**
     * 提取上下文中所有可辨識的句子（最近 3 句）。
     */
    fun extractRecentSentences(text: String, maxSentences: Int = 3): List<String> {
        if (text.isBlank()) return emptyList()

        val sentences = mutableListOf<String>()
        val sentencePattern = Regex("""[^。！？.!?\n\r~～…]+[。！？.!?\n\r~～…]*""")
        val matches = sentencePattern.findAll(text.trim())

        for (match in matches) {
            val s = match.value.trim()
            if (s.length >= 2) sentences.add(s)
        }

        return sentences.takeLast(maxSentences)
    }

    // ─────��───────────────────────────────────
    // 內建常用回覆模板
    // ───────��─────────────────────────────────

    private fun matchBuiltinPatterns(sentence: String): List<String> {
        if (sentence.isBlank()) return emptyList()
        val lower = sentence.lowercase().trim()
        val results = mutableListOf<String>()

        for ((patterns, replies) in builtinReplyPatterns) {
            if (patterns.any { pattern -> matchPattern(lower, pattern) }) {
                results.addAll(replies)
            }
        }
        return results.distinct().take(8)
    }

    private fun matchPattern(text: String, pattern: String): Boolean {
        // 支援簡單的 contains 匹配和 regex
        return if (pattern.startsWith("^") || pattern.endsWith("$")) {
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
        } else {
            text.contains(pattern)
        }
    }

    // ─────────────────────────────────────────
    // 正規化
    // ─────────────────────────────────────────

    private fun normalizeSentence(sentence: String): String {
        return sentence.trim()
            .replace(Regex("[。！？.!?~～…\\s]+$"), "") // 去句末標點
            .lowercase()
            .take(50) // 限制 key 長度
    }

    // ────���────────────────────────────────────
    // 持久化
    // ────────────��────────────────────────────

    fun save() {
        val editor = prefs.edit()

        // 存 sentenceBigrams
        val biKeys = mutableListOf<String>()
        for ((key, map) in sentenceBigrams) {
            val encoded = map.entries.joinToString("||") { "${it.key}\t${it.value}" }
            editor.putString("sb_$key", encoded)
            biKeys.add(key)
        }
        editor.putString("_sb_keys", biKeys.joinToString(";;"))

        // 存 sentencePrefixes
        val pfKeys = mutableListOf<String>()
        for ((key, map) in sentencePrefixes) {
            val encoded = map.entries.joinToString("||") { "${it.key}\t${it.value}" }
            editor.putString("sp_$key", encoded)
            pfKeys.add(key)
        }
        editor.putString("_sp_keys", pfKeys.joinToString(";;"))

        editor.apply()
    }

    fun resetContext() {
        lastContextSentence = ""
        lastCommittedSentence = ""
    }

    // ─────────────────────────────────────────
    // 載入
    // ────────────���────────────────────────────

    private fun loadAll() {
        // 載入 sentenceBigrams
        val biKeys = prefs.getString("_sb_keys", null)?.split(";;") ?: emptyList()
        for (key in biKeys) {
            if (key.isBlank()) continue
            val raw = prefs.getString("sb_$key", null) ?: continue
            sentenceBigrams[key] = parseEntries(raw)
        }

        // 載入 sentencePrefixes
        val pfKeys = prefs.getString("_sp_keys", null)?.split(";;") ?: emptyList()
        for (key in pfKeys) {
            if (key.isBlank()) continue
            val raw = prefs.getString("sp_$key", null) ?: continue
            sentencePrefixes[key] = parseEntries(raw)
        }
    }

    private fun parseEntries(raw: String): MutableMap<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (entry in raw.split("||")) {
            val parts = entry.split("\t")
            if (parts.size == 2) {
                map[parts[0]] = parts[1].toIntOrNull() ?: 0
            }
        }
        return map
    }

    private fun incrementMap(
        store: MutableMap<String, MutableMap<String, Int>>,
        key: String,
        value: String
    ) {
        val map = store.getOrPut(key) { mutableMapOf() }
        map[value] = (map[value] ?: 0) + 1
        if (map.size > MAX_ENTRIES) {
            val sorted = map.entries.sortedBy { it.value }
            sorted.take(map.size - MAX_ENTRIES + 5).forEach { map.remove(it.key) }
        }
        if (store.size > MAX_KEYS) {
            val oldest = store.entries.sortedBy { it.value.values.sum() }.take(store.size - MAX_KEYS + 10)
            oldest.forEach { store.remove(it.key) }
        }
    }

    // ─────────────────────────────────────────
    // 資料類別
    // ─────────────────────────────────────────

    data class SentencePrediction(val sentence: String, val confidence: Float)

    companion object {
        const val LEARNED_WEIGHT = 3.0f
        const val BUILTIN_WEIGHT = 1.5f
        const val PREFIX_WEIGHT = 2.0f
        const val MAX_ENTRIES = 30
        const val MAX_KEYS = 500

        /**
         * 內建常用回覆模板：patterns → replies
         * 涵蓋中/英/日/韓 常見對話模式
         */
        val builtinReplyPatterns: List<Pair<List<String>, List<String>>> = listOf(
            // ─── 中文 ───
            // 問候
            listOf("你好", "嗨", "哈囉", "嘿", "hi", "hello") to
                listOf("你好！", "嗨～", "哈囉！", "你好呀"),
            listOf("早安", "早上好", "good morning") to
                listOf("早安！", "早上好～", "早安 今天也加油"),
            listOf("晚安", "good night") to
                listOf("晚安～", "晚安 好夢", "晚安！"),
            listOf("午安", "中午好") to
                listOf("午安！", "午安～ 吃飽了嗎"),
            // 你好嗎
            listOf("你好嗎", "最近好嗎", "最近怎樣", "過得如何", "how are you") to
                listOf("我很好，你呢？", "還不錯！", "挺好的～", "老樣子啦", "還行！"),
            // 感謝
            listOf("謝謝", "感謝", "多謝", "thanks", "thank you", "ありがとう", "감사") to
                listOf("不客氣！", "不會～", "沒事！", "應該的"),
            // 道歉
            listOf("對不起", "抱歉", "不好意思", "sorry", "すみません", "죄송") to
                listOf("沒關係！", "沒事的～", "沒問題", "不要緊"),
            // 再見
            listOf("再見", "拜拜", "掰掰", "bye", "さようなら", "안녕") to
                listOf("拜拜～", "再見！", "掰掰 下次見", "88"),
            // 吃飯
            listOf("吃飯了嗎", "吃了嗎", "吃什麼") to
                listOf("吃了！", "還沒欸", "正在吃", "不知道要吃什麼"),
            // 在幹嘛
            listOf("在幹嘛", "在做什麼", "在忙嗎", "幹嘛") to
                listOf("沒在幹嘛", "在忙", "在耍廢", "剛到家"),
            // 等下
            listOf("等一下", "稍等", "等等") to
                listOf("好的", "OK", "好～"),
            // 同意
            listOf("好的", "ok", "可以", "沒問題", "好啊") to
                listOf("好！", "OK", "收到", "了解"),
            // 開心
            listOf("哈哈", "笑死", "太好了", "好棒", "讚") to
                listOf("哈哈哈", "對啊！", "真的很讚", "超開心"),
            // 生氣/不爽
            listOf("煩死了", "好煩", "生氣", "氣死") to
                listOf("怎麼了？", "發生什麼事？", "拍拍", "別氣別氣"),
            // 難過
            listOf("好難過", "好傷心", "哭", "嗚��") to
                listOf("怎麼了？", "拍拍 抱抱", "有什麼事可以說", "會好的"),

            // ─── 英文 ───
            listOf("how are you", "how's it going", "what's up") to
                listOf("I'm good, thanks!", "Not bad!", "Pretty good, you?", "Doing well!"),
            listOf("what are you doing", "what are you up to", "whatcha doing") to
                listOf("Nothing much", "Just chilling", "Working", "Not much, you?"),
            listOf("good morning", "morning") to
                listOf("Good morning!", "Morning! ☀️", "Good morning, have a great day!"),
            listOf("good night", "night") to
                listOf("Good night!", "Night! Sleep well", "Sweet dreams!"),
            listOf("i'm sorry", "my bad", "apologies") to
                listOf("No worries!", "It's okay!", "Don't worry about it", "All good!"),
            listOf("see you", "gotta go", "ttyl") to
                listOf("See you!", "Bye!", "Talk to you later!", "Take care!"),
            listOf("lol", "lmao", "haha", "😂") to
                listOf("haha", "😂", "lol same", "ikr"),
            listOf("nice", "cool", "awesome", "great") to
                listOf("Thanks!", "Right?!", "I know!", "Ikr!"),
            listOf("i love you", "love you") to
                listOf("I love you too!", "Love you too ❤️", "❤️"),
            listOf("happy birthday") to
                listOf("Thank you!", "Thanks! 🎂", "Thank you so much!"),

            // ─── 日文 ───
            listOf("おはよう", "おはようございます") to
                listOf("おはよう！", "おはようございます！", "おはよう～ 今日も頑張ろう"),
            listOf("こんにちは", "こんにちわ") to
                listOf("こんにちは！", "こんにちは～"),
            listOf("こんばんは") to
                listOf("こんばんは！", "こんばんは～"),
            listOf("お疲れ様", "おつかれ", "お疲れ") to
                listOf("お疲れ様です！", "お疲れ～", "おつかれさま！"),
            listOf("ただいま") to
                listOf("おかえり！", "おかえりなさい！"),
            listOf("おかえり") to
                listOf("ただいま！", "ただいま～"),
            listOf("いただきます") to
                listOf("召し上がれ！", "いただきます！"),
            listOf("元気", "元気ですか", "調子はどう") to
                listOf("元気だよ！", "まあまあ", "元気！そっちは？"),
            listOf("大丈夫", "平気") to
                listOf("大丈夫だよ！", "ありがとう", "心配しないで"),
            listOf("おやすみ", "おやすみなさい") to
                listOf("おやすみ～", "おやすみなさい！", "いい夢を"),
            listOf("了解", "わかりました", "わかった") to
                listOf("OK！", "了解です", "ありがとう"),
            listOf("すごい", "やばい", "まじ") to
                listOf("でしょ！", "マジで！", "すごいよね"),

            // ─── 韓文 ───
            listOf("안녕하세요", "안녕") to
                listOf("안녕하세요！", "안녕～", "반가워요！"),
            listOf("잘 지내", "잘 지내세요", "어떻게 지내") to
                listOf("잘 지내요！", "네, 잘 지내요!", "덕분에요"),
            listOf("감사합니다", "고마워", "고맙습니다") to
                listOf("천만에요！", "별말씀을요", "아니에요～"),
            listOf("죄송합니다", "미안해", "미안합니다") to
                listOf("괜찮아요！", "아니에요", "신경 쓰지 마세요"),
            listOf("수고하셨습니다", "수고", "고생") to
                listOf("수고하셨습니다！", "감사합니다", "수고하셨어요～"),
            listOf("밥 먹었어", "밥 먹었", "식사") to
                listOf("네 먹었어요!", "아직이요", "지금 먹으려고요"),
            listOf("뭐 해", "뭐해", "뭐하고 있어") to
                listOf("그냥 있어요", "별거 안 해요", "쉬고 있어요"),
            listOf("잘 자", "굿나잇") to
                listOf("잘 자요～", "좋은 꿈 꿔요", "굿나잇！"),
            listOf("대박", "진짜", "헐") to
                listOf("맞아요！", "진짜？！", "대박이다"),
            listOf("사랑해", "좋아해") to
                listOf("나도 사랑해 ❤️", "나도！", "고마워 ❤️"),
        )
    }

    // ─────────────── 雲端 snapshot（Sprint 7-4） ───────────────

    fun exportSnapshot(): JsonObject = buildJsonObject {
        put("sb_keys", prefs.getString("_sb_keys", "") ?: "")
        val sbKeys = prefs.getString("_sb_keys", null)?.split(";;") ?: emptyList()
        for (key in sbKeys) {
            if (key.isBlank()) continue
            prefs.getString("sb_$key", null)?.let { put("sb_$key", it) }
        }
        put("sp_keys", prefs.getString("_sp_keys", "") ?: "")
        val spKeys = prefs.getString("_sp_keys", null)?.split(";;") ?: emptyList()
        for (key in spKeys) {
            if (key.isBlank()) continue
            prefs.getString("sp_$key", null)?.let { put("sp_$key", it) }
        }
    }

    fun importSnapshot(obj: JsonObject) {
        val editor = prefs.edit()
        for ((key, value) in obj) {
            val raw = value.jsonPrimitive.contentOrNull ?: continue
            if (key == "sb_keys") {
                val existing = prefs.getString("_sb_keys", null)?.split(";;")?.filter { it.isNotBlank() } ?: emptyList()
                val incoming = raw.split(";;").filter { it.isNotBlank() }
                editor.putString("_sb_keys", (existing + incoming).distinct().joinToString(";;"))
            } else if (key == "sp_keys") {
                val existing = prefs.getString("_sp_keys", null)?.split(";;")?.filter { it.isNotBlank() } ?: emptyList()
                val incoming = raw.split(";;").filter { it.isNotBlank() }
                editor.putString("_sp_keys", (existing + incoming).distinct().joinToString(";;"))
            } else {
                editor.putString(key, raw)
            }
        }
        editor.apply()
        // S9-C：import 完成後立即 reload in-memory，讓長生命週期 owner 不需等下次 cold start。
        loadAll()
    }
}

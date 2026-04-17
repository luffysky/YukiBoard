package com.snowrealm.yukiboard.input.smart

import android.content.Context
import com.snowrealm.yukiboard.input.dictionary.EnglishDictionary
import com.snowrealm.yukiboard.input.swipe.SwipeLearnManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 智慧輸入總管 — 統合糾錯、自適應字典、語句預測、習慣追蹤。
 *
 * 由 [InputRouter] 持有，在關鍵時刻呼叫：
 * - processChar → 記錄按鍵速度
 * - commit → 記錄詞彙 + 更新 n-gram + 自適應字典
 * - getCandidates → 追加語句預測到候選列尾端
 * - processSpace → 如需糾錯，替換為修正後的詞
 *
 * 所有功能都有獨立 prefs 開關，任一可單獨關閉。
 */
class SmartInputManager(context: Context) {

    val adaptiveDictionary = AdaptiveDictionary(context)
    val phrasePredictor = PhrasePredictor(context)
    val sentencePredictor = SentencePredictor(context)
    val habitTracker = TypingHabitTracker(context)
    val personalDictionary = PersonalDictionary(context)
    val styleProfiler = StyleProfiler(context)
    val comboTracker = ComboTracker()
    val emotionSticker = EmotionStickerEngine()
    val swipeLearnManager = SwipeLearnManager(context)
    val correctionLearnManager = CorrectionLearnManager(context)

    private var dictionary: EnglishDictionary? = null
    private var correctionEngine: SmartCorrectionEngine? = null

    // ── 功能開關（由外部依 YukiPreferences 設定） ──

    var smartCorrectionEnabled = true
    var adaptiveDictEnabled = true
    var phrasePredictEnabled = true
    var habitTrackEnabled = true

    /** 設定字典參考（由 EnglishInputEngine 提供） */
    fun setDictionary(dict: EnglishDictionary) {
        dictionary = dict
        correctionEngine = SmartCorrectionEngine(dict, adaptiveDictionary, correctionLearnManager)
    }

    // ─────────────── 打字時呼叫 ───────────────

    /** 每次按鍵呼叫 — 記錄打字速度 */
    fun onKeystroke() {
        if (habitTrackEnabled) habitTracker.recordKeystroke()
    }

    // ─────────────── 提交時呼叫 ───────────────

    /** 詞彙提交時呼叫 — 更新所有學習模組 */
    fun onWordCommitted(word: String, appPackage: String? = null) {
        val dict = dictionary ?: return

        if (adaptiveDictEnabled) {
            adaptiveDictionary.recordUsage(word, dict)
        }
        if (phrasePredictEnabled) {
            phrasePredictor.onWordCommitted(word)
        }
        if (habitTrackEnabled) {
            habitTracker.recordCommit(word, appPackage)
        }
        // J-1: 個人詞庫三層學習
        personalDictionary.recordUsage(word)
        // J-2: 語氣風格分析
        styleProfiler.analyze(word, appPackage)
    }

    // ─────────────── 整句預測 ───────────────

    /**
     * 更新輸入框上下文（由 Service 在 handleKey 時呼叫）。
     * 用於整句預測的前文分析。
     */
    fun updateSentenceContext(contextText: String) {
        if (phrasePredictEnabled) {
            sentencePredictor.updateContextSentence(contextText)
        }
    }

    /**
     * 提交一段完整句子時呼叫（Enter/換行/切 App 觸發）。
     * 用於學習句子級別的回覆模式。
     */
    fun onSentenceCommitted(sentence: String) {
        if (phrasePredictEnabled) {
            sentencePredictor.onSentenceCommitted(sentence)
        }
    }

    /**
     * 取得整句預測候選列表。
     * @param contextText 輸入框游標前文字
     * @param currentInput 目前正在輸入的文字
     * @param maxResults 最多回傳幾句
     */
    fun getSentencePredictions(
        contextText: String,
        currentInput: String = "",
        maxResults: Int = 5
    ): List<String> {
        if (!phrasePredictEnabled) return emptyList()
        return sentencePredictor.predictSentences(contextText, currentInput, maxResults)
            .map { it.sentence }
    }

    /**
     * 從上下文提取最近的句子（用於整句級別上下文分析）。
     */
    fun extractRecentSentences(contextText: String): List<String> {
        return sentencePredictor.extractRecentSentences(contextText)
    }

    // ─────────────── 糾錯 ───────────────

    /**
     * 嘗試糾錯。回傳修正詞或 null（不需修正）。
     * @param typed 使用者實際打的詞
     * @param prevWord 前一個詞（上下文）
     */
    fun tryCorrect(typed: String, prevWord: String? = null): String? {
        if (!smartCorrectionEnabled) return null
        val engine = correctionEngine ?: return null

        val corrections = engine.correct(typed, prevWord)
        if (corrections.isEmpty()) return null

        // 快打模式 → 更積極糾錯（直接用第一候選）
        // 慢打模式 → 只在高信心時糾錯
        return corrections.first()
    }

    /**
     * 取得糾錯候選列表（顯示在候選列前端）。
     */
    fun getCorrectionCandidates(typed: String, prevWord: String? = null): List<String> {
        if (!smartCorrectionEnabled) return emptyList()
        return correctionEngine?.correct(typed, prevWord) ?: emptyList()
    }

    // ─────────────── 預測 ───────────────

    /**
     * 取得「下一個詞」預測列表（接在候選列後面）。
     */
    fun getPhrasePredictions(maxResults: Int = 3): List<String> {
        if (!phrasePredictEnabled) return emptyList()
        return phrasePredictor.predict(maxResults).map { it.word }
    }

    /**
     * 帶前綴過濾的預測。
     */
    fun getPhrasePredictionsWithPrefix(prefix: String, max: Int = 3): List<String> {
        if (!phrasePredictEnabled) return emptyList()
        return phrasePredictor.predictWithPrefix(prefix, max).map { it.word }
    }

    /**
     * 用外部上下文（getTextBeforeCursor）做 bigram/trigram 預測。
     */
    fun predictWithContext(contextWords: List<String>, max: Int = 5): List<String> {
        if (!phrasePredictEnabled) return emptyList()
        return phrasePredictor.predictWithContext(contextWords, max).map { it.word }
    }

    /**
     * 取得情境建議（時段 + App 綜合）。
     */
    fun getContextSuggestions(appPackage: String? = null, max: Int = 3): List<String> {
        if (!habitTrackEnabled) return emptyList()

        val period = habitTracker.getPeriodSuggestions(max)
        val app = if (appPackage != null) habitTracker.getAppSuggestions(appPackage, max) else emptyList()

        // 合併去重，App 優先
        return (app + period).distinct().take(max)
    }

    // ─────────────── 自我學習回饋 ───────────────

    /**
     * 使用者從滑動候選中選擇了某個詞時呼叫。
     * 記錄到 SwipeLearnManager，下次相同滑動軌跡優先顯示。
     */
    fun onSwipeWordCommitted(bopomofoKey: String, word: String) {
        swipeLearnManager.recordSelection(bopomofoKey, word)
    }

    /**
     * 使用者接受了自動校正建議時呼叫。
     */
    fun onCorrectionAccepted(typed: String, corrected: String) {
        correctionLearnManager.recordAccepted(typed, corrected)
    }

    /**
     * 使用者拒絕了自動校正建議時呼叫（選了其他候選或直接按掉）。
     */
    fun onCorrectionRejected(typed: String, corrected: String) {
        correctionLearnManager.recordRejected(typed, corrected)
    }

    // ─────────────── 生命週期 ───────────────

    /** 換行 / 切 App 時重置上下文 */
    fun resetContext() {
        phrasePredictor.resetContext()
        sentencePredictor.resetContext()
    }

    /** 滑動輸入英文候選（委派給 EnglishDictionary） */
    fun swipeSuggestEnglish(chars: List<Char>): List<String> {
        return dictionary?.swipeSuggest(chars) ?: emptyList()
    }

    /** 候選命中 — 觸發連擊計數 */
    fun onCandidateHit() {
        comboTracker.hit()
    }

    /** 取得語氣加分（候選字排序用） */
    fun getStyleBoost(word: String, appPackage: String? = null): Float {
        return styleProfiler.styleBoost(word, appPackage)
    }

    /** 取得情緒貼圖推薦標籤 */
    fun getEmotionStickerTags(text: String): List<String> {
        return emotionSticker.recommend(text)
    }

    /**
     * S9-C：套用雲端下載的 learning snapshot。
     *
     * 由 [com.snowrealm.yukiboard.data.CloudSyncManager] 在長生命週期 IME alive 時
     * 透過 `setSmartInputApplier` 注入的 callback 呼叫；避免 throwaway instance 寫 prefs
     * 但 long-lived subsystem 記憶體未 reload 的不一致。
     *
     * 委派給 7 個 subsystem 各自的 `importSnapshot()`；subsystem 內部 (S9-C) 已在
     * `editor.apply()` 後 loadAll()，故 callsite 回傳後 in-memory 已同步。
     *
     * snapshot JSON schema 與 Sprint 7-4 的 `exportSnapshot()` 對稱（不在本輪收斂）。
     */
    fun applyCloudSnapshot(snapshot: JsonObject) {
        snapshot["adaptive_dict"]?.jsonObject?.let(adaptiveDictionary::importSnapshot)
        snapshot["personal_dict"]?.jsonObject?.let(personalDictionary::importSnapshot)
        snapshot["phrase_predict"]?.jsonObject?.let(phrasePredictor::importSnapshot)
        snapshot["sentence_predict"]?.jsonObject?.let(sentencePredictor::importSnapshot)
        snapshot["style_profile"]?.jsonObject?.let(styleProfiler::importSnapshot)
        snapshot["typing_habits"]?.jsonObject?.let(habitTracker::importSnapshot)
        snapshot["correction_learn"]?.jsonObject?.let(correctionLearnManager::importSnapshot)
    }

    /** 定期儲存（onFinishInputView 呼叫） */
    fun persist() {
        phrasePredictor.save()
        sentencePredictor.save()
        habitTracker.save()
        styleProfiler.save()
        swipeLearnManager.save()
        correctionLearnManager.save()
        adaptiveDictionary.persist()
    }

    /** 定期清理過期資料 */
    fun maintenance() {
        adaptiveDictionary.purgeStale()
        personalDictionary.maintenance()
        swipeLearnManager.purgeStale()
    }
}

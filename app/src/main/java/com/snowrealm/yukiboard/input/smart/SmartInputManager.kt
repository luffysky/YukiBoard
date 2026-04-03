package com.snowrealm.yukiboard.input.smart

import android.content.Context
import com.snowrealm.yukiboard.input.dictionary.EnglishDictionary

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
    val habitTracker = TypingHabitTracker(context)

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
        correctionEngine = SmartCorrectionEngine(dict, adaptiveDictionary)
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
     * 取得情境建議（時段 + App 綜合）。
     */
    fun getContextSuggestions(appPackage: String? = null, max: Int = 3): List<String> {
        if (!habitTrackEnabled) return emptyList()

        val period = habitTracker.getPeriodSuggestions(max)
        val app = if (appPackage != null) habitTracker.getAppSuggestions(appPackage, max) else emptyList()

        // 合併去重，App 優先
        return (app + period).distinct().take(max)
    }

    // ─────────────── 生命週期 ───────────────

    /** 換行 / 切 App 時重置上下文 */
    fun resetContext() {
        phrasePredictor.resetContext()
    }

    /** 定期儲存（onFinishInputView 呼叫） */
    fun persist() {
        phrasePredictor.save()
        habitTracker.save()
    }

    /** 定期清理過期資料 */
    fun maintenance() {
        adaptiveDictionary.purgeStale()
    }
}

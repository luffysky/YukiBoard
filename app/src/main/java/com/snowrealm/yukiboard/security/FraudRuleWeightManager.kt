package com.snowrealm.yukiboard.security

import android.content.Context
import android.content.SharedPreferences

/**
 * 動態權重管理器。
 * 預設權重 + 使用者回饋調整 = 最終權重。
 *
 * 回饋規則：
 * - 使用者標記「確實有風險」→ 命中關鍵字 weight +2
 * - 使用者標記「不是風險」→ 命中關鍵字 weight -1（最低 1）
 * - 個人化新關鍵字：使用者標記詐騙時，若文本出現不在規則庫的高頻詞，自動加入個人規則
 */
class FraudRuleWeightManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fraud_weights", Context.MODE_PRIVATE)
    private val feedbackStore = FraudFeedbackStore(context)

    /** 取得某類型的動態權重（預設 + 回饋調整） */
    fun getWeight(fraudType: FraudAIAnalyzer.FraudType, defaultWeight: Int): Int {
        val key = "weight_${fraudType.name}"
        return prefs.getInt(key, defaultWeight)
    }

    /** 取得某關鍵字的動態權重調整（加減值） */
    fun getKeywordAdjustment(keyword: String): Int {
        return prefs.getInt("kw_adj_$keyword", 0)
    }

    /**
     * 處理使用者回饋。
     * @param isFraud true = 確實有風險, false = 不是風險
     * @param result 規則引擎的分析結果
     * @param originalText 原始文本（只用來算 hash，不儲存）
     */
    fun processFeedback(
        isFraud: Boolean,
        result: FraudAIAnalyzer.RuleResult,
        originalText: String
    ) {
        // 1. 儲存回饋記錄
        val primaryType = result.matchedTypes.firstOrNull() ?: FraudAIAnalyzer.FraudType.UNKNOWN
        feedbackStore.saveFeedback(
            text = originalText,
            fraudType = primaryType,
            isFraud = isFraud,
            ruleScore = result.riskScore,
            matchedKeywords = result.matchedKeywords.filter { !it.startsWith("[") }
        )

        // 2. 調整關鍵字權重
        val editor = prefs.edit()
        val normalKeywords = result.matchedKeywords.filter { !it.startsWith("[") }
        for (keyword in normalKeywords) {
            val currentAdj = prefs.getInt("kw_adj_$keyword", 0)
            val newAdj = if (isFraud) {
                (currentAdj + 2).coerceAtMost(20)  // 最多 +20
            } else {
                (currentAdj - 1).coerceAtLeast(-5)  // 最多 -5（不會完全消失）
            }
            editor.putInt("kw_adj_$keyword", newAdj)
        }

        // 3. 調整類型權重
        for (type in result.matchedTypes) {
            val key = "weight_${type.name}"
            val currentWeight = prefs.getInt(key, 0)  // 0 = 未調整過
            val newWeight = if (isFraud) {
                (currentWeight + 1).coerceAtMost(10)
            } else {
                (currentWeight - 1).coerceAtLeast(-3)
            }
            editor.putInt(key, newWeight)
        }

        editor.apply()

        // 4. 個人化關鍵字學習（只在標記為詐騙時）
        if (isFraud) {
            learnNewKeywords(originalText, primaryType.name)
        }
    }

    /** 取得個人化規則的額外分數（全類型加總） */
    fun getPersonalBonus(matchedTypes: List<FraudAIAnalyzer.FraudType>): Int {
        return matchedTypes.sumOf { type ->
            prefs.getInt("weight_${type.name}", 0)
        }
    }

    /**
     * 取得某關鍵字的調整後權重。
     * @param keyword 關鍵字
     * @param defaultWeight 該關鍵字在規則庫中的預設權重
     * @return 調整後的最終權重（最低 1）
     */
    fun getAdjustedWeight(keyword: String, defaultWeight: Float): Float {
        val adjustment = getKeywordAdjustment(keyword)
        // 加上雲端同步的額外調整（如果有的話）
        val syncAdj = prefs.getInt("sync_adj_$keyword", 0)
        return (defaultWeight + adjustment + syncAdj).coerceAtLeast(1f)
    }

    /**
     * 個人化關鍵字學習。
     * 使用者標記為詐騙時，從文本中擷取潛在的新關鍵字（不在現有規則庫中的高頻詐騙用語）。
     * 學到的關鍵字儲存在 personal_keywords_{fraudType} 裡。
     */
    fun learnNewKeywords(text: String, fraudType: String) {
        // 分詞：取 2~6 字的 n-gram（中文沒有空白，用滑動視窗）
        val candidates = mutableSetOf<String>()
        val cleaned = text.replace(Regex("[\\s\\p{P}]"), "")
        for (len in 2..6) {
            for (i in 0..cleaned.length - len) {
                candidates.add(cleaned.substring(i, i + len))
            }
        }

        // 載入現有個人關鍵字
        val key = "personal_keywords_$fraudType"
        val existing = prefs.getString(key, "")?.split(",")?.filter { it.isNotBlank() }?.toMutableSet()
            ?: mutableSetOf()

        // 只保留出現在多次回饋中的新詞（至少 2 次標記同類型後才學習）
        val typeStats = feedbackStore.getFeedbackStats(fraudType)
        if (typeStats.first < 2) return  // 回饋次數不足，暫不學習

        // 簡易篩選：只學 3~5 字的詞（太短太長都不精準）
        val newKeywords = candidates.filter { it.length in 3..5 && it !in existing }
            .take(5)  // 每次最多學 5 個

        if (newKeywords.isNotEmpty()) {
            existing.addAll(newKeywords)
            // 限制個人關鍵字總數（避免無限膨脹）
            val trimmed = existing.toList().takeLast(100)
            prefs.edit().putString(key, trimmed.joinToString(",")).apply()
        }
    }

    /**
     * 取得某類型的個人化關鍵字列表。
     */
    fun getPersonalKeywords(fraudType: String): List<String> {
        val key = "personal_keywords_$fraudType"
        return prefs.getString(key, "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    /** 取得回饋統計 */
    fun getFeedbackStats(): Map<String, Pair<Int, Int>> = feedbackStore.getAllFeedbackStats()

    /** 回饋總筆數 */
    fun totalFeedbackCount(): Int = feedbackStore.totalCount()

    /** 重置所有個人化權重（恢復預設） */
    fun resetAll() {
        prefs.edit().clear().apply()
    }
}

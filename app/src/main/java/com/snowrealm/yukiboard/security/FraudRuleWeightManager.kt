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
    }

    /** 取得個人化規則的額外分數（全類型加總） */
    fun getPersonalBonus(matchedTypes: List<FraudAIAnalyzer.FraudType>): Int {
        return matchedTypes.sumOf { type ->
            prefs.getInt("weight_${type.name}", 0)
        }
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

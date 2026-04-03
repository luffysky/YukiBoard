package com.snowrealm.yukiboard.input.predict

import com.snowrealm.yukiboard.ui.keyboard.KeyboardMode

/**
 * 預測下一個動作介面 — 為 SnowRealmClaw AI 整合預留。
 *
 * MVP 階段先用本地規則引擎（[RuleBasedPredictor]），
 * 之後替換成 Claw AI 推論模型。
 *
 * ## 使用情境
 * - 根據目前輸入內容 + 所在 App + 鍵盤模式，預測使用者下一步動作
 * - 預測結果可用於：快捷鍵提示、自動補全建議、候選排序優化
 */
interface NextActionPredictor {

    /**
     * 預測下一個可能的動作。
     *
     * @param context 目前輸入上下文
     * @return 預測結果列表（按信心分數排序）
     */
    fun predict(context: PredictionContext): List<PredictedAction>

    /** 回報使用者實際採取的動作（用於學習） */
    fun feedback(actualAction: String)
}

/**
 * 預測輸入上下文。
 */
data class PredictionContext(
    /** 目前 composing 文字 */
    val composingText: String = "",
    /** 游標前方文字（最多 100 字元） */
    val textBeforeCursor: String = "",
    /** 目前鍵盤模式 */
    val keyboardMode: KeyboardMode = KeyboardMode.NORMAL,
    /** 前景 App package name */
    val appPackage: String = "",
    /** 最近 N 次按鍵記錄 */
    val recentKeys: List<String> = emptyList()
)

/**
 * 預測動作結果。
 */
data class PredictedAction(
    /** 動作 ID（如 "paste", "undo", "snippet:fn", "candidate:0"） */
    val actionId: String,
    /** 顯示用文字 */
    val displayText: String,
    /** 信心分數 0.0 ~ 1.0 */
    val confidence: Float,
    /** 動作分類 */
    val category: ActionCategory = ActionCategory.OTHER
)

enum class ActionCategory {
    SNIPPET,        // 程式碼片段
    SHORTCUT,       // 快捷鍵
    CANDIDATE,      // 候選詞
    NAVIGATION,     // 游標移動
    OTHER
}

/**
 * MVP 本地規則引擎實作。
 *
 * 基於簡單的模式匹配規則預測下一步動作，
 * 未來替換為 SnowRealmClaw AI 模型。
 */
class RuleBasedPredictor : NextActionPredictor {

    override fun predict(context: PredictionContext): List<PredictedAction> {
        val predictions = mutableListOf<PredictedAction>()

        when (context.keyboardMode) {
            KeyboardMode.DEVELOPER -> {
                val text = context.textBeforeCursor

                // 括號自動配對
                if (text.endsWith("(")) predictions.add(PredictedAction("insert:)", ")", 0.8f, ActionCategory.OTHER))
                if (text.endsWith("{")) predictions.add(PredictedAction("insert:}", "}", 0.8f, ActionCategory.OTHER))
                if (text.endsWith("[")) predictions.add(PredictedAction("insert:]", "]", 0.8f, ActionCategory.OTHER))

                // Snippet 偵測
                val lastWord = text.trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""
                when (lastWord) {
                    "fn", "func" -> predictions.add(PredictedAction("snippet:fn", "→ function(){}", 0.9f, ActionCategory.SNIPPET))
                    "if" -> predictions.add(PredictedAction("snippet:if", "→ if(){}", 0.9f, ActionCategory.SNIPPET))
                    "for" -> predictions.add(PredictedAction("snippet:for", "→ for(){}", 0.9f, ActionCategory.SNIPPET))
                    "class" -> predictions.add(PredictedAction("snippet:class", "→ class{}", 0.85f, ActionCategory.SNIPPET))
                    "try" -> predictions.add(PredictedAction("snippet:try", "→ try/catch", 0.85f, ActionCategory.SNIPPET))
                }

                // 常見操作提示
                if (text.lines().size > 5) predictions.add(PredictedAction("shortcut:fold", "摺疊", 0.5f, ActionCategory.SHORTCUT))
                if (text.length > 200) predictions.add(PredictedAction("shortcut:format", "格式化", 0.4f, ActionCategory.SHORTCUT))
            }

            KeyboardMode.DESIGNER -> {
                predictions.add(PredictedAction("shortcut:undo", "復原", 0.4f, ActionCategory.SHORTCUT))
                predictions.add(PredictedAction("shortcut:group", "群組", 0.3f, ActionCategory.SHORTCUT))
                predictions.add(PredictedAction("shortcut:align", "對齊", 0.25f, ActionCategory.SHORTCUT))
            }

            KeyboardMode.NORMAL -> {
                val text = context.textBeforeCursor
                // 問號結尾 → 可能需要快速回覆
                if (text.trimEnd().endsWith("?") || text.trimEnd().endsWith("？")) {
                    predictions.add(PredictedAction("skill:quick_reply", "快速回覆", 0.6f, ActionCategory.OTHER))
                }
                // 長文 → 摘要
                if (text.length > 100) {
                    predictions.add(PredictedAction("skill:summarize", "摘要", 0.4f, ActionCategory.OTHER))
                }
            }

            KeyboardMode.GAME -> { /* 遊戲模式不預測 */ }
        }

        // App 感知預測
        when {
            context.appPackage.contains("github") || context.appPackage.contains("gitlab") ->
                predictions.add(PredictedAction("skill:commit_message", "Commit", 0.5f, ActionCategory.OTHER))
            context.appPackage.contains("line") || context.appPackage.contains("messenger") || context.appPackage.contains("whatsapp") ->
                predictions.add(PredictedAction("skill:quick_reply", "快速回覆", 0.45f, ActionCategory.OTHER))
        }

        return predictions.sortedByDescending { it.confidence }
    }

    override fun feedback(actualAction: String) {
        // MVP 不學習，未來由 Claw AI 處理
    }
}

/**
 * AI 增強預測器 — 結合本地規則和 Claw AI 意圖預測。
 * 先用本地規則快速回應，高信心場景不呼叫 AI 節省 token。
 */
class ClawEnhancedPredictor(
    private val rulePredictor: RuleBasedPredictor = RuleBasedPredictor()
) : NextActionPredictor {

    /** AI 預測回調（由外部注入 ClawClient） */
    var aiPredictCallback: ((PredictionContext) -> List<PredictedAction>)? = null

    override fun predict(context: PredictionContext): List<PredictedAction> {
        val localResults = rulePredictor.predict(context)

        // 若本地規則已有高信心結果（≥0.8），直接回傳不浪費 token
        if (localResults.any { it.confidence >= 0.8f }) {
            return localResults
        }

        // 嘗試 AI 預測
        val aiResults = try {
            aiPredictCallback?.invoke(context) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        // 合併並去重（AI 結果優先）
        val merged = mutableMapOf<String, PredictedAction>()
        for (action in localResults) merged[action.actionId] = action
        for (action in aiResults) merged[action.actionId] = action

        return merged.values.sortedByDescending { it.confidence }
    }

    override fun feedback(actualAction: String) {
        rulePredictor.feedback(actualAction)
    }
}

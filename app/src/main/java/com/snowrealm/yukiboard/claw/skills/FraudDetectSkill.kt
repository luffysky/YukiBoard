package com.snowrealm.yukiboard.claw.skills

import android.content.Context
import com.snowrealm.yukiboard.claw.ClawInput
import com.snowrealm.yukiboard.claw.ClawOutput
import com.snowrealm.yukiboard.claw.ClawSkill
import com.snowrealm.yukiboard.security.FraudAIAnalyzer

/**
 * 防詐偵測 Skill — 分析輸入框文字或剪貼簿對話是否為詐騙。
 *
 * 先用本地規則引擎快速篩選（0 token），
 * 風險 ≥ 30 時建議使用 AI 深度分析（由 ClawPanelView 處理串流）。
 */
class FraudDetectSkill(private val context: Context) : ClawSkill {
    override val id = "fraud_detect"
    override val name = "防詐偵測"
    override val description = "分析對話內容是否為詐騙"
    override val tokensPerCall = 0  // 本地規則引擎不消耗 token

    private val analyzer = FraudAIAnalyzer(context)

    override suspend fun execute(input: ClawInput): ClawOutput {
        val ruleResult = analyzer.analyzeByRules(input.text)
        val formatted = analyzer.formatRuleResult(ruleResult)

        val suggestions = mutableListOf(formatted)

        if (ruleResult.riskScore >= 30) {
            suggestions.add("💡 點擊「AI 深度分析」取得更詳細的判斷")
        }

        if (ruleResult.riskScore >= 60) {
            suggestions.add("📞 165 反詐騙專線")
        }

        return ClawOutput(
            suggestions = suggestions,
            type = com.snowrealm.yukiboard.claw.OutputType.REPLY,
            tokensUsed = 0
        )
    }
}

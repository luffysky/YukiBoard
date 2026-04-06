package com.snowrealm.yukiboard.security

import android.content.Context
import com.snowrealm.yukiboard.claw.ClawClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

/**
 * 防詐 AI 分析器 — A+B 混合架構。
 *
 * A 層：本地規則引擎（< 1ms，零費用）
 *   - 關鍵字 / 正則比對 5 種詐騙類型
 *   - 回傳風險分數 0~100 + 命中規則
 *
 * B 層：AI 深度分析（Gemini Flash / 其他 Provider）
 *   - 將對話格式化後送 AI 分析
 *   - 串流回傳分析結果
 *
 * 流程：先跑 A 層快速篩選 → 風險 ≥ 30 才呼叫 B 層深度分析
 */
class FraudAIAnalyzer(private val context: Context) {

    // =========================================================================
    // 資料模型
    // =========================================================================

    enum class FraudType(val label: String, val emoji: String) {
        INVESTMENT("投資詐騙", "📈"),
        ROMANCE("感情詐騙", "💔"),
        IMPERSONATION("冒充詐騙", "🎭"),
        SHOPPING("購物詐騙", "🛒"),
        PHISHING("釣魚詐騙", "🎣"),
        GAMBLING("博弈詐騙", "🎰"),
        LOAN("貸款詐騙", "💳"),
        JOB("求職詐騙", "💼"),
        UNKNOWN("疑似詐騙", "⚠️")
    }

    data class RuleResult(
        val riskScore: Int,           // 0~100
        val matchedTypes: List<FraudType>,
        val matchedKeywords: List<String>,
        val summary: String
    )

    data class AnalysisResult(
        val ruleResult: RuleResult,
        val aiAnalysis: String? = null // B 層 AI 分析結果（可能為 null）
    )

    // =========================================================================
    // A 層：本地規則引擎
    // =========================================================================

    private data class Rule(
        val type: FraudType,
        val keywords: List<String>,
        val weight: Int  // 每命中一個關鍵字的加分
    )

    private val rules = listOf(
        // 投資詐騙
        Rule(FraudType.INVESTMENT, listOf(
            "穩賺不賠", "保證獲利", "日收益", "月收益", "年報酬",
            "百分之", "高報酬", "零風險", "被動收入", "財富自由",
            "跟單", "帶操", "老師帶", "內線", "飆股",
            "虛擬貨幣", "加密貨幣", "挖礦", "USDT", "入金",
            "出金", "提領", "本金", "翻倍", "日賺",
            "投資群", "操盤", "基金", "外匯", "保證金"
        ), 8),

        // 感情詐騙
        Rule(FraudType.ROMANCE, listOf(
            "我愛你但", "借我一點", "急需用錢", "住院費",
            "飛機票", "海關扣留", "黃金卡住", "幫我代收",
            "匯款給我", "西聯匯款", "比特幣轉帳", "禮品卡",
            "iTunes卡", "Google Play卡", "手術費", "保釋金",
            "還沒見面", "軍人", "石油平台", "工程師在國外"
        ), 10),

        // 冒充詐騙
        Rule(FraudType.IMPERSONATION, listOf(
            "你的帳戶異常", "涉及洗錢", "配合調查", "監管帳戶",
            "公安局", "檢察官", "法院傳票", "通緝令",
            "解除分期", "ATM操作", "轉帳到安全帳戶",
            "客服人員", "銀行通知", "健保卡遭盜用",
            "欠繳罰款", "包裹違禁品", "護照異常"
        ), 12),

        // 購物詐騙
        Rule(FraudType.SHOPPING, listOf(
            "先付訂金", "限時特價", "只剩最後", "私訊下單",
            "面交可議", "匯款後出貨", "海外代購", "免運費",
            "超低價", "清倉特賣", "工廠直營", "一頁式",
            "貨到付款但先付", "訂單異常", "重複扣款"
        ), 7),

        // 釣魚詐騙
        Rule(FraudType.PHISHING, listOf(
            "點擊連結", "驗證身分", "帳號異常登入",
            "重設密碼", "更新資料", "中獎通知",
            "免費領取", "限時兌換", "短網址",
            "bit.ly", "reurl.cc", "tinyurl",
            "輸入信用卡", "OTP驗證碼", "簡訊驗證"
        ), 9),

        // 博弈詐騙
        Rule(FraudType.GAMBLING, listOf(
            "百家樂", "運彩", "體育投注", "線上賭場",
            "必勝法", "破解程式", "包牌", "代操下注",
            "娛樂城", "返水", "首儲優惠", "洗碼量"
        ), 8),

        // 貸款詐騙
        Rule(FraudType.LOAN, listOf(
            "低利貸款", "免擔保", "免抵押", "當日撥款",
            "先繳手續費", "保證過件", "信用瑕疵可辦",
            "代辦費", "前置利息", "包裝費"
        ), 9),

        // 求職詐騙
        Rule(FraudType.JOB, listOf(
            "日薪上萬", "在家工作月入", "兼職高薪",
            "刷單", "代購", "車手", "收簿",
            "租借帳戶", "先繳保證金", "培訓費",
            "人頭帳戶", "點數代買"
        ), 9)
    )

    /**
     * A 層快速分析 — 本地規則引擎，< 1ms。
     */
    fun analyzeByRules(text: String): RuleResult {
        val lower = text.lowercase()
        var totalScore = 0
        val matchedTypes = mutableSetOf<FraudType>()
        val matchedKeywords = mutableListOf<String>()

        for (rule in rules) {
            var ruleHits = 0
            for (keyword in rule.keywords) {
                if (lower.contains(keyword.lowercase())) {
                    ruleHits++
                    matchedKeywords.add(keyword)
                }
            }
            if (ruleHits > 0) {
                matchedTypes.add(rule.type)
                totalScore += ruleHits * rule.weight
            }
        }

        val riskScore = totalScore.coerceIn(0, 100)

        val summary = when {
            riskScore == 0 -> "未偵測到明顯詐騙特徵"
            riskScore < 30 -> "低風險 — 偵測到少量可疑用語，請留意"
            riskScore < 60 -> "中風險 — 偵測到多個詐騙常見用語，建議提高警覺"
            riskScore < 80 -> "高風險 — 符合常見詐騙模式，強烈建議停止互動"
            else -> "極高風險 — 高度疑似詐騙，請立即停止並撥打 165 反詐騙專線"
        }

        return RuleResult(
            riskScore = riskScore,
            matchedTypes = matchedTypes.toList(),
            matchedKeywords = matchedKeywords,
            summary = summary
        )
    }

    // =========================================================================
    // B 層：AI 深度分析
    // =========================================================================

    private val systemPrompt = """你是台灣反詐騙 AI 助手。分析以下對話內容，判斷是否為詐騙。

請依以下格式回覆：
1. 【風險等級】低/中/高/極高
2. 【詐騙類型】投資/感情/冒充/購物/釣魚/博弈/貸款/求職/無
3. 【分析】用 2-3 句簡短說明為什麼（指出具體可疑之處）
4. 【建議】給使用者的具體行動建議

注意：
- 用繁體中文回覆
- 語氣堅定但不恐嚇
- 如果不是詐騙，也要明確說明
- 提醒 165 反詐騙專線"""

    /**
     * B 層 AI 串流分析。
     * 回傳 Flow<String>，UI 可邊收邊顯示。
     */
    suspend fun analyzeByAI(
        text: String,
        clawClient: ClawClient,
        ruleResult: RuleResult
    ): Flow<String> {
        val formatted = ConversationFormatter.formatRaw(text)

        val ruleHint = if (ruleResult.matchedTypes.isNotEmpty()) {
            "\n\n[本地規則引擎預判] 風險分數: ${ruleResult.riskScore}/100, " +
            "疑似類型: ${ruleResult.matchedTypes.joinToString { it.label }}, " +
            "命中關鍵字: ${ruleResult.matchedKeywords.take(5).joinToString()}"
        } else ""

        val prompt = "$formatted$ruleHint"

        return try {
            clawClient.streamSkill(prompt, systemPrompt)
        } catch (_: Exception) {
            emptyFlow()
        }
    }

    /**
     * 完整分析流程（A+B）。
     * 先跑規則引擎，風險 ≥ 30 才呼叫 AI。
     */
    suspend fun analyze(
        text: String,
        clawClient: ClawClient?
    ): AnalysisResult {
        val ruleResult = withContext(Dispatchers.Default) {
            analyzeByRules(text)
        }

        // 風險低且沒有 AI client → 只回傳規則結果
        if (ruleResult.riskScore < 30 || clawClient == null || !clawClient.isAvailable()) {
            return AnalysisResult(ruleResult = ruleResult)
        }

        // 風險 ≥ 30 → 呼叫 AI 深度分析（非串流版，收集完整結果）
        val aiResult = StringBuilder()
        try {
            analyzeByAI(text, clawClient, ruleResult).collect { chunk ->
                aiResult.append(chunk)
            }
        } catch (_: Exception) { }

        return AnalysisResult(
            ruleResult = ruleResult,
            aiAnalysis = aiResult.toString().ifBlank { null }
        )
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    /**
     * 取得風險等級的顏色（ARGB Int）
     */
    fun getRiskColor(score: Int): Int = when {
        score < 30 -> 0xFF4CAF50.toInt()  // 綠色 — 低風險
        score < 60 -> 0xFFFF9800.toInt()  // 橙色 — 中風險
        score < 80 -> 0xFFFF5722.toInt()  // 深橙 — 高風險
        else       -> 0xFFF44336.toInt()  // 紅色 — 極高風險
    }

    /**
     * 取得風險等級 emoji
     */
    fun getRiskEmoji(score: Int): String = when {
        score < 30 -> "✅"
        score < 60 -> "⚠️"
        score < 80 -> "🚨"
        else       -> "🛑"
    }

    /**
     * 格式化規則結果為使用者可讀文本
     */
    fun formatRuleResult(result: RuleResult): String {
        val sb = StringBuilder()
        sb.appendLine("${getRiskEmoji(result.riskScore)} ${result.summary}")
        sb.appendLine("風險分數：${result.riskScore}/100")

        if (result.matchedTypes.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("偵測到的詐騙類型：")
            for (type in result.matchedTypes) {
                sb.appendLine("  ${type.emoji} ${type.label}")
            }
        }

        if (result.matchedKeywords.isNotEmpty()) {
            sb.appendLine()
            sb.append("可疑關鍵字：${result.matchedKeywords.take(8).joinToString("、")}")
            if (result.matchedKeywords.size > 8) sb.append("…")
        }

        if (result.riskScore >= 60) {
            sb.appendLine()
            sb.appendLine()
            sb.appendLine("📞 如有疑慮請撥打 165 反詐騙專線")
        }

        return sb.toString().trim()
    }
}

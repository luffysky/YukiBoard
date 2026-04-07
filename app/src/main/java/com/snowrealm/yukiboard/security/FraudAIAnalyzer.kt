package com.snowrealm.yukiboard.security

import android.content.Context
import com.snowrealm.yukiboard.claw.ClawClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

/**
 * 防詐 AI 分析器 — 三層混合架構。
 *
 * Layer 0：TFLite 本地模型（50~80ms，離線，免費）
 *   - bert-base-chinese fine-tuned 詐騙偵測
 *   - 高信心度直接回傳，無需進一步分析
 *
 * Layer 1：本地規則引擎（< 1ms，零費用）
 *   - 關鍵字 / 正則比對 8 種詐騙類型
 *   - 回傳風險分數 0~100 + 命中規則
 *
 * Layer 2：AI 深度分析（Gemini Flash / 其他 Provider）
 *   - 將對話格式化後送 AI 分析
 *   - 串流回傳分析結果
 *
 * 流程：先跑 Layer 0 → 信心不足跑 Layer 1 → 風險 ≥ 30 且線上可用才呼叫 Layer 2
 */
class FraudAIAnalyzer(private val context: Context) {

    /** 動態權重管理器（個人化回饋調整） */
    val weightManager by lazy { FraudRuleWeightManager(context) }

    /** TFLite 本地模型（Layer 0） */
    var localModel: FraudLocalModel? = null

    /** 政府公開防詐資料同步器 */
    val openDataSync by lazy { FraudOpenDataSync(context) }

    // =========================================================================
    // 資料模型
    // =========================================================================

    enum class FraudType(val label: String, val emoji: String) {
        INVESTMENT("投資風險", "📈"),
        ROMANCE("感情風險", "💔"),
        IMPERSONATION("冒充風險", "🎭"),
        SHOPPING("購物風險", "🛒"),
        PHISHING("釣魚風險", "🎣"),
        GAMBLING("博弈風險", "🎰"),
        LOAN("貸款風險", "💳"),
        JOB("求職風險", "💼"),
        UNKNOWN("可疑模式", "⚠️")
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

    // =========================================================================
    // 組合模式 — 多個關鍵字同時出現 = 高風險
    // =========================================================================

    private data class ComboPattern(
        val type: FraudType,
        val requiredAny: List<List<String>>,  // 每組至少命中一個
        val bonusScore: Int,
        val label: String
    )

    /** 組合模式：多組關鍵字同時出現才觸發，分數遠高於單一關鍵字 */
    private val comboPatterns = listOf(
        // 「老師/導師/助理」+「投資/獲利/入金/群組」= 典型投資詐騙
        ComboPattern(FraudType.INVESTMENT,
            listOf(
                listOf("老師", "導師", "助理", "分析師", "顧問", "學長姐"),
                listOf("投資", "獲利", "入金", "群", "操盤", "帶單", "跟單", "報酬")
            ), 55, "老師帶投資"),

        // 「保證/穩賺」+「獲利/報酬/收益」= 不實承諾
        ComboPattern(FraudType.INVESTMENT,
            listOf(
                listOf("保證", "穩賺", "零風險", "絕對", "一定"),
                listOf("獲利", "報酬", "收益", "賺", "翻倍", "回本")
            ), 50, "保證獲利話術"),

        // 「加LINE/加我/私訊」+「投資/賺錢/群組」= 引流
        ComboPattern(FraudType.INVESTMENT,
            listOf(
                listOf("加LINE", "加我", "私訊", "加好友", "加入", "進群"),
                listOf("投資", "賺錢", "獲利", "群組", "投資群", "操盤群")
            ), 45, "投資引流"),

        // 「點擊/登入」+「連結/網址/URL」+「帳號/密碼/驗證」= 釣魚
        ComboPattern(FraudType.PHISHING,
            listOf(
                listOf("點擊", "登入", "打開", "進入", "前往"),
                listOf("連結", "網址", "link", "URL", "頁面")
            ), 45, "釣魚導向"),

        // 「匯款/轉帳」+「安全帳戶/監管/凍結」= 冒充公務員
        ComboPattern(FraudType.IMPERSONATION,
            listOf(
                listOf("匯款", "轉帳", "存入", "ATM"),
                listOf("安全帳戶", "監管", "凍結", "法院", "檢察")
            ), 60, "冒充公務員"),

        // 「代購/下單」+「先付/訂金/匯款」= 購物詐騙
        ComboPattern(FraudType.SHOPPING,
            listOf(
                listOf("代購", "下單", "購買", "搶購"),
                listOf("先付", "訂金", "匯款", "轉帳", "私下交易")
            ), 40, "購物先付款"),

        // 「借錢/急用」+「愛/寶貝/親愛的」= 感情詐騙
        ComboPattern(FraudType.ROMANCE,
            listOf(
                listOf("借", "急用", "周轉", "手術", "住院"),
                listOf("愛你", "寶貝", "親愛的", "老婆", "老公", "想你")
            ), 50, "感情借款"),

        // 「高薪/日薪」+「兼職/在家/簡單」= 求職詐騙
        ComboPattern(FraudType.JOB,
            listOf(
                listOf("高薪", "日薪", "月入", "輕鬆賺"),
                listOf("兼職", "在家", "簡單", "不用經驗", "手機就能")
            ), 45, "高薪兼職話術")
    )

    // =========================================================================
    // URL 風險偵測
    // =========================================================================

    /** 高風險短網址域名 */
    private val suspiciousShortDomains = setOf(
        "bit.ly", "tinyurl.com", "reurl.cc", "pse.is",
        "lihi.cc", "lihi1.cc", "lihi2.cc", "lihi3.cc",
        "is.gd", "v.gd", "shorturl.at", "cutt.ly",
        "rb.gy", "t.ly", "s.id"
    )

    /** 高風險 TLD */
    private val suspiciousTLDs = setOf(
        ".xyz", ".top", ".club", ".work", ".buzz",
        ".surf", ".monster", ".icu", ".fun", ".site",
        ".online", ".click", ".link", ".rest", ".cam",
        ".gq", ".ml", ".tk", ".cf", ".ga"
    )

    /** 假冒知名網站的域名模式 */
    private val impersonationDomainPatterns = listOf(
        Regex("""(?i)l[i1]ne[\-.]"""),           // l1ne, liine (假 LINE)
        Regex("""(?i)go+gle(?!\.com)"""),         // gooogle (假 Google)
        Regex("""(?i)facebo+k(?!\.com)"""),       // facebok (假 Facebook)
        Regex("""(?i)paypa[l1](?!\.com)"""),      // paypa1 (假 PayPal)
        Regex("""(?i)app[l1]e(?!\.com)"""),       // app1e (假 Apple)
        Regex("""(?i)amaz[o0]n(?!\.com)"""),      // amaz0n (假 Amazon)
        Regex("""(?i)micr[o0]s[o0]ft(?!\.com)"""), // micr0soft
        Regex("""(?i)instag[r]?am(?!\.com)"""),   // instagam
    )

    /** URL 提取正則 */
    private val urlPattern = Regex(
        """(?i)(?:https?://|www\.)[^\s<>\[\](){}「」『』，。！？、；：\u200B-\u200D\uFEFF]{4,200}"""
    )

    data class UrlRisk(
        val url: String,
        val score: Int,
        val reasons: List<String>
    )

    /**
     * 掃描文本中的 URL 並評估風險。
     */
    fun scanUrls(text: String): List<UrlRisk> {
        val urls = urlPattern.findAll(text).map { it.value }.toList()
        if (urls.isEmpty()) return emptyList()

        return urls.map { url -> analyzeUrl(url) }
    }

    private fun analyzeUrl(url: String): UrlRisk {
        val lower = url.lowercase()
        var score = 0
        val reasons = mutableListOf<String>()

        // 0. 政府公開黑名單比對（MODA + 165 + OpenPhish）
        if (openDataSync.isDomainBlocked(url)) {
            score += 80
            reasons.add("此網域已被政府列入詐騙黑名單")
        }

        // 1. 短網址（高風險，因為隱藏真實目的地）
        for (domain in suspiciousShortDomains) {
            if (lower.contains(domain)) {
                score += 25
                reasons.add("短網址（$domain）隱藏真實網址")
                break
            }
        }

        // 2. 可疑 TLD
        for (tld in suspiciousTLDs) {
            if (lower.contains(tld)) {
                score += 20
                reasons.add("可疑域名後綴（$tld）")
                break
            }
        }

        // 3. 假冒知名網站
        for (pattern in impersonationDomainPatterns) {
            if (pattern.containsMatchIn(lower)) {
                score += 40
                reasons.add("疑似仿冒知名網站")
                break
            }
        }

        // 4. IP 位址直接存取（非正規網站）
        if (Regex("""https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").containsMatchIn(lower)) {
            score += 30
            reasons.add("使用 IP 位址而非域名")
        }

        // 5. 可疑路徑關鍵字
        val pathKeywords = listOf("login", "signin", "verify", "confirm", "secure", "account", "update", "wallet", "withdraw")
        for (kw in pathKeywords) {
            if (lower.contains(kw)) {
                score += 10
                reasons.add("網址包含可疑路徑（$kw）")
                break
            }
        }

        // 6. 非 HTTPS
        if (lower.startsWith("http://") && !lower.startsWith("https://")) {
            score += 10
            reasons.add("未使用加密連線（HTTP）")
        }

        // 7. 超長子域名（常見於釣魚）
        val domainPart = lower.removePrefix("https://").removePrefix("http://").split("/").firstOrNull() ?: ""
        if (domainPart.count { it == '.' } >= 4) {
            score += 15
            reasons.add("過多子域名層級")
        }

        // 8. 包含 @ 符號（嘗試迷惑使用者）
        if (url.contains("@")) {
            score += 30
            reasons.add("網址包含 @ 符號（可能是偽造）")
        }

        if (reasons.isEmpty()) {
            reasons.add("未偵測到明顯風險")
        }

        return UrlRisk(url = url, score = score.coerceIn(0, 100), reasons = reasons)
    }

    /**
     * A 層快速分析 — 本地規則引擎，< 1ms。
     * 包含：單一關鍵字比對 + 組合模式加分 + URL 風險掃描。
     */
    fun analyzeByRules(text: String): RuleResult {
        val lower = text.lowercase()
        var totalScore = 0
        val matchedTypes = mutableSetOf<FraudType>()
        val matchedKeywords = mutableListOf<String>()

        // ── 第一層：單一關鍵字（含個人化權重調整） ──
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
                // 動態權重：預設 + 個人回饋調整
                val dynamicWeight = rule.weight + weightManager.getKeywordAdjustment(rule.keywords.first())
                val effectiveWeight = dynamicWeight.coerceAtLeast(1)
                val baseScore = ruleHits * effectiveWeight
                val comboBonus = if (ruleHits >= 3) ruleHits * 5 else 0
                totalScore += baseScore + comboBonus
            }
        }

        // ── 第二層：組合模式（最關鍵的改進） ──
        for (combo in comboPatterns) {
            val allGroupsHit = combo.requiredAny.all { group ->
                group.any { keyword -> lower.contains(keyword.lowercase()) }
            }
            if (allGroupsHit) {
                totalScore += combo.bonusScore
                matchedTypes.add(combo.type)
                matchedKeywords.add("[組合] ${combo.label}")
            }
        }

        // ── 第三層：URL 風險掃描 ──
        val urlRisks = scanUrls(text)
        if (urlRisks.isNotEmpty()) {
            val maxUrlScore = urlRisks.maxOf { it.score }
            totalScore += maxUrlScore
            if (maxUrlScore > 0) {
                matchedTypes.add(FraudType.PHISHING)
                for (ur in urlRisks.filter { it.score > 0 }) {
                    matchedKeywords.add("[連結] ${ur.reasons.first()}")
                }
            }
        }

        // ── 第四層：情境加分 ──
        // 有 URL + 有任何詐騙關鍵字 → 額外加分
        if (urlRisks.any { it.score > 0 } && matchedTypes.size > 1) {
            totalScore += 15  // 連結+話術組合更危險
            matchedKeywords.add("[情境] 可疑連結搭配詐騙話術")
        }
        // 出現「不要告訴」「不要跟別人說」「保密」→ 加分
        val secrecyKeywords = listOf("不要告訴", "不要跟別人說", "保密", "別讓人知道", "先不要說", "私底下")
        for (sk in secrecyKeywords) {
            if (lower.contains(sk)) {
                totalScore += 20
                matchedKeywords.add("[情境] 要求保密")
                break
            }
        }
        // 出現「趕快/馬上/立刻/限時」→ 施壓策略加分
        val urgencyKeywords = listOf("趕快", "馬上", "立刻", "限時", "最後機會", "名額有限", "錯過就沒了", "倒數")
        for (uk in urgencyKeywords) {
            if (lower.contains(uk)) {
                totalScore += 10
                matchedKeywords.add("[情境] 時間壓力話術")
                break
            }
        }

        // 個人化回饋加分（類型層級）
        totalScore += weightManager.getPersonalBonus(matchedTypes.toList())

        val riskScore = totalScore.coerceIn(0, 100)

        val summary = when {
            riskScore == 0 -> "目前看起來沒問題"
            riskScore < 30 -> "有一些常見的可疑用語，留意一下就好"
            riskScore < 60 -> "雪凜覺得這段對話有些地方需要注意，建議多確認一下"
            riskScore < 80 -> "這段對話的用語跟常見的風險模式蠻像的，請謹慎判斷"
            else -> "雪凜非常擔心！這段對話跟已知的風險模式高度吻合，建議先暫停，可撥 165 諮詢"
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

    private val systemPrompt = """你是雪凜鍵盤的安全顧問。分析以下對話內容，評估是否有風險。

請依以下格式回覆：
1. 【風險等級】低/中/高/極高
2. 【可能類型】投資/感情/冒充/購物/釣魚/博弈/貸款/求職/無
3. 【分析】用 2-3 句簡短說明你注意到的地方（客觀描述可疑之處，不要直接斷定是詐騙）
4. 【建議】給使用者的行動建議（委婉、尊重使用者判斷）

注意：
- 用繁體中文回覆
- 語氣溫和關心，像朋友提醒而非警察辦案
- 不要直接說「這是詐騙」，改用「這段對話有些地方需要注意」
- 如果風險不高，也要肯定使用者的判斷
- 高風險時可以建議撥 165 諮詢（不是報案，是諮詢）"""

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
     * 完整分析流程（三層架構）。
     *
     * Layer 0: TFLite 模型（高信心度 ≥ 0.85 直接回傳）
     * Layer 1: 規則引擎
     * Layer 2: AI 深度分析（風險 ≥ 30 且線上可用）
     */
    suspend fun analyze(
        text: String,
        clawClient: ClawClient?
    ): AnalysisResult {
        // ── Layer 0: TFLite 本地模型 ──
        val modelResult = localModel?.predict(text)
        if (modelResult != null && modelResult.confidence >= 0.85f) {
            // 高信心度：直接用模型結果建構 RuleResult
            val ruleResult = RuleResult(
                riskScore = (modelResult.fraudProbability * 100).toInt().coerceIn(0, 100),
                matchedTypes = listOfNotNull(modelResult.fraudType),
                matchedKeywords = listOf("[模型] ${modelResult.predictedLabel} (${String.format("%.1f%%", modelResult.confidence * 100)})"),
                summary = when {
                    modelResult.fraudProbability < 0.3f -> "模型判斷：目前看起來沒問題"
                    modelResult.fraudProbability < 0.6f -> "模型偵測到一些可疑模式，留意一下"
                    modelResult.fraudProbability < 0.8f -> "模型偵測到高風險模式，請謹慎判斷"
                    else -> "模型偵測到極高風險！建議先暫停，可撥 165 諮詢"
                }
            )
            return AnalysisResult(ruleResult = ruleResult)
        }

        // ── Layer 1: 規則引擎 ──
        val ruleResult = withContext(Dispatchers.Default) {
            analyzeByRules(text)
        }

        // 如果模型有結果但信心不足，合併模型分數到規則結果
        if (modelResult != null && modelResult.fraudProbability > 0.3f) {
            val modelBonus = (modelResult.fraudProbability * 20).toInt()
            val enhancedResult = ruleResult.copy(
                riskScore = (ruleResult.riskScore + modelBonus).coerceIn(0, 100),
                matchedKeywords = ruleResult.matchedKeywords +
                    "[模型] ${modelResult.predictedLabel} (${String.format("%.1f%%", modelResult.confidence * 100)})"
            )

            // 風險低且沒有 AI client → 只回傳增強結果
            if (enhancedResult.riskScore < 30 || clawClient == null || !clawClient.isAvailable()) {
                return AnalysisResult(ruleResult = enhancedResult)
            }

            // ── Layer 2: AI 深度分析 ──
            val aiResult = StringBuilder()
            try {
                analyzeByAI(text, clawClient, enhancedResult).collect { chunk ->
                    aiResult.append(chunk)
                }
            } catch (_: Exception) { }

            return AnalysisResult(
                ruleResult = enhancedResult,
                aiAnalysis = aiResult.toString().ifBlank { null }
            )
        }

        // 風險低且沒有 AI client → 只回傳規則結果
        if (ruleResult.riskScore < 30 || clawClient == null || !clawClient.isAvailable()) {
            return AnalysisResult(ruleResult = ruleResult)
        }

        // ── Layer 2: AI 深度分析 ──
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
            val normalKeywords = result.matchedKeywords.filter { !it.startsWith("[") }
            val comboKeywords = result.matchedKeywords.filter { it.startsWith("[組合]") }
            val contextKeywords = result.matchedKeywords.filter { it.startsWith("[情境]") }
            val urlKeywords = result.matchedKeywords.filter { it.startsWith("[連結]") }

            if (normalKeywords.isNotEmpty()) {
                sb.append("可疑關鍵字：${normalKeywords.take(8).joinToString("、")}")
                if (normalKeywords.size > 8) sb.append("…")
                sb.appendLine()
            }
            if (comboKeywords.isNotEmpty()) {
                sb.appendLine("⚡ 組合模式命中：${comboKeywords.joinToString("、") { it.removePrefix("[組合] ") }}")
            }
            if (urlKeywords.isNotEmpty()) {
                sb.appendLine("🔗 連結風險：")
                urlKeywords.forEach { sb.appendLine("  ${it.removePrefix("[連結] ")}") }
            }
            if (contextKeywords.isNotEmpty()) {
                sb.appendLine("🎯 ${contextKeywords.joinToString("、") { it.removePrefix("[情境] ") }}")
            }
        }

        if (result.riskScore >= 60) {
            sb.appendLine()
            sb.appendLine("📞 如有疑慮請撥打 165 反詐騙專線")
        }

        return sb.toString().trim()
    }
}

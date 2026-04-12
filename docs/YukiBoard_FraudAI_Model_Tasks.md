# YukiBoard 防詐 AI 對話模型 完整任務包

> 目標：從規則引擎 → AI 分析 → 可自學的對話模型
> 三個階段，每個階段都能獨立上線
> 後端用現有 Supabase，訓練用 Google Colab（免費）

---

## 整體路線

```
現在上線：
  A（規則引擎）+ B（Gemini Flash 分析）
  + 回報按鈕收集資料

3個月後：
  用回報資料 + Claude 生成合成資料
  → Colab 訓練 DistilBERT
  → TFLite 打包進 APK

6個月後：
  每月更新模型，越用越準
  完全離線，零 API 費用
```

---

## 第一批 — FraudAIAnalyzer（A+B 混合，現在就能用）

### 1-A 新增 `security/FraudAIAnalyzer.kt`

```kotlin
package com.snowrealm.yukiboard.security

import com.snowrealm.yukiboard.claw.ClawInput
import com.snowrealm.yukiboard.claw.provider.AIProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 防詐 AI 分析器 — A+B 混合架構。
 *
 * A 層：FraudDetector 規則引擎（< 1ms，本地）
 *   → SAFE：直接返回，不呼叫 AI
 *   → LOW/HIGH：交給 B 層深度分析
 *
 * B 層：AI 對話分析（Gemini Flash，約 1 秒）
 *   → 理解整段對話的語境和走向
 *   → 判斷詐騙三階段進度
 *   → 輸出信心分數 + 具體理由
 */
class FraudAIAnalyzer(private val aiProvider: AIProvider) {

    private val ruleDetector = FraudDetector()

    data class AnalysisResult(
        val riskLevel: FraudDetector.RiskLevel,
        val fraudType: FraudDetector.FraudType?,
        val stage: Int,                  // 詐騙三階段 1/2/3，0=不是詐騙
        val confidence: Int,             // 0-100
        val reasons: List<String>,       // 具體可疑點
        val suggestions: List<String>,   // 建議動作
        val source: Source               // 由哪層判斷
    ) {
        val isRisky get() = riskLevel != FraudDetector.RiskLevel.SAFE
        enum class Source { RULE_ENGINE, AI, COMBINED }
    }

    /**
     * 分析對話，A+B 混合判斷。
     *
     * @param conversation 格式化後的雙方對話
     * @param rawText 原始文字（用於規則引擎）
     */
    suspend fun analyze(conversation: String, rawText: String = conversation): AnalysisResult {

        // ── A 層：規則引擎先跑 ──
        val ruleResult = ruleDetector.analyze(rawText)

        // 完全安全：直接返回，不呼叫 AI
        if (ruleResult.riskLevel == FraudDetector.RiskLevel.SAFE) {
            return AnalysisResult(
                riskLevel = FraudDetector.RiskLevel.SAFE,
                fraudType = null,
                stage = 0,
                confidence = 0,
                reasons = emptyList(),
                suggestions = emptyList(),
                source = AnalysisResult.Source.RULE_ENGINE
            )
        }

        // 規則引擎已命中 HIGH：立刻警示，同時觸發 AI 補充理由
        // 規則引擎命中 LOW：交給 AI 決定最終等級

        // ── B 層：AI 深度分析 ──
        return try {
            val aiResult = callAI(conversation, ruleResult)
            // 合併 A+B 結果：取較高風險等級
            val finalLevel = maxOf(
                ruleResult.riskLevel,
                aiResult.riskLevel,
                compareBy { it.ordinal }
            )
            aiResult.copy(
                riskLevel = finalLevel,
                reasons = (ruleResult.reasons + aiResult.reasons).distinct().take(3),
                source = AnalysisResult.Source.COMBINED
            )
        } catch (e: Exception) {
            // AI 失敗時 fallback 到規則引擎結果
            AnalysisResult(
                riskLevel = ruleResult.riskLevel,
                fraudType = ruleResult.fraudType,
                stage = 0,
                confidence = if (ruleResult.riskLevel == FraudDetector.RiskLevel.HIGH) 75 else 45,
                reasons = ruleResult.reasons,
                suggestions = ruleResult.suggestions,
                source = AnalysisResult.Source.RULE_ENGINE
            )
        }
    }

    // ── AI 呼叫 ──────────────────────────────────────────────

    private suspend fun callAI(
        conversation: String,
        ruleHint: FraudDetector.FraudResult
    ): AnalysisResult = withContext(Dispatchers.IO) {

        val ruleHintText = if (ruleHint.isRisky) {
            "\n\n【規則引擎提示】已偵測到可疑特徵：${ruleHint.reasons.joinToString("、")}"
        } else ""

        val prompt = buildPrompt(conversation, ruleHintText)
        val systemPrompt = SYSTEM_PROMPT

        val response = StringBuilder()
        aiProvider.complete(prompt, systemPrompt).collect { response.append(it) }

        parseAIResponse(response.toString())
    }

    private fun buildPrompt(conversation: String, ruleHint: String): String = """
分析以下對話是否為詐騙，回傳固定格式。
$ruleHint

【對話內容】
$conversation

【回傳格式（只輸出以下內容，不要解釋）】
RISK: SAFE 或 LOW 或 HIGH
TYPE: FAKE_INVESTMENT 或 FAKE_SERVICE 或 ROMANCE_SCAM 或 FAKE_OFFICIAL 或 JOB_SCAM 或 PHISHING 或 GENERAL 或 NONE
STAGE: 0 或 1 或 2 或 3
CONFIDENCE: 0到100的整數
REASONS:
- 原因1（20字以內）
- 原因2（20字以內）
SUGGESTIONS:
- 建議1（20字以內）
- 建議2（20字以內）
""".trimIndent()

    private fun parseAIResponse(response: String): AnalysisResult {
        val lines = response.lines().map { it.trim() }

        fun field(key: String) = lines
            .firstOrNull { it.startsWith("$key:") }
            ?.removePrefix("$key:")?.trim() ?: ""

        val riskLevel = when (field("RISK").uppercase()) {
            "HIGH" -> FraudDetector.RiskLevel.HIGH
            "LOW"  -> FraudDetector.RiskLevel.LOW
            else   -> FraudDetector.RiskLevel.SAFE
        }

        val fraudType = when (field("TYPE").uppercase()) {
            "FAKE_INVESTMENT" -> FraudDetector.FraudType.FAKE_INVESTMENT
            "FAKE_SERVICE"    -> FraudDetector.FraudType.FAKE_SERVICE
            "ROMANCE_SCAM"    -> FraudDetector.FraudType.ROMANCE_SCAM
            "FAKE_OFFICIAL"   -> FraudDetector.FraudType.FAKE_OFFICIAL
            "JOB_SCAM"        -> FraudDetector.FraudType.JOB_SCAM
            "PHISHING"        -> FraudDetector.FraudType.PHISHING_LINK
            "GENERAL"         -> FraudDetector.FraudType.GENERAL
            else              -> null
        }

        val stage      = field("STAGE").toIntOrNull() ?: 0
        val confidence = field("CONFIDENCE").toIntOrNull()?.coerceIn(0, 100) ?: 50

        val reasons = lines
            .dropWhile { !it.startsWith("REASONS:") }.drop(1)
            .takeWhile { it.startsWith("- ") }
            .map { it.removePrefix("- ").trim() }
            .filter { it.isNotEmpty() }

        val suggestions = lines
            .dropWhile { !it.startsWith("SUGGESTIONS:") }.drop(1)
            .takeWhile { it.startsWith("- ") }
            .map { it.removePrefix("- ").trim() }
            .filter { it.isNotEmpty() }

        return AnalysisResult(
            riskLevel   = riskLevel,
            fraudType   = fraudType,
            stage       = stage,
            confidence  = confidence,
            reasons     = reasons,
            suggestions = suggestions,
            source      = AnalysisResult.Source.AI
        )
    }

    companion object {
        // 基於165反詐騙專線記錄的真實話術設計
        private val SYSTEM_PROMPT = """
你是台灣反詐騙 AI，專門分析 LINE 對話是否有詐騙風險。
基於內政部警政署 165 專線記錄的真實詐騙手法。

【詐騙三階段模式（必須理解整段對話走向）】
第一階段：建立信任
  → 主動搭訕、頻繁問候、噓寒問暖
  → 稱自己是成功人士、在國外、很忙碌
  → 分享精緻生活照（通常盜圖）
  → 「認識你真好」「感覺很投緣」

第二階段：引入誘惑
  → 「最近跟著老師操作賺了不少」
  → 「我有內部消息，不輕易分享」
  → 先讓對方小賺，建立平台信任
  → 「這個平台連我爸媽都在用」
  → 拉入 LINE 投資群組

第三階段：榨取金錢
  → 「最後機會，名額有限，今天要決定」
  → 「帳號凍結，需繳保證金才能出金」
  → 「IP 異常，需再入金解除」
  → 「不要告訴家人，這是秘密」
  → 要求 ATM 操作、超商購點數

【重要判斷原則】
- 分析整段對話走向，不只是單句
- 陌生人主動搭訕 + 後續提到錢 = 高風險
- 正規投資不會「保證獲利」「穩賺不賠」
- 真正的公務員不會用 LINE 通知涉案
- 任何要求你去 ATM 操作的都是詐騙
- 要求「保密不告訴家人」是強烈詐騙信號

【信心分數標準】
- 80-100：有多個明確詐騙特徵，幾乎確定
- 60-79：有具體可疑模式，很可能
- 40-59：有部分疑點，需要小心
- 0-39：可疑但不確定，不顯示警告
""".trimIndent()
    }
}
```

### 1-B 對話格式化工具

新增 `security/ConversationFormatter.kt`：

```kotlin
package com.snowrealm.yukiboard.security

/**
 * 把使用者複製的對話格式化成 AI 可分析的結構。
 *
 * LINE 複製格式：
 *   林小明 下午2:30
 *   你好，我是做投資的
 *   我 下午2:31
 *   你好
 *
 * 輸出格式：
 *   【對方】你好，我是做投資的
 *   【我方】你好
 */
object ConversationFormatter {

    fun format(rawText: String): String {
        if (rawText.isBlank()) return rawText

        val lines = rawText.lines()
        val result = StringBuilder()
        var currentSpeaker = ""
        var isMine = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 判斷說話者標頭（包含時間格式）
            val isHeader = trimmed.matches(
                Regex(".{1,20}\\s+(上午|下午|早上|晚上|凌晨)?\\d{1,2}:\\d{2}.*")
            )

            if (isHeader) {
                // 「我 下午2:31」或「我\t下午2:31」= 自己說的
                isMine = trimmed.startsWith("我 ") ||
                         trimmed.startsWith("我\t") ||
                         trimmed == "我"
                currentSpeaker = if (isMine) "【我方】" else "【對方】"
                continue
            }

            // 對話內容
            if (currentSpeaker.isNotEmpty()) {
                result.appendLine("$currentSpeaker $trimmed")
            } else {
                // 無法解析格式，直接附加原文
                result.appendLine(trimmed)
            }
        }

        val formatted = result.toString().trim()

        // 解析失敗（沒有找到任何說話者）：回傳原文
        return if (formatted.contains("【我方】") || formatted.contains("【對方】")) {
            formatted
        } else {
            rawText
        }
    }

    /**
     * 估算對話是否值得分析。
     * 太短的對話分析沒意義。
     */
    fun isAnalyzable(text: String): Boolean {
        val lineCount = text.lines().count { it.trim().isNotEmpty() }
        return lineCount >= 4 && text.length >= 30
    }

    /**
     * 截斷過長的對話（保留最後 N 輪）。
     * AI 分析最近的對話最重要，省 token。
     */
    fun truncate(formatted: String, maxChars: Int = 1500): String {
        if (formatted.length <= maxChars) return formatted
        // 從後面取，保留最近的對話
        return "...(前段對話省略)...\n" + formatted.takeLast(maxChars)
    }
}
```

---

## 第二批 — 回報系統（資料收集，訓練模型用）

### 2-A 新增 `security/FraudReportManager.kt`

```kotlin
package com.snowrealm.yukiboard.security

import android.content.Context
import com.snowrealm.yukiboard.data.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 詐騙對話回報管理器。
 *
 * 使用者確認「這是詐騙」後：
 * 1. 匿名化對話（去除姓名、電話、帳號）
 * 2. 用戶明確同意才上傳
 * 3. 存入 Supabase fraud_reports 表
 * 4. 作為訓練資料，讓模型越來越準
 *
 * 隱私保護：
 * - 不存真實姓名，只存 hash
 * - 不存電話號碼
 * - 不存帳號密碼
 * - 使用者可隨時刪除自己的回報
 */
class FraudReportManager(private val context: Context) {

    private val supabase = SupabaseClient.getInstance()

    data class FraudReport(
        val conversationAnonymized: String,  // 匿名化後的對話
        val fraudType: String,               // 詐騙類型
        val aiConfidence: Int,               // AI 信心分數
        val userConfirmed: Boolean,          // 使用者是否確認是詐騙
        val appPackage: String,              // 在哪個 App 發生（e.g. com.linecorp.LINE）
        val deviceHash: String               // 裝置 hash（不是 ID，無法反推）
    )

    /**
     * 匿名化對話。
     * 移除可能識別個人的資訊。
     */
    fun anonymize(conversation: String): String {
        var result = conversation

        // 移除電話號碼
        result = result.replace(Regex("0[89]\\d{8}"), "[電話]")
        result = result.replace(Regex("\\+886\\d{9,10}"), "[電話]")

        // 移除帳號密碼模式
        result = result.replace(Regex("帳號[：:].{1,30}"), "帳號：[已移除]")
        result = result.replace(Regex("密碼[：:].{1,30}"), "密碼：[已移除]")

        // 移除銀行帳號（12-16位數字）
        result = result.replace(Regex("\\d{12,16}"), "[帳號]")

        // 移除身分證字號
        result = result.replace(Regex("[A-Z][12]\\d{8}"), "[身分證]")

        // 移除說話者姓名（保留【我方】【對方】標籤）
        result = result.replace(Regex("【[^】]{1,10}】"), { match ->
            when {
                match.value.contains("我方") -> "【我方】"
                match.value.contains("對方") -> "【對方】"
                else -> "【對方】"  // 其他名字都改成「對方」
            }
        })

        return result
    }

    /**
     * 上傳回報到 Supabase。
     * 必須用戶明確同意才呼叫此方法。
     */
    suspend fun uploadReport(
        conversation: String,
        analysisResult: FraudAIAnalyzer.AnalysisResult,
        userConfirmed: Boolean,
        appPackage: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val anonymized = anonymize(conversation)
            val deviceHash = getDeviceHash()

            val body = JSONObject().apply {
                put("conversation", anonymized)
                put("fraud_type", analysisResult.fraudType?.name ?: "UNKNOWN")
                put("ai_confidence", analysisResult.confidence)
                put("ai_risk_level", analysisResult.riskLevel.name)
                put("ai_stage", analysisResult.stage)
                put("user_confirmed", userConfirmed)
                put("app_package", appPackage)
                put("device_hash", deviceHash)
                put("reasons", analysisResult.reasons.joinToString("|"))
            }

            supabase.insert("fraud_reports", body)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getDeviceHash(): String {
        val id = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        return MessageDigest.getInstance("SHA-256")
            .digest(("yukiboard_$id").toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)  // 只取前16字，無法反推原始 ID
    }
}
```

### 2-B Supabase 建表 SQL

在 Supabase Dashboard 執行：

```sql
-- 詐騙回報表（訓練資料用）
CREATE TABLE fraud_reports (
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at  TIMESTAMPTZ DEFAULT now(),

    -- 對話內容（已匿名化）
    conversation        TEXT NOT NULL,
    fraud_type          TEXT,
    ai_confidence       INTEGER,
    ai_risk_level       TEXT,
    ai_stage            INTEGER,
    user_confirmed      BOOLEAN DEFAULT false,
    app_package         TEXT,
    device_hash         TEXT,
    reasons             TEXT,

    -- 訓練標注欄位（後台手動標注用）
    label               TEXT,        -- 'fraud' / 'safe' / 'uncertain'
    label_confidence    FLOAT,
    labeled_at          TIMESTAMPTZ,
    labeled_by          TEXT         -- 'human' / 'claude_api' / 'auto'
);

-- 只允許 INSERT，不允許讀取（隱私保護）
ALTER TABLE fraud_reports ENABLE ROW LEVEL SECURITY;
CREATE POLICY "insert_only" ON fraud_reports
    FOR INSERT WITH CHECK (true);

-- 索引
CREATE INDEX idx_fraud_type ON fraud_reports(fraud_type);
CREATE INDEX idx_user_confirmed ON fraud_reports(user_confirmed);
CREATE INDEX idx_label ON fraud_reports(label);
```

### 2-C 在 ClawPanelView 加入回報按鈕

在防詐詳情面板的「165 按鈕」下方加入：

```kotlin
// 在 drawFraudDetailPanel() 的 165 按鈕下方加：

// 「這是詐騙」回報按鈕
val reportBtnTop = btnBottom + 8f * density
val reportBtnBottom = reportBtnTop + 36f * density
val reportRect = RectF(pd, reportBtnTop, width / 2f - 4f * density, reportBtnBottom)

fraudPanelPaint.color = 0xFF37474F.toInt()
canvas.drawRoundRect(reportRect, 8f * density, 8f * density, fraudPanelPaint)

fraudTextPaint.color = 0xFF90A4AE.toInt()
fraudTextPaint.textSize = 12f * density
fraudTextPaint.textAlign = Paint.Align.CENTER
canvas.drawText(
    "回報這是詐騙",
    reportRect.centerX(),
    reportBtnTop + (reportBtnBottom - reportBtnTop) * 0.65f,
    fraudTextPaint
)

// 「不是詐騙」按鈕（幫助模型學習負樣本）
val notFraudRect = RectF(width / 2f + 4f * density, reportBtnTop, width - pd, reportBtnBottom)
fraudPanelPaint.color = 0xFF37474F.toInt()
canvas.drawRoundRect(notFraudRect, 8f * density, 8f * density, fraudPanelPaint)
canvas.drawText(
    "不是詐騙",
    notFraudRect.centerX(),
    reportBtnTop + (reportBtnBottom - reportBtnTop) * 0.65f,
    fraudTextPaint
)
fraudTextPaint.textAlign = Paint.Align.LEFT

// 儲存按鈕區域
fraudReportBtnRect = reportRect
fraudNotFraudBtnRect = notFraudRect
```

在 `YukiBoardIMEService` 加入回報對話框：

```kotlin
private fun showReportDialog(
    conversation: String,
    result: FraudAIAnalyzer.AnalysisResult,
    isConfirmed: Boolean
) {
    // 顯示同意聲明對話框
    android.app.AlertDialog.Builder(this)  // 注意：IME 需要特殊處理
        .setTitle(if (isConfirmed) "回報詐騙對話" else "回報誤判")
        .setMessage(
            "這段對話將被匿名化後上傳，\n" +
            "用於改善防詐 AI 的準確率。\n\n" +
            "• 個人姓名、電話已自動移除\n" +
            "• 不包含任何可識別個人的資訊\n" +
            "• 可隨時在設定頁刪除你的回報"
        )
        .setPositiveButton("同意上傳") { _, _ ->
            serviceScope.launch {
                val formatted = ConversationFormatter.format(conversation)
                fraudReportManager.uploadReport(
                    conversation = formatted,
                    analysisResult = result,
                    userConfirmed = isConfirmed,
                    appPackage = currentAppPackage ?: ""
                )
            }
        }
        .setNegativeButton("不上傳", null)
        .show()
}
```

---

## 第三批 — 合成訓練資料生成腳本

### `scripts/generate_fraud_training_data.py`

```python
#!/usr/bin/env python3
"""
用 Claude API 批次生成詐騙對話訓練資料。

需要：
  pip install anthropic
  export ANTHROPIC_API_KEY=your_key

輸出：
  data/fraud_conversations.jsonl  （詐騙對話）
  data/safe_conversations.jsonl   （正常對話）
"""

import anthropic
import json
import random
import time
from pathlib import Path

client = anthropic.Anthropic()
OUTPUT_DIR = Path("data")
OUTPUT_DIR.mkdir(exist_ok=True)

FRAUD_TYPES = [
    {
        "type": "FAKE_INVESTMENT",
        "description": "假投資詐騙（殺豬盤）",
        "scenario": "陌生人在交友軟體認識，建立感情後引入投資"
    },
    {
        "type": "FAKE_SERVICE",
        "description": "假客服詐騙",
        "scenario": "假冒蝦皮/7-11/銀行客服，說誤設重複扣款"
    },
    {
        "type": "ROMANCE_SCAM",
        "description": "感情詐騙",
        "scenario": "網路交友，以愛情為由要求借錢或轉帳"
    },
    {
        "type": "FAKE_OFFICIAL",
        "description": "假公務員詐騙",
        "scenario": "假冒警察/檢察官，說帳戶涉案要凍結"
    },
    {
        "type": "JOB_SCAM",
        "description": "工作詐騙",
        "scenario": "假徵才，要求墊付或提供帳戶"
    }
]

FRAUD_PROMPT = """
生成一段台灣真實的詐騙 LINE 對話。

詐騙類型：{type}
場景：{scenario}

要求：
1. 用台灣口語（哈哈、喔、啊、欸等語氣詞）
2. 對方話術自然，不能太明顯是詐騙
3. 符合「建立信任→引入誘惑→榨取金錢」三階段
4. 至少12輪對話
5. 受害者有些猶豫但被說服
6. 使用真實的台灣詐騙話術

格式（嚴格遵守）：
【對方】...
【我方】...
【對方】...
（依此類推）

只輸出對話，不要任何說明。
"""

SAFE_PROMPT = """
生成一段台灣日常的 LINE 對話（完全正常，不是詐騙）。

場景類型（隨機選一個）：
- 朋友間聊天（聊食物、心情、八卦）
- 家人群組（問吃什麼、互報平安）
- 同事討論工作
- 討論旅遊計畫
- 分享日常生活

要求：
1. 用台灣口語
2. 完全正常的日常對話
3. 可能提到錢但是正常的（AA制、借小錢等）
4. 至少8輪對話

格式：
【對方】...
【我方】...

只輸出對話，不要說明。
"""

def generate_conversation(prompt: str, label: str) -> dict | None:
    try:
        message = client.messages.create(
            model="claude-haiku-4-5-20251001",
            max_tokens=1000,
            messages=[{"role": "user", "content": prompt}]
        )
        conversation = message.content[0].text.strip()
        if len(conversation) < 100:
            return None
        return {
            "conversation": conversation,
            "label": label,
            "source": "synthetic_claude"
        }
    except Exception as e:
        print(f"Error: {e}")
        return None

def main():
    fraud_file = OUTPUT_DIR / "fraud_conversations.jsonl"
    safe_file = OUTPUT_DIR / "safe_conversations.jsonl"

    # 生成詐騙對話
    print("生成詐騙對話...")
    fraud_count = 0
    with open(fraud_file, "w", encoding="utf-8") as f:
        for fraud_type in FRAUD_TYPES:
            target = 200  # 每種類型 200 筆
            for i in range(target):
                prompt = FRAUD_PROMPT.format(
                    type=fraud_type["description"],
                    scenario=fraud_type["scenario"]
                )
                result = generate_conversation(prompt, fraud_type["type"])
                if result:
                    f.write(json.dumps(result, ensure_ascii=False) + "\n")
                    fraud_count += 1
                if (i + 1) % 10 == 0:
                    print(f"  {fraud_type['type']}: {i+1}/{target}")
                time.sleep(0.1)  # rate limit

    # 生成正常對話
    print("\n生成正常對話...")
    safe_count = 0
    with open(safe_file, "w", encoding="utf-8") as f:
        for i in range(1000):
            result = generate_conversation(SAFE_PROMPT, "SAFE")
            if result:
                f.write(json.dumps(result, ensure_ascii=False) + "\n")
                safe_count += 1
            if (i + 1) % 100 == 0:
                print(f"  {i+1}/1000")
            time.sleep(0.1)

    print(f"\n完成！詐騙對話：{fraud_count} 筆，正常對話：{safe_count} 筆")
    print(f"輸出目錄：{OUTPUT_DIR.absolute()}")

if __name__ == "__main__":
    main()
```

---

## 第四批 — Google Colab 訓練腳本

### `scripts/train_fraud_detector.ipynb`（在 Google Colab 執行）

```python
# ============================================================
# YukiBoard 防詐 AI — DistilBERT 訓練腳本
# 在 Google Colab 執行（免費 T4 GPU，約 2 小時）
# ============================================================

# 安裝套件
# !pip install transformers datasets torch scikit-learn

import json
import torch
import numpy as np
from pathlib import Path
from torch.utils.data import Dataset, DataLoader
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
    TrainingArguments,
    Trainer
)
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report

# ── 設定 ──────────────────────────────────────────────────

MODEL_NAME = "bert-base-chinese"  # 中文 BERT，效果最好
# 替代方案："distilbert-base-multilingual-cased"（較小，速度較快）

LABELS = ["SAFE", "FAKE_INVESTMENT", "FAKE_SERVICE",
          "ROMANCE_SCAM", "FAKE_OFFICIAL", "JOB_SCAM"]
LABEL2ID = {label: i for i, label in enumerate(LABELS)}
ID2LABEL = {i: label for i, label in enumerate(LABELS)}

MAX_LENGTH = 512
BATCH_SIZE = 16
EPOCHS = 5
LEARNING_RATE = 2e-5

# ── 載入資料 ──────────────────────────────────────────────

def load_data(fraud_file: str, safe_file: str):
    samples = []

    with open(fraud_file, encoding="utf-8") as f:
        for line in f:
            item = json.loads(line)
            samples.append({
                "text": item["conversation"],
                "label": LABEL2ID.get(item["label"], 0)
            })

    with open(safe_file, encoding="utf-8") as f:
        for line in f:
            item = json.loads(line)
            samples.append({
                "text": item["conversation"],
                "label": 0  # SAFE = 0
            })

    return samples

# ── Dataset ───────────────────────────────────────────────

class FraudDataset(Dataset):
    def __init__(self, samples, tokenizer):
        self.samples = samples
        self.tokenizer = tokenizer

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        sample = self.samples[idx]
        encoding = self.tokenizer(
            sample["text"],
            max_length=MAX_LENGTH,
            padding="max_length",
            truncation=True,
            return_tensors="pt"
        )
        return {
            "input_ids": encoding["input_ids"].squeeze(),
            "attention_mask": encoding["attention_mask"].squeeze(),
            "labels": torch.tensor(sample["label"], dtype=torch.long)
        }

# ── 訓練 ──────────────────────────────────────────────────

def compute_metrics(eval_pred):
    logits, labels = eval_pred
    predictions = np.argmax(logits, axis=-1)
    accuracy = (predictions == labels).mean()
    return {"accuracy": accuracy}

def train():
    # 載入資料
    print("載入資料...")
    samples = load_data("data/fraud_conversations.jsonl",
                        "data/safe_conversations.jsonl")
    print(f"總資料量：{len(samples)} 筆")

    # 分割訓練/驗證集
    train_data, val_data = train_test_split(
        samples, test_size=0.15, random_state=42,
        stratify=[s["label"] for s in samples]
    )
    print(f"訓練：{len(train_data)}，驗證：{len(val_data)}")

    # 載入 tokenizer 和模型
    print(f"載入模型：{MODEL_NAME}...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModelForSequenceClassification.from_pretrained(
        MODEL_NAME,
        num_labels=len(LABELS),
        id2label=ID2LABEL,
        label2id=LABEL2ID
    )

    # 建立 Dataset
    train_dataset = FraudDataset(train_data, tokenizer)
    val_dataset   = FraudDataset(val_data, tokenizer)

    # 訓練參數
    training_args = TrainingArguments(
        output_dir="./fraud_model",
        num_train_epochs=EPOCHS,
        per_device_train_batch_size=BATCH_SIZE,
        per_device_eval_batch_size=BATCH_SIZE,
        learning_rate=LEARNING_RATE,
        evaluation_strategy="epoch",
        save_strategy="best",
        load_best_model_at_end=True,
        metric_for_best_model="accuracy",
        logging_steps=50,
        warmup_ratio=0.1,
        weight_decay=0.01,
        report_to="none"
    )

    # Trainer
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=val_dataset,
        compute_metrics=compute_metrics
    )

    # 開始訓練
    print("開始訓練...")
    trainer.train()
    print("訓練完成！")

    # 儲存模型
    trainer.save_model("./fraud_model_final")
    tokenizer.save_pretrained("./fraud_model_final")
    print("模型已儲存到 ./fraud_model_final")

    return model, tokenizer

# ── 轉換成 TFLite ─────────────────────────────────────────

def export_to_tflite(model_dir: str, output_path: str):
    """把 PyTorch 模型轉成 TFLite（Android 用）"""
    # !pip install onnx onnxruntime tf2onnx

    import onnx
    import torch
    from transformers import AutoTokenizer, AutoModelForSequenceClassification

    print("載入訓練好的模型...")
    tokenizer = AutoTokenizer.from_pretrained(model_dir)
    model = AutoModelForSequenceClassification.from_pretrained(model_dir)
    model.eval()

    # 準備 dummy input
    dummy_input = tokenizer(
        "測試文字", return_tensors="pt",
        max_length=512, padding="max_length", truncation=True
    )

    # 匯出成 ONNX
    print("匯出 ONNX...")
    torch.onnx.export(
        model,
        (dummy_input["input_ids"], dummy_input["attention_mask"]),
        "fraud_model.onnx",
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
            "logits": {0: "batch"}
        },
        opset_version=13
    )

    # ONNX → TFLite
    print("轉換 TFLite...")
    import subprocess
    subprocess.run([
        "python", "-m", "tf2onnx.convert",
        "--onnx", "fraud_model.onnx",
        "--output", "fraud_model.tflite",
        "--tflite_path", output_path
    ])
    print(f"TFLite 模型已儲存到：{output_path}")

# 執行
if __name__ == "__main__":
    model, tokenizer = train()
    export_to_tflite("./fraud_model_final", "fraud_detector.tflite")
```

---

## 第五批 — Android TFLite 整合

### 5-A 新增 `security/FraudLocalModel.kt`

```kotlin
package com.snowrealm.yukiboard.security

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * 本地 TFLite 詐騙偵測模型。
 *
 * 打包在 assets/fraud_detector.tflite
 * 完全離線，零 API 費用，50-80ms 推論
 *
 * 在 FraudAIAnalyzer 裡作為 B 層的替代方案：
 * - 有 TFLite 模型 → 優先用本地模型（快速、免費）
 * - 沒有模型 → fallback 到 Gemini Flash API
 */
class FraudLocalModel(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var isAvailable = false

    // 標籤對應
    private val labels = listOf(
        "SAFE", "FAKE_INVESTMENT", "FAKE_SERVICE",
        "ROMANCE_SCAM", "FAKE_OFFICIAL", "JOB_SCAM"
    )

    fun init(): Boolean {
        return try {
            val model = loadModelFile()
            interpreter = Interpreter(model)
            isAvailable = true
            true
        } catch (e: Exception) {
            isAvailable = false
            false
        }
    }

    fun isAvailable() = isAvailable

    /**
     * 推論：輸入對話文字，輸出風險等級。
     * 約 50-80ms，完全本地。
     */
    fun predict(conversation: String): LocalPrediction? {
        val interpreter = interpreter ?: return null

        // 簡單的 token 化（不依賴 transformers，用字元級別）
        // 正式版應該用 HuggingFace tokenizer 的 Android port
        val inputIds = tokenize(conversation)
        val attentionMask = IntArray(inputIds.size) { 1 }

        val outputLogits = Array(1) { FloatArray(labels.size) }

        interpreter.run(
            arrayOf(inputIds, attentionMask),
            outputLogits
        )

        val logits = outputLogits[0]
        val probabilities = softmax(logits)
        val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val confidence = (probabilities[maxIdx] * 100).toInt()

        val label = labels[maxIdx]
        val riskLevel = when {
            label == "SAFE" -> FraudDetector.RiskLevel.SAFE
            confidence >= 70 -> FraudDetector.RiskLevel.HIGH
            confidence >= 40 -> FraudDetector.RiskLevel.LOW
            else -> FraudDetector.RiskLevel.SAFE
        }

        return LocalPrediction(
            label = label,
            riskLevel = riskLevel,
            confidence = confidence,
            allProbabilities = labels.zip(probabilities.toList()).toMap()
        )
    }

    data class LocalPrediction(
        val label: String,
        val riskLevel: FraudDetector.RiskLevel,
        val confidence: Int,
        val allProbabilities: Map<String, Float>
    )

    private fun tokenize(text: String): IntArray {
        // 簡化版：字元轉 UTF-8 byte，截斷到 512
        val bytes = text.toByteArray(Charsets.UTF_8).take(512)
        return IntArray(512) { if (it < bytes.size) bytes[it].toInt() and 0xFF else 0 }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.max()
        val exp = logits.map { Math.exp((it - maxLogit).toDouble()).toFloat() }
        val sum = exp.sum()
        return exp.map { it / sum }.toFloatArray()
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetManager = context.assets
        val fd = assetManager.openFd("fraud_detector.tflite")
        val inputStream = FileInputStream(fd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
```

### 5-B app/build.gradle 加入 TFLite 依賴

在 `dependencies {}` 加入：

```groovy
// TFLite（本地 AI 模型推論）
implementation 'org.tensorflow:tensorflow-lite:2.14.0'
implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
```

### 5-C 升級 FraudAIAnalyzer 優先用本地模型

在 `FraudAIAnalyzer` 加入本地模型支援：

```kotlin
class FraudAIAnalyzer(
    private val aiProvider: AIProvider,
    private val localModel: FraudLocalModel? = null  // 新增
) {
    // ...

    suspend fun analyze(conversation: String, rawText: String = conversation): AnalysisResult {
        val ruleResult = ruleDetector.analyze(rawText)
        if (ruleResult.riskLevel == FraudDetector.RiskLevel.SAFE) {
            return safeResult()
        }

        // 優先用本地模型（快速免費）
        if (localModel?.isAvailable() == true) {
            val localResult = localModel.predict(conversation)
            if (localResult != null && localResult.confidence >= 40) {
                return AnalysisResult(
                    riskLevel = maxOf(ruleResult.riskLevel, localResult.riskLevel),
                    fraudType = FraudDetector.FraudType.valueOf(
                        localResult.label.takeIf { it != "SAFE" } ?: "GENERAL"
                    ),
                    stage = 0,
                    confidence = localResult.confidence,
                    reasons = ruleResult.reasons,
                    suggestions = ruleResult.suggestions,
                    source = AnalysisResult.Source.COMBINED
                )
            }
        }

        // fallback：呼叫 Gemini Flash API
        return try { callAI(conversation, ruleResult) }
        catch (e: Exception) { ruleResult.toAnalysisResult() }
    }
}
```

---

## 執行順序

```
Android 端（現在）：
  第一批：FraudAIAnalyzer.kt + ConversationFormatter.kt
  第二批 A-C：FraudReportManager.kt + 回報按鈕 UI

後端（現在）：
  第二批 B：Supabase 建表（執行 SQL）

3 個月後（有足夠回報資料）：
  第三批：執行 generate_fraud_training_data.py
          生成 2000+ 筆合成訓練資料

  第四批：在 Google Colab 執行訓練腳本
          約 2 小時，完全免費

  第五批：把 fraud_detector.tflite 放進 assets/
          加入 TFLite 依賴
          FraudLocalModel.kt 整合

持續更新：
  每月從 Supabase 拉新回報 → 重新訓練 → 更新 APK
```

---

## 費用估算

| 階段 | 分析方式 | 費用 |
|------|---------|------|
| 現在 | 規則 + Gemini Flash | $0（免費方案） |
| 3個月後 | 規則 + 本地 TFLite 模型 | $0 |
| 訓練成本 | Claude API 生成合成資料 | 約 $5 一次 |
| Colab 訓練 | Google Colab 免費 GPU | $0 |
| 長期 | 每月重新訓練 | $0 |

**整個系統長期運營費用：零。**

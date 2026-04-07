package com.snowrealm.yukiboard.security

import android.app.AlertDialog
import android.content.Context
import com.snowrealm.yukiboard.data.SupabaseClient
import com.snowrealm.yukiboard.ui.settings.YukiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.Locale

/**
 * 匿名詐騙回報管理器。
 *
 * 流程：
 * 1. 使用者在防詐偵測結果頁按下「回報」
 * 2. 首次回報時彈出同意對話框（fraudReportConsent）
 * 3. 文本 → SHA-256 hash（絕不上傳原文）
 * 4. 上傳到 Supabase fraud_reports 表：
 *    text_hash, fraud_type, matched_keywords, is_fraud, rule_score, device_locale
 *
 * 所有上傳資料都是匿名的，不包含任何可識別使用者身分的資訊。
 */
class FraudReportManager(private val context: Context) {

    private val prefs = YukiPreferences(context)
    private val supabase = SupabaseClient.getInstance()

    /**
     * 檢查使用者是否已同意匿名回報。
     */
    fun hasConsent(): Boolean = prefs.fraudReportConsent

    /**
     * 顯示同意對話框（首次回報時使用）。
     * @param onResult true = 同意, false = 拒絕
     */
    fun showConsentDialog(context: Context, onResult: (Boolean) -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("匿名回報協助防詐")
            .setMessage(
                "你的回饋可以幫助其他使用者！\n\n" +
                "雪凜會將偵測結果（不含對話原文）匿名上傳，" +
                "用來改善防詐規則，保護更多人。\n\n" +
                "上傳資料：\n" +
                "  - 文字雜湊值（無法還原原文）\n" +
                "  - 偵測到的詐騙類型\n" +
                "  - 命中的關鍵字\n" +
                "  - 你的判斷（是/不是詐騙）\n" +
                "  - 裝置語系\n\n" +
                "你可以隨時在設定中關閉此功能。"
            )
            .setPositiveButton("同意回報") { _, _ ->
                prefs.fraudReportConsent = true
                onResult(true)
            }
            .setNegativeButton("暫時不要") { _, _ ->
                onResult(false)
            }
            .setCancelable(true)
            .show()
    }

    /**
     * 匿名回報一筆防詐偵測結果。
     *
     * @param text 原始文本（只用來算 hash，不上傳）
     * @param fraudType 偵測到的詐騙類型
     * @param matchedKeywords 命中的關鍵字列表
     * @param isFraud 使用者判斷是否為詐騙
     * @param ruleScore 規則引擎的風險分數
     * @return true = 上傳成功
     */
    suspend fun report(
        text: String,
        fraudType: FraudAIAnalyzer.FraudType,
        matchedKeywords: List<String>,
        isFraud: Boolean,
        ruleScore: Int
    ): Boolean {
        // 必須有同意 + Supabase 已設定
        if (!hasConsent()) return false
        if (!supabase.isConfigured) return false

        return withContext(Dispatchers.IO) {
            try {
                val data = buildJsonObject {
                    put("text_hash", sha256(text.take(500)))
                    put("fraud_type", fraudType.name)
                    put("matched_keywords", matchedKeywords.filter { !it.startsWith("[") }.take(10).joinToString(","))
                    put("is_fraud", isFraud)
                    put("rule_score", ruleScore)
                    put("device_locale", Locale.getDefault().toLanguageTag())
                }
                val result = supabase.insert("fraud_reports", data)
                result != null
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * 撤銷同意（設定頁使用）。
     */
    fun revokeConsent() {
        prefs.fraudReportConsent = false
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

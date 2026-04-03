package com.snowrealm.yukiboard.claw

import android.content.Context
import com.snowrealm.yukiboard.auth.InviteCodeManager
import com.snowrealm.yukiboard.claw.provider.AIProviderManager
import com.snowrealm.yukiboard.claw.skills.*

class ClawClient(context: Context) {
    private val providerManager = AIProviderManager.getInstance(context)
    private val inviteManager = InviteCodeManager(context)
    private val budgetManager = TokenBudgetManager(context)

    private val appContext = context.applicationContext

    private val skills: Map<String, ClawSkill> by lazy {
        val provider = providerManager.getCurrentProvider()
        listOf(
            // --- 原有 Skills ---
            AIPredictionSkill(provider),
            WriterSkill(appContext, provider),
            TranslateSkill(appContext, provider),
            CupidSkill(appContext, provider),
            ContextSkill(),
            AnimationSkill(provider),
            WhisperSkill(),
            // --- T1: 文字處理 ---
            GrammarCheckSkill(appContext, provider),
            ToneAdjustSkill(provider),
            SummarizeSkill(provider),
            // --- T2: 社交娛樂 ---
            MemeTextSkill(appContext, provider),
            EmojiTranslateSkill(provider),
            QuickReplySkill(provider),
            // --- T3: 開發者 ---
            CodeCompleteSkill(provider),
            RegexGenSkill(provider),
            CommitMessageSkill(provider),
            // --- T4: 情境 ---
            YukiChatSkill(appContext, provider),
            MoodDiarySkill(provider),
            WeatherSkill(appContext, provider)
        ).associateBy { it.id }
    }

    /**
     * AI 功能是否可用。
     * 三種身份任一滿足即可：
     * 1. 邀請碼已啟用（免費額度）
     * 2. 自帶 API Key（自己付費，無限）
     * 3. 未來：Z幣帳號登入
     */
    fun isAvailable(): Boolean {
        return providerManager.hasValidConfig() &&
               (inviteManager.isActivated() || hasOwnApiKey())
    }

    /**
     * 剩餘 token 額度。
     * - 自帶 API Key → 無限（自己付費）
     * - 邀請碼 → 每日額度
     * - 未來 Z幣 → 由 ZCoinManager 決定
     */
    fun getRemainingTokens(): Int {
        if (hasOwnApiKey()) return Int.MAX_VALUE
        if (inviteManager.isActivated()) return inviteManager.getRemainingTokens()
        return 0
    }

    suspend fun executeSkill(skillId: String, input: ClawInput): ClawOutput {
        val skill = skills[skillId] ?: throw IllegalArgumentException("Unknown skill: $skillId")

        // 額度檢查（唯一來源：InviteCodeManager 或 自帶Key）
        val remaining = getRemainingTokens()
        if (remaining < skill.tokensPerCall && remaining != Int.MAX_VALUE) {
            return ClawOutput(listOf("Token 額度不足，請明天再試"), OutputType.PREDICTION, 0)
        }

        val output = skill.execute(input)

        // 額度扣款（僅邀請碼用戶，自帶 Key 不扣）
        if (inviteManager.isActivated() && !hasOwnApiKey()) {
            inviteManager.consumeTokens(output.tokensUsed)
        }

        // 統計記錄（所有用戶都記，用於 UI 顯示和週報）
        budgetManager.recordUsage(output.tokensUsed, skillId)

        return output
    }

    private fun hasOwnApiKey(): Boolean {
        val key = providerManager.getApiKey(providerManager.getSelectedProviderType())
        return !key.isNullOrBlank()
    }

    fun getAvailableSkills(): List<ClawSkill> = skills.values.toList()
}

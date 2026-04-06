package com.snowrealm.yukiboard.claw

import android.content.Context
import com.snowrealm.yukiboard.auth.InviteCodeManager
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.claw.provider.AIProviderManager
import com.snowrealm.yukiboard.claw.skills.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class ClawClient(context: Context) {
    private val providerManager = AIProviderManager.getInstance(context)
    private val inviteManager = InviteCodeManager(context)
    private val authClient = SnowRealmAuthClient.getInstance(context)
    private val budgetManager = TokenBudgetManager(context)

    private val appContext = context.applicationContext

    /** 每次動態取 provider，確保切換 Provider 後 Skill 使用新的 */
    private fun getSkill(skillId: String): ClawSkill? {
        val provider = providerManager.getCurrentProvider()
        return when (skillId) {
            "ai_prediction"   -> AIPredictionSkill(provider)
            "writer"          -> WriterSkill(appContext, provider)
            "translate"       -> TranslateSkill(appContext, provider)
            "cupid"           -> CupidSkill(appContext, provider)
            "context"         -> ContextSkill()
            "animation"       -> AnimationSkill(provider)
            "whisper"         -> WhisperSkill()
            "grammar_check"   -> GrammarCheckSkill(appContext, provider)
            "tone_adjust"     -> ToneAdjustSkill(provider)
            "summarize"       -> SummarizeSkill(provider)
            "meme_text"       -> MemeTextSkill(appContext, provider)
            "emoji_translate" -> EmojiTranslateSkill(provider)
            "quick_reply"     -> QuickReplySkill(provider)
            "code_complete"   -> CodeCompleteSkill(provider)
            "regex_gen"       -> RegexGenSkill(provider)
            "commit_message"  -> CommitMessageSkill(provider)
            "yuki_chat"       -> YukiChatSkill(appContext, provider)
            "mood_diary"      -> MoodDiarySkill(provider)
            "weather"         -> WeatherSkill(appContext, provider)
            "fraud_detect"    -> FraudDetectSkill(appContext)
            else              -> null
        }
    }

    private val allSkillIds = listOf(
        "ai_prediction", "writer", "translate", "cupid", "context",
        "animation", "whisper", "grammar_check", "tone_adjust", "summarize",
        "meme_text", "emoji_translate", "quick_reply", "code_complete",
        "regex_gen", "commit_message", "yuki_chat", "mood_diary", "weather",
        "fraud_detect"
    )

    /**
     * AI 功能是否可用。
     * 三種身份任一滿足即可：
     * 1. 邀請碼已啟用（免費額度）
     * 2. 自帶 API Key（自己付費，無限）
     * 3. 未來：Z幣帳號登入
     */
    fun isAvailable(): Boolean {
        return providerManager.hasValidConfig() &&
               (inviteManager.isActivated() || hasOwnApiKey() || authClient.isLoggedIn)
    }

    /**
     * 剩餘 token 額度。
     * - 自帶 API Key → 無限（自己付費）
     * - 邀請碼 → 每日額度
     * - 未來 Z幣 → 由 ZCoinManager 決定
     */
    fun getRemainingTokens(): Int {
        if (hasOwnApiKey()) return Int.MAX_VALUE
        if (authClient.isLoggedIn) return Int.MAX_VALUE  // 平台禮遇由 server 判斷
        if (inviteManager.isActivated()) return inviteManager.getRemainingTokens()
        return 0
    }

    suspend fun executeSkill(skillId: String, input: ClawInput): ClawOutput {
        val skill = getSkill(skillId) ?: throw IllegalArgumentException("Unknown skill: $skillId")

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

    /**
     * 串流執行 Skill — 回傳 Flow<String>，UI 可邊收邊顯示。
     * 目前直接委派給 Provider 的 complete()。
     */
    suspend fun streamSkill(prompt: String, systemPrompt: String = ""): Flow<String> {
        if (!isAvailable()) return emptyFlow()
        val provider = providerManager.getCurrentProvider()
        val model = providerManager.getSelectedModel(providerManager.getSelectedProviderType())
        return provider.complete(prompt, systemPrompt, model)
    }

    fun getAvailableSkills(): List<ClawSkill> = allSkillIds.mapNotNull { getSkill(it) }
}

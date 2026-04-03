package com.snowrealm.yukiboard.claw

import android.content.Context
import com.snowrealm.yukiboard.auth.InviteCodeManager
import com.snowrealm.yukiboard.claw.provider.AIProviderManager
import com.snowrealm.yukiboard.claw.skills.*

class ClawClient(context: Context) {
    private val providerManager = AIProviderManager.getInstance(context)
    private val inviteManager = InviteCodeManager(context)
    private val budgetManager = TokenBudgetManager(context)

    private val skills: Map<String, ClawSkill> by lazy {
        val provider = providerManager.getCurrentProvider()
        listOf(
            AIPredictionSkill(provider),
            WriterSkill(context, provider),
            TranslateSkill(context, provider),
            CupidSkill(context, provider),
            ContextSkill()
        ).associateBy { it.id }
    }

    fun isAvailable(): Boolean {
        return providerManager.hasValidConfig() &&
               (inviteManager.isActivated() || providerManager.getApiKey(providerManager.getSelectedProviderType()) != null)
    }

    fun getRemainingTokens(): Int {
        // User's own key = unlimited
        if (!inviteManager.isActivated()) return Int.MAX_VALUE
        return inviteManager.getRemainingTokens()
    }

    suspend fun executeSkill(skillId: String, input: ClawInput): ClawOutput {
        val skill = skills[skillId] ?: throw IllegalArgumentException("Unknown skill: $skillId")
        val remaining = getRemainingTokens()
        if (remaining < skill.tokensPerCall && remaining != Int.MAX_VALUE) {
            return ClawOutput(listOf("Token 額度不足，請明天再試"), OutputType.PREDICTION, 0)
        }
        val output = skill.execute(input)
        budgetManager.consumeTokens(output.tokensUsed, skillId)
        if (inviteManager.isActivated()) {
            inviteManager.consumeTokens(output.tokensUsed)
        }
        return output
    }

    fun getAvailableSkills(): List<ClawSkill> = skills.values.toList()
}

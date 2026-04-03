package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

data class AnimationProfile(
    val wanderSpeedMultiplier: Float = 1.0f,
    val reactionFrequency: Int = 3,  // react every N keystrokes
    val idleVariant: String = "",    // e.g. "sleepy", "excited", "" for default
    val expressionMood: String = "neutral"
)

class AnimationSkill(private val provider: AIProvider) : ClawSkill {
    override val id = "animation_suggest"
    override val name = "角色動畫"
    override val description = "AI 推薦角色動畫效果"
    override val tokensPerCall = 150

    override suspend fun execute(input: ClawInput): ClawOutput {
        val prompt = """根據以下情境，推薦角色動畫參數（JSON格式）：
主題: ${input.context}
時間: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())}
App: ${input.appPackage}

回傳 JSON：{"wanderSpeed":"slow/normal/fast","reactionFrequency":1-10,"idleVariant":"sleepy/excited/focused/default","mood":"happy/focused/sleepy/excited/neutral"}
只輸出 JSON，不要解釋。"""

        val result = StringBuilder()
        provider.complete(prompt, "你是動畫參數推薦引擎。回傳精確JSON。").collect { result.append(it) }

        return ClawOutput(listOf(result.toString().trim()), OutputType.PREDICTION, tokensPerCall)
    }

    companion object {
        fun parseProfile(json: String): AnimationProfile {
            return try {
                // Simple manual parsing to avoid adding dependency
                val speed = Regex(""""wanderSpeed"\s*:\s*"(\w+)"""").find(json)?.groupValues?.get(1) ?: "normal"
                val freq = Regex(""""reactionFrequency"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 3
                val variant = Regex(""""idleVariant"\s*:\s*"(\w+)"""").find(json)?.groupValues?.get(1) ?: "default"
                val mood = Regex(""""mood"\s*:\s*"(\w+)"""").find(json)?.groupValues?.get(1) ?: "neutral"
                AnimationProfile(
                    wanderSpeedMultiplier = when (speed) { "slow" -> 0.5f; "fast" -> 1.5f; else -> 1.0f },
                    reactionFrequency = freq.coerceIn(1, 10),
                    idleVariant = if (variant == "default") "" else variant,
                    expressionMood = mood
                )
            } catch (_: Exception) {
                AnimationProfile()
            }
        }
    }
}

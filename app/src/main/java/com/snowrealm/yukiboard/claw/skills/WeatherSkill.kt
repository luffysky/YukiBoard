package com.snowrealm.yukiboard.claw.skills

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class WeatherSkill(private val context: Context, private val provider: AIProvider? = null) : ClawSkill {
    override val id = "weather"
    override val name = "天氣助理"
    override val description = "天氣資訊與穿衣建議"
    override val tokensPerCall = 300

    /**
     * 本地天氣短語（0 token），不需要 AI。
     */
    fun getLocalPhrases(temperature: Int, condition: String): List<String> {
        val phrases = mutableListOf<String>()
        when {
            temperature < 10 -> phrases.add("超冷！穿厚一點 🥶")
            temperature < 15 -> phrases.add("有點涼，帶件外套 🧥")
            temperature > 35 -> phrases.add("熱死了，多喝水 🥵")
            temperature > 30 -> phrases.add("好熱，記得防曬 ☀️")
        }
        when {
            condition.contains("雨") -> phrases.add("記得帶傘 🌧️")
            condition.contains("晴") -> phrases.add("天氣超好！✨")
        }
        if (phrases.isEmpty()) phrases.add("普通的天氣，出門注意安全 ⛅")
        return phrases
    }

    override suspend fun execute(input: ClawInput): ClawOutput {
        if (provider == null) {
            return ClawOutput(getLocalPhrases(25, "晴"), OutputType.REPLY, 0)
        }

        val promptTemplate = try {
            context.resources.openRawResource(R.raw.claw_weather_outfit_prompt)
                .bufferedReader().readText()
        } catch (_: Exception) {
            "根據天氣資訊給出穿衣建議，100字以內，雪凜活潑風格。\n天氣：{input}"
        }
        val prompt = promptTemplate
            .replace("{input}", input.text)
            .replace("{city}", input.context)
            .replace("{temp}", "")
            .replace("{condition}", "")
            .replace("{humidity}", "")
            .replace("{wind}", "")

        val systemPrompt = "你是雪凜天氣助理。活潑可愛，100字以內。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        return ClawOutput(listOf(result.toString().trim()), OutputType.REPLY, tokensPerCall)
    }
}

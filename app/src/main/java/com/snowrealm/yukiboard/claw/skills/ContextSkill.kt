package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*

class ContextSkill : ClawSkill {
    override val id = "context"
    override val name = "情境感知"
    override val description = "根據 App 自動調整風格"
    override val tokensPerCall = 0

    override suspend fun execute(input: ClawInput): ClawOutput {
        val style = getContextStyle(input.appPackage)
        return ClawOutput(listOf(style), OutputType.PREDICTION, 0)
    }

    companion object {
        fun getContextStyle(appPackage: String): String = when {
            appPackage.startsWith("com.linecorp") ||
            appPackage.startsWith("org.telegram") ||
            appPackage.startsWith("com.whatsapp") -> "輕鬆聊天，口語化，使用台灣繁體中文日常用語"

            appPackage.startsWith("com.google.android.gm") ||
            appPackage.contains("outlook") -> "正式商務書信，禮貌且專業"

            appPackage.startsWith("com.instagram") ||
            appPackage.startsWith("com.twitter") ||
            appPackage.contains("threads") -> "活潑社群風格，簡短有趣"

            appPackage.startsWith("com.spck") ||
            appPackage.startsWith("com.acode") ||
            appPackage.contains("termux") ||
            appPackage.contains("vscode") -> "技術精準，使用開發者術語"

            else -> "自然台灣繁體中文，口語友善"
        }
    }
}

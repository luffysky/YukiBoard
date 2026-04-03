package com.snowrealm.yukiboard.claw.skills

import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class CommitMessageSkill(private val provider: AIProvider) : ClawSkill {
    override val id = "commit_message"
    override val name = "Commit 訊息"
    override val description = "產生 git commit message"
    override val tokensPerCall = 400

    override suspend fun execute(input: ClawInput): ClawOutput {
        val prompt = "根據以下 diff 或變更描述，產生 3 個 git commit message（Conventional Commits 格式）。每個用 --- 分隔。\n\n${input.text}"
        val systemPrompt = "你是 git commit message 專家。格式：type(scope): description。簡潔有力。"

        val result = StringBuilder()
        provider.complete(prompt, systemPrompt).collect { result.append(it) }

        val suggestions = result.toString().trim()
            .split("---")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ClawOutput(suggestions, OutputType.REWRITE, tokensPerCall)
    }
}

package com.snowrealm.yukiboard.claw.skills

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.claw.*
import com.snowrealm.yukiboard.claw.provider.AIProvider

class WriterSkill(private val context: Context, private val provider: AIProvider) : ClawSkill {
    override val id = "writer"
    override val name = "雪凜寫手"
    override val description = "幫你寫、回覆、換說法"
    override val tokensPerCall = 800

    enum class WriterMode(val task: String) {
        WRITE("根據主題/關鍵字生成完整段落"),
        REPLY("根據對方的話生成適合的回覆"),
        REPHRASE("提供 3 種不同說法"),
        STRENGTHEN("優化文字力道，讓這句話更有力")
    }

    var writerMode: WriterMode = WriterMode.WRITE

    override suspend fun execute(input: ClawInput): ClawOutput {
        val promptTemplate = try {
            context.resources.openRawResource(R.raw.claw_writer_prompt).bufferedReader().readText()
        } catch (_: Exception) {
            "你是寫作助手。任務：{task}。風格參考：{context_style}。\n\n用戶輸入：{input}\n\n提供 3 個版本，用 --- 分隔。"
        }

        val contextStyle = ContextSkill.getContextStyle(input.appPackage)
        val prompt = promptTemplate
            .replace("{context_style}", contextStyle)
            .replace("{task}", writerMode.task)
            .replace("{input}", input.text)

        val result = StringBuilder()
        provider.complete(prompt, "").collect { result.append(it) }

        val suggestions = result.toString().trim().split("---")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ClawOutput(suggestions, OutputType.REWRITE, tokensPerCall)
    }
}

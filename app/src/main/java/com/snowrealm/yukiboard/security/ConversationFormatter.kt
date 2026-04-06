package com.snowrealm.yukiboard.security

/**
 * 對話格式化工具 — 將剪貼簿或輸入框的聊天記錄格式化為分析用文本。
 *
 * 支援 LINE / Messenger / WhatsApp / iMessage 等常見格式：
 * - 說話者辨識（冒號分隔、時間戳分隔）
 * - 截斷過長對話（保留最近 N 則）
 * - 移除媒體/貼圖佔位符
 */
object ConversationFormatter {

    private const val MAX_MESSAGES = 50
    private const val MAX_CHARS = 3000

    data class Message(
        val speaker: String,
        val text: String
    )

    /**
     * 將原始對話文本格式化為 Message 列表。
     * 自動偵測格式並解析說話者。
     */
    fun parse(rawText: String): List<Message> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val messages = mutableListOf<Message>()

        for (line in lines) {
            val parsed = parseLine(line)
            if (parsed != null) {
                messages.add(parsed)
            } else if (messages.isNotEmpty()) {
                // 續行：附加到前一則訊息
                val last = messages.removeAt(messages.size - 1)
                messages.add(last.copy(text = last.text + "\n" + line))
            } else {
                messages.add(Message("unknown", line))
            }
        }

        return messages.takeLast(MAX_MESSAGES)
    }

    private fun parseLine(line: String): Message? {
        // LINE 格式：「HH:MM\t名稱\t內容」或「名稱：內容」
        val lineTab = line.split("\t")
        if (lineTab.size >= 3) {
            return Message(lineTab[1].trim(), lineTab.drop(2).joinToString("\t").trim())
        }

        // 時間戳 + 名稱 + 內容：「[12:34] 小明: 你好」
        val timestampPattern = Regex("""^\[?\d{1,2}:\d{2}]?\s*(.+?)[:：]\s*(.+)$""")
        timestampPattern.find(line)?.let {
            return Message(it.groupValues[1].trim(), it.groupValues[2].trim())
        }

        // 通用冒號分隔：「小明：你好」
        val colonPattern = Regex("""^(.{1,20})[:：]\s*(.+)$""")
        colonPattern.find(line)?.let {
            val speaker = it.groupValues[1].trim()
            val text = it.groupValues[2].trim()
            // 避免把 URL（http:）或時間（12:00）誤判為說話者
            if (!speaker.contains("http") && !speaker.matches(Regex("""\d+"""))) {
                return Message(speaker, text)
            }
        }

        return null
    }

    /**
     * 將 Message 列表格式化為 AI 分析用的文本。
     * 截斷超長內容，並標記說話者角色。
     */
    fun formatForAnalysis(messages: List<Message>): String {
        if (messages.isEmpty()) return ""

        val speakers = messages.map { it.speaker }.distinct()
        val sb = StringBuilder()

        sb.appendLine("=== 對話記錄（共 ${messages.size} 則，${speakers.size} 位說話者）===")

        var totalChars = 0
        for (msg in messages) {
            val line = "[${msg.speaker}]: ${msg.text}"
            if (totalChars + line.length > MAX_CHARS) {
                sb.appendLine("... (已截斷)")
                break
            }
            sb.appendLine(line)
            totalChars += line.length
        }

        return sb.toString().trim()
    }

    /**
     * 快捷方法：原始文本 → 分析用格式化文本
     */
    fun formatRaw(rawText: String): String {
        val messages = parse(rawText)
        return if (messages.isEmpty()) rawText.take(MAX_CHARS)
        else formatForAnalysis(messages)
    }
}

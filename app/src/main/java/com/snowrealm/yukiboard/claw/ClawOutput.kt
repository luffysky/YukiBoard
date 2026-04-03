package com.snowrealm.yukiboard.claw

enum class OutputType {
    PREDICTION, REWRITE, TRANSLATE, REPLY
}

data class ClawOutput(
    val suggestions: List<String>,
    val type: OutputType,
    val tokensUsed: Int = 0
)

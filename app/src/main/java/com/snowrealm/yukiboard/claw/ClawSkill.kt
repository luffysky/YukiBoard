package com.snowrealm.yukiboard.claw

interface ClawSkill {
    val id: String
    val name: String
    val description: String
    val tokensPerCall: Int
    suspend fun execute(input: ClawInput): ClawOutput
}

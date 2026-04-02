package com.snowrealm.yukiboard.language

import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.ui.keyboard.KeyboardState
import com.snowrealm.yukiboard.ui.keyboard.KeyModel

/**
 * 英文（美式 QWERTY）語言包。
 */
object EnglishLanguagePack : LanguagePack {

    override val localeTag: String   = "en-US"
    override val displayName: String = "English (QWERTY)"
    override val inputMode: InputMode = InputMode.ENGLISH
    override val supportsCandidates: Boolean = false

    override val keyRows: List<List<KeyModel>>
        get() = KeyboardState.defaultQwertyRows()

    override val punctuations: List<String> = listOf(
        ".", ",", "!", "?", "'", "\"", ";", ":",
        "-", "_", "(", ")", "[", "]", "@", "#",
        "$", "%", "&", "*", "/", "\\", "+", "="
    )
}

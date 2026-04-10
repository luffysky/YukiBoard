package com.snowrealm.yukiboard.ui.keyboard

import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.theme.KeyboardTheme

data class KeyboardState(
    val inputMode: InputMode = InputMode.ENGLISH,
    val isShifted: Boolean = false,
    val isCapsLock: Boolean = false,
    val composingText: String = "",
    val candidates: List<String> = emptyList(),
    val selectedCandidateIndex: Int = -1,
    val theme: KeyboardTheme = KeyboardTheme.default(),
    val isVoiceInputActive: Boolean = false,
    val showNumberRow: Boolean = true,
    val keyRows: List<List<KeyModel>> = defaultQwertyRows()
) {
    companion object {
        // =================================================================
        // 英文 QWERTY（一般模式）
        // =================================================================
        fun defaultQwertyRows(): List<List<KeyModel>> = listOf(
            // 數字行
            listOf(
                KeyModel.char("1", altChars = listOf("１", "一", "壹", "①", "¹", "₁")),
                KeyModel.char("2", altChars = listOf("２", "二", "貳", "②", "²", "₂")),
                KeyModel.char("3", altChars = listOf("３", "三", "參", "③", "³", "₃")),
                KeyModel.char("4", altChars = listOf("４", "四", "肆", "④", "⁴", "₄")),
                KeyModel.char("5", altChars = listOf("５", "五", "伍", "⑤", "⁵", "₅")),
                KeyModel.char("6", altChars = listOf("６", "六", "陸", "⑥", "⁶", "₆")),
                KeyModel.char("7", altChars = listOf("７", "七", "柒", "⑦", "⁷", "₇")),
                KeyModel.char("8", altChars = listOf("８", "八", "捌", "⑧", "⁸", "₈")),
                KeyModel.char("9", altChars = listOf("９", "九", "玖", "⑨", "⁹", "₉")),
                KeyModel.char("0", altChars = listOf("０", "零", "〇", "⑩", "⁰", "₀"))
            ),
            listOf(
                KeyModel.char("q"), KeyModel.char("w"),
                KeyModel.char("e", altChars = listOf("é", "è", "ê", "ë", "ē")),
                KeyModel.char("r"), KeyModel.char("t"),
                KeyModel.char("y", altChars = listOf("ý", "ÿ")),
                KeyModel.char("u", altChars = listOf("ú", "ù", "û", "ü", "ū")),
                KeyModel.char("i", altChars = listOf("í", "ì", "î", "ï", "ī")),
                KeyModel.char("o", altChars = listOf("ó", "ò", "ô", "ö", "ō")),
                KeyModel.char("p")
            ),
            listOf(
                KeyModel.char("a", altChars = listOf("á", "à", "â", "ä", "ā")),
                KeyModel.char("s", altChars = listOf("ß", "š")),
                KeyModel.char("d"), KeyModel.char("f"), KeyModel.char("g"),
                KeyModel.char("h"), KeyModel.char("j"), KeyModel.char("k"),
                KeyModel.char("l")
            ),
            listOf(
                KeyModel.special("⇧", KeyboardView.KEYCODE_SHIFT, widthWeight = 1.5f),
                KeyModel.char("z"), KeyModel.char("x"),
                KeyModel.char("c", altChars = listOf("ç")),
                KeyModel.char("v"), KeyModel.char("b"),
                KeyModel.char("n", altChars = listOf("ñ")),
                KeyModel.char("m"),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f)
            ),
            // 底行（精簡：合併功能行 + 空白鍵，方向鍵移至開發者模式）
            listOf(
                KeyModel.special("Ctrl", KeyboardView.KEYCODE_CTRL, widthWeight = 0.8f),
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 0.8f),
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.0f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "、", "；", "：", "「", "」")),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 1.5f),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD, altChars = listOf("。", "！", "？", "…", "～", "·")),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.3f)
            )
        )

        // =================================================================
        // 設計者模式
        // =================================================================
        fun designerRows(): List<List<KeyModel>> = listOf(
            // 快捷列：設計工具常用
            listOf(
                KeyModel.special("復原", KeyboardView.KEYCODE_CTRL_Z, widthWeight = 1.3f),
                KeyModel.special("重做", KeyboardView.KEYCODE_CTRL_Y, widthWeight = 1.3f),
                KeyModel.special("複製", KeyboardView.KEYCODE_CTRL_C, widthWeight = 1.3f),
                KeyModel.special("貼上", KeyboardView.KEYCODE_CTRL_V, widthWeight = 1.3f),
                KeyModel.special("剪下", KeyboardView.KEYCODE_CTRL_X, widthWeight = 1.3f),
                KeyModel.special("全選", KeyboardView.KEYCODE_CTRL_A, widthWeight = 1.3f)
            ),
            // 設計快捷鍵
            listOf(
                KeyModel.special("群組", KeyboardView.KEYCODE_DESIGN_GROUP, widthWeight = 1.5f),
                KeyModel.special("解散", KeyboardView.KEYCODE_DESIGN_UNGROUP, widthWeight = 1.5f),
                KeyModel.special("複製物件", KeyboardView.KEYCODE_DESIGN_DUPLICATE, widthWeight = 2f),
                KeyModel.special("鎖定", KeyboardView.KEYCODE_DESIGN_LOCK, widthWeight = 1.5f),
                KeyModel.special("隱藏", KeyboardView.KEYCODE_DESIGN_HIDE, widthWeight = 1.5f)
            ),
            // 圖層 & 翻轉
            listOf(
                KeyModel.special("上移層", KeyboardView.KEYCODE_DESIGN_LAYER_UP, widthWeight = 1.3f),
                KeyModel.special("下移層", KeyboardView.KEYCODE_DESIGN_LAYER_DOWN, widthWeight = 1.3f),
                KeyModel.special("最上層", KeyboardView.KEYCODE_DESIGN_LAYER_TOP, widthWeight = 1.3f),
                KeyModel.special("最下層", KeyboardView.KEYCODE_DESIGN_LAYER_BOT, widthWeight = 1.3f),
                KeyModel.special("水平翻", KeyboardView.KEYCODE_DESIGN_FLIP_H, widthWeight = 1.3f),
                KeyModel.special("垂直翻", KeyboardView.KEYCODE_DESIGN_FLIP_V, widthWeight = 1.3f)
            ),
            // 對齊 & 縮放
            listOf(
                KeyModel.special("靠左", KeyboardView.KEYCODE_DESIGN_ALIGN_L, widthWeight = 1.2f),
                KeyModel.special("置中", KeyboardView.KEYCODE_DESIGN_ALIGN_C, widthWeight = 1.2f),
                KeyModel.special("靠右", KeyboardView.KEYCODE_DESIGN_ALIGN_R, widthWeight = 1.2f),
                KeyModel.special("放大", KeyboardView.KEYCODE_DESIGN_ZOOM_IN, widthWeight = 1.2f),
                KeyModel.special("縮小", KeyboardView.KEYCODE_DESIGN_ZOOM_OUT, widthWeight = 1.2f),
                KeyModel.special("100%", KeyboardView.KEYCODE_DESIGN_ZOOM_FIT, widthWeight = 1.2f)
            ),
            // 文字樣式
            listOf(
                KeyModel.special("粗體", KeyboardView.KEYCODE_DESIGN_BOLD, widthWeight = 1.3f),
                KeyModel.special("斜體", KeyboardView.KEYCODE_DESIGN_ITALIC, widthWeight = 1.3f),
                KeyModel.special("底線", KeyboardView.KEYCODE_DESIGN_UNDERLINE, widthWeight = 1.3f),
                KeyModel.special("字+", KeyboardView.KEYCODE_DESIGN_FONT_UP),
                KeyModel.special("字-", KeyboardView.KEYCODE_DESIGN_FONT_DOWN),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.3f)
            ),
            // 底行
            listOf(
                KeyModel.special("⌨返回", KeyboardView.KEYCODE_BACK_NORMAL, widthWeight = 1.5f),
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.0f),
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT),
                KeyModel.special("↑", KeyboardView.KEYCODE_ARROW_UP),
                KeyModel.special("↓", KeyboardView.KEYCODE_ARROW_DOWN),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.3f)
            )
        )

        // =================================================================
        // 開發者模式
        // =================================================================
        fun developerRows(): List<List<KeyModel>> = listOf(
            // 符號列：程式常用
            listOf(
                KeyModel.char("{"), KeyModel.char("}"),
                KeyModel.char("("), KeyModel.char(")"),
                KeyModel.char("["), KeyModel.char("]"),
                KeyModel.char("<"), KeyModel.char(">"),
                KeyModel.char(";"), KeyModel.char(":")
            ),
            // 運算子 & 特殊字元
            listOf(
                KeyModel.char("="), KeyModel.char("+"),
                KeyModel.char("-"), KeyModel.char("*"),
                KeyModel.char("/"), KeyModel.char("%"),
                KeyModel.char("&"), KeyModel.char("|"),
                KeyModel.char("!"), KeyModel.char("?")
            ),
            // 更多符號
            listOf(
                KeyModel.char("\""), KeyModel.char("'"),
                KeyModel.char("`"), KeyModel.char("~"),
                KeyModel.char("@"), KeyModel.char("#"),
                KeyModel.char("$"), KeyModel.char("^"),
                KeyModel.char("\\"), KeyModel.char("_")
            ),
            // 常用組合符號
            listOf(
                KeyModel.special("->", KeyboardView.KEYCODE_DEV_ARROW, widthWeight = 1.2f),
                KeyModel.special("=>", KeyboardView.KEYCODE_DEV_FAT_ARROW, widthWeight = 1.2f),
                KeyModel.special("//", KeyboardView.KEYCODE_DEV_DSLASH, widthWeight = 1.2f),
                KeyModel.special("/**/", KeyboardView.KEYCODE_DEV_COMMENT_BLOCK, widthWeight = 1.5f),
                KeyModel.special("縮排", KeyboardView.KEYCODE_DEV_INDENT, widthWeight = 1.2f),
                KeyModel.special("反縮", KeyboardView.KEYCODE_DEV_UNINDENT, widthWeight = 1.2f),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.2f)
            ),
            // 開發快捷鍵
            listOf(
                KeyModel.special("復原", KeyboardView.KEYCODE_CTRL_Z, widthWeight = 1.1f),
                KeyModel.special("重做", KeyboardView.KEYCODE_CTRL_Y, widthWeight = 1.1f),
                KeyModel.special("複製", KeyboardView.KEYCODE_CTRL_C, widthWeight = 1.1f),
                KeyModel.special("貼上", KeyboardView.KEYCODE_CTRL_V, widthWeight = 1.1f),
                KeyModel.special("找", KeyboardView.KEYCODE_DEV_FIND, widthWeight = 1f),
                KeyModel.special("註解", KeyboardView.KEYCODE_DEV_COMMENT, widthWeight = 1.1f),
                KeyModel.special("格式化", KeyboardView.KEYCODE_DEV_FORMAT, widthWeight = 1.3f)
            ),
            // 行操作 & 摺疊
            listOf(
                KeyModel.special("行↑", KeyboardView.KEYCODE_DEV_LINE_UP, widthWeight = 1.2f),
                KeyModel.special("行↓", KeyboardView.KEYCODE_DEV_LINE_DOWN, widthWeight = 1.2f),
                KeyModel.special("摺疊", KeyboardView.KEYCODE_DEV_FOLD, widthWeight = 1.2f),
                KeyModel.special("展開", KeyboardView.KEYCODE_DEV_UNFOLD, widthWeight = 1.2f),
                KeyModel.special("Home", KeyboardView.KEYCODE_DEV_HOME, widthWeight = 1.2f),
                KeyModel.special("End", KeyboardView.KEYCODE_DEV_END, widthWeight = 1.2f)
            ),
            // 底行
            listOf(
                KeyModel.special("⌨返回", KeyboardView.KEYCODE_BACK_NORMAL, widthWeight = 1.5f),
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.0f),
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT),
                KeyModel.special("↑", KeyboardView.KEYCODE_ARROW_UP),
                KeyModel.special("↓", KeyboardView.KEYCODE_ARROW_DOWN),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.2f)
            )
        )

        // =================================================================
        // 注音佈局
        // =================================================================
        fun bopomofoRows(): List<List<KeyModel>> = listOf(
            // 大千式標準排列 — 長按對應 QWERTY 英文小寫+大寫/數字
            listOf(
                KeyModel.char("ㄅ", altChars = listOf("1")),
                KeyModel.char("ㄉ", altChars = listOf("2")),
                KeyModel.char("ˇ", altChars = listOf("3")),
                KeyModel.char("ˋ", altChars = listOf("4")),
                KeyModel.char("ㄓ", altChars = listOf("5")),
                KeyModel.char("ˊ", altChars = listOf("6")),
                KeyModel.char("˙", altChars = listOf("7")),
                KeyModel.char("ㄚ", altChars = listOf("8")),
                KeyModel.char("ㄞ", altChars = listOf("9")),
                KeyModel.char("ㄢ", altChars = listOf("0"))
            ),
            listOf(
                KeyModel.char("ㄆ", altChars = listOf("q", "Q")),
                KeyModel.char("ㄊ", altChars = listOf("w", "W")),
                KeyModel.char("ㄍ", altChars = listOf("e", "E")),
                KeyModel.char("ㄐ", altChars = listOf("r", "R")),
                KeyModel.char("ㄔ", altChars = listOf("t", "T")),
                KeyModel.char("ㄗ", altChars = listOf("y", "Y")),
                KeyModel.char("ㄧ", altChars = listOf("u", "U")),
                KeyModel.char("ㄛ", altChars = listOf("i", "I")),
                KeyModel.char("ㄟ", altChars = listOf("o", "O")),
                KeyModel.char("ㄣ", altChars = listOf("p", "P"))
            ),
            listOf(
                KeyModel.char("ㄇ", altChars = listOf("a", "A")),
                KeyModel.char("ㄋ", altChars = listOf("s", "S")),
                KeyModel.char("ㄎ", altChars = listOf("d", "D")),
                KeyModel.char("ㄑ", altChars = listOf("f", "F")),
                KeyModel.char("ㄕ", altChars = listOf("g", "G")),
                KeyModel.char("ㄘ", altChars = listOf("h", "H")),
                KeyModel.char("ㄨ", altChars = listOf("j", "J")),
                KeyModel.char("ㄜ", altChars = listOf("k", "K")),
                KeyModel.char("ㄠ", altChars = listOf("l", "L")),
                KeyModel.char("ㄤ", altChars = listOf(";"))
            ),
            listOf(
                KeyModel.char("ㄈ", altChars = listOf("z", "Z")),
                KeyModel.char("ㄌ", altChars = listOf("x", "X")),
                KeyModel.char("ㄏ", altChars = listOf("c", "C")),
                KeyModel.char("ㄒ", altChars = listOf("v", "V")),
                KeyModel.char("ㄖ", altChars = listOf("b", "B")),
                KeyModel.char("ㄙ", altChars = listOf("n", "N")),
                KeyModel.char("ㄩ", altChars = listOf("m", "M")),
                KeyModel.char("ㄝ", altChars = listOf(",")),
                KeyModel.char("ㄡ", altChars = listOf(".")),
                KeyModel.char("ㄥ", altChars = listOf("/"))
            ),
            listOf(
                KeyModel.char("ㄦ"),
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.0f),
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.0f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 1.5f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "、", "；")),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD, altChars = listOf("。", "！", "？")),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.1f)
            )
        )

        // =================================================================
        // 數字鍵盤（計算機風格，按 numpad 進入）
        // =================================================================
        fun numpadRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("1"), KeyModel.char("2"), KeyModel.char("3"),
                KeyModel.char("+", altChars = listOf("±"))
            ),
            listOf(
                KeyModel.char("4"), KeyModel.char("5"), KeyModel.char("6"),
                KeyModel.char("-", altChars = listOf("–", "—"))
            ),
            listOf(
                KeyModel.char("7"), KeyModel.char("8"), KeyModel.char("9"),
                KeyModel.char("×", altChars = listOf("*"))
            ),
            listOf(
                KeyModel.char("%", altChars = listOf("‰")),
                KeyModel.char("0", altChars = listOf("00", "000")),
                KeyModel.char(".", altChars = listOf(",")),
                KeyModel.char("÷", altChars = listOf("/"))
            ),
            listOf(
                KeyModel.special("ABC", KeyboardView.KEYCODE_SWITCH_ABC, widthWeight = 1.2f),
                KeyModel.special("!?#", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.2f),
                KeyModel.char("=", altChars = listOf("≠", "≈", "≤", "≥")),
                KeyModel.char("(", altChars = listOf(")", "（", "）")),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.2f)
            ),
            listOf(
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.0f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2.0f),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.5f)
            )
        )

        // =================================================================
        // 數字符號鍵盤（按 ?123 進入）
        // =================================================================
        fun numberSymbolRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("1"), KeyModel.char("2"), KeyModel.char("3"),
                KeyModel.char("4"), KeyModel.char("5"), KeyModel.char("6"),
                KeyModel.char("7"), KeyModel.char("8"), KeyModel.char("9"),
                KeyModel.char("0")
            ),
            listOf(
                KeyModel.char("@"), KeyModel.char("#"),
                KeyModel.char("$", altChars = listOf("¥", "€", "£", "¢")),
                KeyModel.char("_"), KeyModel.char("&"),
                KeyModel.char("-", altChars = listOf("–", "—", "·")),
                KeyModel.char("+", altChars = listOf("±")),
                KeyModel.char("(", altChars = listOf("（", "「", "『", "【", "〔")),
                KeyModel.char(")", altChars = listOf("）", "」", "』", "】", "〕")),
                KeyModel.char("/", altChars = listOf("\\"))
            ),
            listOf(
                KeyModel.special("#+=", KeyboardView.KEYCODE_SWITCH_SYMBOLS, widthWeight = 1.5f),
                KeyModel.char("*"), KeyModel.char("\"", altChars = listOf("\u201C", "\u201D", "\u201E")),
                KeyModel.char("'", altChars = listOf("\u2018", "\u2019", "\u201A", "\u201B")),
                KeyModel.char(":", altChars = listOf("：")),
                KeyModel.char(";", altChars = listOf("；")),
                KeyModel.char("!", altChars = listOf("！", "¡")),
                KeyModel.char("?", altChars = listOf("？", "¿")),
                KeyModel.char("%"),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f)
            ),
            // 中文常用標點列
            listOf(
                KeyModel.char("，"), KeyModel.char("。"), KeyModel.char("、"),
                KeyModel.char("！"), KeyModel.char("？"), KeyModel.char("…", altChars = listOf("⋯")),
                KeyModel.char("～"), KeyModel.char("「", altChars = listOf("『", "【", "〈", "《")),
                KeyModel.char("」", altChars = listOf("』", "】", "〉", "》")),
                KeyModel.char("——")
            ),
            listOf(
                KeyModel.special("ABC", KeyboardView.KEYCODE_SWITCH_ABC, widthWeight = 1.3f),
                KeyModel.special("⌨", KeyboardView.KEYCODE_SWITCH_NUMPAD, widthWeight = 1.0f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2.5f),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.5f)
            )
        )

        // =================================================================
        // 更多符號鍵盤（按 #+= 進入）
        // =================================================================
        fun moreSymbolRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("~"), KeyModel.char("`"),
                KeyModel.char("|"), KeyModel.char("•", altChars = listOf("‣", "⁃", "◦")),
                KeyModel.char("√"), KeyModel.char("π", altChars = listOf("Ω", "µ", "λ")),
                KeyModel.char("÷"), KeyModel.char("×"),
                KeyModel.char("§", altChars = listOf("¶")), KeyModel.char("∆", altChars = listOf("∞", "≈", "≠"))
            ),
            listOf(
                KeyModel.char("^"), KeyModel.char("°", altChars = listOf("℃", "℉")),
                KeyModel.char("=", altChars = listOf("≠", "≈", "≤", "≥")),
                KeyModel.char("{"), KeyModel.char("}"),
                KeyModel.char("\\"), KeyModel.char("["),
                KeyModel.char("]"), KeyModel.char("<"),
                KeyModel.char(">")
            ),
            listOf(
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.5f),
                KeyModel.char("©"), KeyModel.char("®"),
                KeyModel.char("™"), KeyModel.char("✓", altChars = listOf("✗", "✔", "✘")),
                KeyModel.char("♥", altChars = listOf("♡", "♣", "♠", "♦")),
                KeyModel.char("★", altChars = listOf("☆", "✦", "✧")),
                KeyModel.char("→", altChars = listOf("←", "↑", "↓", "↔", "⇒")),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f)
            ),
            // 中文標點 + 書名號 + 貨幣
            listOf(
                KeyModel.char("《"), KeyModel.char("》"),
                KeyModel.char("〈"), KeyModel.char("〉"),
                KeyModel.char("【"), KeyModel.char("】"),
                KeyModel.char("『"), KeyModel.char("』"),
                KeyModel.char("£", altChars = listOf("¥", "€", "¢", "₩", "₿")),
                KeyModel.char("¥", altChars = listOf("€", "£", "₩", "₿"))
            ),
            listOf(
                KeyModel.special("ABC", KeyboardView.KEYCODE_SWITCH_ABC, widthWeight = 1.5f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 3.4f),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.5f)
            )
        )

        // =================================================================
        // 遊戲模式（QWERTY + 返回鍵）
        // =================================================================
        fun gameRows(): List<List<KeyModel>> {
            val rows = defaultQwertyRows().toMutableList()
            // 替換最後一行（功能行），加入返回鍵
            if (rows.size >= 2) {
                val lastRow = rows.last().toMutableList()
                // 在最前面加返回鍵
                lastRow.add(0, KeyModel.special("⌨返回", KeyboardView.KEYCODE_BACK_NORMAL, widthWeight = 1.3f))
                rows[rows.lastIndex] = lastRow
            }
            return rows
        }

        // =================================================================
        // 橫向模式：側邊面板
        // =================================================================

        /**
         * 橫向一般模式側邊面板：方向鍵 + 剪貼簿 + 常用功能
         */
        fun landscapeNormalSidePanel(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.special("復原", KeyboardView.KEYCODE_CTRL_Z, widthWeight = 1.5f),
                KeyModel.special("重做", KeyboardView.KEYCODE_CTRL_Y, widthWeight = 1.5f),
                KeyModel.special("全選", KeyboardView.KEYCODE_CTRL_A, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("複製", KeyboardView.KEYCODE_CTRL_C, widthWeight = 1.5f),
                KeyModel.special("貼上", KeyboardView.KEYCODE_CTRL_V, widthWeight = 1.5f),
                KeyModel.special("剪下", KeyboardView.KEYCODE_CTRL_X, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT, widthWeight = 1.5f),
                KeyModel.special("↑", KeyboardView.KEYCODE_ARROW_UP, widthWeight = 1.5f),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("Home", KeyboardView.KEYCODE_DEV_HOME, widthWeight = 1.5f),
                KeyModel.special("↓", KeyboardView.KEYCODE_ARROW_DOWN, widthWeight = 1.5f),
                KeyModel.special("End", KeyboardView.KEYCODE_DEV_END, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.5f),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.5f)
            )
        )

        /**
         * 橫向設計者模式側邊面板：所有設計工具常駐顯示
         */
        fun landscapeDesignerSidePanel(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.special("群組", KeyboardView.KEYCODE_DESIGN_GROUP, widthWeight = 1.5f),
                KeyModel.special("解散", KeyboardView.KEYCODE_DESIGN_UNGROUP, widthWeight = 1.5f),
                KeyModel.special("複製", KeyboardView.KEYCODE_DESIGN_DUPLICATE, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("靠左", KeyboardView.KEYCODE_DESIGN_ALIGN_L, widthWeight = 1.5f),
                KeyModel.special("置中", KeyboardView.KEYCODE_DESIGN_ALIGN_C, widthWeight = 1.5f),
                KeyModel.special("靠右", KeyboardView.KEYCODE_DESIGN_ALIGN_R, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("上層", KeyboardView.KEYCODE_DESIGN_LAYER_UP, widthWeight = 1.5f),
                KeyModel.special("下層", KeyboardView.KEYCODE_DESIGN_LAYER_DOWN, widthWeight = 1.5f),
                KeyModel.special("鎖定", KeyboardView.KEYCODE_DESIGN_LOCK, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("放大", KeyboardView.KEYCODE_DESIGN_ZOOM_IN, widthWeight = 1.5f),
                KeyModel.special("縮小", KeyboardView.KEYCODE_DESIGN_ZOOM_OUT, widthWeight = 1.5f),
                KeyModel.special("100%", KeyboardView.KEYCODE_DESIGN_ZOOM_FIT, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("粗體", KeyboardView.KEYCODE_DESIGN_BOLD, widthWeight = 1.5f),
                KeyModel.special("斜體", KeyboardView.KEYCODE_DESIGN_ITALIC, widthWeight = 1.5f),
                KeyModel.special("底線", KeyboardView.KEYCODE_DESIGN_UNDERLINE, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("水平翻", KeyboardView.KEYCODE_DESIGN_FLIP_H, widthWeight = 1.5f),
                KeyModel.special("垂直翻", KeyboardView.KEYCODE_DESIGN_FLIP_V, widthWeight = 1.5f),
                KeyModel.special("隱藏", KeyboardView.KEYCODE_DESIGN_HIDE, widthWeight = 1.5f)
            )
        )

        /**
         * 橫向開發者模式側邊面板：常用符號 + 組合鍵 + 快捷功能
         */
        fun landscapeDeveloperSidePanel(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.special("->", KeyboardView.KEYCODE_DEV_ARROW, widthWeight = 1.5f),
                KeyModel.special("=>", KeyboardView.KEYCODE_DEV_FAT_ARROW, widthWeight = 1.5f),
                KeyModel.special("//", KeyboardView.KEYCODE_DEV_DSLASH, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("/**/", KeyboardView.KEYCODE_DEV_COMMENT_BLOCK, widthWeight = 1.5f),
                KeyModel.special("找", KeyboardView.KEYCODE_DEV_FIND, widthWeight = 1.5f),
                KeyModel.special("註解", KeyboardView.KEYCODE_DEV_COMMENT, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("行↑", KeyboardView.KEYCODE_DEV_LINE_UP, widthWeight = 1.5f),
                KeyModel.special("行↓", KeyboardView.KEYCODE_DEV_LINE_DOWN, widthWeight = 1.5f),
                KeyModel.special("格式化", KeyboardView.KEYCODE_DEV_FORMAT, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("摺疊", KeyboardView.KEYCODE_DEV_FOLD, widthWeight = 1.5f),
                KeyModel.special("展開", KeyboardView.KEYCODE_DEV_UNFOLD, widthWeight = 1.5f),
                KeyModel.special("縮排", KeyboardView.KEYCODE_DEV_INDENT, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("Home", KeyboardView.KEYCODE_DEV_HOME, widthWeight = 1.5f),
                KeyModel.special("End", KeyboardView.KEYCODE_DEV_END, widthWeight = 1.5f),
                KeyModel.special("反縮", KeyboardView.KEYCODE_DEV_UNINDENT, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("復原", KeyboardView.KEYCODE_CTRL_Z, widthWeight = 1.5f),
                KeyModel.special("重做", KeyboardView.KEYCODE_CTRL_Y, widthWeight = 1.5f),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f)
            )
        )

        /**
         * 橫向一般模式主鍵盤：精簡版 QWERTY（少掉功能行，功能移到側邊）
         */
        fun landscapeQwertyRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("1", altChars = listOf("１", "一", "壹", "①", "¹", "₁")),
                KeyModel.char("2", altChars = listOf("２", "二", "貳", "②", "²", "₂")),
                KeyModel.char("3", altChars = listOf("３", "三", "參", "③", "³", "₃")),
                KeyModel.char("4", altChars = listOf("４", "四", "肆", "④", "⁴", "₄")),
                KeyModel.char("5", altChars = listOf("５", "五", "伍", "⑤", "⁵", "₅")),
                KeyModel.char("6", altChars = listOf("６", "六", "陸", "⑥", "⁶", "₆")),
                KeyModel.char("7", altChars = listOf("７", "七", "柒", "⑦", "⁷", "₇")),
                KeyModel.char("8", altChars = listOf("８", "八", "捌", "⑧", "⁸", "₈")),
                KeyModel.char("9", altChars = listOf("９", "九", "玖", "⑨", "⁹", "₉")),
                KeyModel.char("0", altChars = listOf("０", "零", "〇", "⑩", "⁰", "₀"))
            ),
            listOf(
                KeyModel.char("q"), KeyModel.char("w"),
                KeyModel.char("e", altChars = listOf("é", "è", "ê", "ë", "ē")),
                KeyModel.char("r"), KeyModel.char("t"),
                KeyModel.char("y", altChars = listOf("ý", "ÿ")),
                KeyModel.char("u", altChars = listOf("ú", "ù", "û", "ü", "ū")),
                KeyModel.char("i", altChars = listOf("í", "ì", "î", "ï", "ī")),
                KeyModel.char("o", altChars = listOf("ó", "ò", "ô", "ö", "ō")),
                KeyModel.char("p")
            ),
            listOf(
                KeyModel.char("a", altChars = listOf("á", "à", "â", "ä", "ā")),
                KeyModel.char("s", altChars = listOf("ß", "š")),
                KeyModel.char("d"), KeyModel.char("f"), KeyModel.char("g"),
                KeyModel.char("h"), KeyModel.char("j"), KeyModel.char("k"),
                KeyModel.char("l")
            ),
            listOf(
                KeyModel.special("⇧", KeyboardView.KEYCODE_SHIFT, widthWeight = 1.5f),
                KeyModel.char("z"), KeyModel.char("x"),
                KeyModel.char("c", altChars = listOf("ç")),
                KeyModel.char("v"), KeyModel.char("b"),
                KeyModel.char("n", altChars = listOf("ñ")),
                KeyModel.char("m"),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
                KeyModel.special("Ctrl", KeyboardView.KEYCODE_CTRL, widthWeight = 0.8f),
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 0.8f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "、", "；", "：", "「", "」")),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 1.5f),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD, altChars = listOf("。", "！", "？", "…", "～", "·")),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.0f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.3f)
            )
        )

        // =================================================================
        // 拼音佈局（使用 QWERTY 鍵盤，label 顯示中文切換鍵）
        // =================================================================
        fun pinyinRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("q"), KeyModel.char("w"),
                KeyModel.char("e"), KeyModel.char("r"), KeyModel.char("t"),
                KeyModel.char("y"), KeyModel.char("u"),
                KeyModel.char("i"), KeyModel.char("o"),
                KeyModel.char("p")
            ),
            listOf(
                KeyModel.char("a"), KeyModel.char("s"),
                KeyModel.char("d"), KeyModel.char("f"), KeyModel.char("g"),
                KeyModel.char("h"), KeyModel.char("j"), KeyModel.char("k"),
                KeyModel.char("l")
            ),
            listOf(
                KeyModel.special("⇧", KeyboardView.KEYCODE_SHIFT, widthWeight = 1.5f),
                KeyModel.char("z"), KeyModel.char("x"),
                KeyModel.char("c"), KeyModel.char("v"), KeyModel.char("b"),
                KeyModel.char("n"), KeyModel.char("m"),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "、", "；", "：")),
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.0f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2.0f),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD, altChars = listOf("。", "！", "？", "…")),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.3f)
            )
        )

        // =================================================================
        // 倉頡佈局（字母鍵顯示倉頡字根）
        // =================================================================
        fun cangjieRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("q", altChars = listOf("手")),
                KeyModel.char("w", altChars = listOf("田")),
                KeyModel.char("e", altChars = listOf("水")),
                KeyModel.char("r", altChars = listOf("口")),
                KeyModel.char("t", altChars = listOf("廿")),
                KeyModel.char("y", altChars = listOf("卜")),
                KeyModel.char("u", altChars = listOf("山")),
                KeyModel.char("i", altChars = listOf("戈")),
                KeyModel.char("o", altChars = listOf("人")),
                KeyModel.char("p", altChars = listOf("心"))
            ),
            listOf(
                KeyModel.char("a", altChars = listOf("日")),
                KeyModel.char("s", altChars = listOf("尸")),
                KeyModel.char("d", altChars = listOf("木")),
                KeyModel.char("f", altChars = listOf("火")),
                KeyModel.char("g", altChars = listOf("土")),
                KeyModel.char("h", altChars = listOf("竹")),
                KeyModel.char("j", altChars = listOf("十")),
                KeyModel.char("k", altChars = listOf("大")),
                KeyModel.char("l", altChars = listOf("中"))
            ),
            listOf(
                KeyModel.special("⇧", KeyboardView.KEYCODE_SHIFT, widthWeight = 1.5f),
                KeyModel.char("z", altChars = listOf("金")),
                KeyModel.char("x", altChars = listOf("難")),
                KeyModel.char("c", altChars = listOf("金")),
                KeyModel.char("v", altChars = listOf("女")),
                KeyModel.char("b", altChars = listOf("月")),
                KeyModel.char("n", altChars = listOf("弓")),
                KeyModel.char("m", altChars = listOf("一")),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "、")),
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.0f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2.0f),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD, altChars = listOf("。", "！", "？")),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.3f)
            )
        )

        // =================================================================
        // 日文佈局（QWERTY 用於羅馬字輸入）
        // =================================================================
        /** 是否顯示片假名（由 KeyboardView toggle） */
        var isKatakanaMode = false
        /** 是否顯示羅馬拼音 QWERTY 佈局 */
        var isRomajiMode = false

        fun japaneseRows(): List<List<KeyModel>> = when {
            isRomajiMode -> japaneseRomajiRows()
            isKatakanaMode -> japaneseKatakanaRows()
            else -> japaneseHiraganaRows()
        }

        /** 五十音平假名佈局 */
        fun japaneseHiraganaRows(): List<List<KeyModel>> = listOf(
            // あ段
            listOf(
                KeyModel.char("あ", altChars = listOf("ぁ")),
                KeyModel.char("か", altChars = listOf("が")),
                KeyModel.char("さ", altChars = listOf("ざ")),
                KeyModel.char("た", altChars = listOf("だ")),
                KeyModel.char("な"),
                KeyModel.char("は", altChars = listOf("ば", "ぱ")),
                KeyModel.char("ま"),
                KeyModel.char("や", altChars = listOf("ゃ")),
                KeyModel.char("ら"),
                KeyModel.char("わ")
            ),
            // い段
            listOf(
                KeyModel.char("い", altChars = listOf("ぃ")),
                KeyModel.char("き", altChars = listOf("ぎ")),
                KeyModel.char("し", altChars = listOf("じ")),
                KeyModel.char("ち", altChars = listOf("ぢ")),
                KeyModel.char("に"),
                KeyModel.char("ひ", altChars = listOf("び", "ぴ")),
                KeyModel.char("み"),
                KeyModel.char("ゐ", altChars = listOf("ゃ")),
                KeyModel.char("り"),
                KeyModel.char("を")
            ),
            // う段
            listOf(
                KeyModel.char("う", altChars = listOf("ぅ", "ゔ")),
                KeyModel.char("く", altChars = listOf("ぐ")),
                KeyModel.char("す", altChars = listOf("ず")),
                KeyModel.char("つ", altChars = listOf("づ", "っ")),
                KeyModel.char("ぬ"),
                KeyModel.char("ふ", altChars = listOf("ぶ", "ぷ")),
                KeyModel.char("む"),
                KeyModel.char("ゆ", altChars = listOf("ゅ")),
                KeyModel.char("る"),
                KeyModel.char("ん")
            ),
            // え段
            listOf(
                KeyModel.char("え", altChars = listOf("ぇ")),
                KeyModel.char("け", altChars = listOf("げ")),
                KeyModel.char("せ", altChars = listOf("ぜ")),
                KeyModel.char("て", altChars = listOf("で")),
                KeyModel.char("ね"),
                KeyModel.char("へ", altChars = listOf("べ", "ぺ")),
                KeyModel.char("め"),
                KeyModel.char("ー", altChars = listOf("〜")),
                KeyModel.char("れ"),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.0f)
            ),
            // お段
            listOf(
                KeyModel.char("お", altChars = listOf("ぉ")),
                KeyModel.char("こ", altChars = listOf("ご")),
                KeyModel.char("そ", altChars = listOf("ぞ")),
                KeyModel.char("と", altChars = listOf("ど")),
                KeyModel.char("の"),
                KeyModel.char("ほ", altChars = listOf("ぼ", "ぽ")),
                KeyModel.char("も"),
                KeyModel.char("よ", altChars = listOf("ょ")),
                KeyModel.char("ろ"),
                KeyModel.char("。", altChars = listOf("、", "！", "？", "…"))
            ),
            // 底行
            listOf(
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
                KeyModel.special("カナ", KeyboardView.KEYCODE_TOGGLE_KANA, widthWeight = 1.0f),
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 0.8f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 1.5f),
                KeyModel.special("、", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "「", "」")),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.1f)
            )
        )

        /** 五十音片假名佈局 */
        fun japaneseKatakanaRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("ア", altChars = listOf("ァ")),
                KeyModel.char("カ", altChars = listOf("ガ")),
                KeyModel.char("サ", altChars = listOf("ザ")),
                KeyModel.char("タ", altChars = listOf("ダ")),
                KeyModel.char("ナ"),
                KeyModel.char("ハ", altChars = listOf("バ", "パ")),
                KeyModel.char("マ"),
                KeyModel.char("ヤ", altChars = listOf("ャ")),
                KeyModel.char("ラ"),
                KeyModel.char("ワ")
            ),
            listOf(
                KeyModel.char("イ", altChars = listOf("ィ")),
                KeyModel.char("キ", altChars = listOf("ギ")),
                KeyModel.char("シ", altChars = listOf("ジ")),
                KeyModel.char("チ", altChars = listOf("ヂ")),
                KeyModel.char("ニ"),
                KeyModel.char("ヒ", altChars = listOf("ビ", "ピ")),
                KeyModel.char("ミ"),
                KeyModel.char("ヰ", altChars = listOf("ャ")),
                KeyModel.char("リ"),
                KeyModel.char("ヲ")
            ),
            listOf(
                KeyModel.char("ウ", altChars = listOf("ゥ", "ヴ")),
                KeyModel.char("ク", altChars = listOf("グ")),
                KeyModel.char("ス", altChars = listOf("ズ")),
                KeyModel.char("ツ", altChars = listOf("ヅ", "ッ")),
                KeyModel.char("ヌ"),
                KeyModel.char("フ", altChars = listOf("ブ", "プ")),
                KeyModel.char("ム"),
                KeyModel.char("ユ", altChars = listOf("ュ")),
                KeyModel.char("ル"),
                KeyModel.char("ン")
            ),
            listOf(
                KeyModel.char("エ", altChars = listOf("ェ")),
                KeyModel.char("ケ", altChars = listOf("ゲ")),
                KeyModel.char("セ", altChars = listOf("ゼ")),
                KeyModel.char("テ", altChars = listOf("デ")),
                KeyModel.char("ネ"),
                KeyModel.char("ヘ", altChars = listOf("ベ", "ペ")),
                KeyModel.char("メ"),
                KeyModel.char("ー", altChars = listOf("〜")),
                KeyModel.char("レ"),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.0f)
            ),
            listOf(
                KeyModel.char("オ", altChars = listOf("ォ")),
                KeyModel.char("コ", altChars = listOf("ゴ")),
                KeyModel.char("ソ", altChars = listOf("ゾ")),
                KeyModel.char("ト", altChars = listOf("ド")),
                KeyModel.char("ノ"),
                KeyModel.char("ホ", altChars = listOf("ボ", "ポ")),
                KeyModel.char("モ"),
                KeyModel.char("ヨ", altChars = listOf("ョ")),
                KeyModel.char("ロ"),
                KeyModel.char("。", altChars = listOf("、", "！", "？", "…"))
            ),
            listOf(
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
                KeyModel.special("かな", KeyboardView.KEYCODE_TOGGLE_KANA, widthWeight = 1.0f),
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 0.8f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 1.5f),
                KeyModel.special("、", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "「", "」")),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.1f)
            )
        )

        /** 日文羅馬拼音 QWERTY 佈局（輸入 romaji → 引擎自動轉換為假名/漢字） */
        fun japaneseRomajiRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("q"), KeyModel.char("w"), KeyModel.char("e"),
                KeyModel.char("r"), KeyModel.char("t"), KeyModel.char("y"),
                KeyModel.char("u"), KeyModel.char("i"), KeyModel.char("o"),
                KeyModel.char("p")
            ),
            listOf(
                KeyModel.char("a"), KeyModel.char("s"), KeyModel.char("d"),
                KeyModel.char("f"), KeyModel.char("g"), KeyModel.char("h"),
                KeyModel.char("j"), KeyModel.char("k"), KeyModel.char("l")
            ),
            listOf(
                KeyModel.special("⇧", KeyboardView.KEYCODE_SHIFT, widthWeight = 1.2f),
                KeyModel.char("z"), KeyModel.char("x"), KeyModel.char("c"),
                KeyModel.char("v"), KeyModel.char("b"), KeyModel.char("n"),
                KeyModel.char("m"),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.2f)
            ),
            listOf(
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
                KeyModel.special("あ", KeyboardView.KEYCODE_TOGGLE_KANA, widthWeight = 1.0f),
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 0.8f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 1.5f),
                KeyModel.special("、", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "「", "」")),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.1f)
            )
        )

        // =================================================================
        // 韓文佈局（두벌식 Dubeolsik 標準韓文鍵盤）
        // =================================================================
        fun koreanRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("ㅂ"), KeyModel.char("ㅈ"),
                KeyModel.char("ㄷ"), KeyModel.char("ㄱ"), KeyModel.char("ㅅ"),
                KeyModel.char("ㅛ"), KeyModel.char("ㅕ"),
                KeyModel.char("ㅑ"), KeyModel.char("ㅐ"),
                KeyModel.char("ㅔ")
            ),
            listOf(
                KeyModel.char("ㅁ"), KeyModel.char("ㄴ"),
                KeyModel.char("ㅇ"), KeyModel.char("ㄹ"), KeyModel.char("ㅎ"),
                KeyModel.char("ㅗ"), KeyModel.char("ㅓ"), KeyModel.char("ㅏ"),
                KeyModel.char("ㅣ")
            ),
            listOf(
                KeyModel.special("⇧", KeyboardView.KEYCODE_SHIFT, widthWeight = 1.5f),
                KeyModel.char("ㅋ"), KeyModel.char("ㅌ"),
                KeyModel.char("ㅊ"), KeyModel.char("ㅍ"),
                KeyModel.char("ㅠ"), KeyModel.char("ㅜ"),
                KeyModel.char("ㅡ"),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA),
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.0f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2.0f),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.3f)
            )
        )

        /**
         * 橫向設計者模式主鍵盤：基本文字 + 快捷列（工具在側邊面板）
         */
        fun landscapeDesignerRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.special("復原", KeyboardView.KEYCODE_CTRL_Z, widthWeight = 1.3f),
                KeyModel.special("重做", KeyboardView.KEYCODE_CTRL_Y, widthWeight = 1.3f),
                KeyModel.special("複製", KeyboardView.KEYCODE_CTRL_C, widthWeight = 1.3f),
                KeyModel.special("貼上", KeyboardView.KEYCODE_CTRL_V, widthWeight = 1.3f),
                KeyModel.special("剪下", KeyboardView.KEYCODE_CTRL_X, widthWeight = 1.3f),
                KeyModel.special("全選", KeyboardView.KEYCODE_CTRL_A, widthWeight = 1.3f),
                KeyModel.special("字+", KeyboardView.KEYCODE_DESIGN_FONT_UP),
                KeyModel.special("字-", KeyboardView.KEYCODE_DESIGN_FONT_DOWN)
            ),
            listOf(
                KeyModel.special("最上層", KeyboardView.KEYCODE_DESIGN_LAYER_TOP, widthWeight = 1.5f),
                KeyModel.special("最下層", KeyboardView.KEYCODE_DESIGN_LAYER_BOT, widthWeight = 1.5f),
                KeyModel.special("複製物件", KeyboardView.KEYCODE_DESIGN_DUPLICATE, widthWeight = 2f),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("⌨返回", KeyboardView.KEYCODE_BACK_NORMAL, widthWeight = 1.3f),
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT),
                KeyModel.special("↑", KeyboardView.KEYCODE_ARROW_UP),
                KeyModel.special("↓", KeyboardView.KEYCODE_ARROW_DOWN),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT),
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.0f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.3f)
            )
        )

        // =================================================================
        // 語言套件鍵盤佈局（QWERTY + 語言專屬長按替代字元）
        // =================================================================

        /** 德文鍵盤 */
        fun germanRows(): List<List<KeyModel>> = latinQwertyRows(
            altE = listOf("é", "è", "ê", "ë"),
            altA = listOf("ä", "à", "á", "â"),
            altO = listOf("ö", "ó", "ò", "ô"),
            altU = listOf("ü", "ú", "ù", "û"),
            altS = listOf("ß", "š"),
            altN = listOf("ñ"),
            langLabel = "DE"
        )

        /** 法文鍵盤 */
        fun frenchRows(): List<List<KeyModel>> = latinQwertyRows(
            altE = listOf("é", "è", "ê", "ë"),
            altA = listOf("à", "â", "æ", "á"),
            altO = listOf("ô", "œ", "ó"),
            altU = listOf("ù", "û", "ü", "ú"),
            altS = listOf("ß"),
            altN = listOf("ñ"),
            altC = listOf("ç"),
            altI = listOf("î", "ï", "í"),
            altY = listOf("ÿ"),
            langLabel = "FR"
        )

        /** 西班牙文鍵盤 */
        fun spanishRows(): List<List<KeyModel>> = latinQwertyRows(
            altE = listOf("é", "è", "ê"),
            altA = listOf("á", "à", "â"),
            altO = listOf("ó", "ò", "ô"),
            altU = listOf("ú", "ù", "ü", "û"),
            altS = listOf("ß"),
            altN = listOf("ñ"),
            altI = listOf("í", "ì", "î"),
            langLabel = "ES",
            periodAltChars = listOf("。", "¿", "¡", "…", "～")
        )

        /** 越南文鍵盤 */
        fun vietnameseRows(): List<List<KeyModel>> = latinQwertyRows(
            altE = listOf("è", "é", "ẻ", "ẽ", "ẹ", "ê", "ế", "ề", "ể", "ễ", "ệ"),
            altA = listOf("à", "á", "ả", "ã", "ạ", "ă", "ắ", "ằ", "ẳ", "ẵ", "ặ", "â", "ấ", "ầ", "ẩ", "ẫ", "ậ"),
            altO = listOf("ò", "ó", "ỏ", "õ", "ọ", "ô", "ố", "ồ", "ổ", "ỗ", "ộ", "ơ", "ớ", "ờ", "ở", "ỡ", "ợ"),
            altU = listOf("ù", "ú", "ủ", "ũ", "ụ", "ư", "ứ", "ừ", "ử", "ữ", "ự"),
            altD = listOf("đ"),
            altI = listOf("ì", "í", "ỉ", "ĩ", "ị"),
            altY = listOf("ỳ", "ý", "ỷ", "ỹ", "ỵ"),
            langLabel = "VI"
        )

        /** 義大利文鍵盤 */
        fun italianRows(): List<List<KeyModel>> = latinQwertyRows(
            altE = listOf("è", "é", "ê"),
            altA = listOf("à", "á", "â"),
            altO = listOf("ò", "ó", "ô"),
            altU = listOf("ù", "ú", "û"),
            altI = listOf("ì", "í", "î"),
            langLabel = "IT"
        )

        /** 共用拉丁字母 QWERTY 佈局產生器 */
        private fun latinQwertyRows(
            altE: List<String> = emptyList(),
            altA: List<String> = emptyList(),
            altO: List<String> = emptyList(),
            altU: List<String> = emptyList(),
            altS: List<String> = emptyList(),
            altN: List<String> = emptyList(),
            altC: List<String> = emptyList(),
            altI: List<String> = emptyList(),
            altY: List<String> = emptyList(),
            altD: List<String> = emptyList(),
            langLabel: String = "EN",
            periodAltChars: List<String> = listOf("。", "！", "？", "…", "～", "·")
        ): List<List<KeyModel>> = listOf(
            // 數字行
            listOf(
                KeyModel.char("1"), KeyModel.char("2"), KeyModel.char("3"),
                KeyModel.char("4"), KeyModel.char("5"), KeyModel.char("6"),
                KeyModel.char("7"), KeyModel.char("8"), KeyModel.char("9"),
                KeyModel.char("0")
            ),
            listOf(
                KeyModel.char("q"), KeyModel.char("w"),
                KeyModel.char("e", altChars = altE),
                KeyModel.char("r"), KeyModel.char("t"),
                KeyModel.char("y", altChars = altY),
                KeyModel.char("u", altChars = altU),
                KeyModel.char("i", altChars = altI),
                KeyModel.char("o", altChars = altO),
                KeyModel.char("p")
            ),
            listOf(
                KeyModel.char("a", altChars = altA),
                KeyModel.char("s", altChars = altS),
                KeyModel.char("d", altChars = altD),
                KeyModel.char("f"), KeyModel.char("g"),
                KeyModel.char("h"), KeyModel.char("j"), KeyModel.char("k"),
                KeyModel.char("l")
            ),
            listOf(
                KeyModel.special("⇧", KeyboardView.KEYCODE_SHIFT, widthWeight = 1.5f),
                KeyModel.char("z"), KeyModel.char("x"),
                KeyModel.char("c", altChars = altC),
                KeyModel.char("v"), KeyModel.char("b"),
                KeyModel.char("n", altChars = altN),
                KeyModel.char("m"),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special("Ctrl", KeyboardView.KEYCODE_CTRL, widthWeight = 0.8f),
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 0.8f),
                KeyModel.special("?123", KeyboardView.KEYCODE_SWITCH_NUMBERS, widthWeight = 1.0f),
                KeyModel.special("切換", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.0f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "、", "；", "：", "「", "」")),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 1.5f),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD, altChars = periodAltChars),
                KeyModel.special("🎤", KeyboardView.KEYCODE_VOICE, widthWeight = 0.8f),
                KeyModel.special("✍", KeyboardView.KEYCODE_HANDWRITING, widthWeight = 0.8f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.3f)
            )
        )

        /**
         * 橫向開發者模式主鍵盤：符號 + 運算子（工具在側邊面板）
         */
        fun landscapeDeveloperRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("{"), KeyModel.char("}"),
                KeyModel.char("("), KeyModel.char(")"),
                KeyModel.char("["), KeyModel.char("]"),
                KeyModel.char("<"), KeyModel.char(">"),
                KeyModel.char(";"), KeyModel.char(":"),
                KeyModel.char("="), KeyModel.char("+")
            ),
            listOf(
                KeyModel.char("-"), KeyModel.char("*"),
                KeyModel.char("/"), KeyModel.char("%"),
                KeyModel.char("&"), KeyModel.char("|"),
                KeyModel.char("!"), KeyModel.char("?"),
                KeyModel.char("\""), KeyModel.char("'"),
                KeyModel.char("`"), KeyModel.char("~")
            ),
            listOf(
                KeyModel.char("@"), KeyModel.char("#"),
                KeyModel.char("$"), KeyModel.char("^"),
                KeyModel.char("\\"), KeyModel.char("_"),
                KeyModel.special("複製", KeyboardView.KEYCODE_CTRL_C, widthWeight = 1.2f),
                KeyModel.special("貼上", KeyboardView.KEYCODE_CTRL_V, widthWeight = 1.2f),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.2f)
            ),
            listOf(
                KeyModel.special("⌨返回", KeyboardView.KEYCODE_BACK_NORMAL, widthWeight = 1.3f),
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.0f),
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT),
                KeyModel.special("↑", KeyboardView.KEYCODE_ARROW_UP),
                KeyModel.special("↓", KeyboardView.KEYCODE_ARROW_DOWN),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2.5f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.3f)
            )
        )
    }
}

data class KeyModel(
    val label: String,
    val code: Int,
    val widthWeight: Float = 1f,
    val isSpecial: Boolean = false,
    val altChars: List<String> = emptyList()
) {
    companion object {
        fun char(label: String, altChars: List<String> = emptyList()) =
            KeyModel(label, label[0].code, altChars = altChars)
        fun special(label: String, code: Int, widthWeight: Float = 1f, altChars: List<String> = emptyList()) =
            KeyModel(label, code, widthWeight, isSpecial = true, altChars = altChars)
    }
}

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
            // 功能行
            listOf(
                KeyModel.special("Ctrl", KeyboardView.KEYCODE_CTRL, widthWeight = 1.2f),
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.0f),
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT),
                KeyModel.special("↑", KeyboardView.KEYCODE_ARROW_UP),
                KeyModel.special("↓", KeyboardView.KEYCODE_ARROW_DOWN),
                KeyModel.special("注音", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.3f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.5f)
            ),
            listOf(
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "、", "；", "：", "「", "」")),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 7f),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD, altChars = listOf("。", "！", "？", "…", "～", "·"))
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
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.2f),
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT),
                KeyModel.special("↑", KeyboardView.KEYCODE_ARROW_UP),
                KeyModel.special("↓", KeyboardView.KEYCODE_ARROW_DOWN),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2.5f),
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
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.2f),
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT),
                KeyModel.special("↑", KeyboardView.KEYCODE_ARROW_UP),
                KeyModel.special("↓", KeyboardView.KEYCODE_ARROW_DOWN),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2.5f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.2f)
            )
        )

        // =================================================================
        // 注音佈局
        // =================================================================
        fun bopomofoRows(): List<List<KeyModel>> = listOf(
            listOf(
                KeyModel.char("ㄅ"), KeyModel.char("ㄉ"), KeyModel.char("ˇ"),
                KeyModel.char("ˋ"), KeyModel.char("ㄓ"), KeyModel.char("ˊ"),
                KeyModel.char("˙"), KeyModel.char("ㄚ"), KeyModel.char("ㄞ"),
                KeyModel.char("ㄢ"), KeyModel.char("ㄦ")
            ),
            listOf(
                KeyModel.char("ㄆ"), KeyModel.char("ㄊ"), KeyModel.char("ㄍ"),
                KeyModel.char("ㄐ"), KeyModel.char("ㄔ"), KeyModel.char("ㄗ"),
                KeyModel.char("ㄧ"), KeyModel.char("ㄛ"), KeyModel.char("ㄟ"),
                KeyModel.char("ㄣ")
            ),
            listOf(
                KeyModel.char("ㄇ"), KeyModel.char("ㄋ"), KeyModel.char("ㄎ"),
                KeyModel.char("ㄑ"), KeyModel.char("ㄕ"), KeyModel.char("ㄘ"),
                KeyModel.char("ㄨ"), KeyModel.char("ㄜ"), KeyModel.char("ㄠ"),
                KeyModel.char("ㄤ")
            ),
            listOf(
                KeyModel.char("ㄈ"), KeyModel.char("ㄌ"), KeyModel.char("ㄏ"),
                KeyModel.char("ㄒ"), KeyModel.char("ㄖ"), KeyModel.char("ㄙ"),
                KeyModel.char("ㄩ"), KeyModel.char("ㄝ"), KeyModel.char("ㄡ"),
                KeyModel.char("ㄥ")
            ),
            listOf(
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT),
                KeyModel.special("⌫", KeyboardView.KEYCODE_DELETE, widthWeight = 1.3f),
                KeyModel.special("英文", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.3f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 3f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "、", "；")),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD, altChars = listOf("。", "！", "？")),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.3f)
            )
        )

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
                KeyModel.special("Ctrl", KeyboardView.KEYCODE_CTRL, widthWeight = 1.2f),
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.0f),
                KeyModel.special(",", KeyboardView.KEYCODE_COMMA, altChars = listOf("，", "、", "；", "：", "「", "」")),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 4f),
                KeyModel.special(".", KeyboardView.KEYCODE_PERIOD, altChars = listOf("。", "！", "？", "…", "～", "·")),
                KeyModel.special("注音", KeyboardView.KEYCODE_SWITCH_LANG, widthWeight = 1.3f),
                KeyModel.special("↵", KeyboardView.KEYCODE_ENTER, widthWeight = 1.5f)
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
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT),
                KeyModel.special("↑", KeyboardView.KEYCODE_ARROW_UP),
                KeyModel.special("↓", KeyboardView.KEYCODE_ARROW_DOWN),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT),
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.2f),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 2.5f),
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
                KeyModel.special("Tab", KeyboardView.KEYCODE_TAB, widthWeight = 1.2f),
                KeyModel.special("←", KeyboardView.KEYCODE_ARROW_LEFT),
                KeyModel.special("→", KeyboardView.KEYCODE_ARROW_RIGHT),
                KeyModel.special("↑", KeyboardView.KEYCODE_ARROW_UP),
                KeyModel.special("↓", KeyboardView.KEYCODE_ARROW_DOWN),
                KeyModel.special(" ", KeyboardView.KEYCODE_SPACE, widthWeight = 3f),
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

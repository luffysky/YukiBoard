package com.snowrealm.yukiboard.input.langpack

/**
 * 語言套件資料模型。
 *
 * 每個語言套件包含：
 * - 語言識別資訊（id / locale / 顯示名稱）
 * - 字典檔名（assets 或下載到 internal storage）
 * - 鍵盤佈局中的特殊字元（長按替代字元）
 */
data class LanguagePack(
    val id: String,           // e.g. "de", "fr", "es", "vi", "it"
    val locale: String,       // e.g. "de-DE"
    val displayName: String,  // e.g. "Deutsch"
    val nativeName: String,   // e.g. "German"
    val shortLabel: String,   // e.g. "DE" (顯示在語言切換鍵)
    val icon: String,         // e.g. "🇩🇪"
    val dictFileName: String, // e.g. "dict_de.txt"
    val dictWordCount: Int,   // 預估詞數
    val dictSizeMB: Float,    // 下載大小 (MB)
    /** 各字母的長按替代字元 (QWERTY layout 通用) */
    val altChars: Map<Char, List<String>> = emptyMap()
) {
    /** 是否為拉丁字母語言（使用 LatinInputEngine + Trie 詞庫） */
    val isLatinScript: Boolean get() = id in setOf("en", "de", "fr", "es", "vi", "it")

    /** 對應的 InputMode name */
    val inputModeName: String get() = when (id) {
        "zh_bopomofo" -> "BOPOMOFO"
        "zh_pinyin" -> "PINYIN"
        "zh_cangjie" -> "CANGJIE"
        "zh_speed" -> "SPEED_CANGJIE"
        "ja" -> "JAPANESE"
        "ko" -> "KOREAN"
        "en" -> "ENGLISH"
        "de" -> "GERMAN"
        "fr" -> "FRENCH"
        "es" -> "SPANISH"
        "vi" -> "VIETNAMESE"
        "it" -> "ITALIAN"
        else -> id.uppercase()
    }

    companion object {
        /** 所有可用的語言套件定義（含內建語言） */
        val ALL = listOf(
            // ─── 內建語言 ───
            LanguagePack(
                id = "zh_bopomofo", locale = "zh-TW",
                displayName = "注音", nativeName = "Bopomofo",
                shortLabel = "注", icon = "注",
                dictFileName = "builtin", dictWordCount = 0, dictSizeMB = 0f
            ),
            LanguagePack(
                id = "zh_pinyin", locale = "zh-CN",
                displayName = "拼音", nativeName = "Pinyin",
                shortLabel = "拼", icon = "拼",
                dictFileName = "builtin", dictWordCount = 0, dictSizeMB = 0f
            ),
            LanguagePack(
                id = "zh_cangjie", locale = "zh-TW",
                displayName = "倉頡", nativeName = "Cangjie",
                shortLabel = "倉", icon = "倉",
                dictFileName = "builtin", dictWordCount = 0, dictSizeMB = 0f
            ),
            LanguagePack(
                id = "zh_speed", locale = "zh-TW",
                displayName = "速成", nativeName = "Speed Cangjie",
                shortLabel = "速", icon = "速",
                dictFileName = "builtin", dictWordCount = 0, dictSizeMB = 0f
            ),
            LanguagePack(
                id = "en", locale = "en-US",
                displayName = "English", nativeName = "English",
                shortLabel = "EN", icon = "EN",
                dictFileName = "builtin", dictWordCount = 20000, dictSizeMB = 0f
            ),
            LanguagePack(
                id = "ja", locale = "ja-JP",
                displayName = "日本語", nativeName = "Japanese",
                shortLabel = "あ", icon = "あ",
                dictFileName = "builtin", dictWordCount = 0, dictSizeMB = 0f
            ),
            LanguagePack(
                id = "ko", locale = "ko-KR",
                displayName = "한국어", nativeName = "Korean",
                shortLabel = "한", icon = "한",
                dictFileName = "builtin", dictWordCount = 0, dictSizeMB = 0f
            ),
            // ─── 可下載語言套件 ───
            LanguagePack(
                id = "de", locale = "de-DE",
                displayName = "Deutsch", nativeName = "German",
                shortLabel = "DE", icon = "🇩🇪",
                dictFileName = "dict_de.txt",
                dictWordCount = 15000, dictSizeMB = 0.3f,
                altChars = mapOf(
                    'a' to listOf("ä", "à", "á", "â"),
                    'o' to listOf("ö", "ó", "ò", "ô"),
                    'u' to listOf("ü", "ú", "ù", "û"),
                    's' to listOf("ß", "š"),
                    'e' to listOf("é", "è", "ê", "ë")
                )
            ),
            LanguagePack(
                id = "fr", locale = "fr-FR",
                displayName = "Français", nativeName = "French",
                shortLabel = "FR", icon = "🇫🇷",
                dictFileName = "dict_fr.txt",
                dictWordCount = 15000, dictSizeMB = 0.3f,
                altChars = mapOf(
                    'a' to listOf("à", "â", "æ", "á"),
                    'c' to listOf("ç"),
                    'e' to listOf("é", "è", "ê", "ë"),
                    'i' to listOf("î", "ï", "í"),
                    'o' to listOf("ô", "œ", "ó"),
                    'u' to listOf("ù", "û", "ü", "ú"),
                    'y' to listOf("ÿ")
                )
            ),
            LanguagePack(
                id = "es", locale = "es-ES",
                displayName = "Español", nativeName = "Spanish",
                shortLabel = "ES", icon = "🇪🇸",
                dictFileName = "dict_es.txt",
                dictWordCount = 15000, dictSizeMB = 0.3f,
                altChars = mapOf(
                    'a' to listOf("á", "à", "â"),
                    'e' to listOf("é", "è", "ê"),
                    'i' to listOf("í", "ì", "î"),
                    'n' to listOf("ñ"),
                    'o' to listOf("ó", "ò", "ô"),
                    'u' to listOf("ú", "ù", "ü", "û"),
                    '/' to listOf("¿", "¡")
                )
            ),
            LanguagePack(
                id = "vi", locale = "vi-VN",
                displayName = "Tiếng Việt", nativeName = "Vietnamese",
                shortLabel = "VI", icon = "🇻🇳",
                dictFileName = "dict_vi.txt",
                dictWordCount = 12000, dictSizeMB = 0.25f,
                altChars = mapOf(
                    'a' to listOf("à", "á", "ả", "ã", "ạ", "ă", "ắ", "ằ", "ẳ", "ẵ", "ặ", "â", "ấ", "ầ", "ẩ", "ẫ", "ậ"),
                    'd' to listOf("đ"),
                    'e' to listOf("è", "é", "ẻ", "ẽ", "ẹ", "ê", "ế", "ề", "ể", "ễ", "ệ"),
                    'i' to listOf("ì", "í", "ỉ", "ĩ", "ị"),
                    'o' to listOf("ò", "ó", "ỏ", "õ", "ọ", "ô", "ố", "ồ", "ổ", "ỗ", "ộ", "ơ", "ớ", "ờ", "ở", "ỡ", "ợ"),
                    'u' to listOf("ù", "ú", "ủ", "ũ", "ụ", "ư", "ứ", "ừ", "ử", "ữ", "ự"),
                    'y' to listOf("ỳ", "ý", "ỷ", "ỹ", "ỵ")
                )
            ),
            LanguagePack(
                id = "it", locale = "it-IT",
                displayName = "Italiano", nativeName = "Italian",
                shortLabel = "IT", icon = "🇮🇹",
                dictFileName = "dict_it.txt",
                dictWordCount = 15000, dictSizeMB = 0.3f,
                altChars = mapOf(
                    'a' to listOf("à", "á", "â"),
                    'e' to listOf("è", "é", "ê"),
                    'i' to listOf("ì", "í", "î"),
                    'o' to listOf("ò", "ó", "ô"),
                    'u' to listOf("ù", "ú", "û")
                )
            )
        )

        fun getById(id: String): LanguagePack? = ALL.find { it.id == id }
    }
}

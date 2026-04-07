package com.snowrealm.yukiboard.ui.panel

/**
 * 剪貼簿項目自動分類標籤。
 * 根據內容 regex 自動判斷類型。
 */
enum class ClipContentTag(val label: String, val icon: String) {
    TEXT("文字", "\uD83D\uDCDD"),       // 📝
    LINK("連結", "\uD83D\uDD17"),       // 🔗
    EMAIL("信箱", "\uD83D\uDCE7"),      // 📧
    PHONE("電話", "\uD83D\uDCDE"),      // 📞
    IMAGE("圖片", "\uD83D\uDDBC"),      // 🖼️
    FILE("檔案", "\uD83D\uDCCE");       // 📎

    companion object {
        // URL pattern (http/https/ftp, or www.)
        private val URL_REGEX = Regex(
            """(https?://|ftp://|www\.)[^\s<>"')\]]+""",
            RegexOption.IGNORE_CASE
        )

        // Email pattern
        private val EMAIL_REGEX = Regex(
            """[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}"""
        )

        // Phone pattern (international and local formats)
        private val PHONE_REGEX = Regex(
            """(\+?\d{1,4}[\s\-]?)?\(?\d{2,4}\)?[\s\-]?\d{3,4}[\s\-]?\d{3,4}"""
        )

        // File path pattern (Unix or Windows)
        private val FILE_REGEX = Regex(
            """([A-Za-z]:\\[^\s<>"'|?*]+|/[^\s<>"'|?*]{2,}(/[^\s<>"'|?*]+)+)"""
        )

        // Image URL pattern
        private val IMAGE_URL_REGEX = Regex(
            """\.(png|jpg|jpeg|gif|webp|bmp|svg|ico)(\?[^\s]*)?$""",
            RegexOption.IGNORE_CASE
        )

        // Credit card pattern (4 groups of 4 digits, with optional separators)
        val CREDIT_CARD_REGEX = Regex(
            """\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?\d{4}\b"""
        )

        // Taiwan ID number pattern
        val TW_ID_REGEX = Regex(
            """\b[A-Z][12]\d{8}\b"""
        )

        // Passport-like pattern
        val PASSPORT_REGEX = Regex(
            """\b[A-Z]{1,2}\d{7,9}\b"""
        )

        /**
         * Detect the content tag for a given text.
         */
        fun detect(text: String): ClipContentTag {
            val trimmed = text.trim()
            return when {
                IMAGE_URL_REGEX.containsMatchIn(trimmed) -> IMAGE
                URL_REGEX.containsMatchIn(trimmed) -> LINK
                EMAIL_REGEX.containsMatchIn(trimmed) -> EMAIL
                PHONE_REGEX.matches(trimmed.replace(Regex("""[^\d+\-() ]"""), "").trim()) -> {
                    // Only tag as phone if the content is mostly a phone number
                    if (trimmed.length <= 20 && PHONE_REGEX.containsMatchIn(trimmed)) PHONE
                    else TEXT
                }
                FILE_REGEX.containsMatchIn(trimmed) -> FILE
                else -> TEXT
            }
        }

        /**
         * Check if text contains sensitive content (credit card, ID number, etc.)
         */
        fun containsSensitiveContent(text: String): Boolean {
            return CREDIT_CARD_REGEX.containsMatchIn(text) ||
                    TW_ID_REGEX.containsMatchIn(text) ||
                    PASSPORT_REGEX.containsMatchIn(text)
        }
    }
}

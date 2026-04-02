package com.snowrealm.yukiboard.input.snippet

import android.content.Context
import android.content.SharedPreferences

/**
 * 開發者模式 Snippet 管理器。
 *
 * 輸入縮寫 + Tab 自動展開為程式碼片段。
 * 預設 snippet 內建，使用者可透過 SharedPreferences 自訂新增。
 *
 * ## 使用範例
 * ```
 * fn + Tab  →  function() {\n\n}
 * if + Tab  →  if () {\n\n}
 * cl + Tab  →  console.log()
 * ```
 *
 * 回傳的 [SnippetResult] 包含展開文字和游標偏移量，
 * 讓 Service 端可以正確定位游標。
 */
class SnippetManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_snippets", Context.MODE_PRIVATE)

    private val builtInSnippets = mapOf(
        "fn"     to SnippetTemplate("function() {\n    \n}", cursorOffset = -3),
        "func"   to SnippetTemplate("function() {\n    \n}", cursorOffset = -3),
        "if"     to SnippetTemplate("if () {\n    \n}", cursorOffset = -3),
        "ife"    to SnippetTemplate("if () {\n    \n} else {\n    \n}", cursorOffset = -22),
        "for"    to SnippetTemplate("for () {\n    \n}", cursorOffset = -3),
        "fore"   to SnippetTemplate("for (const  of ) {\n    \n}", cursorOffset = -3),
        "while"  to SnippetTemplate("while () {\n    \n}", cursorOffset = -3),
        "cl"     to SnippetTemplate("console.log()", cursorOffset = -1),
        "cw"     to SnippetTemplate("console.warn()", cursorOffset = -1),
        "ce"     to SnippetTemplate("console.error()", cursorOffset = -1),
        "try"    to SnippetTemplate("try {\n    \n} catch (e) {\n    \n}", cursorOffset = -22),
        "class"  to SnippetTemplate("class  {\n    constructor() {\n        \n    }\n}", cursorOffset = -46),
        "imp"    to SnippetTemplate("import  from '';", cursorOffset = -8),
        "req"    to SnippetTemplate("const  = require('');", cursorOffset = -15),
        "ret"    to SnippetTemplate("return ", cursorOffset = 0),
        "sw"     to SnippetTemplate("switch () {\n    case :\n        break;\n    default:\n        break;\n}", cursorOffset = -58),
        "todo"   to SnippetTemplate("// TODO: ", cursorOffset = 0),
        "doc"    to SnippetTemplate("/**\n * \n */", cursorOffset = -4),
        // Kotlin
        "kfun"   to SnippetTemplate("fun () {\n    \n}", cursorOffset = -3),
        "kval"   to SnippetTemplate("val  = ", cursorOffset = -3),
        "kvar"   to SnippetTemplate("var  = ", cursorOffset = -3),
        "kwhen"  to SnippetTemplate("when () {\n    \n}", cursorOffset = -3),
        // HTML
        "div"    to SnippetTemplate("<div>\n    \n</div>", cursorOffset = -7),
        "span"   to SnippetTemplate("<span></span>", cursorOffset = -7),
        "ul"     to SnippetTemplate("<ul>\n    <li></li>\n</ul>", cursorOffset = -12),
        // CSS
        "flex"   to SnippetTemplate("display: flex;\njustify-content: center;\nalign-items: center;", cursorOffset = 0),
        "grid"   to SnippetTemplate("display: grid;\ngrid-template-columns: ;", cursorOffset = -1),
    )

    /**
     * 嘗試展開 snippet。
     * @param abbreviation 縮寫文字
     * @return 展開結果，若無匹配則回傳 null
     */
    fun expand(abbreviation: String): SnippetResult? {
        val trimmed = abbreviation.trim()
        if (trimmed.isEmpty()) return null

        // 先查使用者自訂
        val userExpansion = prefs.getString("snippet_$trimmed", null)
        if (userExpansion != null) {
            return SnippetResult(
                expansion = userExpansion,
                cursorOffset = 0,
                abbreviationLength = trimmed.length
            )
        }

        // 再查內建
        val template = builtInSnippets[trimmed] ?: return null
        return SnippetResult(
            expansion = template.expansion,
            cursorOffset = template.cursorOffset,
            abbreviationLength = trimmed.length
        )
    }

    /** 新增自訂 snippet */
    fun addCustomSnippet(abbreviation: String, expansion: String) {
        prefs.edit().putString("snippet_$abbreviation", expansion).apply()
    }

    /** 移除自訂 snippet */
    fun removeCustomSnippet(abbreviation: String) {
        prefs.edit().remove("snippet_$abbreviation").apply()
    }

    /** 取得所有自訂 snippet */
    fun getCustomSnippets(): Map<String, String> {
        return prefs.all
            .filter { it.key.startsWith("snippet_") }
            .mapKeys { it.key.removePrefix("snippet_") }
            .mapValues { it.value.toString() }
    }

    /** 取得所有內建 snippet 縮寫列表 */
    fun getBuiltInAbbreviations(): List<String> = builtInSnippets.keys.sorted()
}

data class SnippetTemplate(
    val expansion: String,
    val cursorOffset: Int   // 相對於末尾的偏移（負數往左移）
)

data class SnippetResult(
    val expansion: String,
    val cursorOffset: Int,
    val abbreviationLength: Int  // 要刪除的縮寫長度
)

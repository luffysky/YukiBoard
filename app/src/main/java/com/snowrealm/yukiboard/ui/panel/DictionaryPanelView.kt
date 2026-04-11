package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 字典面板 — 支援全球語言的即時查詞。
 *
 * API 來源：
 * - English: Free Dictionary API (dictionaryapi.dev) — 音標 + 詞性 + 定義 + 例句
 * - 其他語言: Wiktionary API — 全球語言支援
 */
class DictionaryPanelView(context: Context) : LinearLayout(context), PanelInputTarget {

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; applyTheme() }

    /** 外部注入：從輸入框取得選取/游標前文字 */
    var inputTextProvider: (() -> String)? = null

    /** 外部注入：將文字插入輸入框 */
    var onInsertText: ((String) -> Unit)? = null

    /** TTS 朗讀回呼 (text, locale code) */
    var onSpeak: ((String, String?) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val density = resources.displayMetrics.density
    private fun dp(n: Int) = (n * density).toInt()

    // ── PanelInputTarget 輸入緩衝 ──
    private val inputBuffer = StringBuilder()
    private var _isInInputMode = true  // 預設開啟輸入模式

    override val isInInputMode: Boolean get() = _isInInputMode

    private fun updateSearchDisplay(composing: String = "") {
        val display = inputBuffer.toString() + composing + "▏"
        searchInput.text = display
    }

    override fun onCharInput(text: String) {
        if (!_isInInputMode) return
        inputBuffer.append(text)
        updateSearchDisplay()
    }

    override fun onDeleteInput() {
        if (!_isInInputMode || inputBuffer.isEmpty()) return
        inputBuffer.deleteCharAt(inputBuffer.lastIndex)
        updateSearchDisplay()
    }

    override fun onSpaceInput() {
        if (!_isInInputMode) return
        inputBuffer.append(" ")
        updateSearchDisplay()
    }

    override fun onEnterInput() {
        if (!_isInInputMode) return
        doSearch()
    }

    override fun onComposedTextInput(text: String) {
        if (!_isInInputMode) return
        inputBuffer.append(text)
        updateSearchDisplay()
    }

    override fun updateComposingHint(hint: String) {
        updateSearchDisplay(hint)
    }

    override fun exitInputMode() {
        _isInInputMode = false
    }

    override fun enterInputMode() {
        _isInInputMode = true
    }

    // ── UI 元件 ──
    private val searchInput: TextView
    private val langSpinner: Spinner
    private val searchBtn: TextView
    private val resultScroll: ScrollView
    private val resultLayout: LinearLayout
    private val statusText: TextView

    private val LANGUAGES = listOf(
        "en" to "English",
        "zh" to "中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "de" to "Deutsch",
        "fr" to "Français",
        "es" to "Español",
        "vi" to "Tiếng Việt",
        "it" to "Italiano",
        "pt" to "Português",
        "ru" to "Русский",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "th" to "ไทย",
        "tr" to "Türkçe",
    )

    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(6), dp(8), dp(6))

        // ── 搜尋列 ──
        val searchRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        langSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                LANGUAGES.map { it.second }).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            layoutParams = LayoutParams(dp(90), LayoutParams.WRAP_CONTENT)
        }
        searchRow.addView(langSpinner)

        searchInput = TextView(context).apply {
            hint = "直接打字搜尋..."
            setTextColor(0xFFE0E8F0.toInt())
            setHintTextColor(0xFF607080.toInt())
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2840.toInt())
                cornerRadius = dp(6).toFloat()
            }
            setOnClickListener {
                _isInInputMode = true
                inputBuffer.clear()
                text = ""
            }
        }
        searchRow.addView(searchInput)

        searchBtn = TextView(context).apply {
            text = "🔍"
            textSize = 18f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { doSearch() }
        }
        searchRow.addView(searchBtn)

        // 自動填入按鈕
        val autoFillBtn = TextView(context).apply {
            text = "📥"
            textSize = 16f
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                val grabbed = inputTextProvider?.invoke()?.trim() ?: ""
                if (grabbed.isNotEmpty()) {
                    val word = grabbed.split("\\s+".toRegex()).lastOrNull() ?: grabbed
                    inputBuffer.clear()
                    inputBuffer.append(word)
                    searchInput.text = word
                    doSearch()
                }
            }
        }
        searchRow.addView(autoFillBtn)

        addView(searchRow)

        statusText = TextView(context).apply {
            text = "輸入單字後按 🔍 查詢"
            setTextColor(0xFF78909C.toInt())
            textSize = 12f
            setPadding(dp(4), dp(4), 0, dp(2))
        }
        addView(statusText)

        // ── 結果區域 ──
        resultLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(4), 0, dp(4), dp(4))
        }
        resultScroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            addView(resultLayout)
        }
        addView(resultScroll)

        applyTheme()
    }

    private fun applyTheme() {
        setBackgroundColor(theme.candidateBarColorInt)
        searchInput.setTextColor(theme.keyLabelColorInt)
    }

    private fun doSearch() {
        val word = inputBuffer.toString().trim()
        if (word.isEmpty()) return
        val langCode = LANGUAGES[langSpinner.selectedItemPosition].first

        resultLayout.removeAllViews()
        statusText.text = "查詢中..."

        scope.launch {
            try {
                val entries = if (langCode == "en") {
                    fetchFreeDictionary(word)
                } else {
                    fetchWiktionary(word, langCode)
                }

                if (entries.isEmpty()) {
                    statusText.text = "找不到「$word」的結果"
                } else {
                    statusText.text = "共 ${entries.size} 筆結果"
                    renderEntries(entries)
                }
            } catch (e: Exception) {
                statusText.text = "查詢失敗: ${e.message}"
            }
        }
    }

    // ═══════════════════════════════════════════
    // Free Dictionary API (English)
    // ═══════════════════════════════════════════

    data class DictEntry(
        val word: String,
        val phonetic: String? = null,
        val partOfSpeech: String? = null,
        val definitions: List<DictDefinition> = emptyList(),
        val origin: String? = null,
    )

    data class DictDefinition(
        val definition: String,
        val example: String? = null,
        val synonyms: List<String> = emptyList(),
    )

    private suspend fun fetchFreeDictionary(word: String): List<DictEntry> = withContext(Dispatchers.IO) {
        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/${URLEncoder.encode(word, "UTF-8")}"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) return@withContext emptyList()

        val body = response.body?.string() ?: return@withContext emptyList()
        val jsonArray = try { json.parseToJsonElement(body).jsonArray } catch (_: Exception) { return@withContext emptyList() }

        val results = mutableListOf<DictEntry>()

        for (entry in jsonArray) {
            val obj = entry.jsonObject
            val w = obj["word"]?.jsonPrimitive?.contentOrNull ?: word
            val phonetic = obj["phonetic"]?.jsonPrimitive?.contentOrNull
                ?: obj["phonetics"]?.jsonArray?.firstOrNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull != null }
                    ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull

            val meanings = obj["meanings"]?.jsonArray ?: continue
            for (meaning in meanings) {
                val mObj = meaning.jsonObject
                val pos = mObj["partOfSpeech"]?.jsonPrimitive?.contentOrNull

                val defs = mObj["definitions"]?.jsonArray?.map { d ->
                    val dObj = d.jsonObject
                    DictDefinition(
                        definition = dObj["definition"]?.jsonPrimitive?.contentOrNull ?: "",
                        example = dObj["example"]?.jsonPrimitive?.contentOrNull,
                        synonyms = dObj["synonyms"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    )
                } ?: emptyList()

                results.add(DictEntry(
                    word = w, phonetic = phonetic, partOfSpeech = pos, definitions = defs
                ))
            }
        }
        results
    }

    // ═══════════════════════════════════════════
    // Wiktionary API (all languages)
    // ═══════════════════════════════════════════

    private suspend fun fetchWiktionary(word: String, lang: String): List<DictEntry> = withContext(Dispatchers.IO) {
        // 用對應語言的 Wiktionary
        val wikiLang = when (lang) {
            "zh" -> "zh"
            "ja" -> "ja"
            "ko" -> "ko"
            else -> lang
        }
        val encoded = URLEncoder.encode(word, "UTF-8")
        val url = "https://$wikiLang.wiktionary.org/api/rest_v1/page/definition/$encoded"
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            // Fallback: 用英文 Wiktionary 查該語言
            return@withContext fetchWiktionaryEnFallback(word, lang)
        }

        val body = response.body?.string() ?: return@withContext emptyList()
        val root = try { json.parseToJsonElement(body).jsonObject } catch (_: Exception) { return@withContext emptyList() }

        val results = mutableListOf<DictEntry>()

        // Wiktionary REST API 回傳格式: { "$lang": [ { partOfSpeech, language, definitions: [...] } ] }
        for ((_, value) in root) {
            val sections = try { value.jsonArray } catch (_: Exception) { continue }
            for (section in sections) {
                val sObj = section.jsonObject
                val pos = sObj["partOfSpeech"]?.jsonPrimitive?.contentOrNull

                val defs = sObj["definitions"]?.jsonArray?.mapNotNull { d ->
                    val dObj = d.jsonObject
                    val def = dObj["definition"]?.jsonPrimitive?.contentOrNull
                        ?.replace(Regex("<[^>]*>"), "") // 移除 HTML tags
                        ?: return@mapNotNull null
                    val examples = dObj["examples"]?.jsonArray?.mapNotNull {
                        it.jsonPrimitive.contentOrNull?.replace(Regex("<[^>]*>"), "")
                    } ?: emptyList()
                    DictDefinition(
                        definition = def,
                        example = examples.firstOrNull()
                    )
                } ?: emptyList()

                if (defs.isNotEmpty()) {
                    results.add(DictEntry(word = word, partOfSpeech = pos, definitions = defs))
                }
            }
        }
        results
    }

    private suspend fun fetchWiktionaryEnFallback(word: String, lang: String): List<DictEntry> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(word, "UTF-8")
        val url = "https://en.wiktionary.org/api/rest_v1/page/definition/$encoded"
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) return@withContext emptyList()

        val body = response.body?.string() ?: return@withContext emptyList()
        val root = try { json.parseToJsonElement(body).jsonObject } catch (_: Exception) { return@withContext emptyList() }

        val results = mutableListOf<DictEntry>()
        for ((_, value) in root) {
            val sections = try { value.jsonArray } catch (_: Exception) { continue }
            for (section in sections) {
                val sObj = section.jsonObject
                val sectionLang = sObj["language"]?.jsonPrimitive?.contentOrNull ?: ""
                // 過濾只要目標語言（如 "French", "German" 等）
                val targetLangName = langNameForCode(lang)
                if (targetLangName.isNotEmpty() && !sectionLang.equals(targetLangName, ignoreCase = true)) continue

                val pos = sObj["partOfSpeech"]?.jsonPrimitive?.contentOrNull
                val defs = sObj["definitions"]?.jsonArray?.mapNotNull { d ->
                    val dObj = d.jsonObject
                    val def = dObj["definition"]?.jsonPrimitive?.contentOrNull
                        ?.replace(Regex("<[^>]*>"), "") ?: return@mapNotNull null
                    val examples = dObj["examples"]?.jsonArray?.mapNotNull {
                        it.jsonPrimitive.contentOrNull?.replace(Regex("<[^>]*>"), "")
                    } ?: emptyList()
                    DictDefinition(definition = def, example = examples.firstOrNull())
                } ?: emptyList()

                if (defs.isNotEmpty()) {
                    results.add(DictEntry(word = word, partOfSpeech = pos, definitions = defs))
                }
            }
        }
        results
    }

    private fun langNameForCode(code: String): String = when (code) {
        "de" -> "German"; "fr" -> "French"; "es" -> "Spanish"
        "it" -> "Italian"; "pt" -> "Portuguese"; "vi" -> "Vietnamese"
        "ru" -> "Russian"; "ar" -> "Arabic"; "hi" -> "Hindi"
        "th" -> "Thai"; "tr" -> "Turkish"; "ja" -> "Japanese"
        "ko" -> "Korean"; "zh" -> "Chinese"
        else -> ""
    }

    // ═══════════════════════════════════════════
    // 渲染結果
    // ═══════════════════════════════════════════

    private fun renderEntries(entries: List<DictEntry>) {
        resultLayout.removeAllViews()

        for (entry in entries) {
            val card = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF1A2840.toInt())
                    cornerRadius = dp(8).toFloat()
                }
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(4), 0, dp(4))
                }
            }

            // 單字 + 音標 + 朗讀
            val langCode = LANGUAGES[langSpinner.selectedItemPosition].first
            val ttsLocale = langCodeToLocale(langCode)
            val headerRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            headerRow.addView(TextView(context).apply {
                text = entry.word
                setTextColor(0xFF64B5F6.toInt())
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            })
            if (!entry.phonetic.isNullOrBlank()) {
                headerRow.addView(TextView(context).apply {
                    text = "  ${entry.phonetic}"
                    setTextColor(0xFF90CAF9.toInt())
                    textSize = 13f
                })
            }
            // TTS 朗讀
            headerRow.addView(TextView(context).apply {
                text = "🔊"
                textSize = 18f
                setPadding(dp(8), 0, dp(4), 0)
                setOnClickListener { onSpeak?.invoke(entry.word, ttsLocale) }
            })
            card.addView(headerRow)

            // 詞性
            if (!entry.partOfSpeech.isNullOrBlank()) {
                card.addView(TextView(context).apply {
                    text = entry.partOfSpeech
                    setTextColor(0xFFFFAB40.toInt())
                    textSize = 12f
                    setTypeface(null, Typeface.ITALIC)
                    setPadding(0, dp(2), 0, dp(2))
                })
            }

            // 定義
            for ((i, def) in entry.definitions.withIndex()) {
                if (i >= 5) break // 最多顯示 5 個

                card.addView(TextView(context).apply {
                    text = "${i + 1}. ${def.definition}"
                    setTextColor(0xFFE0E8F0.toInt())
                    textSize = 13f
                    setPadding(dp(4), dp(2), 0, 0)
                })

                if (!def.example.isNullOrBlank()) {
                    card.addView(TextView(context).apply {
                        text = "  \"${def.example}\""
                        setTextColor(0xFF80CBC4.toInt())
                        textSize = 12f
                        setTypeface(null, Typeface.ITALIC)
                        setPadding(dp(8), 0, 0, dp(2))
                    })
                }

                if (def.synonyms.isNotEmpty()) {
                    card.addView(TextView(context).apply {
                        text = "  同義: ${def.synonyms.take(5).joinToString(", ")}"
                        setTextColor(0xFF78909C.toInt())
                        textSize = 11f
                        setPadding(dp(8), 0, 0, dp(2))
                    })
                }
            }

            // 長按插入單字
            card.setOnLongClickListener {
                onInsertText?.invoke(entry.word)
                true
            }

            resultLayout.addView(card)
        }
    }

    private fun langCodeToLocale(lang: String): String = when (lang) {
        "en" -> "en-US"
        "ja" -> "ja-JP"
        "ko" -> "ko-KR"
        "zh" -> "zh-TW"
        "de" -> "de-DE"
        "fr" -> "fr-FR"
        "es" -> "es-ES"
        "vi" -> "vi-VN"
        "it" -> "it-IT"
        else -> lang
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
}

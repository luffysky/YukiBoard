package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 語言學習面板 — 查詞時提供完整學習資訊。
 *
 * 功能：
 * - 英文：單字 + 音標 + 詞性 + 定義 + 例句
 * - 日文：單字 + 讀音 + 詞性 + 定義 + 例句
 * - 其他語言：單字 + 詞性 + 定義 + 例句
 * - 可從輸入框自動抓取文字查詢
 * - 學習筆記收藏功能
 */
class LangLearnPanelView(context: Context) : LinearLayout(context), PanelInputTarget {

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; applyTheme() }

    var inputTextProvider: (() -> String)? = null
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
    private var _isInInputMode = true

    override val isInInputMode: Boolean get() = _isInInputMode

    private fun updateSearchDisplay(composing: String = "") {
        val display = inputBuffer.toString() + composing + "\u2581"
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
        doLearn()
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

    // 收藏的單字
    private val savedWords = context.getSharedPreferences("lang_learn", Context.MODE_PRIVATE)

    private val searchInput: TextView
    private val langSpinner: Spinner
    private val resultScroll: ScrollView
    private val resultLayout: LinearLayout
    private val statusText: TextView
    private val tabRow: LinearLayout

    private var currentTab = "search" // "search" | "saved"

    private val LANGUAGES = listOf(
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
        "zh" to "中文",
        "de" to "Deutsch",
        "fr" to "Français",
        "es" to "Español",
        "vi" to "Tiếng Việt",
        "it" to "Italiano",
    )

    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(6), dp(8), dp(6))

        // ── Tab 列 ──
        tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        fun makeTab(label: String, id: String): TextView {
            return TextView(context).apply {
                text = label
                textSize = 13f
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener {
                    currentTab = id
                    refreshTabs()
                    if (id == "saved") showSavedWords()
                }
            }
        }

        tabRow.addView(makeTab("🔍 查詢", "search"))
        tabRow.addView(makeTab("⭐ 收藏", "saved"))
        addView(tabRow)

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

        val searchBtn = TextView(context).apply {
            text = "📖"
            textSize = 18f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { doLearn() }
        }
        searchRow.addView(searchBtn)

        // 從輸入框抓取
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
                    doLearn()
                }
            }
        }
        searchRow.addView(autoFillBtn)

        addView(searchRow)

        statusText = TextView(context).apply {
            text = "🎓 輸入單字開始學習"
            setTextColor(0xFF78909C.toInt())
            textSize = 12f
            setPadding(dp(4), dp(4), 0, dp(2))
        }
        addView(statusText)

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
        refreshTabs()
    }

    private fun applyTheme() {
        setBackgroundColor(theme.candidateBarColorInt)
    }

    private fun refreshTabs() {
        for (i in 0 until tabRow.childCount) {
            val tab = tabRow.getChildAt(i) as? TextView ?: continue
            val id = if (i == 0) "search" else "saved"
            if (id == currentTab) {
                tab.setTextColor(0xFF64B5F6.toInt())
                tab.setTypeface(null, Typeface.BOLD)
            } else {
                tab.setTextColor(0xFF78909C.toInt())
                tab.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun doLearn() {
        currentTab = "search"
        refreshTabs()

        val word = inputBuffer.toString().trim()
        if (word.isEmpty()) return
        val langCode = LANGUAGES[langSpinner.selectedItemPosition].first

        resultLayout.removeAllViews()
        statusText.text = "查詢中..."

        scope.launch {
            try {
                val result = fetchWordInfo(word, langCode)
                if (result == null) {
                    statusText.text = "找不到「$word」的學習資料"
                } else {
                    statusText.text = "📖 ${result.word}"
                    renderLearnCard(result, langCode)
                }
            } catch (e: Exception) {
                statusText.text = "查詢失敗: ${e.message}"
            }
        }
    }

    // ═══════════════════════════════════════════
    // 查詢 API
    // ═══════════════════════════════════════════

    data class WordInfo(
        val word: String,
        val phonetic: String? = null,
        val reading: String? = null, // 日文讀音
        val entries: List<WordEntry> = emptyList(),
    )

    data class WordEntry(
        val partOfSpeech: String,
        val definitions: List<String>,
        val examples: List<String>,
        val synonyms: List<String> = emptyList(),
    )

    private suspend fun fetchWordInfo(word: String, lang: String): WordInfo? = withContext(Dispatchers.IO) {
        if (lang == "en") {
            fetchEnglishWordInfo(word)
        } else {
            fetchWiktionaryWordInfo(word, lang)
        }
    }

    private suspend fun fetchEnglishWordInfo(word: String): WordInfo? {
        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/${URLEncoder.encode(word, "UTF-8")}"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val arr = try { json.parseToJsonElement(body).jsonArray } catch (_: Exception) { return null }
        if (arr.isEmpty()) return null

        val first = arr[0].jsonObject
        val w = first["word"]?.jsonPrimitive?.contentOrNull ?: word
        val phonetic = first["phonetic"]?.jsonPrimitive?.contentOrNull
            ?: first["phonetics"]?.jsonArray?.firstOrNull {
                it.jsonObject["text"]?.jsonPrimitive?.contentOrNull != null
            }?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull

        val entries = mutableListOf<WordEntry>()
        for (entry in arr) {
            val meanings = entry.jsonObject["meanings"]?.jsonArray ?: continue
            for (meaning in meanings) {
                val mObj = meaning.jsonObject
                val pos = mObj["partOfSpeech"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                val defs = mutableListOf<String>()
                val examples = mutableListOf<String>()
                val synonyms = mutableListOf<String>()

                mObj["definitions"]?.jsonArray?.forEach { d ->
                    val dObj = d.jsonObject
                    dObj["definition"]?.jsonPrimitive?.contentOrNull?.let { defs.add(it) }
                    dObj["example"]?.jsonPrimitive?.contentOrNull?.let { examples.add(it) }
                    dObj["synonyms"]?.jsonArray?.forEach { s ->
                        s.jsonPrimitive.contentOrNull?.let { synonyms.add(it) }
                    }
                }

                mObj["synonyms"]?.jsonArray?.forEach { s ->
                    s.jsonPrimitive.contentOrNull?.let { if (it !in synonyms) synonyms.add(it) }
                }

                entries.add(WordEntry(pos, defs.take(5), examples.take(3), synonyms.take(5)))
            }
        }

        return WordInfo(w, phonetic, entries = entries)
    }

    private suspend fun fetchWiktionaryWordInfo(word: String, lang: String): WordInfo? {
        val wikiLang = lang
        val encoded = URLEncoder.encode(word, "UTF-8")

        // 先嘗試對應語言的 Wiktionary
        var url = "https://$wikiLang.wiktionary.org/api/rest_v1/page/definition/$encoded"
        var request = Request.Builder().url(url).header("Accept", "application/json").build()
        var response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            // Fallback 到英文 Wiktionary
            url = "https://en.wiktionary.org/api/rest_v1/page/definition/$encoded"
            request = Request.Builder().url(url).header("Accept", "application/json").build()
            response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
        }

        val body = response.body?.string() ?: return null
        val root = try { json.parseToJsonElement(body).jsonObject } catch (_: Exception) { return null }

        val entries = mutableListOf<WordEntry>()

        for ((_, value) in root) {
            val sections = try { value.jsonArray } catch (_: Exception) { continue }
            for (section in sections) {
                val sObj = section.jsonObject
                val pos = sObj["partOfSpeech"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                val defs = mutableListOf<String>()
                val examples = mutableListOf<String>()

                sObj["definitions"]?.jsonArray?.forEach { d ->
                    val dObj = d.jsonObject
                    dObj["definition"]?.jsonPrimitive?.contentOrNull
                        ?.replace(Regex("<[^>]*>"), "")
                        ?.let { defs.add(it) }
                    dObj["examples"]?.jsonArray?.forEach { ex ->
                        ex.jsonPrimitive.contentOrNull
                            ?.replace(Regex("<[^>]*>"), "")
                            ?.let { examples.add(it) }
                    }
                }

                if (defs.isNotEmpty()) {
                    entries.add(WordEntry(pos, defs.take(5), examples.take(3)))
                }
            }
        }

        if (entries.isEmpty()) return null
        return WordInfo(word, entries = entries)
    }

    // ═══════════════════════════════════════════
    // 渲染學習卡片
    // ═══════════════════════════════════════════

    private fun renderLearnCard(info: WordInfo, lang: String) {
        resultLayout.removeAllViews()

        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2840.toInt())
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(4), 0, dp(4))
            }
        }

        // ── 標題列：單字 + 收藏按鈕 ──
        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        titleRow.addView(TextView(context).apply {
            text = info.word
            setTextColor(0xFF64B5F6.toInt())
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })

        val isSaved = savedWords.contains("word_${info.word}")
        val saveBtn = TextView(context).apply {
            text = if (isSaved) "⭐" else "☆"
            textSize = 20f
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                if (savedWords.contains("word_${info.word}")) {
                    savedWords.edit().remove("word_${info.word}").apply()
                    (it as TextView).text = "☆"
                } else {
                    savedWords.edit().putString("word_${info.word}", "$lang|${info.phonetic ?: ""}").apply()
                    (it as TextView).text = "⭐"
                }
            }
        }
        titleRow.addView(saveBtn)

        // TTS 朗讀按鈕
        val ttsLocale = langCodeToLocale(lang)
        titleRow.addView(TextView(context).apply {
            text = "🔊"
            textSize = 18f
            setPadding(dp(6), 0, dp(4), 0)
            setOnClickListener { onSpeak?.invoke(info.word, ttsLocale) }
        })

        // 插入按鈕
        titleRow.addView(TextView(context).apply {
            text = "📝"
            textSize = 16f
            setPadding(dp(4), 0, 0, 0)
            setOnClickListener { onInsertText?.invoke(info.word) }
        })

        card.addView(titleRow)

        // ── 音標/讀音（點擊朗讀） ──
        if (!info.phonetic.isNullOrBlank()) {
            card.addView(TextView(context).apply {
                text = "🔊 ${info.phonetic}"
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                setPadding(0, dp(2), 0, dp(4))
                setOnClickListener { onSpeak?.invoke(info.word, ttsLocale) }
            })
        }
        if (!info.reading.isNullOrBlank()) {
            card.addView(TextView(context).apply {
                text = "📖 ${info.reading}"
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                setPadding(0, dp(2), 0, dp(4))
                setOnClickListener { onSpeak?.invoke(info.word, ttsLocale) }
            })
        }

        // ── 各詞性區塊 ──
        for (entry in info.entries) {
            // 分隔線
            card.addView(android.view.View(context).apply {
                setBackgroundColor(0xFF2A3A55.toInt())
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply {
                    setMargins(0, dp(6), 0, dp(6))
                }
            })

            // 詞性
            card.addView(TextView(context).apply {
                text = "▸ ${entry.partOfSpeech}"
                setTextColor(0xFFFFAB40.toInt())
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
            })

            // 定義
            for ((i, def) in entry.definitions.withIndex()) {
                card.addView(TextView(context).apply {
                    text = "${i + 1}. $def"
                    setTextColor(0xFFE0E8F0.toInt())
                    textSize = 13f
                    setPadding(dp(8), dp(2), 0, 0)
                })
            }

            // 例句
            if (entry.examples.isNotEmpty()) {
                card.addView(TextView(context).apply {
                    text = "💡 例句"
                    setTextColor(0xFF80CBC4.toInt())
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(8), dp(6), 0, dp(2))
                })
                for (ex in entry.examples) {
                    card.addView(TextView(context).apply {
                        text = "  \"$ex\""
                        setTextColor(0xFF80CBC4.toInt())
                        textSize = 12f
                        setTypeface(null, Typeface.ITALIC)
                        setPadding(dp(12), dp(1), 0, dp(1))
                    })
                }
            }

            // 同義詞
            if (entry.synonyms.isNotEmpty()) {
                card.addView(TextView(context).apply {
                    text = "🔗 同義: ${entry.synonyms.joinToString(", ")}"
                    setTextColor(0xFF78909C.toInt())
                    textSize = 11f
                    setPadding(dp(8), dp(4), 0, dp(2))
                })
            }
        }

        resultLayout.addView(card)
    }

    // ═══════════════════════════════════════════
    // 收藏列表
    // ═══════════════════════════════════════════

    private fun showSavedWords() {
        resultLayout.removeAllViews()
        val allKeys = savedWords.all.keys.filter { it.startsWith("word_") }

        if (allKeys.isEmpty()) {
            statusText.text = "還沒有收藏的單字"
            return
        }

        statusText.text = "⭐ 收藏了 ${allKeys.size} 個單字"

        for (key in allKeys.sorted()) {
            val word = key.removePrefix("word_")
            val value = savedWords.getString(key, "") ?: ""
            val parts = value.split("|", limit = 2)
            val lang = parts.getOrNull(0) ?: "en"
            val phonetic = parts.getOrNull(1) ?: ""

            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF1A2840.toInt())
                    cornerRadius = dp(6).toFloat()
                }
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(2), 0, dp(2))
                }
            }

            row.addView(TextView(context).apply {
                text = word
                setTextColor(0xFF64B5F6.toInt())
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            })

            if (phonetic.isNotBlank()) {
                row.addView(TextView(context).apply {
                    text = phonetic
                    setTextColor(0xFF90CAF9.toInt())
                    textSize = 12f
                    setPadding(dp(4), 0, dp(8), 0)
                })
            }

            row.addView(TextView(context).apply {
                text = lang.uppercase()
                setTextColor(0xFF78909C.toInt())
                textSize = 11f
                setPadding(dp(4), 0, dp(8), 0)
            })

            // TTS 按鈕
            row.addView(TextView(context).apply {
                text = "🔊"
                textSize = 16f
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { onSpeak?.invoke(word, langCodeToLocale(lang)) }
            })

            // 查詢按鈕
            row.addView(TextView(context).apply {
                text = "📖"
                textSize = 16f
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener {
                    inputBuffer.clear()
                    inputBuffer.append(word)
                    searchInput.text = word
                    val idx = LANGUAGES.indexOfFirst { it.first == lang }.coerceAtLeast(0)
                    langSpinner.setSelection(idx)
                    currentTab = "search"
                    refreshTabs()
                    doLearn()
                }
            })

            // 刪除按鈕
            row.addView(TextView(context).apply {
                text = "🗑"
                textSize = 16f
                setPadding(dp(4), 0, 0, 0)
                setOnClickListener {
                    savedWords.edit().remove(key).apply()
                    showSavedWords()
                }
            })

            resultLayout.addView(row)
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

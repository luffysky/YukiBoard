package com.snowrealm.yukiboard.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * MLKit 離線翻譯引擎。
 *
 * 支援語言對應 TranslatePanelView 的 7 種語言。
 * 首次使用需下載離線模型（約 30MB/語言），之後可離線翻譯。
 */
class TranslateEngine {

    private var currentTranslator: Translator? = null
    private var currentFromLang: String = ""
    private var currentToLang: String = ""

    /** 翻譯文字 */
    suspend fun translate(text: String, fromLang: String, toLang: String): String {
        if (text.isBlank()) return ""

        val mlFrom = toMlKitLang(fromLang) ?: return "不支援的語言: $fromLang"
        val mlTo = toMlKitLang(toLang) ?: return "不支援的語言: $toLang"

        val translator = getOrCreateTranslator(mlFrom, mlTo)
        ensureModelDownloaded(translator)

        return suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { e -> cont.resume("翻譯失敗: ${e.message}") }
        }
    }

    /** 檢查離線模型是否已下載 */
    suspend fun isModelDownloaded(langCode: String): Boolean {
        val mlLang = toMlKitLang(langCode) ?: return false
        // MLKit 沒有直接查詢方法，嘗試建立翻譯器並翻譯空字串
        return try {
            val opts = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(mlLang)
                .build()
            val t = Translation.getClient(opts)
            val result = suspendCancellableCoroutine<Boolean> { cont ->
                t.translate("test")
                    .addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { cont.resume(false) }
            }
            t.close()
            result
        } catch (_: Exception) {
            false
        }
    }

    /** 下載離線模型 */
    suspend fun downloadModel(langCode: String): Boolean {
        val mlLang = toMlKitLang(langCode) ?: return false
        val opts = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CHINESE)
            .setTargetLanguage(mlLang)
            .build()
        val translator = Translation.getClient(opts)
        return try {
            ensureModelDownloaded(translator)
            translator.close()
            true
        } catch (_: Exception) {
            translator.close()
            false
        }
    }

    /** 取得支援的語言列表 */
    fun getSupportedLanguages(): List<LangInfo> = SUPPORTED_LANGUAGES

    fun close() {
        currentTranslator?.close()
        currentTranslator = null
    }

    // ─────────────── 內部 ───────────────

    private fun getOrCreateTranslator(from: String, to: String): Translator {
        if (from == currentFromLang && to == currentToLang && currentTranslator != null) {
            return currentTranslator!!
        }

        currentTranslator?.close()
        val opts = TranslatorOptions.Builder()
            .setSourceLanguage(from)
            .setTargetLanguage(to)
            .build()
        val translator = Translation.getClient(opts)
        currentTranslator = translator
        currentFromLang = from
        currentToLang = to
        return translator
    }

    private suspend fun ensureModelDownloaded(translator: Translator) {
        val conditions = DownloadConditions.Builder().build()
        suspendCancellableCoroutine<Unit> { cont ->
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    private fun toMlKitLang(code: String): String? = LANG_MAP[code.lowercase()]

    data class LangInfo(val code: String, val displayName: String)

    companion object {
        private val LANG_MAP = mapOf(
            "zh-tw" to TranslateLanguage.CHINESE,
            "zh-cn" to TranslateLanguage.CHINESE,
            "en" to TranslateLanguage.ENGLISH,
            "ja" to TranslateLanguage.JAPANESE,
            "ko" to TranslateLanguage.KOREAN,
            "es" to TranslateLanguage.SPANISH,
            "fr" to TranslateLanguage.FRENCH,
            "de" to TranslateLanguage.GERMAN
        )

        val SUPPORTED_LANGUAGES = listOf(
            LangInfo("zh-TW", "繁中"),
            LangInfo("en", "英文"),
            LangInfo("ja", "日文"),
            LangInfo("ko", "韓文"),
            LangInfo("es", "西文"),
            LangInfo("fr", "法文"),
            LangInfo("de", "德文")
        )
    }
}

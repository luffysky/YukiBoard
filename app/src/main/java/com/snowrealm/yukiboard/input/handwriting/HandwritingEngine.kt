package com.snowrealm.yukiboard.input.handwriting

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*

/**
 * 手寫辨識引擎 — 封裝 ML Kit Digital Ink Recognition。
 *
 * 支援多語言模型下載、辨識、混合辨識。
 */
class HandwritingEngine(private val context: Context) {

    companion object {
        private const val TAG = "HandwritingEngine"

        /** 支援的語言標籤 */
        val SUPPORTED_LANGUAGES = listOf(
            "zh-Hani-TW" to "繁體中文",
            "en-US" to "English",
            "ja" to "日本語",
            "zh-Hani-CN" to "简体中文"
        )
    }

    private val modelManager = RemoteModelManager.getInstance()

    // 已載入的 recognizer（languageTag → recognizer）
    private val recognizers = mutableMapOf<String, DigitalInkRecognizer>()

    // 已下載的模型
    private val downloadedModels = mutableSetOf<String>()

    // 目前選中的語言
    var activeLanguages: List<String> = listOf("zh-Hani-TW")
        private set

    /** 切換語言（可多選用於混合辨識） */
    fun setLanguages(languageTags: List<String>) {
        activeLanguages = languageTags
        // 確保 recognizer 都已建立
        languageTags.forEach { tag -> getOrCreateRecognizer(tag) }
    }

    /** 檢查某語言模型是否已下載 */
    fun isModelDownloaded(languageTag: String, callback: (Boolean) -> Unit) {
        val modelId = getModelIdentifier(languageTag) ?: run {
            callback(false)
            return
        }
        val model = DigitalInkRecognitionModel.builder(modelId).build()
        modelManager.isModelDownloaded(model)
            .addOnSuccessListener { callback(it) }
            .addOnFailureListener { callback(false) }
    }

    /** 下載語言模型 */
    fun downloadModel(languageTag: String, onProgress: (Boolean) -> Unit) {
        val modelId = getModelIdentifier(languageTag) ?: run {
            onProgress(false)
            return
        }
        val model = DigitalInkRecognitionModel.builder(modelId).build()
        val conditions = DownloadConditions.Builder().build()

        Log.d(TAG, "Downloading model: $languageTag")
        modelManager.download(model, conditions)
            .addOnSuccessListener {
                Log.d(TAG, "Model downloaded: $languageTag")
                downloadedModels.add(languageTag)
                getOrCreateRecognizer(languageTag)
                onProgress(true)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Model download failed: $languageTag", e)
                onProgress(false)
            }
    }

    /** 辨識筆跡（使用目前啟用的語言） */
    fun recognize(ink: Ink, callback: (List<String>) -> Unit) {
        if (activeLanguages.isEmpty()) {
            callback(emptyList())
            return
        }

        if (activeLanguages.size == 1) {
            // 單語辨識
            recognizeSingle(ink, activeLanguages[0], callback)
        } else {
            // 混合辨識：並行送所有 recognizer，合併結果
            recognizeMixed(ink, callback)
        }
    }

    private fun recognizeSingle(ink: Ink, languageTag: String, callback: (List<String>) -> Unit) {
        val recognizer = recognizers[languageTag]
        if (recognizer == null) {
            Log.w(TAG, "No recognizer for $languageTag, trying to create...")
            getOrCreateRecognizer(languageTag)
            callback(emptyList())
            return
        }

        recognizer.recognize(ink)
            .addOnSuccessListener { result ->
                val candidates = result.candidates
                    .map { it.text }
                    .distinct()
                    .take(8)
                callback(candidates)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Recognition failed", e)
                callback(emptyList())
            }
    }

    private fun recognizeMixed(ink: Ink, callback: (List<String>) -> Unit) {
        val allCandidates = mutableListOf<Pair<String, Float>>()
        var remaining = activeLanguages.size

        activeLanguages.forEach { tag ->
            val recognizer = recognizers[tag]
            if (recognizer == null) {
                synchronized(allCandidates) {
                    remaining--
                    if (remaining == 0) emitMerged(allCandidates, callback)
                }
                return@forEach
            }

            recognizer.recognize(ink)
                .addOnSuccessListener { result ->
                    synchronized(allCandidates) {
                        result.candidates.forEach { c ->
                            val score = c.score ?: 0f
                            allCandidates.add(c.text to score)
                        }
                        remaining--
                        if (remaining == 0) emitMerged(allCandidates, callback)
                    }
                }
                .addOnFailureListener {
                    synchronized(allCandidates) {
                        remaining--
                        if (remaining == 0) emitMerged(allCandidates, callback)
                    }
                }
        }
    }

    private fun emitMerged(candidates: List<Pair<String, Float>>, callback: (List<String>) -> Unit) {
        val merged = candidates
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(8)
        callback(merged)
    }

    /** 初始化：檢查已下載的模型，建立 recognizer */
    fun init() {
        SUPPORTED_LANGUAGES.forEach { (tag, _) ->
            isModelDownloaded(tag) { downloaded ->
                if (downloaded) {
                    downloadedModels.add(tag)
                    getOrCreateRecognizer(tag)
                }
            }
        }
    }

    /** 確保已下載預設語言模型（繁中），回傳是否準備好 */
    fun ensureDefaultModel(callback: (Boolean) -> Unit) {
        val defaultTag = "zh-Hani-TW"
        isModelDownloaded(defaultTag) { downloaded ->
            if (downloaded) {
                getOrCreateRecognizer(defaultTag)
                callback(true)
            } else {
                downloadModel(defaultTag) { success -> callback(success) }
            }
        }
    }

    fun isReady(): Boolean = recognizers.isNotEmpty()

    fun getDownloadedLanguages(): List<String> = downloadedModels.toList()

    /** 刪除已下載的語言模型 */
    fun deleteModel(languageTag: String, callback: (Boolean) -> Unit) {
        val modelId = getModelIdentifier(languageTag) ?: run {
            callback(false)
            return
        }
        val model = DigitalInkRecognitionModel.builder(modelId).build()

        // 先關閉 recognizer
        recognizers.remove(languageTag)?.close()

        modelManager.deleteDownloadedModel(model)
            .addOnSuccessListener {
                Log.d(TAG, "Model deleted: $languageTag")
                downloadedModels.remove(languageTag)
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Model delete failed: $languageTag", e)
                callback(false)
            }
    }

    /** 批次檢查所有支援語言的下載狀態，回傳 Map<languageTag, isDownloaded> */
    fun checkAllModelStatus(callback: (Map<String, Boolean>) -> Unit) {
        val result = mutableMapOf<String, Boolean>()
        var remaining = SUPPORTED_LANGUAGES.size

        SUPPORTED_LANGUAGES.forEach { (tag, _) ->
            isModelDownloaded(tag) { downloaded ->
                synchronized(result) {
                    result[tag] = downloaded
                    if (downloaded) downloadedModels.add(tag)
                    remaining--
                    if (remaining == 0) callback(result)
                }
            }
        }
    }

    private fun getOrCreateRecognizer(languageTag: String): DigitalInkRecognizer? {
        recognizers[languageTag]?.let { return it }

        val modelId = getModelIdentifier(languageTag) ?: return null
        val model = DigitalInkRecognitionModel.builder(modelId).build()
        val recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
        recognizers[languageTag] = recognizer
        return recognizer
    }

    private fun getModelIdentifier(languageTag: String): DigitalInkRecognitionModelIdentifier? {
        return try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid language tag: $languageTag", e)
            null
        }
    }

    fun close() {
        recognizers.values.forEach { it.close() }
        recognizers.clear()
    }
}

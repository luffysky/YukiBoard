package com.snowrealm.yukiboard.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * 防詐本地 TFLite 模型推論。
 *
 * 三層架構中的第一層（最快、免費、離線可用）：
 *   Layer 0: TFLite 本地模型  (50~80ms, 離線, 免費)
 *   Layer 1: 規則引擎          (< 1ms, 離線, 免費)
 *   Layer 2: AI 深度分析       (1~3s, 線上, 消耗 token)
 *
 * 載入 assets/fraud_detector.tflite + assets/fraud_vocab.txt，
 * 使用 WordPiece tokenizer 分詞後送入 TFLite Interpreter 推論。
 */
class FraudLocalModel(private val context: Context) {

    companion object {
        private const val TAG = "FraudLocalModel"
        private const val MODEL_ASSET = "fraud_detector.tflite"
        private const val VOCAB_ASSET = "fraud_vocab.txt"
        private const val MAX_SEQ_LENGTH = 256
        private const val UNK_TOKEN = "[UNK]"
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val PAD_TOKEN = "[PAD]"

        /** 標籤對應（與訓練腳本 LABEL_LIST 一致） */
        val LABEL_LIST = listOf(
            "safe",
            "investment",
            "romance",
            "impersonation",
            "shopping",
            "job",
            "loan",
            "lottery",
            "phishing",
        )

        fun labelToFraudType(label: String): FraudAIAnalyzer.FraudType? = when (label) {
            "investment"    -> FraudAIAnalyzer.FraudType.INVESTMENT
            "romance"       -> FraudAIAnalyzer.FraudType.ROMANCE
            "impersonation" -> FraudAIAnalyzer.FraudType.IMPERSONATION
            "shopping"      -> FraudAIAnalyzer.FraudType.SHOPPING
            "job"           -> FraudAIAnalyzer.FraudType.JOB
            "loan"          -> FraudAIAnalyzer.FraudType.LOAN
            "lottery"       -> FraudAIAnalyzer.FraudType.UNKNOWN  // lottery maps to UNKNOWN (中獎)
            "phishing"      -> FraudAIAnalyzer.FraudType.PHISHING
            else            -> null
        }
    }

    data class ModelResult(
        val fraudProbability: Float,      // 0.0 ~ 1.0
        val predictedLabel: String,       // "safe", "investment", etc.
        val fraudType: FraudAIAnalyzer.FraudType?,  // null if safe
        val confidence: Float,            // max softmax probability
        val allProbabilities: Map<String, Float>,
        val latencyMs: Long,
    )

    // TFLite Interpreter — 使用反射避免編譯時強制依賴
    private var interpreter: Any? = null
    private var vocab: Map<String, Int> = emptyMap()
    private var isLoaded = false

    /**
     * 初始化模型 — 載入 TFLite + 詞表。
     * 在背景執行緒呼叫。失敗時 isAvailable() 回傳 false。
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            // 載入詞表
            vocab = loadVocab()
            if (vocab.isEmpty()) {
                Log.w(TAG, "Vocabulary is empty, model not available")
                return@withContext
            }

            // 載入 TFLite 模型
            interpreter = loadInterpreter()
            if (interpreter == null) {
                Log.w(TAG, "Failed to load TFLite interpreter")
                return@withContext
            }

            isLoaded = true
            Log.i(TAG, "Model loaded: vocab=${vocab.size} tokens")
        } catch (e: Exception) {
            Log.w(TAG, "Model initialization failed: ${e.message}")
            isLoaded = false
        }
    }

    /**
     * 模型是否可用。
     */
    fun isAvailable(): Boolean = isLoaded && interpreter != null

    /**
     * 執行推論。
     * @param text 要分析的文本
     * @return ModelResult，包含詐騙機率、類型和延遲
     */
    suspend fun predict(text: String): ModelResult? = withContext(Dispatchers.Default) {
        if (!isAvailable()) return@withContext null

        val startTime = System.currentTimeMillis()

        try {
            // 1. Tokenize
            val tokens = wordPieceTokenize(text)
            val inputIds = IntArray(MAX_SEQ_LENGTH)
            val attentionMask = IntArray(MAX_SEQ_LENGTH)

            // [CLS] + tokens + [SEP] + padding
            val clsId = vocab[CLS_TOKEN] ?: 101
            val sepId = vocab[SEP_TOKEN] ?: 102
            val padId = vocab[PAD_TOKEN] ?: 0

            inputIds[0] = clsId
            attentionMask[0] = 1

            val maxTokens = MAX_SEQ_LENGTH - 2  // reserve for CLS and SEP
            val tokenCount = minOf(tokens.size, maxTokens)

            for (i in 0 until tokenCount) {
                inputIds[i + 1] = tokens[i]
                attentionMask[i + 1] = 1
            }
            inputIds[tokenCount + 1] = sepId
            attentionMask[tokenCount + 1] = 1

            // Pad the rest
            for (i in (tokenCount + 2) until MAX_SEQ_LENGTH) {
                inputIds[i] = padId
                attentionMask[i] = 0
            }

            // 2. Run inference
            val logits = runInference(inputIds, attentionMask) ?: return@withContext null

            // 3. Softmax
            val probabilities = softmax(logits)

            // 4. Find max
            var maxIdx = 0
            var maxProb = probabilities[0]
            for (i in 1 until probabilities.size) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i]
                    maxIdx = i
                }
            }

            val predictedLabel = if (maxIdx < LABEL_LIST.size) LABEL_LIST[maxIdx] else "safe"
            val fraudType = labelToFraudType(predictedLabel)

            // fraud probability = 1 - P(safe)
            val safeProb = probabilities[0]  // index 0 = safe
            val fraudProbability = 1.0f - safeProb

            val allProbs = mutableMapOf<String, Float>()
            for (i in probabilities.indices) {
                if (i < LABEL_LIST.size) {
                    allProbs[LABEL_LIST[i]] = probabilities[i]
                }
            }

            val latency = System.currentTimeMillis() - startTime

            ModelResult(
                fraudProbability = fraudProbability,
                predictedLabel = predictedLabel,
                fraudType = fraudType,
                confidence = maxProb,
                allProbabilities = allProbs,
                latencyMs = latency,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed: ${e.message}")
            null
        }
    }

    /**
     * 釋放模型資源。
     */
    fun release() {
        try {
            interpreter?.let { interp ->
                interp.javaClass.getMethod("close").invoke(interp)
            }
        } catch (_: Exception) { }
        interpreter = null
        isLoaded = false
    }

    // ─── WordPiece Tokenizer ──────────────────────────────────────────────────

    private fun wordPieceTokenize(text: String): List<Int> {
        val unkId = vocab[UNK_TOKEN] ?: 100
        val result = mutableListOf<Int>()

        // Basic pre-tokenization: split on whitespace and punctuation
        val words = basicTokenize(text)

        for (word in words) {
            val subTokens = wordPieceTokenizeWord(word, unkId)
            result.addAll(subTokens)
            if (result.size >= MAX_SEQ_LENGTH - 2) break
        }

        return result
    }

    private fun basicTokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()

        for (ch in text) {
            when {
                ch.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                isCjkCharacter(ch) -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                    result.add(ch.toString())
                }
                isPunctuation(ch) -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                    result.add(ch.toString())
                }
                else -> current.append(ch.lowercaseChar())
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    private fun wordPieceTokenizeWord(word: String, unkId: Int): List<Int> {
        if (word.length > 200) return listOf(unkId)

        val tokens = mutableListOf<Int>()
        var start = 0

        while (start < word.length) {
            var end = word.length
            var found = false

            while (start < end) {
                val substr = if (start > 0) "##${word.substring(start, end)}" else word.substring(start, end)
                val id = vocab[substr]
                if (id != null) {
                    tokens.add(id)
                    found = true
                    start = end
                    break
                }
                end--
            }

            if (!found) {
                tokens.add(unkId)
                start++
            }
        }

        return tokens
    }

    private fun isCjkCharacter(ch: Char): Boolean {
        val cp = ch.code
        return (cp in 0x4E00..0x9FFF) ||
            (cp in 0x3400..0x4DBF) ||
            (cp in 0x20000..0x2A6DF) ||
            (cp in 0x2A700..0x2B73F) ||
            (cp in 0x2B740..0x2B81F) ||
            (cp in 0x2B820..0x2CEAF) ||
            (cp in 0xF900..0xFAFF) ||
            (cp in 0x2F800..0x2FA1F) ||
            (cp in 0x3105..0x312F)  // 注音符號
    }

    private fun isPunctuation(ch: Char): Boolean {
        val cp = ch.code
        if ((cp in 33..47) || (cp in 58..64) || (cp in 91..96) || (cp in 123..126)) return true
        return Character.getType(ch).let {
            it == Character.CONNECTOR_PUNCTUATION.toInt() ||
            it == Character.DASH_PUNCTUATION.toInt() ||
            it == Character.END_PUNCTUATION.toInt() ||
            it == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            it == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            it == Character.OTHER_PUNCTUATION.toInt() ||
            it == Character.START_PUNCTUATION.toInt()
        }
    }

    // ─── TFLite Interpreter (Reflection-based) ────────────────────────────────

    /**
     * 載入 TFLite Interpreter。
     * 使用反射載入，若 TFLite 依賴不存在時不會造成 ClassNotFoundException。
     */
    private fun loadInterpreter(): Any? {
        return try {
            val interpreterClass = Class.forName("org.tensorflow.lite.Interpreter")

            // 嘗試從內部儲存載入（模型更新後的檔案）
            val modelFile = context.getFileStreamPath("fraud_detector.tflite")
            if (modelFile != null && modelFile.exists()) {
                Log.i(TAG, "Loading model from internal storage: ${modelFile.absolutePath}")
                val constructor = interpreterClass.getConstructor(java.io.File::class.java)
                return constructor.newInstance(modelFile)
            }

            // Fallback: 從 assets 載入
            val modelBuffer = loadModelFromAssets()
            if (modelBuffer != null) {
                Log.i(TAG, "Loading model from assets")
                val constructor = interpreterClass.getConstructor(ByteBuffer::class.java)
                return constructor.newInstance(modelBuffer)
            }

            Log.w(TAG, "No model file found")
            null
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "TFLite not available (dependency not included)")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load interpreter: ${e.message}")
            null
        }
    }

    private fun loadModelFromAssets(): MappedByteBuffer? {
        return try {
            val assetFd = context.assets.openFd(MODEL_ASSET)
            val inputStream = FileInputStream(assetFd.fileDescriptor)
            val channel = inputStream.channel
            val startOffset = assetFd.startOffset
            val declaredLength = assetFd.declaredLength
            channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadVocab(): Map<String, Int> {
        val vocabMap = mutableMapOf<String, Int>()

        // Try internal storage first (updated vocab)
        val vocabFile = context.getFileStreamPath("fraud_vocab.txt")
        if (vocabFile != null && vocabFile.exists()) {
            try {
                vocabFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEachIndexed { index, token ->
                        vocabMap[token] = index
                    }
                }
                if (vocabMap.isNotEmpty()) return vocabMap
            } catch (_: Exception) { }
        }

        // Fallback: assets
        return try {
            context.assets.open(VOCAB_ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEachIndexed { index, token ->
                    vocabMap[token] = index
                }
            }
            vocabMap
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * 透過反射呼叫 TFLite Interpreter.run()。
     */
    private fun runInference(inputIds: IntArray, attentionMask: IntArray): FloatArray? {
        val interp = interpreter ?: return null

        return try {
            val numLabels = LABEL_LIST.size

            // Prepare input buffers
            val inputIdBuffer = ByteBuffer.allocateDirect(MAX_SEQ_LENGTH * 4).apply {
                order(ByteOrder.nativeOrder())
                for (id in inputIds) putInt(id)
                rewind()
            }
            val maskBuffer = ByteBuffer.allocateDirect(MAX_SEQ_LENGTH * 4).apply {
                order(ByteOrder.nativeOrder())
                for (m in attentionMask) putInt(m)
                rewind()
            }

            // Prepare output buffer
            val outputBuffer = ByteBuffer.allocateDirect(numLabels * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            // Run: interpreter.runForMultipleInputsOutputs(inputs, outputMap)
            val inputs = arrayOf(inputIdBuffer, maskBuffer)
            val outputs = mutableMapOf<Int, Any>(0 to outputBuffer)

            val runMethod = interp.javaClass.getMethod(
                "runForMultipleInputsOutputs",
                Array<Any>::class.java,
                Map::class.java,
            )
            runMethod.invoke(interp, inputs, outputs)

            // Read output
            outputBuffer.rewind()
            FloatArray(numLabels) { outputBuffer.float }
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            null
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max() ?: 0f
        val exps = FloatArray(logits.size) { kotlin.math.exp(logits[it] - max) }
        val sum = exps.sum()
        return FloatArray(exps.size) { exps[it] / sum }
    }
}

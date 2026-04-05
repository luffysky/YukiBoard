package com.snowrealm.yukiboard.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Google Cloud Text-to-Speech API 語音輸出引擎。
 *
 * 使用 REST API 呼叫 texttospeech.googleapis.com。
 * 支援 WaveNet / Neural2 / Studio 高品質語音。
 *
 * 需要 Google Cloud API Key（在設定頁輸入）。
 * 免費額度：每月 100 萬字（標準）/ 100 萬字（WaveNet）。
 */
class GoogleCloudTtsEngine(
    private val apiKey: String,
    private val voiceName: String = "zh-TW-Wavenet-A",
    private val audioEncoding: String = "LINEAR16"
) : SpeechOutputEngine {

    private val tag = "GoogleCloudTts"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override var isReady: Boolean = true
        private set

    /** 最後一次 API 錯誤訊息（供 UI 顯示） */
    var lastError: String? = null
        private set

    override val isSpeaking: Boolean
        get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    override fun speak(
        text: String,
        locale: String?,
        speed: Float,
        onDone: (() -> Unit)?
    ) {
        if (apiKey.isBlank()) {
            Log.w(tag, "No API key")
            return
        }
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        // 停止前一段
        stop()

        playbackJob = scope.launch {
            try {
                val audioBytes = synthesize(text, locale, speed)
                if (audioBytes != null) {
                    playPcm(audioBytes)
                }
                withContext(Dispatchers.Main) { onDone?.invoke() }
            } catch (e: CancellationException) {
                // cancelled
            } catch (e: Exception) {
                Log.e(tag, "speak failed", e)
                withContext(Dispatchers.Main) { onDone?.invoke() }
            }
        }
    }

    override fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        audioTrack?.release()
        audioTrack = null
    }

    override fun destroy() {
        stop()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }

    // ─────────────── 內部方法 ───────────────

    private fun synthesize(text: String, locale: String?, speed: Float): ByteArray? {
        val langCode = locale ?: detectLanguage(text)

        // 選擇符合語言的 voice
        val selectedVoice = pickVoice(langCode)

        val body = JSONObject().apply {
            put("input", JSONObject().put("text", text))
            put("voice", JSONObject().apply {
                put("languageCode", selectedVoice.first)
                put("name", selectedVoice.second)
            })
            put("audioConfig", JSONObject().apply {
                put("audioEncoding", audioEncoding)
                put("speakingRate", speed.toDouble().coerceIn(0.25, 4.0))
                put("sampleRateHertz", SAMPLE_RATE)
            })
        }

        val request = Request.Builder()
            .url("$API_BASE?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (!response.isSuccessful) {
                    lastError = "HTTP ${response.code}: ${bodyStr?.take(300)}"
                    Log.w(tag, "API error: $lastError")
                    return null
                }
                val json = JSONObject(bodyStr ?: return null)
                val audioContent = json.optString("audioContent", "")
                if (audioContent.isBlank()) {
                    lastError = "API 回傳無 audioContent"
                    return null
                }
                lastError = null
                Base64.decode(audioContent, Base64.DEFAULT)
            }
        } catch (e: Exception) {
            lastError = "網路錯誤: ${e.message}"
            Log.e(tag, "synthesize failed", e)
            null
        }
    }

    /** 根據語言代碼選擇 voice */
    private fun pickVoice(langCode: String): Pair<String, String> {
        val lang = langCode.lowercase()
        return when {
            lang.startsWith("zh-tw") || lang.startsWith("zh-hk") ->
                "zh-TW" to "zh-TW-Wavenet-A"
            lang.startsWith("zh") ->
                "zh-CN" to "cmn-CN-Wavenet-A"
            lang.startsWith("en") ->
                "en-US" to "en-US-Wavenet-D"
            lang.startsWith("ja") ->
                "ja-JP" to "ja-JP-Wavenet-A"
            lang.startsWith("ko") ->
                "ko-KR" to "ko-KR-Wavenet-A"
            lang.startsWith("es") ->
                "es-ES" to "es-ES-Wavenet-B"
            lang.startsWith("fr") ->
                "fr-FR" to "fr-FR-Wavenet-A"
            lang.startsWith("de") ->
                "de-DE" to "de-DE-Wavenet-A"
            else -> "zh-TW" to "zh-TW-Wavenet-A"
        }
    }

    /** 根據文字內容自動偵測語言 */
    private fun detectLanguage(text: String): String {
        val sample = text.take(50)
        return when {
            sample.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' } -> "ja-JP"
            sample.any { it in '\uAC00'..'\uD7AF' } -> "ko-KR"
            sample.any { it in '\u4E00'..'\u9FFF' } -> "zh-TW"
            sample.all { it.code < 128 } -> "en-US"
            else -> "zh-TW"
        }
    }

    /** 播放 LINEAR16 PCM 音訊（含 WAV header 需跳過） */
    private fun playPcm(audioData: ByteArray) {
        // LINEAR16 回傳含 WAV header（44 bytes），跳過
        val pcmOffset = if (audioData.size > 44 &&
            audioData[0] == 'R'.code.toByte() &&
            audioData[1] == 'I'.code.toByte()
        ) 44 else 0
        val pcmData = audioData.copyOfRange(pcmOffset, audioData.size)

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcmData.size)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(pcmData, 0, pcmData.size)
        audioTrack?.play()

        // 等待播放完畢
        val durationMs = (pcmData.size.toLong() * 1000) / (SAMPLE_RATE * 2) // 16-bit mono = 2 bytes/sample
        Thread.sleep(durationMs + 100)

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    companion object {
        private const val API_BASE = "https://texttospeech.googleapis.com/v1/text:synthesize"
        private const val SAMPLE_RATE = 24000
    }
}

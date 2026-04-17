package com.snowrealm.yukiboard.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.snowrealm.yukiboard.app.net.AppHttpClient
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit

/**
 * Faster-Whisper 自架 Server 語音辨識引擎。
 *
 * 流程：AudioRecord 錄音 → WAV → POST 到自架 Faster-Whisper Server → 回傳文字。
 * 支援 SSE streaming partial results。
 *
 * 免費（自架），精準度同 OpenAI Whisper，速度更快。
 */
class FasterWhisperSpeechEngine(
    private val serverUrl: String,
    private val apiKey: String = ""
) : SpeechInputEngine {

    private val tag = "FasterWhisper"

    @Volatile override var isListening: Boolean = false
        private set

    /** 外部可設定上下文文字，提升辨識準確度 */
    var contextText: String? = null

    /** 靜音振幅閾值（可從設定調整） */
    var silenceAmplitudeThreshold: Int = DEFAULT_SILENCE_AMPLITUDE_THRESHOLD

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Faster-Whisper 自架 server：readTimeout 延至 120s 配合長音檔推論，writeTimeout 60s 配合 WAV 上傳
    private val client = AppHttpClient.newBuilder(AppHttpClient.Profile.DEFAULT)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // 錄音參數
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // partial 間隔（毫秒）
    private val partialIntervalMs = 2500L

    override fun startListening(
        locale: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (Int) -> Unit
    ) {
        if (serverUrl.isBlank()) {
            onError(ERROR_NO_SERVER)
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(sampleRate * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat, bufferSize
            )
        } catch (e: SecurityException) {
            onError(ERROR_NO_PERMISSION)
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            onError(ERROR_AUDIO_INIT)
            return
        }

        isListening = true
        audioRecord?.startRecording()

        val lang = locale.substringBefore("-").takeIf { it.length == 2 }
        Log.i(tag, "startListening: server=$serverUrl, locale=$lang")

        recordingJob = scope.launch {
            val pcmStream = ByteArrayOutputStream()
            val buffer = ShortArray(bufferSize / 2)
            var lastPartialTime = System.currentTimeMillis()
            var silenceStartTime = 0L
            val partialAccumulated = StringBuilder()

            try {
                while (isActive && isListening) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        for (i in 0 until read) {
                            pcmStream.write(buffer[i].toInt() and 0xFF)
                            pcmStream.write((buffer[i].toInt() shr 8) and 0xFF)
                        }

                        // 靜音偵測：振幅低於閾值超過 3 秒自動停止
                        val maxAmplitude = (0 until read)
                            .maxOfOrNull { kotlin.math.abs(buffer[it].toInt()) } ?: 0
                        val silenceNow = System.currentTimeMillis()
                        if (maxAmplitude < silenceAmplitudeThreshold) {
                            if (silenceStartTime == 0L) silenceStartTime = silenceNow
                            else if (silenceNow - silenceStartTime >= SILENCE_THRESHOLD_MS
                                     && pcmStream.size() > sampleRate * 2) {
                                Log.i(tag, "silence detected, auto-stopping")
                                isListening = false
                                break
                            }
                        } else {
                            silenceStartTime = 0L
                        }

                        // 定期送 partial
                        val now = System.currentTimeMillis()
                        if (now - lastPartialTime >= partialIntervalMs && pcmStream.size() > sampleRate * 2) {
                            lastPartialTime = now
                            val partialWav = pcmToWav(pcmStream.toByteArray())
                            val partialText = transcribe(partialWav, lang)
                            if (partialText != null) {
                                partialAccumulated.clear()
                                partialAccumulated.append(partialText)
                                withContext(Dispatchers.Main) {
                                    onPartialResult(partialText)
                                }
                            }
                        }
                    }
                }

                // 錄音結束 → 送完整音檔
                if (pcmStream.size() > 0) {
                    val finalWav = pcmToWav(pcmStream.toByteArray())
                    val finalText = transcribe(finalWav, lang)
                    withContext(Dispatchers.Main) {
                        onFinalResult(finalText ?: partialAccumulated.toString())
                    }
                } else {
                    withContext(Dispatchers.Main) { onFinalResult("") }
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) { onFinalResult(partialAccumulated.toString()) }
            } catch (e: Exception) {
                Log.e(tag, "Recording failed", e)
                withContext(Dispatchers.Main) { onError(ERROR_NETWORK) }
            } finally {
                releaseAudioRecord()
            }
        }
    }

    override fun stopListening() {
        isListening = false
    }

    override fun cancel() {
        isListening = false
        recordingJob?.cancel()
        recordingJob = null
        releaseAudioRecord()
    }

    override fun destroy() {
        cancel()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }

    // ─────────────── 內部方法 ───────────────

    @Synchronized
    private fun releaseAudioRecord() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null
    }

    private fun pcmToWav(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 1 * 16 / 8

        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeBytes("RIFF")
            writeIntLE(totalDataLen)
            writeBytes("WAVE")
            writeBytes("fmt ")
            writeIntLE(16)
            writeShortLE(1)        // PCM
            writeShortLE(1)        // mono
            writeIntLE(sampleRate)
            writeIntLE(byteRate)
            writeShortLE(2)        // block align
            writeShortLE(16)       // bits
            writeBytes("data")
            writeIntLE(pcmData.size)
            write(pcmData)
            flush()
        }
        return out.toByteArray()
    }

    private fun DataOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun transcribe(wavData: ByteArray, language: String?): String? {
        val baseUrl = serverUrl.trimEnd('/')

        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "audio.wav",
                wavData.toRequestBody("audio/wav".toMediaType())
            )

        bodyBuilder.addFormDataPart("temperature", "0")
        if (!language.isNullOrBlank()) {
            bodyBuilder.addFormDataPart("language", language)
            // 中文時加 prompt 引導輸出繁體
            if (language == "zh") {
                val contextHint = contextText?.takeIf { it.isNotBlank() }?.let { "前文：$it。" } ?: ""
                bodyBuilder.addFormDataPart("initial_prompt", "${contextHint}以下是繁體中文對話，請輸出繁體中文，不要簡體字。")
            }
        }
        if (apiKey.isNotBlank()) {
            bodyBuilder.addFormDataPart("api_key", apiKey)
        }

        val request = Request.Builder()
            .url("$baseUrl/v1/transcribe")
            .post(bodyBuilder.build())
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(tag, "API error ${response.code}: ${response.body?.string()?.take(200)}")
                    return null
                }
                val json = JSONObject(response.body?.string() ?: return null)
                json.optString("text", "").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(tag, "transcribe failed", e)
            null
        }
    }

    companion object {
        const val ERROR_NO_SERVER = 200
        const val ERROR_NO_PERMISSION = 201
        const val ERROR_AUDIO_INIT = 202
        const val ERROR_NETWORK = 203
        private const val DEFAULT_SILENCE_AMPLITUDE_THRESHOLD = 1200
        private const val SILENCE_THRESHOLD_MS = 2000L
    }
}

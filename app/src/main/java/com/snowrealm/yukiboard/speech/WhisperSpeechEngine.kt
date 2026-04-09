package com.snowrealm.yukiboard.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * OpenAI Whisper API 語音辨識引擎。
 *
 * 流程：AudioRecord 錄音 → WAV → POST /v1/audio/transcriptions → 回傳文字。
 * 支援 partial result（每 2 秒送一次中間片段）。
 */
class WhisperSpeechEngine(
    private val apiKey: String,
    private val model: String = "whisper-1"
) : SpeechInputEngine {

    @Volatile override var isListening: Boolean = false
        private set

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // 錄音參數
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // partial 間隔（毫秒）
    private val partialIntervalMs = 1500L
    // 靜音偵測：連續靜音超過此毫秒數自動停止
    private val silenceThresholdMs = 3000L
    private val silenceAmplitudeThreshold = 500  // PCM 振幅低於此視為靜音

    /** 外部可設定上下文文字，提升 Whisper 辨識準確度 */
    var contextText: String? = null

    override fun startListening(
        locale: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (Int) -> Unit
    ) {
        if (apiKey.isBlank()) {
            onError(ERROR_NO_API_KEY)
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(sampleRate * 2) // 至少 1 秒 buffer

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

        val whisperLocale = locale.substringBefore("-") // "zh-TW" → "zh"
        android.util.Log.i("WhisperEngine", "▶ startListening: apiKey=${apiKey.take(8)}..., locale=$whisperLocale")

        recordingJob = scope.launch {
            val pcmStream = ByteArrayOutputStream()
            val buffer = ShortArray(bufferSize / 2)
            var lastPartialTime = System.currentTimeMillis()
            val partialAccumulated = StringBuilder()
            var silenceStartTime = 0L

            try {
                while (isActive && isListening) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // 寫入 PCM bytes (little-endian)
                        for (i in 0 until read) {
                            pcmStream.write(buffer[i].toInt() and 0xFF)
                            pcmStream.write((buffer[i].toInt() shr 8) and 0xFF)
                        }

                        // 靜音偵測：計算振幅
                        val maxAmplitude = (0 until read).maxOfOrNull { kotlin.math.abs(buffer[it].toInt()) } ?: 0
                        val now = System.currentTimeMillis()
                        if (maxAmplitude < silenceAmplitudeThreshold) {
                            if (silenceStartTime == 0L) silenceStartTime = now
                            else if (now - silenceStartTime >= silenceThresholdMs && pcmStream.size() > sampleRate * 2) {
                                // 靜音超過閾值且有足夠音訊 → 自動停止
                                android.util.Log.i("WhisperEngine", "silence detected, auto-stopping")
                                isListening = false
                                break
                            }
                        } else {
                            silenceStartTime = 0L  // 有聲音，重置
                        }

                        // 定期送 partial
                        if (now - lastPartialTime >= partialIntervalMs && pcmStream.size() > sampleRate * 2) {
                            lastPartialTime = now
                            val partialWav = pcmToWav(pcmStream.toByteArray())
                            val partialText = transcribe(partialWav, whisperLocale)
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
                    val finalText = transcribe(finalWav, whisperLocale)
                    withContext(Dispatchers.Main) {
                        onFinalResult(finalText ?: partialAccumulated.toString())
                    }
                } else {
                    withContext(Dispatchers.Main) { onFinalResult("") }
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) { onFinalResult(partialAccumulated.toString()) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(ERROR_NETWORK) }
            } finally {
                releaseAudioRecord()
            }
        }
    }

    override fun stopListening() {
        isListening = false
        // recordingJob 的 while loop 會自然結束，送出 final result
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
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null
    }

    /** PCM 16-bit mono → WAV byte array */
    private fun pcmToWav(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 1 * 16 / 8 // mono, 16-bit

        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            // RIFF header
            writeBytes("RIFF")
            writeIntLE(totalDataLen)
            writeBytes("WAVE")
            // fmt chunk
            writeBytes("fmt ")
            writeIntLE(16)         // chunk size
            writeShortLE(1)        // PCM format
            writeShortLE(1)        // mono
            writeIntLE(sampleRate)
            writeIntLE(byteRate)
            writeShortLE(2)        // block align (mono 16-bit = 2)
            writeShortLE(16)       // bits per sample
            // data chunk
            writeBytes("data")
            writeIntLE(pcmData.size)
            write(pcmData)
            flush()
        }
        return out.toByteArray()
    }

    /** Little-endian int 寫入 */
    private fun DataOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    /** Little-endian short 寫入 */
    private fun DataOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    /** 呼叫 Whisper API 辨識（不指定語言，由 Whisper 自動偵測） */
    private fun transcribe(wavData: ByteArray, language: String): String? {
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "audio.wav",
                wavData.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
        // 中文時加 prompt 引導輸出繁體（含上下文提升準確度）
        if (language == "zh") {
            bodyBuilder.addFormDataPart("language", "zh")
            val contextHint = contextText?.takeIf { it.isNotBlank() }?.let { "前文：$it。" } ?: ""
            bodyBuilder.addFormDataPart("prompt", "${contextHint}以下是繁體中文語音內容。")
        }
        val body = bodyBuilder.build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("WhisperEngine", "API error ${response.code}: ${response.body?.string()?.take(200)}")
                    return null
                }
                val json = JSONObject(response.body?.string() ?: return null)
                json.optString("text", "").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            android.util.Log.w("WhisperEngine", "transcribe failed", e)
            null
        }
    }

    companion object {
        const val ERROR_NO_API_KEY = 100
        const val ERROR_NO_PERMISSION = 101
        const val ERROR_AUDIO_INIT = 102
        const val ERROR_NETWORK = 103
    }
}

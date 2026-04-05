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

    override var isListening: Boolean = false
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
    private val partialIntervalMs = 2500L

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

        recordingJob = scope.launch {
            val pcmStream = ByteArrayOutputStream()
            val buffer = ShortArray(bufferSize / 2)
            var lastPartialTime = System.currentTimeMillis()
            val partialAccumulated = StringBuilder()

            try {
                while (isActive && isListening) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // 寫入 PCM bytes (little-endian)
                        for (i in 0 until read) {
                            pcmStream.write(buffer[i].toInt() and 0xFF)
                            pcmStream.write((buffer[i].toInt() shr 8) and 0xFF)
                        }

                        // 定期送 partial
                        val now = System.currentTimeMillis()
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

    /** 呼叫 Whisper API 辨識 */
    private fun transcribe(wavData: ByteArray, language: String): String? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "audio.wav",
                wavData.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", model)
            .addFormDataPart("language", language)
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string() ?: return null)
                json.optString("text", "").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
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

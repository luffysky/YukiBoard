package com.snowrealm.yukiboard.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.*

class KeyboardSoundEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sampleRate = 44100
    var volumeScale = 0.45f
    var defaultStyle = ToneStyle.PIANO

    private fun hz(semitone: Int) = 440f * 2f.pow(semitone / 12f)

    private val C3 = hz(-21); private val G3 = hz(-14); private val A3 = hz(-12)
    private val B3 = hz(-10); private val GS3 = hz(-13)
    private val C4 = hz(-9);  private val D4 = hz(-7);  private val E4 = hz(-5)
    private val F4 = hz(-4);  private val G4 = hz(-2);  private val A4 = hz(0)
    private val B4 = hz(2);   private val C5 = hz(3);   private val D5 = hz(5)
    private val E5 = hz(7);   private val F5 = hz(8);   private val G5 = hz(10)
    private val A5 = hz(12);  private val B5 = hz(14);  private val C6 = hz(15)
    private val D6 = hz(17);  private val E6 = hz(19)

    private val keyNotes: Map<String, Float> by lazy { buildMap {
        put("1", C5); put("2", D5); put("3", E5); put("4", F5); put("5", G5)
        put("6", A5); put("7", B5); put("8", C6); put("9", D6); put("0", E6)
        put("q", C4); put("w", D4); put("e", E4); put("r", F4); put("t", G4)
        put("y", A4); put("u", B4); put("i", C5); put("o", D5); put("p", E4)
        put("a", A3); put("s", B3); put("d", C4); put("f", D4); put("g", E4)
        put("h", F4); put("j", G4); put("k", A4); put("l", B4)
        put("z", G3); put("x", A3); put("c", B3); put("v", C4)
        put("b", D4); put("n", E4); put("m", G3)
        // 注音
        put("ㄅ", C4); put("ㄆ", D4); put("ㄇ", E4); put("ㄈ", F4)
        put("ㄉ", G4); put("ㄊ", A4); put("ㄋ", B4); put("ㄌ", C5)
        put("ㄍ", D5); put("ㄎ", E5); put("ㄏ", F5)
        put("ㄐ", G5); put("ㄑ", A5); put("ㄒ", B5)
        put("ㄓ", C4); put("ㄔ", D4); put("ㄕ", E4); put("ㄖ", F4)
        put("ㄗ", G4); put("ㄘ", A4); put("ㄙ", B4)
        put("ㄧ", D5); put("ㄨ", E5); put("ㄩ", G5)
        put("ㄚ", A5); put("ㄛ", B5); put("ㄜ", C5); put("ㄝ", D5)
        put("ㄞ", E5); put("ㄟ", F5); put("ㄠ", G5); put("ㄡ", A5)
        put("ㄢ", B5); put("ㄣ", C6); put("ㄤ", D6); put("ㄥ", E6); put("ㄦ", G5)
        put("ˊ", E5); put("ˇ", C5); put("ˋ", A4); put("˙", G4)
        put(" ", C4); put("⌫", GS3); put("\n", G4)
    }}

    enum class ToneStyle { PIANO, MARIMBA, SOFT }

    fun playKeyTone(label: String, durationMs: Int = 85) {
        val freq = keyNotes[label] ?: keyNotes[label.lowercase()] ?: C4
        scope.launch { try { playTone(freq, durationMs, defaultStyle) } catch (_: Exception) {} }
    }

    private suspend fun playTone(freq: Float, durationMs: Int, style: ToneStyle) {
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val attackSamples = sampleRate * when (style) { ToneStyle.PIANO -> 5; ToneStyle.MARIMBA -> 3; ToneStyle.SOFT -> 20 } / 1000
        val decayRate = when (style) { ToneStyle.PIANO -> 6f; ToneStyle.MARIMBA -> 9f; ToneStyle.SOFT -> 1.5f }

        for (i in samples.indices) {
            val s = sin(2.0 * PI * freq * i / sampleRate).toFloat()
            val harmonic = when (style) {
                ToneStyle.PIANO -> s + sin(2.0 * PI * freq * 2 * i / sampleRate).toFloat() * 0.3f +
                    sin(2.0 * PI * freq * 3 * i / sampleRate).toFloat() * 0.1f
                ToneStyle.MARIMBA -> s + sin(2.0 * PI * freq * 2 * i / sampleRate).toFloat() * 0.5f
                ToneStyle.SOFT -> s
            }
            val env = if (i < attackSamples) i.toFloat() / attackSamples
            else exp(-decayRate * (i - attackSamples).toFloat() / (numSamples - attackSamples))
            samples[i] = (harmonic * env * volumeScale * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val bufSize = AudioTrack.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(numSamples * 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufSize).setTransferMode(AudioTrack.MODE_STATIC).build()
        track.write(samples, 0, samples.size)
        track.play()
        delay(durationMs.toLong() + 15)
        track.stop(); track.release()
    }

    fun destroy() { scope.cancel() }
}

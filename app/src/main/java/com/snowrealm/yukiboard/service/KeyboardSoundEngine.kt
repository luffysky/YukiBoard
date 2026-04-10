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

    // 完整色彩音階（含黑鍵半音）
    // 第 3 八度
    private val C3  = hz(-21); private val CS3 = hz(-20); private val D3  = hz(-19)
    private val DS3 = hz(-18); private val E3  = hz(-17); private val F3  = hz(-16)
    private val FS3 = hz(-15); private val G3  = hz(-14); private val GS3 = hz(-13)
    private val A3  = hz(-12); private val AS3 = hz(-11); private val B3  = hz(-10)
    // 第 4 八度
    private val C4  = hz(-9);  private val CS4 = hz(-8);  private val D4  = hz(-7)
    private val DS4 = hz(-6);  private val E4  = hz(-5);  private val F4  = hz(-4)
    private val FS4 = hz(-3);  private val G4  = hz(-2);  private val GS4 = hz(-1)
    private val A4  = hz(0);   private val AS4 = hz(1);   private val B4  = hz(2)
    // 第 5 八度
    private val C5  = hz(3);   private val CS5 = hz(4);   private val D5  = hz(5)
    private val DS5 = hz(6);   private val E5  = hz(7);   private val F5  = hz(8)
    private val FS5 = hz(9);   private val G5  = hz(10);  private val GS5 = hz(11)
    private val A5  = hz(12);  private val AS5 = hz(13);  private val B5  = hz(14)
    // 第 6 八度
    private val C6  = hz(15);  private val CS6 = hz(16);  private val D6  = hz(17)
    private val DS6 = hz(18);  private val E6  = hz(19)

    /**
     * 按鍵音符對映 — 模擬真實鋼琴鍵盤排列。
     *
     * 數字列：C5 C#5 D5 D#5 E5 F5 F#5 G5 G#5 A5（10 鍵 = 完整色彩音階）
     * Q 列：  C4 C#4 D4 D#4 E4 F4 F#4 G4 G#4 A4（10 鍵）
     * A 列：  A3 A#3 B3 C4  C#4 D4 D#4 E4 F4    （9 鍵）
     * Z 列：  F3 F#3 G3 G#3 A3 A#3 B3           （7 鍵）
     */
    private val keyNotes: Map<String, Float> by lazy { buildMap {
        // 數字列 — 第 5 八度色彩音階
        put("1", C5); put("2", CS5); put("3", D5); put("4", DS5); put("5", E5)
        put("6", F5); put("7", FS5); put("8", G5); put("9", GS5); put("0", A5)
        // Q 列 — 第 4 八度色彩音階
        put("q", C4); put("w", CS4); put("e", D4); put("r", DS4); put("t", E4)
        put("y", F4); put("u", FS4); put("i", G4); put("o", GS4); put("p", A4)
        // A 列 — A3 → F4
        put("a", A3); put("s", AS3); put("d", B3); put("f", C4); put("g", CS4)
        put("h", D4); put("j", DS4); put("k", E4); put("l", F4)
        // Z 列 — F3 → B3
        put("z", F3); put("x", FS3); put("c", G3); put("v", GS3)
        put("b", A3); put("n", AS3); put("m", B3)
        // 注音聲母 — 第 4 八度色彩音階（ㄅ~ㄙ 共 21 鍵）
        put("ㄅ", C4); put("ㄆ", CS4); put("ㄇ", D4); put("ㄈ", DS4)
        put("ㄉ", E4); put("ㄊ", F4);  put("ㄋ", FS4); put("ㄌ", G4)
        put("ㄍ", GS4); put("ㄎ", A4); put("ㄏ", AS4)
        put("ㄐ", B4);  put("ㄑ", C5); put("ㄒ", CS5)
        put("ㄓ", D5);  put("ㄔ", DS5); put("ㄕ", E5); put("ㄖ", F5)
        put("ㄗ", FS5); put("ㄘ", G5);  put("ㄙ", GS5)
        // 注音韻母 — 第 5 八度
        put("ㄧ", A5);  put("ㄨ", AS5); put("ㄩ", B5)
        put("ㄚ", C5);  put("ㄛ", CS5); put("ㄜ", D5); put("ㄝ", DS5)
        put("ㄞ", E5);  put("ㄟ", F5);  put("ㄠ", FS5); put("ㄡ", G5)
        put("ㄢ", GS5); put("ㄣ", A5);  put("ㄤ", AS5); put("ㄥ", B5); put("ㄦ", C6)
        // 聲調
        put("ˊ", CS5); put("ˇ", DS5); put("ˋ", FS4); put("˙", GS4)
        // 功能鍵
        put(" ", C4); put("⌫", DS3); put("\n", E3)
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

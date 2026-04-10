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
     * 按鍵音符對映 — 完整色彩音階，不重疊。
     *
     * Z 列（低音）：F3  F#3 G3  G#3 A3  A#3 B3              — 7 鍵
     * A 列（中低）：C4  C#4 D4  D#4 E4  F4  F#4 G4  G#4     — 9 鍵
     * Q 列（中高）：A4  A#4 B4  C5  C#5 D5  D#5 E5  F5  F#5 — 10 鍵
     * 數字列（高音）：G5 G#5 A5  A#5 B5  C6  C#6 D6  D#6 E6 — 10 鍵
     *
     * 全 36 鍵涵蓋 F3→E6 = 3 個完整八度，無重複。
     */
    private val keyNotes: Map<String, Float> by lazy { buildMap {
        // 數字列 — 高音區 G5→E6
        put("1", G5); put("2", GS5); put("3", A5); put("4", AS5); put("5", B5)
        put("6", C6); put("7", CS6); put("8", D6); put("9", DS6); put("0", E6)
        // Q 列 — 中高音 A4→F#5
        put("q", A4); put("w", AS4); put("e", B4); put("r", C5); put("t", CS5)
        put("y", D5); put("u", DS5); put("i", E5); put("o", F5); put("p", FS5)
        // A 列 — 中低音 C4→G#4
        put("a", C4); put("s", CS4); put("d", D4); put("f", DS4); put("g", E4)
        put("h", F4); put("j", FS4); put("k", G4); put("l", GS4)
        // Z 列 — 低音 F3→B3
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

    enum class ToneStyle { PIANO, MARIMBA, SOFT, GUITAR, MUSIC_BOX, HARP, ORGAN, DRUM, EDRUM, BEATBOX }

    /** 鼓類音色 = 噪音+掃頻合成，不使用 keyNotes 音高 */
    private val isPercussion get() = defaultStyle in setOf(ToneStyle.DRUM, ToneStyle.EDRUM, ToneStyle.BEATBOX)

    fun playKeyTone(label: String, durationMs: Int = 85) {
        if (isPercussion) {
            scope.launch { try { playPercussion(label, durationMs, defaultStyle) } catch (_: Exception) {} }
            return
        }
        val freq = keyNotes[label] ?: keyNotes[label.lowercase()] ?: C4
        scope.launch { try { playTone(freq, durationMs, defaultStyle) } catch (_: Exception) {} }
    }

    private suspend fun playTone(freq: Float, durationMs: Int, style: ToneStyle) {
        val dur = when (style) {
            ToneStyle.MUSIC_BOX -> durationMs + 60  // 音樂盒餘韻長
            ToneStyle.HARP -> durationMs + 40
            else -> durationMs
        }
        val numSamples = sampleRate * dur / 1000
        val samples = ShortArray(numSamples)
        val attackSamples = sampleRate * when (style) {
            ToneStyle.PIANO -> 5; ToneStyle.MARIMBA -> 3; ToneStyle.SOFT -> 20
            ToneStyle.GUITAR -> 2; ToneStyle.MUSIC_BOX -> 1; ToneStyle.HARP -> 8; ToneStyle.ORGAN -> 15
            else -> 5
        } / 1000
        val decayRate = when (style) {
            ToneStyle.PIANO -> 6f; ToneStyle.MARIMBA -> 9f; ToneStyle.SOFT -> 1.5f
            ToneStyle.GUITAR -> 7f; ToneStyle.MUSIC_BOX -> 3f; ToneStyle.HARP -> 4f; ToneStyle.ORGAN -> 0.8f
            else -> 6f
        }

        for (i in samples.indices) {
            val t = i.toDouble() / sampleRate
            val s = sin(2.0 * PI * freq * i / sampleRate).toFloat()
            val harmonic = when (style) {
                ToneStyle.PIANO -> s + sin(2.0 * PI * freq * 2 * i / sampleRate).toFloat() * 0.3f +
                    sin(2.0 * PI * freq * 3 * i / sampleRate).toFloat() * 0.1f
                ToneStyle.MARIMBA -> s + sin(2.0 * PI * freq * 2 * i / sampleRate).toFloat() * 0.5f
                ToneStyle.SOFT -> s
                // 吉他：豐富泛音 + 輕微失諧模擬弦振動
                ToneStyle.GUITAR -> s +
                    sin(2.0 * PI * freq * 2 * i / sampleRate).toFloat() * 0.4f +
                    sin(2.0 * PI * freq * 3 * i / sampleRate).toFloat() * 0.2f +
                    sin(2.0 * PI * freq * 4 * i / sampleRate).toFloat() * 0.08f +
                    sin(2.0 * PI * freq * 1.001 * i / sampleRate).toFloat() * 0.05f  // 微失諧
                // 音樂盒：高頻泛音突出 + 金屬感
                ToneStyle.MUSIC_BOX ->
                    sin(2.0 * PI * freq * 2 * i / sampleRate).toFloat() * 0.6f +  // 二倍頻為主
                    s * 0.3f +
                    sin(2.0 * PI * freq * 3 * i / sampleRate).toFloat() * 0.25f +
                    sin(2.0 * PI * freq * 5 * i / sampleRate).toFloat() * 0.1f     // 高泛音亮度
                // 豎琴：柔和泛音 + 緩起音
                ToneStyle.HARP -> s +
                    sin(2.0 * PI * freq * 2 * i / sampleRate).toFloat() * 0.25f +
                    sin(2.0 * PI * freq * 3 * i / sampleRate).toFloat() * 0.08f
                // 風琴：奇次泛音為主（管風琴特徵）
                ToneStyle.ORGAN -> s +
                    sin(2.0 * PI * freq * 3 * i / sampleRate).toFloat() * 0.3f +
                    sin(2.0 * PI * freq * 5 * i / sampleRate).toFloat() * 0.15f +
                    sin(2.0 * PI * freq * 7 * i / sampleRate).toFloat() * 0.05f
                else -> s  // 打擊樂不走此路徑
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
        delay(dur.toLong() + 15)
        track.stop(); track.release()
    }

    /**
     * 打擊樂合成 — 用噪音 + 頻率掃描模擬鼓聲。
     *
     * 按鍵行列決定鼓件：
     * 數字列 = Hi-Hat, Q列 = Snare, A列 = Tom, Z列 = Kick
     * 空白 = Kick, 退格 = Rim Shot, 換行 = Crash
     */
    private suspend fun playPercussion(label: String, durationMs: Int, style: ToneStyle) {
        val key = label.lowercase()
        val drumType = when {
            key in "1234567890" -> DrumType.HIHAT
            key in "qwertyuiop" || "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏ".contains(label) -> DrumType.SNARE
            key in "asdfghjkl" || "ㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙ".contains(label) -> DrumType.TOM
            key in "zxcvbnm" || "ㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ".contains(label) -> DrumType.KICK
            label == " " -> DrumType.KICK
            label == "⌫" -> DrumType.RIM
            label == "\n" -> DrumType.CRASH
            else -> DrumType.SNARE
        }
        val dur = when (drumType) {
            DrumType.KICK -> 100; DrumType.SNARE -> 80; DrumType.HIHAT -> 50
            DrumType.TOM -> 90; DrumType.RIM -> 40; DrumType.CRASH -> 140
        }
        val numSamples = sampleRate * dur / 1000
        val samples = ShortArray(numSamples)
        val random = java.util.Random()

        for (i in samples.indices) {
            val t = i.toFloat() / numSamples
            val env = exp(-t * when (drumType) {
                DrumType.KICK -> 4f; DrumType.SNARE -> 8f; DrumType.HIHAT -> 15f
                DrumType.TOM -> 5f; DrumType.RIM -> 20f; DrumType.CRASH -> 3f
            })
            val noise = (random.nextFloat() * 2f - 1f)
            val tonal = when (drumType) {
                // Kick: 低頻掃描 150→50 Hz
                DrumType.KICK -> sin(2.0 * PI * (150 - 100 * t) * i / sampleRate).toFloat()
                // Tom: 中頻掃描 200→80 Hz
                DrumType.TOM -> sin(2.0 * PI * (200 - 120 * t) * i / sampleRate).toFloat()
                // Rim: 高頻短促
                DrumType.RIM -> sin(2.0 * PI * 800.0 * i / sampleRate).toFloat()
                else -> 0f
            }
            val mix = when (drumType) {
                DrumType.KICK -> tonal * 0.7f + noise * 0.3f
                DrumType.SNARE -> tonal * 0.1f + noise * 0.9f
                DrumType.HIHAT -> noise  // 純噪音
                DrumType.TOM -> tonal * 0.6f + noise * 0.4f
                DrumType.RIM -> tonal * 0.8f + noise * 0.2f
                DrumType.CRASH -> noise * 0.7f + sin(2.0 * PI * 350.0 * i / sampleRate).toFloat() * 0.3f
            }
            // 風格調整
            val styled = when (style) {
                ToneStyle.DRUM -> mix * env
                ToneStyle.EDRUM -> {
                    // 電子鼓：更乾淨的正弦波 + 壓縮噪音
                    val cleanTonal = when (drumType) {
                        DrumType.KICK -> sin(2.0 * PI * (120 - 80 * t) * i / sampleRate).toFloat()
                        DrumType.SNARE -> sin(2.0 * PI * 200.0 * i / sampleRate).toFloat() * 0.4f + noise * 0.6f
                        DrumType.HIHAT -> noise * (if (t < 0.3f) 1f else 0.3f) // 門控
                        DrumType.TOM -> sin(2.0 * PI * (180 - 100 * t) * i / sampleRate).toFloat()
                        DrumType.RIM -> sin(2.0 * PI * 1000.0 * i / sampleRate).toFloat()
                        DrumType.CRASH -> noise * 0.5f + sin(2.0 * PI * 400.0 * i / sampleRate).toFloat() * 0.5f
                    }
                    cleanTonal * env * 1.2f  // 稍微 boost
                }
                ToneStyle.BEATBOX -> {
                    // Beatbox：口技風，更多中高頻 + 嘴巴共振模擬
                    val formant1 = sin(2.0 * PI * 300.0 * i / sampleRate).toFloat()
                    val formant2 = sin(2.0 * PI * 700.0 * i / sampleRate).toFloat()
                    val breathNoise = noise * exp(-t * 12f)
                    when (drumType) {
                        DrumType.KICK -> (sin(2.0 * PI * (100 - 60 * t) * i / sampleRate).toFloat() * 0.6f + breathNoise * 0.4f) * env
                        DrumType.SNARE -> (breathNoise * 0.7f + formant1 * 0.2f + formant2 * 0.1f) * env
                        DrumType.HIHAT -> (noise * (if (t < 0.15f) 1f else 0.15f) + formant2 * 0.1f) * env  // ts! 聲
                        DrumType.TOM -> (formant1 * 0.5f + sin(2.0 * PI * (180 - 80 * t) * i / sampleRate).toFloat() * 0.3f + breathNoise * 0.2f) * env
                        DrumType.RIM -> (formant2 * 0.6f + noise * 0.4f) * exp(-t * 25f)
                        DrumType.CRASH -> (noise * 0.4f + formant1 * 0.3f + formant2 * 0.3f) * env
                    }
                }
                else -> mix * env  // fallback
            }
            samples[i] = (styled * volumeScale * Short.MAX_VALUE)
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
        delay(dur.toLong() + 15)
        track.stop(); track.release()
    }

    private enum class DrumType { KICK, SNARE, HIHAT, TOM, RIM, CRASH }

    fun destroy() { scope.cancel() }
}

package com.snowrealm.yukiboard.input.engine

import android.content.Context
import com.snowrealm.yukiboard.input.router.InputResult

/**
 * 注音輸入引擎（libchewing JNI 橋接預留）。
 *
 * ## 整合方式（待實作）
 * 1. 在 `app/src/main/cpp/` 加入 chewing JNI wrapper（`chewing_jni.cpp`）
 * 2. `CMakeLists.txt` 連結 `libchewing.a`
 * 3. 在此類的 `companion object` 載入 native library：
 *    ```kotlin
 *    System.loadLibrary("chewing_jni")
 *    ```
 * 4. 將 `processChar` 等方法改為呼叫 native 函式
 *
 * ## 目前狀態
 * 使用純 Kotlin 的簡易拼音緩衝模擬，確保整合前 App 可正常編譯執行。
 * 注音符號會直接放入 composing buffer，空白鍵「假設」直接 commit。
 */
class ChewingInputEngine(private val context: Context) : InputEngine {

    private val bopomofoBuffer = StringBuilder()
    private val committedBuffer = StringBuilder()

    // 預留：JNI handle（整合 libchewing 後使用）
    // private var chewingCtx: Long = 0L

    override val composingText: String
        get() = buildString {
            append(committedBuffer)
            append(bopomofoBuffer)
        }

    override fun processChar(char: String): InputResult {
        if (char.isEmpty()) return InputResult.Idle

        val ch = char[0]
        return when {
            // 注音符號：加入拼音緩衝
            ch in '\u3105'..'\u312F' || ch in '\u02CA'..'\u02CB' || ch == '\u02C7' || ch == '\u02D9' -> {
                bopomofoBuffer.append(ch)
                InputResult.ComposingUpdated(composingText)
            }
            // 數字聲調 1-5
            ch in '1'..'5' -> {
                bopomofoBuffer.append(ch)
                // TODO: 呼叫 chewing_handle_Default，取得候選詞
                InputResult.ComposingUpdated(composingText)
            }
            else -> InputResult.Idle
        }
    }

    override fun processDelete(): InputResult {
        return when {
            bopomofoBuffer.isNotEmpty() -> {
                bopomofoBuffer.deleteCharAt(bopomofoBuffer.lastIndex)
                if (composingText.isEmpty()) InputResult.ComposingCleared
                else InputResult.ComposingUpdated(composingText)
            }
            committedBuffer.isNotEmpty() -> {
                committedBuffer.deleteCharAt(committedBuffer.lastIndex)
                if (composingText.isEmpty()) InputResult.ComposingCleared
                else InputResult.ComposingUpdated(composingText)
            }
            else -> InputResult.ComposingCleared
        }
    }

    override fun processSpace(): InputResult {
        if (composingText.isEmpty()) return InputResult.CommitText(" ")
        // TODO: 呼叫 chewing_handle_Space，選取第一個候選詞
        // 目前：直接提交 composing 原文（Unicode 注音）
        val text = composingText
        reset()
        return InputResult.CommitText(text)
    }

    override fun processEnter(): InputResult {
        if (composingText.isEmpty()) return InputResult.Idle
        val text = composingText
        reset()
        return InputResult.CommitText(text)
    }

    override fun forceCommit(): InputResult {
        if (composingText.isEmpty()) return InputResult.Idle
        val text = composingText
        reset()
        return InputResult.CommitText(text)
    }

    override fun getCandidates(): List<String> {
        // TODO: 呼叫 chewing_cand_* 系列函式取得候選詞
        if (composingText.isEmpty()) return emptyList()
        // 模擬回傳：直接把 composing 作為單一候選
        return listOf(composingText)
    }

    override fun reset() {
        bopomofoBuffer.clear()
        committedBuffer.clear()
    }

    // -------------------------------------------------------------------------
    // JNI 宣告（整合 libchewing 後解除注釋）
    // -------------------------------------------------------------------------

    // private external fun nativeInit(dataPath: String): Long
    // private external fun nativeHandleChar(ctx: Long, char: Int): Int
    // private external fun nativeHandleSpace(ctx: Long): Int
    // private external fun nativeHandleBackspace(ctx: Long): Int
    // private external fun nativeHandleEnter(ctx: Long): Int
    // private external fun nativeGetCommitString(ctx: Long): String
    // private external fun nativeGetPreEditString(ctx: Long): String
    // private external fun nativeCandStringByIndex(ctx: Long, index: Int): String
    // private external fun nativeCandTotalChoice(ctx: Long): Int
    // private external fun nativeDelete(ctx: Long)

    companion object {
        // 整合 libchewing 後載入：
        // init { System.loadLibrary("chewing_jni") }
    }
}

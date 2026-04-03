package com.snowrealm.yukiboard.input.engine

import android.content.Context
import com.snowrealm.yukiboard.R
import com.snowrealm.yukiboard.input.router.InputResult
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 注音輸入引擎 — 內建注音→漢字字典版。
 *
 * 從 `res/raw/bopomofo_dict.txt` 載入注音→漢字對照表，
 * 使用者輸入注音符號後按空白鍵即可取得候選字。
 *
 * 字典格式（每行）：`ㄅㄚ\t八 吧 巴 爸 …`
 * 含聲調版與無聲調版：無聲調版回傳所有聲調的聯集。
 */
class ChewingInputEngine(private val context: Context) : InputEngine {

    // ─── 注音組字緩衝 ───
    private val bopomofoBuffer = StringBuilder()
    // 已選字但尚未 commit 的暫存（連續選字場景）
    private val selectedBuffer = StringBuilder()

    // ─── 字典：注音拼音 → 漢字列表 ───
    private val dict: Map<String, List<String>> by lazy { loadDict() }

    // ─── 當前候選詞 ───
    private var currentCandidates: List<String> = emptyList()
    // 是否正在候選詞選擇狀態（按空白鍵後、選字前）
    private var isSelectingCandidate = false

    // ─── 聲調符號 ───
    private val toneMarks = setOf(
        '\u02C9', // ˉ 一聲（macron, 有時省略）
        '\u02CA', // ˊ 二聲
        '\u02C7', // ˇ 三聲
        '\u02CB', // ˋ 四聲
        '\u02D9', // ˙ 輕聲
    )

    override val composingText: String
        get() = buildString {
            append(selectedBuffer)
            append(bopomofoBuffer)
        }

    override fun processChar(char: String): InputResult {
        if (char.isEmpty()) return InputResult.Idle

        val ch = char[0]
        return when {
            // 注音符號
            ch in '\u3105'..'\u312F' -> {
                // 如果在候選選擇狀態，先確認第一個候選
                if (isSelectingCandidate) {
                    confirmFirstCandidate()
                }
                bopomofoBuffer.append(ch)
                updateCandidatesFromBuffer()
                InputResult.ComposingUpdated(composingText)
            }
            // 聲調符號 → 加入 buffer 並查字典
            ch in toneMarks -> {
                if (bopomofoBuffer.isEmpty()) return InputResult.Idle
                if (isSelectingCandidate) {
                    confirmFirstCandidate()
                }
                bopomofoBuffer.append(ch)
                updateCandidatesFromBuffer()
                InputResult.ComposingUpdated(composingText)
            }
            // 數字 2-5 作為聲調快捷（2=ˊ 3=ˇ 4=ˋ 5=˙，1=一聲/省略）
            ch in '1'..'5' && bopomofoBuffer.isNotEmpty() -> {
                if (isSelectingCandidate) {
                    // 數字也可作為候選序號（1-9 選字）
                    val idx = ch - '1'
                    if (idx < currentCandidates.size) {
                        return selectCandidateAt(idx)
                    }
                }
                val tone = when (ch) {
                    '1' -> null        // 一聲：不加標記，直接查無調版
                    '2' -> '\u02CA'
                    '3' -> '\u02C7'
                    '4' -> '\u02CB'
                    '5' -> '\u02D9'
                    else -> null
                }
                if (tone != null) bopomofoBuffer.append(tone)
                updateCandidatesFromBuffer()
                InputResult.ComposingUpdated(composingText)
            }
            else -> InputResult.Idle
        }
    }

    override fun processDelete(): InputResult {
        return when {
            isSelectingCandidate -> {
                // 取消候選選擇，回到注音緩衝
                isSelectingCandidate = false
                updateCandidatesFromBuffer()
                if (composingText.isEmpty()) InputResult.ComposingCleared
                else InputResult.ComposingUpdated(composingText)
            }
            bopomofoBuffer.isNotEmpty() -> {
                bopomofoBuffer.deleteCharAt(bopomofoBuffer.lastIndex)
                updateCandidatesFromBuffer()
                if (composingText.isEmpty()) InputResult.ComposingCleared
                else InputResult.ComposingUpdated(composingText)
            }
            selectedBuffer.isNotEmpty() -> {
                selectedBuffer.deleteCharAt(selectedBuffer.lastIndex)
                if (composingText.isEmpty()) InputResult.ComposingCleared
                else InputResult.ComposingUpdated(composingText)
            }
            else -> InputResult.ComposingCleared
        }
    }

    override fun processSpace(): InputResult {
        if (bopomofoBuffer.isEmpty() && selectedBuffer.isEmpty()) {
            return InputResult.CommitText(" ")
        }

        if (isSelectingCandidate && currentCandidates.isNotEmpty()) {
            // 空白鍵 = 確認第一候選
            return selectCandidateAt(0)
        }

        // 查字典
        val lookup = lookupCandidates(bopomofoBuffer.toString())
        if (lookup.isNotEmpty()) {
            currentCandidates = lookup
            isSelectingCandidate = true
            // 不清 bopomofoBuffer，讓使用者看到注音 + 候選
            return InputResult.ComposingUpdated(composingText)
        }

        // 查無候選 → 直接 commit 原注音文字
        val text = composingText
        reset()
        return InputResult.CommitText(text)
    }

    override fun processEnter(): InputResult {
        if (composingText.isEmpty()) return InputResult.Idle
        // 如果有候選，選第一個；否則 commit 原文
        if (isSelectingCandidate && currentCandidates.isNotEmpty()) {
            confirmFirstCandidate()
        }
        val text = composingText
        reset()
        return InputResult.CommitText(text)
    }

    override fun forceCommit(): InputResult {
        if (composingText.isEmpty()) return InputResult.Idle
        if (isSelectingCandidate && currentCandidates.isNotEmpty()) {
            confirmFirstCandidate()
        }
        val text = composingText
        reset()
        return InputResult.CommitText(text)
    }

    override fun getCandidates(): List<String> = currentCandidates

    override fun reset() {
        bopomofoBuffer.clear()
        selectedBuffer.clear()
        currentCandidates = emptyList()
        isSelectingCandidate = false
    }

    /**
     * 外部呼叫：使用者從候選列點選第 [index] 個候選字。
     */
    fun selectCandidateAt(index: Int): InputResult {
        if (index !in currentCandidates.indices) return InputResult.Idle

        val selected = currentCandidates[index]
        selectedBuffer.append(selected)
        bopomofoBuffer.clear()
        currentCandidates = emptyList()
        isSelectingCandidate = false

        // 直接 commit 已選字
        val text = selectedBuffer.toString()
        selectedBuffer.clear()
        return InputResult.CommitText(text)
    }

    // ─────────────────────────────────────────────────────────────────
    // 字典查詢
    // ─────────────────────────────────────────────────────────────────

    private fun lookupCandidates(bopomofo: String): List<String> {
        if (bopomofo.isEmpty()) return emptyList()

        // 先查精確（含聲調）
        val exact = dict[bopomofo]
        if (!exact.isNullOrEmpty()) return exact

        // 去掉尾部聲調查無調版（聯集所有聲調）
        val stripped = bopomofo.trimEnd { it in toneMarks }
        if (stripped != bopomofo) {
            val base = dict[stripped]
            if (!base.isNullOrEmpty()) return base
        }

        // 反過來：如果輸入沒聲調，查無調版
        val base = dict[bopomofo]
        return base ?: emptyList()
    }

    private fun updateCandidatesFromBuffer() {
        currentCandidates = if (bopomofoBuffer.isNotEmpty()) {
            lookupCandidates(bopomofoBuffer.toString())
        } else {
            emptyList()
        }
        isSelectingCandidate = false
    }

    private fun confirmFirstCandidate() {
        if (currentCandidates.isNotEmpty()) {
            selectedBuffer.append(currentCandidates[0])
            bopomofoBuffer.clear()
            currentCandidates = emptyList()
            isSelectingCandidate = false
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 字典載入
    // ─────────────────────────────────────────────────────────────────

    private fun loadDict(): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        try {
            val input = context.resources.openRawResource(R.raw.bopomofo_dict)
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.forEachLine { line ->
                    val parts = line.split('\t')
                    if (parts.size >= 2) {
                        val key = parts[0].trim()
                        val chars = parts[1].trim().split(' ').filter { it.isNotBlank() }
                        if (key.isNotEmpty() && chars.isNotEmpty()) {
                            map.getOrPut(key) { mutableListOf() }.addAll(chars)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 字典載入失敗，降級為空字典
            e.printStackTrace()
        }
        // 去重
        return map.mapValues { (_, v) -> v.distinct() }
    }
}

package com.snowrealm.yukiboard.input.engine

import com.snowrealm.yukiboard.input.router.InputResult

/**
 * 輸入引擎介面。
 *
 * 每個引擎維護自己的組字緩衝（composing buffer），
 * 向 [InputRouter] 回報處理結果，由 Router 決定是否提交或更新 composing。
 */
interface InputEngine {

    /** 當前組字緩衝（顯示在 composing bar） */
    val composingText: String

    /**
     * 處理單一字元輸入。
     * @param char 使用者輸入的字元（可能是字母、注音符號等）
     * @return 操作結果
     */
    fun processChar(char: String): InputResult

    /** 處理退格鍵 */
    fun processDelete(): InputResult

    /** 處理空白鍵（通常確認選詞） */
    fun processSpace(): InputResult

    /** 處理 Enter 鍵（強制提交當前 composing 原文） */
    fun processEnter(): InputResult

    /** 強制提交 composing，不經候選詞流程 */
    fun forceCommit(): InputResult

    /** 取得當前候選詞列表 */
    fun getCandidates(): List<String>

    /** 重置引擎狀態 */
    fun reset()
}

package com.snowrealm.yukiboard.yuki

import com.snowrealm.yukiboard.emotion.EmotionState

/**
 * 情緒冷靜期管理 — 監聽 [EmotionState]，偵測激烈用語時攔截發送。
 *
 * 流程：
 * 1. [checkText] 偵測觸發詞
 * 2. 回傳 [CooldownAction] 告知呼叫端要做什麼
 * 3. 呼叫端執行：結冰動畫 → 鍵盤鎖定 1 秒 → 確認對話框
 *
 * 不直接操作 UI（遵守分層規範）。
 */
class EmotionalCooldownManager {

    /** 冷靜期結果 */
    sealed class CooldownAction {
        object Proceed : CooldownAction()
        data class Intercept(val triggerWord: String) : CooldownAction()
    }

    private val COOLDOWN_MS = 60 * 1000L // 觸發後 1 分鐘內不重複
    private var lastTriggerTime = 0L

    /** 檢查文字是否需要攔截 */
    fun checkText(text: String): CooldownAction {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < COOLDOWN_MS) return CooldownAction.Proceed

        val found = findTriggerWord(text)
        if (found != null) {
            lastTriggerTime = now
            EmotionState.update(EmotionState.Tone.ANGRY, 0.9f, text)
            return CooldownAction.Intercept(found)
        }
        return CooldownAction.Proceed
    }

    /** 重置冷卻計時器 */
    fun reset() {
        lastTriggerTime = 0L
    }

    private fun findTriggerWord(text: String): String? {
        val lower = text.lowercase()
        for (word in TRIGGER_WORDS_ZH) {
            if (lower.contains(word)) return word
        }
        for (word in TRIGGER_WORDS_EN) {
            if (lower.contains(word)) return word
        }
        return null
    }

    companion object {
        private val TRIGGER_WORDS_ZH = listOf(
            "去死", "死全家", "垃圾", "廢物", "白痴", "智障",
            "幹你", "操你", "他媽的", "狗娘", "賤人", "婊子",
            "殺了你", "弄死你", "你完了", "滾開", "閉嘴",
            "畜生", "人渣", "噁心"
        )

        private val TRIGGER_WORDS_EN = listOf(
            "fuck you", "kill you", "die", "stfu",
            "piece of shit", "go to hell", "kys"
        )
    }
}

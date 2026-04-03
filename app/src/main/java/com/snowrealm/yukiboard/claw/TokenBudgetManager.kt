package com.snowrealm.yukiboard.claw

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/**
 * Token 用量統計（analytics only）。
 *
 * 這不是額度管控 — 真正的額度由 [com.snowrealm.yukiboard.auth.InviteCodeManager]
 * 或 Z幣系統控制。此類僅記錄「每個 Skill 用了多少」供 UI 顯示和週報使用。
 */
class TokenBudgetManager(context: Context) {
    private val prefs = context.getSharedPreferences("yukiboard_token_budget", Context.MODE_PRIVATE)

    /** 記錄用量（僅統計，不扣額度） */
    fun recordUsage(count: Int, skillId: String) {
        resetDailyIfNeeded()
        val totalKey = "daily_total"
        val skillKey = "daily_skill_$skillId"
        prefs.edit()
            .putInt(totalKey, prefs.getInt(totalKey, 0) + count)
            .putInt(skillKey, prefs.getInt(skillKey, 0) + count)
            .apply()
    }

    fun getDailyUsed(): Int {
        resetDailyIfNeeded()
        return prefs.getInt("daily_total", 0)
    }

    fun getUsageBySkill(): Map<String, Int> {
        resetDailyIfNeeded()
        return prefs.all.entries
            .filter { it.key.startsWith("daily_skill_") }
            .associate { it.key.removePrefix("daily_skill_") to (it.value as? Int ?: 0) }
    }

    private fun resetDailyIfNeeded() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val storedDate = prefs.getString("quota_date", "") ?: ""
        if (storedDate != today) {
            prefs.edit().clear().putString("quota_date", today).apply()
        }
    }
}

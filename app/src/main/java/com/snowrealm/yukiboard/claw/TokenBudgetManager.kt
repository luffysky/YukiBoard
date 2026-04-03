package com.snowrealm.yukiboard.claw

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

class TokenBudgetManager(context: Context) {
    private val prefs = context.getSharedPreferences("yukiboard_token_budget", Context.MODE_PRIVATE)

    fun consumeTokens(count: Int, skillId: String) {
        resetDailyQuotaIfNeeded()
        val totalKey = "daily_total"
        val skillKey = "daily_skill_$skillId"
        prefs.edit()
            .putInt(totalKey, prefs.getInt(totalKey, 0) + count)
            .putInt(skillKey, prefs.getInt(skillKey, 0) + count)
            .apply()
    }

    fun getDailyUsed(): Int {
        resetDailyQuotaIfNeeded()
        return prefs.getInt("daily_total", 0)
    }

    fun getUsageBySkill(): Map<String, Int> {
        resetDailyQuotaIfNeeded()
        return prefs.all.entries
            .filter { it.key.startsWith("daily_skill_") }
            .associate { it.key.removePrefix("daily_skill_") to (it.value as? Int ?: 0) }
    }

    fun resetDailyQuotaIfNeeded() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val storedDate = prefs.getString("quota_date", "") ?: ""
        if (storedDate != today) {
            prefs.edit().clear().putString("quota_date", today).apply()
        }
    }
}

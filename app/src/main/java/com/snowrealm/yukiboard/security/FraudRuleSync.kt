package com.snowrealm.yukiboard.security

import android.content.Context
import android.content.SharedPreferences
import com.snowrealm.yukiboard.data.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * 雲端防詐規則同步。
 *
 * 定期從 Supabase fraud_rules_v2 表拉取最新規則，
 * 與本地回饋權重合併，產生最終規則集。
 *
 * 同步條件：距離上次同步 > 24 小時。
 * 同步內容：
 *   - 新增的關鍵字及權重
 *   - 社群回饋統計調整的權重
 *   - 新的組合模式
 *
 * 合併策略：remote rule weight + local feedback adjustment = final weight
 */
class FraudRuleSync(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fraud_rule_sync", Context.MODE_PRIVATE)
    private val supabase = SupabaseClient.getInstance()

    companion object {
        private const val KEY_LAST_SYNC = "last_sync_time"
        private const val KEY_REMOTE_RULES = "remote_rules_json"
        private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L  // 24 hours
    }

    /**
     * 遠端規則資料模型。
     */
    data class RemoteRule(
        val keyword: String,
        val fraudType: String,
        val weight: Int,
        val isCombo: Boolean = false,
        val comboPartner: String? = null,
        val version: Int = 1
    )

    /**
     * 檢查是否需要同步（距上次 > 24h）。
     */
    fun needsSync(): Boolean {
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0)
        return System.currentTimeMillis() - lastSync > SYNC_INTERVAL_MS
    }

    /**
     * 執行同步。從 Supabase fraud_rules_v2 拉取最新規則。
     * @return true = 同步成功
     */
    suspend fun sync(): Boolean {
        if (!supabase.isConfigured) return false

        return withContext(Dispatchers.IO) {
            try {
                val rows = supabase.select(
                    table = "fraud_rules_v2",
                    query = "select=keyword,fraud_type,weight,is_combo,combo_partner,version&order=version.desc"
                ) ?: return@withContext false

                if (rows.isEmpty()) return@withContext false

                // 儲存原始 JSON
                prefs.edit()
                    .putString(KEY_REMOTE_RULES, rows.toString())
                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    .apply()

                // 將遠端規則寫入 weight manager 的 sync 調整
                applyRemoteRules(rows)

                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * 將遠端規則應用到 FraudRuleWeightManager 的同步調整欄位。
     */
    private fun applyRemoteRules(rows: JsonArray) {
        val weightPrefs = context.getSharedPreferences("fraud_weights", Context.MODE_PRIVATE)
        val editor = weightPrefs.edit()

        for (element in rows) {
            val obj = element.jsonObject
            val keyword = obj["keyword"]?.jsonPrimitive?.contentOrNull ?: continue
            val weight = obj["weight"]?.jsonPrimitive?.intOrNull ?: continue

            // 寫入 sync_adj_ 前綴的調整值（FraudRuleWeightManager.getAdjustedWeight 會讀取）
            editor.putInt("sync_adj_$keyword", weight)
        }

        editor.apply()
    }

    /**
     * 取得已同步的遠端規則（從本地快取讀取，不發網路請求）。
     */
    fun getCachedRemoteRules(): List<RemoteRule> {
        val jsonStr = prefs.getString(KEY_REMOTE_RULES, null) ?: return emptyList()
        return try {
            val rows = Json.parseToJsonElement(jsonStr).jsonArray
            rows.mapNotNull { element ->
                val obj = element.jsonObject
                RemoteRule(
                    keyword = obj["keyword"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    fraudType = obj["fraud_type"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN",
                    weight = obj["weight"]?.jsonPrimitive?.intOrNull ?: 0,
                    isCombo = obj["is_combo"]?.jsonPrimitive?.booleanOrNull ?: false,
                    comboPartner = obj["combo_partner"]?.jsonPrimitive?.contentOrNull,
                    version = obj["version"]?.jsonPrimitive?.intOrNull ?: 1
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 取得合併後的最終權重（remote + local feedback）。
     * @param keyword 關鍵字
     * @param defaultWeight 規則庫的預設權重
     * @param weightManager 本地回饋權重管理器
     * @return 最終權重
     */
    fun getMergedWeight(
        keyword: String,
        defaultWeight: Float,
        weightManager: FraudRuleWeightManager
    ): Float {
        return weightManager.getAdjustedWeight(keyword, defaultWeight)
    }

    /**
     * 取得上次同步時間（毫秒，0 = 從未同步）。
     */
    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC, 0)

    /**
     * 取得已同步的遠端規則數量。
     */
    fun getCachedRuleCount(): Int = getCachedRemoteRules().size

    /**
     * 強制重新同步（忽略 24h 限制）。
     */
    suspend fun forceSync(): Boolean {
        prefs.edit().putLong(KEY_LAST_SYNC, 0).apply()
        return sync()
    }

    /**
     * 清除所有同步快取。
     */
    fun clearCache() {
        prefs.edit().clear().apply()
        // 也清除 fraud_weights 中的 sync_adj_ 前綴
        val weightPrefs = context.getSharedPreferences("fraud_weights", Context.MODE_PRIVATE)
        val editor = weightPrefs.edit()
        weightPrefs.all.keys.filter { it.startsWith("sync_adj_") }.forEach { editor.remove(it) }
        editor.apply()
    }
}

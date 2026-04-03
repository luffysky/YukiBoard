package com.snowrealm.yukiboard.currency

import com.snowrealm.yukiboard.data.SupabaseClient
import kotlinx.serialization.json.*

/**
 * Z幣 Supabase API — 同步餘額 + 交易紀錄。
 *
 * Supabase 資料表：
 * - `zcoin_wallets` (user_id, balance, updated_at)
 * - `zcoin_transactions` (id, user_id, type, amount, description, created_at)
 */
class ZCoinApi {

    private val supabase = SupabaseClient.getInstance()

    /** 從 Supabase 取得目前餘額 */
    suspend fun fetchBalance(userId: String, authToken: String): Int? {
        val result = supabase.select(
            table = "zcoin_wallets",
            query = "user_id=eq.$userId&select=balance",
            authToken = authToken
        )
        return result?.firstOrNull()?.jsonObject
            ?.get("balance")?.jsonPrimitive?.intOrNull
    }

    /** 記錄消費（Supabase RPC 原子操作） */
    suspend fun recordSpend(
        userId: String,
        amount: Int,
        description: String,
        authToken: String
    ): Boolean {
        val params = buildJsonObject {
            put("p_user_id", userId)
            put("p_amount", amount)
            put("p_description", description)
            put("p_type", "SPEND")
        }
        val result = supabase.rpc("zcoin_deduct", params, authToken)
        return result != null
    }

    /** 取得交易紀錄 */
    suspend fun fetchTransactions(
        userId: String,
        authToken: String,
        limit: Int = 50
    ): List<RemoteTransaction> {
        val result = supabase.select(
            table = "zcoin_transactions",
            query = "user_id=eq.$userId&order=created_at.desc&limit=$limit",
            authToken = authToken
        ) ?: return emptyList()

        return result.mapNotNull { elem ->
            val obj = elem.jsonObject
            try {
                RemoteTransaction(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    type = obj["type"]?.jsonPrimitive?.content ?: "",
                    amount = obj["amount"]?.jsonPrimitive?.intOrNull ?: 0,
                    description = obj["description"]?.jsonPrimitive?.content ?: "",
                    createdAt = obj["created_at"]?.jsonPrimitive?.content ?: ""
                )
            } catch (_: Exception) { null }
        }
    }

    data class RemoteTransaction(
        val id: String,
        val type: String,
        val amount: Int,
        val description: String,
        val createdAt: String
    )
}

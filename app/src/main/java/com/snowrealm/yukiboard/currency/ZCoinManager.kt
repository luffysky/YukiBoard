package com.snowrealm.yukiboard.currency

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Z幣錢包管理器 — 本地快取 + Supabase 同步。
 *
 * 餘額以 Supabase 為主，本地僅做快取。
 * 離線時允許暫時使用本地餘額，上線後同步。
 *
 * 使用 EncryptedSharedPreferences 保護金額與交易紀錄。
 */
class ZCoinManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "yukiboard_zcoin_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w("ZCoinManager", "EncryptedSharedPreferences failed, fallback", e)
        context.getSharedPreferences("yukiboard_zcoin", Context.MODE_PRIVATE)
    }

    /** 本地快取餘額 */
    var balance: Int
        get() = prefs.getInt(KEY_BALANCE, 0)
        private set(value) = prefs.edit().putInt(KEY_BALANCE, value).apply()

    /** 是否有足夠 Z幣 */
    fun canAfford(amount: Int): Boolean = balance >= amount

    /**
     * 消費 Z幣（本地扣除，需同步到 Supabase）。
     * @return true = 扣款成功
     */
    fun spend(amount: Int, description: String): Boolean {
        if (amount <= 0 || !canAfford(amount)) return false
        balance -= amount
        addTransaction(Transaction(
            type = TransactionType.SPEND,
            amount = -amount,
            description = description,
            timestampMs = System.currentTimeMillis()
        ))
        return true
    }

    /** 儲值 Z幣（藍新金流回調後呼叫） */
    fun topUp(amount: Int, orderId: String) {
        if (amount <= 0) return
        balance += amount
        addTransaction(Transaction(
            type = TransactionType.TOP_UP,
            amount = amount,
            description = "儲值 #$orderId",
            timestampMs = System.currentTimeMillis()
        ))
    }

    /** 從伺服器同步餘額 */
    fun syncBalance(serverBalance: Int) {
        balance = serverBalance
    }

    /** 取得交易紀錄（最近 50 筆） */
    fun getTransactions(): List<Transaction> {
        val raw = prefs.getString(KEY_TRANSACTIONS, null) ?: return emptyList()
        return raw.split("||").mapNotNull { parseTransaction(it) }
    }

    /** 清除本地資料（登出時呼叫） */
    fun clear() {
        prefs.edit().clear().apply()
    }

    // ─────────────── 內部 ───────────────

    private fun addTransaction(tx: Transaction) {
        val existing = getTransactions().toMutableList()
        existing.add(0, tx)
        val trimmed = existing.take(MAX_TRANSACTIONS)
        val encoded = trimmed.joinToString("||") { encodeTransaction(it) }
        prefs.edit().putString(KEY_TRANSACTIONS, encoded).apply()
    }

    private fun encodeTransaction(tx: Transaction): String =
        "${tx.type.name}|${tx.amount}|${tx.description}|${tx.timestampMs}"

    private fun parseTransaction(raw: String): Transaction? {
        val parts = raw.split("|")
        if (parts.size < 4) return null
        return try {
            Transaction(
                type = TransactionType.valueOf(parts[0]),
                amount = parts[1].toInt(),
                description = parts[2],
                timestampMs = parts[3].toLong()
            )
        } catch (_: Exception) { null }
    }

    data class Transaction(
        val type: TransactionType,
        val amount: Int,
        val description: String,
        val timestampMs: Long
    )

    enum class TransactionType { TOP_UP, SPEND, REWARD, REFUND }

    companion object {
        private const val KEY_BALANCE = "zcoin_balance"
        private const val KEY_TRANSACTIONS = "zcoin_transactions"
        private const val MAX_TRANSACTIONS = 50
    }
}

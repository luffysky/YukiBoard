package com.snowrealm.yukiboard.currency

import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.telemetry.Severity
import com.snowrealm.yukiboard.telemetry.Telemetry
import com.snowrealm.yukiboard.telemetry.TelemetryEvent
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 藍新金流客戶端 — Z幣儲值用。
 *
 * 流程：
 * 1. 建立交易參數 → AES 加密 → SHA256 Hash
 * 2. 開啟 WebView 導向藍新付款頁
 * 3. 付款完成 → NotifyURL 回調 → Supabase 更新餘額
 *
 * API Key 從 .env 注入 BuildConfig。
 */
class NewebpayClient {

    private val merchantId = BuildConfig.NEWEBPAY_MERCHANT_ID
    private val hashKey = BuildConfig.NEWEBPAY_HASH_KEY
    private val hashIv = BuildConfig.NEWEBPAY_HASH_IV

    val isConfigured: Boolean
        get() = merchantId.isNotBlank() && hashKey.isNotBlank() && hashIv.isNotBlank()

    /**
     * 產生儲值頁面 URL 參數。
     * @param amount 儲值金額（TWD）
     * @param orderId 訂單編號
     * @param email 買家 Email
     * @return 可用於 WebView POST 的表單參數，null = 設定不完整
     */
    fun buildPaymentParams(amount: Int, orderId: String, email: String): PaymentParams? {
        if (!isConfigured) return null

        return try {
            val tradeInfo = buildTradeInfo(amount, orderId, email)
            val encrypted = aesEncrypt(tradeInfo)
            val tradeSha = sha256("HashKey=$hashKey&$encrypted&HashIV=$hashIv")

            PaymentParams(
                merchantId = merchantId,
                tradeInfo = encrypted,
                tradeSha = tradeSha,
                version = "2.0",
                postUrl = PAYMENT_URL
            )
        } catch (e: Exception) {
            Telemetry.report(TelemetryEvent(
                category = "PAYMENT",
                severity = Severity.ERROR,
                code = "NEWEBPAY_SIGN_FAIL",
                extras = mapOf("exception" to e.javaClass.simpleName)
            ))
            null
        }
    }

    /** Z幣兌換比率：1 TWD = 10 Z幣 */
    fun calculateZCoins(twdAmount: Int): Int = twdAmount * EXCHANGE_RATE

    /** 儲值方案列表 */
    fun getPlans(): List<TopUpPlan> = listOf(
        TopUpPlan(30, calculateZCoins(30), "入門"),
        TopUpPlan(100, calculateZCoins(100), "標準"),
        TopUpPlan(300, calculateZCoins(300), "超值"),
        TopUpPlan(500, calculateZCoins(500), "豪華"),
        TopUpPlan(1000, calculateZCoins(1000), "至尊")
    )

    // ─────────────── 加密 ───────────────

    private fun buildTradeInfo(amount: Int, orderId: String, email: String): String {
        return "MerchantID=$merchantId" +
                "&RespondType=JSON" +
                "&TimeStamp=${System.currentTimeMillis() / 1000}" +
                "&Version=2.0" +
                "&MerchantOrderNo=$orderId" +
                "&Amt=$amount" +
                "&ItemDesc=Z幣儲值" +
                "&Email=$email" +
                "&LoginType=0"
    }

    private fun aesEncrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(hashKey.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(hashIv.toByteArray(Charsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return encrypted.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
    }

    data class PaymentParams(
        val merchantId: String,
        val tradeInfo: String,
        val tradeSha: String,
        val version: String,
        val postUrl: String
    )

    data class TopUpPlan(val twdAmount: Int, val zCoins: Int, val label: String)

    companion object {
        const val PAYMENT_URL = "https://ccore.newebpay.com/MPG/mpg_gateway"
        const val EXCHANGE_RATE = 10
    }
}

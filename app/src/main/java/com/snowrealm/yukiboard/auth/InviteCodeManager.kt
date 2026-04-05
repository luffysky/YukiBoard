package com.snowrealm.yukiboard.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.snowrealm.yukiboard.BuildConfig
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class InviteCodeType(val char: Char, val dailyTokenLimit: Int) {
    STANDARD('S', 5000),
    KOL('K', 20000),
    PERMANENT('P', Int.MAX_VALUE)
}

class InviteCodeManager(context: Context) {

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "yukiboard_invite",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        android.util.Log.w("InviteCodeMgr", "EncryptedPrefs failed, fallback", e)
        context.getSharedPreferences("yukiboard_invite_fallback", Context.MODE_PRIVATE)
    }

    /** Validate invite code format: YUKI-{PAYLOAD}-{HMAC8} */
    fun validateCode(code: String): InviteCodeType? {
        val upper = code.trim().uppercase()
        // Expected format: YUKI-{PAYLOAD}-{HMAC8}
        val parts = upper.split("-")
        if (parts.size < 3 || parts[0] != "YUKI") return null

        // Reconstruct: PAYLOAD = everything between first YUKI- and last -HMAC
        val hmac = parts.last()
        if (hmac.length != 8) return null

        // Payload is between YUKI- and -HMAC
        val payload = parts.subList(1, parts.size - 1).joinToString("-")
        if (payload.isEmpty()) return null

        // Determine type from first char of payload
        val typeChar = payload[0]
        val codeType = InviteCodeType.entries.find { it.char == typeChar } ?: return null

        // Extract YYYYMM (chars 1..6 of payload)
        if (payload.length < 7) return null
        val yearMonth = payload.substring(1, 7)

        // Check expiry: must be current month or within next 3 months
        if (!isWithinValidPeriod(yearMonth)) return null

        // Verify HMAC-SHA256
        val expectedHmac = computeHmac(payload).take(8).uppercase()
        if (hmac != expectedHmac) return null

        return codeType
    }

    fun activateCode(code: String, type: InviteCodeType) {
        prefs.edit()
            .putString("invite_code", code.trim().uppercase())
            .putString("invite_type", type.name)
            .putString("activation_date", todayString())
            .putInt("daily_token_used", 0)
            .putString("usage_date", todayString())
            .apply()
    }

    fun isActivated(): Boolean = prefs.getString("invite_code", null) != null

    fun getActivatedCode(): String? = prefs.getString("invite_code", null)

    fun getCodeType(): InviteCodeType? {
        val typeName = prefs.getString("invite_type", null) ?: return null
        return try { InviteCodeType.valueOf(typeName) } catch (_: Exception) { null }
    }

    fun getDailyTokenLimit(): Int = getCodeType()?.dailyTokenLimit ?: 0

    fun getDailyTokenUsed(): Int {
        resetDailyUsageIfNeeded()
        return prefs.getInt("daily_token_used", 0)
    }

    fun getRemainingTokens(): Int {
        val limit = getDailyTokenLimit()
        if (limit == Int.MAX_VALUE) return Int.MAX_VALUE
        return (limit - getDailyTokenUsed()).coerceAtLeast(0)
    }

    @Synchronized
    fun consumeTokens(count: Int) {
        resetDailyUsageIfNeeded()
        val current = prefs.getInt("daily_token_used", 0)
        prefs.edit().putInt("daily_token_used", current + count).apply()
    }

    fun resetDailyUsageIfNeeded() {
        val storedDate = prefs.getString("usage_date", "") ?: ""
        val today = todayString()
        if (storedDate != today) {
            prefs.edit()
                .putInt("daily_token_used", 0)
                .putString("usage_date", today)
                .apply()
        }
    }

    fun revokeCode() {
        prefs.edit().clear().apply()
    }

    private fun computeHmac(payload: String): String {
        val secret = BuildConfig.INVITE_HMAC_SECRET
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02X".format(it) }
    }

    private fun isWithinValidPeriod(yearMonth: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("yyyyMM", Locale.US)
            sdf.isLenient = false
            val codeDate = sdf.parse(yearMonth) ?: return false
            val cal = Calendar.getInstance()
            // Start of current month
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val currentMonthStart = cal.time
            // 3 months from now
            cal.add(Calendar.MONTH, 4) // end of 3rd month
            val futureLimit = cal.time
            codeDate >= currentMonthStart && codeDate < futureLimit
        } catch (_: Exception) { false }
    }

    private fun todayString(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}

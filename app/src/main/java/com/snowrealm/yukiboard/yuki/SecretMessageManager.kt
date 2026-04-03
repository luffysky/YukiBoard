package com.snowrealm.yukiboard.yuki

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 私房話模式 — AES-256-GCM 加解密。
 *
 * 加密格式：`[YB🔐] {base64(iv + ciphertext + tag)}`
 * 收方在 YukiBoard 內看到此格式自動解密顯示。
 */
class SecretMessageManager(passphrase: String = DEFAULT_PASSPHRASE) {

    private val secretKey: SecretKey = deriveKey(passphrase)

    /** 加密明文 → `[YB🔐] base64` */
    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // iv + ciphertext（含 tag）
        val combined = iv + ciphertext
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        return "$PREFIX $encoded"
    }

    /** 解密 `[YB🔐] base64` → 明文，失敗回傳 null */
    fun decrypt(message: String): String? {
        if (!isEncryptedMessage(message)) return null

        return try {
            val encoded = message.removePrefix(PREFIX).trim()
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH) return null

            val iv = combined.copyOfRange(0, IV_LENGTH)
            val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** 檢查是否為加密訊息 */
    fun isEncryptedMessage(text: String): Boolean = text.startsWith(PREFIX)

    private fun deriveKey(passphrase: String): SecretKey {
        // SHA-256 hash 作為 key（簡化版，正式版應用 PBKDF2）
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        const val PREFIX = "[YB\uD83D\uDD10]"  // [YB🔐]
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val DEFAULT_PASSPHRASE = "yukiboard_secret_2026"
    }
}

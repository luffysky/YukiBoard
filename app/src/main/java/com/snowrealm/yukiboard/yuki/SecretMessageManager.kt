package com.snowrealm.yukiboard.yuki

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 私房話模式 — AES-256-GCM 加解密。
 *
 * 新格式（密碼制）：`[YUKI🔐]base64(salt + iv + ciphertext + tag)`
 * 舊格式（相容）：`[YB🔐] base64(iv + ciphertext + tag)`
 *
 * 發送方：設定密碼 → Enter 送出時用該密碼 AES 加密
 * 接收方：開啟解密面板 → 輸入密碼 → 貼上密文 → 解密
 */
class SecretMessageManager(passphrase: String = DEFAULT_PASSPHRASE) {

    private val secretKey: SecretKey = deriveKeySimple(passphrase)

    // ═══════════════════════════════════════════════════════════
    // 新版密碼制加解密（PBKDF2 + 隨機 salt）
    // ═══════════════════════════════════════════════════════════

    /** 用自訂密碼加密 → `[YUKI🔐]base64` */
    fun encryptWithPassword(plaintext: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKeyPBKDF2(password, salt)
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // salt + iv + ciphertext（含 GCM tag）
        val combined = salt + iv + ciphertext
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        return "$NEW_PREFIX$encoded"
    }

    /** 用自訂密碼解密 `[YUKI🔐]base64` → 明文，密碼錯誤或格式錯誤回傳 null */
    fun decryptWithPassword(message: String, password: String): String? {
        // 新格式
        if (message.startsWith(NEW_PREFIX)) {
            return try {
                val encoded = message.removePrefix(NEW_PREFIX).trim()
                val combined = Base64.decode(encoded, Base64.NO_WRAP)
                if (combined.size < SALT_LENGTH + IV_LENGTH) return null

                val salt = combined.copyOfRange(0, SALT_LENGTH)
                val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
                val ciphertext = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)

                val key = deriveKeyPBKDF2(password, salt)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (_: Exception) {
                null // 密碼錯誤或資料損壞
            }
        }

        // 舊格式相容（固定 passphrase）
        if (message.startsWith(LEGACY_PREFIX)) {
            return decrypt(message)
        }

        return null
    }

    // ═══════════════════════════════════════════════════════════
    // 舊版固定 passphrase 加解密（向後相容）
    // ═══════════════════════════════════════════════════════════

    /** 舊版加密 → `[YB🔐] base64` */
    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = iv + ciphertext
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        return "$LEGACY_PREFIX $encoded"
    }

    /** 舊版解密 `[YB🔐] base64` → 明文 */
    fun decrypt(message: String): String? {
        if (!message.startsWith(LEGACY_PREFIX)) return null

        return try {
            val encoded = message.removePrefix(LEGACY_PREFIX).trim()
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

    /** 檢查是否為加密訊息（新或舊格式） */
    fun isEncryptedMessage(text: String): Boolean =
        text.startsWith(NEW_PREFIX) || text.startsWith(LEGACY_PREFIX)

    /** 檢查是否為新格式（密碼制） */
    fun isPasswordEncrypted(text: String): Boolean = text.startsWith(NEW_PREFIX)

    // ═══════════════════════════════════════════════════════════
    // Key derivation
    // ═══════════════════════════════════════════════════════════

    private fun deriveKeySimple(passphrase: String): SecretKey {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun deriveKeyPBKDF2(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        const val NEW_PREFIX = "[YUKI\uD83D\uDD10]"  // [YUKI🔐]
        const val LEGACY_PREFIX = "[YB\uD83D\uDD10]"  // [YB🔐]
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val SALT_LENGTH = 16
        private const val TAG_LENGTH_BITS = 128
        private const val PBKDF2_ITERATIONS = 10000
        private const val DEFAULT_PASSPHRASE = "yukiboard_secret_2026"
    }
}

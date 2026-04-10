package com.snowrealm.yukiboard.claw.provider

import android.content.Context
import android.util.Base64
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.data.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * API Key 雲端同步 — 將使用者的 AI API Key 加密後同步到 Supabase。
 *
 * 安全機制：
 * - Key 在裝置端用 AES-256-CBC 加密後才上傳
 * - 加密密鑰由 userId + 固定鹽 衍生（只有同帳號的裝置能解密）
 * - Supabase RLS 確保只有本人能讀寫自己的資料
 *
 * 資料表：user_ai_keys (需在 Supabase 建立)
 *   - user_id TEXT PRIMARY KEY
 *   - encrypted_keys TEXT (JSON string, AES encrypted then Base64)
 *   - updated_at TIMESTAMPTZ
 */
object ApiKeySync {

    private const val TABLE = "user_ai_keys"
    private const val SALT = "YukiBoard_AiKeySync_2026"

    data class SyncResult(val success: Boolean, val message: String)

    /**
     * 上傳目前的 API Key 到雲端（加密）
     */
    suspend fun uploadKeys(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val auth = SnowRealmAuthClient(context)
        if (!auth.isLoggedIn) return@withContext SyncResult(false, "請先登入帳號")
        val userId = auth.currentUserId ?: return@withContext SyncResult(false, "無法取得用戶 ID")
        val token = auth.getToken() ?: return@withContext SyncResult(false, "Token 無效")

        val supabase = SupabaseClient.getInstance()
        if (!supabase.isConfigured) return@withContext SyncResult(false, "Supabase 未設定")

        val mgr = AIProviderManager.getInstance(context)

        // 收集所有 API Key
        val keysJson = buildJsonObject {
            put("selected_provider", mgr.getSelectedProviderType().name)
            for (type in AIProviderType.entries) {
                val key = mgr.getApiKey(type)
                if (!key.isNullOrBlank()) {
                    put("key_${type.name}", key)
                }
                val model = mgr.getSelectedModel(type)
                put("model_${type.name}", model)
            }
        }

        // 加密
        val encrypted = try {
            encrypt(keysJson.toString(), userId)
        } catch (e: Exception) {
            return@withContext SyncResult(false, "加密失敗: ${e.message}")
        }

        // Upsert 到 Supabase
        val data = buildJsonObject {
            put("user_id", userId)
            put("encrypted_keys", encrypted)
            put("updated_at", java.time.Instant.now().toString())
        }

        // 先嘗試 update，失敗則 insert
        val existing = supabase.select(TABLE, "user_id=eq.$userId", token)
        val result = if (existing != null && existing.size > 0) {
            supabase.update(TABLE, "user_id=eq.$userId", data, token)
        } else {
            supabase.insert(TABLE, data, token)
        }

        if (result != null) {
            SyncResult(true, "API Key 已同步到雲端")
        } else {
            SyncResult(false, "上傳失敗，請檢查網路")
        }
    }

    /**
     * 從雲端下載 API Key 並還原到本機
     */
    suspend fun downloadKeys(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val auth = SnowRealmAuthClient(context)
        if (!auth.isLoggedIn) return@withContext SyncResult(false, "請先登入帳號")
        val userId = auth.currentUserId ?: return@withContext SyncResult(false, "無法取得用戶 ID")
        val token = auth.getToken() ?: return@withContext SyncResult(false, "Token 無效")

        val supabase = SupabaseClient.getInstance()
        if (!supabase.isConfigured) return@withContext SyncResult(false, "Supabase 未設定")

        val rows = supabase.select(TABLE, "user_id=eq.$userId&select=encrypted_keys", token)
        if (rows == null || rows.isEmpty()) return@withContext SyncResult(false, "雲端沒有已儲存的 Key")

        val encrypted = rows[0].jsonObject["encrypted_keys"]?.jsonPrimitive?.contentOrNull
            ?: return@withContext SyncResult(false, "資料格式錯誤")

        // 解密
        val decrypted = try {
            decrypt(encrypted, userId)
        } catch (e: Exception) {
            return@withContext SyncResult(false, "解密失敗: ${e.message}")
        }

        // 解析 JSON 並還原
        val json = try {
            Json.parseToJsonElement(decrypted).jsonObject
        } catch (e: Exception) {
            return@withContext SyncResult(false, "JSON 解析失敗")
        }

        val mgr = AIProviderManager.getInstance(context)
        var restoredCount = 0

        for (type in AIProviderType.entries) {
            val key = json["key_${type.name}"]?.jsonPrimitive?.contentOrNull
            if (!key.isNullOrBlank()) {
                mgr.saveApiKey(type, key)
                restoredCount++
            }
            val model = json["model_${type.name}"]?.jsonPrimitive?.contentOrNull
            if (!model.isNullOrBlank()) {
                mgr.setSelectedModel(type, model)
            }
        }

        val provider = json["selected_provider"]?.jsonPrimitive?.contentOrNull
        if (provider != null) {
            try {
                mgr.setSelectedProvider(AIProviderType.valueOf(provider))
            } catch (_: Exception) {}
        }

        SyncResult(true, "已還原 $restoredCount 組 API Key")
    }

    // ═════════════════════════════════════════════════════════
    // AES-256-CBC 加解密
    // ═════════════════════════════════════════════════════════

    private fun deriveKey(userId: String): SecretKeySpec {
        val raw = "$userId:$SALT".toByteArray(Charsets.UTF_8)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw)
        return SecretKeySpec(hash, "AES")
    }

    private fun encrypt(plainText: String, userId: String): String {
        val key = deriveKey(userId)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // iv(16) + encrypted → Base64
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(base64: String, userId: String): String {
        val key = deriveKey(userId)
        val combined = Base64.decode(base64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 16)
        val encrypted = combined.copyOfRange(16, combined.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}

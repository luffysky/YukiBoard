package com.snowrealm.yukiboard.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Insight 主題 / 皮膚雲端同步。
 *
 * 功能：
 * 1. 登入後自動拉取在 Insight 購買的主題
 * 2. 本地設定（主題、背景）上傳備份
 * 3. 換手機後登入自動恢復設定
 */
class InsightThemeSync(private val context: Context) {

    private val api = InsightApiClient.getInstance(context)

    data class RemoteTheme(
        val id: String,
        val name: String,
        val previewUrl: String,
        val isPurchased: Boolean,
        val isFree: Boolean
    )

    /** 從 Insight 拉取使用者擁有的主題清單 */
    suspend fun fetchOwnedThemes(): List<RemoteTheme> = withContext(Dispatchers.IO) {
        val result = api.get("/api/yukiboard/themes")
        val arr = result.dataOrNull()?.jsonObject
            ?.get("themes")?.jsonArray ?: return@withContext emptyList()

        arr.mapNotNull { elem ->
            val obj = elem.jsonObject
            try {
                RemoteTheme(
                    id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    previewUrl = obj["preview_url"]?.jsonPrimitive?.content ?: "",
                    isPurchased = obj["is_purchased"]?.jsonPrimitive?.booleanOrNull ?: false,
                    isFree = obj["is_free"]?.jsonPrimitive?.booleanOrNull ?: true
                )
            } catch (_: Exception) { null }
        }
    }

    /** 上傳目前的鍵盤設定到 Insight（備份用） */
    suspend fun backupSettings(
        currentThemeId: String,
        keyboardHeight: Float,
        keyboardOpacity: Float
    ): Boolean = withContext(Dispatchers.IO) {
        val result = api.post("/api/yukiboard/settings/backup", buildJsonObject {
            put("theme_id", currentThemeId)
            put("keyboard_height_ratio", keyboardHeight)
            put("keyboard_opacity", keyboardOpacity)
        })
        result.isSuccess
    }

    /** 從 Insight 恢復設定（首次登入或換手機後） */
    suspend fun restoreSettings(): RestoreResult = withContext(Dispatchers.IO) {
        val result = api.get("/api/yukiboard/settings/restore")
        val data = result.dataOrNull()?.jsonObject ?: return@withContext RestoreResult(false)
        val restored = data["restored"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!restored) return@withContext RestoreResult(false)

        RestoreResult(
            restored = true,
            themeId = data["theme_id"]?.jsonPrimitive?.content,
            keyboardHeightRatio = data["keyboard_height_ratio"]?.jsonPrimitive?.floatOrNull,
            keyboardOpacity = data["keyboard_opacity"]?.jsonPrimitive?.floatOrNull
        )
    }

    data class RestoreResult(
        val restored: Boolean,
        val themeId: String? = null,
        val keyboardHeightRatio: Float? = null,
        val keyboardOpacity: Float? = null
    )
}

package com.snowrealm.yukiboard.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Insight 社群分享客戶端。
 *
 * Claw Skill 產出的內容一鍵分享到 Insight 貼文。
 * 使用者主動觸發，不自動分享。
 */
class InsightSocialClient(private val context: Context) {

    private val api = InsightApiClient.getInstance(context)

    data class ShareResult(
        val postId: String,
        val postUrl: String
    )

    /**
     * 分享文字到 Insight 貼文。
     * @param content 要分享的文字（限 500 字）
     * @param sourceSkill 哪個 Skill 產生的（標注來源）
     * @return 貼文結果，null 表示失敗
     */
    suspend fun shareText(content: String, sourceSkill: String = ""): ShareResult? =
        withContext(Dispatchers.IO) {
            if (content.isBlank()) return@withContext null

            val result = api.post("/api/yukiboard/social/share", buildJsonObject {
                put("content", content.take(500))
                put("source", "yukiboard")
                if (sourceSkill.isNotBlank()) put("skill", sourceSkill)
            })

            val data = result.dataOrNull()?.jsonObject ?: return@withContext null
            val postId = data["post_id"]?.jsonPrimitive?.content ?: return@withContext null
            val postUrl = data["post_url"]?.jsonPrimitive?.content ?: ""

            ShareResult(postId, postUrl)
        }
}

package com.snowrealm.yukiboard.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Insight 情境問卷客戶端。
 *
 * 觸發時機（由 YukiBoardIMEService 呼叫）：
 * - 打字情緒偵測到 SAD / ANGRY / EXCITED 時
 * - 每次觸發間隔至少 30 分鐘（節流）
 * - 使用者可在設定頁關閉（預設 off，opt-in）
 */
class InsightSurveyClient(private val context: Context) {

    private val api = InsightApiClient.getInstance(context)
    private val prefs = context.getSharedPreferences("yukiboard_survey", Context.MODE_PRIVATE)

    private val THROTTLE_MS = 30 * 60 * 1000L

    data class SurveyQuestion(
        val surveyId: String,
        val question: String,
        val options: List<String>,
        val rewardZCoin: Int = 0
    )

    /**
     * 檢查當前情境是否有對應問卷。
     * 節流：30 分鐘內不重複觸發。
     */
    suspend fun checkTrigger(emotion: String, appPackage: String): SurveyQuestion? =
        withContext(Dispatchers.IO) {
            val lastTrigger = prefs.getLong("last_trigger_ms", 0)
            if (System.currentTimeMillis() - lastTrigger < THROTTLE_MS) return@withContext null

            val result = api.post("/api/yukiboard/survey/trigger", buildJsonObject {
                put("emotion", emotion)
                put("app_package", appPackage)
            })

            val data = result.dataOrNull()?.jsonObject ?: return@withContext null
            val question = data["question"]?.jsonPrimitive?.content ?: return@withContext null
            val options = data["options"]?.jsonArray?.map {
                it.jsonPrimitive.content
            } ?: return@withContext null

            prefs.edit().putLong("last_trigger_ms", System.currentTimeMillis()).apply()

            SurveyQuestion(
                surveyId = data["survey_id"]?.jsonPrimitive?.content ?: "",
                question = question,
                options = options,
                rewardZCoin = data["reward_zcoin"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            )
        }

    /**
     * 提交問卷答案。
     * @return 獲得的 Z 幣數量（0 = 提交失敗或無獎勵）
     */
    suspend fun submitAnswer(surveyId: String, optionIndex: Int): Int =
        withContext(Dispatchers.IO) {
            val result = api.post("/api/yukiboard/survey/answer", buildJsonObject {
                put("survey_id", surveyId)
                put("option_index", optionIndex)
            })
            val count = prefs.getInt("answer_count_30d", 0)
            prefs.edit().putInt("answer_count_30d", count + 1).apply()
            result.dataOrNull()?.jsonObject
                ?.get("reward_zcoin")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        }
}

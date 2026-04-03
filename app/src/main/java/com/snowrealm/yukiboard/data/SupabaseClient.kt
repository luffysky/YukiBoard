package com.snowrealm.yukiboard.data

import com.snowrealm.yukiboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * YukiBoard Supabase 客戶端（Zeabur 獨立專案）。
 *
 * 用途：
 * - Z幣餘額 + 交易紀錄
 * - 皮膚商城商品
 * - 連線對戰 Realtime（預留）
 * - 排行榜
 *
 * JWT 互通：登入取得的 Insight JWT 也可用於 Supabase RLS（需設定 jwt_secret 相同）。
 */
class SupabaseClient private constructor() {

    private val supabaseUrl = BuildConfig.YUKIBOARD_SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.YUKIBOARD_SUPABASE_ANON_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = supabaseUrl.isNotBlank() && anonKey.isNotBlank()

    // ─────────────── REST API ───────────────

    /** 查詢資料表 */
    suspend fun select(
        table: String,
        query: String = "",
        authToken: String? = null
    ): JsonArray? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val url = "$supabaseUrl/rest/v1/$table?$query"
        val request = buildRequest(url, authToken).get().build()
        executeJson(request)?.jsonArray
    }

    /** 插入資料 */
    suspend fun insert(
        table: String,
        data: JsonObject,
        authToken: String? = null
    ): JsonElement? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val url = "$supabaseUrl/rest/v1/$table"
        val body = data.toString().toRequestBody("application/json".toMediaType())
        val request = buildRequest(url, authToken)
            .post(body)
            .header("Prefer", "return=representation")
            .build()
        executeJson(request)
    }

    /** 更新資料 */
    suspend fun update(
        table: String,
        query: String,
        data: JsonObject,
        authToken: String? = null
    ): JsonElement? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val url = "$supabaseUrl/rest/v1/$table?$query"
        val body = data.toString().toRequestBody("application/json".toMediaType())
        val request = buildRequest(url, authToken)
            .patch(body)
            .header("Prefer", "return=representation")
            .build()
        executeJson(request)
    }

    /** RPC 呼叫 */
    suspend fun rpc(
        functionName: String,
        params: JsonObject = buildJsonObject {},
        authToken: String? = null
    ): JsonElement? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val url = "$supabaseUrl/rest/v1/rpc/$functionName"
        val body = params.toString().toRequestBody("application/json".toMediaType())
        val request = buildRequest(url, authToken).post(body).build()
        executeJson(request)
    }

    // ─────────────── 內部 ───────────────

    private fun buildRequest(url: String, authToken: String?): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
        if (authToken != null) {
            builder.header("Authorization", "Bearer $authToken")
        }
        return builder
    }

    private fun executeJson(request: Request): JsonElement? {
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            if (!response.isSuccessful) return null
            json.parseToJsonElement(body)
        } catch (_: Exception) { null }
    }

    companion object {
        @Volatile private var instance: SupabaseClient? = null

        fun getInstance(): SupabaseClient {
            return instance ?: synchronized(this) {
                instance ?: SupabaseClient().also { instance = it }
            }
        }
    }
}

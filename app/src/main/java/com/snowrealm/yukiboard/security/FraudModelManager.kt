package com.snowrealm.yukiboard.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.snowrealm.yukiboard.app.net.AppHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * 防詐模型版本管理器。
 *
 * 職責：
 * 1. 啟動時檢查 Supabase 上的模型版本
 * 2. 若有新版本 → 下載新模型 + 詞表並替換
 * 3. 版本追蹤保存在 SharedPreferences
 *
 * Supabase 表結構（fraud_model_versions）：
 *   - id: UUID
 *   - model_version: Int (遞增)
 *   - download_url: String (tflite 檔案 URL)
 *   - vocab_url: String (vocab.txt URL)
 *   - min_app_version: Int (最低 app versionCode)
 *   - release_notes: String
 *   - created_at: Timestamp
 */
class FraudModelManager(private val context: Context) {

    companion object {
        private const val TAG = "FraudModelManager"
        private const val PREFS_NAME = "fraud_model_prefs"
        private const val KEY_MODEL_VERSION = "model_version"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val KEY_DOWNLOAD_URL = "download_url"
        private const val KEY_VOCAB_URL = "vocab_url"

        /** 最短檢查間隔：6 小時 */
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        private const val MODEL_FILENAME = "fraud_detector.tflite"
        private const val VOCAB_FILENAME = "fraud_vocab.txt"
    }

    @Serializable
    data class ModelVersionInfo(
        val model_version: Int,
        val download_url: String,
        val vocab_url: String = "",
        val min_app_version: Int = 0,
        val release_notes: String = "",
    )

    /** 下載進度回調 */
    interface DownloadCallback {
        fun onProgress(percent: Int)
        fun onComplete(success: Boolean, message: String)
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val httpClient = AppHttpClient.newBuilder(AppHttpClient.Profile.STREAMING).build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 目前已安裝的模型版本（0 = 未安裝） */
    val currentModelVersion: Int
        get() = prefs.getInt(KEY_MODEL_VERSION, 0)

    /** 模型檔案是否存在 */
    val isModelInstalled: Boolean
        get() = context.getFileStreamPath(MODEL_FILENAME)?.exists() == true

    /**
     * 啟動時自動檢查更新。
     * 若距離上次檢查超過 CHECK_INTERVAL_MS 且有新版本，自動下載。
     */
    suspend fun checkAndUpdateIfNeeded(
        supabaseUrl: String,
        supabaseAnonKey: String,
        appVersionCode: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        val now = System.currentTimeMillis()

        if (now - lastCheck < CHECK_INTERVAL_MS) {
            Log.d(TAG, "Skip check: last check was ${(now - lastCheck) / 1000}s ago")
            return@withContext false
        }

        try {
            val remoteInfo = fetchLatestVersion(supabaseUrl, supabaseAnonKey)
                ?: return@withContext false

            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

            // 檢查是否需要更新
            if (remoteInfo.model_version <= currentModelVersion) {
                Log.d(TAG, "Model is up to date (v${currentModelVersion})")
                return@withContext false
            }

            // 檢查 app 版本要求
            if (appVersionCode < remoteInfo.min_app_version) {
                Log.w(TAG, "App too old (${appVersionCode} < ${remoteInfo.min_app_version}), skip model update")
                return@withContext false
            }

            Log.i(TAG, "New model available: v${remoteInfo.model_version} (current: v${currentModelVersion})")

            // 下載新模型
            val success = downloadModel(remoteInfo)
            if (success) {
                prefs.edit()
                    .putInt(KEY_MODEL_VERSION, remoteInfo.model_version)
                    .putString(KEY_DOWNLOAD_URL, remoteInfo.download_url)
                    .putString(KEY_VOCAB_URL, remoteInfo.vocab_url)
                    .apply()
                Log.i(TAG, "Model updated to v${remoteInfo.model_version}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Auto-update failed: ${e.message}")
            false
        }
    }

    /**
     * 手動觸發模型更新（設定頁按鈕）。
     */
    suspend fun manualUpdate(
        supabaseUrl: String,
        supabaseAnonKey: String,
        appVersionCode: Int,
        callback: DownloadCallback? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            callback?.onProgress(0)

            val remoteInfo = fetchLatestVersion(supabaseUrl, supabaseAnonKey)
            if (remoteInfo == null) {
                callback?.onComplete(false, "無法取得模型版本資訊")
                return@withContext false
            }

            callback?.onProgress(10)

            if (remoteInfo.model_version <= currentModelVersion) {
                callback?.onComplete(true, "模型已是最新版本 (v${currentModelVersion})")
                return@withContext true
            }

            if (appVersionCode < remoteInfo.min_app_version) {
                callback?.onComplete(false, "請先更新 App 至最新版本")
                return@withContext false
            }

            callback?.onProgress(20)

            val success = downloadModel(remoteInfo) { percent ->
                // Scale 20~90
                callback?.onProgress(20 + (percent * 0.7).toInt())
            }

            if (success) {
                prefs.edit()
                    .putInt(KEY_MODEL_VERSION, remoteInfo.model_version)
                    .putString(KEY_DOWNLOAD_URL, remoteInfo.download_url)
                    .putString(KEY_VOCAB_URL, remoteInfo.vocab_url)
                    .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                    .apply()

                callback?.onProgress(100)
                callback?.onComplete(true, "模型已更新至 v${remoteInfo.model_version}")
                true
            } else {
                callback?.onComplete(false, "下載失敗，請稍後再試")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Manual update failed: ${e.message}")
            callback?.onComplete(false, "更新失敗：${e.message}")
            false
        }
    }

    // ─── Network ──────────────────────────────────────────────────────────────

    private fun fetchLatestVersion(
        supabaseUrl: String,
        supabaseAnonKey: String,
    ): ModelVersionInfo? {
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            Log.w(TAG, "Supabase not configured, skip version check")
            return null
        }

        val url = "${supabaseUrl.trimEnd('/')}/rest/v1/fraud_model_versions" +
            "?select=model_version,download_url,vocab_url,min_app_version,release_notes" +
            "&order=model_version.desc" +
            "&limit=1"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", supabaseAnonKey)
            .addHeader("Authorization", "Bearer $supabaseAnonKey")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Version check failed: HTTP ${response.code}")
            return null
        }

        val body = response.body?.string() ?: return null

        return try {
            val list = json.decodeFromString<List<ModelVersionInfo>>(body)
            list.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse version info: ${e.message}")
            null
        }
    }

    private fun downloadModel(
        info: ModelVersionInfo,
        onProgress: ((Int) -> Unit)? = null,
    ): Boolean {
        // Download TFLite model
        val modelSuccess = downloadFile(info.download_url, MODEL_FILENAME) { percent ->
            onProgress?.invoke(percent / 2)  // 0~50% for model
        }

        if (!modelSuccess) return false

        // Download vocab if URL provided
        if (info.vocab_url.isNotBlank()) {
            val vocabSuccess = downloadFile(info.vocab_url, VOCAB_FILENAME) { percent ->
                onProgress?.invoke(50 + percent / 2)  // 50~100% for vocab
            }
            if (!vocabSuccess) {
                Log.w(TAG, "Vocab download failed, model might still work with bundled vocab")
            }
        } else {
            onProgress?.invoke(100)
        }

        return true
    }

    private fun downloadFile(
        url: String,
        filename: String,
        onProgress: ((Int) -> Unit)? = null,
    ): Boolean {
        if (url.isBlank()) return false

        return try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Download failed: HTTP ${response.code} for $filename")
                return false
            }

            val body = response.body ?: return false
            val contentLength = body.contentLength()
            val tmpFile = File(context.filesDir, "$filename.tmp")

            body.byteStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var len: Int

                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                        bytesRead += len
                        if (contentLength > 0) {
                            onProgress?.invoke((bytesRead * 100 / contentLength).toInt())
                        }
                    }
                }
            }

            // Atomic replace
            val targetFile = File(context.filesDir, filename)
            if (targetFile.exists()) targetFile.delete()
            tmpFile.renameTo(targetFile)

            Log.i(TAG, "Downloaded $filename (${targetFile.length() / 1024} KB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $filename: ${e.message}")
            false
        }
    }

    /**
     * 刪除已下載的模型（恢復為 assets 內建版本）。
     */
    fun resetToBuiltin() {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        val vocabFile = File(context.filesDir, VOCAB_FILENAME)
        if (modelFile.exists()) modelFile.delete()
        if (vocabFile.exists()) vocabFile.delete()
        prefs.edit()
            .putInt(KEY_MODEL_VERSION, 0)
            .remove(KEY_DOWNLOAD_URL)
            .remove(KEY_VOCAB_URL)
            .apply()
        Log.i(TAG, "Reset to built-in model")
    }

    /**
     * 取得模型狀態摘要（設定頁顯示用）。
     */
    fun getStatusSummary(): String {
        val version = currentModelVersion
        return if (version > 0) {
            "模型版本：v$version"
        } else if (isModelInstalled) {
            "使用內建模型"
        } else {
            "模型未安裝（使用規則引擎偵測，點擊上方按鈕下載 AI 模型）"
        }
    }
}

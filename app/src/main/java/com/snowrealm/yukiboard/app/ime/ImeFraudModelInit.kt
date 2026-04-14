package com.snowrealm.yukiboard.app.ime

import android.content.Context
import android.util.Log
import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.security.FraudAIAnalyzer
import com.snowrealm.yukiboard.security.FraudLocalModel
import com.snowrealm.yukiboard.security.FraudModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 防詐 TFLite 模型初始化 + Supabase 同步 — 從 YukiBoardIMEService.initFraudModel() 抽出。
 */
class ImeFraudModelInit(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    fun init(fraudAnalyzer: FraudAIAnalyzer) {
        scope.launch(Dispatchers.IO) {
            try {
                // 1. 初始化 TFLite 本地模型
                val localModel = FraudLocalModel(context)
                localModel.initialize()
                if (localModel.isAvailable()) {
                    fraudAnalyzer.localModel = localModel
                    Log.i(TAG, "Fraud TFLite model loaded")
                }

                // 2. 檢查 Supabase 是否有新版本（自動下載）
                val supaUrl = BuildConfig.YUKIBOARD_SUPABASE_URL
                val supaKey = BuildConfig.YUKIBOARD_SUPABASE_ANON_KEY
                if (supaUrl.isNotBlank() && supaKey.isNotBlank()) {
                    val mgr = FraudModelManager(context)
                    val updated = mgr.checkAndUpdateIfNeeded(
                        supaUrl, supaKey, BuildConfig.VERSION_CODE
                    )
                    if (updated) {
                        localModel.release()
                        val newModel = FraudLocalModel(context)
                        newModel.initialize()
                        if (newModel.isAvailable()) {
                            fraudAnalyzer.localModel = newModel
                            Log.i(TAG, "Fraud model updated and reloaded")
                        }
                    }
                }

                // 3. 同步政府公開防詐資料（MODA + 165 + OpenPhish）
                val openDataSync = fraudAnalyzer.openDataSync
                if (openDataSync.needsSync()) {
                    val count = openDataSync.sync()
                    Log.i(TAG, "Fraud open data synced: $count domains")
                    if (supaUrl.isNotBlank() && supaKey.isNotBlank()) {
                        openDataSync.uploadToSupabase(supaUrl, supaKey)
                        openDataSync.downloadFromSupabase(supaUrl, supaKey)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fraud model init: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "YukiBoardIME"
    }
}

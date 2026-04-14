package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.data.CloudSyncManager
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 雲端同步卡片。
 *
 * 從 SettingsActivity.buildCloudSyncCard()（原 lines 934-1089）抽出。
 */
class SyncSettingsSection(
    private val auth: SnowRealmAuthClient,
    private val syncManager: CloudSyncManager,
    private val scope: CoroutineScope,
    private val tr: (String) -> String
) {

    private var syncStatusLabel: TextView? = null

    fun buildCard(context: Context): LinearLayout {
        val card = SettingsUiHelper.simpleCard(context, tr("雲端同步"))

        // ── 帳號狀態（初始從本地快取；非同步驗證由 coordinator 統一發起）──
        syncStatusLabel = TextView(context).apply {
            text = if (auth.isLoggedIn) tr("已登入：${auth.currentDisplayName ?: auth.currentEmail ?: auth.currentUserId}")
                   else tr("尚未登入（請先在 Insight 登入）")
            textSize = 13f
            setTextColor(if (auth.isLoggedIn) 0xFF66BB6A.toInt() else 0xFFFF8A65.toInt())
            setPadding(dp(context, 4), dp(context, 4), 0, dp(context, 8))
        }
        card.addView(syncStatusLabel)

        // ── 同步開關 ──
        card.addView(SettingsUiHelper.switchRow(context, tr("啟用雲端同步"), tr("偏好/詞庫/主題/學習資料全量同步"), syncManager.isSyncEnabled) {
            syncManager.setSyncEnabled(it)
            if (it && auth.isLoggedIn) {
                scope.launch {
                    val result = syncManager.syncOnLogin()
                    val msg = when (result) {
                        is CloudSyncManager.SyncResult.Success -> tr("同步完成")
                        is CloudSyncManager.SyncResult.NotLoggedIn -> tr("請先登入")
                        is CloudSyncManager.SyncResult.Error -> result.message
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } else if (it && !auth.isLoggedIn) {
                Toast.makeText(context, tr("請先登入 Insight 帳號"), Toast.LENGTH_SHORT).show()
            }
        })

        // ── 上次同步時間 ──
        val lastSyncLabel = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF9E9E9E.toInt())
            setPadding(dp(context, 16), dp(context, 4), 0, dp(context, 8))
            val ts = syncManager.lastSyncTime
            text = if (ts > 0) {
                val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                tr("上次同步：${sdf.format(Date(ts))}")
            } else tr("尚未同步")
        }
        card.addView(lastSyncLabel)

        // ── 同步內容說明 ──
        card.addView(TextView(context).apply {
            text = tr("同步內容：偏好設定、自訂詞庫、主題、自適應字典、個人詞庫、語句預測、整句預測、語氣風格、打字習慣、校正學習")
            textSize = 11f
            setTextColor(0xFF78909C.toInt())
            setPadding(dp(context, 16), 0, dp(context, 16), dp(context, 8))
        })

        // ── 上傳 / 下載按鈕 ──
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
        }

        btnRow.addView(buildSyncButton(context, tr("上傳到雲端"), 0xFF1565C0.toInt(), lastSyncLabel) { syncManager.uploadAll() })
        btnRow.addView(buildSyncButton(context, tr("從雲端下載"), 0xFF2E7D32.toInt(), lastSyncLabel) { syncManager.downloadAll() })

        card.addView(btnRow)
        return card
    }

    /**
     * 由 SettingsScreenCoordinator 驗證 session 後回呼，更新同步狀態 label。
     * 取代原本 section 內獨立的 fetchMe() 呼叫。
     */
    fun onSessionVerified(isConnected: Boolean, displayName: String?, localIsLoggedIn: Boolean) {
        val label = syncStatusLabel ?: return
        if (isConnected && displayName != null) {
            label.text = tr("已連線：$displayName")
            label.setTextColor(0xFF66BB6A.toInt())
        } else if (localIsLoggedIn) {
            label.text = tr("Token 已過期，請重新登入")
            label.setTextColor(0xFFFF8A65.toInt())
        } else {
            label.text = tr("尚未登入（請先在 Insight 登入）")
            label.setTextColor(0xFFFF8A65.toInt())
        }
    }

    private fun buildSyncButton(
        context: Context,
        label: String,
        color: Int,
        lastSyncLabel: TextView,
        action: suspend () -> CloudSyncManager.SyncResult
    ): Button {
        return Button(context).apply {
            text = label
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(context, 8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(context, 4), 0, dp(context, 4), 0)
            }
            setOnClickListener {
                if (!auth.isLoggedIn) {
                    Toast.makeText(context, tr("請先登入 Insight 帳號"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val originalText = text
                isEnabled = false
                text = tr("處理中...")
                scope.launch {
                    val result = action()
                    isEnabled = true
                    text = originalText
                    val msg = when (result) {
                        is CloudSyncManager.SyncResult.Success -> if (color == 0xFF1565C0.toInt()) tr("上傳成功") else tr("下載成功，重啟鍵盤生效")
                        is CloudSyncManager.SyncResult.NotLoggedIn -> tr("未登入")
                        is CloudSyncManager.SyncResult.Error -> result.message
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    val ts = syncManager.lastSyncTime
                    if (ts > 0) {
                        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                        lastSyncLabel.text = tr("上次同步：${sdf.format(Date(ts))}")
                    }
                }
            }
        }
    }
}

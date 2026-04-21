package com.snowrealm.yukiboard.ui.settings.section

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.ui.auth.LoginActivity
import com.snowrealm.yukiboard.ui.settings.SettingsStrings
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp

/**
 * 登入/登出按鈕 + 使用者名稱顯示。
 *
 * 從 SettingsActivity.onCreateInternal() 的 btnRow 區塊（原 lines 171-233）抽出。
 * 不持有 Activity 引用——只需要 Context + CoroutineScope + 翻譯函數。
 */
class AuthSettingsSection(
    private val auth: SnowRealmAuthClient,
    private val scope: CoroutineScope,
    private val tr: (String) -> String
) {

    private lateinit var userNameLabel: TextView
    private lateinit var authBtn: Button

    /**
     * 建立登入區塊 View 列表（userNameLabel + authBtn），呼叫者自行加入 parent。
     * @return Pair(userNameLabel, authBtn) 方便外部取用
     */
    fun buildViews(parent: LinearLayout): Pair<TextView, Button> {
        val context = parent.context

        userNameLabel = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFF8BC34A.toInt())
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(context, 8) }
        }

        authBtn = Button(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 初始狀態（本地快取）— 非同步驗證由 SettingsScreenCoordinator 統一發起
        updateAuthUI(auth.isLoggedIn, auth.currentDisplayName ?: auth.currentEmail)

        authBtn.setOnClickListener {
            if (auth.isLoggedIn) {
                auth.logout()
                updateAuthUI(false, null)
                Toast.makeText(context, tr("已登出"), Toast.LENGTH_SHORT).show()
            } else {
                context.startActivity(Intent(context, LoginActivity::class.java))
            }
        }

        parent.addView(userNameLabel)
        parent.addView(authBtn)

        return Pair(userNameLabel, authBtn)
    }

    /**
     * 由 SettingsScreenCoordinator 驗證 session 後回呼，更新 auth UI。
     * 取代原本 section 內獨立的 fetchMe() 呼叫。
     */
    fun onSessionVerified(isConnected: Boolean, displayName: String?, localIsLoggedIn: Boolean) {
        if (!::authBtn.isInitialized) return
        updateAuthUI(isConnected || localIsLoggedIn, displayName)
    }

    private fun updateAuthUI(loggedIn: Boolean, displayName: String?) {
        val context = authBtn.context
        if (loggedIn) {
            userNameLabel.text = if (displayName.isNullOrBlank()) tr("已登入") else displayName
            userNameLabel.visibility = View.VISIBLE
            authBtn.text = tr("登出")
            authBtn.background = GradientDrawable().apply {
                setColor(0xFFC62828.toInt())
                cornerRadius = dp(context, 8).toFloat()
            }
        } else {
            userNameLabel.visibility = View.GONE
            authBtn.text = tr("登入")
            authBtn.background = GradientDrawable().apply {
                setColor(0xFF4A90E2.toInt())
                cornerRadius = dp(context, 8).toFloat()
            }
        }
    }
}

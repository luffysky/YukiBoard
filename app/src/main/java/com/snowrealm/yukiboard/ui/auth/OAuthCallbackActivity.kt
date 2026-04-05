package com.snowrealm.yukiboard.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import kotlinx.coroutines.*

/**
 * OAuth callback 接收器 — 透明 Activity。
 *
 * Deep link: yukiboard://auth/callback?token=xxx
 * Insight 後端在 LINE/Google OAuth 完成後 redirect 到此 URI，
 * 帶上 admin_session token。
 */
class OAuthCallbackActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallback(intent)
    }

    private fun handleCallback(intent: Intent?) {
        val uri = intent?.data
        val token = uri?.getQueryParameter("token")

        if (token.isNullOrBlank()) {
            finish()
            return
        }

        val authClient = SnowRealmAuthClient(this)
        authClient.saveTokenDirectly(token)

        scope.launch {
            authClient.fetchMe()
            withContext(Dispatchers.Main) {
                // 回到 LoginActivity 並通知成功
                val loginIntent = Intent(this@OAuthCallbackActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("oauth_success", true)
                }
                startActivity(loginIntent)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

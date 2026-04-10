package com.snowrealm.yukiboard.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

        val authClient = SnowRealmAuthClient.getInstance(this)
        authClient.saveTokenDirectly(token)

        Log.d("OAuthCallback", "Token received: ${token.take(20)}...")
        scope.launch {
            try {
                val me = authClient.fetchMe()
                Log.d("OAuthCallback", "fetchMe result: ${me?.displayName}, role=${me?.role}, tenant=${me?.tenantId}")
                Log.d("OAuthCallback", "isLoggedIn=${authClient.isLoggedIn}, currentUser=${authClient.currentUser}")

                // 登入成功後自動同步 API Key
                try {
                    val syncResult = com.snowrealm.yukiboard.claw.provider.ApiKeySync.downloadKeys(this@OAuthCallbackActivity)
                    Log.d("OAuthCallback", "API Key sync: ${syncResult.message}")
                } catch (e: Exception) {
                    Log.w("OAuthCallback", "API Key sync failed: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    // 回到 LoginActivity 並通知成功
                    val loginIntent = Intent(this@OAuthCallbackActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("oauth_success", true)
                    }
                    startActivity(loginIntent)
                    finish()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("OAuthCallback", "fetchMe failed: ${e.message}")
                withContext(Dispatchers.Main) { finish() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

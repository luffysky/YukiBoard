package com.snowrealm.yukiboard.ui.currency

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient

/**
 * Z幣錢包 — 直接開啟 Insight 錢包頁面（Chrome Custom Tab）。
 */
class ZCoinWalletActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authClient = SnowRealmAuthClient.getInstance(this)
        val baseUrl = BuildConfig.INSIGHT_API_BASE_URL.trimEnd('/')
        val tenantId = authClient.currentUser?.tenantId?.ifBlank { null } ?: "default"

        val url = "$baseUrl/insight-admin/$tenantId/zcoin"
        CustomTabsIntent.Builder().setShowTitle(true).build()
            .launchUrl(this, Uri.parse(url))

        finish()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, ZCoinWalletActivity::class.java))
        }
    }
}

package com.snowrealm.yukiboard.ui.currency

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.currency.NewebpayClient
import com.snowrealm.yukiboard.currency.ZCoinApi
import com.snowrealm.yukiboard.currency.ZCoinManager
import kotlinx.coroutines.*

/**
 * Z幣錢包頁面 — 餘額 + 交易紀錄 + 儲值。
 */
class ZCoinWalletActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var zCoinManager: ZCoinManager
    private lateinit var authClient: SnowRealmAuthClient
    private lateinit var newebpay: NewebpayClient
    private lateinit var balanceText: TextView
    private lateinit var transactionList: LinearLayout
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        zCoinManager = ZCoinManager(this)
        authClient = SnowRealmAuthClient(this)
        newebpay = NewebpayClient()

        setContentView(buildUI())
        refreshBalance()
        loadTransactions()
    }

    private fun buildUI(): View {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(0xFF1A1A2E.toInt())
        }

        // 標題
        root.addView(TextView(this).apply {
            text = "🪙 Z幣錢包"
            textSize = 20f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        // 餘額
        balanceText = TextView(this).apply {
            textSize = 36f; setTextColor(0xFFFFD54F.toInt())
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        root.addView(balanceText)

        // 儲值方案
        root.addView(TextView(this).apply {
            text = "儲值方案"
            textSize = 16f; setTextColor(Color.WHITE)
            setPadding(0, pad, 0, (4 * density).toInt())
        })

        val plans = newebpay.getPlans()
        for (plan in plans) {
            root.addView(Button(this).apply {
                text = "${plan.label} — NT\$${plan.twdAmount} → ${plan.zCoins} Z幣"
                setOnClickListener { startTopUp(plan.twdAmount) }
            })
        }

        // 交易紀錄
        root.addView(TextView(this).apply {
            text = "交易紀錄"
            textSize = 16f; setTextColor(Color.WHITE)
            setPadding(0, pad, 0, (4 * density).toInt())
        })

        transactionList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(transactionList)

        // WebView（隱藏，儲值用）
        webView = WebView(this).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
        }
        root.addView(webView)

        scroll.addView(root)
        return scroll
    }

    private fun refreshBalance() {
        balanceText.text = "${zCoinManager.balance} Z幣"

        if (authClient.isLoggedIn) {
            activityScope.launch {
                val api = ZCoinApi()
                val userId = authClient.currentUserId ?: return@launch
                val token = authClient.getToken() ?: return@launch
                val serverBalance = api.fetchBalance(userId, token)
                if (serverBalance != null) {
                    zCoinManager.syncBalance(serverBalance)
                    balanceText.text = "${serverBalance} Z幣"
                }
            }
        }
    }

    private fun loadTransactions() {
        transactionList.removeAllViews()
        val txs = zCoinManager.getTransactions()
        if (txs.isEmpty()) {
            transactionList.addView(TextView(this).apply {
                text = "暫無交易紀錄"
                setTextColor(0xFF888888.toInt())
            })
            return
        }
        for (tx in txs.take(20)) {
            transactionList.addView(TextView(this).apply {
                val sign = if (tx.amount >= 0) "+" else ""
                text = "$sign${tx.amount} Z幣 — ${tx.description}"
                setTextColor(if (tx.amount >= 0) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt())
                textSize = 13f
                setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
            })
        }
    }

    private fun startTopUp(amount: Int) {
        if (!authClient.isLoggedIn) {
            Toast.makeText(this, "請先登入 SnowRealm 帳號", Toast.LENGTH_SHORT).show()
            return
        }
        if (!newebpay.isConfigured) {
            Toast.makeText(this, "金流設定未完成", Toast.LENGTH_SHORT).show()
            return
        }

        val orderId = "YB${System.currentTimeMillis()}"
        val email = authClient.currentEmail ?: ""
        val params = newebpay.buildPaymentParams(amount, orderId, email) ?: return

        webView.visibility = View.VISIBLE
        val postData = "MerchantID=${params.merchantId}" +
                "&TradeInfo=${params.tradeInfo}" +
                "&TradeSha=${params.tradeSha}" +
                "&Version=${params.version}"
        webView.postUrl(params.postUrl, postData.toByteArray())
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, ZCoinWalletActivity::class.java))
        }
    }
}

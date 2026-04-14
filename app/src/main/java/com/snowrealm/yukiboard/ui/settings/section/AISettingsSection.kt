package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.security.FraudModelManager
import com.snowrealm.yukiboard.ui.settings.AISettingsActivity
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper.dp
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * AI 功能 section（20 個開關 + 防詐模型更新 + AI 設定按鈕）。
 *
 * 從 SettingsActivity section 1（原 lines 181-289）抽出。
 */
class AISettingsSection(
    private val prefs: YukiPreferences,
    private val scope: CoroutineScope,
    private val tr: (String) -> String
) {

    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("AI 功能"), prefs.aiEnabled, { prefs.aiEnabled = it }, children) {
            children += SettingsUiHelper.switchRow(context, tr("AI 補字預測"), tr("智慧預測下一個詞"), prefs.aiPrediction) { prefs.aiPrediction = it }
            children += SettingsUiHelper.switchRow(context, tr("雪凜寫手"), tr("幫你寫、回覆、換說法"), prefs.aiWriter) { prefs.aiWriter = it }
            children += SettingsUiHelper.switchRow(context, tr("即時翻譯"), tr("中英日等多語言翻譯"), prefs.aiTranslate) { prefs.aiTranslate = it }
            children += SettingsUiHelper.switchRow(context, tr("情聖月老"), tr("讓訊息更有魅力"), prefs.aiCupid) { prefs.aiCupid = it }
            children += SettingsUiHelper.switchRow(context, tr("AI 語音 (Whisper)"), tr("OpenAI 語音辨識"), prefs.aiWhisper) { prefs.aiWhisper = it }
            children += SettingsUiHelper.switchRow(context, tr("文法糾錯"), tr("一鍵修正錯字和文法"), prefs.aiGrammarCheck) { prefs.aiGrammarCheck = it }
            children += SettingsUiHelper.switchRow(context, tr("語氣調整"), tr("正式/casual/可愛/專業"), prefs.aiToneAdjust) { prefs.aiToneAdjust = it }
            children += SettingsUiHelper.switchRow(context, tr("摘要精簡"), tr("長文縮成一句話"), prefs.aiSummarize) { prefs.aiSummarize = it }
            children += SettingsUiHelper.switchRow(context, tr("迷因生成"), tr("產生流行語和梗圖文字"), prefs.aiMemeText) { prefs.aiMemeText = it }
            children += SettingsUiHelper.switchRow(context, tr("Emoji 翻譯"), tr("文字與 Emoji 互轉"), prefs.aiEmojiTranslate) { prefs.aiEmojiTranslate = it }
            children += SettingsUiHelper.switchRow(context, tr("快速回覆"), tr("建議整句回覆"), prefs.aiQuickReply) { prefs.aiQuickReply = it }
            children += SettingsUiHelper.switchRow(context, tr("程式碼補全"), tr("開發者模式中 AI 補完"), prefs.aiCodeComplete) { prefs.aiCodeComplete = it }
            children += SettingsUiHelper.switchRow(context, tr("正則產生器"), tr("描述需求產生 Regex"), prefs.aiRegexGen) { prefs.aiRegexGen = it }
            children += SettingsUiHelper.switchRow(context, tr("Commit 訊息"), tr("貼上 diff 產生 commit message"), prefs.aiCommitMessage) { prefs.aiCommitMessage = it }
            children += SettingsUiHelper.switchRow(context, tr("雪凜聊天"), tr("跟雪凜對話，幫你想怎麼說"), prefs.aiYukiChat) { prefs.aiYukiChat = it }
            children += SettingsUiHelper.switchRow(context, tr("心情日記"), tr("被動偵測打字情緒"), prefs.aiMoodDiary) { prefs.aiMoodDiary = it }
            children += SettingsUiHelper.switchRow(context, tr("天氣助理"), tr("天氣資訊與穿衣建議"), prefs.aiWeather) { prefs.aiWeather = it }
            children += SettingsUiHelper.switchRow(context, tr("防詐偵測"), tr("分析對話內容偵測可疑模式"), prefs.aiFraudDetect) { prefs.aiFraudDetect = it }
            children += SettingsUiHelper.switchRow(context, tr("被動防詐掃描"), tr("打字時自動掃描上下文"), prefs.aiFraudPassiveScan) { prefs.aiFraudPassiveScan = it }
            children += SettingsUiHelper.switchRow(context, tr("連結風險掃描"), tr("自動偵測可疑網址"), prefs.aiFraudUrlScan) { prefs.aiFraudUrlScan = it }

            // 防詐模型更新
            children += buildFraudModelUpdate(context)

            // AI 設定按鈕
            children += Button(context).apply {
                text = tr("AI 設定 / Provider")
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                background = GradientDrawable().apply {
                    setColor(0xFF4A90E2.toInt())
                    cornerRadius = dp(context, 8).toFloat()
                }
                setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(context, 8), dp(context, 8), 0, dp(context, 4)) }
                setOnClickListener {
                    context.startActivity(Intent(context, AISettingsActivity::class.java))
                }
            }
        }
    }

    private fun buildFraudModelUpdate(context: Context): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val modelManager = FraudModelManager(context)
        val modelStatusText = TextView(context).apply {
            text = modelManager.getStatusSummary()
            textSize = 11f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 4))
        }
        container.addView(modelStatusText)

        container.addView(Button(context).apply {
            text = tr("防詐模型更新")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(0xFFE25C4A.toInt())
                cornerRadius = dp(context, 8).toFloat()
            }
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(context, 8), dp(context, 4), 0, dp(context, 4)) }
            setOnClickListener {
                isEnabled = false
                text = tr("檢查中...")
                scope.launch {
                    try {
                        modelManager.manualUpdate(
                            supabaseUrl = BuildConfig.YUKIBOARD_SUPABASE_URL,
                            supabaseAnonKey = BuildConfig.YUKIBOARD_SUPABASE_ANON_KEY,
                            appVersionCode = BuildConfig.VERSION_CODE,
                            callback = object : FraudModelManager.DownloadCallback {
                                override fun onProgress(percent: Int) {
                                    scope.launch(Dispatchers.Main) {
                                        text = tr("更新中 $percent%...")
                                    }
                                }
                                override fun onComplete(success: Boolean, message: String) {
                                    scope.launch(Dispatchers.Main) {
                                        isEnabled = true
                                        text = tr("防詐模型更新")
                                        modelStatusText.text = modelManager.getStatusSummary()
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                    catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isEnabled = true
                            text = tr("檢查失敗")
                        }
                    }
                }
            }
        })
        return container
    }
}

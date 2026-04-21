package com.snowrealm.yukiboard.ui.settings

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.snowrealm.yukiboard.app.settings.SettingsScreenCoordinator
import com.snowrealm.yukiboard.theme.ThemeManager

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val RC_MEDIA_PERMISSION = 9003
        private const val REQUEST_IMPORT_DICT = 9010
    }

    private var pendingMediaAction: (() -> Unit)? = null

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRestarting = false
    private lateinit var settingsCoordinator: SettingsScreenCoordinator
    private lateinit var themeManager: ThemeManager
    private lateinit var bgSection: com.snowrealm.yukiboard.ui.settings.section.BackgroundSettingsSection
    private lateinit var authSection: com.snowrealm.yukiboard.ui.settings.section.AuthSettingsSection
    private var syncSection: com.snowrealm.yukiboard.ui.settings.section.SyncSettingsSection? = null
    private lateinit var prefs: YukiPreferences
    private var currentLang = SettingsStrings.Lang.ZH

    /** 多語言翻譯捷徑 */
    private fun tr(zh: String) = SettingsStrings.tr(zh, currentLang)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            onCreateInternal()
        } catch (e: Throwable) {
            val msg = e.stackTraceToString().take(3000)
            android.util.Log.e("SettingsActivity", "CRASH in onCreate", e)
            val errView = TextView(this).apply {
                text = tr("YukiBoard 啟動錯誤：\n\n$msg\n\n請截圖回報")
                textSize = 11f
                setTextColor(0xFFFF5555.toInt())
                setBackgroundColor(0xFF111111.toInt())
                setPadding(32, 64, 32, 32)
                setTextIsSelectable(true)
            }
            setContentView(ScrollView(this).apply { addView(errView) })
        }
    }

    private fun onCreateInternal() {
        settingsCoordinator = SettingsScreenCoordinator(this)
        themeManager = ThemeManager.getInstance(this)
        prefs = YukiPreferences(this)
        currentLang = SettingsStrings.Lang.fromCode(prefs.settingsLanguage)
        wasLoggedIn = com.snowrealm.yukiboard.auth.SnowRealmAuthClient.getInstance(this).isLoggedIn
        settingsCoordinator.refresh()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(0xFF1A2035.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
        }

        // ── 標題列（含語言選單）──
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        titleRow.addView(TextView(this).apply {
            text = tr("YukiBoard 設定")
            textSize = 24f
            setTextColor(0xFFE8F4FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        // 語言下拉選單按鈕
        titleRow.addView(Button(this).apply {
            text = currentLang.displayName
            setTextColor(0xFFE8F4FF.toInt())
            textSize = 12f
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            background = GradientDrawable().apply {
                setColor(0xFF2A3A5C.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            setOnClickListener { btn ->
                val popup = android.widget.PopupMenu(this@SettingsActivity, btn)
                SettingsStrings.Lang.entries.forEachIndexed { i, lang ->
                    popup.menu.add(0, i, i, lang.displayName)
                }
                popup.setOnMenuItemClickListener { item ->
                    val selectedLang = SettingsStrings.Lang.entries[item.itemId]
                    prefs.settingsLanguage = selectedLang.code
                    currentLang = selectedLang
                    // 重建 Activity 以套用新語言
                    isRestarting = true
                    recreate()
                    true
                }
                popup.show()
            }
        })
        root.addView(titleRow)

        // ── 按鈕列（獨立一行，不擠壓標題）──
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(16))
        }
        btnRow.addView(Button(this).apply {
            text = tr("功能說明")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            background = GradientDrawable().apply {
                setColor(0xFF2A3A5C.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, HelpActivity::class.java))
            }
        })
        btnRow.addView(Button(this).apply {
            text = tr("Z幣錢包")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            background = GradientDrawable().apply {
                setColor(0xFFD4A017.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, com.snowrealm.yukiboard.ui.currency.ZCoinWalletActivity::class.java))
            }
        })
        // 登入/登出 — 委託 AuthSettingsSection
        val auth = com.snowrealm.yukiboard.auth.SnowRealmAuthClient.getInstance(this@SettingsActivity)
        authSection = com.snowrealm.yukiboard.ui.settings.section.AuthSettingsSection(auth, activityScope, ::tr)
        authSection.buildViews(btnRow)
        root.addView(btnRow)

        // 1. AI 功能 — 委託 AISettingsSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.AISettingsSection(prefs, activityScope, ::tr).buildCard(this))

        // 2. 雪凜行為 — 委託 YukiBehaviorSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.YukiBehaviorSection(prefs, ::tr).buildCard(this))

        // 3. 感測器 — 委託 SensorSettingsSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.SensorSettingsSection(prefs, ::tr).buildCard(this))

        // 4. 動畫特效 — 委託 AnimationSettingsSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.AnimationSettingsSection(prefs, ::tr).buildCard(this))

        // 5. 遊戲 — 委託 GameSettingsSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.GameSettingsSection(prefs, ::tr).buildCard(this))

        // 5.5 剪貼簿 — 委託 ClipboardSettingsSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.ClipboardSettingsSection(prefs, ::tr).buildCard(this))

        // 6. 輸入行為 — 委託 InputBehaviorSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.InputBehaviorSection(
            prefs, ::tr,
            onExportDict = {
                val dict = com.snowrealm.yukiboard.input.dictionary.UserDictionary(this)
                val data = dict.exportToString()
                if (data.isBlank()) {
                    Toast.makeText(this, tr("詞庫是空的"), Toast.LENGTH_SHORT).show()
                    return@InputBehaviorSection
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "YukiBoard 個人詞庫")
                    putExtra(Intent.EXTRA_TEXT, data)
                }
                startActivity(Intent.createChooser(intent, tr("匯出詞庫")))
            },
            onImportDict = {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "text/plain"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_IMPORT_DICT)
                } catch (_: Exception) {
                    Toast.makeText(this, tr("無法開啟檔案選擇器"), Toast.LENGTH_SHORT).show()
                }
            },
        ).buildCard(this))

        // 6.1 語音辨識 — 委託 SpeechSettingsSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.SpeechSettingsSection(prefs, ::tr, activityScope) {
            settingsCoordinator.getApiKey(com.snowrealm.yukiboard.claw.provider.AIProviderType.OPENAI)
        }.buildCard(this))

        // 功能列 — 委託 ToolbarSettingsSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.ToolbarSettingsSection(prefs, ::tr).buildCard(this))

        // 6.5 TTS 語音輸出 — 委託 TtsSettingsSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.TtsSettingsSection(prefs, ::tr) { testText, keyInput, resetBtn, setBtnText ->
            prefs.googleCloudTtsApiKey = keyInput.text.toString().trim()
            val key = prefs.googleCloudTtsApiKey
            if (key.isBlank()) {
                Toast.makeText(this, "請先填入 Google Cloud API Key", Toast.LENGTH_SHORT).show()
                resetBtn()
                return@TtsSettingsSection
            }
            setBtnText(tr("🔊 呼叫 Google Cloud..."))
            val engine = com.snowrealm.yukiboard.speech.GoogleCloudTtsEngine(key)
            engine.speak(testText, null, prefs.ttsSpeed) {
                runOnUiThread {
                    val err = engine.lastError
                    if (err != null) {
                        Toast.makeText(this, "Google TTS 失敗：$err", Toast.LENGTH_LONG).show()
                        setBtnText("❌ $err")
                    }
                    engine.destroy()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ resetBtn() }, if (err != null) 8000L else 500L)
                }
            }
        }.buildCard(this))

        // 6.6 輸入法選擇 — 委託 InputMethodSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.InputMethodSection(prefs, ::tr).buildCard(this))

        // 6.7 Snippet 管理 — 委託 SnippetSettingsSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.SnippetSettingsSection(::tr).buildCard(this))

        // 7. 智慧輸入 — 委託 SmartInputSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.SmartInputSection(prefs, ::tr).buildCard(this))

        // ═══════════════════════════════════════════════════════
        // Insight 整合 — 委託 InsightSettingsSection
        // ═══════════════════════════════════════════════════════
        root.addView(com.snowrealm.yukiboard.ui.settings.section.InsightSettingsSection(auth, prefs, ::tr).buildCard(this))

        // ═══════════════════════════════════════════════════════
        // 雲端同步 — 委託 SyncSettingsSection
        // ═══════════════════════════════════════════════════════
        val syncManager = com.snowrealm.yukiboard.data.CloudSyncManager.getInstance(this)
        val syncSec = com.snowrealm.yukiboard.ui.settings.section.SyncSettingsSection(auth, syncManager, activityScope, ::tr)
        syncSection = syncSec
        root.addView(syncSec.buildCard(this))

        // ═══════════════════════════════════════════════════════
        // 主題 / 外觀 / 語言 / 背景（獨立卡片）
        // ═══════════════════════════════════════════════════════
        root.addView(com.snowrealm.yukiboard.ui.settings.section.ThemeSettingsSection(themeManager).buildCard(this))
        bgSection = com.snowrealm.yukiboard.ui.settings.section.BackgroundSettingsSection(
            prefs, ::tr, themeManager, activityScope,
            onPickImage = {
                requestMediaPermissionThen("image") {
                    val intent = Intent(Intent.ACTION_PICK).apply {
                        data = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, com.snowrealm.yukiboard.ui.settings.section.BackgroundSettingsSection.REQUEST_PICK_BG_IMAGE)
                }
            },
            onPickVideo = {
                requestMediaPermissionThen("video") {
                    val intent = Intent(Intent.ACTION_PICK).apply {
                        data = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, com.snowrealm.yukiboard.ui.settings.section.BackgroundSettingsSection.REQUEST_PICK_BG_VIDEO)
                }
            }
        )
        root.addView(bgSection.buildCard(this))
        root.addView(com.snowrealm.yukiboard.ui.settings.section.AppearanceSection().buildCard(this))
        root.addView(com.snowrealm.yukiboard.ui.settings.section.LanguageSection().buildCard(this))
        root.addView(com.snowrealm.yukiboard.ui.settings.section.LanguagePackSection(::tr, lifecycleScope).buildCard(this))

        // 法律與隱私 — 委託 LegalSettingsSection
        root.addView(com.snowrealm.yukiboard.ui.settings.section.LegalSettingsSection(::tr, ::showLegalWebView).buildCard(this))

        // ═══════════════════════════════════════════════════════
        // 輸入法測試
        // ═══════════════════════════════════════════════════════
        root.addView(Button(this).apply {
            text = tr("輸入法完整測試")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            background = GradientDrawable().apply {
                setColor(0xFF2E7D32.toInt())
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(4), dp(16), dp(4), dp(8)) }
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, InputTestActivity::class.java))
            }
        })

        scrollView.addView(root)
        setContentView(scrollView)

        // 統一非同步驗證 session（取代 Auth/Sync section 各自獨立的 fetchMe 呼叫）
        activityScope.launch {
            settingsCoordinator.verifySessionAsync()
            val state = settingsCoordinator.uiState.value
            authSection.onSessionVerified(state.isInsightConnected, state.verifiedDisplayName, state.isLoggedIn)
            syncSection?.onSessionVerified(state.isInsightConnected, state.verifiedDisplayName, state.isLoggedIn)
        }
    }


    /** 用內嵌 WebView 顯示 assets/legal/ 下的 HTML 法律文件 */
    private fun showLegalWebView(fileName: String, title: String) {
        val webView = android.webkit.WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setBackgroundColor(0xFF0F1923.toInt())
            loadUrl("file:///android_asset/legal/$fileName")
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(webView)
            .setPositiveButton("關閉") { d, _ -> d.dismiss() }
            .show()
            .apply {
                window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.95).toInt(),
                    (resources.displayMetrics.heightPixels * 0.85).toInt()
                )
            }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun requestMediaPermissionThen(type: String, action: () -> Unit) {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (type == "video") android.Manifest.permission.READ_MEDIA_VIDEO
            else android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingMediaAction = action
            requestPermissions(arrayOf(permission), RC_MEDIA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_MEDIA_PERMISSION) {
            if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingMediaAction?.invoke()
            } else {
                Toast.makeText(this, "需要相冊存取權限", Toast.LENGTH_SHORT).show()
            }
            pendingMediaAction = null
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        // 詞庫匯入
        if (requestCode == REQUEST_IMPORT_DICT) {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
                val dict = com.snowrealm.yukiboard.input.dictionary.UserDictionary(this)
                val count = dict.importFromString(text)
                Toast.makeText(this, "成功匯入 $count 個詞", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "匯入失敗：${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 背景圖片/影片選取 — 委託 BackgroundSettingsSection
        if (requestCode == com.snowrealm.yukiboard.ui.settings.section.BackgroundSettingsSection.REQUEST_PICK_BG_IMAGE ||
            requestCode == com.snowrealm.yukiboard.ui.settings.section.BackgroundSettingsSection.REQUEST_PICK_BG_VIDEO) {
            bgSection.handleBgPickResult(this, requestCode, uri)
            return
        }
    }

    override fun onStart() {
        super.onStart()
        bgSection.onStart(this)
    }

    override fun onStop() {
        super.onStop()
        bgSection.onStop(this)
    }

    override fun onPause() {
        super.onPause()
        bgSection.onPause()
    }

    private var wasLoggedIn: Boolean = false

    override fun onResume() {
        super.onResume()
        bgSection.onResume()
        settingsCoordinator.refresh()

        // 從 LoginActivity 回來時，若登入狀態改變，重建 UI
        val auth = com.snowrealm.yukiboard.auth.SnowRealmAuthClient.getInstance(this)
        val nowLoggedIn = auth.isLoggedIn
        if (nowLoggedIn != wasLoggedIn) {
            wasLoggedIn = nowLoggedIn
            recreate()
        }
    }

    override fun onDestroy() {
        bgSection.onDestroy()
        super.onDestroy()
        activityScope.cancel()
    }
}

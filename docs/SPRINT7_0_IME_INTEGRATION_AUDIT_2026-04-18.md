# Sprint 7-0 — IME 側 Integration Audit（2026-04-18）

回交人：Claude Code B

任務 ID：`S7-0-CCB-IME-INTEGRATION-AUDIT`

依據：`Sprint7-All-Round-Gate.md` Round `S7-0`（audit only）

## 0. 範圍宣告與本輪限制遵守

- **本輪限制**：`audit only` — 不做 implementation、不改 `.kt`、不順手處理 bug、不做 migration plan
- **本文件只產出**：integration source / callsite / mutable owner 真實來源 raw inventory
- **本文件不包含**：migration 策略、round 執行順序建議、refactor 決策（留待 `CodeX A` 指定）
- **範圍**：IME runtime path —
  - `app/src/main/java/com/snowrealm/yukiboard/service/*.kt`（7 檔）
  - `app/src/main/java/com/snowrealm/yukiboard/app/ime/*.kt`（30 檔）
- **非範圍**（Claude Code A 負責）：`ui/settings/*`、`ui/auth/*`、`auth/*`、`claw/provider/*`、`data/*`、`currency/*`、各 provider 實作
- file 路徑皆為倉庫相對路徑；行號以截稿當下為準
- Delta 說明：`S7-PRE` 已做候選範圍盤點，本文件升級為完整 callsite-level audit（加入 owner map、writer/reader map、Dispatchers/scope 標註、prefs 使用密度）

---

## 1. Integration Source 完整 Callsite Map

### 1.A `OkHttpClient.Builder()` / Network Entry

| ID | 位置 | 類型 | Dispatchers | Lifetime | 說明 |
| --- | --- | --- | --- | --- | --- |
| A-1 | `app/ime/ImeImageCommitter.kt:47-50` | IME-owned inline `OkHttpClient.Builder()` | `Dispatchers.IO`（via `scope.launch(Dispatchers.IO)` at line 34） | 每次 `commit()` 呼叫建新 instance，用完即丟 | 貼圖 / GIF 下載；connect 10s / read 15s；cache dir = `context.cacheDir/stickers`；回傳 `content://` URI 經 `FileProvider` 供 `commitContent` 使用 |

**IME 側 inline OkHttp builder 總計：1 個**（確認 `grep -rn "OkHttpClient" service/` 無命中；`app/ime/` 僅 `ImeImageCommitter.kt:47`）。

### 1.B auth token / session 狀態

| ID | 位置 | 類型 | Lifetime | 說明 |
| --- | --- | --- | --- | --- |
| B-1 | `app/ime/ImeSessionCoordinator.kt:23` | `UserSessionRepository(appContext)` fresh 構造 | 每次 `ImeSessionCoordinator(context)` 建構都 new；由 `YukiBoardIMEService.onCreate()` line 236 呼叫一次，service 生命週期內共用 | IME 端唯一的 session 來源 |
| B-2 | `app/ime/ImeSessionCoordinator.kt:135` | `getSessionSnapshot() = sessionRepository.getSnapshot()` | 每次呼叫即查 | 對外暴露的唯一 session read API |

**熱路徑 caller of `getSessionSnapshot()`**：本輪 `grep` 結果，IME 側無任何 caller（`service/*.kt` + `app/ime/*.kt` 全掃 `getSessionSnapshot` 無命中）。即：`ImeSessionCoordinator` 建 repo 後，IME runtime 並未實際用到 session snapshot。這是 audit 觀察點，不在本輪決策。

**Dual-owner 事實**：`UserSessionRepository(appContext)` 亦在 `ui/settings/SettingsScreenCoordinator.kt:24` 建立一個分別的 instance（Claude Code A scope）。IME 端 + Settings 端兩邊都各 new 一次；底層 storage 共用。

**IME 側 auth 觀察**：`service/` + `app/ime/` 全掃 `currentUserFlow` / `authStateFlow` / `observeAuth` / `authToken` / `bearerToken` / `accessToken` / `currentSession` / `signIn` / `signOut` / `verifySession` 均無命中。IME runtime path 完全不接觸 auth token 細節。

### 1.C provider 選擇 / API Key 讀取

| ID | 位置 | 讀取內容 | Dispatchers | Trigger | 說明 |
| --- | --- | --- | --- | --- | --- |
| C-1 | `service/ImeServiceInput.kt:255` | `BuildConfig.OPENAI_API_KEY` | Main（caller `toggleVoiceInput()` 熱路徑） | 使用者按語音按鍵 → speech engine type=1 分支 | 直讀 BuildConfig 的唯一 IME 側位置 |
| C-2 | `service/ImeServiceInput.kt:257-259` | `AIProviderManager.getInstance(ctx).getApiKey(AIProviderType.OPENAI)` | Main（同上） | BuildConfig 為空時 fallback | 唯一 IME 側 `AIProviderManager` 使用點 |
| C-3 | `service/ImeServiceInput.kt:351` | `prefs.googleCloudTtsApiKey` | Main（caller `initTtsEngine()`） | `initTtsEngine()` 被呼叫：(a) `initExternalEngines()` line 309，(b) `toggleVoiceInput()` 觸發重建 | TTS engine type=1 分支 |
| C-4 | `app/ime/ImeFraudModelInit.kt:33-34` | `BuildConfig.YUKIBOARD_SUPABASE_URL` + `BuildConfig.YUKIBOARD_SUPABASE_ANON_KEY` | `Dispatchers.IO`（line 22） | `YukiBoardIMEService.onCreate()` → `initFraudModel()` | IME 側唯一 Supabase URL/key 讀取 |
| C-5 | `app/ime/ImeFraudModelInit.kt:38` | `BuildConfig.VERSION_CODE` | 同上 | 同上 | 傳給 `FraudModelManager.checkAndUpdateIfNeeded` 做版號比對 |

**IME 側 BuildConfig.* 直讀總計：4 點**（C-1 / C-4 x2 / C-5；C-3 為 prefs 非 BuildConfig；C-2 為 manager fallback）。

**非 IME 側但 IME runtime 間接相依**（實作檔不在本輪 scope — 供 owner map 參考，不做處理）：

| BuildConfig key | IME 端讀取 | 非 IME 端讀取 |
| --- | --- | --- |
| `OPENAI_API_KEY` | C-1 | `claw/provider/AIProviderManager.kt`、`ui/settings/section/SpeechSettingsSection.kt` |
| `ANTHROPIC_API_KEY` | 無 | `claw/provider/AIProviderManager.kt` |
| `INSIGHT_API_BASE_URL` | 無 | `auth/SnowRealmAuthClient.kt`、`data/InsightApiClient.kt`、`data/CloudSyncManager.kt`、`claw/provider/SnowRealmProvider.kt`、`input/langpack/LanguagePackManager.kt`、`sticker/InsightStickerApi.kt`、`ui/auth/LoginActivity.kt`、`ui/currency/ZCoinWalletActivity.kt` |
| `PLATFORM_API_KEY` | 無 | `data/InsightApiClient.kt`、`claw/provider/SnowRealmProvider.kt`、`sticker/InsightStickerApi.kt` |
| `GIPHY_API_KEY` | 無（`GiphyApiClient` 建構於 `ImeServiceLifecycle.kt:292`，key 讀取在實作檔） | `gif/GiphyApiClient.kt` |
| `OPENWEATHER_API_KEY` | 無 | `weather/WeatherManager.kt` |
| `YUKIBOARD_SUPABASE_URL/ANON_KEY` | C-4 | `data/SupabaseClient.kt`、`ui/settings/section/AISettingsSection.kt` |
| `INVITE_HMAC_SECRET` | 無 | `auth/InviteCodeManager.kt` |
| `NEWEBPAY_*` | 無 | `currency/NewebpayClient.kt` |

### 1.D Sync / External Gateway 入口

| ID | 位置 | 類型 | Dispatchers | 說明 |
| --- | --- | --- | --- | --- |
| D-1 | `service/ImeServiceLifecycle.kt:285-309 initExternalEngines()` | 外部 engine 集中初始化 extension | Main（從 `onCreate()` line 310 呼叫） | 見 §1.D.1 完整清單 |
| D-2 | `service/YukiBoardIMEService.kt:244` | `CloudSyncManager.getInstance(applicationContext)` | Main（`onCreate()`） | 唯一 IME 側 `CloudSyncManager` 取得點；注入到 `ImeSessionHooks` 建構子 |
| D-3 | `app/ime/ImeSessionHooks.kt:44` | `cloudSyncManager.onSessionEnd()` | `scope.launch {}`（caller 傳入的 `serviceScope`，Main 為 default） | 在 `onFinishInputView` 呼叫 1 次；唯一 `CloudSyncManager` 方法呼叫點 |
| D-4 | `app/ime/ImeFraudModelInit.kt:36` | `FraudModelManager(context)` 實例化 | `Dispatchers.IO` | 每次 `init()` 觸發時 new instance，短命 |
| D-5 | `app/ime/ImeFraudModelInit.kt:37-39` | `FraudModelManager.checkAndUpdateIfNeeded(supaUrl, supaKey, VERSION_CODE)` | `Dispatchers.IO` | 直接跨 Supabase 網路（URL/key 字串 API） |
| D-6 | `app/ime/ImeFraudModelInit.kt:52-54` | `fraudAnalyzer.openDataSync.needsSync()` + `.sync()` | `Dispatchers.IO` | 同步政府公開防詐資料 |
| D-7 | `app/ime/ImeFraudModelInit.kt:57-58` | `openDataSync.uploadToSupabase(supaUrl, supaKey)` + `.downloadFromSupabase(supaUrl, supaKey)` | `Dispatchers.IO` | 雙向 Supabase 同步 |

#### 1.D.1 `initExternalEngines()` 完整實例清單（`ImeServiceLifecycle.kt:286-309`）

| Line | 實例化 | 存放欄位 | 屬性 |
| --- | --- | --- | --- |
| 286 | `StickerRepository(applicationContext)` | `service.stickerRepository` | nullable var；生命週期綁 service；`onDestroy` 呼叫 `destroy()` |
| 287 | `StickerPackDownloadManager(applicationContext)` | `service.stickerDownloadManager` | 同上 |
| 289 | `ClawClient(applicationContext)` | `service.clawClient`（public var, internal set） | 同上；無 explicit destroy |
| 290 | `VoicePostProcessor(clawClient)` | `service.voicePostProcessor` | 依賴 289 存在；`onDestroy` 呼叫 `destroy()` |
| 292 | `GiphyApiClient()` | `service.giphyClient` | 無 destroy；無 explicit lifecycle management |
| 293 | `TranslateEngine()` | `service.translateEngine` | `onDestroy` 呼叫 `close()` |
| 295 | `YukiCommentEngine()` | `service.yukiCommentEngine` | 無 destroy |
| 296 | `EmotionalCooldownManager()` | `service.emotionalCooldown` | 無 destroy |
| 297 | `ToneDetector()` | `service.toneDetector` | 無 destroy |
| 298 | `SecretMessageManager()` | `service.secretMessageManager` | 無 destroy |
| 300 | `FloatingPanelManager(applicationContext)` | `service.floatingPanelManager` | `onDestroy` 呼叫 `dismissAll()` |
| 301 | `WeatherManager(applicationContext)` | local `weatherMgr`（僅傳給 302） | — |
| 302 | `WeatherFloatingPanel(applicationContext, weatherMgr)` | `service.weatherFloatingPanel` | `onDestroy` 呼叫 `dismiss()` |
| 303 | `ClawResultFloatingPanel(applicationContext)` | `service.clawResultFloatingPanel` | `onDestroy` 呼叫 `dismiss()` |
| 304-305 | `floatingPanelManager?.registerPanel(...)` | — | 將 302 / 303 註冊到 300 |
| 307 | `RuleBasedPredictor()` | `service.nextActionPredictor` | 無 destroy |
| 309 | `initTtsEngine()` 呼叫 | 間接設定 `service.ttsEngine` | 見 C-3 |

**觀察**：單一 function 內 14+ 實例化，lifetime 全綁 service 本體 nullable field；5 個有 `onDestroy` cleanup，其餘 9 個無明確 teardown。C-2 `AIProviderManager.getInstance(ctx)` 與 D-2 `CloudSyncManager.getInstance(ctx)` 是 getInstance 模式（app-level singleton），其餘都是 service-level instance。

### 1.E Prefs / Singleton / Mutable Field 真實來源

#### 1.E.1 `YukiPreferences` 實例化

| ID | 位置 | 類型 |
| --- | --- | --- |
| E-1 | `service/YukiBoardIMEService.kt:246` | `prefs = YukiPreferences(applicationContext)` — IME runtime 唯一實例 |

**`prefs.XXX` 使用密度**：

| 檔案 | 次數 |
| --- | --- |
| `service/YukiBoardIMEService.kt` | 20 |
| `service/ImeServiceLifecycle.kt` | 48 |
| `service/ImeServiceInput.kt` | 18 |
| `service/ImeServiceMisc.kt` | 21 |
| `service/ImeServicePanels.kt` | 31 |
| `service/ImeServiceCallbacks.kt` | 9 |
| `app/ime/ImeFloatingModeHelper.kt` | 7 |
| `app/ime/ImeTransparencyOverlay.kt` | 6 |
| **IME 側總計** | **160** |

#### 1.E.2 `.getInstance()` singleton 讀取

| Singleton | IME 側 callsite | 次數 |
| --- | --- | --- |
| `ThemeManager.getInstance(ctx)` | `service/ImeServiceLifecycle.kt:145`、`service/ImeServicePanels.kt:142, 214`、`service/ImeServiceMisc.kt:273, 308`、`app/ime/ImeSessionCoordinator.kt:24` | 6 |
| `CloudSyncManager.getInstance(ctx)` | `service/YukiBoardIMEService.kt:244` | 1 |
| `AIProviderManager.getInstance(ctx)` | `service/ImeServiceInput.kt:258` | 1 |

**觀察**：`ThemeManager.getInstance` 散落 6 點，其中 `ImeSessionCoordinator.kt:24` 已將 `themeManager` 收成 coordinator private field（但其餘 5 點仍各自呼叫）。

#### 1.E.3 Service 本體 mutable field — 外部 integration owner map

（列出有 `destroy()` / network / 外部狀態 / IPC 依賴的 field；排除純 UI view）

| Field | Declare | Writer | Reader | Destroy | 備註 |
| --- | --- | --- | --- | --- | --- |
| `clawClient: ClawClient?` | `YukiBoardIMEService.kt:154` | `ImeServiceLifecycle.kt:289` (setter) | `ImeServiceInput.kt:368`（`requestAICandidates`）、`ImeServiceLifecycle.kt:290`（用於 `VoicePostProcessor`）、`ImeServicePanels.kt:313`（`ImeClawPanelBinder.bind`） | 無 explicit destroy | public var, internal set（唯一公開可變 field） |
| `stickerRepository` | `:147` | `ImeServiceLifecycle.kt:286` | `ImeServicePanels.kt:152`（傳入 `ImePanelFactory`） | `onDestroy` line 627 | — |
| `stickerDownloadManager` | `:148` | `ImeServiceLifecycle.kt:287` | `ImeServicePanels.kt:153` | `onDestroy` line 628 | — |
| `giphyClient` | `:158` | `ImeServiceLifecycle.kt:292` | `ImeServicePanels.kt:151`、`ImePanelFactory.kt:97` | 無 destroy | — |
| `translateEngine` | `:159` | `ImeServiceLifecycle.kt:293` | `ImeServicePanels.kt:150` | `onDestroy` line 631 `close()` | — |
| `speechEngine: SpeechInputEngine?` | `:162` | `ImeServiceInput.kt:244`（`toggleVoiceInput` 熱路徑分支） | `YukiBoardIMEService.kt:377, 670`、`ImeServiceInput.kt:215, 241, 243, 271, 296, 300, 305, 340`、`ImeServiceMisc.kt:525` | `onDestroy` line 621 | 熱路徑 writer（使用者按語音按鍵時重建） |
| `ttsEngine` | `:166` | `ImeServiceInput.kt:349`（`initTtsEngine`） | `ImeServiceLifecycle.kt:242`、`ImeServiceInput.kt:273, 348`、`ImeServiceMisc.kt:526` | `onDestroy` line 624 | 每次 `toggleVoiceInput` 可能重建 |
| `voicePostProcessor` | `:205` | `ImeServiceLifecycle.kt:290` | `ImeServiceInput.kt:319` | `onDestroy` line 623 | 依賴 `clawClient` |
| `yukiCommentEngine` / `emotionalCooldown` / `toneDetector` / `secretMessageManager` | `:173-177` | `ImeServiceLifecycle.kt:295-298` | `YukiBoardIMEService.kt` + `ImeServiceInput.kt`（fraud/tone/cooldown/secret 邏輯多處） | 無 destroy | 全 nullable |
| `floatingPanelManager` | `:180` | `ImeServiceLifecycle.kt:300` | `ImeServiceLifecycle.kt:304-305`（register）、`onDestroy` line 633 | `dismissAll()` | — |
| `weatherFloatingPanel` | `:181` | `ImeServiceLifecycle.kt:302` | `onDestroy` line 634 | `dismiss()` | — |
| `clawResultFloatingPanel` | `:182` | `ImeServiceLifecycle.kt:303` | `YukiBoardIMEService.kt:316`（set onInsertToInput）、`ImeServicePanels.kt:337`、`onDestroy` line 635 | `dismiss()` | — |
| `rimeDictionary` | `:185` | `YukiBoardIMEService.kt:255`（`onCreate`） | `onDestroy` line 632 | `close()` | prefs guard `prefs.rimeDictEnabled` |
| `nextActionPredictor` | `:188` | `ImeServiceLifecycle.kt:307` | `YukiBoardIMEService.kt:884`（`handleKeyPress` 熱路徑） | 無 destroy | 熱路徑 reader |
| `btKeyboard` | `:200` | `service` toggle functions（not in quick-grep result） | `ImeServicePanels.kt:205, 208, 210` | `onDestroy` line 625 | 遠端鍵盤 |
| `wifiServer` | `:201` | 同上 | `ImeServicePanels.kt:206-209, 211` | `onDestroy` line 626 | 遠端鍵盤 |
| `handwritingEngine` | `:203` | `ImeServicePanels.kt:408`（lazy init on first HANDWRITING open） | `ImeServicePanels.kt:413`、`onDestroy` line 637 | `close()` | lazy panel-open-trigger 初始化 |
| `keySoundEngine` | `:204` | `YukiBoardIMEService.kt:393`（`onCreateInputView`） | `ImeServicePanels.kt:402`（MUSIC panel） | `onDestroy` line 622 | — |
| `fraudAnalyzer: FraudAIAnalyzer` | `:132` by lazy | lazy init | `ImeServiceInput.kt:89, 136-137, 190-191`、`YukiBoardIMEService.kt:547`（傳給 `ImeFraudModelInit.init`） | 無 destroy | `by lazy { FraudAIAnalyzer(this) }` |
| `prefs: YukiPreferences` | `:124` lateinit | `onCreate` line 246 | 160 點（見 §1.E.1） | 無（底層 SharedPreferences） | — |

**觀察**：
- 20+ 個外部 integration field 分散於 service 本體，各自有不同 writer / reader / destroy 模式
- 有 5 個 field 完全無 destroy 配對（`giphyClient` / `yukiCommentEngine` / `emotionalCooldown` / `toneDetector` / `secretMessageManager` / `nextActionPredictor`）
- 熱路徑 writer：僅 `speechEngine`（使用者按語音鍵觸發重建）與 `ttsEngine`（prefs 變更觸發重建）
- 大部分 field 由 `initExternalEngines()` 一次性寫入後不再變動

#### 1.E.4 service-side 狀態變數（非外部 integration，列出供 S7-4 scope 判斷）

| Field | 用途 |
| --- | --- |
| `lastFraudScanText / lastFraudReplyText` | fraud scan dedupe（`ImeServiceInput.kt:131, 152`） |
| `sentenceBuffer` | 整句學習 buffer（`applyInputResult()` / `handleEnter()`） |
| `manualModeOverride` | App 自動模式切換 override |
| `voiceFinalHandled` | voice 最終結果去重 |
| `swipeInputPending / swipeInputCandidates / swipeAccumulated` | 滑動輸入暫存 |
| `stickerSyncDone` | sticker repo 首次同步 flag |
| `consecutiveDeleteCount / lastDeletedTextLength / lastTypedChar / sameCharCount` | 吐槽 engine event tracker |
| `aiCandidateJob / uiStateCollectJob / composingCandidatesCollectJob` | coroutine job handles |
| `gameMenuView / gameKeyboardView / gameLoop / currentGame` | 遊戲模式 state |

---

## 2. IME 熱路徑上的 integration 接觸點（來自 `handleKeyPress` 展開）

為協助 `CodeX A` 判定哪些 integration 可安全在 S7-1 / S7-2 / S7-3 / S7-4 動、哪些會影響熱路徑，特別標註 `handleKeyPress` → callback 的 integration 接觸：

| 熱路徑呼叫 | 觸發 integration | 位置 |
| --- | --- | --- |
| `applyInputResult()` → `scanForFraud()` | `fraudAnalyzer.analyzeByRules` + `scanUrls` | `ImeServiceInput.kt:126-146` |
| `applyInputResult()` → `scanReplyForFraud()` | `fraudAnalyzer`（rule-only 路徑） | `ImeServiceInput.kt:148-182` |
| `applyInputResult()` → `emotionalCooldown?.checkText` | `EmotionalCooldownManager` | `YukiBoardIMEService.kt:945` |
| `applyInputResult()` → `toneDetector?.analyze` | `ToneDetector` | `YukiBoardIMEService.kt:971` |
| `applyInputResult()` → `yukiCommentEngine?.onKeystroke` | `YukiCommentEngine` | `YukiBoardIMEService.kt:996` |
| `applyInputResult()` → `inputRouter.smartInput.getEmotionStickerTags` | smart input engine | `YukiBoardIMEService.kt:985` |
| `handleKeyPress()` else branch → `requestAICandidates()` | `clawClient.executeSkill("ai_prediction")` | `ImeServiceInput.kt:366-410`（coroutine, `Dispatchers.IO` line 388） |
| `handleKeyPress()` predictor branch → `nextActionPredictor.predict` | `RuleBasedPredictor` | `YukiBoardIMEService.kt:884-896` |

**熱路徑風險觀察**：只有 `requestAICandidates()` 走網路（透過 `clawClient`），其餘 integration 全部為 in-memory rule / regex / in-process engine。任何 S7-1 收斂若影響 `clawClient.executeSkill`，會直接影響熱路徑的 AI 候選字；但 `speechEngine` 重建路徑（`toggleVoiceInput` 熱路徑分支）不走 `clawClient`，獨立。

---

## 3. IME 側 Integration 與外部 package 相依圖

```
  service/*                            app/ime/*
  ├─ YukiBoardIMEService               ├─ ImeImageCommitter ──► okhttp3 (A-1)
  │   ├─ onCreate()                    ├─ ImeSessionCoordinator ──► UserSessionRepository (B-1)
  │   │   ├─ imeCoordinator      ──►   │                       └─► ThemeManager.getInstance (E-1.E.2)
  │   │   ├─ CloudSyncManager.   ──►   ├─ ImeSessionHooks ──► CloudSyncManager.onSessionEnd (D-3)
  │   │   │   getInstance (D-2)        ├─ ImeFraudModelInit ──► BuildConfig.YUKIBOARD_SUPABASE_* (C-4)
  │   │   ├─ prefs = YukiPrefs   ──►   │                    └─► FraudModelManager / openDataSync (D-4..D-7)
  │   │   ├─ initExternalEngines ──►   ├─ ImePanelFactory  ──► gifClient.search / fetchTrending (panel)
  │   │   │   └─ 14 instances          ├─ ImeClawPanelBinder ──► clawClient (passthrough from service field)
  │   │   └─ initFraudModel      ──►   ├─ ImeHandwritingPanelBinder ──► handwritingEngine (passthrough)
  │   └─ handleKeyPress()              ├─ ImeUserDictPanelBinder ──► UserDictionary (new per open)
  │       ├─ applyInputResult   ──►    ├─ ImeRemotePanelBinder ──► btKeyboard / wifiServer (passthrough)
  │       ├─ requestAICandidates──►    ├─ ImeThemePanelBinder  ──► ThemeManager (listener)
  │       └─ 熱路徑 integrations       ├─ ImeMusicPanelBinder  ──► keySoundEngine (passthrough)
  ├─ ImeServiceInput                   └─ ImeFloatingModeHelper / ImeTransparencyOverlay ──► prefs
  │   ├─ toggleVoiceInput ──► BuildConfig.OPENAI_API_KEY (C-1) / AIProviderManager (C-2)
  │   ├─ initTtsEngine ──► prefs.googleCloudTtsApiKey (C-3)
  │   └─ requestAICandidates ──► clawClient.executeSkill
  ├─ ImeServicePanels                  Sprint 6-X binders 全部僅做 UI wiring，
  │   └─ createHighDependencyPanelOrNull   不直接建構外部 client
  │       └─ 各 panel 透過 clawClient / gifClient / stickerRepository /
  │           giphyClient / translateEngine passthrough（service field）
  └─ ImeServiceMisc / Callbacks / Lifecycle：prefs / engine destruction / toast / misc
```

---

## 4. Ownership 判定（每個 integration 的 single-source-of-truth 候選）

依現況列出「誰實際擁有這個 integration 的 state / instance」，供 `CodeX A` 判定收斂方向：

| Integration | 現況 owner | IME 側使用 |
| --- | --- | --- |
| OkHttp client | 分散：IME 側 inline 1 個 + 10+ 個實作檔各自 inline | IME 側 1 個 |
| `UserSessionRepository` | IME + Settings 各 new 1 個（dual-owner） | `ImeSessionCoordinator` 持有後實際 0 caller |
| `OPENAI_API_KEY` | BuildConfig 直讀 + `AIProviderManager` 兩來源 | IME 側：BuildConfig → AIProviderManager fallback |
| `GoogleCloud TTS api key` | `prefs.googleCloudTtsApiKey` single source | IME 側：`initTtsEngine()` |
| Supabase URL/KEY | BuildConfig 直讀（`SupabaseClient` 有 wrapper，但 IME 側 bypass 直讀） | IME 側：`ImeFraudModelInit` bypass wrapper |
| `CloudSyncManager` | app-level singleton via `getInstance(ctx)` | IME 側：僅 1 取得點 + 1 呼叫點（乾淨） |
| `AIProviderManager` | app-level singleton | IME 側：僅 1 點 |
| `ThemeManager` | app-level singleton via `getInstance(ctx)` | IME 側：6 點直接取 instance（非集中取用） |
| `ClawClient` | service-field instance（唯一） | IME 側 3 reader + passthrough 到 CLAW panel |
| `StickerRepository` / `StickerPackDownloadManager` | service-field instance | IME 側 1 reader passthrough |
| `GiphyApiClient` / `TranslateEngine` / `YukiCommentEngine` / ... | service-field instance | 各自 passthrough |
| `handwritingEngine` | service-field instance（lazy init on HANDWRITING open） | 1 writer + 1 reader |
| `speechEngine` / `ttsEngine` | service-field instance（hot-path rebuild） | 多 reader，1 writer |
| `fraudAnalyzer` | `by lazy` service field | 多 reader |
| `prefs: YukiPreferences` | service-field lateinit（`onCreate`） | 160 讀取點 |

---

## 5. 檔案 scope 分類（供 S7-1 / S7-2 / S7-3 / S7-4 file scope 定界參考）

| 類別 | 檔案 |
| --- | --- |
| 網路入口 | `app/ime/ImeImageCommitter.kt` |
| session / auth | `app/ime/ImeSessionCoordinator.kt`、`app/ime/ImeSessionHooks.kt` |
| provider / api key | `service/ImeServiceInput.kt`、`app/ime/ImeFraudModelInit.kt` |
| sync gateway | `service/ImeServiceLifecycle.kt (initExternalEngines)`、`service/YukiBoardIMEService.kt:244`、`app/ime/ImeSessionHooks.kt`、`app/ime/ImeFraudModelInit.kt` |
| prefs / singleton / mutable | `service/YukiBoardIMEService.kt`（field declarations + `onCreate`）、`service/ImeServiceLifecycle.kt`、`service/ImeServicePanels.kt`、`service/ImeServiceMisc.kt`、`service/ImeServiceInput.kt`、`service/ImeServiceCallbacks.kt`、`app/ime/ImeSessionCoordinator.kt`、`app/ime/ImeFloatingModeHelper.kt`、`app/ime/ImeTransparencyOverlay.kt` |

---

## 6. 與 Claude Code A 的邊界（本輪重申）

| 類別 | Claude Code B（IME）處理 | Claude Code A（非 IME）處理 |
| --- | --- | --- |
| `OkHttpClient` inline | `ImeImageCommitter.kt:47` 的 1 個 | 其餘 `speech/` / `claw/` / `data/` / `gif/` / `weather/` / `sticker/` / `security/` 內部實作 |
| `UserSessionRepository` 構造 | `ImeSessionCoordinator.kt:23` | `SettingsScreenCoordinator.kt:24` + `auth/SnowRealmAuthClient` 內部 |
| BuildConfig 讀取 | `ImeServiceInput.kt:255` + `ImeFraudModelInit.kt:33-38` | 其他 25+ 檔案 |
| `AIProviderManager` 使用 | `ImeServiceInput.kt:258`（fallback 唯一） | `claw/provider/*` 內部 + `ui/settings/section/*` |
| `CloudSyncManager` | `YukiBoardIMEService.kt:244` + `ImeSessionHooks.kt:44` | `CloudSyncManager` 實作、sync state machine、sync backlog |
| `ThemeManager.getInstance` 多點 | IME 側 6 點（supplier → reader） | `ui/settings` / `ui/panel` / `ui/auth` 等內部使用 |
| invite code | IME 僅 `ImeClawPanelBinder` onLockTapped（啟動 Activity intent，無邏輯） | `auth/InviteCodeManager` + `ui/auth/*` |
| prefs 直讀 | IME 側 160 點 | settings 側 prefs 封裝 |

---

## 7. S7-1 / S7-2 / S7-3 / S7-4 IME 側候選觸及點（僅候選，待 CodeX A 指定）

以下僅為 integration entry raw mapping，不代表執行順序、不代表實施範圍、不代表 migration 方案。

| S7 round | IME 側候選觸及 entry ID | file scope 建議範圍 |
| --- | --- | --- |
| `S7-1 AppHttpClient / Network Entry` | A-1 | `app/ime/ImeImageCommitter.kt`（1 檔） |
| `S7-2 Session / Auth / Provider` | B-1、B-2、C-1、C-2、C-3 | `app/ime/ImeSessionCoordinator.kt`、`service/ImeServiceInput.kt` |
| `S7-3 Sync / External Gateway` | C-4、C-5、D-1..D-7 | `app/ime/ImeFraudModelInit.kt`、`service/ImeServiceLifecycle.kt (initExternalEngines)`、`service/YukiBoardIMEService.kt (line 244)`、`app/ime/ImeSessionHooks.kt` |
| `S7-4 Prefs / Singleton / Mutable Field` | E-1（prefs）、E-1.E.2（3 singletons）、E-1.E.3（20+ service-field） | 全 service/ + 部分 app/ime/ |

**提醒**：S7-0 限制 §本輪限制 第 5 條明確禁止把「`AISettingsActivity` section 化、`InviteCodeManager` singleton 化、`CloudSyncManager` gateway 拆分」當成本輪實作 — 本文件完全不觸及這 3 個題目的實作方案，只做 audit。

---

## 8. 自評與 CodeX A 裁決請求

### 8.1 本輪限制逐項自評

| 限制 | 狀態 |
| --- | --- |
| 1. 只允許 audit / inventory / migration map / owner map / file scope 整理 | ✅ §1–§7 |
| 2. 不做 implementation | ✅ 本輪 0 行 `.kt` 修改 |
| 3. 不改 `.kt` | ✅ 同上 |
| 4. 不得順手處理 bug | ✅ 發現 `giphyClient` / `yukiCommentEngine` 等無 destroy pair 屬觀察，未修改 |
| 5. 不把 AISettingsActivity section 化 / InviteCodeManager singleton 化 / CloudSyncManager gateway 拆分當本輪實作 | ✅ §7 明確避談這 3 題實作 |

### 8.2 與 S7-PRE 的 delta

| 項 | S7-PRE（候選範圍） | S7-0（完整 audit） |
| --- | --- | --- |
| Network entry | 列 1 個 | §1.A + Dispatchers / lifetime 標註 |
| Session/auth | 列 2 個 + dual-owner | §1.B + 補 `getSessionSnapshot` 實際 caller 數（0）|
| Provider/api key | 列 3 點 | §1.C + Dispatchers / trigger / fallback path |
| Sync gateway | 列 4 entry | §1.D + 新增 D-4 / D-5 / D-6 / D-7 拆 `ImeFraudModelInit` 成細項；§1.D.1 補 `initExternalEngines` 14 實例明細（含 destroy pair 狀態） |
| Prefs/singleton/mutable | 列 prefs 1 + singleton 3 + service field 概略 | §1.E 完整 writer/reader/destroy matrix；§1.E.1 prefs 160 次使用密度；§1.E.3 20+ mutable field 清單 |
| 熱路徑風險 | 未明確標註 | §2 熱路徑 integration 接觸點清單 |
| 相依圖 | 無 | §3 ASCII 依賴圖 |
| Ownership 表 | 無 | §4 single-source-of-truth 候選 |
| file scope | 無 | §5 按 4 類 round 分類 |

### 8.3 請 CodeX A 裁決

1. **IME 側 audit 是否完整支撐 S7-0 結案**
   - 若是 → `CodeX A` 可判定 `S7-0` 可進入審核路徑
   - 若補充 → 請指明缺項（例如：補 `initExternalEngines` 每個實例的生命週期時序圖？補 service field read/write call-graph？補 prefs 160 點的 category 分類？）

2. **與 CodeX B `S7-0-CXB-INTEGRATION-AUDIT` 的 migration map / owner map 對齊方式**
   - 本文件 §4（Ownership）與 §7（候選觸及點）可作 IME 側原料
   - 是否需要額外對齊格式、或併入 CodeX B 的總 migration map？由 `CodeX A` 指定

3. **不自排後續 round 順序 / scope**
   - §7 的候選 entry ID 分組不代表順序
   - `S7-1/S7-2/S7-3/S7-4` 的啟動與 file scope 由 `CodeX A` 裁決

以上。等 `CodeX A` 指示。

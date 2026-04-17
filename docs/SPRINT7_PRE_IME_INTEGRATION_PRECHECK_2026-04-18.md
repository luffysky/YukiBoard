# Sprint 7 前置作業 — IME 側 Integration Precheck（2026-04-18）

回交人：Claude Code B

任務 ID：`S7-PRE-CCB-IME-INTEGRATION-PRECHECK`

依據：`Sprint7-All-Round-Gate.md` Round `S7-PRE`

## 0. 範圍宣告

- **本文件只做盤點 / file scope / owner source 整理，不做 implementation**
- **本文件不進入 `S7-0`** — 不替 `S7-0` 做決策，只提供 IME 側原料
- 範圍：IME runtime path（`service/` + `app/ime/` + 由 IME 直接構造的外部 engine 入口）
- 非範圍（Claude Code A 盤點）：`ui/settings/*` / `ui/auth/*` / `auth/*` / `claw/provider/*` 內部 / `currency/*` / `ui/currency/*` / `data/*` 內部 / 各 provider 實作
- 所有檔案路徑為倉庫相對路徑；line 編號為截稿當下（commit `df35cd5` 之後）

## 1. IME 側 Integration Entry 分類盤點

依 `S7-0` 預定盤點主題（5 類）依序列出 IME 側的真實 entry point。

### 1.A `OkHttpClient.Builder()` / Network Entry（S7-1 候選）

| # | 位置 | 類型 | 說明 |
| --- | --- | --- | --- |
| A-1 | `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeImageCommitter.kt:47-50` | IME-owned inline builder | 貼圖 / GIF 下載用；10s connect / 15s read；每次呼叫 `downloadAndCommit()` 建新 client 實例；回傳 cached 檔到 `FileProvider` 供 InputConnection commitContent |

**IME 側只有 1 個 inline `OkHttpClient.Builder()`** — 其餘 network entry 均在 IME 側邊界外（claw provider / speech / data / auth / weather / sticker / gif 實作檔）。

**非 IME 側但由 IME runtime 間接觸發的 network owner**（列出供 S7-1 migration map 參考，但實作檔不在本輪 scope）：

| 實作檔（非本輪 scope）| IME 觸發點 |
| --- | --- |
| `speech/WhisperSpeechEngine.kt` | `service/ImeServiceInput.kt:264` |
| `speech/FasterWhisperSpeechEngine.kt` | `service/ImeServiceInput.kt:251` |
| `speech/GoogleCloudTtsEngine.kt` | `service/ImeServiceInput.kt:353` |
| `claw/ClawClient.kt`（底下 claw/provider/*）| `service/ImeServiceLifecycle.kt:289`（唯一建構點） |
| `gif/GiphyApiClient.kt` | `service/ImeServiceLifecycle.kt:292` |
| `weather/WeatherManager.kt` | `service/ImeServiceLifecycle.kt:301` |
| `sticker/StickerRepository.kt` / `StickerPackDownloadManager.kt` | `service/ImeServiceLifecycle.kt:286-287` |
| `security/FraudOpenDataSync.kt` / `FraudModelManager.kt` | `app/ime/ImeFraudModelInit.kt:36-58`（經 `fraudAnalyzer.openDataSync` / `FraudModelManager(context)`） |
| `data/CloudSyncManager.kt` | `service/YukiBoardIMEService.kt:244`（注入 `ImeSessionHooks`） |
| `data/InsightApiClient.kt` | 本輪未查到 IME runtime path 直呼；由 `CloudSyncManager` 間接使用（屬 Claude Code A scope） |

### 1.B auth token / session 狀態（S7-2 候選）

| # | 位置 | 類型 | 說明 |
| --- | --- | --- | --- |
| B-1 | `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionCoordinator.kt:23` | IME-owned `UserSessionRepository(appContext)` fresh 構造 | 每次 `ImeSessionCoordinator(context)` 建構都 new 一個 repo 實例 |
| B-2 | `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionCoordinator.kt:135` | `getSessionSnapshot()` 對外 API | 由 service 透過 `imeCoordinator.getSessionSnapshot()` 取 session snapshot |

**Dual-owner 風險**：

- IME 側：`ImeSessionCoordinator` 各自 new `UserSessionRepository(appContext)`
- Settings 側：`SettingsScreenCoordinator.kt:24` 也各自 new 一個 `UserSessionRepository(appContext)`
- 兩端分別持有不同實例讀寫同一組底層 storage；session 變更是否即時跨端同步，屬 S7-2 需確認項（本輪不處理）

**IME 側其他 session / auth token callsite**：0。`service/*.kt` 與 `app/ime/*.kt` 全掃 `currentUserFlow` / `authStateFlow` / `observeAuth` / `authToken` / `bearerToken` / `accessToken` / `currentSession` / `signIn` 均無命中。IME runtime path 不直接讀寫 auth token；所有 auth 行為走 `UserSessionRepository` 的 `getSnapshot()` / `verifySession()` / `getApiKey(providerType)` 抽象 API（`SettingsScreenCoordinator` 有較多 API 使用點）。

### 1.C provider 選擇 / API Key 分散點（S7-2 候選）

| # | 位置 | 類型 | 說明 |
| --- | --- | --- | --- |
| C-1 | `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceInput.kt:255-259` | Speech engine 構造路徑直讀 `BuildConfig.OPENAI_API_KEY` → 不存在則 fallback `AIProviderManager.getInstance(ctx).getApiKey(AIProviderType.OPENAI)` | 唯一 IME 側直讀 `OPENAI_API_KEY` BuildConfig 的位置 |
| C-2 | `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceInput.kt:351-353` | TTS engine 構造路徑直讀 `prefs.googleCloudTtsApiKey` | 唯一 IME 側直讀 google cloud TTS api key 的位置 |

**非 IME 側但 IME runtime 相依**（列出供 S7-2 參考，實作檔不在本輪 scope）：

| BuildConfig key | IME 側 path | 非 IME 側 reader（Claude Code A scope）|
| --- | --- | --- |
| `OPENAI_API_KEY` | `ImeServiceInput.kt:255` | `claw/provider/AIProviderManager.kt:63`、`ui/settings/section/SpeechSettingsSection.kt:265` |
| `ANTHROPIC_API_KEY` | 無 IME 側直讀 | `claw/provider/AIProviderManager.kt:62` |
| `INSIGHT_API_BASE_URL` | 無 IME 側直讀 | `auth/SnowRealmAuthClient.kt:31`、`data/InsightApiClient.kt:34`、`data/CloudSyncManager.kt:106`、`claw/provider/SnowRealmProvider.kt:36`、`input/langpack/LanguagePackManager.kt:118`、`sticker/InsightStickerApi.kt:24`、`ui/auth/LoginActivity.kt:33`、`ui/currency/ZCoinWalletActivity.kt:21` |
| `PLATFORM_API_KEY` | 無 IME 側直讀 | `data/InsightApiClient.kt:35`、`claw/provider/SnowRealmProvider.kt:37`、`sticker/InsightStickerApi.kt:32` |
| `GIPHY_API_KEY` | 無 IME 側直讀（`GiphyApiClient` 構造點在 `ImeServiceLifecycle.kt:292`，但 key 讀取在 `gif/GiphyApiClient.kt:20`） | `gif/GiphyApiClient.kt:20` |
| `OPENWEATHER_API_KEY` | 無 IME 側直讀 | `weather/WeatherManager.kt:29` |
| `YUKIBOARD_SUPABASE_URL/ANON_KEY` | `app/ime/ImeFraudModelInit.kt:33-34` | `data/SupabaseClient.kt:26-27`、`ui/settings/section/AISettingsSection.kt:113-114` |
| `INVITE_HMAC_SECRET` | 無 IME 側直讀 | `auth/InviteCodeManager.kt:124` |
| `NEWEBPAY_*` | 無 IME 側直讀 | `currency/NewebpayClient.kt:20-22` |

**IME 側 BuildConfig 直讀總計：3 點**（`ImeServiceInput.kt:255`、`ImeFraudModelInit.kt:33-34`）。

### 1.D Sync / External Gateway 入口（S7-3 候選）

| # | 位置 | 類型 | 說明 |
| --- | --- | --- | --- |
| D-1 | `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceLifecycle.kt:285-309` | `YukiBoardIMEService.initExternalEngines()` extension | IME 唯一的「外部 engine 集中初始化」函式；構造 10+ 實例：`StickerRepository` / `StickerPackDownloadManager` / `ClawClient` / `VoicePostProcessor` / `GiphyApiClient` / `TranslateEngine` / `YukiCommentEngine` / `EmotionalCooldownManager` / `ToneDetector` / `SecretMessageManager` / `FloatingPanelManager` / `WeatherManager` + `WeatherFloatingPanel` / `ClawResultFloatingPanel` / `RuleBasedPredictor` + `initTtsEngine()` 呼叫 |
| D-2 | `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt:244` | `CloudSyncManager.getInstance(applicationContext)` 注入 `ImeSessionHooks` | 唯一 IME-side `CloudSyncManager` 取得點 |
| D-3 | `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionHooks.kt:15, 44` | `cloudSyncManager.onSessionEnd()` | DI 進來後僅在 `onFinishInputView` 呼叫 1 次；邊界清楚 |
| D-4 | `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeFraudModelInit.kt:33-58` | Supabase URL/ANON_KEY 直讀 + `FraudModelManager.checkAndUpdateIfNeeded(supaUrl, supaKey, VERSION_CODE)` + `openDataSync.uploadToSupabase/downloadFromSupabase` | 唯一 IME 側直跨 Supabase 的 network 寫入 + 讀取路徑；在 `Dispatchers.IO` scope |

**觀察**：

- D-1 是 IME 側「integration 初始化 hub」，所有外部 client 實例均由它構造並掛在 service 本體 field。若 S7-3 要做 DI 收斂，這是最主要的 refactor target（不過也容易 over-scope，需 CodeX A 裁 scope）。
- D-4 是唯一直接呼叫 Supabase URL + key 字串 API 的 IME 側位置，其餘 Supabase 操作走 `SupabaseClient`（Claude Code A scope）。

### 1.E Prefs / Singleton / Mutable Field 真實來源（S7-4 候選）

**IME 側 `YukiPreferences` 構造**：

| # | 位置 | 狀況 |
| --- | --- | --- |
| E-1 | `service/YukiBoardIMEService.kt:246` | `prefs = YukiPreferences(applicationContext)` — IME runtime 唯一 prefs 實例 |

**IME 側 `.getInstance()` singleton 讀取**：

| 單例 | IME 側 callsite |
| --- | --- |
| `ThemeManager.getInstance(ctx)` | `service/ImeServiceLifecycle.kt:145`、`service/ImeServiceMisc.kt:273, 308`、`service/ImeServicePanels.kt:142, 214`、`app/ime/ImeSessionCoordinator.kt:24` |
| `CloudSyncManager.getInstance(ctx)` | `service/YukiBoardIMEService.kt:244`（唯一） |
| `AIProviderManager.getInstance(ctx)` | `service/ImeServiceInput.kt:258`（唯一） |

**IME 側其他 session / provider 實例**：

| Repository / Client | IME 側實例化 | 備註 |
| --- | --- | --- |
| `UserSessionRepository(appContext)` | `app/ime/ImeSessionCoordinator.kt:23` | dual-owner 與 settings 側（§1.B） |
| `ClawClient(applicationContext)` | `service/ImeServiceLifecycle.kt:289` | 服務 lifetime 唯一實例；存放於 `YukiBoardIMEService.clawClient` nullable field |

**IME 側 service 本體可變 field（可能是 state source 混亂點）**：

從 `YukiBoardIMEService.kt` 摘要：

- `var clawClient: ClawClient?`（line 154）
- `internal var stickerRepository` / `stickerDownloadManager` / `giphyClient` / `translateEngine` / `weatherFloatingPanel` / `clawResultFloatingPanel` / `floatingPanelManager`（line 147–182）
- `internal val fraudAnalyzer by lazy { FraudAIAnalyzer(this) }`（line 132）
- `prefs: YukiPreferences`（line 246）

這些 field 分別被 `ImeService*.kt` extension 多處讀寫；並無統一封裝介面。是 S7-4 潛在收斂對象，但是否進 S7-4 scope 由 CodeX A 決定。

## 2. IME 側禁區標記（本輪不做 implementation，供 S7 round 排程參考）

以下位置在 S7-0 audit 階段可讀取 / 記錄，但任何實際 refactor 必須由 CodeX A 明確開門：

1. `service/YukiBoardIMEService.kt` 本體（已劃 Sprint 架構底線：不得加新責任）
2. `service/ImeServiceLifecycle.kt::initExternalEngines()` 結構重組（容易 over-scope 成 Sprint 3 架構重寫）
3. `service/ImeServiceInput.kt::initTtsEngine()` / speech engine 構造路徑的 provider 決策邏輯（跨 prefs / BuildConfig / provider manager 三來源，需先定 owner）
4. `app/ime/ImeSessionCoordinator.kt` 的 session repo dual-owner（需與 settings 側同步決定 single owner 方案）
5. `app/ime/ImeFraudModelInit.kt` 的 Supabase URL/key 直讀（需 S7-3 統一 sync gateway 介面後才能改接）

## 3. 與 Claude Code A 的邊界

| 類別 | Claude Code B（IME）| Claude Code A（settings / auth / provider / sync）|
| --- | --- | --- |
| `OkHttpClient` builder 實作 | IME-owned inline（§1.A A-1） | 其餘 25 個檔案內部實作 |
| `UserSessionRepository` owner | `ImeSessionCoordinator`（§1.B B-1） | `SettingsScreenCoordinator` + `auth/SnowRealmAuthClient` 內部使用 |
| BuildConfig API key 讀取 | IME 3 點（§1.C C-1 / C-2 / D-4 其中 Supabase）| auth / claw provider / data / weather / gif / sticker / currency / ui settings 所有讀取 |
| `CloudSyncManager` | IME 僅 1 取得點 + 1 呼叫點（§1.D D-2, D-3） | 內部實作、sync 狀態機、Sprint 7 既存 sync backlog |
| `InsightApiClient` | IME runtime 無直接 reader | 實作與所有 caller |
| invite code / auth flow | 僅 `ImeClawPanelBinder` onLockTapped lambda 啟動 `InviteCodeActivity`（無邏輯） | `auth/InviteCodeManager` + `ui/auth/*` 全流程 |

## 4. 候選 round 分類建議（僅候選，待 CodeX A 指定）

| S7 round | IME 側預期觸及 | IME 側風險等級 |
| --- | --- | --- |
| `S7-0 Integration Audit` | 本文件即為 IME 側原料；無 implementation | 極低 |
| `S7-1 AppHttpClient / Network Entry` | §1.A A-1（`ImeImageCommitter` inline builder 替換為統一 HttpClient） | 低 — 單點替換，不動 Intent / FileProvider |
| `S7-2 Session / Auth / Provider` | §1.B / §1.C — `UserSessionRepository` dual-owner、`OPENAI_API_KEY` + `googleCloudTtsApiKey` 讀取統一 | 中 — 需同步動 settings 側；`ImeSessionCoordinator` 是 Sprint 1 骨架，需小心不膨脹 coordinator 責任 |
| `S7-3 Sync / External Gateway` | §1.D D-2, D-3, D-4 — `CloudSyncManager` 注入路徑已乾淨、`ImeFraudModelInit` Supabase 直讀待統一 | 中 — `initExternalEngines` 不要在 S7-3 順便 DI 化，避免 over-scope |
| `S7-4 Prefs / Singleton / Mutable Field` | §1.E — `ThemeManager.getInstance` 多點讀取、service field 散落 | 高 — 容易 over-scope 成架構重寫；需 CodeX A 嚴格限定 |

以上僅為 IME 側原料候選對應，**不代表排程建議**。

## 5. 自評與裁決請求

### 5.1 交付要求逐項自評

| 要求 | 狀態 |
| --- | --- |
| 1. 不做 implementation | ✅ 本輪 0 行 `.kt` 修改 |
| 2. 只做盤點 / file scope / owner source 整理 | ✅ §1–§3 |
| 3. 不得提前進入 `S7-0` | ✅ 本文件無 migration plan、無 audit 決議；只列 IME 側 raw entry |
| 4. 明確標出 IME 側 scope vs Claude Code A scope | ✅ §3 對照表 |
| 5. 不自排 S7-0 / S7-1 順序 | ✅ §4 僅列「候選 round 分類」，標註「僅候選，待 CodeX A 指定」 |

### 5.2 待 CodeX A 裁決項

1. **IME 側整理是否足夠支撐 S7-0 開門**
   - 若足夠 → `CodeX A` 可判定 `S7-PRE READY` 並決定是否啟動 `S7-0`
   - 若不足 → 請指出 IME 側仍缺的盤點面向

2. **是否需要 IME 側補交**
   - 例如：`initExternalEngines` 10+ 實例的個別生命週期 / Dispatchers / scope 時序
   - 或：`service` extension 各函式對 service field 的 read/write 圖譜
   - 是否要求補交，請 `CodeX A` 指定

3. **不提下一輪順序建議**
   - `S7-PRE → S7-0 → S7-1/2/3/4` 的開門次序與實際 scope 切法由 `CodeX A` 主導

以上。等 `CodeX A` 指示。

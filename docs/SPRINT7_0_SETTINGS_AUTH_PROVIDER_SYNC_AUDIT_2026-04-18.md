# Sprint 7-0 Integration Audit — settings / auth / provider / sync 側

- **任務 ID**：`S7-0-CCA-SETTINGS-AUTH-PROVIDER-SYNC-AUDIT`
- **日期**：2026-04-18
- **owner**：Claude Code A
- **限制**（依 `Sprint7-All-Round-Gate.md` `S7-0` 本輪限制）：
  1. 只允許 audit / inventory / migration map / owner map / file scope 整理
  2. 不做 implementation
  3. 不改 `.kt`
  4. 不得順手處理 bug；bugfix 留給 `Sprint 8`
  5. 不得把 `AISettingsActivity` Section 化、`InviteCodeManager` singleton 化、`CloudSyncManager` gateway 拆分當作本門實作
- **與前輪關係**：S7-PRE 是輕量盤點；本文件是 **每個 integration source / callsite / mutable owner 真實來源** 的精確 file:line 表格，供 CodeX B 在 `S7-0-CXB-INTEGRATION-AUDIT` 直接吃進 migration map

---

## A. 整體 Owner Map（settings / auth / provider / sync 側）

> 「Owner」= 該狀態的**單一寫入入口**。不是 owner 的人**不應該**直接寫，但目前可能存在違反。
> 「Reader」= 目前直接讀取此狀態的所有 callsite（含 owner 自己除外）。

### A.1 Auth / Session 狀態 Owner Map

| 狀態項 | 真實 Owner | 儲存載體 | 直接 Writer 數 | 直接 Reader（含 settings 側） | 違反 SSOT？ |
|--------|-----------|---------|--------------|----------------------------|-----------|
| JWT token | `SnowRealmAuthClient` | EncryptedSharedPreferences `yukiboard_auth` key=`jwt_token` | 4 處（`saveTokenDirectly`, `postAuth`, `saveSession`, `logout`） | InsightApiClient, CloudSyncManager, ApiKeySync, SnowRealmProvider | 否（皆透過 `getToken()`） |
| userId | `SnowRealmAuthClient` | 同上 key=`user_id` | 同上 | UserSessionRepository, CloudSyncManager(`resolveUserId`), ApiKeySync | 否（透過 `currentUserId`） |
| email / displayName / role / plan / tenantId / avatarUrl | `SnowRealmAuthClient` | 同上 | `fetchMe / postAuth / saveSession` | UserSessionRepository, AuthSettingsSection (via `auth.currentDisplayName`), SyncSettingsSection, InsightSettingsSection, AISettingsActivity | 否（皆透過 currentXxx 屬性） |
| token expiry | `SnowRealmAuthClient` | 同上 key=`token_expiry` | 4 處 | `isTokenExpired()` 內部 | 否 |
| `isLoggedIn` 邏輯 | `SnowRealmAuthClient.isLoggedIn` | 推導自 token 存在 | — | LoginActivity / OAuthCallbackActivity / SettingsActivity / AISettingsActivity / SyncSettingsSection / InsightSettingsSection / AuthSettingsSection / ZCoinWalletActivity / CloudSyncManager / ApiKeySync / ClawClient | 否 |
| `currentUser` 物件 | `SnowRealmAuthClient.currentUser` | 同上整合 | — | 多處（與上述同） | 否 |
| `Session snapshot` 抽象 | `UserSessionRepository.getSnapshot()` | 包覆 auth + provider | — | `SettingsScreenCoordinator.buildState()` | **半收斂**（其他 settings 讀取點未走 Repository） |
| `verifySession()` async fetchMe | `UserSessionRepository.verifySession()` | wraps `authClient.fetchMe()` | — | `SettingsScreenCoordinator.verifySessionAsync()` | **半收斂**（CloudSyncManager.resolveUserId 仍直接 fetchMe） |
| Supabase JWT（exchanged） | `CloudSyncManager.cachedSupabaseToken`（私有） | 記憶體 cache | `getSupabaseToken()` 一處 | 僅 CloudSyncManager 內部 | 否（但 ApiKeySync 直接用 Insight JWT 作為 Supabase Authorization，路徑不同） |

### A.2 Invite Code / Token Budget 狀態 Owner Map

| 狀態項 | 真實 Owner | 儲存載體 | Writer | Reader | 違反 SSOT？ |
|--------|-----------|---------|--------|--------|-----------|
| 邀請碼啟用 | `InviteCodeManager` | EncryptedSharedPreferences `yukiboard_invite` | `activateCode / revokeCode` | ClawClient, SnowRealmProvider, AISettingsActivity, WeatherFloatingPanel | **是 — 多實例**：每處 `new InviteCodeManager(context)`，4 個獨立物件共用同一 prefs |
| daily token 用量 | `InviteCodeManager` | 同上 key=`daily_token_used` | `consumeTokens / resetDailyUsageIfNeeded` | 同上 | 同上（多實例競態風險：`@Synchronized` 只保護同一實例） |
| token budget（AI 計算） | `TokenBudgetManager` | SharedPreferences `yukiboard_token_budget` | (TokenBudgetManager 自身) | ClawClient | 否 |

### A.3 AI Provider / API Key 狀態 Owner Map

| 狀態項 | 真實 Owner | 儲存載體 | Writer | Reader | 違反 SSOT？ |
|--------|-----------|---------|--------|--------|-----------|
| selected provider | `AIProviderManager` | EncryptedSharedPreferences `yukiboard_ai_secure` key=`selected_provider` | `setSelectedProvider` | UserSessionRepository, ClawClient(getCurrentProvider), AISettingsActivity, ApiKeySync | 否 |
| API key per type | `AIProviderManager` | 同上 key=`api_key_${type}` | `saveApiKey` | UserSessionRepository, ApiKeySync, AISettingsActivity, AISettingsSection (`hasValidConfig` 透過 mgr 判斷) | 否（皆透過 mgr 入口） |
| selected model per type | `AIProviderManager` | 同上 key=`selected_model_${type}` | `setSelectedModel` | UserSessionRepository, AISettingsActivity, ApiKeySync | 否 |
| provider cache | `AIProviderManager.providerCache`（記憶體 mutable map） | 記憶體 | `getProvider`, `saveApiKey`(invalidate) | `getCurrentProvider` | 半破：`setSelectedModel` **未** invalidate cache（model 由 `getSelectedModel` 每次讀 prefs，OK；但 cache 內舊 provider 仍持舊 apiKey 字串，已透過 `saveApiKey` invalidate 處理） |
| BuildConfig key 一次性 import | `AIProviderManager.importBuildConfigKeysIfNeeded()` | 同上 key=`buildconfig_keys_imported` | init 時一次 | — | 否，但 `ImeServiceInput`+`SpeechSettingsSection` **繞過 mgr** 直接讀 `BuildConfig.OPENAI_API_KEY` |

### A.4 Cloud Sync 狀態 Owner Map

| 狀態項 | 真實 Owner | 儲存載體 | Writer | Reader | 違反 SSOT？ |
|--------|-----------|---------|--------|--------|-----------|
| sync enabled flag | `CloudSyncManager` | SharedPreferences `cloud_sync_meta` key=`cloud_sync_enabled` | `setSyncEnabled` | SyncSettingsSection, SettingsScreenCoordinator, IMEService(onFinishInputView 邏輯) | 否 |
| last sync time | `CloudSyncManager` | 同上 key=`cloud_sync_last_time` | `uploadAll / downloadAll / onSessionEnd` | SyncSettingsSection 顯示 | 否 |
| commitsSinceLastSync | `CloudSyncManager`（記憶體） | 記憶體 | `onSessionEnd` 累加 | — | 否（但無持久化，重啟歸零） |
| 7 個學習子系統的 prefs | **應該是** 各 sub-system（AdaptiveDictionary, PersonalDictionary, PhrasePredictor, SentencePredictor, StyleProfiler, TypingHabitTracker, CorrectionLearnManager） | 各自 SharedPreferences | 各 sub-system | **CloudSyncManager 直讀（`getSharedPreferences` 14 處）** | **是 — 嚴重違反**：CloudSyncManager 跨層讀寫他人 prefs，破壞封裝 |
| YukiPreferences 38 個欄位 | `YukiPreferences` | SharedPreferences `yukiboard_prefs` | YukiPreferences 自身 setter | **CloudSyncManager 直接讀寫 38 個欄位**（exportPrefsToJson + importPrefsFromJson） | **是 — 中度違反**：透過 mgr 屬性讀寫但耦合 schema 過重 |
| Theme prefs 9 個欄位 | `YukiPreferences`（背景/流光部分） | 同上 | YukiPreferences | **CloudSyncManager 直接讀寫**（exportThemeToJson + importThemeFromJson） | 同上 |

### A.5 Insight 整合 Gateway Owner Map

| 整合對象 | 預期 Owner | 實際呼叫者 | 違反？ |
|---------|-----------|-----------|-------|
| Insight Engine HTTP | `InsightApiClient`（Sprint 1 後成立的統一 gateway） | InsightSocialClient, InsightSurveyClient, InsightThemeSync ✅ | 部分違反 — 下列繞過 |
| Insight `/api/auth/*` | 應走 `InsightApiClient` | **`SnowRealmAuthClient` 自己持有 OkHttp + URL**（login/register/sendCode/oauth/me/refresh/refresh） | **是**（auth 是 special case，但仍重複 OkHttp + URL 直讀） |
| Insight `/api/auth/supabase-token` | 應走 InsightApiClient | **`CloudSyncManager.getSupabaseToken()` 自己持有 OkHttp + URL** | **是** |
| Insight Claw / chat proxy | 應走 InsightApiClient | **`SnowRealmProvider` 自己持有 OkHttp + URL（含 streaming）** | **是** |
| Insight `/api/yukiboard/themes` | 走 InsightApiClient | InsightThemeSync ✅ | 否 |
| Insight `/api/yukiboard/social/share` | 走 InsightApiClient | InsightSocialClient ✅ | 否 |
| Insight `/api/yukiboard/survey/*` | 走 InsightApiClient | InsightSurveyClient ✅ | 否 |
| Insight `/api/yukiboard/sticker/*` | 走 InsightApiClient | **`InsightStickerApi` 自己持有 OkHttp + URL** | **是** |
| Insight langpack 拉取 | 走 InsightApiClient | **`LanguagePackManager` 自己持有 OkHttp + URL** | **是** |
| Insight ZCoin endpoints | 走 InsightApiClient | **`ZCoinWalletActivity` 自己持有 OkHttp + URL** | **是** |
| Insight OAuth WebView 回呼 | （特殊，無法走 InsightApiClient） | LoginActivity / OAuthCallbackActivity 自建 URL | 不適用（WebView） |
| Supabase REST | `SupabaseClient` | CloudSyncManager, ApiKeySync, ZCoinApi, FraudRuleSync, FraudReportManager | 否 |
| Supabase Auth Token Exchange | `CloudSyncManager.getSupabaseToken`（私有） | CloudSyncManager 自己 | 部分違反 — `ApiKeySync` 不走 exchange，直接用 Insight JWT |

---

## B. Precise Callsite Map（file:line）

### B.1 `OkHttpClient.Builder()` 直建 callsite（settings/auth/provider/sync 側 10 處）

| # | File | Line | timeout 配置 | Owner 類別 |
|---|------|------|------------|-----------|
| 1 | `auth/SnowRealmAuthClient.kt` | 62 | 10s/15s | SnowRealmAuthClient |
| 2 | `data/InsightApiClient.kt` | 39 | 15s/30s/15s | InsightApiClient（單一 gateway 候選） |
| 3 | `data/SupabaseClient.kt` | 29 | 10s/15s | SupabaseClient |
| 4 | `data/CloudSyncManager.kt` | 85 | 10s/10s | CloudSyncManager（為 supabase-token exchange 用） |
| 5 | `claw/provider/OpenAIProvider.kt` | 36 | 30s/60s/30s | OpenAIProvider |
| 6 | `claw/provider/AnthropicProvider.kt` | 31 | 30s/60s/30s | AnthropicProvider |
| 7 | `claw/provider/GeminiProvider.kt` | 37 | 30s/60s/30s | GeminiProvider |
| 8 | `claw/provider/GroqProvider.kt` | 37 | 15s/30s/15s | GroqProvider |
| 9 | `claw/provider/SnowRealmProvider.kt` | 42 | 30s/90s/30s | SnowRealmProvider |
| 10 | `claw/provider/GeminiNanoProvider.kt` | (待確認) | — | 在地推論不該出現 → **Sprint 8 bug 候選** |

> S7-1 收斂目標：建立 `AppHttpClient`（或 `HttpClientProvider`）成為單一 owner，提供 timeout profile（short / default / streaming-long），10 處改走該 owner。

### B.2 `getInstance(context)` callsite（settings/auth/provider/sync 側 25+ 處）

#### B.2.1 `SnowRealmAuthClient.getInstance` — 13 處

| # | File | Line | 用途 |
|---|------|------|------|
| 1 | `app/session/UserSessionRepository.kt` | 17 | Repository 內部讀取 owner（OK） |
| 2 | `claw/ClawClient.kt` | 14 | ClawClient 持有 |
| 3 | `claw/provider/SnowRealmProvider.kt` | 39 | Provider 持有 |
| 4 | `claw/provider/ApiKeySync.kt` | 38, 94 | uploadKeys / downloadKeys 各一次 |
| 5 | `data/InsightApiClient.kt` | 36 | 為了 token + refresh |
| 6 | `data/CloudSyncManager.kt` | 57 | 持有 |
| 7 | `ui/auth/LoginActivity.kt` | 40 | Activity 持有（合理） |
| 8 | `ui/auth/OAuthCallbackActivity.kt` | 40 | Activity 持有（合理） |
| 9 | `ui/currency/ZCoinWalletActivity.kt` | 20 | Activity 持有（合理） |
| 10 | `ui/settings/AISettingsActivity.kt` | 362 | inline lambda 內 reach into |
| 11 | `ui/settings/SettingsActivity.kt` | 64 | `wasLoggedIn` 比對用 |
| 12 | `ui/settings/SettingsActivity.kt` | 163 | onCreateInternal AuthSection 建構 |
| 13 | `ui/settings/SettingsActivity.kt` | 442 | activity 後段 |

> S7-2 收斂候選：第 4, 5, 6 處改走 `UserSessionRepository`；第 1, 2, 3, 7, 8, 9 視 Activity 性質保留；第 10, 11, 12, 13 在 SettingsActivity 內可改走 `SettingsScreenCoordinator` 已經初始化的 sessionRepository。

#### B.2.2 `AIProviderManager.getInstance` — 6 處

| # | File | Line | 用途 |
|---|------|------|------|
| 1 | `app/session/UserSessionRepository.kt` | 18 | Repository 內部 |
| 2 | `app/settings/SettingsScreenCoordinator.kt` | 25 | Coordinator 內部 |
| 3 | `claw/ClawClient.kt` | 12 | 持有 |
| 4 | `claw/provider/ApiKeySync.kt` | 46, 122 | up/download 各一次 |
| 5 | `ui/settings/AISettingsActivity.kt` | 37 | Activity 直持 |

#### B.2.3 `CloudSyncManager.getInstance` — 4 處

| # | File | Line | 用途 |
|---|------|------|------|
| 1 | `app/settings/SettingsScreenCoordinator.kt` | 26 | Coordinator |
| 2 | `service/YukiBoardIMEService.kt` | 244 | IME 持有（onSessionEnd 觸發點） |
| 3 | `ui/settings/SettingsActivity.kt` | 266 | SyncSection 建構時 |

#### B.2.4 `InsightApiClient.getInstance` — 3 處

| # | File | Line | 用途 |
|---|------|------|------|
| 1 | `data/InsightSocialClient.kt` | 16 | 持有 |
| 2 | `data/InsightSurveyClient.kt` | 18 | 持有 |
| 3 | `data/InsightThemeSync.kt` | 18 | 持有 |

#### B.2.5 `SupabaseClient.getInstance()` — 5 處

| # | File | Line | 用途 |
|---|------|------|------|
| 1 | `data/CloudSyncManager.kt` | 56 | 持有 |
| 2 | `claw/provider/ApiKeySync.kt` | 43, 99 | up/download |
| 3 | `currency/ZCoinApi.kt` | 15 | Z 幣 |
| 4 | `security/FraudRuleSync.kt` | 28 | 防詐規則 |
| 5 | `security/FraudReportManager.kt` | 29 | 防詐回報 |

#### B.2.6 `InviteCodeManager(context)` 直 new — 5 處（**非 singleton，多實例風險**）

| # | File | Line | 用途 |
|---|------|------|------|
| 1 | `claw/ClawClient.kt` | 13 | 持有 |
| 2 | `claw/provider/SnowRealmProvider.kt` | 38 | 持有 |
| 3 | `weather/WeatherFloatingPanel.kt` | 36 | 持有 |
| 4 | `ui/auth/InviteCodeActivity.kt` | 33 | Activity 持有 |
| 5 | `ui/settings/AISettingsActivity.kt` | 36 | Activity 持有 |

> Sprint 7 不允許實作 singleton 化（依本輪限制 §5）。但 audit 必須記錄此風險，作為 Sprint 7 後續 round 設計時考量、或 Sprint 8 處理。

### B.3 `BuildConfig.*` 直讀 callsite

#### B.3.1 `INSIGHT_API_BASE_URL` — 8 處

| # | File | Line | 應改走 |
|---|------|------|-------|
| 1 | `auth/SnowRealmAuthClient.kt` | 31 | 暫保留（auth special） |
| 2 | `data/InsightApiClient.kt` | 34 | 保留（owner） |
| 3 | `data/CloudSyncManager.kt` | 106 | InsightApiClient |
| 4 | `claw/provider/SnowRealmProvider.kt` | 36 | InsightApiClient（streaming 變體） |
| 5 | `sticker/InsightStickerApi.kt` | 24 | InsightApiClient |
| 6 | `input/langpack/LanguagePackManager.kt` | 118 | InsightApiClient |
| 7 | `ui/auth/LoginActivity.kt` | 33 | 保留（WebView OAuth 入口需直建 URL） |
| 8 | `ui/currency/ZCoinWalletActivity.kt` | 21 | InsightApiClient |

#### B.3.2 `PLATFORM_API_KEY` — 3 處

| # | File | Line | 備註 |
|---|------|------|------|
| 1 | `data/InsightApiClient.kt` | 35 | owner（gateway header 統一注入） |
| 2 | `claw/provider/SnowRealmProvider.kt` | 37 | 應改透過 InsightApiClient |
| 3 | `sticker/InsightStickerApi.kt` | 32 | 應改透過 InsightApiClient |

#### B.3.3 `OPENAI_API_KEY` 直讀（繞過 AIProviderManager）— 3 處

| # | File | Line | 備註 |
|---|------|------|------|
| 1 | `claw/provider/AIProviderManager.kt` | 63 | owner（一次性 import） |
| 2 | `service/ImeServiceInput.kt` | 255 | **繞過 mgr** → S7-2 候選 |
| 3 | `ui/settings/section/SpeechSettingsSection.kt` | 265 | **繞過 mgr** → S7-2 候選 |

#### B.3.4 `ANTHROPIC_API_KEY` — 1 處

| # | File | Line |
|---|------|------|
| 1 | `claw/provider/AIProviderManager.kt` | 62 | owner |

#### B.3.5 `YUKIBOARD_SUPABASE_URL` / `YUKIBOARD_SUPABASE_ANON_KEY` — 3 處

| # | File | Line | 備註 |
|---|------|------|------|
| 1 | `data/SupabaseClient.kt` | 26-27 | owner |
| 2 | `app/ime/ImeFraudModelInit.kt` | 33-34 | 為 FraudModelManager 初始化（IME 側 — Claude Code B 範圍） |
| 3 | `ui/settings/section/AISettingsSection.kt` | 113-114 | **AISettingsSection 直讀傳給 FraudModelManager** → S7-3/S7-4 候選 |

### B.4 SharedPreferences 直接 `getSharedPreferences(...)` callsite（settings/auth/provider/sync 側）

| # | File | Line | Prefs 名稱 | 是否合理 owner？ |
|---|------|------|----------|----------------|
| 1 | `auth/SnowRealmAuthClient.kt` | 47, 58 | `yukiboard_auth` / `yukiboard_auth_fallback` | 是（owner，Encrypted 失敗 fallback） |
| 2 | `auth/InviteCodeManager.kt` | 33 | `yukiboard_invite_fallback` | 是（同上） |
| 3 | `claw/provider/AIProviderManager.kt` | 46 | `yukiboard_ai_fallback` | 是 |
| 4 | `claw/TokenBudgetManager.kt` | 14 | `yukiboard_token_budget` | 是 |
| 5 | `currency/ZCoinManager.kt` | 32 | `yukiboard_zcoin` | 是 |
| 6 | `data/CloudSyncManager.kt` | 60 | `cloud_sync_meta` | 是（自己的 meta） |
| 7 | `data/CloudSyncManager.kt` | 515, 526 | `yukiboard_adaptive_dict` | **否 — 跨 owner**（owner 為 `AdaptiveDictionary`） |
| 8 | `data/CloudSyncManager.kt` | 558, 569 | `yukiboard_personal_dict` | **否 — 跨 owner**（owner 為 `PersonalDictionary`） |
| 9 | `data/CloudSyncManager.kt` | 604, 624 | `yukiboard_phrase_predict` | **否 — 跨 owner** |
| 10 | `data/CloudSyncManager.kt` | 649, 667 | `yukiboard_sentence_predict` | **否 — 跨 owner** |
| 11 | `data/CloudSyncManager.kt` | 691, 705 | `yukiboard_style` | **否 — 跨 owner** |
| 12 | `data/CloudSyncManager.kt` | 727, 747 | `yukiboard_habits` | **否 — 跨 owner** |
| 13 | `data/CloudSyncManager.kt` | 773, 784 | `yukiboard_correction_learn` | **否 — 跨 owner** |
| 14 | `data/InsightSurveyClient.kt` | 19 | `yukiboard_survey` | 是（自己的） |
| 15 | `ui/settings/YukiPreferences.kt` | 15 | `yukiboard_prefs` | 是（owner） |

> S7-4 收斂目標：第 7–13 共 14 callsite 必須改為各 sub-system 提供 `exportSnapshot()` / `importSnapshot()` API；CloudSyncManager 不再直讀。

### B.5 `EncryptedSharedPreferences` 建立 callsite（4 處 — 各 owner 各一套 try/catch fallback）

| # | File | Line | Prefs |
|---|------|------|-------|
| 1 | `auth/SnowRealmAuthClient.kt` | 38–60 | `yukiboard_auth` |
| 2 | `auth/InviteCodeManager.kt` | 24–34 | `yukiboard_invite` |
| 3 | `claw/provider/AIProviderManager.kt` | 37–47 | `yukiboard_ai_secure` |
| 4 | `currency/ZCoinManager.kt` | (待確認) | `yukiboard_zcoin` |

> 重複的 fallback try/catch pattern（若 keystore 損壞 → 普通 prefs）共 4 套。Sprint 7-4 可考慮抽 `SecurePrefsFactory`，但**不在本輪實作**。

---

## C. 重要 Wiring 細節（給 CodeX B migration map 用）

### C.1 Sprint 1 已建立但未充分利用

- `UserSessionRepository`（`app/session/UserSessionRepository.kt`）
  - 目前**只**讀（getSnapshot, verifySession, getCurrentUser, getSelectedProvider, getApiKey）
  - 沒有任何 write API
  - 沒有持有 `InviteCodeManager`、`CloudSyncManager`、`InsightApiClient`
  - **S7-2 升級路徑**：擴 write API（saveProviderSelection / saveApiKey / clearSession 等）+ 持有 invite manager + 對 CloudSyncManager / InsightApiClient 提供 token 讀取入口
- `SettingsScreenCoordinator`（`app/settings/SettingsScreenCoordinator.kt`）
  - 已經是 `UserSessionRepository` 的唯一 caller in settings 側
  - 已有 `verifySessionAsync()`, `getApiKey()`, `onSyncStarted/Finished()`
  - **沒有** wire 到 `AuthSettingsSection / SyncSettingsSection / AISettingsSection` 的 callbacks（Sprint 1 留接口未接全）

### C.2 Settings Section 與 Coordinator 的耦合度

| Section | 取得 auth 方式 | 取得 syncManager 方式 | 取得 prefs 方式 | 已 DI？ |
|---------|--------------|---------------------|--------------|--------|
| AuthSettingsSection | constructor 參數 | — | — | ✅ |
| SyncSettingsSection | constructor 參數 | constructor 參數 | — | ✅ |
| InsightSettingsSection | constructor 參數 | — | — | ✅ |
| AISettingsSection | — | — | constructor 參數 | ✅（部分；FraudModelManager 仍 reach into BuildConfig） |
| ThemeSettingsSection | — | — | constructor 參數 | ✅ |
| BackgroundSettingsSection | — | — | constructor 參數 | ✅ |
| InputBehaviorSection | — | — | constructor 參數 | ✅ |

> 結論：**section 層已 Sprint 1 清理過**，沒有直接 reach into singleton 的問題。S7-2 / S7-3 收斂只需處理 **SettingsActivity 本體** 與 **CloudSyncManager / InsightApiClient / ApiKeySync** 的內部直讀。

### C.3 SettingsActivity 內 reach into singleton 的 4 處

| Line | Pattern | 用途 |
|------|---------|------|
| 64 | `SnowRealmAuthClient.getInstance(this).isLoggedIn` | onCreate 開頭比對 wasLoggedIn |
| 163 | `SnowRealmAuthClient.getInstance(this@SettingsActivity)` | 為 AuthSettingsSection / InsightSettingsSection 構造 |
| 266 | `CloudSyncManager.getInstance(this)` | 為 SyncSettingsSection 構造 |
| 442 | `SnowRealmAuthClient.getInstance(this)` | activity 後段 logic |

> 收斂方向：4 處改走 `SettingsScreenCoordinator`（Coordinator 已經持有 sessionRepository / cloudSyncManager / providerManager）。**屬 S7-2** 範圍。

---

## D. Migration Risk Flags（給 CodeX B 排序用）

### D.1 高風險（會影響登入/同步行為）

| 風險 | 描述 | 影響 round |
|------|------|----------|
| R-1 | `CloudSyncManager.resolveUserId()` 內 fallback 呼叫 `auth.fetchMe()` —— 與 `UserSessionRepository.verifySession()` 重複 | S7-2 收斂時必須統一，否則 race condition |
| R-2 | `ApiKeySync` 直接用 Insight JWT 作為 Supabase Authorization，但 `CloudSyncManager` 走 token exchange —— 兩套行為不一致 | S7-2 / S7-3 收斂須做選擇 |
| R-3 | `InviteCodeManager` 多實例（5 處 `new`）共享同一 prefs，`@Synchronized` 只保護同實例 → 跨實例 token 計數可能 race | 本輪不修，但 S7-2 設計 session repo 時要決定是否升級 singleton |
| R-4 | `EncryptedSharedPreferences` keystore 損壞 fallback 在 4 處重複，行為一致但程式分散 → 任一 owner 改 fallback 邏輯就會偏離 | Sprint 8 bugfix 候選 |
| R-5 | `AIProviderManager.providerCache` 在 `setSelectedModel` 不 invalidate（依賴 model 即時讀 prefs）→ Sprint 7 抽介面時若改變語意需重檢 | S7-2 注意 |

### D.2 中風險

| 風險 | 描述 |
|------|------|
| R-6 | `SnowRealmAuthClient.postAuth` 內**直接**再呼叫一次 `/api/auth/me`（line 242–254），與 `fetchMe()` 邏輯重複但 cookie header 自建 → S7-3 InsightApiClient 統一時要保留行為 |
| R-7 | `SnowRealmAuthClient` 同時提供 `currentUser` 屬性與 `fetchMe()` async →兩套 source of truth 並存，已是現況 |
| R-8 | `CloudSyncManager.exportPrefsToJson` / `importPrefsFromJson` hard-code 38 個 prefs key 字串 → S7-4 抽 sub-system snapshot 時可能漏欄位 |

### D.3 低風險（純 cleanup）

| 風險 | 描述 |
|------|------|
| R-9 | `BuildConfig.OPENAI_API_KEY` 在 ImeServiceInput / SpeechSettingsSection 各自直讀，AIProviderManager 已有此 key 的官方 import 機制 |
| R-10 | `InsightStickerApi`、`LanguagePackManager`、`ZCoinWalletActivity` 自建 InsightUrl 但無 401 retry / refresh，與 `InsightApiClient` 行為不一致 |
| R-11 | `GeminiNanoProvider` 屬於 on-device 推論，理論上**不應該**在 OkHttpClient 列表 → 須 IME 側（Claude Code B）審查內容 |

---

## E. Sprint 8 bugfix 候選（明確標記，**Sprint 7 不處理**）

| # | Bug 描述 | 來源 | 屬性 |
|---|---------|------|------|
| B-1 | 登入成功後設定頁不顯示帳號/方案/角色 | memory `project_yukiboard_login_bug.md` | UI / display 邏輯 bug |
| B-2 | OAuth 回呼時 `userId` 為空，依賴 `CloudSyncManager.resolveUserId()` 補救 | 程式現狀 | timing / state propagation bug |
| B-3 | EncryptedSharedPreferences keystore 損壞時各自 fallback，行為可能因 owner 不同而不同步 | 程式現狀 | resilience policy |
| B-4 | `AIProviderManager.setSelectedModel` 不 invalidate cache（雖無實質功能影響，但語意不一致） | 程式現狀 | cache 一致性 |
| B-5 | `InviteCodeManager` 多實例 `daily_token_used` 競態（極低概率，但 spec 未保證） | 程式現狀 | concurrency |
| B-6 | `SnowRealmAuthClient.postAuth` 在 login 流程內二次呼叫 `/api/auth/me`，可能造成多餘網路 round-trip | 程式現狀 | performance |
| B-7 | `CloudSyncManager.exportPrefsToJson` 漏欄位風險（hard-coded key list 與 YukiPreferences 欄位漂移） | 程式現狀 | maintenance bug |
| B-8 | Insight 後台檔案管理圖片/影片不顯示、社群監控私訊貼圖不顯示 | memory `project_insight_pending_bugs.md` | **非 YukiBoard 本地，不屬本 sprint 任一輪** |

---

## F. 本輪未處理項（明確標出邊界）

1. **IME 側 integration** — 由 `S7-0-CCB-IME-INTEGRATION-AUDIT`（Claude Code B）負責
2. **整合 round migration map** — 由 `S7-0-CXB-INTEGRATION-AUDIT`（CodeX B）負責，本文件作為其 settings 側輸入
3. **AISettingsActivity Section 化** — 不在本 sprint（依本輪限制 §5）
4. **InviteCodeManager singleton 化** — 不在本 sprint（依本輪限制 §5）
5. **CloudSyncManager 拆 Gateway** — 不在本 sprint（依本輪限制 §5），但已標記為 S7-3 候選
6. **金流 / 防詐 / 天氣 / GIF 等獨立 gateway** — 不在 settings 側 audit 範圍，僅標記
7. **任何 implementation** — 本輪嚴禁

---

## G. 回交 A/B/C/D/E

### A. 變更摘要

- 新增 `docs/SPRINT7_0_SETTINGS_AUTH_PROVIDER_SYNC_AUDIT_2026-04-18.md`
- 零 `.kt` 改動
- 補強 S7-PRE 為**精確 file:line 表格化**的 owner / callsite / mutable owner 真實來源

### B. 風險

- 文件本身零執行風險
- 已標出 11 項 migration risk flags（R-1 ~ R-11）供 CodeX B 排序
- 已標出 8 項 Sprint 8 bugfix 候選（B-1 ~ B-8）防止 Sprint 7 順手處理

### C. 未做的事

- IME 側不盤點（CCB 的 task）
- 不寫 cross-side migration map（CXB 的 task，本文件為其輸入）
- 不修任何已標 R 或 B 的問題

### D. 下一步建議

- `CodeX B` 拿本文件 + IME 側 audit + 自身技術視角，產出 `S7-0` 整合 migration map（含 round 進門條件 / 變更順序 / 回退路徑）
- `CodeX A` 等三份 audit 收齊後判 `S7-0 = COMPLETED → S7-1 開門`
- 後續 Claude Code A 在 S7-1 啟動時，依本文件 §B.1 的 10 處 OkHttp 直建 callsite 進入 implementation

### E. 請 `CodeX A` 判定

請 `CodeX A` 與 `S7-0-CXB-INTEGRATION-AUDIT` 與 `S7-0-CCB-IME-INTEGRATION-AUDIT` 合併審閱後輸出：

- `READY for S7-1` / `NOT READY（需補 X）` / `LOCKED`
- 若 READY：明確指派 S7-1 各執行者的 callsite range（settings 側對應本文件 §B.1 第 1–9 處，第 10 處為 IME / 防詐側）
- 若 NOT READY：列出需補的 audit 細節

---

（文件產出者：Claude Code A，2026-04-18）

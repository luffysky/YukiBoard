# Sprint 7 前置盤點 — settings / auth / provider / sync 側

- **任務 ID**：`S7-PRE-CCA-SETTINGS-AUTH-PROVIDER-PRECHECK`
- **日期**：2026-04-18
- **owner**：Claude Code A
- **用途**：盤點 `Sprint 7` 啟動前的 settings / auth / provider / sync 整合來源、禁區、以及與 `Sprint 8 bugfix` 的分界
- **規則遵守**：
  - 不改 `.kt`
  - 只做前置盤點與邊界整理
  - 不提前進入 `S7-0` implementation

---

## A. 盤點背景

`Sprint 7` 預定主題（由 `Sprint7-All-Round-Gate.md` 定義）：

| Round | 主題 | 當前狀態 |
|-------|------|---------|
| S7-PRE | 前置作業 | ACTIVE（此文件） |
| S7-0   | Integration Audit | LOCKED |
| S7-1   | AppHttpClient / Network Entry 收斂 | LOCKED |
| S7-2   | Session / Auth / Provider 狀態收斂 | LOCKED |
| S7-3   | Sync / External Gateway 收斂 | LOCKED |
| S7-4   | Prefs / Singleton / Mutable Field 真實來源收斂 | LOCKED |

以下盤點依此 5 大主題整理。

---

## B. S7-1 候選：`OkHttpClient.Builder()` 分散點

全專案 `OkHttpClient.Builder()` 實體建構共 **25 個檔案**（grep 結果）。
依 owner / 用途分類如下：

### B.1 auth / session / AI provider（settings 側本輪核心範圍）

| 檔案 | timeout 設定 | 備註 |
|------|-------------|------|
| `auth/SnowRealmAuthClient.kt:62` | 10s / 15s | login / register / fetchMe |
| `claw/provider/OpenAIProvider.kt:36` | 30s / 60s / 30s | OpenAI chat/completions |
| `claw/provider/AnthropicProvider.kt:31` | 30s / 60s / 30s | Anthropic messages |
| `claw/provider/GeminiProvider.kt` | — | Gemini REST |
| `claw/provider/GroqProvider.kt` | — | Groq REST |
| `claw/provider/SnowRealmProvider.kt:42` | 30s / 90s / 30s | insight-engine AI proxy |
| `claw/provider/GeminiNanoProvider.kt` | — | on-device（理論上不該用 http） |
| `data/InsightApiClient.kt:39` | 15s / 30s / 15s | Insight Engine 統一 API |
| `data/SupabaseClient.kt:29` | 10s / 15s | Supabase REST |
| `data/CloudSyncManager.kt:85` | 10s / 10s | Supabase token exchange |

### B.2 其它 owner 側（非本輪直接職責，但屬於 S7-1 收斂範圍）

| 檔案 | owner |
|------|-------|
| `speech/WhisperSpeechEngine.kt` | Claude Code B 側 |
| `speech/FasterWhisperSpeechEngine.kt` | Claude Code B 側 |
| `speech/GoogleCloudTtsEngine.kt` | Claude Code B 側 |
| `input/langpack/LanguagePackManager.kt` | Claude Code B 側 |
| `ui/panel/DictionaryPanelView.kt` | Claude Code B 側 |
| `ui/panel/LangLearnPanelView.kt` | Claude Code B 側 |
| `ui/settings/section/SpeechSettingsSection.kt` | settings 側（OpenAI key 測試） |
| `app/ime/ImeImageCommitter.kt` | Claude Code B 側 |
| `gif/GiphyApiClient.kt` | Claude Code B 側 |
| `gif/GifImageCache.kt` | Claude Code B 側 |
| `sticker/StickerImageCache.kt` | Claude Code B 側 |
| `sticker/InsightStickerApi.kt` | Claude Code B 側 |
| `weather/WeatherManager.kt` | Claude Code B 側 |
| `security/FraudOpenDataSync.kt` | 混合 |
| `security/FraudModelManager.kt` | 混合 |

> S7-1 收斂重點：建立 `AppHttpClient` / `HttpClientProvider` 單一入口，上列 25 處統一改走該來源。
> 本輪（S7-PRE）只盤點，不實作。

---

## C. S7-2 候選：Session / Auth / Provider 狀態分散點

### C.1 auth token / session 狀態擁有者

| 擁有者 | 類型 | 狀態儲存 |
|--------|------|---------|
| `SnowRealmAuthClient`（`getInstance`） | singleton，EncryptedSharedPreferences（`yukiboard_auth`） | JWT、userId、email、displayName、plan、role、tenantId、avatarUrl、tokenExpiry |
| `InviteCodeManager`（每次 new） | class，EncryptedSharedPreferences（`yukiboard_invite`） | inviteCode、type、dailyTokenUsed |
| `UserSessionRepository`（Sprint 1 建立） | class，**目前只讀** | snapshot wrapper |

### C.2 session 讀取點（散落）

| 呼叫點 | 呼叫方式 | 屬性 |
|--------|---------|------|
| `UserSessionRepository.getSnapshot()` | `auth.currentUser / isLoggedIn / getToken` | 已收斂（Sprint 1 入口） |
| `UserSessionRepository.verifySession()` | `authClient.fetchMe()` | 已收斂 |
| `ApiKeySync.uploadKeys/downloadKeys` | `SnowRealmAuthClient.getInstance(context)` → `auth.isLoggedIn / currentUserId / getToken` | **直讀 singleton，未走 Repository** |
| `CloudSyncManager` | `SnowRealmAuthClient.getInstance(context)` → `auth.isLoggedIn / currentUserId / getToken / fetchMe()` | **直讀 singleton，未走 Repository** |
| `InsightApiClient.buildRequest` | `authClient.getToken()`（透過 getInstance） | **直讀 singleton，未走 Repository** |
| `InsightApiClient.execute` | `authClient.refreshIfNeeded()` | 同上 |
| `SnowRealmProvider` | `InviteCodeManager(context)` + `SnowRealmAuthClient.getInstance` | **直讀兩個 singleton** |
| `AuthSettingsSection` | constructor 參數（已做 DI） | 已清理 |
| `SyncSettingsSection` | constructor 參數（已做 DI） | 已清理 |
| `LoginActivity` | — | 直接打 `BuildConfig.INSIGHT_API_BASE_URL` |
| `OAuthCallbackActivity` | — | 混合 |
| `ZCoinWalletActivity` | — | 直接打 Insight |
| `InviteCodeActivity` | — | 直接 new `InviteCodeManager` |
| `AISettingsActivity` | — | 呼叫 `AIProviderManager.getInstance` |

### C.3 provider 選擇與 API key 分散點

| 呼叫點 | 讀取 | 寫入 |
|--------|------|------|
| `AIProviderManager`（singleton） | `getSelectedProviderType / getApiKey / getSelectedModel` | `saveApiKey / setSelectedProvider / setSelectedModel` |
| `UserSessionRepository` | 只讀（getSelectedProvider / getApiKey / getSelectedModel） | — |
| `ApiKeySync` | 讀所有 type 的 key / model | 寫所有 type 的 key / model + setSelectedProvider |
| `AISettingsActivity / AISettingsSection` | 讀（UI 顯示） | 寫（使用者 UI 操作） |
| `ClawClient` / `SnowRealmProvider` / 各 provider | 建構時傳入 `apiKey: String`（by cache） | — |

> S7-2 收斂重點：`UserSessionRepository` 升級為 write owner，`ApiKeySync` / `CloudSyncManager` / `InsightApiClient` 皆改透過 Repository 取得 session / provider state，不再 reach into `SnowRealmAuthClient.getInstance` / `AIProviderManager.getInstance`。
> 本輪（S7-PRE）只盤點，不實作。

### C.4 Supabase token 雙層（Insight JWT ↔ Supabase JWT）

- `CloudSyncManager` 有自己的 token exchange cache（`cachedSupabaseToken` / `supabaseTokenExpiry`）
- `ApiKeySync` 直接用 Insight JWT 當 Supabase `Authorization: Bearer`（與上面不同流程）
- 兩條邏輯不一致，屬於 S7-2 或 S7-3 收斂範圍（待 CodeX B / CodeX A 決定劃到哪一輪）

---

## D. S7-3 候選：Sync / External Gateway 入口

### D.1 雲端同步 owner

| 檔案 | 職責 | 狀態 |
|------|------|------|
| `data/CloudSyncManager.kt` | 偏好 / 詞庫 / 主題 / 學習資料（7 子系統）全量 up/down | singleton，直讀多個 SharedPreferences |
| `data/InsightThemeSync.kt` | Insight 購買主題拉取 + 設定備份 | class，走 `InsightApiClient` |
| `data/InsightSurveyClient.kt` | 使用者調查回報 | class，有自己的 prefs |
| `data/InsightSocialClient.kt` | Claw 結果分享到 Insight 貼文 | class，走 `InsightApiClient` |
| `claw/provider/ApiKeySync.kt` | API Key 加密後同步到 Supabase | object |

### D.2 散落的 Insight 入口

| 檔案 | URL 建立方式 |
|------|------------|
| `SnowRealmAuthClient` | `BuildConfig.INSIGHT_API_BASE_URL` 自建 Request |
| `SnowRealmProvider` | `BuildConfig.INSIGHT_API_BASE_URL` 自建 Request |
| `InsightApiClient` | `BuildConfig.INSIGHT_API_BASE_URL`（統一入口候選） |
| `CloudSyncManager` | 直接用 `BuildConfig.INSIGHT_API_BASE_URL` 做 supabase-token exchange |
| `LanguagePackManager` | 直接用 `BuildConfig.INSIGHT_API_BASE_URL` |
| `InsightStickerApi` | 直接用 `BuildConfig.INSIGHT_API_BASE_URL` |
| `LoginActivity` | 直接用 `BuildConfig.INSIGHT_API_BASE_URL` |
| `ZCoinWalletActivity` | 直接用 `BuildConfig.INSIGHT_API_BASE_URL` |

> S7-3 收斂重點：`InsightApiClient` 升格為唯一 Insight gateway，上列 8 處改走 `InsightApiClient`（或 ReStructured API gateway）。本輪不實作。

### D.3 Supabase 入口

- `SupabaseClient`（singleton）已是唯一入口
- 但 `CloudSyncManager` / `ApiKeySync` 各自管理 auth token → 屬 S7-2/S7-3 接合面

### D.4 外部 non-Insight gateway（不在本輪核心，但要先標出）

| 檔案 | 外部服務 |
|------|---------|
| `currency/NewebpayClient.kt` | 藍新金流（Z 幣儲值） |
| `gif/GiphyApiClient.kt` | Giphy |
| `weather/WeatherManager.kt` | OpenWeatherMap |
| `speech/*.kt` | OpenAI Whisper / Google Cloud TTS |

---

## E. S7-4 候選：Prefs / Singleton / Mutable Field 真實來源

### E.1 Encrypted prefs（authentication 核心）

| Prefs 檔名 | 擁有者 | 內容 |
|-----------|-------|------|
| `yukiboard_auth` | `SnowRealmAuthClient` | JWT + user profile |
| `yukiboard_invite` | `InviteCodeManager` | 邀請碼 + daily token |
| `yukiboard_ai_secure` | `AIProviderManager` | AI API keys + selected provider/model |
| `yukiboard_zcoin` | `ZCoinManager` | Z 幣餘額 |

### E.2 non-encrypted prefs（settings / sync 側）

| Prefs 檔名 | 主擁有者 | 被其他人讀寫？ |
|-----------|---------|--------------|
| `yukiboard_prefs` | `YukiPreferences` | **是**（CloudSyncManager 直讀） |
| `yukiboard_user_dict` | `UserDictionary` | **是**（CloudSyncManager 直讀） |
| `yukiboard_adaptive_dict` | `AdaptiveDictionary` | **是**（CloudSyncManager 直讀） |
| `yukiboard_personal_dict` | `PersonalDictionary` | **是**（CloudSyncManager 直讀） |
| `yukiboard_phrase_predict` | `PhrasePredictor` | **是**（CloudSyncManager 直讀） |
| `yukiboard_sentence_predict` | `SentencePredictor` | **是**（CloudSyncManager 直讀） |
| `yukiboard_style` | `StyleProfiler` | **是**（CloudSyncManager 直讀） |
| `yukiboard_habits` | `TypingHabitTracker` | **是**（CloudSyncManager 直讀） |
| `yukiboard_correction_learn` | `CorrectionLearnManager` | **是**（CloudSyncManager 直讀） |
| `cloud_sync_meta` | `CloudSyncManager` | 只有自己 |
| `yukiboard_survey` | `InsightSurveyClient` | 只有自己 |
| `langpacks` | `LanguagePackManager` | 只有自己 |

> S7-4 收斂重點：`CloudSyncManager` 目前直接 `getSharedPreferences(...)` 讀 7 個子系統的底層資料，屬於 **pref 擁有權破損**。S7-4 要求把每個子系統提供 `exportSnapshot()` / `importSnapshot()`，CloudSyncManager 不再直讀他人 prefs。

### E.3 singleton（`getInstance`）盤點

| 類別 | 是否 `@Volatile` + `synchronized`？ | 備註 |
|------|-----------------------------------|------|
| `SnowRealmAuthClient` | 是 | `getInstance(context)` |
| `AIProviderManager` | 是 | `getInstance(context)` |
| `CloudSyncManager` | 是 | `getInstance(context)` |
| `InsightApiClient` | 是 | `getInstance(context)` |
| `SupabaseClient` | 是 | `getInstance()`（無需 context） |
| `LanguagePackManager` | 是 | `getInstance(context)` |
| `FraudModelManager` | 是 | `getInstance(context)` |
| `FraudOpenDataSync` | 是 | `getInstance(context)` |
| `InviteCodeManager` | 否 | 每次 new（不同 caller 持有不同實例） |

> S7-4 觀察：`InviteCodeManager` 是 class 非 singleton，但 `SnowRealmProvider` / `AISettingsSection` / `InviteCodeActivity` 各自 new 自己的 → token budget 共享不明確。S7-4 或 S7-2 應處理。

### E.4 `BuildConfig.*` 常數分散讀取

| 常數 | 被直讀點數 |
|------|-----------|
| `INSIGHT_API_BASE_URL` | 8 處 |
| `PLATFORM_API_KEY` | 3 處（InsightApiClient / SnowRealmProvider / InsightStickerApi） |
| `YUKIBOARD_SUPABASE_URL` | 3 處（SupabaseClient / AISettingsSection / ImeFraudModelInit） |
| `YUKIBOARD_SUPABASE_ANON_KEY` | 3 處（同上） |
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` | 由 AIProviderManager 一次性 import 到 prefs（OK），但 `ImeServiceInput` / `SpeechSettingsSection` 又直讀 | S7-4 收斂 |

---

## F. 設定頁側 Sprint 7 禁區

以下 Sprint 6 已收斂邊界，Sprint 7 各 round 不得逆向破壞：

### F.1 Panel binder 側（Sprint 6 完成）

| Panel | binder | 限制 |
|-------|--------|------|
| CLAW | `ImeClawPanelBinder` | `ClawClient?` / `YukiPreferences?` 須維持**實例注入**，不得改為 `context`+reach into singleton |
| USER_DICT | `ImeUserDictPanelBinder` | 同上，`UserDictionary` 實例注入不得破 |
| THEME | `ImeThemePanelBinder` | `ThemeManager` writer owner 不得改動 |
| HANDWRITING | `ImeHandwritingPanelBinder` | 零 settings 依賴維持 |
| MUSIC / REMOTE | 對應 binder | 零 auth/provider/sync 依賴維持 |

### F.2 Sprint 1 收斂點（Sprint 7 不得倒退）

- `UserSessionRepository.getSnapshot()` 作為 session 讀取入口 —— S7-2 要擴寫入，不可降級
- `AuthSettingsSection` / `SyncSettingsSection` constructor DI 形式 —— 不得退回 `getInstance`
- `ImeSessionCoordinator` / `ImeSessionHooks` Sprint boundary 維持

### F.3 AI 設定頁全面禁區

- `AISettingsActivity` 目前為 Activity 本體（**未** Section 化），與 `SettingsActivity` 不同級
- Sprint 7 **不處理** `AISettingsActivity` 重寫，只處理其**依賴**（AIProviderManager / ApiKeySync）
- 若要 Section 化需新 Sprint 規劃

---

## G. Sprint 7 候選 round 範圍 vs Sprint 8 bugfix 分界

### G.1 Sprint 7 應做（整合收斂，大 refactor）

| Round | 候選題目 |
|-------|---------|
| S7-0 | 輸出 integration audit + migration map（文件） |
| S7-1 | 建立 `AppHttpClient` / 單一 OkHttp owner，25 處統一 |
| S7-2 | `UserSessionRepository` 升級為 session + provider state write owner；`ApiKeySync` / `CloudSyncManager` / `InsightApiClient` 停止直讀 `SnowRealmAuthClient.getInstance` |
| S7-3 | `InsightApiClient` 升為唯一 Insight gateway，8 處 Insight URL 直讀統一改道；`CloudSyncManager` supabase-token exchange 抽出 |
| S7-4 | 每個 sub-system prefs 提供 snapshot export/import；`CloudSyncManager` 不再直讀他人 prefs；`BuildConfig.*` 常數統一 accessor |

### G.2 Sprint 8 bugfix 應做（保留，不混入 Sprint 7）

以下問題**已知但屬 bug / policy**，非 integration 收斂：

1. **登入帳號在設定頁不顯示**
   - 已記於 memory `project_yukiboard_login_bug.md`
   - 根因可能是 `currentDisplayName` fallback 不完整，或 `fetchMe` 時機點
   - Sprint 7 改架構時要**謹慎不要破壞 fallback**，但 bug 本身留給 Sprint 8

2. **Insight 總後台 bug（檔案管理圖片 / 社群監控貼圖）**
   - 記於 `project_insight_pending_bugs.md`
   - 非 YukiBoard 本地，Sprint 7/8 都不處理

3. **OAuth 回呼時 userId 為空的 patch**
   - `CloudSyncManager.resolveUserId()` 目前做 fallback
   - Sprint 8 應決定要保留這個 fallback，還是統一由 `UserSessionRepository.verifySession()` 處理

4. **`EncryptedSharedPreferences` keystore 損壞 fallback**
   - 目前 `SnowRealmAuthClient` / `InviteCodeManager` / `AIProviderManager` 三處各有一套 try/catch fallback 到普通 prefs
   - Sprint 7 收斂統一 factory 時要保留此行為
   - bugfix level 的例外處理屬 Sprint 8 決定

5. **Provider cache invalidation**
   - `AIProviderManager.saveApiKey` 有做 cache invalidate
   - `setSelectedModel` 沒 cache invalidate（依賴 `getSelectedModel` 每次讀 prefs）
   - 是否為 bug → Sprint 8 判定

### G.3 明確不屬 Sprint 7 / Sprint 8 任一的

- `NewebpayClient`（金流）refactor：待後續 Sprint
- `WeatherManager` / `GiphyApiClient` / `Whisper*` 獨立重構：屬 Claude Code B 側
- `FraudModel` / `FraudRule` sync：屬安全側專門 sprint

---

## H. Sprint 7 Round 切法建議（給 CodeX A 參考）

> **注意**：依 `feedback_gate_no_self_schedule.md`，本節只列候選順序，最終 round 推進仍由 `CodeX A` 拍板。

### H.1 S7-0（本 owner 可協助）

- `CodeX B` 主導 migration map
- `Claude Code A` 補 settings / auth / provider / sync 側欄位（本文件可作基礎）
- `Claude Code B` 補 IME 側欄位

### H.2 S7-1（AppHttpClient）

- 建議 `CodeX B` 起 `AppHttpClient` 骨架（timeout profile、interceptor、單一 owner）
- `Claude Code A` 改：auth / provider / insight / supabase / cloud sync 共 10 處
- `Claude Code B` 改：speech / langpack / sticker / gif / weather / fraud / image committer 共 15 處
- 收斂前**不得拆成 S7-1a/S7-1b**（否則 timeout profile 不一致風險）

### H.3 S7-2（Session / Auth / Provider 狀態）

- `Claude Code A` 主要 owner
- 前置：`UserSessionRepository` 升級為 write owner
- ApiKeySync / CloudSyncManager / InsightApiClient 改走 Repository
- 不處理 `AuthSettingsSection` / `SyncSettingsSection` 本體（已 Sprint 1 清理）

### H.4 S7-3（Sync / External Gateway）

- `Claude Code A` 主要 owner
- 8 處直讀 `INSIGHT_API_BASE_URL` 統一 `InsightApiClient`
- `CloudSyncManager` supabase-token exchange 抽出成單獨 gateway
- 可能需分 S7-3a（InsightApiClient 強化）/ S7-3b（CloudSyncManager 瘦身）

### H.5 S7-4（Prefs / Singleton）

- `Claude Code A` + `Claude Code B` 分工
- Claude Code A：auth/invite/ai provider prefs + CloudSync 偏好 / 主題部分
- Claude Code B：7 個 learning sub-system prefs（adaptive / personal / phrase / sentence / style / habits / correction）各自 export/import
- 建議最後做（前 3 輪完成後依賴路徑才清楚）

---

## I. 本輪（S7-PRE）風險

### I.1 已識別風險

1. **登入 bug 未修**（memory `project_yukiboard_login_bug.md`）
   - Sprint 7 的 `UserSessionRepository` 寫入 owner 變動可能改動觸發時機
   - 收斂時須保留目前 `resolveUserId()` / `fetchMe()` 補救路徑
2. **CLAW panel 完整 session 流程未 implementation**（屬 Sprint 6-6 side note 明確保留）
   - Sprint 7 的 integration 收斂可能順帶改動，需先確認 binder 不受影響
3. **ApiKeySync 與 CloudSyncManager 的 Supabase token 使用不一致**
   - ApiKeySync 直接用 Insight JWT
   - CloudSyncManager 做 Insight JWT → Supabase JWT 交換
   - S7-2 / S7-3 需決定統一方案
4. **`AISettingsActivity` 非 Section 化**
   - Sprint 7 不重寫它，但其直接呼叫 `AIProviderManager.getInstance` 的行為會與 S7-2 的新 Repository 邊界衝突
   - 需 `CodeX A` 判定：是維持現狀，還是 S7-2 順帶 Section 化？

### I.2 建議 `CodeX A` 在開 S7-0 前釐清的三件事

1. **Sprint 7 是否包含 `AISettingsActivity` Section 化？**（若不含，S7-2 要留 legacy access）
2. **`InviteCodeManager` 是否升 singleton？**（若 SnowRealmProvider / Section / Activity 要共享 token 計數）
3. **`CloudSyncManager` 是否在 S7-3 被拆成 `CloudSyncGateway` + 多個 per-subsystem snapshot provider？**（會影響 S7-4 範圍）

---

## J. 回交 A/B/C/D/E

### A. 變更摘要

- 僅新增 `docs/SPRINT7_PRE_SETTINGS_AUTH_PROVIDER_PRECHECK_2026-04-18.md`
- 零 `.kt` 改動
- 盤點範圍：auth / session / AI provider / insight api / supabase / cloud sync / prefs / singleton / BuildConfig

### B. 風險

- 盤點文件本身零執行風險
- 但有 4 項待 `CodeX A` 釐清的邊界問題（見 I.2）

### C. 未做的事

- 不做 IME 側 integration 盤點（`S7-PRE-CCB-IME-INTEGRATION-PRECHECK` 由 Claude Code B 做）
- 不做 round 切法最終決定（`CodeX B` 的 `S7-PRE-CXB-PRECHECK` 提技術建議，`CodeX A` 拍板）
- 不處理 Sprint 8 bugfix 列表（只劃出分界，不分細項）
- 不實作 `UserSessionRepository` 升級（屬 S7-2 implementation）

### D. 下一步建議

- `CodeX B` 基於本文件 + IME 側 precheck 產出 `S7-0` migration map 骨架
- `CodeX A` 回答 I.2 的 3 個邊界問題，決定 `S7-0` 是否直接開門
- `Claude Code A` 待 `CodeX A` 判定後進 `S7-0`（integration audit 文件），仍不進 implementation

### E. 請 `CodeX A` 判定

請 `CodeX A` 於本文件與 `S7-PRE-CXB` / `S7-PRE-CCB` 合併審閱後輸出：

- `READY for S7-0` / `NOT READY（需先釐清 X）` / `LOCKED`
- 若 READY：指名 `S7-0` owner（預期為 `CodeX B`）與 `Claude Code A` / `Claude Code B` 的補欄任務 ID
- 若 NOT READY：列出需補的前置項目與再次回交的條件

---

## K. 附：Sprint 6 → Sprint 7 狀態對照

| 維度 | Sprint 6 收斂成果 | Sprint 7 目標 |
|------|-----------------|--------------|
| Panel 邊界 | 6 個 panel binder 全收斂 + CLAW panel 嚴格 type-parameter 注入 | 維持不變 |
| Session 讀取 | Sprint 1 已引入 `UserSessionRepository`（只讀） | 升級為 write owner |
| Network entry | 散落 25 處 `OkHttpClient.Builder()` | 統一 `AppHttpClient` |
| Insight API | 散落 8 處 `INSIGHT_API_BASE_URL` 直讀 | 統一 `InsightApiClient` |
| Prefs 擁有權 | CloudSyncManager 直讀 9 個子系統 prefs | 每個子系統自管 snapshot |
| Singleton | 9 個 `getInstance` + 1 個每次 new（InviteCodeManager） | 統一策略（可能續用 singleton，但 read/write 皆走 Repository） |

---

（文件產出者：Claude Code A，2026-04-18）

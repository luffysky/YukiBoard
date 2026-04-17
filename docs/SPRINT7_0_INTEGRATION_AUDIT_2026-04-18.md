# Sprint 7-0 Integration Audit

任務 ID：`S7-0-CXB-INTEGRATION-AUDIT`

日期：2026-04-18

身份：CodeX B

依據：

- `Sprint7-All-Round-Gate.md`
- `docs/SPRINT7_PRE_IME_INTEGRATION_PRECHECK_2026-04-18.md`
- `docs/SPRINT7_PRE_SETTINGS_AUTH_PROVIDER_PRECHECK_2026-04-18.md`
- `docs/SPRINT7_PRE_TECH_RISK_AND_ROUND_CUT_2026-04-18.md`

## 0. 範圍宣告

- 本文件是 `Sprint 7-0` 的總 audit 文件
- 本文件只做：
  - audit
  - inventory
  - migration map
  - owner map
  - file scope 與 round 進門條件整理
- 本文件不做 implementation
- 本文件不改任何 `.kt`
- 本文件不處理 bugfix；bugfix 仍留給 `Sprint 8`

## 1. Audit 結論

### 1.1 總結

`Sprint 7` 要解的不是單一 bug，而是四組已被確認的 integration ownership 破碎點：

1. network client / `OkHttpClient.Builder()` 分散
2. session / auth / provider state 同時被 repository 與 singleton 直接 reach-in
3. Insight / Supabase / external gateway 入口分散
4. prefs / singleton / mutable field 的真實 owner 未收斂

### 1.2 CodeX B 判定

- `S7-0` 的 audit 原料已足夠
- 已可支撐 `CodeX A` 在後續 round 對 `S7-1` 到 `S7-4` 開門
- 但目前仍不應直接跳進 implementation；必須依序處理，不能跨 round 混改

## 2. Normalized Inventory

### 2.1 Network Entry Inventory

目前 repo 內 `OkHttpClient.Builder()` 實際命中文件為 **25 個檔案**。

| 分類 | 檔案數 | 代表 owner | Round |
| --- | ---: | --- | --- |
| auth / provider / data / sync 核心 | 10 | `SnowRealmAuthClient` / `InsightApiClient` / `CloudSyncManager` / `SupabaseClient` / `claw/provider/*` | `S7-1` |
| IME runtime 直屬 | 1 | `ImeImageCommitter` | `S7-1` |
| speech / weather / gif / sticker / langpack | 10 | `speech/*` / `WeatherManager` / `GiphyApiClient` / `LanguagePackManager` / `InsightStickerApi` | `S7-1` |
| UI 自帶 http 行為 | 4 | `DictionaryPanelView` / `LangLearnPanelView` / `SpeechSettingsSection` / `GifImageCache` / `StickerImageCache` 類 | `S7-1` |

目前問題不是 builder 數量本身，而是：

- timeout policy 無單一 owner
- auth/header policy 無單一 owner
- 有些 caller 同時兼任 gateway 與 feature 行為

### 2.2 Session / Auth / Provider State Inventory

目前至少有三層 state owner 並存：

| 層級 | 現狀 owner | 問題 | Round |
| --- | --- | --- | --- |
| session snapshot | `UserSessionRepository` | 目前偏讀取 facade，未成為唯一 write owner | `S7-2` |
| auth persistence | `SnowRealmAuthClient` | 多處直接 `getInstance()` reach-in | `S7-2` |
| provider / api key persistence | `AIProviderManager` | UI、sync、provider caller 皆可直達 | `S7-2` |

已確認的 dual-owner / direct-owner 熱點：

- `SettingsScreenCoordinator` 與 `ImeSessionCoordinator` 各自 new `UserSessionRepository`
- `CloudSyncManager`、`InsightApiClient`、`ApiKeySync`、`SnowRealmProvider`、`ClawClient` 直接 reach into singleton
- `AISettingsActivity` 仍直接使用 `AIProviderManager.getInstance(...)`

### 2.3 Gateway Inventory

目前 external gateway 入口分成三種：

| 類型 | 現狀入口 | 問題 | Round |
| --- | --- | --- | --- |
| Insight API | `SnowRealmAuthClient` / `InsightApiClient` / `SnowRealmProvider` / `CloudSyncManager` / `LanguagePackManager` / `InsightStickerApi` / `LoginActivity` / `ZCoinWalletActivity` | base URL 與 auth/header 策略分散 | `S7-3` |
| Supabase | `SupabaseClient` + `CloudSyncManager` token exchange + `ApiKeySync` 直上 JWT | token policy 不一致 | `S7-3` |
| IME external engine hub | `YukiBoardIMEService.initExternalEngines()` + `ImeFraudModelInit` | runtime 組裝點集中，但 owner 邊界未抽清 | `S7-3` |

### 2.4 Prefs / Singleton / Mutable Owner Inventory

目前 owner 混亂不是因為沒有單例，而是：

- singleton 存在，但 caller 可以繞過 repository 直接 reach-in
- prefs 有主擁有者，但 `CloudSyncManager` 直接讀他人底層 storage
- IME service 本體仍持有多組 mutable field，跨 extension 分散讀寫

已確認高風險點：

| 類型 | 熱點 | 問題 | Round |
| --- | --- | --- | --- |
| prefs 直讀 | `CloudSyncManager` 直接讀 `adaptive/personal/phrase/sentence/style/habits/correction` 等 prefs | 擁有權破損 | `S7-4` |
| singleton 直達 | `SnowRealmAuthClient.getInstance` / `AIProviderManager.getInstance` / `ThemeManager.getInstance` | read path 失控 | `S7-4` |
| mutable service field | `YukiBoardIMEService` 內多組 engine/client/panel field | runtime state source 散 | `S7-4` |

## 3. Owner Map

### 3.1 Current Owner Map

| 類別 | Current Owner | 主要 caller |
| --- | --- | --- |
| auth token / profile | `SnowRealmAuthClient` | `UserSessionRepository`、`CloudSyncManager`、`InsightApiClient`、`ClawClient`、`SnowRealmProvider`、多個 Activity |
| provider / API key / model | `AIProviderManager` | `UserSessionRepository`、`ApiKeySync`、`ClawClient`、`AISettingsActivity` |
| session snapshot facade | `UserSessionRepository` | `SettingsScreenCoordinator`、`ImeSessionCoordinator` |
| Insight REST | `InsightApiClient` + 多個自建 caller | data/auth/claw/ui |
| Supabase REST | `SupabaseClient` + `CloudSyncManager` / `ApiKeySync` 特例 | data/security/ui |
| IME runtime external engines | `YukiBoardIMEService.initExternalEngines()` | service runtime |
| sync snapshot aggregation | `CloudSyncManager` | settings / IME session hooks |

### 3.2 Target Owner Map

這裡的 target 是 audit 階段建議的收斂方向，不是本輪 implementation。

| 類別 | Target Owner | Round |
| --- | --- | --- |
| http client policy | `AppHttpClient` 或等價單一 network owner | `S7-1` |
| session state read/write | `UserSessionRepository` | `S7-2` |
| provider state read/write | `UserSessionRepository` 或明確 repository facade | `S7-2` |
| Insight gateway | `InsightApiClient` 或等價唯一 gateway | `S7-3` |
| Supabase token policy | 單一 gateway / token strategy owner | `S7-3` |
| subsystem snapshot exchange | 各 subsystem 自己的 snapshot provider，`CloudSyncManager` 不再直讀 prefs | `S7-4` |

## 4. Migration Map

### 4.1 `S7-1` Migration Map

目標：把 network 建構責任從 feature caller 中抽離。

| Current Source | Target | 備註 |
| --- | --- | --- |
| `OkHttpClient.Builder()` in 25 files | `AppHttpClient` / 單一 provider | 先統一 timeout / header / cache policy，再分批替換 caller |
| `SpeechSettingsSection`、`DictionaryPanelView`、`LangLearnPanelView` 等 UI 自建 client | 呼叫 service/repository/gateway 層，不持有 raw builder | 避免 UI 層成為 network owner |
| `ImeImageCommitter` inline client | 改走統一 http owner，但不得擴寫到 IME 其他責任 | IME 只保留 commit 行為 |

### 4.2 `S7-2` Migration Map

目標：把 session / auth / provider state 的讀寫 owner 固定下來。

| Current Source | Target | 備註 |
| --- | --- | --- |
| `SnowRealmAuthClient.getInstance(...)` direct reach-in | `UserSessionRepository` | feature caller 不再直接碰 auth singleton |
| `AIProviderManager.getInstance(...)` direct reach-in | repository facade | 避免 UI / sync / provider caller 各自碰 provider singleton |
| `SettingsScreenCoordinator` + `ImeSessionCoordinator` 各自 new repo | 保持 facade，但收斂為單一 owner 語義 | 可同實例或同協議，不必先假設 DI 重寫 |
| `ApiKeySync` / `CloudSyncManager` / `InsightApiClient` 直讀 auth/provider | 改走 repository 抽象 | 這是 `S7-2` 核心切點 |

### 4.3 `S7-3` Migration Map

目標：把 gateway 與 token policy 收斂成可預測入口。

| Current Source | Target | 備註 |
| --- | --- | --- |
| `INSIGHT_API_BASE_URL` 散落讀取 | 單一 Insight gateway | caller 不再自己組 base URL |
| `CloudSyncManager` 自做 Supabase token exchange | 單一 gateway / token strategy owner | 與 `ApiKeySync` 對齊策略 |
| `ApiKeySync` 直接用 Insight JWT | 統一到同一 token policy | 不再有兩條不同 Supabase auth 路 |
| `ImeFraudModelInit` 直讀 Supabase config | 保持功能，但 target 應接向統一 gateway/config owner | 本輪只記錄，不改動 |

### 4.4 `S7-4` Migration Map

目標：讓每組 state source 有明確 owner，不再互讀底層 storage。

| Current Source | Target | 備註 |
| --- | --- | --- |
| `CloudSyncManager` 直讀 7+ 個 subsystem prefs | subsystem snapshot provider | `CloudSyncManager` 只協調，不直讀 |
| scattered singleton reads | read path 經 facade / repository | singleton 可留，但不可亂 reach-in |
| `YukiBoardIMEService` 分散 mutable field | 明確標記 owner / lifecycle / read-write boundary | 先 audit，再決定是否進一步重構 |

## 5. File Scope Proposal

### 5.1 `S7-1` 預計觸及範圍

- `auth/`
- `data/`
- `claw/provider/`
- `speech/`
- `gif/`
- `weather/`
- `sticker/`
- `input/langpack/`
- `app/ime/ImeImageCommitter.kt`
- 少量 UI caller（若仍自建 http）

禁區：

- 不得順手改 session/provider owner
- 不得改 panel binder / `YukiBoardIMEService` 責任

### 5.2 `S7-2` 預計觸及範圍

- `app/session/UserSessionRepository.kt`
- `app/settings/SettingsScreenCoordinator.kt`
- `app/ime/ImeSessionCoordinator.kt`
- `auth/`
- `claw/provider/AIProviderManager.kt`
- `data/CloudSyncManager.kt`
- `data/InsightApiClient.kt`
- `claw/provider/ApiKeySync.kt`
- 必要的 settings/auth caller

禁區：

- 不得把 `AISettingsActivity` 整體重寫成新結構
- 不得混入 gateway 層抽換

### 5.3 `S7-3` 預計觸及範圍

- `data/InsightApiClient.kt`
- `data/SupabaseClient.kt`
- `data/CloudSyncManager.kt`
- `auth/`
- `claw/provider/SnowRealmProvider.kt`
- `app/ime/ImeFraudModelInit.kt`
- 仍直組 Insight URL 的 caller

禁區：

- 不得順手做 prefs snapshot/export-import 重寫
- 不得把 IME external engine 組裝重構成新架構 sprint

### 5.4 `S7-4` 預計觸及範圍

- `data/CloudSyncManager.kt`
- 所有被它直讀底層 prefs 的 subsystem owner
- IME / settings 端需要調整 read path 的少量 caller

禁區：

- 不得回頭改 network / gateway owner
- 不得把 bugfix 混成收尾 round

## 6. Round 進門條件

### 6.1 `S7-1` 開門條件

1. `S7-0` 已清楚列出 25 個 builder 檔案與 owner 分類
2. `CodeX A` 同意 network owner 目標形態
3. 明確禁止在 `S7-1` 同時改 session/provider/sync owner

### 6.2 `S7-2` 開門條件

1. `S7-1` 已完成
2. `UserSessionRepository` 的 target role 已被 `CodeX A` 明文化
3. `AISettingsActivity` 是否保留 legacy 直達路徑已被先裁定

### 6.3 `S7-3` 開門條件

1. `S7-2` 已完成
2. Insight gateway 與 Supabase token strategy 的 target owner 已清楚
3. `CloudSyncManager` 不會在本輪被擴成 snapshot refactor

### 6.4 `S7-4` 開門條件

1. `S7-3` 已完成
2. 各 subsystem 的 target snapshot provider 介面已定義
3. 不再存在需要先靠 network/session 決策才能落地的阻塞

## 7. 與 Sprint 8 的分界

以下不屬 `S7-0`，也不應在 `S7-1` 到 `S7-4` 沒有明確 gate 指派時被順手處理：

1. login display / `fetchMe()` 時機類 UI bug
2. OAuth callback fallback 修補
3. keystore fallback policy 統一
4. provider cache invalidation 類 bug
5. 任何純 UI 或行為微修

## 8. CodeX B 裁決建議

### 8.1 本輪結論

我建議 `CodeX A` 對 `S7-0` 最終可給：

`READY`

前提是本文件與兩份 side audit 一併採納為 `S7-0` 盤點基線。

### 8.2 理由

1. 盤點主題已全部落地：network、session/auth/provider、gateway、prefs/singleton/mutable owner
2. migration map 已足夠支撐下一門 implementation 開門
3. round 分界與禁區已可操作，不需要再補前置文件

## 9. 回交摘要

### A. 本輪交付

- 新增本文件
- 0 行 `.kt` 修改
- 完成 normalized inventory、owner map、migration map、file scope proposal、round 進門條件

### B. 本輪風險

- 文件本身無 runtime 風險
- 主要風險在後續 round 若不守邊界，會把 `S7-1` 到 `S7-4` 混成單一大重構

### C. 本輪未做

- 未做 implementation
- 未指定後續 round 的實作者分工
- 未處理任何 `Sprint 8` bug

### D. 下一步建議

- 由 `CodeX A` 審核本文件與兩份 side audit
- 若通過，開 `S7-1 AppHttpClient / Network Entry 收斂`

### E. 請 `CodeX A` 判定

- `READY for S7-1`
- 或 `NOT READY（需補指定 audit 缺口）`


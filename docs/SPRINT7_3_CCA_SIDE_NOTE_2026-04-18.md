# Sprint 7-3 CCA Side-Note + Acting CodeX B Tech Review

- 任務 ID：
  - `S7-3-CCA-SIDE-NOTE`
  - `S7-3-CCA-ACTING-CXB-TECH-REVIEW`（暫代 CodeX B 技術審核）
- Owner：Claude Code A
- Type：pre-implementation side-note + acting tech review（在 CCB 的 `S7-3-CCB-SYNC-GATEWAY-IMPLEMENTATION` 回交前先給紅線 + file scope 邊界）
- Scope：Sync / External Gateway 收斂
- Date：2026-04-18

> 本輪規則：
>
> 1. 不改 `.kt`。
> 2. Side-note 視角：盯住 `CloudSyncManager` prefs 直讀 / Supabase token exchange / `InsightApiClient` 是否成為唯一 Insight gateway；屬 `S7-4` / `S8` 的題目只能註記，不得前移。
> 3. Acting CodeX B 視角：給 CCB 明確的 file scope、owner 邊界、最小安全收斂策略。
> 4. 兩個視角在本文件中分段清楚區分，避免混線。
> 5. `Claude Code A` 與 `Claude Code B` 皆不得自行指定 `S7-4`。

---

## PART I ── `S7-3-CCA-SIDE-NOTE`（Claude Code A 側）

### A. S7-2 完成後的狀態錨定

- `UserSessionRepository` 已成為 session / provider 狀態寫入 owner（新增 `saveProviderSelection` / `saveApiKey` / `saveSelectedModel` / `clearSession` / `currentUserId` / `getAuthToken()` / `getSelectedModel` / `getCurrentProvider` / `hasValidProviderConfig`）。
- `ApiKeySync` 已改走 Repository，移除對 `SnowRealmAuthClient.getInstance` / `AIProviderManager.getInstance` 的直達。
- 本輪未前移：`CloudSyncManager` gateway、`InviteCodeManager` singleton 化、`postAuth` 雙 fetchMe。

- 進入 S7-3 時，gateway 層級仍存在的直達 singleton / gateway-level 問題：
  - `data/CloudSyncManager.kt:56` 直接 `SnowRealmAuthClient.getInstance(context)`（S7-2 明確留）
  - `data/CloudSyncManager.kt:71-78` `resolveUserId()` 內再打一次 `auth.fetchMe()`（與 `SettingsScreenCoordinator.verifySessionAsync` 的 fetchMe 形成 R-1 路徑，S7-2 明確留）
  - `data/CloudSyncManager.kt:94-131` 自有 Supabase token exchange，直接打 Insight `/api/auth/supabase-token`，**未走** `InsightApiClient`
  - `data/InsightApiClient.kt:36` 直接 `SnowRealmAuthClient.getInstance(context)`（尚未走 Repository）
  - `data/InsightApiClient.kt:117-129` 401 refresh + retry 自包（尚未統一）

---

### B. 本輪必須盯住的三個點（Gate §S7-3 side-note 要求）

#### B-1 `CloudSyncManager` prefs 直讀

- **現況**：`CloudSyncManager` 直接讀寫 9 個 SharedPreferences／subsystem 入口：
  - `YukiPreferences`（context 建構）→ `exportPrefsToJson` 讀取 38 個 field（lines 346-394），`importPrefsFromJson` 寫 38 個 field（lines 397-436）
  - `context.getSharedPreferences("cloud_sync_meta", …)`（line 58）— sync meta
  - `context.getSharedPreferences("yukiboard_adaptive_dict", …)`（lines 511, 522）
  - `context.getSharedPreferences("yukiboard_personal_dict", …)`（lines 554, 565）
  - `context.getSharedPreferences("yukiboard_phrase_predict", …)`（lines 600, 620）
  - `context.getSharedPreferences("yukiboard_sentence_predict", …)`（lines 645, 663）
  - `context.getSharedPreferences("yukiboard_style", …)`（lines 687, 701）
  - `context.getSharedPreferences("yukiboard_habits", …)`（lines 723, 743）
  - `context.getSharedPreferences("yukiboard_correction_learn", …)`（lines 769, 780）
  - `UserDictionary(context)`（lines 218, 311）— 直接 new subsystem instance
- **屬性**：
  - 「CloudSyncManager 知道 38 個 YukiPreferences field key + 7 個 subsystem 內部 pref schema」**本身就是** S7-4 要處理的「Prefs / Singleton / Mutable Field 真實來源收斂」。
  - S7-3 的 gateway 拆分**不等於** prefs 收斂。
- **S7-3 要守的紅線**：
  - **不得**在 S7-3 把這 14 處 prefs 直讀搬到 Repository／snapshot provider。那是 S7-4。
  - 可以在 **類內部** 把 export/import 函式群組化（例如抽成 `internal fun exportPrefsPayload()` 聚集在一處），但 **key / schema / subsystem pref file name** 不得被搬家。
  - 若 S7-3 gateway 拆分要引入「snapshot provider 介面」（把 `JsonObject` build 與網路 upload 分開），**介面本體可以**在 S7-3 建立，但 snapshot provider 的 **實作仍留在 CloudSyncManager 側並直讀 prefs**，等 S7-4 再把實作拆到正確的 owner。

#### B-2 Supabase token exchange 路徑

- **現況**：
  - `CloudSyncManager.getSupabaseToken()`（lines 94-131）自行 POST `/api/auth/supabase-token`，**直接使用 `httpClient`**（即 `AppHttpClient.newBuilder(SHORT)`），**沒有**透過 `InsightApiClient`。
  - 自有 token cache（1h expire，5min 提前 refresh）。
  - 與 `ApiKeySync` 的 token 策略不同：後者在 S7-2 已走 `UserSessionRepository.getAuthToken()` 直接作 Supabase Authorization（沒有 exchange）。
  - 這是 S7-0 audit 的 R-2「Token 策略分歧」未解的部分：**CloudSyncManager 仍走 exchange**；`ApiKeySync` 走直用。
- **屬性**：
  - 本輪是 Sync / External Gateway 收斂，**正是** `CloudSyncManager` 自有 exchange 與 InsightApiClient gateway 化的時機。
  - 但「讓 `ApiKeySync` 與 `CloudSyncManager` 走同一條路徑」會讓其中一方行為變更：
    - 若把 exchange 搬到統一 gateway → `ApiKeySync` 行為變（多一次 exchange）
    - 若把 exchange 拿掉、都走直用 → `CloudSyncManager` 行為變（拔掉 exchange cache，可能讓 Supabase 驗證失敗）
  - 該議題的**行為選擇權**應在 `CodeX A` 裁決，不在 CCB 自由發揮。
- **S7-3 要守的紅線**：
  - CCB 若要把 Supabase token 策略統一 → **必須明示** before/after 行為、誰的 token lifecycle 會變、是否保留 exchange cache、為什麼。
  - CCB 若選擇「本輪只把 `CloudSyncManager` 的 exchange 走 `InsightApiClient`」 → 合法，但 **exchange 端點** `/api/auth/supabase-token` 本身沒做過 `InsightApiClient` 401 refresh retry 測試；需在實作文件裡說明 401 是否會 retry（目前自包是直接 return null）。
  - 不得**無聲**把 exchange 拿掉或搬家；不得把 `ApiKeySync` 的直用改成 exchange（S7-2 剛收斂完，本輪不該回頭動它）。

#### B-3 `InsightApiClient` 是否成為唯一 Insight gateway

- **現況**：
  - `InsightApiClient` 已是 singleton（line 28），提供 `get` / `post` / `stream`，並有 401 refresh + retry。
  - 但目前**並非** Insight API 的唯一 gateway：
    - `SnowRealmAuthClient` 的 login / fetchMe / me 直接用自己的 `client`（S7-1 已替 Builder，但仍是獨立 OkHttp instance）。
    - `CloudSyncManager` 的 `/api/auth/supabase-token` 直接用自己的 `httpClient`。
    - provider 裡 `SnowRealmProvider`（Insight streaming proxy）可能也是自包（需 CCB 實作時複檢）。
  - `InsightApiClient` 本身仍直接 `SnowRealmAuthClient.getInstance(context)`（line 36），未走 `UserSessionRepository`。
- **屬性**：本輪 S7-3 的正題。「讓 Insight 唯一進入口 = `InsightApiClient`」是合理的 gateway 收斂目標。
- **S7-3 要守的紅線**：
  - 「讓 `CloudSyncManager.getSupabaseToken()` 走 `InsightApiClient.post("/api/auth/supabase-token", body)`」是**合法**的本輪動作，但前提：`InsightApiClient.post` 目前回傳 `ApiResult`，CCB 需確保 token 解析路徑對齊。
  - 「讓 `SnowRealmAuthClient.postAuth` 內部的二次 fetchMe 走 `InsightApiClient`」→ **不得在本輪做**。那會把 auth 內部流程重塑，並可能動到 `postAuth` 行為（S7-2 已明確留為禁區，屬 Sprint 8 bugfix）。
  - 「讓 `InsightApiClient.authClient` 改走 `UserSessionRepository.getAuthToken()`」→ **合法**但面積小，建議附帶在本輪做（讓 gateway 也不再直達 singleton）。
  - 「擴 `InsightApiClient` 支援 streaming 的 `onHeader` callback 或 SSE builder polish」→ 不在本輪必要範圍，建議**不做**，避免面積擴大。

---

### C. 屬於 S7-4 / Sprint 8 的題目（S7-3 不得前移）

| # | 題目 | 所屬 | 原因 |
|---|------|------|------|
| C-1 | `CloudSyncManager` 14 處 prefs 直讀收斂到 Repository／snapshot provider 實作 | `S7-4` | Prefs / Singleton / Mutable Field 真實來源收斂 |
| C-2 | 7 個 subsystem（AdaptiveDict、PersonalDict、PhrasePredict、SentencePredict、StyleProfiler、TypingHabits、CorrectionLearn）內部 pref schema 成為各自 Repository | `S7-4` | 真實 owner 收斂 |
| C-3 | `YukiPreferences` 38 個 field 成為 `SettingsRepository` | `S7-4` | Prefs owner 收斂 |
| C-4 | `UserDictionary` 是否獨立為 Repository | `S7-4` | mutable owner 收斂 |
| C-5 | `postAuth` 雙 fetchMe 合併 | `Sprint 8` | bugfix |
| C-6 | `SnowRealmAuthClient` fetchMe 走 `InsightApiClient` | `Sprint 8` 或後續 round | bugfix／更深度 gateway 統一 |
| C-7 | `AIProviderManager.setSelectedModel` cache 失效 | `Sprint 8` B-4 | bugfix |
| C-8 | `CloudSyncManager.resolveUserId()` 內的 `auth.fetchMe()`（R-1 衍生路徑） | `Sprint 8` | bugfix（避免在 S7-3 gateway 拆分時一併更動此語意） |
| C-9 | `InviteCodeManager` singleton 化 | 後續 round / Sprint 8 | S7-0 邊界裁決 |
| C-10 | `AISettingsActivity` section 化 | 非 Sprint 7 主線 | S7-0 邊界裁決 |
| C-11 | `SettingsActivity` 4 處直接 reach-in | 後續 round / Sprint 8 | 非 S7-3 gateway 題 |
| C-12 | `YukiBoardIMEService` / panel binder | 不在 Sprint 7 任何 round | 固定禁區 |

> 關鍵分界：**「gateway 拆分」≠「prefs 收斂」≠「bugfix」**。S7-3 只負責 gateway。

---

### D. Settings section 層影響判斷

- `AuthSettingsSection` / `SyncSettingsSection` / `AISettingsSection`：**S7-3 不應碰**。gateway 拆分是 `data/` 與 `app/net/` / `app/session/` 層的題目，不影響 section 介面。
- `SettingsScreenCoordinator`：**S7-3 不應碰**。如果 `verifySessionAsync()` 目前走 `UserSessionRepository.verifySession` → `authClient.fetchMe()`，這條路徑本輪保持不變。
- `SettingsActivity`：**S7-3 不應碰**。任何「順手把 SettingsActivity 4 處 reach-in 改走 Repository」的行為屬越界（非 gateway 題）。

---

### E. 具體紅線總結（CCB 實作時不得破壞）

| 紅線 | 違反後果 |
|------|----------|
| 不把 `CloudSyncManager` 14 處 prefs 直讀搬到 Repository / snapshot provider 實作 | 屬 S7-4 |
| 不動 `YukiPreferences` 38 個 field schema 或 keys | 屬 S7-4 |
| 不動 7 個 subsystem 內部 pref schema / file 名 | 屬 S7-4 |
| 不刪 `SnowRealmAuthClient.postAuth` 內部 /me | 屬 Sprint 8 |
| 不改 `CloudSyncManager.resolveUserId()` 的 `auth.fetchMe()` 行為 | 屬 Sprint 8 |
| 不把 `SnowRealmAuthClient` 的 fetchMe 改走 `InsightApiClient` | 屬 Sprint 8 或後續 round |
| 不升 `InviteCodeManager` singleton | S7-0 邊界裁決 |
| 不 section 化 `AISettingsActivity` | S7-0 邊界裁決 |
| 不動 `YukiBoardIMEService.kt` / panel binder | 本輪禁區 |
| 不動 `ApiKeySync` 的 Supabase token 策略（剛在 S7-2 收斂） | S7-2 成果不得回滾 |
| 不動 `SettingsActivity` 4 處 reach-in | 非本輪 |
| 若 `CloudSyncManager.getSupabaseToken()` 走 `InsightApiClient`，必須明示 401 retry 行為 | 行為變更需文件化 |

---

## PART II ── `S7-3-CCA-ACTING-CXB-TECH-REVIEW`（暫代 CodeX B）

### F. Acting CodeX B 說明

- 因 `CodeX B` 額度因素，本輪繼續由 `Claude Code A` 兼任技術 review 角色。
- 本段為「技術審核 / file scope review」視角，與 PART I 的「settings-side 紅線」視角分開。
- 本段不得覆蓋 CodeX A 的最終裁決；僅提供技術可行性分析與 file scope 建議。

---

### G. S7-3 技術範圍（file scope review）

依 Gate §S7-3 允許修改：

- `data/CloudSyncManager.kt`
- `data/InsightApiClient.kt`
- 與 sync / external gateway 直接相關的檔案

#### G-1 建議進入 S7-3 file scope 的檔案

| 檔案 | 角色 | 建議動作 |
|------|------|----------|
| `data/CloudSyncManager.kt` | Sync 主管（同時扮演 gateway + payload builder + prefs reader） | 本輪核心：把「與 Insight 談 Supabase token」的 HTTP 直打改走 `InsightApiClient`；auth 直達 singleton 收到 `UserSessionRepository`；**不動** prefs 直讀 / subsystem schema |
| `data/InsightApiClient.kt` | Insight gateway | 成為 **所有** Insight `/api/**` 進出口的**唯一路徑**；`authClient` 直達改走 `UserSessionRepository.getAuthToken()`；`post` / `get` 可新增 **不自動注入 Authorization** 的 variant（若 exchange 端點語意需要特定 header） |
| `data/SupabaseClient.kt` | Supabase gateway（小） | 可選：小幅整理以確認「token 由 caller 傳入」的契約不變 |
| `app/session/UserSessionRepository.kt` | Session owner | 可微調 KDoc；若需要新增 `getPlatformKey()` 之類的 pass-through（避免 `InsightApiClient` 自己讀 `BuildConfig`），建議**不做**（增大介面面積無實益，`InsightApiClient` 是 Insight 專屬 gateway，自己讀 BuildConfig 合理） |

#### G-2 禁止進入 S7-3 file scope 的檔案

| 檔案 | 原因 |
|------|------|
| `data/CloudSyncManager.kt` 的 14 處 prefs 直讀位置 | 屬 S7-4（同檔允許改 gateway 相關邏輯，但禁改 prefs 直讀位置的 schema / key / subsystem pref file name） |
| `ui/settings/YukiPreferences.kt` | 屬 S7-4 |
| `input/dictionary/UserDictionary.kt` | 屬 S7-4 |
| `input/smart/*`（7 個 subsystem） | 屬 S7-4 |
| `auth/SnowRealmAuthClient.kt` | S7-3 非 auth 收斂 round；`postAuth` 內部 fetchMe 屬 Sprint 8 |
| `auth/InviteCodeManager.kt` | S7-0 邊界裁決 |
| `claw/provider/*` | 非 gateway；與 S7-2 收斂範圍重疊 |
| `claw/provider/ApiKeySync.kt` | S7-2 剛完成，不得回改 |
| `ui/settings/AISettingsActivity.kt` | S7-0 邊界裁決 |
| `ui/settings/SettingsActivity.kt` / section / coordinator | 非 gateway 題 |
| `app/ime/YukiBoardIMEService.kt` | 固定禁區 |
| panel binder 系列 | 固定禁區 |
| Sprint 8 檔 | 本輪禁止 |

#### G-3 邊界檔案（可讀不可大改）

| 檔案 | 說明 |
|------|------|
| `data/SupabaseClient.kt` | gateway；若 `InsightApiClient` 有新的 ApiResult 形態，`CloudSyncManager` 需對齊，SupabaseClient 應不受影響 |
| `app/net/AppHttpClient.kt` | S7-1 單一 owner；若 `InsightApiClient` 內部 client 仍使用，**不動** |
| `app/session/UserSessionRepository.kt` | session owner；若 gateway 要拿 token，建議透過已有 API 取得 |

---

### H. 最小安全收斂策略建議

建議 CCB 依以下順序動，**每一步完成後先在自己的實作文件內勾選**，避免一次面積過大：

1. **`InsightApiClient` 不再直達 `SnowRealmAuthClient`（小）**
   - `private val authClient = SnowRealmAuthClient.getInstance(context)` → 改為持有 `UserSessionRepository(context)` 並呼叫 `getAuthToken()` / `refreshIfNeeded()` 等價方法。
   - ⚠️ 注意：`authClient.refreshIfNeeded()` 目前在 401 retry 用到；`UserSessionRepository` 目前沒有 refresh pass-through。兩種選擇：
     - 為 `UserSessionRepository` 新增 `suspend fun refreshSession(): Boolean`（pass-through 到 `authClient.refreshIfNeeded`），然後 `InsightApiClient` 走這個。
     - 或：`InsightApiClient` 內部仍直接 call `authClient.refreshIfNeeded`；token 取得走 Repository。
   - 建議選第一種（完整收斂）；但**不得**在本輪觸動 `authClient.refreshIfNeeded` 內部的 `fetchMe` 呼叫（那屬 Sprint 8）。

2. **`CloudSyncManager.getSupabaseToken()` 改走 `InsightApiClient.post()`（中）**
   - 把 `httpClient.newCall(…)` + 手工組 Request 的那段，換成 `InsightApiClient.getInstance(context).post("/api/auth/supabase-token", buildJsonObject { })`。
   - ⚠️ 注意：
     - `InsightApiClient.post` 會自動注入 `Authorization: Bearer {token}` + `X-Platform-Key`；原本 `CloudSyncManager` 也加了 `Authorization`，**結果一致**。
     - `InsightApiClient.post` 會做 401 refresh + retry；原本 `CloudSyncManager` 的 exchange 沒做，**這是行為變更（變更寬鬆）**。需在實作文件裡明示「exchange 拿到 401 時會 retry 一次」。
     - Body 從 `ByteArray(0)` 變成 `buildJsonObject {}`.toString()（空 JSON）；Insight 後端若嚴格檢查 body 可能行為不同，需 CCB 實測。如果後端不介意，**保持空 JSON** 比較一致；若介意，可沿用 empty body 並考慮 `InsightApiClient` 加 `postEmpty(path)` variant。
     - Response 解析從 `json.parseToJsonElement(body).jsonObject["token"]` 改為 `(result as? ApiResult.Success)?.data?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull`。
   - `CloudSyncManager` 自身的 token cache（1h）**保留**；gateway 統一的只是「發請求」這件事，不是 cache 生命週期（cache 生命週期屬 S7-4 或後續）。

3. **`CloudSyncManager` auth 直達改走 `UserSessionRepository`（小）**
   - `private val auth = SnowRealmAuthClient.getInstance(context)` → 改為 `UserSessionRepository(context)`。
   - 用 `session.isLoggedIn()` / `session.currentUserId` / `session.getAuthToken()` 替代。
   - ⚠️ 注意：`resolveUserId()` 內的 `auth.fetchMe()` 要怎麼辦？
     - `UserSessionRepository.verifySession` 會打 fetchMe，但它回傳 `VerifiedSession` 不是 userId。
     - 選項 A：**保留** `CloudSyncManager.resolveUserId()` 內直接 call `authClient.fetchMe()`（本輪不碰 R-1 衍生路徑），先透過 Repository 拿 token 和 userId，fetchMe 仍直達 authClient — 但這違反「直達收斂」。
     - 選項 B：為 `UserSessionRepository` 新增 `suspend fun resolveVerifiedUserId(): String?`（pass-through 到 `authClient.fetchMe()?.id ?: authClient.currentUserId`），`CloudSyncManager` 走這個。
     - **建議選項 B**，但要明示「此方法會觸發一次 `/api/auth/me`，是 S7-0 audit R-1 路徑的一部分，Sprint 8 將整併」。
   - 這條路的 ⚠️：若 `UserSessionRepository` 介面擴大了，記得 KDoc 註記「僅供 gateway caller 使用，避免新 caller 走這條」。

4. **`InsightApiClient` 小幅契約明示（可選）**
   - 在 KDoc 補充：「本類是 Insight `/api/**` 的唯一進出口；新 caller 禁止自建 OkHttpClient / 自組 Authorization」。
   - 不必改 API surface。

5. **`CloudSyncManager` KDoc 明示其 dual-role（本輪暫態）**
   - 在 class KDoc 標記：「本類目前同時扮演 gateway + payload builder + prefs reader；S7-3 已把 gateway 職責統一經由 `InsightApiClient` / `SupabaseClient`，payload / prefs 讀取將在 S7-4 拆分到 Repository / snapshot provider」。
   - 純文件，無行為變更。

---

### I. 風險分析（acting tech review）

| 風險 | 等級 | 說明 |
|------|------|------|
| `InsightApiClient` 自動 401 retry 會改變 exchange endpoint 行為 | 中 | 從「401 直接 return null」改為「401 → refresh → retry 一次」；若 Insight 後端在 exchange 端點回 401 代表根本沒 session，retry 也無意義，但不致命。建議在 retry 後仍失敗時返回 null（行為等價） |
| `CloudSyncManager` 走 Repository 後，`resolveUserId()` 的 fetchMe 路徑如何處理 | 中 | 若選擇「Repository 加 `resolveVerifiedUserId`」，Repository 介面會稍微變大；若堅持 session owner 乾淨，`CloudSyncManager` 可**暫時**保留對 `authClient.fetchMe()` 的直達（明示為 R-1 衍生路徑、Sprint 8 將整併），本輪不強制收 |
| `InsightApiClient.post` 的空 body 行為 | 中 | `buildJsonObject {}` vs `ByteArray(0)`：若 Insight 後端對 `Content-Length: 0` 有特別期待，需 CCB 實測 |
| `CloudSyncManager` 同時 dual-role 仍在 | **高** | S7-3 gateway 拆分**只處理 gateway 面**；payload builder / prefs reader 仍同住一個類。此非 regression，但面積壓力被推到 S7-4。建議 CCB 在實作文件裡**明確聲明**這點 |
| CCB 為了「讓 `CloudSyncManager` 瘦身」而把 prefs 直讀搬出 | **極高** | 這是 S7-4 的範圍。本輪若誤觸，必須立刻停下並升到 CodeX A |
| CCB 為了「讓 gateway 統一」而順手改 `SnowRealmAuthClient` 內部 fetchMe 走 InsightApiClient | **極高** | 那會動到 `postAuth` / `fetchMe` 內部流程（S7-2 禁區延續），屬 Sprint 8。本輪不得碰 |
| `SnowRealmProvider`（Insight streaming proxy）若也算 gateway 直達 | 中 | 若 `SnowRealmProvider` 內部仍直接打 Insight `/api/ai/...` 且未走 `InsightApiClient`，本輪**可選**是否一併收進 gateway；但 provider 在 S7-2 邊界已被碰過，**建議不動**，專注在 `CloudSyncManager` + `InsightApiClient` 兩檔完成 gateway 契約 |

---

### J. Acting tech review 結論（不越權 CodeX A 裁決）

- **file scope 評估**：S7-3 的技術範圍合理。核心就是兩件事：
  1. `InsightApiClient` 成為 Insight 唯一 gateway（至少收 `CloudSyncManager.getSupabaseToken`）
  2. `CloudSyncManager` 的 auth 直達改走 `UserSessionRepository`（對齊 S7-2 的方向）
- **建議最小交付物**（純技術視角，不代表 CodeX A 決議）：
  1. `InsightApiClient` 內部 `authClient` 改走 `UserSessionRepository`（配合 Repository 新增 `refreshSession()` pass-through）
  2. `CloudSyncManager.getSupabaseToken()` 改走 `InsightApiClient.post("/api/auth/supabase-token", …)`
  3. `CloudSyncManager` auth 直達 singleton 改走 `UserSessionRepository`（Repository 可選新增 `resolveVerifiedUserId()` 或保留 fetchMe 直達路徑）
  4. `CloudSyncManager` / `InsightApiClient` KDoc 明示 dual-role / 唯一 gateway 契約
- **不建議**塞入本輪的東西：
  - 任何與 `YukiPreferences` / `UserDictionary` / 7 個 subsystem pref schema 相關的改動（屬 S7-4）
  - 任何 `SnowRealmAuthClient` 內部 fetchMe / postAuth 改動（屬 Sprint 8）
  - `ApiKeySync` 回頭動（S7-2 成果）
  - `SnowRealmProvider` gateway 化（建議留後續 round）
- **最終裁決**仍歸 `CodeX A`，本段僅提供技術可行性分析。

---

## PART III ── 回交（A / B / C / D / E）

### A. 變更摘要

- 新增 `docs/SPRINT7_3_CCA_SIDE_NOTE_2026-04-18.md`
- 同時承擔 `S7-3-CCA-SIDE-NOTE` + `S7-3-CCA-ACTING-CXB-TECH-REVIEW` 兩個任務
- 文件結構：
  - PART I：CCA side-note（3 個 gate 要求的盯點 + S7-4/S8 邊界）
  - PART II：Acting CodeX B tech review（file scope + 最小安全收斂 5 步）
  - PART III：本段回交
- 無 `.kt` 變更

### B. 風險

- 若 CCB 的 `S7-3-CCB-SYNC-GATEWAY-IMPLEMENTATION` 觸及 PART I §E 任一紅線，必須立即停下並升到 CodeX A。
- 最大 regression 風險：CCB 若趁 gateway 拆分把 prefs 直讀也「順手」搬走，會把 S7-4 提前到 S7-3，違反 Gate §S7-3 禁令 + CodeX A 邊界裁決。
- 次大風險：`CloudSyncManager.getSupabaseToken()` 改走 `InsightApiClient` 後，exchange 端點遇 401 會觸發 refresh + retry；需 CCB 在實作文件裡明示是否為可接受的行為變更。
- 本輪結束時 `CloudSyncManager` 仍扮演 gateway + payload builder + prefs reader 三種角色；這不是 regression，但面積壓力被推到 S7-4。

### C. 未做

- 不改 `.kt`
- 不處理 C-1 ~ C-12 清單中的任何項目（見 PART I §C）
- 不升 `InviteCodeManager` singleton
- 不 section 化 `AISettingsActivity`
- 不動 `ApiKeySync`（S7-2 成果）
- 不改 `SnowRealmAuthClient` / `AIProviderManager`

### D. 下一步建議

- 等待 CCB 回交 `S7-3-CCB-SYNC-GATEWAY-IMPLEMENTATION`。
- 收到後，Claude Code A 可再出一份 post-implementation confirmation note（與 S7-1 / S7-2 同模式），驗證：
  1. PART I §E 紅線是否全部保留
  2. PART II §G file scope 是否遵守（§G-1 可動、§G-2 禁動、§G-3 小心動）
  3. §H 最小安全收斂策略的實際落地程度
  4. S7-4 preemption 是否有越界
- 若 CCB 實作越界（尤其是 prefs 直讀被搬家），請 CodeX A 擋下並要求縮回。

### E. CodeX A 審核需求

- 請 CodeX A 判定以下四件事：
  1. 本文件的 PART I 紅線是否可作為 CCB 實作的硬性約束
  2. 本文件的 PART II §J 建議最小交付物是否為可接受的 S7-3 範圍
  3. `UserSessionRepository` 可否在 S7-3 新增 `refreshSession()`（pass-through）與 `resolveVerifiedUserId()`（pass-through）
  4. Claude Code A 是否在 CCB 回交後再出一份 post-implementation confirmation note（依 S7-1 / S7-2 先例）
- 判定格式：`READY / NOT READY / LOCKED`
- 若 CodeX A 認為 PART II §J 的候選交付物太大或太小，請明示縮放方向（例如：僅做 InsightApiClient 的 supabase-token gateway 化，`CloudSyncManager` auth 直達暫留）
- Claude Code A 與 Claude Code B 皆不得自行指定 `S7-4`

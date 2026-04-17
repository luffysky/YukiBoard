# Sprint 7-2 CCA Side-Note + Acting CodeX B Tech Review

- 任務 ID：
  - `S7-2-CCA-SIDE-NOTE`
  - `S7-2-CCA-ACTING-CXB-TECH-REVIEW`（暫代 CodeX B 技術審核）
- Owner：Claude Code A
- Type：pre-implementation side-note + acting tech review（在 CCB 的 `S7-2-CCB-SESSION-AUTH-PROVIDER-IMPLEMENTATION` 回交前先給紅線 + file scope 邊界）
- Scope：Session / Auth / Provider 狀態收斂
- Date：2026-04-18

> 本輪規則：
>
> 1. 不改 `.kt`。
> 2. Side-note 視角：盯住 `postAuth` 雙 fetchMe / token 策略分歧 / provider cache / `InviteCodeManager` 非 singleton；屬 `S7-3` / `S8` 的題目只能註記，不得前移。
> 3. Acting CodeX B 視角：給 CCB 明確的 file scope、owner 邊界、最小安全收斂策略。
> 4. 兩個視角在本文件中分段清楚區分，避免混線。

---

## PART I ── `S7-2-CCA-SIDE-NOTE`（Claude Code A 側）

### A. S7-1 完成後的狀態確認

S7-1 已把 26 個 `OkHttpClient.Builder()` callsite 收斂到 `AppHttpClient.newBuilder(Profile)`。回頭檢視後：

- `SnowRealmAuthClient.client` = `AppHttpClient.newBuilder(STANDARD).build()`（單一 caller 端 `.build()`，未共享 instance）
- `CloudSyncManager` / `InsightApiClient` / `ApiKeySync`（經 Supabase / auth client 間接）均透過 `AppHttpClient` 建構
- `AIProviderManager` 透過 provider classes 間接使用 `AppHttpClient`
- `UserSessionRepository` 僅讀（`getSnapshot / verifySession / getApiKey / isLoggedIn / getCurrentUser / getSelectedProvider`），尚未擴展 write API

**結論**：network entry 層已固化。S7-2 的題目落在「token / session / provider state 的 owner 與 flow」，和 network entry 是兩層。

---

### B. 本輪必須盯住的四個點

#### B-1 `SnowRealmAuthClient.postAuth` 雙 fetchMe（R-1 / R-6）

- **現況**：
  - `SnowRealmAuthClient.kt:200-267` 的 `postAuth` 在登入成功後、於內部又打一次 `/api/auth/me`（lines 238-250）。
  - 外部 caller（例如 `SettingsScreenCoordinator.verifySessionAsync()` 內呼叫 `UserSessionRepository.verifySession()` → `authClient.fetchMe()`）之後還會再打一次 `/api/auth/me`。
- **屬性**：bugfix（多一次 round-trip），屬 `Sprint 8`。
- **S7-2 要守的紅線**：
  - 不得在 S7-2 把內部那次 /me 刪掉當成「自然延伸」。
  - 不得把 `postAuth` 與外部 `fetchMe` 合併成一次呼叫。
  - 若 CCB 在 session 收斂時想「順手修掉這個重複」→ 立即停下並升到 CodeX A。
- **可接受**：在 `UserSessionRepository` / `SnowRealmAuthClient` 層面**註記**此 known-inefficiency，並在 Sprint 8 處理。

#### B-2 Token 策略分歧（R-2）

- **現況**：
  - `CloudSyncManager`（`data/CloudSyncManager.kt`）使用 **Insight JWT → Supabase JWT exchange**，cache 1h，供 Supabase REST 使用。
  - `ApiKeySync`（`claw/provider/ApiKeySync.kt`）使用 **Insight JWT 直接**當 Supabase Authorization，不走 exchange。
- **屬性**：
  - 「兩條分歧的 Supabase 認證策略並存」本身是 session / auth token flow 的議題，**屬於 S7-2 範圍**（session / auth / provider 狀態收斂）。
  - 但「對齊策略」的**實作位置**有爭議：
    - 若把統一策略塞進 `CloudSyncManager` → 碰到 `S7-3 CloudSyncManager gateway 拆分` 的範圍（不得前移）。
    - 若把統一策略塞進 `ApiKeySync` 或 auth 層 → 屬本輪合法。
- **S7-2 本輪建議**：
  - 可以在 `auth/` 側或 `claw/provider/` 側建立 **統一的 Supabase token 取得介面**（例如 `SupabaseAuthTokenProvider`），但不得改 `CloudSyncManager` 自有的 exchange / cache 實作（那是 S7-3 gateway 拆分時一併重塑）。
  - 可以先讓 `ApiKeySync` 透過該介面取得 token（不再直接用 Insight JWT），以對齊策略。
  - 不得把 `CloudSyncManager` 的 exchange cache 搬出去（那動的是 gateway 結構）。
- **紅線**：
  - 不得在 S7-2 把 `CloudSyncManager` 的 supabase-token exchange 邏輯「搬到」新 owner。只能建立新介面讓 `ApiKeySync` 靠過去，`CloudSyncManager` 維持現狀由 S7-3 處理。
  - 若介面建完後 `CloudSyncManager` 還是走自己的，**留在文件註記即可，不得 S7-2 去「順便改」**。

#### B-3 `AIProviderManager` provider cache 失效時機（R-5 / B-4）

- **現況**：
  - `claw/provider/AIProviderManager.kt`：`saveApiKey` 會 invalidate `providerCache`，但 `setSelectedModel` 不會。
  - 結果：切 model 後，cached provider 可能帶舊 model 設定繼續服務。
- **屬性**：provider state owner 的 cache 生命週期議題。
  - 「讓 provider cache 在 API key 變動時失效」本身已是既有行為。
  - 「setSelectedModel 也該 invalidate（若 cache 以 model 為 key 一部分）」是 **bugfix**。
- **S7-2 可做**：
  - 將 provider cache 生命週期**形式化**到 `AIProviderManager` 的介面註解裡（documenting the contract），作為 owner 收斂的一部分。
  - 若 provider cache 的 key schema 顯示「不含 model」，則對齊行為（model 變動時也失效）屬 Sprint 8 B-4，不在 S7-2。
  - 若 CCB 要改 cache 生命週期，必須**先在文件中明示**這是 contract 變更，並提供 before/after 行為。
- **紅線**：
  - 不得在 S7-2「順手」改 cache 生命週期而不標示。
  - 不得把 provider cache 搬到其他 repository。

#### B-4 `InviteCodeManager` 非 singleton 現狀（B-5）

- **現況**：
  - `auth/InviteCodeManager.kt` 不是 singleton；5 處 `new InviteCodeManager(context)`：
    - `claw/ClawClient.kt`
    - `claw/provider/SnowRealmProvider.kt`
    - （weather/sticker/invite 相關 UI）
    - `ui/invite/InviteCodeActivity.kt`
    - `ui/settings/AISettingsActivity.kt`
  - `@Synchronized consumeTokens` 只鎖同一 instance → 跨 instance race。
- **屬性**：CodeX A 在 S7-0 邊界裁決已明確：「`InviteCodeManager` 不在 `S7-1` 直接升 singleton；先維持 audit finding，除非後續 round 證明它是阻塞 integration 收斂的必要前提」。
- **S7-2 要守的紅線**：
  - **不得在 S7-2 把 `InviteCodeManager` 升 singleton**（即使看起來像 session 收斂的自然延伸）。
  - 若 S7-2 的 session / provider owner 需要 invite code token 資料 → 透過「caller 傳入 invite code 快照」或「在 owner 介面註記 invite code 依賴」，不要 singleton 化。
  - 若 S7-2 無法避開這個前提才能完成 session 收斂 → 立即停下，文件註記，升到 CodeX A 裁決。

---

### C. 屬於 S7-3 / Sprint 8 的題目（S7-2 不得前移）

| # | 題目 | 所屬 | 原因 |
|---|------|------|------|
| C-1 | `CloudSyncManager` gateway 拆分 | `S7-3` | S7-0 邊界裁決 |
| C-2 | `CloudSyncManager` 14 處 prefs 直讀收斂 | `S7-3` / `S7-4` | prefs 真實來源議題 |
| C-3 | `AISettingsActivity` section 化 | 非 Sprint 7 主線 | S7-0 邊界裁決 |
| C-4 | `InviteCodeManager` singleton 化 | 後續 round / Sprint 8 | S7-0 邊界裁決 |
| C-5 | `postAuth` 雙 fetchMe 合併 | `Sprint 8` B-？ | bugfix |
| C-6 | `AIProviderManager.setSelectedModel` 失效 cache | `Sprint 8` B-4 | bugfix（若 contract 要求） |
| C-7 | SettingsActivity 4 處直接 reach-in | 後續 round / Sprint 8 | 非 S7-2 session 題 |
| C-8 | `YukiBoardIMEService` / panel binder | 不在 Sprint 7 任何 round | 固定禁區 |

---

### D. Settings section 層影響判斷

- `AuthSettingsSection` / `SyncSettingsSection` / `AISettingsSection`（Sprint 1 已 constructor DI 好）：
  - 這三個 section 目前拿的是 `UserSessionRepository` / `CloudSyncManager` / `AIProviderManager`。
  - S7-2 若讓 `UserSessionRepository` 擴展 write API（例如 `loginAsEmail / logout / refreshSession`），**section 可以改走 repository 新 API**，但本輪的 `.kt` 改動應盡量限制在 `session / auth / provider` owner 本體。
  - 若 section 裡目前有「直接呼叫 `authClient.logout()` / `providerManager.setSelectedProvider()`」這類繞過 repository 的路徑 → 可在本輪收斂，**但請先在本 side-note 確認後再動**。
  - 若 CCB 實作會要求改 section constructor DI（例如新增 `SessionCoordinator`）→ 必須先在回交文件聲明，CodeX A 裁決。

---

### E. 具體紅線總結（CCB 實作時不得破壞）

| 紅線 | 違反後果 |
|------|----------|
| 不刪 `SnowRealmAuthClient.postAuth` 內部 /me | 屬 Sprint 8 bugfix |
| 不搬 `CloudSyncManager` supabase-token exchange 結構 | 屬 S7-3 |
| 不改 `CloudSyncManager` 14 處 prefs 直讀 | 屬 S7-3 / S7-4 |
| 不升 `InviteCodeManager` singleton | S7-0 邊界裁決 |
| 不 section 化 `AISettingsActivity` | S7-0 邊界裁決 |
| 不動 `YukiBoardIMEService.kt` / panel binder | 本輪禁區 |
| `AIProviderManager` provider cache 生命週期若更動必須文件化 | contract 變更需透明 |
| 不把 `SettingsActivity` 4 處 reach-in 順手收掉 | 不在本輪範圍 |

---

## PART II ── `S7-2-CCA-ACTING-CXB-TECH-REVIEW`（暫代 CodeX B）

### F. Acting CodeX B 說明

- 因 `CodeX B` 額度因素，本輪起暫由 `Claude Code A` 兼任技術 review 角色。
- 本段為「技術審核 / file scope review」視角，與 PART I 的「settings-side 紅線」視角分開。
- 本段不得覆蓋 CodeX A 的最終裁決；僅提供技術可行性分析與 file scope 建議。

---

### G. S7-2 技術範圍（file scope review）

依 Gate §S7-2 允許修改：

- `auth/`
- `claw/provider/`
- 與 session / auth / provider 狀態來源直接相關的檔案

#### G-1 建議進入 S7-2 file scope 的檔案

| 檔案 | 角色 | 建議動作 |
|------|------|----------|
| `auth/SnowRealmAuthClient.kt` | JWT / token / user profile owner | 可微調介面、可抽出 `SupabaseAuthTokenProvider` 介面，但不動 postAuth 結構、不動 EncryptedSharedPreferences schema |
| `auth/InviteCodeManager.kt` | invite code state | **只允許讀介面最小調整**，不得 singleton 化 |
| `claw/provider/AIProviderManager.kt` | provider selection + API key + cache | 可整理 owner 介面、可明示 contract；cache 生命週期若動必須文件化 |
| `claw/provider/ApiKeySync.kt` | API key sync | 可改為透過統一 Supabase token 介面取得 token（替代直接用 Insight JWT） |
| `claw/provider/OpenAIProvider.kt` / `AnthropicProvider.kt` / `GeminiProvider.kt` / `GroqProvider.kt` / `SnowRealmProvider.kt` / `GeminiNanoProvider.kt` / `OllamaProvider.kt` / `LocalProvider.kt` | provider impl | 可調整取 token / API key 的路徑（改走 owner 介面），但不動 network entry / payload 組裝 |
| `app/session/UserSessionRepository.kt` | session 讀取邊界 | **可擴展 write API**（login / logout / refresh / switch provider 等），但 repository 本身仍以 `SnowRealmAuthClient` + `AIProviderManager` 為底 |

#### G-2 禁止進入 S7-2 file scope 的檔案

| 檔案 | 原因 |
|------|------|
| `data/CloudSyncManager.kt` | gateway 結構屬 S7-3（註：S7-1 已替換 Builder，本輪不得再動） |
| `data/InsightApiClient.kt` | gateway 級 caller；本輪屬 session / auth owner 層，不是 gateway |
| `data/SupabaseClient.kt` | 同上 |
| `ui/settings/AISettingsActivity.kt` | 不 section 化 |
| `app/ime/YukiBoardIMEService.kt` | 固定禁區 |
| panel binder 系列 | 固定禁區 |

#### G-3 邊界檔案（可讀不可大改）

| 檔案 | 說明 |
|------|------|
| `app/settings/SettingsScreenCoordinator.kt` | coordinator；若 repository 新增 write API，coordinator 可能要跟著呼叫，但介面層級變更需文件化 |
| `ui/settings/section/AuthSettingsSection.kt` / `SyncSettingsSection.kt` / `AISettingsSection.kt` | section；若從繞過 repository 改走 repository 新 API，可修正（最小改動） |
| `claw/ClawClient.kt` | 目前持有 `InviteCodeManager` new 實例；若 provider 的 token path 變更，此 caller 可能受影響，需留意 |

---

### H. 最小安全收斂策略建議

建議 CCB 依以下順序動：

1. **先定介面，不動行為**
   - 在 `auth/` 或 `app/session/` 新增 `SupabaseAuthTokenProvider` 介面（抽象「如何從 Insight JWT 拿到可用於 Supabase 的 token」）。
   - **不動** `CloudSyncManager` 自有 exchange cache（屬 S7-3）。
   - **不動** `AIProviderManager` provider cache 行為（屬 Sprint 8）。

2. **`UserSessionRepository` 擴展 write API（可選）**
   - 建議介面：`login(email, password) / loginWithOAuth / register / logout / refreshSession / setSelectedProvider / setSelectedModel / saveApiKey`。
   - 所有 write 都委派給 `SnowRealmAuthClient` / `AIProviderManager`（現行行為），不改變副作用邊界。
   - **好處**：section 不再直接碰 `SnowRealmAuthClient.getInstance()` / `AIProviderManager.getInstance()`，對應到 CLAUDE.md 「新代碼走 Repository 封裝」的方向。

3. **`ApiKeySync` 替換 token 取得路徑**
   - 從「直接用 Insight JWT 當 Supabase Authorization」改為「透過 `SupabaseAuthTokenProvider` 取得」。
   - `CloudSyncManager` 暫不動（其自有 cache 屬 S7-3）。
   - 結果：S7-2 結束時，`ApiKeySync` 對齊到介面；`CloudSyncManager` 仍在舊路徑，S7-3 再收。

4. **`AIProviderManager` 介面註解與契約固化**
   - 將 provider cache 生命週期寫入 KDoc。
   - 若要改 `setSelectedModel` 的 cache 行為 → **明示 contract change** + before/after 行為。

5. **Provider 類 token 取得路徑**
   - `SnowRealmProvider`：透過 `SnowRealmAuthClient` 拿 token + `InviteCodeManager` 拿 invite token 的路徑不動；只替換為「透過 repository 介面取得」。
   - `OpenAI / Anthropic / Gemini / Groq`：從 `AIProviderManager.getApiKey(type)` 取 API key 的路徑不動；僅在介面層可 polish。
   - `Ollama / LocalProvider / GeminiNanoProvider`：無 token，無需動。

---

### I. 風險分析（acting tech review）

| 風險 | 等級 | 說明 |
|------|------|------|
| `UserSessionRepository` 擴寫 API → section 跟著改 | 中 | section 改動需在同一輪內保持原行為；建議先只擴 repository，section 下一輪再改（避免本輪面積過大） |
| `SupabaseAuthTokenProvider` 介面 + `ApiKeySync` 靠過去 | 低 | `ApiKeySync` 是 object + 少量 callsite，替換成本低 |
| `AIProviderManager` contract 固化 | 低 | 純文件 / KDoc 動作，無行為變更風險 |
| `InviteCodeManager` 若任何動作 | **高** | 本輪禁止 singleton，但即使是最小介面調整也要極度保守；建議**本輪完全不動**，等 CCB 遇到不得不改才停下請示 |
| `CloudSyncManager` 誤觸 | **高** | 本輪禁區；**建議 CCB 宣告「S7-2 零 CloudSyncManager 改動」** |
| `postAuth` 結構 | **高** | 本輪禁改；若 CCB 為了 repository write API 想重構 postAuth，請止步 |

---

### J. Acting tech review 結論（不越權 CodeX A 裁決）

- **file scope 評估**：S7-2 的技術範圍合理，風險可控，前提是 PART I 的紅線與本段 G-2 禁區完整保留。
- **建議最小交付物**（純技術視角，不代表 CodeX A 決議）：
  1. `SupabaseAuthTokenProvider` 介面（auth 層新增）
  2. `ApiKeySync` 替換 token 取得路徑
  3. `UserSessionRepository` 擴展 write API（可選；若面積太大可拆出 S7-2.1）
  4. `AIProviderManager` KDoc 契約固化（純文件）
  5. provider 類 token 取得路徑小幅 polish（可選）
- **不建議**塞入本輪的東西：任何與 `CloudSyncManager` / `InviteCodeManager` / `postAuth` / `AISettingsActivity` / IME 相關的改動。
- **最終裁決**仍歸 `CodeX A`，本段僅提供技術可行性分析。

---

## PART III ── 回交（A / B / C / D / E）

### A. 變更摘要

- 新增 `docs/SPRINT7_2_CCA_SIDE_NOTE_2026-04-18.md`
- 同時承擔 `S7-2-CCA-SIDE-NOTE` + `S7-2-CCA-ACTING-CXB-TECH-REVIEW` 兩個任務
- 文件結構：
  - PART I：CCA side-note（設定側紅線 + S7-3/S8 邊界）
  - PART II：Acting CodeX B tech review（file scope 與最小安全收斂策略）
  - PART III：本段回交
- 無 `.kt` 變更

### B. 風險

- 若 CCB 的 `S7-2-CCB-SESSION-AUTH-PROVIDER-IMPLEMENTATION` 觸及 PART I §E 任一紅線，必須立即停下並升到 CodeX A。
- 「`ApiKeySync` 對齊介面、`CloudSyncManager` 暫不動」會在 S7-2 結束時留下「兩條 Supabase token 路徑仍並存」的過渡態；此過渡態屬 S7-3 目標，**本輪不視為 regression**。
- 如果 CCB 實作面積超過 PART II §J 建議（例如連 section 跟 coordinator 也一起改），面積擴大會讓 review 變重；建議最小化。

### C. 未做

- 不改 `.kt`
- 不碰 `CloudSyncManager` / `InsightApiClient` / `SupabaseClient`（屬 S7-3）
- 不處理 R-1 / R-6 `postAuth` 雙 fetchMe（屬 Sprint 8）
- 不處理 R-5 / B-4 `AIProviderManager.setSelectedModel` cache 失效（屬 Sprint 8）
- 不升 `InviteCodeManager` singleton
- 不 section 化 `AISettingsActivity`
- 不動 `SettingsActivity` 4 處 reach-in

### D. 下一步建議

- 等待 CCB 回交 `S7-2-CCB-SESSION-AUTH-PROVIDER-IMPLEMENTATION`。
- 收到後，Claude Code A 可再出一份 post-implementation confirmation note（與 S7-1 同模式），驗證：
  1. PART I §E 紅線是否全部保留
  2. PART II §G 的 file scope 是否遵守（§G-1 可動、§G-2 禁動、§G-3 小心動）
  3. §H 最小安全收斂策略的實際落地程度
- 若 CCB 實作越界，請 CodeX A 擋下並要求縮回。

### E. CodeX A 審核需求

- 請 CodeX A 判定以下三件事：
  1. 本文件的 PART I 紅線是否可作為 CCB 實作的硬性約束
  2. 本文件的 PART II `SupabaseAuthTokenProvider` 介面建議是否可作為 S7-2 技術方向
  3. Claude Code A 是否在 CCB 回交後再出一份 post-implementation confirmation note（依 S7-1 先例）
- 判定格式：`READY / NOT READY / LOCKED`
- 若 CodeX A 認為 PART II §J 的候選交付物太大或太小，請明示縮放方向（例如：僅做介面 + ApiKeySync 替換，repository write API 延後）

# Sprint 7-1 CCA Network Entry Side-Note

- Task ID：`S7-1-CCA-NETWORK-ENTRY-SIDE-NOTE`
- Owner：Claude Code A
- Type：pre-implementation side-note（在 CCB 的 `S7-1-CCB-NETWORK-ENTRY-IMPLEMENTATION` 回交前，先給紅線）
- Scope：`settings / auth / provider / sync` 側 side effect 審核
- Date：2026-04-18

> 本輪規則：不改 `.kt`。若發現 session / auth / provider state owner 會被碰到，只能文件註記，不得提前擴到 `S7-2`。

---

## A. 本輪 S7-1 的「網路 entry 收斂」對 settings / auth / provider / sync 側的影響面

S7-0 已盤點出 settings / auth / provider / sync 側共 10 處會進入 S7-1 file scope 的 `OkHttpClient.Builder()` callsite：

| # | File | Callsite 摘要 | 與 session/auth/provider state 是否糾纏 | S7-1 是否可直接收斂到 `AppHttpClient` |
|---|------|---------------|------------------------------------------|----------------------------------------|
| 1 | `auth/SnowRealmAuthClient.kt` | JWT login / refresh / me / register 的 client | **高度糾纏**（擁有 refresh token、EncryptedSharedPreferences、getInstance singleton） | 僅允許替換「OkHttp 建構來源」，不得改 postAuth 的雙 fetchMe 行為、不得把 token 來源抽走 |
| 2 | `data/InsightApiClient.kt` | Insight gateway（Authorization: Bearer + X-Platform-Key） | 中度糾纏（401 refresh 流程要求 `SnowRealmAuthClient` 可 refresh） | 可收斂 builder；401 retry 行為必須 byte-for-byte 保留 |
| 3 | `data/SupabaseClient.kt` | Supabase REST（select/insert/update/rpc） | 中度糾纏（需帶 token，但本類自己不決定 token 來源） | 可收斂 builder；token header 組裝路徑不可改 |
| 4 | `data/CloudSyncManager.kt` | 自有 Insight→Supabase token exchange client | **高度糾纏**（自有 token cache、與 (3) 共用 Supabase URL 卻走不同 auth 策略 R-2） | 僅允許收斂 builder；token exchange / cache TTL / 14 處 prefs 直讀**一律不動** |
| 5 | `claw/provider/ApiKeySync.kt` | AES-256-CBC key sync，使用 Insight JWT 直接當 Supabase Authorization | **高度糾纏**（token 策略與 (4) 分歧 R-2） | 僅允許 builder 收斂；不得「順手」把 token 策略對齊（Sprint 8 B-？） |
| 6 | `claw/provider/OpenAIProvider.kt` | OpenAI chat/completion | 低糾纏（API key 取自 `AIProviderManager`，非 session） | 可收斂 builder；API key provider 取得介面不可變 |
| 7 | `claw/provider/AnthropicProvider.kt` | Anthropic messages | 低糾纏（同上） | 同上 |
| 8 | `claw/provider/SnowRealmProvider.kt` | Insight 代理 provider | 中度糾纏（需透過 `SnowRealmAuthClient` 拿 token、使用 `InviteCodeManager`） | 可收斂 builder；token / invite code 取得路徑不可變 |
| 9 | `claw/provider/OllamaProvider.kt` / `claw/provider/LocalProvider.kt` | 本地 / Ollama | 無糾纏 | 可收斂 builder |
| 10 | 其它次級 caller（e.g. weather / sticker / gif / speech 只要在 settings 勾到就掃到） | 各自 client | 無糾纏 | 可收斂 builder |

> 上表是「side effect 審核」，不是實作指令。實際 file scope 以 CCB 的 `S7-1-CCB-NETWORK-ENTRY-IMPLEMENTATION` 為準，本文件只負責紅線。

---

## B. S7-1 本輪必須保留的 invariant（Claude Code B 實作時不得破壞）

### B-1 `SnowRealmAuthClient.postAuth()` 的雙 fetchMe 行為

- 現況：login/refresh 後內部打一次 `/api/auth/me`，之後呼叫端仍會再打一次 `fetchMe()`。
- 這是 S7-0 audit 標記的 **R-1 / R-6 疑似冗餘**。
- **S7-1 不處理**。僅允許把 OkHttp builder 替換成 `AppHttpClient`，不得因「既然在收斂就順手修」而刪任何一次 fetchMe call。
- 若 CCB 想修這個，請改成 Sprint 8 bugfix 題。

### B-2 Token 策略分歧（R-2）不得在 S7-1 對齊

- 現況：
  - `CloudSyncManager`：Insight JWT → Supabase JWT exchange，cache 1h，供 Supabase REST 使用。
  - `ApiKeySync`：Insight JWT **直接**當 Supabase Authorization，不走 exchange。
- **S7-1 不處理**。這是 session/auth state owner 層級的議題，要留到 `S7-2 Session / Auth / Provider 狀態收斂`。
- S7-1 只替換 client builder 來源，不得統一 token 取得策略。

### B-3 `AIProviderManager` provider cache 失效時機（R-5 / B-4）

- 現況：`saveApiKey` 會 invalidate `providerCache`，但 `setSelectedModel` 不會。
- **S7-1 不處理**。cache 是 provider state owner 的題目，屬 `S7-2` or Sprint 8 B-4。
- S7-1 只替換 provider 類內部的 OkHttp builder 來源。

### B-4 `InviteCodeManager` 非 singleton / 5 處 `new` 的問題（B-5）

- S7-0 audit 已明確記錄：`@Synchronized consumeTokens` 只鎖同一實例，跨 instance 有 race。
- **S7-1 不處理**。這是 owner 層議題，依 S7-0 邊界裁決屬 Sprint 8 / 後續 round。
- S7-1 僅可替換 OkHttp builder；若 CCB 的 AppHttpClient 需要拿 invite code header，請改以「由 caller 傳入」的方式，不要把 `InviteCodeManager` singleton 化當成前置條件。

### B-5 `CloudSyncManager` 的 14 處 prefs 直讀（SSOT 違反）

- 現況：`CloudSyncManager` 直接 read 7 個子系統 prefs、38 個 YukiPreferences field key。
- **S7-1 不處理**。這是 `S7-3 Sync / External Gateway 收斂` 的題目，已由 S7-0 邊界裁決鎖定。
- S7-1 僅替換 `CloudSyncManager` 內部的 OkHttp builder；不得拆 gateway / snapshot provider。

### B-6 `AISettingsActivity` 不 section 化（S7-0 邊界裁決）

- 確認：S7-1 不碰 `AISettingsActivity`。
- 若 CCB 發現 `AISettingsActivity` 裡有自建 OkHttp（S7-0 audit 顯示 settings 直建相對罕見），僅允許替換 client builder，不得趁機 section 化。

### B-7 `ImeClawPanelBinder.kt` 不得動

- 確認：已於 Sprint 6-6 完成，嚴格不越界成 Sprint 7 integration 收斂。
- S7-1 的 `AppHttpClient` 不應在此檔案出現；panel binder 只接受已建構好的 `ClawClient?`。

---

## C. S7-1 可安全收斂的邊界（從 settings / auth / provider / sync 視角）

以下是 S7-1 **可以動** 的最小操作集合：

1. 把 (1)~(10) 的 `OkHttpClient.Builder()` 改為向 `AppHttpClient` 取得 `OkHttpClient`，或由 `AppHttpClient` 提供共用 builder template。
2. 允許調整 timeout 取得方式（例如「由 `AppHttpClient` 提供預設 + 各 caller 自行 copy 後覆寫」），只要最終 behavior（timeout 秒數、interceptor 順序）不變。
3. 允許把 base URL 的「來源」收斂到單一入口（例如 `AppHttpClient` 或 `AppNetworkEndpoints`），只要「值」不變：
   - `BuildConfig.INSIGHT_API_BASE_URL`（預設 `https://insight.snowrealm.pet`）
   - `BuildConfig.YUKIBOARD_SUPABASE_URL`
   - provider 各自的官方 base URL
4. 允許把 header 組裝（`X-Platform-Key`、`Authorization: Bearer`）收斂到 interceptor，但前提是「token 從哪裡來」不變：
   - Insight 向 `SnowRealmAuthClient` 取
   - Supabase（`CloudSyncManager` 路徑）向其自有 exchange cache 取
   - Supabase（`ApiKeySync` 路徑）向 `SnowRealmAuthClient` 取 Insight JWT 直用
   - provider API key 向 `AIProviderManager` 取

---

## D. S7-2 preemption 風險清單（S7-1 實作時必須避免的越界）

以下行為看起來像是 S7-1 的自然延伸，**實際上是 S7-2 的題目**，必須被 CCB 避開：

| D-# | 看似自然的延伸 | 為什麼是 S7-2 越界 |
|-----|---------------|---------------------|
| D-1 | 讓 `AppHttpClient` 接受 `authTokenProvider: () -> String?` 作為 constructor 參數 | 這創造了一份新的「session contract」，屬 `S7-2 Session / Auth / Provider 狀態收斂` owner decision |
| D-2 | 讓 `AppHttpClient` 直接依賴 `UserSessionRepository` | 讓 network layer 知道 session owner = session owner 設計決策，屬 S7-2 |
| D-3 | 讓 `AppHttpClient` 自帶 401 refresh | `InsightApiClient` 的 401 + refresh + retry 目前是整包自己處理，抽到 `AppHttpClient` 意味把 auth 生命週期下放給 network entry，屬 S7-2 |
| D-4 | 統一 `ApiKeySync` 與 `CloudSyncManager` 的 Supabase token 策略 | 這是 R-2 的修法，屬 S7-2 或 Sprint 8 |
| D-5 | 讓 `AppHttpClient` 擁有 `InviteCodeManager` 實例並自動塞 invite code header | invite code 是 auth-adjacent owner state，屬後續 round |
| D-6 | 刪除 `SnowRealmAuthClient.postAuth` 內的冗餘 fetchMe | Sprint 8 bugfix 題，S7-1 不處理 |
| D-7 | 把 `AIProviderManager` 的 provider cache 生命週期綁到 `AppHttpClient`（例如「client 換 → cache 清」） | provider state owner 題，屬 S7-2 |
| D-8 | 收斂 `CloudSyncManager` 自有 Supabase token exchange 到共享 auth 層 | `S7-3` 的題目 |

> 原則：S7-1 只收斂「怎麼建 `OkHttpClient`」和「請求從哪個 entry 出去」。只要 CCB 的實作會讓 `AppHttpClient` 看到「token 從誰來」或「session 何時失效」，那就是越界。

---

## E. Settings section 層影響判斷

- `AuthSettingsSection` / `SyncSettingsSection` / `AISettingsSection`（Sprint 1 已 constructor DI 好）：
  - 這三個 section **不直接建構 `OkHttpClient`**，只透過 `UserSessionRepository` / `CloudSyncManager` / `AIProviderManager` 取資料。
  - S7-1 不需要動這三個 section 的 constructor 或 DI。
- `SettingsScreenCoordinator`：
  - 不 own OkHttp；只 own 三個 repository / manager。
  - S7-1 不動。
- `SettingsActivity`（4 處直接 reach-in）：
  - S7-0 audit 已標記為後續 round（不在 S7-1 範圍）。
  - 僅當 SettingsActivity 有**自建 OkHttp** 的情況才會被 S7-1 掃到；目前 audit 未發現這種 callsite。

**結論**：Settings section / coordinator / activity 層在 S7-1 不應有任何 `.kt` 變更。如果 CCB 的實作會要求改 `SettingsActivity.kt` 或任何 section，這就是越界，需立即停下並升到 CodeX A 裁決。

---

## F. 對 IME 側（CCB 側 B）的銜接說明

- `ImeClawPanelBinder.kt`：Sprint 6-6 deliverable，**嚴格不動**。
- `YukiBoardIMEService.kt`：S7-1 禁區（CodeX B integration audit 已註明）。
- 若 S7-1 的 `AppHttpClient` 需要在 IME 側被使用，請透過現有的 `ClawClient` / provider 層，不得在 IME service / panel binder 中新增 network owner。

---

## G. 回交格式（A/B/C/D/E）

### A. 變更摘要

- 新增 `docs/SPRINT7_1_CCA_NETWORK_ENTRY_SIDE_NOTE_2026-04-18.md`
- 本文件為 `S7-1-CCA-NETWORK-ENTRY-SIDE-NOTE` 的 pre-implementation side-note
- 無 `.kt` 變更

### B. 風險

- 若 CCB 的 `S7-1-CCB-NETWORK-ENTRY-IMPLEMENTATION` 觸及本文件 §B 或 §D 任一項 invariant，必須立即停下並升到 CodeX A 裁決。
- `CloudSyncManager` 的 token exchange / prefs 直讀仍是最大 SSOT 違反點，但本輪不處理，風險延後到 `S7-3`。

### C. 未做

- 不改 `.kt`。
- 不處理 R-1 / R-2 / R-5 / R-6 / B-4 / B-5 / B-7（全部延後到 S7-2 或 Sprint 8）。
- 不動 Settings section / coordinator / activity / panel binder。

### D. 下一步建議

- 等待 CCB 回交 `S7-1-CCB-NETWORK-ENTRY-IMPLEMENTATION`。
- 收到後，Claude Code A 可再出一份 post-implementation confirmation note，確認：
  1. §B invariant 是否被保留
  2. §D preemption 是否有越界
  3. §E Settings section 層是否零動
- 若 CCB 實作有越界，請 CodeX A 擋下並要求縮回。

### E. CodeX A 審核需求

- 請 CodeX A 判定：
  1. 本 side-note 的 §B / §C / §D 紅線是否可作為 CCB 實作的硬性約束
  2. 本 side-note 是否可在 CCB 回交前作為「事前紅線」生效
  3. Claude Code A 是否在 CCB 回交後再出一份 post-implementation confirmation note
- 判定格式：`READY / NOT READY / LOCKED`

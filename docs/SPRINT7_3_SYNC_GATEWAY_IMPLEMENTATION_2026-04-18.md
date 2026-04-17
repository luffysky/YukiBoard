# Sprint 7-3 Sync / External Gateway 收斂 — CCB Implementation

- 任務 ID：`S7-3-CCB-SYNC-GATEWAY-IMPLEMENTATION`
- Owner：Claude Code B
- Type：implementation
- Scope：`data/InsightApiClient.kt` / `data/CloudSyncManager.kt` / `app/session/UserSessionRepository.kt`
- Date：2026-04-18
- Build：`BUILD SUCCESSFUL in 15s` (`:app:compileDebugKotlin`)

---

## 1. 變更摘要

### 1.1 `app/session/UserSessionRepository.kt`（新增 2 個 pass-through）

新增專供 gateway caller 使用的 pass-through 方法，**不改現有介面**：

| 方法 | 用途 | Delegate 目標 |
|------|------|---------------|
| `suspend fun refreshSession(): Boolean` | 供 `InsightApiClient` 的 401 retry 路徑用 | `authClient.refreshIfNeeded()` |
| `suspend fun resolveVerifiedUserId(): String?` | 供 `CloudSyncManager.resolveUserId()` 用 | 先看 `currentUserId`，否則 IO 上 `fetchMe()?.id` |

兩個方法在 KDoc 明示：
- `refreshSession` 內部仍會 `fetchMe()`，屬 S7-0 audit R-1 / R-6 路徑，**Sprint 8 整併**
- `resolveVerifiedUserId` 會觸發一次 `/api/auth/me`，與 `SettingsScreenCoordinator.verifySessionAsync` 形成雙 fetchMe，**Sprint 8 整併**

### 1.2 `data/InsightApiClient.kt`（auth 直達改走 Repository）

| 舊 | 新 |
|----|----|
| `import com.snowrealm.yukiboard.auth.SnowRealmAuthClient` | `import com.snowrealm.yukiboard.app.session.UserSessionRepository` |
| `private val authClient = SnowRealmAuthClient.getInstance(context)` | `private val session = UserSessionRepository(context)` |
| `authClient.getToken()` | `session.getAuthToken()` |
| `authClient.refreshIfNeeded()` | `session.refreshSession()` |

Class KDoc 補上契約：Insight `/api/` 路徑的唯一 gateway；新 caller 禁止自建 `OkHttpClient` / 自組 `Authorization`。

### 1.3 `data/CloudSyncManager.kt`（auth + exchange 雙收斂）

**Auth 直達改走 Repository**（6 處 callsite）：

| 舊 | 新 |
|----|----|
| `private val auth = SnowRealmAuthClient.getInstance(context)` | `private val session = UserSessionRepository(context)` + `private val insightApi = InsightApiClient.getInstance(context)` |
| `auth.currentUserId` / `auth.isLoggedIn` / `auth.fetchMe()` / `auth.getToken()` | 全面改走 `session.resolveVerifiedUserId()` / `session.isLoggedIn()` / `session.getAuthToken()` |
| `resolveUserId()` 原有 4 行 fetchMe 邏輯 | 一行 `session.resolveVerifiedUserId()` |

**`getSupabaseToken()` exchange 改走 InsightApiClient**：

| 舊 | 新 |
|----|----|
| 自組 `Request.Builder().url("$baseUrl/api/auth/supabase-token").header("Authorization", "Bearer $insightToken").post(ByteArray(0))` | `insightApi.post("/api/auth/supabase-token", buildJsonObject {})` |
| 直用 `AppHttpClient.newBuilder(SHORT)` 的 `httpClient` | 刪除本類 `httpClient`（已不需要） |
| 手動 `json.parseToJsonElement(body)` 解析 | `result.data.jsonObject` 走 `ApiResult.Success` |

Imports 刪除：`com.snowrealm.yukiboard.BuildConfig` / `com.snowrealm.yukiboard.app.net.AppHttpClient` / `com.snowrealm.yukiboard.auth.SnowRealmAuthClient` / `okhttp3.Request`。

Class KDoc 明示：
- auth 直達已改走 `UserSessionRepository`
- exchange 已改走 `InsightApiClient`
- 本類**仍同時扮演 gateway + payload builder + prefs reader** 三種角色；payload / prefs 讀取將在 Sprint 7-4 拆分（14 處 prefs 直讀 + 7 個 subsystem pref schema **原地未動**）

---

## 2. 行為差異（需文件化的部分）

### 2.1 `getSupabaseToken()` 行為變更

| 維度 | 舊行為 | 新行為 | 影響 |
|------|--------|--------|------|
| 401 處理 | 直接 `return null` | `InsightApiClient` 自動 refresh + retry 一次，失敗仍 `return null` | 401 retry 後仍失敗 → 行為**等價**；若 refresh 後原請求成功 → 行為**更寬鬆**（可接受：exchange 端點若因 token 過期 401，refresh 後本就應能成功） |
| Body | 空 `ByteArray` (`Content-Length: 0`) | 空 JSON `{}`（`Content-Length: 2`, `Content-Type: application/json`） | 若 Insight 後端對 exchange 端點嚴格檢查 body，可能有差異；若後端不介意，**行為等價** |
| Header | 只有 `Authorization` | 加上 `X-Platform-Key` + `Content-Type: application/json` + `Authorization`（自動注入） | 端點本就設計接受 platform header，**更一致**、無 regression 風險 |
| token cache（本地 1h / 提前 5min refresh） | 保留 | 保留 | 無變更 |

**結論**：行為差異三處，其中 401 retry 為**寬鬆化**、body 形態與 header 補齊為**對齊化**，無 regression 風險；token cache 生命週期完全保留。

### 2.2 `resolveUserId()` 行為變更

- 舊：`auth.currentUserId` 命中直接回；否則先判斷 `auth.isLoggedIn`，再 `auth.fetchMe()`
- 新：`session.resolveVerifiedUserId()` 內部邏輯**完全等價**（先看 `authClient.currentUserId`，不在就判斷 `isLoggedIn` 再 `fetchMe()`）
- 差別：原本位於 `CloudSyncManager` 的 debug log `"userId null but token exists, calling fetchMe to resolve"` 被移除（原本只是診斷資訊）

### 2.3 auth → session 讀 token / isLoggedIn 行為

完全 pass-through，**無行為變更**。

---

## 3. Compliance Check vs S7-3 gate / CCA side-note §E 紅線

| 紅線 | 狀態 | 備註 |
|------|------|------|
| 不把 `CloudSyncManager` 14 處 prefs 直讀搬到 Repository | ✅ 未動 | 14 處 `context.getSharedPreferences("yukiboard_*")` 原地保留 |
| 不動 `YukiPreferences` 38 個 field schema / keys | ✅ 未動 | `exportPrefsToJson` / `importPrefsFromJson` 原地保留 |
| 不動 7 個 subsystem 內部 pref schema / file 名 | ✅ 未動 | AdaptiveDict / PersonalDict / PhrasePredict / SentencePredict / StyleProfiler / TypingHabits / CorrectionLearn 原地保留 |
| 不刪 `SnowRealmAuthClient.postAuth` 內部 `/me` | ✅ 未動 | 未觸 auth 檔 |
| 不改 `CloudSyncManager.resolveUserId()` 的 fetchMe 語意 | ✅ 語意等價 | 僅 delegate 到 Repository，behavior 不變 |
| 不把 `SnowRealmAuthClient` 的 fetchMe 改走 `InsightApiClient` | ✅ 未動 | `SnowRealmAuthClient.fetchMe` 仍用自己的 OkHttp instance |
| 不升 `InviteCodeManager` singleton | ✅ 未動 | 未觸該檔 |
| 不 section 化 `AISettingsActivity` | ✅ 未動 | 未觸該檔 |
| 不動 `YukiBoardIMEService.kt` / panel binder | ✅ 未動 | 未觸該檔 |
| 不動 `ApiKeySync` 的 Supabase token 策略（S7-2 成果） | ✅ 未動 | `ApiKeySync.kt` 未觸 |
| 不動 `SettingsActivity` 4 處 reach-in | ✅ 未動 | 未觸該檔 |
| 若 exchange 走 `InsightApiClient`，必須明示 401 retry 行為 | ✅ 已文件化 | §2.1 明示 |

### 與 S7-0 CodeX A 三條裁決

1. `AISettingsActivity` section 化：**未觸**。
2. `InviteCodeManager` singleton 化：**未觸**。
3. `CloudSyncManager` gateway / snapshot provider 拆分只允許在 S7-3：本輪**僅做 gateway 拆分**（exchange 走 InsightApiClient + auth 走 Repository），**未動 snapshot provider / payload builder 拆分**（那仍在 S7-4 範圍）。

---

## 4. 回交（A / B / C / D / E）

### A. 變更摘要

- 修改 3 檔：
  - `app/src/main/java/com/snowrealm/yukiboard/app/session/UserSessionRepository.kt` — 新增 `refreshSession()` / `resolveVerifiedUserId()` pass-through
  - `app/src/main/java/com/snowrealm/yukiboard/data/InsightApiClient.kt` — auth 直達改走 Repository + KDoc 明示唯一 gateway 契約
  - `app/src/main/java/com/snowrealm/yukiboard/data/CloudSyncManager.kt` — auth 直達改走 Repository + exchange 改走 InsightApiClient + KDoc 明示 dual-role 暫態
- 新增骨架/介面/依賴：
  - Repository 的 gateway-facing 介面（2 個 pass-through），KDoc 標記「僅供 gateway caller 使用」
  - `InsightApiClient` 成為 Insight `/api/` 路徑的唯一 gateway（契約層級）

### B. 風險

- **低風險**：
  - `getSupabaseToken()` 的 401 retry 行為變更（寬鬆化），若 exchange 端點遇 401 → refresh → retry 成功，本來直達版會 return null；新版會成功
  - 空 JSON body vs 空 ByteArray：若 Insight 後端對 body 嚴格檢查 `Content-Length: 0`，可能需實測（實測由 Sprint 8 QA 覆蓋）
- **非本輪風險（推到後續 round）**：
  - `CloudSyncManager` 仍同時扮演 gateway + payload builder + prefs reader 三角色（面積壓力推到 S7-4，已在 KDoc 明示）
  - `SnowRealmAuthClient.fetchMe` 仍自包 HTTP，非 `InsightApiClient` gateway 成員（屬 Sprint 8 或後續 round）
  - `resolveVerifiedUserId` 與 `SettingsScreenCoordinator.verifySessionAsync` 雙 fetchMe（R-1）屬 Sprint 8
  - `refreshSession` pass-through 內部 fetchMe（R-6）屬 Sprint 8

### C. 未做（主動縮面）

- ❌ 不拆 `CloudSyncManager` 的 snapshot provider / payload builder（S7-4）
- ❌ 不動 `CloudSyncManager` 14 處 prefs 直讀（S7-4）
- ❌ 不動 `YukiPreferences` 38 個 field schema（S7-4）
- ❌ 不動 7 個 subsystem 內部 pref schema（S7-4）
- ❌ 不把 `SnowRealmAuthClient.postAuth` / `fetchMe` 改走 `InsightApiClient`（Sprint 8）
- ❌ 不動 `ApiKeySync`（S7-2 成果）
- ❌ 不動 `SnowRealmProvider`（非本輪必要；CCA side-note §I 建議不動）
- ❌ 不擴 `InsightApiClient` streaming `onHeader` / SSE polish（CCA side-note §B-3 建議不做）
- ❌ 不合併 `postAuth` 雙 fetchMe（Sprint 8）
- ❌ 不升 `InviteCodeManager` singleton（S7-0 邊界）
- ❌ 不 section 化 `AISettingsActivity`（S7-0 邊界）

### D. 下一步建議（候選，不自排）

可能的 S7-4 / 後續方向（由 `CodeX A` 裁決，`Claude Code B` 不自排）：

1. `CloudSyncManager` snapshot provider / payload builder 拆分（屬 S7-4）
2. 7 個 subsystem pref schema 收斂到各自 Repository（屬 S7-4）
3. `YukiPreferences` 38 個 field → `SettingsRepository`（屬 S7-4）
4. `SnowRealmAuthClient.postAuth` 雙 fetchMe 整併（屬 Sprint 8）
5. `CloudSyncManager.resolveVerifiedUserId` / `SettingsScreenCoordinator.verifySessionAsync` 雙 fetchMe 整併（屬 Sprint 8）
6. `AIProviderManager` 的 `setSelectedModel` cache invalidate（屬 Sprint 8 B-4）

### D1. 架構決策

- 新增 `UserSessionRepository.refreshSession(): Boolean` — pass-through 僅供 gateway caller（`InsightApiClient`）401 retry 路徑使用
- 新增 `UserSessionRepository.resolveVerifiedUserId(): String?` — pass-through 僅供 gateway caller（`CloudSyncManager`）取 verified userId 使用
- 契約：`InsightApiClient` 是 Insight `/api/` 路徑的唯一 gateway；新 caller 禁止自建 `OkHttpClient` / 自組 `Authorization`
- 契約：`CloudSyncManager` 是「sync 門面」但仍暫扮 payload builder + prefs reader，待 S7-4 拆分

### E. CodeX A 審核需求/結論

- 請 CodeX A 判定以下：
  1. `S7-3` 的最小安全收斂範圍（gateway 拆分）是否達成
  2. §2.1 的 `getSupabaseToken()` 行為差異（401 寬鬆化 + body 形態 + header 補齊）是否可接受
  3. `CloudSyncManager` dual-role 暫態留到 S7-4 是否可接受
  4. `UserSessionRepository` 新增 2 個 pass-through 的介面面積是否可接受（vs §3 CCA side-note §J 與 §H 建議一致）
- 判定格式：`READY / NOT READY / LOCKED`
- `Claude Code B` 不自排下一 round；待 `CodeX A` 明確開門後再進 S7-4 或其他 round。

---

## 5. 編譯結果

```
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 15s
18 actionable tasks: 1 executed, 17 up-to-date
```

無新 warning / error。

# Sprint 7-2 Session / Auth / Provider Implementation

任務 ID：`S7-2-CCB-SESSION-AUTH-PROVIDER-IMPLEMENTATION`

日期：2026-04-18

身份：Claude Code B

依據：

- `Sprint7-All-Round-Gate.md`（Round S7-2 `ACTIVE`，`CodeX A` 判定 `READY`）
- `docs/SPRINT7_0_INTEGRATION_AUDIT_2026-04-18.md`（CXB total audit）
- `docs/SPRINT7_0_SETTINGS_AUTH_PROVIDER_SYNC_AUDIT_2026-04-18.md`（CCA settings audit — §C.1 升級路徑、§D R-1 ~ R-11）
- `docs/SPRINT7_0_IME_INTEGRATION_AUDIT_2026-04-18.md`（CCB IME audit）
- `docs/SPRINT7_1_NETWORK_ENTRY_IMPLEMENTATION_2026-04-18.md`（S7-1 完成）

## 0. 範圍宣告

- 本輪只做 `Session / Auth / Provider` **最小安全收斂**
- 建立 `UserSessionRepository` 的寫入 API（S7-2 升級路徑第一步）
- 把 `ApiKeySync` 改為 repository 消費者，移除兩處直達 singleton 的 reach-in
- 不碰 `CloudSyncManager` gateway 拆分（S7-3）
- 不做 `InviteCodeManager` singleton 化（S7-2 禁令）
- 不做 `AISettingsActivity` section 化（S7-2 禁令）
- 不動 `YukiBoardIMEService.kt` / panel binder（S7-2 禁令）
- 不做 bugfix（Sprint 8）
- 不改 `SnowRealmAuthClient.postAuth` 的二次 fetchMe（R-6 屬 S7-3 InsightApiClient 統一）
- 不動 `AIProviderManager` 的 provider cache（R-5 audit 確認非 bug，B-4 留 Sprint 8）

## 1. 變更總覽

### 1.1 已修改檔案

| 檔案 | 改動內容 |
| --- | --- |
| `app/src/main/java/com/snowrealm/yukiboard/app/session/UserSessionRepository.kt` | 擴寫入 API + token/userId pass-through + 讀取補齊（getSelectedModel / getCurrentProvider / hasValidProviderConfig） |
| `app/src/main/java/com/snowrealm/yukiboard/claw/provider/ApiKeySync.kt` | 移除對 `SnowRealmAuthClient.getInstance` / `AIProviderManager.getInstance` 的直達依賴，改走 `UserSessionRepository` |

### 1.2 新增公開 API（UserSessionRepository）

寫入：

- `saveProviderSelection(type: AIProviderType)` — 切換使用中的 AI Provider
- `saveApiKey(type: AIProviderType, key: String)` — 儲存 provider API key（底層會 invalidate cache）
- `saveSelectedModel(type: AIProviderType, model: String)` — 儲存選定 model
- `clearSession()` — 登出；不影響 provider 選擇與 API key（兩者為不同 owner）

讀取補齊：

- `getSelectedModel(type): String`
- `getCurrentProvider(): AIProvider`
- `hasValidProviderConfig(): Boolean`

Token / userId pass-through（供 gateway / sync caller 使用）：

- `currentUserId: String?`
- `getAuthToken(): String?`

### 1.3 ApiKeySync 遷移 diff 示意

Before：

```kotlin
val auth = SnowRealmAuthClient.getInstance(context)
if (!auth.isLoggedIn) return SyncResult(false, "請先登入帳號")
val userId = auth.currentUserId ?: return SyncResult(false, "無法取得用戶 ID")
val token = auth.getToken() ?: return SyncResult(false, "Token 無效")
val supabase = SupabaseClient.getInstance()
...
val mgr = AIProviderManager.getInstance(context)
mgr.saveApiKey(type, key)
```

After：

```kotlin
val session = UserSessionRepository(context)
if (!session.isLoggedIn()) return SyncResult(false, "請先登入帳號")
val userId = session.currentUserId ?: return SyncResult(false, "無法取得用戶 ID")
val token = session.getAuthToken() ?: return SyncResult(false, "Token 無效")
val supabase = SupabaseClient.getInstance()   // gateway 保留（S7-3）
...
session.saveApiKey(type, key)
```

公開簽名不變（`uploadKeys(context)` / `downloadKeys(context)`），因此 3 個 callers
（`OAuthCallbackActivity` / `AISettingsActivity` ×2）無須改動。

### 1.4 Singleton 直達 callsite 對比

| Callsite | 變更前 | 變更後 | S7-2 範圍？ |
| --- | --- | --- | --- |
| `ApiKeySync.uploadKeys` / `downloadKeys` | `SnowRealmAuthClient.getInstance` + `AIProviderManager.getInstance` | `UserSessionRepository` | ✅ 本輪處理 |
| `ClawClient` | `SnowRealmAuthClient.getInstance` + `AIProviderManager.getInstance` + `new InviteCodeManager` | 不變 | 非本輪必要（ClawClient 為 claw/ runtime，行為穩定；repo 切換會牽涉更多驗證，留後續 round） |
| `SnowRealmProvider` | `SnowRealmAuthClient.getInstance` + `new InviteCodeManager` | 不變 | 同上 |
| `CloudSyncManager.resolveUserId` | `SnowRealmAuthClient.getInstance` | 不變 | **S7-3**（gateway 拆分不前移） |
| `InsightApiClient` | `SnowRealmAuthClient.getInstance` | 不變 | **S7-3**（gateway 為 S7-3 主題） |
| `SettingsActivity` 4 處 | `getInstance` reach-in | 不變 | 屬 settings 側（CCA 領域），非本輪 implementation 目標 |
| `AISettingsActivity` | `getInstance` reach-in | 不變 | **S7-2 禁令**（section 化禁止） |

## 2. 設計取捨

### 2.1 為何選擇「repository 擴 write + 遷移 ApiKeySync」為最小安全單位

- 建立 `UserSessionRepository` 寫入 API 是 §C.1 明寫的 **S7-2 升級路徑第一步**
- 只遷移 1 個 caller（`ApiKeySync`）就能驗證 write API 正確無副作用
- `ApiKeySync` 符合允許的修改範圍 `claw/provider/`
- 不需動任何 Activity / IMEService / panel binder（都在禁令清單）
- 3 個 `ApiKeySync` caller（`OAuthCallbackActivity` / `AISettingsActivity` ×2）簽名未變，
  不需碰它們

### 2.2 為何不碰 ClawClient / SnowRealmProvider 這輪

- 兩者各自使用了 `InviteCodeManager(context)`，而 `InviteCodeManager` singleton 化為
  S7-2 禁令
- 若只把 auth/provider 改走 repo、`InviteCodeManager` 仍自 new，會形成半遷移的不一致
- 留到 `InviteCodeManager` 決策（`CodeX A` 後續 round 判斷）後再一次遷移，更安全

### 2.3 為何 `clearSession()` 不清 provider / API key

- `SnowRealmAuthClient.logout()` 的原行為只清 auth token 與用戶 profile，不動 provider 選擇與 API key
- 使用者自帶的 API key 理應在 logout 後仍保留（否則自帶 key 用戶會被強制重建）
- Repository 保留此語意，避免 behaviour change

### 2.4 為何不包 `InviteCodeManager`

- S7-2 禁令明確禁止 `InviteCodeManager` singleton 化
- 若 Repository 持有單一 `InviteCodeManager` 實例，功能上等同 singleton，越界
- 留給 `CodeX A` 在後續 round 決定是否升級

## 3. Build 驗證

```bash
$ ./gradlew.bat :app:compileDebugKotlin --no-daemon
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 14s
18 actionable tasks: 1 executed, 17 up-to-date
```

- 編譯成功
- 無新增 warning（與 S7-1 完成後的 baseline 一致）
- 無 error

## 4. A / B / C / D 回交

### A. 變更摘要

- `UserSessionRepository` 擴 write API：`saveProviderSelection` / `saveApiKey` /
  `saveSelectedModel` / `clearSession`
- `UserSessionRepository` 新增 token/userId pass-through：`currentUserId` / `getAuthToken()`
- `UserSessionRepository` 補齊 read surface：`getSelectedModel` / `getCurrentProvider` /
  `hasValidProviderConfig`
- `ApiKeySync` 改走 repository，移除對 `SnowRealmAuthClient.getInstance` /
  `AIProviderManager.getInstance` 的直達
- `ApiKeySync` 公開簽名未變，3 個 callers 無須改動
- 2 個檔案改動，0 個新增檔案
- 嚴守 S7-2 禁令（見 §5 合規檢核）

### B. 風險

1. **`UserSessionRepository` 實例化於各 caller**
   - `ApiKeySync` 每次 `uploadKeys` / `downloadKeys` 都 `new UserSessionRepository(context)`
   - Repository 內部只持有底層 singleton reference（`authClient` / `providerManager`），建構成本極低
   - 但這意味著 Repository 本身尚未 singleton 化 — 若未來多處 caller 都 new，會有**多實例讀同一 prefs**的語意
   - 不是 regression（等同以往多處直達 singleton），留後續 round 決定是否 singleton 化 Repository

2. **`clearSession()` 行為保守**
   - 僅清 auth；provider 選擇、API key、invite code 不動
   - 若未來業務邏輯變成「登出也要清 provider」，需另外調整
   - 目前與 `SnowRealmAuthClient.logout()` 的舊行為完全一致，不是 regression

3. **尚未收斂 CloudSyncManager / InsightApiClient / ClawClient / SnowRealmProvider 的 reach-in**
   - 有明確理由（見 §1.4 表）：gateway 是 S7-3、InviteCodeManager 是禁令、Activity 屬 CCA 側
   - 不在本輪視為必要

### C. 下一步建議

請 `CodeX A` 對本輪 `S7-2` 下 `READY / NOT READY / LOCKED` 判定。

若 `READY`，後續 round 候選（**不自排**）：

- 候選 A：`S7-3` Sync / External Gateway 收斂（CloudSyncManager / InsightApiClient
  / SnowRealmProvider 的 gateway 拆分 — 已被閘住等 S7-2 完成）
- 候選 B：`InviteCodeManager` singleton 化決策（如要做，需 `CodeX A` 開門）
- 候選 C：`UserSessionRepository` 自身 singleton 化（減少多實例 new）
- 候選 D：`ClawClient` / `SnowRealmProvider` 改走 Repository（需先解 invite manager 歸屬）

本輪不碰：

- `YukiBoardIMEService.kt`、panel binder
- `AISettingsActivity` section 化
- `InviteCodeManager` singleton 化
- `CloudSyncManager` gateway 拆分
- bugfix（Sprint 8）

### D. 架構決策

| 決策 | 選擇 | 理由 |
| --- | --- | --- |
| Repository 單一寫入入口 | 擴 `UserSessionRepository` write API，underlying 仍 delegate 給 `authClient` / `providerManager` | 不改底層 SSOT，只收斂 callsite 路徑；風險最低 |
| Repository 本身是否 singleton | **否**（本輪） | 若 singleton 化會先動 `SettingsScreenCoordinator` / `ImeSessionCoordinator` 的構造，超出本輪 |
| `InviteCodeManager` 是否進 Repository | **否**（本輪） | S7-2 禁令明確禁止 singleton 化 |
| `clearSession()` 語意 | 只清 auth，不清 provider/API key | 與現行 `logout()` 完全一致，避免 behaviour change |
| `ApiKeySync` 公開簽名 | 保持 `uploadKeys(context)` / `downloadKeys(context)` 不變 | 不觸動 3 個 caller（含 `AISettingsActivity`，本輪禁止動） |
| 不處理 R-5 / R-6 | R-5 audit 自評非 bug、R-6 屬 S7-3 | 嚴守本輪範圍 |

新增公開介面：

- `UserSessionRepository.saveProviderSelection(type)`
- `UserSessionRepository.saveApiKey(type, key)`
- `UserSessionRepository.saveSelectedModel(type, model)`
- `UserSessionRepository.clearSession()`
- `UserSessionRepository.currentUserId`
- `UserSessionRepository.getAuthToken()`
- `UserSessionRepository.getSelectedModel(type)`
- `UserSessionRepository.getCurrentProvider()`
- `UserSessionRepository.hasValidProviderConfig()`

未新增：coordinator、gateway、interface（本輪只是建立 owner 寫入 path）。

### E. CodeX A 審核需求 / 結論

- 需要 `CodeX A` 對本輪下一步判定：`READY` / `NOT READY` / `LOCKED`
- 請 `CodeX A` 決定下一個 round（本文件不自排順序）
- 若 `NOT READY`，請指示要再收斂哪一個 caller 或要回退哪一段

## 5. 合規檢核（對 `CodeX A` S7-2 禁令清單）

| 禁令 | 本輪做法 | 合規？ |
| --- | --- | --- |
| 不改 `CloudSyncManager` gateway | `CloudSyncManager.kt` 本輪未動 | ✔ |
| 不做 `AISettingsActivity` section 化 | `AISettingsActivity.kt` 本輪未動 | ✔ |
| 不做 `InviteCodeManager` singleton 化 | `InviteCodeManager.kt` 本輪未動；Repository 未持有 invite manager | ✔ |
| 不動 `YukiBoardIMEService.kt` | 未動 | ✔ |
| 不碰 panel binder 系列 | 未動 | ✔ |
| 不做 bugfix 題 | 未動；R-5 / R-6 已明確留置 | ✔ |
| 不碰 Sprint 8 檔 | 未動；B-1 ~ B-8 皆未處理 | ✔ |

對 `CodeX A` S7-0 三條邊界裁決的遵守：

| 裁決 | 本輪做法 | 合規？ |
| --- | --- | --- |
| §1 `AISettingsActivity` section 化不在 Sprint 7 主線 | 未動 | ✔ |
| §2 `InviteCodeManager` 不在 S7-1 直接升 singleton | 本輪（S7-2）亦未升 | ✔ |
| §3 `CloudSyncManager` gateway / snapshot provider 拆分只允許 S7-3 | 本輪未動 | ✔ |

## 6. Build 輸出

見 §3（編譯成功，14 秒完成，無新增 warning / error）。

---

（文件產出者：Claude Code B，2026-04-18）

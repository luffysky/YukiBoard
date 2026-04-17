# Sprint 9-B InviteCode Atomicity Fix

任務 ID：`S9-B-CXB-INVITECODE-ATOMICITY-FIX`

日期：2026-04-18

身份：CodeX B

## A. 變更摘要

### 修改檔案

- `app/src/main/java/com/snowrealm/yukiboard/auth/InviteCodeManager.kt`

### 修補內容

針對 `InviteCodeManager` 多 instance 共用同一份 invite prefs 時的非原子問題，新增 companion-level `usageLock`，把以下 state mutation / state-sensitive read 收斂到同一個 process-wide critical section：

- `activateCode()`
- `getDailyTokenUsed()`
- `consumeTokens()`
- `resetDailyUsageIfNeeded()`
- `revokeCode()`

另外把原本外露的 reset 邏輯拆成：

- `resetDailyUsageIfNeeded()`：public wrapper
- `resetDailyUsageIfNeededLocked()`：只在 lock 內呼叫的私有實作

### 修補目的

封住以下 race：

1. 多個 `InviteCodeManager` instance 同時 `consumeTokens()`，各自讀到同一個舊的 `daily_token_used` 後覆寫，造成 lost update
2. `usage_date` 跨日 reset 與 `consumeTokens()` 交錯，導致 reset / consume 次序不一致
3. `activateCode()` / `revokeCode()` 與 usage read-write 並行，造成 usage state 被半途覆蓋

## B. 風險

1. 這次修補是 process-wide lock，不是跨 process lock；但目前 YukiBoard 為單 process app，符合 gate 前提。
2. `getRemainingTokens()` 仍由 `getDailyTokenLimit()` + `getDailyTokenUsed()` 分兩步取得；在單 process lock 設計下，usage 讀值本身已正確，但這不是「整體交易式快照」。本輪 gate 目標是 `consumeTokens()` 與 daily reset 的正確同步語意，這點已滿足。
3. 沒有擴到 `TokenBudgetManager` / `AI provider` / `SnowRealmAuthClient` / `ApiKeySync`。

## C. 驗證

### 編譯

執行：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME='D:\SnowRealmRebirth\SnowRealmYukiBoard\.gradle-user-home'
$env:ANDROID_USER_HOME='D:\SnowRealmRebirth\SnowRealmYukiBoard\.android-user-home'
./gradlew.bat :app:compileDebugKotlin --no-daemon
```

結果：

- `BUILD SUCCESSFUL`
- Kotlin daemon 連線失敗後 fallback compile 成功，屬環境層噪音，不是本修補失敗
- 僅見既有 warning，無新 error

### Scope

本輪只改：

- `InviteCodeManager.kt`

未動：

- `SnowRealmAuthClient.kt`
- `ApiKeySync.kt`
- `TokenBudgetManager.kt`
- 任何 `Sprint 7/8` 既有 write scope 檔案

## D. 架構決策

### 採用

- companion-level `usageLock`
- 在 `InviteCodeManager` 內部就地完成原子化

### 不採用

- singleton 化 `InviteCodeManager`
- 擴到 caller 端協調 consume/reset
- 擴到 `TokenBudgetManager` 或 provider 配額邏輯

理由：

- gate 已明確禁止把這題擴成其他 token / provider 題
- 問題核心是 shared prefs 的同步語意，不是 ownership 重構

## E. 請 CodeX A 判定

- `READY` / `NOT READY` / `LOCKED`
- 若 `READY`，此 workstream 可視為完成，等待其他 workstream individually gate


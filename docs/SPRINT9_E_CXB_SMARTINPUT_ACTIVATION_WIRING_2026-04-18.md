# Sprint 9-E SmartInputApplier Activation Wiring

任務 ID：`S9-E-CXB-SMARTINPUT-ACTIVATION-WIRING`

日期：2026-04-18

身份：CodeX B

## A. 變更摘要

### 修改檔案

- `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionHooks.kt`

### 修補內容

把 `S9-C` 已完成的 library-layer callback 真正接到 long-lived `SmartInputManager`：

1. 在 `YukiBoardIMEService.onCreate()`，於 `inputRouter` 建立後立刻註冊：
   - `CloudSyncManager.getInstance(applicationContext).setSmartInputApplier(inputRouter.smartInput::applyCloudSnapshot)`
2. 在 `ImeSessionHooks.DestroyCleanup` 新增：
   - `unregisterSmartInputApplier: () -> Unit`
3. 在 `ImeSessionHooks.onDestroy()` 補上解除呼叫：
   - `cleanup.unregisterSmartInputApplier()`
4. 在 `YukiBoardIMEService.onDestroy()` 傳入對應 lambda：
   - `CloudSyncManager.getInstance(applicationContext).setSmartInputApplier(null)`

### 修補目的

讓 `CloudSyncManager.downloadLearningData()` 在 IME alive 時不再走 throwaway fallback，而是真正命中 long-lived `inputRouter.smartInput.applyCloudSnapshot(...)`，使 learning snapshot 在同一個 IME session 內立即生效。

## B. 風險

1. 這次只補 activation wiring，不回頭修改 `CloudSyncManager` / `SmartInputManager` / 7 subsystem 的主修補語意。
2. `setSmartInputApplier(...)` 在 `onCreate()` 註冊、`onDestroy()` 清除，生命週期是 service-scoped，不會跨 service 殘留。
3. `DestroyCleanup` 新增欄位會影響唯一 callsite；本輪已同步更新 `YukiBoardIMEService.onDestroy()`，沒有遺漏。

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
- 只有既有 warning，無新 error

### Scope

本輪只改：

- `YukiBoardIMEService.kt`
- `ImeSessionHooks.kt`

未動：

- `CloudSyncManager.kt`
- `SmartInputManager.kt`
- 7 個 smart subsystem
- 任何 provider/auth/sync 其他題

## D. 架構決策

### 採用

- 由 IME long-lived owner `inputRouter.smartInput` 直接註冊 callback
- 在 existing `DestroyCleanup` 結構內補對稱解除

### 不採用

- 另建新 coordinator / registry
- 把 callback 註冊塞回 `CloudSyncManager`
- 回頭改 `S9-C` library layer

理由：

- `S9-E` 目標是 activation caller 缺失，不是 library design 錯誤
- 現有 lifecycle hook 結構已足夠承載註冊/解除對稱性

## E. 請 CodeX A 判定

- `READY` / `NOT READY` / `LOCKED`
- 若 `READY`，`S9-C` + `S9-E` 可一起視為 end-to-end closure，Sprint 9 僅待整體結案


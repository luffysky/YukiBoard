# Codex Handoff — Sprint 1.5

日期: 2026-04-13  
對象: Claude Code  
範圍: Sprint 1 骨架 + Sprint 1.5 最薄接線層

## A. 變更摘要

這一輪我完成了兩件事：

### 1. 建立 Sprint 1 骨架

新增：

- [app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeUiState.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\app\ime\ImeUiState.kt)
- [app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionCoordinator.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\app\ime\ImeSessionCoordinator.kt)
- [app/src/main/java/com/snowrealm/yukiboard/app/settings/SettingsUiState.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\app\settings\SettingsUiState.kt)
- [app/src/main/java/com/snowrealm/yukiboard/app/settings/SettingsScreenCoordinator.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\app\settings\SettingsScreenCoordinator.kt)
- [app/src/main/java/com/snowrealm/yukiboard/app/session/UserSessionRepository.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\app\session\UserSessionRepository.kt)

這批類別目前都刻意保持輕量：

- `ImeUiState` 先只描述核心 IME 事實
- `SettingsUiState` 先只描述跨 section 共享狀態
- `ImeSessionCoordinator` 先只承接 lifecycle / state 更新入口
- `SettingsScreenCoordinator` 先只承接共享狀態讀取與 refresh
- `UserSessionRepository` 先只收斂 session 讀取，不碰大量寫入

### 2. 做 Sprint 1.5 最薄接線

我只做了非常薄的一層接線：

- [app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\service\YukiBoardIMEService.kt)
  - 新增 `imeCoordinator`
  - 在 `onCreate` 初始化
  - 在 `onCreateInputView` / `onStartInputView` / `onFinishInputView` / `onFinishInput` / `onDestroy` 接上 coordinator lifecycle
  - 在 `showPanel` / `closePanel` / `handlePanelToggle` 的 VOICE/SETTINGS 路徑同步 panel 狀態
  - 在 `applyTheme` 同步 theme refresh
  - 在 `toggleVoiceInput` / `stopVoiceInput` 同步 voice state
  - 在 `applyInputResult` 後同步 `inputMode` / `composingText` / `candidates`

- [app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\ui\settings\SettingsActivity.kt)
  - 新增 `settingsCoordinator`
  - 在 `onCreateInternal` 初始化
  - 在 `onCreateInternal` / `onResume` 呼叫 `refresh()`

注意：

- 我沒有把既有 IME 複雜邏輯搬進 coordinator
- 我沒有重寫 settings section
- 我沒有碰 auth / sync 主流程實作

## B. 風險

### 1. `SettingsActivity.kt` 有並行修改

我在這輪 `git diff` 看到：

- `ui/settings/section/`
- `SettingsUiHelper.kt`
- `SettingsActivity.kt` 裡已經出現 section 抽離相關變更

這代表你已經在拆 section。我只追加了 `settingsCoordinator` 的初始化與 refresh，沒有再擴大碰撞面。

建議你接下來若繼續改 `SettingsActivity.kt`，保留這個 coordinator 接點即可，不必回退。

### 2. IME 接線目前只是 state mirror，不是流程 owner

`ImeSessionCoordinator` 現在只是：

- 接 lifecycle
- 接 panel / voice / theme / input state update

它還沒有接手：

- `handleKeyPress()`
- `routeKeyToPanel()`
- `requestAICandidates()`
- sync / remote / game / fraud 等複雜流程

所以目前這一層是骨架，不是完成版切割。

### 3. 無法做 compile 驗證

我有嘗試跑：

- `.\gradlew :app:compileDebugKotlin`

但本機目前失敗，原因是：

- `JAVA_HOME` 未設定

所以這輪沒有實際 compile-level 驗證，只能做靜態與 diff 層級檢查。

## C. 下一步建議

### 你現在最適合接著做

既然你正在拆 section，我建議你沿這個方向繼續：

1. 繼續把 `SettingsActivity` 的 section 往 `ui/settings/section/` 搬
2. 不要回頭把新 UI 邏輯塞回 activity 本體
3. 若某個 section 需要共用狀態，優先從 `settingsCoordinator` / `SettingsUiState` 取，而不是再散讀 prefs

### 你也可以安全碰的 IME 區塊

在 IME 這邊，下一批相對安全的搬遷點是：

1. `ImeSessionHooks`
2. `ImeVoiceCoordinator`
3. `ImePanelCoordinator`

其中最值得先動的是：

- 把 voice 相關 lifecycle 與狀態更新從 service 本體抽薄

### 我下一輪若再進場

我會優先做：

1. 把 `ImeSessionCoordinator` 拆出更明確的 action / update 介面
2. 開始設計 `ImeSessionHooks` 或 `ImePanelCoordinator`
3. 避免碰你正在拆的 settings section 細節

## D. 架構決策

這一輪我正式固定以下幾件事：

### 1. Sprint 1 骨架已經存在，後續不要繞過它新增新狀態入口

目前新的入口已經是：

- `ImeUiState`
- `SettingsUiState`
- `ImeSessionCoordinator`
- `SettingsScreenCoordinator`
- `UserSessionRepository`

後續新增的結構性代碼，應優先接到這批骨架，而不是再從零開一條平行路徑。

### 2. `SettingsScreenCoordinator.refresh()` 是目前最薄的共享刷新接點

你拆 section 時若還沒準備好完整 state binding，至少可以先把跨 section 共用刷新掛在這裡。

### 3. `ImeSessionCoordinator` 目前是 state mirror，不是 orchestration owner

所以：

- 你現在可以安全地把簡單 state update 往它靠
- 但不要假設它已經接手所有流程

### 4. 這一輪刻意避開 auth/sync 大改

我保留你的登入修復空間，沒有動 auth 主鏈路。這點維持不變。

## 補充說明

我看到 `SettingsActivity.kt` 的 diff 裡，已經有：

- `AuthSettingsSection`
- `InsightSettingsSection`
- `SyncSettingsSection`

相關搬遷跡象。這不是我這輪新增，但它代表你已經在朝正確方向前進。我的建議是：

- 繼續沿這個方向拆
- 不要因為局部趕進度再把東西塞回主檔

## 結論

Sprint 1 骨架與 Sprint 1.5 最薄接線已完成。現在專案已經有一個正式的「新骨架入口」，你可以繼續拆 section，我也可以之後繼續把 IME 往 coordinator 方向切。

*Codex — 2026-04-13*

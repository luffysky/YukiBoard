# Sprint 5 Round 1 — Container / External State Consume Boundary

> **Status**：Round 1 completed + Round 2 reader migration completed（2026-04-17, pending CodeX A review）
> **Scope (R1)**：建立 container-scope state 的最小 read-only consume 邊界（`ImeContainerStateConsumer`）。
> **Scope (R2)**：9/11 處 reader 遷移至 `imeContainerState.xxx`；2 處（R5 / R11）依規則保留 inline。
> **本輪不做**：writer 整併（Round 3）／local field 移除（Round 3）／KeyboardView 改動／InputRouter 重寫／AI・network・settings 順手改。

## 1. Sprint 4 遺留的 Sprint 5 狀態欄位

Sprint 4 已將 `composingText / candidateState` 的 render ownership 收斂到 `InputUiSync`；
剩餘的 container / external state 欄位屬於 Sprint 5 範圍。

| # | 欄位 | `ImeUiState` 對應 | `YukiBoardIMEService` 本地鏡像 | 來源類別 |
|---|------|-----------------|------------------------------|---------|
| 1 | `isFloatingMode` | `isFloatingMode: Boolean` | `L210 internal var isFloatingMode = false` | container |
| 2 | `isPanelBehindKeyboard` | `isPanelBehindKeyboard: Boolean` | `L95 internal var isPanelBehindKeyboard = false` | container |
| 3 | `isInGameMode` | `isInGameMode: Boolean` | `L226 internal var isInGameMode = false` | container |
| 4 | `activePanel` | `activePanel: PanelType?` | `L143 internal var activePanel: PanelType? = null` | container |
| 5 | `remoteConnectionState` | `remoteConnectionState: ConnectionState` | **（無本地鏡像）** | external |

這五個欄位屬於「container / external state」，共同特徵：

- 影響 IME container 行為（overlay 順序／window 佈局／事件路由），不屬 composing/candidates render path
- 寫入頻率低（不在熱路徑），無 HANDWRITING / AI debounce / swipe 的 RMW 壓力
- `remoteConnectionState` 已經是 coordinator-only；其餘 4 個仍維持 local mutable + coordinator mirror 的 dual-source-of-truth

## 2. Writer 盤點（7 處）

所有 writer 已完成 dual-write，只差 local field 移除（屬 Round 3）。

| # | 位置 | 寫入行為 |
|---|------|---------|
| W1 | `ImeServiceMisc.kt:107-109` `restorePanelOverlayOrder` | `isPanelBehindKeyboard = false` + `imeCoordinator.setPanelBehindKeyboard(false)` |
| W2 | `ImeServiceMisc.kt:159-160` `togglePanelLayer` | `isPanelBehindKeyboard = !isPanelBehindKeyboard` + `imeCoordinator.setPanelBehindKeyboard(...)` |
| W3 | `ImeServiceMisc.kt:268-269` `wrapInFloatingMode` | `isFloatingMode = true` + `imeCoordinator.setFloatingMode(true)` |
| W4 | `ImeServiceMisc.kt:446-447` `enterGameMode` | `isInGameMode = true` + `imeCoordinator.setGameMode(true)` |
| W5 | `ImeServiceMisc.kt:456-457` `exitGameMode` | `isInGameMode = false` + `imeCoordinator.setGameMode(false)` |
| W6 | `ImeServicePanels.kt:171-173` `showPanel` attach overlay callback | `isPanelBehindKeyboard = false` + `imeCoordinator.setPanelBehindKeyboard(false)` |
| W7 | `ImeServiceMisc.kt:99-104` `updatePanelSelection` → `imePanelCoordinator.syncPanelSelection` | `activePanel = selectedPanel`（lambda）+ `imeCoordinator.showPanel(panel)` |
| W8 | `ImeServiceMisc.kt:415` `updateRemotePanelState` | **僅** `imeCoordinator.setRemoteConnectionState(effectiveState)`（coordinator-only，無 local mirror） |

> Writer 狀態：全部已 dual-write / coordinator-write；**本輪不動**，Round 3 再收斂 local field 移除。

## 3. Reader 盤點（11 處）+ Round 2 遷移結果

Round 2 已完成 9/11 處遷移，剩 2 處（R5 / R11）依規則保留 inline（HANDWRITING guard）。

| # | 位置 | 讀取欄位 | Round 2 狀態 |
|---|------|---------|-------------|
| R1 | `YukiBoardIMEService.kt:283` `ShakeDetector onShake` | `isInGameMode` | ✅ 已遷移 → `imeContainerState.isInGame` |
| R2 | `YukiBoardIMEService.kt:537` `onStartInputView` game-mode resume | `isInGameMode` | ✅ 已遷移（session-init 非熱路徑） |
| R3 | `YukiBoardIMEService.kt:650` `onComputeInsets` 懸浮模式 | `isFloatingMode` | ✅ 已遷移 → `imeContainerState.isFloating` |
| R4 | `YukiBoardIMEService.kt:685` `handleKeyPress` 外層 panel-dispatch guard | `activePanel` | ✅ 已遷移（writer dual-write，StateFlow.update 同步） |
| R5 | `YukiBoardIMEService.kt:728` `KEYCODE_HANDWRITING` branch | `activePanel` | ⏸ **保留 inline**（HANDWRITING guard，依規則） |
| R6 | `ImeServicePanels.kt:110` `handlePanelToggle` | `activePanel` | ✅ 已遷移 → `imeContainerState.activePanel` |
| R7 | `ImeServicePanels.kt:172` showPanel overlay attach | `isPanelBehindKeyboard` | ✅ 已遷移 → `imeContainerState.isPanelBehind` |
| R8 | `ImeServiceMisc.kt:108` `restorePanelOverlayOrder` | `isPanelBehindKeyboard` | ✅ 已遷移 |
| R9 | `ImeServiceMisc.kt:163` `togglePanelLayer` | `isPanelBehindKeyboard` | ✅ 已遷移（W2 已同步 dual-write，snapshot 等值） |
| R10 | `ImeServiceMisc.kt:168` `togglePanelLayer` pass-through | `isPanelBehindKeyboard` | ✅ 已遷移 |
| R11 | `ImeServiceCallbacks.kt:197` `onRecognized` HANDWRITING guard | `activePanel` | ⏸ **保留 inline**（HANDWRITING guard，依規則） |

> Reader 狀態（Round 2 結束）：9/11 遷移完成、2/11 保留 inline（純 HANDWRITING guard）。

## 4. 本輪已落地

### 4.1 新檔：`ImeContainerStateConsumer.kt`

位置：`app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeContainerStateConsumer.kt`

- `class ImeContainerStateConsumer(coordinator: ImeSessionCoordinator)`
- 提供 5 個同步 snapshot getter：`isFloating / isPanelBehind / isInGame / remoteConnection / activePanel`
- 每次 get 都從 `coordinator.uiState.value` 取 snapshot（無快取，讀最新 state）
- 額外 `snapshot(): ImeContainerSnapshot`：一次讀所有欄位，避免多次 get 產生不一致讀
- `data class ImeContainerSnapshot(isFloating, isPanelBehind, isInGame, remoteConnection, activePanel)`

設計邊界（寫在檔頭 KDoc）：

- **負責**：container-scope state 的同步 snapshot read
- **不負責**：寫入（仍走 `ImeSessionCoordinator` 各 setter，Round 3 收斂）／反應式 collect（屬 render layer）／熱路徑 RMW（Sprint 4 保留 B 類）
- **與 `InputUiSync` 平行**：一個負責 composing/candidates render 邊界，一個負責 container state consume 邊界
- **封裝原則**：不暴露 `StateFlow<ImeUiState>`，只暴露 5 個 property getter + snapshot()；避免 reader 直接觸及完整 UI state

### 4.2 Wiring：`YukiBoardIMEService.imeContainerState`

位置：`YukiBoardIMEService.kt:111-115`

```kotlin
// Sprint 5 Round 1: container / external state 的 read-only consume 邊界
// （reader 遷移屬 Round 2，writer 整併屬 Round 3；本輪僅建立入口，不替換既有 read/write）
internal val imeContainerState: com.snowrealm.yukiboard.app.ime.ImeContainerStateConsumer by lazy {
    com.snowrealm.yukiboard.app.ime.ImeContainerStateConsumer(imeCoordinator)
}
```

- 採用 `by lazy`（與同檔 `inputUiSync` 對齊）：避開 `imeCoordinator: lateinit var` 於 `onCreate()` 前未初始化的時序風險
- `internal val`：延伸函式（`ImeServiceInput / Misc / Panels / Callbacks`）可直接取用，Round 2 遷移時不需改可見性

## 5. Round 2 已落地 + Round 3 保留清單

### Round 2 — 已完成（2026-04-17）

9/11 reader migrate to `imeContainerState.xxx`；2/11 保留 inline。

- **Batch 1（safe）7 處**：R1 / R3 / R6 / R7 / R8 / R9 / R10 — 感測器 callback、session-init、panel lifecycle、非熱路徑
- **Batch 2（hot path）2 處**：R2 / R4 — 經評估後遷移
  - R2（onStartInputView game-mode resume）：實際為 session-init，非 `handleKeyPress` 熱路徑
  - R4（`handleKeyPress` 外層 panel-dispatch guard）：writer `updatePanelSelection` 已 dual-write，`StateFlow.update` 同步，coordinator 等值於 local field
- **保留 inline（HANDWRITING 規則）2 處**：
  - R5（`handleKeyPress` `KEYCODE_HANDWRITING` branch）：判讀 `activePanel == PanelType.HANDWRITING`
  - R11（`onRecognized` callback）：同上判讀
  - **保留理由**：依 Round 2 明確規則「R5、R11 只准評估，不准硬改；若涉及 HANDWRITING / RMW 就留 inline」

### Round 3 候選（writer 整併 + local field 移除）

### Round 3 候選（writer 整併 + local field 移除）

將 W1–W7 的 local field 寫入移除，由 coordinator 作為單一來源（single source of truth）。

- **先決條件**：Round 2 reader 遷移完成（否則 reader 讀 local field → 讀不到 coordinator 新值）
- **順序**：先 W8（已是 coordinator-only，直接作為樣本）→ W1/W6（panel-behind 對稱 pair）→ W2/W3/W4/W5（互斥 setter）→ W7（updatePanelSelection lambda 改為直接呼 coordinator）
- **副作用**：`internal var isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel` 四個 local field 可完全移除；`ImeUiState.value` 成為單一 read path

### Sprint 5 後續（Round 2 以外的推進）

- `ImeUiState` 欄位已齊備，無需再擴充
- `setRemoteConnectionState` 已 coordinator-only，可作為 Round 3 的參考模式
- 本輪**不**替 `setVoiceInputActive` / theme 等 Sprint 4 其他欄位做 consume boundary（出範圍）

## 6. 安全判準：可安全 consume vs 必須留 inline

| 分類 | 判準 | 對應 reader |
|------|------|-----------|
| **可安全 consume** | 讀取時機在 callback / session-init / panel lifecycle，無與 composing/candidates 同步時序約束 | R1, R3, R6, R7, R8, R9, R10 |
| **Round 2 評估** | 熱路徑上的外層 guard（early return），遷移後需確認讀到的是 coordinator 最新值 | R2, R4 |
| **Sprint 4 保留區 B 類候選** | HANDWRITING / 其他 RMW 路徑；遷移可能破壞 atomicity | R5, R11 |

## 7. 驗證結果

### Round 1（2026-04-17）

- [x] `./gradlew :app:compileDebugKotlin` 通過（BUILD SUCCESSFUL in 4s）
- [x] `ImeContainerStateConsumer.kt` 新增且 lint-clean
- [x] `YukiBoardIMEService.imeContainerState` by lazy wiring 正確
- [x] 既有 reader / writer 完全未改（zero existing call site changed）
- [x] 禁區完全未動：`KeyboardView.kt` / `InputRouter.kt` / AI / network / settings / RMW hot path
- [x] Sprint 4 收斂邊界（`InputUiSync`）未被碰觸

### Round 2（2026-04-17）

- [x] `./gradlew :app:compileDebugKotlin` 通過（BUILD SUCCESSFUL in 21s）
- [x] Batch 1 reader migrate：R1 / R3 / R6 / R7 / R8 / R9 / R10（7 處）
- [x] Batch 2 reader migrate：R2 / R4（2 處；評估後遷移）
- [x] 保留 inline：R5 / R11（2 處 HANDWRITING guard）
- [x] Writer path 零變動：7 處 dual-write + 1 處 coordinator-only 全部未改
- [x] Local field 未移除：`isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel` 宣告仍在（L95 / L143 / L210 / L226）
- [x] 禁區完全未動：`KeyboardView.kt` / `InputRouter.kt` / network / AI debounce / swipe / HANDWRITING RMW

## 8. 與 Sprint 4 的職責邊界

| Sprint | 邊界類別 | 實作 | 負責 state |
|--------|---------|-----|-----------|
| Sprint 4 | render sync | `InputUiSync`（dual-write view + coordinator） | `composingText`, `candidateState` |
| Sprint 5 | container consume（本 Round） | `ImeContainerStateConsumer`（read-only snapshot） | `isFloating`, `isPanelBehind`, `isInGame`, `remoteConnection`, `activePanel` |

兩者**不重疊**：Sprint 4 是 write+render、Sprint 5 本 Round 只 read。
Round 3 的 writer 整併將把 coordinator 升為 single source of truth，屆時 local field 將移除。

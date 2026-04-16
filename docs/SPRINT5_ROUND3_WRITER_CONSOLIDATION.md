# Sprint 5 Round 3 — Writer Consolidation + Local Field Removal

> **Status**：Round 3 completed（2026-04-17, pending CodeX A review）
> **Scope**：依 Round 1 規劃順序逐批收斂 writer；移除 `YukiBoardIMEService` 內 4 個 container state local field。
> **SSOT 轉移**：`isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel` 的單一來源正式變更為 `imeCoordinator.uiState`（`StateFlow<ImeUiState>`）。
> **不做**：Sprint 4 邊界（InputUiSync）變更／KeyboardView 改動／InputRouter 重寫／AI / network / settings / swipe / HANDWRITING RMW。

## 1. 執行順序（依 Round 1 §5 規劃）

原本 plan 為「W8 樣本 → W1/W6 → W2 → W3 → W4/W5 → W7」。由於 W8（`setRemoteConnectionState`）一開始就是 coordinator-only，直接視為參考模式，實際收斂批次依此順序：

| 順序 | 批次 | 目標 writer | 目標欄位 | 收斂手法 |
|------|------|------------|---------|----------|
| 0 | Pre-step | R5 / R11 | `activePanel` | 遷移最後 2 處 inline reader 至 `imeContainerState.activePanel`（Round 3 移除 local field 前置） |
| 1 | α | W1（`restorePanelOverlayOrder`）/ W6（`showPanel` overlay attach） | `isPanelBehindKeyboard` | 移除 `isPanelBehindKeyboard = false` 本地寫，保留 `imeCoordinator.setPanelBehindKeyboard(false)` |
| 2 | β | W2（`togglePanelLayer`） | `isPanelBehindKeyboard` | 由 `x = !x; setter(x)` 合併為 `setter(!imeContainerState.isPanelBehind)` 單 write |
| 3 | γ | W3（`wrapInFloatingMode`） | `isFloatingMode` | 移除 `isFloatingMode = true` 本地寫 |
| 4 | δ | W4（`enterGameMode`）/ W5（`exitGameMode`） | `isInGameMode` | 移除 `isInGameMode = true/false` 本地寫 |
| 5 | ε | W7（`updatePanelSelection` lambda） | `activePanel` | 移除 `activePanel = selectedPanel` 本地寫；`syncPanelSelection` lambda 改為空 body（`{ }`）；coordinator `showPanel` 為單一寫入點 |
| 6 | Final | Local field removal | 4 個欄位 | 刪除 `YukiBoardIMEService.kt` 內 `internal var isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel`，改以標記註解 |

## 2. 每批結束後的 reader 驗證

依規則「每做一批就確認對應 reader 已不再依賴 local field」：

| 批次 | Grep 結果 |
|------|---------|
| α 後（`isPanelBehindKeyboard`） | 剩 W2 writer + 宣告 |
| β 後（`isPanelBehindKeyboard`） | 僅剩宣告 |
| γ 後（`isFloatingMode`） | 僅剩宣告 |
| δ 後（`isInGameMode`） | 僅剩宣告 |
| ε 後（`activePanel`） | 僅剩宣告 + migrated reads + 不相關的 `ToolbarView.activePanel` |

每批次的 grep 輸出都確認：本輪已遷移的 reader 不再讀 local，writer 已收斂為 coordinator-only。Final 步驟才安全移除 4 個宣告。

## 3. Writer 收斂前後對比

### 3.1 W1 `restorePanelOverlayOrder`

```diff
-if (imeContainerState.isPanelBehind) {
-    isPanelBehindKeyboard = false
-    imeCoordinator.setPanelBehindKeyboard(false)
+if (imeContainerState.isPanelBehind) {
+    imeCoordinator.setPanelBehindKeyboard(false)
     panelContainer.bringToFront()
}
```

### 3.2 W2 `togglePanelLayer`

```diff
-isPanelBehindKeyboard = !isPanelBehindKeyboard
-imeCoordinator.setPanelBehindKeyboard(isPanelBehindKeyboard)
+imeCoordinator.setPanelBehindKeyboard(!imeContainerState.isPanelBehind)
 if (imeContainerState.isPanelBehind) { ... }
 imePanelControlBar.updateSwapButton(imeContainerState.isPanelBehind)
```

`MutableStateFlow.update{}` 同步完成；subsequent read of `imeContainerState.isPanelBehind` 讀到翻面後的新值。

### 3.3 W3 `wrapInFloatingMode`

```diff
-isFloatingMode = true
-imeCoordinator.setFloatingMode(true)
+imeCoordinator.setFloatingMode(true)
```

### 3.4 W4 / W5 `enterGameMode / exitGameMode`

```diff
-isInGameMode = true
 imeCoordinator.setGameMode(true)

-isInGameMode = false
 imeCoordinator.setGameMode(false)
```

### 3.5 W6 `showPanel` overlay attach callback

```diff
 onOverlayPanelAttached = {
     if (imeContainerState.isPanelBehind) {
-        isPanelBehindKeyboard = false
         imeCoordinator.setPanelBehindKeyboard(false)
         panelContainer.bringToFront()
     }
 },
```

### 3.6 W7 `updatePanelSelection`

```diff
 internal fun YukiBoardIMEService.updatePanelSelection(panel: PanelType?) {
-    imePanelCoordinator.syncPanelSelection(panel) { selectedPanel ->
-        activePanel = selectedPanel
-        // toolbarView.activePanel 由 renderImeUiState collect 驅動（Sprint 3 Phase 1）
-    }
+    imePanelCoordinator.syncPanelSelection(panel) { }
 }
```

`syncPanelSelection` 內部呼叫 `imeCoordinator.showPanel(panel)` 負責 SSOT 寫入。Lambda 已成空 body；日後 `syncPanelSelection` 可移除 `updateSelection` 參數，屬後續 cleanup，非本輪範圍。

## 4. Local field 移除

`YukiBoardIMEService.kt`：

```diff
-internal var isPanelBehindKeyboard = false
+// Sprint 5 Round 3: isPanelBehindKeyboard local field removed；SSOT = imeCoordinator.uiState

-internal var activePanel: PanelType? = null
+// Sprint 5 Round 3: activePanel local field removed；SSOT = imeCoordinator.uiState.activePanel

-internal var isFloatingMode = false
+// Sprint 5 Round 3: isFloatingMode local field removed；SSOT = imeCoordinator.uiState.isFloatingMode

-internal var isInGameMode = false
+// Sprint 5 Round 3: isInGameMode local field removed；SSOT = imeCoordinator.uiState.isInGameMode
```

## 5. SSOT 驗證

Grep `(isFloatingMode|isPanelBehindKeyboard|isInGameMode)` 全模組結果（排除註解、`ImeUiState` 欄位、coordinator setter、`ImeContainerStateConsumer` getter）：**零 local reference**。

Grep `activePanel` 全模組結果：所有 service-package 引用皆透過 `imeContainerState.activePanel` 或 `state.activePanel`（collect 流 → toolbar render）。

## 6. 驗證結果

- [x] `./gradlew :app:compileDebugKotlin` 通過（BUILD SUCCESSFUL in 5s）
- [x] Writer 全部收斂至 coordinator 單一寫入點（W1–W7，W8 原本即為 coordinator-only）
- [x] 4 個 local field 全部移除（`YukiBoardIMEService.kt` L95 / L143 / L210 / L226 改為標記註解）
- [x] R5 / R11 依 Round 3 前置遷移完成（為 local field 移除鋪路）
- [x] 禁區完全未動：`KeyboardView.kt` / `InputRouter.kt` / network / AI debounce / swipe / HANDWRITING RMW
- [x] Sprint 4 邊界（`InputUiSync`）未被觸動

## 7. Sprint 5 收尾狀態

| Round | 範圍 | 狀態 |
|-------|------|------|
| R1 | 建立 `ImeContainerStateConsumer` read-only 邊界 | ✅ 已落地 |
| R2 | 9/11 reader 遷移、2 inline 保留（HANDWRITING） | ✅ 已落地 |
| R3 | Writer 收斂 + local field 移除；R5/R11 最後一哩遷移 | ✅ 已落地（本輪） |

**Sprint 5 整體目標達成**：
- `isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel / remoteConnectionState` 五個 container / external state 欄位，單一來源（SSOT）均為 `imeCoordinator.uiState`
- `YukiBoardIMEService` 不再持有任何 container-state mutable mirror
- 讀取走 `imeContainerState` facade（或 collect flow 推 UI），寫入走 `imeCoordinator` setter
- 與 Sprint 4 的 `InputUiSync`（composing / candidates render sync）職責邊界明確不重疊

## 8. 後續建議（非本輪範圍）

- `ImePanelCoordinator.syncPanelSelection(panel, updateSelection)` 的 `updateSelection` 參數已無作用，可精簡；但屬 app/ime 包內 API 小修，非 Sprint 5 硬需求
- `imeContainerState` 本身已就位，未來新增 container-scope state 欄位（若 Sprint 6+ 有需要）可循相同 pattern 擴充 getter
- `ToolbarView.activePanel` 仍是獨立的 View-layer field，由 `renderImeUiState` collect 單向驅動，非本 Sprint 處理範圍

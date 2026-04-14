# renderImeUiState 消費端草案（Sprint 3 第一批）

日期: 2026-04-15
角色: `Claude Code B`
前置文件: `IME_STATE_CONVERGENCE_DRAFT.md`（CodeX A 正式決策）

---

## 1. 核心結論

Sprint 3 第一批 consume 接線只做 **2 個安全欄位**，composingText/candidates 延後。

| 欄位 | Sprint 3 安全性 | 原因 |
|------|-----------------|------|
| `activePanel` | **安全，可接** | 寫入頻率低（面板切換時），無同步衝突 |
| `isVoiceInputActive` | **安全，可接** | 寫入頻率低（開始/停止語音時），無同步衝突 |
| `composingText` | **有風險，延後** | handleKeyPress (LOCKED) 同步直寫，collect 是 async Main，會 race |
| `candidates` | **有風險，延後** | 同上，applyInputResult (LOCKED) 同步直寫 |

---

## 2. 時序風險分析

### 為何 composingText / candidates 不能用 collect 驅動

```
handleKeyPress() [同步，Main thread]
  → inputRouter.processKey(keyCode)
  → applyInputResult(result)
    → keyboardView.updateComposingText(text)   // 直接寫 view，即時生效
    → keyboardView.updateCandidates(list)       // 直接寫 view，即時生效
    → syncImeCoordinatorFromRouter()            // 寫入 coordinator StateFlow

如果改為 collect 驅動：
handleKeyPress() [同步]
  → syncImeCoordinatorFromRouter()              // 寫入 StateFlow
  → ... Main loop 排程 collect ...              // 非即時
  → renderImeUiState(state)                     // 延遲到下一個 Main dispatch
    → keyboardView.updateComposingText(text)    // 太晚！使用者按鍵和看到結果之間多一幀延遲
```

每按一個鍵都多一幀延遲 = 打字體驗劣化。在 LOCKED 方法獲 Codex 審閱前，不動 composingText/candidates 的渲染路徑。

### 為何 activePanel / isVoiceInputActive 安全

- `activePanel` 的寫入只發生在使用者點選面板按鈕時（人類操作頻率 ~1Hz），一幀延遲不可感知
- `isVoiceInputActive` 的寫入只發生在語音開始/停止時（~0.1Hz），同理

---

## 3. 現況直接寫 view 盤點

### 3.1 activePanel（2 處直接寫）

| 位置 | 程式碼 | 說明 |
|------|--------|------|
| `ImeServiceMisc.kt:102` | `toolbarView.activePanel = selectedPanel` | showPanel 主流程 |
| `ImeServiceCallbacks.kt:268` | `toolbarView.activePanel = PanelType.EMOJI` | emoji callback 特殊路徑 |

Sprint 3 可安全移除：**2/2 處**（改由 renderImeUiState 驅動）

### 3.2 isVoiceInputActive（語音 UI 寫入）

| 位置 | 程式碼 | 說明 |
|------|--------|------|
| `ImeServiceInput.kt:292` | `keyboardView.voiceListeningLocale = "auto"` | 開始語音時標記 |
| `ImeServiceInput.kt:315` | `keyboardView.voiceListeningLocale = null` | 語音結果返回時清除 |
| `ImeServiceInput.kt:330` | `keyboardView.voiceListeningLocale = null` | 語音錯誤時清除 |
| `ImeServiceInput.kt:342` | `clearVoiceListeningLocale = { keyboardView.voiceListeningLocale = null }` | callback 清除 |
| `YukiBoardIMEService.kt:356` | `keyboardView.voiceListeningLocale = null` | overlay onStop |
| `YukiBoardIMEService.kt:361` | `keyboardView.voiceListeningLocale = null` | overlay onDismiss |

Sprint 3 可安全移除：**6/6 處**（改由 renderImeUiState 根據 `isVoiceInputActive` 統一控制 `voiceListeningLocale`）

注意：`voiceOverlay?.show()` / `voiceOverlay?.dismiss()` 等 overlay 操控不在第一批範圍，因為 overlay 有自己的動畫/回呼狀態，不適合純 state-driven。

### 3.3 composingText（20 處直接寫）— Sprint 3 不動

分布：`YukiBoardIMEService.kt` (8), `ImeServiceInput.kt` (7), `ImeServiceCallbacks.kt` (3), `ImeServicePanels.kt` (2)

### 3.4 candidates（26 處直接寫）— Sprint 3 不動

分布：`YukiBoardIMEService.kt` (10), `ImeServiceInput.kt` (9), `ImeServiceCallbacks.kt` (4), `ImeServicePanels.kt` (3)

---

## 4. renderImeUiState 函式草案

### 4.1 函式簽名與位置

建議放在 `ImeServiceLifecycle.kt` 作為 extension function（與其他 lifecycle/setup 邏輯同處）：

```kotlin
// ImeServiceLifecycle.kt

/**
 * Sprint 3 第一批 UI 消費端。
 * 由 serviceScope collect ImeSessionCoordinator.uiState 驅動。
 * 只處理 activePanel + isVoiceInputActive，不處理 composingText/candidates。
 */
internal fun YukiBoardIMEService.renderImeUiState(state: ImeUiState) {
    // --- activePanel ---
    toolbarView.activePanel = state.activePanel

    // --- isVoiceInputActive → voiceListeningLocale ---
    keyboardView.voiceListeningLocale = if (state.isVoiceInputActive) "auto" else null
}
```

### 4.2 collect 啟動點

在 `onCreateInputView()` 或 `onStartInputView()` 內啟動（只啟動一次）：

```kotlin
// YukiBoardIMEService.kt — onCreateInputView 末尾

private var uiStateCollectJob: Job? = null

// 在 onCreateInputView 中：
uiStateCollectJob?.cancel()
uiStateCollectJob = serviceScope.launch {
    imeCoordinator.uiState.collect { state ->
        renderImeUiState(state)
    }
}
```

### 4.3 生命週期清理

```kotlin
// onDestroy 中：
uiStateCollectJob?.cancel()
uiStateCollectJob = null
```

---

## 5. 移除直接寫的對照表

### Phase 1（Sprint 3 第一批，安全）

| 原始位置 | 原始程式碼 | 改為 |
|----------|-----------|------|
| `ImeServiceMisc.kt:102` | `toolbarView.activePanel = selectedPanel` | 移除（由 renderImeUiState 驅動） |
| `ImeServiceCallbacks.kt:268` | `toolbarView.activePanel = PanelType.EMOJI` | 改為 `imeCoordinator.showPanel(PanelType.EMOJI)` |
| `ImeServiceInput.kt:292` | `keyboardView.voiceListeningLocale = "auto"` | 移除（由 renderImeUiState 驅動） |
| `ImeServiceInput.kt:315` | `keyboardView.voiceListeningLocale = null` | 移除 |
| `ImeServiceInput.kt:330` | `keyboardView.voiceListeningLocale = null` | 移除 |
| `ImeServiceInput.kt:342` | `clearVoiceListeningLocale = { ... }` | 移除（改用 `imeCoordinator.setVoiceInputActive(false)`） |
| `YukiBoardIMEService.kt:356` | `keyboardView.voiceListeningLocale = null` | 移除 |
| `YukiBoardIMEService.kt:361` | `keyboardView.voiceListeningLocale = null` | 移除 |

總計：**8 處直接寫** → 全部由 `renderImeUiState` 取代

### Phase 2（需 Codex 審閱 LOCKED 方法後）

| 欄位 | 直接寫數量 | 前置條件 |
|------|-----------|---------|
| `composingText` | 20 處 | Codex 審閱 handleKeyPress/applyInputResult，確認可改為 coordinator-first |
| `candidates` | 26 處 | 同上 |

---

## 6. 邊界條件與注意事項

### 6.1 activePanel 的 showPanel 流程

目前 `ImeServiceMisc.kt` 的 `showPanel()` 做了很多事（panel container 操作、panel 建立、動畫、overlay 切換）。`renderImeUiState` 只負責 toolbar highlight 同步，不負責 panel 打開/關閉的副作用流程。

正確順序：
1. 呼叫方觸發 `imeCoordinator.showPanel(panelType)` → 寫入 StateFlow
2. `showPanel()` 仍然直接處理 container/animation 等副作用
3. `renderImeUiState` 只同步 `toolbarView.activePanel`（純 UI 指示燈）

### 6.2 voiceOverlay 不在 renderImeUiState 範圍

`voiceOverlay?.show()` / `voiceOverlay?.dismiss()` / `voiceOverlay?.showFinal()` 等 imperative 操作保留原處。`renderImeUiState` 只控制 `keyboardView.voiceListeningLocale`（鍵盤上的語音指示 UI）。

### 6.3 collect 與直接寫共存期

Phase 1 上線後，activePanel 和 voiceListeningLocale 有兩個寫入路徑（collect + 殘留直接寫）。必須確保移除直接寫是原子操作——不能只移除一半。

建議做法：一個 commit 同時做三件事：
1. 加 `renderImeUiState` + collect 啟動
2. 移除 8 處直接寫
3. 補對應的 coordinator 寫入（如 ImeServiceCallbacks:268 改為寫 coordinator）

---

## 7. 給 Sprint 3 執行者的 checklist

- [ ] 在 `ImeServiceLifecycle.kt` 新增 `renderImeUiState(state: ImeUiState)` extension function
- [ ] 在 `YukiBoardIMEService.kt` 新增 `uiStateCollectJob` field + collect 啟動 + onDestroy 清理
- [ ] 移除 `ImeServiceMisc.kt:102` 的 `toolbarView.activePanel = selectedPanel`
- [ ] 將 `ImeServiceCallbacks.kt:268` 改為 `imeCoordinator.showPanel(PanelType.EMOJI)`
- [ ] 移除 6 處 `keyboardView.voiceListeningLocale = ...` 直接寫
- [ ] 確認 `ImeVoiceController` 的 start/stop 已正確呼叫 `imeCoordinator.setVoiceInputActive()`
- [ ] 用 `IME_SMOKE_CHECKLIST.md` 跑一輪回歸（重點：面板切換 highlight + 語音指示燈）
- [ ] compileDebugKotlin 通過
- [ ] 不動 handleKeyPress / applyInputResult / KeyboardView.kt

---

## 8. 本文件定位

這是 `Claude Code B` 的消費端細化草案，不是實作 PR。

Sprint 3 實作時，執行者（Claude Code B 或 Codex）應以本文件 + `IME_STATE_CONVERGENCE_DRAFT.md` 為對照，一次完成 Phase 1 接線。

Phase 2（composingText/candidates）需等 Codex 審閱 LOCKED 方法後才能動。

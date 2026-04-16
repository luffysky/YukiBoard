# Sprint 5 Mode Container State Plan

日期: 2026-04-16  
角色: `CodeX B`  
用途: 作為 Sprint 5 的正式 consume 邊界文件，定義 mode / container / external state 應如何收斂，以及哪些副作用仍由 `YukiBoardIMEService` 保留

---

## 1. Sprint 5 範圍

Sprint 5 只處理四個欄位：

- `remoteConnectionState`
- `isFloatingMode`
- `isPanelBehindKeyboard`
- `isInGameMode`

這四個欄位都已經有 coordinator 寫入端。

Sprint 5 的問題不是「要不要放進 uiState」，而是：

> 哪些 UI consume 可以收斂，哪些 platform/container 副作用仍必須留在 service。

---

## 2. 四個欄位的性質

### 2.1 isFloatingMode

性質：

- container mode
- 牽涉 `wrapInFloatingMode()`、`floatingWrapper`、`floatingBall`
- 有 view 建立、位置保存、最小化/還原等副作用

結論：

- `uiState.isFloatingMode` 可作為顯示層事實來源
- 但 floating wrapper 建立/拆除仍由 service 直控

### 2.2 isPanelBehindKeyboard

性質：

- container layer state
- 牽涉 `bringToFront()`、swap button 外觀、panel/container 堆疊

結論：

- `uiState.isPanelBehindKeyboard` 可驅動純 UI 標示
- `bringToFront()` 這類 imperative container 操作仍由 service 直控

### 2.3 remoteConnectionState

性質：

- external integration state
- 來源來自 Bluetooth / Wi-Fi listener
- 同時影響 remote panel 顯示

結論：

- `uiState.remoteConnectionState` 應成為狀態事實來源
- remote server / HID 啟停與 listener 註冊不屬於 collect 範圍

### 2.4 isInGameMode

性質：

- major mode switch
- 牽涉 `keyboardView` / `gameKeyboardView` / `gameMenuView` / `glSurfaceView`
- 同時影響感測器與一般輸入路徑

結論：

- `uiState.isInGameMode` 可驅動部分 mode indicator
- 真正切換整個 keyboard container 的副作用仍由 service 保留

---

## 3. Sprint 5 建議 consume 原則

Sprint 5 應遵守：

1. service 仍是唯一 collect owner
2. 先收純 UI consume，不收 platform/container 建立與拆除
3. collect 只做顯示同步，不做昂貴副作用
4. 不把 `KeyboardView.kt` 變成新的 state owner

---

## 4. 可安全 collect 的部分

以下屬於 Sprint 5 可優先收斂的 consume 類型：

1. control bar / indicator 的顯示狀態
2. remote panel 上的狀態標示
3. swap button / floating mode icon / game mode badge 類 UI 提示
4. mode 相關的純顯示文字

---

## 5. 應保留 service 直控的部分

以下不應在 Sprint 5 直接 collect 化：

1. `bringToFront()`
2. `setInputView(onCreateInputView())`
3. floating wrapper / floating ball 建立與移除
4. Bluetooth / Wi-Fi server start/stop
5. game menu / game keyboard / GL surface 切換

原因很單純：

- 這些都是 platform/container/integration 副作用
- 不是純 render
- collect 只適合作為 consume owner，不適合作為副作用調度器

---

## 6. Sprint 5 的建議產出

建議新增一個很薄的 mode consume 入口，例如：

- `renderImeModeState(state: ImeUiState)`

第一版只做：

- floating mode indicator
- panel layer indicator
- remote connection badge
- game mode indicator

不做：

- 真正切換容器
- 真正啟停外部連線
- 真正切換 game UI tree

---

## 7. Sprint 5 完成條件

本階段要算完成，至少要同時滿足：

1. 四個欄位都有正式 consume owner
2. service 內不再分散直接維護同一組 indicator 顯示
3. platform/container 副作用仍維持可追蹤
4. 不與 Sprint 4 的輸入熱路徑重構互撞

---

## 8. 與 Sprint 6 的分界

Sprint 5 不處理高依賴 panel。

尤其不應提前展開：

- `THEME`
- `CLAW`
- `REMOTE`
- `HANDWRITING`
- `MUSIC`
- `USER_DICT`

`remoteConnectionState` 在 Sprint 5 只處理狀態 consume，不處理 `REMOTE` panel 的完整重構。

---

## 9. CodeX B 正式判定

### 判定 A

Sprint 5 已具備規劃條件，但還沒有正式 consume 收斂實作。

### 判定 B

Sprint 5 的成功關鍵，是不要把 mode state consume 與 container/integration 副作用混成同一層。

### 判定 C

若 Sprint 4 尚未穩定，Sprint 5 不應搶先實作，以免同時打到 input loop 與 container mode。

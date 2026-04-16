# Sprint 4 Input Flow Review

日期: 2026-04-16  
角色: `CodeX B`  
用途: 作為 Sprint 4 的正式技術邊界文件，定義核心輸入主流程應如何審核、如何拆分，以及本階段明確不做的事

---

## 1. Sprint 4 的定位

Sprint 4 不是重寫輸入引擎。

Sprint 4 的真正目標是：

- 重新審核 `handleKeyPress()`
- 重新審核 `applyInputResult()`
- 把目前混在 service 內的輸入決策、執行、副作用同步拆成可維護結構

這一階段的核心產出應是「可拆的邊界」，不是「功能大搬家」。

---

## 2. 目前主流程的問題

`handleKeyPress()` 目前同時負責：

- session / swipe / panel 狀態前置清理
- panel input route
- InputConnection batch edit
- keyCode 解譯
- inputRouter 執行
- UI 同步
- AI candidate request 觸發
- 特例功能鍵處理

`applyInputResult()` 目前同時負責：

- pending commit 處理
- InputConnection commit / composing 操作
- character / bubble / fraud / tone / sticker 建議等副作用
- remote forwarding
- sentence buffer 累積
- 最後再 `syncImeCoordinatorFromRouter()`

這種結構的問題不是只有大，而是：

- 熱路徑與副作用耦合
- render 責任不單一
- 無法安全推進 Sprint 3 Phase 2
- 無法判斷哪些步驟可同步保留，哪些可後移

---

## 3. Sprint 4 建議拆法

### 3.1 KeyEventInterpreter

責任：

- 解譯 keyCode / label
- 判斷屬於哪一類 action
- 決定是 panel route、direct key send、router input、mode toggle，還是特殊功能鍵

不負責：

- 直接操作 `InputConnection`
- 直接更新 `keyboardView`
- 直接做副作用

### 3.2 InputActionExecutor

責任：

- 依 action 執行 router / `InputConnection` / commit 行為
- 維持輸入邏輯正確性
- 回傳結構化的執行結果

不負責：

- UI render
- toolbar / panel 外觀同步
- toast / bubble / animation 類表現層細節

### 3.3 InputUiSync

責任：

- 統一同步 `composingText / candidates`
- 統一處理清空與 post-update render
- 明確標示哪些屬於同步 render，哪些未來可轉為 state-driven

不負責：

- inputRouter 演算法
- 外部整合 client
- panel container 操作

---

## 4. 建議的最小切分順序

Sprint 4 正確順序應為：

1. 先把 `handleKeyPress()` 內 action 類型分類
2. 再抽出 `InputUiSync`
3. 再抽出 `InputActionExecutor`
4. 最後才考慮是否需要 `KeyEventInterpreter` 獨立檔案

原因：

- `InputUiSync` 是 Sprint 3 Phase 2 的阻塞點
- 先抽 render ownership，比先抽演算法更能降低風險
- `handleKeyPress()` 現在最大的危險是 responsibilities 混在一起，不是單純 case 太多

---

## 5. Sprint 4 的完成條件

本階段要算完成，至少要滿足：

1. `handleKeyPress()` 的邏輯分層已可明確描述
2. `applyInputResult()` 的副作用分類已可明確描述
3. `composing/candidates` 的同步 render owner 已明確
4. Phase 2 readiness 能從 `NOT READY` 提升到可評估 `READY`
5. 不破壞既有輸入體感

---

## 6. Sprint 4 明確不做

1. 不全面重寫 `InputRouter`
2. 不重寫各語言引擎
3. 不同步重構所有 panel
4. 不把所有副作用一次搬去 repository/use case
5. 不為了拆檔而拆檔

---

## 7. 與 Sprint 5 的分界

Sprint 4 處理的是：

- 輸入主流程
- `composingText / candidates`
- 熱路徑 render ownership

Sprint 5 處理的是：

- `isFloatingMode`
- `isPanelBehindKeyboard`
- `remoteConnectionState`
- `isInGameMode`

也就是：

- Sprint 4 解決 input loop
- Sprint 5 解決 mode / container / external state consume

這兩階段不能混做，否則很容易把輸入熱路徑與 layout/container 副作用一起打散。

---

## 8. CodeX B 正式判定

### 判定 A

Sprint 4 的第一優先不是抽更多 helper，而是建立 `InputUiSync` 的正式邊界。

### 判定 B

`applyInputResult()` 不該在 Sprint 4 一次被清空副作用；應先分類，再決定哪些能後移。

### 判定 C

Sprint 4 可以啟動的前提，是先接受今天的事實：

- Sprint 3 Phase 2 還不能直接做 collect 化
- Sprint 4 必須先審核心輸入主流程

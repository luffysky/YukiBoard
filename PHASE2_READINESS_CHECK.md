# PHASE2 Readiness Check

日期: 2026-04-16  
角色: `CodeX B`  
用途: 作為 Sprint 3 Phase 2 的正式啟動判定文件，定義 `composingText / candidates` 是否可以進入 collect-driven consume，以及進入前必須滿足的技術條件

---

## 1. 結論

目前判定：`Sprint 3 Phase 2 = NOT READY`

原因不是 `ImeSessionCoordinator` 沒有 state，而是：

- `composingText / candidates` 仍高度依賴同步直寫 UI
- 熱路徑仍集中在 `handleKeyPress()` / `applyInputResult()`
- panel / callback / candidate select / swipe / voice 等特例仍混用同步 render
- 若現在直接改成 collect 驅動，最可能出現的是一幀延遲、顯示回跳、候選列不同步

一句話結論：

> Phase 2 現在能做的是「收斂 render 邊界與熱路徑分類」，不能做「把 composing/candidates 改成 collect 驅動」。

---

## 2. 目前已具備的前置

- `ImeSessionCoordinator.uiState` 已有：
  - `composingText`
  - `candidates`
- `syncImeCoordinatorFromRouter()` 已在 `applyInputResult()` 後同步寫入 coordinator
- `renderImeUiState(state)` 已存在
- `startUiStateCollect()` 已存在
- Sprint 3 Phase 1 已完成：
  - `activePanel`
  - `isVoiceInputActive`

這代表 Phase 2 不是從零開始，而是卡在：

- 熱路徑仍是 router/service 直接推 UI
- collect 入口還不是 `composing/candidates` 的唯一 render owner

---

## 3. 熱路徑分類

### 3.1 絕對熱路徑

以下路徑屬於打字當下必須同步完成的 UI 更新，不可先改成 async collect 驅動：

1. `handleKeyPress()`
2. `applyInputResult()`
3. `handleDelete()`
4. `handleEnter()`
5. `handleSpace()`
6. `routeKeyToPanel()` 內的 CJK composing/candidate 更新
7. 候選字選取後的即時 composing/candidate 刷新

原因：

- 這些路徑都在一次按鍵或一次候選確認內完成
- `InputConnection` 與 `keyboardView` 更新必須維持同一個同步節奏
- 若把 `keyboardView.updateComposingText()` / `updateCandidates()` 延後到 collect，最容易出現輸入已提交但畫面晚一拍

### 3.2 次熱路徑

以下路徑不是每鍵必走，但仍可能與輸入主流程交錯：

- `setOnRawCommitListener`
- `setOnCandidateSelectListener`
- `handleSwipeInput()` 與 swipe 候選確認
- `onLanguageSelectedListener`
- 語音結果回填到 composing/candidates 的路徑

這些路徑仍不能直接 collect 化，但可以先整理成統一 render helper。

### 3.3 冷路徑

以下較適合先收斂成 helper 或 render 入口：

- session reset / cleanup 時清空 composing/candidates
- panel 關閉時清空 composing/candidates
- language switch 後的候選清空
- handwriting / AI extra candidate 類清理路徑

---

## 4. 目前直接寫 UI 的結構判斷

現況盤點已知：

- `composingText` 直接寫約 20 處
- `candidates` 直接寫約 26 處

分布不是單純散落，而是有三種類型：

1. 熱路徑同步 render
2. callback / cleanup 類清空
3. panel / gesture / candidate interaction 特例

這代表「先把所有直接寫 UI 收掉」不是正確目標。

正確目標是：

- 保留必要同步 render
- 消滅重複且語意不明的清理路徑
- 明確切出唯一 render helper

---

## 5. Ready 前必須完成的條件

Sprint 3 Phase 2 若要從 `NOT READY` 變成 `READY`，至少要同時滿足：

1. `composingText / candidates` 的 render helper 已定義
2. 熱路徑與冷路徑已正式分開
3. `handleKeyPress()` 的 post-update 區塊已能被單獨描述
4. `applyInputResult()` 與後續 UI 同步責任已釐清
5. callback / panel / swipe 類清理路徑已收斂到少數 helper
6. 不再新增新的 `keyboardView.updateComposingText()` / `updateCandidates()` 分散寫入點
7. 有一份明確文件說明：
   - 哪些地方保留同步直寫
   - 哪些地方改走 helper
   - 哪些地方未來才可能 collect 化

缺其中任何一項，都不應拍板進 Phase 2 collect 實作。

---

## 6. 允許先做的前置收斂

目前允許做，而且建議優先做的事：

1. 新增單一 helper，例如：
   - `renderComposingAndCandidatesFromRouter()`
   - `clearComposingAndCandidates()`
2. 將 cleanup 類重複路徑改用 helper
3. 將 panel/callback 特例重新分類
4. 撰寫 `handleKeyPress()` post-update 區塊的拆分草案
5. 為 Sprint 4 定義：
   - `KeyEventInterpreter`
   - `InputActionExecutor`
   - `InputUiSync`

---

## 7. 明確不能做的事

在 `READY` 之前，禁止：

1. 直接把 `composingText` 改為 collect 驅動
2. 直接把 `candidates` 改為 collect 驅動
3. 在 `KeyboardView.kt` 內直接 collect `uiState`
4. 為了 collect 而提前重寫 `handleKeyPress()`
5. 為了「看起來單一狀態」而讓輸入體感多一幀延遲

---

## 8. 與 Sprint 4 的關係

Sprint 3 Phase 2 是否 ready，實際上就是在回答：

> `handleKeyPress()` / `applyInputResult()` 是否已經被審核到足以拆出正式輸入主流程邊界？

目前答案是否定的。

因此本文件的正式結論是：

- Sprint 3 先停在 `Phase 2 readiness complete, implementation deferred`
- Sprint 4 先做核心輸入主流程審核與結構切分設計

---

## 9. CodeX B 正式判定

### 判定 A

`composingText / candidates` 目前不是缺 state，而是缺「同步 render ownership」。

### 判定 B

目前所有 Phase 2 前置工作都應服務於 Sprint 4，而不是繞過 Sprint 4。

### 判定 C

今天可以完成的是：

- Phase 2 readiness check
- Sprint 4 邊界定義
- Sprint 5 consume 邊界定義

今天不能誤報完成的是：

- Phase 2 collect 實作
- `handleKeyPress()` 重構
- `applyInputResult()` 重構

# Sprint Master Plan（2026-04-15）

用途：把 `SnowRealmYukiBoard` 這一輪重構從已完成項到後續數天的執行順序，整理成一份唯一主表。

這份文件包含：

- 已完成的 Sprint / Phase
- 後續 Sprint / Phase 的正式順序
- `CodeX A / CodeX B / Claude Code A / Claude Code B` 的分工
- 每階段完成條件
- 每階段明確不做的事
- 每一個 Sprint / Phase 的 gate 規則

---

## 1. 總目標

這次重構不是為了單純降行數，而是為了把專案收斂到：

1. 核心控制器不再無限膨脹
2. 狀態來源更單一
3. integration boundary 更清楚
4. repo-ready 文件、檢查表、交接流程完整
5. 在不破壞既有功能的前提下，為後續核心主流程重構鋪路

---

## Gate 規則（全 Sprint 通用）

從這份主表開始，所有 Sprint / Phase 一律套用同一條規則：

1. 執行者可做盤點、草案、實作、文件
2. `CodeX B` 可做技術審核與 ready 建議
3. 只有 `CodeX A` 可正式拍板：
   - `READY`
   - `NOT READY`
   - `LOCKED`
4. 沒有 `CodeX A` 的明確核可，不得進下一個 Sprint 或下一個 Phase
5. 詳細規則見：`RULE/SPRINT_GATE_RULE.md`

---

## 2. 已完成 Sprint

## Sprint 1：骨架建立

狀態：`已完成`

目標：

- 建立 `UiState / Coordinator / Repository` 基礎骨架

已完成落點：

- `app/ime/ImeUiState.kt`
- `app/ime/ImeSessionCoordinator.kt`
- `app/settings/SettingsUiState.kt`
- `app/settings/SettingsScreenCoordinator.kt`
- `app/session/UserSessionRepository.kt`

完成判定：

- IME 與 Settings 都已有正式狀態骨架
- 後續重構不再只能依賴 activity / service 私有欄位

本階段不處理：

- 大規模輸入主流程重構
- 所有既有 UI 的 consume 接線

---

## Sprint 2：低風險薄切

狀態：`已完成`

目標：

- 把 `YukiBoardIMEService` 從超大控制器開始切成薄殼
- 只搬低風險邊界
- 不碰 `handleKeyPress()` / `applyInputResult()` 主語意

已完成落點：

- `ImePanelFactory`
- `ImePanelRouter`
- `ImePanelBindings`
- `ImeSessionHooks`
- `ImeVoiceController`
- `ImeThemeRenderDelegate`
- `ImeCandidateInteractionHandler`
- `ImeServiceCallbacks.kt`
- `ImeServiceInput.kt`
- `ImeServiceLifecycle.kt`
- `ImeServiceMisc.kt`
- `ImeServicePanels.kt`

已完成的關鍵成果：

- `YukiBoardIMEService.kt` 已壓到健康範圍
- `showPanel()` 與面板建立路徑已收斂
- `STICKER` 已進入 factory 路徑
- 高依賴 panel 沒有被提前亂搬

完成判定：

- compile 通過
- 核心主流程未被誤改
- panel / lifecycle / service helper 已形成新骨架

本階段不處理：

- `KeyboardView.kt` 深拆
- `USER_DICT`
- `THEME / CLAW / REMOTE / HANDWRITING / MUSIC`

---

## Sprint 3：正式 consume 端接線

狀態：`進行中`

目標：

- 讓 `ImeSessionCoordinator.uiState` 不再只是寫入骨架
- 開始有正式 UI consume 路徑

### Sprint 3 Phase 1

狀態：`已完成`

範圍：

- `activePanel`
- `isVoiceInputActive`

已完成內容：

- `renderImeUiState(state)`
- `startUiStateCollect()`
- `uiStateCollectJob` lifecycle 管理
- `toolbarView.activePanel` 改由 collect 驅動
- `keyboardView.voiceListeningLocale` 改由 collect 驅動

完成判定：

- compile 通過
- 文件一致
- 不碰 `handleKeyPress()` / `applyInputResult()`

### Sprint 3 Phase 2

狀態：`尚未 ready（2026-04-16 已由 CodeX B 正式確認）`

目標：

- `composingText`
- `candidates`

目前為何不能直接做：

- 同步直寫 UI 路徑仍過多
- `handleKeyPress()` / `applyInputResult()` 仍是熱路徑
- 現階段若直接 collect 化，會有 race / 延遲風險

這階段目前只能做：

- 寫入點盤點
- 熱路徑分類
- render 入口前置設計
- readiness check

不能做：

- 真正把 `composingText / candidates` 改成 collect 驅動
- 見：`PHASE2_READINESS_CHECK.md`

---

## 3. 後續 Sprint 順序

## Sprint 4：核心輸入主流程審核與重構

啟動條件：

- Sprint 3 Phase 2 完成
- `composingText / candidates` consume 邊界已釐清
- 熱路徑同步更新與 render 入口已整理完
- 並且 `CodeX A` 已正式核可進 Sprint 4

目標：

- 重審 `handleKeyPress()`
- 重審 `applyInputResult()`
- 開始把輸入主流程拆成可維護結構

建議拆法：

1. `KeyEventInterpreter`
2. `InputActionExecutor`
3. `InputUiSync`

不一次做的事：

- 不直接全面重寫輸入法引擎
- 不同步重構所有 panel

2026-04-16 補充：

- `CodeX B` 已補 Sprint 4 正式邊界文件：`SPRINT4_INPUT_FLOW_REVIEW_2026-04-16.md`
- 目前狀態為：`邊界已定義，尚未進核心實作`

---

## Sprint 5：Mode / Container / External State 收斂

目標：

- `remoteConnectionState`
- `isFloatingMode`
- `isPanelBehindKeyboard`
- `isInGameMode`

說明：

- 這是 mode / container / external integration state 的 consume 收斂
- 仍先由 `YukiBoardIMEService` 當 consume owner
- 不急著讓 `KeyboardView.kt` 自己 collect
- 並且只有在 `CodeX A` 正式核可後才可啟動

2026-04-16 補充：

- `CodeX B` 已補 Sprint 5 正式邊界文件：`SPRINT5_MODE_CONTAINER_STATE_PLAN_2026-04-16.md`
- 目前狀態為：`consume 邊界已定義，尚未進實作`

---

## Sprint 6：高依賴 Panel 新邊界

目標：

- 開始處理 integration-heavy panel

順序：

1. `THEME`
2. `CLAW`
3. `REMOTE`
4. `HANDWRITING`
5. `MUSIC`
6. `USER_DICT`

原則：

- 一次只處理一個高依賴 panel 類別
- 不把多個外部系統重構混在一輪
- 並且只有在 `CodeX A` 正式核可後才可啟動

---

## Sprint 7：整合層與狀態來源總收斂

目標：

- 收斂分散的 `OkHttpClient.Builder()`
- 進一步收斂 auth / provider / sync / external gateway
- 減少 prefs / singleton / mutable field 多頭狀態

說明：

- 這一階段偏 integration / application 層治理
- 不一定只動 IME
- 並且只有在 `CodeX A` 正式核可後才可啟動

---

## 4. 各角色分工

## CodeX A

角色：

- 架構監督
- 階段判定
- 風險控管
- 文件同步

固定任務：

- 檢查各 Sprint / Phase 是否真的達標
- 決定能不能進下一階段
- 防止有人提前碰 locked/high-risk 範圍
- 維持主文件一致：
  - `CODEX.md`
  - `BIG_FILE_REFACTOR_PLAN_0414.md`
  - `todo_list_0414_new.md`
  - 本文件

最適合做的事：

- 審核
- 判定 ready / not ready
- 更新總計畫

不適合做的事：

- 和其他三方搶同一批實作檔

---

## CodeX B

角色：

- IME 架構決策
- Sprint 邊界定義
- readiness check

固定任務：

- 為 IME 每個 Sprint / Phase 定義正式技術邊界
- 決定哪些欄位可進 consume
- 決定哪些 panel 仍延後
- 產出：
  - `IME_STATE_CONVERGENCE_DRAFT.md`
  - `REPO_READY_IME_RISK_SUMMARY_2026-04-15.md`
  - 後續 `PHASE2_READINESS_CHECK.md`

最適合做的事：

- 風險排序
- consume 邊界設計
- 高依賴主題分期

不適合做的事：

- 直接衝進 locked 核心主流程實作

---

## Claude Code A

角色：

- Settings / Auth / Sync / Provider / repo-ready 文檔收尾

已完成重點：

- settings section 化
- `verifySessionAsync()` 收斂
- `SpeechSettingsSection` API key 注入收斂
- `RULE/SETTINGS_SMOKE_CHECKLIST.md`

後續任務定位：

- repo-ready 收尾
- settings 風險摘要
- checklist 維護
- 不再讓 `SettingsActivity` 反彈

不適合做的事：

- 不碰 IME
- 不主導核心輸入主流程重構

---

## Claude Code B

角色：

- IME consume 端落地
- IME checklist / runtime 驗證文檔
- Phase 2 前置盤點

已完成重點：

- Sprint 3 Phase 1 consume 端接線
- `IME_SMOKE_CHECKLIST.md`
- `renderImeUiState(...)`

後續任務定位：

- 盤點 `updateComposingText / updateCandidates` 寫入點
- 提出單一 render 入口草案
- 做 Phase 2 前置，不做 Phase 2 collect 實作

不適合做的事：

- 不直接碰 `handleKeyPress()`
- 不直接碰 `applyInputResult()`
- 不切 `KeyboardView.kt`

---

## 5. 接下來幾天的執行順序

### Day 1

- `Claude Code A`
  - settings repo-ready 收尾
  - 更新 settings smoke checklist
- `Claude Code B`
  - 盤點 composing/candidates 直接寫 UI 路徑
- `CodeX B`
  - 寫 `PHASE2_READINESS_CHECK.md`
- `CodeX A`
  - 做 `Phase 2 ready / not ready` 判定

### Day 2

- 若 `Phase 2 not ready`
  - 持續補前置收斂
- 若 `Phase 2 ready`
  - 由 `CodeX A` 拍板正式進 Sprint 3 Phase 2
  - `Claude Code B` 執行 consume 端第一版
  - `CodeX B` 盯邊界不越界

### Day 3+

- 完成 Sprint 3 後
  - 準備進 Sprint 4
  - 先審 `handleKeyPress()`
  - 再審 `applyInputResult()`

---

## 6. 每個階段的共通完成條件

任何 Sprint / Phase 要算完成，至少要同時滿足：

1. compile 通過
2. 文件一致
3. 沒有越界碰禁止範圍
4. checklist 已更新
5. `CodeX A` 已正式審核並明確判定可進下一階段
6. 可清楚說明：
   - 改了什麼
   - 沒改什麼
   - 還剩什麼風險

---

## 7. 全程禁止事項

除非 `CodeX A` 正式拍板進下一階段，否則：

- 不提前做 `composingText / candidates` collect 化
- 不提前碰 `handleKeyPress()`
- 不提前碰 `applyInputResult()`
- 不提前切 `KeyboardView.kt`
- 不提前做 `USER_DICT`
- 不提前展開高依賴 panel
- 不為了好看而犧牲功能完整性
- 不自行宣告下一 Sprint 或下一 Phase 已解鎖

補充正式 gate：

- 每一步 Sprint 與每一個 Phase，都必須先經 `CodeX A` 審核通過
- `CodeX B`、`Claude Code A`、`Claude Code B` 可完成規劃、實作、文件與前置盤點
- 但沒有 `CodeX A` 的明確核可，不得宣告進入下一階段

---

## 8. 當前正式結論

目前狀態是：

- `Sprint 1`：完成
- `Sprint 2`：完成
- `Sprint 3 Phase 1`：完成
- `Sprint 3 Phase 2`：尚未 ready，只能做前置

所以這幾天的主線不是亂開新題，而是：

> 先把 Sprint 3 做完整，再進 Sprint 4 動核心主流程。

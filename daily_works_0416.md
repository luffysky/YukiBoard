下次從 [SPRINT6_7_EXECUTION_PLAN_2026-04-17.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/SPRINT6_7_EXECUTION_PLAN_2026-04-17.md) 開始做

# daily_works_0416

身份：CodeX A

日期：2026-04-16 ~ 2026-04-17

用途：

- 補記 0416 與 0417 這兩天 `CodeX A` 的正式工作日誌
- 只記錄 `CodeX A` 自身負責的工作流、監督、審核、gate 判定與文件同步
- 若前一個終端已有 `CodeX A` 相關輸出，視為本輪監督工作的一部分，一併納入

## 1. 0416 的主要工作流：重新收口 Sprint 3 / 4 / 5 gate

### 1.1 重新建立基線與審核順序

- 重新讀取並核對：
  - `CODEX.md`
  - `RULE/AI_PROTOCOL.md`
  - `RULE/SPRINT_GATE_RULE.md`
  - `SPRINT_MASTER_PLAN_2026-04-15.md`
  - `todo_list_0414_new.md`
  - `IME_STATE_CONVERGENCE_DRAFT.md`
  - `REPO_READY_IME_RISK_SUMMARY_2026-04-15.md`
- 明確把 `CodeX A` 角色拉回 gate owner，而不是參與實作。
- 確認後續所有 Sprint / Phase / readiness step 都必須先經 `CodeX A` 審核。

### 1.2 補建與確認 Phase 2 readiness 依據

- 補建並核對 `PHASE2_READINESS_CHECK.md`。
- 重新確認 `composingText / candidates` 尚未具備直接 collect 化條件。
- 把阻塞點收斂成 3 條核心結論：
  1. `keyboardView.updateComposingText(...)` 仍有大量 service 端直寫
  2. `keyboardView.updateCandidates(...)` 仍有大量 service 端直寫
  3. `handleKeyPress()` / `applyInputResult()` 仍屬 locked 熱路徑

### 1.3 正式鎖住 Sprint 4 / 5

- 依 gating 規則正式判定：
  - `Sprint 3 Phase 2 = NOT READY`
  - `Sprint 4 = LOCKED`
  - `Sprint 5 = LOCKED`
- 明確阻止「直接宣告今天完成 Sprint 3 / 4 / 5」的說法。
- 強制要求三方先做 readiness 前置，而不是偷跑 implementation。

### 1.4 對其他終端的監督安排

- 對 `Claude Code B`：
  - 只准做 `composingText / candidates` 直寫路徑盤點
  - 只准做熱路徑分類與 consume 草案
  - 不准直接 collect 化
  - 不准碰 `handleKeyPress()` / `applyInputResult()` / `KeyboardView.kt`
- 對 `CodeX B`：
  - 只准做 readiness 邊界驗證
  - 只准整理 Sprint 4 啟動前的技術條件
  - 不准直接衝 Sprint 4 實作
- 對 `Claude Code A`：
  - 維持 settings / repo-ready 收尾
  - 不碰 IME
  - 不讓 `SettingsActivity` 反彈

### 1.5 文件輸出與同步

- 產出 [CODEXA_SUPERVISION_SUMMARY_2026-04-16.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/CODEXA_SUPERVISION_SUMMARY_2026-04-16.md)
- 把 0416 當天的正式監督結論固定為可交接狀態。
- 確保主表、規則、待辦與監督摘要在 0416 當天是一致的。

## 2. 0417 的主要工作流：Sprint 4 / 5 gate、逐輪審核與收尾

### 2.1 接手 Round-based 審核模式

- 從前一個終端已完成的文件與交接中，接手 0417 的 round-based 審核工作。
- 將 `Claude Code B` 與 `CodeX B` 的 round 回交統一轉成 `CodeX A` gate 判定。
- 審核重點固定為：
  - file scope 是否越界
  - 是否真的只做該 round 被授權的事
  - compile 是否獨立通過
  - 文件與 checklist 是否同步
  - 下一 round 是否具備啟動條件

### 2.2 審核 Sprint 4 完成條件

- 讀取並核對：
  - `docs/SPRINT4_INPUTUISYNC_ROUND1.md`
  - `docs/SPRINT4_INPUTUISYNC_ROUND2.md`
  - `docs/SPRINT4_INPUTUISYNC_ROUND3.md`
  - `docs/INPUT_UI_SYNC_BOUNDARY.md`
  - `IME_SMOKE_CHECKLIST.md`
- 確認 Sprint 4 的核心成果包括：
  - `InputUiSync` 正式邊界建立
  - `syncImeCoordinatorFromRouter()` 縮到只剩 `setInputMode`
  - `applyInputResult()` 本體未被誤拆
  - Sprint 4 保留區 A/B/C/D/E 類正式化
- 正式判定 `Sprint 4 = COMPLETED`。

### 2.3 審核 Sprint 5 Round 1

- 核對 `ImeContainerStateConsumer.kt` 的建立、service wiring、與文件一致性。
- 確認 Round 1 只做 read-only boundary，不做 reader/writer 遷移。
- 自行重跑 `:app:compileDebugKotlin` 做獨立驗證。
- 正式放行 `Sprint 5 Round 2`。

### 2.4 審核 Sprint 5 Round 2

- 逐點核對 9/11 處 reader 遷移：
  - `R1 / R2 / R3 / R4 / R6 / R7 / R8 / R9 / R10`
- 確認 `R5 / R11` 因 HANDWRITING guard 保留 inline。
- 確認 Round 2 維持 read-only consume 性質：
  - writer 零變動
  - local field 未移除
  - 高風險主題未被混入
- 自行 grep + compile 驗證後，正式放行 `Sprint 5 Round 3`。

### 2.5 審核 Sprint 5 Round 3

- 逐批核對 writer consolidation：
  - `W1 / W6`
  - `W2`
  - `W3`
  - `W4 / W5`
  - `W7`
- 特別針對 `W7 updatePanelSelection` 追加審核，確認 panel selection 寫入點仍由 `imeCoordinator.showPanel(panel)` 擔任單一來源。
- 核對 4 個 local field 已移除：
  - `isPanelBehindKeyboard`
  - `activePanel`
  - `isFloatingMode`
  - `isInGameMode`
- 再次獨立重跑 `:app:compileDebugKotlin`。
- 正式判定 `Sprint 5 = COMPLETED`。

### 2.6 重新安排所有人做 Sprint 5 驗證

- 在 Sprint 5 完成後，沒有直接開 Sprint 6，而是先安排三方改做驗證與巡檢：
  - `Claude Code B`：Sprint 5 runtime / smoke 驗證
  - `CodeX B`：smoke 結果技術審核與風險分類
  - `Claude Code A`：settings / repo-ready 巡檢維持
- 自身負責收驗證回報，並決定 Sprint 5 是不是穩定完成。

### 2.7 審核 Sprint 5 smoke / runtime 結果

- 接收 `Claude Code B` 的 code-path walk-through + grep 驗證回報。
- 接收 `CodeX B` 的風險分類：
  - Sprint 5 state convergence 問題
  - 舊有 runtime 問題
  - 與 Sprint 4/5 無關的歷史問題
- 正式判定：
  - 沒有發現 Sprint 5 convergence regression
  - `floating mode stale true` 是 pre-existing runtime bug
  - 不需要回退 Sprint 5，也不應混進 Sprint 5 收尾修補

### 2.8 做出 0417 的正式總結

- 正式判定：
  - `Sprint 4 = COMPLETED`
  - `Sprint 5 = COMPLETED-STABLE`
- 補充註記：
  - `floating mode stale true` 應作為獨立小修議題，不能誤判成 Sprint 5 回歸
- 將下一步推進重心切到：
  - Sprint 6 / 7 還剩多少
  - 後續是否應先開高依賴 panel 邊界規劃
  - 如何安排四人分工並維持監督

## 3. 0416-0417 這兩天 `CodeX A` 的實際責任清單

### 3.1 Gate 與判定

- 補建 readiness 文件
- 阻止越級展開 Sprint 4 / 5
- 審核 Sprint 4 Round 1-3
- 審核 Sprint 5 Round 1-3
- 審核 Sprint 5 smoke / runtime 結果
- 正式輸出：
  - `NOT READY`
  - `LOCKED`
  - `READY`
  - `COMPLETED`
  - `COMPLETED-STABLE`

### 3.2 獨立驗證

- 多次獨立重跑 `:app:compileDebugKotlin`
- 用 `git diff` / `rg` / 文件核對方式驗證：
  - file scope
  - writer 是否未誤碰
  - reader 是否按批准範圍遷移
  - local field 是否真的安全移除
  - `W7` 是否仍維持單一寫入點

### 3.3 監督與排程

- 為三個協作角色分別指定可做與不可做的範圍
- 在每個 round 結束後重新安排下一輪任務
- 在 Sprint 5 完成後強制全員轉為驗證模式，而不是直接開 Sprint 6
- 保持 `CodeX A` 只做監督、審核、拍板，不下場搶同一批實作檔

### 3.4 文件同步

- 維持以下文件作為正式依據：
  - `CODEX.md`
  - `SPRINT_MASTER_PLAN_2026-04-15.md`
  - `todo_list_0414_new.md`
  - `CODEXA_SUPERVISION_SUMMARY_2026-04-16.md`
  - `docs/SPRINT4_INPUTUISYNC_ROUND1-3.md`
  - `docs/SPRINT5_ROUND1_STATE_CONSUME.md`
  - `docs/SPRINT5_ROUND3_WRITER_CONSOLIDATION.md`
  - `docs/INPUT_UI_SYNC_BOUNDARY.md`
  - `IME_SMOKE_CHECKLIST.md`

## 4. 這兩天的最終結論

### 0416 結論

- `Sprint 3 Phase 2 = NOT READY`
- `Sprint 4 = LOCKED`
- `Sprint 5 = LOCKED`
- 正確主線是 readiness 前置，不是越級衝 implementation

### 0417 結論

- `Sprint 4 = COMPLETED`
- `Sprint 5 = COMPLETED`
- `Sprint 5 = COMPLETED-STABLE`
- `floating mode stale true` 為 pre-existing runtime bug，後續獨立處理

## 5. 下一步提醒

- 後續若要正式啟動 `Sprint 6`，仍必須由 `CodeX A` 再做一次啟動 gate。
- `Sprint 6 / 7` 目前都還是大範圍未完成狀態，不能因為 Sprint 5 收尾完成就直接口頭宣告開工。
- 下個 `CodeX A` session 建議從：
  - `SPRINT_MASTER_PLAN_2026-04-15.md`
  - `daily_works_0416.md`
  - 新的 Sprint 6/7 詳細分工計畫
  開始接手。

---

身份：Claude Code A

日期：2026-04-16 ~ 2026-04-17

## 今日工作摘要

### 1. Settings Repo-Ready 收尾（0416）

- 依 `todo_list_0414_new.md` 與 `CODEXA_SUPERVISION_SUMMARY_2026-04-16.md` 的指派，完成 settings 側剩餘 repo-ready 工作。
- 完成三項可選優化評估：
  1. `BackgroundSettingsSection.kt`（1235 行）二次切分 → **不切**。理由：職責邊界清晰、切分成本 > 收益、無人正在修改。
  2. `TtsSettingsSection.kt` callback 依賴 → **合規**。Activity 端僅 1 個 callback（~20 行），低於 3 個門檻。
  3. `SettingsActivity` header 抽 section → **不抽**。455 行 < 500 行警戒線，header 屬 Activity-level 路由。
- 校正監督指標行數（442→455）：
  - 更新 [RULE/SETTINGS_BOUNDARY.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SETTINGS_BOUNDARY.md)
  - 更新 [RULE/SETTINGS_SMOKE_CHECKLIST.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SETTINGS_SMOKE_CHECKLIST.md)
- 撰寫 [REPO_READY_SETTINGS_RISK_SUMMARY_2026-04-16.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/REPO_READY_SETTINGS_RISK_SUMMARY_2026-04-16.md) — settings 側最終風險摘要。
- 更新 `todo_list_0414_new.md`，將 Claude Code A 全部項目標記完成。

### 2. 正式待命（0416 ~ 0417）

- 收到 CodeX A 通知後進入正式待命狀態。
- 原因：Sprint 3 Phase 2 啟動缺口由 Claude Code B 單線處理，為避免 settings / IME / 邊界審核三邊互撞。
- 待命期間遵守：
  - 不碰 IME
  - 不開新 settings 主題
  - 不擴大 file scope

### 3. Settings 無反彈巡檢（0417）

- 執行兩次 settings 無反彈巡檢，確認：

| 指標 | 警戒值 | 實測 | 狀態 |
|------|--------|------|------|
| SettingsActivity 行數 | > 500 | 455 | OK |
| Activity 內 SeekBar/Switch/Radio/Checkbox | > 0 | 0 | OK |
| Section 內 `auth.fetchMe()` | > 0 | 0 | OK |
| Section 內 `AIProviderManager.getInstance()` | > 0 | 0 | OK |

- 確認 settings 程式碼未被 IME 側 Sprint 4/5 進度影響。

## 正式結論

1. **Settings repo-ready 收尾已完成**，已被 CodeX A 正式接受。
2. **SettingsActivity 維持 455 行**，全部監督指標在安全範圍內，無反彈。
3. **三項可選優化均評估為不需要**，理由已記錄於風險摘要。
4. **Claude Code A 本輪正確定位為待命位**，未越界碰 IME 或 Sprint 4/5 檔案。
5. **後續若有 settings bug 或 CodeX A 新指派**，隨時可進場。

---

身份：Claude Code B

日期：2026-04-16 ~ 2026-04-17

## 今日工作摘要

### 1. Sprint 3 Phase 2 readiness 盤點（0416）

- 依 `CodeX A` 在 [CODEXA_SUPERVISION_SUMMARY_2026-04-16.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/CODEXA_SUPERVISION_SUMMARY_2026-04-16.md) 的指派，執行 Phase 2 前置盤點，不做實作。
- 完成 `composingText / candidates` 直寫路徑全盤點：
  - `keyboardView.updateComposingText(...)` 全部 call-site 分類
  - `keyboardView.updateCandidates(...)` 全部 call-site 分類
  - 熱路徑 vs 非熱路徑區分（`handleKeyPress()` / `applyInputResult()` 歸為 locked 熱路徑）
- 產出 consume 草案，未下手改動 `KeyboardView.kt` / `InputRouter.kt`。
- 等待 `CodeX A` 的 gate 判定，收到 `Sprint 3 Phase 2 = NOT READY` / `Sprint 4 = LOCKED` / `Sprint 5 = LOCKED` 結論後停手，未越級。

### 2. Sprint 4 InputUiSync 三輪實作（0417）

- 在 `CodeX A` 解鎖 Sprint 4 後，依 round-based 節奏逐輪執行，每輪完成即回交供審核，不連跳。
- **Round 1**：建立 `InputUiSync` 邊界 → 產出 [docs/SPRINT4_INPUTUISYNC_ROUND1.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT4_INPUTUISYNC_ROUND1.md)
- **Round 2**：把 `syncImeCoordinatorFromRouter()` 收到只剩 `setInputMode` → 產出 [docs/SPRINT4_INPUTUISYNC_ROUND2.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT4_INPUTUISYNC_ROUND2.md)
- **Round 3**：保留區 A/B/C/D/E 類正式化、`applyInputResult()` 本體不拆 → 產出 [docs/SPRINT4_INPUTUISYNC_ROUND3.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT4_INPUTUISYNC_ROUND3.md)
- 同步更新 [docs/INPUT_UI_SYNC_BOUNDARY.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/INPUT_UI_SYNC_BOUNDARY.md) 作為 Sprint 4 的正式邊界依據。
- 每輪獨立 `:app:compileDebugKotlin` 通過後才回交，未等下一輪 `READY` 不動手。

### 3. Sprint 5 State Convergence 三輪實作（0417）

- **Round 1**：建立 `ImeContainerStateConsumer` read-only facade（5 getter + `snapshot()` + `ImeContainerSnapshot`），service wiring 完成；本輪不做 reader / writer 遷移、不動 local field → 產出 [docs/SPRINT5_ROUND1_STATE_CONSUME.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT5_ROUND1_STATE_CONSUME.md)
- **Round 2**：reader 遷移 9/11：
  - 安全批：`R1 / R3 / R6 / R7 / R8 / R9 / R10`
  - 熱路徑評估批：`R2 / R4`
  - `R5 / R11` 因 HANDWRITING guard 保留 inline，未硬改
  - 本輪維持 zero writer change / zero local field removal
- **Round 3**：writer consolidation + local field removal → 產出 [docs/SPRINT5_ROUND3_WRITER_CONSOLIDATION.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT5_ROUND3_WRITER_CONSOLIDATION.md)
  - 收斂批次：`W1 / W6 → W2 → W3 → W4 / W5 → W7`
  - 每批後 grep 驗證對應 reader 已不依賴 local，才往下一批
  - Final 才移除 4 個 local field：`isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel`，改為標記註解
  - `R5 / R11` 做為 Round 3 前置遷移（簡單 read guard，非 RMW）
  - `:app:compileDebugKotlin` 通過（BUILD SUCCESSFUL in 5s）
- 禁區嚴守：未動 `KeyboardView.kt` / `InputRouter.kt` / AI debounce / swipe / HANDWRITING RMW / network / settings。

### 4. Sprint 5 Smoke / Runtime 驗證（0417）

- 依 [IME_SMOKE_CHECKLIST.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/IME_SMOKE_CHECKLIST.md) 對 Sprint 5 相關路徑做 code-path walk-through + grep 交叉驗證（無裝置實機測試）。
- 涵蓋情境：
  1. Panel 前後層切換（W1 / W2 / W6）
  2. 懸浮模式進出（W3）
  3. Game mode 進出與恢復（W4 / W5 + R1 ShakeDetector + R2 session-init resume）
  4. Panel toggle / close / reopen activePanel（W7 + `ImePanelRouter.closePanel` → `updatePanelSelection(null)`）
  5. Remote connection state（W8 / 原本即 coordinator-only）
- 發現 **pre-existing latent bug**：`setFloatingMode(false)` 全 codebase 無呼叫；`onDock / onClose / onMinimize` callback 只清 `prefs.floatingModeEnabled`，未 reset coordinator 狀態。
  - 以 `git log --all -S 'isFloatingMode = false'` 驗證為 initial commit 即存在，非 Sprint 5 引入。
  - 依「沒有明確 bug 就不改 .kt」「不再做新重構」兩條禁區，flag 於驗證回報，未下手修。
- 回交 A/B/C/D 驗證報告供 `CodeX A` + `CodeX B` 做最終判定。

### 5. 文件同步

- 每一 Sprint / Round 均在對應 `docs/*.md` 留下完整交接紀錄，格式遵守 A/B/C/D(/E/F)。
- 同步更新：
  - [docs/INPUT_UI_SYNC_BOUNDARY.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/INPUT_UI_SYNC_BOUNDARY.md)
  - [IME_SMOKE_CHECKLIST.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/IME_SMOKE_CHECKLIST.md)（補 §10c-S5R2 / §10c-S5R3 段落 + Known Limitations）
- 未自行改動 `RULE/*.md` / `CODEX.md` / `SPRINT_MASTER_PLAN_2026-04-15.md`（屬 `CodeX A` 權責）。

## 正式結論

1. **Sprint 4 InputUiSync 三輪實作完成**，已被 `CodeX A` 判定 `Sprint 4 = COMPLETED`。
2. **Sprint 5 State Convergence 三輪實作完成**：`isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel / remoteConnectionState` 五欄 SSOT 正式遷移至 `imeCoordinator.uiState`，已被 `CodeX A` 判定 `Sprint 5 = COMPLETED-STABLE`。
3. **Sprint 5 smoke 驗證已交付**：無 Sprint 5 convergence regression，不需要 Sprint 5 修補；`floating mode stale true` 為 pre-existing runtime bug，建議後續獨立處理。
4. **round-based 節奏嚴守**：每輪 compile 獨立通過 + `CodeX A` gate 核可後才進下一輪，零越級。
5. **禁區零違反**：`KeyboardView.kt` / `InputRouter.kt` / AI debounce / swipe / HANDWRITING RMW / network / settings 全程未動；架構重構邊界完全尊重。
6. **後續待 `CodeX A` 啟動 `Sprint 6` gate**，在此之前進入待命。

---

身份：CodeX B

日期：2026-04-16 ~ 2026-04-17

## 今日工作摘要

### 1. Sprint 3 Phase 2 邊界審核與 readiness 判定（0416）

- 依 `CodeX A` 指派，閱讀 [PHASE2_READINESS_CHECK.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/PHASE2_READINESS_CHECK.md)、[CODEXA_SUPERVISION_SUMMARY_2026-04-16.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/CODEXA_SUPERVISION_SUMMARY_2026-04-16.md)、[SPRINT3_PHASE2_TASK_BLOCK_2026-04-16.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/SPRINT3_PHASE2_TASK_BLOCK_2026-04-16.md) 與基線文件，執行 Task C 技術邊界審核。
- 完成 [PHASE2_READINESS_CHECK.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/PHASE2_READINESS_CHECK.md) 與 Sprint 3/4/5 邊界文件補建與同步：
  - [SPRINT4_INPUT_FLOW_REVIEW_2026-04-16.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/SPRINT4_INPUT_FLOW_REVIEW_2026-04-16.md)
  - [SPRINT5_MODE_CONTAINER_STATE_PLAN_2026-04-16.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/SPRINT5_MODE_CONTAINER_STATE_PLAN_2026-04-16.md)
- 對 [docs/SPRINT3_PHASE2_WRITE_PATH_AUDIT.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT3_PHASE2_WRITE_PATH_AUDIT.md) §6.1 的 4 處最小 scope 做正式審核，結論是：
  - 不接受作為 `Phase 2 READY` 放行依據
  - 原因是會形成 collect 與既有直寫的雙寫期，render ownership 仍不夠清楚
  - 建議 `CodeX A` 維持 `Sprint 3 Phase 2 = NOT READY`、`Sprint 4 = LOCKED`、`Sprint 5 = LOCKED`
- 全程未碰 `handleKeyPress()` / `applyInputResult()` / `KeyboardView.kt`，未越界做實作。

### 2. Phase 2 結構決策與 Sprint 4 邊界定義（0417）

- 在 `CodeX A` 改採單線推進後，暫停平行審核與平行規劃，保留既有邊界文件有效，不與 `Claude Code B` 競爭同一批檔案。
- 依 `CodeX A` 新指令，做 **Phase 2 Round 3 唯一結構決策**：
  - 判定 `ImeUiState.candidates` 應升級為可承載 `sourceMap` 的結構
  - 正式提出 `CandidateUiState(items, sourceMap)` 方案
  - 定義最小 file scope 限於 `ImeUiState.kt` / `ImeSessionCoordinator.kt` / `ImeServiceLifecycle.kt` 與 callback 收斂最小檔
  - 明確區分哪些舊寫入點可直接遷移、哪些仍留到 Sprint 4
- 在 `CodeX A` 拍板 `Sprint 4 = READY` 後，補做 Sprint 4 Round 1 / Round 2 的技術邊界與 file scope 定義：
  - Round 1：只放行 `InputUiSync` 的最小同步 render 邊界
  - Round 2：嚴格限制在 `ImeServiceInput.kt` + `InputUiSync.kt`
  - 逐條列出 `ImeServiceInput` 哪些路徑可遷、哪些禁止遷
- 全程只出結構決策與審核意見，不直接改 `.kt`。

### 3. Sprint 5 Round 1 / Round 2 / Round 3 技術審核（0417）

- 依 `CodeX A` 拍板結果，定義 Sprint 5 Round 1 的最小 consume 邊界：
  - 只允許純 mode / container / external state consume
  - 不允許 container/platform/integration 副作用抽離
  - 不允許碰 `KeyboardView.kt` 與高依賴 panel
- 針對 Sprint 5 Round 2 reader migration 做技術審核：
  - 跟審 [docs/SPRINT5_ROUND1_STATE_CONSUME.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT5_ROUND1_STATE_CONSUME.md)
  - 正式分類 `R2 / R4 / R5 / R11`
  - 判定 `R2 / R4` 可遷移、`R5 / R11` 暫留 inline
  - 提前寫出 Round 3 gate 草案，明定 reader 先完成正式分類後，writer 才可整併
- 針對 Sprint 5 Round 3 writer consolidation 持續待命跟審：
  - 特別盯 `W7 updatePanelSelection`
  - 明定 local field 移除順序維持 `W8 → W1/W6 → W2/W3/W4/W5 → W7`
  - 不進實作，只出技術審核意見

### 4. Smoke / Runtime 驗證跟審（0417）

- 收到 `Claude Code B` 的 Sprint 5 runtime / smoke 驗證報告後，執行技術驗證審核，不新增結構變更。
- 依 code-path walk-through + grep 驗證結果，將發現分類為：
  - `Sprint 5 state convergence 通過`：panel 前後層、game mode、panel close、remote state
  - `舊有 runtime 問題`：floating mode never resets
- 正式判定 `floating mode stale true` 為 pre-existing bug，而非 Sprint 5 收斂回歸：
  - `setFloatingMode(false)` 全 codebase 無呼叫
  - 問題在 Sprint 5 前就存在，只是 state 收斂後更容易被觀測
- 建議後續獨立 issue 處理，不混入 Sprint 5 收尾。

### 5. 文件同步與基線維護

- 同步更新與維護的主要文件包括：
  - [CODEX.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/CODEX.md)
  - [RULE/AI_PROTOCOL.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/AI_PROTOCOL.md)
  - [SPRINT_MASTER_PLAN_2026-04-15.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/SPRINT_MASTER_PLAN_2026-04-15.md)
  - [todo_list_0414_new.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/todo_list_0414_new.md)
  - [RULE/SPRINT_GATE_RULE.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SPRINT_GATE_RULE.md)（基線閱讀並遵守）
- 另外，依 `CodeX A` 要求，將「所有 Sprint / Phase / readiness step 都必須先經 `CodeX A` 核可」正式寫回基線文件，作為後續共同規則。
- 沒有順手改動 settings、network、`KeyboardView.kt` 或高依賴 panel 主題。

## 正式結論

1. **2026-04-16 的正式結論**：`Sprint 3 Phase 2 = NOT READY`、`Sprint 4 = LOCKED`、`Sprint 5 = LOCKED`；這個結論是由我完成 readiness / 邊界審核後，交 `CodeX A` 拍板。
2. **2026-04-17 的主要工作**：轉為以 `CodeX B` 身份提供單一結構決策、Sprint 4 邊界定義、Sprint 5 reader/writer 技術審核與 smoke 跟審，不直接實作。
3. **Phase 2 結構決策已完成**：正式提出 `CandidateUiState(items, sourceMap)` 作為唯一合法方向，解決 `sourceMap` 承載問題。
4. **Sprint 4 / Sprint 5 的 round-based 邊界已完成定義**：尤其明確限制 file scope、遷移優先序與禁區，避免 `Claude Code B` 一次切太多。
5. **Sprint 5 smoke 跟審結論**：未發現 Sprint 5 convergence regression；`floating mode stale true` 屬於 pre-existing runtime bug，建議後續獨立修補。
6. **全程遵守 gate 規則**：所有下一步皆以 `CodeX A` 的 `READY / NOT READY / LOCKED` 為準，未自行放行後續 Sprint / Phase。

# InputUiSync 邊界（Sprint 4 Round 3 — `syncImeCoordinatorFromRouter` 最小併入 + Sprint 4 保留區定義）

日期: 2026-04-17
狀態: Sprint 4 Round 3 已落地（`syncImeCoordinatorFromRouter` 簡化為 `setInputMode` only；`onStartInputView` session-init 明確走 InputUiSync；`applyInputResult` 本體盤點確認無直接 UI render）；累計 Sprint 4 共 20 處寫入 / 12 call site 納入 InputUiSync；Sprint 4 保留區（A 狀態欄位 / B RMW / C Niche / D 已驗證無直寫 / E Sprint 5 候選）見 `docs/SPRINT4_INPUTUISYNC_ROUND3.md §5`
相關: `docs/SPRINT3_PHASE2_WRITE_PATH_AUDIT.md`、`docs/SPRINT4_INPUTUISYNC_ROUND1.md`、`docs/SPRINT4_INPUTUISYNC_ROUND2.md`、`docs/SPRINT4_INPUTUISYNC_ROUND3.md`、`PHASE2_READINESS_CHECK.md §5.5`、`SPRINT4_INPUT_FLOW_REVIEW §3.3 / §5`

本文件用途：用 4 點說清楚 `composingText / candidates` 的同步 render 邊界，作為 Phase 2 READY 的正式技術依據。

---

## 1. `composingText` 同步責任

- **同步 render owner**: `YukiBoardIMEService`（含 `service/` package 內的 extension helpers）
- **寫入路徑共 20 處**：11 熱路徑（`handleKeyPress` / `applyInputResult` / `handleDelete/Enter/Space` / `routeKeyToPanel` CJK）維持 inline 同步；4 panel/gesture 特例維持 inline 同步；5 callback/cleanup 全部（Round 2 後）走 `clearComposingAndCandidatesUi()` helper（dual-write view + coordinator）
- **禁止**：不得在 `KeyboardView` 內 collect；不得把熱路徑寫入延遲到 collect；不得新增分散 `keyboardView.updateComposingText(...)` 呼叫點

## 2. `candidates` 同步責任

- **同步 render owner**: `YukiBoardIMEService`（同上）
- **寫入路徑共 26 處**：13 熱路徑維持 inline；6 panel/gesture 特例維持 inline；7 callback/cleanup 全部（Round 2 後）走 `clearComposingAndCandidatesUi()` 或 `clearCandidatesUi()` helper（dual-write view + coordinator）
- **結構升級（Round 3 已完成）**: `ImeUiState.candidateState: CandidateUiState(items, sourceMap)`；coordinator 新增 `updateCandidates(items, sourceMap)` 與 `updateCandidateState(state)` API，可完整承載 sourceMap
- **禁止**：同 composingText；熱路徑（AI debounce / HANDWRITING onRecognized / swipe RMW / `setOnCandidateSelectListener` post-commit 回寫）的 sourceMap collect 驅動屬 Sprint 4 範圍，本輪不動

## 3. 保留 inline 同步的寫入點（本階段不動）

以下寫入仍 inline 直寫 `keyboardView.updateComposingText / updateCandidates`，**不走 InputUiSync，不走 coordinator**：

- `YukiBoardIMEService.handleKeyPress()` 內 2 處 niche（TOGGLE_KANA 特例 L789；intermediate char-commit clear L825-826）
- `applyInputResult()` 本體（副作用分類尚未抽出，屬 Round 3+）
- `ImeServiceInput.handleTab` 候選 focus 刷新（L536）
- `ImeServiceInput.handleSwipeInput` swipe 段累積 RMW（L468-469）
- `ImeServiceInput.handleSwitchLang` 清候選（L650 / L658）
- `ImeServiceInput.requestAICandidates` AI debounce RMW（L402，屬 Sprint 4 Round 3+）
- `ImeServicePanels.kt` HANDWRITING `onRecognized` ML RMW

理由：這些路徑或是 RMW 語意尚未判定可安全 dual-write（AI / swipe / handwriting），或在同一鍵 / 同一 callback 內完成「commit + UI 更新」、或屬於 niche 分支（TOGGLE_KANA）。`CodeX A` 未拍板 Round 3 scope 前維持 inline。

## 4. 已走 InputUiSync / helper / collect consume 的寫入點

InputUiSync 入口（Sprint 4 Round 1 起）：`app.ime.InputUiSync` class 提供 `renderComposing` / `renderCandidates` / `clearComposing` / `clearCandidates` / `clearAll`，語意為**同步 dual-write**（view + coordinator 同幀寫入）。

Cleanup helper（Phase 2 Round 2 建立、Sprint 4 Round 1 refactor）：`YukiBoardIMEService.clearComposingAndCandidatesUi()` / `clearCandidatesUi()`（`service/ImeServicePanels.kt`）已內部改走 `inputUiSync.clearAll()` / `clearCandidates()`；collect 層（`startImeComposingCandidatesCollect`）仍作 `.isEmpty()` safety net 消費。

### 4.1 Session / Callback cleanup（Phase 2 Round 1 + Round 2，共 8 處）

Session 邊界（Phase 2 Round 1，3 處）：
1. `ImeServicePanels.resetInputUiStateForPanelTransition`（panel 開/關前 cleanup）
2. `YukiBoardIMEService.gyroController.setOnCursorMoveListener` F-8 分支（陀螺儀游標移動前的 composing forceCommit 後 cleanup）
3. `YukiBoardIMEService.onStartInputView` 的 `imeSessionHooks` lambda（新 session 殘留 cleanup）

Callback cleanup（Phase 2 Round 2，5 處）：
4. `bindKeyboardViewCallbacks.setOnRawCommitListener` → `flushComposingBeforeRawCommit.clearInputUiState` lambda
5. `bindKeyboardViewCallbacks.setOnCandidateSelectListener` swipe 分支 → `clearSwipeState.clearInputUiState` lambda
6. `bindKeyboardViewCallbacks.setOnCandidateSelectListener` AI 候選分支（`isAICandidate` 成立後 commit）
7. `bindKeyboardViewCallbacks.setOnCandidateSelectListener` HANDWRITING 分支（commit 後，`clearCandidatesUi()` — candidates-only）
8. `bindKeyboardViewCallbacks.setOnLanguageSelectedListener` Language 分支（切換完成後）

### 4.2 熱路徑主層（Sprint 4 Round 1，4 處）

9. `YukiBoardIMEService.handleKeyPress()` post-update composing（L844）→ `inputUiSync.renderComposing(inputRouter.composingText)`
10. `YukiBoardIMEService.handleKeyPress()` post-update candidates（L871）→ `inputUiSync.renderCandidates(candidates, inputRouter.candidateSourceMap)`
11. `ImeServiceCallbacks.setOnCandidateSelectListener` post-commit composing（L222）→ `inputUiSync.renderComposing(...)`
12. `ImeServiceCallbacks.setOnCandidateSelectListener` post-commit candidates（L223）→ `inputUiSync.renderCandidates(..., sourceMap)`

### 4.3 子 handler 薄層（Sprint 4 Round 2，8 處寫入 / 涵蓋 7 分支）

全部位於 `ImeServiceInput.kt`：
13. `handleDelete(ic)` 結尾 render pair → `inputUiSync.renderComposing / renderCandidates`
14. `handleEnter(ic)` applyInputResult 後 render pair → 同上
15. `handleSpace(ic)` Tab focus-confirm 分支（空 composing + candidates）→ `inputUiSync.renderComposing("") + renderCandidates(...)`
16. `handleSpace(ic)` 一般 space 分支 render pair → `inputUiSync.renderComposing / renderCandidates`
17. `routeKeyToPanel` `KEYCODE_DELETE` CJK 分支 render pair → 同上
18. `routeKeyToPanel` `KEYCODE_ENTER` CJK 分支（composing + candidates 完整清空）→ `inputUiSync.clearAll()`
19. `routeKeyToPanel` `KEYCODE_SPACE` CJK 分支 render pair → `inputUiSync.renderComposing / renderCandidates`
20. `routeKeyToPanel` else（一般 label）CJK 分支 render pair → 同上

### 4.4 Session-init + applyInputResult 周邊（Sprint 4 Round 3）

21. `YukiBoardIMEService.onStartInputView` L479-後 session-init 顯式 dual-write → `inputUiSync.renderComposing(inputRouter.composingText) + renderCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)`
22. `syncImeCoordinatorFromRouter()` 簡化為 `setInputMode` only（composing / candidates 的 coordinator-only 寫入移除，改由 caller post-call 走 InputUiSync dual-write；applyInputResult 結尾的 sourceMap 暫失問題消除）

### Collect consume 設計（guarded empty-only）

```kotlin
internal fun YukiBoardIMEService.startImeComposingCandidatesCollect() {
    composingCandidatesCollectJob?.cancel()
    composingCandidatesCollectJob = serviceScope.launch {
        imeCoordinator.uiState.collect { state ->
            if (state.composingText.isEmpty()) keyboardView.updateComposingText("")
            if (state.candidateState.items.isEmpty()) keyboardView.updateCandidates(emptyList())
        }
    }
}
```

Guard 存在原因：

- 熱路徑（`applyInputResult` → `syncImeCoordinatorFromRouter`）會把非空 composing/candidates 寫入 coordinator；若不 guard，collect 會用 coordinator 的非空值覆寫熱路徑剛寫入的 view（idempotent 但多一次寫）
- Round 1 時 callback（`ImeServiceCallbacks.kt`）會直接寫 view 空值但**不更新 coordinator**；若 collect 在之後因其他原因被喚醒（例如 panel 狀態變化），會把 coordinator 的**過期非空**值覆寫回 view → stale state regression
- Round 2（2026-04-17）把 5 處 callback cleanup 全改走 helper，coordinator 與 view 雙寫一致；此 stale race 已關閉。`.isEmpty()` guard 自此由「防 stale」降級為「safety net」（防止未來新增 callback 忘記同步時再度 regress）
- Round 3（2026-04-17）把 `ImeUiState.candidates: List<String>` 升級為 `candidateState: CandidateUiState(items, sourceMap)`；collect guard 讀 `candidateState.items.isEmpty()`；sourceMap 承載層解鎖
- Guard `.isEmpty()` 確保 collect 只能「清空」view，永遠不會用過期值覆寫正在輸入的內容
- 非空 consume 解鎖條件（guard 移除）：
  - ✓ `handleKeyPress()` post-update render ownership 抽到 `InputUiSync`（Sprint 4 Round 1 完成）
  - ✓ `setOnCandidateSelectListener` post-commit 回寫走 `renderCandidates(items, sourceMap)`（Sprint 4 Round 1 完成）
  - ✓ `handleDelete / handleEnter / handleSpace / routeKeyToPanel` 子 handler render ownership 抽到 `InputUiSync`（Sprint 4 Round 2 完成）
  - ✓ `syncImeCoordinatorFromRouter()` composing/candidates 職責移出；session-init 明確走 InputUiSync（Sprint 4 Round 3 完成）
  - ✓ `applyInputResult()` 本體盤點：無直接 UI render，責任已於 Round 1+2 委派給 caller InputUiSync（Sprint 4 Round 3 驗證）
  - 正式保留（Sprint 4 範圍外）：AI debounce / HANDWRITING onRecognized / swipe RMW → B 類
  - 正式保留（Sprint 4 範圍外）：`handleKeyPress` TOGGLE_KANA / intermediate clear、`handleTab` focus、`handleSwitchLang` 清候選 → C 類
  - 正式保留（Sprint 5+）：`applyInputResult` 11 類副作用抽出、`KeyEventInterpreter`、Sprint 5 新狀態欄位 → E 類

---

## 附註：完整 consume 解鎖條件

- **callback 端統一寫 coordinator**（Phase 2 Round 2 已完成 ✓）：`ImeServiceCallbacks.kt` 5 處 cleanup 已全改走 helper，view + coordinator 雙寫一致
- **`ImeUiState.candidates` 結構升級**（Phase 2 Round 3 已完成 ✓）：已升級為 `candidateState: CandidateUiState(items, sourceMap)`，coordinator API 擴充完成
- **Sprint 4 `InputUiSync` 正式邊界**（Round 1 完成 ✓）：`handleKeyPress()` post-update + `setOnCandidateSelectListener` post-commit + cleanup helper 已遷移
- **Sprint 4 子 handler 遷移**（Round 2 完成 ✓）：`handleDelete / handleEnter / handleSpace / routeKeyToPanel` 8 處寫入全改走 InputUiSync
- **Sprint 4 Round 3 收斂與保留區定義完成 ✓**：
  - `syncImeCoordinatorFromRouter()` 簡化為 `setInputMode` only；composing/candidates 移出至 caller InputUiSync
  - `onStartInputView` session-init 顯式 dual-write 至 InputUiSync
  - `applyInputResult()` 本體確認無直接 UI render（0 lines changed）
  - Sprint 4 保留區五類（A / B / C / D / E）正式化，見 `docs/SPRINT4_INPUTUISYNC_ROUND3.md §5`
- **Sprint 5 Round 1 `ImeContainerStateConsumer` 邊界完成 ✓**（2026-04-17）：
  - 新檔 `app/ime/ImeContainerStateConsumer.kt`，承載 container / external 5 欄位（`isFloating / isPanelBehind / isInGame / remoteConnection / activePanel`）
  - 職責與 `InputUiSync` 平行互不重疊：前者負責 **render sync (write+render)**，後者負責 **container state consume (read-only snapshot)**
  - 細節請見 `docs/SPRINT5_ROUND1_STATE_CONSUME.md`
- **Sprint 5 Round 2 reader migration 完成 ✓**（2026-04-17）：9/11 reader 遷至 `imeContainerState`，R5/R11 HANDWRITING guard Round 2 保留 inline
- **Sprint 5 Round 3 writer consolidation + local field removal 完成 ✓**（2026-04-17）：
  - R5/R11 pre-step 遷移；W1/W6/W2/W3/W4/W5/W7 依序收斂；`isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel` 四個 local field 移除
  - `YukiBoardIMEService` 已無 container-state mutable mirror；SSOT = `imeCoordinator.uiState`
  - 細節請見 `docs/SPRINT5_ROUND3_WRITER_CONSOLIDATION.md`
- **Sprint 5+ 候選**：`applyInputResult` 11 類副作用抽出、`KeyEventInterpreter`

累計達成 `PHASE2_READINESS_CHECK.md §5.1 / §5.2 / §5.3 / §5.5 / §5.6 / §5.7` + `SPRINT4_INPUT_FLOW_REVIEW §5.3 / §5.5`；§5.1 / §5.2 本 Sprint 以「完整分類 + 保留區正式化」結尾，實作抽出屬 Sprint 5+。

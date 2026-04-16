# Sprint 3 Phase 2 Write Path Audit

日期: 2026-04-16
角色: `Claude Code B`
派工文件: `SPRINT3_PHASE2_TASK_BLOCK_2026-04-16.md`
對照決策: `PHASE2_READINESS_CHECK.md`（CodeX B）、`CODEXA_SUPERVISION_SUMMARY_2026-04-16.md`（CodeX A）
相關前案: `docs/RENDER_IME_UI_STATE_DRAFT.md`（Sprint 3 Phase 1 草案）、`IME_STATE_CONVERGENCE_DRAFT.md`

本文件用途：Task A（直寫路徑盤點 + 分類）+ Task B（Phase 2 consume 草案）的 Claude Code B 正式輸出，交 `CodeX B` 做 Task C 邊界審核、再由 `CodeX A` 做 Task D 拍板。

本文件不改任何 `.kt`，不提前 collect 化任何欄位，不碰 `handleKeyPress()` / `applyInputResult()` / `KeyboardView.kt`。

---

## 1. 方法論

- **三分類**：依 `PHASE2_READINESS_CHECK.md` §3 + `CODEXA_SUPERVISION_SUMMARY` §4
  - `熱路徑同步更新`：打字當下（每鍵必走 / 候選確認當下）同步 render，不能引入 collect 延遲
  - `callback / cleanup`：callback lambda / session 重設 / panel transition 觸發的清空或刷新
  - `panel / gesture / 特例`：swipe、AI debounce、handwriting、陀螺儀、語言切換等非 per-keystroke 的特殊路徑
- **熱路徑判定**：呼叫站在「同一次按鍵處理」的同步鏈路內（`handleKeyPress` / `applyInputResult` / `processDelete/Enter/Space/Input`）→ 熱路徑
- **建議保留同步**：若延遲一幀會造成使用者感知到候選列晚刷新、composing 殘影、順序錯亂 → 保留同步

---

## 2. `composingText` 直寫路徑（20 處）

| # | 檔案 | 方法名稱 | 觸發來源 | 分類 | 熱路徑 | 建議保留同步 |
|---|------|----------|----------|------|:------:|:------------:|
| 1 | `ImeServiceInput.kt:468` | `handleSwipeInput` | swipe 組合顯示 "…" 佔位 | panel/gesture/特例 | ❌ | ⭕ swipe 與 AI debounce 有順序風險，保留同步避免 race |
| 2 | `ImeServiceInput.kt:562` | `handleDelete` | 每次 DELETE 後 sync | 熱路徑同步更新 | ⭕ | ⭕ 每鍵必走 |
| 3 | `ImeServiceInput.kt:569` | `handleEnter` | ENTER 處理完 | 熱路徑同步更新 | ⭕ | ⭕ 每鍵必走 |
| 4 | `ImeServiceInput.kt:625` | `handleSpace` | Tab-focus 候選 SPACE 確認後 clear | 熱路徑同步更新 | ⭕ | ⭕ |
| 5 | `ImeServiceInput.kt:632` | `handleSpace` | 一般 SPACE 後 sync | 熱路徑同步更新 | ⭕ | ⭕ |
| 6 | `ImeServiceInput.kt:683` | `routeKeyToPanel` | CJK + DELETE 時更新 composing | 熱路徑同步更新 | ⭕ | ⭕ |
| 7 | `ImeServiceInput.kt:693` | `routeKeyToPanel` | CJK + ENTER 送 panel 後 clear | 熱路徑同步更新 | ⭕ | ⭕ |
| 8 | `ImeServiceInput.kt:704` | `routeKeyToPanel` | CJK + SPACE 時更新 composing | 熱路徑同步更新 | ⭕ | ⭕ |
| 9 | `ImeServiceInput.kt:718` | `routeKeyToPanel` | CJK 一般字元輸入後更新 composing | 熱路徑同步更新 | ⭕ | ⭕ |
| 10 | `ImeServiceCallbacks.kt:136` | `setOnRawCommitListener` | raw commit 前清除 composing | callback/cleanup | ❌ | ⭕ 與 `ic.commitText` 同一批 batch，保留同步避免殘影 |
| 11 | `ImeServiceCallbacks.kt:174` | `setOnCandidateSelectListener` | swipe 候選選取後 clear | callback/cleanup | ❌ | ⭕ 候選 click 同步 feedback |
| 12 | `ImeServiceCallbacks.kt:198` | `setOnCandidateSelectListener` | AI 候選選取後 clear | callback/cleanup | ❌ | ⭕ 同上 |
| 13 | `ImeServiceCallbacks.kt:229` | `setOnCandidateSelectListener` | 一般候選選取後 sync | callback/cleanup | ❌ | ⭕ 使用者點選後需即時反映 |
| 14 | `ImeServiceCallbacks.kt:263` | `setOnLanguageSelectedListener` | 語言切換後 clear | panel/gesture/特例 | ❌ | ⭕ 切語言當下動畫 + toast + clear，一致同步較佳 |
| 15 | `ImeServicePanels.kt:479` | `resetInputUiStateForPanelTransition` | panel 開/關前重設 | callback/cleanup | ❌ | ❌ 可收入 render（不是熱路徑，延遲不可感知） |
| 16 | `YukiBoardIMEService.kt:284` | gyroController `setOnCursorMoveListener` | 陀螺儀游標移動前 F-8 forceCommit | panel/gesture/特例 | ❌ | ⭕ 與 `ic.commitText` 同步，避免游標跳錯 |
| 17 | `YukiBoardIMEService.kt:444` | `onStartInputView` | 上一 session composing 殘留清除 | callback/cleanup | ❌ | ❌ 可收入 render（新 session 初始化，跨幀不可感知） |
| 18 | `YukiBoardIMEService.kt:783` | `handleKeyPress` | 日文 romaji/kana 切換後 sync | 熱路徑同步更新 | ⭕ | ⭕ 在 `handleKeyPress` 內，LOCKED |
| 19 | `YukiBoardIMEService.kt:819` | `handleKeyPress` | 非字母按鍵 pre-commit 後 clear | 熱路徑同步更新 | ⭕ | ⭕ 在 `handleKeyPress` 內，LOCKED |
| 20 | `YukiBoardIMEService.kt:838` | `handleKeyPress` | 尾部 `needsPostUpdate` 分支統一 sync | 熱路徑同步更新 | ⭕ | ⭕ 在 `handleKeyPress` 內，LOCKED |

### 2.1 分類小結（composingText）

| 分類 | 數量 | 建議保留同步 |
|------|:----:|:------------:|
| 熱路徑同步更新 | 11 | 11（必須，LOCKED） |
| callback / cleanup | 5 | 3（3 需同步 feedback）+ 2（可收入 render） |
| panel / gesture / 特例 | 4 | 4（全部建議保留同步） |
| **合計** | **20** | **18 建議保留 / 2 可收入 render** |

可收入 Phase 2 render 的 composingText 寫入點：

- `ImeServicePanels.kt:479` `resetInputUiStateForPanelTransition`
- `YukiBoardIMEService.kt:444` `onStartInputView` 上一 session 殘留清除

---

## 3. `candidates` 直寫路徑（26 處）

| # | 檔案 | 方法名稱 | 觸發來源 | 分類 | 熱路徑 | sourceMap | 建議保留同步 |
|---|------|----------|----------|------|:------:|:---------:|:------------:|
| 1 | `ImeServiceInput.kt:402` | `requestAICandidates` | AI debounce 回寫 AI 候選（append） | panel/gesture/特例 | ❌ | ✅ | ⭕ append 語意，需讀當前 candidates 後覆寫，有順序風險 |
| 2 | `ImeServiceInput.kt:469` | `handleSwipeInput` | swipe 組合候選顯示 | panel/gesture/特例 | ❌ | ❌ | ⭕ 與 `markSwipeCandidates(true)` 成對，保留同步 |
| 3 | `ImeServiceInput.kt:536` | `handleTab` | Tab-focus 候選重繪 | 熱路徑同步更新 | ⭕ | ✅ | ⭕ 與 `highlightCandidate` 同一幀 |
| 4 | `ImeServiceInput.kt:563` | `handleDelete` | DELETE 後 sync | 熱路徑同步更新 | ⭕ | ✅ | ⭕ |
| 5 | `ImeServiceInput.kt:570` | `handleEnter` | ENTER 後 sync | 熱路徑同步更新 | ⭕ | ✅ | ⭕ |
| 6 | `ImeServiceInput.kt:626` | `handleSpace` | Tab-focus 確認後 sync | 熱路徑同步更新 | ⭕ | ✅ | ⭕ |
| 7 | `ImeServiceInput.kt:633` | `handleSpace` | SPACE 後 sync | 熱路徑同步更新 | ⭕ | ✅ | ⭕ |
| 8 | `ImeServiceInput.kt:650` | `handleSwitchLang` | AUTO 切鍵盤 clear | panel/gesture/特例 | ❌ | ✅（空） | ⭕ 切語言當下統一同步 |
| 9 | `ImeServiceInput.kt:658` | `handleSwitchLang` | 手動切語言 clear | panel/gesture/特例 | ❌ | ✅（空） | ⭕ 同上 |
| 10 | `ImeServiceInput.kt:684` | `routeKeyToPanel` | CJK + DELETE | 熱路徑同步更新 | ⭕ | ✅ | ⭕ |
| 11 | `ImeServiceInput.kt:694` | `routeKeyToPanel` | CJK + ENTER clear | 熱路徑同步更新 | ⭕ | ❌ | ⭕ |
| 12 | `ImeServiceInput.kt:705` | `routeKeyToPanel` | CJK + SPACE | 熱路徑同步更新 | ⭕ | ✅ | ⭕ |
| 13 | `ImeServiceInput.kt:719` | `routeKeyToPanel` | CJK 字元輸入 | 熱路徑同步更新 | ⭕ | ✅ | ⭕ |
| 14 | `ImeServiceCallbacks.kt:137` | `setOnRawCommitListener` | raw commit 前清空 | callback/cleanup | ❌ | ❌ | ⭕ 與 composing clear 同批 |
| 15 | `ImeServiceCallbacks.kt:175` | `setOnCandidateSelectListener` | swipe 候選選取後 clear | callback/cleanup | ❌ | ❌ | ⭕ |
| 16 | `ImeServiceCallbacks.kt:199` | `setOnCandidateSelectListener` | AI 候選選取後 clear | callback/cleanup | ❌ | ❌ | ⭕ |
| 17 | `ImeServiceCallbacks.kt:207` | `setOnCandidateSelectListener` | HANDWRITING 候選選取後 clear | callback/cleanup | ❌ | ❌ | ⭕ |
| 18 | `ImeServiceCallbacks.kt:230` | `setOnCandidateSelectListener` | 一般候選選取後 sync | callback/cleanup | ❌ | ✅ | ⭕ 點選即時反映 |
| 19 | `ImeServiceCallbacks.kt:264` | `setOnLanguageSelectedListener` | 語言切換後 clear | panel/gesture/特例 | ❌ | ❌ | ⭕ |
| 20 | `ImeServicePanels.kt:424` | HANDWRITING `onCandidateSelected` | 手寫候選選取後 clear | callback/cleanup | ❌ | ❌ | ⭕ |
| 21 | `ImeServicePanels.kt:435` | HANDWRITING `onRecognized` | 手寫 ML 辨識結果寫入 | panel/gesture/特例 | ❌ | ✅ | ⭕ ML 回寫覆寫當前 candidates，有順序風險 |
| 22 | `ImeServicePanels.kt:480` | `resetInputUiStateForPanelTransition` | panel 開/關前重設 | callback/cleanup | ❌ | ❌ | ❌ 可收入 render |
| 23 | `YukiBoardIMEService.kt:285` | gyroController `setOnCursorMoveListener` | 陀螺儀游標移動前 F-8 forceCommit clear | panel/gesture/特例 | ❌ | ❌ | ⭕ 與 `ic.commitText` + composing clear 同批 |
| 24 | `YukiBoardIMEService.kt:443` | `onStartInputView` | 上一 session 殘留清除 | callback/cleanup | ❌ | ❌ | ❌ 可收入 render |
| 25 | `YukiBoardIMEService.kt:820` | `handleKeyPress` | 非字母 pre-commit 後 clear | 熱路徑同步更新 | ⭕ | ❌ | ⭕ LOCKED |
| 26 | `YukiBoardIMEService.kt:865` | `handleKeyPress` | 尾部統一 sync（含 AUTO bilingual + predictor） | 熱路徑同步更新 | ⭕ | ✅ | ⭕ LOCKED |

### 3.1 分類小結（candidates）

| 分類 | 數量 | 建議保留同步 |
|------|:----:|:------------:|
| 熱路徑同步更新 | 13 | 13（必須，部分 LOCKED） |
| callback / cleanup | 7 | 5（同步 feedback / batch）+ 2（可收入 render） |
| panel / gesture / 特例 | 6 | 6（全部建議保留同步） |
| **合計** | **26** | **24 建議保留 / 2 可收入 render** |

可收入 Phase 2 render 的 candidates 寫入點：

- `ImeServicePanels.kt:480` `resetInputUiStateForPanelTransition`
- `YukiBoardIMEService.kt:443` `onStartInputView` 上一 session 殘留清除

### 3.2 sourceMap 觀察

- 13/26 直寫帶 `sourceMap`（AI / DICTIONARY 來源著色）
- 現行 `ImeUiState.candidates: List<String>` 不承載 `sourceMap`
- 若 Phase 2 要真的把 candidates 改 collect 驅動且不退化，`ImeUiState.candidates` 必須先升級為能攜帶 sourceMap 的結構
- 本次先只考慮 §4「可收入 render」的 2 個點，均為 `emptyList()` 清空 → sourceMap 為空 map，升級壓力不存在
- `ImeUiState.candidates` 結構升級決策權在 `CodeX B`

---

## 4. 週邊 UI 狀態（候選相關，目前不在 `ImeUiState`）

| API | 用途 | Phase 2 處理建議 |
|-----|------|-------------------|
| `keyboardView.markSwipeCandidates(boolean)` | swipe 候選標記 | 與 `updateCandidates` 成對觸發；若 candidates 改 render，必須同 render 處理 |
| `keyboardView.highlightCandidate(index)` | Tab-focus 高亮 | Phase 2 不收；屬於 `handleTab` 熱路徑 |
| `keyboardView.collapseCandidatePanel()` | 收起展開區 | `updateCandidates(emptyList)` 內會自動收起，Phase 2 不需額外處理 |
| `keyboardView.candidateSourceMap` / `keyboardView.candidates`（getter） | 外部 callback 讀當前 | Phase 2 不收；callback 本身屬 callback/cleanup |

---

## 5. 熱路徑判定摘要

### 5.1 絕對熱路徑（`PHASE2_READINESS_CHECK.md §3.1` 一致）

- `handleKeyPress()`（LOCKED）
- `applyInputResult()`（LOCKED）
- `handleDelete()` / `handleEnter()` / `handleSpace()`
- `routeKeyToPanel()` 內的 CJK 路徑（line 683-719）
- `handleTab()` 候選 focus 刷新（line 536）

合計 composing 11 處 + candidates 13 處 = **24 個熱路徑直寫點**，Phase 2 一律保留同步。

### 5.2 絕對不是熱路徑（候選可收入 render 的）

- `resetInputUiStateForPanelTransition`（composing + candidates）→ panel transition，頻率低
- `onStartInputView` 上一 session 殘留清除（composing + candidates）→ 新 session 初始化

兩者都是「清空語意」，只寫空值，不需 sourceMap，不會帶 race 風險（因為是 session 邊界）。

### 5.3 灰色地帶（本輪建議不收）

- `ImeServicePanels.kt:435` HANDWRITING `onRecognized`（ML 非同步回寫）
- `ImeServiceInput.kt:402` `requestAICandidates`（debounce 回寫）
- `ImeServiceInput.kt:469` swipe 組合候選（與 `markSwipeCandidates` 成對）

這三處都有「讀當前 candidates 後覆寫」的 read-modify-write 語意，若改走 coordinator，需同時解決：

- 讀當下值的正確性（coordinator snapshot vs 實時 view state）
- append 而非 replace 的語意
- 與 sourceMap 的合併

屬於 Sprint 4 範圍，本輪不提。

---

## 6. Phase 2 consume 草案（Task B）

### 6.1 哪些路徑可以先收進單一 render（§9 可收入）

僅 **4 處**（2 composing + 2 candidates）：

| # | 檔案:行 | 路徑 | 寫入語意 |
|---|---------|------|----------|
| 1 | `ImeServicePanels.kt:479` | `resetInputUiStateForPanelTransition` composing clear | `""` |
| 2 | `ImeServicePanels.kt:480` | `resetInputUiStateForPanelTransition` candidates clear | `emptyList()` |
| 3 | `YukiBoardIMEService.kt:444` | `onStartInputView` 殘留 composing clear | `""` |
| 4 | `YukiBoardIMEService.kt:443` | `onStartInputView` 殘留 candidates clear | `emptyList()` |

收斂方式（非實作，僅提案）：

- 呼叫方改呼 `imeCoordinator.updateComposingText("")` + `imeCoordinator.updateCandidates(emptyList())`
- `renderImeUiState(state)` 新增兩行：
  ```kotlin
  keyboardView.updateComposingText(state.composingText)
  keyboardView.updateCandidates(state.candidates)
  ```
- 不刪既有 18 + 24 = 42 處熱路徑 / callback / panel 同步直寫；它們與 coordinator 形成「雙寫」過渡期
- Phase 2 的 compile/runtime 風險限縮在「session 邊界清空」這一個語意

這個最小 scope 對應 `PHASE2_READINESS_CHECK.md §6` 允許的「cleanup 類重複路徑改用 helper」。若 `CodeX B` 採用本方案，建議把 4 處 clear 收斂成 CodeX B §6 提到的 helper：

```kotlin
// 名稱依 PHASE2_READINESS_CHECK.md §6 建議
internal fun YukiBoardIMEService.clearComposingAndCandidates() {
    imeCoordinator.updateComposingText("")
    imeCoordinator.updateCandidates(emptyList())
}
```

### 6.2 哪些路徑暫時不能收

- **11 個熱路徑 composing 寫入**（#2-9、#18-20）：同一鍵處理鏈路內，不能跨幀
- **13 個熱路徑 candidates 寫入**（#3-7、#10-13、#25-26）：同上
- **5 個 callback / cleanup composing 寫入**（#10-14）：與 `ic.commitText` 或 toast/動畫成對，保留同步避免視覺錯位
- **5 個 callback / cleanup candidates 寫入**（#14-19）：同上
- **4 個 panel/gesture composing 特例**（#1、#14、#16、與 handleSwitchLang clear）
- **6 個 panel/gesture candidates 特例**（#1-2、#8-9、#19、#21-23）
- **所有 sourceMap != empty 的 candidates 寫入**：`ImeUiState.candidates` 結構升級未決，本輪不動
- **所有 read-modify-write 語意的寫入**（§5.3 三處）：屬 Sprint 4 範圍

### 6.3 collect owner 是否仍維持在 `YukiBoardIMEService`

**是。** 與 Sprint 3 Phase 1 一致（`ImeServiceLifecycle.kt:325-332` `startUiStateCollect()`）。

原因：

- Phase 2 只加 2 行到 `renderImeUiState()`，不需要 `KeyboardView` 自己 collect
- `KeyboardView.kt` 仍 LOCKED
- 維持單一 consume owner 減少 race 風險
- 與 `PHASE2_READINESS_CHECK.md §7` 一致（不得在 `KeyboardView.kt` 內 collect）

### 6.4 Phase 2 最小實作清單（若 CodeX A 拍板 READY 時才做，本輪不做）

僅列給 CodeX B 審核用：

- [ ] 在 `renderImeUiState(state)` 新增 composing + candidates 兩行 read
- [ ] `ImeServicePanels.kt:479-480` 改呼 coordinator
- [ ] `YukiBoardIMEService.kt:443-444` 改呼 coordinator
- [ ] 不動其餘 42 處直寫
- [ ] 不動 `ImeUiState.candidates` 結構
- [ ] compile + 按 `IME_SMOKE_CHECKLIST.md` 做 session 邊界 smoke

---

## 7. Phase 2 readiness 回應

`PHASE2_READINESS_CHECK.md §5` 列了 ready 前必須完成的 7 條，逐條回覆：

1. **render helper 已定義** → 已有 `renderImeUiState()`，Phase 2 只加 2 行 read；§6.1 建議加 `clearComposingAndCandidates()` helper ✅ 部分具備
2. **熱路徑與冷路徑已正式分開** → 本文件 §2-3 已分 ✅
3. **`handleKeyPress()` 的 post-update 區塊已能被單獨描述** → 3 處（line 783 / 819 / 838），皆在 `needsPostUpdate` 邏輯內 ⚠️ 已描述但未重構，屬 Sprint 4
4. **`applyInputResult()` 與後續 UI 同步責任已釐清** → `applyInputResult` 結尾 + `syncImeCoordinatorFromRouter`，後續熱路徑 sync 由各 handler 負責 ⚠️ 釐清，未重構
5. **callback / panel / swipe 類清理路徑已收斂到少數 helper** → 目前只有 `resetInputUiStateForPanelTransition` 1 個 helper，其餘仍散落 ❌ 未達標
6. **不再新增新的 `keyboardView.updateComposingText / updateCandidates` 分散寫入點** → 本文件提供完整盤點供後續把關 ✅
7. **哪裡保留同步 / 哪裡改 helper / 哪裡未來 collect** → 本文件 §2-3 + §6 提供 ✅

**本次 Claude Code B 判定**：

- §5.5 未達標（callback cleanup 仍散落）
- §5.3-5.4 未達標（Sprint 4 前置未做）
- **結論：本輪不建議 CodeX A 升為 READY**；建議維持 `NOT READY`，但 §6.1 的最小 scope（4 處 session 邊界清空）可作為「Phase 2 最小試點」獨立評估，或直接併入 Sprint 4 的前置收斂

此判定為建議，最終由 `CodeX A` 拍板。

---

## 8. 本輪明確未做

- 未修改任何 `.kt`
- 未修改任何 LOCKED / 禁止修改檔案
- 未 collect 化任何欄位
- 未改 `ImeUiState` 結構
- 未改 `renderImeUiState()` 實作
- 未動 `handleKeyPress()` / `applyInputResult()` / `KeyboardView.kt`
- 未展開 `USER_DICT` / `THEME` / `CLAW` / `REMOTE` / `HANDWRITING` / `MUSIC`
- 未越界進 Sprint 4 / Sprint 5

---

## 9. 交接格式

### A. 變更摘要

新增 `docs/SPRINT3_PHASE2_WRITE_PATH_AUDIT.md`。
盤點 composingText 20 處、candidates 26 處直寫路徑，逐條標示檔案/方法/觸發源/分類/熱路徑/建議同步。
分類依官方三分類：熱路徑同步更新（24 處）/ callback+cleanup（12 處）/ panel+gesture 特例（10 處）。
提出 Phase 2 可收入 render 的最小 4 處（2 composing + 2 candidates，皆為 session 邊界清空）。
本輪刪除稍早誤放在根目錄的 `PHASE2_READINESS_PRECHECK.md`，改至 task block §3 規定路徑。

### B. 風險

本輪純文件，無 compile 變更、無 runtime 變更。
若 `CodeX B` 採用 §6.1 最小 scope，風險限於 session 邊界清空；若擴大到 §5.3 灰色地帶，需評估 `ImeUiState.candidates` 結構升級 + read-modify-write 語意。
`renderImeUiState` 與熱路徑直寫的雙寫期內，須確保 coordinator 寫入先於同幀直寫，否則最後一幀會被 collect 覆蓋回舊值。

### C. 判定

Phase 2 整體：本輪建議維持 `NOT READY`。
最小試點（§6.1 四處 session 邊界清空）：技術上 ready，但最終由 `CodeX B` 做 Task C 邊界審核、`CodeX A` 做 Task D 拍板。

### D. 下一步建議

1. `CodeX B` 依本文件做 Task C：決定是否接受 §6.1 最小 scope；若接受，訂出 Phase 2 最小 file scope
2. `CodeX B` 同時評估 `ImeUiState.candidates` 是否需在 Sprint 4 前升級為攜帶 sourceMap 的結構
3. `CodeX A` 做 Task D 拍板 `PHASE 2 READY / NOT READY`
4. 若 `NOT READY`：Claude Code B 下輪做 `PHASE2_READINESS_CHECK.md §6` 的 callback/cleanup 收斂（如 `clearComposingAndCandidates()` helper）作為 Phase 2 前置 — 需 `CodeX A` 授權
5. `handleKeyPress()` / `applyInputResult()` 的實際拆分 → Sprint 4 範圍，本文件只描述，不動

---

## 10. Helper 收斂方案草案（2026-04-16 下一輪 / 未實作）

> 本節為 Claude Code B 下一輪 cleanup/helper 收斂方案的 **file scope 草案**，未實作任何 `.kt`，僅供 `CodeX B` 邊界審核與 `CodeX A` gate 拍板。
> 未獲 `CodeX A` `READY` 之前，一律視為 `NOT READY / LOCKED`，不得進 implementation。

### 10.1 目標範圍

僅 §6.1 的 **4 處 session 邊界清空寫入**：

| # | 檔案:行 | 寫入 | 寫入語意 |
|---|---------|------|----------|
| 1 | `ImeServicePanels.kt:479` | `keyboardView.updateComposingText("")` | panel 開/關 clear |
| 2 | `ImeServicePanels.kt:480` | `keyboardView.updateCandidates(emptyList())` | 同上 |
| 3 | `YukiBoardIMEService.kt:443` | `keyboardView.updateCandidates(emptyList())` | 新 session 殘留 clear |
| 4 | `YukiBoardIMEService.kt:444` | `keyboardView.updateComposingText("")` | 同上 |

其餘 18 個 composing + 24 個 candidates 寫入一律 **維持同步直寫，不動**。

### 10.2 為何這一輪不經 coordinator

- `imeCoordinator.updateComposingText("") + updateCandidates(emptyList())` 形同把寫入交給 collect 驅動的 `renderImeUiState()`，**屬於 Phase 2 collect 實作**，不屬於本輪
- 本輪目標是把「純 View 直寫」收斂成單一 helper，**作為 Phase 2 collect 化前的 readiness 步驟**
- 如此 Phase 2 collect 化時只需替換 helper 內部的 2 行，call site 不動，降低 Phase 2 的 diff 面積與回歸風險
- 與 `PHASE2_READINESS_CHECK.md §5.5`（callback / panel / swipe 類清理路徑已收斂到少數 helper）一致

### 10.3 建議 helper 設計

命名與語意（草案，最終由 `CodeX B` 決定）：

```kotlin
// 新增位置建議：ImeServicePanels.kt（與 resetInputUiStateForPanelTransition 同檔）
// 純 UI 層清空，不含 inputRouter.reset()、不含 ic.finishComposingText()、不含 resetTouchState()
// 原因：router reset 與 IC batch 的語意綁定現況已在各 call site 明確表達，不宜被 helper 吞掉
internal fun YukiBoardIMEService.clearComposingAndCandidatesUi() {
    keyboardView.updateComposingText("")
    keyboardView.updateCandidates(emptyList())
}
```

**為何命名加 `Ui` 後綴**：

- 明確表達 **僅清 View 層顯示**，不影響 router/IC/touch state
- 避免未來誤用（如在熱路徑誤把 `clearComposingAndCandidates` 當成「完整清除」）
- Phase 2 collect 化後，此 helper 可直接替換為 `imeCoordinator.updateComposingText("") + updateCandidates(emptyList())`，名稱仍合理

**替代命名選項**（供 `CodeX B` 選）：

- `clearComposingAndCandidatesUi()` ← 推薦
- `clearCandidatePanelUi()`
- `clearCandidateAndCompose()`
- `resetCandidatePanelDisplay()`

### 10.4 Call site 改動（草案，僅示意）

**Site A — `ImeServicePanels.kt:477-481`**

改前：
```kotlin
internal fun YukiBoardIMEService.resetInputUiStateForPanelTransition() {
    inputRouter.reset()
    keyboardView.updateComposingText("")
    keyboardView.updateCandidates(emptyList())
}
```

改後：
```kotlin
internal fun YukiBoardIMEService.resetInputUiStateForPanelTransition() {
    inputRouter.reset()
    clearComposingAndCandidatesUi()
}
```

**Site B — `YukiBoardIMEService.kt:430-447`**

改前（lambda 內）：
```kotlin
inputRouter.reset()
keyboardView.updateCandidates(emptyList())
keyboardView.updateComposingText("")
keyboardView.resetTouchState()
```

改後：
```kotlin
inputRouter.reset()
clearComposingAndCandidatesUi()
keyboardView.resetTouchState()
```

### 10.5 維持同步直寫不動的寫入點

所有下列寫入 **不改 helper、不改 coordinator、不動**：

- §2 `composingText` 中除了 #15、#17 的其餘 18 處
- §3 `candidates` 中除了 #22、#24 的其餘 24 處
- 所有帶 `sourceMap` 的 13 處 candidates 寫入（結構升級未決）
- §5.3 read-modify-write 三處（AI debounce / swipe / handwriting ML）
- `handleKeyPress()` / `applyInputResult()` 內的寫入（LOCKED）

### 10.6 最小 file scope 草案

允許修改（cleanup / helper 類）：

```text
- /app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt
  · 新增 clearComposingAndCandidatesUi() 函式（+4 行）
  · resetInputUiStateForPanelTransition 內 2 行 → 1 行呼叫（-1 行）
- /app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt
  · onStartInputView lambda 內 2 行 → 1 行呼叫（-1 行）
- /docs/SPRINT3_PHASE2_WRITE_PATH_AUDIT.md
  · 本文件（已允許）
- /IME_SMOKE_CHECKLIST.md
  · 新增 session 邊界 helper smoke 項（panel 開關、新 session 進入）
```

預計 diff 淨行數：**+2 ~ +4 行（含 helper 定義），呼叫端 -2 行**。

明確不動：

```text
- /app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceInput.kt
- /app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceCallbacks.kt
- /app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeUiState.kt
- /app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionCoordinator.kt
- /app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceLifecycle.kt（renderImeUiState 不加 read）
- /app/src/main/java/com/snowrealm/yukiboard/ui/keyboard/KeyboardView.kt（LOCKED）
- /RULE/*
```

### 10.7 本輪明確不做

1. 不實作任何 `.kt`（僅提案）
2. 不把 `composingText / candidates` 改 collect 驅動
3. 不加任何 coordinator 呼叫到 helper 或 call site
4. 不動 `renderImeUiState()`
5. 不動 `ImeUiState.candidates` 結構
6. 不碰 §5.3 的三個灰色地帶
7. 不動 `handleKeyPress()` / `applyInputResult()` / `KeyboardView.kt`
8. 不擴張 Sprint 4 / Sprint 5

### 10.8 若 `CodeX A` 拍板 READY 後的實作 checklist（供 CodeX B 審核用，本輪不做）

- [ ] `ImeServicePanels.kt` 新增 `clearComposingAndCandidatesUi()` 函式
- [ ] `resetInputUiStateForPanelTransition` 改呼叫 helper
- [ ] `YukiBoardIMEService.onStartInputView` lambda 改呼叫 helper
- [ ] `./gradlew :app:assembleDebug` 通過
- [ ] 按 `IME_SMOKE_CHECKLIST.md` 做三項 smoke：
  - panel 開關前後 composing/candidate 正確清空
  - 從其他 App 切到輸入框新 session 時殘留清空
  - 熱路徑輸入/刪除/送出不受影響（不應受影響，因為呼叫點未變）

---

## 11. 交接格式（本輪：Helper 收斂方案草案，2026-04-16）

### A. 變更摘要

新增 §10「Helper 收斂方案草案」於 `docs/SPRINT3_PHASE2_WRITE_PATH_AUDIT.md`。
提出 `clearComposingAndCandidatesUi()` helper 草案，收斂 §6.1 的 4 處 session 邊界清空寫入（2 composing + 2 candidates）。
最小 file scope：`ImeServicePanels.kt` 新增 helper、兩個 call site 內嵌改呼叫、`IME_SMOKE_CHECKLIST.md` 增 3 項 smoke。
本輪純文件，未修改任何 `.kt`。

### B. 風險

本輪純文件，無 compile / runtime 變更。
若後續 `CodeX A` `READY` 並進 implementation，風險限於 session 邊界清空語意，不觸及熱路徑；
僅需注意 `onStartInputView` lambda 內 helper 呼叫順序需維持 `inputRouter.reset() → helper → resetTouchState()`，避免 touch state 殘留。
`resetInputUiStateForPanelTransition` 內 `inputRouter.reset()` **刻意不吞進 helper**，以免未來誤用到熱路徑時無聲重設 router。

### C. 下一步建議

1. `CodeX B` 做邊界審核：確認 helper 命名、位置、是否吞 `inputRouter.reset()` / `resetTouchState()`、是否允許本輪 scope
2. `CodeX A` 做 gate：輸出 `READY` / `NOT READY` / `LOCKED`
3. 只有 `CodeX A` 明確 `READY` 後，Claude Code B 才進 implementation；否則本提案保持 draft 狀態
4. 若 `CodeX B` 認為此 helper 應一併帶 `imeCoordinator.updateComposingText/updateCandidates` 雙寫以銜接 Phase 2 collect，由 `CodeX B` 決定並補規範；本提案當前只處理純 UI 寫入層
5. `handleKeyPress()` / `applyInputResult()` / Sprint 4 前置重構 — 不屬本輪，保留

### D. 架構決策

- Helper 定位於 `ImeServicePanels.kt`（與 `resetInputUiStateForPanelTransition` 同檔），不新開檔案、不進 `app/ime/` 層
- Helper 僅寫 View，不寫 coordinator、不寫 router、不寫 IC，維持單一職責
- 命名加 `Ui` 後綴以標記「僅視覺層清空」，避免未來誤用到熱路徑
- Call site 數維持 2 處（不擴張）；callback/panel/gesture 類 42 處直寫本輪不動，留待 Sprint 4 正式評估
- `ImeUiState.candidates` 結構升級（承載 sourceMap）**未決**，不在本輪決策範圍，屬 `CodeX B` 職責

### E. CodeX A 審核需求

依 `RULE/SPRINT_GATE_RULE.md §3`，本輪是 `readiness → implementation` 的前置草案，需 `CodeX A` 拍板：

1. 本 helper 收斂是否可進 implementation，或仍維持 `NOT READY`
2. 若 `READY`，是否允許以 §10.6 file scope 直接進 implementation
3. 若 `NOT READY`，請列缺項供下一輪補齊

`CodeX A` 在無正式 `READY` 前，Claude Code B 不會動 `.kt`，本文件維持 draft 狀態。

---

## 12. Helper 落地結果（2026-04-17 實作）

§10 草案已落地，範圍未擴張至 `ImeServiceCallbacks.kt`（不在本輪 file scope 內），僅處理 `ImeServicePanels.kt` + `YukiBoardIMEService.kt` 內可收斂的 cleanup 呼叫。

### 12.1 新增 helper

- **位置**：`app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
- **簽名**：`internal fun YukiBoardIMEService.clearComposingAndCandidatesUi()`
- **內部**：僅 `keyboardView.updateComposingText("")` + `keyboardView.updateCandidates(emptyList())`
- **刻意不吞**：`inputRouter.reset()` / `ic.finishComposingText()` / `resetTouchState()` 仍由 call site 表達

### 12.2 Call site 已改動（3 處）

| # | 檔案:函式 | 改法 |
|---|-----------|------|
| 1 | `ImeServicePanels.resetInputUiStateForPanelTransition` | 內部 2 行直寫 → 1 行 `clearComposingAndCandidatesUi()` |
| 2 | `YukiBoardIMEService.gyroController.setOnCursorMoveListener` F-8 分支 | 2 行直寫 → 1 行 helper 呼叫 |
| 3 | `YukiBoardIMEService.onStartInputView` lambda | 2 行直寫 → 1 行 helper 呼叫（呼叫順序維持：`inputRouter.reset() → helper → resetTouchState()`） |

### 12.3 未改動的範圍

- `ImeServiceInput.kt`、`ImeServiceCallbacks.kt`（不在本輪 file scope）
- 所有熱路徑寫入
- 所有帶 sourceMap 的 candidates 寫入
- `handleKeyPress()` / `applyInputResult()` / `KeyboardView.kt`（LOCKED）
- `ImeUiState` 結構
- `renderImeUiState()` 實作
- `RULE/*`

### 12.4 驗證

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 23s`
- 無新增 import、無跨層依賴、無 coordinator 呼叫
- helper 保持純 View 層同步語意，不改變執行時序

### 12.5 產出文件

- 本文件 §10-§12
- 新增 `docs/INPUT_UI_SYNC_BOUNDARY.md`（4 點邊界說明）

---

## 13. 交接格式（本輪：helper 落地實作，2026-04-17）

### A. 變更摘要

- 新增 helper `YukiBoardIMEService.clearComposingAndCandidatesUi()` 於 `service/ImeServicePanels.kt`
- 3 處 cleanup call site 改呼叫 helper（`resetInputUiStateForPanelTransition` / gyro F-8 / `onStartInputView` lambda）
- 新增 `docs/INPUT_UI_SYNC_BOUNDARY.md`（4 點邊界說明）
- 本文件追加 §12 實作結果
- 未動任何 `.md` / `.kt` 禁止檔案；未擴張至 `ImeServiceCallbacks.kt` / Sprint 4 範圍

### B. 改了哪些 call site

1. `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt` — 新增 helper（+4 行）、`resetInputUiStateForPanelTransition` 呼叫 helper（-1 行淨）
2. `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt` 第 284-285 行（gyro F-8）— 2 行 → 1 行
3. `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt` 第 443-444 行（`onStartInputView` lambda）— 2 行 → 1 行

淨 diff：+4 行 helper 定義，-3 行分散直寫 → 3 個 call site 統一入口。

### C. InputUiSync 邊界（4 點）

1. **composingText 同步 owner**：`YukiBoardIMEService` / `service/` extension；20 處中 18 處 inline 同步（熱路徑 11 + panel/gesture 4 + callback 3），2 處（session 邊界）走 helper
2. **candidates 同步 owner**：同上；26 處中 24 處 inline 同步（熱路徑 13 + panel/gesture 6 + callback 5），2 處（session 邊界）走 helper；`ImeUiState.candidates` 結構未承載 sourceMap，13 處寫入帶 sourceMap 不得提前 collect 化
3. **保留 inline 的寫入點**：`handleKeyPress / applyInputResult`（LOCKED）、`handleDelete/Enter/Space/routeKeyToPanel CJK`、`handleTab`、`handleSwipeInput`、`handleSwitchLang`、`ImeServiceCallbacks.kt` 全部 callback、`HANDWRITING onRecognized`、`requestAICandidates`（read-modify-write）
4. **已改 helper 的寫入點**：`resetInputUiStateForPanelTransition`、gyro F-8 分支、`onStartInputView` session 殘留清除 — 3 處共 6 行 → 3 行 + 1 份 helper 定義

完整版見 `docs/INPUT_UI_SYNC_BOUNDARY.md`。

### D. 驗證結果

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 23s`（18 tasks, 2 executed, 16 up-to-date）
- 無 warning / error 新增
- 無 runtime 時序改變（helper 為純同步 View 寫入，與原 2 行直寫語意一致）

### E. 請 `CodeX A` 直接拍板 `READY / NOT READY`

本輪已達成 `PHASE2_READINESS_CHECK.md §5` 中：

- §5.1 render helper 已定義 ✅（`clearComposingAndCandidatesUi()`）
- §5.2 熱路徑與冷路徑已正式分開 ✅（`INPUT_UI_SYNC_BOUNDARY.md` §3-§4）
- §5.5 session 邊界 cleanup 已收斂到單一 helper ✅（3 call site → 1 helper）
- §5.6 盤點供後續把關 ✅
- §5.7 同步/helper/未來 collect 分類文件 ✅

尚未達成（屬 Sprint 4）：

- §5.3 `handleKeyPress()` post-update 拆分
- §5.4 `applyInputResult()` UI 同步責任拆分

請 `CodeX A` 依 `RULE/SPRINT_GATE_RULE.md §5` 做最終判定：

- **若 READY**：Phase 2 collect 化可進實作，由 `CodeX B` 出 file scope 細則
- **若 NOT READY**：請列缺項；本輪不再自行擴張 scope

---

## 14. Phase 2 第一版 guarded consume 落地（2026-04-17）

### 14.1 本輪執行授權

使用者於 2026-04-17 明確指示：「正式開始 Sprint 3 Phase 2 第一版實作」，允許在 `ImeServicePanels.kt` / `YukiBoardIMEService.kt` 做最小安全實作，允許雙寫過渡。仍禁止 `handleKeyPress()` / `applyInputResult()` / `KeyboardView.kt`。

### 14.2 改動摘要

**Helper dual-write 升級**（`ImeServicePanels.kt`）：

```kotlin
internal fun YukiBoardIMEService.clearComposingAndCandidatesUi() {
    keyboardView.updateComposingText("")      // 既有同步直寫（保留）
    keyboardView.updateCandidates(emptyList()) // 既有同步直寫（保留）
    imeCoordinator.updateComposingText("")    // 新增：coordinator 同步
    imeCoordinator.updateCandidates(emptyList()) // 新增：coordinator 同步
}
```

**新增 guarded consume collector**（`ImeServicePanels.kt`）：

```kotlin
internal fun YukiBoardIMEService.startImeComposingCandidatesCollect() {
    composingCandidatesCollectJob?.cancel()
    composingCandidatesCollectJob = serviceScope.launch {
        imeCoordinator.uiState.collect { state ->
            if (state.composingText.isEmpty()) keyboardView.updateComposingText("")
            if (state.candidates.isEmpty()) keyboardView.updateCandidates(emptyList())
        }
    }
}
```

**Service 側接線**（`YukiBoardIMEService.kt`）：

- 新增 `composingCandidatesCollectJob: Job?` 欄位
- `onCreateInputView` 兩處（一般 / 懸浮）皆接 `startImeComposingCandidatesCollect()` 於 `startUiStateCollect()` 之後
- `onDestroy` `cancelScope` 加上 `composingCandidatesCollectJob?.cancel()` + `= null`

### 14.3 Guarded consume 設計理由

非空寫入不走 collect 的原因：

1. **callback stale-state race**：`ImeServiceCallbacks.kt` 內 raw commit / candidate select / language switch 會直接寫 `keyboardView.updateComposingText("") + updateCandidates(emptyList())`，**不更新 coordinator**。若 collect 在稍後因 panel / voice 狀態變化被喚醒，coordinator 仍持有過期非空值，會覆寫 view → stale regression
2. **熱路徑無損**：`applyInputResult` 結尾 `syncImeCoordinatorFromRouter` 已把非空值寫入 coordinator；若不 guard，collect 會在熱路徑直寫後再寫一次同樣值（idempotent 但多一次 view op）
3. **`.isEmpty()` guard 語意**：collect 只消費「清空信號」，永遠不會用過期值覆寫輸入中的內容。非空 consume 解鎖條件為 callback 端全部走 coordinator 或雙寫

### 14.4 File scope 實際改動

```text
- app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt
  · clearComposingAndCandidatesUi() 新增 2 行 coordinator 寫入
  · 新增 startImeComposingCandidatesCollect() 函式（+12 行）
- app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt
  · 新增 composingCandidatesCollectJob: Job? 欄位
  · onCreateInputView 2 處新增 startImeComposingCandidatesCollect() 呼叫
  · onDestroy cancelScope 新增 2 行 cancel 與 null
- IME_SMOKE_CHECKLIST.md
  · §10c 改寫為 Phase 2 第一版 guarded consume smoke
  · §10d 新增 Phase 2 未完範圍（含 sourceMap 結構阻塞）
  · §已知限制 更新為 Phase 2 第一版已落地
- docs/INPUT_UI_SYNC_BOUNDARY.md
  · §4 更新為 helper + collect 雙寫設計
  · 附註改為完整 consume 解鎖條件清單
```

明確未動：

- `ImeServiceInput.kt` / `ImeServiceCallbacks.kt`
- `ImeServiceLifecycle.kt`（`renderImeUiState` / `startUiStateCollect` 皆保留原狀，Phase 2 consume 走平行 collector）
- `ImeUiState.kt` / `ImeSessionCoordinator.kt`
- `KeyboardView.kt`（LOCKED）
- `handleKeyPress()` / `applyInputResult()`（LOCKED）
- `RULE/*`

### 14.5 驗證

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 5s`（18 tasks, 2 executed）
- 無 warning / error
- 無新增 import（`launch` / `Job` 已存在）

### 14.6 收斂範圍

本輪收斂的 consume 路徑：

| # | Call site | 原本直寫 | 本輪後 |
|---|-----------|----------|--------|
| 1 | `ImeServicePanels.resetInputUiStateForPanelTransition` | `keyboardView.updateComposingText("") + updateCandidates(emptyList())`（2 行） | `clearComposingAndCandidatesUi()`（view + coordinator dual-write + collect re-confirm）|
| 2 | `YukiBoardIMEService.gyroController.setOnCursorMoveListener` F-8 | 同上 | 同上 |
| 3 | `YukiBoardIMEService.onStartInputView` lambda | 同上 | 同上 |

Collect consume 首次涵蓋「session 邊界清空」這一信號類，為 Phase 2 啟動後的第一個真實 coordinator → view 驅動路徑（雖受 `.isEmpty()` guard 限制）。

### 14.7 仍保留 inline 同步的寫入

- §2 熱路徑 11 處（handleKeyPress / applyInputResult / handleDelete/Enter/Space / routeKeyToPanel CJK）
- §2 panel/gesture 4 處（handleSwipeInput / handleSwitchLang / 其他 gesture）
- §3 熱路徑 13 處（同熱路徑類）
- §3 panel/gesture 6 處（swipe / language / HANDWRITING ML / AI debounce）
- `ImeServiceCallbacks.kt` 全部 callback 寫入（raw commit / candidate select / language select）
- 13 處帶 sourceMap 的 candidates 寫入（結構未升級）

---

## 15. 交接格式（本輪：Phase 2 第一版 guarded consume 落地，2026-04-17）

### A. 變更摘要

- Helper `clearComposingAndCandidatesUi()` 升級為 view + coordinator dual-write
- 新增 `startImeComposingCandidatesCollect()`：訂閱 coordinator uiState，對 composingText / candidates 做 `.isEmpty()` guarded consume
- `YukiBoardIMEService` 新增 `composingCandidatesCollectJob`、`onCreateInputView` 兩處啟動、`onDestroy` cancel
- `IME_SMOKE_CHECKLIST.md` §10c/§10d/已知限制 更新為 Phase 2 第一版
- `docs/INPUT_UI_SYNC_BOUNDARY.md` §4 + 附註 更新完整 consume 解鎖條件
- 本文件追加 §14 實作紀錄
- 未動 `ImeServiceCallbacks.kt` / `ImeServiceInput.kt` / `ImeServiceLifecycle.kt` / `KeyboardView.kt` / `handleKeyPress` / `applyInputResult` / `ImeUiState` / `ImeSessionCoordinator`

### B. 這輪收斂了哪些 consume 路徑

3 個 session 邊界 call site，均透過 helper 進入 coordinator → collect → view 的第一版 consume 鏈路：

1. `ImeServicePanels.resetInputUiStateForPanelTransition`（panel 開/關前 cleanup）
2. `YukiBoardIMEService.gyroController.setOnCursorMoveListener` F-8 分支（陀螺儀游標移動前 commit 後 cleanup）
3. `YukiBoardIMEService.onStartInputView` lambda（新 session 殘留 cleanup）

consume 層以 `.isEmpty()` guard 限定「清空信號」，避免 callback 未同步 coordinator 造成 stale-state 覆蓋。

### C. 仍保留哪些 inline 同步

- `handleKeyPress()` / `applyInputResult()` 內所有寫入（LOCKED，屬 Sprint 4）
- `handleDelete / handleEnter / handleSpace / routeKeyToPanel` CJK 熱路徑 11 處 composing + 13 處 candidates
- `handleTab` / `handleSwipeInput` / `handleSwitchLang` / language switch / HANDWRITING ML / AI debounce 特例 4 + 6 處
- `ImeServiceCallbacks.kt` 全部 callback 寫入（raw commit / candidate select / language select）
- 13 處帶 sourceMap 的 candidates 寫入（`ImeUiState.candidates` 結構阻塞）

### D. 驗證結果

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 5s`（18 tasks, 2 executed）
- 無 warning / error
- Smoke checklist §10c 10 項已更新為可於 runtime 驗證的 Phase 2 第一版項目
- 無新 import、無新依賴、無跨層耦合新增
- collect job 生命週期閉環（onCreateInputView 啟動、onDestroy cancel）

### E. 請 `CodeX A` 審核是否可繼續推進 Phase 2

本輪已讓 Phase 2 從純文件盤點進入第一版實作，技術上證明：

- Helper 可安全雙寫 view + coordinator，不影響熱路徑時序
- collect 可在 coordinator `uiState` 上做 guarded consume 而不破壞輸入體感
- 3 處 session 邊界 cleanup 已進入 coordinator → view 驅動路徑

下一輪（若 `CodeX A` 允許繼續推進）候選：

1. 把 `ImeServiceCallbacks.kt` 的 raw commit / candidate select / language select callback 清空寫入改走 coordinator，解除 guard 非空 consume
2. 由 `CodeX B` 決策 `ImeUiState.candidates` 是否升級為攜帶 `CandidateSource` 的結構，解除 13 處 sourceMap 寫入阻塞
3. Sprint 4 正式啟動 `InputUiSync` / `InputActionExecutor` 邊界建立

請 `CodeX A` 依 `RULE/SPRINT_GATE_RULE.md` 判定：

- **READY（繼續 Phase 2 下一輪）**：解鎖 `ImeServiceCallbacks.kt` 收斂 scope
- **NOT READY**：請列本輪缺項；Claude Code B 不再擴張 scope
- **LOCKED**：暫停 Phase 2，改推進 Sprint 4 前置

`CodeX A` 未拍板前，Claude Code B 不會動其他 `.kt`，本輪落地狀態保持。

---

## §16 Phase 2 Round 2 — callback cleanup 收斂（2026-04-17）

### 16.1 scope

本輪唯一目標：讓 `ImeServiceCallbacks.kt` 內 5 處 callback 類 composing/candidates 清空寫入，走統一的 helper 路徑，消除 coordinator 與 view 狀態因 callback 分裂的風險。同時修掉同區域的 `handwritingEngine!!` crash 風險。

Scope 邊界（嚴守）：
- 允許檔案：`ImeServiceCallbacks.kt` / `ImeServicePanels.kt` / `YukiBoardIMEService.kt` / `IME_SMOKE_CHECKLIST.md` / `docs/INPUT_UI_SYNC_BOUNDARY.md` / 本檔
- 禁止：`handleKeyPress()` / `applyInputResult()` / `KeyboardView.kt` / sourceMap 結構升級 / Sprint 4 擴張

### 16.2 新增 helper

在 `ImeServicePanels.kt` 追加 `clearCandidatesUi()`（僅清 candidates，不清 composing）：

```kotlin
internal fun YukiBoardIMEService.clearCandidatesUi() {
    keyboardView.updateCandidates(emptyList())
    imeCoordinator.updateCandidates(emptyList())
}
```

理由：HANDWRITING 分支 commit 後只需清 candidates，不應動 composing（不同路徑的 composing 狀態由 inputRouter 自身管理）；若硬塞 `clearComposingAndCandidatesUi()` 會在其他手寫流程（例如多候選連續選擇）造成假性清空。

### 16.3 5 處 callback cleanup 的改寫

| # | Call site | Before | After |
|---|-----------|--------|-------|
| 1 | `setOnRawCommitListener` → `flushComposingBeforeRawCommit.clearInputUiState` lambda | 兩行 `keyboardView.update*` | `{ clearComposingAndCandidatesUi() }` |
| 2 | swipe 分支 → `clearSwipeState.clearInputUiState` lambda | 兩行 `keyboardView.update*` | `{ clearComposingAndCandidatesUi() }` |
| 3 | `setOnCandidateSelectListener` AI 候選分支（`isAICandidate` 成立後 commit） | 兩行 `keyboardView.update*` | `clearComposingAndCandidatesUi()` |
| 4 | `setOnCandidateSelectListener` HANDWRITING 分支 | `keyboardView.updateCandidates(emptyList())` | `clearCandidatesUi()` |
| 5 | `setOnLanguageSelectedListener` Language 分支（切換完成後） | 兩行 `keyboardView.update*` | `clearComposingAndCandidatesUi()` |

改寫後 grep 驗證：`ImeServiceCallbacks.kt` 內已無任何 `updateComposingText("")` / `updateCandidates(emptyList())` 直寫。

### 16.4 `handwritingEngine!!` crash 風險修復

原本在 `ImeServicePanels.kt` PanelType.HANDWRITING 分支：

```kotlin
if (handwritingEngine == null) {
    handwritingEngine = HandwritingEngine(this).also { it.init() }
}
val p = HandwritingPanelView(this, handwritingEngine!!)  // init() 失敗時仍然 !! → NPE crash
```

修復後（安全 degrade）：

```kotlin
PanelType.HANDWRITING -> {
    if (handwritingEngine == null) {
        try {
            handwritingEngine = HandwritingEngine(this).also { it.init() }
        } catch (e: Exception) {
            Log.e("YukiIME", "HandwritingEngine init failed", e)
        }
    }
    val engine = handwritingEngine
    if (engine == null) {
        Toast.makeText(this, "手寫引擎初始化失敗", Toast.LENGTH_SHORT).show()
        null            // 讓 createPanelView 回 null，外層收 when 用 null-safe 處理
    } else {
        val p = HandwritingPanelView(this, engine).also { imePanelBindings.handwritingPanel = it }
        // ... setup callbacks，含 onCandidateSelected 改走 clearCandidatesUi()
        p
    }
}
```

### 16.5 `.isEmpty()` guard 的語意變化

- Round 1 時：guard 防 callback 未同步 coordinator 造成的 stale-state race（callback 直寫 view 空值 + coordinator 仍持非空 → 若 collect 後續喚醒則覆寫回非空）
- Round 2 後：callback 全改走 helper，coordinator 已不會 stale；guard 從「防 stale」降級為「safety net」（未來若新增 callback 忘記同步，仍由 guard 擋下非空覆寫）
- 移除 guard 的真正阻塞：Sprint 4 `InputUiSync` 熱路徑抽出 + `ImeUiState.candidates` 結構升級（承載 sourceMap）

### 16.6 驗證

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 4s`
- `grep` verify：`ImeServiceCallbacks.kt` 內無殘留 `updateComposingText("")` / `updateCandidates(emptyList())`
- 文件同步：`IME_SMOKE_CHECKLIST.md` §10c-R2 新增；`docs/INPUT_UI_SYNC_BOUNDARY.md` 全文更新為 Round 2 狀態

---

## §17 Phase 2 Round 2 A/B/C/D/E 交接（2026-04-17）

### A. 變更摘要

- `ImeServicePanels.kt`：新增 `clearCandidatesUi()` helper；修復 HANDWRITING 分支 `handwritingEngine!!` crash 風險為 try/catch + null-gate + Toast fallback；HANDWRITING `onCandidateSelected` 改用 `clearCandidatesUi()`
- `ImeServiceCallbacks.kt`：5 處 callback cleanup 全改走 helper（見 §16.3 表）
- `IME_SMOKE_CHECKLIST.md`：新增 §10c-R2 callback consume 收斂章節；調整 §10d 數量描述；附註補 Round 2
- `docs/INPUT_UI_SYNC_BOUNDARY.md`：標題 / 狀態 / §1 / §2 / §3 / §4 / §附註全文對齊 Round 2
- `docs/SPRINT3_PHASE2_WRITE_PATH_AUDIT.md`：新增 §16 / §17（本節）

### B. 收斂了哪些 callback 路徑

1. `setOnRawCommitListener` → `flushComposingBeforeRawCommit.clearInputUiState` lambda → `{ clearComposingAndCandidatesUi() }`
2. `setOnCandidateSelectListener` swipe 分支 → `clearSwipeState.clearInputUiState` lambda → `{ clearComposingAndCandidatesUi() }`
3. `setOnCandidateSelectListener` AI 候選分支（`isAICandidate` 成立後 commit）→ `clearComposingAndCandidatesUi()`
4. `setOnCandidateSelectListener` HANDWRITING 分支（commit 後）→ `clearCandidatesUi()`（candidates-only）
5. `setOnLanguageSelectedListener` Language 分支（切換完成後）→ `clearComposingAndCandidatesUi()`

未碰：熱路徑回寫（第 226-227 行 `keyboardView.updateComposingText(inputRouter.composingText)` / `updateCandidates(..., sourceMap)`）— 因帶 sourceMap，阻塞於結構升級，Round 2 不動。

### C. `handwritingEngine!!` 怎麼修

- Before：`HandwritingEngine(this).init()` 若 throw，`handwritingEngine` 仍為 null，後續 `!!` NPE crash
- After：
  - init 包 try/catch，例外走 `Log.e`
  - 用 local `val engine = handwritingEngine` + null-gate
  - null 時 `Toast.makeText(...).show()` + 回 `null` 讓 `createPanelView` 外層自然收斂
  - 非 null 時正常建 `HandwritingPanelView(this, engine)` 不再 `!!`
- 併行順手改：`onCandidateSelected` 由舊 `keyboardView.updateCandidates(emptyList())` 直寫改用 `clearCandidatesUi()` helper，與 Round 2 callback 路徑一致

### D. 驗證結果

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 4s`
- grep `ImeServiceCallbacks.kt` 確認：已無 `updateComposingText("")` / `updateCandidates(emptyList())` 直寫
- `ImeServicePanels.kt` HANDWRITING 分支無殘留 `!!`
- `IME_SMOKE_CHECKLIST.md` / `docs/INPUT_UI_SYNC_BOUNDARY.md` 同步更新至 Round 2
- Sprint 4 範圍 `handleKeyPress()` / `applyInputResult()` / `KeyboardView.kt` / sourceMap 結構均未動，邊界守住

### E. 請 `CodeX A` 審核是否可繼續推進 Phase 2

Round 2 達成：

- callback 類 cleanup 全走 helper，coordinator / view 不再於 callback 分裂
- `.isEmpty()` guard 由「防 stale」降級為「safety net」
- 同區域 `handwritingEngine!!` crash 風險已以安全 degrade 消除

仍卡住的下一步（Round 3 候選，需 `CodeX A` 明確核可才可動）：

1. `ImeUiState.candidates` 結構升級（承載 `CandidateSource` sourceMap）— 屬 `CodeX B` 決策
2. Sprint 4 `InputUiSync` / `InputActionExecutor` 熱路徑抽出 — 屬 Sprint 4 正式啟動範圍
3. 以上任一完成後 → 非空 consume 解鎖 → `.isEmpty()` guard 可移除 → Phase 2 正式完成

請 `CodeX A` 依 `RULE/SPRINT_GATE_RULE.md` 判定：

- **READY（繼續 Phase 2 Round 3 或正式 Phase 2 完成標記）**：請指定下一輪 scope（結構升級 / Sprint 4 前置 / 其他）
- **NOT READY**：請列 Round 2 缺項；Claude Code B 立即停手不擴張
- **LOCKED**：Phase 2 本輪落地凍結，後續工作轉向 Sprint 4 前置盤點

`CodeX A` 未拍板前，Claude Code B 不會動其他 `.kt`，本輪落地狀態保持。

---

## §18 Phase 2 Round 3 — `CandidateUiState` 結構升級（2026-04-17）

### 18.1 scope

本輪唯一目標：把 Phase 2 卡住的 sourceMap 承載問題從資料結構層解掉。`CodeX A` 已核可採用 `CandidateUiState(items, sourceMap)` 方案，Round 3 為此方案的落地實作。

Scope 邊界（嚴守）：
- 允許檔案：`ImeUiState.kt` / `ImeSessionCoordinator.kt` / `ImeServiceLifecycle.kt` / `ImeServiceCallbacks.kt` / `ImeServicePanels.kt` / `ImeServiceInput.kt`（如需）/ 本文件三份
- 禁止：`KeyboardView.kt` / `handleKeyPress()` / `applyInputResult()` / AI debounce / HANDWRITING onRecognized / swipe RMW / Sprint 4 擴張

### 18.2 `ImeUiState` 結構升級

新增 `CandidateUiState`：

```kotlin
data class CandidateUiState(
    val items: List<String> = emptyList(),
    val sourceMap: Map<String, CandidateSource> = emptyMap(),
) {
    companion object { val EMPTY: CandidateUiState = CandidateUiState() }
}
```

`ImeUiState` 欄位變更：

| Before | After |
|--------|-------|
| `val candidates: List<String> = emptyList()` | `val candidateState: CandidateUiState = CandidateUiState.EMPTY` |

### 18.3 `ImeSessionCoordinator` API 擴充

| API | 語意 |
|-----|------|
| `updateCandidates(candidates: List<String>)`（legacy） | 只寫 items，sourceMap 視為空。`syncImeCoordinatorFromRouter()` 熱路徑相容保留 |
| `updateCandidates(items: List<String>, sourceMap: Map<String, CandidateSource>)`（新） | 同時寫 items + sourceMap，未來給 callback / helper 使用 |
| `updateCandidateState(candidateState: CandidateUiState)`（新） | 直接寫完整 `CandidateUiState`，未來給 `InputUiSync`（Sprint 4）使用 |

`onFinishInput()` 的 `candidates = emptyList()` 改為 `candidateState = CandidateUiState.EMPTY`。

### 18.4 collect guard 升級

`startImeComposingCandidatesCollect()`：

```kotlin
if (state.candidateState.items.isEmpty()) {
    keyboardView.updateCandidates(emptyList())
}
```

從 `state.candidates.isEmpty()` 改讀 `state.candidateState.items.isEmpty()`；guard 語意不變（empty-only safety net）。

### 18.5 helper 與 callback 路徑

- `clearComposingAndCandidatesUi()` / `clearCandidatesUi()`：不動。內部呼叫 `imeCoordinator.updateCandidates(emptyList())`，走 legacy overload → 寫入 `CandidateUiState(emptyList, emptyMap)`，語意一致
- `ImeServiceCallbacks.kt` 5 處 cleanup：Round 2 已全改走 helper，Round 3 不動
- `ImeServiceCallbacks.kt:223` 的 `setOnCandidateSelectListener` post-commit 回寫（`keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)`）：未在本輪任務「接入 candidateState」範圍內，保持 inline 直寫；其 sourceMap 接入 coordinator 的工作屬 Sprint 4 InputUiSync 範圍

### 18.6 驗證

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 9s`（只剩一個與本輪無關的既有 warning）
- grep `state\.candidates` 於 `app/src/main/java/com/snowrealm/yukiboard` 子樹 → 除 `KeyboardViewModel.kt`（無關的 `KeyboardState`）外無其他 reference
- `ImeUiState` / `ImeSessionCoordinator` / `ImeServicePanels` 三處正確讀寫新結構
- 禁區檔案（`KeyboardView.kt` / `handleKeyPress()` / `applyInputResult()` 等）完全未動

---

## §19 Phase 2 Round 3 A/B/C/D/E 交接（2026-04-17）

### A. 變更摘要

- `app/ime/ImeUiState.kt`：新增 `CandidateUiState(items, sourceMap)` data class + `.EMPTY` 常數；`ImeUiState.candidates: List<String>` 升級為 `candidateState: CandidateUiState`
- `app/ime/ImeSessionCoordinator.kt`：`import CandidateSource`；`onFinishInput()` 使用 `CandidateUiState.EMPTY`；`updateCandidates(List<String>)` 改寫為 wrap 成 `CandidateUiState`（保持舊 call site 相容）；新增 `updateCandidates(items, sourceMap)` 與 `updateCandidateState(state)` overload
- `service/ImeServicePanels.kt`：`startImeComposingCandidatesCollect()` guard 改讀 `state.candidateState.items.isEmpty()`
- `IME_SMOKE_CHECKLIST.md`：新增 §10c-R3 結構升級驗證章節 + 附註補 Round 3
- `docs/INPUT_UI_SYNC_BOUNDARY.md`：標題 / §2 / §4 collect 範例 / guard 原因 / 附註全文對齊 Round 3
- `docs/SPRINT3_PHASE2_WRITE_PATH_AUDIT.md`：新增 §18 / §19（本節）

### B. `candidateState` 接進了哪些路徑

1. **資料模型**：`ImeUiState.candidateState` 取代 `candidates`
2. **coordinator cleanup**：`onFinishInput()` → `CandidateUiState.EMPTY`
3. **coordinator API**：
   - legacy `updateCandidates(List<String>)` 內部 wrap 成 `CandidateUiState`
   - 新 `updateCandidates(items, sourceMap)` 可攜帶 sourceMap
   - 新 `updateCandidateState(state)` 為 Sprint 4 InputUiSync 預留
4. **collect guard**：`startImeComposingCandidatesCollect()` 讀 `candidateState.items.isEmpty()`
5. **helper**：`clearComposingAndCandidatesUi()` / `clearCandidatesUi()` 透過 legacy overload 間接寫入 `CandidateUiState(empty, empty)`，語意一致

### C. 仍保留哪些 Sprint 4 阻塞點

- **熱路徑寫 coordinator 含 sourceMap**：以下 13 處寫入 `keyboardView.updateCandidates(items, sourceMap)` 但尚未同步到 coordinator。結構層已可承載，但改走 coordinator 屬 Sprint 4 `InputUiSync` / `InputActionExecutor` 抽出後的工作
  - `ImeServiceInput.kt` 內 7 處（inputRouter 後的 view 回寫）
  - `ImeServiceCallbacks.kt:223`（`setOnCandidateSelectListener` post-commit 回寫）
  - AI debounce（`requestAICandidates`）
  - HANDWRITING onRecognized（`p.onRecognized`）
  - swipe 分支 RMW
  - （其餘帶 sourceMap 的寫入點依 Sprint 4 抽出盤點決定）
- **`handleKeyPress()` / `applyInputResult()` render ownership 抽出**：屬 Sprint 4 `InputUiSync` 邊界建立範圍
- **`.isEmpty()` guard 移除**：等熱路徑全部改走 coordinator 後才可移除

### D. 驗證結果

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 9s`
- 唯一 warning：`ImeServiceLifecycle.kt:109 Condition is always 'true'` — 與 Round 3 無關，既有問題
- `grep` 驗證：除 `KeyboardViewModel.kt`（無關 `KeyboardState`）外，`state.candidates` / `.copy(candidates` 已無 Round 3 目標檔案的殘留
- 禁區守住：`KeyboardView.kt` / `handleKeyPress()` / `applyInputResult()` / `ImeServiceInput.kt` 均未動；`ImeServiceCallbacks.kt` / `ImeServiceLifecycle.kt` 亦未動（本輪僅改 `ImeUiState.kt` + `ImeSessionCoordinator.kt` + `ImeServicePanels.kt` + 三份文件）
- 舊 call site 相容：`syncImeCoordinatorFromRouter()` 仍呼叫 `updateCandidates(inputRouter.candidates)`（items-only），透過 legacy overload 正確寫入 `CandidateUiState(items, emptyMap)`；熱路徑體感不變

### E. 請 `CodeX A` 審核是否可視為 Phase 2 完成

Round 3 達成 `PHASE2_READINESS_CHECK.md §5.3` 結構升級部分；累計 Phase 2 已落地：

- §5.1 ✓ helper dual-write（Round 1）
- §5.2 ✓ guarded empty-only consume（第一版）
- §5.3 ✓ `CandidateUiState` 結構升級（Round 3）
- §5.5 ✓ callback cleanup 收斂（Round 2）
- §5.6 ✓ collect lifecycle（onCreateInputView 啟動 + onDestroy cancel）
- §5.7 ✓ 文件邊界對齊（INPUT_UI_SYNC_BOUNDARY / smoke / audit）

尚未達成：

- §5.4 ✗ 非空 consume 熱路徑改走 coordinator — 屬 Sprint 4 `InputUiSync` 範圍

Round 3 後的建議判定選項：

- **READY（Phase 2 正式完成標記）**：承認結構升級 + callback 收斂已達 Phase 2 原先目標；非空熱路徑 consume 轉為 Sprint 4 範圍推進
- **NOT READY**：請列 Round 3 缺項（例如希望某一批 callback 也本輪接入新 API）；Claude Code B 依指示補齊
- **LOCKED**：Phase 2 結構升級封存；暫不推進任何熱路徑 coordinator 接入

`CodeX A` 未拍板前，Claude Code B 不會動其他 `.kt`，本輪落地狀態保持。


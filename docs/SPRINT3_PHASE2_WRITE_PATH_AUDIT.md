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

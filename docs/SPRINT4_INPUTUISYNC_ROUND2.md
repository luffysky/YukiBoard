# Sprint 4 Round 2 — `ImeServiceInput.kt` 子 handler 遷移至 `InputUiSync`

日期: 2026-04-17
Gate: `CodeX A` Sprint 4 Round 1 = ACCEPTED（2026-04-17）；Round 2 scope = 嚴格限定本檔列出之 file scope，不得擴張
對應: `docs/SPRINT4_INPUTUISYNC_ROUND1.md §7.2`、`docs/INPUT_UI_SYNC_BOUNDARY.md §3`、`SPRINT4_INPUT_FLOW_REVIEW_2026-04-16.md §3.3 / §5.3 / §5.5`

---

## 1. Round 2 目標

把 `ImeServiceInput.kt` 內**已明確屬於 render sync 薄層**的子 handler 寫入改走 `InputUiSync`，完成 `composing / candidateState` 同步 render ownership 的第二層收斂（熱路徑主層於 Round 1 已完成）。

Round 2 嚴守：

- **只動** `ImeServiceInput.kt`（render 遷移） + 必要 doc 更新
- **不動** `applyInputResult()` 本體、`KeyboardView.kt`、`ImeServicePanels.kt` 其餘程式碼、AI debounce、HANDWRITING onRecognized、swipe RMW、Sprint 5 狀態欄位
- 若需共用輔助 API 擴充，暫時記錄為「Round 3 候選」，**不在本輪落地**

---

## 2. file scope 合約（本輪必須遵守）

| 檔案 | 本輪行為 |
|------|---------|
| `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceInput.kt` | **唯一**實作編修對象（render 遷移） |
| `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt` | 不動（`inputUiSync` 已於 Round 1 就位） |
| `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceCallbacks.kt` | 不動 |
| `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceLifecycle.kt` | 不動 |
| `app/src/main/java/com/snowrealm/yukiboard/app/ime/InputUiSync.kt` | 不動（現有 API 足夠） |
| docs | 只動 `docs/INPUT_UI_SYNC_BOUNDARY.md` / `IME_SMOKE_CHECKLIST.md` / 新增本檔 |

---

## 3. 遷移範圍（8 處寫入點，全在 `ImeServiceInput.kt`）

### 3.1 `handleDelete(ic)` — 2 行（L562-563）

```kotlin
// Before
keyboardView.updateComposingText(inputRouter.composingText)
keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)

// After
inputUiSync.renderComposing(inputRouter.composingText)
inputUiSync.renderCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
```

### 3.2 `handleEnter(ic)` — 2 行（L569-570）

`applyInputResult(ic, result)` 後的 render sync pair，與 3.1 語意相同，一樣遷移。

### 3.3 `handleSpace(ic)` — focus-confirm 分支 2 行（L625-626）

```kotlin
// Before
keyboardView.updateComposingText("")
keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)

// After
inputUiSync.renderComposing("")
inputUiSync.renderCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
```

注意：`renderComposing("")` 不使用 `clearAll()`，因為緊接著要用目前的 `inputRouter.candidates`（可能因 selectCandidate 後產生下一詞預測）重新寫入。

### 3.4 `handleSpace(ic)` — 一般分支 2 行（L632-633）

`applyInputResult(ic, inputRouter.processSpace())` 後的 render sync pair，與 3.1 同模式。

### 3.5 `routeKeyToPanel` — `KEYCODE_DELETE` 分支（L683-684）

CJK 且 composing 非空時呼叫 `inputRouter.processDelete()` 後的 render sync pair。與 3.1 同模式。

### 3.6 `routeKeyToPanel` — `KEYCODE_ENTER` 分支（L693-694）

```kotlin
// Before
keyboardView.updateComposingText("")
keyboardView.updateCandidates(emptyList())

// After
inputUiSync.clearAll()
```

這一處是「CJK composing 清空 + candidates 清空」的完整 reset，改用 `clearAll()` 收斂為單一呼叫。`updateCandidates(emptyList())` → `clearCandidates()` 時 sourceMap 自動為 `emptyMap()`。

### 3.7 `routeKeyToPanel` — `KEYCODE_SPACE` 分支（L704-705）

CJK 且 composing 非空時 `inputRouter.processSpace()` → `panel.onComposedTextInput(result.text)` 後的 render sync pair。與 3.1 同模式。

### 3.8 `routeKeyToPanel` — `else`（一般 label）分支（L718-719）

CJK 時 `inputRouter.processInput(label)` → `panel.onComposedTextInput(result.text)`（若是 CommitText）後的 render sync pair。與 3.1 同模式。

---

## 4. 本輪**未動**的寫入點（守住禁區）

| 行號 | 位置 | 原因 |
|------|------|------|
| `ImeServiceInput.kt:402` | `requestAICandidates()` AI debounce | FORBIDDEN（AI debounce RMW — Round 2 scope 外，需專用 API 判定） |
| `ImeServiceInput.kt:468-469` | `handleSwipeInput()` swipe RMW | FORBIDDEN（swipe 段累積 RMW — Round 2 scope 外） |
| `ImeServiceInput.kt:536` | `handleTab()` 候選 focus 刷新 | boundary doc §3 列為 inline 保留；不在本輪 scope |
| `ImeServiceInput.kt:650` | `handleSwitchLang()` AUTO layout toggle | boundary doc §3 列為 inline 保留 |
| `ImeServiceInput.kt:658` | `handleSwitchLang()` 一般語言切換 | 同上 |
| `ImeServicePanels.kt:*` | HANDWRITING onRecognized ML RMW | FORBIDDEN（file scope 外 + RMW 判定未拍板） |
| `YukiBoardIMEService.kt:789` | `handleKeyPress` TOGGLE_KANA niche | Round 1 已列為「下輪評估」，本輪未納入 |
| `YukiBoardIMEService.kt:825-826` | `handleKeyPress` intermediate clear | 同上 |
| `applyInputResult()` 本體 | 副作用分類抽出 | Round 3+ 範圍 |

---

## 5. 驗證

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 7s`
- `Grep "keyboardView\\.(updateComposingText|updateCandidates)"` on `ImeServiceInput.kt` 結果只剩：
  - L402（AI debounce，FORBIDDEN）
  - L468-469（swipe RMW，FORBIDDEN）
  - L536（handleTab focus，inline 保留）
  - L650 / L658（handleSwitchLang，inline 保留）
  - **4 priority handlers 寫入點全清**
- 其他禁區檔案（`KeyboardView.kt` / `InputRouter.kt` / `ImeServicePanels.kt` / Sprint 5 狀態）未動
- 熱路徑時序：全同步 dual-write；與 `ic.beginBatchEdit` 相容；compile 無 warning
- sourceMap 流向：8 處遷移中凡涉及 candidates 的皆透過 `renderCandidates(items, sourceMap)` 同步進 coordinator（Round 3 結構升級後的正式使用路徑）

---

## 6. 對照 `SPRINT4_INPUT_FLOW_REVIEW §5`

| 條件 | Round 1 前 | Round 1 後 | Round 2 後 |
|------|-----------|-----------|-----------|
| §5.3 composing/candidates 同步 render owner 明確 | NO | 達成（熱路徑主層） | **強化**（子 handler 薄層亦收斂） |
| §5.5 不破壞既有輸入體感 | — | 達成 | 達成（8 處全同步遷移；時序不變） |
| §5.1 `handleKeyPress()` 邏輯分層描述 | 部分 | 部分 | 部分（未變動，Round 3+） |
| §5.2 `applyInputResult()` 副作用分類 | 部分 | 部分 | 部分（未變動，Round 3+） |

---

## 7. Round 3 候選範圍（需 `CodeX A` 核可才可啟動）

1. **`applyInputResult()` 副作用分類落地**（Round 1 §2.3 列的 11 類）— `InputActionExecutor` 抽出
2. **`syncImeCoordinatorFromRouter()` 並入 InputUiSync**：候選做法是增一個 `syncFromRouter()` 方法取代 coordinator-only 寫入
3. **AI debounce / HANDWRITING onRecognized / swipe RMW**：判定 RMW 是否能安全 dual-write，或需 `mergeCandidates` 專用 API
4. **`handleKeyPress` niche 路徑**：TOGGLE_KANA、intermediate char-commit clear
5. **`handleTab / handleSwitchLang` 評估**：目前 boundary doc 列 inline，可評估是否仍需保留

`CodeX A` 未拍板 Round 3 scope 前，Claude Code B 不自行擴張。

---

## 8. A/B/C/D/E 交接

### A. 變更摘要

- `ImeServiceInput.kt`：8 處寫入點遷移至 `inputUiSync.render{Composing,Candidates}` / `inputUiSync.clearAll()`
- 涉及子 handler：`handleDelete` / `handleEnter` / `handleSpace`（2 分支）/ `routeKeyToPanel`（4 分支：DELETE / ENTER / SPACE / else）
- 新文件：`docs/SPRINT4_INPUTUISYNC_ROUND2.md`（本檔）
- 更新文件：`docs/INPUT_UI_SYNC_BOUNDARY.md`（§3 / §4）、`IME_SMOKE_CHECKLIST.md`（新增 §10c-S4R2）
- 未新增檔、未改其他 `.kt`

### B. Round 2 遷移了哪些 `ImeServiceInput.kt` 路徑

| 路徑 | 分支 / 情境 | 寫入模式 | Before → After |
|------|-----------|---------|----------------|
| `handleDelete` | 通用（delete 後 sync） | composing + candidates | `keyboardView.update*` → `inputUiSync.render*` |
| `handleEnter` | applyInputResult 後 sync | composing + candidates | 同上 |
| `handleSpace` | Tab focus 確認後 commit | "" + candidates | 同上 |
| `handleSpace` | 一般 space（CJK 轉換） | composing + candidates | 同上 |
| `routeKeyToPanel` | `KEYCODE_DELETE` CJK 分支 | composing + candidates | 同上 |
| `routeKeyToPanel` | `KEYCODE_ENTER` CJK 分支 | "" + emptyList | **合併** → `inputUiSync.clearAll()` |
| `routeKeyToPanel` | `KEYCODE_SPACE` CJK 分支 | composing + candidates | 同上 |
| `routeKeyToPanel` | else（一般 label）CJK 分支 | composing + candidates | 同上 |

### C. 還留在哪些高風險點

Round 2 **未動**的寫入（維持 inline 直寫）：

- `requestAICandidates()` AI debounce（L402）— RMW，需判定 dual-write 安全性
- `handleSwipeInput()` swipe 段累積 RMW（L468-469）— 同上
- `handleTab()` 候選 focus 刷新（L536）— boundary doc inline 保留
- `handleSwitchLang()` 清候選（L650 / L658）— boundary doc inline 保留
- `handleKeyPress` TOGGLE_KANA / intermediate clear niche 路徑
- `ImeServicePanels.kt` HANDWRITING `onRecognized` ML RMW
- `applyInputResult()` 副作用分類（11 類未抽出）
- `syncImeCoordinatorFromRouter()`（coordinator-only 寫入，未改）
- `startImeComposingCandidatesCollect()` guard 仍在（尚無法撤除）

### D. 驗證結果

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 7s`
- `Grep "keyboardView\\.(updateComposingText|updateCandidates)"` on `ImeServiceInput.kt` 共 5 筆殘留，**全部**屬於 FORBIDDEN 或 boundary-doc-inline
- 4 priority handlers（`handleDelete` / `handleEnter` / `handleSpace` / `routeKeyToPanel`）寫入點 100% 遷移
- file scope 合約達成：只動 `ImeServiceInput.kt` + 3 份 doc，未擴張
- 禁區未動：`KeyboardView.kt` / `InputRouter.kt` / `ImeServicePanels.kt` 其他程式 / Sprint 5 狀態欄位

### E. 請 `CodeX A` 審核是否可進 Sprint 4 Round 3

Round 2 達成 Round 1 §7.2 規劃的「`ImeServiceInput.kt` 子 handler 遷移」全部可安全遷移項目，且全部守住 file scope 合約。

建議判定選項：

- **READY（繼續 Round 3）**：請指定下一輪 scope（建議優先序：§7.1 `applyInputResult()` 副作用分類 → §7.2 `syncImeCoordinatorFromRouter()` 並入 → §7.3 RMW 路徑評估 → §7.4 niche 路徑 → §7.5 `handleTab / handleSwitchLang` 再評估）
- **NOT READY**：請列 Round 2 缺項；Claude Code B 依指示補齊
- **LOCKED**：Sprint 4 Round 2 封存，暫不推進後續熱路徑遷移

`CodeX A` 未拍板前，Claude Code B 不會動其他 `.kt`，本輪落地狀態保持。

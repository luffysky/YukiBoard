# SPRINT10-0 CCB AUTO 模式 bug 前置盤點

身份：Claude Code B
日期：2026-04-18
任務 ID：`S10-0-CCB-AUTO-MODE-PRECHECK`
對應 gate：`Sprint10-All-Round-Gate.md` → `Round S10-0`
本輪限制：**不改 `.kt`**、只做重現路徑 / 邏輯路徑 / 最小 file scope 盤點。

---

## 0. 目的與限制

1. 僅盤點 `AUTO` 模式 bug 的重現條件、candidate path、buffer path 與最小 file scope。
2. 使用者原始回報只有一句（daily_works_0418.md §7）：
   > 「自動模式輸入法仍有 bug」
   本文件據此拆出多個可能重現情境，**不自行排名為 S10-2 實作主題**；最終挑哪一條為 bugfix target 由 `CodeX A` 裁決。
3. 本輪不改 `.kt`、不寫 `kt_review_checkbug.md`、不宣告 `READY / COMPLETED`。

---

## 1. AUTO 模式系統結構（唯讀盤點）

### 1.1 開關入口

| 入口 | 檔案:行 | 行為 |
|---|---|---|
| 設定頁切換 | `ui/settings/section/InputBehaviorSection.kt:33` | 「智慧中英切換」switch → 寫 `prefs.autoInputMode` |
| 環形選單選 AUTO | `service/ImeServiceCallbacks.kt:243-260` | 選 `AUTO` → `prefs.autoInputMode = true` + `inputRouter.switchTo(AUTO)` |
| 環形選單選其它 | `service/ImeServiceCallbacks.kt:247-249` | 若 `prefs.autoInputMode` 為 true 則清為 false |
| onStartInputView 啟動 | `service/YukiBoardIMEService.kt:488-492` | 若 `prefs.autoInputMode` 為 true 且當前不是 AUTO，自動 `switchTo(AUTO)` |
| 短按語言鍵 | `service/ImeServiceInput.kt:645-652` | AUTO 模式下改為呼叫 `inputRouter.toggleAutoLayout()`（僅切鍵盤佈局，不離開 AUTO） |

### 1.2 AUTO 狀態欄位（皆在 `InputRouter.kt`）

| 欄位 | 類型 | 行 | 說明 |
|---|---|---|---|
| `currentMode` | `InputMode` | 95 | AUTO 時固定為 `InputMode.AUTO` |
| `activeEngine` | `InputEngine` | 96 | AUTO 下會在 `englishEngine` / `bopomofoEngine` 之間動態切換，**預設起點 = `bopomofoEngine`**（`engineForMode` line 230） |
| `autoQwertyBuffer` | `StringBuilder` | 61 | 英文解讀緩衝（QWERTY 字母序列） |
| `autoBopomofoBuffer` | `MutableList<String>` | 62 | 注音解讀緩衝（注音符號序列） |
| `autoLastCommitWasEnglish` | `Boolean` | 63 | 上次 commit 是否英文 → 下回合評分連續性訊號 |
| `autoEnglishScore` / `autoBopomofoScore` | `Int` | 64-65 | 最近一次評分結果（僅讀，`autoDetectAndSwitch` 內部更新） |
| `autoLayout` | `enum AutoLayout { QWERTY, BOPOMOFO }` | 68-70 | 使用者手動切的顯示佈局，**不影響 AUTO 判斷**、**未持久化**（每次 `InputRouter` 新建回到 BOPOMOFO） |
| `pendingCommitText` | `String?` | 104 | Chewing→English 切換時暫存 `selectedBuffer`，由 Service 於 `applyInputResult` 開頭 `consumePendingCommit()` |

---

## 2. Candidate path 盤點（AUTO 專用）

### 2.1 路徑 A：Router 內建合併

- `InputRouter.processInput()` → `autoDetectAndSwitch(label)` → `activeEngine.processChar(effectiveLabel)` → `updateCandidates()`
- `updateCandidates()` (InputRouter.kt:596-604)：
  ```
  if (currentMode == AUTO && autoQwertyBuffer.isNotEmpty()) {
      raw = getAutoCombinedCandidates().filter { isRenderable(it) }
  } else {
      raw = activeEngine.getCandidates().filter { isRenderable(it) }
  }
  ```
- 之後 `raw` 進入「非英文模式」分支（line 608-756），因為 AUTO 被視為 `isCjkMode = true`（line 659-662），會加整句預測 / 串聯接續 / COMMON_WORD_FREQUENCY 加分。

### 2.2 路徑 B：Service 再疊一層

- `YukiBoardIMEService.kt:894-900`：
  ```
  if (inputRouter.inputMode == AUTO && autoQwertyBuffer.isNotEmpty()) {
      val bilingual = inputRouter.getAutoCombinedCandidates()
      for (bc in bilingual) if (bc !in candidates) candidates.add(bc)
  }
  ```
  → `inputUiSync.renderCandidates(candidates, candidateSourceMap)`

### 2.3 觀察（非本輪修）

1. **`getAutoCombinedCandidates()` 被呼叫兩次**：
   - 第一次：`InputRouter.updateCandidates()` 已把合併結果灌進 `candidateManager.candidates`，並依 `COMMON_WORD_FREQUENCY`、`smartInput.getSentencePredictions` 重排。
   - 第二次：Service 又 `getAutoCombinedCandidates()` 結果 **append** 到 `candidates` 尾端。
   - 結果：合併候選在 router 層被當成高權排序 (整句預測 / 常用字加分)，Service 層又重新塞進尾端，**造成「排序後的合併候選」與「原始合併候選」同時存在於同一 list**。
   - 因 `if (bc !in candidates)` 僅去重字串，若 router 端已有則跳過；若 router 端因為 `isRenderable()` / `filter` 濾掉某些 → Service 端會把它們補回尾端。
   - `candidateSourceMap` 只反映 router 端建立的來源標記，Service 端補進來的字串**沒有對應 source**，render 時會掉 source 標色。
2. 「合併候選同時來自兩個引擎」依賴 `autoQwertyBuffer.isNotEmpty()`。若使用者在 AUTO 下第一個鍵就是純注音符號（無法 map 到 QWERTY，如 `ㄅ` / `ㄉ` / `ㄦ` / 聲調鍵），line 452-463 會直接走 `return`，**autoQwertyBuffer 保持空**，於是：
   - Router 端 AUTO 分支不啟動 → 走 `activeEngine.getCandidates()`（現在是 `bopomofoEngine`）
   - Service 端 `autoQwertyBuffer.isNotEmpty()` 條件不成立 → 不補雙語
   - 結果：純注音輸入不會看到英文候選，正確。但若使用者打第一下就是這類鍵，之後切回有映射的鍵，會發現英文評分從 0 起算，**首字符永遠評不上英文 threshold (50)**。

## 3. Buffer path 盤點

### 3.1 累積

- `autoDetectAndSwitch` (InputRouter.kt:437-546)：
  - **非 Latin/非注音早返回**（line 441-442）：韓文 Jamo / 日文假名範圍 → `switchToClean(KOREAN/JAPANESE)` → **離開 AUTO**、進入對應模式。
  - **純注音鍵（無 QWERTY 映射）**（line 452-463）：把 activeEngine 換為 Chewing、replay `autoBopomofoBuffer` 到 Chewing、然後清兩個 buffer 並 return。
  - **雙重映射鍵**：同時 append 到兩個 buffer（line 466-471）。
- Score 計算（line 475-522）→ 依 threshold 切 activeEngine（line 524-544）。
- 切換時會 `activeEngine.reset()` + `for (c in ...dropLast(1)) englishEngine.processChar(...)` 重播（line 532-542），**current key 不 replay**（因尾端 dropLast(1)），依賴後續 `activeEngine.processChar(effectiveLabel)` 把當前鍵補上（`processInput` 行 262）。

### 3.2 清除

| 事件 | 行 | autoQwertyBuffer | autoBopomofoBuffer | activeEngine |
|---|---|---|---|---|
| `processDelete`（composing 已空） | 274-277 | clear | clear | 保留 |
| `processEnter` | 307-308 | clear | clear | 保留 |
| `switchTo` | 216-218 | clear | clear | 換為新 mode 引擎 |
| `reset()` | 413-414 | clear | clear | 保留 (activeEngine.reset()) |
| `onTextCommitted`（AUTO 分支） | 945-946 | clear | clear | **保留**（line 950 註記「不再重置 activeEngine」） |
| `autoDetectAndSwitch` 非 Latin 早返回 | — | 保留 | 保留 | switchToClean → 換模式 |
| `autoDetectAndSwitch` 純注音鍵早返回 | 460-461 | clear | clear | 切 Chewing |

### 3.3 觀察（非本輪修）

1. **非 Latin 早返回後 buffer 殘留**：line 441-442 `switchToClean` 只動 `currentMode` 與 `activeEngine`，**沒有清 `autoQwertyBuffer` / `autoBopomofoBuffer`**。若使用者在 AUTO 下先打幾個英數，再按日文假名 / 韓文 Jamo，buffer 殘留進 JAPANESE/KOREAN 模式；但 `currentMode` 已不是 AUTO，`processInput` 下次不會走 AUTO 分支，殘留 buffer 無人消費，等到**下次切回 AUTO 時**才在 `switchTo(AUTO)` 被 clear（line 216-218），無實際 bleed — 但仍屬「離開 AUTO 時 buffer 不對稱清」。
2. **autoLastCommitWasEnglish 的 ASCII 判斷過寬**：line 949 `trimmed.all { it.code < 128 }` 包含空白 / 標點 / 數字 / emoji base。使用者 commit 了純數字或純標點後下次 AUTO 評分會 +20 給英文，可能造成「打標點後下一字偏英文」。

## 4. Commit path 盤點

### 4.1 三種 commit 來源

1. **按 Space / Enter**：Router `processSpace` / `processEnter` → `InputResult.CommitText` → Service `applyInputResult`。
2. **選候選**：Router `selectCandidate(index)` → `directCommit(text)` 或引擎 `selectCandidateAt(engineIndex)` → `InputResult.CommitText` → Service。
3. **AUTO 切引擎 mid-stream**：`autoDetectAndSwitch` line 527-530 把 Chewing `getSelectedBuffer()` 存進 `pendingCommitText` → Service 在下次 `applyInputResult` 開頭 `consumePendingCommit()` 取出並 commit（`YukiBoardIMEService.kt:949-957`）。

### 4.2 pendingCommitText 生命週期

- 產生點只有一個：`autoDetectAndSwitch` 的 Chewing→English 分支（line 527-530）。
- 消費點：`applyInputResult` 開頭（line 952）一次性消費並清為 null。
- 風險點：若 `autoDetectAndSwitch` 裡 set 了 `pendingCommitText`，但 `processInput` 返回後 Service 沒走 `applyInputResult`（例如 handler 提早 `return`），pendingCommit 會**跨到下一次 `applyInputResult`**，被誤 commit 進不相干上下文。目前 `processInput` 後一律 `applyInputResult(ic, result)`（`handleKeyPress` 主路徑），暫時無漏；但若未來有路徑短路，會踩到。

### 4.3 selectCandidate 路徑在 AUTO 下的特例

- Router `selectCandidate` 在 AUTO 下的 `activeEngine` 可能是 `EnglishInputEngine` 或 `ChewingInputEngine`：
  - Chewing 分支：`eng.getCandidates().indexOf(displayedText)` 找 engine index，拿不到就 `directCommit(displayedText)`。
  - English 分支（else 區塊 line 391-399）：透過 `candidateManager.selectAt(index)` → `directCommit`。
- `directCommit` 對 Chewing 會先取 `getSelectedBuffer()` 合併到 text（line 331-338）。對 English 不會。
- 若使用者選到的 `displayedText` 是「由 `getAutoCombinedCandidates()` 產生的注音候選」，但當前 `activeEngine` 已是 English（評分偏英文後切過去），則：
  - `activeEngine = EnglishInputEngine`，走 else 分支
  - `directCommit(selected)` 用 **English engine reset**，不會 reset Chewing 的 composing
  - 然而此時 Chewing 的 composing 早在 `activeEngine.reset()`（line 531）時就被清過 → 應無殘留。
- 需要確認的 edge case：**使用者在切引擎後、selectCandidate 前，再敲一個鍵讓 `activeEngine` 又切回 Chewing** 時，selectCandidate 當前 activeEngine 狀態是否與候選來源對應。

## 5. 可能的重現情境（候選 bug 清單）

> 以下為根據 code path 推出的候選重現情境，**未向使用者逐一確認**；僅供 `CodeX A` 從中圈選真正的 S10-2 target。

### R1. AUTO 下純注音第一鍵 → 永遠進 Chewing
- **條件**：`prefs.autoInputMode = true`；AUTO 下第一鍵為 `ㄅ` / `ㄉ` / `ㄦ` / 任一聲調鍵（無 QWERTY 映射）。
- **行為**：line 452-463 直接把 `activeEngine` 設為 Chewing 並 return，`autoQwertyBuffer` 保持空。
- **風險**：之後打任何英文字母都會進入 Chewing 的 `processChar`，評分從 0 起算，threshold 50 難以翻身。
- **涉及**：`InputRouter.autoDetectAndSwitch`（非 `getAutoCombinedCandidates` / 非 commit path）。

### R2. 候選列重複來源 / source 標記掉色
- **條件**：AUTO 下連續英文輸入產生雙語候選，candidate bar 顯示。
- **行為**：同一字串可能同時被 router 層計入 `candidateSourceMap`（有來源色）與 Service 層補進尾端（無來源色）。若 `isRenderable` 在 router 端濾掉某候選而 Service 端沒濾，該候選出現時**無 source**。
- **涉及**：`InputRouter.updateCandidates` + `YukiBoardIMEService.kt:894-900`（**router ⇄ service 雙處**）。

### R3. 環形選單選 AUTO 時，`switchTo(AUTO)` 回傳的 commit 沒走 `applyInputResult`
- **條件**：composing 非空時從環形選單選 AUTO。
- **行為**：`ImeServiceCallbacks.kt:250-253`
  ```
  val switchResult = inputRouter.switchTo(mode)
  if (switchResult is InputResult.CommitText) {
      currentInputConnection?.commitText(switchResult.text, 1)
  }
  ```
  → 直接 commit，**未走 `applyInputResult`**，因此：
  - 不處理 `pendingCommitText`（其實此情境下不會有 pending，但若未來流程變動會漏）
  - 不觸發 `onTextCommitted` 學詞 / style 更新
- **涉及**：commit path（不在 `applyInputResult`），與 `handleSwitchLang` 的流程對稱性。

### R4. AUTO 下切 `autoLayout`（短按切換鍵）不清 buffer
- **條件**：AUTO 下打了幾字母進 `autoQwertyBuffer`，但還沒 commit，按短按語言鍵。
- **行為**：`handleSwitchLang` 呼叫 `activeEngine.forceCommit()` + `finishComposingText()`，然後**只**呼叫 `toggleAutoLayout()` + `updateCandidates(emptyList())`。
  - `autoQwertyBuffer` / `autoBopomofoBuffer` 仍保有舊字元。
  - 下一鍵在 `autoDetectAndSwitch` 評分時會 append 到舊 buffer，造成跨段累積。
- **涉及**：`InputRouter.toggleAutoLayout`（當前無清 buffer）+ `ImeServiceInput.handleSwitchLang`（切 layout 路徑）。

### R5. `autoLastCommitWasEnglish` 對純標點 / 純數字誤判為英文
- **條件**：AUTO 下 commit 純數字 / 純標點（例 `.`、`123`），下一段純注音輸入。
- **行為**：line 949 `trimmed.all { it.code < 128 }` 判為 true → `autoLastCommitWasEnglish = true` → 下段評分英文 +20 連續性。
- **涉及**：`InputRouter.onTextCommitted` (AUTO 分支)。

### R6. AUTO 下按非 Latin 鍵（日韓）離開 AUTO 後 buffer 殘留
- **條件**：AUTO 下先打數個英數，再按日文假名 / 韓文 Jamo 鍵（透過鍵盤切或手動鍵入特殊字元）。
- **行為**：`autoDetectAndSwitch` line 441-442 `switchToClean` 切 `currentMode` → **不清 AUTO buffer**。下次 `switchTo(AUTO)` 才清。
- **涉及**：`InputRouter.autoDetectAndSwitch`（早返回）+ `InputRouter.switchToClean`（未清 buffer）。
- **影響**：目前評估為 benign（離開 AUTO 後 buffer 無人消費），但屬對稱性缺口。

### R7. onStartInputView 切 AUTO 的 commit 也不走 `applyInputResult`
- **條件**：`prefs.autoInputMode = true`，打開新的輸入框時 inputRouter 當前非 AUTO。
- **行為**：`YukiBoardIMEService.kt:489-492`
  ```
  val sr = inputRouter.switchTo(InputMode.AUTO)
  if (sr is InputResult.CommitText) currentInputConnection?.commitText(sr.text, 1)
  ```
  → 同 R3，不走 `applyInputResult`。通常 onStartInputView 前 IC 新開，不會有 composing，但若跨框切換時前一框還有 pending，會以 "raw commit" 方式丟出，略過學詞 / cooldown 等副作用。
- **涉及**：commit path（啟動路徑與 `applyInputResult` 不對稱）。

### R8. AUTO 下由 `getAutoCombinedCandidates` 生成的候選字，不在當前 activeEngine 字庫裡，但被選中
- **條件**：英文評分高 → activeEngine = English；候選列有「英文候選 + 注音候選混排」；使用者選了注音候選。
- **行為**：Router `selectCandidate` 走 else（English engine）分支 → `candidateManager.selectAt(index)` 取出字串 → `directCommit(selected)`。成功 commit，但：
  - `smartInput.onCandidateHit()` 觸發，adaptiveDict 會記這個中文詞
  - Chewing engine 的 `lastComposingBopomofo` 無值，line 971-976 不做 `userDict.addManual`
- **涉及**：`InputRouter.selectCandidate` (English 分支) + `InputRouter.getAutoCombinedCandidates` + commit path。
- **影響**：學詞偏差而非 commit 錯誤；屬長尾。

---

## 6. 最小 file scope 建議（依候選情境分組）

> 僅為 scope 建議，實際 round file scope 請由 `CodeX A` 裁決。

| 情境群 | 主要 write scope 候選 | 附帶觀察檔（可能只讀） |
|---|---|---|
| R1 / R4 / R6（autoDetect 分支 + toggleAutoLayout 清 buffer） | `input/router/InputRouter.kt` 單檔 | — |
| R2（候選重複 / source 標記） | 可選：router 端去雙呼叫 → `InputRouter.kt`；或 service 端移除補 append → `service/YukiBoardIMEService.kt` | `app/ime/InputUiSync.kt`（render 方）、`input/candidate/CandidateManager.kt`（承接方） |
| R3 / R7（commit 不走 `applyInputResult`） | `service/YukiBoardIMEService.kt` + `service/ImeServiceCallbacks.kt` | `input/router/InputRouter.kt`（`switchTo` 回傳 contract） |
| R5（`autoLastCommitWasEnglish` 判準） | `input/router/InputRouter.kt` 單檔 | — |
| R8（selectCandidate AUTO 學詞偏差） | `input/router/InputRouter.kt` 單檔 | `input/engine/ChewingInputEngine.kt`、`input/dictionary/UserDictionary.kt` |

### 禁區（`Sprint 10` 全程不碰）

- `ui/keyboard/KeyboardView.kt`、`handleKeyPress()` 核心演算法（CLAUDE.md 禁區）
- `ui/settings/SettingsActivity.kt` 本體、`RULE/*`、`CLAUDE.md`、任何 `Sprint[N]-All-Round-Gate.md`
- `Sprint 6/7/8/9` 已結案的 `.kt` 主修補點（例如 `UserDictionary.versionLock`、`ImeSessionHooks.DestroyCleanup.releaseInputRouter`、`CloudSyncManager.setSmartInputApplier` 等，不得回頭改語意）

---

## 7. 涉及項目明示（照 gate 要求）

| 路徑 | 是否涉及 |
|---|---|
| `InputRouter.AUTO`（含 `currentMode` / `autoDetectAndSwitch` / `switchTo(AUTO)`） | **是**（R1 / R3 / R4 / R6 / R7 全部命中） |
| `getAutoCombinedCandidates()` | **是**（R2 / R8 涉及；R1 / R5 不直接涉及） |
| commit path（`applyInputResult` / `consumePendingCommit` / `onTextCommitted`） | **是**（R3 / R5 / R7 / R8 涉及；R1 / R2 / R4 / R6 間接涉及） |

---

## 8. 候選 smoke path（供 S10-2 後用，僅列不排序）

1. 開 AUTO → 打 `apple`：應切英文引擎並出英文候選。
2. 開 AUTO → 打注音 `ㄆㄧㄥ ㄍㄨㄛ`：應切 Chewing 並出注音候選。
3. 開 AUTO → 先打 `ab` 再打 `ㄅ`：觀察 autoBopomofoBuffer 與 activeEngine 變化（涉及 R1 / R6）。
4. 開 AUTO → 打 `ab`，未 commit 前按短按語言鍵（toggleAutoLayout）：觀察 buffer 殘留（R4）。
5. 開 AUTO → commit `123`，下一段打純注音：觀察連續性訊號（R5）。
6. 開 AUTO → 打 `apple`，選一個注音候選：觀察學詞與 Chewing composing（R8）。
7. 開 AUTO → 打 `apple` 中途打開新輸入框：觀察 onStartInputView 切 AUTO 的 commit 路徑（R7）。

---

## 9. 流程狀態聲明

- 本輪僅 **盤點**，未改 `.kt`。
- 未宣告任何 `READY` / `COMPLETED`。
- 未預排 `S10-2+` 的順序；R1-R8 皆列為**候選**，最終挑哪一條進 S10-2 由 `CodeX A` 裁決。
- 若 `CodeX A` 需要使用者親測縮窄到某一情境，CCB 可另外準備最小 smoke 步驟。

## 10. 回交格式（依 AI_PROTOCOL）

### A. 變更摘要
- 新增 `docs/SPRINT10_0_CCB_AUTO_MODE_PRECHECK_2026-04-18.md` 一檔。
- 僅唯讀分析，未改任何 `.kt`。

### B. 風險
- 使用者原始回報 `自動模式輸入法仍有 bug` 描述極簡，實際重現情境可能不在 R1-R8 列舉範圍內；若 `CodeX A` 認為需要更窄的定位，建議轉為請使用者提供具體鍵序或錄影。
- R2（候選重複 / source 色掉）需要 `InputUiSync.renderCandidates` 實際行為對照；本輪未展開。

### C. 判定建議
- 僅建議：`S10-0-CCB = READY`（盤點完成，file scope 與路徑已明示）。
- 下一門 `S10-2` 主題候選由 `CodeX A` 從 R1-R8 中挑選；CCB 不自行指定。

### D. 下一步建議
- 等 `CodeX A` 收齊三份 `S10-0` 前置文件後統一裁決。
- 若 `CodeX A` 選定 R1 / R4 / R5 / R6 → 預期 S10-2 file scope 限 `InputRouter.kt` 單檔。
- 若 `CodeX A` 選定 R2 / R3 / R7 → 預期 S10-2 可能跨 `InputRouter.kt` + `service/*.kt`，需要先決定 owner。

### E. `CodeX A` 審核需求
- 需要 `CodeX A` 依 Sprint 10 gate 裁決：
  1. 本輪 `S10-0-CCB-AUTO-MODE-PRECHECK = READY / NOT READY`。
  2. S10-2 target 從 R1-R8 中選哪一條（或需再縮窄）。
  3. 是否授權 CCB 下輪進 S10-2 實作。

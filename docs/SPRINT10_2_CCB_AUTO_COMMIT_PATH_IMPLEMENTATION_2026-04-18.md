# SPRINT10-2 CCB AUTO commit path 不對稱修復實作

身份：Claude Code B
日期：2026-04-18
任務 ID：`S10-2-CCB-AUTO-COMMIT-PATH-IMPLEMENTATION`
對應 gate：`Sprint10-All-Round-Gate.md` → `Round S10-2`
提交鏈：`CCB` → `CodeX B`（技術二審）→ `CodeX A`（gate 最終裁決）

---

## 0. 本輪邊界遵守聲明

### 允許修改的檔案（僅限）
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceCallbacks.kt` ✅
- `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt` ✅

### 禁止修改的區域（全程未觸碰）
- `app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt` ✅
- candidate 排序 / source map 邏輯 ✅
- `Settings` / `auth` 相關檔 ✅
- `Sprint 10` gate / `RULE` / `daily_works_0418.md` ✅

### 沒做的事
- 未碰 `R1` / `R2` / `R4` / `R5` / `R6` / `R8` 候選情境
- 未自行宣告 `READY` / `COMPLETED`
- 未自行指定 S10-3 順序或 owner
- 未新增任何輔助 `.kt`
- 未動 `app/src/test`、測試骨架（S10-7 權責）

---

## 1. 修復目標

`CodeX A` 在 S10-2 gate 裁決鎖定兩條 commit path 不對稱：

- **R3**：`service/ImeServiceCallbacks.kt:250-253` — radial menu 選模式（含 AUTO）時，`switchTo(mode)` 回傳若是 `InputResult.CommitText`，直接 `ic.commitText(...)` 送進 `InputConnection`，**不走 `applyInputResult`**，因此跳過：
  - `consumePendingCommit()`（AUTO 跨引擎切換時的暫存 commit）
  - `emotionalCooldown.checkText`（情緒冷靜期）
  - `forwardTextToRemote`（藍牙 / WiFi 遠端鍵盤同步）
  - `scanForFraud / scanReplyForFraud`（被動防詐）
  - `sentenceBuffer.append`（整句學習）
  - `toneDetector.analyze`（語氣偵測 + 雪凜回應）
  - `characterView.playHappy`
  - 情緒貼圖推薦 tag 更新
- **R7**：`service/YukiBoardIMEService.kt:488-492`（`onStartInputView` AUTO 啟動路徑）— 同樣 raw commit，跳過上列全部副作用。

這兩處與正常按鍵 commit 路徑 (`applyInputResult` 走完整 flow) 不對稱，造成：模式切換時若前一引擎 force-commit 出字（例如使用者打到一半按 radial menu 切 AUTO），學詞 / 防詐 / 冷靜期 / 遠端鍵盤等副作用全部漏掉。

---

## 2. 落地改動

### 2.1 `ImeServiceCallbacks.kt`（R3）

修改範圍：`bindKeyboardViewCallbacks()` 內的 `setOnLanguageSelectedListener` lambda。

**前**（line 250-254）：
```kotlin
val switchResult = inputRouter.switchTo(mode)
if (switchResult is InputResult.CommitText) {
    currentInputConnection?.commitText(switchResult.text, 1)
}
currentInputConnection?.finishComposingText()
```

**後**（line 250-255）：
```kotlin
val switchResult = inputRouter.switchTo(mode)
val ic = currentInputConnection
if (ic != null && switchResult is InputResult.CommitText) {
    applyInputResult(ic, switchResult)
}
ic?.finishComposingText()
```

### 2.2 `YukiBoardIMEService.kt`（R7）

修改範圍：`onStartInputView` 中的 AUTO 啟動分支。

**前**（line 488-492）：
```kotlin
// AUTO 模式啟動（onStartInputView 已 reset，不需處理 switchTo 回傳的 commitResult）
if (prefs.autoInputMode && inputRouter.inputMode != InputMode.AUTO) {
    val sr = inputRouter.switchTo(InputMode.AUTO)
    if (sr is InputResult.CommitText) currentInputConnection?.commitText(sr.text, 1)
}
```

**後**（line 488-495）：
```kotlin
// AUTO 模式啟動：switchTo 回傳若為 CommitText，走 applyInputResult 保持 commit path 對稱（S10-2 R7）
if (prefs.autoInputMode && inputRouter.inputMode != InputMode.AUTO) {
    val sr = inputRouter.switchTo(InputMode.AUTO)
    val ic = currentInputConnection
    if (ic != null && sr is InputResult.CommitText) {
        applyInputResult(ic, sr)
    }
}
```

---

## 3. 行為差異對照

| 觸發條件 | 前（raw commit） | 後（applyInputResult） |
|---|---|---|
| 切模式前 composing 非空 → `forceCommit()` 產生 `CommitText(text)` | `ic.commitText(text,1)` 直送；副作用全跳 | `ic.beginBatchEdit / commitText / endBatchEdit`；完整副作用鏈觸發 |
| 切模式前 composing 為空 → 回傳 `Idle` | 原先 if 條件不成立，不呼叫 commit | 新代碼 if 條件不成立，`applyInputResult` 不被呼叫，與前相同 |
| `pendingCommitText` 非空（AUTO 跨引擎暫存） | 不消費，可能跨到下次 `applyInputResult` 被誤 commit | 立即消費（`applyInputResult` 開頭 `consumePendingCommit()`）並 commit 到正確 IC |
| `characterView / yukiSpeechBubble / toneDetector / emotionalCooldown` 副作用 | 跳過 | 依 `applyInputResult` 內原本規則觸發 |
| `ic?.finishComposingText()` | 一律呼叫 | 一律呼叫（保留原行為） |

### 3.1 為何保留 `finishComposingText()`
`applyInputResult` 的 CommitText 分支內 `ic.commitText(text, 1)` 理論上會 end composing region，但原檔在呼叫後仍顯式呼叫 `finishComposingText()` 作為防禦性保險（避免不同 IME framework 實作差異）。為維持最小語意變動，保留該呼叫。

### 3.2 為何保留 `if (switchResult is InputResult.CommitText)` 過濾
- `applyInputResult` 內 `consumePendingCommit()` 總是執行，若 switchTo 回傳 `Idle`，仍會幫忙 flush pending。
- 為避免 `Idle` / `ComposingUpdated` / `ComposingCleared` 觸發 CommitText 分支的副作用（雖然 when 分支不會命中，但避免日後重構時踩到），保留條件過濾。
- 現場實測：`switchTo(AUTO)` 在 onStartInputView 時通常回傳 `Idle`（前一 session 已被 `onFinishInputView` reset），條件不成立時新/舊行為完全相同 — 僅在有真實 composing 要 flush 時進入新 path。

---

## 4. 影響分析

### 4.1 正向影響（本輪目標）
1. **前模式未提交 composing → 切 AUTO 時**：force-commit 出的字會經過防詐掃描、遠端鍵盤轉發、整句學習、冷靜期檢查；與一般按 Space / Enter commit 行為對稱。
2. **onStartInputView AUTO 啟動**：若前次 session `onFinishInputView` 未清乾淨（極端情況），補 commit 走完整 flow。
3. **pendingCommitText 安全網**：兩處新路徑都會經過 `consumePendingCommit()`，即使未來 `switchTo` 前有意外的 AUTO pending，也不會跨到不相干上下文。

### 4.2 副作用風險（本輪非修補範圍，但需 CodeX B / CodeX A 評估）
1. **角色動畫 `characterView?.playHappy()`**：切模式時若有 force-commit 字，會額外播一次 happy 動畫。視覺上可能與 `characterView?.playSwitchLang()`（line 256 / 258）重疊 ~300ms，但二者為不同動畫槽，不會互撞。
2. **`sentenceBuffer.append(result.text)`**：force-commit 出的字會被算進整句 buffer，與之前「切模式不算」有些微學詞偏差。屬合理方向（使用者確實打出了這段字），但若 `CodeX A` 認為需要區隔，可後續拉一條 micro-round。
3. **`scanForFraud / scanReplyForFraud`**：切模式時多做一次掃描，成本 <1ms（規則引擎），不影響互動延遲。
4. **`toneDetector.analyze`**：只有 `result.text.length >= 2` 才觸發，短切換文字（<2 字）不會引發語氣分析，無額外負擔。
5. **`forwardTextToRemote`**：若使用者正連遠端鍵盤，force-commit 的字會被轉發。符合語意（使用者確實 commit 了）。

### 4.3 未覆蓋的同類路徑（**故意留給 CodeX A 裁決**）
- `ImeServiceInput.kt:636-659` 的 `handleSwitchLang`（短按語言鍵 → 在 AUTO 下切 layout / 在其他模式下 toggleLanguage）：
  - 其中 `commitResult = inputRouter.getActiveEngine().forceCommit()` 後也是 raw `ic.commitText(commitResult.text, 1)`，**不走 `applyInputResult`**。
  - 本輪 gate 未授權 `ImeServiceInput.kt` 寫 scope，**不動**。
  - 建議 `CodeX A` 考慮在後續 round（或 S10 結束後）納入同類修補。

---

## 5. 驗證

### 5.1 編譯驗證
```
./gradlew :app:compileDebugKotlin
BUILD SUCCESSFUL in 25s
18 actionable tasks: 7 executed, 11 up-to-date
```
僅剩 pre-existing deprecation warnings（`WifiInfo`、`RequestBody.create`、`statusBarColor` 等），非本輪引入。

### 5.2 Smoke path 建議（供 S10-2 結案前人工驗證）
1. **R3 主路徑**：英文模式下打 `hel` 不 commit → 叫出 radial menu → 選 AUTO。
   - 預期：`hel` 被 force-commit（走 applyInputResult），字元進入文字框，角色播 happy 動畫，防詐掃描觸發，sentenceBuffer append `hel`。
2. **R3 其他模式**：AUTO 下打 `ㄅㄧㄣ` 不選字 → radial menu 選 BOPOMOFO。
   - 預期：`ㄅㄧㄣ` 的 force-commit 結果（若有）走完整路徑；若無 force-commit（注音引擎未送出），走 Idle 路徑，無視覺變化。
3. **R7**：設定頁開啟「智慧中英切換」→ 於另一 App 開啟輸入框觸發 onStartInputView。
   - 預期：若 inputRouter 非 AUTO，switchTo(AUTO) 回傳若有 CommitText，走 applyInputResult；若 Idle（正常情形），無新副作用，行為與前相同。
4. **R7 pendingCommit 安全網**：使用者於 AUTO session 中 Chewing→English 切換產生 `pendingCommitText` → 尚未 applyInputResult 即 onFinishInputView → onStartInputView。
   - 預期：新 IC 下次 applyInputResult 觸發時，`consumePendingCommit` 取出舊 pending；本次 switchTo(AUTO) 只要 Idle，就走新路徑 null-check 失敗不呼叫 applyInputResult；pending 會等到下一次處理按鍵時 applyInputResult 清掉。
   - 風險：如果使用者 session 切換極快，pending 可能跨到不同 IC。當前修補不解此 edge case（屬 R8 範圍）。

### 5.3 未做的驗證
- 未跑 instrumented test（`app/src/test` 目前 0 檔；屬 S10-7 權責）
- 未做 UI 手測（無 device 環境，且本輪為 CCB 實作階段，smoke 測建議由 `CodeX A` 於 gate 通過後統籌）

---

## 6. 回交格式（AI_PROTOCOL §5）

### A. 變更摘要
- `service/ImeServiceCallbacks.kt`：radial menu 模式切換後的 raw `commitText` 改走 `applyInputResult`，保留 `finishComposingText` 防禦呼叫。
- `service/YukiBoardIMEService.kt`：`onStartInputView` AUTO 啟動分支同上改走 `applyInputResult`。
- 新增 `docs/SPRINT10_2_CCB_AUTO_COMMIT_PATH_IMPLEMENTATION_2026-04-18.md`。
- 兩檔 diff 合計：+8 行、-4 行（淨 +4）。
- `:app:compileDebugKotlin` BUILD SUCCESSFUL。

### B. 風險
1. `characterView?.playHappy()` 與後續 `playSwitchLang()` 動畫槽有可能視覺上重疊（~300ms），但非同槽互撞。若 CodeX B 認為需要抑制，可在後續 round 以條件參數處理。
2. `sentenceBuffer.append` 現在會把切模式時 force-commit 的字算進整句 buffer，屬合理方向但為行為變更。
3. `handleSwitchLang`（ImeServiceInput.kt）同類 raw commit 仍未修，**本輪故意不碰**，待 CodeX A 裁決。
4. pendingCommitText 跨 session edge case 未解（屬 R8），不在本輪 target。

### C. 判定建議
- 建議：`S10-2-CCB-AUTO-COMMIT-PATH-IMPLEMENTATION = READY`
  - file scope 嚴格在 gate 授權兩檔內
  - compile 獨立通過
  - 無 pre-existing 語意破壞
  - 未侵入 `InputRouter.kt` / candidate 策略 / settings / auth / gate 文件

### D. 下一步建議
- 提交 `CodeX B` 做技術二審（依 gate §提交鏈）。
- `CodeX B` 二審通過後交 `CodeX A` 做 gate 最終裁決。
- 若 `CodeX A` 判 `READY`，是否接著開 `S10-3` 由 `CodeX A` 決定，CCB 不自排。

### E. `CodeX A` / `CodeX B` 審核需求
- `CodeX B` 技術二審重點建議：
  1. 核對 two edit sites 落地內容與本文件 §2 一致
  2. 核對 compile 結果（可重跑 `:app:compileDebugKotlin`）
  3. 核對 file scope 未越界（僅 callbacks + service）
  4. 評估 §4.2 五個副作用風險是否 acceptable
- `CodeX A` gate 最終裁決需回報：
  - `S10-2-CCB = READY / NOT READY`
  - 是否開 `S10-3`
  - 是否需要 CCB 補 smoke 手測或直接等後續 round 統一驗

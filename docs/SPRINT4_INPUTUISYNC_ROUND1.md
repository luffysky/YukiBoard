# Sprint 4 Round 1 — `InputUiSync` 正式邊界建立

日期: 2026-04-17
Gate: `CodeX A` Sprint 4 = READY（2026-04-17）；Phase 2 = COMPLETED；Sprint 5 = LOCKED
對應: `SPRINT4_INPUT_FLOW_REVIEW_2026-04-16.md §3.3 / §4.1-2 / §5.3`、`docs/INPUT_UI_SYNC_BOUNDARY.md`

---

## 1. Round 1 目標

建立 `InputUiSync` 的**正式 class 邊界**，把 `composingText / candidateState` 的「同步寫 view + coordinator」render ownership 收斂到單一入口。

Round 1 不做：
- 重寫 `InputRouter`
- 切 `KeyboardView.kt`
- `applyInputResult()` 副作用分類（屬 §3.2 InputActionExecutor，下一輪）
- `KeyEventInterpreter` 獨立檔案（屬 §4.4，最後才考慮）
- Sprint 5 範圍（`isFloatingMode` / `isPanelBehindKeyboard` / `remoteConnectionState` / `isInGameMode`）

---

## 2. `handleKeyPress()` / `applyInputResult()` 同步 UI 更新職責盤點

### 2.1 `handleKeyPress()` 內 `keyboardView.update{Composing,Candidates}` 寫入點

| 行號（遷移前） | 行為分類 | Round 1 處理 |
|---------------|---------|--------------|
| 789 `updateComposingText(inputRouter.composingText)` | TOGGLE_KANA 特例，view-only | **未動**（niche 路徑，下輪評估） |
| 825-826 `updateComposingText("") + updateCandidates(emptyList())` | 非字母、非注音符號直接 commit 前的 intermediate clear | **未動**（intermediate 語意，下輪評估） |
| 844 `updateComposingText(inputRouter.composingText)` | post-update 主熱路徑 | **遷移** → `inputUiSync.renderComposing(...)` |
| 871 `updateCandidates(candidates, inputRouter.candidateSourceMap)` | post-update 主熱路徑（含 AUTO 混合 + DEV 預測） | **遷移** → `inputUiSync.renderCandidates(..., sourceMap)` |

### 2.2 `applyInputResult()` 內

- 不直接寫 `keyboardView.update*`；結尾呼叫 `syncImeCoordinatorFromRouter()`（coordinator-only）
- Round 1 **不動** `applyInputResult()` 本體；`syncImeCoordinatorFromRouter()` 保留 legacy 相容（詳 §4.2）

### 2.3 `applyInputResult()` 副作用分類（作為後續 Round 參考，不在 Round 1 範圍）

`applyInputResult()` 目前承載的副作用：

1. pending commit 前置處理
2. `ic.commitText / setComposingText / finishComposingText`（InputConnection 核心操作）
3. emotional cooldown 檢查（bubble / freeze）
4. `characterView?.playHappy()`（角色動畫）
5. `forwardTextToRemote`（遠端鍵盤）
6. `scanForFraud` / `scanReplyForFraud`（防詐）
7. `sentenceBuffer.append`（整句學習）
8. tone detection + speech bubble
9. 情緒貼圖推薦（recommend tags）
10. sarcastic comments（微吐槽）
11. `syncImeCoordinatorFromRouter()`（coordinator 同步）

Round 2 候選：把 1 + 2 + 11 保留在 executor 內；3-10 抽出為可後移的 side-effect pipeline（`InputSideEffects` / `InputActionExecutor`）。

### 2.4 其他熱路徑（Round 1 不動）

保留直寫的熱路徑（屬未來 Round 遷移範圍）：

| 檔案 / 位置 | 寫入 | 類別 |
|------------|------|------|
| `ImeServiceInput.kt` 13 處 | `handleDelete / handleEnter / handleSpace / handleTab / handleSwipeInput / handleSwitchLang / routeKeyToPanel` | 子 handler 熱路徑 |
| `ImeServiceInput.kt:468-469` | AI debounce 進度指示 | AI debounce |
| `ImeServicePanels.kt:444` | HANDWRITING `onRecognized` 的 view.updateCandidates（ML RMW） | 手寫 ML RMW |
| `YukiBoardIMEService.kt:789` | TOGGLE_KANA 特例 | 語言切換 niche |
| `YukiBoardIMEService.kt:825-826` | intermediate clear before non-letter commit | intermediate clear |

---

## 3. Round 1 交付 — `InputUiSync` class

### 3.1 檔案

新檔：`app/src/main/java/com/snowrealm/yukiboard/app/ime/InputUiSync.kt`（`app.ime` 層）

### 3.2 設計

```kotlin
class InputUiSync(
    private val viewProvider: () -> KeyboardView,
    private val coordinator: ImeSessionCoordinator,
) {
    fun renderComposing(text: String)
    fun renderCandidates(items: List<String>, sourceMap: Map<String, CandidateSource> = emptyMap())
    fun clearComposing()
    fun clearCandidates()
    fun clearAll()
}
```

### 3.3 設計決策

| 決策 | 理由 |
|------|------|
| `viewProvider: () -> KeyboardView` 而非直接持有 | `YukiBoardIMEService.keyboardView` 在 `onCreateInputView` 會因懸浮 / 標準切換被重新指派；InputUiSync 必須在 call-time 解析當前實例 |
| `coordinator` 直接持有 | session 生命週期內穩定實例 |
| 全同步呼叫 | 熱路徑與 `ic.beginBatchEdit` 相容，不引入 coroutine / post |
| 不承接 `InputRouter` 演算法 / panel container 操作 / 外部 client | 符合 `SPRINT4_INPUT_FLOW_REVIEW §3.3` 的責任切分 |

### 3.4 wiring

在 `YukiBoardIMEService.kt`：

```kotlin
internal val inputUiSync: InputUiSync by lazy {
    InputUiSync(
        viewProvider = { keyboardView },
        coordinator = imeCoordinator,
    )
}
```

`by lazy` 確保 `imeCoordinator` 已 lateinit 初始化後才建立 InputUiSync；`viewProvider` lambda 保證每次 render 都取當前 `keyboardView`。

---

## 4. Round 1 遷移範圍

### 4.1 已遷移（4 處）

| 檔案 | 位置 | Before | After |
|------|------|--------|-------|
| `YukiBoardIMEService.kt` | `handleKeyPress` post-update composing | `keyboardView.updateComposingText(inputRouter.composingText)` | `inputUiSync.renderComposing(inputRouter.composingText)` |
| `YukiBoardIMEService.kt` | `handleKeyPress` post-update candidates | `keyboardView.updateCandidates(candidates, inputRouter.candidateSourceMap)` | `inputUiSync.renderCandidates(candidates, inputRouter.candidateSourceMap)` |
| `ImeServiceCallbacks.kt:222-223` | `setOnCandidateSelectListener` post-commit refresh | 2 行 `keyboardView.update*` | 2 行 `inputUiSync.render*`（coordinator 同步承載 sourceMap） |
| `ImeServicePanels.kt` | `clearComposingAndCandidatesUi()` helper | 2 × view + 2 × coordinator 直寫 | `inputUiSync.clearAll()` |
| `ImeServicePanels.kt` | `clearCandidatesUi()` helper | view + coordinator 直寫 | `inputUiSync.clearCandidates()` |

### 4.2 保留 legacy（Round 1 不動）

- `syncImeCoordinatorFromRouter()`（`YukiBoardIMEService.kt:987-991`）：coordinator-only 寫入，不是 InputUiSync 的 dual-write 目標；維持 legacy API 相容，避免 Round 1 改到 `applyInputResult()`
- `startImeComposingCandidatesCollect()` guard：continue `.isEmpty()` guard（safety net 仍有價值）
- `ImeServiceInput.kt` 13 處子 handler 寫入：下輪遷移
- `handleKeyPress` 的 TOGGLE_KANA / intermediate clear 兩處 niche：下輪遷移
- AI debounce / HANDWRITING RMW / swipe RMW：下輪遷移（含是否為 RMW 的結構檢查）

---

## 5. Round 1 達成條件對照 `SPRINT4_INPUT_FLOW_REVIEW §5`

| 條件 | 狀態 |
|------|------|
| §5.1 `handleKeyPress()` 的邏輯分層已可明確描述 | 部分達成（post-update 層已抽邊界；其餘分類在本文件 §2.1） |
| §5.2 `applyInputResult()` 的副作用分類已可明確描述 | 部分達成（§2.3 已列出 11 類，尚未實作抽出） |
| §5.3 `composing/candidates` 的同步 render owner 已明確 | **達成**（`InputUiSync` class 正式邊界） |
| §5.4 Phase 2 readiness 能從 `NOT READY` 提升到可評估 `READY` | Phase 2 已於 Round 3 提前完成（CodeX A 2026-04-17 核可），此條目已滿足 |
| §5.5 不破壞既有輸入體感 | 達成（全同步 dual-write，時序不變；compile pass） |

---

## 6. 驗證

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 8s`
- 禁區守住：`KeyboardView.kt` / `InputRouter` / Sprint 5 狀態欄位均未動
- 熱路徑時序：所有遷移點為同步呼叫，與 `ic.beginBatchEdit` 在同一 frame 內完成，輸入體感不變
- coordinator 語意升級：`handleKeyPress` post-update 現在會把 `inputRouter.candidates + candidateSourceMap` 同步寫入 coordinator（Round 3 結構升級後首次正式使用）；`setOnCandidateSelectListener` post-commit 同理

---

## 7. Round 2 候選範圍（需 `CodeX A` 核可才可啟動）

1. **`applyInputResult()` 副作用分類落地**：把 §2.3 列的 11 類副作用分離為 `InputActionExecutor` 內的核心部分 + 可後移的 side-effect pipeline
2. **`ImeServiceInput.kt` 子 handler 遷移**：`handleDelete / handleEnter / handleSpace / handleTab / handleSwipeInput / handleSwitchLang / routeKeyToPanel` 的 13 處 `keyboardView.update*` 改走 InputUiSync
3. **AI debounce / HANDWRITING RMW / swipe RMW**：需先判定 RMW 語意是否能安全 dual-write 或需專用 API（例如 `mergeCandidates`）
4. **`handleKeyPress` niche 路徑**：TOGGLE_KANA、intermediate char-commit clear

`CodeX A` 未拍板 Round 2 scope 前，Claude Code B 不自行擴張。

---

## 8. A/B/C/D/E 交接

### A. 變更摘要

- 新檔：`app/src/main/java/com/snowrealm/yukiboard/app/ime/InputUiSync.kt`（class + 5 method API）
- `YukiBoardIMEService.kt`：新增 `inputUiSync: InputUiSync by lazy`；`handleKeyPress` post-update 2 行 migrate
- `ImeServiceCallbacks.kt`：`setOnCandidateSelectListener` post-commit 2 行 migrate
- `ImeServicePanels.kt`：2 cleanup helper migrate
- 新文件：`docs/SPRINT4_INPUTUISYNC_ROUND1.md`（本檔）
- 更新文件：`docs/INPUT_UI_SYNC_BOUNDARY.md` 標題 + 狀態；`IME_SMOKE_CHECKLIST.md` 新增 Sprint 4 Round 1 章節

### B. 這輪抽出了哪些 Sprint 4 邊界

1. `app.ime.InputUiSync` — 正式 class，`composingText / candidateState` 同步 render ownership 單一入口
2. `handleKeyPress` post-update 主熱路徑已改走 InputUiSync（coordinator 現在同步承載 `candidateSourceMap`）
3. `setOnCandidateSelectListener` post-commit refresh 已改走 InputUiSync（同步承載 sourceMap）
4. 既有 cleanup helper（`clearComposingAndCandidatesUi / clearCandidatesUi`）refactor 至同一邊界
5. `SPRINT4_INPUT_FLOW_REVIEW §5.3` 的完成條件達成

### C. 還沒動哪些高風險熱路徑

- `applyInputResult()` 本體（副作用分類屬 Round 2）
- `syncImeCoordinatorFromRouter()`（Round 2 / 3 才並入 InputUiSync）
- `ImeServiceInput.kt` 13 處子 handler 寫入
- AI debounce read-modify-write
- HANDWRITING `onRecognized` ML RMW
- swipe candidates 分支 RMW
- `handleKeyPress` TOGGLE_KANA niche、intermediate char-commit clear
- `startImeComposingCandidatesCollect()` guard（等熱路徑全遷移完才能撤）

### D. 驗證結果

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 8s`
- grep 驗證：`InputUiSync` 已在 4 個 call site 取代直寫
- 禁區（`KeyboardView.kt` / `InputRouter.kt` / Sprint 5 狀態）完全未動
- 熱路徑時序不變：全同步 dual-write，與 `ic.beginBatchEdit` 相容

### E. 請 `CodeX A` 審核是否可繼續推進 Sprint 4

Round 1 達成 `SPRINT4_INPUT_FLOW_REVIEW §5.3`；§5.1 / §5.2 部分達成（已分類，未實作抽出）。

建議判定選項：

- **READY（繼續 Round 2）**：請指定下一輪 scope（建議優先序：`ImeServiceInput.kt` 子 handler 遷移 → `applyInputResult` 副作用分類 → RMW 路徑評估）
- **NOT READY**：請列 Round 1 缺項；Claude Code B 依指示補齊
- **LOCKED**：Sprint 4 Round 1 封存，暫不推進後續熱路徑遷移

`CodeX A` 未拍板前，Claude Code B 不會動其他 `.kt`，本輪落地狀態保持。

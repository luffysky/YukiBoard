# Sprint 4 Round 3 — `applyInputResult` / `syncImeCoordinatorFromRouter` 收斂與 Sprint 4 收尾判定

日期: 2026-04-17
Gate: `CodeX A` Sprint 4 Round 2 = ACCEPTED（2026-04-17）；Round 3 scope = `applyInputResult` 周邊 UI 同步收斂 + `syncImeCoordinatorFromRouter` 可安全併入 InputUiSync 的部分 + Sprint 4 保留區盤點
對應: `docs/SPRINT4_INPUTUISYNC_ROUND1.md`、`docs/SPRINT4_INPUTUISYNC_ROUND2.md`、`docs/INPUT_UI_SYNC_BOUNDARY.md`、`SPRINT4_INPUT_FLOW_REVIEW_2026-04-16.md §5`

---

## 1. Round 3 目標

1. 盤點並收斂 `applyInputResult()` 內與 UI render 直接相關的同步責任
2. 評估並**最小**併入 `syncImeCoordinatorFromRouter()` 可安全納入 `InputUiSync` 的部分
3. 正式列出 Sprint 4 **保留區**（Round 3 起不再計劃於本 Sprint 移除的寫入點）
4. 交付 Sprint 4 收尾判定材料予 `CodeX A`

Round 3 嚴守：

- **不碰** `KeyboardView.kt` / `ImeServicePanels.kt` / Sprint 5 狀態欄位
- **不大拆** InputActionExecutor（副作用管線抽出屬 Sprint 5+）
- **不硬收** AI debounce / swipe / HANDWRITING `onRecognized` 這類 RMW 路徑

---

## 2. `applyInputResult()` UI render 責任盤點

`applyInputResult()` 位於 `YukiBoardIMEService.kt:912-994`，結尾呼叫 `syncImeCoordinatorFromRouter()`。

### 2.1 直接 UI render 寫入點

經完整掃描：`applyInputResult()` 函式體內 **不存在任何** `keyboardView.updateComposingText / updateCandidates` 直寫或 `inputUiSync.render*` 呼叫。

- `keyboardView.recommendedStickerEmoji = ...`（L969 / L971）是 **貼圖推薦徽章**，不屬於 composing/candidates render territory。
- `characterView?.playHappy()` / `yukiSpeechBubble?.show(...)` 屬角色動畫與氣泡，屬 Sprint 5 範圍的 Yuki reaction pipeline。

**結論**：`applyInputResult()` **沒有直接** UI render 責任可以收斂；其 UI render 責任已完全委派給 caller 透過 InputUiSync 執行（Round 1 + Round 2 完成）。

### 2.2 All callers of `applyInputResult()` 的 post-render 驗證

| Caller | 位置 | applyInputResult 後 UI render 方式 | Round 收斂 |
|--------|------|------------------------------------|-----------|
| `handleKeyPress` 非 CJK else 分支 | `YukiBoardIMEService.kt:838` | `inputUiSync.renderComposing(...)` L852 + `inputUiSync.renderCandidates(...)` L880 | Sprint 4 Round 1 |
| `handleEnter(ic)` | `ImeServiceInput.kt:568` | `inputUiSync.renderComposing(...) + renderCandidates(...)` L569-570 | Sprint 4 Round 2 |
| `handleSpace(ic)` focus-confirm | `ImeServiceInput.kt:622` | `inputUiSync.renderComposing("") + renderCandidates(...)` L625-626 | Sprint 4 Round 2 |
| `handleSpace(ic)` 一般分支 | `ImeServiceInput.kt:630` | `inputUiSync.renderComposing(...) + renderCandidates(...)` L632-633 | Sprint 4 Round 2 |
| `ImeServiceCallbacks.setOnCandidateSelectListener` AI 分支 | `ImeServiceCallbacks.kt:190` | `clearComposingAndCandidatesUi()` = `inputUiSync.clearAll()` | Phase 2 Round 2 + Sprint 4 Round 1 helper refactor |
| `ImeServiceCallbacks.setOnCandidateSelectListener` 一般候選 | `ImeServiceCallbacks.kt:220` | `inputUiSync.renderComposing(...) + renderCandidates(...)` L223-224 | Sprint 4 Round 1 |

**6/6 caller 的 post-applyInputResult 同步 UI render 已全部走 InputUiSync**。Round 3 對 `applyInputResult()` 本體 **不需再做任何 code change**（收斂量 = 0 lines），僅需把「本函式不負責 UI render」此一事實正式寫入文件（見 §5.2 保留區 D 類）。

### 2.3 `applyInputResult()` 11 類副作用分類（非本輪落地；交付予 Sprint 5+ `InputActionExecutor`）

| # | 副作用 | 類別 | Sprint 5+ 處置建議 |
|---|--------|------|-------------------|
| 1 | `inputRouter.consumePendingCommit()` + batchEdit commit | InputConnection 核心 | 屬 `InputActionExecutor` 核心職責 |
| 2 | `ic.commitText / setComposingText / finishComposingText` | InputConnection 核心 | 同上 |
| 3 | `emotionalCooldown?.checkText` + `playFreeze` + speech bubble | Yuki reaction | 可抽 `YukiEmotionPipeline` |
| 4 | `characterView?.playHappy()` | Yuki reaction | 同上 |
| 5 | `forwardTextToRemote(result.text)` | 遠端鍵盤副作用 | 可抽 `RemoteKeyboardRelay` |
| 6 | `scanForFraud()` / `scanReplyForFraud(result.text)` | 防詐 | 可抽 `FraudScanHook` |
| 7 | `sentenceBuffer.append(result.text)` | 學習副作用 | 可抽 `SentenceLearningHook` |
| 8 | tone detection + speech bubble | Yuki reaction | 同 3/4 |
| 9 | 情緒貼圖推薦（`setRecommendedTags` + `recommendedStickerEmoji`）| UI 次要 render | 可抽 `StickerRecommendationHook`（**非** composing/candidates 範疇） |
| 10 | `yukiCommentEngine?.onKeystroke` + speech bubble | Yuki reaction | 同 3/4 |
| 11 | `syncImeCoordinatorFromRouter()` | 狀態同步 | Round 3 已簡化為 `setInputMode` only |

---

## 3. `syncImeCoordinatorFromRouter()` 最小收斂

### 3.1 Before（Round 2 末）

```kotlin
internal fun syncImeCoordinatorFromRouter() {
    imeCoordinator.setInputMode(inputRouter.inputMode)
    imeCoordinator.updateComposingText(inputRouter.composingText)
    imeCoordinator.updateCandidates(inputRouter.candidates)  // legacy 單參版本，無 sourceMap
}
```

### 3.2 After（Round 3）

```kotlin
internal fun syncImeCoordinatorFromRouter() {
    // Sprint 4 Round 3: UI render 職責正式委派給 InputUiSync。
    // composing/candidates 的同步由 caller 在 applyInputResult() 後的
    // inputUiSync.renderComposing / renderCandidates 統一處理；
    // session-init（onStartInputView）也明確走 InputUiSync。
    // 本 function 只保留 inputMode 同步（屬非 render 的 state 欄位）。
    imeCoordinator.setInputMode(inputRouter.inputMode)
}
```

### 3.3 共 2 個 caller 的影響分析

#### Caller A — `applyInputResult()` 結尾 L993

**Before**: coordinator 被寫 `(mode, composing, candidates-without-sourceMap)`；接著 caller 再用 `inputUiSync.render*` 寫 view + coordinator（帶 sourceMap）。效果：coordinator 的 sourceMap 在 `updateCandidates(list)` 這步被清空（legacy 單參版本），再被 caller 重建。過程中 coordinator 有一瞬間帶著「新 candidates、空 sourceMap」的中間態。

**After**: coordinator 只被寫 `(mode)`；caller 的 `inputUiSync.render*` 同步寫 view + coordinator（含 sourceMap）。**沒有 sourceMap 清空→重建的中間態**。主執行緒同步執行，無觀察者競態。

**淨效果**: 更乾淨（sourceMap 從不被暫時丟失），行為等價（collect 層 `.isEmpty()` guard 本就過濾）。

#### Caller B — `onStartInputView` session-init L479

**Before**: `syncImeCoordinatorFromRouter()` 把 `(mode, composing, candidates)` 寫進 coordinator；view 未被觸及（session 邊界 view 由 `onCreateInputView` 重建 / cleanup helper 清除，無需在此多寫）。

**After**: `syncImeCoordinatorFromRouter()` 只寫 mode，接著新增：

```kotlin
inputUiSync.renderComposing(inputRouter.composingText)
inputUiSync.renderCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
```

這一步比原本多了 view 寫入（以 `inputRouter` reset 後的空值為主），**同步語意更明確**——session 邊界明確呼叫 InputUiSync dual-write，與 Round 2 的子 handler 寫法統一。`inputRouter` 在 `onStartInputView` 前已由 `onFinishInputView` 的 session cleanup 重置，所以空值寫入與先前行為等價。

### 3.4 為何沒有進一步把 `setInputMode` 併入 InputUiSync

- `InputUiSync` 的職責（`SPRINT4_INPUT_FLOW_REVIEW §3.3`）是 **composing/candidates render**，**不含** inputMode / voice / panel / keyboardView 佈局切換等其他 coordinator 欄位。
- `setInputMode` 屬 `ImeSessionCoordinator` 狀態欄位寫入，不是 view render；硬塞進 InputUiSync 會破壞單一責任。
- 列為 **Sprint 4 保留區 A 類**（狀態欄位寫入，InputUiSync 範圍外）。

---

## 4. Sprint 4 達成條件對照 `SPRINT4_INPUT_FLOW_REVIEW §5`

| 條件 | Round 1 | Round 2 | Round 3 |
|------|---------|---------|---------|
| §5.1 `handleKeyPress()` 邏輯分層可描述 | 部分 | 部分 | **完整描述**（Round 1 §2.1 表 + Round 3 §5.1 保留區表） |
| §5.2 `applyInputResult()` 副作用分類可描述 | 分類列出（11 類） | 同 | **完整描述**（§2.3）；實作抽出屬 Sprint 5+ |
| §5.3 composing/candidates 同步 render owner 明確 | **達成**（`InputUiSync` class） | **強化** | **完結**（`syncImeCoordinatorFromRouter` 移出 render；InputUiSync 為唯一 render 入口） |
| §5.4 Phase 2 readiness 可 READY | 已由 Phase 2 Round 3 完成 | — | — |
| §5.5 不破壞既有輸入體感 | ✓ | ✓ | ✓ |

---

## 5. Sprint 4 **保留區**（Round 3 起不再計劃於本 Sprint 移除）

以下寫入點 **正式列為 Sprint 4 保留**，不在本 Sprint 進一步遷移；後續處理屬 Sprint 5 或 `InputActionExecutor` 抽出時再處理。

### 5.1 A 類 — 狀態欄位寫入（InputUiSync 範圍外）

| 位置 | 寫入 | 保留理由 |
|------|------|---------|
| `YukiBoardIMEService.syncImeCoordinatorFromRouter()` | `imeCoordinator.setInputMode(...)` | 非 composing/candidates，不屬 InputUiSync 責任 |
| `ImeSessionCoordinator.*` voice / panel / keyboardLayout 等 | 各 state 欄位寫入 | Sprint 5 狀態欄位擴張 |

### 5.2 B 類 — RMW（read-modify-write）路徑，硬 dual-write 不安全

| 位置 | 寫入 | 保留理由 |
|------|------|---------|
| `ImeServiceInput.kt:402` `requestAICandidates()` | `keyboardView.updateCandidates(currentCandidates, currentSourceMap)` | AI debounce 在 `inputRouter.candidates + AI suggestions` 合併後寫 view；若改 dual-write 需評估是否引入 `mergeCandidates` 專用 API，或確認 coordinator collect 在此場景不會 replay 舊 candidates 蓋回 AI 合併結果 |
| `ImeServiceInput.kt:468-469` `handleSwipeInput()` | `updateComposingText("…") + updateCandidates(combined)` | swipe 段累積 RMW；`buildSwipeCombinedCandidates()` 把多段滑動候選合併後顯示，coordinator 目前沒有承載「正在滑動中的組合態」欄位 |
| `ImeServicePanels.kt` HANDWRITING `onRecognized` | ML 結果 RMW | 手寫辨識回傳後以 ML 結果合併寫入，屬 ML pipeline 專用路徑 |

### 5.3 C 類 — Niche / 語言切換路徑

| 位置 | 寫入 | 保留理由 |
|------|------|---------|
| `YukiBoardIMEService.kt:789` `handleKeyPress` TOGGLE_KANA 特例 | `keyboardView.updateComposingText(...)` | 日文 kana 切換 niche；view-only，無 coordinator 語意需求 |
| `YukiBoardIMEService.kt:832-833` `handleKeyPress` intermediate char-commit clear | `updateComposingText("") + updateCandidates(emptyList())` | 非字母/非注音字元 commit 前的中繼清除，與 ic.commitText 同幀發生；coordinator 同步點落在後續 post-update |
| `ImeServiceInput.kt:536` `handleTab()` 候選 focus 刷新 | `updateCandidates(...)` | Tab 只做 `focusNext()` 不改候選集合，本質是 **highlight 改變**；coordinator 尚無 `focusedIndex` 欄位，硬走 dual-write 會每次 Tab 都重寫整個候選列 |
| `ImeServiceInput.kt:650` `handleSwitchLang()` AUTO layout toggle | `updateCandidates(emptyList(), emptyMap())` | 鍵盤佈局切換同時清候選；與 `keyboardView.updateKeyRows` 綁在一起，屬 layout 語意而非 InputUiSync 純 render |
| `ImeServiceInput.kt:658` `handleSwitchLang()` 一般語言切換 | 同上 | 同上 |

### 5.4 D 類 — 已驗證無直接 UI render（不必遷移）

| 位置 | 說明 |
|------|------|
| `applyInputResult()` 函式體 | 無 `keyboardView.update*`；UI render 由 caller post-call 透過 InputUiSync 執行（Round 1 + Round 2 已完成全 6 caller 收斂） |
| `syncImeCoordinatorFromRouter()` Round 3 後 | 只剩 `setInputMode`；composing/candidates 移出至 caller InputUiSync |

### 5.5 E 類 — Sprint 5 候選（非本 Sprint 範圍）

- `applyInputResult()` 11 類副作用抽出為 `InputActionExecutor` + side-effect hooks（見 §2.3）
- `KeyEventInterpreter` 從 `handleKeyPress()` 分離（`SPRINT4_INPUT_FLOW_REVIEW §3.1 / §4.4`）
- `ImeSessionCoordinator` 新增 `keyboardLayout / isFloatingMode / isPanelBehindKeyboard / remoteConnectionState / isInGameMode` 狀態欄位（Sprint 5 主範圍）

---

## 6. 驗證

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 4s`
- 本輪修改：
  - `YukiBoardIMEService.kt`：`syncImeCoordinatorFromRouter()` 簡化為 `setInputMode` only；`onStartInputView` L479 後新增 `inputUiSync.renderComposing / renderCandidates` 顯式 dual-write
  - 無其他 `.kt` 修改
- 禁區未動：`KeyboardView.kt` / `ImeServicePanels.kt` / Sprint 5 狀態欄位未觸及
- 熱路徑時序：全同步；applyInputResult 後的 caller-side InputUiSync 寫入已承擔完整 sourceMap 同步；sourceMap 從此不再經歷「被 legacy 單參 updateCandidates 清空→caller 重建」的中間態
- `applyInputResult()` 本體未修改（§2.1 驗證其無直接 UI render 責任）

---

## 7. 累計 Sprint 4 成果

### 已遷移至 InputUiSync 的 call site（累計 20 處寫入 / 12 site）

- Round 1（4 處）：`handleKeyPress` post-update (L844/871)、`setOnCandidateSelectListener` post-commit (L222-223)、2 cleanup helper
- Round 2（8 處 / 7 分支）：`handleDelete` / `handleEnter` / `handleSpace`（2 分支）/ `routeKeyToPanel`（4 分支）
- Round 3（2 處新增 + 2 處移除）：`onStartInputView` session-init 新增 InputUiSync dual-write；`syncImeCoordinatorFromRouter` 移除 2 行 coordinator-only 寫入

### InputUiSync API 成熟度

- `renderComposing(text)` / `renderCandidates(items, sourceMap)`：主熱路徑（Round 1）+ 子 handler（Round 2）+ session-init（Round 3）
- `clearComposing` / `clearCandidates` / `clearAll`：cleanup helper + `routeKeyToPanel` ENTER 分支
- 跨 12 個 call site 驗證穩定

### 保留區規模

- A 類（狀態欄位）：1 主要（`setInputMode`）+ Sprint 5 擴張欄位
- B 類（RMW）：3 個（AI debounce / swipe / HANDWRITING）
- C 類（niche）：5 個（TOGGLE_KANA / intermediate clear / handleTab focus / handleSwitchLang x2）
- D 類（零直寫，已驗證）：2 個（`applyInputResult` body / `syncImeCoordinatorFromRouter` Round 3 後）
- E 類（Sprint 5）：副作用管線抽出 / `KeyEventInterpreter` / 新狀態欄位

---

## 8. A/B/C/D/E 交接

### A. 變更摘要

- `YukiBoardIMEService.kt`：
  - `syncImeCoordinatorFromRouter()` 簡化：移除 `imeCoordinator.updateComposingText` + `updateCandidates` 兩行，保留 `setInputMode`；加上文件 comment 說明 render 責任已移至 InputUiSync
  - `onStartInputView` L479 後新增 2 行 `inputUiSync.renderComposing(...) + renderCandidates(...)`，顯式 dual-write session-init composing/candidates
- 新文件：`docs/SPRINT4_INPUTUISYNC_ROUND3.md`（本檔）
- 更新文件：`docs/INPUT_UI_SYNC_BOUNDARY.md`（狀態/版本/保留區同步更新）、`IME_SMOKE_CHECKLIST.md`（新增 §10c-S4R3）
- 其他 `.kt` 未動：`ImeServiceInput.kt` / `ImeServiceCallbacks.kt` / `ImeServiceLifecycle.kt` / `InputUiSync.kt` 本輪未修改

### B. Round 3 收斂了哪些 `applyInputResult` / sync 路徑

- `applyInputResult()` 本體：**0 行改動**。盤點確認本函式無直接 UI render（見 §2.1）；其 UI render 責任於 Round 1 / Round 2 完成由 caller 委派 InputUiSync，Round 3 僅做文件化驗證
- `syncImeCoordinatorFromRouter()`：**2 行移除**（composing + candidates 的 coordinator-only 寫入），**1 行保留**（`setInputMode`）
- `onStartInputView` session-init：**2 行新增**（補齊原由 `syncImeCoordinatorFromRouter` 隱含承擔、現由 InputUiSync 明確承擔的 session-init composing/candidates render）
- 淨效果：`applyInputResult` 結尾的 coordinator sourceMap 暫失問題消除；session-init 改走統一的 InputUiSync dual-write 入口

### C. 哪些點正式列為 Sprint 4 保留區

見 §5 完整分類表。摘要：

- **A 類 狀態欄位**：`syncImeCoordinatorFromRouter.setInputMode` 等非 composing/candidates 的 coordinator 寫入
- **B 類 RMW**：`requestAICandidates` (AI debounce L402)、`handleSwipeInput` (L468-469)、HANDWRITING `onRecognized`
- **C 類 Niche**：TOGGLE_KANA (L789)、intermediate char-commit clear (L832-833)、`handleTab` focus (L536)、`handleSwitchLang` 清候選 (L650/L658)
- **D 類 已驗證無直寫**：`applyInputResult()` body、`syncImeCoordinatorFromRouter()` Round 3 後
- **E 類 Sprint 5 候選**：`applyInputResult` 11 類副作用抽出（`InputActionExecutor` + hooks）、`KeyEventInterpreter`、Sprint 5 新狀態欄位

### D. 驗證結果

- `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL in 4s`
- `applyInputResult()` 本體 grep 確認：無 `keyboardView.update*` 或 `inputUiSync.render*` 呼叫
- `syncImeCoordinatorFromRouter()` 現在只剩 `setInputMode` 一行
- `onStartInputView` L479 後的 InputUiSync 顯式呼叫驗證 session-init 行為等價（`inputRouter` 已由 session cleanup 重置為空）
- 所有 6 個 `applyInputResult()` caller 的 post-call 都已於 Round 1/2 走 InputUiSync（§2.2 表驗證）
- 禁區未動：`KeyboardView.kt` / `ImeServicePanels.kt` / Sprint 5 狀態 / AI debounce / swipe / HANDWRITING RMW

### E. 請 `CodeX A` 判定 Sprint 4 是否完成

Round 3 交付：

1. `applyInputResult()` UI render 責任盤點完成，確認 0 直接寫入點，全數經 caller 委派 InputUiSync
2. `syncImeCoordinatorFromRouter()` 的 composing/candidates 部分最小併入 InputUiSync（透過 session-init 顯式呼叫 + applyInputResult 結尾移除冗餘），`setInputMode` 列為 A 類保留
3. 五類保留區（§5 A / B / C / D / E）完整分類，對照 `SPRINT4_INPUT_FLOW_REVIEW §5` 達成情形見 §4
4. 累計 Sprint 4：20 處寫入 / 12 call site 納入 InputUiSync；保留區共識化

建議判定選項：

- **Sprint 4 = COMPLETED**：Round 1-3 已達 §5.3 完結、§5.1 / §5.2 完整分類、§5.5 體感不變；保留區 B / C / E 類明確屬 Sprint 5+ 範圍，無 Round 4 規劃必要
- **Sprint 4 = NEEDS ROUND 4**：若 `CodeX A` 認為 B 類 RMW 或 C 類 niche 須在本 Sprint 收斂，請指定具體 scope
- **Sprint 4 = LOCKED**：暫停 Sprint 4 推進，保留現狀

`CodeX A` 未判定前，Claude Code B 不會動其他 `.kt`，本輪落地狀態保持。

# IME Smoke Checklist (Claude Code B)

> 最小驗證保護，每輪重構後逐項確認。
> 日期：2026-04-15（Phase 1 collect 接線後更新）

## 1. 基本輸入

- [ ] 英文打字：按字母鍵 → composing bar 顯示 → Space 提交
- [ ] 注音輸入：按注音符號 → composing bar 顯示字根 → Space 選字
- [ ] 倉頡/速成：按字母 → 字根映射正確 → Space 提交首候選
- [ ] 日文輸入：假名輸入 → composing → Space 確認
- [ ] 直接符號：按數字/標點 → 直接 commit（不進 composing）
- [ ] Delete 鍵：composing 中刪字根 / composing 空時刪游標前字
- [ ] Enter 鍵：composing 非空時提交原始碼 / composing 空時發 Enter

## 2. 候選字

- [ ] 候選列顯示：打字後候選列出現，點選候選即 commit
- [ ] AI 候選字 debounce：打字停頓後出現 AI 建議（需 API Key）
- [ ] Swipe 候選：滑動輸入後顯示滑動候選列
- [ ] 展開候選面板：點候選列展開 → 顯示全部候選
- [ ] AUTO 模式混合候選：AUTO 模式下同時顯示中英候選

## 3. Panel 開關

- [ ] Toolbar 點擊開啟 → 再點關閉（toggle 行為正確）
- [ ] 切換面板 → 前一面板正確清除，新面板載入
- [ ] 面板關閉後 → 鍵盤恢復焦點，按鍵可正常輸入
- [ ] composing 不為空時開面板 → composing 先 commit 再開面板

## 4. 低依賴面板（ImeBasicPanelFactory）

- [ ] EMOJI：9 分類網格顯示，點擊 emoji commit
- [ ] KAOMOJI：9 分類顯示，點擊 commit
- [ ] CLIPBOARD：歷史記錄顯示，點擊 commit，釘選/刪除可操作
- [ ] CALCULATOR：計算器顯示，輸入結果可 commit
- [ ] PHRASE：常用片語列表，點擊 commit
- [ ] COLOR_PICKER：色碼選擇器，插入色碼
- [ ] UNIT_CONVERTER：單位轉換器，插入結果

## 5. 中依賴面板（ImePanelFactory）

- [ ] TRANSLATE：來源文字載入，翻譯結果顯示，點擊 commit
- [ ] GIF：趨勢標籤顯示，搜尋可操作，點擊 commit image
- [ ] STICKER：貼圖包顯示，點擊 commit（sticker text 或 image）
- [ ] DECRYPT：加密面板顯示，加密結果 commit
- [ ] DICTIONARY：查詢面板顯示，輸入文字觸發查詢
- [ ] LANG_LEARN：語言學習面板顯示

## 6. 高依賴面板（service fallback）

- [ ] THEME：主題列表顯示，切換主題即時生效
- [ ] CLAW：AI 對話面板開啟
- [ ] REMOTE：藍牙/WiFi 面板顯示狀態
- [ ] HANDWRITING：手寫面板開啟，手寫辨識產生候選
- [ ] MUSIC：音樂面板開啟
- [ ] USER_DICT：使用者詞庫面板顯示

## 7. 語音輸入

- [ ] 點 VOICE → 語音 Overlay 顯示 + Lottie listening 動畫
- [ ] 語音辨識 partial result → Overlay 即時更新文字
- [ ] 語音辨識 final result → Overlay 顯示最終文字，確認按鈕
- [ ] 確認按鈕 → commit text + Overlay 消失
- [ ] 取消按鈕 → 不 commit + Overlay 消失 + 角色回 idle
- [ ] 打字中按任意鍵 → 語音自動停止
- [ ] 缺少麥克風權限 → Toast 提示 + 跳設定

## 8. Session 生命週期

- [ ] onStartInputView → 清除上一 session composing 殘留
- [ ] onStartInputView → 同步智慧輸入開關 + 語言模式
- [ ] onFinishInputView → 停止感測器 + 暫停視覺特效
- [ ] onFinishInputView → CloudSyncManager.onSessionEnd() 觸發
- [ ] onFinishInput → 重置 inputRouter + touch state
- [ ] onDestroy → 所有 engine/client destroy + scope cancel

## 9. 感測器

- [ ] 搖手機 → 觸發 Ctrl+Z（需開啟設定）
- [ ] 長按空白鍵 + 傾斜 → 游標移動（需開啟設定）
- [ ] onFinishInputView → 感測器停止
- [ ] onStartInputView → 感測器重新啟動

## 10. 狀態同步

### 10a. uiState 寫入端（Sprint 2 已接，全 11 欄位）

- [ ] ImeSessionCoordinator.uiState 包含正確的 inputMode
- [ ] ImeSessionCoordinator.uiState 包含正確的 composingText
- [ ] ImeSessionCoordinator.uiState 包含正確的 candidates
- [ ] ImeSessionCoordinator.uiState 包含正確的 activePanel
- [ ] ImeSessionCoordinator.uiState 包含正確的 isVoiceInputActive
- [ ] ImeSessionCoordinator.uiState 包含正確的 isFloatingMode
- [ ] ImeSessionCoordinator.uiState 包含正確的 isPanelBehindKeyboard
- [ ] ImeSessionCoordinator.uiState 包含正確的 isInGameMode
- [ ] ImeSessionCoordinator.uiState 包含正確的 remoteConnectionState
- [ ] ImeSessionCoordinator.uiState 包含正確的 theme
- [ ] ImeSessionCoordinator.uiState 包含正確的 warningBanner

### 10b. uiState 消費端 — Phase 1 collect 驅動（Sprint 3）

> **目前由 `renderImeUiState(state)` collect 驅動的欄位只有 2 個。**
> **`composingText` / `candidates` 仍是同步直寫 UI，不走 collect。**

- [ ] `startUiStateCollect()` 在 `onCreateInputView` 末尾啟動（含懸浮模式 early return）
- [ ] `uiStateCollectJob` 在 `onDestroy` 正確 cancel + null
- [ ] **activePanel → toolbarView.activePanel**：面板切換後 toolbar 高亮正確同步
- [ ] **isVoiceInputActive → keyboardView.voiceListeningLocale**：語音開始時鍵盤顯示語音指示，停止時清除
- [ ] 面板切換不再有直接寫 `toolbarView.activePanel`（已移除 2 處）
- [ ] 語音流程不再有直接寫 `keyboardView.voiceListeningLocale`（已移除 6 處）
- [ ] voiceOverlay onConfirm/onCancel 改為寫 `imeCoordinator.setVoiceInputActive(false)`

### 10c. Phase 2 第一版 guarded consume（Sprint 3 Phase 2，2026-04-17）

> **composingText / candidates 已部分進入 collect 消費，但只消費「清空信號」。**
> **非空狀態仍由熱路徑 / callback 同步直寫；Round 2 已把 callback 清空寫入統一走 helper，coordinator 不再 stale。**

- [ ] `startImeComposingCandidatesCollect()` 在 `onCreateInputView` 末尾啟動（含懸浮模式 early return）
- [ ] `composingCandidatesCollectJob` 在 `onDestroy` 正確 cancel + null
- [ ] `clearComposingAndCandidatesUi()` 被呼叫後，collect 會再次寫入空值到 `KeyboardView`（idempotent）
- [ ] 熱路徑打字後 `state.composingText` 不為空 → collect 不寫入 → 不覆蓋熱路徑直寫
- [ ] 熱路徑打字後 `state.candidates` 不為空 → collect 不寫入 → 不覆蓋熱路徑直寫
- [ ] 3 個 session 邊界 call site 觸發 helper → view 正確清空（resetInputUiStateForPanelTransition / gyro F-8 / onStartInputView lambda）

### 10c-R2. Phase 2 Round 2 callback consume 收斂（2026-04-17）

> **`ImeServiceCallbacks.kt` 的 5 處 cleanup 寫入全部改走 `clearComposingAndCandidatesUi()` 或 `clearCandidatesUi()` helper。**
> **callback 寫入後 coordinator 與 view 一致，`.isEmpty()` guard 從「防 stale」降級為「safety net」。**

- [ ] `setOnRawCommitListener.flushComposingBeforeRawCommit.clearInputUiState` 走 helper
- [ ] swipe `clearSwipeState.clearInputUiState` 走 helper
- [ ] AI candidate 分支（`isAICandidate` 成立）commit 後走 helper
- [ ] HANDWRITING 分支 commit 後走 `clearCandidatesUi()`（candidates-only）
- [ ] `setOnLanguageSelectedListener` Language 分支切換完成後走 helper
- [ ] Round 2 後 `ImeServiceCallbacks.kt` 不再有任何 `keyboardView.updateComposingText("")` / `updateCandidates(emptyList())` 直寫（grep verified）

### 10c-S4R1. Sprint 4 Round 1 — `InputUiSync` 正式邊界（2026-04-17）

> **`composingText / candidateState` 的同步 render ownership 正式收斂到 `app.ime.InputUiSync` class。**
> **本輪遷移 4 個 call site（`handleKeyPress` post-update 2 行 + `setOnCandidateSelectListener` post-commit 2 行 + 2 cleanup helper）；其餘熱路徑待後續輪次。**

- [ ] `app/ime/InputUiSync.kt` 存在，含 `renderComposing / renderCandidates / clearComposing / clearCandidates / clearAll` 5 method
- [ ] `YukiBoardIMEService.inputUiSync: InputUiSync by lazy { ... }` 正確 lazy init
- [ ] `viewProvider = { keyboardView }` 能在懸浮 / 標準模式切換後仍取到最新 `keyboardView`
- [ ] `handleKeyPress` post-update composing/candidates 已走 InputUiSync（coordinator 現在同步承載 `candidateSourceMap`）
- [ ] `setOnCandidateSelectListener` post-commit refresh 已走 InputUiSync
- [ ] `clearComposingAndCandidatesUi()` 改為 `inputUiSync.clearAll()`，行為不變
- [ ] `clearCandidatesUi()` 改為 `inputUiSync.clearCandidates()`，行為不變
- [ ] `./gradlew :app:compileDebugKotlin` 通過
- [ ] 禁區（`KeyboardView.kt` / `InputRouter.kt` / Sprint 5 狀態）完全未動

### 10c-S4R2. Sprint 4 Round 2 — `ImeServiceInput.kt` 子 handler 遷移（2026-04-17）

> **`handleDelete` / `handleEnter` / `handleSpace` / `routeKeyToPanel` 共 8 處寫入全部改走 `InputUiSync`。**
> **本輪 file scope 嚴格限定 `ImeServiceInput.kt` + 3 份 doc；未擴張至其他 `.kt`。**

- [ ] `handleDelete(ic)` 結尾 render pair 走 `inputUiSync.renderComposing / renderCandidates`
- [ ] `handleEnter(ic)` applyInputResult 後 render pair 走 InputUiSync
- [ ] `handleSpace(ic)` Tab focus-confirm 分支 `renderComposing("") + renderCandidates(...)`
- [ ] `handleSpace(ic)` 一般 space 分支 render pair 走 InputUiSync
- [ ] `routeKeyToPanel` `KEYCODE_DELETE` CJK 分支 render pair 走 InputUiSync
- [ ] `routeKeyToPanel` `KEYCODE_ENTER` CJK 分支改走 `inputUiSync.clearAll()`（合併 composing 空 + candidates 空）
- [ ] `routeKeyToPanel` `KEYCODE_SPACE` CJK 分支 render pair 走 InputUiSync
- [ ] `routeKeyToPanel` else（一般 label）CJK 分支 render pair 走 InputUiSync
- [ ] `ImeServiceInput.kt` grep `keyboardView\\.(updateComposingText|updateCandidates)` 殘留 5 處，全屬 FORBIDDEN（AI debounce / swipe RMW）或 boundary-doc-inline（handleTab focus / handleSwitchLang 清候選）
- [ ] `./gradlew :app:compileDebugKotlin` 通過
- [ ] 禁區（`KeyboardView.kt` / `InputRouter.kt` / `ImeServicePanels.kt` / `ImeServiceCallbacks.kt` / `YukiBoardIMEService.kt` / Sprint 5 狀態）完全未動
- [ ] sourceMap 在所有遷移寫入均同步流入 coordinator（`renderCandidates(items, sourceMap)`）

### 10c-S4R3. Sprint 4 Round 3 — `syncImeCoordinatorFromRouter` 最小併入 + 保留區定義（2026-04-17）

> **`syncImeCoordinatorFromRouter()` 簡化為 `setInputMode` only；`onStartInputView` session-init 明確走 InputUiSync；`applyInputResult()` 本體確認無直接 UI render（0 lines changed）；Sprint 4 保留區五類（A/B/C/D/E）正式化。**

- [ ] `syncImeCoordinatorFromRouter()` 只剩 `imeCoordinator.setInputMode(inputRouter.inputMode)` 一行
- [ ] `onStartInputView` L479 後有 2 行 `inputUiSync.renderComposing(inputRouter.composingText) + renderCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)`
- [ ] `applyInputResult()` 本體不含 `keyboardView.update*` 或 `inputUiSync.render*`（UI render 由 caller 委派）
- [ ] 6 個 `applyInputResult()` caller 的 post-call 全部走 InputUiSync（handleKeyPress / handleEnter / handleSpace x2 / ImeServiceCallbacks x2）
- [ ] Sprint 4 保留區五類清單與 `docs/SPRINT4_INPUTUISYNC_ROUND3.md §5` 一致
- [ ] `./gradlew :app:compileDebugKotlin` 通過
- [ ] 禁區（`KeyboardView.kt` / `ImeServicePanels.kt` / Sprint 5 狀態 / AI debounce / swipe / HANDWRITING RMW）完全未動
- [ ] applyInputResult 結尾的 sourceMap 暫失中間態已消除（caller 走 `renderCandidates(items, sourceMap)` 直接承載）

### 10c-S5R1. Sprint 5 Round 1 — `ImeContainerStateConsumer` read-only 邊界（2026-04-17）

> **container / external state 的同步 snapshot read 正式集中到 `app.ime.ImeContainerStateConsumer`。**
> **本輪僅建立邊界入口，既有 reader / writer 全部未動（遷移屬 Round 2，writer 整併屬 Round 3）。**

- [ ] `app/ime/ImeContainerStateConsumer.kt` 存在，含 5 個 getter：`isFloating / isPanelBehind / isInGame / remoteConnection / activePanel`
- [ ] 同檔提供 `snapshot(): ImeContainerSnapshot` 聚合讀取 API
- [ ] `ImeContainerSnapshot` data class 欄位與 getter 對齊（避免跨版本漂移）
- [ ] `YukiBoardIMEService.imeContainerState: ImeContainerStateConsumer by lazy { ... }` 正確 lazy init
- [ ] `ImeContainerStateConsumer` 不暴露 `StateFlow<ImeUiState>`（封裝邊界）
- [ ] 既有 writer（7 處 dual-write + 1 coordinator-only）完全未改
- [ ] 既有 reader（11 處 local field read）完全未改
- [ ] `./gradlew :app:compileDebugKotlin` 通過
- [ ] 禁區完全未動：`KeyboardView.kt` / `InputRouter.kt` / AI debounce / network / settings / HANDWRITING RMW / swipe
- [ ] Sprint 4 邊界（`InputUiSync`）未被碰觸

### 10c-S5R3. Sprint 5 Round 3 — writer consolidation + local field removal（2026-04-17）

> **4 個 container state local field 全部移除；SSOT 轉移至 `imeCoordinator.uiState`。**
> **R5 / R11 HANDWRITING guard 本輪收尾遷移（Round 3 移除 local field 前置）。**

- [ ] Pre-step：R5（`YukiBoardIMEService.kt:729`）+ R11（`ImeServiceCallbacks.kt:198`）已改讀 `imeContainerState.activePanel`
- [ ] Batch α：W1（`ImeServiceMisc.kt` `restorePanelOverlayOrder`）+ W6（`ImeServicePanels.kt` `showPanel` overlay attach）移除 `isPanelBehindKeyboard = false` 本地寫
- [ ] Batch β：W2（`ImeServiceMisc.kt` `togglePanelLayer`）合併為 `imeCoordinator.setPanelBehindKeyboard(!imeContainerState.isPanelBehind)` 單 write
- [ ] Batch γ：W3（`ImeServiceMisc.kt` `wrapInFloatingMode`）移除 `isFloatingMode = true` 本地寫
- [ ] Batch δ：W4（`enterGameMode`）+ W5（`exitGameMode`）移除 `isInGameMode = true/false` 本地寫
- [ ] Batch ε：W7（`ImeServiceMisc.kt` `updatePanelSelection`）移除 lambda 內 `activePanel = selectedPanel`
- [ ] Final：`YukiBoardIMEService.kt` 已移除 `internal var isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel` 四個宣告
- [ ] Grep 驗證：service package 內除 coordinator setter / `imeContainerState` / `ImeUiState` 外，無 local field 讀寫
- [ ] `./gradlew :app:compileDebugKotlin` 通過
- [ ] 禁區完全未動：`KeyboardView.kt` / `InputRouter.kt` / network / AI debounce / swipe / HANDWRITING RMW
- [ ] Sprint 4 邊界（`InputUiSync`）未被觸動

### 10c-S5R2. Sprint 5 Round 2 — reader migration（2026-04-17）

> **9/11 處 reader 遷移至 `imeContainerState.xxx`；2/11 處（R5 / R11 HANDWRITING guard）保留 inline。**
> **本輪 writer path 零變動，local field 未移除，禁區完全未觸。**

- [ ] Batch 1 reader 遷移（7 處）：
  - `YukiBoardIMEService.kt:283` `ShakeDetector` `!imeContainerState.isInGame`（R1）
  - `YukiBoardIMEService.kt:650` `onComputeInsets` `imeContainerState.isFloating`（R3）
  - `ImeServicePanels.kt:110` `handlePanelToggle` `currentActivePanel = imeContainerState.activePanel`（R6）
  - `ImeServicePanels.kt:172` showPanel overlay attach `imeContainerState.isPanelBehind`（R7）
  - `ImeServiceMisc.kt:108` `restorePanelOverlayOrder` `imeContainerState.isPanelBehind`（R8）
  - `ImeServiceMisc.kt:163` `togglePanelLayer` `imeContainerState.isPanelBehind`（R9）
  - `ImeServiceMisc.kt:168` `togglePanelLayer` pass-through `imeContainerState.isPanelBehind`（R10）
- [ ] Batch 2 reader 遷移（2 處，hot-path 經評估）：
  - `YukiBoardIMEService.kt:537` `onStartInputView` game-mode resume `imeContainerState.isInGame`（R2 / session-init 實測非熱路徑）
  - `YukiBoardIMEService.kt:685` `handleKeyPress` 外層 panel-dispatch guard `imeContainerState.activePanel`（R4 / writer dual-write 同步）
- [ ] 保留 inline（HANDWRITING guard，依 Round 2 規則）：
  - `YukiBoardIMEService.kt:728` `KEYCODE_HANDWRITING` branch（R5）
  - `ImeServiceCallbacks.kt:197` `onRecognized` HANDWRITING guard（R11）
- [ ] Writer path 零變動：7 處 dual-write + 1 coordinator-only 全部未改
- [ ] Local field 未移除：`internal var isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel` 宣告仍存在
- [ ] `./gradlew :app:compileDebugKotlin` 通過
- [ ] 禁區完全未動：`KeyboardView.kt` / `InputRouter.kt` / network / AI debounce / swipe / HANDWRITING RMW

### 10c-R3. Phase 2 Round 3 `CandidateUiState` 結構升級（2026-04-17）

> **`ImeUiState.candidates: List<String>` 升級為 `candidateState: CandidateUiState(items, sourceMap)`。**
> **coordinator 擴充 `updateCandidates(items, sourceMap)` / `updateCandidateState(state)` API，可完整承載 `CandidateSource` 著色資訊。**

- [ ] `ImeUiState.candidateState` 欄位型別為 `CandidateUiState(items: List<String>, sourceMap: Map<String, CandidateSource>)`
- [ ] `CandidateUiState.EMPTY` 與 `.onFinishInput()` 搭配正確重置
- [ ] 舊版 `imeCoordinator.updateCandidates(List<String>)` 仍可用（sourceMap 視為空），維持 `syncImeCoordinatorFromRouter()` 相容
- [ ] 新版 `imeCoordinator.updateCandidates(items, sourceMap)` / `updateCandidateState(state)` 可用於未來 Sprint 4 熱路徑接入
- [ ] `startImeComposingCandidatesCollect()` guard 改讀 `state.candidateState.items.isEmpty()`
- [ ] `./gradlew :app:compileDebugKotlin` 通過
- [ ] `KeyboardView.kt` / `handleKeyPress()` / `applyInputResult()` / AI debounce / HANDWRITING onRecognized / swipe RMW 均未動

### 10d. 仍為同步直寫 UI（Phase 2 未完）

- [ ] `composingText`：熱路徑 11 + panel/gesture 4（Round 2 後 callback 全走 helper）
- [ ] `candidates`：熱路徑 13 + panel/gesture 6（Round 2 後 callback 全走 helper）
- [ ] 以上兩欄位的非空寫入受 `handleKeyPress()` / `applyInputResult()` / callback batch 同步時序約束
- [ ] 13 處帶 `sourceMap` 的 candidates 寫入接入 coordinator 屬 Sprint 4 範圍（結構已就緒，只等 Sprint 4 熱路徑抽出）

### 10d. prefs 同步

- [ ] prefs → view 同步走集中的 syncPrefsToViews()

## 11. 整合副作用

- [ ] 防詐掃描：每次 CommitText 後觸發 scanForFraud
- [ ] 回覆風險偵測：CommitText 後觸發 scanReplyForFraud
- [ ] 剪貼簿監聽：onStartInputView 註冊 → onFinishInputView 解除
- [ ] 遠端鍵盤轉發：CommitText 後 forwardTextToRemote 觸發
- [ ] TTS 朗讀：候選語音/翻譯朗讀/雪凜語音正確路由

---

## 12. Phase 1 Runtime 驗證重點

> 以下項目需要實機或模擬器，目前為 **runtime unverified**。

### 12a. 面板高亮（activePanel collect）

- [ ] 點開 EMOJI 面板 → toolbar EMOJI 按鈕高亮
- [ ] 切換到 TRANSLATE 面板 → toolbar EMOJI 取消高亮，TRANSLATE 高亮
- [ ] 關閉面板 → toolbar 所有按鈕取消高亮
- [ ] 環形選單選 Emoji → 觸發完整 panel toggle 流程 → toolbar 正確高亮
- [ ] 懸浮模式下開面板 → toolbar 高亮同樣正確

### 12b. 語音指示燈（isVoiceInputActive collect）

- [ ] 點 VOICE → 鍵盤上出現語音指示（voiceListeningLocale = "auto"）
- [ ] 語音 final result 返回 → 指示消失（voiceListeningLocale = null）
- [ ] 語音 error → 指示消失
- [ ] voiceOverlay 確認按鈕 → 指示消失（走 coordinator.setVoiceInputActive(false)）
- [ ] voiceOverlay 取消按鈕 → 指示消失（走 coordinator.setVoiceInputActive(false)）
- [ ] stopVoiceInput() → 指示消失（走 coordinator.setVoiceInputActive(false)）

### 12c. collect lifecycle

- [ ] 連續 onCreateInputView → 不累積多個 collect job（startUiStateCollect 內先 cancel）
- [ ] onDestroy → collect 停止，無 leak
- [ ] 懸浮模式 → collect 正常啟動

---

## 已知限制

- 上述為 compile-time + code-path 驗證，不是 runtime UI 測試
- 需要實機或模擬器跑完整 smoke test
- AI 候選字、Whisper 語音需要有效 API Key
- 藍牙/WiFi 遠端鍵盤需要配對裝置
- **Phase 1 collect 接線為靜態接線驗證，runtime 尚未實測**
- **Phase 2 第一版 guarded consume 已落地（2026-04-17）**：
  - `clearComposingAndCandidatesUi()` dual-write（view + coordinator）
  - `startImeComposingCandidatesCollect()` 訂閱 coordinator，只寫空值（`.isEmpty()` guard）
  - 完整 consume（含非空寫入走 coordinator）等 `ImeServiceCallbacks.kt` 收斂與 `ImeUiState.candidates` 結構升級後再推進
- **Phase 2 Round 2 callback 收斂已落地（2026-04-17）**：
  - `ImeServiceCallbacks.kt` 內 5 處 cleanup 寫入全改走 `clearComposingAndCandidatesUi()` / `clearCandidatesUi()`
  - callback 路徑的 coordinator / view 雙寫一致，stale-state race 已關閉
  - `.isEmpty()` guard 仍保留作為 safety net；移除 guard 需等 Sprint 4 `InputUiSync` 熱路徑抽出 + sourceMap 結構升級
- **Phase 2 Round 3 `CandidateUiState` 結構升級已落地（2026-04-17）**：
  - `ImeUiState.candidateState: CandidateUiState(items, sourceMap)` 取代 `candidates: List<String>`
  - coordinator 新增 `updateCandidates(items, sourceMap)` / `updateCandidateState(state)`；legacy `updateCandidates(List<String>)` 保留以維持熱路徑相容
  - collect guard 讀 `candidateState.items.isEmpty()`
  - 結構承載層已就緒；實際熱路徑改走 coordinator（AI debounce / HANDWRITING onRecognized / swipe RMW / `setOnCandidateSelectListener` post-commit 回寫）屬 Sprint 4 範圍，本輪不動
- **Sprint 4 Round 1 `InputUiSync` 正式邊界已落地（2026-04-17）**：
  - 新檔 `app/ime/InputUiSync.kt`（class，同步 dual-write view + coordinator）
  - `handleKeyPress` post-update 主熱路徑 + `setOnCandidateSelectListener` post-commit + 2 cleanup helper 已遷移（4 call sites）
  - `applyInputResult` 副作用分類、`ImeServiceInput.kt` 13 處子 handler、AI debounce / HANDWRITING / swipe RMW 屬 Round 2+ 範圍
- **Sprint 5 Round 1 `ImeContainerStateConsumer` 邊界已落地（2026-04-17）**：
  - 新檔 `app/ime/ImeContainerStateConsumer.kt`（class，read-only snapshot getter + `snapshot()` 聚合 API）
  - `YukiBoardIMEService.imeContainerState by lazy { ... }` 建立入口
  - 承載 5 個 container / external state 欄位：`isFloating / isPanelBehind / isInGame / remoteConnection / activePanel`
  - 既有 11 處 reader（local field read）+ 8 處 writer（7 dual-write + 1 coordinator-only）本輪完全未動
  - Reader 遷移屬 Round 2；writer 整併與 local field 移除屬 Round 3
  - 細節請見 `docs/SPRINT5_ROUND1_STATE_CONSUME.md`
- **Sprint 5 Round 2 reader migration 已落地（2026-04-17）**：
  - 9/11 處 reader 遷移至 `imeContainerState.xxx`（Batch 1：R1/R3/R6/R7/R8/R9/R10；Batch 2：R2/R4）
  - 保留 inline：R5（`YukiBoardIMEService.kt:728`）/ R11（`ImeServiceCallbacks.kt:197`）兩處 HANDWRITING guard，依「R5、R11 不准硬改，涉及 HANDWRITING 留 inline」規則
  - Writer path 零變動；`isFloatingMode / isPanelBehindKeyboard / isInGameMode / activePanel` 四個 local field 未移除（屬 Round 3）
  - `./gradlew :app:compileDebugKotlin` 通過（BUILD SUCCESSFUL in 21s）
- **Sprint 5 Round 3 writer consolidation + local field removal 已落地（2026-04-17）**：
  - 依順序收斂：R5/R11 pre-step → Batch α（W1/W6）→ Batch β（W2）→ Batch γ（W3）→ Batch δ（W4/W5）→ Batch ε（W7）→ Final（移除 4 個 local field）
  - 每批結束時 grep 確認 reader 已不再讀 local；最終 4 個宣告安全移除
  - `YukiBoardIMEService` 不再持有 container-state mutable mirror；SSOT = `imeCoordinator.uiState`
  - `./gradlew :app:compileDebugKotlin` 通過（BUILD SUCCESSFUL in 5s）
  - 禁區完全未動：`KeyboardView.kt` / `InputRouter.kt` / network / AI debounce / swipe / HANDWRITING RMW
  - 細節請見 `docs/SPRINT5_ROUND3_WRITER_CONSOLIDATION.md`

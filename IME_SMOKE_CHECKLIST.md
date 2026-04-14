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

### 10c. 仍為同步直寫 UI（Phase 2 延後）

- [ ] `composingText`：20 處直接寫 `keyboardView.updateComposingText()`（不動）
- [ ] `candidates`：26 處直接寫 `keyboardView.updateCandidates()`（不動）
- [ ] 以上兩欄位受 `handleKeyPress()` / `applyInputResult()` 同步時序約束，不適合用 async collect 驅動

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
- **Phase 2（composingText/candidates）因同步時序風險延後，需 Codex 審閱 LOCKED 方法**

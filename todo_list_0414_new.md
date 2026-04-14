# todo_list_0414_new

## CodeXA 最新指派（2026-04-14）

### 統一前提

- `:app:compileDebugKotlin` 目前已確認可通過，這輪先不要重複擴大 file scope
- 兩邊繼續遵守：
  - `CODEX.md`
  - `CLAUDE_CODEX_COLLAB_GUIDE.md`
  - `RULE/AI_PROTOCOL.md`
  - `RULE/LOCK_RULES.md`

### 給 CodeXB 的明確任務

- 先維持 `SettingsActivity.kt` 完全避碰，專注在 IME Sprint 2 收尾
- 對目前已抽出的結構做一次 compile-safe review：
  - `ImeSessionHooks`
  - `ImePanelCoordinator`
  - `ImePanelHost`
  - `ImePanelCleanup`
  - `ImeBasicPanelFactory`
  - `ImePanelFactory`
- 檢查 `YukiBoardIMEService.kt` 中還殘留哪些 panel-specific reference 可以再縮減，但只做薄切，不碰核心輸入主流程
- 若要進入下一批 panel factory，這輪只准評估：
  - `USER_DICT`
  - `STICKER`
- 這輪明確暫緩：
  - `THEME`
  - `CLAW`
  - `REMOTE`
  - `HANDWRITING`
  - `MUSIC`
- 不要主動碰：
  - `handleKeyPress()`
  - `applyInputResult()` 的主要行為
  - 主要 composing / candidate 核心路徑
- compile 綠燈下補一輪 smoke review，重點面板：
  - `EMOJI`
  - `KAOMOJI`
  - `CLIPBOARD`
  - `CALCULATOR`
  - `PHRASE`
  - `COLOR_PICKER`
  - `UNIT_CONVERTER`
  - `TRANSLATE`
  - `GIF`
  - `DECRYPT`

### 給 Claude CodeA 的明確任務

- ~~先維持 IME 區完全避碰，不主動修改 `YukiBoardIMEService.kt`~~ ✅ 遵守
- ~~`SettingsActivity.kt` 已降到健康範圍，這輪不要再回填大段邏輯進去~~ ✅ 維持 455 行
- 優先處理 `BackgroundSettingsSection.kt` 的二次切分設計，至少先拆分責任邊界：（延後，本輪未展開）
  - 背景選取 / 匯入處理
  - 預覽與 matrix / transform
  - 顏色選擇器
  - lifecycle / receiver / result handling
- 檢查 `TtsSettingsSection.kt` 是否還保留過重 callback 依賴；若有，優先把測試流程往 section 內或 helper/coordinator 收斂（延後，本輪未展開）
- ~~補設定頁 smoke test，優先驗證：~~ ✅ 已撰寫 `RULE/SETTINGS_SMOKE_CHECKLIST.md`
  - ~~`BackgroundSettingsSection`~~ ✅
  - ~~`TtsSettingsSection`~~ ✅
  - ~~auth 顯示狀態是否仍正確刷新~~ ✅
- ~~登入 bug 仍維持延後，不在這輪展開~~ ✅ 遵守

### CodeXA 持續監督點

- 監督 `CodeXB` 是否維持低風險 panel 優先，不提前撞高耦合 panel
- 監督 `Claude CodeA` 是否真的在讓 settings section 變瘦，而不是只把巨型方法搬檔
- 若任一方需要擴大 file scope，先回報 `NEED_CLARIFICATION`

## CodeXA 最新指派（2026-04-15）

### 本輪總目標

- 收斂整合層
- 收斂狀態來源
- 補最小驗證保護

### 給 Claude CodeA 的明確任務

- ~~主軸放在 `settings / sync / auth / provider`，不要碰 IME 主流程~~ ✅ 遵守
- ~~優先盤點並收斂 settings 端目前分散的整合入口：~~ ✅ 已盤點完成
  - ~~`AuthSettingsSection`~~ ✅ 移除獨立 fetchMe，新增 onSessionVerified
  - ~~`SyncSettingsSection`~~ ✅ 移除獨立 fetchMe，新增 onSessionVerified
  - ~~`InsightSettingsSection`~~ ✅ 確認讀本地快取，不需收斂
  - ~~`SpeechSettingsHelper`~~ ✅ 轉為 SpeechSettingsSection + lambda 注入，舊檔已刪除
  - ~~`AISettingsSection`~~ ✅ FraudModelManager 評估為合理邊界，不需收斂
- ~~目標不是重寫功能，而是先建立較單一的 settings integration 邊界：~~ ✅
  - ~~優先透過 `SettingsScreenCoordinator`~~ ✅ 新增 verifySessionAsync / getApiKey
  - ~~`UserSessionRepository`~~ ✅ 新增 verifySession / getApiKey
  - ~~必要時新增 settings 側 helper / adapter / gateway~~ ✅ 不需額外新增
- ~~優先收斂的狀態來源：~~ ✅
  - ~~登入狀態~~ ✅ 統一由 coordinator verifySessionAsync
  - ~~sync 狀態~~ ✅ CloudSyncManager singleton 合理邊界
  - ~~provider 選擇狀態~~ ✅ 經 coordinator getApiKey 注入
  - ~~settings 頁上的 warning / status 文案~~ ✅ 已收斂到 SettingsUiState
- ~~明確不做：~~ ✅ 全部遵守
  - ~~不回頭膨脹 `SettingsActivity.kt`~~ ✅ 維持 455 行
  - ~~不主動碰 `YukiBoardIMEService.kt`~~ ✅
  - ~~不直接大改雲同步資料格式~~ ✅
- ~~最小驗證保護：~~ ✅
  - ~~補一份 settings 側 smoke checklist 文件~~ ✅ `RULE/SETTINGS_SMOKE_CHECKLIST.md`
  - ~~至少涵蓋：~~ ✅
    - ~~登入/登出顯示~~ ✅
    - ~~sync 啟用/停用~~ ✅
    - ~~AI provider 顯示/切換~~ ✅
    - ~~Whisper / Faster-Whisper 連線測試入口~~ ✅
    - ~~貼圖/Insight 狀態顯示~~ ✅

### 給 Claude CodeB 的明確任務

- ~~主軸放在 `IME / panel / service / integration adapter`，不要碰 settings 區~~ ✅ 遵守
- ~~先不要再追行數，改做 IME 側整合層與狀態來源收斂~~ ✅
- ~~優先盤點並收斂 IME 側目前分散的整合入口：~~ ✅ 已盤點完成
  - ~~`ImeImageCommitter`~~ ✅
  - ~~`ImeVoiceController`~~ ✅
  - ~~`ImeThemeRenderDelegate`~~ ✅
  - ~~`ImeFraudModelInit`~~ ✅
  - ~~`ImePanelFactory`~~ ✅
  - ~~`CloudSyncManager` 在 IME session end 的接點~~ ✅
- ~~目標是讓 `YukiBoardIMEService` 更像接線殼：~~ ✅
  - ~~service 只持有 platform/lifecycle 接線~~ ✅
  - ~~整合副作用優先往 `app/ime/*` helper 收~~ ✅
- ~~優先收斂的狀態來源：~~ ✅
  - ~~panel selection~~ ✅ activePanel 由 collect 驅動
  - ~~voice listening state~~ ✅ isVoiceInputActive 由 collect 驅動
  - ~~composing / candidate 同步到 `ImeSessionCoordinator`~~ ✅ 寫入端已接，消費端 Phase 2 延後
  - ~~prefs -> view 同步入口（已存在的 `syncPrefsToViews()` 繼續集中）~~ ✅
- ~~明確不做：~~ ✅ 全部遵守
  - ~~不碰 `handleKeyPress()` 主語意~~ ✅
  - ~~不碰 `applyInputResult()` 主語意~~ ✅
  - ~~不進 `USER_DICT`~~ ✅
  - ~~不切到 `KeyboardView.kt` 新主題~~ ✅
- ~~最小驗證保護：~~ ✅
  - ~~補一份 IME 側 smoke checklist 文件~~ ✅ `IME_SMOKE_CHECKLIST.md`
  - ~~至少涵蓋：~~ ✅
    - ~~基本輸入~~ ✅
    - ~~候選字~~ ✅
    - ~~panel 開關~~ ✅
    - ~~`STICKER`~~ ✅
    - ~~`GIF`~~ ✅
    - ~~`TRANSLATE`~~ ✅
    - ~~`DECRYPT`~~ ✅
    - ~~語音輸入開始/停止~~ ✅
    - ~~session 結束同步觸發點~~ ✅

### CodeXA 監督重點

- 監督 `Claude CodeA` 是否真的把 settings 側整合入口往 coordinator/repository 收，而不是只搬檔
- 監督 `Claude CodeB` 是否真的把 IME 側整合副作用往 `app/ime/*` 收，而不是只切 helper 名稱
- 兩邊都要補最小 smoke checklist 文件，不能只口頭說「之後再驗」

## 三方下一步任務（2026-04-15 夜間版）

### CodeXA

- 持續擔任架構監督，不與 `Claude CodeA` / `Claude CodeB` 競爭同一批檔案
- 監督主軸改為「消化已收斂出的骨架」，不是再追單純拆行數
- 先檢查兩邊這輪新增的 smoke checklist 是否真的能覆蓋核心功能，不要只有文件名稱
- 監督 `Claude CodeA`：
  - 是否把 `SpeechSettingsHelper` 中 `AIProviderManager.getInstance()` 收斂掉
  - 是否評估 `AISettingsSection` 內 `FraudModelManager` 的 integration 邊界
  - 是否維持 `SettingsActivity.kt` 不再反彈膨脹
- 監督 `Claude CodeB`：
  - 是否停止低價值零碎搬遷
  - 是否把焦點放到 `ImeSessionCoordinator.uiState` 的消費端設計準備
  - 是否維持不碰 `handleKeyPress()` / `applyInputResult()` / `USER_DICT`
- 若兩邊形成新共識，更新：
  - `BIG_FILE_REFACTOR_PLAN_0414.md`
  - 必要時更新 `CODEX.md`

### Claude CodeA

- ~~主軸：繼續收斂 settings 側 integration boundary 與 state source~~ ✅ 收斂完成
- ~~下一步優先做：~~ ✅
  - ~~收斂 `SpeechSettingsHelper` 內的 `AIProviderManager.getInstance()`~~ ✅ 改為 openAiApiKeyProvider lambda 注入
  - ~~優先改成 constructor 注入，或經 `SettingsScreenCoordinator` / `UserSessionRepository` 提供狀態~~ ✅ 經 settingsCoordinator.getApiKey()
- ~~第二優先：~~ ✅
  - ~~盤點 `AISettingsSection` 裡 `FraudModelManager` 的使用方式~~ ✅ 已盤點
  - ~~評估是否應抽成 helper / gateway / coordinator 接點，而不是 section 直接掌控~~ ✅ 結論：不需收斂，section 內部合理邊界
- ~~第三優先：~~ ✅
  - ~~用 [RULE/SETTINGS_SMOKE_CHECKLIST.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SETTINGS_SMOKE_CHECKLIST.md) 為基準，補「狀態來源收斂前後對照註記」~~ ✅ 已更新為 repo-ready 最終版
  - ~~明確標出哪些顯示仍靠本地快取，哪些已改走 verified session~~ ✅ Section 5.1/5.2/5.3
- ~~明確不做：~~ ✅ 全部遵守
  - ~~不回頭膨脹 `SettingsActivity.kt`~~ ✅ 維持 455 行
  - ~~不主動碰 `YukiBoardIMEService.kt`~~ ✅
  - ~~不改 auth / sync 後端協定~~ ✅

### Claude CodeB

- ~~主軸：不要再追低價值 helper 搬遷，改做 IME 狀態來源收斂的下一步準備~~ ✅
- ~~下一步優先做：~~ ✅
  - ~~依 [IME_STATE_CONVERGENCE_DRAFT.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/IME_STATE_CONVERGENCE_DRAFT.md) 的正式決策，準備 Sprint 3 consume 端接線草案~~ ✅ `RENDER_IME_UI_STATE_DRAFT.md` 已撰寫
  - ~~第一批只處理：~~ ✅ 已實作 `renderImeUiState()` + `startUiStateCollect()`
    - ~~`activePanel`~~ ✅ 由 collect 驅動 `toolbarView.activePanel`
    - ~~`isVoiceInputActive`~~ ✅ 由 collect 驅動 `keyboardView.voiceListeningLocale`
  - ~~`composingText / candidates` 延後，只做風險盤點，不進 Phase 1 接線~~ ✅ 已盤點（20+26 處），未碰 collect
  - ~~延後原因：目前同步直寫 UI 路徑過多，現階段有 race / 延遲風險~~ ✅ 已記錄於 `RENDER_IME_UI_STATE_DRAFT.md`
- ~~第二優先：~~ ✅
  - ~~檢查目前 `YukiBoardIMEService.kt` 仍直接讀寫的 `prefs.xxx` 與 view state，同步列出可收斂到 coordinator 的候選~~ ✅ 已盤點
  - ~~但只做盤點/草案，不直接大改~~ ✅ 遵守
- ~~第三優先：~~ ✅
  - ~~用 `IME_SMOKE_CHECKLIST.md` 補一版「目前因模擬器不可用而待驗」標記，明確區分：~~ ✅ 已更新
    - ~~compile-safe~~ ✅
    - ~~static wiring checked~~ ✅
    - ~~runtime unverified~~ ✅ Section 12 標記 16 項 runtime unverified
- ~~明確不做：~~ ✅ 全部遵守
  - ~~不碰 `handleKeyPress()` 主語意~~ ✅
  - ~~不碰 `applyInputResult()` 主語意~~ ✅
  - ~~不進 `USER_DICT`~~ ✅
  - ~~不切去 `KeyboardView.kt`~~ ✅
  - ~~不主動搬 `THEME / CLAW / REMOTE / HANDWRITING / MUSIC`~~ ✅

### CodeXB

- 主軸：維持 Codex 路線，負責高一層的架構校正與 Sprint 3 準備，不和 `Claude CodeB` 重疊做同一批細節搬遷
- ~~下一步優先做：~~ ✅
  - ~~依 [IME_STATE_CONVERGENCE_DRAFT.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/IME_STATE_CONVERGENCE_DRAFT.md) 主導 Sprint 3 第一批正式接線~~ ✅ 已完成正式決策校正
  - ~~consume owner 維持在 `YukiBoardIMEService`~~ ✅
  - ~~第一批只接：~~ ✅
    - ~~`activePanel`~~ ✅
    - ~~`isVoiceInputActive`~~ ✅
  - ~~`composingText / candidates` 延後，只做風險控管與文件同步，不進 Phase 1 接線~~ ✅
  - ~~延後原因：目前同步直寫 UI 路徑過多，現階段有 race / 延遲風險~~ ✅
- ~~第二優先：~~ ✅
  - ~~定義 IME 側「單一狀態來源」最小落地範圍~~ ✅
  - ~~其正式範圍已確定，後續重點改為接線落地與風險控管~~ ✅
- ~~第三優先：~~ ✅
  - ~~依最新排序維持高依賴 panel 暫緩：~~ ✅
    - ~~`THEME`~~ ✅
    - ~~`CLAW`~~ ✅
    - ~~`REMOTE`~~ ✅
    - ~~`HANDWRITING`~~ ✅
    - ~~`MUSIC`~~ ✅
    - ~~`USER_DICT`~~ ✅
- ~~明確不做：~~ ✅
  - ~~不直接和 `Claude CodeB` 搶改同一批 `app/ime/*` helper~~ ✅
  - ~~不直接進 `USER_DICT`~~ ✅
  - ~~不直接碰 `handleKeyPress()` / `applyInputResult()` 主語意~~ ✅
  - ~~不主動切到 `KeyboardView.kt`~~ ✅

## CodeXB

### 最高優先

- [x] ~~等待並確認 `SettingsActivity.kt` 的並行修改回到可編譯狀態~~ ✅
- [x] ~~重新執行 `:app:compileDebugKotlin`~~ ✅
- [x] ~~確認目前 compile break 是否已排除，區分：~~ ✅
  - settings 並行修改問題
  - IME 重構問題

### IME 重構待辦

- [x] ~~對目前 Sprint 2 結構做一次 review：~~ ✅
  - `ImeSessionHooks`
  - `ImePanelCoordinator`
  - `ImePanelHost`
  - `ImePanelCleanup`
  - `ImeBasicPanelFactory`
  - `ImePanelFactory`

- [x] ~~檢查 `showPanel()` 內尚未搬出的高依賴 panel，重新排序風險：~~ ✅ 已更新為 `THEME / CLAW / REMOTE / HANDWRITING / MUSIC / USER_DICT`
  - `USER_DICT`
  - `STICKER`
  - `THEME`
  - `CLAW`
  - `REMOTE`
  - `HANDWRITING`
  - `MUSIC`

- [x] ~~若 compile 已恢復，再評估第三批 panel factory 的候選~~ ✅
- [x] ~~優先考慮是否先做 `USER_DICT` 或 `STICKER`~~ ✅ 已完成 `STICKER`，`USER_DICT` 維持只做風險評估
- [x] ~~暫不優先碰 `THEME / CLAW / REMOTE / HANDWRITING`~~ ✅ 遵守

### 結構整理待辦

- [x] ~~檢查 `YukiBoardIMEService.kt` 中仍殘留的 panel-specific reference 是否可再縮減~~ ✅ 已透過 `ImePanelBindings` 收斂
- [x] ~~檢查 `showPanel()` 內的 fallback 區塊是否需要依 panel 類型拆成更清楚的小函式~~ ✅ 已推進 `ImePanelRouter`
- [x] ~~評估是否需要新增 `ImePanelBindings` 或等價結構來收斂 panel reference~~ ✅ 已完成 `ImePanelBindings`

### 驗證待辦

- compile 綠燈後，補一次 IME 面板切換 smoke review：
  - `EMOJI`
  - `KAOMOJI`
  - `CLIPBOARD`
  - `CALCULATOR`
  - `PHRASE`
  - `COLOR_PICKER`
  - `UNIT_CONVERTER`
  - `TRANSLATE`
  - `GIF`
  - `DECRYPT`

- 確認以下行為沒有因重構回歸：
  - panel 開關
  - panel 關閉後鍵盤焦點恢復
  - composing text 在切 panel 時的處理
  - clipboard panel input mode 路徑

### 明確不做

- 0414 這輪不主動做 repo 整理
- 0414 這輪不主動做 Firebase 版本更新
- 0414 這輪不主動碰 Claude Code 正在拆的 settings section 細節

### CodeXB（0415 補記，已完成）

- [x] ~~修正 `IME_STATE_CONVERGENCE_DRAFT.md` / `BIG_FILE_REFACTOR_PLAN_0414.md` / `todo_list_0414_new.md` 的 Sprint 3 Phase 1 描述，將第一批欄位收斂為 `activePanel` + `isVoiceInputActive`~~ ✅
- [x] ~~在文件中明寫 `composingText / candidates` 延後，原因是同步直寫 UI 路徑過多，現階段有 race / 延遲風險~~ ✅
- [x] ~~確認 `IME_SMOKE_CHECKLIST.md` 已與 Phase 1 / Phase 2 邊界一致~~ ✅
- [x] ~~新增 `REPO_READY_IME_RISK_SUMMARY_2026-04-15.md`，整理 repo 前 IME 風險摘要~~ ✅
- [x] ~~將 `REPO_READY_IME_RISK_SUMMARY_2026-04-15.md` 反向掛回 `REPO_READY_EXECUTION_PLAN_2026-04-15.md`~~ ✅

---

## Claude CodeA

### Settings 拆分收尾

- [ ] SettingsActivity 剩餘清理：（可選優化，延後）
  - `onCreateInternal` 裡的標題列/按鈕列/語言下拉選單（~70 行）可考慮抽成 `HeaderSection`
  - `showLegalWebView` 可考慮搬到 `LegalSettingsSection` 內（目前透過 lambda 傳入）
  - 測試按鈕（~15 行）可考慮抽成獨立 section
  - 以上為可選優化，SettingsActivity 已達標（455 行 < 500 行警戒線）

### 功能驗證待辦

- [x] ~~設定頁完整 smoke test（所有 22 個 section 的顯示與互動）~~ ✅ 已撰寫 `RULE/SETTINGS_SMOKE_CHECKLIST.md` repo-ready 最終版
- [x] ~~重點驗證 BackgroundSettingsSection~~ ✅ 已納入 checklist Section 3.1
- [x] ~~驗證 Codex 回報的 compile break 已解決（`REQUEST_PICK_BG_IMAGE`/`VIDEO` 已移到 `BackgroundSettingsSection.companion`）~~ ✅ compile 通過

### Settings 整合入口收斂（0415 新增，已完成）

- [x] ~~盤點 5 個 section 整合入口與狀態來源~~ ✅
- [x] ~~統一 session 驗證（verifySessionAsync）~~ ✅ Auth/Sync section 不再各自 fetchMe
- [x] ~~收斂 SpeechSettingsHelper AIProviderManager.getInstance()~~ ✅ 改為 lambda 注入
- [x] ~~評估 FraudModelManager 邊界~~ ✅ 結論：不需收斂
- [x] ~~SpeechSettingsHelper → SpeechSettingsSection 轉換~~ ✅ 舊檔已刪除，raw Thread → coroutine 修正
- [x] ~~SpeechSettingsSection 內抽出共用 buildDropdownButton() 消除 3 處重複 selector UI~~ ✅
- [x] ~~InputBehaviorSection 脫鉤語音設定（移除 speechWidgets lambda）~~ ✅ 128→118 行
- [x] ~~SETTINGS_SMOKE_CHECKLIST.md 更新為 repo-ready 最終版~~ ✅ 含依賴邊界地圖 + runtime 風險標記
- [x] ~~SETTINGS_BOUNDARY.md 撰寫~~ ✅
- [x] ~~BackgroundSettingsSection dead code 清除（showVideoFirstFrame 18 行）~~ ✅
- [x] ~~全 22 section 逐檔 smoke review（行數、pattern、跨層耦合檢查）~~ ✅
- [x] ~~SettingsScreenCoordinator 新增 getApiKey() 對外暴露~~ ✅
- [x] ~~UserSessionRepository 新增 verifySession() / getApiKey() / VerifiedSession data class~~ ✅
- [x] ~~SettingsUiState 新增 4 個 verified 欄位~~ ✅
- [x] ~~Repo-ready 收尾文檔收口（REPO_READY_EXECUTION_PLAN Section 3.2 完成條件全部達標）~~ ✅

### 登入 Bug（延後）

- [ ] 登入成功後設定頁不顯示帳號/方案/角色 — 待查 Logcat
  - 使用者指示：功能跟 bug 後面做，先打樁

### 結構觀察

- [x] ~~觀察 Codex Sprint 2 的 IME panel 結構是否穩定，確認不衝突~~ ✅ 已確認穩定
- [x] ~~若 Codex 完成 Sprint 1 骨架（UiState/Coordinator/Repository），準備依骨架搬遷 settings section~~ ✅ 已依骨架完成 coordinator/repository 收斂

### 明確不做

- ~~0414 這輪不主動做 repo 整理~~ ✅ 遵守
- ~~0414 這輪不主動做 Firebase 版本更新~~ ✅ 遵守
- ~~0414 這輪不主動碰 Codex 正在做的 IME panel 重構~~ ✅ 遵守

---

## CodeXA

### 監督任務

- [ ] 持續擔任架構監督角色，不直接和 `Claude CodeA` / `CodeXB` 搶同一批檔案
- [ ] 每輪檢查兩邊回報，確認是否仍符合：
  - `CODEX.md`
  - `CLAUDE_CODEX_COLLAB_GUIDE.md`
  - `RULE/AI_PROTOCOL.md`
  - `RULE/LOCK_RULES.md`

- [ ] 持續監督：
  - `Claude CodeA` 的 settings 拆分是否繼續變瘦，而不是只搬檔
  - `CodeXB` 的 IME panel / lifecycle 重構是否仍維持薄切，不要一次把高耦合 panel 全搬爆

### 對 Claude CodeA 的後續監督重點

- [ ] 觀察 `BackgroundSettingsSection.kt` 是否需要再二次切分
  - 背景選取
  - 預覽
  - 顏色選擇器
  - lifecycle / result handling

- [ ] 觀察 `TtsSettingsSection.kt` 是否仍有過重 callback 留在 `SettingsActivity`

- [ ] 確認 `SettingsActivity` 後續維持在健康範圍，不再反彈膨脹

### 對 CodeXB 的後續監督重點

- [ ] 監督 `ImePanelFactory` 下一批搬遷是否遵守低風險優先
- [ ] 若他要繼續搬 panel factory，優先支持：
  - `USER_DICT`
  - `STICKER`
  - 視情況再評估其他中等依賴 panel

- [ ] 明確繼續暫緩：
  - `THEME`
  - `CLAW`
  - `REMOTE`
  - `HANDWRITING`
  - 其他高度依賴 service / manager / external integration 的 panel

- [ ] 監督他是否開始誤碰高風險輸入主流程：
  - `handleKeyPress()`
  - `applyInputResult()`
  - 主要 composing / candidate 核心路徑

### 文件與規則維護

- [x] ~~若兩邊的實際架構方向有新共識，更新：~~
  - `CODEX.md`
  - `ARCHITECTURE_REFACTOR_PLAN.md`
  - 必要時更新 `RULE/`

- [x] ~~持續把新的監督結論寫進日誌與待辦，讓下個 `CodeXA` 對話延續接手~~

### 今日已完成但原先未列入的 CodeXA 任務

- [x] ~~審核 `handleKeyPress()` / `applyInputResult()`，正式否決 `composingText / candidates` 提前進入 `Sprint 3 Phase 1`~~
- [x] ~~確認 `Sprint 1`、`Sprint 2` 已完成，`Sprint 3 Phase 1` 已完成，並把後續 Sprint / Phase 路線整理成主控文件~~
- [x] ~~建立 `SPRINT_MASTER_PLAN_2026-04-15.md`，統一後續幾天的 Sprint / Phase / 分工主線~~
- [x] ~~檢查 `YukiBoard ↔ Insight` 對接面是否被本輪重構改壞，確認 auth / sticker / token exchange 無新增 blocking 問題~~
- [x] ~~執行 repo hygiene，整理根目錄 `md` 與 `docs/` 結構，修正現行工作檔連結~~
- [x] ~~完成 repo-ready 推進並將相關提交推送到 `origin/main`~~
- [x] ~~新增 `daily_works_0414.md`，記錄本日 `CodeX A` 工作日誌並標記下次從 `SPRINT_MASTER_PLAN_2026-04-15.md` 開始~~

### 明確不做

- 0414 這輪 `CodeXA` 不主動做 repo 整理
- 0414 這輪 `CodeXA` 不主動做 Firebase 版本更新
- 0414 這輪 `CodeXA` 不主動和 `Claude CodeA` / `CodeXB` 競爭同一批核心檔案

---

## Phase 2 前置與後續路線（2026-04-15 最新判定）

### 現在的正式判定

- `Phase 1`：已完成
- `Phase 2`：尚未 ready，只能做前置任務
- `Phase 3+`：不得提前展開

原因：

- `composingText / candidates` 的同步直寫 UI 路徑仍過多
- `handleKeyPress()` / `applyInputResult()` 仍是同步熱路徑
- 現階段若直接 collect 化，會有 race / 延遲風險

### CodeX A

- [ ] 監督重點改為：
  - 是否有人誤把 `composingText / candidates` 提前 collect 化
  - 是否真的在做 `Phase 2` 前置，而不是偷跑 `Phase 2` 實作
  - 是否持續維持不碰 `handleKeyPress()` / `applyInputResult()` 主語意
- [x] ~~下一輪輸出一份短版判定：~~
  - `Phase 2 ready`
  - 或 `Phase 2 not ready`
  - 並附不超過 3 條理由

### Claude Code A

- [ ] settings 側不再追 provider 小收斂
- [ ] 下一輪改做 repo-ready 收尾：
  - 更新 `RULE/SETTINGS_SMOKE_CHECKLIST.md`
  - 補一份 settings 側最終風險摘要
  - 確保 `SettingsActivity.kt` 不再反彈
- [ ] 不碰 IME

### Claude Code B

- [x] ~~只做 `Phase 2` 前置盤點，不做 collect 實作~~ ✅
- [x] ~~具體任務：~~ ✅
  - ~~盤點所有 `keyboardView.updateComposingText(...)` 寫入點~~ ✅ 20 處，記錄於 `RENDER_IME_UI_STATE_DRAFT.md`
  - ~~盤點所有 `keyboardView.updateCandidates(...)` 寫入點~~ ✅ 26 處，記錄於 `RENDER_IME_UI_STATE_DRAFT.md`
  - ~~分類為：~~ ✅ 已分析時序風險（熱路徑同步 vs async collect 衝突）
    - ~~熱路徑同步更新~~ ✅
    - ~~panel / gesture 特例~~ ✅
    - ~~callback 清理路徑~~ ✅
  - ~~提出單一 render 入口草案，但不改主流程~~ ✅ `renderImeUiState()` Phase 1 已實作，Phase 2 草案已寫
- [x] ~~不碰：~~ ✅ 全部遵守
  - ~~`handleKeyPress()`~~ ✅
  - ~~`applyInputResult()`~~ ✅
  - ~~`KeyboardView.kt`~~ ✅

### Claude Code B — Repo Ready 3.3 任務（2026-04-15 新增）

- [x] ~~Sprint 3 Phase 1 實作：`renderImeUiState()` + `startUiStateCollect()` + `uiStateCollectJob` lifecycle~~ ✅
- [x] ~~移除 8 處直接寫 UI 路徑（2 處 toolbarView.activePanel + 6 處 voiceListeningLocale）~~ ✅
- [x] ~~更新 `IME_SMOKE_CHECKLIST.md` — Phase 1 檢查項獨立標明~~ ✅
- [x] ~~明寫目前已 collect 驅動的只有 activePanel + isVoiceInputActive~~ ✅
- [x] ~~明寫 composingText / candidates 仍是同步直寫 UI~~ ✅
- [x] ~~補 Phase 1 Runtime 驗證重點（面板高亮 / 語音指示燈 / collect lifecycle）~~ ✅ 16 項 runtime unverified
- [x] ~~撰寫 `RENDER_IME_UI_STATE_DRAFT.md` 消費端草案~~ ✅
- [x] ~~`:app:compileDebugKotlin` BUILD SUCCESSFUL~~ ✅

### CodeX B

- [ ] 只做 `Phase 2` 架構前置決策，不做實作
- [ ] 具體任務：
  - 定義 `composingText / candidates` 若要進 `Phase 2`，需要滿足哪些條件
  - 新增 `PHASE2_READINESS_CHECK.md` 或等價文件
  - 明確列出：
    - 哪些直接寫 UI 路徑必須先收掉
    - 哪些仍可暫時保留
    - 哪些路徑屬於熱路徑，不能引入 collect 延遲
- [ ] 不碰：
  - `USER_DICT`
  - `KeyboardView.kt`
  - 高依賴 panel

---

## Phase 2 之後還有什麼

### Phase 2

目標：

- `composingText`
- `candidates`

啟動條件：

- 單一 render 入口已存在
- 所有同步直寫 UI 路徑已盤乾淨
- 熱路徑與 collect 邊界已釐清
- `handleKeyPress()` / `applyInputResult()` 已再次審核

### Phase 3

目標：

- `remoteConnectionState`
- `isFloatingMode`
- `isPanelBehindKeyboard`
- `isInGameMode`

說明：

- 這一階段是 container / mode / external state 的收斂
- 仍先由 `YukiBoardIMEService` 當 consume owner
- 不急著讓 `KeyboardView.kt` 自己 collect

### Phase 4

目標：

- 開始處理 integration-heavy panel 的新邊界

候選順序：

1. `THEME`
2. `CLAW`
3. `REMOTE`
4. `HANDWRITING`
5. `MUSIC`
6. `USER_DICT`

### Phase 5

目標：

- 重新審 `handleKeyPress()`
- 重新審 `applyInputResult()`
- 判斷能否把 service 再降成更薄的 orchestration shell

這一階段才是：

- 真正的輸入主流程重構
- 更深的 coordinator-first 收斂
- 是否讓 `KeyboardView.kt` 進一步降責

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

- 先維持 IME 區完全避碰，不主動修改 `YukiBoardIMEService.kt`
- `SettingsActivity.kt` 已降到健康範圍，這輪不要再回填大段邏輯進去
- 優先處理 `BackgroundSettingsSection.kt` 的二次切分設計，至少先拆分責任邊界：
  - 背景選取 / 匯入處理
  - 預覽與 matrix / transform
  - 顏色選擇器
  - lifecycle / receiver / result handling
- 檢查 `TtsSettingsSection.kt` 是否還保留過重 callback 依賴；若有，優先把測試流程往 section 內或 helper/coordinator 收斂
- 補設定頁 smoke test，優先驗證：
  - `BackgroundSettingsSection`
  - `TtsSettingsSection`
  - auth 顯示狀態是否仍正確刷新
- 登入 bug 仍維持延後，不在這輪展開

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

- 主軸放在 `settings / sync / auth / provider`，不要碰 IME 主流程
- 優先盤點並收斂 settings 端目前分散的整合入口：
  - `AuthSettingsSection`
  - `SyncSettingsSection`
  - `InsightSettingsSection`
  - `SpeechSettingsHelper`
  - `AISettingsSection`
- 目標不是重寫功能，而是先建立較單一的 settings integration 邊界：
  - 優先透過 `SettingsScreenCoordinator`
  - `UserSessionRepository`
  - 必要時新增 settings 側 helper / adapter / gateway
- 優先收斂的狀態來源：
  - 登入狀態
  - sync 狀態
  - provider 選擇狀態
  - settings 頁上的 warning / status 文案
- 明確不做：
  - 不回頭膨脹 `SettingsActivity.kt`
  - 不主動碰 `YukiBoardIMEService.kt`
  - 不直接大改雲同步資料格式
- 最小驗證保護：
  - 補一份 settings 側 smoke checklist 文件
  - 至少涵蓋：
    - 登入/登出顯示
    - sync 啟用/停用
    - AI provider 顯示/切換
    - Whisper / Faster-Whisper 連線測試入口
    - 貼圖/Insight 狀態顯示

### 給 Claude CodeB 的明確任務

- 主軸放在 `IME / panel / service / integration adapter`，不要碰 settings 區
- 先不要再追行數，改做 IME 側整合層與狀態來源收斂
- 優先盤點並收斂 IME 側目前分散的整合入口：
  - `ImeImageCommitter`
  - `ImeVoiceController`
  - `ImeThemeRenderDelegate`
  - `ImeFraudModelInit`
  - `ImePanelFactory`
  - `CloudSyncManager` 在 IME session end 的接點
- 目標是讓 `YukiBoardIMEService` 更像接線殼：
  - service 只持有 platform/lifecycle 接線
  - 整合副作用優先往 `app/ime/*` helper 收
- 優先收斂的狀態來源：
  - panel selection
  - voice listening state
  - composing / candidate 同步到 `ImeSessionCoordinator`
  - prefs -> view 同步入口（已存在的 `syncPrefsToViews()` 繼續集中）
- 明確不做：
  - 不碰 `handleKeyPress()` 主語意
  - 不碰 `applyInputResult()` 主語意
  - 不進 `USER_DICT`
  - 不切到 `KeyboardView.kt` 新主題
- 最小驗證保護：
  - 補一份 IME 側 smoke checklist 文件
  - 至少涵蓋：
    - 基本輸入
    - 候選字
    - panel 開關
    - `STICKER`
    - `GIF`
    - `TRANSLATE`
    - `DECRYPT`
    - 語音輸入開始/停止
    - session 結束同步觸發點

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

- 主軸：繼續收斂 settings 側 integration boundary 與 state source
- 下一步優先做：
  - 收斂 `SpeechSettingsHelper` 內的 `AIProviderManager.getInstance()`
  - 優先改成 constructor 注入，或經 `SettingsScreenCoordinator` / `UserSessionRepository` 提供狀態
- 第二優先：
  - 盤點 `AISettingsSection` 裡 `FraudModelManager` 的使用方式
  - 評估是否應抽成 helper / gateway / coordinator 接點，而不是 section 直接掌控
- 第三優先：
  - 用 [RULE/SETTINGS_SMOKE_CHECKLIST.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SETTINGS_SMOKE_CHECKLIST.md) 為基準，補「狀態來源收斂前後對照註記」
  - 明確標出哪些顯示仍靠本地快取，哪些已改走 verified session
- 明確不做：
  - 不回頭膨脹 `SettingsActivity.kt`
  - 不主動碰 `YukiBoardIMEService.kt`
  - 不改 auth / sync 後端協定

### Claude CodeB

- 主軸：不要再追低價值 helper 搬遷，改做 IME 狀態來源收斂的下一步準備
- 下一步優先做：
  - 依 [IME_STATE_CONVERGENCE_DRAFT.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/IME_STATE_CONVERGENCE_DRAFT.md) 的正式決策，準備 Sprint 3 consume 端接線草案
  - 第一批只處理：
    - `activePanel`
    - `isVoiceInputActive`
  - `composingText / candidates` 延後，只做風險盤點，不進 Phase 1 接線
  - 延後原因：目前同步直寫 UI 路徑過多，現階段有 race / 延遲風險
- 第二優先：
  - 檢查目前 `YukiBoardIMEService.kt` 仍直接讀寫的 `prefs.xxx` 與 view state，同步列出可收斂到 coordinator 的候選
  - 但只做盤點/草案，不直接大改
- 第三優先：
  - 用 `IME_SMOKE_CHECKLIST.md` 補一版「目前因模擬器不可用而待驗」標記，明確區分：
    - compile-safe
    - static wiring checked
    - runtime unverified
- 明確不做：
  - 不碰 `handleKeyPress()` 主語意
  - 不碰 `applyInputResult()` 主語意
  - 不進 `USER_DICT`
  - 不切去 `KeyboardView.kt`
  - 不主動搬 `THEME / CLAW / REMOTE / HANDWRITING / MUSIC`

### CodeXB

- 主軸：維持 Codex 路線，負責高一層的架構校正與 Sprint 3 準備，不和 `Claude CodeB` 重疊做同一批細節搬遷
- 下一步優先做：
  - 依 [IME_STATE_CONVERGENCE_DRAFT.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/IME_STATE_CONVERGENCE_DRAFT.md) 主導 Sprint 3 第一批正式接線
  - consume owner 維持在 `YukiBoardIMEService`
  - 第一批只接：
    - `activePanel`
    - `isVoiceInputActive`
  - `composingText / candidates` 延後，只做風險控管與文件同步，不進 Phase 1 接線
  - 延後原因：目前同步直寫 UI 路徑過多，現階段有 race / 延遲風險
- 第二優先：
  - 定義 IME 側「單一狀態來源」最小落地範圍
  - 其正式範圍已確定，後續重點改為接線落地與風險控管
- 第三優先：
  - 依最新排序維持高依賴 panel 暫緩：
    - `THEME`
    - `CLAW`
    - `REMOTE`
    - `HANDWRITING`
    - `MUSIC`
    - `USER_DICT`
- 明確不做：
  - 不直接和 `Claude CodeB` 搶改同一批 `app/ime/*` helper
  - 不直接進 `USER_DICT`
  - 不直接碰 `handleKeyPress()` / `applyInputResult()` 主語意
  - 不主動切到 `KeyboardView.kt`

## CodeXB

### 最高優先

- 等待並確認 `SettingsActivity.kt` 的並行修改回到可編譯狀態
- 重新執行 `:app:compileDebugKotlin`
- 確認目前 compile break 是否已排除，區分：
  - settings 並行修改問題
  - IME 重構問題

### IME 重構待辦

- 對目前 Sprint 2 結構做一次 review：
  - `ImeSessionHooks`
  - `ImePanelCoordinator`
  - `ImePanelHost`
  - `ImePanelCleanup`
  - `ImeBasicPanelFactory`
  - `ImePanelFactory`

- 檢查 `showPanel()` 內尚未搬出的高依賴 panel，重新排序風險：
  - `USER_DICT`
  - `STICKER`
  - `THEME`
  - `CLAW`
  - `REMOTE`
  - `HANDWRITING`
  - `MUSIC`

- 若 compile 已恢復，再評估第三批 panel factory 的候選
- 優先考慮是否先做 `USER_DICT` 或 `STICKER`
- 暫不優先碰 `THEME / CLAW / REMOTE / HANDWRITING`

### 結構整理待辦

- 檢查 `YukiBoardIMEService.kt` 中仍殘留的 panel-specific reference 是否可再縮減
- 檢查 `showPanel()` 內的 fallback 區塊是否需要依 panel 類型拆成更清楚的小函式
- 評估是否需要新增 `ImePanelBindings` 或等價結構來收斂 panel reference

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

---

## Claude CodeA

### Settings 拆分收尾

- [ ] SettingsActivity 剩餘清理：
  - `onCreateInternal` 裡的標題列/按鈕列/語言下拉選單（~70 行）可考慮抽成 `HeaderSection`
  - `showLegalWebView` 可考慮搬到 `LegalSettingsSection` 內（目前透過 lambda 傳入）
  - 測試按鈕（~15 行）可考慮抽成獨立 section
  - 以上為可選優化，SettingsActivity 已達標（442 行 < 800 行目標）

### 功能驗證待辦

- [ ] 設定頁完整 smoke test（所有 21 個 section 的顯示與互動）
- [ ] 重點驗證 BackgroundSettingsSection：
  - 選圖片/選影片/清除背景
  - 遮罩深度/顯示比例/旋轉/位置滑桿
  - 鍵盤高度/按鍵大小/透明度滑桿
  - 鍵盤完整預覽即時更新
  - 字體顏色選擇器（預設色 + 自訂 RGB）
  - 影片預覽播放/暫停（lifecycle）
  - 透明鍵盤開關 + 透明度
- [ ] 驗證 Codex 回報的 compile break 已解決（`REQUEST_PICK_BG_IMAGE`/`VIDEO` 已移到 `BackgroundSettingsSection.companion`）

### 登入 Bug（延後）

- [ ] 登入成功後設定頁不顯示帳號/方案/角色 — 待查 Logcat
  - 使用者指示：功能跟 bug 後面做，先打樁

### 結構觀察

- [ ] 觀察 Codex Sprint 2 的 IME panel 結構是否穩定，確認不衝突
- [ ] 若 Codex 完成 Sprint 1 骨架（UiState/Coordinator/Repository），準備依骨架搬遷 settings section

### 明確不做

- 0414 這輪不主動做 repo 整理
- 0414 這輪不主動做 Firebase 版本更新
- 0414 這輪不主動碰 Codex 正在做的 IME panel 重構

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

- [ ] 若兩邊的實際架構方向有新共識，更新：
  - `CODEX.md`
  - `ARCHITECTURE_REFACTOR_PLAN.md`
  - 必要時更新 `RULE/`

- [ ] 持續把新的監督結論寫進日誌與待辦，讓下個 `CodeXA` 對話延續接手

### 明確不做

- 0414 這輪 `CodeXA` 不主動做 repo 整理
- 0414 這輪 `CodeXA` 不主動做 Firebase 版本更新
- 0414 這輪 `CodeXA` 不主動和 `Claude CodeA` / `CodeXB` 競爭同一批核心檔案

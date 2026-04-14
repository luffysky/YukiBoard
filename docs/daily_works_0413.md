# daily_works_0413

## CodeXB

### 今日完成

- 讀取並建立固定基線：
  - `CODEX.md`
  - `PROJECT_HEALTH_CHECK_2026-04-13.md`
  - `ARCHITECTURE_REFACTOR_PLAN.md`
  - `CLAUDE_CODEX_COLLAB_GUIDE.md`
  - `CODEX_HANDOFF.md`
  - `CODEX_REPLY_TO_CLAUDE_CODE_2026-04-13.md`
  - `CODEX_HANDOFF_SPRINT1_5_2026-04-13.md`

- 檢查目前工作樹與並行修改狀態：
  - 確認 `SettingsActivity.kt` 與 `ui/settings/section/*` 正在被拆分
  - 決定避開 settings section 細節，集中推進 IME 端 Sprint 2

- 完成環境驗證與最小編譯檢查：
  - 找到可用 JDK：`C:\Program Files\Android\Android Studio\jbr`
  - 以 workspace-local `GRADLE_USER_HOME` / `ANDROID_USER_HOME` 跑 `:app:compileDebugKotlin`
  - 初次驗證時 Sprint 1 / 1.5 骨架可正常編譯

- 新增 IME session lifecycle 切割：
  - 新增 `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionHooks.kt`
  - 將 `onStartInputView`
  - `onFinishInputView`
  - `onFinishInput`
  - `onDestroy`
  - 的 session reset / cleanup / sync / teardown 收斂到 hooks

- 新增 IME panel 決策切割：
  - 新增 `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelCoordinator.kt`
  - 收斂 `handlePanelToggle` 的決策
  - 收斂 `activePanel` / `toolbarView.activePanel` / `imeCoordinator.showPanel()` 的同步

- 新增 IME panel host 切割：
  - 新增 `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelHost.kt`
  - 收斂 `panelContainer` / `topPanelContainer` 的清空、掛載、控制列顯示責任

- 新增 IME panel cleanup 切割：
  - 新增 `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelCleanup.kt`
  - 收斂 `showPanel()` / `closePanel()` 的 cleanup、input reset、overlay restore、focus restore

- 完成 panel factory 第一批：
  - 新增 `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeBasicPanelFactory.kt`
  - 先收低依賴 panel：
    - `EMOJI`
    - `KAOMOJI`
    - `CLIPBOARD`
    - `CALCULATOR`
    - `PHRASE`
    - `COLOR_PICKER`
    - `UNIT_CONVERTER`
  - `showPanel()` 先問 factory，建不到再 fallback 到 service

- 完成 panel factory 第二批：
  - 新增 `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelFactory.kt`
  - 在委派 basic factory 後，再處理中等依賴 panel：
    - `TRANSLATE`
    - `GIF`
    - `DECRYPT`
  - 保持 `showPanel()` 的 fallback 機制，不一次搬光全部 panel

### 今日判斷

- 目前 IME 端的 Sprint 2 切割方向穩定，新的 application-side 結構已形成：
  - `ImeSessionHooks`
  - `ImePanelCoordinator`
  - `ImePanelHost`
  - `ImePanelCleanup`
  - `ImeBasicPanelFactory`
  - `ImePanelFactory`

- 目前不宜繼續碰：
  - `THEME`
  - `CLAW`
  - `REMOTE`
  - `HANDWRITING`
  - `USER_DICT`
  - `STICKER`
  - `MUSIC`

- 今日最後一次 compile 驗證時，專案編譯失敗點不在 IME，而在 `SettingsActivity.kt` 的並行修改：
  - `REQUEST_PICK_BG_IMAGE` unresolved
  - `REQUEST_PICK_BG_VIDEO` unresolved

### 備註

- 今日依要求未做 repo 整理
- 今日依要求未做 Firebase 版本更新或上傳
- 今日僅做本地重構與本地 compile 驗證

---

## Claude CodeA

### 今日完成

#### SettingsActivity 拆分（3,868 → 442 行，-88%）

**第一輪（前次 session 開始，本次 session 完成接線）：**

- `AnimationSettingsSection.kt` (~185 行) — 流光/角色/VRM/粒子/震動/音效開關 + 動畫主題選擇器 + 音效風格選擇器
- `InputBehaviorSection.kt` (~130 行) — 輸入行為開關 + 詞庫匯出匯入按鈕 + 語音 widgets 注入
- `TtsSettingsSection.kt` (~310 行) — TTS 引擎選擇器 + Google Cloud API key + 語音搜尋/篩選 + 語速滑桿 + 測試按鈕
- `SnippetSettingsSection.kt` (~170 行) — 自訂 snippet CRUD + 內建 snippet 可折疊列表
- `InputMethodSection.kt` (~100 行) — 輸入法 checkbox 列表（英文/注音/拼音/倉頡/速成/日文/韓文）
- `LanguagePackSection.kt` (~180 行) — 啟用輸入法 + 可下載語言包
- `ThemeSettingsSection.kt` (~75 行) — 主題 RadioGroup
- `AppearanceSection.kt` (~80 行) — 外觀模式 RadioGroup
- `LanguageSection.kt` (~80 行) — 語言 RadioGroup + Activity 重啟
- `SpeechSettingsHelper.kt` (~340 行) — 語音引擎/語言/靜音靈敏度選擇器 + Whisper/Faster-Whisper 連線測試 + WAV 產生器
- `SmartInputSection.kt` 更新 — 將 `buildCandidateColorLegend` 移入 class 內部，移除外部 lambda

**第二輪（本次 session）：**

- `BackgroundSettingsSection.kt` (~1,254 行) — **最大塊提取**，包含：
  - 全部 ~20 個背景相關 instance fields
  - `buildBackgroundCard` + 所有滑桿（遮罩深度/顯示比例/旋轉/位置 XY/鍵盤高度/按鍵大小/按鍵透明度/文字透明度）
  - 影片預覽系統（`startVideoPreview`/`stopVideoPreview`/`showVideoFirstFrame`/`updateVideoPreviewTransform`）
  - 鍵盤完整預覽（`buildFullKeyboardPreview` + Canvas onDraw 繪製迷你鍵盤）
  - 字體顏色選擇器（`buildKeyLabelColorPicker`/`showCustomColorPicker`/色塊色環/自訂 RGB 對話框）
  - `syncBgSliders` + `refreshBgPreview` + `reloadFullPreviewBitmap` + `updateFullPreviewHeight`
  - Lifecycle hooks（`onPause`/`onResume`/`onStart`/`onStop`/`onDestroy`）委派
  - `handleBgPickResult`（背景圖片/影片 `onActivityResult` 處理 + MIME 偵測 + 複製到 bgDir）
  - companion object 常數 `REQUEST_PICK_BG_IMAGE` / `REQUEST_PICK_BG_VIDEO`

**清理：**
- 移除 SettingsActivity 中不再使用的 `cardGroup`/`simpleCard`/`switchRow`/`styledButton` 方法（已全面改用 `SettingsUiHelper`）
- 移除不再使用的 imports（okhttp3、Canvas、Color、SeekBar、Switch、FrameLayout、RadioButton、RadioGroup、AppCompatDelegate、Configuration、Locale 等）

#### 累計 section 數量：21 個

| Section | 行數 |
|---------|------|
| AISettingsSection | ~200 |
| AnimationSettingsSection | ~185 |
| AppearanceSection | ~80 |
| AuthSettingsSection | ~120 |
| **BackgroundSettingsSection** | **~1,254** |
| ClipboardSettingsSection | ~80 |
| GameSettingsSection | ~100 |
| InputBehaviorSection | ~130 |
| InputMethodSection | ~100 |
| InsightSettingsSection | ~150 |
| LanguagePackSection | ~180 |
| LanguageSection | ~80 |
| LegalSettingsSection | ~100 |
| SensorSettingsSection | ~120 |
| SmartInputSection | ~75 |
| SnippetSettingsSection | ~170 |
| SpeechSettingsHelper | ~340 |
| SyncSettingsSection | ~200 |
| ThemeSettingsSection | ~75 |
| ToolbarSettingsSection | ~80 |
| TtsSettingsSection | ~310 |
| YukiBehaviorSection | ~100 |

### 今日判斷

- SettingsActivity 已從 3,868 行降到 **442 行**，遠低於 Codex 的 800 行目標
- 現在 SettingsActivity 只剩：`onCreate`/`onCreateInternal`（section 接線）+ `showLegalWebView` + `dp` + 權限處理 + lifecycle 委派
- Codex 回報的 compile break（`REQUEST_PICK_BG_IMAGE`/`REQUEST_PICK_BG_VIDEO` unresolved）已解決：常數移到 `BackgroundSettingsSection.companion`
- Build 已通過 ✅

### 備註

- 今日依要求未做 repo 整理
- 今日依要求未做 Firebase 版本更新或上傳
- 今日僅做本地重構與本地 compile 驗證

---

## CodeXA

### 今日完成

- 建立並整理整個專案的協作基線文件：
  - `CODEX.md`
  - `PROJECT_HEALTH_CHECK_2026-04-13.md`
  - `ARCHITECTURE_REFACTOR_PLAN.md`
  - `CLAUDE_CODEX_COLLAB_GUIDE.md`
  - `CODEX_SESSION_GUIDE.md`
  - `CODEX_REPLY_TO_CLAUDE_CODE_2026-04-13.md`
  - `CODEX_HANDOFF_SPRINT1_5_2026-04-13.md`

- 完成專案健檢與架構判斷：
  - 確認 `YukiBoardIMEService.kt` 與 `SettingsActivity.kt` 是兩個主要超級控制器
  - 確認目前最大問題是架構承載力，不是功能不足
  - 明確定義後續方向為：
    - 先立骨架
    - 再瘦 IME / Settings
    - 再收斂狀態與 integration boundary

- 建立 Sprint 1 骨架：
  - `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeUiState.kt`
  - `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionCoordinator.kt`
  - `app/src/main/java/com/snowrealm/yukiboard/app/settings/SettingsUiState.kt`
  - `app/src/main/java/com/snowrealm/yukiboard/app/settings/SettingsScreenCoordinator.kt`
  - `app/src/main/java/com/snowrealm/yukiboard/app/session/UserSessionRepository.kt`

- 完成 Sprint 1.5 最薄接線：
  - `YukiBoardIMEService.kt` 接上 `ImeSessionCoordinator`
  - `SettingsActivity.kt` 接上 `SettingsScreenCoordinator`
  - 不搬大量舊邏輯，只建立可供後續搬遷的正式入口

- 監督與控盤：
  - 檢查 `Claude CodeA` 的 settings section 拆分是否偏航
  - 檢查 `CodeXB` 的 IME panel 薄切是否超 scope
  - 針對兩邊各自的進度給出 review 與後續建議
  - 明確要求：
    - Claude Code 先專注拆 section
    - 另一個 Codex 對話優先做 `ImePanelCoordinator` / panel factory 薄切

- 整理 RULE 協作規則：
  - 重寫 `RULE/AI_PROTOCOL.md`
  - 重寫 `RULE/LOCK_RULES.md`
  - 重寫 `RULE/SPEC_EXAMPLE.md`
  - 重寫 `RULE/TASK_TEMPLATE.md`
  - 讓 `RULE/` 從舊 web 模板，改成適用本專案的 Android / YukiBoard 協作規則

- 將 `RULE/` 正式納入 `CODEX.md` 的共同基線與閱讀入口

### 今日角色

- 今日 `CodeXA` 的角色不是直接主力實作，而是：
  - 架構守門員
  - 協作基線維護者
  - 進度監督者
  - 風險 review 與分工控盤者

- 今日主要責任：
  - 確保 `Claude CodeA` 與 `CodeXB` 不互撞
  - 確保 settings 與 IME 重構都沿同一個架構方向前進
  - 確保後續新對話仍能繼承同樣的共同記憶與規則

### 今日判斷

- `Claude CodeA` 這條線進展很大，`SettingsActivity` 已實質瘦身成功
- `CodeXB` 這條線方向正確，但仍在 IME 骨架與薄切階段
- 現在最重要的是持續監督，不要讓兩邊在後續重構中又把責任塞回核心大檔

### 備註

- 今日依要求未做 repo 整理
- 今日依要求未做 Firebase 版本更新或上傳
- 今日主要工作是基線建立、重構監督、規則整理與 review

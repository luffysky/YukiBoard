# SnowRealmYukiBoard 架構重構實作藍圖

日期: 2026-04-13  
目的: 將目前專案從「高功能密度但高耦合」整理為可持續演進的架構  
原則: 不大爆炸重寫；以漸進式搬遷、先立骨架再移動功能為主

## 1. 重構目標

這次重構不是為了追求形式上的乾淨，而是為了解決四個已經開始拖慢開發的核心問題：

- `YukiBoardIMEService` 承擔過多責任
- `SettingsActivity` 成為第二個超級控制中心
- UI、狀態、流程、外部整合互相纏住
- 缺少穩定的狀態真實來源與清楚的依賴方向

重構完成後要達成的結果：

- 平台層只接 Android lifecycle 與 UI callback
- 功能流程由 coordinator / use case 接手
- 狀態集中管理，不再四處散落
- 外部 API / storage / auth 整合走統一邊界
- 讓核心輸入流程可穩定演進，外掛型功能可隔離降級

## 2. 不做的事

這次重構明確不做以下事情：

- 不全面改 UI 技術棧
- 不一次拆成多個 Gradle module
- 不先追求 100% 測試覆蓋
- 不為了架構重寫所有功能
- 不在第一階段碰所有 feature

這份計畫的核心是「降低爆炸半徑」。

## 3. 目標依賴方向

建議未來依賴方向固定如下：

`platform -> application -> domain <- integration`

說明：

- `platform`
  - Android framework 接線
  - `InputMethodService`、`Activity`、`Receiver`
  - 只能依賴 `application`
- `application`
  - orchestration
  - state store
  - use case 組合
  - 可依賴 `domain` 與 `integration`
- `domain`
  - 純規則、狀態轉換、演算法
  - 不依賴 Android 與網路
- `integration`
  - API、prefs、db、檔案、第三方 SDK
  - 提供 repository / gateway 給 `application`

## 4. 先建立的 package 骨架

即使暫時不拆 module，也建議先照以下結構重整：

- `com.snowrealm.yukiboard.platform.ime`
- `com.snowrealm.yukiboard.platform.settings`
- `com.snowrealm.yukiboard.app.ime`
- `com.snowrealm.yukiboard.app.settings`
- `com.snowrealm.yukiboard.app.session`
- `com.snowrealm.yukiboard.app.sync`
- `com.snowrealm.yukiboard.domain.input`
- `com.snowrealm.yukiboard.domain.theme`
- `com.snowrealm.yukiboard.domain.session`
- `com.snowrealm.yukiboard.integration.network`
- `com.snowrealm.yukiboard.integration.auth`
- `com.snowrealm.yukiboard.integration.sync`
- `com.snowrealm.yukiboard.integration.theme`
- `com.snowrealm.yukiboard.integration.ai`

重點不是名字，而是開始限制責任。

## 5. `YukiBoardIMEService` 拆分方案

### 5.1 目前承擔的責任

從檔案內容可觀察到它同時處理：

- Android lifecycle
- keyboard view 建立
- toolbar / panel 組裝
- theme 套用
- voice input / TTS
- fraud scan
- cloud sync 觸發
- floating mode / one hand mode
- bluetooth / wifi remote keyboard
- game mode
- swipe input
- AI candidate request
- panel input routing
- snippet expansion
- clipboard risk scan

這個類別現在不是 service，而是系統主控制器。

### 5.2 目標拆分後結構

`YukiBoardIMEService` 目標只保留四件事：

- 接 Android lifecycle
- 建立 root view 與注入依賴
- 把 UI callback 轉成 action
- 訂閱 `ImeUiState` 並 render

建議新增以下物件：

#### `ImeSessionCoordinator`

職責：

- 輸入 session 的主流程 owner
- 接收所有 `ImeAction`
- 協調 input、panel、theme、sync、voice、remote、game 子系統
- 發出 `ImeEffect`

它是重構的第一核心。

#### `ImeStateStore`

職責：

- 管理 `ImeUiState`
- 單一真實來源
- 讓 service / view 只觀察 state，不直接拼裝多份狀態

#### `ImeInputOrchestrator`

職責：

- 處理按鍵、候選字、輸入模式、composing、commit
- 封裝 `InputRouter`、`SnippetManager`、輸入引擎互動
- 把 `handleKeyPress()`、`applyInputResult()`、`handleSwipeInput()` 這類流程從 service 抽出去

#### `ImePanelCoordinator`

職責：

- 管理 panel 顯示、切換、輸入目標、panel layer
- 收斂 `showPanel()`、`closePanel()`、`handlePanelToggle()`、`routeKeyToPanel()`

#### `ImeThemeController`

職責：

- 套用主題
- 刷新背景 / 流光 / 粒子 / 角色
- 處理透明模式、一手模式、尺寸變化對 UI 的影響

#### `ImeVoiceCoordinator`

職責：

- 管理 speech input / output
- 處理 overlay 與 engine lifecycle
- 收斂 `toggleVoiceInput()`、`stopVoiceInput()`、`initTtsEngine()`、`ttsSpeak()`

#### `ImeRemoteCoordinator`

職責：

- 收斂 bluetooth / wifi remote keyboard
- 更新 remote panel 狀態
- 對外輸出 remote connection state

#### `ImeGameCoordinator`

職責：

- 管理遊戲模式進出
- 遊戲選單與事件轉譯
- 將遊戲從核心輸入流程隔離

#### `ImeSecurityCoordinator`

職責：

- fraud model 初始化
- 被動掃描 / 回覆掃描 / 剪貼簿掃描
- 泡泡提醒顯示與節流

#### `ImeSessionHooks`

職責：

- `onStartInputView`
- `onFinishInputView`
- `onFinishInput`
- `onDestroy`
- 負責跨 session 的收尾與同步觸發

### 5.3 `YukiBoardIMEService` 第一步實際切法

第一波不要一次拆完，先拆最穩的：

1. `ImeSessionHooks`
2. `ImeInputOrchestrator`
3. `ImePanelCoordinator`
4. `ImeVoiceCoordinator`

原因：

- 這些功能的邊界相對清楚
- 直接降低 service 檔案複雜度
- 對行為最容易做 smoke 驗證

### 5.4 `ImeAction` 建議

把目前散落在 callback 中的事件收斂為明確 action，例如：

- `OnInputViewCreated`
- `OnStartInputView`
- `OnKeyPressed`
- `OnCandidateSelected`
- `OnPanelRequested`
- `OnPanelClosed`
- `OnVoiceToggleRequested`
- `OnThemeChanged`
- `OnSessionFinished`
- `OnRemoteToggleRequested`
- `OnGameModeRequested`

### 5.5 `ImeUiState` 建議欄位

至少先收斂下面這些：

- `inputMode`
- `composingText`
- `candidates`
- `activePanel`
- `isFloatingMode`
- `isPanelBehindKeyboard`
- `isVoiceInputActive`
- `remoteConnectionState`
- `isInGameMode`
- `themeSnapshot`
- `warningBanner`

先有這些，很多現在散在 service 內的布林值就可以慢慢收回來。

## 6. `SettingsActivity` 拆分方案

### 6.1 目前承擔的責任

從掃描結果可見它同時處理：

- 設定首頁組裝
- 多語系切換
- 登入 / 登出
- 雲同步
- AI 功能開關
- AI provider 設定導流
- 防詐模型更新
- 主題 / 背景 / 預覽
- 語言包下載
- 語音設定
- TTS 設定
- 片段管理
- 媒體權限與匯入
- video preview lifecycle
- receiver 註冊 / 解註冊

這個 activity 現在不是頁面，而是一個 everything controller。

### 6.2 目標拆分後結構

短期不必強制上 Fragment，但至少要把每區塊切成獨立 section binder。

建議新增：

#### `SettingsScreenCoordinator`

職責：

- 整個設定頁流程 owner
- 組裝 section
- 接手跨 section 互動

#### `SettingsStateStore`

職責：

- 管理 `SettingsUiState`
- 記錄登入狀態、同步狀態、主題狀態、當前語言、預覽狀態

#### `AuthSettingsSection`

職責：

- 登入 / 登出按鈕
- 顯示使用者狀態
- 只處理 auth 相關 UI

#### `SyncSettingsSection`

職責：

- 雲同步 UI
- 同步進度 / 結果顯示
- 不直接實作同步，改呼叫 coordinator / use case

#### `AiSettingsSection`

職責：

- AI 功能開關
- 防詐模型更新入口
- AI provider 設定導流

#### `ThemeSettingsSection`

職責：

- 主題、背景、顏色、預覽
- 影片 / 圖片背景切換

#### `SpeechSettingsSection`

職責：

- speech engine selector
- whisper / faster whisper 測試
- TTS 設定

#### `LanguageSettingsSection`

職責：

- 語言切換
- 語言包
- 輸入法模式與字典相關設定

#### `SnippetSettingsSection`

職責：

- snippet 列表與管理

### 6.3 `SettingsActivity` 要保留什麼

保留：

- `onCreate`
- 權限 callback
- activity result callback
- lifecycle event
- root container 建立
- 將 lifecycle 轉交 coordinator

不應再保留：

- 大量 section UI 建構細節
- 外部請求
- 同步邏輯
- model 更新流程
- 預覽細節運算

## 7. 先建立的共用基礎件

### 7.1 `AppHttpClient`

問題：

- 目前多處自行 new `OkHttpClient`

解法：

- 建立單一 `AppHttpClientFactory`
- 統一 timeout、logging、headers、TLS、user-agent

先不用一次改完，但新代碼一律走這裡。

### 7.2 `ApiResult<T>`

問題：

- 各 client 各自處理成功 / 失敗 / message

解法：

- 定義統一結果模型：
  - `Success<T>`
  - `NetworkError`
  - `AuthError`
  - `ServerError`
  - `UnexpectedError`

這能降低 UI 與 service 對細節錯誤的耦合。

### 7.3 `UserSessionRepository`

要收斂的內容：

- login state
- user id
- display name
- auth token
- selected AI provider
- own API key availability

這些現在分散在 auth client、provider manager、settings、service 中。

### 7.4 `ThemeRepository`

要收斂的內容：

- 當前主題
- 背景圖 / 影片
- 顏色設定
- 角色 / 流光 / 粒子配置
- 變更通知

### 7.5 `SyncRepository`

要收斂的內容：

- 上傳 / 下載 / token 交換
- 自動同步策略
- 錯誤回報
- 最後同步時間

目前 `CloudSyncManager` 邏輯偏重，未來應往 repository + use case 方向收斂。

## 8. 第一批 use case 清單

這些 use case 能讓流程 owner 清楚起來。

### IME

- `HandleKeyPressUseCase`
- `SelectCandidateUseCase`
- `TogglePanelUseCase`
- `ToggleVoiceInputUseCase`
- `FinishImeSessionUseCase`
- `ApplyThemeToImeUseCase`
- `ToggleRemoteKeyboardUseCase`

### Settings

- `LoadSettingsScreenUseCase`
- `ChangeSettingsLanguageUseCase`
- `LoginOrLogoutUseCase`
- `RunCloudSyncUseCase`
- `UpdateFraudModelUseCase`
- `UpdateThemeSettingsUseCase`
- `TestSpeechBackendUseCase`

## 9. 搬遷順序

### Sprint 1: 先立骨架，不搬大量邏輯

新增：

- `ImeUiState`
- `SettingsUiState`
- `ImeSessionCoordinator`
- `SettingsScreenCoordinator`
- `UserSessionRepository`
- `AppHttpClientFactory`

這一階段可以先讓新骨架存在，但仍由舊代碼呼叫。

### Sprint 2: 抽 `YukiBoardIMEService` 的低風險區塊

先搬：

- session lifecycle hooks
- voice coordinator
- panel coordinator
- remote coordinator

原因：

- 這些責任相對獨立
- 與輸入核心演算法耦合較少

### Sprint 3: 抽輸入流程核心

搬：

- `handleKeyPress`
- `applyInputResult`
- `handleSwipeInput`
- snippet expansion
- candidate request

這一階段最關鍵，因為它會真正把 IME service 降成殼。

### Sprint 4: 拆 `SettingsActivity`

先拆 section binder：

- auth
- sync
- AI
- theme

等 binder 穩定後，再考慮要不要變成 fragment。

### Sprint 5: 整理 integration layer

搬：

- 各 API client 的 http client 初始化
- token refresh 邏輯
- result mapping

## 10. 每一階段的驗證方式

### Sprint 1 驗證

- 專案可正常編譯
- 設定頁可開啟
- 鍵盤可開啟

### Sprint 2 驗證

- 面板切換正常
- 語音輸入可開關
- 遠端鍵盤不回歸
- session 結束不發生明顯 lifecycle 問題

### Sprint 3 驗證

- 候選字正常
- composing 正常
- 語言切換正常
- swipe input 正常
- snippet 正常

### Sprint 4 驗證

- 設定頁所有 section 可顯示
- 登入 / 同步 / 主題 / AI 設定仍可用
- 背景預覽生命周期正常

### Sprint 5 驗證

- 各 provider 請求成功率不下降
- timeout / 錯誤顯示行為一致
- token 交換流程正常

## 11. 必補的最小測試集

在真正大量搬邏輯前，至少先補這些：

- `InputRouter` / 輸入引擎行為測試
- `CloudSync` 觸發條件測試
- `UserSessionRepository` 狀態測試
- `ImeStateReducer` 狀態轉換測試
- `ThemeRepository` 更新通知測試

如果只能先補最少，我會優先：

1. 候選字與 composing 流程
2. session 結束同步條件
3. provider / session 狀態

## 12. 高風險區域與策略

### 高風險區域 1: 輸入流程

策略：

- 最後才碰演算法
- 先抽 orchestration，再抽邏輯
- 每搬一段就做 smoke 測試

### 高風險區域 2: 背景與主題預覽

策略：

- 先抽 repository 與 state
- 預覽 view 暫時保留原實作
- 不要在第一階段同時改 UI 與資料流

### 高風險區域 3: 語音與外部服務

策略：

- 先包成 coordinator / gateway
- 不先更換 provider 實作
- 先統一 lifecycle 與錯誤邊界

### 高風險區域 4: 雲同步

策略：

- 先把觸發點從 UI / service 抽走
- 之後才逐步整理 `CloudSyncManager` 內部

## 13. 功能分級建議

為了避免所有功能都拖累核心輸入，建議分級：

### Core

- 基本輸入
- composing
- 候選字
- 語言切換
- 手寫 / 語音輸入主流程

### Extended

- AI 候選
- 雲同步
- 主題 / 背景
- 遠端鍵盤
- 反詐功能

### Experimental

- 遊戲
- 特殊互動角色行為
- 部分娛樂型面板

任何 `experimental` 失敗都不應讓 core 跟著失敗。

## 14. 建議先新增的文件

為了讓重構不失控，建議補這些文件：

- `ARCHITECTURE.md`
- `DEPENDENCY_RULES.md`
- `IME_FLOW_MAP.md`
- `SETTINGS_FLOW_MAP.md`
- `SMOKE_TEST_CHECKLIST.md`

## 15. 第一階段最小可執行任務

如果明天就開始做，我建議第一批任務只做這些：

1. 新增 `ImeUiState` 與 `SettingsUiState`
2. 新增 `ImeSessionCoordinator` 與 `SettingsScreenCoordinator` 空骨架
3. 把 `YukiBoardIMEService` 的 session lifecycle 抽成 `ImeSessionHooks`
4. 把 `SettingsActivity` 的 auth 區塊抽成 `AuthSettingsSection`
5. 新增 `AppHttpClientFactory`
6. 新增 `UserSessionRepository`

做完這六件事後，專案不會立刻變漂亮，但架構方向就固定了，後續每次重構都會比較安全。

## 16. 最終建議

這個專案現在最需要的不是更多設計模式，而是更嚴格的責任分工。

判準很簡單：

- Android lifecycle 留在 platform
- 流程決策留在 coordinator / use case
- 業務規則留在 domain
- API / storage / token / auth 留在 integration
- UI 只做 render 與 callback

只要這個原則守住，`YukiBoardIMEService` 和 `SettingsActivity` 就能逐步瘦下來，專案也會從「功能很強但脆弱」轉成「功能很強且能維護」。

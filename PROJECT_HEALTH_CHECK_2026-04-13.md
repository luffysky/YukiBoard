# SnowRealmYukiBoard 專案健檢報告

日期: 2026-04-13  
範圍: 專案結構掃描、核心 Android/Kotlin 程式碼抽樣、建置設定、工作樹狀態  
方式: 僅掃描與閱讀，未修改既有程式碼；本文件為本次健檢輸出

## 1. 總評

這個專案不是一般輸入法，而是一個高功能密度的 IME 平台。它同時承載：

- 核心輸入法流程
- 多語輸入引擎
- AI 能力與多供應商整合
- 語音輸入 / TTS
- 主題 / 動畫 / 3D 角色
- 遊戲系統
- 雲端同步與帳號
- 支付 / 代幣
- 反詐模型與規則
- GIF / 貼圖 / 遠端鍵盤 / 手寫 / 工具面板

從產品角度看，這很有特色，也很少見。從工程角度看，這代表系統已經進入「需要更強架構支撐」的階段。現在最大的風險不是單一 bug，而是功能規模、執行期狀態、UI 邏輯、平台生命週期與外部服務整合已經互相纏住。

結論：

- 產品潛力高，功能面明顯超出一般 side project 水位。
- 現在的主要瓶頸是架構承載力，而不是單點功能缺失。
- 如果不先做架構治理，後續每加一個功能，修 bug 成本會以非線性方式上升。

## 2. 本次掃描看到的客觀訊號

### 2.1 專案規模

- `app/src/main/java/com/snowrealm/yukiboard` 底下 Kotlin 檔約 229 個
- 套件橫跨 `service`、`input`、`ui`、`claw`、`speech`、`security`、`game`、`data`、`weather`、`remote`、`currency` 等多個領域
- 功能分布很廣，但目前仍主要收斂在單一 Android app module

### 2.2 過大核心檔案

以下檔案已經超出「單檔合理責任範圍」：

- [app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\ui\settings\SettingsActivity.kt) 約 3560 行
- [app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\service\YukiBoardIMEService.kt) 約 3453 行
- [app/src/main/java/com/snowrealm/yukiboard/ui/keyboard/KeyboardView.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\ui\keyboard\KeyboardView.kt) 約 2388 行
- 多個輸入引擎檔案也已接近或超過 800 到 1700 行

這些數字本身不代表錯，但它們通常代表：

- 單檔承擔過多責任
- 生命週期、UI、狀態、I/O、事件處理混在一起
- 回歸測試範圍過大
- 修改成本高，容易出現修 A 壞 B

### 2.3 全域單例與直接相依多

掃描可見多處 `getInstance(context)`、自建 `OkHttpClient.Builder()`、直接抓 manager / auth / theme / sync / provider：

- `ThemeManager.getInstance(...)`
- `CloudSyncManager.getInstance(...)`
- `SnowRealmAuthClient.getInstance(...)`
- `AIProviderManager.getInstance(...)`
- 各種 API client / cache / sync manager 自建網路 client

這樣的寫法在專案初期很快，但在規模變大後會帶來：

- 依賴關係不透明
- 測試難
- 生命周期難控
- 初始化順序與資源釋放難以保證
- 邏輯分散，定位錯誤時需要跨很多檔案追蹤

### 2.4 UI 與業務邏輯耦合偏高

代表性訊號：

- `YukiBoardIMEService` 直接掌控面板、動畫、主題、輸入、遠端鍵盤、語音、雲同步、反詐、遊戲
- `SettingsActivity` 同時處理設定頁 UI、登入、同步、背景更新、AI 設定、網路操作
- 多個 View / Panel 類別直接持有網路 client 或對外部系統發請求

這表示 UI 不是單純展示層，而是直接參與 orchestration 與整合。短期上很方便，長期會造成：

- UI 生命週期事件直接牽動同步、網路與資料修改
- 當同一功能要從 IME、設定頁、浮動面板重用時，會複製邏輯
- 同步與狀態一致性更難保證

### 2.5 狀態模型尚未統一

目前可觀察到：

- 有 `StateFlow`
- 有 `LiveData`
- 有 service 內部 mutable state
- 有 view / panel 私有狀態
- 有 SharedPreferences 持久化
- 有 singleton manager 內部快取

這不表示不能用，但目前看起來尚未形成清楚的狀態層級。常見後果：

- 同一個事實有多份狀態副本
- UI 更新路徑不一致
- 資料變更來源不單一
- 很難回答「這個值的真實來源在哪裡」

### 2.6 外部整合分散且缺乏統一邊界

整合面包含：

- OpenAI / Anthropic / Gemini / Groq / 自家 SnowRealm Provider
- Supabase
- Insight API
- Giphy
- OpenWeather
- Whisper / Faster Whisper / Android Speech
- Firebase
- 支付與授權系統

問題不在於整合很多，而在於它們目前沒有很明確地被同一套 integration boundary 管理。表現為：

- 多處各自 new `OkHttpClient`
- 認證、錯誤處理、timeout、重試策略容易不一致
- 服務切換策略分散在 UI 或 client 類別內

### 2.7 測試保護不足

本次掃描幾乎沒有看到正式單元測試 / 整合測試檔案。存在的是：

- [app/src/main/java/com/snowrealm/yukiboard/ui/settings/InputTestActivity.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\ui\settings\InputTestActivity.kt)

這代表目前主要依靠手測與裝置回歸。對這種高互動、高狀態、跨生命週期的 app 來說，風險很高。

### 2.8 Repo hygiene 尚可，但仍偏鬆

`.gitignore` 已有基本設定，但工作樹仍可見：

- `node_modules/`
- `tools/__pycache__/`
- `supabase/.temp/`
- 多張 bug 圖與報告文件

這反映開發資料、產物、工具輸出、問題記錄與正式程式碼仍混在同一 repo 工作流中。不是大問題，但會增加雜訊與維護成本。

## 3. 專案目前最核心的結構困境

你提到的這句判斷是準的：

> 核心流程、UI、狀態、外部整合很可能互相纏住

我認為目前的纏繞，主要來自以下四件事同時發生。

### 3.1 IME 主服務成了超級協調者

[YukiBoardIMEService.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\service\YukiBoardIMEService.kt) 已經不是單純輸入法 service，而是：

- UI 組裝器
- 互動事件轉發器
- 功能開關控制器
- 外部系統進入點
- 執行期狀態容器

當一個類別同時握有太多責任，任何一條功能路徑都可能穿過它，最後它會變成所有問題的交會點。

### 3.2 設定頁不是純設定頁，而是第二個後台控制中心

[SettingsActivity.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\ui\settings\SettingsActivity.kt) 看起來也承擔了過多責任：

- 帳號
- 同步
- AI provider
- 背景 / 主題
- 網路操作
- 廣播接收
- 多塊 UI 組裝

結果是 IME service 與 SettingsActivity 都在控制系統，形成雙核心。雙核心最容易出現的問題是狀態同步與副作用順序難控。

### 3.3 缺少清楚的 Application / Domain / Integration 邊界

目前比較像是「功能套件切分」，不是「依賴方向切分」。

這兩者差很多。

現在的狀態更像：

- UI 可以直接碰 manager、sync、provider、API client
- manager 也可能直接碰 Android context、prefs、network
- service 既負責流程，也直接負責副作用

這讓依賴關係往各方向擴散，而不是往單一方向收斂。

### 3.4 缺少單一、穩定、可觀測的狀態流

如果沒有明確的 state ownership，最後所有互動都會變成：

- 某個地方改 SharedPreferences
- 某個地方改記憶體變數
- 某個地方發廣播
- 某個地方讀 singleton 最新值
- 某個地方再手動刷新 UI

功能少的時候能撐，功能多了就會變成事件驅動但沒有正式 event model。

## 4. 核心風險排序

以下是我認為目前最值得優先處理的風險，按嚴重度排序。

### 4.1 架構風險: 超大 orchestrator 持續膨脹

風險檔案：

- [YukiBoardIMEService.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\service\YukiBoardIMEService.kt)
- [SettingsActivity.kt](D:\SnowRealmRebirth\SnowRealmYukiBoard\app\src\main\java\com\snowrealm\yukiboard\ui\settings\SettingsActivity.kt)

影響：

- 每次改功能都提高回歸面積
- bug 難隔離
- 新成員難上手
- refactor 風險高

### 4.2 狀態一致性風險

狀態來源多頭並存，未來很容易出現：

- UI 顯示與實際設定不同步
- 設定頁更新後 IME 沒即時反映
- 同步後本地 cache 與面板狀態不一致

### 4.3 生命周期風險

IME、Activity、BroadcastReceiver、浮動面板、語音輸入、動畫、網路工作同時存在，若清理不完整會造成：

- receiver leak
- view / context leak
- service 結束後仍有工作在跑
- 旋轉、切換 app、重啟 IME 後狀態殘留

### 4.4 外部整合風險

當 provider、同步、支付、貼圖、語音等整合越來越多，如果沒有統一 client / error policy，很容易：

- timeout 行為不一致
- 錯誤訊息分散
- token 更新流程重複
- fallback 行為難以預期

### 4.5 測試風險

沒有足夠自動化保護時，大型 refactor 幾乎等於裸奔。

## 5. 我建議的目標架構

重點不是全面重寫，而是建立能承受後續成長的骨架。建議改成「分層 + feature orchestration + 統一狀態來源」。

### 5.1 分成四層

#### A. Platform Layer

職責：

- Android framework 互動
- `InputMethodService`
- `Activity`
- `BroadcastReceiver`
- `ContentResolver`
- 權限 / lifecycle / intent

原則：

- 只做平台接線
- 不承擔業務決策
- 不直接處理複雜流程

#### B. Application Layer

職責：

- use case
- orchestration
- feature coordinator
- session management
- command / event 處理

這層要回答：

- 使用者按鍵後，整條流程怎麼走
- 切換主題後，哪些模組要刷新
- session 結束時，要不要同步、要同步哪些資料

這層應該是從 `YukiBoardIMEService` 與 `SettingsActivity` 抽出去的主戰場。

#### C. Domain Layer

職責：

- 純業務規則
- 輸入結果計算
- 候選字排序
- 學習規則
- 權限判斷
- token 預算邏輯

原則：

- 不依賴 Android
- 不直接碰網路
- 可單元測試

#### D. Integration Layer

職責：

- API clients
- local storage
- prefs / db / file
- third-party services
- auth token refresh

原則：

- 統一 HTTP stack
- 統一錯誤模型
- 統一 retry / timeout / logging policy

### 5.2 把 IME 服務縮成殼

目標不是「改小一點」，而是讓它只做下面幾件事：

- 接收 Android lifecycle
- 接收 UI 事件
- 將事件交給 `ImeSessionCoordinator`
- 訂閱 `ImeUiState`
- 依狀態更新 view

也就是：

- Service 不直接知道所有功能細節
- Service 不直接到處 new manager / client
- Service 不直接做跨模組決策

建議新增的核心物件：

- `ImeSessionCoordinator`
- `InputPipeline`
- `PanelCoordinator`
- `FeatureToggleRegistry`
- `ImeStateStore`

### 5.3 設定頁拆成多個 feature controller

`SettingsActivity` 應只剩：

- fragment / section 容器
- navigation
- UI state 綁定

邏輯抽離方向：

- `AuthSettingsController`
- `SyncSettingsController`
- `ThemeSettingsController`
- `AiProviderSettingsController`
- `LanguagePackSettingsController`

如果短期不想上 Fragment，也至少先拆成獨立 binder / section classes。

### 5.4 建立單一狀態來源

建議把現在最核心的互動先統一成明確 state store。

至少先有三個主狀態：

- `ImeUiState`
- `SettingsUiState`
- `UserSessionState`

原則：

- UI 只讀 state
- 使用者操作轉成 action
- action 經 coordinator / reducer / use case 更新 state
- 副作用由 effect handler 執行

你不一定要導完整 MVI，但至少要建立「狀態真實來源只有一個」這件事。

### 5.5 建立統一整合層

建議新增一個統一的 network / integration 基礎：

- `AppHttpClient`
- `RequestExecutor`
- `ApiResult<T>`
- `AuthTokenProvider`
- `RetryPolicy`
- `NetworkErrorMapper`

所有外部 API 走同一套規則，避免每個 client 各做各的。

### 5.6 重新定義模組邊界

目前是單 module app，但可逐步邁向以下邏輯模組，即使先不真的拆 Gradle module，也應先照這個邊界整理 package 與依賴方向：

- `core/platform`
- `core/network`
- `core/state`
- `feature/ime`
- `feature/settings`
- `feature/ai`
- `feature/speech`
- `feature/sync`
- `feature/theme`
- `feature/security`
- `feature/game`
- `feature/remote`

重點不是資料夾名稱，而是限制誰能依賴誰。

## 6. 如何解決「核心流程、UI、狀態、外部整合互相纏住」

以下是具體解法，不是抽象口號。

### 6.1 先畫出三條主流程，再把流程 owner 固定下來

目前最值得先固定的三條主流程：

1. 輸入流程
2. 設定與主題變更流程
3. 登入與同步流程

每條流程都要回答三個問題：

- 入口在哪裡
- 決策在哪裡
- 副作用在哪裡

建議：

- 輸入流程 owner: `ImeSessionCoordinator`
- 設定流程 owner: `SettingsCoordinator`
- 同步流程 owner: `SyncCoordinator`

只要 owner 固定，其他物件就不能偷偷決策。

### 6.2 把事件與副作用顯式化

不要再讓流程藏在：

- 直接改 prefs
- 直接發廣播
- 直接呼叫 singleton
- 直接更新 view

建議把核心事件顯式列出，例如：

- `OnKeyPressed`
- `OnCandidateSelected`
- `OnThemeChanged`
- `OnLoginSucceeded`
- `OnSessionFinished`
- `OnSyncRequested`

副作用也顯式處理，例如：

- `CommitTextEffect`
- `TriggerCloudSyncEffect`
- `LoadThemeAssetsEffect`
- `RefreshPanelsEffect`

這會大幅降低「誰改了什麼」的不透明感。

### 6.3 把跨模組通知從 ad hoc 廣播改成正式事件總線或 store

目前有 `BroadcastReceiver` 用於背景同步 / 更新通知。這在局部是合理的，但不應成為主要的應用內部同步手段。

建議：

- 應用內部狀態同步優先走 state store / shared flow
- 只有真正需要 Android 系統語意時才用 broadcast

### 6.4 把 UI 元件變成被動元件

目標：

- `PanelView` 負責 render
- `KeyboardView` 負責 render 與 input callback
- 不在 view 裡直接建立網路 client
- 不在 view 裡做高階流程決策

這會讓 UI 可替換、可測試、可分拆。

### 6.5 把整合能力收進 gateway / repository

例如：

- AI provider 整合收斂到 `AiGateway`
- 同步整合收斂到 `SyncRepository`
- 主題與背景整合收斂到 `ThemeRepository`
- 語音整合收斂到 `SpeechGateway`

上層不應知道 OpenAI / Groq / Supabase / Insight 的細節。

### 6.6 對高風險功能做 feature flag 分級

這個專案功能太多，應分成三層：

- `core`: 輸入法主流程不可壞
- `extended`: AI、同步、語音、主題等重要增值功能
- `experimental`: 遊戲、特殊角色互動、部分娛樂性面板

這不是產品縮水，而是讓系統知道哪些功能失敗時可以降級，不該把核心打掛。

## 7. 建議的落地順序

不要全面重寫。正確做法是「先穩定骨架，再搬邏輯」。

### Phase 1: 7 到 14 天

目標：建立重構護欄

- 凍結大型新功能開發
- 盤點 `YukiBoardIMEService` 職責清單
- 盤點 `SettingsActivity` 職責清單
- 建立 architecture decision 記錄
- 訂出 package dependency 規則
- 建立最小 smoke test 清單

交付物：

- 職責地圖
- 事件地圖
- 模組邊界草圖

### Phase 2: 2 到 4 週

目標：抽出 orchestrator 與 state store

- 新增 `ImeSessionCoordinator`
- 新增 `SettingsCoordinator`
- 新增 `UserSessionState` / `ImeUiState`
- 將 IME service 內最容易抽離的副作用搬出去
- 將設定頁中的登入 / 同步 / AI provider 操作抽成 controller

優先搬移：

- 雲同步
- 主題刷新
- AI provider 切換
- session 結束後自動同步

### Phase 3: 4 到 8 週

目標：統一 integration layer

- 收斂 `OkHttpClient`
- 建立統一 `ApiResult`
- 抽出 token provider
- 統一錯誤與重試策略
- 把分散在 UI / manager 的外部呼叫搬到 repository / gateway

### Phase 4: 8 週以上

目標：模組化與測試補強

- 視情況拆成多個 Gradle module
- 補齊 domain 層單元測試
- 補關鍵流程整合測試
- 對 experimental feature 做更強隔離

## 8. 立刻值得做的具體切割點

如果只能先動最少的地方，我建議先切這些。

### 8.1 從 `YukiBoardIMEService` 先拆出 5 個物件

- `ImeLifecycleBinder`
- `ImeInputOrchestrator`
- `ImePanelHost`
- `ImeThemeController`
- `ImeSyncDelegate`

短期目標不是完美，而是讓 service 不再直接持有所有責任。

### 8.2 從 `SettingsActivity` 先拆出 4 個區塊

- `SettingsAuthSection`
- `SettingsSyncSection`
- `SettingsThemeSection`
- `SettingsAiSection`

至少先把每個 section 的 UI 綁定與事件處理收斂。

### 8.3 抽出 `AppHttpClient`

把目前分散在各處的 `OkHttpClient.Builder()` 集中。這件事投資小、回報高。

### 8.4 抽出 `SessionRepository`

把登入狀態、token、userId、provider 選擇這些 session 相關資訊收斂成單一來源。

### 8.5 抽出 `ThemeStateRepository`

主題、背景、角色、流光等狀態不該散落在 prefs、manager、receiver、view 之間。

## 9. 測試策略建議

在大重構前，不需要一口氣補滿測試，但至少要建立最低保護。

### 9.1 先補 5 類單元測試

- 輸入模式切換
- 候選字產生與排序
- session 結束同步條件判斷
- token / 配額邏輯
- 主題狀態轉換

### 9.2 補 3 類整合測試

- 登入後同步流程
- 設定頁修改主題後 IME 更新
- 輸入主流程不因 AI / 網路失敗而中斷

### 9.3 補 smoke checklist

每次發版至少人工驗證：

- 基本輸入
- 候選字
- 語言切換
- 語音輸入
- 面板展開
- 主題切換
- 登入 / 同步

## 10. Repo 與工作流建議

- 補強 `.gitignore` 規則與開發產物清理流程
- 將 bug 圖與臨時報告集中到固定資料夾或 issue 流程
- 對 docs 分層：正式決策文件、任務文件、工作日誌、臨時掃描輸出分開
- 建立 `ARCHITECTURE.md`
- 建立 `TESTING.md`

## 11. 最終判斷

這個專案的問題不是做太少，而是已經做太多，卻還在用偏早期專案的承載方式繼續長。

這不是壞消息。它代表產品有生命力，也代表你已經跨過「能不能做出來」那關，現在要解的是「怎麼讓它能持續長」。

我的明確判斷如下：

- 目前最該投資的是架構整理，不是再加更多功能。
- 最優先的兩個重構目標是：
  - 把 `YukiBoardIMEService` 從超級控制器降成平台殼
  - 把 `SettingsActivity` 從超大控制中心拆成多個可維護 section / controller
- 解開 UI、狀態、核心流程、外部整合糾纏的關鍵，不是某個 design pattern，而是：
  - 固定流程 owner
  - 明確 state source
  - 收斂 integration boundary
  - 減少平台層直接決策

如果這份報告要再往下一步落地，最合理的續作是再補一份實作導向文件：

- `ARCHITECTURE_REFACTOR_PLAN.md`

內容應包含：

- 現有 `YukiBoardIMEService` 職責切割表
- 現有 `SettingsActivity` 拆分表
- 新 package / module 邊界
- 第一階段要新增的 coordinator / repository / state store 清單
- 不破壞既有功能的搬遷順序

這會比直接開始大改程式碼安全得多。

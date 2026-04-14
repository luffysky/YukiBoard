# YukiBoard — Claude Code 專案說明

## 專案定位

YukiBoard 是 **SnowRealm 生態系**的動畫鍵盤 Android App（IME）。
核心賣點：雪凜（Yuki）角色 Lottie 浮動 + 全鍵盤 OpenGL ES 流光特效。

## 架構總覽

```
MVVM + Clean Architecture

┌─────────────────────────────────────────────────────────┐
│  YukiBoardIMEService (InputMethodService)                │
│  持有所有子系統，建構 View 層級                            │
└────────────────┬────────────────────────────────────────┘
                 │
  ┌──────────────┼──────────────────────────────────┐
  │              │                                   │
  │   ┌──────────┼──────────────┐                    │
  │   │ ResizeHandle             │ ← 拖曳調整高度     │
  │   │ ToolbarView              │ ← 面板切換按鈕列   │
  │   │ PanelContainer           │ ← 展開面板區域     │
  │   │   ├ EmojiPanel           │                    │
  │   │   ├ KaomojiPanel         │                    │
  │   │   ├ ClipboardPanel       │                    │
  │   │   ├ TranslatePanel       │                    │
  │   │   ├ GifPanel             │                    │
  │   │   ├ StickerPanel         │                    │
  │   │   └ ThemePanel           │                    │
  │   │ KeyboardContainer        │ ← FrameLayout 疊加 │
  │   │   ├ KeyboardView         │   Canvas 鍵盤      │
  │   │   ├ GLSurfaceView       │   流光覆蓋層        │
  │   │   └ CharacterView       │   Lottie 角色       │
  │   └──────────────────────────┘                    │
  │                                                    │
  │  InputRouter ──┬── EnglishEngine + Dictionary      │
  │                └── ChewingEngine (JNI 預留)        │
  │  SnippetManager                                    │
  │  ShakeDetector / GyroscopeCursor                   │
  │  AppModeDetector                                   │
  │  SpeechEngine (語音輸入)                            │
  │  NextActionPredictor (AI 預留)                      │
  │  SmartInputManager (糾錯+學詞+預測+習慣)            │
  │  FloatingKeyboardWrapper (懸浮模式)                 │
  └────────────────────────────────────────────────────┘
```

## 三模式系統

| 模式 | 觸發方式 | 用途 |
|------|---------|------|
| 一般模式 (NORMAL) | 預設 | 標準 QWERTY + 注音鍵盤 |
| 設計者模式 (DESIGNER) | 長按 Tab 環形選單 / App 自動偵測 | Figma/Canva/PS 快捷鍵 |
| 開發者模式 (DEVELOPER) | 長按 Tab 環形選單 / App 自動偵測 | IDE 快捷鍵 + 程式符號 + Snippet |

### 設計者模式快捷鍵
復原/重做/複製/貼上/剪下/全選、群組/解散/複製物件/鎖定/隱藏、
圖層上下移/翻轉、對齊/縮放、粗體/斜體/底線/字體大小

### 開發者模式快捷鍵
程式符號（`{}()[]{}<>;:`）、運算子、組合符號（`->` `=>` `//` `/**/`）、
找/註解/格式化、行上下移/摺疊展開、Home/End、縮排/反縮排

### Snippet 系統（開發者模式）
輸入縮寫 + Tab 自動展開：`fn`→function、`if`→if(){}、`for`→for(){}、`cl`→console.log()
支援自訂 snippet（SharedPreferences 儲存）

## 感測器功能

| 功能 | 感測器 | 說明 |
|------|--------|------|
| 搖手機復原 | 加速度計 | 搖晃 ≥2.5G 觸發 Ctrl+Z |
| 陀螺儀游標 | 陀螺儀 | 長按空白鍵 + 傾斜 = 游標移動 |
| App 自動切模式 | EditorInfo | 偵測 packageName 自動切換模式 |

所有感測器功能可在 SettingsActivity 開關。

## 重要檔案索引

| 檔案 | 職責 |
|------|------|
| `service/YukiBoardIMEService.kt` | IME 主服務，按鍵→Router→IC，感測器整合 |
| `input/router/InputRouter.kt` | 中英雙軌自動切換核心 |
| `input/engine/InputEngine.kt` | 引擎介面（English / Chewing） |
| `input/engine/ChewingInputEngine.kt` | 注音引擎，JNI 橋接預留 |
| `input/snippet/SnippetManager.kt` | 開發者 Snippet 展開系統 |
| `input/predict/NextActionPredictor.kt` | AI 預測介面（SnowRealmClaw 預留） |
| `sensor/ShakeDetector.kt` | 搖手機偵測（加速度計） |
| `sensor/GyroscopeCursorController.kt` | 陀螺儀游標控制器 |
| `sensor/AppModeDetector.kt` | App 感知模式自動切換 |
| `ui/keyboard/KeyboardView.kt` | Canvas 繪製鍵盤 + 觸控 + 長按 |
| `ui/keyboard/KeyboardState.kt` | 四種佈局：QWERTY/注音/設計者/開發者 |
| `ui/keyboard/KeyboardMode.kt` | 模式 enum（NORMAL/DESIGNER/DEVELOPER/GAME） |
| `ui/keyboard/RadialModeMenu.kt` | 環形模式選單（0.2s 展開動畫） |
| `ui/keyboard/CharacterView.kt` | Lottie 角色動畫 View（浮動右上角） |
| `ui/keyboard/KeyboardResizeHandle.kt` | 拖曳調整鍵盤高度的把手 |
| `ui/keyboard/FloatingKeyboardWrapper.kt` | 懸浮鍵盤外殼（可拖動） |
| `ui/panel/ToolbarView.kt` | 工具列：語音/翻譯/GIF/表情/顔文字/貼圖/剪貼簿/主題/設定 |
| `ui/panel/EmojiPanelView.kt` | Emoji 選擇面板（9 分類 + 網格） |
| `ui/panel/KaomojiPanelView.kt` | 顔文字面板（9 分類日式顔文字） |
| `ui/panel/ClipboardPanelView.kt` | 剪貼簿歷史面板（釘選 + 刪除） |
| `ui/panel/TranslatePanelView.kt` | 翻譯面板（7 語言 + 交換） |
| `ui/panel/GifPanelView.kt` | GIF 搜尋面板（趨勢標籤 + 網格） |
| `ui/panel/StickerPanelView.kt` | 貼圖面板（貼圖包 + 網格） |
| `ui/panel/ThemePanelView.kt` | 主題瀏覽面板（即時預覽切換） |
| `ui/settings/SettingsActivity.kt` | 設定頁面（主題/感測器開關/靈敏度） |
| `ui/settings/YukiPreferences.kt` | SharedPreferences 偏好設定封裝 |
| `input/dictionary/EnglishDictionary.kt` | 英文 Trie 字典（~3000 高頻詞 + 自動更正） |
| `input/dictionary/UserDictionary.kt` | 使用者自訂詞庫（學習 + 持久化） |
| `input/smart/SmartInputManager.kt` | 智慧輸入總管（統合糾錯+學詞+預測+習慣） |
| `input/smart/SmartCorrectionEngine.kt` | 三階段糾錯（鄰近鍵+編輯距離+bigram上下文） |
| `input/smart/AdaptiveDictionary.kt` | 自適應字典（常用詞自動學習 + 淘汰） |
| `input/smart/PhrasePredictor.kt` | bigram+trigram 語句預測引擎 |
| `input/smart/TypingHabitTracker.kt` | 打字習慣追蹤（時段/App 詞頻 + 速度） |
| `speech/SpeechInputEngine.kt` | 語音輸入引擎介面 |
| `speech/AndroidSpeechEngine.kt` | Android SpeechRecognizer 實作 |
| `animation/gles/FlowLightShader.kt` | GLSL 流光 Shader 原始碼 |
| `animation/gles/FlowLightRenderer.kt` | GL Renderer，每幀更新 uTime |
| `animation/gles/KeyboardGLSurfaceView.kt` | GL 透明覆蓋層（流光效果） |
| `animation/LottieCharacterAnimation.kt` | Lottie 動畫實作 |
| `theme/KeyboardTheme.kt` | 主題資料類別（顏色 + 流光透明度） |
| `game/GameEngine.kt` | 遊戲引擎核心（GameLogic + GameLoopRunner） |
| `game/SnakeGame.kt` | 貪食蛇遊戲 |
| `game/WhackAMoleGame.kt` | 打地鼠遊戲 |
| `game/RhythmGame.kt` | 節奏遊戲 |
| `game/TypingChallengeGame.kt` | 打字挑戰遊戲 |
| `remote/RemoteKeyboardService.kt` | 遠端鍵盤介面 + 共用資料模型 |
| `remote/BluetoothHidKeyboard.kt` | 藍牙 HID 鍵盤實作 |
| `remote/WifiKeyboardServer.kt` | WiFi WebSocket Server + 內建控制網頁 |
| `ui/panel/RemoteKeyboardPanelView.kt` | 遠端鍵盤控制面板 |
| `game/GameMenuView.kt` | 遊戲選單（2x2 卡片） |
| `game/GameKeyboardView.kt` | 遊戲鍵盤渲染 |
| `res/xml/method.xml` | IME subtype 宣告（系統讀取） |

## 關鍵設計決策

### InputRouter 雙軌並行邏輯
- `AUTO` 模式：偵測注音符號（U+3105-312F）→ 切 BOPOMOFO；偵測字母 → 切 ENGLISH
- 切軌前先 `forceCommit()` 前一軌道的 composing，避免殘留
- `InputResult` 是密封類（sealed class），Service 端用 when 模式匹配，不用 instanceof

### 三模式切換機制
- 長按 Tab 500ms → 彈出 RadialModeMenu（OvershootInterpolator 展開動畫）
- App 自動偵測：onStartInputView 讀取 EditorInfo.packageName → AppModeDetector
- 手動切換會設 `manualModeOverride = true`，覆蓋自動偵測直到下次輸入

### Snippet 展開流程
- 開發者模式按 Tab → 先嘗試 snippet 展開 → 失敗才發 Tab 鍵
- 取游標前方文字最後一個 word → SnippetManager.expand() → 刪除縮寫 + 插入展開

### 感測器生命週期
- onStartInputView → 啟動 ShakeDetector + GyroscopeController
- onFinishInputView → 停止所有感測器
- 長按空白鍵 → activate() gyro；放開 → deactivate()

### OpenGL ES 流光架構
- `KeyboardGLSurfaceView` 設 `setZOrderOnTop(true)` + `PixelFormat.RGBA_8888` 透明疊加
- Fragment Shader 三道光波疊加：主流光 + 慢速寬光 + 亮點閃爍
- 透明度由 `uAlpha` uniform 控制，可從外部呼叫 `setFlowAlpha()` 動態調整

### Lottie 動畫規範
動畫 JSON 放在 `assets/lottie/`，命名規則：`yuki_<name>.json`
- `yuki_idle.json` — 循環浮動（必備）
- `yuki_typing.json` — 打字反應
- `yuki_happy.json` — 選詞成功
- `yuki_listening.json` — 語音輸入中
- `yuki_switch_lang.json` — 切換語言

## 已完成功能

- [x] GL 流光覆蓋層整合（KeyboardGLSurfaceView 透明疊加在 KeyboardView 上）
- [x] Lottie 角色動畫整合（CharacterView 浮動右上角，回應打字/選詞/切模式/遊戲事件）
- [x] 工具列 + 9 面板系統（語音/翻譯/GIF/表情/顔文字/貼圖/剪貼簿/主題/設定）
- [x] 語音輸入（AndroidSpeechEngine + Lottie listening 動畫）
- [x] Emoji 面板（9 分類 700+ emoji）
- [x] 顔文字面板（9 分類 130+ kaomoji）
- [x] 剪貼簿面板（歷史記錄 + 釘選 + 刪除）
- [x] 翻譯面板（7 語言 + 交換 + 翻譯引擎預留）
- [x] GIF 面板（趨勢標籤 + API 預留）
- [x] 貼圖面板（貼圖包 + 網格）
- [x] 主題瀏覽面板（即時預覽切換）
- [x] 英文字典（Trie ~3000 高頻詞 + 前綴匹配 + 自動更正）
- [x] 使用者自訂詞庫（學習 + SharedPreferences 持久化）
- [x] 鍵盤高度調整（拖曳把手，25%~55% 螢幕高度）
- [x] 鍵盤寬度調整（50%~100% 螢幕寬度）
- [x] 懸浮鍵盤模式（可拖動 + 固定/關閉按鈕）
- [x] 藍牙 HID 鍵盤（BluetoothHidDevice API，完整 HID report descriptor）
- [x] WiFi WebSocket 鍵盤（內建 HTTP/WebSocket Server + 控制網頁）
- [x] 遠端鍵盤面板（藍牙/WiFi 啟動、狀態顯示、URL 顯示）
- [x] 遊戲模式（4 款遊戲 + HUD + 計分）
- [x] 模式切換時角色表情反應
- [x] 橫向模式佈局（左主鍵盤 + 右工具面板）

## 待完成項目

- [ ] libchewing JNI 整合：參考 `ChewingInputEngine.kt` 內的注釋
- [ ] NextActionPredictor 接 SnowRealmClaw AI
- [ ] GIF 引擎接 Giphy/Tenor API
- [ ] 翻譯引擎接 MLKit 或 Google Translate
- [ ] 貼圖包下載管理
- [ ] 實際 Lottie 角色動畫設計（目前為幾何佔位動畫）

## 環境變數 / API Key

`.env` 檔案放在專案根目錄，`app/build.gradle` 會讀取並注入 `BuildConfig`。

| 變數名 | 用途 | 申請 |
|--------|------|------|
| `INVITE_HMAC_SECRET` | 邀請碼 HMAC 簽名密鑰 | 自行設定（開發用預設 `yukiboard_dev_2026`） |
| `PLATFORM_API_KEY` | SnowRealm Platform 通訊金鑰 | Insight Engine 後台產生 |
| `INSIGHT_API_BASE_URL` | Insight Engine API 基底 URL | 預設 `https://insight.snowrealm.pet` |
| `GIPHY_API_KEY` | Giphy GIF 搜尋 | https://developers.giphy.com/ |
| `OPENAI_API_KEY` | OpenAI（GPT / Whisper） | https://platform.openai.com/api-keys |
| `ANTHROPIC_API_KEY` | Anthropic（Claude） | https://console.anthropic.com/settings/keys |
| `OPENWEATHER_API_KEY` | OpenWeatherMap 天氣 | https://home.openweathermap.org/api_keys |
| `YUKIBOARD_SUPABASE_URL` | Supabase 專案 URL（Z幣/商城/排行榜） | https://supabase.com/dashboard |
| `YUKIBOARD_SUPABASE_ANON_KEY` | Supabase anon key | 同上，Settings → API |
| `NEWEBPAY_MERCHANT_ID` | 藍新金流商店代號（Z幣儲值） | https://www.newebpay.com/ |
| `NEWEBPAY_HASH_KEY` | 藍新 HashKey | 藍新商店後台 → 交易設定 |
| `NEWEBPAY_HASH_IV` | 藍新 HashIV | 同上 |

> 正式版 AI Key 由使用者在 AISettingsActivity 自行輸入，`.env` 僅供開發測試。

## 開發環境

- **compileSdk** 35, **minSdk** 26
- **Kotlin** 2.0.21, **AGP** 8.7.3, **Gradle** 8.9
- **依賴**：Lottie 6.3, AndroidX ViewModel/LiveData, Coroutines 1.7.3
- **NDK**：arm64-v8a + x86_64（libchewing 預留）

## 命名慣例

- Kotlin 檔案：`PascalCase`
- Package：`com.snowrealm.yukiboard.<layer>`
- 主題顏色 field 以 `Int` 結尾（例如 `backgroundColorInt`）代表 ARGB Int
- InputResult 子類型使用 `object`（無資料）或 `data class`（有資料）
- KeyCode 常數：KEYCODE_DESIGN_*（設計者）、KEYCODE_DEV_*（開發者）

---

## Claude Code x Codex 協作規則（2026-04-13 生效）

本專案由兩個 AI 協作開發，使用者（luffysky）主導分工安排。

### 誰是誰

- **Claude Code**（就是你）：Anthropic CLI agent，負責日常實作、bug 修復、功能搬遷。每次對話都是你。
- **Codex**（OpenAI Codex）：另一個 AI agent，負責架構設計、骨架建立、結構校正。Codex 不在這個對話裡，透過 markdown 文件交接。
- 兩邊不會同時改同一個檔案。透過文件（CODEX_HANDOFF / CODEX_REPLY）做非同步交接。
- 如果使用者提到「Codex 說…」或貼了 Codex 的文件，視為已確認的架構決策，Claude Code 應遵守。
- 如果使用者要求的修改超出 Claude Code 的安全操作範圍，應主動提醒「這個應該由 Codex 主導」。

### 協作時間線

- **2026-04-13**：Codex 完成專案健檢 + 架構重構藍圖 + 協作指南
- **2026-04-13**：Claude Code 寫交接文件（CODEX_HANDOFF.md），Codex 回覆確認（CODEX_REPLY_TO_CLAUDE_CODE_2026-04-13.md）
- **當前階段**：Claude Code 穩定登入 + 平行拆安全 section；等 Codex 立 Sprint 1 骨架後依骨架搬遷

本區塊為 Claude Code 與 Codex 共同確認的架構重構邊界與行為約束。
所有後續對話都必須遵守，不得以「趕進度」或「先塞進去」為由違反。

詳細文件請見：
- `CLAUDE_CODEX_COLLAB_GUIDE.md` — 完整分工與交接格式
- `ARCHITECTURE_REFACTOR_PLAN.md` — Sprint 制重構藍圖
- `PROJECT_HEALTH_CHECK_2026-04-13.md` — 專案健檢報告
- `CODEX_HANDOFF.md` — Claude Code → Codex 交接
- `CODEX_REPLY_TO_CLAUDE_CODE_2026-04-13.md` — Codex 回覆確認

### 角色分工

| 角色 | 負責範圍 |
|------|---------|
| **Codex** | 架構掃描、切割設計、骨架建立、package 邊界、風險盤點、結構校正 |
| **Claude Code** | 依骨架搬遷功能、UI section 拆分、bug 修復、登入流程穩定化 |

### 絕對禁止（Claude Code 不得違反）

1. **不得再往 `YukiBoardIMEService.kt` 加任何新功能或新責任**
2. **不得再往 `SettingsActivity.kt` 加任何新 section 或新邏輯**
3. **不得新增 `getInstance(context)` 式跨層依賴**
4. **不得在 view / panel 類裡直接建立 network client**
5. **不得引入與 `platform -> application -> domain <- integration` 方向衝突的設計**
6. **不得大改 `handleKeyPress()` 核心演算法路徑**（需 Codex 主導）
7. **不得在登入流程未穩定前重寫 auth / sync 全鏈路**
8. **不得把架構重構與功能大改混在同一輪**
9. **不得在沒有說明的情況下大規模搬檔或改 package**
10. **不得把臨時 patch 寫回核心 super class**

### 新代碼必須遵守的方向

- 新功能進設定頁 → 以 section binder 形式落地，不加進 `SettingsActivity` 本體
- 新功能進 IME → 以 coordinator 形式落地，不加進 `YukiBoardIMEService` 本體
- 狀態管理 → 走 `UiState + Coordinator + Repository`，不用 activity/service 私有變數
- 外部整合 → 走統一 Repository / Gateway，不在多處各自處理 token / timeout / 錯誤
- SharedPreferences → 不再散落直讀直寫，新代碼走 Repository 封裝

### 目標依賴方向

```
platform -> application -> domain <- integration
```

- `platform`：Android framework 接線（IMEService、Activity），只依賴 application
- `application`：coordinator、state store、use case
- `domain`：pure Kotlin 業務邏輯，不依賴 Android
- `integration`：外部 API / storage / auth，實作 domain 定義的介面

### 重構優先順序（固定）

1. 立新骨架（Sprint 1：UiState、Coordinator、Repository）
2. 瘦 `YukiBoardIMEService`
3. 瘦 `SettingsActivity`
4. 收斂狀態來源
5. 收斂 integration boundary
6. 最後才考慮加大功能面

### Claude Code 可安全操作的區塊

- `AuthSettingsSection` — 從 SettingsActivity 拆出登入/帳號 UI
- `SyncSettingsSection` — 拆出雲端同步 UI
- `AISettingsSection` — 拆出 AI 設定 UI binder
- `ImeSessionHooks` — 從 IMEService 抽出 session lifecycle
- `ImeVoiceCoordinator` — 語音輸入薄封裝
- 登入流程 bug 修復（不大改架構）
- helper / binder / controller 新增
- 重複 UI 建構邏輯拆出

### 每輪工作交接格式

完成一輪工作後，必須用以下格式留交接紀錄：

- **A. 變更摘要**：改了什麼、主要檔案、新骨架/介面/依賴
- **B. 風險**：回歸風險、暫時只搬殼的地方
- **C. 下一步建議**：下一輪最適合做什麼、哪裡先不碰
- **D. 架構決策**：新增的 coordinator / repository / interface（不藏在 commit 裡）

### 決策衝突時的優先選擇

當方案有衝突，選：
1. 更容易縮小 `YukiBoardIMEService` 的
2. 更容易縮小 `SettingsActivity` 的
3. 更符合單一狀態來源的
4. 更能隔離外部整合副作用的

而不是選看起來比較快但會繼續累積結構債的方案。

### 核心準則

> 每一輪修改，都應讓核心流程更清楚、狀態更單一、依賴更可追蹤，而不是只是把功能先塞進去。

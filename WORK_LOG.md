# YukiBoard 工作日誌

## 專案概況

| 項目 | 數據 |
|------|------|
| 專案名稱 | YukiBoard — SnowRealm 生態系動畫鍵盤 |
| 平台 | Android IME (InputMethodService) |
| 語言 | Kotlin |
| 程式碼規模 | 149 個 .kt 檔案，約 21,800 行 |
| 字典資料 | 注音字典 232,454 條 + 聲母簡拼 40,099 條 + 英文 20,000 詞 |
| 最低 SDK | 26 (Android 8.0) |
| 編譯 SDK | 35 |

---

## 已完成模組總覽

### 核心輸入系統

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| IME 主服務 | `service/YukiBoardIMEService.kt` | ✅ 完成 | InputMethodService 主入口，整合所有子系統 |
| 輸入路由 | `input/router/InputRouter.kt` | ✅ 完成 | 中英雙軌自動切換，AUTO/ENGLISH/BOPOMOFO 三模式 |
| 英文引擎 | `input/engine/EnglishInputEngine.kt` | ✅ 完成 | composing buffer + Trie 候選 |
| 注音引擎 | `input/engine/ChewingInputEngine.kt` | ✅ 完成 | 注音→漢字，McBopomofo 字典，多音節分音 |
| 英文字典 | `input/dictionary/EnglishDictionary.kt` | ✅ 完成 | 20,000 高頻詞 Trie + 自動更正 |
| 使用者詞庫 | `input/dictionary/UserDictionary.kt` | ✅ 完成 | 學習 + SharedPreferences 持久化 |
| 候選管理 | `input/candidate/CandidateManager.kt` | ✅ 完成 | 候選列統一管理 |
| Snippet 系統 | `input/snippet/SnippetManager.kt` | ✅ 完成 | 開發者模式 Tab 展開（fn, if, for, cl 等） |

### 智慧輸入

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| 智慧總管 | `input/smart/SmartInputManager.kt` | ✅ 完成 | 統合糾錯+學詞+預測+習慣 |
| 智慧糾錯 | `input/smart/SmartCorrectionEngine.kt` | ✅ 完成 | 三階段：鄰近鍵→編輯距離→bigram 上下文 |
| 自適應字典 | `input/smart/AdaptiveDictionary.kt` | ✅ 完成 | 常用詞自動學習，CJK 支援，30 天淘汰 |
| 語句預測 | `input/smart/PhrasePredictor.kt` | ✅ 完成 | bigram + trigram 學習式預測 |
| 習慣追蹤 | `input/smart/TypingHabitTracker.kt` | ✅ 完成 | 時段/App 詞頻 + 打字速度 |
| AI 預測介面 | `input/predict/NextActionPredictor.kt` | ✅ 完成 | 本地規則引擎 + Claw AI 增強介面 |

### 鍵盤 UI

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| 主鍵盤 View | `ui/keyboard/KeyboardView.kt` | ✅ 完成 | Canvas 自繪鍵盤 + 觸控 + 長按 + 候選列 |
| 鍵盤狀態 | `ui/keyboard/KeyboardState.kt` | ✅ 完成 | QWERTY / 注音 / 設計者 / 開發者 四佈局 |
| 鍵盤模式 | `ui/keyboard/KeyboardMode.kt` | ✅ 完成 | NORMAL / DESIGNER / DEVELOPER / GAME |
| 環形選單 | `ui/keyboard/RadialModeMenu.kt` | ✅ 完成 | 長按 Tab 彈出模式切換（OvershootInterpolator） |
| 角色 View | `ui/keyboard/CharacterView.kt` | ✅ 完成 | Lottie 動畫浮動右上角 |
| 高度調整 | `ui/keyboard/KeyboardResizeHandle.kt` | ✅ 完成 | 拖曳把手（25%~55% 螢幕高度） |
| 懸浮鍵盤 | `ui/keyboard/FloatingKeyboardWrapper.kt` | ✅ 完成 | 可拖動 + 固定 / 關閉 |
| 按鍵裝飾 | `ui/keyboard/KeyDecorator.kt` | ✅ 完成 | 按鍵視覺裝飾 |
| 鍵盤背景 | `ui/keyboard/KeyboardBackgroundView.kt` | ✅ 完成 | 動態粒子背景 / 自訂圖片影片 |
| 候選展開面板 | `ui/keyboard/KeyboardView.kt` | ✅ 完成 | ▼ 按鈕展開 5 欄網格，可捲動瀏覽 |

### 面板系統（9 面板 + 工具列）

| 面板 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| 工具列 | `ui/panel/ToolbarView.kt` | ✅ 完成 | 面板切換按鈕列 |
| Emoji | `ui/panel/EmojiPanelView.kt` | ✅ 完成 | 9 分類 700+ emoji |
| 顔文字 | `ui/panel/KaomojiPanelView.kt` | ✅ 完成 | 9 分類 130+ kaomoji |
| 剪貼簿 | `ui/panel/ClipboardPanelView.kt` | ✅ 完成 | 歷史記錄 + 釘選 + 刪除 |
| 翻譯 | `ui/panel/TranslatePanelView.kt` | ✅ 完成 | 7 語言 + 交換（引擎預留） |
| GIF | `ui/panel/GifPanelView.kt` | ✅ 完成 | 趨勢標籤 + 網格（API 預留） |
| 貼圖 | `ui/panel/StickerPanelView.kt` | ✅ 完成 | 貼圖包 + 網格 |
| 主題 | `ui/panel/ThemePanelView.kt` | ✅ 完成 | 即時預覽切換 |
| 遠端鍵盤 | `ui/panel/RemoteKeyboardPanelView.kt` | ✅ 完成 | 藍牙/WiFi 控制面板 |
| Claw AI | `ui/panel/ClawPanelView.kt` | ✅ 完成 | AI 技能面板 |
| Claw 工具列 | `ui/panel/ClawToolbarView.kt` | ✅ 完成 | AI 技能選擇工具列 |

### 動畫系統

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| GL 流光 Shader | `animation/gles/FlowLightShader.kt` | ✅ 完成 | GLSL 三道光波疊加 |
| GL Renderer | `animation/gles/FlowLightRenderer.kt` | ✅ 完成 | 每幀更新 uTime |
| GL 覆蓋層 | `animation/gles/KeyboardGLSurfaceView.kt` | ✅ 完成 | 透明 RGBA_8888 疊加 |
| Lottie 動畫 | `animation/LottieCharacterAnimation.kt` | ✅ 完成 | idle/typing/happy/listening/switch |
| 粒子系統 | `animation/KeyboardParticleView.kt` | ✅ 完成 | 動態粒子背景 |

### 感測器

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| 搖晃偵測 | `sensor/ShakeDetector.kt` | ✅ 完成 | 加速度計 ≥2.5G → Ctrl+Z |
| 陀螺儀游標 | `sensor/GyroscopeCursorController.kt` | ✅ 完成 | 長按空白鍵 + 傾斜 = 游標移動 |
| App 偵測 | `sensor/AppModeDetector.kt` | ✅ 完成 | EditorInfo.packageName 自動切模式 |

### 遠端鍵盤

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| 藍牙 HID | `remote/BluetoothHidKeyboard.kt` | ✅ 完成 | BluetoothHidDevice API |
| WiFi WebSocket | `remote/WifiKeyboardServer.kt` | ✅ 完成 | HTTP/WebSocket Server + 內建控制網頁 |
| 共用介面 | `remote/RemoteKeyboardService.kt` | ✅ 完成 | 抽象介面 + 資料模型 |

### 遊戲模式（20 款遊戲）

| 遊戲 | 檔案 | 狀態 |
|------|------|------|
| 遊戲引擎 | `game/GameEngine.kt` | ✅ |
| 遊戲選單 | `game/GameMenuView.kt` | ✅ |
| 遊戲鍵盤 | `game/GameKeyboardView.kt` | ✅ |
| 計分系統 | `game/ScoreManager.kt` | ✅ |
| 貪食蛇 | `game/SnakeGame.kt` | ✅ |
| 打地鼠 | `game/WhackAMoleGame.kt` | ✅ |
| 節奏遊戲 | `game/RhythmGame.kt` | ✅ |
| 打字挑戰 | `game/TypingChallengeGame.kt` | ✅ |
| 彈幕迷你 | `game/BulletHellMiniGame.kt` | ✅ |
| 情侶問答 | `game/CoupleQuizGame.kt` | ✅ |
| 冰晶收集 | `game/IceCrystalGame.kt` | ✅ |
| 踩地雷 | `game/KeyboardMinesweeperGame.kt` | ✅ |
| 鍵盤旅人 | `game/KeyboardTravelerGame.kt` | ✅ |
| 記憶翻牌 | `game/MemoryFlipGame.kt` | ✅ |
| 心情挖礦 | `game/MoodMinerGame.kt` | ✅ |
| 隨機挑戰 | `game/RandomChallengeGame.kt` | ✅ |
| 拼字對決 | `game/SpellingDuelGame.kt` | ✅ |
| 打字競速 | `game/TypingRaceGame.kt` | ✅ |
| 語音遊戲 | `game/VoiceGame.kt` | ✅ |
| 戰艦巡航 | `game/WarshipCruiseGame.kt` | ✅ |
| 語詞守護 | `game/WordGuardianGame.kt` | ✅ |
| Yuki 問答 | `game/YukiQuizGame.kt` | ✅ |

### SnowRealmClaw AI 整合

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| Claw Client | `claw/ClawClient.kt` | ✅ 完成 | API 客戶端 |
| Token 管理 | `claw/TokenBudgetManager.kt` | ✅ 完成 | Token 用量追蹤 |
| Skill 介面 | `claw/ClawSkill.kt` | ✅ 完成 | 技能抽象介面 |
| Provider 介面 | `claw/provider/SnowRealmProvider.kt` | ✅ 完成 | AI Provider 抽象 |

### AI 技能（19 個 Skill）

| 技能 | 檔案 | 說明 |
|------|------|------|
| AI 預測 | `AIPredictionSkill.kt` | 智慧候選預測 |
| 動畫控制 | `AnimationSkill.kt` | 角色動畫觸發 |
| 程式補全 | `CodeCompleteSkill.kt` | 開發者模式程式碼補全 |
| Commit 訊息 | `CommitMessageSkill.kt` | Git commit 自動生成 |
| 上下文感知 | `ContextSkill.kt` | App 場景感知 |
| 丘比特 | `CupidSkill.kt` | 撩人話術 |
| Emoji 翻譯 | `EmojiTranslateSkill.kt` | 文字↔Emoji 轉換 |
| 文法檢查 | `GrammarCheckSkill.kt` | 英文文法糾正 |
| 迷因文字 | `MemeTextSkill.kt` | 迷因/搞笑文字生成 |
| 心情日記 | `MoodDiarySkill.kt` | 打字情緒追蹤 |
| 快速回覆 | `QuickReplySkill.kt` | 訊息快速回覆建議 |
| Regex 生成 | `RegexGenSkill.kt` | 正規表達式生成 |
| 摘要 | `SummarizeSkill.kt` | 文字摘要 |
| 語氣調整 | `ToneAdjustSkill.kt` | 正式/輕鬆語氣轉換 |
| 翻譯 | `TranslateSkill.kt` | 多語言翻譯 |
| 天氣 | `WeatherSkill.kt` | 天氣查詢 |
| 悄悄話 | `WhisperSkill.kt` | 加密訊息 |
| 寫作助手 | `WriterSkill.kt` | 文章寫作輔助 |
| Yuki 聊天 | `YukiChatSkill.kt` | 與 Yuki 角色聊天 |

### 認證 / 貼圖

| 模組 | 檔案 | 狀態 |
|------|------|------|
| 邀請碼管理 | `auth/InviteCodeManager.kt` | ✅ |
| Auth Client | `auth/SnowRealmAuthClient.kt` | ✅ |
| 貼圖 API | `sticker/InsightStickerApi.kt` | ✅ |
| 貼圖快取 | `sticker/StickerImageCache.kt` | ✅ |
| 貼圖包下載 | `sticker/StickerPackDownloadManager.kt` | ✅ |
| 貼圖庫 | `sticker/StickerRepository.kt` | ✅ |

### 語音 / 設定

| 模組 | 檔案 | 狀態 |
|------|------|------|
| 語音引擎介面 | `speech/SpeechInputEngine.kt` | ✅ |
| Android 語音 | `speech/AndroidSpeechEngine.kt` | ✅ |
| 設定頁面 | `ui/settings/SettingsActivity.kt` | ✅ |
| AI 設定 | `ui/settings/AISettingsActivity.kt` | ✅ |
| 偏好管理 | `ui/settings/YukiPreferences.kt` | ✅ |
| 邀請碼頁 | `ui/auth/InviteCodeActivity.kt` | ✅ |
| 登入頁 | `ui/auth/LoginActivity.kt` | ✅ |

### 主題系統

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| 主題資料 | `theme/KeyboardTheme.kt` | ✅ 完成 | 8 主題：液態玻璃/極光/星空/簡約/暗黑/日出/海洋/森林 |

---

## 開發時間軸

### 2026-04-03 — 專案建立 + 核心功能大爆發

**Commit: `ec007eb` — YukiBoard IME complete feature set**
- 建立完整 Android IME 專案架構（MVVM + Clean Architecture）
- 實作 Canvas 自繪鍵盤系統（KeyboardView + KeyboardState）
- 三模式系統：一般 / 設計者 / 開發者（環形選單切換）
- 英文 Trie 字典 + composing buffer 輸入
- OpenGL ES 流光特效覆蓋層
- Lottie 角色動畫系統
- 搖晃復原 + 陀螺儀游標感測器
- 9 面板系統（語音/翻譯/GIF/表情/顔文字/貼圖/剪貼簿/主題/設定）
- Snippet 展開系統
- 鍵盤高度/寬度調整 + 懸浮模式
- 智慧輸入系統（糾錯+學詞+預測+習慣追蹤）
- 語音輸入（Android SpeechRecognizer）

**Commit: `3f3dbae` — Bluetooth HID + WiFi WebSocket remote keyboard**
- 藍牙 HID 鍵盤（完整 HID report descriptor）
- WiFi WebSocket 鍵盤（內建 HTTP/WS Server + 控制網頁）
- 遠端鍵盤面板 UI

**Commit: `6421ed3` — liquid glass keys, dynamic particles, 8 themes**
- 液態玻璃按鍵效果
- 動態粒子背景
- 自訂背景圖片/影片
- 8 套鍵盤主題

**Commit: `e152f19` — Bopomofo dictionary + AI Claw integration**
- 注音字典基礎版（1,319 條目）
- 候選字選擇功能
- SnowRealmClaw AI 框架（19 技能）
- 認證 / 邀請碼系統
- 貼圖管理系統
- 20 款遊戲

### 2026-04-03 ~ 2026-04-04 — 中文輸入大幅強化（未 commit）

**注音輸入引擎重寫**
- 整合 McBopomofo 開源字典（MIT License, openvanilla/McBopomofo）
  - 注音→漢字字典：1,319 → **232,454 條目**
  - 新增聲母簡拼詞庫：**40,099 條目**
- 聲母簡拼輸入（ㄅㄅ→爸爸, ㄐㄊ→今天）
- 單字優先排序（ㄅㄨ → 不 在詞組前面）
- 所有聲調變體展開查詢（ㄅㄨ 查 ˊˇˋ˙ 全部）
- 前綴快取（1-2 字元前綴 → 常用單字，50 個）
- 連續預測索引（選字後預測下一字，30 個候選）
- 反向索引（漢字→注音，用於上下文過濾）
- 自動分音節狀態機（ㄅㄨㄧㄠ → [ㄅㄨ][ㄧㄠ] → 不要）
- 多音節詞組查詢（聲母簡拼 + 交叉驗證 + dict 拼接）
- 連續輸入上下文排序（選「剛」後打ㄏㄠ → 「好」排前面）

**英文字典升級**
- 從 hardcoded ~1,800 詞 → 外部檔案載入 **20,000 高頻詞**
- Google 語料庫頻率排序
- 支援 Context / 無 Context 雙模式

**候選面板改進**
- ▼ 展開按鈕 + 5 欄網格面板
- 可觸控捲動（滑動 vs 點選分離）
- 捲動指示條
- 候選去重（linkedSetOf 自動去重）

**智慧輸入中文支援**
- InputRouter 中文逐字 bigram 記錄（不再 split by space）
- AdaptiveDictionary 支援 CJK 單字（門檻 2 次）
- PhrasePredictor 中文語句預測

**AI 預測清理**
- 移除 hardcoded 意圖模式（intent patterns）
- 保留學習式 bigram/trigram 預測系統

**其他**
- Wireless ADB 部署流程建立（192.168.0.14 手機測試）
- YukiBoardIMEService 整合 Yuki 角色系統（表情/語泡/冷卻）
- SnowRealmProvider graceful stubs（移除 NotImplementedError）

---

## 待完成項目

### 高優先

| 項目 | 說明 |
|------|------|
| 候選字頻率排序 | 中文候選依詞頻排序（目前順序不夠精準） |
| libchewing JNI 整合 | 原生注音引擎（參考 ChewingInputEngine.kt 注釋） |
| 實際 Lottie 動畫 | 目前為幾何佔位動畫，需設計師繪製 |

### 中優先

| 項目 | 說明 |
|------|------|
| GIF 引擎 | 接 Giphy / Tenor API |
| 翻譯引擎 | 接 MLKit 或 Google Translate |
| 貼圖包下載管理 | 實際下載 + 解壓 + 渲染 |
| Claw AI 後端 | 接 SnowRealmClaw 真實 API |
| 主題自訂 | 使用者自訂配色 |

### 低優先

| 項目 | 說明 |
|------|------|
| 遊戲平衡調整 | 20 款遊戲的難度曲線 |
| 無障礙支援 | TalkBack / 大字體 |
| 多語系 | i18n 字串資源 |
| 效能優化 | 字典載入時間 / 記憶體佔用 |

---

## 技術債

| 項目 | 說明 |
|------|------|
| 大量未 commit 變更 | 2026-04-03~04 的工作需整理 commit |
| CRLF 警告 | Windows 開發環境 LF/CRLF 混用 |
| 字典載入效能 | 23 萬條注音字典冷啟動載入（lazy 已處理） |
| SharedPreferences 瓶頸 | 自適應字典/習慣追蹤高頻寫入 |

---

*最後更新：2026-04-04*

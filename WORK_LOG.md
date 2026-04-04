# YukiBoard 工作日誌

## 專案概況

| 項目 | 數據 |
|------|------|
| 專案名稱 | YukiBoard — SnowRealm 生態系動畫鍵盤 |
| 平台 | Android IME (InputMethodService) |
| 語言 | Kotlin |
| 版本 | **v0.2.0** (versionCode 2) |
| 程式碼規模 | ~155 個 .kt 檔案，約 25,000+ 行 |
| 字典資料 | 注音 232,454 條 + 聲母簡拼 40,099 條 + 英文 20,000 詞 + 拼音 2,000 字 + 倉頡 700 碼 + 日文 600 漢字 |
| 支援語言 | 英文 / 注音 / 拼音 / 倉頡 / 速成 / 日文 / 韓文（7 種） |
| 最低 SDK | 26 (Android 8.0) |
| 編譯 SDK | 35 |
| 資料庫 | YukiBoard 獨立 Supabase (Zeabur) |

---

## 已完成模組總覽

### 核心輸入系統

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| IME 主服務 | `service/YukiBoardIMEService.kt` | ✅ | InputMethodService 主入口，整合所有子系統 |
| 輸入路由 | `input/router/InputRouter.kt` | ✅ | 7 模式循環切換（EN/注/拼/倉/速/日/韓/AUTO） |
| 英文引擎 | `input/engine/EnglishInputEngine.kt` | ✅ | composing buffer + Trie 候選 |
| 注音引擎 | `input/engine/ChewingInputEngine.kt` | ✅ | McBopomofo 字典，多音節分音 |
| 拼音引擎 | `input/engine/PinyinInputEngine.kt` | ✅ | 音節分割 + 模糊拼音 + 2000 高頻字 + 詞組 |
| 倉頡引擎 | `input/engine/CangjieInputEngine.kt` | ✅ | 700+ 字碼，支援速成首尾碼匹配 |
| 日文引擎 | `input/engine/JapaneseInputEngine.kt` | ✅ | romaji→kana→kanji，130+ 映射 + 600 漢字 |
| 韓文引擎 | `input/engine/KoreanInputEngine.kt` | ✅ | Dubeolsik Jamo 4 態組合 + 複合母音/終聲 |
| 英文字典 | `input/dictionary/EnglishDictionary.kt` | ✅ | 20,000 高頻詞 Trie + 自動更正 |
| 使用者詞庫 | `input/dictionary/UserDictionary.kt` | ✅ | 學習 + SharedPreferences 持久化 |
| 候選管理 | `input/candidate/CandidateManager.kt` | ✅ | 候選列統一管理 |
| Snippet 系統 | `input/snippet/SnippetManager.kt` | ✅ | 開發者模式 Tab 展開 |

### 智慧輸入

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| 智慧總管 | `input/smart/SmartInputManager.kt` | ✅ | 統合糾錯+學詞+預測+習慣 |
| 智慧糾錯 | `input/smart/SmartCorrectionEngine.kt` | ✅ | 三階段：鄰近鍵→編輯距離→bigram 上下文 |
| 自適應字典 | `input/smart/AdaptiveDictionary.kt` | ✅ | 常用詞自動學習，CJK 支援，30 天淘汰 |
| 語句預測 | `input/smart/PhrasePredictor.kt` | ✅ | bigram + trigram 學習式預測 |
| 習慣追蹤 | `input/smart/TypingHabitTracker.kt` | ✅ | 時段/App 詞頻 + 打字速度 |
| AI 預測介面 | `input/predict/NextActionPredictor.kt` | ✅ | 本地規則引擎 + Claw AI 增強介面 |

### 鍵盤 UI

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| 主鍵盤 View | `ui/keyboard/KeyboardView.kt` | ✅ | Canvas 自繪鍵盤 + 觸控 + 長按 + 候選列 |
| 鍵盤狀態 | `ui/keyboard/KeyboardState.kt` | ✅ | 7 語言佈局 + 數字 + 符號 + 設計者 + 開發者 |
| 鍵盤模式 | `ui/keyboard/KeyboardMode.kt` | ✅ | NORMAL / DESIGNER / DEVELOPER / GAME |
| 模式環形選單 | `ui/keyboard/RadialModeMenu.kt` | ✅ | 長按 Tab 彈出模式切換 |
| 語言環形選單 | `ui/keyboard/RadialLanguageMenu.kt` | ✅ | 長按「切換」鍵彈出 8 語言選擇 |
| 角色 View | `ui/keyboard/CharacterView.kt` | ✅ | Lottie + VRM 雙模式渲染 |
| 高度調整 | `ui/keyboard/KeyboardResizeHandle.kt` | ✅ | 拖曳把手（25%~55%） |
| 懸浮鍵盤 | `ui/keyboard/FloatingKeyboardWrapper.kt` | ✅ | 可拖動 + 固定 / 關閉 |
| 按鍵裝飾 | `ui/keyboard/KeyDecorator.kt` | ✅ | 按鍵視覺裝飾 |
| 鍵盤背景 | `ui/keyboard/KeyboardBackgroundView.kt` | ✅ | 動態粒子背景 / 自訂圖片影片 |

### 面板系統（10 面板 + 工具列）

| 面板 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| 工具列 | `ui/panel/ToolbarView.kt` | ✅ | 膠囊按鈕 + 3 分組 + 動畫 + 水平捲動 |
| Emoji | `ui/panel/EmojiPanelView.kt` | ✅ | 9 分類 700+ emoji |
| 顔文字 | `ui/panel/KaomojiPanelView.kt` | ✅ | 9 分類 130+ kaomoji |
| 剪貼簿 | `ui/panel/ClipboardPanelView.kt` | ✅ | 歷史記錄 + 釘選 + 刪除 |
| 翻譯 | `ui/panel/TranslatePanelView.kt` | ✅ | 7 語言 + 交換（引擎預留） |
| GIF | `ui/panel/GifPanelView.kt` | ✅ | 趨勢標籤 + 網格（API 預留） |
| 貼圖 | `ui/panel/StickerPanelView.kt` | ✅ | 貼圖包 + 網格 |
| 主題 | `ui/panel/ThemePanelView.kt` | ✅ | 即時預覽切換 |
| 遠端鍵盤 | `ui/panel/RemoteKeyboardPanelView.kt` | ✅ | 藍牙/WiFi 控制面板 |
| Claw AI | `ui/panel/ClawPanelView.kt` | ✅ | AI 技能面板 |

### 動畫系統

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| GL 流光 Shader | `animation/gles/FlowLightShader.kt` | ✅ | GLSL 三道光波疊加 |
| GL Renderer | `animation/gles/FlowLightRenderer.kt` | ✅ | 每幀更新 uTime |
| GL 覆蓋層 | `animation/gles/KeyboardGLSurfaceView.kt` | ✅ | 透明 RGBA_8888 疊加 + 觸控穿透 |
| Lottie 動畫 | `animation/LottieCharacterAnimation.kt` | ✅ | idle/typing/happy/listening/switch |
| VRM 角色 | `animation/VrmCharacterView.kt` | ✅ | Filament 3D 渲染 + 9 程序化動作 + 表情 + 自動眨眼 |
| VRM 動畫 | `animation/VrmCharacterAnimation.kt` | ✅ | 鍵盤事件→VRM 動作+表情映射 |
| 粒子系統 | `animation/KeyboardParticleView.kt` | ✅ | 動態粒子背景 + 觸控穿透 |

### 感測器 / 語音 / 遠端

| 模組 | 檔案 | 狀態 | 說明 |
|------|------|------|------|
| 搖晃偵測 | `sensor/ShakeDetector.kt` | ✅ | 加速度計 ≥2.5G → Ctrl+Z |
| 陀螺儀游標 | `sensor/GyroscopeCursorController.kt` | ✅ | 長按空白鍵 + 傾斜 |
| App 偵測 | `sensor/AppModeDetector.kt` | ✅ | packageName 自動切模式 |
| 語音引擎 | `speech/AndroidSpeechEngine.kt` | ✅ | Android SpeechRecognizer |
| 藍牙 HID | `remote/BluetoothHidKeyboard.kt` | ✅ | BluetoothHidDevice API |
| WiFi WebSocket | `remote/WifiKeyboardServer.kt` | ✅ | HTTP/WS Server + 控制網頁 |

### 遊戲模式（20 款）

貪食蛇、打地鼠、節奏、打字挑戰、彈幕迷你、情侶問答、冰晶收集、踩地雷、鍵盤旅人、記憶翻牌、心情挖礦、隨機挑戰、拼字對決、打字競速、語音遊戲、戰艦巡航、語詞守護、Yuki 問答 — 全部 ✅

### SnowRealmClaw AI（19 個 Skill）

AI 預測、動畫控制、程式補全、Commit 訊息、上下文感知、丘比特、Emoji 翻譯、文法檢查、迷因文字、心情日記、快速回覆、Regex 生成、摘要、語氣調整、翻譯、天氣、悄悄話、寫作助手、Yuki 聊天 — 全部 ✅

### 後端 & 帳號

| 模組 | 檔案 | 狀態 |
|------|------|------|
| Auth Client | `auth/SnowRealmAuthClient.kt` | ✅ |
| 邀請碼管理 | `auth/InviteCodeManager.kt` | ✅ |
| Supabase Client | `data/SupabaseClient.kt` | ✅ |
| 登入頁 | `ui/auth/LoginActivity.kt` | ✅ |
| AI Provider | `claw/provider/SnowRealmProvider.kt` | ✅ |

### 資料庫（YukiBoard 獨立 Supabase）

| 表名 | 狀態 | 說明 |
|------|------|------|
| `admin_users` | ✅ | 管理員 + yukiboard_privilege |
| `yukiboard_invite_codes` | ✅ | 邀請碼（standard/kol/permanent） |
| `yukiboard_invite_usages` | ✅ | 邀請碼使用紀錄 |
| `yukiboard_ai_usage` | ✅ | AI token 用量追蹤 |

---

## 開發時間軸

### 2026-04-03 — 專案建立 + 核心功能大爆發

**Commit: `ec007eb` — YukiBoard IME complete feature set**
- 完整 Android IME 架構（MVVM + Clean Architecture）
- Canvas 自繪鍵盤 + 三模式 + 英文字典 + GL 流光 + Lottie 角色
- 感測器 + 語音 + 9 面板 + Snippet + 懸浮模式 + 智慧輸入

**Commit: `3f3dbae` — Bluetooth HID + WiFi WebSocket**

**Commit: `6421ed3` — liquid glass keys, 8 themes**

**Commit: `e152f19` — Bopomofo dictionary + AI Claw 19 skills**

### 2026-04-03 ~ 04-04 — 中文輸入大幅強化

- McBopomofo 字典整合（232,454 條 + 聲母簡拼 40,099 條）
- 英文字典升級（20,000 高頻詞）
- 候選展開面板（▼ 按鈕 + 5 欄網格 + 捲動）
- 智慧輸入 CJK 支援
- 數字/符號子佈局
- Firebase App Distribution + deploy.sh

### 2026-04-04 — v0.2.0 多語言 + VRM + 重設計

**Commit: `a6ee247` — multi-language input + VRM + toolbar redesign**
- 新增拼音、倉頡/速成、日文、韓文 4 個輸入引擎
- InputRouter 7 模式循環切換 + lazy 載入
- CharacterView 改為 Lottie + VRM 雙模式
- VrmCharacterView：Filament 3D + 9 程序化骨骼動畫 + 表情系統
- 工具列膠囊按鈕重設計（3 分組 + 動畫 + 水平捲動）
- 觸控穿透修復 + IC batch edit 修復
- Auth/Claw/Provider 整合

**Commit: `765a400` — radial language menu**
- 長按「切換」鍵環形語言選單（8 選項含 Emoji）

**Commit: `e9bb75a` — Supabase migrations**
- YukiBoard 獨立 Supabase 建表（4 張表）

**Commit: `bd75a28` — version bump v0.2.0**
- versionCode 1→2, versionName 0.1.0→0.2.0
- todo list 全面整理

**Firebase App Distribution — v0.2.0 發布**

---

## 技術債

| 項目 | 說明 |
|------|------|
| CRLF 警告 | Windows 環境 LF/CRLF 混用 |
| 字典載入效能 | 23 萬條注音字典冷啟動（lazy 已處理） |
| SharedPreferences 瓶頸 | 自適應字典/習慣追蹤高頻寫入 |
| Supabase 連線使用者名稱 | .env 裡 YukiBoard 用戶認證失敗，需用 postgres |

---

*最後更新：2026-04-04*

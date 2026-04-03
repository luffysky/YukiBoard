# YukiBoard 全任務統合清單

> 整合 CLAUDE.md 舊債 + Claw v2 殘留 + 最終任務包 v7 + 決策文件
> 最後更新：2026-04-03

---

## 架構防撞規則（所有批次必須遵守）

| 衝突點 | 規則 | 已修正 |
|--------|------|:------:|
| Token 額度 | **唯一扣款源**：InviteCodeManager（邀請碼）或 Z幣。TokenBudgetManager 僅統計用 | ✅ |
| 自帶 API Key | 自帶 Key = 無限額度，但 TokenBudgetManager 仍記錄統計 | ✅ |
| 內嵌 vs 懸浮面板 | PanelCoordinator 統一協調，同功能只開一處。懸浮開→內嵌關 | ✅ |
| 語音輸入 | 統一入口 = AndroidSpeechEngine。WhisperSkill = 後處理增強（不是獨立入口） | ✅ |
| 情緒系統 | 統一數據源 EmotionState。ToneDetector 寫入 / MoodDiary 回應 / Cooldown 攔截 / MoodMiner 統計 | ✅ |
| 預測系統 | AIPredictionSkill = 補字。NextActionPredictor = 補動作。候選詞 UI 統一呈現 | ✅ |
| 貼圖/遊戲面板 | 懸浮版內部複用內嵌版 View，不重寫 UI | — |
| 角色渲染 | CharacterAnimation 介面 + prefs 開關選擇 Lottie/VRM 實作 | — |
| Auth 三階段 | Guest(自帶Key) → InviteCode(免費額度) → SnowRealmAccount(Z幣)，可並存 | — |
| 設計者快捷鍵 | 同模式內不得有重複 Ctrl/Shift/Alt 組合。DESIGN_LOCK=Ctrl+Shift+K, ALIGN_L=Ctrl+Shift+L | ✅ |
| 跨模式快捷鍵 | 不同模式(Designer/Developer)可共用同組合鍵，因同時只有一個模式啟用 | ✅ |
| Batch 4 偏好開關 | 微吐槽/冷靜期/語氣偵測/私房話必須在 IMEService 呼叫前檢查 YukiPreferences 對應開關 | ⏳ |
| Claw Toolbar vs Client | toolbar 按鈕 ID 必須對應 ClawClient skills map（context 為內部 skill 不需按鈕） | ✅ |
| 長按衝突 | Tab 長按=模式選單，Space 長按=陀螺儀。兩者不衝突，但不可再新增同鍵長按功能 | ✅ |
| 搖晃感測器 | ShakeDetector 必須先檢查 prefs.shakeToUndo，啟停跟隨 InputView 生命週期 | ✅ |
| 智慧糾錯 vs 自動更正 | autoCorrect = EnglishDictionary 基本糾錯。smartCorrection = 三階段 AI 糾錯。兩者並存，smart 結果優先顯示 | ✅ |
| Tab 鍵三重用途 | 優先序：①Snippet 展開(Dev)→②候選 Tab 導航→③發送 Tab 鍵。不可重複觸發 | ✅ |
| 候選列合併順序 | 糾錯候選(前) → 字典候選(中) → 自適應字典(中後) → 語句預測(尾) | ✅ |

---

## 總覽

| 批次 | 主題 | 項目數 | 量級 | 狀態 |
|:----:|------|:------:|:----:|:----:|
| 1 | 基礎設施 | 4 | 大 | ✅ |
| 2 | AI Skill 全面升級 | 8 | 大 | ✅ |
| 3 | 懸浮面板群 | 4 | 中 | |
| 4 | 雪凜人格系統 | 3 | 中 | |
| 5 | API 串接 + 環境 | 3 | 中 | |
| 6 | 遊戲 + 美術 | 2 | 超大 | ✅ |
| 7 | 帳號 + Z幣 + 後端串接 | 4 | 大 | |
| 8 | 3D / AR / VR | 3 | 超大 | |
| 9 | 連線對戰 + 跨 App 聯動 | 4 | 大 | |
| 10 | 收尾（教學） | 1 | 中 | |

---

## 第一批 — 基礎設施（其他功能依賴這些）

### B1. 全功能開關設定系統 `[v7 Phase 1]` ✅
- [x] YukiPreferences.kt 加入所有開關（AI / 雪凜行為 / 感測器 / 動畫 / 遊戲 / 懸浮面板 / 輸入行為 / 連線）
- [x] BoolPref 委派實作（SharedPreferences 讀寫）
- [x] SettingsActivity.kt 重構為六群組 CardView 佈局
  - [x] AI 功能（主開關 + 18 子項目）
  - [x] 雪凜行為（5 項）
  - [x] 感測器（3 項）
  - [x] 動畫特效（5 項）
  - [x] 遊戲（3 項）
  - [x] 輸入行為（7 項）
- [x] 主開關關閉時子項目灰化（isEnabled = false）
- [ ] 每個功能執行前檢查對應開關（各 Phase 實作時逐步加入）

### B2. 懸浮面板基礎架構 `[v7 Phase 2]` ✅
- [x] FloatingPanelConfig.kt（min/max/default 尺寸、吸附閾值）
- [x] FloatingPanel.kt 抽象基礎類別（show/hide/dismiss/moveTo/resize/snapToEdge）
- [x] FloatingPanelManager.kt（showPanel/hidePanel/togglePanel/dismissAll + 設定開關檢查）
- [x] DragGestureHandler.kt（標題列拖曳 + WindowManager.LayoutParams 更新）
- [x] PinchGestureHandler.kt（雙指縮放，min 200×120dp / max 半屏）
- [x] ResizeHandle.kt（右下角 ◢ 24×24dp 拖曳調整寬高）
- [x] 靠邊吸附動畫（40dp 閾值，200ms 動畫）
- [x] AndroidManifest.xml 新增 SYSTEM_ALERT_WINDOW + 定位權限

### B13. RadialModeMenu 加入 GAME 模式 `[v7 Phase 13]` ✅
- [x] 從三選項改為四選項（90 度間隔）— 已有，KeyboardMode.entries 含 4 個
- [x] 新增 ModeOption(GAMING, "遊戲", "🎮") — 已在 KeyboardMode enum
- [x] 角度計算：sweepAngle = 360f / sectorCount = 90f

### B15. Lottie 佔位動畫 `[v7 附加]` ✅
- [x] yuki_freeze.json（情緒冷靜期結冰特效 — 六角冰晶 + 霜環）
- [x] yuki_battle_idle.json（遊戲模式待機 — 五角盾 + 能量脈衝）
- [x] yuki_cupid.json（情聖月老 — 粉紅愛心 + 閃光星）
- [x] 動畫不存在時 fallback 到 yuki_happy.json（LottieCharacterAnimation.playReaction）

---

## 第二批 — AI Skill 全面升級

### B10. 12 個新 Skill `[v7 Phase 10]` ✅

**Tier 1 — 高頻剛需**
- [x] GrammarCheckSkill.kt — 文法糾錯（200 token，prefs.aiGrammarCheck）
- [x] ToneAdjustSkill.kt — 語氣調整 四種語氣（300 token，prefs.aiToneAdjust）
- [x] SummarizeSkill.kt — 摘要精簡（300 token，prefs.aiSummarize）

**Tier 2 — 社群場景**
- [x] MemeTextSkill.kt — 迷因/流行語生成（200 token，prefs.aiMemeText）
- [x] EmojiTranslateSkill.kt — 文字 ↔ Emoji 互轉（100 token，prefs.aiEmojiTranslate）
- [x] QuickReplySkill.kt — 快速回覆建議 整句（400 token，prefs.aiQuickReply）

**Tier 3 — 開發者模式限定**
- [x] CodeCompleteSkill.kt — 程式碼補全（500 token，prefs.aiCodeComplete）
- [x] RegexGenSkill.kt — 正則產生器（300 token，prefs.aiRegexGen）
- [x] CommitMessageSkill.kt — Commit 訊息產生（400 token，prefs.aiCommitMessage）

**Tier 4 — 角色深度整合**
- [x] YukiChatSkill.kt — 雪凜聊天 善意毒舌風格（600 token，prefs.aiYukiChat）
- [x] MoodDiarySkill.kt — 心情日記 被動偵測（0-200 token，prefs.aiMoodDiary）
- [x] WeatherSkill.kt — 天氣助理（0-300 token，prefs.aiWeather）

### B11. Prompt 模板補完 `[v7 Phase 11]` ✅
- [x] claw_grammar_prompt.txt
- [x] claw_meme_prompt.txt
- [x] claw_yuki_chat_prompt.txt
- [x] claw_weather_outfit_prompt.txt
- [x] claw_random_challenge_prompt.txt
- [x] claw_yuki_rating_prompt.txt

### B12. ClawToolbar 更新 `[v7 Phase 12]` ✅
- [x] 新增天氣按鈕（prefs.weatherPanelEnabled）
- [x] 新增遊戲按鈕（prefs.gamesEnabled）
- [x] Token 顯示（右側，綠/橙/紅三色）
- [x] 根據 KeyboardMode 動態顯示 Skill 按鈕（NORMAL/DESIGNER/DEVELOPER/GAMING）
- [x] 水平捲動支援（18 個按鈕可橫向滑動）

### A7. 邀請碼系統補完 `[Claw v2 殘留]` ✅
- [x] InviteCodeActivity.kt — 邀請碼輸入界面（Lottie 角色 + 驗證 + 跳 AI 設定）
- [x] generate_invite_codes.py — 產碼腳本（STANDARD/KOL/PERMANENT 三種類型）
- [x] TokenBudgetManager.kt 每日重置邏輯（跨日自動歸零 + 重置時間記錄）

### A4. WhisperSkill 實作 `[Claw v2 殘留]` 🔜
- [ ] 音頻錄製（MediaRecorder / AudioRecord）
- [ ] OpenAI Whisper API 串接（multipart/form-data 上傳）
- [ ] 錄音 UI 整合到語音面板
- [x] WhisperSkill.kt stub 已存在（TODO 標記）

### A6/B9. NextActionPredictor 升級 `[CLAUDE.md + v7 Phase 9]` ✅
- [x] 本地規則引擎升級（括號配對 + snippet + App 感知 + 問句/長文偵測）
- [x] ClawEnhancedPredictor（本地規則 + AI 回調合併，高信心不呼叫 AI 節省 token）
- [ ] 候選詞列顯示預測結果（需 KeyboardView 整合）

---

## 第三批 — 懸浮面板群

### B3. 天氣懸浮面板 `[v7 Phase 3]`
- [ ] WeatherData.kt（溫度/濕度/風速/降雨率/6小時預報）
- [ ] WeatherCondition enum（SUNNY/CLOUDY/RAINY/STORMY/SNOWY/FOGGY/WINDY）
- [ ] WeatherManager.kt（OpenWeatherMap API + 30 分鐘快取）
- [ ] getYukiComment() 雪凜天氣短句（本地，0 token）
- [ ] getQuickPhrases() 快速短語/穿衣建議
- [ ] WeatherFloatingPanel.kt（位置 + 溫濕度 + 6小時橫向滾動 + 雪凜短句）
- [ ] 穿衣建議需邀請碼解鎖的 UI 狀態（未解鎖顯示 🔒，點擊跳 InviteCodeActivity）
- [ ] build.gradle 加 play-services-location + OPENWEATHER_API_KEY
- [ ] .env 加 OPENWEATHER_API_KEY

### B4. Claw 結果懸浮面板 `[v7 Phase 4]`
- [ ] ClawResultFloatingPanel.kt
- [ ] showResult(skillName, results) 外部呼叫
- [ ] Streaming typewriter 效果（逐字顯示）
- [ ] 版本切換（◀ 1/3 ▶）
- [ ] [複製] / [✓ 插入輸入框] 按鈕

### B6. 貼圖懸浮面板 `[v7 Phase 6]`
- [ ] StickerFloatingPanel.kt（繼承 FloatingPanel，複用 StickerPanelView）
- [ ] 分頁：免費 / 角色 / 節日 / 全部
- [ ] 4 欄網格 + 稀有度角標（N灰/R藍/SR紫/SSR金）

### A3. 貼圖包下載管理 `[CLAUDE.md 殘留]`
- [ ] 貼圖包清單 API 串接（Insight Engine）
- [ ] 下載進度 UI
- [ ] 本地快取管理（StickerImageCache 已有基礎）

---

## 第四批 — 雪凜人格系統

### B7. 雪凜微吐槽系統 `[v7 Phase 7]`
- [ ] YukiCommentEngine.kt
- [ ] 觸發事件：同字打錯3次 / 長文全刪 / 打字變慢 / 深夜打字 / 遊戲輸了（打字競速等）
- [ ] 每種事件 3+ 條隨機短句
- [ ] 10 分鐘冷卻機制
- [ ] 雪凜上方說話泡泡（2-3秒淡出）
- [ ] 受 prefs.yukiSarcasticComments 控制

### B8. 情緒冷靜期 `[v7 Phase 8]`
- [ ] EmotionalCooldownManager.kt
- [ ] 觸發詞庫（中文 + 英文激烈用語）
- [ ] 觸發流程：雪凜結冰動畫 → 鍵盤鎖定 1 秒 → 確認對話框
- [ ] 對話框：「等一下。這句話確定要發嗎？」→「算了不發」/「還是發吧」
- [ ] 受 prefs.yukiEmotionalCooldown 控制

### B14. 私房話模式 + 語氣偵測 `[v7 Phase 14]`
- [ ] SecretMessageManager.kt — AES-256-GCM 加解密
- [ ] 加密格式：`[YB🔐] {base64}`
- [ ] 候選詞列特殊加密按鈕
- [ ] 受 prefs.secretMessageMode 控制
- [ ] ToneDetector.kt — 本地關鍵字情緒分析（0 token）
- [ ] Tone enum（POSITIVE/NEGATIVE/NEUTRAL/ANGRY/SAD/EXCITED）
- [ ] NEGATIVE/SAD 時雪凜回應
- [ ] 受 prefs.toneDetection 控制

---

## 第五批 — API 串接 + 環境

### A1. GIF 引擎接 Giphy API `[CLAUDE.md 殘留]`
- [ ] GiphyApiClient.kt（trending + search）
- [ ] API Key 從 BuildConfig.GIPHY_API_KEY 讀取（.env 已有）
- [ ] GifPanelView 串接（現有 UI 已有趨勢標籤 + 網格）
- [ ] GIF 圖片載入 + 快取

### A2. 翻譯引擎接 MLKit `[CLAUDE.md 殘留]`
- [ ] MLKit Translation 依賴加入 build.gradle
- [ ] TranslateEngine.kt 實作（下載離線模型）
- [ ] TranslatePanelView 串接（現有 7 語言 UI）
- [ ] 離線翻譯模型管理（下載/刪除）

### A8. .env.example 範本檔案 `[環境設定]`
- [ ] 建立 .env.example 列出所有需要填的 API Key（值留空）
- [ ] 包含：OPENAI_API_KEY / ANTHROPIC_API_KEY / INVITE_HMAC_SECRET / PLATFORM_API_KEY / INSIGHT_API_BASE_URL / GIPHY_API_KEY / OPENWEATHER_API_KEY
- [ ] 加入 README 或 CLAUDE.md 說明

### B11. 智慧輸入系統 `[新增功能]` ✅
- [x] SmartCorrectionEngine.kt — 三階段糾錯（鄰近鍵 + 編輯距離 + bigram 上下文）
- [x] AdaptiveDictionary.kt — 自動學詞（使用 ≥3 次提升為已知詞，30 天未用淘汰）
- [x] PhrasePredictor.kt — bigram + trigram 語句預測
- [x] TypingHabitTracker.kt — 時段/App 詞頻追蹤 + 打字速度
- [x] SmartInputManager.kt — 統合四模組，接入 InputRouter
- [x] InputRouter 整合（糾錯候選 + 語句預測追加到候選列）
- [x] CandidateManager Tab 導航（focusNext + confirmFocused）
- [x] YukiPreferences 四項開關（smartCorrection / adaptiveDict / phrasePredict / habitTracking）
- [x] SettingsActivity 智慧輸入設定卡片
- [x] YukiBoardIMEService 同步開關 + Tab 候選導航 + 空白鍵確認
- [x] onFinishInputView 持久化 n-gram / 習慣資料

---

## 第六批 — 遊戲 + 美術

### B5. 遊戲懸浮面板 + 14 款新遊戲 `[v7 Phase 5]` ✅

**懸浮面板**
- [x] GameFloatingPanel.kt（320×260dp，分類 Tab：經典/動作/益智/挑戰）
- [x] GameMenuView 升級為分類 Tab + 動態卡片網格
- [x] GameState.kt GameCategory enum + GameMode 擴充至 18 種

**新遊戲 — 動作（5 款）**
- [x] WordGuardianGame.kt — 詞彙守護者（塔防 + 打字消滅）
- [x] KeyboardTravelerGame.kt — 鍵盤旅行者（跑酷 + 打字加速）
- [x] BulletHellMiniGame.kt — 極簡彈幕（閃避 + 點按傳送）
- [x] IceCrystalGame.kt — 冰晶收集（45 秒限時 + 稀有💎）
- [x] WarshipCruiseGame.kt — 戰艦巡航（打字動力 + 擊沉敵艦）

**新遊戲 — 益智（5 款）**
- [x] SpellingDuelGame.kt — 拼字對決（打散字母拼回）
- [x] KeyboardMinesweeperGame.kt — 按鍵掃雷（遞迴展開 + 勝利判定）
- [x] MemoryFlipGame.kt — 記憶翻牌（20 種 emoji 配對）
- [x] YukiQuizGame.kt — 雪凜問答（12 題常識/科技/語文）
- [x] CoupleQuizGame.kt — 情侶測驗（10 題 + 模擬對方回答）

**新遊戲 — 挑戰（3 款）**
- [x] TypingRaceGame.kt — 打字競速 v1.0 單機版（WPM + 準確率）
- [x] RandomChallengeGame.kt — 隨機挑戰（重複/序列/迴避 3 種模式）
- [x] VoiceGame.kt — 語音遊戲 stub（依賴 WhisperSkill）

**被動收集**
- [x] MoodMinerGame.kt — 情緒採礦（5 種情緒礦石 + 60 秒）

**整合**
- [x] YukiBoardIMEService 支援全 18 種遊戲實例化 + 事件回呼
- [x] setGameEventCallback() 統一處理

### A5. Lottie 角色動畫升級 `[CLAUDE.md 殘留]`
- [ ] 現有幾何佔位動畫 → 正式雪凜角色設計（需設計師素材）
- [ ] yuki_idle / typing / happy / listening / switch_lang 全套升級

---

## 完整 Skill 清單（完成後共 19 個）

| # | ID | 名稱 | 狀態 | Token | 模式 |
|---|---|---|:---:|:---:|---|
| 1 | ai_prediction | AI 補字 | ✅ | 100 | 全部 |
| 2 | writer | 雪凜寫手 | ✅ | 800 | 全部 |
| 3 | translate | 即時翻譯 | ✅ | 300 | 全部 |
| 4 | cupid | 情聖月老 | ✅ | 600 | 全部 |
| 5 | context | 情境感知 | ✅ | 0 | 全部 |
| 6 | animation_suggest | 角色動畫 | ✅ | 50 | 全部 |
| 7 | whisper | AI 語音 | 🔜 | 500 | 全部 |
| 8 | grammar_check | 文法糾錯 | ✅ | 200 | 全部 |
| 9 | tone_adjust | 語氣調整 | ✅ | 300 | 全部 |
| 10 | summarize | 摘要精簡 | ✅ | 300 | 全部 |
| 11 | meme_text | 迷因生成 | ✅ | 200 | 全部 |
| 12 | emoji_translate | Emoji 翻譯 | ✅ | 100 | 全部 |
| 13 | quick_reply | 快速回覆 | ✅ | 400 | 全部 |
| 14 | code_complete | 程式碼補全 | ✅ | 500 | 開發者 |
| 15 | regex_gen | 正則產生器 | ✅ | 300 | 開發者 |
| 16 | commit_message | Commit 訊息 | ✅ | 400 | 開發者 |
| 17 | yuki_chat | 雪凜聊天 | ✅ | 600 | 全部 |
| 18 | mood_diary | 心情日記 | ✅ | 0-200 | 全部 |
| 19 | weather | 天氣助理 | ✅ | 0-300 | 全部 |

---

## 第七批 — 帳號 + Z幣 + 後端串接

### B17. SnowRealm 帳號登入 `[決策文件 §3]`
- [ ] `auth/SnowRealmAuthClient.kt` — 打 Insight API（`/api/auth/login`、`/api/auth/me`）
- [ ] JWT token 存入 EncryptedSharedPreferences
- [ ] `ui/auth/LoginActivity.kt` — Email / LINE Login / Google 三種登入方式
- [ ] 登入狀態管理（token 過期自動重新驗證）
- [ ] build.gradle 加 INSIGHT_API_BASE_URL（.env 已有）

### B18. YukiBoard Supabase 整合 `[決策文件 §1]`
- [ ] build.gradle 加 Supabase Android SDK 依賴
- [ ] `data/SupabaseClient.kt` — 初始化（YUKIBOARD_SUPABASE_URL + ANON_KEY）
- [ ] .env 加 YUKIBOARD_SUPABASE_URL / YUKIBOARD_SUPABASE_ANON_KEY
- [ ] build.gradle 讀取 .env 注入 BuildConfig

### B19. Z幣系統 `[決策文件 §2]`
- [ ] `currency/ZCoinManager.kt` — 本地錢包（餘額查詢、消費、儲值記錄）
- [ ] `currency/ZCoinApi.kt` — Supabase 同步（餘額 + 交易紀錄）
- [ ] `currency/NewebpayClient.kt` — 藍新金流串接（儲值頁面 WebView）
- [ ] `ui/currency/ZCoinWalletActivity.kt` — 錢包頁面（餘額 + 紀錄 + 儲值按鈕）
- [ ] SnowRealmProvider.kt 移除 NotImplementedError，接 Z幣扣款
- [ ] .env 加 NEWEBPAY_MERCHANT_ID / HASH_KEY / HASH_IV

### B20. 皮膚商城 `[決策文件 §5]`
- [ ] `store/SkinManager.kt` — 皮膚載入/套用/解鎖管理
- [ ] `store/SkinPackage.kt` — 資料模型（id/name/price/preview/themeConfig）
- [ ] `store/StoreApi.kt` — Supabase 商品列表 + 購買紀錄
- [ ] `ui/store/SkinStoreActivity.kt` — 商城頁面（分類 Tab：免費/Z幣/限定）
- [ ] 免費預設皮膚 3 款（深空藍、雪域白、凜空紫）
- [ ] 貼圖包購買整合（接 InsightStickerApi + Z幣扣款）
- [ ] 表情包解鎖（Emoji 風格包、顔文字包）

---

## 第八批 — 3D / AR / VR

### B21. VRM 3D 角色 `[決策文件 §4]`
- [ ] build.gradle 加 Filament / Sceneform 依賴
- [ ] `vrm/VrmRenderer.kt` — VRM 模型載入 + 渲染
- [ ] `vrm/VrmAnimationController.kt` — 表情/動作控制（對應 Lottie 事件）
- [ ] `ui/character/VrmCharacterView.kt` — 替代 CharacterView 的 3D 版本
- [ ] 模型素材從 Insight 取用（API 下載 or bundled asset）
- [ ] Lottie ↔ VRM 切換開關（prefs 控制）

### B22. AR 雪凜 `[決策文件 §6]`
- [ ] build.gradle 加 ARCore 依賴
- [ ] `ar/ArSessionManager.kt` — ARCore session 管理 + 平面偵測
- [ ] `ar/ArYukiRenderer.kt` — 錨點放置 + VRM 模型渲染
- [ ] `ui/ar/ArYukiActivity.kt` — AR 相機畫面 + 雪凜放置互動
- [ ] 3D 模型與 VRM 共用

### B23. SnowRealmVR 輸入介面
- [ ] `vr/VrKeyboardRenderer.kt` — VR 空間鍵盤佈局
- [ ] `vr/VrInputController.kt` — 射線/手勢輸入控制
- [ ] `vr/VrYukiCompanion.kt` — VR 空間雪凜陪伴
- [ ] VR SDK 框架（OpenXR or Meta Quest SDK 預留）

---

## 第九批 — 連線對戰 + 跨 App 聯動

### B24. 打字競速 v2.0 連線版 `[決策文件]`
- [ ] `game/multiplayer/TypingRaceOnlineGame.kt` — 連線對戰邏輯
- [ ] `game/multiplayer/MatchmakingManager.kt` — Supabase Realtime 配對
- [ ] `game/multiplayer/RaceRoomManager.kt` — 房間建立/加入/觀戰
- [ ] Supabase Realtime channel 即時同步（打字進度、WPM）
- [ ] 排行榜（Supabase 查詢 + UI）
- [ ] 先做本地模擬框架，Supabase 連線後接

### B25. CoinBeast 跨 App 聯動 `[決策文件 §7]`
- [ ] `crossapp/DeepLinkHandler.kt` — Deep Link 接收處理
- [ ] Deep Link scheme 註冊：`yukiboard://reward`, `yukiboard://sync`
- [ ] `crossapp/CoinBeastBridge.kt` — 遊戲獎勵 Z幣同步（預留接口）
- [ ] AndroidManifest 加 intent-filter

### B26. 毛行天下聯動 `[決策文件 §7]`
- [ ] `crossapp/MaoxingBridge.kt` — 鍵盤旅行者打字距離同步
- [ ] Deep Link scheme：`yukiboard://distance`
- [ ] KeyboardTravelerGame 距離數據輸出接口

### B27. 情緒採礦週報接 Insight `[決策文件 §8]`
- [ ] Room DB 情緒紀錄（MoodEntry entity + DAO）
- [ ] `mood/MoodReportGenerator.kt` — 週報生成（7天統計 + 圖表數據）
- [ ] `ui/mood/MoodReportActivity.kt` — 週報頁面
- [ ] 可選上傳到 Insight API 分析（登入後才能用）

---

## 第十批 — 收尾

### B16. 設定頁內建完整使用教學 `[收尾]`
- [ ] 在 SettingsActivity 新增「使用教學」入口（卡片或按鈕）
- [ ] TutorialActivity.kt — 全功能詳細教學頁面（ScrollView 分章節）
  - [ ] 基礎操作：QWERTY / 注音切換、候選詞選擇、空白鍵長按游標
  - [ ] 三模式系統：一般 / 設計者 / 開發者模式說明 + 長按 Tab 環形選單切換
  - [ ] 設計者模式快捷鍵一覽（復原/群組/對齊/圖層...）
  - [ ] 開發者模式快捷鍵 + Snippet 系統（fn/if/for/cl + 自訂）
  - [ ] 感測器功能：搖手機復原、陀螺儀游標、App 自動切模式
  - [ ] 工具列 9 面板說明（語音/翻譯/GIF/表情/顔文字/貼圖/剪貼簿/主題/設定）
  - [ ] AI 功能教學：19 個 Skill 說明 + 邀請碼 vs 自帶 API Key
  - [ ] 雪凜角色互動：微吐槽 / 情緒冷靜期 / 動畫反應
  - [ ] 懸浮面板：天氣 / Claw 結果 / 貼圖 / 遊戲（拖曳/縮放/吸附）
  - [ ] 遊戲模式：各遊戲玩法說明
  - [ ] 鍵盤自訂：高度/寬度調整、懸浮模式、主題切換
  - [ ] 遠端鍵盤：藍牙 HID / WiFi WebSocket 使用方式
  - [ ] 私房話模式 + 語氣偵測
  - [ ] 進階設定：各開關功能說明
  - [ ] Z幣儲值 + 皮膚商城使用方式
  - [ ] 帳號登入 + 跨 App 聯動說明
  - [ ] AR / VRM 3D 角色操作說明

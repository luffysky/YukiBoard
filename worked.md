# YukiBoard — 完成紀錄 (worked.md)

> 最後更新：2026-04-03
> 涵蓋範圍：Batch 1 + Batch 2 全部完成項目

---

## 完成批次總覽

| 批次 | 主題 | 狀態 | 完成日期 |
|:----:|------|:----:|:--------:|
| 1 | 基礎設施（設定/懸浮面板/Lottie/Radial） | ✅ 完成 | 2026-04-02 |
| 2 | AI Skill 全面升級（12 技能 + Prompt + Toolbar + 邀請碼 + 預測器） | ✅ 完成 | 2026-04-03 |

---

## Batch 1 — 基礎設施

### B1. 全功能開關設定系統

**新增/修改檔案：**
- `ui/settings/YukiPreferences.kt` — 完整重寫，40+ BoolPref 委派，8 大分類
- `ui/settings/SettingsActivity.kt` — 6 群組 CardView 佈局，主開關控制子項目灰化

**技術細節：**
- BoolPref/FloatPref/IntPref inner class delegates，用 KProperty.name 作 key
- AI 功能主開關（18 子項目）、遊戲主開關（2 子項目）
- 關閉時 `isEnabled = false` + `alpha = 0.4f`
- cardGroup() builder pattern 簡化重複 UI 建構

### B2. 懸浮面板基礎架構

**新增檔案（6 個）：**
- `ui/floating/FloatingPanelConfig.kt` — min/max/default 尺寸、吸附閾值
- `ui/floating/FloatingPanel.kt` — 抽象基類，WindowManager overlay 管理
- `ui/floating/FloatingPanelManager.kt` — 面板註冊、show/hide/toggle/dismissAll
- `ui/floating/gesture/DragGestureHandler.kt` — 標題列拖曳
- `ui/floating/gesture/PinchGestureHandler.kt` — 雙指縮放
- `ui/floating/gesture/ResizeHandle.kt` — 右下角 24x24dp 三角拖曳

**技術細節：**
- SYSTEM_ALERT_WINDOW 權限檢查 + Settings.canDrawOverlays()
- OvershootInterpolator 展開動畫
- snapToNearestEdge() + ValueAnimator 吸附動畫
- RGBA_8888 透明覆蓋層

### B13. RadialModeMenu — GAME 模式

**結果：** 確認已存在，KeyboardMode enum 含 GAME，RadialModeMenu 用 `.entries` 動態生成。無需修改。

### B15. Lottie 佔位動畫

**新增檔案（3 個）：**
- `assets/lottie/yuki_freeze.json` — 六角冰晶 + 霜環旋轉
- `assets/lottie/yuki_battle_idle.json` — 五角盾 + 能量脈衝
- `assets/lottie/yuki_cupid.json` — 粉紅愛心 + 閃光星

**修改檔案：**
- `animation/LottieCharacterAnimation.kt` — playReaction() fallback 機制（檢查 asset 存在→不存在則 fallback 到 yuki_happy.json）

---

## Batch 2 — AI Skill 全面升級

### B10. 12 個新 Skill

**新增檔案（12 個）：**

| 檔案 | Skill ID | Token | 特點 |
|------|----------|:-----:|------|
| `claw/skills/GrammarCheckSkill.kt` | grammar_check | 200 | 讀 prompt 模板，純修正輸出 |
| `claw/skills/ToneAdjustSkill.kt` | tone_adjust | 300 | 四種語氣 `---` 分隔 |
| `claw/skills/SummarizeSkill.kt` | summarize | 300 | 30 字以內一句話 |
| `claw/skills/MemeTextSkill.kt` | meme_text | 200 | 台灣 PTT/Dcard 風格 |
| `claw/skills/EmojiTranslateSkill.kt` | emoji_translate | 100 | 雙向：偵測輸入是文字還是 emoji |
| `claw/skills/QuickReplySkill.kt` | quick_reply | 400 | 友善/幽默/簡短三風格 |
| `claw/skills/CodeCompleteSkill.kt` | code_complete | 500 | 去除 markdown 標記 |
| `claw/skills/RegexGenSkill.kt` | regex_gen | 300 | 第一行 regex，第二行說明 |
| `claw/skills/CommitMessageSkill.kt` | commit_message | 400 | Conventional Commits 格式 |
| `claw/skills/YukiChatSkill.kt` | yuki_chat | 600 | 讀 prompt 模板，善意毒舌 |
| `claw/skills/MoodDiarySkill.kt` | mood_diary | 0-200 | 本地關鍵字 + 可選 AI |
| `claw/skills/WeatherSkill.kt` | weather | 0-300 | 本地天氣短語 + 可選 AI 穿衣建議 |

**修改檔案：**
- `claw/ClawClient.kt` — skills map 從 5 個擴充到 19 個，新增 appContext 避免 Activity leak

**設計模式：**
- MoodDiarySkill / WeatherSkill 採「本地優先」設計：無 AI provider 時用本地分析（0 token），有 provider 時用 AI
- 所有 Skill 共用 `ClawSkill` 介面 + `ClawInput`/`ClawOutput` 資料類別
- 需要 prompt 模板的 Skill 用 `R.raw.*` 載入，catch 時 fallback 到 inline prompt

### B11. 6 個 Prompt 模板

**新增檔案（6 個）：**
- `res/raw/claw_grammar_prompt.txt` — 繁體中文文法糾錯規則
- `res/raw/claw_meme_prompt.txt` — PTT/Dcard 迷因風格
- `res/raw/claw_yuki_chat_prompt.txt` — 雪凜人設（善意毒舌 + LINE 風格）
- `res/raw/claw_weather_outfit_prompt.txt` — 天氣穿衣建議（含 placeholder: city/temp/condition/humidity/wind）
- `res/raw/claw_random_challenge_prompt.txt` — 打字挑戰出題
- `res/raw/claw_yuki_rating_prompt.txt` — 打字表現評語（wpm/accuracy/game placeholder）

### B12. ClawToolbar 升級

**修改檔案：**
- `ui/panel/ClawToolbarView.kt` — 從 5 個固定按鈕 → 18 個分類按鈕 + 水平捲動

**新功能：**
- `allButtons` 列表含 18 個 SkillButton，每個標註 category（general/developer）
- `currentMode` 屬性：developer 模式看全部，其他模式過濾 developer-only
- `remainingTokens` 屬性：右上角顯示 token 額度（< 500 變紅色）
- 水平捲動支援（btnSize = 40dp，clipRect + scrollOffset）
- 每個按鈕顯示 icon + 小標籤

### A7. 邀請碼系統補完

**已存在檔案確認：**
- `auth/InviteCodeManager.kt` — HMAC-SHA256 驗證、每日重置、三種碼類型
- `ui/auth/InviteCodeActivity.kt` — Lottie 角色 + 輸入框 + 驗證 + 搖晃動畫
- `claw/TokenBudgetManager.kt` — 每日用量追蹤 + 自動歸零
- `scripts/generate_invite_codes.py` — Python 產碼腳本

**修改：**
- `ui/auth/InviteCodeActivity.kt` — onSkipClicked() 改為啟動 AISettingsActivity（原本是 TODO）

### A6/B9. NextActionPredictor 升級

**修改檔案：**
- `input/predict/NextActionPredictor.kt`

**RuleBasedPredictor 新增規則：**
- 開發者模式：`class`、`try` snippet 偵測 + 長文格式化建議
- 設計者模式：對齊快捷鍵提示
- 一般模式：問號結尾 → 快速回覆建議、長文 → 摘要建議
- App 感知：GitHub/GitLab → commit_message、LINE/Messenger → quick_reply

**新增 ClawEnhancedPredictor：**
- 結合本地規則 + AI 回調
- 高信心（≥0.8）直接回傳，不浪費 token
- 合併時 AI 結果優先覆蓋同 actionId

---

## 之前完成的基礎功能（Batch 0）

| 功能 | 完成時間 | 關鍵檔案 |
|------|:--------:|----------|
| 純 Kotlin 注音引擎 | 2026-04-02 | `input/engine/ChewingInputEngine.kt` |
| 注音字典（1319 筆） | 2026-04-02 | `res/raw/bopomofo_dict.txt` |
| 候選詞觸控選擇 | 2026-04-02 | `ui/keyboard/KeyboardView.kt` |
| AI Provider 管理 | 2026-04-02 | `claw/provider/AIProviderManager.kt` |
| OpenAI/Anthropic SSE 串流 | 2026-04-02 | `claw/provider/OpenAIProvider.kt`, `AnthropicProvider.kt` |
| EncryptedSharedPreferences 金鑰 | 2026-04-02 | `claw/provider/AIProviderManager.kt` |
| AI 設定頁面 | 2026-04-02 | `ui/settings/AISettingsActivity.kt` |
| 鍵盤粒子背景 | 2026-04-02 | `animation/KeyboardParticleView.kt` |
| 8 主題系統 | 2026-04-02 | `theme/KeyboardTheme.kt` |
| Key 裝飾器 | 2026-04-02 | `ui/keyboard/KeyDecorator.kt` |

---

## 完整 Skill 清單（19 個）

| # | ID | 名稱 | Token | 狀態 |
|---|---|---|:---:|:---:|
| 1 | ai_prediction | AI 補字 | 100 | ✅ |
| 2 | writer | 雪凜寫手 | 800 | ✅ |
| 3 | translate | 即時翻譯 | 300 | ✅ |
| 4 | cupid | 情聖月老 | 600 | ✅ |
| 5 | context | 情境感知 | 0 | ✅ |
| 6 | animation_suggest | 角色動畫 | 150 | ✅ |
| 7 | whisper | AI 語音 | 500 | 🔜 stub |
| 8 | grammar_check | 文法糾錯 | 200 | ✅ |
| 9 | tone_adjust | 語氣調整 | 300 | ✅ |
| 10 | summarize | 摘要精簡 | 300 | ✅ |
| 11 | meme_text | 迷因生成 | 200 | ✅ |
| 12 | emoji_translate | Emoji 翻譯 | 100 | ✅ |
| 13 | quick_reply | 快速回覆 | 400 | ✅ |
| 14 | code_complete | 程式碼補全 | 500 | ✅ |
| 15 | regex_gen | 正則產生器 | 300 | ✅ |
| 16 | commit_message | Commit 訊息 | 400 | ✅ |
| 17 | yuki_chat | 雪凜聊天 | 600 | ✅ |
| 18 | mood_diary | 心情日記 | 0-200 | ✅ |
| 19 | weather | 天氣助理 | 0-300 | ✅ |

---

## 檔案統計

| 分類 | 新增 | 修改 | 合計 |
|------|:----:|:----:|:----:|
| Skill（claw/skills/） | 12 | 0 | 12 |
| Prompt 模板（res/raw/） | 6 | 0 | 6 |
| 懸浮面板（ui/floating/） | 6 | 0 | 6 |
| Lottie 動畫（assets/lottie/） | 3 | 0 | 3 |
| 設定（ui/settings/） | 0 | 2 | 2 |
| AI 核心（claw/） | 0 | 1 | 1 |
| 預測器（input/predict/） | 0 | 1 | 1 |
| 邀請碼（ui/auth/） | 0 | 1 | 1 |
| UI 工具列（ui/panel/） | 0 | 1 | 1 |
| 動畫（animation/） | 0 | 1 | 1 |
| Manifest | 0 | 1 | 1 |
| **合計** | **27** | **8** | **35** |

---

## 建構驗證

```
> ./gradlew compileDebugKotlin
BUILD SUCCESSFUL in 3s
17 actionable tasks: 8 executed, 9 up-to-date
```

所有 35 個檔案通過 Kotlin 編譯，無錯誤。

---

## 剩餘工作（Batch 3-6）

參見 `todo_list.md`。主要：
- Batch 3：天氣/Claw結果/貼圖懸浮面板 + 貼圖包下載
- Batch 4：雪凜微吐槽 + 情緒冷靜期 + 私房話模式
- Batch 5：Giphy API + MLKit 翻譯 + .env.example
- Batch 6：14 款新遊戲 + Lottie 正式角色設計
- WhisperSkill 實作（音頻錄製 + Whisper API）

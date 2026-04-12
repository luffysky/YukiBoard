# YukiBoard 待辦清單 — 2026-04-05

> 基於全任務統合 + 近期開發進度整理
> ~~刪除線~~ = 已完成

---

## 已完成（v0.2.0）

### 核心鍵盤
- ~~QWERTY 英文鍵盤~~
- ~~注音鍵盤（McBopomofo 字典 + 多音節）~~
- ~~數字鍵盤 `?123`（numberSymbolRows）~~
- ~~符號鍵盤 `#+=`（moreSymbolRows）~~
- ~~ABC / #+= / ?123 子佈局切換邏輯~~
- ~~英文 Trie 字典 ~20000 高頻詞~~
- ~~使用者自訂詞庫（學習 + 持久化）~~
- ~~智慧輸入（糾錯 + 學詞 + bigram/trigram 預測 + 習慣追蹤）~~

### 多語言輸入（v0.2.0 新增）
- ~~拼音輸入引擎（音節分割 + 模糊拼音 + 2000 高頻字 + 詞組）~~
- ~~倉頡輸入引擎（700+ 字碼 + 完整比對）~~
- ~~速成輸入引擎（首尾碼匹配）~~
- ~~日文輸入引擎（romaji → kana → kanji，130+ 羅馬字映射 + 600 漢字）~~
- ~~韓文輸入引擎（Dubeolsik Jamo 組合 + 複合母音/終聲）~~
- ~~InputRouter 7 模式循環切換（EN/注/拼/倉/速/日/韓）~~
- ~~各語言專用鍵盤佈局（KeyboardState）~~
- ~~長按「切換」鍵環形語言選單（8 選項含 Emoji）~~

### 模式系統
- ~~三模式系統（一般/設計者/開發者）~~
- ~~遊戲模式（4 款遊戲 + HUD + 計分）~~
- ~~長按 Tab 環形模式選單~~
- ~~App 自動偵測切模式~~
- ~~Snippet 系統（開發者模式）~~
- ~~設計者/開發者/遊戲模式「⌨返回」鍵~~

### 面板系統
- ~~工具列膠囊按鈕重設計（3 分組 + 動畫 + 水平捲動）~~
- ~~Emoji 面板（9 分類 700+）~~
- ~~顔文字面板（9 分類 130+）~~
- ~~剪貼簿面板（歷史 + 釘選 + 刪除）~~
- ~~翻譯面板（7 語言 + 交換）~~
- ~~GIF 面板（趨勢標籤 + API 預留）~~
- ~~貼圖面板（貼圖包 + 網格）~~
- ~~主題瀏覽面板（即時預覽切換）~~
- ~~遠端鍵盤面板（藍牙/WiFi）~~

### 動畫 & 角色
- ~~Lottie 角色動畫（浮動 + 遊走 + 反應）~~
- ~~VRM 3D 角色渲染（Filament + 程序化骨骼動畫 9 種）~~
- ~~VRM 表情系統（morph targets + 自動眨眼 + 平滑插值）~~
- ~~OpenGL ES 流光特效覆蓋層~~
- ~~粒子特效 View~~
- ~~觸控穿透修復（GL/粒子/角色層）~~

### 感測器 & 輸入
- ~~搖手機復原（加速度計）~~
- ~~陀螺儀游標（長按空白鍵 + 傾斜）~~
- ~~語音輸入（AndroidSpeechEngine）~~
- ~~藍牙 HID 鍵盤~~
- ~~WiFi WebSocket 鍵盤（內建 Server + 控制網頁）~~

### UI
- ~~鍵盤高度/寬度調整~~
- ~~懸浮鍵盤模式~~
- ~~橫向模式佈局~~
- ~~自訂背景圖/影片~~
- ~~雙指縮放背景~~
- ~~自訂字體顏色（11 色）~~
- ~~主題隔離（內建主題不顯示自訂背景）~~

### 後端 & 帳號
- ~~SnowRealmAuthClient + LoginActivity~~
- ~~邀請碼系統（InviteCodeManager + HMAC 驗證）~~
- ~~Claw AI 面板 + Provider 系統 + SnowRealm Provider~~
- ~~YukiBoard 獨立 Supabase 建表（invite_codes + ai_usage）~~
- ~~Insight Engine 管理 API/UI~~
- ~~Firebase App Distribution 整合~~
- ~~deploy.sh 一鍵上傳腳本~~

---

## 本版本待完成（v0.2.x）

### 輸入法補強
- [ ] 手寫辨識（HandwritingRecognitionEngine — MLKit/Tesseract）
- [ ] libchewing JNI 整合（替換純 Kotlin 注音引擎，提升準確度）
- [ ] AI 語音自動標點（Whisper 轉文字後接 Claw 標點模型）

### AI 功能
- [ ] 整句自動完成（ghost text 灰字提示 + Tab 接受）
- [ ] 智慧回覆建議（分析輸入框上下文，建議 3 個回覆）
- [ ] OCR 貼上（拍照/截圖 → 文字辨識 → 貼上）
- [ ] AI 改寫語氣（選中文字 → 正式/輕鬆/可愛/專業 改寫）
- [ ] WhisperSkill 實作（音頻錄製 + OpenAI Whisper API）
- [ ] NextActionPredictor 候選詞列顯示

### 雪凜功能
- [ ] 雪凜語音朗讀（TTS — 選中文字或候選詞朗讀）
- [ ] 雪凜對話模式（Claw AI 延伸，鍵盤內直接聊天）
- [ ] 微吐槽系統（說話泡泡 + 冷卻機制）
- [ ] 情緒冷靜期完善（觸發詞庫擴充 + 結冰動畫優化）

### 無障礙
- [ ] 單手模式（鍵盤縮小靠左/靠右，可切換）
- [ ] 大字模式（放大按鍵 + 高對比度）

### UI / UX 改善
- [ ] 背景圖片拖曳平移（pan drag）
- [ ] 設定頁裁切框（crop frame）
- [ ] ThemePanelView 內直接上傳自訂背景
- [ ] 面板 UI 重構：向上展開 + 長按懸浮化（PanelCoordinator）
- [ ] 各功能執行前檢查 YukiPreferences 對應開關

### API 串接
- [ ] GIF 引擎接 Giphy API
- [ ] 翻譯引擎接 MLKit 離線模型
- [ ] SnowRealm API Proxy（AI 走後端轉發）
- [ ] 貼圖包下載管理（Insight Engine API + 本地快取）

---

## 下個版本（v0.3.0）

### 工具面板
- [ ] 計算機面板（基礎四則 + 科學計算）
- [ ] 常用語面板（自訂快捷短語庫）
- [ ] 色碼選擇器面板（HEX/RGB 取色）
- [ ] 單位換算面板（長度/重量/溫度/貨幣）

### 個人化
- [ ] 打字統計儀表板（速度/常用詞/使用時間）
- [ ] 自製貼圖上傳（圖片 → 貼圖包）
- [ ] 鍵盤音效包（預設 + 自訂音效）
- [ ] 自訂震動模式

### 經濟系統
- [ ] Z 幣系統（ZCoinManager + 藍新金流儲值）
- [ ] 打字任務賺 Z 幣
- [ ] 皮膚商城（主題/貼圖/表情包，3 款免費 + Z 幣付費）

### 懸浮面板群
- [ ] 天氣懸浮面板（OpenWeatherMap + 雪凜天氣短句）
- [ ] Claw 結果懸浮面板（streaming typewriter + 複製/插入）
- [ ] 貼圖懸浮面板（稀有度角標）

### 雪凜人格系統
- [ ] 私房話模式（AES-256-GCM 加解密）
- [ ] 語氣偵測（ToneDetector 本地關鍵字分析）
- [ ] 雪凜打字教學（新手引導）
- [ ] 心情日記

---

## 長期目標（v0.4.0+）

### 3D / AR
- [ ] AR 雪凜（ARCore + 平面偵測 + 放置互動）
- [ ] SnowRealmVR 輸入介面（OpenXR 預留）

### 連線 & 跨 App
- [ ] 打字競速連線版（Supabase Realtime 配對）
- [ ] CoinBeast 跨 App 聯動（Deep Link + Z 幣同步）
- [ ] 毛行天下聯動（打字距離同步）
- [ ] 情緒採礦週報接 Insight

### 收尾
- [ ] 正式雪凜 Lottie 角色動畫（需設計師素材）
- [ ] 正式 VRM 模型表情完善
- [ ] 上架 Google Play

---

## 備註

- 版本號：**v0.2.0**（versionCode 2）
- deploy 指令：`bash scripts/deploy.sh "更新說明"`
- Firebase App Distribution 測試群組：`testers`
- google-services.json 已在 .gitignore，不進版控
- YukiBoard Supabase DB：`43.167.237.37:30183`（獨立專案）
- Insight Engine 總後台管理 YukiBoard 邀請碼

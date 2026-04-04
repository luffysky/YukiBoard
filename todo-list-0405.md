# YukiBoard 待辦清單 — 2026-04-05

> 基於 todo_list.md 全任務統合 + 近期開發進度整理
> ~~刪除線~~ = 已完成

---

## 今日已完成（04-04 ~ 04-05）

- ~~數字鍵盤 `?123`（numberSymbolRows）~~
- ~~符號鍵盤 `#+=`（moreSymbolRows）~~
- ~~QWERTY / 注音 / 橫向底行加 `?123` 切換鍵~~
- ~~ABC / #+= / ?123 子佈局切換邏輯（SubLayout enum）~~
- ~~注音候選字與 composing text 重疊 Bug 修復（candidateBar offset）~~
- ~~Firebase App Distribution 整合（google-services + appdistribution plugin）~~
- ~~deploy.sh 一鍵上傳腳本~~
- ~~首次上傳 debug APK 至 Firebase App Distribution~~

---

## 前次已完成（04-03 及更早）

- ~~影片背景播放修復（setDataSource 取代 Uri.fromFile）~~
- ~~背景圖片雙指縮放（ScaleGestureDetector + Matrix）~~
- ~~背景切換後即時重載（onStartInputView reloadFromStorage）~~
- ~~自訂字體顏色選擇器（11 色 + customKeyLabelColor）~~
- ~~主題隔離：內建主題不顯示自訂背景~~
- ~~設計者/開發者/遊戲模式「⌨返回」鍵~~
- ~~遊戲模式底部被裁切修復（動態 rowHeight）~~
- ~~鍵盤底部 8dp padding~~
- ~~功能說明頁（HelpActivity — AI 功能 18 項詳細教學）~~
- ~~insight-engine SSL 修復~~
- ~~insight-engine yukiboard_privilege 欄位遷移~~
- ~~SettingsActivity 登入入口~~
- ~~SnowRealmAuthClient + LoginActivity 完成~~

---

## 近期優先（下一輪開發）

### UI / UX 改善
- [ ] 背景圖片拖曳平移（pan drag，KeyboardBackgroundView TODO）
- [ ] 設定頁裁切框（crop frame，背景圖精確裁切）
- [ ] ThemePanelView 內直接上傳自訂背景圖/影片
- [ ] 面板 UI 重構：向上展開 + 長按懸浮化（PanelCoordinator）

### API 串接
- [ ] GIF 引擎接 Giphy API（GiphyApiClient + GifPanelView 串接）
- [ ] 翻譯引擎接 MLKit（離線模型下載 + TranslatePanelView 串接）
- [ ] SnowRealm API Proxy（AI 走後端轉發，不暴露 Key 給客戶端）
- [ ] 貼圖包下載管理（Insight Engine API + 本地快取）

### AI 功能補完
- [ ] WhisperSkill 實作（音頻錄製 + OpenAI Whisper API 上傳）
- [ ] NextActionPredictor 候選詞列顯示（KeyboardView 整合）
- [ ] 各功能執行前檢查 YukiPreferences 對應開關

---

## 中期目標

### 第三批 — 懸浮面板群
- [ ] 天氣懸浮面板（WeatherManager + OpenWeatherMap + 雪凜天氣短句）
- [ ] Claw 結果懸浮面板（streaming typewriter + 版本切換 + 複製/插入）
- [ ] 貼圖懸浮面板（複用 StickerPanelView + 稀有度角標）

### 第四批 — 雪凜人格系統
- [ ] 微吐槽系統（YukiCommentEngine + 說話泡泡 + 冷卻機制）
- [ ] 情緒冷靜期（觸發詞庫 + 結冰動畫 + 確認對話框）
- [ ] 私房話模式（AES-256-GCM 加解密）
- [ ] 語氣偵測（ToneDetector 本地關鍵字分析）

### 第五批 — 環境
- [ ] .env.example 範本檔案

---

## 長期目標

### 第七批 — 帳號 + Z幣 + 後端
- [ ] Supabase 整合（SupabaseClient + SDK）
- [ ] Z幣系統（ZCoinManager + 藍新金流 + 錢包頁面）
- [ ] 皮膚商城（SkinStoreActivity + 免費預設 3 款）

### 第八批 — 3D / AR / VR
- [ ] VRM 3D 角色（Filament + VrmRenderer）
- [ ] AR 雪凜（ARCore + 平面偵測 + 放置互動）
- [ ] SnowRealmVR 輸入介面（OpenXR 預留）

### 第九批 — 連線 + 跨 App
- [ ] 打字競速 v2.0 連線版（Supabase Realtime 配對）
- [ ] CoinBeast 跨 App 聯動（Deep Link + Z幣同步）
- [ ] 毛行天下聯動（打字距離同步）
- [ ] 情緒採礦週報接 Insight

### 第十批 — 收尾
- [ ] 正式雪凜 Lottie 角色動畫（需設計師素材）

---

## 備註

- deploy 指令：`bash scripts/deploy.sh "更新說明"`
- Firebase App Distribution 測試群組：`testers`
- google-services.json 已在 .gitignore，不進版控

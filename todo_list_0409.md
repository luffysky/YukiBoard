# YukiBoard 待辦清單 — 2026-04-09

> 整合 todo-list-0405 + 總任務包 + 滑動輸入任務包 + Games_add + Games_bug
> ~~刪除線~~ = 已完成 / ✅ = 已完成
> 版本：**v0.9.30**（versionCode 96）

---

## 已完成

### v0.1.x — 核心鍵盤
- ✅ QWERTY 英文鍵盤 + 注音鍵盤（McBopomofo 字典 + 多音節）
- ✅ 數字鍵盤 / 符號鍵盤 / ABC↔#+= 切換
- ✅ 英文 Trie 字典 ~50000 高頻詞（v0.9.21 擴充 20K→50K）
- ✅ 使用者自訂詞庫（學習 + SharedPreferences 持久化）
- ✅ 智慧輸入（糾錯 + 學詞 + bigram/trigram 預測 + 習慣追蹤）

### v0.2.0 — 多語言 + VRM + Supabase
- ✅ 拼音 / 倉頡 / 速成 / 日文 / 韓文輸入引擎
- ✅ InputRouter 7 模���循環切換 + 環形語言選單
- ✅ 三模���系統（一般/設計者/開發者）+ 遊戲模式
- ✅ 工具列 + 9 面板���語音/翻譯/GIF/表情/顔文字/貼圖/剪貼簿/主題/設定��
- ✅ Lottie 角色 + VRM 3D 角色（Filament + 程序化骨骼動畫 9 種 + morph targets）
- ✅ OpenGL ES 流光特效 + 粒子特效
- ✅ 感測器：搖手機復原 / 陀螺儀游標 / 語���輸入
- ✅ 藍牙 HID + WiFi WebSocket 鍵盤
- ✅ 鍵盤高度/寬度調整 + 懸浮鍵盤 + 橫向模式
- ✅ 自訂背景圖/影片 + 雙指縮放 + 自訂字體顏色
- ✅ SnowRealmAuthClient + LoginActivity + 邀請碼系統
- ✅ Claw AI 面板 + Provider 系統（OpenAI / Anthropic / SnowRealm）
- ✅ Supabase 建表 + Insight Engine API + Firebase App Distribution

### v0.3.0 — 透明鍵盤 + 多語言語音 + 字色自訂
- ✅ 透明鍵盤模式（可調透明度）
- ✅ 多國語言語音辨識（10 ��系）
- ✅ 自訂字體顏色（RGB 滑桿 + HEX 輸入）
- ✅ 面板透明度調整 + 面板/鍵盤圖層交換

### v0.3.1 — Bug 修復 + 注音排列
- ✅ Batch 1 全 19 項 Bug 修復（null safety / scope leaks / encrypted wallet / WiFi PIN）
- ✅ 注音鍵盤改回大千式標準排列（ㄦ移至底行）
- ✅ Firebase App Distribution testers 群組通知

### v0.3.2~v0.3.5 — 選字修復 + 遊戲返回 + 設定穩定
- ✅ 候選字選不中修復（引擎索引映射 + candidateRects → hitTestCandidateIndex 數學計算）
- ✅ 候選列橫向滑動 + 右側 ▼ 展開箭頭
- ✅ 設定頁自動關閉修復（recreate → Intent 重啟 + requestHideSelf）
- ✅ ClawClient skills by lazy → getSkill() 動態工廠（切換 Provider 即時生效）
- ✅ 遊戲模式：遊戲中 HUD + 遊戲選單均有 ⌨返回 按鈕
- ✅ 按鍵 padding 加大（H:3→5dp, V:4→6dp）
- ✅ 雪凜預設 Lottie Q 版角色（VRM 可在設定開啟）
- ✅ 語音輸入加入 RECORD_AUDIO 權限檢查提示

### v0.3.6 — 緊急修復

- ✅ **GIF 搜尋框無法輸入** — CJK 輸入透過 InputRouter 組字→搜尋框；輸入模式面板收縮只留搜尋列
- ✅ **語音辨識加入更多語言** — 新增泰語/越南語/印尼語/義大利語/俄語/阿拉伯語/土耳其語（共 17 語）
- ✅ **候選字/預測字依語意前後文排序** — getTextBeforeCursor → extractContextWords → bigram/trigram 加權
- ✅ **記憶翻牌遊戲初始無牌面** — 開局 2 秒展示所有牌面（藍色光暈），展示期間不可操作
- ✅ **背景圖片 XY 位置拉桿** — 水平/垂直位置滑桿（0%~100%）+ 設定預覽改 MATRIX 完整呈現

### v0.3.7 — Skills 重寫 + 日韓詞庫 + 影片預覽

- ✅ **WeatherSkill 重寫** — GPS + OpenWeatherMap 自動取得天氣，無 AI 也有本地短句回應
- ✅ **WhisperSkill 重寫** — AI 語音後處理（加標點、修正同音錯字），無 AI 時本地基礎標點
- ✅ **TranslateSkill 升級** — 自動偵測目標語言（中→英、其他→中）+ 8 語言支援 + fallback prompt
- ✅ **日文假名/片假名切換** — KEYCODE_TOGGLE_KANA + JapaneseInputEngine.toggleKatakana()
- ✅ **日文詞庫擴充** — 新增 ~180 詞（動詞活用、形容詞、接續詞、日常用語、SNS 用語）
- ✅ **韓文詞庫** — 新增 ~200 常用詞 + getCandidates() 前綴匹配 + selectCandidateAt()
- ✅ **背景顯示比例選擇** — 保持比例（center-crop）/ 整張塞入（fit-xy stretch）
- ✅ **影片預覽可播放** — Settings TextureView + MediaPlayer 循環靜音播放

### v0.3.8 — 鍵盤語言切換 Bug 修復

- ✅ **注音/日/韓鍵盤切數字再切回會跳英文** — KeyboardView.rebuildLayoutForOrientation() 新增 rowsForCurrentInputMode()，根據 currentInputMode 回傳對應佈局

### v0.3.9 — 符號鍵盤擴充

- ✅ **符號鍵盤新增底線 _** — 取代 % 位置（% 移至第三行）
- ✅ **大量符號擴充** — 長按變體：" → ""„、! → ¡、? → ¿、/ → \、° → ℃℉、= → ≠≈≤≥
- ✅ **更多符號頁新增** — ♥♡♣♠♦、★☆✦✧、→←↑↓↔⇒、§、貨幣列 £¥€¢₩₿

### v0.4.0~v0.4.2 — 按鍵自訂 + 鍵盤完整預覽

- ✅ **鍵盤高度拉桿** — 25%~55% 螢幕高度，設定頁即時調整
- ✅ **按鍵大小拉桿** — 70%~130%，調整個別按鍵間距（padding 反向縮放），鍵盤高度不變
- ✅ **按鍵透明度拉桿** — 0~255，按鍵背景 alpha 調整
- ✅ **鍵盤完整預覽** — 背景圖 + 遮罩 + 按鍵即時渲染，所有拉桿同步更新
- ✅ **預覽高度同步** — 背景圖預覽 + 完整鍵盤預覽高度跟隨鍵盤高度拉桿變化
- ✅ **.env 新增 OPEN_WEATHER_MAP_API_KEY**

---

## 已修復（v0.4.3~v0.4.7）

### v0.4.3~v0.4.5 修復
- ✅ **.env 天氣 API Key 名稱修正** — `OPEN_WEATHER_MAP_API_KEY` → `OPENWEATHER_API_KEY`
- ✅ **背景圖片設定即時生效** — `onStartInputView` 同步 bgScaleMode/keyScale/keyOpacity
- ✅ **鍵盤高度即時生效** — `onStartInputView` 重新計算 kbHeight 並 requestLayout

### v0.4.6 修復
- ✅ **雪凜角色不顯示** — GL 流光從 GLSurfaceView（setZOrderOnTop 遮擋）改為 TextureView + 手動 EGL
- ✅ **CharacterView 重複初始化** — 移除 init 裡的 initLottieMode()
- ✅ **Emoji 面板擴充** — 12 分類、1400+ emoji、支援滑動捲動

### v0.4.7 修復
- ✅ **CupidSkill** — 加 try-catch fallback（prompt 缺失時 inline prompt 替代）
- ✅ **WriterSkill** — 加 try-catch fallback（prompt 缺失時 inline prompt 替代）
- ✅ **鍵盤完整預覽** — 影片背景截取第一幀作為預覽（MediaMetadataRetriever）
- ✅ **字體顏色拉桿** — 調整後預覽字色即時同步（fullPreviewView.invalidate）
- ✅ **影片背景比例/XY 調整** — TextureView.setTransform(Matrix) 支援 scaleMode + offsetX/Y
- ✅ **清除/切換背景修復** — 切換前先 stopVideoPreview()，避免影片佔住無法清除
- ✅ **GIF 傳送改善** — 下載改 OkHttp + URL path 副檔名判斷 + commitContent fallback Toast
- ✅ **GIF 搜尋框 CJK 組字提示** — 搜尋框顯示組字中的注音/拼音 hint

### v0.4.8~v0.5.0 — 主題面板 + 背景設定 + 影片即時生效
- ✅ **主題面板整合完整背景設定** + 設定頁預覽即時更新
- ✅ **影片背景比例/XY 調整即時生效**
- ✅ **影片背景切換/清除時 crash 修復**

### v0.5.1 — 候選字修復 + 自訂詞庫 + 功能接線
- ✅ **多音節候選字 fallback 修復** — 所有音節的候選字都顯��（不再只有最後一個）
- ✅ **UserDictionary 手動注音→詞對應** — addManual/queryByBopomofo + 設定頁 UI
- ✅ **ToneDetector 接線** — 提交文字≥2字時分析語氣，負面情緒雪凜回應
- ✅ **SecretMessageManager 修正** — 改為 Enter 整句加密（不再逐字加密）+ 預設關閉
- ✅ **FloatingPanelManager 接線** — 註冊天氣/Claw 懸浮面板
- ✅ **NextActionPredictor 接線** — 空候選列顯示情境建議

### v0.5.2 — 句子級上下文預測
- ✅ **SentencePredictor** — 三層預測：學習 bigram + 內建回覆模板（中/英/日/韓 100+）+ 前綴續寫
- ✅ **SmartInputManager 整合** — updateSentenceContext / onSentenceCommitted
- ✅ **InputRouter 整合** — CJK + 英文模式候選列皆附加句子預測

### v0.5.3 — 直打注音 + 全專案掃描
- ✅ **rawBopomofoMode** — 注音符號直接輸出不轉漢字 + 設定頁開關
- ✅ **移除「直打注音」設定開關** — 功能已整合至鍵盤，設定頁 switchRow 移除（v0.9.21）

### v0.5.4 — 長按滑動選字 + SwipeTrail
- ✅ **Alt-char popup 改為 inline overlay** — 長按後直接滑到目標字放開選中
- ✅ **環形選單滑動選取** — RadialModeMenu / RadialLanguageMenu 長按不用抬手直接滑選
- ✅ **SwipeTrail 滑動軌跡特效** — 紫色光暈路徑 + 起點光圈 + 按鍵高亮 + 400ms 淡出
- ✅ **swipeTrailEnabled 開關** — YukiPreferences + 設定頁


### v0.5.5 — 注音長按直出 + 橫向鍵盤修正
- ✅ **注音/聲調長按直出** — 長按注音鍵顯示該符號為 altChar，滑動選取直接輸出（不走 InputRouter）
- ✅ **橫向鍵盤高度修正** — landscape ratio × 1.6（min 0.55, max 0.75）+ rowHeight 42→48dp

### v0.5.6 — 滑動輸入完整實作
- ✅ **滑動輸入管線** — KeyboardView key sequence tracking → InputRouter.swipeSuggest() → 候選列
- ✅ **EnglishDictionary.swipeSuggest()** — 子序列匹配 + 首尾字母 + 頻率排序
- ✅ **ChewingInputEngine.lookupByBopomofoString()** — 注音滑動解碼���音節切割 + 查詞）
- ✅ **onSwipeInputListener** — 滑動候選直接 commit，不走 InputRouter

### v0.5.7 — 橫向模式一般輸入全寬
- ✅ **橫向 NORMAL 模式移除側邊面板** — getLandscapeLayout() NORMAL 返回 emptyList()
- ✅ 設計者/開發者模式側邊快捷鍵保留

### v0.5.8 — Whisper 語音引擎 + 滑動匹配優化
- ✅ **WhisperSpeechEngine** — AudioRecord 錄音→WAV→OpenAI Whisper API + partial result
- ✅ **AndroidSpeechEngine 優化** — 靜音偵測延長 2.5s + 最低收音 1s + 候選數 5
- ✅ **設定頁語音引擎切換** — Android 內建 / OpenAI Whisper 選擇器
- ✅ **滑動輸入模糊匹配** — 鄰近鍵���錯 + 評分排序 + 搜尋範圍 500→2000

### v0.5.9 — 輸入法測試頁
- ✅ **InputTestActivity** — 12 種輸入欄位完整測試（中英/密碼/Email/URL/數字/電話/搜尋/多行/Snippet/Emoji）
- ✅ **設定頁新增綠色「輸入法完整測試」按鈕**

### 待補：日文漢字辭書擴充
- [ ] **ja_kanji_dict.txt 大擴充** — 目前僅 ~300 讀音，需擴充至 3000+ 讀音（常用漢字 + 動詞活用 + 形容詞 + 地名 + 人名）

### v0.9.21 — RIME 詞庫 + 五語擴充 + Bug 修復
- ✅ **RIME 拼音詞庫合併** — merge_rime_pinyin.py 拼音→注音轉換（+58K 字詞、+14K 詞組）
- ✅ **英文詞庫擴充 20K→50K** — english_words.txt 生成
- ✅ **韓文詞庫擴充 ~3.6K** — ko_words.txt 生成 + KoreanInputEngine 檔案載入
- ✅ **日文漢字辭書 ~300 讀音** — ja_kanji_dict.txt + JapaneseInputEngine 檔案載入
- ✅ **倉頡詞庫擴充 ~5K** — cangjie_dict.txt + CangjieInputEngine 檔案載入
- ✅ **候選字排序修復** — 當前候選排前（深色）、歷史候選排後（淺色）
- ✅ **CJK 模式英文洩漏修復** — 句子預測 + 上下文預測加 Unicode > 0x2E80 過濾
- ✅ **GIF 面板搜尋框修復** — onMeasure 固定 200dp + 移除 requestLayout 跳回
- ✅ **滑動輸入多段累加候選** — swipeAccumulated 累積 + buildSwipeCombinedCandidates()
- ✅ **注音選字殘留修復** — 非引擎候選直接 commit + engine.reset()
- ✅ **移除「直打注音」設定** — 功能已整合至鍵盤
- ✅ **全專案掃描** — 掃出 2 關鍵 + 10 中等問題，加入待辦

### v0.9.22 — GIF/翻譯面板 backspace 修復 + 待辦更新
- ✅ **GIF 面板 Backspace 修復** — GifPanelView 搜尋框 backspace 正確刪字
- ✅ **翻譯面板 Backspace 修復** — TranslatePanelView 同修
- ✅ **待辦清單更新** — 加入掃描問題 + 悄悄話解密面板規劃

### v0.9.23 — 日文漢字辭書擴充
- ✅ **ja_kanji_dict 擴充至 ~300 讀音** — 常用漢字追加 + build 修正

### v0.9.24 — 背景選圖改用系統相簿
- ✅ **ACTION_GET_CONTENT** — 背景選圖片/影片改用系統內建相簿（取代 ACTION_PICK）

### v0.9.30 — AI Proxy 動態金鑰 + 防詐開源資料擴充 + Bug 修復 + n8n 自動化
- ✅ **AI Proxy 動態 API Key** — Insight Engine ai-proxy 4 函數改接 DB 金鑰（AES-256-GCM）+ env fallback + 5 分鐘快取
- ✅ **防詐開源資料擴充** — 新增 MODA CSV / URLhaus / CERT.PL 三來源（共 7 來源）
- ✅ **n8n 自動化工作流** — 建立 6 個 n8n workflow（防詐同步/AI 健康檢查/使用量統計/邀請碼到期/詞庫同步/貼圖主題同步）
- ✅ **Dialog 被鍵盤遮擋修復** — GIF/翻譯/詞庫/解密面板 Dialog 改 SOFT_INPUT_ADJUST_PAN + Gravity.TOP + 48dp margin
- ✅ **AUTO 模式雙語候選字修復** — updateCandidates() 在 AUTO 模式呼叫 getAutoCombinedCandidates() 取中英混合候選
- ✅ **候選字選取後組字延續修復** — directCommit 保留 ChewingEngine selectedBuffer + 移除 PREDICTION/HISTORY 攔截
- ✅ **Firebase App Distribution v0.9.30 (96)** — 推送至 testers 群組

---

## 🔧 v7 Bug 掃描修復（v0.9.36 → v0.9.37）

> 49 項 bug 掃描結果，按嚴重度排序實作
> 詳細說明見 YukiBoard_v7_BugScan_Tasks.md

### 第一批：🔴 崩潰風險（C1-C6）
- ~~[x] **C1** ClipboardEncryptionHelper `as` → `as?` 安全轉型~~
- ~~[x] **C2** EmotionStickerEngine `!!` → `?: return null`~~
- ~~[x] **C3** SettingsActivity `item.second!!` → `?: return`~~
- ~~[x] **C4** KeyboardGLSurfaceView `configs[0]!!` → `?: return`~~
- ~~[x] **C5** MusicTypingGame rows<3 guard~~
- ~~[x] **C6** RhythmGame `firstOrNull` / `lastOrNull`~~

### 第二批：🔴 功能缺失（F1-F2）
- ~~[x] **F1** 7 款遊戲 `onGameEvent` 接線補齊~~
- ~~[x] **F2** 詞庫匯入/匯出 Toast 包 `Handler(mainLooper)`~~

### 第三批：🟡 Coroutine 安全（CR1-CR9）
- ~~[x] **CR1** 翻譯 engine launch try-catch~~
- ~~[x] **CR2** GIF search launch try-catch~~
- ~~[x] **CR3** RIME 字典 init try-catch~~
- ~~[x] **CR4** chewing warmUp try-catch~~
- ~~[x] **CR5** 被動防詐 launch try-catch~~
- ~~[x] **CR6** GifPanelView preview load try-catch + loadingUrls.remove~~
- ~~[x] **CR7** StickerRepository prefetch try-catch~~
- ~~[x] **CR8** StickerPackDownloadManager downloadAllPacks try-catch~~
- ~~[x] **CR9** SettingsActivity modelManager.manualUpdate try-catch~~

### 第四批：🟡 執行緒安全（T1-T12）
- ~~[x] **T1** FlowLightRenderer `@Volatile` alpha/colorR/G/B~~
- ~~[x] **T2** KeyboardGLSurfaceView `@Volatile` surfaceWidth/Height~~
- ~~[x] **T3** KeyboardGLSurfaceView `@Synchronized` startRenderThread~~
- ~~[x] **T4** ShakeDetector `@Volatile` onShakeListener/enabled~~
- ~~[x] **T5** GyroscopeCursorController `@Volatile` active/enabled~~
- ~~[x] **T6** WhisperSpeechEngine `@Volatile` isListening + `@Synchronized` releaseAudioRecord~~
- ~~[x] **T7** FasterWhisperSpeechEngine 同上~~
- ~~[x] **T8** AndroidTtsEngine pendingCallbacks → ConcurrentHashMap~~
- ~~[x] **T9** AdaptiveDictionary entries → ConcurrentHashMap~~
- ~~[x] **T10** PersonalDictionary entries → ConcurrentHashMap~~
- ~~[x] **T11** BluetoothHidKeyboard `@Volatile` hidDevice/hostDevice~~
- ~~[x] **T12** WifiKeyboardServer `@Volatile` stateListener/connectionPin~~

### 第五批：🟡 記憶體洩漏（M1-M8）
- ~~[x] **M1** StickerPanelView scope cancel 呼叫（onDetachedFromWindow）~~
- ~~[x] **M2** StickerPanelView onDetachedFromWindow 清 callback~~
- ~~[x] **M3** StickerRepository destroy 置 null~~
- ~~[x] **M4** StickerPackDownloadManager destroy 置 null~~
- ~~[x] **M5** VrmCharacterAnimation 具名 Runnable~~
- ~~[x] **M6** VrmCharacterView 具名 Runnable~~
- ~~[x] **M7** IMEService fraud-detect 具名 Runnable~~
- ~~[x] **M8** SettingsActivity MediaPlayer 保底釋放（已有 stopVideoPreview）~~

### 第六批：🟢 邏輯/顯示（L1-L12）
- ~~[x] **L1** ClipboardPanelView indexOf -1 guard~~
- ~~[x] **L2** InputRouter indexOf -1 guard~~
- ~~[x] **L3** IMEService indexOfChild -1 guard~~
- ~~[x] **L4** GameKeyboardView 除以零 guard~~
- ~~[x] **L5** GeminiProvider SSE 空 catch 加 Log~~
- ~~[x] **L6** GroqProvider SSE 空 catch 加 Log~~
- ~~[x] **L7** SnowRealmProvider SSE 空 catch 加 Log~~
- ~~[x] **L8** FraudAIAnalyzer 空 catch 加 Log~~
- ~~[x] **L9** ClipboardPanelView 加解密空 catch 加 Log~~
- ~~[x] **L10** YukiPreferences write 空 catch 加 Log~~
- ~~[x] **L11** SettingsActivity `(btn as TextView)` → `(btn as? TextView)`~~
- ~~[x] **L12** InputRouter 字體載入空 catch 加 Log~~

### 額外修復
- ~~[x] **ToolbarView 透明模式可見性** — 透明背景 alpha 0x22→0x88，確保功能列在自訂背景下可見~~

---

## 當前版本待實作（v0.5.5+）

### 批次 A — 滑動輸入 + RIME 詞庫（8 步）

#### A-1 RIME 詞庫轉換腳本
- ~~[ ] 下載 RIME 詞庫（bopomofo_tw.dict.yaml + terra_pinyin.dict.yaml）→ `scripts/dicts/`~~ — v0.9.26 scripts/dicts/ 已建立
- ~~[ ] 新增 `scripts/convert_rime_dict.py`（拼音→注音轉換 + SQLite 輸出）~~ — v0.9.26 已完成（支援 --local / 下載）
- [ ] `app/build.gradle` 加入 `convertRimeDict` task（build 前自動執行）
- ~~[ ] `app/src/main/assets/.gitignore` 排除 rime_bopomofo.db~~ — v0.9.26

#### A-2 RimeDictionary.kt（Android 查詢層）
- ~~[ ] 新增 `input/dictionary/RimeDictionary.kt`~~ — v0.9.26
  - ~~assets → filesDir 複製（首次啟動）~~
  - ~~精確查詢（含聲調）+ 去聲調模糊匹配 + 前綴匹配（滑動用）~~
  - ~~`query()` / `queryPrefix()` / `isAvailable()` / `close()`~~

#### A-3 滑動手勢偵測
- ✅ 新增 `input/swipe/SwipeGestureDetector.kt`（點擊 vs 滑動區分，12dp 閾值）
- ✅ 新增 `input/swipe/BopomofoSwipeDecoder.kt`（注音符���序列解碼 + 鄰近鍵容錯）
- ✅ 新增 `input/swipe/SwipeCandidateRanker.kt`（合併 ChewingEngine + 自適應排序）

#### A-4 ChewingInputEngine 擴充
- ✅ 新增 `lookupByBopomofoString()` — 連續注��查詞（供滑動輸入用）
  - 完整序列查詢 + 子序列查詞 + 前綴匹配

#### A-5 KeyboardView 滑動整合
- ~~[x] 滑動軌跡視覺效果（淡紫色半透明 Path + 外發光 + 起點圓點）~~ — v0.5.4 SwipeTrail
- ✅ `onTouchEvent` 根據 `prefs.swipeInputEnabled` 切換點擊/滑動模式
- ~~[ ] `notifyBopomofoLayout()` 在注音鍵盤 layout 後通知 decoder~~ — 不需要，decoder 使用靜態鄰近表
- ✅ `onSwipeInputListener` 回呼接口 — v0.5.6

#### A-6 YukiBoardIMEService 整合
- ~~[ ] `onCreateInputView()` 初始化 RimeDictionary（IO thread）~~ — v0.9.26 onCreate() 中初始化
- ✅ 設定 `keyboardView.onSwipeInputListener` 回呼 — v0.5.6
- ✅ `handleSwipeInput()` → SwipeCandidateRanker → 更新候選列（v0.9.21 多段累加候選）

#### A-7 YukiPreferences 開關
- ~~[x] `swipeInputEnabled: Boolean`（預設 true）~~ — 已在 YukiPreferences.swipeInput
- ~~[ ] `rimeDictEnabled: Boolean`（預設 true）~~ — v0.9.26 + 設定頁開關
- ~~[x] SettingsActivity 輸入行為群組加入對應開關~~ — 已有「滑動輸入」+ RIME + 自我學習開關

#### A-8 候選字視覺標示
- ~~[ ] 滑動候選標記淡紫色 #B39DDB + 左側 ≈ 符號~~ — 已有 isSwipeCandidateMode 淡紫色渲染
- ~~[ ] `markSwipeCandidates()` 方法~~ — 已存在 KeyboardView.markSwipeCandidates()

---

### ~~批次 B — 全功能開關設定系統（總任務包 第二批）~~ ✅

- ~~[x] YukiPreferences 補齊所有開關（AI 18 項 + 雪凜 5 項 + 感測器 3 項 + 動畫 5 項 + 遊戲 3 項 + 懸浮面板 3 項 + 輸入行為 7 項）~~
- ~~[x] SettingsActivity 重構 7 群組 CardView（AI/雪凜/感測器/動畫/遊戲/輸入行為/智慧輸入）~~
- ~~[x] 主開關關閉時子項目灰化（alpha 0.4 + disabled）~~

---

### 批次 C — 智慧候選字升級（總任務包 第九批）

- ~~[x] 注音自適應學習（AdaptiveDictionary — 用戶選字頻率排序）~~
- [ ] 中文上下文串聯預測（chineseNgramIndex — 上一字→下一字）
- ~~[x] 讀取輸入框上下文（getTextBeforeCursor → contextBeforeCursor → extractContextWords）~~
- ✅ lookupMultiSyllable() fallback — 無片語時顯示每個音節的單字候選（已實作）
- ✅ UserDictionary 注音欄位 — addManual/queryByBopomofo + 反向索引（已實作）
- ✅ 設定頁自訂詞庫管理 UI（新增/刪除/列表）— 釘選/匯出待補
- [ ] 候選字視覺分類標示（DICTIONARY/ADAPTIVE/PREDICTION/CORRECTION/AI 五種顏色）

---

### ~~批次 D — AI Skill 全面升級（總任務包 第六批）~~ ✅

- ~~[x] GrammarCheckSkill — 語法糾錯（200 token）~~
- ~~[x] ToneAdjustSkill — 語氣調整 正式/輕鬆/可愛/專業（300 token）~~
- ~~[x] SummarizeSkill — 摘要精簡（300 token）~~
- ~~[x] MemeTextSkill — 迷因/流行語（200 token）~~
- ~~[x] EmojiTranslateSkill — 文字↔Emoji（100 token）~~
- ~~[x] QuickReplySkill — 整句回覆建議（400 token）~~
- ~~[x] CodeCompleteSkill — 程式碼補全（500 token，開發者模式）~~
- ~~[x] RegexGenSkill — 正則產生器（300 token，開發者模式）~~
- ~~[x] CommitMessageSkill — Commit 訊息（400 token，開發者模式）~~
- ~~[x] YukiChatSkill — 雪凜聊天（600 token）~~
- ~~[x] MoodDiarySkill — 心情日記（0-200 token）~~
- ~~[x] WeatherSkill — 天氣助理（0-300 token）~~
- [x] Prompt 模板 10 個（res/raw/claw_*_prompt.txt）— 缺 tone_adjust/summarize/emoji_translate 3 個
- ~~[x] ClawToolbar 更新（天氣/遊戲按鈕 + Token 顏色指示 + 動態 Skill 過濾）~~
- ~~[x] RadialModeMenu 加入 GAME 模式（四選項 90°）~~

---

### 批次 E — AI Provider 擴充（總任務包 第七批）

- [ ] GeminiProvider.kt（免費 1500 次/天）
- [ ] GroqProvider.kt（免費限額，速度最快）
- [ ] GeminiNanoProvider.kt（Android 14+ 本地離線）
- ✅ AIProviderType enum 更新（+GEMINI/GROQ）
- ✅ GeminiProvider（SSE streaming + gemini-2.0-flash）
- ✅ GroqProvider（OpenAI相容格式 + llama-3.3-70b）
- ✅ AIProviderManager + AISettingsActivity 5平台選擇
- ✅ ClawClient.streamSkill() — Flow<String> 串流結果直通 UI
- ✅ ApiGuideActivity.kt（5 平台申請教學 + 一鍵跳轉）
- [ ] AI 候選字 debounce（600ms）+ ✨ 標記顯示
- [ ] markAICandidates() — 候選列 AI 建議淡紫標示
- [ ] ClawPanelView 串流即時顯示（partial result 邊收邊顯示）

---

### ~~批次 F — 雪凜人格系統（總任務包 第五批）~~ ✅

- ~~[x] YukiCommentEngine.kt — 微吐槽（打錯字/刪全文/打字變慢/深夜/遊戲 → 隨機短句 + 10 分鐘冷卻）~~
- ~~[x] EmotionalCooldownManager.kt — 情緒冷靜期（觸發詞庫 + 結冰動畫 + 鍵盤鎖 1 秒 + 確認對話框）~~
- ~~[x] SecretMessageManager.kt — 私房話 AES-256-GCM（格式 `[YB🔐] {base64}`）~~
- ~~[x] ToneDetector.kt — 語氣偵測（本地關鍵字 0 token，NEGATIVE/SAD 時雪凜回應）~~

---

### 批次 G — 語境預測 + WhisperSkill（總任務包 第十批）

- ~~[x] NextActionPredictor 本地 intentPatterns（0 token）+ AI 意圖預測（邀請碼解鎖）~~
- ✅ WhisperSkill 實作 + WhisperSpeechEngine（AudioRecord + Whisper API）— v0.5.8
- ✅ InviteCodeActivity UI + 驗證流程
- ~~[x] TokenBudgetManager 每日重置邏輯~~

---

### 批次 H — API 串接 + 環境（總任務包 第十一批）

- ~~[x] GiphyApiClient.kt（trending + search）→ GifPanelView 串接~~
- [ ] scripts/generate_invite_codes.py — HMAC-SHA256 邀請碼批量生成腳本
- [ ] 翻譯引擎接 MLKit Translation 離線模型 → TranslatePanelView
- ✅ `.env.example` 範本檔案
- [ ] CLAUDE.md 補充各 Key 用途與申請網址

---

### ~~批次 I — 懸浮面板群完善（總任務包 第三、四批）~~ ✅

- ~~[x] FloatingPanelManager（showPanel/hidePanel/togglePanel/dismissAll + prefs 檢查）~~
- ~~[x] gesture/ 子目錄（DragGestureHandler / PinchGestureHandler / ResizeHandle）~~
- ~~[x] 靠邊吸附（40dp 閾值 + 200ms 動畫）~~
- ~~[x] WeatherFloatingPanel（OpenWeatherMap + 30 分快取 + 雪凜天氣短句 + 6 小時預報）~~
- ~~[x] ClawResultFloatingPanel 完善（streaming typewriter + 版本切換 + 複製/插入）~~

---

### 批次 J — 個人風格智慧系統（個人風格任務包 4 批）

#### J-1 PersonalDictionary（個人詞庫核心）
- ✅ `input/smart/PersonalDictionary.kt` — 三層自動學詞（PINNED→LEARNED→CANDIDATE）
  - 使用 ≥3 次 → 升為 LEARNED
  - 30天未使用 → 降級，60天 → 刪除
  - 手動釘選（永不降級）/ 取消釘選 / 刪除
- ✅ 整合 SmartInputManager + InputRouter 候選字排序
- [ ] 設定頁 UI：個人詞庫管理（釘選/刪除/匯出）

#### J-2 StyleProfiler（語氣風格學習）
- ✅ `input/smart/StyleProfiler.kt` — 語尾詞統計 + App 維度語氣分數
  - 4 風格軸：FORMAL/CASUAL/CUTE/PROFESSIONAL
  - 不同 App 不同風格偏好
- ✅ 候選字列融入語氣加分（styleBoost 影響排序）

#### J-3 ComboTracker（隱形連擊計數器）
- ✅ `input/smart/ComboTracker.kt` — 選字命中累加連擊
  - 4 閾值：5/10/20/50 連擊觸發不同粒子特效
  - 3 秒無操作歸零
- ✅ KeyboardView 粒子動畫整合（金/橙/紅粉/洋紅 4 級）

#### J-4 EmotionStickerEngine（情緒貼圖自動推薦）
- ✅ `input/smart/EmotionStickerEngine.kt` — 依打字情緒推薦貼圖標籤
  - 7 情緒分類 + 關鍵詞/Emoji 偵測
  - recommend() 回傳貼圖標籤
- [ ] StickerPanelView 貼圖標籤系統（接收推薦顯示）
- [ ] 候選列末尾浮出推薦貼圖縮圖

---

### 批次 K — SwipeTrail 進階 + 滑動輸入邏輯

#### K-1 滑動輸入邏輯（接 A-3~A-6）
- ✅ SwipeGestureDetector.kt — 點擊 vs 滑動區分（12dp 閾值）
- ✅ BopomofoSwipeDecoder.kt — 注音符號序列解碼 + 鄰近鍵容錯
- ✅ SwipeCandidateRanker.kt — 合併 ChewingEngine + 自適應排序
- ✅ YukiBoardIMEService 整合 handleSwipeInput()

#### K-2 滑動候選字視覺標示
- ✅ 滑動候選標記淡紫色 #B39DDB + 左側 ≈ 符號
- ✅ `markSwipeCandidates()` 方法


### 批次 L — 防詐 AI 對話模型（FraudAI 任務包 8 批）

> 路線：規則引擎 → 被動偵測 → 自我學習 → 雲端聚合 → TFLite 離線模型
> 參考：YukiBoard_FraudAI_Model_Tasks.md

#### L-1 FraudAIAnalyzer（規則 + Gemini Flash）✅ v0.9.9
- ✅ security/FraudAIAnalyzer.kt — A+B 混合架構（規則引擎 8 類 170+ 關鍵字 < 1ms + AI 串流深度分析）
- ✅ security/ConversationFormatter.kt — LINE/Messenger/WhatsApp 對話格式化（說話者辨識 + 截斷）
- ✅ 整合 ClawPanelView 防詐分析流程 + ClawToolbarView 🛡️ 防詐按鈕
- ✅ FraudDetectSkill.kt — Claw Skill 整合

#### L-1.5 被動偵測（輸入時自動掃描）✅ v0.9.10
- ✅ onStartInputView 讀取輸入框上下文（最多 2000 字）背景跑規則引擎
- ✅ 風險 ≥ 30 → 雪凜泡泡警告（5 秒）+ 驚訝表情
- ✅ 點擊泡泡 → 自動開啟 Claw 面板 fraud_detect 深度分析

#### L-2 本地回饋自我學習（個人化權重調整）
- [ ] security/FraudFeedbackStore.kt — SQLite 儲存使用者回饋
  - 表結構：id / text_hash / fraud_type / is_fraud(bool) / timestamp / matched_keywords
  - 每次回饋後更新本地權重：「確實詐騙」→ 命中關鍵字 weight +2；「不是詐騙」→ weight -1
- [ ] security/FraudRuleWeightManager.kt — 動態權重管理
  - 載入預設權重 + 本地回饋調整 = 最終權重
  - FraudAIAnalyzer 改為讀取動態權重（取代硬編碼 weight）
  - 個人化關鍵字學習：使用者標記詐騙時，自動提取新關鍵字加入個人規則
- [ ] ClawPanelView 防詐結果加「✅ 確實是詐騙 / ❌ 不是詐騙」回饋按鈕
- [ ] 回饋後雪凜回應（「記住了！下次會更注意」/「好的，誤判了抱歉」）

#### L-3 回報系統（匿名聚合，群體智慧）
- [ ] security/FraudReportManager.kt — 匿名化處理
  - 文字 → SHA-256 hash（不上傳原文）
  - 只上傳：fraud_type / matched_keywords / is_fraud / rule_score / device_locale
- [ ] Supabase 建 fraud_reports 表（RLS insert only，使用者只能寫不能讀）
- [ ] ClawPanelView 加「回報詐騙 / 不是詐騙」按鈕（接 FraudReportManager）
- [ ] 同意聲明對話框（首次回報時顯示隱私說明，opt-in）
- [ ] YukiPreferences 加 fraudReportConsent: Boolean

#### L-4 雲端規則下發（定期更新）
- [ ] Supabase 建 fraud_rules_v2 表（type / keyword / weight / updated_at）
- [ ] security/FraudRuleSync.kt — App 啟動時拉最新規則
  - 每 24 小時同步一次（SharedPreferences 記錄 lastSyncTime）
  - 合併遠端規則 + 本地回饋權重 = 最終規則集
  - 新增詐騙類型可動態擴充（不用更新 App）
- [ ] Supabase Edge Function：聚合所有用戶回饋 → 自動調整全局權重
  - 回報數 ≥ 10 且「是詐騙」比例 ≥ 70% → 關鍵字 weight 上調
  - 「不是詐騙」比例 ≥ 60% → weight 下調或移除

#### L-5 合成訓練資料生成
- [ ] scripts/generate_fraud_training_data.py — Claude API 批次生成
- [ ] 8 種詐騙類型 × 200 筆 + 1000 筆正常對話
- [ ] 輸出 JSONL（fraud_conversations + safe_conversations）
- [ ] 加入真實回報資料（L-3 收集）作為訓練補充

#### L-6 DistilBERT 訓練（Google Colab）
- [ ] scripts/train_fraud_detector.ipynb — bert-base-chinese fine-tune
- [ ] 訓練/驗證分割 + accuracy / precision / recall / F1 metrics
- [ ] PyTorch → ONNX → TFLite 轉換（INT8 量化，模型 < 30MB）
- [ ] 定期用新回報資料 retrain（每月/每季）

#### L-7 Android TFLite 本地模型整合
- [ ] security/FraudLocalModel.kt — 離線推論（50-80ms）
  - Tokenizer（WordPiece）+ TFLite Interpreter
  - 輸出：fraud_probability(0~1) + fraud_type
- [ ] assets/fraud_detector.tflite 打包（首版 ~25MB）
- [ ] FraudAIAnalyzer 三層架構：本地模型 → 規則引擎 → AI 深度分析
  - 本地模型可用時優先（離線、零費用、50ms）
  - 模型不可用時 fallback 規則引擎
  - 風險高時可選 AI 深度分析（消耗 token）
- [ ] build.gradle 加 TFLite 依賴（org.tensorflow:tensorflow-lite）
- [ ] 設定頁加「防詐模型更新」按鈕（手動下載最新 tflite）

#### L-8 持續學習閉環
- [ ] 模型版本管理 — Supabase 存 model_version / download_url / min_app_version
- [ ] App 啟動時檢查模型版本，有新版自動下載替換
- [ ] 訓練管線自動化 — GitHub Actions 定期跑 Colab notebook
- [ ] A/B 測試框架 — 規則引擎 vs TFLite 模型準確度對比 dashboard

---

## 下個版本（v0.6.0）

### 新增遊戲（Games_add.md 規劃 + 總任務包 第八批）

**現有 8 款（含 4 原始 + 4 新增）：**
- ✅ SnakeGame / WhackAMoleGame / RhythmGame / TypingChallengeGame
- ✅ WordGuardianGame / SpellingDuelGame / MemoryFlipGame / KeyboardMinesweeperGame

**待實作 13 款（已完成 9 款 + 額外 VoiceGame）：**

| 類型 | 遊戲 | 核心機制 | 狀態 |
|------|------|---------|------|
| 放置型 | ~~KeyboardTravelerGame 🏃~~ | 招財跑酷，打字=移動，停頓=坐下吃便當 | ✅ |
| 放置型 | ~~WarshipCruiseGame 🚢~~ | 戰艦巡航，打字=動力 | ✅ |
| 收集型 | ~~MoodMinerGame 💎~~ | 情緒採礦，打字情緒生原石 + 每週週報 | ✅ |
| 收集型 | ~~IceCrystalGame ❄️~~ | 冰晶收集，完整長句掉落冰晶 | ✅ |
| 動作型 | ~~BulletHellMiniGame ✈️~~ | 極簡彈幕，Swipe 閃避 15-30 秒局 | ✅ |
| 角色型 | ~~YukiQuizGame 🌸~~ | 雪凜問答，按鍵作答 | ✅ |
| 角色型 | ~~CoupleQuizGame 💘~~ | 情侶測驗，結果截圖分享 | ✅ |
| 對戰型 | ~~TypingRaceGame 🏆~~ | 打字競速 v1 單機版 | ✅ |
| AI 型 | ~~RandomChallengeGame 🎲~~ | 雪凜 AI 出題 + AI 評分 | ✅ |
| 節奏型 | MusicTypingGame 🎵 | 按鍵對應音符，打字=演奏 |
| 建造型 | YukiHouseGame 🏠 | 雪凜的家，打字累積建材 |
| 建造型 | TypingFarmGame 🌱 | 打��農場，輸入=澆水 |
| 解謎型 | CrosswordGame 🔲 | 填字遊戲，注音/英文填答 |
| 解謎型 | WordleGame 💡 | 台灣版 Wordle（注��猜詞） |
| 解謎型 | WordChainGame 🎭 | 暗語接龍 |
| 對戰型 | SpeedTypingBattleGame ⚔️ | 打字對決（分割畫面搶同一段文字） |
| 對戰型 | TypingBossRaid 🐉 | 打字團戰（多人打字攻擊 Boss）|
| 社群型 | TypingProfileCard 📊 | 打字履歷（WPM/常用詞/語言分布） |
| 劇情型 | YukiSecretDiary 📔 | 雪凜秘密日記，解密日記解鎖角色故事 |
| 情緒型 | EmotionThermometer 🌡️ | 情緒溫度計，正負文字比例視覺化 |

### 遊戲 Bug 修復（Games_bug.md）
- ✅ GameFloatingPanel scrollY 下限修復（totalContentHeight + cardPadding）
- ✅ GameLoopRunner 生命週期修復（onDetached → gameLoop.stop()）
- ✅ SpellingDuelGame 詞庫已在 res/raw/spelling_words.txt（225 詞）

### 工具面板
- [ ] 計算機面板（基礎四則 + 科學計算）
- [ ] 常用語面板（自訂快捷短語���）
- [ ] 色碼選擇器面板
- [ ] 單位換算面板（長度/重量/溫度/貨幣）���HEX/RGB 取色）
- [ ] 單���換算面板（長度/重量/溫度/貨幣）

### 個人化
- [ ] **按鍵文字透明度調整** — 新增 `keyTextOpacity` 拉桿（0~255），按鍵文字 alpha 獨立於背景透明度
- [ ] **鍵盤即時透明度調整** — 不用進設定頁，直接在鍵盤上調整（長按工具列或快捷手勢叫出滑桿 overlay）
  - 浮動滑桿：背景透明度 + 文字透明度，即時預覽
  - 調整完自動儲存到 YukiPreferences
- [ ] 打字統計儀表板（速度/常用詞/使用時間）
- [ ] 自製貼圖上傳（圖片→貼圖包）
- [ ] 鍵盤音效包（預設 + 自訂）
- [ ] 自訂震動模��

### 經濟系統
- [ ] Z ���系統完善（藍新金流儲值流��）
- [ ] 打字任��賺 Z 幣
- [ ] 皮膚商城（主題/貼圖/表情包）

### 無障礙
- [ ] 單手模式（鍵盤縮小靠左/靠右）
- [ ] 大字模式（放大按鍵 + 高對比度）

### UI / UX
- [ ] 背景圖片拖曳平移（pan drag）
- [ ] 設定頁裁切框（crop frame）
- [ ] ThemePanelView 內直接上傳自訂背景
- [ ] 面板 UI 重構��向上展開 + 長按懸浮化（PanelCoordinator）

### Lottie 佔位動畫
- ✅ yuki_freeze.json（情緒冷靜期結冰）
- [ ] yuki_battle_idle.json（遊戲待機）
- [ ] yuki_cupid.json（情聖月老）
- [ ] 所有 Lottie 載入處加 fallback 邏輯

---

## 長期目標（v1.0+）

### 輸入法進階
- [ ] 手寫辨識（MLKit / Tesseract）
- [ ] libchewing JNI 整合（替���純 Kotlin 引擎）
- [ ] AI 語音自動標點（Whisper + Claw 標點模型）
- [ ] 整句自動完成（ghost text 灰字 + Tab 接受）
- [ ] 智慧回覆建議（分析上下文 → 3 個回覆）
- [ ] OCR 貼上（拍照→文��辨識→貼上）
- [ ] AI 改寫語氣（選中文字→正式/輕鬆/可愛/專業）
- [ ] 雲端設定同步（換機不怕）
- [ ] 語氣強度滑桿

### 雪凜深度功能
- [ ] 雪凜語音朗讀（TTS）
- [ ] 雪��對話模式（鍵盤內聊天）
- [ ] 雪凜打���教學（新手引導）
- [ ] 心情日記 UI

### 3D / AR
- [ ] AR 雪凜（ARCore + 平面偵測 + 放��互動）
- [ ] SnowRealmVR 輸入介面（OpenXR 預留）

### 連線 & ��� App
- [ ] 打字競速 v2.0 連線配對（Supabase Realtime）
- [ ] CoinBeast 跨 App 聯動（Deep Link + Z 幣同步）
- [ ] 毛行天下聯動（打字距離同步）
- [ ] 情緒採礦週報接 Insight 分析

### 上架
- [ ] 正式雪凜 Lottie 角色動畫（需設計師素材）
- [ ] 正式 VRM 模型表情完善
- [ ] 上架 Google Play

---

## 實作順序建議

```
v0.6.0 路線圖 — 核心體驗 + 智慧輸入 + AI（約 63 項）
═══════════════════════════════════════════════════════════════

Phase 1：核心輸入修復（使用者痛點優先）
───────────────────────────────────────
  C-fix  候選字修復 3 項                    ← lookupMultiSyllable + UserDict 注音 + 詞庫管理 UI
    ↓
  C-vis  候選字視覺分類標示                  ← 5 色分類讓使用者知道候選來源
    ↓
  K-adv  滑動輸入進階 5 項                   ← SwipeGestureDetector + BopomofoSwipeDecoder + 視覺標記
    ↓
  遊戲 Bug 修復 3 項                        ← scrollY / lifecycle / 詞庫搬遷

Phase 2：智慧輸入（打字越用越聰明）
───────────────────────────────────────
  J-1  PersonalDictionary                   ← 三層自動學詞，所有智慧功能基礎
    ↓
  J-2  StyleProfiler                        ← 語氣學習，候選字更貼近用戶
    ↓
  J-3  ComboTracker                         ← 隱形遊戲化連擊系統 + 粒子特效
    ↓
  J-4  EmotionStickerEngine                 ← 情緒貼圖推薦

Phase 3：AI 擴充 + 防詐
───────────────────────────────────────
  E-providers  Gemini/Groq/Nano Provider    ← 免費 AI 方案 + 離線推論
    ↓
  E-stream   AI streaming + debounce        ← 串流即時顯示 + 候選列 AI 標記
    ↓
  E-guide    ApiGuideActivity               ← 使用者自助申請教學
    ↓
  L-1  FraudAIAnalyzer + Formatter          ← 規則引擎 + Gemini Flash 分析
    ↓
  L-2  回報系統 + Supabase                   ← 收集資料為訓練做準備

Phase 4：基建 + 內容
───────────────────────────────────────
  A-rime   RIME 詞庫 + RimeDictionary       ← 大詞庫支援（23 萬→50 萬+）
    ↓
  H-rest   MLKit 翻譯 + invite 腳本          ← 翻譯面板實際可用
    ↓
  中文 n-gram 上下文串聯預測                  ← 中文候選字語意排序
    ↓
  新遊戲一輪 4 款                            ← MusicTyping + YukiHouse + CrosswordGame + WordleGame
    ↓
  工具面板 4 項                              ← 計算機 + 常用語 + 色碼 + 單位換算

已完成：B ✅ D ✅ F ✅ G(WhisperSkill) ✅ I ✅
```

```
v0.7.0 路線圖 — 經濟系統 + 內容擴充 + 體驗打磨（約 30 項）
═══════════════════════════════════════════════════════════════

Phase 1：經濟系統上線（變現基礎）
───────────────────────────────────────
  Z 幣系統完善                               ← 藍新金流儲值 API 串接
    ↓
  打字任務賺 Z 幣                            ← 每日任務 + 成就獎勵系統
    ↓
  皮膚商城                                   ← 主題/貼圖/表情包用 Z 幣購買

Phase 2：防詐 AI 本地模型（L-3~L-5）
───────────────────────────────────────
  L-3  合成訓練資料生成                       ← Claude API 批次生成 2000+ 筆對話
    ↓
  L-4  DistilBERT 訓練（Colab）              ← bert-base-chinese fine-tune + TFLite 轉換
    ↓
  L-5  FraudLocalModel 整合                  ← 離線推論 50-80ms，零 API 費用

Phase 3：個人化 + 無障礙
───────────────────────────────────────
  打字統計儀表板                              ← 速度/常用詞/使用時間視覺化
    ↓
  鍵盤音效包                                 ← 預設 + 自訂音效
    ↓
  自訂震動模式                               ← 震動樣式選擇
    ↓
  單手模式                                   ← 鍵盤縮小靠左/靠右
    ↓
  大字模式                                   ← 放大按鍵 + 高對比度

Phase 4：遊戲二輪 + UI 打磨
───────────────────────────────────────
  新遊戲二輪 5 款                            ← TypingFarm + WordChain + SecretDiary + EmotionThermometer + SpeedTypingBattle
    ↓
  TypingBossRaid + TypingProfileCard         ← 對戰/社群型（需連線功能）
    ↓
  UI/UX 打磨                                ← 背景平移 + 裁切框 + 面板重構
    ↓
  Lottie 佔位動畫補完                        ← yuki_battle_idle + yuki_cupid + fallback 邏輯
    ↓
  自製貼圖上傳                               ← 圖片→貼圖包
```

```
v0.8.0 路線圖 — 進階輸入 + 雪凜角色深化（約 12 項）
═══════════════════════════════════════════════════════════════

Phase 1：AI 輸入進階（鍵盤智能再升級）
───────────────────────────────────────
  整句自動完成                               ← ghost text 灰字預覽 + Tab 一鍵接受
    ↓
  智慧回覆建議                               ← 分析輸入框上下文 → 浮出 3 個整句回覆
    ↓
  AI 改寫語氣                                ← 選取文字→正式/輕鬆/可愛/專業四模式
    ↓
  語氣強度滑桿                               ← 0~100 拖桿微調改寫幅度
    ↓
  AI 語音自動標點                            ← Whisper 辨識結果 + Claw 標點模型後處理

Phase 2：雪凜角色深化（讓雪凜從吉祥物變成夥伴）
───────────────────────────────────────
  雪凜語音朗讀（TTS）                        ← 候選字/翻譯結果語音播放
    ↓
  雪凜對話模式                               ← 鍵盤內聊天泡泡，問雪凜怎麼打/怎麼說
    ↓
  雪凜打字教學                               ← 新手引導：注音配置、滑動輸入、模式切換
    ↓
  心情日記 UI                                ← 每日情緒記錄介面 + 週報圖表

Phase 3：影像輸入
───────────────────────────────────────
  OCR 貼上                                   ← 拍照→MLKit 文字辨識→貼上到輸入框
    ↓
  手寫辨識                                   ← MLKit Digital Ink 手寫→文字轉換
    ↓
  雲端設定同步                               ← Supabase 備份偏好/詞庫/主題（換機還原）
```

```
v0.9.0 路線圖 — 連線社群 + 跨 App 生態（約 8 項）
═══════════════════════════════════════════════════════════════

Phase 1：連線對戰（從單機到多人）
───────────────────────────────────────
  打字競速 v2.0 連線配對                      ← Supabase Realtime 即時對戰房間
    ↓
  TypingBossRaid 連線版                      ← 多人同時打字攻擊 Boss，傷害即時同步
    ↓
  排行榜系統                                 ← Supabase leaderboard + 每週/每月/總榜

Phase 2：SnowRealm 生態聯動
───────────────────────────────────────
  CoinBeast 跨 App 聯動                      ← Deep Link yukiboard://reward + Z 幣雙向同步
    ↓
  毛行天下聯動                               ← 打字距離→步數同步 maoxing://distance
    ↓
  情緒採礦週報接 Insight Engine               ← MoodMinerGame 數據→Insight 情緒分析報告

Phase 3：注音引擎升級
───────────────────────────────────────
  libchewing JNI 整合                        ← NDK 編譯 libchewing + JNI bridge
    ↓
  libchewing 取代 ChewingInputEngine          ← 更準確的注音選字 + 聯想詞
```

```
v1.0.0 路線圖 — 品質收尾 + 上架（約 8 項）
═══════════════════════════════════════════════════════════════

Phase 1：角色素材正式化（需設計師協作）
───────────────────────────────────────
  正式雪凜 Lottie 角色動畫                    ← 替換所有佔位幾何動畫，完整表情組
    ↓
  正式 VRM 3D 模型完善                       ← morph targets 表情微調 + 服裝切換
    ↓
  Lottie 全局 fallback 邏輯                   ← 任何動畫載入失敗→優雅降級不 crash

Phase 2：沉浸體驗
───────────────────────────────────────
  AR 雪凜                                    ← ARCore 平面偵測 + 雪凜放置互動 + 拍照分享
    ↓
  SnowRealmVR 輸入介面                       ← OpenXR 預留架構（Meta Quest / Vision Pro）

Phase 3：上架準備
───────────────────────────────────────
  效能最佳化                                 ← 記憶體/啟動速度/電量消耗全面 profiling
    ↓
  隱私合規                                   ← 隱私權政策 + GDPR/個資法合規檢查
    ↓
  Google Play 上架                           ← 商店頁面素材 + 審核 + 分階段發布
```

---

### 批次 M — 語音辨識 UX 重構 + Faster-Whisper

> 目標：辨識中文字不直接寫入輸入框，改用液態玻璃氣泡框 Overlay 顯示，辨識完才一次送入

#### M-1 VoiceRecognitionOverlay（液態玻璃氣泡框）
- [ ] 新增 `ui/speech/VoiceRecognitionOverlay.kt` — 鍵盤上方浮動氣泡框
  - 頂部標題「語音辨識中...」+ 引擎名稱小字
  - 液態玻璃效果（模糊背景 + 半透明漸層 + 圓角 + 光澤邊框 + 內陰影）
  - RenderEffect.createBlurEffect() 毛玻璃 + 漸層邊框呼吸動畫
  - 辨識中文字區域：兩行高度 + 可垂直 scroll（長文不截斷）
  - 文字即時更新（typewriter 逐字浮現動畫）
  - 聲波動畫條（3~5 條高低跳動，表示正在收音）
  - 雪凜小頭像在氣泡左側（listening 表情）
- [ ] 底部按鈕列：「取消 ✕」「確認送出 ✓」
- [ ] 辨識完成自動送出（3 秒倒數，可手動提前送出或取消）
- [ ] 顯示當前引擎名稱（Android / Whisper / Faster-Whisper）

#### M-2 語音辨識流程重構
- [ ] `YukiBoardIMEService.toggleVoiceInput()` 改用 Overlay
  - partial result → `overlay.updateText(partial)`（不再 setComposingText）
  - final result → `overlay.showFinal(result)` + 倒數自動 commitText
  - 取消 → `overlay.dismiss()` 不送任何文字
- [ ] Overlay 確認送出 → `commitText(result, 1)` 一次性寫入
- [ ] 錯誤訊息也顯示在 Overlay 內（不用 Toast）

#### M-3 Faster-Whisper Server（Zeabur 部署）
- [ ] 新建 `faster-whisper-server/` 專案目錄
  - `main.py` — FastAPI server
  - `Dockerfile` — Python 3.11 + faster-whisper + CUDA（Zeabur GPU）
  - `zeabur.json` — 部署設定
  - `requirements.txt` — faster-whisper, fastapi, uvicorn, python-multipart
- [ ] `POST /v1/transcribe` — 接收 WAV/WebM，回傳 JSON `{ "text": "..." }`
  - 參數：`language`（可選，自動偵測）、`model`（tiny/base/small/medium）
  - 回傳：`{ "text", "language", "duration", "segments" }`
- [ ] `POST /v1/transcribe/stream` — SSE 串流 partial results
- [ ] 健康檢查 `GET /health`
- [ ] Zeabur 部署 + 綁定 subdomain（如 `whisper.snowrealm.com`）

#### M-4 FasterWhisperSpeechEngine.kt
- [ ] 新增 `speech/FasterWhisperSpeechEngine.kt`
  - AudioRecord 錄音 → WAV → POST 到自架 server
  - 支援 SSE partial results
  - server URL 從 YukiPreferences 讀取
- [ ] `SpeechInputEngine` 介面不變，直接替換

#### M-5 設定頁整合
- [ ] 語音引擎選單新增第三選項「Faster-Whisper（自架・免費）」
- [ ] 新增 Faster-Whisper Server URL 輸入欄（預設 `https://whisper.snowrealm.com`）
- [ ] 新增「測試連線」按鈕（呼叫 `/health`）
- [ ] YukiPreferences 新增 `fasterWhisperServerUrl` 屬性

---

### 批次 N — UI 缺口修復

#### N-1 Z幣錢包入口 ✅
- [x] 設定頁加「Z幣錢包」按鈕 → 跳轉 `ZCoinWalletActivity`
- [ ] 顯示目前餘額預覽

#### N-2 懸浮鍵盤快捷入口 ✅
- [x] 長按 Tab 環形選單加「懸浮」選項（FloatingKeyboardWrapper toggle）

#### N-3 Snippet 管理 UI ✅
- [x] 設定頁加「Snippet 管理」入口
- [x] 列表顯示所有 snippet（內建 + 自訂）
- [x] 新增 / 刪除自訂 snippet 對話框

#### N-4 輸入法選擇 ✅
- [x] 設定頁加「啟用的輸入法」多選
- [x] 勾選：注音 / 拼音 / 倉頡 / 速成 / 日文 / 韓文 / 英文
- [x] InputRouter.enabledModes 讀取設定

#### N-5 VRM 角色開關 ✅
- [x] 設定頁動畫群組加 `vrmCharacterEnabled` toggle

#### N-6 Dead Preferences 清理 ✅
- [x] 移除 `hapticFeedbackEnabled`（已有 `keyPressVibration` 取代）
- [x] 移除 `multiplayerEnabled`（未使用）
- [x] 保留 `weatherPanelEnabled`、`stickerPanelEnabled`（FloatingPanelManager 使用中）
- [x] 保留 `keyboardWidthRatio`（IMEService 使用中）

---

## 🔴 關鍵問題（v0.9.21 掃描）

- [ ] **藍新金流未接**：build.gradle 有 NEWEBPAY_MERCHANT_ID / HASH_KEY / HASH_IV，但無付款處理程式碼。Z幣儲值無法使用
- [ ] **Supabase 未接**：build.gradle 有 YUKIBOARD_SUPABASE_URL / ANON_KEY，但 codebase 零整合。Z幣餘額、排行榜、皮膚商城後端缺失

## 🟡 中等問題（v0.9.21 掃描）

- [ ] **AI Writer 開關無效**：`YukiPreferences.aiWriter` 有 switch 但 Service 未檢查就執行 WriterSkill
- [ ] **AI EmojiTranslate 開關無效**：`YukiPreferences.aiEmojiTranslate` 同上
- [ ] **AI ToneAdjust 無前端觸發**：後端 ToneAdjustSkill 存在但沒有 UI 入口呼叫
- [ ] **AI CodeComplete / RegexGen 開關**：設定有但無明確整合路徑
- [ ] **GIF 面板搜尋**：onSearch 依賴 GiphyApiClient + API key，無 key 時無法搜尋
- [ ] **天氣面板穿衣建議**：outfitText UI 有但推薦邏輯是 stub（"需解鎖"）
- [ ] **TTS 設定**：ttsTranslateRead / ttsCandidateRead 偏好可能未完全生效
- [ ] **VRM 3D 角色**：框架完整但模型素材 / 資產管線不確定
- [ ] **EmotionalCooldownManager**：情緒冷靜期管理器邏輯可能不完整
- [ ] **OpenWeather API**：key 有設定但實際 API 呼叫完整性待確認

## 🆕 新功能：剪貼簿強化（加密 + 分類 + 跨裝置 + 多媒體）

### 核心：加密剪貼簿
- [ ] **剪貼簿加密儲存** — 敏感項目可標記為「🔒加密」，AES-256 加密存入本地 DB
  - 長按項目 →「加密保存」選項，需設定解鎖密碼/生物辨識
  - 加密項目顯示為「●●●●●●」遮罩，點擊需驗證才顯示明文
  - 自動偵測敏感內容（信用卡號、密碼格式、身份證號）→ 提示加密
- [ ] **加密項目解鎖** — 生物辨識（指紋/臉部）或密碼解鎖後顯示 30 秒自動遮回

### 一鍵複製 & 貼上
- [ ] **點一下複製** — 剪貼簿項目點擊即複製到系統剪貼簿（文字/圖片/檔案皆可）
  - 文字：直接複製 + Toast 提示「已複製」
  - 圖片：複製為 ClipData.Item(Uri) 供其他 App 貼上
  - 檔案：複製檔案 Uri 到系統剪貼簿
- [ ] **長按選單** — 複製 / 加密 / 釘選 / 分類 / 刪除

### 分類 & 搜尋
- [ ] **自動分類標籤** — 依內容自動標記：📝文字 / 🔗連結 / 📧信箱 / 📞電話 / 🖼️圖片 / 📎檔案 / 🔒加密
- [ ] **手動分類** — 使用者可自訂標籤（工作/個人/密碼/地址等）
- [ ] **搜尋功能** — 頂部搜尋框，即時過濾剪貼簿歷史（加密項目需解鎖後才可搜尋內容）
- [ ] **篩選標籤列** — 橫向標籤快速篩選分類

### 圖片 & 檔案支援
- [ ] **圖片剪貼簿** — 監聽 ClipboardManager 圖片項目，縮圖預覽 + 儲存本地
- [ ] **檔案剪貼簿** — 支援檔案 Uri 記錄，顯示檔名 + 類型圖示 + 大小
- [ ] **截圖自動擷取** — 偵測新截圖自動加入剪貼簿歷史（可設定開關）

### 釘選管理強化
- [ ] **釘選群組** — 釘選項目可分組管理（常用帳號/地址/簽名檔等）
- [ ] **釘選排序** — 拖曳排序釘選項目順序
- [ ] **釘選上限提升** — 不限數量（原有限制移除）

### 跨裝置同步
- [ ] **Supabase 同步** — 剪貼簿歷史 + 釘選項目雲端備份（需登入）
  - 加密項目以加密狀態同步（伺服器無法讀取明文）
  - 衝突解決：以最新時間戳為準
- [ ] **同步開關** — YukiPreferences 加 `clipboardSyncEnabled`
- [ ] **同步範圍設定** — 僅釘選 / 全部 / 排除加密

## 🆕 新功能：自訂辭彙 CRUD（功能列入口）

- [ ] **功能列新增「📖辭彙」按鈕** — ToolbarView 加入自訂辭彙面板入口
- [ ] **UserDictionaryPanelView** — 新面板顯示使用者自訂詞庫列表
  - 列表顯示所有自訂詞彙（注音/拼音 → 漢字對應）
  - 新增按鈕：輸入讀音 + 詞彙 → 加入 UserDictionary
  - 編輯功能：點擊詞條可修改讀音或詞彙
  - 刪除功能：左滑或長按刪除
  - 搜尋/篩選功能
- [ ] **UserDictionary CRUD 完善** — 確保 addManual / update / delete / queryAll 完整
- [ ] **匯出/匯入** — 自訂辭彙匯出為 txt/csv + 匯入還原

## 🆕 新功能：語音音色選擇（Google TTS）

- [ ] **TTS 音色列表** — 列出裝置可用的 Google TTS Voice（TextToSpeech.getVoices()）
  - 按語言分組（中文/英文/日文/韓文等）
  - 顯示音色名稱 + 性別 + 語言
- [ ] **設定頁 TTS 音色選擇器** — 下拉選單或列表讓使用者選擇偏好音色
  - YukiPreferences 新增 `ttsVoiceName: String`
  - 「試聽」按鈕：選中音色後播放範例句子
- [ ] **套用音色** — SpeechEngine / 候選字朗讀 / 翻譯朗讀使用選定音色
  - `TextToSpeech.setVoice()` 套用使用者選擇

## 🆕 新功能：悄悄話解密面板

- [ ] **解密面板 UI**：工具列新增「🔐」按鈕，開啟 DecryptPanelView
  - 上方：密碼輸入框（對方約定的密碼）
  - 中間：貼上加密訊息的輸入框
  - 下方：「解密」按鈕 → 顯示原始明文
  - 支援「複製明文」按鈕
- [ ] **加密模式改為密碼制**：私房話模式從固定 passphrase 改為使用者自訂密碼
  - 發送方：長按工具列🔐 或在設定設定密碼 → Enter 送出時用該密碼 AES 加密
  - 接收方：開啟解密面板 → 輸入密碼 → 貼上密文 → 解密
- [ ] **加密訊息格式**：`[YUKI🔐]Base64密文` 讓雙方辨識這是 YukiBoard 加密訊息

---

## 備註

- 版本號：**v0.9.30**（versionCode 96）
- deploy 指令：`bash scripts/deploy.sh "更新說明"` 或 `./gradlew appDistributionUploadDebug`
- Firebase App Distribution 測��群��：`testers`
- google-services.json 已在 .gitignore
- YukiBoard Supabase DB：`43.167.237.37:30183`（獨立專案）
- Insight Engine ��後台管理邀請碼 + 貼圖
- 注音詞庫現有 23 萬行 dict + 4 萬行 phrases（McBopomofo 來源）
- RIME 詞庫為額外補充��SQLite 約 8-15MB，打包進 assets

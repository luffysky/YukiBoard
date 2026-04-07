# DailyWorks — 2026-04-07

> YukiBoard v0.9.21 (versionCode 87)
> Firebase App Distribution 已上傳 testers 群組

---

## 今日完成項目

### 1. RIME 拼音詞庫合併
- 執行 `scripts/merge_rime_pinyin.py` 拼音→注音轉換
- bopomofo_dict.txt：+58K 字詞（257K keys / 382K words）
- bopomofo_phrases.txt：+14K 詞組（48K keys / 165K phrases）

### 2. 五語詞庫大擴充
- **英文**：`english_words.txt` 20K→50K 高頻詞
- **韓文**：`ko_words.txt` ~3,578 詞 + KoreanInputEngine 檔案載入 + fallback
- **日文**：`ja_kanji_dict.txt` ~300 讀音 + JapaneseInputEngine 檔案載入 + fallback
- **倉頡**：`cangjie_dict.txt` ~5,048 行 + CangjieInputEngine 檔案載入 + 索引重建

### 3. 候選字排序修復
- CandidateManager.update()：當前段候選排前面（深色）、歷史候選排後面（淺色）
- 自動封存邏輯同步調整

### 4. CJK 模式英文洩漏修復
- InputRouter：句子預測 + 上下文預測加 `c.code > 0x2E80` CJK 過濾
- 拼音/注音/倉頡/日文/韓文模式不再顯示英文候選

### 5. GIF 面板搜尋框修復
- GifPanelView.onMeasure() 固定返回 200dp 高度
- 移除 search bar 點擊後的 requestLayout() 呼叫
- 搜尋框不再跳回/面板不再收縮

### 6. 滑動輸入多段累加候選
- YukiBoardIMEService 新增 `swipeAccumulated` 累積列表
- `buildSwipeCombinedCandidates()` 產生多字組合候選
- 選字/按鍵時清除累積

### 7. 注音選字殘留修復
- 問題：候選字來自句子預測（非引擎內部候選），indexOf 找不到 → selectCandidateAt 傳錯索引 → 注音 buffer 殘留
- 修復：偵測非引擎候選 → engine.reset() + 直接 commitText
- 影響引擎：ChewingInputEngine / PinyinInputEngine / CangjieInputEngine / JapaneseInputEngine / KoreanInputEngine

### 8. 移除「直打注音」設定
- SettingsActivity 移除 `rawBopomofoMode` switchRow
- 功能已直接整合在鍵盤上（長按注音鍵直出）

### 9. 全專案掃描
- 掃出 2 項關鍵問題：藍新金流未接、Supabase 未接
- 掃出 10 項中等問題：AI 開關未生效、GIF API key、天氣穿衣建議 stub 等
- 結果已加入 todo_list_0409.md

### 10. Firebase 部署
- v0.9.21 (87) release build 成功上傳
- testers 群組已收到更新通知

### 11. 私房話模式確認
- SecretMessageManager AES-256-GCM 加密功能正常
- 格式：`[YB🔐] {base64}`，Enter 整句加密，自動偵測解密

---

## 新增待辦

### 加入 todo_list_0409.md
- 🔴 藍新金流未接（Z幣儲值無法使用）
- 🔴 Supabase 未接（Z幣餘額、排行榜、皮膚商城缺失）
- 🟡 AI Writer/EmojiTranslate 開關無效等 10 項
- 🆕 悄悄話解密面板（密碼制 AES 加密，雙方約定密碼）
- 🆕 自訂辭彙 CRUD（功能列入口 + 面板 + 編輯/刪除/匯出）

---

## 修改的檔案

| 檔案 | 變更 |
|------|------|
| `input/candidate/CandidateManager.kt` | 候選字排序：當前→歷史 |
| `input/router/InputRouter.kt` | CJK 過濾 + 非引擎候選 commit + context 傳遞 |
| `input/engine/JapaneseInputEngine.kt` | Context + 檔案載入 ja_kanji_dict.txt |
| `input/engine/KoreanInputEngine.kt` | Context + 檔案載入 ko_words.txt |
| `input/engine/CangjieInputEngine.kt` | Context + 檔案載入 cangjie_dict.txt |
| `service/YukiBoardIMEService.kt` | 滑動累加 + buildSwipeCombinedCandidates() |
| `ui/panel/GifPanelView.kt` | onMeasure 固定 200dp + 移除 requestLayout |
| `ui/settings/SettingsActivity.kt` | 移除直打注音 switch |
| `app/build.gradle` | v0.9.21 (87) + releaseNotes |
| `res/raw/english_words.txt` | 20K→50K |
| `res/raw/ko_words.txt` | 新增 3,578 韓文詞 |
| `res/raw/ja_kanji_dict.txt` | 新增 ~300 讀音 |
| `res/raw/cangjie_dict.txt` | 新增 ~5,048 行 |
| `res/raw/bopomofo_dict.txt` | RIME 合併 +58K |
| `res/raw/bopomofo_phrases.txt` | RIME 合併 +14K |
| `todo_list_0409.md` | 更名 + 加入掃描結果 + 新功能 |

---

> ~~下次繼續：2026-04-09（禮拜四）~~
> ~~優先項目：自訂辭彙 CRUD 面板、悄悄話解密面板~~

---

## 下午場（v0.9.22→v0.9.30, versionCode 88→96）

### 12. Insight Engine AI Proxy 動態金鑰
- `ai-proxy/route.ts` 4 個 API 呼叫函數改接動態 `apiKey` 參數
- 新增 `getApiKey()` — 查詢 Supabase `platform_ai_models` 表取加密金鑰（AES-256-GCM）
- 新增 `getCachedApiKey()` — 5 分鐘記憶體快取，避免每次請求都查 DB
- fallback 機制：DB 無資料時自動降級使用環境變數
- 修復 502：部署版本仍用舊的全域常數，推送新版後恢復

### 13. n8n 自動化工作流（6 個）
- `tools/n8n_create_all_workflows.js` 建立 6 個 workflow 於 n8n.snowrealm.pet
- **防詐資料同步** — 每日 3:00 呼叫 FraudOpenDataSync API
- **AI 模型健康檢查** — 每 6 小時檢查 5 平台 API 可用性
- **使用量統計報表** — 每週一 9:00 彙總 token 用量
- **邀請碼到期提醒** — 每日檢查即將過期邀請碼
- **詞庫同步更新** — 每週日 2:00 同步開源詞庫
- **貼圖/主題包同步** — 每日 4:00 同步新資源

### 14. 防詐開源資料擴充（4→7 來源）
- FraudOpenDataSync.kt 新增 3 個資料來源：
  - **MODA CSV** — 數位發展部公開詐騙網址 CSV
  - **URLhaus** — abuse.ch 惡意 URL 資料庫
  - **CERT.PL** — 波蘭 CERT 釣魚網域清單
- 新增 `fetchTextUrlList()` — 通用 TXT/CSV/domain-per-line 格式解析器

### 15. Dialog 被鍵盤遮擋修復
- 問題：面板 Dialog 使用 SOFT_INPUT_ADJUST_RESIZE，overlay window 下無效
- 修復：改為 SOFT_INPUT_ADJUST_PAN + Gravity.TOP + 48dp top margin
- 影響：GifPanelView / TranslatePanelView / ClipboardPanelView / UserDictionaryPanelView / YukiBoardIMEService.showImeDialog()

### 16. AUTO 模式雙語候選字修復
- 問題：AUTO 模式 updateCandidates() 只用 activeEngine 候選，無中英混合
- 修復：AUTO 模式偵測到 autoQwertyBuffer 時呼叫 `getAutoCombinedCandidates()`
- 效果：打字同時顯示中文注音候選 + 英文 QWERTY 候選

### 17. 候選字選取後組字延續修復
- 問題 A：PREDICTION/HISTORY 攔截過早，引擎候選被 directCommit 而非走 selectCandidateAt
- 修復 A：移除早期 CandidateSource 攔截，讓引擎優先處理
- 問題 B：directCommit 時 ChewingEngine 的 selectedBuffer 被 reset 清除
- 修復 B：directCommit 先讀取 composingText，reset 後將 pending + text 合併提交

### 18. Firebase App Distribution 部署
- v0.9.30 (96) release build 成功上傳
- testers 群組已收到更新通知

---

### 下午場修改的檔案

| 檔案 | 變更 |
|------|------|
| `insight-engine/.../ai-proxy/route.ts` | 動態 API Key + getApiKey/getCachedApiKey |
| `tools/n8n_create_all_workflows.js` | 新增：6 個 n8n workflow 建立腳本 |
| `security/FraudOpenDataSync.kt` | +3 開源來源 + fetchTextUrlList() |
| `service/YukiBoardIMEService.kt` | showImeDialog() ADJUST_PAN + Gravity.TOP |
| `input/router/InputRouter.kt` | AUTO 雙語候選 + selectCandidate 修復 + directCommit 組字延續 |
| `ui/panel/GifPanelView.kt` | Dialog ADJUST_PAN 修復 |
| `ui/panel/ClipboardPanelView.kt` | Dialog ADJUST_PAN 修復 |
| `ui/panel/UserDictionaryPanelView.kt` | Dialog ADJUST_PAN 修復 |
| `todo_list_0409.md` | 版號更新 v0.9.30 (96) + v0.9.22~v0.9.30 記錄 |
| `app/build.gradle` | versionCode 96, versionName 0.9.30 |

---

### 待處理
- ⚠️ `platform_ai_models` 表尚未在 Supabase 建立（404，AI Proxy 目前走 env fallback）
- 💡 Insight Engine n8n 可進一步擴展（社群監控、圖片審核等）

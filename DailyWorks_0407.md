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

> 下次繼續：2026-04-09（禮拜四）
> 優先項目：自訂辭彙 CRUD 面板、悄悄話解密面板

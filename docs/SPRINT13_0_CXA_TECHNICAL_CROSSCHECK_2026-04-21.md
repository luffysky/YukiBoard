# Sprint 13-0 CXA — Technical Cross-Check

日期：2026-04-21
提交者：`衡鑑`（CXA）
任務 ID：`S13-0-CXA-TECHNICAL-CROSSCHECK`
交付型：technical cross-check / file scope review / contract coherence review
提交對象：`玄樞`（final gate）

---

## A. 審核範圍

本次二審對象：

1. [docs/SPRINT13_0_CCA_LEGAL_PRIVACY_CONTRACT_AUDIT_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_0_CCA_LEGAL_PRIVACY_CONTRACT_AUDIT_2026-04-21.md)
2. [docs/SPRINT13_0_CCB_NETWORK_FRAUD_SYNC_THREAT_AUDIT_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_0_CCB_NETWORK_FRAUD_SYNC_THREAT_AUDIT_2026-04-21.md)

本輪交付為純文件 audit，未修改 `app/src/main/**`。  
但本二審仍以實檔為準，已重讀並對照以下關鍵檔：

1. [app/src/main/java/com/snowrealm/yukiboard/data/CloudSyncManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/data/CloudSyncManager.kt)
2. [app/src/main/java/com/snowrealm/yukiboard/security/FraudReportManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/security/FraudReportManager.kt)
3. [app/src/main/java/com/snowrealm/yukiboard/ui/settings/AISettingsActivity.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/AISettingsActivity.kt)
4. [app/src/main/java/com/snowrealm/yukiboard/claw/ClawClient.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/claw/ClawClient.kt)
5. [app/src/main/java/com/snowrealm/yukiboard/claw/provider/AIProviderManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/claw/provider/AIProviderManager.kt)
6. [app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/ClipboardSettingsSection.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/ClipboardSettingsSection.kt)
7. [app/src/main/java/com/snowrealm/yukiboard/ui/settings/YukiPreferences.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/YukiPreferences.kt)
8. [app/src/main/java/com/snowrealm/yukiboard/auth/SnowRealmAuthClient.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/auth/SnowRealmAuthClient.kt)
9. [app/src/main/java/com/snowrealm/yukiboard/ui/panel/ClipboardPanelView.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/panel/ClipboardPanelView.kt)
10. [app/src/main/assets/legal/privacy_policy.html](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/assets/legal/privacy_policy.html)
11. [app/src/main/AndroidManifest.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/AndroidManifest.xml)
12. [app/src/main/res/xml/network_security_config.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/res/xml/network_security_config.xml)

---

## B. 結論摘要

### B.1 總結

`S13-0` 兩份 audit 的主軸判斷成立，且彼此分工清楚：

- `CCA` 聚焦 contract / wording / consume / consent 邊界
- `CCB` 聚焦 runtime / service / data flow / network / storage 邊界

本輪最重要的結論也一致且被實檔支撐：

1. `privacy_policy.html` 對刪除權、可攜權、AI provider 揭露、sync 上傳範圍的承諾，超出目前程式實作能力
2. `clipboardSyncEnabled` 是真空 preference，設定文案宣稱可同步，但 runtime 無任何對應路徑
3. `CloudSyncManager` 的 `user_learning_data` 真實會同步多組學習資料，與 policy 的「打字習慣僅本機」直接衝突
4. `FraudReportManager` 的匿名上傳資料最小化方向成立，但 rate limit、RLS 驗證、觀察性仍有缺口

### B.2 本輪二審處理結果

`CCA` 原稿中我已要求修正的兩個錯配已收斂：

1. 原先把 `Mood / 心情日記` 寫進 `CloudSyncManager` 同步範圍，現已修正為實際存在的 `TypingHabit / Style / Adaptive / Personal / Phrase / Sentence / CorrectionLearn`
2. 原先把 `GeminiNano` 混入「可能上傳的 provider 缺口」，現已修正為本地推論，不列入上傳清單

因此本輪沒有 blocking factual mismatch 殘留在 `CCA` / `CCB` 正式文件內。

---

## C. 技術核對結果

### C.1 AI consent / provider 揭露

實檔確認：

1. `ClawClient.isAvailable()` 只檢查 provider config + invite/key/login，沒有 consent gate
2. `AISettingsActivity` 有 provider 選擇、API key、model 選擇，但沒有首次同意、撤回同意、per-provider 隱私提示
3. `AIProviderManager` / `AISettingsActivity` 實際 provider 包含：
   - OpenAI
   - Anthropic
   - Gemini
   - Groq
   - Gemini Nano
   - SnowRealm
4. `privacy_policy.html §2.2 / §5` 目前只揭露 OpenAI / Anthropic

二審判定：

- `CCA` 對 AI consent 缺口的定義成立
- `GeminiNano` 不應列入雲端上傳 provider 清單，修正後表述可接受
- `S13-3` 進 round 前，確實需要先用 contract 把：
  - 何時同意
  - 切 provider 是否重簽
  - `aiEnabled` 與 consent 的分離
  - 撤回後 runtime gate
  寫清楚

### C.2 export / delete 契約缺口

實檔確認：

1. `privacy_policy.html §6` 仍寫「設定 → 清除所有資料」
2. `SnowRealmAuthClient.logout()` 只清 local token / profile / plan 類 SharedPreferences
3. 現有可見匯出能力主要是詞庫匯出，不存在完整帳號資料 dump
4. `ClipboardPanelView` 有局部刪除與「清除全部未釘選」，但沒有 app-level「清除所有本機資料」入口

二審判定：

- `CCA` 對刪除權 / 可攜權 / logout 邊界的定義成立
- `CCB` 補的 runtime/storage 缺口與 `CCA` 的契約分層一致，沒有互撞
- `fraud_reports` 因匿名 hash 設計，本來就不具 per-user delete 能力，這點必須寫進後續 contract，不能在 UI 說得比實作更強

### C.3 sync / clipboard / userdict

實檔確認：

1. `YukiPreferences.clipboardSyncEnabled` 存在
2. `ClipboardSettingsSection` 文案明寫「透過 Insight 同步剪貼簿（需登入）」
3. 全專案沒有 clipboard upload/download/sync path
4. `ClipboardPanelView` 使用 `yukiboard_clipboard` 本地 SharedPreferences
5. `CloudSyncManager` 實際同步四表：
   - `user_settings`
   - `user_dictionary`
   - `user_themes`
   - `user_learning_data`
6. `user_learning_data` 真實內容包含：
   - `adaptive_dict`
   - `personal_dict`
   - `phrase_predict`
   - `sentence_predict`
   - `style_profile`
   - `typing_habits`
   - `correction_learn`
7. `downloadDictionary()` 只補不存在詞，不做刪除回放
8. `syncOnLogin()` 是 download-only

二審判定：

- `CCA` 與 `CCB` 都把 `clipboardSyncEnabled` 標成高優先風險，這個判定正確
- `CCA` 對 policy 衝突的描述，在修正 `Mood` 後已與實作一致
- `CCB` 對 `UserDictionary` 刪除不回放、`syncOnLogin` 單向下載、clipboard contract 懸空的判定成立
- `S13-5` 應優先先收斂「不再虛假承諾」，而不是偷做 clipboard sync 本體

### C.4 network security / fraud report runtime

實檔確認：

1. `AndroidManifest.xml` 已設 `usesCleartextTraffic="false"`
2. `network_security_config.xml` base-config 禁 cleartext，debug-only user CA，另外只放行 `localhost` / `127.0.0.1`
3. `FraudReportManager` 上傳欄位為：
   - `text_hash`
   - `fraud_type`
   - `matched_keywords`
   - `is_fraud`
   - `rule_score`
   - `device_locale`
4. `FraudReportManager` 未傳 `userId` / device id / 原文
5. `FraudReportManager.report()` 失敗時只有 `false`，沒有 error 分類

二審判定：

- `CCB` 對 network baseline 的結論成立：當前不是 cleartext 全面失守，而是已達基線、僅有本地例外與少量 observation
- `CCB` 對 fraud report runtime 風險分級也成立：核心問題不是資料過量，而是 rate limit、RLS 可驗證性與觀察性不足

---

## D. Blocking / Non-Blocking

### D.1 Blocking

本輪 blocking 數量：`0`

理由：

1. `CCA` 與 `CCB` 都已形成正式實檔
2. `CCA` 先前的 factual mismatch 已修正
3. 兩份文件的 scope 都符合 `S13-0 Legal Scope Cut`：
   - 無 `.kt` 改動
   - 未偷開 `S13-1 ~ S13-5`
   - 未越界到對方負責面

### D.2 Non-Blocking

1. `CCA` 文件內的「衡鑑 cross-check 時建議抽查」字樣，不影響 round 結論，但作為流程描述不夠嚴。後續若再寫給 `衡鑑` 的驗證說明，建議改成「逐項對照實檔」。
2. `CCB` 的 network 區把 `AppHttpClient` callsite 收斂數寫成 26 處；這屬於現況盤點數字，不影響主結論，但若後續 round 要引用該數字，建議重新跑一次全文搜尋，避免數量隨提交漂移。

---

## E. 對玄樞的提交建議

### E.1 判定建議

`READY`

Reason:
1. `S13-0` 的 contract / wording / runtime / data flow 邊界已完成雙份 audit，且已經過 `衡鑑` 依實檔對照收斂。
2. 本輪最重要的高優先風險已被明確切出，特別是：
   - `clipboardSyncEnabled` 虛假承諾
   - `privacy_policy.html` 超額承諾
   - AI consent 缺口
   - export / delete 責任邊界不存在
3. 本輪沒有偷跑 implementation，也沒有越界碰 `app/src/main/**`；適合作為 `S13-1 ~ S13-5` 的正式開門前置。

### E.2 開門順序建議

若 `玄樞` 採納本二審，後續優先順序建議為：

1. `S13-1` 可輕量處理，因 network baseline 已基本到位
2. `S13-5` 與 `S13-3` 應視合規急迫性優先安排
3. `S13-4` 必須把 export / delete 能力邊界寫清，避免 UI 再超額承諾
4. `S13-2` 若要動 `FraudReportManager.kt`，需同步處理 rate limit / keyword 洩漏縮量 / RLS 驗證步驟

---

## F. 交付摘要

### F.1 變更摘要

1. `CCA` 文件已修正 sync 範圍與 AI provider 類型錯配
2. `CCB` 文件已正式落地為實檔
3. `S13-0` 兩份文件現已具備提交 `玄樞` 的完整性

### F.2 風險

1. 若後續 `S13-3 / S13-4 / S13-5` 並行改 `privacy_policy.html`，必須統一 merge，不可各自覆蓋
2. 若 `S13-5` 不先處理 `clipboardSyncEnabled`，則合規風險會持續暴露在設定頁
3. 若 `S13-4` 只做 wording 不補最小本機清除 / 匯出能力，政策風險只能降一半

### F.3 下一步建議

1. 交 `玄樞` 做 final gate
2. 若 `玄樞` 放行，下一門應直接指定：
   - 是否把 `clipboardSyncEnabled` 視為先移除還是先灰化
   - `S13-3` 的 consent contract 是整組 AI 還是 per-provider
   - `S13-4` 是否至少落一個 Tier 2 本機資料管理入口

### F.4 架構決策

1. `S13-0` 僅建立 contract，不做 implementation，決策正確
2. 對於 clipboard sync，先下架虛假承諾優先於偷做最小同步
3. 對於 AI consent，先把 provider / consent / opt-out 契約寫清，再進入 UI 與 runtime gate 實作

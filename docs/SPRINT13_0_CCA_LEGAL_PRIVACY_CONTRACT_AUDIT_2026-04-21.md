# Sprint 13-0 CCA — Legal / Privacy / Contract Audit

日期：2026-04-21
提交者：`雪鑰`（CCA）
任務 ID：`S13-0-CCA-LEGAL-PRIVACY-CONTRACT-AUDIT`
交付型：audit / contract / wording cut（**不改 `app/src/main/**`**）
提交對象：`衡鑑`（technical cross-check）→ `玄樞`（final gate）

---

## A. Executive Summary

本輪盤點 `Sprint 13` 主題「合規高優先項 / 隱私與 consent 收斂 / sync 類資料責任邊界」中，三個 CCA-負責範圍：

1. AI consent / opt-out
2. data export / delete
3. clipboard / userdict sync

**總體結論：** YukiBoard 目前的隱私承諾（`privacy_policy.html`）與實際行為（code path）之間有 **結構性不一致**。主要問題不在 bug，而在 **政策宣告超出實作能力**：政策允諾的 6 項使用者權利裡，**至少 4 項沒有對應 UI／code path**，政策對「哪些資料上傳 / 哪些資料本機」的描述與 `CloudSyncManager` 實際同步範圍 **直接衝突**。

**風險光譜：**

- 🔴 **P0 Critical**：Privacy policy §6「刪除權 → 設定→清除所有資料」完全不存在；`clipboardSyncEnabled` UI 開關寫「透過 Insight 同步剪貼簿」但後端無對應實作（dangling pref）；policy §2.1 稱「打字習慣僅本機」但 `CloudSyncManager` 會同步整個 `user_learning_data`（含 TypingHabit / StyleProfile 等學習資料）
- 🟠 **P1 High**：AI consent 無任何 per-provider 同意 UI，使用者按下 AI 功能即發送內容到 OpenAI/Anthropic/Gemini/Groq/SnowRealm proxy；policy §2.2 只列 OpenAI / Anthropic，未提 Gemini / Groq / SnowRealm proxy
- 🟡 **P2 Medium**：`logout()` 只清本地 session token，未觸發 server-side data 清除；「可攜權」僅有詞庫 txt export，缺帳號層 data dump
- 🟢 **P3 Low**：`fraudReportConsent` 有正式 consent dialog 是目前唯一合格樣本，可作其他 consent 的 reference pattern

---

## B. 範圍與方法

### B.1 範圍

| 區 | 本輪盤點 | 本輪不盤點（另交 CCB） |
|---|---|---|
| AI consent / opt-out | 產品契約 + 設定面邊界 + wording | runtime / network / TLS |
| data export / delete | 使用者可見契約 + 資料責任界線 | server-side flow、storage |
| clipboard / userdict sync | 設定面同意與承諾邊界、UI 文案 | sync gateway runtime 實作細節 |

與 `鍵律` 的 `S13-0-CCB-NETWORK-FRAUD-SYNC-THREAT-AUDIT` 互補：他看 runtime / network / data flow threat；我看 user-facing contract、wording、consent UI。

### B.2 方法

1. 掃三個主題的 pref / section / manager / activity，盤出 state source
2. 對照 `privacy_policy.html` / `terms_of_service.html` 既有承諾
3. 逐條比對「政策承諾 → 實際可兌現？」
4. 切出 contract / wording / 禁止承諾 / consume 影響面

### B.3 不動範圍

- 不改 `app/src/main/**`
- 不改 `privacy_policy.html` / `terms_of_service.html`（屬後續 S13-3 實作 round）
- 不改 gate 檔
- 不偷開 `S13-1` ~ `S13-5`

---

## C. AI Consent / Opt-out 區

### C.1 State Source 盤點

| 項 | 檔 / 位置 | 狀態 |
|---|---|---|
| `aiEnabled` 總開關 | `ui/settings/YukiPreferences.kt:21` 預設 `true` | 存在 |
| 20+ 功能開關（`aiWriter`/`aiTranslate`/`aiCupid`/...） | `YukiPreferences.kt:22-55` | 存在，**都是功能啟用開關，不是 consent** |
| AI 功能啟用 gate | `claw/ClawClient.kt:62 isAvailable()` — 看 invite + key + login | 存在 |
| Provider 選擇 | `AIProviderManager.kt`，EncryptedPrefs | 存在 |
| API Key 輸入 UI | `AISettingsActivity.kt` | 存在 |
| **per-provider 資料去向告知** | — | **不存在** |
| **AI consent dialog（首次啟用）** | — | **不存在** |
| **AI opt-out（整組撤回）** | — | **不存在**（`aiEnabled = false` 只是不顯示功能，不等於撤回同意） |
| **政策 §2.2 涵蓋 AI provider 清單** | `privacy_policy.html:37` 只列 OpenAI / Anthropic | **缺 Gemini / Groq / SnowRealm proxy；GeminiNano 為本地推論，應自上傳清單排除** |
| 第三方 provider 隱私政策連結 | — | 不存在（`AISettingsActivity` 有「API 教學」但非隱私連結） |

### C.2 Policy vs 實作 Gap

| privacy_policy.html 條文 | 實作 | Gap |
|---|---|---|
| §2.2「AI 功能時輸入內容**可能**傳送至 OpenAI / Anthropic」 | 實際會傳到 6 種 provider | **缺 4 種 provider 揭露** |
| §5「第三方服務：OpenAI / Anthropic」 | 同上 | 同上 |
| §6「撤回同意權：隨時撤回先前給予的同意」 | 無 AI-specific opt-out | **政策允諾但實作無** |
| §9「國際資料傳輸」 | 無 per-call 提示 | 政策一次性告知，未逐次提示 |

### C.3 Contract 建議（僅切邊界，不進實作）

**AI consent 最小 contract：**

1. 首次啟用 AI 功能（任一）前，必須彈 consent dialog，至少告知：
   - 輸入內容會離開本機
   - 目前 selected provider（可變）
   - 各 provider 有獨立隱私政策
2. consent 一旦撤回，`ClawClient.isAvailable()` 應回 `false`
3. 切換 provider 時，若新 provider 未曾 consent，應重新 consent（或一次性「任何 provider 都同意」的 consent，需明寫）
4. consent 狀態必須持久化（`aiConsentVersion: Int` — 政策變更時版本+1，使用者需重新 consent）
5. opt-out UI 應與 `aiEnabled` 分離：`aiEnabled = false` 是「不要看到功能」；`aiConsentRevoked = true` 是「不要送出任何東西」

**與 S13-3 的銜接：** 本輪只產出 contract，實作由 S13-3-CCA 執行。

### C.4 禁止承諾清單（AI 區）

- ❌ 不得在 UI / policy 承諾「AI 功能只使用 OpenAI / Anthropic」— 現實有 6 種 provider，且使用者可切
- ❌ 不得承諾「AI 輸入不會被 provider 存儲」— 各 provider 政策不同，我們無法代言
- ❌ 不得承諾「AI 可完全離線」— 僅 `GEMINI_NANO` 為 on-device，其他都上雲
- ❌ 不得承諾「撤回同意後會刪除已傳送資料」— 已傳出的資料在第三方手上，我們不可代為刪除

---

## D. Data Export / Delete 區

### D.1 State Source 盤點

| 項 | 檔 / 位置 | 狀態 |
|---|---|---|
| 詞庫匯出 | `ui/settings/section/InputBehaviorSection.kt:61` → `SettingsActivity.kt:201` → SAF `Intent.ACTION_CREATE_DOCUMENT` | 存在（txt） |
| 詞庫匯入 | `InputBehaviorSection.kt:66` → `SettingsActivity.kt:398` | 存在 |
| 偏好 cloud snapshot | `YukiPreferences.kt:298 exportSettingsSnapshot()` | 存在（走 CloudSyncManager） |
| 主題 cloud snapshot | `YukiPreferences.kt:385 exportThemeSnapshot()` | 存在 |
| 學習資料 snapshot | `AdaptiveDictionary.exportSnapshot()` 等 7 組 | 存在 |
| **全帳號資料 export（user-initiated）** | — | **不存在** |
| **帳號刪除 / 資料清除** | — | **不存在** |
| `SnowRealmAuthClient.logout()` | `auth/SnowRealmAuthClient.kt:179` | **只清本地 token，不刪 server 資料** |
| 剪貼簿歷史刪除 | ClipboardPanelView 內有「清空」 | 存在（local-only） |

### D.2 Policy vs 實作 Gap

| privacy_policy.html 條文 | 實作 | Gap |
|---|---|---|
| §6「刪除權：要求刪除您的資料（設定 → 清除所有資料）」 | **「清除所有資料」選項不存在** | **P0：UI 路徑與政策不符** |
| §6「可攜權：以通用格式匯出您的資料」 | 僅詞庫 txt 匯出 | 缺：剪貼簿、習慣、學習資料、帳號資料 |
| §6「撤回同意權」 | 僅 `fraudReportConsent` 撤回有明確路徑 | AI、sync、其他 consent 無 |
| §7「兒童隱私：13 歲以下不收集，發現誤收集立即刪除」 | 無年齡驗證，誤收集後也無刪除機制 | 宣告 > 能力 |
| §10「政策變更：重大變更需要您重新同意」 | 無 policy version 機制 | 政策允諾但實作無 |

### D.3 Contract 建議

**Data Export / Delete 最小 contract：**

1. **export 能力分層**：
   - **Tier 1（現有）**：詞庫 txt — 已落地
   - **Tier 2（S13-4 最小）**：`Settings → 匯出我的資料`，產出 JSON，涵蓋 local-only 資料（偏好、剪貼簿、學習、習慣）
   - **Tier 3（後續 sprint）**：完整帳號 dump（含 server-side user_settings / user_dictionary / user_themes / user_learning_data）
2. **delete 能力分層**：
   - **Tier 1（現有）**：各 panel 的局部清除（剪貼簿清空、個別詞庫刪除）— 已落地
   - **Tier 2（S13-4 最小）**：`Settings → 清除所有本機資料`，刪 prefs + 本機 DB + snapshot
   - **Tier 3（後續 sprint）**：帳號刪除 — 觸發 server-side `DELETE FROM user_*` + logout；需 server 支援
3. **logout 邊界必須寫清**：
   - 目前 `logout()` = 只清 token，**雲端資料保留**
   - UI 應明寫「登出不會刪除雲端備份；要刪資料請走『刪除我的帳號』」
4. **child privacy fallback**：policy §7 若要保留，必須補 `反饋 email + 手動刪除流程`；否則應改寫成「我們不主動針對兒童蒐集，但無年齡驗證機制」

### D.4 禁止承諾清單（export / delete 區）

- ❌ 不得在 UI 或 policy 承諾「一鍵刪除所有資料包含雲端」直到 server-side delete API 落地
- ❌ 不得承諾「可攜權已完整實作」— 目前僅詞庫 txt
- ❌ 不得承諾「撤回同意 = 刪除已傳第三方資料」
- ❌ 不得承諾「13 歲以下不會收集到」— 無年齡驗證
- ❌ 不得承諾「政策變更會提示重新同意」— 無 policy version 機制
- ❌ logout 不得 UI 寫「登出會清除所有資料」— 目前只清 token

---

## E. Clipboard / UserDict Sync 區

### E.1 State Source 盤點

| 項 | 檔 / 位置 | 狀態 |
|---|---|---|
| `clipboardSyncEnabled` | `YukiPreferences.kt:155` | **Dangling pref — 無任何 consumer** |
| `ClipboardSettingsSection` UI 文案 | `section/ClipboardSettingsSection.kt:21` 「透過 Insight 同步剪貼簿（需登入）」 | **虛假功能承諾** |
| UserDict sync（實際） | `CloudSyncManager.uploadDictionary()` / `downloadDictionary()` → Supabase `user_dictionary` table | **存在** |
| Sync 開關 | `SyncSettingsSection` → `CloudSyncManager.setSyncEnabled()` | 存在（全量 sync 總開關） |
| Sync 範圍（實際） | `user_settings` / `user_dictionary` / `user_themes` / `user_learning_data` | 全量上傳 |
| Sync 範圍（UI 文案） | `SyncSettingsSection.kt:79` 「偏好設定、自訂詞庫、主題、自適應字典、個人詞庫、語句預測、整句預測、語氣風格、打字習慣、校正學習」 | ✅ 文案與實作大致一致 |
| `privacy_policy.html §2.1` | 「剪貼簿歷史：儲存於裝置本機」+「打字習慣：僅本機候選字排序」 | **與 CloudSyncManager 同步 `user_learning_data`（含 TypingHabit）衝突** |
| 撤回同步 = 刪雲端？ | — | **無 contract**，關掉開關只是不再上傳 |

### E.2 Policy vs 實作 Gap

| privacy_policy.html 條文 | 實作 | Gap |
|---|---|---|
| §2.1「剪貼簿歷史：儲存於裝置本機 SharedPreferences」 | `clipboardSyncEnabled` UI 宣稱可同步 | **矛盾（但實際無同步路徑，較輕）** |
| §2.1「打字習慣：僅用於本機候選字排序」 | `CloudSyncManager` 同步 `user_learning_data`（含 TypingHabit / Style / Adaptive / Phrase / Sentence / CorrectionLearn） | **P0 直接衝突** |
| §2.2 可能上傳的資料清單 | 缺「打字習慣、語氣風格、自適應字典、個人詞庫、語句預測、整句預測、校正學習」 | **政策未揭露即上傳** |
| §6「撤回同意權」 | sync 關閉 = 停止上傳，**雲端既存資料不動** | 無 contract |

### E.3 Contract 建議

**Clipboard Sync 最小 contract（建議路徑 A 或 B 擇一，由 `玄樞` 拍板）：**

- **路徑 A（移除）**：`S13-5` 落地時直接移除 `clipboardSyncEnabled` pref + `ClipboardSettingsSection` 該開關；UI 只留「本機加密 / 敏感偵測」
  - 優點：政策承諾 §2.1 不變
  - 缺點：失去未來 sync 能力
- **路徑 B（實作最小）**：`S13-5` 落地最小 clipboard sync：
  - 明確 contract：只同步「釘選」項，不同步全歷史
  - 首次啟用需 consent dialog（類 `fraudReportManager.showConsentDialog()`）
  - 政策 §2.1 補「已釘選的剪貼簿項目在啟用同步後會上傳」

**UserDict Sync contract 補強：**

1. sync 開關 UI 文案需補一句：「停用同步不會刪除雲端備份；如需刪除請見『資料管理』」（串到 D 區的 Tier 3 delete）
2. `privacy_policy.html §2.1` 需改寫，把 TypingHabit / Style / AdaptiveDict / PersonalDict / PhrasePredict / SentencePredict / CorrectionLearn 從「本機」移至「可能上傳（需啟用雲端同步）」清單
3. 首次啟用雲端同步應彈 consent dialog，明告：
   - 上傳範圍（列表）
   - 儲存位置（Supabase，需寫入 region）
   - 撤回方式（關開關停止上傳 + 走 Tier 3 delete 刪已上傳）

### E.4 禁止承諾清單（sync 區）

- ❌ **不得保留** `ClipboardSettingsSection` 的「透過 Insight 同步剪貼簿」文案直到 S13-5 落地實作或移除 pref
- ❌ 不得在 `privacy_policy.html` 維持「打字習慣僅本機」的舊條文，已被 `CloudSyncManager` 打破
- ❌ 不得宣稱「sync 內容全部加密」— 目前 Supabase 是欄位級（非端對端加密）
- ❌ 不得承諾「停用同步會自動刪除雲端資料」— 無此 flow
- ❌ 不得承諾「同步是 real-time」— 實際為 24h 排程 + 50 次 commit 觸發

---

## F. Privacy Wording 建議（整合版）

以下條列 `privacy_policy.html` 需修訂的條文，供 `S13-3`（AI consent）/ `S13-4`（data export/delete）/ `S13-5`（sync contract）分別拉進各自 round 的實作檔裡。**本輪不落地 HTML**，只交付需改寫的條文清單：

| 條文 | 現有 | 建議改為 | 屬 sprint 13 哪門 |
|---|---|---|---|
| §2.1 本機處理（打字習慣條） | 「詞頻統計僅用於本機候選字排序」 | 「打字習慣僅在本機候選排序使用；啟用雲端同步時會上傳至 Supabase」 | S13-5 |
| §2.1 本機處理（剪貼簿條） | 「儲存於裝置本機 SharedPreferences」 | 若採路徑 A：維持不變；路徑 B：補「啟用跨裝置同步時，已釘選項會上傳」 | S13-5 |
| §2.2 可能上傳清單 | 缺 Gemini / Groq / SnowRealm proxy；GeminiNano 為本地推論不應列入上傳清單 | 補完整雲端 provider 清單 + 各自政策連結，並把 GeminiNano 明確標為 on-device | S13-3 |
| §2.2 新增 | — | 「雲端同步（需登入）：偏好、自訂詞庫、主題、自適應字典、個人詞庫、語句預測、整句預測、語氣風格、打字習慣、校正學習」 | S13-5 |
| §6 刪除權 | 「設定 → 清除所有資料」 | 改為實際可行路徑；若 `S13-4` 只做本機，則寫「設定 → 清除本機資料」並移除雲端刪除宣告 | S13-4 |
| §6 可攜權 | 「以通用格式匯出您的資料」 | 明寫目前可匯出範圍（詞庫 / 本機 snapshot） | S13-4 |
| §7 兒童隱私 | 「13 歲以下不會故意收集」 | 補「無年齡驗證機制；若家長發現請 email privacy@snowrealm.pet」 | S13-3 |
| §10 政策變更 | 「重大變更需要您重新同意」 | 若無 policy version 機制，改為「政策變更將在下次啟動時告知，繼續使用視為同意」 | S13-3 |

---

## G. 禁止承諾清單（Hard Limits — 全 Sprint 13 適用）

**Sprint 13 期間，任何 UI、policy、wording 都不得作以下承諾，直到對應 sprint 有實際 round 落地：**

1. ❌ 「一鍵刪除所有資料包含雲端」（D 區，S13-4 前禁）
2. ❌ 「完整資料可攜權」（D 區，S13-4 前禁）
3. ❌ 「登出 = 刪除雲端資料」（D 區，恆禁，除非未來有 server-side delete flow）
4. ❌ 「AI 只走 OpenAI / Anthropic」（C 區，S13-3 改 policy 前禁）
5. ❌ 「AI 撤回同意 = 第三方刪已傳資料」（C 區，恆禁）
6. ❌ 「打字習慣僅本機」（E 區，已被 CloudSyncManager 打破，S13-5 改 policy 前禁）
7. ❌ 「跨裝置同步剪貼簿」（E 區，S13-5 路徑 B 實作或路徑 A 移除前禁）
8. ❌ 「13 歲以下不會收集到」（D 區，無年齡驗證前禁）
9. ❌ 「政策變更會提示重新同意」（D 區，無 policy version 前禁）
10. ❌ 「同步資料端對端加密」（E 區，現為欄位級加密）

---

## H. Consume 影響面

**若 `S13-3` / `S13-4` / `S13-5` 依本 audit contract 落地，對既有 UI / 使用者流程的影響：**

| 區 | 影響 | 影響等級 |
|---|---|---|
| AISettingsSection | 首次啟用 AI 功能需 consent dialog（新增一次對話） | 🟡 中 |
| SyncSettingsSection | 首次啟用雲端同步需 consent dialog | 🟡 中 |
| ClipboardSettingsSection | 路徑 A：「跨裝置同步」開關消失；路徑 B：需 consent dialog | 🟠 高（路徑 A 為破壞性移除） |
| SettingsActivity | 新增「資料管理」卡片（匯出 / 清除本機） | 🟡 中 |
| privacy_policy.html | 多處條文改寫 + 新增 §2.2 雲端同步清單 | 🟢 低（純文件） |
| LegalSettingsSection | 可能需新增「第三方 AI provider 政策連結」 | 🟢 低 |
| ClawPanelView / 各 AI 功能入口 | 若 consent 未給，需改走 consent fallback UI | 🟠 高（所有 AI 功能入口都要檢查 consent gate） |

---

## I. 不測區域（Not-tested / Out-of-scope）

**本輪 audit 不涵蓋，明確標為後續 round 責任：**

1. **AI provider 各自的隱私政策逐條分析** — 依賴第三方，本專案只能做「告知 + 連結」
2. **GDPR / PDPA / CCPA 法條級合規評估** — 需法律專業，非工程範圍
3. **Supabase region / cross-border transfer 實際路徑** — 由 `鍵律 CCB` 在 S13-0-CCB 盤；本輪只提出需補述要求
4. **`fraud_reports` RLS insert-only 技術實作** — 屬 `S13-2-CCB 鍵律`
5. **network security config / usesCleartextTraffic** — 屬 `S13-1-CCB 鍵律`
6. **兒童隱私年齡驗證的技術方案** — 超出 S13 範圍
7. **logout 後 server-side 實際 retention policy** — 需與 server 側對齊，本輪只標「UI 不得宣告已刪除」
8. **policy version / 重新同意 flow** — 屬後續 sprint

---

## J. 建議的 S13-1 ~ S13-5 落點（初步，由玄樞最終決定）

供 `衡鑑` cross-check 及 `玄樞` gate 參考，本輪只建議，不裁定：

| Round | 主要負擔（CCA 側） | 對應 audit 條目 |
|---|---|---|
| S13-1 Network Security | 補 policy §4 wording「HTTPS 強制」具體化 | F 表暫無，屬 CCB 主導 |
| S13-2 Fraud Report Privacy | policy wording + fraud_report_consent 流程補強 | （本 audit 未深入 fraud，屬 S13-2-CCA） |
| S13-3 AI Consent / Opt-out | C.3 + F 表 §2.2 / §5 / §6 / §7 / §10 改寫 | C 區全部 |
| S13-4 Data Export / Delete | D.3 Tier 1+Tier 2 + F 表 §6 改寫 | D 區全部 |
| S13-5 Clipboard / UserDict Sync | E.3 路徑 A 或 B 擇一 + F 表 §2.1 / §2.2 改寫 | E 區全部 |

---

## K. 已知風險

1. **R-1**：路徑 A（移除 `clipboardSyncEnabled`）屬破壞性變更，既有使用者若開過此開關會看到功能消失。緩解建議：release note 明寫「原跨裝置同步剪貼簿為未啟用功能，已下架」
2. **R-2**：consent dialog 一旦加入，會拉高首次使用成本；建議不要 per-skill 設 consent，以「整組 AI」為單位
3. **R-3**：policy §2.1「打字習慣僅本機」條文若要改，需與 UI 文案（`SyncSettingsSection.kt:79`）一致，避免「政策更誠實但 UI 仍舊承諾」反向矛盾
4. **R-4**：本 audit 依賴 `privacy_policy.html` 當前為 2026-04-11 版本；若 S13-3 / S13-4 / S13-5 並行改 policy，需一次 merge，不得各自 override
5. **R-5**：`logout()` 清 token 邊界 vs server data 邊界的對齊，需要 `UserSessionRepository` / server API 配合；本輪僅定 UI 不得宣告，實際 server 行為由後續 sprint 與 server 端共同定義
6. **R-6**：若 `玄樞` 採路徑 B（實作 clipboard sync），需啟動 Supabase schema 新表 `user_clipboard_pinned`，屬 infra 改動，需 `S13-5` 重新 scope

---

## L. 回交格式總結（雪鑰標準回交）

### 問題分類

| 類 | 項 |
|---|---|
| P0 政策 > 實作 | §6「清除所有資料」UI 不存在；§2.1「打字習慣僅本機」已被 CloudSync 打破；`clipboardSyncEnabled` 虛假開關 |
| P1 揭露不全 | privacy §2.2 缺 4 種 AI provider；缺 7 種 subsystem 同步揭露 |
| P2 權利名存實亡 | 可攜權僅詞庫 txt；撤回權僅 fraudReportConsent；刪除權無路徑 |
| P3 可接受現況 | `fraudReportConsent` 的 consent dialog 流程可作其他 consent 的 reference |

### State Source 盤點

詳見 C.1 / D.1 / E.1 三表。

### 最小 Contract

- AI：C.3（1-5 條）
- Export/Delete：D.3（Tier 1/2/3 分層）
- Sync：E.3（路徑 A 或 B + userdict 文案補強）

### Wording 改寫清單

詳見 F 表（9 條）。

### 禁止承諾

詳見 G 表（10 條 hard limit）。

### 已知風險

詳見 K（R-1 ~ R-6）。

### 驗證方式

- 本輪為 audit / contract，驗證 = 文件一致性檢查 + 後續 S13-3/4/5 回頭對照
- 衡鑑 cross-check 時建議抽查：
  - `grep -r "clipboardSyncEnabled"` 確認 dangling pref
  - `grep -r "user_learning_data"` 確認同步範圍與 policy 差距
  - 打開 `privacy_policy.html` 對照 §2 / §6 與 F 表差距
  - 打開 `AISettingsActivity.kt` 確認無 AI consent UI

---

## M. 提交鏈下一站

1. `雪鑰`（本輪）完成 `S13-0-CCA-LEGAL-PRIVACY-CONTRACT-AUDIT` → 本文件
2. `鍵律` 完成 `S13-0-CCB-NETWORK-FRAUD-SYNC-THREAT-AUDIT`（獨立並行）
3. 兩份收齊後由 `衡鑑` 做 `S13-0-CXA-TECHNICAL-CROSSCHECK`
4. 提交給 `玄樞` 做最終 gate
5. 只有 `玄樞` 判定 `READY`，`S13-1` ~ `S13-5` 才得以開門

**本輪屬純文件交付，未動 `app/src/main/**`，file scope 合規。**

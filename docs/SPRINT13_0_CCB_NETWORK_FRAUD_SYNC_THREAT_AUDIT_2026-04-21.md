# Sprint 13-0 CCB — Network / Fraud / Sync Threat Audit

日期：2026-04-21
提交者：`鍵律`（CCB）
任務 ID：`S13-0-CCB-NETWORK-FRAUD-SYNC-THREAT-AUDIT`
交付型：audit / runtime / service / data flow cut（**不改 `app/src/main/**`**）
提交對象：`衡鑑`（technical cross-check）→ `玄樞`（final gate）

---

## A. 盤點總覽

本 audit 只涵蓋 runtime / service / data flow 面向，不涉及法務 wording / consent 設定面（歸 `S13-0-CCA` 雪鑰）。

---

## B. 題一｜Network Security（對應 S13-1）

### B.1 現況事實

| 項 | 位置 | 狀態 |
|---|---|---|
| `usesCleartextTraffic` | `AndroidManifest.xml:41` | `false` ✓ |
| `networkSecurityConfig` | `AndroidManifest.xml:42` → `res/xml/network_security_config.xml` | 已套用 ✓ |
| `base-config` | `network_security_config.xml:4-8` | `cleartextTrafficPermitted="false"` + system trust anchors ✓ |
| `debug-overrides` | 同上 :11-15 | user trust anchor（debug build only）✓ |
| localhost 例外 | 同上 :18-21 | `localhost` / `127.0.0.1` 開放 cleartext（WiFi keyboard + Faster-Whisper 自架 server 需要） |
| OkHttp 建構入口 | `app/net/AppHttpClient.kt` | 單一 owner ✓；`OkHttpClient.Builder()` 直用點 = 0 |
| callsite 收斂 | 全專案 | 26 處 `AppHttpClient.newBuilder(...)`，未見繞過 |
| `http://` 字面值 | `WifiKeyboardServer.kt:117 getConnectionUrl()` | 本機 IP，落在 localhost 例外框架以外 → 詳見 B.2 |
| Insight 預設 base URL | `app/build.gradle:52` | `https://insight.snowrealm.pet` ✓ |

### B.2 Runtime / data flow 風險

| 風險 | 等級 | 說明 |
|---|---|---|
| `R-N1`：`WifiKeyboardServer.getConnectionUrl()` 回傳 `http://<LAN-IP>:8765` | 中 | manifest 的 cleartext 例外只寫 `localhost` 與 `127.0.0.1`；當 server 被瀏覽器以 LAN IP 連入時，server 側由 `ServerSocket` 自身處理不受 manifest 限制，但 app 內若有任何自身連回 LAN IP 的 HTTP client 會被 `NetworkSecurityPolicy` 拒絕。目前 app 無此 callsite，視為 observation |
| `R-N2`：`FraudOpenDataSync` 三道政府 / PhishTank / OpenPhish 全走 HTTPS | 低 | 實測 URL 全為 `https://`；github.com raw 走 TLS。無 cleartext 洩漏 |
| `R-N3`：`InsightApiClient` 允許 `path.startsWith("http")` 直通 | 低 | `InsightApiClient.kt:135` 若呼叫端傳入 `http://...` 絕對 URL，`NetworkSecurityPolicy` 會在 runtime 拒絕 → fail closed，但錯誤訊息不夠明確 |
| `R-N4`：`debug-overrides` 允許 user CA | 低 | 僅 debug build 生效；release 不受影響 |

### B.3 候選 file scope（僅供玄樞決策 S13-1）

minimal（若玄樞僅要求確認基線已達）：

- 無 `.kt` 改動，文件化現狀即可

若決定補強（可能的最小變更點，非自排）：

- `res/xml/network_security_config.xml` — 加上 `domain-config` 註解說明為何放行 localhost
- 無需改任何 `.kt`

### B.4 禁區

1. 不得放寬 `cleartextTrafficPermitted` 到非 localhost
2. 不得在 release 打開 user trust anchor
3. 不得繞過 `AppHttpClient` 自開 `OkHttpClient.Builder()`

---

## C. 題二｜Fraud Report Privacy（對應 S13-2）

### C.1 現況事實

| 項 | 位置 | 內容 |
|---|---|---|
| 上傳管道 | `security/FraudReportManager.kt:97` | `supabase.insert("fraud_reports", data)` |
| auth 模式 | 同上，未傳 `authToken` | 走 Supabase anon key → 依 RLS 放行 |
| 同意開關 | `:34 prefs.fraudReportConsent` | 首次 `showConsentDialog` 後儲存；`revokeConsent()` 可撤回 |
| 上傳欄位 | `:89-96` | `text_hash`（SHA-256 of `text.take(500)`）/ `fraud_type` / `matched_keywords`（filter `[` 開頭、取前 10）/ `is_fraud` / `rule_score` / `device_locale` |
| 不含欄位 | — | 無 `userId`、無裝置 ID、無時間戳、無原文 ✓ |

### C.2 Runtime / data flow 風險

| 風險 | 等級 | 說明 |
|---|---|---|
| `R-F1`：client 端無 rate limit | 中 | 單一裝置可無限連打 insert；Supabase RLS 只能擋 `SELECT/UPDATE/DELETE`，對 `INSERT` 的速率需靠後端 Edge Function 或 `fraud_reports` 表的 trigger |
| `R-F2`：`matched_keywords` 可能洩漏使用者文本特徵 | 中 | 雖已 `.filter { !it.startsWith("[") }.take(10)`，但若命中的關鍵字本身是使用者自創詞或敏感組合，會隨 hash 一起上雲；hash 無法還原文字，但 keyword set 有機會推回話題 |
| `R-F3`：`SHA-256(text.take(500))` 碰撞可被跨裝置比對 | 低 | 同一文本會產生同一 hash；若詐騙訊息是群發，Supabase 側可統計重複數量（這是本設計意圖），但對小眾自訂文本則有去匿名風險 |
| `R-F4`：`device_locale` 細粒度 | 低 | `zh-Hant-TW` 等含地區標籤；本身不識別個人，但與 hash 組合可縮小群體 |
| `R-F5`：RLS insert-only 是否真的落地 | 未驗證 | client 程式沒讀 `fraud_reports`，但 Supabase RLS policy 需在後端確認；本 audit 無法從 client codebase 證實。需 `S13-2` 實作階段補驗證步驟（或由玄樞指向後端 repo） |
| `R-F6`：`catch (_: Exception)` 吞錯 | 低 | `report()` 失敗回傳 `false`，但完全不區分「網路失敗 / 被 RLS 拒 / 欄位錯」。runtime 觀察性差 |

### C.3 候選 file scope（僅供玄樞決策 S13-2）

唯一實作點：

- `app/src/main/java/com/snowrealm/yukiboard/security/FraudReportManager.kt`

可能併同的只讀確認：

- Supabase 後端 schema / RLS（不在 client repo 內，需玄樞指向）

### C.4 禁區

1. 不得擴成全 fraud system 重構（gate 本輪限制已明文）
2. 不得在 client 側加 `userId` / `deviceId` 欄位
3. 不得把原文納入 payload（即使截斷）
4. 不得降級 hash 演算法
5. 不得在本 round 動 `FraudAIAnalyzer` / `FraudModelManager` / `FraudRuleSync` 等鄰近檔案

---

## D. 題三｜Sync Contract（對應 S13-5，並影響 S13-4）

### D.1 現況事實

| 項 | 檔案 | 狀態 |
|---|---|---|
| 雲端同步主控 | `data/CloudSyncManager.kt` | 458 行；覆蓋 `user_settings` / `user_dictionary` / `user_themes` / `user_learning_data` 四表 |
| auth 交換 | `:119-151 getSupabaseToken()` | 走 `InsightApiClient.post("/api/auth/supabase-token")` → 快取 1h |
| UserDictionary sync | `:236-262`（上）/ `:322-342`（下） | 真契約存在 ✓ |
| 7 種智慧學習資料 sync | `:369-419` | 真契約存在 ✓（各 subsystem `exportSnapshot()` / `importSnapshot()`） |
| Clipboard sync | 全專案查無實作 | `prefs.clipboardSyncEnabled` 只是 UI 開關，沒有任何 `upload/download` 路徑 |
| Clipboard 實際儲存 | `ui/panel/ClipboardPanelView.kt:40` | `getSharedPreferences("yukiboard_clipboard", MODE_PRIVATE)` — 純本地 |
| Clipboard sync 開關 | `YukiPreferences.kt:155 clipboardSyncEnabled` | 設定頁文案「透過 Insight 同步剪貼簿（需登入）」→ 與實作不符 |

### D.2 Runtime / data flow 風險

| 風險 | 等級 | 說明 |
|---|---|---|
| `R-S1`：`clipboardSyncEnabled` 為虛假承諾 | 高（合規面） | 設定頁宣稱「透過 Insight 同步」但 runtime 無任何 sync path；使用者以為剪貼簿會跨裝置同步，實際未發生。屬 gate 文件第 5 項「不把 local-only 行為包裝成已支援 sync」已明確觸犯 |
| `R-S2`：UserDictionary 刪除單向 | 中 | `downloadDictionary()` 僅補本地不存在的詞，不處理刪除回放；使用者在 A 裝置刪詞，B 裝置雲端下載時不會刪掉 A 上已刪的詞。`S13-4` data export/delete contract 必須面對此語意缺口 |
| `R-S3`：UserDictionary 逐筆 upsert，O(N) 網路成本 | 低 | `:242-258` 逐筆 Supabase 請求；詞量大時會慢，但不影響正確性 |
| `R-S4`：`onSessionEnd` 50 次 commit 觸發 | 低 | 閾值寫死；本 round 不改 |
| `R-S5`：`syncOnLogin()` 是 download 單向 | 中 | 登入後只下載，不會先上傳本地（避免覆蓋雲端）— 語意合理但需寫入 contract 明文 |
| `R-S6`：`smartInputApplier` null-fallback 在冷啟動前同步 | 低 | `CloudSyncManager.kt:405-416` 已處理；S9-C 已驗證 |
| `R-S7`：clipboard 若未來真做 sync，原文 / 加密內容 / 敏感偵測結果的跨裝置責任邊界未定 | 高（合規面） | 本 audit 建議 `S13-5` 明文寫「本版不支援 clipboard cloud sync」作為契約，先下架虛假承諾，真正 sync 功能拉到未來 sprint |

### D.3 候選 file scope（僅供玄樞決策 S13-5）

不做雲同步本體（gate 明文）前提下，最小收斂範圍：

| 檔案 | 可能動作 |
|---|---|
| `ui/settings/section/ClipboardSettingsSection.kt:21` | 開關 UI 文案改為「（本版未支援）」灰化，或直接移除 — 歸雪鑰 `S13-5-CCA` |
| `ui/settings/YukiPreferences.kt:155 clipboardSyncEnabled` | 保留 pref 鍵但不再宣稱可用；或改為 `clipboardSyncPlanned` 類 placeholder — 跨邊界，需雪鑰 + 鍵律協議 |
| `ui/settings/SettingsStrings.kt` | 文案同步修正 — 歸雪鑰 |
| `data/CloudSyncManager.kt` | 本 round 不動；保留 `user_settings` / `user_dictionary` / `user_themes` / `user_learning_data` 四表契約 |
| `ui/panel/ClipboardPanelView.kt:40` | 本 round 不動；維持純本地儲存 |

鍵律本人 `S13-5-CCB` 實作契約內容建議（僅文件與 README 性質，不改 runtime）：

- 新檔案候選：`docs/SYNC_CONTRACT.md`（或加到現有 handoff 目錄）
- 明列 4 項已支援 sync、1 項不支援、刪除回放缺口、50/24h 觸發條件

### D.4 禁區

1. 不做 clipboard cloud sync 本體（gate 第 4 項明文）
2. 不動 `CloudSyncManager` 四表上下行邏輯
3. 不改 `smartInputApplier` 注入鏈
4. 不動 `SupabaseClient.getSupabaseToken()` token 交換路徑
5. 不碰 Insight 後端契約（Insight repo 獨立）

---

## E. 題四｜data export / delete runtime flow（對應 S13-4-CCB）

本題只做 runtime / storage / service 面向的最小 flow note，實際 contract 面由雪鑰 `S13-4-CCA` 主導。

### E.1 現況事實

YukiBoard 使用者資料儲存位置：

| 類別 | 儲存位置 | 刪除路徑 |
|---|---|---|
| 偏好設定 | `YukiPreferences` 多 SharedPreferences | 目前無批次刪除 API |
| 自訂詞庫 | `UserDictionary` SharedPreferences + `user_dictionary` 雲表 | 本地有 `removeManual`；雲端只有 `deleted=true` soft delete（無 hard delete） |
| 主題設定 | `YukiPreferences` 主題欄位 + `user_themes` 雲表 | 無專用清除 |
| 7 種學習資料 | 各 subsystem SharedPreferences + `user_learning_data` 雲表 | 無專用清除 |
| 剪貼簿 | `yukiboard_clipboard` SharedPreferences | 面板內可逐筆刪；有「清除全部未釘選」操作，無統一 app-level 清空 API |
| `fraud_reports` | 僅雲端 Supabase | client 完全無法刪除（insert-only 假設） |
| clipboard history | 純本地 | 可逐筆刪 |
| VRM 快取 / Lottie / 下載主題包 | 檔案系統 | 無統一清除 |
| audio / logs / tmp | cache dir | OS 管理 |

### E.2 Runtime 契約缺口

- `G-D1`：沒有 app 層的「刪除所有本地資料」入口（需走 Android 系統清除 app data）
- `G-D2`：沒有「登出同時刪除本機敏感資料」路徑（目前登出只清 session token）
- `G-D3`：雲端 `user_*` 表若要 delete，需 Insight / Supabase 後端提供 endpoint（client 不能直接 `DELETE`，否則違反最小權限）
- `G-D4`：`fraud_reports` 的匿名 hash 無法事後還原為「該使用者的」資料 → 技術上無法按使用者刪除（這是匿名設計的必然後果，需寫入契約明文）

### E.3 禁區

1. 本 round 只寫 flow note，不做真刪除流程（gate 第 5 項明文）
2. 不擴到 VRM 快取 / Lottie 清除（非 Sprint 13 範圍）
3. 不動登出 session 清除鏈（已在 S11 收斂）

---

## F. 風險分級總覽

| ID | 風險 | 題 | 等級 | 建議 round |
|---|---|---|---|---|
| `R-S1` | `clipboardSyncEnabled` 虛假承諾 | Sync | 高（合規） | `S13-5` 必處理 |
| `R-S7` | 未來 clipboard sync 責任邊界 | Sync | 高（合規） | `S13-5` 契約明文不支援 |
| `R-F1` | `fraud_reports` client 無 rate limit | Fraud | 中 | `S13-2` 或後端 round |
| `R-F2` | `matched_keywords` 洩漏特徵 | Fraud | 中 | `S13-2` 補 take 縮量 / 白名單 |
| `R-F5` | RLS insert-only 客端無法驗證 | Fraud | 中 | `S13-2` 驗證步驟由玄樞指向後端 |
| `R-S2` | UserDictionary 刪除不回放 | Sync | 中 | `S13-4` 契約明文缺口 |
| `R-S5` | `syncOnLogin` 單向下載 | Sync | 中 | `S13-5` 契約明文 |
| `R-N1` | LAN IP 可能走 cleartext | Network | 中 | 無 app 內 callsite；`S13-1` 只需文件化 |
| `G-D1~G-D4` | export / delete 契約缺口 | Export/Delete | 中 | `S13-4` 契約主導 |
| `R-F3 / R-F4 / R-F6` | hash 碰撞 / locale / 吞錯 | Fraud | 低 | `S13-2` 可順手，非必要 |
| `R-N2 / R-N3 / R-N4` | fraud 政府源 / Insight 絕對 URL / debug CA | Network | 低 | 觀察即可 |

---

## G. 候選 round 切法（僅供玄樞決策，不自排）

基於上述盤點，後續五道 round 的可能切法（玄樞可否決 / 重排）：

1. `S13-1`（Network Security Baseline）：現狀已達基線，玄樞可裁定僅補文件（manifest 註解 + `network_security_config` 註解），不必動 `.kt`
2. `S13-2`（Fraud Report Privacy）：focal 檔案 `FraudReportManager.kt`，主要處理 `R-F1` rate limit 與 `R-F2` keyword 縮量；RLS 驗證步驟由玄樞指向後端 repo
3. `S13-3`（AI Consent / Opt-out）：整題歸雪鑰，本 audit 不預判 scope
4. `S13-4`（Data Export / Delete Contract）：主契約歸雪鑰 `CCA`；本 `CCB` flow note 主題為 `G-D1~G-D4` 四缺口的 runtime / storage 盤點
5. `S13-5`（Clipboard / UserDict Sync Contract）：最高合規優先；本 `CCB` 契約文件應明列「本版不支援 clipboard cloud sync」、列出刪除不回放缺口；雪鑰 `CCA` 同步修設定文案

---

## H. 本輪未觸碰（守分工）

- 法務文字 / wording → 雪鑰 `S13-0-CCA`
- AI consent UI → 雪鑰 `S13-3`
- 資料責任界線的 user-facing 宣告 → 雪鑰 `S13-4-CCA`
- 設定面文案修正 → 雪鑰 `S13-5-CCA`
- 所有 `.kt` 實作 → 本輪 audit-only，依 gate 第 1 項限制
- 後續 round 順序與是否開門 → `玄樞` 決策

---

## I. 提交鏈下一步

本份 `S13-0-CCB` 交接：

1. 待雪鑰完成 `S13-0-CCA-LEGAL-PRIVACY-CONTRACT-AUDIT`
2. 兩份收齊 → `衡鑑` `S13-0-CXA-TECHNICAL-CROSSCHECK`
3. → `玄樞` final gate → `READY` 方可開 `S13-1`

---

## J. 玄樞審核需求

- 無 `.kt` 改動，本輪純文字 audit
- 未越界：未動雪鑰 `CCA` scope、未改任何 `app/src/main/**`、未自排後續 round 順序
- 已標高優先風險：`R-S1`（`clipboardSyncEnabled` 虛假承諾）為 Sprint 13 最具合規急迫性項目
- 狀態建議：`PENDING` / 等待 `S13-0-CCA` 收齊後併交衡鑑

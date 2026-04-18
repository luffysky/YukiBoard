# Sprint 12-1 CCA — Newebpay Contract Plan

日期：2026-04-19
提交者：`雪鑰`（CCA）
任務 ID：`S12-1-CCA-NEWEBPAY-CONTRACT-PLAN`
狀態：`READY_FOR_REVIEW`（鍵律 → 雪鑰合併 → 玄樞）
交付型：contract / plan / wording（**不改 `.kt`**）

---

## A. Executive Summary

**關鍵事實**：`NewebpayClient.kt` 在 `app/src/main/**` **無 caller**，實際 payment 路徑是 `ZCoinWalletActivity` → Chrome Custom Tab → `https://insight.snowrealm.pet/insight-admin/{tenantId}/zcoin`，**整套簽章、NotifyURL、Supabase 餘額更新皆在 Insight 後端完成**。APK 內 `NEWEBPAY_HASH_KEY` / `NEWEBPAY_HASH_IV` 純粹是歷史包袱，無功能依賴。

**S12-1 contract 結論**：**確認既有 Custom Tab 跳板即 server-side signing 的 de facto 實踐**，S12-2 的工作是把死檔 + 死 BuildConfig 砍掉，並把 YukiBoard 端的 payment lifecycle（回到 app 後 refresh balance）顯式化。

**兩條選項**：
- **選項 A（推薦）— 僅刪除路徑**：S12-2 刪 `NewebpayClient.kt` + 刪 `NEWEBPAY_MERCHANT_ID/HASH_KEY/HASH_IV` BuildConfig，保留 Custom Tab 跳板為唯一 payment 通道
- **選項 B — 刪除 + 未來 in-app WebView 契約**：同選項 A，並補 Insight `POST /zcoin/topup/sign` API 草案，留給未來若要做 app 內嵌 WebView 體驗時使用

本文件以 **選項 A 為建議** + **選項 B 作為 forward-compatible 草案保留**，最終由玄樞（或董事長）拍板。

---

## B. 現況 payment 架構圖

```
┌─────────────────────────────────────────────────────────────┐
│  YukiBoard Android (client)                                  │
│                                                              │
│  設定頁 → ZCoinWalletActivity                                 │
│       ↓ (onCreate)                                           │
│  baseUrl = BuildConfig.INSIGHT_API_BASE_URL                  │
│  tenantId = SnowRealmAuthClient.currentUser?.tenantId        │
│  url = "$baseUrl/insight-admin/$tenantId/zcoin"              │
│       ↓                                                      │
│  CustomTabsIntent.launchUrl(url) → finish()                  │
│                                                              │
│  [client 不持有 Newebpay secret，不做簽章]                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Insight Engine (server)                                     │
│                                                              │
│  /insight-admin/{tenantId}/zcoin                             │
│    ├─ 顯示方案 / 餘額                                         │
│    ├─ 使用者選方案 → POST /zcoin/topup                        │
│    ├─ 後端持 HashKey/HashIV → AES sign → SHA256 hash         │
│    └─ 302 redirect → ccore.newebpay.com/MPG/mpg_gateway      │
│                                                              │
│  /zcoin/notify (NotifyURL)                                   │
│    ├─ 驗簽 → update Supabase zcoin_wallets.balance           │
│    └─ 寫 zcoin_transactions (type=TOP_UP)                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Supabase                                                    │
│  - zcoin_wallets (user_id, balance, updated_at)              │
│  - zcoin_transactions                                        │
└─────────────────────────────────────────────────────────────┘
                          ↑
┌─────────────────────────────────────────────────────────────┐
│  YukiBoard Android (client, 回到 app 後)                      │
│  ZCoinApi.fetchBalance(userId, authToken)                    │
│    ↓                                                         │
│  ZCoinManager.syncBalance(serverBalance)                     │
│    ↓                                                         │
│  UI refresh                                                  │
└─────────────────────────────────────────────────────────────┘
```

**key takeaway**：Newebpay 簽章從未真的在 YukiBoard 端跑過（`NewebpayClient.buildPaymentParams` 沒有呼叫點）。`NEWEBPAY_HASH_KEY/IV` 是「配置打進 APK 但從未被程式碼讀取去動真的付款」— 泄漏後攻擊者最多拿到 hash key 去另起爐灶偽造商店頁，但無法攔截 YukiBoard 自己的付款流程（因為 YukiBoard 本身不發起 payment）。

---

## C. Insight API Contract（草案 / 選項 B 保留）

**前提**：Insight 後端已有 `/insight-admin/{tenantId}/zcoin` HTML 頁面處理完整流程。以下 API 是**未來若 YukiBoard 要改走 in-app WebView** 時的契約；**S12-1 選項 A 下不需要這些 API**。

### C.1 `POST /zcoin/topup/sign`

簽章端點，client 送金額 / orderId / email，server 回簽好的 Newebpay POST 參數。

```http
POST /zcoin/topup/sign HTTP/1.1
Host: insight.snowrealm.pet
Authorization: Bearer {InsightJWT}
X-Platform-Key: {PLATFORM_API_KEY}
Content-Type: application/json

{
  "amount": 100,
  "orderId": "ZC-20260419-abc123",
  "email": "user@example.com"
}
```

Response 200：
```json
{
  "merchantId": "MSxxxxxx",
  "tradeInfo": "<AES_encrypted_hex>",
  "tradeSha":  "<SHA256_hash>",
  "version":   "2.0",
  "postUrl":   "https://ccore.newebpay.com/MPG/mpg_gateway"
}
```

Response 4xx（統一錯誤包裝，對齊 `InsightApiClient.ApiResult`）：
- `400 INVALID_AMOUNT` — 金額不在允許範圍
- `401 UNAUTHORIZED` — token 失效
- `403 TENANT_DISABLED` — tenant 儲值被停用
- `409 DUPLICATE_ORDER` — orderId 已存在
- `503 PAYMENT_PROVIDER_DOWN` — Newebpay 不可用

### C.2 `GET /zcoin/balance`（既有，僅記錄契約）

目前走 Supabase 直接 query。若未來改走 Insight 代理（解除對 Supabase anon key 的直接依賴），契約：

```http
GET /zcoin/balance HTTP/1.1
Authorization: Bearer {InsightJWT}
X-Platform-Key: {PLATFORM_API_KEY}
```

Response：
```json
{ "balance": 1200, "updatedAt": "2026-04-19T12:34:56Z" }
```

### C.3 `POST /zcoin/orders/{orderId}/status`（可選）

給 client 回到 app 後主動查詢訂單狀態（因 NotifyURL 有延遲）：

```json
{ "orderId": "ZC-...", "status": "SUCCESS|PENDING|FAILED|CANCELLED", "zCoinsAdded": 1000 }
```

### C.4 與 `InsightApiClient` 既有介面對齊

所有新 endpoint 都走 `InsightApiClient.post(path, body)` / `.get(path)`，不繞過：

- `Authorization: Bearer` 由 `UserSessionRepository` 自動附上
- `X-Platform-Key` 由 `buildRequest()` 自動附上
- 401 自動 refresh + retry 一次
- 錯誤統一走 `ApiResult.Error(code, message)`

**嚴禁**新建 OkHttpClient / 自組 Authorization header（違反 Sprint 7-3 契約，`InsightApiClient.kt:23-25` 明載）。

---

## D. 使用者流程（選項 A，即現況收斂）

### D.1 Golden Path

1. 使用者在 YukiBoard 設定 / 錢包面板按「儲值 Z幣」
2. `ZCoinWalletActivity.onCreate` → 組 URL → `CustomTabsIntent.launchUrl()` → `finish()`（自身不留）
3. Custom Tab 開啟 Insight 錢包頁
4. 使用者在 Insight 頁選方案 / 填卡 → Newebpay 頁 → 付款完成
5. Newebpay 302 回 Insight return URL → 顯示成功頁 → 使用者關閉 Custom Tab
6. 回到 YukiBoard（原 IME / Launcher）
7. **下一次開啟錢包 / 商城 / 消費時**，`ZCoinManager.syncBalance(ZCoinApi.fetchBalance(...))` 從 Supabase 拉最新餘額

### D.2 Settings UX（S12-2 要做的小事）

現況 `ZCoinWalletActivity` 的 `onCreate` → `finish()` 模型在 Custom Tab 關閉後沒有「回來刷新」的 hook。選項 A 建議補：

**Option D.2.a（最小改動）**：在「商城」/「錢包入口」等會用到 Z 幣的地方，進入該畫面時無條件拉 `ZCoinApi.fetchBalance` 更新 `ZCoinManager.syncBalance`。
- 優點：不動 `ZCoinWalletActivity` lifecycle
- 缺點：使用者儲值完立刻查看某頁的話，若該頁沒觸發 fetch 就看不到新餘額

**Option D.2.b**：讓 `ZCoinWalletActivity` 不 `finish()`，改用 `onResume` 偵測 Custom Tab 關閉後 fetch balance。
- 優點：即時看到新餘額
- 缺點：需處理 Custom Tab 與 Activity 的 lifecycle 互動，複雜度上升；違反現有「跳板式」設計

**建議**：D.2.a（最小改動），S12-2 CCB 實作時在 `ZCoinSpendUseCase` / 錢包入口一行 `refresh()` 即可。

### D.3 設定面 wording（S12-2 同時落地）

錢包 / 商城頁的狀態訊息：

| 狀態 | 文案 | 對應邏輯 |
|---|---|---|
| 未登入 | 「登入後才能使用 Z 幣儲值」 | `SnowRealmAuthClient.currentUser == null` |
| 登入但 tenantId 空 | 「帳號同步中，請稍候再試」 | tenantId.isBlank() |
| 網路失敗 | 「暫時無法取得餘額，顯示上次的結果」 | `ZCoinApi.fetchBalance` 回 null |
| 儲值中 | 「儲值頁正在開啟⋯」 | Custom Tab 跳轉前 0.5s |
| 儲值完成（延遲）| 「訂單正在處理，新餘額約 1 分鐘內更新」 | 返回 app 後 `fetchBalance` 尚未包含最新 tx |

---

## E. 錯誤處理與文案

### E.1 各錯誤情境 → 使用者可見文案

| 情境 | trigger | 使用者文案 | 技術動作 |
|---|---|---|---|
| Custom Tab 無法開啟（no browser） | `CustomTabsIntent.launchUrl` throws | 「請先安裝 Chrome 或預設瀏覽器」 | catch `ActivityNotFoundException`，不 finish() |
| Insight URL 不可達 | Custom Tab 打開後 DNS/網路失敗 | 瀏覽器自身顯示 | — |
| 使用者在 Newebpay 取消 | 302 回 Insight → Insight return page 顯示「已取消」 | Insight 頁自理 | — |
| 付款成功 / NotifyURL 尚未處理 | 使用者先回 app 查餘額 | 「訂單處理中（通常 1 分鐘內完成）」 | `fetchBalance` 回舊餘額 → UI 顯示這條 toast |
| Insight 驗簽失敗（provider 回傳異常） | Insight NotifyURL 驗簽 mismatch | Insight 頁顯示「交易異常，請洽客服」 | Insight 側；client 不直接看到 |
| Supabase fetch 失敗 | `fetchBalance` catch exception | 「暫時無法取得餘額」 + 顯示快取值 | `ZCoinManager.balance` fallback |

### E.2 未來選項 B 啟用時的 client 錯誤對應

若將來改 in-app WebView + `POST /zcoin/topup/sign`：

| ApiResult | 使用者文案 |
|---|---|
| `Error(400, "INVALID_AMOUNT")` | 「金額超出允許範圍」 |
| `Error(409, "DUPLICATE_ORDER")` | 「訂單已存在，請稍後重試」 |
| `Error(503, "PAYMENT_PROVIDER_DOWN")` | 「藍新金流暫時無法使用」 |
| `Unauthorized` | 「請重新登入」（InsightApiClient 自動處理 refresh，失敗才到此） |
| `NetworkError` | 「網路異常，請確認連線後再試」 |

---

## F. Release Contract

### F.1 BuildConfig 變動（S12-2 要做的事）

**移除**（選項 A 採納）：
```diff
- buildConfigField "String", "NEWEBPAY_MERCHANT_ID", "\"..."
- buildConfigField "String", "NEWEBPAY_HASH_KEY", "\"..."
- buildConfigField "String", "NEWEBPAY_HASH_IV", "\"..."
```

**保留**：
- `INSIGHT_API_BASE_URL`（公開 URL）
- `PLATFORM_API_KEY`（Insight identity claim，非 secret 本質）
- `YUKIBOARD_SUPABASE_URL/ANON_KEY`（anon key by-design）

### F.2 `.env` 檔案變動

`.env.example`（若存在 / 新建）刪除 `NEWEBPAY_*` 三行 + 對應 `CLAUDE.md` API Key 表移除三條。

### F.3 檔案移除 / 標記

**選項 A**：直接刪除 `app/src/main/java/com/snowrealm/yukiboard/currency/NewebpayClient.kt`（無 caller 確認完畢）。

**選項 B（若採納）**：保留檔名但整類重寫成 thin wrapper：
```kotlin
class NewebpayClient(private val insightApi: InsightApiClient) {
    suspend fun sign(amount: Int, orderId: String, email: String): ApiResult =
        insightApi.post("/zcoin/topup/sign", buildJsonObject { ... })
    // 無 HashKey / HashIV / aesEncrypt / sha256
}
```

### F.4 CI Gate（S12-3 才實作；此處只列契約）

Release APK 驗證：
- `BuildConfig.NEWEBPAY_MERCHANT_ID` 不存在（S12-2 已移）→ 若存在 → FAIL
- `BuildConfig.NEWEBPAY_HASH_KEY` 不存在 → FAIL
- `BuildConfig.NEWEBPAY_HASH_IV` 不存在 → FAIL

S12-3 統合與其他 key 的 release policy CI gate 一起做。

---

## G. Server / Client 分工

### G.1 Server 端（Insight）責任

| 項 | 責任歸屬 |
|---|---|
| `HashKey` / `HashIV` 保管 | **Insight 後端 env / secrets manager** |
| `TradeInfo` AES 加密 | Insight `/insight-admin/{tenantId}/zcoin` route handler |
| `TradeSha` SHA256 | 同上 |
| Newebpay POST 表單組裝 | 同上 |
| NotifyURL / ReturnURL 端點 | Insight `/zcoin/notify` / `/zcoin/return` |
| 驗簽 | Insight NotifyURL handler |
| 更新 Supabase `zcoin_wallets` | Insight service role（非 anon key） |
| 寫 `zcoin_transactions` | 同上 |
| 退款 / 爭議處理 | Insight admin dashboard |
| `/zcoin/topup/sign` API（若選項 B） | Insight |
| `/zcoin/balance` API（若未來收斂） | Insight |

### G.2 Client 端（YukiBoard）責任

| 項 | 責任歸屬 |
|---|---|
| 顯示 Z 幣餘額 | `ZCoinManager.balance` 讀 + `ZCoinApi.fetchBalance` 同步 |
| 跳 Custom Tab | `ZCoinWalletActivity` |
| `recordSpend` RPC | `ZCoinApi.recordSpend` → Supabase `zcoin_deduct` |
| 儲值後 refresh 餘額 | 錢包入口 `fetchBalance` |
| 離線快取 | `ZCoinManager` EncryptedSharedPreferences |
| 交易紀錄顯示 | `ZCoinApi.fetchTransactions` |
| 本地 `topUp()` 方法 | **Deprecated / 刪除**（Supabase 由 Insight 寫入） |

**S12-2 CCB 要處理**：`ZCoinManager.topUp(amount, orderId)` 目前是 public API 但無 caller（grep 確認）。建議 S12-2 一併刪除，否則未來若誤呼叫會在本地無中生有餘額 → 跟 Supabase drift。

---

## H. D.1-1 / D.1-2 明確方案（回答 gate verdict §D.1）

### H.1 D.1-1 — Newebpay server 端由誰做？

**方案**：**Insight 後端已完成**（證據：`ZCoinWalletActivity` 已走 Custom Tab 到 `/insight-admin/{tenantId}/zcoin`，該路徑若後端未實作，儲值功能現在就是壞的；專案標為 `ACTIVE`，表示後端已到位）。

**S12-1 不需要新 server work**；僅需要在 S12-2 之前與 Insight 團隊**確認一次**：
1. `/insight-admin/{tenantId}/zcoin` 是否含完整 Newebpay sign + redirect 流程？（預期：是）
2. NotifyURL 端點與驗簽是否在 Insight 側？（預期：是）
3. Supabase `zcoin_wallets` 的 TOP_UP 寫入是否在 Insight service role 中完成？（預期：是）

**若三項皆為是** → S12-2 即可放心刪 `NEWEBPAY_*` BuildConfig + 死檔
**若任一為否** → 該項由 Insight sprint 補做，YukiBoard S12-2 等待 Insight ready

**責任**：確認動作由董事長或 `玄樞` 透過 Insight gate 對接；YukiBoard sprint 不主動碰 Insight 後端實作。

### H.2 D.1-2 — Release build 是否立即從 build.gradle 剝除 payment key 預設值？

**方案**：**是，S12-2 立即剝除**，因：
1. `NewebpayClient` 是死檔（grep `app/src/main` 無 caller），預設空值不影響功能
2. Custom Tab 路徑不讀 `NEWEBPAY_*` BuildConfig
3. 預設空值的缺點（開發者本地 build 要有 `.env`）對 YukiBoard 不適用，因為**本地 build 本來就不會走 payment**
4. `.env` 已 gitignore，且本地 `.env` 有 `NEWEBPAY_*` 也無害（僅編進 debug APK）

**S12-2 實作**：
- 刪除 `app/build.gradle:62-65` 三行 `buildConfigField` 宣告
- 刪除 `app/src/main/java/com/snowrealm/yukiboard/currency/NewebpayClient.kt`
- 保留 `.env` 中 `NEWEBPAY_*` 註解（標 deprecated，Insight 側使用）— 或一併移除

---

## I. S12-2 File Scope 建議（給鍵律 runtime plan 對齊用）

選項 A 下 S12-2 實作檔案白名單：

```
app/build.gradle                                                    ← 刪 L62-65 三行
app/src/main/java/com/snowrealm/yukiboard/currency/NewebpayClient.kt ← 刪整檔
app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinManager.kt   ← 刪 topUp() 方法 + 加 syncBalance 測試 hook
CLAUDE.md                                                           ← 移除 API Key 表中 NEWEBPAY_* 三行
.env.example（若存在）                                               ← 移除 NEWEBPAY_* 三行
```

**禁區**：
- ❌ 不改 `ZCoinWalletActivity.kt`（Custom Tab 跳板本身已是 server-side pattern，不動）
- ❌ 不改 `ZCoinApi.kt`（Supabase RPC 現況正確）
- ❌ 不動 Insight 後端 / Supabase schema
- ❌ 不動 `InsightApiClient.kt`（gateway 契約已存在）
- ❌ 不擴成 D.2.b 的 Activity lifecycle 改造（scope creep；留 Sprint 13）

**CCB 獨立要做的 runtime 細節**（鍵律那份 plan 應涵蓋，雪鑰這裡不越代）：
- `ZCoinManager` 的 `EncryptedSharedPreferences` fallback 明文寫入 + `Log.w` stack 印出問題（CCB audit §D-3）
- `topUp()` 刪除後，其他既有 caller（若有）如何處理
- S12-2 CCA/CCB 交界：CCA 審 UX / 文案 / release contract 落實、CCB 實作 `.kt`

---

## J. 開放問題（等玄樞 / 董事長裁示）

1. **選項 A vs 選項 B**：S12-1 contract 鎖定選項 A（僅刪除）還是保留選項 B（forward-compatible API 契約）？
   - 建議：**選項 A**；選項 B 的 API 契約放本文件存檔，未來若要做 in-app WebView 再啟動
2. **H.1 Insight 側三項確認**：由誰去對 Insight 團隊確認三項 readiness？
   - 建議：董事長透過 Insight gate 或直接跟 Insight 團隊確認；YukiBoard S12-2 在確認回來前不刪 BuildConfig
3. **`.env` 中 `NEWEBPAY_*` 要不要一併刪**：刪了對本地開發者無影響（無 caller），但會破壞本地 `.env` 歷史
   - 建議：刪，並在 `CLAUDE.md` 標 2026-04-19 S12-2 移除
4. **`ZCoinManager.topUp()` 刪除後，未來若要做 reward 發放（不經過 Newebpay）是否要保留內部 API？**
   - 建議：保留 `reward(amount, description)` 方法專做 REWARD type；徹底刪 `topUp()`，因 TOP_UP 本質就是要經過 Supabase（由 Insight 寫入）
5. **CCA vs CCB 的 `ZCoinManager` 處理邊界**：
   - CCA 本文件建議：S12-2 一併刪 `topUp()` 公開 API
   - CCB audit §D-3 建議：處理 `EncryptedSharedPreferences` fallback 風險
   - 兩者同一檔，S12-2 由 CCB 實作 → CCA 審。**建議在本輪與鍵律對齊誰動哪幾行**，避免 S11-5 式越代

---

## K. 驗證

- 無 `.kt` 改動，git status 預期僅新增 `docs/SPRINT12_1_CCA_NEWEBPAY_CONTRACT_PLAN_2026-04-19.md`
- 無需 build / test
- 關鍵事實驗證：
  - `grep -rn NewebpayClient app/src/main` → 僅 `NewebpayClient.kt` 本身，無 caller ✓
  - `grep -rn buildPaymentParams app/src/main` → 僅 `NewebpayClient.kt` 內定義 ✓
  - `ZCoinWalletActivity.kt` 全檔僅 `finish()` + Custom Tab，不含 Newebpay 呼叫 ✓

---

## L. 交付者自白

- Scope 嚴守 `S12-1-CCA-NEWEBPAY-CONTRACT-PLAN`：contract / wording / release / server-client 分工。
- **未越代 CCB**：`S12-1-CCB-NEWEBPAY-RUNTIME-PLAN` 的 Android client runtime path / 最小 file scope / EncryptedSharedPreferences fallback 處理等 CCB 主責內容**未在本文件實作**，僅在 §I / §J-5 提出「CCA/CCB 對齊建議」並留給鍵律 plan 主導。
- 依 `feedback_role_boundary.md` 規則：看到 CCB scope 的細節（如 `ZCoinManager.topUp` 刪除）只在本文件標「建議」不實作，具體實作方案由 CCB runtime plan 拍板。
- 依 `feedback_gate_no_self_schedule.md`：§J 5 個開放問題列候選，不自排先後；最終由董事長 / 玄樞拍板。

### 下一步

1. 鍵律 完成 `S12-1-CCB-NEWEBPAY-RUNTIME-PLAN`
2. 雪鑰 合併兩份 plan（gate verdict 格式）
3. 玄樞 判 READY → 解鎖 S12-2
4. S12-2 由 CCB 實作（依 CH-1 順序）+ CCA 審 UX / release contract 落實

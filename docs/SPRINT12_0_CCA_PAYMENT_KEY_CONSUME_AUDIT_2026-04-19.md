# Sprint 12-0 CCA — Payment / Key Consume & Exposure Audit

日期：2026-04-19
提交者：`雪鑰`（CCA）
任務 ID：`S12-0-CCA-PAYMENT-KEY-CONSUME-AUDIT`
交付型：audit / contract / risk cut（**不改 `.kt`**）

---

## A. Executive Summary

Sprint 12 主線是「高風險 client secret 移出 APK + 建立後端簽章與 release policy 邊界」。本輪盤點 YukiBoard 目前所有經 `BuildConfig` 注入的 secret / key 與其 consume 點，按三分類歸檔（client secret / release policy / settings consent / expose），並為 S12-1 ~ S12-5 各輪切出 file scope 與禁區。

**總體風險光譜：**

- 🔴 **P0 Critical**：藍新 `HashKey` / `HashIV` 寫死 APK（AES 簽章 client-side 完成），邀請碼 HMAC secret 預設值 `yukiboard_dev_2026` 仍可能編入 release
- 🟠 **P1 High**：AI keys（OpenAI / Anthropic）與 Giphy / OpenWeatherMap keys 若 release APK 編入非空值即全用戶共用，計費失控
- 🟡 **P2 Medium**：AIProviderManager 有 user-input 機制但兩條旁路（`ImeServiceInput`、`SpeechSettingsSection`）仍直讀 BuildConfig，繞過 manager
- 🟢 **P3 Low**：Supabase anon key（RLS 保護）+ `PLATFORM_API_KEY`（後端互信）— 暴露後需靠 server-side policy 擋，不算「client secret 本質問題」

---

## B. Key Inventory 全表

| ID | BuildConfig key | 來源 `.env` | 定義 | consume 點（file:line） | 分類 | 等級 |
|---|---|---|---|---|---|---|
| K-1 | `NEWEBPAY_MERCHANT_ID` | `.env` 或 CI env | `app/build.gradle:63` | `currency/NewebpayClient.kt:20` | client secret（低敏） | P1 |
| K-2 | `NEWEBPAY_HASH_KEY` | `.env` 或 CI env | `app/build.gradle:64` | `currency/NewebpayClient.kt:21`（AES key） | **client secret（致命）** | **P0** |
| K-3 | `NEWEBPAY_HASH_IV` | `.env` 或 CI env | `app/build.gradle:65` | `currency/NewebpayClient.kt:22`（AES IV） | **client secret（致命）** | **P0** |
| K-4 | `INVITE_HMAC_SECRET` | `.env` 或 CI env | `app/build.gradle:49`，**預設值 `yukiboard_dev_2026`** | `auth/InviteCodeManager.kt:146`（HMAC-SHA256 簽章） | **client secret（致命）** | **P0**（若 release 沿用預設或 `.env` 注入）|
| K-5 | `PLATFORM_API_KEY` | `.env` 或 CI env | `app/build.gradle:50` | `data/InsightApiClient.kt:39`、`sticker/InsightStickerApi.kt:28`、`claw/provider/SnowRealmProvider.kt:36` | release policy | P3 |
| K-6 | `INSIGHT_API_BASE_URL` | `.env` | `app/build.gradle:52`，預設 `https://insight.snowrealm.pet` | 多處 | 非 secret（公開 URL） | 資訊 |
| K-7 | `GIPHY_API_KEY` | `.env` 或 CI env | `app/build.gradle:53` | `gif/GiphyApiClient.kt:19` | client secret（低敏計費） | P1 |
| K-8 | `OPENAI_API_KEY` | `.env`（開發用） | `app/build.gradle:55` | `claw/provider/AIProviderManager.kt:63` + `service/ImeServiceInput.kt:255` + `ui/settings/section/SpeechSettingsSection.kt:266` | 混合（製版）client secret + user-BYO 設計 | P1 |
| K-9 | `ANTHROPIC_API_KEY` | `.env`（開發用） | `app/build.gradle:56` | `claw/provider/AIProviderManager.kt:62` | 混合（製版）client secret + user-BYO 設計 | P1 |
| K-10 | `OPENWEATHER_API_KEY` | `.env` 或 CI env | `app/build.gradle:58` | `weather/WeatherManager.kt:28` | client secret（低敏計費） | P1 |
| K-11 | `YUKIBOARD_SUPABASE_URL` | `.env` 或 CI env | `app/build.gradle:60` | `data/SupabaseClient.kt:25`、`app/ime/ImeFraudModelInit.kt:33`、`ui/settings/section/AISettingsSection.kt:113` | release policy（URL 本就公開） | P3 |
| K-12 | `YUKIBOARD_SUPABASE_ANON_KEY` | `.env` 或 CI env | `app/build.gradle:61` | `data/SupabaseClient.kt:26`、`app/ime/ImeFraudModelInit.kt:34`、`ui/settings/section/AISettingsSection.kt:114` | release policy（anon 本就公開，靠 RLS） | P3 |
| Keystore | `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | `.env` 或 CI env | `app/build.gradle:71-73`，**預設值 `yukiboard2026`** | Gradle signingConfig | **build-time secret（致命）** | **P0**（若 release 沿用預設）|

> **補充**：`.env` 本身已於 `.gitignore` 宣告（`.env` + `keystore/` 都 ignored），git tree 不洩。主要風險來自「**build 時被編入 APK** → APK 反編譯後即洩」。

---

## C. 三分類歸檔

### C.1 Client Secret 本質問題（必須移出 APK）

**定義**：此類 key 的安全性仰賴 secrecy，放進 APK = 公開。任何人拿到 APK 反編譯即可 dump。

| ID | key | 為什麼是 client secret | 最終應該放哪裡 |
|---|---|---|---|
| K-2 | `NEWEBPAY_HASH_KEY` | 藍新交易 AES 加密 key — 有它就能偽造任何 MerchantOrderNo / Amt 的 tradeInfo，劫持付款意圖 | **後端 server 獨佔**，client 只傳 amount/orderId，由 server 簽完回來 |
| K-3 | `NEWEBPAY_HASH_IV` | 同上，AES IV 一起跑就能解開/偽造 | **後端 server 獨佔** |
| K-4 | `INVITE_HMAC_SECRET` | HMAC 簽章是邀請碼的唯一驗證機制；secret 洩 = 任何人可造任意邀請碼、啟用 AI / 領 Z幣 | **後端 server 簽發，client 只驗證 existence via API**（本質轉為 API 查表）|
| Keystore | release keystore 密碼 | 攻擊者可用同 keystore 簽出偽版 APK 做 side-load 攻擊 | **CI 環境變數注入 or 開發機外部不進 repo**（目前 `.gitignore` 擋，但預設值 `yukiboard2026` 若 release 沿用就等於公開）|

**建議**：S12-1 `Newebpay` server-side signing 是 K-2 / K-3 的唯一正解；K-4 的後端轉型建議一併在 S12-1/2 盤算；Keystore 只需 release contract 檢核，不必動 `.kt`。

---

### C.2 Release Policy 問題（不是 client secret 本質，但仍需分級策略）

**定義**：key 本身不是「不能公開」，但「無差別編入 APK」會造成計費 / 配額 / 封鎖等營運風險。**解法是 release 策略**而不是「移出 APK」。

| ID | key | 風險場景 | 解法 |
|---|---|---|---|
| K-1 | `NEWEBPAY_MERCHANT_ID` | 公開後可被模仿成立假付款頁（但沒 HashKey 仍無法完成簽章） | release 編入可接受；但整個藍新 client 應走 server-side 簽章，MerchantID 自然不需 client 讀 |
| K-7 | `GIPHY_API_KEY` | release 若編入，全用戶共用 → Giphy 每月配額秒耗；被標為濫用則整個 client id 被停 | release APK **不編**（置空）+ 設計 server proxy `INSIGHT_API/gif-proxy` 走 Insight；或設計面讓企業版自帶 |
| K-10 | `OPENWEATHER_API_KEY` | 同 K-7：共用 key 配額秒耗 + 被停 | 同 K-7：Insight 代理 + 設計面 BYO |
| K-8 | `OPENAI_API_KEY` | release APK 若編入 = 公司信用卡代付全用戶 AI | release APK **強制置空**（目前 `ifNotBlank` 才 import，但預設 `''`，gradle 會編入字串常量 `""`）；用戶必須自帶或啟用 SnowRealm proxy |
| K-9 | `ANTHROPIC_API_KEY` | 同 K-8 | 同 K-8 |
| K-5 | `PLATFORM_API_KEY` | Insight 後端互信 token，若編 APK = 後端識別不出真實 client | release 靠 HTTPS + server-side IP/origin check + rotation；APK 編入是既定設計（identity claim）而非 secret |
| K-11/12 | Supabase URL + anon key | anon key 本就設計為公開，**安全由 RLS 政策決定** | RLS 審計是 Sprint 12+ 議題；本身 release 編入可接受 |

---

### C.3 Settings / Consent / Expose 問題

**定義**：雖然 key 有 BYO 設計，但 UI / settings / telemetry / log 表面上仍有暴露或繞過風險。

| 問題 | 位置 | 說明 |
|---|---|---|
| **AIProviderManager 旁路直讀**（已於 S7 audit R-9 標註，未收斂） | `service/ImeServiceInput.kt:255`、`ui/settings/section/SpeechSettingsSection.kt:266` | 兩條旁路 `BuildConfig.OPENAI_API_KEY.takeIf { isNotBlank } ?: mgr.getApiKey(...)`。若 release 編入非空 key，旁路會優先使用 BuildConfig 而非用戶輸入的 key。違反「正式版用戶自帶 / release 置空」契約。|
| **WeatherManager 無 BYO** | `weather/WeatherManager.kt:28` | 直讀 BuildConfig，無 user-input fallback。release 若置空 = 功能完全失效（無替代路徑）|
| **GiphyApiClient 無 BYO** | `gif/GiphyApiClient.kt:19` | 同 Weather；`isAvailable()` 為 false 時面板僅顯示空，沒有「請到設定填入 Giphy key」提示 |
| **ZCoinWalletActivity 跳 Custom Tab** | `ui/currency/ZCoinWalletActivity.kt:21-24` | 已走 Insight 錢包頁跳板，本地不做 payment。這是 S12-1 可利用的現成 escape hatch — Newebpay 本地 client 實質可淘汰 |
| **consent 尺度** | 無總設定面 | 目前用戶看不到「我的 OpenWeatherMap 是否發出定位」、「Giphy 查詢詞是否送出」的明確 consent；S12-3 / S12-5 的 observability / release policy round 應補 |
| **Log / telemetry 是否洩 key** | grep `Log\.(d\|i\|w\|e\|v).*API_KEY\|hashKey\|HMAC\|merchant` → **0 matches** | OK：目前 log 無 key 洩漏 |

---

## D. File Scope 與禁區

### D.1 S12-1 Newebpay Server-side Signing Plan（CCA plan 部分）

**主要 file scope（閱讀不改）：**
- `app/src/main/java/com/snowrealm/yukiboard/currency/NewebpayClient.kt`（整檔）
- `app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinApi.kt`（Supabase 交易紀錄端）
- `app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinManager.kt`
- `app/src/main/java/com/snowrealm/yukiboard/ui/currency/ZCoinWalletActivity.kt`（既有 Insight Custom Tab 跳板）
- `app/build.gradle:62-65`（NEWEBPAY_* buildConfigField 宣告）

**禁區：**
- ❌ 不改 `.kt`
- ❌ 不盤 Insight 後端實作（Insight 是另一專案、另一玄樞 gate）
- ❌ 不擴成 billing / subscription / 收據管理（超出 Sprint 12 邊界）

**交付物（S12-1 CCA 負責寫）：**
- 產品流程：使用者操作序列（WebView / Custom Tab / 結果回傳）
- 設定面 UX：錯誤碼對應的文案、fallback 路徑、離線處理
- 錯誤訊息：payment cancelled / network failed / server reject / 驗簽 mismatch 各情境
- release contract：client 只保留 `MerchantID`（可選）+ 呼叫 Insight `/zcoin/topup/sign` API；`HashKey` / `HashIV` 由 server 獨佔
- Insight API contract 草案：`POST /zcoin/topup/sign` → `{ tradeInfo, tradeSha, postUrl }`

---

### D.2 S12-3 API Key Release Policy（CCA 主責）

**file scope（閱讀不改）：**
- `app/build.gradle:49-65`（所有 buildConfigField）
- `app/src/main/java/com/snowrealm/yukiboard/claw/provider/AIProviderManager.kt`（BYO 機制）
- `app/src/main/java/com/snowrealm/yukiboard/gif/GiphyApiClient.kt:19`
- `app/src/main/java/com/snowrealm/yukiboard/weather/WeatherManager.kt:28`
- `app/src/main/java/com/snowrealm/yukiboard/ui/settings/AISettingsActivity.kt`（BYO UI 既有設計）
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceInput.kt:255`（R-9 旁路）
- `app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/SpeechSettingsSection.kt:266`（R-9 旁路）

**禁區：**
- ❌ 不改 `.kt`
- ❌ 不擴成全 AI provider 架構重寫（用戶 BYO 機制已存在，僅需契約化）
- ❌ 不碰 Insight 後端 proxy 實作細節

**交付物（S12-3 CCA）：**
- 產品面：哪些 key 由 release policy 置空 / 哪些允許編入 / 哪些走 Insight proxy
- 設定面：AISettings 已有 BYO，補 Weather / Giphy 的 BYO 設計或 Insight proxy 說明
- 同意與 consent 語意：「啟用 AI 時送 OpenAI 訊息」、「啟用天氣時送 OpenWeatherMap 定位」
- release contract：
  - `release` build：OPENAI / ANTHROPIC / GIPHY / OPENWEATHER **強制 CI 環境置空**（`.env` 僅 debug 用）
  - `debug` build：允許 `.env` 注入
  - 掃描策略：CI 加 step 檢 APK BuildConfig，非 debug 若任一 key 非空 → fail

---

### D.3 S12-5 Observability Hook（CCA consent / error wording 部分）

**file scope（閱讀不改）：**
- `app/src/main/java/com/snowrealm/yukiboard/app/net/AppHttpClient.kt`
- 各 `ImeService*.kt` silent catch 已清區（S11-4 成果）
- `service/YukiBoardIMEService.kt` lifecycle hooks
- payment / AI / weather / gif / Supabase 各 entry 的錯誤對應 wording

**禁區：**
- ❌ 不改 `.kt`
- ❌ 不實作 Crashlytics / telemetry lib 接線（由 S12-5 CCB 做）
- ❌ 不把 PII / key / user message 列入上報

**交付物（S12-5 CCA）：**
- 觀測接點 wording：哪些錯誤可見、怎麼告訴用戶、是否需 consent
- 隱私邊界：打字內容不上報、composing buffer 不上報、只上報 event name + error code + SDK version
- consent 流程：首次啟用觀測前需 dialog 明示

---

## E. 風險分級（P0 → P3）

### 🔴 P0 — 必須先處理，否則 release 就是漏洞

1. **K-2 `NEWEBPAY_HASH_KEY` 寫死 APK** — S12-1 + S12-2 解
2. **K-3 `NEWEBPAY_HASH_IV` 寫死 APK** — S12-1 + S12-2 解
3. **K-4 `INVITE_HMAC_SECRET` 預設值可能編入 release** — S12-3 release policy + （可選）server-side HMAC 簽發
4. **Keystore 密碼預設值 `yukiboard2026` 可能沿用** — S12-3 release checklist 補一行（不改 `.kt`）

### 🟠 P1 — release 前應處理

5. **OPENAI / ANTHROPIC release 編入** — R-9 旁路 + release policy
6. **GIPHY / OPENWEATHER release 編入** — BYO 缺 + release policy
7. **Newebpay client 既定死檔但 Supabase 餘額更新 contract 未定** — S12-1 contract

### 🟡 P2 — 可在 S12 內收斂的設計債

8. R-9 兩條旁路：`ImeServiceInput:255` + `SpeechSettingsSection:266`
9. WeatherManager / GiphyApiClient 無 BYO（設計債）

### 🟢 P3 — 不在 Sprint 12 scope，留 record

10. Supabase RLS 政策審計（Sprint 13+）
11. `PLATFORM_API_KEY` rotation 機制（Sprint 13+）
12. 整體 consent dashboard（Sprint 13+）

---

## F. Contract 草案（給後續 round 參考）

### F.1 Newebpay server-side signing contract（S12-1 共識前草案）

```
POST https://insight.snowrealm.pet/zcoin/topup/sign
Authorization: Bearer <InsightJWT>
Body: { "amount": 100, "orderId": "ZC-xxxx", "email": "user@..." }

Response:
{
  "tradeInfo": "<AES_encrypted_hex>",
  "tradeSha":  "<SHA256_hash>",
  "postUrl":   "https://ccore.newebpay.com/MPG/mpg_gateway",
  "merchantId": "MSxxxxxx",
  "version":   "2.0"
}

Client 只做：WebView POST 這四個欄位 → 等 NotifyURL 回 Supabase。
Client **不再**持有 HashKey / HashIV。
```

### F.2 Release policy contract（S12-3 共識前草案）

```
Release Build (BuildConfig must be empty):
- OPENAI_API_KEY            ← ""（強制）
- ANTHROPIC_API_KEY         ← ""（強制）
- GIPHY_API_KEY             ← ""（強制，走 Insight proxy）
- OPENWEATHER_API_KEY       ← ""（強制，走 Insight proxy）
- NEWEBPAY_HASH_KEY / _IV   ← ""（強制，已移至 server）
- INVITE_HMAC_SECRET        ← 嚴禁預設值 `yukiboard_dev_2026`

Release Build (可編入):
- PLATFORM_API_KEY          ← identity claim，靠 server-side 驗證補強
- YUKIBOARD_SUPABASE_URL / _ANON_KEY ← 設計公開，靠 RLS
- INSIGHT_API_BASE_URL      ← 公開 URL
- NEWEBPAY_MERCHANT_ID      ← 公開資訊（可選）
- KEYSTORE_PASSWORD 等       ← CI 注入，不使用預設值

CI Gate（S12-3 可實作）：
- 檢查 release APK BuildConfig → 前 6 項任一非空 → FAIL
- 檢查 INVITE_HMAC_SECRET 不等於 `yukiboard_dev_2026` → FAIL
- 檢查 KEYSTORE_PASSWORD 不等於 `yukiboard2026` → FAIL
```

---

## G. 盲區 / 疑問（留給玄樞裁示）

1. **K-4 `INVITE_HMAC_SECRET` 後端轉型**：是否要在 S12 內轉為 Insight 後端簽發 API，還是 S12-3 只做 release policy 限制（禁預設值 + CI gate）即可？**建議**：S12 先做 policy；後端轉型列 Sprint 13。
2. **Newebpay server-side 是由 Insight 團隊做還是 YukiBoard 團隊做**？contract 接口由誰定？**建議**：Insight 已有 `/zcoin/*` 前綴，此 API 落 Insight；YukiBoard 只提案 contract 草案，實作屬 Insight sprint。
3. **R-9 旁路是否 S12-3 順手清**？原 S7 標為 NOT CRITICAL，但若 release policy 強制 OPENAI 置空，旁路的 `takeIf { isNotBlank }` 永遠走 else，旁路等於 dead code。**建議**：S12-3 順手 remove 兩條旁路，不另立 round。
4. **Supabase anon key 暴露**：是否在 S12 做 RLS policy 審計？**建議**：不在 S12 scope（`ACTIVE` 狀態 anon key 設計上公開），列 Sprint 13 候選。
5. **Keystore 預設值**：是否要 S12-3 CI gate 檢查？**建議**：是，低成本高價值。

---

## H. 驗證

- 無 `.kt` 改動，git status 預期僅新增 `docs/SPRINT12_0_CCA_PAYMENT_KEY_CONSUME_AUDIT_2026-04-19.md`
- 無需跑 build / test
- grep 交叉比對：`BuildConfig\.(NEWEBPAY|GIPHY|OPENAI|ANTHROPIC|OPENWEATHER|YUKIBOARD_SUPABASE|PLATFORM|INSIGHT|INVITE)` 命中已全數納入 B 節 inventory

---

## I. 下一步（等玄樞 gate）

1. 玄樞判 `READY` → 解鎖 S12-1
2. S12-1 由 `雪鑰`（contract plan）+ `鍵律`（runtime plan）並行交付
3. 本輪 CCA + CCB 兩份 audit 合併後，S12-1 的 contract / runtime 邊界才能準確切
4. 若玄樞指出疑問（G 節），本檔補稿後再 gate

### 交付者自白

- Role check：本文件 scope 嚴格落在雪鑰 `S12-0-CCA-PAYMENT-KEY-CONSUME-AUDIT` 範圍，未越界至鍵律 `S12-0-CCB-REMOTE-SECURITY-AUDIT`（remote keyboard PIN / brute-force / local attack surface 全未涉及）。
- 文件型：audit / contract / risk，不改 `.kt`，符合 S12-0「先 contract 不 implementation」原則。
- 分類守則：三分類嚴格按「是否為 client secret 本質」區分，不把 release policy 問題錯置為 client secret 問題。

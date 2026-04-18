# Sprint 12-1 Gate Verdict — 合併審查（董事長 2026-04-19 指示修訂版）

日期：2026-04-19
Gate 身份：`雪鑰`（CCA 代行 `玄樞`）
審查對象：
- `docs/SPRINT12_1_CCA_NEWEBPAY_CONTRACT_PLAN_2026-04-19.md`（雪鑰 CCA）
- `docs/SPRINT12_1_CCB_NEWEBPAY_RUNTIME_PLAN_2026-04-19.md`（鍵律 CCB）
CH 順序：CH-1（S12-1 → S12-2 → S12-4 → S12-3 → S12-5），董事長 2026-04-19 拍板
**修訂註**：本版本於 2026-04-19 依董事長指示「ZCoin 不能刪」重寫，並納入對 Insight 專案真實 ZCoin 實作的 cross-check 結果。原初稿中「刪除 `currency/` 三檔」方向**撤回**。

---

## A. 結論

**Gate 判定：`READY`**（修訂後）

1. 兩份 plan 在「Newebpay 實質 server-side signing 已由 Insight 後端完成」此主結論上**一致且正確** —— 經 cross-check Insight 專案確認無誤（見 §B.5 Insight 實作地圖）。
2. 兩份 plan 對「`currency/` 三檔當前 0 caller」的 grep 結論技術上正確，但對其**語意定位**都判斷錯：該三檔並非可直接清除的 dead code，而是 **YukiBoard × Insight ZCoin 整合的預留 Android client scaffold**。證據：
   - `ui/currency/ZCoinWalletActivity.kt` 已由 `SettingsActivity.kt:159` 串接，是使用中的 entry。
   - `data/InsightSurveyClient.kt:27/56/73` 已消費 Insight 回傳的 `reward_zcoin` 欄位，是使用中的 reward 讀取路徑。
   - `claw/ClawClient.kt` 註解明示「未來：Z幣帳號登入」「未來 Z幣 → 由 ZCoinManager 決定」。
   - `store/SkinManager.kt` 註解明示「Z幣皮膚從 Supabase 拉取」預留。
   - Insight 專案有完整的 ZCoin backend（見 §B.5），`currency/` 三檔是 YukiBoard 側對應的尚未 wire-up 的 Android client scaffold。
3. P0 風險**改以「拔掉 APK 內的 secret」而非「刪除 scaffold」方式處理**（見 §C 最終 scope）。
4. 開放問題 D.1-1（Insight 歸屬）/ D.1-2（release 立即剝除）**兩方建議一致**：Insight 已完成 server-side signing；release APK 立即剝除 `NEWEBPAY_HASH_KEY/IV` 真值 —— 本 gate 採納但**以「空值注入 + scaffold 保留」實施**，而非「整檔刪除」。
5. CCB 三個錯誤處理補強（§E.2）**全部納入**。

**建議解鎖**：`S12-2-CCB-ANDROID-PAYMENT-REFACTOR-IMPLEMENTATION`（由鍵律實作）→ `S12-2-CCA-ANDROID-PAYMENT-CONSUME-REVIEW`（由雪鑰審）。

---

## B. 一致性交叉比對

### B.1 主線結論一致（可直接合併）

| 結論 | CCA 位置 | CCB 位置 | 合併 |
|---|---|---|---|
| Newebpay server-side signing 已由 Insight 完成（Custom Tab 跳板即 de facto pattern） | §B、§C | §B、§C.3、§G | ✅ |
| `ZCoinWalletActivity` 保留為 payment 唯一 entry | §I、§H.2 | §C.1、§D.3 | ✅ 保留 |
| `app/build.gradle:63-65` 三個 NEWEBPAY_* 欄位**真值必須從 release APK 剝除** | §F.1、§I | §C.2、§D.2 | ✅（採空值注入） |
| 嚴禁 client 端簽章 / 本地 payment builder | §G.2、§I 禁區 | §F-2、§F-3 禁區 | ✅ 禁 |
| 嚴禁 client 直寫 Supabase zcoin | 隱含於 §G.2 表 | §F-3 明確禁區 | ✅ 禁 |
| D.1-1 Newebpay server 歸屬 Insight | §H.1 | §G.7 偏 A | ✅ Insight |
| D.1-2 release 立即剝除真值 | §H.2 | §G.7 偏「是」 | ✅ 立即 |
| Insight `/zcoin/topup/sign` 為 forward-compatible 契約，當前 Custom Tab 路徑不必做 | §C 草案 | §C.3 不需 sign response 回 app | ✅ 存檔 |

### B.2 差異點 → 合併決定（**修訂**）

| # | CCA 主張 | CCB 主張 | **修訂後合併決定** | 理由 |
|---|---|---|---|---|
| 1 | `ZCoinApi.kt` 保留（§G.2 client 責任中保留 `fetchBalance` / `recordSpend`） | `ZCoinApi.kt` 整檔刪（§D.1） | **保留** | 董事長指示 ZCoin 不可刪。`ZCoinApi` 是未來 Android 顯示餘額 / 紀錄消費時的 client 層 scaffold；Insight 有 `GET /api/zcoin`、`POST /api/zcoin/spend` 對應 endpoint。目前採 dormant 保留，未來接 Insight 時由 `ZCoinApi` 改走 `InsightApiClient` 而非直連 Supabase。 |
| 2 | `ZCoinManager.topUp()` 刪、class 保留（§G.2 + §J-4 提議 `reward()` 方法） | `ZCoinManager.kt` 整檔刪（§C.2） | **保留整檔** | 董事長指示 ZCoin 不可刪。`ZCoinManager` 是未來 Android 快取 / reward 發放 scaffold。`topUp()` 雖然當前無 caller，但其存在本身並非 runtime 風險（真正風險在 `NewebpayClient` 的 HashKey/IV 與 Supabase 直寫），保留等 Sprint 13+ 對接 Insight `/api/zcoin` 時清理。`EncryptedSharedPreferences` 明文 fallback 改為 S12-3 `API Key Release Policy` 時處理。 |
| 3 | NewebpayClient.kt 整檔刪 | NewebpayClient.kt 整檔刪 | **保留檔案但移除 client-side signing 能力** | 董事長指示 ZCoin 不可刪。P0 風險（HashKey/IV 可反編譯）改以兩道方式處理：(a) `app/build.gradle:63-65` 三行真值**改為空字串注入**（release APK 不再包含真 secret）；(b) `NewebpayClient.kt` 在 class 頂部加 deprecation 註解明示「client-side signing 已廢棄，真實簽章走 Insight `/api/payment/zcoin-recharge`」，保留資料類別與 API shape 供未來若轉 in-app WebView 路徑時繼承。 |
| 4 | §D.2 建議錢包入口 refresh balance | §D.4 不在 S12-2 scope（balance widget 留 Sprint 13+） | **延後至 Sprint 13+** | 當前 `ZCoinWalletActivity` 走 Custom Tab 開 Insight `/wallet` 頁，balance 由 Insight 網頁端顯示，Android 端無需 refresh。未來若在 Android 內做 balance widget，再用 `ZCoinApi.fetchBalance()`（對接 Insight `/api/zcoin`，**而非**直連 Supabase）。 |
| 5 | §E.1 Custom Tab 無法開啟 → `catch ActivityNotFoundException` + Toast | §E.1-E.2 三個錯誤黑洞具體化：CustomTab 不可用 / tenantId default / 未登入 | **採 CCB：三個都補** | CCB 更完整。S12-2 CCB 實作 + CCA 審 wording（雪鑰 §D.3 wording 表對接 CCB §E.2 的三種情境）。 |
| 6 | §C API 契約（含 Bearer + Platform-Key） | §G-5 建議「Android 端不傳 JWT，依 Chrome cookie」 | **保留兩者，情境不同** | CCB G-5 正確描述當前 Custom Tab 路徑（cookie 足夠）；CCA §C 是未來 in-app WebView 啟動時的契約。現況走 cookie、未來走 Bearer，forward-compatible。 |

### B.3 CCB 獨有補強（CCA 未提，直接納入）

| CCB 內容 | 納入位置 |
|---|---|
| §B.3 當前 runtime 無 Z 幣漏洞（只是 APK 含 secret） | 合併 S12-1 風險判斷：**P0 在「APK 反編譯可還原 HashKey/IV」，非 runtime 偽造** |
| §E.2 ZCoinWalletActivity 三個錯誤處理補強 | 併入 S12-2 scope 作為 CCB 實作項 |
| §G-1~G-6 runtime 契約提議（sign response 不回 HashKey、orderId server 端生成等） | 併入 forward-compatible Insight API 契約條款（未來真轉 in-app WebView 時採用） |

### B.4 CCA 獨有補強（CCB 未提，保留）

| CCA 內容 | 保留理由 |
|---|---|
| §C.1-C.4 Insight API 草案 + 與 `InsightApiClient` 對齊契約 | Forward-compatible 文件，未來 in-app WebView 啟動時可用 |
| §D.3 錯誤情境 wording 表 | S12-2 CCA 審 wording 依據，對接 CCB §E.2 三個錯誤點 |
| §F.2 `.env` / `.env.example` 處置 | S12-2 實作 checklist 項 |
| §H.1 Insight 三項 readiness 對接 | S12-2 解鎖前置條件（§E） |

### B.5 Insight 真實 ZCoin 實作地圖（本次 cross-check 新增）

董事長指示「找到 Insight 真正實作 ZCoin 有接付款端的地方 還有 Z 幣餘額顯示頁面」，結果：

**付款端（Newebpay server-side signing）：**
```
insight-engine/src/app/api/payment/zcoin-recharge/route.ts  ← GET endpoint
  - PACKAGES 字典（starter/basic/popular/value）
  - 讀 MIG_/NEWEBPAY_ env（server-side）
  - 呼叫 lib/newebpay.ts createPaymentData()
  - 產 auto-submit HTML form → Newebpay paymentUrl
  - 寫 payment_logs（pending，plan='zcoin_{packageId}'）

insight-engine/src/app/api/payment/notify/      ← Newebpay 回調
insight-engine/src/app/api/payment/return/      ← 成功返回頁
insight-engine/src/app/api/payment/create/      ← 通用建單
insight-engine/src/lib/newebpay.ts              ← AES/CBC/PKCS5Padding + SHA256 簽章
insight-engine/src/lib/zcoinEngine.ts           ← Z 幣核算引擎
```

**Z 幣餘額顯示頁：**
```
insight-engine/src/app/(public)/wallet/page.tsx  ← /wallet
  - 呼叫 /api/zcoin 拿 { balance, totalEarned, totalSpent, history }
  - 顯示餘額卡 + 賺取方式 + 交易紀錄 + REASON_LABEL

insight-engine/src/app/api/zcoin/route.ts        ← 查詢 balance + history
insight-engine/src/app/api/zcoin/spend/          ← 消費扣款
insight-engine/src/app/api/zcoin/tasks/          ← 任務獎勵
insight-engine/src/app/(public)/zcoin-shop/      ← 商城
insight-engine/src/app/insight-admin/[tenantId]/zcoin/  ← tenant 管理頁（YukiBoard ZCoinWalletActivity 當前 Custom Tab 指向處）
insight-engine/supabase/migration_yukiboard_integration.sql  ← reward_zcoin schema
```

**結論**：Insight 側 ZCoin 功能**完備**，YukiBoard 的 `currency/` scaffold 之所以 0 caller，是因為 YukiBoard 端尚未把 scaffold wire-up 到 Insight endpoint（目前僅 Custom Tab 跳板模式）。未來拔掉 Custom Tab、改用 in-app 顯示時，`ZCoinApi` 改呼叫 Insight `/api/zcoin*`，`ZCoinManager` 作為快取層 —— 這是 scaffold 的設計意圖。

---

## C. S12-2 最終 File Scope（**修訂**）

### C.1 修改（2 處）

```
app/build.gradle:63-65
  三行 NEWEBPAY_* buildConfigField 真值**改為空字串注入**（若 .env 無該鍵則 ""），
  保留欄位本身讓 BuildConfig.NEWEBPAY_* 可在 code 中 reference 但 release APK 不再含真 secret。
  具體：確認 property reader 對 missing key 回傳 "" 而非例外；若當前邏輯會讀出 .env 真值，
  追加一條「release build 強制空值」policy（或直接把 .env 三行註解掉）。

app/src/main/java/com/snowrealm/yukiboard/ui/currency/ZCoinWalletActivity.kt
  補 3 個錯誤處理（CCB §E.2）：
  (a) CustomTabsIntent.launchUrl 外包 try/catch ActivityNotFoundException → Toast + finish
  (b) SnowRealmAuthClient.currentUser == null → Toast「請先登入雲端帳號」+ 引導登入 + finish（不走 "default" fallback）
  (c) tenantId == "default" 保留兜底但補 wording 提示
```

### C.2 Scaffold 保留 + 降級註記（3 檔）

```
app/src/main/java/com/snowrealm/yukiboard/currency/NewebpayClient.kt
  不刪檔；class 頂部加 KDoc：
  """
  [DEPRECATED SCAFFOLD — 2026-04-19 / Sprint 12-1]
  Client-side Newebpay signing 已廢棄。正式簽章由 Insight 後端完成：
    POST /api/payment/zcoin-recharge （Insight 專案）
  YukiBoard 目前透過 CustomTabsIntent 開 Insight wallet 頁，
  Newebpay redirect + notify 均在 Insight 側處理。
  本檔保留作為未來若轉 in-app WebView / 新 sign API 路徑時的資料類別參考，
  不得在當前版本中被呼叫。release APK 已不再含真 HashKey/IV。
  """
  其他不改（本身就 0 caller，不會產生 runtime 風險）。

app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinManager.kt
  不刪檔；class 頂部加 KDoc：
  """
  [DORMANT SCAFFOLD — 2026-04-19 / Sprint 12-1]
  未來 YukiBoard 對接 Insight /api/zcoin 時的 Android 快取 / 發放層。
  當前 0 caller；EncryptedSharedPreferences 明文 fallback 風險已於 S12-0
  audit 紀錄，待 Sprint 13+ 整體 storage policy 處理。
  禁止新增 client 端直寫餘額邏輯；所有 balance 真值以 Insight 為 source of truth。
  """
  其他不改。

app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinApi.kt
  不刪檔；class 頂部加 KDoc：
  """
  [DORMANT SCAFFOLD — 2026-04-19 / Sprint 12-1]
  未來對接路徑**變更**：原設計走 Supabase RPC，實際實作需改走 Insight HTTP API：
    GET  /api/zcoin          → balance + history
    POST /api/zcoin/spend    → 消費扣款
    POST /api/zcoin/tasks/*  → 任務獎勵
  當前 0 caller；未來 wire-up 時以 InsightApiClient 替換 SupabaseClient.rpc。
  禁止 client 端直連 Supabase zcoin_wallets。
  """
  其他不改。
```

### C.3 同步清理

```
.env / .env.example
  NEWEBPAY_HASH_KEY / NEWEBPAY_HASH_IV 清空或註解（開發機若需測整合流程，以 Insight dev 環境代之）
  NEWEBPAY_MERCHANT_ID 可留（非 secret；公開的商店代號）

CLAUDE.md API Key 表
  NEWEBPAY_HASH_KEY / NEWEBPAY_HASH_IV 加註「現版本不再使用；由 Insight 後端持有」
  NEWEBPAY_MERCHANT_ID 保留（forward-compatible）

CLAUDE.md 檔案索引表
  currency/NewebpayClient.kt / ZCoinManager.kt / ZCoinApi.kt 行保留，職責欄補「[Scaffold — dormant] 待接 Insight」
```

### C.4 保留完全不動

```
ui/currency/ZCoinWalletActivity.kt 本體（僅 §C.1 增補錯誤處理）
ui/settings/SettingsActivity.kt:159                ← Intent 開 wallet 的那行
AndroidManifest.xml:132 附近 Activity 宣告          ← 不動
BuildConfig.INSIGHT_API_BASE_URL                   ← 保留（P3 公開 URL）
auth/SnowRealmAuthClient.kt                        ← 不動
data/InsightSurveyClient.kt                        ← reward_zcoin 讀取路徑（使用中，不動）
claw/ClawClient.kt                                 ← future reference 註解保留
store/SkinManager.kt                               ← future reference 註解保留
```

### C.5 與原初稿（已撤回）的差異

| 項 | 原初稿方向 | **修訂後方向** |
|---|---|---|
| `currency/NewebpayClient.kt` | 整檔刪 | **保留**，加 DEPRECATED KDoc |
| `currency/ZCoinManager.kt` | 整檔刪 | **保留**，加 DORMANT KDoc |
| `currency/ZCoinApi.kt` | 整檔刪 | **保留**，加 DORMANT KDoc + 路徑變更備註 |
| `build.gradle:63-65` | 三行整刪 | **保留欄位**，真值改空注入（release APK 不含 secret） |
| P0 secret 治理手段 | 刪檔消除 surface | 抽真值、保 scaffold（scaffold 本身非 runtime 風險） |
| `.env.example` NEWEBPAY_* | 刪 | 註解或清空（保留欄位名供開發參照） |

---

## D. S12-2 提交鏈與責任邊界

依 Sprint12-All-Round-Gate.md S12-2 條文：

```
1. 鍵律 完成 S12-2-CCB-ANDROID-PAYMENT-REFACTOR-IMPLEMENTATION
2. 提交給：雪鑰
3. 雪鑰 完成 S12-2-CCA-ANDROID-PAYMENT-CONSUME-REVIEW
4. 提交給：玄樞
5. 只有玄樞判定 READY，才可決定是否開 S12-3、S12-4、S12-5
```

### D.1 CCB 實作範圍（鍵律）

- `app/build.gradle:63-65` HASH_KEY / HASH_IV 真值改空注入（MERCHANT_ID 保留）
- 在 `currency/NewebpayClient.kt` / `ZCoinManager.kt` / `ZCoinApi.kt` 各自 class 頂部加 §C.2 指定的 KDoc 區塊
- 在 `ZCoinWalletActivity.kt` 補 3 個錯誤處理（§C.1）
- 同步清理 `.env.example`（§C.3）
- 跑 `:app:compileDebugKotlin` + `:app:testDebugUnitTest` 確保無 regression
- 驗證 release build 的 `BuildConfig.NEWEBPAY_HASH_KEY/IV` 為空字串（可 grep 或 APK analyzer）
- 產出 `docs/SPRINT12_2_CCB_ANDROID_PAYMENT_REFACTOR_IMPLEMENTATION_2026-04-19.md`

### D.2 CCA 審查範圍（雪鑰）

- 審 `ZCoinWalletActivity.kt` 三個錯誤處理 wording（對照 §D.3 wording 表）
- 審三份 DORMANT/DEPRECATED KDoc 文字是否清楚表達「當前禁用 + 未來接入方向」
- 審 `.env.example` / `CLAUDE.md` 同步
- 審 release contract 實際落地（`BuildConfig.NEWEBPAY_HASH_KEY/IV` 確認為空）
- 審 scaffold 保留對 release APK 反編譯風險是否已降到可接受（重點：secret 已抽離、class shell 本身不構成 exploit）
- 產出 `docs/SPRINT12_2_CCA_ANDROID_PAYMENT_CONSUME_REVIEW_2026-04-19.md`

### D.3 嚴守分工（`feedback_role_boundary.md`）

- CCB 負責改 `.kt` 與 build.gradle；CCA **不動 .kt**
- CCA 只審、不實作；發現問題在 review 文件中列出，由 CCB 二次修
- 兩人不混改同一檔

---

## E. S12-2 前置條件（董事長 / 玄樞需確認）

### E.1 降級至「資訊同步」而非「blocking readiness」

原初稿列 Insight 三項 readiness 為 S12-2 blocking，**修訂後降級為資訊同步**，因為：

修訂後 S12-2 scope **不刪 scaffold、不改 Insight/YukiBoard 串接路徑**，僅做：
- build.gradle 值清空
- KDoc 註記
- ZCoinWalletActivity 錯誤處理補強

這三項**完全不依賴 Insight readiness**，可獨立進行。

### E.2 與 Insight 團隊同步事項（非 blocking）

1. 確認 Insight `/insight-admin/{tenantId}/zcoin` 路徑穩定（現況 `ZCoinWalletActivity` Custom Tab 指向）
2. 確認 Insight `/api/payment/zcoin-recharge` + `/api/payment/notify` 已上線（Newebpay 正式簽約與否）
3. 告知 Insight：YukiBoard S12-1 後 release APK 不再含真 HashKey/IV，所有 payment 依賴 Insight server 端

若 Insight 該三項任一未就緒，S12-2 仍可完成（不 blocking）；真正 blocking 的是 Sprint 13+ 若要做 Android in-app balance widget 時。

---

## F. 未完全解決的 runtime 契約項（待玄樞拍板）

CCB §G-1~G-6 的 6 條 runtime 提議，對當前 Custom Tab 路徑**皆非必要**，但對未來 in-app WebView / sign API 啟動時有參考價值：

| # | CCB 提議 | 當前是否必要 | 未來啟動 in-app WebView 時 |
|---|---|---|---|
| G-1 | Sign response 不回 HashKey/IV | N/A | 必要 |
| G-2 | orderId 由 server 生成 | N/A | 必要（防重放） |
| G-3 | tradeInfo/tradeSha 直接 render 在 Insight 頁 form | 當前設計 | 可選（若改 WebView 則要回 Android） |
| G-4 | NotifyURL 驗簽 | 當前設計 | 必要 |
| G-5 | Android 端不傳 JWT，依 cookie | 當前設計 | 變（若 in-app WebView 要傳 Bearer） |
| G-6 | `?return_app=yukiboard` deep link | 可選 UX | 同 |

**gate 結論**：G-1 / G-2 / G-4 作為未來契約硬需求存檔；G-3 / G-5 / G-6 隨實作路徑再定。

---

## G. 開放問題合併（給董事長 / 玄樞）

### G.1 S12-2 前需董事長 / 玄樞確認

1. **三份 scaffold KDoc wording 是否由董事長定調？** 本 gate 提供草稿（§C.2）；若董事長希望更輕量（例如只留「SCAFFOLD — do not call」），可指示 CCB 簡化。
2. **`.env` NEWEBPAY_HASH_KEY/IV 是清空還是註解掉？**（本 gate 傾向註解，保留欄位名讓後續追溯）
3. **若 Sprint 13+ 要 wire-up `ZCoinApi` 對接 Insight `/api/zcoin`，是否立刻開 S12-6 或新 Sprint？** 本 gate 建議先完成 CH-1 順序（S12-2 → S12-4 → S12-3 → S12-5）後再評估。

### G.2 S12-2 後視情況處理

4. `CLAUDE.md` 檔案索引表 currency/ 三檔職責欄補「[Scaffold — dormant] 待接 Insight」—— S12-2 CCB 實作時順手
5. 未來 Android in-app balance widget —— Sprint 13 候選（依賴 §E.2 三項 Insight readiness）

### G.3 等未來 sprint

6. INVITE_HMAC_SECRET 後端轉型（S12-0 gate verdict §D.2-3，建議 Sprint 13）
7. Supabase RLS 審計（S12-0 gate verdict §D.5-9，建議 Sprint 13）
8. Firebase Crashlytics 決策（S12-0 gate verdict §D.4-7，S12-5 拍板）
9. R-9 兩條旁路清理（S12-0 gate verdict §D.2-4，S12-3 順手）
10. Keystore 預設值 CI gate（S12-0 gate verdict §D.2-5，S12-3）
11. ZCoinManager EncryptedSharedPreferences 明文 fallback（S12-0 gate verdict，已與 scaffold 一同保留，S12-3 API key release policy 時整體 storage policy 一併處理）

---

## H. Gate 輸出動作

`READY` 判定下，本 gate 要做的副作用：

1. 更新 `Sprint12-All-Round-Gate.md`：
   - S12-1 狀態 `ACTIVE` → `COMPLETED`
   - S12-1 段落 strikethrough + 指向本 gate verdict 與兩份 plan
   - S12-2 狀態 `LOCKED` → `ACTIVE`
   - §6 玄樞預設判定補 S12-1 結論摘要（含「scaffold 保留、secret 抽離」修訂點）
2. 本輪內不改 `.kt`（已確認 CCA / CCB 兩份 plan 都是純文件交付）。
3. S12-2 scope 降低 → 不依賴 Insight readiness，可直接解鎖（§E.1）。

---

## I. 交付者自白

- 本 gate 由 `雪鑰`（CCA）代行 `玄樞`；依 `feedback_role_boundary.md` 規則，gate 只做 gate，不實作，也不覆寫 CCB 的 runtime 判斷（B.2 差異點 1/2/3 原本採 CCB「整檔刪」版本，經董事長 2026-04-19 指示「ZCoin 不能刪」後修訂為 scaffold 保留 + secret 抽離，屬於董事長明確指示下的覆寫，非 CCA 越代）。
- 本版本為**修訂版**，原初稿（刪除 scaffold）已撤回。CCB 原 plan §C.2 / §D.1 / §D.2 「整檔刪」條款視為已被本 gate 覆寫，S12-2 CCB 執行時依本 gate §C.2 為準。
- 審查對象是兩份 plan 文件，不涉及 `.kt` 修改。
- CH-1 順序由董事長 2026-04-19 拍板，本 gate 依序推進至 S12-2；非自排。
- S12-2 實作主責 CCB（鍵律），審查主責 CCA（雪鑰），兩人不混改同一檔（CCA 發現問題在 review 文件中列，由 CCB 二次修）。
- Insight ZCoin 實作地圖（§B.5）由 CCA 本次 cross-check 產出，作為 S12-2 及未來 Sprint 13+ wire-up 的事實依據。
- 若董事長對本修訂版任何項不同意，本 gate 可再次補稿。

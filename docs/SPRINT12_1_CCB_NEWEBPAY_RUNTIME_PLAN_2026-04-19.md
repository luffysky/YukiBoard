# S12-1-CCB Newebpay Runtime Plan

- 任務 ID：`S12-1-CCB-NEWEBPAY-RUNTIME-PLAN`
- 負責：鍵律
- 提交對象：雪鑰（並案）→ 玄樞 gate
- 日期：2026-04-19
- 狀態：`READY_FOR_REVIEW`
- 本輪性質：**plan / runtime contract**；`不改任何 .kt`
- 前置：S12-0 COMPLETED（CH-1 順序）

---

## A. Executive Summary

本計畫從 **Android client runtime** 線盤點「HashKey/IV 移出 APK 後，client 還剩什麼」。

**關鍵發現**（grep 驗證）：

> `currency/NewebpayClient.kt`、`currency/ZCoinManager.kt`、`currency/ZCoinApi.kt` 三檔**全無 caller**，在當前 runtime 是 dead code。
> 目前真正上線的 Z幣 runtime 只有 `ui/currency/ZCoinWalletActivity.kt` → Chrome Custom Tab → `$INSIGHT_API_BASE_URL/insight-admin/$tenantId/zcoin`。

這決定了 S12-1 runtime plan 的主旋律：**不是重構 payment path，而是「收斂到現有 Custom Tab 跳板」＋「刪除三檔 dead code」＋「從 build.gradle 剝除 NEWEBPAY_* 字段」**。S12-2 CCB implementation 將變得相對輕量（刪除 > 新增）。

依 `feedback_gate_no_self_schedule.md`，本 plan 不自排 S12-2 順序，只給 runtime contract + file scope 候選；最終由玄樞 + 雪鑰 CCA 並案時拍板。

---

## B. 當前 Payment Runtime 實況（grep 佐證）

### B.1 Dead code 清單

| 檔 | 定義 | Caller 數 | 證據 |
|---|---|---|---|
| `currency/NewebpayClient.kt` | `class NewebpayClient` + `buildPaymentParams()` | **0** | grep `NewebpayClient\|buildPaymentParams` → 僅命中定義本身 |
| `currency/ZCoinManager.kt` | `class ZCoinManager(context)` + `topUp / spend / syncBalance / clear` | **0** | grep `ZCoinManager(\|zCoinManager\.\|\.topUp(` → 僅命中定義本身 |
| `currency/ZCoinApi.kt` | `class ZCoinApi` + `fetchBalance / recordSpend / fetchTransactions` | **0** | grep `ZCoinApi(\|ZCoinApi\.\|zCoinApi\.` → 0 matches |

結論：**整個 `currency/` package 目前是 dead code**，雖 `BuildConfig.NEWEBPAY_HASH_KEY / HASH_IV` 會因為 `NewebpayClient.kt:21-22` 的欄位宣告被編進 APK（Kotlin 編譯會把常量參考保留），但 runtime 沒任何路徑消費它。

### B.2 真正上線的 Z幣 runtime

| 位置 | 行為 |
|---|---|
| `ui/settings/SettingsActivity.kt:159` | `Intent(…, ZCoinWalletActivity::class.java)` 直接開錢包 Activity |
| `ZCoinWalletActivity.launch(context)` | 對外入口（companion object），目前 SettingsActivity 未呼叫此 helper，直接走 Intent |
| `ZCoinWalletActivity.onCreate()` | 讀 `BuildConfig.INSIGHT_API_BASE_URL`（P3 公開 URL）+ `SnowRealmAuthClient.currentUser.tenantId`（fallback `"default"`）→ `CustomTabsIntent` 開 `{baseUrl}/insight-admin/{tenantId}/zcoin` → `finish()` |
| Chrome Custom Tab | 走 Chrome cookie / Insight session 完成使用者認證（Android app 不持 payment token） |
| Insight 後端 | 負責頁面、儲值、藍新 NotifyURL webhook、餘額更新 |

### B.3 為什麼目前 runtime 沒有 Z幣漏洞

- Payment 簽章**不在 client 端執行**（Custom Tab 只是跳轉）
- HashKey / HashIV 雖然**編入 APK**（因 `NewebpayClient.kt` 欄位宣告），但無 runtime 路徑消費 → 反編譯仍可抽出 secret，但當前無可觸發的偽造路徑
- 餘額顯示、交易紀錄 UI 都沒有（CustomTab 不屬於 app UI 樹）
- 因此**當前風險是「APK 含 secret」而非「runtime 漏洞」**；S12-1 要解的是「secret 不該進 APK」這一層

### B.4 本地 `.kt` 中殘留的 BuildConfig 消費者（runtime 會編入）

```
currency/NewebpayClient.kt:20  private val merchantId = BuildConfig.NEWEBPAY_MERCHANT_ID
currency/NewebpayClient.kt:21  private val hashKey    = BuildConfig.NEWEBPAY_HASH_KEY    ← P0 核心
currency/NewebpayClient.kt:22  private val hashIv     = BuildConfig.NEWEBPAY_HASH_IV     ← P0 核心
```

> 即使 class 無 caller，**只要 class 存在且被編譯，BuildConfig 字段就會被寫入 APK 的 BuildConfig.class**。唯一的 runtime-clean 解法是 (a) 把整個類從編譯單元移除 **且** (b) 把 `buildConfigField "...NEWEBPAY_HASH_KEY..."` 從 `app/build.gradle:63-65` 移除。

---

## C. Server-side Signing 後的 Runtime 邊界（client 該留什麼、不該留什麼）

與 CCA §F.1 contract 草案對齊（`POST /zcoin/topup/sign` → `{ tradeInfo, tradeSha, postUrl, merchantId, version }`）的同時，本節給 **runtime 視角** 的最終客端責任。

### C.1 Client 保留（允許在 APK 內）

| 能力 | 原因 | 檔 |
|---|---|---|
| 錢包入口 Activity | 使用者需要一個按鈕 / Setting row 開啟儲值 | `ui/currency/ZCoinWalletActivity.kt`（保留） |
| Insight base URL 讀取 | URL 本就公開（CCA §B K-6） | `BuildConfig.INSIGHT_API_BASE_URL` |
| tenantId 解析 | 用於組 URL path | `SnowRealmAuthClient.currentUser.tenantId` |
| Custom Tab 跳轉 | 讓使用者在 Chrome session 內完成付款 | `CustomTabsIntent` |
| （未來）餘額顯示 read-only | 若要顯示餘額，呼叫 Insight 受認證 API，**不**自建 Supabase Z幣寫路徑 | TBD（不在 S12-2） |

### C.2 Client **不得**保留（必須移出 APK）

| 物件 | 原因 | 處置 |
|---|---|---|
| `NewebpayClient.kt` 整檔 | HashKey/IV 直接宣告、AES client 簽章 | **刪除** |
| `BuildConfig.NEWEBPAY_HASH_KEY` / `NEWEBPAY_HASH_IV` | P0 secret | `build.gradle:64-65` 兩行**整行刪除** |
| `BuildConfig.NEWEBPAY_MERCHANT_ID` | 本非 secret，但 client 無 runtime 需要 | `build.gradle:63` **整行刪除**（釘死 `isConfigured = false`） |
| `ZCoinManager.kt` 整檔 | dead code、含 EncryptedSharedPreferences fallback 到明文的風險（CCB audit §D-3）| **刪除**（任何本地錢包暫存改由 Insight API 讀） |
| `ZCoinApi.kt` 整檔 | dead code，Supabase 直接寫已非現行 runtime | **刪除** |
| `.env.example` 中 `NEWEBPAY_*` 對應行（若有） | 避免新開發者誤配 | `.env.example` 同步刪（不在 `.kt`，列 S12-2 附帶） |

### C.3 Runtime 後的 Payment Flow（設計）

```
使用者 → SettingsActivity（點 "Z幣錢包" row）
    ↓
ZCoinWalletActivity.onCreate()
    ├─ tenantId = SnowRealmAuthClient.currentUser?.tenantId ?: "default"
    ├─ url = "${INSIGHT_API_BASE_URL}/insight-admin/${tenantId}/zcoin"
    └─ CustomTabsIntent.launchUrl(url) → finish()
    
    ↓ Chrome 共享 session
Insight 後端錢包頁
    ├─ 顯示餘額 / 歷史
    ├─ 使用者選金額 → Insight 後端生成 orderId → 呼叫 /zcoin/topup/sign
    ├─ Insight 用 **server-side HashKey/IV** AES 加密 tradeInfo + SHA256
    ├─ 回頁面 HTML form（tradeInfo/tradeSha/postUrl/merchantId/version）auto-submit 到藍新
    └─ 藍新付款完成 → NotifyURL 回 Insight webhook → 更新 Supabase zcoin_wallets
    
    ↓ 使用者回到 YukiBoard
（無 app 端事件 hook；下次開 wallet 或 Balance widget 讀取 Insight API）
```

**runtime 關鍵性質：**
- Android app **完全不持有** HashKey/IV
- Android app **不接 NotifyURL**（webhook 全在 Insight 後端）
- Android app **不寫** Supabase zcoin 資料表（read-only via Insight API，若未來要顯示餘額）
- 簽章過程**對 Android 完全不可見**（就算 reverse-engineer APK 也拿不到 secret）

---

## D. S12-2 File Scope 候選（runtime 視角）

> 不自排順序，僅列出 S12-2 CCB implementation 要動的 file。最終由玄樞 gate 核定。

### D.1 要**刪除**的檔

```
app/src/main/java/com/snowrealm/yukiboard/currency/NewebpayClient.kt    ← 整檔 delete
app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinManager.kt      ← 整檔 delete
app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinApi.kt          ← 整檔 delete
```

> 若 grep 後發現任一檔其實**有**外部 caller（未來 CCA / CCB review 時補 sanity check），則只刪除與 BuildConfig secret 相關的欄位並保留類骨架；但依目前 grep 結果都是 dead code，可全刪。

### D.2 要**修改**的檔

```
app/build.gradle:63-65      ← 刪除 NEWEBPAY_MERCHANT_ID / HASH_KEY / HASH_IV 三行 buildConfigField
.env.example                ← 同步刪除 NEWEBPAY_* 欄位（若存在）
```

### D.3 要**保留不動**的檔

```
app/src/main/java/com/snowrealm/yukiboard/ui/currency/ZCoinWalletActivity.kt ← 當前 runtime 唯一 entry
app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt:159 ← Intent 開 wallet 的那行
app/src/main/AndroidManifest.xml:132 ← ZCoinWalletActivity 宣告
app/build.gradle:52 INSIGHT_API_BASE_URL ← P3 公開 URL，保留
```

### D.4 **不在 S12-2 file scope**（留後續 sprint）

- 餘額顯示 widget / 錢包 UI 內嵌 — 若要做須先與 CCA 協商 Insight read API（Sprint 13+ 候選）
- `ZCoinWalletActivity` 改為 WebView 內嵌（棄用 Custom Tab） — 不是必要，且會把 Chrome cookie session 變回需自管，收益不大
- AndroidManifest 或 URL scheme 改動 — 不在 runtime plan 範圍

---

## E. 錯誤處理 / Fallback / Observability（Runtime 視角）

### E.1 現行 runtime 的錯誤黑洞

| 情境 | 現行行為 | 評估 |
|---|---|---|
| `INSIGHT_API_BASE_URL` 空 / 格式錯 | `baseUrl.trimEnd('/')` → 開不出合法 URL，Custom Tab 顯示錯頁 | 使用者沒有錯誤訊息 |
| 使用者未登入 Insight | `tenantId = "default"` 回退；開到 `default` 頁，Insight 後端顯示登入 / 無權 | 有機會混淆使用者 |
| Chrome / Custom Tab 不可用 | `CustomTabsIntent.launchUrl` throw `ActivityNotFoundException` | **目前無 try/catch**，會 crash Activity |
| 使用者付款中途關閉 Chrome | 無任何 app 端通知 | 正常；Insight webhook 負責對帳 |
| Insight 後端儲值 API 失敗 | 後端頁面自己處理，app 端無感知 | 正常 |

### E.2 S12-2 CCB 要補的 runtime 防線（最小集）

1. `ZCoinWalletActivity.onCreate()` 外層加 `try { CustomTabsIntent... } catch (ActivityNotFoundException) { Toast + finish }`
2. `tenantId == "default"` 時短暫 Toast 提示「請先登入雲端帳號再儲值」（或直接跳登入；wording 由 CCA 決定）
3. 若使用者未登入（`SnowRealmAuthClient.currentUser == null`），直接 finish 並引導登入，**不**用 `"default"` fallback 開出空頁
4. （配合 S12-5）這三個失敗點需呼叫未來 `ErrorReporter.report(category = "PAYMENT_ENTRY", ...)`；S12-1 僅預留 hook 位置，不接實作

### E.3 觀測點候選（供 S12-5 引用）

| 事件 | 目前處理 | S12-5 應上報 |
|---|---|---|
| `PAYMENT_ENTRY_NO_CUSTOM_TAB` | crash | warn + 一般錯誤 UI |
| `PAYMENT_ENTRY_NO_USER` | 走 default fallback | info + 引導登入 |
| `PAYMENT_ENTRY_NO_BASE_URL` | 空字串跑 URL | error |

這三個 event name 由 CCB 提案；wording / UX 由 CCA 在 S12-5 CCA 決。

---

## F. 禁區（S12-1 / S12-2 不得跨）

1. **不改 `ui/currency/ZCoinWalletActivity.kt` 以外的 UI 樹**
   - Reason：當前 wallet 唯一 entry 穩定、CCA 亦標為 escape hatch；重寫會拉長 sprint。
2. **不新建 `NewebpayClient` 替代 class（例如 `NewebpaySignRequester` 之類）**
   - Reason：整個 client-side 簽章方向應消滅，**不該**有 client payload builder。Insight 全管。
3. **不自建 Supabase Z幣寫路徑**
   - Reason：CCB audit §D-2 已指出 topUp 無 server 確認；若 client 繞過 Insight 直接寫 Supabase，所有 server-side 簽章努力歸零。
4. **不動 `AndroidManifest.xml` / 網路安全 config（除非玄樞另開）**
   - Reason：scope creep；S12-1 / S12-2 僅做 secret 移除 + dead code 清理。
5. **不把 `ZCoinWalletActivity` 改 WebView 內嵌**
   - Reason：Chrome Custom Tab 共享 Insight session，自建 WebView 要自管 cookie、增加攻擊面。
6. **不在 S12-1 / S12-2 引入 Firebase Crashlytics / Sentry**
   - Reason：gate verdict D.4-7 未拍板；S12-5 才是觀測門。本 plan 只預留 hook point。
7. **不碰 `auth/InviteCodeManager.kt` 的 HMAC secret**
   - Reason：K-4 的後端轉型 CCA §G-1 建議留 Sprint 13，不綁到 S12-1 / S12-2。
8. **不改 `service/YukiBoardIMEService.kt` 或 `SettingsActivity.kt` 主體**
   - Reason：CLAUDE.md 常駐禁令。SettingsActivity L159 的 Intent 行可保留。
9. **不把 `.env` 的 NEWEBPAY_* 注入機制改寫**（只刪 field、不改 `localProps` 讀取邏輯）
   - Reason：同一套邏輯 S12-3 還會用於 OPENAI / ANTHROPIC / GIPHY / OPENWEATHER 下放；本輪只清 Newebpay。
10. **不在 S12-1 / S12-2 碰 Supabase schema / migration / RLS**
    - Reason：gate verdict F 明文禁區。

---

## G. Runtime 契約要求（給 CCA contract plan 參考）

從 runtime 視角，對 Insight 後端 `POST /zcoin/topup/sign` API 的要求：

| 項 | Runtime 需求 | 理由 |
|---|---|---|
| G-1 | **Response 不回 HashKey / HashIV**（即便 debug build） | 否則 Android 端仍能從 log / network trace 抓到 |
| G-2 | **orderId 由 server 端生成並綁 user**，client 不傳 orderId | 防重放；client 只傳 amount / email |
| G-3 | **tradeInfo / tradeSha 當場 render 在 Insight 頁的 form**，不回到 Android app | 當前設計是 Custom Tab 打開 Insight 頁，不需要把 sign response 回 Android |
| G-4 | **NotifyURL 只接受來自藍新的 signature 驗證**（server 端） | 防偽造 callback |
| G-5 | **使用者身份以 Insight session cookie 為準**，Android 端不傳 JWT 給 sign API | Custom Tab 已共享 Chrome cookie，最省心 |
| G-6 | Insight 錢包頁面支援 `?return_app=yukiboard` 類 query，付款完成後可 deep link 回 app（可選） | 僅為 UX，非 runtime 硬需求 |

> 這 6 條是 **CCB runtime 視角** 給 CCA 的提議；CCA contract plan 可採納、駁回、或與 Insight 團隊協商後修改。最終由玄樞 gate 裁定。

### G.7 對董事長開放問題 D.1-1 / D.1-2 的 runtime 依賴

| 開放問題 | 若答 A | 若答 B | CCB 立場 |
|---|---|---|---|
| **D.1-1 Newebpay server 歸屬** | Insight 團隊做 → CCB plan §C.3 可直接照走 | YukiBoard 自建後端 → 須另立 sprint 規畫 web service、CI、dns | 強烈偏向 A（Insight 已有 `/zcoin/*` routing） |
| **D.1-2 release 立即剝除 payment key 預設值** | 是 → S12-2 build.gradle 徹底 delete 三行；debug build 也拿不到 | 否 → S12-2 只 delete `.kt`，build.gradle 留空字串作為兼容，之後 sprint 再剝 | 強烈偏向「是」（因 dead code 已 100% 確認，沒有 client 需要 fallback） |

**若 CCA / 董事長選 A + 是：** S12-2 CCB runtime 工作變為純刪除，`.kt` 減 3 檔、`build.gradle` 減 3 行、風險極低。

**若 CCA / 董事長選 A + 否：** S12-2 CCB runtime 仍刪 `.kt`，但 `build.gradle` 暫留空 default；CI gate（S12-3）補掃描。

**若 CCA / 董事長選 B：** S12-1 只能出紙稿，S12-2 解鎖條件變成「YukiBoard 後端專案啟動」；此情況下鍵律建議 Sprint 12 在此暫停 payment 線、轉走 S12-4 PIN hardening。

---

## H. Runtime Plan 驗證 Checklist（供 CCA 並案與 S12-2 起手時用）

- [ ] grep 再確認 `currency/` 三檔 0 caller（本 plan 已驗證 2026-04-19）
- [ ] `ZCoinWalletActivity` 唯一被 `SettingsActivity:159` 使用
- [ ] `app/build.gradle:62-65` 為 `NEWEBPAY_*` 四行定義（實際是 3 行 L63-65；L62 是 `// Newebpay 金流` 註解或 buildConfigField 起始）
- [ ] `.env.example` 是否含 `NEWEBPAY_*`（未在本 plan 中開檔驗證，留給 S12-2 實作時 check）
- [ ] Insight 後端 `/insight-admin/{tenantId}/zcoin` 路由確實存在且可付款（需與 Insight 團隊確認）
- [ ] S12-5 `ErrorReporter` 契約若提前建立（gate verdict D.4-8），本 plan §E.3 的三個事件名可直接 port

---

## I. 交付者自白

- Role check：本文件 scope 嚴守 `S12-1-CCB-NEWEBPAY-RUNTIME-PLAN` — runtime / 檔案邊界 / 最小 file scope，不涉及 CCA 負責的產品流程、UX 文案、release contract、server/client 分工「語意定義」。
- **重要發現未越代**：B.1 揭示 `currency/` 三檔 dead code，此結論對 CCA contract plan 有重大影響（可直接刪除而非重寫），但**本 plan 不替 CCA 改寫 contract**，僅把事實列出供雪鑰 CCA 並案時採用。
- 文件型：plan / contract，**不改 `.kt`**，符合 S12-1「先 plan 不 implementation」原則。
- 不自排順序（`feedback_gate_no_self_schedule.md`）：§D.1 / §D.2 僅列候選，S12-2 的實際執行順序、D.1-1 / D.1-2 的採答、是否採納 G-1 ~ G-6 runtime 提議，全由玄樞 gate + CCA 並案時拍板。
- 無 `.kt` 改動，git status 預期僅新增 `docs/SPRINT12_1_CCB_NEWEBPAY_RUNTIME_PLAN_2026-04-19.md`。

**提交：** 鍵律 → 雪鑰（並案）→ 玄樞 gate
**狀態：** `READY_FOR_REVIEW`

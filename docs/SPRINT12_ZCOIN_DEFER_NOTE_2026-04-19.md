# Z幣整合暫緩記錄（Sprint 12-1 後續）

日期：2026-04-19
身份：`雪鑰`（CCA）
決策來源：董事長 2026-04-19 指示「Z 幣後面做」

---

## 1. 暫緩決策

- S12-1 gate verdict（修訂版）已通過 → `READY`
- 但 **S12-2 Android Client Payment Refactor 暫不進行**
- 原因：董事長拍板 Z 幣相關互通延後到後續 Sprint 處理
- 影響：CH-1 順序中 **S12-2 跳過**，直接進 S12-4（Remote Keyboard PIN Hardening）

修訂後 CH 順序：
```
~~S12-1~~ → ~~S12-2~~（DEFERRED）→ S12-4 → S12-3 → S12-5
```

---

## 2. Z 幣現狀（凍結快照）

### 2.1 YukiBoard 端（scaffold，0 caller）

```
app/src/main/java/com/snowrealm/yukiboard/currency/NewebpayClient.kt   ← P0：含 client-side AES signing + HashKey/IV
app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinManager.kt     ← EncryptedSharedPreferences 錢包，含明文 fallback
app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinApi.kt         ← 直連 Supabase RPC（zcoin_deduct 等）
app/src/main/java/com/snowrealm/yukiboard/ui/currency/ZCoinWalletActivity.kt  ← Custom Tab 跳 Insight admin 頁
app/build.gradle:63-65                                                 ← NEWEBPAY_MERCHANT_ID/HASH_KEY/HASH_IV buildConfigField
```

**使用中**（非 dead code）：
- `ZCoinWalletActivity` 由 `SettingsActivity.kt:159` 串接（「Z 幣錢包」按鈕）
- `InsightSurveyClient.kt:27/56/73` 已消費 Insight 回傳的 `reward_zcoin` 欄位

### 2.2 Insight 端（已完整實作）

**儲值鏈路：**
```
(public)/zcoin-shop/page.tsx                  ← 4 個 package 選擇頁
  RECHARGE_PACKAGES (page.tsx:322-327)
    starter 30 NTD / 150 Z幣
    basic   90 NTD / 500 Z幣（最划算）
    popular 290 NTD / 1750 Z幣（推薦）
    value   690 NTD / 4500 Z幣（超值）

api/payment/zcoin-recharge/route.ts           ← cookie 驗 → createPaymentData → Newebpay POST 表單
api/payment/notify/route.ts                   ← 驗簽 + 冪等 + 金額驗證 + earnZcoin 入帳
api/payment/return/route.ts                   ← 使用者付款後返回頁
lib/newebpay.ts                               ← AES/CBC/PKCS5Padding + SHA256
lib/zcoinEngine.ts                            ← 核算引擎
```

**餘額/消費鏈路：**
```
(public)/wallet/page.tsx                      ← /wallet 餘額顯示頁
api/zcoin/route.ts                            ← GET balance + history
api/zcoin/spend/                              ← POST 消費扣款
api/zcoin/tasks/                              ← 任務獎勵
insight-admin/[tenantId]/zcoin/               ← tenant 管理頁（YukiBoard ZCoinWalletActivity 當前 Custom Tab 目標）
super-admin / 後台 zcoin 管理                 ← 跨 tenant 操作
supabase/migration_yukiboard_integration.sql  ← reward_zcoin 欄位 schema
```

---

## 3. 接通最小路徑（暫不執行，存檔）

### 3.1 一行變更

```
app/src/main/java/com/snowrealm/yukiboard/ui/currency/ZCoinWalletActivity.kt
  Custom Tab 目標 URL 從：
    $INSIGHT_API_BASE_URL/insight-admin/$tenantId/zcoin
  改為：
    $INSIGHT_API_BASE_URL/zcoin-shop
```

理由：`/zcoin-shop` 是面向使用者的儲值入口（顯示 4 個 package + 餘額卡 + 邀請賺 Z 幣 + 交易紀錄連結）；`/insight-admin/{tenantId}/zcoin` 是 admin 後台管理頁，不是使用者買 Z 幣的地方。

### 3.2 阻擋項（董事長 / Insight 團隊需確認）

| # | 項目 | 確認方 | 阻擋原因 |
|---|---|---|---|
| 1 | Insight production 環境 `MIG_MERCHANT_ID` / `MIG_HASH_KEY` / `MIG_HASH_IV` 是否設好 | 董事長 / Insight DevOps | `zcoin-recharge/route.ts:48-58` 會檢查；缺一即顯示「付款功能尚未開通」錯誤頁 |
| 2 | 藍新金流商店是否已正式簽約上線（test → prod 切換） | 董事長 | 決定 env var 用 `MIG_*` (staging) 或 `NEWEBPAY_*` (prod) |
| 3 | Insight `(public)/zcoin-shop` 頁面在 production 是否公開可達（無 admin gating） | Insight 團隊 | 公開頁 (public) 通常 ok，但要確認 |

### 3.3 後續 wire-up（Sprint 13+ 候選）

若董事長未來決定做 in-app balance widget / 整合餘額顯示：

```
ZCoinApi.kt
  原：SupabaseClient.rpc("zcoin_deduct" / "zcoin_balance")  ← 直連 Supabase
  改：InsightApiClient.get("/api/zcoin")                     ← 走 Insight HTTP API
       InsightApiClient.post("/api/zcoin/spend", body)

ZCoinManager.kt
  syncBalance() → 透過新版 ZCoinApi（已是 Insight）
  topUp()       → 撤除（充值由 Insight server 處理，client 不寫餘額）
  spend()       → 透過新版 ZCoinApi

UI（選配）
  SettingsActivity「Z 幣錢包」按鈕文字加餘額：「Z 幣錢包（X）」
  IME lifecycle hook：開鍵盤時 syncBalance
```

**Auth 注意**：
- `/api/zcoin*` 系列吃哪種驗證？目前看到 `zcoin-recharge` 是 cookie；其他 endpoint 待確認。
- 若也是 cookie → Android in-app 互通需設計 cookie sync 或 Insight 加 Bearer 支援。
- 若已支援 Bearer → `InsightApiClient` 現有機制可直接套用。

---

## 4. P0 secret 風險目前狀態

**未處理。** S12-1 gate verdict 修訂版規劃在 S12-2 處理，但 S12-2 暫緩 → 此 P0 仍開放：

```
app/build.gradle:63-65
  NEWEBPAY_HASH_KEY / NEWEBPAY_HASH_IV 真值可能仍隨 release APK 打包
  → APK 反編譯可還原 → 攻擊者可偽造交易（風險範圍：藍新商店）
```

**緩解選項**（董事長需擇一）：
- **A. 立即把 build.gradle:63-65 真值改空注入**（最小改動，1 行修，不改 .kt） → 但要先確認當前 release APK 是否確實打入 `.env` 內的真 secret
- **B. 確認 `.env` 內無真 HashKey/IV**（若本地開發機根本沒設 → 釋出 APK 也是空的 → 無風險，可暫不動）
- **C. 全留待 Sprint 13+ 與 Z 幣 wire-up 一併處理**（接受目前 P0 風險）

**建議**：先 grep `.env` 確認當前真值狀態（5 秒事），若有真值即採 A，若無真值即採 C。

---

## 5. 已交付文件清單（Z 幣相關）

```
docs/SPRINT12_0_CCA_PAYMENT_KEY_CONSUME_AUDIT_2026-04-19.md  ← S12-0 雪鑰：含 ZCoin 在 consume 端的 audit
docs/SPRINT12_0_CCB_REMOTE_SECURITY_AUDIT_2026-04-19.md      ← S12-0 鍵律：§D-1 Newebpay HashKey/IV runtime 風險
docs/SPRINT12_0_GATE_VERDICT_2026-04-19.md                   ← S12-0 合併 verdict
docs/SPRINT12_1_CCA_NEWEBPAY_CONTRACT_PLAN_2026-04-19.md     ← S12-1 雪鑰：contract / release policy
docs/SPRINT12_1_CCB_NEWEBPAY_RUNTIME_PLAN_2026-04-19.md      ← S12-1 鍵律：runtime / file scope
docs/SPRINT12_1_GATE_VERDICT_2026-04-19.md                   ← S12-1 合併 verdict（修訂版，含 Insight 實作地圖）
docs/SPRINT12_ZCOIN_DEFER_NOTE_2026-04-19.md                 ← 本檔
```

---

## 6. 解凍條件（恢復 Z 幣 wire-up 工作的觸發點）

任一條件成立後重新檢視：

1. 董事長明確指示恢復 Z 幣整合（例如「現在做 Z 幣 in-app 餘額」）
2. Insight production 環境 Newebpay env var + 商店簽約完成 → 通知 YukiBoard 端做 wire-up
3. 使用者反映「Z 幣錢包按鈕跳出去看不到充值方案」（當前 `ZCoinWalletActivity` 跳 admin 頁，使用者體驗錯位）→ 觸發 §3.1 一行 URL 變更
4. Sprint 13+ 規劃時，董事長決定把「Android in-app balance widget」列入 sprint 主題

恢復時，本檔 §3.3 的 wire-up 規格直接套用；§4 的 P0 緩解若採 C 則一併在 wire-up 時處理。

---

## 7. 不要做的事（防止後續 sprint 誤判）

- ❌ 不要把 `currency/` 三檔當 dead code 刪除（董事長 2026-04-19 明確指示「ZCoin 不可刪」）
- ❌ 不要在 YukiBoard client 端做 Newebpay AES 簽章（永久禁止，由 Insight server 處理）
- ❌ 不要讓 YukiBoard client 直連 Supabase 寫 zcoin_wallets（必須走 Insight `/api/zcoin*`）
- ❌ 不要在 S12-2 暫緩期間把 `ZCoinWalletActivity` 的 Custom Tab URL 改掉（除非董事長明確同意 §3.1）
- ❌ 不要把 `NEWEBPAY_*` env var 從 `.env` / `build.gradle` 整欄刪除（保留欄位供未來 wire-up；只處理真值，見 §4）

---

## 8. 交付者自白

- 本檔由 `雪鑰`（CCA）落地，純記錄與決策存檔，不涉及 .kt 變更。
- S12-1 gate verdict 仍為 READY，但其 §C 規劃的 S12-2 scope 不執行（董事長 Z 幣後做指示）。
- Sprint12-All-Round-Gate.md 將同步更新 S12-2 狀態為 `DEFERRED` 而非 `ACTIVE`，並把 CH 跳到 S12-4。

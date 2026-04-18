# Sprint 12-5 Gate Verdict — Observability Hook

日期：2026-04-19
Gate 身份：`雪鑰`（CCA 代行 `玄樞`）
審查對象：
- `docs/SPRINT12_5_CCB_OBSERVABILITY_HOOK_IMPLEMENTATION_2026-04-19.md`（鍵律 CCB）
- `docs/SPRINT12_5_CCA_OBSERVABILITY_CONTRACT_NOTE_2026-04-19.md`（雪鑰 CCA）
CH 順序：CH-1 最末門 ~~S12-1~~ → ~~S12-2 DEFERRED~~ → ~~S12-4~~ → ~~S12-3~~ → **S12-5**

---

## A. 結論

**Gate 判定：`READY`**

1. CCB 落地 facade（`telemetry/Telemetry.kt` 9 行）+ 5 處 consumer wire（AI prefs / ZCoin prefs / Invite prefs + dev-secret guard / Newebpay sign-fail / BT adapter & HID register fail），編譯通過（`compileDebugKotlin BUILD SUCCESSFUL in 5s`）。
2. CCA review 確認：5 處 wire site + facade 全部符合 S12-0 §E-3 audit + S12-3 §E.2 runtime logging 紅線；7 個 TelemetryEvent 全部 PII-safe（exception class simple name / 結構化 boolean，無 device name / IP / userId / API key / stack message / 輸入文字 leakage）。
3. CCB §D.1 四項開放問題 CCA 全部裁決完成（不轉 user-visible alert / extras forward rule / S13+ opt-in contract / wording 慣例），未推回董事長。
4. 無 BLOCKING / STRONG findings；2 項 MINOR_OPTIONAL（forward extras rule KDoc / dev-secret guard 移到 Application.onCreate）皆可延後 S13+ Crashlytics swap round 一併處理。
5. 本輪 scope 嚴守 Path B：未引入 Crashlytics / google-services / firebase plugin / consent UX 實作；未越擴 build-infra。

**S12-5 結案。Sprint 12 主目標 §5 全部覆蓋（見 §E）。**

下一候選：S12-3.1 實作 round（須先解 §J.4 #1-2 仲裁）/ Sprint 12 收尾進 Sprint 13，由董事長拍板。

---

## B. CCB 落地核對

### B.1 新增檔

| 檔 | 行數 | 用途 | CCA 核對 |
|---|---|---|---|
| `telemetry/Telemetry.kt` | 9 | Application.onCreate swap 點 facade | ✅ 無 Context 依賴 / `@Volatile` thread-safe / 預設 `AndroidLogReporter` |

### B.2 修改檔（5 處 wire）

| # | 檔案:行 | 事件 (severity / code) | extras | CCA PII 評估 |
|---|---|---|---|---|
| 1 | `AIProviderManager.kt:49-54` | ERROR / `AI_KEY_PREFS_FALLBACK_PLAIN` | `exception=<ClassName>` | ✅ |
| 2 | `ZCoinManager.kt:35-40` | ERROR / `ZCOIN_PREFS_FALLBACK_PLAIN` | `exception=<ClassName>` | ✅ |
| 3a | `InviteCodeManager.kt:36-41` | ERROR / `INVITE_PREFS_FALLBACK_PLAIN` | `exception=<ClassName>` | ✅ |
| 3b | `InviteCodeManager.kt:46-52` | ERROR / `INVITE_DEV_SECRET_DETECTED` | （無） | ✅ |
| 4 | `NewebpayClient.kt:53-58` | ERROR / `NEWEBPAY_SIGN_FAIL` | `exception=<ClassName>` | ✅ |
| 5a | `BluetoothHidKeyboard.kt:192-197` | WARN / `BT_ADAPTER_UNAVAILABLE` | `adapterNull=<bool>` | ✅ |
| 5b | `BluetoothHidKeyboard.kt:220-224` | ERROR / `BT_HID_REGISTER_FAIL` | （無） | ✅ |

### B.3 隱含 signature 改動（NewebpayClient.buildPaymentParams）

CCB 自承新增 try/catch 包住 `aesEncrypt`，行為從 exception propagate → silent null + telemetry log。CCA 評估：caller 已有 `if (!isConfigured) return null` null-check 慣例；ZCoin DEFERRED 中無 caller 真觸發；視為可接受 trade-off。**不阻擋**。

---

## C. CCB 4 項開放問題裁決確認

| # | CCB 問 | CCA 裁決 | Gate 確認 |
|---|---|---|---|
| Q1 | `INVITE_DEV_SECRET_DETECTED` 是否轉 user-visible alert | 不轉；根因是 build supply-chain failure，user 無法行動；真正解法在 S12-3 §K.2.2 build 端 fail-build | ✅ 採納 |
| Q2 | extras 隱私審 / cloud redaction | 當前全 PII-safe；提案 Forward Rule（允許 exception class name / 結構化 bool / enum / 數值範圍 / stable code；禁 message / stack / 自由文本 / device 標識 / userId / 輸入內容） | ✅ 採納（Forward Rule 列為 contract，KDoc 落地由 D.3-1 處理） |
| Q3 | S13+ Crashlytics opt-in UX / `ConsentAwareErrorReporter` wrapper | opt-in default OFF / SettingsActivity「隱私與安全」section /「協助我們改善」switch / `ConsentAwareErrorReporter(real, consentProvider)` wrapper 設計 contract 已敲；本輪不實作 | ✅ 採納（屬 S13+ round contract） |
| Q4 | error wording 語意 | 內部 codes 維持英文大寫底線（stable identifier）；severity 三級慣例（ERROR=功能失敗/安全紅線；WARN=可預期 fallback；INFO=觀測）；user-visible 文案候選列 S13+ | ✅ 採納 |

CCA 裁決面對 4 問皆給出明確答案 + contract，無「推回董事長」項。Gate 確認 4 問結案。

---

## D. MINOR_OPTIONAL（不阻擋 gate，存檔）

### D.3-1 Forward extras rule 寫入 KDoc

- 位置：`telemetry/Telemetry.kt` 或 `telemetry/ErrorReporter.kt` 檔頭
- 內容：CCA §C.2 兩欄允許 / 禁用表
- Gate 處理：**延後到 S13+ Crashlytics swap round** 一併補。理由：本輪 facade 設計已固定，KDoc 為文檔級補強，不影響執行；S13+ 必動同檔，一起改更省事。

### D.3-2 `INVITE_DEV_SECRET_DETECTED` 移到 Application.onCreate

- 理由：避免每次 InviteCodeManager 建構 fire（多 caller × 多 ERROR upload quota）
- Gate 處理：**延後到 S13+ Crashlytics swap round**。理由：本輪 logcat-only，多次 fire 無 quota 壓力；S13+ Crashlytics 接入時必檢視所有 fire 頻率，那時一併處理。

兩項皆不阻擋本門 gate。

---

## E. Sprint 12 主目標覆蓋確認

| §5 主目標 | 覆蓋情況 | 落地點 |
|---|---|---|
| §5.1 不再把支付核心 secret 留在 APK | ⚠ **部分**：S12-1 plan 敲 + S12-3 release policy 敲；**S12-2 DEFERRED 中**（Z 幣後做），實際 secret 抽離未落地。S12-5 補 `NEWEBPAY_SIGN_FAIL` telemetry 為運維側可見性。 | S12-1 / S12-3 / S12-5 |
| §5.2 高風險 key 有明確 release policy | ✅ S12-3 7-key inventory + P0/P1/P2 + release contract 全部敲 | S12-3 |
| §5.3 remote keyboard 最低安全基線提升 | ✅ S12-4 6-digit SecureRandom PIN + brute-force tracker + session token + panel PIN 顯示 | S12-4 |
| §5.4 觀測接點有明確落地 | ✅ S12-4 ErrorReporter interface + S12-5 facade + 5 處 wire | S12-4 / S12-5 |

**Sprint 12 整體：4 主目標中 3 完全覆蓋、1 部分覆蓋（S12-2 DEFERRED 為已知遺留）。**

P0 Newebpay HashKey/IV 在 release APK 風險未閉環，列為 `docs/SPRINT12_ZCOIN_DEFER_NOTE_2026-04-19.md` §4 開放項，待 Sprint 13+ 與 Z 幣 wire-up 一併處理。

---

## F. Gate 輸出動作

`READY` 判定下：

1. 更新 `Sprint12-All-Round-Gate.md`：
   - S12-5 狀態 `ACTIVE` → `COMPLETED`（採 Sprint11 全行刪除線格式）
   - §6 玄樞預設判定：`S12-5 ACTIVE` → `S12-5 COMPLETED`
   - 補 reason #16 記錄 S12-5 結案 + Sprint 12 主目標覆蓋總結
2. 本輪對外 user-visible 改動：**無**（全為 logcat-only，使用者無感）
3. 對運維 / dev：
   - 5 個關鍵錯誤事件現在有 structured logcat 輸出（`Telemetry/<CATEGORY>` tag）
   - release 出貨若 `INVITE_HMAC_SECRET` 為 dev 預設，logcat 會看到 `INVITE_DEV_SECRET_DETECTED` ERROR（QA 自驗點）
   - S13+ 換 Crashlytics 只動 1 行：`Telemetry.reporter = ConsentAwareErrorReporter(CrashlyticsReporter(this), settings::isTelemetryConsented)`
4. 無使用者需要做的動作 — 升級後自動生效。

---

## G. Sprint 12 進度快照（最終）

```
S12-0 COMPLETED  Payment / Key Audit
S12-1 COMPLETED  Newebpay Server-side Signing Plan（修訂：ZCoin 不可刪）
S12-2 DEFERRED   Android Client Payment Refactor（Z 幣後做）
S12-4 COMPLETED  Remote Keyboard PIN Hardening
S12-3 COMPLETED  API Key Release Policy（policy round；S12-3.1 實作 round 待開）
S12-5 COMPLETED  Observability Hook  ← 本輪結案
```

**Sprint 12 5/6 round COMPLETED + 1 DEFERRED；CH-1 順序全跑完。**

**下一候選 round（待董事長拍板）：**
- S12-3.1 實作 round（合併 9 項；2 項待 §J.4 #1-2 仲裁：Whisper precedence 反轉 / 預設 provider 改 SNOWREALM）
- Sprint 12 收尾文件 + 進 Sprint 13（含 ZCoin wire-up / Crashlytics swap / S13+ auth round / Whisper round 等候選）

---

## H. 交付者自白

- 本 gate 由 `雪鑰`（CCA）代行 `玄樞`；gate 只做 gate，不實作；不再次 run 編譯（CCB 已報 BUILD SUCCESSFUL）。
- Final pass 以 diff 核對 + CCA review 對接為主，不重寫任何 .kt。
- CCB 4 問 CCA 全部回應；無未決問題推回董事長。
- 2 項 MINOR_OPTIONAL（D.3-1 / D.3-2）延後 S13+，不開新二次修 round。
- 不自排下一 round；S12-3.1 / Sprint 13 啟動由董事長拍板。
- 本 gate verdict + CCB / CCA 兩份子文件構成 S12-5 完整交付鏈。

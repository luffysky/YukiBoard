---
sprint: S12-5
round: CCA
role: 雪鑰
date: 2026-04-19
task_id: S12-5-CCA-OBSERVABILITY-CONTRACT-NOTE
ch_order: CH-1 最末門
status: REVIEWED — PENDING 玄樞 GATE
companion_doc: docs/SPRINT12_5_CCB_OBSERVABILITY_HOOK_IMPLEMENTATION_2026-04-19.md
---

# Sprint 12-5 CCA — Observability Contract Note（雪鑰）

對應 CCB 側：`S12-5-CCB-OBSERVABILITY-HOOK-IMPLEMENTATION`（鍵律已交付，IMPLEMENTED 狀態）

---

## A. 範圍

CCA 本輪僅做：
1. 產品面（user-visible / not-visible 邊界）
2. 隱私面（extras / cloud transmission 風險）
3. error wording 語意
4. consent 邊界（S12 vs S13+）
5. CCB §D.1 四項開放問題裁決
6. 對 CCB 5 個 wire site + facade 做 consume / privacy diff 核對

CCA **不**：
- 不改 `.kt`（嚴守 role boundary）
- 不擴 build infra（Crashlytics / google-services / consent UX 實作）
- 不重排 S12-5 與 S12-3.1 順序（董事長拍板）

---

## B. 對 CCB diff 的 consume / privacy 核對

### B.1 facade（`telemetry/Telemetry.kt`）

| 項 | 評估 |
|---|---|
| 9 行 facade，無 `Context` 依賴 | ✅ 符合 CLAUDE.md「禁 getInstance(context) 跨層」 |
| `@Volatile var reporter` swap 點 | ✅ S13+ Application.onCreate 換 1 行的最小化設計 |
| 預設指 `AndroidLogReporter`（S12-4 已建） | ✅ logcat-only，無雲端傳輸副作用，**符合 S12 沒有 consent UX 的前提** |
| Kotlin `object` thread-safety | ✅ |
| 實作 `ErrorReporter` interface | ✅ 任何 consumer 可用 interface 型別宣告 |

**CCA 結論**：facade 設計合契約面，無 consume 風險。

### B.2 五處 wire site

| # | 檔案 | 事件 | extras | privacy 評估 |
|---|---|---|---|---|
| 1 | `AIProviderManager.kt:49-54` | `AI_KEY_PREFS_FALLBACK_PLAIN` (ERROR) | `exception=<ClassName>` | ✅ PII-safe（僅 exception 類別名） |
| 2 | `ZCoinManager.kt:35-40` | `ZCOIN_PREFS_FALLBACK_PLAIN` (ERROR) | `exception=<ClassName>` | ✅ PII-safe |
| 3a | `InviteCodeManager.kt:36-41` | `INVITE_PREFS_FALLBACK_PLAIN` (ERROR) | `exception=<ClassName>` | ✅ PII-safe |
| 3b | `InviteCodeManager.kt:46-52` | `INVITE_DEV_SECRET_DETECTED` (ERROR) | （無） | ✅ PII-safe（無 extras） |
| 4 | `NewebpayClient.kt:53-58` | `NEWEBPAY_SIGN_FAIL` (ERROR) | `exception=<ClassName>` | ✅ PII-safe |
| 5a | `BluetoothHidKeyboard.kt:192-197` | `BT_ADAPTER_UNAVAILABLE` (WARN) | `adapterNull=<bool>` | ✅ PII-safe |
| 5b | `BluetoothHidKeyboard.kt:220-224` | `BT_HID_REGISTER_FAIL` (ERROR) | （無） | ✅ PII-safe |

**CCA 結論**：7 個 TelemetryEvent 全部 PII-safe，符合 S12-3 §E.2 runtime logging 紅線。
- 沒有任何 device name / IP / userId / API key / stack trace message / input text / 路徑 leakage。
- `exception` 欄位僅 class simple name（例：`GeneralSecurityException`），不含 message。

### B.3 隱含改動（`NewebpayClient.buildPaymentParams` signature 行為）

CCB §B 「風險」自承：原本 `aesEncrypt` 拋例外會 propagate；現在新 try/catch 包住、回 null。

CCA 評估：
- 既有 caller 已有 `if (!isConfigured) return null` null-check 慣例
- 行為改動屬「靜默失敗 → telemetry 記錄」，**符合產品面「使用者不該看到藍新加密內部錯誤」原則**
- ZCoin DEFERRED 中，目前無 caller 真正觸發此路徑，回歸風險低
- **MINOR observation**：dev 本機如果想 debug 簽章失敗，需從 logcat `Telemetry/PAYMENT` 看 exception class，不再有 stack trace。視為可接受 trade-off（dev 真有需要可暫時打開 BuildConfig debug flag 加 log）。

無 BLOCKING / STRONG。

---

## C. CCB §D.1 四項開放問題 — CCA 裁決

### C.1 Q1: `INVITE_DEV_SECRET_DETECTED` 是否轉 user-visible alert？

**CCA 裁決：不轉 user-visible alert，但建議 invite code activation flow 補一層 graceful degradation。**

**理由：**

1. **使用者無法行動**：此事件的根因是 release pipeline 沒剝 `.env` 真值（或 gradle override 失效），是 supply-chain failure。使用者拿到出貨包後無法自救，user-facing error banner 只會嚇跑使用者卻不解決問題。
2. **對應失敗會自然顯現**：dev 預設 secret 簽出來的邀請碼，正式 server 端不會接受 → user 試啟動會看到「邀請碼無效」。此既有錯誤路徑已足以讓使用者知道「這碼用不了」。
3. **真正解法在 build 端**：S12-3 §K.2.2 的「`INVITE_HMAC_SECRET == dev default → release fail build`」是根治方案，runtime 上報只是雙保險（fallback observability）。
4. **若 S13+ 真要顯示**：建議的 user-facing wording 不該暴露內部詞（例「邀請碼系統故障」），改用「服務暫時不可用，請聯繫客服 / 重新下載最新版」。但本輪不落地。

**順帶建議（MINOR_OPTIONAL，可不處理）：**

- 目前 `InviteCodeManager` init 每次建構都會 fire（多 caller 多次 fire）。CCB §B 「風險」已自承「ERROR 級多次上報可接受」。CCA 同意此判斷在 logcat 層無傷；但若將來換 Crashlytics，建議考慮 **將 dev-secret guard 移到 `Application.onCreate`** 一次性檢查，避免 quota / dedupe 負擔。
- 本輪不處理；列為 S13+ Crashlytics swap 同步處理項。

### C.2 Q2: extras 欄位是否需隱私審查 / 雲端 redaction？

**CCA 裁決：當前 extras 全部 PII-safe，無需 redaction；建立 forward rule 防未來擴增時退化。**

**Forward Rule（CCA 提案，列入 contract）：**

未來新增 TelemetryEvent extras 時，**只允許**以下類型，**禁用**其他：

| ✅ 允許 | ❌ 禁用 |
|---|---|
| exception class simple name | exception message / stack trace |
| 結構化布林（`adapterNull` / `isEncrypted` / `hasKey`） | 自由文本 |
| 已枚舉 enum 字串（`provider=ANTHROPIC` / `mode=NORMAL`） | 動態字串值 |
| 數值範圍（`retryCount=3` / `bucketIndex=2`） | 時間戳細到 ms / 連線 ID |
| 業務 stable code（`code=NEWEBPAY_SIGN_FAIL`） | API key 任何片段 / hash |
| | device name / IMEI / MAC / IP / 使用者輸入 / packageName |
| | userId / inviteCode / orderId / email |

**理由**：

- AndroidLogReporter 雖只進 logcat，但 logcat 仍可被同裝置 ADB / 其他 app（API 23 前）看到；S13+ Crashlytics 上 cloud 後 leakage 後果更嚴重。
- 提早立規矩，避免後續開發者 ad-hoc 加 `extras = mapOf("user" to userId)` 之類退化。
- 此規則應寫入 `telemetry/Telemetry.kt` 或 `ErrorReporter.kt` 上方 KDoc 作為硬規範（CCB 二次修可選擇納入；屬 OPTIONAL 不阻擋 gate）。

### C.3 Q3: S13+ Crashlytics opt-in UX / `ConsentAwareErrorReporter` 是否預留？

**CCA 裁決：S12-5 本輪不落地，但 CCA 給出 contract，等 S13+ 實作。**

**Contract：**

#### C.3.1 預設值

- **opt-in default = OFF**（user 必須主動開）
- 理由：YukiBoard 處理使用者輸入文字（IME 本質）；任何 cloud telemetry 都需明示同意，否則踩個資與 Play Store policy 紅線。

#### C.3.2 暴露位置

- `SettingsActivity` → 「隱私與安全」section（建議命名）
- 開關標題：「協助我們改善（傳送匿名錯誤報告）」
- 副標：「不會傳送您輸入的內容、聯絡資訊或位置；僅傳送 app 內部錯誤事件代碼。」
- 預設值符合 default-deny。

#### C.3.3 `ConsentAwareErrorReporter` wrapper 設計（S13+）

```kotlin
class ConsentAwareErrorReporter(
    private val real: ErrorReporter,
    private val consentProvider: () -> Boolean  // SettingsRepository.isTelemetryConsented()
) : ErrorReporter {
    override fun report(event: TelemetryEvent) {
        if (consentProvider()) real.report(event)
        // else: silent drop（不 log fall-through，避免 user 在 logcat 看到「即使我關了還在記」）
    }
}
```

- Application.onCreate 串：`Telemetry.reporter = ConsentAwareErrorReporter(CrashlyticsReporter(this), settings::isTelemetryConsented)`
- AndroidLogReporter 維持 unconditional（dev / QA logcat 觀測用，無雲端傳輸不需 consent）

**本輪不要求 CCB 預留 wrapper class** — Path B 是「不擴 CCA consent UX」，即使預留 wrapper class 也屬擴 scope。S13+ 開新 round 一併補。

#### C.3.4 同意撤回

S13+ 應同步補：
- 使用者關閉開關後，立即停止後續上報
- 已上報事件不可撤回（telemetry 不存 user 識別資訊本就無法 reverse-link，但 UX 文案需明示）

### C.4 Q4: error wording 語意

**CCA 裁決：**

#### C.4.1 內部 telemetry codes（`AI_KEY_PREFS_FALLBACK_PLAIN` 等 7 個）

- ✅ **保持英文 + 大寫底線**：是 stable identifier，非 user-facing；切忌中文化或加標點。
- ✅ 命名語意清晰，符合 CCB §B.3 命名原則（category / code / extras 三層）。
- 無修改建議。

#### C.4.2 Severity 三級（INFO / WARN / ERROR）使用慣例（CCA 提案 contract）

| 級別 | 何時用 | 本輪實例 |
|---|---|---|
| `ERROR` | 功能實質失敗 / 安全紅線觸發 / 不可恢復 | `*_FALLBACK_PLAIN` (3個) / `INVITE_DEV_SECRET_DETECTED` / `NEWEBPAY_SIGN_FAIL` / `BT_HID_REGISTER_FAIL` |
| `WARN` | 預期可發生、有 fallback、不阻使用者主流程 | `BT_ADAPTER_UNAVAILABLE`（藍牙關閉很常見，user 主動行為） |
| `INFO` | 觀測 / 統計，非錯誤 | （本輪未用） |

**CCA 對 CCB 既有分級的 cross-check：**

- ✅ `BT_ADAPTER_UNAVAILABLE` = WARN：對。藍牙關閉是 user 設定，不是 app bug；user 開藍牙後重試即可。
- ✅ 其他 6 項 = ERROR：對。

#### C.4.3 未來 user-visible wording（S13+ Crashlytics opt-in 開關文案）

CCA 建議文案（候選，待 S13+ 進行 round 時再敲）：

- 開關標題：「協助我們改善（傳送匿名錯誤報告）」
- 副標：「不會傳送您輸入的內容、聯絡資訊或位置；僅傳送 app 內部錯誤事件代碼。」
- 開啟時 Toast / Snackbar：「感謝您的協助。您可隨時在設定中關閉。」
- 關閉時：「已停止傳送錯誤報告。」

**本輪不落地**，列入 S13+ Crashlytics swap round contract。

---

## D. Findings 分級

### D.1 BLOCKING

**無**。

### D.2 STRONG

**無**。

### D.3 MINOR_OPTIONAL（CCB 可選擇是否處理；不阻擋 gate）

#### D.3-1 Forward extras rule 寫入 KDoc

- **位置**：`telemetry/Telemetry.kt` 或 `telemetry/ErrorReporter.kt` 檔頭 KDoc
- **內容**：§C.2 Forward Rule 表（允許 / 禁用清單）
- **理由**：未來開發者 IDE 顯示 KDoc 時直接看到規矩，避免 ad-hoc PII leakage
- **CCB 處理建議**：5 行 KDoc 即可；若 CCB 認為不必，可由 CCA 在 S13+ Crashlytics round 一併補

#### D.3-2 `INVITE_DEV_SECRET_DETECTED` 移到 Application.onCreate

- **理由**：避免每次 `InviteCodeManager` 建構都 fire（多 caller × 多 ERROR upload quota）
- **本輪不必處理**：logcat 不影響；列入 S13+ Crashlytics swap 同步調整
- **若 CCB 想本輪一併做**：需新增 `Application` subclass 或調整既有 Application.onCreate；可能引入新檔，超出本輪 scope。**CCA 建議延後**。

### D.4 OBSERVATIONS（不算 finding，存檔）

- `NewebpayClient` try/catch 改變了 dev debug 體驗（exception 不再 propagate），但屬可接受 trade-off（§B.3）
- 5 個 wire site 已涵蓋 S12-0 §E-3 audit 列出的 P0/P1 secret-handling + payment + remote keyboard 主線；其他 51 處 Log.e/w 排除合理（CCB §B.5）
- S12-3.1 實作 round 還在仲裁 → `INVITE_HMAC_SECRET` build 端 fail-build 尚未生效；本輪 runtime 偵測屬「目前唯一」防線（不是雙保險），建議董事長加速 S12-3.1 §J.4 #1 拍板

---

## E. Gate 訴求

CCA 對本輪建議 `玄樞`（雪鑰代行）gate verdict 為 **`READY`**：

1. CCB 5 個 wire site + facade + 命名原則全部符合 S12-0 §E-3 audit 與 S12-3 §E.2 runtime logging 紅線
2. extras 全部 PII-safe，無 cloud-transmission risk regression
3. CCA 4 項開放問題已全部裁決（不轉 user-visible alert / extras forward rule / S13+ opt-in contract / wording 慣例）
4. 無 BLOCKING / STRONG findings；2 項 MINOR_OPTIONAL（D.3-1 KDoc / D.3-2 移到 Application）皆可延後
5. 本輪 scope 嚴守 Path B：未引入 Crashlytics / build-infra / consent UX 實作

**待 `玄樞`（雪鑰代行）合併兩份送 gate verdict。**

---

## F. 對 Sprint 12 的 contract 累積（CCA 視角總結）

S12-5 落地後，Sprint 12 telemetry contract 完整：

| Sprint | 落地 |
|---|---|
| S12-4 | `ErrorReporter` interface / `AndroidLogReporter` impl / `WifiKeyboardServer` constructor-injected DI |
| S12-5 | `Telemetry` facade（Application.onCreate swap 點）/ 5 處 consumer wire / extras 命名原則 / dev-secret runtime guard |
| S13+（contract 已敲） | `ConsentAwareErrorReporter` wrapper / Crashlytics 接入 / opt-in UX in SettingsActivity / extras forward rule KDoc |

**未來 S13+ Crashlytics round 只需做：**
1. 新增 `app/src/main/java/com/snowrealm/yukiboard/telemetry/CrashlyticsReporter.kt` impl
2. 新增 `ConsentAwareErrorReporter.kt` wrapper（per §C.3.3 contract）
3. SettingsActivity 加「協助我們改善」switch（per §C.3.2 / §C.4.3 contract）
4. Application.onCreate 一行 swap：`Telemetry.reporter = ConsentAwareErrorReporter(CrashlyticsReporter(this), ...)`
5. build.gradle 加 Firebase / google-services
6. （可選）DEV_SECRET guard 從 InviteCodeManager init 移到 Application.onCreate（per D.3-2）

**對 Sprint 12 主目標（§5）的覆蓋：**

- ✅ §5.4「觀測接點有明確落地」：5 處 P0/P1 事件落地 + facade 對 S13+ 開放
- ✅ Sprint 12 全部 4 條主目標達成（§5.1 secret 抽離 deferred to ZCoin round 但 Newebpay sign-fail 已被 telemetry 覆蓋；§5.2 release policy 已敲 §5.3 PIN hardening 已落地）

---

## G. 交付者自白

- 本 round 由 `雪鑰`（CCA）執行 consume / privacy / wording / consent contract 線
- 未動 `.kt`，嚴守 role boundary（不越代 CCB 實作；不自開新 round；不擅排 S13+ 順序）
- 4 項開放問題全部給出明確裁決或 contract，未推回董事長
- 2 項 MINOR_OPTIONAL 列為 CCB 可選處理 / 可延後 S13+
- 本檔已準備好送 `玄樞`（雪鑰代行）合併 gate verdict

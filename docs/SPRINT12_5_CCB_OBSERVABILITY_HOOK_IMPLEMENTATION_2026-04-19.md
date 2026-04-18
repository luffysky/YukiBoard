---
sprint: S12-5
round: CCB
role: 鍵律 (代行 CodeX B)
date: 2026-04-19
task_id: S12-5-CCB-OBSERVABILITY-HOOK-IMPLEMENTATION
ch_order: CH-1 最末門
status: IMPLEMENTED — PENDING CCA CONSUME/PRIVACY REVIEW
---

# Sprint 12-5 CCB — Observability Hook Implementation（鍵律）

對應 CCA 側（待交付）：`S12-5-CCA-OBSERVABILITY-CONTRACT-NOTE`（雪鑰寫，產品面 / 隱私面 / error wording / consent 邊界）

---

## A. 範圍與決策

### A.1 scope path 決策（董事長拍板 Path B）

S12-0 §F.3 #3 原開放問題「S12-5 是否在 Sprint 12 內接 Firebase Crashlytics？」— 董事長 2026-04-19 拍板：

- **採 Path B**：僅接 consumer points + DI 骨架，**不引入 Crashlytics**
- CrashlyticsReporter 留 S13+ 由 Application.onCreate 替換 `Telemetry.reporter` 即可
- 本輪不擴 build-infra（Firebase / google-services.json / gradle plugin 皆不動）
- 本輪不擴 CCA 側 opt-in consent UX（等 CCA 交付評估後由 §K.4 開放問題判斷是否預留 `ConsentAwareErrorReporter` wrapper）

### A.2 新增 `Telemetry` 模組級 facade

**理由**：S12-0 §E-3 audit 明示 "S13+ 換 CrashlyticsReporter 只要換 Application 初始化"。facade 為該 swap 點的最簡實現。

**設計**：

```kotlin
// app/src/main/java/com/snowrealm/yukiboard/telemetry/Telemetry.kt（新檔）
object Telemetry : ErrorReporter {

    @Volatile
    var reporter: ErrorReporter = AndroidLogReporter

    override fun report(event: TelemetryEvent) {
        reporter.report(event)
    }
}
```

**特性**：
- 無 `Context` 依賴（不違反 CLAUDE.md「禁 getInstance(context) 跨層依賴」）
- Kotlin `object`，thread-safe singleton
- `reporter` 可變（@Volatile）允許 Application.onCreate swap（S13+ 預留）
- 預設指向 `AndroidLogReporter`（S12-4 已建）
- 實作 `ErrorReporter` interface → 任何 consumer 可直接以 interface 型別宣告 collaborator，facade 可被替換
- 不改 S12-4 `WifiKeyboardServer` 的 constructor-injected `errorReporter`（該 pattern 同樣 DI-ready，與 facade 並存不衝突）

### A.3 與 S12-4 既有 telemetry 的關係

| 項 | S12-4 落地 | S12-5 補強 |
|---|---|---|
| `ErrorReporter` interface | ✅ 已建 | 維持，新增 1 個實作入口 `Telemetry` |
| `AndroidLogReporter` object | ✅ 已建 | 維持，作為 `Telemetry.reporter` 預設值 |
| `WifiKeyboardServer` brute-force 事件 | ✅ 已 wire（constructor-injected） | 保留現況；facade 模式留給其他 4 處新 consumer |
| 其他 consumer | — | 本輪新增 5 處 |

---

## B. 實作摘要

### B.1 新增檔

- `app/src/main/java/com/snowrealm/yukiboard/telemetry/Telemetry.kt`（9 行）— facade

### B.2 修改檔（5 處 consumer wire）

| # | 檔案 | 事件 | TelemetryEvent |
|---|---|---|---|
| 1 | `claw/provider/AIProviderManager.kt:44-54` | `EncryptedSharedPreferences` 建立失敗 → fallback `MODE_PRIVATE` | `SECURE_STORAGE / ERROR / AI_KEY_PREFS_FALLBACK_PLAIN`，extras: `exception` |
| 2 | `currency/ZCoinManager.kt:30-41` | 同上（ZCoin prefs） | `SECURE_STORAGE / ERROR / ZCOIN_PREFS_FALLBACK_PLAIN`，extras: `exception` |
| 3 | `auth/InviteCodeManager.kt:31-41 / 44-52` | (a) prefs fallback<br>(b) release build 偵測 `INVITE_HMAC_SECRET == "yukiboard_dev_2026"` | (a) `SECURE_STORAGE / ERROR / INVITE_PREFS_FALLBACK_PLAIN`<br>(b) `AUTH / ERROR / INVITE_DEV_SECRET_DETECTED` |
| 4 | `currency/NewebpayClient.kt:buildPaymentParams` | AES/SHA 簽章失敗（InvalidKey / BadPadding 等） | `PAYMENT / ERROR / NEWEBPAY_SIGN_FAIL`，extras: `exception` |
| 5 | `remote/BluetoothHidKeyboard.kt:start / registerApp` | (a) BT adapter null / disabled<br>(b) `hid.registerApp()` 回 false | (a) `REMOTE_KEYBOARD / WARN / BT_ADAPTER_UNAVAILABLE`，extras: `adapterNull`<br>(b) `REMOTE_KEYBOARD / ERROR / BT_HID_REGISTER_FAIL` |

### B.3 TelemetryEvent category / code 命名原則

- `category` 為事件類型（大寫）：`SECURE_STORAGE` / `AUTH` / `PAYMENT` / `REMOTE_KEYBOARD` / `NETWORK`（未採用）
- `code` 為 stable identifier（大寫 + 底線），不含可變內容（時間 / 裝置名 / PII）
- `extras` 僅放可枚舉的 structured 資料（exception class name、boolean 狀態等），**禁放 API key、device 名、IP、user ID**
- 符合 S12-3 §E.2 runtime logging 紅線 + S12-4 既有 `WifiKeyboardServer` 先例

### B.4 InviteCodeManager dev-default guard 設計細節

```kotlin
init {
    if (!BuildConfig.DEBUG && BuildConfig.INVITE_HMAC_SECRET == "yukiboard_dev_2026") {
        Telemetry.report(TelemetryEvent(
            category = "AUTH",
            severity = Severity.ERROR,
            code = "INVITE_DEV_SECRET_DETECTED"
        ))
    }
}
```

- `!BuildConfig.DEBUG` gate 避免 dev 本機每次 InviteCodeManager 建構都噴 ERROR（debug 用 dev 預設值本來就是正常）
- release build 若這條 fire，代表 release pipeline 沒剝 .env 真值或 gradle override 失效 → 屬嚴重 supply-chain failure
- 與 S12-3 §K.2.2 `INVITE_HMAC_SECRET == dev default → release fail build` 雙保險：build 端漏抓時 runtime 也能上報
- fire 頻率：每次 `InviteCodeManager` 建構（多個 caller）。ERROR 級即使多次上報也無妨，但未來 CrashlyticsReporter 可考慮 dedupe

### B.5 未在 S12-5 scope 內的 consumer（明確排除）

依 recon 回覆董事長時確認：

| 點 | 排除理由 | 延後 round |
|---|---|---|
| `data/SupabaseClient.kt` / `data/InsightApiClient.kt` network error | CCA 未評估隱私/noise 影響；每次網路錯誤上報可能 spam | 待 CCA review 後 S13+ |
| `auth/SnowRealmAuthClient.kt` OAuth 失敗 | S12-0 audit 未列；與 InviteCodeManager 邏輯不同 | S13+ auth round |
| `WhisperSpeechEngine` / `FasterWhisperSpeechEngine` 失敗 | Whisper 路徑 S12-3 §K.2.1 precedence 開放問題未拍板前不擴 | S12-3.1 decide 後 |
| 51 檔 Log.e/w 其他點 | 不屬 S12-0 §E-3 指定 P0/P1 事件 | 不擴 |

---

## C. 驗證

### C.1 編譯

```
$ ./gradlew compileDebugKotlin
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 5s
18 actionable tasks: 1 executed, 17 up-to-date
```

### C.2 運行時驗證（待 CCA 或使用者 smoke test）

本輪無 UI-visible 改動。5 處事件皆為「只在特定錯誤路徑才觸發」類：

| 事件 | 觸發條件 | 如何驗 |
|---|---|---|
| `AI_KEY_PREFS_FALLBACK_PLAIN` | Android KeyStore 失敗（極罕） | 需模擬 / root 破壞 keystore；未做 |
| `ZCOIN_PREFS_FALLBACK_PLAIN` | 同上 | 同上 |
| `INVITE_PREFS_FALLBACK_PLAIN` | 同上 | 同上 |
| `INVITE_DEV_SECRET_DETECTED` | release build 且 .env `INVITE_HMAC_SECRET` 為 dev 預設 | 需 assembleRelease + 未注入真值；未做 |
| `NEWEBPAY_SIGN_FAIL` | AES key 非 16/24/32 bytes 或 IV 非 16 bytes | 可測但 ZCoin DEFERRED 中；未做 |
| `BT_ADAPTER_UNAVAILABLE` | 裝置無藍牙或藍牙關閉 | 手動測試（關藍牙 → 啟動遠端鍵盤 → logcat 看 `Telemetry/REMOTE_KEYBOARD`） |
| `BT_HID_REGISTER_FAIL` | 藍牙開啟但 HID 註冊失敗（系統層 reject） | 難模擬；OEM 特定 |

**CCB 執行測試**：僅靜態 compile 驗證；runtime smoke test 建議董事長開實機後自測 `BT_ADAPTER_UNAVAILABLE`（最容易觸發）。

### C.3 logcat 範例（AndroidLogReporter 當前實作）

`BT_ADAPTER_UNAVAILABLE` 觸發時 logcat 形式：

```
W/Telemetry/REMOTE_KEYBOARD: BT_ADAPTER_UNAVAILABLE extras=adapterNull=false
```

category / severity / code / extras 四欄位皆 structured，S13+ CrashlyticsReporter 可直接沿用相同 TelemetryEvent 欄位 mapping。

---

## D. 跟 CCA 的銜接點

### D.1 CCA 側待判問題（請雪鑰 review 時評估）

1. **`AUTH / INVITE_DEV_SECRET_DETECTED` 是否需轉為 user-visible alert？**
   - CCB 現況：僅 log 上報，不影響使用者
   - CCA 視角：release 出現此事件 = app 實質不安全，是否要顯示 user-facing error banner / refuse 功能啟用？
   - 建議：屬 CCA 產品面拍板

2. **extras 欄位是否需隱私審查？**
   - 目前 extras 只有 `exception` class name（無 stack trace、無 PII）、`adapterNull` boolean
   - CCA 視角：若 S13+ 換 Crashlytics 把 extras 上 cloud，是否需要額外 redaction？

3. **S13+ 換 Crashlytics 時的 opt-in UX**（本輪不落地，但 CCA 需規劃）
   - 使用者首次啟動 app 是否需看到 consent？
   - 若需要 opt-in，應在 SettingsActivity 哪個 section 暴露開關？
   - CCB 是否需預留 `ConsentAwareErrorReporter`（只有 user 同意後才實際上報）wrapper class？

4. **error wording 語意**
   - `WAS / WAR / ERROR` severity 的 user-facing 翻譯（若有需要）
   - CCA 負責敲（本輪 AndroidLogReporter 僅 logcat，無 user-facing）

### D.2 CCA 執行建議

CCA final pass 時可：
1. 檢查 `Telemetry.report(...)` 5 處 call site 是否符合 CCA 隱私 / wording 線
2. 對照 `S12-5-CCA-OBSERVABILITY-CONTRACT-NOTE` 中定義的 event category / severity 慣例
3. 若需新增 consumer（例如 network error），列為 §D.1 evidence，CCB 補第二輪
4. 若需移除 / 微調某事件的 severity 或 code，列為 MINOR fix，CCB 二次修

---

## E. 變更摘要（CLAUDE_CODEX_COLLAB_GUIDE 格式）

**A. 變更摘要**
- 新增 1 檔：`telemetry/Telemetry.kt`（facade，9 行）
- 修改 5 檔：`AIProviderManager` / `ZCoinManager` / `InviteCodeManager` / `NewebpayClient` / `BluetoothHidKeyboard`
- 5 處 `Telemetry.report(...)` + 1 處 dev-secret guard
- 無 UI 改動，無新依賴，無 build-infra 變動

**B. 風險**
- facade 採 `@Volatile var reporter`，若 S13+ 多執行緒併發 swap 需考慮 memory visibility（目前 AndroidLogReporter 作為預設值穩定，swap 只在 Application.onCreate 發生，風險低）
- `InviteCodeManager` init dev-secret guard 會在每個 caller 建構時 fire；release 下多 caller 同時 trigger 會多次上報（預期設計，ERROR 級事件多次上報可接受）
- `NewebpayClient.buildPaymentParams` 新增 try/catch 改變方法 signature 的隱式錯誤行為（之前 exception 直接 propagate，現在吞掉回 null）— caller 都已 null-check（`!isConfigured return null` 同 signature）
- runtime smoke test 未跑；CCA 若需要實機驗證，建議董事長或 CCA 自測 `BT_ADAPTER_UNAVAILABLE`（最易觸發）

**C. 下一步建議**
- CCA 完成 `S12-5-CCA-OBSERVABILITY-CONTRACT-NOTE`
- CCA final pass（若有 BLOCKING / STRONG，CCB 二次修）
- 兩份齊備送 `玄樞` gate verdict
- READY 後 S12-5 結案、Sprint 12 總結

**D. 架構決策**
- 確立 `Telemetry` facade 作為 telemetry swap 點（S13+ 換 Crashlytics 只動 Application.onCreate）
- `ErrorReporter` interface / `AndroidLogReporter` impl 維持 S12-4 設計，不改
- `WifiKeyboardServer` 既有 constructor-injected `errorReporter` 不改動；facade 與 constructor DI 並存
- TelemetryEvent 命名原則確立（category / code 大寫底線、extras 禁 PII）

**E. 玄樞審核需求 / 結論**
- 需要 `玄樞` gate verdict: `READY / NOT READY / LOCKED`（與 CCA 同時送審）
- 本輪 scope 嚴守 Path B（不擴 Crashlytics / build-infra / CCA consent UX）
- CCA 側 §D.1 四項開放問題需 CCA / 玄樞 裁
- 若玄樞判 READY，建議同時結 S12-5 + Sprint 12 總結（S12-0~12-5 路線完整）

---

## F. 本輪實際生效

- 5 個關鍵錯誤事件現在有 structured logcat 輸出（`Telemetry/XXX`）
- release 出貨若 `INVITE_HMAC_SECRET` 為 dev 預設，會 logcat 看到 `INVITE_DEV_SECRET_DETECTED`（供 QA / 董事長自驗 release 真值是否正確注入）
- S13+ 換 Crashlytics 時 only Application.onCreate 一行：`Telemetry.reporter = CrashlyticsReporter(...)`
- 5 類 category（SECURE_STORAGE / AUTH / PAYMENT / REMOTE_KEYBOARD）為未來事件擴充提供命名基線

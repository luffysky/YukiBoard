# S12-0-CCB Remote Security Audit

- 任務 ID：`S12-0-CCB-REMOTE-SECURITY-AUDIT`
- 負責：鍵律
- 提交對象：玄樞（經雪鑰 CCA 並案後）
- 日期：2026-04-19
- 狀態：`READY_FOR_REVIEW`
- 本輪性質：**純 audit / threat model / file scope cut**，`不改任何 .kt`

---

## A. Audit 範圍與方法

依 `Sprint12-All-Round-Gate.md` S12-0 條文，鍵律 CCB 負責從 **runtime / input / service / telemetry** 線盤點，並切成三類：

1. remote keyboard 本體風險（§C）
2. payment runtime / secret handling 風險（§D）
3. 記錄 / log / telemetry 缺口（§E）

方法：

- 直接 read：`remote/RemoteKeyboardService.kt`、`remote/WifiKeyboardServer.kt`、`remote/BluetoothHidKeyboard.kt`、`currency/NewebpayClient.kt`、`currency/ZCoinApi.kt`、`currency/ZCoinManager.kt`
- Grep：`BuildConfig.*` 所有 key consumer、`Log.*` 在 remote/ 與 currency/、`Crashlytics|Sentry|Bugsnag|observability`（0 matches）
- 只做盤點、threat model、file scope，沒有改動 `.kt`。

---

## B. 速覽（Executive Summary）

| # | 類別 | 最高風險項 | 最小落地門 |
|---|------|-----------|----------|
| 1 | remote keyboard | WiFi 模式 PIN 只有 4 位（kotlin.random.Random）、無 brute-force 保護、明文 HTTP/WS | S12-4 |
| 2 | remote keyboard | 藍牙 HID 無 app 層 PIN，只靠 Bluetooth pairing | S12-4（次要） |
| 3 | payment runtime | `NewebpayClient` 把 `HashKey` / `HashIV` 以 `BuildConfig` 打進 APK，可反編譯 | S12-1 / S12-2 |
| 4 | payment runtime | 簽章在 client 做、缺 server-side 驗 notify，訂單可偽造 | S12-1 / S12-2 |
| 5 | runtime secret | OPENAI / ANTHROPIC / PLATFORM / OPENWEATHER / GIPHY key 全在 `BuildConfig`，APK 可反編譯抽取 | S12-3 |
| 6 | runtime secret | `ZCoinManager` EncryptedSharedPreferences fallback 到 `MODE_PRIVATE` 時會把餘額 / tx 明文寫入 | S12-2 延伸 |
| 7 | telemetry | 全專案 **0 個** Crashlytics / Sentry / Bugsnag hook；S11-4 的 silent catch 並無上報通道 | S12-5 |
| 8 | telemetry | WifiKeyboardServer 的 PIN 拒絕只有 `Log.w` + 客戶端 IP，沒有 rate limit / lockout / 統計 | S12-4 + S12-5 |

---

## C. Remote Keyboard 本體風險

### C-1 WiFi keyboard server（`remote/WifiKeyboardServer.kt`）

**威脅面：**

| 項 | 現況 | 風險 |
|---|------|------|
| PIN 強度 | 4-digit，以 `(1000..9999).random()`（`kotlin.random.Random`） | 10,000 組合、非 cryptographic PRNG；近似可預測 |
| PIN 傳輸 | 明文 HTTP 提供控制頁 + `ws://?pin=xxxx` 走 plain WebSocket | 同 Wi-Fi AP 可被動 sniff，PIN 直接外洩 |
| Brute-force | 無 retry 限制、無 lockout、無失敗計數、無 sleep | 10,000 組合可以在 2–3 分鐘 sweep 完 |
| 連線層鑑別 | 僅比對 URL query `pin=xxxx` sub-string | 失敗只回 403 + `Log.w`，不影響後續嘗試 |
| 來源 IP 控制 | 沒有 allowlist、沒有 LAN-only 檢查 | 若手機同時在 public Wi-Fi，任何 peer 可嘗試連 |
| WebSocket frame | payload cap 64KB、遵守 RFC6455 masking | 此項 OK |
| Client 身份 | 連上後立刻信任；沒有 session token、沒有 per-connection nonce | 拿到 PIN = 拿到整個 IME 輸入通道 |
| 控制頁 HTML | 內嵌在 Kotlin 字串中，PIN 在 HTML `<input value="...">` 側印出來 | PIN 只要本地 HTTP 存活，任何人掃 port 8765 即可看到 |
| TLS | 完全沒有 TLS，自簽 cert 也沒有 | 無法在 public Wi-Fi 上安全使用 |

**行為缺口：**

1. `generatePin()` 使用 `(1000..9999).random()`（`WifiKeyboardServer.kt:44`）
   - 應改 `SecureRandom`；位數 ≥6、建議 alphanumeric
2. PIN 驗證位置：`WifiKeyboardServer.kt:192-203`
   - 無 failed-attempts 追蹤、無 lockout 時間窗
3. 連線身份：連上後沒有二次 challenge、沒有 heartbeat token

**S12-4 可落地的最小 cut（不動 transport 架構）：**

- a. PIN 生成走 `SecureRandom`，位數 6，僅數字
- b. 在 server 端以 `ConcurrentHashMap<InetAddress, AttemptTracker>` 收斂 failed attempts
  - 視窗 60s 上限 5 次，超過後 300s lockout
- c. PIN 錯誤時統一回 403，不回「PIN 格式錯 / PIN 值錯」的差異訊息
- d. 控制頁 HTML 不把 PIN 印在 DOM，改要求使用者自行輸入 PIN 再 POST 進入 WS handshake
- e. 首次 handshake 成功後，server 簽一個 session token（`SecureRandom` 128-bit），之後 ws 訊息皆需帶 session token，驗 session 而非 PIN
- f. server 綁定只監聽 `getLocalIpAddress()` 非 `0.0.0.0`、拒絕 WiFi 斷線時的殘留 socket

> 不在本 sprint 做的：自簽 TLS、SRP / PAKE、配對流程 UI 改造。

### C-2 Bluetooth HID keyboard（`remote/BluetoothHidKeyboard.kt`）

**威脅面：**

| 項 | 現況 | 風險 |
|---|------|------|
| 鑑別層 | 完全依賴 Android Bluetooth pairing（`BluetoothHidDevice` API 28+） | 配對一旦建立即永久信任；沒有 app 層 PIN |
| 連線對象過濾 | `onConnectionStateChanged` 直接接受任何 host | 任何已配對 host 都能連並送 key |
| 可辨別性 | SDP name 為 "YukiBoard Keyboard" + manufacturer "SnowRealm" | 廣播可被外部掃描 |
| Log | 連線時 `Log.d` 帶 host name + address | 非 PII 重災區，但在設備共用情境會累積裝置歷史 |
| 意外啟動 | `start()` 直接走 profile proxy，無 user re-confirm | IME 被喚醒時若 UI 殘留狀態可能誤啟 |

**S12-4 最小 cut：**

- a. `start()` 前要求使用者在設定頁按下「啟動藍牙鍵盤」，且 consent 狀態存 `ZCoinManager` 外獨立 prefs
- b. `onConnectionStateChanged` → 成功時觸發一次性通知，讓使用者能在 notification shade 看到目前連線的 host 名稱
- c. `Log.d` host name / address 降為 sanitized（僅 MAC 後 4 位 + name 長度），或只在 debug build 輸出

> 不在本 sprint 做的：自訂 pairing 流程、重寫 HID 報告 descriptor。

### C-3 `RemoteKeyboardService` 介面邊界（`remote/RemoteKeyboardService.kt`）

介面本身乾淨，無威脅；但介面層可加一個：

- `val authRequirement: AuthLevel`（`NONE` / `PIN` / `PIN_PLUS_SESSION`）

讓 `S12-4` 在 PIN hardening 時可以把 WiFi impl 標為 `PIN_PLUS_SESSION`、藍牙 impl 標為 `PAIRED_ONLY`，使用者可在設定頁知道目前鏈路強度。

---

## D. Payment Runtime / Secret Handling 風險

### D-1 `NewebpayClient.kt`（payment core）

| 項 | 現況（行號） | 風險 |
|---|--------|------|
| MerchantId | `BuildConfig.NEWEBPAY_MERCHANT_ID`（L20） | APK 反編譯即知商店代號（本身非 secret，但曝露攻擊面） |
| HashKey | `BuildConfig.NEWEBPAY_HASH_KEY`（L21） | **高危 client secret**，APK 可抽出，藍新規範要求 server 保管 |
| HashIV | `BuildConfig.NEWEBPAY_HASH_IV`（L22） | **高危 client secret**，同上 |
| 簽章位置 | client 端 `aesEncrypt()` + `sha256()` 計算 `TradeInfo` + `TradeSha`（L37-39, L76-89） | 攻擊者持有 HashKey 後可直接偽造交易請求 |
| NotifyURL | 程式中**完全沒有**設定 `NotifyURL` / `ReturnURL` / `ClientBackURL` | Newebpay 付款完成後沒有 server 可接收、無法確認 orderId 真偽 |
| OrderId | 來源為呼叫端參數，無 client-side nonce 驗證 | 重放攻擊 / 重複扣款需靠 server 端防禦 |

**結論：** `NewebpayClient` 目前等同**把整個商店簽章能力塞進 APK**。必須整組移到後端，APK 只保留 orderId 與顯示流程。

### D-2 `currency/ZCoinApi.kt`（Supabase RPC）

- `fetchBalance` / `recordSpend` / `fetchTransactions` 皆透過 `supabase.rpc()` + `authToken` 做驗證
- `authToken` 來源為呼叫端（未在此檔 log）；此檔本身無額外 secret leak
- `zcoin_deduct` RPC 為 Supabase-side 原子扣款，是 **正確方向**，應把所有 mutation 都移進 RPC
- 風險點：**topUp 路徑**在 client code 裡沒看到 API 呼叫，意味著 `ZCoinManager.topUp` 只在本地加錢，若 Newebpay 沒有 notify 回 Supabase，整個錢包與實際付款脫節

### D-3 `currency/ZCoinManager.kt`（本地錢包）

| 項 | 現況（行號） | 風險 |
|---|--------|------|
| 主 prefs | `EncryptedSharedPreferences` AES256_GCM（L19-29） | OK |
| Fallback | `try` 失敗則退到 `MODE_PRIVATE` 明文 prefs（L30-33） | **明文**寫餘額 + 交易字串，且 `Log.w("ZCoinManager", "EncryptedSharedPreferences failed, fallback", e)` 會把 exception stack 印出 |
| `topUp()` | 本地直接 `balance += amount`（L62-71） | 若被呼叫端繞過 Newebpay，會無中生有 |
| `spend()` | `@Synchronized`、檢查 `balance >= amount` | OK，但未要求 server RPC 確認 |
| 交易序列化 | `pipe-delimited` 字串 + `split("||")` | 若 description 包含 `|` 或 `||` 會 parse 失敗；非 security，但 S11-4 silent catch 基線已改，應連帶補 |
| `clear()` | 直接 `prefs.edit().clear()`（L86-88） | OK |

### D-4 APK 內其他高危 secret（grep 結果）

| Key | 消費點 | 狀態 |
|-----|------|------|
| `PLATFORM_API_KEY` | `data/InsightApiClient.kt:39`, `sticker/InsightStickerApi.kt:28`, `claw/provider/SnowRealmProvider.kt:36` | APK 中，S12-3 範圍 |
| `YUKIBOARD_SUPABASE_ANON_KEY` | `data/SupabaseClient.kt:26`, `ui/settings/section/AISettingsSection.kt:114`, `app/ime/ImeFraudModelInit.kt:34` | anon key 本即 client-side，但需 RLS 保證 |
| `INVITE_HMAC_SECRET` | `auth/InviteCodeManager.kt:146` | client 端驗邀請碼 HMAC，若 secret 外洩可偽造邀請碼 |
| `OPENAI_API_KEY` | `claw/provider/AIProviderManager.kt:63`, `service/ImeServiceInput.kt:255`, `ui/settings/section/SpeechSettingsSection.kt:266` | S12-3 |
| `ANTHROPIC_API_KEY` | `claw/provider/AIProviderManager.kt:62` | S12-3 |
| `OPENWEATHER_API_KEY` | `weather/WeatherManager.kt:28` | S12-3 |
| `GIPHY_API_KEY` | `gif/GiphyApiClient.kt:19` | S12-3 |
| `NEWEBPAY_HASH_KEY` / `NEWEBPAY_HASH_IV` | `currency/NewebpayClient.kt:21,22` | **S12-1 / S12-2 核心** |

> 這邊 **runtime 觀點** 的切法：凡是「如果 APK 被反編譯 → 立刻變現或直接惡用」的 key，都應該走 S12-1 / S12-2 / S12-3。其餘 anon key 類的走 **RLS / 行為限制**，不走 key 移出。

---

## E. 記錄 / Log / Telemetry 缺口

### E-1 全專案觀測面現況

- `Grep` `Crashlytics|FirebaseCrash|Sentry|Bugsnag|observability`：**0 matches on `app/src/main`**
- 也沒有自建的 `ErrorReporter` / `CrashSink` / `TelemetryPipe`
- 結果：**沒有任何 crash / error / security event 出得了 device**

### E-2 已知但無通道上報的錯誤

| 來源 | 目前行為 | 觀測缺口 |
|------|--------|--------|
| S11-4 整批 silent catch（見 `SPRINT11_4_CCB_SILENT_CATCH_FIRST_PASS_IMPLEMENTATION_2026-04-18.md`，8 檔 × 15 sites） | 現在是 `Log.w`，但 `Log.w` 只進 logcat | prod 裝置無法回收 |
| `ZCoinManager` Encrypted prefs 失敗 | `Log.w` + fallback | 使用者錢包已進入降級模式，但我們不知道 |
| `NewebpayClient.aesEncrypt` 失敗 | 會 throw，呼叫端目前無全域 catch | payment failure 會讓 `buildPaymentParams` 拋 exception 到 UI，使用者看到空畫面 |
| `WifiKeyboardServer` PIN 拒絕 | `Log.w(TAG, "WebSocket rejected: wrong PIN from ${socket.inetAddress}")` | 無彙總、無告警 |
| `BluetoothHidKeyboard.registerApp` 失敗 | `Log.e(TAG, "Failed to register HID app")` + `connectionState = ERROR` | 使用者看到 `ERROR`，我們收不到原因 |
| `SupabaseClient` / `InsightApiClient` 網路錯誤 | 目前多半 Log 後吞掉（S11-4 清過一輪） | 沒有 retry 統計、沒有 latency histogram |

### E-3 S12-5 可落地的最小 observability hook

- a. 新 `com.snowrealm.yukiboard.telemetry.ErrorReporter`（純 domain interface）
  - `fun report(event: TelemetryEvent)` → `TelemetryEvent { category, severity, code, extras }`
  - 實作 1：`AndroidLogReporter`（等同 `Log.w` + structured tag）
  - 實作 2：`FirebaseCrashlyticsReporter`（Sprint 12 先 scaffold，不納 build）
- b. 關鍵落地點（由 S12-5 CCB 擴充）：
  - `ZCoinManager` prefs fallback
  - `NewebpayClient` buildPaymentParams 失敗
  - `WifiKeyboardServer` PIN 拒絕（聚合 → 5 次以上 / 60 秒視窗上報 1 次）
  - `BluetoothHidKeyboard` registerApp / onConnectionStateChanged = ERROR
  - `S11-4` silent catch 點位中，標 `SEC` / `PAYMENT` / `SYNC` 類的 15 處
- c. Consent / privacy：使用者在設定頁可開關 error reporting，預設 OFF；首次開啟時顯示資料欄位清單（由 CCA 負責 wording）

---

## F. File Scope（S12-1 ~ S12-5 可動檔案白名單草案）

> 下列為 **CCB runtime 觀點** 的最小 file scope。CCA 的 consume / contract 觀點另定。

### S12-1 / S12-2 Newebpay server-side signing + client refactor
```
app/src/main/java/com/snowrealm/yukiboard/currency/NewebpayClient.kt      ← 大砍 80%
app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinManager.kt        ← topUp 接 server notify
app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinApi.kt            ← 新增 createTopUpOrder / confirmTopUp RPC
app/build.gradle                                                          ← 移除 NEWEBPAY_HASH_KEY / NEWEBPAY_HASH_IV 注入
```

### S12-3 API key release policy（runtime 觀點）
```
app/src/main/java/com/snowrealm/yukiboard/gif/GiphyApiClient.kt
app/src/main/java/com/snowrealm/yukiboard/weather/WeatherManager.kt
app/src/main/java/com/snowrealm/yukiboard/claw/provider/AIProviderManager.kt
app/src/main/java/com/snowrealm/yukiboard/claw/provider/SnowRealmProvider.kt
app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceInput.kt      ← 只動 OPENAI_API_KEY 讀取點
app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/SpeechSettingsSection.kt
app/src/main/java/com/snowrealm/yukiboard/auth/InviteCodeManager.kt       ← HMAC secret 路徑
app/build.gradle                                                          ← release build 剝除 default 值
```

### S12-4 Remote keyboard PIN hardening
```
app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt    ← 主要改動
app/src/main/java/com/snowrealm/yukiboard/remote/RemoteKeyboardService.kt ← 加 AuthLevel
app/src/main/java/com/snowrealm/yukiboard/remote/BluetoothHidKeyboard.kt  ← 加 consent hook + log sanitize
app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt ← 顯示 AuthLevel + session 狀態
```

### S12-5 Observability hook
```
新增：app/src/main/java/com/snowrealm/yukiboard/telemetry/ErrorReporter.kt
新增：app/src/main/java/com/snowrealm/yukiboard/telemetry/AndroidLogReporter.kt
app/src/main/java/com/snowrealm/yukiboard/currency/ZCoinManager.kt        ← prefs fallback 呼叫 reporter
app/src/main/java/com/snowrealm/yukiboard/currency/NewebpayClient.kt      ← payment 失敗上報
app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt    ← PIN 拒絕聚合上報
app/src/main/java/com/snowrealm/yukiboard/remote/BluetoothHidKeyboard.kt  ← registerApp 失敗上報
```

---

## G. 禁區（不許在 Sprint 12 內動）

1. **不得重寫 `YukiBoardIMEService.kt` 任何 lifecycle**
   - Reason：CLAUDE.md 絕對禁止之第 1 條；Sprint 12 僅 payment / key / remote / telemetry
2. **不得重寫 `SettingsActivity.kt` 本體**
   - Reason：同上第 2 條；S12-4 的設定顯示走 section binder
3. **不得動 `input/` 核心（router / engine / smart）**
   - Reason：Sprint 12 無 input 行為變更
4. **不得動 GL / Lottie / 遊戲**
   - Reason：Sprint 11-5 剛落地，避免 regression
5. **不得把 S12-5 的 `ErrorReporter` 塞進 CLAUDE 禁單上的 `getInstance(context)` pattern**
   - Reason：CLAUDE.md 第 3 條；應走 DI / Coordinator 注入
6. **不得在 S12-4 把 WiFi keyboard 改用非 WebSocket transport**
   - Reason：本輪只做 PIN 強度與 session token，transport 重寫不在 sprint 12
7. **不得為了 payment 切換而動 Supabase schema / migrations**
   - Reason：schema migration 需 CCA 主導、需 server 端配合，非 CCB runtime 範圍
8. **不得把 `NEWEBPAY_HASH_KEY` 改成 remote config / Firebase config 仍留在 client 取**
   - Reason：違反 S12-1 server-side signing 的核心目的；只能由 server 簽完交還 tradeInfo / tradeSha
9. **不得在 Sprint 12 內開始 TLS 自簽 cert、Bluetooth 客製 pairing、PAKE / SRP 實作**
   - Reason：scope creep，CLAUDE.md 固定重構順序
10. **不得把 silent catch 再全量轉成 crash report**
    - Reason：S11-4 才剛把 catch 分級完，S12-5 上報應只挑 §E-3 明列的 whitelist 點位

---

## H. 最小落地切法（Sprint 12 建議順序）

> 本節給 CCA + 玄樞做參考，最終順序由玄樞拍板；鍵律不自排。

### 候選順序 CH-1（優先封 payment 洞）
1. `S12-1` CCA contract + CCB runtime plan（§F / §D 為準）
2. `S12-2` CCB Android payment refactor → CCA consume review
3. `S12-4` CCB PIN hardening → CCA consume review
4. `S12-3` CCA release policy + CCB runtime policy
5. `S12-5` CCB observability hook + CCA contract note

理由：Newebpay hash key 目前是**可反編譯即失錢**的直接漏洞，應先砍；PIN 與 API key 為次要；telemetry 最後建，因 S12-5 的第一批 reporter 落地點剛好是前 3 門的產物。

### 候選順序 CH-2（先做觀測、再動金流）
1. `S12-5` 先建 `ErrorReporter` + scaffold（只接 AndroidLogReporter）
2. `S12-1` `S12-2` 走上面
3. `S12-4`
4. `S12-3`

理由：S12-1/2 的改造重，落地時需要觀測通道；先有 reporter 才看得到 payment refactor 的 regression。

### 候選順序 CH-3（雙線並行）
- 線 A：`S12-1` → `S12-2`（雪鑰 主導 + 鍵律 runtime）
- 線 B：`S12-4` → `S12-5` scaffold（鍵律 主導 + 雪鑰 consume）
- 並行收斂到 `S12-3`

理由：S12-1/2 的 contract 重、主要卡在 server 側；S12-4 是純 client，可以獨立開。但並行風險是 S12-5 reporter 契約在 payment refactor 前未定型。

**鍵律對三者的信心分布：** CH-2 > CH-1 > CH-3。
（CH-2 先有觀測再做重改，最安全；CH-3 需要玄樞特別 gate payment contract 與 reporter 契約。）

---

## I. 交接需求 / 待玄樞裁示事項

1. **是否允許 S12-4 PIN hardening 同時引入 session token？**
   - 引入 → S12-4 改動半徑擴大到 `RemoteKeyboardPanelView` + HTML 控制頁
   - 不引入 → PIN 再強也有 URL sniff 問題
2. **Newebpay server 端由誰做？**
   - 若 SnowRealm 後端未準備，S12-1 只能做 contract 紙稿、不能解 APK 中的 HashKey
3. **S12-5 是否在 Sprint 12 內接 Firebase Crashlytics？**
   - 若是 → Gradle plugin / `google-services.json` / 權限宣告入 sprint
   - 若否 → 先做 `AndroidLogReporter` scaffold，CrashlyticsReporter 留 Sprint 13
4. **Release build 是否立即從 build.gradle 剝除 payment key 預設值？**
   - 若是 → 開發者本地 build 必須有 `local.properties` 或 `.env`，否則 `isConfigured = false`、儲值 UI 必須顯示 fallback
5. **是否允許本輪後 CCB 代起草 S12-5 `ErrorReporter` 介面（只做介面、不接實作）？**
   - 若允許 → 下輪 S12-1 CCB runtime plan 可直接引用

---

## J. 結論（CCB 觀點）

- **最大洞**：`NewebpayClient` 把支付簽章能力整組塞進 APK；其次是 `WifiKeyboardServer` 4-digit PIN + 無 brute-force 防線。
- **最大盲點**：全專案沒有任何 crash / error / security event 上報通道。
- **三類切分**：§C remote / §D payment / §E telemetry 各自 file scope 乾淨、不重疊，適合 S12-1 ~ S12-5 依玄樞裁定順序逐門開。
- **鍵律建議**：照 CH-2 走（S12-5 先建觀測骨架，再動金流）；但順序由玄樞決定，本文件只列候選。

提交對象：玄樞（經雪鑰 CCA 並案後）
狀態：`READY_FOR_REVIEW`

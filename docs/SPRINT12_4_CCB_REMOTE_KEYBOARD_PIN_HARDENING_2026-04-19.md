# S12-4-CCB — Remote Keyboard PIN Hardening

日期: 2026-04-19
身份: 鍵律 (代行 CodeX B runtime / threat / observability)
任務 ID: `S12-4-CCB-REMOTE-KEYBOARD-PIN-HARDENING`
狀態: `SECOND_PASS_APPLIED — PENDING CCA FINAL PASS`（原狀態 `IMPLEMENTED — PENDING CCA REVIEW`）

---

## A. 實作摘要

依董事長 2026-04-19「可以 照你建議」指示，按四條建議落地 remote keyboard app-layer 驗證基線。

### A.1 更動檔案（7 個 production + 2 個新建）

新建：
- `app/src/main/java/com/snowrealm/yukiboard/telemetry/ErrorReporter.kt`
- `app/src/main/java/com/snowrealm/yukiboard/telemetry/AndroidLogReporter.kt`

修改：
- `app/src/main/java/com/snowrealm/yukiboard/remote/RemoteKeyboardService.kt`
- `app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt`
- `app/src/main/java/com/snowrealm/yukiboard/remote/BluetoothHidKeyboard.kt`
- `app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt`
- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeRemotePanelBinder.kt`
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceMisc.kt`
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`

### A.2 編譯結果

`gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`，無新增 error，僅 3 個 pre-existing warning（WifiInfo deprecated / Condition always true，不屬本輪 scope）。

---

## B. 四條建議的落地對照

### B.1 Q1：Session token（answer：採用）

檔案：`WifiKeyboardServer.kt`

- `generateSessionToken()`：`SecureRandom.nextBytes(16)` → `Base64.getUrlEncoder().withoutPadding()` → 約 22 字元 URL-safe token。
- `upgradeToWebSocket()` 在 HTTP 101 握手完成、`WebSocketClient` 建立時產生 session token，與 client instance 1:1 綁定（不共享給其他 client）。
- 歡迎訊息 JSON 加上 `session` 欄位，瀏覽器端儲存 `sessionToken` 全域變數。
- 所有後續 inbound `ws.send(...)` JSON 都必須帶 `session` 欄位，由 `handleWebSocketMessage(message, expectedSessionToken)` 對比。
- Session mismatch → `Log.w` + return（不觸發 brute-force tracker — PIN 已通過，這裡僅防 session 猜測）。
- HTML JS 修正：`sendCurrentText` / `sendKey` / input 即時送字，全都加 `session: sessionToken`，且 `sessionToken` 為空時不送。

威脅收口：
- 同網段他機即使被動觀察到 WebSocket 明文流量，要冒充既有連線也須同時擁有 session token。
- 單純重放 HTTP upgrade 仍然需要 PIN，重放完還須再拿到新的 session。

未收口（依舊存在）：
- 全程明文 HTTP/WS，主動 MITM 仍可抄到 session + PIN；此部分列入 Sprint 13+ TLS 範圍，不在本輪。

### B.2 Q2：Panel 顯示 PIN（answer：採用）

檔案：`RemoteKeyboardPanelView.kt` + `ImeRemotePanelBinder.kt` + `ImeServicePanels.kt` + `ImeServiceMisc.kt`

- `RemoteKeyboardPanelView` 新增欄位：
  - `var wifiPin: String = ""`
  - `var authLevel: AuthLevel = AuthLevel.NONE`
- 渲染規則：
  - PIN 只在 `wifiState == LISTENING` 時顯示（CONNECTED 後隱藏，避免已連線後還在畫面上曝）。
  - PIN 字體 18sp monospace，使用 theme candidateSelected color（高對比，讓董事長可讀）。
  - Auth level 以小字「🔒 驗證: PIN + Session / PIN / 系統配對」顯示於 URL / PIN 下方。
  - 面板整體高度從 `200 * density` 擴為 `240 * density` 以容納新 row。
- `ImeRemotePanelBinder.bind(...)` signature 擴充兩個參數 `wifiPin` / `authLevel`。
- `ImeServicePanels.createHighDependencyPanelOrNull(REMOTE)` 與 `ImeServiceMisc.updateRemotePanelState()` 都從 `wifiServer?.connectionPin` / `wifiServer?.authLevel ?: btKeyboard?.authLevel ?: AuthLevel.NONE` 寫入。

使用者語意：現在 user 能從面板直接看到要輸入到瀏覽器的 6 位 PIN（過去面板完全沒有顯示 PIN 的路徑）。

### B.3 Q3：ErrorReporter interface（answer：採用）

新建：
- `telemetry/ErrorReporter.kt`：`interface ErrorReporter { fun report(event: TelemetryEvent) }`，`data class TelemetryEvent(category, severity, code, extras)`，`enum class Severity { INFO, WARN, ERROR }`。
- `telemetry/AndroidLogReporter.kt`：`object AndroidLogReporter : ErrorReporter`，用 `Log.i/w/e` 輸出 tag `Telemetry/<category>`、msg `<code> extras=<k=v,...>`。

目前唯一消費者：`WifiKeyboardServer`，在觸發 brute-force lockout 的「邊緣事件」上報一次：
```
TelemetryEvent(
    category = "REMOTE_KEYBOARD",
    severity = Severity.WARN,
    code = "WIFI_PIN_BRUTE_FORCE_LOCKOUT",
    extras = mapOf("lockoutMs" to ..., "maxAttempts" to ...)
)
```

注意：
- **不接 Crashlytics、不接 Firebase、不接任何雲端**，純 interface + Log 實作，讓 S12-5 之後可直接替換。
- 為避免噪音，lockout 每次觸發**只報一次**（tracker 已進入 lockout 期間重複 fail 不再重複上報）。
- extras 採字串 k=v，可讀性高且不會夾帶個資。**故意不放 IP 或 PIN**。

### B.4 Q4：BuildConfig.DEBUG 分支（answer：採用）

檔案：`BluetoothHidKeyboard.kt`

- `import com.snowrealm.yukiboard.BuildConfig`。
- `onAppStatusChanged` / `onConnectionStateChanged` 兩個 callback 的 `Log.d`：
  - `BuildConfig.DEBUG == true` → 原樣印 `device: ${device?.name}`。
  - release → 只印 `$registered` / `$state`，不帶 name / address。
- 其他檔案原本就沒直接印 HID device name，無需改動。

以及 `WifiKeyboardServer.kt` 的附帶潔化：
- `Log.d(TAG, "WebSocket client connected: ${socket.inetAddress}")` → `Log.d(TAG, "WebSocket client connected")`（移除 IP 輸出）。
- 403 rejection log 原本為 `Log.w(TAG, "WebSocket rejected: wrong PIN from ${socket.inetAddress}")` → 已移除（改由 AttemptTracker 的 ErrorReporter 代勞）。

---

## C. 新架構要素

### C.1 `AuthLevel` enum（新，`remote/RemoteKeyboardService.kt`）

```kotlin
enum class AuthLevel {
    NONE,               // 無任何驗證
    PIN,                // 僅 PIN（不再使用，保留作為 contract 參考）
    PIN_PLUS_SESSION,   // PIN + session token（WifiKeyboardServer 目前等級）
    PAIRED_ONLY         // 依賴系統 Bluetooth 配對（BluetoothHidKeyboard 目前等級）
}
```

`RemoteKeyboardService` interface 強制每個 impl 宣告 `val authLevel: AuthLevel`，讓 UI / audit 可以直接讀取。

### C.2 `AttemptTracker`（`WifiKeyboardServer` 內部）

- 以 `ConcurrentHashMap<String, AttemptTracker>()` 依 `socket.inetAddress.hostAddress` 分桶。
- 參數：window 60s、max 5 fails、lockout 300s。
- `recordFailure()` 回傳 `true` **僅當** 本次 failure 是剛好觸發 lockout 的邊緣事件，供 ErrorReporter 一次性上報。
- `isLockedOut(now)` 檢查 `now < lockoutUntilMs`，在 PIN 比對之前就先短路回 429。
- `reset()` 在 PIN 成功時呼叫。
- `@Synchronized` methods，ConcurrentHashMap 本身已保證 bucket 取得 atomicity。

### C.3 6-digit PIN

- `PIN_LENGTH = 6`、`generatePin()` 使用 `SecureRandom.nextInt(10^6)` 後 `padStart(6, '0')`。
- entropy: log2(10^6) ≈ 19.9 bit（原 4-digit 為 13.3 bit，+6.6 bit）。
- 配合 5/60s + 300s lockout，**線上窮舉時間**（單 IP）由「數十秒」升為「單 IP 幾乎不可能」。

### C.4 Session token 契約

- Format: Base64 URL-safe（無 padding）22 字元，16 byte entropy = 128 bit。
- 生成時機：WebSocket upgrade 成功當下。
- 作用範圍：對應 `WebSocketClient` instance；連線結束即失效（不持久化）。
- Client 責任：從 welcome message 的 `session` 欄位抓取，之後所有 outbound JSON 都帶 `session`。
- Server 驗證：每條 inbound JSON 都必須 `json.optString("session", "") == expectedSessionToken`，否則 warn + drop。

---

## D. 不做（明確排除，避免 scope 漂移）

- ❌ **不接 TLS / HTTPS / WSS**：Sprint 13+ 範圍，需要憑證 / mkcert / trust anchor 規劃。
- ❌ **不引入 Firebase Crashlytics 或任何雲端 telemetry**：ErrorReporter 僅 log 實作，保留介面給日後決策。
- ❌ **不強化 Bluetooth 層**：PIN 對 HID 層無意義（系統配對 PIN 由 Android 自己跑），仍為 `PAIRED_ONLY`。
- ❌ **不做帳號 / token signing / device binding**：超出本 round 授權。
- ❌ **不移除 Logcat 內所有 device-name 輸出**：僅處理 release build 下最高風險兩處（on* callback），其他 debug-only log 保留。
- ❌ **不做 WifiKeyboardServer 的 structural refactor**：brute-force + session token 是最小侵入式貼片，上層 architecture 不動。

---

## E. 待 CCA 審核項目（`S12-4-CCA-REMOTE-KEYBOARD-PIN-CONSUME-REVIEW`）

請雪鑰從 consume / UX / 使用者可見語意角度審核：

1. **PIN 顯示語意**：
   - 6 位 PIN 在 LISTENING 才出現、CONNECTED 後自動隱藏，是否符合「夠用但不過曝」的設定面期待？
   - Panel 高度 200 → 240dp，在一般 / 橫向 / 懸浮鍵盤模式下是否有截斷、遮擋可能？
   - PIN 字體 18sp monospace vs theme candidateSelected color，低亮度主題下可讀性如何？

2. **Auth level 標示**：
   - 「🔒 驗證: PIN + Session / 系統配對」的文案是否足以讓非技術使用者理解？
   - 需不需要追加「說明」Tooltip / 說明面板（目前沒加）？

3. **Lockout 使用者語意**：
   - 瀏覽器端被 429 時，現在只會看到 WebSocket open 失敗 + onerror 回「連線錯誤（PIN 碼錯誤？）」，其實是已被 lockout — 是否要讓網頁分辨 403 vs 429 並給出「5 分鐘後再試」訊息？
   - 手機端目前只靠 Log + telemetry，面板**不**顯示「有人嘗試暴力破解」，是否需要提示 user？

4. **文案一致性**：
   - HTML 面「輸入 6 位 PIN 碼」、panel 面「PIN: XXXXXX」、error「PIN 碼需為 6 位數」三處是否文字規格一致？

5. **PIN lifecycle**：
   - `start()` 會重新 `generatePin()`，`stop()` 不會清 PIN — 是否要在 stop 時把 PIN 欄位也清空避免殘留於面板記憶體 / 畫面 snapshot？

6. **開放問題**：
   - 目前 session token 只存在 runtime 記憶體，IME 被系統 kill 重建時會遺失 — 使用者是否需要被告知需要「重新連線」？
   - 既有 `InsightSurveyClient.kt` 有沒有其他消費端也依賴 `connectionPin` 的舊 4-digit 格式？（grep 結果 = 0，供 CCA 二次確認。）

---

## F. Gate 建議

- 本輪對外行為**使用者不需做任何事**即可自動進入「PIN+Session + 6 位 + Brute-force lockout + Auth level 顯示」。
- 若 CCA review 無阻擋項，建議 gate 以 `READY` 結案 S12-4，並**不**立即解鎖 S12-3 / S12-5（仍須按 Sprint12 gate rule「一次只開一門」+ 董事長核可）。
- 若 CCA 認為 §E.3（lockout 使用者語意）需處理，建議作為獨立 follow-up round（例如 `S12-4.1`），不擴張當前 scope。

---

## G. 變更摘要（依 CLAUDE_CODEX_COLLAB_GUIDE 格式）

**A. 變更摘要**
- 新增 `telemetry/` 模組：`ErrorReporter` interface + `AndroidLogReporter` object。
- `RemoteKeyboardService` 新增 `authLevel` 契約 + `AuthLevel` enum。
- `WifiKeyboardServer`：4-digit `kotlin.random` PIN → 6-digit `SecureRandom`；新增 per-client session token；新增 per-IP brute-force tracker（5/60s + 300s lockout）；HTML 同步 6 位、加 session 欄位。
- `BluetoothHidKeyboard`：`authLevel = PAIRED_ONLY`；device name log 改 `BuildConfig.DEBUG` only。
- `RemoteKeyboardPanelView`：新增 `wifiPin` / `authLevel` 欄位與渲染；高度 +40dp。
- Binder + service wiring：`ImeRemotePanelBinder.bind(...)` 擴兩參；`updateRemotePanelState()` 同步寫入。

**B. 風險**
- Panel 高度變化可能影響既有 layout（floating / landscape），待 CCA 視覺審核。
- Session token 只保存於 runtime，app 被 kill 後 client 必須重連（無自動續期 — 是 by design，非 bug）。
- HTML JS 未做 `sessionToken` 為空時的 UX 提示（現在是 silently drop send），CCA 可能會要求加提示。

**C. 下一步建議**
- CCA review → 回 `玄樞` gate → 決定 `READY / NOT READY`。
- 若 READY：不自動解下一門；等董事長指定 S12-3 或 S12-5 或 S13 規劃。
- 若 NOT READY：依 CCA 意見追加小修，不擴 scope。

**D. 架構決策**
- 新增 `com.snowrealm.yukiboard.telemetry` package，作為日後 observability 統一 Gateway。
- `AuthLevel` 成為 remote transport 的 audit 標記，面板可直接讀取而不需要知道底層實作。
- Brute-force 邏輯**留在 `WifiKeyboardServer` 內部**（不抽 util），因為僅此一個 caller；若未來新增其他 network endpoint 再抽。

**E. 玄樞審核需求 / 結論**
- 需要 `玄樞` gate：`READY / NOT READY / LOCKED`。
- 建議在 CCA review 回傳後才開 gate verdict。

---

## H. 二次修（Second Pass — 回應 CCA review）

日期: 2026-04-19
依據: `docs/SPRINT12_4_CCA_REMOTE_KEYBOARD_PIN_CONSUME_REVIEW_2026-04-19.md` §D

### H.1 修項對照表

| # | CCA 分類 | 項目 | 檔案 | 修法 |
|---|---|---|---|---|
| 1 | BLOCKING D.1-1 | Panel auth wording 中文化 | `RemoteKeyboardPanelView.kt` | `PIN + Session` → `PIN + 連線金鑰`（採 CCA 方案 A） |
| 2 | BLOCKING D.1-2 | HTML 5 秒無限重連推向 lockout | `WifiKeyboardServer.kt`（JS 區塊） | 加 `wasConnected` flag + `backToPinInput()` helper；`onclose` 分支；`onerror` 文案中性化 |
| 3 | STRONG D.2-3 | `stop()` 清 PIN + tracker | `WifiKeyboardServer.kt` | `connectionPin = ""` + `attemptTrackers.clear()` |
| 4 | STRONG D.2-4 | Panel PIN label 文案統一 | `RemoteKeyboardPanelView.kt` | `PIN:` → `連線 PIN 碼:` |

### H.2 修項詳細

**H.2.1（D.1-1）Panel auth wording**

```kotlin
// 前
AuthLevel.PIN_PLUS_SESSION -> "🔒 驗證: PIN + Session"
// 後
AuthLevel.PIN_PLUS_SESSION -> "🔒 驗證: PIN + 連線金鑰"
```

其餘兩項 (`PIN` / `PAIRED_ONLY`) 保持既有 wording（CCA §B.2 未要求變）。

**H.2.2（D.1-2）HTML reconnect 邏輯**

採 CCA §B.3 方案 B。新增 JS 片段：

- 新增全域 `wasConnected = false`。
- 新增 `backToPinInput(message)` helper：清 `reconnectTimer` / `currentPin` / `sessionToken` / `wasConnected`、pinInput 值清空、顯示 pinBox、隱藏 textInput、呼叫 `setStatus('disconnected', message)`。
- `connectWithPin()` 在啟動 `connect()` 前 reset `wasConnected = false`（確保失敗分支正確）。
- `ws.onopen`: 設 `wasConnected = true`。
- `ws.onclose` 分支：
  - `wasConnected === true` → 維持原邏輯「5 秒後重連」。
  - `wasConnected === false` → 視為「尚未成功就被 close」（多半 PIN 錯 / lockout），呼叫 `backToPinInput('無法連線，請確認 PIN 碼正確。若已連續輸入錯誤多次，請等待 5 分鐘後再試。')`，**不**再 `setTimeout(connect, 5000)`。
- `ws.onerror`: 留空 body（+ 註解），交由 `onclose` 統一處理，避免先提前覆寫 status。

行為改變：使用者輸錯一次 PIN → WS 403 → server close socket → `onclose` 走「尚未連上」分支 → 回到 PIN 輸入頁（不會再自動重送舊 PIN），**單次手誤不再推進 lockout**。

**H.2.3（D.2-3）stop() 清 PIN + tracker**

```kotlin
override fun stop() {
    running.set(false)
    clients.forEach { it.close() }
    clients.clear()
    try { serverSocket?.close() } catch (_: Exception) {}
    serverSocket = null
    serverThread = null
    connectionState = ConnectionState.IDLE
    connectionPin = ""          // ← 新增
    attemptTrackers.clear()     // ← 新增
}
```

- `connectionPin = ""`：下次 `start()` 會 `generatePin()` 重新產生，清空只在 stop → start 之間有記憶體殘留縫；`ImeServiceMisc.updateRemotePanelState()` 若在 stop 後被呼叫，會自動寫 `wifiPin = ""` 到 panel，畫面立即清乾淨。
- `attemptTrackers.clear()`：避免下次 start 帶舊 lockout 狀態（否則跨 session 可能誤鎖無辜 IP）。

**H.2.4（D.2-4）Panel PIN label 文案**

```kotlin
// 前
canvas.drawText("PIN: $wifiPin", ...)
// 後
canvas.drawText("連線 PIN 碼: $wifiPin", ...)
```

與 HTML 既有「PIN 碼」用字一致。

### H.3 Optional / Deferred 的處理

CCA §D.3 OPTIONAL 兩項（HTML 初始 status class / `ConnectionState.LISTENING` 註解）：**本 pass 不處理**，理由：

- 雙項都屬非 user-facing 風險；
- 初始 status class `connecting` 顯示橙色其實已符合「待處理中」的中性直覺，改 `disconnected`（紅）反而讓 user 誤以為「已失敗」；
- `LISTENING` 註解僅開發者可見，與 runtime 行為無關。

如 CCA final pass 仍要求處理，單獨 follow-up round 即可。

CCA §D.4 DEFERRED 三項維持不在本 round 範圍（主題低對比 fallback / WS close frame reason / 三模式 layout 截圖）。

### H.4 驗證

`JAVA_HOME=... ./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`（1s，僅 pre-existing WifiInfo deprecation warnings，與本 pass 無關）。

### H.5 更新後的檔案清單

本 pass 只碰兩個檔：
- `app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt`（H.2.1 + H.2.4）
- `app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt`（H.2.2 + H.2.3）

其他五檔（`RemoteKeyboardService.kt` / `BluetoothHidKeyboard.kt` / `ImeRemotePanelBinder.kt` / `ImeServicePanels.kt` / `ImeServiceMisc.kt`）以及 `telemetry/` 兩個新檔未動。

### H.6 回 CCA 備忘

- 已修完 D.1 BLOCKING × 2 + D.2 STRONG × 2。
- D.3 兩項 OPTIONAL 採不處理；若 CCA final pass 反對，請明示，CCB 會補修。
- D.4 三項 DEFERRED 不動。
- 下一步：請雪鑰 final pass（只看 wording / JS 邏輯 / stop() 清理語意，不需 re-run 編譯）→ 回傳 `FINAL_REVIEWED` → 送 `玄樞` gate。

# S12-4-CCA — Remote Keyboard PIN Hardening Consume Review

日期: 2026-04-19
身份: 雪鑰 (CCA — consume / UX / 設定面 / wording / lockout 語意)
任務 ID: `S12-4-CCA-REMOTE-KEYBOARD-PIN-CONSUME-REVIEW`
審查對象:
- `docs/SPRINT12_4_CCB_REMOTE_KEYBOARD_PIN_HARDENING_2026-04-19.md`
- `app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt`
- `app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt`（含內建控制網頁 HTML）
- `app/src/main/java/com/snowrealm/yukiboard/remote/RemoteKeyboardService.kt`
- `app/src/main/java/com/snowrealm/yukiboard/remote/BluetoothHidKeyboard.kt`
- `app/src/main/java/com/snowrealm/yukiboard/telemetry/{ErrorReporter,AndroidLogReporter}.kt`
- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeRemotePanelBinder.kt`
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceMisc.kt`

狀態: `REVIEWED — RECOMMEND READY WITH 2 BLOCKING + 4 MINOR FIXES`

---

## A. 總結論

| 維度 | 判定 |
|---|---|
| Runtime hardening 強度（PIN entropy / brute-force / session token） | ✅ PASS — 6-digit + 5/60s + 300s lockout + 128-bit session 對 app-layer 已足夠 |
| Telemetry 介面 | ✅ PASS — interface + Log impl + 不接 Crashlytics 符合 S12-5 預留 |
| BT device-name release 潔化 | ✅ PASS — 兩處 callback 已 BuildConfig.DEBUG gate |
| 設定面 wording / 使用者語意 | ⚠️ NEEDS_CHANGE — 見 §B/§D，2 項 BLOCKING + 4 項 MINOR |
| Lockout UX | ❌ BLOCKING — 瀏覽器端 5 秒無限重連會把使用者「打錯一次 PIN」加速推進 lockout（見 §B.3） |
| PIN lifecycle 殘留 | ⚠️ NEEDS_CHANGE — stop() 未清 PIN，記憶體 / snapshot 殘留風險 |

**Gate 建議**：CCA 把 §D BLOCKING 兩項回拋給 CCB 二次修，修畢後 CCA 二次過目 → 玄樞 gate 判 `READY`。MINOR 4 項可同次修或列為 follow-up，由 CCB 自行判斷。

---

## B. CCB §E 六項逐項審核

### B.1 PIN 顯示語意（§E.1）

#### B.1.a LISTENING 才出現、CONNECTED 後隱藏

✅ **PASS**。語意正確 — 連線中再曝 PIN 對「對方已驗證通過」這個事實是冗訊；隱藏避免肩看者抄走。

對照 `RemoteKeyboardPanelView.kt:151`：
```kotlin
if (wifiPin.isNotEmpty() && wifiState == ConnectionState.LISTENING) {
    pinPaint.color = theme.candidateSelectedColorInt
    canvas.drawText("PIN: $wifiPin", 12 * density, statusY + 58 * density, pinPaint)
}
```
條件正確。

#### B.1.b Panel 高度 200 → 240dp

⚠️ **NEEDS_VERIFY**（非 BLOCKING，CCB 自驗即可）

CCA 無法直接跑 emulator。需 CCB 在以下三個情境快速截圖確認無遮擋：
1. 一般直立 + 預設鍵盤高度
2. 橫向（landscape）
3. 懸浮鍵盤模式（FloatingKeyboardWrapper）

特別注意懸浮模式下 panel 高度可能撞到 wrapper header。若任一截斷，建議 PIN row 與 auth row 改更緊湊（例如把 auth 11sp 改 10sp、行距收緊）而非繼續加高。

#### B.1.c 18sp monospace + theme candidateSelected 對比

⚠️ **MINOR_OPTIONAL**

`pinPaint.color = theme.candidateSelectedColorInt` 在多數主題下對比足夠，但 YukiBoard 有「雪白主題」之類淡色系，candidateSelected 可能本身淺色 → 18sp 數字洗掉。

建議：
- **方案 A**（保守）：保持現況，等實際使用者反映再處理
- **方案 B**（CCA 偏好）：對 PIN row 加最小亮度保證 — 若 candidateSelected 與背景對比 < WCAG AA，fallback 到 keyLabelColor 或固定一個高對比黃（與 URL 同色）

CCA 不阻擋本 round，列為 follow-up。

---

### B.2 Auth level 標示（§E.2）

❌ **BLOCKING — wording 對非技術使用者不可解**

對照 `RemoteKeyboardPanelView.kt:156-161`：
```kotlin
val authText = when (authLevel) {
    AuthLevel.PIN_PLUS_SESSION -> "🔒 驗證: PIN + Session"
    AuthLevel.PIN -> "🔒 驗證: PIN"
    AuthLevel.PAIRED_ONLY -> "🔒 驗證: 系統配對"
    AuthLevel.NONE -> ""
}
```

「**Session**」是技術術語，使用者不會理解這代表「每條訊息要帶連線金鑰」這層保護。對普通使用者就只是「Session 是什麼？我為什麼要看到？」

**建議改 wording（請 CCB 二擇一）：**

| 方案 | PIN_PLUS_SESSION | PIN | PAIRED_ONLY |
|---|---|---|---|
| A 中文化 | 「🔒 驗證: PIN + 連線金鑰」 | 「🔒 驗證: PIN」 | 「🔒 驗證: 系統配對」 |
| B 等級制 | 「🔒 安全等級: 高（PIN + 動態金鑰）」 | 「🔒 安全等級: 中（僅 PIN）」 | 「🔒 安全等級: 中（系統配對）」 |

CCA 偏好 **方案 A**：保留技術細節摘要 + 中文化 Session，符合 YukiBoard 過去「不過度抽象、保留可讀的技術詞」的風格（對照 SettingsActivity 既有 wording）。

不需要追加 tooltip / 說明面板（佔空間，且 panel 在 IME context 不適合彈跳浮層）。

---

### B.3 Lockout 使用者語意（§E.3）

❌ **BLOCKING — HTML 重連邏輯會把單次手誤推成 5 分鐘 lockout**

對照 `WifiKeyboardServer.kt:542-546`：
```js
ws.onclose = () => {
    setStatus('disconnected', '已斷線');
    addLog('連線已斷開，5 秒後重連...');
    reconnectTimer = setTimeout(connect, 5000);
};
```

**情境**：使用者輸入錯 PIN → server 回 403 + close socket → onclose 觸發 → 5 秒後重連（用同個錯誤 PIN）→ 又 403 → 再 5 秒重連 → ...

**結果**：5 秒 × 5 次 = **25 秒**內就被 lockout 5 分鐘，遠小於 60 秒 brute-force window。使用者體驗：「打錯一次就被鎖 5 分鐘，且完全不知道為什麼」。

且 `onerror` 寫「**連線錯誤（PIN 碼錯誤？）**」更誤導 — 帶問號表示推測，但實際情境可能是 lockout、網路斷線、IP 變更。

**建議改法（CCB 二次修，請二擇一）：**

| 方案 | 改動 |
|---|---|
| A 最小修 | onclose 區分「已連線過後斷線（auto reconnect ok）」 vs 「沒連上就斷（PIN 錯，停止 reconnect，回 PIN 輸入頁）」 — 用一個 `wasConnected` flag 判斷 |
| B 完整修 | A + onerror 文案改「無法連線。請確認 PIN 碼正確。若已連續輸入錯誤多次，請等待 5 分鐘後再試。」+ pinBox display 重新設回 `''`（讓使用者重輸） |

CCA 強烈推薦 **方案 B**（HTML 全部都在 `buildControlPageHtml()` 內，CCB 改一段 inline JS 即成本低）。

**手機端 panel** 是否需「⚠️ 偵測到 brute-force 嘗試」訊息？

⚠️ **MINOR_OPTIONAL**：可選。Telemetry 已寫 Log + ErrorReporter，使用者層面看到這類訊息會懷疑「是不是被駭」反而焦慮。CCA 建議**不在 panel 顯示**，但保留 telemetry 給未來真要做時可消費。

---

### B.4 文案一致性（§E.4）

⚠️ **MINOR**

| 出處 | 字串 |
|---|---|
| Panel L153 | `"PIN: $wifiPin"` |
| HTML L478 | `"請輸入 PIN 碼連線"` |
| HTML L482 | `"輸入 6 位 PIN 碼"` |
| HTML L525 | `"PIN 碼需為 6 位數"` |

不一致：HTML 全用「**PIN 碼**」（含「碼」字），Panel 只用「**PIN**」（無「碼」字）。

**建議 CCB 改**：Panel 統一改成 `"連線 PIN 碼: $wifiPin"`（更明確「這串數字是要讓對方輸入的」），或最少改成 `"PIN 碼: $wifiPin"`。三處 HTML 字維持不變。

---

### B.5 PIN lifecycle（§E.5）

⚠️ **NEEDS_CHANGE**

對照 `WifiKeyboardServer.kt:149-157`：
```kotlin
override fun stop() {
    running.set(false)
    clients.forEach { it.close() }
    clients.clear()
    try { serverSocket?.close() } catch (_: Exception) {}
    serverSocket = null
    serverThread = null
    connectionState = ConnectionState.IDLE
}
```

`connectionPin` 在 stop() **未清空**，仍持有上次的值。雖然：
- Panel 渲染條件 `wifiState == ConnectionState.LISTENING` 已避免曝
- Server socket 已關，外部無法用舊 PIN 連線

但記憶體 / snapshot / debugger inspect 仍可讀到。考慮 IME 服務常駐特性，這個殘留會**長期累積在 process 記憶體**直到 IME 被 system kill。

**建議 CCB 改法**（一行修）：
```kotlin
override fun stop() {
    running.set(false)
    // ...
    connectionState = ConnectionState.IDLE
    connectionPin = ""   // ← 加這行；start() 會在啟動時 generatePin()
    attemptTrackers.clear()  // ← 順手清，避免下次 start 帶舊 lockout 狀態
}
```

順帶：`ImeServiceMisc.updateRemotePanelState()` 在 stop 後若仍被呼叫，會把 `wifiPin = ""` 寫入 panel（因為 `connectionPin = ""`），panel 自動清。✅

---

### B.6 開放問題（§E.6）

#### B.6.a IME 被 kill 重建後 PIN 遺失

✅ **PASS**（與 B.3 修法整合即可）

採 §B.3 方案 B 後，重建後若使用者不再有正確 PIN，HTML 會回 PIN 輸入頁，使用者在面板看到新 PIN 後重輸即可。不必額外加 panel 提示「請重新輸入」。

#### B.6.b 舊 4-digit 格式無其他 caller

✅ **PASS — CCA 二次確認**

CCA 執行 `Grep "connectionPin"` 確認 5 處引用：
```
WifiKeyboardServer.kt:89   定義
WifiKeyboardServer.kt:122  start() reset
WifiKeyboardServer.kt:237  PIN 比對
ImeServicePanels.kt:208    讀取寫入 panel
ImeServiceMisc.kt:421      讀取寫入 panel
```
無外部消費端依賴 4-digit 字面長度。✅

---

## C. CCA 額外發現（CCB §E 未列）

### C.1 BT 連線時 panel 顯示 `connectedDeviceName`

對照 `RemoteKeyboardPanelView.kt:142-144`：
```kotlin
if (connectedDeviceName.isNotEmpty()) {
    canvas.drawText("裝置: $connectedDeviceName", 12 * density, statusY + 16 * density, statusPaint)
}
```

CCB Q4 把 BT log device name 改 `BuildConfig.DEBUG only`，但 panel 仍會顯示完整 device name（user 必須知道連到哪台 — 這是 UX 必要）。**這個不一致是合理的**（log 是給開發者，panel 是給使用者）。

⚠️ **MINOR_OBSERVATION**：對「肩看者」來說 panel 上的 device name 也可被看到，但這是 user 主動需要的訊息（user 必須確認連到哪台），不應砍。**CCA 結論：保留現況。**

### C.2 HTML status 初始 wording

`WifiKeyboardServer.kt:478`：
```html
<div id="status" class="status connecting">請輸入 PIN 碼連線</div>
```

`status` class 為 `connecting`（橙色） — 但實際狀態是「待輸入 PIN」，用 `connecting` 顏色容易讓使用者以為「自動連線中，我不用做什麼」。

⚠️ **MINOR**：建議 CCB 改初始 class 為 `disconnected`（紅色）或新增一個 `pending` class（中性灰色）。次要，CCA 不阻擋。

### C.3 ConnectionState `LISTENING` 註解

對照 `RemoteKeyboardService.kt:51`：
```kotlin
LISTENING,      // 等待連線（WiFi Server 模式）
```

註解未提到「也是 PIN 顯示時段」。`LISTENING` 現在多承擔了「PIN 對外可見」這層語意，建議 CCB 補一行註解：
```kotlin
LISTENING,      // 等待連線（WiFi Server 模式；PIN 對使用者面板可見）
```

⚠️ **MINOR**

---

## D. 建議 CCB 二次修項清單

### D.1 BLOCKING（必須修，否則 CCA 不送 gate）

| # | 項 | 修法 | 預估改動 |
|---|---|---|---|
| 1 | Panel auth level wording 「PIN + Session」中文化 | `RemoteKeyboardPanelView.kt:156-161` 採 §B.2 方案 A | 4 行字串改 |
| 2 | HTML 5 秒無限重連 → 區分 PIN 錯誤情境停止重連 + onerror wording 修正 | `WifiKeyboardServer.kt buildControlPageHtml()` JS 區塊採 §B.3 方案 B | ~15 行 JS |

### D.2 STRONG RECOMMEND（同次修）

| # | 項 | 修法 |
|---|---|---|
| 3 | `WifiKeyboardServer.stop()` 清 `connectionPin` + `attemptTrackers` | `stop()` 加 2 行 |
| 4 | Panel PIN row wording 統一為「連線 PIN 碼」 | `RemoteKeyboardPanelView.kt:153` 1 行字串改 |

### D.3 OPTIONAL（CCB 自行判斷是否同次或 follow-up）

| # | 項 | 修法 |
|---|---|---|
| 5 | HTML 初始 status class 由 `connecting` 改 `disconnected` 或 `pending` | 1 行 |
| 6 | `ConnectionState.LISTENING` 註解補充 | 1 行 |

### D.4 DEFERRED（不在本 round）

| # | 項 | 預定 round |
|---|---|---|
| 7 | Panel 主題低對比下 PIN 字洗白的 fallback 機制 | 觀察使用者反映後再開（無預定 round） |
| 8 | Server WebSocket close frame 帶 reason 區分 403/429 | Sprint 13+ TLS 範圍順手做 |
| 9 | Panel 高度三模式截圖驗證 | CCB 二次修時順手截 |

---

## E. Gate 建議路徑

```
CCB 二次修 D.1 + D.2（4 項，估 30 分鐘內）
  ↓
CCA 二次過目（只看 wording / JS 邏輯，不 re-run 編譯）
  ↓
玄樞 gate 判 READY（建議 ✅）
  ↓
S12-4 結案；S12-3 / S12-5 維持 LOCKED 等董事長指示
```

若 CCB 認為 D.1 第 2 項（HTML JS 改動）有 regression 風險，可提議：
- 替代路徑：D.1 第 2 項只改 onerror wording（從「PIN 碼錯誤？」改「無法連線，請確認 PIN 碼正確或稍後再試」），先閉環語意誤導；重連邏輯改動延後 follow-up。
- 但 CCA 不偏好此替代 — 重連邏輯本身會主動把使用者推向 lockout，是 hardening 的反向力。建議完整修。

---

## F. 對 S12-4 整體的肯定

CCA 必須先說清楚：

1. **CCB 在 runtime hardening 上的決策非常乾淨** —— SecureRandom + 6-digit + per-IP brute tracker + per-client session token + edge-only telemetry 是教科書級的最小侵入式 app-layer 防護組合。
2. **AuthLevel enum 的引入是優雅的** —— 給 panel / 未來 audit 一個低耦合的讀取點。
3. **不接 Crashlytics 是對的選擇** —— ErrorReporter interface 為 S12-5 留好替換點，避免本 round 越權。
4. **BluetoothHidKeyboard release 潔化** —— 對 PII 敏感度的處理符合 SnowRealm 既有 release contract 風格。

CCA review 的 BLOCKING 兩項都是「使用者可見的 wording / 重連邏輯」層面，**不影響 runtime 強度**。

---

## G. 交付者自白

- 本 review 由 `雪鑰`（CCA）落地，僅文件，不動 .kt（嚴守 `feedback_role_boundary.md` — CCA 只審不實作）。
- 所有 BLOCKING / NEEDS_CHANGE 項都在 §D 列出，由 CCB 二次修；CCA 不混改同一檔。
- §B.6.b 對 CCB 提及的「0 caller」做了二次確認，結果一致。
- §C 三項是 CCB §E 未列、CCA 額外發現的 wording / 註解問題，全屬 MINOR，不阻擋 gate。
- Gate 建議：CCB 修 §D BLOCKING 兩項（D.1 第 1、2 項）後，CCA 過目即可送玄樞判 READY。
- 不自排下一 round；S12-3 / S12-5 解鎖由董事長 / 玄樞拍板。

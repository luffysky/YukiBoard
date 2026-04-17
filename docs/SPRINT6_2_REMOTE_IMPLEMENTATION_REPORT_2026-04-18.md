# Sprint 6-2 REMOTE Implementation Report（2026-04-18）

回交人：Claude Code B

任務 ID：`S6-2-CCB-REMOTE-IMPLEMENTATION`

依據：`Sprint6-All-Round-Gate.md` Round `S6-2 REMOTE Panel Boundary`

## A. 變更摘要

### A.1 新增檔案

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeRemotePanelBinder.kt`
  - `object ImeRemotePanelBinder`
  - 單一方法 `bind(context, theme, btState, wifiState, wifiUrl, connectedDeviceName, onBluetoothToggle, onWifiToggle): RemoteKeyboardPanelView`
  - 只負責 panel 建構 + 初始狀態 snapshot + toggle callback 掛接
  - 不接任何 listener，避免與 service 端 `updateRemotePanelState()` 形成重複 writer
  - 不反向依賴 `BluetoothHidKeyboard` / `WifiKeyboardServer` / `RemoteKeyboardService`

### A.2 修改檔案

- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
  - 新增 import：`ImeRemotePanelBinder`
  - `createHighDependencyPanelOrNull()` 的 `PanelType.REMOTE` 分支：
    - 原本 12 行 inline（panel 建構 + 8 屬性賦值 + 2 callback 掛接）
    - 收斂為 11 行 binder call（所有具名參數），尾端 `.also` 保留 `imePanelBindings.remotePanel = it` 賦值
  - Bindings slot 賦值刻意留在 service 端：`updateRemotePanelState()` 是 cross-cutting writer，owner 必須在 service 層，不下放到 binder

### A.3 未改檔案（遵守禁區）

| 禁區 | 狀況 |
| --- | --- |
| `BluetoothHidKeyboard.kt` | ✅ 未動 |
| `WifiKeyboardServer.kt` | ✅ 未動 |
| `RemoteKeyboardService.kt` | ✅ 未動 |
| `YukiBoardIMEService.kt::handleKeyPress()` 主語意 | ✅ 未動 |
| `KeyboardView.kt` | ✅ 未動 |
| `InputRouter.kt` | ✅ 未動 |
| settings 檔 | ✅ 未動 |
| Sprint 7 檔 | ✅ 未動 |
| `RemoteKeyboardPanelView.kt` | ✅ 未動（允許但無必要） |
| `ImeServiceMisc.kt`（`toggleBluetooth / toggleWifi / updateRemotePanelState`） | ✅ 未動 |
| `YukiBoardIMEService.kt`（整檔） | ✅ 未動 |

### A.4 REMOTE 分支收斂前後對照

**Before（12 行 inline）**
```kotlin
PanelType.REMOTE -> {
    val p = RemoteKeyboardPanelView(this).also { imePanelBindings.remotePanel = it }
    p.theme = theme
    p.btState = btKeyboard?.connectionState ?: ConnectionState.IDLE
    p.wifiState = wifiServer?.connectionState ?: ConnectionState.IDLE
    p.wifiUrl = wifiServer?.getConnectionUrl() ?: ""
    p.connectedDeviceName = btKeyboard?.connectedDevice?.name
        ?: wifiServer?.connectedDevice?.name ?: ""
    p.onBluetoothToggle = { toggleBluetooth() }
    p.onWifiToggle = { toggleWifi() }
    p
}
```

**After（11 行 binder call，語義 1:1 對應）**
```kotlin
PanelType.REMOTE -> ImeRemotePanelBinder.bind(
    context = this,
    theme = theme,
    btState = btKeyboard?.connectionState ?: ConnectionState.IDLE,
    wifiState = wifiServer?.connectionState ?: ConnectionState.IDLE,
    wifiUrl = wifiServer?.getConnectionUrl() ?: "",
    connectedDeviceName = btKeyboard?.connectedDevice?.name
        ?: wifiServer?.connectedDevice?.name ?: "",
    onBluetoothToggle = { toggleBluetooth() },
    onWifiToggle = { toggleWifi() },
).also { imePanelBindings.remotePanel = it }
```

## B. Code-path Smoke Checklist

本機無連接裝置（Bluetooth HID / WiFi WebSocket 皆需實機或同網段瀏覽器驗證），因此改以 code-path 比對取代實機 smoke。

### B.1 panel 開啟

| 預期行為 | Before 路徑 | After 路徑 | 狀態 |
| --- | --- | --- | --- |
| `RemoteKeyboardPanelView` 建構 | `RemoteKeyboardPanelView(this)` | `ImeRemotePanelBinder.bind(context = this, ...)` | ✅ 等價 |
| `imePanelBindings.remotePanel` 被賦值 | `.also { imePanelBindings.remotePanel = it }` | `.also { imePanelBindings.remotePanel = it }` | ✅ 保留 |
| `theme` 掛接 | `p.theme = theme` | binder 內 `panel.theme = theme` | ✅ 等價 |
| 初始 `btState` snapshot | `p.btState = btKeyboard?.connectionState ?: ConnectionState.IDLE` | binder 接收同表達式 | ✅ 等價 |
| 初始 `wifiState` snapshot | 同上 | 同上 | ✅ 等價 |
| 初始 `wifiUrl` snapshot | `p.wifiUrl = wifiServer?.getConnectionUrl() ?: ""` | binder 接收同表達式 | ✅ 等價 |
| 初始 `connectedDeviceName` snapshot | 完整 null-chain 表達式 | binder 接收同表達式 | ✅ 等價 |

### B.2 Bluetooth toggle callback

| 預期行為 | Before 路徑 | After 路徑 | 狀態 |
| --- | --- | --- | --- |
| 點按 BT 按鈕 → `panel.onBluetoothToggle()` | `p.onBluetoothToggle = { toggleBluetooth() }` | binder 透過 `panel.onBluetoothToggle = onBluetoothToggle` 掛接同 lambda `{ toggleBluetooth() }` | ✅ 等價 |
| lambda 呼叫 `YukiBoardIMEService.toggleBluetooth()`（`ImeServiceMisc.kt:359`） | ✅ | ✅ | ✅ 等價 |
| `toggleBluetooth()` 內部流程（服務建立 / 斷開 / `updateRemotePanelState()`）| 未動 | 未動 | ✅ 保留 |

### B.3 WiFi toggle callback

| 預期行為 | Before 路徑 | After 路徑 | 狀態 |
| --- | --- | --- | --- |
| 點按 WiFi 按鈕 → `panel.onWifiToggle()` | `p.onWifiToggle = { toggleWifi() }` | binder 掛接同 lambda | ✅ 等價 |
| lambda 呼叫 `YukiBoardIMEService.toggleWifi()`（`ImeServiceMisc.kt:382`） | ✅ | ✅ | ✅ 等價 |
| `toggleWifi()` 內部流程（server 建立 / listener 三件組 / `updateRemotePanelState()`） | 未動 | 未動 | ✅ 保留 |

### B.4 連線狀態 push 到 panel（持續更新）

| 預期行為 | 路徑 | 狀態 |
| --- | --- | --- |
| Bluetooth listener → `updateRemotePanelState()` | `ImeServiceMisc.kt:363, 369` | ✅ 未動 |
| WiFi listener → `updateRemotePanelState()` | `ImeServiceMisc.kt:386, 392, 401` | ✅ 未動 |
| `updateRemotePanelState()` 內寫入 `imePanelBindings.remotePanel?.apply { ... }` | `ImeServiceMisc.kt:404-423` | ✅ 未動 |
| Bindings slot 仍由 service 端 `panel 建立時賦值 / 讀寫` | ✅ | ✅ |

### B.5 panel 關閉後不殘留新 writer / 新 listener

| 檢查項 | 狀況 |
| --- | --- |
| Binder 內部是否 `addListener` / 註冊 callback 給 `btKeyboard` / `wifiServer` | ❌ 無，binder 純 panel 建構 |
| Binder 內部是否 `setOnStateChangedListener` / `setOnRemoteKeyListener` / `setOnRemoteTextListener` | ❌ 無 |
| Binder 是否持有 `YukiBoardIMEService` 強參考 | ❌ 無（只接受 `Context` + 已封裝的 toggle lambdas） |
| 新增的 writer 路徑數量 | 0（唯一 writer 仍是 service 端 `updateRemotePanelState()`） |
| 新增的 listener 路徑數量 | 0 |

### B.6 Panel 關閉時 bindings 是否清除

> 觀察（非本輪修補範圍）：`ImePanelBindings.clearInputModeBindings()` 目前不清 `remotePanel`。本輪維持既有行為；Sprint 6-2 不處理 `remotePanel` 生命週期課題。

## C. 邊界純度自評

### C.1 binder → 外部依賴

| binder 依賴 | 類型 |
| --- | --- |
| `android.content.Context` | framework |
| `com.snowrealm.yukiboard.remote.ConnectionState` | enum 值（不經由 `BluetoothHidKeyboard` / `WifiKeyboardServer` 實例）|
| `com.snowrealm.yukiboard.theme.KeyboardTheme` | 資料類 |
| `com.snowrealm.yukiboard.ui.panel.RemoteKeyboardPanelView` | panel view |

**未引入**：`BluetoothHidKeyboard` / `WifiKeyboardServer` / `RemoteKeyboardService` / `YukiBoardIMEService`。

### C.2 未擴張功能面

- 未加新 state field
- 未加新 listener 註冊 / 註銷
- 未改變連線狀態推送路徑
- 未改變 toggle 行為
- 未改 `ImePanelBindings` 結構（`remotePanel` slot 原本即已存在）
- 未改 panel 開關主幹（`handlePanelToggle / routeToggle / attachPanel`）

## D. 風險

### D.1 本輪引入的風險

無新風險。binder 僅 panel 建構重構，所有外部可觀察行為等價。compile 通過（`:app:compileDebugKotlin` BUILD SUCCESSFUL in 2s）。

### D.2 既存風險（本輪不動）

1. `ImePanelBindings.clearInputModeBindings()` 未清 `remotePanel`；panel 關閉後 bindings 仍指向舊 view。此為 Sprint 6-2 之外的邊界課題，非本輪修補範圍。
2. 本機無實機；完整 smoke 僅能由實機覆蓋（Android 藍牙配對、WiFi 同網段瀏覽器連線）。code-path 比對已逐項完成。

## E. 請 CodeX A 判定

### E.1 交付要求逐項自評

| 要求 | 狀態 |
| --- | --- |
| 1. REMOTE panel 建立邊界更清楚 | ✅ 12 行 inline → binder call + service-side bindings 賦值 |
| 2. service 端 REMOTE 建立邏輯最小收斂 | ✅ 收斂；bindings slot 賦值刻意留在 service 端維持 cross-cutting writer 一致性 |
| 3. `:app:compileDebugKotlin` 通過 | ✅ BUILD SUCCESSFUL in 2s |
| 4. 回交附 smoke 結果或無法執行原因 | ✅ 本機無裝置，改以 code-path smoke checklist 取代（§B） |
| 5. code-path smoke 含 panel 開啟 / BT toggle / WiFi toggle / 關閉不殘留新 writer listener | ✅ §B.1–B.5 |
| 6. 不自排下一輪 | ✅ 本文件無順序建議 |

### E.2 待 CodeX A 裁決項

1. **判定結果（擇一）**
   - `READY` → Sprint 6-2 視為完成
   - `NOT READY` → 請指出補強項
   - `LOCKED` → 請指出哪條底線被越線

2. **是否需要實機 smoke 補交**
   - 本機無裝置，若需 Bluetooth HID / WiFi WebSocket 實機驗證，需 luffysky 或 CodeX B 以實機或同網段瀏覽器執行
   - 是否要求此輪補實機 smoke，請 `CodeX A` 指定

3. **下一步指派**（候選方向，僅供 `CodeX A` 參考，待 `CodeX A` 指定）
   - 未來 panel round：`THEME` / `CLAW` / `HANDWRITING` / `USER_DICT`（仍 NOT READY）
   - 其他 bugfix / audit round
   - 其他方向

以上。等 `CodeX A` 指示。

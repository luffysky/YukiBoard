# SPRINT6 Panel Risk Matrix（2026-04-18）

任務 ID：`S6-0-CXB-RISK-MATRIX`  
身份：`CodeX B`  
日期：`2026-04-18`

## 1. 目的

本文件只服務 `Sprint 6-0`：

- 盤點 6 個高依賴 panel 的實際依賴與風險
- 給 `CodeX A` 一個可執行的排序建議
- 明確指出第一個 panel 應該先做哪個最安全
- 明確指出其餘 5 個 panel 為什麼現在不該先做

本文件**不是** implementation 設計稿，也**不是** `Sprint 6-1+` 開工授權。

## 2. 總結判定

### 建議第一個 panel

`MUSIC`

### 理由

1. `MUSIC` 的 service 直接依賴最少，主要只有 `keySoundEngine` 與 `MidiImportActivity` 啟動。
2. 它幾乎不碰 `ImeUiState`、不碰 panel 外部共享狀態、也不碰 auth / provider / sync。
3. 它不依賴 `KeyboardView.updateCandidates(...)`、`inputRouter`、`currentInputConnection` 這類熱路徑。
4. 即使邊界薄切失敗，影響範圍仍大致侷限在 panel UI、音效引擎、匯入流程，不容易污染 IME 主輸入語意。
5. 很適合作為 `Sprint 6` 的第一個「流程驗證 panel」：先驗證高依賴 panel 的 boundary / scope / gate 節奏能否穩定複製，再去碰更危險的 `THEME / CLAW / REMOTE / HANDWRITING`。

### 建議排序

1. `MUSIC`
2. `USER_DICT`
3. `REMOTE`
4. `THEME`
5. `CLAW`
6. `HANDWRITING`

### 對 `THEME` 的正式判定

`THEME` **不建議作為第一個 panel**。

理由不是它不能做，而是它目前同時跨到：

- `ThemeManager`
- `applyTheme(...)`
- `KeyboardBackgroundView`
- `BackgroundPickerActivity`
- `prefs.useCustomBackground`
- `ACTION_BG_UPDATED` broadcast
- `keyboardView` / `backgroundView` / `setInputView(onCreateInputView())`
- settings 端共用的背景與主題資料來源

這會讓第一個 Sprint 6 panel 同時承擔：

- panel host 邊界
- theme render 邊界
- background refresh 邊界
- settings 共用資料來源邊界

這不適合當第一個試點。

## 3. 風險矩陣

| Panel | Service 直接依賴 | 外部依賴密度 | Runtime 風險 | 狀態/副作用風險 | 綜合風險 | 排序 |
|------|------------------|--------------|--------------|-----------------|----------|------|
| `MUSIC` | 低 | 低 | 低到中 | 低 | 最低 | 1 |
| `USER_DICT` | 中 | 低到中 | 中 | 中 | 中低 | 2 |
| `REMOTE` | 中到高 | 高 | 高 | 中到高 | 中高 | 3 |
| `THEME` | 高 | 中 | 高 | 很高 | 高 | 4 |
| `CLAW` | 中到高 | 很高 | 高 | 高 | 很高 | 5 |
| `HANDWRITING` | 高 | 高 | 很高 | 很高 | 最高 | 6 |

## 4. Panel 逐一盤點

## 4.1 `MUSIC`

### Service 直接依賴

- `ImeServicePanels.kt:393`
- `YukiBoardIMEService.keySoundEngine`
- `MidiImportActivity`

### Manager / repository / client / engine 依賴

- `KeyboardSoundEngine`
- `MidiImportActivity.onMidiImported` 靜態回呼

### UI consume / callback / side effect owner

- UI consume owner：`MusicPanelView`
- callback owner：`onImportMidi`
- side effect owner：
  - `YukiBoardIMEService` 啟動 `MidiImportActivity`
  - `MusicPanelView` 使用 `KeyboardSoundEngine` 播放音效

### Runtime 風險

- `MidiImportActivity.onMidiImported` 是靜態 callback，生命週期與回收風險需要留意
- `keySoundEngine` 同時也是鍵盤主輸入音效來源，若邊界切不好可能誤傷一般按鍵音效
- 但整體仍屬局部風險，不直接碰輸入主流程或 candidate / composing 狀態

### 判定

適合當第一個 panel。  
風險存在，但多半侷限在 panel 自身與媒體匯入，不會直接把輸入主流程拖下水。

## 4.2 `USER_DICT`

### Service 直接依賴

- `ImeServicePanels.kt:343`
- `showImeDialog(...)`
- 匯出檔案分享
- 匯入檔案讀取
- `enterInputMode()`

### Manager / repository / client / engine 依賴

- `UserDictionary`
- `InputRouter` 也直接持有 `UserDictionary`
- `CloudSyncManager` 也直接讀寫 `UserDictionary`
- `SettingsActivity` 也直接使用 `UserDictionary`

### UI consume / callback / side effect owner

- UI consume owner：`UserDictionaryPanelView`
- callback owner：
  - `onShowDialog`
  - `onExportRequest`
  - `onImportRequest`
- side effect owner：
  - `YukiBoardIMEService` 透過 `showImeDialog(...)` 顯示 Dialog
  - service 端負責檔案匯出分享與匯入讀檔

### Runtime 風險

- 與 `InputRouter` 共用同一份 user dictionary 資料來源
- 與 settings / sync 也共用資料來源，容易牽涉 state ownership
- 屬於「資料共享比 UI 邊界更敏感」的 panel
- 但比 `THEME / CLAW / HANDWRITING` 好的地方是：沒有直接碰 candidate render、網路 provider、外部 server 或模型下載

### 判定

可以排第二，但不建議先於 `MUSIC`。  
它的風險不是 panel UI，而是 shared data source 已經同時被 IME / settings / sync 使用。

## 4.3 `REMOTE`

### Service 直接依賴

- `ImeServicePanels.kt:196`
- `toggleBluetooth()`
- `toggleWifi()`
- `YukiBoardIMEService.btKeyboard`
- `YukiBoardIMEService.wifiServer`
- `sendKeyEvent(...)`
- `sendText(...)`

### Manager / repository / client / engine 依賴

- `BluetoothHidKeyboard`
- `WifiKeyboardServer`
- `RemoteKeyboardService`
- `ConnectionState`

### UI consume / callback / side effect owner

- UI consume owner：`RemoteKeyboardPanelView`
- callback owner：
  - `onBluetoothToggle`
  - `onWifiToggle`
- side effect owner：
  - service 端控制藍牙 HID 啟停
  - service 端控制 Wi-Fi server 啟停
  - 連線狀態與裝置名稱由 service/remote service 回寫 panel

### Runtime 風險

- 直接碰外部連線生命週期
- 藍牙 HID 註冊/反註冊與 Wi-Fi server 啟停都不是單純 UI 事件
- 已經不只是 panel 顯示；它會跨到 remote keyboard 實際輸出通道
- 若邊界切壞，可能傷到遠端輸入能力，不只是 panel 顯示

### 判定

不適合第一個 panel。  
雖然比 `HANDWRITING` 安全，但它已經跨到外部 integration 與 transport lifecycle，太早做會把 Sprint 6 變成半個 remote 系統重構。

## 4.4 `THEME`

### Service 直接依賴

- `ImeServicePanels.kt:208`
- `ThemeManager.getInstance(...)`
- `applyTheme(...)`
- `backgroundView`
- `keyboardView`
- `toolbarView.setAnimStyle(...)`
- `setInputView(onCreateInputView())`
- `showTransparencyOverlay()`（toolbar long-press）

### Manager / repository / client / engine 依賴

- `ThemeManager`
- `KeyboardBackgroundView`
- `BackgroundPickerActivity`
- `YukiPreferences`
- `ImeThemeRenderDelegate`

### UI consume / callback / side effect owner

- UI consume owner：`ThemePanelView`
- callback owner：
  - `onThemeSelected`
  - `onAnimThemeSelected`
  - `onPickBackground`
  - `onPickVideo`
  - `onClearBackground`
  - `onOverlayAlphaChanged`
  - `onLabelColorChanged`
  - `onBackgroundSettingsChanged`
  - `onKeySettingsChanged`
  - `onPerKeySizeChanged`
  - `onKeyboardHeightChanged`
- side effect owner：目前大量仍在 `YukiBoardIMEService`

### Runtime 風險

- 同時碰主題切換、背景檔案、廣播、keyboard 重建、透明模式、個別按鍵尺寸、鍵盤高度
- 同一套資料來源也被 settings 主題/背景頁使用
- 一個 panel 就橫跨 UI render、prefs、storage、activity launch、broadcast、IME view recreate

### 判定

不建議先做。  
雖然原先 Sprint 6 詳拆把 `THEME` 放在 6-1，但以目前實際耦合來看，拿它做第一個 panel 風險過高，會讓 Sprint 6 的第一輪失去「可控試點」特性。

## 4.5 `CLAW`

### Service 直接依賴

- `ImeServicePanels.kt:303`
- `YukiBoardIMEService.clawClient`
- `currentInputConnection`
- `inputRouter.currentAppPackage`
- `keyboardView.currentMode`
- `clawResultFloatingPanel?.showResult(...)`
- `InviteCodeActivity`
- 被動防詐泡泡可直接 `showPanel(PanelType.CLAW)`
- `YukiBoardIMEService` 還會直接 `(panelContainer.getChildAt(0) as? ClawPanelView)?.triggerSkill("fraud_detect")`

### Manager / repository / client / engine 依賴

- `ClawClient`
- `AIProviderManager`
- `SnowRealmAuthClient`
- `InviteCodeManager`
- `FraudAIAnalyzer`
- `FraudReportManager`
- `FraudRuleSync`
- 多個 provider / skill / network path

### UI consume / callback / side effect owner

- UI consume owner：`ClawPanelView`
- callback owner：
  - `inputTextProvider`
  - `appPackageProvider`
  - `keyboardModeProvider`
  - `onSuggestionSelected`
  - `onClose`
  - `onLockTapped`
- side effect owner：
  - panel 自己會起 coroutine、串流 skill、跑防詐分析
  - service 端負責 commit suggestion、打開 InviteCodeActivity、顯示 floating result

### Runtime 風險

- 網路、provider、auth、token budget、fraud、floating result 全混在同一個 panel
- 容易和 Sprint 7 的 provider / gateway / session state 收斂撞題
- panel UI 並不是主要難點，主要難點是 integration boundary 已經非常厚

### 判定

不適合先做。  
若太早動 `CLAW`，高機率會把 Sprint 6 panel 邊界任務污染成 Sprint 7 的 integration 收斂前哨戰。

## 4.6 `HANDWRITING`

### Service 直接依賴

- `ImeServicePanels.kt:417`
- `YukiBoardIMEService.handwritingEngine`
- `keyboardView.updateCandidates(...)`
- `clearCandidatesUi()`
- `inputRouter.onHandwritingCommit(...)`
- `KeyboardView.KEYCODE_HANDWRITING`
- `ImeServiceCallbacks.kt` 仍有 HANDWRITING activePanel guard 與 commit 路徑

### Manager / repository / client / engine 依賴

- `HandwritingEngine`
- ML Kit `DigitalInkRecognizer`
- 模型下載 / 刪除 / readiness
- `inputRouter.smartInput.adaptiveDictionary`

### UI consume / callback / side effect owner

- UI consume owner：`HandwritingPanelView`
- callback owner：
  - `onCandidateSelected`
  - `onRecognized`
  - `onBackspace`
  - `onConfirm`
  - `ensureReady(...)`
- side effect owner：目前關鍵 side effect 大量仍在 service

### Runtime 風險

- 直接碰 candidate render 與 handwriting commit 路徑
- `onRecognized` 仍在 service 端直接 `keyboardView.updateCandidates(...)`
- 與 `InputRouter`、`KeyboardView`、候選字 UI、模型 readiness 全耦合
- 已知它靠近 Sprint 4/5 留下的熱區與保留區，最不適合拿來做 Sprint 6 第一槍

### 判定

必須最後做。  
它不是單純 panel 邊界題，而是 panel host、recognition lifecycle、candidate write path、input commit path 混合題。

## 5. 為什麼其他 5 個 panel 不能先做

## 5.1 `USER_DICT` 不能先於 `MUSIC`

- 因為它共享 `UserDictionary` 給 IME / settings / sync / input router 四邊使用
- 第一個 panel 不應先碰 shared data ownership

## 5.2 `REMOTE` 不能先於 `MUSIC`

- 因為它直接操作藍牙 HID 與 Wi-Fi server lifecycle
- 第一個 panel 不應先碰外部 transport integration

## 5.3 `THEME` 不能先於 `MUSIC`

- 因為它同時跨 theme render、background storage、broadcast、keyboard 重建、settings 共用資料來源
- 第一個 panel 不應先碰這種多副作用面板

## 5.4 `CLAW` 不能先於 `MUSIC`

- 因為它會提前把 Sprint 6 與 Sprint 7 的 integration boundary 混在一起
- 第一個 panel 不應先碰 provider / auth / fraud / streaming network

## 5.5 `HANDWRITING` 不能先於 `MUSIC`

- 因為它直接靠近 candidate / commit 熱路徑與模型 readiness
- 第一個 panel 不應先碰候選字寫入與輸入核心交界

## 6. 對 `CodeX A` 的正式建議

### 建議判定

`Sprint 6` 仍維持 `LOCKED`，但若三方盤點文件齊備，第一個可考慮解鎖的 panel 應是 `MUSIC`。

### 建議解鎖順序

1. 先只解鎖 `MUSIC`
2. 其餘 `USER_DICT / REMOTE / THEME / CLAW / HANDWRITING` 維持 `LOCKED`
3. 等 `MUSIC` 跑完一輪 compile / checklist / smoke / gate 後，再決定是否放行第二個 panel

### 理由

`Sprint 6` 的真正目標不是最快拆最大 panel，而是先驗證「高依賴 panel 邊界重構」這條流程可不可以被安全複製。  
從這個角度看，`MUSIC` 是目前最安全的起手式。

## 7. 回交格式

### A. 變更摘要

- 完成 6 個高依賴 panel 的風險矩陣與排序建議。
- 明確建議第一個 panel 應為 `MUSIC`，而非 `THEME`。
- 補齊每個 panel 的 service 直接依賴、外部依賴、runtime 風險、UI consume / callback / side effect owner。

### B. 風險

- 本文件是靜態 code-path 盤點，不含 runtime 裝置驗證。
- `REMOTE / CLAW / HANDWRITING` 的實際 runtime 風險可能比靜態盤點更高，不會更低。
- `THEME` 與 settings/背景共用資料來源的影響面已明確偏高，不建議用作第一個 panel。

### C. 未做的事

- 沒有做任何 `.kt` implementation。
- 沒有產出 panel boundary 落地設計。
- 沒有偷跑 `Sprint 7` integration audit。

### D. 下一步建議

- 請 `CodeX A` 將 `MUSIC` 作為第一個候選 panel 審核。
- 若 `CodeX A` 放行下一步，應只解鎖單一 `MUSIC` panel，其他 5 個維持 `LOCKED`。

### E. 請 `CodeX A` 判定

- 是否接受 `MUSIC` 為 `Sprint 6` 第一個 panel。
- 是否維持 `THEME` 非第一順位。

# Sprint 6 Panel Callsite & Scope Audit（2026-04-18）

身份：Claude Code B
任務 ID：`S6-0-CCB-CALLSITE-AUDIT`
基線：`CODEXA_TASK_DISPATCH_2026-04-18.md` §3.2
Gate 狀態：`Sprint 6 = LOCKED`（本輪只盤點，不做 implementation）

---

## 0. 盤點方法

- 對每個 panel 追：**建立入口 / 關閉入口 / callback / state read / state write / 與 `YukiBoardIMEService` 的耦合點**
- 檔案路徑一律絕對；行號以 2026-04-18 snapshot 為準
- file scope 分三級：
  - **最小必改**：不改就做不到 panel 邊界切邊的檔
  - **可能需要但未授權**：有接線可能、需 CodeX A 另外核可
  - **明確禁止**：LOCK_RULES / SPRINT_GATE_RULE / CodeX A 指令明列的禁區
- 本稿不提重構方案，只盤耦合點
- 高敏感檔一律不碰：`KeyboardView.kt` / `handleKeyPress()` 主語意 / `applyInputResult()` 主語意 / Sprint 7 相關檔 / `floating mode stale true` 議題

---

## 1. 共同前置（6 panel 共用）

### 1.1 panel 開啟主幹

1. `ToolbarView.onPanelSelected` → `handlePanelToggle(panel)`
   - 綁定於 `service/YukiBoardIMEService.kt:356`
2. `handlePanelToggle` → `imePanelRouter.routeToggle(...)`（讀 `imeContainerState.activePanel`）
   - `service/ImeServicePanels.kt:107-118`
3. `ImePanelRouter.routeToggle` → `ShowPanel` / `CloseCurrentPanel` / `OpenVoiceInput` / `OpenSettings`
   - `app/ime/ImePanelRouter.kt:24-45`
4. `showPanel(panel)`：
   - `ImePanelRouter.preparePanelOpen`（commit composing、clear bindings、resetInputUiState、clearPanelContainers）
   - `ImePanelFactory.createOrNull`（低依賴 panel）or `createHighDependencyPanelOrNull`（本輪 6 panel）
   - `ImePanelRouter.attachPanel`（add to `panelContainer`，show control bar，`setPanelBehindKeyboard(false)` + `bringToFront`）
   - `service/ImeServicePanels.kt:120-179`

### 1.2 panel 關閉主幹

1. `closePanel()` → `ImePanelRouter.closePanel(...)`
   - `service/ImeServicePanels.kt:181-192`
2. 路徑：`updatePanelSelection(null)` → `ImePanelCleanup.cleanupAfterPanelClose`
   - clear bindings → hide panels → reset input UI → finishComposingText → restoreOverlayOrder（`ImeServiceMisc.kt:105`）→ restoreKeyboardFocus → restoreKeyboardBackground（`ImeServiceMisc.kt:115`）→ hidePanelControlBar
3. HANDWRITING 特殊關閉：`handleKeyPress` 的 `KEYCODE_HANDWRITING` 分支（`YukiBoardIMEService.kt:727-735`，Sprint 5 Round 3 遷移點，**屬熱路徑禁區**）

### 1.3 panel 狀態 SSOT

- `activePanel` 已於 Sprint 5 Round 3 移除 local field，SSOT = `imeCoordinator.uiState.activePanel`
- reader 走 `imeContainerState.activePanel`（`ImeContainerStateConsumer`）
- writer 唯一入口為 `updatePanelSelection(panel)` → `imeCoordinator.showPanel(panel)`
- Sprint 6 各 panel **不得新增 activePanel 寫入點**

### 1.4 panel bindings（6 panel 中持有 reference 的）

`app/ime/ImePanelBindings.kt`：
- `themePanel: ThemePanelView?`
- `remotePanel: RemoteKeyboardPanelView?`
- `userDictPanel: UserDictionaryPanelView?`
- `handwritingPanel: HandwritingPanelView?`
- **CLAW / MUSIC 目前不透過 `ImePanelBindings` 持有**（createHighDependencyPanelOrNull 內 local 建立後直接 return，只有 `clawResultFloatingPanel` 走 service-level field）

---

## 2. PANEL: THEME

### 2.1 建立入口

- Toolbar：`ToolbarView.onPanelSelected` → `handlePanelToggle(PanelType.THEME)` → `showPanel(PanelType.THEME)` → `createHighDependencyPanelOrNull(PanelType.THEME)`
  - `service/ImeServicePanels.kt:208-302`
- 長按 Toolbar：`toolbarView.onPanelLongPressed` + `PanelType.THEME` 分支 → `showTransparencyOverlay()`
  - `service/YukiBoardIMEService.kt:357-362`，這**不是** THEME panel 開啟，是另一條 overlay 入口（邊界需區分）

### 2.2 關閉入口

- 走共同 `closePanel()` 主幹
- **特殊回呼**：`onKeyboardHeightChanged` 內部直接呼叫 `closePanel()` + `backgroundView?.destroy()` + `setInputView(onCreateInputView())`
  - `service/ImeServicePanels.kt:295-300`（高風險：重建整個 input view）

### 2.3 callback（面板 → service）

全部於 `createHighDependencyPanelOrNull` PanelType.THEME 分支設定：
- `onThemeSelected(selected)` → `ThemeManager.currentTheme = selected`、`prefs.useCustomBackground = false`、`applyTheme(selected)`、`p.previewBgBitmap = null`
- `onAnimThemeSelected(styleId)` → `toolbarView.setAnimStyle(styleId)`、`keyboardView.setAnimStyle(styleId)`
- `onPickBackground` / `onPickVideo` → `startActivity(BackgroundPickerActivity)`（`FLAG_ACTIVITY_NEW_TASK`）
- `onClearBackground` → 刪除 `KeyboardBackgroundView.getBackgroundDir` 內檔案、reset `prefs.useCustomBackground`、`backgroundView?.reloadFromStorage()`、`keyboardView.hasCustomBackground = false`
- `onOverlayAlphaChanged(alpha)` → `backgroundView?.overlayAlpha`、`keyboardView.bgOverlayAlpha`
- `onLabelColorChanged(color)` → `keyboardView.customKeyLabelColor`
- `onBackgroundSettingsChanged()` → `backgroundView` scaleMode/offsetX/offsetY/bgRotation + `sendBroadcast("ACTION_BG_UPDATED")`
- `onKeySettingsChanged()` → `keyboardView.keyScalePercent/keyOpacity/keyTextOpacity`、`applyTransparentMode()`、重載背景、重算 glassKeys
- `onPerKeySizeChanged()` → `keyboardView.perKeySizes = prefs.getAllPerKeySizes()`
- `onKeyboardHeightChanged()` → **重建 input view**（見 §2.2）

額外監聽：
- `ThemeManager.addOnThemeChangedListener(themeListener)`：panel `currentThemeId` / `theme` 同步；`p.addOnDetachListener { removeOnThemeChangedListener(...) }`
  - `service/ImeServicePanels.kt:225-233`

### 2.4 state read（panel 讀到的外部狀態）

- `ThemeManager.getInstance(this).currentTheme`
- `ThemeManager.availableThemes`
- `prefs.useCustomBackground`、`bgScaleMode`、`bgOffsetX/Y`、`bgRotation`
- `prefs.keyScalePercent`、`keyOpacity`、`keyTextOpacity`、`transparentKeyboard`
- `prefs.getAllPerKeySizes()`
- `KeyboardBackgroundView.getBackgroundDir`
- panel 自身 `previewBgBitmap`（外部可控）

### 2.5 state write

- `prefs.useCustomBackground`（onClearBackground / onThemeSelected）
- `ThemeManager.currentTheme = selected`（onThemeSelected）
- `backgroundView.*`（scaleMode/offsetX/offsetY/bgRotation/overlayAlpha）— 走 service 端存取
- `keyboardView.*`（customKeyLabelColor / bgOverlayAlpha / keyScalePercent / keyOpacity / keyTextOpacity / perKeySizes / hasCustomBackground）— 走 service 端存取
- `sendBroadcast("com.snowrealm.yukiboard.ACTION_BG_UPDATED")`
- **間接**：觸發 `bgUpdateReceiver` → `imePanelBindings.themePanel?.reloadPreviewBitmap()`（`YukiBoardIMEService.kt:433-434`）

### 2.6 與 YukiBoardIMEService 的耦合點

- `imePanelBindings.themePanel`（`ImePanelBindings.kt:31`）
- `bgUpdateReceiver` onReceive 呼叫 panel 方法（`YukiBoardIMEService.kt:427-436`）
- `applyTheme()`（`ImeServiceInput.kt:202-208`）→ `ImeThemeRenderDelegate.applyTheme`（`app/ime/ImeThemeRenderDelegate.kt:15-28`）
- `applyTransparentMode()`（`ImeServiceMisc.kt:249`）
- `showTransparencyOverlay()`（長按入口，`ImeServiceMisc.kt:123`）
- `onKeyboardHeightChanged` 觸發 `onCreateInputView()` 重建 → 背景/流光/鍵盤全部重建

### 2.7 最小 file scope 草案（THEME）

**最小必改**（建立 THEME panel 邊界時）：
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`（切出建立段）
- `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelBindings.kt`（themePanel 欄位是否留）
- `/app/src/main/java/com/snowrealm/yukiboard/ui/panel/ThemePanelView.kt`（僅 callback interface 可能需調整，不改繪圖邏輯）
- 新增 `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeThemePanelBinder.kt`（或等價名，**新增建議，須 CodeX A 拍板**）

**可能需要但未授權**：
- `/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`（`bgUpdateReceiver` 裡的 `themePanel?.reloadPreviewBitmap()`、長按 overlay 入口）
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceInput.kt`（`applyTheme` 調用點）
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceMisc.kt`（`applyTransparentMode` / `showTransparencyOverlay` / `restoreKeyboardBackgroundAfterPanelClose`）
- `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeThemeRenderDelegate.kt`（若要整合 panel 端 theme listener）
- `/app/src/main/java/com/snowrealm/yukiboard/theme/ThemeManager.kt`

**明確禁止**：
- `ui/keyboard/KeyboardView.kt`、`ui/keyboard/KeyboardBackgroundView.kt`（繪圖與背景核心）
- `ui/settings/section/ThemeSettingsSection.kt`（Claude Code A 責任線）
- `app/build.gradle`、`keystore/*`、`/.env`

### 2.8 風險備註

- `onKeyboardHeightChanged` 會全量重建 input view，涉及背景/GL/Lottie/toolbar，**不能在 panel 邊界切邊輪順手動**
- `bgUpdateReceiver` 觸發的 `themePanel?.reloadPreviewBitmap()` 是 panel lifecycle 外的回寫，拆邊界時需保留同等行為
- THEME callback 數量（10+）是 6 panel 之冠，切分成本在「callback surface」而非「依賴數量」

---

## 3. PANEL: CLAW

### 3.1 建立入口

- Toolbar：`handlePanelToggle(PanelType.CLAW)` → `showPanel(PanelType.CLAW)` → `createHighDependencyPanelOrNull(PanelType.CLAW)`
  - `service/ImeServicePanels.kt:303-342`
- 觸發防詐 Skill：`fraudDetectRunnable`（`YukiBoardIMEService.kt:135-139`）經 `charAnchor.postDelayed` 排程（`ImeServiceInput.kt:116-117`）→ `(panelContainer.getChildAt(0) as? ClawPanelView)?.triggerSkill("fraud_detect")`
  - **隱式依賴**：假設 `panelContainer.getChildAt(0)` 就是 ClawPanelView，未經 state 對照

### 3.2 關閉入口

- 面板自身 `onClose = { closePanel() }`（`service/ImeServicePanels.kt:334`）
- 走共同 `closePanel()` 主幹
- `onDestroy`：`clawResultFloatingPanel?.dismiss()`（`YukiBoardIMEService.kt:631`）

### 3.3 callback

- `onSuggestionSelected(text)` → `currentInputConnection.performContextMenuAction(selectAll)` + `commitText(text, 1)` + `clawResultFloatingPanel?.showResult("AI", listOf(text))`
  - `service/ImeServicePanels.kt:326-333`
- `onClose` → `closePanel()`
- `onLockTapped` → `startActivity(InviteCodeActivity)`

### 3.4 state read / providers

- `inputTextProvider` 讀 `currentInputConnection.getSelectedText` / `getTextBeforeCursor(500, 0)`
- `appPackageProvider` 讀 `inputRouter.currentAppPackage`
- `keyboardModeProvider` 讀 `keyboardView.currentMode`
- `setCurrentMode`：從 `keyboardView.currentMode` 衍生（service 同步）

### 3.5 state write

- 面板內部：`ClawPanelView.clawClient` setter 寫 `toolbarView.remainingTokens` / `isLocked` / `resultView.isLocked`（`ClawPanelView.kt:46-52`）
- Commit 路徑：`currentInputConnection.commitText`
- `clawResultFloatingPanel.onInsertToInput` 在 `onCreate` 綁定（`YukiBoardIMEService.kt:316-319`）→ 寫 `currentInputConnection.commitText`

### 3.6 與 YukiBoardIMEService 的耦合點

- service-level fields：
  - `var clawClient: ClawClient?`（`YukiBoardIMEService.kt:154`）
  - `internal var clawResultFloatingPanel: ClawResultFloatingPanel?`（`YukiBoardIMEService.kt:182`）
- `fraudDetectRunnable`（`YukiBoardIMEService.kt:135-139`）→ 依賴 `panelContainer` 首子為 ClawPanelView
- `scanForFraud()` / clipboard fraud listener（`registerClipboardFraudListener`）於 `onStartInputView`（`YukiBoardIMEService.kt:531-533`）
- `ClawPanelView` 內建 `FraudReportManager` / `FraudRuleSync`（`ui/panel/ClawPanelView.kt:72-73`）— 直接 `new` 整合類別

### 3.7 最小 file scope 草案（CLAW）

**最小必改**：
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`（CLAW 建立段）
- 新增 `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeClawPanelBinder.kt`（新增建議，須 CodeX A 拍板）
- `/app/src/main/java/com/snowrealm/yukiboard/ui/panel/ClawPanelView.kt`（僅 callback/provider interface 可能調整）

**可能需要但未授權**：
- `/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`（`clawClient` / `clawResultFloatingPanel` 欄位與 onCreate 綁定、`fraudDetectRunnable`）
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceInput.kt`（`fraudDetectRunnable` postDelayed 排程處）
- `/app/src/main/java/com/snowrealm/yukiboard/claw/ClawClient.kt`
- `/app/src/main/java/com/snowrealm/yukiboard/ui/floating/ClawResultFloatingPanel.kt`
- `/app/src/main/java/com/snowrealm/yukiboard/ui/panel/ClawToolbarView.kt`

**明確禁止**：
- `KeyboardView.kt` / `handleKeyPress()` / `applyInputResult()`
- `/app/src/main/java/com/snowrealm/yukiboard/claw/skills/**`（Sprint 6-2 邊界不等於 skill 重構）
- network client / okhttp 建立處（屬 Sprint 7）
- `security/FraudAIAnalyzer` 演算法層

### 3.8 風險備註

- `fraudDetectRunnable` 用 `panelContainer.getChildAt(0) as? ClawPanelView` 做 runtime 類型判斷，與 `activePanel` SSOT 未對齊，panel 邊界切邊時需小心不破壞此 runtime 行為
- `clawResultFloatingPanel` 是 service-level 與 panel 同時引用，生命週期跨 panel（panel 關閉後 floating 仍存在）
- CLAW 與 Sprint 7 provider / API key / okhttp 收斂**直接撞題**，必須在本 panel 邊界切邊時排除 integration 範圍

---

## 4. PANEL: REMOTE

### 4.1 建立入口

- Toolbar：`handlePanelToggle(PanelType.REMOTE)` → `showPanel` → `createHighDependencyPanelOrNull(PanelType.REMOTE)`
  - `service/ImeServicePanels.kt:196-207`

### 4.2 關閉入口

- 走共同 `closePanel()` 主幹
- `onDestroy`：`btKeyboard?.destroy()` + `wifiServer?.destroy()`（`YukiBoardIMEService.kt:621-622`）

### 4.3 callback

- `onBluetoothToggle` → `toggleBluetooth()`（`ImeServiceMisc.kt:359`）
- `onWifiToggle` → `toggleWifi()`（`ImeServiceMisc.kt:382`）

### 4.4 state read（建立瞬間一次性讀取）

- `btKeyboard?.connectionState` → panel `btState`
- `wifiServer?.connectionState` → panel `wifiState`
- `wifiServer?.getConnectionUrl()` → panel `wifiUrl`
- `btKeyboard?.connectedDevice?.name` 或 `wifiServer?.connectedDevice?.name` → `connectedDeviceName`

### 4.5 state write

- callback 觸發 `toggleBluetooth` / `toggleWifi` 影響 `btKeyboard.connectionState` / `wifiServer.connectionState`
- **已知限制**：panel 只在 open 時一次性讀 state，無 listener；state 變更後 panel 顯示會 stale（與 Sprint 6 邊界無關，現狀如此）

### 4.6 與 YukiBoardIMEService 的耦合點

- service-level fields：
  - `internal var btKeyboard: BluetoothHidKeyboard?`（`YukiBoardIMEService.kt:200`）
  - `internal var wifiServer: WifiKeyboardServer?`（`YukiBoardIMEService.kt:201`）
- `imePanelBindings.remotePanel`（`ImePanelBindings.kt:32`）
- `toggleBluetooth` / `toggleWifi`（`ImeServiceMisc.kt`）內部處理 btKeyboard / wifiServer 建立與銷毀

### 4.7 最小 file scope 草案（REMOTE）

**最小必改**：
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`（REMOTE 建立段）
- 新增 `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeRemotePanelBinder.kt`（新增建議，須 CodeX A 拍板）
- `/app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt`（僅 state input/callback interface）

**可能需要但未授權**：
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceMisc.kt`（`toggleBluetooth` / `toggleWifi` 的呼叫處）
- `/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`（`btKeyboard` / `wifiServer` 欄位、onDestroy 銷毀）
- `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelBindings.kt`（remotePanel 欄位）

**明確禁止**：
- `/app/src/main/java/com/snowrealm/yukiboard/remote/BluetoothHidKeyboard.kt`
- `/app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt`
- `/app/src/main/java/com/snowrealm/yukiboard/remote/RemoteKeyboardService.kt`
- WebSocket / HID descriptor 相關改動（不是 Sprint 6-3 範圍）

### 4.8 風險備註

- REMOTE 看起來 callback 數最少（2 個），但 `btKeyboard` / `wifiServer` 銷毀分散於 `onDestroy`，edge cases（例如 panel 開著時 service destroy）需看切邊時是否搬動
- `connectionState` 無 listener，切邊時若要做 live update 會**擴大 scope**，屬 Sprint 6-3 後的議題，不在本輪

---

## 5. PANEL: HANDWRITING

### 5.1 建立入口

- Toolbar：`handlePanelToggle(PanelType.HANDWRITING)` → `showPanel` → `createHighDependencyPanelOrNull(PanelType.HANDWRITING)`
  - `service/ImeServicePanels.kt:417-461`
- 鍵盤鍵：`handleKeyPress` 的 `KEYCODE_HANDWRITING` 分支（`YukiBoardIMEService.kt:727-735`）— **屬 handleKeyPress 主流程禁區，本輪不得動**
- lazy init：`handwritingEngine == null` 時建立 `HandwritingEngine(this).also { it.init() }`（failure 時 toast + 不 attach）

### 5.2 關閉入口

- 走共同 `closePanel()` 主幹
- `KEYCODE_HANDWRITING` 時若 `activePanel == HANDWRITING` → `closePanel()`（禁區）
- `ImePanelBindings.clearInputModeBindings` 中 `handwritingPanel = null`（`ImePanelBindings.kt:91`）
- `onDestroy`：`handwritingEngine?.close()`（`YukiBoardIMEService.kt:633`）

### 5.3 callback

- `onCandidateSelected(text)` → `currentInputConnection.commitText(text, 1)` + `clearCandidatesUi()` + `inputRouter.onHandwritingCommit(text)`
  - `service/ImeServicePanels.kt:432-436`
  - `inputRouter.onHandwritingCommit` 定義在 `input/router/InputRouter.kt:921`，屬 RMW heat path 邊緣
- `onRecognized(rawCandidates)` → `inputRouter.smartInput.adaptiveDictionary.getFrequency` 排序 → `keyboardView.updateCandidates(boosted, sourceMap)`
  - `service/ImeServicePanels.kt:437-447`
  - **這是 KeyboardView candidate 直寫**，依 LOCK_RULES 是熱路徑；Sprint 4 `InputUiSync` 邊界也未能接掉 HANDWRITING callback（Sprint 5 smoke 驗證把 R5/R11 留 inline 的主因）
- `onBackspace` → `currentInputConnection.deleteSurroundingText(1, 0)`
- `onConfirm` → `sendDefaultEditorAction(true)`
- `ensureReady` → toast「模型下載中」

### 5.4 state read

- 面板內部：`engine.checkAllModelStatus`（`HandwritingPanelView.kt:56-62`）
- `inputRouter.smartInput.adaptiveDictionary.getFrequency(word)`（排序用）

### 5.5 state write

- `currentInputConnection.commitText` / `deleteSurroundingText`
- `keyboardView.updateCandidates(...)`（**RMW heat path 禁區**）
- `inputRouter.onHandwritingCommit(text)`（RMW heat path 邊緣）
- `handwritingEngine.init()` / `close()`（lifecycle）

### 5.6 與 YukiBoardIMEService 的耦合點

- service-level field：`internal var handwritingEngine: HandwritingEngine?`（`YukiBoardIMEService.kt:203`）
- `imePanelBindings.handwritingPanel`（`ImePanelBindings.kt:37`）
- `handleKeyPress` 熱路徑 KEYCODE_HANDWRITING toggle（**禁區**）
- `ImePanelCleanup`、`resetInputUiStateForPanelTransition` 路徑

### 5.7 最小 file scope 草案（HANDWRITING）

**最小必改**：
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`（HANDWRITING 建立段的 lazy init 與 callback wire）
- 新增 `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeHandwritingPanelBinder.kt`（新增建議，須 CodeX A 拍板）
- `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelBindings.kt`（`handwritingPanel` 欄位）
- `/app/src/main/java/com/snowrealm/yukiboard/ui/panel/HandwritingPanelView.kt`（只接口，不動辨識邏輯）

**可能需要但未授權**：
- `/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`（`handwritingEngine` 欄位、onDestroy）

**明確禁止**：
- `/app/src/main/java/com/snowrealm/yukiboard/ui/keyboard/KeyboardView.kt`（`updateCandidates` 直寫）
- `handleKeyPress()` 主語意（KEYCODE_HANDWRITING 分支）
- `applyInputResult()` 主語意
- `/app/src/main/java/com/snowrealm/yukiboard/input/handwriting/HandwritingEngine.kt` 演算法層（生命週期接線可外部代理，但不改 engine 內容）
- `/app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt` 中 `onHandwritingCommit` 邏輯（RMW）
- `InputUiSync.kt` 的 candidate render 規約（Sprint 4 鎖定）

### 5.8 風險備註

- HANDWRITING 是 6 panel 中**唯一**會主動寫 `keyboardView.updateCandidates`，Sprint 5 Round 3 把 R5/R11 刻意留 inline 即為此
- `handleKeyPress` 的 toggle 分支與 `activePanel` SSOT 已接，但 toggle 動作本身仍在熱路徑
- Sprint 6-4 切邊時必須只動建立 / callback wiring，**不得**碰 recognition callback 的 candidate 回寫語意

---

## 6. PANEL: MUSIC

### 6.1 建立入口

- Toolbar：`handlePanelToggle(PanelType.MUSIC)` → `showPanel` → `createHighDependencyPanelOrNull(PanelType.MUSIC)`
  - `service/ImeServicePanels.kt:393-416`

### 6.2 關閉入口

- 走共同 `closePanel()` 主幹
- `MidiImportActivity.onMidiImported` 是 static var callback，若 panel 關閉後尚未觸發，會有跨 lifecycle refernce 殘留（**現狀設計如此**）

### 6.3 callback

- `onImportMidi` → 設 `MidiImportActivity.onMidiImported = { midiSong -> ... p.addImportedSong(...) }` + `startActivity(MidiImportActivity, FLAG_ACTIVITY_NEW_TASK)`
  - `service/ImeServicePanels.kt:397-413`

### 6.4 state read

- 無對 service 狀態的讀取（panel 自持狀態）

### 6.5 state write

- `keySoundEngine`（由 service 傳入，panel 內彈奏時用）
- `MidiImportActivity.onMidiImported` static 屬性（**非單元內狀態**，跨 Activity 設定）

### 6.6 與 YukiBoardIMEService 的耦合點

- service-level field：`internal var keySoundEngine: KeyboardSoundEngine?`（`YukiBoardIMEService.kt:204`）
- 無 `imePanelBindings` 欄位（目前沒 reference 持有）
- `MidiImportActivity`（全新 task 啟動）

### 6.7 最小 file scope 草案（MUSIC）

**最小必改**：
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`（MUSIC 建立段）
- 新增 `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeMusicPanelBinder.kt`（新增建議，須 CodeX A 拍板）
- `/app/src/main/java/com/snowrealm/yukiboard/ui/panel/MusicPanelView.kt`（僅 callback interface；不動音樂引擎）

**可能需要但未授權**：
- `/app/src/main/java/com/snowrealm/yukiboard/ui/MidiImportActivity.kt`（static callback 改為事件化）
- `/app/src/main/java/com/snowrealm/yukiboard/service/KeyboardSoundEngine.kt`（只要不動 API surface 可不碰）

**明確禁止**：
- `KeyboardSoundEngine` 音訊合成內部實作
- `/app/src/main/java/com/snowrealm/yukiboard/game/MusicTypingGame.kt`（遊戲模式共用邏輯）
- 全域 media session 或 ExoPlayer 相關檔（Sprint 6-5 不等於媒體 stack 重構）

### 6.8 風險備註

- MUSIC 是 6 panel 中依賴最淺的，切邊最安全
- `MidiImportActivity.onMidiImported` 是 static callback，本身已是 panel-scope 外的狀態；切邊時若想同時處理，會立刻擴 scope 到 Activity（**建議留到 Sprint 6-5 另議**）

---

## 7. PANEL: USER_DICT

### 7.1 建立入口

- Toolbar：`handlePanelToggle(PanelType.USER_DICT)` → `showPanel` → `createHighDependencyPanelOrNull(PanelType.USER_DICT)`
  - `service/ImeServicePanels.kt:343-392`
- 建立時即 `p.enterInputMode()`（`service/ImeServicePanels.kt:390`）— panel 實作 `PanelInputTarget`

### 7.2 關閉入口

- 走共同 `closePanel()` 主幹
- `ImePanelBindings.clearInputModeBindings()` 內呼叫 `userDictPanel?.exitInputMode()`（`ImePanelBindings.kt:79`）
- 入力模式下會透過 `activeInputTarget()` 接手 `handleKeyPress` 的 key routing → `routeKeyToPanel(...)`（熱路徑禁區）

### 7.3 callback

- `onShowDialog(dialog)` → `showImeDialog(dialog)`（`ImeServiceMisc.kt:209`）
- `onExportRequest(data)` → `FileProvider.getUriForFile(...)` + `ACTION_SEND` intent chooser（`service/ImeServicePanels.kt:348-370`）
- `onImportRequest()` → 讀 `getExternalFilesDir(null)/yukiboard_user_dict.txt` + `p.importEntries(file.readText())` + toast（`service/ImeServicePanels.kt:371-389`）

### 7.4 state read

- panel 內部 `userDictionary` reload entries（見 7.5）
- `getExternalFilesDir(null)`（service context）

### 7.5 state write

- panel 建立時：`p.userDictionary = UserDictionary(this)`（**每次建立都 new 一份**，與 service / InputRouter.smartInput 中的 UserDictionary 是否為同一實例需核對，屬**潛在資料一致性 bug**，現狀行為保留，不修改）
- `p.importEntries(...)` 會寫 UserDictionary
- export 會 `file.writeText(...)` 至 external files dir

### 7.6 與 YukiBoardIMEService 的耦合點

- `imePanelBindings.userDictPanel`（`ImePanelBindings.kt:33`）
- `activeInputTarget() / activeRawCommitTarget()` 路由（`ImePanelBindings.kt:52-69`）— 進入 handleKeyPress 熱路徑（禁區）
- `showImeDialog`、`getExternalFilesDir`、`FileProvider`、`packageName`
- `handleKeyPress` 頂端 `imePanelBindings.activeInputTarget()` 判斷（熱路徑）

### 7.7 最小 file scope 草案（USER_DICT）

**最小必改**：
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`（USER_DICT 建立段）
- 新增 `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeUserDictPanelBinder.kt`（新增建議，須 CodeX A 拍板）
- `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelBindings.kt`（`userDictPanel` 欄位）
- `/app/src/main/java/com/snowrealm/yukiboard/ui/panel/UserDictionaryPanelView.kt`（只接 interface，不動 CRUD 邏輯）

**可能需要但未授權**：
- `/app/src/main/java/com/snowrealm/yukiboard/input/dictionary/UserDictionary.kt`（若要與 service / smartInput 共用單一實例，必須 CodeX A 先確認 SSOT）
- `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceMisc.kt`（`showImeDialog`）

**明確禁止**：
- `handleKeyPress()` 主語意、`PanelInputTarget` routing 邏輯（熱路徑）
- `/app/src/main/java/com/snowrealm/yukiboard/input/smart/**`（smart input 字典邏輯）
- `/app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt`
- settings 側的字典管理頁（Claude Code A 責任線）

### 7.8 風險備註

- `UserDictionary(this)` 每次 open 都 new，**可能與 InputRouter.smartInput 的字典實例不同**，屬 pre-existing 資料一致性議題；**本輪不修、但切邊時需保留等價行為以免放大問題**
- `activeInputTarget()` 路徑走 handleKeyPress 熱路徑，是 Sprint 6-6 最可能誤碰的高敏感點
- Sprint 6 規劃將 `USER_DICT` 置於最後做，主因即「panel / dictionary repository / user data mutation / sync 四層交集」

---

## 8. 共同禁區清單（6 panel 一體適用）

無論先做哪個 panel，以下檔案於本 Sprint 6 邊界切邊**一律不得觸碰**：

1. `/app/src/main/java/com/snowrealm/yukiboard/ui/keyboard/KeyboardView.kt`
2. `/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt` 內 `handleKeyPress()` 主語意
3. `/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt` 內 `applyInputResult()` 主語意
4. `/app/src/main/java/com/snowrealm/yukiboard/app/ime/InputUiSync.kt` render 規約
5. `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeContainerStateConsumer.kt`（Sprint 5 SSOT，邊界切邊不得繞過）
6. `/app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt` 熱路徑
7. 任何 `OkHttpClient.Builder()` 建立處（**屬 Sprint 7，一律避開**）
8. `/app/src/main/java/com/snowrealm/yukiboard/ui/settings/**`（Claude Code A 責任線）
9. `app/build.gradle` / `gradle.properties` / `keystore/*` / `/.env`
10. `/CODEX.md` / `/RULE/**`（屬 CodeX A 權責）
11. `floating mode stale true` 相關修補（pre-existing bug，獨立議題）

---

## 9. Panel 難易度預評（不做排序決策，僅供 CodeX B / CodeX A 參考）

| Panel | 依賴數 | 熱路徑耦合 | 外部 integration | 建議複雜度 |
|---|---|---|---|---|
| MUSIC | 低 | 無 | Activity 一次性 | 低 |
| REMOTE | 中 | 無 | bt/wifi 生命週期 | 中 |
| THEME | 高 | 無 | prefs/broadcast/applyTheme | 中高 |
| HANDWRITING | 中 | **有（candidate RMW）** | HandwritingEngine lifecycle | 高 |
| CLAW | 中 | 無（但 fraudDetectRunnable 有隱式依賴） | ClawClient / floating panel | 高（含 Sprint 7 撞題風險） |
| USER_DICT | 中 | **有（PanelInputTarget 路由）** | UserDictionary SSOT 未明 | 最高（Sprint 6 規劃置最後） |

**排序建議的決策責任在 CodeX B（`S6-0-CXB-RISK-MATRIX`）**，本文件不提案具體順序。

---

## 10. 回交

### A. 變更摘要

- 新增 `/docs/SPRINT6_PANEL_CALLSITE_SCOPE_AUDIT_2026-04-18.md`（本檔）
- 對 THEME / CLAW / REMOTE / HANDWRITING / MUSIC / USER_DICT 六個 panel 完成：
  - 建立入口 / 關閉入口 / callback / state read / state write / 與 `YukiBoardIMEService` 耦合點
  - 最小 file scope 三級分類（最小必改 / 可能需要但未授權 / 明確禁止）
  - 風險備註
- 未修改任何 `.kt`

### B. 風險

- HANDWRITING 的 `keyboardView.updateCandidates` 回寫於 Sprint 4/5 邊界外仍保留（Sprint 5 R5/R11 刻意 inline），Sprint 6-4 若切邊操作不當會**直接撞熱路徑禁區**。
- CLAW 的 `fraudDetectRunnable` 以 `panelContainer.getChildAt(0) as? ClawPanelView` 做 runtime 判斷，與 `activePanel` SSOT 未對齊；若切邊時順手改判斷方式，會改到 Sprint 3/5 已鎖定的 SSOT 邊界。
- USER_DICT 建立時 `UserDictionary(this)` 是新實例，可能與 `InputRouter.smartInput` 內的字典不同；**非本輪修補項**，但切邊設計時需意識到這個 pre-existing 不一致。
- THEME `onKeyboardHeightChanged` 觸發 `setInputView(onCreateInputView())` 重建 input view，與 Sprint 5 State Convergence 後的 re-init 路徑互動不明；若 panel 切邊改動生命週期時序，可能出現 regression。
- 本稿僅盤「現狀耦合點」，**不保證任何建議檔案新增（`Ime*PanelBinder`）會被 CodeX A 核可**；是否採用此模式需 CodeX B risk matrix 與 CodeX A 拍板。

### C. 未做的事

- 未對任何 panel 提出切邊方案或重構順序
- 未修改任何 `.kt`
- 未動 `RULE/*` / `CODEX.md` / `SPRINT_MASTER_PLAN_2026-04-15.md`
- 未 compile / smoke / runtime 驗證（本輪不在 gate 要求）
- 未碰 `floating mode stale true`
- 未對 Sprint 7 範疇的 integration 點（okhttp / provider / sync）做任何動作
- 未排序第一個 panel（屬 CodeX B `S6-0-CXB-RISK-MATRIX`）

### D. 下一步建議

1. `CodeX B` 以本稿為基礎，補 risk matrix 並提出第一個 panel 排序建議。
2. `Claude Code A` 以本稿 §2~§7 的「state read/write / 與 settings 可能重疊處」為線索，補 `SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md`。
3. `CodeX A` 在三方盤點齊備後判定：
   - 是否確認用 `app/ime/Ime*PanelBinder` 模式做切邊
   - 指定第一個 panel
   - 鎖其餘 5 個 panel
4. Sprint 6-1 正式啟動前，請 CodeX A 明示「本檔 §X.7 最小 file scope 是否可作為實作輪的授權依據」。

### E. 請 `CodeX A` 判定

- 本稿是否符合 `S6-0-CCB-CALLSITE-AUDIT` 交付要求？
- 「最小必改 / 可能需要但未授權 / 明確禁止」三級分類粒度是否可接受？
- `Ime*PanelBinder` 新增檔案建議是否需 CodeX B 再確認，或可直接作為 Sprint 6-1 的 file scope 基礎？
- 是否同意 §8 共同禁區清單作為六個 panel 一體適用的 Sprint 6 邊界？
- 本輪我方（Claude Code B）**不提排序建議**的做法是否符合 gate 規則下的角色邊界？

等待 `CodeX A` `READY / NOT READY / LOCKED` 判定。

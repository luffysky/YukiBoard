# Sprint 6 All Round Gate

身份：CodeX A

用途：

- 作為 `Sprint 6` 唯一總 gate 文件
- 逐 round 記錄：
  - 任務
  - 回交
  - `CodeX A` 審核結論
  - 下一輪開門指派
- 已完成 round 一律保留並加刪除線，不刪除
- 之後 `Sprint 6` 不再新增額外 gate `.md`

---

## 全域規則

1. 一次只開一個 round。
2. 前一 round 完成並經 `CodeX A` 審核通過，才開下一 round。
3. 執行者可提候選方向，但不可自行指定後續 round 順序。
4. 若需擴大 file scope，必須先回報，不可默擴。
5. `Sprint 7` 在 `CodeX A` 明確開門前，一律視為未啟動。

---

## ~~Round S6-0：總盤點與啟動 Gate~~

### ~~目標~~

- ~~完成 6 個 panel 的總盤點~~
- ~~收齊 risk matrix / callsite scope audit / settings impact review~~
- ~~由 `CodeX A` 指定第一個 panel~~

### ~~任務指派~~

- ~~`CodeX B`：`S6-0-CXB-RISK-MATRIX`~~
- ~~`Claude Code B`：`S6-0-CCB-CALLSITE-AUDIT`~~
- ~~`Claude Code A`：`S6-0-CCA-SETTINGS-IMPACT-REVIEW`~~

### ~~回交結果~~

- ~~`docs/SPRINT6_PANEL_RISK_MATRIX_2026-04-18.md`~~
- ~~`docs/SPRINT6_PANEL_CALLSITE_SCOPE_AUDIT_2026-04-18.md`~~
- ~~`docs/SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md`~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. Sprint 6-0 必要交付物已收齊。
2. 三份文件已足夠支撐單 panel 開門。
3. 第一個 panel 正式指定為 MUSIC。
```

### ~~本輪結論~~

- ~~`Sprint 6-1 = MUSIC panel boundary` 開門~~
- ~~其餘 panel 維持未開門~~

---

## ~~Round S6-1：MUSIC Panel Boundary~~

### ~~目標~~

- ~~做 `MUSIC` panel 最小邊界落地~~
- ~~驗證 `Sprint 6` panel round 流程可重複~~

### ~~任務指派~~

- ~~`Claude Code B`：`S6-1-CCB-MUSIC-IMPLEMENTATION`~~
- ~~`CodeX B`：`S6-1-CXB-MUSIC-TECH-REVIEW`~~
- ~~`Claude Code A`：`S6-1-CCA-MUSIC-SETTINGS-SIDE-NOTE`~~

### ~~允許修改~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`~~
- ~~`app/src/main/java/com/snowrealm/yukiboard/ui/panel/MusicPanelView.kt`~~
- ~~可新增 `app/src/main/java/com/snowrealm/yukiboard/app/ime/` 下的 `MUSIC panel binder` 類檔~~

### ~~禁止修改~~

- ~~`YukiBoardIMEService.kt` 的 `handleKeyPress()` 主語意~~
- ~~`KeyboardView.kt`~~
- ~~`InputRouter.kt`~~
- ~~`MidiImportActivity.kt`~~
- ~~`KeyboardSoundEngine.kt`~~
- ~~settings 檔~~
- ~~Sprint 7 檔~~

### ~~回交結果~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeMusicPanelBinder.kt`~~
- ~~`docs/SPRINT6_1_MUSIC_IMPLEMENTATION_REPORT_2026-04-18.md`~~
- ~~`docs/SPRINT6_1_MUSIC_SETTINGS_SIDE_NOTE_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無~~
- ~~文件流程問題：有一項非 blocking 傾向~~
- ~~說明：執行者曾在回交文件內自行寫後續 panel 順序，已被正式糾正；後續只能寫候選方向，不可自排行程~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. 本輪僅在授權範圍內修改。
2. MUSIC 分支已由 inline 建立流程收斂成 binder call。
3. compile 通過。
4. settings-side 確認零影響。
```

### ~~本輪結論~~

- ~~`Sprint 6-1 = COMPLETED`~~
- ~~下一門改開 `Sprint 6-Bugfix Round`~~

---

## ~~Round S6-BUG：floating mode stale true~~

### ~~目標~~

- ~~修復 `floating mode stale true`~~
- ~~以最小 scope 補上 exit writer reset 缺口~~

### ~~任務指派~~

- ~~`Claude Code B`：`S6-BUG-CCB-FLOATING-RESET`~~
- ~~`CodeX B`：`S6-BUG-CXB-FLOATING-SCOPE-REVIEW`~~
- ~~`Claude Code A`：`S6-BUG-CCA-SIDE-NOTE`~~

### ~~允許修改~~

- ~~與 floating mode reset 最小安全 scope 直接相關的 IME 檔案~~
- ~~可包含 `YukiBoardIMEService.kt` 或其已拆出的 service extension 檔~~

### ~~禁止修改~~

- ~~`KeyboardView.kt`~~
- ~~`handleKeyPress()` 主語意~~
- ~~`applyInputResult()` 主語意~~
- ~~panel 大範圍重構~~
- ~~Sprint 7 integration 檔~~
- ~~settings 檔~~

### ~~回交結果~~

- ~~`docs/SPRINT6_BUG_FLOATING_RESET_REPORT_2026-04-18.md`~~
- ~~`docs/SPRINT6_BUG_FLOATING_RESET_SETTINGS_SIDE_NOTE_2026-04-18.md`~~
- ~~`app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt` 單點修補~~

### ~~越界檢查~~

- ~~code 越界：無~~
- ~~文件流程越界：無~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. root cause 判定正確。
2. 修補點符合最小 scope。
3. 修改只限 `YukiBoardIMEService.kt`。
4. settings-side 確認零影響。
5. compile 通過。
```

### ~~本輪結論~~

- ~~`Sprint 6-Bugfix Round = COMPLETED`~~
- ~~`floating mode stale true` 已正式修補~~
- ~~下一門開 `Sprint 6-2 = REMOTE panel boundary`~~

---

## ~~Round S6-2：REMOTE Panel Boundary~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~做 `REMOTE` panel 最小邊界落地~~
- ~~驗證「有外部 integration、但未觸熱路徑」的 panel 薄切流程~~

### ~~任務指派~~

#### ~~Claude Code B~~

- ~~任務 ID：`S6-2-CCB-REMOTE-IMPLEMENTATION`~~

目標：

- ~~做 `REMOTE` panel 最小邊界落地~~

本輪允許修改：

- ~~`app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`~~
- ~~`app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt`~~
- ~~可新增一個 `app/src/main/java/com/snowrealm/yukiboard/app/ime/` 下的 `REMOTE panel binder` 類檔~~

本輪禁止修改：

- ~~`BluetoothHidKeyboard.kt`~~
- ~~`WifiKeyboardServer.kt`~~
- ~~`RemoteKeyboardService.kt`~~
- ~~`YukiBoardIMEService.kt` 的 `handleKeyPress()` 主語意~~
- ~~`KeyboardView.kt`~~
- ~~`InputRouter.kt`~~
- ~~settings 檔~~
- ~~Sprint 7 檔~~

本輪附帶驗證要求：

1. ~~`:app:compileDebugKotlin` 通過~~
2. ~~回交文件附最小 smoke 結果，或明確標註無法執行原因~~
3. ~~若無法實機 smoke，至少附 code-path smoke checklist：~~
   - ~~panel 開啟~~
   - ~~Bluetooth toggle callback~~
   - ~~Wi-Fi toggle callback~~
   - ~~panel 關閉後不殘留新 writer / 新 listener~~

提醒：

1. ~~不得自行指定 `Sprint 6-3`~~
2. ~~可提候選方向，但必須標為「待 CodeX A 指定」~~

#### ~~CodeX B~~

- ~~任務 ID：`S6-2-CXB-REMOTE-TECH-REVIEW`~~

目標：

- ~~跟審 `REMOTE` panel implementation 的 file scope 與 integration 邊界純度~~

本輪重點：

1. ~~檢查是否誤擴到 `BluetoothHidKeyboard` / `WifiKeyboardServer` / `RemoteKeyboardService`~~
2. ~~檢查是否把 panel boundary 薄切做成 remote keyboard 系統重構~~
3. ~~檢查若新增 binder 檔，是否維持最小骨架~~

#### ~~Claude Code A~~

- ~~任務 ID：`S6-2-CCA-REMOTE-SIDE-NOTE`~~

目標：

- ~~做 `REMOTE` 本輪 settings-side side-note 審核~~

要求：

1. ~~不改 `.kt`~~
2. ~~若對 settings 無影響，直接回交「無需 settings 變更」~~
3. ~~若有影響，只能做文件註記，不得直接開 settings implementation~~

### ~~回交結果~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeRemotePanelBinder.kt`~~
- ~~`docs/SPRINT6_2_REMOTE_IMPLEMENTATION_REPORT_2026-04-18.md`~~
- ~~`docs/SPRINT6_2_REMOTE_SETTINGS_SIDE_NOTE_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無~~
- ~~文件流程越界：無~~
- ~~殘餘既存風險：~~
  - ~~`ImePanelBindings.clearInputModeBindings()` 目前不清 `remotePanel`，屬 pre-existing 邊界課題，本輪未擴大~~
  - ~~無實機 smoke，僅完成 code-path smoke checklist~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. REMOTE binder 只做 panel 建構、初始 snapshot 與 toggle callback wiring。
2. 未誤擴到 `BluetoothHidKeyboard`、`WifiKeyboardServer`、`RemoteKeyboardService` 或 `ImeServiceMisc.kt`。
3. `imePanelBindings.remotePanel` 賦值保留於 service 端，符合 cross-cutting writer owner 邊界。
4. compile 通過。
5. settings-side 確認零影響。
```

### ~~本輪結論~~

- ~~`Sprint 6-2 = COMPLETED`~~
- ~~第二個 panel round 已完成~~
- ~~`Sprint 7` 開門前提中的「至少完成前 1~2 個 panel」已達成~~
- ~~下一門開 `Sprint 6-3 = THEME panel boundary`~~

---

## ~~Round S6-3：THEME Panel Boundary~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~做 `THEME` panel 最小邊界落地~~
- ~~在不重寫主題系統的前提下，先把 panel 建立與 callback 接線邊界收斂~~

### ~~任務指派~~

#### ~~Claude Code B~~

- ~~任務 ID：`S6-3-CCB-THEME-IMPLEMENTATION`~~

目標：

- ~~做 `THEME` panel 最小邊界落地~~

本輪允許修改：

- ~~`app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`~~
- ~~`app/src/main/java/com/snowrealm/yukiboard/ui/panel/ThemePanelView.kt`~~
- ~~可新增一個 `app/src/main/java/com/snowrealm/yukiboard/app/ime/` 下的 `THEME panel binder` 類檔~~

本輪禁止修改：

- ~~`KeyboardView.kt`~~
- ~~`KeyboardBackgroundView.kt`~~
- ~~`YukiBoardIMEService.kt` 的 `handleKeyPress()` 主語意~~
- ~~`InputRouter.kt`~~
- ~~`ThemeManager.kt`~~
- ~~`ImeThemeRenderDelegate.kt`~~
- ~~`BackgroundPickerActivity.kt`~~
- ~~settings 檔~~
- ~~Sprint 7 檔~~

本輪附帶驗證要求：

1. ~~`:app:compileDebugKotlin` 通過~~
2. ~~回交文件附最小 smoke 結果，或明確標註無法執行原因~~
3. ~~若無法實機 smoke，至少附 code-path smoke checklist：~~
   - ~~panel 開啟~~
   - ~~theme 選擇 callback~~
   - ~~anim style callback~~
   - ~~background settings callback~~
   - ~~panel 關閉後既有 listener / receiver 不新增殘留~~

提醒：

1. ~~不得自行指定 `Sprint 6-4`~~
2. ~~可提候選方向，但必須標為「待 CodeX A 指定」~~
3. ~~不得把本輪擴成 `ThemeManager` / background / settings 的總重構~~

#### ~~CodeX B~~

- ~~任務 ID：`S6-3-CXB-THEME-TECH-REVIEW`~~

目標：

- ~~跟審 `THEME` panel implementation 的 file scope 與 writer owner 邊界~~

本輪重點：

1. ~~檢查是否誤擴到 `ThemeManager` / `ImeThemeRenderDelegate` / `KeyboardBackgroundView`~~
2. ~~檢查 `ThemeManager.currentTheme` 與 bg 相關 prefs 的 writer owner 是否仍清楚~~
3. ~~檢查若新增 binder 檔，是否維持最小骨架~~

#### ~~Claude Code A~~

- ~~任務 ID：`S6-3-CCA-THEME-SIDE-NOTE`~~

目標：

- ~~做 `THEME` 本輪 settings-side side-note 審核~~

要求：

1. ~~不改 `.kt`~~
2. ~~若發現 settings 交集風險，只能做文件註記，不得直接開 settings implementation~~
3. ~~特別盯：~~
   - ~~`ThemeManager` singleton writer~~
   - ~~`BackgroundSettingsSection`~~
   - ~~`ThemeSettingsSection`~~
   - ~~`insightThemeSyncEnabled`~~

### ~~回交結果~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeThemePanelBinder.kt`~~
- ~~`docs/SPRINT6_3_THEME_IMPLEMENTATION_REPORT_2026-04-18.md`~~
- ~~`docs/SPRINT6_3_THEME_SETTINGS_SIDE_NOTE_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`ImeThemePanelBinder.kt` 已真正新增並落地~~
  - ~~`ImeServicePanels.kt` THEME 分支已真正從 inline 收斂成 binder call~~
- ~~殘餘既存風險：~~
  - ~~`themePanel` slot 清理是否完整，仍是 pre-existing 生命週期課題~~
  - ~~無實機 smoke，僅完成 code-path smoke checklist~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. THEME binder 只做 panel 建構、初始 snapshot 與 11 個 callback wiring。
2. `ThemeManager` listener、`reloadPreviewBitmap()`、`themePanel` binding 生命週期都保留在 service 端。
3. 未誤擴到 `ThemeManager.kt`、`KeyboardBackgroundView.kt`、`ImeThemeRenderDelegate.kt`、`BackgroundPickerActivity.kt` 或 settings 檔。
4. compile 通過。
5. settings-side 確認零影響。
```

### ~~本輪結論~~

- ~~`Sprint 6-3 = COMPLETED`~~
- ~~第三個 panel round 已完成~~
- ~~下一門開 `Sprint 6-4 = USER_DICT panel boundary`~~

---

## ~~Round S6-4：USER_DICT Panel Boundary~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~做 `USER_DICT` panel 最小邊界落地~~
- ~~在不重構字典核心與 sync 的前提下，先收斂 panel 建立與 callback 接線邊界~~

### ~~任務指派~~

#### ~~Claude Code B~~

- ~~任務 ID：`S6-4-CCB-USER_DICT-IMPLEMENTATION`~~

目標：

- ~~做 `USER_DICT` panel 最小邊界落地~~

本輪允許修改：

- ~~`app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`~~
- ~~`app/src/main/java/com/snowrealm/yukiboard/ui/panel/UserDictionaryPanelView.kt`~~
- ~~可新增一個 `app/src/main/java/com/snowrealm/yukiboard/app/ime/` 下的 `USER_DICT panel binder` 類檔~~

本輪禁止修改：

- ~~`InputRouter.kt`~~
- ~~`handleKeyPress()` 主語意~~
- ~~`UserDictionary.kt`~~
- ~~`CloudSyncManager.kt`~~
- ~~`SettingsActivity.kt`~~
- ~~`InputBehaviorSection.kt`~~
- ~~`KeyboardView.kt`~~
- ~~Sprint 7 檔~~

本輪附帶驗證要求：

1. ~~`:app:compileDebugKotlin` 通過~~
2. ~~回交文件附最小 smoke 結果，或明確標註無法執行原因~~
3. ~~若無法實機 smoke，至少附 code-path smoke checklist：~~
   - ~~panel 開啟~~
   - ~~export callback~~
   - ~~import callback~~
   - ~~input mode enter 路徑~~
   - ~~panel 關閉後不新增新的熱路徑 writer~~

提醒：

1. ~~不得自行指定 `Sprint 6-5`~~
2. ~~可提候選方向，但必須標為「待 CodeX A 指定」~~
3. ~~不得把本輪擴成 `UserDictionary` / sync / settings 字典管理總重構~~

#### ~~CodeX B~~

- ~~任務 ID：`S6-4-CXB-USER_DICT-TECH-REVIEW`~~

目標：

- ~~跟審 `USER_DICT` panel implementation 的 file scope 與 shared data 邊界~~

本輪重點：

1. ~~檢查是否誤擴到 `InputRouter` / `UserDictionary` / `CloudSyncManager`~~
2. ~~檢查 `enterInputMode()` 與 panel callback 路徑是否仍維持最小薄切~~
3. ~~檢查若新增 binder 檔，是否維持最小骨架~~

#### ~~Claude Code A~~

- ~~任務 ID：`S6-4-CCA-USER_DICT-SIDE-NOTE`~~

目標：

- ~~做 `USER_DICT` 本輪 settings-side side-note 審核~~

要求：

1. ~~不改 `.kt`~~
2. ~~若發現 settings 交集風險，只能做文件註記，不得直接開 settings implementation~~
3. ~~特別盯：~~
   - ~~`SettingsActivity` 第 190 / 402 行 `UserDictionary(this)` 相關交集~~
   - ~~`InputBehaviorSection`~~
   - ~~`CloudSyncManager`~~

### ~~回交結果~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeUserDictPanelBinder.kt`~~
- ~~`docs/SPRINT6_4_USER_DICT_IMPLEMENTATION_REPORT_2026-04-18.md`~~
- ~~`docs/SPRINT6_4_USER_DICT_SETTINGS_SIDE_NOTE_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`ImeUserDictPanelBinder.kt` 已真正新增並落地~~
  - ~~`ImeServicePanels.kt` 的 `USER_DICT` 分支已真正從 inline 收斂成 binder call~~
- ~~殘餘既存風險：~~
  - ~~`userDictPanel` slot 清理是否完整，仍是 pre-existing 生命週期課題~~
  - ~~settings / IME 各自 `new UserDictionary(this)` 仍是 pre-existing shared-data 課題~~
  - ~~無實機 smoke，僅完成 code-path smoke checklist~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. USER_DICT binder 只做 panel 建構、theme / userDictionary 注入與 3 個 callback wiring。
2. `enterInputMode()` 與 `userDictPanel` bindings slot 賦值保留在 service 端，ownership 清楚。
3. 未誤擴到 `InputRouter.kt`、`UserDictionary.kt`、`CloudSyncManager.kt`、`SettingsActivity.kt`、`InputBehaviorSection.kt` 或 Sprint 7 檔。
4. compile 通過。
5. settings-side 確認零影響。
```

### ~~本輪結論~~

- ~~`Sprint 6-4 = COMPLETED`~~
- ~~第四個 panel round 已完成~~
- ~~下一門開 `Sprint 6-5 = HANDWRITING panel boundary`~~

---

## ~~Round S6-5：HANDWRITING Panel Boundary~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~做 `HANDWRITING` panel 最小邊界落地~~
- ~~嚴格只收斂 panel 建立、初始狀態與 callback 接線，不碰 candidate RMW 熱路徑語意~~

### ~~任務指派~~

#### ~~Claude Code B~~

- ~~任務 ID：`S6-5-CCB-HANDWRITING-IMPLEMENTATION`~~

目標：

- ~~做 `HANDWRITING` panel 最小邊界落地~~

本輪允許修改：

- ~~`app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`~~
- ~~`app/src/main/java/com/snowrealm/yukiboard/ui/panel/HandwritingPanelView.kt`~~
- ~~可新增一個 `app/src/main/java/com/snowrealm/yukiboard/app/ime/` 下的 `HANDWRITING panel binder` 類檔~~

本輪禁止修改：

- ~~`KeyboardView.kt`~~
- ~~`InputRouter.kt`~~
- ~~`handleKeyPress()` 主語意~~
- ~~`applyInputResult()` 主語意~~
- ~~`HandwritingEngine.kt`~~
- ~~`ImeServiceCallbacks.kt`~~
- ~~`InputUiSync.kt`~~
- ~~settings 檔~~
- ~~Sprint 7 檔~~

本輪附帶驗證要求：

1. ~~`:app:compileDebugKotlin` 通過~~
2. ~~回交文件附最小 smoke 結果，或明確標註無法執行原因~~
3. ~~若無法實機 smoke，至少附 code-path smoke checklist：~~
   - ~~panel 開啟~~
   - ~~engine init 路徑~~
   - ~~`onCandidateSelected`~~
   - ~~`onRecognized`~~
   - ~~`onBackspace` / `onConfirm`~~
   - ~~panel 關閉後不新增新的熱路徑 writer~~

提醒：

1. ~~不得自行指定 `Sprint 6-6`~~
2. ~~可提候選方向，但必須標為「待 CodeX A 指定」~~
3. ~~不得把本輪擴成 `HandwritingEngine` / candidate pipeline / InputRouter 熱路徑重構~~

#### ~~CodeX B~~

- ~~任務 ID：`S6-5-CXB-HANDWRITING-TECH-REVIEW`~~

目標：

- ~~跟審 `HANDWRITING` panel implementation 的 file scope 與熱路徑邊界~~

本輪重點：

1. ~~檢查是否誤擴到 `KeyboardView.updateCandidates(...)` 語意~~
2. ~~檢查是否誤擴到 `InputRouter.onHandwritingCommit(...)`~~
3. ~~檢查 engine lifecycle / binder 邊界是否維持最小骨架~~

#### ~~Claude Code A~~

- ~~任務 ID：`S6-5-CCA-HANDWRITING-SIDE-NOTE`~~

目標：

- ~~做 `HANDWRITING` 本輪 settings-side side-note 審核~~

要求：

1. ~~不改 `.kt`~~
2. ~~若對 settings 無影響，直接回交「無需 settings 變更」~~
3. ~~若有影響，只能做文件註記，不得直接開 settings implementation~~

### ~~回交結果~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeHandwritingPanelBinder.kt`~~
- ~~`docs/SPRINT6_5_HANDWRITING_IMPLEMENTATION_REPORT_2026-04-18.md`~~
- ~~`docs/SPRINT6_5_HANDWRITING_SETTINGS_SIDE_NOTE_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`ImeHandwritingPanelBinder.kt` 已真正新增並落地~~
  - ~~`ImeServicePanels.kt` 的 `HANDWRITING` 分支已真正從 inline 收斂成 binder call~~
- ~~殘餘既存風險：~~
  - ~~`handwritingPanel` slot 清理是否完整，仍是 pre-existing 生命週期課題~~
  - ~~`handwritingEngine` 作為 service-level lazy cache 仍是既有設計風險，但本輪未擴大~~
  - ~~無實機 smoke，僅完成 code-path smoke checklist~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. HANDWRITING binder 只做 panel 建構、engine / theme 注入與 4 個 callback wiring。
2. `HandwritingEngine` lazy init、`ensureReady()` 與 `handwritingPanel` bindings slot 皆保留在 service 端。
3. 未誤擴到 `KeyboardView.kt`、`InputRouter.kt`、`HandwritingEngine.kt`、`ImeServiceCallbacks.kt`、`InputUiSync.kt` 或 Sprint 7 檔。
4. compile 通過。
5. settings-side 確認零影響。
```

### ~~本輪結論~~

- ~~`Sprint 6-5 = COMPLETED`~~
- ~~第五個 panel round 已完成~~
- ~~下一門開 `Sprint 6-6 = CLAW panel boundary`~~

---

## ~~Round S6-6：CLAW Panel Boundary~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~做 `CLAW` panel 最小邊界落地~~
- ~~嚴格只收斂 panel 建立、初始狀態與 callback 接線，不把本輪擴成 provider / auth / session / integration 收斂~~

### ~~任務指派~~

#### ~~Claude Code B~~

- ~~任務 ID：`S6-6-CCB-CLAW-IMPLEMENTATION`~~

目標：

- ~~做 `CLAW` panel 最小邊界落地~~

本輪允許修改：

- ~~`app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`~~
- ~~`app/src/main/java/com/snowrealm/yukiboard/ui/panel/ClawPanelView.kt`~~
- ~~可新增一個 `app/src/main/java/com/snowrealm/yukiboard/app/ime/` 下的 `CLAW panel binder` 類檔~~

本輪禁止修改：

- ~~`ClawClient.kt`~~
- ~~`AIProviderManager.kt`~~
- ~~`app/session/UserSessionRepository.kt`~~
- ~~`auth/SnowRealmAuthClient.kt`~~
- ~~`data/CloudSyncManager.kt`~~
- ~~`claw/provider/*`~~
- ~~`handleKeyPress()` 主語意~~
- ~~`applyInputResult()` 主語意~~
- ~~`InputRouter.kt`~~
- ~~settings 檔~~
- ~~Sprint 7 檔~~

本輪附帶驗證要求：

1. ~~`:app:compileDebugKotlin` 通過~~
2. ~~回交文件附最小 smoke 結果，或明確標註無法執行原因~~
3. ~~若無法實機 smoke，至少附 code-path smoke checklist：~~
   - ~~panel 開啟~~
   - ~~callback wiring~~
   - ~~panel 關閉後不新增新的 cross-layer writer~~
   - ~~不新增 provider / auth / session owner 混亂~~

提醒：

1. ~~不得自行指定 `Sprint 7-0`~~
2. ~~可提候選方向，但必須標為「待 CodeX A 指定」~~
3. ~~不得把本輪擴成 CLAW integration / provider / auth 總重構~~

#### ~~CodeX B~~

- ~~任務 ID：`S6-6-CXB-CLAW-TECH-REVIEW`~~

目標：

- ~~跟審 `CLAW` panel implementation 的 file scope 與 integration 邊界純度~~

本輪重點：

1. ~~檢查是否誤擴到 `ClawClient` / `AIProviderManager` / `UserSessionRepository` / `SnowRealmAuthClient`~~
2. ~~檢查是否把 panel binder 做成半個 Sprint 7 integration 收斂~~
3. ~~檢查 binder 是否維持最小骨架~~

#### ~~Claude Code A~~

- ~~任務 ID：`S6-6-CCA-CLAW-SIDE-NOTE`~~

目標：

- ~~做 `CLAW` 本輪 settings-side / auth-side / provider-side side-note 審核~~

要求：

1. ~~不改 `.kt`~~
2. ~~若發現 settings / auth / provider 交集風險，只能做文件註記，不得直接開 implementation~~
3. ~~特別盯：~~
   - ~~`AIProviderManager`~~
   - ~~`SnowRealmAuthClient`~~
   - ~~`UserSessionRepository`~~
   - ~~`CloudSyncManager`~~
   - ~~settings / auth / provider 共用狀態來源~~

### ~~回交結果~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeClawPanelBinder.kt`~~
- ~~`docs/SPRINT6_6_CLAW_IMPLEMENTATION_REPORT_2026-04-18.md`~~
- ~~`docs/SPRINT6_6_CLAW_SETTINGS_SIDE_NOTE_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`ImeClawPanelBinder.kt` 已真正新增並落地~~
  - ~~`ImeServicePanels.kt` 的 `CLAW` 分支已真正從 inline 收斂成 binder call~~
- ~~殘餘既存風險：~~
  - ~~`CLAW` 相關 integration owner 仍分散於既有 architecture，屬 Sprint 7 題，不是本輪新增問題~~
  - ~~無實機 smoke，僅完成 code-path smoke checklist~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. CLAW binder 只做 panel 建構、theme / clawClient / prefs 注入、初始 mode 與 provider / callback wiring。
2. `AIProviderManager`、`SnowRealmAuthClient`、`UserSessionRepository`、`CloudSyncManager` 與 `claw/provider/*` 都未被誤擴進本輪。
3. `keyboardView.currentMode` 轉換、`inputRouter.currentAppPackage` 讀取、`currentInputConnection` commit 與 `InviteCodeActivity` 啟動都仍留在 service 端 owner。
4. compile 通過。
5. settings-side / auth-side / provider-side 確認零影響。
```

### ~~本輪結論~~

- ~~`Sprint 6-6 = COMPLETED`~~
- ~~第六個 panel round 已完成~~
- ~~`Sprint 6` 全部 round（6 panel + 1 bugfix）已正式結案~~
- ~~下一步轉入 `Sprint 7` 前置作業 `S7-PRE`~~

---

## Sprint 6 當前總狀態

- 已完成：
  - `S6-0` `總盤點與啟動 Gate`
  - `S6-1` `MUSIC Panel Boundary`
  - `S6-BUG` `floating mode stale true`
  - `S6-2` `REMOTE Panel Boundary`
  - `S6-3` `THEME Panel Boundary`
  - `S6-4` `USER_DICT Panel Boundary`
  - `S6-5` `HANDWRITING Panel Boundary`
  - `S6-6` `CLAW Panel Boundary`
- 進行中：
  - 無
- 未開門：
  - 無
- `Sprint 7`：
  - `READY TO START S7-PRE WHEN CODEX A EXPLICITLY OPENS`

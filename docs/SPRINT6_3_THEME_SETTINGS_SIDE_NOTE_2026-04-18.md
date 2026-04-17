# Sprint 6-3 THEME Settings Side-Note（2026-04-18）

回交人：Claude Code A

任務 ID：`S6-3-CCA-THEME-SIDE-NOTE`

依據：`Sprint6-All-Round-Gate.md` Round `S6-3 THEME Panel Boundary`

## 0. 本輪本質

- `S6-3-CCA` 要求：THEME 本輪 settings-side side-note 審核；若零影響則回交「無需 settings 變更」，若有影響只做文件註記不得開 settings implementation。
- THEME 於 `SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` 被歸類為 settings 高交集 panel（與 CLAW 同級），本 side-note 需比 MUSIC / REMOTE 更詳實說明 callback 路徑是否真的 1:1 等價。
- 本文件即為該正式回交憑證。未改任何 `.kt`。

## 1. 審核對象

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeThemePanelBinder.kt`（新增 57 行）
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`（THEME 分支 94 行 → binder call + service-side listener 生命週期）
- `docs/SPRINT6_3_THEME_IMPLEMENTATION_REPORT_2026-04-18.md`（Claude Code B 回交）

## 2. Settings-Side Grep 驗證

### 2.1 `ImeThemePanelBinder.kt` 本體

| Keyword | Hit |
|---------|:---:|
| `YukiPreferences` | 0 |
| `prefs.` | 0 |
| `SettingsActivity` | 0 |
| `AISettings` / `AISettingsActivity` | 0 |
| `AIProviderManager` | 0 |
| `SnowRealmAuthClient` / `AuthRepository` | 0 |
| `UserSessionRepository` | 0 |
| `CloudSyncManager` / `CloudSync` | 0 |
| `Insight` | 0 |
| `BackgroundSettingsSection` | 0 |
| `ThemeSettingsSection` | 0 |
| `insightThemeSyncEnabled` | 0 |
| `ThemeManager` | 0 |
| `KeyboardBackgroundView` | 0 |
| `BackgroundPickerActivity` | 0 |

binder 本體 import 僅 3 項（`Context` / `KeyboardTheme` / `ThemePanelView`），settings 軸 0 import。

### 2.2 `ImeServicePanels.kt` THEME 分支（lines 210–306）

| Keyword | THEME 分支 Before | THEME 分支 After | 變化 |
|---------|:----------------:|:---------------:|:----:|
| `YukiPreferences` | 0 | 0 | 無 |
| `SettingsActivity` | 0 | 0 | 無 |
| `AISettings` | 0 | 0 | 無 |
| `AIProviderManager` | 0 | 0 | 無 |
| `SnowRealmAuth` | 0 | 0 | 無 |
| `UserSessionRepository` | 0 | 0 | 無 |
| `CloudSync` | 0 | 0 | 無 |
| `Insight` | 0 | 0 | 無 |
| `BackgroundSettingsSection` | 0 | 0 | 無 |
| `ThemeSettingsSection` | 0 | 0 | 無 |
| `insightThemeSyncEnabled` | 0 | 0 | 無 |

### 2.3 THEME callback body 實際讀寫的 prefs 欄位對照

（callback body 保留於 service 層，以 lambda 具名參數傳入 binder；Before / After 皆為 service 端 prefs 直讀直寫）

| callback | 讀 / 寫 prefs 欄位 | Before | After | 差異 |
|---------|-----------------|:-----:|:-----:|:----:|
| `onThemeSelected` | 寫 `prefs.useCustomBackground = false` | ✅ | ✅ | 無 |
| `onClearBackground` | 寫 `prefs.useCustomBackground = false` | ✅ | ✅ | 無 |
| `onBackgroundSettingsChanged` | 讀 `prefs.bgScaleMode` / `bgOffsetX` / `bgOffsetY` / `bgRotation` | ✅ | ✅ | 無 |
| `onKeySettingsChanged` | 讀 `prefs.keyScalePercent` / `keyOpacity` / `keyTextOpacity` / `useCustomBackground` / `transparentKeyboard` | ✅ | ✅ | 無 |
| `onPerKeySizeChanged` | 讀 `prefs.getAllPerKeySizes()` | ✅ | ✅ | 無 |
| `onThemeSelected` | 寫 `ThemeManager.currentTheme = selected`（singleton writer） | ✅ | ✅ | 無 |
| `onAnimThemeSelected` / `onOverlayAlphaChanged` / `onLabelColorChanged` / `onPickBackground` / `onPickVideo` / `onKeyboardHeightChanged` | 不碰 prefs | — | — | 無 |

**核心結論**：本輪 binder 不改變任何 prefs writer / reader 路徑；callback body 僅從 `p.onX = { ... }` inline 位置搬到「以具名參數傳入 `ImeThemePanelBinder.bind(...)`」，lambda 捕獲語境仍在 `YukiBoardIMEService`，`prefs` / `ThemeManager` / `keyboardView` / `backgroundView` 所有 writer owner 與 Before 100% 一致。

## 3. 對 0418 Impact Review 預判的核對

`SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` §1.5 對 THEME 的預判：

| Impact Review §1.5 預判（THEME） | Sprint 6-3 實作結果 | 核對 |
|-------------------------------|-------------------|------|
| Prefs 直讀：`useCustomBackground` / `bg*` / `key*` / `transparentKeyboard` 等 | callback body 仍讀寫同一批欄位，未新增／刪減 | ✅ 一致 |
| `ThemeManager` singleton writer：`currentTheme` | `onThemeSelected` 仍走 `tm.currentTheme = selected`，路徑未改 | ✅ 一致 |
| `ThemeSettingsSection` 交集：`BackgroundSettingsSection` 持有 bg prefs UI | 本輪未觸 section 檔，UI 行為不變 | ✅ 一致 |
| `insightThemeSyncEnabled` 交集：Insight 同步若開啟會雙寫 | 本輪 callback 無任何 Insight 同步路徑；該 prefs 未被讀寫 | ✅ 一致 |
| `BackgroundPickerActivity` 交集：由 panel 透過 Intent 啟動 | Intent 建構位置不變（service 端 callback body 內），`EXTRA_PICK_TYPE` / `FLAG_ACTIVITY_NEW_TASK` 皆保留 | ✅ 一致 |
| `ThemeManager.addOnThemeChangedListener` 生命週期 | 仍於 service 端 `addOnDetachListener { removeOnThemeChangedListener }` 單一出口註銷 | ✅ 一致 |

Impact Review 預判與實作結果 1:1 對齊，無需修正該文件。

## 4. 四個 CodeX A 指定盯看點

本輪 `Sprint6-All-Round-Gate.md` §S6-3-CCA 明定特別盯 4 個 settings 交集點，逐項核對如下：

### 4.1 `ThemeManager` singleton writer

| 檢查項 | 狀態 |
|--------|------|
| binder 是否 import `ThemeManager` | ❌ 否（禁區守住） |
| binder 是否在 `bind(...)` 內呼叫 `ThemeManager.getInstance(...)` | ❌ 否 |
| `tm.currentTheme = selected` writer 位置 | service 端 `onThemeSelected` lambda body（Before / After 同） |
| `tm.addOnThemeChangedListener` / `removeOnThemeChangedListener` 呼叫點 | service 端 `createHighDependencyPanelOrNull()` 內 `.also { bindings.themePanel = it }` 之後，與 `p.addOnDetachListener { ... }` 成對（Before / After 同） |
| writer owner | `YukiBoardIMEService` 仍為 `ThemeManager` 的 panel-scoped writer owner，未下放到 binder |

**結論**：`ThemeManager` singleton writer owner 完全未動，binder 刻意不引入以守住禁區。

### 4.2 `BackgroundSettingsSection`

| 檢查項 | 狀態 |
|--------|------|
| 本輪是否修改 `BackgroundSettingsSection.kt` | ❌ 否 |
| 本輪 binder / THEME 分支是否 import `BackgroundSettingsSection` | ❌ 否 |
| `BackgroundSettingsSection` 消費的 bg prefs 是否被本輪改寫 | ❌ 否（`bgScaleMode` / `bgOffsetX` / `bgOffsetY` / `bgRotation` 僅由 `onBackgroundSettingsChanged` 讀出套到 `backgroundView`，不 writer 這些欄位） |
| 設定頁 `BackgroundSettingsSection` 的 UI 行為是否改變 | ❌ 否 |

**結論**：`BackgroundSettingsSection` 零影響。該 section 仍為 bg prefs 的唯一 UI writer，THEME panel 僅做 read-and-apply。

### 4.3 `ThemeSettingsSection`

| 檢查項 | 狀態 |
|--------|------|
| 本輪是否修改 `ThemeSettingsSection.kt` | ❌ 否 |
| 本輪是否 import `ThemeSettingsSection` | ❌ 否 |
| `ThemeSettingsSection` 的 `currentTheme` 顯示 / 切換邏輯 | 未動；仍由該 section 自身透過 `ThemeManager.getInstance` 維護 |
| 設定頁 theme 切換 vs IME 內 THEME panel 切換的雙寫路徑 | 雙邊皆走 `ThemeManager.currentTheme = ...`，listener 回呼統一；本輪未改動該匯流 |

**結論**：`ThemeSettingsSection` 零影響。

### 4.4 `insightThemeSyncEnabled`

| 檢查項 | 狀態 |
|--------|------|
| 本輪 binder 是否 import `Insight*` | ❌ 否 |
| `ImeServicePanels.kt` THEME 分支 Before / After 對 `insightThemeSyncEnabled` 的讀寫 | 0 hit / 0 hit |
| 該 prefs 唯一消費點 | `InsightSettingsSection.kt`（本輪未動）+ `YukiPreferences.kt` 欄位定義（本輪未動） |
| IME 端 THEME 切換是否觸發 Insight 雲端同步 | 本輪未新增此路徑；`onThemeSelected` callback body 無任何 Insight client 呼叫 |

**結論**：`insightThemeSyncEnabled` 零影響。雲端同步是否對 IME 端 theme 切換生效，仍取決於 `InsightSettingsSection` 端既有設計，與本輪 panel boundary 無交集。

## 5. 關鍵邊界確認：callback body 遷移是否改變 writer owner？

這是 THEME 本輪唯一需要特別驗證的點（MUSIC / REMOTE 皆無此量級的 callback chain）：

| 面向 | 判定 |
|------|------|
| callback body 實際執行時的 `this` receiver | `YukiBoardIMEService`（Before 與 After 皆於 service 端宣告 lambda，捕獲 `this` 為 service 本體） |
| `prefs` / `keyboardView` / `backgroundView` / `toolbarView` 所屬 owner | `YukiBoardIMEService` 私有成員（未下放到 binder） |
| binder 是否持有 `YukiBoardIMEService` 強參考 | ❌ 否（僅接 `Context` 與已封裝 lambda） |
| callback 觸發點 | `ThemePanelView` 內部 touch / slider / button 事件（未動） |
| callback → service → prefs / view 的完整 writer chain | 未改；lambda 位置從 `p.onX = { ... }` → `ImeThemePanelBinder.bind(onX = { ... })` 僅為語法位移 |
| `previewBgBitmap = null` 改寫路徑（Impl Report §D.1 自揭點） | 由 closure `p` 改走 `imePanelBindings.themePanel?` slot；此 slot 由 `.also` 於同一 statement 賦值，panel 存活期間該 slot = 該 panel；panel detach 後 callback 不再觸發，無空指風險。**與 settings 無關，屬 bindings slot 生命週期觀察**。 |

**結論**：writer owner 未變，邊界 1:1 等價，對 settings 軸零影響。

## 6. 對 settings UI 的連帶影響評估

| 項目 | 評估結果 |
|------|---------|
| `SettingsActivity` 行數 | 維持 455 行（與 0416 基線一致） |
| `ThemeSettingsSection` / `BackgroundSettingsSection` 內 UI widget | 無變動 |
| `toolbar_*` Bool prefs（含 `toolbarTheme` 顯示開關） | 無變動 |
| `ThemeManager.currentTheme` 雙向匯流（settings ↔ IME） | 無變動 |
| `insightSettingsBackup` 備份 payload（含 prefs 快照） | payload 無變動（未新增／刪除 prefs 欄位） |
| `CloudSyncManager.uploadAll / downloadAll` payload | 無變動 |
| `settingsCoordinator.getApiKey(...)` 路徑 | 無變動 |
| `BackgroundPickerActivity` 啟動路徑 | 仍由 THEME panel callback 透過 `Intent(FLAG_ACTIVITY_NEW_TASK)` 啟動；未改 |
| 廣播 `ACTION_BG_UPDATED`（`onBackgroundSettingsChanged` 末尾） | 仍發送；contract 未動 |

全部維持基線。

## 7. Settings-Side 本輪禁區是否守住？

對照 `SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` §4 自我約束清單：

| 禁區 | 本輪狀態 |
|------|---------|
| `SettingsActivity.kt` 不得新增 section / router / UI | ✅ 未動 |
| `BackgroundSettingsSection.kt` 不得切分 / 改 prefs 欄位 | ✅ 未動 |
| `ThemeSettingsSection.kt` 不得改 singleton writer owner | ✅ 未動 |
| `AISettingsActivity.kt` / `AISettingsSection.kt` 不得遷移 provider owner | ✅ 未動 |
| `CloudSyncManager.kt` 不得改 payload / gateway | ✅ 未動 |
| `SnowRealmAuthClient` / `UserSessionRepository` 不得改 session writer | ✅ 未動 |
| `toolbar_*` Bool prefs 不得新增或改名 | ✅ 未動 |

全數守住。

## 8. 殘留待觀察事項（僅紀錄，不改）

Claude Code B 報告 §D.2 自揭既存風險：

1. **`ImePanelBindings.clearInputModeBindings()` 未清 `themePanel`**：屬 Sprint 6-3 之外的邊界課題，與 Sprint 6-2 `remotePanel` 觀察同性質。settings-side 無關。
2. **`ThemeManager` / `KeyboardBackgroundView` / `BackgroundPickerActivity` 跨檔依賴仍維持**：此為 Sprint 6-3 禁區限制下的預期結果，settings-side 無關。
3. **本機無實機 smoke**：主題切換 / 背景選擇 / 透明鍵盤 / 鍵盤高度變更需實機覆蓋；實機 smoke 是否補交由 `CodeX A` 決定；與 settings 側無關。
4. **IME 端 THEME 切換 vs `ThemeSettingsSection` 端 theme 切換的雲端同步行為差異**（若 `insightThemeSyncEnabled = true`）：此為 Sprint 6-3 之外、屬 `InsightSettingsSection` 與 `ThemeManager` 既有設計議題；本輪未擴大。

## 9. 回交格式

### A. 變更摘要

- 新增 `docs/SPRINT6_3_THEME_SETTINGS_SIDE_NOTE_2026-04-18.md`
- 未改任何 `.kt`
- 正式結論：**Sprint 6-3 THEME 實作對 settings 線無影響，無需 settings 變更**

### B. 風險

- 本輪 settings-side 無引入任何風險。
- THEME 為 settings 高交集 panel，本輪實作仍維持「binder 不碰 prefs / ThemeManager / sections，callback body 保留於 service 端」的分層；未來若要把 `ThemeSettingsSection` 與 IME THEME panel 的雙向匯流重構（例如收斂到單一 state source 或 Repository），會需要 settings 側配合；屬未排定議題，本輪不動。
- `insightThemeSyncEnabled` 未來若被提升為 IME 端即時雙寫觸發點，會需要重新評估 THEME callback 是否要走 Repository；本輪不預做。

### C. 未做的事

- 未對 Claude Code B 的實作做 runtime / 實機 smoke（不屬 settings-side 職責；主題切換 / 背景選擇 / 鍵盤高度變更驗證由 `CodeX A` 決定是否授權補交）。
- 未碰任何 `.kt`、未新增實作、未修改既有 settings 文件或 impact review 本體。
- 未對後續 panel / round 順序做任何排序或暗示（遵守 `feedback_gate_no_self_schedule.md`）。

### D. 下一步建議

- 以下僅為候選方向，**最終順序由 `CodeX A` 指定**，本文件不預排：
  - 候選：下一個 panel round（未開門 panel：`CLAW` / `HANDWRITING` / `USER_DICT`）
  - 候選：其他 bugfix round
  - 候選：其他 audit / 觀察輪
- Claude Code A 本輪後重回待命位。
- 若 `CodeX A` 指派下一輪為 settings 交集高的 panel（`CLAW` / `USER_DICT`），我可在實作前提交前置 settings 提醒；settings 交集低的 panel（`HANDWRITING`）只需實作後補 side-note。具體做法待 `CodeX A` 指派。

### E. 請 `CodeX A` 判定

1. 本輪 side-note 是否符合 `S6-3-CCA-THEME-SIDE-NOTE` 交付標準？
2. 是否授權 Claude Code A 重回待命位？

---

（本文件僅為 side-note 正式回交憑證，完全未改 `.kt`，不自排後續順序。）

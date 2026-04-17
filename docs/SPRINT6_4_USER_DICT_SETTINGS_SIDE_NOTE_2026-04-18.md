# Sprint 6-4 USER_DICT Settings Side-Note（2026-04-18）

回交人：Claude Code A

任務 ID：`S6-4-CCA-USER_DICT-SIDE-NOTE`

依據：`Sprint6-All-Round-Gate.md` Round `S6-4 USER_DICT Panel Boundary`

## 0. 本輪本質

- `S6-4-CCA` 要求：USER_DICT 本輪 settings-side side-note 審核；若零影響則回交「無需 settings 變更」，若有影響只做文件註記不得開 settings implementation。
- USER_DICT 於 `SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` 被歸類為 settings 中交集 panel（有 sync / settings 內 `UserDictionary(this)` 雙實例交集），比 MUSIC / REMOTE 高，比 THEME / CLAW 低。
- 本 side-note 需明確核對三個 CodeX A 指定盯看點：`SettingsActivity:190/402` `UserDictionary(this)` 交集、`InputBehaviorSection`、`CloudSyncManager`。
- 本文件即為該正式回交憑證。未改任何 `.kt`。

## 1. 審核對象

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeUserDictPanelBinder.kt`（新增 38 行）
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`（USER_DICT 分支 50 行 → binder call + service-side `enterInputMode()` / bindings slot 賦值）
- `docs/SPRINT6_4_USER_DICT_IMPLEMENTATION_REPORT_2026-04-18.md`（Claude Code B 回交）

## 2. Settings-Side Grep 驗證

### 2.1 `ImeUserDictPanelBinder.kt` 本體

| Keyword | Code Hit | 備註 |
|---------|:---:|------|
| `YukiPreferences` | 0 | — |
| `prefs.` | 0 | — |
| `SettingsActivity` | 0 | — |
| `AISettings` / `AISettingsActivity` | 0 | — |
| `AIProviderManager` | 0 | — |
| `SnowRealmAuthClient` / `AuthRepository` | 0 | — |
| `UserSessionRepository` | 0 | — |
| `CloudSyncManager` / `CloudSync` | 0 | — |
| `Insight` | 0 | — |
| `InputBehaviorSection` | 0 | — |
| `BackgroundSettingsSection` / `ThemeSettingsSection` | 0 | — |
| `settings` | 1（僅 javadoc 註釋中「不觸碰 ... settings」文字） | 非代碼引用 |
| `UserDictionary` 內部 API（`addEntry` / `removeEntry` / `syncUserDict` 等） | 0 | binder 僅接既建構實例，不 reach into |

binder 本體 import 僅 5 項（`AlertDialog` / `Context` / `UserDictionary`（實例注入）/ `KeyboardTheme` / `UserDictionaryPanelView`），settings 軸 0 import。

### 2.2 `ImeServicePanels.kt` USER_DICT 分支（lines 348–)

| Keyword | USER_DICT 分支 Before | USER_DICT 分支 After | 變化 |
|---------|:-------------------:|:-------------------:|:----:|
| `SettingsActivity` | 0 | 0 | 無 |
| `InputBehaviorSection` | 0 | 0 | 無 |
| `CloudSyncManager` / `syncUserDict` | 0 | 0 | 無 |
| `CloudSync` | 0 | 0 | 無 |
| `Insight` | 0 | 0 | 無 |
| `AISettings` | 0 | 0 | 無 |
| `AIProviderManager` | 0 | 0 | 無 |
| `SnowRealmAuth` | 0 | 0 | 無 |
| `UserSessionRepository` | 0 | 0 | 無 |
| `prefs.` | 0 | 0 | 無 |
| `YukiPreferences` | 0 | 0 | 無 |
| `UserDictionary(this)` 建構位置 | service 端 inline `p.userDictionary = UserDictionary(this)` | service 端 `userDictionary = com.snowrealm...UserDictionary(this)` 傳入 binder | 僅語法位移，建構 owner 未變 |

**核心結論**：本輪 binder 不改變任何 settings / sync / auth / provider / prefs 路徑。callback body（3 個：`onShowDialog` / `onExportRequest` / `onImportRequest`）100% 保留於 service 端 lambda，file I/O / `FileProvider` / `ACTION_SEND` Intent / `importEntries` 呼叫語意與 Before 等價。

## 3. 對 0418 Impact Review 預判的核對

`SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` §1.6 對 USER_DICT 的預判：

| Impact Review §1.6 預判（USER_DICT） | Sprint 6-4 實作結果 | 核對 |
|----------------------------------|-------------------|------|
| Prefs 直讀：零（僅透過 `UserDictionary` 封裝存取 SharedPreferences） | binder 0 `prefs.`；service 端 diff 亦 0 | ✅ 一致 |
| Auth：零 | binder 不引用 auth | ✅ 一致 |
| AI provider：零 | binder 不引用 provider | ✅ 一致 |
| Sync 交集：`CloudSyncManager.syncUserDict` 路徑屬 pre-existing，不應被擴大 | binder / service diff 均未 import `CloudSyncManager` | ✅ 一致 |
| Settings 端雙實例：`SettingsActivity:190/402` `UserDictionary(this)` 直讀 | 該兩行仍為 settings 端持有；本輪未觸、未統一 | ✅ 一致（pre-existing 課題不變） |
| `InputBehaviorSection` 交集：詞庫相關設定 UI | 本輪未觸 section 檔 | ✅ 一致 |
| Theme data：只讀 `KeyboardTheme` | binder 僅 `panel.theme = theme` 傳遞 | ✅ 一致 |
| Toolbar 開關：僅 `toolbarUserDict` | 本輪未碰 `toolbar_*` 路徑 | ✅ 一致 |

Impact Review 預判與實作結果 1:1 對齊，無需修正該文件。

## 4. 三個 CodeX A 指定盯看點

本輪 `Sprint6-All-Round-Gate.md` §S6-4-CCA 明定特別盯 3 個 settings 交集點，逐項核對如下：

### 4.1 `SettingsActivity` 第 190 / 402 行 `UserDictionary(this)` 相關交集

| 檢查項 | 狀態 |
|--------|------|
| `SettingsActivity:190` `val dict = com.snowrealm.yukiboard.input.dictionary.UserDictionary(this)` | ✅ 仍存在，本輪未動 |
| `SettingsActivity:402` 同上 | ✅ 仍存在，本輪未動 |
| service 端 `UserDictionary(this)` 建構位置（`ImeServicePanels.kt` USER_DICT 分支） | ✅ 仍由 service 端建構並傳入 binder，owner 未變 |
| Settings 端 vs Service 端 `UserDictionary(this)` 雙實例（兩個實例讀寫同一 SharedPreferences） | ⚠️ Pre-existing shared data 課題；本輪未統一、未擴大 |
| 本輪 binder 是否 reach into `UserDictionary` 內部 API 來改變 shared writer 數量 | ❌ 否（binder 只做 `panel.userDictionary = userDictionary`，不 call `addEntry` / `removeEntry` / `saveEntries` / `syncUserDict`） |
| `SettingsActivity` 行數 | 維持 455 行（與 0416 基線一致） |

**結論**：`SettingsActivity:190/402` 交集零影響。兩個 `UserDictionary(this)` 實例並存仍是 Sprint 6 之外的 shared data 邊界課題，未來若要收斂（例如統一到 `UserDictionaryRepository` 單例）屬重構議題，本輪不動。Claude Code B 報告 §D.2.2 亦自揭此 pre-existing 課題。

### 4.2 `InputBehaviorSection`

| 檢查項 | 狀態 |
|--------|------|
| 本輪是否修改 `InputBehaviorSection.kt` | ❌ 否 |
| binder / USER_DICT 分支是否 import `InputBehaviorSection` | ❌ 否 |
| `InputBehaviorSection` 消費的輸入行為 prefs（例 `autoCapitalize` / `smartCorrection` 等）是否被本輪改寫 | ❌ 否 |
| 設定頁「輸入行為」section 的 UI 行為是否改變 | ❌ 否 |
| USER_DICT panel 內的 CRUD 操作是否會廣播 / callback 到 `InputBehaviorSection` | ❌ 否（原本即無此路徑，本輪未新增） |

**結論**：`InputBehaviorSection` 零影響。

### 4.3 `CloudSyncManager`

| 檢查項 | 狀態 |
|--------|------|
| 本輪是否修改 `CloudSyncManager.kt` | ❌ 否 |
| binder / USER_DICT 分支是否 import `CloudSyncManager` | ❌ 否 |
| `CloudSyncManager.syncUserDict` 路徑是否被新增呼叫點 | ❌ 否 |
| `CloudSyncManager.uploadAll` / `downloadAll` payload 是否改變 | ❌ 否（未新增／刪除 sync key） |
| `insightSettingsBackup` 備份 payload（含使用者詞庫 entry snapshot） | ❌ 未動 |
| panel export / import 與 sync 的一致性 | ⚠️ Pre-existing 課題（panel export/import 與 `syncUserDict` 是兩條獨立路徑），本輪未統一 |

**結論**：`CloudSyncManager` 零影響。USER_DICT 的雲端同步路徑（若已接）仍由既有 `CloudSyncManager.syncUserDict` 負責；panel 端 export/import 為手動 file-based 路徑，兩者本輪均未改動。

## 5. 關鍵邊界確認：callback body 遷移是否改變 writer owner？

| 面向 | 判定 |
|------|------|
| callback body 實際執行時的 `this` receiver | `YukiBoardIMEService`（Before 與 After 皆於 service 端宣告 lambda，捕獲 `this` 為 service 本體） |
| `showImeDialog` / `FileProvider` / `startActivity` / `Toast` / `getExternalFilesDir` 所屬 owner | `YukiBoardIMEService` / Android framework（未下放到 binder） |
| `UserDictionary` 內部 writer（`addEntry` / `saveEntries`） | `UserDictionaryPanelView` 內部 delegate（未動） |
| binder 是否持有 `YukiBoardIMEService` 強參考 | ❌ 否（僅接 `Context` / `UserDictionary` 實例 / 3 個已封裝 lambda） |
| `importEntries` 改走 slot（Impl Report §A.2 自揭點） | 由 closure `p` 改走 `imePanelBindings.userDictPanel?`，panel 存活期間等價；與 settings 無關，屬 bindings 生命週期觀察 |
| `enterInputMode()` writer owner | 仍在 service 端 `.also { slot = it }` 之後呼叫（與 Sprint 6-3 THEME `reloadPreviewBitmap()` 同原則） |

**結論**：writer owner 未變，邊界 1:1 等價，對 settings / sync 軸零影響。

## 6. 對 settings UI / sync payload 的連帶影響評估

| 項目 | 評估結果 |
|------|---------|
| `SettingsActivity` 行數 | 維持 455 行 |
| `InputBehaviorSection` 內 UI widget 或邏輯 | 無變動 |
| 設定頁「詞庫管理」/「個人詞庫」相關 section | 無變動 |
| `toolbar_*` Bool prefs（含 `toolbarUserDict` 顯示開關） | 無變動 |
| `insightSettingsBackup` 備份 payload | 無變動（未新增／刪除 prefs 欄位） |
| `CloudSyncManager.uploadAll / downloadAll` payload | 無變動 |
| `CloudSyncManager.syncUserDict` 觸發點 | 無變動 |
| `settingsCoordinator.getApiKey(...)` 路徑 | 無變動 |
| panel export 檔案路徑 `yukiboard_user_dict.txt` + `FileProvider` URI | 維持基線（Intent flags / MIME / EXTRA 全同） |
| panel import 檔案讀取 `readText()` → `importEntries(...)` | 維持基線 |

全部維持基線。

## 7. Settings-Side 本輪禁區是否守住？

對照 `SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` §4 自我約束清單：

| 禁區 | 本輪狀態 |
|------|---------|
| `SettingsActivity.kt` 不得新增 section / router / UI | ✅ 未動 |
| `InputBehaviorSection.kt` 不得改 prefs 欄位 / 詞庫管理 UI | ✅ 未動 |
| `BackgroundSettingsSection.kt` / `ThemeSettingsSection.kt` | ✅ 未動 |
| `AISettingsActivity.kt` / `AISettingsSection.kt` 不得遷移 provider owner | ✅ 未動 |
| `CloudSyncManager.kt` 不得改 payload / gateway / `syncUserDict` | ✅ 未動 |
| `SnowRealmAuthClient` / `UserSessionRepository` 不得改 session writer | ✅ 未動 |
| `toolbar_*` Bool prefs 不得新增或改名 | ✅ 未動 |
| `UserDictionary.kt` 本身 | ✅ 未動（Sprint 6-4 允許修改清單外，binder 不 reach into） |

全數守住。

## 8. 殘留待觀察事項（僅紀錄，不改）

Claude Code B 報告 §D.2 自揭既存風險：

1. **`ImePanelBindings.clearInputModeBindings()` 未清 `userDictPanel`**：屬 Sprint 6-4 之外的邊界課題，與 Sprint 6-2 / 6-3 同性質。settings-side 無關。
2. **`SettingsActivity:190/402` vs service 端 `UserDictionary(this)` 雙實例並存**：pre-existing shared data 課題，與 settings 直接相關但屬未排定議題。若未來要統一，需 settings 側配合（例如導入 `UserDictionaryRepository` 單例或遷移到 `application` 層）；本輪不動。
3. **panel 端 export/import 與 `CloudSyncManager.syncUserDict` 的一致性**：pre-existing 整合邊界課題；未來若要把手動 export/import 與雲端同步統一為單一 Repository，會需要 sync 側與 settings 側同時配合；本輪不動。
4. **本機無實機 smoke**：CRUD、export/import Intent、搜尋、分類切換、input mode 接收鍵盤輸入需實機覆蓋；實機 smoke 是否補交由 `CodeX A` 決定；與 settings 側無關。

## 9. 回交格式

### A. 變更摘要

- 新增 `docs/SPRINT6_4_USER_DICT_SETTINGS_SIDE_NOTE_2026-04-18.md`
- 未改任何 `.kt`
- 正式結論：**Sprint 6-4 USER_DICT 實作對 settings 線無影響，無需 settings 變更**

### B. 風險

- 本輪 settings-side 無引入任何風險。
- USER_DICT 為 settings 中交集 panel，本輪實作仍維持「binder 不碰 sync / settings，callback body 與 `UserDictionary` 建構留在 service 端」的分層；未來若要把 `SettingsActivity` 兩處 `UserDictionary(this)` 與 service 端實例統一到 Repository，會需要 settings 側配合；屬未排定議題，本輪不動。
- `CloudSyncManager.syncUserDict` 未來若被提升為 IME panel CRUD 即時觸發雲端同步，會需要重新評估 binder / service 端 callback 是否要走 Repository；本輪不預做。

### C. 未做的事

- 未對 Claude Code B 的實作做 runtime / 實機 smoke（不屬 settings-side 職責；CRUD / export / import / 分類搜尋 / input mode 驗證由 `CodeX A` 決定是否授權補交）。
- 未碰任何 `.kt`、未新增實作、未修改既有 settings 文件或 impact review 本體。
- 未對後續 panel / round 順序做任何排序或暗示（遵守 `feedback_gate_no_self_schedule.md`）。
- 未統一 `SettingsActivity:190/402` 與 service 端 `UserDictionary(this)` 雙實例（非本輪 scope）。

### D. 下一步建議

- 以下僅為候選方向，**最終順序由 `CodeX A` 指定**，本文件不預排：
  - 候選：下一個 panel round（未開門 panel：`CLAW` / `HANDWRITING`）
  - 候選：其他 bugfix round
  - 候選：其他 audit / 觀察輪（如 shared data 邊界專題）
- Claude Code A 本輪後重回待命位。
- 若 `CodeX A` 指派下一輪為 settings 交集高的 panel（`CLAW`），我可在實作前提交前置 settings 提醒；settings 交集低的 panel（`HANDWRITING`）只需實作後補 side-note。具體做法待 `CodeX A` 指派。

### E. 請 `CodeX A` 判定

1. 本輪 side-note 是否符合 `S6-4-CCA-USER_DICT-SIDE-NOTE` 交付標準？
2. 是否授權 Claude Code A 重回待命位？

---

（本文件僅為 side-note 正式回交憑證，完全未改 `.kt`，不自排後續順序。）

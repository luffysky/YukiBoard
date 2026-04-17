# Sprint 6-5 HANDWRITING Settings Side-Note（2026-04-18）

回交人：Claude Code A

任務 ID：`S6-5-CCA-HANDWRITING-SIDE-NOTE`

依據：`Sprint6-All-Round-Gate.md` Round `S6-5 HANDWRITING Panel Boundary`

## 0. 本輪本質

- `S6-5-CCA` 要求：HANDWRITING 本輪 settings-side side-note 審核；若零影響則回交「無需 settings 變更」，若有影響只做文件註記不得開 settings implementation。
- HANDWRITING 於 `SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` 被歸類為 settings 低交集 panel（風險主要在熱路徑 `KeyboardView.updateCandidates` / `InputRouter.onHandwritingCommit`，非 settings 軸），本輪 gate 未指定特別盯看點。
- 本文件即為該正式回交憑證。未改任何 `.kt`。

## 1. 審核對象

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeHandwritingPanelBinder.kt`（新增 39 行）
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`（HANDWRITING 分支 46 行 → binder call + service-side engine lifecycle / `ensureReady` / bindings slot 賦值）
- `docs/SPRINT6_5_HANDWRITING_IMPLEMENTATION_REPORT_2026-04-18.md`（Claude Code B 回交）

## 2. Settings-Side Grep 驗證

### 2.1 `ImeHandwritingPanelBinder.kt` 本體

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
| `InputBehaviorSection` | 0 |
| `BackgroundSettingsSection` / `ThemeSettingsSection` | 0 |
| `handwritingEnabled` | 0 |
| `toolbarHandwriting` | 0 |

binder 本體 import 僅 4 項（`Context` / `HandwritingEngine`（實例注入）/ `KeyboardTheme` / `HandwritingPanelView`），settings 軸 0 import。

### 2.2 `ImeServicePanels.kt` HANDWRITING 分支（line 407–）

| Keyword | HANDWRITING 分支 Before | HANDWRITING 分支 After | 變化 |
|---------|:--------------------:|:--------------------:|:----:|
| `SettingsActivity` | 0 | 0 | 無 |
| `InputBehaviorSection` | 0 | 0 | 無 |
| `CloudSyncManager` | 0 | 0 | 無 |
| `Insight` | 0 | 0 | 無 |
| `AISettings` | 0 | 0 | 無 |
| `AIProviderManager` | 0 | 0 | 無 |
| `SnowRealmAuth` | 0 | 0 | 無 |
| `UserSessionRepository` | 0 | 0 | 無 |
| `prefs.` | 0 | 0 | 無 |
| `YukiPreferences` | 0 | 0 | 無 |
| `handwritingEnabled` | 0 | 0 | 無 |

### 2.3 HANDWRITING 相關 prefs 全清單核對

搜尋 `YukiPreferences.kt` 內所有 handwriting 相關欄位：

| Prefs | 位置 | 消費點 | 本輪狀態 |
|-------|------|--------|---------|
| `toolbarHandwriting`（`toolbar_handwriting`, default true） | `YukiPreferences.kt:179` | `ImeServicePanels.kt:104`（`if (!prefs.toolbarHandwriting) hidden += PanelType.HANDWRITING`，pre-existing panel 顯示開關判斷） | ✅ 未動 |

無其他 `handwriting*` / `stroke*` / `inkColor*` 之類 prefs 欄位存在；本輪 HANDWRITING panel 本身在 settings 端無任何 prefs 消費點（僅 toolbar 顯示開關）。

**核心結論**：本輪 binder 不改變任何 settings / sync / auth / provider / prefs 路徑。callback body（4 個：`onCandidateSelected` / `onRecognized` / `onBackspace` / `onConfirm`）100% 保留於 service 端 lambda，熱路徑（`keyboardView.updateCandidates` / `inputRouter.onHandwritingCommit` / `inputRouter.smartInput.adaptiveDictionary.getFrequency`）呼叫點與語意與 Before 等價——但熱路徑與 settings 軸本就無交集，故與本 side-note 無關。

## 3. 對 0418 Impact Review 預判的核對

`SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` §1.4 對 HANDWRITING 的預判：

| Impact Review §1.4 預判（HANDWRITING） | Sprint 6-5 實作結果 | 核對 |
|------------------------------------|-------------------|------|
| Prefs 直讀：零 | binder 0 `prefs.`；service 端 HANDWRITING 分支 diff 亦 0 | ✅ 一致 |
| Auth：零 | binder 不引用 auth | ✅ 一致 |
| AI provider：零 | binder 不引用 provider | ✅ 一致 |
| Sync：零 | binder 不引用 `CloudSyncManager` | ✅ 一致 |
| Insight：零 | 無 | ✅ 一致 |
| Theme data：只讀 `KeyboardTheme` | binder 僅 `panel.theme = theme` 傳遞 | ✅ 一致 |
| Toolbar 開關：僅 `toolbarHandwriting` | 本輪未碰 `toolbar_*` 路徑 | ✅ 一致 |
| `HandwritingEngine` 生命週期於 service | 仍為 service-level lazy cache，binder 不反向依賴 engine 內部 API | ✅ 一致 |
| settings 端無 `HandwritingSettingsSection` | 維持現狀（settings 端本就無此 section） | ✅ 一致 |
| 風險主在熱路徑（`updateCandidates` / `onHandwritingCommit`）非 settings 軸 | 熱路徑呼叫點未動，與 settings 無交集 | ✅ 一致 |

Impact Review 預判與實作結果 1:1 對齊，無需修正該文件。

## 4. 關鍵邊界確認：callback body 遷移是否改變 writer owner？

本輪 4 個 callback body 實際執行路徑：

| callback | 實際寫入目標 | owner | Before / After 差異 |
|---------|------------|-------|-------------------|
| `onCandidateSelected` | `currentInputConnection.commitText` / `clearCandidatesUi()` / `inputRouter.onHandwritingCommit` | `YukiBoardIMEService` / `InputRouter` | 無 |
| `onRecognized` | `inputRouter.smartInput.adaptiveDictionary.getFrequency`（讀）/ `keyboardView.updateCandidates`（寫） | `InputRouter` / `KeyboardView` | 無 |
| `onBackspace` | `currentInputConnection.deleteSurroundingText(1, 0)` | Android framework | 無 |
| `onConfirm` | `sendDefaultEditorAction(true)` | Android framework | 無 |

**全部 writer 均與 settings / prefs / sync / auth / provider 零交集**；熱路徑語意雖具備高風險，但該風險由 `CodeX B` 跟審（`S6-5-CXB-HANDWRITING-TECH-REVIEW` 檢查 `updateCandidates` 與 `onHandwritingCommit`），不在 settings side-note 盯看範圍。

## 5. 關鍵邊界確認：engine 生命週期是否污染 settings？

| 檢查項 | 狀態 |
|--------|------|
| `handwritingEngine` service-level lazy cache | 本輪未動（仍為 `YukiBoardIMEService` 私有成員） |
| `HandwritingEngine.init()` 呼叫位置 | service 端 lazy 建構時的 `.also { it.init() }`，本輪未動 |
| binder 是否做 engine 建立 / `init()` | ❌ 否 |
| binder 是否呼叫 `ensureReady` | ❌ 否（service 端 `.also { slot = it }` 之後呼叫） |
| engine init 失敗 Toast「手寫引擎初始化失敗」 | 仍在 service 端 HANDWRITING 分支內 |
| model 下載 Toast「手寫模型下載中…」 | 仍由 service 端 `ensureReady { ... }` lambda 處理 |
| engine 狀態是否寫入 prefs / sync / Insight | ❌ 否（engine 不觸碰 settings 層 SharedPreferences） |

engine 生命週期完全於 service / engine 自身層，未污染 settings 軸。

## 6. 對 settings UI / sync payload 的連帶影響評估

| 項目 | 評估結果 |
|------|---------|
| `SettingsActivity` 行數 | 維持 455 行（與 0416 基線一致） |
| 設定頁內 handwriting 相關 section | 不存在（settings 端無 `HandwritingSettingsSection`，本輪亦未新增） |
| `InputBehaviorSection` | 無變動 |
| `toolbar_*` Bool prefs（含 `toolbarHandwriting` 顯示開關） | 無變動 |
| `insightSettingsBackup` 備份 payload | 無變動（未新增／刪除 prefs 欄位） |
| `CloudSyncManager.uploadAll / downloadAll` payload | 無變動 |
| `settingsCoordinator.getApiKey(...)` 路徑 | 無變動 |
| handwriting model 下載 / 更新機制 | 仍由 `HandwritingEngine` 內部自管，未新增 settings UI 觸發點 |

全部維持基線。

## 7. Settings-Side 本輪禁區是否守住？

對照 `SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` §4 自我約束清單：

| 禁區 | 本輪狀態 |
|------|---------|
| `SettingsActivity.kt` 不得新增 section / router / UI | ✅ 未動 |
| `InputBehaviorSection.kt` / `BackgroundSettingsSection.kt` / `ThemeSettingsSection.kt` | ✅ 未動 |
| `AISettingsActivity.kt` / `AISettingsSection.kt` 不得遷移 provider owner | ✅ 未動 |
| `CloudSyncManager.kt` 不得改 payload / gateway | ✅ 未動 |
| `SnowRealmAuthClient` / `UserSessionRepository` 不得改 session writer | ✅ 未動 |
| `toolbar_*` Bool prefs 不得新增或改名 | ✅ 未動（`toolbarHandwriting` 維持基線） |
| `HandwritingEngine.kt` / `KeyboardView.kt` / `InputRouter.kt`（本輪禁區） | ✅ 未動（雖非 settings 禁區，但本輪 gate 指定禁區亦守住） |

全數守住。

## 8. 殘留待觀察事項（僅紀錄，不改）

Claude Code B 報告 §D.2 自揭既存風險：

1. **`ImePanelBindings.clearInputModeBindings()` 未清 `handwritingPanel`**：屬 Sprint 6-5 之外的邊界課題，與 Sprint 6-2 / 6-3 / 6-4 同性質 pre-existing 生命週期課題。settings-side 無關。
2. **`handwritingEngine` 為 service-level lazy cache 跨 panel 開關不 reset**：屬 pre-existing engine ownership 課題。settings-side 無關（engine 不觸 settings）。
3. **`HandwritingEngine.init()` 失敗僅 Log.e + null Toast 無 retry**：屬 pre-existing error-handling 課題。settings-side 無關。
4. **本機無實機 smoke**：手寫筆跡 / 候選顯示 / model 下載進度需實機覆蓋；實機 smoke 是否補交由 `CodeX A` 決定；與 settings 側無關。

## 9. 回交格式

### A. 變更摘要

- 新增 `docs/SPRINT6_5_HANDWRITING_SETTINGS_SIDE_NOTE_2026-04-18.md`
- 未改任何 `.kt`
- 正式結論：**Sprint 6-5 HANDWRITING 實作對 settings 線無影響，無需 settings 變更**

### B. 風險

- 本輪 settings-side 無引入任何風險。
- HANDWRITING 為 settings 低交集 panel（settings 端僅有 `toolbarHandwriting` 顯示開關 1 個 prefs 欄位，本輪未觸），本輪實作仍維持「binder 不碰 settings，callback body 保留於 service 端，engine 生命週期留在 service」的分層。
- 未來若要把手寫模型下載進度 / 失敗策略 / 預設筆畫設定提升為 settings UI 可見項目，會需要新增 `HandwritingSettingsSection` 與 prefs 欄位；屬未排定議題，本輪不動。

### C. 未做的事

- 未對 Claude Code B 的實作做 runtime / 實機 smoke（不屬 settings-side 職責；手寫筆跡 / 候選 / model 下載驗證由 `CodeX A` 決定是否授權補交）。
- 未碰任何 `.kt`、未新增實作、未修改既有 settings 文件或 impact review 本體。
- 未對後續 panel / round 順序做任何排序或暗示（遵守 `feedback_gate_no_self_schedule.md`）。
- 未檢視熱路徑（`updateCandidates` / `onHandwritingCommit`）正確性——此為 `S6-5-CXB-HANDWRITING-TECH-REVIEW` 跟審範圍，非 settings-side 職責。

### D. 下一步建議

- 以下僅為候選方向，**最終順序由 `CodeX A` 指定**，本文件不預排：
  - 候選：最後一個 panel round（`CLAW`，仍 NOT READY，Sprint 6 最後一個 panel）
  - 候選：其他 bugfix round
  - 候選：Sprint 6 收斂 / 總結 audit round
  - 候選：其他 audit / 觀察輪
- Claude Code A 本輪後重回待命位。
- 若 `CodeX A` 指派下一輪為 `CLAW`（Sprint 6 未開門 panel 中 settings / provider 交集最高），我可在實作前提交前置 settings 提醒；具體做法待 `CodeX A` 指派。

### E. 請 `CodeX A` 判定

1. 本輪 side-note 是否符合 `S6-5-CCA-HANDWRITING-SIDE-NOTE` 交付標準？
2. 是否授權 Claude Code A 重回待命位？

---

（本文件僅為 side-note 正式回交憑證，完全未改 `.kt`，不自排後續順序。）

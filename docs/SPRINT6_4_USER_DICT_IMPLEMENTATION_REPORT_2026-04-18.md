# Sprint 6-4 USER_DICT Implementation Report（2026-04-18）

回交人：Claude Code B

任務 ID：`S6-4-CCB-USER_DICT-IMPLEMENTATION`

依據：`Sprint6-All-Round-Gate.md` Round `S6-4 USER_DICT Panel Boundary`

## A. 變更摘要

### A.1 新增檔案

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeUserDictPanelBinder.kt`
  - `object ImeUserDictPanelBinder`
  - 單一方法 `bind(context, theme, userDictionary, onShowDialog, onExportRequest, onImportRequest): UserDictionaryPanelView`
  - 只負責 panel 建構 + 主題 / `UserDictionary` 實例掛接 + 3 個 callback wiring
  - 不做 file I/O、不做 input-mode 切換、不持有 service 強參考
  - `UserDictionary` 由呼叫端建構後注入（binder 不 reach into 字典內部 API）

### A.2 修改檔案

- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
  - 新增 import：`ImeUserDictPanelBinder`
  - `createHighDependencyPanelOrNull()` 的 `PanelType.USER_DICT` 分支：
    - 原本 50 行 inline（`UserDictionaryPanelView(this)` + `UserDictionary(this)` 注入 + 3 個 callback inline lambda + `enterInputMode()`）
    - 收斂為 `ImeUserDictPanelBinder.bind(...)` 具名參數 call + `.also { imePanelBindings.userDictPanel = it }` + 尾端 `p.enterInputMode()`
  - `onImportRequest` 內 `p.importEntries(...)` 由 closure `p` 改走 `imePanelBindings.userDictPanel?.importEntries(...)`（callback lambda 在 bind 階段不可 forward-reference panel 本體；slot 於 `.also` 同時賦值，callback 觸發時 slot 必為當前 panel）

### A.3 未改檔案（遵守禁區）

| 禁區 | 狀況 |
| --- | --- |
| `InputRouter.kt` | ✅ 未動 |
| `YukiBoardIMEService.kt::handleKeyPress()` 主語意 | ✅ 未動 |
| `UserDictionary.kt` | ✅ 未動 |
| `CloudSyncManager.kt` | ✅ 未動 |
| `SettingsActivity.kt` | ✅ 未動 |
| `InputBehaviorSection.kt` | ✅ 未動 |
| `KeyboardView.kt` | ✅ 未動 |
| Sprint 7 檔 | ✅ 未動 |
| `UserDictionaryPanelView.kt` | ✅ 未動（允許但無必要） |
| `ImeServiceMisc.kt` | ✅ 未動 |
| `YukiBoardIMEService.kt`（整檔） | ✅ 未動 |

### A.4 USER_DICT 分支收斂前後對照

**Before（50 行 inline）**

```kotlin
PanelType.USER_DICT -> {
    val p = UserDictionaryPanelView(this).also { imePanelBindings.userDictPanel = it }
    p.theme = theme
    p.userDictionary = UserDictionary(this)
    p.onShowDialog = { dialog -> showImeDialog(dialog) }
    p.onExportRequest = { data -> ... (24 行 file/share Intent + catch Toast) ... }
    p.onImportRequest = { ... (18 行 file read + importEntries + Toast) ... }
    p.enterInputMode()
    p
}
```

**After（binder call + service-side `enterInputMode()`）**

```kotlin
PanelType.USER_DICT -> {
    val p = ImeUserDictPanelBinder.bind(
        context = this,
        theme = theme,
        userDictionary = UserDictionary(this),
        onShowDialog = { dialog -> showImeDialog(dialog) },
        onExportRequest = { data -> ... (同 before) ... },
        onImportRequest = {
            ...
            val count = imePanelBindings.userDictPanel?.importEntries(file.readText()) ?: 0
            ...
        },
    ).also { imePanelBindings.userDictPanel = it }
    p.enterInputMode()
    p
}
```

兩個語義變更點（均為實作細節，對外行為等價）：

1. `importEntries` 呼叫路徑：原本走 closure 強參考 `p`，現在走 `imePanelBindings.userDictPanel?` slot，失敗時以 `?: 0` fallback。callback 僅於 panel detach 前的 UI 觸控觸發，slot 必為當前 panel。
2. `enterInputMode()` 仍由 service 端控制（保留於 binder return 之外），維持與 THEME binder 同樣的「side-effect call 留在 service」原則。

## B. Code-path Smoke Checklist

本機無 UI 實機測試；以 code-path 比對取代。

### B.1 panel 開啟

| 預期行為 | Before 路徑 | After 路徑 | 狀態 |
| --- | --- | --- | --- |
| `UserDictionaryPanelView` 建構 | `UserDictionaryPanelView(this)` | `ImeUserDictPanelBinder.bind(context = this, ...)` 內 `UserDictionaryPanelView(context)` | ✅ 等價 |
| `imePanelBindings.userDictPanel` slot 賦值 | `.also { imePanelBindings.userDictPanel = it }` | 相同位置 `.also { imePanelBindings.userDictPanel = it }`（於 binder return 之後緊接掛接） | ✅ 等價 |
| `theme` 掛接 | `p.theme = theme` | binder 內 `panel.theme = theme` | ✅ 等價 |
| `UserDictionary` 注入 | `p.userDictionary = UserDictionary(this)` | binder 接收已建構實例並寫回 `panel.userDictionary`；service 端保留 `UserDictionary(this)` 建構呼叫 | ✅ 等價（`userDictionary` setter 觸發 `reloadEntries()`，行為不變） |
| 3 個 callback 掛接 | 直接賦值 | binder 於建構後逐項賦值 | ✅ 等價 |

### B.2 `onShowDialog` callback

| 檢查項 | Before | After | 狀態 |
| --- | --- | --- | --- |
| lambda body | `{ dialog -> showImeDialog(dialog) }` | 同 | ✅ 等價 |
| 觸發者 | panel 內部 delete 確認 dialog / add / edit 互動 | 未動 | ✅ 等價 |

### B.3 `onExportRequest` callback（export 路徑）

| 檢查項 | Before | After | 狀態 |
| --- | --- | --- | --- |
| `file.writeText(data)` | ✅ | 相同 lambda body 傳入 binder | ✅ 等價 |
| `FileProvider.getUriForFile(this, "$packageName.fileprovider", file)` | ✅ | 同 | ✅ 等價 |
| `ACTION_SEND` + MIME `text/plain` + `EXTRA_STREAM` + `EXTRA_SUBJECT` | ✅ | 同 | ✅ 等價 |
| `FLAG_GRANT_READ_URI_PERMISSION` + `FLAG_ACTIVITY_NEW_TASK` | ✅ | 同 | ✅ 等價 |
| `createChooser` + `FLAG_ACTIVITY_NEW_TASK` | ✅ | 同 | ✅ 等價 |
| exception Toast on main thread | ✅ | 同 | ✅ 等價 |

### B.4 `onImportRequest` callback（import 路徑）

| 檢查項 | Before | After | 狀態 |
| --- | --- | --- | --- |
| `File(getExternalFilesDir(null), "yukiboard_user_dict.txt")` | ✅ | 同 | ✅ 等價 |
| `file.exists()` 分支 | ✅ | 同 | ✅ 等價 |
| `importEntries(file.readText())` 呼叫 | `p.importEntries(...)` 走 closure | `imePanelBindings.userDictPanel?.importEntries(...) ?: 0` 走 slot | ✅ 對外等價（callback 只在 panel 活著時被觸發 → slot 必為當前 panel） |
| 成功 Toast：「已匯入 N 筆詞彙」 | ✅ | 同 | ✅ 等價 |
| 不存在 Toast：「找不到匯入檔案：...」 | ✅ | 同 | ✅ 等價 |
| exception Toast | ✅ | 同 | ✅ 等價 |

### B.5 input mode enter 路徑

| 檢查項 | Before | After | 狀態 |
| --- | --- | --- | --- |
| `p.enterInputMode()` 呼叫時機 | panel 建構 + callback 全部掛接完成後 | 同 — `.also { slot = it }` 之後、return 之前 | ✅ 等價 |
| `enterInputMode` 內部行為（`isInputMode = true` / `inputBuffer.clear()` / `composingHint = ""` / `invalidate()`）| 未動 | 未動 | ✅ 等價 |
| binder 是否自行呼叫 `enterInputMode()` | ❌ 否（刻意留在 service 端） | ❌ 否 | ✅ 邊界清晰 |

### B.6 panel 關閉後不新增新的熱路徑 writer

| 檢查項 | 狀況 |
| --- | --- |
| Binder 內部是否 `addListener` / `BroadcastReceiver` | ❌ 無 |
| Binder 內部是否呼叫 `UserDictionary.add / delete / save` 等內部 API | ❌ 無（只接受外部注入的 instance，不 reach into） |
| Binder 是否持有 `YukiBoardIMEService` 強參考 | ❌ 無（只接受 `Context` + `UserDictionary` + 3 個封裝 lambdas） |
| 新增的 writer 路徑數量（對 prefs / sync / `CloudSyncManager` / `InputRouter`）| 0 |
| 新增的 listener 路徑數量 | 0 |
| `enterInputMode()` writer owner | 仍在 service 端 | ✅ 未擴散 |
| `importEntries` 寫入 writer owner | 仍由 panel 內部 delegate 至 `UserDictionary`（未動）| ✅ 未擴散 |
| panel detach 後 slot 殘留狀況 | 與 pre-existing `ImePanelBindings.clearInputModeBindings()` 行為一致，本輪不改 | ✅ 未擴大既存課題 |

## C. 邊界純度自評

### C.1 binder → 外部依賴

| binder 依賴 | 類型 |
| --- | --- |
| `android.app.AlertDialog` | framework |
| `android.content.Context` | framework |
| `com.snowrealm.yukiboard.input.dictionary.UserDictionary` | 實例注入（非 reach into） |
| `com.snowrealm.yukiboard.theme.KeyboardTheme` | 資料類 |
| `com.snowrealm.yukiboard.ui.panel.UserDictionaryPanelView` | panel view |

**未引入**：`CloudSyncManager` / `InputRouter` / `SettingsActivity` / `InputBehaviorSection` / `YukiBoardIMEService` / `YukiPreferences` / `FileProvider`（binder 不做 file I/O）。

### C.2 未擴張功能面

- 未加新 state field
- 未加新 listener 註冊 / 註銷
- 未改變 `UserDictionary` 寫入路徑
- 未改變 export / import file 位置、MIME、Intent flags
- 未改 `ImePanelBindings` 結構（`userDictPanel` slot 原本即已存在）
- 未改 `enterInputMode` / `exitInputMode` 主幹
- 未改 panel 開關主幹（`handlePanelToggle / routeToggle / attachPanel`）
- 未碰 sync 路徑（`CloudSyncManager.syncUserDict` 未被引入）
- 未碰 settings 交集點（`SettingsActivity:190/402` 的 `UserDictionary(this)` 建構仍在 settings 端，與 service 端互不影響）

## D. 風險

### D.1 本輪引入的風險

無新行為風險。binder 僅 panel 建構 / 初始狀態 / 3 個 callback wiring 的重構，所有外部可觀察行為等價。

一個實作細節差異（B.4 已分析）：`importEntries` 呼叫由 closure `p` 改走 `imePanelBindings.userDictPanel?` slot。panel 存活期間必等價；panel detach 後 touch 不抵達 → callback 不觸發 → 無空指 null 風險。

compile 通過：`:app:compileDebugKotlin` BUILD SUCCESSFUL in 2s。

### D.2 既存風險（本輪不動）

1. `ImePanelBindings.clearInputModeBindings()` 是否清 `userDictPanel`，本輪未檢視 / 未調整（與 Sprint 6-2 / 6-3 同性質 pre-existing 生命週期課題）。
2. `SettingsActivity` 另外持有 `UserDictionary(this)` 實例（兩個實例讀寫同一 SharedPreferences），本輪未統一；屬 shared data 邊界課題，非本輪 scope。
3. `CloudSyncManager.syncUserDict` 路徑與 panel export/import 的一致性未觸，本輪不動。
4. 本機無實機；完整 smoke 僅能由實機覆蓋（CRUD、export/import Intent、搜尋、分類切換、input mode 接收鍵盤輸入）。code-path 比對已逐項完成。

## E. 請 CodeX A 判定

### E.1 交付要求逐項自評

| 要求 | 狀態 |
| --- | --- |
| 1. USER_DICT panel 建立邊界更清楚 | ✅ 50 行 inline → binder call（含 3 具名 callback）+ service-side `enterInputMode()` / bindings slot 賦值 |
| 2. service 端 USER_DICT 建立邏輯最小收斂 | ✅ `UserDictionary(this)` 建構 / `enterInputMode()` 生命週期 / `userDictPanel` bindings 賦值刻意留在 service 端 |
| 3. `:app:compileDebugKotlin` 通過 | ✅ BUILD SUCCESSFUL in 2s |
| 4. 回交附 smoke 結果或無法執行原因 | ✅ 本機無實機 UI，改以 code-path smoke checklist 取代（§B） |
| 5. code-path smoke 含 panel 開啟 / export / import / input mode enter / 關閉不新增熱路徑 writer | ✅ §B.1–B.6 |
| 6. 不擴成 `UserDictionary` / sync / settings 字典管理總重構 | ✅ 禁區逐項未動（§A.3） |
| 7. 不自排下一輪 | ✅ 本文件無順序建議 |

### E.2 待 CodeX A 裁決項

1. **判定結果（擇一）**
   - `READY` → Sprint 6-4 視為完成
   - `NOT READY` → 請指出補強項
   - `LOCKED` → 請指出哪條底線被越線

2. **是否需要實機 smoke 補交**
   - 本機無實機，若需 CRUD / export / import / 分類搜尋 / input mode 接收鍵盤輸入等實機驗證，需 luffysky 或 CodeX B 以實機執行
   - 是否要求此輪補實機 smoke，請 `CodeX A` 指定

3. **下一步指派**（候選方向，僅供 `CodeX A` 參考，待 `CodeX A` 指定）
   - 未來 panel round：`CLAW` / `HANDWRITING`（仍 NOT READY）
   - 其他 bugfix / audit round
   - 其他方向

以上。等 `CodeX A` 指示。

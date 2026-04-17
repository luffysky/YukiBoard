# Sprint 6-5 HANDWRITING Implementation Report（2026-04-18）

回交人：Claude Code B

任務 ID：`S6-5-CCB-HANDWRITING-IMPLEMENTATION`

依據：`Sprint6-All-Round-Gate.md` Round `S6-5 HANDWRITING Panel Boundary`

## A. 變更摘要

### A.1 新增檔案

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeHandwritingPanelBinder.kt`
  - `object ImeHandwritingPanelBinder`
  - 單一方法 `bind(context, engine, theme, onCandidateSelected, onRecognized, onBackspace, onConfirm): HandwritingPanelView`
  - 只負責 panel 建構 + 主題掛接 + 4 個 callback wiring
  - **不做**：engine 建立 / `init()` / lazy cache、`ensureReady()` 呼叫、candidate pipeline 操作、`KeyboardView.updateCandidates` / `InputRouter.onHandwritingCommit` 的語意變更
  - 不反向依賴 `HandwritingEngine` 內部 API（只接受已建構且完成 `init()` 的實例）

### A.2 修改檔案

- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
  - 新增 import：`ImeHandwritingPanelBinder`
  - `createHighDependencyPanelOrNull()` 的 `PanelType.HANDWRITING` 分支：
    - 原本 46 行 inline（engine lazy init + null Toast + `HandwritingPanelView(this, engine)` + `theme` 掛接 + 4 個 callback inline lambda + `ensureReady`）
    - 收斂為：engine lazy init / null Toast 分支保留（生命週期仍在 service 端）→ `ImeHandwritingPanelBinder.bind(...)` 具名參數 call + `.also { imePanelBindings.handwritingPanel = it }` → `p.ensureReady { ... }` 留在 service 端

### A.3 未改檔案（遵守禁區）

| 禁區 | 狀況 |
| --- | --- |
| `KeyboardView.kt` | ✅ 未動（`updateCandidates` 呼叫位置不變、語意不變） |
| `InputRouter.kt` | ✅ 未動（`onHandwritingCommit` / `smartInput.adaptiveDictionary.getFrequency` 呼叫位置不變） |
| `handleKeyPress()` 主語意 | ✅ 未動 |
| `applyInputResult()` 主語意 | ✅ 未動 |
| `HandwritingEngine.kt` | ✅ 未動 |
| `ImeServiceCallbacks.kt` | ✅ 未動 |
| `InputUiSync.kt` | ✅ 未動 |
| settings 檔 | ✅ 未動 |
| Sprint 7 檔 | ✅ 未動 |
| `HandwritingPanelView.kt` | ✅ 未動（允許但無必要） |
| `YukiBoardIMEService.kt`（整檔） | ✅ 未動 |

### A.4 HANDWRITING 分支收斂前後對照

**Before（46 行 inline）**

```kotlin
PanelType.HANDWRITING -> {
    if (handwritingEngine == null) {
        try {
            handwritingEngine = HandwritingEngine(this).also { it.init() }
        } catch (e: Exception) {
            Log.e("YukiIME", "HandwritingEngine init failed", e)
        }
    }
    val engine = handwritingEngine
    if (engine == null) {
        Toast.makeText(this, "手寫引擎初始化失敗", ...).show()
        null
    } else {
        val p = HandwritingPanelView(this, engine).also { imePanelBindings.handwritingPanel = it }
        p.theme = theme
        p.onCandidateSelected = { text -> ... 3 行 ... }
        p.onRecognized = { rawCandidates -> ... 9 行 ... }
        p.onBackspace = { currentInputConnection?.deleteSurroundingText(1, 0) }
        p.onConfirm = { sendDefaultEditorAction(true) }
        p.ensureReady { ready -> if (!ready) Toast.makeText(..., "手寫模型下載中…", ...).show() }
        p
    }
}
```

**After（engine 生命週期保留 + binder call + service-side `ensureReady`）**

```kotlin
PanelType.HANDWRITING -> {
    if (handwritingEngine == null) {
        try {
            handwritingEngine = HandwritingEngine(this).also { it.init() }
        } catch (e: Exception) {
            Log.e("YukiIME", "HandwritingEngine init failed", e)
        }
    }
    val engine = handwritingEngine
    if (engine == null) {
        Toast.makeText(this, "手寫引擎初始化失敗", ...).show()
        null
    } else {
        val p = ImeHandwritingPanelBinder.bind(
            context = this,
            engine = engine,
            theme = theme,
            onCandidateSelected = { text -> ... (same body) },
            onRecognized = { rawCandidates -> ... (same body) },
            onBackspace = { ... (same body) },
            onConfirm = { ... (same body) },
        ).also { imePanelBindings.handwritingPanel = it }
        p.ensureReady { ready -> if (!ready) Toast.makeText(..., "手寫模型下載中…", ...).show() }
        p
    }
}
```

語義變更點：0 個。所有原本的副作用語句 body 1:1 搬入 binder 的 `bind(...)` 具名參數 lambda，engine 生命週期與 `ensureReady` 全保留在 service 端。

## B. Code-path Smoke Checklist

本機無 UI 實機測試；以 code-path 比對取代。

### B.1 panel 開啟

| 預期行為 | Before 路徑 | After 路徑 | 狀態 |
| --- | --- | --- | --- |
| `HandwritingPanelView` 建構 | `HandwritingPanelView(this, engine)` | `ImeHandwritingPanelBinder.bind(context = this, engine = engine, ...)` 內 `HandwritingPanelView(context, engine)` | ✅ 等價 |
| `imePanelBindings.handwritingPanel` slot 賦值 | `.also { imePanelBindings.handwritingPanel = it }` | 相同位置 | ✅ 等價 |
| `theme` 掛接 | `p.theme = theme` | binder 內 `panel.theme = theme` | ✅ 等價 |
| 4 callback 掛接 | 直接賦值 | binder 於建構後逐項賦值 | ✅ 等價 |
| `ensureReady` 呼叫時機 | panel 建構後 | 同 — `.also { slot = it }` 之後、return 之前 | ✅ 等價 |

### B.2 engine init 路徑

| 檢查項 | Before | After | 狀態 |
| --- | --- | --- | --- |
| `handwritingEngine == null` → lazy 建構 | `handwritingEngine = HandwritingEngine(this).also { it.init() }` | 未動 | ✅ 留在 service 端 |
| engine init exception catch + Log.e 訊息「HandwritingEngine init failed」 | 未動 | 未動 | ✅ 等價 |
| engine 取出仍為 null → Toast「手寫引擎初始化失敗」+ return null | 未動 | 未動 | ✅ 等價 |
| binder 是否做 engine lazy 建構 | ❌ 否 | ❌ 否（仍在 service 端） | ✅ 邊界清晰 |

### B.3 `onCandidateSelected` callback

| 檢查項 | Before | After | 狀態 |
| --- | --- | --- | --- |
| `currentInputConnection?.commitText(text, 1)` | ✅ | lambda body 原樣傳入 binder | ✅ 等價 |
| `clearCandidatesUi()` | ✅ | 同 | ✅ 等價 |
| `inputRouter.onHandwritingCommit(text)` | ✅ | 同 | ✅ 等價（`InputRouter` 未動） |
| 呼叫順序（commit → clear → router） | ✅ | 同 | ✅ 等價 |

### B.4 `onRecognized` callback

| 檢查項 | Before | After | 狀態 |
| --- | --- | --- | --- |
| `rawCandidates.isNotEmpty()` 前置判斷 | ✅ | 同 | ✅ 等價 |
| `sortedByDescending { inputRouter.smartInput.adaptiveDictionary.getFrequency(word) }` | ✅ | 同 | ✅ 等價（`InputRouter` 讀取） |
| `boosted.associateWith { CandidateSource.DICTIONARY }` | ✅ | 同 | ✅ 等價 |
| `keyboardView.updateCandidates(boosted, sourceMap)` | ✅ | 同 | ✅ 等價（`KeyboardView.updateCandidates` 語意未改） |
| binder 是否直接呼叫 `updateCandidates` / `getFrequency` | ❌ 否（由 service lambda 持有並執行） | ❌ 否 | ✅ 熱路徑語意不擴散 |

### B.5 `onBackspace` / `onConfirm` callback

| 檢查項 | Before | After | 狀態 |
| --- | --- | --- | --- |
| `onBackspace` body：`currentInputConnection?.deleteSurroundingText(1, 0)` | ✅ | 同 | ✅ 等價 |
| `onConfirm` body：`sendDefaultEditorAction(true)` | ✅ | 同 | ✅ 等價 |

### B.6 panel 關閉後不新增新的熱路徑 writer

| 檢查項 | 狀況 |
| --- | --- |
| Binder 內部是否 `addListener` / 註冊 recognizer callback | ❌ 無 |
| Binder 內部是否呼叫 `HandwritingEngine.init / checkAllModelStatus / download` 等 API | ❌ 無（只接受已 init 實例） |
| Binder 內部是否直接呼叫 `KeyboardView.updateCandidates` 或 `InputRouter.onHandwritingCommit` | ❌ 無 |
| Binder 是否持有 `YukiBoardIMEService` 強參考 | ❌ 無（只接受 `Context` + `HandwritingEngine` + 4 封裝 lambdas） |
| 新增的 writer 路徑數量（對 `keyboardView` / `inputRouter` / `currentInputConnection`）| 0（所有 writer 仍走 service lambda body） |
| 新增的 listener 路徑數量 | 0 |
| `ensureReady` writer owner | 仍在 service 端（panel 內部 `checkAllModelStatus` init block 未動） | ✅ 未擴散 |

## C. 邊界純度自評

### C.1 binder → 外部依賴

| binder 依賴 | 類型 |
| --- | --- |
| `android.content.Context` | framework |
| `com.snowrealm.yukiboard.input.handwriting.HandwritingEngine` | 實例注入（非 reach into） |
| `com.snowrealm.yukiboard.theme.KeyboardTheme` | 資料類 |
| `com.snowrealm.yukiboard.ui.panel.HandwritingPanelView` | panel view |

**未引入**：`KeyboardView` / `InputRouter` / `CandidateSource` / `ImeServiceCallbacks` / `InputUiSync` / `YukiBoardIMEService` / `YukiPreferences`。

### C.2 未擴張功能面

- 未加新 state field
- 未加新 listener 註冊 / 註銷
- 未改變 engine 建立 / init 路徑
- 未改變 candidate pipeline（`updateCandidates` 呼叫點與語意）
- 未改變 `InputRouter.onHandwritingCommit` 呼叫點
- 未改 `ImePanelBindings` 結構（`handwritingPanel` slot 原本即已存在）
- 未改 panel 開關主幹（`handlePanelToggle / routeToggle / attachPanel`）
- 未碰 model 下載 / 訓練路徑

## D. 風險

### D.1 本輪引入的風險

無新行為風險。binder 僅 panel 建構 + 初始主題 + 4 個 callback wiring 的純薄切重構，engine 生命週期與 `ensureReady` 均留在 service 端，所有外部可觀察行為等價。

compile 通過：`:app:compileDebugKotlin` BUILD SUCCESSFUL in 1s。

### D.2 既存風險（本輪不動）

1. `ImePanelBindings.clearInputModeBindings()` 是否清 `handwritingPanel`，本輪未檢視（與 Sprint 6-2 / 6-3 / 6-4 同性質 pre-existing 生命週期課題）。
2. `handwritingEngine` 為 service-level lazy cache，跨 panel 開關不 reset；屬 pre-existing engine ownership 課題，本輪未動。
3. `HandwritingEngine.init()` 失敗僅 Log.e + null Toast，無 retry 策略；屬 pre-existing error-handling 課題，本輪不擴大。
4. 本機無實機；完整 smoke 僅能由實機覆蓋（手寫筆跡輸入、候選顯示、model 下載進度）。code-path 比對已逐項完成。

## E. 請 CodeX A 判定

### E.1 交付要求逐項自評

| 要求 | 狀態 |
| --- | --- |
| 1. HANDWRITING panel 建立邊界更清楚 | ✅ 46 行 inline → binder call（含 4 具名 callback）+ service-side engine lifecycle / `ensureReady` / bindings slot 賦值 |
| 2. service 端 HANDWRITING 建立邏輯最小收斂 | ✅ engine lazy init / null Toast 分支 / `ensureReady` / `handwritingPanel` bindings 賦值刻意留在 service 端 |
| 3. `:app:compileDebugKotlin` 通過 | ✅ BUILD SUCCESSFUL in 1s |
| 4. 回交附 smoke 結果或無法執行原因 | ✅ 本機無實機 UI，改以 code-path smoke checklist 取代（§B） |
| 5. code-path smoke 含 panel 開啟 / engine init / onCandidateSelected / onRecognized / onBackspace / onConfirm / 關閉不新增熱路徑 writer | ✅ §B.1–B.6 |
| 6. 不擴成 `HandwritingEngine` / candidate pipeline / `InputRouter` 熱路徑重構 | ✅ 禁區逐項未動（§A.3） |
| 7. 不自排下一輪 | ✅ 本文件無順序建議 |

### E.2 待 CodeX A 裁決項

1. **判定結果（擇一）**
   - `READY` → Sprint 6-5 視為完成
   - `NOT READY` → 請指出補強項
   - `LOCKED` → 請指出哪條底線被越線

2. **是否需要實機 smoke 補交**
   - 本機無實機，若需手寫筆跡 / 候選選擇 / model 下載進度 / backspace / confirm 等實機驗證，需 luffysky 或 CodeX B 以實機執行
   - 是否要求此輪補實機 smoke，請 `CodeX A` 指定

3. **下一步指派**（候選方向，僅供 `CodeX A` 參考，待 `CodeX A` 指定）
   - 未來 panel round：`CLAW`（仍 NOT READY，Sprint 6 最後一個 panel）
   - 其他 bugfix / audit round
   - 其他方向

以上。等 `CodeX A` 指示。

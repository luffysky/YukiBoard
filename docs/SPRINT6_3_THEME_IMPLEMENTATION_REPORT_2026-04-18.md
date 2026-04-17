# Sprint 6-3 THEME Implementation Report（2026-04-18）

回交人：Claude Code B

任務 ID：`S6-3-CCB-THEME-IMPLEMENTATION`

依據：`Sprint6-All-Round-Gate.md` Round `S6-3 THEME Panel Boundary`

## A. 變更摘要

### A.1 新增檔案

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeThemePanelBinder.kt`
  - `object ImeThemePanelBinder`
  - 單一方法 `bind(context, theme, currentThemeId, availableThemes, onThemeSelected, onAnimThemeSelected, onPickBackground, onPickVideo, onClearBackground, onOverlayAlphaChanged, onLabelColorChanged, onBackgroundSettingsChanged, onKeySettingsChanged, onPerKeySizeChanged, onKeyboardHeightChanged): ThemePanelView`
  - 只負責 panel 建構 + 3 個初始狀態 snapshot + 11 個 callback wiring
  - 不反向依賴 `ThemeManager` / `KeyboardBackgroundView` / `BackgroundPickerActivity`（Sprint 6-3 禁區）
  - 不做 listener 註冊、不呼叫 `reloadPreviewBitmap()`（保留在 service 端）

### A.2 修改檔案

- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
  - 新增 import：`ImeThemePanelBinder`
  - `createHighDependencyPanelOrNull()` 的 `PanelType.THEME` 分支：
    - 原本 94 行 inline（`ThemeManager.getInstance` + `ThemePanelView(this)` + 4 個 state 賦值 + 11 個 callback inline lambda + `reloadPreviewBitmap()` + listener 掛接）
    - 收斂為 `ImeThemePanelBinder.bind(...)` 具名參數 call（11 個 callback 以 lambda 形式具名傳入），`.also { imePanelBindings.themePanel = it }` 保留，`reloadPreviewBitmap()` / `addOnThemeChangedListener` / `addOnDetachListener` 留在 service 端
  - Bindings slot 賦值刻意留在 service 端，維持與 Sprint 6-1 / 6-2 同樣的 ownership 原則
  - `previewBgBitmap = null` 原本透過 closure 捕獲的 `p` 引用，改走 `imePanelBindings.themePanel?.previewBgBitmap = null`，避免 callback lambda 在 bind 階段 forward-reference `p` 本身（binder 簽章不接 panel 自我引用）

### A.3 未改檔案（遵守禁區）

| 禁區 | 狀況 |
| --- | --- |
| `KeyboardView.kt` | ✅ 未動 |
| `KeyboardBackgroundView.kt` | ✅ 未動 |
| `YukiBoardIMEService.kt::handleKeyPress()` 主語意 | ✅ 未動 |
| `InputRouter.kt` | ✅ 未動 |
| `ThemeManager.kt` | ✅ 未動 |
| `ImeThemeRenderDelegate.kt` | ✅ 未動 |
| `BackgroundPickerActivity.kt` | ✅ 未動 |
| settings 檔 | ✅ 未動 |
| Sprint 7 檔 | ✅ 未動 |
| `ThemePanelView.kt` | ✅ 未動（允許但無必要） |
| `ImeServiceMisc.kt` | ✅ 未動 |
| `YukiBoardIMEService.kt`（整檔） | ✅ 未動 |

### A.4 THEME 分支收斂前後對照

**Before（94 行 inline）**

```kotlin
PanelType.THEME -> {
    val tm = ThemeManager.getInstance(this)
    val p = ThemePanelView(this).also { imePanelBindings.themePanel = it }
    p.theme = theme
    p.currentThemeId = tm.currentTheme.id
    p.availableThemes = tm.availableThemes
    p.reloadPreviewBitmap()
    p.onThemeSelected = { selected ->
        tm.currentTheme = selected
        prefs.useCustomBackground = false
        applyTheme(selected)
        p.previewBgBitmap = null
    }
    p.onAnimThemeSelected = { styleId -> ... }
    val themeListener: ... = { newTheme -> ... }
    tm.addOnThemeChangedListener(themeListener)
    p.addOnDetachListener { tm.removeOnThemeChangedListener(themeListener) }
    p.onPickBackground = { ... }
    p.onPickVideo = { ... }
    p.onClearBackground = { ... }
    p.onOverlayAlphaChanged = { alpha -> ... }
    p.onLabelColorChanged = { color -> ... }
    p.onBackgroundSettingsChanged = { ... }
    p.onKeySettingsChanged = { ... }
    p.onPerKeySizeChanged = { ... }
    p.onKeyboardHeightChanged = { ... }
    p
}
```

**After（binder call + service-side lifecycle）**

```kotlin
PanelType.THEME -> {
    val tm = ThemeManager.getInstance(this)
    val p = ImeThemePanelBinder.bind(
        context = this,
        theme = theme,
        currentThemeId = tm.currentTheme.id,
        availableThemes = tm.availableThemes,
        onThemeSelected = { ... (same body) },
        onAnimThemeSelected = { ... },
        onPickBackground = { ... },
        onPickVideo = { ... },
        onClearBackground = { ... },
        onOverlayAlphaChanged = { ... },
        onLabelColorChanged = { ... },
        onBackgroundSettingsChanged = { ... },
        onKeySettingsChanged = { ... },
        onPerKeySizeChanged = { ... },
        onKeyboardHeightChanged = { ... },
    ).also { imePanelBindings.themePanel = it }
    p.reloadPreviewBitmap()
    val themeListener: ... = { newTheme -> ... }
    tm.addOnThemeChangedListener(themeListener)
    p.addOnDetachListener { tm.removeOnThemeChangedListener(themeListener) }
    p
}
```

兩個語義變更點（僅內部改寫，對外行為等價）：

1. `p.previewBgBitmap = null` 於 `onThemeSelected` / `onClearBackground` 兩處，改走 `imePanelBindings.themePanel?.previewBgBitmap = null`。reason：binder 建構階段 callback lambda 不可自我 forward-reference panel 本體；改由 `imePanelBindings.themePanel` slot 讀回（此 slot 在 `.also` 建立同一時機賦值，callback 被觸發時 slot 必為當前 panel）。
2. `reloadPreviewBitmap()` 從 binder 內移到 service 端 `.also` 之後呼叫。reason：binder 不做任何 view-method side effect call，只做 state snapshot + callback wiring，保持最小邊界。

## B. Code-path Smoke Checklist

本機無 UI 實機測試；改以 code-path 比對取代。

### B.1 panel 開啟

| 預期行為 | Before 路徑 | After 路徑 | 狀態 |
| --- | --- | --- | --- |
| `ThemePanelView` 建構 | `ThemePanelView(this)` | `ImeThemePanelBinder.bind(context = this, ...)` 內 `ThemePanelView(context)` | ✅ 等價 |
| `imePanelBindings.themePanel` slot 賦值 | `.also { imePanelBindings.themePanel = it }` | `.also { imePanelBindings.themePanel = it }` | ✅ 等價 |
| `theme` / `currentThemeId` / `availableThemes` 初始 snapshot | `p.theme = ... / p.currentThemeId = tm.currentTheme.id / p.availableThemes = tm.availableThemes` | binder 接收相同表達式並於內部以同樣順序寫回 panel | ✅ 等價 |
| `reloadPreviewBitmap()` 初始呼叫 | `p.reloadPreviewBitmap()`（inline 第 4 行） | `.also { imePanelBindings.themePanel = it }` 後立即 `p.reloadPreviewBitmap()` | ✅ 等價 |
| `ThemeManager.addOnThemeChangedListener(themeListener)` | 直接呼叫 | 相同位置 service 端直接呼叫 | ✅ 等價 |
| `panel.addOnDetachListener { tm.removeOnThemeChangedListener(...) }` | 直接掛接 | 相同位置 service 端直接掛接 | ✅ 等價 |

### B.2 11 個 callback 路徑

| callback | Before inline 內容 | After 傳入 binder 的 lambda | 語義 |
| --- | --- | --- | --- |
| `onThemeSelected` | `tm.currentTheme = selected; prefs.useCustomBackground = false; applyTheme(selected); p.previewBgBitmap = null` | 同樣 4 步，最後一步改為 `imePanelBindings.themePanel?.previewBgBitmap = null` | ✅ 對外等價 |
| `onAnimThemeSelected` | `toolbarView.setAnimStyle(styleId); keyboardView.setAnimStyle(styleId)` | 同 | ✅ 等價 |
| `onPickBackground` | 建 `Intent(this, BackgroundPickerActivity)` with `EXTRA_PICK_TYPE = "image"` + `FLAG_ACTIVITY_NEW_TASK` + `startActivity` | 同 | ✅ 等價 |
| `onPickVideo` | 同上，`EXTRA_PICK_TYPE = "video"` | 同 | ✅ 等價 |
| `onClearBackground` | 清 dir + `prefs.useCustomBackground = false` + `backgroundView?.reloadFromStorage()` + `keyboardView.hasCustomBackground = false` + `keyboardView.invalidate()` + `p.previewBgBitmap = null` | 同 6 步，最後一步改走 bindings slot | ✅ 對外等價 |
| `onOverlayAlphaChanged` | `backgroundView?.overlayAlpha = alpha; keyboardView.bgOverlayAlpha = alpha; keyboardView.invalidate()` | 同 | ✅ 等價 |
| `onLabelColorChanged` | `keyboardView.customKeyLabelColor = color; keyboardView.invalidate()` | 同 | ✅ 等價 |
| `onBackgroundSettingsChanged` | `backgroundView?.let { ... 4 prefs assign + reloadFromStorage }; keyboardView.invalidate(); sendBroadcast(ACTION_BG_UPDATED)` | 同 | ✅ 等價 |
| `onKeySettingsChanged` | `keyScalePercent / keyOpacity / keyTextOpacity assign + applyTransparentMode + reloadFromStorage + glassKeysNow 計算 + hasCustomBackground assign + invalidate` | 同 | ✅ 等價 |
| `onPerKeySizeChanged` | `keyboardView.perKeySizes = prefs.getAllPerKeySizes(); keyboardView.invalidate()` | 同 | ✅ 等價 |
| `onKeyboardHeightChanged` | `closePanel(); backgroundView?.destroy(); backgroundView = null; setInputView(onCreateInputView())` | 同 | ✅ 等價 |

### B.3 ThemeManager listener 生命週期

| 檢查項 | 路徑 | 狀態 |
| --- | --- | --- |
| `tm.addOnThemeChangedListener(themeListener)` 呼叫點 | service 端 `.also { bindings.themePanel = it }` 與 `reloadPreviewBitmap()` 後緊接呼叫 | ✅ 仍在 service 端 |
| `themeListener` 內容（`p.currentThemeId = newTheme.id; p.theme = newTheme; p.invalidate()`）| 未動 | ✅ 未動 |
| `p.addOnDetachListener { tm.removeOnThemeChangedListener(themeListener) }` | 未動，掛接點同 | ✅ 未動 |
| 是否在 binder 端註冊 listener | ❌ 否，binder 內無任何 listener 註冊 | ✅ 邊界清晰 |

### B.4 panel 關閉後不殘留新 writer / listener

| 檢查項 | 狀況 |
| --- | --- |
| Binder 內部是否 `addOnThemeChangedListener` / `addOnDetachListener` | ❌ 無 |
| Binder 內部是否註冊 `SharedPreferences` / `BroadcastReceiver` | ❌ 無 |
| Binder 是否持有 `YukiBoardIMEService` 強參考 | ❌ 無（只接受 `Context` + 已封裝的 lambdas） |
| 新增的 writer 路徑數量（對 `keyboardView` / `backgroundView` / `prefs` / `toolbarView`） | 0（所有 writer 仍走 service 端 lambda body） |
| 新增的 listener 路徑數量 | 0 |
| ThemeManager listener 註銷路徑 | 仍是 `addOnDetachListener { removeOnThemeChangedListener }` 單一出口，未動 |

### B.5 `previewBgBitmap = null` 改寫走向驗證

| 觸發點 | Before | After | 一致性 |
| --- | --- | --- | --- |
| `onThemeSelected` 末段 | `p.previewBgBitmap = null`（`p` 為當前 panel 強引用） | `imePanelBindings.themePanel?.previewBgBitmap = null`（panel 啟用期間 slot 即為該 panel） | ✅ 當 panel 活著時等價；panel 關閉後 slot 被 `clearInputModeBindings`（若涵蓋）或重新 open 時覆寫，此兩 callback 僅於 panel 存活期間觸發 |
| `onClearBackground` 末段 | 同上 | 同上 | ✅ 同上 |

> 註：`onThemeSelected` / `onClearBackground` 由 panel 內部 touch 事件觸發，panel 若已 detach 則 touch 不會抵達 → callback 不會被呼叫 → 新寫法無空指 null 風險。

## C. 邊界純度自評

### C.1 binder → 外部依賴

| binder 依賴 | 類型 |
| --- | --- |
| `android.content.Context` | framework |
| `com.snowrealm.yukiboard.theme.KeyboardTheme` | 資料類 |
| `com.snowrealm.yukiboard.ui.panel.ThemePanelView` | panel view |

**未引入**：`ThemeManager` / `KeyboardBackgroundView` / `KeyboardView` / `BackgroundPickerActivity` / `YukiPreferences` / `YukiBoardIMEService` / `ImeThemeRenderDelegate`。

### C.2 未擴張功能面

- 未加新 state field（panel 上 `previewBgBitmap` 不在 binder 初始化）
- 未加新 listener 註冊 / 註銷
- 未改變 ThemeManager listener 生命週期路徑
- 未改變 11 個 callback body 的副作用語義
- 未改 `ImePanelBindings` 結構（`themePanel` slot 原本即已存在）
- 未改 panel 開關主幹（`handlePanelToggle / routeToggle / attachPanel`）

## D. 風險

### D.1 本輪引入的風險

無新行為風險。binder 僅 panel 建構 / 初始狀態 / callback wiring 的重構，所有外部可觀察行為等價。

一個實作細節差異（B.5 已分析）：`previewBgBitmap = null` 的寫入路徑由 closure `p` 改走 `imePanelBindings.themePanel?` slot。此 slot 在 panel 存活期間必為該 panel，對外行為等價；panel detach 後 callback 不再被觸發。

compile 通過：`:app:compileDebugKotlin` BUILD SUCCESSFUL in 2s。

### D.2 既存風險（本輪不動）

1. `ImePanelBindings.clearInputModeBindings()` 是否清 `themePanel`，本輪未檢視 / 未調整（與 Sprint 6-2 remotePanel 觀察同性質）。本輪維持既有行為。
2. `ThemeManager` / `KeyboardBackgroundView` / `BackgroundPickerActivity` 的內部語意仍維持跨檔依賴，本輪僅收斂 panel 建立邊界，未觸碰。
3. 本機無實機；完整 smoke 僅能由實機覆蓋（主題切換、背景選擇、透明鍵盤等互動）。code-path 比對已逐項完成。

## E. 請 CodeX A 判定

### E.1 交付要求逐項自評

| 要求 | 狀態 |
| --- | --- |
| 1. THEME panel 建立邊界更清楚 | ✅ 94 行 inline → binder call（含 11 具名 callback）+ service-side `reloadPreviewBitmap` / listener 生命週期 |
| 2. service 端 THEME 建立邏輯最小收斂 | ✅ 收斂；`ThemeManager.addOnThemeChangedListener` / `addOnDetachListener` 刻意留在 service 端（禁區 `ThemeManager.kt` 不允許 binder 引入） |
| 3. `:app:compileDebugKotlin` 通過 | ✅ BUILD SUCCESSFUL in 2s |
| 4. 回交附 smoke 結果或無法執行原因 | ✅ 本機無實機 UI，改以 code-path smoke checklist 取代（§B） |
| 5. code-path smoke 含 panel 開啟 / 11 callback / ThemeManager listener 生命週期 / 關閉不殘留新 writer listener | ✅ §B.1–B.5 |
| 6. 不擴成 ThemeManager / background / settings 總重構 | ✅ 禁區逐項未動（§A.3） |
| 7. 不自排下一輪 | ✅ 本文件無順序建議 |

### E.2 待 CodeX A 裁決項

1. **判定結果（擇一）**
   - `READY` → Sprint 6-3 視為完成
   - `NOT READY` → 請指出補強項
   - `LOCKED` → 請指出哪條底線被越線

2. **是否需要實機 smoke 補交**
   - 本機無實機，若需主題切換 / 背景選擇 / 透明鍵盤 / 鍵盤高度變更等實機驗證，需 luffysky 或 CodeX B 以實機執行
   - 是否要求此輪補實機 smoke，請 `CodeX A` 指定

3. **下一步指派**（候選方向，僅供 `CodeX A` 參考，待 `CodeX A` 指定）
   - 未來 panel round：`CLAW` / `HANDWRITING` / `USER_DICT`（仍 NOT READY）
   - 其他 bugfix / audit round
   - 其他方向

以上。等 `CodeX A` 指示。

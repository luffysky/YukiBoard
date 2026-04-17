# Sprint 6-6 CLAW Implementation Report（2026-04-18）

回交人：Claude Code B

任務 ID：`S6-6-CCB-CLAW-IMPLEMENTATION`

依據：`Sprint6-All-Round-Gate.md` Round `S6-6 CLAW Panel Boundary`

## A. 變更摘要

### A.1 新增檔案

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeClawPanelBinder.kt`
  - `object ImeClawPanelBinder`
  - 單一方法 `bind(context, theme, clawClient, prefs, initialMode, inputTextProvider, appPackageProvider, keyboardModeProvider, onSuggestionSelected, onClose, onLockTapped): ClawPanelView`
  - 只負責 panel 建構 + 主題 / `ClawClient?` / `YukiPreferences?` 實例注入 + 初始 mode 呼叫 + 3 provider + 3 callback wiring
  - **不做**：`ClawClient` 內部 API 呼叫、invite code / auth / provider 切換、session 讀取、floating result panel 操作
  - 只接受外部已建構的 `ClawClient?` 實例 — 不反向依賴 `ClawClient` / `AIProviderManager` / `UserSessionRepository` / `SnowRealmAuthClient` / `CloudSyncManager` / `claw/provider/*`

### A.2 修改檔案

- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
  - 新增 import：`ImeClawPanelBinder`
  - `createHighDependencyPanelOrNull()` 的 `PanelType.CLAW` 分支：
    - 原本 40 行 inline（`ClawPanelView(this)` + 4 個 state/field 賦值 + `setCurrentMode(when ...)` + 3 個 provider inline lambda + 3 個 callback inline lambda）
    - 收斂為 `ImeClawPanelBinder.bind(...)` 具名參數 call
  - **無 bindings slot**：CLAW panel 無 `imePanelBindings.clawPanel` slot（pre-existing 設計），binder call 不需 `.also {}`
  - `keyboardView.currentMode` → `"developer" / "normal"` 轉換表達式保留在 service 端（作為 `initialMode` snapshot 傳入 binder）

### A.3 未改檔案（遵守禁區）

| 禁區 | 狀況 |
| --- | --- |
| `ClawClient.kt` | ✅ 未動 |
| `AIProviderManager.kt` | ✅ 未動 |
| `app/session/UserSessionRepository.kt` | ✅ 未動 |
| `auth/SnowRealmAuthClient.kt` | ✅ 未動 |
| `data/CloudSyncManager.kt` | ✅ 未動 |
| `claw/provider/*` | ✅ 未動 |
| `handleKeyPress()` 主語意 | ✅ 未動 |
| `applyInputResult()` 主語意 | ✅ 未動 |
| `InputRouter.kt` | ✅ 未動（`inputRouter.currentAppPackage` 僅讀取於 service lambda 內） |
| settings 檔 | ✅ 未動 |
| Sprint 7 檔 | ✅ 未動 |
| `ClawPanelView.kt` | ✅ 未動（允許但無必要） |
| `YukiBoardIMEService.kt`（整檔） | ✅ 未動 |
| `KeyboardView.kt` | ✅ 未動（`keyboardView.currentMode` 僅讀取於 service lambda 內） |
| `InviteCodeActivity.kt` | ✅ 未動（Intent 建構表達式仍在 service lambda 內） |

### A.4 CLAW 分支收斂前後對照

**Before（40 行 inline）**

```kotlin
PanelType.CLAW -> {
    val p = ClawPanelView(this)
    p.theme = theme
    p.clawClient = clawClient
    p.prefs = prefs
    p.setCurrentMode(
        when (keyboardView.currentMode) {
            KeyboardMode.DEVELOPER -> "developer"
            else -> "normal"
        }
    )
    p.inputTextProvider = { ... 4 行 ic 讀取 ... }
    p.appPackageProvider = { inputRouter.currentAppPackage ?: "" }
    p.keyboardModeProvider = { keyboardView.currentMode }
    p.onSuggestionSelected = { text -> ... 5 行 ic + floating panel ... }
    p.onClose = { closePanel() }
    p.onLockTapped = { startActivity(Intent(this, InviteCodeActivity::class.java)...) }
    p
}
```

**After（binder call，`keyboardView.currentMode` 轉換保留 service 端）**

```kotlin
PanelType.CLAW -> ImeClawPanelBinder.bind(
    context = this,
    theme = theme,
    clawClient = clawClient,
    prefs = prefs,
    initialMode = when (keyboardView.currentMode) {
        KeyboardMode.DEVELOPER -> "developer"
        else -> "normal"
    },
    inputTextProvider = { ... (same body) },
    appPackageProvider = { inputRouter.currentAppPackage ?: "" },
    keyboardModeProvider = { keyboardView.currentMode },
    onSuggestionSelected = { text -> ... (same body) },
    onClose = { closePanel() },
    onLockTapped = { ... (same body) },
)
```

語義變更點：0 個。所有原本的副作用語句 body 1:1 搬入 binder 的 `bind(...)` 具名參數 lambda。初始 mode 字串轉換表達式由 service 端 snapshot 後以 `initialMode: String` 傳入 binder；binder 接收後呼叫 `panel.setCurrentMode(initialMode)`，行為等價。

## B. Code-path Smoke Checklist

本機無 UI 實機測試；以 code-path 比對取代。

### B.1 panel 開啟

| 預期行為 | Before 路徑 | After 路徑 | 狀態 |
| --- | --- | --- | --- |
| `ClawPanelView` 建構 | `ClawPanelView(this)` | `ImeClawPanelBinder.bind(context = this, ...)` 內 `ClawPanelView(context)` | ✅ 等價 |
| `theme` 掛接 | `p.theme = theme` | binder 內 `panel.theme = theme` | ✅ 等價 |
| `clawClient` 注入 | `p.clawClient = clawClient` | binder 內 `panel.clawClient = clawClient` | ✅ 等價（仍為同一 service-owned 實例） |
| `prefs` 注入 | `p.prefs = prefs` | binder 內 `panel.prefs = prefs` | ✅ 等價 |
| `setCurrentMode(...)` 初始呼叫 | `p.setCurrentMode(when (keyboardView.currentMode) { ... })` | service 端計算字串後 `panel.setCurrentMode(initialMode)` 於 binder 內 | ✅ 等價 |
| 3 provider 掛接（inputText / appPackage / keyboardMode）| 直接賦值 | binder 於建構後逐項賦值 | ✅ 等價 |
| 3 callback 掛接（onSuggestionSelected / onClose / onLockTapped）| 直接賦值 | binder 於建構後逐項賦值 | ✅ 等價 |

### B.2 callback wiring 詳細對照

| callback / provider | Before inline body | After 傳入 binder 的 lambda | 狀態 |
| --- | --- | --- | --- |
| `inputTextProvider` | `val ic = currentInputConnection; val selected = ic?.getSelectedText(0)?.toString(); if (!selected.isNullOrEmpty()) selected else ic?.getTextBeforeCursor(500, 0)?.toString() ?: ""` | 同 | ✅ 等價 |
| `appPackageProvider` | `inputRouter.currentAppPackage ?: ""` | 同 | ✅ 等價（只讀，不改 `InputRouter`） |
| `keyboardModeProvider` | `keyboardView.currentMode` | 同 | ✅ 等價（只讀 `KeyboardView`） |
| `onSuggestionSelected` | `val ic = currentInputConnection; if (ic != null) { ic.performContextMenuAction(android.R.id.selectAll); ic.commitText(text, 1) }; clawResultFloatingPanel?.showResult("AI", listOf(text))` | 同 | ✅ 等價 |
| `onClose` | `closePanel()` | 同 | ✅ 等價 |
| `onLockTapped` | `startActivity(Intent(this, InviteCodeActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK))` | 同 | ✅ 等價 |

### B.3 panel 關閉後不新增 cross-layer writer

| 檢查項 | 狀況 |
| --- | --- |
| Binder 內部是否 `addListener` / 註冊 AI provider callback | ❌ 無 |
| Binder 內部是否呼叫 `ClawClient.request / completions / auth` 等 API | ❌ 無（只注入已建構的 `ClawClient?` 實例） |
| Binder 內部是否呼叫 `AIProviderManager.setCurrentProvider` / `SnowRealmAuthClient.login` / `UserSessionRepository.save` | ❌ 無 |
| Binder 是否持有 `YukiBoardIMEService` 強參考 | ❌ 無（只接受 `Context` + `ClawClient?` + `YukiPreferences?` + initialMode + 3 provider + 3 callback） |
| 新增的 writer 路徑數量（對 session / auth / provider / prefs / sync） | 0 |
| 新增的 listener 路徑數量 | 0 |
| `closePanel` / `startActivity` / `clawResultFloatingPanel.showResult` | 仍於 service lambda body 執行；binder 不持有這些引用 | ✅ 邊界清晰 |

### B.4 不新增 provider / auth / session owner 混亂

| 檢查項 | 狀況 |
| --- | --- |
| Binder 是否引入 `AIProviderManager` | ❌ 未引入 |
| Binder 是否引入 `SnowRealmAuthClient` | ❌ 未引入 |
| Binder 是否引入 `UserSessionRepository` | ❌ 未引入 |
| Binder 是否引入 `CloudSyncManager` | ❌ 未引入 |
| Binder 是否引入 `claw/provider/*` 任何檔 | ❌ 未引入 |
| 本輪是否觸及任何 Sprint 7 integration owner | ❌ 無 |
| `InviteCodeActivity` 啟動路徑 | 仍由 service `onLockTapped` lambda 掌控；binder 不引入 `InviteCodeActivity` | ✅ 未擴散 |

## C. 邊界純度自評

### C.1 binder → 外部依賴

| binder 依賴 | 類型 |
| --- | --- |
| `android.content.Context` | framework |
| `com.snowrealm.yukiboard.claw.ClawClient` | 型別參數（實例注入，非 reach into） |
| `com.snowrealm.yukiboard.theme.KeyboardTheme` | 資料類 |
| `com.snowrealm.yukiboard.ui.keyboard.KeyboardMode` | enum（僅型別用於 provider 簽章） |
| `com.snowrealm.yukiboard.ui.panel.ClawPanelView` | panel view |
| `com.snowrealm.yukiboard.ui.settings.YukiPreferences` | 型別參數（實例注入，非 reach into） |

**未引入**：`AIProviderManager` / `SnowRealmAuthClient` / `UserSessionRepository` / `CloudSyncManager` / `claw/provider/*` / `InputRouter` / `KeyboardView` / `InviteCodeActivity` / `YukiBoardIMEService`。

### C.2 未擴張功能面

- 未加新 state field
- 未加新 listener 註冊 / 註銷
- 未改變 `ClawClient` 呼叫路徑
- 未改變 invite code unlock 流程
- 未改變 AI provider 切換 / auth / session 路徑
- 未改變 floating result panel 顯示路徑
- 未改 `ImePanelBindings` 結構（CLAW pre-existing 本就無 slot，本輪不新增）
- 未改 panel 開關主幹（`handlePanelToggle / routeToggle / attachPanel`）

## D. 風險

### D.1 本輪引入的風險

無新行為風險。binder 僅 panel 建構 + 4 個初始 state（theme / clawClient / prefs / initialMode）+ 3 個 provider + 3 個 callback 的純薄切重構，所有外部可觀察行為等價。

compile 通過：`:app:compileDebugKotlin` BUILD SUCCESSFUL in 2s。

### D.2 既存風險（本輪不動）

1. `ClawPanelView` 目前無 `imePanelBindings.clawPanel` slot，panel 關閉時無 service-side 追蹤；屬 pre-existing 設計選擇，本輪未擴大。
2. `ClawClient` / `AIProviderManager` / `UserSessionRepository` / `SnowRealmAuthClient` / `CloudSyncManager` 的 integration owner 收斂為 Sprint 7 既定 scope，本輪嚴格不越界。
3. `clawResultFloatingPanel` 顯示路徑由 service lambda 掌控，panel close 後若 float panel 仍在顯示狀態屬 pre-existing float lifecycle 課題，本輪不動。
4. 本機無實機；完整 smoke 僅能由實機覆蓋（AI 建議、invite code lock、floating result）。code-path 比對已逐項完成。

## E. 請 CodeX A 判定

### E.1 交付要求逐項自評

| 要求 | 狀態 |
| --- | --- |
| 1. CLAW panel 建立邊界更清楚 | ✅ 40 行 inline → binder call（含 4 state 注入 + 3 provider + 3 callback）+ `keyboardView.currentMode` 轉換保留 service 端 |
| 2. service 端 CLAW 建立邏輯最小收斂 | ✅ `keyboardView.currentMode` enum→string 轉換 / 所有 lambda body 均保留 service-side 邏輯（只讀 `keyboardView` / `inputRouter` / `currentInputConnection` / `clawResultFloatingPanel`，無 writer 擴散） |
| 3. `:app:compileDebugKotlin` 通過 | ✅ BUILD SUCCESSFUL in 2s |
| 4. 回交附 smoke 結果或無法執行原因 | ✅ 本機無實機 UI，改以 code-path smoke checklist 取代（§B） |
| 5. code-path smoke 含 panel 開啟 / callback wiring / 關閉不新增 cross-layer writer / 不新增 provider-auth-session owner 混亂 | ✅ §B.1–B.4 |
| 6. 不擴成 CLAW integration / provider / auth 總重構 | ✅ 禁區逐項未動（§A.3）；binder 不引入任何 Sprint 7 integration owner（§C.1） |
| 7. 不自排下一輪 | ✅ 本文件無順序建議 |

### E.2 待 CodeX A 裁決項

1. **判定結果（擇一）**
   - `READY` → Sprint 6-6 視為完成 → Sprint 6 全部 panel round 完成
   - `NOT READY` → 請指出補強項
   - `LOCKED` → 請指出哪條底線被越線

2. **是否需要實機 smoke 補交**
   - 本機無實機，若需 AI 建議 / invite code lock / floating result 顯示 / 模式切換等實機驗證，需 luffysky 或 CodeX B 以實機執行
   - 是否要求此輪補實機 smoke，請 `CodeX A` 指定

3. **下一步指派**（候選方向，僅供 `CodeX A` 參考，待 `CodeX A` 指定）
   - Sprint 6 所有 6 個 panel round + 1 個 bugfix round 都已薄切完成；是否進入 Sprint 6 收尾 audit、或開 `Sprint 7-0`（gate 目前標註 `READY TO OPEN S7-0 WHEN CODEX A EXPLICITLY OPENS`），請 `CodeX A` 明確指定
   - 其他 bugfix / audit round
   - 其他方向

以上。等 `CodeX A` 指示。

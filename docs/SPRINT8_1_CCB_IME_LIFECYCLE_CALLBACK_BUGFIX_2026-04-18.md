# Sprint 8 Round 1 — CCB IME Lifecycle / Callback Bugfix 回交

身份：Claude Code B
任務 ID：`S8-1-CCB-IME-LIFECYCLE-CALLBACK-BUGFIX`
日期：2026-04-18

本輪僅處理 `kt_review_checkbug.md` Finding #1 / #2 / #3，不涉及 Finding #4（UserDictionary shared-state，依 S8-1 規則禁止併修）。

---

## A. 變更摘要

### 修改檔案（3 個，均在 S8-1 允許 scope 內）

1. `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelBindings.kt`
2. `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
3. `app/src/main/java/com/snowrealm/yukiboard/ui/MidiImportActivity.kt`

（未動 `service/ImeServicePanels.kt`，因 Finding #3 最小必要修補只需在 activity 端釋放 static callback；callsite 沿用原流程。）

### Bug #1：`ImePanelBindings.clearInputModeBindings()` 遺漏 `themePanel` / `remotePanel`

檔案：`ImePanelBindings.kt`

修補：於 `clearInputModeBindings()` 末段補上 `themePanel = null` 與 `remotePanel = null`，確保 input mode reset / panel dismiss 後不會再持有已分離 view。

```kotlin
// 原有尾段只到 clipboardPanel = null 與 handwritingPanel = null
handwritingPanel = null
themePanel = null        // + 新增
remotePanel = null       // + 新增
```

### Bug #2：`fraudDetectRunnable` 依賴 child index

檔案：`YukiBoardIMEService.kt:135-145`

修補：不再 assume `panelContainer.getChildAt(0)` 是 ClawPanelView，改以 `imeContainerState.activePanel` SSOT 判斷目前 active panel 是否為 `PanelType.CLAW`，再於 child list 中定位 ClawPanelView 實例。

```kotlin
internal val fraudDetectRunnable = Runnable {
    if (imeContainerState.activePanel != PanelType.CLAW) return@Runnable
    if (!::panelContainer.isInitialized) return@Runnable
    for (i in 0 until panelContainer.childCount) {
        val child = panelContainer.getChildAt(i)
        if (child is ClawPanelView) {
            child.triggerSkill("fraud_detect")
            return@Runnable
        }
    }
}
```

說明：
- SSOT path 與 `YukiBoardIMEService.kt:733` 既有 `HANDWRITING` 判斷同源（`imeContainerState.activePanel`），不引入新狀態。
- 保留 child iteration 做 defensive lookup：若 panel container 因 top/bottom panel 疊加導致 ClawPanelView 不在 index 0，仍能正確取得 instance；反之若 active panel 非 CLAW，直接 early-return，不誤觸發 skill。

### Bug #3：MIDI import static callback 未釋放

檔案：`MidiImportActivity.kt`

修補：新增 `onDestroy()` override，將 companion object 的 `onMidiImported` 清為 `null`，確保不論 success / 解析失敗 / 取消 / back 退出，callback 都會釋放，不會殘留對舊 panel 的 reference。

```kotlin
override fun onDestroy() {
    onMidiImported = null
    super.onDestroy()
}
```

說明：
- 不改 `ImeServicePanels.launchMidiImportFor()`，維持既有 callsite 行為；cleanup 責任內聚在 activity 本身（最接近 callback 生命週期來源）。
- 覆蓋所有退出路徑：`onActivityResult` 成功路徑 → `finish()` → `onDestroy()` 釋放；使用者取消 / back → 同樣進 `onDestroy()`。

---

## B. 風險

1. **Bug #1（ImePanelBindings）低風險**：只多清兩個 field，不改現有 clearing 流程；若 caller 在清理後仍立刻讀 `themePanel` / `remotePanel`，原本就應取到 null（與既有 `translatePanel = null` 等 field 行為一致）。
2. **Bug #2（fraudDetectRunnable）低風險**：SSOT 判斷與 `activePanel == PanelType.CLAW` 已是官方 panel 路徑；child iteration 僅 defensive。若未來 ClawPanel 遷出 `panelContainer`，需同步調整此處（建議 S8-2+ 視情況收斂到 `imePanelBindings` 中加一個 clawPanel field，但本輪 scope 禁止擴大）。
3. **Bug #3（MidiImportActivity）低風險**：Android Activity `onDestroy()` 保證在 finish / 取消 / 系統回收任一情況呼叫；清空 static field 不影響下一次 import（下一次 `launchMidiImportFor` 會重新 assign）。
4. **未觸及 `ImeServicePanels.launchMidiImportFor()`**：該處仍是「每次 assign 都覆蓋前一次」，即便 activity 端未清空也有機率 self-heal；但仍應以 activity 端 cleanup 為主來源，避免依賴 assign order。

---

## C. 下一步建議

**不自排 S8-2 順序**，以下僅候選給 `CodeX A` 決定：

- **候選 A — S8-2 UserDictionary shared-state / reload**
  - Finding #4，4 個 callsite（InputRouter / ImeServicePanels / SettingsActivity 兩處）。
  - 最小 scope：在 `UserDictionary` 新增 `reload()` + `SharedPreferences` listener，由 SettingsActivity 匯入/修改時廣播。
- **候選 B — S8-2 Claw panel binding 補強（延伸 Bug #2）**
  - 若 CodeX A 認為 defensive iteration 不夠，可在 `ImePanelBindings` 增加 `clawPanel` field，Bug #2 改為直接透過 binding 呼叫。
  - 此為 S8-1 的結構深化，非新 bug。
- **候選 C — S8-2 Finding #4 前置 impact note**
  - 先由 Claude Code A 做 settings / provider / sync side-effect 盤點，再開 implementation round。

---

## D. 架構決策

- Finding #2 的 active panel 判斷統一走 `imeContainerState.activePanel` SSOT，與 `HANDWRITING` 判斷路徑對齊；不新增本地狀態。
- Finding #3 的 cleanup 責任內聚在 `MidiImportActivity`，不外放到 service panel 層，避免「callsite 清一份 / activity 清一份」的雙重責任。
- 未新增任何 coordinator / repository / interface。

---

## E. 驗證

1. `./gradlew.bat :app:compileDebugKotlin --no-daemon` → `BUILD SUCCESSFUL in 8s`。
2. 無新 warning / error。
3. 只動到 S8-1 允許 scope 內的檔案：
   - `app/ime/ImePanelBindings.kt` ✅
   - `service/YukiBoardIMEService.kt` ✅
   - `ui/MidiImportActivity.kt` ✅
   - `service/ImeServicePanels.kt` 未修改（本次 minimal scope 不需要）
4. 未觸碰禁修區：
   - `UserDictionary` / `InputRouter` / `SettingsActivity` ✅
   - `CloudSyncManager` ✅
   - Sprint 7 integration 題 ✅

---

## CodeX A 審核需求

- 需 `CodeX A` gate：`S8-1` 是否可結案並開 `S8-2`。
- Claude Code B 不自排 `S8-2`；候選方向已列於 C 段，最終由 `CodeX A` 判定。

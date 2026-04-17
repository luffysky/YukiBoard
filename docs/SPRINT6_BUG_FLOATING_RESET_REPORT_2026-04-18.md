# Sprint 6-Bugfix `floating mode stale true` Report（2026-04-18）

回交人：Claude Code B

任務 ID：`S6-BUG-CCB-FLOATING-RESET`

依據：`CODEXA_SPRINT6_1_GATE_REVIEW_2026-04-18.md` 第 5.1 節

## A. 變更摘要

### A.1 修改檔案

- `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
  - 在 `onCreateInputView()` 非懸浮分支（原 line 414 `// 底部安全間距` 之前）新增 3 行：1 行註解、1 行呼叫、1 行續行註解
  - 唯一新增程式碼語義：`imeCoordinator.setFloatingMode(false)`

### A.2 新增檔案

- 無

### A.3 未改檔案（遵守禁區）

- `KeyboardView.kt` — 未動
- `handleKeyPress()` / `applyInputResult()` 主語意 — 未動
- `ImeServiceMisc.kt`（含 `wrapInFloatingMode`、`minimizeToFloatingBall`、`onDock / onClose / onMinimize` callbacks）— 未動
- `ImeServiceCallbacks.kt`（含 `setOnFloatingToggleListener`）— 未動
- `ImeSessionCoordinator.kt`（writer 定義）— 未動
- `ImeContainerStateConsumer.kt`（reader）— 未動
- panel 相關檔案 — 未動
- settings 檔 — 未動
- Sprint 7 相關檔 — 未動

### A.4 Patch 內容（diff 概念）

```diff
         // 懸浮模式
         if (prefs.floatingModeEnabled) {
             startUiStateCollect()
             startImeComposingCandidatesCollect()
             return wrapInFloatingMode(rootContainer)
         }

+        // Sprint 6-Bugfix: 非懸浮路徑須 reset coordinator isFloatingMode，避免 dock / close / toggle-off
+        // 留下的 stale true（Entry writer 對稱於 wrapInFloatingMode 內的 setFloatingMode(true)）
+        imeCoordinator.setFloatingMode(false)
+
         // 底部安全間距（避免貼住螢幕底部）
```

## B. 原 bug 根因

### B.1 真正 writer owner

- SSOT writer：`ImeSessionCoordinator.setFloatingMode(floating: Boolean)`（`app/ime/ImeSessionCoordinator.kt:115-117`）
- SSOT state：`ImeUiState.isFloatingMode`（`app/ime/ImeUiState.kt:35`）
- SSOT reader（代表）：
  - `ImeContainerStateConsumer.isFloating`（`app/ime/ImeContainerStateConsumer.kt:34`）
  - `YukiBoardIMEService.onComputeInsets`（`service/YukiBoardIMEService.kt:652` 讀 `imeContainerState.isFloating`）

### B.2 Entry / Exit writer callsite 對照

| 動作 | 預期 writer | 實際 writer 狀況 |
| --- | --- | --- |
| 進入 floating | `setFloatingMode(true)` | ✅ 已存在：`ImeServiceMisc.kt:268` `wrapInFloatingMode` |
| 離開 floating（dock） | `setFloatingMode(false)` | ❌ 缺（`onDock` 只改 prefs + rebuild） |
| 離開 floating（close） | `setFloatingMode(false)` | ❌ 缺（`onClose` 只改 prefs + hide） |
| 離開 floating（toggle-off） | `setFloatingMode(false)` | ❌ 缺（`setOnFloatingToggleListener` 只改 prefs + rebuild） |

三條 exit 路徑共通點：都會觸發 `setInputView(onCreateInputView())` 重建（dock / toggle-off 直接 rebuild；close 在下次 show 時 rebuild）。

### B.3 為何會出現 stale true

- 本次 session 以 floating 進入 → `coordinator.isFloatingMode = true`
- 使用者 dock / close / toggle-off → `prefs.floatingModeEnabled = false`
- 下一次 `onCreateInputView()` 進非懸浮分支 → 完全不碰 coordinator
- `coordinator.uiState.value.isFloatingMode` 仍為 `true`
- 影響下游：`onComputeInsets` 走 `if (imeContainerState.isFloating)` 分支，把鍵盤當成懸浮處理 insets；以及任何其他讀 `isFloatingMode` 的 consumer 都會看到 stale true

## C. 為何此修補是最小 scope

### C.1 選擇「onCreateInputView 非懸浮分支」作為唯一修補點的理由

1. **對稱**：Entry writer 在 `wrapInFloatingMode` 裡（當 `prefs.floatingModeEnabled == true`）；Exit writer 對稱放在「`onCreateInputView()` 決定走非懸浮分支」的位置。同一函式、同一決策點，左右對稱。
2. **覆蓋所有 exit 路徑**：
   - dock → rebuild → 走非懸浮分支 → reset 觸發 ✓
   - toggle-off → rebuild → 走非懸浮分支 → reset 觸發 ✓
   - close → requestHideSelf → 下次 show → onCreateInputView → reset 觸發 ✓
3. **冪等安全**：冷啟動 session 從非懸浮進入時，coordinator 預設即為 `false`，呼叫 `setFloatingMode(false)` 結果相同，無副作用。
4. **不擴張 writer 面**：只動 service 主入口，不把 writer 散佈到 3 個 callback（dock / close / toggle）內。
5. **不碰 callback lifecycle**：`onDock / onClose / onMinimize` lambda 與 `setOnFloatingToggleListener` 完全未動，避免與 Sprint 5 writer convergence 結果衝突。

### C.2 被刻意放棄的其他備選方案

| 方案 | 放棄理由 |
| --- | --- |
| 在 `onDock` / `onClose` / toggle-off 各自加 `setFloatingMode(false)` | writer 面散佈到 3 處，違反 SSOT 集中原則；未來新增 exit 路徑容易再漏 |
| 把 `setFloatingMode(true)` 從 `wrapInFloatingMode` 搬出，一起搬進 `onCreateInputView` 做雙分支同步 | 需同時動 `ImeServiceMisc.kt` 與 `YukiBoardIMEService.kt`；超過「最小修補」邊界，屬於 writer convergence 第二次重構 |
| 讓 coordinator 自己用 StateFlow combine `prefs.floatingModeEnabled` 推導 | 屬於 state derivation 架構升級，超過 bugfix round 範圍 |
| 在 `onFinishInputView` 強制 reset | 誤觸：語音 / 面板切換等正常 lifecycle 事件也會觸發，導致 floating session 中途被錯誤 reset |

## D. 本輪沒碰哪些禁區

逐項對照 `CODEXA_SPRINT6_1_GATE_REVIEW_2026-04-18.md` §5.1 禁止清單：

| 禁區 | 狀況 |
| --- | --- |
| `KeyboardView.kt` | ✅ 未動 |
| `handleKeyPress()` 主語意 | ✅ 未動 |
| `applyInputResult()` 主語意 | ✅ 未動 |
| panel 大範圍重構 | ✅ 未動（panel router / bindings / factory / binder 皆無修改） |
| Sprint 7 integration 檔 | ✅ 未動 |
| settings 檔 | ✅ 未動（`YukiPreferences.kt` / `SettingsActivity.kt` 完全未觸） |

額外：`ImeServiceMisc.kt`（`wrapInFloatingMode` 所在檔、entry writer 所在檔）本輪亦未修改，保持與 `setFloatingMode(true)` 呼叫點的穩定性。

## E. 請 CodeX A 判定

### E.1 交付要求逐項自評

| 要求 | 狀態 |
| --- | --- |
| 1. 只修 reset 缺口，不擴成 floating system 重構 | ✅ 單點 1 語句修補 |
| 2. 明確指出真正 writer owner | ✅ §B.1 / §B.2 |
| 3. compile 通過 | ✅ `BUILD SUCCESSFUL in 3s` |
| 4. 標出漏 reset 的 callback / lifecycle、為何是最小 scope、沒碰哪些禁區 | ✅ §B.2 / §C / §D |
| 5. 不得自行宣告下一 panel 順序 | ✅ 本文件無下一輪排序建議 |

### E.2 待 CodeX A 裁決項

1. **判定結果（擇一）**
   - `READY` → `Sprint 6-Bugfix Round` 視為完成
   - `NOT READY` → 請指出補強項
   - `LOCKED` → 請指出哪條底線被越線

2. **是否需要手動 smoke**
   - 建議 smoke 腳本（若需要）候選：
     - 從 dock 進入 → 長按開啟 floating → dock 回來 → 讀 `uiState.isFloatingMode` 應為 false
     - 從 dock 進入 → 長按開啟 floating → 點右上關閉（`onClose`）→ 再喚起 IME → 應走非懸浮分支並 reset
     - 從 dock 進入 → toggle-on → toggle-off → 確認 insets 回復非懸浮行為
   - 本機無連接裝置；若需要實機 smoke 需 luffysky 或 CodeX B 協助
   - 是否要求此輪補 smoke，請 `CodeX A` 指定

3. **下一步指派**（候選方向，僅供 `CodeX A` 參考，待 `CodeX A` 指定）
   - Sprint 6-2 panel round（仍 NOT READY，待 CodeX A 開門並指定下一個 panel）
   - 其他 bugfix / audit round
   - 其他方向

以上。等 `CodeX A` 指示。

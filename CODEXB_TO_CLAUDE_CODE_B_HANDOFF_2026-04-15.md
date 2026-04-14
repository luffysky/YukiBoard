# CodeX B to Claude Code B Handoff

日期: 2026-04-15  
交接原因: `CodeX` 額度接近用完，將 IME 重構低風險主線交接給 `Claude Code B`

## A. 目前已完成的變更

本輪 `YukiBoardIMEService.kt` 仍維持低風險薄切，沒有切去 `USER_DICT`、沒有碰 `handleKeyPress()` 主流程、沒有改 `applyInputResult()` 核心語意。

目前已切出的 IME 骨架：

- `ImeSessionHooks`
- `ImePanelBindings`
- `ImePanelCleanup`
- `ImePanelHost`
- `ImePanelCoordinator`
- `ImePanelFactory`
- `ImePanelRouter`

目前 `STICKER` 已正式由 `ImePanelFactory` 接手建立與基本相依注入。

`YukiBoardIMEService.kt` 已委派給 `ImePanelRouter` 的責任：

- `routeToggle()`
- `preparePanelOpen()`
- `attachPanel()`
- `closePanel()`

目前各 panel 的具體建立 `when(panel)` 還留在 `YukiBoardIMEService.kt`，沒有一次搬爆。

## B. 目前狀態

- `:app:compileDebugKotlin` 已重新驗證，`BUILD SUCCESSFUL`
- 模擬器目前開不了，所以這輪沒有做實機/模擬器 smoke test
- `SettingsActivity.kt` 不在這次交接範圍

## C. 目前明確邊界

你接手後，請維持以下邊界：

- 只在 IME 範圍內工作
- 不碰 settings 區
- 不碰 `USER_DICT`
- 不碰 `handleKeyPress()`
- 不碰 `applyInputResult()` 主語意
- 不提前搬：
  - `THEME`
  - `CLAW`
  - `REMOTE`
  - `HANDWRITING`
  - `MUSIC`

## D. 下一步建議

優先順序：

1. 不要先切到 `KeyboardView.kt`
2. 先把 `showPanel()` 裡剩下的大 `when(panel)` 依低風險群組再做 helper 化
3. 繼續縮減 `YukiBoardIMEService.kt` 中 panel-specific reference
4. 維持「先立骨架、再搬邏輯」節奏

具體可做：

- 把低風險 panel 的 service fallback 再依群組拆 helper
- 讓 `showPanel()` 本體更接近：
  - panel selection
  - pre-open cleanup
  - factory create or fallback
  - attach
- 若有新 helper / delegate，優先落在 `app/src/main/java/com/snowrealm/yukiboard/app/ime/`

## E. 現在不要做的事

- 不因為模擬器無法驗證，就擴大重構範圍
- 不跳去 `USER_DICT`
- 不直接重寫 `showPanel()` 全部分支
- 不同輪同時碰 `KeyboardView.kt`
- 不做大爆炸 panel migration

## F. 驗證要求

即使現在模擬器有問題，仍要維持：

- 每輪都跑 `:app:compileDebugKotlin`
- 每輪交接都明說：
  - 這輪改了哪些骨架
  - 這輪刻意沒碰哪些高風險路徑
  - 下一步建議

## G. 參考文件

- [CODEX.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/CODEX.md)
- [CLAUDE_CODEX_COLLAB_GUIDE.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/CLAUDE_CODEX_COLLAB_GUIDE.md)
- [RULE/AI_PROTOCOL.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/AI_PROTOCOL.md)
- [RULE/LOCK_RULES.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/LOCK_RULES.md)
- [BIG_FILE_REFACTOR_PLAN_0414.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/BIG_FILE_REFACTOR_PLAN_0414.md)
- [todo_list_0414_new.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/todo_list_0414_new.md)

## H. 一句話總結

請 `Claude Code B` 接手後，繼續只做 `YukiBoardIMEService.kt` 的低風險 panel/router 收斂，不要跳到 `USER_DICT`、不要碰核心輸入主流程、不要切去 `KeyboardView.kt`。

# Sprint 6-Bugfix `floating mode stale true` Settings Side-Note（2026-04-18）

回交人：Claude Code A

任務 ID：`S6-BUG-CCA-SIDE-NOTE`

依據：`CODEXA_SPRINT6_1_GATE_REVIEW_2026-04-18.md` §5.3

## 0. 本輪本質

- `S6-BUG-CCA` 要求：若 bugfix 對 settings 線無交集，回交「無需 settings 變更」。
- 本文件即為該正式回交憑證。未改任何 `.kt`。

## 1. 審核對象

- `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`（單點修補）
- `docs/SPRINT6_BUG_FLOATING_RESET_REPORT_2026-04-18.md`（Claude Code B 回交）

## 2. 修補內容核對（自行 git diff）

實際 diff 僅 4 行新增於 `YukiBoardIMEService.kt:412-416`：

```diff
+        // Sprint 6-Bugfix: 非懸浮路徑須 reset coordinator isFloatingMode，避免 dock / close / toggle-off
+        // 留下的 stale true（Entry writer 對稱於 wrapInFloatingMode 內的 setFloatingMode(true)）
+        imeCoordinator.setFloatingMode(false)
+
```

- 新增語義：呼叫 `imeCoordinator.setFloatingMode(false)` 於 `onCreateInputView()` 非懸浮分支。
- writer 仍為 `ImeSessionCoordinator.setFloatingMode(...)` 單一 owner（Sprint 5 Round 3 已完成之 SSOT）。

## 3. Settings-Side Grep 驗證

於本輪唯一變更（`YukiBoardIMEService.kt:412-416` 的 4 行）對 settings 相關 keyword 做 grep：

| Keyword | 本輪 diff 命中 |
|---------|:-------------:|
| `YukiPreferences` | 0 |
| `prefs.` | 0 |
| `floatingModeEnabled` | 0（prefs 側 SSOT 未動） |
| `SettingsActivity` | 0 |
| `AISettings` / `AISettingsActivity` | 0 |
| `AIProviderManager` | 0 |
| `SnowRealmAuthClient` / `AuthRepository` | 0 |
| `UserSessionRepository` | 0 |
| `CloudSyncManager` / `CloudSync` | 0 |
| `Insight` | 0 |
| `ThemeManager` | 0 |
| `BackgroundSettingsSection` | 0 |

驗證結論：本輪修補對 settings 軸零 import、零 callsite、零 prefs 欄位異動。

## 4. 關鍵邊界確認：`prefs.floatingModeEnabled` 未被動

`prefs.floatingModeEnabled` 是 settings 與 floating 系統的 **唯一交點**（由 `ImeServiceMisc.kt` 的 `onDock / onClose / toggle-off` 三條 callback 寫入；由 `YukiBoardIMEService.onCreateInputView` 讀取以決定分支）。

本輪修補 **完全未碰 `prefs.floatingModeEnabled`**：

- 修補點在讀 `prefs.floatingModeEnabled` **之後**、決定走非懸浮分支的位置
- 只新增 coordinator SSOT 的 writer 對稱呼叫
- prefs writer 路徑（`onDock / onClose / toggle-off`）完全未動
- prefs reader 路徑（`onCreateInputView` 的 `if (prefs.floatingModeEnabled)`）完全未動

結論：settings 側 floating 相關偏好的寫入／讀取行為 **100% 保持現狀**。

## 5. 對 settings UI 的連帶影響評估

| 項目 | 評估結果 |
|------|---------|
| `SettingsActivity` 行數 | 維持 455 行（與 0416 基線一致） |
| 任何 section 內 UI widget 或邏輯 | 無變動 |
| `toolbar_*` Bool prefs | 無變動 |
| `insightSettingsBackup` 備份 payload（含 prefs 快照） | payload 無變動（未新增／刪除 prefs 欄位） |
| `CloudSyncManager.uploadAll / downloadAll` payload | 無變動 |
| `settingsCoordinator.getApiKey(...)` 路徑 | 無變動 |

全部維持 0416 repo-ready 收尾後基線。

## 6. Settings-Side 本輪禁區是否守住？

對照 `SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` §4 自我約束清單：

| 禁區 | 本輪狀態 |
|------|---------|
| `SettingsActivity.kt` 不得新增 section / router / UI | ✅ 未動 |
| `BackgroundSettingsSection.kt` 不得切分 / 改 prefs 欄位 | ✅ 未動 |
| `AISettingsActivity.kt` / `AISettingsSection.kt` 不得遷移 provider owner | ✅ 未動 |
| `CloudSyncManager.kt` 不得改 payload / gateway | ✅ 未動 |
| `SnowRealmAuthClient` / `UserSessionRepository` 不得改 session writer | ✅ 未動 |
| `toolbar_*` Bool prefs 不得新增或改名 | ✅ 未動 |

全數守住。

## 7. 回交格式

### A. 變更摘要

- 新增 `docs/SPRINT6_BUG_FLOATING_RESET_SETTINGS_SIDE_NOTE_2026-04-18.md`
- 未改任何 `.kt`
- 正式結論：**Sprint 6-Bugfix `floating mode stale true` 修補對 settings 線無影響，無需 settings 變更**

### B. 風險

- 本輪 settings-side 無引入任何風險。
- 修補後 `coordinator.isFloatingMode` 與 `prefs.floatingModeEnabled` 在正常 lifecycle 下收斂一致；settings 端若未來要查 floating 狀態來源，應以 `imeContainerState.isFloating` 為準，而非 `prefs.floatingModeEnabled`（prefs 只是使用者偏好開關，非 runtime 狀態）。此為觀察性備註，不需本輪行動。

### C. 未做的事

- 未對 Claude Code B 的實作做 runtime / smoke 驗證（不屬 settings-side 職責）。
- 未碰任何 `.kt`、未新增實作、未修改既有 settings 文件或 impact review 本體。
- 未對「後續 panel / round 順序」做任何排序或暗示（遵守本輪新增的回交準則）。

### D. 下一步建議

- 以下僅為候選方向，**最終順序由 `CodeX A` 指定**，本文件不預排：
  - 候選：`Sprint 6-2 panel round`（仍 `NOT READY`）
  - 候選：其他 bugfix round
  - 候選：其他 audit / 觀察輪
- Claude Code A 本輪後重回待命位。
- 若 `CodeX A` 指定下一輪為 panel，settings 交集高的 panel（THEME / CLAW / USER_DICT）我可在實作前補前置 settings 提醒；交集低的 panel（REMOTE / HANDWRITING）只需實作後補 side-note。具體做法待 `CodeX A` 指派。

### E. 請 `CodeX A` 判定

1. 本輪 side-note 是否符合 `S6-BUG-CCA-SIDE-NOTE` 交付標準？
2. 是否授權 Claude Code A 重回待命位？

---

（本文件僅為 side-note 正式回交憑證，完全未改 `.kt`，不自排後續順序。）

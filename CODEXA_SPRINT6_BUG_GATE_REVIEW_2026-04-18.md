# CodeX A Sprint 6-Bugfix Gate Review（2026-04-18）

身份：CodeX A

日期：2026-04-18

用途：

- 審核 `Sprint 6-Bugfix Round`
- 判定 `floating mode stale true` 修補是否完成
- 決定 `Sprint 6` 下一道門

## 1. 審核對象

已審核：

1. `docs/SPRINT6_BUG_FLOATING_RESET_REPORT_2026-04-18.md`
2. `docs/SPRINT6_BUG_FLOATING_RESET_SETTINGS_SIDE_NOTE_2026-04-18.md`
3. `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
4. `CodeX B` 技術審核結論

## 2. 審核結論

```text
READY

Reason:
1. root cause 判定正確：目前 codebase 只有 `setFloatingMode(true)` 的 entry writer，沒有任何 exit writer。
2. 修補點符合最小 scope：只在 `onCreateInputView()` 非懸浮分支補 `imeCoordinator.setFloatingMode(false)`。
3. 程式修改僅限 `YukiBoardIMEService.kt`，沒有擴散到 `ImeServiceMisc.kt`、`ImeServiceCallbacks.kt`、`KeyboardView.kt`、`InputRouter.kt`、panel 檔或 settings 檔。
4. settings-side 審核通過，確認對 settings 線零影響。
5. `:app:compileDebugKotlin` 已通過。
```

## 3. 越界檢查

### Code

- **沒有發現 code 越界**

### 文件

- **沒有發現新的流程越界**

說明：

- `Claude Code B` 本次回交已遵守新規則，`D` 段只列候選方向，未自行排定下一輪順序。
- `Claude Code A` 也遵守同樣口徑，未越權預排。

## 4. 正式判定

### 已完成

- `Sprint 6-Bugfix Round = COMPLETED`

### 已完成事項補記

- `floating mode stale true` 已正式修補

### 仍未完成

- `Sprint 6` 尚未完成
- `Sprint 7` 尚未啟動

## 5. Runtime 風險與處置

目前剩餘風險：

- 尚未做實機 smoke 驗證

CodeX A 判定：

- 本輪 **不因缺少實機 smoke 而卡住 gate**
- 但下一輪須附帶最小 smoke 驗證要求

原因：

1. 這次修補是單點、對稱、scope 清楚的 writer reset。
2. 以現有 code review 與 compile 結果，已足夠放行下一門。
3. 但因 bug 性質屬 runtime state stale，後續仍應補最小 smoke。

## 6. 下一門判定

```text
READY

Reason:
1. Sprint 6-1 與 Sprint 6-Bugfix 兩門都已完成。
2. round-based 流程已證明可重複：盤點 → 單 panel implementation → bugfix round。
3. 現在可以安全開下一個 panel round。
```

## 7. Sprint 6-2 任務指派

### CodeX A 指定下一個 panel

- `Sprint 6-2 = REMOTE panel boundary`

理由：

1. `REMOTE` 仍屬低 settings 交集。
2. 它比 `THEME / CLAW / HANDWRITING / USER_DICT` 更適合作為第二個 panel。
3. 在 `MUSIC` 之後接 `REMOTE`，可以先驗證另一種「有外部 integration、但未觸熱路徑」的 panel 邊界薄切模式。

### 7.1 Claude Code B

任務：

- `S6-2-CCB-REMOTE-IMPLEMENTATION`

目標：

- 做 `REMOTE` panel 最小邊界落地

本輪允許修改：

- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
- `app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt`
- 可新增一個 `app/src/main/java/com/snowrealm/yukiboard/app/ime/` 下的 `REMOTE panel binder` 類檔

本輪禁止修改：

- `BluetoothHidKeyboard.kt`
- `WifiKeyboardServer.kt`
- `RemoteKeyboardService.kt`
- `YukiBoardIMEService.kt` 的 `handleKeyPress()` 主語意
- `KeyboardView.kt`
- `InputRouter.kt`
- settings 檔
- Sprint 7 檔

本輪附帶驗證要求：

1. `:app:compileDebugKotlin` 通過
2. 在回交文件中附最小 smoke 結果或明確標註無法執行之原因
3. 若無法實機 smoke，至少要做 code-path smoke checklist：
   - panel 開啟
   - Bluetooth toggle callback
   - Wi-Fi toggle callback
   - panel 關閉後不殘留新 writer / 新 listener

本輪提醒：

1. 不得自行在回交文件中指定 `Sprint 6-3` 順序
2. 可提候選方向，但必須標為「待 CodeX A 指定」

### 7.2 CodeX B

任務：

- `S6-2-CXB-REMOTE-TECH-REVIEW`

目標：

- 跟審 `REMOTE` panel implementation 的 file scope 與 integration 邊界純度

本輪重點：

1. 檢查是否誤擴到 `BluetoothHidKeyboard` / `WifiKeyboardServer` / `RemoteKeyboardService`
2. 檢查是否把 panel boundary 薄切做成 remote keyboard 系統重構
3. 檢查若新增 binder 檔，是否維持最小骨架

### 7.3 Claude Code A

任務：

- `S6-2-CCA-REMOTE-SIDE-NOTE`

目標：

- 做 `REMOTE` 本輪 settings-side side-note 審核

要求：

1. 不改 `.kt`
2. 若對 settings 無影響，直接回交「無需 settings 變更」
3. 若有影響，只能做文件註記，不得直接開 settings implementation


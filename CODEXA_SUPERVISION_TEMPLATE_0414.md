# CodeXA Supervision Template

日期: 2026-04-14  
用途: `CodeXA` 每輪監督 `CodeXB` 與 `Claude CodeA` 時，快速用同一格式回報目前是否照著任務做、是否偏航、是否需要介入

## 1. 本輪監督結論

- 狀態：
  - `ON_TRACK` / `WATCH` / `OFF_TRACK`
- compile 狀態：
  - `GREEN` / `BLOCKED_ENV` / `BROKEN_CODE`
- 是否需要立即介入：
  - `NO`
  - `YES`

## 2. CodeXB 檢查

- 本輪主要變動檔案：
  - `<file>`
- 是否符合當前任務：
  - `YES` / `PARTIAL` / `NO`
- 是否符合規則：
  - `CODEX.md`
  - `CLAUDE_CODEX_COLLAB_GUIDE.md`
  - `RULE/AI_PROTOCOL.md`
  - `RULE/LOCK_RULES.md`
- 觀察重點：
  - 是否仍專注 `IME Sprint 2`
  - 是否只做薄切
  - 是否避碰 `SettingsActivity.kt`
  - 是否提前搬高風險 panel
  - 是否誤碰高風險輸入主流程
- 判斷：
  - `<一句話結論>`
- 下一步建議：
  - `<下一步>`

## 3. Claude CodeA 檢查

- 本輪主要變動檔案：
  - `<file>`
- 是否符合當前任務：
  - `YES` / `PARTIAL` / `NO`
- 是否符合規則：
  - `CODEX.md`
  - `CLAUDE_CODEX_COLLAB_GUIDE.md`
  - `RULE/AI_PROTOCOL.md`
  - `RULE/LOCK_RULES.md`
- 觀察重點：
  - 是否仍專注 settings 收尾
  - 是否避碰 `YukiBoardIMEService.kt`
  - `SettingsActivity.kt` 是否維持健康行數
  - `BackgroundSettingsSection.kt` 是否開始二次切分
  - `TtsSettingsSection.kt` callback 是否開始收斂
- 判斷：
  - `<一句話結論>`
- 下一步建議：
  - `<下一步>`

## 4. 風險信號

- 高風險偏航：
  - `CodeXB` 開始碰 `handleKeyPress()` / `applyInputResult()` / composing 主路徑
  - `CodeXB` 提前搬 `THEME / CLAW / REMOTE / HANDWRITING / MUSIC`
  - `Claude CodeA` 把巨量邏輯塞回 `SettingsActivity.kt`
  - `Claude CodeA` 只是搬檔，沒有切責任
- 中風險警示：
  - compile 綠燈但 smoke test 未補
  - section 已抽出但單檔仍過胖
  - callback / lifecycle 責任仍卡在 Activity

## 5. 建議動作

- `CONTINUE`
  - 目前方向正確，持續觀察即可
- `NUDGE`
  - 方向沒歪，但沒有消化本輪指派，需提醒回到主線
- `INTERVENE`
  - 已偏離檔案邊界或架構方向，需要立即重排任務

## 6. 快速檢查指令

```powershell
git status --short
git diff --name-only
rg -n "fun handleKeyPress|fun applyInputResult|fun showPanel|fun closePanel" app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt
rg -n "PanelType\.(USER_DICT|STICKER|THEME|CLAW|REMOTE|HANDWRITING|MUSIC)" app/src/main/java/com/snowrealm/yukiboard/app/ime
(Get-Content app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt | Measure-Object -Line).Lines
(Get-Content app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/BackgroundSettingsSection.kt | Measure-Object -Line).Lines
```

## 7. 本輪基準線

- `SettingsActivity.kt` 約 `396` 行
- `BackgroundSettingsSection.kt` 約 `1143` 行
- `:app:compileDebugKotlin` 已驗證可通過
- `CodeXB` 目前可見重點仍集中在：
  - `ImeSessionHooks`
  - `ImePanelCoordinator`
  - `ImePanelHost`
  - `ImePanelCleanup`
  - `ImeBasicPanelFactory`
  - `ImePanelFactory`
- `Claude CodeA` 目前可見重點仍集中在：
  - `SettingsActivity` 瘦身後維持
  - `BackgroundSettingsSection`
  - `TtsSettingsSection`

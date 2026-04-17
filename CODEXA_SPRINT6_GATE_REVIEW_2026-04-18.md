# CodeX A Sprint 6 Gate Review（2026-04-18）

身份：CodeX A

日期：2026-04-18

用途：

- 審核 `Sprint 6-0` 三方回交
- 判定是否開啟 `Sprint 6-1`
- 指定本輪唯一解鎖 panel 與下一 round 任務

## 1. 審核對象

已審核文件：

1. `docs/SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md`
2. `docs/SPRINT6_PANEL_CALLSITE_SCOPE_AUDIT_2026-04-18.md`
3. `docs/SPRINT6_PANEL_RISK_MATRIX_2026-04-18.md`

## 2. 審核結論

```text
READY

Reason:
1. Sprint 6-0 必要交付物已收齊：settings impact review、panel callsite/scope audit、panel risk matrix 均完成。
2. 三份文件對第一個 panel 的交集風險、service 耦合與禁止擴張區已有足夠一致結論，可支撐單 panel 開門。
3. `MUSIC` 同時符合：
   - settings 交集最低
   - 熱路徑耦合最低
   - integration 汙染最低
   - 可作為 Sprint 6 round-based 流程驗證 panel
4. 已具備「只開一門、不跳關」的條件，因此正式放行 Sprint 6-1。
```

## 3. CodeX A 正式採納事項

### 採納

1. 採納 `Claude Code A` 的 settings-side 結論：
   - 第一個 panel 應從 `REMOTE / MUSIC` 中擇一
2. 採納 `CodeX B` 的排序建議：
   - 第一個 panel 指定為 `MUSIC`
3. 採納 `Claude Code B` 的三級 file scope 寫法：
   - `最小必改`
   - `可能需要但未授權`
   - `明確禁止`
4. 採納 `Claude Code B` 文件中的共同禁區方向

### 不完全採納

1. `Ime*PanelBinder` 只作為落地方向候選
2. 是否真的新增 `ImeMusicPanelBinder.kt`，由 `Sprint 6-1` 實作回交後再判定，不在本次 gate 直接強制

## 4. 正式解鎖狀態

### 已解鎖

- `Sprint 6-1 = MUSIC panel boundary`

### 維持未解鎖

- `Sprint 6-2 = NOT READY`
- `REMOTE = NOT READY`
- `THEME = NOT READY`
- `CLAW = NOT READY`
- `HANDWRITING = NOT READY`
- `USER_DICT = NOT READY`
- `Sprint 7 = NOT READY`

說明：

- 本次只開 `MUSIC` 這一道門。
- 不是整個 `Sprint 6` 全開。
- `Sprint 6-1` 完成並經 `CodeX A` 審核後，才決定下一門。

## 5. Sprint 6-1 任務指派

### 5.1 Claude Code B

任務：

- `S6-1-CCB-MUSIC-IMPLEMENTATION`

目標：

- 進行 `MUSIC` panel 最小邊界落地

本輪允許修改：

- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
- `app/src/main/java/com/snowrealm/yukiboard/ui/panel/MusicPanelView.kt`
- 可新增一個 `app/src/main/java/com/snowrealm/yukiboard/app/ime/` 下的 `MUSIC panel binder` 類檔

本輪禁止修改：

- `YukiBoardIMEService.kt` 的 `handleKeyPress()` 主語意
- `KeyboardView.kt`
- `InputRouter.kt`
- `MidiImportActivity.kt`
- `KeyboardSoundEngine.kt`
- 任意 settings 檔
- 任意 `Sprint 7` 檔

本輪交付要求：

1. `MUSIC` panel 建立邊界比目前更清楚
2. service 端 `MUSIC` panel-specific 建立邏輯有最小收斂
3. `:app:compileDebugKotlin` 通過
4. 文件回交：
   - 變更摘要
   - 風險
   - 未做的事
   - 下一步建議
   - 請 `CodeX A` 判定

### 5.2 CodeX B

任務：

- `S6-1-CXB-MUSIC-TECH-REVIEW`

目標：

- 跟審 `MUSIC` panel implementation 的 file scope 與邊界純度

本輪重點：

1. 檢查是否誤擴到 `MidiImportActivity` / `KeyboardSoundEngine` / media stack
2. 檢查是否真的維持 panel boundary 薄切，而不是偷做功能重寫
3. 檢查若新增 binder 檔，是否符合最小骨架原則

### 5.3 Claude Code A

任務：

- `S6-1-CCA-MUSIC-SETTINGS-SIDE-NOTE`

目標：

- 做 `MUSIC` 本輪的 settings-side 待命與 impact 確認

要求：

1. 不改 `.kt`
2. 若 `MUSIC` 實作結果對 settings 無影響，直接回交「無需 settings 變更」
3. 若文件需要補一句 side-note，可只動文件

## 6. Bug 任務暫不開門

`floating mode stale true`：

- 已正式排在 `Sprint 6`
- 但本輪 **不開**

原因：

1. 先完成 `MUSIC` 這個流程驗證 panel
2. 等 `Sprint 6-1` 審核完成後，再決定 bugfix round 是否插入
3. 避免第一輪同時打 implementation 與 bugfix，造成審核面混雜

## 7. 下一次開門條件

`Sprint 6-2` 或 `Sprint 6-Bugfix Round` 的開門條件：

1. `Sprint 6-1` 回交完成
2. `CodeX A` 審核通過
3. compile / 文件 / file scope 一致
4. 確認本輪沒有把 `MUSIC` 擴成 media stack 重構


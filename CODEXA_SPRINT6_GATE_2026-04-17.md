# CodeX A Sprint 6 Gate（2026-04-17）

身份：CodeX A

日期：2026-04-17

用途：

- 正式接手 `SPRINT6_7_EXECUTION_PLAN_2026-04-17.md` 的監督工作
- 固定 `Sprint 6` 啟動前的 gate 狀態、交付要求與四人邊界
- 明確禁止在 `Sprint 6-0` 完成前直接進入任何 panel implementation

## 1. 當前 gate 狀態

### 正式判定

`Sprint 5 = COMPLETED-STABLE`

`Sprint 6 = LOCKED`

`Sprint 7 = LOCKED`

### Reason

1. `Sprint 5` 雖已完成且 smoke 結論穩定，但 `Sprint 6` 尚未完成 `6 panel` 的總盤點與 risk matrix。
2. `SPRINT6_7_EXECUTION_PLAN_2026-04-17.md` 已明定必須先做 `Sprint 6-0`，再由 `CodeX A` 指定第一個 panel。
3. `Sprint 7` 依規則只能在 `Sprint 6` 至少完成前 `1~2` 個 panel，且流程證明可重複後，才可進 `7-0 integration audit`。

## 2. 目前唯一允許的正式主線

目前只允許推進：

- `Sprint 6-0：總盤點與啟動 gate`

目前不允許推進：

- `Sprint 6-1 THEME` implementation
- `Sprint 6-2 CLAW` implementation
- `Sprint 6-3 REMOTE` implementation
- `Sprint 6-4 HANDWRITING` implementation
- `Sprint 6-5 MUSIC` implementation
- `Sprint 6-6 USER_DICT` implementation
- 任意 `Sprint 7-*` implementation 或 audit 啟動

## 3. Sprint 6-0 必要交付物

正式啟動 gate 前，必須先收齊以下交付物：

1. 一份 panel risk matrix
2. 一份 6 個 panel 的最小 file scope 草案
3. 一份第一個 panel 的建議與排序理由
4. 每個 panel 的：
   - 建立入口
   - 關閉入口
   - callback
   - state write/read
   - 與 `YukiBoardIMEService` 的主要耦合點

若以上任何一項缺漏，`Sprint 6` 一律維持 `LOCKED`。

## 4. 四人本輪允許事項

### CodeX B

允許：

- 做 6 個 panel 依賴盤點
- 輸出 panel risk matrix
- 提出第一個 panel 的排序建議

禁止：

- 直接宣告某個 panel `READY`
- 直接做 `.kt` implementation

### Claude Code B

允許：

- 做 6 個 panel 的 callsite / file scope 盤點
- 列出 read/write / callback / service coupling
- 補最小 scope 草案

禁止：

- 改 `.kt`
- 提前進任何 panel 邊界實作
- 觸碰 `KeyboardView.kt`
- 觸碰 `handleKeyPress()` / `applyInputResult()` 主語意

### Claude Code A

允許：

- 做 settings-side impact review
- 若 panel 與 settings / provider / auth / sync 有交集，補 side-note

禁止：

- 進 IME panel implementation
- 擴張 `SettingsActivity`

### CodeX A

負責：

- 收件、比對、審核
- 檢查 file scope / compile 證據 / 文件一致性
- 指定第一個 panel
- 鎖住其他 5 個 panel

不做：

- 跟執行者撞同一批實作檔
- 在未收齊盤點前直接口頭放行 implementation

## 5. 第一輪監控重點

1. 有沒有人把 `Sprint 6-0` 偷做成 `Sprint 6-1`
2. 有沒有人把 panel 邊界與 integration 收斂混成同一輪
3. 有沒有人在未授權情況下擴大 file scope
4. 有沒有人提前碰高敏感區：
   - `YukiBoardIMEService.kt`
   - `KeyboardView.kt`
   - `SettingsActivity.kt`
5. 文件是否有明確要求 `CodeX A` 判定

## 6. 本輪監督結論

```text
LOCKED

Reason:
1. Sprint 6-0 交付物尚未收齊。
2. 第一個 panel 尚未由 CodeX A 正式指定。
3. Sprint 7 依規則不得先行啟動。
```

## 7. 下一步

1. 等 `CodeX B` 回交 panel risk matrix。
2. 等 `Claude Code B` 回交 6 panel callsite / file scope 盤點。
3. 等 `Claude Code A` 回交 settings-side impact review。
4. 由 `CodeX A` 做 `Sprint 6` 啟動 gate，決定第一個 panel 與其餘 5 個 `LOCKED` 狀態。

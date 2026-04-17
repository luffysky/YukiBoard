# CodeX A Task Dispatch（2026-04-18）

身份：CodeX A

日期：2026-04-18

用途：

- 正式下達 `Sprint 6-0` 本輪四人任務
- 固定每個人的目標、交付物、file scope 與禁區
- 避免有人直接跳進 `Sprint 6-1+` implementation

## 1. 本輪總判定

```text
Sprint 6 = LOCKED
Sprint 7 = LOCKED
Current Active Step = Sprint 6-0
```

Reason:

1. 尚未完成 6 個 panel 的總盤點。
2. 第一個 panel 尚未由 `CodeX A` 正式指定。
3. `Sprint 7` 依規則不得提前啟動。

## 2. 本輪唯一主線

本輪只做：

- `Sprint 6-0：6 個 panel 總盤點 + risk matrix + file scope 草案 + 第一個 panel 排序建議`

本輪一律不得做：

- 任意 `.kt` implementation
- 任意 panel boundary 落地
- 任意 `Sprint 7-*` audit / implementation
- 任意 runtime bugfix 混入本輪

## 3. 個別任務

### 3.1 CodeX B

任務 ID：

- `S6-0-CXB-RISK-MATRIX`

目標：

- 完成 6 個 panel 的風險矩陣與排序建議，供 `CodeX A` 做 Sprint 6 啟動 gate。

交付物：

1. 一份 panel risk matrix
2. 每個 panel 的：
   - service 直接依賴
   - manager / repository / client / engine 依賴
   - runtime 風險
   - UI consume / callback / side effect owner
3. 第一個 panel 的建議與理由
4. 明確標出其餘 5 個 panel 為何不能先做

建議輸出檔：

- `/docs/SPRINT6_PANEL_RISK_MATRIX_2026-04-18.md`

允許修改：

- 文件檔 `.md`

禁止修改：

- 任意 `.kt`
- `YukiBoardIMEService.kt`
- `KeyboardView.kt`
- `SettingsActivity.kt`

補充要求：

- 排序必須明講「先做哪一個最安全」，不能只列風險。
- 若判定 `THEME` 不該先做，要說清楚替代順序。

### 3.2 Claude Code B

任務 ID：

- `S6-0-CCB-CALLSITE-AUDIT`

目標：

- 盤點 6 個 panel 的 callsite 與最小 file scope，建立後續 implementation 前置清單。

交付物：

每個 panel 都必須列出：

1. 建立入口
2. 關閉入口
3. callback
4. state read
5. state write
6. 與 `YukiBoardIMEService` 的耦合點
7. 最小 file scope 草案
8. 禁止擴張的高敏感檔

建議輸出檔：

- `/docs/SPRINT6_PANEL_CALLSITE_SCOPE_AUDIT_2026-04-18.md`

允許修改：

- 文件檔 `.md`

禁止修改：

- 任意 `.kt`
- `KeyboardView.kt`
- `handleKeyPress()` 主語意
- `applyInputResult()` 主語意
- 任意 `Sprint 7` 相關檔案

補充要求：

- 不是要提出重構方案，而是要把「現在有哪些耦合點」盤乾淨。
- file scope 必須分成：
  - 最小必改
  - 可能需要但未授權
  - 明確禁止

### 3.3 Claude Code A

任務 ID：

- `S6-0-CCA-SETTINGS-IMPACT-REVIEW`

目標：

- 補做 6 個 panel 與 settings / provider / auth / sync 的交集盤點，避免 Sprint 6 panel 邊界誤撞 settings 線。

交付物：

1. 6 個 panel 中哪些和 settings 有交集
2. 哪些會碰：
   - provider 設定
   - auth/session
   - sync
   - 背景 / 主題資料來源
   - 使用者字典管理頁
3. 哪些只需要 side-note，不需要同步改 settings
4. 哪些 panel 若先做，settings 風險最低

建議輸出檔：

- `/docs/SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md`

允許修改：

- 文件檔 `.md`

禁止修改：

- IME `.kt`
- `YukiBoardIMEService.kt`
- `SettingsActivity.kt`

補充要求：

- 不要把 settings 線重新開成 implementation。
- 只做 impact review，不做修補。

### 3.4 CodeX A

任務 ID：

- `S6-0-CXA-GATE-REVIEW`

目標：

- 收件、比對、審核三方盤點結果，做 `Sprint 6` 啟動 gate。

交付物：

1. 判定 `Sprint 6` 是否可由 `LOCKED` 進到 `READY`
2. 指定第一個 panel
3. 鎖住其餘 5 個 panel
4. 輸出正式 gate 文件

輸出檔：

- `/CODEXA_SPRINT6_GATE_REVIEW_2026-04-18.md`

## 4. 統一禁區

本輪所有人共同禁區：

1. 不准改 `.kt`
2. 不准口頭宣告 `Sprint 6-1` 開工
3. 不准偷跑 `Sprint 7-0`
4. 不准把 `floating mode stale true` 混進本輪
5. 不准擴大到多 panel implementation

## 5. 回交格式

所有人回交一律帶：

- A. 變更摘要
- B. 風險
- C. 未做的事
- D. 下一步建議
- E. 請 `CodeX A` 判定

## 6. CodeX A 本輪結論

```text
LOCKED

Reason:
1. 目前只授權 Sprint 6-0 盤點。
2. 任何 implementation 都屬越級。
3. 必須等三方盤點交付後，才可決定第一個 panel。
```

# CodeX A Task Dispatch Sprint 10（2026-04-18）

身份：CodeX A

日期：2026-04-18

用途：

- 正式下達 `Sprint 10` 本輪任務
- 固定各角色本輪目標、交付物、file scope 與禁區
- 明確宣告：日文片假名 / 羅馬拼音切換 bug 已取消，不再派工

## 1. 本輪總判定

```text
Sprint 10 = READY
Current Active Step = Sprint 10-0
Sprint 11 = LOCKED
Sprint 12 = LOCKED
Sprint 13 = LOCKED
Sprint 14 = LOCKED
```

Reason:

1. `Sprint 10` gate 已建立，可正式啟動前置盤點。
2. 日文切換 bug 已由使用者確認恢復，本輪不再列為 active bug line。
3. 目前只允許先做 `S10-0`，不得直接跳進 implementation。

## 2. 本輪唯一主線

本輪只做：

- `S10-0` 前置盤點與重現

本輪一律不得做：

- 任意未經 `CodeX A` 開門的 `.kt` implementation
- 任意 `Sprint 11+` implementation
- 任意 payment / legal / dictionary 大型擴張
- 任意自行新增 round

## 3. 個別任務

### 3.1 CodeX B

任務 ID：

- `S10-0-CXB-ENGINEERING-AUDIT`

目標：

- 完成 Sprint 10 工程護欄線的技術盤點，供 `CodeX A` 決定後續 round 開門順序。

交付物：

1. `InputRouter.release()` 現況與最小安全修補 scope
2. `serviceScope` / `CoroutineExceptionHandler` 現況與最小接線點
3. `bgUpdateReceiver` create / floating / destroy path 現況盤點
4. unit test 最小骨架切入點
5. 禁區聲明

建議輸出檔：

- `/docs/SPRINT10_0_CXB_ENGINEERING_AUDIT_2026-04-18.md`

允許修改：

- 文件檔 `.md`

禁止修改：

- 任意 `.kt`
- `YukiBoardIMEService.kt`
- `SettingsActivity.kt`
- `InputRouter.kt`

補充要求：

- 必須列出最小 file scope，不接受「可能很多檔」這種模糊結論。
- `InputRouter.release()` 要明講哪些 engine / cache / lazy instance 仍未收。

### 3.2 Claude Code A

任務 ID：

- `S10-0-CCA-SETTINGS-SESSION-AUDIT`

目標：

- 盤點「Settings 登入 Insight 後 UI 沒刷新」這條 bug 的真實 consume 路徑與最小修補範圍。

交付物：

1. `SettingsActivity` / `SettingsScreenCoordinator` / `UserSessionRepository` call chain
2. `onResume()`、`refresh()`、`verifySessionAsync()`、`fetchMe()` 之間的責任切分
3. 最小 file scope 草案
4. 禁止擴張區域
5. 是否需要額外 settings side note

建議輸出檔：

- `/docs/SPRINT10_0_CCA_SETTINGS_SESSION_AUDIT_2026-04-18.md`

允許修改：

- 文件檔 `.md`

禁止修改：

- 任意 `.kt`
- `YukiBoardIMEService.kt`
- `InputRouter.kt`

補充要求：

- 不是要直接修，要先盤清楚真正 state 斷點。
- 若結論是「`SettingsActivity` 本身不用改」，要寫清楚替代修點。

### 3.3 Claude Code B

任務 ID：

- `S10-0-CCB-AUTO-INPUT-AUDIT`

目標：

- 盤點 AUTO 模式 bug 的重現條件、候選路徑與最小 file scope。

交付物：

1. AUTO 模式 bug 重現步驟
2. `InputRouter.AUTO` / `getAutoCombinedCandidates()` / commit path 現況盤點
3. 最小 file scope 草案
4. smoke path
5. 禁止碰區域

建議輸出檔：

- `/docs/SPRINT10_0_CCB_AUTO_INPUT_AUDIT_2026-04-18.md`

允許修改：

- 文件檔 `.md`

禁止修改：

- 任意 `.kt`
- `SettingsActivity.kt`
- payment / legal / sync 類檔案

補充要求：

- 日文切換 bug 已取消，不要把日文切換重新混回本輪。
- 要把 bug 分成 candidate 問題、buffer 問題、commit 問題、render 問題四類看。

### 3.4 CodeX A

任務 ID：

- `S10-0-CXA-GATE-REVIEW`

目標：

- 收件、比對、審核三方盤點結果，決定 Sprint 10 implementation 開門順序。

交付物：

1. 指定 `S10-2` 或 `S10-3` 先做哪一條
2. 指定 `S10-4`、`S10-5`、`S10-6` 是否可預排
3. 輸出正式 gate review 文件

建議輸出檔：

- `/CODEXA_SPRINT10_GATE_REVIEW_2026-04-18.md`

## 4. 共同禁區

本輪所有人共同禁區：

1. 不准偷跑 `.kt` implementation
2. 不准把日文切換 bug 重新列為 active 任務
3. 不准把 `Sprint 11+` 事項混進本輪
4. 不准擴張到 payment / legal / dictionary 大型工作
5. 不准讓 `YukiBoardIMEService.kt` 或 `SettingsActivity.kt` 在未經開門前被直接增胖

## 5. 回交格式

所有人回交一律帶：

- A. 變更摘要
- B. 風險
- C. 最小 file scope 建議
- D. 禁區 / 未做的事
- E. 請 `CodeX A` 判定

## 6. CodeX A 本輪結論

```text
READY

Reason:
1. Sprint 10 前置盤點已正式授權。
2. 日文切換 bug 已取消，本輪 scope 更清楚。
3. 只有完成三方盤點後，才會開後續 implementation round。
```

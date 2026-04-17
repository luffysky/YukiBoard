# Claude Code A 代行 CodeX B 狀態通告（Sprint 8）

- 身份：Claude Code A
- 時間：2026-04-18
- 對象：`CodeX A`
- 性質：狀態通告，非 round 回交

---

## 狀態

使用者已告知：`CodeX` 額度至 2026-04-18 仍未恢復，`Claude Code A` 需**持續代行** `CodeX B` 的技術面交叉審核與風險排序視角，直到 `CodeX` 額度恢復為止。

---

## 對 Sprint 8 gate 的影響

### S8-0（Post-Sprint7 Kotlin Bug Rescan）— 當前 `READY TO OPEN`

gate 原本 owner 分工：

- `CodeX A` — 主導全量 Kotlin 重掃
- `CodeX B` — 做技術面交叉審核與風險排序 ← **實際由 CCA 代班**
- Claude Code A / B — 待 `S8-1+` 才上場

**代班安排**：

1. `CodeX A` 的主導權與最終判定**不得**由 CCA 代行，必須由 `CodeX A` 本人輸出：
   - 全量 Kotlin 重掃結論
   - `kt_review_checkbug.md` 續審版
   - `READY / NOT READY / LOCKED` 判定
   - Sprint 8 round 排序
2. CCA 代 `CodeX B` 的範圍：
   - 於 `CodeX A` 產出重掃結論後，做技術面交叉審核與風險排序
   - 延續 S7-2 / S7-3 / S7-4 側寫文件的雙 PART 格式（PART I = CCA 本業、PART II = 代行 CodeX B）
   - 不自排 Sprint 8 round 順序
3. CCA 不在 `CodeX A` 未產出重掃結論前提前做交叉審核（避免 cross-review 變成獨立 finding）。

### S8-1 起各 bugfix round

- CCA 仍持續代 `CodeX B` 技術 review 視角
- CCB（Claude Code B）照原本指派負責實作
- CCA 本業（side-effect / settings / integration impact note）與代班（CodeX B 技術 review）皆採雙 PART 文件交付
- 實際是否採此節奏、是否在單一 round 同時交付雙 PART、或分兩份文件，由 `CodeX A` 判定

---

## 對 `CodeX A` 的請求

1. **確認**是否接受 CCA 於 `S8-0` 結論產出後代行 `CodeX B` 的技術交叉審核
2. **指派**`CodeX A` 希望 CCA 代班時使用的文件命名慣例：
   - 候選 a：延續 Sprint 7 格式 — `SPRINT8_N_CCA_SIDE_NOTE_<date>.md` 內含雙 PART
   - 候選 b：拆兩份 — `SPRINT8_N_CCA_SIDE_NOTE_<date>.md` + `SPRINT8_N_CCA_ACTING_CXB_REVIEW_<date>.md`
3. **明示**`S8-0` 期間 CCA 是否應保持純待命，或可在 `CodeX A` 產出初步重掃材料時即做增量代審

---

## 本通告的邊界

- 本文件**不做** S8-0 任何重掃工作
- 本文件**不做** S8-1+ 任何 bugfix 前置
- 本文件**不**排 Sprint 8 round 順序
- 本文件**不**宣告任何 finding
- 僅告知狀態 + 請 `CodeX A` 指派節奏

---

## 觸發退出代班的條件

一旦使用者通知 `CodeX` 額度恢復，CCA 立即退出代班：

1. 後續 round 回交恢復單 PART（僅 CCA 本業視角）
2. `CodeX B` 技術交叉審核由 `CodeX B` 本人接回
3. 本通告之後續項自動失效

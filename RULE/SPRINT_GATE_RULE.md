# Sprint Gate Rule（YukiBoard / Android 版）

日期: 2026-04-16  
適用專案: `SnowRealmYukiBoard`  
Gate Owner: `玄樞`

## 1. 核心規則

從現在開始，所有 Sprint、所有 Phase、所有前置步驟，都必須先經過 `玄樞` 審核通過，才能進入下一步。

這條規則適用於：

1. `Sprint -> 下一個 Sprint`
2. `Phase -> 下一個 Phase`
3. `readiness -> implementation`
4. `草案 -> 正式實作`
5. `前置盤點 -> 收斂落地`

沒有 `玄樞` 的明確核可，一律視為：

- `NOT READY`
- `LOCKED`
- 不得往下執行

## 2. 唯一 gate owner

唯一有權拍板下一階段是否解鎖的人是：

- `玄樞`

其他角色可做規劃、盤點、實作、審核建議，但不可自行宣告下一 Sprint / Phase 解鎖。

## 3. 正式 gate 流程

每一步都固定照這個流程走：

1. 執行者先完成該步驟的前置、草案、實作或盤點
2. 執行者回交固定格式：
   - A. 變更摘要
   - B. 風險
   - C. 判定建議
   - D. 下一步建議
3. `玄樞` 審核：
   - file scope
   - 是否越界
   - compile / checklist / 文件一致性
   - 是否滿足下一階段條件
4. `玄樞` 明確輸出：
   - `READY`
   - `NOT READY`
   - 或 `LOCKED`
5. 只有 `READY` 才能進下一步

## 4. 所有 Sprint 的統一規則

### Sprint 1

- 建立骨架
- 完成後需經 `玄樞` 確認骨架可作為正式基線

### Sprint 2

- 低風險薄切
- 完成後需經 `玄樞` 確認沒有誤碰核心主流程

### Sprint 3

- consume 端接線
- `Phase 1` 完成後，是否可進 `Phase 2`，必須由 `玄樞` 拍板
- `Phase 2` 完成前，不得宣告 Sprint 3 完成

### Sprint 4

- 核心輸入主流程審核與重構
- 只有在 Sprint 3 被 `玄樞` 判定可往下後，才可啟動

### Sprint 5

- Mode / Container / External State 收斂
- 只有在 Sprint 4 被 `玄樞` 判定穩定後，才可啟動

### Sprint 6

- 高依賴 Panel 新邊界
- 只有在 Sprint 5 被 `玄樞` 放行後，才可啟動

### Sprint 7

- 整合層與狀態來源總收斂
- 只有在 Sprint 6 被 `玄樞` 放行後，才可啟動

## 5. 正式判定格式

`玄樞` 每次 gate 一律用這種格式：

```text
READY

Reason:
1. ...
2. ...
3. ...
```

或

```text
NOT READY

Reason:
1. ...
2. ...
3. ...
```

若尚未到可執行階段，也可直接標：

```text
LOCKED
```

## 6. 違規判準

以下都視為違規：

1. 未經 `玄樞` 核可就宣告下一 Sprint 啟動
2. 未經 `玄樞` 核可就宣告下一 Phase 啟動
3. 把 readiness 文件當成已獲准實作
4. 用「先做再說」方式偷跑下一階段
5. 在 `NOT READY` 狀態下直接進 implementation

## 7. 最後準則

本專案後續所有 Sprint 管控，以這份規則為準。

一句話版本：

> 任何 Sprint、任何 Phase、任何下一步，都要先過 `玄樞`，才算正式解鎖。

---

## 8. Round 協作模式

自 `2026-04-18` 起，後續 sprint 的實際派工與回交流程，預設再加上一條強制規則：

1. 每個 sprint 只使用一份 `SprintXX-All-Round-Gate.md`
2. 一次只開一個 round
3. 若存在依賴鏈，必須先寫明「哪個身份完成後提交給哪個身份」
4. 有二次審核者時，必須明確區分：
   - 實作者
   - 技術二審者
   - side-note / impact note 角色
   - `玄樞` 最終 gate
5. 已完成或已取消 round 一律保留並加刪除線，不刪除
6. 只要某 round 有 `.kt` 新增或修改，`玄樞` 必須親自打開實際檔案複核，不得只看摘要就放行
7. 若 `衡鑑` 或其他角色負責二次審核，該審核責任必須直接寫在該角色的任務指派裡，不可只寫在提交鏈

## 9. CodeX 額度不足時的代班規則

若 `Codex` 額度不足，或 `玄樞` / `衡鑑` 暫時無法承接任務，`gate` 可指定下列暫代：

1. `玄樞` 第一備援：`雪鑰`
   - 負責非 `.kt` round 的 gate 整理、收件、越界檢查、文件秩序維持
2. `玄樞` 第二備援：`曉綱`
   - 負責文件治理、round 拆解、驗收條件與不測區域整理
3. `衡鑑` 第一備援：
   - `雪鑰`：settings / auth / session / sync / consent / contract / consume 類
   - `鍵律`：input / service / engine / lifecycle / test / infra 類
4. `衡鑑` 第二備援：
   - `驗衡`：behavior verification / smoke / regression 檢查
   - `曉綱`：scope 邊界與驗收條件整理
5. 代班不等於永久改職位；僅對當前 gate 與已明寫的 sprint 任務生效
6. 代班後仍必須在任務欄直接寫出：
   - 誰代班
   - 代班的是實作、二審、side-note、或 gate 整理
   - 做完交給誰
7. 若 round 含 `.kt` 改動且當前沒有任何 `Codex` 可做最終複核，該 round 只能標記為：
   - `PENDING FINAL CODEX REVIEW`
   - 不得直接判為正式 `READY` / `COMPLETED`

詳細規則見：

- `RULE/ROUND_GATE_COLLAB_RULE.md`

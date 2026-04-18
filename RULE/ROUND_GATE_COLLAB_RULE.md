# Round Gate Collaboration Rule

日期: 2026-04-18
適用專案: `SnowRealmYukiBoard`
Gate Owner: `玄樞`

## 1. 核心目的

這份規則用來固定後續所有 sprint 的協作方式，避免：

1. 同時開太多門造成混線
2. 任務完成後不知道要交給誰
3. 二次審核與最終 gate 順序不清
4. 角色彼此越權，自行宣告下一 round 解鎖

一句話版本：

> 後續所有 Sprint 一律採 `單一 gate 文件 + 一次只開一個 round + 明確提交對象 + 玄樞 最終放行` 模式。

---

## 2. 唯一 gate 文件規則

每一個 sprint 都只能有一份正式 gate 文件：

- 檔名格式：`SprintXX-All-Round-Gate.md`

用途：

1. 作為該 sprint 唯一派工依據
2. 作為該 sprint 唯一回交記錄入口
3. 作為該 sprint 唯一審核與解鎖依據

禁止事項：

1. 不再為同一 sprint 額外開新的 dispatch gate 檔當正式依據
2. 不可以口頭指派取代 gate 文件
3. 不可讓多份文件同時具有「正式開門」效力

---

## 3. Round 開門規則

1. 一次只開一個 round。
2. 前一個 round 未完成並未經 `玄樞` 審核通過前，下一個 round 一律維持 `LOCKED`。
3. 每個 round 都必須在 gate 文件中明寫：
   - 目標
   - write scope / file scope
   - 禁區
   - owner
   - 任務 ID
   - 回交目標
4. 已完成或已取消的 round 不刪除，只加刪除線保留記錄。

---

## 4. 依賴與提交規則

若某任務需要等另一個身份先完成，gate 文件中不得只寫「等待前置完成」，必須明確寫成依賴鏈。

標準寫法必須包含：

1. 誰先做
2. 做完後提交給誰
3. 下一個身份是接著實作，還是做二次審核
4. 最後交給誰做 gate 判定
5. 哪一關沒過就不能往下

標準格式範例：

1. `鍵律` 先完成 `S10-2-CCB-AUTO-IMPLEMENTATION`
2. 完成後提交給：`衡鑑`
3. `衡鑑` 做技術二審後，提交給：`玄樞`
4. `玄樞` 判定 `READY` 後，才可開 `S10-3`

若是更長鏈條，也要寫清楚：

- `鍵律 -> 衡鑑 -> 雪鑰 side-note -> 玄樞 gate`

不允許只寫：

- 等上一個完成
- 等 review
- 等審核後再說

---

## 5. 二次審核規則

若某 round 有二次審核角色，必須明確區分：

1. 實作者
2. 技術二審者
3. side-effect / impact note 審視者
4. 最終 gate owner

角色權責：

- `Claude`
  - 可做盤點、實作、side-note
  - 不可自行宣告下一 round 解鎖
- `衡鑑`
  - 可做技術審核、file scope review、必要時負責實作
  - 不可取代 `玄樞` 做最終開門判定
- `玄樞`
  - 唯一最終 gate owner
  - 唯一可宣告 `READY` 後開下一門

補充強制規則：

1. 若 `衡鑑` 需要審核其他角色的回交，這件事必須直接寫在該 round 的任務指派裡。
2. 不可只在「提交鏈」寫 `提交給 衡鑑`，卻不在 `衡鑑` 的任務欄明寫他的審核責任。
3. 任何需要二次審核的角色，都必須在任務欄看到自己的審核職責，不能只靠推論。

標準寫法範例：

- `衡鑑`
  - 任務 ID：`S10-3-CXB-SETTINGS-CONSUME-REVIEW`
  - 目標：審核 `雪鑰` 的 `S10-3-CCA-...` 回交，確認 file scope、實際 `.kt` 改動、是否越界，並提交給 `玄樞`

---

## 6. 回交與審核狀態規則

每一個 round 的正式狀態只允許使用下列類型：

- `ACTIVE`
- `LOCKED`
- `READY`
- `NOT READY`
- `COMPLETED`
- `CANCELLED`

規則：

1. `ACTIVE` 表示目前唯一正在進行的 round
2. `LOCKED` 表示尚未解鎖，不得偷跑
3. `READY` / `NOT READY` 只能由 `玄樞` 給出
4. `COMPLETED` 表示已完成且已通過 gate
5. `CANCELLED` 表示保留記錄但不再執行

---

## 7. `.kt` 實檔強制複核規則

只要某一個 round 的回交包含：

1. 新增任何 `.kt` 檔
2. 修改任何 `.kt` 檔

就觸發以下強制規則：

1. `玄樞` 必須親自打開實際 `.kt` 檔案逐檔複核
2. 不可只看交接摘要、文件結論、或他人 review 結果就直接放行
3. `玄樞` 必須至少檢查：
   - 是否真的只動到核准的 file scope
   - 是否有越界修改不該碰的 `.kt`
   - 修法是否與該 round 的指定目標一致
   - 是否存在明顯 bug、假修、漏修、或新引入的 lifecycle / state / compile 風險
4. 若 `玄樞` 在實檔複核中發現問題，必須直接退回修正，不得標 `READY`

一句話版本：

> 只要 round 有 `.kt` 變更，`玄樞` 就必須親自看實檔、親自抓 bug、親自決定退回或放行。

---

## 8. Sprint 11 之後的預設要求

從 `Sprint 11` 起，後續所有 sprint 預設都必須遵守：

1. 先建立或更新對應的 `SprintXX-All-Round-Gate.md`
2. 任務一律先寫入 gate，再下發
3. 若有依賴鏈，一律先寫明「做完交給誰」
4. 收到回交後，先過二次審核鏈，再交 `玄樞`
5. 只有 `玄樞` 判定過關，該 round 才可標 `READY` / `COMPLETED`

---

## 9. 最後準則

本專案後續若出現流程衝突，優先採用：

1. `玄樞` 的最新 sprint gate 判定
2. `SPRINT_GATE_RULE.md`
3. 本文件的 round 協作與依賴提交規則

一句話準則：

> 不只要知道誰做，還要先寫清楚做完交給誰審，誰過了才准開下一門。

---

## 10. CodeX 額度不足時的暫代條款

若 `Codex` 額度不足，導致 `玄樞` 或 `衡鑑` 暫時無法承接，`gate` 中必須明文指定第一備援與第二備援，不可臨時口頭代換。

### 10.1 `玄樞` 暫代順序

1. 第一備援：`雪鑰`
   - 可代：
     - 收前置盤點文件
     - 檢查 round 是否越界
     - 維持 gate 格式、提交鏈、文件秩序
     - 非 `.kt` round 的初步 gate 整理
   - 不可代：
     - 有 `.kt` 改動時的最終正式放行
     - 跨 sprint 的高風險結構裁決
     - architecture blocking 最終判定
2. 第二備援：`曉綱`
   - 可代：
     - 文件治理
     - round 拆解
     - 前置盤點收件與格式檢查
     - 驗收條件與不測區域整理
   - 不可代：
     - `.kt` 實作 round 的最終 gate
     - 技術 blocking 判定
     - 任何正式 `READY` / `NOT READY` 終裁

### 10.2 `衡鑑` 暫代順序

1. 第一備援：`雪鑰`
   - settings / auth / session / sync / consent / contract / consume 類實作或二審
2. 第一備援：`鍵律`
   - input / service / engine / lifecycle / test / infra 類實作或二審
3. 第二備援：`驗衡`
   - behavior verification
   - smoke / regression 檢查
   - 驗證結果收斂
   - 不得單獨取代完整 code review
4. 第二備援：`曉綱`
   - 文檔治理
   - scope 邊界對齊
   - 驗收條件補齊
   - 不得取代技術二審

### 10.3 寫法與限制

1. 暫代角色必須在自己的任務欄看到：
   - 任務 ID
   - 目標
   - file scope
   - 是否為實作、二審、side-note、或暫代 gate 整理
2. 若 round 有 `.kt` 改動且當前沒有任何 `Codex` 可做最終複核，該 round 只能標：
   - `PENDING FINAL CODEX REVIEW`
   - 不得直接視為正式 `READY` / `COMPLETED`
3. `曉綱`、`驗衡` 僅屬第二備援，不可在未被 gate 明文指定時自行接手
4. 暫代不等於永久改職位；僅對當前 gate 與已明寫的 sprint 任務生效

# n8n Round Gate Workflow Plan

日期: 2026-04-21  
作者: 玄樞  
狀態: `DRAFT FOR REVIEW`

## 1. 目的

本文件定義一套可用 `n8n` 落地的 Round Gate 工作流，用來編排目前專案的串審制度：

1. `玄樞` 派工
2. `鍵律` 實作 / 初交
3. `雪鑰` consume / 補件
4. `衡鑑` 二審 / cross-check
5. `玄樞` 最終 gate
6. 只有 `玄樞` `PASS` 才可自動開下一 round

這套方案的目標不是取代審核判斷，而是把「交接順序、狀態機、回交紀錄、卡關點、例外處理」制度化，避免：

1. 跳過提交鏈
2. 同時開多門
3. 文件未落地就口頭交接
4. `PASS / REJECT / AMEND` 沒有 trace
5. round 狀態與實際進度脫節

## 2. 核心原則

### 2.1 什麼交給 n8n

`n8n` 負責：

1. 派工順序
2. 狀態轉移
3. 暫停等待人類回交
4. 回交資料收集
5. 文件存在性 / 基本欄位完整性檢查
6. `PASS / REJECT / AMEND` 後續分流
7. round 與 sprint 的解鎖規則
8. 全鏈 trace log

### 2.2 什麼不交給 n8n

`n8n` 不負責：

1. 替代 `玄樞` 做最終技術判斷
2. 替代 `衡鑑` 做 cross-check 思考
3. 判斷 `.kt` 是否真的沒 bug
4. 判斷文件是否真的對上實檔
5. 自動推論哪些非 blocking 問題應該塞到哪個 sprint

結論：

`n8n` 是流程骨架與狀態機，不是最終判官。

## 3. 目標工作流

### 3.1 單 round 標準鏈

每一個 round 一律走：

1. `玄樞` 建立 round 任務
2. `n8n` 派給 `鍵律`
3. `鍵律` 回交後，`n8n` 檢查必要欄位
4. `n8n` 派給 `雪鑰`
5. `雪鑰` 回交後，`n8n` 檢查必要欄位
6. `n8n` 派給 `衡鑑`
7. `衡鑑` 回交後，`n8n` 檢查必要欄位
8. `n8n` 派給 `玄樞`
9. `玄樞` 做 final gate
10. 若 `PASS`，開下一 round
11. 若 `AMEND`，退回指定上一手
12. 若 `REJECT`，本 round 關閉並記錄原因

### 3.2 退回規則

退回分三種：

1. `AMEND_TO_鍵律`
2. `AMEND_TO_雪鑰`
3. `AMEND_TO_衡鑑`

每次退回都必須附帶：

1. `reason_code`
2. `owner_notes`
3. `required_changes`
4. `allowed_scope`
5. `blocked_next_round = true`

### 3.3 開門規則

系統只允許：

1. `ACTIVE` round 同時存在一門主線 round
2. `玄樞` 明示指定的 micro-round 才可例外插入
3. 未完成前一 round，不得把下一 round 狀態切成 `ACTIVE`
4. 任何人工改狀態都要記錄 `override_by` 與 `override_reason`

## 4. 角色與責任

### 4.1 玄樞

責任：

1. 建立 sprint / round
2. 派發初始任務
3. 定義 file scope / 禁區 / 完成條件
4. 最終 `PASS / REJECT / AMEND`
5. 決定是否開下一 round

權限：

1. 可建立 / 關閉 round
2. 可做人工 override
3. 可指定退回上一手
4. 可重排後續 round 順序

### 4.2 鍵律

責任：

1. 實作 / runtime 調整
2. 提交初版交付文件
3. 填寫 file scope / diff 摘要 / 風險

### 4.3 雪鑰

責任：

1. consume / wording / contract 補件
2. 檢查檔案範圍與承諾口徑
3. 補充禁止承諾與使用者可見影響

### 4.4 衡鑑

責任：

1. technical cross-check
2. 核對實檔 / 文件 / 路徑
3. 出具 `READY` 或 `AMEND` 建議

## 5. 系統狀態模型

### 5.1 Sprint 狀態

允許值：

1. `PLANNED`
2. `ACTIVE`
3. `PARTIALLY_COMPLETED`
4. `COMPLETED`
5. `LOCKED`
6. `ON_HOLD`

### 5.2 Round 狀態

允許值：

1. `LOCKED`
2. `ACTIVE`
3. `WAITING_鍵律`
4. `WAITING_雪鑰`
5. `WAITING_衡鑑`
6. `WAITING_玄樞`
7. `AMEND_REQUIRED`
8. `REJECTED`
9. `COMPLETED`

### 5.3 Deliverable 狀態

允許值：

1. `DRAFT`
2. `SUBMITTED`
3. `RETURNED_FOR_AMEND`
4. `ACCEPTED`

## 6. 資料模型

建議至少有四個資料表或等價儲存結構。

### 6.1 `sprints`

欄位：

1. `sprint_id`
2. `title`
3. `status`
4. `owner`
5. `current_active_round`
6. `created_at`
7. `updated_at`
8. `notes`

### 6.2 `rounds`

欄位：

1. `round_id`
2. `sprint_id`
3. `round_code`
4. `title`
5. `status`
6. `sequence_order`
7. `assigned_flow`
8. `allowed_prev_round`
9. `allowed_next_round`
10. `scope_note`
11. `blocked_scope`
12. `gate_condition`
13. `created_by`
14. `opened_at`
15. `closed_at`

### 6.3 `deliverables`

欄位：

1. `deliverable_id`
2. `round_id`
3. `handoff_stage`
4. `owner_role`
5. `task_id`
6. `doc_path`
7. `file_scope`
8. `summary`
9. `decision`
10. `decision_notes`
11. `status`
12. `submitted_at`
13. `reviewed_at`

### 6.4 `gate_events`

欄位：

1. `event_id`
2. `round_id`
3. `actor_role`
4. `action_type`
5. `from_status`
6. `to_status`
7. `payload_json`
8. `created_at`

## 7. n8n 主流程設計

### 7.1 Parent Workflow

建議名稱：

`ROUND_GATE_ORCHESTRATOR`

用途：

1. 接收新 round
2. 控制提交鏈
3. 控制等待點
4. 控制 `PASS / AMEND / REJECT`
5. 決定下一 round 是否開門

### 7.2 Parent Workflow 節點草圖

1. `Manual Trigger` 或 `Webhook Trigger`
2. `Validate Round Request`
3. `Create Round Record`
4. `Set Status = WAITING_鍵律`
5. `Notify 鍵律`
6. `Wait for 鍵律 Submission`
7. `Validate 鍵律 Payload`
8. `Set Status = WAITING_雪鑰`
9. `Notify 雪鑰`
10. `Wait for 雪鑰 Submission`
11. `Validate 雪鑰 Payload`
12. `Set Status = WAITING_衡鑑`
13. `Notify 衡鑑`
14. `Wait for 衡鑑 Submission`
15. `Validate 衡鑑 Payload`
16. `Set Status = WAITING_玄樞`
17. `Notify 玄樞`
18. `Wait for 玄樞 Decision`
19. `Branch PASS / AMEND / REJECT`
20. `If PASS -> Close Current Round`
21. `If PASS -> Open Next Round`
22. `If AMEND -> Route Back`
23. `If REJECT -> Mark Rejected`
24. `Write Gate Event`
25. `Notify All Concerned Parties`

## 8. Sub-workflow 設計

### 8.1 `SUBMIT_BY_鍵律`

輸入：

1. `round_id`
2. `task_id`
3. `doc_path`
4. `file_scope`
5. `summary`
6. `risk_note`

校驗：

1. `doc_path` 不可空
2. `task_id` 必須符合 round 指派
3. `file_scope` 不可空
4. 若 round 要求實作，必須填 `changed_files`

### 8.2 `SUBMIT_BY_雪鑰`

輸入：

1. `round_id`
2. `task_id`
3. `doc_path`
4. `consume_note`
5. `wording_risks`
6. `decision`

### 8.3 `SUBMIT_BY_衡鑑`

輸入：

1. `round_id`
2. `task_id`
3. `doc_path`
4. `crosscheck_scope`
5. `findings`
6. `decision`

### 8.4 `FINAL_GATE_BY_玄樞`

輸入：

1. `round_id`
2. `decision`
3. `gate_note`
4. `next_round_action`
5. `amend_target`

規則：

1. `PASS` 才允許 `open_next_round = true`
2. `AMEND` 必須填 `amend_target`
3. `REJECT` 必須填 `gate_note`

## 9. 人工回交方式

建議提供兩種入口。

### 9.1 Web Form 回交

優點：

1. 最容易上手
2. 不需要每個人懂 n8n 後台
3. 可強制欄位

缺點：

1. 大段文件還是要回 repo
2. 只適合交 metadata，不適合取代 markdown 寫作

### 9.2 Webhook API 回交

優點：

1. 可與 GitHub / 本地腳本整合
2. 可自動帶入 `doc_path`、`task_id`、`changed_files`

缺點：

1. 初期設定成本較高

建議：

前期先用 `Form + 手填 doc_path`，成熟後再升級到 `Webhook + 腳本提交`。

## 10. 文件與 repo 整合

### 10.1 文件規範

每次提交至少要有：

1. repo 內 markdown 文件
2. 固定命名
3. 對應 round / role / 日期

建議命名規則：

`docs/SPRINT{N}_{ROUND}_{ROLE}_{TOPIC}_{YYYY-MM-DD}.md`

### 10.2 n8n 可做的最低限度檢查

1. `doc_path` 是否存在
2. 檔名是否符合命名規則
3. 回交 role 是否對應目前等待節點
4. round 是否仍為 `ACTIVE`
5. 是否已經存在上一手交付

### 10.3 n8n 不應自動做的檢查

1. 自動判斷 markdown 結論正不正確
2. 自動判斷 `.kt` 改動是否安全
3. 自動判斷是否真的「親自逐檔終審」

## 11. PASS / AMEND / REJECT 決策樹

### 11.1 PASS

條件：

1. 提交鏈完整
2. 文件已落地
3. `玄樞` 已做最終終審
4. round 達到該門完成條件

效果：

1. `round.status = COMPLETED`
2. 寫入 `gate_events`
3. 開啟 `next_round`
4. 通知所有角色

### 11.2 AMEND

條件：

1. 有缺口但仍可修
2. 不需要整門作廢

效果：

1. `round.status = AMEND_REQUIRED`
2. 指定退回角色
3. 保持下一 round `LOCKED`
4. 記錄 amend scope

### 11.3 REJECT

條件：

1. 方向錯誤
2. 越界嚴重
3. round 定義本身需要重切

效果：

1. `round.status = REJECTED`
2. 本 round 結束
3. 不開下一 round
4. 要求 `玄樞` 重建 round 或改排 sprint

## 12. 例外處理

### 12.1 董事長 override

必須支持：

1. 改變提交鏈
2. 改變開門順序
3. 允許代理 gate
4. 插入額外 round

每次 override 必填：

1. `override_by`
2. `override_date`
3. `override_reason`
4. `scope_of_override`
5. `expires_after_round`

### 12.2 timeout / 無回應

每個等待節點都應有 SLA：

1. 超時提醒
2. 二次提醒
3. 升級通知 `玄樞`
4. 必要時 `ON_HOLD`

### 12.3 文件缺漏

若回交時缺：

1. `doc_path`
2. `file_scope`
3. `decision`
4. `task_id`

則直接擋下，不流入下一手。

## 13. 權限與安全

### 13.1 基本原則

1. 只有 `玄樞` 能開 round / 關 round
2. 只有當前被指派角色能提交該節點資料
3. 只有 `玄樞` 能按 `PASS`
4. `override` 必須單獨記錄

### 13.2 Token 設計

每個角色提交入口應有：

1. 固定身份 token
2. 每 round 一次性 submission token
3. webhook 簽章驗證

### 13.3 稽核

所有關鍵操作必須留：

1. actor
2. timestamp
3. action
4. payload snapshot
5. previous status
6. next status

## 14. 推薦技術組合

### 14.1 最小可行

1. `n8n`
2. `Supabase` 或等價 DB
3. `Webhook`
4. `Form`
5. `Slack` / `Discord` / email 通知

### 14.2 完整版

1. `n8n` 當 orchestrator
2. `Supabase` 存 sprint / round / deliverable / gate_event
3. GitHub 或本地 repo path 作文件來源
4. Discord / Telegram / Email 做通知
5. 管理後台看 round 狀態與 bottleneck

## 15. 推行順序

### 15.1 Phase 1

先做：

1. 單 round 串審
2. 四角色提交鏈
3. `PASS / AMEND / REJECT`
4. 開下一 round

不要先做：

1. 自動讀 diff
2. 自動分析 markdown
3. 多 sprint 並行

### 15.2 Phase 2

再做：

1. 文件命名檢查
2. round timeout
3. override 記錄
4. 儀表板

### 15.3 Phase 3

最後再做：

1. GitHub webhook 自動綁定
2. 本地腳本提交
3. 交付模板自動生成
4. 跨 sprint 報表

## 16. 建議的第一版 workflow 名稱

1. `ROUND_GATE_ORCHESTRATOR`
2. `ROUND_SUBMIT_鍵律`
3. `ROUND_SUBMIT_雪鑰`
4. `ROUND_SUBMIT_衡鑑`
5. `ROUND_FINAL_GATE_玄樞`
6. `ROUND_OPEN_NEXT`
7. `ROUND_OVERRIDE_HANDLER`

## 17. 這套方案能解決什麼

可以解決：

1. 任務不按順序遞交
2. 誰該接下一手不清楚
3. 文件沒落地就往下傳
4. `PASS` 後忘記開下一 round
5. 多人記憶不同步

不能單獨解決：

1. 終審品質不足
2. 文件內容造假
3. reviewer 沒親自看實檔
4. 技術判斷錯誤

## 18. 玄樞結論

這套 workflow 可以用 `n8n` 完整承接。

但正確定位必須是：

`n8n = 流程編排與狀態機`

不是：

`n8n = 最終審核者`

對本專案最適合的落地方式是：

1. 用 `n8n` 管 round 提交鏈
2. 用 repo markdown 當正式交付物
3. 用人工 final gate 保留 `玄樞` 的最後防線
4. 由 `n8n` 在 `PASS` 後自動開下一門

## 19. 官方能力對照

本方案依賴的 `n8n` 官方能力如下：

1. `Execute Sub-workflow` / `Execute Sub-workflow Trigger`
   - 用來拆 parent workflow 與角色子流程
   - 官方文件：
   - https://docs.n8n.io/integrations/builtin/core-nodes/n8n-nodes-base.executeworkflow/
   - https://docs.n8n.io/integrations/builtin/core-nodes/n8n-nodes-base.executeworkflowtrigger/
   - https://docs.n8n.io/flow-logic/subworkflows/
2. `Wait`
   - 用來等待人類回交、Webhook、表單提交
   - 官方文件：
   - https://docs.n8n.io/integrations/builtin/core-nodes/n8n-nodes-base.wait/
   - https://docs.n8n.io/flow-logic/waiting/
3. Execution order
   - 用來確保單線順序流程的預期行為
   - 官方文件：
   - https://docs.n8n.io/flow-logic/execution-order/

## 20. 下一步

若董事長確認方向可行，下一份文件建議直接進入實作規格：

`N8N_ROUND_GATE_WORKFLOW_BUILD_SPEC_2026-04-21.md`

內容只做三件事：

1. 寫出每個 workflow 的節點表
2. 寫出 webhook / form payload schema
3. 寫出資料表 DDL 與狀態轉移條件

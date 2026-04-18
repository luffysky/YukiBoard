# CODEX.md

日期: 2026-04-13  
用途: 提供後續所有 Codex session 與協作 agent 的固定基線，避免每次新對話重新誤判專案狀態、協作分工與架構方向

## 1. 專案現況記憶

這個專案是 `SnowRealmYukiBoard`，一個高功能密度的 Android IME 專案，不是一般鍵盤 demo。

目前已知功能面包括：

- 核心輸入法流程
- 多語輸入引擎
- AI provider 整合
- 語音輸入 / TTS
- 主題 / 動畫 / 角色 / 背景
- 雲端同步 / 帳號
- 反詐
- 遠端鍵盤
- 遊戲
- GIF / 貼圖 / 多種工具面板

目前工程判斷不是「功能不足」，而是「功能成長已超過當前架構承載力」。

## 2. 專案的正確高層判斷

後續對此專案的預設判斷必須是：

- `YukiBoardIMEService` 是超級控制器，責任過重
- `SettingsActivity` 是超級設定中心，責任過重
- UI、狀態、流程、外部整合耦合偏高
- 狀態來源分散
- integration boundary 尚未收斂
- 專案現在最需要的是架構收斂，而不是再擴張核心控制器

不要再假設：

- 現在已經是成熟乾淨的 MVVM + Clean Architecture
- `YukiBoardIMEService` 應繼續持有所有子系統
- `SettingsActivity` 應繼續承載所有 section 邏輯

## 3. 協作記憶

目前已建立的協作關係如下：

- `Claude Code` 已讀過基線文件，並已交接一份 [CODEX_HANDOFF.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/CODEX_HANDOFF.md)
- `Codex` 已回交一份 [CODEX_REPLY_TO_CLAUDE_CODE_2026-04-13.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/CODEX_REPLY_TO_CLAUDE_CODE_2026-04-13.md)

雙方已形成共識：

- Codex 主導掃描、切割、骨架設計、架構校正
- Claude Code 主導具體功能搬遷、UI 拆分、局部 bug 修復
- 先立骨架，再搬邏輯
- 登入 bug 可平行修，但不應用大改 auth 架構的方式處理

這些共識在新對話中視為既有背景，不需要重新發明一次。

另外，`/RULE` 資料夾已於 2026-04-13 改寫為本專案專用版本，可作為 task / spec / lock / 協作流程的正式補充規則來源。

## 3.1 SnowRealm 團隊主檔

`SnowRealm` 的正式團隊身份、八人配置、人格、職位、職責、Prompt、VRM 規格，統一以：

- [RULE/SNOWREALM_TEAM_MASTER.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SNOWREALM_TEAM_MASTER.md)

作為唯一主源文件。

此處只保留與本專案直接相關的最小摘要：

- 使用者 = `SnowRealm 董事長`
- `玄樞`：技術總監 / 交付 gate owner
- `衡鑑`：資深技術審查工程師 / 實作型 reviewer
- `驗衡`：測試與驗證工程師
- `矩衡`：架構治理與邊界審計師
- `雪鑰`：設定與帳號流資深工程師
- `鍵律`：核心輸入與 IME 行為工程師
- `曉綱`：產品與流程分析師
- `煙汐`：設計與體驗審校

若 `CODEX.md`、`CLAUDE.md`、`RULE/` 與 `RULE/SNOWREALM_TEAM_MASTER.md` 有角色設定衝突，以 master 為準。

## 4. 固定角色分工

### Codex 負責

- 專案健檢
- 架構分析
- 責任切割
- 新骨架設計
- 重構順序規劃
- 高風險修改前的風險控管
- 階段 gate 審核（由 `玄樞` 執行最終下一階段核可）

### Claude Code 負責

- 依骨架做功能搬遷
- UI section / binder 拆分
- 修局部功能 bug
- 讓既有功能在新骨架下重新落地

### 4.1 角色職位對應

- `玄樞`：技術總監 / 交付 gate owner
- `衡鑑`：資深技術審查工程師 / 實作型 reviewer
- `雪鑰`：設定與帳號流資深工程師
- `鍵律`：核心輸入與 IME 行為工程師

## 5. 固定規則與界線

後續任何 agent 或 Codex session 在本專案工作時，遵守以下規則。

### 5.1 核心方向

- 每一輪修改，都應讓核心流程更清楚、狀態更單一、依賴更可追蹤
- 不要只是把功能先塞進去

### 5.2 對 `YukiBoardIMEService` 的規則

- 視它為「待瘦身的平台殼」
- 不再把新功能直接塞進去
- 新邏輯優先抽往 coordinator / use case / repository
- service 應逐步收斂為：
  - lifecycle 接線
  - action dispatch
  - state render

### 5.3 對 `SettingsActivity` 的規則

- 視它為「settings 容器頁」
- 不再把新 section 邏輯直接堆在本體
- 新增功能優先做 section / binder / controller
- 目標是讓 activity 只負責容器、navigation、lifecycle

### 5.4 對狀態管理的規則

- 不再擴張多頭狀態來源
- 新代碼優先走 `UiState + Coordinator + Repository`
- 不要再把 prefs、singleton cache、activity/service field 當主要真實來源
- broadcast 不應成為主要應用內同步機制

### 5.5 對整合層的規則

- 不在 view / panel 類內直接 new network client
- 不在多處分散處理 token / timeout / error policy
- 外部整合應逐步收斂到 repository / gateway / 統一 http client

### 5.6 對重構節奏的規則

- 不大爆炸重寫
- 一次只拆一層責任
- 先立骨架再搬功能
- 不要把架構重構與大型功能變更混在同一輪

## 6. 固定依賴方向

後續新增或重構代碼時，以這個方向為準：

`platform -> application -> domain <- integration`

說明：

- `platform`: Android lifecycle / Activity / Service / Receiver / UI 接線
- `application`: coordinator / state store / use case
- `domain`: 純規則、純狀態轉換、演算法
- `integration`: API、storage、auth、external systems

## 7. 當前優先順序

固定優先順序如下：

1. 建立新骨架
2. 瘦 `YukiBoardIMEService`
3. 瘦 `SettingsActivity`
4. 建立單一狀態來源
5. 收斂 integration layer
6. 最後才繼續擴張功能

如果有新需求進來，先問：

> 這會不會讓核心控制器更胖？

如果答案是會，先改設計再實作。

## 8. 後續應優先閱讀的文件

新 session 進來後，優先閱讀：

1. [PROJECT_HEALTH_CHECK_2026-04-13.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/PROJECT_HEALTH_CHECK_2026-04-13.md)
2. [ARCHITECTURE_REFACTOR_PLAN.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/ARCHITECTURE_REFACTOR_PLAN.md)
3. [CLAUDE_CODEX_COLLAB_GUIDE.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/CLAUDE_CODEX_COLLAB_GUIDE.md)
4. [CODEX_HANDOFF.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/CODEX_HANDOFF.md)
5. [CODEX_REPLY_TO_CLAUDE_CODE_2026-04-13.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/CODEX_REPLY_TO_CLAUDE_CODE_2026-04-13.md)
6. [CODEX_SESSION_GUIDE.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/CODEX_SESSION_GUIDE.md)
7. [RULE/AI_PROTOCOL.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\RULE\AI_PROTOCOL.md)
8. [RULE/LOCK_RULES.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\RULE\LOCK_RULES.md)
9. [RULE/TASK_TEMPLATE.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\RULE\TASK_TEMPLATE.md)
10. [RULE/SPRINT_GATE_RULE.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\RULE\SPRINT_GATE_RULE.md)
11. [RULE/ROUND_GATE_COLLAB_RULE.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\RULE\ROUND_GATE_COLLAB_RULE.md)
12. [RULE/SNOWREALM_ROLE_RULE.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\RULE\SNOWREALM_ROLE_RULE.md)
13. [RULE/SNOWREALM_TEAM_MASTER.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SNOWREALM_TEAM_MASTER.md)
14. [docs/SNOWREALM_AGENT_ROLE_PROMPT_2026-04-18.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SNOWREALM_AGENT_ROLE_PROMPT_2026-04-18.md)

## 9. 當前未解但重要的事

- 登入流程仍可能不穩，這會直接影響雲同步
- `YukiBoardIMEService` 與 `SettingsActivity` 持續膨脹中
- 專案幾乎沒有正式測試保護

這些屬於已知背景，不要在新對話裡當成新發現重新講一遍。

## 10. 什麼是好改動

- 新增 `UiState`
- 新增 `Coordinator`
- 新增 `Repository` / `Gateway`
- 把 lifecycle 副作用從核心 activity / service 抽出
- 把整合層收斂
- 把 UI 改成主要只拿 state 與 callback

## 11. 什麼是壞改動

- 再往 `YukiBoardIMEService` 塞新 feature
- 再往 `SettingsActivity` 加更多大段 section builder
- 在 panel / view 內直接做業務邏輯或網路請求
- 再新增更多分散的 `OkHttpClient.Builder()`
- 再新增更多 ad hoc singleton 依賴
- 用臨時 patch 加深結構債

## 12. 交接格式

Codex 或 Claude Code 若完成一輪工作，盡量用以下格式交接：

- A. 變更摘要
- B. 風險
- C. 下一步建議
- D. 架構決策

另外，任何 Sprint / Phase 的前進都要多一個必要步驟：

- E. `玄樞` 審核結論（`READY` / `NOT READY`）

補充強制規則：

- 只要某一 round 的回交包含 `.kt` 新增或修改，`玄樞` 必須親自打開實際 `.kt` 檔逐檔複核。
- 不可只看交接摘要、side-note、或二次審核結論就直接放行。
- 若實檔複核時發現 bug、越界、假修、漏修、或新風險，必須直接退回，不得標 `READY`。
- 若 `衡鑑` 或其他角色需要做二次審核，必須把該審核職責直接寫在該角色自己的任務欄，不能只寫「提交給某人」。

## 13. 最後準則

這份 `CODEX.md` 的目的不是取代其他文件，而是讓後續新對話先有共同記憶與共同邊界。

若未來文件內容彼此衝突，優先採用：

1. 最新的實際架構決策
2. 能讓 `YukiBoardIMEService` 更瘦的方案
3. 能讓 `SettingsActivity` 更瘦的方案
4. 能讓狀態來源更單一、整合邊界更清楚的方案
5. `玄樞` 的 Sprint / Phase gate 規則

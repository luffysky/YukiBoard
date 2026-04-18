# AI 協作協議（YukiBoard / Android 版）

日期: 2026-04-13  
適用專案: `SnowRealmYukiBoard`

## 1. 協議目的

這份協議用來規範 `Claude Code` 與 `Codex` 在本專案中的合作方式，避免：

- 重工
- 並行互撞
- 未授權跨檔修改
- 臨時 patch 讓核心檔繼續膨脹
- 架構方向在不同對話中反覆漂移

## 2. 專案現況前提

本專案是高功能密度的 Android IME，不是一般 demo。

後續所有協作必須以這些文件為基線：

- `/CODEX.md`
- `/docs/PROJECT_HEALTH_CHECK_2026-04-13.md`
- `/docs/ARCHITECTURE_REFACTOR_PLAN.md`
- `/docs/CLAUDE_CODEX_COLLAB_GUIDE.md`

若和舊認知衝突，以上文件優先。

## 3. 角色定義

### Claude Code

負責：

- 撰寫或整理 task / spec
- 推進具體功能搬遷
- 拆 UI section / binder / helper
- 修正局部 bug
- 在既定架構方向下完成實作

禁止：

- 未說明情況下擴張 `YukiBoardIMEService.kt`
- 未說明情況下擴張 `SettingsActivity.kt`
- 在未確認 file scope 前跨檔案大改

### Codex

負責：

- 專案健檢
- 架構分析
- 切割策略
- state / coordinator / repository 骨架設計
- 高風險區域重構與方向控制
- 必要時做結構性實作

禁止：

- 忽略既有 task / file scope 直接大範圍改檔
- 在 Claude Code 明顯正在拆同一區塊時硬撞同一檔案
- 新增與既定架構方向衝突的平行骨架

### SnowRealm 身份補充

本專案所有角色的公司身份固定如下：

- 使用者 = `SnowRealm 董事長`
- `玄樞`（原 `CodeX A`）= `SnowRealm 技術總監 / 交付 gate owner`
- `衡鑑`（原 `CodeX B`）= `SnowRealm 資深技術審查工程師 / 實作型 reviewer`
- `雪鑰`（原 `Claude Code A`）= `SnowRealm 設定與帳號流資深工程師`
- `鍵律`（原 `Claude Code B`）= `SnowRealm 核心輸入與 IME 行為工程師`

所有角色目前共同執行的是：

- `SnowRealm` 的 `YukiBoard` 輸入法專案

## 4. 決策權歸屬

| 項目 | 主要決策者 | 說明 |
|------|------------|------|
| 功能需求 | 使用者 / Claude Code | 定義要完成什麼 |
| 架構掃描 | Codex | 定義問題在哪裡 |
| 架構切割與骨架 | Codex | 定義先立什麼骨架 |
| 功能搬遷落地 | Claude Code | 按骨架推進 |
| 局部實作細節 | 執行者 | 在既定方向內調整 |
| 跨層重構 | Codex 主導，Claude Code 配合 | 需要明確交接 |
| Sprint / Phase 進階核可 | `CodeX A` | 每一步 Sprint/Phase 都需先審核通過才可進下一階段 |

## 5. 協作流程

建議流程：

1. 使用者提出需求
2. 若涉及架構或高風險區域，Codex 先掃描 / 設計
3. 形成 task / handoff / file scope
4. Claude Code 或 Codex 執行
5. 用固定格式回交

固定回交格式：

- A. 變更摘要
- B. 風險
- C. 下一步建議
- D. 架構決策
- E. `CodeX A` 審核需求或審核結論

所有 Sprint / Phase 的正式前進，還必須遵守：

- 先由執行者完成前置或回交
- 再由 `CodeX A` 輸出 `READY / NOT READY / LOCKED`
- 沒有 `CodeX A` 的明確核可，不得宣告進入下一階段

## 6. 衝突處理

### 情境 1：Task 與基線文件衝突

處理：

- 以最新基線文件優先
- 執行者回報 `NEED_CLARIFICATION`

### 情境 2：兩個 agent 同時修改同一檔案

處理：

- 優先避免並行
- 若已並行，後進者先讀 `git diff` 與現況再決定是否繼續
- 若風險高，暫停並改做不衝突區塊

### 情境 3：執行中發現需要擴大 file scope

處理：

- 回報 `NEED_CLARIFICATION`
- 列出新增檔案與原因
- 不自行默默擴大範圍

## 7. 本專案特別規則

- 不要再把新功能直接塞進 `YukiBoardIMEService.kt`
- 不要再把新 section 直接塞進 `SettingsActivity.kt`
- 新結構優先走：
  - `platform -> application -> domain <- integration`
- 新共享狀態優先走：
  - `UiState`
  - `Coordinator`
  - `Repository`

## 8. 最後準則

每一輪合作都應讓專案：

- 更容易理解
- 更容易測試
- 更容易替換外部整合
- 更不依賴超大核心檔

如果某個方案只是比較快，但會讓兩個核心檔更胖，原則上不選。

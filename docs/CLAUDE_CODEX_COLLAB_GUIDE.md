# Claude Code x Codex 協作說明

日期: 2026-04-13  
目的: 定義本專案中 Claude Code 與 Codex 的合作方式，避免重工、互相覆蓋、或架構方向失焦

## 1. 協作前提

目前本專案已經有兩份重要文件：

- [PROJECT_HEALTH_CHECK_2026-04-13.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\PROJECT_HEALTH_CHECK_2026-04-13.md)
- [ARCHITECTURE_REFACTOR_PLAN.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\ARCHITECTURE_REFACTOR_PLAN.md)

後續 Claude Code 與 Codex 的合作，請以這兩份文件為共同基線，不要再假設：

- `YukiBoardIMEService` 應繼續持有所有子系統
- `SettingsActivity` 應繼續當作一個超大型 everything controller
- 現況已經是完整的 MVVM + Clean Architecture

現況判斷應以「高功能密度、單模組、核心檔案過大、UI/狀態/整合耦合偏高」為準。

## 2. 角色分工

### Codex 負責

- 專案健檢
- 架構整理
- 重構切割設計
- 高風險區域責任拆分
- 建立新骨架與遷移順序
- 做結構性修改時的風險控管

### Claude Code 負責

- 依照既定骨架推進具體功能改造
- 實作 UI section 拆分
- 補齊既有功能流程
- 修正回歸 bug
- 幫忙把設計稿落實成可執行程式碼

### 共同原則

- 先守住核心輸入流程，再處理增值功能
- 不做大爆炸重寫
- 不為了局部功能方便，繼續加深 `YukiBoardIMEService` / `SettingsActivity` 的責任
- 所有新增設計都要朝 `platform -> application -> domain <- integration` 方向收斂

## 3. 建議的合作模式

最有效率的方式不是兩邊同時亂改，而是：

1. Codex 先做掃描、切割、設計、骨架建立
2. Claude Code 依照骨架做功能搬遷與局部修復
3. Codex 再做第二輪掃描與架構校正

換句話說：

- Codex 比較像 architecture surgeon
- Claude Code 比較像 primary implementer

如果反過來，容易出現：

- Claude 先把功能補上，但架構更重
- Codex 再回來拆時，成本更高

## 4. 協作邊界

### Claude Code 適合直接動手的區域

- UI section 抽離
- helper / binder / controller 新增
- 把重複 UI 建構邏輯拆出
- 把網路 / 設定頁事件從 activity 本體移走
- 補 smoke test 所需的小型保護

### Claude Code 不應自行擴張的區域

- 再把更多 feature 塞進 `YukiBoardIMEService`
- 再把更多設定區塊塞進 `SettingsActivity`
- 新增更多 `getInstance(context)` 式跨層依賴
- 在 view / panel 類裡直接建立新的 network client
- 新寫一套和既定架構方向衝突的狀態管理方式

### Codex 適合主導的區域

- `YukiBoardIMEService` 拆分策略
- `SettingsActivity` 拆分路線
- state store 設計
- integration layer 收斂
- package / module 邊界
- 重大重構前的風險盤點

## 5. 交接格式

之後 Claude Code 或 Codex 在做完一輪工作後，建議都用同一個簡短格式交接：

### A. 變更摘要

- 這輪改了什麼
- 主要檔案
- 是否有新骨架 / 新介面 / 新依賴方向

### B. 風險

- 哪些地方可能有回歸風險
- 哪些地方暫時只是搬殼，還沒完全解耦

### C. 下一步建議

- 下一輪最適合接著做什麼
- 哪些地方先不要碰

### D. 若有架構決策，必須明說

例如：

- 新增 `ImeSessionCoordinator`
- `SettingsActivity` 開始只負責 section 組裝
- AI provider 呼叫之後要統一走某個 gateway

不要把這些決策藏在 commit 或程式碼裡。

## 6. 文件更新規則

如果 Claude Code 或 Codex 的工作改變了架構方向，請同步更新：

- `PROJECT_HEALTH_CHECK_2026-04-13.md`
- `ARCHITECTURE_REFACTOR_PLAN.md`

至少要讓這兩份文件仍然可信。不要讓文件停留在過期狀態。

## 7. 當前優先順序

目前協作優先順序應固定如下：

1. 立新骨架
2. 瘦 `YukiBoardIMEService`
3. 瘦 `SettingsActivity`
4. 收斂狀態來源
5. 收斂 integration boundary
6. 最後才考慮繼續加大功能面

如果有新需求進來，先問一件事：

> 這會不會讓核心控制器更胖？

如果答案是會，優先改設計，不要直接實作。

## 8. 協作禁忌

- 不要在沒有說明的情況下大規模搬檔或改 package
- 不要把架構重構與功能大改混在同一輪
- 不要同時大改 IME 輸入流程與設定頁流程
- 不要因為趕進度，把臨時 patch 寫回核心 super class
- 不要把文件、設計、實作三者完全分離

## 9. 最後準則

本專案後續合作以這句話為共同準則：

> 每一輪修改，都應讓核心流程更清楚、狀態更單一、依賴更可追蹤，而不是只是把功能先塞進去。

如果 Claude Code 與 Codex 對某個方案有衝突，優先選：

- 更容易縮小 `YukiBoardIMEService`
- 更容易縮小 `SettingsActivity`
- 更符合單一狀態來源
- 更能隔離外部整合副作用

而不是選看起來比較快但會繼續累積結構債的方案。

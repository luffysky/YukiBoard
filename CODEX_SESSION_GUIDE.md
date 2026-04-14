# Codex Session Guide

日期: 2026-04-13  
目的: 提供所有後續 Codex session 一份共用上下文，避免每次重頭判斷與重複犯同樣的架構錯誤

## 1. 先讀這三份文件

進入本專案後，優先閱讀：

1. [PROJECT_HEALTH_CHECK_2026-04-13.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\PROJECT_HEALTH_CHECK_2026-04-13.md)
2. [ARCHITECTURE_REFACTOR_PLAN.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\ARCHITECTURE_REFACTOR_PLAN.md)
3. [CLAUDE_CODEX_COLLAB_GUIDE.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\CLAUDE_CODEX_COLLAB_GUIDE.md)

這三份是目前可信的高層上下文。

## 2. 專案現況的正確判斷

這不是一般 Android IME 專案。它是一個高功能密度的單 app module，功能包括：

- 核心輸入法
- 多語輸入
- AI provider 整合
- 語音輸入 / TTS
- 主題 / 動畫 / 角色
- 雲同步 / 帳號
- 反詐
- 遊戲
- 遠端鍵盤
- GIF / 貼圖 / 多種工具面板

不要再沿用「目前已經是乾淨 MVVM / Clean Architecture」這種過度樂觀前提。

更接近事實的判斷是：

- 功能很多
- 核心檔案過大
- UI / 狀態 / 整合耦合偏高
- 現在最需要的是架構收斂

## 3. 當前主要技術債

後續 Codex session 應預設以下是本專案的主要結構問題：

- `YukiBoardIMEService` 是超級控制器
- `SettingsActivity` 是超級設定中心
- 狀態來源分散
- 多處直接碰 singleton / prefs / network
- 外部整合沒有完全統一邊界
- 測試保護不足

任何提案如果忽略這幾點，通常方向就會偏掉。

## 4. 當前最高優先目標

優先順序固定如下：

1. 建立新骨架
2. 讓 `YukiBoardIMEService` 瘦身
3. 讓 `SettingsActivity` 瘦身
4. 建立單一狀態來源
5. 收斂 integration layer
6. 最後才是擴張功能

如果某次任務同時會：

- 加功能
- 增加核心耦合

優先拒絕第二件事，而不是優先完成第一件事。

## 5. 建議的依賴方向

後續新增或重構代碼時，優先往這個方向收斂：

`platform -> application -> domain <- integration`

對應意義：

- `platform`: Android lifecycle / UI 接線
- `application`: coordinator / store / use case
- `domain`: 純規則
- `integration`: API / storage / auth / external systems

## 6. 後續 Codex session 的工作守則

### 做掃描時

- 先判斷本次修改是否會讓核心控制器更胖
- 先找責任邊界，不要直接進入 patch
- 先看是否有現成文件已經定義方向

### 做重構時

- 一次只拆一層責任
- 優先建立 coordinator / repository / state store 骨架
- 優先抽流程 owner，不要先大改 UI 表面
- 能降低爆炸半徑的改動，優先級高於表面整潔

### 做功能時

- 不要把新功能直接塞回 `YukiBoardIMEService`
- 不要把新設定直接塞回 `SettingsActivity`
- 不要在 view / panel 內直接建立新的網路呼叫
- 不要新增新的 ad hoc 狀態來源

## 7. 對 `YukiBoardIMEService` 的預設態度

後續任何 session 都應視它為「正在拆除中的 super orchestrator」，不是新的功能承載點。

預設策略：

- 減少責任
- 不增加責任
- 將邏輯搬到 application 層物件
- service 只留下 lifecycle、action dispatch、state render

## 8. 對 `SettingsActivity` 的預設態度

後續任何 session 都應視它為「準備拆成多個 settings section / controller 的容器」，不是繼續堆內容的地方。

預設策略：

- 新增設定功能時，優先做 section 化
- 既有區塊改動時，順手抽離 binder / section class
- 將登入 / 同步 / AI / 主題等流程移往 coordinator / repository

## 9. 什麼算是好改動

以下變更通常是高品質方向：

- 新增 `UiState`
- 新增 `Coordinator`
- 新增 `Repository` 或 `Gateway`
- 把 lifecycle 副作用從 activity / service 抽出
- 把 network 初始化收斂
- 讓 UI 只拿 state + callback

## 10. 什麼算是壞改動

以下變更通常應避免：

- 再往 `YukiBoardIMEService` 加一堆成員與 helper
- 再往 `SettingsActivity` 加一大段 card builder
- 在 panel / view 內直接做業務邏輯
- 再新增更多 `getInstance(context)` 作為跨層捷徑
- 再新增更多分散的 `OkHttpClient.Builder()`
- 用 broadcast 或 prefs 當作主要應用內同步機制

## 11. 回報格式

後續 Codex session 結束時，建議至少回報：

- 這輪做了什麼
- 對架構有沒有新影響
- 哪些風險還在
- 下一步最合理的是什麼

如果你做的是架構性改動，請明說：

- 哪個責任從哪裡搬到哪裡
- 狀態來源是否變更
- 依賴方向是否更清楚

## 12. 若任務和架構方向衝突

當使用者需求和目前架構方向衝突時，不要直接硬做。先判斷：

- 能否在不增加核心耦合的前提下完成
- 能否順便沿重構方向落地
- 是否應先補最小骨架再做功能

如果只能二選一，優先選擇對長期骨架較友善的方案。

## 13. 本專案的共同原則

後續所有 Codex session 請以這句話當作判準：

> 每一次修改，都應讓專案更容易理解、更容易測試、更容易替換外部整合，而不是只讓功能暫時能跑。

只要這個原則還成立，方向通常就不會太偏。

# Codex 回交 Claude Code

日期: 2026-04-13  
對象: Claude Code  
依據: `CODEX_HANDOFF.md`、`PROJECT_HEALTH_CHECK_2026-04-13.md`、`ARCHITECTURE_REFACTOR_PLAN.md`、`CLAUDE_CODEX_COLLAB_GUIDE.md`

## A. 變更摘要

我已完成以下工作，作為目前協作基線：

- 新增 [PROJECT_HEALTH_CHECK_2026-04-13.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\PROJECT_HEALTH_CHECK_2026-04-13.md)
  - 專案整體健檢
  - 核心風險排序
  - 為何 `YukiBoardIMEService` / `SettingsActivity` 已成為結構瓶頸

- 新增 [ARCHITECTURE_REFACTOR_PLAN.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\ARCHITECTURE_REFACTOR_PLAN.md)
  - 定義 `platform -> application -> domain <- integration`
  - 定義 `YukiBoardIMEService` 與 `SettingsActivity` 的拆分方向
  - 定義 Sprint 式落地順序與高風險區域

- 新增 [CLAUDE_CODEX_COLLAB_GUIDE.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\CLAUDE_CODEX_COLLAB_GUIDE.md)
  - 明確 Codex / Claude Code 分工
  - 定義交接格式與合作禁忌

- 新增 [CODEX_SESSION_GUIDE.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\CODEX_SESSION_GUIDE.md)
  - 提供所有後續 Codex session 共用上下文

我也已閱讀你的 [CODEX_HANDOFF.md](D:\SnowRealmRebirth\SnowRealmYukiBoard\CODEX_HANDOFF.md)，確認目前共識一致：

- 先立骨架，再搬功能
- 先瘦 `YukiBoardIMEService`
- 再瘦 `SettingsActivity`
- 登入流程 bug 可與架構重構平行處理，但不應讓 auth code path 在未穩定前被大改

## B. 風險

我目前最在意的風險如下：

- `YukiBoardIMEService` 的 session lifecycle、panel、voice、remote、game、fraud、sync 全都交會在同一檔
- `SettingsActivity` 的 section UI 建構與事件綁定完全混在一起，短期每加功能都會持續膨脹
- auth / sync 仍不穩，若在登入流程未穩定前大改 session 與 token path，容易讓問題更難定位
- 專案幾乎沒有正式測試保護，大型搬遷只能靠 smoke 驗證

額外提醒：

- `CODEX_HANDOFF.md` 裡的數字比我第一輪掃描更高，代表最近幾輪修改仍在讓兩個核心大檔繼續成長
- 這使得 Sprint 1 已不是「優化」，而是必要止血

## C. 下一步建議

我建議接下來的工作分工如下。

### Codex 先做

我下一輪若直接進場實作，會優先建立 Sprint 1 骨架：

1. `ImeUiState`
2. `SettingsUiState`
3. `ImeSessionCoordinator`
4. `SettingsScreenCoordinator`
5. `UserSessionRepository`

但我會刻意只做「骨架與依賴方向」，不一次搬大量舊邏輯，避免爆炸半徑過大。

### Claude Code 接著做

你可以在不破壞上面骨架前提下，平行推進：

1. 登入流程穩定化
2. `SettingsActivity` 的低風險 section 拆分
3. `YukiBoardIMEService` 中明顯獨立的 UI / binder 類搬移

我建議你優先碰這些相對安全的區塊：

- `AuthSettingsSection`
- `SyncSettingsSection`
- `AISettingsSection` 的 UI binder
- `ImeSessionHooks`
- `ImeVoiceCoordinator` 的薄封裝

### 先不要碰

- 不要先大改 `handleKeyPress()` 的核心演算法路徑
- 不要在登入流程未穩前重寫 auth / sync 全鏈路
- 不要再把新功能塞回 `YukiBoardIMEService` 或 `SettingsActivity`

## D. 架構決策

以下決策我先明確寫下，後續請以此為準：

### 1. `YukiBoardIMEService` 定位變更

從現在開始，`YukiBoardIMEService` 視為「待瘦身的平台殼」，不是新的功能擴張點。

它未來應只保留：

- Android lifecycle 接線
- UI callback dispatch
- state render
- coordinator 注入

### 2. `SettingsActivity` 定位變更

從現在開始，`SettingsActivity` 視為「settings 容器頁」，不是一個繼續承載所有 section 邏輯的 everything controller。

新功能若進設定頁，優先以 section / binder / controller 形式落地。

### 3. 單一狀態來源是必要條件

不再接受繼續擴張：

- activity/service 私有變數
- prefs 直讀直寫
- singleton 臨時快取
- broadcast 當作主要應用內狀態同步

至少從新代碼開始，要往 `UiState + Coordinator + Repository` 收斂。

### 4. integration boundary 要收斂

後續任何新的外部整合，不應再：

- 在 view / panel 內直接 new client
- 在多處各自處理 token / timeout / 錯誤

應逐步走向：

- `UserSessionRepository`
- `SyncRepository`
- `ThemeRepository`
- `AiGateway`
- `AppHttpClientFactory`

### 5. 允許平行推進，但不允許方向衝突

你可以繼續修登入與搬 section，我可以立骨架；但兩邊都不應引入與上述方向相衝突的新設計。

## 補充判斷

你在 handoff 裡提到的登入 bug，我同意它是高優先，但我認為它屬於「核心功能穩定性修復」，不屬於架構重構阻塞項。

也就是說：

- 可以先修
- 應該修
- 但不要用大改 auth 架構的方式修

目前最理想的合作節奏是：

1. 你穩登入
2. 我立骨架
3. 你依骨架搬 UI / flow
4. 我再做第二輪結構校正

## 結論

我接受你的 handoff，也同意你提出的 Sprint 1 優先順序。

目前對 Claude Code 的正式回覆只有一句話：

> 先停止讓兩個核心大檔繼續膨脹；登入 bug 與骨架建立可以平行，但後續所有修改都要以瘦 `YukiBoardIMEService` 與 `SettingsActivity` 為共同方向。

*Codex — 2026-04-13*

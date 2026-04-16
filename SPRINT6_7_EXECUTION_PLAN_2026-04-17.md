# Sprint 6 / Sprint 7 Execution Plan（2026-04-17）

用途：

- 把 `Sprint 6` 與 `Sprint 7` 的後續工作拆成正式可管理的詳細分工
- 明確規範 `CodeX A / CodeX B / Claude Code A / Claude Code B` 四人的角色、任務、禁區與交接格式
- 避免在 `Sprint 5` 完成後，大家各自開題、撞檔、越界或把 integration / panel / runtime bug 混成同一輪

## 1. 當前正式前提

### 1.1 已完成到哪裡

- `Sprint 1`：完成
- `Sprint 2`：完成
- `Sprint 3`：consume 與 readiness 路線已走完實際主線
- `Sprint 4`：完成
- `Sprint 5`：完成，並經 `CodeX A` 判定 `COMPLETED-STABLE`

### 1.2 尚未混入 Sprint 5 的事項

- `floating mode stale true` 為 pre-existing runtime bug
- 該問題不算 Sprint 5 回歸
- 若要修，必須作為獨立小修或 Sprint 6+ 的小議題，不得反向污染 Sprint 5 判定

### 1.3 目前真正還沒做的大區塊

#### Sprint 6

高依賴 panel 新邊界，順序如下：

1. `THEME`
2. `CLAW`
3. `REMOTE`
4. `HANDWRITING`
5. `MUSIC`
6. `USER_DICT`

#### Sprint 7

整合層與狀態來源總收斂，主題如下：

1. 分散的 `OkHttpClient.Builder()`
2. auth / provider / sync / external gateway
3. prefs / singleton / mutable field 多頭狀態

## 2. 總原則

### 2.1 Sprint 6 的原則

- 一次只處理一個高依賴 panel
- 每個 panel 先做 boundary / scope / 依賴盤點，再決定是否進 implementation
- 不把 panel 邊界重構與大型 runtime bugfix 混在同一輪
- 不把多個外部系統一起搬

### 2.2 Sprint 7 的原則

- 先盤點，再分批
- 先收斂 integration entry，再收斂 state source
- 不一口氣改全專案的 http client / auth / provider / sync
- 新的共用 abstraction 必須明確說明 owner 與遷移順序

### 2.3 CodeX A 的監督原則

- `CodeX A` 只做：
  - 啟動 gate
  - 風險控管
  - 文件同步
  - compile / smoke / diff 審核
  - 各 round / phase / sprint 的正式拍板
- `CodeX A` 不做：
  - 跟三方搶同一批實作檔
  - 越過 gate 直接宣告開 Sprint 6 / 7 implementation

## 3. Sprint 6 詳細拆法

## 3.1 Sprint 6-0：總盤點與啟動 gate

目的：

- 在真正實作任何高依賴 panel 前，先把 6 個 panel 的依賴圖與風險圖重新盤點

交付物：

- 一份 panel risk matrix
- 一份 file scope 草案
- 一份「哪一個 panel 應先做」的理由

Owner 分工：

### CodeX A

- 審核是否具備啟動 Sprint 6 的條件
- 指定第一個 panel
- 明確鎖住其他 5 個 panel，不准同時開工

### CodeX B

- 盤點 6 個 panel 的依賴：
  - service 直接依賴
  - manager / repository / client / engine 依賴
  - runtime 風險
  - UI consume / callback / side effect owner
- 輸出 panel risk matrix

### Claude Code B

- 做實作前的 callsite / file scope 盤點
- 不改 `.kt`
- 對每個 panel 列出：
  - 建立入口
  - 關閉入口
  - callback
  - state write/read
  - 與 `YukiBoardIMEService` 的耦合點

### Claude Code A

- 保持 settings 線待命
- 若某 panel 與 settings / provider / auth / sync 有交集，補做 side-note 盤點

完成條件：

1. 6 個 panel 都有最小依賴盤點
2. 第 1 個要做的 panel 已被 `CodeX A` 指定
3. 其他 5 個 panel 明確維持 `LOCKED`

## 3.2 Sprint 6-1：THEME panel 邊界

目的：

- 把 `THEME` 從 service 內直接承載的高耦合 UI/狀態/預覽責任切出邊界

預期範圍：

- panel 顯示與切換邊界
- theme apply / preview / background refresh 的責任分界
- `ThemeManager` / `ImeThemeRenderDelegate` / panel UI 的相依路徑

不做：

- 全面重寫主題系統
- 同時重構 background / animation / particle 全鏈

Owner 分工：

### CodeX A

- 指定 THEME 是否正式開工
- 審核 file scope 是否過大
- 監控不要把「主題系統總重構」偷塞進 Sprint 6-1

### CodeX B

- 先寫 `THEME` panel boundary doc
- 定義哪些責任可以出 service、哪些仍暫留 platform side effect
- 判定 theme apply 與 panel open/close 的安全切點

### Claude Code B

- 在被核可後，做 THEME panel 的最小落地實作
- 優先搬：
  - panel open/close routing
  - panel host / binding / callback
  - 純 state consume / action dispatch
- 不碰：
  - `KeyboardView.kt` 大改
  - 大型 background engine 重寫

### Claude Code A

- 若 THEME 與 settings 共用主題 / 背景資料來源，補做 settings 端 impact review
- 核對 settings 主題頁是否會因 IME 邊界調整而需要文件更新

完成條件：

1. `THEME` panel 建立與切換邊界更清楚
2. service 不再直接持有一坨 theme panel-specific 細節
3. compile 通過
4. checklist / 文件已更新
5. `CodeX A` 明確判定可進下一個 panel

## 3.3 Sprint 6-2：CLAW panel 邊界

重點：

- AI 對話 panel 與 service 的耦合切邊
- `ClawClient` / panel UI / request lifecycle / side effects 的責任明確化

Owner 分工：

### CodeX A

- 審是否與 Sprint 7 的 provider / gateway 收斂發生撞題
- 禁止把 Sprint 6-2 擴成「AI provider 全重構」

### CodeX B

- 定義 `CLAW` panel 的 integration boundary
- 區分：
  - panel UI state
  - provider / network request
  - conversation persistence

### Claude Code B

- 實作 panel 邊界薄切
- 收斂 service 端對 `CLAW` 的 panel-specific 建立與 callback

### Claude Code A

- 協助檢查 `CLAW` 若依賴 API key / provider 設定，是否需與 settings / session 邊界同步

## 3.4 Sprint 6-3：REMOTE panel 邊界

重點：

- `REMOTE` panel 與 `BluetoothHidKeyboard` / `WifiKeyboardServer` / remote state display 的切邊

最大風險：

- 不要把 Sprint 6-3 擴成整個 remote keyboard 系統重寫

Owner 分工：

### CodeX A

- 監控 remote panel 與 external integration 是否被混在一輪做爆

### CodeX B

- 定義 remote panel 只處理：
  - panel 顯示
  - state consume
  - action dispatch
- 將 server start/stop、listener、連線邏輯保留於較安全的 owner

### Claude Code B

- 只做 panel 邊界與最小 consume
- 不做 Bluetooth / Wi-Fi server 架構大修

### Claude Code A

- 若 remote 功能與 settings 的裝置設定 / 權限文字 / 連線選項有交集，補做文件註記

## 3.5 Sprint 6-4：HANDWRITING panel 邊界

重點：

- HANDWRITING 是 Sprint 6 最危險的 panel 之一
- 需嚴格區分：
  - panel 顯示
  - candidate 回寫
  - engine lifecycle
  - recognition callback

Owner 分工：

### CodeX A

- 嚴格監控不得碰 HANDWRITING RMW 熱路徑
- 若 scope 超出 panel 邊界，直接打回

### CodeX B

- 寫 HANDWRITING boundary doc
- 明確列出哪些 callback 不准在本輪遷移

### Claude Code B

- 只做 panel host / binding / lifecycle 邊界
- 不動 recognition 演算法 / candidate RMW 主邏輯

### Claude Code A

- 無主要實作責任
- 只做 side-note / 文件支援

## 3.6 Sprint 6-5：MUSIC panel 邊界

重點：

- panel UI 與媒體控制整合的責任拆分

Owner 分工：

### CodeX A

- 控制 file scope，避免牽動整個 media stack

### CodeX B

- 定義媒體控制 side effect 與 panel state consume 的邊界

### Claude Code B

- 做 panel 邊界最小切出
- 不擴大到播放器核心或多媒體 session 總重構

### Claude Code A

- 若 settings 中有 media / 音效選項被牽動，做 impact review

## 3.7 Sprint 6-6：USER_DICT panel 邊界

重點：

- `USER_DICT` 先前一直延後
- 它可能與輸入核心、字典資料、使用者資料儲存同時有關

Owner 分工：

### CodeX A

- 將 `USER_DICT` 視為 Sprint 6 最後處理
- 不允許在前五個 panel 未穩前提前進場

### CodeX B

- 先做風險分類：
  - panel UI
  - dictionary repository
  - user data mutation
  - sync / persistence

### Claude Code B

- 被授權後才做 panel 邊界薄切
- 不提前改 user dictionary 核心資料流程

### Claude Code A

- 若 `USER_DICT` 與 settings 的字典管理頁有交集，再補 settings 側對照

## 4. Sprint 7 詳細拆法

## 4.1 Sprint 7-0：整體 integration audit

目的：

- 先把所有 integration source 與 state source 總盤點

盤點主題：

1. `OkHttpClient.Builder()` 分散點
2. auth token / session 狀態分散點
3. provider 選擇與 API key 分散點
4. sync / external gateway 入口
5. prefs / singleton / mutable field 真實來源

Owner 分工：

### CodeX A

- 決定盤點是否完成到可進 implementation

### CodeX B

- 主導 integration audit 文件
- 產出 migration map

### Claude Code B

- 盤點 IME 側 integration entry

### Claude Code A

- 盤點 settings / auth / provider / sync 側 integration entry

完成條件：

1. 所有高價值分散點已列出
2. 每個分散點都有 owner 與後續處置方向
3. `CodeX A` 明確核可 Sprint 7 implementation 分批啟動

## 4.2 Sprint 7-1：AppHttpClient / network entry 收斂

重點：

- 收斂分散 `OkHttpClient.Builder()`
- 建立一致 timeout / headers / logging / TLS / user-agent 策略

Owner 分工：

### CodeX A

- 監督不得把整個網路層一次重寫

### CodeX B

- 設計 `AppHttpClientFactory` / network gateway 邊界

### Claude Code B

- 處理 IME 側直接 new client 的收斂

### Claude Code A

- 處理 settings / provider / speech / sync 側直接 new client 的收斂

## 4.3 Sprint 7-2：Session / Auth / Provider 狀態收斂

重點：

- `UserSessionRepository`
- API key availability
- selected provider
- display name / plan / auth state

Owner 分工：

### CodeX A

- 拍板哪些欄位成為正式單一來源

### CodeX B

- 設計 auth / provider / session state map

### Claude Code A

- 主實作 settings / session 側的收斂

### Claude Code B

- 配合調整 IME 側 session / provider consume

## 4.4 Sprint 7-3：Sync / External Gateway 收斂

重點：

- `CloudSyncManager`
- remote / speech / AI / fraud / 其他外部系統的 gateway 化

Owner 分工：

### CodeX A

- 控制不要把所有外部系統一口氣混成同一個 mega refactor

### CodeX B

- 決定 gateway 切分順序

### Claude Code A

- 主責 settings / sync / auth / provider 相關 gateway

### Claude Code B

- 主責 IME / remote / speech / panel 相關 gateway

## 4.5 Sprint 7-4：Prefs / Singleton / Mutable Field 真實來源收斂

重點：

- 減少多頭狀態來源
- 把「prefs / singleton / field」由 ad hoc 狀態改為明確 repository / state store owner

Owner 分工：

### CodeX A

- 最終判定哪些來源還可暫留、哪些必須收斂

### CodeX B

- 定義 state ownership matrix

### Claude Code A

- settings 側 multi-source state 收斂

### Claude Code B

- IME 側 multi-source state 收斂

## 5. 四人固定責任表

## 5.1 CodeX A

固定責任：

- gate owner
- 風險控管
- compile / smoke / diff 審核
- file scope 守門
- 文件同步與主線維護

固定監控清單：

1. 有沒有越級開下一個 panel / 下一個子 sprint
2. 有沒有把高依賴 panel 跟 integration 收斂混在一起
3. 有沒有碰禁區：
   - `KeyboardView.kt`
   - `handleKeyPress()` 主語意
   - `applyInputResult()` 主語意
4. 有沒有在未核可情況下擴大 file scope
5. compile 與 checklist 是否同步

## 5.2 CodeX B

固定責任：

- 技術邊界設計
- risk matrix
- readiness / gate 草案
- architecture-level 審核

不做：

- 直接與 `Claude Code B` 搶同一批 `.kt`
- 越過 `CodeX A` 直接宣告解鎖

## 5.3 Claude Code B

固定責任：

- IME / panel / service 側 implementation
- checklist / runtime 驗證
- 高依賴 panel 的最小落地

不做：

- settings 線
- 未授權的核心熱路徑重構
- 未授權的 Sprint 7 integration 總重寫

## 5.4 Claude Code A

固定責任：

- settings / auth / provider / sync / repo-ready 文件線
- Sprint 7 settings-side integration 收斂
- settings / provider / session 相關風險驗證

不做：

- IME panel implementation
- 搶改 `YukiBoardIMEService` 相關主線檔

## 6. 建議執行節奏

### Day A

- `CodeX B`：Sprint 6-0 盤點與 risk matrix
- `Claude Code B`：6 個 panel callsite / file scope 盤點
- `Claude Code A`：settings-side impact review
- `CodeX A`：做 Sprint 6 啟動 gate

### Day B 之後

- 一次只做 1 個 Sprint 6 panel
- 每個 panel 完成後都先：
  - compile
  - checklist
  - smoke
  - `CodeX A` gate
- 未過 gate 不得啟動下一個 panel

### Sprint 7 啟動前

- Sprint 6 至少要完成前 1~2 個 panel，並證明流程可重複
- `CodeX A` 再拍板是否啟動 Sprint 7-0 integration audit

## 7. 交接格式

所有人回交一律帶：

- A. 變更摘要
- B. 風險
- C. 未做的事
- D. 下一步建議
- E. 請 `CodeX A` 判定

## 8. 最後結論

目前最重要的不是立刻實作 `Sprint 6 / 7`，而是：

1. 先把 Sprint 6 切成可管理的單 panel 任務
2. 先由 `CodeX A` 指定第一個 panel
3. 再用同一套 gate 機制往下推

一句話版本：

> `Sprint 6` 要拆成 6 個小戰場逐一打；`Sprint 7` 要先做 audit 再做收斂；四個人都要被鎖在自己的責任邊界內，由 `CodeX A` 持續監控與拍板。

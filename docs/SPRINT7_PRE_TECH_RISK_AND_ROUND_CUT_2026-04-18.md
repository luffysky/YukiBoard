# Sprint 7 前置作業 — CodeX B 技術風險與 Round Cut 建議

任務 ID：`S7-PRE-CXB-PRECHECK`

日期：2026-04-18

身份：CodeX B

依據：`Sprint7-All-Round-Gate.md`

## 0. 範圍宣告

- 本文件只做 `Sprint 7` 前置技術風險整理與 round cut 建議
- 本文件不做 implementation，不修改任何 `.kt`
- 本文件不替 `CodeX A` 自行開門，只提供 `S7-PRE` 裁決依據
- 本文件以：
  - `docs/SPRINT7_PRE_IME_INTEGRATION_PRECHECK_2026-04-18.md`
  - `docs/SPRINT7_PRE_SETTINGS_AUTH_PROVIDER_PRECHECK_2026-04-18.md`
  作為前置原料

## 1. 前置結論

### 1.1 `Sprint 6` 狀態

- `Sprint 6` 六個 panel round 加一個 bugfix round 已全部完成
- 目前沒有未關閉的 `Sprint 6` implementation work
- 因此 `Sprint 7` 的唯一前置門檻，已縮到：`S7-PRE` 是否把 integration 題的分界講清楚

### 1.2 `Sprint 7` 當前真正問題

`Sprint 7` 不是「開始做整合」，而是先定義：

1. 哪些題目屬於 `integration audit`
2. 哪些題目屬於後續 implementation round
3. 哪些題目只是 bugfix，應留到 `Sprint 8`
4. 哪些高風險區塊在 `S7-0` 只能讀、不能改

### 1.3 CodeX B 判定

- `S7-PRE` 就前置資訊完整度而言，已足夠支撐 `CodeX A` 判定是否開 `S7-0`
- 但前提是：`S7-0` 必須被嚴格定義成「audit only」
- 若 `S7-0` 沒有把最小交付物與禁區寫死，極容易失控成半個 `S7-1` / `S7-2`

## 2. Sprint 7 核心技術風險

### 2.1 `S7-0` 與 implementation 混線風險

目前兩份前置文件已把 integration source 找得很完整，這反而帶來第一個風險：一旦 `S7-0` 開門時沒有嚴格限制，執行者很容易順手開始：

- 抽 `AppHttpClient`
- 收斂 `UserSessionRepository`
- 改 `InsightApiClient`
- 瘦身 `CloudSyncManager`

這些都不屬於 `S7-0`，而是後續 round implementation。

### 2.2 owner 尚未單一化的風險

前置文件已確認至少有四組 owner 邊界仍未單一：

1. `OkHttpClient.Builder()` 分散於多個領域
2. session / auth / provider state 同時被 repository 與 singleton 直接讀寫
3. Insight / Supabase gateway 分散於多個 caller
4. `CloudSyncManager` 直接讀多個 subsystem prefs

這些都不是單點 bug，而是跨 round 的結構題；若在單一 round 想一次解完，scope 會失控。

### 2.3 `YukiBoardIMEService` 與既有 binder 邊界回退風險

`Sprint 6` 才剛把 panel 建構責任抽離。`Sprint 7` 如果為了整合方便，重新把 network / session / sync owner 往 `YukiBoardIMEService` 或 panel binder 塞回去，等於直接回退既有成果。

因此 `Sprint 7` 應明確禁止：

- 新責任回灌到 `YukiBoardIMEService`
- 把 panel binder 變成 reach-into-singleton 的 integration owner
- 以「先能跑」為名破壞 Sprint 6 的注入邊界

### 2.4 bugfix 與 integration 收斂混線風險

前置文件已點出多個已知問題，例如：

- login display / `fetchMe()` 時機
- OAuth callback fallback
- keystore fallback policy
- provider cache invalidation

這些都可能在 `Sprint 7` 收斂時被碰到，但它們本質上不是 `S7-0` 的交付物，也不該在沒有 gate 指派的情況下被順手修掉。

## 3. `S7-0` 最小交付物定義

我建議 `CodeX A` 若要開 `S7-0`，必須把交付物限制為以下四項：

1. integration source inventory
2. state source inventory
3. migration map
4. round-by-round owner / file-scope proposal

`S7-0` 不應包含：

- 新增或替換 `AppHttpClient`
- 改 `UserSessionRepository` API
- 改 `CloudSyncManager`
- 改 `InsightApiClient`
- 改任何 panel binder / IME runtime 路徑 / settings UI 行為

換句話說，`S7-0` 的完成標準應是「知道要怎麼切」，不是「已經開始切」。

## 4. 建議 round cut

### 4.1 `S7-0 Integration Audit`

應做：

- 彙總 IME 側與 settings/auth/provider/sync 側所有 integration entry
- 建立 migration map
- 標明每個 entry 的 current owner、target owner、禁止提前改動的檔案
- 產出後續 `S7-1` 到 `S7-4` 的 implementation input

不應做：

- 任何 `.kt` 修改
- 任何 singleton / repository API 調整
- 任何 network client 替換

### 4.2 `S7-1 AppHttpClient / Network Entry 收斂`

應做：

- 建立單一 http client owner
- 收斂各類 `OkHttpClient.Builder()` 直建點
- 定義 timeout / interceptor / cache policy 的統一策略

不應混入：

- session state owner 改造
- provider 選擇邏輯重寫
- sync gateway 改造

### 4.3 `S7-2 Session / Auth / Provider 狀態收斂`

應做：

- 決定 `UserSessionRepository` 是否升級為唯一讀寫 owner
- 收斂 `SnowRealmAuthClient` / `AIProviderManager` 的直接 reach-in
- 明確決定 API key / selected provider / selected model 的 target source

不應混入：

- `AISettingsActivity` 大重寫
- panel / IME UI 流程調整
- network gateway 重構

### 4.4 `S7-3 Sync / External Gateway 收斂`

應做：

- 收斂 Insight gateway 入口
- 明確切開 Insight JWT 與 Supabase JWT 的責任
- 釐清 `CloudSyncManager` 的外部 gateway 邊界

不應混入：

- `CloudSyncManager` 內部 snapshot/export-import 全面改寫
- learning subsystem data model 重構

### 4.5 `S7-4 Prefs / Singleton / Mutable Field 真實來源收斂`

應做：

- 決定每組 prefs / singleton / mutable field 的真實 owner
- 把 `CloudSyncManager` 對他人 prefs 的直讀改為受控 snapshot provider
- 補齊 migration 終點

這一輪應最後做，因為它依賴前面三輪的 owner 決策已經穩定。

## 5. 與 Sprint 8 的分界

以下議題應明確留在 `Sprint 8` 或之後，不應併入 `S7-0`：

1. login 畫面顯示異常、fallback 補救與顯示 bug
2. OAuth callback 特例修補
3. keystore fallback policy 一致化
4. provider cache invalidation 是否屬 bug
5. 任何「目前可運作，但行為不夠漂亮」的 UI 細節修補

判準很簡單：

- 若問題需要先決定 single owner / gateway / state source，屬 `Sprint 7`
- 若問題是在既有 owner 已知的前提下修錯誤行為，屬 `Sprint 8`

## 6. `CodeX A` 開門前應寫死的禁區

我建議 `CodeX A` 在 `S7-0` gate 明文寫入以下禁區：

1. 不得修改任何 `.kt`
2. 不得新增 `AppHttpClient` 骨架
3. 不得改 `YukiBoardIMEService` 責任
4. 不得改 panel binder 依賴形態
5. 不得把 `AISettingsActivity` 重構偷包成 integration audit
6. 不得順手修 `Sprint 8` bugfix 題

## 7. CodeX B 對 `S7-PRE` 的裁決建議

### 7.1 建議結論

我建議 `CodeX A` 對 `S7-PRE` 給：

`READY`

### 7.2 理由

1. `Sprint 6` 已全部結案，無前置阻塞
2. IME 側與 settings/auth/provider/sync 側的 integration source 已足夠盤清
3. `Sprint 7` 主要風險不是資訊不足，而是 round 邊界不夠硬
4. 只要 `CodeX A` 在 `S7-0` 明確限制為 audit only，就可安全開門

### 7.3 對 `S7-0` 的建議 owner 分工

- `CodeX B`：主整合 migration map 與 round cut 文件
- `Claude Code B`：補 IME 側 inventory 欄位與 owner source
- `Claude Code A`：補 settings/auth/provider/sync 側 inventory 欄位與 owner source
- `CodeX A`：審核 `S7-0` 是否達到可進 implementation 的清晰度

## 8. 回交摘要

### A. 本輪交付

- 新增本文件
- 0 行 `.kt` 修改

### B. 本輪結論

- `S7-PRE` 資訊已足夠
- 建議 `READY`
- 但 `S7-0` 必須嚴格限定為 audit only

### C. 本輪未做

- 未做任何 implementation
- 未替 `S7-1` 之後指定實作者
- 未提前處理 `Sprint 8` bugfix

### D. 下一步建議

- 由 `CodeX A` 在 gate 中明文定義 `S7-0` 最小交付物與禁區
- 若開門，`CodeX B` 直接進 `S7-0` integration audit 文件整合

### E. 請 `CodeX A` 判定

- `READY for S7-0`
- 或 `NOT READY（需補指定前置缺口）`


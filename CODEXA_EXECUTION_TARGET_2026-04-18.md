# CodeX A Execution Target（2026-04-18）

身份：CodeX A

日期：2026-04-18

用途：

- 將今日目標明確定為：`Sprint 6` 完成、`Sprint 7` 完成、並修復昨日驗證發現的 bug
- 把 bug 修補正式排進 `Sprint 6`
- 指定各 round owner，維持「前一 round 完成並經 CodeX A 審核後，才開下一門」

## 1. 今日目標

今日主目標：

1. `Sprint 6` 完成
2. `Sprint 7` 完成
3. 修復昨日驗證發現的 bug：`floating mode stale true`

補充原則：

- 目標可以積極，但流程仍維持 round-based gate。
- 任何後續 round 的開門條件，都是前一 round 完成並經 `CodeX A` 審核通過。
- 達標就立刻開下一門，不拖；未達標就明確 `NOT READY`。

## 2. 昨日 bug 的正式歸位

bug：

- `floating mode stale true`

來源判定：

- 屬 pre-existing runtime bug
- 不屬 `Sprint 5` regression

本日處置：

- 正式排入 `Sprint 6`，作為 `Sprint 6-Bugfix Round`
- 不排入 `Sprint 7`

理由：

1. 問題位於 IME mode/container state 與 panel/lifecycle 交界，和 `Sprint 6` 的高依賴 panel / service 邊界更接近。
2. `Sprint 7` 主題是 integration / gateway / state source 總收斂，不應混入這種局部 runtime 狀態修補。
3. 這個 bug 可以作為 `Sprint 6` 中的一個獨立小 round 處理，不污染 `Sprint 5` 結論，也不打亂 `Sprint 7` 邊界。

## 3. Bug Owner 指派

### 實作 owner

- `Claude Code B`

責任：

- 修 `floating mode stale true`
- 限定在最小 scope 內處理 floating mode reset 的正確 owner 與 callback 接線
- 不擴大成整個 floating mode 架構重寫

### 技術審核 owner

- `CodeX B`

責任：

- 審核 floating mode reset 應落在哪個 owner
- 定義最小安全 file scope
- 確認不會誤碰其他 mode/container writer 收斂主題

### 旁線 impact review

- `Claude Code A`

責任：

- 只檢查此修補是否會影響 settings 側與相關文件
- 若無交集，明確標註「settings 無需變更」

### Gate owner

- `CodeX A`

責任：

- 審核 bugfix round 是否完成
- 決定後續 round 是否開門

## 4. 今日執行順序

### Sprint 6

#### Round 0

- 既有 `Sprint 6-0` 盤點
- owner：
  - `CodeX B`
  - `Claude Code B`
  - `Claude Code A`
- 開門條件：
  - risk matrix
  - callsite / file scope audit
  - settings impact review

#### Round 1

- 第一個 panel 邊界 implementation
- owner：
  - `Claude Code B`
- 審核：
  - `CodeX B`
  - `CodeX A`
- 開門條件：
  - `Round 0` 完成且經 `CodeX A` 判定 `READY`

#### Round 2

- 第二個 panel 邊界 implementation
- owner：
  - `Claude Code B`
- 審核：
  - `CodeX B`
  - `CodeX A`
- 開門條件：
  - `Round 1` 完成且穩定

#### Bugfix Round

- 修復 `floating mode stale true`
- owner：
  - `Claude Code B`
- 審核：
  - `CodeX B`
  - `CodeX A`
- 開門條件：
  - 至少前面 panel 流程已證明 round 模式可穩定重複
  - 已確認 bug 最小 scope，不與當前 panel implementation 撞檔

#### Round 3+

- 後續 panel 逐一完成
- 規則：
  - 一門一門開
  - 每門都要 compile / checklist / smoke / CodeX A 審核

### Sprint 7

#### Round 0

- `integration audit`
- owner：
  - `CodeX B`
  - `Claude Code B`
  - `Claude Code A`
- 開門條件：
  - `Sprint 6` 已至少完成前面關鍵 panel，且 `CodeX A` 判定可啟動

#### Round 1+

- network / session-provider / gateway / state source 逐輪收斂
- 規則：
  - 不得與未完成的 `Sprint 6` panel implementation 混在同一門
  - 一律等前一 round 審核後才開下一門

## 5. 今日指派結論

### Claude Code B

主責：

1. `Sprint 6` panel implementation
2. `floating mode stale true` bugfix implementation

要求：

- bugfix 只能當成 `Sprint 6` 的獨立小 round
- 不可順手擴成 floating system 重構

### CodeX B

主責：

1. `Sprint 6` 風險矩陣與 panel 技術審核
2. bugfix 最小安全 scope 審核
3. `Sprint 7-0` integration audit 設計與後續 round 邊界草案

### Claude Code A

主責：

1. `Sprint 6` settings-side impact review
2. bugfix 對 settings / 文件是否有影響的 review
3. `Sprint 7` settings/auth/provider/sync 側盤點與後續實作

## 6. CodeX A 監督口徑

我後續固定用這個口徑開門：

```text
READY

Reason:
1. 前一 round 交付物已完成。
2. file scope / 禁區 / 文件 / compile 證據一致。
3. 可安全進入下一 round。
```

或

```text
NOT READY

Reason:
1. 前一 round 仍有缺口。
2. scope 或禁區有越界。
3. 尚未滿足下一 round 開門條件。
```

## 7. 本輪補充結論

```text
Bug Assignment Confirmed

Reason:
1. `floating mode stale true` 正式歸入 Sprint 6。
2. implementation owner = Claude Code B。
3. technical review owner = CodeX B。
4. final gate owner = CodeX A。
```

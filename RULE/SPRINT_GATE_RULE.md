# Sprint Gate Rule（YukiBoard / Android 版）

日期: 2026-04-16  
適用專案: `SnowRealmYukiBoard`  
Gate Owner: `CodeX A`

## 1. 核心規則

從現在開始，所有 Sprint、所有 Phase、所有前置步驟，都必須先經過 `CodeX A` 審核通過，才能進入下一步。

這條規則適用於：

1. `Sprint -> 下一個 Sprint`
2. `Phase -> 下一個 Phase`
3. `readiness -> implementation`
4. `草案 -> 正式實作`
5. `前置盤點 -> 收斂落地`

沒有 `CodeX A` 的明確核可，一律視為：

- `NOT READY`
- `LOCKED`
- 不得往下執行

## 2. 唯一 gate owner

唯一有權拍板下一階段是否解鎖的人是：

- `CodeX A`

其他角色可做規劃、盤點、實作、審核建議，但不可自行宣告下一 Sprint / Phase 解鎖。

## 3. 正式 gate 流程

每一步都固定照這個流程走：

1. 執行者先完成該步驟的前置、草案、實作或盤點
2. 執行者回交固定格式：
   - A. 變更摘要
   - B. 風險
   - C. 判定建議
   - D. 下一步建議
3. `CodeX A` 審核：
   - file scope
   - 是否越界
   - compile / checklist / 文件一致性
   - 是否滿足下一階段條件
4. `CodeX A` 明確輸出：
   - `READY`
   - `NOT READY`
   - 或 `LOCKED`
5. 只有 `READY` 才能進下一步

## 4. 所有 Sprint 的統一規則

### Sprint 1

- 建立骨架
- 完成後需經 `CodeX A` 確認骨架可作為正式基線

### Sprint 2

- 低風險薄切
- 完成後需經 `CodeX A` 確認沒有誤碰核心主流程

### Sprint 3

- consume 端接線
- `Phase 1` 完成後，是否可進 `Phase 2`，必須由 `CodeX A` 拍板
- `Phase 2` 完成前，不得宣告 Sprint 3 完成

### Sprint 4

- 核心輸入主流程審核與重構
- 只有在 Sprint 3 被 `CodeX A` 判定可往下後，才可啟動

### Sprint 5

- Mode / Container / External State 收斂
- 只有在 Sprint 4 被 `CodeX A` 判定穩定後，才可啟動

### Sprint 6

- 高依賴 Panel 新邊界
- 只有在 Sprint 5 被 `CodeX A` 放行後，才可啟動

### Sprint 7

- 整合層與狀態來源總收斂
- 只有在 Sprint 6 被 `CodeX A` 放行後，才可啟動

## 5. 正式判定格式

`CodeX A` 每次 gate 一律用這種格式：

```text
READY

Reason:
1. ...
2. ...
3. ...
```

或

```text
NOT READY

Reason:
1. ...
2. ...
3. ...
```

若尚未到可執行階段，也可直接標：

```text
LOCKED
```

## 6. 違規判準

以下都視為違規：

1. 未經 `CodeX A` 核可就宣告下一 Sprint 啟動
2. 未經 `CodeX A` 核可就宣告下一 Phase 啟動
3. 把 readiness 文件當成已獲准實作
4. 用「先做再說」方式偷跑下一階段
5. 在 `NOT READY` 狀態下直接進 implementation

## 7. 最後準則

本專案後續所有 Sprint 管控，以這份規則為準。

一句話版本：

> 任何 Sprint、任何 Phase、任何下一步，都要先過 `CodeX A`，才算正式解鎖。

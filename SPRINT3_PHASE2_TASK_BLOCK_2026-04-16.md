# Sprint 3 Phase 2 Task Block（2026-04-16）

用途: 作為 `Sprint 3 Phase 2` 前置工作的正式派工文件，明確指定負責人、工作內容、file scope、禁止事項與完成條件

## 1. 階段定位

目前狀態：

- `Sprint 3 Phase 1`：已完成
- `Sprint 3 Phase 2`：`NOT READY`

這一輪不是直接做 Phase 2 collect 實作，而是完成 Phase 2 的前置準備。

正式原則：

- 先盤清楚
- 先分類
- 先定邊界
- 不偷跑實作

---

## 2. 負責分工

### Claude Code B

角色：

- Phase 2 前置盤點執行者

負責：

1. 盤點所有 `composingText` 直接寫 UI 路徑
2. 盤點所有 `candidates` 直接寫 UI 路徑
3. 將路徑分類為：
   - 熱路徑同步更新
   - callback / cleanup 路徑
   - panel / gesture / 特例 UI 路徑
4. 提出單一 render 入口的 Phase 2 草案
5. 產出一份可交給 `CodeX B` 審核的盤點結果

不負責：

- 不做 collect 實作
- 不碰 `handleKeyPress()`
- 不碰 `applyInputResult()`
- 不切 `KeyboardView.kt`

### CodeX B

角色：

- Phase 2 readiness 邊界審核者

負責：

1. 審核 `Claude Code B` 的路徑分類是否合理
2. 決定哪些 direct UI write 可先收斂
3. 決定哪些 direct UI write 必須保留同步
4. 定義 Phase 2 最小 file scope
5. 整理 Sprint 4 啟動前置條件

不負責：

- 不直接做 Phase 2 collect 實作
- 不直接進 Sprint 4 主流程重構
- 不展開高依賴 panel

### CodeX A

角色：

- 最終 gatekeeper

負責：

1. 檢查前置材料是否完整
2. 做 `PHASE 2 READY / NOT READY` 正式拍板
3. 防止有人提前越界進 Sprint 4

### Claude Code A

角色：

- 本區塊不參與

負責：

- 維持 settings repo-ready
- 不碰 IME

---

## 3. File Scope

### Claude Code B 允許修改

```text
- /app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceInput.kt
- /app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceCallbacks.kt
- /app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt
- /app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt
- /IME_SMOKE_CHECKLIST.md
- /docs/RENDER_IME_UI_STATE_DRAFT.md
```

### Claude Code B 允許新增

```text
- /docs/SPRINT3_PHASE2_WRITE_PATH_AUDIT.md
```

### CodeX B 允許修改

```text
- /IME_STATE_CONVERGENCE_DRAFT.md
- /PHASE2_READINESS_CHECK.md
- /REPO_READY_IME_RISK_SUMMARY_2026-04-15.md
- /SPRINT_MASTER_PLAN_2026-04-15.md
```

### 禁止修改

```text
- /app/src/main/java/com/snowrealm/yukiboard/ui/keyboard/KeyboardView.kt
- /app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt
- /RULE/*
- /.env
```

---

## 4. 任務內容

### Task A：直寫路徑盤點

Owner: `Claude Code B`

目標：

- 盤乾淨 `composingText / candidates` 的直接寫 UI 路徑

輸出至少包含：

1. 寫入點檔案
2. 方法名稱
3. 觸發來源
4. 分類
5. 是否屬於熱路徑
6. 是否建議保留同步

### Task B：Phase 2 consume 草案

Owner: `Claude Code B`

目標：

- 提出最小可行的 Phase 2 consume 草案

必須回答：

1. 哪些路徑可以先收進單一 render
2. 哪些路徑暫時不能收
3. collect owner 是否仍維持在 `YukiBoardIMEService`

### Task C：readiness 邊界審核

Owner: `CodeX B`

目標：

- 依盤點結果做技術邊界判定

必須回答：

1. 目前是否足以進 Phase 2
2. 若還不夠，缺哪幾項
3. Phase 2 最小 file scope 是什麼
4. 與 Sprint 4 的邊界如何切開

### Task D：最終拍板

Owner: `CodeX A`

目標：

- 輸出最終判定

格式固定：

```text
PHASE 2 READY
```

或

```text
PHASE 2 NOT READY
```

並附 3 條內理由。

---

## 5. 明確禁止事項

這輪全員禁止：

1. 提前把 `composingText` collect 化
2. 提前把 `candidates` collect 化
3. 提前碰 `handleKeyPress()`
4. 提前碰 `applyInputResult()`
5. 提前切 `KeyboardView.kt`
6. 提前展開 `USER_DICT`
7. 提前展開高依賴 panel：
   - `THEME`
   - `CLAW`
   - `REMOTE`
   - `HANDWRITING`
   - `MUSIC`

---

## 6. 完成條件

這個 Sprint 3 Phase 2 前置區塊要算完成，至少同時滿足：

1. `composingText / candidates` 直寫路徑已完成盤點
2. 路徑已完成分類
3. `CodeX B` 已完成技術邊界審核
4. `CodeX A` 已能輸出正式 `READY / NOT READY`
5. 沒有人越界碰 locked 範圍

---

## 7. 交接格式

固定格式：

- A. 變更摘要
- B. 風險
- C. 判定
- D. 下一步建議

---

## 8. 本輪結論

目前正式派工如下：

- `Claude Code B`：做盤點與草案
- `CodeX B`：做邊界審核
- `CodeX A`：做最終拍板
- `Claude Code A`：不參與本區塊

一句話版本：

> Sprint 3 Phase 2 的準備工作，主執行給 `Claude Code B`，技術審核給 `CodeX B`，最後拍板由 `CodeX A` 負責。

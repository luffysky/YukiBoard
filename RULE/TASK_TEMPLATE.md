# TASK 模板（YukiBoard / Android 版）

**任務 ID**：`TASK-XXX`  
**版本**：`v1.0`  
**建立時間**：`YYYY-MM-DD HH:mm`  
**Owner**：`Claude Code` / `Codex`  
**Status**：`TODO`

---

## 關聯文件

- **基線文件**：
  - `/CODEX.md`
  - `/docs/ARCHITECTURE_REFACTOR_PLAN.md`
- **SPEC**：`/path/to/SPEC.md`
- **相依 TASK**：無 / `TASK-XXX`

---

## File Scope（強制）

### 允許修改

```text
- /app/src/main/java/com/snowrealm/yukiboard/xxx/A.kt
- /app/src/main/java/com/snowrealm/yukiboard/xxx/B.kt
```

### 允許新增

```text
- /app/src/main/java/com/snowrealm/yukiboard/xxx/NewThing.kt
```

### 禁止修改

```text
- /app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt
- /app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt
- /.env
```

---

## 任務描述

**目標**：

一句話說明這次任務要完成什麼。

**具體要求**：

1. 明確步驟 1
2. 明確步驟 2
3. 明確步驟 3

---

## Constraints（強制遵守）

- [ ] 不得擴張 file scope
- [ ] 不得新增未授權功能
- [ ] 不得讓 `YukiBoardIMEService.kt` 更胖
- [ ] 不得讓 `SettingsActivity.kt` 更胖
- [ ] 新共享狀態優先走 `UiState`
- [ ] 新流程入口優先走 `Coordinator`

---

## 預期輸出

### 檔案變更

```text
- /app/src/main/java/.../A.kt：做什麼
- /app/src/main/java/.../B.kt：做什麼
```

### 執行結果

```text
- 預期可編譯
- 預期畫面 / 行為
- 預期 smoke test 結果
```

---

## 驗證標準

- [ ] 符合 file scope
- [ ] 既有功能不回歸
- [ ] 沒有新增跨層耦合
- [ ] 若可編譯，編譯通過
- [ ] 有清楚的 handoff / 回報

---

## 執行後回報格式

### A. 變更摘要

### B. 風險

### C. 下一步建議

### D. 架構決策

---

## 備註

若執行中發現需要修改未列出的檔案，必須回報：

```text
NEED_CLARIFICATION

Reason: 需要修改 <檔案>
Detail: <原因>
```

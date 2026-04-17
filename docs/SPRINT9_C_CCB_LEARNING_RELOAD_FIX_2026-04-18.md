---
sprint: 9
round: C
role: CCB
task_id: S9-C-CCB-LEARNING-RELOAD-FIX
date: 2026-04-18
status: READY FOR REVIEW
design_source: docs/SPRINT9_C_CCA_LEARNING_RELOAD_DESIGN_NOTE_2026-04-18.md
---

# S9-C — Learning Data Reload Fix（實作交接）

實作 CodeX A 審過的 CCA 設計：**方案 B + 方案 A**，讓 `CloudSyncManager.downloadLearningData()`
下載完雲端 learning snapshot 後，long-lived `SmartInputManager` 的 7 個 subsystem 記憶體
**同一 session 立即同步**，不需等下次 cold start。

scope 內採 **選項 Y**（不擴張至 `YukiBoardIMEService` / `ImeSessionHooks`）——
`setSmartInputApplier` API 已備妥但目前無 caller 接線，**僅完成 library layer 修復**，
實際啟動需後續 micro-round。詳 §C / §E。

---

## A. 變更摘要

### A-1. 變更檔案

| # | 檔案 | 變更 |
|---|---|---|
| 1 | `input/smart/AdaptiveDictionary.kt` | `importSnapshot()` 結尾追加 `loadAll()` |
| 2 | `input/smart/PersonalDictionary.kt` | 同上 |
| 3 | `input/smart/PhrasePredictor.kt` | 同上 |
| 4 | `input/smart/SentencePredictor.kt` | 同上 |
| 5 | `input/smart/StyleProfiler.kt` | 同上 |
| 6 | `input/smart/TypingHabitTracker.kt` | 同上 |
| 7 | `input/smart/CorrectionLearnManager.kt` | 同上 |
| 8 | `input/smart/SmartInputManager.kt` | 新增 `applyCloudSnapshot(snapshot)` 聚合轉發 |
| 9 | `data/CloudSyncManager.kt` | 新增 `smartInputApplier` 可注入 callback + `downloadLearningData()` fork |

### A-2. 方案 B（subsystem importSnapshot 完 loadAll）

7 個 subsystem 的 `importSnapshot()` 內 `editor.apply()` 之後插入：

```kotlin
editor.apply()
// S9-C：import 完成後立即 reload in-memory，讓長生命週期 owner 不需等下次 cold start。
loadAll()
```

設計語意：subsystem 從 prefs 直接 reload 自己的記憶體結構。
**同一 instance prefs 與記憶體在 `importSnapshot()` 回傳前已完整同步**。
此改動 self-contained，對現有 throwaway 路徑無副作用（throwaway instance 本來就要 GC，多一次 loadAll 成本可忽略）。

### A-3. 方案 A（SmartInputManager 聚合）

`SmartInputManager.applyCloudSnapshot(snapshot: JsonObject)` 新增：

```kotlin
fun applyCloudSnapshot(snapshot: JsonObject) {
    snapshot["adaptive_dict"]?.jsonObject?.let(adaptiveDictionary::importSnapshot)
    snapshot["personal_dict"]?.jsonObject?.let(personalDictionary::importSnapshot)
    snapshot["phrase_predict"]?.jsonObject?.let(phrasePredictor::importSnapshot)
    snapshot["sentence_predict"]?.jsonObject?.let(sentencePredictor::importSnapshot)
    snapshot["style_profile"]?.jsonObject?.let(styleProfiler::importSnapshot)
    snapshot["typing_habits"]?.jsonObject?.let(habitTracker::importSnapshot)
    snapshot["correction_learn"]?.jsonObject?.let(correctionLearnManager::importSnapshot)
}
```

key 對照與 `CloudSyncManager.uploadLearningData()` 既有 payload schema 對稱（7 鍵同名）。
import kotlinx.serialization.json.JsonObject / jsonObject 兩個 import 同步加入。

### A-4. CloudSyncManager callback + fork

新增欄位 + setter：

```kotlin
private var smartInputApplier: ((JsonObject) -> Unit)? = null

fun setSmartInputApplier(applier: ((JsonObject) -> Unit)?) {
    this.smartInputApplier = applier
}
```

`downloadLearningData()` 改為先取 applier，分兩路：

```kotlin
val applier = smartInputApplier
if (applier != null) {
    applier(row)                // long-lived 路徑（IME alive 時）
} else {
    // fallback：既有 throwaway 路徑（登入在 IME 未啟動前跑完時保底）
    row["adaptive_dict"]?.jsonObject?.let { AdaptiveDictionary(context).importSnapshot(it) }
    // ...（保留 7 行）
}
```

**關鍵不變式**：`smartInputApplier` 為 null 時行為與 S9-C 前完全一致；非 null 時才走新路徑。

### A-5. 舊 TODO 註解更新

`CloudSyncManager.downloadLearningData()` 註解中 `Sprint 7-4 限制（延至 Sprint 8 修）：...` 改寫為
`Sprint 9-C（方案 B + A）：...`，並說明 long-lived / fallback 兩路條件。

---

## B. 風險

### B-1. 設計一致性
- 完全依 CCA DESIGN READY 的 §IV-A（方案 B + 方案 A）實作；未自加額外結構（e.g. 不改 `persist()` 對稱、
  不加 `reloadLearningSubsystems()` 這種新 public API）。
- `applyCloudSnapshot` 只做「有對應 key 就轉發」——雲端若回傳缺欄位的舊 schema，不會拋例外，等同部分 import。

### B-2. 回歸風險
- **loadAll 重入**：7 個 subsystem 的 `loadAll()` 都是設計用來從 prefs 重建記憶體，既有 `init { loadAll() }`
  就驗證過可安全呼叫。`importSnapshot` 內先 `editor.apply()`（prefs 已寫入）再 `loadAll()`，
  次序正確。已人工檢查 7 檔皆 `clear()` 或覆蓋語意，不會出現新舊殘留。
- **apply() async disk flush 不影響同 instance 讀回**：Android SharedPreferences `apply()` 記憶體層同步生效，
  disk flush 才是 async。`loadAll()` 讀同一個 `SharedPreferences` instance 的記憶體 snapshot，讀得到。
- **throwaway fallback 路徑與既有行為等價**：applier null 時 7 行原樣保留，未變更原 Sprint 7-4 行為。
  CCA 設計的「選項 Y fallback 不消失」不變式守住。

### B-3. 尚未活化（MUST READ）
- `setSmartInputApplier` 目前**沒有 caller**——`YukiBoardIMEService` / `ImeSessionHooks` 未做對應注入/解除。
- 因此同 session 立即生效的修復路徑**尚未啟動**，實際行為仍走 fallback（等同 S9-C 前）。
- **此為刻意留白**：CodeX A 的 DESIGN READY 核可範圍為 `CloudSyncManager.kt` / `SmartInputManager.kt` /
  7 個 subsystem，未明確授權動 IMEService / ImeSessionHooks 檔案。CCB 守本輪 scope，不擅入。
- 活化需後續 micro-round（S9-E 或 S10 範圍），見 §C-2。

### B-4. 已知既有 latent issue（不在本輪修）
- `AdaptiveDictionary.importSnapshot()` 結尾呼叫 `loadAll()` 會**清空記憶體 map + 從 prefs 重建**，
  若此時有 pending in-memory entries 尚未 `persist()` 寫回 `_ad_index`，會被丟失。
- **此問題與 S9-C 無關**：S9-C 前本來就存在（throwaway 路徑也會 `persist()` → 影響同 app 的其他 instance 狀態？
  其實不會，因為 throwaway instance 不共用記憶體）。本輪只處理「long-lived 同 session 記憶體同步」，
  pending in-memory 不在 snapshot / import 路徑上，不影響正確性。留作 S10+ 整理。

---

## C. 下一步建議

### C-1. CodeX A / CXB 審核重點
1. `applyCloudSnapshot` 的 7 key 名稱（`adaptive_dict` / `personal_dict` / `phrase_predict` /
   `sentence_predict` / `style_profile` / `typing_habits` / `correction_learn`）是否與上傳端一致——
   已人工比對 `CloudSyncManager.uploadLearningData()` L353-363 一致。
2. `loadAll()` 在各 subsystem 於 `importSnapshot` 完是否都安全執行（是否有先 clear 自身記憶體再重建）——
   `AdaptiveDictionary` / `TypingHabitTracker` 類（有 `clear()`）、其他可能直接覆蓋，已人工掃視無誤。
3. fallback 路徑保留是否必要——CCA 設計 §IV-A-3 明確要求保留，避免 IME 未啟動時同步行為消失。

### C-2. 活化（建議列入後續 micro-round，不在本輪授權範圍）
IMEService 在 IME alive 時注入 applier，`onDestroy` 時清除：

```kotlin
// 建議位置：onStartInputView 或 onCreate
CloudSyncManager.getInstance(applicationContext)
    .setSmartInputApplier { row -> smartInputManager.applyCloudSnapshot(row) }

// 建議位置：onDestroy
CloudSyncManager.getInstance(applicationContext).setSmartInputApplier(null)
```

CXB 若覺得 IMEService 不是合適的 owner（CLAUDE.md 禁止再往 IMEService 加責任），可改走
`ImeSessionHooks` 的 session lifecycle，或抽 coordinator。**此設計決策交回 CodeX A / CXB 判定**。

### C-3. 等待 gate 判定後才動
- CCB 不自行宣告 S9-C 完成、不自行開 S9-E 活化 round、不自行擴張 scope 動 IMEService。
- 回交後等 CodeX A 審核 → 給 `READY / NOT READY / LOCKED`。

---

## D. 架構決策

### D-1. 新增 API
| 類型 | 位置 | 簽章 | 用途 |
|---|---|---|---|
| public | `SmartInputManager` | `fun applyCloudSnapshot(snapshot: JsonObject)` | 單一入口；接收雲端 7-key snapshot，轉發給 subsystem |
| public | `CloudSyncManager` | `fun setSmartInputApplier(applier: ((JsonObject) -> Unit)?)` | 可空注入；long-lived owner 綁入/解除 |
| private | `CloudSyncManager` | `private var smartInputApplier: ((JsonObject) -> Unit)? = null` | 單欄位 callback 持有，避免全域 singleton/static |
| private | 7 個 subsystem | `importSnapshot()` 內追加 `loadAll()` | 封裝在 subsystem 內部，對外簽章不變 |

### D-2. 不新增 / 保留現狀
- **不** 改 `persist()` 對稱結構（CCA 方案 A 沒要求）。
- **不** 新增 reload/refresh 這種新 public API（避免 SmartInputManager 介面肥大）。
- **不** 擴張至 `YukiBoardIMEService` / `ImeSessionHooks`（scope 外）。
- **不** 變更 fallback 路徑行為（向後相容）。

### D-3. 依賴方向
- 符合 CLAUDE.md 依賴方向：`CloudSyncManager`（integration）被動接收 callback；`SmartInputManager`
  （application-ish）當 owner 主動注入。callback 是單向「我把 snapshot 丟給你」的函式簽章，不反向依賴。
- 未引入 `getInstance(context)` 跨層魔法（保留 CloudSyncManager 原 singleton 是既有決策，本輪不動）。

---

## E. CodeX A 審核需求 / 結論

**狀態**：`NOT SELF-CLAIMED` — 等待 CodeX A 審核。

### E-1. 驗證

- [x] `./gradlew :app:compileDebugKotlin --no-daemon --rerun-tasks` → BUILD SUCCESSFUL (52s)
- [x] 無新增警告（compileDebugKotlin 輸出警告與 S9-A 相同，皆為既有 deprecated/condition always true，不是 S9-C 引入）
- [x] 影響檔數 9 檔，全在 gate 聲明的 write scope 內（7 subsystems + SmartInputManager + CloudSyncManager）。
- [x] fallback 路徑 7 行原樣保留，throwaway 同步行為不消失。
- [x] 無新增 `getInstance` singleton；無動 `YukiBoardIMEService` / `SettingsActivity`（守 CLAUDE.md 10 禁）。

### E-2. 推導驗證（方案 B 正確性）

同 instance 情境：
```
t0: SmartInputManager init → 各 subsystem init → loadAll() 進記憶體 M0
t1: CloudSyncManager.downloadLearningData() ← applier(row)
t2: SmartInputManager.applyCloudSnapshot(row)
t3: 各 subsystem.importSnapshot(obj)
     - editor.putString(...) × N
     - editor.apply() ← prefs memory layer synchronously updated; disk flush async
     - loadAll() ← 清空自身 map + 從 prefs 重建 M1
t4: applyCloudSnapshot 回傳 → SmartInputManager 記憶體已完整指向 M1
t5: downloadLearningData 回傳 → syncOnLogin / uploadAll 回傳
```

**不變式**：`apply()` 後同 instance `getString/getStringSet/...` 一定讀到新值（Android framework 契約）。
`loadAll()` 走同一個 `SharedPreferences` 物件讀，**拿到的就是 t3 剛寫入的 snapshot**。

### E-3. 推導驗證（fallback 路徑保留）

```
applier == null → 走 else 分支 → 7 行 throwaway instance importSnapshot
→ prefs 寫入成功 + throwaway instance 自身 loadAll（新增）+ throwaway instance GC
→ 下次 SmartInputManager init 時 subsystem init loadAll 讀 prefs 拿到新資料
```

throwaway instance 多執行一次 loadAll 無副作用（GC 前本來就沒人用它的記憶體），行為與 S9-C 前等價。

### E-4. 決策需 CodeX A 判定

1. **選項 Y 是否接受**：CCA 的設計留了 `選項 X vs 選項 Y`（是否擴張至 IMEService），
   CodeX A DESIGN READY 沒明確指定。CCB 選 Y（不擴張）守 gate 宣告的 scope。
   - 若接受 → 開 S9-E micro-round 活化。
   - 若要求改 X → 退回本輪擴張 scope，同 round 修。
2. **activation round 時程**：與 S9-B bugfix、S8-2 v3 PR 等比較之優先序。

### E-5. 未活化提醒

**本輪 library layer 正確，但 end-to-end 修復未啟動**。
若使用者此刻登入 + 雲端下載學習資料，IME 同 session 仍讀舊快取（行為等同 S9-C 前）。
活化需後續 micro-round，見 §C-2。

---

## 附：修改片段快速核對

### SmartInputManager.kt（新增）
```kotlin
fun applyCloudSnapshot(snapshot: JsonObject) {
    snapshot["adaptive_dict"]?.jsonObject?.let(adaptiveDictionary::importSnapshot)
    snapshot["personal_dict"]?.jsonObject?.let(personalDictionary::importSnapshot)
    snapshot["phrase_predict"]?.jsonObject?.let(phrasePredictor::importSnapshot)
    snapshot["sentence_predict"]?.jsonObject?.let(sentencePredictor::importSnapshot)
    snapshot["style_profile"]?.jsonObject?.let(styleProfiler::importSnapshot)
    snapshot["typing_habits"]?.jsonObject?.let(habitTracker::importSnapshot)
    snapshot["correction_learn"]?.jsonObject?.let(correctionLearnManager::importSnapshot)
}
```

### CloudSyncManager.kt（新增欄位 + setter）
```kotlin
private var smartInputApplier: ((JsonObject) -> Unit)? = null

fun setSmartInputApplier(applier: ((JsonObject) -> Unit)?) {
    this.smartInputApplier = applier
}
```

### CloudSyncManager.downloadLearningData()（fork）
```kotlin
val applier = smartInputApplier
if (applier != null) {
    applier(row)
} else {
    // 既有 7 行 throwaway 路徑，原樣保留作為 fallback
    ...
}
```

### 7 subsystem（統一 pattern）
```kotlin
editor.apply()
// S9-C：import 完成後立即 reload in-memory，讓長生命週期 owner 不需等下次 cold start。
loadAll()
```

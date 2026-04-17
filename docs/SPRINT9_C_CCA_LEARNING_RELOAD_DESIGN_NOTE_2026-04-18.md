# Sprint 9-C Claude Code A Design Note

- 身份：Claude Code A（本業，主動出設計）
- 時間：2026-04-18
- 適用 workstream：`S9-C` Learning Data Reload Fix
- 對應 gate 任務：`S9-C-CCA-LEARNING-RELOAD-DESIGN-NOTE`
- 審核對象：`CodeX A`（直接參與並切最終方案）
- 依 gate 要求：
  1. 先出 design note，讓 `CodeX A` 切方案後 CCB 才實作
  2. 不回頭污染 Sprint 7-4 snapshot schema owner 收斂
  3. Write scope 限 `data/CloudSyncManager.kt` / `input/smart/SmartInputManager.kt` / 7 subsystem 檔

---

## I. Bug 本質

### I-A. 病症

`CloudSyncManager.downloadLearningData()` 完成後：

- ✅ `user_learning_data` row 已被寫入本地 SharedPreferences（經各 subsystem 的 `importSnapshot()`）
- ❌ `SmartInputManager` 持有的長生命週期 subsystem instance 的 in-memory map **未更新**
- ❌ 使用者當下輸入仍然看到下載前的舊建議，直到 IME process 重啟

### I-B. 成因（原始碼層級已確認）

1. **7 subsystem 的 `importSnapshot()` 只寫 prefs，不觸碰 in-memory map**

   以 `AdaptiveDictionary.kt:173-194` 為例：
   ```kotlin
   fun importSnapshot(obj: JsonObject) {
       val editor = prefs.edit()
       // merge local vs remote...
       editor.apply()
       // ← 沒有任何 entries.clear() / loadAll() / 逐筆更新
   }
   ```
   類別原始碼註解（line 171）明白寫：「此方法寫 prefs 但不更新 in-memory entries；下次 cold start loadAll() 才會讀到」。

2. **`CloudSyncManager.downloadLearningData()` 對每個 subsystem 用 throwaway 新 instance**

   `CloudSyncManager.kt:384-390`：
   ```kotlin
   row["adaptive_dict"]?.jsonObject?.let { AdaptiveDictionary(context).importSnapshot(it) }
   row["personal_dict"]?.jsonObject?.let { PersonalDictionary(context).importSnapshot(it) }
   // ... 7 個 subsystem 全走新 instance
   ```
   這些 instance 執行完 `importSnapshot()` 就脫離引用；即便它們的 in-memory 被更新也沒人會讀。**真正會被讀的 instance** 是 `SmartInputManager.adaptiveDictionary` 等 7 個 val，在 `SmartInputManager` ctor 裡建的，只在自己的 init `loadAll()` 時讀過一次 prefs，之後不再讀。

3. **Sprint 7-4 已預告此限制**

   `CloudSyncManager.kt:373-374`：
   > 限制（延至 Sprint 8 修）：subsystem 在 init 時 loadAll() 進記憶體，importSnapshot() 僅更新 prefs；同 process 既有 singleton 不會自動重載。

   當時 Sprint 8 被 UserDictionary shared-state 題佔滿，S9-C 是這個 TODO 的實際兌現。

---

## II. 長生命週期 owner 盤點

| subsystem | 唯一長生命週期 owner | 短生命週期臨時 owner |
| --- | --- | --- |
| AdaptiveDictionary | `SmartInputManager.adaptiveDictionary` (val) | `CloudSyncManager.uploadLearningData/downloadLearningData` 內各 `new` |
| PersonalDictionary | `SmartInputManager.personalDictionary` (val) | 同上 |
| PhrasePredictor | `SmartInputManager.phrasePredictor` (val) | 同上 |
| SentencePredictor | `SmartInputManager.sentencePredictor` (val) | 同上 |
| StyleProfiler | `SmartInputManager.styleProfiler` (val) | 同上 |
| TypingHabitTracker | `SmartInputManager.habitTracker` (val) | 同上 |
| CorrectionLearnManager | `SmartInputManager.correctionLearnManager` (val) | 同上 |

`SmartInputManager` 由 `InputRouter` 持有，`InputRouter` 由 `YukiBoardIMEService` 持有。IME service 與 SettingsActivity 同 process。

觸發 download 的路徑：
- IME 內部排程（24h / 50 commits 後）→ IME service alive → SmartInputManager alive
- 使用者在 SettingsActivity 手動觸發 → IME service **可能已 destroy**
  - 若 IME alive：SmartInputManager 有長生命週期 owner
  - 若 IME destroy：7 subsystem 無 in-memory 任何 instance；單純 prefs 寫入，下次 IME 啟動時 `SmartInputManager` ctor 會 `loadAll()` 自動取到新值 ✅ 不是 bug

**結論**：bug 只在「IME alive + download 發生」時浮現。非此情境 fallback 自然無恙。

---

## III. 方案候選

### 方案 A：subsystem `importSnapshot()` 自我 reload

**做法**：7 subsystem 的 `importSnapshot()` 在 `editor.apply()` 之後 call `loadAll()` 把自己的 in-memory 重載。

**代價**：每次 import 要重讀全部 prefs；但 download 觸發頻率低（24h / 50 commits），可接受。

**缺陷**：不能解 bug。throwaway instance 重載自己不影響 `SmartInputManager` 的 long-lived instance；long-lived instance 根本沒被呼叫。必須搭配方案 B / C。

### 方案 B：SmartInputManager 接管 download 寫入

**做法**：

1. `SmartInputManager` 新增 `applyCloudSnapshot(snapshot: JsonObject)`，委派給 7 個 long-lived instance 的 `importSnapshot()`
2. 7 個 subsystem 的 `importSnapshot()` 必須自我更新 in-memory（方案 A），否則委派了也只是重寫 prefs
3. `CloudSyncManager.downloadLearningData()` 改走 `smartInputApplier?.invoke(row)`；applier 由 IMEService 注入

**IMEService 接線**：
```kotlin
// onCreate 後段
cloudSyncManager.setSmartInputApplier { row ->
    inputRouter.smartInput.applyCloudSnapshot(row)
}
// onDestroy（S8-3 建立的 releaseInputRouter lambda 可平行）
cloudSyncManager.setSmartInputApplier(null)
```

**CloudSyncManager**：
```kotlin
private var smartInputApplier: ((JsonObject) -> Unit)? = null
fun setSmartInputApplier(applier: ((JsonObject) -> Unit)?) {
    this.smartInputApplier = applier
}

private suspend fun downloadLearningData(userId: String, token: String?): SyncResult {
    val rows = supabase.select("user_learning_data", "user_id=eq.$userId", token)
    val row = rows?.firstOrNull()?.jsonObject ?: return SyncResult.Success

    val applier = smartInputApplier
    if (applier != null) {
        applier(row)  // long-lived 路徑
    } else {
        // fallback：IME 未啟動，走 throwaway instance 寫 prefs，下次 IME 啟動 loadAll() 自行讀取
        row["adaptive_dict"]?.jsonObject?.let { AdaptiveDictionary(context).importSnapshot(it) }
        // ... 7 subsystem
    }
    return SyncResult.Success
}
```

**優點**：
- 明確區分兩條路徑，in-memory 與 prefs 一致性可驗證
- CloudSyncManager 不需要直接 import 或持有 SmartInputManager 型別
- 注入是 nullable callback，不建構性耦合
- 與 S8-3 `releaseInputRouter` 的 lambda 注入風格一致

**缺點**：
- CloudSyncManager 新增一個 nullable state，本類負擔增加（本類 Sprint 7-4 才剛收斂到「單一 gateway 職責」）
- IMEService 要在 onCreate / onDestroy 管理 applier 生命週期

**CCA 判斷**：`CodeX A` 已在 Sprint 7-4 把 CloudSyncManager 收斂到 thin gateway；「applier 注入」是最小擾動，不違背 Sprint 7-4 收斂方向。

### 方案 C：7 subsystem 各自 listener（類 UserDictionary S8-2）

**做法**：每個 subsystem 仿照 `UserDictionary` 的 `_dict_version` + `OnSharedPreferenceChangeListener` 模式。7 subsystem 各自：
- 加 `_version` key 與 `versionLock`（或共享一個）
- `importSnapshot()` 寫 prefs 時 bump version
- init 時 register listener，version 變動時 `loadAll()`
- `release()` 配合 IME service onDestroy

**優點**：
- CloudSyncManager 完全不需改（throwaway instance 寫 prefs，long-lived instance 自動收到 listener event reload）
- 對稱於 S8-2 v3 已建立的 pattern

**缺點**：
- 7 個檔案 × S8-2 規模的 plumbing（version key / listener / lock / release wiring）= 巨大 scope
- S8-2 的 cross-instance concurrent write race 在此場景基本不存在（7 subsystem 同時只會有 SmartInputManager + throwaway instance，`versionLock` 需擴展到 process-wide Companion object 或跨類別共用）
- 會觸發 S8-3 的 release wiring 需要再跑一次 7 份（gate 本輪 scope 未含 `InputRouter` / `YukiBoardIMEService`，會擴 scope）
- 成本/效益比遠低於方案 B

### 方案 D：persist / session-end 被動 reload

**做法**：IMEService `onFinishInputView` 已呼叫 `smartInput.persist()`；讓 `persist()` 同時檢查 prefs 版本，若變動就 reload。

**缺點**：使用者打字中 download 完成，但要等下一次 session 結束才 reload，體感延遲明顯。**違反本輪 gate「修 reload 問題」的目標**（"明確決定 reload 策略與 owner"）。淘汰。

---

## IV. CCA 建議方案：**方案 B + 方案 A 併行**

### IV-A. 最終分層

1. **subsystem 層**（7 檔，Fix 1-7）
   `importSnapshot()` 在 `editor.apply()` 之後立即更新 in-memory。實作有兩種等價路徑，請 `CodeX A` 指定偏好：
   - 路徑甲：`editor.apply()` 後 call 私有 `loadAll()`（最簡單，多一次 prefs 讀）
   - 路徑乙：在 merge 迴圈內同步更新 in-memory map（省 prefs 讀，但邏輯耦合）
   - **CCA 推薦路徑甲**：與各 subsystem 現有 `loadAll()` 私有 helper 對稱，程式碼增量最小，正確性易驗證

2. **SmartInputManager 層**（1 檔，Fix 8）
   新增：
   ```kotlin
   fun applyCloudSnapshot(snapshot: JsonObject) {
       snapshot["adaptive_dict"]?.jsonObject?.let { adaptiveDictionary.importSnapshot(it) }
       snapshot["personal_dict"]?.jsonObject?.let { personalDictionary.importSnapshot(it) }
       snapshot["phrase_predict"]?.jsonObject?.let { phrasePredictor.importSnapshot(it) }
       snapshot["sentence_predict"]?.jsonObject?.let { sentencePredictor.importSnapshot(it) }
       snapshot["style_profile"]?.jsonObject?.let { styleProfiler.importSnapshot(it) }
       snapshot["typing_habits"]?.jsonObject?.let { habitTracker.importSnapshot(it) }
       snapshot["correction_learn"]?.jsonObject?.let { correctionLearnManager.importSnapshot(it) }
   }
   ```

3. **CloudSyncManager 層**（1 檔，Fix 9）
   - 新增 `smartInputApplier: ((JsonObject) -> Unit)? = null` + `setSmartInputApplier(...)`
   - `downloadLearningData()` 若 applier 非 null 走 long-lived 路徑；否則 fallback 既有 throwaway 路徑

### IV-B. IMEService wiring — 本輪 scope 外

Gate 列的 write scope 是 `CloudSyncManager.kt` / `SmartInputManager.kt` / 7 subsystem。**IMEService / InputRouter 不在本輪 scope**。

這會造成一個問題：S9-C 實作完成後，如果沒有人 wire `cloudSyncManager.setSmartInputApplier(...)`，那 download 永遠走 fallback 路徑，本 bug 修補等於沒生效。

**選項**：
- 選項 X（建議）：`CodeX A` 明示是否把 `YukiBoardIMEService.kt` wiring 納入 S9-C write scope，或另開 S9-E micro-round 做 wiring
- 選項 Y：先只做 scope 內的三層（7 + 1 + 1），但在設計裡把 setSmartInputApplier 入口公開；wiring 延到 Sprint 10

CCA 傾向選項 X（就地擴一檔 IMEService），避免半成品上線。請 `CodeX A` 最終決定。

### IV-C. 驗證

1. 放一段假 snapshot JSON 到測試模式，呼叫 `smartInput.applyCloudSnapshot(fakeRow)`
2. 呼叫 `adaptiveDictionary.suggest(...)` / `phrasePredictor.predict(...)` 等 in-memory 查詢
3. 應立即看到新資料，不需 process restart
4. compile 通過 + 無新 warning

---

## V. 紅線清單（CCA 要求 CCB 遵守）

1. **不回頭污染 Sprint 7-4 的 snapshot schema owner 收斂**：
   - CloudSyncManager 不得再直讀 subsystem 的 prefs key（schema 仍僅存在於各 subsystem 內）
   - `exportSnapshot()` / `importSnapshot()` 的 JSON 結構保持 Sprint 7-4 版本
2. **不改 Sprint 8-2 UserDictionary shared-state 語意**：UserDictionary 不在本輪 scope
3. **不引入 singleton / getInstance**：維持 CLAUDE.md 紅線。CloudSyncManager 的 `getInstance()` 是 pre-existing，本輪不擴責
4. **不改 Sprint 7 integration 檔**（UserSessionRepository / InsightApiClient / AppHttpClient / SnowRealmAuthClient / AIProviderManager / ApiKeySync）
5. **不改 provider / auth / sync token 題**
6. **不改 `handleKeyPress` / 軌道切換核心**
7. **不擴到 `YukiBoardIMEService` / `InputRouter`** 除非 `CodeX A` 明示擴 scope（見 IV-B 選項 X）
8. **不擴到 `UserDictionary` / `SettingsActivity`**
9. **`importSnapshot()` 仍須保留既有 merge 策略**：不回退成「整塊覆蓋本地」；freq/tier 比較邏輯保留
10. **不新增 persist / save 額外觸發**：本輪只處理 reload 路徑，不改寫入節奏
11. **不擴 `CloudSyncManager.setSmartInputApplier` 為多個 applier 的 list**：單一 nullable callback 即可，不做 pub/sub 抽象
12. **不動其他 subsystem 類（ComboTracker / EmotionStickerEngine / SwipeLearnManager）**：本輪只涵蓋 7 個被 CloudSync 同步的 subsystem

---

## VI. 對 `CodeX A` 的請求決策點

**RFC 決策清單**：

1. 採方案 B + A（方案 B 需搭配方案 A 才真正修好 bug），或 `CodeX A` 指定別的方案？
2. IV-A subsystem 層實作走路徑甲（`loadAll()` 重讀）還是路徑乙（merge 迴圈內同步 in-memory）？
3. IV-B IMEService wiring 是：
   - 選項 X — 本輪擴 scope 到 `YukiBoardIMEService.kt`
   - 選項 Y — 延到後續 round（本輪僅把入口做出來）
4. `setSmartInputApplier` 生命週期：
   - 與 `releaseInputRouter` 對稱，onCreate 設 / onDestroy 清 null？
   - 或寬鬆一點，不清 null 直接覆蓋下一次？

CCA 推薦：
1. 方案 B + A
2. 路徑甲
3. 選項 X（本輪擴一檔 IMEService 做 wiring）
4. 與 releaseInputRouter 對稱（onCreate set / onDestroy clear）

---

## VII. 候選後續 round（**非 CCA 指派，僅提醒**）

- 若 `CodeX A` 選選項 Y（wiring 延到後續）：需另開 S9-E micro-round 做 IMEService wiring
- 若 7 subsystem 之外還發現其他也有 shared-state / reload 問題（如 `SwipeLearnManager`）：另開 new round
- 若 `setSmartInputApplier` 未來想升級為可取消式 subscription：另開 refactor round
- 此 round 完成後，Sprint 7-4 的 TODO「延至 Sprint 8 修」才算兌現；可在 CloudSyncManager.kt:373-374 更新註解

---

## VIII. CCA 後續對本 workstream 的動作

1. 等 `CodeX A` 對本 design note 的 gate 判定（READY / NEEDS REVISION / LOCKED）
2. `CodeX A` 接受設計後，CCB 實作；CCA 在 CCB 交付後做交付 review（新 impact note）
3. 不自排其他 S9-E / Sprint 10 round
4. 平行 workstream（S9-A / S9-B / S9-D）CCA 依既有 impact note 任務等 CCB / CodeX B / CodeX A 各自交付後再行審核

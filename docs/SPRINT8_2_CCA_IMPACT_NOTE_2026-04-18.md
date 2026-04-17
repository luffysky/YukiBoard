# Sprint 8-2 Claude Code A Impact Note

- 身份：Claude Code A（本業；`CodeX B` 額度已恢復，CCA 退出代班）
- 時間：2026-04-18
- 適用 round：`S8-2` UserDictionary Shared-State / Reload Bugfix Batch
- 對應 gate 任務：`S8-2-CCA-USERDICT-IMPACT-NOTE`
- 審核對象：`CodeX A`
- 依 gate 要求：不改 `.kt`、只盯 UserDictionary 一致性題、不得自行指定 `S8-3`
- 本文件格式：單 PART（非雙 PART — 技術審核由 `CodeX B` 以 `S8-2-CXB-USERDICT-TECH-REVIEW` 任務回職處理）

---

## I. UserDictionary 現況盤點（本輪審核基準）

### I-A. 5 個實例化點確認

依 `kt_review_checkbug.md` Finding 4 + 本輪再次核對：

| 呼叫點 | 用途 | 寫入 prefs？ | 讀取時機 |
| --- | --- | --- | --- |
| `InputRouter.kt:27` | IME 主路徑（ChewingInputEngine 透過 `userDict = userDict` 持有引用） | 間接（`learn()` 自動學習） | Router 建立時 init `loadAll()`，之後只操作 in-memory + prefs |
| `ImeServicePanels.kt:351` | USER_DICT panel（`userDictionary = UserDictionary(this)`） | 是（`addManual` / `removeManual` / `updateManual`） | Panel 建立時 init `loadAll()` |
| `SettingsActivity.kt:190` | 匯出按鈕（呼叫 `exportToString`） | 否（純讀） | onExportDict lambda 觸發時 new |
| `SettingsActivity.kt:402` | 匯入按鈕（呼叫 `importFromString`） | 是（寫入 manualWords） | onImport lambda 觸發時 new |
| `CloudSyncManager.kt:220` | 同步上傳（呼叫 `getAllManualWithCategory`） | 否（純讀） | `uploadDictionary` 執行時 new |
| `CloudSyncManager.kt:312` | 同步下載（呼叫 `addManual`） | 是（寫入 manualWords） | `downloadDictionary` 執行時 new |

共 **6 個實例化點**（Finding 4 原列 5 個；本輪 grep 核實 `CloudSyncManager.kt` 有 2 個 `new UserDictionary(context)`，故實際為 6）。
**提醒 `CodeX B` / `CodeX A`**：若後續修復以呼叫點數量做為基準，應以 6 為準。

### I-B. In-memory state 的生命週期

- `UserDictionary.init { loadAll() }` 只在 constructor 執行
- 類別內無 `reload()` / `OnSharedPreferenceChangeListener` / broadcast subscriber
- 所有 write 方法（`learn` / `addManual` / `removeManual` / `updateManual` / `importFromString` / `clear`）都走 `prefs.edit().apply()` 寫入
- `learnedWords` / `manualWords` / `bopomofIndex` 三個 `mutableMapOf`，無同步保護；若多執行緒同時寫，存在 race

### I-C. 跨 instance 寫 → 讀方向的 stale 路徑

| 寫入路徑 | 同 instance 可見 | `InputRouter` 既有 instance 是否可見 | 其他 `UserDictionary` 新 instance 是否可見（透過 prefs） |
| --- | --- | --- | --- |
| USER_DICT panel 加詞 | ✅ 立即可見（panel 自己這份） | ❌ **stale** | ✅ 下次 new 時 loadAll |
| SettingsActivity 匯入 | ✅ 立即可見（settings 這份，但 Activity 結束後即釋放） | ❌ **stale** | ✅ 下次 new 時 loadAll |
| CloudSync 下載 | ✅ 立即可見（sync 這份，但 sync 結束後釋放） | ❌ **stale** | ✅ 下次 new 時 loadAll |
| InputRouter.learn（自動學習） | ✅ 立即可見 | — | ✅ 下次 new 時 loadAll |

**核心 stale 問題**：`InputRouter` 這份在 service 生命週期內長駐，只有 init 時 loadAll。來自 panel / settings / sync 的寫入不會同步到 `InputRouter.userDict`，也就不會同步到 `ChewingInputEngine`（透過建構時的 `userDict = userDict` 引用）。

---

## II. 對 settings / integration / sync 的 side-effect 審核

### II-A. Settings side-effect

#### II-A-1. USER_DICT panel 編輯 → 輸入建議即時性

**現況**：panel 加詞後，**當前 IME session 的 `InputRouter.userDict` 仍為舊資料**；注音輸入時 `queryByBopomofo` 從 `InputRouter` 引擎走，新加的詞不會出現直到 IME service 重啟或 router 重建。
**修復方向對此的影響**：
- 若 CCB 採 singleton 化 — panel 與 Router 共享同一 instance，加詞立即可見於 Router。✅ 問題消失
- 若 CCB 採 reload / listener — panel 寫入後觸發 Router reload，加詞立即可見。✅ 問題消失
- 若 CCB 採混合（singleton only for manualWords，learnedWords 維持 per-instance）— 需評估分裂策略，不建議。⚠️
**CCA 守線**：不評估實作策略，但提醒 CCB 驗收時**必須實機驗證「panel 加詞 → 下一個注音輸入立刻查到」**。

#### II-A-2. SettingsActivity 匯入 → IME 立即反映

**現況**：匯入成功 toast 顯示「成功匯入 N 個詞」，但實際 IME 查詢 / 注音候選並不會立刻包含這些詞。使用者體驗符合 MEMORY `project_yukiboard_login_bug.md` 同類「寫入成功但 UI 不更新」的感受。
**修復方向對此的影響**：
- singleton — Settings 匯入的 instance 是 singleton，Router 立即看到新資料。✅
- reload — Settings 匯入後呼叫 Router reload。✅ 但需明確的解耦機制（Settings 不該直接觸碰 IME service 內部）。
**CCA 守線**：**Settings → IME 之間不得直接 import / 直接呼叫 service**；任何 cross-layer 通訊應走 prefs 事件 / broadcast / singleton。若 CCB 引入直接耦合，CCA 會標為需調整。

#### II-A-3. 匯出 / 讀取操作

**現況**：匯出（`exportToString`）是純讀，每次 new 會 `loadAll()` 拿最新 prefs 資料，**語意正確**。
**修復方向對此的影響**：
- singleton — 匯出拿到 singleton 的 in-memory map，等同於最新（因 singleton 同步寫入）。✅
- reload / listener — 匯出每次 new 仍會 load，等同現狀。✅
**CCA 守線**：無風險點。

#### II-A-4. `YukiPreferences` / 其他 settings 欄位

本輪不觸碰 `YukiPreferences`（38 欄位重構屬 Sprint 9 級）。UserDictionary 使用自己的 prefs 檔 `yukiboard_user_dict`，獨立於 `YukiPreferences` 所用檔案，**不會互相污染**。
**CCA 守線**：若 CCB 修復過程要加 prefs listener，**必須用 `yukiboard_user_dict` 檔的 prefs instance 監聽**，不可監聽整個 `YukiPreferences`。

### II-B. Integration（Sprint 7 交付物）side-effect

#### II-B-1. `CloudSyncManager` 的 2 個 new point 會如何影響

- `CloudSyncManager.kt:220` uploadDictionary — 純讀，`getAllManualWithCategory` 走 fresh instance 的 loadAll，等同最新資料。無風險。
- `CloudSyncManager.kt:312` downloadDictionary — 寫入 fresh instance 的 `addManual`，寫 prefs；與 USER_DICT panel / SettingsActivity 匯入同屬「寫完 prefs 但 Router 不 reload」case。
**修復方向**：同 II-A-1 / II-A-2；singleton 或 reload 均可解。
**CCA 守線**：
- **不得在 `CloudSyncManager.kt` 內加「寫完呼叫 Router reload」邏輯** — `CloudSyncManager` 屬 data / integration 層，不該知道 `InputRouter` 存在。若 CCB 採 reload 策略，reload 的觸發應由 `UserDictionary` 本身（via listener）或由 IME service 層監聽 prefs 變更。
- 若 CCB 動 `CloudSyncManager.kt:220` / `:312` 改為 getInstance — 檢查是否同步有動 Sprint 7-4 交付物（應該不該動）。

#### II-B-2. Sprint 7-4 交付物的連動

- `UserSessionRepository` / `InsightApiClient` / `AppHttpClient` 皆不使用 `UserDictionary` — 無 side-effect
- S7-4 若有 `UserDataSnapshotProvider`（payload builder），其中 `UserDictionary(context)` 依然存在於 builder 內部 — 屬 S7-4 交付後的結構；本輪若 CCB 把 `UserDictionary.getInstance(context)` 化，builder 內部呼叫跟著換即可，**不算結構改動**
**CCA 守線**：**不得藉此輪擴改 `UserDataSnapshotProvider` / `SyncMetaRepository`（S7-4 交付物本體）**；只能跟著 `UserDictionary` API 變動做最小跟進。

#### II-B-3. Sprint 7 其他 integration 檔

- `SnowRealmAuthClient` / `AIProviderManager` / `AppHttpClient` / `InsightApiClient` — 完全不觸碰 `UserDictionary`，無 side-effect
- `UserSessionRepository` — 同上
**CCA 守線**：禁區，本輪不得動。

### II-C. Sync side-effect

#### II-C-1. 上傳流程

`CloudSyncManager.uploadDictionary` → `new UserDictionary(context)` → `getAllManualWithCategory()` → 上傳 Supabase
**現況**：每次上傳都拿 fresh instance + 最新 prefs，無 staleness 風險。
**修復方向對此的影響**：
- singleton — 上傳拿 singleton 的 in-memory map；若 manualWords 是經 singleton 寫過的最新值，等同於最新 prefs。✅
- reload / listener — 每次 new 仍會 load，等同現狀。✅
**CCA 守線**：上傳本身邏輯不該改；若 CCB 為了「一致性」而加上傳前 reload，屬**冗餘**，不建議。

#### II-C-2. 下載流程（覆蓋本地）

`CloudSyncManager.downloadDictionary` → 解析遠端 rows → `new UserDictionary(context)` → for each row `addManual(...)` → 寫 prefs
**現況**：寫完後 `InputRouter.userDict` stale（同 II-A / II-B-1）。
**觀察**：下載是否應**先 clear 再 add**？這是**語意題**：
- 若下載為「merge」語意 — 現況 `addManual` 會覆寫同 key，新增不存在的，等同 upsert
- 若下載為「replace」語意 — 需先 `clear()` 再 add
- 看現有行為應為 merge（未呼叫 `clear`）
**CCA 守線**：**不動下載語意**（merge vs replace 為產品決策，不在本 batch 題中）；CCB 若為了修 shared-state 順手改下載語意屬逾界。

#### II-C-3. 自動學習（`learn`）與同步的互動

`InputRouter` → `ChewingInputEngine` 在 commit 時呼叫 `userDict.learn(word)` → 寫 `prefs.putInt("word_$word", freq)` + `putString("_word_list", ...)`
**觀察**：
- `learn()` 操作 `learnedWords`；`CloudSyncManager.uploadDictionary` 只 upload `getAllManualWithCategory()`（manualWords），**不 upload learnedWords** — 即**自動學習詞不同步上雲**
- 下載路徑同理，只 merge manualWords
**CCA 守線**：**不擴 sync 範圍**（上傳 learnedWords 會影響 Supabase schema 與使用者資料量，屬產品級決策，不在本 batch）。提醒 CCB 修復過程中**不可混修 sync schema**。

---

## III. 風險盤點（供 CodeX A / CodeX B 參考）

### III-A. CCB 若採 singleton 化（推測最可能方向）

| 風險 | 描述 | 需盯 |
| --- | --- | --- |
| 多執行緒寫入 race | `learnedWords` / `manualWords` / `bopomofIndex` 三 map 無同步；singleton 後 IME input thread、Settings UI thread、Sync IO thread 可能同時寫入 | CCB 應在 singleton 化的同時補 `@Synchronized` 或走 `ConcurrentHashMap` |
| `ChewingInputEngine` 持有舊 ref | 現狀 `InputRouter.kt:27` new 一份、`kt:30-32` 以 `userDict = userDict` 傳給 `ChewingInputEngine`；若 InputRouter 改走 `getInstance`，ChewingInputEngine 引用應一致（因同 singleton） | 改動後 ChewingInputEngine 仍應能透過引用拿到最新 manualWords |
| `init { loadAll() }` 只跑一次 | singleton 化後 loadAll 只在首次 getInstance 時執行；之後 prefs 外部被改（如裝置端資料夾被第三方工具覆寫）不會 reload — 本輪不考慮 | 正常情境下 singleton 自管 prefs，無此風險 |
| `clear()` 語意 | singleton 後 `clear()` 會清所有 observer 看到的 state；本輪應無人主動呼叫 `clear` | 驗收時確認 |

### III-B. CCB 若採 reload / listener

| 風險 | 描述 | 需盯 |
| --- | --- | --- |
| Listener 註冊時機 | `OnSharedPreferenceChangeListener` 需在 IME service / Router init 註冊，並在 destroy 時 unregister，避免 leak | 驗收時確認 lifecycle pairing |
| Reload 瞬間 state 空 | `loadAll()` 會 `clear()` 三個 map 再 add — 若在 clear 之後、add 完成之前有讀操作，可能讀到空 map | 考慮 `loadAll` 用臨時 map 換原子替換 |
| 觸發循環 | 若 reload 過程中又觸發寫（理論極小可能），listener 可能遞迴 | CCB 應在 reload 期間設 guard flag |

### III-C. 共通風險（不分策略）

1. **`SharedPreferences.edit().apply()` 是異步寫**：singleton 或 reload，若緊接讀 prefs 可能拿不到剛寫的值。`UserDictionary` 內部直接操作 in-memory map，所以此問題對 `UserDictionary` 自身不致命；但若外部寫完 prefs 後依賴 prefs.getString 拿最新值（例如 CloudSync 寫完後立即 upload）— 實務上不存在此路徑，安全。
2. **`CloudSyncManager.kt:220` / `:312` 的 fresh instance**：若 CCB 改為 getInstance，**CloudSync 上下行流程的語意不變**（讀寫同一份 singleton），但要驗證 upload 流程不會被「其他 thread 寫到一半」的狀態影響。

---

## IV. CCA 紅線（本輪禁止出現）

1. 不得擴到 7 個 smart subsystem（`AdaptiveDictionary` / `PersonalDictionary` / `PhrasePredictor` 等）的 singleton 化 — gate 明列禁區
2. 不得動 Sprint 7 任何交付物（`AppHttpClient` / `InsightApiClient` / `UserSessionRepository` / `SnowRealmAuthClient` / `AIProviderManager`）
3. 不得修改 sync schema / Supabase 表結構 / CloudSync 上下行語意
4. 不得在 `CloudSyncManager.kt` 內加 Router reload 邏輯（跨層耦合）
5. 不得動 `YukiPreferences` 38 欄位
6. 不得動 `YukiBoardIMEService` 本體架構（只能在必要時接 listener 入口）
7. 不得擴 `panelContainer.getChildAt` / `ImePanelBindings` 題（屬 S8-1 已處理 / 其他 round 候選）
8. 不得動 `InviteCodeManager` 題
9. 不得動 `AISettingsActivity` section 化題
10. 不得自行指定 `S8-3` 開門
11. 不得把 S8-1 的 MIDI callback 修復再調整
12. 不得混修 Sprint 8-PRE backlog 其他候選（A-1 postAuth 雙 fetchMe / A-3 鏡像 stale / B-1 provider cache 等）

---

## V. 驗收觀察點（CCB 交付後 CCA 會檢視）

1. 5 個（實為 6 個）UserDictionary 實例化點是否一致走 singleton 或 reload 路徑？（一致性）
2. USER_DICT panel 加詞 → 馬上注音輸入能否查到新詞？（手動驗證）
3. SettingsActivity 匯入 → 立即注音輸入能否查到匯入詞？（手動驗證）
4. CloudSync 下載 → 立即注音輸入能否查到下載詞？（手動驗證）
5. `ChewingInputEngine` 透過 `userDict = userDict` 引用是否仍能拿到最新 manualWords？（程式碼審）
6. 7 smart subsystem 本體是否未被動？（禁區核對）
7. Sprint 7 交付物是否未被動？（禁區核對）
8. CloudSync upload / download 語意是否保持 merge？（行為核對）
9. 自動學習 `learn()` 的行為是否未變？（回歸檢查）
10. Thread-safety：若採 singleton，map 是否加同步保護？

---

## VI. A / B / C / D / E

### A. 變更摘要（本文件）

- 產出 S8-2 CCA 本業 impact note（**單 PART**，`CodeX B` 已回職）
- 盤點 6 個 `UserDictionary` 實例化點（較 Finding 4 原列 5 個多 1 個）
- settings / integration / sync 三向 side-effect 審核，共 10 子項
- 風險盤點 3 類（singleton / reload / 共通）
- 12 條 CCA 紅線 + 10 項驗收觀察點
- 本文件不動任何 `.kt`

### B. 風險

1. **實例化點數量校正**：Finding 4 原列 5 個，實際 6 個（CloudSyncManager 有 2 個 new）— 若 CCB 只改 5 個會遺漏。
2. **thread-safety**：若 CCB 採 singleton 不加同步，多 thread 寫可能 race。
3. **`ChewingInputEngine` 引用鏈**：需確認 singleton 化後 ChewingInputEngine 的 `userDict` 引用仍一致。
4. **CCB 逾界跨層耦合**：若 reload 策略在 `CloudSyncManager` / `SettingsActivity` 內直接呼叫 Router，屬架構逾界。
5. **語意順手改**：CloudSync 下載若順手改成 clear+add 語意屬逾界。

### C. 本輪未做（刻意不做）

- 7 smart subsystem singleton 化（禁區）
- Sprint 7 交付物任何動作
- `YukiPreferences` 重構
- `AISettingsActivity` section 化
- CloudSync 上下行語意 / schema 改動
- 自動學習詞（`learnedWords`）上雲
- S8-PRE backlog 其他候選（A-1 / A-3 / B-1 / F-2）
- S8-3 順序 / 主題建議

### D. 架構決策（無）

本文件為 impact note，不做架構決策。CCB / `CodeX B` 會在實作層決定 singleton vs reload 策略；CCA 僅審其 side-effect。

### E. `CodeX A` 審核請求

請 `CodeX A` 針對本文件判定：

- `READY` / `NOT READY` / `需調整的 scope / 紅線`

候選後續（**非 CCA 指派**）：

- 若 `READY`：`S8-2-CXB-USERDICT-TECH-REVIEW`（由回職的 `CodeX B` 產出）+ `S8-2-CCB-USERDICT-SHARED-STATE-BUGFIX` 開 CCB 實作
- CCB 交付後：`S8-2-CCA-IMPL-REVIEW` 由 CCA 本業做交付後 review
- `S8-3` 開門順序由 `CodeX A` 決定，CCA 不自排

---

## 附：代班狀態更新

`CodeX B` 額度於 S8-2 開門時已恢復（gate 明列「恢復原職」）。本文件由 CCA 純本業身份交付，**不再採雙 PART 代班格式**。

- 代班期間紀錄：Sprint 7 S7-2 / S7-3 / S7-4 + Sprint 8 S8-0 / S8-1
- memory 已同步更新：`project_cca_acting_cxb.md`
- 後續若再次遇 `CodeX` 額度不足，依原代班規則重新啟動

---

## 附錄 B：v2 → v3 重交再審（2026-04-18）

### 事件時序

1. v1：`System.currentTimeMillis()` → `CodeX A` 標 `NOT READY`（`updateManual()` remove→add 同 ms 會重號）
2. v2：`max(currentPrefVersion, lastKnownVersion) + 1` → `CodeX A` 標 `NOT READY`（跨 instance 兩執行緒同讀 currentPrefVersion 仍會算出相同 N+1）
3. v3：`max + 1` 整段搬進 `synchronized(versionLock)`，`versionLock` 為 companion object `Any()`

### CCA 對 v3 的逐條紅線核對

- 檔案範圍：v3 delta 僅限 `UserDictionary.kt`（+130 行，含 doc + listener + batch import 收斂 + lock）。其他 39 個 working-tree modified 檔為 Sprint 7 / S8-1 累積 uncommitted state，**非 v3 新增**。
- ❶ 禁 singleton：✅ 仍無 `getInstance()`；6 個 callsite 建構簽章未變
- ❷ 禁 7 subsystem（AdaptiveDictionary / CorrectionLearnManager / PersonalDictionary / PhrasePredictor / SentencePredictor / StyleProfiler / TypingHabitTracker）：✅ v3 未觸碰
- ❸ 禁 Sprint 7 交付物（UserSessionRepository / InsightApiClient / AppHttpClient / SnowRealmAuthClient / AIProviderManager / ApiKeySync）：✅
- ❹ 禁 CloudSync 語意變更：✅ `CloudSyncManager.kt:220` / `:312` 未改；upload/download 仍走 `getAllManualWithCategory` / `addManual`
- ❺ 禁 YukiPreferences：✅
- ❻ 禁擴 AISettingsActivity section：✅
- ❼ 禁跨層耦合新增：✅
- ❽ 禁 InviteCodeManager 改動：✅
- ❾ 禁新 Sprint section binder：✅
- ❿ 禁改 `handleKeyPress` / Router 核心：✅
- ⓫ 禁 IME service / SettingsActivity 加新責任：✅ 6 個 callsite 零改動
- ⓬ 禁改動 lifecycle hook：✅

### v3 正確性審核

- `versionLock` 為 companion object `Any()` → class-level（程序級）共用 ✅
- `AndroidManifest.xml` 無 `android:process` 宣告 → IME service / Activity / CloudSyncManager 同 process ✅（`synchronized` 可覆蓋全部 6 callsite）
- critical section 同時框住 `prefs.getLong` + 版本計算 + `lastKnownVersion` 更新 + `editor.apply()` → 先進者 apply 後、後進者才進，必讀到新的 prefs 值，不再重號
- apply() 原子提交 → `OnSharedPreferenceChangeListener` 只對 `VERSION_KEY` key 過濾觸發 `loadAll()`，資料 key 的 event 不會重複 reload
- `lastKnownVersion` 在 apply 前先自我記錄 → 自寫的 listener callback 因 `newVersion == lastKnownVersion` 跳過，不 reload 自己的寫入
- `clear()` 路徑：`editWithVersionBump { clear() }` 在同一 editor 內先 `clear()` 再 `putLong(VERSION_KEY, version)`，版本不遺失
- `reload()` / `release()` 未入 lock：`reload()` 只做 read + 更新 lastKnownVersion，極端情境最差多 load 一次（無壞狀態）；`release()` 只解 listener

### 非 blocking 觀察

1. **scope 仍超出 gate 明文建議**：gate 只建議 `max + 1`，v3 主動加 `synchronized`。技術必要且 scope 未外擴到其他檔，但嚴格來說是 CCB 判斷延伸。`CodeX A` 可視為合理延伸放行，或要求把 lock 策略寫回 gate 下一版 rule（非本輪 blocking）。
2. **compile 驗證**：CCB 報告 `./gradlew.bat :app:compileDebugKotlin --no-daemon → BUILD SUCCESSFUL in 20s`，CCA 未重跑驗證，以交付側回報為準。

### CCA 對 v3 的最終意見

- 12 條紅線：全部通過
- 技術：三次迭代收斂到正確解；cross-instance race 真正消除
- settings / auth / provider / sync / lifecycle 視角：無副作用擴散，callsite 不需跟改
- 不自排 S8-3，等 `CodeX A` 正式 gate 判定

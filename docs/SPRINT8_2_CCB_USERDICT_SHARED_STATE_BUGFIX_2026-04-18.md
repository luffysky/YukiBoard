# Sprint 8 Round 2 — CCB UserDictionary Shared-State / Reload Bugfix 回交

身份：Claude Code B
任務 ID：`S8-2-CCB-USERDICT-SHARED-STATE-BUGFIX`
日期：2026-04-18

對應 `kt_review_checkbug.md` Finding #4。

---

## 修訂紀錄

- **v1（初版）**：以 `System.currentTimeMillis()` 當 `_dict_version`。CodeX A 退回，指出 `updateManual` 的連續 remove→add 若落在同一毫秒，會產生相同版本號，讓其他 instance 的 listener 第二次因 `newVersion == lastKnownVersion` 跳過 reload，停在中間狀態。
- **v2**：改為 `max(currentPrefVersion, lastKnownVersion) + 1`。CodeX A 再退回：只解掉「同一 instance 連續寫」和「先後寫」的 collision，沒解掉**跨 instance 真正並發寫** — 兩個 instance 同時進入 `editWithVersionBump()` 都可能讀到同一個 `currentPrefVersion = N`、各自 `lastKnownVersion = N`、都算出 `N+1`，另一 observer 第二次仍因重號跳過。
- **v3（本次重交，同一 round）**：把版本號產生過程（`getLong` 讀取 → 計算 → `lastKnownVersion` 更新 → `editor.apply()`）整段納入 class-level `synchronized(versionLock)` critical section。YukiBoard 為單 process app，process-wide lock 即序列化所有 callsite 的版本號分配，任兩次寫入的 `_dict_version` 嚴格唯一且遞增，徹底解掉跨 instance 並發寫 collision。

---

## A. 變更摘要

### 修改檔案（僅 1 個）

- `app/src/main/java/com/snowrealm/yukiboard/input/dictionary/UserDictionary.kt`

### 設計決策：為何 scope 縮到單檔

Finding #4 的根本成因是「4 個 callsite 各自 `new UserDictionary(context)`，類別只在 init 時 `loadAll()`，無 reload / listener 機制」。

S8-2 task 允許的 scope 包含 `InputRouter` / `ImeServicePanels` / `SettingsActivity` / `CloudSyncManager`，但我選擇 **僅改 UserDictionary.kt**，理由：

1. **CLAUDE.md 鐵則：不得新增 `getInstance(context)` 式跨層依賴。** 因此 singleton 方案被排除。
2. 若把 reload 協調做到 callsite（例如讓 SettingsActivity 匯入後廣播、InputRouter 監聽），等於在 4 個檔案各加一份 reload 鏈，責任散落，未來新增 callsite 仍會漏接。
3. 把「跨 instance 一致性」內聚到 UserDictionary 本身（透過 SharedPreferences 版本 bump + listener），所有 callsite 繼續寫原本的 `new UserDictionary(context)`，零 callsite 改動即得到正確行為。這最符合 CLAUDE.md「單一狀態來源、依賴更可追蹤」的準則。

### Fix 細節

#### 1. 新增版本 bump 機制（v3：process-wide lock + 嚴格唯一遞增）

新增 `_dict_version: Long` prefs key、class-level `versionLock` 與 `editWithVersionBump` 私有輔助：

```kotlin
companion object {
    private const val VERSION_KEY = "_dict_version"
    private val versionLock = Any()  // process-wide 版本號鎖
}

@Volatile private var lastKnownVersion: Long = 0L

private fun editWithVersionBump(block: SharedPreferences.Editor.() -> Unit) {
    synchronized(versionLock) {
        val currentPrefVersion = prefs.getLong(VERSION_KEY, 0L)
        val version = maxOf(currentPrefVersion, lastKnownVersion) + 1
        lastKnownVersion = version
        val editor = prefs.edit()
        editor.block()
        editor.putLong(VERSION_KEY, version)
        editor.apply()
    }
}
```

- 所有資料寫入與 `_dict_version` bump 在同一個 `edit()` 交易內 apply，保證原子性。
- 同步更新 `lastKnownVersion`，讓 listener 能區分「自己寫的」和「別 instance 寫的」。
- **版本號產生整段在 `versionLock` critical section 內**（含 prefs 讀取、計算、`lastKnownVersion` 更新、`apply()`）。
  - 解 v2 退回理由（跨 instance 並發寫 collision）：兩個 instance 同時嘗試 bump 版本時，`versionLock` 序列化它們的進入順序。先進入者讀到 prefs=N、算出 N+1 並 `apply()`（SharedPreferencesImpl 的 `commitToMemory` 同步更新 in-memory map）→ 後進入者讀到 prefs=N+1、算出 N+2 → 每次寫入都得到嚴格大於前值且全 process 唯一的新版本。
  - `maxOf(currentPrefVersion, lastKnownVersion)` 仍保留：涵蓋「init 後 prefs 被別 process 改動」（本 app 是單 process，不會實際觸發，但保持防禦）與「lastKnownVersion 已由 listener 前進過但 prefs 尚未 flush」等 edge case。
- YukiBoard 為單 process app（IME Service / Activity / CloudSync 同 process），class-level lock 即涵蓋所有實際 callsite。

#### 2. 跨 instance reload listener

init 時註冊 `OnSharedPreferenceChangeListener`，只關心 `_dict_version` 變更：

```kotlin
private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    if (key == VERSION_KEY) {
        val newVersion = prefs.getLong(VERSION_KEY, 0L)
        if (newVersion != lastKnownVersion) {
            lastKnownVersion = newVersion
            loadAll()
        }
    }
}

init {
    loadAll()
    lastKnownVersion = prefs.getLong(VERSION_KEY, 0L)
    prefs.registerOnSharedPreferenceChangeListener(prefsListener)
}
```

- 自己寫入觸發 listener 時 `newVersion == lastKnownVersion`（已在 bump 時更新），跳過 reload，無遞迴。
- 別 instance 寫入時 `newVersion != lastKnownVersion`，呼叫 `loadAll()` 重建 in-memory map。
- Android `SharedPreferencesImpl` 在 main thread 派送 listener，不會與 main-thread reader 並發，沒有 `ConcurrentModificationException` 風險。

#### 3. 所有 mutator 改走同一管道

將原本散落的 `prefs.edit().putX().apply()` 與 `updateLearnedWordList()` / `saveManualWords()` 全部收斂到 `editWithVersionBump`：

- `learn()`：一次 edit 寫 `word_X` + `_word_list`
- `forget()`：一次 edit remove `word_X` + 更新 `_word_list`
- `addManual()` / `removeManual()` / `updateManual()`：走 `writeManualWords()`（已改為 `editWithVersionBump`）
- `clear()`：`editWithVersionBump { clear() }`（`editor.clear()` 後再 put VERSION_KEY，重新建立版本）
- `importFromString()`：batch 優化 — 先全量更新 in-memory map，最後單次 `writeManualWords()`，避免匯入 N 筆觸發 N 次 listener reload

#### 4. 新增對外入口

- `fun reload()`：callsite 明確知道有背景變動時可強制重載（一般不需要）
- `fun release()`：長生命週期 instance（例如 InputRouter 持有的）拆解時呼叫，解除 listener

### 未改動的 4 個 callsite 行為對照

| Callsite | 原行為 | 新行為（無 callsite 修改） |
|---|---|---|
| `InputRouter.kt:27` 長生命週期 instance | init 時 load，之後永不刷新 | 監聽版本 bump，其他 instance 寫入後自動 reload |
| `ImeServicePanels.kt:351` USER_DICT panel 短生命週期 | panel 開啟時 load | 開啟時 load；若使用者從 Settings 匯入，panel instance 也會收到 listener 觸發 reload |
| `SettingsActivity.kt:190` 匯出短生命週期 | 純讀 `exportToString()` | 不變（匯出路徑本來就不改狀態） |
| `SettingsActivity.kt:402` 匯入短生命週期 | `importFromString` 寫 prefs | 寫入時 bump 版本；InputRouter 的長生命週期 instance 立刻收到 listener，reload 完成 |
| `CloudSyncManager.kt:220` 上傳短生命週期 | 純讀 | 不變 |
| `CloudSyncManager.kt:312` 下載短生命週期 | `addManual` 寫 prefs | 寫入時 bump 版本；InputRouter 立刻 reload |

---

## B. 風險

1. **嚴格單調遞增依賴 Long 範圍**：每次寫入 +1，`Long.MAX_VALUE ≈ 9.2e18`，即使每秒寫 1000 次也要約 2.9 億年才溢位，實務無風險。
2. **Listener 弱引用生命週期**：Android `SharedPreferencesImpl.mListeners` 為 `WeakHashMap`，listener 由 UserDictionary 強持有，UserDictionary 被 GC 後 listener 也會被回收；短生命週期 instance 未呼叫 `release()` 亦不會洩漏到 process 結束。長生命週期 instance 建議呼叫 `release()`（本輪未強制 wire InputRouter，見 C 段）。
3. **Import batch 模式改變 listener 觸發粒度**：原本匯入 100 筆會寫 100 次 prefs，現在合併為 1 次；若有其它程式碼監聽 `_manual_list` key 變化（目前沒有）會看到通知次數減少。搜過整 repo 沒有這類依賴。
4. **未同步 reload `CloudSyncManager` / `SettingsActivity` 短生命週期 instance**：這些 instance 寫完立刻被 GC，不需要 reload，也不需要 release。行為正確。
5. **versionLock 潛在阻塞**：寫入路徑串行化後，高頻並發寫會排隊。實際負載評估：本 app 的 UserDictionary 寫入都是使用者 commit / 手動匯入 / CloudSync 下載，頻率低（每秒遠低於 10 次），`apply()` 非同步返回快速，lock 持有時間可忽略。無效能疑慮。
6. **跨 process 並發（非本 app 情境）**：若未來有另一 process 也寫同一 SharedPreferences，class-level lock 無法跨 process 保護。目前 YukiBoard 只有單 process（`android:process` 未拆），此情境不存在；若未來拆 process，需引入 content provider 或 file lock，屬新 round 題目。

---

## C. 下一步建議

**不自排 S8-3**，候選如下供 `CodeX A` 決定：

- **候選 A — InputRouter 呼叫 UserDictionary.release()**
  - 目前未 wire。需要在 `InputRouter` 加 `release()`，並在 `YukiBoardIMEService.onDestroy` / `DestroyCleanup` 呼叫。此為 S8-2 的結構強化，可並入 S8-3 或更後面。
  - 不緊急：listener 目前靠 WeakHashMap 可被 GC，無功能性問題。
- **候選 B — 其他 Sprint 8 候選 bug**
  - `kt_review_checkbug.md` 目前只列 4 個 finding（S8-1 / S8-2 已涵蓋），若要開 S8-3 需要先由 `CodeX A` 重新掃描或指定新題目。
- **候選 C — Sprint 8 結案**
  - 若 4 個 finding 都已修完且無新 blocking，可由 `CodeX A` 宣告 Sprint 8 = COMPLETED，轉 Sprint 9。

---

## D. 架構決策

- **Shared-state 一致性內聚到 class 本身**：以 prefs 版本 bump + listener 取代 singleton / broadcast。無新增 coordinator / repository / interface。
- **不修改其他 4 個 callsite**：保持 minimal scope，架構邊界清晰。
- **Mutator 寫入管道收斂**：所有 prefs 寫入統一走 `editWithVersionBump`，杜絕「漏 bump」可能。

---

## E. 驗證

1. `./gradlew.bat :app:compileDebugKotlin --no-daemon` → `BUILD SUCCESSFUL in 20s`（v3 重交後）。
2. 無新 warning / error。
3. 版本單調遞增自我驗證（推導）：
   - **單 instance 連續寫**：lastKnownVersion=k → 第 1 次 max(k,k)+1=k+1 → 第 2 次 max(k+1,k+1)+1=k+2 → 嚴格遞增 ✓
   - **跨 instance 連續寫**：A 寫後 prefs=k+1；B 透過 listener 更新 lastKnownVersion=k+1；B 寫 max(k+1,k+1)+1=k+2 → 嚴格遞增 ✓
   - **updateManual 同毫秒雙寫（v1 退回案例）**：
     - remove：currentPrefVersion=10, lastKnownVersion=10 → version=11；prefs=11, self lastKnownVersion=11
     - add：currentPrefVersion=11, lastKnownVersion=11 → version=12；prefs=12, self lastKnownVersion=12
     - 另一 instance listener：第一次收到 11（≠其 lastKnownVersion=10）→ reload；第二次收到 12（≠其 lastKnownVersion=11）→ reload ✓ 不再跳過
   - **跨 instance 真正並發寫（v2 退回案例，v3 解題核心）**：Instance A 與 B 同時進入 `editWithVersionBump()`：
     - A 先搶到 `versionLock` → 進入 critical section → 讀 prefs=N → 算 v=N+1 → `lastKnownVersion=N+1` → `editor.apply()`（`SharedPreferencesImpl.commitToMemory` 同步更新 in-memory prefs map=N+1）→ 釋放 lock
     - B 接著進入 critical section → 讀 prefs=N+1（已被 A 更新）→ 算 v=N+2 → `lastKnownVersion=N+2` → `apply()`
     - 結果：兩次寫入的版本號為 N+1 / N+2，嚴格唯一且遞增 ✓ 其他 observer 的 listener 兩次皆觸發（N+1 ≠ 其舊 lastKnownVersion=N；N+2 ≠ 剛被更新成的 N+1）→ 兩次 reload 都執行，最終狀態正確 ✓
3. 只動到 S8-2 允許 scope 內且為最小子集：
   - `input/dictionary/UserDictionary.kt` ✅
   - `input/router/InputRouter.kt` 未修改（不需要）
   - `service/ImeServicePanels.kt` 未修改（不需要）
   - `ui/settings/SettingsActivity.kt` 未修改（不需要）
   - `data/CloudSyncManager.kt` 未修改（不需要）
4. 未觸碰禁修區：
   - Sprint 7 integration 檔 ✅
   - 7 smart subsystem singleton 題 ✅
   - provider / auth / CloudSync token 題 ✅
5. 行為驗證（靜態分析層面）：
   - `learn` / `forget` / `addManual` / `removeManual` / `updateManual` / `clear` / `importFromString` 全部透過 `editWithVersionBump` 寫 prefs
   - init 時註冊 listener、記錄初始版本
   - listener 自動忽略自己的寫入（`lastKnownVersion` 已同步更新），只對別 instance 的寫入觸發 `loadAll()`
   - `reload()` / `release()` 對外入口已提供

---

## CodeX A 審核需求

- 需 `CodeX A` gate：`S8-2` 是否可結案；若 Sprint 8 四個 finding 都已落地，是否宣告 Sprint 8 = COMPLETED 轉 Sprint 9。
- Claude Code B 不自排 `S8-3`；候選方向已列於 C 段，最終由 `CodeX A` 判定。

# SPRINT10-5-CCB-INPUTROUTER-RELEASE-IMPLEMENTATION

身份：鍵律（SnowRealm 核心輸入與 IME 行為工程師）

日期：2026-04-18

任務 ID：

- `S10-5-CCB-INPUTROUTER-RELEASE-IMPLEMENTATION`

提交鏈下一棒：`雪鑰`（代班 `衡鑑` 做技術二審）→ `玄樞` gate 裁決。

---

## 0. 本輪邊界遵守

- 依 `Sprint10-All-Round-Gate.md` §Round S10-5：
  - 允許修改：
    - `app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt`
  - 禁止修改：
    - `YukiBoardIMEService.kt`
    - `ImeServiceCallbacks.kt`
    - `ImeServiceInput.kt`
    - candidate / scoring / auto detect 規則
    - `Settings` / `auth` / payment / sync 相關檔
    - `Sprint 10` gate / `RULE` / `daily_works_0418.md`
- 實際本 round 修改檔案：
  - `app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt`（單檔）
- 未修改任何其他 `.kt`、gate、`RULE`、`daily_works`。
- 未新增任何 `release()` / `close()` / `destroy()` API 到 engine / SmartInputManager / CandidateManager；全程只使用既有 public API（`smartInput.persist()`、`smartInput.maintenance()`、`InputEngine.reset()`、`CandidateManager.clear()`、`userDict.release()`）。

---

## 1. 修法目標對位

本輪 `玄樞` 裁決明文：

1. 只補 `InputRouter.release()` 目前已知缺漏的 owner release 鏈
2. 以最小 file scope 完成可證實的 release 補完，不擴成 input stack 重構
3. 讓 `release()` 不再只停在 `userDict` 單點清理

依 `S10-0-CXB-ENGINEERING-AUDIT` §C2 / §E：

- 只做 owner map + 最小釋放鏈補完；不擴成全面 input engine lifecycle 重構。
- 先把 `SmartInputManager` 當次級 owner，使用既有 `persist()` / `maintenance()` 做最終落地；不為本輪新增 `release()` API。
- 不為 engine 擴 `close()` API（audit 明列 8 個 engine / `CandidateManager` 目前都無顯式 release/close/destroy API，這是 `Sprint 11+` 再議的決策點）。

本次 patch 對位：

- 只擴張 `InputRouter.release()` 函式本體，不增加新 method、不新增新依賴。
- 釋放鏈從「1 項（userDict listener）」擴到「6 大類」，對應 `SmartInputManager` + 已初始化 engines + `CandidateManager` + `latinEngines` LRU + AUTO 模式 buffer + `userDict`。

---

## 2. 實際變更

### 2.1 Imports

無新增 import。使用的 API 全部來自既有 import：

- `com.snowrealm.yukiboard.input.smart.SmartInputManager`（line 6 已有）
- `com.snowrealm.yukiboard.input.engine.*`（line 5 已有）
- `com.snowrealm.yukiboard.input.candidate.CandidateManager`（line 4 已有）

### 2.2 `release()` 前後對照

#### 修前（`InputRouter.kt:422-431`）

```kotlin
/**
 * S8-3：IME service 拆解時呼叫，解除長生命週期 UserDictionary 的
 * SharedPreferences listener，避免留在 `SharedPreferencesImpl.mListeners` 的
 * WeakHashMap entry 延後到 GC 才清掉。
 *
 * 僅清 listener，不影響已 commit 的 prefs 資料。
 */
fun release() {
    userDict.release()
}
```

#### 修後（`InputRouter.kt:422-469`）

```kotlin
/**
 * IME service 拆解時呼叫，做 InputRouter owner 鏈的最小釋放。
 *
 * S8-3 初版：只解除 UserDictionary 的 SharedPreferences listener。
 * S10-5：補完至 SmartInputManager 落地 + 已初始化的 engine / candidate
 *       / AUTO buffer / pendingCommitText 全域清理。
 *
 * 執行順序（固定）：
 *   1. SmartInputManager 在記憶體中的學習狀態先 persist 再 maintenance，
 *      避免 IME 被系統回收時丟失學詞 / 片語 / 風格 / 習慣 / 糾錯樣本。
 *   2. 清已確定 eagerly 持有的 engine composing state（englishEngine、bopomofoEngine）。
 *   3. 清 latinEngines LRU 內所有 runtime-registered 引擎的 composing state 並清空 map。
 *   4. 清 candidateManager 的候選列，避免殘留帶到下次 onCreate。
 *   5. 清 AUTO 模式的雙重解釋 buffer 與 pendingCommitText。
 *   6. userDict.release()：解除 UserDictionary listener（S8-3 既有行為）。
 *
 * 刻意不做（保留給後續 round 或 Sprint 11+）：
 *   - 不強制觸發 lazy engine（pinyin / cangjie / speedCangjie / japanese / korean）
 *     的初始化；若從未被使用，就沒有可釋放的 composing state。
 *   - 不新增 engine 層的 close()/destroy() API。
 *   - 不在 SmartInputManager 新增 release()；沿用既有 persist()+maintenance()。
 */
fun release() {
    smartInput.persist()
    smartInput.maintenance()

    englishEngine.reset()
    bopomofoEngine.reset()
    for (engine in latinEngines.values) {
        engine.reset()
    }
    latinEngines.clear()

    candidateManager.clear()

    autoQwertyBuffer.setLength(0)
    autoBopomofoBuffer.clear()
    pendingCommitText = null

    userDict.release()
}
```

---

## 3. owner 鏈逐項對齊

依 `S10-0-CXB-ENGINEERING-AUDIT §E` 的 owner map 分類，本次 patch 對每一類的處理如下：

| audit §E 分類 | owner | 本輪釋放動作 | 備註 |
|---|---|---|---|
| 已有明確 release API | `userDict` | `userDict.release()` | S8-3 既有行為，保留在最後 |
| 只有 persist / maintenance，沒有 release API | `smartInput`（含 AdaptiveDictionary / PhrasePredictor / SentencePredictor / TypingHabitTracker / PersonalDictionary / StyleProfiler / SwipeLearnManager / CorrectionLearnManager） | 先 `persist()` 再 `maintenance()` | 以「flush 學習狀態 + 清過期資料」取代新增 release()，符合 audit 建議 |
| 無顯式 release API — eagerly 持有 | `englishEngine`、`bopomofoEngine` | `reset()` 清 composing | 不新增 engine close API |
| 無顯式 release API — lazy 持有 | `pinyinEngine`、`cangjieEngine`、`speedCangjieEngine`、`japaneseEngine`、`koreanEngine` | **刻意不 touch** | 避免在 release() 觸發 lazy 初始化；未用 = 無 state 可釋放 |
| 無顯式 release API — 動態 LRU | `latinEngines`（LinkedHashMap） | 逐個 `reset()` + `clear()` map | 只處理已登記項目，不觸發新 load |
| 無顯式 release API | `candidateManager` | `clear()` | 清候選，不新增 API |
| InputRouter 本身狀態 | `autoQwertyBuffer` / `autoBopomofoBuffer` / `pendingCommitText` | `setLength(0)` / `clear()` / `= null` | 避免跨 service lifecycle 殘留 |

其他 InputRouter 內 in-memory 欄位（`currentMode`、`activeEngine`、`lastCommittedWord`、`currentAppPackage`、`contextBeforeCursor`、`candidateSourceMap`、`autoLastCommitWasEnglish`、`autoEnglishScore`、`autoBopomofoScore`、`autoLayout`）：

- 都是 value/ref type，無 listener / file handle / coroutine。
- 隨 `InputRouter` 實例被 GC 自然釋放，`release()` 不需明示清理；保留給後續 round 再視需求決定是否納入。

---

## 4. 行為差異

| 情境 | 修前 | 修後 |
|---|---|---|
| IME 正常 `onDestroy()` → `inputRouter.release()` | 只 unregister `userDict` listener；`SmartInputManager` 學習狀態若未透過 `onFinishInputView` 落地則遺失；engine composing state 殘留到 GC；latinEngines 容器不收 | 先 `persist + maintenance` 落地 → 清 engine composing → 清 candidate → 清 AUTO buffer → 解 userDict listener |
| `SmartInputManager` 的 learned state 已於 `onFinishInputView` persist 過 | listener 解綁，其他不動 | 再 persist 一次（冪等），maintenance 清過期（冪等） |
| latinEngines 從未被 load（英中文使用者） | 無差異 | 無差異（for 迴圈跳過空 map） |
| lazy engine 從未被 init | 無差異 | 無差異（刻意不 touch，不觸發 init） |
| AUTO 模式最後一次輸入有 `pendingCommitText` 未被 Service 吃掉 | 留在 router 實例裡直到 GC | 顯式清為 null |
| 外部呼叫頻率 | `onDestroy()` 呼叫一次 | 仍然只一次 |
| 對正常輸入路徑（`processChar` / `commitAndReset` / `switchTo` / `autoDetectAndSwitch`） | 無關 | 無關（release 不在熱路徑） |

### 4.1 冪等性

- `smartInput.persist()`：內部各 sub-system 自行呼叫 `save()` / `persist()`，非冪等即冪等；即使 `onFinishInputView` 已叫過，再叫一次僅是重寫入 SharedPreferences，無錯誤。
- `smartInput.maintenance()`：`purgeStale()` 系列以 timestamp 為準，冪等。
- `InputEngine.reset()`：所有實作都清 composing 到 initial；冪等。
- `CandidateManager.clear()`：冪等。
- `latinEngines.clear()`：冪等。
- `userDict.release()`：S8-3 設計即為冪等 unregister。

整個 `release()` 可被重複呼叫而不出錯。

### 4.2 thread safety

- `onDestroy()` 在 Main thread 執行；`release()` 同樣在 Main。
- `smartInput.persist()` 內部多數實作為同步 I/O 呼叫（`SharedPreferences.Editor.apply()` 已 async commit）；若未來擴成 async，不影響本 patch 語意。
- 本 patch 沒有引入任何 coroutine / thread 切換。

---

## 5. 影響面 / 風險盤點

### 5.1 可能被質疑的點（給二審預備答）

1. **為什麼不幫每個 lazy engine 都 reset？**
   - 會觸發 lazy init。若使用者從未用過 pinyin/cangjie/japanese/korean，在 release 路徑強制建立它們只為 reset 是負收益。
   - lazy engine 未被 init 時即無 state 可釋放。
   - audit §E 明文：不要在同一 round 直接把所有 engine API 擴成 close()。

2. **為什麼不幫 SmartInputManager 加正式 `release()` 方法？**
   - 跨 file scope（`SmartInputManager.kt` 不在本輪授權）。
   - audit §E 建議是「先判定是否需新增極小 release()」，這是 Sprint 11+ 決策題，不在本 round。
   - 沿用既有 `persist() + maintenance()` 已能覆蓋「flush 學詞 / 片語 / 風格 / 習慣 / 糾錯樣本」的語意，本 round 收益已足。

3. **會不會造成 double persist（`onFinishInputView` + `onDestroy`）？**
   - 會。但 `persist()` 冪等、`apply()` 為 async commit，不會造成使用者可感問題。
   - 加一次 flush 可對付 IME 在 `onFinishInputView` 之後但 `onDestroy` 之前才拿到新學詞的極端 race（例如系統 kill 前最後一次 `commitText`）。
   - 實際 CPU / I/O 成本：遠小於避免學詞遺失的價值。

4. **`candidateManager.clear()` 會不會影響下次 onCreate 的候選顯示？**
   - 不會。下次 `onCreate` 會建立新的 `InputRouter`，`candidateManager` 也是新實例。
   - 本 clear 只是保證當前實例在 GC 前狀態乾淨，避免任何潛在的 cross-instance reference 洩漏（雖然目前未觀察到，但屬零成本 safety）。

5. **`autoQwertyBuffer.setLength(0)` / `autoBopomofoBuffer.clear()` 的必要性？**
   - 同上，實例即將 GC 無強制必要。
   - 但若未來 release 被設計成可重複使用（例如 service restart 共用 router 實例），此步驟確保 AUTO 模式不帶舊資料。
   - 零成本，預防性清理。

6. **會不會影響到 S10-4 的 coroutine exception handler？**
   - 無關。release 在 Main thread 同步執行，與 `serviceScope` 的 child coroutine 例外監測無交互作用。

7. **會不會影響 S10-2 的 commit path 對稱修正？**
   - 無關。`release()` 只在 service 生命週期末端呼叫，不在 `applyInputResult` 的 commit path 上。
   - 對 `pendingCommitText` 的清理是一致的收尾：如果 AUTO 模式最後一次切引擎留下 pending，正常情況 Service 會 consume；release 作為最後兜底清掉。

### 5.2 未觸碰項（刻意保留）

- `SmartInputManager`：未新增 `release()` API。
- `InputEngine`：未新增 `close()` / `destroy()` API。
- `CandidateManager`：未新增 `release()` API。
- lazy engine fields 的初始化方式：未改成 `Lazy<>` 明示 wrapper 以支援 `isInitialized()` 檢查。
- `ImeSessionHooks` / `DestroyCleanup`：未介入 service 端的 cleanup 順序（那是 `S10-6` 的題目）。

---

## 6. 驗證

### 6.1 編譯

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

結果：

```
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 3s
18 actionable tasks: 1 executed, 17 up-to-date
```

- 單檔增量編譯通過。
- 無新 warning 與本輪 patch 相關。
- 無 unresolved reference（全部 API 皆為既有 public）。

### 6.2 Runtime smoke path（靜態推演）

本次為 release / teardown 類變更，runtime 觸發點在 IME 生命週期末端。

- **正常鍵入路徑**：與 `release()` 無關，行為不變。
- **切換輸入法到別的 IME**：觸發 `onFinishInput` → `onDestroy` → `inputRouter.release()`；擴充後的 release 行為如 §4 表所述。
- **系統 kill IME 進程**：`onDestroy()` 可能被跳過；本 patch 與該路徑無關（系統層行為）。
- **repeated `release()` 呼叫**：冪等，無副作用（見 §4.1）。

### 6.3 使用者可觀察變化

- 主觀：無（release 在 teardown 端，使用者看不到）。
- 客觀：學習過的詞更有機會在 kill 前落地到 prefs，跨 session 更穩。

---

## 7. A–E 回交格式

### A. 變更摘要

- 單檔變更：`app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt`
- `release()` 從 1 行延伸至 6 類清理鏈：
  1. `smartInput.persist()` + `smartInput.maintenance()`
  2. `englishEngine.reset()` + `bopomofoEngine.reset()`
  3. `latinEngines` 逐個 `reset()` + `clear()` map
  4. `candidateManager.clear()`
  5. AUTO buffers + `pendingCommitText` 清理
  6. `userDict.release()`（S8-3 既有，保留收尾）
- 文件註解同步更新（執行順序、刻意不做的項目）
- 無新增 import、無新增 API、無跨檔變更

### B. 風險

1. **低風險**：全程使用既有 public API，冪等，不在熱路徑。
2. **double persist**：`onFinishInputView` 與 `onDestroy` 可能都叫到 `smartInput.persist()`；冪等 + async commit 不影響使用者。
3. **刻意保留的缺口**：lazy engine 未被 reset（需要擴 engine API 才值得做，屬 `Sprint 11+` 決策）；`SmartInputManager` 未有正式 `release()`（跨 file scope）。
4. **未測試 runtime probe**：本輪是 teardown 類補強，無新增使用者可見行為，以 compile + 冪等性推演驗證為主。

### C. 下一步建議

- 本 round 結束後依 `玄樞` 裁決是否開 `S10-6 Receiver / Lifecycle 風險修補`。
- 若使用者希望未來把 lazy engine 釋放也納入 guardrail，應先討論是否在 engine 層加 `close()` API（這是 `Sprint 11+` 議題，不適合塞進 `Sprint 10`）。
- `鍵律` 不自排順序，等 `玄樞` 裁決。

### D. 架構決策

- 不新增任何 API / coordinator / repository / interface。
- `release()` 執行順序採用「先 persist 再 cleanup」的慣用 teardown 原則：先把可能還在記憶體的學習狀態落地，再清 in-memory state，最後解 listener。
- lazy engine 處理原則：**不因 release 而觸發 init**，避免負收益。
- 重複呼叫保證：所有 step 皆冪等，future-proof。

### E. `玄樞` 審核需求

建議判定：

```text
READY

Reason:
1. 單檔 patch 完全落在本輪授權的 InputRouter.kt，未越界碰 YukiBoardIMEService / ImeServiceCallbacks / ImeServiceInput / candidate 規則 / Settings / auth / payment / sync。
2. 修法對位 S10-0-CXB-ENGINEERING-AUDIT §C2 的最小 owner-map 補完建議：使用既有 persist()+maintenance() 取代新增 release()，未擴 engine close API。
3. compileDebugKotlin 通過，未新增任何 import、method、dependency。
4. lazy engine 的刻意不 touch 已於 KDoc 明文，避免 release 路徑誤觸發初始化；latinEngines LRU 逐項清理後清空 map。
5. release 全程冪等，與 onFinishInputView 的 persist 不衝突，未改變熱路徑行為。
```

請 `雪鑰` 代班做二次審核，確認：

1. file scope 單檔、無越界
2. patch 實檔落地（非文件層級宣告）
3. compile 通過
4. `release()` 執行順序、冪等性、lazy engine 處理策略無風險
5. 未誤觸 candidate / scoring / auto detect 規則（本 round 禁區）

二審通過後交 `玄樞` 做 gate 最終裁決。

# SPRINT10-4-CCB-COROUTINE-EXCEPTION-HANDLER-IMPLEMENTATION

身份：鍵律（SnowRealm 核心輸入與 IME 行為工程師）

日期：2026-04-18

任務 ID：

- `S10-4-CCB-COROUTINE-EXCEPTION-HANDLER-IMPLEMENTATION`

提交鏈下一棒：`雪鑰`（代班 `衡鑑` 做技術二審）→ `玄樞` gate 裁決。

---

## 0. 本輪邊界遵守

- 依 `Sprint10-All-Round-Gate.md` §Round S10-4：
  - 允許修改：
    - `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
  - 禁止修改：
    - `InputRouter.kt`
    - `ImeServiceCallbacks.kt`
    - `ImeServiceInput.kt`
    - `Settings` / `auth` / payment / sync 相關檔
    - `Sprint 10` gate / `RULE` / `daily_works_0418.md`
- 實際修改檔案（經 `git status` 可驗證）：
  - `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`（單檔）
- 未修改任何其他 `.kt`、gate、`RULE`、`daily_works`。
- 未擴張 file scope，未默擴到各 `serviceScope.launch {}` callsite 的個別 try-catch。

---

## 1. 修法目標對位

本輪 `玄樞` 裁決明文：

1. 只補 `YukiBoardIMEService.serviceScope` 的 `CoroutineExceptionHandler`
2. 以單檔最小 patch 讓 service coroutine 例外至少可被統一記錄
3. 不把本輪擴成完整 observability / crash reporting 專案

本次 patch 對位：

- 在 `serviceScope` 定義處掛上一個 class 級 `CoroutineExceptionHandler`，作為未捕捉例外的統一觀測入口。
- 沒有觸碰任何一個 `serviceScope.launch {}` 的 callsite，沒有動 `SupervisorJob()` 的 child 隔離語意。
- 沒有引入 Crashlytics 或其他外部上報框架；僅用既有 `android.util.Log.e(...)`，與同檔 268 / 286 行既存 log 風格一致。

---

## 2. 實際變更

### 2.1 Imports

檔案：`app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`

新增一行 import（置於 `kotlinx.coroutines.CoroutineScope` 之前，維持字母序）：

```kotlin
import kotlinx.coroutines.CoroutineExceptionHandler
```

### 2.2 `serviceScope` 定義處

原本（僅 1 行）：

```kotlin
// 非同步
internal val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
```

改為：

```kotlin
// 非同步
// S10-4：serviceScope 加上統一 CoroutineExceptionHandler，
// 讓 child coroutine 未捕捉例外至少有一個可觀測的 IME tag 記錄入口，
// 不改變 SupervisorJob 的 child 隔離語意。
private val serviceScopeExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    android.util.Log.e(
        "IME",
        "Unhandled coroutine exception in serviceScope: ${throwable.message}",
        throwable
    )
}
internal val serviceScope = CoroutineScope(
    Dispatchers.Main + SupervisorJob() + serviceScopeExceptionHandler
)
```

定位（修改後）：
- `YukiBoardIMEService.kt:158` — 註解與 handler 區塊起點
- `YukiBoardIMEService.kt:162-168` — `serviceScopeExceptionHandler` 定義
- `YukiBoardIMEService.kt:169-171` — `serviceScope` 組裝，`+ serviceScopeExceptionHandler`

---

## 3. 行為差異

| 情境 | 修前 | 修後 |
|---|---|---|
| `serviceScope.launch { normalPath() }` 正常完成 | 完成 | 完成（無差異） |
| child coroutine 內自行 `try-catch` 吞例外 | catch block 處理 | catch block 處理（無差異） |
| child coroutine 內出現未捕捉例外 | 走 Kotlin 預設未捕捉路徑，只剩系統預設 log，無 IME tag、無 throwable stack 明確關聯到 serviceScope | 觸發 `serviceScopeExceptionHandler`，以 tag `IME` 記錄 `Unhandled coroutine exception in serviceScope: <msg>` + 完整 throwable stack |
| `SupervisorJob()` 的 child 隔離 | child 失敗不連坐 sibling | child 失敗不連坐 sibling（無差異，handler 不干擾 SupervisorJob 語意） |
| `serviceScope.cancel()`（`onDestroy` 路徑 line 665） | 正常 cancel | 正常 cancel（`CancellationException` 依 Kotlin 慣例不會進 handler） |

重點：

- `CoroutineExceptionHandler` 只對**未捕捉**的非 `CancellationException` 例外觸發。
- 對已 try-catch 的 callsite（例如 268 / 286 行的 RIME / Chewing warmup）完全無影響。
- 對 `withContext(Dispatchers.IO) {}` 這類 rethrow-to-caller 形式也無改變（例外會回到 caller；若 caller 漏抓才進 handler）。

---

## 4. 影響面 / 風險盤點

### 4.1 既有 `serviceScope.launch` callsite 清點

本次未修改 callsite，僅列出一次讓 `雪鑰` 二審時能快速掃過 handler 觸發域：

- `YukiBoardIMEService.kt:265` — `serviceScope.launch { ... }`（RIME init 相關，內已 try-catch）
- `YukiBoardIMEService.kt:283` — `serviceScope.launch(Dispatchers.IO) { ... }`（Chewing warmup 相關，內已 try-catch）
- `YukiBoardIMEService.kt:566` — 傳給其他 coordinator 的 `scope = serviceScope`
- `YukiBoardIMEService.kt:665` — `serviceScope.cancel()`（`onDestroy` 清理）
- 其餘經 `scope = serviceScope` 傳遞到 `ImeSessionCoordinator` / `ImeFraudModelInit` / `ImeImageCommitter` 等處的 child coroutine，也會套用同一 handler（這是本輪 patch 的主要收益面）。

### 4.2 可能被質疑的點（給二審預備答）

1. **為什麼不是各 launch 個別補 try-catch？**
   - `S10-0-CXB-ENGINEERING-AUDIT` §C1 已明文：最小修點在 scope 建立處；本輪裁決也禁止擴張到個別 callsite 或其他 `Ime*` 檔。
   - 於 scope 掛統一 handler 是 guardrail 最小收斂，避免本輪演變成「順手把所有 launch 都改一遍」。

2. **為什麼用 `android.util.Log.e`？**
   - 同檔 268 / 286 行既存 log 即以 `android.util.Log.w("IME", ...)` 形式、fully-qualified 呼叫。沿用同一 tag / 同一風格可維持 log 觀測一致。
   - `Log.e` 對應未捕捉例外的嚴重度語意；提交 throwable 參數可保留完整 stack。
   - 不引入 Crashlytics / Timber 等新依賴，符合裁決 §3「不擴成完整 observability / crash reporting」。

3. **`CancellationException` 會不會被誤記？**
   - 不會。Kotlin coroutine 協議：`CancellationException` 在正常 cancel 路徑（`serviceScope.cancel()` line 665）由框架吞掉，不會進 `CoroutineExceptionHandler`。handler 內也無特別排除邏輯，維持官方預設行為。

4. **`SupervisorJob` + handler 組合會否影響 child 隔離？**
   - 不會。handler 只在「例外已經 propagate 到 scope 頂部且無 parent 處理」時觸發。`SupervisorJob()` 的語意是「child 失敗不殺 sibling」，兩者互補不衝突。

5. **`private` visibility 是否合適？**
   - `serviceScopeExceptionHandler` 僅於 `serviceScope` 組裝時使用，不暴露給其他 file；採 `private`。
   - `serviceScope` 沿用既有 `internal`，其他 `Ime*` 檔使用方式不變。

### 4.3 未觸碰項（刻意保留）

- `SupervisorJob()`：未換成 `Job()`，child 失敗不連坐 sibling 的既有設計保留。
- `Dispatchers.Main`：預設 dispatcher 未變。
- 各 `launch` callsite：未動任一行。
- 既有 try-catch block（268 / 286）：未移除，維持本地處理優先。
- 其他 scope（`ImeFraudModelInit` / `ImeImageCommitter` 等子 coordinator 的 scope 傳遞）：未補額外 handler，因為它們共用同一個 `serviceScope`，本 patch 已覆蓋。

---

## 5. 驗證

### 5.1 編譯

執行：

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
```

結果：

```
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 13s
18 actionable tasks: 2 executed, 16 up-to-date
```

無 compile error，無新 warning 與本輪 patch 相關。

### 5.2 Runtime smoke path（靜態推演）

本次是 observability guardrail，runtime 實際觸發需要 child coroutine 真的發生未捕捉例外；修後符合：

- 正常鍵入 → `inputUiSync` / coordinator 的 coroutine 無異常 → handler 不觸發（預期）。
- 人為模擬（若 `雪鑰` 需要驗證）：在某一 `serviceScope.launch { throw RuntimeException("probe") }` 即可觀察到 logcat `IME` tag 出現 `Unhandled coroutine exception in serviceScope: probe` 一行 + stack。本次 patch 未加入 probe。

---

## 6. A–E 回交格式

### A. 變更摘要

- 單檔變更：`app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
- 新增 import：`kotlinx.coroutines.CoroutineExceptionHandler`
- 新增 private field：`serviceScopeExceptionHandler`（class 級）
- `serviceScope` 組裝改為 `Dispatchers.Main + SupervisorJob() + serviceScopeExceptionHandler`
- 附帶三行繁中註解，說明補 handler 的目的與 `SupervisorJob` 語意不變

### B. 風險

1. **低風險**：handler 只在未捕捉例外時觸發，不改變正常 coroutine 流程。
2. **log 觀測面**：若未來有頻繁 unhandled exception，logcat `IME` tag 會多出 `E/IME` 訊息；屬預期、屬本輪收益。
3. **未做的事（刻意）**：未動任一 `launch` callsite、未升級 log 基礎建設、未接外部 crash reporting；若未來需要，屬後續 round 再議。

### C. 下一步建議

- 本 round 結束後依 `玄樞` 裁決是否開 `S10-5 InputRouter 釋放鏈補完`。
- 若 `玄樞` 決定繼續開護欄線，`鍵律` 可接受 `S10-5`，但該門涉及 `InputRouter.kt` / `SmartInputManager.kt`，屬 Input 核心；需由 `玄樞` 明文鎖定最小 scope（如 `S10-0-CXB-ENGINEERING-AUDIT §C2` 所列），避免擴成 input stack refactor。
- `鍵律` 不自排順序，等 `玄樞` 裁決。

### D. 架構決策

- 不新增 coordinator / repository / interface。
- 不引入第三方 log / crash reporting。
- `CoroutineExceptionHandler` 以 class 級 `private val` 持有，避免每次組 scope 都重新 allocate、並保留未來可測試性。

### E. `玄樞` 審核需求

建議判定：

```text
READY

Reason:
1. 單檔 patch 完全落在本輪授權的 YukiBoardIMEService.kt，未越界碰 InputRouter / ImeServiceCallbacks / ImeServiceInput / Settings / auth / payment / sync。
2. 修法對位 S10-0-CXB-ENGINEERING-AUDIT §C1 的最小修點建議，未擴張到個別 launch callsite。
3. compileDebugKotlin 通過，未引入新依賴。
4. SupervisorJob 的 child 隔離語意保留，CancellationException 走正常路徑不誤記，log 風格與同檔既存 android.util.Log.w("IME", ...) 一致。
```

請 `雪鑰` 代班做二次審核，確認：

1. file scope 單檔、無越界
2. patch 實檔確實落地（非文件層級宣告）
3. compile 通過
4. handler 語意、SupervisorJob 行為、log tag 無風險

二審通過後交 `玄樞` 做 gate 最終裁決。

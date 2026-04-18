# SPRINT10-4-CCA-COROUTINE-EXCEPTION-HANDLER-REVIEW

身份：雪鑰（SnowRealm 設定與帳號流資深工程師，本輪代班 `衡鑑` 做技術二審）

日期：2026-04-18

任務 ID：

- `S10-4-CCA-COROUTINE-EXCEPTION-HANDLER-REVIEW`

審核對象：

- 鍵律 回交：`docs/SPRINT10_4_CCB_COROUTINE_EXCEPTION_HANDLER_IMPLEMENTATION_2026-04-18.md`
- 實際 patch：`app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`

提交鏈下一棒：`玄樞` gate 終裁。

---

## 0. 二審邊界遵守

依 `Sprint10-All-Round-Gate.md` §Round S10-4 二審重點：

1. 只允許 `YukiBoardIMEService.kt`
2. 不得偷碰 `InputRouter.kt` / `ImeServiceCallbacks.kt`（S10-2 已 COMPLETED 的範圍不該再動）/ `ImeServiceInput.kt`
3. 修法必須對準 `serviceScope` 的 `CoroutineExceptionHandler`
4. 若發現 bug、越界、假修、漏修，直接退回，不得自行放行下一門

雪鑰本輪行為：

- 無 `.kt` 改動（二審者不寫程式碼）
- 親自打開 `YukiBoardIMEService.kt` 實檔複核 patch 落地
- `git diff` 比對工作樹 .kt 全集，確認 file scope
- 以 `compileDebugKotlin` 重跑驗證

---

## 1. File scope 守規檢查

### 1.1 工作樹 .kt 修改全集

`git diff --stat` 結果（僅 `.kt` 部分）：

```text
 .../yukiboard/service/ImeServiceCallbacks.kt       |  7 ++++---
 .../yukiboard/service/YukiBoardIMEService.kt       | 22 +++++++++++++++++++---
 .../ui/settings/section/AuthSettingsSection.kt     |  4 ++--
```

雪鑰交叉比對：

| 檔案 | 來源 round | 是否屬本輪 |
|------|-----------|-----------|
| `ImeServiceCallbacks.kt`（+4/-3） | S10-2 R3（switchTo + applyInputResult，玄樞已 READY） | 否 |
| `YukiBoardIMEService.kt`（+19/-3） | S10-2 R7（line 488-507）+ **S10-4（line 71 import + line 158-171 handler/scope）** | **本輪 +13/-1** |
| `AuthSettingsSection.kt`（+2/-2） | S10-3（updateAuthUI consume rule，玄樞已 READY） | 否 |

→ 鍵律 S10-4 patch **真正落點僅在 `YukiBoardIMEService.kt`**，未越界到任何禁區檔。

### 1.2 禁區清單對照

| 禁區檔/區 | 結果 |
|-----------|------|
| `InputRouter.kt` | 未動 ✓ |
| `ImeServiceCallbacks.kt` | 未在本輪動（既有 S10-2 變更與本輪無關） ✓ |
| `ImeServiceInput.kt` | 未動 ✓ |
| `Settings` 相關 | 未在本輪動（`AuthSettingsSection.kt` 是 S10-3 既有） ✓ |
| `auth` 相關 | 未動 ✓ |
| payment / sync 相關 | 未動 ✓ |
| `Sprint 10` gate | 未動 ✓ |
| `RULE/` | 未動 ✓ |
| `daily_works_0418.md` | 未動 ✓ |

→ File scope 守規 **完全符合** 玄樞本輪裁決。

---

## 2. 實檔複核（Patch 真實落地）

雪鑰打開 `YukiBoardIMEService.kt` 實檔，逐處核對：

### 2.1 Import（line 71）

```kotlin
import kotlinx.coroutines.CoroutineExceptionHandler
```

- 位置：第 71 行，在 `kotlinx.coroutines.CoroutineScope`（line 72）之前 ✓
- 字母序：`Co...Exception... < Co...Scope` ✓
- 非 wildcard、非 fully-qualified-only ✓

### 2.2 `serviceScopeExceptionHandler` 與 `serviceScope`（line 158-171）

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

逐項對照：

| 檢查項 | 鍵律宣稱 | 實檔結果 |
|--------|---------|---------|
| handler 為 `private val` class field | 是 | ✓ 是 |
| handler 名稱 `serviceScopeExceptionHandler` | 是 | ✓ 是 |
| 使用 `android.util.Log.e("IME", ...)` | 是 | ✓ 是 |
| 帶 throwable 完整 stack | 是 | ✓ 第 4 參數傳 throwable |
| `serviceScope` 仍 `internal val` | 是 | ✓ 是 |
| `Dispatchers.Main` 維持 | 是 | ✓ 是 |
| `SupervisorJob()` 維持 | 是 | ✓ 是 |
| 三行繁中註解說明 | 是 | ✓ line 159-161 |

→ 實檔 **與 implementation 文件描述完全一致**，非單純文件層級宣告。

### 2.3 既有 callsite 未動（抽查）

雪鑰抽查 `serviceScope` 既有 callsite：

- `YukiBoardIMEService.kt:272` — `imeFraudModelInit = ImeFraudModelInit(applicationContext, serviceScope)` → 傳遞，未動 ✓
- `YukiBoardIMEService.kt:273` — `imeImageCommitter = ImeImageCommitter(this, serviceScope)` → 傳遞，未動 ✓
- `YukiBoardIMEService.kt:278-282` — RIME init `serviceScope.launch { try {...} catch ... }`，內含 `Log.w("IME", ...)`，**未動** ✓
- `onDestroy` 路徑（line 670 區塊周邊）`serviceScope.cancel()` → 雪鑰確認未動 ✓

→ 鍵律 patch **未擴張到任一 launch callsite**，符合 §3 裁決「不擴成完整 observability」要求。

---

## 3. 編譯驗證

雪鑰執行：

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew :app:compileDebugKotlin
```

結果：

```text
> Task :app:compileDebugKotlin UP-TO-DATE
BUILD SUCCESSFUL in 1s
18 actionable tasks: 18 up-to-date
```

說明：

- `UP-TO-DATE` 表示鍵律已執行過 compile，且 source hash 與已編譯 output 一致 → source **目前處於可編譯狀態**
- 無新 warning 與本輪 patch 相關
- 鍵律宣稱 `BUILD SUCCESSFUL in 13s`（首次 compile）→ 本次 `1s` UP-TO-DATE 屬遞增驗證一致

→ 編譯 **通過**。

---

## 4. 語意風險檢查

### 4.1 `CoroutineExceptionHandler` 語意

- ✓ Handler 只在「例外 propagate 到 scope 頂部且無 parent 處理」時觸發
- ✓ `CancellationException` 依 Kotlin coroutine 協議由框架處理，**不會進** handler → `serviceScope.cancel()`（onDestroy 路徑）行為無誤
- ✓ Handler 不會吞掉 caller-side `try-catch`：例如 line 278-282 的 RIME init 內已 catch `Exception`，不會雙重觸發

### 4.2 `SupervisorJob` + handler 共存

- ✓ `SupervisorJob()` 語意：child failure 不殺 sibling
- ✓ Handler 語意：child failure propagate 到 root 後記錄
- ✓ 兩者**互補不衝突**：SupervisorJob 阻止連坐，handler 提供 root-level 觀測點
- ✓ 鍵律未把 `SupervisorJob()` 換成 `Job()`，child 隔離完整保留

### 4.3 Log tag 一致性

- ✓ 既有同檔 line 281 已使用 `android.util.Log.w("IME", ...)`
- ✓ 鍵律新加 `android.util.Log.e("IME", ...)` → tag 一致，level 對應「未捕捉例外」嚴重度合理
- ✓ 訊息前綴 `"Unhandled coroutine exception in serviceScope: "` 明確標示來源 scope，便於 logcat 過濾

### 4.4 Visibility

- ✓ `serviceScopeExceptionHandler`：`private val` — 僅本檔內 scope 組裝使用，無外洩
- ✓ `serviceScope`：維持 `internal val` — 其他 `Ime*` 檔的傳遞與 `launch` 用法不變

### 4.5 假修 / 漏修檢查

- ✓ **非假修**：handler 真實掛入 `CoroutineScope` constructor 第 3 個 context element，runtime 會生效
- ✓ **非漏修**：本輪裁決明文「以單檔最小 patch 統一記錄」，不要求動 callsite；patch 已覆蓋所有透過 `serviceScope` 傳遞的 child coroutine 觀測面
- ✓ **無誤抓 CancellationException**：依 Kotlin 慣例，無需特別排除

---

## 5. 二審結論

### 5.1 雪鑰判定

**通過二審**，可交 `玄樞` 做 gate 終裁。

### 5.2 通過理由

1. **File scope 完全合規**：本輪 .kt 真實落點僅 `YukiBoardIMEService.kt` line 71（import）+ line 158-171（handler/scope 組裝）。其他工作樹中的 `.kt` 修改（`ImeServiceCallbacks.kt` / `AuthSettingsSection.kt`）為 S10-2 / S10-3 既有變更，與本輪無關。
2. **實檔落地驗證**：雪鑰打開 `YukiBoardIMEService.kt` 親自核對，與鍵律 implementation 文件描述完全一致，非紙上宣告。
3. **編譯通過**：`compileDebugKotlin BUILD SUCCESSFUL`（UP-TO-DATE 遞增驗證一致）。
4. **語意正確**：handler 與 `SupervisorJob` 互補、`CancellationException` 自動排除、log tag 與既存 `Log.w("IME", ...)` 一致、`private` / `internal` visibility 配置合理。
5. **未越界、未假修、未漏修**：鍵律未動任一 launch callsite、未升級 log 框架、未碰禁區檔；裁決 §3「不擴成完整 observability / crash reporting」與 §C1「最小修點在 scope 建立處」皆嚴守。

### 5.3 Non-blocking 觀察（不構成退件）

| 項目 | 說明 | 處理建議 |
|------|------|---------|
| `withContext(Dispatchers.IO)` rethrow-to-caller 路徑 | 若上游 caller 漏抓，例外會回到 `serviceScope` 進 handler；若上游有抓則本 patch 不影響 | 已涵蓋，無需動作 |
| `coroutineScope { ... }` 內部 child 例外 | `coroutineScope` 自身 propagate 到 caller，仍經 `serviceScope` → handler 統一捕捉 | 已涵蓋 |
| 各 sub-coordinator（`ImeFraudModelInit` / `ImeImageCommitter`）若日後改用獨立 scope | 屆時 handler 不會自動覆蓋，需各自掛 handler | 屬未來 round 議題（S10-5+），本輪不納入 |

---

## 6. 給 `玄樞` 的二審摘要

```text
雪鑰二審通過。建議 READY。

Reason:
1. File scope 親自實檔複核：S10-4 真實 patch 僅 YukiBoardIMEService.kt 的 import + serviceScope 組裝（line 71、158-171），未越界。
2. 工作樹其他 .kt 修改（ImeServiceCallbacks.kt / AuthSettingsSection.kt）屬 S10-2 / S10-3 既有，與本輪無關。
3. compileDebugKotlin 通過，語意檢查（CoroutineExceptionHandler vs CancellationException vs SupervisorJob 三者共存）皆無風險。
4. Log tag 與既有 line 281 Log.w("IME", ...) 風格一致；handler 為 private val，scope 維持 internal val。
5. 未動任一 launch callsite，未升級 log 基礎建設，符合本輪裁決「最小單檔 guardrail」。

請 玄樞 親自打開 YukiBoardIMEService.kt 行 71、行 158-171 做最終實檔複核。
```

---

## 7. 雪鑰本輪交付資產

| 資產 | 路徑 |
|------|------|
| 本份 review 文件 | `docs/SPRINT10_4_CCA_COROUTINE_EXCEPTION_HANDLER_REVIEW_2026-04-18.md` |

雪鑰本輪 **未產生任何 `.kt` 改動**，符合二審角色定位。

提交給：`玄樞`。

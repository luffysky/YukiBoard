# Sprint 8-3 Claude Code A Impact Note

- 身份：Claude Code A（本業）
- 時間：2026-04-18
- 適用 round：`S8-3` UserDictionary Listener Release Strengthening
- 對應 gate 任務：`S8-3-CCA-USERDICT-RELEASE-IMPACT-NOTE`
- 審核對象：`CodeX A`
- 依 gate 要求：不改 `.kt`、只盯 `release()` wiring 與 lifecycle 對稱性、不得自行指定後續 round
- 本文件格式：單 PART（`CodeX B` 已回職，技術審由 `S8-3-CXB-USERDICT-RELEASE-TECH-REVIEW` 處理）

---

## I. S8-3 本輪定位

S8-2 已修好 UserDictionary 的 cross-instance shared-state 問題（`versionLock` + `max+1` + listener reload + `apply()` 原子提交）。S8-2 附帶產出兩個 non-blocking 強化點：

1. `UserDictionary.release()` 已被宣告（unregister listener 出口），但**目前無 callsite 呼叫**
2. 長生命週期持有者（`InputRouter.userDict`）理論上應在 IME service onDestroy 時 release listener，避免 SharedPreferences 的 WeakHashMap entry 滯留到 GC

S8-3 的唯一目標：**補上 `release()` 的實際接線**，不改 S8-2 的 shared-state 主修補語意。

---

## II. UserDictionary 6 個 callsite 生命週期盤點

| callsite | 生命週期 | 本輪是否需 release() |
| --- | --- | --- |
| `InputRouter.kt:27` `private val userDict` | 長生命週期（Router 與 IME service 同壽） | **是**（S8-3 主要 wiring 目標） |
| `ImeServicePanels.kt:351` USER_DICT panel | 中生命週期（panel 建立後常駐至 IME destroy） | 否（gate 未列 `ImeServicePanels.kt` 為允許檔；本輪 out of scope） |
| `SettingsActivity.kt:190` 匯出 lambda | 短生命週期（lambda 內 new，執行完即脫離引用） | 否（gate 未列 `SettingsActivity.kt`；本輪 out of scope） |
| `SettingsActivity.kt:402` 匯入 lambda | 短生命週期 | 否（同上） |
| `CloudSyncManager.kt:220` upload | 短生命週期（方法內 new，方法結束脫離） | 否（gate 未列 `CloudSyncManager.kt`；本輪 out of scope） |
| `CloudSyncManager.kt:312` download | 短生命週期 | 否（同上） |

**CCA 對 gate scope 的判讀**：S8-3 只鎖 `InputRouter` 這個長生命週期持有者，其餘 5 個短/中生命週期 callsite 的 listener 由 SharedPreferences 內部 `WeakHashMap` 在 GC 時自動清理，不屬本輪題。後續若要補上短生命週期 callsite 的 try/finally release，需另開新 round。

---

## III. 現有 lifecycle hook 基礎建設（S8-3 接線依據）

### III-A. `ImeSessionHooks.kt` 已有的 cleanup 資料類

目前 `app/ime/ImeSessionHooks.kt` 已定義 3 個 cleanup data class（line 67-91），`YukiBoardIMEService` 透過它們把 lifecycle lambdas 傳進 session hooks：

| 資料類 | 觸發時機 | 現有欄位 |
| --- | --- | --- |
| `FinishInputViewCleanup` | onFinishInputView | cancelAsyncWork / stopClipboardMonitoring / persistSmartInput / resetInputState / stopSensors / resetManualModeOverride / pauseVisualEffects / flushSentenceBuffer / pauseGameLoop |
| `FinishInputCleanup` | onFinishInput | resetInputState / stopSensors / resetTouchState |
| `DestroyCleanup` | onDestroy | unregisterReceivers / stopSensors / stopGameLoop / destroyAudioAndRemote / cancelScope |

### III-B. Gate 列的檔案對映

Gate 列允許檔：`app/ime/DestroyCleanup.kt` / `app/ime/FinishInputCleanup.kt`。實際這兩個資料類目前**內嵌於 `ImeSessionHooks.kt`**，沒有單獨 `.kt` 檔。CCB 有兩種實作選擇：

- **選項 A（建議）**：在 `ImeSessionHooks.kt` 內就地擴充 `DestroyCleanup` data class，新增 `releaseInputRouter: () -> Unit`（或等義命名）欄位；不拆檔。scope 最小。
- **選項 B**：先把 `DestroyCleanup` / `FinishInputCleanup` 拆成獨立 `.kt`，再擴欄位。屬 refactor + bug fix 混題，scope 較大，不建議。

**CCA 建議 CCB 走選項 A**。gate 的檔名可解釋為「這些資料類」而非「這些檔」；若 `CodeX A` 事後堅持要拆檔，可另開 refactor round。

---

## IV. S8-3 wiring 推薦設計（供 CCB / `CodeX B` 參考）

### IV-A. `InputRouter` 新增 `release()`

```kotlin
// InputRouter.kt
fun release() {
    userDict.release()
    // 不清 engine / smartInput / candidateManager（S8-3 scope 只盯 userDict listener）
}
```

要求：
- 冪等：重複呼叫不崩（`UserDictionary.release()` 本身是 `unregisterOnSharedPreferenceChangeListener`，重複 call safe）
- 只處理 `userDict`，不擴到其他 engine（`JapaneseInputEngine` / `ChewingInputEngine` 等若有 listener 亦留待後續 round）

### IV-B. `DestroyCleanup` 擴欄位

```kotlin
// ImeSessionHooks.kt
data class DestroyCleanup(
    val unregisterReceivers: () -> Unit,
    val stopSensors: () -> Unit,
    val stopGameLoop: () -> Unit,
    val destroyAudioAndRemote: () -> Unit,
    val cancelScope: () -> Unit,
    val releaseInputRouter: () -> Unit,  // ← 新增
)

fun onDestroy(cleanup: DestroyCleanup) {
    imeCoordinator.onDestroy()
    cleanup.unregisterReceivers()
    cleanup.stopSensors()
    cleanup.stopGameLoop()
    cleanup.destroyAudioAndRemote()
    cleanup.releaseInputRouter()   // ← 新增；建議排在 cancelScope 之前
    cleanup.cancelScope()
}
```

### IV-C. `YukiBoardIMEService.onDestroy` 接線

```kotlin
// YukiBoardIMEService.kt:615-
override fun onDestroy() {
    imeSessionHooks.onDestroy(
        cleanup = DestroyCleanup(
            unregisterReceivers = { ... },
            stopSensors = { ... },
            stopGameLoop = { gameLoop?.stop() },
            destroyAudioAndRemote = { ... },
            releaseInputRouter = { inputRouter.release() },  // ← 新增
            cancelScope = { ... },
        ),
    )
    super.onDestroy()
}
```

### IV-D. `FinishInputCleanup` **不**加 release

`onFinishInput` 只代表一次輸入 session 結束（切 App / 收鍵盤），Router 與 UserDictionary 依舊存活跨 session 使用。若在 finishInput unregister listener，下次 session 需重新 register，語意錯誤，還會多一次 register/unregister 的抖動。

**CCA 紅線**：`FinishInputCleanup` / `FinishInputViewCleanup` 本輪**不擴**。gate 列 `app/ime/FinishInputCleanup.kt` 為允許檔，但不代表必須改；`CodeX A` 可能只是把 scope 範圍開寬，實際不需動它。

---

## V. 紅線清單（CCA 要求 CCB 遵守）

1. **不改 S8-2 shared-state 主修補語意**：`versionLock` / `max+1` / `editWithVersionBump` / `OnSharedPreferenceChangeListener` / `loadAll()` 觸發邏輯全部保留原樣
2. **不引入 singleton**：`UserDictionary.getInstance(context)` 禁止
3. **不動 Sprint 7 integration 檔**（UserSessionRepository / InsightApiClient / AppHttpClient / SnowRealmAuthClient / AIProviderManager / ApiKeySync）
4. **不動 7 smart subsystem**（AdaptiveDictionary / CorrectionLearnManager / PersonalDictionary / PhrasePredictor / SentencePredictor / StyleProfiler / TypingHabitTracker）
5. **不動 provider / auth / sync / token 題**
6. **不動 `ImeServicePanels.kt` / `SettingsActivity.kt` / `CloudSyncManager.kt`** — gate 未列允許，這輪屬 out of scope
7. **不改 `handleKeyPress` 或軌道切換核心**（CLAUDE.md 原生紅線）
8. **不改 `YukiBoardIMEService` 新增非 release-wiring 的責任**（CLAUDE.md 原生紅線）
9. **不改 SharedPreferences key / 版本號格式**
10. **不順手處理短生命週期 callsite**（SettingsActivity / CloudSyncManager local var 的 release）— 另開 round
11. **不擴到其他 engine 的 listener release**（Japanese / Chewing / Latin 語言包）— 另開 round
12. **不加 finishInput 時的 release**（語意錯）

---

## VI. Lifecycle 對稱性驗證點（供 `CodeX B` 技術審）

1. `UserDictionary.init { registerOnSharedPreferenceChangeListener }` ↔ `release { unregisterOnSharedPreferenceChangeListener }` — 對稱 ✅
2. `InputRouter` 建構時 new `userDict` ↔ `release()` 呼叫 `userDict.release()` — 對稱 ✅
3. `YukiBoardIMEService` 建構 `inputRouter` ↔ `onDestroy` 呼叫 `inputRouter.release()` — 對稱 ✅
4. `release()` 冪等：`unregisterOnSharedPreferenceChangeListener` 對未註冊 listener 是 no-op，安全
5. Release 後 router 是否應視為死？S8-3 scope：release 只解 listener，不改變 router 可用性；如需完整 destroy router，另開 round
6. Release 之後若有 background coroutine 仍在寫 UserDictionary：`editWithVersionBump` 仍可執行但 listener 已被解，其他 instance 收不到事件。此情境僅在「IME destroy 後 CloudSync 仍在跑 upload」時出現。CCA 觀察：`ImeSessionHooks.onFinishInputView` 已先行 `cloudSyncManager.onSessionEnd()`，onDestroy 時背景 upload 理論上已收斂；即便未收斂，也只影響該次 upload 的 cross-instance 可見性，不損資料持久性。

---

## VII. 本輪驗證 checklist（CCB 交付後 CCA 本業會再核對）

1. ✅ `InputRouter.release()` 存在且呼叫 `userDict.release()`
2. ✅ `DestroyCleanup` 新增 `releaseInputRouter: () -> Unit`
3. ✅ `ImeSessionHooks.onDestroy` 內呼叫順序合理（其他 destroy lambda 之後、cancelScope 之前或之後皆可）
4. ✅ `YukiBoardIMEService.onDestroy` supply `releaseInputRouter = { inputRouter.release() }`
5. ✅ `FinishInputCleanup` / `FinishInputViewCleanup` 未被改動
6. ✅ `UserDictionary.kt` 本輪 delta 僅限可能的小幅 doc 微調（若有），shared-state 相關程式碼一字不改
7. ✅ `ImeServicePanels.kt` / `SettingsActivity.kt` / `CloudSyncManager.kt` 一字不改
8. ✅ 無 singleton / getInstance 引入
9. ✅ 未新增其他 engine release
10. ✅ compile 通過，無新 warning

---

## VIII. 本輪 CCA 不做的事

- 不主導 `release()` 實作，只提設計建議
- 不指派 S8-4 / 後續 round
- 不擴 impact note 到 shared-state 主修補的第二次審（S8-2 已結）
- 不跑 compile 驗證（由 CCB 交付側回報）

---

## IX. 回報給 `CodeX A` 的關鍵請求

1. 請 `CodeX A` 確認選項 A（就地擴充 `DestroyCleanup` in `ImeSessionHooks.kt`）是否可接受，或堅持要拆 `DestroyCleanup.kt` / `FinishInputCleanup.kt` 成獨立檔
2. 請確認「短生命週期 callsite 的 release」確實留給後續 round，非本輪題
3. 請確認本輪 `FinishInputCleanup` 不擴是否符合原意

候選後續（**非 CCA 指派**）：

- S8-4 候選題（擇一由 `CodeX A` 決定開門）：
  - 短生命週期 callsite 的 try/finally release
  - 其他 engine 的 listener release 盤點
  - `InputRouter` 完整 destroy 語意
  - Sprint 8 結案

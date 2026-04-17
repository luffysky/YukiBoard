# Sprint 8 Round 3 — CCB UserDictionary Listener Release Strengthening 回交

身份：Claude Code B
任務 ID：`S8-3-CCB-USERDICT-RELEASE-STRENGTHENING`
日期：2026-04-18

對應 `Sprint8-All-Round-Gate.md` S8-3 目標：為 `UserDictionary.release()` 補實際接線，讓長生命週期持有者在拆解時明確解除 listener。僅做 non-blocking strengthening，不改變 `S8-2` 已完成的 shared-state 修補語意。

---

## A. 變更摘要

### 修改檔案（3 個，全在 S8-3 允許 scope 內）

1. `app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt`
2. `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionHooks.kt`（內含 `DestroyCleanup` data class）
3. `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`

未修改：`UserDictionary.kt`（`release()` 已於 S8-2 落地，本輪只接線）。

### 設計決策

- S8-2 的 `UserDictionary.release()` 已是 no-op-safe（僅 `unregisterOnSharedPreferenceChangeListener`），但沒有 callsite 呼叫，長生命週期 instance 靠 GC 才被清掉。
- YukiBoard 只有一個真正的長生命週期 `UserDictionary` instance：由 `InputRouter` 在 `InputRouter(applicationContext)` 建構時持有（`YukiBoardIMEService.kt:239`）。其他 4 個 callsite（USER_DICT panel / SettingsActivity 2 處 / CloudSyncManager 2 處）都是短生命週期，用完即 GC，不需要明確 release。
- 因此把 release 責任內聚在 `InputRouter`（它擁有 `userDict`），並由 IME service 的 `onDestroy` 路徑觸發，語意與 `destroyAudioAndRemote`、`stopSensors` 等其他 destroy 動作對稱。

### Fix 細節

#### 1. `InputRouter.release()`（新增）

```kotlin
/**
 * S8-3：IME service 拆解時呼叫，解除長生命週期 UserDictionary 的
 * SharedPreferences listener，避免留在 SharedPreferencesImpl.mListeners 的
 * WeakHashMap entry 延後到 GC 才清掉。
 *
 * 僅清 listener，不影響已 commit 的 prefs 資料。
 */
fun release() {
    userDict.release()
}
```

- 位置：`warmUpChewing()` 之後（與其他 service-lifecycle 相關方法同區塊）。
- 不串接其他 engine：`EnglishInputEngine`、`ChewingInputEngine` 等目前沒有 listener-class 需要釋放，保持 minimal scope。若未來新增，再擴充本方法。

#### 2. `DestroyCleanup` 新增 `releaseInputRouter` 欄位

`ImeSessionHooks.kt`：

```kotlin
data class DestroyCleanup(
    val unregisterReceivers: () -> Unit,
    val stopSensors: () -> Unit,
    val stopGameLoop: () -> Unit,
    val destroyAudioAndRemote: () -> Unit,
    val releaseInputRouter: () -> Unit,   // ← 新增
    val cancelScope: () -> Unit,
)
```

`onDestroy` 呼叫順序：

```kotlin
fun onDestroy(cleanup: DestroyCleanup) {
    imeCoordinator.onDestroy()
    cleanup.unregisterReceivers()
    cleanup.stopSensors()
    cleanup.stopGameLoop()
    cleanup.destroyAudioAndRemote()
    cleanup.releaseInputRouter()   // ← 放在 audio/remote 之後、cancelScope 之前
    cleanup.cancelScope()
}
```

- 放在 `cancelScope` 之前：確保 coroutine 還在時 release（雖然 release 本身不用 coroutine，順序上讓 Kotlin 物件 cleanup 都在 scope cancel 前完成比較一致）。
- 放在 `destroyAudioAndRemote` 之後：與其他「釋放外部資源 / listener」類別同一段落。

#### 3. `YukiBoardIMEService.onDestroy` 傳入 callback

```kotlin
destroyAudioAndRemote = { ... },
releaseInputRouter = {
    // S8-3：解除 InputRouter 持有的 UserDictionary listener。
    if (::inputRouter.isInitialized) {
        inputRouter.release()
    }
},
cancelScope = { ... },
```

- `::inputRouter.isInitialized` 防禦：即使 `onStartInputView` 未曾被呼叫（service 極短命週期），destroy 路徑也不會 NPE。

### Gate 文件 scope 說明

Gate 原本列 `app/ime/DestroyCleanup.kt` 與 `app/ime/FinishInputCleanup.kt` 兩檔。實際 codebase 這兩個都是 **data class**，共存在 `app/ime/ImeSessionHooks.kt` 中（非獨立檔）。本輪只改 `ImeSessionHooks.kt` 裡的 `DestroyCleanup` 欄位結構，等同 gate 精神。未新建獨立檔避免額外結構變動。

---

## B. 風險

1. **低風險**：僅新增一個 field + 一次 callback 呼叫，現有 destroy 路徑其他步驟零改動。
2. **`isInitialized` 檢查**：若 `onDestroy` 在 `onStartInputView` 之前被呼叫（理論上 Android 不會發生，但 service 被系統 kill 前若未 bind view 仍可能），`inputRouter` 未初始化，此檢查避免 NPE。
3. **release 後若還有其他 instance 寫入**：其他（短生命週期）instance 寫 prefs 時，InputRouter 已 release listener，不會觸發 reload — 但此時 InputRouter 本身也已在 destroy 路徑，不再提供建議服務，沒有實際影響。
4. **不影響 S8-2 shared-state 語意**：`UserDictionary.release()` 只 unregister listener，不清 in-memory map、不清 prefs，其他 instance 的 shared-state 行為完全不變。
5. **未處理 FinishInputView / FinishInput 階段**：按 gate 目標只做 destroy 級別 release（InputRouter 的生命週期是 service-scoped，與 `onFinishInput` 解耦）。若未來要拆 per-session 的 UserDictionary instance，再另開 round 處理。

---

## C. 下一步建議

**不自排 S8-4**，候選給 `CodeX A` 決定：

- **候選 A — Sprint 8 正式結案**
  - S8-0 / S8-1 / S8-2 / S8-3 均已落地，`kt_review_checkbug.md` 的 4 個 finding 全數收斂且 non-blocking strengthening 亦完成。由 `CodeX A` 宣告 `Sprint 8 = COMPLETED` 並規劃 Sprint 9。
- **候選 B — S8-4 新 bug 掃描**
  - 若 `CodeX A` 想在結案前再做一次最終 rescan，確認 S8-1 / S8-2 / S8-3 改動未引入新 regression。
- **候選 C — 其他 non-blocking strengthening**
  - 若有其他類似 S8-3 的弱項（如 `SpeechEngine` listener、`CloudSyncManager` polling job 的 lifecycle 對稱性），可同規格續做。但目前 `kt_review_checkbug.md` 未列，需要 `CodeX A` 指定。

---

## D. 架構決策

- **Release 責任內聚在 InputRouter**：`userDict` 為 InputRouter private field，對外只曝露 `release()` 一個入口，其他層（IME service / cleanup 資料類）不直接碰 `UserDictionary.release()`。
- **Cleanup 結構沿用 S2 既有模式**：`DestroyCleanup` data class 以 lambda field 集中 destroy 動作，本輪新增 `releaseInputRouter` 維持同風格，不新增 coordinator / repository / interface。
- **未建立獨立 `DestroyCleanup.kt`/`FinishInputCleanup.kt`**：尊重 codebase 現狀（兩者共存於 `ImeSessionHooks.kt`），不為了對齊 gate 文件字面而製造額外檔案。

---

## E. 驗證

1. `./gradlew.bat :app:compileDebugKotlin --no-daemon` → `BUILD SUCCESSFUL in 17s`。
2. 無新 warning / error（pre-existing `ImeServiceLifecycle.kt:109` condition-always-true warning 非本輪引入）。
3. 行為驗證（靜態推導）：
   - **正常路徑**：user 使用 IME → `onStartInputView` → `inputRouter` 初始化、`userDict` 註冊 listener → user 結束 → `onDestroy` → `cleanup.releaseInputRouter()` → `inputRouter.release()` → `userDict.release()` → `unregisterOnSharedPreferenceChangeListener` → `SharedPreferencesImpl.mListeners` WeakHashMap entry 立即移除 ✓
   - **service 未開啟面板就 destroy**：`::inputRouter.isInitialized` 為 false → 跳過，不 NPE ✓
   - **S8-2 shared-state 語意保持**：release 不動 prefs、不動 in-memory map、不動 `lastKnownVersion`，其他 instance 的 listener 未受影響，version bump 行為完全不變 ✓
4. 只動到 S8-3 允許 scope 內且為最小子集：
   - `input/router/InputRouter.kt` ✅
   - `app/ime/ImeSessionHooks.kt`（內含 `DestroyCleanup` data class）✅
   - `service/YukiBoardIMEService.kt` ✅
   - `app/ime/DestroyCleanup.kt` / `FinishInputCleanup.kt`：檔案實際不存在，其內容在 `ImeSessionHooks.kt` 內處理 ✅
   - `input/dictionary/UserDictionary.kt` 未修改（S8-2 的 `release()` 已存在，本輪只接線）✅
5. 未觸碰禁修區：
   - UserDictionary shared-state 主修補語意（`versionLock` / `editWithVersionBump` / prefsListener）✅
   - Sprint 7 integration 檔 ✅
   - 7 smart subsystem ✅
   - provider / auth / sync 題 ✅
   - 其他未經核可的 batch 題 ✅

---

## CodeX A 審核需求

- 需 `CodeX A` gate：`S8-3` 是否可結案；若 Sprint 8 已無待辦，是否宣告 `Sprint 8 = COMPLETED` 轉 Sprint 9。
- Claude Code B 不自排 `S8-4`；候選方向已列於 C 段，最終由 `CodeX A` 判定。

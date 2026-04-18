# Sprint 11-4 — Silent-Catch First Pass Implementation

- **Task ID**: `S11-4-CCB-SILENT-CATCH-FIRST-PASS-IMPLEMENTATION`
- **日期**: 2026-04-18
- **交付人**: 鍵律（CCB），依 §10.2 代行 衡鑑 input/service lane 技術實作
- **審核人**: 雪鑰，依 §10 代行 玄樞 終審（CodeX A / CodeX B 額度雙耗盡）
- **Scope**: A（service/ime 空 catch）+ B（input/engine 明顯吞例外 = `printStackTrace`）
- **授權來源**: 雪鑰代行玄樞 2026-04-18 正式裁示（scope 鎖死 9 檔以內、tag 不動、coroutine 必排 CancellationException、不開 S11-5、不 unrelated refactor）

---

## A. 改動清單（8 檔 × 15 處）

| # | 檔案 | 位置 | 原樣 | 改後 | 類型 |
|---|------|------|------|------|------|
| 1 | `service/ImeServiceLifecycle.kt` | `registerClipboardFraudListener` 內層 listener | `catch (_: Exception) { }` | `Log.w("IME", "Clipboard fraud scan failed: ...", e)` | A |
| 2 | `service/ImeServiceLifecycle.kt` | `registerClipboardFraudListener` 外層 | `catch (_: Exception) { }` | `Log.w("IME", "registerClipboardFraudListener failed: ...", e)` | A |
| 3 | `service/ImeServiceLifecycle.kt` | `setupKeyboardOverlays`：`OneHandSide.valueOf` | `catch (_: Exception) { KeyboardView.OneHandSide.NONE }` | `Log.w("IME", "Invalid oneHandSide pref '...': ...", e)` + 保留 fallback | A |
| 4 | `service/ImeServiceMisc.kt` | `unregisterClipboardFraudListener` | `catch (_: Exception) { }` | `Log.w("IME", "unregisterClipboardFraudListener failed: ...", e)` | A |
| 5 | `service/ImeServicePanels.kt` | `createHighDependencyPanelOrNull` BackgroundPicker image | `catch (_: Exception) {}` | `Log.w("YukiIME", "Launch BackgroundPicker (image) failed: ...", e)` | A |
| 6 | `service/ImeServicePanels.kt` | 同上，video | `catch (_: Exception) {}` | `Log.w("YukiIME", "Launch BackgroundPicker (video) failed: ...", e)` | A |
| 7 | `service/ImeServicePanels.kt` | `launchMidiImportFor` | `catch (_: Exception) {}` | `Log.w("YukiIME", "Launch MidiImportActivity failed: ...", e)` | A |
| 8 | `service/KeyboardSoundEngine.kt` | `playFreq` → `scope.launch { playPercussion }` | 空 catch（coroutine） | +`CancellationException` rethrow；`Log.w("KeyboardSoundEngine", "playFreq/playPercussion failed: ...", e)` | A-coroutine |
| 9 | `service/KeyboardSoundEngine.kt` | `playFreq` → `scope.launch { playTone }` | 同上 | +rethrow；`Log.w("KeyboardSoundEngine", "playFreq/playTone failed: ...", e)` | A-coroutine |
| 10 | `service/KeyboardSoundEngine.kt` | `playKeyTone` → `scope.launch { playPercussion }` | 同上 | +rethrow；`Log.w("KeyboardSoundEngine", "playKeyTone/playPercussion failed: ...", e)` | A-coroutine |
| 11 | `service/KeyboardSoundEngine.kt` | `playKeyTone` → `scope.launch { playTone }` | 同上 | +rethrow；`Log.w("KeyboardSoundEngine", "playKeyTone/playTone failed: ...", e)` | A-coroutine |
| 12 | `service/YukiBoardIMEService.kt` | `unregisterBgUpdateReceiver` | `try { unregisterReceiver(it) } catch (_: Exception) {}` | `Log.v("IME", "unregisterBgUpdateReceiver: already unregistered (<ExceptionClass>)")`（無 throwable、降級為 Log.v） | A-unregister |
| 13 | `app/ime/ImeSessionHooks.kt` | `onFinishInputView` → `scope.launch { cloudSyncManager.onSessionEnd() }` | 空 catch（coroutine） | +`CancellationException` rethrow；`Log.w("ImeSessionHooks", "cloudSyncManager.onSessionEnd failed: ...", e)` | A-coroutine |
| 14 | `input/dictionary/EnglishDictionary.kt` | `loadFromResource` | `e.printStackTrace()`（保留 BASIC_WORDS 降級） | `Log.w("EnglishDictionary", "loadFromResource failed, falling back to BASIC_WORDS: ...", e)`（**移除** printStackTrace） | B |
| 15 | `input/engine/LatinInputEngine.kt` | `loadDictionary` | `e.printStackTrace()` | `Log.w("LatinInputEngine", "loadDictionary($langId) failed: ...", e)`（**移除** printStackTrace） | B |

`git diff --stat` 輸出（只針對我這輪新增的 15 處；YukiBoardIMEService 的其他差異為 S10-4/S10-6 既有改動，不在本輪 scope）：

```
 app/ime/ImeSessionHooks.kt                    |  4 +-
 input/dictionary/EnglishDictionary.kt         |  2 +-
 input/engine/LatinInputEngine.kt              |  2 +-
 service/ImeServiceLifecycle.kt                | 13 +++-
 service/ImeServiceMisc.kt                     |  4 +-
 service/ImeServicePanels.kt                   | 12 +++-
 service/KeyboardSoundEngine.kt                | 36 +++++++--
 service/YukiBoardIMEService.kt                |  本輪只動 L349 unregister 區塊（約 6 行）
 8 files changed
```

---

## B. 每處修法理由

### A 類：Service / IME lifecycle 空 catch（#1–#12）

- **理由**：這些原本 `catch (_: Exception) { }` 屬於「吞例外無觀測」典型 silent-catch。修改後全部落點為 `android.util.Log.w(tag, "<context>: ${e.message}", e)`，讓回歸期間 logcat 抓得到 stack trace；執行流程不變（原本就是 swallow-and-continue，沒人 re-throw），所以對外行為無回歸。
- **tag 來源**：每檔沿用既有 tag——
  - `ImeServiceLifecycle.kt` / `ImeServiceMisc.kt` / `YukiBoardIMEService.kt` → 已有 `"IME"`
  - `ImeServicePanels.kt` → 已有 `"YukiIME"`（檔案內其他 Log 呼叫就是這個 tag）
  - `KeyboardSoundEngine.kt` → 檔案內無現存 tag，依雪鑰規則使用檔名 `"KeyboardSoundEngine"`
  - `ImeSessionHooks.kt` → 檔案內無現存 tag，使用檔名 `"ImeSessionHooks"`
- **import**：全部用 full-path `android.util.Log.w(...)` / `Log.v(...)`；**無新增任何 import**，守住「不 unrelated refactor」。

### A-coroutine 類：coroutine 內必排除 CancellationException（#8–#11、#13）

- **理由**：`scope.launch { try { ... } catch (e: Exception) { ... } }` 若把 `CancellationException` 也吞掉，structured concurrency 的取消信號會斷。雪鑰裁示明定：coroutine 內必加 `if (e is kotlinx.coroutines.CancellationException) throw e`。
- **寫法**：在 `catch (e: Exception) {` 的第一行先檢查並 rethrow，再走 Log.w。同樣用 full-path 型別名，避免新增 `import kotlinx.coroutines.CancellationException`。

### A-unregister 類：#12（YukiBoardIMEService.unregisterBgUpdateReceiver）

- **理由**：`unregisterReceiver` 對「已經沒註冊過」丟 `IllegalArgumentException` 是 Android framework 的正常回聲，不屬於真故障；用 Log.w + throwable 會在 logcat 冒出醒目的 stack，造成雜訊。雪鑰規則指名此處使用 `Log.v` + 不帶 throwable，只留可觀測的 exception class 名稱即可。
- **注意**：這個改法**與其他 14 處刻意不一致**，是規則指名的例外。

### B 類：`printStackTrace()` → `Log.w`（#14、#15）

- **理由**：`e.printStackTrace()` 只寫 stderr，生產環境的 logcat 不會帶 tag / level，屬於吞例外的另一種典型樣式。改成 `Log.w(tag, msg, e)` 後，logcat 有完整 stack、能 filter tag。
- **雪鑰規則明定**：**移除** `printStackTrace()`，不與 Log.w 並存（避免雙寫）。已確認兩處原本的降級邏輯（`EnglishDictionary` 降級到 `BASIC_WORDS`）保留不動。

---

## C. File scope 自檢

雪鑰授權的 file scope 是「service/、app/ime/、input/ 內共 ≤9 檔」，並明確排除 `ChewingInputEngine.kt`、speech / GL 線、以及任何跨模組 exception framework。本輪實際動到 8 檔：

| 檔案 | 授權落點 | 實際動到 | 邊界確認 |
|------|---------|---------|---------|
| `service/ImeServiceLifecycle.kt` | service | ✅ | 3 處 |
| `service/ImeServiceMisc.kt` | service | ✅ | 1 處 |
| `service/ImeServicePanels.kt` | service | ✅ | 3 處 |
| `service/KeyboardSoundEngine.kt` | service | ✅ | 4 處 |
| `service/YukiBoardIMEService.kt` | service | ✅ | 1 處（unregister idiom） |
| `app/ime/ImeSessionHooks.kt` | app/ime | ✅ | 1 處 |
| `input/dictionary/EnglishDictionary.kt` | input | ✅ | 1 處（B 類） |
| `input/engine/LatinInputEngine.kt` | input | ✅ | 1 處（B 類） |

**授權 9 檔上限 → 實際用 8 檔，留 1 檔預算未使用。**

越界自檢：
- ❌ 未觸碰 `ChewingInputEngine.kt`（明令排除）
- ❌ 未新增任何 util / logger wrapper / exception framework class
- ❌ 未觸碰 speech / GL / animation / Lottie
- ❌ 未新增任何 import（所有 Log / CancellationException 都用 full-path）
- ❌ 未改任何 `handleKeyPress` / InputRouter 主路徑
- ❌ 未開 S11-5

另外對「工作樹其他既有修改」（`InputRouter.kt`、`ImeServiceCallbacks.kt`、`AuthSettingsSection.kt`、`YukiBoardIMEService.kt` 的 S10-4/S10-6 區塊、`build.gradle`、`docs/todo_list_0414.md` 等）**完全未碰**——這些是 Sprint 10 既有 WIP，本輪 stash 隔離驗證時已確認 baseline 可編譯（見 D 節）。

---

## D. Compile + Test 證據

### D.1 Baseline 驗證（先 stash 本輪 15 處，只剩既有 WIP）

```
./gradlew :app:compileDebugKotlin
> Task :app:compileDebugKotlin
w: ...ImeServiceLifecycle.kt:109:24 Condition is always 'true'.
（既有 deprecation warning 無新增，略）
BUILD SUCCESSFUL in 23s
18 actionable tasks: 1 executed, 17 up-to-date
```

確認 baseline 乾淨，既有 WIP 不會污染本輪 gate。

### D.2 本輪 15 處 restore 後

```
./gradlew :app:compileDebugKotlin
> Task :app:compileDebugKotlin
w: ...ImeServiceLifecycle.kt:113:24 Condition is always 'true'.
BUILD SUCCESSFUL in 4s
18 actionable tasks: 1 executed, 17 up-to-date
```

- 唯一警告（L113 "Condition is always 'true'"）來自既有邏輯本身，因我在其上新增 3 行造成行號位移；warning 本體早已存在於 baseline，非本輪引入。
- **無任何 `e:` compile error。**

### D.3 Unit test gate

```
./gradlew :app:testDebugUnitTest
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 7s
31 actionable tasks: 4 executed, 27 up-to-date
```

- 包含 Sprint 11-1 / 11-2 / 11-3 已建立的 `InputRouterTest`、`SmartInputManagerTest`、候選詞 smoke test harness，**全數通過**。

---

## E. 架構決策

- **無新增 class / interface / coordinator / repository。** 本輪刻意不引入 exception framework、logger wrapper、CoroutineExceptionHandler 變更，完全在「把 silent catch 原地加可觀測 log」的邊界內。
- **Tag 策略**：per-file 沿用既有 tag，若無則用檔名 →「tag 不動」規則具象化。
- **Coroutine 規則**：CancellationException rethrow 使用 full-path 型別名，避免新增 import。這維持 S11-4 的最小改動語意，也為後續若統一做 coroutine policy class 留空間（但不在本輪 scope）。
- **Unregister idiom（#12）** 刻意用 Log.v 不帶 throwable——這是一個規則指名的例外，不視為風格不一致，而是對「Android framework 正常回聲」與「實際故障」的刻意區分。

---

## F. 風險盤點

| 風險 | 評估 | 緩解 |
|------|------|------|
| 行為回歸 | **極低**。所有原本就是 swallow-and-continue，本輪只加 log，執行路徑不變。| 已過 `:app:compileDebugKotlin` + `:app:testDebugUnitTest`。|
| Logcat 雜訊爆量 | **低**。#12 unregister idiom 降級為 Log.v、其餘落點多為罕見路徑（載入失敗、剪貼簿異常、picker 未裝、MIDI import 缺）。| 若實機出現 hotspot 可在 S11-5+ 再做分級。|
| Cancellation 行為破壞 | **無**。5 處 coroutine catch 均已優先 rethrow `CancellationException`。| `cloudSyncManager.onSessionEnd` 若長時間 block，取消信號可正常傳播。|
| Tag 不一致 | **可接受**。規則本來就允許 per-file tag + unregister idiom 降級。| 在 D.E 節明文記錄。|

---

## G. 雪鑰審核清單（6 項請逐條核對）

- [ ] **G-1**：15 處改動全部落在授權 8 檔內，未越界到 ChewingInputEngine / speech / GL / Lottie（見 C 節）
- [ ] **G-2**：所有 coroutine 內 catch 都有 `if (e is kotlinx.coroutines.CancellationException) throw e`（#8、#9、#10、#11、#13 = 5 處）
- [ ] **G-3**：Log.w 寫法帶 throwable（14 處非 unregister idiom）；#12 unregister idiom 用 Log.v 且**不帶** throwable（1 處）
- [ ] **G-4**：#14、#15 的 `printStackTrace()` 已**移除**，非與 Log.w 並存
- [ ] **G-5**：無新增 import、無新建 class / util / logger wrapper、無跨模組 exception framework；所有 Log / CancellationException 都用 full-path
- [ ] **G-6**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` 雙 BUILD SUCCESSFUL（D 節 log 已貼；baseline vs restore 對照亦貼）

---

## H. 下一步建議

1. **雪鑰終審 G-1～G-6**，若通過即可在 `Sprint11-All-Round-Gate.md` 將 S11-4 標 `READY / COMPLETED`。
2. **S11-5 仍 LOCKED**：本輪刻意不碰 speech / GL / Chewing 線。開 S11-5 前建議 CodeX 額度恢復、或雪鑰明示放行。
3. **建議下一步 S11-4 followup（非必要）**：若要推行全專案統一的 coroutine policy（例如把 CancellationException 處理集中到一個 extension function），應獨立成 S12+ 的重構 round，不在 S11 內追加。
4. 本輪**未處理**的 silent catch 仍散落於：`speech/`、`ui/panel/`、`animation/`、`sensor/`、`remote/`、`game/`——屬於 S11-4 scope 外，留待後續 round 盤點。

---

## I. 代行鏈條紀錄

- **CodeX A（玄樞 gate 終審）** 額度耗盡 → **雪鑰** 依 §10.1 代行（`READY / NOT READY / LOCKED` 終裁、回交審核）
- **CodeX B（衡鑑 技術實作／審查）** 額度耗盡 → **鍵律（本代理）** 依 §10.2 代行 input/service/engine/lifecycle lane
- 本次 gate **不走 PENDING FINAL CODEX REVIEW**：因雪鑰親自在崗代行玄樞，已取得 .kt 變更的終審權（§10.3 例外條款）

```
CodeX A 額度耗盡 ──→ 雪鑰 代行玄樞（終審）
CodeX B 額度耗盡 ──→ 鍵律 代行衡鑑（實作/diff/測試證據）
董事長 下達 scope=A+B、9 檔上限、tag 不動、CancellationException rethrow、移除 printStackTrace
鍵律 實作 15 處 → compile + test 雙綠 → 本 handoff
→ 雪鑰 逐項核對 G-1～G-6 → READY / NOT READY
```

---

**— 鍵律（CCB 代行）筆**

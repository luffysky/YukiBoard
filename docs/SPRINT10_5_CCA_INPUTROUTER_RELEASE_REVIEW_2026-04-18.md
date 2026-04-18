# SPRINT10-5-CCA-INPUTROUTER-RELEASE-REVIEW

身份：雪鑰（SnowRealm 設定與帳號流資深工程師，本輪代班 `衡鑑` 做技術二審）

日期：2026-04-18

任務 ID：

- `S10-5-CCA-INPUTROUTER-RELEASE-REVIEW`

審核對象：

- 鍵律 回交：`docs/SPRINT10_5_CCB_INPUTROUTER_RELEASE_IMPLEMENTATION_2026-04-18.md`
- 實際 patch：`app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt`

提交鏈下一棒：`玄樞` gate 終裁。

---

## 0. 二審邊界遵守

依 `Sprint10-All-Round-Gate.md` §Round S10-5 二審重點：

1. 只允許 `InputRouter.kt`
2. 不得偷碰 `YukiBoardIMEService.kt` / `ImeServiceCallbacks.kt` / `ImeServiceInput.kt`
3. 修法必須對準 `release()` owner 補完，不得擴成 router 規則 / engine 行為重構
4. 若發現 bug、越界、假修、漏修，直接退回，不得自行放行下一門

雪鑰本輪行為：

- 無 `.kt` 改動（二審角色）
- 親自 `git diff` `InputRouter.kt`，逐行比對 patch 與文件描述
- `grep` 驗證所有 patch 引用的 field / API 真實存在且屬 public
- 抽查確認 lazy engine **未被 touch**
- `compileDebugKotlin` 重跑驗證

---

## 1. File scope 守規檢查

### 1.1 工作樹 .kt 修改全集

`git diff --stat` 結果（僅 `.kt` 部分）：

```text
 .../yukiboard/input/router/InputRouter.kt          | 39 ++++++++++--
 .../yukiboard/service/ImeServiceCallbacks.kt       |  7 ++-
 .../yukiboard/service/YukiBoardIMEService.kt       | 22 ++++++-
 .../ui/settings/section/AuthSettingsSection.kt     |  4 +-
```

雪鑰交叉比對：

| 檔案 | 來源 round | 是否屬本輪 |
|------|-----------|-----------|
| `InputRouter.kt`（+35/-4） | **S10-5（本輪，全部 +35/-4 都是 release() patch）** | **是** |
| `ImeServiceCallbacks.kt`（+4/-3） | S10-2（玄樞已 READY） | 否 |
| `YukiBoardIMEService.kt`（+19/-3） | S10-2 + S10-4（玄樞已 READY） | 否 |
| `AuthSettingsSection.kt`（+2/-2） | S10-3（玄樞已 READY） | 否 |

→ 鍵律 S10-5 patch **真實落點僅 `InputRouter.kt`**，未越界。

### 1.2 禁區清單對照

| 禁區檔/區 | 結果 |
|-----------|------|
| `YukiBoardIMEService.kt` | 未在本輪動 ✓ |
| `ImeServiceCallbacks.kt` | 未在本輪動 ✓ |
| `ImeServiceInput.kt` | 未動 ✓ |
| candidate / scoring / auto detect 規則 | 未動（diff 僅 `release()` 函式內部，未碰 `processChar` / `commitAndReset` / `switchTo` / `autoDetectAndSwitch` / `getAutoCombinedCandidates` 等熱路徑） ✓ |
| `Settings` / `auth` / payment / sync 相關 | 未在本輪動 ✓ |
| `Sprint 10` gate / `RULE` / `daily_works_0418.md` | 未動 ✓ |

→ File scope 守規 **完全符合** 玄樞本輪裁決。

---

## 2. 實檔複核（Patch 真實落地）

### 2.1 Diff 結構驗證

`git diff` 顯示 `InputRouter.kt` 唯一變更區塊位於 `release()`（line 422-469），分為：

- **KDoc 重寫**（+18 行）：說明執行順序與刻意保留項
- **Body 擴張**（+12 行 / -1 行）：從單行 `userDict.release()` 延伸至 6 類清理鏈

雪鑰逐項對照鍵律宣稱與實檔：

| 步驟 | 鍵律宣稱 | 實檔 line | 結果 |
|------|---------|-----------|------|
| 1. SmartInput flush | `smartInput.persist()` + `smartInput.maintenance()` | 446-447 | ✓ 落實 |
| 2. eager engine reset | `englishEngine.reset()` + `bopomofoEngine.reset()` | 449-450 | ✓ 落實 |
| 3. latinEngines 清理 | for-loop reset + map clear | 451-454 | ✓ 落實 |
| 4. candidate 清理 | `candidateManager.clear()` | 456 | ✓ 落實 |
| 5. AUTO buffer + pending 清理 | `setLength(0)` / `clear()` / `= null` | 458-460 | ✓ 落實 |
| 6. userDict release（S8-3 收尾） | `userDict.release()` | 462 | ✓ 落實 |

→ 與文件描述 **完全一致**，非紙上宣告。

### 2.2 引用 Field / API 真實性驗證

雪鑰 `grep` `InputRouter.kt` field 宣告：

| 引用 | 宣告 line | 類型 / lazy 狀態 |
|------|-----------|-----------------|
| `smartInput` | 54 | `val smartInput = SmartInputManager(context)` — **eager** ✓ |
| `englishEngine` | 28 | `private val englishEngine: InputEngine = EnglishInputEngine(context)` — **eager** ✓ |
| `bopomofoEngine` | 29 | `private val bopomofoEngine: InputEngine = ChewingInputEngine(...)` — **eager** ✓ |
| `latinEngines` | 40 | `private val latinEngines = LinkedHashMap<String, InputEngine>(8, 0.75f, true)` — runtime LRU ✓ |
| `candidateManager` | 53 | `val candidateManager = CandidateManager()` — eager ✓ |
| `autoQwertyBuffer` | 61 | `val autoQwertyBuffer = StringBuilder()` ✓ |
| `autoBopomofoBuffer` | 62 | `val autoBopomofoBuffer = mutableListOf<String>()` ✓ |
| `pendingCommitText` | 104 | `var pendingCommitText: String? = null` ✓ |
| `userDict` | 27 | `private val userDict = UserDictionary(context)` ✓ |

雪鑰 `grep` 對應 method 真實性：

| API | 檔案:line | Visibility |
|-----|-----------|-----------|
| `SmartInputManager.persist()` | `smart/SmartInputManager.kt:263` | public ✓ |
| `SmartInputManager.maintenance()` | `smart/SmartInputManager.kt:274` | public ✓ |
| `InputEngine.reset()` | `engine/InputEngine.kt:39`（介面） | public ✓ |
| `EnglishInputEngine.reset()` | `engine/EnglishInputEngine.kt:86` | override ✓ |
| `ChewingInputEngine.reset()` | `engine/ChewingInputEngine.kt:303` | override ✓ |
| `LatinInputEngine.reset()` | `engine/LatinInputEngine.kt:117` | override ✓ |
| `CandidateManager.clear()` | `candidate/CandidateManager.kt:127` | public ✓ |
| `UserDictionary.release()` | `dictionary/UserDictionary.kt:290` | public（S8-3 既有） ✓ |

→ 全部 API 引用 **真實存在且 public**，無 unresolved reference 風險。

### 2.3 Lazy Engine 未觸發驗證

雪鑰 `grep` 確認以下 5 個 lazy engine 在 patch 中**完全未出現**：

```text
33: private val pinyinEngine: InputEngine by lazy { PinyinInputEngine() }
34: private val cangjieEngine: InputEngine by lazy { CangjieInputEngine(speedMode = false, ...) }
35: private val speedCangjieEngine: InputEngine by lazy { CangjieInputEngine(speedMode = true, ...) }
36: private val japaneseEngine: InputEngine by lazy { JapaneseInputEngine(context) }
37: private val koreanEngine: InputEngine by lazy { KoreanInputEngine(context) }
```

→ 鍵律宣稱「不觸發 lazy init」**屬實**，避免 release 路徑誤拉起未使用的 engine。語意正確。

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

- `UP-TO-DATE` 表示鍵律已執行過 compile（宣稱 `BUILD SUCCESSFUL in 3s`），source hash 與已編譯 output 一致 → 目前處於可編譯狀態
- 無新 warning 與本輪 patch 相關

→ 編譯 **通過**。

---

## 4. 語意風險檢查

### 4.1 執行順序合理性

鍵律採用「先 persist 再 cleanup，最後解 listener」的 teardown 慣例：

1. **persist 第一順位** — 系統 kill 前最後一次學詞落地，避免遺失 SmartInput 狀態
2. **engine reset 第二順位** — 清 composing；若先 clear candidate 再 reset，仍正確（彼此無依賴）
3. **candidate clear 第三順位** — 清候選列
4. **AUTO buffer / pendingCommitText 第四順位** — InputRouter 自身狀態收尾
5. **userDict listener 解綁最後** — S8-3 既有，避免 listener 解綁後仍有寫入嘗試

→ 順序 **合理**，無 ordering bug。

### 4.2 冪等性

| Step | 冪等性 | 雪鑰判定 |
|------|--------|---------|
| `smartInput.persist()` | 多次呼叫 = 多次 `apply()`，無錯誤 | ✓ 冪等 |
| `smartInput.maintenance()` | 以 timestamp purge stale，多次呼叫無副作用 | ✓ 冪等 |
| `engine.reset()` | 介面契約：清 composing 到 initial state | ✓ 冪等 |
| `latinEngines.clear()` | Kotlin `MutableMap.clear()` | ✓ 冪等 |
| `candidateManager.clear()` | `CandidateManager.kt:127` 公開 method | ✓ 冪等 |
| `autoQwertyBuffer.setLength(0)` | StringBuilder API | ✓ 冪等 |
| `autoBopomofoBuffer.clear()` | MutableList API | ✓ 冪等 |
| `pendingCommitText = null` | 賦值 | ✓ 冪等 |
| `userDict.release()` | S8-3 設計即冪等 | ✓ 冪等 |

→ `release()` 整體 **可重複呼叫無副作用**。

### 4.3 與 `onFinishInputView` 的 double-persist 評估

- `SmartInputManager.persist()` 在 IME 生命週期中可能於 `onFinishInputView` 已被叫過，再叫一次屬重複 SharedPreferences write
- 由於 `apply()` 是 async commit，且 SharedPreferences 內部會合併 pending writes，雪鑰判定**無使用者可感影響**
- 收益面：覆蓋「`onFinishInputView` 後但 `onDestroy` 前」的學詞 race window，符合 audit §C2 的 owner 補完動機

→ Double-persist **可接受**，屬鍵律有意識的取捨，文件 §5.1 第 3 點已明文。

### 4.4 Thread safety

- `release()` 在 Main thread（`onDestroy()` callback）
- 引用的 mutable state（`autoQwertyBuffer` / `autoBopomofoBuffer` / `pendingCommitText` / `latinEngines`）原本在 Main thread 寫入
- 本 patch 未引入新 coroutine、新 dispatcher、新 thread 切換

→ 無 thread safety 退化風險。

### 4.5 熱路徑無關性

雪鑰確認 diff 僅修改 `release()` 函式體（line 422-469），**未動**：

- `processChar` / `applyChar`
- `commitAndReset` / `commit`
- `switchTo` / `autoDetectAndSwitch`
- `getAutoCombinedCandidates`
- candidate sourceMap 邏輯
- AUTO score 規則（`autoEnglishScore` / `autoBopomofoScore`）

→ 熱路徑 **完全未動**，不影響使用者輸入體驗。

### 4.6 假修 / 漏修檢查

- ✓ **非假修**：6 類釋放動作真實落地，runtime 會生效
- ✓ **非漏修**：本輪裁決明文「最小 owner-map 補完，不擴 engine close API」；鍵律精準對位 `S10-0-CXB-ENGINEERING-AUDIT §C2 / §E` 建議
- ✓ **lazy engine 不 touch**：審慎判斷，避免負收益（在 release 路徑強制 init）
- ✓ **未誤觸禁區**：candidate 規則 / AUTO 規則 / engine 行為 / 跨檔 API 全部未動

---

## 5. 二審結論

### 5.1 雪鑰判定

**通過二審**，可交 `玄樞` 做 gate 終裁。

### 5.2 通過理由

1. **File scope 完全合規**：本輪 .kt 真實落點僅 `InputRouter.kt` line 422-469（`release()` 函式體 + KDoc）。其他工作樹 `.kt` 修改皆為 S10-2 / S10-3 / S10-4 既有，與本輪無關。
2. **實檔落地驗證**：雪鑰 `git diff` + `grep` field/API 雙重交叉驗證，patch 與文件描述完全一致；6 類釋放鏈真實寫入，非紙上宣告。
3. **API 引用合法**：所有引用 field / method 經 `grep` 確認真實存在且為 public（`SmartInputManager.persist/maintenance`、`InputEngine.reset`、`CandidateManager.clear`、`UserDictionary.release`）。
4. **Lazy engine 未觸發**：5 個 by-lazy engine（pinyin/cangjie/speedCangjie/japanese/korean）在 patch 中完全未出現，避免 release 路徑誤拉起未使用 engine。
5. **編譯通過**：`compileDebugKotlin BUILD SUCCESSFUL`（UP-TO-DATE 遞增驗證一致）。
6. **語意正確**：執行順序合理（persist → reset → clear → 解 listener）、整體冪等、Main thread 安全、熱路徑無關、無 ordering bug。
7. **未越界、未假修、未漏修**：精準對位 `S10-0-CXB-ENGINEERING-AUDIT §C2 / §E` 的「owner map + 最小釋放鏈補完」建議，未擴 engine close API、未動 candidate / scoring / auto detect 規則。

### 5.3 Non-blocking 觀察（不構成退件）

| 項目 | 說明 | 處理建議 |
|------|------|---------|
| `SmartInputManager` 仍無正式 `release()` API | 跨檔 scope，本輪明確排除 | 屬 Sprint 11+ 決策題 |
| Lazy engine 無 `close()` API | 需擴 `InputEngine` 介面 | 屬 Sprint 11+ 決策題 |
| `release()` 內 in-memory 欄位（`currentMode` / `lastCommittedWord` 等）未顯式清 | 隨 `InputRouter` GC 自然釋放，無 listener / file handle | 鍵律已於 §3 文件說明，刻意保留，零成本 |
| double-persist with `onFinishInputView` | 冪等 + async commit，無使用者可感影響 | 收益（覆蓋 race window）大於成本 |

---

## 6. 給 `玄樞` 的二審摘要

```text
雪鑰二審通過。建議 READY。

Reason:
1. File scope 親自實檔複核：S10-5 真實 patch 僅 InputRouter.kt 的 release() 函式體 + KDoc（line 422-469），未越界。
2. 工作樹其他 .kt 修改（ImeServiceCallbacks.kt / YukiBoardIMEService.kt / AuthSettingsSection.kt）屬 S10-2 / S10-3 / S10-4 既有，與本輪無關。
3. compileDebugKotlin 通過；所有 patch 引用的 field / method 經 grep 雙重驗證真實存在且 public，無 unresolved reference。
4. Lazy engine（pinyin/cangjie/speedCangjie/japanese/korean）在 patch 中完全未出現，避免 release 路徑誤觸發 init，符合 audit §E 的「不擴 engine close API」決策。
5. 執行順序（persist → engine reset → latinEngines clear → candidate clear → AUTO buffer/pending clear → userDict release）合理且全部冪等；Main thread 安全；熱路徑無關。
6. 精準對位 S10-0-CXB-ENGINEERING-AUDIT §C2 / §E 的「owner map + 最小釋放鏈補完」建議，未動 candidate / scoring / auto detect 規則。

請 玄樞 親自打開 InputRouter.kt 行 422-469 做最終實檔複核。
```

---

## 7. 雪鑰本輪交付資產

| 資產 | 路徑 |
|------|------|
| 本份 review 文件 | `docs/SPRINT10_5_CCA_INPUTROUTER_RELEASE_REVIEW_2026-04-18.md` |

雪鑰本輪 **未產生任何 `.kt` 改動**，符合二審角色定位。

提交給：`玄樞`。

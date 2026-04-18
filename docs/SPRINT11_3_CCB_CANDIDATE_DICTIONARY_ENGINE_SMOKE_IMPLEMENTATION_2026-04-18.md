# Sprint 11-3 — 鍵律 Implementation：Candidate / Dictionary / Engine Smoke Tests

身份：鍵律
任務 ID：`S11-3-CCB-CANDIDATE-DICTIONARY-ENGINE-SMOKE-IMPLEMENTATION`
日期：2026-04-18
依據：`Sprint11-All-Round-Gate.md` `S11-3 Candidate / Dictionary / Engine Smoke Tests`
依據：`docs/SPRINT11_0_CCB_INPUT_ENGINE_CASE_INVENTORY_2026-04-18.md`（S11-0 已核准 inventory）

---

## A. 變更摘要

本輪只新增 7 個 test 檔案，無 production code 改動、無新依賴：

1. `app/src/test/java/com/snowrealm/yukiboard/input/candidate/CandidateManagerTest.kt` — 10 tests
2. `app/src/test/java/com/snowrealm/yukiboard/input/engine/PinyinInputEngineTest.kt` — 12 tests
3. `app/src/test/java/com/snowrealm/yukiboard/input/engine/EnglishInputEngineTest.kt` — 12 tests
4. `app/src/test/java/com/snowrealm/yukiboard/input/engine/KoreanInputEngineTest.kt` — 11 tests
5. `app/src/test/java/com/snowrealm/yukiboard/input/engine/JapaneseInputEngineTest.kt` — 11 tests
6. `app/src/test/java/com/snowrealm/yukiboard/input/engine/CangjieInputEngineTest.kt` — 14 tests
7. `app/src/test/java/com/snowrealm/yukiboard/input/dictionary/EnglishDictionaryTest.kt` — 14 tests

合計 84 個 test method。全部為 pure JVM test（不掛 `@RunWith(RobolectricTestRunner::class)`），靠各 target 的 `Context? = null` fallback 跑無 Android 環境基線。

---

## B. 本輪對準的 gate 目標

`Sprint11-All-Round-Gate.md` 對 `S11-3` 的要求：

- 建立 `Sprint 11` 第一輪 candidate / dictionary / engine smoke tests
- 僅依 `S11-0` 已核准 inventory 落地最小高 CP 值覆蓋
- 本輪 target 固定為 `CandidateManager` / `PinyinInputEngine` / `EnglishInputEngine` / `KoreanInputEngine` / `JapaneseInputEngine` / `CangjieInputEngine` / `EnglishDictionary`

本輪實際對應：

- **CandidateManager**：初始 quiet 狀態、`update()` 首頁與分頁、`nextPage/prevPage` 翻頁、`focusNext/confirmFocused`、`selectAt` 絕對索引、`clear`、`archiveCurrentAsHistory`、`update` 自動歸檔舊 current、`PAGE_SIZE == 9` 常數
- **PinyinInputEngine**：processChar 追加 composing、"ni" 候選包含「你」、"nihao" 候選包含「你好」、processEnter 提交 raw "ni"、processSpace 提交首選候選、processDelete 縮減與清空
- **EnglishInputEngine**：composing 追加、"th" suggest 含 "the"、processSpace 提交 "the "（含尾空格）、processEnter 提交 "hi" 無尾空格、processDelete 縮減與 `ComposingCleared`、`forceCommit`、`getCandidatesForPrefix` 不擾動內部 buffer
- **KoreanInputEngine**：Dubeolsik 單子音 → 孤立 jamo、子+母 → 音節（r+k → 가）、子+母+終 → 帶 jongsung 音節（r+k+s → 간）、複合母音（r+h+k → 과，ㅘ）、processDelete 依序退格、**空狀態 processDelete 回 Idle**（非 ComposingCleared，鍵律 S11-0 inventory 明列）、processSpace 提交 "가 " 含尾空格、processEnter 提交 "가" 無尾空格、非 jamo 字元直接 commit 透通
- **JapaneseInputEngine**：空 engine、`ka→か`、greedy `shi→し` / `tsu→つ`、sokuon `kka→っか`、拗音 `nya→にゃ`、processEnter 提交當前 kana、processDelete 退到空、reset、`forceCommit`
- **CangjieInputEngine**：空 engine、`a→日`、`ab→日月`、大寫先轉小寫、`x` 非合法 key 回 Idle、非字母回 Idle、第 6 碼被拒、processDelete 退格與清空、processEnter 提交 raw letter codes、`processSpace` 在空字典下 fall through 保持 composing（鍵律 S11-0 inventory 明列）、reset
- **EnglishDictionary**：BASIC_WORDS fallback 模式下 isWord 真值/偽值、case-insensitive、`suggest("th")` 命中 "the"、`maxResults` 受限、空/未知 prefix、`frequencyOf` 正/零、`autocorrect("teh")` swap 回 "the"、`fuzzyCorrectWithAdjacency("thd")` 鄰近鍵修正到 "the"、`insert` 後 `isWord` 成立

未擴張到：

- `ChewingInputEngine`（被 `S11-3` 禁區明列）
- `YukiBoardIMEService`、service / ui / sensor / speech / remote
- smart 子系統獨立測（`AdaptiveDictionary` / `PhrasePredictor` / `SentencePredictor` / `TypingHabitTracker` / `PersonalDictionary` / `StyleProfiler` / `SwipeLearnManager` / `CorrectionLearnManager`）
- `app/src/main/**` production code
- Cangjie 在有 Context 時的實際 radical-to-Chinese 字典查詢
- Japanese 在有 Context 時的 kanji conversion 準確性
- English 的 `Context` 裝載時擴充字典的覆蓋
- Pinyin 的 bigram / trigram predictor 品質

---

## C. 驗證

執行：

```text
./gradlew :app:testDebugUnitTest \
  --tests "com.snowrealm.yukiboard.input.candidate.*" \
  --tests "com.snowrealm.yukiboard.input.engine.*" \
  --tests "com.snowrealm.yukiboard.input.dictionary.*"
```

結果：

```text
BUILD SUCCESSFUL in 31s
```

逐檔 test result（來自 `app/build/test-results/testDebugUnitTest/`）：

| Test class | tests | skipped | failures | errors |
|---|---|---|---|---|
| `CandidateManagerTest` | 10 | 0 | 0 | 0 |
| `PinyinInputEngineTest` | 12 | 0 | 0 | 0 |
| `EnglishInputEngineTest` | 12 | 0 | 0 | 0 |
| `KoreanInputEngineTest` | 11 | 0 | 0 | 0 |
| `JapaneseInputEngineTest` | 11 | 0 | 0 | 0 |
| `CangjieInputEngineTest` | 14 | 0 | 0 | 0 |
| `EnglishDictionaryTest` | 14 | 0 | 0 | 0 |
| **合計** | **84** | **0** | **0** | **0** |

代表：

- 本輪新增的 7 個 test class 皆可編譯
- 7 個指定 target 的 smoke tests 皆實跑通過
- 不需 Robolectric、不需 mockk、不需 coroutines-test

---

## D. File scope 守規

符合 gate 授權範圍：

- `app/src/test/java/com/snowrealm/yukiboard/input/candidate/CandidateManagerTest.kt` ✓
- `app/src/test/java/com/snowrealm/yukiboard/input/engine/{Pinyin,English,Korean,Japanese,Cangjie}InputEngineTest.kt` ✓
- `app/src/test/java/com/snowrealm/yukiboard/input/dictionary/EnglishDictionaryTest.kt` ✓

未碰禁區：

- `app/src/main/**` production code ✓（零動）
- `app/build.gradle`、其他 `build.gradle*` ✓（零動）
- `ChewingInputEngine` ✓
- `YukiBoardIMEService` ✓
- `service/`、`ui/`、`sensor/`、`speech/`、`remote/` ✓
- smart 子系統獨立測（`AdaptiveDictionary`、`PhrasePredictor`、`SentencePredictor`、`TypingHabitTracker`、`PersonalDictionary`、`StyleProfiler`、`SwipeLearnManager`、`CorrectionLearnManager`）✓
- 新增 `mockk` / `mockito` / `coroutines-test` 依賴 ✓（零新增）
- `S11-1` 的 `InputRouterTest`、`S11-2` 的 `SmartInputManagerTest`、`S11-4` / `S11-5` 未開 round 的檔案 ✓（零動）

---

## E. 風險

### 本輪已控制

- 只落在 test 檔，零動 production code
- 全部走 no-Context fallback，不需 Robolectric，避免 startup cost 與平台依賴
- 測試斷言對準 API 行為基線（composing / commit / delete / reset 狀態機），不碰字典內容品質
- 所有斷言的 kana / jamo / radical / 字形皆對照 production code 的常數表（ROMAJI_TO_KANA、CangjieDict、Dubeolsik mapping、BASIC_WORDS）逐一核對，不靠推測

### 非 blocking observation

- **Cangjie / Japanese 字典查詢未覆蓋**：`CangjieDict.buildDict()` 在 no-Context 下回 emptyMap，`JapaneseInputEngine.loadKanjiDictionary(null)` 亦為 emptyMap；本輪 smoke tests 對此狀態固化為 expected behaviour（`processSpaceWithEmptyDictKeepsComposing`、japanese tests 未碰 space/convert 路徑）。若後續 round 要覆蓋實際字典命中，需有 Context 輸入，屬 `S11-4` 以後的範疇。
- **EnglishDictionary assets-backed 模式未覆蓋**：本輪只測 BASIC_WORDS fallback（~120 words）。asset-backed dictionary（`dict_top3000.txt`）需 Context，本輪刻意擱置，文件中已註明 out of scope for S11-3。
- **PinyinInputEngine phrase predictor 品質未覆蓋**：僅驗證 "nihao" 能命中 PHRASE_DICT、"ni" 能命中 PINYIN_DICT；未驗證 greedy segmentation 邊界、未驗證 predictor 的排序品質。此屬於 integration 層，不擴入本輪 smoke scope。
- **Korean compound jongsung 未覆蓋**：本輪只覆蓋 ㄱ+ㅏ+ㄴ 的單 jongsung 案例，未擴到複合 jongsung（例如 ㄳ、ㅄ）；此屬 S11-4 以後可補強的品質層。

這些不構成本輪退件理由，因為 `Sprint11-All-Round-Gate.md` 對 `S11-3` 明定「本輪只做 baseline / 最小高 CP 值覆蓋」。

---

## F. 建議玄樞判定

```text
READY

Reason:
1. File scope 全部落在 S11-3 授權的 input/{candidate,engine,dictionary}/ 三個 test 目錄，未碰任何 S11-3 禁區（ChewingInputEngine、YukiBoardIMEService、service/ui/sensor/speech/remote、smart 子系統獨立測、app/src/main/**）。
2. 7 個預設 target 全部落地最小 smoke tests，合計 84 個 test 全綠，零 skipped / failures / errors。
3. 依 S11-0 已核准 inventory 的 API 行為逐項對齊，未擴成整合測試，維持 baseline 層性質。
4. 零 production code 改動、零新 dependency，符合 S11 Sprint 「先有最低可跑 baseline」的節奏。

提交鏈：
- 鍵律 → 衡鑑（技術二審：檢查 file scope / 未越界 Chewing / service / smart 子系統獨測 / baseline 層未失控成整合測試）
- 衡鑑 → 玄樞
```

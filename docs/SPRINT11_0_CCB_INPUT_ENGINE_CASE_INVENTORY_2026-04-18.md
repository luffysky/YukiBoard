# SPRINT11-0-CCB-INPUT-ENGINE-CASE-INVENTORY

身份：鍵律（SnowRealm 核心輸入與 IME 行為工程師）

日期：2026-04-18

任務 ID：

- `S11-0-CCB-INPUT-ENGINE-CASE-INVENTORY`

提交鏈下一棒：與 `衡鑑`、`雪鑰` 三份 S11-0 audit 同時收齊 → `玄樞` gate 裁決是否開 `S11-1`。

---

## 0. 本輪邊界遵守

- 依 `Sprint11-All-Round-Gate.md` §3 S11-0：
  - 本輪限制：
    1. 不改 `app/src/main/**`
    2. 不偷開 `S11-1` 到 `S11-5`
    3. 只做 scope / harness / strategy / 不測區域說明
  - 交付物：test target 清單、fixture / fake strategy、不測區域與原因
- 實際產出：
  - 本 audit 文件（單檔，`docs/` 之下，不觸碰 `main/` 原始碼）
- 未擴張 scope：不盤 `ui/`、`service/`、`sensor/`、`remote/`、`speech/`、`theme/`、`animation/`、`game/` 線。這些不是 `S11-0-CCB` 的授權題。

---

## 1. 現況盤點（測試骨幹已存在）

### 1.1 測試基礎建設

`app/build.gradle` 測試依賴（line 225-229）：

```gradle
testImplementation 'junit:junit:4.13.2'
testImplementation 'androidx.test:core:1.5.0'
testImplementation 'org.robolectric:robolectric:4.13'
androidTestImplementation 'androidx.test.ext:junit:1.1.5'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
```

觀察：

- **Robolectric 4.13 已存在**：任何需要 `Context` / `SharedPreferences` 的類都可在 host JVM unit test 中用 `ApplicationProvider.getApplicationContext<Context>()` 直接建構，不必上 Android Emulator。
- **沒有 `mockk` / `mockito-kotlin`**：本 sprint 不該擅自引入新 mocking library；採「真物 + thin fake」策略。
- **沒有 `kotlinx-coroutines-test`**：若 `S11-1` / `S11-2` 需要對 coroutine scheduler 做精細控制，得先由 `玄樞` 決定是否開門引入；在 `S11-0` 本輪不預設擴充。

### 1.2 已存在的 seed tests（已 pass）

`app/src/test/java/com/snowrealm/yukiboard/` 底下已有兩個檔，各 2 個 test，皆 Robolectric-backed：

- `input/router/InputRouterTest.kt`
  - `startsInEnglishModeWithEmptyCandidates()` — 建構即檢查 `InputMode.ENGLISH` / `candidates.isEmpty()` / `composingText == ""`
  - `enabledModesContainsCoreModesByDefault()` — default `enabledModes` 含 ENGLISH/BOPOMOFO/JAPANESE/KOREAN
- `input/smart/SmartInputManagerTest.kt`
  - `startsWithFeatureFlagsEnabled()` — 預設 feature flag 全開
  - `sentencePredictionsAreEmptyWhenPhrasePredictionDisabled()` — flag off 後預測為空

涵意：

- **`S11-1` / `S11-2` 不是「從零開題」，是擴充既有 seed 檔**。這對本輪 inventory 判斷很重要 —— harness 已被證實能動，後續只是加測案。
- Seed 檔已經示範了「`ApplicationProvider` 直取 Context + 直建構真物」的風格，後續題目應沿用，不換風格。

---

## 2. Context 依賴矩陣（決定 harness 選擇）

本矩陣是 S11-1 / S11-2 / S11-3 的 harness 策略基礎。`Robolectric` 僅在「Context 為 required」時才必要。

| 類 | 構造 Context | Host JVM 可測？ | 建議 harness |
|---|---|---|---|
| `input.candidate.CandidateManager` | 無 | ✅ 純 Kotlin | 直接 `@Test`，不需 Robolectric |
| `input.engine.InputEngine`（interface） | n/a | ✅ | 純 Kotlin |
| `input.engine.PinyinInputEngine` | 無 | ✅ 純 Kotlin | 純 `@Test` |
| `input.engine.EnglishInputEngine` | `Context? = null` | ✅ 兩種模式 | 可純 JVM（走 BASIC_WORDS fallback）或 Robolectric（走 assets 大詞庫） |
| `input.engine.KoreanInputEngine` | `Context? = null` | ✅ 核心 Jamo 邏輯 | 純 `@Test`；若想測 assets 漢字候選再上 Robolectric |
| `input.engine.JapaneseInputEngine` | `Context? = null` | ✅ 核心ローマ字邏輯 | 純 `@Test`；候補辞書那段再上 Robolectric |
| `input.engine.CangjieInputEngine` | `Context? = null` | ✅ 字根對應邏輯 | 純 `@Test`；字典部分再上 Robolectric |
| `input.engine.LatinInputEngine` | `Context` 必要 | ⚠️ 只能 Robolectric | `@RunWith(RobolectricTestRunner)`，且需要 `LanguagePackManager` 已載入或 fake lang-pack |
| `input.engine.ChewingInputEngine` | `Context` + JNI | ❌ | 不測（見 §5） |
| `input.dictionary.EnglishDictionary` | `Context? = null` | ✅ 兩種模式 | BASIC_WORDS 模式純 JVM；assets 模式 Robolectric |
| `input.dictionary.UserDictionary` | `Context` 必要 | ✅（Robolectric） | `@RunWith(RobolectricTestRunner)` |
| `input.router.InputRouter` | `Context` 必要 | ✅（已證明） | 延續 `InputRouterTest` 風格 |
| `input.smart.SmartInputManager` | `Context` 必要 | ✅（已證明） | 延續 `SmartInputManagerTest` 風格 |
| `input.smart.SmartCorrectionEngine` | 非 Context | ✅ 純 Kotlin | 純 `@Test`（Sprint 11 不在目標範圍，列出僅供下游 round 參考） |
| `input.smart.AdaptiveDictionary` / `PhrasePredictor` / `SentencePredictor` / `TypingHabitTracker` / `PersonalDictionary` / `StyleProfiler` / `SwipeLearnManager` / `CorrectionLearnManager` | `Context` 必要 | ✅（Robolectric） | 透過 `SmartInputManager` 集成測試覆蓋，不各自獨測 |

關鍵判斷：

- **Robolectric 不是必需品**；大半引擎的核心組字邏輯可以純 JVM 測。
- **Context-optional 的引擎**（English / Korean / Japanese / Cangjie）是 Sprint 11 最高 CP 值的覆蓋點 —— 不依賴 assets / Robolectric、啟動快、斷言穩。
- **Context-required 的類**（`InputRouter` / `SmartInputManager` / `UserDictionary` / `LatinInputEngine`）仍 testable，但每個 test class 會承擔 Robolectric 的啟動開銷。Sprint 11 要控制數量、一類一 file、聚在同層。

---

## 3. Test Target 清單（按優先級排序）

所有 target 皆限於 `input/engine/`、`input/candidate/`、`input/dictionary/`、`input/router/`、`input/smart/` 五個 package，與 Sprint 11 主題（`InputRouterTest` / `SmartInputManagerTest` / candidate / dictionary / engine 最小測試）對齊。

### Tier 1 — 純 Kotlin smoke，Sprint 11 必備基線（最優先）

這一層沒有 Robolectric 成本、CI 最快、回歸信號最清楚。建議 `S11-3` 起手先全量落地。

| # | Target | 建議 test file | 最小 case 建議 |
|---|---|---|---|
| T1-A | `CandidateManager` | `input/candidate/CandidateManagerTest.kt` | update → currentPage 前 9 個；`nextPage` / `prevPage` 邊界；`focusNext` 循環；`selectAt` 範圍；`clear` 後狀態歸零；`archiveCurrentAsHistory` 往 history 推 |
| T1-B | `PinyinInputEngine` | `input/engine/PinyinInputEngineTest.kt` | 單音 `ni` → composing `ni`；`processSpace` 出候選；`processDelete` 退回；連續兩音節切分；`forceCommit` 清空 |
| T1-C | `EnglishInputEngine`（無 Context 模式） | `input/engine/EnglishInputEngineTest.kt` | BASIC_WORDS 模式下：`hel` → 有 `hello` 候選；`processSpace` commit；`processDelete` 退字；`reset` 清 buffer |
| T1-D | `KoreanInputEngine`（核心 Jamo） | `input/engine/KoreanInputEngineTest.kt` | 子音+母音組字（ㄱ+ㅏ → 가）；加尾音（→ 간）；尾音再遇母音拆分；compound vowel（ㅘ）；`processDelete` 逐 Jamo 退 |
| T1-E | `JapaneseInputEngine`（核心ローマ字） | `input/engine/JapaneseInputEngineTest.kt` | `ka` → か；`kk` → っ+か（促音）；`nn` → ん；ひらがな⇔カタカナ切替；`processDelete` |
| T1-F | `CangjieInputEngine`（字根對應） | `input/engine/CangjieInputEngineTest.kt` | speed mode 僅首尾；full mode 5 碼；`processDelete` 退字根；`reset` |

### Tier 2 — Robolectric-backed smoke，Sprint 11 主題題

對應 gate §2「正式範圍」第 1 / 2 / 3 項，延伸既有 seed 檔。

| # | Target | 擴充 test file | 最小 case 建議 |
|---|---|---|---|
| T2-A | `InputRouter` 自動切換 | `input/router/InputRouterTest.kt`（擴充） | 注音字元觸發 BOPOMOFO 軌；字母觸發 ENGLISH 軌；切軌前 `forceCommit` 有發；空 buffer 切軌不炸；`reset()` 後兩軌皆乾淨 |
| T2-B | `InputRouter.release()`（S10-5 已擴充） | 同上 | release 後 `composingText` 空、candidates 空、userDict 不再回應（或 stub observation）；重入 release 不 crash |
| T2-C | `SmartInputManager` 核心路徑 | `input/smart/SmartInputManagerTest.kt`（擴充） | `onKeystroke` 不炸；`onWordCommitted` 進 adaptive dict；`updateSentenceContext` + `onSentenceCommitted` 不炸；`persist()` 不炸；`maintenance()` 不炸 |
| T2-D | `EnglishDictionary`（assets 模式） | `input/dictionary/EnglishDictionaryTest.kt` | 基本 Trie prefix；最常用詞優先；自動更正（1-edit-distance 回饋）；不存在詞不 crash |

### Tier 3 — 選配，視 S11-3 時間決定

屬於「有就好」，不是 Sprint 11 基線。

- `UserDictionary` 新增/學習/持久化（需 Robolectric + SharedPreferences file cleanup）
- `SmartCorrectionEngine` 純 Kotlin 三階段糾錯 single-shot test（非 Sprint 11 明確主題）
- `LatinInputEngine` 需 `LanguagePackManager` fake；高成本低收益，可推到 Sprint 12 以後

### Tier 4 — 明確不排入本 sprint（建議後續 round 處理）

| Target | 原因 |
|---|---|
| `ChewingInputEngine` | 依賴 `libchewing` JNI，host JVM 無 native lib。要測得整 NDK toolchain / emulator，不是 Sprint 11 範圍。 |
| `ImeSessionCoordinator` / `ImeFraudModelInit` / `ImeImageCommitter` / 其他 `service/ime*` | Sprint 11 主題是 `InputRouter` + `SmartInputManager` + 引擎，不是 IME service 層。後者動 Android framework 太深，屬 S12+。 |
| `YukiBoardIMEService` | 需要完整 `InputMethodService` lifecycle + `InputConnection`，Robolectric 支援薄，回饋成本比小，留到未來整合測試層。 |
| 各 smart 子系統 `AdaptiveDictionary` / `PhrasePredictor` / `SentencePredictor` / `TypingHabitTracker` / `PersonalDictionary` / `StyleProfiler` / `SwipeLearnManager` / `CorrectionLearnManager` | 共 8 個子系統分散測會爆量。Sprint 11 透過 `SmartInputManager` 入口做整合煙霧測即可；等待某子系統有明確回歸或 contract 穩定後再獨立測。 |
| 所有 `ui/`、`sensor/`、`speech/`、`remote/`、`theme/`、`animation/`、`game/` | 非 `S11-0-CCB` 授權域；本輪明文不盤。 |

---

## 4. Fixture / Fake Strategy

### 4.1 建構真物，不 mock

- 沿用 seed 檔風格：`InputRouter(context)` / `SmartInputManager(context)` 直接以 `ApplicationProvider.getApplicationContext()` 起。
- **不要引入 `mockk`**：本 sprint 沒這依賴，引入會擴張 build graph；多數引擎核心邏輯是純 Kotlin，沒有要 mock 的外界，不需 mocking framework。
- 需要 fake 的場合（例：候選回調、Router 外部 callback）→ 手寫最小 `object : Xxx { ... }` 當 fake，不走 mocking library。

### 4.2 Context-optional 引擎：優先「無 Context 模式」

- `EnglishInputEngine` / `EnglishDictionary` 兩類在 `Context == null` 時走 `BASIC_WORDS` fallback。Tier 1 case 全部用 null Context 跑即可。
- `Korean` / `Japanese` / `Cangjie` 同理：用 null Context 測核心組字邏輯；大詞庫/候選字典那塊留給 Tier 3（或不測）。
- 優點：啟動無 Robolectric 成本、跑得快、與 Gradle `:app:testDebugUnitTest` 穩定。

### 4.3 Robolectric 的使用紀律

只在以下情形用 `@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])`：

1. 類構造簽章只收 `Context`（非 nullable），沒有 fallback 路徑。
2. 類透過 `SharedPreferences` / `AssetManager` / `Resources` 做持久化或載入資源，且該行為是測試目標本身。
3. 與 seed 檔已在使用的類同層。

不符合上述三條之一者 → 用純 JVM `@Test`，不戴 `@RunWith`。

### 4.4 SharedPreferences 清理

凡是跑到 SharedPreferences 的 test（`UserDictionary` / `SmartInputManager` 持久化路徑等），每個 test 結束應在 `@After` 或 test body 末端做 preferences `clear()`，避免 test 間污染。

具體策略由 `S11-1` / `S11-2` 執行者（`衡鑑`）決定 per-class 命名方式；Sprint 11 不強制抽共用 helper。

### 4.5 Coroutine 測試

本輪不引入 `kotlinx-coroutines-test`。理由：

- `InputRouter` / `SmartInputManager` / 各引擎的 public API 是同步的；回歸 suite 走 sync 路徑即可覆蓋。
- `SmartInputManager` 內部若有 background maintenance 走 IO scheduler，Sprint 11 先以「只斷言 persist/maintenance 不炸」為底線，不驗 scheduler 細節。
- 若 `S11-1` / `S11-2` 執行時確實碰到需要 scheduler 控制的 case，回報 `玄樞` 走單獨議題，不在 `S11-0` audit 中預先開門。

### 4.6 Assertion 風格

沿用 seed 檔 `org.junit.Assert.assertEquals / assertTrue / assertFalse`。不引入 `Truth` / `Kluent` / `AssertK`。

---

## 5. 不測區域（明確理由）

| 區域 | 原因 |
|---|---|
| `ChewingInputEngine` | `libchewing` JNI 依賴，host JVM 無 native；測需 emulator / device，超出 unit test 層與 Sprint 11 主題。 |
| `YukiBoardIMEService` 行為層 | 需完整 `InputMethodService` + `InputConnection` lifecycle，Robolectric 支援不完整，回饋成本大於信號。留 integration layer。 |
| 各 `service/ime*` 子協調器（`ImeSessionCoordinator` 等） | Sprint 11 主題是 `input/` 線；service 層動邏輯是 Sprint 12+ 的題。`S10-6` / `S10-7` 的 silent catch 治理後再評估。 |
| `ui/`、`sensor/`、`speech/`、`remote/`、`theme/`、`animation/`、`game/` | 非本輪題。`S11-0` 明文：`input / engine / candidate` 線。 |
| Network / Firebase / auth / sync 路徑 | 與 Sprint 11 的 harness baseline 無關，且屬 `雪鑰` 的設定/TTS 線。 |
| `KeyboardGLSurfaceView` frame loop 細節 | 屬 `S11-5` GL debt 範圍，本輪 audit 不盤。 |
| `GoogleCloudTtsEngine` `Thread.sleep` | 屬 `雪鑰` `S11-0-CCA-SETTINGS-TTS-CONSUME-NOTE` 範圍，不在我這份。 |
| 子 smart 子系統各自獨立測 | 共 8 個 Context-dep 子系統。獨立測每個會讓 Sprint 11 test count 膨脹 3-5 倍；`SmartInputManager` 入口的整合 smoke 已能抓到構造層崩潰與 persist 路徑，CP 值更高。等某子系統出現可重現回歸後再獨立拉題。 |

---

## 6. Boundary：本 inventory ↔ S11-1 / S11-2 / S11-3 的繼承關係

這是最容易越界的地方，明文劃：

- **S11-1 InputRouterTest**（`衡鑑` 主責）：擴充既有 `InputRouterTest.kt`，對應本 inventory §3 T2-A / T2-B。本輪 inventory 不預寫 case 清單細節以外的任何實作建議。
- **S11-2 SmartInputManagerTest**（`衡鑑` 主責）：擴充既有 `SmartInputManagerTest.kt`，對應 §3 T2-C。同上。
- **S11-3 Candidate / Dictionary / Engine Smoke**（`鍵律` 主責、`衡鑑` 技術審核）：
  - Tier 1 全部 target（§3 T1-A ～ T1-F）
  - Tier 2 T2-D（`EnglishDictionary` assets 模式）
  - Tier 3 視時間決定
  - 不承諾做 Tier 4 / 不測區域的任何 target
- **S11-4 Silent Catch First Pass**（`衡鑑` 主責）：與本 inventory 無直接關係，不重複規劃。
- **S11-5 GL / TTS Debt Fix**：同上，與本 inventory 無關。

本輪 `S11-0-CCB` **不自排 S11-1 / S11-2 / S11-3 的順序**，也**不宣告任何後續 round 為 READY**。順序與解鎖由 `玄樞` 依 `Sprint11-All-Round-Gate.md §4` 規則決定。

---

## 7. A–E 回交格式

### A. 變更摘要

- 新增單檔：`docs/SPRINT11_0_CCB_INPUT_ENGINE_CASE_INVENTORY_2026-04-18.md`
- 無 `app/src/main/**` 變更
- 無 gate / `RULE` / `daily_works` 變更

### B. 風險

1. **低風險**：audit-only，不動原始碼、不動測試。
2. **繼承誤讀風險**：若下一個執行 round（`衡鑑` 或我）把 Tier 3 / Tier 4 誤當成承諾覆蓋範圍，Sprint 11 會爆量。本文件 §3 / §6 已明文劃開，再請 `玄樞` 在開後續 round 時比對本清單。
3. **Context-optional 誤用**：`Korean` / `Japanese` / `Cangjie` / `English` 引擎在 null Context 下走 fallback，若未來 assets 新增詞庫依賴改造成 required，本 inventory 需同步更新。這點屬 `S11-3` 執行期的 hygiene，不在本輪解決。

### C. 下一步建議（不自排順序）

- 本 audit 與 `衡鑑` 的 `S11-0-CXB-TEST-SCOPE-HARNESS-AUDIT`、`雪鑰` 的 `S11-0-CCA-SETTINGS-TTS-CONSUME-NOTE` 三份同時收齊後，交 `玄樞` 做 gate 裁決。
- 是否開 `S11-1` 由 `玄樞` 決定；我不自排順序。
- 若 `玄樞` 最終指定我接 `S11-3`，我會以本 inventory §3 Tier 1 + Tier 2 T2-D 為起手，單 round 內不越界 Tier 3 / Tier 4。

### D. 架構決策

- 不引入任何新測試依賴（`mockk` / `Truth` / `coroutines-test` 皆不加）。
- Harness 選擇採「純 Kotlin 優先、Robolectric 只在必要時」的二階策略。
- 不為 Sprint 11 共用 helper（例如 prefs 清理 util）預先設計；若 `衡鑑` 執行 `S11-1` / `S11-2` 時出現重複需求，再單獨提出議題。這符合 CLAUDE.md「三行重複優於過早抽象」的規則。

### E. `玄樞` 審核需求

建議判定：

```text
READY (audit-only)

Reason:
1. 單檔 audit 文件，完全落在 docs/ 層，未觸碰 app/src/main/** 與任何 gate / RULE / daily_works。
2. 交付物三項（test target 清單、fixture / fake strategy、不測區域與原因）皆對位 Sprint11-All-Round-Gate.md §3 S11-0-CCB 的要求。
3. 未自排 S11-1 / S11-2 / S11-3 順序，維持 §4 解鎖規則由 玄樞 掌握。
4. 不承諾 Tier 4 / 不測區域的任何 target，不預留擴張。
5. 未引入新依賴，不擴張 build graph。
```

請 `衡鑑` 與 `雪鑰` 各自完成本輪任務後三份一起交 `玄樞`，不由 `鍵律` 代理提交。

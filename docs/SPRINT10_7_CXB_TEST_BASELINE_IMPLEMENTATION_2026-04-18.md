# Sprint 10-7 — 衡鑑 Implementation：測試骨架前置

身份：衡鑑
任務 ID：`S10-7-CXB-TEST-BASELINE-IMPLEMENTATION`
日期：2026-04-18
依據：`Sprint10-All-Round-Gate.md` `Round S10-7`

---

## A. 變更摘要

本輪只修改：

- `app/build.gradle`
- `app/src/test/java/com/snowrealm/yukiboard/input/router/InputRouterTest.kt`
- `app/src/test/java/com/snowrealm/yukiboard/input/smart/SmartInputManagerTest.kt`

本輪達成內容：

1. 建立 `app/src/test` baseline
2. 補上 `InputRouterTest` 骨架
3. 補上 `SmartInputManagerTest` 骨架
4. 讓 unit test task 可以實際執行，而不是只停留在空目錄

---

## B. 實際改動

### 1. `app/build.gradle`

新增：

- `testOptions.unitTests.includeAndroidResources = true`
- `testImplementation 'androidx.test:core:1.5.0'`
- `testImplementation 'org.robolectric:robolectric:4.13'`

目的：

- 讓 `app/src/test` 的 JVM unit test 可以透過 Robolectric 取得 Android `Context`
- 避免 `InputRouter` / `SmartInputManager` 這種 constructor 依賴 `Context` 的 class 無法建立 baseline test

### 2. `InputRouterTest.kt`

新增兩個最小測試：

- `startsInEnglishModeWithEmptyCandidates()`
- `enabledModesContainsCoreModesByDefault()`

覆蓋內容：

- `InputRouter` 可被建立
- 預設 `inputMode = ENGLISH`
- 初始 `candidates` 為空
- 預設 `enabledModes` 含核心 mode

### 3. `SmartInputManagerTest.kt`

新增兩個最小測試：

- `startsWithFeatureFlagsEnabled()`
- `sentencePredictionsAreEmptyWhenPhrasePredictionDisabled()`

覆蓋內容：

- `SmartInputManager` 可被建立
- 4 個核心 feature flag 預設為 enabled
- 關閉 `phrasePredictEnabled` 後，`getSentencePredictions()` 會回空集合

---

## C. File scope 守規

符合 gate 授權：

- 允許修改：
  - `app/src/test/**` ✓
  - `app/build.gradle` ✓

未碰禁區：

- `app/src/main/**` ✓
- `app/src/androidTest/**` ✓
- `Sprint 10` gate / `RULE` / `daily_works_0418.md` ✓

---

## D. 驗證

先在 sandbox 內執行：

```text
./gradlew :app:testDebugUnitTest
```

結果卡在 Android SDK `android.jar` 權限，不是測試碼本身錯誤。

之後以可讀取本機 SDK 的環境重新執行：

```text
./gradlew :app:testDebugUnitTest
```

結果：

```text
BUILD SUCCESSFUL
```

重點：

- `:app:compileDebugUnitTestKotlin` 成功
- `:app:testDebugUnitTest` 成功
- baseline test 已不只是「可編譯骨架」，而是可實際跑的最小單元測試入口

---

## E. 風險

### 本輪控制範圍內

- 只建立 baseline，不承諾高覆蓋率
- 未引入 `androidTest`
- 未碰 production code

### 非 blocking observation

- 目前測試仍偏 smoke baseline，不足以覆蓋 `InputRouter` / `SmartInputManager` 的複雜語意行為
- 後續若要深入測：
  - `InputRouter` 的 AUTO / candidate / commit path
  - `SmartInputManager` 的 snapshot / predictor / learning 行為
  仍需要 Sprint 11+ 再擴充

---

## F. 請 玄樞 判定

建議判定：

```text
READY

Reason:
1. 本輪只落在 gate 授權的 test baseline 範圍，file scope 正確。
2. `app/src/test` 已建立，`InputRouterTest` 與 `SmartInputManagerTest` 骨架已落地並可編譯執行。
3. :app:testDebugUnitTest 已實際跑通，代表 Sprint 10 最後一門 baseline 任務已完成。
```


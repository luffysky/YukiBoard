# Sprint 11-2 — 衡鑑 Implementation：SmartInputManagerTest baseline

身份：衡鑑
任務 ID：`S11-2-CXB-SMARTINPUTMANAGERTEST-BASELINE-IMPLEMENTATION`
日期：2026-04-18
依據：`Sprint11-All-Round-Gate.md` `S11-2 SmartInputManagerTest`

---

## A. 變更摘要

本輪只修改：

- `app/src/test/java/com/snowrealm/yukiboard/input/smart/SmartInputManagerTest.kt`

本輪補上的 baseline：

1. `phrasePredictionApisReturnEmptyWhenFeatureDisabled()`
2. `resetContextDoesNotThrow()`
3. `applyCloudSnapshotWithMinimalPayloadDoesNotThrow()`
4. `persistAndMaintenanceDoNotThrow()`

加上既有兩個 seed tests，`SmartInputManagerTest` 目前共 6 個 test。

---

## B. 本輪對準的 gate 目標

`Sprint11-All-Round-Gate.md` 對 `S11-2` 的要求是：

- 在既有 `SmartInputManagerTest.kt` 上擴充最小 baseline
- 先做 feature gate / pure API return / snapshot-persist-maintenance 不崩潰基線
- 不擴成 predictor 品質、style boost 分數、長期學習資料演化驗證

本輪實際對應：

- feature gate：`phrasePredictEnabled = false` 時的 API return
- pure API return：`getPhrasePredictionsWithPrefix()` / `predictWithContext()`
- snapshot：`applyCloudSnapshot(minimal payload)`
- lifecycle-like baseline：`resetContext()` / `persist()` / `maintenance()`

未擴張到：

- predictor 品質驗證
- style boost 分數驗證
- 長期學習資料演化驗證
- production code 改造

---

## C. 驗證

執行：

```text
./gradlew :app:testDebugUnitTest --tests "com.snowrealm.yukiboard.input.smart.SmartInputManagerTest"
```

結果：

```text
BUILD SUCCESSFUL
```

代表：

- 本輪新增的 `SmartInputManagerTest` baseline 可編譯
- 指定 test class 可實際跑通

---

## D. File scope 守規

符合 gate 授權：

- 只修改 `app/src/test/java/com/snowrealm/yukiboard/input/smart/SmartInputManagerTest.kt` ✓

未碰禁區：

- `app/build.gradle` ✓
- `app/src/main/**` ✓
- `S11-3` 到 `S11-5` 任何檔案或實作 ✓
- 新增 `mockk` / `mockito` / `coroutines-test` ✓

---

## E. 風險

### 本輪已控制

- 只做 baseline，不碰 production code
- 測試斷言偏 API 行為與不崩潰基線，不碰資料品質

### 非 blocking observation

- `applyCloudSnapshot()` 目前只驗到 minimal payload 不 crash，未驗證 schema 每個欄位的正確導入
- `persist()` / `maintenance()` 目前只驗到可呼叫，不驗證寫入內容品質

這些不構成本輪退件理由，因為 gate 已明定本輪只做 baseline

---

## F. 建議玄樞判定

```text
READY

Reason:
1. 本輪只落在 gate 授權的單一 test 檔案，file scope 正確。
2. SmartInputManagerTest 已補上 feature gate / pure API return / snapshot-persist-maintenance 三類最小 baseline，符合本輪目標。
3. 指定 test class 已實際跑通，可作為開 `S11-3` 前的最低可跑標準之一。
```


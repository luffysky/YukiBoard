# Sprint 11-1 — 衡鑑 Implementation：InputRouterTest baseline

身份：衡鑑
任務 ID：`S11-1-CXB-INPUTROUTERTEST-BASELINE-IMPLEMENTATION`
日期：2026-04-18
依據：`Sprint11-All-Round-Gate.md` `S11-1 InputRouterTest`

---

## A. 變更摘要

本輪只修改：

- `app/src/test/java/com/snowrealm/yukiboard/input/router/InputRouterTest.kt`

本輪補上的 baseline：

1. `switchToDifferentModeUpdatesInputModeAndClearsAutoBuffers()`
2. `switchToSameModeReturnsIdleWithoutChangingMode()`
3. `resetClearsComposingAndAutoBuffers()`
4. `releaseClearsAutoBuffersWithoutThrowing()`

加上既有兩個 seed tests，`InputRouterTest` 目前共 6 個 test。

---

## B. 本輪對準的 gate 目標

`Sprint11-All-Round-Gate.md` 對 `S11-1` 的要求是：

- 在既有 `InputRouterTest.kt` 上擴充最小 baseline
- 先做 mode switch / buffer / release 基線
- 不擴成 candidate 排序細節或 AUTO 全 edge case

本輪實際對應：

- mode switch：`switchTo(...)`
- buffer：`autoQwertyBuffer` / `autoBopomofoBuffer` / `reset()`
- release：`release()`

未擴張到：

- candidate 排序細節
- AUTO 所有 edge case
- engine dictionary 正確性
- service / input connection 整合

---

## C. 驗證

執行：

```text
./gradlew :app:testDebugUnitTest --tests "com.snowrealm.yukiboard.input.router.InputRouterTest"
```

結果：

```text
BUILD SUCCESSFUL
```

代表：

- 本輪新增的 `InputRouterTest` baseline 可編譯
- 指定 test class 可實際跑通

---

## D. File scope 守規

符合 gate 授權：

- 只修改 `app/src/test/java/com/snowrealm/yukiboard/input/router/InputRouterTest.kt` ✓

未碰禁區：

- `app/build.gradle` ✓
- `app/src/main/**` ✓
- `S11-2` 到 `S11-5` 任何檔案或實作 ✓
- 新增 `mockk` / `mockito` / `coroutines-test` ✓

---

## E. 風險

### 本輪已控制

- 只做 baseline，不碰 production code
- 測試斷言偏狀態基線，不碰高脆弱排序細節

### 非 blocking observation

- `release()` 的驗證目前只到「可呼叫且清掉 AUTO buffers」，還沒深入驗證所有 owner side-effect
- `AUTO` mode 的複雜規則、candidate 內容與 dictionary correctness 仍留給後續 round

這些不構成本輪退件理由，因為 gate 已明定本輪只做 baseline

---

## F. 建議玄樞判定

```text
READY

Reason:
1. 本輪只落在 gate 授權的單一 test 檔案，file scope 正確。
2. InputRouterTest 已補上 mode switch / buffer / release 三類最小 baseline，符合本輪目標。
3. 指定 test class 已實際跑通，可作為 S11-2 前的最低可跑標準之一。
```


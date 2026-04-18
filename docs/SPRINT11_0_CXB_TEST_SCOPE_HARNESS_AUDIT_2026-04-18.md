# Sprint 11-0 — 衡鑑 Audit：Test Scope / Harness

身份：衡鑑
任務 ID：`S11-0-CXB-TEST-SCOPE-HARNESS-AUDIT`
日期：2026-04-18
依據：`Sprint11-All-Round-Gate.md` `S11-0 Test Scope / Harness`

用途：

- 整理 `Sprint 11` 的 test target
- 定義 `InputRouterTest` / `SmartInputManagerTest` 的 fixture / fake strategy
- 明確切開 baseline、後續 round 與不測區域

---

## A. 目前基線現況

已存在的 baseline：

- [app/src/test/java/com/snowrealm/yukiboard/input/router/InputRouterTest.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/test/java/com/snowrealm/yukiboard/input/router/InputRouterTest.kt)
- [app/src/test/java/com/snowrealm/yukiboard/input/smart/SmartInputManagerTest.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/test/java/com/snowrealm/yukiboard/input/smart/SmartInputManagerTest.kt)
- [app/build.gradle](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/build.gradle)

目前測試技術基線：

- `RobolectricTestRunner`
- `ApplicationProvider.getApplicationContext()`
- `androidx.test:core`
- `robolectric`

目前已證實可跑：

- `:app:testDebugUnitTest`

結論：

- `Sprint 10` 已把「空目錄 → 可跑 baseline」補起來
- `Sprint 11` 不需要再處理「測試能不能跑」這個問題，現在重點是「先測哪裡、用什麼 fixture、哪些不碰」

---

## B. Sprint 11 測試目標切分

### B1. `S11-1 InputRouterTest`

本 round 應先測：

1. mode switch 基線
2. input buffer / candidate 基線
3. AUTO mode 最小規則
4. release 基線

建議最小 test target：

1. `switchTo()`：
   - `ENGLISH -> BOPOMOFO`
   - `BOPOMOFO -> AUTO`
   - same mode 不應產生副作用
2. `processInput()`：
   - English 單字母輸入後 composing / candidates 非 crash
   - Bopomofo 基本輸入後 composing 非 crash
3. `processDelete()`：
   - empty state delete 不 crash
   - AUTO 緩衝清理符合最小預期
4. `processSpace()` / `processEnter()`：
   - empty state 不 crash
   - commit 後 candidate state reset 基線
5. `release()`：
   - 可呼叫
   - 重複呼叫不 crash
   - 不強制驗證內部每個 subsystem side-effect

### B2. `S11-2 SmartInputManagerTest`

本 round 應先測：

1. feature flags 基線
2. pure API return 基線
3. snapshot / persist / maintenance 最小不崩潰基線

建議最小 test target：

1. `getSentencePredictions()` 在 `phrasePredictEnabled=false` 時回空
2. `getPhrasePredictionsWithPrefix()` 在 feature 關閉時回空
3. `predictWithContext()` 在 feature 關閉時回空
4. `resetContext()` 可呼叫且不 crash
5. `applyCloudSnapshot(empty/minimal)` 可呼叫且不 crash
6. `persist()` / `maintenance()` 可呼叫且不 crash

### B3. `S11-3 Candidate / Dictionary / Engine Smoke Tests`

這一門不應直接從 `InputRouterTest` 擴寫，而要拆給 `鍵律` 主責。

建議 target：

1. `ChewingInputEngine`
2. `EnglishInputEngine`
3. `CandidateManager`
4. `UserDictionary`
5. `AdaptiveDictionary` / `PersonalDictionary` 最小 smoke

但這門在 `S11-1` / `S11-2` 之前都不應開。

---

## C. Fixture / Fake Strategy

### C1. 共用測試 fixture 原則

優先策略：

1. 用 `Robolectric + ApplicationProvider` 取得真 `Context`
2. 用真 `SharedPreferences` / 真 in-memory state
3. 不在 `S11-1` / `S11-2` 先引入 mock framework
4. 每個 test case 自建 SUT，避免 state 互污染

理由：

- `InputRouter` / `SmartInputManager` 都深度依賴 `Context`
- 目前還沒有為測試設計的 constructor seam
- 這一輪的目標是最小可持續基線，不是高隔離度單元測試純化

### C2. `InputRouterTest` fixture 策略

建議固定模板：

1. `val context = ApplicationProvider.getApplicationContext<Context>()`
2. `val router = InputRouter(context)`
3. 每個 test case 自己建立 router，不共享 instance

必要時的輔助策略：

- 若某 case 需要清乾淨 prefs，可用獨立 helper 清除相關 shared prefs
- 不要為了測 `InputRouter` 去 fake `InputEngine`
- 不要在 `S11-1` 嘗試 mock `UserDictionary` / `SmartInputManager`

### C3. `SmartInputManagerTest` fixture 策略

建議固定模板：

1. `val context = ApplicationProvider.getApplicationContext<Context>()`
2. `val manager = SmartInputManager(context)`

必要時的輔助策略：

- 若測 correction path，才補 `EnglishDictionary` 注入
- baseline round 先避免依賴字典內容或詞頻資料
- `JsonObject` 測試用最小 payload 直接 inline 建立，不另開 fake repository

### C4. 本 sprint 不引入的 fake / harness

本輪不建議引入：

- `MockK`
- `Mockito`
- 自訂 fake `InputEngine`
- 自訂 fake `CloudSyncManager`
- 測試專用 subclass of `InputRouter`
- 測試專用 subclass of `SmartInputManager`

原因：

- 這些都會把 `S11-0` 從 scope audit 變成測試基建專案
- 目前 baseline 足夠支撐 `S11-1` / `S11-2` 的最小 smoke

---

## D. Baseline 邊界

`Sprint 11` 的 baseline 應明確限定為：

1. 可執行
2. 可重複
3. 可擴充
4. 不碰 production code seams

不應在 baseline 階段要求：

1. 高覆蓋率
2. 細緻 assertion 到每一個 dictionary 內容
3. 所有 engine 行為都 deterministic
4. 跨 `service` / `settings` / `input` 端到端整合測試

---

## E. 不測區域與原因

### E1. `S11-1` / `S11-2` 暫不測

1. `IME service` lifecycle
2. `InputConnection` 互動
3. panel / toolbar / UI rendering
4. GL / TTS / remote keyboard
5. OAuth / network / sync

原因：

- 這些不屬於 `InputRouterTest` / `SmartInputManagerTest` 最小基線
- 若硬塞進來，會直接跨到 integration / instrumentation

### E2. `InputRouterTest` 暫不測

1. 多語輸入法完整詞庫正確性
2. candidate 排序細節
3. `AUTO` 全部 edge case
4. `InputRouter.release()` 對每個內部 owner 的 side-effect 完整驗證

原因：

- 詞庫與排序規則變動面太大，應留給 `S11-3`
- `AUTO` edge case 已知很多，Sprint 11 不應一開始就把 test 面炸開

### E3. `SmartInputManagerTest` 暫不測

1. predictor 內容品質
2. style boost 具體分數
3. snapshot schema 全欄位對照
4. 長期學習資料的真實演化

原因：

- 這些會導致測試對資料內容過度脆弱
- baseline 應先驗證 API 行為與 feature gate，不先驗證模型品質

---

## F. 建議開門順序

建議 `玄樞` 在 `S11-0` 後維持 gate 原順序：

1. `S11-1 InputRouterTest`
2. `S11-2 SmartInputManagerTest`
3. `S11-3 Candidate / Dictionary / Engine Smoke Tests`
4. `S11-4 Silent Catch First Pass`
5. `S11-5 GL / TTS Debt Fix`

補充判斷：

- `S11-1` / `S11-2` 可平行心智設計，但 gate 已明寫仍須等 `READY`
- `S11-3` 不應提早插隊，否則測面會先炸開

---

## G. 建議 `玄樞` 判定

```text
READY

Reason:
1. Sprint 11 的 test target、fixture / harness 策略與 baseline 邊界已切清楚。
2. InputRouter / SmartInputManager 兩條線都已有可執行 baseline，可直接進下一輪最小擴測。
3. 不測區域與原因已明列，能避免 S11-1 / S11-2 一開始就擴成 integration 測試專案。
```


# SPRINT10-0-CXB-ENGINEERING-AUDIT

身份：CodeX B

日期：2026-04-18

任務 ID：

- `S10-0-CXB-ENGINEERING-AUDIT`

用途：

- 盤點 `Sprint 10` 工程護欄線的真實現況
- 明確列出最小 file scope、owner、禁區與 round cut 建議
- 只做 audit，不做 implementation

---

## A. 變更摘要

- 已完成 4 條工程護欄題的靜態盤點：
  1. `YukiBoardIMEService.serviceScope` / `CoroutineExceptionHandler`
  2. `InputRouter.release()` 釋放鏈
  3. `bgUpdateReceiver` create / floating / destroy path
  4. `app/src/test` 最小測試骨架入口
- 已核對 `Sprint10-All-Round-Gate.md` 與 `CODEXA_TASK_DISPATCH_SPRINT10_2026-04-18.md`，本回交只涵蓋 `S10-0`，未進入任何 implementation。
- 已補出每條題的最小修點，不接受「可能很多檔」的模糊結論。

---

## B. 風險

### 1. `serviceScope` 缺 `CoroutineExceptionHandler` 是真問題，但修點不應外擴

現況：

- [YukiBoardIMEService.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt:158) 目前是
  `CoroutineScope(Dispatchers.Main + SupervisorJob())`
- service 內有多個 `serviceScope.launch {}` / `serviceScope.launch(Dispatchers.IO) {}` callsite，缺少統一的未捕捉例外觀測入口。

風險：

- child coroutine 內若漏掉 local try-catch，例外會直接走預設 handler，只剩系統 log，IME 自身缺少統一 tag / actionable context。
- 這題屬工程護欄，不是業務 bug；若 round 被擴成「順手把所有 launch 都各自改 try-catch」，scope 會失控。

結論：

- 這題可以留在 `Sprint 10` 做。
- 最小修點應收斂在 `YukiBoardIMEService.kt` 單檔，不需要先碰 `ImeServiceInput.kt` / `ImeServicePanels.kt` / `ImeServiceLifecycle.kt`。

### 2. `InputRouter.release()` 目前只釋放 `UserDictionary` listener，釋放鏈明顯不完整

現況：

- [InputRouter.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt:27) 持有：
  - `userDict`
  - `englishEngine`
  - `bopomofoEngine`
  - `pinyinEngine`
  - `cangjieEngine`
  - `speedCangjieEngine`
  - `japaneseEngine`
  - `koreanEngine`
  - `latinEngines`
  - `candidateManager`
  - `smartInput`
- [InputRouter.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt:429) 的 `release()` 目前只做 `userDict.release()`
- [YukiBoardIMEService.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt:651) 在 `onDestroy()` 會呼叫 `inputRouter.release()`

風險：

- `SmartInputManager` 內持有 7 個長生命週期 subsystem 與 `SwipeLearnManager`，雖多數以 prefs 為主、沒有顯式 `release()` API，但目前沒有任何 owner-map 文件說清楚「哪些故意不釋放、哪些其實應補 release/persist/maintenance」。
- `latinEngines` 是動態 LRU 容器，現階段沒有顯式 close/release API；若未在 round 內明確定義「只補 owner map、不擴展 engine API」，很容易演變成跨整個 input stack 的架構修改。

結論：

- 這題可以留在 `Sprint 10`，但只適合做「owner map + 最小釋放鏈補完」。
- 不適合在 `Sprint 10` 擴成全面性 input engine lifecycle 重構。

### 3. `bgUpdateReceiver` create / destroy 對稱性基本存在，但 create path 仍過度集中於 `onCreateInputView()`

現況：

- [YukiBoardIMEService.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt:439) 在 `onCreateInputView()` 註冊 `bgUpdateReceiver`
- 同檔 [336](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt:336) 與 [620](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt:620) 都有 unregister path
- `ImeSessionHooks.onDestroy()` 只吃 `DestroyCleanup.unregisterReceivers()`，未再拆分 receiver 類型

風險：

- 目前沒有直接證據顯示 receiver 洩漏仍在發生；較像「已補基本對稱，但仍缺集中 owner 註記」。
- 若 round 直接改 receiver 註冊時機或抽成新 coordinator，會觸到 `YukiBoardIMEService` 高敏感區與 panel/background render path，風險高於本輪護欄值。

結論：

- 這題在 `Sprint 10` 內可做，但應排在 `serviceScope` / `InputRouter.release()` 後面。
- round 應限制為最小對稱性修補與 owner 註記，不開新 receiver framework。

### 4. `app/src/test` 目前不存在，測試骨架是空白狀態，不是部分完成

現況：

- `app/src/test` 目前不存在。
- [app/build.gradle](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/build.gradle:219) 只有 `testImplementation 'junit:junit:4.13.2'`
- 沒有 `robolectric`、`mockk`、`kotlinx-coroutines-test`。

風險：

- 若直接想測 `InputRouter` 或 `SmartInputManager`，兩者 constructor 都依賴 Android `Context`：
  - [InputRouter.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt:22)
  - [SmartInputManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/input/smart/SmartInputManager.kt:20)
- 現有依賴不足以在純 JVM test 直接 new 這些 class。

結論：

- `S10-7` 不能直接承諾「先做 InputRouterTest / SmartInputManagerTest」。
- 若要在 `Sprint 10` 落地，最小 round 應先建 test baseline，而不是先寫功能測試。

---

## C. 最小 file scope 建議

### C1. `S10-4 Coroutine 例外護欄`

最小 file scope：

- 允許修改：
  - `/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
- 禁止擴張：
  - `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceInput.kt`
  - `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
  - `/app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceLifecycle.kt`
  - `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionHooks.kt`

理由：

- `serviceScope` 的定義點在 service 本體，最小補法是於 scope 建立處掛統一 handler。
- 現階段不需要為了這個 round 把每個 launch callsite 分散改寫。

### C2. `S10-5 InputRouter 釋放鏈補完`

最小 file scope：

- 第一層 audit / owner map round：
  - `/app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt`
  - `/app/src/main/java/com/snowrealm/yukiboard/input/smart/SmartInputManager.kt`
  - 視需要補一份 lifecycle 接線：
    - `/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
- 禁止擴張：
  - `input/engine/*`
  - `input/smart/*` 全面式 API 重構
  - `dictionary/*` 除非 `CodeX A` 明確核可新增釋放 API

理由：

- 目前真正暴露的 release entry 在 `InputRouter.release()`，owner map 也從這裡往下看最自然。
- `SmartInputManager` 是次級 owner；是否需要新增 `release()` 應先由這一輪判定，而不是直接擴成 7 個 subsystem 全改。

### C3. `S10-6 Receiver / lifecycle 風險修補`

最小 file scope：

- 允許修改：
  - `/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
  - `/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionHooks.kt`
- 禁止擴張：
  - `ui/settings/section/BackgroundSettingsSection.kt`
  - `KeyboardBackgroundView.kt`
  - 任何 panel binder / theme panel 檔案

理由：

- 目前 receiver create / destroy path 都在 IME service 與 `DestroyCleanup` 間，不需要往 settings 背景同步線擴。

### C4. `S10-7 測試骨架前置`

最小 file scope：

- 允許修改：
  - `/app/build.gradle`
  - `/app/src/test/java/com/snowrealm/yukiboard/...` 新增最小 baseline
- 禁止擴張：
  - 任意 `.kt` production code
  - `androidTest` 大量 UI 測試

理由：

- 目前最大缺口是測試基礎建設為零。
- 先把 `app/src/test` 落地、把 JVM 測試依賴補齊，才有資格談 `InputRouterTest` / `SmartInputManagerTest`。

---

## D. 題目逐條判定

### 1. `CoroutineExceptionHandler`

判定：

- `Sprint 10` 可做
- 阻塞級別：中
- 建議順位：高

原因：

- 風險真實存在，且修點集中。
- 是低擴散、高收益的 guardrail 題。

### 2. `InputRouter.release()`

判定：

- `Sprint 10` 可做，但只能做最小釋放鏈補完
- 阻塞級別：中高
- 建議順位：中高

原因：

- `onDestroy()` 已有接線，代表 owner 入口清楚。
- 真風險在於 `release()` 名稱與實際釋放內容嚴重不對稱；若不先收斂，後續每次補 subsystem lifecycle 都會繼續往 service 本體塞。

### 3. `bgUpdateReceiver`

判定：

- `Sprint 10` 可做
- 阻塞級別：中低
- 建議順位：低於前兩題

原因：

- create / destroy 基本對稱已存在，較像補強題，不像當前 active bug。
- 不宜排在使用者 bug 線之前。

### 4. `test baseline`

判定：

- `Sprint 10` 可做，但要定義為 baseline，不是直接保證功能測試
- 阻塞級別：中
- 建議順位：最後

原因：

- 現況是空目錄 + 基礎依賴不足。
- 這題一旦開始就容易牽涉「先為了測試重構 production code」，不適合插到 active bug 線前面。

---

## E. `InputRouter.release()` owner map

### 已有明確釋放 API

- `userDict.release()`：
  - [UserDictionary.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/input/dictionary/UserDictionary.kt:290)
  - 目前也是 `InputRouter.release()` 唯一有呼叫的釋放點

### 目前只有 persist / maintenance，沒有 release API

- `SmartInputManager.persist()` / `maintenance()`：
  - [SmartInputManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/input/smart/SmartInputManager.kt:263)
- 其內部持有：
  - `AdaptiveDictionary`
  - `PhrasePredictor`
  - `SentencePredictor`
  - `TypingHabitTracker`
  - `PersonalDictionary`
  - `StyleProfiler`
  - `SwipeLearnManager`
  - `CorrectionLearnManager`

### 目前沒有發現顯式 release/close/destroy API 的持有者

- `EnglishInputEngine`
- `ChewingInputEngine`
- `PinyinInputEngine`
- `CangjieInputEngine`
- `JapaneseInputEngine`
- `KoreanInputEngine`
- `LatinInputEngine`
- `CandidateManager`

判讀：

- 這代表 `S10-5` 不該預設成「把所有持有物都 release 一遍」。
- 正確做法應是：
  1. 先定義哪些 object 本質是純 in-memory / prefs-backed、故不需顯式釋放
  2. 再看 `SmartInputManager` 是否需要新增極小 `release()` 來集中 persist / maintenance / future-proof hook
  3. 不要在同一 round 直接把所有 engine API 擴成 `close()`

---

## F. 建議 round cut

### 建議 `CodeX A` 先開的 guardrail 順序

1. `S10-4` `Coroutine 例外護欄`
2. `S10-5` `InputRouter 釋放鏈補完`
3. `S10-6` `Receiver / lifecycle 風險修補`
4. `S10-7` `測試骨架前置`

### 理由

- `S10-4` 最集中，最不碰主流程。
- `S10-5` 雖重要，但要先由 `CodeX A` 鎖住只做最小 owner-map 補完，避免擴成 input stack refactor。
- `S10-6` 現況已有基本對稱，不是最急。
- `S10-7` 需要先定義 baseline，否則容易為了測試去改 production code。

---

## G. 禁區 / 未做的事

- 未修改任何 `.kt`
- 未修改 `YukiBoardIMEService.kt`
- 未修改 `InputRouter.kt`
- 未新增 `app/src/test`
- 未提案任何 `Sprint 11+` 題
- 未把日文切換 bug 重新納入 active scope
- 未把 `InputRouter.release()` 題擴寫成 engine 全面重構方案

---

## H. 請 `CodeX A` 判定

建議判定：

```text
READY

Reason:
1. Sprint 10 guardrail 線的 4 題都已盤出最小 file scope，沒有再停留在模糊描述。
2. serviceScope / release chain / receiver / test baseline 的優先順序已可裁決，不需要再回頭做額外靜態盤點。
3. S10-5 與 S10-7 已明確標出不可擴張邊界，可避免 implementation round 失控。
```

建議 `CodeX A` 裁決事項：

1. 是否在 `S10-2` / `S10-3` 使用者 bug 線之後，優先開 `S10-4`。
2. 若要開 `S10-5`，是否明文限制：
   - 不碰 `input/engine/*`
   - 不新增大範圍 lifecycle framework
3. 若要開 `S10-7`，是否接受本 round 目標僅為：
   - 補 test 依賴
   - 建 `app/src/test`
   - 放一個最小 smoke baseline
   而不是直接要求完整 `InputRouterTest` / `SmartInputManagerTest`


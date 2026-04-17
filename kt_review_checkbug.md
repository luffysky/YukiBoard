# Kotlin Review Checkbug

S8-0 狀態：`POST-SPRINT7 RESCAN COMPLETED`

範圍：`app/src/main/java/**/*.kt`

方法：
- 先全量列出 Kotlin 檔並用 `rg` 掃高風險模式
- 再對 IME / panel / game / settings / dictionary / callback 熱區逐檔核對
- 本文件只列目前已確認的 bug / 高風險缺陷，不列一般重構建議

本次重掃結論：
- Sprint 7 未引入新的 blocking regression
- Sprint 6 / 7 完成後，先前確認的 4 個 bug 仍然存在
- 其中 1 / 2 / 3 可收斂為同一批 IME lifecycle / callback 修補
- 4 為 shared-data 一致性題，應獨立成後續 round，不和 1 / 2 / 3 混修

## Findings

### 1. `ImePanelBindings.clearInputModeBindings()` 沒有清掉 `themePanel` / `remotePanel`，會留下 stale view reference

檔案：
- [app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelBindings.kt](app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelBindings.kt)
- [app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt](app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt)
- [app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceMisc.kt](app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceMisc.kt)

定位：
- `ImePanelBindings.kt:31-32` 定義了 `themePanel` / `remotePanel`
- `ImePanelBindings.kt:73-92` 的 `clearInputModeBindings()` 只清到 `userDictPanel` / `handwritingPanel`，沒有把 `themePanel` / `remotePanel` 設回 `null`
- `YukiBoardIMEService.kt:437-438` 背景更新廣播仍會直接打 `imePanelBindings.themePanel?.reloadPreviewBitmap()`
- `ImeServiceMisc.kt:415-422` remote 狀態更新仍會直接打 `imePanelBindings.remotePanel?.invalidate()`

風險：
- panel 關掉後，service 仍可能持有已分離 view 的 reference
- 後續背景更新或 remote 狀態推送會打到舊 panel
- 現象通常不是立即 crash，而是 stale UI、生命週期錯亂、或難追的 reference retention

判定：
- 這是已存在的 lifecycle bug，不是單純技術債

### 2. `fraudDetectRunnable` 仍用 `panelContainer.getChildAt(0)` 硬抓 `ClawPanelView`，依賴 child order，和 active-panel SSOT 不一致

檔案：
- [app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt](app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt)

定位：
- `YukiBoardIMEService.kt:135-139`

問題：
- 現在邏輯直接做 `(panelContainer.getChildAt(0) as? ClawPanelView)?.triggerSkill("fraud_detect")`
- 這是假設第 0 個 child 永遠是 claw panel
- 但同檔 `YukiBoardIMEService.kt:143-144` 已經明寫 active panel 的 SSOT 是 `imeCoordinator.uiState.activePanel`

風險：
- panel container 只要調整插入順序、疊加 top/bottom panel、或保留舊 child，fraud detect 就可能靜默失效
- 這類問題不一定 crash，但會造成「功能偶發不觸發」的假死

判定：
- 這是邏輯對象選取錯誤，應優先改成走 active panel / binding owner，而不是 child index

### 3. MIDI 匯入仍靠 static callback，且 callback 沒有清空，panel 關閉後可能把結果送進舊 `MusicPanelView`

檔案：
- [app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt](app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt)
- [app/src/main/java/com/snowrealm/yukiboard/ui/MidiImportActivity.kt](app/src/main/java/com/snowrealm/yukiboard/ui/MidiImportActivity.kt)

定位：
- `ImeServicePanels.kt:465-476`
- `MidiImportActivity.kt:16-20`
- `MidiImportActivity.kt:60`
- `MidiImportActivity.kt:71`

問題：
- `launchMidiImportFor(panel)` 把 callback capture 到當下的 `panel`
- `MidiImportActivity.onMidiImported` 是 companion object static callback
- Activity 完成後只 `finish()`，沒有把 `onMidiImported` 清成 `null`

風險：
- 使用者開啟 MUSIC panel 後按匯入，若中途關閉 panel、切換模式、或重建 IME，再回來拿到匯入結果時，callback 仍可能把資料送進舊 panel instance
- 這會形成 stale callback / view retention，嚴重時是匯入結果落到不可見 panel，輕則是 ghost update

判定：
- 這是 callback 生命周期缺口，屬實質 bug

### 4. `UserDictionary` 在 settings / IME / router / sync 各自 new instance，但類別本身只在 init 時載入一次，沒有 reload 或 shared-state 機制

檔案：
- [app/src/main/java/com/snowrealm/yukiboard/input/dictionary/UserDictionary.kt](app/src/main/java/com/snowrealm/yukiboard/input/dictionary/UserDictionary.kt)
- [app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt](app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt)
- [app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt](app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt)
- [app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt](app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt)

定位：
- `UserDictionary.kt:25-33` 持有 in-memory `mutableMap`
- `UserDictionary.kt:40-42` 只在 init `loadAll()`
- `UserDictionary.kt` 內沒有 `reload()` 或 `SharedPreferences` listener
- `InputRouter.kt:27` 建一份 `UserDictionary(context)`
- `ImeServicePanels.kt:353` USER_DICT panel 再建一份
- `SettingsActivity.kt:190` 匯出再建一份
- `SettingsActivity.kt:402` 匯入再建一份

風險：
- settings 匯入 / 修改之後，`InputRouter` 裡那份 dictionary 不會自動 reload
- USER_DICT panel 跟實際輸入建議可能讀的是不同批 in-memory state
- 這種問題通常表現成「明明匯入成功，但輸入建議沒立刻反映，直到重開 IME / Activity 才正常」

判定：
- 這是 shared-data 一致性 bug，不只是架構不漂亮

## 其他觀察

- `VoiceGame` 仍是 stub，但目前 `GameFloatingPanel` 已鎖住 `VOICE_GAME` 卡片，不算當前 blocking bug
- 最近 Sprint 6 新增的 binder 類別本身沒有看到新的 blocking issue；主要問題仍集中在既有 lifecycle / state ownership

## 建議優先順序

1. 修 `ImePanelBindings` stale reference 清理
2. 修 `fraudDetectRunnable` 的 child-index 依賴
3. 修 MIDI static callback 生命周期
4. 規劃 `UserDictionary` shared-state / reload 策略

## S8-0 Gate 結論

```text
READY

Reason:
1. Post-Sprint7 Kotlin 全量重掃已完成，且結果已重新落成正式 bug review。
2. 4 個 finding 均已重新核實，沒有只靠舊文件沿用。
3. 已可安全開 S8-1，但只開一門，先處理 IME lifecycle / callback 類 bug。
```

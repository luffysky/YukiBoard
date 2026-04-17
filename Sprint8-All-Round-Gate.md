# Sprint 8 All Round Gate

身份：CodeX A

用途：

- 作為 `Sprint 8` 唯一總 gate 文件
- `Sprint 8` 專門處理前面 sprint 實作後確認的 bug / lifecycle defect / state inconsistency
- 逐 round 記錄：
  - 任務
  - 回交
  - `CodeX A` 審核結論
  - 下一輪開門指派
- 已完成 round 一律保留並加刪除線，不刪除
- 之後 `Sprint 8` 不再新增額外 gate `.md`

---

## 全域規則

1. 一次只開一個 round。
2. 前一 round 完成並經 `CodeX A` 審核通過，才開下一 round。
3. 執行者可提候選方向，但不可自行指定後續 round 順序。
4. 若需擴大 file scope，必須先回報，不可默擴。
5. `Sprint 8` 僅處理 bugfix / defect cleanup，不可混入新功能。
6. `Sprint 8` 的 bug 清單以 `CodeX A` 審核後的文件與 round review 為準，不以口頭印象為準。
7. 允許在 `Sprint 7` 最後一輪期間並行準備 `Sprint 8` 前置作業，但不得提前開 `S8-0` 或任何 bugfix round。

---

## 開門前提

`Sprint 8` 的正式前提：

1. `Sprint 7` 全部完成，並經 `CodeX A` 最終審核通過。
2. `CodeX A` 於 `Sprint 7` 完成後，重新全量掃描一次所有 Kotlin 檔。
3. 重新掃描結果需正式落成 bug review 文件，作為 `Sprint 8` 的 round 排序依據。
4. 不得在 `Sprint 7` 未完成前，以零星 bugfix 名義提前開 `Sprint 8`。

---

## ~~Round S8-PRE：Sprint 8 前置準備~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~在 `S7-4` 進行期間先整理 `Sprint 8` 的前置材料~~
- ~~預先收斂 bug backlog、重掃範圍、驗證計畫與 round 切法~~
- ~~確保 `Sprint 7` 一結束就能立即進 `S8-0`~~

### ~~允許事項~~

1. ~~整理既有 bug 清單與候選分組~~
2. ~~整理 `kt_review_checkbug.md` 後續續審範圍~~
3. ~~整理驗證腳本 / smoke checklist / endpoint checklist~~
4. ~~做文件盤點與 round 切法建議~~

### ~~禁止事項~~

1. ~~不改 `.kt`~~
2. ~~不做 bugfix implementation~~
3. ~~不得宣告任何 `S8-0` finding 已最終成立~~
4. ~~不得跳過 `Sprint 7` 結案直接開 `S8-0`~~

### ~~任務指派~~

#### ~~CodeX A~~

- ~~主導前置準備與最終開門判定~~

#### ~~Claude Code B~~

- ~~整理 bugfix implementation 候選 batch 與最小 file scope 建議~~

#### ~~Claude Code A~~

- ~~整理 settings / auth / provider / sync / lifecycle 類 bug 的 side-note backlog~~
- ~~若 `CodeX B` 額度仍不足，持續補技術 review 視角的前置盤點~~

### ~~CodeX A 當前判定~~

```text
READY

Reason:
1. Sprint 8 正式 round 仍未開門。
2. 但可先在 S7-4 期間並行準備 Sprint 8 前置材料。
3. 此前置不涉及任何 .kt implementation，不會與 Sprint 7 主線衝突。
```

### ~~回交結果~~

- ~~`docs/SPRINT8_PRE_CCA_BUG_BACKLOG_2026-04-18.md`~~
- ~~`docs/SPRINT8_PRE_CCB_IMPL_SCOPE_2026-04-18.md`~~

### ~~本輪結論~~

- ~~`S8-PRE = COMPLETED`~~
- ~~下一門待開 `S8-0 = Post-Sprint7 Kotlin Bug Rescan`~~

---

## ~~Round S8-0：Post-Sprint7 Kotlin Bug Rescan~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~在 `Sprint 7` 結束後重新掃描所有 Kotlin 檔~~
- ~~重新確認：~~
  - ~~既有 bug 是否仍存在~~
  - ~~`Sprint 7` 是否引入新 bug~~
  - ~~哪些問題應升級為 `Sprint 8` 實作 round~~

### ~~預定輸出~~

1. ~~`kt_review_checkbug.md` 更新版或續審版~~
2. ~~`Sprint 8` bug round 候選清單~~
3. ~~風險排序與最小修補建議~~

### ~~預定 owner~~

#### ~~CodeX A~~

- ~~主導全量 Kotlin 重掃~~
- ~~決定哪些問題正式進入 `Sprint 8`~~

#### ~~Claude Code A（暫代 CodeX B）~~

- ~~任務 ID：`S8-0-CCA-ACTING-CXB-CROSS-REVIEW`~~
- ~~暫代 `CodeX B` 做技術面交叉審核與風險排序~~
- ~~文件命名慣例：延續單檔雙段格式，若需回交則使用 `docs/SPRINT8_0_CCA_CROSS_REVIEW_2026-04-18.md`~~

#### ~~Claude Code B~~

- ~~待 `S8-1+` 開門後負責實作~~

#### ~~Claude Code A~~

- ~~待 `S8-1+` 開門後負責 side-effect / settings / integration impact note~~

### ~~當前開門條件判定~~

```text
READY

Reason:
1. Sprint 7 已完成，S8-PRE 文件也已收齊。
2. 依規則，下一步必須是 CodeX A 主導的全量 Kotlin 重掃。
3. 在 S8-0 完成前，S8-1+ 仍不得開門。
```

### ~~任務指派~~

#### ~~CodeX A~~

- ~~任務 ID：`S8-0-CODEXA-KOTLIN-BUG-RESCAN`~~

目標：

- ~~重新全量掃描 Kotlin 檔~~
- ~~更新 `kt_review_checkbug.md`~~
- ~~產出正式 `Sprint 8` bug round 候選與排序依據~~

#### ~~Claude Code A（暫代 CodeX B）~~

- ~~任務 ID：`S8-0-CCA-ACTING-CXB-CROSS-REVIEW`~~

目標：

- ~~對 `CodeX A` 的重掃結果做技術面交叉審核與風險排序補充~~

要求：

1. ~~不主導 round 判定~~
2. ~~不自排 `S8-1+`~~
3. ~~僅做 cross-review，不做 implementation~~
4. ~~若需回交，沿用單檔格式，不拆 side-note / acting review 兩份~~

### ~~回交結果~~

- ~~`kt_review_checkbug.md`~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. Post-Sprint7 Kotlin 全量重掃已完成，且 finding 已重新核實。
2. Sprint 7 沒有引入新的 blocking regression；已確認 bug 可切成明確後續 round。
3. 現在可安全只開 `S8-1`，先處理 IME lifecycle / callback 類 bug。
```

### ~~本輪結論~~

- ~~`S8-0 = COMPLETED`~~
- ~~下一門開 `S8-1 = IME Lifecycle / Callback Bugfix Batch`~~

---

## ~~Round S8-1：IME Lifecycle / Callback Bugfix Batch~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~修 `ImePanelBindings` stale reference 清理~~
- ~~修 `fraudDetectRunnable` 的 child-index 依賴~~
- ~~修 MIDI import static callback 生命周期~~
- ~~不混入 `UserDictionary` shared-state 題~~

### ~~任務指派~~

#### ~~Claude Code B~~

- ~~任務 ID：`S8-1-CCB-IME-LIFECYCLE-CALLBACK-BUGFIX`~~

目標：

- ~~以最小 file scope 修正 IME lifecycle / callback 類 bug~~

本輪允許修改：

- ~~`app/ime/ImePanelBindings.kt`~~
- ~~`service/YukiBoardIMEService.kt`~~
- ~~`service/ImeServicePanels.kt`~~
- ~~`ui/MidiImportActivity.kt`~~
- ~~與上述三題直接相關的最小必要檔案~~

本輪禁止修改：

- ~~`UserDictionary` / `InputRouter` / `SettingsActivity`~~
- ~~`CloudSyncManager`~~
- ~~Sprint 7 integration 題~~
- ~~其他未經核可的 batch 題~~

#### ~~Claude Code A~~

- ~~任務 ID：`S8-1-CCA-IME-LIFECYCLE-IMPACT-NOTE`~~

目標：

- ~~審核本輪 bugfix 對 settings / integration / lifecycle 的 side-effect~~

要求：

1. ~~不改 `.kt`~~
2. ~~只盯本 batch 三題~~
3. ~~不得擴成 `UserDictionary` round~~

#### ~~Claude Code A（暫代 CodeX B）~~

- ~~任務 ID：`S8-1-CCA-ACTING-CXB-TECH-REVIEW`~~

目標：

- ~~暫代 `CodeX B` 做本輪技術審核與 file scope review~~

提醒：

1. ~~`CodeX B` 任務持續由 `Claude Code A` 代班~~
2. ~~回交時需明確區分 impact note 與 acting tech review 視角~~
3. ~~不得自行指定 `S8-2`~~

### ~~回交結果~~

- ~~`docs/SPRINT8_1_CCB_IME_LIFECYCLE_CALLBACK_BUGFIX_2026-04-18.md`~~
- ~~`docs/SPRINT8_1_CCA_SIDE_NOTE_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無 blocking finding~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`ImePanelBindings.clearInputModeBindings()` 已補 `themePanel = null` 與 `remotePanel = null`~~
  - ~~`fraudDetectRunnable` 已改走 `imeContainerState.activePanel == PanelType.CLAW` SSOT + defensive child iteration，不再依賴 index 0~~
  - ~~`MidiImportActivity.onDestroy()` 已清掉 static `onMidiImported` callback~~
- ~~本輪未前移的禁區：~~
  - ~~`UserDictionary` / `InputRouter` / `SettingsActivity` 未動~~
  - ~~`CloudSyncManager` 未動~~
  - ~~Sprint 7 integration 題未動~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. 三個 S8-0 finding 已按最小 file scope 落地修補，且我已核對實際 `.kt`。
2. 修補方向與回交文件一致，沒有新增 blocking regression。
3. `UserDictionary` 題仍被正確留在下一門，未混修。
4. compile 已通過。
```

### ~~本輪結論~~

- ~~`S8-1 = COMPLETED`~~
- ~~下一門開 `S8-2 = UserDictionary Shared-State / Reload Bugfix Batch`~~

---

## ~~Round S8-2：UserDictionary Shared-State / Reload Bugfix Batch~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~修 `UserDictionary` 多 instance + init-only load 造成的 shared-state 不一致~~
- ~~明確處理 IME / settings / sync / router 之間的 reload 或 SSOT 策略~~
- ~~不混入其他 provider / auth / sync 題~~

### ~~任務指派~~

#### ~~CodeX B~~

- ~~任務 ID：`S8-2-CXB-USERDICT-TECH-REVIEW`~~

目標：

- ~~恢復原職，負責本輪技術審核與 file scope review~~

#### ~~Claude Code B~~

- ~~任務 ID：`S8-2-CCB-USERDICT-SHARED-STATE-BUGFIX`~~

目標：

- ~~以最小 file scope 修正 `UserDictionary` shared-state / reload 類 bug~~

本輪允許修改：

- ~~`input/dictionary/UserDictionary.kt`~~
- ~~`input/router/InputRouter.kt`~~
- ~~`service/ImeServicePanels.kt`~~
- ~~`ui/settings/SettingsActivity.kt`~~
- ~~`data/CloudSyncManager.kt`~~
- ~~與 `UserDictionary` 一致性直接相關的最小必要檔案~~

本輪禁止修改：

- ~~Sprint 7 integration 檔~~
- ~~7 smart subsystem singleton 題~~
- ~~provider / auth / CloudSync token 題~~
- ~~其他未經核可的 Sprint 8 batch 題~~

#### ~~Claude Code A~~

- ~~任務 ID：`S8-2-CCA-USERDICT-IMPACT-NOTE`~~

目標：

- ~~審核本輪對 settings / integration / sync 的 side-effect~~

要求：

1. ~~不改 `.kt`~~
2. ~~只盯 `UserDictionary` 一致性題~~
3. ~~不得自行指定 `S8-3`~~

### ~~回交結果~~

- ~~`docs/SPRINT8_2_CCA_IMPACT_NOTE_2026-04-18.md`~~
- ~~`docs/SPRINT8_2_CCB_USERDICT_SHARED_STATE_BUGFIX_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`UserDictionary.kt` 已新增 `_dict_version` + listener reload 機制，且 v3 已把版本分配整段納入 process-wide `versionLock` critical section~~
  - ~~本輪實作維持單檔最小 scope，6 個既有 callsite 全未被改動~~
  - ~~這輪是同一 round 內的重交：v1 → `NOT READY`、v2 仍不足、v3 才通過；不另開新 round~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. `UserDictionary.kt` 的 v3 修補已把 `_dict_version` 分配整段納入 companion-level `versionLock`，跨 instance 並發寫也會被序列化。
2. `max(currentPrefVersion, lastKnownVersion) + 1` 在 lock 內執行後，版本號已成為嚴格唯一且遞增的 token，先前 v1 / v2 的 collision 問題已消失。
3. `CodeX B` 與 `Claude Code A` 再審都確認紅線守住，且 scope 仍維持單檔 `UserDictionary.kt`。
4. compile 已通過。
```

### ~~修補歷程備註~~

 - ~~本輪有二次重交，但都屬 `S8-2` 同一 round 內修補，不另開新 round：~~
 - ~~v1：`System.currentTimeMillis()`，因 same-ms collision 被退回~~
 - ~~v2：`max(...) + 1`，因 true concurrent write collision 被退回~~
 - ~~v3：`versionLock + max(...) + 1`，通過~~

### ~~本輪結論~~

- ~~`S8-2 = COMPLETED`~~
- ~~`Sprint 8 = COMPLETED`~~
- ~~若後續要做 `InputRouter.release()` 等非 blocking 強化，需另開新 sprint / 新 gate，不併入本輪~~

---

## ~~Round S8-3：UserDictionary Listener Release Strengthening~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~補 `UserDictionary.release()` 的實際接線~~
- ~~讓長生命週期持有者在拆解時明確解除 listener~~
- ~~只做 non-blocking strengthening，不改變 `S8-2` 已完成的 shared-state 修補語意~~

### ~~任務指派~~

#### ~~CodeX B~~

- ~~任務 ID：`S8-3-CXB-USERDICT-RELEASE-TECH-REVIEW`~~

目標：

- ~~審核 `release()` 接線的最小 file scope 與 lifecycle 對稱性~~

#### ~~Claude Code B~~

- ~~任務 ID：`S8-3-CCB-USERDICT-RELEASE-STRENGTHENING`~~

目標：

- ~~以最小 file scope 補上 `UserDictionary.release()` 的實際 wiring~~

本輪允許修改：

- ~~`input/router/InputRouter.kt`~~
- ~~`service/YukiBoardIMEService.kt`~~
- ~~`app/ime/DestroyCleanup.kt`~~
- ~~`app/ime/FinishInputCleanup.kt`~~
- ~~`input/dictionary/UserDictionary.kt`~~
- ~~與 listener release lifecycle 直接相關的最小必要檔案~~

本輪禁止修改：

- ~~`UserDictionary` shared-state 主修補語意~~
- ~~Sprint 7 integration 檔~~
- ~~7 smart subsystem~~
- ~~provider / auth / sync 題~~
- ~~其他未經核可的 batch 題~~

#### ~~Claude Code A~~

- ~~任務 ID：`S8-3-CCA-USERDICT-RELEASE-IMPACT-NOTE`~~

目標：

- ~~審核本輪對 IME lifecycle / settings / integration 的 side-effect~~

要求：

1. ~~不改 `.kt`~~
2. ~~只盯 `release()` wiring 與 lifecycle 對稱性~~
3. ~~不得自行指定後續 round~~

### ~~回交結果~~

- ~~`docs/SPRINT8_3_CCA_IMPACT_NOTE_2026-04-18.md`~~
- ~~`docs/SPRINT8_3_CCB_USERDICT_RELEASE_STRENGTHENING_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無 blocking finding~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`InputRouter.release()` 已只內聚釋放 `userDict.release()`~~
  - ~~`ImeSessionHooks.kt` 的 `DestroyCleanup` 已新增 `releaseInputRouter` 欄位並接到 `onDestroy` 鏈~~
  - ~~`YukiBoardIMEService.kt` 已補 `::inputRouter.isInitialized` 防禦後再執行 `inputRouter.release()`~~
- ~~本輪未前移的禁區：~~
  - ~~`UserDictionary` shared-state 主修補語意未動~~
  - ~~`ImeServicePanels.kt` / `SettingsActivity.kt` / `CloudSyncManager.kt` 未動~~
  - ~~Sprint 7 integration、7 smart subsystem、provider/auth/sync 題未動~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. `release()` wiring 已按最小 file scope 落地，且 lifecycle 對稱性成立。
2. S8-2 的 shared-state 主修補語意完全未被回頭污染。
3. `CodeX B` 與 `Claude Code A` 都確認本輪無 blocking finding。
4. compile 已通過。
```

### ~~本輪結論~~

- ~~`S8-3 = COMPLETED`~~
- ~~`Sprint 8 = COMPLETED`~~
- ~~`S8-4` 保留為未來需要時才開的新補強門，不視為當前 sprint 未完成~~

---

## Round S8-4：Bugfix Batch 4

### 當前狀態

- `LOCKED`

### 說明

- 待 `S8-3` 完成後，若還有新的 non-blocking strengthening 或 bugfix 題，再決定是否開門。

---

## Sprint 8 當前總狀態

- 進行中：
  - 無
- 可審核但未開門：
  - 無
- 已完成：
  - `S8-PRE` `Sprint 8 前置準備`
  - `S8-0` `Post-Sprint7 Kotlin Bug Rescan`
  - `S8-1` `IME Lifecycle / Callback Bugfix Batch`
  - `S8-2` `UserDictionary Shared-State / Reload Bugfix Batch`
  - `S8-3` `UserDictionary Listener Release Strengthening`
- 待前置完成後開門：
  - 無
- `CodeX A` 當前判定：
  - `Sprint 8 = COMPLETED`

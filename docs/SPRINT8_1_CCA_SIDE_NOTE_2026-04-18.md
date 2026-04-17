# Sprint 8-1 Claude Code A 雙視角回交

- 身份：Claude Code A（本業 + 代行 CodeX B）
- 時間：2026-04-18
- 適用 round：`S8-1` IME Lifecycle / Callback Bugfix Batch
- 對應 gate 任務：
  - `S8-1-CCA-IME-LIFECYCLE-IMPACT-NOTE`（CCA 本業 — settings / integration / lifecycle side-effect 審核）
  - `S8-1-CCA-ACTING-CXB-TECH-REVIEW`（CCA 代行 `CodeX B` — 技術審核與 file scope review）
- 審核對象：`CodeX A`
- 依 gate 要求：回交「需明確區分 impact note 與 acting tech review 視角」、「不自行指定 `S8-2`」
- 文件格式：依 S8-0 gate 指示延續單檔雙段格式

---

## PART I — CCA 本業：IME Lifecycle Impact Note

### I-A. 本 batch 三題對 settings / integration / lifecycle 的 side-effect 審核

以下每題按「修法方向（供 CCB 參考）→ 可能副作用 → CCA 要守的邊界」三段列出。
修法細節由 CCB 決定，CCA 不代筆。

#### 1. `ImePanelBindings.clearInputModeBindings()` 遺漏 `themePanel` / `remotePanel`

**修法方向（供 CCB 參考）**：
- 在 `ImePanelBindings.kt:73-92` 的 `clearInputModeBindings()` 結尾追加 `themePanel = null` 與 `remotePanel = null`
- 不改 method 命名（避免改動上層 `clearActivePanelBindings()` wrapper 語意）

**side-effect 地圖**：

| 受影響路徑 | 檔案 / 行 | 當前行為 | 修後預期 |
| --- | --- | --- | --- |
| 背景更新廣播 | `YukiBoardIMEService.kt:437-438`（`bgUpdateReceiver`） | panel 關閉後 bg 廣播仍打 `themePanel?.reloadPreviewBitmap()` → stale view 被 reload | `themePanel` 為 null 時 safe-call 短路，不再觸碰已 detach 的 view |
| Remote 狀態更新 | `ImeServiceMisc.kt:415-422`（`updateRemoteConnectionState` 類流程） | panel 關閉後仍寫 `remotePanel?.btState`、`wifiUrl`、`invalidate()` | `remotePanel` 為 null 時 safe-call 短路 |
| Theme listener 生命週期 | `ImeServicePanels.kt:297` 後續 `ThemeManager.addOnThemeChangedListener(themeListener)` + panel `addOnDetachListener { tm.removeOnThemeChangedListener(themeListener) }` | listener 於 panel detach 時被移除（與 `imePanelBindings.themePanel` 狀態無關） | **不受此修改影響** — listener 自管 |
| panel 重新建立 | `ImeServicePanels.kt:212`、`297` `.also { imePanelBindings.themePanel/remotePanel = it }` | 下次 create 覆寫原 slot | 若 slot 為 null 起點，覆寫行為等價 |

**可能副作用（CCA 需要盯）**：

1. **bg 廣播接收器仍 registered**：bg 廣播接收器在 `onCreateInputView` 註冊（`registerReceiver(bgUpdateReceiver, bgFilter, ...)`），其生命週期與 service 綁定，與 panel slot null/非 null 無關。修法只影響「廣播觸發時 `themePanel?` 是否為 null」，不破壞 receiver 註冊。
2. **remote 狀態廣播來源**：`updateRemoteConnectionState` 類呼叫來自 Bluetooth / WiFi 連線事件；panel 關閉期間若仍收到連線事件，原本會在 stale view 上寫 state，修後改為 safe-call 短路。等同於「panel 關閉期間 remote 狀態不更新 UI」，符合預期。
3. **`bindCreation()` 初始化的 9 個低依賴 panel**：已有現成 null 清理，本題不動。
4. **`ImePanelBindings` 的 `activeRawCommitTarget()` / `activeInputTarget()`**：未列入 `themePanel` / `remotePanel`，修法不影響 input target 選擇。

**CCA 邊界守線**：
- 不得擴成「清理所有其他 panel slot」的結構改動（例如 `userDictPanel` reload 時機、其他 listener 重綁）— 那些是 S8-4+ / Sprint 9 級別主題，本輪禁止擴。
- 不得在 `clearInputModeBindings()` 內加入新的副作用（例如 `themePanel?.detach()`、`remotePanel?.disconnect()`）— 僅設 null。
- 不得動 `clearActivePanelBindings()` wrapper。

#### 2. `fraudDetectRunnable` 走 `panelContainer.getChildAt(0)` 改走 SSOT

**修法方向（供 CCB 參考）**：
- `YukiBoardIMEService.kt:135-139` 的 `fraudDetectRunnable` 內，改為查 `imeCoordinator.uiState.activePanel`（SSOT）
- 若 SSOT 顯示 active panel 為 CLAW，再透過 bindings 或 factory 產出路徑拿到該 `ClawPanelView` 實例
- 若 bindings 無 `clawPanel` slot，則**新增最小必要 slot**（`var clawPanel: ClawPanelView? = null`）並在 CLAW panel 建立處補 `.also { imePanelBindings.clawPanel = it }`，並於 `clearInputModeBindings()` 一併清 null

**side-effect 地圖**：

| 受影響路徑 | 當前行為 | 修後預期 |
| --- | --- | --- |
| fraud detect 觸發時機 | 假設 child 0 = ClawPanel；只要 child order 洗牌 / 其他 panel overlay，就靜默失效 | 走 SSOT，active panel 非 CLAW 時直接短路，與使用者預期一致 |
| 其他 `panelContainer.getChildAt(...)` 路徑 | 本輪不變動 | **本輪 CCA 禁止擴掃其他 child-index 依賴點** — 屬 S8-2+ 候選 |
| `ClawPanelView.triggerSkill("fraud_detect")` 語意 | 等同現有 | 不改 panel API |

**可能副作用（CCA 需要盯）**：

1. **SSOT 不同步時的行為變化**：若 `imeCoordinator.uiState.activePanel` 與實際 UI 有延遲（例如 coordinator 先於 panel create / destroy 更新狀態），可能出現「active panel 顯示 CLAW 但 bindings.clawPanel 尚未 set」或反之的短暫視窗。修法需在這類視窗使用 null-safe 短路（不觸發 fraud detect），而非 crash。
2. **新增 `clawPanel` binding slot 的擴面問題**：若 CCB 選擇新增 slot，相當於擴大 `ImePanelBindings` 本體；依 CCA 判斷這屬「最小必要擴面」，符合 gate 允許範圍（binding owner 在 service 層是 Sprint 6 已定方向）。但 CCB 若選擇不新增 slot 而用其他方法（例如從 factory 回查）也可接受，**此為 CCB 選擇**。
3. **fraud detect 延遲觸發 runnable 的時機**：`fraudDetectRunnable` 是 `Runnable`，通常由 `Handler.postDelayed` 排程；修法不改排程路徑，只改 runnable 內部查詢。

**CCA 邊界守線**：
- 不得順手把 `panelContainer.getChildAt(...)` 其他使用點都改掉 — 本輪只處理 `fraudDetectRunnable`。
- 不得改 `imeCoordinator.uiState.activePanel` 定義。
- 不得改 `ClawPanelView.triggerSkill` 簽章。
- 若 CCB 新增 `clawPanel` slot，**必須同時在 `clearInputModeBindings()` 加清理**，避免製造新的 stale reference。

#### 3. MIDI import static callback 生命週期

**修法方向（供 CCB 參考）**：
- `MidiImportActivity.kt:60` `onMidiImported?.invoke(song)` 之後立即或於 `finally` nullify `companion object` 的 `onMidiImported`
- `MidiImportActivity.onDestroy()` 或 `finish()` 路徑也 nullify（防 Activity 未跑到 `onActivityResult` 就被 kill）
- `ImeServicePanels.kt:463-479` `launchMidiImportFor(panel)` 設 callback 的處理保持不變（設 lambda 捕獲 `panel`），但要容忍重入 — 連續兩次啟動應以最後一次 callback 為準

**side-effect 地圖**：

| 受影響路徑 | 當前行為 | 修後預期 |
| --- | --- | --- |
| Activity 正常流程 | import 結果 → `onMidiImported?.invoke` → Activity finish；callback 留著 | import 結果 → `onMidiImported?.invoke` → callback 清 null → Activity finish |
| Activity 被 kill（取消 / back / 錯誤） | callback 留著，下次重建 Activity 可能用到舊 callback | `onDestroy()` 清 null |
| 重入（連續兩次 `launchMidiImportFor`） | 後一次覆寫 callback，前一次 lambda 引用的舊 panel 變 orphan（但不可達） | 行為不變（覆寫語意仍正確） |
| panel 關閉後舊 callback 觸發 | callback 把結果塞進舊 `MusicPanelView` 實例，view 已 detach | callback 已 null，`onMidiImported?.invoke` 短路 |

**可能副作用（CCA 需要盯）**：

1. **`Handler(Looper.getMainLooper()).post { panel.addImportedSong(song) }` 的 closure**：callback lambda 捕獲 `panel` 引用；即使 Activity 側 nullify callback，已 invoke 的 main-thread post 仍會跑。若 panel 已 detach 此時 `addImportedSong` 需自身 safe（view.isAttachedToWindow / null-check）。**此屬 panel 本體防護題，不屬本 round**，CCA 只標為觀察點。
2. **`MusicPanelView.addImportedSong` 當下的 view state**：若 view 已被 `ViewGroup.removeView` 但 instance 還存在，`addImportedSong` 若只更新內部 model 不操作 canvas 並無立即傷害；若操作 canvas 則潛在小幅浪費但非 crash。屬觀察點。
3. **callback nullify 時機**：若 CCB 選擇在 `invoke` 前 nullify（例如 `val cb = onMidiImported; onMidiImported = null; cb?.invoke(...)`）以防重入期間 callback 被替換，語意略有差異；**此為 CCB 選擇**，CCA 只要修後測試覆蓋正常流程即可。
4. **`onMidiImported?.invoke(song)` vs `Handler.post { panel.addImportedSong(song) }`**：invoke 走主緒 post，callback nullify 順序應放在 invoke 之後不影響已排程的 main post。

**CCA 邊界守線**：
- 不得順手改 `MusicPanelView` 本體（屬 panel 類 ownership，不在本 batch）。
- 不得改 `launchMidiImportFor(panel)` 的 lambda 設計（仍然可 capture `panel`）— 只要 callback nullify 正確即可。
- 不得順手處理「連續兩次 import 的覆寫語意」以外的重入情境（例如 panel 切換時 Activity 仍在背景）— 屬 S8-2+ 候選。
- 不得把 `onMidiImported` 從 static companion 改為 instance field — 改架構等同重做 Sprint 6-1 MIDI 匯入方案，逾界。

### I-B. 跨題整合 side-effect 審核

1. **三題都在 IME lifecycle 主線上**：修改都只加 null 設定或 SSOT 查詢，不動 service 主流程。不會引發 onCreate / onStartInputView / onFinishInputView 順序變化。
2. **無 settings UI 相關變動**：本 batch 三題全在 IME service / activity 範圍，不觸碰 `SettingsActivity` / `AISettingsActivity` / `YukiPreferences`。
3. **無 integration boundary 變動**：不觸碰 `AppHttpClient` / `InsightApiClient` / `SupabaseClient` / `SnowRealmAuthClient` / `UserSessionRepository` / `CloudSyncManager`（Sprint 7 交付物）。
4. **無 input / engine 變動**：不觸碰 `InputRouter` / `UserDictionary` / `SmartInputManager` / 7 subsystem（Finding 4 禁混入）。
5. **Sprint 7 交付物穩定性**：本 batch 三題不動 Sprint 7 任何檔案，Sprint 7 的 session / sync / gateway / prefs 收斂不受影響。

### I-C. 本輪 CCA 觀察但不處理（記錄但不動手）

以下為審查時順手看到但**屬他 round**，CCA 只記錄：

1. `launchMidiImportFor` callback lambda 的 `Handler(Looper.getMainLooper()).post { panel.addImportedSong(song) }` — panel 已 detach 時的 safe-guard，屬 `MusicPanelView` 本體防護，S8-2+ 候選。
2. `panelContainer.getChildAt(...)` 其他使用點是否還有 child-index 依賴 — 屬 S8-2+ 候選。
3. `bgUpdateReceiver` 於 `onFinishInputView` 是否 `unregisterReceiver` — 本輪未列入修復範圍，屬 service lifecycle 獨立題。
4. `ImePanelBindings.bindCreation()` 與 `imePanelBindings.themePanel = it` / `remotePanel = it` 的不對稱（前者走 creation struct、後者散在 `ImeServicePanels.kt`）— 屬架構整理，Sprint 9 級。
5. `UserDictionary` shared-state（Finding 4）— 明列禁區，等後續 round。
6. Sprint 8 前置整理（`SPRINT8_PRE_CCA_BUG_BACKLOG_2026-04-18.md`）中 A-1 / A-3 / F-2 登入鏈候選、B-1 provider cache — 均未混入本 batch。

---

## PART II — 代行 `CodeX B`：技術審核與 file scope review

### II-A. File scope review

#### II-A-1. Gate 允許範圍

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImePanelBindings.kt`
- `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
- `app/src/main/java/com/snowrealm/yukiboard/ui/MidiImportActivity.kt`
- 與上述三題直接相關的最小必要檔案

#### II-A-2. 直接相關最小必要檔案判定

基於 finding 定位與上文 side-effect 地圖：

| 檔案 | 是否允許本輪修改 | 理由 |
| --- | --- | --- |
| `ImePanelBindings.kt` | ✅ 本輪主戰場（Finding 1） | 加 `themePanel` / `remotePanel` null 清理 |
| `YukiBoardIMEService.kt:135-139` | ✅ 本輪主戰場（Finding 2） | 改 `fraudDetectRunnable` 查 SSOT |
| `ImeServicePanels.kt` | ✅ 本輪主戰場（Finding 3） | 視 callback nullify 策略可能微調，亦可能不動 |
| `MidiImportActivity.kt` | ✅ 本輪主戰場（Finding 3） | callback nullify |
| `ImeServiceMisc.kt:415-422` | ⚠️ **不是主戰場，但本輪 side-effect 路徑** | `remotePanel?` 呼叫點 — null 化後行為改為短路，**不需改 `.kt`**，因 `?.apply` 已 safe-call |

**CCB 必須注意**：`ImeServiceMisc.kt` 與 `YukiBoardIMEService.kt:437-438` 本身皆使用 `?.` safe-call，Finding 1 的修法**無需動這兩處**。若 CCB 在這兩處另行加防護邏輯，**屬擴界**，需先回報。

#### II-A-3. 禁止檔案（擴界即違 gate）

- `UserDictionary.kt`（Finding 4 禁區）
- `InputRouter.kt`
- `SettingsActivity.kt`
- `CloudSyncManager.kt`
- 所有 Sprint 7 integration 交付物（`AppHttpClient.kt` / `InsightApiClient.kt` / `UserSessionRepository.kt` 等）
- `MusicPanelView.kt`（本輪屬觀察點，非主戰場）
- `ClawPanelView.kt`（不改 API，只消費）
- `ImeSessionCoordinator.kt` / `imeCoordinator.uiState` 定義類

### II-B. 技術審核重點（最小安全策略）

#### II-B-1. Finding 1 技術重點

**推薦修法**：

```kotlin
// ImePanelBindings.kt:73-92 clearInputModeBindings() 尾部追加
themePanel = null
remotePanel = null
```

**不推薦**：
- ❌ 在 panel 本體加 detach listener 主動清 slot — 屬架構整理，超出 bugfix 範圍
- ❌ 改 `clearInputModeBindings()` 命名語意 — 影響 `clearActivePanelBindings()` wrapper

**自查重點**：
- `clearInputModeBindings()` 執行後，`themePanel` / `remotePanel` 是否真的 null（單測或手動 trace）
- `YukiBoardIMEService.kt:437-438` 與 `ImeServiceMisc.kt:415-422` 修後行為：panel 關閉期間廣播 / 狀態更新不再觸碰 view

#### II-B-2. Finding 2 技術重點

**推薦修法（方案 A — 新增 clawPanel slot）**：

```kotlin
// ImePanelBindings.kt 加欄位
var clawPanel: ClawPanelView? = null

// clearInputModeBindings() 結尾加
clawPanel = null

// ImeServicePanels.kt CLAW panel 建立處加
.also { imePanelBindings.clawPanel = it }

// YukiBoardIMEService.kt:135-139 改
internal val fraudDetectRunnable = Runnable {
    if (imeCoordinator.uiState.value.activePanel == PanelType.CLAW) {
        imePanelBindings.clawPanel?.triggerSkill("fraud_detect")
    }
}
```

**推薦修法（方案 B — 不動 bindings，從 factory 回查）**：
- 若 CLAW panel 不走 `imePanelBindings` slot，CCB 可自訂最小回查機制，但**需在 PR 說明為何不走 slot 方式**

**技術權衡**：
- 方案 A 與 Sprint 6-1/6-2/6-3 已建立的 binding slot 慣例一致（owner 在 service 層）
- 方案 A 需在 `clearInputModeBindings()` 一併清 null — 否則會製造**新的 stale reference bug**，與 Finding 1 同類
- 方案 B 若沒有明確 owner，則當前 SSOT 檢查 + 短路即可，不必然需要 slot

**自查重點**：
- `uiState.value.activePanel` 的型別是否正確（`PanelType`）
- SSOT 讀取是否在正確 thread（`StateFlow.value` 無 thread 限制，但若未來改為 `collect` 需重新評估）

#### II-B-3. Finding 3 技術重點

**推薦修法**：

```kotlin
// MidiImportActivity.kt onActivityResult 內：
// 原本：onMidiImported?.invoke(song)
val cb = onMidiImported
onMidiImported = null
cb?.invoke(song)

// MidiImportActivity.kt 加：
override fun onDestroy() {
    super.onDestroy()
    onMidiImported = null
}
```

**技術權衡**：
- 先取值再 null 的順序避免「invoke 中間 callback 被新設 overwrite」的理論 race（雖實務罕見）
- `onDestroy` 清 null 覆蓋「Activity 未走完 `onActivityResult` 就被 kill」的情境
- `onActivityResult` 裡 `RESULT_OK` 分支之外（取消 / 錯誤）也會走 `finish()` → `onDestroy` → 清 null，已涵蓋

**自查重點**：
- `onMidiImported = null` 時機必須在 `cb?.invoke(song)` 之前（避免 invoke 中的 Handler.post lambda 被 Activity 復活時踢回來的情況雖罕見但理論存在）
- `onDestroy` 的 null 化必須與 `onActivityResult` 的 null 化都存在（防 kill 情境）

### II-C. 交付自查清單（CCB 完成前對自己檢核）

1. `ImePanelBindings.clearInputModeBindings()` 是否已清 `themePanel` / `remotePanel`（+ 若採方案 A，`clawPanel`）？
2. 若採 Finding 2 方案 A，`clawPanel` 是否加入 `ImePanelBindings`、在 CLAW panel 建立處賦值、在 `clearInputModeBindings()` 清 null — 三處齊全？
3. `fraudDetectRunnable` 是否走 SSOT `imeCoordinator.uiState.value.activePanel`，而非 `panelContainer.getChildAt(0)`？
4. `MidiImportActivity.onMidiImported` 是否在 invoke 前先取值再 null、且 `onDestroy` 亦清 null？
5. `ImeServiceMisc.kt` / `YukiBoardIMEService.kt:437-438` 是否**未被動**（safe-call 已有，不需擴改）？
6. 本輪是否碰到 `UserDictionary` / `InputRouter` / `SettingsActivity` / `CloudSyncManager` / Sprint 7 任何交付物？（必須為零）
7. 本輪是否碰到 `MusicPanelView` / `ClawPanelView` / `ThemePanelView` / `RemoteKeyboardPanelView` 本體？（應為零，本輪只 null 化引用）
8. 本輪是否動到 `ImeSessionCoordinator` / `uiState` 定義類？（必須為零）
9. 三題各自是否能透過最小編輯達成？若任一題擴成 >30 行改動，需先回報。
10. `method.xml` / `AndroidManifest.xml` / build.gradle 是否被動到？（必須為零）

### II-D. 風險排序（cross-review 觀點）

| 題號 | 實際風險 | 修復風險 | 備註 |
| --- | --- | --- | --- |
| Finding 1 | 🟡 中（stale UI、reference retention，非 crash） | 🟢 極低（只加兩行 null） | 修復最單純，應優先完成 |
| Finding 2 | 🟡 中（靜默失效，使用者無提示） | 🟡 中（方案 A 需三處齊修；方案 B 設計要明確） | 建議選方案 A 一致性較高 |
| Finding 3 | 🟡 中（ghost update、stale callback） | 🟢 低（Activity 內改動） | 修復範圍小，須注意 onDestroy + invoke 兩處都要清 |

**cross-review 總體判定**：三題都是 well-scoped 的 lifecycle bug，可安全在單一 round 完成，互不耦合。CCB 實作順序建議：Finding 1 → Finding 3 → Finding 2（由易至難，方案 A 若採用涉及 bindings 改動放最後以確認其他題不受影響）。

---

## PART III — A / B / C / D / E

### A. 變更摘要（本文件）

- 產出 S8-1 CCA 本業 impact note + 代行 CodeX B 技術 review（單檔雙段，依 S8-0 gate 指示）
- PART I：三題 side-effect 地圖、跨題整合審核、6 項觀察但不處理項
- PART II：file scope review（允許 / 禁止 / 擴界警示）、Finding 1/2/3 技術重點、10 項自查清單、風險排序
- 本文件不動任何 `.kt`

### B. 風險

1. Finding 2 方案選擇（A 或 B）由 CCB 決定，**若選 A 則必須三處齊修**，漏一處會製造新 stale reference。
2. Finding 3 的 nullify 時機（invoke 前 vs 後）理論上有 race 差異，建議 invoke 前取值再 null。
3. `ImeServiceMisc.kt:415-422` 與 `YukiBoardIMEService.kt:437-438` 本輪**不應被動**，CCB 若擴界改動即違 gate。
4. `ImePanelBindings` 的 `bindCreation()` 與 `.also { ... = it }` 寫法不對稱是結構整理，本輪禁止擴。
5. 本 batch 三題修復都集中於 IME lifecycle 主路徑，建議 CCB 交付後由 CCA 本業（本輪後續 round 或 impl review）補一次實機手動測試觀察點清單。

### C. 本輪未做（刻意不做）

- Finding 4（`UserDictionary` shared-state）— 明列禁區
- `panelContainer.getChildAt(...)` 其他使用點掃描
- `MusicPanelView.addImportedSong` 本體 safe-guard
- `bgUpdateReceiver` lifecycle 整理（unregister 時機）
- `ImePanelBindings.bindCreation()` 與 slot 賦值不對稱修正
- Sprint 8-PRE backlog 其他候選（A-1/A-3/F-2/B-1 等）
- S8-2 / S8-3 排序（屬 `CodeX A` 判定）

### D. 架構決策（本輪不動，僅記錄）

本輪為 bugfix batch，無新增架構決策。以下為審查時的方向傾向，**非本輪決策**：

1. 若 CCB 採 Finding 2 方案 A，確立「CLAW panel 採 bindings slot 模式」— 與 Sprint 6 慣例一致。
2. MIDI callback static companion 仍維持，不改 instance 化 — 符合 Sprint 6-1 既有架構，改之屬 Sprint 9 級重構。
3. `clearInputModeBindings()` 名稱語意維持，不重命名為 `clearAllBindings()` — 避免上層 wrapper 語意漂移。

### E. `CodeX A` 審核請求

請 `CodeX A` 針對本文件判定：

- `READY` / `NOT READY` / `需調整的 scope / 紅線`

候選後續（**非本 CCA 指派**，僅為提示）：

- 若 `READY`：`S8-1-CCB-IME-LIFECYCLE-CALLBACK-BUGFIX` 開 CCB 實作
- CCB 交付後：`S8-1-CCA-IMPL-REVIEW`（CCA 本業 + 代行 CodeX B 雙段）
- `S8-2` 開門順序與主題由 `CodeX A` 決定，CCA 不自排

---

## 附：代行 `CodeX B` 身份聲明

- `CodeX` 額度至 2026-04-18 仍未恢復，本文件 PART II 由 CCA 代行 `CodeX B` 技術 review 視角
- 依 `docs/CCA_ACTING_CXB_STATUS_SPRINT8_2026-04-18.md` 代班安排執行
- 單檔雙段格式依 S8-0 gate 指示
- 一旦使用者通知 `CodeX` 額度恢復，CCA 立即退出代班

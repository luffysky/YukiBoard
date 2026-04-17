# Sprint 6 Settings-Side Impact Review（2026-04-18）

身份：Claude Code A

任務 ID：`S6-0-CCA-SETTINGS-IMPACT-REVIEW`

日期：2026-04-18

用途：

- 為 `Sprint 6-0` 交付 6 個 panel 與 `settings / provider / auth / sync` 的交集盤點
- 幫 `CodeX A` 判定「哪個 panel 先做，settings 線風險最低」
- 本文件只做 impact review，不提實作方案、不改 `.kt`

---

## 0. 盤點範圍與手法

### 0.1 盤點範圍

6 個高依賴 panel（依 `SPRINT6_7_EXECUTION_PLAN_2026-04-17.md` §1.3 次序）：

1. `THEME` — `ui/panel/ThemePanelView.kt`（1112 行）
2. `CLAW` — `ui/panel/ClawPanelView.kt`（683 行）
3. `REMOTE` — `ui/panel/RemoteKeyboardPanelView.kt`（223 行）
4. `HANDWRITING` — `ui/panel/HandwritingPanelView.kt`（795 行）
5. `MUSIC` — `ui/panel/MusicPanelView.kt`（1091 行）
6. `USER_DICT` — `ui/panel/UserDictionaryPanelView.kt`（1204 行）

### 0.2 settings 側對齊的交集軸

| 軸 | 代表檔案 |
|----|----------|
| Prefs | `ui/settings/YukiPreferences.kt`（347 行） |
| Auth | `ui/settings/section/AuthSettingsSection.kt` + `auth/SnowRealmAuthClient` |
| AI provider | `ui/settings/section/AISettingsSection.kt` + `AISettingsActivity.kt`（462 行）+ `claw/provider/AIProviderManager.kt` + `claw/provider/ApiKeySync.kt` |
| Sync | `ui/settings/section/SyncSettingsSection.kt` + `data/CloudSyncManager.kt` |
| Insight | `ui/settings/section/InsightSettingsSection.kt` |
| Theme data | `theme/ThemeManager.kt`（singleton）+ `ui/settings/section/ThemeSettingsSection.kt`（69 行）+ `ui/settings/section/BackgroundSettingsSection.kt`（1235 行） |
| Toolbar 開關 | `ui/settings/section/ToolbarSettingsSection.kt`（`toolbar_*` Bool prefs） |
| 字典 | `input/dictionary/UserDictionary.kt` + `ui/settings/section/InputBehaviorSection.kt`（匯出/匯入 UserDictionary） |
| Settings 主 Activity | `ui/settings/SettingsActivity.kt`（455 行，警戒 500 行） |

### 0.3 手法

- Grep `YukiPreferences` / `prefs\.` / `getSharedPreferences` / `AIProviderManager` / `CloudSync` / `UserSession` / `AuthRepository` / `ThemeManager` 在 6 個 panel 與各 settings section 的交集
- 比對 `CloudSyncManager` 的同步欄位與 panel 所操作的資料
- 比對 `SettingsActivity` 對 panel 相關資料的直接 callsite

---

## 1. 各 panel 與 settings 交集表

### 1.1 THEME panel — 交集 **高**

| 軸 | 交集點 | 說明 |
|----|--------|------|
| Prefs | `bgOverlayAlpha` / `bgOffsetX/Y` / `bgScaleMode` / `bgRotation` / `useCustomBackground` / `keyScalePercent` / `keyOpacity` / `transparentKeyboard(Alpha)` / `keyboardHeightRatio` / `customKeyLabelColor` / `getAllPerKeySizes()` / `getPerKeyWidth/Height` | `ThemePanelView` 第 324–785 行直接讀寫（**與 `BackgroundSettingsSection` 高度重疊**） |
| Prefs（動畫） | `toolbarAnimStyle` | 第 324 行 |
| Auth | 無直接依賴 | — |
| AI provider | 無 | — |
| Sync | **間接**：`insightThemeSyncEnabled` 會觸發 Insight 購買主題雲端同步（`InsightSettingsSection` line 43） | `ThemeManager` 被 `ImeThemeRenderDelegate` / `InsightSettingsSection` / `ThemeSettingsSection` 共用 |
| Theme data | `ThemeManager.getInstance(context)`（singleton）、`currentTheme` writer | 與 `ThemeSettingsSection` RadioGroup **共用同一 writer** |
| Toolbar 開關 | `toolbarTheme` | 只是顯示開關 |

**風險摘要**：

- `ThemePanelView` 與 `BackgroundSettingsSection`（1235 行，最肥的 section）**共用大量 bg / key / keyboard prefs 欄位**，SSOT 是 `YukiPreferences`；panel 邊界遷移時，若改動 writer 或 SSOT 抽離，會直接撞上 settings 頁。
- `ThemeManager` 是 `getInstance(context)` singleton，**panel 側與 settings 側同時 writer**，需要在 panel 邊界遷移前先明定 writer owner。
- `insightThemeSyncEnabled`、`insightSettingsBackup` 會造成跨裝置同步主題；若 panel 切換主題的寫入點改變，會影響 Insight sync payload。

---

### 1.2 CLAW panel — 交集 **最高**

| 軸 | 交集點 | 說明 |
|----|--------|------|
| Prefs | `YukiPreferences` 20+ 個 `ai*` 開關（`aiPrediction` / `aiWriter` / `aiTranslate` / `aiCupid` / `aiWhisper` / `aiGrammarCheck` / `aiToneAdjust` / `aiSummarize` / `aiMemeText` / `aiEmojiTranslate` / `aiQuickReply` / `aiCodeComplete` / `aiRegexGen` / `aiCommitMessage` / `aiYukiChat` / `aiMoodDiary` / `aiWeather` / `aiFraudDetect` / `aiFraudPassiveScan` / `aiFraudUrlScan`） | `ClawPanelView` 第 64 行宣告 `prefs: YukiPreferences?`；`AISettingsSection` 逐條對應 |
| Auth | **間接**：`ClawClient` 被設定為 available 與否與 token 有關 | panel 顯示 `remainingTokens` / `isLocked` / `onLockTapped` |
| AI provider | `ClawClient` / `AIProviderManager`（`getInstance`）/ `ApiKeySync` | `AISettingsActivity`（462 行）是 provider 設定主場，`AISettingsSection` 開按鈕導向 `AISettingsActivity` |
| Sync | 無直接；但 `CloudSyncManager` 會同步「偏好設定」整表（含 `ai*` 開關） | Sync payload 受 panel 修改 prefs 影響 |
| Insight | **間接**：`insightSurveyEnabled` / 訂閱等級（Plus/Pro/Enterprise）影響 AI skill 可用性 | `InsightSettingsSection` |
| Theme data | `theme: KeyboardTheme`（只讀） | 純 render |
| Toolbar 開關 | `toolbarClaw` | — |
| 其他 | `FraudReportManager` / `FraudRuleSync` / `FraudModelManager`（AISection 亦用） | — |

**風險摘要**：

- **Sprint 6 與 Sprint 7 最容易打架的 panel**。`ClawClient` / `AIProviderManager` / `ApiKeySync` 正是 Sprint 7-2「Session / Auth / Provider 狀態收斂」的目標。若先做 CLAW，極易混成「panel 邊界 + provider 收斂」雙題。
- `AISettingsActivity` 在 `SettingsActivity` line 219 被 `SpeechSettingsSection` 透過 `settingsCoordinator.getApiKey(OPENAI)` 取用——panel 邊界若改 API key provision 路徑，會同步影響 settings 側 speech / whisper。
- `Insight` 訂閱等級來自 `SnowRealmAuthClient.currentUser.plan`；CLAW panel 的 skill 可用與否與其綁定。

---

### 1.3 REMOTE panel — 交集 **最低**

| 軸 | 交集點 | 說明 |
|----|--------|------|
| Prefs | **panel 本體零直接 prefs**；只有 `remoteKeyboardEnabled`（line 159）與 `toolbarRemote`（line 171）由 service / toolbar 邏輯使用 | `RemoteKeyboardPanelView`（223 行）grep 0 命中 `YukiPreferences` / `prefs.` |
| Auth | 無 | — |
| AI provider | 無 | — |
| Sync | 無 | — |
| Insight | 無 | — |
| Theme data | `theme: KeyboardTheme`（只讀） | 純 render |
| Toolbar 開關 | `toolbarRemote` | — |
| 其他 | `BluetoothHidKeyboard` / `WifiKeyboardServer` 在 `service/ImeServicePanels.kt` 與 `YukiBoardIMEService.kt` 持有；panel 只消費 `ConnectionState` 與 `wifiUrl` | **panel 223 行是 6 個 panel 中最小** |

**風險摘要**：

- settings 端沒有 RemoteSettingsSection。整個 remote 的 prefs 僅一個 `remoteKeyboardEnabled` Bool，無權限 UI、無裝置 UI、無連線 UI；所有連線邏輯在 service 側。
- panel 邊界遷移 **不會撞 settings 線**；side-note 只需標「遷移後若 panel 新增 `remoteKeyboardEnabled` consume，再回 settings 對齊」。

---

### 1.4 HANDWRITING panel — 交集 **低**（但熱路徑最險）

| 軸 | 交集點 | 說明 |
|----|--------|------|
| Prefs | 無直接 `prefs.` 讀寫（grep 0 命中） | panel 只消費 `HandwritingEngine` |
| Auth | 無 | — |
| AI provider | 無 | — |
| Sync | 無 | — |
| Insight | 無 | — |
| Theme data | 未從 panel 直接引用 `KeyboardTheme`（需 Sprint 6-0 後續細查） | — |
| Toolbar 開關 | `toolbarHandwriting`（line 179） | — |
| 其他 | `HandwritingEngine` 在 `input/handwriting/` 下，panel 第 33 行直接依賴；引擎 lifecycle / candidate RMW 由 service 與 `ImePanelBindings.kt` 管 | **Sprint 6-0 plan 明定此 panel 為 Sprint 6 最危險，不准碰 candidate RMW 熱路徑** |

**風險摘要**：

- settings 端風險最低（只有 toolbar 顯示開關），但 **panel 本身的熱路徑**是 Sprint 6 最險。settings 線不會拖累，但也幫不上忙。

---

### 1.5 MUSIC panel — 交集 **最低**

| 軸 | 交集點 | 說明 |
|----|--------|------|
| Prefs | 無直接 `prefs.` 讀寫（grep 0 命中） | — |
| Auth | 無 | — |
| AI provider | 無 | — |
| Sync | 無 | — |
| Insight | 無 | — |
| Theme data | `theme: KeyboardTheme`（只讀） | — |
| Toolbar 開關 | `toolbarMusic`（line 180） | — |
| 其他 | `KeyboardSoundEngine` / `MidiImportActivity` / 匯入曲庫 `onImportMidi` callback 由 service 端處理 | — |

**風險摘要**：

- 與 REMOTE 同屬「settings 零交集」陣營。panel 邊界遷移不會擾動 settings 線。
- 唯一需要 side-note 的是「音效引擎」，但那屬 `KeyboardSoundEngine` 而非 settings。

---

### 1.6 USER_DICT panel — 交集 **中高**

| 軸 | 交集點 | 說明 |
|----|--------|------|
| Prefs | panel 本體零 `prefs.` 直讀；`UserDictionary` 內部用 SharedPreferences | panel 第 69 行持有 `userDictionary: UserDictionary?` |
| Auth | 無直接 | — |
| AI provider | 無 | — |
| Sync | **高**：`CloudSyncManager` 第 222 / 315 行 `UserDictionary(context)` 直接 new，`uploadAll()` / `downloadAll()` 同步「user_dictionary」 | 見 `data/CloudSyncManager.kt` |
| Insight | **間接**：`insightSettingsBackup` 若覆蓋字典資料亦生效 | — |
| Theme data | 未從 panel 直接引用（需 Sprint 6-0 後續細查） | — |
| Toolbar 開關 | `toolbarUserDict`（line 175） | — |
| Settings 直接 callsite | `SettingsActivity` **第 190 / 402 行** 在 `InputBehaviorSection` 的 `onExportDict` / `onImportDict` 與 `onActivityResult` 裡 **直接 `UserDictionary(this)`** | 是 `SettingsActivity` 裡少數還在 new 資料物件的地方 |

**風險摘要**：

- `UserDictionary` 實例在多處**各自 new**：panel（由 service 注入）、`SettingsActivity` 直接 new、`CloudSyncManager` 直接 new、`InputRouter` 使用。**沒有單一 owner**，Sprint 7-4「prefs / singleton / mutable field」收斂項目正是這類。
- panel 邊界若動字典 owner，**會同時撞 settings 匯入/匯出與 CloudSync 的 uploadAll/downloadAll**。

---

## 2. 「哪些需要同步改 settings」vs「只需 side-note」

### 2.1 **完全不需要動 settings**（side-note 即可）

| Panel | 理由 |
|-------|------|
| REMOTE | settings 側無對應 section；panel 本體不讀 prefs；所有 remote 連線邏輯在 service。 |
| MUSIC | settings 側無對應 section；panel 本體不讀 prefs。 |
| HANDWRITING | settings 側僅 `toolbarHandwriting` Bool；panel 本體不讀 prefs。 |

### 2.2 **需要 side-note + 同步檢視 settings（但本輪不改）**

| Panel | 需檢視的 settings 檔 |
|-------|--------------------|
| THEME | `ThemeSettingsSection`（writer 共用）+ `BackgroundSettingsSection`（prefs 高度重疊，1235 行）+ `InsightSettingsSection`（`insightThemeSyncEnabled`） |
| CLAW | `AISettingsSection`（20+ `ai*` 開關）+ `AISettingsActivity`（provider 主場）+ `InsightSettingsSection`（訂閱等級）+ `AuthSettingsSection`（token）+ `SpeechSettingsSection`（`settingsCoordinator.getApiKey(OPENAI)` 共用） |
| USER_DICT | `SyncSettingsSection`（`uploadAll/downloadAll` 含字典）+ `InputBehaviorSection`（匯出/匯入字典）+ `SettingsActivity`（第 190/402 行 `UserDictionary(this)`） |

### 2.3 **絕對禁止本輪同步改的 settings 線**

- `SettingsActivity.kt`（455 行，臨 500 警戒）
- `BackgroundSettingsSection.kt`（1235 行，已確認 0416 不切）
- `AISettingsActivity.kt`（462 行，provider 收斂屬 Sprint 7-2）
- `CloudSyncManager.kt`（屬 Sprint 7-3 gateway 收斂）
- `SnowRealmAuthClient` / `UserSessionRepository`（屬 Sprint 7-2）

---

## 3. 先做哪個 panel，settings 風險最低？

### 3.1 依 settings 交集深度排序（小→大）

| 名次 | Panel | 交集 | Settings 風險 |
|------|-------|------|--------------|
| 1 | **REMOTE** | 最低 | 幾乎零 |
| 1 | **MUSIC** | 最低 | 幾乎零 |
| 3 | HANDWRITING | 低（settings）／熱路徑高 | settings 零，但 panel 本身熱路徑是 Sprint 6 最險 |
| 4 | USER_DICT | 中高 | sync + InputBehaviorSection + SettingsActivity 直接 new |
| 5 | THEME | 高 | 與 `BackgroundSettingsSection`（1235 行）高度重疊 |
| 6 | **CLAW** | 最高 | 與 Sprint 7-2 provider 收斂 **高度重疊**，本輪不先做最安全 |

### 3.2 Claude Code A 對「第一個 panel」的 settings-side 建議

> **以 settings 風險評估，首選 `REMOTE` 或 `MUSIC`。**

**理由**：

1. **零 prefs 直讀**：panel 本體 grep 0 命中 `YukiPreferences`，不會撞 settings SSOT。
2. **零 settings section**：沒有 RemoteSettingsSection / MusicSettingsSection 需要對齊；`SettingsActivity` 不會因 panel 邊界遷移而膨脹。
3. **零 sync 交集**：不在 `CloudSyncManager` 的同步欄位內，不會擾動 Sprint 7-3。
4. **不會與 Sprint 7 撞題**：REMOTE 與 MUSIC 都不觸及 auth / provider / network client / session。

**兩者比較（純 settings 視角）**：

| 比較軸 | REMOTE | MUSIC |
|--------|--------|-------|
| Panel 行數 | 223（最小） | 1091 |
| settings 交集 | 零 | 零 |
| 非 settings 風險（service 耦合） | 中（Bluetooth/WiFi 在 service） | 中（soundEngine / MidiImport 在 service） |

> settings-side 無決勝點；選擇由 `CodeX B` 的 runtime risk matrix 決定。

### 3.3 不建議作為第一個 panel 的順序

- **CLAW 絕對不應第一個做**：會在 Sprint 6-1 就把 Sprint 7-2 的 provider 收斂議題拖進來。
- **THEME 不建議第一個做**：與 `BackgroundSettingsSection`（1235 行）prefs 高度重疊、`ThemeManager` singleton 雙 writer，邊界拉不乾淨。
- **USER_DICT 不建議第一個做**：`UserDictionary` 實例散落，會同時動 panel / sync / SettingsActivity / InputBehaviorSection 四處。
- **HANDWRITING 不建議第一個做**：settings 雖乾淨，但熱路徑是 Sprint 6 最險，不應當示範案例。

---

## 4. Settings 線本輪必須堅守的邊界（自我約束）

1. `SettingsActivity.kt` 本輪不得新增任何 section / router / UI。
2. `BackgroundSettingsSection.kt`（1235 行）本輪不得切分或改任何 prefs 欄位。
3. `AISettingsActivity.kt` / `AISettingsSection.kt` 本輪不得遷移 provider owner（Sprint 7-2 才做）。
4. `CloudSyncManager.kt` 本輪不得改 payload / gateway（Sprint 7-3 才做）。
5. `SnowRealmAuthClient` / `UserSessionRepository` 本輪不得改 session writer（Sprint 7-2）。
6. 所有 `toolbar_*` Bool prefs 本輪不得新增或改名。

---

## 5. 補充盤點：settings 側對 panel 的「隱性耦合點」

| 耦合點 | 檔案：行 | 性質 |
|--------|---------|------|
| `UserDictionary(this)` 直接 new | `SettingsActivity.kt` 第 190、402 行 | **強**，Sprint 7-4 收斂候選 |
| `settingsCoordinator.getApiKey(OPENAI)` | `SettingsActivity.kt` 第 219 行 | 與 CLAW / SpeechSettings 共用 API key |
| `ThemeManager.currentTheme` writer（同 singleton） | `ThemeSettingsSection.kt` 第 63 行、`ThemePanelView.kt` 第 936 行 | **強**，雙 writer |
| `insightThemeSyncEnabled` → Insight 同步購買主題 | `InsightSettingsSection.kt` 第 43 行 | 間接影響 THEME panel |
| `insightSettingsBackup` → 同步偏好設定 | `InsightSettingsSection.kt` 第 44 行 | 間接影響 CLAW（`ai*` 開關覆蓋） |
| `CloudSyncManager.uploadAll/downloadAll` 含 user_dictionary | `data/CloudSyncManager.kt` 第 222、315 行 | 與 USER_DICT 同步共用 |

---

## 6. 回交格式

### A. 變更摘要

- 新增 `docs/SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md`
- 未改任何 `.kt`
- 完成 6 個 panel 的 settings / auth / provider / sync 交集盤點、side-note 需求分類、第一 panel 之 settings-side 建議、settings 線本輪禁區

### B. 風險

- 本文件為 **settings 側 impact review**，不含 runtime / 引擎 / IME 熱路徑風險；最終第一 panel 決策仍需 `CodeX B` 的 panel risk matrix 與 `Claude Code B` 的 callsite audit 合流判讀。
- `THEME` 與 `BackgroundSettingsSection` 的 prefs 重疊面積大於預期，若 Sprint 6-1 落在 THEME，需提前定義 writer owner（屬 `CodeX B` 職責）。
- `CLAW` 與 Sprint 7-2 provider 收斂議題交疊嚴重，若誤排 Sprint 6-2 提前做，會直接洩題 Sprint 7。

### C. 未做的事

- 未盤點各 panel 的 runtime callback / lifecycle（屬 `Claude Code B` 的 `S6-0-CCB-CALLSITE-AUDIT`）。
- 未盤點各 panel 的 manager / engine 依賴圖（屬 `CodeX B` 的 `S6-0-CXB-RISK-MATRIX`）。
- 未碰 `.kt`；未提任何實作方案；未列 file scope 草案。
- 未對 `HANDWRITING` / `MUSIC` / `USER_DICT` panel 是否 import `KeyboardTheme` 做完整 include 驗證（settings 視角不需要，留給 `CodeX B` / `Claude Code B`）。

### D. 下一步建議

1. `CodeX B` 的 runtime risk matrix 與本文件的 settings 風險合流後，`CodeX A` 再做第一 panel 指派。
2. settings-side 視角建議：第一 panel **從 REMOTE 或 MUSIC 中擇一**，避免把 Sprint 7 收斂議題偷進 Sprint 6-1。
3. `THEME` 若被指定為第一 panel，**須先由 `CodeX B` 明定 `ThemeManager.currentTheme` writer owner 與 `bg*` prefs writer 邊界**，否則 panel 邊界會拉不乾淨。
4. `CLAW` 必須排到 `Sprint 7-2` 完成 provider/session 收斂之後，不得先做。
5. `USER_DICT` 必須排到 `Sprint 7-4` 的 SSOT 收斂討論之後。

### E. 請 `CodeX A` 判定

- 本輪 settings-side impact review 是否符合 `S6-0-CCA-SETTINGS-IMPACT-REVIEW` 交付標準？
- 首選 panel（REMOTE / MUSIC）之 settings 零風險判定是否正式採納？
- 是否允許 Claude Code A 本輪轉入待命，等 `CodeX A` 指派第一 panel 後再做對應 panel 的 settings side-note 深化？

---

（本文件僅做 impact review，完全未改 `.kt`。）

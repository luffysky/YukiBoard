# Sprint 6-1 MUSIC Settings Side-Note（2026-04-18）

回交人：Claude Code A

任務 ID：`S6-1-CCA-MUSIC-SETTINGS-SIDE-NOTE`

依據：`CODEXA_SPRINT6_GATE_REVIEW_2026-04-18.md` §5.3

## 0. 本輪本質

- `S6-1-CCA` 要求：`MUSIC` 實作對 settings 無影響則回交「無需 settings 變更」；如需補 side-note，可只動文件。
- 本文件即為該 side-note 正式回交，非實作文件。未改任何 `.kt`。

## 1. 審核對象

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeMusicPanelBinder.kt`（新增）
- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`（MUSIC 分支 + `launchMidiImportFor` extension）
- `docs/SPRINT6_1_MUSIC_IMPLEMENTATION_REPORT_2026-04-18.md`（Claude Code B 回交）

## 2. Settings-Side Grep 驗證

於 Sprint 6-1 本輪新增 / 修改範圍對 settings 相關 keyword 做 grep：

| Keyword | `ImeMusicPanelBinder.kt` | `ImeServicePanels.kt` diff 片段 |
|---------|:-----------------------:|:------------------------------:|
| `YukiPreferences` | 0 | 0 |
| `prefs.` | 0 | 0 |
| `SettingsActivity` | 0 | 0 |
| `AISettings` / `AISettingsActivity` | 0 | 0 |
| `AIProviderManager` | 0 | 0 |
| `SnowRealmAuthClient` / `AuthRepository` | 0 | 0 |
| `UserSessionRepository` | 0 | 0 |
| `CloudSyncManager` / `CloudSync` | 0 | 0 |
| `Insight` | 0 | 0 |
| `ThemeManager` | 0 | 0 |
| `BackgroundSettingsSection` | 0 | 0 |

驗證結論：本輪實作對 settings 軸零 import、零 callsite。

## 3. 對 0418 Impact Review 預判的核對

| Impact Review §1.5 預判（MUSIC） | Sprint 6-1 實作結果 | 核對 |
|-------------------------------|-------------------|------|
| Prefs 直讀：零 | Binder 零 `prefs.` | ✅ 一致 |
| Auth：零 | Binder 不引用 auth | ✅ 一致 |
| AI provider：零 | Binder 不引用 provider | ✅ 一致 |
| Sync：零 | Binder 與 MIDI 匯入路徑皆不觸 `CloudSyncManager` | ✅ 一致 |
| Insight：零 | 無 | ✅ 一致 |
| Theme data：只讀 `KeyboardTheme` | Binder 僅 `theme = value` 傳遞 | ✅ 一致 |
| Toolbar 開關：僅 `toolbarMusic` | 本輪未碰 `toolbar_*` 路徑 | ✅ 一致 |
| `KeyboardSoundEngine` / `MidiImportActivity` 在 service | 仍在 service；`launchMidiImportFor` 為 service extension，binder 不反向依賴 | ✅ 一致 |

Impact Review 預判與實作結果 1:1 對齊，無需修正該文件。

## 4. Settings 線是否需要同步修改？

**結論：不需要。**

理由：

1. Sprint 6-1 未新增、未修改、未刪除任何 prefs 欄位。
2. Sprint 6-1 未改變 `toolbarMusic` 的消費路徑（消費者仍在 `ToolbarView`）。
3. Sprint 6-1 未改變 `CloudSyncManager` 的同步 payload（MUSIC 資料本來就不在同步範圍內）。
4. Sprint 6-1 未改變 `AISettingsActivity` / `AuthSettingsSection` / `SyncSettingsSection` / `InsightSettingsSection` / `ThemeSettingsSection` / `BackgroundSettingsSection` 任何 UI、邏輯或 callsite。
5. Sprint 6-1 未觸及 `settingsCoordinator.getApiKey(OPENAI)` 路徑。
6. `SettingsActivity.kt` 維持 455 行（0416 已驗證的警戒值以下）。

## 5. Settings-Side 本輪禁區是否守住？

對照 `SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` §4 自我約束清單：

| 禁區 | 本輪狀態 |
|------|---------|
| `SettingsActivity.kt` 不得新增 section / router / UI | ✅ 未動 |
| `BackgroundSettingsSection.kt` 不得切分 / 改 prefs 欄位 | ✅ 未動 |
| `AISettingsActivity.kt` / `AISettingsSection.kt` 不得遷移 provider owner | ✅ 未動 |
| `CloudSyncManager.kt` 不得改 payload / gateway | ✅ 未動 |
| `SnowRealmAuthClient` / `UserSessionRepository` 不得改 session writer | ✅ 未動 |
| `toolbar_*` Bool prefs 不得新增或改名 | ✅ 未動 |

全數守住。

## 6. 殘留待觀察事項（僅紀錄，不改）

以下由 Claude Code B 報告 §B.2 自揭，屬既存議題，本輪 settings-side 不動：

1. **`MidiImportActivity` 靜態 callback**：與 settings 無關，屬 media stack 既有設計；若未來要移除，屬 Sprint 6 後續 panel 擴充或 Sprint 7-3 gateway 收斂議題。
2. **`MusicPanelView.Song / SongNote` 被 service 直接構造**：與 settings 無關，純 panel 內型別。
3. **`floating mode stale true`**：CodeX A 已明定 Sprint 6 獨立 bugfix round，settings 線不介入。

## 7. 回交格式

### A. 變更摘要

- 新增 `docs/SPRINT6_1_MUSIC_SETTINGS_SIDE_NOTE_2026-04-18.md`
- 未改任何 `.kt`
- 正式結論：**Sprint 6-1 MUSIC 實作對 settings 線無影響，無需 settings 變更**

### B. 風險

- 本輪 settings-side 無引入任何風險。
- 唯一需持續觀察的是：若 Sprint 6 後續 panel（特別是 THEME / CLAW / USER_DICT）採用同一 binder pattern 時，`ThemeManager` singleton、`AIProviderManager` singleton、`UserDictionary` 多處 new 的 SSOT 問題會浮上；MUSIC 因零耦合未暴露此類風險，後續不可據此誤判 THEME / CLAW / USER_DICT 同樣安全。

### C. 未做的事

- 未對 Claude Code B 實作做 runtime / smoke 驗證（不屬 settings-side 職責；由 CodeX A 決定是否授權手動 smoke）。
- 未對 Claude Code B 的 `binder pattern 沉澱為通用樣板`（§D.2）做 settings 側影響評估 — 若 CodeX A 決定採納該樣板，後續 THEME / CLAW / USER_DICT panel 輪次我將另出 settings-side 評估。
- 未碰任何 `.kt`、未新增實作、未修改 `SPRINT6_SETTINGS_IMPACT_REVIEW_2026-04-18.md` 本體（該文件的 MUSIC 預判已被本輪驗證，維持原樣）。

### D. 下一步建議

1. Sprint 6-1 MUSIC settings-side 視為完成；Claude Code A 重回待命位。
2. 若 CodeX A 判定 Sprint 6-1 = READY 並指派 Sprint 6-2 / 6-Bugfix：
   - 6-2 若指定為 REMOTE：預期 settings 側同樣零交集（依 Impact Review §1.3），仍會提交對應 side-note 做正式憑證。
   - 6-2 若指定為 THEME：settings 側交集最高（ThemeManager singleton 雙 writer + BackgroundSettingsSection 1235 行 prefs 重疊），我需在 panel 實作前先出一份 writer owner 提醒文件給 CodeX B 參考。
   - 6-2 若指定為 USER_DICT：我會在實作前補盤點 `SettingsActivity` 第 190 / 402 行 `UserDictionary(this)` 與 `CloudSyncManager` 的交互。
   - 6-Bugfix `floating mode stale true`：settings 側無交集，我只需 side-note 形式確認。
3. 若 CodeX A 採納 Claude Code B 的 binder pattern 為通用樣板，建議我在後續 panel 進場前逐 panel 出 settings-side 前置確認（避免 THEME / CLAW / USER_DICT 實作後才發現 settings 線需同步）。

### E. 請 `CodeX A` 判定

1. 本輪 side-note 是否符合 `S6-1-CCA-MUSIC-SETTINGS-SIDE-NOTE` 交付標準？
2. 是否授權 Claude Code A 重回待命位，等下一輪指派？
3. 是否採納「下一 panel 實作前由 Claude Code A 先出 settings-side 前置提醒」的節奏？（僅限 settings 交集高的 panel，如 THEME / CLAW / USER_DICT）

---

（本文件僅為 side-note 正式回交憑證，完全未改 `.kt`。）

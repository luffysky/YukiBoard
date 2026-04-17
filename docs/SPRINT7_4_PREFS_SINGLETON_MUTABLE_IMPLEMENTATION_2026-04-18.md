# Sprint 7-4 Prefs / Singleton / Mutable Field 真實來源收斂 — CCB Implementation

- 任務 ID：`S7-4-CCB-PREFS-SINGLETON-MUTABLE-IMPLEMENTATION`
- Owner：Claude Code B
- Type：implementation
- Scope：
  - `data/CloudSyncManager.kt`
  - `ui/settings/YukiPreferences.kt`
  - `input/smart/AdaptiveDictionary.kt`
  - `input/smart/PersonalDictionary.kt`
  - `input/smart/PhrasePredictor.kt`
  - `input/smart/SentencePredictor.kt`
  - `input/smart/StyleProfiler.kt`
  - `input/smart/TypingHabitTracker.kt`
  - `input/smart/CorrectionLearnManager.kt`
- Date：2026-04-18
- Build：`BUILD SUCCESSFUL in 16s` (`:app:compileDebugKotlin`)

---

## 1. 變更摘要

本輪核心動作：**把 schema 的真正 owner 從 `CloudSyncManager` 收回各 subsystem / `YukiPreferences`**。

`CloudSyncManager` 在 S7-3 後仍同時扮演 gateway + payload builder + prefs reader 三種角色，
S7-4 把 payload builder / prefs reader 的 schema knowledge 全數推回給各自擁有該 prefs 的類。

### 1.1 新增 `exportSnapshot()` / `importSnapshot()` API（7 個 subsystem）

每個 subsystem 在自己的類裡新增一對 snapshot API，schema 完全由擁有者決定：

| 檔案 | SharedPreferences 檔名 | Snapshot schema owner |
|------|------------------------|----------------------|
| `AdaptiveDictionary.kt` | `yukiboard_adaptive_dict` | 自己擁有 `_ad_index` / `ad_$word`（格式 `freq|timestamp`）的 merge 策略（remote freq 較高才覆蓋） |
| `PersonalDictionary.kt` | `yukiboard_personal_dict` | 自己擁有 `_pd_index` / `pd_$word`（格式 `tier|freq|timestamp`）的 merge 策略（tier > freq） |
| `PhrasePredictor.kt` | `yukiboard_phrase_predict` | 自己擁有 `_bi_keys` / `bi_$key` / `_tri_keys` / `tri_$key` 的序列化格式 |
| `SentencePredictor.kt` | `yukiboard_sentence_predict` | 自己擁有 `_sb_keys` / `sb_$key` / `_sp_keys` / `sp_$key`（`;;` 分隔符）的序列化格式 |
| `StyleProfiler.kt` | `yukiboard_style` | 自己擁有 `_suffixes` / `_global_style` / `_app_keys` / `app_style_$pkg` |
| `TypingHabitTracker.kt` | `yukiboard_habits` | 自己擁有 `_period_keys` / `period_$key` / `_app_keys` / `app_$key` |
| `CorrectionLearnManager.kt` | `yukiboard_correction_learn` | 自己擁有 `_cl_index` / `cl_$typed` |

每個 subsystem 新增：

```kotlin
import kotlinx.serialization.json.*

fun exportSnapshot(): JsonObject { /* 從自己的 prefs 讀出整個 schema */ }
fun importSnapshot(obj: JsonObject) { /* 依自己的 merge 規則寫回 prefs */ }
```

`AdaptiveDictionary` / `PersonalDictionary` 還特別把 merge 策略（freq 比較 / tier+freq 比較）搬回自己手裡，
`CloudSyncManager` 不再需要知道這兩個子系統的格式細節。

### 1.2 新增 `YukiPreferences.exportSettingsSnapshot()` / `importSettingsSnapshot()` / `exportThemeSnapshot()` / `importThemeSnapshot()`

| 方法 | 內容 |
|------|------|
| `exportSettingsSnapshot(): JsonObject` | 38 欄使用者可控設定（AI 開關 13 / 感測器 4 / 動畫音效 7 / 輸入 3 / 面板 3 / 遊戲 3 / 雪凜 5） |
| `importSettingsSnapshot(obj: JsonObject)` | 反序列化回 38 個 field setter |
| `exportThemeSnapshot(): JsonObject` | 8 個 theme 欄位（背景 6 + 鍵盤高度 + toolbar 動畫） |
| `importThemeSnapshot(obj: JsonObject)` | 反序列化回 theme setter |

Schema 完全由 `YukiPreferences` 自己決定；`CloudSyncManager` 不再持有 field 列表。

### 1.3 `CloudSyncManager.kt` 瘦身（830 → 432 行，-398 行，-48%）

**刪除的 18 個 helper**：

- `exportPrefsToJson()` / `importPrefsFromJson()`
- `exportThemeToJson()` / `importThemeFromJson()`
- `exportAdaptiveDict()` / `importAdaptiveDict()`
- `exportPersonalDict()` / `importPersonalDict()`
- `exportPhrasePredict()` / `importPhrasePredict()`
- `exportSentencePredict()` / `importSentencePredict()`
- `exportStyleProfile()` / `importStyleProfile()`
- `exportTypingHabits()` / `importTypingHabits()`
- `exportCorrectionLearn()` / `importCorrectionLearn()`

**改寫的 4 個 callsite**：

| 位置 | 舊 | 新 |
|------|----|----|
| `uploadSettings` | `val settingsJson = exportPrefsToJson()` | `prefs.exportSettingsSnapshot()` 直接內聯到 buildJsonObject |
| `downloadSettings` | `importPrefsFromJson(settingsObj)` | `prefs.importSettingsSnapshot(settingsObj)` |
| `uploadThemes` | `val themeJson = exportThemeToJson()` | `prefs.exportThemeSnapshot()` 直接內聯 |
| `downloadThemes` | `importThemeFromJson(themeObj)` | `prefs.importThemeSnapshot(themeObj)` |

**改寫的 14 個 subsystem callsite**：

| 舊 | 新 |
|----|----|
| `exportAdaptiveDict()` / `importAdaptiveDict(it)` | `AdaptiveDictionary(context).exportSnapshot()` / `.importSnapshot(it)` |
| `exportPersonalDict()` / `importPersonalDict(it)` | `PersonalDictionary(context).exportSnapshot()` / `.importSnapshot(it)` |
| `exportPhrasePredict()` / `importPhrasePredict(it)` | `PhrasePredictor(context).exportSnapshot()` / `.importSnapshot(it)` |
| `exportSentencePredict()` / `importSentencePredict(it)` | `SentencePredictor(context).exportSnapshot()` / `.importSnapshot(it)` |
| `exportStyleProfile()` / `importStyleProfile(it)` | `StyleProfiler(context).exportSnapshot()` / `.importSnapshot(it)` |
| `exportTypingHabits()` / `importTypingHabits(it)` | `TypingHabitTracker(context).exportSnapshot()` / `.importSnapshot(it)` |
| `exportCorrectionLearn()` / `importCorrectionLearn(it)` | `CorrectionLearnManager(context).exportSnapshot()` / `.importSnapshot(it)` |

Class KDoc 更新：
- 移除「payload builder / prefs reader 暫扮」字眼
- 明示 S7-4 收斂結果：本類回歸單一職責 = Supabase gateway + 排程（24h / 50 commits）
- `CloudSyncManager` 再也沒有 `yukiboard_*` SharedPreferences 直讀

---

## 2. 行為差異（需文件化的部分）

### 2.1 Snapshot 語意等價

各 export / import 方法的序列化格式、merge 策略、index key 合併規則，都從 `CloudSyncManager` **原封不動搬進 subsystem**：

| 面向 | 舊 (in CloudSyncManager) | 新 (in subsystem) | 差異 |
|------|--------------------------|--------------------|------|
| `AdaptiveDictionary` merge | remote freq > local freq 才覆蓋 | 同 | 無 |
| `PersonalDictionary` merge | tier 高者勝；tier 同則 freq 高者勝 | 同 | 無 |
| `PhrasePredictor` | bi_keys / tri_keys `,` 合併 distinct；entries 雲端覆寫 | 同 | 無 |
| `SentencePredictor` | sb_keys / sp_keys `;;` 合併 distinct；entries 雲端覆寫 | 同 | 無 |
| `StyleProfiler` | suffixes / global_style 覆寫、app_keys 合併、app_style_$pkg 覆寫 | 同 | 無 |
| `TypingHabitTracker` | period_keys / app_keys 合併、period_$key / app_$key 覆寫 | 同 | 無 |
| `CorrectionLearnManager` | cl 直接覆寫、_cl_index 合併 distinct | 同 | 無 |

**行為等價**，搬家不改語意。

### 2.2 `YukiPreferences` settings / theme snapshot 欄位對照

export / import 的欄位名、型別、數量完全等價於舊 `exportPrefsToJson` / `importPrefsFromJson`：

- 設定：38 欄（AI 13 + 感測器 4 + 動畫音效 7 + 輸入 3 + 面板 3 + 遊戲 3 + 雪凜 5）
- 主題：8 欄（use_custom_background / bg_overlay_alpha / bg_scale_mode / bg_offset_x / bg_offset_y / bg_rotation / keyboard_height_ratio / toolbar_anim_style）

Import 使用 `?.jsonPrimitive?.booleanOrNull?.let { prefs.xxx = it }` 三段安全取值，未知 key 被忽略，型別不對時不覆寫，**與舊路徑等價**。

### 2.3 Subsystem `init { loadAll() }` 的 in-memory / prefs 分離限制（留至 Sprint 8）

各 subsystem 在建構時會 `loadAll()` 把 prefs 讀進 ConcurrentHashMap / MutableMap。
`importSnapshot()` 目前**只更新 prefs，不 reload in-memory map**。

| 場景 | 行為 |
|------|------|
| `CloudSyncManager.downloadLearningData()` 中 `AdaptiveDictionary(context).importSnapshot(it)` | 會新建一個臨時 instance，該 instance `init` 時 `loadAll()` 讀了舊 prefs，然後 `importSnapshot()` 改寫 prefs |
| 同 process 中存在的**其他** AdaptiveDictionary instance（例如 SmartInputManager 持有者） | 不會自動 reload；下次 process 重啟才會讀到新 prefs |

這是 S7-4 特意保留的行為：bugfix（in-memory reload）屬 Sprint 8 範圍。`importSnapshot` 的 KDoc 已明示此限制。

### 2.4 Theme snapshot 包含 `keyboard_height_ratio`

`keyboard_height_ratio` 原本在 `exportPrefsToJson` 中沒有，但在舊 `exportThemeToJson` 中就已存在；S7-4 `exportThemeSnapshot` 把它原位保留。**行為等價**，無欄位消失、無欄位新增。

---

## 3. Compliance Check vs S7-4 gate

| 紅線 | 狀態 | 備註 |
|------|------|------|
| 不碰 `SnowRealmAuthClient.postAuth` 雙 `fetchMe` 行為整併 | ✅ 未動 | `SnowRealmAuthClient.kt` 未觸 |
| 不做 provider cache invalidate bugfix | ✅ 未動 | 未觸 `AIProviderManager.kt` |
| 不升 `InviteCodeManager` singleton | ✅ 未動 | 未觸該檔 |
| 不改 `YukiBoardIMEService.kt` | ✅ 未動 | 未觸該檔 |
| 不動 panel binder 系列 | ✅ 未動 | 未觸 panel / section 檔 |
| 不動 Sprint 8 檔 | ✅ 未動 | 僅在 scope 內的 9 檔 |
| 不順手修 bug | ✅ 遵守 | `init { loadAll() }` 與 `importSnapshot` 的 in-memory 不同步問題**原地保留**（Sprint 8） |
| `CloudSyncManager` 的 prefs 直讀搬遷 | ✅ 完成 | 14 處 prefs 直讀 + 7 個 subsystem pref schema 全搬回 owner |
| singleton owner 是否清楚 | ✅ 達成 | schema owner = 擁有該 SharedPreferences 的類，不再由 gateway 持有 |
| mutable mirror / dual source-of-truth 是否真的收斂 | ⚠️ 部分 | prefs 面的收斂完成；但 subsystem 的 in-memory ConcurrentHashMap 與 prefs 的雙軌關係**未改**（按 gate 要求 bugfix 留 Sprint 8） |

### 與 S7-4 CodeX A 裁決

> READY — S7-3 已完成，gateway owner 已先收斂；現在可安全進入 prefs / singleton / mutable source-of-truth 收斂；bugfix 題仍留給 Sprint 8。

- ✅ prefs 層 source-of-truth 收斂完成
- ✅ singleton 層：schema owner 已收回 subsystem，不再由 gateway 借用
- ⚠️ mutable mirror（in-memory map）層：按 gate 指示**只做收斂、不修 bug**

---

## 4. 回交（A / B / C / D / E）

### A. 變更摘要

- 修改 9 檔：
  - `app/src/main/java/com/snowrealm/yukiboard/data/CloudSyncManager.kt` — 瘦身 830→432 行（-398，-48%），刪除 18 helper，4 個 prefs callsite + 14 個 subsystem callsite 改走擁有者 snapshot API
  - `app/src/main/java/com/snowrealm/yukiboard/ui/settings/YukiPreferences.kt` — 新增 `exportSettingsSnapshot()` / `importSettingsSnapshot()` / `exportThemeSnapshot()` / `importThemeSnapshot()`
  - `input/smart/AdaptiveDictionary.kt` — 新增 snapshot API + freq-based merge 搬回
  - `input/smart/PersonalDictionary.kt` — 新增 snapshot API + tier/freq merge 搬回
  - `input/smart/PhrasePredictor.kt` — 新增 snapshot API
  - `input/smart/SentencePredictor.kt` — 新增 snapshot API（`;;` 分隔）
  - `input/smart/StyleProfiler.kt` — 新增 snapshot API
  - `input/smart/TypingHabitTracker.kt` — 新增 snapshot API
  - `input/smart/CorrectionLearnManager.kt` — 新增 snapshot API
- 新增骨架 / 介面：
  - 標準化的 `exportSnapshot(): JsonObject` / `importSnapshot(obj: JsonObject)` pair：每個擁有 SharedPreferences 的 subsystem / Pref facade 都有
  - `CloudSyncManager` 從三角色（gateway + payload builder + prefs reader）降回單角色（gateway + 排程）

### B. 風險

- **低風險**：
  - 序列化格式、merge 策略、index key 合併規則搬家後語意等價；上下行 payload 欄位 1:1 對應；未知 key 安全忽略
  - `YukiPreferences` 的 38+8 欄 snapshot API 沒有擴大欄位，沒有減少欄位
- **已明示、非本輪風險（留 Sprint 8）**：
  - Subsystem 的 `init { loadAll() }` + `importSnapshot` 不同步：同 process 中既有 instance 的 in-memory map 不會自動 reload，要 process 重啟才會讀到雲端下載的新 prefs（KDoc 明示）
  - `CloudSyncManager.downloadLearningData` 每次 new 一個臨時 `AdaptiveDictionary(context)` 等，屬短命 instance 模式；無 leak 風險，但有輕微 GC 抖動（可接受，修法屬 Sprint 8）
  - 無新的 breaking change；雲端 schema 完全兼容舊版

### C. 未做（主動縮面）

- ❌ 不改 `SnowRealmAuthClient.postAuth` 雙 `fetchMe`（Sprint 8）
- ❌ 不修 provider cache invalidate bugfix（Sprint 8）
- ❌ 不升 `InviteCodeManager` singleton（S7-0 邊界，Sprint 8 或後續）
- ❌ 不動 `YukiBoardIMEService.kt`（禁區）
- ❌ 不動 panel binder 系列（禁區）
- ❌ 不改 subsystem 的 in-memory ConcurrentHashMap / mutable mirror 與 prefs reload 同步問題（Sprint 8 bugfix）
- ❌ 不把 subsystem `init { loadAll() }` 改成 lazy / 按需重載（Sprint 8）
- ❌ 不動 `SettingsActivity` 4 處 reach-in（S7-0 邊界）
- ❌ 不合併 Sprint 8 bugfix 題目進本輪

### D. 下一步建議（候選，不自排）

可能的 Sprint 8 / 後續方向（由 `CodeX A` 裁決，`Claude Code B` 不自排）：

1. Subsystem 的 in-memory map 與 prefs 的 reload 同步機制（`importSnapshot` 後強制 reload）
2. `SnowRealmAuthClient.postAuth` / `fetchMe` 整併（Sprint 8 bugfix）
3. `CloudSyncManager.resolveVerifiedUserId` / `SettingsScreenCoordinator.verifySessionAsync` 雙 fetchMe 整併（Sprint 8 bugfix）
4. `AIProviderManager.setSelectedModel` cache invalidate（Sprint 8 B-4）
5. `SnowRealmAuthClient.fetchMe` 改走 `InsightApiClient` gateway（Sprint 8 或後續）
6. `YukiPreferences` 38 個 field → `SettingsRepository` 分層（Sprint 8 或後續，非 bugfix）

### D1. 架構決策

- 建立契約：**任何擁有 SharedPreferences 檔的類，自己持有 `exportSnapshot()` / `importSnapshot()`**，schema 不對外洩漏。新增 snapshot 需求的 subsystem 必須在自己的類裡實作，不得回退到 `CloudSyncManager` 擴欄位
- 建立契約：`CloudSyncManager` 只做 Supabase gateway + 排程，**不再持有任何 schema knowledge**；未來新增同步項目 = 新 subsystem 自己提供 snapshot API + `CloudSyncManager` 單行 callsite
- `YukiPreferences` 的 settings / theme 欄位新增 = 自動納入 snapshot API（只要新增 field 並補進 export/import），不需動 `CloudSyncManager`

### E. CodeX A 審核需求/結論

- 請 CodeX A 判定以下：
  1. `S7-4` 的最小安全收斂範圍（prefs schema owner 搬遷）是否達成
  2. §2.3 的 subsystem `init { loadAll() }` 與 `importSnapshot` 不同步限制留到 Sprint 8 bugfix 是否可接受
  3. `CloudSyncManager` 降回單一職責（gateway + 排程）是否可接受
  4. `exportSnapshot()` / `importSnapshot()` 作為跨 subsystem 的新介面契約（7 個 subsystem + 1 個 YukiPreferences）是否可接受
- 判定格式：`READY / NOT READY / LOCKED`
- `Claude Code B` 不自排下一 round；待 `CodeX A` 明確開門後再進 Sprint 8 或其他 round。

---

## 5. 編譯結果

```
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 16s
18 actionable tasks: 1 executed, 17 up-to-date
```

無新 warning / error。

---

## 6. 關鍵收斂指標

| 指標 | Before S7-4 | After S7-4 | Δ |
|------|-------------|------------|---|
| `CloudSyncManager.kt` 行數 | 830 | 432 | -398（-48%） |
| `CloudSyncManager` 持有的 SharedPreferences 檔 schema 數 | 8（1 prefs_yukiboard_settings + 7 subsystem） | 0 | -8 |
| `CloudSyncManager` 的 private helper 數（export/import） | 18 | 0 | -18 |
| 「schema 只有 owner 知道」的類數 | 0 | 8（7 subsystem + YukiPreferences） | +8 |
| Gateway / payload / prefs reader 多重角色 | 3 | 1（gateway + 排程） | -2 |

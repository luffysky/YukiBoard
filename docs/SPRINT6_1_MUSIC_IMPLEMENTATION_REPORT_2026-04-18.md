# Sprint 6-1 MUSIC Implementation Report（2026-04-18）

回交人：Claude Code B

任務 ID：`S6-1-CCB-MUSIC-IMPLEMENTATION`

依據：`CODEXA_SPRINT6_GATE_REVIEW_2026-04-18.md` 第 5.1 節

## A. 變更摘要

### A.1 新增檔案

- `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeMusicPanelBinder.kt`
  - `object ImeMusicPanelBinder`
  - 單一方法 `bind(context, theme, soundEngine, onImportMidiClick): MusicPanelView`
  - 負責 `MusicPanelView` 建立與 `theme / soundEngine / onImportMidi` 掛接
  - 不依賴 `MidiImportActivity`、不依賴 service、不依賴 `KeyboardSoundEngine` 內部
  - MIDI 匯入流程透過 `onImportMidiClick: (MusicPanelView) -> Unit` 注入，保持反向隔離

### A.2 修改檔案

- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
  - 新增 import：`ImeMusicPanelBinder`
  - `createHighDependencyPanelOrNull()` 的 `PanelType.MUSIC` 分支：
    - 原本 24 行（含內嵌 `onMidiImported` 設定、song 轉換、activity 啟動）
    - 收斂為 6 行 binder call
  - 新增 extension helper：`YukiBoardIMEService.launchMidiImportFor(panel: MusicPanelView)`
    - 集中處理 `MidiImportActivity.onMidiImported` static callback 設定
    - 集中處理 `MidiSong → MusicPanelView.Song / SongNote` 轉換
    - 集中處理 `startActivity` with `FLAG_ACTIVITY_NEW_TASK`
    - 行為與原本完全一致，只是從 panel 建立 lambda 內搬到獨立函式

### A.3 未改檔案（遵守禁區）

- `YukiBoardIMEService.kt` — 完全未動（`handleKeyPress()` 主語意、onCreate、lifecycle 皆未改）
- `MusicPanelView.kt` — 完全未動
- `MidiImportActivity.kt` — 完全未動
- `KeyboardSoundEngine.kt` — 完全未動
- `KeyboardView.kt` / `InputRouter.kt` — 完全未動
- 任意 settings 檔 — 完全未動
- 任意 Sprint 7 相關檔 — 完全未動

### A.4 MUSIC 分支收斂前後對照

**Before（`ImeServicePanels.kt` 內 24 行）**
```kotlin
PanelType.MUSIC -> {
    val p = MusicPanelView(this)
    p.theme = theme
    p.soundEngine = keySoundEngine
    p.onImportMidi = {
        com.snowrealm.yukiboard.ui.MidiImportActivity.onMidiImported = { midiSong ->
            val song = MusicPanelView.Song(...)
            android.os.Handler(...).post { p.addImportedSong(song) }
        }
        try {
            val intent = android.content.Intent(this, ...MidiImportActivity::class.java)
            intent.addFlags(...)
            startActivity(intent)
        } catch (_: Exception) {}
    }
    p
}
```

**After（`ImeServicePanels.kt` 內 6 行）**
```kotlin
PanelType.MUSIC -> ImeMusicPanelBinder.bind(
    context = this,
    theme = theme,
    soundEngine = keySoundEngine,
    onImportMidiClick = ::launchMidiImportFor,
)
```

## B. 風險

### B.1 本輪引入的風險

無新風險引入。行為與原本完全一致：

1. Binder 輸入輸出 signature 與原本 inline 寫法語意 1:1 對應
2. `onImportMidi` 被呼叫時的副作用（設 static callback、啟動 Activity、post 回主執行緒加入歌曲）皆原封不動移到 `launchMidiImportFor`
3. Compile check 通過（`:app:compileDebugKotlin` BUILD SUCCESSFUL）

### B.2 既存風險（本輪不處理）

以下為 `S6-0` callsite audit 已記錄、本輪 gate 明定不碰之項目：

1. **MidiImportActivity 靜態 callback**
   - `onMidiImported` 為 companion object `var`，屬 process-global 可被覆寫
   - 每次 `launchMidiImportFor` 都會重新賦值，因此多 panel 實例或 race 時只會以最後一次為準
   - 本輪維持現狀；若要移除 static callback 模式，需碰 `MidiImportActivity.kt`（禁區）

2. **`MusicPanelView.Song / SongNote` 被 service 直接構造**
   - `launchMidiImportFor` 仍持有 `MusicPanelView` 內部資料型別的 knowledge
   - 不算耦合 hotspot（只在 import 流程出現一次），但邊界非純粹
   - 若要完全隔離，需在 `MusicPanelView` 加 factory 方法（動到 `MusicPanelView.kt`，本輪不擴張）

3. **`floating mode stale true`**
   - 既存 bug，Sprint 6 明定暫不開門，本輪不動

### B.3 回歸風險

評估為低：

- 本輪無變更 panel 開關主幹（`handlePanelToggle` / `routeToggle` / `attachPanel`）
- 本輪無變更 panel container 注入、SSOT 讀寫、activePanel 邏輯
- 本輪無 touch input hot path（`handleKeyPress` / `applyInputResult` / `InputUiSync`）
- 唯一變動面為 MUSIC panel 自身建立流程，影響範圍 = MUSIC panel 開啟 + MIDI 匯入

## C. 未做的事

以下為 gate 未授權或刻意不做：

1. **未新增 `imePanelBindings.musicPanel` 欄位**
   - MUSIC 無 input mode、無 cleanup hook、無主題 listener，依現狀不需要 bindings slot
   - 若 Sprint 6-2 + 有擴充需求，再由 CodeX A 授權後加入

2. **未修改 `MusicPanelView.kt`**
   - Gate 沒禁，但保持「最小骨架」原則，Sprint 6-1 只做 service-side 邊界收斂
   - Panel view 內部未 audit 過，貿然動有擴張風險

3. **未移除 MidiImportActivity 的 static callback 模式**
   - 會動到 `MidiImportActivity.kt`（本輪明定禁改）

4. **未把 `launchMidiImportFor` 搬進 binder**
   - Binder 若反向持有 service/Activity 啟動能力，會變成 media stack 耦合點
   - 故把 Activity 啟動留在 service-side helper，binder 只做純粹 panel 建立

5. **未觸碰其他 5 個 panel（THEME / CLAW / REMOTE / HANDWRITING / USER_DICT）**
   - Gate 明定其餘 panel 均 `NOT READY`

6. **未執行 smoke / 裝置測試**
   - 本機無連接裝置；僅做 compile 驗證
   - 建議 CodeX A 判定是否需要手動 smoke（開 MUSIC panel → 切樂器 → 按 import → 回 panel 確認歌曲進列表）

## D. 下一步建議

### D.1 Sprint 6-1 審核後可接的方向（三選一，由 CodeX A 指定）

1. **Sprint 6-Bugfix Round：`floating mode stale true`**
   - 屬獨立面，MUSIC 收斂完成後可先插入 bugfix 驗證流程
   - 參考 `SPRINT6_PANEL_CALLSITE_SCOPE_AUDIT_2026-04-18.md` §8 共同禁區

2. **Sprint 6-2：下一個 panel 收斂**
   - 依 risk matrix 與 settings impact review，建議順序為：
     - REMOTE（設定交集低、熱路徑無耦合）
     - HANDWRITING（熱路徑 RMW 需謹慎，但 panel 自身邊界相對清楚）
     - USER_DICT / THEME / CLAW（耦合最高，留到流程驗證更成熟後）

3. **Sprint 6-1 追加：加 `ImePanelBindings.musicPanel` slot**
   - 若 CodeX A 認為 MUSIC 也應納入 bindings 體系以保持一致性
   - 但依目前 MUSIC 無 lifecycle hook 的事實，不推薦提前加

### D.2 Binder pattern 沉澱（僅供 CodeX A 參考，不在本輪實作）

本輪以 `object ImeMusicPanelBinder` 落地，形成可複用樣板：

- 若後續 REMOTE / THEME / HANDWRITING / CLAW / USER_DICT 也採用相同樣板，可逐 panel 取代 `createHighDependencyPanelOrNull` 內各分支
- 樣板特徵：
  - `object` 或 `class`（無狀態時用 `object`）
  - 只接收明確 dependency（context / theme / 其他 engine / callback）
  - 不反向依賴 service、不反向依賴 Activity 啟動能力
- Service-side 保留「邊界外耦合」helper（例：本輪 `launchMidiImportFor`）
- 此沉澱是否成為 Sprint 6 通用規範，請 CodeX A 判定

## E. 請 CodeX A 判定

### E.1 本輪 deliverable 是否符合 `S6-1-CCB-MUSIC-IMPLEMENTATION` 交付要求

交付要求逐項自評：

| 要求 | 狀態 |
| --- | --- |
| 1. MUSIC panel 建立邊界比目前更清楚 | ✅ 24 行 inline → 6 行 binder call + 獨立 helper |
| 2. service 端 MUSIC panel-specific 建立邏輯有最小收斂 | ✅ MidiImport 耦合從 lambda 內提升為命名函式 `launchMidiImportFor` |
| 3. `:app:compileDebugKotlin` 通過 | ✅ `BUILD SUCCESSFUL in 23s` |
| 4. 文件回交（A/B/C/D/E） | ✅ 本文件 |

### E.2 待 CodeX A 裁決項

1. **判定結果（擇一）**
   - `READY` → Sprint 6-1 視為完成，進入下一輪決策（6-Bugfix 或 6-2）
   - `NOT READY` → 請指出補強項
   - `LOCKED` → 請指出哪條底線被越線

2. **binder pattern 是否沉澱為 Sprint 6 通用樣板**
   - 若是，後續 REMOTE / THEME / HANDWRITING / CLAW / USER_DICT 本輪皆沿用此模板
   - 若否，請指定例外處理方式

3. **下一輪任務方向**
   - Sprint 6-Bugfix Round（`floating mode stale true`）
   - Sprint 6-2（下一個 panel，請指定）
   - 其他（請指示）

4. **是否需要手動 smoke**
   - 若需要，請授權裝置 smoke 項目；目前本機無裝置，需 luffysky 或 CodeX B 協助

以上。等 `CodeX A` 指示。

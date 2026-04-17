# CodeX A Sprint 6-1 Gate Review（2026-04-18）

身份：CodeX A

日期：2026-04-18

用途：

- 審核 `Sprint 6-1 MUSIC` 回交
- 判定 `Sprint 6-1` 是否完成
- 決定下一門是否開啟

## 1. 審核對象

已審核：

1. `docs/SPRINT6_1_MUSIC_IMPLEMENTATION_REPORT_2026-04-18.md`
2. `docs/SPRINT6_1_MUSIC_SETTINGS_SIDE_NOTE_2026-04-18.md`
3. `app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeMusicPanelBinder.kt`
4. `app/src/main/java/com/snowrealm/yukiboard/service/ImeServicePanels.kt`
5. `CodeX B` 的技術審核結論

## 2. 審核結論

```text
READY

Reason:
1. `Sprint 6-1` 僅在授權範圍內修改：
   - `ImeServicePanels.kt`
   - 新增 `ImeMusicPanelBinder.kt`
2. 未越界碰觸：
   - `YukiBoardIMEService.kt`
   - `KeyboardView.kt`
   - `InputRouter.kt`
   - `MusicPanelView.kt`
   - `MidiImportActivity.kt`
   - settings 線
   - Sprint 7 檔案
3. `MUSIC` 分支已從 inline 建立流程收斂成 binder call，符合本輪「最小邊界落地」目標。
4. `:app:compileDebugKotlin` 已通過。
5. `Claude Code A` settings-side 確認本輪對 settings 線零影響。
```

## 3. 本輪是否有越界

### 程式碼

- **沒有發現 code 越界**

### 文件

- 有一項 **非 blocking 的流程越界傾向**

說明：

- `docs/SPRINT6_1_MUSIC_IMPLEMENTATION_REPORT_2026-04-18.md` 的下一輪建議中，自行寫了後續 panel 順序。
- 這不構成 code 問題，也不影響本輪 `READY`。
- 但 **下一輪排序與開門權限只屬於 `CodeX A`**，執行者不可在回交文件中自行重排成既定路線。

正式要求：

1. 後續回交可提「候選方向」
2. 不可把候選方向寫成既定順序
3. 不可與既有 gate 文件衝突

## 4. 正式判定

### 已完成

- `Sprint 6-1 = COMPLETED`

### 下一門

- `Sprint 6-Bugfix Round = READY`

### 仍未開門

- `Sprint 6-2 panel round = NOT READY`
- `REMOTE = NOT READY`
- `THEME = NOT READY`
- `CLAW = NOT READY`
- `HANDWRITING = NOT READY`
- `USER_DICT = NOT READY`
- `Sprint 7 = NOT READY`

說明：

- 依今日目標，`floating mode stale true` 已排入 `Sprint 6`。
- 現在 `MUSIC` 這一門已過，適合先插入 bugfix round。
- panel 下一門等 bugfix round 審核完成後再開。

## 5. Sprint 6-Bugfix Round 任務指派

### 5.1 Claude Code B

任務：

- `S6-BUG-CCB-FLOATING-RESET`

目標：

- 修復 `floating mode stale true`

本輪允許修改：

- 與 floating mode reset 最小安全 scope 直接相關的 IME 檔案
- 可包含：
  - `YukiBoardIMEService.kt` 或其已拆出的 service extension 檔
  - 與 floating mode 狀態 writer 直接相連的最小 callback / lifecycle 接線檔

本輪禁止修改：

- `KeyboardView.kt`
- `handleKeyPress()` 主語意
- `applyInputResult()` 主語意
- panel 大範圍重構
- Sprint 7 integration 檔
- settings 檔

本輪要求：

1. 只修 reset 缺口，不擴成 floating system 重構
2. 明確指出真正 writer owner
3. compile 通過
4. 回交時必須標出：
   - 哪個 callback / lifecycle 之前漏掉 reset
   - 為何這次修補是最小 scope
   - 沒有碰哪些禁區
5. 不得自行在文件內宣告下一個 panel 順序

### 5.2 CodeX B

任務：

- `S6-BUG-CXB-FLOATING-SCOPE-REVIEW`

目標：

- 審核 floating reset 的最小安全 scope

本輪重點：

1. 確認 writer owner 是否正確
2. 確認沒有把 bugfix 擴成 state convergence 第二次重構
3. 確認沒有把 panel / container / remote / theme 題混進來

### 5.3 Claude Code A

任務：

- `S6-BUG-CCA-SIDE-NOTE`

目標：

- 確認此次 bugfix 對 settings 線是否零影響

要求：

1. 不改 `.kt`
2. 若無交集，回交「無需 settings 變更」

## 6. 下一輪提醒

以下規則從現在起再次重申：

1. `CodeX A` 只開當前這一門
2. 執行者回交時不可自行把後面 round 排成既定順序
3. 可以提建議，但必須明確標為「待 CodeX A 指定」
4. 若發現需要擴大 file scope，必須先回報，不可默擴


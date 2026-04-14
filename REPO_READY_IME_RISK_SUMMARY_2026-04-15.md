# Repo Ready IME 風險摘要（2026-04-15）

角色：`CodeX B`  
用途：作為 repo 前 IME 側的短版風險摘要，對齊 Sprint 3 Phase 1 邊界、目前完成項與仍保留的 runtime 風險

## 1. 本輪已完成

- `YukiBoardIMEService.kt` 已完成一批低風險薄切：
  - `ImeSessionHooks`
  - `ImePanelBindings`
  - `ImePanelCleanup`
  - `ImePanelHost`
  - `ImePanelCoordinator`
  - `ImePanelFactory`
  - `ImeVoiceController`
  - `ImeThemeRenderDelegate`
  - `ImeCandidateInteractionHandler`
  - `ImePanelRouter`
- `STICKER` 已進入 factory 路徑，不再列為下一輪待拆主題
- Sprint 3 Phase 1 正式決策已同步到文件：
  - `activePanel`
  - `isVoiceInputActive`
- `composingText / candidates` 已明確延後，不進 Phase 1

## 2. Phase 1 正式邊界

Phase 1 collect/consume 只允許：

- `activePanel`
- `isVoiceInputActive`

Phase 1 明確不做：

- `composingText` collect 化
- `candidates` collect 化
- `handleKeyPress()` 重構
- `applyInputResult()` 重構
- `KeyboardView.kt`
- `USER_DICT`
- `THEME / CLAW / REMOTE / HANDWRITING / MUSIC`

延後 `composingText / candidates` 的原因：

- 目前同步直寫 UI 的路徑仍然過多
- 現階段若改由 `uiState` collect 驅動，會有 race / 延遲風險
- 這兩項仍受 `handleKeyPress()` / `applyInputResult()` 同步時序約束

## 3. 文件一致性狀態

以下文件目前已對齊同一個 IME Phase 1 決策：

- [IME_STATE_CONVERGENCE_DRAFT.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/IME_STATE_CONVERGENCE_DRAFT.md)
- [BIG_FILE_REFACTOR_PLAN_0414.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/BIG_FILE_REFACTOR_PLAN_0414.md)
- [todo_list_0414_new.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/todo_list_0414_new.md)
- [IME_SMOKE_CHECKLIST.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/IME_SMOKE_CHECKLIST.md)
- [REPO_READY_EXECUTION_PLAN_2026-04-15.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/REPO_READY_EXECUTION_PLAN_2026-04-15.md)

## 4. 目前可接受的 repo 前狀態

目前可確認：

- compile 驗證已通過
- 靜態接線與文件邊界一致
- Phase 1 / Phase 2 邊界已明確
- smoke checklist 已存在

目前尚未完成：

- IME runtime smoke test
- Phase 1 collect 的實機驗證

## 5. Repo 前仍保留的 IME 風險

1. `runtime unverified`
   - 目前模擬器曾被 Android compatibility modal 阻塞，互動式 smoke 未完整跑完

2. `activePanel` collect 仍缺實機確認
   - 需確認 toolbar 高亮、panel toggle、panel close 後狀態恢復是否完全正確

3. `isVoiceInputActive` collect 仍缺實機確認
   - 需確認語音指示燈、overlay confirm/cancel、error/stop 路徑是否全部一致收斂

4. `composingText / candidates` 仍維持同步直寫
   - 這不是本輪缺陷，但代表 Phase 2 尚未開始，後續若誤提前 collect 化會有回歸風險

5. 高依賴 panel 仍留在延後區
   - `THEME / CLAW / REMOTE / HANDWRITING / MUSIC / USER_DICT` 均未展開，後續不得被誤認為 repo-ready 範圍內已處理

## 6. 給 CodeX A 的 repo 判定建議

若要以最低保護進 repo，可接受的判定基線是：

- compile 綠燈
- 文件一致
- checklist 齊
- 明確標註 runtime unverified
- 不再擴大到 Phase 2 或高依賴 panel

若要提高信心，再補一輪最小 runtime 驗證即可：

- `activePanel`
- `isVoiceInputActive`
- `STICKER`
- `GIF`
- `TRANSLATE`
- `DECRYPT`

## 7. CodeX B 結論

從 `CodeX B` 角度，目前 IME 已達到「文件與靜態邊界可交接、可整理、可進 repo-ready 判定」的狀態；這不代表 runtime 已完全驗證完成，真正剩下的仍是 runtime 驗證尚未補齊。

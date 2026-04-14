# Repo Ready 任務總表（2026-04-15）

目標：把目前重構中的工作，收斂到「可整理、可交接、可進 repo」的狀態。

這份文件不是下一個大重構提案，而是 **把現有成果收乾淨** 的執行清單。

---

## 1. Repo Ready 定義

要達到「可以 repo」至少要同時滿足：

1. `:app:compileDebugKotlin` 持續通過
2. 目前已拆出的骨架與文件一致，不再互相矛盾
3. Phase 1 狀態收斂路徑完成且邊界清楚
4. Settings 側與 IME 側都有最小 smoke checklist
5. 不再新增高風險範圍擴張
6. handoff / plan / todo 文件更新到同一版本
7. 可以清楚回答「這輪改了什麼、沒改什麼、剩什麼風險」

---

## 2. 本輪總原則

- 不為了再降行數而亂拆
- 不新增新功能
- 不移除既有功能
- 不改 `handleKeyPress()` 主語意
- 不改 `applyInputResult()` 主語意
- 不進 `USER_DICT`
- 不切 `KeyboardView.kt`
- 不主動搬 `THEME / CLAW / REMOTE / HANDWRITING / MUSIC`
- 每一輪都以 compile + 文件一致性 + smoke checklist 完整為優先

---

## 3. 三方分工

### 3.1 CodeX A

角色：監督 / 收口 / 最終 repo-ready 判定

本輪任務：

1. 監督三邊是否維持以下正式決策：
   - IME Phase 1 只接 `activePanel`
   - IME Phase 1 只接 `isVoiceInputActive`
   - `composingText / candidates` 延後
2. 確認下列文件內容一致：
   - `IME_STATE_CONVERGENCE_DRAFT.md`
   - `BIG_FILE_REFACTOR_PLAN_0414.md`
   - `todo_list_0414_new.md`
   - 本文件
3. 做一輪 repo-ready 健康檢查：
   - compile 狀態
   - 主要大檔是否維持在健康區
   - smoke checklist 是否齊
   - 是否有新的越界修改
4. 最後整理一份短版結案摘要，供 repo 前交接

完成條件：

- 四份核心文件沒有互相矛盾
- 三方任務狀態明確標成 `完成 / 未完成 / 延後`
- 可列出 repo 前剩餘風險，不超過 5 項

禁止事項：

- 不代替他們做各自實作
- 不新增新的大重構主題

---

### 3.2 Claude Code A

角色：Settings / Sync / Auth / AI provider 邊界收尾

本輪任務：

1. 確認 settings 側收斂工作正式收尾：
   - section 層不再有 `auth.fetchMe()`
   - section 層不再有 `AIProviderManager.getInstance()`
   - `SpeechSettingsSection` 持續使用注入式 `openAiApiKeyProvider`
2. 更新 `RULE/SETTINGS_SMOKE_CHECKLIST.md`
   - 把「已收斂 / 不需收斂 / 待觀察」標記寫清楚
   - 明寫 `AISettingsActivity` 是合理終端頁邊界
3. 做 settings 側 repo-ready 文檔收口：
   - 說清楚哪些依賴留在 coordinator
   - 哪些依賴留在 Activity/Section 是合理邊界
4. 不再追小型 provider 收斂；只做收尾與驗證整理

完成條件：

- `SpeechSettingsHelper` 不再直接 `getInstance()`
- `RULE/SETTINGS_SMOKE_CHECKLIST.md` 已更新成最終可交接版
- 可明確說明 `AISettingsActivity` 保留 provider manager 的理由
- `SettingsActivity.kt` 不反彈成新巨檔

禁止事項：

- 不碰 IME
- 不把新邏輯塞回 `SettingsActivity.kt`
- 不把 `FraudModelManager` 硬搬出 `AISettingsSection`

---

### 3.3 Claude Code B

角色：IME Phase 1 收尾與驗證文檔

本輪任務：

1. 收尾 Sprint 3 Phase 1：
   - `renderImeUiState(...)`
   - `startUiStateCollect()`
   - `uiStateCollectJob` lifecycle
2. 更新 `IME_SMOKE_CHECKLIST.md`
   - 把 Phase 1 檢查項目獨立標明
   - 明寫目前已 collect 驅動的只有：
     - `activePanel`
     - `isVoiceInputActive`
   - 明寫 `composingText / candidates` 仍是同步直寫 UI
3. 補一份簡短的 runtime 驗證重點
   - 面板高亮是否正確
   - 語音指示燈是否正確
   - collect 啟動與清理是否正確
4. 不再擴大到 Phase 2

完成條件：

- `IME_SMOKE_CHECKLIST.md` 可直接用來做回歸
- 文件明確標示 Phase 1 / Phase 2 邊界
- 沒有偷偷開始碰 `composingText / candidates`

禁止事項：

- 不碰 `handleKeyPress()`
- 不碰 `applyInputResult()`
- 不碰 `KeyboardView.kt`
- 不碰 `USER_DICT`

---

### 3.4 CodeX B

角色：IME 架構決策校正與 repo 前一致性維護

本輪任務：

1. 維持 `IME_STATE_CONVERGENCE_DRAFT.md` 為正式決策基準
2. 檢查 IME 相關文件是否仍一致：
   - `IME_STATE_CONVERGENCE_DRAFT.md`
   - `BIG_FILE_REFACTOR_PLAN_0414.md`
   - `todo_list_0414_new.md`
   - `IME_SMOKE_CHECKLIST.md`
3. 盤點是否仍有新的越界風險：
   - 是否有人提早進 `composingText / candidates`
   - 是否有人提早碰 `KeyboardView.kt`
   - 是否有人提早進高依賴 panel
4. 整理一份 repo 前 IME 風險摘要

完成條件：

- IME 決策文件持續一致
- Phase 1 / Phase 2 邊界描述一致
- 可列出目前 IME 仍未驗證的 runtime 風險

禁止事項：

- 不直接進 Phase 2 實作
- 不直接重開新的 panel factory 遷移主題

---

## 4. 執行順序

建議照這個順序做：

1. `Claude Code A`
   - 先把 settings 側收尾文件補成最終版
2. `Claude Code B`
   - 把 IME Phase 1 checklist / 驗證文檔補齊
3. `CodeX B`
   - 做 IME 文件一致性與風險摘要
4. `CodeX A`
   - 最後做總監督、compile 核對、repo-ready 判定

---

## 5. Repo 前最低驗證

### 5.1 必做

- `:app:compileDebugKotlin` 通過
- settings checklist 已存在且為最新
- IME checklist 已存在且為最新
- IME Phase 1 正式決策文件與 todo 一致

### 5.2 若模擬器仍不可用

可接受先以：

- compile 驗證
- 靜態接線驗證
- checklist 完整性
- 風險清單

作為 repo 前最低保護。

但必須在文件裡明寫：

- 哪些 runtime 尚未實測
- 哪些風險因此保留

---

## 6. Repo 前禁止新增的主題

以下主題全部延後，不在這輪展開：

- `composingText / candidates` collect 化
- `handleKeyPress()` 重構
- `applyInputResult()` 重構
- `KeyboardView.kt` 新主題
- `USER_DICT`
- `THEME`
- `CLAW`
- `REMOTE`
- `HANDWRITING`
- `MUSIC`
- 登入 bug 修復主題

---

## 7. 最終交付物

做到 repo-ready 時，至少要有這些交付：

1. `RULE/SETTINGS_SMOKE_CHECKLIST.md`
2. `IME_SMOKE_CHECKLIST.md`
3. `IME_STATE_CONVERGENCE_DRAFT.md`
4. `BIG_FILE_REFACTOR_PLAN_0414.md`
5. `todo_list_0414_new.md`
6. [REPO_READY_IME_RISK_SUMMARY_2026-04-15.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/REPO_READY_IME_RISK_SUMMARY_2026-04-15.md)
7. 一份 CodeX A 最終監督摘要

---

## 8. 結案標準

符合以下條件即可視為「做到可以 repo」：

- compile 綠燈
- settings 側收尾完成
- IME Phase 1 收尾完成
- 文件一致
- checklist 齊
- 高風險主題未被提早展開
- 剩餘風險有明確紀錄

如果缺其中任一項，就還不算正式 repo-ready。

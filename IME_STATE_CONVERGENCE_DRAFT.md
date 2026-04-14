# IME State Convergence Draft

日期: 2026-04-15  
角色: `CodeX B`  
用途: 作為 Sprint 3 前的正式接線決策草案，定義 `ImeSessionCoordinator.uiState` 的最小消費端落地範圍，避免後續協作在 helper 細節上重工

## 1. 結論先講

目前 `ImeSessionCoordinator.uiState` 已不再只是空骨架。

現況判斷：

- `uiState` 的第一批核心欄位已具備完整寫入端
- 真正缺的是「誰先讀它」
- Sprint 3 不應再討論要不要有 `uiState`
- Sprint 3 應直接決定第一批 UI 消費端接線順序

一句話決策：

> Sprint 3 Phase 1 只讓 `activePanel / isVoiceInputActive` 有正式 UI 讀取端；`composingText / candidates` 明確延後。

---

## 2. uiState 現況

目前 [ImeSessionCoordinator.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/app/ime/ImeSessionCoordinator.kt:19) 已持有以下 `ImeUiState` 欄位：

| 欄位 | 寫入端狀態 | 備註 |
|------|------------|------|
| `inputMode` | ✅ 已接 | 由 `syncImeCoordinatorFromRouter()` 同步 |
| `composingText` | ✅ 已接 | 由 `syncImeCoordinatorFromRouter()` 同步 |
| `candidates` | ✅ 已接 | 由 `syncImeCoordinatorFromRouter()` 同步 |
| `activePanel` | ✅ 已接 | 經 `updatePanelSelection()` / `ImePanelCoordinator` |
| `isFloatingMode` | ✅ 已接 | 已有 `setFloatingMode()` |
| `isPanelBehindKeyboard` | ✅ 已接 | 已有 `setPanelBehindKeyboard()` |
| `isVoiceInputActive` | ✅ 已接 | 經 `ImeVoiceController` start/stop |
| `remoteConnectionState` | ✅ 已接 | 已有 `setRemoteConnectionState()` |
| `isInGameMode` | ✅ 已接 | 已有 `setGameMode()` |
| `theme` | ✅ 已接 | lifecycle/theme refresh |
| `warningBanner` | ✅ 已接 | lifecycle 清空 |

現況真正的核心問題不是「缺寫入」，而是：

- `uiState` 目前幾乎沒有正式 UI collect/consume 路徑
- `YukiBoardIMEService` 仍直接推動多個 view 狀態

---

## 3. Sprint 3 的正式最小範圍

`CodeX B` 決定的第一批單一狀態來源最小落地範圍如下：

### 第一批必收

- `activePanel`
- `isVoiceInputActive`

理由：

- 這兩項已經有穩定寫入端
- 都是高頻且邏輯邊界相對清楚的 UI 可觀測狀態
- 都能直接降低 service 對 toolbar/voice UI 的裸寫
- 不需要立刻碰 `handleKeyPress()` / `applyInputResult()` 主語意

### `composingText / candidates` 明確延後

- `composingText`
- `candidates`

延後原因：

- 目前同步直寫 UI 的路徑過多，`YukiBoardIMEService` 內仍有多處直接更新 composing / candidate 顯示
- 若在 Phase 1 就改成由 `uiState` 消費，現階段容易產生 race condition 與延遲顯示風險
- 這兩項必須等同步路徑先收斂，再進入正式 consume 範圍

### 第二批可延後

- `isFloatingMode`
- `isPanelBehindKeyboard`
- `isInGameMode`
- `remoteConnectionState`

理由：

- 寫入端已存在
- 但目前主要影響的是 container/layout/特殊模式 UI
- 不適合作為 Sprint 3 第一刀

### 暫不作為 coordinator 主狀態

- `manualModeOverride`
- `swipeInputPending`
- `voiceFinalHandled`
- `sentenceBuffer`
- `lastFraudScanText`
- `stickerSyncDone`
- 各種 job / cache / engine instance

理由：

- 屬於 session flag、互動暫態、cache 或 integration handle
- 不是第一批 UI 真實來源

---

## 4. 正式讀取端接線方向

Sprint 3 建議採「局部消費端接線」，不是一次全面 collect。

### 第一批接線目標

| UI/接線點 | 目前來源 | Sprint 3 方向 |
|-----------|----------|----------------|
| `toolbarView.activePanel` | service field 直接指定 | 改為以 `uiState.activePanel` 為準 |
| `voice overlay / keyboard voice state` | callback 內分散處理 | 以 `uiState.isVoiceInputActive` 作為主要顯示依據 |

### 明確不在 Sprint 3 第一批做的

- 不直接讓整個 `KeyboardView.kt` 自己 collect `uiState`
- 不大改 `handleKeyPress()`
- 不大改 `applyInputResult()`
- 不把所有 prefs 同步強行塞進 coordinator

原因：

- 這會把 Sprint 3 從「接線」變成「重寫輸入主流程」
- 違反目前鎖定規則與你給 `CodeX B` 的定位

---

## 5. 建議的接線模式

正式建議不是讓 `KeyboardView` 自己管理 flow collect，而是：

1. `YukiBoardIMEService` 保留 collect owner 身分
2. 在 service 內新增一個非常薄的 consume 函式，例如：
   - `renderImeUiState(state: ImeUiState)`
3. `renderImeUiState` 第一批只做：
   - `toolbarView.activePanel`
   - `voice overlay / listening UI`
4. 原本 service 內分散的直接 view 更新，逐步改成：
   - 先寫 coordinator
   - 再由 `renderImeUiState()` 消費

這樣的好處：

- collect owner 還是在 service，風險低
- 不需要本輪切 `KeyboardView.kt`
- 可以避免讓多個 UI 元件各自直接碰 flow

---

## 6. 哪些東西先保留 service 直控

Sprint 3 第一批明確保留以下項目不收：

- `prefs -> view` 大量樣式/外觀同步
- `theme` 初始化時的複合 render 流程
- 高耦合 panel 內部狀態
- `remote / handwriting / claw / theme` 這類 integration-heavy panel 的內部控制

原因：

- 這些不是 `uiState` 第一批要解的問題
- 先硬收只會讓 service 轉成另一種更難懂的大 coordinator

---

## 7. 高依賴 panel 風險排序更新

以 Sprint 3 的接線與整合風險來看，保留在 `showPanel()` fallback 內的高依賴 panel，風險排序更新如下：

1. `THEME`
2. `CLAW`
3. `REMOTE`
4. `HANDWRITING`
5. `MUSIC`
6. `USER_DICT`

說明：

- `THEME` 直接碰背景、預覽、prefs、input view 重建，平台/UI 交錯最多
- `CLAW` 同時牽涉 AI、session、floating result、invite/auth 導流
- `REMOTE` 牽涉藍牙/Wi-Fi 連線與外部裝置狀態
- `HANDWRITING` 牽涉模型準備、辨識 callback、候選回寫
- `MUSIC` 有外部 activity / import 流程
- `USER_DICT` 本身也高風險，但目前已明確列入「不進實作」，所以僅保留設計風險判斷

這個排序的用途是：

- 提醒 `Claude CodeB` 不要誤以為剩下只是單純 helper 搬遷
- 讓後續 Sprint 3/4 有一致的風險邊界

---

## 8. CodeX B 的正式決策

### 決策 A

Sprint 3 第一批單一狀態來源只收：

- `activePanel`
- `isVoiceInputActive`

### 決策 B

Sprint 3 讀取端不進 `KeyboardView.kt`，先由 `YukiBoardIMEService` 作為唯一 consume owner。

### 決策 C

`prefs -> view` 同步與高依賴 panel，不納入 Sprint 3 第一批接線範圍。

### 決策 D

高依賴 panel 本輪只做風險排序，不進入實作。

---

## 9. 給下一輪 Codex / Claude CodeB 的 handoff

下一輪若由 Codex 主導 Sprint 3，建議直接從以下步驟開始：

1. 在 `YukiBoardIMEService` 內加一個薄的 `renderImeUiState(state)` consume 入口
2. 第一批只接：
   - `activePanel`
   - `isVoiceInputActive`
3. `composingText / candidates` 先維持延後，原因是目前同步直寫 UI 路徑過多，現階段有 race / 延遲風險
4. 把 service 內最外層、非核心語意的直接 view 更新，逐步改成：
   - 寫 coordinator
   - 再由 consume 入口更新 UI

`Claude CodeB` 若要配合，適合做的是：

- 補 `IME_SMOKE_CHECKLIST.md` 的 runtime unverified 標記
- 補 consume 入口草稿
- 盤點目前仍直接寫 view 的地方

不適合做的是：

- 再追零碎 helper 搬遷
- 提前進 `USER_DICT`
- 提前碰 `KeyboardView.kt`

---

## 10. 本輪明確未做

- 不修改 `handleKeyPress()` 主語意
- 不修改 `applyInputResult()` 主語意
- 不進 `USER_DICT`
- 不切 `KeyboardView.kt`
- 不直接和 `Claude CodeB` 搶改同一批 `app/ime/*` helper

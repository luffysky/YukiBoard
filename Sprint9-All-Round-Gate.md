# Sprint 9 All Round Gate

身份：CodeX A

用途：

- 作為 `Sprint 9` 唯一總 gate 文件
- 本 sprint 專門處理全專案掃描後的新一批 bug / cross-project integration defect
- 這次採 `parallel workstream` 模式：先一次排好全部工作包，但每個 workstream 必須有獨立 write scope
- 已完成項目保留並加刪除線，不刪除

---

## 全域規則

1. 本 sprint 允許平行執行，但每個 workstream 必須是獨立 write set。
2. 不得跨 workstream 偷改他人檔案；若需擴 scope，先回報 `CodeX A`。
3. 每個 workstream 完成後先回交，由 `CodeX A` 個別審核。
4. 某條線過關，不代表整個 sprint 自動結案；以全部指定 workstream 過關為準。
5. `Insight` / `YukiBoard` 交界題優先按契約與 runtime correctness 處理，不順手做結構重構。

---

## 本 sprint 來源

來自本輪全專案掃描與 `Insight` / `YukiBoard` 交界複查，正式納入的主題：

1. `InsightApiClient.stream()` SSE 切包解析不安全
2. `InviteCodeManager` 多 instance 下的 daily token consume 非原子
3. `CloudSyncManager.downloadLearningData()` 後長生命週期 smart subsystem 不自動 reload
4. `SnowRealmAuthClient` 手刻 JSON body，輸入含跳脫字元時可能送出 malformed JSON

---

## Workstream S9-A：Insight SSE Stream Fix

### 狀態

- `COMPLETED`

### 目標

- 修 `InsightApiClient.stream()` 的 SSE 解析
- 正確處理 chunked / partial line / partial JSON
- 不改 ai-proxy 協定，不擴到 provider business logic

### Write Scope

- `data/InsightApiClient.kt`
- 如有必要，可動最小相關 caller 測試/文件，但不得碰 `CloudSyncManager.kt` / `SnowRealmAuthClient.kt`

### 分工

- `Claude Code B`
  - 任務 ID：`S9-A-CCB-INSIGHT-SSE-FIX`
  - 負責實作
- `CodeX B`
  - 任務 ID：`S9-A-CXB-INSIGHT-SSE-TECH-REVIEW`
  - 負責技術審核
- `Claude Code A`
  - 任務 ID：`S9-A-CCA-INSIGHT-SSE-IMPACT-NOTE`
  - 負責 side-effect / integration note
- `CodeX A`
  - 負責 gate

### 回交結果

- `docs/SPRINT9_A_CCB_INSIGHT_SSE_FIX_2026-04-18.md`

### CodeX A 判定

```text
READY

Reason:
1. `InsightApiClient.stream()` 已真正改成 line-based SSE parsing，event boundary、multi-line `data:`、`[DONE]` 終止與 response close 都補到了。
2. write scope 嚴守單檔 `InsightApiClient.kt`，未撞到其他 workstream。
3. 就這條線原始 bug 而言，修補已完整到位。
```

---

## Workstream S9-B：InviteCode Atomicity Fix

### 狀態

- `COMPLETED`

### 目標

- 修 `InviteCodeManager.consumeTokens()` 在多 instance 下的非原子扣減
- 確保 daily usage reset + consume 在正確同步語意下成立
- 不擴到 Z 幣 / TokenBudgetManager / AI provider 題

### Write Scope

- `auth/InviteCodeManager.kt`
- 如有必要，可動最小相關 caller 驗證點，但不得碰 `SnowRealmAuthClient.kt` / `ApiKeySync.kt`

### 分工

- `CodeX B`
  - 任務 ID：`S9-B-CXB-INVITECODE-ATOMICITY-FIX`
  - 負責實作與技術主責
- `Claude Code A`
  - 任務 ID：`S9-B-CCA-INVITECODE-IMPACT-NOTE`
  - 負責 impact note
- `Claude Code B`
  - 任務 ID：`S9-B-CCB-INVITECODE-CROSSCHECK`
  - 負責交叉檢查
- `CodeX A`
  - 負責 gate

### 回交結果

- `docs/SPRINT9_B_CXB_INVITECODE_ATOMICITY_FIX_2026-04-18.md`

### CodeX A 判定

```text
READY

Reason:
1. `usageLock` 已把 activate / reset / consume / revoke / daily-used read 收進同一個 process-wide critical section，核心 lost update 題基本解掉。
2. 本 workstream 原始 blocking bug 是多 instance 下的 lost update 與 reset/consume 交錯，這兩點已被修掉。
3. `getRemainingTokens()` 的 read-side 非同一快照問題目前保留為 non-blocking observation，不阻擋本線結案。
```

### Non-blocking Observation

- `getRemainingTokens()` 仍由 `getDailyTokenLimit()` + `getDailyTokenUsed()` 兩步組合，不是單一 atomic snapshot。
- 目前不影響本 workstream 原始 blocking bug 的 closure；若後續要把 read-side 也收成嚴格快照，可另開補強小門。

---

## Workstream S9-C：Learning Data Reload Fix

### 狀態

- `PARTIAL COMPLETE / LIBRARY LAYER READY / ACTIVATION PENDING`

### 目標

- 修 `downloadLearningData()` 後長生命週期 smart subsystem 不 reload 的問題
- 明確決定 reload 策略與 owner
- 不回頭污染 `Sprint 7-4` snapshot schema owner 收斂

### Write Scope

- `data/CloudSyncManager.kt`
- `input/smart/SmartInputManager.kt`
- 7 個 smart subsystem 中實際需要補 reload hook 的檔案

### 分工

- `Claude Code A`
  - 任務 ID：`S9-C-CCA-LEARNING-RELOAD-DESIGN-NOTE`
  - 先出 impact / design note，因這題跨 subsystem
- `Claude Code B`
  - 任務 ID：`S9-C-CCB-LEARNING-RELOAD-FIX`
  - 在 design note 被 `CodeX A` 接受後負責實作
- `CodeX B`
  - 任務 ID：`S9-C-CXB-LEARNING-RELOAD-TECH-REVIEW`
  - 負責技術審核
- `CodeX A`
  - 我直接參與這條線，負責切最終方案與 gate，不只審核

### 回交結果

- `docs/SPRINT9_C_CCA_LEARNING_RELOAD_DESIGN_NOTE_2026-04-18.md`
- `docs/SPRINT9_C_CCB_LEARNING_RELOAD_FIX_2026-04-18.md`

### CodeX A 判定

```text
NOT READY FOR CLOSURE

Reason:
1. library layer 已實作到位：7 個 subsystem `importSnapshot()` 後會 `loadAll()`，`SmartInputManager.applyCloudSnapshot()` 與 `CloudSyncManager.setSmartInputApplier()` 也都已落地。
2. 但 `setSmartInputApplier()` 在整個 codebase 仍無 caller，`downloadLearningData()` 實際上仍只會走 fallback throwaway 路徑，long-lived owner 不會被活化。
3. 因此這條線目前只能算 library layer 正確，還不能算 end-to-end bug 已完整修完。
```

### 補充判定

- `S9-C` 本體不退回重做，保留為已完成的 library-layer groundwork。
- 但 end-to-end activation 必須另開 micro-round 補上 caller wiring，完成後這條線才算真正結案。

---

## Workstream S9-E：SmartInputApplier Activation Wiring

### 狀態

- `COMPLETED`

### 目標

- 把 `CloudSyncManager.setSmartInputApplier(...)` 真正接到 long-lived `SmartInputManager`
- 讓 `downloadLearningData()` 在 IME alive 時走 long-lived 路徑，雲端 learning snapshot 能同 session 立即生效
- 保持 lifecycle 對稱：建立時註冊，銷毀時解除

### Write Scope

- `service/YukiBoardIMEService.kt`
- `app/ime/ImeSessionHooks.kt`
- 如有必要，可動最小相關 wiring owner；不得回頭改 `CloudSyncManager.kt` / 7 subsystem 主修補語意

### 分工

- `CodeX B`
  - 任務 ID：`S9-E-CXB-SMARTINPUT-ACTIVATION-WIRING`
  - 因 `Claude` 額度不足，本輪由 `CodeX B` 代班負責實作
- `CodeX A`
  - 任務 ID：`S9-E-CODEXA-SMARTINPUT-ACTIVATION-GATE`
  - 負責 scope 裁決、實際 code review 與 gate

### 回交結果

- `docs/SPRINT9_E_CXB_SMARTINPUT_ACTIVATION_WIRING_2026-04-18.md`

### CodeX A 判定

```text
READY

Reason:
1. `YukiBoardIMEService.onCreate()` 已真正註冊 `CloudSyncManager.setSmartInputApplier(inputRouter.smartInput::applyCloudSnapshot)`，`S9-C` 的 library layer 不再只是 dormant callback。
2. `ImeSessionHooks.DestroyCleanup` 已補 `unregisterSmartInputApplier`，`onDestroy()` 也對稱解除成 null，lifecycle 完整。
3. write scope 嚴守在 `YukiBoardIMEService.kt` 與 `ImeSessionHooks.kt`，未回頭污染 `CloudSyncManager` / 7 subsystem 主修補語意。
```

---

## Workstream S9-D：Auth JSON Body Safety Fix

### 狀態

- `COMPLETED`

### 目標

- 修 `SnowRealmAuthClient` 以字串插值手刻 JSON body 的風險
- 改為安全 JSON encode
- 不改 auth API 契約，不順手整併 `postAuth` / `fetchMe` 題

### Write Scope

- `auth/SnowRealmAuthClient.kt`

### 分工

- `CodeX A`
  - 任務 ID：`S9-D-CODEXA-AUTH-JSON-FIX`
  - 我直接負責這條線的實作與最終整合
- `CodeX B`
  - 任務 ID：`S9-D-CXB-AUTH-JSON-REVIEW`
  - 負責技術審核
- `Claude Code A`
  - 任務 ID：`S9-D-CCA-AUTH-IMPACT-NOTE`
  - 負責 impact note
- `Claude Code B`
  - 不介入此線，避免與 `S9-A` / `S9-C` 撞檔

### CodeX A 判定

```text
READY

Reason:
1. `SnowRealmAuthClient` 的 login / oauth / send-code / register body 已改成安全 JSON encode，不再用字串插值手刻 JSON。
2. compile 已通過。
3. 本條線只碰 `SnowRealmAuthClient.kt`，write scope 乾淨，且原始 bug 已修掉。
```

---

## Workstream 對撞檢查

- `S9-A` 只碰 `InsightApiClient.kt`
- `S9-B` 只碰 `InviteCodeManager.kt`
- `S9-C` 只碰 `CloudSyncManager.kt` / `SmartInputManager.kt` / 7 subsystem
- `S9-D` 只碰 `SnowRealmAuthClient.kt`

結論：

- 4 條線 write scope 互不重疊
- 可平行開工

---

## CodeX A 當前判定

```text
READY

Reason:
1. 這 4 題都已被正式掃描確認，不是口頭懷疑。
2. write scope 已拆開，現在可一次排工，不會互撞。
3. 我已直接加入 `S9-C` 與 `S9-D`，不只做 gate owner。
```

---

## Sprint 9 當前總狀態

- 已完成：
  - `S9-A` `Insight SSE Stream Fix`
  - `S9-B` `InviteCode Atomicity Fix`
  - `S9-C` `Learning Data Reload Fix`
  - `S9-D` `Auth JSON Body Safety Fix`
  - `S9-E` `SmartInputApplier Activation Wiring`
- `CodeX A` 當前判定：
  - `Sprint 9 = COMPLETED`

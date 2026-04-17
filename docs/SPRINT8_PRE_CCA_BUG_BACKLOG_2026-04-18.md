# Sprint 8-PRE Claude Code A 前置 Bug Backlog

- 身份：Claude Code A
- 時間：2026-04-18
- 適用 round：`S8-PRE` Sprint 8 前置準備（與 `S7-4` 並行）
- 對應 gate 任務：
  - 整理 settings / auth / provider / sync / lifecycle 類 bug 的 side-note backlog
  - 補技術 review 視角的前置盤點（CodeX B 額度不足期間）
- 審核對象：`CodeX A`
- 本文件性質：**候選整理，非最終 finding**

---

## 重要前提

1. 本文件**不宣告**任何項目已正式成立為 Sprint 8 bug。
2. 本文件**不改任何 `.kt`**。
3. 本文件**不排序** Sprint 8 round 順序，只做候選分類與 provenance 標記。
4. 最終 Sprint 8 bug round 清單以 `CodeX A` 於 `Sprint 7` 結束後的重掃結果（`S8-0`）為準，本文件僅為重掃時的輔助備忘。
5. 所有項目 provenance 追溯至 Sprint 7 各 round 已交付文件，皆為既有觀察，非新增 finding。

---

## 類別一覽

- A. Auth / Session
- B. Provider / AI Integration
- C. Sync / External Gateway
- D. Prefs / Singleton / Mutable Owner
- E. Lifecycle / IME Session
- F. Settings UI / Section 化
- G. Security / Fallback
- H. Integration Test 驗證項

---

## A. Auth / Session 類候選

### A-1. postAuth 雙 fetchMe（R-1）

- Provenance：S7-0 audit 列為 R-1；S7-2 CCA side-note 列為紅線禁區；S7-4 CCA side-note 列入 Sprint 8 backlog
- 現象：`SnowRealmAuthClient.postAuth()` 登入後內部呼叫 `/me`；`SettingsScreenCoordinator.verifySessionAsync()` 於設定頁打開時又打一次 `/me`
- 疑似影響：登入後設定頁顯示資料不一致、多打一次 HTTP、並發 race 可能導致 UI 顯示舊資料
- Sprint 8 處理建議：應由 `UserSessionRepository` 提供「登入後一次性快照」，兩端共用
- 跨 round 依賴：可在 `UserSessionRepository.getInstance` 後（S7-4 交付後）處理

### A-2. postAuth `parsePlan` 空字串 edge case

- Provenance：S7-0 audit；S7-4 CCA side-note 列入 backlog
- 現象：`parsePlan(raw: String?)` 對 `null` 與空字串 `""` 行為不同；空字串 raw 會回 `FREE`，若伺服器回 `plan: ""` 會被誤判為 FREE
- 疑似影響：付費會員顯示為 FREE、plan 相關 UI 誤判
- Sprint 8 處理建議：空字串視同 null 或走 strict validation

### A-3. `saveTokenDirectly` 後本地鏡像 stale

- Provenance：S7-0 audit；S7-2 CCA side-note observation
- 現象：`saveTokenDirectly` 寫入 encrypted prefs 後，`SnowRealmAuthClient` 記憶體本地鏡像欄位（如 `currentUser`）可能未同步更新
- 疑似影響：寫 token 後緊接的 `getCurrentUser()` 可能返回舊資料
- Sprint 8 處理建議：寫入後主動 invalidate 鏡像或強制 reload
- 跨 round 依賴：與 A-1 整併解可一次處理

### A-4. `UserSessionRepository` 建構子散佈（至 S7-4 結束）

- Provenance：S7-4 CCA side-note 已列為 S7-4 要處理項
- 現象：`UserSessionRepository(context)` 在多處 new（`CloudSyncManager.kt:59` 等），可能存在跨呼叫點狀態鏡像
- Sprint 8 處理建議：**若 S7-4 已完成 `getInstance` 化，本項可從 S8 候選中移除**；若未完成則升為 S8 bug

---

## B. Provider / AI Integration 類候選

### B-1. AIProviderManager cache invalidate（R-5 / B-4）

- Provenance：S7-0 audit 列為 R-5 / B-4；S7-2 CCA side-note 紅線禁區；S7-3 CCA side-note 列入 S8 backlog
- 現象：provider 切換（OpenAI → Anthropic）後，舊 provider cache（token / model list / config）未清
- 疑似影響：切換後仍使用舊 provider 行為、model 選單顯示錯誤
- Sprint 8 處理建議：`saveProviderSelection(type)` 時同步 invalidate 舊 provider cache

### B-2. `AISettingsActivity` section 化（邊界）

- Provenance：CLAUDE.md 永久禁區（不可擴 `SettingsActivity` / `AISettingsActivity` 本體）；S7-2 CCA side-note observation
- 現象：`AISettingsActivity` 本體仍單檔承載所有 provider UI 邏輯
- Sprint 8 處理建議：屬架構重構級，應走 Sprint 9 或專門 round，非 bugfix；本項建議**移出 S8 bug 清單**，標為 long-term refactor

### B-3. provider model list 空值處理

- Provenance：S7-3 exchange observation 的延伸 — provider 回傳 model list 為空時的 UI 行為未明確定義
- 疑似影響：UI 空白、預設值缺失
- Sprint 8 處理建議：列入 S8-0 重掃時驗證項

---

## C. Sync / External Gateway 類候選

### C-1. S7-3 exchange body 格式差異驗證

- Provenance：S7-3 implementation doc 明列未做；S7-4 CCA side-note 列入 S8-0 驗證項
- 現象：`/api/auth/supabase-token` exchange 的 body 從空 `ByteArray` 改為空 JSON `{}`；若 Insight 後端嚴格檢查 body，行為可能不同
- Sprint 8 處理建議：整合測試驗證，若後端不介意則無行動；若行為異常需回退或調整
- 性質：**驗證項，非 bug**（除非驗證失敗）

### C-2. `commitsSinceLastSync` 跨 process 不一致

- Provenance：S7-4 CCA side-note observation
- 現象：`commitsSinceLastSync` 是 `CloudSyncManager` 私有 `var`，在 IME service 被 kill/restart 時會歸零，可能導致同步觸發不及時
- 疑似影響：非關鍵同步延遲
- Sprint 8 處理建議：可選擇搬到 `SyncMetaRepository` 持久化，或維持現狀並在文件標明「盡力而為」

### C-3. Supabase token 刷新失敗重試策略

- Provenance：S7-3 CCA side-note observation
- 現象：`getSupabaseToken()` 若 exchange 失敗 return null，無退避 retry；下次同步時從頭再試
- 疑似影響：離線或間歇性網路問題下的同步穩定性
- Sprint 8 處理建議：列入 S8-0 重掃時觀察實際失敗頻率再決定是否加 retry

### C-4. `CloudSyncManager` / Payload schema 版本演進

- Provenance：S7-4 CCA side-note observation
- 現象：export/import payload 無 schema version 欄位；未來 schema 變更無版本對照
- 疑似影響：跨版本同步相容性風險
- Sprint 8 處理建議：加 `schemaVersion` 欄位 + 讀時相容判斷，屬預防性改動，非 bugfix

---

## D. Prefs / Singleton / Mutable Owner 類候選

### D-1. 7 smart subsystem new-per-call race

- Provenance：S7-4 CCA side-note；7 subsystem（`AdaptiveDictionary` / `PersonalDictionary` / `PhrasePredictor` / `SentencePredictor` / `StyleProfiler` / `TypingHabitTracker` / `CorrectionLearnManager`）各自 own 一個 SharedPreferences 檔，但本體非 singleton
- 現象：每次 `new AdaptiveDictionary(context)` 會建立新實例；若多處同時寫同一 prefs 檔，即使各自有 `@Volatile` / `@Synchronized`，仍可能 race
- 疑似影響：學習資料寫入遺失、統計值短暫不一致
- Sprint 8 處理建議：逐一 singleton 化（`getInstance(context)`）
- 跨 round 依賴：牽動 IME service 讀寫路徑

### D-2. `UserDictionary` new-per-call

- Provenance：S7-4 CCA side-note
- 現象：`CloudSyncManager.exportData()` / `importData()` 各 new 一次 `UserDictionary(context)`
- Sprint 8 處理建議：singleton 化

### D-3. `YukiPreferences` 38 欄位 API 擴散

- Provenance：S7-4 CCA side-note；非 bug，屬結構債
- Sprint 8 處理建議：**建議移出 S8 bug 清單，標為 Sprint 9 結構重構**

### D-4. `SupabaseTokenCache` 未封裝（若 S7-4 未處理）

- Provenance：S7-4 CCA side-note 列為 optional；若 CCB 本輪未封裝則會留到 S8
- 現象：`cachedSupabaseToken` / `supabaseTokenExpiry` 仍為 `CloudSyncManager` 裸 `var`
- Sprint 8 處理建議：封裝為 `SupabaseTokenCache` 私有持有類

---

## E. Lifecycle / IME Session 類候選

### E-1. IME service kill/restart 後感測器狀態

- Provenance：CLAUDE.md 架構描述；S7-0 integration audit
- 現象：service 被系統 kill 後 restart，感測器狀態（ShakeDetector / GyroscopeController）是否正確重建需驗證
- Sprint 8 處理建議：列入 S8-0 重掃時確認 `onStartInputView` / `onFinishInputView` 對稱性

### E-2. onStartInputView 重入 race

- Provenance：S7-PRE IME integration precheck
- 現象：快速切換輸入框時 `onStartInputView` 可能重入；AppModeDetector 初始化是否冪等需驗證
- Sprint 8 處理建議：列入重掃驗證項

### E-3. panel binder 狀態與 service 生命週期解耦

- Provenance：CLAUDE.md 禁區；S7-0 integration audit
- 現象：panel binder 狀態是否在 service restart 時正確重建
- Sprint 8 處理建議：架構級，屬 Sprint 9 候選，非 S8 bugfix

---

## F. Settings UI / Section 化 類候選

### F-1. `SettingsActivity` / `AISettingsActivity` section 化

- Provenance：CLAUDE.md 永久禁區；S7-0 settings audit
- 性質：架構重構，**不屬 Sprint 8 bugfix**
- 建議：移出 S8 清單，列為 Sprint 9 專案

### F-2. 登入成功後設定頁不顯示帳號 / 方案 / 角色

- Provenance：MEMORY.md `project_yukiboard_login_bug.md`；S7-0 audit 間接相關
- 現象：登入成功後設定頁 UI 不更新顯示使用者資料
- 疑似影響：使用者體驗直接受損，優先級高
- 疑似成因：與 A-1（雙 fetchMe）/ A-3（鏡像 stale）相關
- Sprint 8 處理建議：應作為 S8 早期 round 處理；與 A-1/A-3 整併解

---

## G. Security / Fallback 類候選

### G-1. `EncryptedSharedPreferences` 明文 fallback

- Provenance：S7-2 CCA side-note；`SnowRealmAuthClient` 使用 `yukiboard_auth_fallback` 作為明文備援
- 現象：Keystore 失敗時降級到明文 SharedPreferences 儲存 token
- 疑似影響：Keystore 失敗裝置上 token 明文儲存
- 性質：**安全 review 項**，不建議用 Sprint 8 一次性處理；應走 security review 流程
- 建議：列為 Sprint 9 安全審查項，但記錄現狀

### G-2. fallback prefs 的資料遷移

- Provenance：G-1 延伸
- 現象：fallback 啟用後的 token 資料若 Keystore 恢復也不會自動遷回 encrypted 儲存
- Sprint 8 處理建議：列入安全 review 時一併處理

---

## H. Integration Test 驗證項（非 bug，屬 S8-0 重掃驗證）

1. S7-1 `AppHttpClient` 5 個 Profile 的 timeout 實際行為是否符合預期
2. S7-2 `UserSessionRepository` write API 在多處呼叫下的狀態一致性
3. S7-3 `InsightApiClient` 401 refresh + retry 實際流程（是否避免無限 retry）
4. S7-3 exchange body `{}` vs `ByteArray` 差異（C-1）
5. S7-4 `UserDataSnapshotProvider` export / import roundtrip schema 正確性
6. S7-4 `SyncMetaRepository` 的 prefs key 是否與原 `cloud_sync_meta` 完全一致（避免遷移資料遺失）
7. Sprint 7 全輪次後的登入 → 顯示使用者資料 → 切 provider → 同步 端到端流程

---

## 建議的 Sprint 8 round 切法（候選，不排序）

以下只列分組建議，**不排 round 順序**，最終由 `CodeX A` 於 `S8-0` 重掃後決定：

- **候選 Batch α — 登入鏈修復**：A-1 + A-3 + F-2（共用使用者資料一致性主題）
- **候選 Batch β — Provider 行為修復**：B-1 + B-3
- **候選 Batch γ — Singleton race 清理**：D-1 + D-2（+ D-4 若 S7-4 未處理）
- **候選 Batch δ — 邊緣情境修復**：A-2 + C-3
- **候選 Batch ε — 驗證與文件**：H 類（非獨立 round，應與 α/β/γ/δ 各 round 附帶交付）

**移出 S8 建議**（應屬 Sprint 9 / 長期 refactor / 安全 review）：

- B-2（`AISettingsActivity` section 化）
- D-3（`YukiPreferences` 38 欄位重構）
- E-3（panel binder 架構解耦）
- F-1（`SettingsActivity` section 化）
- G-1、G-2（安全 fallback 審查）

---

## A / B / C / D / E

### A. 變更摘要（本文件）

- 產出 S8-PRE Claude Code A 前置 bug backlog
- 從 A 到 H 共 8 類、24 個候選項整理並標記 provenance
- 提出候選 Batch α–ε 分組建議與「建議移出 S8」清單
- 本文件不動任何 `.kt`、不宣告 finding 最終成立、不排 round 順序

### B. 風險

1. 本文件整理於 S7-4 實作完成前，**S7-4 交付後部分項目（A-4、D-4）可能自動消失**；S8-0 重掃時需重新核對。
2. 部分候選項（如 E-1 / E-2）需實機 reproduce 才能確認是否為真實 bug，本文件僅列觀察。
3. 「建議移出 S8」部分可能與 `CodeX A` 判斷不同，最終以 `CodeX A` S8-0 結論為準。
4. 候選 Batch 分組可能與 `CodeX A` 實際切法不同，純屬輔助備忘。

### C. 本輪未做（刻意不做）

- 不宣告任何 finding 最終成立（屬 S8-0 重掃職責）
- 不排 Sprint 8 round 順序（屬 `CodeX A` 判定）
- 不建立 `kt_review_checkbug.md` 續審版（屬 S8-0 CodeX A 主導）
- 不整理驗證腳本 / smoke checklist 完整版（屬 CCB 前置，本輪僅列驗證項清單 H）
- 不改任何 `.kt`、不做任何 bugfix 實作

### D. 架構決策（無）

本文件為前置整理，不涉及架構決策；僅做 provenance 彙整與候選分組。

### E. CodeX A 審核請求

請 `CodeX A` 針對本文件判定：

- `READY`（可作為 S8-0 重掃輔助材料） / `NOT READY`（需補項或調整分類）/ `需刪除 / 合併哪些類別`

後續候選：

- `CodeX A` 於 `Sprint 7` 結案後主導 `S8-0` Kotlin 全量重掃
- 重掃結果落成正式 `kt_review_checkbug.md` 續審版
- 正式 Sprint 8 bugfix round 依 `CodeX A` 判定順序開門

> 本 backlog 為候選，不排 round 順序。是否採用本分組、是否合併項目、是否加入新 finding，均由 `CodeX A` 決定。

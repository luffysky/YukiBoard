# Sprint 8-PRE Claude Code B 前置 Impl Scope 建議

- 身份：Claude Code B
- 時間：2026-04-18（`S7-4` 實作完成後當日）
- 適用 round：`S8-PRE` Sprint 8 前置準備
- 對應 gate 任務：
  - 「整理 bugfix implementation 候選 batch 與最小 file scope 建議」
- 審核對象：`CodeX A`
- 本文件性質：**候選整理，非最終 finding；不改任何 `.kt`、不排 round 順序**

---

## 重要前提

1. 本文件**不宣告**任何項目已正式成立為 Sprint 8 bug。
2. 本文件**不改任何 `.kt`**、不做任何 implementation。
3. 本文件**不排序** Sprint 8 round 順序。
4. 與 `SPRINT8_PRE_CCA_BUG_BACKLOG_2026-04-18.md` **互補**：
   - CCA 角度：bug provenance 分類 + 候選 Batch α–ε 分組建議
   - CCB 角度（本文件）：候選 batch 的最小 file scope + post-S7-4 現況校正 + 實作面 overlap 警示
5. 最終 bug round 清單、順序、切法由 `CodeX A` 於 `S8-0` 重掃後定案。

---

## 1. Post-S7-4 現況校正（CCA backlog 兩項）

CCA backlog §A-4 與 §D-4 標記「若 S7-4 完成某動作則自動消失」。S7-4 已於 2026-04-18 完成實作並回交（`BUILD SUCCESSFUL`），以下為實際狀態校對：

### 1.1 A-4 `UserSessionRepository` 建構子散佈 — **未解決，留在 S8**

S7-4 實作**未**做 `UserSessionRepository.getInstance` 化。當前 `new UserSessionRepository(context)` 仍有 4 處：

| 檔案 | 行號 | 建構場景 |
|------|------|---------|
| `claw/provider/ApiKeySync.kt` | 38 | `pushLocalKeysToInsight()` 內 |
| `claw/provider/ApiKeySync.kt` | 92 | `pullKeysFromInsight()` 內 |
| `data/CloudSyncManager.kt` | 64 | singleton 持有（長壽） |
| `data/InsightApiClient.kt` | 40 | singleton 持有（長壽） |

- `ApiKeySync` 的兩處是 **short-lived**（每次呼叫 new 一次）
- `CloudSyncManager` / `InsightApiClient` 的兩處是 **long-lived**（singleton 內部欄位）
- 都走相同的 underlying `SnowRealmAuthClient` singleton，所以 token state 是**共享**的；但 `UserSessionRepository` 本身**非 singleton**，呼叫 `refreshSession` / `resolveVerifiedUserId` 時會有額外 GC 與（潛在）state mirror 疑慮

**S8 候選建議**：A-4 留在 Sprint 8 bugfix / cleanup 類，若整併可和 A-1 / A-3 同 batch 處理（共用 `SnowRealmAuthClient` state 改動邊界）。

### 1.2 D-4 `SupabaseTokenCache` 未封裝 — **未處理，留在 S8**

S7-4 實作範圍**未**封裝 `SupabaseTokenCache`。當前 `cachedSupabaseToken` / `supabaseTokenExpiry` 仍為 `CloudSyncManager.kt:89-90` 的裸 `private var`：

```kotlin
private var cachedSupabaseToken: String? = null
private var supabaseTokenExpiry: Long = 0
```

使用點：`CloudSyncManager.kt:105,106,118,119`（共 4 處 read/write）。

**S8 候選建議**：D-4 留在 Sprint 8；若 `CodeX A` 認為封裝有效益，可與 C-3（Supabase token 刷新重試策略）同 batch 處理。

---

## 2. 候選 Batch 最小 file scope 建議

以下為 CCA 建議的候選 Batch α–δ（參 §SPRINT8_PRE_CCA_BUG_BACKLOG§224）**若實作時**會觸及的最小 file scope。**僅供 `CodeX A` 於 S8-0 重掃時參考**，不等於 round 指派。

### 2.1 候選 Batch α — 登入鏈修復（A-1 + A-3 + F-2）

**主題**：使用者資料在登入後的一致性（雙 fetchMe 合併 + 鏡像失效 + 設定頁顯示）

| 檔案 | 對應 bug | 具體觸及 |
|------|---------|---------|
| `auth/SnowRealmAuthClient.kt` | A-1 / A-3 | `postAuth`（line 200）內部 `/me` 呼叫 / `saveTokenDirectly` 後鏡像 / `currentUser` 欄位 |
| `app/session/UserSessionRepository.kt` | A-1 整併 | line 98 `resolveVerifiedUserId` 與 S8 一次性 snapshot 契約 |
| `app/settings/SettingsScreenCoordinator.kt` | A-1 / F-2 | line 41 `verifySessionAsync` 改走 snapshot |
| `ui/settings/SettingsActivity.kt` | F-2 | line 330 `settingsCoordinator.verifySessionAsync()` 呼叫點，確認顯示流程對接 snapshot |
| `data/CloudSyncManager.kt` | A-1 延伸 | line 80 `resolveUserId` 的 fetchMe 若被 snapshot 取代則同步調整 |

**新增建議（CCA 已提）**：`UserSessionRepository` 公開「登入後一次性快照」API，讓 `postAuth` 內 fetchMe 結果直接餵入此 snapshot，後續 `verifySessionAsync` / `resolveVerifiedUserId` 全部走該 snapshot。

**面積預估**：5 檔；最小改動可只動前 3 檔 + 最後 2 檔的呼叫點 1 行調整。

**紅線**：不動 `SnowRealmAuthClient` 的 `login` / `register` 成功路徑本體、`EncryptedSharedPreferences` 儲存策略；snapshot 容器建議放 `UserSessionRepository`，不放 `SnowRealmAuthClient`。

### 2.2 候選 Batch β — Provider 行為修復（B-1 + B-3）

**主題**：provider 切換後的 cache 失效 + model list 空值

| 檔案 | 對應 bug | 具體觸及 |
|------|---------|---------|
| `claw/provider/AIProviderManager.kt` | B-1 | `setSelectedModel` / `saveProviderSelection(type)` 時 invalidate cache |
| `claw/provider/AIProviderManager.kt` | B-3 | provider model list 空值時的 UI fallback |
| `ui/settings/AISettingsActivity.kt` | B-3（read-only 驗證） | 讀 model list 的 UI 位置，**僅驗證**，不改 UI 本體（CLAUDE.md 禁區） |

**面積預估**：主要 1 檔（`AIProviderManager`）；`AISettingsActivity` 僅確認不受影響。

**紅線**：不擴 `AISettingsActivity` 本體、不 section 化（B-2 建議移出 S8，屬長期 refactor）。

### 2.3 候選 Batch γ — Singleton race 清理（D-1 + D-2，+ D-4 若採納）

**主題**：subsystem `new-per-call` → 雙軌 in-memory map / prefs 不同步 + singleton 化

| 檔案 | 對應 bug | 現狀 new 位置 |
|------|---------|--------------|
| `input/smart/AdaptiveDictionary.kt` | D-1 | `SmartInputManager:20` 長壽、`CloudSyncManager:355,384` 短命 |
| `input/smart/PhrasePredictor.kt` | D-1 | `SmartInputManager:21` 長壽、`CloudSyncManager:357,386` 短命 |
| `input/smart/SentencePredictor.kt` | D-1 | `SmartInputManager:22` 長壽、`CloudSyncManager:358,387` 短命 |
| `input/smart/TypingHabitTracker.kt` | D-1 | `SmartInputManager:23` 長壽、`CloudSyncManager:360,389` 短命 |
| `input/smart/PersonalDictionary.kt` | D-1 | `SmartInputManager:24` 長壽、`CloudSyncManager:356,385` 短命 |
| `input/smart/StyleProfiler.kt` | D-1 | `SmartInputManager:25` 長壽、`CloudSyncManager:359,388` 短命 |
| `input/smart/CorrectionLearnManager.kt` | D-1 | `SmartInputManager:29` 長壽、`CloudSyncManager:361,390` 短命 |
| `input/dictionary/UserDictionary.kt` | D-2 | `CloudSyncManager:220,312`、`InputRouter.kt:27` |
| `input/smart/SmartInputManager.kt` | D-1 調用點調整 | 7 個欄位 `= X(context)` → `= X.getInstance(context)` |
| `input/router/InputRouter.kt` | D-2 調用點調整 | line 27 UserDictionary 建構 |
| `data/CloudSyncManager.kt` | D-1 / D-2 調用點 | line 220 / 312 / 355-361 / 384-390 共 16 處 new → getInstance |
| `data/CloudSyncManager.kt` | D-4（若採納） | line 89-90 / 105-119 封裝為 `SupabaseTokenCache` |

**核心 bug 本質（S7-4 KDoc 已明示）**：
- subsystem 在 `init { loadAll() }` 時載 prefs 進 in-memory map
- `CloudSyncManager.downloadLearningData` new 出的**新 instance** 僅更新 prefs，**不會** reload 長壽 `SmartInputManager` 持有的 instance 的 in-memory map
- 使用者當前輸入的候選排序仍用 stale map，直到 process 重啟

**修法選項（由 `CodeX A` 決定）**：
- **方案 A**：7 subsystem 全部 `getInstance` 化（CCA backlog 建議），配合各自 `importSnapshot` 後自動 reload in-memory map
- **方案 B**：不 singleton，但在 `importSnapshot` 內加公開 `reload()` hook，`CloudSyncManager.downloadLearningData` 完成後通知 `SmartInputManager.reloadAll()`
- **方案 C**：保留現狀，只在文件說明「下次 process 重啟才生效」（不修 bug）

**面積預估**：方案 A = 12 檔；方案 B = 8 檔（7 subsystem + SmartInputManager 新增 reloadAll）；方案 C = 0 檔。

**紅線**：不動 subsystem 的 public API 語意；不改 SharedPreferences 檔名；不動 merge 策略（S7-4 已落在 owner）。

### 2.4 候選 Batch δ — 邊緣情境修復（A-2 + C-3）

**主題**：plan 空字串 + Supabase token 退避

| 檔案 | 對應 bug | 具體觸及 |
|------|---------|---------|
| `auth/SnowRealmAuthClient.kt` | A-2 | line 374 `parsePlan(raw: String?)`：空字串視同 null（一行改動） |
| `data/CloudSyncManager.kt` | C-3 | line 97-129 `getSupabaseToken`：加 exponential backoff 或 retry 次數上限 |

**面積預估**：2 檔；每檔修改範圍 < 30 行。

**紅線**：不動 `parsePlan` 對其他輸入（`"free"` / `"pro"` / `"premium"` / `null`）的語意；不擴 `getSupabaseToken` 成為 network retry policy provider。

---

## 3. 跨 Batch File Overlap 警示

若 `CodeX A` 決定將以下 Batch 連續開 round，以下檔案會**被多個 batch 連續觸及**，需注意合併順序：

| 檔案 | 觸及的 Batch | 建議合流策略 |
|------|-------------|-------------|
| `auth/SnowRealmAuthClient.kt` | α（A-1/A-3）+ δ（A-2） | α → δ（α 改動較大，δ 單點小改） |
| `data/CloudSyncManager.kt` | α（延伸）+ γ（16 處）+ δ（1 處） | γ → δ → α（γ 是建構子改動，δ 是 retry 加法，α 是呼叫點微調） |
| `app/session/UserSessionRepository.kt` | α（核心） | 單一 batch 處理 |
| `input/smart/SmartInputManager.kt` | γ | 單一 batch 處理 |

**若跨 batch 同檔改動**：建議先做「結構改動」（γ singleton、α snapshot container）再做「語意微調」（δ parsePlan、δ retry）。

---

## 4. S8-0 重掃時建議的驗證項（補 CCA H 類）

CCA backlog §H 已列 7 項通用驗證。CCB 補充 3 項**與 S7-4 具體改動相關的**驗證項：

### 4.1 S7-4 subsystem snapshot roundtrip

```
前置：process 內已有 SmartInputManager（長壽 subsystem）
步驟：
  1. 觸發 downloadLearningData（例如 syncOnLogin）
  2. 確認 7 檔 SharedPreferences 中的資料被更新
  3. 確認 SmartInputManager 內的 ConcurrentHashMap **未自動 reload**（此為 S7-4 KDoc 明示的 S8 bugfix 目標）
  4. kill process、重啟、確認 loadAll 讀到雲端新資料
預期：步驟 3 失敗即 D-1 / γ Batch 正式成立
```

### 4.2 S7-4 theme snapshot 欄位完整性

```
前置：YukiPreferences.exportThemeSnapshot 內 8 欄（keyboard_height_ratio 在 theme snapshot 而非 settings snapshot）
步驟：修改 bg_offset_x / bg_rotation / keyboard_height_ratio → upload → download → 確認三欄全部回寫
預期：無欄位遺失；若缺 → 視為 S7-4 regression，屬 S8-0 回歸
```

### 4.3 S7-4 CloudSyncManager 無 SharedPreferences 直讀

```
前置：S7-4 完成後 CloudSyncManager.kt 應無 `getSharedPreferences("yukiboard_*"` 字串
步驟：grep `yukiboard_` in CloudSyncManager.kt
預期：僅保留 `cloud_sync_meta`（本類 own 的 meta prefs），無 yukiboard_adaptive_dict / yukiboard_personal_dict 等
```

---

## 5. 移出 S8 建議（與 CCA 對齊 + CCB 補充）

CCA 已建議移出 S8：
- B-2、D-3、E-3、F-1、G-1、G-2

CCB 補充建議：**若 `CodeX A` 同意 D-1 採方案 C（僅文件化，不修）**，則 D-1 亦移出 S8（文件角度而非 bugfix）。D-2（UserDictionary）同理。

CCB 同意 CCA 全部移出 S8 建議，並進一步提議：
- **A-2 `parsePlan` 空字串**：若 `CodeX A` 於 S8-0 重掃時**無法 reproduce**（例如後端實際不會回空字串），應移出 S8

---

## 6. A / B / C / D / E

### A. 變更摘要（本文件）

- 產出 `Claude Code B` 的 S8-PRE impl-scope 建議，與 CCA bug backlog 互補
- 校正 CCA backlog 中兩項 post-S7-4 現況（A-4 / D-4 皆未解決、留 S8）
- 列出候選 Batch α–δ 的最小 file scope（含行號）+ 紅線
- 列出跨 Batch file overlap 警示（`SnowRealmAuthClient` / `CloudSyncManager` 多 batch 觸及）
- 補 3 項針對 S7-4 改動的 S8-0 驗證項
- 本文件**不動任何 `.kt`**、**不排 round 順序**、**不宣告 finding 最終成立**

### B. 風險

1. 本文件列出的 file scope 為「若採 CCA 分組實作」的預估，`CodeX A` 可能採不同分組 → scope 隨之變化
2. §2.3 方案 A / B / C 三選一由 `CodeX A` 決定；本文件僅列選項不指定
3. §4 驗證項 4.1 假設 `SmartInputManager` 為長壽（IMEService 持有）；若 `CodeX A` 於 S8-0 發現並非如此，該驗證項需重寫
4. 本文件依據是 S7-4 實作完成當日（2026-04-18）現況；若 S7-4 於 CCA 審核後需回修，scope 可能變動

### C. 本輪未做（刻意不做）

- 不改任何 `.kt`（本 round gate 禁止）
- 不做任何 bugfix implementation（本 round gate 禁止）
- 不宣告任何 finding 已最終成立（屬 S8-0 `CodeX A` 職責）
- 不排 Sprint 8 round 順序（屬 `CodeX A` 判定）
- 不建立 / 更新 `kt_review_checkbug.md`（屬 S8-0 `CodeX A` 主導）
- 不宣告 CCA backlog 24 項中哪些應正式進 S8（屬 `CodeX A` 判定）
- 不選擇方案 A / B / C（§2.3；屬 `CodeX A` 判定）

### D. 架構決策（無）

本文件為前置整理，不涉及架構決策；僅做 file scope 彙整、post-S7-4 現況校對、驗證項建議。

### E. CodeX A 審核請求

請 `CodeX A` 針對本文件判定：

- `READY`（可作為 S8-0 重掃輔助材料，補充 CCA bug backlog 的 impl-scope 角度）
- `NOT READY`（需調整或補項）
- 或指定刪除 / 合併哪些建議

後續：

- `Claude Code B` 不自排下一 round，等 `CodeX A` 於 `Sprint 7` 結案後主導 `S8-0` 重掃
- 重掃完成後 `CodeX A` 指派 `S8-1+` 的實作 round，`Claude Code B` 才進 `.kt` 改動

> 本文件為 impl-scope 候選整理，不排 round 順序、不宣告 finding 成立、不改任何 `.kt`。實際 Sprint 8 bug round 清單、順序、切法以 `CodeX A` `S8-0` 重掃結論為準。

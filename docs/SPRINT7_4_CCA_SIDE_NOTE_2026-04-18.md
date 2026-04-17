# Sprint 7-4 Claude Code A 側寫與代行 CodeX B 技術 Review

- 身份：Claude Code A
- 時間：2026-04-18
- 適用 round：`S7-4` Prefs / Singleton / Mutable Field 真實來源收斂
- 對應 gate 任務：
  - `S7-4-CCA-SIDE-NOTE`（Claude Code A 本業）
  - `S7-4-CCA-ACTING-CXB-TECH-REVIEW`（CCA 代行 CodeX B 技術視角）
- 審核對象：`CodeX A`

---

## PART I — CCA 本業側寫（實作前看守）

### I-A. S7-4 必須盯住的 3 個核心

#### 1. `CloudSyncManager` prefs 直讀搬遷是否只搬殼

S7-3 delivery doc 明列：`CloudSyncManager` 目前扮演 gateway + payload builder + prefs reader 三重角色，14 處直讀 + 38 處 `YukiPreferences` 欄位 + 7 個 subsystem pref schema 硬綁在 `exportData()` / `importData()` 裡。S7-4 的題目是把 payload builder / prefs reader 從 gateway 拆出來，但最容易的失敗模式是：

- 新拉一個 `CloudSyncPayloadBuilder`，但邏輯只是把 `exportData()` 整段搬過去 → 本體檔案行數少了，但 schema 依舊綁死在一個大函式內，狀態來源仍然是 builder 自己的 ad-hoc 讀 prefs。
- `metaPrefs`（`cloud_sync_meta`）沒有一併封裝成 repository，導致 `isSyncEnabled` / `lastSyncTime` 仍是 `CloudSyncManager` 私有 prefs 入口。

**守線**：payload builder 抽出後，`CloudSyncManager` 必須只看得到三種東西 — session 狀態、payload 物件、同步 meta repository。不應再出現任何 `getSharedPreferences` / `SharedPreferences` 型別宣告在 `CloudSyncManager.kt`。

#### 2. Singleton owner 是否清楚（誰是 SSOT）

目前 singleton 清單：

- ✅ `SnowRealmAuthClient`（`getInstance(context)`）
- ✅ `AIProviderManager`（`getInstance(context)`）
- ✅ `CloudSyncManager`（`getInstance(context)`，S7-0 後補）
- ✅ `SupabaseClient`（`getInstance()`）
- ✅ `InsightApiClient`（`getInstance(context)`）
- ✅ `UserSessionRepository`：**目前仍以 `UserSessionRepository(context)` 建構散佈**（`CloudSyncManager.kt:59`、`InsightApiClient` 內部、`SettingsScreenCoordinator` 等）

非 singleton / each-call new：

- ❌ `InviteCodeManager`（禁區：Sprint 8 處理）
- ❌ `UserDictionary`（`UserDictionary(context)` — `CloudSyncManager.exportData()` / `importData()` 各 new 一次）
- ❌ 7 個 smart subsystem（`AdaptiveDictionary` / `PersonalDictionary` / `PhrasePredictor` / `SentencePredictor` / `StyleProfiler` / `TypingHabitTracker` / `CorrectionLearnManager`） — 每次 new 實例，但各自 own 一個 SharedPreferences 檔；目前有 `@Volatile private var` 或 `@Synchronized` 保護，但**跨 instance 仍可能同時寫同一 prefs 檔**，屬潛在 race。

**守線**：
- `UserSessionRepository` 應在本輪改為 `@Volatile getInstance(context)` 或至少 `provideFor(context)` 單一入口（與 S7-2 交付的 `SupabaseAuthTokenProvider` 介面方向一致），避免跨呼叫點各自持有狀態鏡像。
- `UserDictionary` / 7 subsystem 的 singleton 化若動到 IME 或 panel 路徑即逾界，屬 Sprint 8 bugfix；本輪**僅於 payload builder 內集中 new 一次，不散佈到外部**。

#### 3. Mutable mirror / dual source-of-truth 是否真的收斂

`CloudSyncManager` 目前仍有三個 mutable owner：

- `cachedSupabaseToken: String?`（`CloudSyncManager.kt:84`）
- `supabaseTokenExpiry: Long`（`CloudSyncManager.kt:85`）
- `commitsSinceLastSync: Int`（`CloudSyncManager.kt:69`）

前兩者是 Supabase JWT 快取，與 `InsightApiClient` exchange 結果掛鉤；後者是「提交次數超過門檻觸發同步」的本地旗標。三者都屬於「gateway 的暫態」，不是業務真實來源。

最容易的失敗模式：
- 把 `cachedSupabaseToken` / `supabaseTokenExpiry` 搬到 `UserSessionRepository` — **這是越界**。Supabase token 是 Insight exchange 產物，不屬 Insight session owner；會反過來讓 Repository 被 gateway 產物污染。
- 正確方向：保留於 gateway 層（`CloudSyncManager` 或專門的 `SupabaseTokenCache`），但封裝到一個明確的 cache 物件（`SupabaseTokenCache` 或類似），讓外部不再看到 mutable 欄位。

**守線**：mutable 欄位可以暫留本輪，但必須封裝、KDoc 標記「gateway 暫態，屬 exchange 快取、非 SSOT」；不得混入 `UserSessionRepository`。

### I-B. S7-4 應產出但不該越界的 backlog

以下為 S7-4 實作時若不謹慎很容易順手做掉、但屬他 round / Sprint 8 / 禁區的項目，**只記錄，不動手**：

1. **R-1 postAuth 雙 fetchMe 整併**（`SnowRealmAuthClient.postAuth()` 內部 `/me` + `SettingsScreenCoordinator.verifySessionAsync()` 外部 `/me`）— Sprint 8 bugfix。
2. **R-5 / B-4 `AIProviderManager` cache invalidate**（provider 切換後舊 cache 未清）— Sprint 8 bugfix。
3. **InviteCodeManager singleton 化** — 禁區。
4. **`AISettingsActivity` section 化** — 禁區。
5. **`YukiBoardIMEService.kt` / panel binder 改動** — 禁區。
6. **`YukiPreferences.kt` 本體 38 個欄位 API 重構** — 屬 S8 或 Sprint 9 級別結構改動，本輪不動。
7. **`UserDictionary` 真正的 singleton 化** — 牽動 IME 端使用路徑，屬 Sprint 8。
8. **7 個 smart subsystem singleton 化** — 同上。
9. **`postAuth` 內部 `parsePlan` 空字串 edge case**（空字串 → FREE，可能誤分類）— Sprint 8。
10. **`saveTokenDirectly` 用後 `authClient` 本地鏡像可能 stale** — Sprint 8。
11. **`EncryptedSharedPreferences` → `yukiboard_auth_fallback` 的明文 fallback** — 安全相關，屬 Sprint 8 / 9 review。
12. **S7-3 exchange body 格式差異（空 `ByteArray` → 空 JSON `{}`）** — 需整合測試驗證，屬 S8-0 驗證項。

### I-C. 絕對紅線（S7-4 禁止出現）

1. 不得在 `UserSessionRepository` 內加入 Supabase token 相關欄位或快取。
2. 不得把 `InsightApiClient` 的 auth 注入改成由 `CloudSyncManager` 或 payload builder 傳入。
3. 不得在 payload builder / snapshot provider 裡直接呼叫 `InsightApiClient` 或 `SupabaseClient` — payload 只負責「組資料」，不管 I/O。
4. 不得順手把 `postAuth` 的 `/me` 拿掉。
5. 不得把 7 個 subsystem 的 `@Volatile` / `@Synchronized` 拆掉「順便重構」。
6. 不得碰 `YukiBoardIMEService` / panel binder。
7. 不得碰 `AISettingsActivity` / `SettingsActivity` 本體。
8. 不得在 `YukiPreferences` 加新欄位或改欄位名。
9. 不得對 `InviteCodeManager` 做任何改動。
10. 不得在任何 subsystem 類別 `getSharedPreferences` 呼叫上加抽象層（例如 `PrefsFactory`）— 本輪題目不是 prefs 統一入口。
11. 不得開 Sprint 8 任何 bugfix round 的 `.md`。
12. 不得在 S7-4 交付裡 inline 修理 S7-3 / S7-2 / S7-1 的遺留項（只能列為 observation）。

---

## PART II — 代行 CodeX B 的技術 Review（給 CCB 的 file scope + 最小安全策略）

### II-A. File scope（S7-4 允許觸碰範圍）

**主戰場**：

- `app/src/main/java/com/snowrealm/yukiboard/data/CloudSyncManager.kt`
  - 拆 payload builder / snapshot provider / sync meta repository
  - mutable mirror 封裝
- `app/src/main/java/com/snowrealm/yukiboard/app/session/UserSessionRepository.kt`
  - 加 `@Volatile getInstance(context)`（與現有建構子並存作為 deprecated 路徑）
  - 不擴充業務方法

**新增建議**（都在 `app/` 層，不跨 package 邊界）：

- `app/session/UserDataSnapshotProvider.kt`（新檔）
  - 職責：組 export payload、讀 import payload
  - 依賴：`YukiPreferences`、`UserDictionary`、7 subsystem
  - 不依賴：`SupabaseClient`、`InsightApiClient`、`UserSessionRepository`
- `app/session/SyncMetaRepository.kt`（新檔）
  - 職責：封裝 `cloud_sync_meta` SharedPreferences
  - 對外只暴露 `isSyncEnabled`、`lastSyncTime`、`markSyncCompleted(time)`、`setSyncEnabled(flag)`
- `app/session/SupabaseTokenCache.kt`（可選，新檔）
  - 職責：封裝 `cachedSupabaseToken` / `supabaseTokenExpiry`
  - 僅於 `CloudSyncManager` 內部使用，不 export 出去

**禁區**（不得觸碰）：

- `SnowRealmAuthClient.kt`（S7-2 已定案，postAuth 雙 fetchMe 屬 Sprint 8）
- `AIProviderManager.kt`（cache invalidate 屬 Sprint 8）
- `InsightApiClient.kt`（S7-3 已定案）
- `AppHttpClient.kt`（S7-1 已定案）
- `InviteCodeManager.kt`（禁區）
- `YukiBoardIMEService.kt`、`ui/panel/*`、`ui/settings/AISettingsActivity.kt`、`ui/settings/SettingsActivity.kt`（禁區）
- `YukiPreferences.kt`（不加欄位、不改名）
- 7 個 subsystem 類別本體（`AdaptiveDictionary.kt` 等）— 只調用，不改實作

### II-B. 最小安全策略（5 步）

#### Step 1 — `UserSessionRepository` 加 `getInstance(context)`

```kotlin
companion object {
    @Volatile private var instance: UserSessionRepository? = null
    fun getInstance(context: Context): UserSessionRepository {
        return instance ?: synchronized(this) {
            instance ?: UserSessionRepository(context.applicationContext).also { instance = it }
        }
    }
}
```

- 保留現有 `UserSessionRepository(context)` 建構子不動，但呼叫點全部改用 `getInstance`
- 呼叫點：`CloudSyncManager.kt:59`、`InsightApiClient.kt`（session 取得處）、`SettingsScreenCoordinator` 等
- KDoc：「Session SSOT — 跨呼叫點應透過 `getInstance`；直接建構僅供測試」

#### Step 2 — 抽 `SyncMetaRepository`（封裝 `cloud_sync_meta`）

- 移走：`PREF_LAST_SYNC`、`PREF_SYNC_ENABLED`、`metaPrefs`、`isSyncEnabled`、`lastSyncTime`
- `CloudSyncManager` 改為 `private val syncMeta = SyncMetaRepository(context)`
- 同步完成寫入改 `syncMeta.markSyncCompleted(System.currentTimeMillis())`
- **不動 `commitsSinceLastSync`**（屬 gateway 暫態，不是 meta）

#### Step 3 — 抽 `UserDataSnapshotProvider`（payload builder）

對外 API 建議：

```kotlin
class UserDataSnapshotProvider(private val context: Context) {
    suspend fun buildExportPayload(): JsonObject
    suspend fun applyImportPayload(payload: JsonObject)
}
```

- 內部 own：`YukiPreferences`、`UserDictionary`、7 subsystem
- `CloudSyncManager` 改為 `private val snapshot = UserDataSnapshotProvider(context)`
- `exportData()` / `importData()` 內部改為 `snapshot.buildExportPayload()` / `snapshot.applyImportPayload(...)`
- Provider 內部保留目前 schema 原樣（14 處直讀 + 38 欄位 + 7 subsystem schema）— 本輪不優化 schema，只搬殼
- KDoc：「此類別為 export/import 唯一 schema 定義點；任何 schema 變更必須改此類別並同步 Supabase 表結構」

#### Step 4 — mutable mirror 封裝（optional，可留到觀察）

若時間允許，把 `cachedSupabaseToken` / `supabaseTokenExpiry` 搬進新的 `SupabaseTokenCache`（仍在 `CloudSyncManager` 私有持有）。若時間不足，**保留原欄位但在 KDoc 明確標記「gateway 暫態，非 SSOT，屬 exchange 快取」**，作為 S8 進一步處理的觀察點。

`commitsSinceLastSync` 本輪**不動**，維持 gateway 私有 mutable，標 KDoc。

#### Step 5 — 文件與 KDoc 更新

- `CloudSyncManager.kt` class KDoc：S7-3 的「三角色」描述更新為「僅剩 gateway 角色；payload builder → `UserDataSnapshotProvider`；meta → `SyncMetaRepository`」
- `UserSessionRepository.kt` class KDoc：明示 `getInstance` 為正規入口
- `UserDataSnapshotProvider.kt` / `SyncMetaRepository.kt` 各自 KDoc 寫明職責邊界與禁止事項（不得呼叫 `InsightApiClient` / `SupabaseClient` / `SnowRealmAuthClient`）

### II-C. 交付自查清單（CCB 實作完成前對自己檢核）

1. `CloudSyncManager.kt` 內是否仍有任何 `getSharedPreferences` 呼叫？（應為零）
2. `CloudSyncManager.kt` 是否仍直接 import `YukiPreferences`、`UserDictionary`、`input.smart.*`？（應為零，改走 `UserDataSnapshotProvider`）
3. `UserSessionRepository` 是否多了 Supabase 相關欄位？（必須為零）
4. `UserDataSnapshotProvider` 是否有呼叫任何 `InsightApiClient` / `SupabaseClient` / `SnowRealmAuthClient`？（必須為零）
5. 有沒有「順便」改到 `SnowRealmAuthClient` / `AIProviderManager` / `InsightApiClient`？（必須為零）
6. 有沒有對 `YukiPreferences` 加 field？（必須為零）
7. `UserSessionRepository(context)` 是否還有散佈建構？（應全改 `getInstance`，且舊呼叫點全覆蓋）
8. `CloudSyncManager` 以外是否有新增任何 mutable owner？（應為零）
9. 7 個 subsystem 本體是否被改動？（必須為零）
10. 本輪新增 `.kt` 是否全部落在 `app/session/`？（應是）

---

## PART III — A / B / C / D / E

### A. 變更摘要（本文件）

- 產出 S7-4 CCA side-note + 代行 CodeX B 技術 review
- 聚焦三個核心：prefs 直讀搬遷不只搬殼、singleton owner 清楚、mutable mirror 不污染 Repository
- 為 CCB 提供 file scope + 5 步最小安全策略 + 自查清單
- 本文件不動任何 `.kt`

### B. 風險

1. **payload builder 抽出後若 schema 邏輯複雜，容易一口氣把 schema 一起改** — 已以紅線限制本輪只搬殼、schema 原樣。
2. **`UserSessionRepository.getInstance` 與既有 `UserSessionRepository(context)` 並存** — 需確保全部呼叫點一次改完，否則會出現雙 instance 狀態不一致。
3. **mutable mirror 封裝 optional** — 若 CCB 為求穩妥不動 `SupabaseTokenCache`，S8 會繼續追；若動了，需確保只在 `CloudSyncManager` 私有持有，不 leak 出去。
4. **7 subsystem 的 new-per-call 仍是潛在 race** — 本輪不處理，已列入 Sprint 8 backlog。
5. **S7-3 exchange body 格式差異未經整合測試** — 列入 S8-0 rescan 驗證項，本輪不補測。

### C. 本輪未做（刻意不做）

- postAuth 雙 fetchMe 整併（Sprint 8）
- AIProviderManager cache invalidate（Sprint 8）
- InviteCodeManager singleton 化（禁區）
- AISettingsActivity section 化（禁區）
- YukiBoardIMEService / panel binder（禁區）
- YukiPreferences 38 欄位 API 重構（Sprint 9 級）
- UserDictionary / 7 subsystem singleton 化（Sprint 8）
- `EncryptedSharedPreferences` 明文 fallback 改善（Sprint 8 / 9 安全 review）
- `saveTokenDirectly` 本地鏡像 staleness（Sprint 8）
- `parsePlan` 空字串 edge case（Sprint 8）

### D. 架構決策

1. `UserSessionRepository` 正式升為 `@Volatile getInstance` SSOT，與 S7-2 `SupabaseAuthTokenProvider` 方向一致。
2. `CloudSyncManager` 從「三角色」削為「gateway only」，新建 `UserDataSnapshotProvider`（payload builder）與 `SyncMetaRepository`（sync meta）。
3. Supabase JWT 快取仍留在 gateway 層，不污染 `UserSessionRepository`。
4. schema 不在本輪優化，payload provider 內部仍維持現行 schema，KDoc 明示為唯一 schema 定義點。
5. 7 個 smart subsystem 在本輪只作「payload provider 內部的 collaborator」，不做 singleton 化。

### E. CodeX A 審核請求

請 `CodeX A` 針對本文件判定：

- `READY` / `NOT READY` / `需要調整的紅線或 scope`

若 `READY`，請接著開：

- `S7-4-CCB-PREFS-SINGLETON-MUTABLE-IMPLEMENTATION`（Claude Code B 依本文件 PART II 實作）
- `S7-4-CCA-IMPL-REVIEW`（Claude Code A 於 CCB 交付後做 side-note impl review）

> 本 round 候選順序僅為建議；實際 CCB 是否開門、S8-PRE 是否並行交付、S7-4 之後是否直接進 S7-5 或回補觀察，均由 `CodeX A` 決定。

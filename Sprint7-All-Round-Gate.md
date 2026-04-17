# Sprint 7 All Round Gate

身份：CodeX A

用途：

- 作為 `Sprint 7` 唯一總 gate 文件
- 逐 round 記錄：
  - 任務
  - 回交
  - `CodeX A` 審核結論
  - 下一輪開門指派
- 已完成 round 一律保留並加刪除線，不刪除
- 之後 `Sprint 7` 不再新增額外 gate `.md`

---

## 全域規則

1. 一次只開一個 round。
2. 前一 round 完成並經 `CodeX A` 審核通過，才開下一 round。
3. 執行者可提候選方向，但不可自行指定後續 round 順序。
4. 若需擴大 file scope，必須先回報，不可默擴。
5. `Sprint 7` 不可與尚未完成的 `Sprint 6` panel implementation 混成同一門。

---

## 開門前提

`Sprint 7` 的正式前提：

1. `Sprint 6` 全部完成。
2. 必須先完成 `Sprint 7` 前置作業 round，並經 `CodeX A` 審核通過。
3. `CodeX A` 明確判定可啟動 `Sprint 7-0 integration audit`。
4. 不得因為 `Sprint 6` 已完成，就口頭視為 `Sprint 7` 自動解鎖。

---

## ~~Round S7-PRE：Sprint 7 前置作業~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~在 `Sprint 6` 全部完成後，先整理 `Sprint 7` 啟動前的前置條件~~
- ~~確認 integration 題的範圍、禁區、資料來源與 round 切法~~
- ~~讓 `S7-0` 開門時不會和 `Sprint 6` / `Sprint 8` 混線~~

### ~~預定前置內容~~

1. ~~確認 `Sprint 6` 已正式結案~~
2. ~~整理 `Sprint 7` integration source 候選範圍~~
3. ~~確認 `Sprint 7` 與 `Sprint 8` bugfix sprint 的分界~~
4. ~~明確寫出 `S7-0` 的 owner / 禁區 / 最小交付物~~

### ~~預定 owner~~

#### ~~CodeX A~~

- ~~主導前置條件整理與開門判定~~

#### ~~CodeX B~~

- ~~補技術面前置風險與 round 切法建議~~

#### ~~Claude Code B~~

- ~~盤點 IME 側 integration source 候選範圍與禁區~~

#### ~~Claude Code A~~

- ~~盤點 settings / auth / provider / sync 側 integration source 候選範圍與禁區~~

### ~~任務指派~~

#### ~~CodeX B~~

- ~~任務 ID：`S7-PRE-CXB-PRECHECK`~~

目標：

- ~~交付 Sprint 7 前置技術風險與 round 切法建議~~
- ~~明確區分哪些題目屬 `S7-0`，哪些應留在後續 round 或 `Sprint 8`~~

#### ~~Claude Code B~~

- ~~任務 ID：`S7-PRE-CCB-IME-INTEGRATION-PRECHECK`~~

目標：

- ~~盤點 IME 側 integration source 候選範圍與禁區~~

要求：

1. ~~不做 implementation~~
2. ~~只做盤點 / file scope / owner source 整理~~
3. ~~不得提前進入 `S7-0`~~

#### ~~Claude Code A~~

- ~~任務 ID：`S7-PRE-CCA-SETTINGS-AUTH-PROVIDER-PRECHECK`~~

目標：

- ~~盤點 settings / auth / provider / sync 側 integration source 候選範圍與禁區~~

要求：

1. ~~不改 `.kt`~~
2. ~~只做前置盤點與邊界整理~~
3. ~~要明確標出與 `Sprint 8 bugfix` 的分界~~

### ~~回交結果~~

- ~~`docs/SPRINT7_PRE_TECH_RISK_AND_ROUND_CUT_2026-04-18.md`~~
- ~~`docs/SPRINT7_PRE_IME_INTEGRATION_PRECHECK_2026-04-18.md`~~
- ~~`docs/SPRINT7_PRE_SETTINGS_AUTH_PROVIDER_PRECHECK_2026-04-18.md`~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. Sprint 6 已正式結案，Sprint 7 前置文件已收齊。
2. 三份前置文件已把 IME 側、settings/auth/provider/sync 側、以及技術 round cut 分界講清楚。
3. Sprint 8 bugfix 分界已明確，不與 Sprint 7 integration 收斂混線。
4. S7-0 可以開門，但只能是 audit only，不可提前進 implementation。
```

### ~~本輪結論~~

- ~~`S7-PRE = COMPLETED`~~
- ~~下一門開 `S7-0 = Integration Audit (audit only)`~~

---

## ~~Round S7-0：Integration Audit~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~先把所有 integration source 與 state source 做總盤點~~

### ~~預定盤點主題~~

1. ~~`OkHttpClient.Builder()` 分散點~~
2. ~~auth token / session 狀態分散點~~
3. ~~provider 選擇與 API key 分散點~~
4. ~~sync / external gateway 入口~~
5. ~~prefs / singleton / mutable field 真實來源~~

### ~~本輪限制~~

1. ~~`S7-0` 只允許 audit / inventory / migration map / owner map / file scope 整理~~
2. ~~不做 implementation~~
3. ~~不改 `.kt`~~
4. ~~不得順手處理 bug；bugfix 留給 `Sprint 8`~~
5. ~~不得把 `AISettingsActivity` section 化、InviteCodeManager singleton 化、CloudSyncManager gateway 拆分 直接當成這一門實作~~

### ~~預定 owner~~

#### ~~CodeX B~~

- ~~主導 integration audit 文件~~
- ~~產出 migration map~~

#### ~~Claude Code B~~

- ~~盤點 IME 側 integration entry~~

#### ~~Claude Code A~~

- ~~盤點 settings / auth / provider / sync 側 integration entry~~

#### ~~CodeX A~~

- ~~決定盤點是否完成到可進 implementation~~

### ~~任務指派~~

#### ~~CodeX B~~

- ~~任務 ID：`S7-0-CXB-INTEGRATION-AUDIT`~~

目標：

- ~~主導 integration audit 文件~~
- ~~產出 migration map / owner map / round 進門條件~~

#### ~~Claude Code B~~

- ~~任務 ID：`S7-0-CCB-IME-INTEGRATION-AUDIT`~~

目標：

- ~~盤點 IME 側 integration source / callsite / mutable owner 真實來源~~

要求：

1. ~~不做 implementation~~
2. ~~不改 `.kt`~~
3. ~~只做 audit 與文件盤點~~

#### ~~Claude Code A~~

- ~~任務 ID：`S7-0-CCA-SETTINGS-AUTH-PROVIDER-SYNC-AUDIT`~~

目標：

- ~~盤點 settings / auth / provider / sync 側 integration source / owner / singleton / prefs 直讀路徑~~

要求：

1. ~~不改 `.kt`~~
2. ~~只做 audit 與文件盤點~~
3. ~~要明確標記哪些問題屬 `Sprint 8`~~

### ~~回交結果~~

- ~~`docs/SPRINT7_0_INTEGRATION_AUDIT_2026-04-18.md`~~
- ~~`docs/SPRINT7_0_IME_INTEGRATION_AUDIT_2026-04-18.md`~~
- ~~`docs/SPRINT7_0_SETTINGS_AUTH_PROVIDER_SYNC_AUDIT_2026-04-18.md`~~

### ~~CodeX A 邊界裁決~~

1. ~~`AISettingsActivity` section 化不列入 `Sprint 7` 主線；這是 structure cleanup，不是本 sprint 當前必要 integration 收斂。~~
2. ~~`InviteCodeManager` 不在 `S7-1` 直接升 singleton；先維持 audit finding，除非後續 round 證明它是阻塞 integration 收斂的必要前提。~~
3. ~~`CloudSyncManager` gateway / snapshot provider 拆分只允許在 `S7-3` 討論，不得前移到 `S7-1` 或 `S7-2`。~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. 三份 audit 文件已把 inventory、owner map、migration map、round cut 與 Sprint 8 分界整理完成。
2. S7-0 的 audit only 限制有被遵守，沒有提早進 implementation。
3. 現在可安全開 `S7-1`，但只開這一門。
```

### ~~本輪結論~~

- ~~`S7-0 = COMPLETED`~~
- ~~下一門開 `S7-1 = AppHttpClient / Network Entry 收斂`~~

---

## ~~Round S7-1：AppHttpClient / Network Entry 收斂~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~收斂 network entry 與 client 建構來源~~
- ~~先處理 `OkHttpClient.Builder()`、base URL、以及 network owner 分散問題~~
- ~~不提前碰 session / auth / provider state 收斂~~

### ~~任務指派~~

#### ~~CodeX B~~

- ~~任務 ID：`S7-1-CXB-NETWORK-ENTRY-REVIEW`~~

目標：

- ~~跟審 `AppHttpClient / Network Entry` 收斂的 file scope 與 owner 邊界~~

#### ~~Claude Code B~~

- ~~任務 ID：`S7-1-CCB-NETWORK-ENTRY-IMPLEMENTATION`~~

目標：

- ~~做 `AppHttpClient / Network Entry` 最小安全收斂~~

本輪允許修改：

- ~~network client / API client / provider 相關 entry 檔~~
- ~~與 `OkHttpClient.Builder()` / base URL / shared network entry 直接相關的檔案~~

本輪禁止修改：

- ~~`YukiBoardIMEService.kt`~~
- ~~panel binder 系列~~
- ~~`AISettingsActivity` section 化~~
- ~~`InviteCodeManager` singleton 化~~
- ~~`CloudSyncManager` gateway 拆分~~
- ~~bugfix 題~~
- ~~Sprint 8 檔~~

#### ~~Claude Code A~~

- ~~任務 ID：`S7-1-CCA-NETWORK-ENTRY-SIDE-NOTE`~~

目標：

- ~~審核本輪 network entry 收斂對 settings / auth / provider / sync 的 side effect~~

要求：

1. ~~不改 `.kt`~~
2. ~~若發現 session / auth / provider state owner 會被碰到，只能文件註記，不得提前擴到 `S7-2`~~

### ~~回交結果~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/app/net/AppHttpClient.kt`~~
- ~~`docs/SPRINT7_1_NETWORK_ENTRY_IMPLEMENTATION_2026-04-18.md`~~
- ~~`docs/SPRINT7_1_CCA_NETWORK_ENTRY_SIDE_NOTE_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`AppHttpClient.kt` 已真正新增並落地~~
  - ~~repo 內僅剩 `AppHttpClient.kt` 自己保留 `OkHttpClient.Builder()` 作為單一 source~~
  - ~~`CloudSyncManager` / `InsightApiClient` / `ImeImageCommitter` 已實際改走 `AppHttpClient.newBuilder(...)`~~
- ~~殘餘非 blocking 風險：~~
  - ~~部分 caller write timeout 較原生 default 更寬鬆，可能讓 fail-fast 稍慢~~
  - ~~shared client instance / header policy / gateway 收斂仍留在後續 round~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. `AppHttpClient` 單一 owner 已建立，直接 `OkHttpClient.Builder()` callsite 已收斂。
2. `CloudSyncManager` 只換 builder，未提前碰 gateway；`InsightApiClient` / IME 端也只收斂建構入口。
3. settings-side 紅線有守住，未碰 session / auth / provider owner 收斂。
4. compile 通過。
```

### ~~本輪結論~~

- ~~`S7-1 = COMPLETED`~~
- ~~下一門開 `S7-2 = Session / Auth / Provider 狀態收斂`~~

---

## ~~Round S7-2：Session / Auth / Provider 狀態收斂~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~收斂 session / auth / provider 狀態來源與 token 路徑~~
- ~~明確處理 `SnowRealmAuthClient` / `AIProviderManager` / `ApiKeySync` / auth token flow 的 owner~~
- ~~不提前碰 `CloudSyncManager` gateway 拆分與 prefs 大收斂~~

### ~~任務指派~~

#### ~~Claude Code B~~

- ~~任務 ID：`S7-2-CCB-SESSION-AUTH-PROVIDER-IMPLEMENTATION`~~

目標：

- ~~做 `Session / Auth / Provider` 最小安全收斂~~

本輪允許修改：

- ~~`auth/`~~
- ~~`claw/provider/`~~
- ~~與 session / auth / provider 狀態來源直接相關的檔案~~

本輪禁止修改：

- ~~`CloudSyncManager` gateway 拆分~~
- ~~`AISettingsActivity` section 化~~
- ~~`InviteCodeManager` singleton 化~~
- ~~`YukiBoardIMEService.kt`~~
- ~~panel binder 系列~~
- ~~bugfix 題~~
- ~~Sprint 8 檔~~

#### ~~Claude Code A~~

- ~~任務 ID：`S7-2-CCA-SIDE-NOTE`~~

目標：

- ~~做本輪 settings / auth / provider side-note~~

要求：

1. ~~不改 `.kt`~~
2. ~~明確盯住：~~
   - ~~`SnowRealmAuthClient.postAuth` 雙 `fetchMe`~~
   - ~~token 策略分歧~~
   - ~~`AIProviderManager` provider cache~~
   - ~~`InviteCodeManager` 非 singleton 現狀~~
3. ~~屬於 `S7-3` / `S8` 的題目只能註記，不得前移~~

#### ~~Claude Code A（暫代 CodeX B）~~

- ~~任務 ID：`S7-2-CCA-ACTING-CXB-TECH-REVIEW`~~

目標：

- ~~暫代 `CodeX B` 做本輪技術審核與 file scope review~~

說明：

- ~~因 `CodeX B` 額度因素，本輪起暫由 `Claude Code A` 兼任技術 review 角色~~
- ~~必須在回交文件中明確區分：~~
  - ~~side-note 視角~~
  - ~~acting `CodeX B` 技術審核視角~~

### ~~回交結果~~

- ~~`docs/SPRINT7_2_CCA_SIDE_NOTE_2026-04-18.md`~~
- ~~`docs/SPRINT7_2_SESSION_AUTH_PROVIDER_IMPLEMENTATION_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無 blocking finding~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`UserSessionRepository.kt` 已新增 `saveProviderSelection` / `saveApiKey` / `saveSelectedModel` / `clearSession` / `currentUserId` / `getAuthToken`~~
  - ~~`ApiKeySync.kt` 已改走 `UserSessionRepository`，移除對 `SnowRealmAuthClient.getInstance` / `AIProviderManager.getInstance` 的直接 reach-in~~
- ~~本輪未前移的禁區：~~
  - ~~`CloudSyncManager` gateway 拆分未動~~
  - ~~`InviteCodeManager` singleton 化未動~~
  - ~~`SnowRealmAuthClient.postAuth` 雙 `fetchMe` 未動~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. `UserSessionRepository` 已成為 session / provider 狀態寫入 owner。
2. `ApiKeySync` 已改走 Repository，直接 reach-in 已收斂。
3. `CloudSyncManager` gateway、`InviteCodeManager` singleton、postAuth 雙 fetchMe 都未被前移，邊界有守住。
4. compile 通過。
```

---

## ~~Round S7-3：Sync / External Gateway 收斂~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~收斂 sync / external gateway 的真正 owner~~
- ~~明確處理 `CloudSyncManager`、`InsightApiClient`、Supabase token exchange、external gateway 邊界~~
- ~~不提前進 prefs / singleton / mutable field 大收斂~~

### ~~任務指派~~

#### ~~Claude Code B~~

- ~~任務 ID：`S7-3-CCB-SYNC-GATEWAY-IMPLEMENTATION`~~

目標：

- ~~做 `Sync / External Gateway` 最小安全收斂~~

本輪允許修改：

- ~~`data/CloudSyncManager.kt`~~
- ~~`data/InsightApiClient.kt`~~
- ~~與 sync / external gateway 直接相關的檔案~~

本輪禁止修改：

- ~~`AISettingsActivity` section 化~~
- ~~`InviteCodeManager` singleton 化~~
- ~~`YukiBoardIMEService.kt`~~
- ~~panel binder 系列~~
- ~~bugfix 題~~
- ~~Sprint 8 檔~~

#### ~~Claude Code A~~

- ~~任務 ID：`S7-3-CCA-SIDE-NOTE`~~

目標：

- ~~做本輪 settings / auth / provider / sync side-note~~

要求：

1. ~~不改 `.kt`~~
2. ~~明確盯住：~~
   - ~~`CloudSyncManager` prefs 直讀~~
   - ~~Supabase token exchange 路徑~~
   - ~~`InsightApiClient` 是否成為唯一 Insight gateway~~
3. ~~屬於 `S7-4` / `S8` 的題目只能註記，不得前移~~

#### ~~Claude Code A（暫代 CodeX B）~~

- ~~任務 ID：`S7-3-CCA-ACTING-CXB-TECH-REVIEW`~~

目標：

- ~~暫代 `CodeX B` 做本輪技術審核與 file scope review~~

提醒：

1. ~~本輪繼續由 `Claude Code A` 暫代 `CodeX B`~~
2. ~~與 `Claude Code B` 回交時，兩人都不得自行指定 `S7-4`~~

### ~~回交結果~~

- ~~`docs/SPRINT7_3_CCA_SIDE_NOTE_2026-04-18.md`~~
- ~~`docs/SPRINT7_3_SYNC_GATEWAY_IMPLEMENTATION_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無 blocking finding~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`UserSessionRepository.kt` 已新增 `refreshSession()` / `resolveVerifiedUserId()` pass-through~~
  - ~~`InsightApiClient.kt` 已改走 `UserSessionRepository`，不再直達 `SnowRealmAuthClient`~~
  - ~~`CloudSyncManager.kt` 的 auth 與 `/api/auth/supabase-token` exchange 已改走 Repository + `InsightApiClient`~~
- ~~本輪未前移的禁區：~~
  - ~~`CloudSyncManager` 14 處 prefs 直讀未動~~
  - ~~`SnowRealmAuthClient.postAuth` 雙 `fetchMe` 未動~~
  - ~~`ApiKeySync` 未動~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. `InsightApiClient` 與 `CloudSyncManager` 的 gateway owner 已收斂到指定邊界。
2. `UserSessionRepository` 的新增 pass-through 僅支撐 gateway 收斂，沒有越界成 session 大重構。
3. `prefs` 直讀、`postAuth` 雙 `fetchMe`、`ApiKeySync` 都未被前移。
4. compile 通過。
```

### ~~本輪結論~~

- ~~`S7-3 = COMPLETED`~~
- ~~下一門開 `S7-4 = Prefs / Singleton / Mutable Field 真實來源收斂`~~

---

## ~~Round S7-4：Prefs / Singleton / Mutable Field 真實來源收斂~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~收斂 prefs / singleton / mutable field 的真正 owner~~
- ~~明確處理 `CloudSyncManager` 的 prefs 直讀、shared snapshot source、singleton / mutable source-of-truth 分散問題~~
- ~~不順手修 bug；bugfix 留給 `Sprint 8`~~

### ~~任務指派~~

#### ~~Claude Code B~~

- ~~任務 ID：`S7-4-CCB-PREFS-SINGLETON-MUTABLE-IMPLEMENTATION`~~

目標：

- ~~做 `Prefs / Singleton / Mutable Field` 最小安全收斂~~

本輪允許修改：

- ~~`data/CloudSyncManager.kt`~~
- ~~`app/session/`~~
- ~~與 prefs / singleton / mutable owner 直接相關的檔案~~

本輪禁止修改：

- ~~`SnowRealmAuthClient.postAuth` 雙 `fetchMe` 行為整併~~
- ~~provider cache invalidate bugfix~~
- ~~`InviteCodeManager` singleton 化~~
- ~~`YukiBoardIMEService.kt`~~
- ~~panel binder 系列~~
- ~~Sprint 8 檔~~

#### ~~Claude Code A~~

- ~~任務 ID：`S7-4-CCA-SIDE-NOTE`~~

目標：

- ~~做本輪 side-note，盯住 prefs / singleton / mutable owner 收斂是否越界進 bugfix~~

要求：

1. ~~不改 `.kt`~~
2. ~~屬於 Sprint 8 的缺陷只能註記，不得前移~~
3. ~~特別盯：~~
   - ~~`CloudSyncManager` prefs 直讀搬遷~~
   - ~~singleton owner 是否清楚~~
   - ~~mutable mirror / dual source-of-truth 是否真的收斂~~

#### ~~Claude Code A（暫代 CodeX B）~~

- ~~任務 ID：`S7-4-CCA-ACTING-CXB-TECH-REVIEW`~~

目標：

- ~~暫代 `CodeX B` 做本輪技術審核與 file scope review~~

提醒：

1. ~~本輪繼續由 `Claude Code A` 暫代 `CodeX B`~~
2. ~~與 `Claude Code B` 回交時，兩人都不得自行指定 `Sprint 8`~~

### ~~回交結果~~

- ~~`docs/SPRINT7_4_CCA_SIDE_NOTE_2026-04-18.md`~~
- ~~`docs/SPRINT7_4_PREFS_SINGLETON_MUTABLE_IMPLEMENTATION_2026-04-18.md`~~

### ~~越界檢查~~

- ~~code 越界：無 blocking finding~~
- ~~文件流程越界：無~~
- ~~我已實際查看 `.kt`：~~
  - ~~`CloudSyncManager.kt` 已從 schema owner 退回 gateway / scheduler 角色，payload 組裝改走 `YukiPreferences` 與各 subsystem 自有 `exportSnapshot()` / `importSnapshot()`~~
  - ~~`UserSessionRepository.kt` 未被污染成 mutable mirror 集中桶，仍維持 session / auth pass-through owner~~
  - ~~smart subsystem 與 `YukiPreferences` 的 snapshot API 已真正落地，`CloudSyncManager` 不再維護那 7 組 subsystem schema helper~~
- ~~本輪未前移的禁區：~~
  - ~~`SnowRealmAuthClient.postAuth` 雙 `fetchMe` 未動~~
  - ~~`InviteCodeManager` singleton 化未動~~
  - ~~provider cache invalidate bugfix 未動~~
  - ~~Sprint 8 bug backlog 題目未被偷渡進本輪~~

### ~~CodeX A 審核結論~~

```text
READY

Reason:
1. `CloudSyncManager` 已把 snapshot schema ownership 收回到 `YukiPreferences` 與各 subsystem，自身邊界回到 gateway / scheduler。
2. `UserSessionRepository` 沒有被擴成新的 dual source-of-truth；mutable owner 收斂方向正確。
3. `SnowRealmAuthClient.postAuth`、`InviteCodeManager`、provider cache invalidate 等禁區都未前移。
4. compile 通過，且本輪實作與 side-note / tech-review 回交一致。
```

### ~~本輪結論~~

- ~~`S7-4 = COMPLETED`~~
- ~~`Sprint 7 = COMPLETED`~~
- ~~下一步改由 `Sprint 8` 承接 bugfix 主線；先以 `S8-PRE` 與 `S8-0` 重掃 gate 推進~~

---

## Sprint 7 當前總狀態

- 進行中：
  - 無
- 可審核但未開門：
  - 無
- 待前置完成後開門：
  - 無
- 待前一門完成後開門：
  - 無
- 已完成：
  - `S7-PRE` `Sprint 7 前置作業`
  - `S7-0` `Integration Audit`
  - `S7-1` `AppHttpClient / Network Entry 收斂`
  - `S7-2` `Session / Auth / Provider 狀態收斂`
  - `S7-3` `Sync / External Gateway 收斂`
  - `S7-4` `Prefs / Singleton / Mutable Field 真實來源收斂`
- `CodeX A` 當前判定：
  - `Sprint 7 = COMPLETED`

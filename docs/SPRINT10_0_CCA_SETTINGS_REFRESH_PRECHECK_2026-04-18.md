# Sprint 10-0 — CCA Precheck：Settings 登入 Insight 後 UI 未刷新

身份：Claude Code A
任務 ID：`S10-0-CCA-SETTINGS-REFRESH-PRECHECK`
日期：2026-04-18
角色定位：本輪只做盤點 / 重現 / file scope / state consume 路徑分析；**不改 `.kt`**；不指定後續 round 順序；最終裁決由 `CodeX A`。

---

## 0. 範圍與限制宣告

- 範圍：使用者回報「設定頁登入 Insight 後，跳回的設定頁面仍顯示『登入』」。
- 限制：
  - 不改任何 `.kt`
  - 不做 implementation
  - 不宣告任何 `READY / NOT READY / LOCKED / COMPLETED`
  - 候選方案僅供 `CodeX A` 切板，不可視為已核可
- 對齊：`Sprint10-All-Round-Gate.md` 對 CCA 在 `S10-0` 的指派只要求三件事：
  1. 重現條件
  2. state source
  3. 最小 file scope
  4. 並明確分類為 **lifecycle / repository / session consume** 哪一類

---

## 1. 涉及檔案盤點（read-only）

| 檔案 | 行 | 角色 |
|------|----|------|
| `app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt` | 39-58 / 59-335 / 419-454 | Activity 容器、coordinator 接線、`onResume` 重建判斷 |
| `app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AuthSettingsSection.kt` | 30-107 | 登入/登出按鈕 + 名稱 label，**UI consume 規則所在地** |
| `app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/InsightSettingsSection.kt` | 23-46 | Insight 卡片（plan badge / 三個開關），讀 `auth.isLoggedIn` / `auth.currentUser.plan` |
| `app/src/main/java/com/snowrealm/yukiboard/app/settings/SettingsScreenCoordinator.kt` | 19-83 | `refresh()` 同步重建 + `verifySessionAsync()` 一次 fetchMe，唯一 session 寫入點 |
| `app/src/main/java/com/snowrealm/yukiboard/app/settings/SettingsUiState.kt` | 12-55 | 狀態欄位定義（`isInsightConnected` / `verifiedDisplayName` / `userPlan`） |
| `app/src/main/java/com/snowrealm/yukiboard/app/session/UserSessionRepository.kt` | 30-106 | snapshot + `verifySession()`，內部包 `authClient.fetchMe()` |
| `app/src/main/java/com/snowrealm/yukiboard/auth/SnowRealmAuthClient.kt` | 67-70 / 137-176 / 211-216 / 367-379 | `isLoggedIn` 只看 token；`saveTokenDirectly` 只寫 token；profile 欄位需 `fetchMe()` 才會落地 |
| `app/src/main/java/com/snowrealm/yukiboard/ui/auth/LoginActivity.kt` | 30-87 | Chrome Custom Tab 載 `${INSIGHT_API_BASE_URL}/login`；`onResume` 自帶 fetchMe 補救路徑 |
| `app/src/main/java/com/snowrealm/yukiboard/ui/auth/OAuthCallbackActivity.kt` | 17-79 | Deep link 接收：`saveTokenDirectly` → `fetchMe` → `startActivity(LoginActivity[oauth_success=true])` |
| `app/src/main/AndroidManifest.xml` | 113-128 | `OAuthCallbackActivity` 註冊 `yukiboard://auth/callback`，`launchMode=singleTask` |

---

## 2. 完整登入路徑重現

### 2.1 起點

- `SettingsActivity.onCreateInternal` 第 64 行：`wasLoggedIn = SnowRealmAuthClient.getInstance(this).isLoggedIn`（=false）
- 第 162-166 行：`AuthSettingsSection(auth, activityScope, ::tr).buildViews(btnRow)` — 呼叫 `context.startActivity(Intent(context, LoginActivity::class.java))`（line 70 of `AuthSettingsSection`）
  - **觀察**：用的是 `startActivity`，不是 `startActivityForResult`。`SettingsActivity.onActivityResult` 對登入路徑不會觸發。

### 2.2 LoginActivity → Chrome Custom Tab

- `LoginActivity.openInsightPage("/login")` 第 196-200 行：`CustomTabsIntent.launchUrl(this, "$baseUrl/login?mobile_callback=yukiboard://auth/callback")`
- 使用者在 Insight 網頁完成 email/password 或 LINE/Google OAuth。
- Insight 後端在登入成功後 redirect 到 `yukiboard://auth/callback?token=<JWT>`。

### 2.3 OAuthCallbackActivity 接收（**第一個 fetchMe 點**）

- `handleCallback`：
  1. `authClient.saveTokenDirectly(token)` — 第 41 行。**只寫 `KEY_TOKEN` + `KEY_TOKEN_EXPIRY`**，沒有寫 `KEY_USER_ID / KEY_EMAIL / KEY_DISPLAY_NAME / KEY_PLAN / KEY_ROLE`。此時 `authClient.isLoggedIn = true`，但 `authClient.currentDisplayName / currentEmail / currentUser` 全為空字串或 null。
  2. `scope.launch { val me = authClient.fetchMe(); ... withContext(Main) { startActivity(LoginActivity[oauth_success=true]); finish() } }` — 第 44-66 行。
  3. `fetchMe()` 成功 → 寫入完整 profile 七欄。失敗 → **回 null，不重試，不通報**，但**仍會繼續 startActivity → LoginActivity 並結束自己**。

### 2.4 LoginActivity 拉回前景（singleTop / clearTop）

- `LoginActivity.onNewIntent` 第 53-61 行：`oauth_success=true` + `isLoggedIn=true` → `setResult(RESULT_OK)` + `recreate()`。
- 補救路徑：`LoginActivity.onResume` 第 67-87 行 — 若 `wasLoggedIn=false && isLoggedIn=true` 仍會自己再打一次 `fetchMe()`。

### 2.5 使用者按「先跳過」 → SettingsActivity.onResume

- `SettingsActivity.onResume` 第 436-448 行：
  1. `bgSection.onResume()`
  2. `settingsCoordinator.refresh()` — 同步重建 `uiState`（呼叫 `sessionRepository.getSnapshot()` → `authClient.currentUser`，**只讀 prefs**）。
  3. `nowLoggedIn = auth.isLoggedIn`（=true）— 與 `wasLoggedIn`（=false）不同 → `recreate()`。

### 2.6 SettingsActivity.onCreateInternal 第二次

- 第 64 行：`wasLoggedIn = true` 寫回成員。
- 第 65 行：`settingsCoordinator.refresh()` 再 build 一次 `uiState`（仍是 prefs 同步快照）。
- 第 162-166 行：`AuthSettingsSection.buildViews(btnRow)` →
  - 第 62 行：`updateAuthUI(auth.isLoggedIn, auth.currentDisplayName ?: auth.currentEmail)` — **同步初始 render**。
- 第 329-334 行：`activityScope.launch { settingsCoordinator.verifySessionAsync(); val state = ...; authSection.onSessionVerified(state.isInsightConnected, state.verifiedDisplayName); syncSection?.onSessionVerified(...) }` — **第二次 fetchMe**（透過 coordinator）。

### 2.7 UI 規則最後拍板處（症狀根點）

`AuthSettingsSection.updateAuthUI` 第 89-107 行：

```kotlin
if (loggedIn && displayName != null) {
    // 顯示「登出」（紅）
} else {
    // 顯示「登入」（藍）  ← 症狀
}
```

→ 只要 `displayName == null`，**即使 `loggedIn = true`**，按鈕仍渲染為「登入」。

---

## 3. 症狀觸發條件矩陣

| # | OAuthCallback 的 fetchMe | SettingsActivity 第二次 fetchMe（verifySessionAsync）| AuthSection 初始 render 結果 | onSessionVerified 後結果 | 使用者所見 |
|---|--------------------------|------------------------------------------------------|------------------------------|--------------------------|------------|
| 1 | 成功 | 成功 | displayName 已落 prefs → 「登出」 | 再次「登出」 | 正常（無 bug） |
| 2 | **失敗**（網路斷 / 401 / JSON shape 變動 / 任何 exception） | 成功 | displayName 仍 null → **「登入」** | `state.verifiedDisplayName` 有值 → 切回「登出」 | **短暫顯示「登入」後刷新為「登出」**（接近正常，但有閃爍） |
| 3 | 失敗 | 失敗 | displayName null → **「登入」** | `state.verifiedDisplayName` 仍 null → **不切換** | **持續顯示「登入」（症狀）** |
| 4 | 成功但 `displayName` 與 `userName` 都為空字串 | 同上 | `currentDisplayName ?: currentEmail` 取到空字串（非 null） | `verifiedDisplayName` 走 `me.displayName?.ifBlank { me.email }`，可能為空字串而非 null | **空字串通過 `!= null`，按鈕變「登出」但旁邊名稱空白**（次要症狀） |
| 5 | 使用者極快返回，OAuthCallback 還沒 await fetchMe 完 | — | — | — | **不會發生**：OAuthCallback 在 fetchMe 完成前不會 startActivity；UI 一直停在 Custom Tab |
| 6 | network 過程中已發回 token，但 callback intent 因系統路徑被吃掉 | — | `isLoggedIn` 仍 false | — | 不在本題範圍（屬於 deep link 漏接） |

**最常見命中**：**第 3 列**。Insight 端 `/api/auth/me` 暫時 5xx、token 與 cookie 不對齊、或 LoginActivity → SettingsActivity 切回時 process 被回收（記憶體吃緊）導致 second fetchMe 也失敗。

**次要命中**：**第 4 列**。Insight 對某些 OAuth 帳戶不回 `displayName`，僅回 `userName`；`me.displayName.ifBlank { me.userName }` 仍可能是空字串（取決於後端契約）。

---

## 4. State source 盤點

### 4.1 寫入路徑（誰能改 `currentDisplayName`）

| 寫入點 | 觸發 | 寫入欄位 |
|--------|------|----------|
| `SnowRealmAuthClient.saveTokenDirectly` | OAuth callback 第一手 | **只寫 `KEY_TOKEN / KEY_TOKEN_EXPIRY`** |
| `SnowRealmAuthClient.fetchMe`（成功） | OAuthCallback / LoginActivity.onResume / SettingsScreenCoordinator.verifySessionAsync | `KEY_USER_ID / KEY_EMAIL / KEY_DISPLAY_NAME / KEY_AVATAR_URL / KEY_PLAN / KEY_ROLE / tenant_id` |
| `SnowRealmAuthClient.postAuth`（email/password 直接 API 路徑） | 目前 LoginActivity 不走，僅留為內部 API | `KEY_TOKEN + saveSession(...)` 七欄 |
| `SnowRealmAuthClient.logout` | 任意 logout 點 | 清空所有 KEY |

→ **profile 欄位只有 `fetchMe()` 成功才會寫入**。`saveTokenDirectly` + fetchMe 失敗 = profile 永久空白直到下次 fetchMe 成功。

### 4.2 讀取路徑（誰渲染按鈕）

| 讀取點 | 來源 | 觸發 |
|--------|------|------|
| `AuthSettingsSection.buildViews` 第 62 行 | `auth.currentDisplayName ?: auth.currentEmail`（同步） | onCreateInternal 一進入立即 render |
| `AuthSettingsSection.onSessionVerified` 第 84-87 行 | coordinator 提供的 `verifiedDisplayName`（async） | verifySessionAsync 完成後 |
| `InsightSettingsSection.buildCard` 第 27-40 行 | `auth.isLoggedIn` + `auth.currentUser.plan`（同步） | 一次性 render，無 onSessionVerified hook |

→ **InsightSettingsSection 完全沒有 onSessionVerified 機制**。當 first fetchMe 失敗、second fetchMe 補回 plan 時，Insight 卡片的 plan badge 還是空白（次要症狀）。

### 4.3 唯一 session truth 的目前實際語意

- `isLoggedIn = (token 存在)` — 這是真實 truth
- `currentDisplayName / currentEmail / currentUser` = **best effort 快取**，不保證跟 `isLoggedIn` 同步

`AuthSettingsSection` 卻把按鈕語意定為 `loggedIn && displayName != null`，把「快取狀態」誤當「真實 truth」。這是 **session consume 規則錯位**。

---

## 5. 分類裁定（按 gate 的三選一格式）

> Gate 要求：**必須明確區分是 lifecycle、repository、還是 session consume 題。**

### 主分類：**SESSION CONSUME**

理由：

1. `AuthSettingsSection.updateAuthUI` 第 91 行 `if (loggedIn && displayName != null)` 是**錯誤的 consume rule**。
2. 真實 session truth = token 存在 = `isLoggedIn`，profile 是裝飾資訊，不該升格為「是否顯示登出」的決定因子。
3. Repository 與 lifecycle 都有正常工作（fetchMe 寫入點、onResume recreate 都按設計運作），但消費端把「拿不到 displayName」視同「未登入」，這是 UI 層的 contract 誤判。

### 次要分類：**REPOSITORY**

理由：

1. `SnowRealmAuthClient.saveTokenDirectly` 與 `fetchMe` 之間存在**狀態跳階**：token 已寫但 profile 未寫，這段視窗對 consume 端不友善。
2. `SnowRealmAuthClient` 沒有「token-only logged-in」這個合法中間狀態的對外標記。
3. 上層 (`UserSessionRepository / SettingsScreenCoordinator`) 也沒有把這個中間狀態 surface 出來給 UI 用。

### 第三分類：**LIFECYCLE**

理由：

1. `SettingsActivity.onResume` 已正確監聽 `wasLoggedIn` 變化並 recreate（line 442-447）。
2. `SettingsActivity.onCreateInternal` 第 329-334 行已正確發起 `verifySessionAsync` 且把結果回灌 `authSection.onSessionVerified` / `syncSection?.onSessionVerified`。
3. **lifecycle 路徑沒有缺口**。但 `verifySessionAsync` 第二次 fetchMe 若也失敗，整條補救鏈就斷了，這算 lifecycle 上的次優處理（沒有 retry / 沒有降級顯示），不是 lifecycle 缺漏。

---

## 6. 候選最小 file scope（**僅供 CodeX A 切板，不為實作核可**）

### 6.1 候選 A：純 consume rule 修正（最小變更）

- 主寫：`app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AuthSettingsSection.kt`
- 內容大綱：
  - `updateAuthUI(loggedIn, displayName)` 改成：`loggedIn=true` 即顯示「登出」；`displayName` 為空 → 顯示 fallback（例如 `tr("已登入")` 或 `auth.currentEmail` 補值；都空就只顯示按鈕，不顯示名字 label）。
  - 取消 `loggedIn && displayName != null` 雙條件耦合。
- 風險：低。單檔內邏輯翻轉，不動 repository。
- 缺點：未處理 `InsightSettingsSection` 的 plan badge 同類問題；只解主症狀。

### 6.2 候選 B：A + Insight 卡片 onSessionVerified（中等）

- 寫：`AuthSettingsSection.kt` + `InsightSettingsSection.kt` + `SettingsActivity.kt`（一行 onSessionVerified 接線）
- 內容大綱：
  - 候選 A 的全部修正
  - `InsightSettingsSection` 新增 `fun onSessionVerified(plan: SnowRealmAuthClient.UserPlan?)` 介面，把 plan badge 改成可在 verifySession 完成後 patch
  - `SettingsActivity` 第 329-334 行多接一個 `insightSection.onSessionVerified(state.userPlan)`
- 風險：中。三個檔案，需要 `SettingsActivity` 多持有一個 section instance（目前是 inline 建構，要改成先 lazy 變數）
- 涵蓋次要症狀（plan badge 空白）。

### 6.3 候選 C：A + repository 補 token-only 中間狀態（較大）

- 寫：`AuthSettingsSection.kt` + `SnowRealmAuthClient.kt`（新增一個只標 `loggedIn` 不要求 profile 的查詢方法或語意宣告） + 可能 `UserSessionRepository.kt`
- 風險：中。動到 `SnowRealmAuthClient`（`LOCK_RULES` 沒列高敏感，但是 auth 核心檔，建議加說明）。
- 優點：state source 真正單一化，未來其他 consume 點不會再踩同樣的坑。
- 缺點：超出本 bug 必要 scope，可能踩 `S7-2 / S9-D` 已收斂的 auth 邊界，需要 `CodeX A` 評估是否現在動。

### 6.4 候選 D：A + OAuthCallback 重試（中等）

- 寫：`AuthSettingsSection.kt` + `OAuthCallbackActivity.kt`（fetchMe 失敗時做一次或數次帶 backoff 的 retry，或在 fetchMe 失敗時把錯誤帶回 LoginActivity 顯示）
- 風險：中。動到 `OAuthCallbackActivity`，會多 N 個網路 round trip 與 UI 等待。
- 適用情境：當 telemetry 顯示 fetchMe 失敗率高時。

### CCA 個人意見（**僅候選，不指定**）

- 最小、可獨立交付、零外擴：**候選 A**
- 若要一次解 plan badge 同類問題：**候選 B**
- 候選 C / D 屬於更深層治本，**CodeX A 若評估有時間再考慮，不在本題必要 scope**

順序由 `CodeX A` 拍板。

---

## 7. 禁區聲明

實作 round（任何之後的 `S10-3`）絕不應該動到的：

1. `SettingsActivity.onResume` 既有 `wasLoggedIn` recreate 邏輯（已正確）
2. `SettingsScreenCoordinator.refresh / verifySessionAsync`（S7-2 已收斂為唯一入口）
3. `UserSessionRepository.verifySession`（S7-2 收斂結果）
4. `SnowRealmAuthClient.fetchMe` 內部寫入欄位順序（S9-D 才剛動完手刻 JSON 安全化，不應再動）
5. `OAuthCallbackActivity.handleCallback` 啟動 LoginActivity 的 `FLAG_ACTIVITY_CLEAR_TOP / SINGLE_TOP` 組合（與 manifest `singleTask` 緊密耦合）
6. `AndroidManifest.xml` 的 deep link intent-filter
7. `SettingsActivity` 行數警戒（455 行；`SETTINGS_BOUNDARY` 警戒 500 行）— 候選 B 若加 InsightSection lazy 變數，要計算行數變化

---

## 8. Smoke path（給後續實作 round 用）

1. 清掉 token：設定頁 → 登出（若未登入則跳過）
2. 重啟 SettingsActivity
3. 確認頂端按鈕為「登入」（藍）
4. 點「登入」→ LoginActivity
5. 點 LoginActivity 的「登入」→ Chrome Custom Tab
6. 在 Insight 網頁完成登入
7. 系統返回 LoginActivity 顯示「登出」按鈕與帳號名
8. 點 LoginActivity「先跳過」
9. **觀察點**：SettingsActivity 頂端按鈕是否為「登出」（紅）+ 名稱 label 顯示
10. 額外觀察：Insight 卡片是否顯示 plan badge

人工模擬第 3 列（fetchMe 失敗）：

- 用 mock 或關 wifi 後 deep link 回 callback；驗證候選 A 在 fetchMe 失敗時是否仍正確顯示「登出」+ fallback 名稱

---

## 9. 候選回交格式（A/B/C/D/E）

### A. 變更摘要

- 不改 `.kt`
- 產出本份 precheck 文件：`docs/SPRINT10_0_CCA_SETTINGS_REFRESH_PRECHECK_2026-04-18.md`
- 為 `S10-0-CCA-SETTINGS-REFRESH-PRECHECK` 任務交付物

### B. 風險

- 本輪只盤點，無 code 風險。
- 已盤但未在本輪處理的 `InsightSettingsSection.plan badge` 為次要症狀，若候選 A 通過後仍未動，會留為已知 cosmetic gap。

### C. 候選方向（不自排順序）

- 候選 A / B / C / D 已列於 §6
- 不建議的順序：請 `CodeX A` 拍板

### D. 架構決策

- 不引入新 coordinator / repository
- 候選 B 若採用，會把「session 二次驗證後 patch UI」的 onSessionVerified pattern 從 Auth/Sync 推廣到 Insight，與 S7-2 的 coordinator 邏輯一致

### E. CodeX A 審核需求

- 請 `CodeX A` 確認：
  1. 主分類 = **SESSION CONSUME** 是否同意
  2. 候選 A 是否為 `S10-3` 的最小可放行 scope
  3. 是否本 sprint 一併解次要症狀（候選 B），或留下 sprint
  4. 候選 C / D 是否暫不採用
- 在 `CodeX A` 明確輸出 `READY` 之前，CCA 不會主動進入任何實作 round。

---

## 10. 與其他 S10-0 前置文件的邊界

- **與 `S10-0-CXB-GUARDRAIL-PRECHECK` 的邊界**：本文件只看 settings 登入後 UI 刷新；CXB 處理 `CoroutineExceptionHandler / InputRouter.release / bgUpdateReceiver / test baseline`，與本題零交集。
- **與 `S10-0-CCB-AUTO-MODE-PRECHECK` 的邊界**：CCB 處理 `InputRouter.AUTO / candidate / commit`，跟 settings session 完全不同 surface，零交集。

本文件的 file scope 不會與另兩份前置撞檔。

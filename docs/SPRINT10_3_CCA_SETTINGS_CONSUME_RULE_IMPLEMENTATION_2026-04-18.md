# Sprint 10-3 — CCA Implementation：Settings Insight UI 刷新修復

身份：Claude Code A
任務 ID：`S10-3-CCA-SETTINGS-CONSUME-RULE-IMPLEMENTATION`
日期：2026-04-18
依據：`Sprint10-All-Round-Gate.md` `Round S10-3` `CodeX A` 本輪裁決（採用 `S10-0-CCA` 候選 `A`）。

---

## A. 變更摘要

### 修改檔案

| 檔案 | 變更內容 |
|------|---------|
| `app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AuthSettingsSection.kt` | `updateAuthUI` 把 `if (loggedIn && displayName != null)` 改為 `if (loggedIn)`；`displayName` 為 null/blank 時 fallback 至 `tr("已登入")` |

### Diff（兩行語意變更）

```diff
     private fun updateAuthUI(loggedIn: Boolean, displayName: String?) {
         val context = authBtn.context
-        if (loggedIn && displayName != null) {
-            userNameLabel.text = displayName
+        if (loggedIn) {
+            userNameLabel.text = if (displayName.isNullOrBlank()) tr("已登入") else displayName
             userNameLabel.visibility = View.VISIBLE
             authBtn.text = tr("登出")
             authBtn.background = GradientDrawable().apply {
                 setColor(0xFFC62828.toInt())
                 cornerRadius = dp(context, 8).toFloat()
             }
         } else {
             userNameLabel.visibility = View.GONE
             authBtn.text = tr("登入")
             authBtn.background = GradientDrawable().apply {
                 setColor(0xFF4A90E2.toInt())
                 cornerRadius = dp(context, 8).toFloat()
             }
         }
     }
```

### 變更語意

- **舊規則**：登入按鈕渲染為「登出」需同時滿足 `loggedIn = true` AND `displayName != null`；任一不滿足都退到「登入」。把 `displayName` 是否存在當成 session 真實 truth 的一部分。
- **新規則**：`loggedIn = true` 即顯示「登出」（紅）；`displayName` 只是裝飾資訊，blank/null 時 fallback 顯示「已登入」。Session truth 與 profile 快取解耦。
- 對應 `S10-0-CCA` precheck §5 主分類裁定：**SESSION CONSUME**。

### 編譯驗證

```text
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 4s
```

無 warning、無 error。

---

## B. File scope 守規

### 本輪允許修改（已用）

- `app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AuthSettingsSection.kt` ✓

### 本輪禁止修改（皆未動）

- `SettingsActivity.kt` ✓
- `InsightSettingsSection.kt` ✓
- `SettingsScreenCoordinator.kt` ✓
- `UserSessionRepository.kt` ✓
- `SnowRealmAuthClient.kt` ✓
- `OAuthCallbackActivity.kt` ✓
- 任何 `auth` / `service` / `input` 相關檔 ✓
- `Sprint 10` gate / `RULE` / `daily_works_0418.md` ✓

`git status` 應只顯示 `AuthSettingsSection.kt` + 本份 implementation 文件兩個變更。

---

## C. 行為矩陣（修補後）

| 觸發情境 | `auth.isLoggedIn` | `displayName` (currentDisplayName ?: currentEmail) | 修補前 | 修補後 |
|---------|-------------------|-----------------------------------------------------|--------|--------|
| 正常登入（fetchMe 成功，profile 完整） | true | `"Alice"` | 「登出」+ "Alice" | 「登出」+ "Alice" |
| OAuthCallback fetchMe 失敗 + verifySessionAsync fetchMe 失敗 | true | `null` | **「登入」（症狀）** | **「登出」+ "已登入"** ✓ |
| fetchMe 成功但 displayName/email 皆為空字串 | true | `""` | 「登出」+ ""（label 空白） | 「登出」+ "已登入" ✓ |
| 未登入 | false | `null` | 「登入」 | 「登入」 |
| 登入中按下「登出」 | true → false | 任意 → null | 「登入」（label hide） | 「登入」（label hide） |
| `onSessionVerified(false, null)`：server 確認 token 失效 | param=false, displayName=null | — | 「登入」 | 「登入」 |

→ 修補命中 `S10-0-CCA` precheck §3 觸發矩陣的第 3 列（主症狀）與第 4 列（次要症狀）。

---

## D. Smoke path 驗證指引（給 CXB / CCA 後續 runtime）

由於目前尚無模擬器可跑 Insight OAuth 真鏈路，本輪只做 compile 驗證。建議實機 smoke：

1. 清空 token：設定頁 → 登出（若已登入）→ 確認頂端按鈕顯示「登入」（藍）。
2. 點「登入」→ LoginActivity → 點「登入」→ Chrome Custom Tab → 在 Insight 完成登入。
3. 系統返回 LoginActivity 顯示已登入狀態 → 點「先跳過」回到 SettingsActivity。
4. **觀察點**：頂端按鈕是否為「登出」（紅）+ 旁邊 label 顯示帳號名稱。
5. 模擬 fetchMe 失敗（關 wifi 後再回到 SettingsActivity，或在 Insight 暫時 5xx）：確認頂端按鈕仍為「登出」（紅），label 顯示「已登入」 fallback。

非主路徑觀察（已知本輪未涵蓋）：

- Insight 卡片 plan badge 在 fetchMe 失敗時仍會空白（屬 `S10-0-CCA` precheck §6.2 候選 B 範圍，本輪未納入）。
- OAuthCallback fetchMe 失敗無 retry（屬候選 D 範圍，本輪未納入）。

---

## E. 風險

### 直接風險

- **無 logic 風險**：邏輯翻轉只放寬 UI 條件，不改 session truth。`loggedIn=false` 分支完全沒動。
- **無翻譯風險**：`tr("已登入")` 使用 `SettingsStrings.tr` 已驗證 fallback 行為（`SettingsStrings.kt:41`：`table[zh] ?: en[zh] ?: zh`），未列入翻譯表時直接回傳中文原文。

### 已知未涵蓋

| 項目 | 來源 | 處理 |
|------|------|------|
| Insight 卡片 plan badge fetchMe 失敗時空白 | precheck §6.2 候選 B | 本輪不納入（gate 明確排除） |
| `SnowRealmAuthClient` token-only 中間狀態未對外 surface | precheck §6.3 候選 C | 本輪不納入（gate 明確排除） |
| `OAuthCallbackActivity` fetchMe 失敗無 retry | precheck §6.4 候選 D | 本輪不納入（gate 明確排除） |
| `SettingsActivity` recreate 路徑、`SettingsScreenCoordinator` / `UserSessionRepository` 結構性調整 | gate 明示禁區 | 本輪不納入 |

### 回歸觀察

- `SETTINGS_BOUNDARY` 警戒：未動 `SettingsActivity`，行數無變化。
- `SETTINGS_SMOKE_CHECKLIST` Section 2.2「登入 / 登出顯示」：本修補影響此區塊，建議 runtime 跑該 checklist 全部 5 項。

---

## F. CodeX A 審核需求

- 提交鏈：CCA → **CodeX B** 技術二審 → **CodeX A** 終審。
- 本輪只動 `AuthSettingsSection.kt`，符合 gate 明列 file scope。
- 無 `NEED_CLARIFICATION`，未擴 file scope。
- 不自行宣告 `READY / COMPLETED`。

請 `CodeX A` 在 `CodeX B` 二審後核可。

---

## G. 下一步候選（不自排，待 CodeX A 切板）

依 `Sprint10-All-Round-Gate.md` 排定，下一門候選為：

- `S10-4 = Coroutine 例外護欄`

是否在 `S10-4` 之前先補 `S10-0-CCA` 候選 B（Insight plan badge）/ C（repository 中間狀態）/ D（OAuthCallback retry）任一條，由 `CodeX A` 決定。CCA 不主動排程。

---

## H. 提交資產清單

| 路徑 | 變更類型 |
|------|---------|
| `app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AuthSettingsSection.kt` | modified（updateAuthUI 兩行語意變更） |
| `docs/SPRINT10_3_CCA_SETTINGS_CONSUME_RULE_IMPLEMENTATION_2026-04-18.md` | new（本份回交文件） |

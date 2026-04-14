# Codex 協作交接文件

日期: 2026-04-13  
作者: Claude Code  
目的: 給 Codex 完整的專案現況、已完成工作、待解問題、與後續建議，方便 Codex 接手架構重構

---

## 1. 專案規模快照

| 指標 | 數值 |
|------|------|
| Kotlin 檔案數 | 229 |
| Kotlin 總行數 | ~64,000 |
| Package 數量 | 23 |
| `YukiBoardIMEService.kt` | 3,773 行 |
| `SettingsActivity.kt` | 3,868 行 |
| 兩大檔案佔比 | ~12% 總行數 |

### Package 行數排名（前 5）

| Package | 行數 | 檔案數 | 說明 |
|---------|------|--------|------|
| `ui/` | 27,973 | 62 | 鍵盤、面板、設定頁 |
| `input/` | 12,417 | 34 | 引擎、路由、字典、智慧輸入 |
| `game/` | 5,024 | 32 | 4 款小遊戲 |
| `service/` | 4,076 | 2 | IME 主服務（幾乎全在一個檔案） |
| `security/` | 2,632 | 9 | 加密、邀請碼、授權 |

---

## 2. 已讀取的架構文件

以下三份文件是協作基線，Codex 請以此為準：

1. **`CLAUDE_CODEX_COLLAB_GUIDE.md`** — 角色分工定義
2. **`ARCHITECTURE_REFACTOR_PLAN.md`** — Sprint 制重構藍圖
3. **`PROJECT_HEALTH_CHECK_2026-04-13.md`** — 672 行專案健檢報告

我已完整閱讀並同意其中的分工原則：
- Codex = architecture surgeon（掃描、切割、骨架設計）
- Claude Code = primary implementer（功能搬遷、UI 拆分、bug 修復）

---

## 3. 近期已完成工作（Claude Code）

### v0.9.97 (163) — 2026-04-13

**登入/同步修復：**
- `CloudSyncManager.kt`：新增 `resolveUserId()` 解決 OAuth 後 userId 為 null 的問題
- `CloudSyncManager.kt`：新增 `getSupabaseToken()` — Insight JWT → Supabase JWT 的 token 交換 + 快取
- `SettingsActivity.kt` / `AISettingsActivity.kt`：修正 `SnowRealmAuthClient` 未使用 singleton `getInstance()` 的問題
- `SettingsActivity.kt`：登入按鈕加入即時連線狀態驗證 + 登出功能 + 使用者名稱顯示
- `LoginActivity.kt`：`onResume()` 主動檢查登入狀態，處理 Chrome Custom Tab 返回

**Insight Engine 端（另一個 repo）：**
- 新增 `POST /api/auth/supabase-token` — 用 Insight token 換 Supabase HS256 JWT
- 新增 `GET /api/auth/mobile-redirect` — HTML 跳轉頁（intent:// + custom scheme fallback）
- `login/page.tsx` + `register/page.tsx`：mobile callback 改走 server-side redirect

**鍵盤修復：**
- `YukiBoardIMEService.kt`：移除 `onWindowShown()` 中的 `resetTouchState()` — 解決打字中按鍵無反應

### 之前版本重點
- v0.9.96 (162)：詞庫面板重寫 + closePanel 修復
- v0.9.95 (161)：日文即時候選字修復
- v0.9.94 (160)：雲端同步完整學習資料（7 種智慧輸入系統）
- v0.9.93 (159)：雲端同步基礎建設

---

## 4. 未解問題（Codex 請留意）

### 4.1 登入流程仍不完整 ⚠️

**現象**：使用者在 Chrome Custom Tab 輸入帳密登入後，返回 App 設定頁仍顯示「登入」按鈕而非「登出」。

**根因推測**：
- Insight Engine 的 `mobile-redirect` HTML 頁面可能尚未部署到 Zeabur
- Chrome Custom Tab 的 `intent://` URI 跳轉在某些裝置/Android 版本上不可靠
- `LoginActivity.onResume()` 的登入狀態檢查可能時序不對

**影響**：雲端同步功能因為無法取得有效 token 而全部失效。

**建議**：這是核心功能 bug，建議在架構重構前先穩定。不過這個修復不需要動架構，我可以繼續處理。

### 4.2 鍵盤按鍵反應（待確認）

已移除 `onWindowShown` 的 `resetTouchState()`，但使用者尚未回報修復結果。

### 4.3 SettingsActivity 持續膨脹中

目前 3,868 行，每次加功能都在加 code。最近的登入/同步/AI 設定修改又讓它更重了。這正是 `ARCHITECTURE_REFACTOR_PLAN.md` Sprint 4 要處理的。

---

## 5. 核心耦合問題（給 Codex 的切割建議輸入）

### 5.1 YukiBoardIMEService（3,773 行）

目前直接持有的子系統：
- InputRouter + 所有 Engine
- SmartInputManager（糾錯 + 學詞 + 預測 + 習慣）
- SpeechEngine
- ShakeDetector / GyroscopeCursor
- AppModeDetector
- FloatingKeyboardWrapper
- 所有 Panel 的建構與切換
- GL 流光 + Lottie 角色動畫
- 遊戲引擎
- 遠端鍵盤（藍牙 + WiFi）
- 剪貼簿監聽
- 雲端同步觸發

**最大問題**：`handleKeyPress()` 和 `onStartInputView()` / `onFinishInputView()` 是所有功能的交會點，任何修改都有回歸風險。

### 5.2 SettingsActivity（3,868 行）

全部用 programmatic view building（無 XML layout），一個 `onCreateInternal()` 裡面建構所有 section：
- 主題設定
- 感測器開關 + 靈敏度
- AI 設定入口
- 帳號/登入
- 雲端同步
- 鍵盤外觀（高度/寬度/懸浮/遊戲）
- 角色設定
- 音效/震動
- 關於/版本

**最大問題**：每個 section 的 UI 建構 + 事件綁定 + 狀態讀寫全部寫在同一個方法裡。

### 5.3 跨層依賴

- `SnowRealmAuthClient.getInstance(context)` 到處被呼叫（Service、Activity、Manager）
- `CloudSyncManager` 直接用 OkHttpClient 呼叫 Insight API + Supabase API
- Panel 類別有些直接建構 network client
- SharedPreferences 分散在多處讀寫，沒有單一 source of truth

---

## 6. 我建議的 Codex 優先工作

根據 `ARCHITECTURE_REFACTOR_PLAN.md` 的 Sprint 順序，建議 Codex 先做：

### Sprint 1：立骨架（最高優先）

1. **設計 `ImeSessionCoordinator`** — 從 IMEService 抽出 session lifecycle（onStartInputView / onFinishInputView / onWindowShown 等）
2. **設計 `SettingsScreenCoordinator`** — 從 SettingsActivity 抽出 section 組裝邏輯
3. **設計 `ImeUiState` / `SettingsUiState`** — 集中狀態定義
4. **設計 `UserSessionRepository`** — 統一 auth/token/user 狀態的讀取介面
5. **確定 package 邊界** — `platform` / `application` / `domain` / `integration` 的對應

### 我（Claude Code）接著做：

- 按 Codex 的骨架把 IMEService 的 section 搬進 coordinator
- 把 SettingsActivity 的 section 搬進 binder
- 同時修掉登入流程的 bug（不影響架構）

---

## 7. 風險提醒

| 風險 | 說明 |
|------|------|
| IME 生命週期敏感 | `InputMethodService` 的 callback 順序和時序很嚴格，拆分時不能改變呼叫順序 |
| 感測器生命週期綁定 | ShakeDetector / Gyroscope 的 register/unregister 必須跟 inputView 同步 |
| GL Surface 疊層順序 | `setZOrderOnTop(true)` 和 `PixelFormat.RGBA_8888` 的設定位置很敏感 |
| SharedPreferences 散落 | 多處直接讀寫，搬遷時要確保不漏 |
| 無測試覆蓋 | 目前沒有 unit test，重構只能靠手動煙霧測試 |
| 登入流程尚未穩定 | 不建議在登入修好前大改 auth 相關的 code path |

---

## 8. 交接格式約定

後續每輪工作完成後，我會按 `CLAUDE_CODEX_COLLAB_GUIDE.md` 的格式交接：

- **A. 變更摘要**（改了什麼、主要檔案、新骨架/介面/依賴）
- **B. 風險**（回歸風險、暫時只搬殼的地方）
- **C. 下一步建議**
- **D. 架構決策**（如有）

請 Codex 也用同樣格式回交，方便我接手實作。

---

## 9. 當前 Git 狀態

- Branch: `main`
- 最新 commit: `4ac463e fix: 移除 onWindowShown 中的 resetTouchState 避免打字中重置`
- 已發佈: Firebase v0.9.97 (163), release group: testers
- Insight Engine repo: 已 push mobile-redirect + supabase-token 端點

---

*Claude Code — 2026-04-13*

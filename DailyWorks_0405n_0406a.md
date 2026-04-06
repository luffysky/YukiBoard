# Daily Works — 0405 夜 ~ 0406 早

## Insight x YukiBoard 深度整合

### 後端 API（Insight Engine）
- 新增 `/api/yukiboard/ai-proxy` SSE 串流支援（stream=true）
- 新增 `/api/yukiboard/survey/trigger` + `/answer` 問卷觸發 / 回答端點
- 新增 `/api/yukiboard/themes` 主題列表（含 is_purchased）
- 新增 `/api/yukiboard/settings/backup` + `/restore` 鍵盤設定備份還原
- 新增 `/api/yukiboard/social/share` 社群分享端點
- 新增 `src/lib/yukiboard-auth.ts` 共用驗證工具（X-Platform-Key + Bearer）
- `/api/auth/me` 回傳新增 displayName / avatarUrl / plan 欄位

### OAuth 修正
- LINE / Google OAuth：mobile flow 改用 state 編碼 mobile_callback（解決跨 app cookie 丟失）
- LINE / Google callback：double-fetch 防護 — token exchange 失敗時 fallback 已有 session

### 登入 / 註冊 mobile_callback 支援
- `/api/auth/login` + `/api/auth/register`：帶 mobile_callback 時回傳 token
- `login/page.tsx`：email 登入成功 + mobile_callback → redirect 到 deep link；OAuth 按鈕帶 mobile_callback 參數
- `register/page.tsx`：同上

### 資料庫 Migration
- 執行 `migration_yukiboard_integration.sql`
  - 新增 `yukiboard_surveys`、`yukiboard_survey_answers`、`yukiboard_user_settings` 三張表
  - `admin_users` 新增 `avatar_url` 欄位
  - 插入 3 筆預設問卷 + 3 個 index

---

## YukiBoard Android

### LoginActivity 改寫
- 移除原生 email / password / 驗證碼 UI（-397 行）
- 改為 Chrome Custom Tab 直接開 Insight 登入 / 註冊頁
  - 登入 → `https://insight.snowrealm.pet/login?mobile_callback=yukiboard://auth/callback`
  - 註冊 → `https://insight.snowrealm.pet/register?mobile_callback=yukiboard://auth/callback`
- 已登入狀態顯示使用者名稱 + 方案 + 登出按鈕

### SnowRealmAuthClient 擴充
- 新增 `UserPlan` enum（FREE / PLUS / PRO / ENTERPRISE）
- `currentUser` property 回傳 `UserProfile`（含 avatarUrl / plan）
- `fetchMe()` 解析新欄位並持久化

### 新增 Insight API Client 群
- `InsightApiClient` — 統一 HTTP client，auto 401 refresh + retry
- `InsightSurveyClient` — 問卷觸發 / 回答（30 分鐘節流）
- `InsightThemeSync` — 主題同步 / 設定備份還原
- `InsightSocialClient` — 社群分享

### SnowRealmProvider（Claw AI）
- 改用 SSE 串流解析
- `getRecommendedModel(plan)`：FREE→haiku / PLUS→sonnet / PRO+→opus

### 設定頁
- 修正 header 排版（標題與按鈕拆兩行）
- 新增「Insight 整合」卡片（方案 badge + 3 toggle）
- `YukiPreferences` 新增 3 個 Insight 整合偏好

### build.gradle
- `INSIGHT_API_BASE_URL` fallback 修正為 `insight.snowrealm.pet`
- v0.9.0 → v0.9.1

---

## Commits

| Repo | Hash | Message |
|------|------|---------|
| Insight | `08706d4` | feat: OAuth 支援 mobile app callback |
| Insight | `9362042` | fix: OAuth mobile flow — state 編碼修正 |
| Insight | `ef7db32` | feat: YukiBoard x Insight 深度整合 — 後端 API |
| Insight | `67d4901` | feat: login/register 支援 mobile_callback |
| Insight | `075d24c` | fix: OAuth callback double-fetch fallback |
| YukiBoard | `878d045` | fix: v0.8.23 — LINE/Google OAuth mobile state |
| YukiBoard | `f3f994b` | feat: v0.9.0 — Insight 深度整合 + 設定頁修正 |
| YukiBoard | `45438cf` | feat: v0.9.1 — 登入/註冊改用 Insight 網頁 |

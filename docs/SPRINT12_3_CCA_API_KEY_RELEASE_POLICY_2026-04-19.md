---
sprint: S12-3
round: CCA
role: 雪鑰
date: 2026-04-19
task_id: S12-3-CCA-API-KEY-RELEASE-POLICY
ch_order: CH-1 第 4 門（修訂後 S12-2 deferred）
---

# Sprint 12-3 CCA — API Key Release Policy（雪鑰）

身份：`雪鑰`（CCA — consume / contract / release policy 線）
任務：補 AI / weather / gif key 的產品面、設定面、同意與 release contract，附 Insight cross-check appendix

---

## A. 範圍與目的

### A.1 範圍

本輪 CCA 處理三類 key 的 **release policy**：

1. **AI key**（OPENAI_API_KEY / ANTHROPIC_API_KEY）
2. **天氣 key**（OPENWEATHER_API_KEY）
3. **GIF key**（GIPHY_API_KEY）

並順手 cover 同樣為 BuildConfig 注入但風險面相近的 key：

4. **Platform key**（PLATFORM_API_KEY） — Insight gateway
5. **Supabase anon key**（YUKIBOARD_SUPABASE_ANON_KEY） — 設計上 public，但 release 行為仍需明示
6. **Invite HMAC secret**（INVITE_HMAC_SECRET） — 不是 API key 但同 BuildConfig 機制

藍新（NEWEBPAY_*）三 key 已於 S12-1 收口、S12-2 DEFERRED，本輪不再處理。

### A.2 目的

回答四個問題：

1. 每支 key 對應哪個使用者可見功能？
2. 每支 key 是否可由使用者覆寫，或只能用 .env 預設值？
3. 「使用我自己的 key」vs「使用平台預設 key」對使用者的 consent 與費用影響為何？
4. release APK 是否可帶該 key 的真值？若可，風險與替代方案？

### A.3 與 CCB 的邊界

| 主題 | CCA（雪鑰，本份） | CCB（鍵律） |
|---|---|---|
| 產品 surface（哪個 key 對應哪個功能） | ✅ | — |
| 設定 UX / 文案 / 同意流程 | ✅ | — |
| Release contract（哪支 key 不准進 release） | ✅ 規範 | ✅ 落地 |
| Build / runtime 暴露點 | — | ✅ |
| Logging / crash report 的 key 防漏 | — | ✅ |
| ProGuard / 反編譯掃描策略 | — | ✅ |
| `.env.example` / CI scan landing | — | ✅ |
| Insight cross-check（appendix） | ✅ 觀察 | — 不處理 |

---

## B. YukiBoard 端 key 全景

### B.1 七支 BuildConfig key 一覽

| Key | 來源 | 預設值 | 消費點 | release 真值是否合理 |
|---|---|---|---|---|
| `INVITE_HMAC_SECRET` | `app/build.gradle:49` | `yukiboard_dev_2026` | `auth/InviteCodeManager.kt:146` | ❌ 預設值不可進 release（已於 S12-0 標 P0） |
| `PLATFORM_API_KEY` | `:50` | `''` | `data/InsightApiClient.kt:39`, `sticker/InsightStickerApi.kt:28`, `claw/provider/SnowRealmProvider.kt:36` | ⚠️ 條件性可帶（共享密鑰，洩漏即可冒充任意 client） |
| `INSIGHT_API_BASE_URL` | `:52` | `https://insight.snowrealm.pet` | 各 Insight 客戶端 | ✅ 非 secret |
| `GIPHY_API_KEY` | `:53` | `''` | `gif/GiphyApiClient.kt:19` | ⚠️ 第三方 key，洩漏=配額被偷 |
| `OPENAI_API_KEY` | `:55` | `''` | `claw/provider/AIProviderManager.kt:63`（首次匯入）, `service/ImeServiceInput.kt:255`, `ui/settings/section/SpeechSettingsSection.kt:266` | ❌ release 不可帶（直接刷錢） |
| `ANTHROPIC_API_KEY` | `:56` | `''` | `claw/provider/AIProviderManager.kt:62`（首次匯入） | ❌ release 不可帶（直接刷錢） |
| `OPENWEATHER_API_KEY` | `:58` | `''` | `weather/WeatherManager.kt:28` | ⚠️ 第三方 key，洩漏=配額被偷 |
| `YUKIBOARD_SUPABASE_URL` | `:60` | `''` | `data/SupabaseClient.kt:25` | ✅ 非 secret |
| `YUKIBOARD_SUPABASE_ANON_KEY` | `:61` | `''` | `data/SupabaseClient.kt:26`, `app/ime/ImeFraudModelInit.kt:34`, `ui/settings/section/AISettingsSection.kt:114` | ✅ 設計上 public（RLS 保護），但仍受 anon abuse 風險 |
| `NEWEBPAY_*` | `:63-65` | `''` | `currency/NewebpayClient.kt:20-22` | ❌ release 不可帶（S12-2 DEFERRED 同步處理） |

### B.2 user-overrideable 矩陣

| Key | 設定頁可改？ | 改了之後實際生效路徑 | 問題 |
|---|---|---|---|
| `OPENAI_API_KEY` | ✅ AISettingsActivity | `AIProviderManager.saveApiKey(OPENAI, key)` → `EncryptedSharedPreferences` | ⚠️ Whisper 路徑（`ImeServiceInput.kt:255-256`、`SpeechSettingsSection.kt:266-268`）**先讀 BuildConfig 再 fallback 用戶 key** — 若 release APK 帶 .env 預設值，用戶覆寫無效 |
| `ANTHROPIC_API_KEY` | ✅ AISettingsActivity | `AIProviderManager.saveApiKey(ANTHROPIC, key)` → 加密 SP | ✅ 走 AIProviderManager.getApiKey()，無 BuildConfig 反讀 |
| `GIPHY_API_KEY` | ❌ 無 UI | 直接用 BuildConfig 注入值；`isAvailable()` 依此判 | ⚠️ 若 release 不帶，GIF 面板永遠空 |
| `OPENWEATHER_API_KEY` | ❌ 無 UI | 直接用 BuildConfig；`fetchFromApi()` blank 即 return null | ⚠️ 同上，release 不帶=天氣面板永遠 stub |
| `PLATFORM_API_KEY` | ❌ 無 UI | 直接用 BuildConfig；`X-Api-Key` header 嵌入 | ⚠️ 整個 Insight 通訊鏈倚賴它 |
| `YUKIBOARD_SUPABASE_ANON_KEY` | ❌ 無 UI | 直接用 BuildConfig；isConfigured 判 | ✅ anon key 設計上即 public |
| `INVITE_HMAC_SECRET` | ❌ 無 UI | 直接用 BuildConfig 簽 HMAC | ❌ 預設值若進 release，邀請碼可被偽造（已 S12-0 P0） |

### B.3 P0 / P1 / P2 分級（產品面）

**P0 — release 必嚴禁真值或必嚴禁預設值**

- `OPENAI_API_KEY` — 直接刷錢，無使用者額度概念
- `ANTHROPIC_API_KEY` — 同上
- `INVITE_HMAC_SECRET` 預設值 `yukiboard_dev_2026` — 只要進 release 就可離線偽造任意邀請碼

**P1 — release 不建議帶真值，但業務可接受配額被偷的代價**

- `GIPHY_API_KEY` — 走 Giphy 免費層，配額被偷=GIF 面板降速
- `OPENWEATHER_API_KEY` — 同上
- `PLATFORM_API_KEY` — 共享 client→server 密鑰；洩漏可冒充任意 YukiBoard client 打 Insight，但 Insight 端有 Bearer auth 二層防

**P2 — 設計即公開，但仍需注意**

- `YUKIBOARD_SUPABASE_ANON_KEY` — 公開 anon key，必須搭配嚴格 RLS（不在 CCA scope，列為 follow-up）
- `INSIGHT_API_BASE_URL` — 非 secret

---

## C. 設定 UX 與「我的 key vs 平台 key」consent

### C.1 現狀 consent 邏輯

YukiBoard 目前對使用者「key 來源」沒有任何明示：

1. AISettingsSection 有「OpenAI API Key」、「Anthropic API Key」輸入框，但**沒有寫**「不填會自動用平台 key」或「填了之後計費走你的帳號」
2. 首次安裝時，`AIProviderManager.importBuildConfigKeysIfNeeded()` 會**靜默把 .env 內的 key 複製進 EncryptedSharedPreferences**，使用者完全不知道自己的 AI 對話用的是平台 key
3. SnowRealmProvider 走 invite code + auth token，是另一條 consent 完整的路徑（使用者主動啟用邀請碼）

### C.2 release 後的 consent 路徑差異

| 路徑 | release 帶 .env key | release 不帶 .env key | 使用者知情 |
|---|---|---|---|
| AISettings 預設選 SNOWREALM | 不影響（走 proxy） | 不影響（走 proxy） | ✅ 邀請碼流程是顯式的 |
| AISettings 預設選 ANTHROPIC | 自動用平台 key | hasValidConfig=false → 提示填 key | ❌ 使用者不知用了誰的 key |
| AISettings 預設選 OPENAI | 同上 | 同上 | ❌ 同上 |
| Whisper 語音 | `ImeServiceInput.kt:255` 先 BuildConfig，後使用者 → 永遠用平台 key | fallback 使用者 key | ❌ 使用者覆寫被 silent override |

### C.3 release contract 建議（CCA 提案，待玄樞拍板）

#### C.3.1 P0 key — 強制清空

`OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `INVITE_HMAC_SECRET` 預設值：

- release build 時 `.env` **必須不存在或這三個 key 必須為空**
- `AIProviderManager.importBuildConfigKeysIfNeeded()` 行為自然降級：blank 即不匯入
- `ImeServiceInput.kt:255` / `SpeechSettingsSection.kt:266` 的「先 BuildConfig 後使用者」需**反轉為「先使用者 key 後 BuildConfig fallback」**（CCB 落地）
- `INVITE_HMAC_SECRET` release 必須注入真實 secret，預設 `yukiboard_dev_2026` fallback 需在 release build 時偵測並 fail build（CCB 落地點）

#### C.3.2 P1 key — release 帶 + 文案說明

`GIPHY_API_KEY` / `OPENWEATHER_API_KEY` / `PLATFORM_API_KEY`：

- release 帶真值 = 接受配額被偷的代價（產品面決策）
- AISettingsActivity / SettingsActivity 加一段文案：「GIF / 天氣由 SnowRealm 平台提供，不會用您的個人帳號」（讓 consent 明示）
- 監控：CCB 配合加 release 後的 quota 使用量觀察點（屬 S12-5 observability）

#### C.3.3 預設 provider 的明示

AISettings 開啟時 `AIProviderManager.importBuildConfigKeysIfNeeded()` 行為需改：

- 若使用者**從未進過** AISettings → 預設選 `SNOWREALM`（邀請碼路徑）而非 ANTHROPIC / OPENAI
- 進入 AISettings 後第一次切到 ANTHROPIC / OPENAI 時，跳一次「您將使用平台 API key（消耗平台額度）」/「您可填入自己的 key 改為消耗您自己的額度」對話框
- 一次性 ack flag，存在 prefs

### C.4 Settings 文案（CCA 提案具體 wording）

| 位置 | 現狀 | 建議 wording |
|---|---|---|
| AISettings 「OpenAI API Key」 input hint | 無 | 「留空= 使用 SnowRealm 平台 key（消耗平台額度）；填入= 使用您自己的 OpenAI 帳號計費」 |
| AISettings 「Anthropic API Key」 input hint | 無 | 同上 |
| AISettings 切換到 OPENAI/ANTHROPIC 第一次 | 無提示 | 「此 Provider 需要 API Key。可填入自己的 key，或保持空白使用平台預設額度（受公平使用限制）。」 |
| Settings 「GIF 面板」說明（若加） | 無 | 「GIF 由 Giphy 提供，使用 SnowRealm 平台共用 key」 |
| Settings 「天氣面板」說明（若加） | 無 | 「天氣由 OpenWeatherMap 提供，使用 SnowRealm 平台共用 key」 |

---

## D. Release contract — 「哪支 key 在哪種 build 可帶」

### D.1 build variant × key 容許表

| Key | debug | release（內測） | release（正式上架） |
|---|---|---|---|
| `INVITE_HMAC_SECRET` | 可預設值 | 必真值 | 必真值 |
| `PLATFORM_API_KEY` | 可空 | 必真值 | 必真值 |
| `INSIGHT_API_BASE_URL` | 可預設值 | 可預設值 | 可預設值 |
| `GIPHY_API_KEY` | 可空 | 可真值 | 可真值（接受配額風險） |
| `OPENAI_API_KEY` | 可真值（開發者本機） | **必空** | **必空** |
| `ANTHROPIC_API_KEY` | 可真值（開發者本機） | **必空** | **必空** |
| `OPENWEATHER_API_KEY` | 可空 | 可真值 | 可真值（接受配額風險） |
| `YUKIBOARD_SUPABASE_URL` | 可空 | 必真值 | 必真值 |
| `YUKIBOARD_SUPABASE_ANON_KEY` | 可空 | 必真值（anon 可公開） | 必真值（anon 可公開） |
| `NEWEBPAY_*` | 可空 | **必空**（S12-2 DEFERRED） | **必空**（待 Z 幣解凍 + Insight server-side wire-up） |

### D.2 Build-time 守衛（CCB 落地建議）

CCA 提需求，CCB 決定怎麼擋：

1. release task 觸發時掃 `.env` 與 `localProps`，若 `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `NEWEBPAY_*` 任何一支非空 → fail build
2. release task 偵測 `INVITE_HMAC_SECRET == "yukiboard_dev_2026"` → fail build
3. release task 偵測 `PLATFORM_API_KEY` blank → fail build
4. CI 端額外 grep 已編譯 APK / dex 是否含上述 key 字面值（CCB 工作）

### D.3 Runtime fallback 行為要求（CCA 規範，CCB 落地）

| 情境 | 期望行為 |
|---|---|
| AI key 全空 | AISettings 預設 SNOWREALM；hasValidConfig() 維持現邏輯 |
| GIPHY blank | GIF 面板顯示「需 Giphy key — 此功能未啟用」（不 silent empty） |
| OPENWEATHER blank | 天氣面板顯示「需設定天氣服務 key」（不 silent return） |
| PLATFORM blank | InsightApiClient / InsightStickerApi 顯示「未設定平台 key — 雲端功能不可用」 |
| INVITE_HMAC predefault | InviteCodeManager 強制拒絕所有邀請碼驗證（避免 dev secret 進 release 後簽假碼） |

---

## E. 開放問題（待玄樞 / 董事長拍板）

### E.1 SNOWREALM 平台預設 key 的營運面

C.3.3 建議「預設 provider = SNOWREALM」走邀請碼路徑，但邀請碼目前需要使用者**主動取得**。是否：

- (a) 第一次安裝給每位使用者一個 device-bound 的免費 trial 邀請碼（需 Insight 端配合）
- (b) 維持現狀，預設選 SNOWREALM 但 hasValidConfig=false，引導使用者去拿邀請碼
- (c) 維持現狀，預設選 ANTHROPIC + 平台 key，但加 consent 對話框

### E.2 Whisper 路徑的「BuildConfig 先讀」是 bug 還是 by design？

`ImeServiceInput.kt:255-256` 與 `SpeechSettingsSection.kt:266-268` 都是 `BuildConfig.OPENAI_API_KEY.takeIf { isNotBlank() } ?: 使用者 key`。

CCA 觀察：這違反 AISettingsActivity 的「使用者覆寫優先」直覺。建議反轉。請玄樞確認是否為刻意設計（例如為了讓平台 Whisper 額度永遠可用，使用者 key 只是 fallback）。

### E.3 PLATFORM_API_KEY 的 client→server 共享密鑰本質

該 key 一旦 release 必被反編譯取出。Insight 端應改為「無 PLATFORM_API_KEY 也能接受 yukiboard origin，但無 PLATFORM_API_KEY 的請求一律走更嚴格 rate limit + Bearer auth」。屬 Insight server 改造，列為 cross-project follow-up。

### E.4 Supabase RLS 是否已嚴格設定？

YUKIBOARD_SUPABASE_ANON_KEY 是 public 設計，但前提是 RLS policy 必須鎖死所有 `SELECT/INSERT/UPDATE/DELETE`。CCA 沒有 Supabase 後台權限可驗證。請董事長確認，或列入 Sprint 13+。

---

## F. Insight Cross-check Appendix（董事長指示「Insight那邊也幫我留意」）

### F.1 Insight 端 key 清單（grep 結果）

| Key | 來源 | 暴露面 | 風險 |
|---|---|---|---|
| `OPENAI_API_KEY` | env | server-only（`api/yukiboard/ai-proxy/route.ts:6`、`lib/aiService.ts:60`） | ✅ server only，安全 |
| `ANTHROPIC_API_KEY` | env | server-only（同上） | ✅ server only，安全 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` / `GOOGLE_CALLBACK_URL` | env | server-only（`api/auth/google/*`） | ✅ server only |
| `GOOGLE_API_KEY` | env | server-only（`api/translate/route.ts:3`） | ✅ server only |
| **`NEXT_PUBLIC_GIPHY_API_KEY`** | env | **client bundle**（`components/social/GifPicker.tsx:6`） | ⚠️ **任何訪客可從 JS bundle 取出**，等於明文公開 |
| **`NEXT_PUBLIC_UNSPLASH_ACCESS_KEY`** | env | **client bundle**（`components/social/ImagePicker.tsx:7`） | ⚠️ 同上 |
| `NEWEBPAY_*` | env | server-only（`lib/newebpay.ts`、`api/payment/*`） | ✅ server only |
| `platform_ai_models.encrypted_api_key` | DB（AES-GCM） | server-only，5 分鐘快取 | ✅ DB 加密儲存，env 為 fallback |

### F.2 重點觀察

#### F.2.1 ✅ AI key 處理良好

Insight 對 OPENAI / ANTHROPIC 採三層：

1. **首選**：`platform_ai_models` 表 AES-GCM 加密儲存（`api/yukiboard/ai-proxy/route.ts:10-36`）
2. **fallback**：env 變數
3. **快取**：5 分鐘 in-memory cache 避免每 request 解密

這是 YukiBoard 端 AISettings 該對齊的範本（已對齊：YukiBoard 用 EncryptedSharedPreferences）。

#### F.2.2 ⚠️ NEXT_PUBLIC_* key 是 client bundle 風險

`NEXT_PUBLIC_GIPHY_API_KEY` 與 `NEXT_PUBLIC_UNSPLASH_ACCESS_KEY` 透過 Next.js 的 `NEXT_PUBLIC_` prefix 機制注入 client JS bundle。**任何訪客打開 Insight 社群頁，都可以從 browser devtools / 反混淆 JS 取出真值**。

對齊 YukiBoard 端的決策：

- YukiBoard `GIPHY_API_KEY` 是 BuildConfig 注入（編譯期常數，可被反編譯取出，本質與 NEXT_PUBLIC 相同等級的「半公開」）
- Insight 端目前直接走 client → Giphy（沒有 server proxy）
- **建議**（給 Insight 側）：把 GIF / Unsplash 改為 server-side proxy（`/api/giphy/search`、`/api/unsplash/search`），key 留 server，client 不再持有真值

此項為 cross-project recommendation，由董事長決定是否開單給 Insight 端執行。

#### F.2.3 platform_ai_models 加密欄位（YukiBoard 可參考）

Insight 的 `platform_ai_models` 表結構：

- `encrypted_api_key`
- `encryption_iv`
- `encryption_tag`

AES-GCM with iv + tag — 標準做法，YukiBoard 端 EncryptedSharedPreferences 走 AndroidX security-crypto 的 AES256_GCM，本質等價。

### F.3 Insight 端建議（cross-project follow-up，CCA 不主導）

1. **NEXT_PUBLIC_GIPHY_API_KEY / NEXT_PUBLIC_UNSPLASH_ACCESS_KEY 改 server proxy**（移除 client bundle 暴露）
2. Insight 端 `.env.example` 標註哪些 key 是 client-bundle 暴露的
3. Insight `api/admin/system/route.ts:98` 的 health check 對 NEXT_PUBLIC_* 做 health 報告時，明示「客戶端可見」

以上不在 S12-3 scope，列為跨專案 follow-up note。

---

## G. CCA 自白與 boundary

- 本份 CCA：盤點 + 規範 + 文案 + 開放問題 + Insight cross-check appendix；不改任何 .kt / build.gradle
- runtime / build / scan 落地由 CCB 負責
- E.1-E.4 開放問題等玄樞 / 董事長拍板，CCA 不自決
- 不踩 CCB 領地：本份不寫 ProGuard rule、不寫 build-time fail 條件、不寫 logging 防漏實作
- Insight cross-check 為 appendix；本 sprint 不主導 Insight 端改動，僅留 follow-up note

提交給：`鍵律` / `玄樞`（待 CCB 完成 `S12-3-CCB-API-KEY-RUNTIME-POLICY` 後合併 gate）

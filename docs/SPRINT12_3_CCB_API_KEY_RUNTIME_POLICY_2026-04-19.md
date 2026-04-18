# S12-3-CCB — API Key Runtime Policy

日期: 2026-04-19
身份: 鍵律 (代行 CodeX B — runtime / build / logging / release scan)
任務 ID: `S12-3-CCB-API-KEY-RUNTIME-POLICY`
狀態: `AMENDED_POST_CCA — PENDING 玄樞 GATE`
修訂註記: 本文件於 CCA 交付 `docs/SPRINT12_3_CCA_API_KEY_RELEASE_POLICY_2026-04-19.md` 後追加 §K（CCA handoff reconcile），並對 §B.2 / §G 做最小 surgical edit；原 §A–§J 正文保持，以保留 CCA 前 CCB 觀點的 audit trail。最終認知以 §K 為準。

---

## A. 本文件定位

依 `Sprint12-All-Round-Gate.md` §S12-3 CCB 指派，本文件是 **policy 文件**，不是實作。

- 對應 CCA 側：`S12-3-CCA-API-KEY-RELEASE-POLICY`（雪鑰寫，產品面 / 同意面 / release contract）
- 本 CCB 側聚焦：**key 在 code / build / runtime / logging 的最小暴露政策與掃描落點**
- **不動 .kt / .gradle**（policy round，實作留 S12-3.1 或由玄樞拍板）

Scope 與 S12-1 / S12-4 的分界：
- `NEWEBPAY_HASH_KEY` / `NEWEBPAY_HASH_IV` / `INVITE_HMAC_SECRET` / `KEYSTORE_PASSWORD` 已在 S12-0 audit 標為 P0，**不在本 round scope**（Newebpay 走 S12-2 deferred path；其餘屬 build-infra 範疇，建議 S13+ 獨立處理）。
- 本 round 聚焦 **app-consumable external API keys**：AI / weather / gif / Supabase / platform-key 五類。

---

## B. Key 清冊與分級

### B.1 在 scope 內（本 round 處理）

| ID | 類型 | 定義出處 | Consumer |
|---|---|---|---|
| `OPENWEATHER_API_KEY` | 外部 API | `app/build.gradle:58` | `WeatherManager.kt:28` |
| `GIPHY_API_KEY` | 外部 API | `app/build.gradle:53` | `GiphyApiClient.kt:19` |
| `OPENAI_API_KEY` | AI provider | `app/build.gradle:55` | `AIProviderManager.kt:63`, `SpeechSettingsSection.kt:266`, `ImeServiceInput.kt:255` |
| `ANTHROPIC_API_KEY` | AI provider | `app/build.gradle:56` | `AIProviderManager.kt:62` |
| `YUKIBOARD_SUPABASE_URL` | 連線端點 | `app/build.gradle:60` | `SupabaseClient.kt:25`, `AISettingsSection.kt:113`, `ImeFraudModelInit.kt:33` |
| `YUKIBOARD_SUPABASE_ANON_KEY` | API key (RLS 前門) | `app/build.gradle:61` | `SupabaseClient.kt:26`, `AISettingsSection.kt:114`, `ImeFraudModelInit.kt:34` |
| `PLATFORM_API_KEY` | 內部 gateway key | `app/build.gradle:50` | `InsightApiClient.kt:39`, `SnowRealmProvider.kt:36`, `InsightStickerApi.kt:28` |
| `INSIGHT_API_BASE_URL` | 連線端點 | `app/build.gradle:52` | `InsightApiClient.kt:38` |

### B.2 風險分級

| 等級 | 定義 | 本 round 成員 |
|---|---|---|
| **P0-leak-hard** | APK 內即是可直接辨識的可用密鑰，洩漏後直接損失（金流、帳單、配額） | `ANTHROPIC_API_KEY`（產生 usage 帳單，hard-cap 漏洞）<br>`OPENAI_API_KEY`（同上）<br>~~`GIPHY_API_KEY`~~ → 見 §K.1 修訂為 **P1**<br>~~`OPENWEATHER_API_KEY`~~ → 見 §K.1 修訂為 **P1** |
| **P1-leak-gated** | APK 內即是密鑰，但使用端有 RLS / JWT / signature gate / 配額受限，單密鑰洩漏不等於帳單爆炸 | `YUKIBOARD_SUPABASE_ANON_KEY`（有 RLS，但 anon role 預設可讀的 table 仍會被讀）<br>`PLATFORM_API_KEY`（Insight 端可 rotate + 有 JWT 第二關）<br>`GIPHY_API_KEY`（見 §K.1，配額被偷可接受）<br>`OPENWEATHER_API_KEY`（見 §K.1，同上） |
| **P2-non-secret** | 公開端點資訊，本身不是秘密 | `YUKIBOARD_SUPABASE_URL`<br>`INSIGHT_API_BASE_URL` |

**§B.2 修訂**（AMENDED_POST_CCA）：GIPHY / OPENWEATHER 原列 P0-leak-hard，經 CCA §B.3 產品面 reconcile，修訂為 P1-leak-gated。詳見 §K.1。

**CCA 視角需求**：P0 / P1 / P2 的分級應同步進 CCA release policy，作為「哪些 key 可出 APK / 哪些必須 server-proxy」的判斷基準。CCA §B.3 已採同三級框架。

---

## C. 流向審核

### C.1 Build-time：`.env` → `app/build.gradle` → `BuildConfig`

```
.env（git-ignored）  →  localProps.getProperty(...)  →  buildConfigField "String", "XXX"  →  BuildConfig.XXX（編譯進 classes.dex）
```

對照 `app/build.gradle:9-28`（.env 讀取）+ `49-65`（buildConfigField 十一條）。

**Finding C.1-1（PASS）**：`.env` 已列在 `.gitignore` 第 15 行；`git log -- .env` 無任何歷史 commit，未曾意外進 repo。

**Finding C.1-2（WARN）**：`INVITE_HMAC_SECRET` 預設值 `yukiboard_dev_2026` 寫死在 gradle 第 49 行（不在本 round scope，S12-0 已標 P0，此處僅交叉參照）。

**Finding C.1-3（NEEDS_POLICY）**：十一條 `buildConfigField` 無「類型分級」標記，未來工程師新增 key 時無法判斷「該不該進 BuildConfig」。建議加註解 band（§E.1 policy）。

**Finding C.1-4（PASS）**：`local.properties` / `/build` / `app/google-services.json` / `keystore/` 皆在 `.gitignore`，沒有二次洩漏路徑。

### C.2 Runtime：`BuildConfig` → consumer 類

三種使用模式：

#### C.2.a 純 server-only key（consumer 內部直用，user 不參與）

- `OPENWEATHER_API_KEY`：`WeatherManager.kt:28` 直接當 URL query `&appid=$apiKey`（`WeatherManager.kt:101`）。
- `GIPHY_API_KEY`：`GiphyApiClient.kt:19` 直接當 URL query `?api_key=$apiKey`（`GiphyApiClient.kt:30,38`）。
- `PLATFORM_API_KEY`：`InsightApiClient.kt:39` 當 HTTP header `X-Platform-Key`（`:138`）。

**風險**：使用明文 URL query 傳 API key，雖然 TLS 保護 wire，但**會進入 server log / proxy log / browser history**。OpenWeatherMap / Giphy 官方接受此方式，非本 app 能改的 upstream contract。

#### C.2.b AI key 有「.env fallback → user 覆寫」雙軌

`AIProviderManager.kt:59-80`（`importBuildConfigKeysIfNeeded`）：
1. 首次啟動讀 `BuildConfig.ANTHROPIC_API_KEY` / `OPENAI_API_KEY`
2. 寫入 EncryptedSharedPreferences (`yukiboard_ai_secure`)
3. 標記 `buildconfig_keys_imported = true`，之後不再讀 BuildConfig

user 在 `AISettingsActivity` 輸入自己的 key → 覆寫 EncryptedSharedPreferences 的 entry。

**Finding C.2.b-1（PASS）**：正式版 AI key 由使用者輸入，.env key 僅供開發／首啟 seed，符合 CLAUDE.md「正式版 AI Key 由使用者在 AISettingsActivity 自行輸入」。

**Finding C.2.b-2（NEEDS_POLICY）**：release build 若 .env 內有真值 → 會被 seed 進所有使用者的 EncryptedSharedPreferences → 變成「全域預付開發 key」。必須在 release 前檢查 .env 是否已剝除 AI key 真值（§F scan 項）。

**Finding C.2.b-3（NEEDS_CODE_REVIEW）**：`AIProviderManager.kt:44-47` EncryptedSharedPreferences 建立失敗 fallback 到 `yukiboard_ai_fallback` + `MODE_PRIVATE`，此時 AI key **以明文**存在 `/data/data/.../shared_prefs/`。root / adb backup-enabled 裝置可讀。這個 fallback 非本 round 改，但 policy 應註記「fallback 觸發時須經 telemetry 上報」。

#### C.2.c Supabase 雙讀路徑（結構性問題）

`YUKIBOARD_SUPABASE_URL` / `YUKIBOARD_SUPABASE_ANON_KEY` 有 **三個 consumer 各自讀 BuildConfig**：

- `data/SupabaseClient.kt:25,26`（gateway 單例）
- `ui/settings/section/AISettingsSection.kt:113,114`（直接給 ImeFraudModelInit 用？需 CCA 確認）
- `app/ime/ImeFraudModelInit.kt:33,34`（fraud model init）

**Finding C.2.c-1（NEEDS_POLICY）**：未收斂到 gateway 單例，三個 call site 各自 `BuildConfig.YUKIBOARD_SUPABASE_*`。這違反 CLAUDE.md「外部整合 → 走統一 Repository / Gateway」。policy 建議：S13+ 收斂到 `SupabaseClient` 唯一讀點，其他 caller 取 runtime getter。**本 round 不改**（跨越 scope 邊界）。

### C.3 Logging：Log.* 呼叫點

執行 `Log\.[dwei]` × {apiKey, api_key, API_KEY, hmacSecret, HashKey, HashIV, ANON_KEY, anonKey, platformKey, supaKey} 交叉 grep，結果：

| 檔案 | 行 | 內容 | 判定 |
|---|---|---|---|
| `speech/WhisperSpeechEngine.kt` | 87 | `"apiKey=${if (apiKey.isNotBlank()) "[SET]" else "[EMPTY]"}"` | ✅ PASS — 只印 `[SET]/[EMPTY]` 狀態，未印真值 |
| `claw/provider/AIProviderManager.kt` | 45 | `"EncryptedPrefs failed, fallback"`（含 Throwable e） | ⚠️ MINOR — exception 不會夾帶 key，但 stack trace 會暴露 fallback 事實。可接受 |
| `claw/provider/{Gemini,Groq,SnowRealm}Provider.kt` | - | 僅印 `"SSE parse error: ${e.message}"` | ✅ PASS — error message 不含 key |

**Finding C.3-1（PASS）**：app code 無任何直接 `Log.* key` 洩漏。

**Finding C.3-2（PASS — 驚喜）**：雖然 `app/build.gradle:196` 引入 `okhttp3-logging-interceptor`，但**全 codebase 無任何 `HttpLoggingInterceptor` 使用**（grep 結果 0 命中）。依存已引入但未啟用 = 零 logcat 外洩風險。policy 應明文禁止啟用 `HttpLoggingInterceptor` 或（若要開）必須 `BuildConfig.DEBUG` gate + `Level.BASIC`（不印 header / body）。

### C.4 Network 路徑的 key 露出

- HTTP query `?appid=`、`?api_key=`：wire 有 TLS 保護，但會進 OpenWeatherMap / Giphy server log（非本 app 可控）。
- HTTP header `X-Platform-Key: ...`：受 TLS 保護，不入 logcat（因 HttpLoggingInterceptor 未啟）。
- Supabase anon key 透過 `Authorization: Bearer <anonKey>`（需跟 `SupabaseClient.buildRequest` 確認 — §H.1 留 CCA 對照）。

**Finding C.4-1（NEEDS_POLICY）**：對 P0 AI key，**禁止**在 HTTP query 傳（目前 OpenAI / Anthropic 都是 header `Authorization: Bearer`，符合要求，僅為未來新 provider 預設紅線）。

### C.5 Release APK：classes.dex 可被 strings / apktool 取出

現況：`app/build.gradle:80` `minifyEnabled true`（release）+ `proguard-android-optimize.txt` + `proguard-rules.pro`。

對 string 常數（`buildConfigField "String"` 產生的 `public static final String`）：
- R8 預設**不混淆字串字面值**，只混淆 class / field name。`BuildConfig.ANTHROPIC_API_KEY` 的**真值字串**會原封不動落在 dex pool。
- `strings classes.dex | grep -i "sk-"` 可直接撈 OpenAI key；`grep -i "sk-ant"` 撈 Anthropic key。
- ProGuard string encryption（DexGuard / 其他商用）我們**沒有**。

**結論 C.5-1**：**凡是進 BuildConfig 的 P0 key，等於直接寫在 APK 明文裡**。這是本輪最重要的 finding。

**mitigation 路徑**（CCA policy 決定走哪條）：
1. **Server proxy**（強）：AI 請求全走 `InsightApiClient.stream` → Insight 端代理 OpenAI/Anthropic；APK 只留 `PLATFORM_API_KEY`。現況已有此路徑（`SnowRealmProvider`），但 user 也能切「自填 key」走直連。
2. **User-supplied only**（中）：release build 強制 `.env` 中 AI key 為空；首啟不自動 seed；user 必須在 AISettingsActivity 輸入自己的 key。APK 不攜帶任何 AI key 真值。
3. **現況**（弱）：release 前人工記得清 .env。無自動化 gate。

**CCB 建議 §E.3 採路徑 2**（簡單、無 server 依賴、無 APK 真值）；路徑 1 為長期方向但不在本 round。

---

## D. 已有 mitigation 評估

| Mitigation | 位置 | 評估 |
|---|---|---|
| `.env` git-ignored + 無歷史 commit | `.gitignore:15` | ✅ 有效 |
| EncryptedSharedPreferences for AI key | `AIProviderManager.kt:33-47` | ✅ 有效，但 fallback 明文風險見 §C.2.b-3 |
| EncryptedSharedPreferences for Invite / Auth | `auth/{SnowRealmAuthClient,InviteCodeManager}.kt` | ✅ 有效 |
| `HttpLoggingInterceptor` 未啟用 | 全 codebase 0 命中 | ✅ 有效 |
| User override AI key | `AISettingsSection` | ✅ 有效 |
| ProGuard `-keep security.crypto` | `proguard-rules.pro:31-34` | ✅ 有效 |

---

## E. 本輪 policy contract（最小侵入版）

### E.1 BuildConfig 分級註解（compile-time self-documenting）

建議 `app/build.gradle` 每條 `buildConfigField` 上方加一行**分級註解**，例如：

```groovy
// [P0-leak-hard] 真值會進 APK 明文；release 必須為空或移除，由 user 自行輸入
buildConfigField "String", "ANTHROPIC_API_KEY", ...
// [P1-leak-gated] 真值會進 APK 但有 RLS gate；接受 release 攜帶
buildConfigField "String", "YUKIBOARD_SUPABASE_ANON_KEY", ...
// [P2-non-secret] 端點 URL，非密鑰
buildConfigField "String", "INSIGHT_API_BASE_URL", ...
```

**作用**：新工程師 PR 新增 key 時，PR reviewer / AI agent 看註解 band 即知道要不要質疑。

**落地成本**：11 行註解，純文件式，不改 runtime 行為。留給 S12-3.1 實作 round。

### E.2 Runtime logging 紅線（Repository / Gateway 層 enforce）

policy 條款：
1. **禁止** 對任何 P0 / P1 key 直接 `Log.*(... $apiKey ...)`。違反即 review blocker。
2. **允許** 印 `[SET]` / `[EMPTY]` 狀態（對照 `WhisperSpeechEngine.kt:87`）。
3. **禁止** 啟用 `HttpLoggingInterceptor` 於 release build；debug 啟用時必須 `Level.BASIC` 或更低，不印 body / header。
4. **建議** `AppHttpClient`（`app/net/AppHttpClient.kt`）在 build 層加 `addInterceptor` 黑白名單自檢（長期 S13 項，本 round 只是政策宣告）。

### E.3 Release build 的 P0 key 空值契約

policy 條款：**release APK 不得攜帶任何 P0-leak-hard key 真值**。

實作路徑（擇一，由 CCA + 玄樞 拍板）：
- **路徑 A**：release gradle 變體強制 override P0 key 為空（`release { buildConfigField "String", "ANTHROPIC_API_KEY", "\"\"" }`），.env 真值只影響 debug build。
- **路徑 B**：pre-release scan 腳本（§F）檢查 .env 中 P0 key 必須為空，否則 build fail。

CCB 偏好 **路徑 A**（強 enforce、不依賴人工記得跑 script）。

### E.4 EncryptedSharedPreferences fallback 事件上報

對照 `AIProviderManager.kt:44-47` — fallback 目前僅 `Log.w`。

policy 條款：**fallback 到 non-encrypted prefs 必須經 `ErrorReporter` 上報**（S12-4 已落地 `telemetry/ErrorReporter.kt`，可直接消費）。

code 面（留 S12-3.1 實作）：
```kotlin
} catch (e: Exception) {
    Log.w("AIProviderMgr", "EncryptedPrefs failed, fallback", e)
    AndroidLogReporter.report(TelemetryEvent(
        category = "SECURE_STORAGE",
        severity = Severity.ERROR,
        code = "AI_KEY_PREFS_FALLBACK_PLAIN",
        extras = mapOf("exception" to e.javaClass.simpleName)
    ))
    context.getSharedPreferences("yukiboard_ai_fallback", Context.MODE_PRIVATE)
}
```

### E.5 新 key 的流程 gate（文件化）

policy 條款：任何新增 `buildConfigField "String"` 進 `app/build.gradle` 的 PR，必須在 PR description / 本文件同級位置宣告：
- 分級（P0 / P1 / P2）
- Release 攜帶策略（攜帶 / 空值 / user-supplied）
- Consumer 類路徑
- logging / network 露出路徑

**落地方式**：不做 CI gate（本 round 不擴 scope），留在 CLAUDE.md + §E.1 註解雙重提醒。

---

## F. Release scan strategy 落點建議

### F.1 最小掃描項

| # | 掃描目標 | 規則 | 建議落點 |
|---|---|---|---|
| 1 | `.env` 中 P0 key 為空 | `ANTHROPIC_API_KEY=` / `OPENAI_API_KEY=` 後方無 `sk-` 開頭真值 | `scripts/release_scan.sh`（新檔，`./gradlew assembleRelease` 前跑） |
| 2 | `BuildConfig` 類 P0 欄位 release 後為空 | 反編 `app/build/generated/source/buildConfig/release/**/BuildConfig.java` grep `"sk-"` / `"sk-ant"` 無命中 | 同上 |
| 3 | `classes.dex` 真值字串篩 | `strings app/build/outputs/apk/release/app-release.apk | grep -E "sk-[A-Za-z0-9_-]{20,}"` 無命中 | 同上 |
| 4 | `.gitignore` 涵蓋 `.env`、`keystore/`、`google-services.json` | diff check | CI（若 S13+ 上 CI） |
| 5 | `HttpLoggingInterceptor` 未在 release 啟用 | grep codebase `HttpLoggingInterceptor(` 結果皆在 `BuildConfig.DEBUG` 分支內 | 同上 |

### F.2 scan 時機建議

- **本地 release 前**：跑 `scripts/release_scan.sh`（手動），失敗即 block build。
- **Firebase App Distribution 前**：pre-distribution hook（未來 CI）。
- **重大 release 前**：人工 + 自動雙道。

**本 round 不提供 scan 腳本實作**（S12-3.1 交付），僅確立 policy 需求。

### F.3 false positive 白名單

- `proguard-rules.pro` 內 `-keep class com.google.crypto.tink.**` 不是 key 真值。
- 任何 `.md` / `.txt` 內的 `sk-` 範例字串（例如本文件）如有，視情況加入白名單。

---

## G. Scope 排除（明確不做）

| 項 | 理由 | 建議 round |
|---|---|---|
| 真正移除 APK P0 key 真值（實作） | 本 round 是 policy | S12-3.1 |
| AI key proxy 全收斂至 Insight server | 跨 repo + legal / 計費影響 | S13+ |
| Supabase key 收斂到 gateway 單例 | §C.2.c-1，架構重構 | S13+ |
| `HttpLoggingInterceptor` 黑白名單 self-check | 跨模組 runtime 改動 | S13+ |
| CI 自動化 scan | 目前無 CI | S13+（需先建 CI） |
| 商用 ProGuard string encryption | 成本 / 工具鏈變更 | 長期評估 |
| `NEWEBPAY_HASH_KEY/IV` | S12-1 / S12-2 deferred | 已在 ZCoin defer note（CCA §A.1 亦確認不擴） |
| `INVITE_HMAC_SECRET`（runtime 路徑 audit） | S12-0 P0 finding，本 round CCB runtime audit 不擴 | S13+ 專門 round；**但 release contract 層由 CCA §B.1 / §D.1 / §D.2 覆蓋，本 CCB 承認並於 §K.3 reconcile** |
| `KEYSTORE_PASSWORD` | build-infra 範疇 | S13+ 專門 round |

**§G 修訂**（AMENDED_POST_CCA）：INVITE_HMAC_SECRET 的 CCB runtime 路徑 audit 仍排除本 round，但 CCA §D.1 / §D.2 已把該 key 的 release contract 納入覆蓋；兩份邊界互補而非衝突。詳見 §K.3。

---

## H. 跟 CCA 的銜接點

### H.1 CCA 需額外確認項（請雪鑰 review 時對照）

1. **Supabase `Authorization: Bearer <anonKey>` 路徑是否在 `SupabaseClient.buildRequest`？**
   - CCB 未逐行讀完該檔，但 `buildRequest` 慣例應該是 anonKey 作 Bearer；CCA 請確認文案在 release policy 中正確描述。
2. **`AISettingsSection.kt:113-114` 為何直接讀 BuildConfig 而非走 gateway？**
   - 屬 consume/UX 側判斷（user 看得到哪些預設），CCA 請判斷是 scope 內問題還是保留現狀。
3. **user-supplied AI key 的 UX 要求**：
   - Release 走 §E.3 路徑 A 後，user 首次用 AI 功能會拿不到 key → UX 提示流是否 already covered？
4. **release policy 對「沒 AI key 怎麼辦」的預設提示文案**：
   - 建議由 CCA 側敲文案（例：「尚未設定 AI 金鑰，請至設定 → AI 服務輸入」）。

### H.2 雙份收斂後的期待

CCA + CCB 兩份 policy 收齊 → 玄樞 gate verdict → 若 READY，進 S12-3.1（實作 round）或由董事長指定下一步。

---

## I. CCA review 待看項（§E CCB §E.X 對應 CCA-facing summary）

1. §B.2 的 P0/P1/P2 分級是否需微調？（CCA 可能對「哪些算 P0」有 UX / 法務觀點）
2. §E.1 BuildConfig 註解 band 要不要直接進 gradle？（建議本 round 純 policy，留 S12-3.1）
3. §E.3 release 攜帶契約，採路徑 A 還是 B？（CCB 偏好 A）
4. §E.4 `AIProviderManager` fallback telemetry 是否需 CCA UX 面提示「安全儲存失敗」？
5. §F scan 腳本落點是否同意 `scripts/release_scan.sh`？
6. §H.1 四個 CCA 對照問。

---

## J. 變更摘要（依 CLAUDE_CODEX_COLLAB_GUIDE 格式）

**A. 變更摘要**
- 純 policy 文件（`docs/SPRINT12_3_CCB_API_KEY_RUNTIME_POLICY_2026-04-19.md`）。
- 交叉比對 `app/build.gradle` 11 條 buildConfigField、9 個 consumer 檔、Log 呼叫點、proguard 規則、`.gitignore`、git 歷史。
- 提出 5 條 policy 契約（E.1–E.5）+ 5 項 scan 項（F.1）。
- 明確排除 9 項（§G），**不動 .kt / .gradle**。

**B. 風險**
- 文件本身無 runtime 風險。
- 若 CCA 對 P0/P1 分級有歧見，§C.5 / §E.3 的 release 契約會需調。

**C. 下一步建議**
- CCA 完成 `S12-3-CCA-API-KEY-RELEASE-POLICY`（雪鑰）。
- 兩份收齊送 `玄樞` gate。
- gate READY 後開 S12-3.1（實作：E.1 註解 band + E.3 release 契約路徑 A + E.4 fallback telemetry），不在本 round。

**D. 架構決策**
- 確立「P0 / P1 / P2 三級分類」作為往後新增 BuildConfig key 的強制 policy。
- `telemetry/ErrorReporter`（S12-4 新建）新增第二個 consumer（AIProviderManager fallback），確立跨 round 的 observability 收斂方向。
- 不引入新 gateway / 不動既有 runtime 類。

**E. 玄樞審核需求 / 結論**
- 需要 `玄樞` gate：`READY / NOT READY / LOCKED`（與 CCA 同時送審）。
- 本輪是 policy round，READY 判定著重「policy 是否完備 + 與 CCA 一致」，不需要 runtime 驗證。
- 本文件已於 2026-04-19 CCA 交付後追加 §K 做 reconcile；若 §K 的 reconcile 方式本身需玄樞調整，請於 gate verdict 明示。

---

## K. CCA Handoff 對應（Post-CCA Reconcile — AMENDED 2026-04-19）

本段於 CCA 交付 `docs/SPRINT12_3_CCA_API_KEY_RELEASE_POLICY_2026-04-19.md` 後追加，用途：
1. 對齊兩份 policy 的分級、scope 邊界、runtime 落地期望
2. 把 CCA 明確點名「CCB 落地」的條款納入 CCB 承接清單
3. 不改 CCA 文件；所有 reconcile 動作在 CCB 側完成

### K.1 分級 reconcile — GIPHY / OPENWEATHER 由 P0 降 P1

**歧見來源**
- CCB 原 §B.2：列 `GIPHY_API_KEY` / `OPENWEATHER_API_KEY` 為 **P0-leak-hard**（安全視角 — any APK-embedded key = P0）
- CCA §B.3：列為 **P1**（產品視角 — 配額被偷的代價產品面可接受）

**Reconcile 結論：採 CCA 分級**

採用理由：
1. CCA 是 release / product 線的對口，P0/P1 邊界本質是「release 是否容許攜帶真值」的產品決策
2. CCB §C.5 R8 string literal 不混淆 finding 對 GIPHY / OPENWEATHER 仍成立（真值會落 APK 明文），但**這是暴露路徑事實，不是分級定義**。分級是「暴露後要不要阻擋」的 policy judgment，該層由 CCA 主導
3. CCA §D.1 release contract：GIPHY / OPENWEATHER 「release 可帶真值（接受配額風險）」；若 CCB 強持 P0，會使 §E.3 release 必空契約自動套用到這兩 key，產生 release 後 GIF / 天氣面板永遠 stub 的後果，CCA §D.3 已明示這不是期望行為

**修訂落點**
- §B.2 表格已做 surgical edit（刪除線 + 新 P1 列）
- 連動修訂：§C.5 「P0 AI key」文案無誤（本文原就只點名 AI key）；§E.3 「release 必空契約」對象本文原表述為 `P0-leak-hard AI key`，正確收斂到 OPENAI / ANTHROPIC，不需改

**P1 release 行為**（承接 CCA §C.3.2 + §D.1）
- release build 帶真值 OK
- 但需在 Settings UI 明示「由 SnowRealm 平台共用 key 提供」（CCA §C.4 文案）— 屬 CCA UX 落地，非 CCB scope
- CCB 層追加：`GiphyApiClient` / `WeatherManager` blank 時 user-visible 行為要求，見 §K.2.3

### K.2 CCA 指派給 CCB 的 runtime 落地承接

#### K.2.1 Whisper 路徑 BuildConfig 反讀 — precedence 反轉

**CCA 源條款**：§C.3.1 + §E.2

**現況**（`ImeServiceInput.kt:255-256` / `SpeechSettingsSection.kt:266-268`）：
```kotlin
BuildConfig.OPENAI_API_KEY.takeIf { isNotBlank() } ?: userKey
```
→ BuildConfig 先、user key 後。release 若攜帶 OPENAI 真值，user 在 AISettingsActivity 輸入的 key 永遠不生效。

**CCB 認定**：違反 AISettingsActivity 「使用者覆寫優先」直覺，屬 bug。

**但**：CCA §E.2 將此列為「**by design?**」的開放問題給玄樞 / 董事長拍板。可能情境：
- (a) bug — 應反轉為 `userKey.takeIf { isNotBlank() } ?: BuildConfig.OPENAI_API_KEY`
- (b) by design — 刻意讓平台 Whisper 額度永遠可用，user key 只是 fallback

**CCB policy 條款（待玄樞拍板）**：
- 若判 (a)：納入 S12-3.1 實作落地項，修 2 處 call site
- 若判 (b)：不改 code，但 CCA §C.4 文案需補「Whisper 語音恆用平台 key」明示，CCB policy 追加 logging 要求（Whisper 走哪條 key 路徑必須經 telemetry 上報 category/source）

CCB 偏好：(a) — 與 §E.3 路徑 A「release 不攜帶 P0 AI key」自然一致；若 release .env 已剝除真值，反轉 vs 現況行為等價，風險最低。

#### K.2.2 Build-time fail 條件承接（CCA §D.2）

CCA §D.2 列 release task 4 條 fail 條件。對照 CCB 現有條款：

| CCA §D.2 # | 條件 | CCB 現有覆蓋 | 差異 |
|---|---|---|---|
| 1 | `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` 非空 → fail | §E.3 路徑 A（gradle override 為空）+ §F.1-1/2 scan 覆蓋 | ✅ 一致 |
| 1 | `NEWEBPAY_*` 非空 → fail | §G 排除 + CCA §A.1 確認 S12-2 DEFERRED 處理 | ✅ 一致 |
| 2 | `INVITE_HMAC_SECRET == "yukiboard_dev_2026"` → fail | §G 原排除 | ❌ 新增承接，見 §K.3 |
| 3 | `PLATFORM_API_KEY` blank → fail | 未明示；CCB 原視為 P1 可帶但未規範 release 必帶 | ❌ 新增承接，見下段 |
| 4 | CI grep 已編譯 APK / dex 字面值 | §F.1-3 已覆蓋（dex strings grep `sk-`） | ✅ 一致，CCB 範圍更窄（僅 AI key 字首） |

**新增承接條款（A）**：`PLATFORM_API_KEY` blank → fail release
- policy 條款（CCB 新增）：release build 偵測 `PLATFORM_API_KEY` blank → build fail
- 理由：CCA §B.1 / §D.1 列 PLATFORM release「必真值」；CCB InsightApiClient 無 PLATFORM key 時整個雲端通訊鏈失效（CCA §D.3 已明示 user-visible error 行為），release 出貨必帶
- 實作落點：追加至 §F.1 scan 表的第 6 項（scan 腳本 S12-3.1 交付）

**新增承接條款（B）**：`INVITE_HMAC_SECRET` dev 預設 fail → 見 §K.3

#### K.2.3 Runtime fallback UX 規範承接（CCA §D.3）

CCA §D.3 定義 5 情境 user-visible 行為。CCB policy 承接如下：

| 情境 | CCA 期望行為 | CCB runtime 落地責任 | 落地 round |
|---|---|---|---|
| AI key 全空 | AISettings 預設 SNOWREALM；hasValidConfig() 維持現邏輯 | `AIProviderManager.importBuildConfigKeysIfNeeded()` 行為需改（見 CCA §C.3.3），屬 provider 層 | S12-3.1（依 §K.2.1 玄樞裁定） |
| `GIPHY_API_KEY` blank | GIF 面板顯示「需 Giphy key — 此功能未啟用」（不 silent empty） | `GifPanelView` 渲染路徑需檢查 `GiphyApiClient.isAvailable()` 並顯示空狀態訊息 | S12-3.1 |
| `OPENWEATHER_API_KEY` blank | 天氣面板顯示「需設定天氣服務 key」 | `WeatherManager.fetchFromApi()` blank 時回特定 sealed result，UI 層渲染訊息 | S12-3.1 |
| `PLATFORM_API_KEY` blank | InsightApiClient / InsightStickerApi 顯示「未設定平台 key — 雲端功能不可用」 | Gateway 層 blank-check + sealed result；UI consume | S12-3.1（與 §K.2.2 PLATFORM fail build 搭配） |
| `INVITE_HMAC_SECRET` predefault | InviteCodeManager 強制拒絕所有邀請碼驗證 | `InviteCodeManager.kt` 啟動時檢查 BuildConfig 值是否為 dev 預設，是則 `isVerified=false` 永遠返回 | S12-3.1（與 §K.2.2 INVITE fail build 搭配雙保險） |

**policy 條款（CCB 新增，E.6）**：
> 所有 P0 / P1 key 的 consumer 在遇到 blank / dev-default 時，**必須**以 user-visible error message 呈現（不得 silent empty / silent fail）。具體文案由 CCA UX 側敲（CCA §D.3 已列草案）。

**落地成本評估**（for S12-3.1 實作 round）：
- `GifPanelView` / `WeatherWidget` / `InsightStickerApi` consumer：估 5 處改動
- `InviteCodeManager` dev-default guard：估 1 處改動（init block）
- 無新 gateway / 無新架構，僅 rendering 分支

### K.3 Scope 邊界 reconcile — INVITE_HMAC / NEWEBPAY

**歧見來源**
- CCB §A / §G：將 `INVITE_HMAC_SECRET` / `NEWEBPAY_*` 排除在本 round scope 外
- CCA §A.1 / §B.1：`INVITE_HMAC_SECRET` 納入 scope 並列 P0；`NEWEBPAY_*` 以提醒方式納入並同時註明 S12-2 DEFERRED 已 cover

**Reconcile 結論**

兩份 scope 本質上**互補而非衝突**：
- CCA 是 release contract 線：看「每支 key release 是否可帶真值」— 自然 cover 所有 BuildConfig key（寬範圍）
- CCB 是 runtime 路徑 audit 線：看「每支 key 在 code / log / network 如何流動」— 本 round 聚焦 5 類 app-consumable key（窄範圍）

**CCB 接受以下 policy 認知**（不擴 CCB runtime audit scope）：
1. `INVITE_HMAC_SECRET` 的 release contract（必真值、預設 `yukiboard_dev_2026` fail build）依 CCA §D.1 / §D.2，**CCB 承認**並在 §K.2.2 / §K.2.3 承接 2 條 runtime 落地條款（fail build + runtime reject invite）
2. `INVITE_HMAC_SECRET` 的 runtime 消費路徑（`InviteCodeManager.kt:146` HMAC 簽章流程、logging 檢查、ProGuard rule 比對）**保留** S13+ 專門 round（原 §G 表述維持）
3. `NEWEBPAY_*` 完全依 S12-2 DEFERRED + Z 幣解凍後重啟，兩份一致不擴

**審查友善性**：玄樞 gate 看兩份時，INVITE_HMAC 不會看到「兩份互打 in-scope / out-of-scope」—
- 在 release contract 維度：兩份都 in-scope（CCA 主導、CCB 承接落地）
- 在 runtime audit 維度：兩份都延後（CCA 未 audit runtime、CCB 明示 S13+）

### K.4 §B.2 分級最終版（AMENDED 後）

| Key | 原 CCB §B.2 | AMENDED 後 | 理由 |
|---|---|---|---|
| `OPENAI_API_KEY` | P0-leak-hard | P0-leak-hard | 維持 |
| `ANTHROPIC_API_KEY` | P0-leak-hard | P0-leak-hard | 維持 |
| `GIPHY_API_KEY` | P0-leak-hard | **P1-leak-gated** | §K.1 採 CCA §B.3 |
| `OPENWEATHER_API_KEY` | P0-leak-hard | **P1-leak-gated** | §K.1 採 CCA §B.3 |
| `YUKIBOARD_SUPABASE_ANON_KEY` | P1-leak-gated | P1-leak-gated | 維持；CCA §B.3 列 P2 但附註「仍需 RLS」，CCB 採 P1 更保守，CCA §E.4 開放問題拍板後再調 |
| `PLATFORM_API_KEY` | P1-leak-gated | P1-leak-gated | 維持；新增 §K.2.2 PLATFORM blank fail build 條款 |
| `YUKIBOARD_SUPABASE_URL` | P2-non-secret | P2-non-secret | 維持 |
| `INSIGHT_API_BASE_URL` | P2-non-secret | P2-non-secret | 維持 |

**CCA-only scope key（CCB 承認 release contract，runtime audit 延後）**：
- `INVITE_HMAC_SECRET` — CCA P0 / CCB 承認但 runtime audit 延後 S13+
- `NEWEBPAY_*` — 兩份一致：S12-2 DEFERRED，不擴

### K.5 S12-3.1 實作 round 預期落地項（合併版）

若玄樞 gate 判 `READY`，S12-3.1 實作 round 應一次完成以下 policy 落地（整合 CCB 原 §E + §K 新增）：

| # | 來源 | 條款 | 修改點 | 預估規模 |
|---|---|---|---|---|
| 1 | §E.1 | BuildConfig 分級註解 band | `app/build.gradle` 11 條欄位上方註解 | 11 行 |
| 2 | §E.3（路徑 A） | release variant override P0 AI key 為空 | `app/build.gradle` `android.buildTypes.release` 區塊 | ~4 行 |
| 3 | §E.4 | AIProviderManager fallback telemetry | `AIProviderManager.kt:44-47` 新增 ErrorReporter 上報 | ~6 行 |
| 4 | §K.2.1（若玄樞判 bug） | Whisper precedence 反轉 | `ImeServiceInput.kt:255-256` + `SpeechSettingsSection.kt:266-268` | 2 處 |
| 5 | §K.2.2 | `PLATFORM_API_KEY` blank → release fail | `scripts/release_scan.sh` 新增檢查 | 1 項 |
| 6 | §K.2.2 | `INVITE_HMAC_SECRET == dev default` → release fail | 同上 | 1 項 |
| 7 | §K.2.3 | 5 情境 runtime fallback UX | `GifPanelView` / `WeatherManager` / `InsightStickerApi` / `InviteCodeManager` consumer 補 blank-branch user-visible message | ~5 處 |
| 8 | §F.1 | `scripts/release_scan.sh` 主骨架（5+2 項掃描） | 新檔 | ~80 行 shell |
| 9 | （CCA §C.3.3） | AIProviderManager 首次安裝預設 `SNOWREALM` | `AIProviderManager.importBuildConfigKeysIfNeeded()` logic | ~10 行 |

總規模：~8 處 .kt / 1 處 .gradle / 1 新 shell 腳本 / 11 行註解。

**注意**：#4 / #9 需玄樞先裁定 §K.2.1 + CCA §E.1 / §E.2 的開放問題（4 項），才能進 S12-3.1。若暫不裁定，S12-3.1 可先交付 #1-3, #5-8，保留 #4 / #9。

### K.6 CCA 兩份收齊後的玄樞 gate 聚焦建議

請玄樞 gate verdict 時特別審：

1. **§K.1 分級 reconcile 是否合理** — CCB 接受 CCA 分級的邏輯（P0/P1 是 release 決策，非暴露事實）是否成立
2. **§K.2.1 Whisper precedence — bug or by design** — 需玄樞 / 董事長明示
3. **§K.2.2 PLATFORM blank fail build 納入** — CCB 新增條款，CCA 原 §D.2 有此意但未明文
4. **§K.2.3 fallback UX 5 情境** — CCB 承接責任範圍是否 OK（屬 runtime consumer 落地，不越 CCA UX 線）
5. **§K.3 INVITE_HMAC scope reconcile** — 兩份 in/out-of-scope 說法是否協調

以上 5 點若都 `READY`，則 S12-3 policy round 整體 `READY`，進 S12-3.1 實作 round。

---

## L. 變更摘要追加（§J 之後，AMENDED_POST_CCA）

**A. 變更摘要（補）**
- 追加 §K「CCA Handoff 對應」6 小節
- §B.2 分級表 surgical edit（GIPHY / OPENWEATHER P0→P1）
- §G scope 表註記 INVITE_HMAC 的 release contract 由 CCA 覆蓋
- status header 改 `AMENDED_POST_CCA — PENDING 玄樞 GATE`

**B. 風險（補）**
- §K.2.1 Whisper precedence 開放問題未拍板，S12-3.1 #4 規模待定
- §K.4 SUPABASE_ANON CCB 採 P1 / CCA 採 P2，CCA §E.4 RLS 驗證拍板後可能需再調
- 修訂未改任何 .kt / .gradle，policy 文件內一致性為主要風險面

**C. 下一步建議**
- 兩份（CCA + AMENDED CCB）同送玄樞 gate
- gate verdict 同時處理 CCA §E.1-E.4 + CCB §K.2.1 + §K.6 共 5 + 4 個開放問題
- READY 後開 S12-3.1（~8 處 .kt + 1 gradle + 1 shell）

**D. 架構決策（補）**
- 確立 policy 分級由「release contract 側」主導（CCA 分級為準）
- 確立 runtime fallback UX 規範由 CCB 承接落地責任，CCA 敲文案 → CCB 落 consumer 分支
- Scope 邊界原則：「release contract 寬 / runtime audit 窄」作為兩線分工通則

**E. 玄樞審核需求 / 結論（補）**
- §K 新增 reconcile 合理性本身需玄樞審（§K.6 五項）
- 若 §K.6 1-3 READY 但 4-5 需 CCA 再調，CCB 可依玄樞指示再做第三輪修訂
- 本 CCB 不越 CCA 側做 UX 文案 / 釋義工作

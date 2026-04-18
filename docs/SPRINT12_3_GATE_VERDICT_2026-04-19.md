# Sprint 12-3 Gate Verdict — API Key Release Policy

日期：2026-04-19
Gate 身份：`雪鑰`（CCA 代行 `玄樞`）
審查對象：
- `docs/SPRINT12_3_CCA_API_KEY_RELEASE_POLICY_2026-04-19.md`（雪鑰 CCA — release / consume / contract）
- `docs/SPRINT12_3_CCB_API_KEY_RUNTIME_POLICY_2026-04-19.md`（鍵律 CCB — runtime / build / logging / scan；§K AMENDED_POST_CCA 版本）
CH 順序：CH-1（修訂後）~~S12-1~~ → ~~S12-2 DEFERRED~~ → ~~S12-4~~ → **S12-3** → S12-5

**Amendment note（本 verdict 於 CCB §K 交付後修訂）**：CCB 於 CCA 交付後追加 §K 主動 reconcile（接受 CCA P1 分級、新增 PLATFORM blank fail build 條款、承接 fallback UX 5 情境、明示 INVITE_HMAC scope 邊界）。原 §C 的主要分歧已消解，詳見本 verdict §J。

---

## A. 結論

**Gate 判定：`READY`**（post-CCB-§K amendment；原初稿 `READY with 1 needs-arbitration` 的分歧項已消解）

1. 兩份 policy 文件 scope 互補、零實作越界（CCA 不寫 .gradle / proguard，CCB 不寫 UX wording / consent flow），符合 `feedback_role_boundary.md`。
2. 對 release contract 的核心立場一致：P0 AI key 不得進 release APK；採「release variant 強制空值 + scan 雙重保險」。
3. 雙份共同識別四個 finding 互相印證（§B.1）；雙份**互補補強**八個盲點（§B.2）。
4. ~~一個分歧需玄樞拍板：**GIPHY / OPENWEATHER / SUPABASE_ANON 的 P 級**（§C）—— 不阻擋本 gate，但需要在 S12-3.1 實作前定案。~~ → **已由 CCB §K.1 主動採納 CCA P1 分級消解**；SUPABASE_ANON 兩方一致延後到 RLS audit 拍板（§J.1）。
5. CCB §H.1 四個 CCA 對照問已於本 verdict §D 回應（含 1 條 CCB 認知修正）。
6. CCB §K 新增三條承接條款（PLATFORM blank fail build / fallback UX 5 情境落地責任 / INVITE_HMAC scope 邊界）全部 CCA 接受，收錄進 §J.2。

**S12-3 policy round 結案**。S12-3.1 實作 round 待開（§J.3 合併 9 項落地），需先解 §J.4 三項玄樞 / 董事長仲裁（Whisper precedence / SNOWREALM default trial / Supabase RLS audit）。S12-5 維持 `LOCKED`。

---

## B. 雙份對照

### B.1 共同識別（高信心 finding）

| 主題 | CCA 位置 | CCB 位置 | 一致結論 |
|---|---|---|---|
| OPENAI/ANTHROPIC release 不可帶真值 | §B.3 P0 / §D.1 | §B.2 P0 / §E.3 | release variant 強制空值（CCB 路徑 A） |
| `AIProviderManager.importBuildConfigKeysIfNeeded` 靜默 seed | §C.1 / §C.3.3 | §C.2.b-2 | release 後 .env 必須清空，否則 seed 進所有使用者裝置 |
| `INVITE_HMAC_SECRET` 預設值 P0（S12-0 已標） | §B.3 | §C.1-2 / §G | 不在本 round scope，留 S13+ 專門 round |
| `.env` 已 git-ignored | §F.1 假設 | §C.1-1 verified（git log 無歷史 commit） | ✅ 安全 |

### B.2 雙份互補補強（單份漏掉的點）

| 補強項 | 來源 | 對方為何漏 | 處理 |
|---|---|---|---|
| **HttpLoggingInterceptor 引入但未啟用**（零 logcat 外洩） | CCB §C.3-2 | CCA 沒 grep runtime logging | ✅ 採納，併入合併 policy |
| **AIProviderManager EncryptedSharedPreferences fallback 明文風險** | CCB §C.2.b-3 + §E.4 | CCA 沒讀 33-47 行 fallback 分支 | ✅ 採納，併入合併 policy |
| **三個 SUPABASE consumer 未收斂到 gateway**（架構 debt） | CCB §C.2.c-1 | CCA 視為架構問題、未列 | ✅ 採納為 S13+ follow-up |
| **BuildConfig 分級註解 band**（self-documenting） | CCB §E.1 | CCA 只寫 wording，未提 build.gradle 註解 | ✅ 採納為 S12-3.1 實作項 |
| **NEXT_PUBLIC_GIPHY/UNSPLASH 在 Insight 客戶端 bundle 暴露** | CCA §F.2.2 | CCB scope 限本 repo | ✅ 採納為 cross-project follow-up |
| **「我的 key vs 平台 key」consent dialog** | CCA §C.3 / §C.4 wording | CCB scope 限 runtime/build | ✅ 採納為 S12-3.1 UX 工作 |
| **預設 provider 改 SNOWREALM**（避免 silent 用平台 key） | CCA §C.3.3 / §E.1 | CCB scope 限 runtime/build | ✅ 採納為 S12-3.1 UX 工作（仍待 §C 開放問題定案） |
| **Whisper 路徑 BuildConfig 反讀使用者 key** | CCA §B.2 / §E.2 | CCB §B.1 列了 consumer 但未旗 policy 衝突 | ✅ 採納為 S12-3.1 必修項 |

### B.3 評估：CCB policy 分級補強了 CCA wording 補強了 CCB

兩份在「policy 紅線 vs 使用者體驗」兩面都到位，無遺漏實質風險面向。

---

## ~~C. 分歧 — P 級分級（待玄樞拍板）~~ → RESOLVED by CCB §K.1（post-amendment）

> **註記**：本段為 CCA + CCB 初交付後的原 gate 分析，CCB 於 §K.1 主動採納 CCA P1 分級後**整段作廢**。保留劃刪除線做 audit trail，最終認知請看 §J.1。

### ~~C.1 對照表~~

| ~~Key~~ | ~~CCA 分級~~ | ~~CCB 分級~~ | ~~分歧理由~~ |
|---|---|---|---|
| ~~`OPENAI_API_KEY`~~ | ~~P0~~ | ~~P0~~ | ~~一致~~ |
| ~~`ANTHROPIC_API_KEY`~~ | ~~P0~~ | ~~P0~~ | ~~一致~~ |
| ~~`INVITE_HMAC_SECRET` 預設值~~ | ~~P0~~ | ~~（S12-0 範疇）~~ | ~~一致~~ |
| ~~`GIPHY_API_KEY`~~ | ~~**P1**~~ | ~~**P0-leak-hard**~~ | ~~CCA：免費層配額被偷=GIF 降速可接受；CCB：rate limit DoS 風險~~ |
| ~~`OPENWEATHER_API_KEY`~~ | ~~**P1**~~ | ~~**P0-leak-hard**~~ | ~~同上~~ |
| ~~`PLATFORM_API_KEY`~~ | ~~P1~~ | ~~P1~~ | ~~一致~~ |
| ~~`YUKIBOARD_SUPABASE_ANON_KEY`~~ | ~~**P2**~~ | ~~**P1-leak-gated**~~ | ~~CCA：anon 設計即 public；CCB：anon role 預設可讀的 table 仍會被讀~~ |
| ~~`INSIGHT_API_BASE_URL`~~ | ~~P2~~ | ~~P2~~ | ~~一致~~ |
| ~~`YUKIBOARD_SUPABASE_URL`~~ | ~~P2~~ | ~~P2~~ | ~~一致~~ |

### ~~C.2 仲裁建議（非結論，給玄樞參考）~~

- ~~**GIPHY / OPENWEATHER**：兩種立場都成立，差別在「release contract 是否強制空值」。CCA 立場 = release 帶真值 + 文案 + S12-5 配額監控；CCB 立場 = release 強制空值 + user-supplied or proxy。**CCA 立場較貼合現產品「不需使用者帳號就能用 GIF / 天氣」的 UX 假設**；CCB 立場較貼合「最小 APK 暴露面」的安全潔癖。**建議玄樞傾向 CCA**（保持 release 帶真值），但接受 CCB 的「兩支 key 升 P0 對待」=> release 做用量監控 + key rotation SLA + 第二層 origin restriction（Giphy 後台可設）。~~ → **CCB §K.1 已直接採 CCA P1**
- ~~**SUPABASE_ANON_KEY**：CCB 觀點更嚴謹。**anon 公開的前提是 RLS 100% 鎖死所有可寫 / 可敏感讀的 table**，這個前提目前**沒有人驗證過**（CCA §E.4 開放問題）。在 RLS 未驗證前，**建議玄樞採 CCB 的 P1 分級**，並把「Supabase RLS audit」開為 S13+ 工作。~~ → **兩方一致採 CCB P1 為 interim 分級，待 Supabase RLS audit 拍板後調整，詳 §J.1**

### ~~C.3 對 release 立即影響~~

~~無論 P 級如何收斂，**P0 的 OPENAI/ANTHROPIC release 不可帶真值**這個結論不變。本 gate 結案不需先解 P 級分歧。~~ → 已結論

---

## D. CCA 對 CCB §H.1 四問的回應

### D.1 H.1.1 — Supabase `Authorization: Bearer <anonKey>` 路徑？

**驗證結果**：CCB 認知**不正確**。

`SupabaseClient.kt:96-104` 的 `buildRequest`：

```kotlin
.header("apikey", anonKey)                         // ← anonKey 走 apikey header
if (authToken != null) {
    builder.header("Authorization", "Bearer $authToken")  // ← Bearer 走 user JWT
}
```

anonKey 走 `apikey:` header（Supabase 標準慣例），`Authorization: Bearer` 攜帶的是**使用者 JWT**（Insight 登入的 token，用於 RLS）。**anonKey 不會走 Bearer**。

CCA wording 已正確：未在 release policy 中誤述。

### D.2 H.1.2 — `AISettingsSection.kt:113-114` 為何直讀 BuildConfig？

**驗證結果**：是因為 `FraudModelManager.manualUpdate()` 採參數注入式 contract，要求 caller 直接傳 `supabaseUrl` + `supabaseAnonKey`（而非從 `SupabaseClient.getInstance()` 內部讀）。屬**架構 debt**：FraudModelManager 應改為 inject SupabaseClient 而非 caller 端傳 raw key。

CCB §C.2.c-1 的「三個 consumer 未收斂」分析正確。**CCA 確認此項列為 S13+ 收斂工作，本 round 不擴 scope**。

### D.3 H.1.3 — Release 走 §E.3 路徑 A 後，user 首次用 AI 拿不到 key 的 UX？

**CCA 立場**：與 §C.3.3 提案綁定 —

1. AISettings 預設 provider 改為 `SNOWREALM`（邀請碼路徑），**不依賴使用者填 key 也能用 AI**（CCA §C 開放問題 E.1）
2. 使用者主動切到 OPENAI / ANTHROPIC 時跳一次性 consent dialog（CCA §C.3.3）
3. dialog 文案 = CCA §C.4 「留空= 使用 SnowRealm 平台 key（消耗平台額度）；填入= 使用您自己的 OpenAI 帳號計費」
4. 「沒 AI key 怎麼辦」的 UX 流程，建議由 SNOWREALM provider 兜底（前提：邀請碼可低門檻取得，待 §C 開放問題 E.1 拍板）

→ 此項在 CCA 文件已 covered；不需 CCB 端再補。

### D.4 H.1.4 — 「沒 AI key 怎麼辦」的預設提示文案？

CCA §C.4 已給：
- AISettings 切換到 OPENAI/ANTHROPIC 第一次：「此 Provider 需要 API Key。可填入自己的 key，或保持空白使用平台預設額度（受公平使用限制）。」
- 若採 §C.3.3 預設 SNOWREALM 方案，使用者進入 AISettings 不會看到「沒 key」狀態，僅在主動切 OpenAI/Anthropic 時提示。

→ 此項在 CCA 文件已 covered。

---

## E. 合併 policy 收斂表（gate 後生效）

下列項全部 READY 進入 S12-3.1 實作 round（不在本 gate scope，僅明示落地排序）：

### E.1 必做（P0 釋出守門）

| # | 項 | 來源 | 落地形式 |
|---|---|---|---|
| 1 | release variant `buildConfigField "OPENAI_API_KEY", "\"\""` | CCB §E.3 路徑 A | gradle |
| 2 | release variant 同上 ANTHROPIC | CCB §E.3 路徑 A | gradle |
| 3 | `AIProviderManager.importBuildConfigKeysIfNeeded` 預設 provider 改 SNOWREALM | CCA §C.3.3 | kotlin |
| 4 | Whisper 路徑反轉「使用者 key 優先 / BuildConfig fallback」 | CCA §C.3.1 / §E.2 | kotlin |
| 5 | AISettings 切 OPENAI/ANTHROPIC 跳 consent dialog | CCA §C.3.3 / §C.4 | kotlin + UI |
| 6 | `AIProviderManager` EncryptedSharedPreferences fallback 接 ErrorReporter | CCB §E.4 | kotlin |
| 7 | `scripts/release_scan.sh` — 5 條掃描項 | CCB §F.1 | shell |

### E.2 應做（self-documenting / 預防回歸）

| # | 項 | 來源 | 落地形式 |
|---|---|---|---|
| 8 | `app/build.gradle` 11 條 buildConfigField 加 [P0/P1/P2] 註解 band | CCB §E.1 | gradle 註解 |
| 9 | CLAUDE.md 加新 key 流程 gate 章節 | CCB §E.5 | docs |
| 10 | AISettings 加「GIF / 天氣使用平台 key」說明文案 | CCA §C.4 | UI |

### E.3 留 S13+

| # | 項 | 來源 |
|---|---|---|
| 11 | Supabase 三個 consumer 收斂到 SupabaseClient gateway | CCB §C.2.c-1 / CCA §B.2 confirm |
| 12 | Supabase RLS audit | CCA §E.4 |
| 13 | Insight `NEXT_PUBLIC_GIPHY_API_KEY` / `NEXT_PUBLIC_UNSPLASH_ACCESS_KEY` 改 server proxy | CCA §F.3 |
| 14 | PLATFORM_API_KEY 共享密鑰本質的 Insight 端二層加固 | CCA §E.3 |
| 15 | `INVITE_HMAC_SECRET` release 必真值 + 預設 fail build | CCA §C.3.1 / CCB §C.1-2 |
| 16 | `KEYSTORE_PASSWORD` 等 build-infra 系列 | CCB §G |
| 17 | CI 化 release scan + APK strings 自動掃描 | CCB §F.2 |

### ~~E.4 待玄樞拍板才能進 S12-3.1~~ → 由 §J.4 取代

| ~~#~~ | ~~項~~ | ~~阻塞原因~~ |
|---|---|---|
| ~~A1~~ | ~~GIPHY / OPENWEATHER 是 P0 還是 P1~~ | ~~§C.2 仲裁~~ → CCB §K.1 採 CCA P1，已消解 |
| ~~A2~~ | ~~SUPABASE_ANON 是 P1 還是 P2~~ | ~~§C.2 仲裁~~ → 兩方一致採 CCB P1 為 interim，待 RLS audit 拍板 |
| ~~A3~~ | ~~預設 provider 改 SNOWREALM 是否需要 device-bound trial 邀請碼~~ | ~~涉及 Insight 端產品決策~~ → 移至 §J.4 |

---

## F. Follow-up 觀察（非阻擋，存檔）

### F.1 CCB §C.2.b-3 fallback 路徑值得正視

EncryptedSharedPreferences 失敗的觸發條件不常見（KeyStore 損毀 / OEM 客製化 ROM），但**一旦觸發就是 AI key 明文落地 `/data/data/.../shared_prefs/`**。E.1 第 6 項（telemetry 上報）是必要起點，但長期應加：

- 偵測 fallback 即**清掉所有 AI key**（強制使用者重新輸入）+ 提示對話框
- 或 fallback 即**禁用 OPENAI/ANTHROPIC provider**，強制走 SNOWREALM

此項非 S12-3.1 scope，留為 S13+ follow-up。

### F.2 release_scan.sh 與 CI 整合時機

CCB §F.2「Firebase App Distribution 前 pre-distribution hook」目前無 CI；本地 release 前手動跑 `release_scan.sh` 是合理起點。建議 S13+ 評估：

- GitHub Actions / 自架 runner 跑 release scan
- App Distribution 上傳前的 hook 可用 `firebaseAppDistribution` task 的 `doFirst` 注入 scan

### ~~F.3 P0/P1 分歧的折衷可能~~ → MOOT（CCB §K.1 已直採 CCA P1）

~~若玄樞不想直接選 CCA 或 CCB 立場，第三條路：~~

- ~~**GIPHY / OPENWEATHER 維持 release 帶真值（CCA 立場），但加「key 月用量觀測 + 1 個月內可 rotate」SLA**（屬 S12-5 observability）~~
- ~~**SUPABASE_ANON 維持 release 帶（不可避免，anon 設計就是給 client）但加 S13+ RLS audit 為阻塞前提**~~

~~此折衷把 P 級爭議轉成「執行面 SLA」而非「分類爭議」，或許更符合工程現實。~~

→ S12-5 配額觀測 SLA 仍合理，移至 §J.5 為 S12-5 / S12-3.1 follow-up；其餘討論不再需要。

---

## G. Gate 輸出動作

`READY` 判定下：

1. 更新 `Sprint12-All-Round-Gate.md`：
   - S12-3 狀態 `ACTIVE` → `COMPLETED`（strikethrough + 指向本 gate verdict + 兩份 CCA / CCB 文件）
   - §6 玄樞預設判定補 S12-3 結論 + P 級分歧待拍板
2. S12-5 維持 `LOCKED`，由董事長拍板解鎖。
3. 本輪純 policy 文件，**無使用者可見改動**；S12-3.1 實作 round 才會產生 user-visible 變化。
4. §C 三項 P 級 / 預設 provider 仲裁等董事長 / 玄樞拍板後再開 S12-3.1。

---

## H. Sprint 12 進度快照（更新）

```
S12-0 COMPLETED  Payment / Key Audit
S12-1 COMPLETED  Newebpay Server-side Signing Plan（修訂：ZCoin 不可刪）
S12-2 DEFERRED   Android Client Payment Refactor（Z 幣後做）
S12-4 COMPLETED  Remote Keyboard PIN Hardening
S12-3 COMPLETED  API Key Release Policy（policy round；S12-3.1 實作待開）  ← 本輪結案
S12-5 LOCKED     Observability Hook
```

**Sprint 12 5 大主題目前狀態**：
1. ✅ Newebpay server signing plan + ZCoin defer + 跨 repo wire-up 路徑明示
2. ✅ Remote Keyboard PIN brute-force hardening 結案
3. ✅ API Key release policy 結案（實作留 S12-3.1）
4. ⏳ Z 幣 wire-up（DEFERRED 至 S13+）
5. 🔒 Observability Hook（LOCKED，等董事長指示）

**下一候選 round（待董事長拍板）**：
- **S12-3.1 實作 round**：執行 §J.3 9 項合併落地（需先解 §J.4 三項仲裁）
- **S12-5 Observability Hook**：CH-1 原排第 5；S12-4 已預留 `ErrorReporter` interface
- **Sprint 12 收尾 / 進 Sprint 13**：若 S12-3.1 與 S12-5 不在本 sprint 內處理

---

## I. 交付者自白

- 本 gate 由 `雪鑰`（CCA）代行 `玄樞`；gate 只做 gate，不實作；不改任何 .kt / .gradle。
- §B 對照係雙份文件互讀比對而成；§C 分歧為實質性技術觀點分歧，非文件 bug；於 CCB §K 修訂後 §C 整段失效，劃刪除線保留 audit trail。
- §D CCB §H.1 四問，第一問（Supabase Bearer 路徑）認知修正已留書面。
- §E 為原初稿合併 policy 排序，post-§J 後 §E.4 失效；S12-3.1 實際落地以 §J.3 為準。
- §F follow-up 中 F.3 失效（CCB 已採 CCA 立場）；F.1 / F.2 仍有效。
- §J 為 post-CCB-§K 修訂後的最終 reconcile 視圖，所有「以何為準」皆指向 §J。
- 不自排 S12-3.1 / S12-5 / S13 啟動順序；等董事長指示。

---

## J. Post-CCB-§K 修訂後最終 reconcile（AMENDED — 以本段為準）

CCB 於 CCA 交付後追加 `§K. CCA Handoff 對應`，主動採 CCA P1 分級、新增 PLATFORM blank fail build 條款、承接 fallback UX 5 情境、明示 INVITE_HMAC scope 邊界。本段把所有 reconcile 結果合併為單一視圖。

### J.1 P 級分類最終版

| Key | 最終分級 | 來源 / 註記 |
|---|---|---|
| `OPENAI_API_KEY` | **P0-leak-hard** | 兩方一致 |
| `ANTHROPIC_API_KEY` | **P0-leak-hard** | 兩方一致 |
| `GIPHY_API_KEY` | **P1-leak-gated** | CCB §K.1 採 CCA §B.3 |
| `OPENWEATHER_API_KEY` | **P1-leak-gated** | CCB §K.1 採 CCA §B.3 |
| `PLATFORM_API_KEY` | **P1-leak-gated** | 兩方一致；新增 §J.2 blank fail build 條款 |
| `YUKIBOARD_SUPABASE_ANON_KEY` | **P1-leak-gated**（interim） | 兩方接受採 CCB P1 為 interim，待 Supabase RLS audit（§J.4 #3）拍板後可調 P2 |
| `YUKIBOARD_SUPABASE_URL` | **P2-non-secret** | 兩方一致 |
| `INSIGHT_API_BASE_URL` | **P2-non-secret** | 兩方一致 |
| `INVITE_HMAC_SECRET` | **P0**（release contract 維度，runtime audit 延後 S13+） | CCA in-scope / CCB §K.3 承認，runtime path audit 留 S13+ 專門 round |
| `NEWEBPAY_*` | **P0**（已 S12-2 DEFERRED） | 兩方一致，等 Z 幣解凍 |

### J.2 CCB §K 新增 / 承接條款（CCA 全部接受）

| 條款 | 來源 | CCA 立場 |
|---|---|---|
| `PLATFORM_API_KEY` blank → release fail build | CCB §K.2.2 (新增) | ✅ 接受，CCA §D.2 原意已 cover，CCB 明文化更佳 |
| `INVITE_HMAC_SECRET == "yukiboard_dev_2026"` → release fail build | CCB §K.2.2 承接 CCA §D.2 | ✅ 接受 |
| Fallback UX 5 情境 runtime 落地責任由 CCB 承接 consumer 分支 | CCB §K.2.3 承接 CCA §D.3 | ✅ 接受；UX 文案由 CCA 敲，consumer rendering 由 CCB 落 |
| INVITE_HMAC scope 邊界：release contract in / runtime audit out | CCB §K.3 | ✅ 接受 |
| Whisper precedence 反轉偏好 (a)：使用者 key 優先 | CCB §K.2.1（待玄樞裁） | ✅ 偏好一致；CCA §E.2 / CCB §K.2.1 都偏 (a) |

### J.3 S12-3.1 實作 round 合併落地（CCB §K.5 9 項，CCA 已對齊）

| # | 條款 | 修改點 | 規模 | 阻塞 |
|---|---|---|---|---|
| 1 | BuildConfig 11 條欄位加 [P0/P1/P2] 註解 band | `app/build.gradle` | 11 行註解 | 無 |
| 2 | release variant override `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` 為空 | `app/build.gradle release { }` | ~4 行 | 無 |
| 3 | `AIProviderManager` EncryptedSharedPreferences fallback 接 `ErrorReporter` | `AIProviderManager.kt:44-47` | ~6 行 | 無 |
| 4 | Whisper precedence 反轉（使用者 key 優先 / BuildConfig fallback） | `ImeServiceInput.kt:255-256` + `SpeechSettingsSection.kt:266-268` | 2 處 | **§J.4 #1 仲裁** |
| 5 | `PLATFORM_API_KEY` blank → release fail | `scripts/release_scan.sh` | 1 項 | 無 |
| 6 | `INVITE_HMAC_SECRET == dev default` → release fail | 同上 | 1 項 | 無 |
| 7 | Fallback UX 5 情境 consumer 分支 user-visible message | `GifPanelView` / `WeatherManager` / `InsightStickerApi` / `InviteCodeManager` | ~5 處 | 無（文案由 CCA 提供） |
| 8 | `scripts/release_scan.sh` 主骨架（5+2 項掃描） | 新檔 | ~80 行 shell | 無 |
| 9 | `AIProviderManager.importBuildConfigKeysIfNeeded()` 預設 provider 改 `SNOWREALM` | `AIProviderManager.kt:73-77` | ~10 行 | **§J.4 #2 仲裁** |

可即刻進 S12-3.1 的：#1, #2, #3, #5, #6, #7, #8（共 7 項）
需先仲裁的：#4, #9

### J.4 待玄樞 / 董事長仲裁的開放問題（最終列表）

| # | 開放問題 | 來源 | 影響 |
|---|---|---|---|
| 1 | Whisper 路徑 BuildConfig 反讀使用者 key — bug 還是 by design？ | CCA §E.2 / CCB §K.2.1 | 阻 S12-3.1 #4；兩方都偏 (a)=反轉 |
| 2 | 預設 provider 改 SNOWREALM 後，使用者首次安裝是否要 device-bound trial 邀請碼？ | CCA §E.1 | 阻 S12-3.1 #9；涉及 Insight 端產品決策 |
| 3 | Supabase RLS audit — 何時做？ | CCA §E.4 / CCB §K.4 | 影響 SUPABASE_ANON 終局分級（P1 → P2 或維持 P1）；不阻 S12-3.1，建議 S13+ |
| 4 | Insight 端 `NEXT_PUBLIC_GIPHY_API_KEY` / `NEXT_PUBLIC_UNSPLASH_ACCESS_KEY` 是否要改 server proxy？ | CCA §F.3 | 跨專案決策，不阻本 sprint |
| 5 | Insight 端 PLATFORM_API_KEY 共享密鑰本質的二層加固（Bearer + rate limit by origin）？ | CCA §E.3 | 跨專案決策，不阻本 sprint |

**仲裁建議（給玄樞參考，非結論）**：
- #1 直接判 (a) 反轉；理由：兩方都偏，且 release `.env` 剝除 P0 key 後行為等價，零風險。
- #2 二選一：(a) 走 device-bound trial 邀請碼（需 Insight 端開單）/ (b) 維持空 hasValidConfig 引導使用者主動拿邀請碼。CCA 偏 (a)，CCB 不表態。
- #3 排 S13+ 獨立 round；不阻 S12-3.1。
- #4 / #5 跨專案 follow-up，董事長拍板要不要開單給 Insight 端。

### J.5 S12-5 Observability 預留鉤子（從 §F.3 折衷遺留）

雖 P 級分歧已消解，CCA 原 §F.3 提的「GIPHY / OPENWEATHER 月用量觀測 + key rotation SLA」仍合理，作為 S12-5 Observability Hook 的具體 use case 預留：

- AIProviderManager fallback 上報（CCB §K.2.3 / S12-3.1 #3）
- GIPHY / OPENWEATHER quota 警戒上報（S12-5 工作）
- 兩支 P0 AI key 若被反編譯偵測到（CI 端 scan）也可上報

### J.6 對 Sprint12-All-Round-Gate.md 的更新摘要

- S12-3 標記 COMPLETED（policy round 結案；S12-3.1 實作待開）
- §6 玄樞預設判定補 reason 11-13（已寫入）
- 遺留三項仲裁（§J.4 #1-3）等董事長指示

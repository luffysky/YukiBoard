# Sprint 12 All Round Gate

日期: 2026-04-18  
身份: 玄樞  
狀態: `ACTIVE`

## 1. Sprint 主題

Sprint 12 主題定義為：

- 支付與 API key 安全整改
- 高風險 client secret 移出 APK
- 建立後端簽章與 release policy 邊界

## 2. Sprint 12 正式範圍

1. `NewebpayClient` server-side signing
2. payment / AI / gif / weather key 下放策略
3. remote keyboard PIN / brute-force 風險
4. 關鍵錯誤觀測接點

## 3. Round Gate

### ~~S12-0 Payment / Key Audit~~

### ~~當前狀態~~

- ~~`COMPLETED`~~

### ~~目標~~

1. ~~對 Sprint 12 主題（payment / API key / remote keyboard / observability）做第一輪 audit~~
2. ~~識別 P0 / P1 / P2 風險點，並標出 `consume / contract / release policy` 與 `runtime / threat / observability` 兩條軸~~
3. ~~為 S12-1 ~ S12-5 切出最小 file scope，不直接動 `.kt`~~

### ~~本輪限制~~

1. ~~只做 audit，不改 `app/src/main/**`~~
2. ~~不偷排 S12-1 ~ S12-5 順序~~
3. ~~只允許交付 `docs/SPRINT12_0_*` audit 文件~~

### ~~指定負責~~

- ~~`雪鑰`：consume / contract / release policy 軸~~
- ~~`鍵律`：runtime / threat / observability 軸~~
- ~~`玄樞`：合併 gate（CodeX 額度耗盡，由 `雪鑰` 代行）~~

### ~~任務指派~~

#### ~~雪鑰~~

- ~~任務 ID：`S12-0-CCA-PAYMENT-KEY-CONSUME-AUDIT`~~

~~目標：~~

- ~~從 consume / contract / release policy 角度盤出 payment、AI key、Supabase、Newebpay、Keystore 等高風險點~~

#### ~~鍵律~~

- ~~任務 ID：`S12-0-CCB-REMOTE-SECURITY-AUDIT`~~

~~目標：~~

- ~~從 runtime / threat / observability 角度盤出 WiFi PIN、brute-force、藍牙 HID、ZCoinManager fallback、Crashlytics 缺位等風險~~

### ~~提交鏈~~

1. ~~`鍵律` 完成 `S12-0-CCB-REMOTE-SECURITY-AUDIT`~~
2. ~~`雪鑰` 完成 `S12-0-CCA-PAYMENT-KEY-CONSUME-AUDIT`~~
3. ~~兩份收齊後提交給：`玄樞`（雪鑰代行）~~
4. ~~只有 `玄樞` 判定 `READY`，才可開 `S12-1`~~

### ~~玄樞 gate 結論（雪鑰 代行）~~

```text
~~READY~~

~~Reason:~~
~~1. 兩份 audit 同時交付、sub-scope 嚴守各自任務 ID、無越代、無內容衝突、相互補位到位。~~
~~2. 三分類覆蓋符合 Gate 檔 §3 S12-0 要求：CCA 從 consume / contract / release policy，CCB 從 runtime / threat / observability 三條軸互補。~~
~~3. 識別到的 P0 漏洞（Newebpay HashKey/IV、INVITE_HMAC_SECRET 預設值、Keystore 預設值、WiFi PIN + brute-force）彼此對齊，解法方向一致。~~
~~4. S12-1 file scope 已切出明確邊界（`currency/NewebpayClient.kt`、`currency/ZCoinManager.kt`、`currency/ZCoinApi.kt`、`ui/currency/ZCoinWalletActivity.kt`、`app/build.gradle:62-65`）。~~
~~5. 董事長拍板採 CH-1 順序（S12-1 → S12-2 → S12-4 → S12-3 → S12-5）。~~
~~6. Gate verdict 詳見 `docs/SPRINT12_0_GATE_VERDICT_2026-04-19.md`。~~
```

### ~~S12-1 Newebpay Server-side Signing Plan~~

### ~~當前狀態~~

- ~~`COMPLETED`（2026-04-19 修訂版，採 ZCoin scaffold 保留 + secret 抽離方向）~~

### ~~目標~~

1. ~~規劃 Newebpay 簽章移至後端（Insight）的 contract / runtime plan~~
2. ~~決定 `currency/` 三檔（NewebpayClient / ZCoinManager / ZCoinApi）的去留~~
3. ~~只做 plan / contract，不改 `.kt`~~

### ~~本輪限制~~

1. ~~只允許交付 `docs/SPRINT12_1_*` plan 文件，不改 `app/src/main/**`~~
2. ~~不偷開 S12-2 實作~~
3. ~~不擴成 Insight 後端重構~~

### ~~指定負責~~

- ~~`雪鑰`：contract / consume / release policy plan~~
- ~~`鍵律`：runtime / threat / build plan~~
- ~~`玄樞`：合併 gate（雪鑰 代行）~~

### ~~任務指派~~

#### ~~雪鑰~~

- ~~任務 ID：`S12-1-CCA-NEWEBPAY-CONTRACT-PLAN`~~

~~目標：~~

- ~~規劃 YukiBoard × Insight 的 Newebpay 簽章 contract、Custom Tab vs in-app WebView 路徑、release APK secret 剝除策略~~

#### ~~鍵律~~

- ~~任務 ID：`S12-1-CCB-NEWEBPAY-RUNTIME-PLAN`~~

~~目標：~~

- ~~規劃 build.gradle 真值剝除、`NewebpayClient` runtime 行為、錯誤處理三補強（CustomTab 不可用 / tenantId default / 未登入）~~

### ~~提交鏈~~

1. ~~`雪鑰` 完成 `S12-1-CCA-NEWEBPAY-CONTRACT-PLAN`~~
2. ~~`鍵律` 完成 `S12-1-CCB-NEWEBPAY-RUNTIME-PLAN`~~
3. ~~兩份收齊後提交給：`玄樞`（雪鑰代行）~~
4. ~~只有 `玄樞` 判定 `READY`，才可開 `S12-2`~~

### ~~玄樞 gate 結論（雪鑰 代行）~~

```text
~~READY（修訂後）~~

~~Reason:~~
~~1. 兩份 plan 在「Newebpay 實質 server-side signing 已由 Insight 後端完成」此主結論上一致且正確。~~
~~2. 經 cross-check Insight 專案確認：Insight 已具備 `/api/payment/zcoin-recharge`、`/api/payment/notify`、`/api/zcoin*`、`(public)/wallet/page.tsx`、`insight-admin/[tenantId]/zcoin` 完整實作。~~
~~3. 兩份 plan 對「`currency/` 三檔當前 0 caller」的 grep 結論技術上正確，但語意定位需修正：該三檔為 YukiBoard × Insight ZCoin 整合的預留 Android client scaffold，非 dead code。~~
~~4. 證據：`SettingsActivity.kt:159` 串接 `ZCoinWalletActivity`、`InsightSurveyClient.kt:27/56/73` 已消費 `reward_zcoin`、`ClawClient.kt` / `SkinManager.kt` 註解明示未來預留。~~
~~5. 依董事長 2026-04-19 指示「ZCoin 不可刪」，原初稿「刪除 `currency/` 三檔」方向撤回，改採「scaffold 保留 + secret 抽離」（`build.gradle:63-65` 真值改空注入 + 三檔加 DORMANT/DEPRECATED KDoc）。~~
~~6. 開放問題 D.1-1（Newebpay server 歸屬 Insight）/ D.1-2（release 立即剝除真值）兩方一致，已收口於 S12-2 scope。~~
~~7. CCB 三個錯誤處理補強（CustomTab 不可用 / tenantId default / 未登入）全部納入 S12-2。~~
~~8. Gate verdict 詳見 `docs/SPRINT12_1_GATE_VERDICT_2026-04-19.md`（修訂版）。~~
```

### S12-2 Android Client Payment Refactor

狀態: `DEFERRED`（2026-04-19 董事長指示「Z 幣後面做」；CH 順序跳至 S12-4。詳見 `docs/SPRINT12_ZCOIN_DEFER_NOTE_2026-04-19.md`）

目標：

1. 依 `S12-1` 核准 contract 收斂 Android client payment path
2. 移除 APK 內高風險 payment secret 依賴
3. 不擴成整個 billing / backend / legal 重構

指定負責：

- `雪鑰`
- `鍵律`

任務指派：

#### 鍵律

- 任務 ID：`S12-2-CCB-ANDROID-PAYMENT-REFACTOR-IMPLEMENTATION`

目標：

- 落地 Android client 端 payment path 的最小安全重構

#### 雪鑰

- 任務 ID：`S12-2-CCA-ANDROID-PAYMENT-CONSUME-REVIEW`

目標：

- 審 payment UX、設定面、錯誤顯示、fallback 路徑與使用者可見語意

提交鏈：

1. `鍵律` 完成 `S12-2-CCB-ANDROID-PAYMENT-REFACTOR-IMPLEMENTATION`
2. 提交給：`雪鑰`
3. `雪鑰` 完成 `S12-2-CCA-ANDROID-PAYMENT-CONSUME-REVIEW`
4. 提交給：`玄樞`
5. 只有 `玄樞` 判定 `READY`，才可決定是否開 `S12-3`、`S12-4`、`S12-5`

### ~~S12-4 Remote Keyboard PIN Hardening~~

### ~~當前狀態~~

- ~~`COMPLETED`（CH-1 順序：S12-2 DEFERRED 後跳此門）~~

### ~~目標~~

1. ~~強化 remote keyboard 的 PIN、retry、brute-force 基線~~
2. ~~檢查設定面、提示文案、使用者可見行為與 lockout 語意~~
3. ~~不越做到 TLS / Crashlytics / 帳號層；telemetry 保留 interface 給 S12-5 替換~~

### ~~本輪限制~~

1. ~~只動 `remote/WifiKeyboardServer.kt`、`remote/BluetoothHidKeyboard.kt`、`ui/panel/RemoteKeyboardPanelView.kt` 與相關 telemetry interface~~
2. ~~不擴成 WebSocket close frame 改造~~
3. ~~不偷開 S12-3 / S12-5~~

### ~~指定負責~~

- ~~`鍵律`：implementation~~
- ~~`雪鑰`：consume review~~
- ~~`玄樞`：合併 gate（雪鑰 代行）~~

### ~~任務指派~~

#### ~~鍵律~~

- ~~任務 ID：`S12-4-CCB-REMOTE-KEYBOARD-PIN-HARDENING`~~

~~目標：~~

- ~~強化 remote keyboard 的 PIN、retry、brute-force 基線~~

#### ~~雪鑰~~

- ~~任務 ID：`S12-4-CCA-REMOTE-KEYBOARD-PIN-CONSUME-REVIEW`~~

~~目標：~~

- ~~檢查設定面、提示文案、使用者可見行為與 lockout 語意~~

### ~~提交鏈~~

1. ~~`鍵律` 完成 `S12-4-CCB-REMOTE-KEYBOARD-PIN-HARDENING`~~
2. ~~提交給：`雪鑰`~~
3. ~~`雪鑰` 完成 `S12-4-CCA-REMOTE-KEYBOARD-PIN-CONSUME-REVIEW`~~
4. ~~`鍵律` 二次修（採納 CCA D.1+D.2 BLOCKING/STRONG 4 項）~~
5. ~~`雪鑰` final pass diff 核對~~
6. ~~提交給：`玄樞`（雪鑰代行）~~
7. ~~只有 `玄樞` 判定 `READY`，本門才可結案~~

### ~~玄樞 gate 結論（雪鑰 代行）~~

```text
~~READY~~

~~Reason:~~
~~1. 四條 runtime hardening 建議（session token / panel PIN / telemetry interface / BT BuildConfig）全部落地，編譯通過。~~
~~2. CCA review §D BLOCKING 兩項 + STRONG 兩項全部二次修完成，實際 diff 核對無誤。~~
~~3. CCA §D.3 OPTIONAL 兩項（HTML initial class / LISTENING 註解）CCB 選擇不處理，理由合理（`connecting` 橙色符合「等待驗證」直覺；`LISTENING` 註解僅開發者可見），不阻擋。~~
~~4. 本輪 scope 嚴守：無越做到 TLS / Crashlytics / 帳號層；telemetry 保留 `ErrorReporter` interface 給 S12-5 替換。~~
~~5. 實際生效：6-digit SecureRandom PIN、per-IP brute-force tracker（5/60s + 300s lockout）、128-bit per-client session token、面板 PIN 顯示（18sp monospace）、驗證等級標示（🔒 驗證: PIN + 連線金鑰 / 系統配對）、BT device-name release 潔化、telemetry `ErrorReporter` interface（Log impl）。~~
~~6. onclose 邏輯閉合：第一次輸錯 PIN / lockout / 連上後真斷線 / `onerror` 讓渡 `onclose` 四情境驗證通過。~~
~~7. Follow-up §C.1（server 重啟 PIN 變 browser 不知情）、§C.2（三模式截圖未驗）、§C.3（panel 主題低對比）存檔不阻擋；後續若實際觸發再處理。~~
~~8. Gate verdict 詳見 `docs/SPRINT12_4_GATE_VERDICT_2026-04-19.md`。~~
```

### ~~S12-3 API Key Release Policy~~

### ~~當前狀態~~

- ~~`COMPLETED`（policy round 結案；S12-3.1 實作 round 待開、需先解 P 級分歧仲裁）~~

### ~~目標~~

1. ~~對 7 把 BuildConfig API key（INVITE_HMAC_SECRET / PLATFORM_API_KEY / GIPHY / OPENAI / ANTHROPIC / OPENWEATHER / SUPABASE_ANON）做 release policy 定義~~
2. ~~建立 P0/P1/P2 風險分級框架，明確 release variant 行為（BuildConfig 真值 vs 強制空值 vs 可保留）~~
3. ~~規劃 user-overrideable matrix、consent UX、release contract、scan 機制~~
4. ~~不直接動 `.kt`，policy round 只交付文件~~

### ~~本輪限制~~

1. ~~只允許交付 `docs/SPRINT12_3_*` policy 文件~~
2. ~~不改 `app/src/main/**`、不動 `app/build.gradle`~~
3. ~~不偷開 S12-5 / S12-3.1 實作~~
4. ~~Insight cross-check 為附錄，不擴成 Insight 重構任務~~

### ~~指定負責~~

- ~~`雪鑰`：release / consume / consent policy + Insight cross-check~~
- ~~`鍵律`：runtime / build / logging / scan policy~~
- ~~`玄樞`：合併 gate（雪鑰 代行）~~

### ~~任務指派~~

#### ~~雪鑰~~

- ~~任務 ID：`S12-3-CCA-API-KEY-RELEASE-POLICY`~~

~~目標：~~

- ~~7-key inventory + P0/P1/P2 grading + user-overrideable matrix + consent UX + release contract + Insight cross-check appendix~~

#### ~~鍵律~~

- ~~任務 ID：`S12-3-CCB-API-KEY-RUNTIME-POLICY`~~

~~目標：~~

- ~~runtime injection / build variant override / HttpLoggingInterceptor / AIProviderManager fallback / Supabase consumer 收斂 / `release_scan.sh` 設計~~

### ~~提交鏈~~

1. ~~`雪鑰` 完成 `S12-3-CCA-API-KEY-RELEASE-POLICY`~~
2. ~~`鍵律` 完成 `S12-3-CCB-API-KEY-RUNTIME-POLICY`~~
3. ~~`鍵律` 二次修（§K AMENDED_POST_CCA：採 CCA P1 分級 / 新增 PLATFORM blank fail build / fallback UX 5 情境承接 / INVITE_HMAC scope 邊界）~~
4. ~~兩份收齊後提交給：`玄樞`（雪鑰代行）~~
5. ~~只有 `玄樞` 判定 `READY`，才可進下一安全實作門（S12-3.1 實作 round 須先解 §J.4 仲裁）~~

### ~~玄樞 gate 結論（雪鑰 代行）~~

```text
~~READY~~

~~Reason:~~
~~1. 兩份 scope 互補、共同識別 OPENAI/ANTHROPIC release 不可帶真值（CCB 路徑 A 強制空值）。~~
~~2. CCA 補 release / consume / consent + Insight cross-check appendix（發現 `NEXT_PUBLIC_GIPHY_API_KEY` / `NEXT_PUBLIC_UNSPLASH_ACCESS_KEY` 在 Insight client bundle 暴露）。~~
~~3. CCB 補 runtime / build / logging / scan policy（發現 HttpLoggingInterceptor 已引入但未啟用、`AIProviderManager` EncryptedSharedPreferences fallback 明文風險、三個 Supabase consumer 未收斂 gateway）。~~
~~4. 原初稿存在 GIPHY/OPENWEATHER（CCA P1 vs CCB P0）、SUPABASE_ANON（CCA P2 vs CCB P1）兩處 P 級分歧，CCB 於 §K（AMENDED_POST_CCA）主動採 CCA P1 分級，並新增 PLATFORM blank fail build / fallback UX 5 情境承接 / INVITE_HMAC scope 邊界三條條款。~~
~~5. SUPABASE_ANON 兩方接受採 CCB P1 為 interim，待 Supabase RLS audit 拍板。~~
~~6. CCB H.1.1 Supabase Bearer 認知修正：實際 `SupabaseClient.kt:96-104` 用 `apikey:` header 帶 anonKey + `Authorization: Bearer` 帶 user JWT；非 anonKey 走 Bearer。~~
~~7. S12-3.1 實作 round 合併 9 項清單已落定：BuildConfig 註解 band / release variant 強制空值 OPENAI/ANTHROPIC / AIProviderManager fallback 接 ErrorReporter / Whisper precedence 反轉（待仲裁）/ PLATFORM blank release fail / INVITE_HMAC dev-default release fail / fallback UX 5 情境 consumer 分支 / `scripts/release_scan.sh` / 預設 provider 改 SNOWREALM（待仲裁）。可即刻進 7 項，2 項待玄樞仲裁（§J.4）。~~
~~8. Gate verdict 詳見 `docs/SPRINT12_3_GATE_VERDICT_2026-04-19.md`。~~
```

### ~~S12-5 Observability Hook~~

### ~~當前狀態~~

- ~~`COMPLETED`（CH-1 最末門；2026-04-19 董事長拍板解鎖 → 同日完成 CCB 實作 + CCA review + 雪鑰代行玄樞 gate）~~

### ~~目標~~

1. ~~落地重要錯誤上報點（5 處 P0/P1 consumer wire）~~
2. ~~建立 telemetry facade（Application.onCreate swap 點，S13+ 換 Crashlytics 只動 1 行）~~
3. ~~補觀測接點的產品面 / 隱私面 / error wording / consent 邊界 contract~~
4. ~~不引入 Crashlytics / build-infra / consent UX 實作（董事長拍板 Path B）~~

### ~~本輪限制~~

1. ~~只動 telemetry facade + 5 處 consumer wire（AI prefs / ZCoin prefs / Invite prefs + dev-secret guard / Newebpay sign-fail / BT adapter & HID register fail）~~
2. ~~不擴 Firebase / google-services / gradle plugin~~
3. ~~不擴 SettingsActivity opt-in UX 實作（contract 由 CCA 敲，留 S13+）~~
4. ~~不偷碰其他 round 範圍~~

### ~~指定負責~~

- ~~`鍵律`：implementation~~
- ~~`雪鑰`：consume / privacy / wording / consent contract~~
- ~~`玄樞`：合併 gate（雪鑰 代行）~~

### ~~任務指派~~

#### ~~鍵律~~

- ~~任務 ID：`S12-5-CCB-OBSERVABILITY-HOOK-IMPLEMENTATION`~~

~~目標：~~

- ~~落地重要錯誤上報點與最小 observability hook~~

#### ~~雪鑰~~

- ~~任務 ID：`S12-5-CCA-OBSERVABILITY-CONTRACT-NOTE`~~

~~目標：~~

- ~~補觀測接點的產品面、隱私面、error wording 與 consent 邊界~~

### ~~提交鏈~~

1. ~~`鍵律` 完成 `S12-5-CCB-OBSERVABILITY-HOOK-IMPLEMENTATION`~~
2. ~~提交給：`雪鑰`~~
3. ~~`雪鑰` 完成 `S12-5-CCA-OBSERVABILITY-CONTRACT-NOTE`~~
4. ~~提交給：`玄樞`（雪鑰代行）~~
5. ~~只有 `玄樞` 判定 `READY`，本門才可結案~~

### ~~玄樞 gate 結論（雪鑰 代行）~~

```text
~~READY~~

~~Reason:~~
~~1. CCB 落地 facade（`telemetry/Telemetry.kt` 9 行）+ 5 處 consumer wire，編譯通過（compileDebugKotlin BUILD SUCCESSFUL in 5s）。~~
~~2. CCA review 確認 7 個 TelemetryEvent 全部 PII-safe（exception class simple name / 結構化 boolean / 無 device name / IP / userId / API key / stack message / 輸入文字 leakage），符合 S12-3 §E.2 runtime logging 紅線。~~
~~3. CCB §D.1 四項開放問題 CCA 全部裁決完成（不轉 user-visible alert / extras forward rule / S13+ opt-in contract / wording 慣例），未推回董事長。~~
~~4. 無 BLOCKING / STRONG findings；2 項 MINOR_OPTIONAL（forward extras rule KDoc / dev-secret guard 移到 Application.onCreate）皆延後 S13+ Crashlytics swap round 一併處理。~~
~~5. 本輪 scope 嚴守 Path B：未引入 Crashlytics / google-services / firebase plugin / consent UX 實作。~~
~~6. Sprint 12 §5 主目標 4 條中 3 條完全覆蓋（§5.2 release policy / §5.3 PIN hardening / §5.4 觀測接點），1 條部分覆蓋（§5.1 secret 抽離 — S12-2 DEFERRED 為已知遺留，Newebpay sign-fail telemetry 提供運維側可見性）。~~
~~7. S13+ Crashlytics swap 只需 1 行：`Telemetry.reporter = ConsentAwareErrorReporter(CrashlyticsReporter(this), settings::isTelemetryConsented)`。~~
~~8. Gate verdict 詳見 `docs/SPRINT12_5_GATE_VERDICT_2026-04-19.md`。~~
```

## 4. 解鎖規則

1. `S12-0` 完成後才開 `S12-1`。
2. `S12-1` 通過後，才開 `S12-2`。
3. `S12-3`、`S12-4`、`S12-5` 只能在 `S12-2` 路徑穩定後開啟。

## 5. Sprint 12 完成條件

1. 不再把支付核心 secret 留在 APK。
2. 高風險 key 有明確 release policy。
3. remote keyboard 最低安全基線提升。
4. 觀測接點有明確落地。

## 6. 玄樞預設判定

`S12-0 COMPLETED / S12-1 COMPLETED / S12-2 DEFERRED / S12-4 COMPLETED / S12-3 COMPLETED / S12-5 COMPLETED`

Reason（2026-04-19 雪鑰代行玄樞 gate + 董事長拍板 + S12-1 修訂版）:
1. Sprint 10-11 基線已建立，`S12-0` 已完成 audit / contract cut。
2. `雪鑰`（consume / contract / release policy 線）與 `鍵律`（runtime / threat / observability 線）S12-0 audit 互補、scope 嚴守，共同識別 P0：Newebpay HashKey/IV、INVITE_HMAC_SECRET 預設值、Keystore 預設值、WiFi 4-digit PIN + brute-force。
3. 董事長 2026-04-19 拍板採 **CH-1 順序**：S12-1 → S12-2 → S12-4 → S12-3 → S12-5（先封 payment 洞）。
4. S12-1 兩份 plan（CCA contract / CCB runtime）交付齊全；合併 gate verdict 原初稿主張「`currency/` 三檔整刪」，經董事長指示「ZCoin 不可刪」+ CCA cross-check Insight 專案後，**修訂為 scaffold 保留 + secret 抽離**方向，見 `docs/SPRINT12_1_GATE_VERDICT_2026-04-19.md`（修訂版）。
5. 關鍵 cross-check 結果：Insight 側已完成完整 ZCoin/Newebpay server-side 實作（`/api/payment/zcoin-recharge`、`/api/payment/notify`、`/api/zcoin*`、`(public)/wallet/page.tsx`、`insight-admin/[tenantId]/zcoin`），YukiBoard `currency/` 三檔（NewebpayClient / ZCoinManager / ZCoinApi）為對應 Android client scaffold，當前 0 caller 但為預留接入位；`InsightSurveyClient.kt` 已消費 `reward_zcoin` 欄位屬使用中整合證據。
6. S12-1 開放問題 D.1-1（Newebpay server 歸屬 Insight）/ D.1-2（release 立即剝除真值）兩方一致，已收口於 S12-2 scope。
7. S12-2 scope 原為：`build.gradle:63-65` HashKey/IV 真值改空注入、三檔加 DORMANT/DEPRECATED KDoc、`ZCoinWalletActivity` 補三個錯誤處理。
8. **2026-04-19 董事長追加指示「Z 幣後面做」** → S12-2 全部 DEFERRED，CH 順序跳至 S12-4（Remote Keyboard PIN Hardening）。Z 幣現狀凍結與恢復條件見 `docs/SPRINT12_ZCOIN_DEFER_NOTE_2026-04-19.md`。
9. P0 Newebpay HashKey/IV 在 release APK 的風險**未閉環**，列為 deferred note §4 開放項，待 Sprint 13+ 與 Z 幣 wire-up 一併處理；若董事長要求即時緩解，可先做 build.gradle 真值改空注入（不依 S12-2，1 行）。
10. S12-4 Remote Keyboard PIN Hardening 於 2026-04-19 經 CCB 實作 + CCA review + CCB 二次修 + CCA final pass 後結案 `READY`；實際生效：6-digit SecureRandom PIN、per-IP brute-force tracker（5/60s + 300s lockout）、128-bit per-client session token、面板 PIN 顯示、驗證等級標示、BT device-name release 潔化、telemetry `ErrorReporter` interface（Log impl，為 S12-5 預留替換點）。
11. S12-4 後 S12-3 API Key Release Policy 啟動，雪鑰 CCA + 鍵律 CCB 兩份 policy 並行交付；CCA 補 release / consume / consent + Insight cross-check appendix（發現 `NEXT_PUBLIC_GIPHY_API_KEY` / `NEXT_PUBLIC_UNSPLASH_ACCESS_KEY` 在 Insight client bundle 暴露），CCB 補 runtime / build / logging / scan policy（發現 HttpLoggingInterceptor 已引入但未啟用、`AIProviderManager` EncryptedSharedPreferences fallback 明文風險、三個 Supabase consumer 未收斂 gateway）。
12. S12-3 合併 gate verdict 結案 `READY`：兩份 scope 互補、共同識別 OPENAI/ANTHROPIC release 不可帶真值（CCB 路徑 A 強制空值）。原初稿存在 GIPHY/OPENWEATHER（CCA P1 vs CCB P0）、SUPABASE_ANON（CCA P2 vs CCB P1）兩處 P 級分歧，**CCB 於 §K（AMENDED_POST_CCA）主動採 CCA P1 分級**，並新增 PLATFORM blank fail build / fallback UX 5 情境承接 / INVITE_HMAC scope 邊界三條條款。SUPABASE_ANON 兩方接受採 CCB P1 為 interim，待 Supabase RLS audit 拍板（gate verdict §J.1）。
13. S12-3.1 實作 round 待開（合併 9 項：BuildConfig 註解 band、release variant 強制空值 OPENAI/ANTHROPIC、AIProviderManager fallback 接 ErrorReporter、Whisper precedence 反轉、PLATFORM blank release fail、INVITE_HMAC dev-default release fail、fallback UX 5 情境 consumer 分支、`scripts/release_scan.sh`、預設 provider 改 SNOWREALM）。可即刻進 7 項，2 項（Whisper 反轉、SNOWREALM 預設）待玄樞仲裁（gate verdict §J.4）。
14. 下一候選 round 由董事長拍板：S12-3.1 實作 round（須先解 §J.4 #1-2 仲裁）/ S12-5 Observability Hook（telemetry interface 已由 S12-4 預留）/ Sprint 12 收尾進 Sprint 13。
15. **2026-04-19 董事長指示「12-5 可以開了」** → S12-5 Observability Hook 解鎖，狀態改 `ACTIVE`。提交鏈：`鍵律` 先做 `S12-5-CCB-OBSERVABILITY-HOOK-IMPLEMENTATION`（落地重要錯誤上報點 + 替換 S12-4 預留的 `ErrorReporter` interface）→ `雪鑰` 做 `S12-5-CCA-OBSERVABILITY-CONTRACT-NOTE`（補產品面 / 隱私面 / error wording / consent 邊界）→ 提交給 `玄樞`（雪鑰代行）。
16. S12-5 同日結案 `READY`：CCB 落地 facade `telemetry/Telemetry.kt`（9 行，Application.onCreate swap 點）+ 5 處 consumer wire（AIProviderManager / ZCoinManager / InviteCodeManager 含 dev-secret guard / NewebpayClient / BluetoothHidKeyboard）+ 7 個 TelemetryEvent 全部 PII-safe；CCA 4 項開放問題全部裁決（不轉 user-visible alert / extras forward rule / S13+ opt-in contract `ConsentAwareErrorReporter` wrapper 設計 / severity 三級慣例）；無 BLOCKING / STRONG，2 項 MINOR_OPTIONAL（KDoc / Application.onCreate dev-secret guard 移位）延後 S13+ Crashlytics swap round 一併處理。本輪 scope 嚴守 Path B 未引入 Crashlytics / build-infra / consent UX 實作。詳見 `docs/SPRINT12_5_GATE_VERDICT_2026-04-19.md`。
17. **Sprint 12 5/6 round COMPLETED + 1 DEFERRED**（CH-1 順序全跑完）。§5 主目標：§5.2 / §5.3 / §5.4 完全覆蓋，§5.1 部分覆蓋（S12-2 DEFERRED 為已知遺留，Newebpay sign-fail telemetry 提供運維可見性）。下一候選 round 由董事長拍板：S12-3.1 實作 round（須先解 §J.4 #1-2 仲裁）/ Sprint 12 收尾文件 + 進 Sprint 13。

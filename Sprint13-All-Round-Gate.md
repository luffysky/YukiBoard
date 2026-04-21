# Sprint 13 All Round Gate

日期: 2026-04-18  
身份: 玄樞  
狀態: `ACTIVE`

## 0. 額度恢復後角色編成

自 2026-04-20 起，`Codex` 額度已恢復；`Sprint 13` 不再沿用前一段的代班配置，正式恢復四角編成：

- `玄樞`：Sprint owner / gate owner / 高風險仲裁 / 最終放行
- `衡鑑`：technical review owner / `.kt` 與 runtime cross-check / 測試與風險複核
- `雪鑰`：contract / consume / settings / legal wording
- `鍵律`：implementation / runtime / service / panel / engine

## 1. Sprint 主題

Sprint 13 主題定義為：

- 合規高優先項
- 隱私與 consent 收斂
- sync / export / delete 類資料責任邊界

## 2. Sprint 13 正式範圍

1. `usesCleartextTraffic` / network security config
2. AI consent / opt-out UI
3. fraud report privacy
4. `fraud_reports` RLS insert only
5. data export / delete contract
6. clipboard sync / user dictionary sync contract
7. IME lifecycle / service warning cleanup micro-round

## 3. Round Gate

### ~~S13-0 Legal Scope Cut~~

~~狀態: `COMPLETED`（2026-04-21）~~

~~目標：~~

~~1. 盤清 `Sprint 13` 涉及的 legal / privacy / consent / sync contract 邊界~~
~~2. 切出 network security、fraud report privacy、AI consent、data export/delete、clipboard/userdict sync 的最小 contract~~
~~3. 先做 wording / contract / scope，不進 implementation~~

~~本輪限制：~~

~~1. 不改 `app/src/main/**`~~
~~2. 不偷開 `S13-1` 到 `S13-5`~~
~~3. 只做 contract / wording / data responsibility cut~~

~~指定負責：~~

~~- `雪鑰`~~
~~- `鍵律`~~
~~- `衡鑑` technical cross-check~~
~~- `玄樞` final gate~~

~~任務指派：~~

#### ~~雪鑰~~

~~- 任務 ID：`S13-0-CCA-LEGAL-PRIVACY-CONTRACT-AUDIT`~~

~~目標：~~

~~- 盤 AI consent / opt-out、data export / delete、clipboard / userdict sync 的產品契約與設定面邊界~~
~~- 補 privacy wording、consume 影響面、不測區域與禁止承諾~~

#### ~~鍵律~~

~~- 任務 ID：`S13-0-CCB-NETWORK-FRAUD-SYNC-THREAT-AUDIT`~~

~~目標：~~

~~- 盤 network security、fraud report privacy、sync contract 的 runtime / service / data flow 風險~~
~~- 補最小 file scope、禁區與後續 round 切法~~

~~提交鏈：~~

~~1. `雪鑰` 完成 `S13-0-CCA-LEGAL-PRIVACY-CONTRACT-AUDIT`~~
~~2. `鍵律` 完成 `S13-0-CCB-NETWORK-FRAUD-SYNC-THREAT-AUDIT`~~
~~3. 兩份收齊後提交給：`衡鑑`~~
~~4. `衡鑑` 完成 `S13-0-CXA-TECHNICAL-CROSSCHECK`~~
~~5. 提交給：`玄樞`~~
~~6. 只有 `玄樞` 判定 `READY`，才可開 `S13-1` 到 `S13-5`~~

~~Gate Verdict：~~
~~READY / ACCEPTED / COMPLETED~~
~~Reason:~~
~~1. `CCA` / `CCB` / `CXA` 三份 audit 已收齊，且 `玄樞` 已親自把引用到的關鍵實檔逐一終審。~~
~~2. 終審時抓到的關鍵缺口已補上：`privacy_policy.html` 已改寫 API Key 雲端同步條文，與 `AISettingsActivity` / `ApiKeySync` 的實際行為對齊。~~
~~3. 設定頁登入後仍回到藍色登入按鈕的狀態判斷缺口，也已在 `AuthSettingsSection` / `SettingsActivity` 補正，避免本地已登入時 UI 回退成未登入態。~~
~~4. `S13-0` 作為 contract / wording / scope cut 的前置門，現已具備開啟後續 round 的完整性；依一道一道開門規則，下一門只開 `S13-1`，其餘 round 維持 `LOCKED`。~~

### S13-6 IME Service Warning Cleanup

狀態: `LOCKED`

目標：

- 收斂已確認屬 `IME lifecycle / service extension` 範圍的非 blocking warning / 語意噪音
- 只接受單檔、行為等價、已被前 round observation 指到的 cleanup
- 不讓這類小修補污染 `Sprint 14` 字典 infra 或 `ZCoin / Newebpay` 主線

本輪限制：

1. 只接受 `IME service` / `lifecycle` / `service extension` 類 observation
2. 不得混入合規、payment、dictionary、sync contract 主線
3. 每次只開單一最小 file scope，不做 bundle cleanup

指定負責：

- `鍵律`
- `雪鑰`
- `衡鑑` technical review
- `玄樞` final gate

任務指派：

#### 鍵律

- 任務 ID：`S13-6-CCB-IME-SERVICE-WARNING-CLEANUP`

目標：

- 依 `玄樞` 指定 observation 做最小語意清理，不改 runtime 行為

#### 雪鑰

- 任務 ID：`S13-6-CCA-IME-SERVICE-WARNING-CONSUME-REVIEW`

目標：

- 核對 file scope、行為等價、邊界限制與非 blocking 性質

提交鏈：

1. `鍵律` 完成 `S13-6-CCB-IME-SERVICE-WARNING-CLEANUP`
2. 提交給：`雪鑰`
3. `雪鑰` 完成 `S13-6-CCA-IME-SERVICE-WARNING-CONSUME-REVIEW`
4. 提交給：`衡鑑`
5. `衡鑑` 完成 `S13-6-CXA-IME-SERVICE-WARNING-TECHNICAL-REVIEW`
6. 提交給：`玄樞`
7. 只有 `玄樞` 判定 `READY`，本門才可結案

### ~~S13-1 Network Security Baseline~~

~~狀態: `COMPLETED`（2026-04-21）~~

~~目標：~~

~~- 收斂 `usesCleartextTraffic`、network security config、明文傳輸基線~~

~~本輪限制：~~

~~1. 只做 network security baseline，不偷包 fraud / consent / sync contract~~
~~2. 任何 `.kt` / manifest / xml 改動都屬正式實作，需等 gate 開門~~

~~指定負責：~~

~~- `雪鑰`~~
~~- `鍵律`~~
~~- `衡鑑` technical review~~
~~- `玄樞` final gate~~

~~任務指派：~~

#### ~~雪鑰~~

~~- 任務 ID：`S13-1-CCA-NETWORK-SECURITY-CONSUME-IMPLEMENTATION`~~

~~目標：~~

~~- 補設定、文案、使用者可見風險與 release contract 對齊~~

#### ~~鍵律~~

~~- 任務 ID：`S13-1-CCB-NETWORK-SECURITY-RUNTIME-IMPLEMENTATION`~~

~~目標：~~

~~- 落地 network security baseline 的 runtime / manifest / config 調整~~

~~提交鏈：~~

~~1. `鍵律` 完成 `S13-1-CCB-NETWORK-SECURITY-RUNTIME-IMPLEMENTATION`~~
~~2. 提交給：`雪鑰`~~
~~3. `雪鑰` 完成 `S13-1-CCA-NETWORK-SECURITY-CONSUME-IMPLEMENTATION`~~
~~4. 提交給：`衡鑑`~~
~~5. `衡鑑` 完成 `S13-1-CXA-NETWORK-SECURITY-TECHNICAL-REVIEW`~~
~~6. 提交給：`玄樞`~~
~~7. 只有 `玄樞` 判定 `READY`，本門才可結案~~

~~Gate Verdict：~~
~~READY / ACCEPTED / COMPLETED~~
~~Reason:~~
~~1. `CCB` 的唯一實作檔 [network_security_config.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/res/xml/network_security_config.xml) 已由 `玄樞` 親自終審，確認僅為 XML 註解與結構文件化補強，無 runtime 語意變更。~~
~~2. [AndroidManifest.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/AndroidManifest.xml) 仍維持 `usesCleartextTraffic="false"` 與 `networkSecurityConfig` 綁定，release outbound cleartext baseline 未被放鬆。~~
~~3. `CCA` / `CXA` 指出的缺口屬使用者可見揭露與後續路由問題，不阻擋本 round 的 network security baseline 結案。~~
~~4. Non-blocking 殘留已確認：~~
~~- `privacy_policy.html` 對 LAN WiFi 鍵盤 cleartext 揭露仍不足~~
~~- `RemoteKeyboardPanelView` 無「未加密」提示~~
~~- 上述兩點不得默認丟入 `S13-5`，需由 `玄樞` 後續明確指定承接 round~~
~~5. 依一道一道開門規則，`S13-1` 結案後，下一門只開 `S13-2`，其餘 round 維持 `LOCKED`。~~

### S13-2 Fraud Report Privacy

狀態: `ACTIVE`

目標：

- 收斂 fraud report 的 privacy wording、資料最小化、RLS / insert-only 契約

本輪限制：

1. 不擴成全 fraud system 重構
2. 先處理 privacy 與 contract，資料分析能力留到後續

指定負責：

- `鍵律`
- `雪鑰` 法務文字/設定說明
- `衡鑑` technical review
- `玄樞` final gate

任務指派：

#### 鍵律

- 任務 ID：`S13-2-CCB-FRAUD-REPORT-PRIVACY-IMPLEMENTATION`

目標：

- 處理 fraud report 路徑的資料最小化與 runtime 邊界

#### 雪鑰

- 任務 ID：`S13-2-CCA-FRAUD-REPORT-WORDING-NOTE`

目標：

- 補法務文字、設定說明、同意前提與不承諾區域

提交鏈：

1. `鍵律` 完成 `S13-2-CCB-FRAUD-REPORT-PRIVACY-IMPLEMENTATION`
2. 提交給：`雪鑰`
3. `雪鑰` 完成 `S13-2-CCA-FRAUD-REPORT-WORDING-NOTE`
4. 提交給：`衡鑑`
5. `衡鑑` 完成 `S13-2-CXA-FRAUD-REPORT-PRIVACY-TECHNICAL-REVIEW`
6. 提交給：`玄樞`
7. 只有 `玄樞` 判定 `READY`，本門才可結案

### S13-3 AI Consent / Opt-out

狀態: `LOCKED`

目標：

- 收斂 AI consent、opt-out、設定面提示與實際行為一致性

本輪限制：

1. 不擴成 AI 功能重寫
2. 先定 consent contract，再談後續 capability 分流

指定負責：

- `雪鑰`
- `衡鑑` technical review
- `玄樞` final gate

任務指派：

#### 雪鑰

- 任務 ID：`S13-3-CCA-AI-CONSENT-OPTOUT-IMPLEMENTATION`

目標：

- 落地 AI consent / opt-out 的設定面與 contract

提交鏈：

1. `雪鑰` 完成 `S13-3-CCA-AI-CONSENT-OPTOUT-IMPLEMENTATION`
2. 提交給：`衡鑑`
3. `衡鑑` 完成 `S13-3-CXA-AI-CONSENT-OPTOUT-TECHNICAL-REVIEW`
4. 提交給：`玄樞`
5. 只有 `玄樞` 判定 `READY`，本門才可結案

### S13-4 Data Export / Delete Contract

狀態: `LOCKED`

目標：

- 定義 data export / delete 的最小 contract、責任界線、可做 / 不可做範圍

本輪限制：

1. 先做 contract，不偷做真刪除流程
2. 不承諾超出現有資料邊界的 export / delete 能力

指定負責：

- `雪鑰`
- `鍵律`
- `衡鑑` technical cross-check
- `玄樞` final gate

任務指派：

#### 雪鑰

- 任務 ID：`S13-4-CCA-DATA-EXPORT-DELETE-CONTRACT`

目標：

- 補使用者可見契約、設定說明、資料責任界線

#### 鍵律

- 任務 ID：`S13-4-CCB-DATA-EXPORT-DELETE-FLOW-NOTE`

目標：

- 補 runtime / storage / service flow 的最小技術契約

提交鏈：

1. `雪鑰` 完成 `S13-4-CCA-DATA-EXPORT-DELETE-CONTRACT`
2. `鍵律` 完成 `S13-4-CCB-DATA-EXPORT-DELETE-FLOW-NOTE`
3. 兩份收齊後提交給：`衡鑑`
4. `衡鑑` 完成 `S13-4-CXA-DATA-EXPORT-DELETE-TECHNICAL-CROSSCHECK`
5. 提交給：`玄樞`
6. 只有 `玄樞` 判定 `READY`，本門才可結案

### S13-5 Clipboard / UserDict Sync Contract

狀態: `LOCKED`

目標：

- 定義 clipboard / user dictionary sync 的真正契約，不再停留在 prefs 開關

本輪限制：

1. 先做 sync contract，不偷做雲同步本體
2. 不把 local-only 行為包裝成已支援 sync

指定負責：

- `鍵律`
- `雪鑰`
- `衡鑑` technical review
- `玄樞` final gate

任務指派：

#### 鍵律

- 任務 ID：`S13-5-CCB-CLIPBOARD-USERDICT-SYNC-FLOW-CONTRACT`

目標：

- 補 clipboard / userdict sync 的 runtime / storage / sync flow 契約

#### 雪鑰

- 任務 ID：`S13-5-CCA-CLIPBOARD-USERDICT-SYNC-CONSUME-CONTRACT`

目標：

- 補設定面、同意、資料責任與使用者可見說明

提交鏈：

1. `鍵律` 完成 `S13-5-CCB-CLIPBOARD-USERDICT-SYNC-FLOW-CONTRACT`
2. 提交給：`雪鑰`
3. `雪鑰` 完成 `S13-5-CCA-CLIPBOARD-USERDICT-SYNC-CONSUME-CONTRACT`
4. 提交給：`衡鑑`
5. `衡鑑` 完成 `S13-5-CXA-CLIPBOARD-USERDICT-SYNC-TECHNICAL-REVIEW`
6. 提交給：`玄樞`
7. 只有 `玄樞` 判定 `READY`，本門才可結案

## 4. 解鎖規則

1. `Sprint 13` 採一道一道開門；當前只開 `S13-2`，其餘 round 全部維持 `LOCKED`。
2. 只有前一 round 經 `玄樞` 正式判定 `READY` / 結案後，才可開下一道。
3. 涉及法務文字與隱私承諾的 round，必須先有 contract / wording，再開實作。
4. sync 類 round 不得在前面 security / privacy 邊界未定時偷跑。
5. `S13-6` 屬 tech-debt micro-round，僅能在主線 round 節奏允許時由 `玄樞` 另行開門，不得自行插隊或擴成 bundle cleanup。

## 5. Sprint 13 完成條件

1. 高優先合規項有正式 contract 或實作。
2. fraud report 與 AI consent 不再維持模糊狀態。
3. sync 類功能不再是只有 prefs 開關沒有真正契約。
4. `IME lifecycle / service` 類既知小型 warning 不再漂浮於 sprint 外。

## 6. 玄樞預設判定

`ACTIVE`

Reason:
1. `S13-0` 與 `S13-1` 已完成並結案；依一道一道開門規則，當前主線 round 為 `S13-2 Fraud Report Privacy`。
2. `S13-3` 到 `S13-6` 仍維持 `LOCKED`；`S13-6` 雖已併入 Sprint 13，但不得先於主線 round 插隊。

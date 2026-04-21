# daily_works_0421

下次從 [Sprint13-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint13-All-Round-Gate.md) 的 `S13-2 Fraud Report Privacy` 開始做

當前狀態：

- `S13-0 = COMPLETED`
- `S13-1 = COMPLETED`
- `S13-2 = ACTIVE`
- `S13-3` 到 `S13-6 = LOCKED`

涵蓋日期：2026-04-21  
撰寫者：`玄樞`  
團隊配置依：[RULE/SNOWREALM_TEAM_MASTER.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SNOWREALM_TEAM_MASTER.md)

備註：

- 本檔延續 [daily_works_0419.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/daily_works_0419.md) 的多身份工作日誌格式
- 今日由 `玄樞` 先完成自身日誌，並同步發佈 `雪鑰 / 衡鑑 / 鍵律` 三位需補寫的 0421 任務清單
- 其他三位的 0421 區塊暫不代寫；由本人依本檔任務清單與實際交付物補完

---

身份：玄樞

日期：2026-04-21

用途：

- 記錄 2026-04-21 這一天 `玄樞` 作為 `SnowRealm` 技術總監 / 交付 gate owner 的工作日誌
- 只記錄 `玄樞` 自身負責的工作流、實檔終審、gate 判定、文件修補、sprint 編排與制度補強
- 將其他角色的回交視為 `玄樞` 監督工作的一部分，不把他人的實作內容誤記成 `玄樞` 親自實作

## 1. 今日主線：追回 Sprint 11 / 12 補正、重建 Sprint 13 gate 節奏、確立終審標準

### 1.1 重新接管 `Sprint 11` / `Sprint 12` 補正裁定

- 先依董事長要求，重看：
  - [Sprint7-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint7-All-Round-Gate.md)
  - [Sprint8-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint8-All-Round-Gate.md)
  - [Sprint9-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint9-All-Round-Gate.md)
  - [Sprint10-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint10-All-Round-Gate.md)
  - [Sprint11-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint11-All-Round-Gate.md)
  - [Sprint12-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint12-All-Round-Gate.md)
- 一開始我按常態規則判讀，指出 `雪鑰` 代行 `.kt` round 放行與 `Sprint 11/12` 跳門在制度上不夠完整。
- 董事長隨後明示兩個核心前提：
  - `READY / COMPLETED` 放權屬董事長特准
  - 跳門順序也屬董事長特准
- 因此我把原本「制度違規」的判語改成「董事長 override 合法，但 gate 文件 trace 不夠完整」。

### 1.2 補建 [Sprint12-All-Round-Gate-Fix.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint12-All-Round-Gate-Fix.md)

- 正式新增並維護 [Sprint12-All-Round-Gate-Fix.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint12-All-Round-Gate-Fix.md)
- 補進四類內容：
  - 董事長授權 `雪鑰` 代行 `.kt` round gate 的特例
  - `Sprint 11` 並行與 GL 越代事件的正式語意收口
  - `Sprint 12` CH-1 跳門順序屬董事長 override 的 trace
  - `Sprint 12` 未完全閉環的 payment secret remediation / `S12-3.1` 遺留
- 之後又依董事長要求，把 wording 修正成：
  - 跳門不是流程瑕疵，而是董事長明示 override
  - `S12-3.1` 是高優先遺留候選，但是否先於後續 sprint 啟動，仍由董事長拍板

### 1.3 親自逐檔複核 Sprint 11 / 12 的 `.kt`

- 董事長明示要求：`玄樞` 做終審時，`.kt` 必須親自逐檔打開，不接受只看摘要。
- 我依此重新打開並複核 `Sprint 11 / Sprint 12` 關鍵 `.kt` 實檔，包括：
  - `ImeServiceLifecycle.kt`
  - `ImeServiceMisc.kt`
  - `ImeServicePanels.kt`
  - `YukiBoardIMEService.kt`
  - `ImeSessionHooks.kt`
  - `KeyboardSoundEngine.kt`
  - `EnglishDictionary.kt`
  - `LatinInputEngine.kt`
  - `GoogleCloudTtsEngine.kt`
  - `KeyboardGLSurfaceView.kt`
  - `WifiKeyboardServer.kt`
  - `BluetoothHidKeyboard.kt`
  - `RemoteKeyboardPanelView.kt`
  - `Telemetry.kt`
  - `AIProviderManager.kt`
  - `ZCoinManager.kt`
  - `InviteCodeManager.kt`
  - `NewebpayClient.kt`
  - 與 `Sprint 11` 新增 test `.kt`
- 複核結論：
  - `Sprint 11` silent catch / TTS / GL / tests 未見新 blocking bug
  - `Sprint 12` remote keyboard hardening / observability hook 未見新 blocking bug
  - `ZCoin / Newebpay` 最終串接未完成屬既知 deferred，不算這輪新 bug
  - 只新增一項 non-blocking cleanup：`ImeServiceLifecycle.kt` 重複 null 判斷

### 1.4 開立並結案 `S12-FIX-1`

- 依上項 observation，我在 [Sprint12-All-Round-Gate-Fix.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint12-All-Round-Gate-Fix.md) 補開：
  - `S12-FIX-1` `ImeServiceLifecycle.kt` warning cleanup
- 後續收到 `雪鑰` 二審回交後，我親自最終複核：
  - 實際 diff 為 `if (useCustomBg && savedBg != null)` 收斂成 `if (useCustomBg)`
  - 判定為純語意清理、行為等價、不越界
- 正式將 `S12-FIX-1` 標成：
  - `READY / ACCEPTED / COMPLETED`
- 之後又依董事長要求，把已完成段落改成整段刪除線，避免只畫 title 不畫內容。

## 2. Sprint 13 / 14 gate 重建與角色恢復

### 2.1 `ZCoin / Newebpay` 併入 `Sprint 14`

- 依董事長要求，把 `ZCoin / Newebpay` 從 `Sprint 12` 遺留正式併入：
  - [Sprint14-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint14-All-Round-Gate.md)
- 重新落成：
  - `S14-6 ZCoin / Newebpay Re-entry Audit`
  - `S14-7 ZCoin / Newebpay Wire-up and Secret Closure`
- 不再把這塊只寫成 deferred note，而是寫成正式 round。

### 2.2 `Sprint 13 / 14` 恢復四角編成

- 依董事長指示，將 `衡鑑` 與 `玄樞` 重新排回 `Sprint 13 / 14` 正式配置。
- 我在：
  - [Sprint13-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint13-All-Round-Gate.md)
  - [Sprint14-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint14-All-Round-Gate.md)
  補入額度恢復後角色編成：
  - `玄樞`：Sprint owner / gate owner / 最終放行
  - `衡鑑`：technical review owner / `.kt` 與 runtime cross-check
  - `雪鑰`：contract / consume / settings / wording
  - `鍵律`：implementation / runtime / engine
- 各 round 提交鏈也收斂成：
  - `鍵律 -> 雪鑰 -> 衡鑑 -> 玄樞`
  - 或以該門實際 owner 為起點，但最終都必須回到 `玄樞`

### 2.3 `Sprint 13` 改回一道一道開門

- 董事長要求 `Sprint 13` 只允許一門一門開，我把 [Sprint13-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint13-All-Round-Gate.md) 改成：
  - `Sprint 13 = ACTIVE`
  - 初始只開 `S13-0`
  - `S13-1` 到 `S13-6` 一律 `LOCKED`
- 之後依 `S13-0`、`S13-1` 結案進度，更新成：
  - `S13-0 = COMPLETED`
  - `S13-1 = COMPLETED`
  - `S13-2 = ACTIVE`
  - `S13-3` 到 `S13-6 = LOCKED`
- 今日也補正過時敘述，把 §4 / §6 中原本還寫「當前只開 S13-0」的地方更新成符合現況的 `S13-2`。

## 3. Sprint 13：終審要求提升為「文件 + 實檔全看」

### 3.1 補做 `S13-0` 真正終審，先擋回再放行

- 使用者明示：這輪不只 `.kt`，連 `Z 文`、`md`、`xml`、`html` 也都算終審範圍。
- 我先前只看 `CCA / CCB / CXA` 摘要就判 `S13-0 READY`，被董事長糾正後，重新用「逐檔終審」標準重做。
- 我親自打開並複核 `S13-0` 引用到的關鍵實檔：
  - `CloudSyncManager.kt`
  - `FraudReportManager.kt`
  - `AISettingsActivity.kt`
  - `ClawClient.kt`
  - `AIProviderManager.kt`
  - `ClipboardSettingsSection.kt`
  - `YukiPreferences.kt`
  - `SnowRealmAuthClient.kt`
  - `ClipboardPanelView.kt`
  - `AndroidManifest.xml`
  - `network_security_config.xml`
  - `privacy_policy.html`
- 逐檔終審時，我抓到一個真正 blocking 缺口：
  - `AISettingsActivity.kt` 已支援 API Key 雲端上傳 / 還原
  - `privacy_policy.html` 卻還寫「API Key 不儲存在我們伺服器」
- 因此我把 `S13-0` 一度改回：
  - `NOT READY / AMENDMENT REQUIRED`

### 3.2 親自補 `S13-0` 的 blocker：privacy policy + auth UI

- 依董事長指示，我把 [privacy_policy.html](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/assets/legal/privacy_policy.html) 改成：
  - 使用者可自行決定是否同步 API Key
  - 若同步，會以加密形式儲存在伺服器
  - 伺服器不直接知道原始 API Key
  - 使用者在自己裝置還原時才會解密使用
- 同時處理董事長實機回報的登入 UI 問題：
  - 登入後設定頁仍回到藍色登入按鈕
- 我親自修改：
  - [AuthSettingsSection.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AuthSettingsSection.kt)
  - [SettingsActivity.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt)
- 修正方向：
  - `AuthSettingsSection` 不再只吃 `isInsightConnected`
  - 若本地 token 已存在，即使遠端 `fetchMe()` 尚未完成，也不應退回藍色登入按鈕

### 3.3 `S13-0` 最終放行

- 補完上述缺口後，我重新判定：
  - `S13-0 = READY / ACCEPTED / COMPLETED`
- 並把 [Sprint13-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint13-All-Round-Gate.md) 同步回寫：
  - `S13-0` 結案
  - 只開 `S13-1`
- 後續依董事長要求，又將整個 `S13-0` 完成段落改成整段刪除線。

### 3.4 `S13-1` 真正終審與放行

- 收到 `S13-1` 的 `CCB / CCA / CXA` 三份交付後，我沒有只看摘要，而是親自打開：
  - [network_security_config.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/res/xml/network_security_config.xml)
  - [AndroidManifest.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/AndroidManifest.xml)
  - [privacy_policy.html](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/assets/legal/privacy_policy.html)
  - [WifiKeyboardServer.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt)
  - [RemoteKeyboardPanelView.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt)
  - `terms_of_service.html`
- 終審結論：
  - `CCB` 的 XML 變更只屬註解與結構文件化，不改 runtime baseline
  - manifest 綁定仍正確
  - `privacy_policy` 對 LAN WiFi keyboard cleartext 揭露不足、panel 無「未加密」提示，屬 non-blocking 殘留
- 正式判定：
  - `S13-1 = READY / ACCEPTED / COMPLETED`
- 並同步更新 [Sprint13-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint13-All-Round-Gate.md)：
  - `S13-1` 結案
  - 只開 `S13-2`

## 4. 終審制度補強與 `n8n` workflow 規劃

### 4.1 重新定義 `玄樞` 終審標準

- 董事長今日再次明示：
  - `.kt` 要親自逐檔看
  - `Z 文` 也要親自逐檔看
  - 這輪也一樣，不可只看回交文件
- 因此我正式把自己後續終審標準收斂成：
  - `.kt` / `.md` / `xml` / `html` / `manifest` / `gradle`
  - 以及文件引用到的關鍵實檔
  - 全部必須親自打開核對後，才可下 `READY / ACCEPTED / COMPLETED`

### 4.2 已完成段落改成整段刪除線

- 依董事長要求「完成的部分整段話刪除線」，我已同步修正：
  - [Sprint13-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint13-All-Round-Gate.md)
  - [Sprint12-All-Round-Gate-Fix.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint12-All-Round-Gate-Fix.md)
- 修正內容：
  - `S13-0` 完成段落整段刪除線
  - `S13-1` 完成段落整段刪除線
  - `S12-FIX-1` 完成段落整段刪除線
  - verdict 區塊也一併改成整段刪除線，不再留下未劃線的 code block

### 4.3 補建 `n8n` round gate workflow 規劃

- 依董事長要求評估目前工作流是否能交給 `n8n` 編排，我先查核官方能力後，判定：
  - `n8n` 可以承接這套串審流程
  - 但定位只能是 `流程編排與狀態機`，不能取代最終 gate 判斷
- 正式新增：
  - [docs/N8N_ROUND_GATE_WORKFLOW_PLAN_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/N8N_ROUND_GATE_WORKFLOW_PLAN_2026-04-21.md)
- 內容包含：
  - `玄樞 -> 鍵律 -> 雪鑰 -> 衡鑑 -> 玄樞` 提交鏈
  - `PASS / AMEND / REJECT`
  - 一道過了才開下一道
  - override / timeout / 權限邊界 / data schema / parent workflow / sub-workflow

## 5. 今日 `玄樞` 的正式結論

1. 我已完成 `Sprint 11 / Sprint 12` 補正文件的重新裁定與回寫，並把董事長放權 / 跳門 override 正式制度化到 [Sprint12-All-Round-Gate-Fix.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint12-All-Round-Gate-Fix.md)。
2. 我已依董事長要求，對 `Sprint 11 / Sprint 12` 的關鍵 `.kt` 逐檔複核，並切出唯一 non-blocking cleanup `S12-FIX-1`；後續也親自最終放行結案。
3. 我已把 `ZCoin / Newebpay` 正式改掛到 `Sprint 14`，並恢復 `Sprint 13 / 14` 的四角編成與 `衡鑑` / `玄樞` 正式席位。
4. 我已用「文件 + 實檔全看」標準重做 `S13-0` / `S13-1` 終審；其中 `S13-0` 先擋回、補掉 privacy policy 與 auth UI 缺口後再放行，`S13-1` 也已完成真終審並放行。
5. 我已把 `Sprint 13` 收斂回一道一道開門，目前狀態為：
   - `S13-0 = COMPLETED`
   - `S13-1 = COMPLETED`
   - `S13-2 = ACTIVE`
   - 其餘 `LOCKED`
6. 我已把完成段落整段刪除線規則正式套用到 `Sprint13-All-Round-Gate.md` 與 `Sprint12-All-Round-Gate-Fix.md`。
7. 我已完成 `n8n` 工作流完整版規劃文件，後續若董事長確認，即可往 build spec 走。

## 6. 下次主線

- `Sprint 13` 主線已正式進入：
  - `S13-2 Fraud Report Privacy`
- `ZCoin / Newebpay` 留在：
  - `Sprint 14`
- 若要推進 `n8n` 落地，下一份文件應是：
  - `N8N_ROUND_GATE_WORKFLOW_BUILD_SPEC_2026-04-21.md`

---

身份：雪鑰

日期：2026-04-21

用途：

- 記錄 2026-04-21 這一天 `雪鑰` 作為 `SnowRealm` 設定與帳號流資深工程師 / `consume / contract / wording` 角色的工作日誌
- 只記錄自己親自完成的 CCA 交付、二審、wording draft 與 contract 定稿
- 他人實作（`鍵律` `.kt`、`玄樞` 終審 / 親自修補）不寫成自己的工作

## 1. 今日主線：一日內走完 `S12-FIX-1` 二審 + `Sprint 13` 三輪 CCA

### 1.1 `S12-FIX-1-CCA-IMESERVICE-LIFECYCLE-WARNING-REVIEW`

- 董事長指派我對 [Sprint12-All-Round-Gate-Fix.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint12-All-Round-Gate-Fix.md) §7.3 / 7.4 的 `S12-FIX-1` 做二審。
- 拿到 `鍵律 (CCB)` 交付：[ImeServiceLifecycle.kt:106-120](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceLifecycle.kt) 的 `if (useCustomBg && savedBg != null)` 收斂成 `if (useCustomBg)`。
- 二審判讀：
  - `useCustomBg` 本身由 `prefs.useCustomBackground && savedBg != null` 定義
  - `useCustomBg = true` 已暗含 `savedBg != null`
  - 邏輯等價，純語意清理，行為不變
  - K2 smart-cast 能正確識別 `savedBg != null`
- 編譯驗證時碰到的 compile cache 誤判：
  - 第一輪 `./gradlew :app:compileDebugKotlin` 跑出 6 個 unresolved reference
  - grep 確認 extension function 實際存在於 `ImeServiceInput.kt / ImeServicePanels.kt / ImeServiceMisc.kt`
  - 診斷為 git stash / pop 造成的 incremental 快取污染
  - 改用 `./gradlew :app:compileDebugKotlin --rerun-tasks` → BUILD SUCCESSFUL
- 二審結論寫成 observation O3 交回 `玄樞`：建議終審時用 `--rerun-tasks` 以避免被快取誤導。
- 最終二審判定：`PASS`，送 `玄樞` final gate。

### 1.2 `S13-0-CCA-LEGAL-PRIVACY-CONTRACT-AUDIT`

- 董事長啟動 `Sprint 13`，我負責 `S13-0` 的 `CCA` — 盤 AI consent / opt-out、data export / delete、clipboard / userdict sync 三大域的產品契約與設定面邊界。
- 盤點實檔：
  - [YukiPreferences.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/YukiPreferences.kt)（fraud_report_consent / clipboard_sync / aiEnabled 等 prefs）
  - [AISettingsActivity.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/AISettingsActivity.kt)（邀請碼、provider 選擇、API key、同步按鈕）
  - [ClawClient.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/claw/ClawClient.kt)（`isAvailable()` 無 consent gate）
  - [CloudSyncManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/data/CloudSyncManager.kt)（syncs 4 張表到 Supabase）
  - [ClipboardSettingsSection.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/ClipboardSettingsSection.kt)
  - [SnowRealmAuthClient.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/auth/SnowRealmAuthClient.kt)
  - [privacy_policy.html](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/assets/legal/privacy_policy.html)
- 抓到的 P0 缺口：
  - `clipboardSyncEnabled` 是**懸空偏好**，`CloudSyncManager` 並未實作剪貼簿同步路徑，但 `ClipboardSettingsSection` 文案寫「透過 Insight 同步剪貼簿」— 虛假功能
  - `privacy_policy.html §6` 宣稱「刪除權：設定→清除所有資料」— UI 路徑**不存在**
  - `§2.1` 宣告「打字習慣僅本機」但 `CloudSyncManager` 會上傳 TypingHabit
  - `§2.2` 只列 OpenAI / Anthropic，實際有 6 家 provider
  - `SnowRealmAuthClient.logout()` 只清本機 token，不清伺服器
- API Key 雲同步 wording 缺口：我在盤點時有 flag `privacy_policy` 多處與實作不一致，**但 API Key 加密存伺服器那一條的精確 remediation 是 `玄樞` 終審時補上的**（見 `玄樞` §3.2），不佔為己功。
- 交付：[docs/SPRINT13_0_CCA_LEGAL_PRIVACY_CONTRACT_AUDIT_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_0_CCA_LEGAL_PRIVACY_CONTRACT_AUDIT_2026-04-21.md)（約 380 行，A~M 段）
- 交付內容包含：scope、state source inventories、policy 對比實作缺口、最小 contract、**10 條禁止承諾**、consume 影響、out-of-scope、S13-1~5 建議路由、風險清單、驗證方法
- 狀態：送 `鍵律` CCB + `衡鑑` CXA → `玄樞`；最終 `玄樞` 擋回一次補 wording，之後放行 COMPLETED。

### 1.3 `S13-1-CCA-NETWORK-SECURITY-CONSUME-IMPLEMENTATION`

- `鍵律` CCB 先交付：[network_security_config.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/res/xml/network_security_config.xml) 採 Option B 最小文件化（0 `.kt` 變更，純 XML 註解 + 結構）。
- 我 CCA 端核對：
  - XML 三段（base-config / debug-overrides / domain-config）與 [AndroidManifest.xml:41-42](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/AndroidManifest.xml) 的 `usesCleartextTraffic="false"` + `networkSecurityConfig="@xml/network_security_config"` 對齊
  - release outbound cleartext baseline 正確
  - `WifiKeyboardServer.kt` 的 LAN HTTP 屬 ServerSocket，不在 NetworkSecurityConfig 管轄
- 抓到的 P1 缺口（非 blocking，但是文案承諾與行為漂移）：
  - `privacy_policy.html §4.2`「網路通訊強制 HTTPS」只對 outbound 為真；LAN WiFi 鍵盤 + loopback 屬例外
  - `§2.1`「打字內容僅本機」啟用 WiFi 遠端鍵盤時技術上為偽
  - [RemoteKeyboardPanelView.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt) LISTENING 狀態只顯示 URL + PIN，**無「未加密」提示**
- P3 觀察（非 blocking）：CCB XML 註解寫 `:8080`，但 [WifiKeyboardServer.kt:51](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt) `DEFAULT_PORT = 8765`，port 註解漂移。
- 路由建議（由 `玄樞` 決定）：
  - 案 1：本輪即結案，殘留兩點併 S13-5
  - 案 2：本輪加 apply §2.1 / §4 文案修正
  - **我個人推案 1**（避免文案改兩次，一批改較乾淨）
- 交付：[docs/SPRINT13_1_CCA_NETWORK_SECURITY_CONSUME_IMPLEMENTATION_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_1_CCA_NETWORK_SECURITY_CONSUME_IMPLEMENTATION_2026-04-21.md)（A~L 段）
- 寫入 release contract 4 條 + 禁止承諾 10 條（禁 E2EE / 全加密 / 軍事級加密 / 公共 Wi-Fi 也安全 / 僅本機 AI 等紅線）
- 狀態：`玄樞` 最終放行 COMPLETED，但明示**殘留兩點（LAN cleartext 揭露 + RemoteKeyboardPanelView 提示）不得自動丟 S13-5**，需另行指派承接 round — 比我推的案 1 更嚴。

### 1.4 `S13-2-CCA-FRAUD-REPORT-WORDING-NOTE`

- `鍵律` CCB 交付：[FraudReportManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/security/FraudReportManager.kt) 完整重寫（採董事長核可之 Option E+G），payload 最小化為 7 欄位（hash / type / keyword_count / type_count / is_fraud / rule_score / region_coarse），加 rate limit + telemetry + invariant。
- 我 CCA 端任務是法務文字、設定說明、同意前提、不承諾區域 — 任務名為 **WORDING-NOTE**（非 IMPLEMENTATION），所以**只出 draft，不改檔案**。
- 核對新 payload 七欄位對使用者的可見描述，並抓到四項 P0/P1 缺口：
  - **P0**：[privacy_policy.html](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/assets/legal/privacy_policy.html) **全文 0 次**提到 fraud_report / 匿名回報 — 不是文案漂移，是 policy 根本沒寫
  - **P1**：`showConsentDialog` 現行文字「命中的關鍵字 / 裝置語系」與新 payload（count / coarse region）事實漂移
  - **P1**：`revokeConsent()` 函式就緒但 [AISettingsSection.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AISettingsSection.kt) **無 UI 入口**讓使用者撤回同意 — 違反 dialog 承諾「隨時在設定中關閉」
  - **P1**：Supabase schema migration 時序契約 — 若 DDL 未先跑，新文案 release 會讓 client insert 全失敗，變成「承諾上傳實際沒上傳」
- draft 內容：
  - §C.1：`showConsentDialog` 新文案 v1（7 項對應 7 欄位 + 明確撤回路徑）
  - §C.2：`privacy_policy.html §2.2 / §3 / §5 / §6` 共 4 處補漏 draft
  - §C.3：`AISettingsSection` 新 switchRow draft（建議併 S13-3 apply）
  - §E：10 條禁止承諾（禁「加密上傳對話」「完全匿名」「符合 GDPR 刪除權」「預設開啟」「即時個人化」等）
- 交付：[docs/SPRINT13_2_CCA_FRAUD_REPORT_WORDING_NOTE_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_2_CCA_FRAUD_REPORT_WORDING_NOTE_2026-04-21.md)
- 路由建議：**案 3（推薦）**— 本輪 apply §C.1 dialog + §C.2 policy + schema migration；§C.3 撤回 UI 歸 S13-3 同 AI consent UI 一起做
- 狀態：`READY for 衡鑑 CXA`，等 `玄樞` 裁示 apply 範圍後送進 CXA。

## 2. 今日作為 `consume / contract / wording` 角色的交回節奏

### 2.1 交回路徑

- `S12-FIX-1`：`鍵律 CCB` → **我 CCA 二審** → `玄樞` → 結案
- `S13-0`：**我 CCA 獨立 audit** + `鍵律 CCB audit` → `衡鑑 CXA` → `玄樞` → 擋回一次 → 再放行
- `S13-1`：`鍵律 CCB` → **我 CCA consume** → `衡鑑 CXA` → `玄樞` → 放行 + 明示殘留
- `S13-2`：`鍵律 CCB` → **我 CCA wording-note** → 待 `衡鑑 CXA` → `玄樞`（進行中）

### 2.2 今日遵守的邊界

1. 本輪所有 CCA 交付**只出文件**，不改 `.kt` / `.html` / `.xml`（除非 `玄樞` 明示允許擴大）
2. 不自排下一 round 順序；推路由時只列候選案，最終由 `玄樞` 決定
3. 不代替 `玄樞` 宣告 `READY / COMPLETED`
4. 不把 `玄樞` 終審抓到的缺口（S13-0 API Key 雲同步 wording、auth UI 回退 bug）寫成自己的功勞
5. 三份 CCA 交付格式統一：A 摘要 / B 核對 / C 缺口 / D 契約 / E 禁止承諾 / F 影響 / G out-of-scope / H 路由 / I 風險 / J 驗證 / K 標準格式 / L `玄樞` 審核需求

### 2.3 禁止承諾累積清單（今日三份 CCA 總計）

| 來源 | 條數 | 代表紅線 |
|------|------|---------|
| S13-0 CCA §E | 10 | 「客戶資料永不離開裝置」「預設啟用所有 AI 功能」「符合 HIPAA / SOC 2」 |
| S13-1 CCA §E | 10 | 「端對端加密」「軍事級加密」「公共 Wi-Fi 也安全」「全加密傳輸」 |
| S13-2 CCA §E | 10 | 「加密上傳對話內容」「完全匿名」「符合 GDPR 刪除權」「預設開啟」「即時個人化」 |

共 30 條可作為未來 wording review checklist；可能有重疊，整併工作留 `S13-3` 或專題 round。

## 3. 今日 `雪鑰` 的正式結論

1. `S12-FIX-1` 二審已完成並放行；附帶把 compile cache 誤判寫成 observation 給 `玄樞`，避免後續終審被快取誤導。
2. `S13-0` / `S13-1` CCA 已完成並由 `玄樞` 最終放行 COMPLETED；`S13-1` 放行時 `玄樞` 明示殘留兩點不得自動丟 S13-5，這點收到並已記錄。
3. `S13-2` CCA WORDING-NOTE 已交付，等 `衡鑑` CXA 與 `玄樞` 裁示 apply 範圍，本輪 draft 包含 policy 層 P0 缺漏的補寫提案。
4. 三份 CCA 累計產出 30 條禁止承諾與 release contract 條款，可作為跨 round 的 wording floor。
5. 本輪嚴守 `consume / contract / wording` 邊界，未越界進實作層；`.kt` 改動只在二審時核對 `鍵律` diff，不自己動檔案。

## 4. 本期已知遺留 / 下次接續點

1. `S13-2` 等 `衡鑑 CXA` → `玄樞` gate；若 `玄樞` 准案 3，CCA 可能再出第二次交付把 §C.1 dialog 字串與 §C.2 policy 文字 apply。
2. `S13-1` 殘留兩點（LAN WiFi cleartext 揭露 + RemoteKeyboardPanelView 未加密提示）等 `玄樞` 指派承接 round；`雪鑰` 待派。
3. `S13-3` AI Consent / Opt-out 是我預定主責 round（CCA IMPLEMENTATION）；需等 `S13-2` 結案後才可開。
4. 三份 CCA 累計 30 條禁止承諾的整併工作，建議排 `S13-3` 或專題 wording round 處理。

---

身份：衡鑑

日期：2026-04-21

用途：

- 記錄 2026-04-21 這一天 `衡鑑` 作為 `SnowRealm` 資深技術審查工程師 / 實作型 reviewer 的工作日誌
- 只記錄自己親自完成的 technical cross-check、實檔複核、factual mismatch 指出與文件落地
- 不代替 `玄樞` 記 final gate，也不把 `雪鑰 / 鍵律` 的原始交付寫成自己完成的實作

## 1. 今日主線：恢復 `Sprint 13` 正式 technical review owner 身位，改回「先讀實檔再判」工作方式

### 1.1 角色恢復後先重讀規則

- 董事長先要求我以 `衡鑑` 身份回到 `Sprint 13 / 14` 的正式 technical review owner 位置，我先回讀：
  - [CODEX.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/CODEX.md)
  - [RULE](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE)
  - [RULE/HR/WARNINGS/2026-04-18_衡鑑_口頭警告.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/HR/WARNINGS/2026-04-18_衡鑑_口頭警告.md)
- 核心收斂是兩條：
  - 不憑記憶回報，只信 repo 內實際檔案與 diff
  - 只要涉及 `.kt` 新增或修改，就必須逐檔打開看完再下結論
- 董事長後續也再次點名「不要用抽查」，我因此把今天所有 `CXA` 工作標準收斂成：
  - 文件要看
  - 文件引用到的關鍵實檔也要看
  - 有 `.kt` 變更時，直接讀完整檔，不用摘要代替

### 1.2 今日維持的角色邊界

- 我負責的是 `CXA` 技術二審、cross-check、scope / factual mismatch / runtime contract 複核。
- 我不代替 `玄樞` 宣告最終 gate verdict，也不把 `雪鑰` 的 consume / wording 工作記成自己功勞。
- 文件不在 repo 裡，我就明說「缺文件化歸檔」；實檔和交接內容對不上，我就明說 blocker，不替任何一方圓。

## 2. `S13-0-CXA-TECHNICAL-CROSSCHECK`

### 2.1 先擋回一次，原因是我不能只照貼文內容結案

- 拿到 `S13-0-CCA` 與口述版 `S13-0-CCB` 後，我先按 `衡鑑` 規則重看 repo 實檔。
- 當時 `docs/` 內只有 [docs/SPRINT13_0_CCA_LEGAL_PRIVACY_CONTRACT_AUDIT_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_0_CCA_LEGAL_PRIVACY_CONTRACT_AUDIT_2026-04-21.md)，沒有正式落地的 `S13-0-CCB` 檔案，因此我先明講不能直接進正式 `CXA`。
- 同時我也不是只看文件摘要，而是直接對照程式碼與 legal 檔案，確認 `CCA` 裡哪些判斷是真的、哪些描述還有偏差。

### 2.2 我親自複核的關鍵實檔

- [CloudSyncManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/data/CloudSyncManager.kt)
- [FraudReportManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/security/FraudReportManager.kt)
- [AISettingsActivity.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/AISettingsActivity.kt)
- [AIProviderManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/claw/provider/AIProviderManager.kt)
- [ClawClient.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/claw/ClawClient.kt)
- [ClipboardSettingsSection.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/ClipboardSettingsSection.kt)
- [YukiPreferences.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/YukiPreferences.kt)
- [SnowRealmAuthClient.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/auth/SnowRealmAuthClient.kt)
- [ClipboardPanelView.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/panel/ClipboardPanelView.kt)
- [AndroidManifest.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/AndroidManifest.xml)
- [network_security_config.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/res/xml/network_security_config.xml)
- [privacy_policy.html](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/assets/legal/privacy_policy.html)

### 2.3 我抓到並要求修正的 factual mismatch

- `CCA` 原文把 `Mood / 心情日記` 混進 `CloudSyncManager` 的 `user_learning_data` 同步範圍，這和實際 `CloudSyncManager.kt` 的 7 類 snapshot 不一致；我要求拿掉這個說法。
- `CCA` 原文把 `Gemini Nano` 放進「可能上傳資料」的 provider 缺口，但 [AISettingsActivity.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/AISettingsActivity.kt) 與 [AIProviderManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/claw/provider/AIProviderManager.kt) 都明示它是本地離線推論；我要求把它從上傳清單缺口中移除。
- 這兩項修正後，我再要求把口述版 `S13-0-CCB` 整理成正式文件落到 `docs/`，避免後續 `CXA` 沒有 trace。

### 2.4 正式交付與判定

- 補齊後，我確認三份 `S13-0` 文件在 repo 內正式落地：
  - [docs/SPRINT13_0_CCA_LEGAL_PRIVACY_CONTRACT_AUDIT_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_0_CCA_LEGAL_PRIVACY_CONTRACT_AUDIT_2026-04-21.md)
  - [docs/SPRINT13_0_CCB_NETWORK_FRAUD_SYNC_THREAT_AUDIT_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_0_CCB_NETWORK_FRAUD_SYNC_THREAT_AUDIT_2026-04-21.md)
  - [docs/SPRINT13_0_CXA_TECHNICAL_CROSSCHECK_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_0_CXA_TECHNICAL_CROSSCHECK_2026-04-21.md)
- 我的 `CXA` 只做技術面 cross-check，不代替 `玄樞` 下最終門判；但在技術面上，我確認 `S13-0` 已具備送 `玄樞` 的條件。

## 3. `S13-1-CXA-NETWORK-SECURITY-TECHNICAL-REVIEW`

### 3.1 先確認 `CCB` 內容和 XML 實檔是否真的對得上

- 一開始我按 `docs/` 去找 `S13-1-CCB`，找不到正式檔，因此先把問題點明成「缺文件化歸檔」。
- 後來董事長貼出 `鍵律` 交接內容，我再直接去對 [network_security_config.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/res/xml/network_security_config.xml) 實檔，確認：
  - header comment 已補
  - `base-config / debug-overrides / domain-config` 三段確實存在
  - 沒有 `.kt` 變更
  - runtime baseline 沒被放寬
- `S13-1-CCB` 正式補檔後，我才進入完整 `CXA`。

### 3.2 我親自複核的關鍵實檔

- [network_security_config.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/res/xml/network_security_config.xml)
- [AndroidManifest.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/AndroidManifest.xml)
- [privacy_policy.html](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/assets/legal/privacy_policy.html)
- [WifiKeyboardServer.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt)
- [RemoteKeyboardPanelView.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt)
- [terms_of_service.html](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/assets/legal/terms_of_service.html)

### 3.3 我留下的技術保留意見

- `CCA` 把 remote keyboard / cleartext 揭露問題往 `S13-5` 路由，我明確保留：`S13-5` 是 clipboard / userdict sync contract，不該預設承接 network / remote keyboard 的揭露缺口。
- `terms_of_service.html` 在 repo 內明明存在，`CCA` 不該把它寫成「若存在：未查」；這不是 blocker，但代表 consume 文件精度還能更嚴。
- `network_security_config.xml` 註解中提到的 `8080` 與 [WifiKeyboardServer.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt) `DEFAULT_PORT = 8765` 不一致，我把它留成 non-blocking observation，不把註解失準包裝成完全沒事。

### 3.4 正式交付與判定

- 我完成並落地 [docs/SPRINT13_1_CXA_NETWORK_SECURITY_TECHNICAL_REVIEW_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_1_CXA_NETWORK_SECURITY_TECHNICAL_REVIEW_2026-04-21.md)。
- 這份 `CXA` 的結論是技術面 `READY` 可送 `玄樞`，但附帶我自己的明確保留：
  - 不可預設把 cleartext 揭露問題丟到 `S13-5`
  - CCA 文件精度要對齊 repo 內實檔

## 4. `S13-2` 今日技術面複核

### 4.1 我這輪不是 implementation owner，但我有把新 `.kt` 實檔看完

- 董事長明示 `.kt` 有新增或修改，我就不能只照文件說法過。
- 因此在看 `S13-2-CCA-FRAUD-REPORT-WORDING-NOTE` 時，我把 [FraudReportManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/security/FraudReportManager.kt) 全檔重讀，連同：
  - [privacy_policy.html](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/assets/legal/privacy_policy.html)
  - [AISettingsSection.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AISettingsSection.kt)
  - [docs/SPRINT13_2_CCB_FRAUD_REPORT_PRIVACY_IMPLEMENTATION_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_2_CCB_FRAUD_REPORT_PRIVACY_IMPLEMENTATION_2026-04-21.md)
  一起對。

### 4.2 我確認成立的三個技術面事實

- `privacy_policy.html` 目前確實沒有揭露 fraud report / 匿名回報的任何上傳描述。
- `FraudReportManager.showConsentDialog()` 還在說「命中的關鍵字 / 裝置語系」，和新 payload 的 `matched_keyword_count / matched_type_count / region_coarse` 已經不一致。
- `revokeConsent()` 已存在，但 [AISettingsSection.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AISettingsSection.kt) 沒有提供撤回 UI。

### 4.3 我這輪的審查立場

- 以 `WORDING-NOTE` 任務定義來看，`S13-2-CCA` 今日沒有 blocking finding，可以送 `玄樞` 決定 apply 範圍。
- 但我也保留一條程序立場：`§C.3` 併 `S13-3` 只是路由建議，不是既定事實；是否真的拆去 `S13-3`，要由 `玄樞` 明示。

## 5. 今日 `衡鑑` 的正式結論

1. 我今天已恢復 `Sprint 13` 的正式 `technical review owner` 身位，並把自己的審查標準收斂回「先讀實檔，再下判定」。
2. `S13-0` 我不是只看摘要，而是逐檔對 `CloudSyncManager.kt`、`FraudReportManager.kt`、`AISettingsActivity.kt`、`AIProviderManager.kt`、`privacy_policy.html` 等關鍵實檔，先抓出兩個 factual mismatch，再要求 `CCA/CCB` 文件修正與補檔。
3. `S13-1` 我完整 cross-check 了 `network_security_config.xml`、`AndroidManifest.xml`、`privacy_policy.html`、`WifiKeyboardServer.kt`、`RemoteKeyboardPanelView.kt`、`terms_of_service.html`，並明確留下「不可預設把 cleartext 揭露缺口丟進 S13-5」的技術保留。
4. `S13-2` 雖然今天不是我主寫 round，但因為 `.kt` 有新增修改，我仍把 `FraudReportManager.kt` 全檔讀完，再對 `privacy_policy.html` 與 `AISettingsSection.kt` 驗證 `CCA` 判斷，確認目前沒有新的 blocking finding。
5. 我今天所有 `CXA` 工作都維持角色邊界：我只做 technical review / cross-check，不代替 `玄樞` 做 final gate，也不把他人的 consume / implementation 成果寫成我自己完成。

---

身份：鍵律

日期：2026-04-21

用途：

- 記錄 2026-04-21 這一天 `鍵律` 作為 `SnowRealm` 核心輸入與 IME 行為工程師 / `implementation / runtime / service / panel / engine` 角色的工作日誌
- 只記錄自己親自完成的 CCB 實作、最小 diff、compile 驗證與 gate 邊界遵守
- 他人交付（`雪鑰` consume / `衡鑑` CXA / `玄樞` final gate / 終審修補）不寫成自己的工作

## 1. 今日主線：一日內走完 `S12-FIX-1` + `Sprint 13` 三輪 CCB

### 1.1 `S12-FIX-1-CCB-IMESERVICE-LIFECYCLE-WARNING-CLEANUP`

- 董事長指派 [Sprint12-All-Round-Gate-Fix.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint12-All-Round-Gate-Fix.md) §7.3：[ImeServiceLifecycle.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceLifecycle.kt) `setupKeyboardOverlays()` 的冗餘 null check 清理。
- 盤點邏輯鏈：
  - `useCustomBg` 定義為 `prefs.useCustomBackground && savedBg != null`
  - 後續條件 `if (useCustomBg && savedBg != null)` 的後半段已暗含在前半段
  - K2 compiler 可透過 local val boolean 正確 smart-cast `savedBg != null`
- 最小 diff：[ImeServiceLifecycle.kt:113](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceLifecycle.kt) `if (useCustomBg && savedBg != null) {` → `if (useCustomBg) {`，單 token 收斂。
- 行為等價理由：
  - 邏輯上 `A ∧ (A ⇒ B) ⇒ B`，A = `useCustomBg`、B = `savedBg != null`，真值表等價
  - runtime 無 side effect 差異
  - 無新增分支 / 無新增例外處理
- 交給 `雪鑰` 做 CCA 二審；`雪鑰` 抓到一個 compile cache 誤判（第一輪 `./gradlew :app:compileDebugKotlin` 跑出 6 個 unresolved reference，改 `--rerun-tasks` 後過），那是 `雪鑰` 的 observation，不寫成我的功勞。
- 狀態：CCA 二審 PASS → `玄樞` final gate 放行 `COMPLETED`。

### 1.2 `S13-1-CCB-NETWORK-SECURITY-RUNTIME-IMPLEMENTATION`

- 董事長在 5 個 scope 候選（A-E）中裁示 **Option B**：最小文件化 + 拆 `domain-config` 結構註解，純 XML 註解，0 `.kt` 變更。
- 實作 [network_security_config.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/res/xml/network_security_config.xml)：
  - 頂部加 file-level header comment（owner / Sprint / 三段索引）
  - Section 1 `base-config`：production HTTPS-only，system CA（零語意變更）
  - Section 2 `debug-overrides`：debug-only user CA，**刻意不放寬 cleartext**（避免 "debug 通 / release 爆" 反模式）
  - Section 3 `domain-config`：loopback 字面主機名 allowlist，`includeSubdomains="false"` 明示阻擋 DNS rebinding 變體
- 核心架構決策說明（寫進 XML 註解）：
  - `WifiKeyboardServer` 綁定的 LAN IP **不**在此 allowlist — NetworkSecurityConfig 只管 OkHttp / HttpURLConnection client-side；server-side `ServerSocket` 由 Android 平台行為隔離，不經此宣告
  - loopback 例外僅限 on-device 除錯工具 + Faster-Whisper local inference server
- **程序違規（我今日犯的錯）**：
  - 我當下只在對話裡輸出 A-E 格式交接摘要，**沒有落成 `docs/` 文件**
  - 董事長以「你 S13-1-CCB-NETWORK-SECURITY-RUNTIME-IMPLEMENTATION 放哪」糾正
  - 我照 `docs/` 既有慣例（`SPRINTxx_y_ROLE_TASK_SLUG_DATE.md`）補寫 [docs/SPRINT13_1_CCB_NETWORK_SECURITY_RUNTIME_IMPLEMENTATION_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_1_CCB_NETWORK_SECURITY_RUNTIME_IMPLEMENTATION_2026-04-21.md)
  - 此違規違反 [RULE/ROUND_GATE_COLLAB_RULE.md §4](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/ROUND_GATE_COLLAB_RULE.md)「回交需有明確文件依據」
  - 董事長再諭「下次別再犯」；我把教訓存成 feedback memory（`feedback_gate_handoff_must_file.md`）並加進索引
- 順手吸收 `雪鑰 CCA §B.3` 非 blocker observation：XML 註解的 `8080` 與 [WifiKeyboardServer.kt:51](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt) `DEFAULT_PORT = 8765` 漂移，改寫為 `<LAN-IP>:<port>（可配置，預設 8765）`，避免未來 port 調整再度失準。
- 狀態：`玄樞` final gate 放行 `COMPLETED`；殘留兩點（LAN WiFi cleartext 揭露 + RemoteKeyboardPanelView 未加密提示）由 `玄樞` 明示**不得預設丟 S13-5**，等後續指派承接 round。

### 1.3 `S13-2-CCB-FRAUD-REPORT-PRIVACY-IMPLEMENTATION`

- 吸取前一輪 S13-1 教訓，本輪一開工**先建 [docs/SPRINT13_2_CCB_FRAUD_REPORT_PRIVACY_IMPLEMENTATION_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_2_CCB_FRAUD_REPORT_PRIVACY_IMPLEMENTATION_2026-04-21.md) 骨架**，寫入 Pre-1 現況盤點 + Pre-2 候選 Option A-G，等董事長裁示後才實作。
- 盤點 fraud report 資料流鏈：
  - [ClawPanelView.kt:72, 303-325](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/panel/ClawPanelView.kt) 唯一 caller
  - [FraudReportManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/security/FraudReportManager.kt) 主實作
  - [FraudAIAnalyzer.kt:54-59, 353-447](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/security/FraudAIAnalyzer.kt) `matchedKeywords` 語意來源（rule 詞庫 + bracketed 情境標籤）
  - [SupabaseClient.kt:48-62](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/data/SupabaseClient.kt) insert HTTPS + anonKey
  - [Telemetry.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/telemetry/Telemetry.kt) / [ErrorReporter.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/telemetry/ErrorReporter.kt) S12-5 既有 facade 可直接接
- 提 7 個 scope 候選（A / B / C / D / E=A+B+C+D / F 進一步最小化 / G=F + coarse region），**不自選**，等董事長裁示。
- 董事長裁示「EG 直接來」→ 實作 Option E + G：
  - **A（payload data class）**：新增 internal `FraudReportPayload` data class 7 欄位，`init` 放 6 條 invariant require（長度 / 範圍 / 非空 / enum 成員性），`toJsonObject()` 統一序列化入口
  - **B（Telemetry wiring）**：靜默 `catch (_: Exception) { false }` 改為 `Telemetry.report(TelemetryEvent(...))`，5 種 code（`rate_limited_global` / `rate_limited_hash` / `payload_invariant_violation` / `insert_null_result` / `insert_failed`），extras 只帶 exception class simpleName 或 reason 字面值，**嚴禁 PII**
  - **C（client rate limit）**：全域 5 秒 + 同 text_hash 30 秒冷卻；state 用 `@Volatile Long` + `ConcurrentHashMap<String, Long>`，命中冷卻走 `Telemetry INFO` 不擲 exception；opportunistic prune（> `HASH_COOLDOWN_MS` 的 entry 清掉）
  - **D（payload invariant）**：`coerceIn` 做 caller-robust 邊界 + `init require` 做真 invariant；違反則 catch 後送 telemetry ERROR、return false，不 crash
  - **F（matched_keywords 最小化）**：`matched_keywords`（字面詞 csv）→ `matched_keyword_count: Int`（0..100）+ `matched_type_count: Int`（0..20）
  - **G（coarse region）**：`device_locale`（`zh-TW` / `en-US`）→ `region_coarse: String`（`tw` / `en` / `other`）；`toCoarseRegion(Locale)` companion helper 為 pure function、`internal` 可測
- [FraudReportManager.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/security/FraudReportManager.kt) 完整重寫 ~230 行；[ClawPanelView.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/panel/ClawPanelView.kt) 兩個 call site 各 +1 行 `matchedTypeCount = ruleResult.matchedTypes.size`（新參數有 default = 0 向後相容）。
- 編譯驗證：`./gradlew :app:compileDebugKotlin`（Android Studio JBR 17）→ **BUILD SUCCESSFUL in 10s**。
- **刻意未動**（法務 / 產品文案歸 `雪鑰`）：
  - `showConsentDialog` 文案仍寫「命中的關鍵字 / 裝置語系」— 與新 payload 事實漂移
  - `privacy_policy.html` 未動
  - Supabase schema migration（DROP `matched_keywords` / `device_locale`，ADD `matched_keyword_count` / `matched_type_count` / `region_coarse`）未執行 — 鍵律不管 DDL
- 這三點缺口明寫進 CCB 文件 §B.3 / §C，flag 給 CCA / CXA / 玄樞 處理。
- 狀態：`READY for 雪鑰 CCA consume`；`雪鑰 CCA WORDING-NOTE` 已交付（見 `雪鑰` §1.4），本輪進行至 `衡鑑 CXA` 階段。

## 2. 今日作為 `implementation / runtime / service` 角色的交回節奏

### 2.1 交回路徑

- `S12-FIX-1`：**我 CCB 單 token 最小 diff** → `雪鑰` CCA 二審 → `玄樞` → 結案
- `S13-1`：**我 CCB Option B 純 XML** → `雪鑰` CCA consume → `衡鑑` CXA → `玄樞` → 放行 + 明示殘留
- `S13-2`：**我 CCB Option E+G 完整重寫** → `雪鑰` CCA WORDING-NOTE → `衡鑑` CXA（已過）→ 待 `玄樞`

### 2.2 今日遵守的邊界

1. **不越界改 `.kt`**：每輪只動 gate 明示的 file scope；S13-2 雖修 `FraudReportManager.kt`，但刻意不碰 dialog wording、不改 `FraudAIAnalyzer`、不改 `YukiPreferences`、不改 `SupabaseClient`
2. **不自宣 `READY / COMPLETED`**：本輪三份 CCB 交付狀態都只寫 `READY for 下一環 consume`，最終門判定留給 `玄樞`
3. **文件必須落 `docs/`**：S13-1 違規只輸出 chat 摘要被糾正後，S13-2 開工前先建骨架；並把教訓存 feedback memory 以免跨對話重犯
4. **不代寫其他角色**：`雪鑰` CCA wording / `衡鑑` CXA cross-check / `玄樞` final gate 一律不碰；即便同檔（如 `FraudReportManager.kt` 內含 `showConsentDialog`）也嚴守 runtime / wording 邊界
5. **scope 不自選**：S13-1 / S13-2 都先列候選 Option 等董事長裁示，不自行推實作路徑
6. **吸收他人 observation 時明確標註來源**：S13-1 XML 註解 port 修正明標「採納 `雪鑰 CCA §B.3` 非 blocker observation」，不佔為己功

### 2.3 新 API / 契約累積（今日三輪 CCB 總計）

| 來源 | 新增物 | 用途 |
|------|--------|------|
| S13-2 | `internal data class FraudReportPayload`（7 欄 + 6 invariant + `toJsonObject`）| fraud_reports 對 Supabase 的 client 端契約 source of truth |
| S13-2 | `FraudReportManager.toCoarseRegion(Locale): String` | locale → `tw/en/other` pure fn |
| S13-2 | `Telemetry` category `fraud_report` 5 種 code | fraud report 失敗可觀測 |
| S13-1 | `network_security_config.xml` 三段 header + 結構註解 | loopback 邊界意圖單一宣告點 |

## 3. 今日 `鍵律` 的正式結論

1. `S12-FIX-1-CCB` 以單 token 最小 diff 達成冗餘 null check 清理；`雪鑰` CCA PASS，`玄樞` final gate `COMPLETED`。
2. `S13-1-CCB` 以 Option B 純 XML 註解結構化落地 network security baseline；`玄樞` final gate `COMPLETED`；過程中犯了「只輸出 chat 摘要未落檔」的程序違規，已補檔並把 `8080 → <port>` 事實漂移一併修正。
3. `S13-2-CCB` 以 Option E+G 完整重寫 `FraudReportManager.kt`，payload 最小化為 7 欄 + rate limit + telemetry + invariant，`compileDebugKotlin` BUILD SUCCESSFUL；相關 Supabase schema migration / dialog wording / privacy_policy 缺口已明 flag 給 CCA / CXA / 玄樞，本輪自身不越界處理。
4. 本日三輪 CCB 嚴守 `implementation / runtime / service / panel / engine` 邊界：runtime / data schema / telemetry 我動；法務文字 / settings UI 文案 / 產品承諾 / DDL 全歸 `雪鑰` 或 `玄樞`。
5. 本日最重要的 process 修正是 [RULE/ROUND_GATE_COLLAB_RULE.md §4](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/ROUND_GATE_COLLAB_RULE.md)「回交必須落 `docs/` 文件」— 已存進 feedback memory，未來所有 gate round 一律**先建 docs 骨架再開工**，不再以 chat 摘要代替正式回交。

## 4. 本期已知遺留 / 下次接續點

1. `S13-2` 等 `玄樞` final gate；若 `玄樞` 指示 `雪鑰` apply dialog + policy wording 後回送，我作為 CCB 不再介入（除非 re-open 有 runtime 層面改動需求）。
2. Supabase `fraud_reports` schema migration 未跑前，release client 端 insert 會走我新寫的 `insert_failed` telemetry 全失敗；migration 時序由 `玄樞` 或 `CodeX A` 協同 `雪鑰` / `衡鑑` 決定，鍵律不主動推進 DDL。
3. `S13-1` 殘留兩點（LAN WiFi cleartext 揭露 + `RemoteKeyboardPanelView` 未加密提示）等 `玄樞` 指派承接 round；若該 round 落 runtime 類（如 `RemoteKeyboardPanelView` 狀態列加警示 icon、或 `WifiKeyboardServer` server-side TLS），屬我主責範圍，待派。
4. `S13-6 IME Service Warning Cleanup` 是我預定主責 round（CCB 實作），目前 `LOCKED`，等 `玄樞` 另行開門。

---

## 7. 今日董事長拍板紀錄

| 日期 | 指示 | 影響 |
|---|---|---|
| 2026-04-21 | `Sprint 11 / 12` 的 `READY / COMPLETED` 與跳門屬董事長特准 | `Sprint12-All-Round-Gate-Fix.md` 改寫為 override trace，不再寫成制度違規 |
| 2026-04-21 | `.kt` 要全部親自看一遍 | `玄樞` 重新逐檔複核 Sprint 11 / 12 `.kt` |
| 2026-04-21 | 終審不只 `.kt`，`Z 文` 也要親自看 | `S13-0` / `S13-1` 改採文件 + 實檔全看標準 |
| 2026-04-21 | `ZCoin / Newebpay` 併入 `Sprint 14` | `Sprint14-All-Round-Gate.md` 補 `S14-6` / `S14-7` |
| 2026-04-21 | `Sprint 13` 一道一道開，其他 round 一樣鎖 | `Sprint13-All-Round-Gate.md` 改為單門 active |
| 2026-04-21 | `S12-FIX-1-CCB` 結案自己回寫，不要等提醒 | `玄樞` 之後負責自行收尾回寫 gate / fix 文件 |
| 2026-04-21 | 完成的部分整段話刪除線 | `Sprint13-All-Round-Gate.md` / `Sprint12-All-Round-Gate-Fix.md` 已套用整段刪除線 |
| 2026-04-21 | 看目前工作流能不能用 `n8n` 完成，並先寫規劃稿 | 新增 `docs/N8N_ROUND_GATE_WORKFLOW_PLAN_2026-04-21.md` |

---

## 8. 本期已知遺留 / 下次接續點

1. `Sprint 13` 現行 active round：
   - `S13-2 Fraud Report Privacy`
2. `S13-1` 的 non-blocking 殘留仍存在：
   - `privacy_policy.html` 對 LAN WiFi keyboard cleartext 揭露不足
   - `RemoteKeyboardPanelView` 無「未加密」提示
3. `ZCoin / Newebpay` 最終接線留在 `Sprint 14`
4. `n8n` 目前只有 plan，尚未進 build spec

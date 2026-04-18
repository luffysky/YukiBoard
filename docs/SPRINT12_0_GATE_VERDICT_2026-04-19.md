# Sprint 12-0 Gate Verdict — 合併審查

日期：2026-04-19
Gate 身份：`雪鑰`（CCA 代行 `玄樞` — CodeX 額度耗盡期）
審查對象：
- `docs/SPRINT12_0_CCA_PAYMENT_KEY_CONSUME_AUDIT_2026-04-19.md`（雪鑰 CCA）
- `docs/SPRINT12_0_CCB_REMOTE_SECURITY_AUDIT_2026-04-19.md`（鍵律 CCB）
提交鏈：鍵律 → 雪鑰 → 玄樞（本檔即玄樞 gate 輸出，等董事長最終核可）

---

## A. 結論

**Gate 判定：`READY`**

- 兩份 audit 同時交付、sub-scope 嚴守各自任務 ID、無越代、無內容衝突、相互補位到位。
- 三分類覆蓋符合 Gate 檔 §3 S12-0 要求：CCA 從 consume / contract / release policy，CCB 從 runtime / threat / observability 三條軸互補。
- 識別到的 P0 漏洞（Newebpay HashKey/IV、INVITE_HMAC_SECRET 預設值、Keystore 預設值、WiFi PIN + brute-force）彼此對齊，解法方向一致（server-side 簽章 / release policy CI gate / PIN hardening）。
- S12-1 file scope 可以據此切出明確邊界。

**建議解鎖**：`S12-1`（`S12-1-CCA-NEWEBPAY-CONTRACT-PLAN` + `S12-1-CCB-NEWEBPAY-RUNTIME-PLAN` 並行）。

**等董事長裁示**：下方 §D 共 10 個開放問題 + §E 的 CH-1/2/3 執行順序候選（此二事項按 `feedback_gate_no_self_schedule.md` 規則不由 gate 自排）。

---

## B. 互補性交叉比對

### B.1 共同識別到的 P0

| 風險 | CCA 位置 | CCB 位置 | 結論 |
|---|---|---|---|
| `NEWEBPAY_HASH_KEY` / `NEWEBPAY_HASH_IV` 寫死 APK → AES 簽章可偽造 | §B K-2/K-3、§E P0-1/2、§C.1 | §B-3、§D-1（L20-22、L37-39） | ✅ 一致；解法同為 S12-1 server-side signing |
| `WifiKeyboardServer` 4-digit PIN + 無 brute-force 防線 | — | §B-1、§C-1（L44、L192-203） | ✅ CCB 獨有，CCA scope 外 |
| `INVITE_HMAC_SECRET` 預設值 `yukiboard_dev_2026` 可能編入 release | §B K-4、§E P0-3、§F.2 | §D-4（列位置但未標預設值危機） | ✅ CCA 補足；CCB 識別位置，CCA 補足預設值具體威脅 |
| Keystore 密碼預設值 `yukiboard2026` | §B Keystore、§E P0-4、§F.2 | — | ✅ CCA 獨有，CCB scope 外 |

### B.2 runtime 側 CCB 獨有發現（CCA 未盤到）

| 項 | CCB 位置 | 評估 |
|---|---|---|
| `ZCoinManager` EncryptedSharedPreferences fallback 到 `MODE_PRIVATE` 時明文寫入 + `Log.w` 印 stack | §B-6、§D-3（L30-33、L138） | 合理補位；屬 runtime/secret handling，CCB 本分。併入 S12-2 處理。 |
| `NewebpayClient.aesEncrypt` 失敗無全域 catch，呼叫端 UI 會空畫面 | §E-2 | 屬 runtime + observability 交界；S12-5 落地點。 |
| `BluetoothHidKeyboard` 連線 Log 帶 host MAC / name | §C-2（L83-92） | 屬 remote/telemetry；S12-4 最小 cut 建議 sanitize。 |
| 全專案 0 個 Crashlytics / Sentry hook | §E-1 grep 結果 | Sprint 12 最大盲點；與 CCA §D.3 觀測接點呼應，一起落 S12-5。 |

### B.3 CCA 獨有發現（CCB 未盤到）

| 項 | CCA 位置 | 評估 |
|---|---|---|
| R-9 旁路二處（`ImeServiceInput:255` + `SpeechSettingsSection:266`）會在 release 編入 key 時繞過 AIProviderManager | §C.3 表 / §E P2-8 / §G-3 | 合理，屬 consume / release policy 交界，S12-3 順手清。 |
| `ZCoinWalletActivity` Custom Tab 跳板（payment 本地已有 escape hatch）| §C.3 表 / §D.1 | CCB file scope 未列此檔；併入 S12-1 scope 觀察（不改 `.kt`）。 |
| AI keys 的 BYO 契約（用戶輸入）vs release policy 置空 vs Insight proxy 的三段設計 | §C.2 表 / §D.2 | CCA 本分；CCB 只列風險不處理契約，正確。 |
| Giphy / OpenWeather 無 BYO fallback（release 置空 = 功能完全失效） | §C.3 表 / §E P1-6 | CCA 本分；S12-3 需決策：BYO vs Insight proxy。 |

### B.4 文本衝突 check

| 項 | CCA 主張 | CCB 主張 | 衝突？ |
|---|---|---|---|
| Newebpay server-side 簽章方向 | S12-1 contract + client 保留 orderId/WebView | §D-1「APK 只保留 orderId 與顯示流程」 | ❌ 無衝突，同向 |
| Supabase anon key | P3，靠 RLS，Sprint 13 審計 | §D-4「anon key 本即 client-side 但需 RLS」 | ❌ 無衝突，同向 |
| HMAC secret 後端轉型時機 | §G-1 建議 S13（S12 只做 release policy） | §D-4 只列風險未建議時機 | ❌ 無衝突，CCA 主張更明確 |
| S12-5 先建 reporter scaffold vs 最後建 | 無明確排序 | §H CH-2 建議先建觀測 | ⚠ 不拍板；列董事長候選 |
| S12-4 是否引入 session token | 無討論 | §I-1 開放問題 | ⚠ 列董事長候選 |

**結論**：兩份 audit **無實質衝突**。差異僅在 S12 內部執行順序候選（CH-1/2/3）與 session token / Crashlytics 的 sprint boundary 問題，這些本就應該由董事長（或玄樞恢復後）裁示，不由 gate 自排。

---

## C. S12-1 File Scope 合併建議

依兩份 audit 合併出 S12-1 的 **閱讀 file scope**（S12-1 仍是 contract/plan，不改 `.kt`）：

```
currency/NewebpayClient.kt                ← CCA+CCB 共同標
currency/ZCoinManager.kt                  ← CCA+CCB 共同標（CCB 補 fallback 風險）
currency/ZCoinApi.kt                      ← CCA+CCB 共同標
ui/currency/ZCoinWalletActivity.kt        ← CCA 獨有（既有 Custom Tab 跳板觀察）
app/build.gradle (L62-65 NEWEBPAY_*)      ← CCA+CCB 共同標
```

**S12-1 禁區**（依兩份 audit 合併）：
- ❌ 不改 `.kt`，僅 contract / runtime plan
- ❌ 不盤 Insight 後端實作細節（另一專案、另一 sprint）
- ❌ 不擴成 billing / subscription / 收據管理（scope creep）
- ❌ 不碰 Supabase schema / migrations（CCB §G-7）
- ❌ 不改 `YukiBoardIMEService.kt` 或 `SettingsActivity.kt` 本體（CLAUDE.md 常駐禁令）

---

## D. 開放問題合併（10 項，等董事長裁示）

來自 CCA §G 5 問 + CCB §I 5 問，合併並去重：

### D.1 Payment / server-side 相關

1. **Newebpay server 端由誰做？Insight 團隊還是 YukiBoard 團隊？contract 接口由誰定？**
   （CCA G-2 + CCB I-2，重疊）
   - CCA 建議：Insight 已有 `/zcoin/*` 前綴，API 落 Insight，YukiBoard 僅提案 contract 草案
   - CCB：若 SnowRealm 後端未準備，S12-1 只能做 contract 紙稿

2. **Release build 是否立即從 build.gradle 剝除 payment key 預設值？**
   （CCB I-4）
   - 若是：開發者本地 build 必須有 `.env` 或 `local.properties`，否則 `isConfigured = false`、儲值 UI 需 fallback
   - 若否：S12-1 只做 contract，實際剝除進 S12-2

### D.2 Key / release policy 相關

3. **K-4 `INVITE_HMAC_SECRET` 後端轉型**：S12 內做 Insight 後端簽發 API，還是 S12-3 只做 release policy + CI gate + Sprint 13 再轉？
   （CCA G-1）
   - CCA 建議：S12 先做 policy，後端轉型留 S13

4. **R-9 旁路是否 S12-3 順手清？**
   （CCA G-3）
   - CCA 建議：若 release 強制 OPENAI 置空，`takeIf { isNotBlank }` 永遠走 else，旁路變 dead code。S12-3 順手 remove。

5. **Keystore 預設值 CI gate 是否納 S12-3？**
   （CCA G-5）
   - CCA 建議：是，低成本高價值。

### D.3 Remote / PIN 相關

6. **S12-4 PIN hardening 同時引入 session token？**
   （CCB I-1）
   - 引入：改動半徑擴大到 `RemoteKeyboardPanelView` + HTML 控制頁
   - 不引入：PIN 再強仍有 URL sniff 問題

### D.4 Observability 相關

7. **S12-5 是否在 Sprint 12 內接 Firebase Crashlytics？**
   （CCB I-3）
   - 若是：Gradle plugin + `google-services.json` + 權限宣告入 sprint
   - 若否：先做 `AndroidLogReporter` scaffold，Crashlytics 留 S13

8. **是否允許 CCB 代起草 S12-5 `ErrorReporter` 介面（只做介面、不接實作）？**
   （CCB I-5）
   - 若允許：S12-1 CCB runtime plan 可直接引用
   - 若否：S12-5 scaffold 時才立介面

### D.5 其他

9. **Supabase anon key RLS 審計**
   （CCA G-4）
   - CCA 建議：不在 S12 scope，列 Sprint 13 候選

10. **consent dashboard（天氣定位、Giphy 查詢詞、打字內容）**
    （CCA §C.3 + CCA §E P3-12）
    - CCA 建議：Sprint 13+；S12-3 / S12-5 僅補各別 consent wording

---

## E. 執行順序候選（CH-1 / CH-2 / CH-3）— 不由 gate 拍板

CCB §H 已提三套候選，鍵律自陳信心分布 CH-2 > CH-1 > CH-3。雪鑰 (gate) 依 `feedback_gate_no_self_schedule.md` 不自排 panel / round 順序，原封轉列給董事長拍板：

| 候選 | 執行序 | 主要理由 | 主要風險 |
|---|---|---|---|
| **CH-1** 先封 payment 洞 | S12-1 → S12-2 → S12-4 → S12-3 → S12-5 | Newebpay hash key 是反編譯即失錢的直接漏洞，應先砍 | S12-1/2 改造時沒有 reporter，regression 看不見 |
| **CH-2** 先建觀測，再動金流 | S12-5 scaffold → S12-1 → S12-2 → S12-4 → S12-3 | reporter 先有，後續重改才看得到 regression | S12-5 若 S12-1 contract 未定就開，reporter 落地點可能返工 |
| **CH-3** 雙線並行 | 線 A：S12-1→S12-2（雪鑰主導） / 線 B：S12-4→S12-5 scaffold（鍵律主導），並行收斂 S12-3 | payment 與 remote PIN 可獨立開，節省時序 | reporter 契約在 payment refactor 前未定型；需玄樞特別 gate |

雪鑰 (gate) 補充觀察：
- CH-2 的 S12-5 scaffold 在 S12-1 contract 之前開確實會有「reporter 落地點返工」風險；但若 scaffold 僅落 `ErrorReporter` interface + `AndroidLogReporter` 一個實作（不接點），返工風險近零。
- CH-3 並行對 CodeX 額度耗盡期的單對話協作壓力較大，雙線切換時容易出現 R-9 式跨子題越代（cf. `feedback_role_boundary.md`）。
- 若選 CH-1，需在 S12-1 / S12-2 完成時補一次手動驗證節點，補償 reporter 缺席。

**不拍板**，等董事長選 CH-1 / CH-2 / CH-3 或其他。

---

## F. Gate 輸出動作

`READY` 判定下，本 gate 要做的副作用：

1. 更新 `Sprint12-All-Round-Gate.md`：
   - S12-0 狀態 `ACTIVE` → `COMPLETED`
   - §6 玄樞預設判定補 gate 結論摘要
   - S12-1 狀態 `LOCKED` → `ACTIVE`（等董事長核可 CH 順序後才真正啟動並行）
2. 本輪內不改 `.kt`（已確認 CCA / CCB 兩份 audit 都是純文件交付）。
3. 10 個開放問題 + CH-1/2/3 候選集中於本檔 §D/§E，董事長裁示後再進 S12-1 具體分工。

---

## G. 交付者自白

- 本 gate 由 `雪鑰`（CCA）代行 `玄樞`；依 `feedback_role_boundary.md` 規則，gate 只做 gate，不做 implementation，更不做 CCB 任務的越代。
- 審查對象是兩份 audit 文件，不涉及 `.kt` 修改。
- 董事長若採納本 gate 結論，S12-1 並行解鎖；若對三分類邊界 / 10 問任一項有調整指示，本 gate 可補稿後再次送交。
- 本次審查未涉及資料庫 schema / Supabase RPC 實作（Sprint 11 closeout 已含 DB 面審查，本輪 scope 為 audit 文件審，不需再審 DB）。

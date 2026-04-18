# Sprint 12-4 Gate Verdict — Remote Keyboard PIN Hardening

日期：2026-04-19
Gate 身份：`雪鑰`（CCA 代行 `玄樞`）
審查對象：
- `docs/SPRINT12_4_CCB_REMOTE_KEYBOARD_PIN_HARDENING_2026-04-19.md`（二次修版；鍵律 CCB）
- `docs/SPRINT12_4_CCA_REMOTE_KEYBOARD_PIN_CONSUME_REVIEW_2026-04-19.md`（雪鑰 CCA）
CH 順序：CH-1（修訂後）~~S12-1~~ → ~~S12-2 DEFERRED~~ → **S12-4** → S12-3 → S12-5

---

## A. 結論

**Gate 判定：`READY`**

1. 四條 runtime hardening 建議（session token / panel PIN / telemetry interface / BT BuildConfig）全部落地，編譯通過。
2. CCA review §D BLOCKING 兩項 + STRONG 兩項全部二次修完成，實際 diff 核對無誤（見 §B）。
3. CCA §D.3 OPTIONAL 兩項（HTML initial class / LISTENING 註解）CCB 選擇不處理，理由在 CCB 文件 §H.3 給出（`connecting` 橙色符合「等待驗證」直覺；`LISTENING` 註解僅開發者可見）—— CCA final pass 評估合理，不阻擋。
4. 本輪 scope 嚴守：無越做到 TLS / Crashlytics / 帳號層；telemetry 保留 interface 給 S12-5 替換。

**S12-4 結案**。S12-3 / S12-5 維持 `LOCKED`，由董事長指示開啟下一門。

---

## B. CCA Final Pass 對二次修 diff 的核對

| 項 | CCA 要求 | CCB 實際落地 | 判定 |
|---|---|---|---|
| D.1-1 | Panel auth wording 採方案 A「PIN + 連線金鑰」 | `RemoteKeyboardPanelView.kt:157` `"🔒 驗證: PIN + 連線金鑰"`；其他三個 AuthLevel 字串保留 | ✅ PASS |
| D.1-2 | HTML JS 區分「連上後斷線」vs「沒連上就斷」，後者停止重連 + 回 PIN 輸入頁 + 新文案 | `WifiKeyboardServer.kt:524/530/534-543/550-556/558-567/569-571`：全域 `wasConnected` flag + `backToPinInput(message)` helper + `onclose` 分支 + `onerror` 讓渡給 `onclose` 處理 | ✅ PASS |
| D.2-3 | `stop()` 清 `connectionPin` + `attemptTrackers` | `WifiKeyboardServer.kt:157-158` 已加 `connectionPin = "" / attemptTrackers.clear()` | ✅ PASS |
| D.2-4 | Panel PIN row「PIN:」→「連線 PIN 碼:」 | `RemoteKeyboardPanelView.kt:153` `"連線 PIN 碼: $wifiPin"` | ✅ PASS |
| D.3-5 | HTML initial class 改 `pending`（OPTIONAL） | 不處理；理由 CCB §H.3 | ⚪ ACCEPTED |
| D.3-6 | `LISTENING` 註解補充（OPTIONAL） | 不處理；理由 CCB §H.3 | ⚪ ACCEPTED |

**onclose 邏輯再檢查（CCA 額外）**：
- 第一次輸錯 PIN → server 403 close → `onclose`（`wasConnected=false`）→ `backToPinInput("無法連線，請確認 PIN 碼正確...")` → user 看到文案重輸 ✅
- 被 lockout 後再連 → server 429 close → 同上分支 → 同文案包含「等 5 分鐘」指引 ✅
- 連上後真斷線（手機 sleep / IME restart）→ `onclose`（`wasConnected=true`）→ 5 秒 auto reconnect ✅
- `onerror` 空 body 註解明示讓渡給 `onclose`，避免提前 setStatus 覆蓋 ✅

邏輯閉合，無 BLOCKING 遺留。

---

## C. Follow-up 觀察（非阻擋，存檔）

### C.1 Edge case — Server 重啟後 PIN 變，browser 不知情

情境：
1. User 在 browser 連上 YukiBoard（`wasConnected=true`）
2. 手機端 IME 重啟 / 停止再啟動 → `start()` 重 `generatePin()`，PIN 值變更
3. Browser 的 5 秒 auto reconnect 帶舊 PIN → server 403 → close → `onclose`(`wasConnected=true`) → 繼續 5 秒重連
4. 重複 5 次內撞上 lockout → server 429 → 繼續 5 秒重連（仍 `wasConnected=true`）

**影響**：browser 端持續顯示「已斷線 / 5 秒後重連」，從不回 PIN 輸入頁；user 可能不知道要手動關瀏覽器重輸 PIN。

**風險等級**：低（需要手動觸發 IME restart；不構成 exploit；使用者可自行 refresh 頁面）

**建議 follow-up（可延後）**：
- 方案 1：在 `wasConnected=true` 的 reconnect 也加重試上限（例如 3 次失敗後回 PIN 輸入頁 + 「伺服器可能已重啟，請重新輸入 PIN」）
- 方案 2：server 在 PIN 重生時發特殊 close frame reason（但目前 server socket 直接 close，沒走 WebSocket close frame）

**處理時機**：不開新 round；若使用者實際反映再做，或與 Sprint 13+ WebSocket close frame 改造一併處理。

### C.2 Panel 三模式截圖驗證（CCB §E.1.b / CCA §B.1.b）

CCB 二次修時未提供三模式截圖。由於 final pass 是文件審，CCA 無法自驗。**建議 CCB 在下次 push / PR 時順手補三張截圖**（直立 / landscape / 懸浮），或列為個人確認項即可，**不阻擋 gate**。

### C.3 Panel 主題低對比下 PIN 字可讀性（CCA §B.1.c）

CCA 原建議為 MINOR_OPTIONAL。CCB 未處理，符合 CCA 「等使用者反映再處理」的原結論。不阻擋。

---

## D. Gate 輸出動作

`READY` 判定下：

1. 更新 `Sprint12-All-Round-Gate.md`：
   - S12-4 狀態 `ACTIVE` → `COMPLETED`（strikethrough + 指向本 gate verdict + 兩份 CCB / CCA 文件）
   - §6 玄樞預設判定補 S12-4 結論
2. S12-3 / S12-5 維持 `LOCKED`，由董事長拍板解鎖哪一門。
3. 本輪對外 user-visible 改動：
   - 面板新增連線 PIN 碼 18sp monospace 顯示（LISTENING 期間）
   - 面板新增驗證等級標示（🔒 驗證: PIN + 連線金鑰 / 系統配對）
   - WiFi 控制網頁錯誤提示改善，避免誤導
   - BT release log 不再含 device name
4. 無使用者需要做的動作 — 升級後自動生效。

---

## E. Sprint 12 進度快照（更新）

```
S12-0 COMPLETED  Payment / Key Audit
S12-1 COMPLETED  Newebpay Server-side Signing Plan（修訂：ZCoin 不可刪）
S12-2 DEFERRED   Android Client Payment Refactor（Z 幣後做）
S12-4 COMPLETED  Remote Keyboard PIN Hardening  ← 本輪結案
S12-3 LOCKED     API Key Release Policy（AI / weather / gif）
S12-5 LOCKED     Observability Hook
```

**下一候選 round（待董事長拍板）**：
- S12-3 API Key Release Policy（CH-1 原排第 4）
- S12-5 Observability Hook（CH-1 原排第 5；telemetry interface 已由 S12-4 預留）

---

## F. 交付者自白

- 本 gate 由 `雪鑰`（CCA）代行 `玄樞`；gate 只做 gate，不實作；不再次 run 編譯（CCB 已報 BUILD SUCCESSFUL）。
- Final pass 以 diff 核對為主，不重寫任何 .kt。
- CCB 二次修採納 CCA 4 項（D.1+D.2 全部），CCA 3 項 OPTIONAL（D.3+C.2+C.3）接受 CCB 不處理決定。
- §C Follow-up 三項存檔，不開新 round；後續若實際觸發再處理。
- 不自排下一 round；S12-3 / S12-5 解鎖等董事長指示。

# Sprint 13 — S13-1 CCA Network Security Consume Implementation

日期: 2026-04-21  
執行身份: `雪鑰` (Claude CCA / 設定與帳號流資深工程師)  
對應 CCB 交付: `S13-1-CCB-NETWORK-SECURITY-RUNTIME-IMPLEMENTATION`（鍵律，2026-04-21）  
範圍: consume-side 契約、文案、使用者可見風險、release contract 對齊  
狀態: `DRAFT` — 等董事長確認後提交 `衡鑑` CXA

---

## A. 交付摘要

CCB 採 Option B 最小文件化，僅改 `app/src/main/res/xml/network_security_config.xml`，0 個 .kt 變更。CCA 側 consume 本輪同樣採最小路徑：

1. **Affirm release contract**：確認 CCB 基線符合 `privacy_policy.html §4` 目前文案的核心主張「outbound 網路通訊強制 HTTPS」。
2. **Flag 文案對齊缺口**：`privacy_policy.html §2.1 / §4` 與 `RemoteKeyboardPanelView` 使用者可見文字，存在對 LAN WiFi 鍵盤 cleartext 路徑的揭露不足 — 本輪僅列缺口，不在 S13-1 內改文案。
2 或 3 條 wording 變更建議路由 `S13-5 clipboard / userdict sync contract` 或未來新設 round。
3. **Prohibited commitments**：列出本輪基線下不得對使用者做出的 10 條承諾。

本交付**不修改任何 .kt / manifest / xml**；僅為 wording 與 contract 層交付文件。

---

## B. CCB 基線確認

### B.1 檔案對照

| 項目 | 實檔 | 狀態 |
|------|------|------|
| NetworkSecurityConfig 實檔 | `app/src/main/res/xml/network_security_config.xml` | 已讀，結構符合 CCB 描述 |
| Manifest 綁定 | `AndroidManifest.xml:41-42`：`usesCleartextTraffic="false"` + `networkSecurityConfig="@xml/network_security_config"` | 已對上 |
| OkHttp 共用 builder | `app/src/main/java/com/snowrealm/yukiboard/app/net/AppHttpClient.kt` | 未觸動（正確，CCB 未改 .kt） |
| 實際 cleartext 發起點 | `WifiKeyboardServer.kt` ServerSocket bind | 非 OkHttp client，不受 NetworkSecurityConfig 管 — CCB 邊界說明正確 |

### B.2 三段結構等價驗證

| Section | CCB 描述 | CCA 核對結果 |
|---------|---------|-------------|
| 1. base-config | `cleartextTrafficPermitted="false"` + system CA | ✅ 等價，release 行為：所有 OkHttp 外發 cleartext 請求會被 `IOException(cleartext not permitted)` 擋下 |
| 2. debug-overrides | user CA 僅 debug，未放寬 cleartext | ✅ 等價，避免「debug 通 / release 爆」反模式 — 與 `fraudReportConsent` 的「先實機再承諾」精神一致 |
| 3. domain-config | `localhost` + `127.0.0.1`，`includeSubdomains="false"` | ✅ 等價，僅允許 loopback 字面主機，阻擋 DNS rebinding 變體 |

### B.3 觀察：CCB XML comment 的小幅事實漂移（非 blocking）

`network_security_config.xml:30` 註解寫 `WifiKeyboardServer binds http://<LAN-IP>:8080`，但實檔 `WifiKeyboardServer.kt:51` `DEFAULT_PORT = 8765`。註解屬於文件描述，與 runtime 行為無關；建議：

- **O1**：CCB 可在下一次微調或 S13-5 同題目順手改 `8080` → `8765`，或改為不寫明 port（`http://<LAN-IP>:<port>`）以避免端口未來調整造成註解再度失準。**本輪 CCA 不作為 blocker**。

---

## C. 使用者可見風險 / 文案對齊缺口

### C.1 `privacy_policy.html §4` 四點對齊

目前文字（2026-04-11 last update）：

```
本機資料使用 Android SharedPreferences 儲存，敏感資料使用 EncryptedSharedPreferences
網路通訊強制使用 HTTPS（TLS 1.2+）
API Key 由使用者自行管理。若使用者主動啟用雲端同步，我們會以加密形式儲存於伺服器；
  伺服器端無法直接得知原始 API Key，僅在使用者於自己的裝置還原時才會解密使用。
藍牙 HID 鍵盤通訊使用標準 BLE 加密
```

| # | 條文 | 對齊狀態 | 問題 | 建議 |
|---|------|---------|------|------|
| §4.1 | SharedPreferences + EncryptedSharedPreferences | ✅ 對齊 | — | — |
| §4.2 | 網路通訊強制使用 HTTPS（TLS 1.2+） | ⚠️ **P1 半對齊** | 僅對 outbound OkHttp 為真；loopback（本機 AI 推論） + LAN WiFi 鍵盤（ServerSocket）屬 cleartext 例外 | 改成「**向外部伺服器**的網路通訊強制使用 HTTPS；本機 loopback 與使用者自行啟動的區域網遠端鍵盤屬例外」 |
| §4.3 | API Key 雲端同步加密 | ✅ S13-0 終審已對齊 | — | — |
| §4.4 | 藍牙 HID 使用 BLE 加密 | ✅ 對齊 | — | — |
| §4 新增建議 | — | — | 缺 WiFi 區網鍵盤風險條 | 新增：「啟用『WiFi 遠端鍵盤』時，您的輸入會以**未加密**方式在區域網內傳輸，僅以 PIN 限制連線；請僅在可信網路使用」 |

### C.2 `privacy_policy.html §2.1` 打字內容僅本機宣告的例外

目前文字 §2.1：

```
打字內容：注音組字、候選字選擇僅在裝置本機處理，不會傳送至任何伺服器
```

- ⚠️ **P1 半對齊**：啟用 WiFi 遠端鍵盤後，打字按鍵 / 合成文字會**透過 WebSocket 以 cleartext 傳到區網內連線的瀏覽器**（`WifiKeyboardServer.kt` 的 `remoteKeyListener` / `remoteTextListener` 訊息流）。
- 建議修改（最小改動）：「打字內容：注音組字、候選字選擇僅在裝置本機處理，**不會傳送至任何外部伺服器**；僅在使用者主動啟用 WiFi 遠端鍵盤時，才會經由區域網以未加密方式傳至已配對的瀏覽器」。

### C.3 `RemoteKeyboardPanelView` 使用者可見揭露

- 現狀（`RemoteKeyboardPanelView.kt` line 146-153）：LISTENING 狀態顯示 `URL: http://<IP>:<port>` 與 `連線 PIN 碼: <pin>`。
- 缺口：**沒有任何** UI 文字向使用者揭露「此連線未加密」。使用者可能誤以為 PIN = 加密。
- ⚠️ **P2 使用者認知缺口**
- 建議（不在本輪實作）：LISTENING 狀態額外顯示一行小字 `⚠ 未加密，僅限可信網路`，或在 Toolbar 啟動按鈕前彈確認對話框首次提示。

### C.4 S13-0 盤點之 `clipboardSyncEnabled` 懸空偏好（跨 round 引用）

S13-0 已盤出 `YukiPreferences.kt:155` 有 `clipboardSyncEnabled` 但無實作；本輪 S13-1 不處理 sync 契約，**維持 S13-5 負責**。CCA 本輪僅確認：clipboard cleartext WebSocket 路徑不在 S13-1 的 NetworkSecurityConfig 責任邊界內，屬 S13-5。

---

## D. Release Contract（CCA 端簽署之基線）

CCA 本輪代表「consume 側」正式承接以下基線作為 release contract 的一部分：

1. **Release build 必須 reject outbound cleartext**：CCB 的 `base-config cleartextTrafficPermitted="false"` 為不可降級之 floor。任何新 API、新 provider、新 third-party SDK，若要求 cleartext，一律視為破壞 release contract，須先走新 round。
2. **User CA 僅 debug 可信**：任何要求 release build 接受使用者安裝憑證的需求（例如企業 MDM），須走新 round + 法務 review，不得由 CCA 或 CCB 單邊決定。
3. **Cleartext allowlist 維持 loopback only**：`localhost` / `127.0.0.1` 為唯一允許 cleartext 的 domain。新增任何 domain 至允清單（例如中國內網 mirror、遊戲內網伺服器）均屬破壞 release contract，須走新 round。
4. **LAN WiFi HTTP server 的 cleartext 不在 NetworkSecurityConfig 管轄範圍**：這是 Android 平台行為（ServerSocket 不過 NSC）。CCA 確認此邊界由 S13-5（sync contract） / 未來 round 處理；S13-1 不承諾亦不隱瞞。

---

## E. 禁止承諾清單（Prohibited Commitments）

以下承諾**不得**出現在任何使用者可見文字（隱私政策、服務條款、設定頁文案、商店描述、Release Note、行銷素材）：

1. ❌「YukiBoard 所有資料均加密傳輸」— LAN WiFi 鍵盤路徑是 cleartext。
2. ❌「端對端加密」（End-to-End Encryption, E2EE）— WiFi 遠端鍵盤無 E2EE；悄悄話（SecretMessageManager）是**內容層**對稱加密，不是 transport layer E2EE，兩者不得混用名詞。
3. ❌「軍事級加密」/「銀行級加密」之類誇大用語 — 法務已在 `legal.md` 標註為高風險措辭。
4. ❌「完全不會洩漏您的輸入」— 啟用 WiFi 遠端鍵盤時，同網內攻擊者可抓包 + 嘗試 PIN 爆破（雖有 lockout，但不等於不可能）。
5. ❌「即使連接公共 Wi-Fi 也安全」— 與 §C.1 修正方向相反。
6. ❌「所有 AI 推論皆本機」— 本機 loopback 到 Faster-Whisper 屬特例，仍存在 on-device 之 IPC；且 OpenAI/Anthropic/Gemini/Groq 為遠端。
7. ❌「符合 HIPAA / PCI-DSS / FedRAMP / SOC 2」 — 未通過正式稽核，屬 legal.md 30 項高風險之一，S13-1 不解鎖此類承諾。
8. ❌「使用者 CA / 自簽憑證 release 也可用」— 明確與 `debug-overrides` only 基線衝突。
9. ❌「Debug build 與 Release build 行為一致」— 兩者故意不一致（user CA 差異），不得以「一致」宣傳欺瞞。
10. ❌「未加密通訊已禁用」— outbound 是禁用，但 LAN HTTP server 未禁；不得省略後半句。

---

## F. Consume 影響面

### F.1 設定頁 / UI 影響

- 本輪 0 實作，僅記錄以下 UI 節點為「未來 round 必須補揭露」：
  - `RemoteKeyboardPanelView` LISTENING 狀態文字（C.3）
  - `ToolbarSettingsSection` 遠端鍵盤開關旁的說明（目前無；建議加「僅限可信網路」註腳）
  - `InputBehaviorSection` WiFi 相關條目（已確認無新增需求）

### F.2 文件 / 文案影響

- `privacy_policy.html §2.1` / §4：建議本輪直接改，**或**延到 S13-5 sync contract 一起改（建議延，因 §2.1 的 clipboard / userdict 剪貼簿路徑也與 S13-5 強相關，一次改不割裂）。
- `terms_of_service.html`（若存在）：未查，列為 B 級後續核查項。
- `legal.md`（SnowRealm 法律審查報告）：不需異動，本輪基線符合其「建議加強項」中「明確揭露 cleartext 路徑」方向。

### F.3 release contract 對齊

- 已透過 §D「Release Contract」正式承接基線。
- `玄樞` 未來 gate 其他 round 時，可以 §E 禁止承諾清單作 checklist 使用。

---

## G. Out-of-scope（本輪明確不做）

1. **不改任何 .kt / manifest / xml 以外檔案**（CCA 端）— 本輪就是最小 consume。
2. **不新增 certificate pinning** — 屬於下一層 security roadmap；S13-1 僅定 cleartext baseline。
3. **不加 LAN HTTP server-side TLS** — 屬於 S13-5 sync contract 或未來專題 round。
4. **不改 `WifiKeyboardServer` 協議** — 不碰 runtime，不改 PIN 長度、lockout 窗口。
5. **不處理 Faster-Whisper loopback 授信模型** — 已明確列為 CCB 邊界；不在 S13-1 解鎖。
6. **不增修 privacy_policy.html / terms 文字**（建議延至 S13-5 同批改，避免文案改兩次）— 需 `玄樞` 判定。
7. **不觸碰 S13-6 IME Service Warning Cleanup** — 屬不同 micro-round，按 Sprint13-All-Round-Gate.md 規則不得插隊。

---

## H. 建議路由（候選清單，順序由 `玄樞` 決定）

1. **本輪即結案**：`衡鑑` 就 CCB + CCA 兩份做 `S13-1-CXA-NETWORK-SECURITY-TECHNICAL-REVIEW`，若無 blocker 由 `玄樞` 判 READY，S13-1 結束；使用者可見文案缺口（§C）併入 S13-5 處理。
2. **最小文案補丁同輪落地**：若 `玄樞` 希望 §C.1 / §C.2 文案 S13-1 就處理，CCA 可新增第二次交付修改 `privacy_policy.html §2.1` + §4 兩句 — **但需 `玄樞` 先明示本輪允許擴至 .html 檔**，否則維持第 1 案。
3. **RemoteKeyboardPanelView UI 揭露**：路由至 S13-5（與 clipboard sync 一起）或未來新設「遠端鍵盤安全揭露」micro-round；CCA 不自行排期。

---

## I. 已知風險 / 邊界

| 編號 | 描述 | 嚴重 | 本輪處理 |
|------|------|------|---------|
| R1 | `privacy_policy.html §4.2` 主張「網路通訊強制 HTTPS」嚴格說只對 outbound 為真 | P1 | Flag only，留 §H.2 或 S13-5 |
| R2 | `§2.1` 打字內容「僅本機」在啟用 WiFi 遠端鍵盤時技術上為偽 | P1 | Flag only |
| R3 | `RemoteKeyboardPanelView` 無 cleartext 揭露 | P2 | Flag only，排後續 round |
| R4 | CCB XML 註解端口漂移（8080 vs 8765） | P3 | 非 blocker，O1 |
| R5 | debug-overrides 未顯式寫 `cleartextTrafficPermitted="false"` 仍沿用 base | P3 | 等價；不改 |

---

## J. 驗證方法（供 `衡鑑` CXA 使用）

1. **Outbound cleartext block 驗證**：release build 下，對任意非 loopback domain 發 HTTP（非 HTTPS）請求 — 預期拋 `java.io.IOException: Cleartext HTTP traffic to <host> not permitted`。
2. **Loopback cleartext 放行驗證**：release build 下，對 `http://127.0.0.1:*` 發請求 — 預期成功（loopback 在允清單）。
3. **User CA release reject 驗證**：release build 下，對使用者安裝 CA 簽發的 HTTPS 發請求 — 預期拋 `SSLHandshakeException(... trust anchor ...)`。
4. **User CA debug accept 驗證**：debug build 下同動作 — 預期成功。
5. **Manifest ↔ XML 對齊驗證**：`aapt dump badging`（或 Android Studio Merged Manifest）確認 `usesCleartextTraffic="false"` 與 `networkSecurityConfig="@xml/network_security_config"` 同時存在。
6. **WifiKeyboardServer 不受 NSC 管轄驗證**：在 release build 下啟動 WiFi 遠端鍵盤，LAN 另一裝置透過 `http://<LAN-IP>:8765` 能開啟控制頁 — 預期成功（證明 ServerSocket 不走 NSC）。

`衡鑑` 可選做 1~3（release 行為核心）；4~6 為加分項。

---

## K. 雪鑰 CCA 標準交付格式

- **問題分類**：P1（文案／policy 不對齊）、P2（UI 揭露不足）、P3（註解／次要事實漂移）
- **狀態來源**：`network_security_config.xml`、`AndroidManifest.xml`、`privacy_policy.html`、`WifiKeyboardServer.kt`、`AppHttpClient.kt`、`RemoteKeyboardPanelView.kt`
- **最小契約**：見 §D Release Contract 四條
- **建議文案修正**：§C.1 / §C.2 共 2 條（本輪 flag 不實改）
- **禁止承諾**：§E 10 條
- **風險清單**：§I R1~R5
- **驗證方法**：§J 6 項
- **Out-of-scope**：§G 7 項

---

## L. 玄樞 審核需求 / 結論

- 本交付為 `READY for 衡鑑 CXA consume`（CCA 端）。
- 尚需 `玄樞` gate 判定 S13-1 整體 round 是否 `READY` 進 S13-2，須等 CCB + CCA + CXA 三份收齊。
- CCA 側不自行宣告 round 完成，等 `衡鑑` 回饋。
- 若 `玄樞` 要求本輪即處理 §C.1 / §C.2 privacy_policy 文案，CCA 可立即加第二次交付；需 `玄樞` 明示後才動。

---

交付狀態：`S13-1-CCA` 已落地。等董事長確認後送 `衡鑑` 進 CXA。

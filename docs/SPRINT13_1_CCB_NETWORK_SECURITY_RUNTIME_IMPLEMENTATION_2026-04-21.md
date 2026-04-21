# Sprint 13 — S13-1 CCB Network Security Runtime Implementation

日期: 2026-04-21
執行身份: `鍵律` (Claude CCB / 核心輸入與 IME 行為工程師)
對應 Gate: `Sprint13-All-Round-Gate.md §3 S13-1 Network Security Baseline`
任務 ID: `S13-1-CCB-NETWORK-SECURITY-RUNTIME-IMPLEMENTATION`
範圍決定: 董事長核可之 **Option B** — 最小文件化 + 拆 `domain-config` 結構註解
狀態: `READY` for 雪鑰 CCA consume（CCA 已先行完成 `SPRINT13_1_CCA_NETWORK_SECURITY_CONSUME_IMPLEMENTATION_2026-04-21.md` `DRAFT`）

---

## 補記：為何此文件在 CCA 之後才補寫

本交付原本於對話中以 A-E 交接摘要形式輸出，未立即落成 `docs/` 文件；董事長以 `你 S13-1-CCB-NETWORK-SECURITY-RUNTIME-IMPLEMENTATION 放哪` 糾正後補齊。補寫時同步採納 CCA §B.3 對 XML 註解 `port 8080` 事實漂移的非 blocker 建議，改為 `<port>（可配置，預設 8765）`。

---

## A. 變更摘要

**唯一檔案**：`app/src/main/res/xml/network_security_config.xml`

純 XML 註解與結構化文件化改動，0 `.kt` 變更，0 manifest 變更，0 OkHttp client 變更。

### A.1 具體改動

1. **頂部加入 file-level header comment**
   - Owner 標註：`鍵律 (Claude CCB) / 雪鑰 (Claude CCA) consume`
   - Sprint 歸屬：`S13-1`
   - 三段式結構索引（base / debug-overrides / domain-config）

2. **Section 1 `base-config`**（零 XML 語意變更）
   - 保留 `cleartextTrafficPermitted="false"` + `certificates src="system"`
   - 註解升級為 "Production baseline: reject cleartext, trust system CA only"

3. **Section 2 `debug-overrides`**（零 XML 語意變更）
   - 保留 `certificates src="user"`
   - 註解明示：僅 debug 構型生效、使用情境為 mitmproxy / Charles、刻意不放寬 cleartext

4. **Section 3 `domain-config`**（零 XML 語意變更）
   - 保留 `cleartextTrafficPermitted="true"` + `localhost` + `127.0.0.1`（`includeSubdomains="false"`）
   - 註解補上三個關鍵事實：
     - `WifiKeyboardServer` 綁定 `http://<LAN-IP>:<port>`（可配置，預設 8765） — **不**在此 allowlist（LAN IP 不經 domain-config）
     - loopback 允例用途：on-device 除錯工具 + Faster-Whisper local inference server
     - `includeSubdomains="false"` 明示僅限字面主機名，禁止通配，阻擋 DNS rebinding 變體

### A.2 未觸動清單（刻意保留）

- `AndroidManifest.xml`（`usesCleartextTraffic="false"` + `networkSecurityConfig` 綁定，S13-0 已就位）
- 任何 `.kt` 檔 — 含 `AppHttpClient.kt`、`WifiKeyboardServer.kt`、`FraudReportManager.kt`、`CloudSyncManager.kt`
- `domain-config` 實際 `domain` entries（localhost、127.0.0.1 語意等價）
- OkHttp profiles / TLS 設定

---

## B. 風險

### B.1 回歸風險

**零。** Android `NetworkSecurityConfig` parser 僅解析 `<base-config>`、`<debug-overrides>`、`<domain-config>`、`<trust-anchors>`、`<certificates>`、`<domain>` 等節點及其屬性；XML comment 不影響 runtime。

編譯層：AAPT 對 `res/xml/*.xml` 的 comment 節點僅剝除，不影響 resource id 或 binary XML 結構。

### B.2 未覆蓋項（由設計）

1. **`WifiKeyboardServer` LAN IP 綁定**
   - 真正的 LAN cleartext 用途（`http://192.168.x.y:8765`）**未**出現在 `domain-config` allowlist
   - 這是**預期行為**：`NetworkSecurityConfig` 只管 **OkHttp / HttpURLConnection client-side** 外發；`WifiKeyboardServer` 自持 `ServerSocket` (`WifiKeyboardServer.kt:77, 129`)，server-side cleartext 不受 `NetworkSecurityConfig` 管轄
   - 此邊界留給 S13-2 / 後續 round 重新評估 server-side TLS 可行性

2. **`debug-overrides` 未顯式放寬 cleartext**
   - 刻意選擇：避免 "debug 能通 / release 才爆" 的 worst-case debug 偏差
   - 若未來某 debug 情境需要 cleartext mock server，應**該 round 明確擴充**而非靜默放寬

### B.3 CCA B.3 觀察已吸收

CCA §B.3 指出初版 XML 註解 `8080` 與實檔 `WifiKeyboardServer.kt:51 DEFAULT_PORT = 8765` 不一致（非 blocker, O1）。本 CCB 文件補寫時同步 fix，改為 `<port>（可配置，預設 8765）`，避免未來 port 調整再度漂移。

---

## C. 下一步建議（候選清單，順序由 CodeX A 決定）

1. **S13-1-CCA**（雪鑰，已 `DRAFT`）：已先行交付 `SPRINT13_1_CCA_NETWORK_SECURITY_CONSUME_IMPLEMENTATION_2026-04-21.md`，覆蓋 privacy_policy wording 缺口、10 條 prohibited commitments、release contract 對齊。待董事長確認後進 `衡鑑` CXA。
2. **S13-1-CXA**（衡鑑）：技術二審 XML 實檔 + CCA 契約 + 是否引入 runtime 風險。
3. **`RULE/NETWORK_SECURITY_POLICY.md`（候選）**：把 loopback-only cleartext、LAN HTTP server-side TLS deferral、user CA debug-only 三條邊界正式化為 rule 檔。不在本 round 做。
4. **`WifiKeyboardServer` LAN 綁定的威脅模型**（R-S2/S3 延伸）：獨立 round 處理 server-side TLS / mTLS，不在 S13-1 偷包。

---

## D. 架構決策

### D.1 `NetworkSecurityConfig` 為 cleartext 邊界單一宣告點

- 所有 **OkHttp / HttpURLConnection client** 外發受 `base-config` 管制（`cleartextTrafficPermitted="false"`）
- loopback 例外集中在 `domain-config`（localhost + 127.0.0.1）
- LAN HTTP（`WifiKeyboardServer` 自持 ServerSocket）不經此宣告，由 client 層不發起外部 cleartext 請求達成隔離；此為 Android 平台行為，非本 CCB 的新決策

### D.2 Debug 路徑不放寬 cleartext

- `debug-overrides` 僅放寬 user CA（給 mitmproxy / Charles 抓包用），cleartext 嚴格度與 release 一致
- 避免 "debug 通 / release 爆" 反模式

### D.3 `includeSubdomains="false"` 顯式標註

- 防止後續誤改為 `true`，意外允許 `*.localhost` / `*.127.0.0.1` 等 DNS rebinding 載體
- 明示邊界意圖，而非依賴預設值

---

## E. 玄樞審核需求 / 結論

### E.1 CCB 側狀態

`READY` for 雪鑰 CCA consume。

### E.2 本 round 整體

未達 `玄樞` gate 判定階段；依 Sprint13 gate §3 S13-1 提交鏈：

```
鍵律 CCB → 雪鑰 CCA → 衡鑑 CXA → 玄樞 gate
   ↑             ↑              ↑
已完成      已 DRAFT        尚未開始
```

鍵律 CCB 不自行宣告 round `READY` / `COMPLETED`，等 CodeX A 排序與後續鏈路回饋。

### E.3 `.kt` 複核觸發

本輪 **0 `.kt` 變更**，未觸發 `ROUND_GATE_COLLAB_RULE.md §7` 的 `.kt` 實檔強制複核條款。

僅 `res/xml/network_security_config.xml` 一檔變更，請 CCA / CXA 實檔核對。

---

## F. 實檔引用

- `app/src/main/res/xml/network_security_config.xml`（本輪唯一改動）
- `app/src/main/AndroidManifest.xml:41-42`（manifest 綁定，本輪未動，確認狀態）
- `app/src/main/java/com/snowrealm/yukiboard/app/net/AppHttpClient.kt`（OkHttp 單入口，本輪未動，確認狀態）
- `app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt:51, 77, 129`（LAN ServerSocket owner，本輪未動，port 事實來源）

---

## G. 補寫備忘

本文件於董事長 `你 S13-1-CCB-NETWORK-SECURITY-RUNTIME-IMPLEMENTATION 放哪` 提示後補寫。違反 `ROUND_GATE_COLLAB_RULE.md §4` 所要求的「回交需有明確文件依據」慣例。已同步吸收 CCA §B.3 觀察修正 XML 註解事實漂移。後續同類回交必須在 chat 輸出當下立即落 `docs/` 文件，不再以摘要代替。

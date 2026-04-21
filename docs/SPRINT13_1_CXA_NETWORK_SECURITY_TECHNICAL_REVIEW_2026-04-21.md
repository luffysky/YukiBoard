# Sprint 13-1 CXA — Network Security Technical Review

日期：2026-04-21
提交者：`衡鑑`（CXA）
任務 ID：`S13-1-CXA-NETWORK-SECURITY-TECHNICAL-REVIEW`
交付型：technical review / runtime cross-check / consume alignment review
提交對象：`玄樞`（final gate）

---

## A. 審核範圍

本次二審對象：

1. [docs/SPRINT13_1_CCB_NETWORK_SECURITY_RUNTIME_IMPLEMENTATION_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_1_CCB_NETWORK_SECURITY_RUNTIME_IMPLEMENTATION_2026-04-21.md)
2. [docs/SPRINT13_1_CCA_NETWORK_SECURITY_CONSUME_IMPLEMENTATION_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_1_CCA_NETWORK_SECURITY_CONSUME_IMPLEMENTATION_2026-04-21.md)

已對照的實檔：

1. [app/src/main/res/xml/network_security_config.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/res/xml/network_security_config.xml)
2. [app/src/main/AndroidManifest.xml](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/AndroidManifest.xml)
3. [app/src/main/assets/legal/privacy_policy.html](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/assets/legal/privacy_policy.html)
4. [app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt)
5. [app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/panel/RemoteKeyboardPanelView.kt)

本 round 實際變更檔：

1. `app/src/main/res/xml/network_security_config.xml`

本 round 無 `.kt` 變更。

---

## B. 審核結論

### B.1 CCB 實作面

`CCB` 的核心主張成立：

1. 本 round 唯一 runtime 實作點確實是 `network_security_config.xml`
2. 改動為註解與結構說明增補，未改變 XML 節點語意
3. `AndroidManifest.xml` 仍維持：
   - `usesCleartextTraffic="false"`
   - `networkSecurityConfig="@xml/network_security_config"`
4. XML 三段結構與實際內容對齊：
   - `base-config`: `cleartextTrafficPermitted="false"` + system CA
   - `debug-overrides`: user CA，debug only
   - `domain-config`: `localhost` / `127.0.0.1`

另外，CCB 已把先前 `8080` 的註解漂移修正成 `<port>（可配置，預設 8765）`，這點與 [WifiKeyboardServer.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/remote/WifiKeyboardServer.kt:51) 的 `DEFAULT_PORT = 8765` 對齊。

### B.2 CCA consume 面

`CCA` 的主軸判斷大致成立：

1. `privacy_policy.html §4.2` 目前寫「網路通訊強制使用 HTTPS（TLS 1.2+）」，這句嚴格說只對 outbound client path 為真
2. `WifiKeyboardServer` 的 LAN `http://<LAN-IP>:8765` 是 server-side cleartext，不受 `NetworkSecurityConfig` 管轄
3. `RemoteKeyboardPanelView` 在 LISTENING 狀態只顯示：
   - `URL: http://...`
   - `連線 PIN 碼: ...`
   - `驗證: PIN + 連線金鑰`
   沒有任何「未加密」提示
4. `privacy_policy.html §2.1` 目前仍寫「打字內容...不會傳送至任何伺服器」，對啟用 WiFi 遠端鍵盤的情境而言，屬技術上不精確

因此，`CCA` 把 consume / wording 缺口標成 `P1/P2` 是合理的。

---

## C. Findings

1. `S13-1-CCA` 把 network / remote keyboard 揭露缺口直接路由到 `S13-5`，scope 不對。  
[docs/SPRINT13_1_CCA_NETWORK_SECURITY_CONSUME_IMPLEMENTATION_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_1_CCA_NETWORK_SECURITY_CONSUME_IMPLEMENTATION_2026-04-21.md:161) 建議「本輪即結案，文案缺口併入 `S13-5`」，但 `S13-5` 的 gate 是 clipboard / userdict sync contract，不是 remote keyboard / network security。這不是事實錯誤，但屬路由錯誤。若 `玄樞` 不打算在 `S13-1` 內處理文案，應改成「留待新 round 或由 `玄樞` 明示指定」，不應預設塞給 `S13-5`。

2. `S13-1-CCA` 的文件精度不足：`terms_of_service.html` 已存在，不應寫成「若存在」。  
[docs/SPRINT13_1_CCA_NETWORK_SECURITY_CONSUME_IMPLEMENTATION_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_1_CCA_NETWORK_SECURITY_CONSUME_IMPLEMENTATION_2026-04-21.md:137) 寫「`terms_of_service.html`（若存在）」；但 repo 內實檔存在於 `app/src/main/assets/legal/terms_of_service.html`。這是 non-blocking，但代表 legal 文件面沒有完整盤完。

3. `S13-1-CCB` 原先未即時落檔，現在已補正，不再是 blocker。  
[docs/SPRINT13_1_CCB_NETWORK_SECURITY_RUNTIME_IMPLEMENTATION_2026-04-21.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/SPRINT13_1_CCB_NETWORK_SECURITY_RUNTIME_IMPLEMENTATION_2026-04-21.md:146) 已明寫這是補寫。流程紀律上是一次違規，但文件現已補齊，且內容與實檔一致，因此不阻擋本 round 技術結案。

---

## D. Blocking / Non-Blocking

### D.1 Blocking

本輪 blocking 數量：`0`

理由：

1. `CCB` 文件已補齊並與唯一實作檔 `network_security_config.xml` 對齊
2. 本 round 的 runtime 變更不存在 `.kt` 實檔越界或假修
3. `CCA` 的問題集中在 consume 路由與 wording 後續安排，不影響本 round「network security baseline 已收斂」這個核心結論

### D.2 Non-Blocking

1. `CCA` 不應預設把 remote keyboard / cleartext 揭露問題丟給 `S13-5`
2. `CCA` 對 `terms_of_service.html` 的存在性描述不精確
3. `CCB` 交接文件補寫晚於交接當下，流程上需記錄

---

## E. 對玄樞的判定建議

`READY`

Reason:
1. `S13-1` 的 runtime baseline 已到位：manifest 與 `network_security_config.xml` 對齊，release outbound cleartext 仍被拒，debug-only user CA 邊界清楚。
2. 本 round 實作面只有 XML 註解與結構說明補強，未引入新的 runtime 風險，也未越界擴張 `.kt` / manifest / network client。
3. `CCA` 已把 consume 端的真缺口指出來：`privacy_policy.html` 與 `RemoteKeyboardPanelView` 對 LAN cleartext 揭露不足。這些缺口應由 `玄樞` 指定留在本題後補或開新 round，但不阻止 S13-1 的 baseline 結案。

---

## F. 交付摘要

### F.1 變更摘要

1. `CCB` 已完成 `network_security_config.xml` 的最小文件化補強
2. `CCA` 已完成 consume / wording 缺口盤點與禁止承諾清單
3. `CXA` 二審確認：
   - runtime 實作與交付說明一致
   - consume 缺口存在但不構成 baseline blocker

### F.2 風險

1. `privacy_policy.html §2.1 / §4.2` 與 remote keyboard cleartext 揭露仍未真正落地修文
2. `RemoteKeyboardPanelView` 仍無「未加密」提示
3. 若後續 round 沒有明確承接這兩點，使用者可見契約仍會落後於實際邊界

### F.3 下一步建議

1. 交 `玄樞` final gate
2. 請 `玄樞` 明示以下二選一：
   - 在 `S13-1` 補一個最小 wording patch round
   - 另開新 round 承接 remote keyboard / network disclosure
3. 不建議默認丟到 `S13-5`

### F.4 架構決策

1. `NetworkSecurityConfig` 仍是 outbound cleartext 的單一宣告點
2. `WifiKeyboardServer` 的 LAN HTTP cleartext 屬 server-side 邊界，不應假裝已被 NSC 覆蓋
3. consume 文案必須和這條平台邊界對齊，不能用「全 HTTPS」一筆帶過

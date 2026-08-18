# YukiBoard — 可以升級的地方

> 2026-08-18 寫。作者讀過 `app/src/main/`（302 個 Kotlin 檔）、
> `AndroidManifest.xml`、`network_security_config.xml`、`WifiKeyboardServer.kt`、
> 測試目錄與 sprint 文件之後的判斷，不是複述 README。
>
> 對照組是 `D:\SnowRealmRebirth\1page`（Web），所以下面刻意只搬
> **與平台無關**的做法，不硬套 Web 的東西。

---

## 一、這個專案在做什麼、現在到哪裡

SnowRealm 生態系的動畫鍵盤 Android App（IME）。
雪凜角色 Lottie 浮動 + 全鍵盤 OpenGL ES 流光特效。

```text
規模    302 個 Kotlin 檔、約 22,000 行、9 個測試檔、**225 份 markdown**
        （docs/ 179 份 + 根目錄 46 份）
輸入    倉頡 / 拼音 / 英文 / 日文 / 韓文 引擎、候選字、智慧輸入、
        滑動輸入、手寫、語音
周邊    主題面板、音樂面板、剪貼簿面板、使用者字典面板、背景設定、
        VRM 角色、遠端鍵盤（Wi-Fi）、雲端同步、Insight 整合、金流
CI      無
```

**先說做得好的地方，因為它決定了下面的判斷順序**：

```text
9 個測試檔全部在 input/ 底下     倉頡 / 拼音 / 英日韓引擎 / InputRouter /
                                 CandidateManager / SmartInputManager。
                                 一個 IME 最該測的正是這些——測對了地方
network_security_config.xml      production 拒絕明文、只信系統 CA；
                                 debug 才放 user CA（給 mitmproxy）；
                                 明文白名單只有 loopback，而且**寫了理由**
遠端鍵盤的 PIN                   6 位 PIN + session token + 連續錯誤鎖定
                                 5 分鐘。AuthLevel.PIN_PLUS_SESSION
```

這些都不是隨手寫的。**問題不在於沒想過，在於想過的事沒有被追蹤。**

---

## 二、最該先修的三件事

### 1. 🔴 遠端鍵盤全程明文——而你們自己記過，然後那筆記被埋了

`WifiKeyboardServer` 綁在 `http://<LAN-IP>:8765`，WebSocket 是 `ws://`，
而 PIN 是**放在網址的查詢字串**裡送出去的：

```js
const wsUrl = 'ws://' + location.host + '/?pin=' + currentPin;
```

`docs/SPRINT12_4_CCB_REMOTE_KEYBOARD_PIN_HARDENING_2026-04-19.md` 第 53 行
自己寫得很清楚：

> 全程明文 HTTP/WS，主動 MITM 仍可抄到 session + PIN；
> 此部分列入 Sprint 13+ TLS 範圍，不在本輪。

**那個判斷是對的。而 Sprint 13 做了 `network_security_config`，
沒有做 LAN 的 TLS。** 於是一件被正確識別、正確延期的事，
現在躺在一份 179 份文件裡的第 53 行。

⚠️ **這一條排第一是因為這是 IME，不是一般 App。**
使用者打的每一個字都經過它——密碼、訊息、銀行帳號。
在咖啡廳或共用辦公室的 Wi-Fi 上開遠端鍵盤，同網段任何人：

```text
被動側錄  → 看得到 ws:// 的每一個 frame，也就是每一個按鍵
          → 也看得到握手網址裡的 PIN
主動 MITM → 完全接管
```

**修法有三層，成本差很多**：

```text
最低成本（今天就能做）
  PIN 從查詢字串移到第一個 WebSocket 訊息裡。
  這不解決竊聽，但至少 PIN 不會出現在網址列、瀏覽器歷史、
  以及任何中間裝置的存取紀錄裡

中成本
  自簽憑證 + wss://，第一次連線時在鍵盤面板顯示指紋讓使用者比對。
  這是同類工具（scrcpy、KDE Connect）走的路

最誠實的一步（不管做不做 TLS 都該做）
  在開啟遠端鍵盤的那個畫面上寫清楚：
  「這個連線沒有加密。只在你信任的網路上使用。」
  現在的 UI 顯示的是 PIN 與鎖定機制——那會讓人以為它是安全的。
  ⚠️ 讓人以為安全比不安全更糟，這與「不做一顆按了會失敗的按鈕」
     是同一條判斷
```

### 2. 225 份 markdown、302 個程式檔——文件比程式碼還多，而重要的事會沉下去

```text
docs/       179 份
根目錄      46 份
比例        每 1.3 個 Kotlin 檔配 1 份文件
```

檔名長這樣：

```text
SPRINT12_4_CCA_REMOTE_KEYBOARD_PIN_CONSUME_REVIEW_2026-04-19.md
SPRINT11_3_CCB_CANDIDATE_DICTIONARY_ENGINE_SMOKE_IMPLEMENTATION_2026-04-18.md
SPRINT10_6_CXB_BGUPDATE_RECEIVER_LIFECYCLE_IMPLEMENTATION_2026-04-18.md
```

**這套流程本身是有效的**——每一輪都有 pre-check、實作、review、gate verdict，
角色分工清楚。第 1 點那個明文風險就是靠它被正確識別的。

**但它有一個結構性的漏洞**：那些文件是**當下的紀錄**，
沒有任何一份是**現在還沒解決的事的清單**。
所以「延到 Sprint 13+」這種話寫下來之後，除非有人記得，
否則不會有任何機制把它撿回來。

⚠️ 這與 1page 反覆記的第二種毛病同源：
**紀錄存在不等於紀錄有效**。179 份 sprint 文件證明的是
「每一輪都有被檢視」，不證明「檢視出來的事都被處理了」。

**修法（很便宜）**：

```text
1. 建一份 docs/DEFERRED.md ——**唯一一份**「已知但還沒做」的清單。
   每一條要有：是什麼、哪一輪識別的、為什麼延、什麼條件下要撿回來
2. 把現有 179 份裡所有「不在本輪」「列入 Sprint N+」的句子撈出來，
   一次性填進去。這件事一個下午做得完
3. 之後每一輪 gate 的最後一步是：翻一次 DEFERRED.md
```

⚠️ **不要試圖整理那 225 份文件**。它們是歷史，歷史本來就會累積。
要做的是給「還沒做的事」一個**單一入口**，那是完全不同的一件事。

### 3. 沒有 CI——9 個測試只在有人記得跑的時候跑

`.github/workflows/` 不存在。而那 9 個測試涵蓋的是
倉頡、拼音、英日韓引擎、InputRouter、CandidateManager、SmartInputManager——
**這個 App 最核心也最容易安靜壞掉的部分**。

一個輸入引擎改壞的表現不是崩潰，是「某個字打不出來了」或
「候選字順序變了」，而那要等使用者抱怨才會知道。

**最小可行的 CI**（一個檔案，半小時）：

```yaml
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - run: ./gradlew test           # 那 9 個測試
      - run: ./gradlew assembleDebug  # 至少證明編得過
```

⚠️ **先不要加 lint 或 detekt。** 22,000 行的專案第一次跑靜態檢查
一定噴出幾百個警告，而那會讓人直接把整個 CI 關掉。
先讓「測試 + 編譯」綠著跑一個月，再談加規則。

---

## 三、可以從 1page 搬過去的（跨平台成立的部分）

Web 的東西大多搬不過來，所以只列真的適用的：

```text
故意改壞驗守衛            每加一個測試就把程式改壞一次，確認它真的會紅、
                         而且訊息說得出問題在哪。改壞的方式要對應
                         它宣稱要擋的那件事。
                         ⚠️ 這一條對輸入引擎特別有用：
                         「倉頡測試有沒有真的在驗倉頡」很容易變成
                         「有沒有回傳非空陣列」

「反過來問」型的守衛       不要列「倉頡要有測試」，要問
                         「input/engine 底下有沒有哪一個引擎沒有對應的測試檔」。
                         前者每加一種輸入法都要記得補，後者自己會發現下一次。
                         刻意不測的要列進具名清單**並寫理由**

斷言要釘不會過期的事實     1page 有兩條測試拿「還沒實作的那一個」當例子，
                         實作之後一條紅了、另一條照樣綠但驗的已經不是
                         名字說的那件事。需要「目前還沒做的那一個」時，
                         去問註冊表算出來，不要寫死

介面要有非拖曳的替代路徑    WCAG 2.1 §2.5.7 在 Android 上同樣成立
                         （TalkBack）。滑動輸入、手寫、面板拖曳
                         各自有沒有替代操作，值得逐一問一次
```

---

## 四、不建議做的事

```text
❌ 大重構那幾個千行檔        BackgroundSettingsSection 1235 行、
                            UserDictionaryPanelView 1204 行、
                            InputRouter 1126 行看起來該拆。
                            ⚠️ 但 InputRouter 有測試，另外兩個沒有——
                            在沒有測試的情況下拆 UI 面板是盲拆。
                            真的要拆就先補測試，而補測試的優先序
                            在第 3 點的 CI 後面

❌ 整理那 225 份 markdown    見第 2 點。要的是「還沒做的事」的單一入口，
                            不是把歷史整理漂亮

❌ 現在加 detekt / ktlint    見第 3 點。第一次跑會噴幾百個警告，
                            結果是整個 CI 被關掉

❌ 為了 SSO 動 auth          見 SnowRealm-Platform 的規劃。
                            而且這個專案已經走 Insight 當唯一 gateway
                            （CloudSyncManager 的註解寫得很清楚），
                            那個決定本身是對的，先別動

❌ 追測試覆蓋率              9 個測試測對了地方（輸入引擎）。
                            往 UI 擴的邊際效益遠低於「讓這 9 個自動跑」
```

---

## 五、順序

```text
第一步   PIN 從查詢字串移到 WebSocket 第一個訊息（今天就能做）
第二步   遠端鍵盤畫面上寫清楚「這個連線沒有加密」
第三步   一個 gradlew test + assembleDebug 的 CI
第四步   docs/DEFERRED.md，把 179 份裡所有「不在本輪」撈進來
之後     LAN 的 wss + 憑證指紋比對、輸入引擎的反過來問守衛
```

第一、二步加起來一小時，而它們處理的是**這個 App 最高風險的那個功能**。
第三步讓既有的 9 個測試從「有人記得就跑」變成「一定會跑」。

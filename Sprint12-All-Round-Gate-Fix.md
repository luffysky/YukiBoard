# Sprint 12 All Round Gate Fix

日期: 2026-04-20  
身份: 玄樞  
性質: `Sprint 11 / Sprint 12 gate 修補文件`  
用途:

- 不覆蓋原 `Sprint11-All-Round-Gate.md`
- 不覆蓋原 `Sprint12-All-Round-Gate.md`
- 用來補正：
  - 董事長授權例外
  - gate 文件未寫清的 override
  - `Sprint 12` 尚未閉環的遺留項
  - 進 `Sprint 13` 前必須承認的未完成事實

---

## 1. 修補目的

這份文件不是要推翻 `Sprint 11` 與 `Sprint 12` 已完成的實作與審核結果。

這份文件的目的，是把原 gate 檔中「已實際發生、但未被完整制度化寫清」的部分正式補上，避免後續交接時出現三種誤判：

1. 把董事長特准放權，誤讀成一般常態規則
2. 把單次 override，誤讀成 round 規則已永久改寫
3. 把 `Sprint 12` 的部分覆蓋，誤讀成主題已全部閉環

---

## 2. 董事長授權例外正式補記

### 2.1 適用背景

在 `Sprint 11` 後段到 `Sprint 12` 期間，因 `Codex / 玄樞 / 衡鑑` 額度不足，董事長已明確授權：

1. `雪鑰` 可暫代 `玄樞` 執行 gate 判定
2. `雪鑰` 可在董事長明示放權下，對當輪 `.kt` round 直接標記 `READY / COMPLETED`
3. 該放權屬董事長的單次授權，不視為永久改寫 `RULE/SPRINT_GATE_RULE.md`

### 2.2 對 `Sprint 11` 與 `Sprint 12` 的正式裁定

因此，下列標記視為：

- `Sprint11-All-Round-Gate.md` 中：
  - `S11-4`
  - `S11-5`
- `Sprint12-All-Round-Gate.md` 中：
  - `S12-4`
  - `S12-5`

其 `雪鑰代行玄樞` 所做的 `READY / COMPLETED`，
在董事長已明示授權的前提下，視為**本次有效**，不追溯改判為無效。

### 2.3 邊界說明

這個例外只代表：

1. 本次董事長授權成立
2. 本次 round 的 gate 結論成立
3. 後續若再發生同類情況，仍應先在 gate 檔或補件檔明寫「董事長授權放權」

這不代表：

1. `雪鑰` 成為常設 gate owner
2. `.kt` round 可常態由非 `Codex` 角色直接正式結案
3. 原 `RULE/SPRINT_GATE_RULE.md` 已被永久改寫

---

## 3. Sprint 11 修補結論

### 3.1 已完成部分，正式維持有效

`Sprint 11` 下列結果維持有效：

1. `S11-0` test scope / harness
2. `S11-1` `InputRouterTest` baseline
3. `S11-2` `SmartInputManagerTest` baseline
4. `S11-3` candidate / dictionary / engine smoke tests
5. `S11-4` silent catch first pass
6. `S11-5` GL / TTS debt fix

### 3.2 需要補正的不是實作，而是文件語意

`Sprint 11` 真正需要修補的點，不在 code 完成度，而在 gate 文件語意：

1. `S11-4`、`S11-5` 的 `.kt` round，由 `雪鑰` 代行 gate 屬董事長放權例外，原檔未在開頭總則區正式提升成「本 sprint 特例」
2. `S11-3` 通過後同時開 `S11-4` 與 `S11-5`，屬本 sprint 的平行處理例外；原檔有寫解鎖規則，但沒把「為何本 sprint 可雙門並行」升成正式 sprint-level override
3. `S11-5` 的治理重點其實有兩個：
   - TTS / GL 技術債修補完成
   - `雪鑰` 越代 GL 事件已被董事長糾正並收口
   原檔有記錄事件，但沒有在 sprint 總結層明寫「治理事件已結案，後續不得援引為常態 precedent」

### 3.3 對 Sprint 11 的補正裁定

`Sprint 11` 的正式修補結論如下：

```text
COMPLETED

Reason:
1. Sprint 11 的 code / test / debt fix 實作已完整收口，完成度成立。
2. 雪鑰代行 gate 的合法性，來自董事長明示放權；本 fix 文件已補正這項制度依據。
3. S11-4 / S11-5 的並行推進，視為本 sprint 的單次例外，不視為永久改寫「一次只開一個 round」。
4. GL 越代事件已在當輪被董事長糾正、還原、重交與擇優採用，治理閉環成立。
```

---

## 4. Sprint 12 修補結論

### 4.1 已完成且維持有效的部分

`Sprint 12` 下列 round 完成度成立，維持有效：

1. `S12-0` Payment / Key Audit
2. `S12-1` Newebpay Server-side Signing Plan
3. `S12-4` Remote Keyboard PIN Hardening
4. `S12-3` API Key Release Policy
5. `S12-5` Observability Hook

上述五門的 audit / policy / hardening / observability 成果，均視為有效交付，不因代行 gate 而追溯撤銷。

### 4.2 `Sprint 12` 沒有完全做完的部分

`Sprint 12` 的主題是安全整改。

以主題完成度來看，`Sprint 12` 並不是「全部閉環」，而是「大部分完成，關鍵一門 deferred 後留下 P0 遺留」。

未完成的核心點如下：

1. `S12-2 Android Client Payment Refactor` 已被董事長指示 deferred
2. 因 `S12-2` deferred，`Newebpay HashKey / IV` 仍未從 APK 風險面完全閉環
3. `S12-3.1` 實作 round 尚未開門
4. `S12-3` 已把 release policy 定義清楚，但 policy 並不等於 implementation 完成

### 4.3 原 gate 檔中需要補正的語意缺口

`Sprint12-All-Round-Gate.md` 的主要缺口有四個：

1. `S12-2` deferred 後，實際流程已依董事長明示 override 改採 CH-1 特殊順序；原 gate 檔有記錄結果，但未把這個 override 在規則層整理成一條 sprint-level trace note，較不利後續交接追溯
2. `S12-4` / `S12-5` 已做完，但 sprint-level 狀態仍保留 `ACTIVE`，容易讓交接者誤判 sprint 是否正式收口
3. `Sprint 12 完成條件` 第 1 條寫的是「不再把支付核心 secret 留在 APK」，但這條在 `S12-2` deferred 後其實尚未完全成立
4. `S12-3.1` 被明確列為下一實作門，但沒有被提升成「Sprint 12 未閉環的第一優先遺留」

### 4.4 對 Sprint 12 的正式修補判定

因此，`Sprint 12` 不能被寫成單純 `COMPLETED`，更精確的狀態應補正為：

```text
PARTIALLY COMPLETED / SECURITY POLICY COMPLETE / PAYMENT SECRET REMEDIATION DEFERRED

Reason:
1. Audit、policy、remote PIN hardening、observability hook 均已完成。
2. 董事長已特准雪鑰代行 gate；因此 S12-4、S12-5 的 READY / COMPLETED 保持有效。
3. 但 S12-2 deferred 後，支付核心 secret 風險尚未實作閉環，因此 Sprint 12 主題不是 100% complete。
4. S12-3.1 應視為 Sprint 12 遺留中的第一優先候選接續門，而不是一般低優先候選事項；是否先於 Sprint 13 進行，仍由董事長拍板。
```

---

## 5. Sprint 12 狀態修補

### 5.1 正式狀態改寫建議

原檔目前寫：

- `狀態: ACTIVE`

這個狀態在 2026-04-20 已不精確。

本修補文件裁定，後續若需要在總覽或 handoff 文件引用 `Sprint 12`，應使用以下狀態：

```text
狀態: PARTIALLY COMPLETED
```

或更完整地寫成：

```text
狀態: PARTIALLY COMPLETED（S12-0 / S12-1 / S12-3 / S12-4 / S12-5 已完成；S12-2 deferred；S12-3.1 未開）
```

### 5.2 `Sprint 12 完成條件` 的修補解讀

原第 5 節四條完成條件中：

1. `不再把支付核心 secret 留在 APK`
2. `高風險 key 有明確 release policy`
3. `remote keyboard 最低安全基線提升`
4. `觀測接點有明確落地`

本修補文件重新解讀如下：

- 第 2、3、4 條：已成立
- 第 1 條：尚未完全成立，因 `S12-2` deferred

因此 `Sprint 12` 是：

- `3 / 4 主條件成立`
- `1 / 4 主條件 deferred`

### 5.3 `S12-3.1` 的優先級修補

`S12-3.1` 不應再只是「下一候選 round」。

本修補文件裁定：

1. `S12-3.1` 是 `Sprint 12` 安全整改鏈的直接延續門
2. `S12-3.1` 應列為後續排程中的高優先遺留候選；是否先於 `Sprint 13` 啟動，仍由董事長裁示
3. `S12-3.1` 開門前，需先由 `玄樞` 對 §J.4 的兩項仲裁題做正式判定

---

## 6. 對後續交接的正式口徑

之後若任何 handoff / summary / review 需要引用 `Sprint 11` 與 `Sprint 12`，統一使用以下口徑：

### 6.1 Sprint 11

```text
Sprint 11 = COMPLETED

補充：
- 雪鑰代行 gate 為董事長明示放權，本次有效
- S11-4 / S11-5 並行屬單次 sprint 例外
- GL 越代事件已糾正並收口，不得援引為常態 precedent
```

### 6.2 Sprint 12

```text
Sprint 12 = PARTIALLY COMPLETED

已完成：
- S12-0 audit
- S12-1 plan
- S12-3 release policy
- S12-4 remote PIN hardening
- S12-5 observability hook

未閉環：
- S12-2 payment secret remediation（deferred）
- S12-3.1 implementation round（not opened）

補充：
- 雪鑰代行 gate 為董事長明示放權，本次有效
- CH-1 跳門順序屬董事長 override，不視為永久改寫一般解鎖規則
```

---

## 7. 實檔複核補件

### 7.1 複核範圍

`玄樞` 已於 `2026-04-20` 親自重新打開 `Sprint 11` / `Sprint 12` 實際改動的 `.kt` 檔逐檔複核，並補跑：

- `:app:compileDebugKotlin`
- `:app:testDebugUnitTest`

結果：

- `BUILD SUCCESSFUL`

### 7.2 複核結論

本次複核結論如下：

1. `Sprint 11` 的 test baseline、silent catch、TTS、GL 實作未見新的 blocking bug
2. `Sprint 12` 的 remote keyboard hardening 與 observability hook 未見新的 blocking bug
3. `ZCoin / payment` 最終串接未完成，屬董事長已接受的 deferred 範圍，不列為本次補件缺陷
4. 目前僅發現一項應補的非 blocking 清理項

### 7.3 補修項目

#### Fix-1：移除 `ImeServiceLifecycle.kt` 的重複 null 判斷

檔案：

- `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceLifecycle.kt`

位置：

- `setupKeyboardOverlays()` 內 `if (useCustomBg && savedBg != null)`

問題描述：

1. `useCustomBg` 已經由 `prefs.useCustomBackground && savedBg != null` 推導而來
2. 後續再寫一次 `savedBg != null` 屬重複條件
3. 目前不影響 runtime 行為，但會持續留下 Kotlin warning 與語意噪音

正式分類：

- `NON-BLOCKING CLEANUP`

處理要求：

1. 不改行為
2. 只做最小語意清理
3. 不得順手擴修 `ImeServiceLifecycle.kt` 其他區塊

建議修法：

- 將該條件收斂為單一判斷，避免重複 null check

### 7.4 修補安排與結案狀態

本補件項目已完成並正式結案：

#### ~~`S12-FIX-1`~~

~~主題：~~

~~- `ImeServiceLifecycle.kt` warning cleanup~~

~~狀態：~~

~~- `READY / ACCEPTED / COMPLETED`~~

~~指定負責：~~

~~- `鍵律`：實作~~
~~- `雪鑰`：二審~~
~~- `玄樞`：最終 gate~~

~~提交鏈：~~

~~1. `鍵律` 完成 `S12-FIX-1-CCB-IMESERVICE-LIFECYCLE-WARNING-CLEANUP`~~
~~2. 提交給：`雪鑰`~~
~~3. `雪鑰` 完成 `S12-FIX-1-CCA-IMESERVICE-LIFECYCLE-WARNING-REVIEW`~~
~~4. 提交給：`玄樞`~~
~~5. `玄樞` 已完成最終複核並判定 `READY / ACCEPTED`，此補件結案~~

~~限制：~~

~~1. 只允許改 `app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceLifecycle.kt`~~
~~2. 只允許清掉這個重複條件與其直接相鄰必要語句~~
~~3. 不得偷修其他 warning~~
~~4. 不得混入 `ZCoin`、`Newebpay`、`S12-3.1` 或 `Sprint 13` 題~~

~~結案備註：~~

~~1. 實際 diff 為 `if (useCustomBg && savedBg != null)` 收斂為 `if (useCustomBg)`~~
~~2. `雪鑰` 二審確認 file scope 正確、行為等價、compile 驗證通過~~
~~3. `玄樞` 最終複核確認此修補不越界、不改 runtime 行為，正式接受~~

---

## 8. 玄樞最終修補裁定

```text
Sprint 11 Gate Fix: ACCEPTED
Sprint 12 Gate Fix: ACCEPTED

Reason:
1. 董事長已明示授權雪鑰在我額度不足期間代行 gate，故 S11 後段與 S12 的代行 READY / COMPLETED 本次成立。
2. Sprint 11 的主要缺口不在完成度，而在代行與並行例外未被正式制度化；本修補文件已補齊。
3. Sprint 12 的主要缺口不在 audit / policy / hardening / observability 品質，而在 sprint-level 狀態與未完項描述不夠精確；本修補文件已補齊。
4. `玄樞` 已完成 Sprint 11 / Sprint 12 實檔複核；目前僅新增一項 non-blocking cleanup 派工，已併入本 fix 文件。
5. 後續交接、審核、或進 Sprint 13 前，一律以本 fix 文件作為 Sprint 11 / Sprint 12 的補充裁定依據。
```

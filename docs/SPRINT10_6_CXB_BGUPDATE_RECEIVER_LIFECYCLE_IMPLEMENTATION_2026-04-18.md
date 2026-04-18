# Sprint 10-6 — 衡鑑 Implementation：bgUpdateReceiver lifecycle 對稱性修補

身份：衡鑑
任務 ID：`S10-6-CXB-BGUPDATE-RECEIVER-LIFECYCLE-IMPLEMENTATION`
日期：2026-04-18
依據：`Sprint10-All-Round-Gate.md` `Round S10-6`

---

## A. 變更摘要

本輪只修改：

- `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`

本輪修補目標：

- 收斂 `bgUpdateReceiver` 在 `onCreateInputView()` 的 create / floating / destroy path 對稱性
- 讓 floating 與 non-floating create path 都走同一條 receiver 註冊邏輯

實際改動：

1. 新增 `unregisterBgUpdateReceiver()`
2. 新增 `registerBgUpdateReceiver()`
3. `onCreateInputView()` 開頭改為統一呼叫 `unregisterBgUpdateReceiver()`
4. `onCreateInputView()` 在 `keyboardContainer` 加入後、floating 分支前，統一呼叫 `registerBgUpdateReceiver()`
5. `onDestroy()` 的 receiver cleanup 改為統一呼叫 `unregisterBgUpdateReceiver()`

核心語意：

- 修補前：`prefs.floatingModeEnabled = true` 時，`onCreateInputView()` 會在 receiver 註冊前直接 `return wrapInFloatingMode(rootContainer)`，導致 floating path 不註冊 `bgUpdateReceiver`
- 修補後：floating / non-floating 兩條 create path 都先完成 receiver 註冊，destroy path 也走同一個解除入口

---

## B. 風險

### 本輪修掉的真問題

- 原本只有 non-floating path 會走到：
  - `registerReceiver(bgUpdateReceiver, bgFilter, ...)`
- floating path 會提前 return，造成：
  - create path 不對稱
  - 背景更新廣播在 floating 模式下不一定生效
  - destroy path 卻一律假設可能有 receiver 可解除

### 本輪控制的風險

- 沒有改 receiver payload
- 沒有改 action string
- 沒有改 `backgroundView` / `themePanel` reload 行為
- 沒有擴成其他 cleanup / lifecycle framework

### 非 blocking observation

- `YukiBoardIMEService.kt` 目前工作樹同時包含前幾輪已存在但尚未 commit 的同檔改動；本輪新增 patch 已刻意限制在 receiver helper 與 create/destroy 接線，不擴寫到其他區塊。

---

## C. 最小 file scope 實際落地

符合 gate 授權：

- 允許修改：
  - `app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt` ✓

未碰禁區：

- `InputRouter.kt` ✓
- `ImeServiceCallbacks.kt` ✓
- `ImeServiceInput.kt` ✓
- `Settings` / `auth` / payment / sync 相關檔 ✓
- `Sprint 10` gate / `RULE` / `daily_works_0418.md` ✓

---

## D. 驗證

執行：

```text
./gradlew :app:compileDebugKotlin
```

結果：

```text
BUILD SUCCESSFUL
```

補充：

- Kotlin daemon 連線失敗後 fallback compile，最終仍成功
- console 中的 warning 均為 pre-existing deprecation / warning，不是本輪新增

---

## E. 請 玄樞 判定

建議判定：

```text
READY

Reason:
1. 本輪修補只落在授權單檔，file scope 正確。
2. bgUpdateReceiver 已從「只有 non-floating path 註冊」收斂成 floating / non-floating 共用同一條 create/destroy lifecycle。
3. compile 已通過，未見新的 blocking bug 或越界修改。
```


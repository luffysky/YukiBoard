# Sprint 11-5 — GL Frame Loop Fix Implementation

- **Task ID**: `S11-5-CXB-GL-FRAME-LOOP-FIX-IMPLEMENTATION`
- **日期**: 2026-04-19
- **交付人**: 鍵律（CCB），依 2026-04-19 董事長裁示代行 衡鑑 GL 子題
- **審核人**: 雪鑰（代行玄樞），並行審 TTS + GL 兩題
- **Scope**: Option **C** — VSYNC align + `onPause` 立即喚醒 + `stopRenderThread` join 超時告警
- **檔案**: `app/src/main/java/com/snowrealm/yukiboard/animation/gles/KeyboardGLSurfaceView.kt`
- **Baseline**: HEAD（由董事長將工作樹殘留 WIP 還原為 `git show HEAD:` 後再交付本輪）

---

## A. 改動清單（1 檔 × 3 個 scope 項目）

### A.1 Option C 三項實作對照

| Scope | 實作位置 | 實作要點 |
|-------|---------|---------|
| **C-1**：VSYNC align（`Thread.sleep(16)` → Choreographer + Handler 轉發） | `startVsyncPump()` / `stopVsyncPump()` / `waitNextVsync()` + render loop | main Looper Handler 轉發 Choreographer frame callback；render thread 以 `vsyncLock` wait；2×TARGET_FRAME_NS timeout fallback |
| **C-2**：`onPause` 立即 `notifyAll()` 喚醒 wait | `onPause()` 新增兩條 notifyAll | 同時喚醒 `this` lock（alpha=0 wait）與 `vsyncLock`（VSYNC wait），避免最壞要等 wait(2000) timeout 才退出 loop |
| **C-3**：`stopRenderThread` join(500) 超時加 `Log.w` | `stopRenderThread()` 重寫 | join 後檢查 `t.isAlive` 時 Log.w；InterruptedException 時也 Log.w + restore interrupt flag |

### A.2 git diff vs HEAD（本輪唯一改動來源）

```text
 KeyboardGLSurfaceView.kt | 75 insertions, 4 deletions
```

- 新增：`vsyncLock` / `vsyncTick` / `vsyncCallback` 三欄位、`waitNextVsync()` / `startVsyncPump()` / `stopVsyncPump()` 三方法、companion `TARGET_FRAME_NS = 16_666_666L`
- 修改：`onPause()`、`startRenderThread()`、`stopRenderThread()`、render loop frame pacing 區段

---

## B. 每處修法理由

### B.1 C-1：Choreographer VSYNC pump（本輪核心）

- **問題**：原本 `Thread.sleep(16)` 硬鎖 60 FPS。120 Hz 螢幕跑不滿；render thread 不響應 VSYNC，可能撞上下一個 frame 的中段造成 tearing / 無意義喚醒。
- **設計**：
  - `startVsyncPump()` 透過 `Handler(Looper.getMainLooper()).post { ... }` 把 `Choreographer.getInstance().postFrameCallback(cb)` 安排到 main Looper（Choreographer 必須在具 Looper 的 thread 呼叫）。
  - `FrameCallback.doFrame()` 每幀在 `vsyncLock` 內 `vsyncTick++` + `notifyAll()`，並遞迴 `postFrameCallback(this)` 續下一幀；`running=false` 時自動不再 repost，callback 自然終止。
  - render thread 畫完一幀後呼叫 `waitNextVsync()` 阻塞到 `vsyncTick` 前進，最長 `2 * TARGET_FRAME_NS` 後 timeout 返回；若被 interrupt 則回 `false`，呼叫端退出 render loop。
- **為什麼獨立 `vsyncLock` 而不是沿用 `this`**：
  - `this` lock 已被 alpha=0 暫停、`setFlowAlpha`、`onPause` 使用
  - 若共用，VSYNC 每幀 `notifyAll(this)` 會吵醒正在 alpha=0 wait(2000) 的 render thread → 空轉（每 8–16 ms 醒一次 → `continue` → 重進 wait）→ 耗電反而惡化
  - 獨立 lock 把「VSYNC 節拍」與「alpha / pause 生命週期」兩個信號源徹底分開
- **為什麼把 wait 邏輯抽成 `waitNextVsync()` top-level 方法**：
  - Kotlin `synchronized {}` 是 inline lambda；原本想在 lambda 內 `break@loop` 會觸發 `break continue in inline lambdas` 實驗 feature，compile 直接擋（首次編譯的真實錯誤）
  - 抽 top-level function 後用 `return false / return true` 傳遞結果，cleaner、compile 零警告、並把「等下一幀」語意變成 self-documenting 的 API

### B.2 C-2：`onPause` 立即喚醒

- **原樣**：`fun onPause() { running = false }`（在 HEAD baseline）
- **問題**：`running=false` 設下後，render thread 若正卡在 alpha=0 `wait(2000)`，最壞要等 2 秒才檢查 running；本輪新增的 `waitNextVsync` 也會有類似延遲。
- **修法**：`onPause()` 同時 `synchronized(this) { notifyAll() }` 與 `synchronized(vsyncLock) { notifyAll() }`，讓兩邊 wait 都立即退出。
- **對稱性**：和 `setFlowAlpha` 從 0 變正時 `synchronized(this) { notifyAll() }` 的既有寫法對稱。

### B.3 C-3：`stopRenderThread` join 超時告警 + Interrupted 恢復

- **原樣**：`try { renderThread?.join(500) } catch (_: Exception) {}`（在 HEAD baseline）
- **問題**：
  1. join(500) 超時代表 render thread 可能仍在跑，仍持有 EGL context，資源洩漏無可觀測
  2. 吃掉 `InterruptedException` 不 restore interrupt flag，上游若靠 interrupt 傳遞取消會失效
- **修法**：
  - `t.join(500)` 後檢查 `t.isAlive` → `Log.w("FlowLightGL", "stopRenderThread: join timeout (500ms), thread still alive")`
  - `InterruptedException` 時 `Log.w(..., e)` + `Thread.currentThread().interrupt()` 恢復 interrupt flag
  - 整體結構加上 `stopVsyncPump()` 呼叫（對稱 `startVsyncPump`）、`synchronized(this) { notifyAll() }` 喚醒 alpha=0 wait

---

## C. File scope 自檢

董事長 2026-04-19 scope：**只改 `KeyboardGLSurfaceView.kt`**，option C，不擴成 renderer 重構、不碰其他 round。

| 檢查項 | 狀態 |
|--------|------|
| 只動 `KeyboardGLSurfaceView.kt`（1 檔） | ✅ |
| 未改 `FlowLightRenderer.kt` / `FlowLightShader.kt` | ✅ |
| 未改 `service/`、`app/ime/`、`input/`（S11-0～S11-4 範圍） | ✅ |
| 未改 TTS 線（`GoogleCloudTtsEngine.kt`，屬 S11-5 TTS 子題，雪鑰負責） | ✅ |
| 未新增任何 class / util / wrapper 跨模組物件 | ✅（只在此檔內加三個 private 欄位 + 三個 private method + 一個 companion 常數） |
| 未碰 `startRenderThread` 併發 race（option C 明列留 Sprint 12） | ✅ |
| 未改任何 import | ✅（`android.view.Choreographer`、`android.os.Handler`、`android.os.Looper` 全部走 full path，與 S11-4 規則一致） |
| 未擅自擴 `eglSwapBuffers` 失敗 log（scope 未列） | ✅（保留原 silent break，不順手 refactor） |
| 未動 `initEGL / releaseEGL` | ✅ |

---

## D. Compile + Test 證據

### D.1 首次編譯失敗（記錄）

```
e: ...KeyboardGLSurfaceView.kt:152:33 The feature "break continue in inline lambdas" is experimental and should be enabled explicitly
BUILD FAILED in 1s
```

**失敗原因**：`break@loop` 寫在 `synchronized(vsyncLock) { ... }` 的 inline lambda 內。Kotlin 2.0 起此為 experimental feature，預設 off。

**修正方式**：把 wait 邏輯抽到獨立函式 `waitNextVsync(): Boolean`，用 `return false` 傳 interrupt、`return true` 傳正常醒來；render loop 以 `if (!waitNextVsync()) break` 退出。結構更清楚且無需 opt-in experimental flag。

### D.2 修正後編譯

```
./gradlew :app:compileDebugKotlin
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 1s
18 actionable tasks: 1 executed, 17 up-to-date
```

- **無 error、無新增 warning。**

### D.3 Unit test gate

```
./gradlew :app:testDebugUnitTest
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 5s
31 actionable tasks: 5 executed, 26 up-to-date
```

- S11-1 `InputRouterTest`、S11-2 `SmartInputManagerTest`、S11-3 candidate/dictionary/engine smoke 全數通過
- GL 路徑目前沒有獨立 unit test（Android GL thread 無法在 JVM unit test 跑），屬 S11-0 既有不測區域，與本輪無關

---

## E. 架構決策

- **獨立 `vsyncLock`**：與 `this` 的 alpha=0 / onPause 生命週期 lock 刻意分離，避免空轉（E.1 已說明）。
- **Main thread Handler 轉發 Choreographer**：Choreographer 必須在 Looper thread 使用；render thread 沒有 Looper，因此透過 `Handler(Looper.getMainLooper()).post { Choreographer.postFrameCallback }` 把 frame callback 安置到 main Looper。
- **`waitNextVsync()` top-level private function**：避開 Kotlin inline lambda 的 non-local break 實驗限制；同時把「等下一幀」語意抽成 self-documenting 的 API。
- **Timeout fallback（2 × TARGET_FRAME_NS）**：VSYNC 不送達時（黑屏、Choreographer 暫停）render thread 最多等兩幀即返回，避免永久卡住或錯過 `running=false` 訊號。
- **`TARGET_FRAME_NS` 語意改變**：從「frame pacing 的硬鎖目標」改為「VSYNC wait 的 fallback deadline 計算基準」；companion 註解同步更新。
- **不動的地方**：
  - `startRenderThread` 的 `@Synchronized` + `if (running) return` race（scope 明列留 Sprint 12）
  - alpha=0 暫停分支維持 `wait(2000)` on `this`，未改 lock 語意
  - `initEGL / releaseEGL` 完全未動
  - `eglSwapBuffers` 失敗仍為 silent break（scope 未列）

---

## F. 風險盤點

| 風險 | 評估 | 緩解 |
|------|------|------|
| Main thread Handler 負擔增加 | **極低**。每幀只做 `postFrameCallback` 的遞迴 post，不做工作。| 與系統 Choreographer 機制一致，這就是 Android 標準做法。|
| Frame callback 在 view 被 detach 後仍觸發 | **低**。`doFrame` 內先檢查 `running`；若 false 就不再 `postFrameCallback`，callback 自然終止。`stopVsyncPump` 另外 post `removeFrameCallback` 加保險。| 雙保險。|
| 120 Hz 螢幕下 render 太快壓 GPU | **可能**。現在會跟上 VSYNC = 120 FPS。如果流光本身 GPU cost 不高則合理；若造成 thermal 問題，可在 `waitNextVsync` 引入「每 N 個 tick 畫一幀」節拍調速（非本輪 scope）。| Sprint 12 可追加省電觀察指標。|
| `stopVsyncPump` 內 `removeFrameCallback` 是 async post | **低**。遞迴 `postFrameCallback` 在下一個 `doFrame` 會被 `if (running)` 擋住，最多晚一幀停下；已設 `vsyncCallback = null` 讓下次 `startVsyncPump` 能拿新實例。| 符合 Android 習慣。|
| alpha=0 wait(2000) 期間沒有 VSYNC 驅動 | **無**。alpha=0 分支不進 VSYNC wait 路徑；VSYNC callback 即使在 pump 上仍繼續送 tick（但沒人在 vsyncLock 上 wait，notifyAll 變 no-op）。| 兩個 lock 分離的直接好處。|
| onPause 同時 notify 兩個 lock 的開銷 | **無**。兩次 `synchronized { notifyAll() }` 都是常數時間，對一個每 session 呼叫一次的方法來說可忽略。| — |
| 被 interrupt 時的行為 | **正確**。`waitNextVsync()` 回傳 `false` → render loop `break` → `finally { releaseEGL() }` 正常釋放。| 與原 Thread.sleep 的 break 語意對齊。|

---

## G. 雪鑰審核清單（7 項，GL 子題）

- [ ] **G-1**：diff 僅落在 `KeyboardGLSurfaceView.kt`，未越界到 TTS / renderer / service / input 等
- [ ] **G-2**：option C 三項（VSYNC align、onPause notifyAll、stopRenderThread join 超時告警）全部落地，且全部從 HEAD baseline 做起
- [ ] **G-3**：`startVsyncPump` 透過 `Looper.getMainLooper()` Handler 轉發，符合 Choreographer 使用規範
- [ ] **G-4**：VSYNC wait 有 `2 * TARGET_FRAME_NS` timeout fallback，不會因 VSYNC 未送達永久卡 thread
- [ ] **G-5**：`vsyncLock` 為獨立 lock，避免 alpha=0 wait 被 VSYNC 喚醒空轉；`onPause` 對兩個 lock 對稱 notify
- [ ] **G-6**：未改動 `startRenderThread` 併發 race（留 Sprint 12）、未改 `initEGL / releaseEGL`、未擅自順手改 `eglSwapBuffers` silent break、未新增跨檔依賴
- [ ] **G-7**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` 均 `BUILD SUCCESSFUL`；首次編譯失敗紀錄與修正方式已如實揭露（D.1 節）

---

## H. 已知遺留項（不在本輪 scope）

1. **`startRenderThread` 併發 race**（option C 明列留 Sprint 12）：若 `stopRenderThread` join(500) 超時，舊 thread 可能仍持 EGL context；此時下次 `onSurfaceTextureAvailable` 進 `startRenderThread`，`running=false` 讓 `if (running) return` 不生效，會試圖啟新 thread → 兩個 render thread 並存。
2. **`eglSwapBuffers` 失敗 silent break**：scope 未列；保留既有行為，留待後續 round 盤點。
3. **`onSurfaceTextureDestroyed` 直接 `stopRenderThread`**：join 超時已有告警（本輪 C-3 完成），但 TextureView 生命週期 + EGL 資源洩漏觀測仍薄弱，Sprint 12 可再審。
4. **120 Hz 以上的 thermal / battery profile**：尚未量測；Sprint 12 視需要追加「每 N tick 畫一幀」調速。

---

## I. 代行鏈條紀錄

- 2026-04-19 **董事長**裁示 TTS / GL 均採 option C、並行執行、自己做自己審
- **CodeX A（玄樞）** 額度耗盡 → **雪鑰** 代行 gate，並行審 TTS（自己做自己審）+ GL（審 鍵律）
- **CodeX B（衡鑑）** 額度耗盡 → **雪鑰** 代行 TTS 子題實作 + **鍵律** 代行 GL 子題實作
- 本 handoff 屬 **鍵律 → 雪鑰** 的 GL 子題回交；**與 TTS 子題並行，不互相 block**

```
雪鑰（代行玄樞 + TTS 實作）─┐
                            ├─→ S11-5 整體 READY（並行收齊後）
鍵律（代行 GL 實作）  ──────┘
```

---

**— 鍵律（CCB 代行）筆**

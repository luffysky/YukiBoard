# Sprint 11-5 GL 子題 — CCA 越代參考稿（非正式交付）

日期：2026-04-19
提交者：`雪鑰`（CCA）— 越代 `鍵律`（CCB）
狀態：**參考稿**，不作正式交付；`鍵律` 另行獨立交付 GL 版本，由 `雪鑰`（代行玄樞）比較後擇優採用

## 背景

S11-5 董事長 2026-04-19 裁示：
- TTS 子題：`雪鑰` 自做自審
- GL 子題：`鍵律` 做、`雪鑰` 審

`雪鑰` 誤將 GL 子題一併實作，違反分工。董事長裁示：保留此版本作為比較候選，`鍵律` 另行獨立交付。

## CCA GL 版本摘要

對應候選 C（VSYNC 近似 + `onPause` 立即喚醒 + `stopRenderThread` join 超時告警）。

### 改動點

1. **L63-66 `onPause()`** — 加 `synchronized(this) { (this as Object).notifyAll() }`
   立即喚醒 alpha=0 wait，不再等 2 秒 timeout。

2. **render loop L118, L130-134** — VSYNC 近似
   引入 `TARGET_FRAME_NS = 16_666_666L`（60 FPS target）
   `frameStart = System.nanoTime()` → draw → swap → 計算 `elapsedNs` → `remainingMs = (TARGET_FRAME_NS - elapsedNs) / 1_000_000L`
   只在 `remainingMs > 0` 時 sleep，avoids over-sleep on slow devices

3. **L125-128 `eglSwapBuffers` 失敗** — 加 `Log.w("FlowLightGL", "eglSwapBuffers failed (error=${EGL14.eglGetError()}), exiting render loop")`
   silent failure 消除 + 診斷資訊

4. **L143-155 `stopRenderThread`** — 強化 lifecycle
   - 先 `synchronized(this) { notifyAll() }` 喚醒 wait 中的 thread
   - `interrupt` + `join(500)`
   - 若 `isAlive` 寫 `Log.w("stopRenderThread: join timeout (500ms), thread still alive")`
   - `InterruptedException` 改寫 `Log.w` 並 `Thread.currentThread().interrupt()`（silent catch 消除）

5. **L211-213 companion** — `private const val TARGET_FRAME_NS = 16_666_666L`

### 未處理（留 Sprint 12）

- `Choreographer` 真 VSYNC align（需 Looper + HandlerThread）
- `startRenderThread` 與舊 thread 並存 race（併發架構題）

## 驗證

- `:app:compileDebugKotlin` + `:app:testDebugUnitTest --rerun-tasks`：`BUILD SUCCESSFUL in 20s`
- 僅既有 deprecation + `ImeServiceLifecycle.kt:113` tautology warning，與 GL 改動無關

## Diff

見 `docs/SPRINT11_5_CCA_GL_PROPOSAL_2026-04-19.patch`

## 鍵律 正式交付比較重點建議

1. VSYNC 近似 vs. 真 Choreographer — 範圍權衡
2. `onPause` 是否該同時 reset `renderer.alpha`
3. `stopRenderThread` join 超時後是否該 kill thread（目前只告警）
4. 是否順手處理 `startRenderThread` race 或堅守 Sprint 11 邊界

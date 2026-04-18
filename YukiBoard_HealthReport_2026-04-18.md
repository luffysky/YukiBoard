# YukiBoard 全專案健康度與 Bug 修復清單
**掃描日期：** 2026-04-18
**版本：** versionCode 164 / versionName 0.10.0
**範圍：** 290 個 Kotlin 檔案、約 5.9 萬行 Kotlin 代碼
**Sprint 狀態：** 已完成 Sprint 1-9 重構

---

## 0. TL;DR — 一頁總覽

| 維度 | 分數 | 說明 |
|---|---|---|
| **整體健康度** | **7.5 / 10** | 中上，有重構痕跡但工程衛生待補 |
| 架構紀律 | 8/10 | SSOT 收斂、Cleanup 結構化 |
| 狀態管理 | 7/10 | StateFlow + Coordinator 模式正確 |
| **測試覆蓋** | **1/10** | ⚠️ **0 個測試檔** |
| 文檔豐富度 | 10/10 | 過多，需要歸檔 |
| 模組化 | 7/10 | 物理拆分到位、邏輯仍是 god object |
| **例外處理** | **4/10** | ⚠️ 129 處 silent catch |
| Lifecycle 治理 | 8/10 | hook 結構化做得好 |
| **金流安全** | **2/10** | 🔴 商家 HashKey 寫死 APK |

**最高優先級三件事：**
1. 🔴 **NewebpayClient 改後端簽章**（P0 安全）
2. 🟠 **InputRouter / SmartInputManager / 各 Engine 補單元測試**（P1 工程基礎）
3. 🟡 **129 處 silent catch 至少改成 `Log.w`**（P2 可觀測性）

---

## 1. Bug 清單（按嚴重度排序）

### P0 — 立即修，影響金錢/帳號安全

#### BUG-001 🔴 藍新金流 HashKey/HashIV 寫死在 APK
**檔案：** `app/src/main/java/com/snowrealm/yukiboard/currency/NewebpayClient.kt:20-22`
**檔案：** `app/build.gradle:63-65`

```kotlin
private val merchantId = BuildConfig.NEWEBPAY_MERCHANT_ID
private val hashKey = BuildConfig.NEWEBPAY_HASH_KEY  // ← 寫死在 APK
private val hashIv = BuildConfig.NEWEBPAY_HASH_IV    // ← 寫死在 APK
```

**問題：**
- 藍新金流的 HashKey + HashIV 等同商家私鑰
- 寫進 BuildConfig 等於寫進 APK，任何人 apktool/jadx 反編譯都能拿到
- 拿到後可以**偽造任意金額的合法簽章**送進你的 NotifyURL，使用者沒付錢但餘額會被加值

**正確做法：**
```
[Android client]
  ↓ POST /api/payment/create  { amount, orderId }
[Supabase Edge Function（持有 HashKey/IV）]
  ↓ AES + SHA256 簽章
  ← { tradeInfo, tradeSha, postUrl }
[Android client] 把這些參數丟進 WebView POST
```

**修復步驟：**
1. 把 `buildPaymentParams` 整個邏輯搬到 Supabase Edge Function（TypeScript）
2. Android 端只做 `fetch("/api/payment/create")` → 拿回簽章好的參數
3. 從 `app/build.gradle` 拿掉 `NEWEBPAY_HASH_KEY` / `NEWEBPAY_HASH_IV` 兩個 buildConfigField
4. 既有的 keystore 要把這兩個 key 視為已洩漏，**藍新後台立即輪換**

**影響範圍：** 整個 Z 幣儲值鏈路必須跟著改。建議跟 Supabase 端的 `payment_orders` table + RLS 一起做。

---

#### BUG-002 🟠 多個 API key 寫死在 APK
**檔案：**
- `gif/GiphyApiClient.kt:19` — `BuildConfig.GIPHY_API_KEY`
- `weather/WeatherManager.kt:28` — `BuildConfig.OPENWEATHER_API_KEY`
- `data/SupabaseClient.kt:25-26` — `BuildConfig.YUKIBOARD_SUPABASE_URL` / `YUKIBOARD_SUPABASE_ANON_KEY`
- `claw/provider/AIProviderManager.kt:62-63` — `BuildConfig.ANTHROPIC_API_KEY` / `OPENAI_API_KEY`

**問題：**
- Giphy / OpenWeather 是免費 quota，被別人盜用會導致你的 quota 爆掉、服務中斷
- AI key（OpenAI / Anthropic）若有 build-time 注入，被反編譯後**會被別人花你的錢**
- Supabase ANON_KEY 雖然設計上是公開的，但仍需確認 RLS 政策完整

**修復順位：**
1. **AI key**（OpenAI/Anthropic）：必須改成 user-input（你註解寫了「正式版由使用者自行輸入」，確認 release build 是空字串即可）
2. **Giphy/OpenWeather**：改用後端 proxy，前端只打你自己的 endpoint
3. **Supabase ANON_KEY**：確認 RLS policy 有「authenticated users only」這類限制（可保留 ANON_KEY 在 client）

**檢查清單：**
```bash
# release build 前掃一次，確認這些 field 是空字串
./gradlew assembleRelease
unzip -p app/build/outputs/apk/release/*.apk classes.dex | strings | grep -E "sk-|claude_|giphy_"
```

---

### P1 — 影響穩定性與資源

#### BUG-003 🟠 InputRouter.release() 漏放多個 engine
**檔案：** `app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt:429-431`

```kotlin
fun release() {
    userDict.release()
    // ❌ 缺：englishEngine、bopomofoEngine、pinyinEngine、cangjieEngine、
    //       speedCangjieEngine、japaneseEngine、koreanEngine、latinEngines.values
}
```

**問題：**
- IME service onDestroy 時呼叫 `inputRouter.release()`，但只清了 userDict listener
- `bopomofoEngine` (ChewingInputEngine) 內部可能持有 SQLite cursor、warmUp 過的索引快取
- `latinEngines` LinkedHashMap 可能堆積到 8 個 LatinInputEngine 實例
- 所有 lazy engine 隨 service 一起被 GC，但**過程中如果有外掛持有引用會延後回收**

**修復：**
```kotlin
fun release() {
    userDict.release()
    runCatching { (englishEngine as? AutoCloseable)?.close() }
    runCatching { (bopomofoEngine as? AutoCloseable)?.close() }
    if (this::pinyinEngine.isLazyInitialized) (pinyinEngine as? AutoCloseable)?.close()
    // ... 其他 lazy engine
    latinEngines.values.forEach { (it as? AutoCloseable)?.close() }
    latinEngines.clear()
}
```

需要先讓各 Engine 實作 `AutoCloseable`（或自定義 `release()`），裡面釋放 SQLite cursor、cancel coroutine、清 cache。

---

#### BUG-004 🟠 0 個 CoroutineExceptionHandler，46 處 launch 失敗會無聲消失
**檔案：** `service/YukiBoardIMEService.kt:158`

```kotlin
internal val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
// ❌ 缺：CoroutineExceptionHandler
```

**問題：**
- 全專案 0 個 CoroutineExceptionHandler
- 46 處 `serviceScope.launch { ... }`，任一個拋例外都只會印 logcat，不會通知你
- 上線後使用者遇到問題你完全收不到信號

**修復：**
```kotlin
private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    android.util.Log.e("IME", "uncaught coroutine exception", throwable)
    // 進階：上報 Firebase Crashlytics / 自家 telemetry
    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
        .recordException(throwable)
}

internal val serviceScope = CoroutineScope(
    Dispatchers.Main + SupervisorJob() + coroutineExceptionHandler
)
```

你已經引了 Firebase BOM，順手接 Crashlytics 即可（build.gradle 加 `firebase-crashlytics`）。

---

#### BUG-005 🟠 129 處 silent catch 吞光所有錯誤
**全專案掃描結果：**
- `catch (_: Exception) { }` — **129 處**
- `catch (e: Exception) { ... }` — 108 處（其中部分有 `Log.w`）

**典型反例：**
```kotlin
// service/ImeServiceLifecycle.kt:88-99
try { ... } catch (_: Exception) { }
```

**問題：**
- IME 任何 InputConnection 異常、SharedPref 寫入失敗、感測器讀取失敗都被吞掉
- debug 期看不到問題，prod 期變成「使用者說鍵盤怪怪的、但你查不到 log」
- 這是一個專案技術債的**最可靠指標**

**修復策略（不要一次改光）：**
1. 寫一個專案級 helper：
   ```kotlin
   inline fun <T> swallow(tag: String = "IME", reason: String = "", block: () -> T): T? {
       return try { block() } catch (e: Exception) {
           android.util.Log.w(tag, "swallowed: $reason", e); null
       }
   }
   ```
2. 用 sed 半自動替換：
   ```bash
   # 不要 100% 自動，因為有些 catch block 內有額外邏輯
   # 但 `catch (_: Exception) {}` 這種純空 block 可以批量處理
   ```
3. 重點區優先（按目錄）：`service/` → `app/ime/` → `input/` → `claw/` → 其他

---

#### BUG-006 🟡 onCreateInputView 中 bgUpdateReceiver 註冊邏輯有競態風險
**檔案：** `service/YukiBoardIMEService.kt:336-451`

```kotlin
override fun onCreateInputView(): View {
    bgUpdateReceiver?.let {
        try { unregisterReceiver(it) } catch (_: Exception) {}
    }
    bgUpdateReceiver = null
    // ... 250 行後 ...
    if (prefs.floatingModeEnabled) {
        // ...
        return wrapInFloatingMode(rootContainer)  // ❌ 早 return，跳過 register
    }
    // ...
    registerReceiver(bgUpdateReceiver, bgFilter, ...)  // line 451
}
```

**問題：**
- 懸浮模式分支在 line 420 提早 return，**跳過 line 451 的 receiver 註冊**
- 結果：懸浮模式時改背景圖片不會即時刷新
- 這跟你過去 `YukiBoard_BgFullscreen_Rotation_Sync.md` 修的 bug 是同類型

**修復：** 把 receiver 註冊提前到 floating 分支之前，或兩個分支都呼叫一次。

---

#### BUG-007 🟡 onCreate 中對 null callback 賦值（脆弱設計）
**檔案：** `service/YukiBoardIMEService.kt:319-328`

```kotlin
initExternalEngines()  // line 319 — 此處才建立 clawResultFloatingPanel
initFraudModel()       // line 322
clawResultFloatingPanel?.onInsertToInput = { text -> ... }  // ✅ 目前順序對
```

**問題：**
- 目前順序對，但用 `?.` 等於把「初始化失敗時 callback 默默消失」當正常流程
- 任何人未來改 `onCreate` 順序，這個 callback 會無聲失效
- 14 個 `lateinit var` 都有類似的順序耦合風險

**修復：**
- 短期：把這段 callback 設定移進 `initExternalEngines()` 內部，跟物件建立同地點綁定
- 長期：把 14 個 lateinit var 包成一個 `ImeDependencyGraph` data class，onCreate 一次性 build

---

#### BUG-008 🟡 handleKeyPress VOICE / HANDWRITING 分支跳過候選更新
**檔案：** `service/YukiBoardIMEService.kt:745-757, 884-921`

```kotlin
KeyboardView.KEYCODE_VOICE -> {
    toggleVoiceInput()
    return  // ← 跳過 line 884 之後的 inputUiSync.renderCandidates
}
KeyboardView.KEYCODE_HANDWRITING -> {
    if (...) closePanel() else handlePanelToggle(PanelType.HANDWRITING)
    return  // ← 同樣
}
```

**澄清：** Kotlin `try-finally` 中的 `return` **會**走 finally，所以 `endBatchEdit()` 會執行，IC 不會卡。但兩個 panel toggle key 直接 return，會：
- 跳過 candidates 重繪
- 跳過 AI candidate 請求

**評估：** 對 VOICE / HANDWRITING 來說**邏輯上是合理的**（panel 自己接管 UI），但跟其他 panel toggle（例如 SETTINGS、THEME 走完整 when）**不對稱**。

**建議：** 不算 bug，但加一條 `// 故意不走 candidates render：panel 接管 UI` 註解避免後人誤改。

---

### P2 — 健康度與可維護性

#### BUG-009 🟡 KeyboardGLSurfaceView 用 Thread.sleep(16) 實作 16fps
**檔案：** `animation/gles/KeyboardGLSurfaceView.kt:126`

```kotlin
try { Thread.sleep(16) } catch (_: InterruptedException) { break }
```

**問題：**
- Android GL 應該用 `setRenderMode(RENDERMODE_CONTINUOUSLY)` 讓 EGL 排程
- 自己 sleep 16ms 等於跑在某個 thread 上做手動 60fps，VSYNC miss、battery 多燒
- Doze mode / 低電量模式下這 thread 不會睡

**修復：** 移除自製 frame loop，改用 GLSurfaceView 內建的 RENDERMODE。

---

#### BUG-010 🟡 GoogleCloudTtsEngine.kt 主執行緒 sleep
**檔案：** `speech/GoogleCloudTtsEngine.kt:221`

```kotlin
Thread.sleep(durationMs + 100)
```

**問題：** TTS 播放完畢用 `Thread.sleep` 等待，如果在 main thread 會卡 UI；如果在 IO thread 也是 thread 浪費。
**修復：** 改用 `kotlinx.coroutines.delay()` + suspending function。

---

#### BUG-011 🟡 callbackFlow / SharedFlow 0 處使用
**全專案掃描：**
- StateFlow / MutableStateFlow：18 處 ✅
- callbackFlow / SharedFlow：0 處 ❌

**問題：**
- `ShakeDetector.setOnShakeListener { ... }`、`GyroscopeCursorController.setOnCursorMoveListener { ... }`、`KeyboardView.onPanelSelected = { ... }` 這些都是傳統 callback
- 應該包成 `callbackFlow` 變成可組合、可 cancel、可 testable 的 Flow

**建議：** 不急，但下次重構 ShakeDetector / GyroController 時順便做。

---

#### BUG-012 🟡 0 個技術債標記（TODO/FIXME）
**全專案掃描結果：** 真 TODO 只有 2 個（KeyboardBackgroundView.kt:97-98）

**問題：** 這不是好事，是「沒在記」。Sprint 文檔裡記了一堆 known issue，但 code 裡完全沒對應的 `// TODO(sprint10): ...` 標記。下次重構時找不到上次掃過的點。

**建議：** 從這份報告開始，每個未修的 bug 在 code 上對應加 `// TODO(YB-XXX): ...` 標記。

---

## 2. 健康度詳細分析

### 2.1 ✅ 做得好的地方

#### 架構意識強
- Sprint 1-9 的重構軌跡清楚 — 從 SSOT 收斂（ImeSessionCoordinator）、到 panel binder 邊界（ImePanelBindings）、到 InputUiSync 的 render ownership 集中化
- 註解風格健康：`// Sprint 5 Round 3: isFloatingMode local field removed; SSOT = imeCoordinator.uiState` — 不只改，還留下「為什麼改」

#### Cleanup 結構化
`DestroyCleanup` / `FinishInputViewCleanup` / `FinishInputCleanup` 用 data class 包 lifecycle hook，傳進 `ImeSessionHooks` 統一執行 — 比大部分 Android IME 的「onDestroy 寫一坨 80 行」進步

#### 分層清晰
```
input/engine/      — 輸入引擎（注音/拼音/倉頡/日韓）
input/router/      — InputRouter 路由
input/smart/       — 智慧輸入（個人字典、句子預測、習慣追蹤）
input/dictionary/  — RIME / English / User 字典
input/handwriting/ — MLKit 手寫
input/swipe/       — 滑動輸入
claw/              — AI Provider 抽象
yuki/              — 角色互動（吐槽、語氣偵測、加密訊息）
remote/            — 藍牙 + WiFi 鍵盤
```
每層職責明確，沒有 cross-layer 隨意呼叫

#### 依賴管理克制
build.gradle 只有 ~25 個必要 lib，沒有 Hilt/Dagger 過度抽象，沒有引第三方 UI framework — 對個人專案是對的選擇

#### 有 Sprint Gate 文化
`Sprint6-All-Round-Gate.md` ~ `Sprint9-All-Round-Gate.md` + `IME_SMOKE_CHECKLIST.md`（23K！）— 在獨立開發專案裡非常罕見

---

### 2.2 ⚠️ 健康但有警訊的地方

#### god object 還沒拆完
- `YukiBoardIMEService.kt` 1041 行 + 6 個 partial extension 檔（總 3799 行）
- 雖然已經切成 `ImeServiceInput` `ImeServicePanels` `ImeServiceCallbacks` 等
- **但這只是物理拆分，邏輯上還是 god object** — extension function 的 `this` 都是 service
- 14 個 `lateinit var` + 20+ 個 nullable field，service 仍是中央依賴池
- 真正的重構應該是 `ImeDependencyGraph` 這種 immutable container

#### Field 初始化順序脆弱
- onCreate 裡 14 個 `lateinit var` 必須按特定順序初始化
- 沒有編譯期保證，任何人重排 onCreate 就可能 crash on first use
- Sprint 7 的 `prefs singleton mutable` 文檔已注意到這問題，但沒徹底解

#### silent catch 氾濫（已列為 BUG-005）
129 處 `catch (_: Exception) {}`，是專案技術債最可靠的指標

#### 文檔密度嚴重 > 代碼密度
- `docs/` 目錄 ~150 個 markdown，~1.8MB
- `RULE/` 目錄 7 個規範檔
- 根目錄還有 ~25 個 sprint/plan/gate 文件
- Kotlin 代碼 290 個檔案
- **文檔/代碼比 ≈ 1:1.5**（健康專案是 1:5 ~ 1:10）

**建議行動：**
```bash
mkdir -p docs/archive/sprint1-9
mv docs/SPRINT[1-9]_*.md docs/archive/sprint1-9/
mv docs/CODEX_HANDOFF_*.md docs/archive/sprint1-9/
mv docs/CODEXA_*.md docs/archive/sprint1-9/
mv docs/CODEXB_*.md docs/archive/sprint1-9/
mv CODEXA_*.md docs/archive/sprint1-9/
mv CODEXB_*.md docs/archive/sprint1-9/
mv SPRINT*_*.md docs/archive/sprint1-9/
mv Sprint[6-9]-All-Round-Gate.md docs/archive/sprint1-9/
mv daily_works_*.md docs/archive/
mv docs/daily_works_*.md docs/archive/
```

#### 沒有自動化測試（已列為頭號優先）
- `find app/src/test app/src/androidTest -name "*.kt"` → 0 個
- build.gradle 引了 junit 跟 espresso 但沒有測試
- IME 這種狀態極複雜的 service，每個 Sprint 都靠 23K 的手動 smoke checklist 跑
- 對純邏輯類別（InputRouter / SmartInputManager / 各 Engine）寫測試成本最低、ROI 最高

#### 命名與註解中英混雜
- `Sprint 5 Round 3: isFloatingMode local field removed；SSOT = imeCoordinator.uiState.isFloatingMode`
- 全形分號、中文 + 英文 + 程式碼變數混用
- 中文母語團隊 OK，但未來有英文 contributor 或要 open source 會卡

---

### 2.3 同類專案橫向對比

| 項目 | YukiBoard | 一般獨立 IME 專案 | 商用 IME（Gboard 等） |
|---|---|---|---|
| 架構紀律 | 7/10 | 4/10 | 9/10 |
| 狀態管理 | 7/10（SSOT 有意識） | 3/10（global 一坨） | 9/10 |
| 測試覆蓋 | 1/10 | 1/10 | 9/10 |
| 文檔豐富度 | 10/10（過多） | 2/10 | 7/10 |
| 模組化 | 7/10 | 5/10 | 9/10 |
| 例外處理 | 4/10 | 3/10 | 9/10 |
| Lifecycle 治理 | 8/10 | 4/10 | 9/10 |
| **金流安全** | **2/10**（HashKey 寫死） | N/A | 10/10 |

---

## 3. 升級建議（按 ROI 排序）

### 短期（一個週末可完成）

#### 1. 補單元測試骨架（最高 ROI）
建立 `app/src/test/java/com/snowrealm/yukiboard/`，先寫這幾個檔：

**`InputRouterTest.kt`**（建議 ~30 個 case）
```kotlin
class InputRouterTest {
    private lateinit var router: InputRouter
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setup() { router = InputRouter(context) }

    @Test fun `初始模式為 ENGLISH`() { ... }
    @Test fun `switchTo BOPOMOFO 時 commit 既有 composing`() { ... }
    @Test fun `AUTO 模式下 qwerty 字元累積到 buffer`() { ... }
    @Test fun `processSpace 在空 composing 時回傳 Idle`() { ... }
    @Test fun `reset 清空 autoQwertyBuffer 與 autoBopomofoBuffer`() { ... }
    @Test fun `latinEngines 超過 MAX 時 LRU 淘汰最舊`() { ... }
    @Test fun `release 不會拋例外`() { ... }
    // ... 其他 23 個
}
```

**`SmartInputManagerTest.kt`**（建議 ~20 個 case）
- onSentenceCommitted 觸發 PhrasePredictor.learn
- adaptiveDictEnabled = false 時不更新 AdaptiveDictionary
- getEmotionStickerTags 在情緒詞彙時返回非空 tags

**`CandidateManagerTest.kt`**（建議 ~10 個 case）
- updateCandidates 後可正確讀取
- clear() 清空所有狀態
- selectCandidate index 越界時不崩

**`ChewingInputEngineTest.kt`**（建議 ~15 個 case）
- 標準注音輸入序列
- raw bopomofo mode 切換
- forceCommit 行為

**預期效益：** 下次重構信心翻倍，IME_SMOKE_CHECKLIST.md 可瘦身一半

#### 2. 修 BUG-001（藍新金流）
P0 必修。設計改成 Edge Function，~1 天工作量

#### 3. 加 CoroutineExceptionHandler + Crashlytics
30 分鐘工作量，prod 端立刻有可觀測性

#### 4. silent catch 批量改 Log.w
寫個 helper function `swallow { ... }`，從 service/ 目錄開始替換

#### 5. docs 歸檔
把 Sprint 1-9 的歷史文件搬到 `docs/archive/`，root 只留 active

---

### 中期（一個 Sprint 可完成）

#### 6. InputRouter.release() 補完
所有 engine 實作 `AutoCloseable`，release() 統一呼叫

#### 7. 拆 ImeDependencyGraph
把 14 個 lateinit var + 20 個 nullable 包成 immutable container，service 只持有 ref

#### 8. UI 狀態用 Compose 重寫設定頁
`SettingsActivity` 460+ 行 + `BackgroundSettingsSection` 1236 行已經超過 view-binding 能管的範圍。Compose 對這種設定頁是甜蜜點

#### 9. 引入 Detekt + ktlint
靜態分析能直接抓出 silent catch、long function、complex condition 這類債

```kotlin
// build.gradle
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.5"
}

detekt {
    config.setFrom("$projectDir/detekt-config.yml")
    autoCorrect = true
}
```

#### 10. 升 Kotlin 2.0 + K2 編譯器
你目前 build.gradle 沒寫 Kotlin 版本，假設是 1.9.x。K2 可加快編譯 30-50%

---

### 長期（v0.11+ 規劃）

#### 11. AI 預測整合到 candidate bar
你的工作筆記提過「embedding AI predictions directly into the candidate word bar」— 這個目前還沒做。建議在補完測試後再開動

#### 12. 拆 multi-module
- `:core-input`（所有 engine + router，純 Kotlin）
- `:core-smart`（SmartInput / Predict）
- `:feature-claw`（AI）
- `:feature-yuki`（角色）
- `:feature-remote`（藍牙/WiFi）
- `:app`（IME service + UI）

multi-module 後可以分別跑 `:core-input:test`，CI 時間從 5 分鐘降到 30 秒

#### 13. Compose Multiplatform 鋪路
如果 SnowRealm 生態系未來要做 iOS / Desktop 版本，Compose Multiplatform 比 React Native 路線更順 — 但這是 v1.x 後再考慮

---

## 4. 修復優先順序總表

| ID | 標題 | 嚴重度 | 預估工時 | Sprint |
|---|---|---|---|---|
| BUG-001 | 藍新金流 HashKey 寫死 | 🔴 P0 | 1 天 | Sprint 10 |
| BUG-002 | API key 寫死 | 🟠 P1 | 半天 | Sprint 10 |
| BUG-003 | InputRouter.release 漏 | 🟠 P1 | 半天 | Sprint 10 |
| BUG-004 | 0 個 ExceptionHandler | 🟠 P1 | 30 分鐘 | Sprint 10 |
| BUG-005 | 129 處 silent catch | 🟠 P1 | 2 天（漸進） | Sprint 10-11 |
| BUG-006 | floating 路徑漏註冊 receiver | 🟡 P2 | 1 小時 | Sprint 10 |
| BUG-007 | onCreate 順序耦合 | 🟡 P2 | 半天 | Sprint 11 |
| BUG-008 | VOICE/HANDWRITING return 不對稱 | 🟡 P3 | 5 分鐘加註解 | Sprint 10 |
| BUG-009 | GL Thread.sleep frame loop | 🟡 P2 | 1 小時 | Sprint 11 |
| BUG-010 | TTS Thread.sleep | 🟡 P2 | 30 分鐘 | Sprint 11 |
| BUG-011 | callbackFlow 0 處使用 | 🟢 P3 | 漸進 | Sprint 12+ |
| BUG-012 | 沒記技術債 | 🟢 P3 | 隨手 | 持續 |
| **TEST-001** | **InputRouter 單元測試** | 🟠 **P1** | 1 天 | Sprint 10 |
| **TEST-002** | **SmartInputManager 測試** | 🟠 P1 | 半天 | Sprint 10 |
| **TEST-003** | **各 Engine 測試** | 🟠 P1 | 1 天 | Sprint 11 |
| ARCH-001 | docs 歸檔 | 🟡 P2 | 30 分鐘 | Sprint 10 |
| ARCH-002 | ImeDependencyGraph | 🟡 P2 | 2 天 | Sprint 11 |
| ARCH-003 | Compose 設定頁 | 🟢 P3 | 1 週 | Sprint 12 |
| ARCH-004 | Detekt + ktlint | 🟡 P2 | 2 小時 | Sprint 10 |
| ARCH-005 | 升 Kotlin 2.0 | 🟢 P3 | 半天 | Sprint 11 |
| ARCH-006 | multi-module 拆分 | 🟢 P3 | 1 週 | Sprint 13 |

---

## 5. Sprint 10 建議規劃

基於以上分析，建議 Sprint 10 主題定為 **「工程基礎建設補強」**，不做新功能：

### Round 1（前 2 天）— 安全與可觀測性
- BUG-001 藍新金流 Edge Function 改造
- BUG-002 API key 全面盤點
- BUG-004 + Crashlytics 接通
- ARCH-001 docs 歸檔
- ARCH-004 Detekt 引入

### Round 2（中 3 天）— 測試骨架
- TEST-001 InputRouterTest 30 個 case
- TEST-002 SmartInputManagerTest 20 個 case
- BUG-003 InputRouter.release 補完（順便跑剛寫的測試驗證）

### Round 3（後 2 天）— 例外處理
- BUG-005 silent catch 從 service/ 開始替換
- BUG-006 floating receiver 修
- BUG-008 VOICE/HANDWRITING 加註解
- 寫 Sprint10-All-Round-Gate.md 收尾

---

## 6. 評語

這是一個**「在認真治理的個人專案」**，不是「下班隨便寫」的等級。重構痕跡明顯，Sprint 制度跑得起來，這在獨立開發領域是上位 25%。

但有典型的「**有規劃癖、缺工程癖**」特徵：規劃文件很多，gate 規範完整，但測試是 0、silent catch 一堆、god object 拆分只是物理拆分。這跟你的 Insight、毛行天下、OrderEase 等其他專案模式一致 — 你在規劃和架構決策上很強，但在工程衛生細節上會妥協。

這不是壞事，是創辦人/產品型開發者的典型 profile。但累積到 v1.0 上線後會吃到反作用力（每次重構都要靠 23K 的 smoke checklist 護航）。

**最高 ROI 動作：** 寫 InputRouter 的 30 個單元測試。一個下午，下次重構信心翻倍。

---

*本報告由全專案代碼掃描產生，建議 commit 進 `docs/audits/` 作為 baseline，下次掃描時對比。*

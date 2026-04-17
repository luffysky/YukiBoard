# Sprint 7-1 Network Entry Implementation

任務 ID：`S7-1-CCB-NETWORK-ENTRY-IMPLEMENTATION`

日期：2026-04-18

身份：Claude Code B

依據：

- `Sprint7-All-Round-Gate.md`（Round S7-1 `ACTIVE`，`CodeX A` 判定 `READY`）
- `docs/SPRINT7_0_INTEGRATION_AUDIT_2026-04-18.md`（CXB total audit）
- `docs/SPRINT7_0_IME_INTEGRATION_AUDIT_2026-04-18.md`（CCB IME-side audit）
- `docs/SPRINT7_0_SETTINGS_AUTH_PROVIDER_SYNC_AUDIT_2026-04-18.md`（CCA settings-side audit）
- `CodeX A` 在 `S7-0` 的三條 ruling（見 §5 合規檢核）

## 0. 範圍宣告

- 本輪只做 `AppHttpClient / Network Entry` 最小安全收斂
- 不碰 session / auth / provider state（留給 `S7-2`）
- 不碰 gateway 入口（留給 `S7-3`）
- 不做 singleton 升級、不做 section 化、不做 bugfix

## 1. 變更總覽

### 1.1 新增檔案

| 路徑 | 角色 |
| --- | --- |
| `app/src/main/java/com/snowrealm/yukiboard/app/net/AppHttpClient.kt` | Network entry 單一 owner：提供 `Profile` enum + `newBuilder(profile)` factory |

### 1.2 遷移檔案（25 個 caller，共 26 個 `OkHttpClient.Builder()` callsite）

以下所有檔案原先皆直接 `OkHttpClient.Builder().connectTimeout(...).readTimeout(...)...build()`，
現統一改走 `AppHttpClient.newBuilder(Profile.XXX).build()`；caller 仍各自 `.build()`，
未共享 instance、未共享 ConnectionPool。

| 檔案 | 原 timeout（connect/read/write） | 新 Profile | 備註 |
| --- | --- | --- | --- |
| `auth/SnowRealmAuthClient.kt` | 10/15/(default 10) | `STANDARD` | write 10→15 較寬鬆，安全 |
| `data/SupabaseClient.kt` | 10/15/(default 10) | `STANDARD` | write 10→15 較寬鬆，安全 |
| `data/CloudSyncManager.kt` | 10/10/(default 10) | `SHORT` | 純 Builder 替換，**未動 gateway 結構** |
| `data/InsightApiClient.kt` | 15/30/15 | `DEFAULT` | 完全同策略 |
| `claw/provider/OpenAIProvider.kt` | 30/60/30 | `STREAMING` | 完全同策略 |
| `claw/provider/AnthropicProvider.kt` | 30/60/30 | `STREAMING` | 完全同策略 |
| `claw/provider/GeminiProvider.kt` | 30/60/30 | `STREAMING` | 完全同策略 |
| `claw/provider/GroqProvider.kt` | 15/30/15 | `DEFAULT` | 完全同策略 |
| `claw/provider/SnowRealmProvider.kt` | 30/90/30 | `STREAMING_LONG` | 完全同策略（Insight streaming proxy） |
| `claw/provider/GeminiNanoProvider.kt` | 5/30/(default 10) | `DEFAULT` + `.connectTimeout(5s)` override | 本地 AICore endpoint 保留 5s connect |
| `speech/FasterWhisperSpeechEngine.kt` | 15/120/60 | `DEFAULT` + `.readTimeout(120s).writeTimeout(60s)` override | 自架 Whisper server 長推論 |
| `speech/GoogleCloudTtsEngine.kt` | 15/30/(default 10) | `DEFAULT` | write 10→15 較寬鬆，安全 |
| `speech/WhisperSpeechEngine.kt` | 30/60/60 | `STREAMING` + `.writeTimeout(60s)` override | WAV 上傳需 60s write |
| `weather/WeatherManager.kt` | 10/10/(default 10) | `SHORT` | 完全同策略 |
| `gif/GiphyApiClient.kt` | 10/10/(default 10) | `SHORT` | 完全同策略 |
| `gif/GifImageCache.kt` | 10/15/(default 10) | `STANDARD` | write 10→15 較寬鬆，安全 |
| `input/langpack/LanguagePackManager.kt` | 15/30/(default 10) | `DEFAULT` | write 10→15 較寬鬆，安全 |
| `sticker/InsightStickerApi.kt` | 10/15/(default 10) | `STANDARD` | write 10→15 較寬鬆，安全 |
| `sticker/StickerImageCache.kt` | 10/15/(default 10) | `STANDARD` | write 10→15 較寬鬆，安全 |
| `security/FraudOpenDataSync.kt` | 15/30/(default 10) | `DEFAULT` | write 10→15 較寬鬆，安全 |
| `security/FraudModelManager.kt` | 30/60/(default 10) | `STREAMING` | write 10→30 較寬鬆，安全 |
| `ui/settings/section/SpeechSettingsSection.kt` (Whisper test, line 295) | 15/15/(default 10) | `STANDARD` | write 10→15 較寬鬆，安全 |
| `ui/settings/section/SpeechSettingsSection.kt` (Faster-Whisper test, line 340) | 10/10/(default 10) | `SHORT` | 完全同策略 |
| `ui/panel/DictionaryPanelView.kt` | 10/10/(default 10) | `SHORT` | 完全同策略 |
| `ui/panel/LangLearnPanelView.kt` | 10/10/(default 10) | `SHORT` | 完全同策略 |
| `app/ime/ImeImageCommitter.kt` | 10/15/(default 10) | `STANDARD` | 唯一 IME 直屬 callsite |

全部 25 個 caller 均僅改 `OkHttpClient.Builder()` 段與 import，**未改呼叫語意、未改 header、未動 interceptor**。

### 1.3 遷移後 `OkHttpClient.Builder()` 直呼點

```
$ grep -rn 'OkHttpClient\.Builder()' app/src/main/java
app/src/main/java/com/snowrealm/yukiboard/app/net/AppHttpClient.kt:58
```

只剩 `AppHttpClient` 自己一處，代表 Network entry 實質收斂。

## 2. AppHttpClient 設計取捨

### 2.1 採用

- Builder-returning factory（回傳 `OkHttpClient.Builder`）而非直接回傳 `OkHttpClient`
  - 原因：caller 仍需自己加 `Interceptor` / `cookieJar` / `cache` 彈性，且現在每家 auth header 作法不同，強行共用 instance 會破相容性。
  - 代價：暫時無 ConnectionPool 共享，但這屬於 `S7-2` 之後的題，不在本輪範圍。

### 2.2 Profile 分層

5 個 profile，對應 `S7-0` audit 中 25 個 caller 的 timeout 分布聚類：

| Profile | connect/read/write (s) | 對應場景 |
| --- | ---: | --- |
| `SHORT` | 10/10/10 | 快 REST（CloudSync、天氣、Giphy、字典/學習 panel） |
| `STANDARD` | 10/15/15 | 一般 REST（auth、Supabase、sticker、image commit、測試探針） |
| `DEFAULT` | 15/30/15 | 標準 gateway（Insight API、Groq、langpack、公開資料、TTS） |
| `STREAMING` | 30/60/30 | LLM streaming（OpenAI、Anthropic、Gemini、Whisper、fraud model） |
| `STREAMING_LONG` | 30/90/30 | Insight streaming proxy（SnowRealmProvider） |

3 個 outlier（連線層需要覆蓋特定欄位）允許在 `newBuilder(...).xxxTimeout(..., SECONDS)` 上 override。
這樣單一 profile 不會因極少數特例被推寬，caller 側特殊 timeout 仍可讀。

## 3. Build 驗證

```bash
$ ./gradlew.bat :app:compileDebugKotlin --no-daemon --rerun-tasks
BUILD SUCCESSFUL in 33s
18 actionable tasks: 1 executed, 17 up-to-date
```

- 編譯成功
- 10 個 pre-existing deprecation warning（`MediaType.create`、`connectionInfo.ipAddress`、
  `statusBarColor`、`THEME_DEVICE_DEFAULT_DARK` 等），**與本輪遷移無關**
- 無 new warning、無 error

## 4. A / B / C / D 回交

### A. 變更摘要

- 新增 `app/net/AppHttpClient.kt`：以 `object + Profile enum + newBuilder(profile)` 的形式，成為 network entry 單一 owner
- 25 caller 全數從 `OkHttpClient.Builder()` 改為 `AppHttpClient.newBuilder(Profile.XXX).build()`
- 3 個 outlier（GeminiNano / FasterWhisper / Whisper）用 profile + override 保留原 timeout 語意
- 完整編譯通過（`compileDebugKotlin` SUCCESSFUL）

主要影響檔案見 §1.2 表格。新增骨架：

- `AppHttpClient.Profile`（5 段 timeout policy）
- `AppHttpClient.newBuilder(profile)`（唯一 network entry）

### B. 風險

1. **3 caller write timeout 由 OkHttp default 10s 變為 15s 或 30s**
   - 涉及：SnowRealmAuthClient、SupabaseClient、GoogleCloudTtsEngine、GifImageCache、
     LanguagePackManager、InsightStickerApi、StickerImageCache、FraudOpenDataSync、
     FraudModelManager、SpeechSettingsSection（Whisper 探針）、ImeImageCommitter
   - 性質：**更寬鬆**，原本 10s 內失敗的 write 現在有更長容忍。**不會造成行為倒退**
   - 但：若上游 server 需要 fail-fast（例如刻意 10s timeout 做熔斷），此放寬可能延長 UI stuck
   - 處置：保留此決定。若 `CodeX A` 認為需收緊，可另增 `SHORT` 以外的 profile 或逐檔 override

2. **尚未共享 OkHttpClient instance**
   - 目前每個 caller 仍各自 `.build()`，意味著每個 caller 持有獨立 ConnectionPool / Dispatcher
   - 這不是 regression（原本就如此），但也沒趁 S7-1 一起解掉
   - 處置：留給 `S7-2+`。若強推 shared instance 會觸碰 `CloudSyncManager` 等 caller 的 header/cookie 邏輯，違反本輪禁令

3. **`CloudSyncManager` 只改 Builder，未動 gateway 結構**
   - 刻意為之，嚴守 `CodeX A` 在 `S7-0` §3 ruling（gateway 拆分只允 `S7-3`）
   - 但 `CloudSyncManager` 仍同時扮演 gateway + feature，本輪無改善
   - 處置：標記於 `S7-3` round 再處理

### C. 下一步建議

**建議優先**：請 `CodeX A` 對本輪 `S7-1` 下 `READY / NOT READY / LOCKED` 判定。

若 `READY`，則後續 round 順序應由 `CodeX A` 決定（候選如下，**不自排**）：

- 候選：`S7-2` Session / Auth / Provider 狀態收斂
- 候選：把 shared ConnectionPool / interceptor 納入 `AppHttpClient`（非必要 S7-2 前置）
- 候選：base URL / header policy 的下一層收斂（目前 `InsightApiClient.baseUrl`、provider 各自 URL 仍分散）

**本輪不碰**（嚴守禁令）：

- `YukiBoardIMEService.kt`、panel binder
- `AISettingsActivity` section 化
- `InviteCodeManager` singleton 化
- `CloudSyncManager` gateway 拆分
- bugfix

### D. 架構決策

| 決策 | 選擇 | 理由 |
| --- | --- | --- |
| Network entry 形式 | `object AppHttpClient` + `Profile enum` + `newBuilder(Profile)` | 單一 owner、不奪走 caller 的 interceptor 彈性 |
| 是否共享 `OkHttpClient` instance | 否（本輪） | 每個 caller 仍 `.build()`；shared instance 改動面積過大，留給後續 |
| Outlier 處理（3 caller） | profile + override | 避免為極少數特例推寬整個 profile |
| 遷移順序 | auth/data/provider → speech/weather/gif/sticker/security/langpack → UI/IME | 從核心 gateway caller 往外層搬，過程中隨時可停 |
| 是否一併收斂 header / auth | 否 | 超出本輪範圍，屬 `S7-2+` |
| Gateway 拆分 | 否 | `CodeX A` S7-0 ruling：只允 `S7-3` |

新增公開介面：

- `com.snowrealm.yukiboard.app.net.AppHttpClient`
  - `enum class Profile { SHORT, STANDARD, DEFAULT, STREAMING, STREAMING_LONG }`
  - `fun newBuilder(profile: Profile): OkHttpClient.Builder`

未新增：repository、coordinator、interface（本輪純粹 network entry 收斂，不碰 state owner）。

### E. CodeX A 審核需求 / 結論

- 需要 `CodeX A` 對本輪下一步判定：`READY` / `NOT READY` / `LOCKED`
- 要求 `CodeX A` 決定下一個 round（本文件不自排）
- 若 `NOT READY`，請指示要收緊 / 覆寫 / 回退哪一段

## 5. 合規檢核（對 `CodeX A` S7-0 三條 ruling）

| S7-0 Ruling | 本輪做法 | 合規？ |
| --- | --- | --- |
| §1 `AISettingsActivity` section 化不在 `Sprint 7` 主線 | 本輪完全未動 `AISettingsActivity` | ✔ |
| §2 `InviteCodeManager` 不在 `S7-1` 升 singleton | 本輪完全未動 `InviteCodeManager` | ✔ |
| §3 `CloudSyncManager` gateway 拆分只允 `S7-3` | 僅替換 `OkHttpClient.Builder()` 單行；未拆結構、未抽介面 | ✔ |

對 `S7-1` round 禁令檢核：

| 禁令 | 本輪做法 |
| --- | --- |
| 不改 `YukiBoardIMEService.kt` | ✔ 未動 |
| 不改 panel binder 系列 | ✔ 未動 |
| 不改 `AISettingsActivity` section 化 | ✔ 未動 |
| 不升 `InviteCodeManager` singleton | ✔ 未動 |
| 不拆 `CloudSyncManager` gateway | ✔ 僅 Builder 替換 |
| 不做 bugfix | ✔ 未動 |
| 不碰 Sprint 8 檔 | ✔ 未動 |

---

## 6. 附錄：遷移前後最小 diff 示意

遷移前（以 `SnowRealmAuthClient.kt` 為例）：

```kotlin
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
...
private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()
```

遷移後：

```kotlin
import com.snowrealm.yukiboard.app.net.AppHttpClient
...
private val client = AppHttpClient.newBuilder(AppHttpClient.Profile.STANDARD).build()
```

Outlier（以 `WhisperSpeechEngine.kt` 為例）：

```kotlin
import com.snowrealm.yukiboard.app.net.AppHttpClient
import java.util.concurrent.TimeUnit
...
// OpenAI Whisper：writeTimeout 60s 配合 WAV 上傳
private val client = AppHttpClient.newBuilder(AppHttpClient.Profile.STREAMING)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()
```

以上即本輪 `S7-1-CCB-NETWORK-ENTRY-IMPLEMENTATION` 全部產出。

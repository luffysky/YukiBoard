# Sprint 11-0 雪鑰 Settings / TTS Consume Note

日期: 2026-04-18
身份: 雪鑰（CCA / Settings & Auth & Session 資深工程師）
任務 ID: `S11-0-CCA-SETTINGS-TTS-CONSUME-NOTE`
狀態: 回交（不自宣 READY，等 玄樞 收齊三份 precheck 統一裁決）

---

## 0. 任務目標重述

依 `Sprint11-All-Round-Gate.md` §3 / `S11-0`：

> 補 `settings / TTS` 線在 `Sprint 11` 內需要驗證的 consume 風險與不測區域說明。

本輪只做 scope / risk note，不寫測試、不改 `app/src/main/**`、不偷開 `S11-1` ~ `S11-5`。

---

## 1. Sprint 11 對 Settings / TTS 的影響面

Sprint 11 主題 = 測試 baseline + silent catch 治理 + GL/TTS 技術債。對應到 雪鑰 線的衝擊：

| Sprint 11 round | 對 Settings / TTS 線的衝擊 |
| --- | --- |
| `S11-3` candidate / dictionary / engine 最小測試 | 不直接動 Settings；但 `SmartInputSection` 暴露的開關（`autoCorrectEnabled` / `learnNewWordsEnabled` / `predictionEnabled`）是測試前置，需確認預設值不被影響。 |
| `S11-4` silent catch 第一輪治理（`service/` `app/ime/` `input/`） | 不直接動 `ui/settings/`，但 `service/YukiBoardIMEService` 的 silent catch 一旦改寫，會影響 Settings 端「session truth」的 Toast / 顯示語意（如 RIME init 失敗訊息回流）。Settings 線需在 consume 端確認顯示語意未被破壞。 |
| `S11-5` GL / TTS 技術債（`KeyboardGLSurfaceView` / `GoogleCloudTtsEngine`） | 直接命中 雪鑰 主場：`TtsSettingsSection` 是 `GoogleCloudTtsEngine` 的唯一使用者面板；`Thread.sleep` / playPcm timeout 一旦改寫，Settings 內的「測試播放」按鈕行為會跟著變。 |

雪鑰本輪不必接 `S11-1` / `S11-2` 的 InputRouter / SmartInputManager 測試本體（屬 衡鑑），但 `SettingsScreenCoordinator` ↔ `UserSessionRepository` 路徑會被 silent catch 治理間接掃到，須先列出 consume 風險。

---

## 2. Settings 線 Consume 風險清單

下列為 Sprint 11 內 雪鑰 需要 watch 的 consume 點。標 `[S11-4]` 表示 silent catch 治理可能間接改變顯示語意，標 `[S11-5]` 表示 TTS 技術債修補後的 settings 端後遺。

### 2.1 Auth / Session 顯示鏈

- **`SettingsScreenCoordinator.verifySessionAsync` 單一 fetchMe 入口（S7-2 既定規則）**
  - 風險：silent catch 治理若把 `service/` 的 `runCatching {}` 改成 `try { ... } catch (e) { Log.w(...) }`，要確認 IME-side 不會在 session 還沒 verify 時就觸發二次 fetchMe。雪鑰側必須維持「session truth = `authClient.isLoggedIn` + `verifySessionAsync` 一次」這條規則。
  - Consume 動作：`S11-4` 收尾後 跑一次「啟動 IME → 開 Settings → 看 logcat tag `IME` 是否出現非預期 `fetchMe` 呼叫」。
  - 不寫單元測試，僅口述 consume 步驟。

- **登入後三件事顯示（帳號 / 方案 / 角色）**
  - 風險：歷史 bug `project_yukiboard_login_bug` 餘音未絕；S11-4 一旦改寫 IME tag 的 silent catch，profile cache 路徑可能再被遮蔽。
  - Consume 動作：保留現狀觀察，不在本 sprint 動 `AuthSettingsSection`。
  - 不測區域：OAuth Chrome Custom Tab 流程 → 屬 `S11-外`。

### 2.2 SmartInput 設定面（S11-3 前置）

- **`SmartInputSection` 預設開關**
  - 風險：`S11-3` candidate / dictionary / engine 測試會 spawn `SmartInputManager`；若 `SmartInputSection` 內預設值在後續 sprint 被改動，會讓測試的「開箱行為」與 settings 預設不符。
  - Consume 動作：`S11-3` 開門前，雪鑰需在 review 階段檢查 `YukiPreferences.autoCorrectEnabled / learnNewWordsEnabled / predictionEnabled` 預設仍為 `true`（與測試 fixture 預期一致）。
  - 不寫測試本體（屬 鍵律 / 衡鑑），僅 Settings 端 consume 守門。

### 2.3 Sync / Cloud（順帶提醒）

- **`SyncSettingsSection` 與 `CloudSyncManager.resolveVerifiedUserId`**
  - 風險：S11-4 silent catch 治理若觸及 `service/` 的 sync 觸發點，可能放大「沒登入 → 同步靜默失敗」的觀察難度。
  - Consume 動作：本 sprint 不動 sync；只記錄「若 silent catch 改成顯式 log，sync 失敗應有 `IME` tag 可追」。

---

## 3. TTS 線 Consume 風險清單

### 3.1 引擎切換（Engine type 0 / 1）

- **`prefs.ttsEngineType` 0 = Android `TextToSpeech` / 1 = `GoogleCloudTtsEngine`**
  - 風險：`S11-5` 若改寫 `GoogleCloudTtsEngine` 的 `Thread.sleep` 或 `playPcm` timeout，「測試播放」按鈕 5 秒固定 timeout 的語意會與底層 playback 真實長度脫節。
  - Consume 動作：`S11-5` 修補後，Settings 端需 consume 一次「engine type=1 + 短句 / 長句各一次」，確認按鈕 reset / Toast 無殘留。
  - 不測區域：實際 `texttospeech.googleapis.com` HTTP 呼叫（屬整合測，不在 Sprint 11 範圍）。

### 3.2 API Key 與 Voice 選擇

- **`prefs.googleCloudTtsApiKey` / `prefs.ttsVoiceName`**
  - 風險：S11-4 silent catch 若改寫到 `service/` 與 TTS 互動的路徑（IME 內亦有 TTS 唸字），voice fallback（空字串 → 系統預設 → `Locale.TAIWAN`）的觸發路徑會被放大或縮小。
  - Consume 動作：`S11-5` 收尾後跑 voiceName 空 / 有 / 無效 三種 case，僅 Settings 端 Toast 觀察，不入 unit test。
  - 不測區域：API Key 加密寫入（`EncryptedSharedPreferences` / Android KeyStore）— 屬整合面。

### 3.3 測試播放按鈕（5 秒固定 timeout）

- **`TtsSettingsSection.testBtn` `mainHandler.postDelayed({ ... }, 5000)`（line 234-237）**
  - 風險：S11-5 若把 `GoogleCloudTtsEngine` 改成 await playback 完成才 release，5 秒固定 timeout 可能切早 / 切晚。
  - 本 sprint 不擅自改 `5000` 常數（不在範圍內，不偷渡）。
  - 紀錄為「Sprint 12 候選 fix」，等 `S11-5` 結論出爐再決定 settings 側是否需要 follow-up patch。

### 3.4 Speech 系（`SpeechSettingsSection`）

- **語音輸入（input）非 TTS（output），但同 Section 鄰居**
  - 風險：低；`S11-5` 不動 input 路徑。
  - Consume 動作：無，僅標記「不在本輪 review 範圍」。

---

## 4. 不測區域 與 原因

| 區域 | 不測原因 |
| --- | --- |
| OAuth Chrome Custom Tab 流程（`LoginActivity` → 外部 browser → `OAuthCallbackActivity`） | 跨 Activity + 外部 browser，Sprint 11 是 unit test baseline，無 instrumentation harness。 |
| `EncryptedSharedPreferences` / Android KeyStore（API key、token 持久化） | 平台側元件，需 device，無 robolectric fixture，屬整合面。 |
| Android `TextToSpeech` 真實播放（系統 service） | 系統 service + 音訊輸出，無 fake 可替；且本身非本 sprint 主題。 |
| `GoogleCloudTtsEngine` 對 `texttospeech.googleapis.com` 的真實 HTTP | 外部依賴 + API Key + 配額，屬整合測，不在 Sprint 11 unit test baseline。 |
| `AudioTrack` PCM 播放路徑 | 系統 service，需 device，屬 `S11-5` 修補面而非測試面。 |
| `SettingsActivity` Activity recreate / lifecycle | UI 框架邊界，需 instrumentation；本 sprint 只做 unit baseline。 |
| `SettingsScreenCoordinator` ↔ `verifySessionAsync` 真實打 `/api/auth/me` | 屬整合面（網路 + token + server），unit test baseline 不涵蓋。 |
| `WhisperSpeechEngine` / `FasterWhisperSpeechEngine` 推論 | 不在 Sprint 11 主題，且需模型檔 / native lib。 |

---

## 5. 玄樞 對 雪鑰 線可能下達的指派預判（不替玄樞做決定）

雪鑰只列「若 玄樞 願意分配，可承接的 settings / TTS 線候選」，順序由玄樞決定：

- 候選 A：`S11-5` 的 settings 端 consume review（TTS 修補後 Settings 行為 regression）
- 候選 B：`S11-4` silent catch 治理對 IME → Settings 顯示鏈的副作用 review
- 候選 C：上述兩者皆不分配，雪鑰本 sprint 僅做 consume 守門 / log 觀察

> 依「Gate 回交不自排順序」規則（`feedback_gate_no_self_schedule.md`），順序與是否承接由 玄樞 裁定。

---

## 6. 本輪自我檢查

- [x] 本回交僅在 `docs/` 下新增 `.md`，未動 `app/src/main/**`
- [x] 未偷開 `S11-1` ~ `S11-5`
- [x] 未自宣 `READY`
- [x] 已標出 不測區域 與 原因
- [x] 已標出 consume 風險所對應的後續 round
- [x] 候選 follow-up（5 秒 timeout、AuthSection）已標為 Sprint 12 候選，不在本輪推進

---

## 7. 提交

回交 玄樞，等 衡鑑（`S11-0-CXB-TEST-SCOPE-HARNESS-AUDIT`）與 鍵律（`S11-0-CCB-INPUT-ENGINE-CASE-INVENTORY`）三份收齊後由 玄樞 統一裁決是否放行 `S11-1`。

— 雪鑰

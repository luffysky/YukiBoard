# Settings Smoke Checklist（Repo-Ready 最終版）

日期: 2026-04-15  
適用專案: `SnowRealmYukiBoard`  
維護者: Claude Code A  
狀態: **Repo-Ready 收尾完成**

## 1. 目的

每輪 settings 區修改後，至少跑完以下 checklist 再交接。
不需要每項都通過（有些功能尚未完成），但必須確認沒有回歸。

## 2. 必驗項目（每輪必跑）

### 2.1 基本啟動

- [ ] 設定頁能正常開啟（不 crash）
- [ ] ScrollView 可正常捲動
- [ ] 22 個 section 卡片全部正常顯示（語音辨識已獨立為 SpeechSettingsSection）
- [ ] 標題列 + 語言下拉選單正常
- [ ] 切換語言後重建 UI 正常

### 2.2 登入 / 登出顯示

- [ ] 未登入狀態：顯示「登入」按鈕（藍色）
- [ ] 已登入狀態：顯示使用者名稱 + 「登出」按鈕（紅色）
- [ ] 非同步驗證後 UI 正確更新（由 coordinator 統一驗證，一次 fetchMe）
- [ ] 登出後按鈕文案即時切換
- [ ] 從 LoginActivity 返回後，若登入狀態改變，UI 正確重建

### 2.3 雲端同步

- [ ] Sync 卡片正常顯示
- [ ] 帳號狀態 label 顯示正確（已連線 / Token 過期 / 尚未登入）
- [ ] 啟用/停用同步開關正常
- [ ] 上傳/下載按鈕可點擊，處理中狀態正確
- [ ] 上次同步時間顯示正確

### 2.4 AI Provider 顯示/切換

- [ ] AI 功能 master switch 正常
- [ ] 20 個 AI 子開關正常切換
- [ ] 「AI 設定 / Provider」按鈕可導航到 AISettingsActivity
- [ ] AISettingsActivity 帳號同步顯示正確
- [ ] 防詐模型更新按鈕可觸發

### 2.5 語音辨識（SpeechSettingsSection）

- [ ] 語音辨識卡片獨立顯示（不再嵌入 InputBehaviorSection）
- [ ] 語音辨識引擎選擇器正常（Android / Whisper / Faster-Whisper）
- [ ] 語音辨識語言選擇器正常
- [ ] 靜音偵測靈敏度選擇器正常
- [ ] 「測試 Whisper 連線」按鈕可觸發連線測試
- [ ] 「測試 Faster-Whisper」按鈕可觸發連線測試
- [ ] Faster-Whisper Server URL 輸入框可編輯並儲存

### 2.6 Insight 整合

- [ ] Insight 卡片正常顯示
- [ ] 登入時顯示訂閱等級（Free/Plus/Pro/Enterprise）
- [ ] 情境問卷 / 主題雲端同步 / 設定備份開關正常

### 2.7 TTS 語音輸出

- [ ] TTS 引擎選擇器正常（Android / Google Cloud / OpenAI）
- [ ] 語速滑桿正常
- [ ] 測試按鈕可觸發播放
- [ ] 語音選擇器可搜尋/篩選

## 3. 選驗項目（有修改該區才跑）

### 3.1 背景設定（BackgroundSettingsSection）

- [ ] 選圖片 / 選影片 / 清除背景
- [ ] 遮罩深度 / 顯示比例 / 旋轉 / 位置滑桿
- [ ] 鍵盤高度 / 按鍵大小 / 透明度滑桿
- [ ] 鍵盤完整預覽即時更新
- [ ] 字體顏色選擇器（預設色 + 自訂 RGB）
- [ ] 影片預覽播放 / 暫停（lifecycle）
- [ ] 透明鍵盤開關 + 透明度

### 3.2 輸入行為（InputBehaviorSection）

- [ ] 匯出詞庫可觸發分享
- [ ] 匯入詞庫可觸發檔案選擇器
- [ ] 語音後處理開關正常

### 3.3 Snippet 管理（SnippetSettingsSection）

- [ ] 新增 / 編輯 / 刪除 snippet 正常
- [ ] snippet 清單顯示正常

### 3.4 主題（ThemeSettingsSection）

- [ ] 主題卡片顯示
- [ ] 主題切換正常

### 3.5 外觀 / 語言（AppearanceSection / LanguageSection）

- [ ] 外觀卡片顯示
- [ ] 語言卡片顯示

### 3.6 法律與隱私（LegalSettingsSection）

- [ ] 法律文件按鈕可點擊
- [ ] WebView 彈窗正常顯示

## 4. 回歸觀察重點

以下為常見回歸方向，每輪應特別留意：

- SettingsActivity 行數是否超過 500 行警戒線（2026-04-16 確認：455 行）
- Section 是否反向引用 Activity（應為 0）
- 是否有新增的 `auth.fetchMe()` 散落在 section 內（應統一由 coordinator 發起）
- 是否有新增的 `AIProviderManager.getInstance()` 散落在 section 內
- 背景 section lifecycle 委派是否完整（onStart/onStop/onPause/onResume/onDestroy）
- 從 LoginActivity 返回的 recreate 是否正常運作

## 5. 狀態來源收斂最終結論

### 5.1 已收斂（由 coordinator / repository 統一管理）

| 狀態 | 入口 | 說明 |
|------|------|------|
| 登入驗證 | `SettingsScreenCoordinator.verifySessionAsync()` | 統一一次 fetchMe，取代 Auth/Sync section 各自獨立呼叫 |
| 使用者資料 | `UserSessionRepository.verifySession()` | 回傳 VerifiedSession（isConnected, displayName, plan） |
| Whisper API Key | `settingsCoordinator.getApiKey()` → lambda 注入 `SpeechSettingsSection` | section 不再直接 getInstance |

### 5.2 合理邊界（不需收斂）

| 狀態 | 位置 | 理由 |
|------|------|------|
| AI Provider 設定 | `AISettingsActivity:37` `AIProviderManager.getInstance(this)` | 該 Activity 本身就是 provider 設定終端頁，持有 manager 是核心業務 |
| Fraud Model | `AISettingsSection.buildFraudModelUpdate()` 內 `FraudModelManager(context)` | 建構成本低、只在 section 內使用、不涉及跨 section 狀態共享 |
| Sync 操作 | `SyncSettingsSection` 直接持有 `CloudSyncManager` | sync 操作（上傳/下載/toggle）是 section 核心業務，且 CloudSyncManager 是 singleton |
| TTS Google Cloud callback | `SettingsActivity` lambda 閉包內 | 需要 `runOnUiThread` + Activity context，目前僅 1 處，不超標 |

### 5.3 已確認清零

| 檢查項 | section 層結果 |
|--------|---------------|
| `auth.fetchMe()` | **0 處**（統一由 coordinator 發起） |
| `AIProviderManager.getInstance()` | **0 處**（改為 lambda 注入） |
| `SpeechSettingsHelper` | **已刪除**（轉為 `SpeechSettingsSection`） |

## 6. 依賴邊界地圖

```
SettingsActivity (455 行, 2026-04-16 確認)
  ├─ settingsCoordinator: SettingsScreenCoordinator
  │    ├─ sessionRepository: UserSessionRepository
  │    │    ├─ authClient: SnowRealmAuthClient.getInstance()
  │    │    └─ providerManager: AIProviderManager.getInstance()
  │    ├─ prefs: YukiPreferences
  │    ├─ themeManager: ThemeManager.getInstance()
  │    └─ cloudSyncManager: CloudSyncManager.getInstance()
  │
  ├─ AuthSettingsSection(auth, scope, tr)
  │    └─ onSessionVerified() ← coordinator 驗證後回呼
  │
  ├─ SyncSettingsSection(auth, syncManager, scope, tr)
  │    └─ onSessionVerified() ← coordinator 驗證後回呼
  │
  ├─ SpeechSettingsSection(prefs, tr, scope, openAiApiKeyProvider)
  │    └─ openAiApiKeyProvider = { settingsCoordinator.getApiKey(OPENAI) }
  │
  ├─ AISettingsSection(prefs, scope, tr)
  │    └─ FraudModelManager(context) — section 內部建構
  │
  ├─ InsightSettingsSection(auth, prefs, tr)
  │    └─ 讀 auth.isLoggedIn / currentUser（本地快取，不做網路呼叫）
  │
  └─ 其他 17 個 section（僅依賴 prefs / tr / themeManager）
```

## 7. Runtime 尚未實測項目

因模擬器不可用，以下項目僅通過 compile 驗證，尚未 runtime 實測：

- [ ] coordinator 統一 fetchMe 後，Auth/Sync section UI 是否正確更新
- [ ] SpeechSettingsSection 獨立卡片的 UI 呈現是否正常
- [ ] 從 LoginActivity 返回後 recreate 是否穩定
- [ ] TTS Google Cloud 測試按鈕的 lifecycle 是否正常

風險等級：低。所有改動都是收斂存取路徑，不改功能語意。

## 8. 使用方式

每輪 settings 修改完成後：

1. 打開此 checklist
2. 至少跑完「必驗項目」(Section 2)
3. 若有修改特定 section，跑對應的「選驗項目」(Section 3)
4. 在交接文件的「C. 驗證結果」中標注通過/失敗項
5. 若有新發現的回歸，更新 Section 4

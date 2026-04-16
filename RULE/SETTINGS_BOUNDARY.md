# Settings 邊界規則（YukiBoard / Android 版）

日期: 2026-04-14  
適用專案: `SnowRealmYukiBoard`  
維護者: Claude Code A

## 1. 目的

SettingsActivity 已從 3,868 行瘦身至 455 行（-88%）。
本規則確保後續開發不讓它反彈膨脹。

## 2. SettingsActivity 允許持有的責任

- `onCreate` / lifecycle 委派
- ScrollView + root LinearLayout 容器
- 標題列 + 語言下拉選單（Activity 層 UI 路由）
- 按鈕列（功能說明 / Z幣 / AuthSection 接線）
- Section 接線（`root.addView(xxxSection.buildCard(this))`）
- 權限請求與回調（`requestMediaPermissionThen` / `onRequestPermissionsResult`）
- `onActivityResult` 路由（詞庫匯入 / 背景圖片委派）
- `showLegalWebView`（WebView 需 Activity context）
- 輸入法測試按鈕（路由到 InputTestActivity）
- `dp()` 工具方法

## 3. 絕對禁止加回 SettingsActivity 的東西

1. **SeekBar / Switch / RadioButton / CheckBox** — 全部在 section 內建構
2. **prefs 直接讀寫業務邏輯** — 新設定走 section constructor 注入
3. **大段 UI 建構程式碼** — 超過 10 行的 UI 建構必須放到 section
4. **網路呼叫 / API client 建構** — 走 section 或 repository
5. **Canvas / Bitmap / MediaPlayer 操作** — 走 section（如 BackgroundSettingsSection）
6. **AlertDialog 業務邏輯** — 走 section 內部

## 4. 新增設定項的標準流程

1. 在 `ui/settings/section/` 新增或修改對應的 `XxxSection.kt`
2. Section constructor 接收 `YukiPreferences` + `(String) -> String` + 其他必要依賴
3. Section 提供 `fun buildCard(context: Context): LinearLayout`
4. SettingsActivity 裡只加一行：`root.addView(xxxSection.buildCard(this))`
5. 若 section 需要 Activity-level 操作（權限、startActivityForResult），透過 lambda 注入

## 5. Section 設計規範

### 依賴注入

- `YukiPreferences` — 透過 constructor
- `ThemeManager` — 透過 constructor
- `CoroutineScope` — 透過 constructor（用 `activityScope` 或 `lifecycleScope`）
- `SnowRealmAuthClient` — 透過 constructor（不在 section 內 getInstance）
- 翻譯函數 — `tr: (String) -> String`

### UI 建構

- 共用元件使用 `SettingsUiHelper`：`simpleCard` / `cardGroup` / `switchRow` / `styledButton` / `dp`
- 不在 section 內直接引用 SettingsActivity
- 不在 section 內呼叫 `startActivity`（透過 lambda 或 Context）

### Lifecycle

- 若 section 有 lifecycle 需求（如 BackgroundSettingsSection），提供 `onPause` / `onResume` / `onStart` / `onStop` / `onDestroy`
- SettingsActivity 在對應 lifecycle 呼叫委派

## 6. SettingsUiHelper 擴展規則

- 只放純 UI 建構工具（不放業務邏輯）
- 只接 Context，不接 Activity
- 新增方法需所有 section 共用才加（不為單一 section 加方法）

## 7. 已知邊界灰色地帶

### Google Cloud TTS 測試 lambda

`TtsSettingsSection` 的 `onTestGoogleTts` callback 在 SettingsActivity lambda 內讀寫 prefs 和建構 `GoogleCloudTtsEngine`。

原因：需要 `runOnUiThread` + Activity context。

處置：目前可接受。若未來 Activity-level callback 超過 3 個，考慮提供輕量 callback interface。

## 8. 監督指標

| 指標 | 警戒值 | 目前值 |
|------|--------|--------|
| SettingsActivity 行數 | > 500 行 | 455 行（2026-04-16 確認） |
| Activity 內 SeekBar/Switch/Radio/Checkbox 數量 | > 0 | 0 |
| Activity 內 prefs 直接業務存取 | > 0（lambda 閉包除外） | 0 |
| Section 反向引用 Activity | > 0 | 0 |

超過警戒值時，必須回報 CodeX A 並排定修正。

## 9. 違規方向

以下行為視為違規：

- 「先塞回 Activity，之後再拆」
- 「這個設定太小不值得開 section」（10 行以上就該在 section）
- 「Section 需要 Activity 引用才能做」（改用 lambda / Context）
- 「直接在 Activity 加 SharedPreferences 讀寫」

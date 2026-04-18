# Works Detail Assign All

日期: 2026-04-18  
身份: 玄樞  
用途: Sprint 10 與後續 Sprint 的總排程與角色指派

## 1. 後續 Sprint 總覽

目前建議後續共 5 個 Sprint：

1. `Sprint 10` 使用者當前 bug + 工程護欄
2. `Sprint 11` 測試骨架 + 例外治理 + lifecycle 釋放補完
3. `Sprint 12` 支付 / API key / 安全整改
4. `Sprint 13` 合規 / 雲同步 / 隱私與回報治理
5. `Sprint 14` 詞庫擴充 / 大型基礎設施 / 中長線架構項

## 2. Sprint 10

主題：

- AUTO 模式
- Settings Insight UI 刷新
- `CoroutineExceptionHandler`
- `InputRouter.release()`
- receiver / lifecycle 風險
- 最小測試骨架

主要角色：

- `玄樞`: gate owner / 最終核可
- `衡鑑`: 結構修補與測試前置
- `雪鑰`: Settings / auth / session 線
- `鍵律`: 日文 / AUTO / 輸入主流程線

暫代規則：

- `衡鑑` 額度不足時，`雪鑰` 代班 settings / auth / contract / consume review 類
- `衡鑑` 額度不足時，`鍵律` 代班 service / input / engine / test / infra 類

## 3. Sprint 11

主題：

- `InputRouterTest`
- `SmartInputManagerTest`
- engine / dictionary / candidate manager 單元測試
- silent catch 第一輪收斂
- GL / TTS sleep 類問題

預計 rounds：

1. `S11-0` test scope / harness
2. `S11-1` `InputRouterTest`
3. `S11-2` `SmartInputManagerTest`
4. `S11-3` candidate / dictionary / engine smoke tests
5. `S11-4` silent catch service/app/ime 第一輪
6. `S11-5` `KeyboardGLSurfaceView` / `GoogleCloudTtsEngine` 技術債修補

主要角色：

- `衡鑑`: 測試與技術債主責
- `雪鑰`: Settings / TTS 相關 consume 驗證
- `鍵律`: input / candidate / engine 測試案例輔助

暫代規則：

- `Sprint 11` 若 `衡鑑` 無法承接，`S11-0` 到 `S11-5` 以 `鍵律` 為主責
- 涉及 settings / TTS consume 驗證的 cross-check 由 `雪鑰` 代班二審

## 4. Sprint 12

主題：

- `NewebpayClient` server-side signing 遷移
- API key / payment key 下放後端
- payment / security 流水整理

預計 rounds：

1. `S12-0` payment / key audit
2. `S12-1` Newebpay signing server-side plan
3. `S12-2` Android client 改為 fetch signed payload
4. `S12-3` AI / weather / gif key 盤點與 release policy
5. `S12-4` remote keyboard PIN / brute-force 防護

主要角色：

- `玄樞`: 風險判定與 gate
- `衡鑑`: Android client 改造
- `雪鑰`: auth / sync / security side note
- `鍵律`: payment UI / wallet flow smoke

暫代規則：

- `衡鑑` 額度不足時，client / observability hook 類由 `鍵律` 先代班
- auth / sync / security wording 與 consume 邊界由 `雪鑰` 代班補位

## 5. Sprint 13

主題：

- 合規高優先項
- cloud sync / clipboard sync / fraud report privacy
- account export/delete 與 consent 類工作

預計 rounds：

1. `S13-0` legal scope cut
2. `S13-1` fraud report privacy hardening
3. `S13-2` clipboard sync contract
4. `S13-3` user data export/delete API contract
5. `S13-4` AI consent / opt-out UI
6. `S13-5` `usesCleartextTraffic` / network security config

主要角色：

- `玄樞`: scope owner
- `雪鑰`: legal / settings / consent UI
- `衡鑑`: Android contract integration
- `鍵律`: panel / clipboard / fraud UI consume

暫代規則：

- Android contract integration 類先由 `雪鑰` 做 contract / consume 線盤點
- 若需要動 service / panel / sync runtime，再由 `鍵律` 代班實作或審核

## 6. Sprint 14

主題：

- 詞庫擴充
- 詞庫基礎設施
- 中長線未完功能與長債

預計 rounds：

1. `S14-0` dictionary infra design
2. `S14-1` 拼音外部詞庫化
3. `S14-2` 倉頡詞組化
4. `S14-3` 日韓詞庫擴充
5. `S14-4` 詞庫版本管理 / 增量更新
6. `S14-5` 使用者詞庫雲同步

主要角色：

- `玄樞`: 架構切板
- `衡鑑`: infra / format / sync
- `鍵律`: input engine consume 端
- `雪鑰`: settings / import-export / migration note

暫代規則：

- infra / format / sync 若偏 engine/runtime，由 `鍵律` 代班
- settings / import-export / migration contract 若偏設定面，由 `雪鑰` 代班

## 7. 真正未完成項目總表

來自 `todo_list_0414.md` 與 `YukiBoard_HealthReport_2026-04-18.md` 交叉後，現階段真正應排入 Sprint 的項目為：

1. 使用者當前 bug 兩條
2. `InputRouter.release()` 補完
3. `CoroutineExceptionHandler`
4. 0 測試檔問題
5. silent catch 治理
6. `Newebpay` 與 API key 安全整改
7. `usesCleartextTraffic` / network security / PIN / DPA / consent 類法務安全項
8. clipboard sync / fraud report privacy / account export-delete 類產品合規項
9. 大型詞庫與詞庫基礎設施

## 8. 不再列為真未完成的項目

以下項目已不應再用舊待辦口徑反覆排工：

1. 面板輸入框無法打字
2. AI Writer / EmojiTranslate / ToneAdjust / CodeComplete / RegexGen 開關無效
3. UserDictionary CRUD 與匯出匯入
4. TTS 音色列表 / 選擇器 / 套用
5. Clipboard 搜尋 / 自動分類 / 標籤篩選 / 長按選單 / 同步開關
6. 日文片假名 / 羅馬拼音切換 bug（2026-04-18 使用者確認恢復，已取消）

## 9. Gate 準則

1. 所有 Sprint 仍遵守 `玄樞` gate。
2. 每個 Sprint 先前置盤點，再開 implementation。
3. 高風險安全項與大型基礎設施不得與新功能擴張混做。

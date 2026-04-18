# YukiBoard 待辦清單 — 2026-04-18

> 來源：`docs/todo_list_0414.md` + `YukiBoard_HealthReport_2026-04-18.md` + `daily_works_0418.md` + CodeX A 實際程式碼複核  
> 原則：只列「現在還沒實作或未真正收口」的項目  
> 記號：`~~刪除線~~` = 已確認不再列為未完成

---

## 1. 已複核後移出主待辦的項目

- ~~面板輸入框無法打字~~
- ~~AI Writer / EmojiTranslate / ToneAdjust / CodeComplete / RegexGen 開關無效~~
- ~~UserDictionary CRUD 完善~~
- ~~UserDictionary 匯出/匯入~~
- ~~TTS 音色套用~~
- ~~Clipboard 搜尋 / 自動分類 / 標籤篩選 / 長按選單 / 同步開關~~

以上已在 2026-04-18 由 CodeX A 直接看 code 複核，不再列為新 Sprint 主工作。

## 2. Sprint 10

### S10-UserBug

- ~~[ ] 日文片假名 / 羅馬拼音切換仍有實際 bug，需重現與修補~~ — 2026-04-18 使用者確認：片假名與羅馬拼音切換已恢復，此 bug 取消
- [ ] AUTO 模式輸入仍有實際 bug，需重現與修補
- [ ] Settings 登入 Insight 後 UI 沒刷新，需找出 session verify / state consume 真正斷點

### S10-Engineering

- [ ] `InputRouter.release()` 補完 engine / cache / lazy engine 釋放責任
- [ ] `YukiBoardIMEService.serviceScope` 加入 `CoroutineExceptionHandler`
- [ ] `bgUpdateReceiver` create / destroy / floating path 對稱性檢查與修補
- [ ] 建立最小 unit test 骨架

## 3. Sprint 11

### S11-Test

- [ ] `InputRouterTest`
- [ ] `SmartInputManagerTest`
- [ ] candidate / dictionary / engine 最小測試

### S11-Stability

- [ ] silent catch 第一輪治理，先從 `service/`、`app/ime/`、`input/` 開始
- [ ] `KeyboardGLSurfaceView` 自製 frame loop / sleep 問題
- [ ] `GoogleCloudTtsEngine` 的 `Thread.sleep` 問題

## 4. Sprint 12

### S12-PaymentSecurity

- [ ] `NewebpayClient` 改為後端簽章，不再把 HashKey / HashIV 放進 APK
- [ ] release policy：AI key / weather key / gif key 盤點與下放策略
- [ ] WiFi keyboard PIN 改 6 位數並加入限制

### S12-Observability

- [ ] 規劃 Crashlytics 或等價錯誤記錄接點
- [ ] 補關鍵錯誤路徑 `Log.w` / `Log.e`

## 5. Sprint 13

### S13-Compliance

- [ ] `usesCleartextTraffic` 關閉或改白名單
- [ ] AI consent / opt-out UI
- [ ] fraud report privacy：`matched_keywords` 改 hash 或聚合
- [ ] `fraud_reports` RLS insert only

### S13-DataAndSync

- [ ] 使用者資料匯出 API 規格
- [ ] 使用者資料刪除 API 規格
- [ ] clipboard sync 本體
- [ ] 使用者詞庫雲同步

## 6. Sprint 14

### S14-Dictionary

- [ ] 拼音詞庫外部檔案化
- [ ] 拼音常用詞與成語大擴充
- [ ] 倉頡詞組化與常用詞擴充
- [ ] 日文漢字辭書擴充至更完整等級
- [ ] 韓文詞庫擴充

### S14-Infra

- [ ] 詞庫統一格式
- [ ] 詞庫版本管理
- [ ] 詞庫匯入工具
- [ ] 增量更新機制

## 7. 健康報告導入項

以下來自 `YukiBoard_HealthReport_2026-04-18.md`，已納入 Sprint，不再獨立散列：

- [ ] 0 測試檔問題
- [ ] `InputRouter.release()` 不完整
- [ ] `CoroutineExceptionHandler` 缺失
- [ ] silent catch 過多
- [ ] payment key 安全問題
- [ ] API key server-side proxy 問題

## 8. 目前不排進短期 Sprint 的項目

- [ ] Compose 重寫設定頁
- [ ] multi-module 拆分
- [ ] callbackFlow 全量改造
- [ ] VR / AR 長線功能
- [ ] 大型新遊戲 / 新功能擴張

原因：

- 這些不是 2026-04-18 當前最有 ROI 的工作，也會破壞 Sprint 10-12 的收口節奏。

## 9. 接手順序

下一個 session 建議閱讀順序：

1. [Sprint10-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint10-All-Round-Gate.md)
2. [Works_Detail_Assign_All.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Works_Detail_Assign_All.md)
3. [Works_Detail_Assign_0418.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Works_Detail_Assign_0418.md)
4. [daily_works_0418.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/daily_works_0418.md)
5. [YukiBoard_HealthReport_2026-04-18.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/YukiBoard_HealthReport_2026-04-18.md)

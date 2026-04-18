# Works Detail Assign 0418

日期: 2026-04-18  
身份: 玄樞  
用途: 2026-04-18 起算的近期執行排程與當前指派

## 1. 今日之後的直接主線

今天之後不再從舊 `todo_list_0414.md` 直接派工。  
正式改以：

1. [Sprint10-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint10-All-Round-Gate.md)
2. [Works_Detail_Assign_All.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Works_Detail_Assign_All.md)
3. [todo_list_0418.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/docs/todo_list_0418.md)

作為接手基線。

## 2. 0418 當前優先順序

優先順序固定如下：

1. `S10-0` 前置盤點
2. AUTO 模式 bug
3. Settings 登入 Insight 後 UI 刷新
4. `CoroutineExceptionHandler`
5. `InputRouter.release()`
6. `bgUpdateReceiver` / floating path 檢查
7. unit test 骨架

## 3. 角色分派

### 玄樞

負責：

- gate owner
- round 解鎖
- scope cut
- 風險裁決
- 文件同步

當前任務：

1. 維護 `Sprint10-All-Round-Gate.md`
2. 維護 `todo_list_0418.md`
3. `Sprint 10` 已完成
4. `S11-1` 已完成
5. `S11-2` 已完成
6. `S11-3` 已完成
7. `Sprint 11` 已開 `S11-4`

### 衡鑑

負責：

- 結構性修補
- lifecycle / release / error boundary
- 測試骨架

當前任務：

1. `S10-0` 技術盤點
2. `S10-4` `CoroutineExceptionHandler`（已完成）
3. `S10-5` `InputRouter.release()`（已完成）
4. `S10-6` `bgUpdateReceiver`（已完成）
5. `S10-7` test harness（已完成）
6. `S11-0` test scope / harness audit（已完成）
7. `S11-1` `InputRouterTest` baseline implementation（已完成）
8. `S11-2` `SmartInputManagerTest` baseline implementation（已完成）
9. `S11-3` smoke test review（已完成）
10. `S11-4` silent catch first pass（進行中）

回任說明：

1. `衡鑑` 已恢復可承接任務
2. `S10-6` 起恢復標準鏈：`衡鑑 -> 玄樞`
3. 若後續再遇 `Codex` 額度不足，才重新啟用 `雪鑰 / 鍵律` 代班規則

### 雪鑰

負責：

- settings / auth / provider / sync side
- consume 與 UI 狀態驗證

當前任務：

1. `S10-0` Settings / session scope audit
2. `S10-3` Settings Insight UI refresh bug（已完成）
3. `S10-4` 代班二審：`CoroutineExceptionHandler`（已完成）
4. `S10-5` 代班二審：`InputRouter.release()`（已完成）
5. `S11-0` Settings / TTS consume note（已完成）
6. 當前待命，等後續 `Sprint 11` round 指派

### 鍵律

負責：

- 輸入流程
- keyboard consume
- panel / engine / candidate 路徑

當前任務：

1. `S10-0` 日文 / AUTO scope audit
2. `S10-2` AUTO 模式 bugfix（已完成）
3. `S10-4` 代班實作：`CoroutineExceptionHandler`（已完成）
4. `S10-5` 代班實作：`InputRouter.release()`（已完成）
5. `S11-0` input / engine case inventory（已完成）
6. `S11-3` candidate / dictionary / engine smoke tests（已完成）
7. 當前其餘任務 standby，待 `Sprint 11` 後續 gate 指派

## 4. 今日不應插隊的項目

以下項目先不要插進 Sprint 10 前半段：

1. 大型詞庫擴充
2. clipboard cloud sync 本體
3. payment server-side 全改造
4. legal 全量整改
5. multi-module / Compose / 大重構

理由：

- 這些都會打斷使用者當前 bug 與工程護欄收口節奏。

## 5. 今日完成標準

0418 這輪若要算「收束完成」，至少要有：

1. 新待辦與新分派文件正式落地
2. Sprint 10 gate 落地
3. 三條使用者當前 bug 已有明確 owner
   - 其中日文切換 bug 已於 2026-04-18 由使用者確認恢復，正式取消
4. 後續 Sprint 10-14 已有明確主題與分界
5. `Sprint 10` 全 round 經 gate 審核完成
6. `Sprint 11` 已完成前置盤點並開到 `S11-4`

## 6. 玄樞備註

`YukiBoard_HealthReport_2026-04-18.md` 的工作安排已被吸收進：

- `todo_list_0418.md`
- `Works_Detail_Assign_All.md`

但採用時有做二次複核：

1. 已被實作追回的項目不再重排。
2. 真正高風險且仍未落地的項目，才納入 Sprint 10 之後。
3. `Sprint 11` 目前以 `S11-4 Silent Catch First Pass` 為唯一 `ACTIVE` round。
4. `S11-3` 已收斂完成，scope 只落在 `CandidateManager`、`Pinyin`、`English`、`Korean`、`Japanese`、`Cangjie`、`EnglishDictionary`；`ChewingInputEngine` 與 smart 子系統獨測仍維持不測區域。

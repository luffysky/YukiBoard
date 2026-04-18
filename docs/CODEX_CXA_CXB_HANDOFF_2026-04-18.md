# CodeX 交接文件（玄樞 / 衡鑑）

日期: 2026-04-18  
交接角色: `玄樞`（CXA）、`衡鑑`（CXB）  
目的: 在 `Codex` 額度不足時，讓後手能無斷點接手 `gate` 與 `CXB` 當前任務

---

## 1. 當前總狀態

### Sprint 10

- `COMPLETED`

### Sprint 11

- `S11-0 = COMPLETED`
- `S11-1 = COMPLETED`
- `S11-2 = COMPLETED`
- `S11-3 = COMPLETED`
- `S11-4 = ACTIVE`
- `S11-5 = LOCKED`

唯一正在進行的 round：

- `S11-4-CXB-SILENT-CATCH-FIRST-PASS-IMPLEMENTATION`

正式 gate 檔：

- [Sprint11-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint11-All-Round-Gate.md)

---

## 2. 玄樞 交接

### 2.1 玄樞已完成的 gate 終審

- `S10-2`：親讀 `.kt` 實檔後放行
- `S10-3`：親讀 `AuthSettingsSection.kt` 後放行
- `S10-4`：親讀 `YukiBoardIMEService.kt` 後放行
- `S10-5`：親讀 `InputRouter.kt` 後放行
- `S10-6`：親讀 `YukiBoardIMEService.kt` 後放行
- `S10-7`：親讀 test 檔與 `app/build.gradle`，並親跑 `:app:testDebugUnitTest` 後放行
- `S11-0`：三份前置文件收齊後放行
- `S11-1`：親讀 `InputRouterTest.kt`，並親跑指定 test class 後放行
- `S11-2`：親讀 `SmartInputManagerTest.kt`，並親跑指定 test class 後放行
- `S11-3`：親讀 7 個 smoke test 檔，並親跑 candidate/engine/dictionary 測試群組後放行

### 2.2 玄樞當前責任

1. 維持 `Sprint11-All-Round-Gate.md` 為唯一正式派工與解鎖文件
2. 維持一次只開一門
3. `S11-4` 若有 `.kt` 改動，必須親自逐檔複核
4. 只有 `玄樞` 可輸出正式：
   - `READY`
   - `NOT READY`
   - `COMPLETED`

### 2.3 玄樞接手人必知規則

1. 顯示層一律用中文角色名
2. 任務 ID 一律保留代碼：
   - `CXA / CXB / CXC / CXD`
   - `CCA / CCB / CCC / CCD`
3. 若 round 有 `.kt` 改動，不可只看摘要或二審結論就放行
4. 若 `CodeX` 額度不足，代班規則以：
   - [RULE/SPRINT_GATE_RULE.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SPRINT_GATE_RULE.md)
   - [RULE/ROUND_GATE_COLLAB_RULE.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/ROUND_GATE_COLLAB_RULE.md)
   為準

---

## 3. 衡鑑 交接

### 3.1 衡鑑已完成項目

- `S10-0-CXB-ENGINEERING-AUDIT`
- `S10-6-CXB-BGUPDATE-RECEIVER-LIFECYCLE-IMPLEMENTATION`
- `S10-7-CXB-TEST-BASELINE-IMPLEMENTATION`
- `S11-0-CXB-TEST-SCOPE-HARNESS-AUDIT`
- `S11-1-CXB-INPUTROUTERTEST-BASELINE-IMPLEMENTATION`
- `S11-2-CXB-SMARTINPUTMANAGERTEST-BASELINE-IMPLEMENTATION`
- `S11-3-CXB-CANDIDATE-DICTIONARY-ENGINE-SMOKE-REVIEW`

### 3.2 衡鑑當前唯一任務

- 任務 ID：`S11-4-CXB-SILENT-CATCH-FIRST-PASS-IMPLEMENTATION`

目標：

1. 對 `service/`、`app/ime/`、`input/` 做第一輪 silent catch 治理
2. 先收斂高頻且可安全落地的 silent catch
3. 不擴成跨模組 exception framework

本輪限制：

1. 只處理明顯 silent catch / 空 catch / 吃例外不記錄的路徑
2. 不得偷開 `S11-5`
3. 若改到 `.kt`，交回後一定會被 `玄樞` 親自逐檔複核

提交鏈：

1. `衡鑑` 完成 `S11-4-CXB-SILENT-CATCH-FIRST-PASS-IMPLEMENTATION`
2. 直接提交給：`玄樞`
3. 只有 `玄樞` 判定 `READY`，才可開 `S11-5`

### 3.3 衡鑑審核口徑延續

若後手需要代行 `衡鑑` 職責，至少要維持這些標準：

1. 親讀實際檔案，不得靠記憶回話
2. 逐項核對：
   - file scope
   - 是否越界
   - 是否假修 / 漏修
   - compile / test 結果是否與回交一致
3. 若需要二審責任，必須寫在自己的任務欄，不可只存在於提交鏈

---

## 4. S11-3 已核准範圍備忘

`S11-3` 已完成，且本輪正式核准 scope 只有：

1. `CandidateManager`
2. `PinyinInputEngine`
3. `EnglishInputEngine`
4. `KoreanInputEngine`
5. `JapaneseInputEngine`
6. `CangjieInputEngine`
7. `EnglishDictionary`

以下仍屬禁區 / 不測區域：

1. `ChewingInputEngine`
2. `YukiBoardIMEService`
3. `service/`、`ui/`、`sensor/`、`speech/`、`remote/`
4. smart 子系統獨立測
5. `app/src/main/**`

這份範圍已寫回 `Sprint11-All-Round-Gate.md`，後手不得回頭擴張成既成事實。

---

## 5. 相關檔案

優先看：

1. [Sprint11-All-Round-Gate.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Sprint11-All-Round-Gate.md)
2. [Works_Detail_Assign_0418.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/Works_Detail_Assign_0418.md)
3. [RULE/SPRINT_GATE_RULE.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SPRINT_GATE_RULE.md)
4. [RULE/ROUND_GATE_COLLAB_RULE.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/ROUND_GATE_COLLAB_RULE.md)
5. [RULE/SNOWREALM_TEAM_MASTER.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/SNOWREALM_TEAM_MASTER.md)

HR 備忘：

- [RULE/HR/WARNINGS/2026-04-18_衡鑑_口頭警告.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/RULE/HR/WARNINGS/2026-04-18_%E8%A1%A1%E9%91%91_%E5%8F%A3%E9%A0%AD%E8%AD%A6%E5%91%8A.md)

---

## 6. 明確禁止事項

1. 不要補 `daily_works_0418.md`
   - 除非董事長明確叫寫
   - 而且必須寫成晚上的場次，不可混進既有凌晨段落
2. 不要把中文名寫進任務 ID
3. 不要在未經 `玄樞` 終裁前擅自標 `READY`
4. 不要讓 `S11-5` 提前開門

---

## 7. 一句話交接

> 目前 `Sprint 11` 已走到 `S11-4`，前面三門測試基線與 smoke tests 都已正式過 gate；後手只要守住單門模式、維持 `S11-4` 不擴 scope，並照規則做 `.kt` 實檔終審，就不會斷鏈。

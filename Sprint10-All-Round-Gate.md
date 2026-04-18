# Sprint 10 All Round Gate

身份：玄樞

用途：

- 作為 `Sprint 10` 唯一總 gate 文件
- `Sprint 10` 專門處理當前仍有效的使用者 bug 與工程護欄補強
- 逐 round 記錄：
  - 任務
  - 回交
  - `玄樞` 審核結論
  - 下一輪開門指派
- 已完成或已取消 round 一律保留並加刪除線，不刪除
- 之後 `Sprint 10` 不再新增額外 dispatch gate `.md`

---

## 全域規則

1. 一次只開一個 round。
2. 前一 round 完成並經 `玄樞` 審核通過，才開下一 round。
3. 執行者可提候選方向，但不可自行指定後續 round 順序。
4. 若需擴大 file scope，必須先回報，不可默擴。
5. `Sprint 10` 只處理已核實的 bugfix / guardrail / test baseline，不可混入新功能。
6. 本 sprint 的任務下發、回交、審核，全以這份 gate 為準。
7. 每一 round 完成後保留原記錄並加刪除線，不刪除。

---

## 開門前提

`Sprint 10` 的正式前提：

1. `Sprint 6` 到 `Sprint 9` 已完成，且不再回頭混修舊 sprint 主線。
2. `docs/todo_list_0414.md` 已完成複核，已落地項目不得再重複當成未完成派工。
3. `YukiBoard_HealthReport_2026-04-18.md` 只作為候選來源，是否納入 sprint 以 `玄樞` 二次核實為準。
4. 必須先做 `S10-0` 前置盤點與重現，通過後才可開實作 round。

---

## ~~Round S10-1：日文片假名 / 羅馬拼音切換修復~~

### ~~當前狀態~~

- ~~`CANCELLED`~~

### ~~主題~~

- ~~`KEYCODE_TOGGLE_KANA`~~
- ~~平假名 / 片假名 / 羅馬拼音切換一致性~~
- ~~`KeyboardState` 與 `JapaneseInputEngine` 對齊~~

### ~~取消原因~~

1. ~~2026-04-18 使用者已明確確認：片假名與羅馬拼音切換已恢復。~~
2. ~~此題不再列為 `Sprint 10` 待修主線。~~
3. ~~保留記錄，不刪除。~~

---

## ~~Round S10-0：前置盤點與重現~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~目標~~

- ~~先把 `Sprint 10` 仍有效的 bug 與 guardrail 題做正式盤點~~
- ~~只確認重現條件、file scope、smoke path、禁區與 round 切法~~
- ~~不做 implementation~~
- ~~不提前開 `S10-2+`~~

### ~~本輪正式範圍~~

1. ~~`AUTO` 模式輸入 bug 的重現路徑與最小 scope~~
2. ~~`Settings` 登入 `Insight` 後 UI 未刷新題的重現路徑與最小 scope~~
3. ~~`YukiBoardIMEService.serviceScope` 缺 `CoroutineExceptionHandler` 的最小補法~~
4. ~~`InputRouter.release()` 仍只釋放 `userDict` 的真實 release owner 與 file scope~~
5. ~~`bgUpdateReceiver` create / floating / destroy path 對稱性盤點~~
6. ~~`app/src/test` 最小測試骨架的落地入口~~

### ~~本輪限制~~

1. ~~不改 `.kt`~~
2. ~~不做 bugfix implementation~~
3. ~~不得因為已有直覺就跳過重現與 file scope 盤點~~
4. ~~不得自行宣告 `S10-2` 到 `S10-7` 任一門已可直接實作~~
5. ~~若發現題目已自然消失或非 blocking，必須在回交中明確寫出，不可口頭略過~~

### ~~預定 owner~~

#### ~~玄樞~~

- ~~主導 `Sprint 10` gate~~
- ~~收件後裁決 round 順序與開門條件~~

#### ~~衡鑑~~

- ~~主導工程護欄題盤點~~

#### ~~雪鑰~~

- ~~主導 `Settings / session / insight refresh` 類盤點~~

#### ~~鍵律~~

- ~~主導 `AUTO` 模式 bug 類盤點~~

### ~~任務指派~~

#### ~~衡鑑~~

- ~~任務 ID：`S10-0-CXB-ENGINEERING-AUDIT`~~

目標：

- ~~盤點下列工程護欄題的最小 file scope、owner 與禁區：~~
  - ~~`CoroutineExceptionHandler`~~
  - ~~`InputRouter.release()`~~
  - ~~`bgUpdateReceiver`~~
  - ~~test baseline~~

要求：

1. ~~不做 implementation~~
2. ~~只做盤點 / owner map / round cut 建議~~
3. ~~要明確區分哪些能在 `Sprint 10` 內做，哪些應留給 `Sprint 11+`~~

#### ~~雪鑰~~

- ~~任務 ID：`S10-0-CCA-SETTINGS-REFRESH-PRECHECK`~~

目標：

- ~~盤點 `Settings` 登入 `Insight` 後 UI 未刷新題的重現條件、state source 與最小 file scope~~

要求：

1. ~~不改 `.kt`~~
2. ~~只做重現、file scope、state consume 路徑盤點~~
3. ~~要明確區分 UI 未刷新是 lifecycle、repository、還是 session consume 題~~

#### ~~鍵律~~

- ~~任務 ID：`S10-0-CCB-AUTO-MODE-PRECHECK`~~

目標：

- ~~盤點 `AUTO` 模式 bug 的重現條件、candidate path、buffer path 與最小 file scope~~

要求：

1. ~~不改 `.kt`~~
2. ~~只做重現與邏輯路徑盤點~~
3. ~~要明確標出是否涉及 `InputRouter.AUTO` / `getAutoCombinedCandidates()` / commit path~~

### ~~回交結果~~

- ~~`docs/SPRINT10_0_CXB_ENGINEERING_AUDIT_2026-04-18.md`~~
- ~~`docs/SPRINT10_0_CCA_SETTINGS_REFRESH_PRECHECK_2026-04-18.md`~~
- ~~`docs/SPRINT10_0_CCB_AUTO_MODE_PRECHECK_2026-04-18.md`~~

### ~~玄樞 審核結論~~

```text
READY

Reason:
1. 三份前置文件已收齊，且都遵守了 S10-0 的 audit only 邊界，沒有提早進 implementation。
2. `AUTO` 線、`Settings refresh` 線、與 guardrail 線的 file scope 已切開，不會互撞。
3. `Settings refresh` 已明確判定主分類是 session consume；`AUTO` 線也已把 buffer / candidate / commit 三條路徑拆清楚。
4. guardrail 線已證實 `CoroutineExceptionHandler`、`InputRouter.release()`、`bgUpdateReceiver`、test baseline 的 round cut 足夠清楚，可留待後續 round。
5. 依使用者規則，下一門只能開一門；本輪後只開 `S10-2`。
```

### ~~本輪結論~~

- ~~`S10-0 = COMPLETED`~~
- ~~下一門開 `S10-2 = AUTO 模式 bugfix`~~

---

## ~~Round S10-2：AUTO 模式 bugfix~~

### ~~當前狀態~~

- ~~`ACTIVE`~~

### ~~主題~~

- ~~`InputRouter.AUTO`~~
- ~~`getAutoCombinedCandidates()`~~
- ~~AUTO buffer / candidate / commit 一致性~~

### ~~目標~~

- ~~只修 `AUTO` 模式當前最值得先收斂的一條主線 bug~~
- ~~先收斂 `switchTo(AUTO)` / `onStartInputView` 兩條 raw commit 沒走 `applyInputResult` 的不對稱~~
- ~~不在同一門混修 `R1-R8` 全部候選情境~~

### ~~玄樞 本輪裁決~~

~~`S10-2` 本輪 target 鎖定為：~~

1. ~~`R3`：環形選單選 `AUTO` 時，`switchTo(AUTO)` 回傳的 commit 沒走 `applyInputResult`~~
2. ~~`R7`：`onStartInputView` 切 `AUTO` 時，commit 同樣沒走 `applyInputResult`~~

~~不納入本輪的候選：~~

1. ~~`R1` / `R4` / `R5` / `R6`~~
2. ~~`R2`~~
3. ~~`R8`~~

~~裁決理由：~~

1. ~~`R3` / `R7` 同屬 commit path 不對稱，問題邊界最清楚。~~
2. ~~兩題可望共用同一修法，且主線集中在 service callback / service lifecycle，不需要先擴到 candidate 策略或 auto score 規則。~~
3. ~~`R2` / `R8` 偏 candidate / 學詞副作用，`R1` / `R4` / `R5` / `R6` 偏 `InputRouter` 規則題，暫不與本輪混修。~~

### ~~任務指派~~

#### ~~鍵律~~

- ~~任務 ID：`S10-2-CCB-AUTO-COMMIT-PATH-IMPLEMENTATION`~~

目標：

- ~~以最小 file scope 修正 `R3` / `R7` 的 commit path 不對稱~~

~~本輪允許修改：~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/service/ImeServiceCallbacks.kt`~~
- ~~`app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`~~

~~本輪禁止修改：~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt`~~
- ~~candidate 排序 / source map 邏輯~~
- ~~`Settings` / `auth` 相關檔~~
- ~~`Sprint 10` gate / `RULE` / `daily_works_0418.md`~~

### ~~提交鏈~~

1. ~~`鍵律` 完成 `S10-2-CCB-AUTO-COMMIT-PATH-IMPLEMENTATION`~~
2. ~~完成後提交給：`衡鑑`~~
3. ~~`衡鑑` 只做技術二審與 file scope review，不直接接下一門~~
4. ~~`衡鑑` 二審後提交給：`玄樞`~~
5. ~~只有 `玄樞` 判定 `READY`，才可決定是否開 `S10-3`~~

### ~~回交結果~~

- ~~`docs/SPRINT10_2_CCB_AUTO_COMMIT_PATH_IMPLEMENTATION_2026-04-18.md`~~

### ~~玄樞 審核結論~~

```text
READY

Reason:
1. 我已依規則親自打開 `ImeServiceCallbacks.kt` 與 `YukiBoardIMEService.kt` 實檔複核，改動確實只落在 gate 授權的兩個 `.kt`。
2. `R3` / `R7` 兩條 raw `commitText(...)` 已實際改成走 `applyInputResult(...)`，修法與本輪 target 一致，沒有越界碰 `InputRouter.kt`、candidate 邏輯、settings 或 auth。
3. `衡鑑` 的技術二審與 compile 通過結論，和我看到的實檔一致，未見新的 blocking compile / lifecycle / state 問題。
4. 同類 raw commit 路徑在 `ImeServiceInput.kt:636-643` 仍存在，但該檔不在本輪授權 scope 內；這是 non-blocking observation，不構成本輪退件理由。
```

### ~~本輪結論~~

- ~~`S10-2 = COMPLETED`~~
- ~~下一門開 `S10-3 = Settings Insight UI 刷新修復`~~

---

## ~~Round S10-3：Settings Insight UI 刷新修復~~

### ~~當前狀態~~

- ~~`COMPLETED`~~

### ~~主題~~

- ~~`SettingsActivity`~~
- ~~`SettingsScreenCoordinator`~~
- ~~`UserSessionRepository`~~
- ~~`fetchMe / verifySession / onResume` consume 路徑~~

### ~~玄樞 本輪裁決~~

~~`S10-3` 本輪 target 鎖定為：~~

1. ~~採用 `S10-0-CCA` 候選 `A`~~
2. ~~只修 `AuthSettingsSection.kt` 的 session consume rule~~
3. ~~目標是讓 `loggedIn = true` 時，不再因 `displayName == null` 被誤渲染成「登入」~~

~~不納入本輪：~~

1. ~~`InsightSettingsSection` plan badge 補 patch~~
2. ~~`SnowRealmAuthClient` repository / token-only 中間狀態 surface~~
3. ~~`OAuthCallbackActivity` retry~~
4. ~~`SettingsActivity` / `SettingsScreenCoordinator` / `UserSessionRepository` 的結構性調整~~

~~裁決理由：~~

1. ~~`S10-0-CCA` 已明確判定主分類是 session consume，不是 lifecycle 主缺口。~~
2. ~~候選 `A` 單檔最小、風險最低，足以先修主症狀。~~
3. ~~plan badge、repository 中間狀態、callback retry 都屬次級或更深層題，不與本輪混修。~~

### ~~任務指派~~

#### ~~雪鑰~~

- ~~任務 ID：`S10-3-CCA-SETTINGS-CONSUME-RULE-IMPLEMENTATION`~~

~~目標：~~

- ~~以最小 file scope 修正 `AuthSettingsSection.updateAuthUI` 的 consume rule~~

~~本輪允許修改：~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AuthSettingsSection.kt`~~

~~本輪禁止修改：~~

- ~~`SettingsActivity.kt`~~
- ~~`InsightSettingsSection.kt`~~
- ~~`SettingsScreenCoordinator.kt`~~
- ~~`UserSessionRepository.kt`~~
- ~~`SnowRealmAuthClient.kt`~~
- ~~`OAuthCallbackActivity.kt`~~
- ~~任何 `auth` / `service` / `input` 相關檔~~
- ~~`Sprint 10` gate / `RULE` / `daily_works_0418.md`~~

#### ~~衡鑑~~

- ~~任務 ID：`S10-3-CXB-SETTINGS-CONSUME-REVIEW`~~

~~目標：~~

- ~~審核 `雪鑰` 的 `S10-3-CCA-SETTINGS-CONSUME-RULE-IMPLEMENTATION` 回交~~
- ~~確認 file scope、實際 `.kt` 改動、是否越界、是否有明顯 bug~~
- ~~二審完成後提交給 `玄樞`~~

~~本輪審核重點：~~

1. ~~只允許 `AuthSettingsSection.kt`~~
2. ~~不得偷碰 `SettingsActivity.kt`、`InsightSettingsSection.kt`、`SnowRealmAuthClient.kt`~~
3. ~~修法必須對準 consume rule，不得擴成 repository / lifecycle 重構~~
4. ~~若發現 bug、越界、假修、漏修，直接退回，不得自行放行下一門~~

### ~~提交鏈~~

1. ~~`雪鑰` 完成 `S10-3-CCA-SETTINGS-CONSUME-RULE-IMPLEMENTATION`~~
2. ~~完成後提交給：`衡鑑`~~
3. ~~`衡鑑` 做技術二審與 file scope review~~
4. ~~`衡鑑` 二審後提交給：`玄樞`~~
5. ~~只有 `玄樞` 判定 `READY`，才可決定是否開 `S10-4`~~

### ~~回交結果~~

- ~~`docs/SPRINT10_3_CCA_SETTINGS_CONSUME_RULE_IMPLEMENTATION_2026-04-18.md`~~

### ~~玄樞 審核結論~~

```text
READY

Reason:
1. 我已依規則親自打開 `AuthSettingsSection.kt` 實檔複核，改動確實只落在本輪授權單檔。
2. `updateAuthUI` 已改為 `loggedIn = true` 就顯示登出，且 `displayName` 空值時 fallback `tr("已登入")`，修法對準 session consume rule。
3. `衡鑑` 的技術二審與 compile 通過結論，和我看到的實檔一致，未見新的 blocking bug、越界或編譯風險。
4. plan badge、token-only 中間狀態、`OAuthCallbackActivity` retry 仍未處理，但這些都在本輪明確排除 scope 內，不構成本輪退件理由。
```

### ~~本輪結論~~

- ~~`S10-3 = COMPLETED`~~
- ~~下一門開 `S10-4 = Coroutine 例外護欄`~~

---

## ~~Round S10-4：Coroutine 例外護欄~~

### ~~當前狀態~~

- ~~`COMPLETED`~~

### ~~主題~~

- ~~`YukiBoardIMEService.serviceScope`~~
- ~~`CoroutineExceptionHandler`~~
- ~~最小可觀測性與錯誤記錄~~

### ~~玄樞本輪裁決~~

~~`S10-4` 本輪 target 鎖定為：~~

1. ~~只補 `YukiBoardIMEService.serviceScope` 的 `CoroutineExceptionHandler`~~
2. ~~以單檔最小 patch 讓 service coroutine 例外至少可被統一記錄~~
3. ~~不把本輪擴成完整 observability / crash reporting 專案~~

~~不納入本輪：~~

1. ~~`InputRouter.release()` 補完~~
2. ~~`bgUpdateReceiver` / floating path~~
3. ~~`app/src/test` baseline~~
4. ~~Crashlytics 或其他外部上報整合~~

~~裁決理由：~~

1. ~~`S10-0-CXB` 已明確指出這題可在 `YukiBoardIMEService.kt` 單檔做最小補法。~~
2. ~~這是 `Sprint 10` guardrail 線裡風險最低、最不會互撞的一門。~~
3. ~~先把 service exception boundary 補上，再開 release / receiver / test 題較穩。~~
4. ~~因 `衡鑑` 暫時受 `Codex` 額度限制，本輪改由 `鍵律` 實作、`雪鑰` 代班二審。~~

### ~~任務指派~~

#### ~~鍵律~~

- ~~任務 ID：`S10-4-CCB-COROUTINE-EXCEPTION-HANDLER-IMPLEMENTATION`~~

~~目標：~~

- ~~以最小 file scope 補上 `YukiBoardIMEService.serviceScope` 的 `CoroutineExceptionHandler`~~

~~本輪允許修改：~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`~~

~~本輪禁止修改：~~

- ~~`InputRouter.kt`~~
- ~~`ImeServiceCallbacks.kt`~~
- ~~`ImeServiceInput.kt`~~
- ~~`Settings` / `auth` / payment / sync 相關檔~~
- ~~`Sprint 10` gate / `RULE` / `daily_works_0418.md`~~

#### ~~雪鑰~~

- ~~任務 ID：`S10-4-CCA-COROUTINE-EXCEPTION-HANDLER-REVIEW`~~

~~目標：~~

- ~~代班 `衡鑑` 做 `S10-4` 的二次審核~~
- ~~確認 `鍵律` 回交的 file scope、實際 `.kt` 改動、是否越界、是否有明顯 bug~~
- ~~二審完成後提交給 `玄樞`~~

~~本輪審核重點：~~

1. ~~只允許 `YukiBoardIMEService.kt`~~
2. ~~不得偷碰 `InputRouter.kt`、`ImeServiceCallbacks.kt`、`ImeServiceInput.kt`~~
3. ~~修法必須對準 `serviceScope` 的 `CoroutineExceptionHandler`~~
4. ~~若發現 bug、越界、假修、漏修，直接退回，不得自行放行下一門~~

### ~~提交鏈~~

1. ~~`鍵律` 完成 `S10-4-CCB-COROUTINE-EXCEPTION-HANDLER-IMPLEMENTATION`~~
2. ~~完成後提交給：`雪鑰`~~
3. ~~`雪鑰` 代班 `衡鑑` 做二次審核~~
4. ~~`雪鑰` 二審後提交給：`玄樞`~~
5. ~~只有 `玄樞` 判定 `READY`，才可決定是否開 `S10-5`~~

### ~~回交結果~~

- ~~`docs/SPRINT10_4_CCB_COROUTINE_EXCEPTION_HANDLER_IMPLEMENTATION_2026-04-18.md`~~
- ~~`docs/SPRINT10_4_CCA_COROUTINE_EXCEPTION_HANDLER_REVIEW_2026-04-18.md`~~

### ~~玄樞 審核結論~~

```text
READY

Reason:
1. 我已依規則親自打開 `YukiBoardIMEService.kt` 實檔複核，這輪改動確實只落在授權單檔，且重點集中在 import 與 `serviceScope` 組裝區塊。
2. `serviceScopeExceptionHandler` 已實際掛入 `Dispatchers.Main + SupervisorJob() + serviceScopeExceptionHandler`，修法對準本輪 target，沒有擴到 callsite、observer 框架或其他 service/input 檔。
3. `雪鑰` 的代班二審與 compile 通過結論，和我看到的實檔一致；未見新的 blocking bug、越界或語意倒退。
4. 本輪只補最小可觀測性入口，`InputRouter.release()`、receiver / lifecycle、test baseline 仍保留給後續 round，不構成本輪退件理由。
```

### ~~本輪結論~~

- ~~`S10-4 = COMPLETED`~~
- ~~下一門開 `S10-5 = InputRouter 釋放鏈補完`~~

---

## ~~Round S10-5：InputRouter 釋放鏈補完~~

### ~~當前狀態~~

- ~~`COMPLETED`~~

### ~~主題~~

- ~~`InputRouter.release()`~~
- ~~engine / cache / smart subsystem 釋放責任~~

### ~~玄樞本輪裁決~~

~~`S10-5` 本輪 target 鎖定為：~~

1. ~~只補 `InputRouter.release()` 目前已知缺漏的 owner release 鏈~~
2. ~~以最小 file scope 完成可證實的 release 補完，不擴成 input stack 重構~~
3. ~~讓 `release()` 不再只停在 `userDict` 單點清理~~

~~不納入本輪：~~

1. ~~candidate / scoring / auto detect 規則調整~~
2. ~~`YukiBoardIMEService` 其他 lifecycle 重構~~
3. ~~`bgUpdateReceiver` / floating path~~
4. ~~test baseline~~

~~裁決理由：~~

1. ~~`S10-0-CXB-ENGINEERING-AUDIT` 已明確指出這題只能做 owner map + 最小補完，不能擴成整個 input stack 重構。~~
2. ~~這題直接承接 `Sprint 10` guardrail 主線，且與 `S10-4` 的 coroutine guardrail 已切開，不會互撞。~~
3. ~~因 `衡鑑` 仍受 `Codex` 額度限制，本輪維持 `鍵律` 實作、`雪鑰` 代班二審。~~

### ~~任務指派~~

#### ~~鍵律~~

- ~~任務 ID：`S10-5-CCB-INPUTROUTER-RELEASE-IMPLEMENTATION`~~

~~目標：~~

- ~~以最小 file scope 補完 `InputRouter.release()` 的已知釋放缺口~~

~~本輪允許修改：~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/input/router/InputRouter.kt`~~

~~本輪禁止修改：~~

- ~~`YukiBoardIMEService.kt`~~
- ~~`ImeServiceCallbacks.kt`~~
- ~~`ImeServiceInput.kt`~~
- ~~candidate / scoring / auto detect 規則~~
- ~~`Settings` / `auth` / payment / sync 相關檔~~
- ~~`Sprint 10` gate / `RULE` / `daily_works_0418.md`~~

#### ~~雪鑰~~

- ~~任務 ID：`S10-5-CCA-INPUTROUTER-RELEASE-REVIEW`~~

~~目標：~~

- ~~代班 `衡鑑` 做 `S10-5` 的二次審核~~
- ~~確認 `鍵律` 回交的 file scope、實際 `.kt` 改動、是否越界、是否有明顯 bug~~
- ~~二審完成後提交給 `玄樞`~~

~~本輪審核重點：~~

1. ~~只允許 `InputRouter.kt`~~
2. ~~不得偷碰 `YukiBoardIMEService.kt`、`ImeServiceCallbacks.kt`、`ImeServiceInput.kt`~~
3. ~~修法必須對準 `release()` owner 補完，不得擴成 router 規則或 engine 行為重構~~
4. ~~若發現 bug、越界、假修、漏修，直接退回，不得自行放行下一門~~

### ~~提交鏈~~

1. ~~`鍵律` 完成 `S10-5-CCB-INPUTROUTER-RELEASE-IMPLEMENTATION`~~
2. ~~完成後提交給：`雪鑰`~~
3. ~~`雪鑰` 代班 `衡鑑` 做二次審核~~
4. ~~`雪鑰` 二審後提交給：`玄樞`~~
5. ~~只有 `玄樞` 判定 `READY`，才可決定是否開 `S10-6`~~

### ~~回交結果~~

- ~~`docs/SPRINT10_5_CCB_INPUTROUTER_RELEASE_IMPLEMENTATION_2026-04-18.md`~~
- ~~`docs/SPRINT10_5_CCA_INPUTROUTER_RELEASE_REVIEW_2026-04-18.md`~~

### ~~玄樞 審核結論~~

```text
READY

Reason:
1. 我已依規則親自打開 `InputRouter.kt` 實檔複核，這輪改動確實只落在授權單檔，且範圍集中在 `release()` KDoc 與函式體。
2. `release()` 已實際補上 `smartInput.persist()/maintenance()`、eager engine reset、latinEngines 清理、candidate 清理、AUTO buffer 清理與 `userDict.release()` 收尾，修法對準 owner release 鏈補完目標。
3. `雪鑰` 的代班二審與 compile 通過結論，和我看到的實檔一致；未見新的 blocking bug、越界或 lazy engine 誤觸發風險。
4. candidate / scoring / auto detect 規則、其他 lifecycle 題與 test baseline 仍保留給後續 round，不構成本輪退件理由。
```

### ~~本輪結論~~

- ~~`S10-5 = COMPLETED`~~
- ~~下一門開 `S10-6 = Receiver / lifecycle 風險修補`~~

---

## ~~Round S10-6：Receiver / Lifecycle 風險修補~~

### ~~當前狀態~~

- ~~`COMPLETED`~~

### ~~主題~~

- ~~`bgUpdateReceiver`~~
- ~~create / floating / destroy path 對稱性~~

### ~~玄樞本輪裁決~~

~~`S10-6` 本輪 target 鎖定為：~~

1. ~~只收斂 `bgUpdateReceiver` 的 create / floating / destroy path 對稱性~~
2. ~~以最小 file scope 修補 receiver lifecycle 風險，不擴成整個 IME cleanup 重構~~
3. ~~把 `Sprint 10` guardrail 線收口到 service teardown / receiver 這一層~~

~~不納入本輪：~~

1. ~~`InputRouter.release()` 追加補丁~~
2. ~~test baseline~~
3. ~~大型 service cleanup / panel cleanup 重構~~
4. ~~新增觀測框架或其他非必要 guardrail~~

~~裁決理由：~~

1. ~~`S10-0-CXB-ENGINEERING-AUDIT` 已指出這題優先度低於前兩題，但仍屬 `Sprint 10` 真 guardrail。~~
2. ~~`S10-4`、`S10-5` 已收口 coroutine 與 release 鏈，現在開 receiver / lifecycle 題邊界最清楚。~~
3. ~~`衡鑑` 已恢復可承接任務，本輪回復標準鏈：`衡鑑` 實作並直接交 `玄樞` 終審。~~

### ~~任務指派~~

#### ~~衡鑑~~

- ~~任務 ID：`S10-6-CXB-BGUPDATE-RECEIVER-LIFECYCLE-IMPLEMENTATION`~~

~~目標：~~

- ~~以最小 file scope 修補 `bgUpdateReceiver` 的 create / floating / destroy path 對稱性風險~~

~~本輪允許修改：~~

- ~~`app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`~~

~~本輪禁止修改：~~

- ~~`InputRouter.kt`~~
- ~~`ImeServiceCallbacks.kt`~~
- ~~`ImeServiceInput.kt`~~
- ~~`Settings` / `auth` / payment / sync 相關檔~~
- ~~`Sprint 10` gate / `RULE` / `daily_works_0418.md`~~

### ~~提交鏈~~

1. ~~`衡鑑` 完成 `S10-6-CXB-BGUPDATE-RECEIVER-LIFECYCLE-IMPLEMENTATION`~~
2. ~~完成後直接提交給：`玄樞`~~
3. ~~只有 `玄樞` 判定 `READY`，才可決定是否開 `S10-7`~~

### ~~回交結果~~

- ~~`docs/SPRINT10_6_CXB_BGUPDATE_RECEIVER_LIFECYCLE_IMPLEMENTATION_2026-04-18.md`~~

### ~~玄樞 審核結論~~

```text
READY

Reason:
1. 我已依規則親自打開 `YukiBoardIMEService.kt` 實檔複核，這輪改動只落在授權單檔，且主體集中在 `bgUpdateReceiver` helper 與 create/destroy 接線。
2. `registerBgUpdateReceiver()` / `unregisterBgUpdateReceiver()` 已實際落地，floating 與 non-floating 兩條 create path 都先完成 receiver 註冊，destroy path 也統一走同一個解除入口，對準本輪 target。
3. 這輪沒有擴到 receiver payload、action string、其他 cleanup framework 或禁區檔；編譯也已通過，未見新的 blocking bug、越界或 lifecycle 倒退。
```

### ~~本輪結論~~

- ~~`S10-6 = COMPLETED`~~
- ~~下一門開 `S10-7 = 測試骨架前置`~~

---

## Round S10-7：測試骨架前置

### ~~當前狀態~~

- ~~`COMPLETED`~~

### ~~主題~~

- ~~`app/src/test`~~
- ~~`InputRouterTest`~~
- ~~`SmartInputManagerTest`~~
- ~~最小可跑 test baseline~~

### ~~玄樞本輪裁決~~

~~`S10-7` 本輪 target 鎖定為：~~

1. ~~建立 `app/src/test` 最小可跑 baseline~~
2. ~~至少補出 `InputRouterTest` 與 `SmartInputManagerTest` 的可編譯骨架~~
3. ~~不承諾在本輪完成完整功能覆蓋，只先讓 `Sprint 11` 有可延續基線~~

~~不納入本輪：~~

1. ~~大規模測試覆蓋擴張~~
2. ~~instrumentation / `androidTest` 大量建置~~
3. ~~silent catch 第一輪治理~~
4. ~~GL / TTS 技術債修補~~

~~裁決理由：~~

1. ~~`S10-0-CXB-ENGINEERING-AUDIT` 已明確指出 `app/src/test` 目前不存在，這題應定義為先建 baseline，不是直接承諾完整功能測試。~~
2. ~~`Sprint 10` 使用者 bug 與主要 guardrail 已收口，現在可以把最後一門測試前置補上。~~
3. ~~`衡鑑` 已恢復承接，這門回到標準鏈，由 `衡鑑` 實作並直接交 `玄樞` 終審。~~

### ~~任務指派~~

#### ~~衡鑑~~

- ~~任務 ID：`S10-7-CXB-TEST-BASELINE-IMPLEMENTATION`~~

~~目標：~~

- ~~建立 `app/src/test` 最小可跑 baseline，並補出 `InputRouterTest` / `SmartInputManagerTest` 骨架~~

~~本輪允許修改：~~

- ~~`app/src/test/**`~~
- ~~`app/build.gradle`~~

~~本輪禁止修改：~~

- ~~`app/src/main/**`~~
- ~~`app/src/androidTest/**`~~
- ~~`Sprint 10` gate / `RULE` / `daily_works_0418.md`~~

### ~~提交鏈~~

1. ~~`衡鑑` 完成 `S10-7-CXB-TEST-BASELINE-IMPLEMENTATION`~~
2. ~~完成後直接提交給：`玄樞`~~
3. ~~只有 `玄樞` 判定 `READY`，才可裁定 `Sprint 10` 是否正式結束~~

### ~~回交結果~~

- ~~`docs/SPRINT10_7_CXB_TEST_BASELINE_IMPLEMENTATION_2026-04-18.md`~~

### ~~玄樞 審核結論~~

```text
READY

Reason:
1. 我已依規則親自打開 `app/build.gradle`、`InputRouterTest.kt`、`SmartInputManagerTest.kt` 實檔複核，這輪改動只落在 gate 授權的 test baseline 範圍。
2. `app/build.gradle` 只補了 Robolectric baseline 所需的 `includeAndroidResources` 與測試依賴；兩個 test 檔也都屬最小可跑骨架，沒有越界碰 production code。
3. 我已親自執行 `:app:testDebugUnitTest`，結果 `BUILD SUCCESSFUL`，表示本輪不是紙上骨架，而是可實際執行的 baseline。
4. 這輪不承諾完整覆蓋率，只完成 `Sprint 10` 最後一門 baseline 前置；更深的測試擴張仍留給 `Sprint 11`。
```

### ~~本輪結論~~

- ~~`S10-7 = COMPLETED`~~
- ~~`Sprint 10 = COMPLETED`~~

### 說明

- 待前面工程護欄題完成後，再決定是否開門。

---

## Sprint 10 當前總狀態

- 進行中：
  - 無
- 已取消：
  - `S10-1` `日文片假名 / 羅馬拼音切換修復`
- 已完成：
  - `S10-0` `前置盤點與重現`
  - `S10-2` `AUTO 模式 bugfix`
  - `S10-3` `Settings Insight UI 刷新修復`
  - `S10-4` `Coroutine 例外護欄`
  - `S10-5` `InputRouter 釋放鏈補完`
  - `S10-6` `Receiver / lifecycle 風險修補`
- 待前一門完成後開門：
  - 無
- `玄樞` 當前判定：
  - `Sprint 10 = COMPLETED`
  - `Sprint 11+` 仍維持 `LOCKED`

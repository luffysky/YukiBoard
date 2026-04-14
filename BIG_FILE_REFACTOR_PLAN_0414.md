# Big File Refactor Plan 0414

日期: 2026-04-14  
用途: 針對目前超大 Kotlin 檔案建立正式拆分計畫，明確標記負責人、拆分方式、邊界、驗證要求，確保後續重構不破壞既有功能

## 1. 這份文件要解決什麼

目前專案中，超大檔案已經成為主要維護風險來源。這份文件只處理以下 4 個超大檔：

- [YukiBoardIMEService.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt)
- [KeyboardView.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/ui/keyboard/KeyboardView.kt)
- [CangjieInputEngine.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/input/engine/CangjieInputEngine.kt)
- [JapaneseInputEngine.kt](/D:/SnowRealmRebirth/SnowRealmYukiBoard/app/src/main/java/com/snowrealm/yukiboard/input/engine/JapaneseInputEngine.kt)

這輪不處理 1200 行以下檔案。

## 2. 固定原則

- 不砍功能
- 不改使用者可見功能語意
- 不改既有設定鍵名、資料格式、同步格式
- 不做大爆炸重寫
- 先立骨架，再搬邏輯，再刪舊碼
- 每一輪都必須維持 `:app:compileDebugKotlin` 可通過
- 每一輪都要補對應 smoke test
- 拆分目標是「更容易維護」，不是只把行數分散出去

## 3. 責任分工

### CodeXA

負責：

- 架構監督
- 拆分順序控制
- 邊界審查
- 風險控管
- 更新文件與待辦

不負責：

- 和 `CodeXB` / `Claude CodeA` 搶同一批核心檔
- 直接執行大段功能搬遷

### CodeXB

負責：

- `YukiBoardIMEService.kt` 拆分主線
- 之後接 `KeyboardView.kt` 的低風險 delegate 骨架拆分
- IME 相關 smoke 驗證

不負責：

- settings section 細節
- 登入 bug 主線
- 同輪同時大改輸入引擎

### Claude CodeA

負責：

- 持續維護 settings 區與 smoke review
- 協助觀察重構後是否有 UI 回歸
- 在 `CodeXB` 先立骨架後，配合做低風險邊界整理

不負責：

- 主動拆 `YukiBoardIMEService.kt`
- 主動拆 `KeyboardView.kt`
- 主動拆 `CangjieInputEngine.kt`
- 主動拆 `JapaneseInputEngine.kt`

## 4. 優先順序

固定順序如下：

1. `YukiBoardIMEService.kt`
2. `KeyboardView.kt`
3. `JapaneseInputEngine.kt`
4. `CangjieInputEngine.kt`

理由：

- 先瘦平台超級控制器，能降低整體耦合
- 再拆 UI 大檔，降低互動層風險
- 最後才處理輸入引擎規則拆分，避免同時碰平台層與演算法主線

## 5. 各檔案拆分計畫

### 5.1 YukiBoardIMEService.kt

目前狀態：

- 已開始拆出 `ImeSessionHooks`
- `ImePanelFactory`
- `ImePanelBindings`
- `ImePanelCleanup`
- `ImePanelHost`
- `ImePanelCoordinator`

主責：

- `CodeXB`

`CodeXA` 監督重點：

- 不可誤碰 `handleKeyPress()`
- 不可改壞 `applyInputResult()` 語意
- 不可提早擴張到高耦合 panel

拆分順序：

1. `ImeLifecycleCoordinator`
   - 收 `onStartInputView`
   - `onFinishInputView`
   - `onFinishInput`
   - `onDestroy`
   - 目標：把 cleanup / session 收尾順序固定化

2. `ImePanelRouter`
   - 收 `handlePanelToggle`
   - `showPanel`
   - `closePanel`
   - 目標：service 不再直接承擔 panel 建立與切換決策

3. `ImeVoiceController`
   - 收 `toggleVoiceInput`
   - `stopVoiceInput`
   - mic / TTS / listening state 協調

4. `ImeThemeRenderDelegate`
   - 收 `applyTheme`
   - toolbar / keyboard / background / character render 同步

5. `ImeCandidateInteractionHandler`
   - 候選點選
   - panel input target routing
   - candidate speak

這輪明確不做：

- 不拆 `handleKeyPress()` 主流程
- 不拆 `applyInputResult()` 核心語意
- 不主動碰 `USER_DICT` 匯入匯出/外部檔案流程
- 不主動碰 `THEME / CLAW / REMOTE / HANDWRITING / MUSIC`

每輪最低驗證：

- 基本輸入
- 候選字選取
- 面板開關
- panel 關閉後焦點恢復
- 語音輸入開始/停止
- 切 app / 收鍵盤 / 重開鍵盤

### 5.2 KeyboardView.kt

主責：

- `CodeXB`
- 前提：`YukiBoardIMEService.kt` 先穩到可持續薄切

`CodeXA` 監督重點：

- 不讓 view 內業務邏輯變更多
- 只拆 render / touch / layout / animation / accessibility
- 不把 InputRouter 規則硬塞進 view delegate

拆分順序：

1. `KeyboardLayoutCalculator`
   - key bounds
   - row spacing
   - hit area

2. `KeyboardRenderer`
   - `onDraw()` 相關
   - key background
   - label/icon draw

3. `KeyboardTouchController`
   - touch down/move/up
   - long press
   - swipe
   - reset touch state

4. `KeyboardAnimationDelegate`
   - key press animation
   - toolbar animation style

5. `KeyboardAccessibilityDelegate`
   - accessibility event
   - 朗讀與提示

這輪明確不做：

- 不改鍵盤輸入規則語意
- 不改對外 callback 介面
- 不把 AI / panel / network 邏輯塞進 view delegate

每輪最低驗證：

- 點擊按鍵
- 長按
- 滑動輸入
- 候選點選
- 觸控狀態 reset

### 5.3 JapaneseInputEngine.kt

主責：

- `CodeXB`
- 啟動條件：`YukiBoardIMEService.kt` 與 `KeyboardView.kt` 先進入穩定期

`CodeXA` 監督重點：

- 不在同輪同時大改 IME 平台殼
- 只拆規則、狀態、候選來源，不變更外部使用介面

拆分順序：

1. `JapaneseRomajiConverter`
   - romaji -> kana 規則

2. `JapaneseCompositionBuffer`
   - composing state
   - backspace / clear / confirm transition

3. `JapaneseCandidateProvider`
   - 候選查詢
   - 字典/學習來源取得

4. `JapaneseConversionCoordinator`
   - 轉換模式切換
   - 未轉換 / 已轉換 / 候選中 狀態管理

5. `JapaneseLearningAdapter`
   - 學習回寫

這輪明確不做：

- 不調整使用者可見日文輸入行為
- 不混入平台層 lifecycle 或 UI 邏輯

每輪最低驗證：

- romaji -> kana
- backspace
- 候選切換
- commit text
- clear composing

### 5.4 CangjieInputEngine.kt

主責：

- `CodeXB`
- 啟動條件：日文引擎拆分策略已穩定

`CodeXA` 監督重點：

- 嚴格維持「規則 / 狀態 / 候選 / 排序」分離
- 不在 engine 本體保留全部查詢與學習邏輯

拆分順序：

1. `CangjieCodeParser`
   - 拆碼
   - 合法性判斷

2. `CangjieCompositionState`
   - composing buffer
   - reset / clear / select transition

3. `CangjieCandidateProvider`
   - 候選來源查詢

4. `CangjieRankingPolicy`
   - 候選排序
   - 學習優先級

5. `CangjieLearningAdapter`
   - 學習資料存取

這輪明確不做：

- 不重寫整套引擎
- 不順手改別的輸入模式

每輪最低驗證：

- 基本拆碼
- 候選產生
- 選字
- backspace
- reset

## 6. 安全拆分模式

每個檔案都照以下流程拆，不准跳步：

1. 先新增新類別或 delegate
2. 舊檔改成呼叫新類
3. 保持對外介面與回傳型別不變
4. compile 驗證
5. smoke 驗證
6. 最後才刪除舊內嵌邏輯

禁止方式：

- 直接刪大片舊碼再補
- 同一輪同時重寫平台層與演算法層
- 一邊拆分一邊改功能語意

## 7. Smoke Test 基準

### IME 主流程

- 鍵盤正常叫出
- 基本輸入
- 候選字顯示與選取
- composing text 正常
- panel 開關正常
- panel 關閉後鍵盤焦點恢復

### Panel 路徑

- `EMOJI`
- `KAOMOJI`
- `CLIPBOARD`
- `CALCULATOR`
- `PHRASE`
- `COLOR_PICKER`
- `UNIT_CONVERTER`
- `TRANSLATE`
- `GIF`
- `DECRYPT`
- `STICKER`

### 語音

- 開始 listening
- 停止 listening
- TTS 不搶 mic

### Settings 觀察

- Settings 頁能正常開啟
- 主要 section 顯示正常
- 不因 IME 重構導致設定頁狀態錯亂

## 8. 交接格式

每輪拆分完成後，固定用這個格式交接：

- A. 變更摘要
- B. 風險
- C. 驗證結果
- D. 下一步建議
- E. 本輪明確未做

## 9. 當前執行結論

目前最適合立刻推進的是：

1. `CodeXB` 繼續完成 `YukiBoardIMEService.kt` 的 panel/lifecycle/voice/theme 薄切
2. `CodeXB` 補 `STICKER` 路徑 smoke test
3. `Claude CodeA` 持續維持 settings 邊界，不碰這 4 個核心大檔
4. `CodeXA` 持續監督是否有擴大 file scope 或改壞主流程

一句話原則：

> 先把超級控制器變成平台殼，再拆 UI 大檔，最後拆輸入引擎；每一步都必須可 compile、可 smoke、不可掉功能。

## 10. Sprint 3 正式接線決策（2026-04-15）

此段以 [IME_STATE_CONVERGENCE_DRAFT.md](/D:/SnowRealmRebirth/SnowRealmYukiBoard/IME_STATE_CONVERGENCE_DRAFT.md) 為正式決策同步。

### 第一批正式接入 `ImeUiState` 的 UI 欄位

- `activePanel`
- `isVoiceInputActive`

### 明確延後的欄位

- `composingText`
- `candidates`

延後原因：

- 目前同步直寫 UI 的路徑仍然過多
- 若在 Phase 1 就改由 `ImeUiState` consume，現階段有 race / 延遲風險
- 需等 direct UI write 路徑先收斂，再進下一批

### consume owner 決策

- Sprint 3 第一批讀取端，先維持由 `YukiBoardIMEService` 作為唯一 consume owner
- 不直接讓 `KeyboardView.kt` 自己 collect `ImeUiState`

### Sprint 3 第一批明確不做

- 不碰 `handleKeyPress()`
- 不碰 `applyInputResult()` 主語意
- 不進 `USER_DICT`
- 不切 `KeyboardView.kt`

### 高依賴 panel 風險排序（更新版）

1. `THEME`
2. `CLAW`
3. `REMOTE`
4. `HANDWRITING`
5. `MUSIC`
6. `USER_DICT`

### 執行含義

- `Claude CodeB` 可依這份決策準備 consume 端接線草案
- `CodeXB` 依這份決策主導 Sprint 3 的最小狀態來源落地
- `CodeXA` 之後監督時，以這份同步過的計畫為準，不再以口頭描述為準

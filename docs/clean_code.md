# Universal Clean Code 規範 v1.0

> 適用範圍：所有前端、後端、全端、AI 協作、工具型、SaaS、個人專案與商業專案。
>
> 目的：降低維護成本、避免技術債失控、提升多人與多 AI 協作的一致性。

---

## 0. 核心原則

所有程式碼必須符合以下四個核心要求：

1. **可讀性（Readable）**：讓人一眼理解用途與流程。
2. **可維護性（Maintainable）**：讓未來修改時風險可控。
3. **可預期性（Predictable）**：行為明確，不靠猜測。
4. **可隔離性（Isolated）**：修改一處，不應連鎖炸裂整個系統。

### 優先級

當規則衝突時，優先順序如下：

**可讀性 > 可維護性 > 效能優化 > 炫技寫法**

---

## 1. 命名規範（Naming Rules）

### 原則

- 名稱必須清楚表達用途。
- 禁止使用模糊命名。
- 不使用沒有上下文的縮寫。
- 讓名稱本身成為文件的一部分。

### 必須遵守

- 變數名稱要能直接表達資料意義。
- function 名稱必須以動詞開頭。
- boolean 名稱必須以 `is`、`has`、`can`、`should` 開頭。
- 陣列名稱應呈現複數概念。
- 常數名稱應具體描述用途，不可只寫值的外觀。

### 建議範例

```ts
const userProfile = getUserProfile()
const orderItems = getOrderItems()
const isUserLoggedIn = checkLoginStatus()
const hasPermission = validatePermission()
function createPaymentSession() {}
function updateUserSettings() {}
```

### 禁止範例

```ts
const data = getData()
const temp = {}
const obj = {}
const item = list[0]
function doThing() {}
function handleStuff() {}
```

---

## 2. 函式規範（Function Rules）

### 原則

- 一個 function 只做一件事。
- 一個 function 應該能用一句話描述用途。
- 小函式優於大函式。

### 必須遵守

- 單一職責（Single Responsibility）。
- 每個 function 盡量控制在 **10～20 行**，上限 **30 行**。
- 巢狀邏輯不得超過 **2 層**。
- 參數數量盡量不超過 **3 個**；超過時應改用物件參數。
- 必須明確定義輸入與輸出。
- 儘量避免隱性副作用。

### 不良訊號

若 function 出現以下特徵，通常代表該拆分：

- 同時做驗證、轉換、存取 API、更新 UI
- 含大量 `if / else`
- 多個 `try / catch`
- 同時操作多種資料來源
- 名稱需要用「and」連接兩個以上動作

---

## 3. 分層規範（Separation of Concerns）

### 原則

不同責任必須放在不同層，不可混寫。

### 建議分層

- `components`：UI 顯示
- `hooks`：可重用邏輯
- `services`：API / 外部服務呼叫
- `features`：業務邏輯
- `stores`：狀態管理
- `types`：型別定義
- `utils`：純工具函式

### 必須遵守

- UI 元件內不得寫複雜業務邏輯。
- UI 元件內不得直接寫 API 呼叫。
- `services` 不得處理畫面顯示。
- `utils` 必須保持純函式，不耦合框架。
- 商業規則不得散落在多個頁面元件中。

### 禁止行為

- component 裡同時做畫面、資料請求、驗證、轉單、追蹤事件
- service 內直接操作 DOM 或 UI 狀態
- store 內夾雜大量 view-specific formatting

---

## 4. 重複控制（DRY）

### 原則

重複邏輯會讓 bug 成倍增加。

### 必須遵守

- 相同商業規則不得重複出現 2 次以上。
- 共用邏輯應抽成 function、hook、service 或 utility。
- 共用字串、常數、設定值應集中管理。

### 例外

不要為了 DRY 而過度抽象。

若兩段程式碼只是表面相似、未來可能獨立演化，允許保留適度重複。

**原則：避免重複，不是追求抽象成癮。**

---

## 5. 狀態管理規範（State Rules）

### 原則

狀態來源要清楚，流向要明確。

### 必須遵守

- state 必須有單一可信來源（Single Source of Truth）。
- 跨元件共享狀態應集中管理，不可到處複製一份。
- Prop drilling 超過 2 層時，應考慮 store、context 或更合理的結構。
- UI state 與 business state 應分開管理。

### 分類建議

- UI state：Modal 開關、tab、hover、toast
- Domain state：使用者資料、訂單資料、權限狀態
- Server state：API 取得的後端資料

### 禁止行為

- 同一筆資料同時存在 3 個以上來源且沒有同步規則
- 在多個元件各自維護同一份業務狀態
- 畫面元件直接改動核心商業狀態而沒有中介邏輯

---

## 6. 副作用規範（Side Effects）

### 原則

副作用要集中、可追蹤、可控。

### 包含副作用的操作

- API 呼叫
- localStorage / sessionStorage
- cookie 存取
- 導頁
- 訂閱 / 取消訂閱
- timer
- analytics / logging
- 檔案讀寫

### 必須遵守

- 副作用必須集中於明確位置（例如 hook、service、effect handler）。
- render 過程中不得執行副作用。
- 必須處理副作用失敗情境。
- timer / subscription 必須正確清除。

---

## 7. 錯誤處理規範（Error Handling）

### 原則

錯誤不能假裝沒看到。

### 必須遵守

- 所有 API / I/O 操作都必須有錯誤處理。
- 不可 silent fail。
- 必須提供適當 fallback、提示或 logging。
- 錯誤訊息應可追蹤，不可只有模糊提示。

### 建議

- 區分使用者可見錯誤與系統內部錯誤。
- 針對可預期錯誤建立明確錯誤型別。
- 前端錯誤文案對人說話，log 對工程師說話。

---

## 8. 型別安全規範（Type Safety）

### 原則

型別不是裝飾，是防爆層。

### 必須遵守

- 禁止濫用 `any`。
- 所有 API response 必須定義型別。
- 所有公開 function 必須有明確輸入與 return type。
- 複雜資料結構必須建立獨立 type / interface / schema。
- 前後端資料契約必須一致。

### 允許例外

若因第三方套件限制暫時使用 `any`，必須加上註解說明原因與待改善項目。

---

## 9. 檔案與資料夾結構規範（Project Structure）

### 原則

結構要讓新人和未來的自己都能迅速定位。

### 建議結構

```bash
/src
  /components
  /features
  /hooks
  /services
  /stores
  /types
  /utils
  /constants
```

### 必須遵守

- 一個檔案只負責一個主要模組。
- 檔名必須與內容責任一致。
- 不得把無關邏輯塞進「萬用檔」。
- `index` 檔只能做合理匯出，不可藏邏輯。

### 禁止行為

- `helper.ts` 裡塞 40 個不相關函式
- `common.ts` 變成垃圾場
- 一個檔案包含 UI、API、type、business logic 全部混在一起

---

## 10. Component 規範（前端 / React 類專案）

### 原則

component 先是 UI，再來才是互動；不是邏輯垃圾桶。

### 必須遵守

- component 優先保持純 UI。
- 複雜邏輯抽到 hook。
- API 請求抽到 service 或 data layer。
- 單一 component 盡量不超過 **150 行**。
- 畫面組裝與商業規則分離。

### 建議分類

- Presentational Component：純展示
- Container / Feature Entry：接資料與組裝
- Shared UI：通用元件

---

## 11. 常數與魔法值規範（Constants & Magic Numbers）

### 原則

看不懂的數字與字串，就是未來炸彈。

### 必須遵守

- 魔法數字必須命名。
- 重複字串應集中為常數。
- 時間、限制值、狀態碼等需統一定義。

### 範例

```ts
const MAX_UPLOAD_SIZE_MB = 5
const SESSION_TIMEOUT_MINUTES = 30
const DEFAULT_PAGE_SIZE = 20
```

---

## 12. 註解規範（Comment Rules）

### 原則

優秀程式碼應減少對註解的依賴，但不是完全禁止註解。

### 可以寫的註解

- 為什麼這樣做
- 臨時 workaround 原因
- 第三方限制
- 高風險邏輯的背景說明

### 不應寫的註解

- 重述程式碼表面意思
- 已過時卻沒更新的註解
- 拿註解掩蓋爛命名

### 禁止範例

```ts
// 把使用者設為已登入
isLoggedIn = true
```

---

## 13. 測試與可測性規範（Testability）

### 原則

寫不出測試，通常代表耦合過高。

### 必須遵守

- 核心商業邏輯應可脫離 UI 測試。
- function 應易於 mock 與隔離。
- 測試重點放在業務規則、邊界條件、失敗情境。

### 建議優先測試區域

1. 訂單 / 支付流程
2. 權限控制
3. 資料轉換
4. 表單驗證
5. AI 或外部 API 串接結果處理

---

## 14. 效能規範（Performance Rules）

### 原則

不要以效能之名，把 code 寫成古文明密碼。

### 必須遵守

- 先正確，再優化。
- 僅在有明確瓶頸時進行效能優化。
- 效能優化不能犧牲結構清晰度到無法維護。

### 建議

- 避免過早優化
- 以 profiling / measurement 為基準
- 記錄優化前後差異

---

## 15. Commit 與重構規範（Commit & Refactor Rules）

### 必須遵守

- 每次 commit 應小而明確。
- 一次只處理一類問題。
- 重構不得偷偷改商業行為，除非有明確需求。
- 重構前應先確認現有行為。

### 建議 commit 類型

- `refactor:` 結構重整，不改功能
- `fix:` 修 bug
- `feat:` 新功能
- `chore:` 雜務 / 工具 / 設定
- `docs:` 文件更新

---

## 16. 禁止事項（Hard Rules）

以下為高風險反模式，原則上禁止：

- God function（超長、超多責任）
- God component
- 深層巢狀超過 3 層
- 模糊命名
- 未處理錯誤
- 重複商業邏輯
- UI / API / business logic 混寫
- 濫用 any
- 魔法值散落
- 過度抽象導致可讀性崩壞
- 建立「暫時先這樣」但沒有註記與追蹤

---

## 17. AI 協作規範（給 Antigravity / Copilot / Claude / GPT）

### AI 必須遵守

1. 嚴格依本文件輸出程式碼。
2. 不得破壞既有功能。
3. 不得把 UI、商業邏輯、API 混在同一層。
4. 若需求本身違反本規範，應先提出風險，再寫 code。
5. 輸出前應先說明結構與改動範圍。
6. 若無法確保安全重構，應明確指出風險點。

### 建議固定 Prompt

```md
Follow /docs/clean-code.md strictly.

Requirements:
- Keep existing behavior unchanged
- Separate UI, logic, and API
- Improve naming and readability
- Avoid duplication
- Use strict typing
- Explain structure before writing code

If any clean code rule would be violated, stop and explain the risk first.
```

### 建議 Review Prompt

```md
Review this code based on /docs/clean-code.md.

Check:
1. Naming quality
2. Function complexity
3. Architecture violations
4. State management issues
5. Duplication
6. Error handling gaps
7. Type safety problems
8. Refactor suggestions
```

---

## 18. 最終判斷標準（Final Quality Check）

每段程式碼合併前，至少問這 6 題：

1. 這段 code 能不能一眼看懂？
2. 三天後我還敢不敢改它？
3. 改這裡會不會爆別的地方？
4. 命名是否清楚到幾乎不需要註解？
5. UI、邏輯、資料存取是否已分層？
6. 若交給另一個人或另一個 AI，他能否快速接手？

只要其中 2 題答不出「是」，就不算乾淨。

---

## 19. 一句話版本

**Clean Code 不是寫得漂亮，而是讓未來的你不會想掐死現在的自己。**


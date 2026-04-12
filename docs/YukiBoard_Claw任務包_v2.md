# YukiBoard × SnowRealmClaw 整合任務包 v2

> 給 Claude Code 讀取的完整任務文件。
> 請依照順序執行，每個階段完成後再進行下一個。
> 最後更新：2026年4月

---

## 商業邏輯說明

```
免費功能
├── 基本鍵盤（英文/注音）
├── 流光動畫（基礎款）
├── 雪凜浮動（基礎）
└── 鍵盤遊戲

付費功能（Z幣 或 直接購買）— 之後版本
├── 主題皮膚
├── 節日限定特效
├── 角色貼圖包
└── 進階動畫（VRM 升級）

AI 功能（邀請碼內測期）
└── 輸入邀請碼解鎖，之後改接 Z幣
    邀請碼由 SnowRealm 官方發放
    不燒平台 AI 費用，邀請碼管控用量
```

---

## 執行順序

1. **Phase 1** — AI 供應商基礎架構
2. **Phase 2** — 邀請碼系統（取代帳號登入）
3. **Phase 3** — Claw Skill 六大功能
4. **Phase 4** — Claw UI 元件

---

## Phase 1：AI 供應商基礎架構

### 目錄結構
```
app/src/main/java/com/snowrealm/yukiboard/claw/
└── provider/
    ├── AIProvider.kt
    ├── AIProviderManager.kt
    ├── OpenAIProvider.kt
    ├── AnthropicProvider.kt
    └── SnowRealmProvider.kt
```

### AIProvider.kt — 供應商介面
```kotlin
enum class AIProviderType {
    OPENAI,      // GPT-4o / GPT-4o-mini
    ANTHROPIC,   // Claude Sonnet / Haiku
    SNOWREALM    // 未來接 Z幣（預留）
}

data class AIProviderConfig(
    val type: AIProviderType,
    val apiKey: String,
    val model: String,
    val baseUrl: String
)

interface AIProvider {
    suspend fun complete(prompt: String, systemPrompt: String): Flow<String>
    suspend fun validateKey(): Boolean
    fun getTokenCount(text: String): Int
}
```

### AIProviderManager.kt
- 從 `EncryptedSharedPreferences` 安全讀寫 API Key
- 管理目前選擇的供應商
- 驗證 API Key 是否有效（打測試請求）
- 預設使用 OpenAI（費用較低）
- 邀請碼啟用後優先使用平台 Key（`BuildConfig.PLATFORM_API_KEY`）
- 未啟用邀請碼則使用用戶自己填的 Key

### OpenAIProvider.kt
- 支援 GPT-4o-mini（預設，便宜）
- 支援 GPT-4o（進階）
- Streaming 回應（Flow<String>）
- 錯誤處理：rate limit / invalid key / network error
- Base URL：https://api.openai.com/v1

### AnthropicProvider.kt
- 支援 claude-haiku-3（預設，便宜）
- 支援 claude-sonnet-4-5（進階）
- Streaming 回應（Flow<String>）
- Base URL：https://api.anthropic.com

### SnowRealmProvider.kt（預留）
- Z幣計費系統接口（暫時空實作，回傳 NotImplementedError）
- 之後接 SnowRealmCore 帳號系統

---

### AISettingsActivity.kt

UI 設計：
- 深藍背景 `#0f1f3d`
- 頂部雪凜小圖示（`assets/lottie/yuki_idle.json`）
- 分三個區塊：

**區塊一：AI 啟用狀態（最上方）**
- 未啟用邀請碼：
  - 說明文字「輸入邀請碼解鎖 AI 功能」
  - 「輸入邀請碼」按鈕 → 跳 InviteCodeActivity
- 已啟用邀請碼：
  - ✅ 綠色「AI 功能已啟用（邀請碼）」
  - 顯示每日剩餘 token 用量
- 自訂 API Key 模式：
  - 顯示目前使用的供應商和模型

**區塊二：AI 供應商選擇**
- 說明：「邀請碼啟用後不需要填寫，或填入自己的 Key 取得更多用量」
- Radio Button：OpenAI / Anthropic / SnowRealm（顯示「即將推出」灰色）
- API Key 輸入框（密碼遮罩，眼睛圖示切換顯示）
- 模型選擇下拉：
  - OpenAI: GPT-4o-mini ★推薦 / GPT-4o
  - Anthropic: claude-haiku ★推薦 / claude-sonnet
- 測試連線按鈕
  - 成功：綠色勾勾 + 「連線成功」
  - 失敗：紅色 X + 錯誤原因

**區塊三：用量顯示**
- 今日已用 token 數 / 每日免費額度（5000）
- 各 Skill 用量明細
- 重置時間顯示

**安全說明（頁面底部）**
- 「您的 API Key 使用 Android 系統加密儲存於本機，不會上傳至任何伺服器」

---

### build.gradle 新增依賴
```groovy
// 安全儲存
implementation 'androidx.security:security-crypto:1.1.0-alpha06'

// HTTP 客戶端
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

// JSON 序列化
implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3'

// 非同步
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0'

// BuildConfig 欄位
buildConfigField "String", "INVITE_HMAC_SECRET", '"${System.getenv("INVITE_HMAC_SECRET") ?: "yukiboard_dev_2026"}"'
buildConfigField "String", "PLATFORM_API_KEY", '"${System.getenv("PLATFORM_API_KEY") ?: ""}"'
```

### AndroidManifest.xml 新增
```xml
<uses-permission android:name="android.permission.INTERNET" />

<activity
    android:name=".ui.settings.AISettingsActivity"
    android:label="AI 設定"
    android:exported="false" />

<activity
    android:name=".ui.auth.InviteCodeActivity"
    android:label="輸入邀請碼"
    android:exported="false" />
```

---

## Phase 2：邀請碼系統

> 不需要帳號登入，不打任何外部 API。
> 邀請碼本機驗證，用 HMAC-SHA256 簽名防偽造。

### 目錄結構
```
app/src/main/java/com/snowrealm/yukiboard/
├── auth/
│   └── InviteCodeManager.kt
└── ui/
    └── auth/
        └── InviteCodeActivity.kt

scripts/
└── generate_invite_codes.py
```

### InviteCodeManager.kt
```kotlin
enum class InviteCodeType {
    STANDARD,   // 標準邀請碼，每日 5000 token
    KOL,        // KOL 專用，每日 20000 token
    PERMANENT   // 永久無限制（內部測試用）
}

class InviteCodeManager(context: Context) {

    // 驗證邀請碼（HMAC-SHA256）
    // 格式：YUKI-{TYPE}{DATE}-{HMAC前8碼}
    // 例：YUKI-S2026Q1-A3F9B2C1
    fun validateCode(code: String): InviteCodeType?

    // 儲存已驗證的邀請碼
    fun activateCode(code: String, type: InviteCodeType)

    // 是否已啟用
    fun isActivated(): Boolean

    // 取得邀請碼類型
    fun getCodeType(): InviteCodeType?

    // 每日 token 上限（根據 type）
    fun getDailyTokenLimit(): Int {
        return when (getCodeType()) {
            InviteCodeType.STANDARD -> 5000
            InviteCodeType.KOL -> 20000
            InviteCodeType.PERMANENT -> Int.MAX_VALUE
            null -> 0
        }
    }

    // 重置每日用量（每日 00:00）
    fun resetDailyUsageIfNeeded()

    // 撤銷（清除邀請碼）
    fun revokeCode()
}
```

**HMAC 驗證邏輯：**
```kotlin
// 邀請碼格式：YUKI-{PAYLOAD}-{HMAC}
// PAYLOAD = {TYPE_CHAR}{YYYYMM}（例：S202604 = Standard 2026年4月）
// HMAC = HMAC-SHA256(PAYLOAD, BuildConfig.INVITE_HMAC_SECRET) 取前8碼大寫
// 驗證：重新計算 HMAC，比對是否一致
// 有效期：YYYYMM 需在當月或未來3個月內
```

---

### InviteCodeActivity.kt

UI 設計：
- 深藍背景 `#0f1f3d`
- 頂部雪凜動畫（`yuki_idle.json`）
- 標題：「輸入邀請碼」
- 副標題：「解鎖 YukiBoard AI 功能」
- 邀請碼輸入框：
  - 自動補 `YUKI-` 前綴
  - 自動大寫
  - 格式提示：`YUKI-XXXXXXXX`
- 「啟用」按鈕
- 成功：
  - 雪凜慶祝動畫（`yuki_happy.json`）
  - 「🎉 AI 功能已解鎖！」
  - 顯示每日可用 token 數
  - 2 秒後自動關閉返回
- 失敗：
  - 雪凜搖頭動畫
  - 「邀請碼無效或已過期」紅色提示
- 底部：「跳過，使用自己的 API Key」文字按鈕

---

### generate_invite_codes.py（腳本）

```python
#!/usr/bin/env python3
"""
YukiBoard 邀請碼產生器
用法：python generate_invite_codes.py --type standard --count 100 --month 202604
"""

import hmac
import hashlib
import argparse
import csv
from datetime import datetime

TYPE_MAP = {
    'standard': 'S',
    'kol': 'K',
    'permanent': 'P'
}

def generate_code(type_char: str, year_month: str, secret: str) -> str:
    payload = f"{type_char}{year_month}"
    signature = hmac.new(
        secret.encode(),
        payload.encode(),
        hashlib.sha256
    ).hexdigest()[:8].upper()
    return f"YUKI-{payload}-{signature}"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--type', choices=['standard', 'kol', 'permanent'], required=True)
    parser.add_argument('--count', type=int, default=10)
    parser.add_argument('--month', default=datetime.now().strftime('%Y%m'))
    parser.add_argument('--secret', default='yukiboard_dev_2026')
    args = parser.parse_args()

    type_char = TYPE_MAP[args.type]
    codes = []
    for i in range(args.count):
        # 加入序號讓每個碼不同
        payload = f"{type_char}{args.month}{i:04d}"
        signature = hmac.new(
            args.secret.encode(),
            payload.encode(),
            hashlib.sha256
        ).hexdigest()[:8].upper()
        code = f"YUKI-{payload}-{signature}"
        codes.append({
            'code': code,
            'type': args.type,
            'month': args.month,
            'index': i
        })

    # 輸出 CSV
    filename = f"invite_codes_{args.type}_{args.month}.csv"
    with open(filename, 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['code', 'type', 'month', 'index'])
        writer.writeheader()
        writer.writerows(codes)

    print(f"✅ 產生 {len(codes)} 個 {args.type} 邀請碼")
    print(f"📄 儲存至 {filename}")
    print("\n前 5 個預覽：")
    for c in codes[:5]:
        print(f"  {c['code']}")

if __name__ == '__main__':
    main()
```

**使用方式：**
```bash
# 產生 100 個標準邀請碼（2026年4月有效）
python scripts/generate_invite_codes.py --type standard --count 100 --month 202604

# 產生 10 個 KOL 邀請碼
python scripts/generate_invite_codes.py --type kol --count 10 --month 202604

# 指定 secret（正式環境）
python scripts/generate_invite_codes.py --type standard --count 50 --secret YOUR_REAL_SECRET
```

---

## Phase 3：Claw Skill 六大功能

### 目錄結構
```
app/src/main/java/com/snowrealm/yukiboard/claw/
├── ClawClient.kt
├── ClawInput.kt
├── ClawOutput.kt
├── ClawSkill.kt
├── TokenBudgetManager.kt
└── skills/
    ├── AIPredictionSkill.kt
    ├── WriterSkill.kt
    ├── WhisperSkill.kt
    ├── TranslateSkill.kt
    ├── ContextSkill.kt
    └── CupidSkill.kt
```

### ClawInput.kt
```kotlin
data class ClawInput(
    val text: String,
    val context: String = "",
    val appPackage: String = "",
    val language: String = "zh-TW",
    val mode: KeyboardMode = KeyboardMode.NORMAL
)
```

### ClawOutput.kt
```kotlin
enum class OutputType {
    PREDICTION,
    REWRITE,
    TRANSLATE,
    REPLY
}

data class ClawOutput(
    val suggestions: List<String>,
    val type: OutputType,
    val tokensUsed: Int = 0
)
```

### ClawSkill.kt 介面
```kotlin
interface ClawSkill {
    val id: String
    val name: String
    val description: String
    val tokensPerCall: Int
    suspend fun execute(input: ClawInput): ClawOutput
}
```

---

### Skill 1：AIPredictionSkill.kt — AI 補字預測
- 接收目前 buffer 文字
- 回傳 3-5 個最可能的下一個詞/句
- 顯示在候選詞列最前面（帶 ✨ 標記區分）
- 理解台灣繁體中文語境，自然口語風格

### Skill 2：WriterSkill.kt — 雪凜寫手模式
觸發：長按空白鍵 → 選「✍️ 寫手模式」

四個子功能：
- **幫我寫**：輸入主題/關鍵字 → 生成完整段落
- **幫我回覆**：貼入對方說的話 → 生成適合的回覆
- **換個說法**：選取現有文字 → 提供 3 種不同說法
- **讓這句話更有力**：優化文字力道

結果顯示為可左右滑動的卡片，點選直接插入輸入框。
Prompt 檔案：`res/raw/claw_writer_prompt.txt`

### Skill 3：WhisperSkill.kt — 語音輸入升級
- 接 OpenAI Whisper API（`whisper-1`）
- 支援中文/英文/日文自動偵測
- 比 Android 原生更準確
- 錄音時雪凜有傾聽動畫（`yuki_listening.json`）
- 錄音格式：m4a，最長 60 秒

### Skill 4：TranslateSkill.kt — 即時翻譯
觸發方式：
- 選取文字後點翻譯圖示
- 輸入 `/tr` 開頭自動觸發

功能：
- 中→英 / 英→中 / 自動偵測
- 翻譯結果顯示在候選詞列，點選替換
- 保留原文語氣

Prompt 檔案：`res/raw/claw_translate_prompt.txt`

### Skill 5：ContextSkill.kt — App 情境感知
根據 `appPackage` 自動調整所有 AI 回應風格：

| App | 風格 |
|-----|------|
| com.linecorp.* | 輕鬆聊天，口語化 |
| com.google.android.gm | 正式商務 |
| com.instagram.android | 活潑社群 |
| com.spck.editor / com.acode.editor | 技術精準 |
| 遊戲類 App | 遊戲術語，簡短 |

### Skill 6：CupidSkill.kt — 💘 情聖月老模式
觸發：長按空白鍵 → 選「💘 情聖模式」

流程：
1. 選擇身份：👨 男生 / 👩 女生
2. 輸入原始文字
3. AI 改寫成四個版本：
   - 😏 幽默有梗版
   - 🥰 甜蜜溫柔版
   - 🔥 直接大膽版
   - 🌙 詩意浪漫版

特效：雪凜粉紅愛心爆炸動畫（`yuki_cupid.json`）

Prompt 重點：
- 台灣年輕人戀愛語境
- 幽默但不油膩，真誠但不肉麻
- 每個版本明確差異化

Prompt 檔案：
- `res/raw/claw_cupid_male_prompt.txt`
- `res/raw/claw_cupid_female_prompt.txt`

---

### TokenBudgetManager.kt
```kotlin
class TokenBudgetManager(context: Context) {

    fun getDailyLimit(): Int {
        // 邀請碼啟用 → 依邀請碼類型
        // 未啟用但有自訂 Key → 不限制（用戶自己的 Key）
        // 都沒有 → 0（提示輸入邀請碼）
    }

    fun getRemainingTokens(): Int
    fun consumeTokens(count: Int, skillId: String)
    fun resetDailyQuotaIfNeeded()  // 每日 00:00 自動重置
    fun getUsageBySkill(): Map<String, Int>
}
```

---

## Phase 4：Claw UI 元件

### ClawPanel.kt — 浮動 AI 面板
- 從鍵盤上方滑入（0.3 秒動畫）
- 左側雪凜小頭像
- 右側 Skill 結果卡片，支援左右滑動
- 點選卡片直接插入輸入框
- 向下滑或點 X 關閉
- Token 不足時顯示「輸入邀請碼解鎖」提示

### ClawToolbar.kt — Claw 快捷列
位置：候選詞列右側

點擊展開選單（根據 App 情境自動排序）：
- ✍️ 寫手
- 💘 情聖
- 🌐 翻譯
- 🎤 語音
- ✨ 補字（開關）

未啟用邀請碼時：圖示顯示 🔒，點擊跳 InviteCodeActivity。

---

## Prompt 模板檔案

請在 `app/src/main/res/raw/` 建立：

**claw_writer_prompt.txt**
```
你是雪凜，SnowRealm 的 AI 寫作助手。
使用台灣繁體中文，自然口語風格。
當前情境：{context_style}
任務：{task}
原始文字：{input}
請生成 3 個不同版本，每個版本用 --- 分隔。
只輸出版本內容，不要解釋。
```

**claw_translate_prompt.txt**
```
請將以下文字翻譯，保留原本語氣和風格。
目標語言：{target_language}
原文：{input}
只輸出翻譯結果，不要解釋。
```

**claw_cupid_male_prompt.txt**
```
你是月老，幫男生寫出讓女生心動的訊息。
台灣年輕人戀愛語境，幽默不油膩，真誠不肉麻。
原始訊息：{input}
請改寫成以下四個版本，每版用 --- 分隔，前面加版本標題：
1. 😏 幽默有梗版（讓她忍不住笑）
2. 🥰 甜蜜溫柔版（暖心不肉麻）
3. 🔥 直接大膽版（勇敢表達心意）
4. 🌙 詩意浪漫版（有文學感）
```

**claw_cupid_female_prompt.txt**
```
你是月老，幫女生寫出讓男生融化的訊息。
台灣年輕人戀愛語境，撒嬌可愛但不失真誠。
原始訊息：{input}
請改寫成以下四個版本，每版用 --- 分隔，前面加版本標題：
1. 🥺 撒嬌可愛版（讓他想保護你）
2. 💪 獨立自信版（展現魅力）
3. 😄 幽默俏皮版（讓他覺得你有趣）
4. 💝 溫柔體貼版（讓他感受被關心）
```

---

## 安全性要求

- API Key 全部用 `EncryptedSharedPreferences` 儲存
- 邀請碼驗證在本機完成，不打伺服器
- HMAC Secret 從 `BuildConfig` 讀取，不寫死在程式碼
- 所有 Key 不寫入任何 log
- `BuildConfig.PLATFORM_API_KEY` 透過 CI/CD 環境變數注入，不 commit 到 git

---

## 完成後驗證清單

**Phase 1**
- [ ] `gradlew assembleDebug` 無錯誤
- [ ] AISettingsActivity 可正常開啟
- [ ] API Key 輸入後測試連線成功

**Phase 2**
- [ ] InviteCodeActivity 可顯示
- [ ] 正確邀請碼驗證成功，雪凜慶祝動畫出現
- [ ] 錯誤邀請碼顯示失敗提示
- [ ] 啟用後 AISettingsActivity 顯示已啟用狀態
- [ ] `generate_invite_codes.py` 可正常執行

**Phase 3**
- [ ] 六個 Skill 全部可執行
- [ ] 情聖模式生成四個版本
- [ ] Token 用量正確追蹤
- [ ] 用量超限時顯示提示

**Phase 4**
- [ ] ClawPanel 滑入/滑出動畫正常
- [ ] ClawToolbar 在鍵盤上正常顯示
- [ ] 未啟用邀請碼時圖示顯示 🔒

---

## 未來版本規劃（不在本次範圍）

- 邀請碼 → Z幣購買 AI 用量
- SnowRealmCore 統一帳號登入
- 皮膚/貼圖商城（Z幣付費）
- VRM 3D 角色升級
- AR 雪凜（ARCore）
- SnowRealmVR 輸入介面

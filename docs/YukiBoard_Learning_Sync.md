# YukiBoard 詞庫學習 + 雲端同步任務包

---

## 現況分析

### 學習系統（本地）已有的：
- `AdaptiveDictionary`：詞頻學習，打 2 次 CJK / 3 次英文自動收錄
- `PersonalDictionary`：三層（CANDIDATE → LEARNED → PINNED），打 3 次升級
- `UserDictionary`：手動詞庫 + 自動學習 + 注音索引
- `SmartInputManager.onWordCommitted`：統一觸發以上三個

### 學習系統目前的 bug：
1. **CJK 非注音模式**（選了候選字）沒有呼叫 `recordUsage`，只有英文和手寫有
2. **`persist()` 的呼叫時機**：`AdaptiveDictionary.persist()` 在 `onFinishInputView` 才寫磁碟，但 `PersonalDictionary` 每次 `save()` 都直接寫，不一致

### 雲端同步（CloudSyncManager）已有的：
- 架構完整：上傳/下載 settings, dictionary, themes, learning_data
- 7 種學習資料都有序列化/反序列化
- 綁定 Insight Engine 帳號（SnowRealmAuthClient）
- Supabase 作為後端，需要設定 `YUKIBOARD_SUPABASE_URL` + `YUKIBOARD_SUPABASE_ANON_KEY`

### 雲端同步目前缺的：
1. **沒有自動同步觸發**：只有手動呼叫 `uploadAll()`，沒有登入後自動同步、定期同步
2. **沒有衝突解決**：下載時直接覆蓋本地，沒有 merge 邏輯
3. **設定頁沒有同步開關**：用戶看不到也控制不了

---

## Bug 修復：CJK 學習觸發缺失

### `input/router/InputRouter.kt` — `onTextCommitted()`

找到這段：
```kotlin
if (hasCjk || hasKana || hasHangul) {
    for (ch in trimmed) {
        if (ch.isWhitespace()) continue
        val charStr = ch.toString()
        smartInput.onWordCommitted(charStr, currentAppPackage)
        lastCommittedWord = charStr
    }
    if (trimmed.length > 1) {
        smartInput.onWordCommitted(trimmed, currentAppPackage)
    }
    // 自動學詞：中文選字後記錄 word + bopomofo 到 UserDictionary
    if (hasCjk && activeEngine is ChewingInputEngine) {
        val bopo = (activeEngine as ChewingInputEngine).lastComposingBopomofo
        if (bopo.isNotEmpty() && trimmed.length >= 2) {
            userDict.addManual(trimmed, bopo)
        }
        userDict.learn(trimmed)
    }
```

**問題：非注音 CJK（如日文、韓文、自動模式選字）沒有呼叫 `userDict.learn()`**

**修法：把 `userDict.learn()` 移到 CJK 的通用路徑：**

```kotlin
if (hasCjk || hasKana || hasHangul) {
    for (ch in trimmed) {
        if (ch.isWhitespace()) continue
        val charStr = ch.toString()
        smartInput.onWordCommitted(charStr, currentAppPackage)
        lastCommittedWord = charStr
    }
    if (trimmed.length > 1) {
        smartInput.onWordCommitted(trimmed, currentAppPackage)
    }
    // ← 所有 CJK/日文/韓文都學習，不只注音
    userDict.learn(trimmed)

    // 注音專用：記錄注音索引
    if (hasCjk && activeEngine is ChewingInputEngine) {
        val bopo = (activeEngine as ChewingInputEngine).lastComposingBopomofo
        if (bopo.isNotEmpty() && trimmed.length >= 2) {
            userDict.addManual(trimmed, bopo)
        }
    }
```

---

## 雲端同步功能完整實作

### 1. Supabase 資料表設計

需要在 Supabase 建立以下資料表（SQL）：

```sql
-- 用戶設定
create table user_settings (
  user_id text primary key,
  settings jsonb,
  updated_at timestamp default now()
);

-- 手動詞庫
create table user_dictionary (
  id bigint generated always as identity primary key,
  user_id text not null,
  word text not null,
  bopomofo text,
  category text default '一般',
  updated_at timestamp default now(),
  unique(user_id, word)
);

-- 主題設定
create table user_themes (
  user_id text primary key,
  theme_data jsonb,
  updated_at timestamp default now()
);

-- 學習資料
create table user_learning_data (
  user_id text primary key,
  adaptive_dict jsonb,
  personal_dict jsonb,
  phrase_predict jsonb,
  sentence_predict jsonb,
  style_profile jsonb,
  typing_habits jsonb,
  correction_learn jsonb,
  updated_at timestamp default now()
);
```

RLS 設定（每個表都要加）：
```sql
alter table user_settings enable row level security;
create policy "users can only access own data" on user_settings
  using (user_id = current_setting('request.jwt.claims', true)::json->>'sub');
-- 其他表同樣設定
```

---

### 2. `data/CloudSyncManager.kt` — 新增自動同步觸發

**新增同步策略：**

```kotlin
// 新增 companion object 常數
private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L  // 24小時同步一次
private const val SYNC_ON_COMMIT_THRESHOLD = 50  // 每提交 50 次觸發一次同步

// 新增計數器
private var commitsSinceLastSync = 0

/** 每次提交詞後呼叫（由 IMEService 的 onFinishInputView 呼叫） */
suspend fun onSessionEnd() {
    if (!isSyncEnabled) return
    if (auth.currentUserId == null) return

    commitsSinceLastSync++
    val now = System.currentTimeMillis()
    val timeSinceSync = now - lastSyncTime

    // 超過 24 小時 或 累積 50 次提交 → 上傳學習資料
    if (timeSinceSync > SYNC_INTERVAL_MS || commitsSinceLastSync >= SYNC_ON_COMMIT_THRESHOLD) {
        commitsSinceLastSync = 0
        uploadLearningData(auth.currentUserId!!, auth.getToken())
        uploadDictionary(auth.currentUserId!!, auth.getToken())
        metaPrefs.edit().putLong(PREF_LAST_SYNC, now).apply()
    }
}

/** 登入後立即同步（下載雲端資料到本地） */
suspend fun syncOnLogin(): SyncResult {
    val userId = auth.currentUserId ?: return SyncResult.NotLoggedIn
    val token = auth.getToken()
    return try {
        downloadAll()
    } catch (e: Exception) {
        SyncResult.Error("登入同步失敗: ${e.message}")
    }
}
```

---

### 3. `service/YukiBoardIMEService.kt` — 觸發同步

**`onFinishInputView()` 裡呼叫同步：**

```kotlin
override fun onFinishInputView(finishingInput: Boolean) {
    super.onFinishInputView(finishingInput)
    // ... 現有邏輯 ...

    // 學習資料持久化
    inputRouter.smartInput.adaptiveDictionary.persist()

    // 雲端同步（非阻塞，背景執行）
    serviceScope.launch {
        try {
            com.snowrealm.yukiboard.data.CloudSyncManager
                .getInstance(applicationContext)
                .onSessionEnd()
        } catch (_: Exception) {}
    }
}
```

**登入成功後觸發同步：**

在設定頁或登入流程完成後，呼叫：
```kotlin
serviceScope.launch {
    CloudSyncManager.getInstance(applicationContext).syncOnLogin()
}
```

---

### 4. 合併策略（避免雲端覆蓋本地較新的資料）

**`CloudSyncManager.importAdaptiveDict()` — 改為 merge 而不是覆蓋：**

```kotlin
private fun importAdaptiveDict(obj: JsonObject) {
    val sp = context.getSharedPreferences("yukiboard_adaptive_dict", Context.MODE_PRIVATE)
    val editor = sp.edit()
    val keys = mutableListOf<String>()

    for ((word, value) in obj) {
        val remoteRaw = value.jsonPrimitive.contentOrNull ?: continue
        val localRaw = sp.getString("ad_$word", null)

        if (localRaw == null) {
            // 本地沒有 → 直接用雲端
            editor.putString("ad_$word", remoteRaw)
            keys.add(word)
        } else {
            // 兩邊都有 → 取頻率較高的
            val localFreq = localRaw.split("|").getOrNull(0)?.toIntOrNull() ?: 0
            val remoteFreq = remoteRaw.split("|").getOrNull(0)?.toIntOrNull() ?: 0
            if (remoteFreq > localFreq) {
                editor.putString("ad_$word", remoteRaw)
            }
            keys.add(word)
        }
    }

    val existing = sp.getString("_ad_index", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val merged = (existing + keys).distinct()
    editor.putString("_ad_index", merged.joinToString(","))
    editor.apply()
}
```

`importPersonalDict()` 同樣改為 merge：取 tier 較高的或 frequency 較高的。

---

### 5. 隱私保護

**什麼會上傳：**
- 打字習慣統計（詞頻、使用時間）
- 手動新增詞庫
- 輸入法設定、主題

**什麼不會上傳：**
- 輸入的完整文字內容
- 輸入框的上下文
- AI 對話記錄

**加密：**
在上傳前對學習資料做基本混淆（不是強加密，但避免純文字存儲）：

```kotlin
// 可選：上傳前 Base64 encode learning data
private fun obfuscate(data: String): String =
    android.util.Base64.encodeToString(data.toByteArray(), android.util.Base64.NO_WRAP)

private fun deobfuscate(data: String): String =
    String(android.util.Base64.decode(data, android.util.Base64.NO_WRAP))
```

**隱私告知文字（在設定頁開啟同步時顯示）：**
> 開啟後，您的個人詞庫和打字習慣統計（不含輸入內容）將安全地儲存至 YukiBoard 雲端，方便換機後繼續使用。您可以隨時在設定中關閉此功能。

---

### 6. `ui/settings/SettingsActivity.kt` — 新增同步設定區

在帳號設定卡片裡加：

```kotlin
// 雲端同步開關
card.addView(switchRow(
    tr("雲端同步"),
    tr("詞庫與學習資料跟著帳號走，換機也能用"),
    cloudSync.isSyncEnabled
) { enabled ->
    if (enabled && !auth.isLoggedIn) {
        // 未登入 → 提示先登入
        Toast.makeText(this, "請先登入 Insight 帳號", Toast.LENGTH_SHORT).show()
        return@switchRow
    }
    cloudSync.setSyncEnabled(enabled)
    if (enabled) {
        activityScope.launch {
            val result = cloudSync.syncOnLogin()
            withContext(Dispatchers.Main) {
                when (result) {
                    is SyncResult.Success -> Toast.makeText(this@SettingsActivity, "同步完成", Toast.LENGTH_SHORT).show()
                    is SyncResult.NotLoggedIn -> Toast.makeText(this@SettingsActivity, "請先登入", Toast.LENGTH_SHORT).show()
                    is SyncResult.Error -> Toast.makeText(this@SettingsActivity, "同步失敗：${result.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
})

// 手動同步按鈕
if (cloudSync.isSyncEnabled && auth.isLoggedIn) {
    card.addView(styledButton("立即同步", 0xFF2A3A5C.toInt(), 0xFFE8F4FF.toInt()) {
        activityScope.launch {
            val result = cloudSync.uploadAll()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity,
                    if (result is SyncResult.Success) "上傳完成" else "上傳失敗",
                    Toast.LENGTH_SHORT).show()
            }
        }
    })

    // 上次同步時間
    val lastSync = cloudSync.lastSyncTime
    if (lastSync > 0) {
        val df = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
        card.addView(TextView(this).apply {
            text = "上次同步：${df.format(java.util.Date(lastSync))}"
            textSize = 11f
            setTextColor(0xFF6688AA.toInt())
            setPadding(dp(12), dp(4), 0, dp(8))
        })
    }
}
```

---

## 執行順序

```
Step 1（Bug 修復，馬上可做）：
  InputRouter.onTextCommitted：CJK 學習路徑修復

Step 2（需要 Supabase 設定）：
  - 在 Supabase 建立 4 個資料表
  - local.properties 加 YUKIBOARD_SUPABASE_URL + YUKIBOARD_SUPABASE_ANON_KEY

Step 3（程式碼）：
  - CloudSyncManager 加 onSessionEnd() + syncOnLogin() + merge 邏輯
  - YukiBoardIMEService.onFinishInputView 加同步觸發
  - SettingsActivity 加同步開關 UI

Step 4（測試）：
  - 打字 → 關閉鍵盤 → 確認學習資料已上傳
  - 登入新手機 → 開啟同步 → 確認詞庫下載
```

---

## 驗證

```
學習修復：
□ 打注音選字 2 次 → 詞庫記錄
□ 打英文 3 次 → 詞庫記錄
□ 手寫辨識選字 → 詞庫記錄
□ 日文/韓文選字 → 詞庫記錄

雲端同步：
□ 登入 Insight → 自動下載雲端詞庫
□ 打字一段時間 → 背景自動上傳
□ 手動點「立即同步」→ 立即上傳
□ 換手機登入 → 詞庫完整恢復
□ 兩台手機都有資料 → merge 後兩邊資料都保留

隱私：
□ Supabase 資料表的 RLS 只允許本人存取
□ 上傳資料不包含輸入框原始文字
```

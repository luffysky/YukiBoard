請升級 YukiBoard 的 AI 整合，依序執行以下四個改善：

═══════════════════════════════════════
改善 1：AI 補字直接融入候選字列
═══════════════════════════════════════

目標：用戶打字時，候選字列末尾自動出現 AI 預測的詞，
跟一般候選字混在一起，有視覺區分但不突兀。

修改 YukiBoardIMEService.kt：
1. 加入 debounce 機制：用戶停頓 600ms 才觸發 AI
```kotlin
   private var aiPredictJob: Job? = null
   
   private fun scheduleAIPrediction(text: String) {
       if (!prefs.aiPrediction) return
       if (!clawClient!!.isAvailable()) return
       aiPredictJob?.cancel()
       aiPredictJob = serviceScope.launch {
           delay(600)  // 停頓 600ms 才觸發
           val result = clawClient!!.executeSkill(
               "ai_prediction",
               ClawInput(text = text, context = getTextBeforeCursor(50))
           )
           // 把 AI 預測加到候選字列末尾
           val current = inputRouter.candidates.toMutableList()
           result.suggestions.forEach { s ->
               if (s !in current) current.add(s)
           }
           keyboardView.updateCandidates(current)
           keyboardView.markAICandidates(result.suggestions)  // 標記哪些是 AI 的
       }
   }
```

2. 在 `onKeyPress` 處理完後呼叫：
```kotlin
   scheduleAIPrediction(inputRouter.composingText.ifBlank { getTextBeforeCursor(30) })
```

3. 在 `onFinishInputView` 裡 cancel：
```kotlin
   aiPredictJob?.cancel()
```

修改 KeyboardView.kt：
加入 `markAICandidates(aiSuggestions: List<String>)` 方法，
把 AI 候選字的文字顏色改為淡紫色（#B39DDB），
加一個小 ✨ 前綴或底線區分。

═══════════════════════════════════════
改善 2：修復 ClawClient provider 動態更新
═══════════════════════════════════════

問題：skills 是 lazy 建立，provider 被固定，切換 Provider 後 Skill 不更新。

修改 ClawClient.kt：
把 skills Map 改為每次執行時動態注入 provider：
```kotlin
// 移除 lazy skills Map
// 改為 Skill 工廠方法
private fun getSkill(skillId: String): ClawSkill? {
    val provider = providerManager.getCurrentProvider()  // 每次動態取
    return when (skillId) {
        "ai_prediction" -> AIPredictionSkill(provider)
        "writer"        -> WriterSkill(appContext, provider)
        "translate"     -> TranslateSkill(appContext, provider)
        "cupid"         -> CupidSkill(appContext, provider)
        "context"       -> ContextSkill()
        "whisper"       -> WhisperSkill()
        "grammar_check" -> GrammarCheckSkill(appContext, provider)
        "tone_adjust"   -> ToneAdjustSkill(provider)
        "summarize"     -> SummarizeSkill(provider)
        "meme_text"     -> MemeTextSkill(appContext, provider)
        "emoji_translate" -> EmojiTranslateSkill(provider)
        "quick_reply"   -> QuickReplySkill(provider)
        "code_complete" -> CodeCompleteSkill(provider)
        "regex_gen"     -> RegexGenSkill(provider)
        "commit_message"-> CommitMessageSkill(provider)
        "yuki_chat"     -> YukiChatSkill(appContext, provider)
        "mood_diary"    -> MoodDiarySkill(provider)
        "weather"       -> WeatherSkill(appContext, provider)
        else            -> null
    }
}

suspend fun executeSkill(skillId: String, input: ClawInput): ClawOutput {
    val skill = getSkill(skillId) ?: throw IllegalArgumentException("Unknown skill: $skillId")
    // ... 其餘邏輯不變
}
```

═══════════════════════════════════════
改善 3：App 感知 Prompt 自動調整
═══════════════════════════════════════

修改 ClawInput.kt，加入 app 感知：
```kotlin
data class ClawInput(
    val text: String,
    val context: String = "",
    val appPackage: String = "",
    val appStyle: AppStyle = AppStyle.CHAT
)

enum class AppStyle {
    CHAT,       // LINE, IG, Threads → 短句、口語、emoji OK
    EMAIL,      // Gmail, Outlook → 正式、完整句子
    CODE,       // GitHub, VS Code → 技術用語、英文 OK
    SOCIAL,     // Twitter/X, Facebook → 精煉、有趣
    GENERAL     // 其他
}
```

在 `ContextSkill.getContextStyle()` 裡補完 App 對應：
```kotlin
fun getAppStyle(packageName: String): AppStyle = when {
    packageName.contains("line") ||
    packageName.contains("instagram") ||
    packageName.contains("threads") ||
    packageName.contains("whatsapp") -> AppStyle.CHAT
    
    packageName.contains("gmail") ||
    packageName.contains("outlook") ||
    packageName.contains("yahoo") -> AppStyle.EMAIL
    
    packageName.contains("github") ||
    packageName.contains("vscode") ||
    packageName.contains("termux") -> AppStyle.CODE
    
    packageName.contains("twitter") ||
    packageName.contains("facebook") -> AppStyle.SOCIAL
    
    else -> AppStyle.GENERAL
}
```

在 YukiBoardIMEService 傳遞 appPackage：
```kotlin
ClawInput(
    text = selectedText,
    context = getTextBeforeCursor(50),
    appPackage = currentAppPackage ?: "",
    appStyle = ContextSkill.getAppStyle(currentAppPackage ?: "")
)
```

各 Skill 的 prompt 根據 AppStyle 微調語氣：
- CHAT → 短句、口語、emoji OK
- EMAIL → 正式、完整句子、不用 emoji
- CODE → 技術用語、可以英文

═══════════════════════════════════════
改善 4：Streaming 結果即時顯示在候選字列
═══════════════════════════════════════

目標：Skill 執行時，結果一邊生成一邊更新候選字列，
不要等全部生成完再顯示。

修改 ClawPanelView.kt：
現在結果是等全部收完再顯示，改為 Streaming 邊收邊更新：
```kotlin
// 在 executeSkill 改用 Flow 直接更新
serviceScope.launch {
    val partial = StringBuilder()
    clawClient.streamSkill(skillId, input).collect { chunk ->
        partial.append(chunk)
        // 每收到一段就更新候選字顯示
        val partialSuggestions = partial.toString()
            .split("---")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        withContext(Dispatchers.Main) {
            keyboardView.updateCandidates(partialSuggestions)
        }
    }
}
```

在 ClawClient 加入 `streamSkill()` 方法，直接回傳 Flow<String>：
```kotlin
fun streamSkill(skillId: String, input: ClawInput): Flow<String> = flow {
    val skill = getSkill(skillId) ?: return@flow
    // 直接把 provider.complete() 的 Flow 往外傳
    val provider = providerManager.getCurrentProvider()
    provider.complete(buildPrompt(skill, input), buildSystemPrompt(skill, input))
        .collect { emit(it) }
}
```
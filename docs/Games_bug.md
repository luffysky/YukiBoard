🔴 嚴重 — 1 個
記憶翻牌 crash（已知）
MemoryFlipGame.kt 第 75-76 行 firstFlip!! / secondFlip!!，快速連點時 null crash。之前已列在 BUG-8，提醒一下這個要優先修。

🟡 中等 — 3 個
GameFloatingPanel 捲動沒有下限
scrollY = (scrollY + dy).coerceAtMost(0f) — 只限制不能超過 0（往上），但沒有計算最大下捲距離，遊戲卡片少的時候可以一直往下捲到空白。
修復：
kotlin// 計算內容總高度
val totalContentHeight = (games.size + cardsPerRow - 1) / cardsPerRow * (cardSize + cardPadding)
val visibleHeight = height - startY
val maxScrollUp = -(totalContentHeight - visibleHeight).coerceAtLeast(0f)
scrollY = (scrollY + dy).coerceIn(maxScrollUp, 0f)
VoiceGame 是空實作但顯示在遊戲清單
VoiceGame.kt 整個是 stub，點進去只顯示「即將推出」，但已列在 GameMode 可被選到。應該在 GameFloatingPanel 裡過濾掉，或在卡片上顯示「🔒 即將推出」並禁止點擊。
GameLoopRunner 沒有確認綁定生命週期
GameLoopRunner 用 Handler 跑迴圈，但沒看到在 onDetachedFromWindow 或 IME onFinishInputView 裡確實呼叫 stop()。如果 View 被銷毀但 stop() 沒被呼叫，Handler 會繼續跑，有記憶體洩漏風險。需確認 GameKeyboardView.onDetachedFromWindow() 裡有 gameLoopRunner.stop()。

🟢 輕微 — 2 個
SpellingDuelGame 單字詞庫太小
詞庫只有內建幾個英文單字，很快就重複，體驗差。建議把詞庫放到 res/raw/spelling_words.txt，從檔案載入。
ScoreManager combo 顏色邏輯
getComboColor() 的「彩虹」是橘色，名不符實，可以之後換成真正的漸層邏輯，輕微問題。

總結：把這段貼給 Claude Code：
請修復遊戲區塊的以下 Bug：

1. MemoryFlipGame.kt 第 75-76 行 firstFlip!! / secondFlip!!
   → val flip1 = firstFlip ?: return
   → val flip2 = secondFlip ?: return

2. GameFloatingPanel.kt scrollY 沒有下限
   → 計算 totalContentHeight，用 coerceIn(maxScrollUp, 0f)

3. VoiceGame 是空實作但可以被點選
   → 卡片顯示「即將推出」文字並設 isEnabled = false，或從清單過濾

4. GameLoopRunner 生命週期
   → 確認 GameKeyboardView.onDetachedFromWindow() 裡有 gameLoopRunner.stop()

5. SpellingDuelGame 詞庫
   → 移到 res/raw/spelling_words.txt 從檔案載入
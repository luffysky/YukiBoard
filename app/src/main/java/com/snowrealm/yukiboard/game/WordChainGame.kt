package com.snowrealm.yukiboard.game

import android.content.Context

/**
 * 接龍遊戲 — 中文成語/詞語接龍。
 *
 * 規則：
 * - 雪凜先出一個四字詞，玩家接一個開頭字相同的詞
 * - 接不上或 15 秒超時 → 遊戲結束
 * - 每成功接一個 +50 分，連續接龍加分
 */
class WordChainGame(context: Context) : GameLogic {

    override val gameMode = GameMode.WORD_CHAIN
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var chain = mutableListOf<String>()
    private var currentInput = ""
    private var timeLeftMs = 15000L
    private var isPlayerTurn = false
    private var combo = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    // 常用詞庫（確保可以接龍）
    private val wordPool = mapOf(
        '天' to listOf("天長地久", "天涯海角", "天氣晴朗", "天花亂墜", "天衣無縫"),
        '地' to listOf("地久天長", "地大物博", "地動山搖"),
        '長' to listOf("長驅直入", "長話短說", "長年累月"),
        '久' to listOf("久而久之", "久負盛名", "久別重逢"),
        '人' to listOf("人山人海", "人才濟濟", "人生如夢"),
        '海' to listOf("海闊天空", "海底撈月", "海誓山盟"),
        '山' to listOf("山明水秀", "山窮水盡", "山高水長"),
        '水' to listOf("水落石出", "水深火熱", "水到渠成"),
        '心' to listOf("心想事成", "心花怒放", "心有靈犀"),
        '花' to listOf("花好月圓", "花團錦簇", "花言巧語"),
        '月' to listOf("月明星稀", "月下老人"),
        '風' to listOf("風和日麗", "風花雪月", "風調雨順"),
        '日' to listOf("日新月異", "日理萬機", "日積月累"),
        '春' to listOf("春暖花開", "春風得意", "春光明媚"),
        '大' to listOf("大吉大利", "大開眼界", "大功告成"),
        '一' to listOf("一心一意", "一見鍾情", "一帆風順", "一鳴驚人"),
        '金' to listOf("金玉滿堂", "金碧輝煌"),
        '百' to listOf("百花齊放", "百年好合", "百發百中"),
        '龍' to listOf("龍飛鳳舞", "龍馬精神"),
        '馬' to listOf("馬到成功", "馬不停蹄"),
        '成' to listOf("成竹在胸", "成群結隊"),
        '空' to listOf("空前絕後", "空穴來風"),
        '明' to listOf("明月當空", "明察秋毫", "明知故犯"),
        '秀' to listOf("秀色可餐", "秀外慧中"),
        '開' to listOf("開天闢地", "開門見山"),
        '意' to listOf("意氣風發", "意想不到"),
        '夢' to listOf("夢寐以求"),
        '高' to listOf("高山流水", "高瞻遠矚"),
        '光' to listOf("光明正大", "光陰似箭"),
        '盛' to listOf("盛氣凌人"),
        '萬' to listOf("萬事如意", "萬象更新"),
        '如' to listOf("如魚得水", "如日中天"),
        '前' to listOf("前程似錦", "前所未有"),
        '步' to listOf("步步高升"),
        '升' to listOf("升官發財"),
        '氣' to listOf("氣象萬千"),
    )

    private val starterWords = listOf(
        "天長地久", "春暖花開", "一心一意", "風和日麗", "人山人海",
        "山明水秀", "心想事成", "花好月圓", "大吉大利", "百花齊放"
    )

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        chain.clear()
        val starter = starterWords.random()
        chain.add(starter)
        currentInput = ""
        scoreManager.reset()
        combo = 0
        timeLeftMs = 15000L
        isPlayerTurn = true
        status = GameStatus.PLAYING
        renderGrid()
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return
        if (isPlayerTurn) {
            timeLeftMs -= deltaMs
            if (timeLeftMs <= 0) {
                status = GameStatus.GAME_OVER
            }
        }
        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING || !isPlayerTurn) return

        when {
            label == "⌫" || label == "DEL" -> {
                if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
            }
            label == "↵" || label == "ENTER" || label == "確認" -> {
                if (currentInput.length >= 2) submitWord()
            }
            label.length == 1 && label[0].code >= 0x4E00 -> {
                if (currentInput.length < 8) currentInput += label
            }
        }
        renderGrid()
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun submitWord() {
        val lastWord = chain.last()
        val lastChar = lastWord.last()
        if (currentInput.first() != lastChar) return // 開頭字不對

        // 接龍成功
        chain.add(currentInput)
        combo++
        val bonus = 50 + combo * 10
        scoreManager.addScore(bonus)
        onGameEvent?.invoke(GameEvent.PERFECT)

        // 雪凜回應
        isPlayerTurn = false
        val yukiResponse = findYukiResponse(currentInput.last())
        currentInput = ""
        timeLeftMs = 15000L

        if (yukiResponse != null) {
            chain.add(yukiResponse)
            isPlayerTurn = true
        } else {
            // 雪凜接不上 → 玩家贏
            scoreManager.addScore(200)
            status = GameStatus.GAME_OVER
        }
    }

    private fun findYukiResponse(startChar: Char): String? {
        val candidates = wordPool[startChar] ?: return null
        val unused = candidates.filter { it !in chain }
        return unused.randomOrNull()
    }

    private fun renderGrid() {
        if (!::grid.isInitialized) return
        clearGrid()

        // 標題列
        setRowText(0, "🎭 接龍 連擊$combo  ⏱${timeLeftMs / 1000}s")

        // 最近幾個詞
        val recent = chain.takeLast((rows - 3).coerceAtLeast(3))
        recent.forEachIndexed { i, word ->
            if (i + 1 < rows) {
                val idx = chain.size - recent.size + i
                val prefix = if (idx % 2 == 0) "🌸" else "👤"
                setRowText(i + 1, "$prefix $word")
            }
        }

        // 輸入列
        val inputRow = recent.size + 1
        if (inputRow < rows && status == GameStatus.PLAYING && isPlayerTurn) {
            val lastChar = chain.last().last()
            val display = if (currentInput.isEmpty()) "（${lastChar}___）" else currentInput
            setRowText(inputRow, "▶ $display")
        }

        // 結束訊息
        if (status == GameStatus.GAME_OVER) {
            val msgRow = (recent.size + 2).coerceAtMost(rows - 1)
            if (timeLeftMs <= 0) {
                setRowText(msgRow, "⏰ 時間到！+${scoreManager.currentScore}")
            } else {
                setRowText(msgRow, "🎉 雪凜接不上！+${scoreManager.currentScore}")
            }
        }
    }

    private fun setRowText(row: Int, text: String) {
        if (row >= rows) return
        for (c in 0 until cols.coerceAtMost(text.length)) {
            grid[row][c] = grid[row][c].copy(gameLabel = text[c].toString())
        }
    }

    private fun clearGrid() {
        for (r in 0 until rows) for (c in 0 until cols) {
            grid[r][c] = KeyCell(r, c)
        }
    }
}

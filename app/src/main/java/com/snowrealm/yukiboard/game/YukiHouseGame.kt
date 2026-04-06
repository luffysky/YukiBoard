package com.snowrealm.yukiboard.game

import android.content.Context

/**
 * 雪凜的家 — 打字累積建材，建造房屋。
 *
 * 規則：
 * - 鍵盤上隨機出現建材圖示（🪵🧱🪨🔩）
 * - 按下對應按鍵收集建材
 * - 每收集一定數量，房屋升級一階段
 * - 60 秒內盡量升級
 */
class YukiHouseGame(context: Context) : GameLogic {

    override val gameMode = GameMode.YUKI_HOUSE
    override var status = GameStatus.IDLE; private set
    override lateinit var grid: Array<Array<KeyCell>>
    override val scoreManager = ScoreManager(context)

    private var rows = 0; private var cols = 0
    private var timeLeftMs = 60_000L
    private var spawnAccum = 0L
    private var houseLevel = 0
    private var materials = 0

    var onGameEvent: ((GameEvent) -> Unit)? = null

    private data class Material(val row: Int, val col: Int, val icon: String, var ttlMs: Long = 3000L)

    private val materialIcons = listOf("🪵", "🧱", "🔩", "🪨")
    private val activeMaterials = mutableListOf<Material>()

    private val houseLevels = listOf(
        "🏕️ 帳篷",     // 0
        "🛖 小屋",      // 10
        "🏠 房屋",      // 25
        "🏡 花園洋房",   // 45
        "🏰 城堡",      // 70
        "🌟 夢幻宮殿",  // 100
    )
    private val levelThresholds = listOf(0, 10, 25, 45, 70, 100)

    override fun init(rows: Int, cols: Int) {
        this.rows = rows; this.cols = cols
        grid = Array(rows) { r -> Array(cols) { c -> KeyCell(r, c) } }
    }

    override fun start() {
        scoreManager.reset()
        timeLeftMs = 60_000L
        spawnAccum = 0
        houseLevel = 0
        materials = 0
        activeMaterials.clear()
        status = GameStatus.PLAYING
        renderGrid()
    }

    override fun pause() { if (status == GameStatus.PLAYING) status = GameStatus.PAUSED }
    override fun resume() { if (status == GameStatus.PAUSED) status = GameStatus.PLAYING }

    override fun update(deltaMs: Long) {
        if (status != GameStatus.PLAYING) return
        timeLeftMs -= deltaMs
        if (timeLeftMs <= 0) { status = GameStatus.GAME_OVER; renderGrid(); return }

        // 生成建材
        spawnAccum += deltaMs
        if (spawnAccum >= 1200L) {
            spawnAccum = 0
            if (activeMaterials.size < 5) {
                val r = (1 until rows).random()
                val c = (0 until cols).random()
                if (activeMaterials.none { it.row == r && it.col == c }) {
                    activeMaterials.add(Material(r, c, materialIcons.random()))
                }
            }
        }

        // 建材過期
        activeMaterials.forEach { it.ttlMs -= deltaMs }
        activeMaterials.removeAll { it.ttlMs <= 0 }

        // 升級檢查
        val newLevel = levelThresholds.indexOfLast { materials >= it }.coerceAtLeast(0)
        if (newLevel > houseLevel) {
            houseLevel = newLevel
            onGameEvent?.invoke(GameEvent.PERFECT)
        }

        renderGrid()
    }

    override fun onKeyPress(row: Int, col: Int, label: String) {
        if (status != GameStatus.PLAYING) return

        val hit = activeMaterials.find { it.row == row && it.col == col }
        if (hit != null) {
            activeMaterials.remove(hit)
            materials++
            scoreManager.addScore(15)
            onGameEvent?.invoke(GameEvent.EAT)
        }
        renderGrid()
    }

    override fun onSwipe(direction: SwipeDirection) {}
    override fun destroy() { status = GameStatus.IDLE }

    private fun renderGrid() {
        if (!::grid.isInitialized) return
        clearGrid()

        val levelName = houseLevels.getOrElse(houseLevel) { houseLevels.last() }
        setRowText(0, "🏠 $levelName  建材$materials  ⏱${timeLeftMs / 1000}s")

        // 顯示建材
        activeMaterials.forEach { mat ->
            if (mat.row in 0 until rows && mat.col in 0 until cols) {
                grid[mat.row][mat.col] = grid[mat.row][mat.col].copy(
                    gameLabel = mat.icon,
                    glowColor = 0xFFFFAB40.toInt(),
                    isActive = true
                )
            }
        }

        // 結束
        if (status == GameStatus.GAME_OVER) {
            val msgRow = 2.coerceAtMost(rows - 1)
            setRowText(msgRow, "🎉 $levelName +${scoreManager.currentScore}")
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

# YukiBoard 滑動輸入 + 詞庫擴充 任務包

> 給 Claude Code 執行，請依照批次順序完成，每批次完成後告訴我。
> 最後更新：2026-04-04

---

## 背景說明

iOS 18 剛在 2024 年 9 月加入注音滑動輸入，Android 目前沒有任何輸入法有注音滑動。
YukiBoard 是第一個機會。

技術方向：
- 滑動路徑 → 對應注音按鍵序列 → 查詢現有 ChewingInputEngine
- 不需要訓練資料，直接用現有詞庫
- RIME 詞庫只取 .dict.yaml 純文字，不接 librime 引擎（太重）

---

## 第一批 — RIME 詞庫轉換腳本（build time）

### 1-A 下載詞庫檔案

手動下載以下兩個檔案，放到 `scripts/dicts/`：

```
https://raw.githubusercontent.com/rime/rime-bopomofo/master/bopomofo_tw.dict.yaml
https://raw.githubusercontent.com/rime/terra-pinyin/master/terra_pinyin.dict.yaml
```

如果下載失敗，用以下備用腳本自動抓取：

```bash
mkdir -p scripts/dicts
curl -o scripts/dicts/bopomofo_tw.dict.yaml \
  https://raw.githubusercontent.com/rime/rime-bopomofo/master/bopomofo_tw.dict.yaml
curl -o scripts/dicts/terra_pinyin.dict.yaml \
  https://raw.githubusercontent.com/rime/terra-pinyin/master/terra_pinyin.dict.yaml
```

### 1-B 新增轉換腳本

新增 `scripts/convert_rime_dict.py`：

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 RIME .dict.yaml 詞庫轉成 SQLite DB，供 YukiBoard 查詢。
用法：python3 convert_rime_dict.py
輸出：app/src/main/assets/rime_bopomofo.db
"""

import sqlite3
import os
import sys

DICT_FILES = [
    "scripts/dicts/bopomofo_tw.dict.yaml",
    "scripts/dicts/terra_pinyin.dict.yaml",
]
OUTPUT_DB = "app/src/main/assets/rime_bopomofo.db"

# 拼音 → 注音對照表（terra_pinyin 用，把拼音轉成注音符號）
PINYIN_TO_BOPO = {
    "b":"ㄅ","p":"ㄆ","m":"ㄇ","f":"ㄈ",
    "d":"ㄉ","t":"ㄊ","n":"ㄋ","l":"ㄌ",
    "g":"ㄍ","k":"ㄎ","h":"ㄏ",
    "j":"ㄐ","q":"ㄑ","x":"ㄒ",
    "zh":"ㄓ","ch":"ㄔ","sh":"ㄕ","r":"ㄖ",
    "z":"ㄗ","c":"ㄘ","s":"ㄙ",
    "a":"ㄚ","o":"ㄛ","e":"ㄜ","ai":"ㄞ","ei":"ㄟ",
    "ao":"ㄠ","ou":"ㄡ","an":"ㄢ","en":"ㄣ","ang":"ㄤ","eng":"ㄥ","er":"ㄦ",
    "i":"ㄧ","u":"ㄨ","v":"ㄩ","yu":"ㄩ",
    "ia":"ㄧㄚ","ie":"ㄧㄝ","iao":"ㄧㄠ","iu":"ㄧㄡ",
    "ian":"ㄧㄢ","in":"ㄧㄣ","iang":"ㄧㄤ","ing":"ㄧㄥ","iong":"ㄩㄥ",
    "ua":"ㄨㄚ","uo":"ㄨㄛ","uai":"ㄨㄞ","ui":"ㄨㄟ",
    "uan":"ㄨㄢ","un":"ㄨㄣ","uang":"ㄨㄤ","ong":"ㄨㄥ",
    "ve":"ㄩㄝ","van":"ㄩㄢ","vn":"ㄩㄣ",
}

TONE_MAP = {"1":"ˉ","2":"ˊ","3":"ˇ","4":"ˋ","5":"˙"}


def pinyin_to_bopomofo(pinyin_str):
    """把拼音音節轉成注音，例如 'ke3 yi3' → 'ㄎㄜˇ ㄧˇ'"""
    result = []
    for syllable in pinyin_str.split():
        tone = ""
        if syllable and syllable[-1].isdigit():
            tone = TONE_MAP.get(syllable[-1], "")
            syllable = syllable[:-1]
        bopo = ""
        # 嘗試最長匹配
        i = 0
        while i < len(syllable):
            matched = False
            for length in (2, 1):
                chunk = syllable[i:i+length]
                if chunk in PINYIN_TO_BOPO:
                    bopo += PINYIN_TO_BOPO[chunk]
                    i += length
                    matched = True
                    break
            if not matched:
                bopo += syllable[i]
                i += 1
        result.append(bopo + tone)
    return " ".join(result)


def parse_yaml_dict(filepath):
    """解析 RIME dict.yaml，回傳 [(word, bopomofo, frequency)]"""
    entries = []
    in_data = False
    is_pinyin = "terra_pinyin" in filepath

    with open(filepath, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            # YAML front matter 結束標記
            if line.strip() == "...":
                in_data = True
                continue
            if not in_data:
                continue
            if not line or line.startswith("#"):
                continue

            parts = line.split("\t")
            if len(parts) < 2:
                continue

            word = parts[0].strip()
            reading = parts[1].strip()
            freq = 0
            if len(parts) >= 3:
                try:
                    freq = int(parts[2].strip())
                except ValueError:
                    pass

            if not word or not reading:
                continue

            # terra_pinyin 要轉換成注音
            if is_pinyin:
                try:
                    bopomofo = pinyin_to_bopomofo(reading)
                except Exception:
                    continue
            else:
                bopomofo = reading

            entries.append((word, bopomofo, freq))

    return entries


def build_db(all_entries, output_path):
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    if os.path.exists(output_path):
        os.remove(output_path)

    conn = sqlite3.connect(output_path)
    c = conn.cursor()
    c.execute("""
        CREATE TABLE rime_dict (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            word      TEXT NOT NULL,
            bopomofo  TEXT NOT NULL,
            frequency INTEGER DEFAULT 0
        )
    """)
    c.execute("CREATE INDEX idx_bopo ON rime_dict(bopomofo)")
    c.execute("CREATE INDEX idx_word ON rime_dict(word)")
    c.executemany(
        "INSERT INTO rime_dict (word, bopomofo, frequency) VALUES (?,?,?)",
        all_entries
    )
    conn.commit()
    conn.close()
    print(f"✅ Done: {len(all_entries)} entries → {output_path}")
    size_mb = os.path.getsize(output_path) / 1024 / 1024
    print(f"   DB size: {size_mb:.1f} MB")


def main():
    all_entries = []
    seen = set()

    for filepath in DICT_FILES:
        if not os.path.exists(filepath):
            print(f"⚠️  找不到詞庫：{filepath}，跳過")
            continue
        print(f"📖 解析 {filepath}...")
        entries = parse_yaml_dict(filepath)
        print(f"   → {len(entries)} 筆")
        for e in entries:
            key = (e[0], e[1])  # 去重：同一個詞+注音只保留一筆
            if key not in seen:
                seen.add(key)
                all_entries.append(e)

    if not all_entries:
        print("❌ 沒有詞庫資料，請先下載 scripts/dicts/ 裡的 .dict.yaml 檔案")
        sys.exit(1)

    print(f"\n📦 共 {len(all_entries)} 筆，寫入 DB...")
    build_db(all_entries, OUTPUT_DB)


if __name__ == "__main__":
    main()
```

### 1-C 設定 build 時自動執行

在 `app/build.gradle` 的 `android {}` 區塊之前加入：

```groovy
// RIME 詞庫轉換：每次 build 前自動執行（如果 DB 不存在）
task convertRimeDict {
    def dictDir = file("${rootDir}/scripts/dicts")
    def outputDb = file("${projectDir}/src/main/assets/rime_bopomofo.db")
    inputs.dir dictDir
    outputs.file outputDb
    doLast {
        if (!outputDb.exists() || dictDir.lastModified() > outputDb.lastModified()) {
            exec {
                commandLine 'python3', "${rootDir}/scripts/convert_rime_dict.py"
                workingDir rootDir
            }
        } else {
            println "RIME DB 已是最新版本，跳過轉換"
        }
    }
}
preBuild.dependsOn convertRimeDict
```

在 `app/src/main/assets/` 目錄加入 `.gitignore`，避免大檔案進 git：
```
rime_bopomofo.db
```

---

## 第二批 — RimeDictionary.kt（Android 查詢層）

新增 `app/src/main/java/com/snowrealm/yukiboard/input/dictionary/RimeDictionary.kt`：

```kotlin
package com.snowrealm.yukiboard.input.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * RIME 注音詞庫查詢器。
 *
 * 資料來源：scripts/dicts/bopomofo_tw.dict.yaml + terra_pinyin.dict.yaml
 * Build time 由 scripts/convert_rime_dict.py 轉換成 SQLite。
 * 首次啟動時從 assets 複製到 filesDir。
 *
 * 不依賴 librime 引擎，純 SQLite 查詢。
 */
class RimeDictionary(private val context: Context) {

    private var db: SQLiteDatabase? = null
    private val dbFile get() = File(context.filesDir, DB_FILENAME)

    /** 初始化：從 assets 複製 DB 到 filesDir（僅首次或更新時）*/
    suspend fun init() = withContext(Dispatchers.IO) {
        try {
            // 檢查 assets 是否存在 DB
            val assetList = context.assets.list("") ?: emptyArray()
            if (DB_FILENAME !in assetList) {
                return@withContext  // 詞庫未打包，跳過
            }

            if (!dbFile.exists()) {
                copyFromAssets()
            }

            db = SQLiteDatabase.openDatabase(
                dbFile.path, null, SQLiteDatabase.OPEN_READONLY
            )
        } catch (e: Exception) {
            // 詞庫初始化失敗不影響主功能
        }
    }

    /**
     * 查詢注音對應的詞，依詞頻排序。
     *
     * @param bopomofo 注音字串，例如 "ㄎㄜˇ ㄧˇ"
     * @param limit 最多回傳幾個候選詞
     * @return 候選詞列表
     */
    fun query(bopomofo: String, limit: Int = 10): List<String> {
        val db = db ?: return emptyList()
        val results = mutableListOf<String>()

        // 第一優先：精確匹配（含聲調）
        queryExact(db, bopomofo, limit).let { results.addAll(it) }

        // 第二優先：去聲調模糊匹配（補足候選詞數量）
        if (results.size < limit) {
            val stripped = stripTones(bopomofo)
            queryFuzzy(db, stripped, limit - results.size)
                .filter { it !in results }
                .let { results.addAll(it) }
        }

        return results
    }

    /**
     * 查詢前綴匹配（滑動輸入用）。
     * 例如滑了一半，ㄎㄜ 開頭的詞都顯示。
     */
    fun queryPrefix(bopomofoPrefix: String, limit: Int = 8): List<String> {
        val db = db ?: return emptyList()
        val stripped = stripTones(bopomofoPrefix)
        return try {
            val cursor = db.rawQuery(
                """SELECT word FROM rime_dict
                   WHERE bopomofo LIKE ?
                   ORDER BY frequency DESC, length(word) ASC
                   LIMIT ?""",
                arrayOf("$stripped%", limit.toString())
            )
            cursor.use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isAvailable(): Boolean = db != null

    fun close() {
        db?.close()
        db = null
    }

    // ── 內部方法 ────────────────────────────────────────────────

    private fun queryExact(db: SQLiteDatabase, bopo: String, limit: Int): List<String> {
        return try {
            val cursor = db.rawQuery(
                "SELECT word FROM rime_dict WHERE bopomofo = ? ORDER BY frequency DESC LIMIT ?",
                arrayOf(bopo, limit.toString())
            )
            cursor.use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun queryFuzzy(db: SQLiteDatabase, strippedBopo: String, limit: Int): List<String> {
        return try {
            val cursor = db.rawQuery(
                """SELECT word FROM rime_dict
                   WHERE replace(replace(replace(replace(replace(
                       bopomofo,'ˉ',''),'ˊ',''),'ˇ',''),'ˋ',''),'˙','') = ?
                   ORDER BY frequency DESC LIMIT ?""",
                arrayOf(strippedBopo, limit.toString())
            )
            cursor.use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun stripTones(bopo: String): String =
        bopo.replace(Regex("[ˉˊˇˋ˙]"), "").trim()

    private fun copyFromAssets() {
        context.assets.open(DB_FILENAME).use { input ->
            dbFile.outputStream().use { input.copyTo(it) }
        }
    }

    companion object {
        private const val DB_FILENAME = "rime_bopomofo.db"
    }
}
```

---

## 第三批 — 注音滑動手勢偵測

新增目錄：`app/src/main/java/com/snowrealm/yukiboard/input/swipe/`

### 3-A SwipeGestureDetector.kt

```kotlin
package com.snowrealm.yukiboard.input.swipe

import android.graphics.PointF
import android.view.MotionEvent

/**
 * 滑動手勢偵測器。
 * 區分「點擊」（單鍵）和「滑動」（多鍵）。
 */
class SwipeGestureDetector(
    private val onTap: (x: Float, y: Float) -> Unit,
    private val onSwipeStart: () -> Unit,
    private val onSwipeMove: (points: List<PointF>) -> Unit,
    private val onSwipeEnd: (points: List<PointF>) -> Unit
) {
    private val points = mutableListOf<PointF>()
    private var startX = 0f
    private var startY = 0f
    private var isSwiping = false

    companion object {
        const val SWIPE_THRESHOLD_DP = 12f   // 超過這個距離才算滑動
        const val SAMPLE_INTERVAL_PX = 8f    // 每隔幾px記一個點（避免資料過密）
    }

    private var density = 1f

    fun setDensity(d: Float) { density = d }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                points.clear()
                startX = x
                startY = y
                isSwiping = false
                points.add(PointF(x, y))
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - startX
                val dy = y - startY
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                if (!isSwiping && dist > SWIPE_THRESHOLD_DP * density) {
                    isSwiping = true
                    onSwipeStart()
                }

                if (isSwiping) {
                    // 間隔取樣，避免點過密
                    val last = points.last()
                    val d = Math.sqrt(
                        ((x - last.x) * (x - last.x) + (y - last.y) * (y - last.y)).toDouble()
                    ).toFloat()
                    if (d >= SAMPLE_INTERVAL_PX) {
                        points.add(PointF(x, y))
                        onSwipeMove(points.toList())
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isSwiping) {
                    points.add(PointF(x, y))
                    onSwipeEnd(points.toList())
                } else {
                    onTap(startX, startY)
                }
                points.clear()
                isSwiping = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                points.clear()
                isSwiping = false
                return true
            }
        }
        return false
    }
}
```

### 3-B BopomofoSwipeDecoder.kt

```kotlin
package com.snowrealm.yukiboard.input.swipe

import android.graphics.PointF
import android.graphics.RectF

/**
 * 把滑動路徑座標序列轉換成注音符號序列。
 *
 * 輸入：觸控點座標 List<PointF>（相對於 KeyboardView 左上角）
 * 輸出：注音符號序列，例如 ["ㄎ","ㄜ","ㄧ"]
 */
class BopomofoSwipeDecoder {

    // 注音按鍵佈局 Map：注音符號 → 鍵盤格子 RectF（相對座標）
    // 由 KeyboardView 在 layout 完成後呼叫 updateLayout() 設定
    private val keyRects = mutableMapOf<String, RectF>()

    // 注音按鍵中心點快取
    private val keyCenters = mutableMapOf<String, PointF>()

    /**
     * 由 KeyboardView 在 onSizeChanged / onLayout 後呼叫，
     * 傳入每個注音按鍵的螢幕位置。
     */
    fun updateLayout(layout: Map<String, RectF>) {
        keyRects.clear()
        keyCenters.clear()
        layout.forEach { (key, rect) ->
            keyRects[key] = rect
            keyCenters[key] = PointF(rect.centerX(), rect.centerY())
        }
    }

    /**
     * 把觸控路徑轉換成注音序列。
     *
     * @param points 觸控座標序列
     * @return 注音符號列表，已去除連續重複，例如 ["ㄎ","ㄜ","ㄧ"]
     */
    fun decode(points: List<PointF>): List<String> {
        if (points.isEmpty() || keyRects.isEmpty()) return emptyList()

        val rawSequence = mutableListOf<String>()

        for (point in points) {
            val nearest = findNearestKey(point) ?: continue
            rawSequence.add(nearest)
        }

        // 去除連續重複：ㄎㄎㄎㄜㄜㄧ → ㄎㄜㄧ
        return deduplicateConsecutive(rawSequence)
    }

    /**
     * 找最近的注音按鍵。
     */
    private fun findNearestKey(point: PointF): String? {
        // 優先：點在按鍵範圍內
        for ((key, rect) in keyRects) {
            if (rect.contains(point.x, point.y)) return key
        }

        // 次之：找距離最近的按鍵中心
        var minDist = Float.MAX_VALUE
        var nearest: String? = null

        for ((key, center) in keyCenters) {
            val dx = point.x - center.x
            val dy = point.y - center.y
            val dist = dx * dx + dy * dy  // 不需開根號，比較大小就夠
            if (dist < minDist) {
                minDist = dist
                nearest = key
            }
        }

        return nearest
    }

    private fun deduplicateConsecutive(seq: List<String>): List<String> {
        if (seq.isEmpty()) return emptyList()
        val result = mutableListOf(seq.first())
        for (i in 1 until seq.size) {
            if (seq[i] != seq[i - 1]) result.add(seq[i])
        }
        return result
    }
}
```

### 3-C SwipeCandidateRanker.kt

```kotlin
package com.snowrealm.yukiboard.input.swipe

import com.snowrealm.yukiboard.input.dictionary.RimeDictionary
import com.snowrealm.yukiboard.input.engine.ChewingInputEngine

/**
 * 把注音序列轉成候選詞，合併 ChewingInputEngine + RimeDictionary 結果。
 *
 * 優先順序：
 * 1. ChewingInputEngine（libchewing 詞頻 + 自適應學習）
 * 2. RimeDictionary（RIME 詞庫補充）
 */
class SwipeCandidateRanker(
    private val chewingEngine: ChewingInputEngine,
    private val rimeDict: RimeDictionary
) {

    /**
     * 輸入注音序列，回傳候選詞列表。
     *
     * @param bopomofoKeys 注音符號序列，例如 ["ㄎ","ㄜ","ㄧ"]
     * @return 候選詞列表，最多 10 個
     */
    fun rank(bopomofoKeys: List<String>): List<String> {
        if (bopomofoKeys.isEmpty()) return emptyList()

        // 組成注音字串，例如 "ㄎㄜㄧ"（無聲調，讓 chewing 自己推測）
        val bopomofoStr = bopomofoKeys.joinToString("")

        // 用空格分隔的版本："ㄎㄜ ㄧ"（RIME 查詢用）
        val bopomofoSpaced = bopomofoKeys.joinToString(" ")

        val results = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        // 1. 先查 ChewingInputEngine（把注音序列當作連續輸入）
        val chewingCandidates = chewingEngine.lookupByBopomofoSequence(bopomofoStr)
        for (c in chewingCandidates) {
            if (seen.add(c)) results.add(c)
        }

        // 2. 再查 RimeDictionary 補充
        if (rimeDict.isAvailable()) {
            // 嘗試多種切分方式查詢
            val rimeCandidates = rimeDict.query(bopomofoSpaced, 8)
            for (c in rimeCandidates) {
                if (seen.add(c)) results.add(c)
            }

            // 如果結果太少，也試試前綴匹配
            if (results.size < 5) {
                val prefixCandidates = rimeDict.queryPrefix(bopomofoStr, 5)
                for (c in prefixCandidates) {
                    if (seen.add(c)) results.add(c)
                }
            }
        }

        return results.take(10)
    }
}
```

---

## 第四批 — ChewingInputEngine 新增 API

更新 `ChewingInputEngine.kt`，新增以下方法：

```kotlin
/**
 * 直接用注音序列字串查詢候選詞（供滑動輸入使用）。
 * 不走 IME 輸入流程，直接查詢詞典。
 *
 * @param bopomofoSeq 注音符號連接字串，例如 "ㄎㄜㄧ"
 * @return 候選詞列表
 */
fun lookupByBopomofoSequence(bopomofoSeq: String): List<String> {
    // 用 bopomofoSeq 逐字查詢 prefixCache + 詞組詞典
    // 回傳所有長度 >= 2 的詞組匹配
    val results = mutableListOf<String>()

    // 先查完整序列
    val exact = lookupCandidates(bopomofoSeq)
    results.addAll(exact)

    // 再查子序列（允許注音序列對應多個詞）
    if (results.size < 5 && bopomofoSeq.length >= 4) {
        for (end in bopomofoSeq.length downTo 2) {
            val sub = bopomofoSeq.substring(0, end)
            val sub_candidates = lookupCandidates(sub)
            for (c in sub_candidates) {
                if (c !in results) results.add(c)
            }
            if (results.size >= 10) break
        }
    }

    return results.take(10)
}
```

---

## 第五批 — KeyboardView 整合滑動手勢

更新 `ui/keyboard/KeyboardView.kt`：

### 5-A 新增滑動相關屬性

```kotlin
// 在 class KeyboardView 頂部加入：
private val swipeDetector = SwipeGestureDetector(
    onTap = { x, y -> handleKeyPress(x, y) },
    onSwipeStart = { clearSwipeTrail() },
    onSwipeMove = { points -> updateSwipeTrail(points) },
    onSwipeEnd = { points -> handleSwipeEnd(points) }
)

private val bopomofoDecoder = BopomofoSwipeDecoder()
private val swipeTrailPoints = mutableListOf<PointF>()
private var isSwipeMode = false

// Paint for swipe trail
private val swipePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xAAB39DDB.toInt()  // 淡紫色半透明
    strokeWidth = 6f * density
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    pathEffect = CornerPathEffect(16f * density)
}
private val swipeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x44B39DDB.toInt()
    strokeWidth = 12f * density
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
}
private val swipePath = Path()
```

### 5-B 覆寫 onTouchEvent

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!prefs.swipeInputEnabled) {
        // 滑動關閉時走原本的點擊邏輯
        return handleTapEvent(event)
    }

    swipeDetector.setDensity(density)
    return swipeDetector.onTouchEvent(event)
}
```

### 5-C 滑動軌跡視覺效果

```kotlin
private fun clearSwipeTrail() {
    swipeTrailPoints.clear()
    isSwipeMode = true
    invalidate()
}

private fun updateSwipeTrail(points: List<PointF>) {
    swipeTrailPoints.clear()
    swipeTrailPoints.addAll(points)
    invalidate()
}

private fun handleSwipeEnd(points: List<PointF>) {
    isSwipeMode = false
    swipeTrailPoints.clear()

    // 解碼注音序列
    val bopomofoKeys = bopomofoDecoder.decode(points)
    if (bopomofoKeys.isNotEmpty()) {
        onSwipeInput?.invoke(bopomofoKeys)  // 回呼給 IMEService 處理
    }
    invalidate()
}
```

### 5-D 在 onDraw 繪製滑動軌跡

在 `onDraw()` 末尾加入：

```kotlin
// 滑動軌跡
if (isSwipeMode && swipeTrailPoints.size >= 2) {
    swipePath.reset()
    swipePath.moveTo(swipeTrailPoints[0].x, swipeTrailPoints[0].y)
    for (i in 1 until swipeTrailPoints.size) {
        swipePath.lineTo(swipeTrailPoints[i].x, swipeTrailPoints[i].y)
    }
    // 外發光
    canvas.drawPath(swipePath, swipeGlowPaint)
    // 軌跡線
    canvas.drawPath(swipePath, swipePaint)

    // 起點圓點
    val start = swipeTrailPoints.first()
    canvas.drawCircle(start.x, start.y, 10f * density, swipePaint)
}
```

### 5-E 更新注音鍵盤佈局後通知 decoder

在注音鍵盤 layout 完成時呼叫：

```kotlin
fun notifyBopomofoLayout(keyRects: Map<String, RectF>) {
    bopomofoDecoder.updateLayout(keyRects)
}

// 外部呼叫入口（IMEService 或 InputRouter 設定）
var onSwipeInput: ((List<String>) -> Unit)? = null
```

---

## 第六批 — YukiBoardIMEService 整合

更新 `service/YukiBoardIMEService.kt`：

### 6-A 初始化 RimeDictionary 和 SwipeCandidateRanker

```kotlin
// 在 class 頂部加入：
private lateinit var rimeDict: RimeDictionary
private lateinit var swipeRanker: SwipeCandidateRanker

// 在 onCreate() 裡加入：
rimeDict = RimeDictionary(applicationContext)
serviceScope.launch(Dispatchers.IO) {
    rimeDict.init()
    // RimeDictionary ready 後初始化 SwipeCandidateRanker
    swipeRanker = SwipeCandidateRanker(
        chewingEngine = inputRouter.getChewingEngine(),
        rimeDict = rimeDict
    )
}

// 在 onDestroy() 裡加入：
rimeDict.close()
```

### 6-B 處理滑動輸入回呼

```kotlin
// 在 onCreateInputView() 裡，設定鍵盤的滑動回呼：
keyboardView.onSwipeInput = { bopomofoKeys ->
    handleSwipeInput(bopomofoKeys)
}

// 新增方法：
private fun handleSwipeInput(bopomofoKeys: List<String>) {
    if (!prefs.swipeInputEnabled) return
    serviceScope.launch(Dispatchers.Main) {
        val candidates = withContext(Dispatchers.IO) {
            swipeRanker.rank(bopomofoKeys)
        }
        if (candidates.isNotEmpty()) {
            // 更新候選字列，標記這些是滑動輸入的結果
            keyboardView.updateCandidates(candidates)
            keyboardView.markSwipeCandidates(candidates)
        }
    }
}
```

---

## 第七批 — YukiPreferences 新增開關

在 `YukiPreferences.kt` 加入：

```kotlin
var swipeInputEnabled: Boolean by BoolPref("swipe_input", true)
var rimeDictEnabled: Boolean by BoolPref("rime_dict", true)
```

在 `SettingsActivity.kt` 的「輸入行為」群組加入對應開關 UI。

---

## 第八批 — 候選字視覺標示

更新 `KeyboardView.kt`，加入：

```kotlin
private var swipeCandidateSet = emptySet<String>()

fun markSwipeCandidates(candidates: List<String>) {
    swipeCandidateSet = candidates.toSet()
    invalidate()
}

// 在繪製候選字時，如果 candidate in swipeCandidateSet：
// → 文字顏色改為淡紫色 #B39DDB
// → 左側加一個小 ≈ 符號
// → 表示這是滑動輸入的結果
```

---

## 執行順序確認

```
第一批：RIME 詞庫腳本 + build.gradle 設定
    ↓
第二批：RimeDictionary.kt
    ↓
第三批：SwipeGestureDetector + BopomofoSwipeDecoder + SwipeCandidateRanker
    ↓
第四批：ChewingInputEngine 新增 lookupByBopomofoSequence()
    ↓
第五批：KeyboardView 滑動視覺 + 回呼
    ↓
第六批：YukiBoardIMEService 整合
    ↓
第七批：YukiPreferences 開關
    ↓
第八批：候選字視覺標示
    ↓
Build 驗證：確認 BUILD SUCCESSFUL
```

---

## 注意事項

1. **詞庫檔案需手動下載**（或用 scripts 裡的 curl 指令），build 前必須存在於 `scripts/dicts/`

2. **注音佈局 Map**：`BopomofoSwipeDecoder.updateLayout()` 需要在 KeyboardView layout 完成後呼叫，傳入每個注音按鍵的 RectF。這個 Map 由 KeyboardView 內部根據實際 layout 計算。

3. **聲調處理**：滑動輸入暫時不需要輸入聲調，`SwipeCandidateRanker` 會用無聲調模糊匹配，候選詞用詞頻排序。

4. **受開關控制**：所有滑動相關功能受 `prefs.swipeInputEnabled` 控制，關閉時 KeyboardView 走原本的點擊邏輯。

5. **RIME DB 大小**：約 8-15MB，打包進 assets，首次啟動時複製到 filesDir，之後直接讀取不再複製。

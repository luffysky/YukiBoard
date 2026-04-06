package com.snowrealm.yukiboard.input.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * RIME 注音詞庫查詢層。
 *
 * 從 assets/rime_bopomofo.db 複製到 filesDir，使用 SQLite 查詢。
 * 支援：精確查詢（含聲調）、去聲調模糊匹配、前綴匹配（滑動用）。
 *
 * DB schema 預期：
 * ```
 * CREATE TABLE dict (
 *     word         TEXT NOT NULL,
 *     bopomofo     TEXT NOT NULL,   -- 含聲調，例如 "ㄓㄨˋ ㄧㄣ"
 *     bopomofo_plain TEXT NOT NULL, -- 去聲調，例如 "ㄓㄨ ㄧㄣ"
 *     frequency    INTEGER NOT NULL DEFAULT 0
 * );
 * CREATE INDEX idx_bopomofo ON dict(bopomofo);
 * CREATE INDEX idx_bopomofo_plain ON dict(bopomofo_plain);
 * ```
 */
class RimeDictionary(private val context: Context) {

    companion object {
        private const val TAG = "RimeDictionary"
        private const val DB_NAME = "rime_bopomofo.db"
        private const val VERSION_FILE = "rime_bopomofo.version"

        /** 遞增此值以強制重新複製 asset DB */
        private const val DB_VERSION = 1
    }

    private var db: SQLiteDatabase? = null

    // ─────────────────────────────────────────
    // 初始化
    // ─────────────────────────────────────────

    /**
     * 從 assets 複製 DB 到 filesDir（若尚未複製或版本不符）。
     * 應在 IO 執行緒呼叫（例如 `withContext(Dispatchers.IO)`）。
     */
    suspend fun init() = withContext(Dispatchers.IO) {
        try {
            val dbFile = File(context.filesDir, DB_NAME)
            if (!dbFile.exists() || isVersionMismatch()) {
                copyFromAssets(dbFile)
                writeVersionMarker()
            }
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            Log.i(TAG, "RIME DB opened: ${dbFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init RIME DB", e)
            db = null
        }
    }

    /** DB 是否已成功開啟並可查詢 */
    fun isAvailable(): Boolean = db != null

    // ─────────────────────────────────────────
    // 查詢
    // ─────────────────────────────────────────

    /**
     * 精確查詢（含聲調）。
     * @param bopomofo 含聲調的注音字串，例如 "ㄓㄨˋ ㄧㄣ"
     * @param limit 最大回傳筆數
     * @return 依頻率降序排列的候選詞列表
     */
    fun query(bopomofo: String, limit: Int = 20): List<String> {
        val database = db ?: return emptyList()
        val results = mutableListOf<String>()
        try {
            database.rawQuery(
                "SELECT word FROM dict WHERE bopomofo = ? ORDER BY frequency DESC LIMIT ?",
                arrayOf(bopomofo, limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(cursor.getString(0))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "query() failed for: $bopomofo", e)
        }
        return results
    }

    /**
     * 去聲調模糊匹配。
     * @param bopomofoPlain 去聲調注音，例如 "ㄓㄨ ㄧㄣ"
     * @param limit 最大回傳筆數
     * @return 依頻率降序排列的候選詞列表
     */
    fun queryFuzzy(bopomofoPlain: String, limit: Int = 20): List<String> {
        val database = db ?: return emptyList()
        val results = mutableListOf<String>()
        try {
            database.rawQuery(
                "SELECT word FROM dict WHERE bopomofo_plain = ? ORDER BY frequency DESC LIMIT ?",
                arrayOf(bopomofoPlain, limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(cursor.getString(0))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryFuzzy() failed for: $bopomofoPlain", e)
        }
        return results
    }

    /**
     * 前綴匹配（滑動輸入用，輸入部分注音即時顯示候選）。
     * @param prefix 注音前綴（去聲調），例如 "ㄓㄨ"
     * @param limit 最大回傳筆數
     * @return 依頻率降序排列的候選詞列表
     */
    fun queryPrefix(prefix: String, limit: Int = 30): List<String> {
        val database = db ?: return emptyList()
        if (prefix.isBlank()) return emptyList()
        val results = mutableListOf<String>()
        try {
            database.rawQuery(
                "SELECT word FROM dict WHERE bopomofo_plain LIKE ? ORDER BY frequency DESC LIMIT ?",
                arrayOf("$prefix%", limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(cursor.getString(0))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryPrefix() failed for: $prefix", e)
        }
        return results
    }

    // ─────────────────────────────────────────
    // 關閉
    // ─────────────────────────────────────────

    /** 關閉 DB 連線，應在 IME onDestroy 時呼叫 */
    fun close() {
        try {
            db?.close()
        } catch (e: Exception) {
            Log.e(TAG, "close() failed", e)
        } finally {
            db = null
        }
    }

    // ─────────────────────────────────────────
    // 內部工具
    // ─────────────────────────────────────────

    private fun copyFromAssets(destFile: File) {
        context.assets.open(DB_NAME).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
        Log.i(TAG, "Copied $DB_NAME from assets to ${destFile.absolutePath}")
    }

    private fun isVersionMismatch(): Boolean {
        val versionFile = File(context.filesDir, VERSION_FILE)
        if (!versionFile.exists()) return true
        return try {
            versionFile.readText().trim().toInt() != DB_VERSION
        } catch (_: Exception) {
            true
        }
    }

    private fun writeVersionMarker() {
        val versionFile = File(context.filesDir, VERSION_FILE)
        versionFile.writeText(DB_VERSION.toString())
    }
}

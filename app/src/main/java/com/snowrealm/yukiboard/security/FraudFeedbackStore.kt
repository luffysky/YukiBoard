package com.snowrealm.yukiboard.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

/**
 * 防詐回饋 SQLite 儲存。
 * 記錄使用者對每次偵測結果的回饋（是/不是詐騙），
 * 供 FraudRuleWeightManager 調整個人化權���。
 */
class FraudFeedbackStore(context: Context) : SQLiteOpenHelper(
    context, "fraud_feedback.db", null, 1
) {
    data class FeedbackRecord(
        val id: Long,
        val textHash: String,
        val fraudType: String,
        val isFraud: Boolean,
        val ruleScore: Int,
        val matchedKeywords: String,
        val timestamp: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text_hash TEXT NOT NULL,
                fraud_type TEXT NOT NULL,
                is_fraud INTEGER NOT NULL,
                rule_score INTEGER NOT NULL,
                matched_keywords TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX idx_feedback_type ON feedback(fraud_type)")
        db.execSQL("CREATE INDEX idx_feedback_hash ON feedback(text_hash)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS feedback")
        onCreate(db)
    }

    /** 儲���一筆回饋 */
    fun saveFeedback(
        text: String,
        fraudType: FraudAIAnalyzer.FraudType,
        isFraud: Boolean,
        ruleScore: Int,
        matchedKeywords: List<String>
    ) {
        val hash = sha256(text.take(500))  // 只 hash 前 500 字，不儲存原文
        writableDatabase.insert("feedback", null, android.content.ContentValues().apply {
            put("text_hash", hash)
            put("fraud_type", fraudType.name)
            put("is_fraud", if (isFraud) 1 else 0)
            put("rule_score", ruleScore)
            put("matched_keywords", matchedKeywords.joinToString(","))
            put("timestamp", System.currentTimeMillis())
        })
    }

    /** 取得某類型的回饋統計 */
    fun getFeedbackStats(fraudType: String): Pair<Int, Int> {
        val db = readableDatabase
        var positive = 0
        var negative = 0
        db.rawQuery(
            "SELECT is_fraud, COUNT(*) FROM feedback WHERE fraud_type = ? GROUP BY is_fraud",
            arrayOf(fraudType)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val isFraud = cursor.getInt(0) == 1
                val count = cursor.getInt(1)
                if (isFraud) positive = count else negative = count
            }
        }
        return positive to negative
    }

    /** 取得所有類型的回饋統計 */
    fun getAllFeedbackStats(): Map<String, Pair<Int, Int>> {
        val result = mutableMapOf<String, Pair<Int, Int>>()
        readableDatabase.rawQuery(
            "SELECT fraud_type, is_fraud, COUNT(*) FROM feedback GROUP BY fraud_type, is_fraud",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val type = cursor.getString(0)
                val isFraud = cursor.getInt(1) == 1
                val count = cursor.getInt(2)
                val (pos, neg) = result[type] ?: (0 to 0)
                result[type] = if (isFraud) (pos + count to neg) else (pos to neg + count)
            }
        }
        return result
    }

    /** 取得某關鍵字被標記為「不是詐騙」的次數 */
    fun getKeywordFalsePositiveCount(keyword: String): Int {
        var count = 0
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM feedback WHERE is_fraud = 0 AND matched_keywords LIKE ?",
            arrayOf("%$keyword%")
        ).use { cursor ->
            if (cursor.moveToFirst()) count = cursor.getInt(0)
        }
        return count
    }

    /** 取得某關鍵字被標記為「確實詐騙」的次數 */
    fun getKeywordTruePositiveCount(keyword: String): Int {
        var count = 0
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM feedback WHERE is_fraud = 1 AND matched_keywords LIKE ?",
            arrayOf("%$keyword%")
        ).use { cursor ->
            if (cursor.moveToFirst()) count = cursor.getInt(0)
        }
        return count
    }

    /** 回饋總筆數 */
    fun totalCount(): Int {
        var count = 0
        readableDatabase.rawQuery("SELECT COUNT(*) FROM feedback", null).use {
            if (it.moveToFirst()) count = it.getInt(0)
        }
        return count
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

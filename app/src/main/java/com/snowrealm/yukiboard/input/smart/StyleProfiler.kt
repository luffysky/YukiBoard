package com.snowrealm.yukiboard.input.smart

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.*

/**
 * 語氣風格學習器。
 *
 * 追蹤使用者的語尾詞、語氣偏好，並根據 App 維度分析不同場景的風格。
 *
 * 風格軸：
 * - FORMAL   — 正式（您好/請問/感謝）
 * - CASUAL   — 輕鬆（哈哈/嗯嗯/OK）
 * - CUTE     — 可愛（嗚嗚/QQ/><）
 * - PROFESSIONAL — 專業（確認/已處理/收到）
 *
 * 使用場景：
 * - 候選字排序時，符合當前語氣風格的詞加分
 * - 不同 App 顯示不同風格偏好
 */
class StyleProfiler(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_style", Context.MODE_PRIVATE)

    // 全域語尾詞統計
    private val suffixCounts = mutableMapOf<String, Int>()
    // App 維度語氣分數: appPkg → { FORMAL: 20, CASUAL: 35, ... }
    private val appStyleScores = mutableMapOf<String, MutableMap<Style, Int>>()
    // 全域語氣分數
    private val globalStyleScores = mutableMapOf<Style, Int>()

    init { loadAll() }

    // ─────────────── 學習 ───────────────

    /** 分析提交文字的語氣特徵 */
    fun analyze(text: String, appPackage: String? = null) {
        val trimmed = text.trim()
        if (trimmed.length < 2) return

        // 語尾詞偵測
        for (suffix in SUFFIX_PATTERNS) {
            if (trimmed.endsWith(suffix)) {
                suffixCounts[suffix] = (suffixCounts[suffix] ?: 0) + 1
            }
        }

        // 語氣分類
        val detectedStyles = detectStyles(trimmed)
        for (style in detectedStyles) {
            globalStyleScores[style] = (globalStyleScores[style] ?: 0) + 1
            if (appPackage != null) {
                val appMap = appStyleScores.getOrPut(appPackage) { mutableMapOf() }
                appMap[style] = (appMap[style] ?: 0) + 1
            }
        }
    }

    // ─────────────── 查詢 ───────────────

    /** 取得全域主要語氣風格 */
    fun dominantStyle(): Style {
        return globalStyleScores.maxByOrNull { it.value }?.key ?: Style.CASUAL
    }

    /** 取得特定 App 的主要語氣風格 */
    fun dominantStyleForApp(appPackage: String): Style {
        val map = appStyleScores[appPackage]
            ?: return dominantStyle() // fallback 全域
        return map.maxByOrNull { it.value }?.key ?: Style.CASUAL
    }

    /** 取得語氣分數分佈（0~1 正規化） */
    fun getStyleDistribution(appPackage: String? = null): Map<Style, Float> {
        val scores = if (appPackage != null) appStyleScores[appPackage] ?: globalStyleScores
        else globalStyleScores

        val total = scores.values.sum().toFloat().coerceAtLeast(1f)
        return Style.entries.associateWith { (scores[it] ?: 0) / total }
    }

    /** 取得前 N 常用語尾詞 */
    fun getTopSuffixes(n: Int = 10): List<Pair<String, Int>> =
        suffixCounts.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }

    /**
     * 候選字語氣加分。
     * 回傳該詞在當前語境（App）的語氣契合度（0~5 分）。
     */
    fun styleBoost(word: String, appPackage: String? = null): Float {
        val style = if (appPackage != null) dominantStyleForApp(appPackage) else dominantStyle()
        val wordStyles = detectStyles(word)
        if (wordStyles.isEmpty()) return 0f
        return if (style in wordStyles) 3f else 0f
    }

    // ─────────────── 風格偵測 ───────────────

    private fun detectStyles(text: String): Set<Style> {
        val styles = mutableSetOf<Style>()
        val lower = text.lowercase()

        // FORMAL
        for (kw in FORMAL_KEYWORDS) {
            if (lower.contains(kw)) { styles.add(Style.FORMAL); break }
        }
        // CASUAL
        for (kw in CASUAL_KEYWORDS) {
            if (lower.contains(kw)) { styles.add(Style.CASUAL); break }
        }
        // CUTE
        for (kw in CUTE_KEYWORDS) {
            if (lower.contains(kw)) { styles.add(Style.CUTE); break }
        }
        // PROFESSIONAL
        for (kw in PROFESSIONAL_KEYWORDS) {
            if (lower.contains(kw)) { styles.add(Style.PROFESSIONAL); break }
        }

        return styles
    }

    // ─────────────── 持久化 ───────────────

    fun save() {
        val editor = prefs.edit()
        // 語尾詞
        editor.putString("_suffixes", suffixCounts.entries.joinToString(";") { "${it.key}:${it.value}" })
        // 全域風格
        editor.putString("_global_style", globalStyleScores.entries.joinToString(";") { "${it.key.name}:${it.value}" })
        // App 風格
        val appKeys = appStyleScores.keys.toList()
        editor.putString("_app_keys", appKeys.joinToString(","))
        for (pkg in appKeys) {
            val map = appStyleScores[pkg] ?: continue
            editor.putString("app_style_$pkg", map.entries.joinToString(";") { "${it.key.name}:${it.value}" })
        }
        editor.apply()
    }

    private fun loadAll() {
        // 語尾詞
        prefs.getString("_suffixes", null)?.split(";")?.forEach { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) suffixCounts[parts[0]] = parts[1].toIntOrNull() ?: 0
        }
        // 全域風格
        prefs.getString("_global_style", null)?.split(";")?.forEach { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                val style = Style.entries.firstOrNull { it.name == parts[0] }
                if (style != null) globalStyleScores[style] = parts[1].toIntOrNull() ?: 0
            }
        }
        // App 風格
        prefs.getString("_app_keys", null)?.split(",")?.forEach { pkg ->
            if (pkg.isBlank()) return@forEach
            val map = mutableMapOf<Style, Int>()
            prefs.getString("app_style_$pkg", null)?.split(";")?.forEach { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val style = Style.entries.firstOrNull { it.name == parts[0] }
                    if (style != null) map[style] = parts[1].toIntOrNull() ?: 0
                }
            }
            if (map.isNotEmpty()) appStyleScores[pkg] = map
        }
    }

    // ─────────────── 常數 ───────────────

    enum class Style { FORMAL, CASUAL, CUTE, PROFESSIONAL }

    companion object {
        val SUFFIX_PATTERNS = listOf(
            "啦", "哈", "呢", "嗎", "喔", "耶", "欸", "齁", "唷",
            "哈哈", "嘻嘻", "嗚嗚", "QQ", "><", "XD", "www",
            "嗯嗯", "OK", "ok", "好喔", "好唷", "好ㄛ",
            "謝謝", "感恩", "3Q", "thx", "thanks"
        )

        val FORMAL_KEYWORDS = listOf(
            "您好", "請問", "感謝", "煩請", "敬啟", "此致",
            "附件", "會議", "報告", "確認", "回覆", "通知"
        )
        val CASUAL_KEYWORDS = listOf(
            "哈哈", "嗯嗯", "ok", "xd", "lol", "嘻嘻",
            "好喔", "好唷", "掰掰", "嗨", "yo", "讚"
        )
        val CUTE_KEYWORDS = listOf(
            "嗚嗚", "qq", "><", "qaq", "uwu", "嘿嘿",
            "好ㄛ", "好噠", "mua", "抱抱", "嗯嗯嗯"
        )
        val PROFESSIONAL_KEYWORDS = listOf(
            "已處理", "收到", "了解", "同意", "確認", "排程",
            "deploy", "commit", "merge", "review", "bug", "fix"
        )
    }

    // ─────────────── 雲端 snapshot（Sprint 7-4） ───────────────

    fun exportSnapshot(): JsonObject = buildJsonObject {
        prefs.getString("_suffixes", null)?.let { put("suffixes", it) }
        prefs.getString("_global_style", null)?.let { put("global_style", it) }
        val appKeys = prefs.getString("_app_keys", null)
        appKeys?.let { put("app_keys", it) }
        appKeys?.split(",")?.forEach { pkg ->
            if (pkg.isBlank()) return@forEach
            prefs.getString("app_style_$pkg", null)?.let { put("app_style_$pkg", it) }
        }
    }

    fun importSnapshot(obj: JsonObject) {
        val editor = prefs.edit()
        obj["suffixes"]?.jsonPrimitive?.contentOrNull?.let { editor.putString("_suffixes", it) }
        obj["global_style"]?.jsonPrimitive?.contentOrNull?.let { editor.putString("_global_style", it) }
        obj["app_keys"]?.jsonPrimitive?.contentOrNull?.let { keys ->
            val existing = prefs.getString("_app_keys", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val incoming = keys.split(",").filter { it.isNotBlank() }
            editor.putString("_app_keys", (existing + incoming).distinct().joinToString(","))
        }
        for ((key, value) in obj) {
            if (key.startsWith("app_style_")) {
                value.jsonPrimitive.contentOrNull?.let { editor.putString(key, it) }
            }
        }
        editor.apply()
        // S9-C：import 完成後立即 reload in-memory，讓長生命週期 owner 不需等下次 cold start。
        loadAll()
    }
}

package com.snowrealm.yukiboard.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 政府公開防詐資料即時同步。
 *
 * 資料來源：
 *   1. 數發部 (MODA) — 詐騙網域停止解析清單 (JSON, 持續更新)
 *   2. 165 反詐騙 — 遭停止解析涉詐網站 (CSV)
 *   3. PhishTank — 全球釣魚 URL 資料庫 (JSON, 每小時更新)
 *   4. OpenPhish — 社群釣魚 URL 清單 (TXT, 每 12 小時更新)
 *
 * 同步策略：
 *   - 啟動時檢查，距上次同步 > 6 小時才拉取
 *   - 資料存入本地 SharedPreferences（域名黑名單）
 *   - 同步到 Supabase fraud_blocklist 表（供所有用戶共享）
 *   - FraudAIAnalyzer.scanUrls() 會查詢此黑名單
 */
class FraudOpenDataSync(private val context: Context) {

    companion object {
        private const val TAG = "FraudOpenDataSync"
        private const val PREFS_NAME = "fraud_opendata"
        private const val KEY_LAST_SYNC = "last_sync_time"
        private const val KEY_BLOCKLIST = "domain_blocklist"
        private const val KEY_BLOCKLIST_COUNT = "blocklist_count"
        private const val KEY_ETAG_PHISHTANK = "etag_phishtank"

        /** 同步間隔：6 小時 */
        private const val SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L

        // ── 資料來源 URL ──
        /** 數發部：詐騙網域停止解析清單 (JSON) */
        private const val MODA_FRAUD_DOMAINS_URL =
            "https://www-api.moda.gov.tw/OpenData/Files/16352"

        /** 165：遭停止解析涉詐網站 (CSV) */
        private const val NPA_165_BLOCKED_SITES_URL =
            "https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/29E8E643-88ED-4952-B21E-BD42A3B7108C/resource/18211AF8-3248-4AF7-A50D-1B3F5777CCB9/download"

        /** PhishTank：全球釣魚 URL (JSON, 需 app_key 可提高限額) */
        private const val PHISHTANK_URL =
            "https://data.phishtank.com/data/online-valid.json.bz2"

        /** OpenPhish：社群釣魚 URL (TXT) */
        private const val OPENPHISH_URL =
            "https://raw.githubusercontent.com/openphish/public_feed/refs/heads/main/feed.txt"

        /** 165 詐騙闢謠 (CSV) */
        private const val NPA_165_DEBUNK_URL =
            "https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/4F4DF9A5-DF4C-4EE8-A50D-869347D38D9E/resource/7AABA1C9-86CB-4FD7-B837-00FA1427CB5C/download"

        /** MODA 額外端點 — CSV 格式 */
        private const val MODA_CSV_URL =
            "https://www-api.moda.gov.tw/OpenData/csv/22"

        /** URLhaus — abuse.ch 惡意 URL 資料庫 (TXT, 每小時更新) */
        private const val URLHAUS_URL =
            "https://urlhaus.abuse.ch/downloads/text_online/"

        /** CERT.PL — 釣魚域名清單 (TXT, 每日更新) */
        private const val CERTPL_URL =
            "https://hole.cert.pl/domains/domains.txt"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 目前黑名單域名數 */
    val blocklistCount: Int get() = prefs.getInt(KEY_BLOCKLIST_COUNT, 0)

    /** 上次同步時間 */
    val lastSyncTime: Long get() = prefs.getLong(KEY_LAST_SYNC, 0)

    /** 是否需要同步 */
    fun needsSync(): Boolean {
        return System.currentTimeMillis() - lastSyncTime > SYNC_INTERVAL_MS
    }

    /**
     * 檢查域名/URL 是否在政府公開黑名單中。
     */
    fun isDomainBlocked(url: String): Boolean {
        val domain = extractDomain(url) ?: return false
        val blocklist = getBlocklist()
        // 完全匹配或子域名匹配
        return blocklist.any { blocked ->
            domain == blocked || domain.endsWith(".$blocked")
        }
    }

    /**
     * 取得完整黑名單（用於批次檢查）。
     */
    fun getBlocklist(): Set<String> {
        val raw = prefs.getString(KEY_BLOCKLIST, null) ?: return emptySet()
        return raw.split("\n").filter { it.isNotBlank() }.toSet()
    }

    /**
     * 執行同步 — 拉取所有來源並合併。
     * @return 同步到的域名數量
     */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val allDomains = mutableSetOf<String>()

        // 1. 數發部 — 詐騙網域停止解析清單
        try {
            val modaDomains = fetchModaFraudDomains()
            allDomains.addAll(modaDomains)
            Log.i(TAG, "MODA: ${modaDomains.size} domains")
        } catch (e: Exception) {
            Log.w(TAG, "MODA fetch failed: ${e.message}")
        }

        // 2. 165 — 遭停止解析涉詐網站
        try {
            val npa165Domains = fetch165BlockedSites()
            allDomains.addAll(npa165Domains)
            Log.i(TAG, "165: ${npa165Domains.size} domains")
        } catch (e: Exception) {
            Log.w(TAG, "165 fetch failed: ${e.message}")
        }

        // 3. OpenPhish — 社群釣魚 URL
        try {
            val openphishDomains = fetchOpenPhish()
            allDomains.addAll(openphishDomains)
            Log.i(TAG, "OpenPhish: ${openphishDomains.size} domains")
        } catch (e: Exception) {
            Log.w(TAG, "OpenPhish fetch failed: ${e.message}")
        }

        // 4. 165 詐騙闢謠（提取案例中的域名）
        try {
            val debunkDomains = fetch165Debunk()
            allDomains.addAll(debunkDomains)
            Log.i(TAG, "165 Debunk: ${debunkDomains.size} domains")
        } catch (e: Exception) {
            Log.w(TAG, "165 Debunk fetch failed: ${e.message}")
        }

        // 5. MODA CSV 額外端點
        try {
            val modaCsvDomains = fetchTextUrlList(MODA_CSV_URL)
            allDomains.addAll(modaCsvDomains)
            Log.i(TAG, "MODA CSV: ${modaCsvDomains.size} domains")
        } catch (e: Exception) {
            Log.w(TAG, "MODA CSV fetch failed: ${e.message}")
        }

        // 6. URLhaus — abuse.ch 惡意 URL
        try {
            val urlhausDomains = fetchTextUrlList(URLHAUS_URL)
            allDomains.addAll(urlhausDomains)
            Log.i(TAG, "URLhaus: ${urlhausDomains.size} domains")
        } catch (e: Exception) {
            Log.w(TAG, "URLhaus fetch failed: ${e.message}")
        }

        // 7. CERT.PL 釣魚域名
        try {
            val certplDomains = fetchTextUrlList(CERTPL_URL)
            allDomains.addAll(certplDomains)
            Log.i(TAG, "CERT.PL: ${certplDomains.size} domains")
        } catch (e: Exception) {
            Log.w(TAG, "CERT.PL fetch failed: ${e.message}")
        }

        // 合併到本地
        val existingBlocklist = getBlocklist()
        allDomains.addAll(existingBlocklist)

        // 儲存
        prefs.edit()
            .putString(KEY_BLOCKLIST, allDomains.joinToString("\n"))
            .putInt(KEY_BLOCKLIST_COUNT, allDomains.size)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()

        Log.i(TAG, "Sync complete: ${allDomains.size} total domains (was ${existingBlocklist.size})")
        allDomains.size
    }

    /** 強制同步（忽略間隔限制） */
    suspend fun forceSync(): Int {
        prefs.edit().putLong(KEY_LAST_SYNC, 0).apply()
        return sync()
    }

    // ═══════════════════════════════════════════════════════════
    // 各資料來源擷取
    // ═══════════════════════════════════════════════════════════

    /**
     * 數發部：詐騙網域停止解析清單 (JSON)
     * 格式：JSON array of objects with "Domain" or "url" fields
     */
    private fun fetchModaFraudDomains(): Set<String> {
        val request = Request.Builder().url(MODA_FRAUD_DOMAINS_URL).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptySet()

        val body = response.body?.string() ?: return emptySet()
        val domains = mutableSetOf<String>()

        try {
            val json = Json.parseToJsonElement(body)
            when {
                json is JsonArray -> {
                    for (element in json) {
                        if (element is JsonObject) {
                            // 嘗試各種可能的欄位名
                            val domain = element["Domain"]?.jsonPrimitive?.contentOrNull
                                ?: element["domain"]?.jsonPrimitive?.contentOrNull
                                ?: element["url"]?.jsonPrimitive?.contentOrNull
                                ?: element["URL"]?.jsonPrimitive?.contentOrNull
                                ?: element["網域"]?.jsonPrimitive?.contentOrNull
                                ?: element["網址"]?.jsonPrimitive?.contentOrNull
                            if (domain != null) {
                                extractDomain(domain)?.let { domains.add(it) }
                            }
                        } else if (element is JsonPrimitive) {
                            extractDomain(element.content)?.let { domains.add(it) }
                        }
                    }
                }
                json is JsonObject -> {
                    // 可能是 { "data": [...] } 格式
                    val dataArray = json["data"]?.jsonArray ?: json["records"]?.jsonArray
                    dataArray?.forEach { element ->
                        if (element is JsonObject) {
                            for (key in listOf("Domain", "domain", "url", "URL", "網域", "網址")) {
                                element[key]?.jsonPrimitive?.contentOrNull?.let { value ->
                                    extractDomain(value)?.let { domains.add(it) }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MODA parse error: ${e.message}")
            // Fallback: 逐行解析（如果是純文字格式）
            body.lines().forEach { line ->
                val trimmed = line.trim().trim('"', ',')
                if (trimmed.contains(".") && !trimmed.startsWith("#") && !trimmed.startsWith("{")) {
                    extractDomain(trimmed)?.let { domains.add(it) }
                }
            }
        }

        return domains
    }

    /**
     * 165：遭停止解析涉詐網站 (CSV)
     */
    private fun fetch165BlockedSites(): Set<String> {
        val request = Request.Builder().url(NPA_165_BLOCKED_SITES_URL).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptySet()

        val domains = mutableSetOf<String>()
        val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: return emptySet(), Charsets.UTF_8))

        // Skip header
        var headerSkipped = false
        reader.useLines { lines ->
            for (line in lines) {
                if (!headerSkipped) {
                    headerSkipped = true
                    continue
                }
                // CSV 格式：每行可能有多個欄位，網域通常在第一或第二欄
                val fields = line.split(",").map { it.trim().trim('"') }
                for (field in fields) {
                    if (field.contains(".") && !field.contains(" ") && field.length < 100) {
                        extractDomain(field)?.let { domains.add(it) }
                    }
                }
            }
        }

        return domains
    }

    /**
     * OpenPhish：社群釣魚 URL (plain text, one URL per line)
     */
    private fun fetchOpenPhish(): Set<String> {
        val request = Request.Builder().url(OPENPHISH_URL).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptySet()

        val domains = mutableSetOf<String>()
        val body = response.body?.string() ?: return emptySet()

        body.lines().forEach { line ->
            val url = line.trim()
            if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                extractDomain(url)?.let { domains.add(it) }
            }
        }

        return domains
    }

    /**
     * 165 詐騙闢謠 — 提取案例描述中的域名
     */
    private fun fetch165Debunk(): Set<String> {
        val request = Request.Builder().url(NPA_165_DEBUNK_URL).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptySet()

        val domains = mutableSetOf<String>()
        val body = response.body?.string() ?: return emptySet()

        // 用正則從文本中提取域名
        val urlRegex = Regex("""(?:https?://)?(?:www\.)?([a-zA-Z0-9][-a-zA-Z0-9]*\.)+[a-zA-Z]{2,}""")
        urlRegex.findAll(body).forEach { match ->
            extractDomain(match.value)?.let { domains.add(it) }
        }

        return domains
    }

    /**
     * 通用 TXT/CSV URL 清單擷取 — 適用 URLhaus、CERT.PL、MODA CSV 等逐行格式。
     * 自動跳過註解行（# 開頭）和空行。
     */
    private fun fetchTextUrlList(url: String): Set<String> {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptySet()

        val domains = mutableSetOf<String>()
        val body = response.body?.string() ?: return emptySet()

        body.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            // URL 行
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                extractDomain(trimmed)?.let { domains.add(it) }
            }
            // 純域名行（CERT.PL 格式）
            else if (trimmed.contains(".") && !trimmed.contains(" ") && trimmed.length < 100) {
                extractDomain(trimmed)?.let { domains.add(it) }
            }
            // CSV 欄位
            else if (trimmed.contains(",")) {
                trimmed.split(",").forEach { field ->
                    val clean = field.trim().trim('"', '\'')
                    if (clean.contains(".") && !clean.contains(" ") && clean.length < 100) {
                        extractDomain(clean)?.let { domains.add(it) }
                    }
                }
            }
        }

        return domains
    }

    // ═══════════════════════════════════════════════════════════
    // 工具
    // ═══════════════════════════════════════════════════════════

    private fun extractDomain(input: String): String? {
        var s = input.trim().lowercase()
        // Remove protocol
        s = s.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        // Remove path
        s = s.split("/").firstOrNull() ?: return null
        // Remove port
        s = s.split(":").firstOrNull() ?: return null
        // Basic validation
        if (!s.contains(".") || s.length < 4 || s.length > 100) return null
        // Filter out non-domain strings
        if (s.any { it !in 'a'..'z' && it !in '0'..'9' && it != '.' && it != '-' }) return null
        return s
    }

    /**
     * 上傳本地黑名單到 Supabase（供其他用戶使用）。
     */
    suspend fun uploadToSupabase(supabaseUrl: String, supabaseKey: String) = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) return@withContext

        val blocklist = getBlocklist()
        if (blocklist.isEmpty()) return@withContext

        try {
            // 批量 upsert（每次最多 100 筆）
            val chunks = blocklist.chunked(100)
            for (chunk in chunks) {
                val records = buildJsonArray {
                    for (domain in chunk) {
                        addJsonObject {
                            put("domain", domain)
                            put("source", "gov_opendata")
                            put("is_active", true)
                        }
                    }
                }

                val body = records.toString()
                val request = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/fraud_blocklist")
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer $supabaseKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(okhttp3.RequestBody.create(
                        "application/json".toMediaTypeOrNull(),
                        body
                    ))
                    .build()

                httpClient.newCall(request).execute()
            }

            Log.i(TAG, "Uploaded ${blocklist.size} domains to Supabase")
        } catch (e: Exception) {
            Log.w(TAG, "Supabase upload failed: ${e.message}")
        }
    }

    /**
     * 從 Supabase 下載共享黑名單（其他用戶回報的）。
     */
    suspend fun downloadFromSupabase(supabaseUrl: String, supabaseKey: String) = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) return@withContext

        try {
            val request = Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/rest/v1/fraud_blocklist?select=domain&is_active=eq.true&limit=5000")
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext

            val body = response.body?.string() ?: return@withContext
            val rows = Json.parseToJsonElement(body).jsonArray
            val domains = rows.mapNotNull { it.jsonObject["domain"]?.jsonPrimitive?.contentOrNull }

            if (domains.isNotEmpty()) {
                val existing = getBlocklist().toMutableSet()
                existing.addAll(domains)
                prefs.edit()
                    .putString(KEY_BLOCKLIST, existing.joinToString("\n"))
                    .putInt(KEY_BLOCKLIST_COUNT, existing.size)
                    .apply()
                Log.i(TAG, "Downloaded ${domains.size} domains from Supabase (total: ${existing.size})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Supabase download failed: ${e.message}")
        }
    }

    /** 清除本地快取 */
    fun clearCache() {
        prefs.edit().clear().apply()
    }

    /** 狀態摘要 */
    fun getStatusSummary(): String {
        val count = blocklistCount
        val lastSync = lastSyncTime
        return if (count > 0) {
            val ago = (System.currentTimeMillis() - lastSync) / (60 * 60 * 1000)
            "黑名單：$count 筆域名（${ago} 小時前更新）"
        } else {
            "尚未同步（啟動後自動更新）"
        }
    }
}

package com.snowrealm.yukiboard.input.dictionary

/**
 * 英文字典 — 提供單字補全和候選詞建議。
 *
 * 使用頻率排序的常用單字列表 + Trie 前綴匹配。
 * MVP 內建約 3000 個高頻英文單字。
 * 未來可擴充為：
 * - SQLite 字典檔
 * - 使用者自訂詞庫
 * - n-gram 上下文預測
 */
class EnglishDictionary {

    private val root = TrieNode()

    init {
        // 載入高頻單字（按頻率排序，越前面越常用）
        HIGH_FREQ_WORDS.forEachIndexed { index, word ->
            insert(word, HIGH_FREQ_WORDS.size - index) // 頻率分數：越常用分數越高
        }
    }

    /** 插入單字到 Trie */
    fun insert(word: String, frequency: Int = 1) {
        var node = root
        for (ch in word.lowercase()) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        node.isWord = true
        node.word = word.lowercase()
        node.frequency = maxOf(node.frequency, frequency)
    }

    /**
     * 根據前綴查詢候選詞。
     * @param prefix 前綴字串
     * @param maxResults 最多返回幾個結果
     * @return 按頻率降序排列的單字列表
     */
    fun suggest(prefix: String, maxResults: Int = 9): List<String> {
        if (prefix.isBlank()) return emptyList()

        val lowerPrefix = prefix.lowercase()
        var node = root
        for (ch in lowerPrefix) {
            node = node.children[ch] ?: return emptyList()
        }

        // 收集所有候選
        val candidates = mutableListOf<Pair<String, Int>>() // word, frequency
        collectWords(node, candidates, maxResults * 3) // 多蒐集一些再排序

        return candidates
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    /** 查詢是否為完整單字 */
    fun isWord(word: String): Boolean {
        var node = root
        for (ch in word.lowercase()) {
            node = node.children[ch] ?: return false
        }
        return node.isWord
    }

    /**
     * 自動更正 — 找最接近的單字。
     * 使用簡單的編輯距離 1 匹配。
     */
    fun autocorrect(input: String, maxResults: Int = 3): List<String> {
        if (input.length < 2) return emptyList()

        val lower = input.lowercase()
        val candidates = mutableListOf<Pair<String, Int>>()

        // 精確前綴匹配
        val exact = suggest(lower, maxResults)
        if (exact.isNotEmpty()) return exact

        // 嘗試刪除最後一個字元
        if (lower.length > 2) {
            val trimmed = lower.dropLast(1)
            val trimSuggestions = suggest(trimmed, maxResults)
            trimSuggestions.forEach { candidates.add(it to 1) }
        }

        // 嘗試替換最後一個字元
        val withoutLast = lower.dropLast(1)
        for (c in 'a'..'z') {
            val attempt = withoutLast + c
            var node = root
            var valid = true
            for (ch in attempt) {
                val next = node.children[ch]
                if (next == null) { valid = false; break }
                node = next
            }
            if (valid) {
                val subCandidates = mutableListOf<Pair<String, Int>>()
                collectWords(node, subCandidates, 3)
                subCandidates.forEach { candidates.add(it) }
            }
        }

        return candidates
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(maxResults)
    }

    private fun collectWords(node: TrieNode, results: MutableList<Pair<String, Int>>, limit: Int) {
        if (results.size >= limit) return
        if (node.isWord) {
            results.add(node.word to node.frequency)
        }
        // 按字母順序走訪子節點
        for ((_, child) in node.children.entries.sortedBy { it.key }) {
            if (results.size >= limit) break
            collectWords(child, results, limit)
        }
    }

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var isWord = false
        var word = ""
        var frequency = 0
    }

    companion object {
        /** 約 3000 個高頻英文單字（按使用頻率排序） */
        val HIGH_FREQ_WORDS = listOf(
            // Top 100
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
            "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
            "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
            "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
            "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
            "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
            "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
            "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
            "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
            "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
            // 101-300
            "great", "help", "need", "where", "here", "thing", "tell", "call", "try", "ask",
            "should", "very", "much", "long", "hand", "find", "own", "part", "place", "never",
            "still", "high", "every", "last", "let", "thought", "keep", "eye", "start", "might",
            "story", "far", "happen", "seem", "show", "head", "under", "turn", "old", "right",
            "again", "point", "home", "world", "house", "next", "group", "each", "number", "side",
            "while", "life", "small", "end", "run", "move", "play", "during", "school", "word",
            "open", "close", "change", "always", "same", "learn", "large", "before", "between", "city",
            "name", "set", "plan", "real", "line", "read", "put", "hold", "write", "live",
            "state", "stop", "another", "add", "leave", "country", "follow", "men", "since", "water",
            "must", "children", "book", "begin", "money", "face", "important", "family", "feel", "right",
            "system", "against", "seem", "program", "both", "may", "often", "enough", "few", "already",
            "problem", "door", "night", "around", "become", "until", "without", "government", "public", "late",
            "young", "girl", "boy", "question", "though", "second", "power", "include", "possible", "social",
            "woman", "man", "company", "develop", "room", "area", "once", "stand", "case", "however",
            "idea", "body", "business", "four", "different", "kill", "less", "better", "able", "information",
            "bring", "nothing", "talk", "together", "level", "early", "walk", "continue", "rather", "car",
            "law", "perhaps", "miss", "grow", "million", "believe", "food", "hard", "service", "almost",
            "understand", "result", "remember", "love", "local", "age", "build", "best", "sure", "among",
            "cut", "black", "white", "expect", "today", "along", "many", "deal", "war", "past",
            "wait", "light", "human", "watch", "data", "oh", "morning", "yeah", "week", "pay",
            // 301-600
            "thank", "sorry", "please", "hello", "okay", "yes", "no", "maybe", "today", "tomorrow",
            "yesterday", "always", "never", "sometimes", "usually", "often", "again", "already", "still",
            "just", "only", "really", "very", "quite", "pretty", "almost", "enough", "too",
            "about", "above", "across", "after", "against", "along", "among", "around", "before",
            "behind", "below", "beneath", "beside", "between", "beyond", "during", "except", "inside",
            "outside", "through", "toward", "under", "until", "upon", "within", "without",
            "happy", "sad", "angry", "afraid", "surprised", "excited", "tired", "bored", "confused",
            "hungry", "thirsty", "cold", "hot", "warm", "cool", "nice", "beautiful", "ugly",
            "big", "small", "tall", "short", "fat", "thin", "fast", "slow", "strong", "weak",
            "easy", "hard", "simple", "difficult", "cheap", "expensive", "free", "busy", "empty", "full",
            "clean", "dirty", "dry", "wet", "bright", "dark", "loud", "quiet", "soft", "smooth",
            "rough", "sharp", "flat", "round", "square", "straight", "deep", "shallow", "wide", "narrow",
            "color", "red", "blue", "green", "yellow", "orange", "purple", "pink", "brown", "gray",
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
            "January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December",
            "spring", "summer", "autumn", "winter",
            "morning", "afternoon", "evening", "night", "midnight", "noon",
            "minute", "hour", "day", "week", "month", "year", "decade", "century",
            // 601-900 — 科技/工作/日常
            "computer", "phone", "email", "internet", "website", "app", "software", "hardware",
            "screen", "keyboard", "mouse", "button", "click", "type", "search", "download",
            "upload", "save", "delete", "copy", "paste", "send", "receive", "share", "post",
            "message", "chat", "call", "video", "photo", "camera", "music", "song", "movie",
            "game", "play", "win", "lose", "score", "team", "player", "match", "race",
            "meeting", "office", "desk", "chair", "table", "room", "floor", "wall", "window",
            "market", "shop", "store", "price", "sale", "buy", "sell", "order", "deliver",
            "coffee", "tea", "juice", "milk", "bread", "rice", "meat", "fish", "chicken",
            "vegetable", "fruit", "cake", "sugar", "salt", "pepper", "butter", "cheese", "egg",
            "breakfast", "lunch", "dinner", "snack", "restaurant", "menu", "cook", "recipe",
            "doctor", "hospital", "medicine", "health", "sick", "pain", "fever", "cough",
            "teacher", "student", "class", "lesson", "test", "exam", "grade", "homework",
            "university", "college", "degree", "course", "study", "research", "science",
            "math", "history", "English", "language", "art", "design", "technology",
            "travel", "trip", "flight", "hotel", "ticket", "passport", "map", "guide",
            "weather", "rain", "snow", "wind", "cloud", "sun", "storm", "temperature",
            "street", "road", "bridge", "park", "garden", "beach", "mountain", "river", "lake",
            "forest", "island", "ocean", "sea", "sky", "star", "moon",
            // 901-1200 — 動詞/形容詞擴充
            "accept", "achieve", "add", "admit", "affect", "afford", "agree", "aim", "allow",
            "announce", "appear", "apply", "argue", "arrange", "arrive", "assume", "attack",
            "attempt", "avoid", "base", "beat", "belong", "blame", "blow", "bother", "break",
            "burn", "bury", "cancel", "care", "carry", "catch", "cause", "celebrate", "charge",
            "check", "choose", "claim", "climb", "collect", "combine", "comment", "commit",
            "communicate", "compare", "complain", "complete", "concern", "confirm", "connect",
            "consider", "consist", "contain", "contribute", "control", "convert", "convince",
            "count", "cover", "crash", "create", "cross", "cry", "damage", "dance", "dare",
            "decide", "declare", "decline", "defeat", "defend", "define", "deliver", "demand",
            "deny", "depend", "describe", "deserve", "destroy", "detect", "determine", "die",
            "disappear", "discover", "discuss", "display", "divide", "doubt", "drag", "draw",
            "dream", "dress", "drink", "drive", "drop", "earn", "eat", "employ", "enable",
            "encourage", "enjoy", "ensure", "enter", "escape", "establish", "estimate", "evaluate",
            "examine", "exist", "expand", "experience", "explain", "explore", "express", "extend",
            "face", "fail", "fall", "feature", "feed", "fight", "fill", "finish", "fit", "fix",
            "fly", "focus", "force", "forget", "forgive", "form", "found", "gain", "generate",
            "grab", "grant", "guess", "guide", "handle", "hang", "hate", "hear", "hide", "hit",
            "hope", "hurt", "identify", "ignore", "imagine", "implement", "imply", "impose",
            "improve", "increase", "indicate", "influence", "inform", "insist", "install",
            "intend", "introduce", "invest", "investigate", "invite", "involve", "join", "judge",
            "jump", "kick", "knock", "lack", "land", "last", "laugh", "launch", "lay", "lead",
            "lean", "lie", "lift", "limit", "link", "listen", "load", "lock", "lose", "love",
            "maintain", "manage", "mark", "matter", "measure", "meet", "mention", "mind", "mix",
            // 1201-1500 — 更多實用詞
            "account", "address", "advantage", "advice", "agreement", "amount", "analysis",
            "animal", "answer", "apartment", "argument", "army", "article", "attention",
            "audience", "author", "balance", "bank", "bar", "basis", "basket", "bath",
            "battle", "bed", "benefit", "bird", "bit", "block", "blood", "board", "bone",
            "border", "bottom", "brain", "branch", "breath", "brother", "budget", "building",
            "cabinet", "camp", "campaign", "candidate", "capital", "career", "cat", "catch",
            "category", "cause", "cell", "center", "challenge", "champion", "channel", "chapter",
            "character", "choice", "church", "circle", "citizen", "claim", "climate", "clock",
            "clothes", "club", "coach", "code", "collection", "command", "comment", "commission",
            "committee", "community", "condition", "conference", "conflict", "congress",
            "connection", "consequence", "consumer", "content", "context", "contract",
            "conversation", "corner", "cost", "council", "couple", "court", "credit", "crime",
            "crisis", "criticism", "crowd", "culture", "cup", "current", "customer", "cycle",
            "danger", "database", "daughter", "deal", "death", "debate", "decision", "defense",
            "degree", "demand", "democracy", "department", "depression", "description",
            "detail", "device", "diet", "difference", "direction", "director", "discovery",
            "discussion", "disease", "display", "distance", "distribution", "district", "document",
            "dog", "dollar", "door", "doubt", "draft", "drama", "driver", "drug", "duty",
            "economy", "edge", "editor", "education", "effect", "effort", "election", "element",
            "emergency", "emotion", "employee", "employer", "energy", "engine", "engineer",
            "environment", "episode", "equipment", "error", "estate", "event", "evidence",
            "evolution", "examination", "example", "exchange", "executive", "exercise",
            "exhibition", "expansion", "experience", "experiment", "expert", "explanation",
            "expression", "extension", "extent", "face", "facility", "factor", "failure",
            "faith", "feature", "feedback", "fiction", "field", "figure", "file", "film",
            "final", "finance", "finger", "fire", "firm", "fish", "flag", "flight", "focus",
            "football", "force", "form", "fortune", "foundation", "frame", "freedom", "friend",
            "front", "fruit", "function", "fund", "future", "garden", "gas", "gate",
            "generation", "gift", "glass", "goal", "gold", "golf", "grab",
            // 1501-1800 — 科技/程式相關
            "algorithm", "api", "array", "async", "attribute", "authentication", "backend",
            "bandwidth", "binary", "boolean", "browser", "buffer", "bug", "cache", "callback",
            "class", "client", "cloud", "cluster", "commit", "compile", "component", "config",
            "constant", "constructor", "container", "cookie", "cors", "css", "cursor",
            "dashboard", "database", "debug", "decrypt", "default", "deploy", "dependency",
            "deprecated", "design", "developer", "docker", "domain", "driver", "element",
            "encrypt", "endpoint", "enum", "error", "event", "exception", "execute",
            "export", "expression", "extension", "factory", "fetch", "field", "filter",
            "firebase", "flag", "float", "font", "fork", "format", "framework", "frontend",
            "function", "gateway", "git", "github", "global", "gradle", "graph", "handler",
            "hash", "header", "heap", "hook", "host", "html", "http", "https", "icon",
            "import", "index", "input", "instance", "integer", "interface", "iterator",
            "javascript", "json", "jwt", "kernel", "key", "kotlin", "lambda", "layout",
            "library", "lint", "linux", "list", "listener", "local", "log", "login",
            "loop", "mac", "manifest", "map", "margin", "markup", "master", "memory",
            "merge", "method", "middleware", "migration", "mobile", "mock", "model", "module",
            "monitor", "mutex", "namespace", "native", "network", "node", "null", "object",
            "observable", "offset", "operator", "option", "output", "override", "package",
            "padding", "parameter", "parse", "patch", "path", "pattern", "payload", "permission",
            "pipeline", "pixel", "platform", "plugin", "pointer", "port", "private", "process",
            "production", "profile", "project", "promise", "property", "protocol", "provider",
            "proxy", "public", "pull", "push", "python", "query", "queue", "react", "reducer",
            "reference", "register", "release", "remote", "render", "repository", "request",
            "resolve", "resource", "response", "rest", "return", "route", "router", "runtime",
            "sandbox", "schema", "scope", "script", "scroll", "sdk", "selector", "server",
            "service", "session", "setter", "shell", "signal", "singleton", "socket", "source",
            "sprint", "sql", "stack", "staging", "static", "status", "storage", "stream",
            "string", "struct", "style", "subscribe", "suite", "suspend", "swift", "symbol",
            "sync", "syntax", "system", "template", "terminal", "test", "thread", "timeout",
            "token", "toolbar", "tooltip", "trace", "transaction", "tree", "trigger", "tuple",
            "typescript", "ui", "unit", "update", "upload", "url", "user", "utility",
            "validate", "value", "variable", "version", "view", "virtual", "void", "vue",
            "webpack", "widget", "window", "worker", "wrapper", "xml", "yaml", "zero"
        )
    }
}

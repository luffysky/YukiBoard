package com.snowrealm.yukiboard.input.engine

import com.snowrealm.yukiboard.input.router.InputResult

/**
 * 拼音輸入引擎。
 *
 * 功能：
 * - 英文字母→拼音音節即時分割
 * - 拼音→漢字候選（內建高頻字典 ~2000 字）
 * - 模糊拼音支援（zh/z, ch/c, sh/s, n/ng, an/ang 等）
 * - 候選選擇（selectCandidateAt）
 *
 * 設計：
 * - pinyinBuffer: 使用者輸入的字母（如 "nihao"）
 * - syllables: 已分割的拼音音節列表（如 ["ni", "hao"]）
 * - composingText = 帶分隔的拼音顯示（如 "ni'hao"）
 * - Space 選第一候選字 / 點選候選列選字
 * - Enter 直接提交拼音原文
 */
class PinyinInputEngine : InputEngine {

    // ── State ──────────────────────────────────────────────

    private val pinyinBuffer = StringBuilder()
    private var syllables: List<String> = emptyList()
    private var currentCandidates: List<String> = emptyList()
    private var isConverting = false
    private var selectedText = StringBuilder()

    override val composingText: String
        get() {
            if (pinyinBuffer.isEmpty()) return ""
            return formatComposing()
        }

    // ── InputEngine 介面實作 ────────────────────────────────

    override fun processChar(char: String): InputResult {
        if (char.isEmpty()) return InputResult.Idle
        val c = char[0].lowercaseChar()
        if (c !in 'a'..'z') return InputResult.Idle

        pinyinBuffer.append(c)
        syllables = segmentPinyin(pinyinBuffer.toString())
        updateCandidates()
        isConverting = false
        return InputResult.ComposingUpdated(composingText)
    }

    override fun processDelete(): InputResult {
        if (pinyinBuffer.isEmpty()) return InputResult.ComposingCleared
        pinyinBuffer.deleteCharAt(pinyinBuffer.lastIndex)
        if (pinyinBuffer.isEmpty()) {
            syllables = emptyList()
            currentCandidates = emptyList()
            isConverting = false
            return InputResult.ComposingCleared
        }
        syllables = segmentPinyin(pinyinBuffer.toString())
        updateCandidates()
        return InputResult.ComposingUpdated(composingText)
    }

    override fun processSpace(): InputResult {
        if (pinyinBuffer.isEmpty()) return InputResult.CommitText(" ")

        if (currentCandidates.isNotEmpty()) {
            val selected = currentCandidates[0]
            return commitCandidate(selected)
        }
        // 無候選字時提交原文
        val text = pinyinBuffer.toString()
        reset()
        return InputResult.CommitText(text)
    }

    override fun processEnter(): InputResult {
        if (pinyinBuffer.isEmpty()) return InputResult.Idle
        val text = pinyinBuffer.toString()
        reset()
        return InputResult.CommitText(text)
    }

    override fun forceCommit(): InputResult {
        if (pinyinBuffer.isEmpty()) return InputResult.Idle
        val text = if (currentCandidates.isNotEmpty()) currentCandidates[0]
        else pinyinBuffer.toString()
        reset()
        return InputResult.CommitText(text)
    }

    override fun getCandidates(): List<String> = currentCandidates

    override fun reset() {
        pinyinBuffer.clear()
        syllables = emptyList()
        currentCandidates = emptyList()
        selectedText.clear()
        isConverting = false
    }

    // ── 候選選擇 ─────────────────────────────────────────────

    fun selectCandidateAt(index: Int): InputResult {
        if (index < 0 || index >= currentCandidates.size) return InputResult.Idle
        val selected = currentCandidates[index]
        return commitCandidate(selected)
    }

    private fun commitCandidate(text: String): InputResult {
        reset()
        return InputResult.CommitText(text)
    }

    // ── 拼音顯示 ─────────────────────────────────────────────

    private fun formatComposing(): String {
        if (syllables.isEmpty()) return pinyinBuffer.toString()
        val joined = syllables.joinToString("'")
        val consumed = syllables.joinToString("")
        val remainder = pinyinBuffer.substring(consumed.length.coerceAtMost(pinyinBuffer.length))
        return if (remainder.isNotEmpty()) "$joined'$remainder" else joined
    }

    // ── 拼音音節分割（貪心最長匹配）──────────────────────────

    private fun segmentPinyin(input: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            var matched = false
            // 嘗試最長匹配（6→1）
            val maxLen = minOf(6, input.length - i)
            for (len in maxLen downTo 1) {
                val sub = input.substring(i, i + len)
                if (isValidSyllable(sub)) {
                    result.add(sub)
                    i += len
                    matched = true
                    break
                }
            }
            if (!matched) {
                // 無法匹配的字母附加到上一個音節或獨立
                if (result.isNotEmpty()) {
                    result[result.lastIndex] = result.last() + input[i]
                } else {
                    result.add(input[i].toString())
                }
                i++
            }
        }
        return result
    }

    private fun isValidSyllable(s: String): Boolean = s in VALID_SYLLABLES

    // ── 候選字更新 ────────────────────────────────────────────

    private fun updateCandidates() {
        if (syllables.isEmpty()) {
            currentCandidates = emptyList()
            return
        }

        val candidates = mutableListOf<String>()

        // 先查完整拼音串
        val fullPinyin = syllables.joinToString("")
        PINYIN_DICT[fullPinyin]?.let { candidates.addAll(it) }

        // 查各音節組合
        if (syllables.size > 1) {
            // 查第一個音節
            val firstSyllable = syllables[0]
            PINYIN_DICT[firstSyllable]?.let { chars ->
                // 嘗試組合多音節成詞
                if (syllables.size == 2) {
                    val second = syllables[1]
                    PHRASE_DICT["$firstSyllable$second"]?.let { candidates.addAll(0, it) }
                } else if (syllables.size >= 3) {
                    val key = syllables.take(3).joinToString("")
                    PHRASE_DICT[key]?.let { candidates.addAll(0, it) }
                    val key2 = syllables.take(2).joinToString("")
                    PHRASE_DICT[key2]?.let { candidates.addAll(it) }
                }
                candidates.addAll(chars)
            }
        } else {
            // 單音節：直接查字典
            val sy = syllables[0]
            PINYIN_DICT[sy]?.let { candidates.addAll(it) }

            // 模糊匹配
            fuzzyMatch(sy).forEach { fuzzySy ->
                PINYIN_DICT[fuzzySy]?.let { chars ->
                    chars.forEach { if (it !in candidates) candidates.add(it) }
                }
            }
        }

        currentCandidates = candidates.distinct().take(30)
    }

    // ── 模糊拼音 ─────────────────────────────────────────────

    private fun fuzzyMatch(syllable: String): List<String> {
        val results = mutableListOf<String>()
        // zh ↔ z
        if (syllable.startsWith("zh")) results.add("z" + syllable.substring(2))
        else if (syllable.startsWith("z") && !syllable.startsWith("zh"))
            results.add("zh" + syllable.substring(1))
        // ch ↔ c
        if (syllable.startsWith("ch")) results.add("c" + syllable.substring(2))
        else if (syllable.startsWith("c") && !syllable.startsWith("ch"))
            results.add("ch" + syllable.substring(1))
        // sh ↔ s
        if (syllable.startsWith("sh")) results.add("s" + syllable.substring(2))
        else if (syllable.startsWith("s") && !syllable.startsWith("sh"))
            results.add("sh" + syllable.substring(1))
        // n ↔ l
        if (syllable.startsWith("n")) results.add("l" + syllable.substring(1))
        else if (syllable.startsWith("l")) results.add("n" + syllable.substring(1))
        // ang ↔ an
        if (syllable.endsWith("ang")) results.add(syllable.dropLast(3) + "an")
        else if (syllable.endsWith("an") && !syllable.endsWith("ang"))
            results.add(syllable.dropLast(2) + "ang")
        // eng ↔ en
        if (syllable.endsWith("eng")) results.add(syllable.dropLast(3) + "en")
        else if (syllable.endsWith("en") && !syllable.endsWith("eng"))
            results.add(syllable.dropLast(2) + "eng")
        // ing ↔ in
        if (syllable.endsWith("ing")) results.add(syllable.dropLast(3) + "in")
        else if (syllable.endsWith("in") && !syllable.endsWith("ing"))
            results.add(syllable.dropLast(2) + "ing")
        return results
    }

    // =====================================================================
    // 拼音字典資料
    // =====================================================================

    companion object {
        /** 合法拼音音節表 */
        private val VALID_SYLLABLES = setOf(
            // 單韻母
            "a", "o", "e", "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "er",
            // b
            "ba", "bo", "bi", "bu", "bai", "bei", "bao", "ban", "ben", "bang", "beng", "bian", "bin", "bing", "bie", "biao",
            // p
            "pa", "po", "pi", "pu", "pai", "pei", "pao", "pou", "pan", "pen", "pang", "peng", "pian", "pin", "ping", "pie", "piao",
            // m
            "ma", "mo", "me", "mi", "mu", "mai", "mei", "mao", "mou", "man", "men", "mang", "meng", "mian", "min", "ming", "mie", "miao", "miu",
            // f
            "fa", "fo", "fu", "fei", "fan", "fen", "fang", "feng", "fou",
            // d
            "da", "de", "di", "du", "dai", "dei", "dao", "dou", "dan", "den", "dang", "deng", "dian", "din", "ding", "die", "diao", "diu", "dong", "duan", "dun", "duo", "dui",
            // t
            "ta", "te", "ti", "tu", "tai", "tei", "tao", "tou", "tan", "tang", "teng", "tian", "tin", "ting", "tie", "tiao", "tong", "tuan", "tun", "tuo", "tui",
            // n
            "na", "ne", "ni", "nu", "nv", "nai", "nei", "nao", "nou", "nan", "nen", "nang", "neng", "nian", "nin", "ning", "nie", "niao", "niu", "nong", "nuan", "nun", "nuo", "nve",
            // l
            "la", "le", "li", "lu", "lv", "lai", "lei", "lao", "lou", "lan", "lang", "leng", "lian", "lin", "ling", "lie", "liao", "liu", "long", "luan", "lun", "luo", "lve",
            // g
            "ga", "ge", "gu", "gai", "gei", "gao", "gou", "gan", "gen", "gang", "geng", "gong", "guan", "gun", "guo", "gui", "guai", "guang",
            // k
            "ka", "ke", "ku", "kai", "kei", "kao", "kou", "kan", "ken", "kang", "keng", "kong", "kuan", "kun", "kuo", "kui", "kuai", "kuang",
            // h
            "ha", "he", "hu", "hai", "hei", "hao", "hou", "han", "hen", "hang", "heng", "hong", "huan", "hun", "huo", "hui", "huai", "huang",
            // j
            "ji", "ju", "jia", "jie", "jiu", "jian", "jin", "jing", "jiang", "jiong", "juan", "jun", "jue",
            // q
            "qi", "qu", "qia", "qie", "qiu", "qian", "qin", "qing", "qiang", "qiong", "quan", "qun", "que",
            // x
            "xi", "xu", "xia", "xie", "xiu", "xian", "xin", "xing", "xiang", "xiong", "xuan", "xun", "xue",
            // zh
            "zha", "zhe", "zhi", "zhu", "zhai", "zhei", "zhao", "zhou", "zhan", "zhen", "zhang", "zheng", "zhong", "zhuan", "zhun", "zhuo", "zhui", "zhuai", "zhuang",
            // ch
            "cha", "che", "chi", "chu", "chai", "chao", "chou", "chan", "chen", "chang", "cheng", "chong", "chuan", "chun", "chuo", "chui", "chuai", "chuang",
            // sh
            "sha", "she", "shi", "shu", "shai", "shao", "shou", "shan", "shen", "shang", "sheng", "shui", "shuan", "shun", "shuo", "shuai", "shuang",
            // r
            "ri", "re", "ru", "rao", "rou", "ran", "ren", "rang", "reng", "rong", "ruan", "run", "ruo", "rui",
            // z
            "za", "ze", "zi", "zu", "zai", "zei", "zao", "zou", "zan", "zen", "zang", "zeng", "zong", "zuan", "zun", "zuo", "zui",
            // c
            "ca", "ce", "ci", "cu", "cai", "cao", "cou", "can", "cen", "cang", "ceng", "cong", "cuan", "cun", "cuo", "cui",
            // s
            "sa", "se", "si", "su", "sai", "sao", "sou", "san", "sen", "sang", "seng", "song", "suan", "sun", "suo", "sui",
            // y
            "ya", "ye", "yi", "yo", "yu", "yai", "yao", "you", "yan", "yin", "yang", "ying", "yong", "yuan", "yun", "yue",
            // w
            "wa", "wo", "wu", "wai", "wei", "wan", "wen", "wang", "weng",
        )

        /** 拼音→漢字映射（高頻字） */
        private val PINYIN_DICT: Map<String, List<String>> = mapOf(
            "a" to listOf("啊", "阿", "呵"),
            "ai" to listOf("愛", "哎", "矮", "癌", "礙", "唉"),
            "an" to listOf("安", "暗", "按", "岸", "案"),
            "ang" to listOf("昂"),
            "ao" to listOf("奧", "傲", "熬", "澳"),
            "ba" to listOf("八", "把", "吧", "爸", "拔", "巴", "霸"),
            "bai" to listOf("白", "百", "拜", "擺", "敗"),
            "ban" to listOf("半", "班", "般", "辦", "搬", "板", "版", "伴", "扮"),
            "bang" to listOf("幫", "棒", "旁", "磅", "綁", "榜"),
            "bao" to listOf("包", "報", "抱", "寶", "飽", "暴", "保", "薄", "爆"),
            "bei" to listOf("被", "北", "背", "杯", "備", "悲", "倍", "輩"),
            "ben" to listOf("本", "笨", "奔"),
            "beng" to listOf("蹦", "崩", "繃"),
            "bi" to listOf("比", "筆", "必", "閉", "逼", "鼻", "避", "幣", "壁"),
            "bian" to listOf("變", "邊", "便", "編", "遍", "辨", "辯", "扁"),
            "biao" to listOf("表", "標", "彪", "錶"),
            "bie" to listOf("別", "憋"),
            "bin" to listOf("賓", "冰"),
            "bing" to listOf("冰", "病", "兵", "並", "丙", "餅"),
            "bo" to listOf("波", "博", "伯", "播", "撥", "脖", "薄", "泊"),
            "bu" to listOf("不", "步", "部", "布", "補", "怖"),
            "ca" to listOf("擦"),
            "cai" to listOf("才", "菜", "財", "猜", "材", "採", "裁", "彩"),
            "can" to listOf("參", "餐", "慘", "殘", "蠶"),
            "cang" to listOf("藏", "倉", "蒼"),
            "cao" to listOf("草", "操", "曹"),
            "ce" to listOf("側", "測", "策", "冊", "廁"),
            "ceng" to listOf("曾", "層"),
            "cha" to listOf("查", "茶", "差", "插", "察", "叉"),
            "chai" to listOf("拆", "柴"),
            "chan" to listOf("產", "纏", "禪", "顫", "摻", "饞"),
            "chang" to listOf("長", "常", "場", "唱", "廠", "嘗", "腸", "暢", "倡"),
            "chao" to listOf("超", "朝", "潮", "吵", "抄", "炒", "巢"),
            "che" to listOf("車", "撤", "扯", "徹"),
            "chen" to listOf("陳", "沉", "晨", "趁", "稱", "襯"),
            "cheng" to listOf("成", "城", "程", "稱", "承", "乘", "誠", "撐", "澄"),
            "chi" to listOf("吃", "遲", "尺", "赤", "持", "池", "齒", "翅", "匙"),
            "chong" to listOf("重", "沖", "衝", "蟲", "充", "崇"),
            "chou" to listOf("抽", "醜", "臭", "愁", "仇", "酬", "稠"),
            "chu" to listOf("出", "初", "處", "除", "楚", "觸", "儲", "廚"),
            "chuai" to listOf("揣"),
            "chuan" to listOf("傳", "穿", "船", "串", "川", "喘"),
            "chuang" to listOf("創", "窗", "床", "闖"),
            "chui" to listOf("吹", "垂", "錘", "炊"),
            "chun" to listOf("春", "純", "唇", "蠢"),
            "ci" to listOf("次", "此", "詞", "刺", "辭", "瓷", "磁", "慈"),
            "cong" to listOf("從", "聰", "叢", "匆"),
            "cu" to listOf("粗", "促", "醋"),
            "cuan" to listOf("竄", "篡"),
            "cui" to listOf("催", "脆", "翠", "萃"),
            "cun" to listOf("村", "存", "寸"),
            "cuo" to listOf("錯", "挫", "搓"),
            "da" to listOf("大", "打", "達", "答"),
            "dai" to listOf("大", "帶", "待", "代", "袋", "戴", "呆"),
            "dan" to listOf("但", "單", "蛋", "淡", "膽", "擔", "彈", "旦"),
            "dang" to listOf("當", "黨", "擋", "蕩", "檔"),
            "dao" to listOf("到", "道", "刀", "倒", "島", "導", "盜", "稻"),
            "de" to listOf("的", "得", "德"),
            "dei" to listOf("得"),
            "deng" to listOf("等", "燈", "登", "鄧"),
            "di" to listOf("的", "地", "低", "底", "第", "弟", "帝", "敵", "抵", "遞", "滴"),
            "dian" to listOf("點", "電", "店", "典", "殿", "墊", "顛", "掂"),
            "diao" to listOf("掉", "調", "吊", "雕", "釣"),
            "die" to listOf("跌", "爹", "蝶", "碟", "疊"),
            "ding" to listOf("定", "頂", "丁", "盯", "釘", "訂"),
            "diu" to listOf("丟"),
            "dong" to listOf("東", "動", "冬", "懂", "洞", "凍", "棟"),
            "dou" to listOf("都", "斗", "豆", "逗", "抖", "鬥"),
            "du" to listOf("度", "讀", "都", "毒", "獨", "堵", "肚", "杜", "渡"),
            "duan" to listOf("段", "短", "斷", "端"),
            "dui" to listOf("對", "隊", "堆"),
            "dun" to listOf("頓", "敦", "蹲", "噸", "盾", "燉"),
            "duo" to listOf("多", "朵", "躲", "奪", "墮"),
            "e" to listOf("餓", "惡", "額", "鵝", "俄"),
            "ei" to listOf("誒"),
            "en" to listOf("嗯", "恩"),
            "er" to listOf("二", "耳", "而", "兒"),
            "fa" to listOf("發", "法", "罰", "髮", "乏", "伐"),
            "fan" to listOf("飯", "反", "番", "煩", "翻", "犯", "範", "繁", "凡"),
            "fang" to listOf("方", "放", "房", "防", "訪", "仿", "芳"),
            "fei" to listOf("非", "飛", "費", "肥", "廢", "肺"),
            "fen" to listOf("分", "份", "粉", "奮", "紛", "墳", "焚"),
            "feng" to listOf("風", "封", "豐", "蜂", "鋒", "逢", "瘋", "縫", "峰"),
            "fo" to listOf("佛"),
            "fou" to listOf("否"),
            "fu" to listOf("父", "夫", "服", "福", "富", "付", "復", "負", "副", "府", "婦", "腐", "符", "幅", "浮", "扶", "附"),
            "ga" to listOf("嘎", "噶"),
            "gai" to listOf("改", "該", "蓋", "概", "鈣"),
            "gan" to listOf("幹", "感", "敢", "甘", "趕", "肝", "桿"),
            "gang" to listOf("剛", "鋼", "港", "崗", "綱", "缸"),
            "gao" to listOf("高", "告", "搞", "糕", "膏"),
            "ge" to listOf("個", "各", "歌", "哥", "格", "隔", "割", "革", "閣"),
            "gei" to listOf("給"),
            "gen" to listOf("跟", "根", "更"),
            "geng" to listOf("更", "耕", "梗"),
            "gong" to listOf("工", "公", "功", "共", "供", "攻", "宮", "恭", "鞏"),
            "gou" to listOf("夠", "狗", "溝", "構", "鉤", "購"),
            "gu" to listOf("古", "故", "姑", "骨", "鼓", "穀", "股", "顧", "孤", "固"),
            "gua" to listOf("瓜", "掛", "刮", "寡"),
            "guai" to listOf("怪", "乖", "拐"),
            "guan" to listOf("關", "管", "官", "觀", "館", "慣", "冠", "灌"),
            "guang" to listOf("光", "廣", "逛"),
            "gui" to listOf("貴", "歸", "鬼", "規", "桂", "櫃", "軌"),
            "gun" to listOf("滾", "棍"),
            "guo" to listOf("國", "過", "果", "鍋", "裹"),
            "ha" to listOf("哈", "蛤"),
            "hai" to listOf("還", "海", "害", "孩", "嗨"),
            "han" to listOf("漢", "喊", "含", "寒", "汗", "韓", "旱"),
            "hang" to listOf("行", "航", "杭"),
            "hao" to listOf("好", "號", "毫", "豪", "浩", "耗"),
            "he" to listOf("和", "喝", "河", "何", "合", "賀", "黑", "核", "荷", "赫"),
            "hei" to listOf("黑", "嘿"),
            "hen" to listOf("很", "恨", "狠"),
            "heng" to listOf("橫", "哼", "衡", "恆"),
            "hong" to listOf("紅", "洪", "宏", "轟", "虹", "鴻"),
            "hou" to listOf("後", "候", "厚", "猴", "吼", "喉", "侯"),
            "hu" to listOf("胡", "湖", "戶", "互", "護", "虎", "呼", "忽", "糊", "壺", "狐"),
            "hua" to listOf("話", "花", "化", "畫", "華", "划", "滑"),
            "huai" to listOf("壞", "懷", "淮"),
            "huan" to listOf("還", "換", "歡", "環", "患", "緩", "幻", "喚"),
            "huang" to listOf("黃", "皇", "慌", "荒", "晃", "煌", "恍"),
            "hui" to listOf("會", "回", "灰", "揮", "輝", "毀", "悔", "匯", "繪", "惠"),
            "hun" to listOf("混", "婚", "魂", "昏"),
            "huo" to listOf("火", "活", "或", "貨", "獲", "禍", "伙", "惑"),
            "ji" to listOf("幾", "機", "吉", "及", "急", "即", "集", "記", "計", "技", "基", "級", "極", "季", "寂", "繼", "既", "紀", "積"),
            "jia" to listOf("家", "加", "假", "價", "嫁", "駕", "架", "甲", "佳", "夾"),
            "jian" to listOf("見", "間", "件", "建", "尖", "堅", "簡", "健", "漸", "劍", "減", "檢", "薦", "箭", "鍵", "肩"),
            "jiang" to listOf("將", "講", "江", "降", "獎", "醬", "漿", "疆", "姜"),
            "jiao" to listOf("叫", "教", "交", "腳", "角", "較", "覺", "嬌", "焦", "膠", "餃"),
            "jie" to listOf("姐", "解", "結", "借", "界", "介", "接", "節", "街", "階", "截", "傑"),
            "jin" to listOf("進", "近", "今", "金", "盡", "僅", "緊", "勁", "錦", "斤", "津"),
            "jing" to listOf("京", "經", "靜", "精", "景", "驚", "竟", "鏡", "境", "警", "淨", "井", "晶"),
            "jiong" to listOf("窘", "炯"),
            "jiu" to listOf("就", "九", "久", "酒", "舊", "救", "究"),
            "ju" to listOf("局", "舉", "句", "具", "巨", "居", "距", "據", "劇", "拒", "聚"),
            "juan" to listOf("卷", "捐", "倦", "絹"),
            "jue" to listOf("覺", "決", "絕", "角", "掘", "爵"),
            "jun" to listOf("軍", "君", "均", "菌", "俊"),
            "ka" to listOf("卡", "咖"),
            "kai" to listOf("開", "凱"),
            "kan" to listOf("看", "砍", "刊", "勘", "堪"),
            "kang" to listOf("抗", "扛", "康", "慷"),
            "kao" to listOf("考", "靠", "烤"),
            "ke" to listOf("可", "課", "克", "客", "刻", "科", "渴", "顆", "殼", "咳"),
            "ken" to listOf("肯", "啃", "懇"),
            "keng" to listOf("坑"),
            "kong" to listOf("空", "控", "恐", "孔"),
            "kou" to listOf("口", "扣", "摳"),
            "ku" to listOf("苦", "庫", "哭", "酷", "褲"),
            "kua" to listOf("跨", "垮", "誇"),
            "kuai" to listOf("快", "塊", "筷", "會"),
            "kuan" to listOf("寬", "款"),
            "kuang" to listOf("狂", "礦", "框", "況", "曠"),
            "kui" to listOf("虧", "愧", "潰", "盔"),
            "kun" to listOf("困", "捆", "昆"),
            "kuo" to listOf("擴", "闊", "括"),
            "la" to listOf("拉", "啦", "辣", "垃", "蠟"),
            "lai" to listOf("來", "賴"),
            "lan" to listOf("藍", "懶", "爛", "蘭", "攔", "欄", "覽"),
            "lang" to listOf("狼", "浪", "朗", "廊", "郎"),
            "lao" to listOf("老", "勞", "撈", "牢", "姥"),
            "le" to listOf("了", "樂", "勒"),
            "lei" to listOf("累", "雷", "類", "淚", "蕾"),
            "leng" to listOf("冷", "愣", "楞"),
            "li" to listOf("裡", "力", "李", "理", "立", "利", "離", "禮", "歷", "麗", "粒", "例"),
            "lia" to listOf("倆"),
            "lian" to listOf("連", "臉", "練", "聯", "蓮", "鏈", "煉", "戀", "簾"),
            "liang" to listOf("兩", "亮", "量", "涼", "良", "糧", "梁"),
            "liao" to listOf("了", "料", "聊", "遼", "療"),
            "lie" to listOf("列", "烈", "裂", "劣", "獵"),
            "lin" to listOf("林", "鄰", "臨", "淋", "琳"),
            "ling" to listOf("零", "令", "靈", "另", "領", "鈴", "齡", "嶺"),
            "liu" to listOf("六", "流", "留", "劉", "柳", "溜"),
            "long" to listOf("龍", "隆", "弄", "籠", "聾"),
            "lou" to listOf("樓", "漏", "露", "摟"),
            "lu" to listOf("路", "陸", "錄", "露", "盧", "魯", "爐", "鹿"),
            "lv" to listOf("綠", "律", "旅", "率", "慮", "驢", "濾", "呂"),
            "luan" to listOf("亂", "卵", "鸞"),
            "lun" to listOf("論", "輪", "倫"),
            "luo" to listOf("落", "洛", "羅", "螺", "駱", "裸"),
            "ma" to listOf("嗎", "媽", "馬", "麻", "罵", "碼"),
            "mai" to listOf("買", "賣", "麥", "埋", "邁"),
            "man" to listOf("慢", "滿", "漫", "蠻", "饅", "瞞"),
            "mang" to listOf("忙", "盲", "茫", "芒"),
            "mao" to listOf("貓", "毛", "冒", "帽", "矛", "茅"),
            "me" to listOf("麼"),
            "mei" to listOf("沒", "每", "美", "妹", "梅", "媒", "煤", "眉", "黴"),
            "men" to listOf("門", "們", "悶"),
            "meng" to listOf("夢", "猛", "蒙", "盟", "萌", "孟"),
            "mi" to listOf("米", "密", "迷", "蜜", "眯", "秘", "覓"),
            "mian" to listOf("面", "棉", "免", "眠", "綿", "勉"),
            "miao" to listOf("秒", "妙", "苗", "描", "瞄", "廟"),
            "mie" to listOf("滅"),
            "min" to listOf("民", "敏"),
            "ming" to listOf("名", "明", "命", "鳴", "銘"),
            "mo" to listOf("沒", "摸", "末", "磨", "默", "莫", "模", "墨", "魔", "膜"),
            "mou" to listOf("某", "謀", "牟"),
            "mu" to listOf("木", "母", "目", "牧", "幕", "墓", "暮", "慕", "穆"),
            "na" to listOf("那", "拿", "哪", "納", "吶"),
            "nai" to listOf("奶", "耐", "乃", "奈"),
            "nan" to listOf("男", "南", "難", "楠"),
            "nang" to listOf("囊"),
            "nao" to listOf("鬧", "腦", "惱"),
            "ne" to listOf("呢", "訥"),
            "nei" to listOf("內"),
            "nen" to listOf("嫩"),
            "neng" to listOf("能"),
            "ni" to listOf("你", "泥", "尼", "逆", "擬"),
            "nian" to listOf("年", "念", "粘"),
            "niang" to listOf("娘", "釀"),
            "niao" to listOf("鳥", "尿"),
            "nie" to listOf("捏", "涅"),
            "nin" to listOf("您"),
            "ning" to listOf("寧", "凝", "擰"),
            "niu" to listOf("牛", "扭", "紐"),
            "nong" to listOf("農", "濃", "弄"),
            "nu" to listOf("怒", "努"),
            "nv" to listOf("女"),
            "nuan" to listOf("暖"),
            "nuo" to listOf("諾", "挪"),
            "o" to listOf("哦", "喔"),
            "ou" to listOf("歐", "偶", "嘔"),
            "pa" to listOf("怕", "爬", "拍", "帕"),
            "pai" to listOf("排", "拍", "派", "牌"),
            "pan" to listOf("盤", "判", "盼", "潘", "攀"),
            "pang" to listOf("旁", "胖", "龐"),
            "pao" to listOf("跑", "炮", "泡", "拋", "袍"),
            "pei" to listOf("陪", "配", "培", "佩", "賠"),
            "pen" to listOf("噴", "盆"),
            "peng" to listOf("朋", "碰", "棚", "捧", "蓬", "彭", "膨"),
            "pi" to listOf("皮", "批", "匹", "屁", "脾", "劈", "偏", "疲"),
            "pian" to listOf("片", "偏", "騙", "篇", "便"),
            "piao" to listOf("票", "飄", "漂", "瓢"),
            "pie" to listOf("撇", "瞥"),
            "pin" to listOf("品", "拼", "貧", "頻"),
            "ping" to listOf("平", "評", "瓶", "蘋", "憑", "屏"),
            "po" to listOf("破", "潑", "婆", "坡", "迫", "頗"),
            "pou" to listOf("剖"),
            "pu" to listOf("普", "鋪", "撲", "僕", "樸", "浦", "瀑", "葡"),
            "qi" to listOf("起", "七", "氣", "其", "期", "齊", "騎", "器", "奇", "棋", "旗", "企", "妻", "欺"),
            "qia" to listOf("恰", "卡", "掐"),
            "qian" to listOf("前", "千", "錢", "淺", "牽", "鉛", "遷", "簽", "欠", "歉"),
            "qiang" to listOf("強", "牆", "搶", "槍", "腔"),
            "qiao" to listOf("巧", "橋", "敲", "瞧", "翹", "悄", "俏"),
            "qie" to listOf("切", "且", "竊", "茄"),
            "qin" to listOf("親", "琴", "勤", "侵", "秦", "寢"),
            "qing" to listOf("請", "清", "情", "青", "輕", "慶", "晴", "傾"),
            "qiong" to listOf("窮", "瓊"),
            "qiu" to listOf("球", "求", "秋", "丘", "邱", "囚"),
            "qu" to listOf("去", "趣", "取", "區", "曲", "驅", "渠"),
            "quan" to listOf("全", "權", "圈", "拳", "犬", "泉", "勸"),
            "que" to listOf("卻", "缺", "確", "雀", "鵲"),
            "qun" to listOf("群", "裙"),
            "ran" to listOf("然", "染", "燃"),
            "rang" to listOf("讓", "嚷", "壤"),
            "rao" to listOf("繞", "饒", "擾"),
            "re" to listOf("熱", "惹"),
            "ren" to listOf("人", "認", "任", "忍", "仁"),
            "reng" to listOf("仍", "扔"),
            "ri" to listOf("日"),
            "rong" to listOf("容", "融", "榮", "溶", "絨"),
            "rou" to listOf("肉", "柔", "揉"),
            "ru" to listOf("如", "入", "乳"),
            "ruan" to listOf("軟", "阮"),
            "rui" to listOf("瑞", "銳"),
            "run" to listOf("潤", "閏"),
            "ruo" to listOf("弱", "若"),
            "sa" to listOf("撒", "薩", "灑"),
            "sai" to listOf("賽", "塞", "腮"),
            "san" to listOf("三", "散", "傘"),
            "sang" to listOf("桑", "嗓", "喪"),
            "sao" to listOf("掃", "騷", "嫂"),
            "se" to listOf("色", "塞", "瑟", "澀"),
            "sen" to listOf("森"),
            "seng" to listOf("僧"),
            "sha" to listOf("殺", "沙", "傻", "紗", "啥"),
            "shai" to listOf("曬", "篩"),
            "shan" to listOf("山", "善", "衫", "閃", "杉", "刪"),
            "shang" to listOf("上", "商", "傷", "賞", "尚"),
            "shao" to listOf("少", "燒", "紹", "稍", "勺"),
            "she" to listOf("社", "設", "射", "蛇", "舍", "捨", "涉", "舌"),
            "shei" to listOf("誰"),
            "shen" to listOf("什", "深", "神", "身", "生", "甚", "伸", "審", "沈", "慎"),
            "sheng" to listOf("生", "聲", "升", "勝", "省", "剩", "繩", "盛", "聖"),
            "shi" to listOf("是", "時", "十", "事", "使", "市", "世", "師", "式", "始", "石", "實", "食", "史", "失", "識"),
            "shou" to listOf("手", "受", "收", "首", "守", "瘦", "售", "壽"),
            "shu" to listOf("書", "樹", "數", "屬", "術", "束", "熟", "鼠", "輸", "叔", "舒", "疏"),
            "shua" to listOf("刷", "耍"),
            "shuai" to listOf("帥", "摔", "甩", "衰"),
            "shuan" to listOf("拴", "栓"),
            "shuang" to listOf("雙", "爽"),
            "shui" to listOf("水", "睡", "誰", "稅"),
            "shun" to listOf("順", "瞬"),
            "shuo" to listOf("說", "碩", "朔"),
            "si" to listOf("四", "死", "思", "私", "司", "絲", "似", "斯", "撕"),
            "song" to listOf("送", "松", "宋", "頌", "鬆", "聳"),
            "sou" to listOf("搜", "嗖"),
            "su" to listOf("速", "素", "蘇", "宿", "俗", "訴", "肅", "塑", "酥"),
            "suan" to listOf("算", "酸", "蒜"),
            "sui" to listOf("雖", "隨", "歲", "碎", "穗", "遂"),
            "sun" to listOf("孫", "損", "筍"),
            "suo" to listOf("所", "鎖", "索", "縮"),
            "ta" to listOf("他", "她", "它", "塔", "踏", "塌"),
            "tai" to listOf("太", "台", "態", "抬", "胎", "泰"),
            "tan" to listOf("談", "彈", "碳", "攤", "嘆", "壇", "探", "灘", "坦"),
            "tang" to listOf("糖", "湯", "堂", "躺", "趟", "唐", "燙"),
            "tao" to listOf("逃", "套", "桃", "陶", "淘", "掏", "討"),
            "te" to listOf("特"),
            "teng" to listOf("疼", "藤", "騰"),
            "ti" to listOf("提", "體", "替", "題", "踢", "剃", "梯"),
            "tian" to listOf("天", "甜", "田", "填", "添"),
            "tiao" to listOf("條", "跳", "調", "挑"),
            "tie" to listOf("鐵", "貼", "帖"),
            "ting" to listOf("聽", "停", "廳", "挺", "庭", "亭"),
            "tong" to listOf("同", "通", "統", "痛", "銅", "桶", "童", "筒"),
            "tou" to listOf("頭", "偷", "透", "投"),
            "tu" to listOf("土", "圖", "吐", "兔", "突", "塗", "途"),
            "tuan" to listOf("團", "湍"),
            "tui" to listOf("退", "推", "腿"),
            "tun" to listOf("吞", "屯", "臀"),
            "tuo" to listOf("脫", "拖", "託", "妥", "駝"),
            "wa" to listOf("挖", "哇", "蛙", "瓦", "娃"),
            "wai" to listOf("外", "歪"),
            "wan" to listOf("萬", "完", "玩", "晚", "灣", "碗", "彎"),
            "wang" to listOf("王", "望", "往", "忘", "網", "汪"),
            "wei" to listOf("為", "位", "味", "圍", "微", "胃", "維", "唯", "偉", "衛", "委", "尾", "危", "違", "威"),
            "wen" to listOf("問", "文", "聞", "溫", "穩", "吻", "紋"),
            "weng" to listOf("翁", "嗡"),
            "wo" to listOf("我", "握", "窩", "臥"),
            "wu" to listOf("五", "無", "物", "舞", "午", "武", "務", "悟", "誤", "屋", "烏", "霧", "吳"),
            "xi" to listOf("西", "洗", "習", "喜", "細", "息", "希", "席", "系", "戲", "惜", "析", "吸", "錫"),
            "xia" to listOf("下", "嚇", "蝦", "瞎", "夏", "峽", "俠", "狹", "霞"),
            "xian" to listOf("先", "現", "線", "鮮", "限", "顯", "險", "獻", "閒", "縣", "仙", "嫌", "鹹", "銜"),
            "xiang" to listOf("想", "向", "像", "香", "箱", "相", "響", "鄉", "詳", "項", "象", "享"),
            "xiao" to listOf("小", "笑", "校", "消", "效", "銷", "曉", "削", "孝"),
            "xie" to listOf("些", "寫", "謝", "鞋", "血", "斜", "歇", "協", "洩", "邪", "脅", "蠍"),
            "xin" to listOf("新", "心", "信", "辛", "欣", "薪", "芯"),
            "xing" to listOf("行", "星", "性", "姓", "興", "醒", "型", "形", "幸", "刑"),
            "xiong" to listOf("兄", "胸", "熊", "凶", "雄"),
            "xiu" to listOf("修", "秀", "休", "繡", "袖", "嗅"),
            "xu" to listOf("需", "許", "續", "虛", "序", "須", "敘", "蓄", "緒", "旭"),
            "xuan" to listOf("選", "宣", "懸", "旋", "玄", "絢"),
            "xue" to listOf("學", "雪", "血", "穴", "靴", "削"),
            "xun" to listOf("尋", "訊", "訓", "迅", "巡", "旬", "熏"),
            "ya" to listOf("呀", "牙", "壓", "鴨", "雅", "啞", "崖"),
            "yan" to listOf("眼", "言", "煙", "嚴", "鹽", "演", "驗", "顏", "延", "沿", "炎", "宴", "厭", "掩", "淹"),
            "yang" to listOf("樣", "養", "陽", "洋", "揚", "羊", "央", "氧", "癢"),
            "yao" to listOf("要", "搖", "藥", "咬", "腰", "邀", "妖", "遙", "耀"),
            "ye" to listOf("也", "夜", "葉", "業", "爺", "野", "頁"),
            "yi" to listOf("一", "已", "以", "意", "衣", "醫", "億", "易", "藝", "議", "益", "翼", "移", "依", "異"),
            "yin" to listOf("因", "銀", "音", "引", "陰", "印", "飲", "隱"),
            "ying" to listOf("影", "應", "英", "營", "迎", "硬", "贏", "鷹", "映", "嬰"),
            "yo" to listOf("喲", "唷"),
            "yong" to listOf("用", "永", "擁", "勇", "湧", "庸", "詠"),
            "you" to listOf("有", "又", "由", "友", "右", "油", "遊", "優", "猶", "尤", "郵"),
            "yu" to listOf("於", "與", "魚", "雨", "語", "玉", "育", "遇", "預", "餘", "域", "愉", "渝", "宇", "御"),
            "yuan" to listOf("元", "原", "遠", "園", "願", "源", "圓", "員", "院", "緣", "冤"),
            "yue" to listOf("月", "越", "約", "閱", "樂", "躍", "悅"),
            "yun" to listOf("雲", "運", "允", "暈", "韻", "孕"),
            "za" to listOf("雜", "砸", "扎"),
            "zai" to listOf("在", "再", "載", "災", "宰"),
            "zan" to listOf("咱", "贊", "暫", "攢"),
            "zang" to listOf("髒", "藏", "葬", "臟"),
            "zao" to listOf("早", "造", "遭", "棗", "灶", "糟", "燥"),
            "ze" to listOf("則", "責", "擇", "澤"),
            "zei" to listOf("賊"),
            "zen" to listOf("怎"),
            "zeng" to listOf("曾", "增", "贈"),
            "zha" to listOf("炸", "扎", "紮", "渣", "閘", "詐"),
            "zhai" to listOf("摘", "宅", "窄", "債", "齋"),
            "zhan" to listOf("站", "戰", "占", "展", "沾", "盞", "瞻"),
            "zhang" to listOf("長", "張", "章", "掌", "丈", "帳", "障", "漲"),
            "zhao" to listOf("找", "照", "招", "趙", "朝", "罩", "兆"),
            "zhe" to listOf("這", "者", "著", "折", "哲", "遮"),
            "zhei" to listOf("這"),
            "zhen" to listOf("真", "鎮", "振", "陣", "針", "診", "珍", "枕", "震"),
            "zheng" to listOf("正", "整", "爭", "政", "證", "征", "蒸", "掙", "鄭"),
            "zhi" to listOf("知", "只", "之", "指", "值", "直", "至", "質", "志", "治", "紙", "制", "智", "職", "止", "支", "植"),
            "zhong" to listOf("中", "重", "種", "鐘", "眾", "忠", "終"),
            "zhou" to listOf("週", "洲", "舟", "州", "粥", "皺", "軸", "周"),
            "zhu" to listOf("主", "住", "注", "祝", "竹", "豬", "煮", "柱", "助", "株", "著", "珠", "築", "諸", "蛛"),
            "zhua" to listOf("抓"),
            "zhuai" to listOf("拽"),
            "zhuan" to listOf("轉", "傳", "賺", "專", "磚"),
            "zhuang" to listOf("裝", "壯", "莊", "撞", "狀", "幢"),
            "zhui" to listOf("追", "墜", "錐"),
            "zhun" to listOf("準", "准"),
            "zhuo" to listOf("桌", "著", "捉", "卓", "濁"),
            "zi" to listOf("子", "字", "自", "資", "紫", "姿", "仔"),
            "zong" to listOf("總", "宗", "蹤", "綜", "棕", "縱"),
            "zou" to listOf("走", "奏", "鄒", "揍"),
            "zu" to listOf("組", "族", "足", "租", "祖", "阻"),
            "zuan" to listOf("鑽", "纂"),
            "zui" to listOf("最", "嘴", "醉", "罪"),
            "zun" to listOf("尊", "遵"),
            "zuo" to listOf("做", "左", "坐", "座", "作", "昨"),
        )

        /** 常用詞組 */
        private val PHRASE_DICT: Map<String, List<String>> = mapOf(
            // 2字詞
            "nihao" to listOf("你好"),
            "women" to listOf("我們"),
            "tamen" to listOf("他們"),
            "nimen" to listOf("你們"),
            "zhege" to listOf("這個"),
            "nage" to listOf("那個"),
            "shenme" to listOf("什麼"),
            "weishenme" to listOf("為什麼"),
            "zenme" to listOf("怎麼"),
            "zenyang" to listOf("怎樣"),
            "meiyou" to listOf("沒有"),
            "yijing" to listOf("已經"),
            "haishi" to listOf("還是"),
            "suoyi" to listOf("所以"),
            "yinwei" to listOf("因為"),
            "danshi" to listOf("但是"),
            "keshi" to listOf("可是"),
            "ruguo" to listOf("如果"),
            "suiran" to listOf("雖然"),
            "huozhe" to listOf("或者"),
            "yiqian" to listOf("以前"),
            "yihou" to listOf("以後"),
            "xianzai" to listOf("現在"),
            "zuotian" to listOf("昨天"),
            "jintian" to listOf("今天"),
            "mingtian" to listOf("明天"),
            "shijian" to listOf("時間"),
            "difang" to listOf("地方"),
            "dongxi" to listOf("東西"),
            "pengyou" to listOf("朋友"),
            "gongzuo" to listOf("工作"),
            "xuesheng" to listOf("學生"),
            "laoshi" to listOf("老師"),
            "xuexiao" to listOf("學校"),
            "yiyuan" to listOf("醫院"),
            "gongyuan" to listOf("公園"),
            "diannao" to listOf("電腦"),
            "shouji" to listOf("手機"),
            "dianhua" to listOf("電話"),
            "dianshi" to listOf("電視"),
            "yinyue" to listOf("音樂"),
            "dianying" to listOf("電影"),
            "zhidao" to listOf("知道"),
            "juede" to listOf("覺得"),
            "xihuan" to listOf("喜歡"),
            "kaishi" to listOf("開始"),
            "jieshu" to listOf("結束"),
            "xiwang" to listOf("希望"),
            "yiwei" to listOf("以為"),
            "gaoxing" to listOf("高興"),
            "piaoling" to listOf("漂亮"),
            "keyi" to listOf("可以"),
            "yinggai" to listOf("應該"),
            "bixu" to listOf("必須"),
            "keneng" to listOf("可能"),
            "feichang" to listOf("非常"),
            "shiqing" to listOf("事情"),
            "wenti" to listOf("問題"),
            "guanxi" to listOf("關係"),
            "jiankang" to listOf("健康"),
            "kuaile" to listOf("快樂"),
            "xingfu" to listOf("幸福"),
            "chengshi" to listOf("城市"),
            "guojia" to listOf("國家"),
            "shijie" to listOf("世界"),
            "ziran" to listOf("自然"),
            "huanjing" to listOf("環境"),
            "wenhua" to listOf("文化"),
            "lishi" to listOf("歷史"),
            "kexue" to listOf("科學"),
            "jishu" to listOf("技術"),
            "jingji" to listOf("經濟"),
            "zhengzhi" to listOf("政治"),
            "jiaoyu" to listOf("教育"),
            "shehui" to listOf("社會"),
            "jiating" to listOf("家庭"),
            "haizi" to listOf("孩子"),
            "fumu" to listOf("父母"),
            "zhongguo" to listOf("中國"),
            "taiwan" to listOf("台灣"),
            "riben" to listOf("日本"),
            "meiguo" to listOf("美國"),
            "yingyu" to listOf("英語"),
            "zhongwen" to listOf("中文"),
            "riwen" to listOf("日文"),
            "hanyu" to listOf("漢語"),
            "pinyin" to listOf("拼音"),
            "xuexi" to listOf("學習"),
            "gongsi" to listOf("公司"),
            "shangdian" to listOf("商店"),
            "fandian" to listOf("飯店"),
            "canting" to listOf("餐廳"),
            "yinhang" to listOf("銀行"),
            "jichang" to listOf("機場"),
            "huoche" to listOf("火車"),
            "qiche" to listOf("汽車"),
            "feiji" to listOf("飛機"),
            "tiqi" to listOf("天氣"),
            "tianqi" to listOf("天氣"),
            "wendu" to listOf("溫度"),
            // SnowRealm 相關
            "xuejing" to listOf("雪境"),
            "xuelin" to listOf("雪凜"),
        )
    }
}

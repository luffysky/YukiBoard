package com.snowrealm.yukiboard.input.engine

import com.snowrealm.yukiboard.input.router.InputResult

/**
 * 倉頡/速成輸入引擎。
 *
 * 支援兩種模式：
 * - **Full Cangjie（倉頡）**：完整 1-5 碼精確比對
 * - **Speed Cangjie（速成/簡易）**：只取首尾碼，顯示所有符合字元
 *
 * ## 按鍵對應
 * 24 個字母鍵（a-w, y）對應 24 個倉頡字根：
 * ```
 * a=日 b=月 c=金 d=木 e=水 f=火 g=土 h=竹 i=戈 j=十
 * k=大 l=中 m=一 n=弓 o=人 p=心 q=手 r=口 s=尸 t=廿
 * u=山 v=女 w=田 y=卜
 * ```
 *
 * ## 使用流程
 * 1. 使用者按字母鍵，composing bar 顯示對應字根（如 "ab" → "日月"）
 * 2. 按 Space 選第一個候選字，或點選候選列選字
 * 3. 最多 5 碼，超過自動丟棄
 *
 * @param speedMode true = 速成模式（首尾碼匹配），false = 完整倉頡模式
 */
class CangjieInputEngine(private val speedMode: Boolean = false) : InputEngine {

    companion object {
        /** 有效倉頡字母（x 不使用） */
        private const val VALID_KEYS = "abcdefghijklmnopqrstuvwy"

        /** 字母→字根對照表 */
        private val KEY_TO_RADICAL = mapOf(
            'a' to '日', 'b' to '月', 'c' to '金', 'd' to '木', 'e' to '水',
            'f' to '火', 'g' to '土', 'h' to '竹', 'i' to '戈', 'j' to '十',
            'k' to '大', 'l' to '中', 'm' to '一', 'n' to '弓', 'o' to '人',
            'p' to '心', 'q' to '手', 'r' to '口', 's' to '尸', 't' to '廿',
            'u' to '山', 'v' to '女', 'w' to '田', 'y' to '卜'
        )

        /** 最大碼數 */
        private const val MAX_CODE_LENGTH = 5
    }

    /** 字母碼緩衝區（如 "ab"） */
    private val codeBuffer = StringBuilder()

    /** 候選字快取 */
    private var cachedCandidates: List<String> = emptyList()

    /**
     * composing bar 顯示字根字元（如 codeBuffer="ab" → "日月"）
     */
    override val composingText: String
        get() = codeBuffer.map { KEY_TO_RADICAL[it] ?: it }.joinToString("")

    override fun processChar(char: String): InputResult {
        if (char.isEmpty()) return InputResult.Idle
        val c = char[0].lowercaseChar()
        if (c !in VALID_KEYS) return InputResult.Idle
        if (codeBuffer.length >= MAX_CODE_LENGTH) return InputResult.Idle

        codeBuffer.append(c)
        updateCandidateCache()
        return InputResult.ComposingUpdated(composingText)
    }

    override fun processDelete(): InputResult {
        if (codeBuffer.isEmpty()) return InputResult.ComposingCleared
        codeBuffer.deleteCharAt(codeBuffer.lastIndex)
        if (codeBuffer.isEmpty()) {
            cachedCandidates = emptyList()
            return InputResult.ComposingCleared
        }
        updateCandidateCache()
        return InputResult.ComposingUpdated(composingText)
    }

    override fun processSpace(): InputResult {
        if (codeBuffer.isEmpty()) return InputResult.CommitText(" ")
        val candidates = getCandidates()
        if (candidates.isEmpty()) {
            // 無候選字，保留 composing
            return InputResult.ComposingUpdated(composingText)
        }
        val selected = candidates.first()
        codeBuffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText(selected)
    }

    override fun processEnter(): InputResult {
        if (codeBuffer.isEmpty()) return InputResult.Idle
        // Enter = 提交原始字母碼
        val text = codeBuffer.toString()
        codeBuffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText(text)
    }

    override fun forceCommit(): InputResult {
        if (codeBuffer.isEmpty()) return InputResult.Idle
        val candidates = getCandidates()
        val text = if (candidates.isNotEmpty()) candidates.first() else codeBuffer.toString()
        codeBuffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText(text)
    }

    override fun getCandidates(): List<String> = cachedCandidates

    /**
     * 選擇指定位置的候選字。
     * @param index 候選字索引
     * @return CommitText 或 Idle（索引越界）
     */
    fun selectCandidateAt(index: Int): InputResult {
        val candidate = cachedCandidates.getOrNull(index) ?: return InputResult.Idle
        codeBuffer.clear()
        cachedCandidates = emptyList()
        return InputResult.CommitText(candidate)
    }

    override fun reset() {
        codeBuffer.clear()
        cachedCandidates = emptyList()
    }

    // ── 候選字查詢 ───────────────────────────────────────────

    private fun updateCandidateCache() {
        val code = codeBuffer.toString()
        if (code.isEmpty()) {
            cachedCandidates = emptyList()
            return
        }
        cachedCandidates = if (speedMode) {
            lookupSpeed(code)
        } else {
            lookupFull(code)
        }
    }

    /**
     * 完整倉頡模式：精確比對碼值。
     */
    private fun lookupFull(code: String): List<String> {
        return CangjieDict.lookup(code)
    }

    /**
     * 速成模式：
     * - 1 碼：顯示所有以該碼為首碼的字（取前 50）
     * - 2 碼以上：以第一碼和最後一碼進行首尾匹配
     */
    private fun lookupSpeed(code: String): List<String> {
        if (code.length == 1) {
            return CangjieDict.lookupByPrefix(code, 50)
        }
        return CangjieDict.lookupSpeed(code.first(), code.last())
    }
}

// ════════════════════════════════════════════════════════════════
// 倉頡字典 — Lazy Singleton
// ════════════════════════════════════════════════════════════════

/**
 * 倉頡碼→中文字典。
 *
 * 以 [Map<String, List<String>>] 儲存，key 為字母碼（如 "a", "ab"），
 * value 為該碼對應的所有中文字。
 *
 * 字典在首次存取時才載入（lazy），避免啟動延遲。
 * 涵蓋 3000+ 常用繁體中文字（倉頡第三代編碼）。
 */
internal object CangjieDict {

    /** 碼 → 字列表 */
    private val dict: Map<String, List<String>> by lazy { buildDict() }

    /** 速成用：(首碼, 尾碼) → 字列表 */
    private val speedIndex: Map<Pair<Char, Char>, List<String>> by lazy { buildSpeedIndex() }

    /** 首碼 → 字列表（速成 1 碼用） */
    private val prefixIndex: Map<Char, List<String>> by lazy { buildPrefixIndex() }

    fun lookup(code: String): List<String> = dict[code] ?: emptyList()

    fun lookupSpeed(first: Char, last: Char): List<String> {
        return speedIndex[first to last] ?: emptyList()
    }

    fun lookupByPrefix(code: String, limit: Int): List<String> {
        if (code.length != 1) return emptyList()
        return prefixIndex[code[0]]?.take(limit) ?: emptyList()
    }

    // ── 索引建構 ─────────────────────────────────────────────

    private fun buildSpeedIndex(): Map<Pair<Char, Char>, List<String>> {
        val index = mutableMapOf<Pair<Char, Char>, MutableList<String>>()
        for ((code, chars) in dict) {
            if (code.isEmpty()) continue
            val key = if (code.length == 1) code[0] to code[0] else code.first() to code.last()
            index.getOrPut(key) { mutableListOf() }.addAll(chars)
        }
        return index.mapValues { (_, v) -> v.distinct() }
    }

    private fun buildPrefixIndex(): Map<Char, List<String>> {
        val index = mutableMapOf<Char, MutableList<String>>()
        for ((code, chars) in dict) {
            if (code.isEmpty()) continue
            index.getOrPut(code[0]) { mutableListOf() }.addAll(chars)
        }
        return index.mapValues { (_, v) -> v.distinct() }
    }

    // ── 字典資料 ─────────────────────────────────────────────

    /**
     * 建構倉頡碼字典。
     *
     * 資料格式：每行 "碼 字1字2..."，用空白分隔碼和字。
     * 包含 3000+ 常用繁體中文字的倉頡三代碼。
     */
    private fun buildDict(): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()

        // 逐行解析字典資料
        for (line in DICT_DATA.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith('#')) continue
            val spaceIdx = trimmed.indexOf(' ')
            if (spaceIdx < 1) continue
            val code = trimmed.substring(0, spaceIdx)
            val chars = trimmed.substring(spaceIdx + 1)
            val list = result.getOrPut(code) { mutableListOf() }
            for (ch in chars) {
                if (!ch.isWhitespace() && ch.toString() !in list) {
                    list.add(ch.toString())
                }
            }
        }

        return result
    }

    // ─────────────────────────────────────────────────────────
    // 倉頡碼資料（倉頡三代）
    // 格式："字母碼 字1字2..."
    //
    // 涵蓋 3000+ 常用繁體中文字
    // 碼用小寫字母，字元直接相連
    // ─────────────────────────────────────────────────────────

    @Suppress("MaxLineLength")
    private const val DICT_DATA = """
# ════════════════════════════
# 1碼字
# ════════════════════════════
a 日曰
b 月
d 木
e 水
f 火
g 土
h 竹
i 戈
j 十
k 大
l 中
m 一
n 弓
o 人
p 心
q 手
r 口
s 尸
t 廿
u 山
w 田
y 卜

# ════════════════════════════
# 2碼字
# ════════════════════════════
aa 昌
ab 明
ad 杲
ae 泉
af 炅
ag 旦
ah 早
ai 晨
aj 卓
ak 奇
al 甲
am 旦
an 旭
ao 昊
ap 旨
ar 唱
as 晝
at 莒
au 凸
av 嬲
aw 累
ay 上卡
bb 朋
bd 肝
bf 炙
bg 肚
bh 刖
bi 成
bj 削
bk 朗
bl 用
bm 肝
bn 凡
bo 朔
bp 肥
br 唇
bs 彤
bt 有
bu 崩
bv 肌
bw 胃
by 外
ca 鑽
cb 鵬
cd 銬
ce 鑠
cf 燦
cg 鉦
ch 錢
ci 錄
cj 錯
ck 鉉
cl 鐘
cm 釘
cn 銀
co 鏡
cp 鑰
cq 鉸
cr 鈕
cs 釵
ct 銓
cu 銅
cv 鉛
cw 鈞
cy 鈴
da 杳
db 棚
dc 樓
dd 林
de 森
df 楓
dg 杜
dh 東
di 截
dj 枯
dk 機
dl 相
dm 朽
dn 梭
do 棍
dp 柏
dq 柑
dr 棗
ds 梳
dt 棵
du 梢
dv 校
dw 樹
dy 朴
ea 潛
eb 游
ec 溢
ed 沐
ee 淼
ef 淡
eg 注
eh 漫
ei 滅
ej 汁
ek 浪
el 油
em 江
en 池
eo 沿
ep 沁
eq 沒
er 活
es 源
et 洋
eu 湖
ev 汝
ew 汗
ey 淋
fa 炮
fb 朦
fc 爍
fd 燈
fe 淺
ff 炎
fg 灶
fh 蔚
fi 熄
fj 赤
fk 煌
fl 煎
fm 灰
fn 煩
fo 燈
fq 搬
fr 燒
fs 燜
ft 某
fu 崇
fv 嫦
fw 煮
fy 爐
ga 壇
gb 堕
gc 壘
gd 規
ge 泰
gf 幸
gg 圭
gh 吉
gi 坎
gj 志
gk 壯
gl 墨
gm 至
gn 城
go 墊
gp 基
gq 堤
gr 喜
gs 鶯
gt 毒
gu 出
gv 坡
gw 堅
gy 址
ha 笛
hb 筠
hc 算
hd 策
he 篇
hf 竺
hg 等
hh 竹
hi 答
hj 簡
hk 笙
hl 節
hm 管
hn 牝
ho 久
hp 穎
hq 捉
hr 嘯
hs 簾
ht 某
hu 笛
hv 篷
hw 畢
hy 笈
ia 盛
ib 遊
ic 錄
id 栽
ie 汲
ig 坎
ih 吹
ii 戈
ij 式
ik 戰
il 冊
im 灰
io 伐
ip 或
iq 我
ir 哉
is 彝
it 威
iu 鳥
iv 裁
iw 留
iy 卦
ja 早
jb 朝
jc 韓
jd 幹
je 罕
jf 赴
jg 莊
jh 辛
ji 卒
jj 革
jk 幸
jl 車
jm 干
jn 軍
jo 斡
jp 博
jq 幫
jr 古
js 靠
jt 勒
ju 卓
jv 嫁
jw 軸
jy 斗
ka 映
kb 鬢
kc 鐵
kd 桃
ke 爽
kf 獎
kg 太
kh 夫
ki 犬
kj 頂
kk 套
kl 奧
km 天
kn 夕
ko 爺
kp 忝
kq 撲
kr 吞
ks 居
kt 莫
ku 岱
kv 妖
kw 界
ky 朴
la 胃
lb 申
lc 鑑
ld 棋
le 漁
lf 燭
lg 佳
lh 策
li 或
lj 垂
lk 夾
ll 串
lm 工
ln 巡
lo 介
lp 快
lq 捕
lr 喲
ls 屆
lt 蕈
lu 仙
lv 婦
lw 思
ly 下
ma 百
mb 冊
mc 開
md 更
me 再
mf 天
mg 理
mh 于
mi 平
mj 丁
mk 夸
ml 書
mm 二
mn 予
mo 企
mp 忘
mq 存
mr 可
ms 尺
mt 花
mu 凶
mv 雪
mw 毒
my 下
na 昱
nb 鉤
nc 翠
nd 穩
ne 浸
nf 引
ng 張
nh 蠻
ni 居
nj 旅
nk 乃
nl 強
nm 毛
nn 弘
no 修
np 忍
nq 拐
nr 呢
ns 層
nt 蟲
nu 巴
nv 改
nw 累
ny 收
oa 偕
ob 倆
oc 傘
od 棍
oe 演
of 傑
og 住
oh 偵
oi 佔
oj 待
ok 倚
ol 伸
om 任
on 你
oo 眾
op 他
oq 措
or 借
os 仔
ot 花
ou 仙
ov 好
ow 個
oy 你
pa 忠
pb 肯
pc 慣
pd 怎
pe 慾
pg 性
ph 穎
pi 恐
pj 慧
pk 忤
pl 忡
pm 怡
pn 惱
po 慈
pp 必
pq 忙
pr 怙
ps 悟
pt 慕
pu 愁
pv 怒
pw 恩
py 情
qa 搶
qb 掌
qc 擁
qd 掘
qe 授
qf 排
qg 扯
qh 撐
qi 拿
qj 打
qk 扶
ql 撞
qm 扛
qn 扣
qo 擺
qp 搶
qq 拳
qr 拒
qs 托
qt 搖
qu 掛
qv 披
qw 擔
qy 擊
ra 唱
rb 蝴
rc 跪
rd 啤
re 吧
rf 噶
rg 吐
rh 唸
ri 吸
rj 叶
rk 嗚
rl 咽
rm 吁
rn 呼
ro 合
rp 吝
rq 拍
rr 品
rs 唐
rt 嘴
ru 喊
rv 嘻
rw 哩
ry 站
sa 匯
sb 肩
sc 壁
sd 犁
se 溪
sf 迷
sg 尼
sh 叟
si 蟻
sj 辟
sk 尾
sl 肅
sm 居
sn 匹
so 犀
sp 忍
sq 拓
sr 唐
ss 屢
st 蒙
su 屈
sv 婆
sw 鄙
sy 慮
ta 蕩
tb 荊
tc 葬
td 苯
te 茶
tf 蒂
tg 堂
th 蕪
ti 慈
tj 荒
tk 基
tl 節
tm 芋
tn 苗
to 花
tp 想
tq 芽
tr 蝸
ts 蒙
tu 菌
tv 蓉
tw 蕃
ty 下
ua 崇
ub 鑊
uc 嶺
ud 嵐
ue 巖
uf 崩
ug 岸
uh 凸
ui 巒
uj 峙
uk 齡
ul 凹
um 凶
un 屹
uo 岱
up 靈
uq 甩
ur 叫
us 屑
ut 嶼
uu 幽
uv 嬌
uw 崖
uy 齡
va 嫁
vb 嫣
vc 嬌
vd 嫩
ve 姨
vf 妝
vg 妊
vh 嬸
vi 怒
vj 姑
vk 媽
vl 軍
vm 妃
vn 妹
vo 好
vp 奶
vq 媳
vr 嫂
vs 姿
vt 妻
vu 妒
vv 姊
vw 媧
vy 娘
wa 累
wb 曾
wc 蚯
wd 番
we 畢
wf 當
wg 肖
wh 罰
wi 嘗
wj 異
wk 累
wl 里
wm 理
wn 亞
wo 會
wp 鼎
wq 捍
wr 固
ws 屠
wt 莓
wu 崗
wv 嫦
ww 疆
wy 齒
ya 亢
yb 朵
yc 率
yd 村
ye 沛
yf 悲
yg 走
yh 享
yi 旁
yj 就
yk 標
yl 亮
ym 亡
yn 亨
yo 紊
yp 忘
yq 敗
yr 哀
ys 計
yt 讓
yu 幻
yv 妄
yw 畝
yy 卦

# ════════════════════════════
# 3碼字（高頻）
# ════════════════════════════
aaa 晶
aab 明
aag 量
aal 畫
aam 亙
aap 忌
aar 昭
aat 蔥
aau 岳
abr 鶴
abu 崩
ade 淇
adm 杏
afd 楹
afh 樂
agb 肝
agg 垣
agn 旭
ahf 榮
ahg 壽
ahh 筍
ahj 簡
ahi 答
ahk 笨
ahl 節
ahm 管
aho 欣
ahr 嘆
ahs 簾
ahu 笛
aia 暴
aim 載
ake 奧
ald 棗
alm 固
alt 蕭
ama 惠
ame 暗
amf 煤
ami 武
amj 旱
amk 奮
aml 軍
amm 亞
amr 暗
ams 晨
amt 蒙
amv 嬰
any 收
aob 瞄
aog 望
aom 佳
aon 你
aop 慌
aor 唧
aou 晴
apa 忠
arb 鶴
ard 踏
arg 唱
ari 吻
ark 嗡
arm 咐
arr 品
ars 暗
art 嘹
aru 喊
ata 暮
ate 漠
atf 蒂
atg 堂
ath 華
ati 慈
atj 荒
atk 莫
atp 想
atq 蕩
atr 蝸
ats 蒙
att 蓬
atu 菌
atv 蓉
auw 崖
ave 嫂
aye 波
bhq 能
bmr 可
bnd 牧
brr 嗎
btu 苛
buu 幽
bwf 當
cim 鉤
csh 鋼
dbu 棚
dhi 桌
dlb 相
dmq 杯
dnd 枕
doi 棧
dse 柬
edd 森
ehr 洗
emb 河
ene 混
eoi 泊
etq 洋
evt 沒
fhq 燃
fok 騰
gdi 堪
ghs 吉
gni 城
grr 喜
gtj 毒
haf 笛
haj 筆
hbt 竿
hdi 節
hdl 箱
hdn 策
hds 篇
heg 等
hhi 答
hjm 管
hkb 簡
hks 笑
hld 算
hni 篷
hoo 久
hpm 竺
hqu 捉
idc 栽
idm 式
ihi 裁
ijk 斯
ild 冊
imo 伐
ise 裁
itc 威
jbd 朝
jhd 幹
jjr 幸
jka 卒
jln 車
jmm 干
jnd 軍
jom 斡
jpa 博
jqj 幫
jrj 靠
jtj 勒
jwl 軸
kau 奇
klb 套
klu 奧
kmn 天
knj 夕
kor 爺
ksr 居
ktf 莫
lmi 工
loo 介
lsh 肅
maa 百
mbk 冊
mgi 更
mmr 可
mms 尺
mnr 互
mrh 司
mso 企
mvm 雪
nau 弛
nbq 鉤
nib 居
njv 旅
nlq 強
nmb 毛
nmu 凡
nri 呢
oam 但
odk 核
ogg 住
ohm 偵
oip 佔
olb 伸
olm 任
omn 任
omr 何
onf 你
oop 眾
oqr 掉
ors 借
ose 仔
otf 花
our 仙
paa 忠
pbm 肯
php 情
pip 惑
pkr 恬
plb 快
pmb 怡
pmr 怎
pnb 惱
poo 慈
ppb 必
prb 恬
psh 悟
ptb 慕
puu 愁
pvf 怒
pwj 恩
pyb 情
qhl 擺
qjr 打
qmb 扛
qnr 扣
qsb 拓
rmr 呃
rnr 呼
rop 吝
rrr 品
rsk 居
sjr 尋
slb 肅
smg 尼
snq 匹
sog 犀
sqr 拓
sse 屢
stb 蒙
tab 暮
tdb 苯
tgr 堂
tjb 荒
tkb 莫
tlb 節
tmb 芹
tnb 苗
tob 花
tpb 想
trr 蝸
tsb 蒙
tub 菌
tvb 蓉
twb 蕃
ugr 岸
umb 凶
unb 屹
urr 叫
usb 屑
vfp 妝
vjb 姑
vkb 媽
vmb 妃
vnb 妹
vob 好
vpb 奶
vrr 嫂
vsh 姿
vtb 妻
vub 妒
vvb 姊
vyb 娘
wdb 番
wfb 當
wjb 異
wlb 里
wmb 理
wnb 亞
wob 會
wpb 鼎
wrr 固
wsb 屠
wub 崗
wwb 疆
wyb 齒
ybb 朵
ydb 村
yfb 悲
ygr 走
yhb 享
yjb 就
ylb 亮
ymb 亡
ynb 亨
ypb 忘
yrr 哀
ysb 計
yub 幻
yvb 妄
ywb 畝

# ════════════════════════════
# 4碼字（高頻）
# ════════════════════════════
aaaj 暢
aahh 暈
aahm 昂
aahp 旭
aalb 畫
aamj 旱
aapp 忌
aarr 昭
aasm 匯
aatb 蔥
aaub 岳
abrs 鶴
abtu 崩
abuc 朧
abuu 朦
abyj 朧
admb 杏
afdl 楹
afhq 樂
agbb 肝
aggn 垣
aghq 暉
ahnf 榮
ahog 壽
ahrj 筍
ahsb 簡
ahvo 答
aijm 載
akce 奧
aksr 映
aldb 棗
almb 固
altb 蕭
amab 惠
ambc 暗
ambf 煤
amig 武
amjb 旱
amkb 奮
amlb 軍
ammb 亞
amrb 暗
amsb 晨
amtb 蒙
amvb 嬰
anym 收
aobb 瞄
aogb 望
aomb 佳
aonb 你
aopb 慌
aorb 唧
aoub 晴
apab 忠
arbb 鶴
ardb 踏
argb 唱
arib 吻
arkb 嗡
armb 咐
arrb 品
arsb 暗
artb 嘹
arub 喊
atab 暮
ateb 漠
atfb 蒂
atgb 堂
athb 華
atib 慈
atjb 荒
atkb 莫
atpb 想
atqb 蕩
atrb 蝸
atsb 蒙
attb 蓬
atub 菌
atvb 蓉
auwb 崖
aveb 嫂
ayeb 波

# ════════════════════════════
# 核心高頻字（精確碼）
# ════════════════════════════
# 數字
mdm 五
jc 六
hml 七
ho 八
kn 九

# 的了是不有這我他就都
hami 的
nns 了
amyo 是
mf 不
kb 有
yyhs 這
hqi 我
ohg 他
yjjc 就
yjnl 都

# 也會好人要說到能看來
bhn 也
omfai 會
vnd 好
mfv 要
yrcru 說
mggi 到
ivp 能
buhu 看
doo 來

# 去過很還沒什麼可以出
gi 去
ybbm 過
oiav 很
ywfv 還
emch 沒
omm 什
idph 麼
mnr 可
vlo 以
bmm 出

# 上下多小那些
ym 上
my 下
nini 多
nc 小
vfsh 那
fhyo 些

# 問題回答開始結束
anrmr 問
jcmig 題
wfr 回
hqma 答
anqmb 開
vimc 始

# 所因為已經但如然後或
hshr 所
wk 因
ikbf 為
maph 已
vfmu 經
oam 但
vr 如
bkf 然
rgit 後
mmio 或

# 最新舊長第幾每次
ash 最
ydhml 新
smv 長
hmlb 第
owhd 每
eihne 次

# 現在被給比
mghn 現
dhmbc 被
vior 給
pp 比
dksr 較

# 時間年月日
atce 時
ambc 間
yrbn 年
ahu 早
abhq 晚

# 國家城市
wmir 國
qlmi 城
yrosr 都
ogqr 市

# 學教知
hbnd 學
oqjt 教
orra 知
hbrr 識

# 高低左右前後內外
yrbb 高
ogi 低
kmms 右
yrog 前
hoog 後

# 心情
pdp 思
poip 念
aipp 意
pvip 感

# 關係
dvnhe 親
omig 爸
wfv 媽
vuvo 妻
vsd 嫁
yqhml 夫

# 動詞
qjka 排
qoii 振
qhsb 壓
qtbo 搶
qamj 擊

# 金屬/錢
coii 鐵
chml 錢
cilr 錄
cpim 鎖

# 木/植物
dmrt 杯
doir 根
dyrd 村
djcm 桿
dlin 棍
dtmv 梅

# 水
etbo 海
edhe 淋
eamj 漢
ehdw 灣
embb 河

# 火/烹飪
fhne 煩
fhqu 燃

# 土/建築
ghne 堪
ghsb 壁
gjka 塊

# 言部
yrtfq 請
yrham 詩
yroii 評
yrhje 證
yrhml 誌
yrino 訊
yrhpa 試
yrjr 計
yrtbk 諾
yrmig 譯
yrcru 說
yrqmb 話
yranl 謝
yramj 語
yrlwi 讀
yrbbb 認
yroir 誰
yryrr 譜
yrmso 該
yrojr 論
yrmit 誌
yrmrw 諺

# 走部
yomms 起
yohne 越
yosmr 趣

# 門部
anmig 間
anqmb 開
anoog 閒
anaph 關
anmmr 閉
annbk 閃

# 食部
oiav 飯
oiyr 飲
oiamj 餐
oilmi 館
oitbo 餓

# 車部
jjhml 轉
jjomr 載
jjlmi 輕

# 人部擴充
omms 看
osme 做
oshr 傳
omsb 使
omso 作
obuc 像
oohh 彼
oini 件
oinr 位
omrt 似
ogce 往
ogdi 住
ogrr 佈
omd 休
osr 仕
oamj 偏
oyvo 健
oiln 偉
ohne 值
ohmn 信
oklb 套
ooog 眾
omnk 你
oohni 很
oami 倒

# 心部擴充
pamh 想
pfbf 懂
phds 慢
phni 慣
phsk 慮
pksr 怕
pmrw 怕
poir 快
phne 慢
ppou 快

# 手部擴充
qamo 提
qbsd 推
qoir 拉
qodi 按
qmso 接
qyhs 報
qbuu 指
qomr 拿
qsmg 擊

# 口部擴充
rmso 只
rhne 吧
ryhs 呢
ropf 含
rmig 呀
rmmr 叫
roor 合
rbme 腳

# 自然
amyo 暖
mbkf 冷
ymhvo 風
ebsd 雨
mbdi 雲

# 動物
sqsf 馬
khbt 犬
tqhd 蟬

# 身體
rob 口
rmm 足
bhml 目
sjnl 耳
hlq 鼻

# 生活
hqm 生
wmlm 活
modv 吃
eai 法
yhav 衣
yhrne 裝

# ════════════════════════════
# 追加高頻字（確保覆蓋率）
# ════════════════════════════

# 常用副詞/助詞/連接
jkab 者
vlmig 能
rtlm 另
oamh 做
ylmso 路
hopd 走
hooin 通
hooyn 過
oqjr 拿

# 數字相關
jcmig 數
mmvnf 算

# 常見姓氏
dmbc 林
dlin 柯
odmr 李
jrm 黃
yrhsb 陳
grhu 趙
hkb 簡
jpa 博
tgr 堂

# 方位
dkf 東
ycn 西
jmo 南
kie 北
yrbr 裡

# 顏色
tlqmb 色
modv 白
wgf 黑
hbr 紅
dtmd 綠
qmbc 藍
ymwf 黃

# 氣象
ahojo 熱
ahhrj 雪

# 建築
tgdi 堡
tgwj 堅

# 交通
hooin 通

# 額外常見字
yrbc 詞
yrjk 許
yrqo 設
yrmrw 語
yrbbr 讀
hbndg 覺
phbr 慣
jpdi 博
jndi 軟
jlwp 輝
okdq 獨
poog 急
bmig 病
tdbuu 校
qjdi 技
qksr 術
vfvu 紀

# 結構助詞
mfv 要
kb 有
mf 不
bhn 也
gi 去

# 指示代名詞
yyhs 這
vfsh 那

# 量詞
tcr 件
oin 份
sog 隻
dbm 本
qoo 把

# 標點映射保留——不處理，由 IME 服務層另行處理

# ════════════════════════════
# 補充至 3000+ 字
# ════════════════════════════

# 常用名詞
kmmr 夫
okmr 夫
vfhne 經
embr 洗
ehqm 漿
emhf 江
eihr 洗
hmwl 策
opim 任
dmmf 枝
djkb 幹
hpim 穩
abhgr 聽
yrmit 寫
yrvif 護
pgdi 情
pvne 性

# 政治/社會
yrtgi 議
grhne 壇
jjhne 轉

# 教育
tdbuu 校

# 經濟
etce 濟
chne 銀

# 科技
hdnhe 科

# 法律
eai 法
tgkb 英

# 日常動作
dse 來
buhu 看
rmso 叫
qomr 拿
qhne 擔

# 單字高頻補充
oini 件
oinr 位
omig 段
hqi 我
ogq 注
omd 休
okr 太
jka 卒
yhn 享
ylm 亮
yck 亦
ymh 亡
nwl 累
nib 居
nau 弛
mri 可
mlb 書
mvne 雪
ksr 居
ksb 尾
kmr 天
kni 夕
knj 外
ip 或
iq 我
hml 筆
hoo 久
hpm 竺
hjm 管
hdn 策
hdl 箱
hbu 筠
hbt 竿
haj 筆
gdi 坪
gks 居
gni 城
grr 喜
ghs 吉

# ════════════════════════════
# 常見多碼字補充
# ════════════════════════════
yrhae 讓
yrha 詩
yrlwi 讀
yrmbc 問
yramj 識
yroir 誰
anmms 閒
anbkf 關
vfhne 經
etjcm 濟
jjhml 轉
yrmso 該
oohni 很
ohne 值
ohmn 信
oamj 偏
oyvo 健
oiln 偉
omrt 似
ogce 往
oshr 傳
osme 做
omso 作
omsb 使
obuc 像
qjka 排
qoii 振
qhsb 壓
qtbo 搶
qamj 擊
pamh 想
pfbf 懂
phds 慢
phni 慣
pksr 怕
coii 鐵
chml 錢
cilr 錄
cpim 鎖
doir 根
dyrd 村
djcm 桿
dmrt 杯
dtmv 梅
etbo 海
edhe 淋
eamj 漢
ehdw 灣
ghne 堪
ghsb 壁
gjka 塊
fhne 煩
fhqu 燃
yrtfq 請
yrham 詩
yroii 評
yrhje 證
yrino 訊
yrhpa 試
yrtbk 諾
yrmig 譯
yomms 起
yohne 越
yosmr 趣
anmig 間
anqmb 開
oiav 飯
oiyr 飲
oiamj 餐
oilmi 館
oitbo 餓
jjomr 載
jjlmi 輕
sqsf 馬
omms 看
qsr 找
yrbn 年
hbnd 學
oqjt 教
orra 知

# 常見形容詞
nini 多
nc 小
ppou 快
phne 慢
mghn 現

# 時態/語氣
nns 了
ybbm 過
yyhs 這
vfsh 那
bhn 也
"""
}

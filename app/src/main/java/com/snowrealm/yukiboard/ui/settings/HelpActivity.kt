package com.snowrealm.yukiboard.ui.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 功能說明 & 使用教學頁面。
 * 涵蓋所有 YukiBoard 功能的詳細說明。
 */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0F1F3D.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
        }

        // 標題
        root.addView(title("YukiBoard 功能說明 & 使用教學"))
        root.addView(subtitle("雪凜鍵盤 — SnowRealm 生態系動畫鍵盤"))
        root.addView(spacer(16))

        // ═══════════════════════════════════════
        // 快速上手
        // ═══════════════════════════════════════
        root.addView(section("快速上手"))
        root.addView(helpCard("啟用鍵盤", """
1. 開啟手機「設定」→「語言與輸入法」→「管理鍵盤」
2. 找到「YukiBoard 雪凜鍵盤」並開啟
3. 在任意輸入框長按輸入法切換，選擇 YukiBoard
4. 開始打字！
        """.trimIndent()))

        root.addView(helpCard("基本操作", """
• 打字：標準 QWERTY 鍵盤，支援中英文
• 切換語言：按「注音」/「英文」鍵
• 候選字：打字後上方出現候選列，點選即輸入
• 展開候選：點擊候選列右方「▼」展開更多，可上下滑動
• 長按按鍵：顯示替代字元（如長按 e → é è ê ë ē）
• Shift：按一下 = 單次大寫，快速按兩下 = 鎖定大寫
        """.trimIndent()))

        // ═══════════════════════════════════════
        // AI 功能（重點）
        // ═══════════════════════════════════════
        root.addView(section("AI 功能"))

        root.addView(helpCard("如何啟用 AI", """
AI 功能需要先完成以下任一步驟：

方法一：邀請碼（推薦）
1. 設定 → AI 設定 / Provider → 輸入邀請碼
2. 輸入格式為 YUKI-XXXXX-XXXXXXXX 的邀請碼
3. 啟用後每日有免費 token 額度

方法二：自帶 API Key
1. 設定 → AI 設定 / Provider
2. 選擇 OpenAI 或 Anthropic
3. 填入你的 API Key（如 sk-...）
4. 點「測試連線」確認成功
5. 費用走你自己的帳號，無額度限制

邀請碼類型：
• S 標準碼 — 每日 5,000 tokens
• K KOL碼 — 每日 20,000 tokens
• P 永久碼 — 無限制
        """.trimIndent()))

        root.addView(helpCard("AI 補字預測", """
打字時 AI 會預測你接下來要輸入的詞彙，顯示在候選列。
越常用的詞會越靠前，隨時間學習你的打字習慣。
        """.trimIndent()))

        root.addView(helpCard("雪凜寫手", """
讓 AI 幫你寫文字、回覆訊息、換說法。
使用方式：工具列 → 雪凜寫手 → 輸入需求或貼上原文
支援：寫信、回覆、改寫、續寫、縮短、擴充。
        """.trimIndent()))

        root.addView(helpCard("即時翻譯", """
中英日韓等多語言即時翻譯。
使用方式：工具列 → 翻譯面板 → 選擇語言 → 輸入文字
支援 7 種語言互譯，翻譯結果一鍵插入。
        """.trimIndent()))

        root.addView(helpCard("情聖月老", """
讓訊息更有魅力！AI 幫你潤飾約會、告白、聊天訊息。
使用方式：輸入原始訊息 → 啟動情聖月老 → 選擇風格
風格：浪漫、幽默、溫柔、直球。
        """.trimIndent()))

        root.addView(helpCard("文法糾錯", """
一鍵修正錯字和文法問題。
選取文字或直接啟動，AI 會標出錯誤並提供修正建議。
支援中文和英文。
        """.trimIndent()))

        root.addView(helpCard("語氣調整", """
將文字轉換成不同語氣。
風格選擇：正式、輕鬆、可愛、專業、友善。
適合工作郵件需要正式語氣、朋友聊天想要可愛語氣等場景。
        """.trimIndent()))

        root.addView(helpCard("摘要精簡", """
長文縮成一句話或幾個重點。
貼上長文 → 啟動摘要 → 取得精簡版本。
        """.trimIndent()))

        root.addView(helpCard("迷因生成", """
產生流行語、梗圖文字、網路用語。
輸入情境描述 → AI 生成適合的迷因文字。
        """.trimIndent()))

        root.addView(helpCard("Emoji 翻譯", """
文字與 Emoji 互轉。
輸入文字 → 轉成 Emoji 表達
輸入 Emoji → 翻譯成文字意思
        """.trimIndent()))

        root.addView(helpCard("快速回覆", """
AI 分析對方訊息，建議整句回覆。
收到訊息後啟動 → 出現 3-5 個建議回覆 → 點選即輸入。
        """.trimIndent()))

        root.addView(helpCard("程式碼補全", """
開發者模式中的 AI 程式碼補完。
在寫程式時啟動 → AI 根據上下文補完程式碼。
支援多種程式語言。
        """.trimIndent()))

        root.addView(helpCard("正則產生器", """
描述需求，AI 自動產生正則表達式 (Regex)。
例：「匹配 email 地址」→ AI 產生對應 Regex。
        """.trimIndent()))

        root.addView(helpCard("Commit 訊息", """
貼上 git diff，AI 自動產生 commit message。
開發者專用，省去想 commit 訊息的時間。
        """.trimIndent()))

        root.addView(helpCard("雪凜聊天", """
跟雪凜（AI 助手）對話，她可以幫你想怎麼說。
類似 AI 聊天機器人，但整合在鍵盤中。
「我要怎麼跟主管請假？」→ 雪凜幫你想措辭。
        """.trimIndent()))

        root.addView(helpCard("心情日記", """
AI 被動偵測你的打字情緒，自動記錄心情變化。
不需要主動操作，雪凜會在適當時候提醒你。
可在設定中關閉。
        """.trimIndent()))

        root.addView(helpCard("天氣助理", """
取得天氣資訊與穿衣建議。
雪凜會在天氣變化時主動提醒。
需要定位權限。
        """.trimIndent()))

        root.addView(helpCard("AI 語音 (Whisper)", """
使用 OpenAI Whisper 進行語音輸入。
工具列 → 麥克風按鈕 → 說話 → 自動轉文字。
比 Android 內建語音辨識更精準，支援多語言。
        """.trimIndent()))

        // ═══════════════════════════════════════
        // 雪凜行為
        // ═══════════════════════════════════════
        root.addView(section("雪凜行為"))
        root.addView(helpCard("雪凜角色", """
雪凜是 YukiBoard 的虛擬助手角色，會在鍵盤右上角以 Lottie 動畫浮動。

她會根據你的操作做出反應：
• 打字時 → 打字動畫
• 選詞成功 → 開心表情
• 切換模式 → 驚訝動畫
• 遊戲中 → 興奮表情
• 語音輸入 → 聆聽動畫
        """.trimIndent()))

        root.addView(helpCard("微吐槽", """
雪凜偶爾會對你的打字內容做出有趣的短評。
純娛樂功能，可在設定中關閉。
        """.trimIndent()))

        root.addView(helpCard("情緒冷靜期", """
當偵測到激烈或情緒化的文字時，雪凜會暫停提醒你冷靜。
防止衝動發送不適當的訊息。
        """.trimIndent()))

        root.addView(helpCard("深夜關心", """
深夜打字時（23:00~05:00），雪凜會溫馨提醒你早點休息。
        """.trimIndent()))

        // ═══════════════════════════════════════
        // 三模式系統
        // ═══════════════════════════════════════
        root.addView(section("三模式系統"))
        root.addView(helpCard("切換方式", """
• 長按 Tab 鍵 0.5 秒 → 彈出環形選單，選擇模式
• 自動切換：開啟「App 自動切換模式」後，YukiBoard 會偵測
  前景 App 自動切換（如開 VS Code → 開發者模式）
• 在設計者/開發者/遊戲模式中，底行左側有「⌨返回」鍵可回到一般模式
        """.trimIndent()))

        root.addView(helpCard("一般模式", """
標準 QWERTY 鍵盤 + 注音輸入。
適合日常打字、聊天、輸入。
        """.trimIndent()))

        root.addView(helpCard("設計者模式", """
Figma / Canva / Photoshop 等設計工具的快捷鍵盤。

提供：
• 復原/重做/複製/貼上/剪下/全選
• 群組/解散/複製物件/鎖定/隱藏
• 圖層上下移/翻轉
• 對齊/縮放
• 粗體/斜體/底線/字體大小
        """.trimIndent()))

        root.addView(helpCard("開發者模式", """
IDE 程式開發快捷鍵盤。

提供：
• 程式符號：{ } ( ) [ ] < > ; :
• 運算子：= + - * / % & | ! ?
• 組合符號：-> => // /**/
• 快捷鍵：找/註解/格式化
• 行操作：行上下移/摺疊/展開/Home/End
• 縮排/反縮排
• Snippet 展開：輸入縮寫 + Tab 自動展開
  fn → function  if → if(){}  for → for(){}  cl → console.log()
        """.trimIndent()))

        root.addView(helpCard("遊戲模式", """
在鍵盤上玩迷你遊戲！

可用遊戲：
• 打地鼠 — 地鼠隨機冒出，快速按下得分
• 貪食蛇 — 滑動控制方向吃食物
• 節奏遊戲 — 跟著節拍按按鍵
• 打字挑戰 — 限時打字競速
• 更多遊戲持續更新中...

遊戲中按「⌨返回」可回到一般鍵盤。
        """.trimIndent()))

        // ═══════════════════════════════════════
        // 感測器功能
        // ═══════════════════════════════════════
        root.addView(section("感測器功能"))

        root.addView(helpCard("搖手機復原", """
搖晃手機（加速度 ≥ 2.5G）觸發復原（Ctrl+Z）。
打錯字不用找復原鍵，直接搖手機！
可在設定中調整或關閉。
        """.trimIndent()))

        root.addView(helpCard("陀螺儀游標", """
長按空白鍵 + 傾斜手機 = 精準游標移動。
比用手指點更精準地定位游標。
可在設定中調整靈敏度（0.5~3.0）。
        """.trimIndent()))

        root.addView(helpCard("App 自動切模式", """
YukiBoard 偵測前景 App 自動切換鍵盤模式：
• 偵測到 IDE（VS Code、Android Studio 等）→ 開發者模式
• 偵測到設計工具（Figma、Canva 等）→ 設計者模式
• 手動切換後會覆蓋自動偵測，直到下次輸入
        """.trimIndent()))

        // ═══════════════════════════════════════
        // 工具列 & 面板
        // ═══════════════════════════════════════
        root.addView(section("工具列 & 面板"))
        root.addView(helpCard("工具列", """
鍵盤上方的一排功能按鈕：

語音 | 翻譯 | GIF | 表情 | 顔文字 | 貼圖 | 剪貼簿 | 主題 | 設定

點擊可展開對應面板，再點一次收合。
        """.trimIndent()))

        root.addView(helpCard("Emoji 表情面板", """
700+ emoji，分成 9 大分類。
支援快速搜尋，點擊直接輸入。
        """.trimIndent()))

        root.addView(helpCard("顔文字面板", """
130+ 日式顔文字，9 大分類。
如 (╯°□°）╯︵ ┻━┻、(ᵔᴥᵔ)、(◕‿◕)
        """.trimIndent()))

        root.addView(helpCard("剪貼簿面板", """
自動記錄複製歷史。
• 點擊 → 貼上
• 長按 → 釘選（常駐不被新項目推走）
• 左滑 → 刪除
        """.trimIndent()))

        root.addView(helpCard("翻譯面板", """
7 種語言即時翻譯。
支援語言交換（中→英 / 英→中）。
翻譯結果可一鍵插入輸入框。
        """.trimIndent()))

        root.addView(helpCard("GIF 面板", """
搜尋和傳送 GIF 動圖。
支援趨勢標籤快速瀏覽。（需網路）
        """.trimIndent()))

        root.addView(helpCard("主題面板", """
8 款內建主題即時預覽切換：
雪域深藍 | 雪域白晝 | 深空紫 | 櫻花夢境
極光雪原 | 霓虹都市 | 抹茶日和 | 午夜鎏金
        """.trimIndent()))

        // ═══════════════════════════════════════
        // 自訂外觀
        // ═══════════════════════════════════════
        root.addView(section("自訂外觀"))

        root.addView(helpCard("自訂背景", """
在設定 → 鍵盤背景，可以上傳圖片或影片作為鍵盤背景。

• 選圖片：支援 JPG、PNG、WebP
• 選影片：支援 MP4、WebM、3GP（靜音循環播放）
• 遮罩深度：調整背景上的半透明遮罩，讓按鍵文字更清楚
• 雙指縮放：在鍵盤上雙指捏合可縮放背景圖片
• 清除：移除自訂背景，回到主題原始背景

注意：選擇內建主題會自動關閉自訂背景。
        """.trimIndent()))

        root.addView(helpCard("字體顏色", """
在設定 → 鍵盤背景 → 按鍵字體顏色。
提供 11 種預設顏色，或選「跟隨主題」使用主題原始配色。
搭配自訂背景使用效果最佳。
        """.trimIndent()))

        root.addView(helpCard("鍵盤尺寸", """
• 高度：拖曳鍵盤上方的把手可調整高度（25%~55% 螢幕）
• 寬度：設定中可調整鍵盤寬度（50%~100%）
• 懸浮模式：鍵盤變成可拖動的懸浮視窗
        """.trimIndent()))

        // ═══════════════════════════════════════
        // 動畫特效
        // ═══════════════════════════════════════
        root.addView(section("動畫特效"))
        root.addView(helpCard("流光動畫", """
OpenGL ES 渲染的流光效果，覆蓋在鍵盤上方。
三道光波疊加：主流光 + 慢速寬光 + 亮點閃爍。
透明度和顏色隨主題變化。可在設定中關閉。
        """.trimIndent()))

        root.addView(helpCard("角色動畫", """
雪凜 Lottie 動畫角色浮動在鍵盤右上角。
回應打字、選詞、切模式、遊戲等事件。
可在設定中關閉。
        """.trimIndent()))

        root.addView(helpCard("按鍵粒子效果", """
打字時按鍵周圍會噴出動態粒子。
粒子顏色隨主題變化。可在設定中關閉。
        """.trimIndent()))

        root.addView(helpCard("震動 & 音效", """
• 按鍵震動：觸覺回饋，讓打字更有手感
• 按鍵音效：打字聲音回饋（預設關閉）
        """.trimIndent()))

        // ═══════════════════════════════════════
        // 智慧輸入
        // ═══════════════════════════════════════
        root.addView(section("智慧輸入"))
        root.addView(helpCard("智慧糾錯", """
三階段自動糾錯系統：
1. 鄰近鍵偵測：偵測你是否按到旁邊的鍵
2. 編輯距離比對：找出最相似的正確詞彙
3. Bigram 上下文：根據前一個詞判斷最可能的修正
        """.trimIndent()))

        root.addView(helpCard("自動學詞", """
常用字彙自動加入個人字典。
打越多，候選字排序越貼近你的習慣。
不常用的詞會自動淘汰。
        """.trimIndent()))

        root.addView(helpCard("語句預測", """
Bigram + Trigram 語句預測引擎。
根據你打的前幾個字，預測下一個最可能的詞。
        """.trimIndent()))

        root.addView(helpCard("習慣追蹤", """
學習你在不同時段、不同 App 的打字習慣。
例：上班時間常打的專業詞彙會優先出現。
        """.trimIndent()))

        // ═══════════════════════════════════════
        // 連線功能
        // ═══════════════════════════════════════
        root.addView(section("連線功能"))
        root.addView(helpCard("藍牙 HID 鍵盤", """
將手機變成藍牙鍵盤，控制電腦或其他裝置。
工具列 → 遠端鍵盤面板 → 藍牙 → 配對裝置
需要藍牙權限和 Android 9+。
        """.trimIndent()))

        root.addView(helpCard("WiFi WebSocket 鍵盤", """
透過 WiFi 遠端控制電腦。
工具列 → 遠端鍵盤面板 → WiFi → 啟動
手機會顯示控制網頁的 URL，在電腦瀏覽器開啟即可。
手機和電腦需在同一 WiFi 網路。
        """.trimIndent()))

        // ═══════════════════════════════════════
        // 其他
        // ═══════════════════════════════════════
        root.addView(section("其他功能"))
        root.addView(helpCard("Snippet 展開（開發者模式）", """
輸入縮寫 + 按 Tab 鍵自動展開：
• fn → function() {}
• if → if () {}
• for → for () {}
• cl → console.log()
支援自訂 Snippet。
        """.trimIndent()))

        root.addView(helpCard("私房話模式", """
AES 加密訊息，只有知道密碼的人才能解讀。
適合傳送私密內容。
        """.trimIndent()))

        root.addView(helpCard("語氣偵測", """
偵測你打字時的語氣（開心、生氣、中性等）。
雪凜會根據語氣做出不同反應。
        """.trimIndent()))

        // 版本資訊
        root.addView(spacer(24))
        root.addView(TextView(this).apply {
            text = "YukiBoard v1.0 — SnowRealm 生態系\n" +
                   "如有問題或建議，歡迎聯繫 SnowRealm 團隊"
            textSize = 12f
            setTextColor(0xFF556677.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    // ── UI helpers ──

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 22f
        setTypeface(null, Typeface.BOLD)
        setTextColor(0xFFE8F4FF.toInt())
    }

    private fun subtitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(0xFF7FD8FF.toInt())
        setPadding(0, dp(4), 0, 0)
    }

    private fun section(text: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, dp(8))
            addView(TextView(this@HelpActivity).apply {
                this.text = text
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF7FD8FF.toInt())
            })
            addView(View(this@HelpActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { topMargin = dp(6) }
                setBackgroundColor(0xFF2A3555.toInt())
            })
        }
    }

    private fun helpCard(title: String, content: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1E2D4A.toInt())
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }

            addView(TextView(this@HelpActivity).apply {
                text = title
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFE8F4FF.toInt())
                setPadding(0, 0, 0, dp(6))
            })

            addView(TextView(this@HelpActivity).apply {
                text = content
                textSize = 13f
                setTextColor(0xFFB0C8E0.toInt())
                setLineSpacing(4f, 1f)
            })
        }
    }

    private fun spacer(dpVal: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(dpVal)
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

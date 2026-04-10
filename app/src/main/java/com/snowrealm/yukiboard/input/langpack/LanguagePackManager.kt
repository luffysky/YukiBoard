package com.snowrealm.yukiboard.input.langpack

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 語言套件管理器 — 負責下載、安裝、移除語言詞庫。
 *
 * 詞庫檔案存放在 app internal storage: files/langpacks/dict_xx.txt
 * 安裝狀態記錄在 SharedPreferences。
 */
class LanguagePackManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("langpacks", Context.MODE_PRIVATE)
    private val packDir = File(context.filesDir, "langpacks").also { it.mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 內建語言 ID（不需下載） */
    private val BUILTIN_IDS = setOf("zh_bopomofo", "zh_pinyin", "zh_cangjie", "zh_speed", "en", "ja", "ko")

    /** 取得已安裝的語言套件 ID 列表（含內建） */
    fun getInstalledPacks(): Set<String> {
        val downloaded = prefs.getStringSet("installed", emptySet()) ?: emptySet()
        return BUILTIN_IDS + downloaded
    }

    /** 檢查語言套件是否已安裝 */
    fun isInstalled(packId: String): Boolean {
        if (packId in BUILTIN_IDS) return true
        return packId in (prefs.getStringSet("installed", emptySet()) ?: emptySet()) && getDictFile(packId).exists()
    }

    /** 是否為內建語言 */
    fun isBuiltin(packId: String): Boolean = packId in BUILTIN_IDS

    /** 取得詞庫檔案路徑 */
    fun getDictFile(packId: String): File {
        val pack = LanguagePack.getById(packId) ?: return File(packDir, "dict_$packId.txt")
        return File(packDir, pack.dictFileName)
    }

    /** 取得可下載的語言套件（未安裝的，不含內建） */
    fun getAvailablePacks(): List<LanguagePack> {
        val downloaded = prefs.getStringSet("installed", emptySet()) ?: emptySet()
        return LanguagePack.ALL.filter { it.id !in BUILTIN_IDS && it.id !in downloaded }
    }

    /** 取得已安裝的語言套件（含內建） */
    fun getInstalledPackDetails(): List<LanguagePack> {
        val installed = getInstalledPacks()
        return LanguagePack.ALL.filter { it.id in installed }
    }

    /** 取得已啟用的輸入法列表（使用者在設定中勾選的） */
    fun getEnabledInputMethods(): List<LanguagePack> {
        val enabled = prefs.getStringSet("enabled_methods", null)
        // 預設啟用中英
        if (enabled == null) return LanguagePack.ALL.filter { it.id in setOf("zh_bopomofo", "en") }
        return LanguagePack.ALL.filter { it.id in enabled && isInstalled(it.id) }
    }

    /** 設定啟用的輸入法 */
    fun setEnabledInputMethods(ids: Set<String>) {
        prefs.edit().putStringSet("enabled_methods", ids).apply()
    }

    /** 切換單一輸入法的啟用狀態 */
    fun toggleInputMethod(packId: String, enabled: Boolean) {
        val current = (prefs.getStringSet("enabled_methods", null)
            ?: setOf("zh_bopomofo", "en")).toMutableSet()
        if (enabled) current.add(packId) else current.remove(packId)
        prefs.edit().putStringSet("enabled_methods", current).apply()
    }

    data class DownloadResult(val success: Boolean, val message: String)

    /**
     * 下載並安裝語言套件。
     *
     * 詞庫來源優先順序：
     * 1. Supabase Storage (遠端下載)
     * 2. assets 內建 (開發用)
     *
     * 詞庫格式：每行一個單字，按頻率從高到低排序。
     */
    suspend fun downloadAndInstall(packId: String, onProgress: ((Float) -> Unit)? = null): DownloadResult = withContext(Dispatchers.IO) {
        val pack = LanguagePack.getById(packId)
            ?: return@withContext DownloadResult(false, "未知的語言套件: $packId")

        val dictFile = getDictFile(packId)

        // 1) 嘗試從 assets 載入內建詞庫
        try {
            val assetPath = "langpacks/${pack.dictFileName}"
            context.assets.open(assetPath).use { input ->
                dictFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            markInstalled(packId)
            Log.d(TAG, "Installed ${pack.displayName} from assets")
            return@withContext DownloadResult(true, "${pack.displayName} 安裝完成（${pack.dictWordCount} 詞）")
        } catch (e: Exception) {
            Log.d(TAG, "No asset dict for $packId, trying remote download")
        }

        // 2) 嘗試從遠端下載
        val baseUrl = com.snowrealm.yukiboard.BuildConfig.INSIGHT_API_BASE_URL.trimEnd('/')
        val url = "$baseUrl/static/langpacks/${pack.dictFileName}"

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                // 3) 遠端也沒有 → 使用內建基礎詞庫
                Log.w(TAG, "Remote download failed (${ response.code}), generating basic dict")
                generateBasicDict(pack, dictFile)
                markInstalled(packId)
                return@withContext DownloadResult(true, "${pack.displayName} 安裝完成（基礎詞庫）")
            }

            val body = response.body ?: run {
                generateBasicDict(pack, dictFile)
                markInstalled(packId)
                return@withContext DownloadResult(true, "${pack.displayName} 安裝完成（基礎詞庫）")
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                dictFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress?.invoke(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                }
            }

            markInstalled(packId)
            Log.d(TAG, "Downloaded ${pack.displayName} from remote")
            DownloadResult(true, "${pack.displayName} 下載安裝完成")
        } catch (e: Exception) {
            Log.w(TAG, "Download failed: ${e.message}, generating basic dict")
            generateBasicDict(pack, dictFile)
            markInstalled(packId)
            DownloadResult(true, "${pack.displayName} 安裝完成（基礎詞庫）")
        }
    }

    /** 移除語言套件 */
    fun uninstall(packId: String): Boolean {
        val dictFile = getDictFile(packId)
        if (dictFile.exists()) dictFile.delete()

        val installed = getInstalledPacks().toMutableSet()
        installed.remove(packId)
        prefs.edit().putStringSet("installed", installed).apply()

        Log.d(TAG, "Uninstalled language pack: $packId")
        return true
    }

    private fun markInstalled(packId: String) {
        val installed = getInstalledPacks().toMutableSet()
        installed.add(packId)
        prefs.edit().putStringSet("installed", installed).apply()
    }

    /**
     * 產生基礎詞庫 — 當遠端和 assets 都沒有時的降級方案。
     * 包含各語言最常用的基礎詞彙。
     */
    private fun generateBasicDict(pack: LanguagePack, outFile: File) {
        val words = when (pack.id) {
            "de" -> BASIC_GERMAN
            "fr" -> BASIC_FRENCH
            "es" -> BASIC_SPANISH
            "vi" -> BASIC_VIETNAMESE
            "it" -> BASIC_ITALIAN
            else -> emptyList()
        }
        outFile.writeText(words.joinToString("\n"))
    }

    companion object {
        private const val TAG = "LangPackMgr"

        @Volatile private var instance: LanguagePackManager? = null

        fun getInstance(context: Context): LanguagePackManager {
            return instance ?: synchronized(this) {
                instance ?: LanguagePackManager(context.applicationContext).also { instance = it }
            }
        }

        // ═════════════════════════════════════════════
        // 基礎詞庫（各語言 ~500 高頻詞）
        // ═════════════════════════════════════════════

        private val BASIC_GERMAN = listOf(
            "der","die","und","in","den","von","zu","das","mit","sich",
            "des","auf","für","ist","im","dem","nicht","ein","eine","als",
            "auch","es","an","werden","aus","er","hat","dass","sie","nach",
            "wird","bei","einer","um","am","sind","noch","wie","einem","über",
            "so","zum","war","haben","nur","oder","aber","vor","zur","bis",
            "mehr","durch","man","sein","wurde","sei","können","diese","einem","ihr",
            "dann","unter","wir","soll","ich","was","vom","können","schon","wenn",
            "habe","seine","ihre","nach","noch","machen","gibt","alle","seit","muss",
            "doch","jetzt","drei","mal","gegen","sagen","will","zwischen","immer",
            "wieder","gehen","gut","kommen","können","lassen","stehen","machen","sehen",
            "geben","nehmen","finden","denken","bleiben","halten","tragen","sprechen",
            "bringen","fahren","lesen","schreiben","lernen","arbeiten","spielen",
            "wissen","heißen","helfen","brauchen","glauben","verstehen","leben","lieben",
            "kaufen","essen","trinken","schlafen","wohnen","suchen","zeigen","führen",
            "fragen","legen","setzen","stellen","ziehen","fallen","folgen","beginnen",
            "Haus","Mann","Frau","Kind","Zeit","Tag","Welt","Land","Stadt","Wasser",
            "Arbeit","Schule","Geld","Auto","Straße","Buch","Mensch","Jahr","Hand",
            "Kopf","Auge","Freund","Mutter","Vater","Bruder","Schwester","Familie",
            "danke","bitte","ja","nein","hallo","morgen","heute","gestern","gut",
            "schlecht","groß","klein","alt","neu","schnell","langsam","richtig","falsch",
            "Montag","Dienstag","Mittwoch","Donnerstag","Freitag","Samstag","Sonntag",
            "Januar","Februar","März","April","Mai","Juni","Juli","August","September",
            "Oktober","November","Dezember","Frühling","Sommer","Herbst","Winter",
            "rot","blau","grün","gelb","weiß","schwarz","braun","grau",
            "eins","zwei","drei","vier","fünf","sechs","sieben","acht","neun","zehn",
            "hundert","tausend","Million","Milliarde",
            "Entschuldigung","Wiedersehen","Tschüss","willkommen","Geburtstag",
            "Weihnachten","Ostern","Urlaub","Reise","Flughafen","Bahnhof",
            "Krankenhaus","Restaurant","Supermarkt","Apotheke","Kirche","Museum",
            "Kindergarten","Universität","Bibliothek","Polizei","Feuerwehr"
        )

        private val BASIC_FRENCH = listOf(
            "le","de","un","être","et","en","avoir","que","pour","dans",
            "ce","il","qui","ne","sur","se","pas","plus","pouvoir","par",
            "je","avec","tout","faire","son","mettre","autre","on","mais","nous",
            "comme","ou","si","leur","dire","elle","devoir","avant","deux","même",
            "prendre","aussi","celui","donner","bien","où","fois","vous","encore",
            "nouveau","aller","ceux","entre","premier","vouloir","falloir","voir",
            "grand","mon","savoir","sans","venir","quel","après","trouver","vie",
            "très","jour","homme","monde","temps","femme","petit","bon","maison",
            "pays","gens","enfant","main","chose","eau","travail","école","ami",
            "famille","père","mère","frère","sœur","fille","fils","mari","femme",
            "merci","bonjour","bonsoir","salut","oui","non","comment","pourquoi",
            "quand","combien","pardon","excusez","bienvenue","félicitations",
            "lundi","mardi","mercredi","jeudi","vendredi","samedi","dimanche",
            "janvier","février","mars","avril","mai","juin","juillet","août",
            "septembre","octobre","novembre","décembre","printemps","été","automne","hiver",
            "rouge","bleu","vert","jaune","blanc","noir","brun","gris","rose","orange",
            "un","deux","trois","quatre","cinq","six","sept","huit","neuf","dix",
            "cent","mille","million","milliard",
            "manger","boire","dormir","parler","écrire","lire","jouer","acheter",
            "vendre","ouvrir","fermer","aimer","penser","croire","comprendre",
            "apprendre","connaître","vivre","mourir","naître","rester","partir",
            "arriver","monter","descendre","entrer","sortir","tomber","courir",
            "marcher","nager","chanter","danser","cuisiner","conduire","voyager",
            "aéroport","gare","hôpital","restaurant","supermarché","pharmacie",
            "église","musée","bibliothèque","université","police","pompier"
        )

        private val BASIC_SPANISH = listOf(
            "de","la","que","el","en","y","a","los","del","se",
            "las","por","un","para","con","no","una","su","al","lo",
            "como","más","pero","sus","le","ya","o","este","ha","si",
            "porque","esta","entre","cuando","muy","sin","sobre","también","me",
            "hasta","hay","donde","quien","desde","todo","nos","durante","todos",
            "uno","les","ni","contra","otros","ese","eso","ante","ellos","ella",
            "dos","hacer","poder","decir","tener","ir","ver","dar","saber","querer",
            "llegar","pasar","deber","poner","parecer","quedar","creer","hablar",
            "llevar","dejar","seguir","encontrar","llamar","venir","pensar","salir",
            "volver","tomar","conocer","vivir","sentir","tratar","mirar","contar",
            "empezar","esperar","buscar","existir","entrar","trabajar","escribir",
            "perder","producir","ocurrir","entender","pedir","recibir","recordar",
            "hombre","mujer","niño","niña","tiempo","día","mundo","vida","casa",
            "país","agua","trabajo","escuela","amigo","familia","padre","madre",
            "hermano","hermana","hijo","hija","esposo","esposa",
            "gracias","hola","adiós","sí","no","cómo","por qué","cuándo","cuánto",
            "perdón","bienvenido","felicidades","bueno","malo","grande","pequeño",
            "lunes","martes","miércoles","jueves","viernes","sábado","domingo",
            "enero","febrero","marzo","abril","mayo","junio","julio","agosto",
            "septiembre","octubre","noviembre","diciembre","primavera","verano","otoño","invierno",
            "rojo","azul","verde","amarillo","blanco","negro","marrón","gris",
            "uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez",
            "cien","mil","millón",
            "comer","beber","dormir","hablar","leer","jugar","comprar","vender",
            "abrir","cerrar","amar","correr","caminar","nadar","cantar","bailar",
            "cocinar","conducir","viajar","aeropuerto","estación","hospital",
            "restaurante","supermercado","farmacia","iglesia","museo","biblioteca",
            "universidad","policía","bombero"
        )

        private val BASIC_VIETNAMESE = listOf(
            "và","của","có","là","được","cho","không","trong","đã","với",
            "người","một","những","này","các","để","theo","về","từ","tại",
            "như","hay","cũng","khi","trên","còn","đó","nào","ra","rất",
            "nhiều","nhưng","vì","thì","nếu","tôi","bạn","anh","chị","em",
            "họ","chúng","ta","mình","ông","bà","nó","đây","ấy","sẽ",
            "làm","đi","đến","biết","nói","ăn","uống","ngủ","đọc","viết",
            "học","chơi","mua","bán","mở","đóng","yêu","thương","nghĩ","hiểu",
            "sống","chết","sinh","chạy","đi bộ","bơi","hát","nhảy","nấu","lái",
            "nhà","nước","thời gian","ngày","thế giới","đời","đất nước","thành phố",
            "trường","bạn bè","gia đình","bố","mẹ","anh trai","chị gái","con trai",
            "con gái","chồng","vợ","công việc","tiền","xe","đường","sách",
            "cảm ơn","xin chào","tạm biệt","vâng","không","thế nào","tại sao",
            "khi nào","bao nhiêu","xin lỗi","chào mừng",
            "thứ hai","thứ ba","thứ tư","thứ năm","thứ sáu","thứ bảy","chủ nhật",
            "tháng một","tháng hai","tháng ba","tháng tư","tháng năm","tháng sáu",
            "tháng bảy","tháng tám","tháng chín","tháng mười","tháng mười một","tháng mười hai",
            "xuân","hạ","thu","đông",
            "đỏ","xanh","lục","vàng","trắng","đen","nâu","xám","hồng","cam",
            "một","hai","ba","bốn","năm","sáu","bảy","tám","chín","mười",
            "trăm","nghìn","triệu","tỷ",
            "tốt","xấu","lớn","nhỏ","cũ","mới","nhanh","chậm","đúng","sai",
            "sân bay","ga","bệnh viện","nhà hàng","siêu thị","nhà thuốc",
            "nhà thờ","bảo tàng","thư viện","đại học","cảnh sát","cứu hỏa"
        )

        private val BASIC_ITALIAN = listOf(
            "di","e","il","la","che","un","è","per","in","una",
            "sono","mi","si","ho","ma","lo","ha","le","non","con",
            "ci","questo","da","come","io","suo","al","più","del","se",
            "qui","era","delle","anche","molto","tutti","nella","dei","gli","nel",
            "alla","lei","lui","essere","avere","fare","dire","potere","volere","dovere",
            "andare","venire","dare","stare","sapere","vedere","prendere","trovare",
            "parlare","mangiare","bere","dormire","leggere","scrivere","giocare",
            "comprare","vendere","aprire","chiudere","amare","pensare","capire",
            "vivere","morire","nascere","correre","camminare","nuotare","cantare",
            "ballare","cucinare","guidare","viaggiare","lavorare","studiare",
            "uomo","donna","bambino","bambina","tempo","giorno","mondo","vita","casa",
            "paese","acqua","lavoro","scuola","amico","famiglia","padre","madre",
            "fratello","sorella","figlio","figlia","marito","moglie",
            "grazie","ciao","buongiorno","buonasera","arrivederci","sì","no",
            "come","perché","quando","quanto","scusa","benvenuto","auguri",
            "buono","cattivo","grande","piccolo","vecchio","nuovo","veloce","lento",
            "lunedì","martedì","mercoledì","giovedì","venerdì","sabato","domenica",
            "gennaio","febbraio","marzo","aprile","maggio","giugno","luglio","agosto",
            "settembre","ottobre","novembre","dicembre","primavera","estate","autunno","inverno",
            "rosso","blu","verde","giallo","bianco","nero","marrone","grigio","rosa","arancione",
            "uno","due","tre","quattro","cinque","sei","sette","otto","nove","dieci",
            "cento","mille","milione","miliardo",
            "aeroporto","stazione","ospedale","ristorante","supermercato","farmacia",
            "chiesa","museo","biblioteca","università","polizia","pompiere",
            "bella","bravo","prego","basta","magari","forse","sempre","mai","spesso",
            "poco","tanto","troppo","insieme","solo","tutto","niente","qualcosa"
        )
    }
}

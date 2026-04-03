package com.snowrealm.yukiboard.store

import android.content.Context
import android.content.SharedPreferences
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 皮膚管理器 — 載入/套用/解鎖。
 *
 * 免費皮膚 3 款內建，Z幣皮膚從 Supabase 拉取。
 * 解鎖狀態存 SharedPreferences。
 */
class SkinManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yukiboard_skins", Context.MODE_PRIVATE)

    private val _ownedIds = mutableSetOf<String>()
    private var _activeSkinId: String = DEFAULT_SKIN_ID

    init {
        _ownedIds.addAll(
            prefs.getStringSet(KEY_OWNED, FREE_SKIN_IDS) ?: FREE_SKIN_IDS
        )
        _activeSkinId = prefs.getString(KEY_ACTIVE, DEFAULT_SKIN_ID) ?: DEFAULT_SKIN_ID
    }

    /** 目前啟用的皮膚 ID */
    val activeSkinId: String get() = _activeSkinId

    /** 取得所有可用皮膚（含解鎖狀態） */
    fun getAllSkins(): List<SkinPackage> {
        return BUILTIN_SKINS.map { it.copy(isOwned = _ownedIds.contains(it.id)) }
    }

    /** 按分類取得皮膚 */
    fun getSkinsByCategory(category: SkinCategory): List<SkinPackage> {
        return getAllSkins().filter { it.category == category }
    }

    /** 是否擁有此皮膚 */
    fun isOwned(skinId: String): Boolean = _ownedIds.contains(skinId)

    /** 解鎖皮膚（購買後呼叫） */
    fun unlock(skinId: String) {
        _ownedIds.add(skinId)
        prefs.edit().putStringSet(KEY_OWNED, _ownedIds).apply()
    }

    /** 套用皮膚 → 回傳對應的 KeyboardTheme */
    fun applySkin(skinId: String): KeyboardTheme? {
        if (!isOwned(skinId)) return null
        _activeSkinId = skinId
        prefs.edit().putString(KEY_ACTIVE, skinId).apply()
        return getSkinTheme(skinId)
    }

    /** 取得皮膚對應的主題 */
    fun getSkinTheme(skinId: String): KeyboardTheme? {
        return BUILTIN_SKINS.firstOrNull { it.id == skinId }?.themeConfig
    }

    /** 取得目前啟用的主題 */
    fun getActiveTheme(): KeyboardTheme {
        return getSkinTheme(_activeSkinId) ?: KeyboardTheme.default()
    }

    /** 新增遠端皮膚（從 Supabase 拉取後加入） */
    fun addRemoteSkins(skins: List<SkinPackage>) {
        // 遠端皮膚可以動態加入，但本版只用內建
    }

    companion object {
        private const val KEY_OWNED = "owned_skins"
        private const val KEY_ACTIVE = "active_skin"
        const val DEFAULT_SKIN_ID = "deep_space_blue"

        private val FREE_SKIN_IDS = setOf("deep_space_blue", "snow_field_white", "rin_sky_purple")

        /** 內建皮膚 — 3 免費 + 預留 Z幣皮膚 */
        val BUILTIN_SKINS = listOf(
            SkinPackage(
                id = "deep_space_blue",
                name = "深空藍",
                category = SkinCategory.FREE,
                themeConfig = KeyboardTheme(
                    id = "skin_deep_space_blue", displayName = "深空藍",
                    backgroundColorInt = 0xFF0D1B2A.toInt(),
                    keyColorInt = 0xFF1B2838.toInt(),
                    specialKeyColorInt = 0xFF152030.toInt(),
                    keyLabelColorInt = 0xFFE0E0E0.toInt(),
                    keyPressedColorInt = 0xFF2A4060.toInt(),
                    candidateBarColorInt = 0xFF0A1520.toInt(),
                    candidateTextColorInt = 0xFFBBDEFB.toInt(),
                    candidateSelectedColorInt = 0xFF4FC3F7.toInt(),
                    composingTextColorInt = 0xFF80D8FF.toInt(),
                    accentColorInt = 0xFF4FC3F7.toInt(),
                    flowLightAlpha = 0.6f
                )
            ),
            SkinPackage(
                id = "snow_field_white",
                name = "雪域白",
                category = SkinCategory.FREE,
                themeConfig = KeyboardTheme(
                    id = "skin_snow_field_white", displayName = "雪域白",
                    backgroundColorInt = 0xFFF5F5F5.toInt(),
                    keyColorInt = 0xFFFFFFFF.toInt(),
                    specialKeyColorInt = 0xFFE0E0E0.toInt(),
                    keyLabelColorInt = 0xFF212121.toInt(),
                    keyPressedColorInt = 0xFFBBDEFB.toInt(),
                    candidateBarColorInt = 0xFFEEEEEE.toInt(),
                    candidateTextColorInt = 0xFF424242.toInt(),
                    candidateSelectedColorInt = 0xFF42A5F5.toInt(),
                    composingTextColorInt = 0xFF1565C0.toInt(),
                    accentColorInt = 0xFF42A5F5.toInt(),
                    flowLightAlpha = 0.3f, isDark = false
                )
            ),
            SkinPackage(
                id = "rin_sky_purple",
                name = "凜空紫",
                category = SkinCategory.FREE,
                themeConfig = KeyboardTheme(
                    id = "skin_rin_sky_purple", displayName = "凜空紫",
                    backgroundColorInt = 0xFF1A0A2E.toInt(),
                    keyColorInt = 0xFF2D1B4E.toInt(),
                    specialKeyColorInt = 0xFF231040.toInt(),
                    keyLabelColorInt = 0xFFE1BEE7.toInt(),
                    keyPressedColorInt = 0xFF4A2878.toInt(),
                    candidateBarColorInt = 0xFF120820.toInt(),
                    candidateTextColorInt = 0xFFE1BEE7.toInt(),
                    candidateSelectedColorInt = 0xFFCE93D8.toInt(),
                    composingTextColorInt = 0xFFBA68C8.toInt(),
                    accentColorInt = 0xFFCE93D8.toInt(),
                    flowLightAlpha = 0.7f
                )
            ),
            SkinPackage(
                id = "sakura_pink",
                name = "櫻花粉",
                category = SkinCategory.ZCOIN,
                price = 200,
                themeConfig = KeyboardTheme(
                    id = "skin_sakura_pink", displayName = "櫻花粉",
                    backgroundColorInt = 0xFF2D1B2E.toInt(),
                    keyColorInt = 0xFF3D2B3E.toInt(),
                    specialKeyColorInt = 0xFF332030.toInt(),
                    keyLabelColorInt = 0xFFF8BBD0.toInt(),
                    keyPressedColorInt = 0xFF5A3058.toInt(),
                    candidateBarColorInt = 0xFF201020.toInt(),
                    candidateTextColorInt = 0xFFF48FB1.toInt(),
                    candidateSelectedColorInt = 0xFFFF80AB.toInt(),
                    composingTextColorInt = 0xFFFF4081.toInt(),
                    accentColorInt = 0xFFFF80AB.toInt(),
                    flowLightAlpha = 0.5f
                )
            ),
            SkinPackage(
                id = "cyber_neon",
                name = "賽博霓虹",
                category = SkinCategory.ZCOIN,
                price = 300,
                themeConfig = KeyboardTheme(
                    id = "skin_cyber_neon", displayName = "賽博霓虹",
                    backgroundColorInt = 0xFF0A0A0A.toInt(),
                    keyColorInt = 0xFF1A1A2E.toInt(),
                    specialKeyColorInt = 0xFF12122A.toInt(),
                    keyLabelColorInt = 0xFF00E5FF.toInt(),
                    keyPressedColorInt = 0xFF3A1050.toInt(),
                    candidateBarColorInt = 0xFF08081A.toInt(),
                    candidateTextColorInt = 0xFF76FF03.toInt(),
                    candidateSelectedColorInt = 0xFFFF1744.toInt(),
                    composingTextColorInt = 0xFF00E5FF.toInt(),
                    accentColorInt = 0xFFFF1744.toInt(),
                    flowLightAlpha = 0.9f
                )
            ),
            SkinPackage(
                id = "aurora_night",
                name = "極光之夜",
                category = SkinCategory.LIMITED,
                price = 500,
                themeConfig = KeyboardTheme(
                    id = "skin_aurora_night", displayName = "極光之夜",
                    backgroundColorInt = 0xFF0B1929.toInt(),
                    keyColorInt = 0xFF142640.toInt(),
                    specialKeyColorInt = 0xFF0F1E35.toInt(),
                    keyLabelColorInt = 0xFFB2FF59.toInt(),
                    keyPressedColorInt = 0xFF1B4030.toInt(),
                    candidateBarColorInt = 0xFF081018.toInt(),
                    candidateTextColorInt = 0xFFA7FFEB.toInt(),
                    candidateSelectedColorInt = 0xFF64FFDA.toInt(),
                    composingTextColorInt = 0xFF1DE9B6.toInt(),
                    accentColorInt = 0xFF64FFDA.toInt(),
                    flowLightAlpha = 0.8f
                )
            )
        )
    }
}

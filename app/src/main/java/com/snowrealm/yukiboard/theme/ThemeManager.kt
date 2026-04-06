package com.snowrealm.yukiboard.theme

import android.content.Context
import android.content.SharedPreferences

/**
 * 主題管理器（單例）。
 *
 * 職責：
 * - 持有所有可用主題清單
 * - 讀寫目前選擇的主題 ID（存入 SharedPreferences）
 * - 提供目前主題的 [KeyboardTheme] 物件
 */
class ThemeManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val availableThemes: List<KeyboardTheme> = KeyboardTheme.all()

    var currentTheme: KeyboardTheme
        get() {
            val id = prefs.getString(KEY_THEME_ID, KeyboardTheme.default().id)
            return availableThemes.firstOrNull { it.id == id } ?: KeyboardTheme.default()
        }
        set(value) {
            prefs.edit().putString(KEY_THEME_ID, value.id).apply()
            themeChangedListeners.forEach { it(value) }
        }

    private val themeChangedListeners = mutableListOf<(KeyboardTheme) -> Unit>()

    fun addOnThemeChangedListener(listener: (KeyboardTheme) -> Unit) {
        themeChangedListeners.add(listener)
    }

    fun removeOnThemeChangedListener(listener: (KeyboardTheme) -> Unit) {
        themeChangedListeners.remove(listener)
    }

    fun setOnThemeChangedListener(listener: (KeyboardTheme) -> Unit) {
        themeChangedListeners.clear()
        themeChangedListeners.add(listener)
    }

    companion object {
        private const val PREFS_NAME  = "yukiboard_theme"
        private const val KEY_THEME_ID = "theme_id"

        @Volatile private var INSTANCE: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}

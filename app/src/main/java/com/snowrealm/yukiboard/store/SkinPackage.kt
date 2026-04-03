package com.snowrealm.yukiboard.store

import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 皮膚包資料模型。
 */
data class SkinPackage(
    val id: String,
    val name: String,
    val description: String = "",
    val previewUrl: String = "",
    val price: Int = 0,               // 0 = 免費
    val category: SkinCategory = SkinCategory.FREE,
    val themeConfig: KeyboardTheme? = null,
    val isOwned: Boolean = false
)

enum class SkinCategory(val displayName: String) {
    FREE("免費"),
    ZCOIN("Z幣"),
    LIMITED("限定")
}

package com.snowrealm.yukiboard.app.ime

import android.content.Context
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.panel.ThemePanelView

/**
 * Sprint 6-3：THEME panel 最小邊界 binder。
 *
 * 只負責 [ThemePanelView] 建立、初始狀態（theme / currentThemeId / availableThemes）
 * snapshot 與 11 個 UI callback wiring。
 *
 * 不直接依賴 `ThemeManager` / `KeyboardBackgroundView` / `BackgroundPickerActivity` 的細節：
 * ThemeManager listener 生命週期（addOnThemeChangedListener / removeOnThemeChangedListener）
 * 刻意留在 service 端，避免 binder 反向引入 `ThemeManager.kt`（Sprint 6-3 禁區）。
 *
 * `previewBgBitmap` 初始載入由呼叫端在取得 panel 後自行呼叫 `reloadPreviewBitmap()`，
 * 保留 panel 內部既有 load 路徑不動。
 */
object ImeThemePanelBinder {

    fun bind(
        context: Context,
        theme: KeyboardTheme,
        currentThemeId: String,
        availableThemes: List<KeyboardTheme>,
        onThemeSelected: (KeyboardTheme) -> Unit,
        onAnimThemeSelected: (String) -> Unit,
        onPickBackground: () -> Unit,
        onPickVideo: () -> Unit,
        onClearBackground: () -> Unit,
        onOverlayAlphaChanged: (Int) -> Unit,
        onLabelColorChanged: (Int) -> Unit,
        onBackgroundSettingsChanged: () -> Unit,
        onKeySettingsChanged: () -> Unit,
        onPerKeySizeChanged: () -> Unit,
        onKeyboardHeightChanged: () -> Unit,
    ): ThemePanelView {
        val panel = ThemePanelView(context)
        panel.theme = theme
        panel.currentThemeId = currentThemeId
        panel.availableThemes = availableThemes
        panel.onThemeSelected = onThemeSelected
        panel.onAnimThemeSelected = onAnimThemeSelected
        panel.onPickBackground = onPickBackground
        panel.onPickVideo = onPickVideo
        panel.onClearBackground = onClearBackground
        panel.onOverlayAlphaChanged = onOverlayAlphaChanged
        panel.onLabelColorChanged = onLabelColorChanged
        panel.onBackgroundSettingsChanged = onBackgroundSettingsChanged
        panel.onKeySettingsChanged = onKeySettingsChanged
        panel.onPerKeySizeChanged = onPerKeySizeChanged
        panel.onKeyboardHeightChanged = onKeyboardHeightChanged
        return panel
    }
}

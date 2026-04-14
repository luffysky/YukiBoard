package com.snowrealm.yukiboard.app.ime

import com.snowrealm.yukiboard.animation.gles.KeyboardGLSurfaceView
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.keyboard.KeyboardView
import com.snowrealm.yukiboard.ui.panel.ToolbarView

/**
 * 收斂 IME runtime theme 套用時的 render 同步。
 *
 * 目前只處理既有 applyTheme() 內的同步步驟，不改初始化流程與外部語意。
 */
class ImeThemeRenderDelegate {

    fun applyTheme(
        theme: KeyboardTheme,
        toolbarAnimStyle: String,
        refreshThemeState: () -> Unit,
        toolbarView: ToolbarView,
        keyboardView: KeyboardView,
        glSurfaceView: KeyboardGLSurfaceView?,
        applyParticleTheme: (KeyboardTheme) -> Unit,
    ) {
        refreshThemeState()
        toolbarView.theme = theme
        toolbarView.setAnimStyle(toolbarAnimStyle)
        keyboardView.setAnimStyle(toolbarAnimStyle)
        keyboardView.applyTheme(theme)
        glSurfaceView?.setFlowAlpha(theme.flowLightAlpha)
        glSurfaceView?.setFlowColor(theme.flowLightColorInt)
        applyParticleTheme(theme)
    }
}

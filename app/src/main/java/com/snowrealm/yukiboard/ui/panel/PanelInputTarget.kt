package com.snowrealm.yukiboard.ui.panel

/**
 * 可接收鍵盤輸入的面板介面。
 * 所有支援輸入的面板都實作此介面，讓 IMEService 統一路由。
 */
interface PanelInputTarget {
    val isInInputMode: Boolean

    fun onCharInput(text: String)
    fun onDeleteInput()
    fun onSpaceInput()
    fun onEnterInput()
    fun onComposedTextInput(text: String)
    fun updateComposingHint(hint: String)
    fun exitInputMode()
    fun enterInputMode()
}

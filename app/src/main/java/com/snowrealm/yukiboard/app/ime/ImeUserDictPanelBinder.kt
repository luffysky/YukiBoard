package com.snowrealm.yukiboard.app.ime

import android.app.AlertDialog
import android.content.Context
import com.snowrealm.yukiboard.input.dictionary.UserDictionary
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.panel.UserDictionaryPanelView

/**
 * Sprint 6-4：USER_DICT panel 最小邊界 binder。
 *
 * 只負責 [UserDictionaryPanelView] 建立、主題與 [UserDictionary] 實例掛接、
 * 3 個 callback（onShowDialog / onExportRequest / onImportRequest）wiring。
 *
 * 不直接依賴 `UserDictionary` 的內部 API（只接受既建構實例）、不啟動 file I/O、
 * 不觸碰 `CloudSyncManager` / `InputRouter` / settings。`enterInputMode()`
 * 由呼叫端在 panel 建立後自行呼叫，保留服務端對 input mode 生命週期的控制。
 */
object ImeUserDictPanelBinder {

    fun bind(
        context: Context,
        theme: KeyboardTheme,
        userDictionary: UserDictionary,
        onShowDialog: (AlertDialog) -> Unit,
        onExportRequest: (String) -> Unit,
        onImportRequest: () -> Unit,
    ): UserDictionaryPanelView {
        val panel = UserDictionaryPanelView(context)
        panel.theme = theme
        panel.userDictionary = userDictionary
        panel.onShowDialog = onShowDialog
        panel.onExportRequest = onExportRequest
        panel.onImportRequest = onImportRequest
        return panel
    }
}

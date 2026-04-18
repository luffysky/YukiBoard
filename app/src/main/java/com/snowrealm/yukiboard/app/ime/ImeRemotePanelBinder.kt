package com.snowrealm.yukiboard.app.ime

import android.content.Context
import com.snowrealm.yukiboard.remote.AuthLevel
import com.snowrealm.yukiboard.remote.ConnectionState
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.panel.RemoteKeyboardPanelView

/**
 * Sprint 6-2：REMOTE panel 最小邊界 binder。
 *
 * 只負責 [RemoteKeyboardPanelView] 建立、主題掛接與初始狀態 / toggle callback wiring。
 * 不直接依賴 `BluetoothHidKeyboard` / `WifiKeyboardServer` / `RemoteKeyboardService` 的內部細節：
 * 初始狀態由呼叫端在 snapshot 時讀好再傳入，toggle 動作由 `onBluetoothToggle` / `onWifiToggle` 注入。
 *
 * 連線狀態變化的即時更新仍由 service 端透過 `updateRemotePanelState()` 寫入
 * `ImePanelBindings.remotePanel`，此 binder 不接 listener，避免重複 writer。
 */
object ImeRemotePanelBinder {

    fun bind(
        context: Context,
        theme: KeyboardTheme,
        btState: ConnectionState,
        wifiState: ConnectionState,
        wifiUrl: String,
        wifiPin: String,
        authLevel: AuthLevel,
        connectedDeviceName: String,
        onBluetoothToggle: () -> Unit,
        onWifiToggle: () -> Unit,
    ): RemoteKeyboardPanelView {
        val panel = RemoteKeyboardPanelView(context)
        panel.theme = theme
        panel.btState = btState
        panel.wifiState = wifiState
        panel.wifiUrl = wifiUrl
        panel.wifiPin = wifiPin
        panel.authLevel = authLevel
        panel.connectedDeviceName = connectedDeviceName
        panel.onBluetoothToggle = onBluetoothToggle
        panel.onWifiToggle = onWifiToggle
        return panel
    }
}

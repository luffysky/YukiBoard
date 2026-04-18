package com.snowrealm.yukiboard.remote

/**
 * 遠端鍵盤服務介面 — 抽象化 Bluetooth HID 和 WiFi WebSocket 兩種傳輸方式。
 *
 * 使用情境：
 * 1. 手機作為藍牙鍵盤 → 連接電腦/平板，手機打字直接輸出到對方
 * 2. WiFi 區網模式 → 手機開 WebSocket Server，電腦瀏覽器連線接收按鍵
 * 3. 反向模式 → 電腦打字傳到手機（WebSocket 雙向）
 */
interface RemoteKeyboardService {

    /** 目前連線狀態 */
    val connectionState: ConnectionState

    /** 連線中的裝置資訊 */
    val connectedDevice: RemoteDevice?

    /** 此傳輸當前實施的驗證等級 */
    val authLevel: AuthLevel

    /** 啟動服務（開始可被發現/監聽） */
    fun start()

    /** 停止服務 */
    fun stop()

    /** 傳送按鍵事件 */
    fun sendKeyEvent(event: RemoteKeyEvent)

    /** 傳送文字（批次） */
    fun sendText(text: String)

    /** 設定狀態變更回呼 */
    fun setOnStateChangedListener(listener: (ConnectionState) -> Unit)

    /** 設定收到遠端按鍵回呼（反向模式） */
    fun setOnRemoteKeyListener(listener: (RemoteKeyEvent) -> Unit)

    /** 設定收到遠端文字回呼 */
    fun setOnRemoteTextListener(listener: (String) -> Unit)

    /** 釋放資源 */
    fun destroy()
}

/** 連線狀態 */
enum class ConnectionState {
    IDLE,           // 未啟動
    DISCOVERING,    // 藍牙搜尋中
    LISTENING,      // 等待連線（WiFi Server 模式）
    CONNECTING,     // 連線中
    CONNECTED,      // 已連線
    DISCONNECTED,   // 已斷線
    ERROR           // 錯誤
}

/** 遠端裝置資訊 */
data class RemoteDevice(
    val name: String,
    val address: String,      // MAC 或 IP:port
    val type: RemoteType
)

enum class RemoteType {
    BLUETOOTH,
    WIFI
}

enum class AuthLevel {
    NONE,
    PIN,
    PIN_PLUS_SESSION,
    PAIRED_ONLY
}

/** 遠端按鍵事件 */
data class RemoteKeyEvent(
    val keyCode: Int,           // Android KeyEvent keyCode
    val action: KeyAction,
    val modifiers: Int = 0,     // META_CTRL_ON | META_SHIFT_ON 等
    val character: Char? = null // 可選：直接傳字元
)

enum class KeyAction {
    DOWN, UP, PRESS  // PRESS = DOWN + UP
}

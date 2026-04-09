package com.snowrealm.yukiboard.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * 藍牙 HID 鍵盤服務 — 將手機模擬為藍牙鍵盤。
 *
 * 使用 [BluetoothHidDevice] API（Android 9+, API 28+）。
 * 手機作為 HID Device，電腦/平板作為 HID Host。
 *
 * ## 使用流程
 * 1. start() → 註冊 BluetoothHidDevice app
 * 2. 電腦端搜尋並配對手機
 * 3. 配對成功 → onConnectionStateChanged → CONNECTED
 * 4. sendKeyEvent() / sendText() → 透過 HID report 傳送按鍵
 * 5. stop() → 取消註冊
 *
 * ## HID Report Descriptor
 * 標準 keyboard report：8 bytes
 * - byte 0: modifier keys (Ctrl, Shift, Alt, GUI)
 * - byte 1: reserved (0x00)
 * - byte 2-7: up to 6 simultaneous key codes
 */
@SuppressLint("MissingPermission")
class BluetoothHidKeyboard(context: Context) : RemoteKeyboardService {

    companion object {
        private const val TAG = "BtHidKeyboard"

        // HID Report Descriptor for a standard keyboard
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(),  // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(),  // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(),  // Collection (Application)

            // Modifier keys (8 bits)
            0x05.toByte(), 0x07.toByte(),  //   Usage Page (Key Codes)
            0x19.toByte(), 0xE0.toByte(),  //   Usage Minimum (224) - Left Ctrl
            0x29.toByte(), 0xE7.toByte(),  //   Usage Maximum (231) - Right GUI
            0x15.toByte(), 0x00.toByte(),  //   Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(),  //   Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(),  //   Report Size (1)
            0x95.toByte(), 0x08.toByte(),  //   Report Count (8)
            0x81.toByte(), 0x02.toByte(),  //   Input (Data, Variable, Absolute)

            // Reserved byte
            0x75.toByte(), 0x08.toByte(),  //   Report Size (8)
            0x95.toByte(), 0x01.toByte(),  //   Report Count (1)
            0x81.toByte(), 0x01.toByte(),  //   Input (Constant)

            // LED output (5 bits)
            0x05.toByte(), 0x08.toByte(),  //   Usage Page (LEDs)
            0x19.toByte(), 0x01.toByte(),  //   Usage Minimum (1)
            0x29.toByte(), 0x05.toByte(),  //   Usage Maximum (5)
            0x75.toByte(), 0x01.toByte(),  //   Report Size (1)
            0x95.toByte(), 0x05.toByte(),  //   Report Count (5)
            0x91.toByte(), 0x02.toByte(),  //   Output (Data, Variable, Absolute)

            // LED padding (3 bits)
            0x75.toByte(), 0x03.toByte(),  //   Report Size (3)
            0x95.toByte(), 0x01.toByte(),  //   Report Count (1)
            0x91.toByte(), 0x01.toByte(),  //   Output (Constant)

            // Key array (6 keys)
            0x05.toByte(), 0x07.toByte(),  //   Usage Page (Key Codes)
            0x19.toByte(), 0x00.toByte(),  //   Usage Minimum (0)
            0x29.toByte(), 0xFF.toByte(),  //   Usage Maximum (255)
            0x15.toByte(), 0x00.toByte(),  //   Logical Minimum (0)
            0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), // Logical Maximum (255)
            0x75.toByte(), 0x08.toByte(),  //   Report Size (8)
            0x95.toByte(), 0x06.toByte(),  //   Report Count (6)
            0x81.toByte(), 0x00.toByte(),  //   Input (Data, Array)

            0xC0.toByte()                  // End Collection
        )
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val btAdapter: BluetoothAdapter? = btManager?.adapter

    @Volatile private var hidDevice: BluetoothHidDevice? = null
    @Volatile private var hostDevice: BluetoothDevice? = null
    private var stateListener: ((ConnectionState) -> Unit)? = null
    private var remoteKeyListener: ((RemoteKeyEvent) -> Unit)? = null
    private var remoteTextListener: ((String) -> Unit)? = null

    override var connectionState: ConnectionState = ConnectionState.IDLE
        private set(value) {
            field = value
            handler.post { stateListener?.invoke(value) }
        }

    override val connectedDevice: RemoteDevice?
        get() = hostDevice?.let {
            RemoteDevice(
                name = it.name ?: "Unknown",
                address = it.address,
                type = RemoteType.BLUETOOTH
            )
        }

    // BluetoothHidDevice 回呼
    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "App registered: $registered, device: ${pluggedDevice?.name}")
            if (registered) {
                connectionState = ConnectionState.LISTENING
            } else {
                connectionState = ConnectionState.IDLE
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "Connection state: $state, device: ${device?.name}")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    hostDevice = device
                    connectionState = ConnectionState.CONNECTED
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    hostDevice = null
                    connectionState = ConnectionState.DISCONNECTED
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    connectionState = ConnectionState.CONNECTING
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            // Host 請求 report → 回傳空報告
            hidDevice?.replyReport(device, type, id, ByteArray(8))
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            // Host 設定 LED 狀態等，目前忽略
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            // 收到 Host 端的資料（少見）
        }
    }

    // BluetoothProfile.ServiceListener
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as? BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                connectionState = ConnectionState.IDLE
            }
        }
    }

    override fun start() {
        if (btAdapter == null || !btAdapter.isEnabled) {
            connectionState = ConnectionState.ERROR
            Log.e(TAG, "Bluetooth not available or not enabled")
            return
        }

        connectionState = ConnectionState.CONNECTING
        btAdapter.getProfileProxy(appContext, profileListener, BluetoothProfile.HID_DEVICE)
    }

    private fun registerApp() {
        val hid = hidDevice ?: return

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "YukiBoard Keyboard",
            "SnowRealm Yuki Keyboard",
            "SnowRealm",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HID_REPORT_DESCRIPTOR
        )

        val registered = hid.registerApp(sdp, null, null, { it.run() }, hidCallback)
        if (!registered) {
            connectionState = ConnectionState.ERROR
            Log.e(TAG, "Failed to register HID app")
        }
    }

    override fun stop() {
        hidDevice?.unregisterApp()
        btAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
        hostDevice = null
        connectionState = ConnectionState.IDLE
    }

    /**
     * 傳送按鍵事件到 HID Host。
     *
     * HID keyboard report 格式（8 bytes）：
     * [modifier, 0x00, key1, key2, key3, key4, key5, key6]
     */
    override fun sendKeyEvent(event: RemoteKeyEvent) {
        val host = hostDevice ?: return
        val hid = hidDevice ?: return

        val hidKeyCode = androidKeyToHid(event.keyCode)
        val modifier = androidModifierToHid(event.modifiers)

        when (event.action) {
            KeyAction.DOWN, KeyAction.PRESS -> {
                // Key down report
                val report = byteArrayOf(
                    modifier.toByte(), 0x00,
                    hidKeyCode.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00
                )
                hid.sendReport(host, 0, report)

                if (event.action == KeyAction.PRESS) {
                    // Key up report (全部釋放)
                    handler.postDelayed({
                        val releaseReport = ByteArray(8)
                        hid.sendReport(host, 0, releaseReport)
                    }, 20)
                }
            }
            KeyAction.UP -> {
                val releaseReport = ByteArray(8)
                hid.sendReport(host, 0, releaseReport)
            }
        }
    }

    override fun sendText(text: String) {
        val host = hostDevice ?: return
        val hid = hidDevice ?: return

        // 逐字元傳送
        text.forEachIndexed { index, char ->
            handler.postDelayed({
                val (hidKey, needShift) = charToHidKey(char)
                if (hidKey != 0) {
                    val modifier = if (needShift) 0x02 else 0x00 // Left Shift
                    val report = byteArrayOf(
                        modifier.toByte(), 0x00,
                        hidKey.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00
                    )
                    hid.sendReport(host, 0, report)

                    // 釋放
                    handler.postDelayed({
                        hid.sendReport(host, 0, ByteArray(8))
                    }, 15)
                }
            }, index * 35L) // 35ms 間隔，避免太快
        }
    }

    override fun setOnStateChangedListener(listener: (ConnectionState) -> Unit) {
        stateListener = listener
    }

    override fun setOnRemoteKeyListener(listener: (RemoteKeyEvent) -> Unit) {
        remoteKeyListener = listener
    }

    override fun setOnRemoteTextListener(listener: (String) -> Unit) {
        remoteTextListener = listener
    }

    override fun destroy() {
        handler.removeCallbacksAndMessages(null)
        stop()
        stateListener = null
        remoteKeyListener = null
        remoteTextListener = null
    }

    // =========================================================================
    // Android KeyCode → USB HID KeyCode 轉換
    // =========================================================================

    private fun androidKeyToHid(androidKeyCode: Int): Int {
        return when (androidKeyCode) {
            KeyEvent.KEYCODE_A -> 0x04
            KeyEvent.KEYCODE_B -> 0x05
            KeyEvent.KEYCODE_C -> 0x06
            KeyEvent.KEYCODE_D -> 0x07
            KeyEvent.KEYCODE_E -> 0x08
            KeyEvent.KEYCODE_F -> 0x09
            KeyEvent.KEYCODE_G -> 0x0A
            KeyEvent.KEYCODE_H -> 0x0B
            KeyEvent.KEYCODE_I -> 0x0C
            KeyEvent.KEYCODE_J -> 0x0D
            KeyEvent.KEYCODE_K -> 0x0E
            KeyEvent.KEYCODE_L -> 0x0F
            KeyEvent.KEYCODE_M -> 0x10
            KeyEvent.KEYCODE_N -> 0x11
            KeyEvent.KEYCODE_O -> 0x12
            KeyEvent.KEYCODE_P -> 0x13
            KeyEvent.KEYCODE_Q -> 0x14
            KeyEvent.KEYCODE_R -> 0x15
            KeyEvent.KEYCODE_S -> 0x16
            KeyEvent.KEYCODE_T -> 0x17
            KeyEvent.KEYCODE_U -> 0x18
            KeyEvent.KEYCODE_V -> 0x19
            KeyEvent.KEYCODE_W -> 0x1A
            KeyEvent.KEYCODE_X -> 0x1B
            KeyEvent.KEYCODE_Y -> 0x1C
            KeyEvent.KEYCODE_Z -> 0x1D
            KeyEvent.KEYCODE_1 -> 0x1E
            KeyEvent.KEYCODE_2 -> 0x1F
            KeyEvent.KEYCODE_3 -> 0x20
            KeyEvent.KEYCODE_4 -> 0x21
            KeyEvent.KEYCODE_5 -> 0x22
            KeyEvent.KEYCODE_6 -> 0x23
            KeyEvent.KEYCODE_7 -> 0x24
            KeyEvent.KEYCODE_8 -> 0x25
            KeyEvent.KEYCODE_9 -> 0x26
            KeyEvent.KEYCODE_0 -> 0x27
            KeyEvent.KEYCODE_ENTER -> 0x28
            KeyEvent.KEYCODE_ESCAPE -> 0x29
            KeyEvent.KEYCODE_DEL -> 0x2A        // Backspace
            KeyEvent.KEYCODE_TAB -> 0x2B
            KeyEvent.KEYCODE_SPACE -> 0x2C
            KeyEvent.KEYCODE_MINUS -> 0x2D
            KeyEvent.KEYCODE_EQUALS -> 0x2E
            KeyEvent.KEYCODE_LEFT_BRACKET -> 0x2F
            KeyEvent.KEYCODE_RIGHT_BRACKET -> 0x30
            KeyEvent.KEYCODE_BACKSLASH -> 0x31
            KeyEvent.KEYCODE_SEMICOLON -> 0x33
            KeyEvent.KEYCODE_APOSTROPHE -> 0x34
            KeyEvent.KEYCODE_GRAVE -> 0x35
            KeyEvent.KEYCODE_COMMA -> 0x36
            KeyEvent.KEYCODE_PERIOD -> 0x37
            KeyEvent.KEYCODE_SLASH -> 0x38
            KeyEvent.KEYCODE_CAPS_LOCK -> 0x39
            KeyEvent.KEYCODE_F1 -> 0x3A
            KeyEvent.KEYCODE_F2 -> 0x3B
            KeyEvent.KEYCODE_F3 -> 0x3C
            KeyEvent.KEYCODE_F4 -> 0x3D
            KeyEvent.KEYCODE_F5 -> 0x3E
            KeyEvent.KEYCODE_F6 -> 0x3F
            KeyEvent.KEYCODE_F7 -> 0x40
            KeyEvent.KEYCODE_F8 -> 0x41
            KeyEvent.KEYCODE_F9 -> 0x42
            KeyEvent.KEYCODE_F10 -> 0x43
            KeyEvent.KEYCODE_F11 -> 0x44
            KeyEvent.KEYCODE_F12 -> 0x45
            KeyEvent.KEYCODE_SYSRQ -> 0x46      // Print Screen
            KeyEvent.KEYCODE_SCROLL_LOCK -> 0x47
            KeyEvent.KEYCODE_BREAK -> 0x48       // Pause
            KeyEvent.KEYCODE_INSERT -> 0x49
            KeyEvent.KEYCODE_MOVE_HOME -> 0x4A
            KeyEvent.KEYCODE_PAGE_UP -> 0x4B
            KeyEvent.KEYCODE_FORWARD_DEL -> 0x4C // Delete
            KeyEvent.KEYCODE_MOVE_END -> 0x4D
            KeyEvent.KEYCODE_PAGE_DOWN -> 0x4E
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0x4F
            KeyEvent.KEYCODE_DPAD_LEFT -> 0x50
            KeyEvent.KEYCODE_DPAD_DOWN -> 0x51
            KeyEvent.KEYCODE_DPAD_UP -> 0x52
            else -> 0x00
        }
    }

    private fun androidModifierToHid(modifiers: Int): Int {
        var hid = 0
        if (modifiers and KeyEvent.META_CTRL_ON != 0) hid = hid or 0x01   // Left Ctrl
        if (modifiers and KeyEvent.META_SHIFT_ON != 0) hid = hid or 0x02  // Left Shift
        if (modifiers and KeyEvent.META_ALT_ON != 0) hid = hid or 0x04    // Left Alt
        if (modifiers and KeyEvent.META_META_ON != 0) hid = hid or 0x08   // Left GUI
        return hid
    }

    /**
     * 字元 → HID key code + 是否需要 Shift。
     */
    private fun charToHidKey(char: Char): Pair<Int, Boolean> {
        return when (char) {
            in 'a'..'z' -> (0x04 + (char - 'a')) to false
            in 'A'..'Z' -> (0x04 + (char - 'A')) to true
            in '0'..'9' -> {
                if (char == '0') 0x27 to false
                else (0x1E + (char - '1')) to false
            }
            ' ' -> 0x2C to false
            '\n' -> 0x28 to false
            '\t' -> 0x2B to false
            '-' -> 0x2D to false
            '=' -> 0x2E to false
            '[' -> 0x2F to false
            ']' -> 0x30 to false
            '\\' -> 0x31 to false
            ';' -> 0x33 to false
            '\'' -> 0x34 to false
            '`' -> 0x35 to false
            ',' -> 0x36 to false
            '.' -> 0x37 to false
            '/' -> 0x38 to false
            // Shifted symbols
            '!' -> 0x1E to true  // Shift+1
            '@' -> 0x1F to true  // Shift+2
            '#' -> 0x20 to true  // Shift+3
            '$' -> 0x21 to true  // Shift+4
            '%' -> 0x22 to true  // Shift+5
            '^' -> 0x23 to true  // Shift+6
            '&' -> 0x24 to true  // Shift+7
            '*' -> 0x25 to true  // Shift+8
            '(' -> 0x26 to true  // Shift+9
            ')' -> 0x27 to true  // Shift+0
            '_' -> 0x2D to true  // Shift+-
            '+' -> 0x2E to true  // Shift+=
            '{' -> 0x2F to true  // Shift+[
            '}' -> 0x30 to true  // Shift+]
            '|' -> 0x31 to true  // Shift+\
            ':' -> 0x33 to true  // Shift+;
            '"' -> 0x34 to true  // Shift+'
            '~' -> 0x35 to true  // Shift+`
            '<' -> 0x36 to true  // Shift+,
            '>' -> 0x37 to true  // Shift+.
            '?' -> 0x38 to true  // Shift+/
            else -> 0 to false
        }
    }
}

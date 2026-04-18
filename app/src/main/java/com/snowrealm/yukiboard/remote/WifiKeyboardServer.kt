package com.snowrealm.yukiboard.remote

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.snowrealm.yukiboard.telemetry.AndroidLogReporter
import com.snowrealm.yukiboard.telemetry.ErrorReporter
import com.snowrealm.yukiboard.telemetry.Severity
import com.snowrealm.yukiboard.telemetry.TelemetryEvent
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WiFi WebSocket 鍵盤服務 — 手機開 WebSocket Server，
 * 電腦瀏覽器或其他裝置透過 WebSocket 連線。
 *
 * ## 雙向通訊
 * - 手機 → 電腦：手機打字，按鍵事件透過 WebSocket 傳送
 * - 電腦 → 手機：電腦透過網頁輸入，文字傳回手機 IME
 *
 * ## 協議
 * JSON 訊息格式：
 * ```json
 * {"type": "key", "code": 65, "action": "press", "modifiers": 0}
 * {"type": "text", "content": "Hello world"}
 * {"type": "status", "state": "connected"}
 * ```
 *
 * ## 內建網頁
 * Server 同時提供 HTTP 服務，瀏覽器訪問 http://手機IP:port 即可開啟控制頁面。
 */
class WifiKeyboardServer(
    context: Context,
    private val errorReporter: ErrorReporter = AndroidLogReporter
) : RemoteKeyboardService {

    companion object {
        private const val TAG = "WifiKbServer"
        const val DEFAULT_PORT = 8765
        private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-5AB5DC76E97B"
        private const val PIN_LENGTH = 6
        private const val BRUTE_FORCE_WINDOW_MS = 60_000L
        private const val BRUTE_FORCE_MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 300_000L
        private const val TELEMETRY_CATEGORY = "REMOTE_KEYBOARD"

        private val secureRandom = SecureRandom()

        private fun generatePin(): String {
            val upperBound = Math.pow(10.0, PIN_LENGTH.toDouble()).toInt()
            val n = secureRandom.nextInt(upperBound)
            return n.toString().padStart(PIN_LENGTH, '0')
        }

        private fun generateSessionToken(): String {
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private val clients = CopyOnWriteArrayList<WebSocketClient>()
    private val attemptTrackers = ConcurrentHashMap<String, AttemptTracker>()

    @Volatile private var stateListener: ((ConnectionState) -> Unit)? = null
    @Volatile private var remoteKeyListener: ((RemoteKeyEvent) -> Unit)? = null
    @Volatile private var remoteTextListener: ((String) -> Unit)? = null

    var port: Int = DEFAULT_PORT

    /** 6 位連線 PIN — 顯示在面板，WebSocket 握手時驗證 */
    @Volatile var connectionPin: String = generatePin()
        private set

    override val authLevel: AuthLevel = AuthLevel.PIN_PLUS_SESSION

    override var connectionState: ConnectionState = ConnectionState.IDLE
        private set(value) {
            field = value
            handler.post { stateListener?.invoke(value) }
        }

    override val connectedDevice: RemoteDevice?
        get() = clients.firstOrNull()?.let {
            RemoteDevice(
                name = "WebSocket Client",
                address = it.address,
                type = RemoteType.WIFI
            )
        }

    /** 取得本機 WiFi IP 地址 */
    fun getLocalIpAddress(): String {
        val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifiManager.connectionInfo.ipAddress
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }

    /** 取得完整連線 URL */
    fun getConnectionUrl(): String = "http://${getLocalIpAddress()}:$port"

    override fun start() {
        if (running.get()) return

        connectionPin = generatePin()
        attemptTrackers.clear()
        running.set(true)
        connectionState = ConnectionState.LISTENING

        serverThread = Thread {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Server started on port $port")

                while (running.get()) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        handleNewConnection(socket)
                    } catch (e: Exception) {
                        if (running.get()) {
                            Log.e(TAG, "Accept error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server start failed", e)
                connectionState = ConnectionState.ERROR
            }
        }.also { it.isDaemon = true; it.start() }
    }

    override fun stop() {
        running.set(false)
        clients.forEach { it.close() }
        clients.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverThread = null
        connectionState = ConnectionState.IDLE
        connectionPin = ""
        attemptTrackers.clear()
    }

    override fun sendKeyEvent(event: RemoteKeyEvent) {
        val json = JSONObject().apply {
            put("type", "key")
            put("code", event.keyCode)
            put("action", event.action.name.lowercase())
            put("modifiers", event.modifiers)
            event.character?.let { put("char", it.toString()) }
        }
        broadcast(json.toString())
    }

    override fun sendText(text: String) {
        val json = JSONObject().apply {
            put("type", "text")
            put("content", text)
        }
        broadcast(json.toString())
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
        stop()
        stateListener = null
        remoteKeyListener = null
        remoteTextListener = null
    }

    // =========================================================================
    // 連線處理
    // =========================================================================

    private fun handleNewConnection(socket: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val firstLine = reader.readLine() ?: return@Thread

                if (firstLine.contains("GET") && firstLine.contains("HTTP")) {
                    // 解析請求路徑
                    val path = firstLine.split(" ").getOrNull(1) ?: "/"

                    // HTTP 請求
                    val headers = mutableMapOf<String, String>()
                    var line = reader.readLine()
                    while (line != null && line.isNotEmpty()) {
                        val colonIdx = line.indexOf(':')
                        if (colonIdx > 0) {
                            headers[line.substring(0, colonIdx).trim()] =
                                line.substring(colonIdx + 1).trim()
                        }
                        line = reader.readLine()
                    }

                    val wsKey = headers["Sec-WebSocket-Key"]
                    if (wsKey != null) {
                        val clientIp = socket.inetAddress?.hostAddress ?: "unknown"
                        val now = System.currentTimeMillis()
                        val tracker = attemptTrackers.computeIfAbsent(clientIp) { AttemptTracker() }

                        if (tracker.isLockedOut(now)) {
                            val reject = "HTTP/1.1 429 Too Many Requests\r\nConnection: close\r\n\r\nLocked"
                            socket.getOutputStream().write(reject.toByteArray())
                            socket.close()
                            return@Thread
                        }

                        val pinParam = path.substringAfter("pin=", "").substringBefore("&").substringBefore(" ")
                        if (pinParam == connectionPin) {
                            tracker.reset()
                            upgradeToWebSocket(socket, wsKey)
                        } else {
                            val lockoutJustTriggered = tracker.recordFailure(now)
                            val reject = "HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\nInvalid PIN"
                            socket.getOutputStream().write(reject.toByteArray())
                            socket.close()
                            if (lockoutJustTriggered) {
                                errorReporter.report(
                                    TelemetryEvent(
                                        category = TELEMETRY_CATEGORY,
                                        severity = Severity.WARN,
                                        code = "WIFI_PIN_BRUTE_FORCE_LOCKOUT",
                                        extras = mapOf(
                                            "lockoutMs" to LOCKOUT_MS.toString(),
                                            "maxAttempts" to BRUTE_FORCE_MAX_ATTEMPTS.toString()
                                        )
                                    )
                                )
                            }
                        }
                    } else {
                        // 普通 HTTP → 回傳控制頁面
                        serveControlPage(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection handling error", e)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun upgradeToWebSocket(socket: Socket, wsKey: String) {
        val acceptKey = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest("$wsKey$WEBSOCKET_GUID".toByteArray())
        )

        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"

        socket.getOutputStream().write(response.toByteArray())

        val sessionToken = generateSessionToken()
        val client = WebSocketClient(socket, sessionToken)
        clients.add(client)
        connectionState = ConnectionState.CONNECTED

        Log.d(TAG, "WebSocket client connected")

        // 發送歡迎訊息（含 sessionToken，後續訊息需帶回）
        val welcome = JSONObject().apply {
            put("type", "status")
            put("state", "connected")
            put("device", "YukiBoard")
            put("session", sessionToken)
        }
        client.send(welcome.toString())

        // 讀取迴圈
        try {
            val inputStream = socket.getInputStream()
            while (running.get() && !socket.isClosed) {
                val frame = readWebSocketFrame(inputStream) ?: break
                handleWebSocketMessage(frame, sessionToken)
            }
        } catch (e: Exception) {
            Log.d(TAG, "WebSocket read ended: ${e.message}")
        } finally {
            clients.remove(client)
            client.close()
            if (clients.isEmpty()) {
                connectionState = ConnectionState.LISTENING
            }
        }
    }

    private fun readWebSocketFrame(input: java.io.InputStream): String? {
        val b1 = input.read()
        if (b1 == -1) return null

        val b2 = input.read()
        if (b2 == -1) return null

        val isMasked = (b2 and 0x80) != 0
        var payloadLen = (b2 and 0x7F).toLong()

        if (payloadLen == 126L) {
            val b3 = input.read()
            val b4 = input.read()
            payloadLen = ((b3 shl 8) or b4).toLong()
        } else if (payloadLen == 127L) {
            var len = 0L
            for (i in 0 until 8) {
                len = (len shl 8) or input.read().toLong()
            }
            payloadLen = len
        }

        val mask = if (isMasked) {
            ByteArray(4).also { input.read(it) }
        } else null

        if (payloadLen > 65536) return null // 安全限制

        val payload = ByteArray(payloadLen.toInt())
        var read = 0
        while (read < payloadLen) {
            val n = input.read(payload, read, (payloadLen - read).toInt())
            if (n == -1) return null
            read += n
        }

        if (mask != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
        }

        return String(payload)
    }

    private fun handleWebSocketMessage(message: String, expectedSessionToken: String) {
        try {
            val json = JSONObject(message)
            val providedSession = json.optString("session", "")
            if (providedSession != expectedSessionToken) {
                Log.w(TAG, "WebSocket message rejected: session token mismatch")
                return
            }
            when (json.optString("type")) {
                "key" -> {
                    val event = RemoteKeyEvent(
                        keyCode = json.getInt("code"),
                        action = when (json.optString("action", "press")) {
                            "down" -> KeyAction.DOWN
                            "up" -> KeyAction.UP
                            else -> KeyAction.PRESS
                        },
                        modifiers = json.optInt("modifiers", 0),
                        character = json.optString("char", "").firstOrNull()
                    )
                    handler.post { remoteKeyListener?.invoke(event) }
                }
                "text" -> {
                    val text = json.getString("content")
                    handler.post { remoteTextListener?.invoke(text) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Message parse error: $message", e)
        }
    }

    private fun broadcast(message: String) {
        clients.forEach { client ->
            try {
                client.send(message)
            } catch (e: Exception) {
                Log.e(TAG, "Broadcast error", e)
            }
        }
    }

    // =========================================================================
    // 內建控制網頁
    // =========================================================================

    private fun serveControlPage(socket: Socket) {
        val html = buildControlPageHtml()
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${html.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n" +
                html
        socket.getOutputStream().write(response.toByteArray())
        socket.close()
    }

    private fun buildControlPageHtml(): String = """
<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>YukiBoard Remote</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    background: #0f1729; color: #e8f4ff;
    display: flex; flex-direction: column; align-items: center;
    min-height: 100vh; padding: 20px;
}
h1 { color: #7fd8ff; margin-bottom: 8px; font-size: 24px; }
.status {
    padding: 6px 16px; border-radius: 20px; margin-bottom: 16px;
    font-size: 13px; transition: all 0.3s;
}
.status.connected { background: #1b5e20; color: #a5d6a7; }
.status.disconnected { background: #b71c1c; color: #ef9a9a; }
.status.connecting { background: #e65100; color: #ffcc80; }
#textInput {
    width: 100%; max-width: 600px; height: 120px; padding: 12px;
    background: #1a2035; border: 1px solid #2a3555; border-radius: 8px;
    color: #e8f4ff; font-size: 16px; resize: vertical; outline: none;
    margin-bottom: 12px;
}
#textInput:focus { border-color: #4a90e2; }
.btn-row { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; margin-bottom: 16px; }
button {
    padding: 8px 20px; border: none; border-radius: 6px; cursor: pointer;
    font-size: 14px; transition: all 0.2s;
}
.btn-primary { background: #4a90e2; color: white; }
.btn-primary:hover { background: #357abd; }
.btn-secondary { background: #253050; color: #b0d4ff; }
.btn-secondary:hover { background: #3d6a9e; }
.btn-danger { background: #c62828; color: white; }
.shortcuts {
    display: grid; grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
    gap: 6px; width: 100%; max-width: 600px; margin-bottom: 16px;
}
.shortcut-btn {
    padding: 10px 4px; background: #253050; border: 1px solid #2a3555;
    border-radius: 6px; color: #b0d4ff; font-size: 12px; cursor: pointer;
    text-align: center;
}
.shortcut-btn:hover { background: #3d6a9e; border-color: #4a90e2; }
.log {
    width: 100%; max-width: 600px; height: 100px; overflow-y: auto;
    background: #111827; border-radius: 6px; padding: 8px;
    font-family: monospace; font-size: 11px; color: #6688aa;
}
</style>
</head>
<body>
<h1>❄ YukiBoard Remote</h1>
<div id="status" class="status connecting">請輸入 PIN 碼連線</div>

<div id="pinBox" style="margin-bottom:16px;text-align:center;">
    <input id="pinInput" type="text" maxlength="6" placeholder="輸入 6 位 PIN 碼"
        style="width:200px;padding:10px;font-size:20px;text-align:center;background:#1a2035;border:1px solid #2a3555;border-radius:8px;color:#e8f4ff;letter-spacing:8px;">
    <br><button class="btn-primary" style="margin-top:8px;" onclick="connectWithPin()">連線</button>
</div>

<textarea id="textInput" placeholder="在這裡輸入文字，會即時傳送到手機..." style="display:none;"></textarea>

<div class="btn-row">
    <button class="btn-primary" onclick="sendCurrentText()">傳送全部</button>
    <button class="btn-secondary" onclick="sendKey(13,0)">Enter</button>
    <button class="btn-secondary" onclick="sendKey(8,0)">Backspace</button>
    <button class="btn-secondary" onclick="sendKey(9,0)">Tab</button>
    <button class="btn-danger" onclick="clearInput()">清除</button>
</div>

<div class="shortcuts">
    <div class="shortcut-btn" onclick="sendKey(67,1)">Ctrl+C</div>
    <div class="shortcut-btn" onclick="sendKey(86,1)">Ctrl+V</div>
    <div class="shortcut-btn" onclick="sendKey(88,1)">Ctrl+X</div>
    <div class="shortcut-btn" onclick="sendKey(65,1)">Ctrl+A</div>
    <div class="shortcut-btn" onclick="sendKey(90,1)">Ctrl+Z</div>
    <div class="shortcut-btn" onclick="sendKey(89,1)">Ctrl+Y</div>
    <div class="shortcut-btn" onclick="sendKey(83,1)">Ctrl+S</div>
    <div class="shortcut-btn" onclick="sendKey(70,1)">Ctrl+F</div>
    <div class="shortcut-btn" onclick="sendKey(38,0)">↑</div>
    <div class="shortcut-btn" onclick="sendKey(40,0)">↓</div>
    <div class="shortcut-btn" onclick="sendKey(37,0)">←</div>
    <div class="shortcut-btn" onclick="sendKey(39,0)">→</div>
    <div class="shortcut-btn" onclick="sendKey(36,0)">Home</div>
    <div class="shortcut-btn" onclick="sendKey(35,0)">End</div>
    <div class="shortcut-btn" onclick="sendKey(27,0)">Esc</div>
    <div class="shortcut-btn" onclick="sendKey(46,0)">Del</div>
</div>

<div id="log" class="log"></div>

<script>
let ws = null;
let reconnectTimer = null;
let currentPin = '';
let sessionToken = '';
let wasConnected = false;

function connectWithPin() {
    const pin = document.getElementById('pinInput').value.trim();
    if (pin.length !== 6) { setStatus('disconnected', 'PIN 碼需為 6 位數'); return; }
    currentPin = pin;
    wasConnected = false;
    connect();
}

function backToPinInput(message) {
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
    currentPin = '';
    sessionToken = '';
    wasConnected = false;
    document.getElementById('pinInput').value = '';
    document.getElementById('pinBox').style.display = '';
    document.getElementById('textInput').style.display = 'none';
    setStatus('disconnected', message);
}

function connect() {
    if (!currentPin) return;
    const wsUrl = 'ws://' + location.host + '/?pin=' + currentPin;
    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        wasConnected = true;
        setStatus('connected', '已連線');
        addLog('WebSocket 已連線（PIN 驗證通過）');
        document.getElementById('pinBox').style.display = 'none';
        document.getElementById('textInput').style.display = '';
    };

    ws.onclose = () => {
        if (wasConnected) {
            setStatus('disconnected', '已斷線');
            addLog('連線已斷開，5 秒後重連...');
            reconnectTimer = setTimeout(connect, 5000);
        } else {
            addLog('連線失敗（PIN 錯誤或連線已被鎖定）');
            backToPinInput('無法連線，請確認 PIN 碼正確。若已連續輸入錯誤多次，請等待 5 分鐘後再試。');
        }
    };

    ws.onerror = () => {
        // 不在此 setStatus — 交由 onclose 依 wasConnected 判斷後續行為，避免提前覆蓋
    };

    ws.onmessage = (event) => {
        try {
            const msg = JSON.parse(event.data);
            if (msg.type === 'status') {
                addLog('狀態: ' + msg.state);
                if (msg.session) sessionToken = msg.session;
            }
            else if (msg.type === 'key') addLog('按鍵: ' + msg.code);
            else if (msg.type === 'text') addLog('文字: ' + msg.content);
        } catch(e) { addLog('收到: ' + event.data); }
    };
}

function setStatus(cls, text) {
    const el = document.getElementById('status');
    el.className = 'status ' + cls;
    el.textContent = text;
}

function addLog(msg) {
    const log = document.getElementById('log');
    const time = new Date().toLocaleTimeString();
    log.textContent += '[' + time + '] ' + msg + '\n';
    log.scrollTop = log.scrollHeight;
}

// 即時傳送每個字元
document.getElementById('textInput').addEventListener('input', (e) => {
    const data = e.data;
    if (data && ws && ws.readyState === 1 && sessionToken) {
        ws.send(JSON.stringify({type: 'text', content: data, session: sessionToken}));
    }
});

function sendCurrentText() {
    const text = document.getElementById('textInput').value;
    if (text && ws && ws.readyState === 1 && sessionToken) {
        ws.send(JSON.stringify({type: 'text', content: text, session: sessionToken}));
        addLog('傳送: ' + text.substring(0, 50) + (text.length > 50 ? '...' : ''));
    }
}

function sendKey(code, ctrl) {
    if (ws && ws.readyState === 1 && sessionToken) {
        ws.send(JSON.stringify({
            type: 'key', code: code, action: 'press',
            modifiers: ctrl ? 1 : 0, session: sessionToken
        }));
    }
}

function clearInput() {
    document.getElementById('textInput').value = '';
}

document.getElementById('pinInput').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') connectWithPin();
});
</script>
</body>
</html>
""".trimIndent()

    // =========================================================================
    // Brute-force Tracker
    // =========================================================================

    private class AttemptTracker {
        private var count: Int = 0
        private var windowStartMs: Long = 0L
        private var lockoutUntilMs: Long = 0L

        @Synchronized
        fun isLockedOut(nowMs: Long): Boolean = nowMs < lockoutUntilMs

        @Synchronized
        fun reset() {
            count = 0
            windowStartMs = 0L
            lockoutUntilMs = 0L
        }

        /** @return true if this failure just triggered lockout (edge event) */
        @Synchronized
        fun recordFailure(nowMs: Long): Boolean {
            if (nowMs - windowStartMs > BRUTE_FORCE_WINDOW_MS) {
                windowStartMs = nowMs
                count = 0
            }
            count += 1
            if (count >= BRUTE_FORCE_MAX_ATTEMPTS && lockoutUntilMs <= nowMs) {
                lockoutUntilMs = nowMs + LOCKOUT_MS
                return true
            }
            return false
        }
    }

    // =========================================================================
    // WebSocket Client 包裝
    // =========================================================================

    private class WebSocketClient(
        private val socket: Socket,
        val sessionToken: String
    ) {
        val address: String = socket.inetAddress?.hostAddress ?: "unknown"
        private val output: OutputStream = socket.getOutputStream()

        fun send(message: String) {
            val data = message.toByteArray()
            val frame = buildWebSocketFrame(data)
            synchronized(output) {
                output.write(frame)
                output.flush()
            }
        }

        fun close() {
            try { socket.close() } catch (_: Exception) {}
        }

        private fun buildWebSocketFrame(payload: ByteArray): ByteArray {
            val len = payload.size
            val header: ByteArray

            when {
                len < 126 -> {
                    header = byteArrayOf(0x81.toByte(), len.toByte())
                }
                len < 65536 -> {
                    header = byteArrayOf(
                        0x81.toByte(), 126.toByte(),
                        (len shr 8).toByte(), (len and 0xFF).toByte()
                    )
                }
                else -> {
                    header = ByteArray(10)
                    header[0] = 0x81.toByte()
                    header[1] = 127.toByte()
                    for (i in 0 until 8) {
                        header[9 - i] = (len shr (8 * i)).toByte()
                    }
                }
            }

            return header + payload
        }
    }
}

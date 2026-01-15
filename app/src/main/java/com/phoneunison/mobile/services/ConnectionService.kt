package com.phoneunison.mobile.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.phoneunison.mobile.MainActivity
import com.phoneunison.mobile.PhoneUnisonApp
import com.phoneunison.mobile.R
import com.phoneunison.mobile.protocol.Message
import com.phoneunison.mobile.protocol.MessageHandler
import com.phoneunison.mobile.utils.CryptoUtils
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*

class ConnectionService : Service() {

    companion object {
        private const val TAG = "ConnectionService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_CONNECT = "com.phoneunison.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.phoneunison.ACTION_DISCONNECT"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_CODE = "code"
        const val EXTRA_PUBLIC_KEY = "public_key"

        @Volatile
        var isConnected = false
            private set

        @Volatile
        var connectedDeviceName: String? = null
            private set

        var instance: ConnectionService? = null
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    lateinit var smsHandler: SmsHandler
        private set
    lateinit var fileHandler: FileHandler
        private set
    lateinit var callHandler: CallHandler
        private set
    private var messageHandler: com.phoneunison.mobile.protocol.MessageHandler? = null

    var serverHost: String? = null
        private set
    var serverPort: Int = 8765
        private set
    private var pairingCode: String? = null
    private var reconnectAttempts = 0
    private val maxReconnectDelay = 60_000L

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Connection service created")

        smsHandler = SmsHandler(this)
        fileHandler = FileHandler(this, this)
        callHandler = CallHandler(this, this)
        messageHandler = MessageHandler(this)

        client =
                OkHttpClient.Builder()
                        .pingInterval(30, TimeUnit.SECONDS)
                        .readTimeout(0, TimeUnit.MILLISECONDS)
                        .build()

        val filter = IntentFilter("com.phoneunison.SEND_MESSAGE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(messageReceiver, filter)
        }
    }

    private val messageReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.phoneunison.SEND_MESSAGE") {
                        val json = intent.getStringExtra("message")
                        if (json != null) {
                            webSocket?.send(json)
                        }
                    }
                }
            }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                serverHost = intent.getStringExtra(EXTRA_HOST)
                serverPort = intent.getIntExtra(EXTRA_PORT, 8765)
                pairingCode = intent.getStringExtra(EXTRA_CODE)
                val publicKey = intent.getStringExtra(EXTRA_PUBLIC_KEY)

                startForeground(NOTIFICATION_ID, createNotification("Connecting..."))
                connect(publicKey)
            }
            ACTION_DISCONNECT -> {
                disconnect()
                stopSelf()
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification("Ready to connect"))
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(messageReceiver)
        } catch (e: IllegalArgumentException) {}
        disconnect()
        serviceScope.cancel()
        Log.i(TAG, "Connection service destroyed")
    }

    private fun connect(serverPublicKey: String?) {
        serviceScope.launch {
            try {
                val url = "ws://$serverHost:$serverPort/phoneunison"
                Log.i(TAG, "Connecting to $url")

                val request = Request.Builder().url(url).build()

                webSocket =
                        client?.newWebSocket(
                                request,
                                object : WebSocketListener() {
                                    override fun onOpen(webSocket: WebSocket, response: Response) {
                                        Log.i(TAG, "WebSocket connected")
                                        sendPairingRequest()
                                    }

                                    override fun onMessage(webSocket: WebSocket, text: String) {
                                        handleMessage(text)
                                    }

                                    override fun onClosing(
                                            webSocket: WebSocket,
                                            code: Int,
                                            reason: String
                                    ) {
                                        Log.i(TAG, "WebSocket closing: $reason")
                                        webSocket.close(1000, null)
                                    }

                                    override fun onClosed(
                                            webSocket: WebSocket,
                                            code: Int,
                                            reason: String
                                    ) {
                                        Log.i(TAG, "WebSocket closed: $reason")
                                        setDisconnected()
                                    }

                                    override fun onFailure(
                                            webSocket: WebSocket,
                                            t: Throwable,
                                            response: Response?
                                    ) {
                                        Log.e(TAG, "WebSocket error", t)
                                        setDisconnected()
                                        scheduleReconnect()
                                    }
                                }
                        )
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                setDisconnected()
            }
        }
    }

    private fun sendPairingRequest() {
        val deviceId = CryptoUtils.getDeviceId(this)
        val deviceName = android.os.Build.MODEL

        val data =
                mapOf(
                        "code" to pairingCode,
                        "deviceId" to deviceId,
                        "deviceName" to deviceName,
                        "deviceModel" to android.os.Build.MODEL,
                        "publicKey" to CryptoUtils.generateKeyPair()
                )

        val message = Message(Message.PAIRING_REQUEST, data)
        sendMessage(message)
    }

    private fun handleMessage(json: String) {
        try {
            val message = gson.fromJson(json, Message::class.java)
            Log.d(TAG, "Received: ${message.type}")

            when (message.type) {
                Message.PAIRING_RESPONSE -> handlePairingResponse(message)
                Message.HEARTBEAT -> sendHeartbeat()
                else -> messageHandler?.handleMessage(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
        }
    }

    private fun handlePairingResponse(message: Message) {
        val successValue = message.data?.get("success")
        val success =
                when (successValue) {
                    is Boolean -> successValue
                    is Number -> successValue.toInt() == 1
                    is String -> successValue.equals("true", ignoreCase = true)
                    else -> false
                }

        Log.i(TAG, "Pairing response: success=$success, raw=$successValue")

        if (success) {
            val deviceName = message.data?.get("deviceName") as? String
            setConnected(deviceName ?: "Windows PC")
            Log.i(TAG, "Pairing successful: $deviceName")
        } else {
            Log.w(TAG, "Pairing failed - response indicates failure")
            setDisconnected()
        }
    }

    private fun sendHeartbeat() {
        val data = mapOf("battery" to getBatteryLevel(), "charging" to isCharging())
        sendMessage(Message(Message.HEARTBEAT, data))
    }

    fun sendMessage(message: Message) {
        val json = gson.toJson(message)
        webSocket?.send(json)
    }

    private fun setConnected(deviceName: String) {
        isConnected = true
        connectedDeviceName = deviceName
        reconnectAttempts = 0
        updateNotification("Connected to $deviceName")
    }

    private fun setDisconnected() {
        isConnected = false
        connectedDeviceName = null
        updateNotification("Disconnected")
    }

    private fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        setDisconnected()
    }

    private fun scheduleReconnect() {
        serviceScope.launch {
            val delayMs =
                    minOf(5000L * (1 shl reconnectAttempts.coerceAtMost(5)), maxReconnectDelay)
            reconnectAttempts++
            delay(delayMs)
            if (!isConnected && serverHost != null) {
                Log.i(TAG, "Attempting reconnect after ${delayMs}ms (attempt $reconnectAttempts)")
                connect(null)
            }
        }
    }

    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        return NotificationCompat.Builder(this, PhoneUnisonApp.CHANNEL_ID_SERVICE)
                .setContentTitle("PhoneUnison")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
    }

    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isCharging(): Boolean {
        val batteryManager = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.isCharging
    }
}

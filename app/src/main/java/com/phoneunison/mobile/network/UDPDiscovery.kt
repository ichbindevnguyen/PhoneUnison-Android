package com.phoneunison.mobile.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.phoneunison.mobile.utils.CryptoUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.*
import java.nio.charset.StandardCharsets

data class DiscoveredDevice(
    val alias: String,
    val deviceModel: String?,
    val deviceType: String,
    val fingerprint: String,
    val host: String,
    val port: Int,
    val protocol: String = "ws"
)

class UDPDiscovery(private val context: Context) {
    
    companion object {
        private const val TAG = "UDPDiscovery"
        const val MULTICAST_ADDRESS = "224.0.0.167"
        const val DISCOVERY_PORT = 53318
    }
    
    private val gson = Gson()
    private var multicastSocket: MulticastSocket? = null
    private var unicastSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val deviceId = CryptoUtils.getDeviceId(context)
    
    fun discoverDevices(): Flow<DiscoveredDevice> = callbackFlow {
        val devices = mutableSetOf<String>()
        
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("PhoneUnison")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
            
            val multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS)
            multicastSocket = MulticastSocket(DISCOVERY_PORT)
            multicastSocket?.reuseAddress = true
            multicastSocket?.soTimeout = 1000
            
            try {
                val networkInterface = getActiveNetworkInterface()
                if (networkInterface != null) {
                    multicastSocket?.joinGroup(
                        InetSocketAddress(multicastGroup, DISCOVERY_PORT),
                        networkInterface
                    )
                } else {
                    @Suppress("DEPRECATION")
                    multicastSocket?.joinGroup(multicastGroup)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to join multicast group with interface, trying legacy", e)
                @Suppress("DEPRECATION")
                multicastSocket?.joinGroup(multicastGroup)
            }
            
            unicastSocket = DatagramSocket()
            
            Log.i(TAG, "UDP Discovery started on $MULTICAST_ADDRESS:$DISCOVERY_PORT")
            
            sendAnnouncement(true)
            
            val buffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()
            val timeout = 10000L
            
            while (System.currentTimeMillis() - startTime < timeout) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    multicastSocket?.receive(packet)
                    
                    val json = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                    val senderIp = packet.address.hostAddress ?: continue
                    
                    val device = parseDiscoveryMessage(json, senderIp)
                    if (device != null && device.fingerprint != deviceId && !devices.contains(device.fingerprint)) {
                        devices.add(device.fingerprint)
                        Log.i(TAG, "Discovered: ${device.alias} at ${device.host}:${device.port}")
                        trySend(device)
                    }
                    
                } catch (e: SocketTimeoutException) {
                    sendAnnouncement(true)
                } catch (e: Exception) {
                    if (e !is SocketException) {
                        Log.e(TAG, "Error receiving UDP", e)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Discovery failed", e)
        }
        
        awaitClose { 
            cleanup()
        }
    }
    
    suspend fun scanOnce(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DiscoveredDevice>()
        val seen = mutableSetOf<String>()
        
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("PhoneUnison")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
            
            val multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS)
            multicastSocket = MulticastSocket(DISCOVERY_PORT)
            multicastSocket?.reuseAddress = true
            multicastSocket?.soTimeout = 500
            
            try {
                @Suppress("DEPRECATION")
                multicastSocket?.joinGroup(multicastGroup)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to join multicast", e)
            }
            
            unicastSocket = DatagramSocket()
            
            repeat(3) {
                sendAnnouncement(true)
                delay(300)
            }
            
            val buffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()
            val timeout = 3000L
            
            while (System.currentTimeMillis() - startTime < timeout) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    multicastSocket?.receive(packet)
                    
                    val json = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                    val senderIp = packet.address.hostAddress ?: continue
                    
                    val device = parseDiscoveryMessage(json, senderIp)
                    if (device != null && device.fingerprint != deviceId && !seen.contains(device.fingerprint)) {
                        seen.add(device.fingerprint)
                        devices.add(device)
                        Log.i(TAG, "Found: ${device.alias} at ${device.host}:${device.port}")
                    }
                    
                } catch (e: SocketTimeoutException) {
                } catch (e: Exception) {
                    if (e !is SocketException) {
                        Log.w(TAG, "Receive error", e)
                    }
                    break
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
        } finally {
            cleanup()
        }
        
        devices
    }
    
    private fun sendAnnouncement(isAnnounce: Boolean) {
        try {
            val message = mapOf(
                "alias" to Build.MODEL,
                "version" to "1.0",
                "deviceModel" to Build.MODEL,
                "deviceType" to "mobile",
                "fingerprint" to deviceId,
                "port" to 8765,
                "protocol" to "ws",
                "announce" to isAnnounce
            )
            
            val json = gson.toJson(message)
            val data = json.toByteArray(StandardCharsets.UTF_8)
            
            val multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS)
            val packet = DatagramPacket(data, data.size, multicastGroup, DISCOVERY_PORT)
            multicastSocket?.send(packet)
            
            Log.d(TAG, "Sent announcement: announce=$isAnnounce")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send announcement", e)
        }
    }
    
    fun sendResponse(targetIp: String) {
        try {
            val message = mapOf(
                "alias" to Build.MODEL,
                "version" to "1.0",
                "deviceModel" to Build.MODEL,
                "deviceType" to "mobile",
                "fingerprint" to deviceId,
                "port" to 8765,
                "protocol" to "ws",
                "announce" to false
            )
            
            val json = gson.toJson(message)
            val data = json.toByteArray(StandardCharsets.UTF_8)
            
            val targetAddress = InetAddress.getByName(targetIp)
            val packet = DatagramPacket(data, data.size, targetAddress, DISCOVERY_PORT)
            unicastSocket?.send(packet)
            
            Log.d(TAG, "Sent response to $targetIp")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send response", e)
        }
    }
    
    private fun parseDiscoveryMessage(json: String, senderIp: String): DiscoveredDevice? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val data = gson.fromJson(json, Map::class.java) as Map<String, Any>
            
            val alias = data["alias"] as? String ?: return null
            val deviceModel = data["deviceModel"] as? String
            val deviceType = data["deviceType"] as? String ?: "unknown"
            val fingerprint = data["fingerprint"] as? String ?: return null
            val port = (data["port"] as? Number)?.toInt() ?: 8765
            val protocol = data["protocol"] as? String ?: "ws"
            
            DiscoveredDevice(
                alias = alias,
                deviceModel = deviceModel,
                deviceType = deviceType,
                fingerprint = fingerprint,
                host = senderIp,
                port = port,
                protocol = protocol
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse: $json", e)
            null
        }
    }
    
    private fun getActiveNetworkInterface(): NetworkInterface? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                    val addresses = ni.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            return ni
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get network interface", e)
        }
        return null
    }
    
    private fun cleanup() {
        try {
            val multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS)
            @Suppress("DEPRECATION")
            multicastSocket?.leaveGroup(multicastGroup)
        } catch (e: Exception) {
            Log.w(TAG, "Error leaving group", e)
        }
        
        multicastSocket?.close()
        multicastSocket = null
        
        unicastSocket?.close()
        unicastSocket = null
        
        multicastLock?.release()
        multicastLock = null
        
        Log.i(TAG, "UDP Discovery cleaned up")
    }
}

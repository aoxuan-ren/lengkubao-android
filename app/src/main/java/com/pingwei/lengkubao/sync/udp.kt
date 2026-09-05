package com.pingwei.lengkubao.sync.udp

import android.content.Context
import android.content.Intent
import android.util.Log
import com.pingwei.lengkubao.utils.Constant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.*

class UdpDeviceDiscovery private constructor(private val context: Context) {
    companion object {
        private const val TAG = "UdpDeviceDiscovery"

        @Volatile
        private var instance: UdpDeviceDiscovery? = null

        fun getInstance(context: Context): UdpDeviceDiscovery {
            return instance ?: synchronized(this) {
                instance ?: UdpDeviceDiscovery(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    data class DiscoveredDevice(
        val deviceName: String,      // 服务器名称
        val ip: String,              // 服务器IP
        val port: Int,               // TCP端口
        val pairingCode: String      // 配对码
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryJob: Job? = null
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _messageFlow = MutableStateFlow("")
    val messageFlow: StateFlow<String> = _messageFlow

    private var broadcastSocket: DatagramSocket? = null

    private fun getSubnetBroadcastAddresses(): List<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.interfaceAddresses) {
                    addr.broadcast?.let { targets.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取子网广播地址失败: ${e.message}")
        }
        if (targets.isEmpty()) {
            targets.add(InetAddress.getByName("255.255.255.255"))
        }
        return targets.toList()
    }

    private fun parseServerUdpMessage(
        response: String,
        serverIp: String,
        pairingCode: String?
    ): DiscoveredDevice? {
        val parts = response.split("|")
        if (parts.isEmpty() || parts.size < 4) return null
        val isResponse = parts[0] == Constant.UDP_MSG_RESPONSE
        val isAnnounce = parts[0] == Constant.UDP_MSG_SERVER_ANNOUNCE
        if (!isResponse && !isAnnounce) return null

        val serverName = parts[1]
        val serverPairingCode = parts[2]
        val serverPort = parts[3].toIntOrNull() ?: 8080
        if (!pairingCode.isNullOrBlank() && serverPairingCode != pairingCode) return null

        return DiscoveredDevice(
            deviceName = serverName,
            ip = serverIp,
            port = serverPort,
            pairingCode = serverPairingCode
        )
    }

    /**
     * 同步 UDP 发现（主动请求 + 被动接收广播）
     */
    suspend fun discoverServers(
        pairingCode: String? = null,
        timeoutMs: Long = Constant.UDP_BROADCAST_TIMEOUT
    ): List<DiscoveredDevice> {
        return withContext(Dispatchers.IO) {
            val foundDevices = mutableListOf<DiscoveredDevice>()
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0))
                    broadcast = true
                    soTimeout = 500
                }

                val deviceId = getDeviceId()
                val deviceName = getDeviceName()
                val discoverMsg = if (pairingCode.isNullOrBlank()) {
                    "DISCOVER_LENGKUBAO|$deviceId|$deviceName"
                } else {
                    "DISCOVER_LENGKUBAO|$deviceId|$deviceName|$pairingCode"
                }
                val sendData = discoverMsg.toByteArray(Charsets.UTF_8)
                val broadcastTargets = getSubnetBroadcastAddresses()
                Log.i(TAG, "🔍 UDP发现: 向 ${broadcastTargets.size} 个子网发送请求")

                repeat(3) { round ->
                    for (target in broadcastTargets) {
                        try {
                            val packet = DatagramPacket(
                                sendData,
                                sendData.size,
                                target,
                                Constant.UDP_BROADCAST_PORT
                            )
                            socket.send(packet)
                            Log.d(TAG, "📤 发送UDP广播(${round + 1}/3): $discoverMsg -> ${target.hostAddress}")
                        } catch (e: Exception) {
                            Log.w(TAG, "⚠️ 发送UDP到 ${target.hostAddress} 失败: ${e.message}")
                        }
                    }
                    delay(250)
                }

                val responseBuffer = ByteArray(1024)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val receivePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                        val serverIp = receivePacket.address?.hostAddress ?: continue
                        Log.d(TAG, "📥 收到UDP: $response 来自 $serverIp")

                        val device = parseServerUdpMessage(response, serverIp, pairingCode) ?: continue
                        if (foundDevices.none { it.ip == device.ip }) {
                            foundDevices.add(device)
                            Log.i(TAG, "✅ 发现服务器: ${device.deviceName} (${device.ip}:${device.port})")
                            sendDeviceFoundBroadcast(device)
                        }
                    } catch (_: SocketTimeoutException) {
                        // 继续等待
                    }
                }
                foundDevices
            } catch (e: Exception) {
                Log.e(TAG, "❌ UDP发现异常: ${e.message}", e)
                emptyList()
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * 开始UDP广播发现服务器
     */
    fun startDiscovery(pairingCode: String? = null) {
        if (_isScanning.value) {
            Log.w(TAG, "⚠️ 已在扫描中，跳过重复启动")
            return
        }

        discoveryJob?.cancel()
        _discoveredDevices.value = emptyList()

        discoveryJob = scope.launch {
            _isScanning.value = true
            _messageFlow.value = "正在UDP广播扫描服务器..."
            Log.i(TAG, "🔍 开始UDP广播扫描，配对码: ${pairingCode ?: "任意"}")

            try {
                val foundDevices = discoverServers(pairingCode)
                _discoveredDevices.value = foundDevices
                _messageFlow.value = if (foundDevices.isEmpty()) {
                    "未找到任何服务器"
                } else {
                    "找到 ${foundDevices.size} 个服务器"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ UDP发现异常: ${e.message}", e)
                _messageFlow.value = "扫描异常: ${e.message}"
            } finally {
                broadcastSocket?.close()
                broadcastSocket = null
                _isScanning.value = false
            }
        }
    }

    /**
     * 发送配对请求（可选，用于快速验证）
     */
    suspend fun sendPairingRequest(serverIp: String, pairingCode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 3000

                val deviceId = getDeviceId()
                val requestMsg = "PAIRING_REQUEST|$deviceId|$pairingCode"
                val sendData = requestMsg.toByteArray(Charsets.UTF_8)

                val packet = DatagramPacket(
                    sendData,
                    sendData.size,
                    InetAddress.getByName(serverIp),
                    Constant.UDP_BROADCAST_PORT
                )

                socket.send(packet)

                // 等待响应
                val buffer = ByteArray(256)
                val receivePacket = DatagramPacket(buffer, buffer.size)
                socket.receive(receivePacket)

                val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                socket.close()

                response.startsWith("PAIRING_ACCEPTED")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 配对请求失败: ${e.message}")
                false
            }
        }
    }

    /**
     * 停止扫描
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        try {
            broadcastSocket?.close()
        } catch (e: Exception) { }
        _isScanning.value = false
        _messageFlow.value = "扫描已停止"
    }

    /**
     * 自动连接：先UDP发现，然后自动选择第一个匹配的服务器
     */
    suspend fun autoConnect(
        pairingCode: String,
        announceTimeoutMs: Long = 3000L,
        discoverTimeoutMs: Long = Constant.UDP_BROADCAST_TIMEOUT + 2000,
    ): DiscoveredDevice? {
        return withContext(Dispatchers.IO) {
            try {
                _messageFlow.value = "正在UDP自动发现服务器..."

                listenForServerAnnounce(pairingCode, announceTimeoutMs)?.let { announceDevice ->
                    _messageFlow.value = "✅ 收到服务器广播: ${announceDevice.deviceName}"
                    return@withContext announceDevice
                }

                val devices = discoverServers(pairingCode, discoverTimeoutMs)

                if (devices.isNotEmpty()) {
                    val matchedDevice = devices.firstOrNull { it.pairingCode == pairingCode }
                    val selectedDevice = matchedDevice ?: devices.first()
                    _messageFlow.value = "✅ 自动发现服务器: ${selectedDevice.deviceName}"

                    val verified = sendPairingRequest(selectedDevice.ip, pairingCode)
                    if (verified) {
                        Log.i(TAG, "✅ UDP配对验证成功")
                    }
                    return@withContext selectedDevice
                }

                _messageFlow.value = "❌ 未发现任何服务器"
                null
            } catch (e: Exception) {
                Log.e(TAG, "❌ 自动连接失败: ${e.message}")
                _messageFlow.value = "自动连接失败: ${e.message}"
                null
            }
        }
    }

    /**
     * 被动监听电脑端广播（LENGKUBAO_SERVER_ANNOUNCE），用于冷启动重连。
     */
    suspend fun listenForServerAnnounce(pairingCode: String, timeoutMs: Long = 3000L): DiscoveredDevice? {
        return withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0))
                    soTimeout = 500
                }
                val buffer = ByteArray(1024)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val serverIp = packet.address?.hostAddress ?: continue
                        parseServerUdpMessage(response, serverIp, pairingCode)?.let { return@withContext it }
                    } catch (_: SocketTimeoutException) {
                        // 继续等待
                    }
                }
                null
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 监听服务器广播失败: ${e.message}")
                null
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun sendDeviceFoundBroadcast(device: DiscoveredDevice) {
        val intent = Intent(Constant.ACTION_UDP_DEVICE_FOUND).apply {
            putExtra(Constant.EXTRA_DEVICE_IP, device.ip)
            putExtra(Constant.EXTRA_DEVICE_PORT, device.port)
            putExtra(Constant.EXTRA_DEVICE_NAME, device.deviceName)
            putExtra(Constant.EXTRA_PAIRING_CODE, device.pairingCode)
        }
        context.sendBroadcast(intent)
    }

    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = "HANDHELD_${System.currentTimeMillis()}"
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    private fun getDeviceName(): String {
        return try {
            val manufacturer = android.os.Build.MANUFACTURER
            val model = android.os.Build.MODEL
            if (model.startsWith(manufacturer)) model else "$manufacturer $model"
        } catch (e: Exception) {
            "Android设备"
        }
    }

    fun cleanup() {
        stopDiscovery()
        scope.cancel()
    }
}
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
                // 创建UDP广播Socket
                broadcastSocket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = Constant.UDP_BROADCAST_TIMEOUT.toInt()
                }

                // 发送发现广播
                val deviceId = getDeviceId()
                val deviceName = getDeviceName()

                // 构建发现消息: DISCOVER_LENGKUBAO|设备ID|设备名称|配对码(可选)
                val discoverMsg = if (pairingCode.isNullOrBlank()) {
                    "DISCOVER_LENGKUBAO|$deviceId|$deviceName"
                } else {
                    "DISCOVER_LENGKUBAO|$deviceId|$deviceName|$pairingCode"
                }

                val sendData = discoverMsg.toByteArray(Charsets.UTF_8)
                val broadcastPacket = DatagramPacket(
                    sendData,
                    sendData.size,
                    InetAddress.getByName("255.255.255.255"),
                    Constant.UDP_BROADCAST_PORT
                )

                Log.d(TAG, "📤 发送UDP广播: $discoverMsg")
                broadcastSocket?.send(broadcastPacket)

                // 等待响应
                val startTime = System.currentTimeMillis()
                val responseBuffer = ByteArray(1024)
                val foundDevices = mutableListOf<DiscoveredDevice>()

                while (System.currentTimeMillis() - startTime < Constant.UDP_BROADCAST_TIMEOUT) {
                    try {
                        val receivePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                        broadcastSocket?.receive(receivePacket)

                        val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                        val serverIp = receivePacket.address.hostAddress

                        Log.d(TAG, "📥 收到UDP响应: $response 来自 $serverIp")

                        // 解析服务器响应: LENGKUBAO_SERVER|服务器名称|配对码|TCP端口
                        val parts = response.split("|")
                        if (parts.isNotEmpty() && parts[0] == Constant.UDP_MSG_RESPONSE && parts.size >= 4) {
                            val serverName = parts[1]
                            val serverPairingCode = parts[2]
                            val serverPort = parts[3].toIntOrNull() ?: 8080

                            val device = DiscoveredDevice(
                                deviceName = serverName,
                                ip = serverIp,
                                port = serverPort,
                                pairingCode = serverPairingCode
                            )

                            // 如果指定了配对码，只添加匹配的
                            if (pairingCode.isNullOrBlank() || serverPairingCode == pairingCode) {
                                if (!foundDevices.any { it.ip == serverIp }) {
                                    foundDevices.add(device)
                                    Log.i(TAG, "✅ 发现服务器: $serverName ($serverIp:$serverPort) 配对码: $serverPairingCode")

                                    // 发送广播
                                    sendDeviceFoundBroadcast(device)
                                }
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // 超时正常，继续等待剩余时间
                    }
                }

                _discoveredDevices.value = foundDevices

                if (foundDevices.isEmpty()) {
                    _messageFlow.value = "未找到任何服务器"
                } else {
                    _messageFlow.value = "找到 ${foundDevices.size} 个服务器"
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ UDP发现异常: ${e.message}", e)
                _messageFlow.value = "扫描异常: ${e.message}"
            } finally {
                try {
                    broadcastSocket?.close()
                } catch (e: Exception) { }
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
    suspend fun autoConnect(pairingCode: String): DiscoveredDevice? {
        return withContext(Dispatchers.IO) {
            try {
                _messageFlow.value = "正在UDP自动发现服务器..."

                // 先快速扫描
                startDiscovery(pairingCode)

                // 等待扫描完成
                var retry = 0
                while (_isScanning.value && retry < 10) {
                    delay(500)
                    retry++
                }

                // 获取发现的设备
                val devices = _discoveredDevices.value

                if (devices.isNotEmpty()) {
                    // 优先选择配对码完全匹配的
                    val matchedDevice = devices.firstOrNull { it.pairingCode == pairingCode }
                    val selectedDevice = matchedDevice ?: devices.first()

                    _messageFlow.value = "✅ 自动发现服务器: ${selectedDevice.deviceName}"

                    // 可选：发送UDP配对请求验证
                    val verified = sendPairingRequest(selectedDevice.ip, pairingCode)
                    if (verified) {
                        Log.i(TAG, "✅ UDP配对验证成功")
                    }

                    return@withContext selectedDevice
                } else {
                    _messageFlow.value = "❌ 未发现任何服务器"
                    return@withContext null
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 自动连接失败: ${e.message}")
                _messageFlow.value = "自动连接失败: ${e.message}"
                return@withContext null
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
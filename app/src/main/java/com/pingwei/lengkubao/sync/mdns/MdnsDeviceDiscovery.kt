package com.pingwei.lengkubao.sync.mdns

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.pingwei.lengkubao.utils.Constant
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * mDNS设备发现管理器
 * 负责扫描局域网内广播相同配对码的电脑端
 */
class MdnsDeviceDiscovery(private val context: Context) {
    companion object {
        private const val TAG = "MdnsDiscovery"

        @Volatile
        private var instance: MdnsDeviceDiscovery? = null

        fun getInstance(context: Context): MdnsDeviceDiscovery {
            return instance ?: synchronized(this) {
                instance ?: MdnsDeviceDiscovery(context.applicationContext).also { instance = it }
            }
        }
    }

    data class DiscoveredDevice(
        val ip: String,
        val port: Int,
        val deviceName: String,
        val pairingCode: String,
        val serviceInfo: NsdServiceInfo,
        val firstSeen: Long = System.currentTimeMillis()
    )

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 发现的设备列表
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices

    // 发现的设备缓存（去重）
    private val deviceMap = ConcurrentHashMap<String, DiscoveredDevice>()

    // 是否正在扫描
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    // 当前配对码
    private var targetPairingCode: String? = null

    // 发现监听器
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListeners = mutableMapOf<String, NsdManager.ResolveListener>()

    // ========== 新增：标记是否已启动发现 ==========
    private val isDiscoveryStarted = AtomicBoolean(false)

    // 消息通道
    private val _messageChannel = Channel<String>(Channel.UNLIMITED)
    val messageFlow = _messageChannel.receiveAsFlow()

    /**
     * 开始扫描指定配对码的设备
     */
    fun startDiscovery(pairingCode: String): Boolean {
        return try {
            // ========== 修改：安全停止之前的扫描 ==========
            if (isDiscoveryStarted.get()) {
                safeStopDiscovery()
            }

            targetPairingCode = pairingCode
            deviceMap.clear()
            _discoveredDevices.value = emptyList()

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    Log.i(TAG, "✅ mDNS发现已启动: $serviceType")
                    _isScanning.value = true
                    isDiscoveryStarted.set(true)  // 标记已启动
                    _messageChannel.trySend("开始扫描设备...")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "📡 发现服务: ${serviceInfo.serviceName}, 类型: ${serviceInfo.serviceType}")

                    // 只关心我们的服务类型
                    if (serviceInfo.serviceType != Constant.MDNS_SERVICE_TYPE) {
                        return
                    }

                    // 解析服务详情
                    resolveService(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "📴 服务丢失: ${serviceInfo.serviceName}")

                    // 从缓存中移除
                    val key = serviceInfo.serviceName
                    deviceMap.remove(key)?.let {
                        _discoveredDevices.value = deviceMap.values.toList()
                        _messageChannel.trySend("设备断开: ${it.deviceName}")

                        // 发送广播 - 修复这里
                        val intent = Intent(Constant.ACTION_MDNS_DEVICE_LOST)
                        intent.putExtra(Constant.EXTRA_DEVICE_NAME, it.deviceName)
                        context.sendBroadcast(intent)
                    }
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.i(TAG, "⏹️ mDNS发现已停止")
                    _isScanning.value = false
                    isDiscoveryStarted.set(false)  // 标记已停止
                    _messageChannel.trySend("停止扫描")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "❌ 启动发现失败: $errorCode")
                    _isScanning.value = false
                    isDiscoveryStarted.set(false)
                    _messageChannel.trySend("启动扫描失败: $errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "❌ 停止发现失败: $errorCode")
                    _isScanning.value = false
                    isDiscoveryStarted.set(false)
                }
            }

            nsdManager.discoverServices(
                Constant.MDNS_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动mDNS发现异常: ${e.message}", e)
            _isScanning.value = false
            isDiscoveryStarted.set(false)
            false
        }
    }

    /**
     * ========== 新增：安全停止发现 ==========
     */
    private fun safeStopDiscovery() {
        try {
            if (isDiscoveryStarted.get() && discoveryListener != null) {
                nsdManager.stopServiceDiscovery(discoveryListener)
            }
        } catch (e: IllegalArgumentException) {
            // 忽略 "listener not registered" 错误
            Log.w(TAG, "⚠️ 停止扫描时监听器未注册，忽略")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止扫描异常: ${e.message}", e)
        } finally {
            isDiscoveryStarted.set(false)
        }
    }

    /**
     * 解析服务详情
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val key = serviceInfo.serviceName

        // 避免重复解析
        if (deviceMap.containsKey(key)) {
            return
        }

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "❌ 解析服务失败: ${serviceInfo.serviceName}, code=$errorCode")
                resolveListeners.remove(key)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "✅ 服务解析成功: ${serviceInfo.serviceName}")

                try {
                    val host = serviceInfo.host ?: return
                    val port = serviceInfo.port

                    // 从TXT记录中获取配对码和设备名
                    val attributes = serviceInfo.attributes
                    val pairingCode = String(attributes[Constant.MDNS_PAIRING_CODE_KEY] ?: byteArrayOf())
                    val deviceName = String(attributes[Constant.MDNS_DEVICE_NAME_KEY] ?: byteArrayOf())

                    // 检查配对码是否匹配
                    val targetCode = targetPairingCode
                    if (targetCode != null && pairingCode != targetCode) {
                        Log.d(TAG, "❌ 配对码不匹配: $pairingCode != $targetCode")
                        return
                    }

                    val device = DiscoveredDevice(
                        ip = host.hostAddress,
                        port = port,
                        deviceName = deviceName,
                        pairingCode = pairingCode,
                        serviceInfo = serviceInfo
                    )

                    deviceMap[key] = device
                    _discoveredDevices.value = deviceMap.values.toList()

                    Log.i(TAG, "🎯 发现匹配设备: ${device.deviceName} (${device.ip}:${device.port}), 配对码: $pairingCode")

                    val message = if (targetCode != null) {
                        "✅ 找到匹配设备: ${device.deviceName}"
                    } else {
                        "📡 发现设备: ${device.deviceName} (配对码: $pairingCode)"
                    }
                    _messageChannel.trySend(message)

                    // 发送广播 - 修复这里
                    val intent = Intent(Constant.ACTION_MDNS_DEVICE_FOUND)
                    intent.putExtra(Constant.EXTRA_DEVICE_IP, device.ip)
                    intent.putExtra(Constant.EXTRA_DEVICE_PORT, device.port)
                    intent.putExtra(Constant.EXTRA_DEVICE_NAME, device.deviceName)
                    intent.putExtra(Constant.EXTRA_PAIRING_CODE, device.pairingCode)
                    context.sendBroadcast(intent)

                } catch (e: Exception) {
                    Log.e(TAG, "❌ 处理解析结果异常: ${e.message}", e)
                } finally {
                    resolveListeners.remove(key)
                }
            }
        }

        resolveListeners[key] = resolveListener
        nsdManager.resolveService(serviceInfo, resolveListener)
    }

    /**
     * 停止扫描
     */
    fun stopDiscovery() {
        try {
            if (isDiscoveryStarted.get() && discoveryListener != null) {
                nsdManager.stopServiceDiscovery(discoveryListener)
            }
        } catch (e: IllegalArgumentException) {
            // 忽略 "listener not registered" 错误
            Log.w(TAG, "⚠️ 停止扫描时监听器未注册，忽略")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止扫描异常: ${e.message}", e)
        } finally {
            discoveryListener = null
            resolveListeners.clear()
            _isScanning.value = false
            isDiscoveryStarted.set(false)
        }
    }

    /**
     * 自动扫描并连接（带超时）
     */
    suspend fun autoConnect(pairingCode: String, timeoutMs: Long = Constant.MDNS_SCAN_TIMEOUT): DiscoveredDevice? {
        return withContext(Dispatchers.IO) {
            try {
                _messageChannel.trySend("🔍 开始自动扫描，配对码: $pairingCode")

                // 启动扫描
                if (!startDiscovery(pairingCode)) {
                    _messageChannel.trySend("❌ 启动扫描失败")
                    return@withContext null
                }

                // 等待发现设备
                var startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    val devices = _discoveredDevices.value
                    if (devices.isNotEmpty()) {
                        // 找到第一个匹配的设备
                        val device = devices.firstOrNull { it.pairingCode == pairingCode }
                        if (device != null) {
                            _messageChannel.trySend("🎉 自动连接成功: ${device.deviceName}")
                            return@withContext device
                        }
                    }
                    delay(500)
                }

                _messageChannel.trySend("⏰ 扫描超时，未找到配对设备")
                null
            } catch (e: Exception) {
                Log.e(TAG, "❌ 自动连接异常: ${e.message}", e)
                _messageChannel.trySend("❌ 自动连接异常: ${e.message}")
                null
            } finally {
                stopDiscovery()
            }
        }
    }

    /**
     * 清理资源
     */
    fun destroy() {
        stopDiscovery()
        scope.cancel()
        _messageChannel.close()
    }
}
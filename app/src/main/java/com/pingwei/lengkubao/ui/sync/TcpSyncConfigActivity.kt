package com.pingwei.lengkubao.ui.sync

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.service.TcpSyncService
import com.pingwei.lengkubao.sync.QrPairingHelper
import com.pingwei.lengkubao.sync.SyncState
import com.pingwei.lengkubao.sync.TcpSyncManager
import com.pingwei.lengkubao.sync.rememberTcpConnectionState
import com.pingwei.lengkubao.sync.rememberTcpSyncState
import com.pingwei.lengkubao.sync.mdns.MdnsDeviceDiscovery
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import com.pingwei.lengkubao.utils.Constant
import com.pingwei.lengkubao.utils.ScannerUtils
import com.pingwei.lengkubao.utils.SyncStatusUtils
import kotlinx.coroutines.delay

import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
// 在文件顶部添加（在 TcpSyncConfigScreen 函数之前）
sealed class DiscoveredDeviceItem {
    abstract val deviceName: String
    abstract val ip: String
    abstract val port: Int
    abstract val pairingCode: String

    data class UdpDevice(val device: com.pingwei.lengkubao.sync.udp.UdpDeviceDiscovery.DiscoveredDevice) : DiscoveredDeviceItem() {
        override val deviceName: String get() = device.deviceName
        override val ip: String get() = device.ip
        override val port: Int get() = device.port
        override val pairingCode: String get() = device.pairingCode
    }

    data class MdnsDevice(val device: com.pingwei.lengkubao.sync.mdns.MdnsDeviceDiscovery.DiscoveredDevice) : DiscoveredDeviceItem() {
        override val deviceName: String get() = device.deviceName
        override val ip: String get() = device.ip
        override val port: Int get() = device.port
        override val pairingCode: String get() = device.pairingCode
    }
}
@SuppressLint("RememberReturnType", "UnrememberedMutableState")
@Composable
fun TcpSyncConfigScreen(
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activityScope = (context as? ComponentActivity)?.lifecycleScope
    val scrollState = rememberScrollState()

    val connectionState = rememberTcpConnectionState()
    val syncState = rememberTcpSyncState()

    val prefs = remember { context.getSharedPreferences("sync_config", Context.MODE_PRIVATE) }

    var serverIp: String by remember {
        mutableStateOf(LengKuBaoApplication.getSyncManager().getCurrentConfig().serverIp)
    }
    var serverPort: String by remember {
        mutableStateOf(LengKuBaoApplication.getSyncManager().getCurrentConfig().serverPort.toString())
    }
    var autoSync: Boolean by remember { mutableStateOf(prefs.getBoolean(Constant.PREF_AUTO_SYNC, Constant.PREF_AUTO_SYNC_DEFAULT)) }

    // 配对码相关状态
    var pairingCode by remember { mutableStateOf(prefs.getString(Constant.PREF_PAIRING_CODE, "") ?: "") }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) return@rememberLauncherForActivityResult
        val payload = QrPairingHelper.parse(raw)
        if (payload == null) {
            Toast.makeText(context, "无法识别配对二维码，请扫描电脑端显示的二维码", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        try {
            LengKuBaoApplication.getSyncManager().applyQrPairing(payload)
            pairingCode = payload.code
            serverIp = payload.ip
            serverPort = payload.port.toString()
            val label = if (payload.name.isNotBlank()) payload.name else payload.ip
            Toast.makeText(context, "已配对 $label，正在连接…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "配对失败", Toast.LENGTH_LONG).show()
        }
    }
    var autoConnect by remember { mutableStateOf(prefs.getBoolean(Constant.PREF_AUTO_CONNECT, true)) }
    var showResetDialog by remember { mutableStateOf(false) }
    var isResettingSyncStatus by remember { mutableStateOf(false) }
    var resetStartDate by remember { mutableStateOf("") }
    var resetEndDate by remember { mutableStateOf("") }
    var resetResultText by remember { mutableStateOf("") }
    // ========== 根据TCP连接状态确定主显示内容 ==========
    val isTcpConnected = connectionState == TcpSyncManager.ConnectionState.CONNECTED

    // mDNS 发现管理器
    val mdnsDiscovery = remember { MdnsDeviceDiscovery.getInstance(context) }
    val discoveredDevicesState by mdnsDiscovery.discoveredDevices.collectAsStateWithLifecycle()
    val isScanningState by mdnsDiscovery.isScanning.collectAsStateWithLifecycle()
    var scanResult by remember { mutableStateOf("") }

    // UDP 发现管理器
    val udpDiscovery = remember { com.pingwei.lengkubao.sync.udp.UdpDeviceDiscovery.getInstance(context) }
    val udpDevices by udpDiscovery.discoveredDevices.collectAsStateWithLifecycle()
    val isUdpScanning by udpDiscovery.isScanning.collectAsStateWithLifecycle()
    val udpMessage by udpDiscovery.messageFlow.collectAsStateWithLifecycle()

    var showAdvancedSettings by remember {
        mutableStateOf(prefs.getBoolean(Constant.PREF_SHOW_ADVANCED_SYNC, false))
    }

    var discoveryMethod by remember {
        mutableStateOf(prefs.getString(Constant.PREF_DISCOVERY_METHOD, "both") ?: "both")
    }

    // ========== 新增：页面加载时自动扫描 ==========
    // 修改这个 LaunchedEffect
    LaunchedEffect(isTcpConnected, pairingCode, showAdvancedSettings) {
        if (!showAdvancedSettings || isTcpConnected || pairingCode.isBlank()) return@LaunchedEffect
        delay(500)

        while (!isTcpConnected && pairingCode.isNotBlank() && showAdvancedSettings) {
            scanResult = "🔄 持续扫描中..."

            when (discoveryMethod) {
                "udp" -> {
                    udpDiscovery.startDiscovery(pairingCode)
                }
                "mdns" -> {
                    mdnsDiscovery.startDiscovery(pairingCode)
                }
                "both" -> {
                    udpDiscovery.startDiscovery(pairingCode)
                    mdnsDiscovery.startDiscovery(pairingCode)
                }
            }

            // 扫描一轮后等待3秒，然后重新扫描
            delay(3000)
        }
    }

    // 监听UDP消息
    LaunchedEffect(udpMessage) {
        if (udpMessage.isNotEmpty()) {
            scanResult = udpMessage
        }
    }

    LaunchedEffect(isScanningState) {
        // 扫描状态变化时的处理
    }

    LaunchedEffect(discoveredDevicesState) {
        if (discoveredDevicesState.isNotEmpty()) {
            scanResult = "找到 ${discoveredDevicesState.size} 个设备"
        }
    }

    LaunchedEffect(Unit) {
        mdnsDiscovery.messageFlow.collect { message ->
            scanResult = message
        }
    }


    // 连接状态文本和颜色
    val (connectionStatus, statusColor) = remember(connectionState) {
        when (connectionState) {
            TcpSyncManager.ConnectionState.CONNECTED -> Pair("✅ 已连接（配对成功）", Color.Green)
            TcpSyncManager.ConnectionState.CONNECTING -> Pair("🔄 正在连接/注册…", Color.Yellow)
            TcpSyncManager.ConnectionState.ERROR -> Pair("❌ TCP连接错误", Color.Red)
            TcpSyncManager.ConnectionState.SYNCING -> Pair("📤 同步中...", Color.Blue)
            TcpSyncManager.ConnectionState.DISCONNECTED -> Pair("📴 TCP未连接", Color.Gray)
            TcpSyncManager.ConnectionState.WAITING_RECONNECT -> Pair("⏳ 等待重连...", Color(0xFFFFA500))
        }
    }

    // 同步状态文本
    val syncStatusText = remember(syncState) {
        when (syncState) {
            is SyncState.Idle -> "就绪"
            is SyncState.Syncing -> "同步中: ${(syncState as SyncState.Syncing).dataType}"
            is SyncState.Success -> "✅ ${(syncState as SyncState.Success).message}"
            is SyncState.Failed -> "❌ ${(syncState as SyncState.Failed).message}"
            else -> "未知状态"
        }
    }

    // 合并设备列表为密封类
    val allDevices = remember(udpDevices, discoveredDevicesState) {
        val list = mutableListOf<DiscoveredDeviceItem>()
        list.addAll(udpDevices.map { DiscoveredDeviceItem.UdpDevice(it) })
        list.addAll(discoveredDevicesState.map { DiscoveredDeviceItem.MdnsDevice(it) })
        list.distinctBy { it.ip }
    }

    // ========== 自动连接逻辑 ==========
    LaunchedEffect(allDevices.size, pairingCode) {
        if (!isTcpConnected && pairingCode.isNotBlank() && allDevices.isNotEmpty()) {
            // 找出配对码匹配的设备
            val matchedDevices = allDevices.filter { it.pairingCode == pairingCode }

            when (matchedDevices.size) {
                0 -> {
                    // 没有匹配的设备，不自动连接
                    scanResult = "❌ 未找到配对码为 $pairingCode 的设备"
                }
                1 -> {
                    // 只有一个匹配的设备，自动连接
                    val device = matchedDevices.first()
                    scanResult = "✅ 自动选择设备: ${device.deviceName}"

                    // 延迟一下让用户看到提示
                    delay(500)

                    // 自动填写并连接
                    serverIp = device.ip
                    serverPort = device.port.toString()
                    prefs.edit().apply {
                        putString(Constant.PREF_PAIRED_SERVER_IP, device.ip)
                        putInt(Constant.PREF_PAIRED_SERVER_PORT, device.port)
                    }.apply()

                    // 自动连接TCP
                    LengKuBaoApplication.getSyncManager().updateConfig(
                        TcpSyncManager.SyncConfig(
                            serverIp = serverIp,
                            serverPort = serverPort.toIntOrNull() ?: 8080
                        )
                    )
                    LengKuBaoApplication.getSyncManager().connect()

                    scanResult = "✅ 已自动连接到: ${device.deviceName}"
                }
                else -> {
                    // 多个匹配的设备，提示用户选择
                    scanResult = "⚠️ 发现 ${matchedDevices.size} 个配对码为 $pairingCode 的设备，请手动选择"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text("📡 TCP同步配置", style = MaterialTheme.typography.headlineMedium)
        }

        // TCP连接状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isTcpConnected) Color(0xFFE8F5E9) else Color.White
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📊 连接状态",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(color = statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = connectionStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                if (isTcpConnected) {
                    Text(
                        text = "服务器: ${LengKuBaoApplication.getSyncManager().getCurrentConfig().serverIp}:${LengKuBaoApplication.getSyncManager().getCurrentConfig().serverPort}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "配对码: $pairingCode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "同步状态: $syncStatusText",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 扫码配对（主路径）
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📷 扫码连接电脑",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "在电脑端顶部查看配对二维码，手持扫一次即可完成配对",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Button(
                    onClick = {
                        val options = ScannerUtils.getQrScanOptions()
                            .setPrompt("请扫描电脑屏幕上的配对二维码")
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("扫描配对二维码")
                }
                TextButton(
                    onClick = {
                        showAdvancedSettings = !showAdvancedSettings
                        prefs.edit().putBoolean(Constant.PREF_SHOW_ADVANCED_SYNC, showAdvancedSettings).apply()
                    }
                ) {
                    Text(if (showAdvancedSettings) "收起高级设置" else "高级设置（手动 IP / 发现方式）")
                }
            }
        }

        if (showAdvancedSettings) {
        // 服务器设置卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "服务器设置",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = serverIp,
                    onValueChange = { newValue -> serverIp = newValue },
                    label = { Text("服务器IP地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isTcpConnected
                )

                OutlinedTextField(
                    value = serverPort,
                    onValueChange = { newValue -> serverPort = newValue },
                    label = { Text("服务器端口") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isTcpConnected
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val trimmedIp = serverIp.trim()
                            if (!TcpSyncManager.isValidServerIp(trimmedIp)) {
                                Toast.makeText(context, "请输入有效的 IPv4 地址（如 192.168.1.100）", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val port = serverPort.toIntOrNull() ?: 8080
                            if (port !in 1..65535) {
                                Toast.makeText(context, "端口号需在 1-65535 之间", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            serverIp = trimmedIp
                            LengKuBaoApplication.getSyncManager().updateConfig(
                                TcpSyncManager.SyncConfig(
                                    serverIp = trimmedIp,
                                    serverPort = port
                                )
                            )
                            prefs.edit().apply {
                                putString(Constant.PREF_PAIRED_SERVER_IP, trimmedIp)
                                putInt(Constant.PREF_PAIRED_SERVER_PORT, port)
                            }.apply()
                            Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTcpConnected
                    ) {
                        Text("保存配置")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                if (isTcpConnected) {
                                    LengKuBaoApplication.getSyncManager().disconnect()
                                } else {
                                    LengKuBaoApplication.getSyncManager().connect()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTcpConnected) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isTcpConnected) "断开连接" else "连接服务器")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("自动后台同步", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = autoSync,
                        onCheckedChange = { checked ->
                            autoSync = checked
                            prefs.edit().putBoolean(Constant.PREF_AUTO_SYNC, checked).apply()
                            if (checked) {
                                TcpSyncService.startService(context)
                            } else {
                                TcpSyncService.stopService(context)
                            }
                        }
                    )
                }
            }
        }

        // 同步操作卡片（只在连接后显示）
        if (isTcpConnected) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "同步操作",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "同步状态: $syncStatusText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (syncState) {
                            is SyncState.Success -> MaterialTheme.colorScheme.primary
                            is SyncState.Failed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                TcpSyncService.syncPendingNow(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = syncState !is SyncState.Syncing
                        ) {
                            Text("同步未同步数据")
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "同步状态重置（按时间段）",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "作用于入库单、销售单、包装单、预支、扣款（按创建时间）。重置后会重新进入待同步队列。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Button(
                    onClick = {
                        val end = Calendar.getInstance()
                        val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -6) }
                        resetStartDate = formatDateForInput(start.timeInMillis)
                        resetEndDate = formatDateForInput(end.timeInMillis)
                        showResetDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isResettingSyncStatus
                ) {
                    Text(if (isResettingSyncStatus) "重置中..." else "选择时间段并重置")
                }

                if (resetResultText.isNotBlank()) {
                    Text(
                        text = resetResultText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (resetResultText.startsWith("✅")) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }
        }

        // 自动配对卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔐 自动发现设置",
                    style = MaterialTheme.typography.titleMedium
                )

                // 发现方式选择
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("发现方式:", style = MaterialTheme.typography.bodyMedium)

                    Row {
                        FilterChip(
                            selected = discoveryMethod == "udp",
                            onClick = {
                                discoveryMethod = "udp"
                                prefs.edit().putString(Constant.PREF_DISCOVERY_METHOD, "udp").apply()
                            },
                            label = { Text("UDP广播") },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FilterChip(
                            selected = discoveryMethod == "mdns",
                            onClick = {
                                discoveryMethod = "mdns"
                                prefs.edit().putString(Constant.PREF_DISCOVERY_METHOD, "mdns").apply()
                            },
                            label = { Text("mDNS") },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FilterChip(
                            selected = discoveryMethod == "both",
                            onClick = {
                                discoveryMethod = "both"
                                prefs.edit().putString(Constant.PREF_DISCOVERY_METHOD, "both").apply()
                            },
                            label = { Text("同时") }
                        )
                    }
                }

                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = {
                        pairingCode = it
                        prefs.edit().putString(Constant.PREF_PAIRING_CODE, it).apply()

                        // 当用户修改配对码时，如果当前未连接，自动重新扫描
                        if (!isTcpConnected && it.isNotBlank()) {
                            scanResult = "🔄 配对码已修改，重新扫描..."

                            when (discoveryMethod) {
                                "udp" -> {
                                    udpDiscovery.startDiscovery(it)
                                }
                                "mdns" -> {
                                    mdnsDiscovery.startDiscovery(it)
                                }
                                "both" -> {
                                    udpDiscovery.startDiscovery(it)
                                    mdnsDiscovery.startDiscovery(it)
                                }
                            }
                        }
                    },
                    label = { Text("6位数字配对码") },
                    placeholder = { Text("请输入电脑端显示的6位数字") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isTcpConnected
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("启动时自动连接", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = {
                            autoConnect = it
                            prefs.edit().putBoolean(Constant.PREF_AUTO_CONNECT, it).apply()
                        }
                    )
                }

                // 只在未连接时显示扫描相关UI
                if (!isTcpConnected) {
                    if (scanResult.isNotEmpty()) {
                        Text(
                            text = scanResult,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                scanResult.startsWith("✅") -> Color(0xFF4CAF50)
                                scanResult.startsWith("⚠️") -> Color(0xFFFFA500)
                                scanResult.startsWith("❌") -> Color(0xFFF44336)
                                scanResult.startsWith("🔄") -> Color(0xFF2196F3)
                                else -> Color.Gray
                            }
                        )
                    }

                    if (allDevices.isNotEmpty()) {
                        Text(
                            text = "📱 发现设备 (${allDevices.size}):",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = allDevices,
                                key = { "${it::class.simpleName}_${it.ip}" }
                            ) { deviceItem ->
                                DeviceItem(
                                    deviceItem = deviceItem,
                                    currentPairingCode = pairingCode,
                                    onSelect = { selectedDevice ->
                                        serverIp = selectedDevice.ip
                                        serverPort = selectedDevice.port.toString()
                                        prefs.edit().apply {
                                            putString(Constant.PREF_PAIRED_SERVER_IP, selectedDevice.ip)
                                            putInt(Constant.PREF_PAIRED_SERVER_PORT, selectedDevice.port)
                                        }.apply()

                                        // 连接TCP
                                        LengKuBaoApplication.getSyncManager().updateConfig(
                                            TcpSyncManager.SyncConfig(
                                                serverIp = serverIp,
                                                serverPort = serverPort.toIntOrNull() ?: 8080
                                            )
                                        )
                                        LengKuBaoApplication.getSyncManager().connect()
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (pairingCode.isNotBlank()) {
                                    scanResult = "正在扫描..."

                                    when (discoveryMethod) {
                                        "udp" -> {
                                            udpDiscovery.startDiscovery(pairingCode)
                                        }
                                        "mdns" -> {
                                            mdnsDiscovery.startDiscovery(pairingCode)
                                        }
                                        "both" -> {
                                            udpDiscovery.startDiscovery(pairingCode)
                                            mdnsDiscovery.startDiscovery(pairingCode)
                                        }
                                    }

                                    scope.launch {
                                        delay(5000)
                                        if (allDevices.isEmpty() && (isUdpScanning || isScanningState)) {
                                            scanResult = "⏳ 正在扫描中，请稍候..."
                                        }
                                    }
                                } else {
                                    scanResult = "❌ 请输入配对码"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = (!isUdpScanning && !isScanningState) && pairingCode.isNotBlank()
                        ) {
                            if (isUdpScanning || isScanningState) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isUdpScanning || isScanningState) "扫描中..." else "重新扫描")
                        }

                        OutlinedButton(
                            onClick = {
                                udpDiscovery.stopDiscovery()
                                mdnsDiscovery.stopDiscovery()
                                scanResult = ""
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isUdpScanning || isScanningState
                        ) {
                            Text("停止扫描")
                        }
                    }
                } else {
                    // 已连接时显示成功信息
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "✅ 已成功连接到服务器，配对码验证通过",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Divider()
                Text(
                    text = "💡 提示: 请输入电脑端显示的6位数字配对码，应用会自动扫描并连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        } // showAdvancedSettings

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("重置同步状态") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "请输入日期范围（格式：yyyy-MM-dd），系统会按创建时间重置同步状态。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = resetStartDate,
                            onValueChange = { resetStartDate = it.trim() },
                            label = { Text("开始日期") },
                            placeholder = { Text("例如 2026-04-01") },
                            singleLine = true,
                            enabled = !isResettingSyncStatus
                        )
                        OutlinedTextField(
                            value = resetEndDate,
                            onValueChange = { resetEndDate = it.trim() },
                            label = { Text("结束日期") },
                            placeholder = { Text("例如 2026-04-07") },
                            singleLine = true,
                            enabled = !isResettingSyncStatus
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isResettingSyncStatus,
                        onClick = {
                            val range = buildResetTimeRange(resetStartDate, resetEndDate)
                            if (range == null) {
                                resetResultText = "❌ 日期格式错误，请使用 yyyy-MM-dd"
                                return@TextButton
                            }

                            if (range.startTime > range.endTime) {
                                resetResultText = "❌ 开始日期不能晚于结束日期"
                                return@TextButton
                            }

                            showResetDialog = false
                            isResettingSyncStatus = true
                            scope.launch {
                                val result = SyncStatusUtils.resetBillSyncStatusByTimeRange(
                                    context = context,
                                    startTime = range.startTime,
                                    endTime = range.endTime
                                )
                                if (result.totalCount > 0) {
                                    LengKuBaoApplication.getSyncManager().clearConfirmedItems()
                                }
                                resetResultText = "✅ 已重置：入库${result.inStockCount}，销售${result.saleCount}，包装${result.packagingCount}，预支${result.advanceCount}，扣款${result.deductionCount}，预售${result.presaleCount}，预售收款${result.presalePaymentCount}，流水${result.ledgerCount}，合计${result.totalCount}"
                                isResettingSyncStatus = false
                            }
                        }
                    ) {
                        Text("确认重置")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isResettingSyncStatus,
                        onClick = { showResetDialog = false }
                    ) {
                        Text("取消")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DeviceItem(
    deviceItem: DiscoveredDeviceItem,
    currentPairingCode: String,
    onSelect: (com.pingwei.lengkubao.sync.udp.UdpDeviceDiscovery.DiscoveredDevice) -> Unit
) {
    val source = when (deviceItem) {
        is DiscoveredDeviceItem.UdpDevice -> "UDP"
        is DiscoveredDeviceItem.MdnsDevice -> "mDNS"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (deviceItem.pairingCode == currentPairingCode)
                Color(0xFFE8F5E9) else Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = deviceItem.deviceName, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "[$source]",
                        fontSize = 10.sp,
                        color = if (source == "UDP") Color(0xFF2196F3) else Color(0xFF4CAF50)
                    )
                }
                Text(
                    text = "${deviceItem.ip}:${deviceItem.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "配对码: ${deviceItem.pairingCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deviceItem.pairingCode == currentPairingCode)
                        Color(0xFF4CAF50) else Color.Gray
                )
            }

            if (deviceItem.pairingCode == currentPairingCode) {
                Button(
                    onClick = {
                        // 创建UDP设备对象传递给onSelect
                        val selectedDevice = com.pingwei.lengkubao.sync.udp.UdpDeviceDiscovery.DiscoveredDevice(
                            deviceName = deviceItem.deviceName,
                            ip = deviceItem.ip,
                            port = deviceItem.port,
                            pairingCode = deviceItem.pairingCode
                        )
                        onSelect(selectedDevice)
                    },
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("选择", fontSize = 12.sp)
                }
            }
        }
    }
}

private data class ResetTimeRange(
    val startTime: Long,
    val endTime: Long
)

private fun formatDateForInput(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(timeMillis)
}

private fun buildResetTimeRange(startDate: String, endDate: String): ResetTimeRange? {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        isLenient = false
    }
    return try {
        val start = format.parse(startDate) ?: return null
        val end = format.parse(endDate) ?: return null

        val startCalendar = Calendar.getInstance().apply {
            time = start
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCalendar = Calendar.getInstance().apply {
            time = end
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        ResetTimeRange(
            startTime = startCalendar.timeInMillis,
            endTime = endCalendar.timeInMillis
        )
    } catch (_: Exception) {
        null
    }
}

class TcpSyncConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TcpSyncConfigScreen(
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}
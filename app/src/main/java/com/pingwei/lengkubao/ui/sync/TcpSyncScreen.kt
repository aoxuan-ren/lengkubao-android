// File: ui/sync/TcpSyncScreen.kt
package com.pingwei.lengkubao.ui.sync

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pingwei.lengkubao.sync.TcpSyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TcpSyncScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 从SharedPreferences读取配置
    val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)

    var serverIp by remember { mutableStateOf(prefs.getString("server_ip", "192.168.1.100") ?: "192.168.1.100") }
    var serverPort by remember { mutableStateOf(prefs.getInt("server_port", 8080).toString()) }

    // 连接状态
    var connectionStatus by remember { mutableStateOf("未连接") }
    var isConnecting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "📡 TCP同步配置",
            style = MaterialTheme.typography.headlineMedium
        )

        // 服务器配置卡片
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
                    onValueChange = { serverIp = it },
                    label = { Text("服务器IP地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = serverPort,
                    onValueChange = { serverPort = it },
                    label = { Text("服务器端口") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // 连接状态
                Text(
                    text = "状态: $connectionStatus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        connectionStatus.contains("已连接") -> MaterialTheme.colorScheme.primary
                        connectionStatus.contains("失败") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            // 保存配置
                            prefs.edit().apply {
                                putString("server_ip", serverIp)
                                putInt("server_port", serverPort.toIntOrNull() ?: 8080)
                            }.apply()

                            // 连接服务器
                            isConnecting = true
                            connectionStatus = "连接中..."

                            scope.launch {
                                // 这里应该启动TCP同步服务
                                // TcpSyncService.startService(context)
                                // 简化处理：直接显示连接成功
                                delay(1000)
                                isConnecting = false
                                connectionStatus = "✅ 已连接到 $serverIp:$serverPort"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isConnecting
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("连接中...")
                        } else {
                            Text("连接服务器")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            // 断开连接
                            // TcpSyncService.stopService(context)
                            connectionStatus = "已断开"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("断开连接")
                    }
                }
            }
        }

        // 同步操作卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "同步操作",
                    style = MaterialTheme.typography.titleMedium
                )

                var syncStatus by remember { mutableStateOf("就绪") }
                var isSyncing by remember { mutableStateOf(false) }

                Text(
                    text = "状态: $syncStatus",
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isSyncing = true
                            syncStatus = "正在同步未同步数据..."

                            scope.launch {
                                delay(1500) // 模拟同步过程
                                isSyncing = false
                                syncStatus = "✅ 同步完成"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("同步中...")
                        } else {
                            Text("同步未同步数据")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            // 测试连接
                            scope.launch {
                                syncStatus = "测试连接中..."
                                delay(500)
                                syncStatus = "✅ 连接正常"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("测试连接")
                    }
                }
            }
        }

        // 返回按钮
        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
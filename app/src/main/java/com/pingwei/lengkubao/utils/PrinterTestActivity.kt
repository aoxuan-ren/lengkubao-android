// utils/PrinterTestActivity.kt
package com.pingwei.lengkubao.utils

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pingwei.lengkubao.service.SunmiPrintService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrinterTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PrinterTestScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterTestScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var connectionStatus by remember { mutableStateOf("检查中...") }
    var printerInfo by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    // 修复1：将 loadPrinterInfo 定义在 LaunchedEffect 外部，且在可组合函数作用域内
    fun loadPrinterInfo() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val printService = SunmiPrintService.getInstance(context)

                // 检查连接
                val connected = printService.ensureConnectedForPrint()
                connectionStatus = if (connected) "已连接" else "未连接"

                // 获取详细信息
                val status = printService.checkPrinterStatus()
                val info = printService.getPrinterInfo()
                printerInfo = "状态: $status\n$info"

            } catch (e: Exception) {
                connectionStatus = "错误: ${e.message}"
                printerInfo = ""
            }
        }
    }

    // 修复2：LaunchedEffect 中正确调用 loadPrinterInfo
    LaunchedEffect(Unit) {
        loadPrinterInfo()
    }

    fun testPrint() {
        isTesting = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val printService = SunmiPrintService.getInstance(context)
                val success = printService.testPrint()

                withContext(Dispatchers.Main) {
                    isTesting = false
                    Toast.makeText(
                        context,
                        if (success) "测试打印成功" else "测试打印失败",
                        Toast.LENGTH_LONG
                    ).show()

                    // 刷新信息
                    loadPrinterInfo()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isTesting = false
                    Toast.makeText(context, "测试失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 修复3：使用非实验性的 Scaffold 配置，或添加实验性 API 注解
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("打印机测试") },
                navigationIcon = {
                    IconButton(onClick = { /* 返回 */ }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 连接状态
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "连接状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold // 修复4：FontWeight 已导入
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = connectionStatus,
                        color = when {
                            connectionStatus.contains("已连接") -> MaterialTheme.colorScheme.primary
                            connectionStatus.contains("未连接") -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            // 打印机信息
            if (printerInfo.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "打印机信息",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(printerInfo)
                    }
                }
            }

            // 测试按钮
            Button(
                onClick = { testPrint() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("打印中...")
                } else {
                    Icon(Icons.Default.Print, contentDescription = "测试")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("打印测试页")
                }
            }

            // 重新连接按钮
            OutlinedButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val printService = SunmiPrintService.getInstance(context)
                        val reconnected = printService.reconnect()

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (reconnected) "重新连接成功" else "重新连接失败",
                                Toast.LENGTH_SHORT
                            ).show()
                            loadPrinterInfo()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "重新连接")
                Spacer(modifier = Modifier.width(8.dp))
                Text("重新连接打印机")
            }

            // 说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "故障排除",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. 确保打印机已开机")
                    Text("2. 检查USB连接是否牢固")
                    Text("3. 检查纸张是否充足")
                    Text("4. 重启打印机和设备")
                }
            }
        }
    }
}

// 可选：如果仍有实验性API警告，添加此注解（放在文件顶部或函数上）
// @OptIn(ExperimentalMaterial3Api::class)
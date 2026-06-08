package com.pingwei.lengkubao.ui.config

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.service.SunmiPrintService
import com.pingwei.lengkubao.ui.common.ConfigManager
import com.pingwei.lengkubao.ui.common.printer.PrintStatus
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinter
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinterFactory
import com.sunmi.peripheral.printer.InnerPrinterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintConfigScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val configManager = remember { ConfigManager(context) }
    val printService = remember { SunmiPrintService.getInstance(context) }

    // 修复：使用自定义工厂类创建带参 ViewModel 实例
    val printerViewModel: SaleBillPrinter = viewModel(
        factory = SaleBillPrinterFactory(context)
    )

    // 企业信息状态
    var companyName by remember { mutableStateOf(configManager.getCompanyName()) }
    var companyAddress by remember { mutableStateOf(configManager.getCompanyAddress()) }
    var companyPhone by remember { mutableStateOf(configManager.getCompanyPhone()) }

    // 打印配置状态
    var autoCut by remember { mutableStateOf(configManager.getAutoCut()) }
    var paperWidth by remember { mutableStateOf(configManager.getPaperWidth()) }
    var fontSize by remember { mutableStateOf(configManager.getFontSize()) }

    // UI状态
    var showSaveDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    // 新增：诊断和详细测试对话框状态变量
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var showTestPrintDialog by remember { mutableStateOf(false) }
    var diagnosticContent by remember { mutableStateOf("") }
    var diagnosticTitle by remember { mutableStateOf("") }

    val printerStatus by printerViewModel.printStatus.collectAsStateWithLifecycle()
    val printerInfo by printerViewModel.printerInfo.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 应用启动时自动检查并恢复打印机连接状态
    LaunchedEffect(Unit) {
        // 等待1秒让UI加载完成，避免操作过早导致的UI卡顿
        delay(1000)

        // 检查上次的打印机状态
        val connectionState = printService.getConnectionState()
        val savedConnected = connectionState["saved_connected"] as? Boolean ?: false
        val memoryConnected = connectionState["memory_connected"] as? Boolean ?: false

        if (savedConnected && !memoryConnected) {
            // 上次是连接状态，但当前未连接，尝试自动恢复
            Log.d("PrintConfig", "检测到上次连接状态，尝试自动恢复打印机连接...")
            printerViewModel.initializePrinter(retryCount = 1) // 或者使用新的别名方法：
        }
    }

    // 诊断对话框显示函数
    fun showDiagnosticDialog(
        title: String,
        content: String,
        coroutineScope: CoroutineScope,
        snackbarHostState: SnackbarHostState
    ) {
        diagnosticTitle = title
        diagnosticContent = content
        showDiagnosticDialog = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("打印配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 打印机状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Print,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "打印机状态",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 修复：直接使用 PrintStatus （已导入），修正大小写，保证穷尽性
                    when (printerStatus) {
                        is PrintStatus.Idle -> {
                            StatusChip(
                                text = "未初始化",
                                icon = Icons.Filled.Warning,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = { printerViewModel.initializePrinter() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = true
                            ) {
                                Text("初始化打印机")
                            }
                        }
                        is PrintStatus.Initializing -> {
                            StatusChip(
                                text = "正在初始化...",
                                icon = Icons.Filled.Sync,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        is PrintStatus.Ready -> {
                            StatusChip(
                                text = "已就绪",
                                icon = Icons.Filled.CheckCircle,
                                color = MaterialTheme.colorScheme.primary
                            )
                            printerInfo?.let { info ->
                                Text(
                                    text = info.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Text(
                                    text = "最后检查: ${info.lastChecked}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is PrintStatus.Printing -> {
                            StatusChip(
                                text = "打印中...",
                                icon = Icons.Filled.Sync,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        is PrintStatus.Success -> {
                            StatusChip(
                                text = "打印成功",
                                icon = Icons.Filled.CheckCircle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is PrintStatus.Error -> {
                            val error = (printerStatus as PrintStatus.Error)
                            StatusChip(
                                text = "错误",
                                icon = Icons.Filled.Error,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = error.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        // 保证 when 表达式穷尽性，避免编译错误
                        else -> {
                            StatusChip(
                                text = "未知状态",
                                icon = Icons.Filled.QuestionMark,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 修复：直接使用 PrintStatus.Ready 判断，修正大小写和引用
                    if (printerStatus is PrintStatus.Ready) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { printerViewModel.checkStatus() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("刷新状态")
                            }
                            Button(
                                onClick = { showTestDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("测试打印")
                            }
                        }
                    }
                }
            }

            // 新增：打印机诊断工具卡片
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "打印机诊断工具",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 诊断按钮组
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 强制重新初始化按钮
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    printerViewModel.initializePrinter()
                                    snackbarHostState.showSnackbar("正在强制重新初始化打印机...")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("强制重新初始化")
                        }

                        // 检查连接详情按钮（修复：复用SunmiPrintService公共方法，避免私有成员访问）
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        // 获取打印服务实例
                                        val printService = SunmiPrintService.getInstance(context)

                                        // 获取详细的连接状态（复用公共方法，避免私有成员访问）
                                        val connectionStatus = printService.getConnectionStatus()
                                        val printerInfo = printService.getPrinterInfo()
                                        val status = mutableListOf<String>()

                                        status.add("=== 打印机连接诊断 ===")
                                        // 1. 检查绑定状态（使用公共回调属性 printerCallback）
                                        val bindService = InnerPrinterManager.getInstance().bindService(context, printService.printerCallback)
                                        status.add("绑定服务结果: $bindService")

                                        // 2. 从公共方法结果中提取连接状态
                                        val isConnected = connectionStatus["isConnected"] as Boolean
                                        val hasService = connectionStatus["hasService"] as Boolean
                                        status.add("连接状态: $isConnected")
                                        status.add("服务实例: ${if (hasService) "有效" else "null"}")

                                        // 3. 提取打印机状态码和状态描述
                                        val printerStatusCode = connectionStatus["printerStatusCode"] as Int
                                        val printerStatusText = connectionStatus["printerStatusText"] as String
                                        status.add("打印机状态码: $printerStatusCode")
                                        status.add("状态描述: $printerStatusText")

                                        // 4. 补充打印机详细信息
                                        if (printerInfo != "打印机未连接") {
                                            status.add("")
                                            status.add("=== 打印机详细信息 ===")
                                            status.add(printerInfo)
                                        }

                                        // 显示诊断结果
                                        showDiagnosticDialog(
                                            title = "打印机诊断报告",
                                            content = status.joinToString("\n"),
                                            coroutineScope = coroutineScope,
                                            snackbarHostState = snackbarHostState
                                        )

                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("诊断失败: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("检查连接详情")
                        }

                        // 打印测试页（详细版）
                        OutlinedButton(
                            onClick = {
                                showTestPrintDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = printerStatus is PrintStatus.Ready
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("详细测试打印")
                        }

                        // 断开连接按钮（用于测试重连）
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val printService = SunmiPrintService.getInstance(context)
                                        printService.disconnect()
                                        printerViewModel.resetStatus()
                                        snackbarHostState.showSnackbar("已断开连接，请重新初始化")
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("断开失败: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("断开连接")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 企业信息配置卡片
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "企业信息配置",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("企业名称*") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Title, contentDescription = null)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = companyAddress,
                        onValueChange = { companyAddress = it },
                        label = { Text("企业地址") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.LocationOn, contentDescription = null)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = companyPhone,
                        onValueChange = { companyPhone = it },
                        label = { Text("联系电话") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Phone, contentDescription = null)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 打印设置卡片
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "打印设置",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 自动切纸
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自动切纸")
                        Switch(
                            checked = autoCut,
                            onCheckedChange = { autoCut = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 纸张宽度
                    Text("纸张宽度", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = paperWidth == ConfigManager.PAPER_WIDTH_58,
                            onClick = { paperWidth = ConfigManager.PAPER_WIDTH_58 },
                            label = { Text("58mm") }
                        )
                        FilterChip(
                            selected = paperWidth == ConfigManager.PAPER_WIDTH_80,
                            onClick = { paperWidth = ConfigManager.PAPER_WIDTH_80 },
                            label = { Text("80mm") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 字体大小
                    Text("字体大小", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = fontSize == 1,
                            onClick = { fontSize = 1 },
                            label = { Text("标准") }
                        )
                        FilterChip(
                            selected = fontSize == 2,
                            onClick = { fontSize = 2 },
                            label = { Text("大号") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 操作按钮
            Button(
                onClick = {
                    showSaveDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = companyName.isNotBlank()
            ) {
                Text("保存所有配置")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    // 修复：恢复默认值（使用ConfigManager的公开常量/重新赋值默认值，避免访问私有属性）
                    val defaultConfig = ConfigManager(context)
                    companyName = defaultConfig.getCompanyName() // 读取默认值（ConfigManager内部已兜底）
                    companyAddress = defaultConfig.getCompanyAddress()
                    companyPhone = defaultConfig.getCompanyPhone()
                    autoCut = true
                    paperWidth = ConfigManager.PAPER_WIDTH_58
                    fontSize = 1
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("恢复默认值")
            }
        }
    }

    // 保存确认对话框
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存配置") },
            text = { Text("确认保存所有打印配置吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            configManager.saveCompanyInfo(companyName, companyAddress, companyPhone)
                            configManager.setAutoCut(autoCut)
                            configManager.setPaperWidth(paperWidth)
                            configManager.setFontSize(fontSize)

                            showSaveDialog = false
                            snackbarHostState.showSnackbar("配置保存成功")
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 测试打印对话框（简易版）
    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = { Text("测试打印") },
            text = { Text("确认执行测试打印吗？这将打印一张测试页。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        printerViewModel.testPrint()
                        showTestDialog = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTestDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 新增：诊断报告对话框
    if (showDiagnosticDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticDialog = false },
            title = { Text(diagnosticTitle) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 400.dp)
                ) {
                    Text(
                        text = diagnosticContent,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showDiagnosticDialog = false }
                ) {
                    Text("关闭")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // 复制到剪贴板
                        coroutineScope.launch {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            val clip = ClipData.newPlainText("诊断报告", diagnosticContent)
                            clipboard.setPrimaryClip(clip)
                            snackbarHostState.showSnackbar("诊断报告已复制到剪贴板")
                        }
                    }
                ) {
                    Text("复制")
                }
            }
        )
    }

    // 新增：详细测试打印对话框
    if (showTestPrintDialog) {
        var testPrintOption by remember { mutableStateOf(1) }

        AlertDialog(
            onDismissRequest = { showTestPrintDialog = false },
            title = { Text("选择测试类型") },
            text = {
                Column {
                    Text("请选择要执行的测试类型：")
                    Spacer(modifier = Modifier.height(12.dp))

                    // 测试选项
                    RadioButtonItem(
                        selected = testPrintOption == 1,
                        onClick = { testPrintOption = 1 },
                        text = "简单测试页",
                        description = "打印基础文本和表格"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    RadioButtonItem(
                        selected = testPrintOption == 2,
                        onClick = { testPrintOption = 2 },
                        text = "销售单测试",
                        description = "模拟销售单打印"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    RadioButtonItem(
                        selected = testPrintOption == 3,
                        onClick = { testPrintOption = 3 },
                        text = "入库单测试",
                        description = "模拟入库单打印"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    RadioButtonItem(
                        selected = testPrintOption == 4,
                        onClick = { testPrintOption = 4 },
                        text = "包装单测试",
                        description = "模拟包装单打印"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (testPrintOption) {
                            1 -> printerViewModel.testPrint()
                            2 -> {
                                // 模拟销售单打印
                                coroutineScope.launch {
                                    val testItems = listOf(
                                        com.pingwei.lengkubao.service.model.SaleItemPrint(
                                            productName = "测试商品A",
                                            quantity = 10,
                                            unitPrice = 25.50,
                                            amount = 255.00
                                        ),
                                        com.pingwei.lengkubao.service.model.SaleItemPrint(
                                            productName = "测试商品B",
                                            quantity = 5,
                                            unitPrice = 30.00,
                                            amount = 150.00
                                        )
                                    )


                                }
                            }
                            3 -> {
                                // 模拟入库单打印
                                coroutineScope.launch {
                                    val testItems = listOf(
                                        com.pingwei.lengkubao.service.model.InStockItemPrint(
                                            productName = "测试入库商品A",
                                            quantity = 100.0
                                        ),
                                        com.pingwei.lengkubao.service.model.InStockItemPrint(
                                            productName = "测试入库商品B",
                                            quantity = 50.0
                                        )
                                    )

                                    printerViewModel.printInStockBill(
                                        billNo = "TEST-IN-001",
                                        customerName = "测试供应商",
                                        customerCode = "SUPP001",
                                        location = "入库测试库位",
                                        operator = "测试操作员",
                                        items = testItems,
                                        totalItems = 2,
                                        totalQuantity = 150.0,
                                        remark = "这是测试入库单",
                                        creator = "测试系统"
                                    )
                                }
                            }
                            4 -> {
                                // 模拟包装单打印
                                coroutineScope.launch {
                                    val testItems = listOf(
                                        com.pingwei.lengkubao.service.model.PackagingItemPrint(
                                            packagingType = "塑料袋包装",
                                            quantity = 100,
                                            unitPrice = 0.50,
                                            amount = 50.00
                                        ),
                                        com.pingwei.lengkubao.service.model.PackagingItemPrint(
                                            packagingType = "纸箱包装",
                                            quantity = 20,
                                            unitPrice = 5.00,
                                            amount = 100.00
                                        )
                                    )

                                    printerViewModel.printPackagingBill(
                                        billNo = "TEST-PACK-001",
                                        customerName = "测试包装客户",
                                        customerCode = "PACK001",
                                        operator = "测试操作员",
                                        items = testItems,
                                        totalAmount = 150.00,
                                        remark = "这是测试包装单",
                                        creator = "测试系统"
                                    )
                                }
                            }
                        }
                        showTestPrintDialog = false
                    }
                ) {
                    Text("执行测试")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTestPrintDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

// 修复：修正StatusChip函数的参数类型，icon参数改为ImageVector，补充Color导入后可正常使用copy方法
@Composable
fun StatusChip(
    text: String,
    icon: ImageVector, // 修正：改为ImageVector（实际传入的是Icons.Filled下的矢量图，属于ImageVector类型）
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

// 新增：RadioButtonItem Composable 函数
@Composable
fun RadioButtonItem(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
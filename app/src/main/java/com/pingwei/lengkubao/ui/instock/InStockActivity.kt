package com.pingwei.lengkubao.ui.instock

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.service.SunmiPrintService
import com.pingwei.lengkubao.service.SunmiScannerService
import com.pingwei.lengkubao.service.model.InStockItemPrint
import com.pingwei.lengkubao.ui.common.printer.PrintStatus
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinter
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinterFactory
import com.pingwei.lengkubao.ui.instock.components.*
import com.pingwei.lengkubao.ui.instock.viewmodel.InStockViewModel
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import com.pingwei.lengkubao.utils.SunmiScannerReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pingwei.lengkubao.ui.instock.viewmodel.ProductInputItem

class InStockActivity : ComponentActivity() {
    private val TAG = "InStockActivity"

    // 商米扫码头服务
    private lateinit var sunmiScannerService: SunmiScannerService

    // 扫码头广播接收器
    private lateinit var scannerReceiver: SunmiScannerReceiver

    // 扫码启动器（摄像头扫码）
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedContent = result.contents.trim()
            handleScannedCustomer(scannedContent)
        } else {
            runOnUiThread {
                Toast.makeText(this, "扫码取消", Toast.LENGTH_SHORT).show()
            }
            _isScanning.value = false
        }
    }

    private lateinit var viewModel: InStockViewModel

    // 扫码状态
    private val _isScanning = mutableStateOf(false)
    val isScanning: State<Boolean> = _isScanning

    // 最后扫码结果
    private val _lastScannedCode = mutableStateOf("")
    val lastScannedCode: State<String> = _lastScannedCode

    // 扫码头状态
    private val _scannerStatus = mutableStateOf("未连接")
    val scannerStatus: State<String> = _scannerStatus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "✅ InStockActivity onCreate 启动")

        // 初始化商米扫码头服务
        sunmiScannerService = SunmiScannerService(this)

        // 初始化扫码头广播接收器
        scannerReceiver = SunmiScannerReceiver(this) { scannedCode ->
            handleScannedCustomer(scannedCode)
        }

        // 绑定扫码头服务
        sunmiScannerService.bindService()

        // 注册广播接收器
        scannerReceiver.register()

        // 初始化ViewModel
        viewModel = InStockViewModel(application)

        setContent {
            LengkubaoTheme {
                InStockScreen(
                    viewModel = viewModel,
                    isScanning = isScanning.value,
                    lastScannedCode = lastScannedCode.value,
                    scannerStatus = scannerStatus.value,
                    onScanCustomer = { launchScanner() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 更新扫码头状态
        updateScannerStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 解绑扫码头服务
        sunmiScannerService.unbindService()
        // 注销广播接收器
        scannerReceiver.unregister()
    }

    /**
     * 处理物理按键事件 - 扫描头按键触发
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 监听特定按键作为扫码头触发键
        // 商米设备通常使用侧边键或特定功能键
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                // 侧边扫码键（常见于商米设备）
                KeyEvent.KEYCODE_CAMERA -> {
                    Log.d(TAG, "📱 扫码头物理按键按下，触发扫码")
                    sunmiScannerService.startScan()
                    return true
                }
                // F2键（部分设备使用）
                KeyEvent.KEYCODE_F2 -> {
                    Log.d(TAG, "📱 F2按键按下，触发扫码")
                    sunmiScannerService.startScan()
                    return true
                }
                // 其他可能的扫码键
                KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    // 可根据设备实际情况调整
                    Log.d(TAG, "📱 功能键按下，keyCode=${event.keyCode}")
                    sunmiScannerService.startScan()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 更新扫码头状态
     */
    private fun updateScannerStatus() {
        if (sunmiScannerService.isConnected) {
            val model = sunmiScannerService.getScannerModel()
            _scannerStatus.value = "已连接 ($model)"
            Log.d(TAG, "✅ 扫码头状态: ${_scannerStatus.value}")
        } else {
            _scannerStatus.value = "未连接"
            Log.w(TAG, "⚠️ 扫码头未连接")
        }
    }

    // 启动摄像头扫码（原有功能不变）
    private fun launchScanner() {
        try {
            _isScanning.value = true
            _lastScannedCode.value = ""
            val scanOptions = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("请扫描客户二维码")
                setCameraId(0) // 后置摄像头
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(true)
            }
            scanLauncher.launch(scanOptions)
            Log.d(TAG, "📱 摄像头扫码器已启动")
        } catch (e: Exception) {
            _isScanning.value = false
            Log.e(TAG, "❌ 启动摄像头扫码器失败", e)
            Toast.makeText(this, "扫码器启动失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 处理扫码结果 - 统一处理扫码头和摄像头的扫码结果
    private fun handleScannedCustomer(scannedContent: String) {
        Log.d(TAG, "=== 入库开单处理扫码 ===")
        Log.d(TAG, "原始扫码内容: $scannedContent")

        // 提取客户编号
        val customerNo = extractCustomerNoFromQrContent(scannedContent)
        Log.d(TAG, "提取的客户编号: $customerNo")

        if (customerNo.isNullOrBlank()) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "无法识别二维码内容\n格式应为：客户名称（KH001）",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        if (!customerNo.startsWith("KH")) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "客户编号格式错误，应为KH开头\n当前：$customerNo",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        // 后台查询客户
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@InStockActivity)
                val customer = db.customerDao().getByCustomerNo(customerNo)

                // Main线程更新UI
                withContext(Dispatchers.Main) {
                    if (customer != null) {
                        Log.d(TAG, "✅ 成功找到客户: ${customer.customerName} (${customer.customerNo})")
                        viewModel.selectCustomer(customer)
                        Toast.makeText(
                            this@InStockActivity,
                            "已选择客户: ${customer.customerName ?: customer.customerNo}",
                            Toast.LENGTH_SHORT
                        ).show()
                        _lastScannedCode.value = scannedContent
                    } else {
                        Log.w(TAG, "❌ 未找到客户编号: $customerNo")
                        Toast.makeText(
                            this@InStockActivity,
                            "未找到客户: $customerNo",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // 3秒后清空扫码结果
                delay(3000)
                withContext(Dispatchers.Main) {
                    _lastScannedCode.value = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 查询客户失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@InStockActivity,
                        "查询失败：${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                delay(3000)
                withContext(Dispatchers.Main) {
                    _lastScannedCode.value = ""
                }
            }
        }
    }

    // 提取客户编号（复用原有逻辑）
    private fun extractCustomerNoFromQrContent(content: String): String? {
        if (content.isBlank()) return null

        // 格式1：中文括号 "客户名称（KH001）"
        val chinesePattern = Regex("（([^）]+)）")
        chinesePattern.find(content)?.let {
            val result = it.groupValues[1].trim()
            Log.d(TAG, "匹配中文括号: $result")
            if (result.startsWith("KH")) return result
        }

        // 格式2：英文括号 "客户名称(KH001)"
        val englishPattern = Regex("\\(([^)]+)\\)")
        englishPattern.find(content)?.let {
            val result = it.groupValues[1].trim()
            Log.d(TAG, "匹配英文括号: $result")
            if (result.startsWith("KH")) return result
        }

        // 格式3：纯客户编号（以KH开头）
        if (content.startsWith("KH") && content.length >= 5) {
            Log.d(TAG, "匹配纯编号格式: $content")
            return content
        }

        // 格式4：尝试查找KH开头的部分
        val khPattern = Regex("KH\\d+")
        khPattern.find(content)?.let {
            val result = it.value
            Log.d(TAG, "匹配KH开头编号: $result")
            return result
        }

        // 格式5：旧格式兼容（C开头转KH开头）
        val cPattern = Regex("C\\d+")
        cPattern.find(content)?.let {
            val oldNo = it.value
            val newNo = oldNo.replaceFirst("C", "KH")
            Log.d(TAG, "转换旧格式: $oldNo -> $newNo")
            return newNo
        }

        Log.d(TAG, "未匹配到任何客户编号格式")
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InStockScreen(
    viewModel: InStockViewModel,
    isScanning: Boolean,
    lastScannedCode: String,
    scannerStatus: String, // 新增：扫码头状态
    onScanCustomer: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val TAG = "InStockScreen"

    // 【修复】打印机ViewModel工厂+ViewModel判空，避免空指针
    val printerFactory = SaleBillPrinterFactory(context)
    val printerViewModel: SaleBillPrinter? = if (printerFactory != null) {
        viewModel(factory = printerFactory)
    } else {
        Log.e(TAG, "❌ 打印机ViewModel工厂创建失败")
        null
    }
    val printStatus by printerViewModel?.printStatus?.collectAsState(initial = PrintStatus.Idle)
        ?: remember { mutableStateOf(PrintStatus.Idle) }

    // 初始化配置管理器 - 页面启动仅执行一次
    LaunchedEffect(Unit) {
        try {
            viewModel.initConfigManager(context)
            Log.d(TAG, "✅ 页面启动，初始化ConfigManager完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化ConfigManager失败", e)
            Toast.makeText(context, "配置初始化失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 状态管理
    var showLocationDialog by remember { mutableStateOf(false) }
    var showOperatorDialog by remember { mutableStateOf(false) }
    var showSaveSuccessDialog by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var printMessage by remember { mutableStateOf("正在准备打印...") }
    var savedBillId by remember { mutableLongStateOf(0L) }
    var savedBillNo by remember { mutableStateOf("") }
    var isPrinting by remember { mutableStateOf(false) }
    var pendingChoiceDialog by remember { mutableStateOf(false) }

    // 收集ViewModel状态
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val selectedOperator by viewModel.selectedOperator.collectAsState()
    val productInputs by viewModel.productInputs.collectAsState()
    val totalAmount by viewModel.totalAmount.collectAsState()
    val totalQuantity by viewModel.totalQuantity.collectAsState()
    val remark by viewModel.remark.collectAsState()
    val allCustomers = viewModel.allCustomers.collectAsState(initial = emptyList()).value
    val locations = viewModel.allLocations.collectAsState(initial = emptyList()).value
    val operators = viewModel.allOperators.collectAsState(initial = emptyList()).value

    fun showPostPrintChoiceDialog() {
        pendingChoiceDialog = false
        showPrintDialog = false
        showSaveSuccessDialog = true
    }

    // 监听打印状态变化
    LaunchedEffect(printStatus) {
        when (printStatus) {
            is PrintStatus.Success -> {
                printMessage = "✅ 打印成功！"
                isPrinting = false
                if (showPrintDialog) {
                    delay(800)
                    showPostPrintChoiceDialog()
                }
                Toast.makeText(context, "打印成功", Toast.LENGTH_SHORT).show()
            }
            is PrintStatus.Error -> {
                val error = printStatus as PrintStatus.Error
                printMessage = "❌ ${error.message}"
                isPrinting = false
                Log.e(TAG, "❌ 打印失败: ${error.message}")
                if (showPrintDialog && pendingChoiceDialog) {
                    showPostPrintChoiceDialog()
                }
            }
            is PrintStatus.Printing -> {
                printMessage = "正在打印中..."
                isPrinting = true
            }
            else -> {}
        }
    }

    // 扫码结果显示逻辑
    var localLastScannedCode by remember { mutableStateOf("") }
    LaunchedEffect(lastScannedCode) {
        if (lastScannedCode.isNotEmpty()) {
            localLastScannedCode = lastScannedCode
            Log.d(TAG, "📤 扫码结果更新: $localLastScannedCode")
        }
    }

    // 库位选择对话框
    if (showLocationDialog) {
        LocationSelectorDialog(
            locations = locations,
            onDismiss = { showLocationDialog = false },
            onLocationSelected = {
                viewModel.selectLocation(it)
                viewModel.saveAsDefaultLocation()
                showLocationDialog = false
                Log.d(TAG, "📍 选择库位: ${it.locationName}")
            }
        )
    }

    // 经手人选择对话框
    if (showOperatorDialog) {
        OperatorSelectorDialog(
            operators = operators,
            onDismiss = { showOperatorDialog = false },
            onOperatorSelected = {
                viewModel.selectOperator(it)
                viewModel.saveAsDefaultHandler()
                showOperatorDialog = false
                Log.d(TAG, "👷 选择经手人: ${it.name}")
            }
        )
    }

    // 保存并打印完成后，询问是否再打印一张
    if (showSaveSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("✅ 保存成功") },
            text = { Text("入库单 $savedBillNo 已保存并打印，是否再打印一张？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveSuccessDialog = false
                        showPrintDialog = true
                    },
                    enabled = !isPrinting
                ) {
                    Text("立即打印")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveSuccessDialog = false
                        viewModel.clearAll()
                        savedBillId = 0L
                        savedBillNo = ""
                    },
                    enabled = !isPrinting
                ) {
                    Text("继续开单")
                }
            }
        )
    }

    // 打印对话框 - 【修复】全量异常捕获、打印机服务判空
    if (showPrintDialog) {
        LaunchedEffect(showPrintDialog) {
            try {
                if (showPrintDialog && savedBillId > 0 && !isPrinting) {
                    isPrinting = true
                    printMessage = "正在获取单据信息..."
                    Log.d(TAG, "🖨️ 开始打印流程，单据ID: $savedBillId")

                    // 1. 获取单据详情
                    val bill = viewModel.getInStockBill(savedBillId)
                    val items = viewModel.getInStockItems(savedBillId)
                    if (bill == null || items.isEmpty()) {
                        printMessage = "❌ 获取单据信息失败，无数据"
                        isPrinting = false
                        Log.e(TAG, "❌ 打印失败：单据/明细为空，ID: $savedBillId")
                        return@LaunchedEffect
                    }

                    // 2. 转换为打印模型
                    val printItems = items.map { item ->
                        InStockItemPrint(
                            productName = item.productName,
                            quantity = item.quantity.toDouble(),
                            unit = item.unit ?: "件"
                        )
                    }

                    // 3. 打印机服务初始化+连接
                    printMessage = "正在连接打印机..."
                    val printService = SunmiPrintService.getInstance(context) ?: run {
                        printMessage = "❌ 打印机服务未初始化"
                        isPrinting = false
                        Log.e(TAG, "❌ 打印失败：SunmiPrintService为null")
                        return@LaunchedEffect
                    }

                    // 重试连接3次
                    var connectionAttempts = 0
                    var connected = false
                    while (!connected && connectionAttempts < 3) {
                        connectionAttempts++
                        printMessage = "连接打印机尝试 $connectionAttempts/3..."
                        try {
                            connected = printService.ensureConnectedForPrint()
                            if (connected) {
                                printMessage = "打印机连接成功，开始打印..."
                                Log.d(TAG, "✅ 打印机连接成功，尝试次数: $connectionAttempts")
                            } else {
                                printMessage = "连接失败，等待重试..."
                                delay(1000)
                            }
                        } catch (e: Exception) {
                            printMessage = "连接异常: ${e.message?.take(20)}"
                            delay(1000)
                            Log.w(TAG, "⚠️ 打印机连接尝试${connectionAttempts}失败: ${e.message}")
                        }
                    }

                    if (!connected) {
                        printMessage = "❌ 打印机连接失败，请检查设备"
                        isPrinting = false
                        Log.e(TAG, "❌ 打印机3次连接均失败")
                        return@LaunchedEffect
                    }

                    // 4. 调用打印 - 判空打印机ViewModel
                    printerViewModel?.let { pvm ->
                        pvm.printInStockBill(
                            billNo = bill.billNo,
                            customerName = bill.customerName ?: selectedCustomer?.customerName ?: "未知客户",
                            customerCode = bill.customerNo ?: "",
                            location = bill.locationName ?: selectedLocation?.locationName ?: "未知库位",
                            operator = bill.operatorName ?: selectedOperator?.name ?: "未知经手人",
                            items = printItems,
                            totalItems = items.size,
                            totalQuantity = bill.totalQuantity.toDouble(),
                            remark = bill.remark ?: "无备注",
                            creator = selectedOperator?.name ?: "系统"
                        )
                        Log.d(TAG, "✅ 调用打印接口，单据号: ${bill.billNo}")
                    } ?: run {
                        printMessage = "❌ 打印服务初始化失败"
                        isPrinting = false
                        Log.e(TAG, "❌ 打印失败：printerViewModel为null")
                    }
                }
            } catch (e: Exception) {
                printMessage = "❌ 打印异常: ${e.message?.take(20)}"
                isPrinting = false
                Toast.makeText(context, "打印准备失败: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "❌ 打印流程未捕获异常", e)
            }
        }

        AlertDialog(
            onDismissRequest = { },
            title = { Text("打印入库单") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(printMessage, textAlign = TextAlign.Center)
                    if (isPrinting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            },
            confirmButton = {
                if (!isPrinting) {
                    TextButton(
                        onClick = { showPostPrintChoiceDialog() }
                    ) {
                        Text("确定")
                    }
                }
            }
        )
    }

    // 滚动状态
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("入库开单")
                            if (isScanning) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "扫码模式",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "扫码中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        // 扫码头状态显示
                        Text(
                            text = scannerStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (scannerStatus.contains("已连接"))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    var showSetDefaultsDialog by remember { mutableStateOf(false) }

                    IconButton(onClick = { showSetDefaultsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置默认值")
                    }

                    IconButton(
                        onClick = { viewModel.clearAll() },
                        enabled = selectedCustomer != null || selectedLocation != null ||
                                selectedOperator != null || productInputs.any { it.quantity > 0 }
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = "清空")
                    }

                    // 设置默认值对话框
                    if (showSetDefaultsDialog) {
                        AlertDialog(
                            onDismissRequest = { showSetDefaultsDialog = false },
                            title = { Text("设置默认值") },
                            text = {
                                Column {
                                    Text("将当前选择的项目设置为默认值：")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    selectedLocation?.let { Text("• 库位: ${it.locationName}") }
                                    selectedOperator?.let { Text("• 经手人: ${it.name}") }
                                    if (selectedLocation == null && selectedOperator == null) {
                                        Text("请先选择库位或经手人", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        selectedLocation?.let { viewModel.saveAsDefaultLocation() }
                                        selectedOperator?.let { viewModel.saveAsDefaultHandler() }
                                        Toast.makeText(context, "默认值已保存", Toast.LENGTH_SHORT).show()
                                        showSetDefaultsDialog = false
                                    },
                                    enabled = selectedLocation != null || selectedOperator != null
                                ) {
                                    Text("保存")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSetDefaultsDialog = false }) {
                                    Text("取消")
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 可滚动内容区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(AppDimens.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
                ) {
                    // 1. 客户信息卡片
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "客户信息",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SearchableCustomerField(
                                    customers = allCustomers,
                                    selectedCustomer = selectedCustomer,
                                    onCustomerSelected = { customer ->
                                        viewModel.selectCustomer(customer)
                                        if (customer != null) {
                                            Log.d(TAG, "👤 从列表选择客户: ${customer.customerName}")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    isError = selectedCustomer == null
                                )

                                // 扫码按钮
                                IconButton(
                                    onClick = { onScanCustomer() },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码选择", Modifier.size(28.dp))
                                        Text("扫码", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                                    }
                                }
                            }

                        }
                    }

                    // 2. 商品明细卡片 - 简化版（完全固定，不限制高度）
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(AppDimens.pagePadding)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("商品明细", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "已选: ${productInputs.count { it.quantity > 0 }} 种",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (productInputs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Inventory,
                                            contentDescription = "暂无商品",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("暂无商品型号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else {
                                // 【修复】使用Column代替LazyColumn，商品明细完全展开
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    productInputs.forEach { inputItem ->
                                        DirectProductInputItem(
                                            inputItem = inputItem,
                                            onQuantityChange = { quantity ->
                                                viewModel.updateProductQuantity(inputItem.product.id, quantity)
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("总计数量：", style = MaterialTheme.typography.bodyMedium)
                                Text("$totalQuantity", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }

                    // 3. 库位和经手人卡片
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "其他信息",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // 库位选择
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = selectedLocation?.let { "${it.locationName} (${it.locationNo})" } ?: "请选择库位",
                                    onValueChange = {},
                                    label = { Text("库位") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showLocationDialog = true },
                                    readOnly = true,
                                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "库位") },
                                    trailingIcon = {
                                        Row {
                                            if (selectedLocation?.id == viewModel.getDefaultLocationId()) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = "默认库位",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            IconButton(onClick = { showLocationDialog = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "选择库位")
                                            }
                                        }
                                    },
                                    isError = selectedLocation == null
                                )
                            }

                            // 经手人选择
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = selectedOperator?.name ?: "请选择经手人",
                                    onValueChange = {},
                                    label = { Text("经手人") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showOperatorDialog = true },
                                    readOnly = true,
                                    leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = "经手人") },
                                    trailingIcon = {
                                        Row {
                                            if (selectedOperator?.id == viewModel.getDefaultHandlerId()) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = "默认经手人",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            IconButton(onClick = { showOperatorDialog = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "选择经手人")
                                            }
                                        }
                                    },
                                    isError = selectedOperator == null
                                )
                            }
                        }
                    }

                    // 4. 备注信息卡片
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(AppDimens.pagePadding)) {
                            Text(
                                text = "备注信息",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = remark,
                                onValueChange = { viewModel.setRemark(it) },
                                label = { Text("请输入备注信息（可选）") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )
                        }
                    }
                }

                // 底部固定保存按钮
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("已选种类", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${productInputs.count { it.quantity > 0 }} 种",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("总计数量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "$totalQuantity",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 保存按钮 - 【修复】全量异常捕获
                        // 保存按钮 - 【修复】全量异常捕获 + 移除实时同步逻辑
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val result = viewModel.saveBill()
                                        if (result.isSuccess) {
                                            val (billId, billNo) = result.getOrNull()!!
                                            savedBillId = billId
                                            savedBillNo = billNo
                                            pendingChoiceDialog = true
                                            showPrintDialog = true
                                            // 仅打印日志，不触发实时同步，依赖TCP连接成功后自动同步
                                            Log.d(TAG, "✅ 保存入库单成功，单号: $billNo，已标记为未同步状态，等待TCP连接后自动同步")
                                        } else {
                                            val errorMsg = result.exceptionOrNull()?.message ?: "保存失败，原因未知"
                                            Toast.makeText(context, "保存失败：$errorMsg", Toast.LENGTH_SHORT).show()
                                            Log.e(TAG, "❌ 保存入库单失败", result.exceptionOrNull())
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "保存异常：${e.message}", Toast.LENGTH_LONG).show()
                                        Log.e(TAG, "❌ 保存入库单未捕获异常", e)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AppDimens.buttonHeight),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            enabled = selectedCustomer != null &&
                                    selectedLocation != null &&
                                    selectedOperator != null &&
                                    productInputs.any { it.quantity > 0 }
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("保存入库单", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            // 扫码成功提示
            androidx.compose.animation.AnimatedVisibility(
                visible = localLastScannedCode.isNotEmpty(),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AppDimens.pagePadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "扫码成功",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("已扫码: $localLastScannedCode", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

// 直接商品输入项组件 - 修复版
@Composable
private fun DirectProductInputItem(
    inputItem: ProductInputItem,
    onQuantityChange: (Int) -> Unit,
) {
    // 【修复】不再使用 remember 缓存本地状态，直接使用 inputItem.quantity
    // 当 inputItem.quantity 变化时，UI 会自动刷新
    val quantityText = if (inputItem.quantity > 0) inputItem.quantity.toString() else ""
    var showCustomKeyboard by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = inputItem.product.productName,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(modifier = Modifier.width(88.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .clickable { showCustomKeyboard = true }
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Text(quantityText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                // 自定义数字键盘弹窗
                if (showCustomKeyboard) {
                    CustomNumberDialog(
                        currentValue = quantityText,
                        onValueChanged = { newValue ->
                            // 【修复】这里直接调用 onQuantityChange，不缓存本地状态
                            onQuantityChange(newValue.toIntOrNull() ?: 0)
                        },
                        onConfirm = { showCustomKeyboard = false },
                        onDismiss = { showCustomKeyboard = false }
                    )
                }
            }
        }
    }
}

// 自定义数字键盘对话框（紧凑版本）- 修复版
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomNumberDialog(
    currentValue: String,
    onValueChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // 【修复】使用 mutableStateOf 管理临时输入，但确认后才提交
    var tempValue by remember { mutableStateOf(currentValue) }

    // 当 currentValue 变化时，更新 tempValue
    LaunchedEffect(currentValue) {
        tempValue = currentValue
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.pagePadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 输入值显示
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tempValue.ifEmpty { "0" },
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 数字键盘
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "←")
                )
                keys.forEach { rowKeys ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        rowKeys.forEach { key ->
                            val isSpecialKey = key == "C" || key == "←"
                            val bgColor = if (isSpecialKey) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                            val textColor = if (isSpecialKey) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(bgColor)
                                    .clickable {
                                        when (key) {
                                            "C" -> {
                                                tempValue = ""
                                                onValueChanged("") // 实时更新，让外部知道清空了
                                            }
                                            "←" -> {
                                                tempValue = tempValue.dropLast(1)
                                                onValueChanged(tempValue) // 实时更新
                                            }
                                            else -> {
                                                if (tempValue.length < 4) {
                                                    tempValue = tempValue + key
                                                    onValueChanged(tempValue) // 实时更新
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key,
                                    fontSize = if (isSpecialKey) 16.sp else 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            // 【修复】确认时才调用 onConfirm，但数量已经通过 onValueChanged 实时更新了
                            onConfirm()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

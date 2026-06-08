package com.pingwei.lengkubao.ui.packaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.service.TcpSyncService
import com.pingwei.lengkubao.service.model.PackagingItemPrint
import com.pingwei.lengkubao.sync.TcpSyncManager
import com.pingwei.lengkubao.ui.common.printer.PrintStatus
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinter
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinterFactory
import com.pingwei.lengkubao.ui.instock.components.*
import com.pingwei.lengkubao.ui.packaging.viewmodel.PackagingViewModel
import com.pingwei.lengkubao.ui.packaging.viewmodel.PackagingInputItem
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 添加广播常量
private const val ACTION_SELECT_CUSTOMER = "com.pingwei.lengkubao.SELECT_CUSTOMER"
private const val EXTRA_CUSTOMER_NO = "customer_no"

class PackagingActivity : ComponentActivity() {
    companion object {
        private const val TAG = "PackagingActivity"
    }

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

    private lateinit var localBroadcastManager: LocalBroadcastManager

    private val _isScanning = mutableStateOf(false)
    val isScanning: State<Boolean> = _isScanning

    private val _lastScannedCode = mutableStateOf("")
    val lastScannedCode: State<String> = _lastScannedCode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        setContent {
            LengkubaoTheme {
                PackagingScreen(
                    isScanning = isScanning.value,
                    lastScannedCode = lastScannedCode.value,
                    onScanCustomer = { launchScanner() }
                )
            }
        }
    }

    private fun launchScanner() {
        try {
            _isScanning.value = true
            _lastScannedCode.value = ""
            val scanOptions = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("请扫描客户二维码")
                setCameraId(0)
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(true)
            }
            scanLauncher.launch(scanOptions)
            Log.d(TAG, "📱 扫码器已启动")
        } catch (e: Exception) {
            _isScanning.value = false
            Log.e(TAG, "❌ 启动扫码器失败", e)
            Toast.makeText(this, "扫码器启动失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleScannedCustomer(scannedContent: String) {
        _isScanning.value = false
        _lastScannedCode.value = scannedContent

        Log.d(TAG, "=== 包装记账处理扫码 ===")
        Log.d(TAG, "原始扫码内容: $scannedContent")

        val customerNo = extractCustomerNoFromQrContent(scannedContent)
        Log.d(TAG, "提取的客户编号: $customerNo")

        if (customerNo.isNullOrBlank()) {
            Toast.makeText(
                this,
                "无法识别二维码内容\n格式应为：客户名称（KH001）",
                Toast.LENGTH_LONG
            ).show()
            lifecycleScope.launch {
                delay(3000)
                _lastScannedCode.value = ""
            }
            return
        }

        if (!customerNo.startsWith("KH")) {
            Toast.makeText(
                this,
                "客户编号格式错误，应为KH开头\n当前：$customerNo",
                Toast.LENGTH_LONG
            ).show()
            lifecycleScope.launch {
                delay(3000)
                _lastScannedCode.value = ""
            }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@PackagingActivity)
                val customer = db.customerDao().getByCustomerNo(customerNo)

                withContext(Dispatchers.Main) {
                    if (customer != null) {
                        Log.d(TAG, "✅ 成功找到客户: ${customer.customerName} (${customer.customerNo})")
                        val intent = Intent(ACTION_SELECT_CUSTOMER).apply {
                            putExtra(EXTRA_CUSTOMER_NO, customerNo)
                        }
                        localBroadcastManager.sendBroadcast(intent)

                        Toast.makeText(
                            this@PackagingActivity,
                            "已选择客户: ${customer.customerName ?: customer.customerNo}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Log.w(TAG, "❌ 未找到客户编号: $customerNo")
                        Toast.makeText(
                            this@PackagingActivity,
                            "未找到客户: $customerNo",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                delay(3000)
                withContext(Dispatchers.Main) {
                    _lastScannedCode.value = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 查询客户失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@PackagingActivity,
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

    private fun extractCustomerNoFromQrContent(content: String): String? {
        if (content.isBlank()) return null

        val chinesePattern = Regex("（([^）]+)）")
        chinesePattern.find(content)?.let {
            val result = it.groupValues[1].trim()
            Log.d(TAG, "匹配中文括号: $result")
            if (result.startsWith("KH")) return result
        }

        val englishPattern = Regex("\\(([^)]+)\\)")
        englishPattern.find(content)?.let {
            val result = it.groupValues[1].trim()
            Log.d(TAG, "匹配英文括号: $result")
            if (result.startsWith("KH")) return result
        }

        if (content.startsWith("KH") && content.length >= 5) {
            Log.d(TAG, "匹配纯编号格式: $content")
            return content
        }

        val khPattern = Regex("KH\\d+")
        khPattern.find(content)?.let {
            val result = it.value
            Log.d(TAG, "匹配KH开头编号: $result")
            return result
        }

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
fun PackagingScreen(
    viewModel: PackagingViewModel = viewModel(),
    isScanning: Boolean = false,
    lastScannedCode: String = "",
    onScanCustomer: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showOperatorDialog by remember { mutableStateOf(false) }
    var showSaveSuccessDialog by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var printMessage by remember { mutableStateOf("正在准备打印...") }
    var savedBillId by remember { mutableLongStateOf(0L) }
    var savedBillNo by remember { mutableStateOf("") }
    var isPrinting by remember { mutableStateOf(false) }
    var pendingChoiceDialog by remember { mutableStateOf(false) }

    var localLastScannedCode by remember { mutableStateOf("") }

    LaunchedEffect(lastScannedCode) {
        if (lastScannedCode.isNotEmpty()) {
            localLastScannedCode = lastScannedCode
            delay(3000)
            localLastScannedCode = ""
        }
    }

    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val selectedOperator by viewModel.selectedOperator.collectAsState()
    val packagingInputs by viewModel.packagingInputs.collectAsState()
    val totalAmount by viewModel.totalAmount.collectAsState()
    val remark by viewModel.remark.collectAsState()
    val packagingTypeFlag by viewModel.packagingTypeFlag.collectAsState() // 【新增】获取包装类型标记
    val allCustomers = viewModel.allCustomers.collectAsState(initial = emptyList()).value
    val allOperators = viewModel.allOperators.collectAsState(initial = emptyList()).value

    val broadcastReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_SELECT_CUSTOMER) {
                    val customerNo = intent.getStringExtra(EXTRA_CUSTOMER_NO) ?: return

                    coroutineScope.launch {
                        val customer = withContext(Dispatchers.IO) {
                            val database = AppDatabase.getInstance(context!!)
                            database.customerDao().getByCustomerNo(customerNo)
                        }

                        if (customer != null) {
                            viewModel.selectCustomer(customer)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_SELECT_CUSTOMER)
        LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver, filter)

        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
        }
    }

    val printerFactory = SaleBillPrinterFactory(context)
    val printerViewModel: SaleBillPrinter = viewModel(factory = printerFactory)
    val printStatus by printerViewModel.printStatus.collectAsState()

    fun showPostPrintChoiceDialog() {
        pendingChoiceDialog = false
        showPrintDialog = false
        showSaveSuccessDialog = true
    }

    LaunchedEffect(printStatus) {
        when (printStatus) {
            is PrintStatus.Success -> {
                printMessage = "✅ 打印成功！"
                isPrinting = false
                if (showPrintDialog) {
                    delay(800)
                    showPostPrintChoiceDialog()
                }
            }
            is PrintStatus.Error -> {
                val error = printStatus as PrintStatus.Error
                printMessage = "❌ ${error.message}"
                isPrinting = false
                if (showPrintDialog && pendingChoiceDialog) {
                    showPostPrintChoiceDialog()
                }
            }
            is PrintStatus.Printing -> {
                printMessage = "正在打印中..."
                isPrinting = true
            }
            PrintStatus.Ready -> {
                printMessage = "准备就绪"
            }
            PrintStatus.Initializing -> {
                printMessage = "正在初始化..."
            }
            PrintStatus.Idle -> {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initConfigManager(context)
    }

    LaunchedEffect(selectedCustomer) {
        selectedCustomer?.let {
            localLastScannedCode = it.customerNo
        }
    }

    if (showOperatorDialog) {
        OperatorSelectorDialog(
            operators = allOperators,
            onDismiss = { showOperatorDialog = false },
            onOperatorSelected = {
                viewModel.selectOperator(it)
                viewModel.saveAsDefaultHandler()
            }
        )
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("包装记账")

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
                },
                navigationIcon = {
                    IconButton(onClick = {
                        (context as? ComponentActivity)?.finish()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    var showSetDefaultsDialog by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = { showSetDefaultsDialog = true }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "设置默认值")
                    }

                    IconButton(
                        onClick = {
                            TcpSyncService.syncPendingNow(context)
                            Toast.makeText(context, "已提交后台同步请求", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "同步未完成单据",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.clearAll() },
                        enabled = selectedCustomer != null || selectedOperator != null ||
                                packagingInputs.any { it.quantity > 0 }
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = "清空")
                    }

                    if (showSetDefaultsDialog) {
                        AlertDialog(
                            onDismissRequest = { showSetDefaultsDialog = false },
                            title = { Text("设置默认值") },
                            text = {
                                Column {
                                    Text("将当前选择的经手人设置为默认值：")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    selectedOperator?.let {
                                        Text("• 经手人: ${it.name}")
                                    }
                                    if (selectedOperator == null) {
                                        Text("请先选择经手人")
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        selectedOperator?.let { viewModel.saveAsDefaultHandler() }
                                        Toast.makeText(context, "默认值已保存", Toast.LENGTH_SHORT).show()
                                        showSetDefaultsDialog = false
                                    },
                                    enabled = selectedOperator != null
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(AppDimens.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
                ) {
                    // ========== 1. 客户信息卡片 ==========
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                                    },
                                    modifier = Modifier.weight(1f),
                                    isError = selectedCustomer == null
                                )

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

                    // ========== 1.5 包装类型选择卡片（新增） ==========
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "包装类型",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 取包装选项
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setPackagingTypeFlag("TAKE") },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (packagingTypeFlag == "TAKE")
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (packagingTypeFlag == "TAKE") 4.dp else 1.dp
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Outbound,
                                            contentDescription = "取包装",
                                            tint = if (packagingTypeFlag == "TAKE")
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "取包装",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (packagingTypeFlag == "TAKE")
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "客户领取包装",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // 退包装选项
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setPackagingTypeFlag("RETURN") },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (packagingTypeFlag == "RETURN")
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (packagingTypeFlag == "RETURN") 4.dp else 1.dp
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowBack,
                                            contentDescription = "退包装",
                                            tint = if (packagingTypeFlag == "RETURN")
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "退包装",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (packagingTypeFlag == "RETURN")
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "客户退回包装",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 添加说明文本
                            Text(
                                text = if (packagingTypeFlag == "TAKE")
                                    "取包装：客户领取包装，金额计入应收账款"
                                else "退包装：客户退回包装，金额从应收账款扣除",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // ========== 2. 包装明细卡片 ==========
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "包装明细",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = "已选: ${packagingInputs.count { it.quantity > 0 }} 种",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (packagingInputs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Inventory2,
                                            contentDescription = "暂无包装类型",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "暂无包装类型",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    packagingInputs.forEach { inputItem ->
                                        OptimizedPackagingInputItem(
                                            inputItem = inputItem,
                                            packagingTypeFlag = packagingTypeFlag, // 【新增】传入包装类型标记
                                            onQuantityChange = { quantity ->
                                                viewModel.updatePackagingQuantity(inputItem.packagingType.id, quantity)
                                            },
                                            onPriceChange = { price ->
                                                viewModel.updatePackagingPrice(inputItem.packagingType.id, price)
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                                ) {
                                    Divider()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("包装总金额：", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "¥${String.format("%.2f", totalAmount)}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (packagingTypeFlag == "RETURN" && totalAmount > 0)
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ========== 3. 经手人卡片 ==========
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "其他信息",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Box(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedOperator?.name ?: "请选择经手人",
                                    onValueChange = {},
                                    label = { Text("经手人") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showOperatorDialog = true },
                                    readOnly = true,
                                    leadingIcon = {
                                        Icon(Icons.Default.PersonOutline, contentDescription = "经手人")
                                    },
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

                    // ========== 4. 备注信息卡片 ==========
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding)
                        ) {
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

                // ===== 底部固定保存按钮 =====
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 包装种类
                            Column {
                                Text(
                                    "包装种类",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${packagingInputs.count { it.quantity > 0 }} 种",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // 【新增】包装类型标记显示
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "包装类型",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (packagingTypeFlag == "TAKE")
                                            Icons.Default.Outbound else Icons.Default.ArrowBack,
                                        contentDescription = null,
                                        tint = if (packagingTypeFlag == "TAKE")
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (packagingTypeFlag == "TAKE") "取包装" else "退包装",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (packagingTypeFlag == "TAKE")
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // 包装总数量
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "包装总数量",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${packagingInputs.sumOf { it.quantity }} 个",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // 包装总金额
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "包装总金额",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "¥${String.format("%.2f", totalAmount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (packagingTypeFlag == "RETURN" && totalAmount > 0)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val result = viewModel.saveBill()
                                    if (result.isSuccess) {
                                        val (billId, billNo) = result.getOrNull()!!
                                        savedBillId = billId
                                        savedBillNo = billNo
                                        pendingChoiceDialog = true
                                        showPrintDialog = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "保存失败：${result.exceptionOrNull()?.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
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
                                    selectedOperator != null &&
                                    packagingInputs.any { it.quantity > 0 }
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "保存包装单",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

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
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .widthIn(max = 300.dp)
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
                            Text(
                                "已扫码: $localLastScannedCode",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("✅ 保存成功") },
            text = { Text("包装单 $savedBillNo 已保存并打印，是否再打印一张？") },
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

    if (showPrintDialog) {
        LaunchedEffect(showPrintDialog) {
            if (showPrintDialog && savedBillId > 0 && !isPrinting) {
                isPrinting = true
                printMessage = "正在获取单据信息..."

                try {
                    val bill = viewModel.getPackagingBill(savedBillId)
                    val items = viewModel.getPackagingItems(savedBillId)

                    if (bill == null || items.isEmpty()) {
                        printMessage = "获取单据信息失败"
                        isPrinting = false
                        return@LaunchedEffect
                    }

                    val printItems = items.map { item ->
                        PackagingItemPrint(
                            packagingTypeFlag = item.packagingTypeFlag,
                            packagingType = item.packagingTypeName,
                            quantity = item.quantity,
                            unitPrice = item.unitPrice,
                            amount = item.amount
                        )
                    }

                    printMessage = "正在连接打印机..."
                    printerViewModel.printPackagingBill(
                        billNo = bill.billNo,
                        customerName = bill.customerName,
                        customerCode = bill.customerNo,
                        operator = bill.operatorName,
                        items = printItems,
                        totalAmount = bill.totalAmount,
                        remark = bill.remark ?: "无备注",
                        creator = bill.operatorName ?: "系统"
                    )

                } catch (e: Exception) {
                    printMessage = "❌ 打印异常: ${e.message}"
                    isPrinting = false
                }
            }
        }

        AlertDialog(
            onDismissRequest = { },
            title = { Text("📄 打印包装单") },
            text = {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
                ) {
                    Text(printMessage)
                    if (isPrinting) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isPrinting) {
                            showPostPrintChoiceDialog()
                        }
                    },
                    enabled = !isPrinting
                ) {
                    Text("确定")
                }
            }
        )
    }
}

// OptimizedPackagingInputItem 组件 - 添加取/退标记支持
@Composable
fun OptimizedPackagingInputItem(
    inputItem: PackagingInputItem,
    packagingTypeFlag: String, // 【新增】包装类型标记参数
    onQuantityChange: (Int) -> Unit,
    onPriceChange: (Double) -> Unit
) {
    val quantityText = if (inputItem.quantity > 0) inputItem.quantity.toString() else ""
    val priceText = if (inputItem.unitPrice > 0) {
        if (inputItem.unitPrice == inputItem.unitPrice.toInt().toDouble())
            inputItem.unitPrice.toInt().toString()
        else
            String.format("%.2f", inputItem.unitPrice)
    } else ""

    var showCustomKeyboard by remember { mutableStateOf(false) }
    var activeInputField by remember { mutableStateOf<InputField?>(null) }

    // 【新增】根据取/退标记调整背景色
    val itemBackgroundColor = if (packagingTypeFlag == "RETURN")
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = itemBackgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = inputItem.packagingType.typeName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (packagingTypeFlag == "TAKE")
                        Icons.Default.Outbound else Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = if (packagingTypeFlag == "TAKE")
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
            }

            Box(
                modifier = Modifier.width(72.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                        .clickable {
                            activeInputField = InputField.QUANTITY
                            showCustomKeyboard = true
                        }
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.small
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (quantityText.isEmpty()) "数量" else quantityText,
                        fontSize = 14.sp,
                        color = if (quantityText.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier.width(88.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                        .clickable {
                            activeInputField = InputField.PRICE
                            showCustomKeyboard = true
                        }
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.small
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (priceText.isEmpty()) "单价" else priceText,
                        fontSize = 14.sp,
                        color = if (priceText.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (showCustomKeyboard) {
                CustomNumberDialog(
                    title = when (activeInputField) {
                        InputField.QUANTITY -> "输入数量"
                        InputField.PRICE -> "输入单价"
                        else -> "输入"
                    },
                    currentValue = when (activeInputField) {
                        InputField.QUANTITY -> quantityText
                        InputField.PRICE -> priceText
                        else -> ""
                    },
                    onValueChanged = { newValue ->
                        when (activeInputField) {
                            InputField.QUANTITY -> {
                                val quantity = newValue.toIntOrNull() ?: 0
                                onQuantityChange(quantity)
                            }
                            InputField.PRICE -> {
                                val price = newValue.toDoubleOrNull() ?: 0.0
                                onPriceChange(price)
                            }
                            else -> {}
                        }
                    },
                    onConfirm = {
                        showCustomKeyboard = false
                        activeInputField = null
                    },
                    onDismiss = {
                        showCustomKeyboard = false
                        activeInputField = null
                    }
                )
            }
        }
    }
}

private enum class InputField {
    QUANTITY, PRICE
}

// 自定义数字键盘对话框 - 修复版
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomNumberDialog(
    title: String,
    currentValue: String,
    onValueChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var tempValue by remember { mutableStateOf(currentValue) }

    LaunchedEffect(currentValue) {
        tempValue = currentValue
    }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.pagePadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.medium
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tempValue.isEmpty()) "0" else tempValue,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            val backgroundColor = when {
                                isSpecialKey -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                            val textColor = when {
                                isSpecialKey -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(backgroundColor)
                                    .clickable {
                                        when (key) {
                                            "C" -> {
                                                tempValue = ""
                                                onValueChanged("")
                                            }
                                            "←" -> {
                                                if (tempValue.isNotEmpty()) {
                                                    tempValue = tempValue.dropLast(1)
                                                    onValueChanged(tempValue)
                                                }
                                            }
                                            else -> {
                                                if (tempValue.length < 4) {
                                                    tempValue = tempValue + key
                                                    onValueChanged(tempValue)
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

                if (title == "输入单价") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .height(50.dp)
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .clickable {
                                    if (tempValue.isNotEmpty() && !tempValue.contains(".")) {
                                        if (tempValue.length < 4) {
                                            tempValue = tempValue + "."
                                            onValueChanged(tempValue)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "小数点 .",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}
//com.pingwei.lengkubao.ui.saleout.SaleOutActivity
package com.pingwei.lengkubao.ui.saleout

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.data.db.entity.CustomerType
import com.pingwei.lengkubao.data.db.entity.Location
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.data.db.entity.SaleItem
import com.pingwei.lengkubao.data.model.ProductWithStock
import com.pingwei.lengkubao.service.TcpSyncService
import com.pingwei.lengkubao.sync.TcpSyncManager
import com.pingwei.lengkubao.ui.common.ProductQuantityPriceInput
import com.pingwei.lengkubao.ui.customer.CustomerAddActivity
import com.pingwei.lengkubao.ui.instock.components.CompactSelectField
import com.pingwei.lengkubao.ui.instock.components.LocationSelectorDialog
import com.pingwei.lengkubao.ui.instock.components.OperatorSelectorDialog
import com.pingwei.lengkubao.ui.instock.components.SearchableCustomerField
import com.pingwei.lengkubao.ui.saleout.components.*
import com.pingwei.lengkubao.ui.saleout.viewmodel.SaleOutViewModel
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModelProvider
import com.pingwei.lengkubao.ui.saleout.SaleOutActivity.Companion.extractCustomerNo

class SaleOutActivity : ComponentActivity() {
    private val TAG = "SaleOutActivity"
    private lateinit var viewModel: SaleOutViewModel
    // 扫码启动器
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

    // 扫码状态
    private val _isScanning = mutableStateOf(false)
    val isScanning: State<Boolean> = _isScanning

    // 最后扫码结果
    private val _lastScannedCode = mutableStateOf("")
    val lastScannedCode: State<String> = _lastScannedCode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 创建 ViewModel 实例
        viewModel = ViewModelProvider(this)[SaleOutViewModel::class.java]

        setContent {
            LengkubaoTheme {
                SaleOutScreen(
                    viewModel = viewModel,  // 传入同一个 ViewModel 实例
                    isScanning = isScanning.value,
                    lastScannedCode = lastScannedCode.value,
                    onScanCustomer = { launchScanner() }
                )
            }
        }
    }

    // 启动扫码
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
            Log.d(TAG, "📱 扫码器已启动")
        } catch (e: Exception) {
            _isScanning.value = false
            Log.e(TAG, "❌ 启动扫码器失败", e)
            Toast.makeText(this, "扫码器启动失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 处理扫码结果 - 参考入库界面优化
    private fun handleScannedCustomer(scannedContent: String) {
        _isScanning.value = false
        _lastScannedCode.value = scannedContent
        Log.d(TAG, "=== 销售出库处理扫码 ===")
        Log.d(TAG, "原始扫码内容: $scannedContent")

        // 提取客户编号
        val customerNo = extractCustomerNoFromQrContent(scannedContent)
        Log.d(TAG, "提取的客户编号: $customerNo")

        if (customerNo.isNullOrBlank()) {
            Toast.makeText(
                this,
                "无法识别二维码内容\n格式应为：客户名称（KH001）",
                Toast.LENGTH_LONG
            ).show()
            // 使用lifecycleScope
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

        // 后台查询客户
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@SaleOutActivity)
                val customer = db.customerDao().getByCustomerNo(customerNo)

                withContext(Dispatchers.Main) {
                    if (customer != null) {
                        Log.d(
                            TAG,
                            "✅ 成功找到客户: ${customer.customerName} (${customer.customerNo})"
                        )
                        // 通过广播通知Compose更新
                        viewModel.selectCustomer(customer)
                        Log.d(TAG, "✅ 已调用 ViewModel.selectCustomer，客户: ${customer.customerName}")

                        Toast.makeText(
                            this@SaleOutActivity,
                            "已选择客户: ${customer.customerName ?: customer.customerNo}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Log.w(TAG, "❌ 未找到客户编号: $customerNo")
                        Toast.makeText(
                            this@SaleOutActivity,
                            "未找到客户: $customerNo",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // 延迟清空结果
                delay(3000)
                withContext(Dispatchers.Main) {
                    _lastScannedCode.value = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 查询客户失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SaleOutActivity,
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

    // 提取客户编号（复用入库界面逻辑）
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
    // ========== 新增：供 Compose 使用的提取函数 ==========
    companion object {
        fun extractCustomerNo(content: String): String? {
            if (content.isBlank()) return null

            // 格式1：中文括号 "客户名称（KH001）"
            val chinesePattern = Regex("（([^）]+)）")
            chinesePattern.find(content)?.let {
                val result = it.groupValues[1].trim()
                if (result.startsWith("KH")) return result
            }

            // 格式2：英文括号 "客户名称(KH001)"
            val englishPattern = Regex("\\(([^)]+)\\)")
            englishPattern.find(content)?.let {
                val result = it.groupValues[1].trim()
                if (result.startsWith("KH")) return result
            }

            // 格式3：纯客户编号（以KH开头）
            if (content.startsWith("KH") && content.length >= 5) {
                return content
            }

            // 格式4：尝试查找KH开头的部分
            val khPattern = Regex("KH\\d+")
            khPattern.find(content)?.let {
                return it.value
            }

            // 格式5：旧格式兼容（C开头转KH开头）
            val cPattern = Regex("C\\d+")
            cPattern.find(content)?.let {
                return it.value.replaceFirst("C", "KH")
            }

            return null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleOutScreen(
    viewModel: SaleOutViewModel = viewModel(),
    isScanning: Boolean = false,
    lastScannedCode: String = "",
    onScanCustomer: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 在进入时初始化配置管理器
    LaunchedEffect(Unit) {
        viewModel.initConfigManager(context)
    }

    // ========== 新增：监听扫码码值，自动查询并填入客户 ==========
    LaunchedEffect(lastScannedCode) {
        if (lastScannedCode.isNotEmpty()) {
            Log.d("SaleOutScreen", "🔍 收到扫码码值: $lastScannedCode")
            // 提取客户编号并查询
            val customerNo = extractCustomerNo(lastScannedCode)
            if (customerNo != null) {
                try {
                    val customer = withContext(Dispatchers.IO) {
                        val database = AppDatabase.getInstance(context)
                        database.customerDao().getByCustomerNo(customerNo)
                    }
                    if (customer != null) {
                        viewModel.selectCustomer(customer)
                        Log.d("SaleOutScreen", "✅ 扫码自动填入客户: ${customer.customerName}")
                        Toast.makeText(context, "已选择客户: ${customer.customerName}", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.w("SaleOutScreen", "❌ 未找到客户: $customerNo")
                        Toast.makeText(context, "未找到客户: $customerNo", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("SaleOutScreen", "❌ 扫码查询客户失败", e)
                    Toast.makeText(context, "查询失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 状态管理
    var showLocationDialog by remember { mutableStateOf(false) }
    var showOperatorDialog by remember { mutableStateOf(false) }
    var showSaveSuccessDialog by remember { mutableStateOf(false) }
    var showSetDefaultsDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var savedBillNo by remember { mutableStateOf("") }

    // ========== 扫码状态相关逻辑 ==========
    var localLastScannedCode by remember { mutableStateOf("") }

    // 监听外部传入的扫码码值变化
    LaunchedEffect(lastScannedCode) {
        if (lastScannedCode.isNotEmpty()) {
            localLastScannedCode = lastScannedCode
            delay(3000)
            localLastScannedCode = ""
        }
    }

    // 收集 ViewModel 状态
    val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val selectedLocation by viewModel.selectedLocation.collectAsStateWithLifecycle()
    val selectedOperator by viewModel.selectedOperator.collectAsStateWithLifecycle()
    val saleItems by viewModel.saleItems.collectAsStateWithLifecycle()
    val totalAmount by viewModel.totalAmount.collectAsStateWithLifecycle()
    val totalQuantity by viewModel.totalQuantity.collectAsStateWithLifecycle()
    val remark by viewModel.remark.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val productsWithStock by viewModel.productsWithStock.collectAsStateWithLifecycle(emptyList())
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle(emptyList())

    // 选完客户并选择库位后，才加载该库位的商品
    LaunchedEffect(selectedCustomer?.id, selectedLocation?.id) {
        if (selectedCustomer != null && selectedLocation?.id != null) {
            Log.d("SaleOutScreen", "库位已选择: ${selectedLocation?.locationName}, 开始加载商品...")
            viewModel.loadProductsByLocation(selectedLocation!!.id)
        } else {
            viewModel.clearProducts()
        }
    }

    // 监听 saleItems 状态变化
    LaunchedEffect(saleItems) {
        Log.d("SaleOutScreen", "🔄 saleItems 状态变化: 当前共 ${saleItems.size} 项")
        saleItems.forEachIndexed { index, item ->
            Log.d(
                "SaleOutScreen",
                "   [$index] 商品名称: ${item.productName ?: "未知"}, 数量: ${item.quantity}, 单价: ¥${item.salePrice}, 金额: ¥${item.amount}"
            )
        }
    }

    // 库位选择对话框 - 自动保存为默认值
    if (showLocationDialog) {
        LocationSelectorDialog(
            locations = viewModel.allLocations.collectAsStateWithLifecycle(emptyList()).value,
            onDismiss = { showLocationDialog = false },
            onLocationSelected = {
                viewModel.selectLocation(it)
                viewModel.saveAsDefaultLocation() // 选择后自动保存为默认
                showLocationDialog = false
            }
        )
    }

    // 经手人选择对话框 - 自动保存为默认值
    if (showOperatorDialog) {
        OperatorSelectorDialog(
            operators = viewModel.allOperators.collectAsStateWithLifecycle(emptyList()).value,
            onDismiss = { showOperatorDialog = false },
            onOperatorSelected = {
                viewModel.selectOperator(it)
                viewModel.saveAsDefaultHandler()
                showOperatorDialog = false
            }
        )
    }

    // 设置默认值对话框（现在功能主要是查看）
    if (showSetDefaultsDialog) {
        AlertDialog(
            onDismissRequest = { showSetDefaultsDialog = false },
            title = { Text("当前默认值") },
            text = {
                Column {
                    val defaultLocationId = viewModel.getDefaultLocationId()
                    val defaultHandlerId = viewModel.getDefaultHandlerId()

                    Text("当前已设置的默认值：")
                    Spacer(modifier = Modifier.height(8.dp))

                    if (defaultLocationId != null) {
                        val location =
                            viewModel.allLocations.collectAsStateWithLifecycle(emptyList()).value
                                .find { it.id == defaultLocationId }
                        location?.let {
                            Text("• 默认库位: ${it.locationName}")
                        } ?: Text("• 默认库位: 未设置")
                    } else {
                        Text("• 默认库位: 未设置")
                    }

                    if (defaultHandlerId != null) {
                        val operator =
                            viewModel.allOperators.collectAsStateWithLifecycle(emptyList()).value
                                .find { it.id == defaultHandlerId }
                        operator?.let {
                            Text("• 默认经手人: ${it.name}")
                        } ?: Text("• 默认经手人: 未设置")
                    } else {
                        Text("• 默认经手人: 未设置")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "提示：选择库位和经手人时会自动保存为默认值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // 清空默认值功能
                    viewModel.clearDefaultLocation()
                    viewModel.clearDefaultHandler()
                    Toast.makeText(context, "已清除默认值", Toast.LENGTH_SHORT).show()
                    showSetDefaultsDialog = false
                }) {
                    Text("清除默认值")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetDefaultsDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // 保存成功对话框 - 修改为支持继续开单
    // 在 SaleOutScreen 函数中，找到保存成功对话框部分，替换为：

// 保存成功对话框 - 修改为支持继续开单
    if (showSaveSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                // 点击外部不关闭，强制用户选择
            },
            title = { Text("✅ 保存成功") },
            text = { Text("销售单 $savedBillNo 已保存成功") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveSuccessDialog = false
                        // 保存并退出 - 清空并退出
                        viewModel.clearAll()
                        (context as? ComponentActivity)?.finish()
                    }
                ) {
                    Text("保存并退出")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveSuccessDialog = false
                        // 继续开单 - 只保留默认值，清空客户和商品明细
                        viewModel.clearForContinue()
                        // 刷新界面，但保持当前activity不退出
                        Toast.makeText(
                            context,
                            "可以继续开单",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text("继续开单")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("客户报账")
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(context, CustomerAddActivity::class.java).apply {
                                        putExtra(
                                            CustomerAddActivity.EXTRA_CUSTOMER_TYPE,
                                            CustomerType.SELLER
                                        )
                                    }
                                )
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "添加卖家客户",
                                modifier = Modifier.size(20.dp)
                            )
                        }

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
                    IconButton(
                        onClick = {
                            if (saleItems.isNotEmpty() && !isSaving) {
                                androidx.appcompat.app.AlertDialog.Builder(context)
                                    .setTitle("提示")
                                    .setMessage("当前有未保存的商品，确定要返回吗？")
                                    .setPositiveButton("确定") { _, _ ->
                                        (context as? ComponentActivity)?.finish()
                                    }
                                    .setNegativeButton("取消", null)
                                    .show()
                            } else {
                                (context as? ComponentActivity)?.finish()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 设置默认值按钮
                    IconButton(
                        onClick = { showSetDefaultsDialog = true }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "设置默认值")
                    }

                    // ===================== 新增：手动同步按钮 =====================
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
                    // ============================================================

                    IconButton(
                        onClick = {
                            if (!isSaving) {
                                viewModel.clearAll()
                                Toast.makeText(context, "已清空表单", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSaving && (selectedCustomer != null || selectedLocation != null ||
                                selectedOperator != null || saleItems.isNotEmpty())
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = "清空")
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
                    // 1. 客户 + 库位（无标题；经手人不挪到此处）
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SearchableCustomerField(
                                    customers = allCustomers,
                                    selectedCustomer = selectedCustomer,
                                    onCustomerSelected = { customer ->
                                        if (!isSaving) {
                                            viewModel.selectCustomer(customer)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    isError = selectedCustomer == null,
                                    fieldHeight = 40.dp,
                                    fieldTextStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    showFloatingLabel = false
                                )

                                IconButton(
                                    onClick = {
                                        if (!isSaving) {
                                            onScanCustomer()
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                    enabled = !isSaving
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.QrCodeScanner,
                                            contentDescription = "扫码选择",
                                            Modifier.size(26.dp)
                                        )
                                        Text(
                                            "扫码",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            CompactSelectField(
                                text = selectedLocation?.locationName.orEmpty(),
                                placeholder = if (selectedCustomer == null) "请先选择客户" else "请选择库位",
                                isError = selectedCustomer != null && selectedLocation == null,
                                showDefaultStar = selectedLocation?.id == viewModel.getDefaultLocationId(),
                                enabled = !isSaving,
                                onClick = {
                                    if (selectedCustomer == null) {
                                        Toast.makeText(
                                            context,
                                            "⚠️ 请先选择客户",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        showLocationDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 2. 商品明细 + 备注 + 经手人（无分区标题）
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedCustomer == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "请先选择客户",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (selectedLocation == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "请先选择库位",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (productsWithStock.isEmpty()) {
                                EmptySaleItemsPlaceholder(
                                    onAddProduct = {
                                        Toast.makeText(
                                            context,
                                            "当前库位暂无商品库存",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            } else {
                                val filteredProducts =
                                    productsWithStock.filter { productWithStock ->
                                        productWithStock.availableStock > 0
                                    }

                                if (filteredProducts.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "当前库位暂无可报账商品",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.heightIn(max = 400.dp)
                                    ) {
                                        items(filteredProducts) { productWithStock ->
                                            ProductQuantityPriceInput(
                                                productWithStock = productWithStock,
                                                existingQuantity = saleItems.find { it.productId == productWithStock.product.id }?.quantity ?: 0,
                                                existingPrice = saleItems.find { it.productId == productWithStock.product.id }?.salePrice ?: 0.0,
                                                stockLabel = "可报账",
                                                onQuantityChange = { quantity, price ->
                                                    coroutineScope.launch {
                                                        if (quantity > 0) {
                                                            val result =
                                                                viewModel.addOrUpdateSaleItem(
                                                                    product = productWithStock.product,
                                                                    salePrice = price,
                                                                    quantity = quantity
                                                                )

                                                            if (result.isSuccess) {
                                                                Log.d(
                                                                    "SaleOutScreen",
                                                                    "✅ 商品添加/更新成功"
                                                                )
                                                            } else {
                                                                val error =
                                                                    result.exceptionOrNull()?.message
                                                                        ?: "未知错误"
                                                                Toast.makeText(
                                                                    context,
                                                                    "❌ $error",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        } else {
                                                            val index = saleItems.indexOfFirst {
                                                                it.productId == productWithStock.product.id
                                                            }
                                                            if (index != -1) {
                                                                viewModel.removeItem(index)
                                                            }
                                                        }
                                                    }
                                                },
                                                isEnabled = !isSaving
                                            )
                                        }
                                    }

                                    if (saleItems.isNotEmpty()) {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.heightIn(max = 200.dp)
                                        ) {
                                            items(saleItems) { item ->
                                                val index = saleItems.indexOf(item)
                                                SaleItemDisplay(
                                                    item = item,
                                                    index = index,
                                                    onRemove = { removeIndex ->
                                                        if (!isSaving) viewModel.removeItem(
                                                            removeIndex
                                                        )
                                                    },
                                                    isEnabled = !isSaving
                                                )
                                            }
                                        }

                                        SaleSummaryInfo(
                                            totalQuantity = totalQuantity.toDouble(),
                                            totalAmount = totalAmount
                                        )
                                    }
                                }
                            }

                            Divider()
                            BasicTextField(
                                value = remark,
                                onValueChange = { if (!isSaving) viewModel.setRemark(it) },
                                singleLine = true,
                                enabled = !isSaving,
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(MaterialTheme.shapes.small)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline,
                                                MaterialTheme.shapes.small
                                            )
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (remark.isEmpty()) {
                                            Text(
                                                "备注（可选）",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )

                            CompactSelectField(
                                text = selectedOperator?.name.orEmpty(),
                                placeholder = "请选择",
                                isError = selectedOperator == null,
                                showDefaultStar = selectedOperator?.id == viewModel.getDefaultHandlerId(),
                                enabled = !isSaving,
                                onClick = { showOperatorDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 底部保存按钮区域
                SaveButtonArea(
                    totalAmount = totalAmount,
                    totalQuantity = totalQuantity,
                    isSaving = isSaving,
                    canSave = selectedCustomer != null &&
                            selectedLocation != null &&
                            selectedOperator != null &&
                            saleItems.isNotEmpty() &&
                            !isSaving,
                    onSave = {
                        when {
                            selectedCustomer == null -> Toast.makeText(
                                context,
                                "⚠️ 请先选择客户",
                                Toast.LENGTH_SHORT
                            ).show()

                            selectedLocation == null -> Toast.makeText(
                                context,
                                "⚠️ 请先选择库位",
                                Toast.LENGTH_SHORT
                            ).show()

                            selectedOperator == null -> Toast.makeText(
                                context,
                                "⚠️ 请先选择经手人",
                                Toast.LENGTH_SHORT
                            ).show()

                            saleItems.isEmpty() -> Toast.makeText(
                                context,
                                "⚠️ 请先添加销售商品",
                                Toast.LENGTH_SHORT
                            ).show()

                            else -> {
                                coroutineScope.launch {
                                    isSaving = true
                                    try {
                                        val result = viewModel.saveSaleBill()
                                        isSaving = false

                                        if (result.isSuccess) {
                                            val (_, billNo) = result.getOrNull() ?: Pair(
                                                0L,
                                                ""
                                            )
                                            savedBillNo = billNo
                                            showSaveSuccessDialog = true
                                        } else {
                                            val errorMsg = result.exceptionOrNull()?.message
                                                ?: "销售单保存失败，请稍后重试"
                                            Toast.makeText(
                                                context,
                                                "❌ $errorMsg",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        isSaving = false
                                        val errorMsg =
                                            "保存过程中出现未知异常：${e.message ?: "未知错误"}"
                                        Toast.makeText(context, "❌ $errorMsg", Toast.LENGTH_LONG)
                                            .show()
                                    }
                                }
                            }
                        }
                    }
                )
            }

            // 扫码成功提示
            AnimatedVisibility(
                visible = localLastScannedCode.isNotEmpty(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.pagePadding)
            ) {
                Box(
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
}

@Composable
private fun SaveButtonArea(
    totalAmount: Double,
    totalQuantity: Int,
    isSaving: Boolean,
    canSave: Boolean,
    onSave: () -> Unit
) {
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
                    Text(
                        "销售总金额",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "¥${String.format("%.2f", totalAmount)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "销售总数量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$totalQuantity 箱",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isSaving) {
                SavingInProgressIndicator()
            } else {
                SaveButton(
                    canSave = canSave,
                    onSave = onSave
                )
            }
        }
    }
}

@Composable
private fun SavingInProgressIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.buttonHeight),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("保存中...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SaveButton(
    canSave: Boolean,
    onSave: () -> Unit
) {
    Button(
        onClick = onSave,
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.buttonHeight),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
        ),
        enabled = canSave
    ) {
        Icon(Icons.Default.Save, contentDescription = "保存", modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "保存销售单",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
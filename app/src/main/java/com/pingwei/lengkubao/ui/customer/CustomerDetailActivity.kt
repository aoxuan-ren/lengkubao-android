// ui/customer/CustomerDetailActivity.kt
package com.pingwei.lengkubao.ui.customer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.ui.customer.utils.QRCodeGenerator
import com.pingwei.lengkubao.utils.CustomerLabelPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CustomerDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CUSTOMER_ID = "customer_id"
    }

    private val customerDao by lazy {
        // 修改这里：使用新的 LengKuBaoApplication 获取数据库
        LengKuBaoApplication.getDatabase().customerDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val customerId = intent.getLongExtra(EXTRA_CUSTOMER_ID, -1L)
        if (customerId == -1L) {
            Toast.makeText(this, "客户信息错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CustomerDetailScreen(
                        customerId = customerId,
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: Long,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 状态管理
    var customer by remember { mutableStateOf<Customer?>(null) }
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPrinting by remember { mutableStateOf(false) }
    var printerStatus by remember { mutableStateOf<CustomerLabelPrinter.PrinterStatus?>(null) }
    var showPrinterStatusDialog by remember { mutableStateOf(false) }
    // 新增：删除确认对话框状态
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 新增：删除客户函数（含二维码文件删除+数据库删除）
    fun deleteCustomer() {
        Toast.makeText(
            context,
            com.pingwei.lengkubao.utils.PC_ONLY_CONFIG_DELETE_MESSAGE,
            Toast.LENGTH_LONG
        ).show()
    }

    // 新增：编辑客户函数 - 跳转到编辑界面
    fun editCustomer() {
        val currentCustomer = customer ?: return

        val intent = Intent(context, CustomerEditActivity::class.java).apply {
            putExtra(CustomerEditActivity.EXTRA_CUSTOMER_ID, currentCustomer.id)
        }
        context.startActivity(intent)
    }

    // 重新生成二维码函数
    fun regenerateQrCode() {
        val currentCustomer = customer ?: return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val newPath = QRCodeGenerator.regenerateCustomerQRCode(
                    customerName = currentCustomer.customerName,
                    customerNo = currentCustomer.customerNo,
                    oldQrCodePath = currentCustomer.qrCodePath,
                    context = context
                )

                // 更新数据库
                if (newPath != null) {
                    val customerDao = LengKuBaoApplication.getDatabase().customerDao()
                    val updated = customerDao.updateQrCodePath(
                        id = currentCustomer.id,
                        qrCodePath = newPath,
                        updateTime = System.currentTimeMillis()
                    )

                    if (updated > 0) {
                        // 重新加载图片
                        val file = File(newPath)
                        if (file.exists()) {
                            val newBitmap = BitmapFactory.decodeFile(newPath)
                            withContext(Dispatchers.Main) {
                                qrCodeBitmap = newBitmap
                                Toast.makeText(context, "二维码已重新生成", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "重新生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 降级打印函数（单独的Composable函数）
    fun retryWithSimplePrint(customer: Customer) {
        coroutineScope.launch(Dispatchers.IO) {
            val result = CustomerLabelPrinter.printCustomerLabel(
                context = context,
                customer = customer,
                printQrCodeImage = false // 不打印图片，只打印文本
            )

            withContext(Dispatchers.Main) {
                if (result.success) {
                    Toast.makeText(context, "已打印简化版标签", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "打印失败: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 打印二维码标签
    fun printQrCodeLabel() {
        val currentCustomer = customer ?: return

        isPrinting = true

        coroutineScope.launch(Dispatchers.IO) {
            val result = CustomerLabelPrinter.printCustomerLabel(
                context = context,
                customer = currentCustomer,
                printQrCodeImage = true // 尝试打印二维码图片
            )

            withContext(Dispatchers.Main) {
                isPrinting = false

                if (result.success) {
                    Toast.makeText(context, "标签打印成功", Toast.LENGTH_SHORT).show()
                } else {
                    // 如果带图片打印失败，尝试简单打印
                    if (result.message.contains("图片") || result.message.contains("二维码")) {
                        // 降级到简单打印
                        retryWithSimplePrint(currentCustomer)
                    } else {
                        Toast.makeText(context, "打印失败: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 检查打印机状态
    fun checkAndPrint() {
        coroutineScope.launch(Dispatchers.IO) {
            // 首先检查打印机状态
            val status = CustomerLabelPrinter.checkPrinterStatus(context)

            withContext(Dispatchers.Main) {
                printerStatus = status

                when (status) {
                    CustomerLabelPrinter.PrinterStatus.READY -> {
                        // 打印机就绪，开始打印
                        printQrCodeLabel()
                    }
                    CustomerLabelPrinter.PrinterStatus.NO_PAPER -> {
                        Toast.makeText(context, "打印机缺纸，请添加纸张", Toast.LENGTH_LONG).show()
                        showPrinterStatusDialog = true
                    }
                    CustomerLabelPrinter.PrinterStatus.NOT_CONNECTED -> {
                        Toast.makeText(context, "打印机未连接，请检查连接", Toast.LENGTH_LONG).show()
                        showPrinterStatusDialog = true
                    }
                    else -> {
                        Toast.makeText(context, "打印机状态异常: $status", Toast.LENGTH_LONG).show()
                        showPrinterStatusDialog = true
                    }
                }
            }
        }
    }

    // 打印测试页
    fun printTestPage() {
        coroutineScope.launch(Dispatchers.IO) {
            val result = CustomerLabelPrinter.printTestPage(context)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "测试页打印: ${if (result.success) "成功" else "失败"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 检查打印机状态（初始检查）
    LaunchedEffect(Unit) {
        printerStatus = CustomerLabelPrinter.checkPrinterStatus(context)
    }

    // 加载客户数据
    LaunchedEffect(customerId) {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val customerDao = LengKuBaoApplication.getDatabase().customerDao()
            val loadedCustomer = customerDao.getCustomerById(customerId)

            withContext(Dispatchers.Main) {
                customer = loadedCustomer

                // 加载二维码图片
                loadedCustomer?.qrCodePath?.let { path ->
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            qrCodeBitmap = BitmapFactory.decodeFile(path)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("客户详情") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                // 新增：顶部操作栏 - 编辑、删除按钮
                actions = {
                    IconButton(onClick = { editCustomer() }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑客户")
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除客户",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 调试按钮（小按钮）
                FloatingActionButton(
                    onClick = { printTestPage() },
                    modifier = Modifier.size(40.dp),
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = "调试")
                }

                // 主打印按钮
                ExtendedFloatingActionButton(
                    onClick = { checkAndPrint() },
                    icon = {
                        if (isPrinting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Print, contentDescription = "打印")
                        }
                    },
                    text = { Text("打印二维码标签") },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                customer == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("客户信息不存在")
                    }
                }

                else -> {
                    // 客户信息卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // 客户编号
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Numbers,
                                    contentDescription = "编号",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "客户编号：${customer!!.customerNo}",
                                    fontSize = 18.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }

                            // 客户名称
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "名称",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "客户名称：${customer!!.customerName}",
                                    fontSize = 16.sp
                                )
                            }

                            // 联系电话（如果有）
                            customer!!.phone?.let { phone ->
                                if (phone.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Phone,
                                            contentDescription = "电话",
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = "联系电话：$phone",
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 二维码显示区域
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "客户二维码",
                                fontSize = 18.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            if (qrCodeBitmap != null) {
                                // 显示二维码图片
                                Image(
                                    bitmap = qrCodeBitmap!!.asImageBitmap(),
                                    contentDescription = "客户二维码",
                                    modifier = Modifier.size(200.dp)
                                )
                            } else {
                                // 二维码未生成
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = "二维码",
                                        modifier = Modifier.size(80.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = "二维码未生成",
                                        modifier = Modifier.padding(top = 16.dp),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            // 二维码内容文本
                            Text(
                                text = "${customer!!.customerName}（${customer!!.customerNo}）",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 16.dp),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 重新生成按钮
                            OutlinedButton(
                                onClick = { regenerateQrCode() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "重新生成",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("重新生成二维码")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 打印说明
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
                                text = "打印说明",
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Text(
                                text = "• 点击下方打印按钮打印二维码标签",
                                fontSize = 14.sp
                            )
                            Text(
                                text = "• 将标签贴在客户的货物或文件上",
                                fontSize = 14.sp
                            )
                            Text(
                                text = "• 使用扫码枪扫描二维码快速选择客户",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // 新增：删除客户确认对话框
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("确定要删除客户 ${customer?.customerName ?: ""} 吗？")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "此操作将同时删除二维码文件，且无法恢复。",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        deleteCustomer()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 原有：打印机状态对话框
    if (showPrinterStatusDialog) {
        AlertDialog(
            onDismissRequest = { showPrinterStatusDialog = false },
            title = { Text("打印机状态") },
            text = {
                Column {
                    Text("当前状态: ${printerStatus ?: "未知"}")
                    Spacer(modifier = Modifier.height(8.dp))
                    when (printerStatus) {
                        CustomerLabelPrinter.PrinterStatus.NO_PAPER -> {
                            Text("• 请检查打印机纸张")
                            Text("• 确保纸卷安装正确")
                            Text("• 重新装入纸张后重试")
                        }
                        CustomerLabelPrinter.PrinterStatus.NOT_CONNECTED -> {
                            Text("• 请检查打印机电源")
                            Text("• 确保USB连接正常")
                            Text("• 重启打印机后重试")
                        }
                        CustomerLabelPrinter.PrinterStatus.OVERHEAT -> {
                            Text("• 打印头过热，请等待冷却")
                            Text("• 关闭打印机休息5分钟")
                        }
                        CustomerLabelPrinter.PrinterStatus.COVER_OPEN -> {
                            Text("• 打印机舱门未关闭")
                            Text("• 请关闭打印机舱门")
                        }
                        else -> {
                            Text("请检查打印机连接和状态")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPrinterStatusDialog = false }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPrinterStatusDialog = false
                        // 可以添加重试连接功能
                        coroutineScope.launch {
                            printerStatus = CustomerLabelPrinter.checkPrinterStatus(context)
                        }
                    }
                ) {
                    Text("刷新状态")
                }
            }
        )
    }
}
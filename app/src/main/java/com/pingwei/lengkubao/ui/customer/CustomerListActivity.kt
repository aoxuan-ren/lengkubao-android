// CustomerListActivity.kt - 修复线程问题的版本
package com.pingwei.lengkubao.ui.customer

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.ui.customer.utils.QRCodeGenerator
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerListActivity : ComponentActivity() {
    // 扫码启动器
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedContent = result.contents.trim()
            handleScannedCustomer(scannedContent)
        } else {
            Toast.makeText(this, "扫码取消", Toast.LENGTH_SHORT).show()
        }
    }

    // 处理扫码结果
    private fun handleScannedCustomer(scannedContent: String) {
        lifecycleScope.launch(Dispatchers.IO) {  // 修复：确保在后台线程执行
            try {
                val customerNo = extractCustomerNoFromQrContent(scannedContent)
                Log.d("CustomerListActivity", "提取的客户编号: $customerNo")

                if (customerNo.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CustomerListActivity,
                            "无法识别二维码内容\n格式应为：客户名称（KH001）",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val db = AppDatabase.getInstance(this@CustomerListActivity)
                val allCustomers = db.customerDao().getAllCustomersSync()

                // 查找匹配的客户
                val customer = allCustomers.find { it.customerNo == customerNo }

                withContext(Dispatchers.Main) {
                    if (customer != null) {
                        Log.d("CustomerListActivity", "✓ 成功找到客户: ${customer.customerName} (${customer.customerNo})")
                        onCustomerSelected(customer)
                    } else {
                        Log.w("CustomerListActivity", "✗ 未找到客户编号: $customerNo")
                        Toast.makeText(
                            this@CustomerListActivity,
                            "未找到客户编号：$customerNo",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CustomerListActivity,
                        "查询失败：${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // 从二维码内容中提取客户编号 - 适配KH格式
    private fun extractCustomerNoFromQrContent(content: String): String? {
        // ... 保持原有的 extractCustomerNoFromQrContent 函数不变 ...
        if (content.isBlank()) return null

        // 格式1：中文括号 "客户名称（KH001）"
        val chinesePattern = Regex("（([^）]+)）")
        val chineseMatch = chinesePattern.find(content)
        if (chineseMatch != null) {
            val result = chineseMatch.groupValues[1].trim()
            Log.d("CustomerListActivity", "匹配中文括号: $result")
            if (result.startsWith("KH")) {
                return result
            }
        }

        // 格式2：英文括号 "客户名称(KH001)"
        val englishPattern = Regex("\\(([^)]+)\\)")
        val englishMatch = englishPattern.find(content)
        if (englishMatch != null) {
            val result = englishMatch.groupValues[1].trim()
            Log.d("CustomerListActivity", "匹配英文括号: $result")
            if (result.startsWith("KH")) {
                return result
            }
        }

        // 格式3：纯客户编号（以KH开头）
        if (content.startsWith("KH") && content.length >= 5) {
            Log.d("CustomerListActivity", "匹配纯编号格式: $content")
            return content
        }

        // 格式4：尝试查找KH开头的部分
        val khPattern = Regex("KH\\d+")
        val khMatch = khPattern.find(content)
        if (khMatch != null) {
            val result = khMatch.value
            Log.d("CustomerListActivity", "匹配KH开头编号: $result")
            return result
        }

        Log.d("CustomerListActivity", "未匹配到任何格式")
        return null
    }

    // 客户被选中后的处理
    private fun onCustomerSelected(customer: Customer) {
        val intent = android.content.Intent(this, CustomerDetailActivity::class.java).apply {
            putExtra(CustomerDetailActivity.EXTRA_CUSTOMER_ID, customer.id)
        }
        startActivity(intent)
    }

    // 删除客户 - 修复线程问题
    private fun deleteCustomer(customer: Customer) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@CustomerListActivity)

                // 先删除二维码文件（如果存在）
                customer.qrCodePath?.let { path ->
                    QRCodeGenerator.deleteQRCodeFile(path)
                }

                // 删除数据库记录
                db.customerDao().delete(customer)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CustomerListActivity,
                        "客户 ${customer.customerName} 已删除",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CustomerListActivity,
                        "删除失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // 编辑客户
    private fun editCustomer(customer: Customer) {
        val intent = android.content.Intent(this, CustomerEditActivity::class.java).apply {
            putExtra(CustomerEditActivity.EXTRA_CUSTOMER_ID, customer.id)
        }
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CustomerListScreen(
                        onScanClick = {
                            val scanOptions = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("请扫描客户二维码")
                                setCameraId(0)
                                setBeepEnabled(true)
                                setBarcodeImageEnabled(false)
                            }
                            scanLauncher.launch(scanOptions)
                        },
                        onEditCustomer = { customer -> editCustomer(customer) },
                        onDeleteCustomer = { customer -> deleteCustomer(customer) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onScanClick: () -> Unit,
    onEditCustomer: (Customer) -> Unit,
    onDeleteCustomer: (Customer) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // 使用Flow加载客户数据 - 修复：正确处理协程
    LaunchedEffect(Unit) {
        try {
            val db = AppDatabase.getInstance(context)

            // 在后台线程执行数据库操作
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 方法1：使用Flow监听数据变化
                    db.customerDao().getAllCustomers().collectLatest { customerList ->
                        withContext(Dispatchers.Main) {
                            customers = customerList
                            isLoading = false
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 方法2：如果Flow失败，使用同步方法
                    try {
                        val customerList = db.customerDao().getAllCustomersSync()
                        withContext(Dispatchers.Main) {
                            customers = customerList
                            isLoading = false
                        }
                    } catch (e2: Exception) {
                        withContext(Dispatchers.Main) {
                            customers = emptyList()
                            isLoading = false
                            Toast.makeText(context, "加载客户数据失败: ${e2.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 处理异常情况
            withContext(Dispatchers.Main) {
                customers = emptyList()
                isLoading = false
                Toast.makeText(context, "初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("客户管理") },
                navigationIcon = {
                    IconButton(onClick = { (context as? androidx.activity.ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(
                            android.content.Intent(context, CustomerAddActivity::class.java)
                        )
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加客户")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码") },
                text = { Text("扫码选择客户") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索框
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("搜索客户编号或姓名") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                }
            }

            // 客户列表
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (customers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PeopleOutline,
                            contentDescription = "无客户",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "暂无客户数据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                context.startActivity(
                                    android.content.Intent(context, CustomerAddActivity::class.java)
                                )
                            }
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "添加")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("添加第一个客户")
                        }
                    }
                }
            } else {
                val filteredCustomers = if (searchQuery.isBlank()) {
                    customers
                } else {
                    customers.filter { customer ->
                        customer.customerNo.contains(searchQuery, ignoreCase = true) ||
                                customer.customerName?.contains(searchQuery, ignoreCase = true) == true ||
                                (customer.phone?.contains(searchQuery, ignoreCase = true) == true)
                    }
                }

                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "未找到匹配的客户",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        item {
                            Text(
                                text = "共 ${filteredCustomers.size} 个客户",
                                modifier = Modifier.padding(vertical = 8.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(filteredCustomers) { customer ->
                            CustomerItem(
                                customer = customer,
                                onClick = { onEditCustomer(customer) },
                                onDelete = { onDeleteCustomer(customer) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerItem(
    customer: Customer,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 客户图标
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "客户",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))

                // 客户信息
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = customer.customerName ?: "未知客户",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = customer.customerNo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!customer.phone.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "电话",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = customer.phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 二维码信息
                    if (!customer.qrCodePath.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "二维码",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "已生成二维码",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 菜单按钮
                Box(
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    IconButton(
                        onClick = { showMenu = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多操作"
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                showMenu = false
                                onClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = "编辑")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        )
                    }
                }
            }
        }
    }
}
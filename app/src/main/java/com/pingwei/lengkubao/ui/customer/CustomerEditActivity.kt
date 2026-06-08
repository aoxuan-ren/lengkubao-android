// 修正后的 CustomerEditActivity.kt
package com.pingwei.lengkubao.ui.customer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.data.db.entity.Customer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerEditActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CUSTOMER_ID = "customer_id"
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
                    CustomerEditScreen(
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
fun CustomerEditScreen(
    customerId: Long,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 获取数据库实例
    val customerDao = remember {
        LengKuBaoApplication.getDatabase().customerDao()
    }

    // 状态管理
    var customer by remember { mutableStateOf<Customer?>(null) }
    var customerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // 加载客户数据
    LaunchedEffect(customerId) {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val loadedCustomer = customerDao.getCustomerById(customerId)

            withContext(Dispatchers.Main) {
                customer = loadedCustomer
                loadedCustomer?.let {
                    customerName = it.customerName ?: ""
                    phone = it.phone ?: ""
                }
                isLoading = false
            }
        }
    }

    // 保存修改
    fun saveChanges() {
        if (customerName.isBlank()) {
            Toast.makeText(context, "客户名称不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        val currentCustomer = customer ?: return
        isSaving = true

        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 更新客户信息
                val updatedCustomer = currentCustomer.copy(
                    customerName = customerName,
                    phone = phone.ifBlank { null },
                    updateTime = System.currentTimeMillis()
                )

                val result = customerDao.insertCustomer(updatedCustomer)

                withContext(Dispatchers.Main) {
                    isSaving = false
                    if (result > 0) {
                        Toast.makeText(context, "客户信息已更新", Toast.LENGTH_SHORT).show()
                        onBackPressed()
                    } else {
                        Toast.makeText(context, "更新失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(context, "更新失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("编辑客户") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!isLoading) {
                        IconButton(
                            onClick = { saveChanges() },
                            enabled = !isSaving && customerName.isNotBlank()
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Save, contentDescription = "保存")
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = "错误",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text("客户信息不存在")
                            Button(onClick = onBackPressed) {
                                Text("返回")
                            }
                        }
                    }
                }
                else -> {
                    // 客户编号（只读）
                    OutlinedTextField(
                        value = customer!!.customerNo,
                        onValueChange = { },
                        label = { Text("客户编号") },
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.Numbers, contentDescription = "编号")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        singleLine = true
                    )

                    // 客户名称
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("客户名称 *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        singleLine = true
                    )

                    // 联系电话
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("联系电话") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        singleLine = true
                    )

                    // 保存按钮
                    Button(
                        onClick = { saveChanges() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving && customerName.isNotBlank()
                    ) {
                        if (isSaving) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在保存...")
                            }
                        } else {
                            Text("保存修改", fontSize = 16.sp)
                        }
                    }

                    // 操作说明
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "编辑说明",
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "• 客户编号不可修改",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            Text(
                                text = "• 修改客户名称后，需要重新生成二维码",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            Text(
                                text = "• 可在客户详情页面重新生成二维码",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
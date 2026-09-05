// CustomerAddActivity.kt - 重构版本
package com.pingwei.lengkubao.ui.customer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.utils.CustomerCodeGenerator // 导入代码生成器
import com.pingwei.lengkubao.utils.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerAddActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CUSTOMER_TYPE = "customer_type"
    }

    private val customerType: String by lazy {
        intent.getStringExtra(EXTRA_CUSTOMER_TYPE) ?: com.pingwei.lengkubao.data.db.entity.CustomerType.SELLER
    }

    private val customerDao by lazy {
        // 修改这里：使用新的 LengKuBaoApplication 获取数据库
        LengKuBaoApplication.getDatabase().customerDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CustomerAddScreen(
                        title = if (customerType == com.pingwei.lengkubao.data.db.entity.CustomerType.BUYER) "添加买家" else "添加客户",
                        customerType = customerType,
                        onSaveCustomer = { customerNo, customerName, phone ->
                            saveCustomer(customerNo, customerName, phone)
                        }
                    )
                }
            }
        }
    }

    /**
     * 保存客户
     */
    private fun saveCustomer(customerNo: String, customerName: String, phone: String?) {
        if (customerName.isBlank()) {
            showToast("客户名称不能为空")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 创建客户对象
                val now = System.currentTimeMillis()
                val customer = Customer(
                    customerNo = customerNo,
                    customerName = customerName,
                    phone = phone,
                    qrCodePath = null,
                    createTime = now,
                    updateTime = now,
                    customerType = customerType
                )

                // 保存到数据库
                val result = customerDao.insertCustomer(customer)

                withContext(Dispatchers.Main) {
                    if (result > 0) {
                        SyncTrigger.triggerCustomerSync(this@CustomerAddActivity, result)
                        showToast("客户添加成功")
                        finish()
                    } else {
                        showToast("添加失败，客户编号已存在")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("添加失败：${e.message ?: "未知错误"}")
                }
                e.printStackTrace()
            }
        }
    }

    private fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAddScreen(
    title: String = "添加客户",
    customerType: String = com.pingwei.lengkubao.data.db.entity.CustomerType.SELLER,
    onSaveCustomer: (String, String, String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 状态管理
    var customerNo by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // 自动生成客户编号（页面加载时）
    LaunchedEffect(customerType) {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val generatedNo = CustomerCodeGenerator.generateNextCustomerNo(context, customerType)
            withContext(Dispatchers.Main) {
                customerNo = generatedNo
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 客户编号（只读，KHxxx格式）
        OutlinedTextField(
            value = customerNo,
            onValueChange = { }, // 只读
            label = { Text("客户编号") },
            readOnly = true,
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Person, contentDescription = "客户")
                }
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
            onClick = {
                if (customerName.isBlank()) {
                    Toast.makeText(context, "请输入客户名称", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (isSaving) return@Button // 防止重复点击

                isSaving = true
                onSaveCustomer(customerNo, customerName, phone.ifBlank { null })
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && customerName.isNotBlank() && !isSaving
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
                Text(
                    text = if (isLoading) "生成编号中..." else "保存客户",
                    fontSize = 16.sp
                )
            }
        }

        // 编号格式说明
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
                    text = "客户编号说明",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "客户编号采用 KHxxx 格式，系统自动生成：",
                    fontSize = 14.sp
                )
                Text(
                    text = "• KH 表示客户 (KeHu)",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    text = "• 001、002、003... 为顺序编号",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    text = "例如：KH001、KH002、KH123",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
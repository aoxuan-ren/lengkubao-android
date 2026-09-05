package com.pingwei.lengkubao.ui.customer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.data.db.entity.CustomerType
import com.pingwei.lengkubao.ui.common.rememberDismissKeyboard
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import com.pingwei.lengkubao.utils.PC_ONLY_CONFIG_DELETE_MESSAGE
import com.pingwei.lengkubao.utils.SyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BuyerListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BuyerListScreen(
                        onAddClick = {
                            startActivity(
                                Intent(this, CustomerAddActivity::class.java).apply {
                                    putExtra(CustomerAddActivity.EXTRA_CUSTOMER_TYPE, CustomerType.BUYER)
                                }
                            )
                        },
                        onEditCustomer = { customer ->
                            startActivity(
                                Intent(this, CustomerEditActivity::class.java).apply {
                                    putExtra(CustomerEditActivity.EXTRA_CUSTOMER_ID, customer.id)
                                }
                            )
                        },
                        onDeleteCustomer = { _ ->
                            Toast.makeText(
                                this@BuyerListActivity,
                                PC_ONLY_CONFIG_DELETE_MESSAGE,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyerListScreen(
    onAddClick: () -> Unit,
    onEditCustomer: (Customer) -> Unit,
    onDeleteCustomer: (Customer) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var buyers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val dismissKeyboard = rememberDismissKeyboard()

    LaunchedEffect(Unit) {
        val db = AppDatabase.getInstance(context)
        db.customerDao().getCustomersByType(CustomerType.BUYER).collectLatest { list ->
            buyers = list
            isLoading = false
        }
    }

    val filtered = remember(buyers, searchQuery) {
        if (searchQuery.isBlank()) buyers
        else buyers.filter {
            it.customerName.contains(searchQuery, ignoreCase = true) ||
                it.customerNo.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("买家列表") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = "添加买家")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("添加买家") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("搜索买家") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无买家，请点击添加")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { buyer ->
                        BuyerListItem(
                            buyer = buyer,
                            onToggleEnabled = { enabled ->
                                dismissKeyboard()
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val db = AppDatabase.getInstance(context)
                                            db.customerDao().updateEnabledStatus(buyer.id, enabled)
                                            SyncTrigger.triggerCustomerSync(context, buyer.id)
                                        }
                                        snackbarHostState.showSnackbar(
                                            "${buyer.customerName} ${if (enabled) "已启用" else "已禁用"}"
                                        )
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("操作失败：${e.message ?: "未知错误"}")
                                    }
                                }
                            },
                            onEdit = {
                                dismissKeyboard()
                                onEditCustomer(buyer)
                            },
                            onDelete = {
                                dismissKeyboard()
                                onDeleteCustomer(buyer)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuyerListItem(
    buyer: Customer,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (buyer.enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = buyer.customerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (buyer.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Gray
                            },
                        )
                        if (!buyer.enabled) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ) {
                                Text("已禁用", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(buyer.customerNo)
                            if (!buyer.phone.isNullOrBlank()) {
                                append("  ")
                                append(buyer.phone)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = buyer.enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

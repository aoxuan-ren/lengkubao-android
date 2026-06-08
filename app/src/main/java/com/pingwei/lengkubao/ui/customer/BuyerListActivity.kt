package com.pingwei.lengkubao.ui.customer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.data.db.entity.CustomerType
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
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
                        onDeleteCustomer = { customer ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    AppDatabase.getInstance(this@BuyerListActivity)
                                        .customerDao().delete(customer)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@BuyerListActivity, "买家已删除", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@BuyerListActivity, "删除失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
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
    var buyers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

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
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(buyer.customerName, fontWeight = FontWeight.Bold)
                                    Text("${buyer.customerNo}  ${buyer.phone ?: ""}")
                                }
                                Row {
                                    IconButton(onClick = { onEditCustomer(buyer) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                                    }
                                    IconButton(onClick = { onDeleteCustomer(buyer) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

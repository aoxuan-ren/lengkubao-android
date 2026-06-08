package com.pingwei.lengkubao.ui.presale

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.data.db.entity.*
import com.pingwei.lengkubao.ui.common.ProductQuantityPriceInput
import com.pingwei.lengkubao.ui.instock.components.SearchableCustomerField
import com.pingwei.lengkubao.ui.presale.viewmodel.PreSaleOutViewModel
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import kotlinx.coroutines.launch

class PreSaleOutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                PreSaleOutScreen(viewModel = viewModel())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreSaleOutScreen(viewModel: PreSaleOutViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val saleMode by viewModel.saleMode.collectAsState()
    val selectedBuyer by viewModel.selectedBuyer.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val selectedOperator by viewModel.selectedOperator.collectAsState()
    val items by viewModel.items.collectAsState()
    val productsWithStock by viewModel.productsWithStock.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val buyers by viewModel.buyers.collectAsState(initial = emptyList())
    val locations by viewModel.allLocations.collectAsState(initial = emptyList())
    val operators by viewModel.allOperators.collectAsState(initial = emptyList())
    val isSaving = saveResult is PreSaleOutViewModel.SaveResult.Loading

    var showLocationDialog by remember { mutableStateOf(false) }
    var showOperatorDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var savedBillId by remember { mutableStateOf(0L) }
    var savedBillNo by remember { mutableStateOf("") }

    LaunchedEffect(saveResult) {
        when (val result = saveResult) {
            is PreSaleOutViewModel.SaveResult.Success -> {
                savedBillId = result.billId
                savedBillNo = result.billNo
                showPaymentDialog = true
                viewModel.clearSaveResult()
            }
            is PreSaleOutViewModel.SaveResult.Error -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                viewModel.clearSaveResult()
            }
            else -> {}
        }
    }

    if (showPaymentDialog) {
        PreSalePaymentInputDialog(
            totalAmount = viewModel.totalAmount,
            onDismiss = {
                showPaymentDialog = false
                Toast.makeText(context, "预售单 $savedBillNo 已保存", Toast.LENGTH_SHORT).show()
                viewModel.clearForContinue()
            },
            onConfirm = { amount, method ->
                scope.launch {
                    if (amount > 0 && savedBillId > 0) {
                        val db = com.pingwei.lengkubao.data.db.AppDatabase.getInstance(context)
                        val service = com.pingwei.lengkubao.service.PreSaleService(
                            db,
                            com.pingwei.lengkubao.service.StockService(db.stockDao(), db.stockChangeDao())
                        )
                        service.recordPayment(savedBillId, amount, method, "开单收款")
                    }
                    showPaymentDialog = false
                    Toast.makeText(context, "预售单 $savedBillNo 已保存", Toast.LENGTH_SHORT).show()
                    viewModel.clearForContinue()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预售出库") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(Modifier.padding(AppDimens.pagePadding)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("合计金额", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "¥${"%.2f".format(viewModel.totalAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("合计数量", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${viewModel.totalQuantity}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Button(
                        onClick = { viewModel.saveBill() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("保存单据")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppDimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = saleMode == PreSaleMode.PRESALE,
                    onClick = { if (!isSaving) viewModel.setSaleMode(PreSaleMode.PRESALE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("预售模式") }
                SegmentedButton(
                    selected = saleMode == PreSaleMode.DIRECT_OUT,
                    onClick = { if (!isSaving) viewModel.setSaleMode(PreSaleMode.DIRECT_OUT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("出库销售") }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AppDimens.pagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "买家信息",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SearchableCustomerField(
                        customers = buyers,
                        selectedCustomer = selectedBuyer,
                        onCustomerSelected = { if (!isSaving) viewModel.selectBuyer(it) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = selectedBuyer == null,
                        label = "选择买家"
                    )
                    OutlinedTextField(
                        value = when {
                            selectedBuyer == null -> "请先选择买家"
                            selectedLocation != null -> selectedLocation!!.locationName
                            else -> "请选择库位"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("库位") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSaving && selectedBuyer != null) {
                                showLocationDialog = true
                            },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        enabled = !isSaving && selectedBuyer != null
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AppDimens.pagePadding)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("商品明细", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "已选: ${items.size} 种",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    when {
                        selectedBuyer == null -> PreSalePlaceholder("请先选择买家", Icons.Default.PersonOutline)
                        selectedLocation == null -> PreSalePlaceholder("请先选择库位", Icons.Default.LocationOn)
                        else -> {
                            val filtered = productsWithStock.filter { it.availableStock > 0 }
                            if (filtered.isEmpty()) {
                                PreSalePlaceholder("当前库位暂无库存商品", Icons.Default.Inventory)
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.heightIn(max = 400.dp)
                                ) {
                                    items(filtered, key = { it.product.id }) { productWithStock ->
                                        val existing = items.find { it.productId == productWithStock.product.id }
                                        ProductQuantityPriceInput(
                                            productWithStock = productWithStock,
                                            existingQuantity = existing?.quantity ?: 0,
                                            existingPrice = existing?.salePrice ?: 0.0,
                                            onQuantityChange = { quantity, price ->
                                                scope.launch {
                                                    viewModel.addOrUpdateItem(
                                                        product = productWithStock.product,
                                                        quantity = quantity,
                                                        salePrice = price
                                                    ).onFailure { e ->
                                                        Toast.makeText(
                                                            context,
                                                            e.message ?: "操作失败",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            },
                                            isEnabled = !isSaving
                                        )
                                    }
                                }

                                if (items.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "已选择商品 (${items.size}项)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    items.forEach { item ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${item.productName}  ${item.quantity}${item.unit} × ¥${item.salePrice}")
                                            Text("¥${"%.2f".format(item.amount)}", fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AppDimens.pagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "其他信息",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = selectedOperator?.name ?: "请选择经手人",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("经手人") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSaving) { showOperatorDialog = true },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        enabled = !isSaving
                    )
                }
            }

            Text(
                if (saleMode == PreSaleMode.PRESALE) {
                    "预售模式：锁库存，延迟发货，产生应收款"
                } else {
                    "出库销售：即时扣减库存，产生应收款"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showLocationDialog) {
        PreSaleSelectionDialog("选择库位", locations.map { it.locationName }) { index ->
            viewModel.selectLocation(locations[index])
            showLocationDialog = false
        }
    }
    if (showOperatorDialog) {
        PreSaleSelectionDialog("选择经手人", operators.map { it.name }) { index ->
            viewModel.selectOperator(operators[index])
            showOperatorDialog = false
        }
    }
}

@Composable
private fun PreSalePlaceholder(message: String, icon: ImageVector) {
    Box(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Text(message, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun PreSaleSelectionDialog(title: String, options: List<String>, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options.size) { i ->
                    TextButton(onClick = { onSelect(i) }, modifier = Modifier.fillMaxWidth()) {
                        Text(options[i], modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun PreSalePaymentInputDialog(
    totalAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PayMethod.CASH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登记收款（可选）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("应收: ¥${"%.2f".format(totalAmount)}")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("收款金额") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PayMethod.CASH, PayMethod.TRANSFER, PayMethod.OTHER).forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0, method) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("跳过") } }
    )
}

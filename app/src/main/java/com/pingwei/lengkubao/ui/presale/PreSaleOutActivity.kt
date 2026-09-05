package com.pingwei.lengkubao.ui.presale

import android.content.BroadcastReceiver
import android.os.Bundle
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.data.db.entity.*
import com.pingwei.lengkubao.ui.common.ProductQuantityPriceInput
import com.pingwei.lengkubao.ui.instock.components.CompactSelectField
import com.pingwei.lengkubao.ui.instock.components.LocationSelectorDialog
import com.pingwei.lengkubao.ui.instock.components.OperatorSelectorDialog
import com.pingwei.lengkubao.ui.instock.components.SearchableCustomerField
import com.pingwei.lengkubao.ui.presale.viewmodel.PreSaleOutViewModel
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import kotlinx.coroutines.launch

class PreSaleOutActivity : ComponentActivity() {
    private var yearChangeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        yearChangeReceiver = registerFinishOnFiscalYearChanged()
        setContent {
            LengkubaoTheme {
                PreSaleOutScreen(viewModel = viewModel())
            }
        }
    }

    override fun onDestroy() {
        unregisterFinishOnFiscalYearChanged(yearChangeReceiver)
        yearChangeReceiver = null
        super.onDestroy()
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
    val remark by viewModel.remark.collectAsState()
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
                title = {
                    Text(
                        "预售出库",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { (context as? ComponentActivity)?.finish() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                expandedHeight = 48.dp,
                windowInsets = WindowInsets.statusBars
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (saleMode == PreSaleMode.DIRECT_OUT) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                        .clickable(enabled = !isSaving) {
                            viewModel.setSaleMode(PreSaleMode.DIRECT_OUT)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "出库",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (saleMode == PreSaleMode.DIRECT_OUT) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (saleMode == PreSaleMode.PRESALE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                        .clickable(enabled = !isSaving) {
                            viewModel.setSaleMode(PreSaleMode.PRESALE)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "预售",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (saleMode == PreSaleMode.PRESALE) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchableCustomerField(
                        customers = buyers,
                        selectedCustomer = selectedBuyer,
                        onCustomerSelected = { if (!isSaving) viewModel.selectBuyer(it) },
                        modifier = Modifier.weight(1.2f),
                        isError = selectedBuyer == null,
                        fieldHeight = 40.dp,
                        fieldTextStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        showFloatingLabel = false
                    )
                    CompactSelectField(
                        text = selectedLocation?.locationName.orEmpty(),
                        placeholder = if (selectedBuyer == null) "请先选择买家" else "请选择库位",
                        isError = selectedBuyer != null && selectedLocation == null,
                        enabled = !isSaving,
                        onClick = {
                            if (selectedBuyer == null) {
                                Toast.makeText(context, "请先选择买家", Toast.LENGTH_SHORT).show()
                            } else {
                                showLocationDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(AppDimens.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        selectedBuyer == null -> PreSalePlaceholder("请先选择买家")
                        selectedLocation == null -> PreSalePlaceholder("请先选择库位")
                        else -> {
                            val filtered = productsWithStock.filter { it.availableStock > 0 }
                            if (filtered.isEmpty()) {
                                PreSalePlaceholder("当前库位暂无库存商品")
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
                                    items.forEach { item ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${item.productName}  ${item.quantity}${item.unit} × ¥${item.salePrice}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                "¥${"%.2f".format(item.amount)}",
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
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
                        enabled = !isSaving,
                        onClick = { showOperatorDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text(
                if (saleMode == PreSaleMode.PRESALE) {
                    "预售：锁库存，延迟发货，产生应收"
                } else {
                    "出库：即时扣库存，产生应收"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showLocationDialog) {
        LocationSelectorDialog(
            locations = locations,
            onDismiss = { showLocationDialog = false },
            onLocationSelected = {
                viewModel.selectLocation(it)
                showLocationDialog = false
            }
        )
    }

    if (showOperatorDialog) {
        OperatorSelectorDialog(
            operators = operators,
            onDismiss = { showOperatorDialog = false },
            onOperatorSelected = {
                viewModel.selectOperator(it)
                showOperatorDialog = false
            }
        )
    }
}

@Composable
private fun PreSalePlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreSalePaymentInputDialog(
    totalAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PayMethod.WECHAT) }
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
                    listOf(PayMethod.WECHAT, PayMethod.TRANSFER, PayMethod.OTHER).forEach { m ->
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

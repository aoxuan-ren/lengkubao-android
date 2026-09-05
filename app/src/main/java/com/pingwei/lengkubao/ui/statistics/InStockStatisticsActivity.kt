package com.pingwei.lengkubao.ui.statistics

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.data.db.entity.CustomerType
import com.pingwei.lengkubao.data.db.entity.PcInboundDailySnapshot
import com.pingwei.lengkubao.ui.common.ConfigManager
import com.pingwei.lengkubao.ui.instock.components.SearchableOptionalCustomerField
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class InStockStatisticsActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getInstance(this)

        setContent {
            LengkubaoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InStockStatisticsContent()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun InStockStatisticsContent() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val configManager = remember { ConfigManager(context) }

        // 状态管理
        var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
        var customerList by remember { mutableStateOf<List<Customer>>(emptyList()) }

        // 日期范围：开始日期记住上次选择；结束日期每次进入默认为今天
        val calendar = remember { Calendar.getInstance() }
        val defaultStartDate = remember {
            Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis
        }
        var startDate by remember {
            mutableLongStateOf(configManager.getInStockStatsStartDate(defaultStartDate))
        }
        var endDate by remember {
            mutableLongStateOf(todayEndMillis())
        }

        var showStartDatePicker by remember { mutableStateOf(false) }
        var showEndDatePicker by remember { mutableStateOf(false) }

        // 统计结果
        var statsList by remember { mutableStateOf<List<InboundStatItem>>(emptyList()) }
        var totalStats by remember { mutableStateOf(InboundTotalStats()) }
        var isLoading by remember { mutableStateOf(false) }
        var hasQueried by remember { mutableStateOf(false) }

        // 视图模式 (0-按库位分组, 1-按商品分组)
        var viewMode by remember { mutableIntStateOf(0) }

        suspend fun loadCustomers(): List<Customer> = withContext(Dispatchers.IO) {
            try {
                database.customerDao().getCustomersByTypeSync(CustomerType.SELLER)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

        // 加载客户列表
        LaunchedEffect(Unit) {
            customerList = loadCustomers()
        }

        fun queryStatistics() {
            coroutineScope.launch {
                isLoading = true
                hasQueried = true
                try {
                    val customerNo = selectedCustomer?.customerNo

                    // 添加日期格式化调试
                    val startDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(startDate))
                    val endDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(endDate))

                    Log.d("InStockStats", "查询参数: customerNo=$customerNo")
                    Log.d("InStockStats", "startDate=$startDate ($startDateStr)")
                    Log.d("InStockStats", "endDate=$endDate ($endDateStr)")

                    val startDateKey = dateFormat.format(Date(startDate))
                    val endDateKey = dateFormat.format(Date(endDate))

                    val snapshotRows = withContext(Dispatchers.IO) {
                        database.pcInboundDailySnapshotDao().queryRange(
                            startDate = startDateKey,
                            endDate = endDateKey,
                            customerNo = customerNo
                        )
                    }

                    Log.d("InStockStats", "PC入库快照: ${snapshotRows.size} 条")

                    aggregateFromSnapshotRows(snapshotRows, viewMode).let { (items, totals) ->
                        statsList = items
                        totalStats = totals
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "查询失败: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isLoading = false
                }
            }
        }

        // 日期选择器
        if (showStartDatePicker) {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth, 0, 0, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    startDate = calendar.timeInMillis
                    configManager.saveInStockStatsStartDate(startDate)
                    showStartDatePicker = false
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        if (showEndDatePicker) {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth, 23, 59, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    endDate = calendar.timeInMillis
                    showEndDatePicker = false
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("入库统计", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "按库位/商品分组统计入库数量",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF4CAF50),
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        // 视图切换按钮
                        IconButton(onClick = { viewMode = if (viewMode == 0) 1 else 0 }) {
                            Icon(
                                if (viewMode == 0) Icons.Default.Sort else Icons.Default.ViewList,
                                contentDescription = "切换视图"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            // 使用 Column + weight 布局
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)  // 减少顶部内边距
            ) {
                // 查询条件卡片 - 减小内边距，更紧凑
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),  // 从16dp减小到12dp
                        verticalArrangement = Arrangement.spacedBy(6.dp)  // 从8dp减小到6dp，减小间距
                    ) {
                        // 客户选择
                        SearchableOptionalCustomerField(
                            customers = customerList,
                            selectedCustomer = selectedCustomer,
                            onCustomerSelected = { selectedCustomer = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 日期范围 - 直接显示日期文本，没有图标
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)  // 从8dp减小到6dp
                        ) {
                            // 开始日期
                            OutlinedCard(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    calendar.timeInMillis = startDate
                                    showStartDatePicker = true
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = formatDate(startDate),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 结束日期（截止时间，每次进入页面默认为今天）
                            OutlinedCard(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    calendar.timeInMillis = endDate
                                    showEndDatePicker = true
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = formatDate(endDate),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // 查询按钮
                        Button(
                            onClick = { queryStatistics() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),  // 从40dp减小到36dp
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("查询中...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("查询统计", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))  // 从12dp减小到10dp

                // 统计结果
                if (hasQueried) {
                    // 统计卡片 - 更紧凑，已删除"统计总计"文字
                    StatsSummaryCard(totalStats)

                    Spacer(modifier = Modifier.height(6.dp))  // 从8dp减小到6dp

                    // 统计明细表格
                    StatsTable(
                        statsList = statsList,
                        viewMode = viewMode,
                        isLoading = isLoading,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // 初始提示 - 使用 weight 占满剩余空间并居中
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFF4CAF50).copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "入库统计",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                text = "请选择查询条件开始统计",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun StatsSummaryCard(totalStats: InboundTotalStats) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 12.dp)  // 从10dp减小到8dp
            ) {
                // 四个统计数量并列一横排 - 已删除"统计总计"文字
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        title = "总数量",
                        value = "${totalStats.totalQuantity}",
                        color = Color(0xFF4CAF50)
                    )

                    StatItem(
                        title = "总金额",
                        value = "¥${String.format("%.2f", totalStats.totalAmount)}",
                        color = Color(0xFF2196F3)
                    )

                    StatItem(
                        title = "商品种数",
                        value = "${totalStats.productCount}",
                        color = Color(0xFFFF9800)
                    )

                    StatItem(
                        title = "入库单数",
                        value = "${totalStats.orderCount}",
                        color = Color(0xFF9C27B0)
                    )
                }
            }
        }
    }

    @Composable
    fun StatItem(title: String, value: String, color: Color) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            // 单位已删除
        }
    }

    @Composable
    fun StatsTable(
        statsList: List<InboundStatItem>,
        viewMode: Int,
        isLoading: Boolean,
        modifier: Modifier = Modifier
    ) {
        Log.d("InStockStats", "StatsTable 接收到数据: ${statsList.size} 条")

        Card(
            modifier = modifier  // 使用传入的 modifier（包含 weight(1f)）
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {
                // 表头 - 只有库位、型号、数量、单数
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "库位",
                        modifier = Modifier.weight(1.5f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "型号",
                        modifier = Modifier.weight(2f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "数量",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "单数",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF4CAF50))
                    }
                } else {
                    if (statsList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无数据，请先与 PC 同步",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(statsList) { item ->
                                Log.d("InStockStats", "渲染行: ${item.productName}, 数量: ${item.quantity}")
                                StatsTableRow(item, viewMode)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun StatsTableRow(item: InboundStatItem, viewMode: Int) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.locationName.ifEmpty { "未知库位" },
                modifier = Modifier.weight(1.5f),
                fontSize = 12.sp,
                maxLines = 1
            )
            Text(
                text = item.productName.ifEmpty { "未知型号" },
                modifier = Modifier.weight(2f),
                fontSize = 12.sp,
                maxLines = 1
            )
            Text(
                text = "${item.quantity}",
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4CAF50)
            )
            Text(
                text = "${item.orderCount}",
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                color = Color.Gray
            )
        }
    }

    private fun aggregateFromSnapshotRows(
        snapshotRows: List<PcInboundDailySnapshot>,
        viewMode: Int
    ): Pair<List<InboundStatItem>, InboundTotalStats> {
        val grouped = when (viewMode) {
            1 -> snapshotRows.groupBy { it.spec }
            else -> snapshotRows.groupBy { "${it.locationName}||${it.spec}" }
        }
        val items = grouped.values.map { rows ->
            val first = rows.first()
            InboundStatItem(
                locationId = 0L,
                locationName = if (viewMode == 1) "" else first.locationName,
                productId = 0L,
                productNo = "",
                productName = first.spec,
                quantity = rows.sumOf { it.quantity },
                orderCount = rows.sumOf { it.orderCount },
                totalAmount = rows.sumOf { it.amount }
            )
        }.sortedWith(
            compareByDescending<InboundStatItem> { it.quantity }
                .thenBy { it.locationName }
                .thenBy { it.productName }
        )
        val totals = InboundTotalStats(
            totalQuantity = snapshotRows.sumOf { it.quantity },
            totalAmount = snapshotRows.sumOf { it.amount },
            productCount = snapshotRows.map { it.spec }.distinct().size,
            orderCount = snapshotRows.sumOf { it.orderCount }
        )
        return items to totals
    }

    private fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    private fun todayEndMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
}

/**
 * UI层使用的数据类
 */
data class InboundStatItem(
    val locationId: Long,
    val locationName: String,
    val productId: Long,
    val productNo: String,
    val productName: String,
    val quantity: Int,
    val orderCount: Int,
    val totalAmount: Double
)

/**
 * UI层使用的总计类
 */
data class InboundTotalStats(
    val totalQuantity: Int = 0,
    val totalAmount: Double = 0.0,
    val productCount: Int = 0,
    val orderCount: Int = 0
)
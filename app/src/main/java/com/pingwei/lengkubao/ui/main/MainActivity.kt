package com.pingwei.lengkubao.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.pingwei.lengkubao.ui.packaging.PackagingActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.pingwei.lengkubao.ui.config.ConfigMainActivity
import com.pingwei.lengkubao.ui.customer.CustomerAddActivity
import com.pingwei.lengkubao.ui.instock.InStockActivity
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pingwei.lengkubao.ui.saleout.SaleOutActivity
import com.pingwei.lengkubao.ui.presale.PreSaleOutActivity
import com.pingwei.lengkubao.ui.presale.PreSaleQueryActivity
import com.pingwei.lengkubao.ui.query.instock.InStockQueryActivity
import com.pingwei.lengkubao.ui.query.packaging.PackagingQueryActivity
import com.pingwei.lengkubao.ui.query.saleout.SaleOutQueryActivity
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.service.TcpSyncService
import com.pingwei.lengkubao.sync.rememberTcpConnectionStatus
import com.pingwei.lengkubao.utils.Constant
import com.pingwei.lengkubao.ui.advancededuction.AdvanceDeductionActivity
import com.pingwei.lengkubao.ui.cashflow.CashFlowActivity
import com.pingwei.lengkubao.ui.statistics.InStockStatisticsActivity
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
        val storageGranted = permissions[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false

        if (cameraGranted && storageGranted) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "部分权限未授予，可能影响扫码功能", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        checkRequiredPermissions()
        val prefs = getSharedPreferences("sync_config", MODE_PRIVATE)
        if (prefs.getBoolean(Constant.PREF_AUTO_SYNC, Constant.PREF_AUTO_SYNC_DEFAULT)) {
            startTcpSyncService()
        }
        setContent {
            LengkubaoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreenContent(lifecycleScope)
                }
            }
        }
    }

    private fun checkRequiredPermissions() {
        val requiredPermissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val deniedPermissions = requiredPermissions.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (deniedPermissions.isNotEmpty()) {
            permissionLauncher.launch(deniedPermissions)
        }
    }
    /**
     * 启动TCP同步服务
     */
    private fun startTcpSyncService() {
        try {
            TcpSyncService.startService(this)
            Log.i("MainActivity", "✅ TCP同步服务已启动")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ 启动TCP同步服务失败: ${e.message}")
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreenContent(activityScope: kotlinx.coroutines.CoroutineScope) {
        val context = LocalContext.current
        val queryScrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        // 未同步单据总数、详情、加载状态、弹窗控制
        var pendingTotal by remember { mutableIntStateOf(0) }
        var pendingDetails by remember { mutableStateOf<Map<String, Int>?>(null) }
        var showSyncDialog by remember { mutableStateOf(false) }

        // TCP连接状态（年份切换后自动换绑当前 TcpSyncManager）
        val (isTcpConnected, tcpConnectionState) = rememberTcpConnectionStatus()

        // 初始加载未同步数量
        LaunchedEffect(Unit) {
            refreshPendingSyncCount(context) { count, details ->
                pendingTotal = count
                pendingDetails = details
            }
        }

        // 定时刷新未同步数量（30秒一次）
        LaunchedEffect(Unit) {
            while (true) {
                delay(30000)
                refreshPendingSyncCount(context) { count, details ->
                    pendingTotal = count
                    pendingDetails = details
                }
            }
        }

        // 从开单等页面返回主页时立即刷新待同步数量
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    coroutineScope.launch {
                        refreshPendingSyncCount(context) { count, details ->
                            pendingTotal = count
                            pendingDetails = details
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 服务中单张单据同步完成时刷新（与查询页广播一致）
        DisposableEffect(context) {
            val syncReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != TcpSyncService.ACTION_SYNC_COMPLETE) return
                    coroutineScope.launch {
                        refreshPendingSyncCount(context) { count, details ->
                            pendingTotal = count
                            pendingDetails = details
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                syncReceiver,
                IntentFilter(TcpSyncService.ACTION_SYNC_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            onDispose {
                try {
                    context.unregisterReceiver(syncReceiver)
                } catch (_: IllegalArgumentException) {
                    Log.w("MainActivity", "同步完成广播接收器已注销或重复注销")
                }
            }
        }

        // 打开同步详情弹窗时拉取最新明细
        LaunchedEffect(showSyncDialog) {
            if (showSyncDialog) {
                refreshPendingSyncCount(context) { count, details ->
                    pendingTotal = count
                    pendingDetails = details
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "冷库宝管理系统",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            // 在标题下方显示TCP连接状态文本
                            Text(
                                text = "TCP: $tcpConnectionState",
                                fontSize = 9.sp,
                                color = if (isTcpConnected) Color.Green else Color.Red
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    // 导航栏右侧添加状态指示灯+同步图标
                    actions = {
                        // TCP连接状态指示灯
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isTcpConnected) Color.Green else Color.Red,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = { showSyncDialog = true }) {
                            BadgedBox(
                                badge = {
                                    if (pendingTotal > 0) {
                                        Badge {
                                            Text(text = pendingTotal.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "同步状态",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val viewportHeight = maxHeight

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(queryScrollState)
                ) {
                    // 8 个主功能：刚好占满首屏；条形查询在下方滑动可见
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(viewportHeight)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(AppDimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                        ) {
                            ButtonRow(
                                modifier = Modifier.weight(1f),
                                button1 = {
                                    ActionButton(
                                        text = "入库开单",
                                        icon = Icons.Default.Input,
                                        color = Color(0xFF4CAF50),
                                        onClick = {
                                            context.startActivity(Intent(context, InStockActivity::class.java))
                                        }
                                    )
                                },
                                button2 = {
                                    ActionButton(
                                        text = "客户报账",
                                        icon = Icons.Default.ShoppingCart,
                                        color = Color(0xFF2196F3),
                                        onClick = {
                                            context.startActivity(Intent(context, SaleOutActivity::class.java))
                                        }
                                    )
                                }
                            )

                            ButtonRow(
                                modifier = Modifier.weight(1f),
                                button1 = {
                                    ActionButton(
                                        text = "包装记账",
                                        icon = Icons.Default.Inventory2,
                                        color = Color(0xFF9C27B0),
                                        onClick = {
                                            context.startActivity(Intent(context, PackagingActivity::class.java))
                                        }
                                    )
                                },
                                button2 = {
                                    ActionButton(
                                        text = "收支流水",
                                        icon = Icons.Default.AccountBalance,
                                        color = Color(0xFF00897B),
                                        onClick = {
                                            context.startActivity(Intent(context, CashFlowActivity::class.java))
                                        }
                                    )
                                }
                            )

                            ButtonRow(
                                modifier = Modifier.weight(1f),
                                button1 = {
                                    ActionButton(
                                        text = "预售出库",
                                        icon = Icons.Default.LocalShipping,
                                        color = Color(0xFFE91E63),
                                        onClick = {
                                            context.startActivity(Intent(context, PreSaleOutActivity::class.java))
                                        }
                                    )
                                },
                                button2 = {
                                    ActionButton(
                                        text = "预支扣款",
                                        icon = Icons.Default.AttachMoney,
                                        color = Color(0xFFFF9800),
                                        onClick = {
                                            context.startActivity(Intent(context, AdvanceDeductionActivity::class.java))
                                        }
                                    )
                                }
                            )

                            ButtonRow(
                                modifier = Modifier.weight(1f),
                                button1 = {
                                    ActionButton(
                                        text = "基础配置",
                                        icon = Icons.Default.Settings,
                                        color = Color(0xFF795548),
                                        onClick = {
                                            context.startActivity(Intent(context, ConfigMainActivity::class.java))
                                        }
                                    )
                                },
                                button2 = {
                                    ActionButton(
                                        text = "TCP配置",
                                        icon = Icons.Default.Sync,
                                        color = Color(0xFF009688),
                                        onClick = {
                                            try {
                                                val intent = Intent(
                                                    context,
                                                    Class.forName("com.pingwei.lengkubao.ui.sync.TcpSyncConfigActivity")
                                                )
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "TCP配置界面未找到", Toast.LENGTH_SHORT).show()
                                                e.printStackTrace()
                                            }
                                        }
                                    )
                                }
                            )
                        }

                        if (pendingTotal > 0) {
                            PendingSyncWarningCard(
                                pendingCount = pendingTotal,
                                onClick = { showSyncDialog = true },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.itemSpacing)
                            )
                        }
                    }

                    // 条形查询按钮：主功能下方，下滑查看
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.pagePadding),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                    ) {
                        QueryButton(
                            text = "入库统计",
                            icon = Icons.Default.BarChart,
                            targetClass = InStockStatisticsActivity::class.java
                        )
                        QueryButton(
                            text = "入库单查询",
                            icon = Icons.Default.Receipt,
                            targetClass = InStockQueryActivity::class.java
                        )
                        QueryButton(
                            text = "包装单查询",
                            icon = Icons.Default.Inventory2,
                            targetClass = PackagingQueryActivity::class.java
                        )
                        QueryButton(
                            text = "报账单查询",
                            icon = Icons.Default.Receipt,
                            targetClass = SaleOutQueryActivity::class.java
                        )
                        QueryButton(
                            text = "预售单查询",
                            icon = Icons.Default.LocalShipping,
                            targetClass = PreSaleQueryActivity::class.java
                        )
                        QueryButton(
                            text = "预支扣款查询",
                            icon = Icons.Default.AttachMoney,
                            targetClass = com.pingwei.lengkubao.ui.query.advancededuction.AdvanceDeductionQueryActivity::class.java
                        )
                        CopyrightText()
                    }
                }

                if (showSyncDialog) {
                    SyncStatusDialog(
                        pendingDetails = pendingDetails,
                        onDismiss = { showSyncDialog = false },
                        onSyncNow = {
                            TcpSyncService.syncPendingNow(context)
                            Toast.makeText(context, "已提交后台同步请求", Toast.LENGTH_SHORT).show()
                            activityScope.launch {
                                refreshPendingSyncCount(context) { count, details ->
                                    pendingTotal = count
                                    pendingDetails = details
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * 刷新未同步单据数量和详情（包含预支扣款）
     */
    private suspend fun refreshPendingSyncCount(
        context: android.content.Context,
        onResult: (Int, Map<String, Int>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val database = AppDatabase.getInstance(context)

        // 查询各类未同步单据
        val pendingIn = database.inStockBillDao().getAllBills().first().count { it.syncStatus == 0 }
        val pendingSale = database.saleBillDao().getAllBills().first().count { it.syncStatus == 0 }
        val pendingPack = database.packagingBillDao().getAllBills().first().count { !it.isSynced }

        // 新增：查询未同步的预支款和扣款 - 修正写法
        val pendingAdvances = database.advanceDao().getUnsyncedAdvances().size
        val pendingDeductions = database.deductionDao().getUnsyncedDeductions().size
        val pendingPresales = database.preSaleBillDao().getUnsyncedBills().size
        val pendingPresalePayments = database.paymentRecordDao().getUnsyncedPayments().size
        val pendingLedger = database.ledgerEntryDao().getUnsyncedEntries().size

        val total = pendingIn + pendingSale + pendingPack + pendingAdvances + pendingDeductions +
            pendingPresales + pendingPresalePayments + pendingLedger
        val details = mapOf(
            "入库单" to pendingIn,
            "销售单" to pendingSale,
            "包装单" to pendingPack,
            "预支款" to pendingAdvances,
            "扣款" to pendingDeductions,
            "预售单" to pendingPresales,
            "预售收款" to pendingPresalePayments,
            "收支流水" to pendingLedger,
            "总计" to total
        )

        // 切回主线程更新UI
        withContext(Dispatchers.Main) {
            onResult(total, details)
        }
    }
}

// ====================== UI组件 ======================

/**
 * 待同步单据提醒卡片
 */
@Composable
fun PendingSyncWarningCard(
    pendingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3CD),
            contentColor = Color(0xFF5D4037)
        ),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Error, contentDescription = "待同步")
                Text(
                    text = "$pendingCount 张单据待同步，点击查看详情",
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(Icons.Default.ArrowForwardIos, contentDescription = "查看详情", modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * 同步状态详情弹窗
 */
@Composable
fun SyncStatusDialog(
    pendingDetails: Map<String, Int>?,
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit
) {
    val total = pendingDetails?.get("总计") ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同步状态详情") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pendingDetails == null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    }
                } else if (total == 0) {
                    Text("✅ 所有数据已完成同步，无待同步数据", color = Color(0xFF388E3C))
                } else {
                    Text("待同步数据明细：", fontWeight = FontWeight.Bold)
                    pendingDetails.forEach { (type, count) ->
                        if (type != "总计" && count > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(type)
                                Text("$count 条")
                            }
                        }
                    }
                    Text(
                        text = "总计：$total 条数据",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (total > 0) {
                Button(onClick = {
                    onSyncNow()
                    onDismiss()
                }) {
                    Text("立即同步")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun ButtonRow(
    button1: @Composable () -> Unit,
    button2: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            button1()
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            button2()
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f),
            contentColor = color
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(AppDimens.homeActionIconSize),
                tint = color
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * 统一查询按钮封装
 */
@Composable
fun QueryButton(
    text: String,
    icon: ImageVector,
    targetClass: Class<*>
) {
    val context = LocalContext.current
    Button(
        onClick = {
            context.startActivity(Intent(context, targetClass))
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.homeQueryButtonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = text, modifier = Modifier.size(AppDimens.homeQueryIconSize))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun CopyrightText() {
    Text(
        // 使用 \n 实现换行
        text = "© 2025 冷库宝管理系统\n联系电话：13315168281",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.itemSpacing),
        textAlign = TextAlign.Center,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
package com.pingwei.lengkubao.ui.config

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.sync.TcpSyncManager
import com.pingwei.lengkubao.utils.AppCacheCleaner
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import com.pingwei.lengkubao.ui.customer.BuyerListActivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                ConfigMainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigMainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("基础配置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (navController.currentBackStackEntry?.destination?.route != "main") {
                                navController.navigateUp()
                            } else {
                                // 如果是主页面，直接返回
                                (context as? ComponentActivity)?.finish()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = "main"
            ) {
                composable("main") {
                    // 使用可滚动的内容区域
                    ScrollableConfigMainContent(
                        onProductConfigClick = { navController.navigate("product_config") },
                        onLocationConfigClick = { navController.navigate("location_config") },
                        onOperatorConfigClick = { navController.navigate("operator_config") },
                        onPackagingConfigClick = { navController.navigate("packaging_config") },
                        onPrintConfigClick = {
                            context.startActivity(Intent(context, PrintConfigActivity::class.java))
                        },
                        onBuyerListClick = {
                            context.startActivity(Intent(context, BuyerListActivity::class.java))
                        },
                        snackbarHostState = snackbarHostState,
                        coroutineScope = coroutineScope
                    )
                }

                composable("product_config") {
                    ProductConfigScreen()
                }

                composable("location_config") {
                    LocationConfigScreen(onBack = { navController.navigateUp() })
                }

                composable("operator_config") {
                    OperatorConfigScreen(onBack = { navController.navigateUp() })
                }

                composable("packaging_config") {
                    PackagingConfigScreen()
                }
            }
        }
    }
}

@Composable
fun ScrollableConfigMainContent(
    onProductConfigClick: () -> Unit,
    onLocationConfigClick: () -> Unit,
    onOperatorConfigClick: () -> Unit,
    onPackagingConfigClick: () -> Unit,
    onPrintConfigClick: () -> Unit,
    onBuyerListClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
) {
    val appContext = LocalContext.current.applicationContext

    // ========== 关键修复：在这里声明所有状态变量 ==========
    val syncManager = remember { TcpSyncManager.getInstance(appContext, AppDatabase.getInstance(appContext)) }

    // 同步状态变量
    var isSyncing by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableStateOf(0) }
    var syncTotal by remember { mutableStateOf(0) }
    var syncType by remember { mutableStateOf("") }
    var syncResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var unsyncedCounts by remember { mutableStateOf<Map<String, Int>?>(null) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cleanupPreview by remember { mutableStateOf<AppCacheCleaner.CleanupPreview?>(null) }

    // 加载未同步数量
    LaunchedEffect(Unit) {
        try {
            unsyncedCounts = syncManager.checkUnsyncedConfigs()
        } catch (e: Exception) {
            Log.e("Config", "检查未同步失败", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 标题
        Text(
            text = "系统配置",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        )

        // 同步功能区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "数据同步",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 显示未同步状态
                unsyncedCounts?.let { counts ->
                    val total = counts["总计"] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 只显示库位、经手人、客户，不显示商品
                        listOf("库位", "经手人", "客户").forEach { type ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = (counts[type] ?: 0).toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if ((counts[type] ?: 0) > 0)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = type,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (total > 0) {
                        Text(
                            text = "有 $total 条数据未同步",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                 }

                // 进度条
                if (isSyncing && syncTotal > 0) {
                    LinearProgressIndicator(
                        progress = syncProgress.toFloat() / syncTotal.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        text = "$syncType: $syncProgress/$syncTotal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // 结果显示
                syncResult?.let { result ->
                    val (success, message) = result
                    Text(
                        text = if (success) "✅ $message" else "❌ $message",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (success) Color.Green else Color.Red,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 定时检查按钮
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    syncResult = null
                                    unsyncedCounts = syncManager.checkUnsyncedConfigs()
                                    val total = unsyncedCounts?.get("总计") ?: 0
                                    snackbarHostState.showSnackbar(
                                        if (total > 0) "发现 $total 条数据未同步"
                                        else "所有基础数据均已同步"
                                    )
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("检查失败：${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSyncing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("定时检查")
                    }

                    // 同步基础数据按钮
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSyncing = true
                                syncResult = null
                                syncProgress = 0
                                syncTotal = 0
                                syncType = ""

                                // 检查连接
                                if (!syncManager.isConnected()) {
                                    syncResult = Pair(false, "未连接服务器，请先连接")
                                    isSyncing = false
                                    snackbarHostState.showSnackbar("未连接服务器，请先连接")
                                    return@launch
                                }

                                // 显示开始同步提示
                                snackbarHostState.showSnackbar("开始同步基础配置...")

                                // 执行同步（内部已包含正向同步和自动反向同步）
                                syncManager.syncAllConfigs(
                                    onProgress = { current, total, type ->
                                        syncProgress = current
                                        syncTotal = total
                                        syncType = type
                                        Log.d("Config", "同步进度: $type $current/$total")
                                    },
                                    onResult = { success, message ->
                                        syncResult = Pair(success, message)
                                        isSyncing = false

                                        // 重新检查未同步状态
                                        coroutineScope.launch {
                                            unsyncedCounts = syncManager.checkUnsyncedConfigs()
                                        }

                                        // 显示结果
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(message)

                                            // 如果同步成功，额外显示详细信息
                                            if (success) {
                                                val counts = syncManager.checkUnsyncedConfigs()
                                                val totalUnsynced = counts["总计"] ?: 0
                                                if (totalUnsynced == 0) {
                                                    Log.d("Config", "所有基础数据已同步完成")
                                                } else {
                                                    Log.d("Config", "还有 $totalUnsynced 条数据未同步")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSyncing && syncManager.isConnected()
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSyncing) {
                            if (syncType.isNotEmpty()) "syncType同步中..." else "同步中..."
                        } else {
                            "同步基础数据"
                        })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 配置项卡片
        ConfigCard(
            title = "商品型号管理",
            description = "管理梨的型号，如42型、45型等",
            icon = Icons.Filled.ShoppingBag,
            onClick = onProductConfigClick
        )

        ConfigCard(
            title = "包装类型管理",
            description = "管理包装材料，如纸箱、泡沫箱、胶带等",
            icon = Icons.Filled.Inventory2,
            onClick = onPackagingConfigClick
        )

        ConfigCard(
            title = "库位管理",
            description = "管理冷库位置，如东1库、西2库等",
            icon = Icons.Filled.LocationOn,
            onClick = onLocationConfigClick
        )

        ConfigCard(
            title = "经手人管理",
            description = "管理操作员/经手人信息",
            icon = Icons.Filled.Person,
            onClick = onOperatorConfigClick
        )

        ConfigCard(
            title = "买家列表",
            description = "管理预售出库买家信息",
            icon = Icons.Filled.People,
            onClick = onBuyerListClick
        )

        ConfigCard(
            title = "打印配置",
            description = "配置打印机和企业信息",
            icon = Icons.Filled.Print,
            onClick = onPrintConfigClick
        )

        ConfigCard(
            title = "企业信息",
            description = "设置公司名称、地址、电话",
            icon = Icons.Filled.Business,
            onClick = onPrintConfigClick
        )

        ConfigCard(
            title = "清理缓存并重启",
            description = "清理配置和临时缓存，不删除本地单据",
            icon = Icons.Filled.CleaningServices,
            onClick = { showClearCacheDialog = true }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 查看数据状态按钮
        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val db = AppDatabase.getInstance(appContext)
                        val productCount = withContext(Dispatchers.IO) {
                            db.productDao().getAll().size
                        }
                        snackbarHostState.showSnackbar("当前共有 $productCount 个商品")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("查询失败: ${e.message ?: "未知错误"}")
                    }
                }
            }
        ) {
            Text("查看数据状态")
        }

        // 底部说明
        Text(
            text = "提示：首次使用建议点击'同步基础数据'将本地数据上传到服务器",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            textAlign = TextAlign.Center
        )
    }

    if (showClearCacheDialog) {
        LaunchedEffect(showClearCacheDialog) {
            cleanupPreview = withContext(Dispatchers.IO) {
                AppCacheCleaner.getCleanupPreview(appContext)
            }
        }
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清理缓存并重启") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("将清理以下内容后自动重启：")
                    cleanupPreview?.items?.forEach { item ->
                        Text("• $item", style = MaterialTheme.typography.bodySmall)
                    }
                    val cacheMb = ((cleanupPreview?.cacheBytes ?: 0L) / 1024.0 / 1024.0)
                    Text("预计清理临时缓存: ${"%.2f".format(cacheMb)} MB")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                AppCacheCleaner.clearCacheAndPrepareRestart(appContext)
                            }
                            if (result.success) {
                                snackbarHostState.showSnackbar("缓存清理完成，正在重启应用...")
                                AppCacheCleaner.restartApp(appContext)
                            } else {
                                snackbarHostState.showSnackbar("清理失败：${result.message}")
                            }
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ConfigCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            // 文本区域
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 右侧箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}



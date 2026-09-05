package com.pingwei.lengkubao.ui.config

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.fiscal.FiscalYearManager
import com.pingwei.lengkubao.fiscal.YearSwitchCoordinator
import com.pingwei.lengkubao.utils.AppCacheCleaner
import com.pingwei.lengkubao.utils.ConfigSyncStatusNotifier
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import com.pingwei.lengkubao.ui.customer.BuyerListActivity
import com.pingwei.lengkubao.ui.customer.CustomerListActivity

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

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
    val mainScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

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
                        scrollState = mainScrollState,
                        onCustomerManagementClick = {
                            context.startActivity(Intent(context, CustomerListActivity::class.java))
                        },
                        onProductConfigClick = { navController.navigateConfigSub("product_config") },
                        onLocationConfigClick = { navController.navigateConfigSub("location_config") },
                        onOperatorConfigClick = { navController.navigateConfigSub("operator_config") },
                        onPackagingConfigClick = { navController.navigateConfigSub("packaging_config") },
                        onCompanyInfoClick = {
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
                    ProductConfigScreen(
                        navController = navController,
                        snackbarHostState = snackbarHostState,
                    )
                }

                composable("product_add") {
                    ProductFormScreen(productId = null, onSaved = { navController.navigateUp() })
                }

                composable(
                    route = "product_edit/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: return@composable
                    ProductFormScreen(productId = id, onSaved = { navController.navigateUp() })
                }

                composable("location_config") {
                    LocationConfigScreen(
                        navController = navController,
                        snackbarHostState = snackbarHostState,
                    )
                }

                composable("location_add") {
                    LocationFormScreen(locationId = null, onSaved = { navController.navigateUp() })
                }

                composable(
                    route = "location_edit/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: return@composable
                    LocationFormScreen(locationId = id, onSaved = { navController.navigateUp() })
                }

                composable("operator_config") {
                    OperatorConfigScreen(
                        navController = navController,
                        snackbarHostState = snackbarHostState,
                    )
                }

                composable("operator_add") {
                    OperatorFormScreen(operatorId = null, onSaved = { navController.navigateUp() })
                }

                composable(
                    route = "operator_edit/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: return@composable
                    OperatorFormScreen(operatorId = id, onSaved = { navController.navigateUp() })
                }

                composable("packaging_config") {
                    PackagingConfigScreen(
                        navController = navController,
                        snackbarHostState = snackbarHostState,
                    )
                }

                composable("packaging_add") {
                    PackagingTypeFormScreen(packagingTypeId = null, onSaved = { navController.navigateUp() })
                }

                composable(
                    route = "packaging_edit/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: return@composable
                    PackagingTypeFormScreen(packagingTypeId = id, onSaved = { navController.navigateUp() })
                }
            }
        }
    }
}

private fun NavHostController.navigateConfigSub(route: String) {
    navigate(route) {
        popUpTo("main") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun ScrollableConfigMainContent(
    scrollState: ScrollState,
    onCustomerManagementClick: () -> Unit,
    onProductConfigClick: () -> Unit,
    onLocationConfigClick: () -> Unit,
    onOperatorConfigClick: () -> Unit,
    onPackagingConfigClick: () -> Unit,
    onCompanyInfoClick: () -> Unit,
    onBuyerListClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    // 同步状态变量
    var isSyncing by remember { mutableStateOf(false) }
    var isFullPulling by remember { mutableStateOf(false) }
    var showFullPullDialog by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableStateOf(0) }
    var syncTotal by remember { mutableStateOf(0) }
    var syncType by remember { mutableStateOf("") }
    var syncResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var unsyncedCounts by remember { mutableStateOf<Map<String, Int>?>(null) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cleanupPreview by remember { mutableStateOf<AppCacheCleaner.CleanupPreview?>(null) }

    suspend fun refreshUnsyncedCounts() {
        unsyncedCounts = LengKuBaoApplication.getSyncManager().checkUnsyncedConfigs()
    }

    // 加载未同步数量，并在配置变更/页面恢复时实时刷新
    LaunchedEffect(Unit) {
        try {
            refreshUnsyncedCounts()
        } catch (e: Exception) {
            Log.e("Config", "检查未同步失败", e)
        }
        ConfigSyncStatusNotifier.refreshRequests.collect {
            try {
                refreshUnsyncedCounts()
            } catch (e: Exception) {
                Log.e("Config", "刷新未同步数量失败", e)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    try {
                        refreshUnsyncedCounts()
                    } catch (e: Exception) {
                        Log.e("Config", "恢复时检查未同步失败", e)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // 标题
        Text(
            text = "系统配置",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        )

        // 年份管理
        FiscalYearManagementCard(
            appContext = appContext,
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutineScope,
            onYearChanged = {
                (context as? ComponentActivity)?.finish()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                    val syncTypeLabels = listOf(
                        listOf("库位", "经手人", "客户"),
                        listOf("商品型号", "包装类型"),
                    )
                    syncTypeLabels.forEachIndexed { index, rowTypes ->
                        ConfigSyncCountRow(counts = counts, types = rowTypes)
                        if (index < syncTypeLabels.lastIndex) {
                            Spacer(modifier = Modifier.height(6.dp))
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
                if ((isSyncing || isFullPulling) && syncTotal > 0) {
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
                                    refreshUnsyncedCounts()
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
                        enabled = !isSyncing && !isFullPulling
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
                                if (!LengKuBaoApplication.getSyncManager().isConnected()) {
                                    syncResult = Pair(false, "未连接服务器，请先连接")
                                    isSyncing = false
                                    snackbarHostState.showSnackbar("未连接服务器，请先连接")
                                    return@launch
                                }

                                // 显示开始同步提示
                                snackbarHostState.showSnackbar("开始双向增量同步...")

                                LengKuBaoApplication.getSyncManager().bidirectionalConfigSync(
                                    onResult = { success, message ->
                                        syncResult = Pair(success, message)
                                        isSyncing = false

                                        coroutineScope.launch {
                                            refreshUnsyncedCounts()
                                        }

                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(message)
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSyncing && !isFullPulling &&
                            LengKuBaoApplication.getSyncManager().isConnected()
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
                        Text(if (isSyncing) "同步中..." else "双向增量同步")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showFullPullDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing && !isFullPulling &&
                        LengKuBaoApplication.getSyncManager().isConnected()
                ) {
                    if (isFullPulling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isFullPulling) "全量拉取中..." else "从电脑全量拉取")
                }

                if (showFullPullDialog) {
                    AlertDialog(
                        onDismissRequest = { showFullPullDialog = false },
                        title = { Text("从电脑全量拉取") },
                        text = {
                            Text(
                                "将以电脑端数据为准，全量覆盖本地基础配置（客户/库位/经手人/型号/包装等）。" +
                                    "本地未上传的改动会先尝试上传。删除/禁用以电脑为准。是否继续？",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showFullPullDialog = false
                                    coroutineScope.launch {
                                        isFullPulling = true
                                        syncResult = null
                                        syncProgress = 0
                                        syncTotal = 3
                                        syncType = "全量拉取"

                                        if (!LengKuBaoApplication.getSyncManager().isConnected()) {
                                            syncResult = Pair(false, "未连接服务器，请先连接")
                                            isFullPulling = false
                                            snackbarHostState.showSnackbar("未连接服务器，请先连接")
                                            return@launch
                                        }

                                        LengKuBaoApplication.getSyncManager().pullFullConfigFromPc(
                                            onProgress = { current, total, type ->
                                                syncProgress = current
                                                syncTotal = total
                                                syncType = type
                                            },
                                            onResult = { success, message ->
                                                syncResult = Pair(success, message)
                                                isFullPulling = false
                                                coroutineScope.launch {
                                                    refreshUnsyncedCounts()
                                                    snackbarHostState.showSnackbar(message)
                                                }
                                            },
                                        )
                                    }
                                },
                            ) {
                                Text("继续")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showFullPullDialog = false }) {
                                Text("取消")
                            }
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 配置项卡片
        ConfigCard(
            title = "客户管理",
            description = "管理客户信息，新增、编辑和查看客户",
            icon = Icons.Filled.People,
            onClick = onCustomerManagementClick
        )

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
            title = "企业信息",
            description = "设置公司名称、地址、电话",
            icon = Icons.Filled.Business,
            onClick = onCompanyInfoClick
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
            text = "提示：日常同步用「双向增量同步」；数据不一致或重装恢复时用「从电脑全量拉取」",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiscalYearManagementCard(
    appContext: android.content.Context,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    onYearChanged: () -> Unit,
) {
    var availableYears by remember { mutableStateOf(FiscalYearManager.listAvailableYears(appContext)) }
    var selectedYear by remember { mutableIntStateOf(FiscalYearManager.activeYear) }
    var expanded by remember { mutableStateOf(false) }
    var showSwitchConfirm by remember { mutableStateOf<Int?>(null) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var showNewYearDialog by remember { mutableStateOf(false) }
    var newYearInput by remember {
        mutableStateOf(
            (availableYears.maxOrNull()?.plus(1) ?: Calendar.getInstance().get(Calendar.YEAR)).toString()
        )
    }
    var isBusy by remember { mutableStateOf(false) }

    val activeDbName = FiscalYearManager.getActiveDbName()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "年份管理",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "当前库：$activeDbName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!isBusy) expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedYear.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("活跃年份") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = !isBusy
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableYears.forEach { year ->
                        DropdownMenuItem(
                            text = { Text("$year 年") },
                            onClick = {
                                expanded = false
                                if (year != FiscalYearManager.activeYear) {
                                    showSwitchConfirm = year
                                } else {
                                    selectedYear = year
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showSaveConfirm = true },
                    modifier = Modifier.weight(1f),
                    enabled = !isBusy
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("保存年份")
                }
                Button(
                    onClick = {
                        newYearInput = (availableYears.maxOrNull()?.plus(1)
                            ?: Calendar.getInstance().get(Calendar.YEAR)).toString()
                        showNewYearDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isBusy
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新建年份")
                }
            }
        }
    }

    showSwitchConfirm?.let { targetYear ->
        AlertDialog(
            onDismissRequest = { showSwitchConfirm = null },
            title = { Text("切换年份") },
            text = {
                Text("切换到 $targetYear 年？切换后当前页面将关闭，请返回主页查看该年数据。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSwitchConfirm = null
                        isBusy = true
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                YearSwitchCoordinator.switchToYear(appContext, targetYear)
                            }
                            isBusy = false
                            if (result.isSuccess) {
                                selectedYear = targetYear
                                availableYears = FiscalYearManager.listAvailableYears(appContext)
                                snackbarHostState.showSnackbar("已切换到 $targetYear 年")
                                onYearChanged()
                            } else {
                                snackbarHostState.showSnackbar("切换失败：${result.exceptionOrNull()?.message}")
                            }
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchConfirm = null }) { Text("取消") }
            }
        )
    }

    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("保存年份") },
            text = {
                Text("将当前 ${FiscalYearManager.activeYear} 年数据保存到 ${FiscalYearManager.getActiveDbName()}？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveConfirm = false
                        isBusy = true
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                YearSwitchCoordinator.saveCurrentYear(appContext)
                            }
                            isBusy = false
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("年份数据已保存")
                            } else {
                                snackbarHostState.showSnackbar("保存失败：${result.exceptionOrNull()?.message}")
                            }
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showNewYearDialog) {
        AlertDialog(
            onDismissRequest = { showNewYearDialog = false },
            title = { Text("新建年份") },
            text = {
                Column {
                    Text("从当前年复制基础资料（客户/型号/库位/经手人/包装），单据与库存为空。")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newYearInput,
                        onValueChange = { newYearInput = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("年份 (2000–2100)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val year = newYearInput.toIntOrNull()
                        if (year == null || year !in 2000..2100) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("请输入 2000–2100 之间的有效年份")
                            }
                            return@TextButton
                        }
                        showNewYearDialog = false
                        isBusy = true
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                YearSwitchCoordinator.createNewYear(appContext, year)
                            }
                            isBusy = false
                            if (result.isSuccess) {
                                selectedYear = year
                                availableYears = FiscalYearManager.listAvailableYears(appContext)
                                snackbarHostState.showSnackbar("已新建并切换到 $year 年")
                                onYearChanged()
                            } else {
                                snackbarHostState.showSnackbar("新建失败：${result.exceptionOrNull()?.message}")
                            }
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showNewYearDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ConfigSyncCountRow(counts: Map<String, Int>, types: List<String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        types.forEach { type ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                ConfigSyncCountItem(
                    label = type,
                    count = counts[type] ?: 0,
                )
            }
        }
    }
}

@Composable
private fun ConfigSyncCountItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = if (count > 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
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



package com.pingwei.lengkubao.ui.query.packaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.service.TcpSyncService
import com.pingwei.lengkubao.ui.common.ConfigManager
import com.pingwei.lengkubao.ui.query.QueryTimeRangeUtils
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.query.components.BillListItem
import com.pingwei.lengkubao.ui.query.components.QueryFilterBar
import com.pingwei.lengkubao.ui.query.packaging.viewmodel.PackagingQueryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagingQueryScreen(
    onBack: () -> Unit,
    viewModel: PackagingQueryViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configManager = remember { ConfigManager(context) }

    // 搜索状态
    var searchText by remember { mutableStateOf("") }
    var timeRangeLabel by remember { mutableStateOf(viewModel.savedTimeRangeLabel) }

    // 收集数据
    val bills by viewModel.bills.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    fun launchUiTask(taskName: String, block: suspend () -> Unit) {
        runCatching {
            coroutineScope.launch { block() }
        }.onFailure { e ->
            val isDisposedScope = e.message?.contains("rememberCoroutineScope left the composition") == true
            if (isDisposedScope) {
                Log.w("PackagingQueryScreen", "页面已销毁，忽略任务：$taskName")
            } else {
                Log.e("PackagingQueryScreen", "$taskName 失败", e)
            }
        }
    }

    // 新增：同步广播接收器
    var syncBroadcastReceiver by remember { mutableStateOf<BroadcastReceiver?>(null) }

    // 监听同步完成广播
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    TcpSyncService.ACTION_SYNC_COMPLETE -> {
                        val isSuccess = intent.getBooleanExtra(TcpSyncService.EXTRA_RESULT, false)
                        val billId = intent.getLongExtra(TcpSyncService.EXTRA_BILL_ID_BROADCAST, 0)
                        val billType = intent.getStringExtra(TcpSyncService.EXTRA_BILL_TYPE_BROADCAST)

                        if (billId > 0 && billType == "PACKAGING") {
                            Log.d("PackagingQueryScreen", "🔄 接收到包装单同步完成广播，billId=$billId, success=$isSuccess")

                            // 刷新数据
                            launchUiTask("同步完成后刷新包装单列表") {
                                // 延迟一小段时间确保数据库已更新
                                delay(300)
                                viewModel.refreshData()

                                // 显示同步结果提示
                                val message = if (isSuccess) "同步成功" else "同步失败"
                                Toast.makeText(context, "包装单($billId) $message", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        // 注册广播接收器
        val filter = IntentFilter(TcpSyncService.ACTION_SYNC_COMPLETE)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        syncBroadcastReceiver = receiver

        onDispose {
            syncBroadcastReceiver?.let {
                try {
                    context.unregisterReceiver(it)
                } catch (e: IllegalArgumentException) {
                    // 忽略已经注销的情况
                }
            }
        }
    }

    // 新增：Activity结果监听 - 监听详情页返回事件
    val detailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 检查详情页是否返回了需要刷新的标志
        val refreshNeeded = result.data?.getBooleanExtra("REFRESH_NEEDED", false) ?: false
        if (refreshNeeded) {
            Log.d("PackagingQueryScreen", "🔄 接收到刷新信号，刷新列表数据")
            viewModel.refreshData()
        } else if (result.resultCode == android.app.Activity.RESULT_OK) {
            // 即使没有明确标志，只要成功返回就刷新
            Log.d("PackagingQueryScreen", "🔍 详情页返回，刷新列表")
            viewModel.refreshData()
        }
    }

    // 在Composable初始加载时获取数据
    LaunchedEffect(Unit) {
        Log.d("PackagingQueryScreen", "🔍 初始加载包装单数据")
        viewModel.refreshData()
    }

    // 处理时间筛选点击
    val onTimeRangeSelected = { label: String ->
        timeRangeLabel = label
        configManager.saveQueryTimeRangeLabel(
            ConfigManager.QueryTimeRangeType.PACKAGING,
            label
        )
        launchUiTask("时间筛选") {
            viewModel.search(
                keyword = searchText.takeIf { it.isNotBlank() },
                startTime = QueryTimeRangeUtils.getStartTime(label),
                endTime = QueryTimeRangeUtils.getEndTime(label)
            )
        }
    }

    // 修改：更新导航到详情页的函数
    val navigateToDetail = { billId: Long ->
        val intent = Intent(context, PackagingDetailActivity::class.java).apply {
            putExtra("BILL_ID", billId)
            putExtra("NEED_REFRESH", true) // 确保设置刷新标志
        }
        detailLauncher.launch(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("包装单查询") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 新增：手动同步未同步单据按钮
                    IconButton(
                        onClick = {
                            TcpSyncService.syncPendingNow(context)
                            Toast.makeText(context, "已提交后台同步请求", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "同步未完成单据",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 新增：手动刷新按钮
                    IconButton(
                        onClick = {
                            launchUiTask("手动刷新包装单列表") {
                                viewModel.refreshData()
                            }
                        },
                        enabled = !isLoading // 加载中时禁用刷新
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = if (isLoading) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 查询筛选栏
            QueryFilterBar(
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                onSearch = {
                    launchUiTask("条件搜索") {
                        viewModel.search(
                            keyword = searchText.takeIf { it.isNotBlank() },
                            startTime = QueryTimeRangeUtils.getStartTime(timeRangeLabel),
                            endTime = QueryTimeRangeUtils.getEndTime(timeRangeLabel)
                        )
                    }
                },
                timeRangeLabel = timeRangeLabel,
                onTimeRangeClick = {
                    // TODO: 打开日期选择对话框
                    // 暂时先处理自定义选择
                    onTimeRangeSelected("自定义")
                },
                onFilterChipClick = { label ->
                    onTimeRangeSelected(label)
                },
                modifier = Modifier.padding(AppDimens.itemSpacing)
            )

            // 列表
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.itemSpacing)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (bills.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = "无数据",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无包装单数据",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                    ) {
                        items(bills) { bill ->
                            BillListItem(
                                billNo = bill.billNo,
                                customerName = bill.customerName ?: "",
                                createTime = bill.createTime,
                                totalAmount = bill.totalAmount,
                                status = if (bill.isVoided) "2" else "1",
                                packagingType = "包装单",
                                syncStatus = if (bill.isSynced) 1 else 0, // 新增：同步状态
                                onClick = {
                                    navigateToDetail(bill.id) // 使用新的导航函数
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
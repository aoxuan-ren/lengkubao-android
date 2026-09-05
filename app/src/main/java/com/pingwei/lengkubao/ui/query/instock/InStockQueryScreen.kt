package com.pingwei.lengkubao.ui.query.instock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.pingwei.lengkubao.ui.query.instock.viewmodel.InStockQueryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InStockQueryScreen(
    onBack: () -> Unit,
    viewModel: InStockQueryViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configManager = remember { ConfigManager(context) }
    fun launchUiTask(taskName: String, block: suspend () -> Unit) {
        runCatching {
            coroutineScope.launch { block() }
        }.onFailure { e ->
            val isDisposedScope = e.message?.contains("rememberCoroutineScope left the composition") == true
            if (isDisposedScope) {
                Log.w("InStockQueryScreen", "页面已销毁，忽略任务：$taskName")
            } else {
                Log.e("InStockQueryScreen", "$taskName 失败", e)
            }
        }
    }

    // 搜索状态
    var searchText by remember { mutableStateOf("") }
    var timeRangeLabel by remember { mutableStateOf(viewModel.savedTimeRangeLabel) }

    // 收集ViewModel数据状态
    val bills by viewModel.bills.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // ==================== 新增：同步完成广播监听 ====================
    DisposableEffect(Unit) {
        // 定义广播接收器，监听同步服务发送的完成广播
        val syncReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (context == null || intent == null) return
                when (intent.action) {
                    TcpSyncService.ACTION_SYNC_COMPLETE -> {
                        // 解析广播携带的参数
                        val syncSuccess = intent.getBooleanExtra(TcpSyncService.EXTRA_RESULT, false)
                        val billId = intent.getLongExtra(TcpSyncService.EXTRA_BILL_ID_BROADCAST, 0)
                        val billType = intent.getStringExtra(TcpSyncService.EXTRA_BILL_TYPE_BROADCAST)

                        // 仅处理入库单类型的同步结果
                        if (billId > 0 && billType == "IN_STOCK") {
                            Log.d("InStockQueryScreen", "🔄 接收到入库单同步完成广播，billId=$billId, 同步结果=$syncSuccess")
                            launchUiTask("同步完成后刷新入库单列表") {
                                // 同步完成后刷新列表数据
                                viewModel.refreshData()
                                // 弹出结果提示
                                val toastMsg = if (syncSuccess) "入库单($billId) 同步成功" else "入库单($billId) 同步失败"
                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        // 注册广播过滤器，仅监听同步完成事件
        val intentFilter = IntentFilter(TcpSyncService.ACTION_SYNC_COMPLETE)
        ContextCompat.registerReceiver(
            context,
            syncReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 页面销毁时注销广播，避免内存泄漏
        onDispose {
            try {
                context.unregisterReceiver(syncReceiver)
                Log.d("InStockQueryScreen", "✅ 同步广播接收器已注销")
            } catch (e: IllegalArgumentException) {
                Log.w("InStockQueryScreen", "⚠️ 广播接收器重复注销或已注销", e)
            }
        }
    }

    // 创建ActivityResultLauncher处理详情页返回刷新
    val detailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val refreshNeeded = result.data?.getBooleanExtra("REFRESH_NEEDED", false) ?: false
        if (refreshNeeded || result.resultCode == android.app.Activity.RESULT_OK) {
            Log.d("InStockQueryScreen", "🔄 详情页返回，触发列表刷新")
            viewModel.refreshData()
        }
    }

    // 页面初始加载时刷新数据
    LaunchedEffect(Unit) {
        Log.d("InStockQueryScreen", "🔍 初始加载入库单数据")
        viewModel.refreshData()
    }

    // 跳转入库单详情页
    val navigateToDetail = { billId: Long ->
        val intent = Intent(context, InStockDetailActivity::class.java).apply {
            putExtra("BILL_ID", billId)
            putExtra("NEED_REFRESH", true)
        }
        detailLauncher.launch(intent)
    }

    // 页面主体布局
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("入库单查询") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 手动同步未同步单据按钮
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

                    // 手动刷新列表按钮
                    IconButton(
                        onClick = {
                            launchUiTask("手动刷新入库单列表") {
                                viewModel.refreshData()
                            }
                        },
                        enabled = !isLoading
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
            // 搜索筛选栏
            QueryFilterBar(
                searchText = searchText,
                onSearchTextChange = {
                    searchText = it
                    viewModel.updateKeyword(it)
                },
                onSearch = {
                    launchUiTask("条件搜索") {
                        val startTime = QueryTimeRangeUtils.getStartTime(timeRangeLabel)
                        val endTime = QueryTimeRangeUtils.getEndTime(timeRangeLabel)
                        viewModel.search(
                            keyword = searchText.takeIf { it.isNotBlank() },
                            startTime = startTime,
                            endTime = endTime
                        )
                    }
                },
                timeRangeLabel = timeRangeLabel,
                onTimeRangeClick = { /* 可扩展自定义时间选择逻辑 */ },
                onFilterChipClick = { label ->
                    timeRangeLabel = label
                    configManager.saveQueryTimeRangeLabel(
                        ConfigManager.QueryTimeRangeType.IN_STOCK,
                        label
                    )
                    launchUiTask("时间筛选") {
                        viewModel.search(
                            keyword = searchText.takeIf { it.isNotBlank() },
                            startTime = QueryTimeRangeUtils.getStartTime(label),
                            endTime = QueryTimeRangeUtils.getEndTime(label)
                        )
                    }
                },
                modifier = Modifier.padding(AppDimens.itemSpacing)
            )

            // 数据列表/空状态/加载状态容器
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.itemSpacing)
            ) {
                // 加载中状态
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                // 无数据状态
                else if (bills.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Receipt,
                                contentDescription = "无数据",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无入库单数据",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.refreshData() }
                            ) {
                                Text("点击刷新")
                            }
                        }
                    }
                }
                // 数据列表展示
                else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                    ) {
                        items(bills) { bill ->
                            // 新增：传递syncStatus同步状态到列表项组件
                            BillListItem(
                                billNo = bill.billNo,
                                customerName = bill.customerName ?: "",
                                createTime = bill.createTime,
                                totalAmount = bill.totalAmount,
                                status = bill.status,
                                locationName = bill.locationName,
                                syncStatus = bill.syncStatus,
                                onClick = { navigateToDetail(bill.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
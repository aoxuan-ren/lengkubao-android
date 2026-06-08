// ui/ConfigCheckActivity.kt (修改版)
package com.pingwei.lengkubao.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.ui.config.ConfigMainActivity
import com.pingwei.lengkubao.ui.instock.InStockActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConfigCheckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ConfigCheckScreen()
        }
    }
}

@Composable
fun ConfigCheckScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var productCount by remember { mutableIntStateOf(0) }
    var locationCount by remember { mutableIntStateOf(0) }
    var operatorCount by remember { mutableIntStateOf(0) }
    var enabledProductCount by remember { mutableIntStateOf(0) }

    // 加载数据 - 使用正确的方法名
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)

            // 1. 商品总数 - 使用ProductDao.getAll()
            productCount = db.productDao().getAll().size

            // 2. 启用的商品数 - 从所有商品中过滤enabled=true的
            val allProducts = db.productDao().getAll()
            enabledProductCount = allProducts.count { it.enabled }

            // 3. 库位数 - 使用LocationDao.getAllSimple()（非Flow版本）
            locationCount = db.locationDao().getAllSimple().size

            // 4. 经手人数 - 需要添加一个非Flow版本的getAll()方法到OperatorDao
            // 先临时使用Flow版本（需要collect）
            db.operatorDao().getAllOperators().collect { operators ->
                operatorCount = operators.size
            }
        }
    }

    val allConfigured = productCount > 0 && locationCount > 0 && operatorCount > 0
    val hasEnabledProducts = enabledProductCount > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标
        Icon(
            imageVector = Icons.Default.Checklist,
            contentDescription = "配置检查",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "开始入库开单前，请确认",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 配置检查项
        ConfigCheckItem(
            label = "商品型号配置",
            status = productCount > 0,
            detail = if (productCount > 0) "已配置 $productCount 个商品" else "未配置",
            onConfigure = { context.startActivity(Intent(context, ConfigMainActivity::class.java)) }
        )

        ConfigCheckItem(
            label = "商品启用状态",
            status = hasEnabledProducts,
            detail = if (hasEnabledProducts) "$enabledProductCount 个商品已启用" else "没有启用的商品",
            onConfigure = { context.startActivity(Intent(context, ConfigMainActivity::class.java)) }
        )

        ConfigCheckItem(
            label = "库位配置",
            status = locationCount > 0,
            detail = if (locationCount > 0) "已配置 $locationCount 个库位" else "未配置",
            onConfigure = { context.startActivity(Intent(context, ConfigMainActivity::class.java)) }
        )

        ConfigCheckItem(
            label = "经手人配置",
            status = operatorCount > 0,
            detail = if (operatorCount > 0) "已配置 $operatorCount 个经手人" else "未配置",
            onConfigure = { context.startActivity(Intent(context, ConfigMainActivity::class.java)) }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 开始入库按钮
        Button(
            onClick = {
                if (allConfigured && hasEnabledProducts) {
                    context.startActivity(Intent(context, InStockActivity::class.java))
                    (context as? ConfigCheckActivity)?.finish()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = allConfigured && hasEnabledProducts
        ) {
            Text("开始入库开单")
        }

        // 或去配置
        if (!allConfigured || !hasEnabledProducts) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = {
                    context.startActivity(Intent(context, ConfigMainActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("前往基础配置")
            }
        }
    }
}

@Composable
fun ConfigCheckItem(
    label: String,
    status: Boolean,
    detail: String,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (status) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (status) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "状态",
                    tint = if (status) Color.Green else Color(0xFFFF9800)
                )
                if (!status) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfigure) {
                        Text("配置")
                    }
                }
            }
        }
    }
}
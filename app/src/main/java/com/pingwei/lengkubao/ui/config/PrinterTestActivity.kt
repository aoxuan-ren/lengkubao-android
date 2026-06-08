// 在 com.pingwei.lengkubao.ui.config 包中创建 PrinterTestActivity.kt

package com.pingwei.lengkubao.ui.config

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinter
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinterFactory
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

class PrinterTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                PrinterTestScreen()
            }
        }
    }
}

@Composable
fun PrinterTestScreen() {
    val context = LocalContext.current
    val printerFactory = SaleBillPrinterFactory(context)
    val printerViewModel: SaleBillPrinter = viewModel(factory = printerFactory)
    val printStatus by printerViewModel.printStatus.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("打印机测试工具", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // 状态显示
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("当前状态: ${printStatus.javaClass.simpleName}")

                when (printStatus) {
                    is com.pingwei.lengkubao.ui.common.printer.PrintStatus.Error -> {
                        val error = printStatus as com.pingwei.lengkubao.ui.common.printer.PrintStatus.Error
                        Text("错误信息: ${error.message}")
                    }
                    else -> {}
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 测试按钮
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { printerViewModel.initializePrinter() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("1. 初始化打印机")
            }

            Button(
                onClick = { printerViewModel.testPrint() },
                modifier = Modifier.fillMaxWidth(),
                enabled = printStatus is com.pingwei.lengkubao.ui.common.printer.PrintStatus.Ready
            ) {
                Text("2. 测试打印")
            }

            Button(
                onClick = {
                    // 模拟一个简单的销售单打印
                    val testItems = listOf(
                        com.pingwei.lengkubao.service.model.SaleItemPrint(
                            productName = "测试商品",
                            quantity = 1,
                            unitPrice = 1.0,
                            amount = 1.0
                        )
                    )

                },
                modifier = Modifier.fillMaxWidth(),
                enabled = printStatus is com.pingwei.lengkubao.ui.common.printer.PrintStatus.Ready
            ) {
                Text("3. 测试销售单打印")
            }
        }
    }
}
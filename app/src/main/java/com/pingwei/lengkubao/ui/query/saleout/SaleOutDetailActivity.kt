// ui/query/saleout/SaleOutDetailActivity.kt
package com.pingwei.lengkubao.ui.query.saleout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

class SaleOutDetailActivity : ComponentActivity() {
    companion object {
        const val RESULT_REFRESH_NEEDED = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val billId = intent.getLongExtra("BILL_ID", -1L)
        if (billId == -1L) {
            finish()
            return
        }
        setContent {
            LengkubaoTheme {
                var showEditScreen by remember { mutableStateOf(false) }

                if (showEditScreen) {
                    // 编辑页面
                    Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Text("报账单编辑功能开发中")
                        androidx.compose.material3.Button(
                            onClick = { showEditScreen = false },
                            modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                        ) {
                            androidx.compose.material3.Text("返回")
                        }
                    }
                } else {
                    SaleOutDetailScreen(
                        billId = billId,
                        onBack = {
                            setResult(RESULT_OK)
                            finish()
                        },
                        onEdit = { showEditScreen = true },
                        onDeleteSuccess = {
                            // 删除成功时设置需要刷新的标志
                            val resultIntent = Intent().apply {
                                putExtra("REFRESH_NEEDED", true)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish() // 删除成功后自动关闭详情页
                        }
                    )
                }
            }
        }
    }

    override fun onBackPressed() {
        // 覆盖返回键，确保能设置结果
        setResult(RESULT_OK)
        super.onBackPressed()
    }
}
// ui/query/instock/InStockDetailActivity.kt
package com.pingwei.lengkubao.ui.query.instock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

class InStockDetailActivity : ComponentActivity() {
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
                    // TODO: 编辑页面
                } else {
                    InStockDetailScreen(
                        billId = billId,
                        onBack = {
                            // 返回时可以设置结果
                            setResult(RESULT_OK)
                            finish()
                        },
                        onEdit = {
                            // TODO: 跳转到编辑页面
                            showEditScreen = true
                        },
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
//com.pingwei.lengkubao.ui.query.packaging.PackagingDetailActivity
package com.pingwei.lengkubao.ui.query.packaging

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

class PackagingDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val billId = intent.getLongExtra("BILL_ID", -1L)

        if (billId == -1L) {
            finish()
            return
        }

        setContent {
            LengkubaoTheme {
                // 直接调用提取后的 Composable 主函数，保证上下文合规
                PackagingDetailContainer(
                    billId = billId,
                    onBack = { finish() },
                    // 新增：删除成功回调，用于设置返回结果并关闭页面
                    onDeleteSuccess = {
                        // 设置返回结果，通知查询页面需要刷新
                        val resultIntent = Intent().apply {
                            putExtra("REFRESH_NEEDED", true)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish() // 关闭详情页
                    }
                )
            }
        }
    }
}

/**
 * 提取独立的 @Composable 函数，包裹所有 UI 逻辑，解决上下文报错
 */
@Composable
private fun PackagingDetailContainer(
    billId: Long,
    onBack: () -> Unit,
    onDeleteSuccess: () -> Unit // 新增：删除成功回调参数
) {
    var showEditScreen by remember { mutableStateOf(false) }

    if (showEditScreen) {
        // TODO: 包装单编辑页面
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("包装单编辑功能开发中")
            Button(
                onClick = { showEditScreen = false },
                modifier = Modifier.align(Alignment.BottomCenter) // 补充对齐，优化布局（可选）
            ) {
                Text("返回")
            }
        }
    } else {
        PackagingDetailScreen(
            billId = billId,
            onBack = onBack,
            onEdit = {
                // 跳转到编辑页面
                showEditScreen = true
            },
            onDeleteSuccess = {
                // 调用外层传递的删除成功回调，由Activity处理返回结果
                onDeleteSuccess()
            }
        )
    }
}
// ui/query/saleout/SaleOutQueryActivity.kt
package com.pingwei.lengkubao.ui.query.saleout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

/**
 * 销售单查询Activity
 * 包装SaleOutQueryScreen，与入库/包装查询Activity结构一致
 */
class SaleOutQueryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                SaleOutQueryScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
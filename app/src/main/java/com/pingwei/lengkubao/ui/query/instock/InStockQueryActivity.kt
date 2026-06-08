// ui/query/instock/InStockQueryActivity.kt
package com.pingwei.lengkubao.ui.query.instock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

/**
 * 入库单查询Activity
 * 包装InStockQueryScreen
 */
class InStockQueryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LengkubaoTheme {
                InStockQueryScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
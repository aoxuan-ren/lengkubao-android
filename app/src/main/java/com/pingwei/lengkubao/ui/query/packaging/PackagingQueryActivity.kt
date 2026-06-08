// ui/query/packaging/PackagingQueryActivity.kt
package com.pingwei.lengkubao.ui.query.packaging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

/**
 * 包装单查询Activity
 * 包装PackagingQueryScreen
 */
class PackagingQueryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LengkubaoTheme {
                PackagingQueryScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
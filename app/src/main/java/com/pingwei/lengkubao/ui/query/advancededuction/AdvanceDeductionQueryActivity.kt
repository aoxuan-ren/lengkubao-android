package com.pingwei.lengkubao.ui.query.advancededuction

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

class AdvanceDeductionQueryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                AdvanceDeductionQueryScreen(onBack = { finish() })
            }
        }
    }
}

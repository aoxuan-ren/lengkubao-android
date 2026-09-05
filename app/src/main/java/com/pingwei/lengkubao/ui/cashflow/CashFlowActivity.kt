package com.pingwei.lengkubao.ui.cashflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.ui.cashflow.viewmodel.CashFlowViewModel
import com.pingwei.lengkubao.ui.cashflow.viewmodel.CashFlowViewModelFactory
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

class CashFlowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: CashFlowViewModel = viewModel(
                        factory = CashFlowViewModelFactory(application)
                    )
                    CashFlowScreen(viewModel)
                }
            }
        }
    }
}

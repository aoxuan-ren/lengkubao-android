package com.pingwei.lengkubao.ui.advancededuction

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.ui.advancededuction.viewmodel.AdvanceDeductionViewModel
import com.pingwei.lengkubao.ui.advancededuction.viewmodel.AdvanceDeductionViewModelFactory
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

class AdvanceDeductionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 使用工厂方法创建ViewModel
                    val viewModel: AdvanceDeductionViewModel = viewModel(
                        factory = AdvanceDeductionViewModelFactory(application)
                    )
                    AdvanceDeductionScreen(viewModel)
                }
            }
        }
    }
}
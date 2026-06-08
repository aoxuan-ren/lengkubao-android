//com.pingwei.lengkubao.ui.config.PrintConfigActivity
package com.pingwei.lengkubao.ui.config

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

class PrintConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrintConfigScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
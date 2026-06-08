// File: utils/FlowExtensions.kt
package com.pingwei.lengkubao.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> {
    return collectAsStateWithLifecycle()
}

// 在 Activity/Fragment 中监听状态
fun <T> LifecycleOwner.collectFlow(flow: Flow<T>, action: (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect(action)
        }
    }
}
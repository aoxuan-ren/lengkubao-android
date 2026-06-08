package com.pingwei.lengkubao.ui.common.printer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SaleBillPrinterFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SaleBillPrinter::class.java)) {
            return SaleBillPrinter(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
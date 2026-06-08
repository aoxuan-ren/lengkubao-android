package com.pingwei.lengkubao.ui.advancededuction.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AdvanceDeductionViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdvanceDeductionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AdvanceDeductionViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
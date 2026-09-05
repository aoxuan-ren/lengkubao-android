package com.pingwei.lengkubao.ui.config

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.dao.OperatorDao
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.utils.ConfigDeleteResult
import com.pingwei.lengkubao.utils.ConfigDeleteService
import com.pingwei.lengkubao.utils.SyncTrigger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OperatorConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val operatorDao: OperatorDao
    private val appContext = application.applicationContext
    private val _operators = MutableStateFlow<List<Operator>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val operators: StateFlow<List<Operator>> = _operators.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        operatorDao = AppDatabase.getInstance(application).operatorDao()
        loadOperators()
    }

    private fun loadOperators() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                operatorDao.getAllOperators()
                    .collect { operators ->
                        _operators.value = operators
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    suspend fun countOperatorBillRefs(operatorId: Long): Int {
        return ConfigDeleteService.countOperatorBillRefs(appContext, operatorId)
    }

    suspend fun addOperator(
        name: String,
        phone: String = "",
        role: String = "操作员",
        remark: String = "",
    ): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return false
        if (operatorDao.countByName(trimmedName) > 0) return false

        val operator = Operator(
            name = trimmedName,
            phone = phone,
            role = role,
            remark = remark,
            enabled = true,
        )

        val id = operatorDao.insert(operator)
        SyncTrigger.triggerOperatorSync(appContext, id)
        return true
    }

    suspend fun updateOperator(operator: Operator) {
        operatorDao.update(operator.copy(syncStatus = 0))
        SyncTrigger.triggerOperatorSync(appContext, operator.id)
    }

    suspend fun deleteOperator(operator: Operator): ConfigDeleteResult {
        val result = ConfigDeleteService.deleteOperator(appContext, operator)
        when (result) {
            is ConfigDeleteResult.PhysicallyDeleted ->
                _operators.value = _operators.value.filter { it.id != operator.id }
            is ConfigDeleteResult.DisabledDueToReferences ->
                _operators.value = _operators.value.map {
                    if (it.id == operator.id) it.copy(enabled = false) else it
                }
            else -> {}
        }
        return result
    }

    suspend fun toggleOperatorEnabled(operator: Operator) {
        operatorDao.updateEnabledStatus(operator.id, !operator.enabled)
        SyncTrigger.triggerOperatorSync(appContext, operator.id)
    }
}

class OperatorConfigViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OperatorConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OperatorConfigViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

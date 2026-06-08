// ui/config/OperatorConfigViewModel.kt
package com.pingwei.lengkubao.ui.config

import android.app.Application
import androidx.lifecycle.*
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.dao.OperatorDao
import com.pingwei.lengkubao.data.db.entity.Operator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OperatorConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val operatorDao: OperatorDao
    private val _searchText = MutableStateFlow("")
    private val _operators = MutableStateFlow<List<Operator>>(emptyList())
    private val _searchResults = MutableStateFlow<List<Operator>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val searchText: StateFlow<String> = _searchText.asStateFlow()
    val operators: StateFlow<List<Operator>> = _operators.asStateFlow()
    val searchResults: StateFlow<List<Operator>> = _searchResults.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        operatorDao = AppDatabase.getInstance(application).operatorDao()
        loadOperators()
        setupSearch()
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

    private fun setupSearch() {
        viewModelScope.launch {
            _searchText
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        _searchResults.value = emptyList()
                    } else {
                        operatorDao.searchOperators(query)
                            .collect { results ->
                                _searchResults.value = results
                            }
                    }
                }
        }
    }

    fun searchOperators(query: String) {
        _searchText.value = query
    }

    fun clearSearch() {
        _searchText.value = ""
    }

    suspend fun addOperator(
        operatorNo: String,
        name: String,
        phone: String = "",
        role: String = "操作员",
        remark: String = ""
    ): Boolean {
        // 检查编号是否已存在
        val exists = operatorDao.countByOperatorNo(operatorNo) > 0
        if (exists) {
            return false
        }

        val operator = Operator(
            operatorNo = operatorNo,
            name = name,
            phone = phone,
            role = role,
            remark = remark,
            enabled = true
        )

        operatorDao.insert(operator)
        return true
    }

    suspend fun updateOperator(operator: Operator) {
        operatorDao.update(operator)
    }

    suspend fun deleteOperator(operator: Operator) {
        operatorDao.delete(operator)
    }

    suspend fun toggleOperatorEnabled(operator: Operator) {
        operatorDao.updateEnabledStatus(operator.id, !operator.enabled)
    }


}

// ViewModel Factory
class OperatorConfigViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OperatorConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OperatorConfigViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
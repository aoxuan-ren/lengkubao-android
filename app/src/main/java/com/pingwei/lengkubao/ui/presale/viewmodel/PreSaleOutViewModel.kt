package com.pingwei.lengkubao.ui.presale.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.*
import com.pingwei.lengkubao.data.model.ProductWithStock
import com.pingwei.lengkubao.service.PreSaleService
import com.pingwei.lengkubao.service.StockService
import com.pingwei.lengkubao.ui.common.ConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PreSaleOutViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PreSaleOutViewModel"
    private val context = application.applicationContext
    private val database = AppDatabase.getInstance(context)
    private val stockService = StockService(database.stockDao(), database.stockChangeDao())
    private val preSaleService = PreSaleService(database, stockService)
    private lateinit var configManager: ConfigManager

    private val _saleMode = MutableStateFlow(PreSaleMode.PRESALE)
    val saleMode: StateFlow<String> = _saleMode.asStateFlow()

    private val _selectedBuyer = MutableStateFlow<Customer?>(null)
    val selectedBuyer: StateFlow<Customer?> = _selectedBuyer.asStateFlow()

    private val _selectedLocation = MutableStateFlow<Location?>(null)
    val selectedLocation: StateFlow<Location?> = _selectedLocation.asStateFlow()

    private val _selectedOperator = MutableStateFlow<Operator?>(null)
    val selectedOperator: StateFlow<Operator?> = _selectedOperator.asStateFlow()

    private val _items = MutableStateFlow<List<PreSaleItem>>(emptyList())
    val items: StateFlow<List<PreSaleItem>> = _items.asStateFlow()

    private val _remark = MutableStateFlow("")
    val remark: StateFlow<String> = _remark.asStateFlow()

    private val _productsWithStock = MutableStateFlow<List<ProductWithStock>>(emptyList())
    val productsWithStock: StateFlow<List<ProductWithStock>> = _productsWithStock.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    sealed class SaveResult {
        data class Success(val billId: Long, val billNo: String) : SaveResult()
        data class Error(val message: String) : SaveResult()
        object Loading : SaveResult()
    }

    val buyers = database.customerDao().getCustomersByType(CustomerType.BUYER)
    val allLocations = database.locationDao().getAllLocations()
    val allOperators = database.operatorDao().getAllOperators()

    val totalAmount: Double get() = _items.value.sumOf { it.amount }
    val totalQuantity: Int get() = _items.value.sumOf { it.quantity }

    init {
        configManager = ConfigManager(context)
        loadDefaults()
    }

    fun setSaleMode(mode: String) {
        _saleMode.value = mode
    }

    fun selectBuyer(buyer: Customer?) {
        _selectedBuyer.value = buyer
    }

    fun selectLocation(location: Location?) {
        _selectedLocation.value = location
        location?.let { loadProductsWithStock(it.id) }
    }

    fun selectOperator(operator: Operator?) {
        _selectedOperator.value = operator
    }

    fun setRemark(text: String) {
        _remark.value = text
    }

    private fun loadDefaults() {
        viewModelScope.launch {
            try {
                val locationId = configManager.defaultLocationId
                val operatorId = configManager.defaultHandlerId
                if (locationId > 0) {
                    database.locationDao().getLocationById(locationId)?.let { selectLocation(it) }
                }
                if (operatorId > 0) {
                    database.operatorDao().getOperatorById(operatorId)?.let { selectOperator(it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "加载默认值失败: ${e.message}")
            }
        }
    }

    private fun loadProductsWithStock(locationId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val stocksWithProduct = database.stockDao().getStocksWithProductByLocation(locationId)
                val allEnabled = database.productDao().getAll().filter { it.enabled }
                _productsWithStock.value = allEnabled.map { product ->
                    val stockInfo = stocksWithProduct.find { it.productId == product.id }
                    ProductWithStock(
                        product = product,
                        availableStock = stockInfo?.availableQuantity ?: 0,
                        locationId = locationId,
                        locationName = _selectedLocation.value?.locationName
                    )
                }.sortedBy { it.product.productNo }
            } catch (e: Exception) {
                _productsWithStock.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun addOrUpdateItem(product: Product, quantity: Int, salePrice: Double): Result<Unit> {
        val locationId = _selectedLocation.value?.id
            ?: return Result.failure(IllegalStateException("请先选择库位"))

        if (quantity <= 0) {
            removeItem(product.id)
            return Result.success(Unit)
        }

        val available = stockService.getAvailableStock(product.id, locationId)
        if (quantity > available) {
            return Result.failure(IllegalStateException("库存不足，可用: $available"))
        }

        val amount = quantity * salePrice
        val newItem = PreSaleItem(
            billId = 0,
            productId = product.id,
            productNo = product.productNo,
            productName = product.productName,
            quantity = quantity,
            salePrice = salePrice,
            amount = amount,
            unit = product.unit
        )
        val current = _items.value.toMutableList()
        val index = current.indexOfFirst { it.productId == product.id }
        if (index >= 0) current[index] = newItem else current.add(newItem)
        _items.value = current
        return Result.success(Unit)
    }

    fun removeItem(productId: Long) {
        _items.value = _items.value.filter { it.productId != productId }
    }

    fun saveBill(initialPayment: Double = 0.0, payMethod: String = PayMethod.CASH) {
        viewModelScope.launch {
            _saveResult.value = SaveResult.Loading
            val validation = when {
                _selectedBuyer.value == null -> "请选择买家"
                _selectedLocation.value == null -> "请选择库位"
                _selectedOperator.value == null -> "请选择经手人"
                _items.value.isEmpty() -> "请添加商品"
                else -> null
            }
            if (validation != null) {
                _saveResult.value = SaveResult.Error(validation)
                return@launch
            }
            val result = preSaleService.saveBill(
                buyer = _selectedBuyer.value!!,
                location = _selectedLocation.value!!,
                operator = _selectedOperator.value!!,
                items = _items.value,
                saleMode = _saleMode.value,
                remark = _remark.value,
                initialPayment = initialPayment,
                payMethod = payMethod
            )
            result.fold(
                onSuccess = { (billId, billNo) ->
                    _saveResult.value = SaveResult.Success(billId, billNo)
                },
                onFailure = { e ->
                    _saveResult.value = SaveResult.Error(e.message ?: "保存失败")
                }
            )
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun clearAll() {
        _selectedBuyer.value = null
        _items.value = emptyList()
        _remark.value = ""
        loadDefaults()
    }

    fun clearForContinue() {
        _items.value = emptyList()
        _remark.value = ""
        _saveResult.value = null
    }
}

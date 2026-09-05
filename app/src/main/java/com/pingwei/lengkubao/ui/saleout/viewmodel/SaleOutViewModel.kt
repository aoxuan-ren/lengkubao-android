package com.pingwei.lengkubao.ui.saleout.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.LengKuBaoApplication // 【新增】导入Application获取同步管理器
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.*
import com.pingwei.lengkubao.data.model.ProductWithStock
import com.pingwei.lengkubao.service.CustomerInboundStockBackfill
import com.pingwei.lengkubao.service.CustomerInboundStockService
import com.pingwei.lengkubao.sync.TcpSyncManager // 【新增】导入TCP同步管理器
import com.pingwei.lengkubao.ui.common.ConfigManager
import com.pingwei.lengkubao.utils.PrintUtils.generateBillNo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SaleOutViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SaleOutViewModel"

    private val context = application.applicationContext
    private val database by lazy { AppDatabase.getInstance(context) }
    private val customerInboundStockService by lazy {
        CustomerInboundStockService(
            database.customerInboundStockDao(),
            CustomerInboundStockBackfill(database)
        )
    }

    // 【新增】获取全局TCP同步管理器单例（与入库单/包装单/配置页共用一个TCP连接）
    private val tcpSyncManager by lazy {
        LengKuBaoApplication.getSyncManager()
    }

    // === 新增：默认值相关 ===
    private lateinit var configManager: ConfigManager
    // 默认值ID
    private var defaultLocationId: Long = -1L
    private var defaultHandlerId: Long = -1L
    // 在 SaleOutViewModel 类中添加缓存变量
    private var cachedDefaultLocation: Location? = null
    private var cachedDefaultOperator: Operator? = null
    // === 状态管理 ===
    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    private val _selectedLocation = MutableStateFlow<Location?>(null)
    val selectedLocation: StateFlow<Location?> = _selectedLocation.asStateFlow()

    private val _selectedOperator = MutableStateFlow<Operator?>(null)
    val selectedOperator: StateFlow<Operator?> = _selectedOperator.asStateFlow()

    private val _saleItems = MutableStateFlow<List<SaleItem>>(emptyList())
    val saleItems: StateFlow<List<SaleItem>> = _saleItems.asStateFlow()

    private val _remark = MutableStateFlow("")
    val remark: StateFlow<String> = _remark.asStateFlow()

    // === 商品和库存状态 ===
    private val _availableProducts = MutableStateFlow<List<Product>>(emptyList())
    val availableProducts: StateFlow<List<Product>> = _availableProducts.asStateFlow()

    private val _productStocks = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val productStocks: StateFlow<Map<Long, Int>> = _productStocks.asStateFlow()

    // === 带库存的商品列表 ===
    private val _productsWithStock = MutableStateFlow<List<ProductWithStock>>(emptyList())
    val productsWithStock: StateFlow<List<ProductWithStock>> = _productsWithStock.asStateFlow()

    // === 加载状态 ===
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // === 新增：保存状态 ===
    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    sealed class SaveResult {
        data class Success(val billId: Long, val billNo: String) : SaveResult()
        data class Error(val message: String) : SaveResult()
        object Loading : SaveResult()
    }

    init {
        Log.d(TAG, "SaleOutViewModel initialized")
        loadAvailableProducts()
    }

    // === 数据流 ===
    val allCustomers = database.customerDao().getCustomersByType(CustomerType.SELLER)
    val allLocations = database.locationDao().getAllLocations()
    val allOperators = database.operatorDao().getAllOperators()

    // === 计算方法 ===
    val totalAmount: StateFlow<Double>
        get() = MutableStateFlow(_saleItems.value.sumOf { it.amount }).asStateFlow()

    val totalQuantity: StateFlow<Int>
        get() = MutableStateFlow(_saleItems.value.sumOf { it.quantity }).asStateFlow()

    // === 加载启用商品 ===
    private fun loadAvailableProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val products = database.productDao().getAll()
                _availableProducts.value = products.filter { it.enabled }
                Log.d(TAG, "加载到 ${_availableProducts.value.size} 个启用商品")
            } catch (e: Exception) {
                Log.e(TAG, "加载商品失败: ${e.message}", e)
                _availableProducts.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // === 选择操作 ===
    fun selectCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
        _selectedLocation.value?.let { loadProductsWithStock(it.id) }
    }

    fun selectLocation(location: Location?) {
        _selectedLocation.value = location
        // 当选择库位时，加载该库位的商品库存
        location?.let { loadProductsWithStock(it.id) }
    }

    fun selectOperator(operator: Operator?) {
        _selectedOperator.value = operator
    }

    fun setRemark(text: String) {
        _remark.value = text
    }

    // === 加载带库存的商品 ===
    private fun loadProductsWithStock(locationId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val customer = _selectedCustomer.value
                if (customer == null) {
                    _productsWithStock.value = emptyList()
                    _productStocks.value = emptyMap()
                    return@launch
                }

                customerInboundStockService.ensureInitialized()

                val allEnabledProducts = database.productDao().getAll()
                    .filter { it.enabled }

                val productWithStockList = allEnabledProducts.map { product ->
                    val available = customerInboundStockService.getAvailable(
                        customerNo = customer.customerNo,
                        locationId = locationId,
                        productId = product.id
                    )
                    ProductWithStock(
                        product = product,
                        availableStock = available,
                        locationId = locationId,
                        locationName = _selectedLocation.value?.locationName
                    )
                }.sortedBy { it.product.productNo }

                _productsWithStock.value = productWithStockList
                Log.d(TAG, "✅ 加载可报账库存商品: ${_productsWithStock.value.size} 个")

                _productStocks.value = productWithStockList.associate {
                    it.product.id to it.availableStock
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载可报账库存失败: ${e.message}", e)
                _productsWithStock.value = emptyList()
                _productStocks.value = emptyMap()
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getRealTimeStock(productId: Long): Int {
        val customer = _selectedCustomer.value ?: return 0
        val locationId = _selectedLocation.value?.id ?: return 0
        return customerInboundStockService.getAvailable(customer.customerNo, locationId, productId)
    }

    // === 添加销售商品 ===
    suspend fun addSaleItem(product: Product, salePrice: Double, quantity: Int): kotlin.Result<Unit> {
        return try {
            Log.d(TAG, "🔄 [addSaleItem] 开始添加商品")
            Log.d(TAG, "📦 商品信息: ${product.productName} (ID: ${product.id})")
            Log.d(TAG, "💰 单价: $salePrice, 数量: $quantity")

            // 1. 检查必填项（库位）
            val locationId = _selectedLocation.value?.id
            if (locationId == null) {
                Log.e(TAG, "❌ 错误: 未选择库位")
                return kotlin.Result.failure(IllegalStateException("请先选择库位"))
            }

            // 2. 检查库存
            Log.d(TAG, "📊 检查库存...")
            val availableStock = getRealTimeStock(product.id)
            Log.d(TAG, "📊 可用库存: $availableStock, 需求数量: $quantity")

            if (availableStock < quantity) {
                Log.e(TAG, "❌ 错误: 可报账库存不足 (可用: $availableStock, 需求: $quantity)")
                return kotlin.Result.failure(IllegalStateException("可报账库存不足，可用: $availableStock"))
            }

            // 【已移除】销售单价必须大于0的校验

            // 3. 创建销售明细
            val newItem = SaleItem(
                billId = 0, // 临时值，保存单据时会更新
                productId = product.id,
                productNo = product.productNo,
                productName = product.productName,
                quantity = quantity,
                salePrice = salePrice,
                amount = quantity * salePrice,
                unit = product.unit,
                remark = ""
            )

            // 4. 添加到列表
            val currentList = _saleItems.value
            Log.d(TAG, "📋 当前销售商品列表大小: ${currentList.size}")

            val newList = currentList + newItem
            _saleItems.value = newList

            Log.d(TAG, "✅ 商品添加成功！")
            Log.d(TAG, "📋 新销售商品列表大小: ${_saleItems.value.size}")

            // 打印所有商品（调试用，方便查看完整列表）
            _saleItems.value.forEachIndexed { idx, item ->
                Log.d(TAG, "   [$idx] 商品: ${item.productName} | 数量: ${item.quantity} | 单价: ¥${item.salePrice} | 金额: ¥${item.amount}")
            }

            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 商品添加失败: ${e.message}", e) // 打印完整异常栈，方便定位问题
            kotlin.Result.failure(e)
        }
    }

    // === 更新销售商品单价 ===
    fun updateSaleItemPrice(productId: Long, newPrice: Double) {
        val currentList = _saleItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.productId == productId }

        if (index != -1) {
            val item = currentList[index]
            // 【已移除】newPrice > 0 的校验，允许任何价格（包括0）
            val updatedItem = item.copy(
                salePrice = newPrice,
                amount = item.quantity * newPrice
            )
            currentList[index] = updatedItem
            _saleItems.value = currentList
            Log.d(TAG, "✅ 更新商品单价: ${item.productName} 新单价: $newPrice")
        }
    }

    // === 添加商品（支持更新已存在的商品）===
    suspend fun addOrUpdateSaleItem(product: Product, salePrice: Double, quantity: Int): kotlin.Result<Unit> {
        return try {
            Log.d(TAG, "🔄 [addOrUpdateSaleItem] 开始处理商品")

            // 1. 检查必填项
            val locationId = _selectedLocation.value?.id
            if (locationId == null) {
                return kotlin.Result.failure(IllegalStateException("请先选择库位"))
            }

            // 2. 检查库存
            val availableStock = getRealTimeStock(product.id)
            if (availableStock < quantity) {
                return kotlin.Result.failure(IllegalStateException("可报账库存不足，可用: $availableStock"))
            }

            // 【已移除】销售单价必须大于0的校验

            // 3. 检查是否已存在该商品
            val currentList = _saleItems.value
            val existingIndex = currentList.indexOfFirst { it.productId == product.id }

            if (existingIndex != -1) {
                // 更新已存在的商品
                val existingItem = currentList[existingIndex]
                val updatedItem = existingItem.copy(
                    quantity = quantity,
                    salePrice = salePrice,
                    amount = quantity * salePrice
                )

                val newList = currentList.toMutableList()
                newList[existingIndex] = updatedItem
                _saleItems.value = newList

                Log.d(TAG, "✅ 更新商品: ${product.productName}, 数量: $quantity, 单价: $salePrice")
            } else {
                // 添加新商品
                val newItem = SaleItem(
                    billId = 0,
                    productId = product.id,
                    productNo = product.productNo,
                    productName = product.productName,
                    quantity = quantity,
                    salePrice = salePrice,
                    amount = quantity * salePrice,
                    unit = product.unit,
                    remark = ""
                )

                _saleItems.value = currentList + newItem
                Log.d(TAG, "✅ 添加新商品: ${product.productName}")
            }

            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 商品处理失败: ${e.message}", e)
            kotlin.Result.failure(e)
        }
    }

    // === 保存销售单据（核心方法 - 返回 Result<Pair<Long, String>>）===
    suspend fun saveSaleBill(): kotlin.Result<Pair<Long, String>> {
        return try {
            _saveResult.value = SaveResult.Loading

            // 1. 验证必填项
            val validationError = when {
                _selectedCustomer.value == null -> "请选择客户"
                _selectedLocation.value == null -> "请选择库位"
                _selectedOperator.value == null -> "请选择经手人"
                _saleItems.value.isEmpty() -> "请添加销售商品"
                else -> null
            }

            if (validationError != null) {
                _saveResult.value = SaveResult.Error(validationError)
                return kotlin.Result.failure(IllegalStateException(validationError))
            }

            // 2. 验证库存 - 【已移除】销售单价必须大于0的校验，只保留数量校验
            for (item in _saleItems.value) {
                if (item.quantity <= 0) {
                    _saveResult.value = SaveResult.Error("商品数量必须大于0")
                    return kotlin.Result.failure(IllegalStateException("商品数量必须大于0"))
                }
                // 【已移除】销售单价必须大于0的校验
            }

            val customer = _selectedCustomer.value!!
            val location = _selectedLocation.value!!
            val operator = _selectedOperator.value!!
            val items = _saleItems.value

            // 3. 生成单据号
            val billNo = generateBillNo("XS")

            // 4. 预留可报账库存
            Log.d(TAG, "🔒 开始预留可报账库存...")
            val reserveResult = customerInboundStockService.reserveMultipleForSale(
                customerNo = customer.customerNo,
                items = items,
                locationId = location.id
            )

            if (reserveResult.isFailure) {
                val errorMsg = reserveResult.exceptionOrNull()?.message ?: "可报账库存预留失败"
                _saveResult.value = SaveResult.Error(errorMsg)
                return kotlin.Result.failure(IllegalStateException(errorMsg))
            }

            // 5. 创建销售单据 - 修复：添加缺失的 locationName 和 operatorName 字段
            val bill = SaleBill(
                billNo = billNo,
                customerNo = customer.customerNo,
                customerName = customer.customerName,
                customerId = customer.id.toString(), // 转换为String以匹配实体类
                locationId = location.id,
                locationName = location.locationName, // 添加库位名称
                operatorId = operator.id,
                operatorName = operator.name, // 添加经手人姓名
                totalAmount = totalAmount.value,
                totalQuantity = totalQuantity.value,
                remark = _remark.value,
                createTime = System.currentTimeMillis(),
                status = "DRAFT",
                syncStatus = 0, // 已标记为未同步，符合需求
                printTime = null,
                customerBalance = 0.0
            )

            // 6. 保存单据
            val billId = database.saleBillDao().insert(bill)
            if (billId <= 0) {
                releaseReservedStocks(items, customer, location)
                _saveResult.value = SaveResult.Error("销售单保存失败")
                return kotlin.Result.failure(IllegalStateException("销售单保存失败"))
            }

            // 7. 保存明细
            val itemsWithBillId = items.map { it.copy(billId = billId) }
            database.saleItemDao().insertAll(itemsWithBillId)

            // 8. 确认可报账扣减（释放预留，已售量由 sale_item 统计）
            Log.d(TAG, "💰 开始确认可报账扣减...")
            val confirmResult = customerInboundStockService.confirmMultipleDeductions(
                customerNo = customer.customerNo,
                items = items,
                locationId = location.id
            )

            if (confirmResult.isFailure) {
                Log.e(TAG, "库存扣减失败: ${confirmResult.exceptionOrNull()?.message}")
            }

            // 9. 更新单据状态
            database.saleBillDao().updateStatus(billId, "COMPLETED")

            // ==============================================
            // 【已移除】实时同步代码块，无任何TCP同步调用
            // ==============================================

            Log.d(TAG, "✅ 销售单保存成功: ID=$billId, NO=$billNo")
            _saveResult.value = SaveResult.Success(billId, billNo)

            // 注意：这里不清空表单，由Activity决定何时清空
            // clearAll()

            // 返回单据ID和单据编号组成的Pair
            kotlin.Result.success(Pair(billId, billNo))
        } catch (e: Exception) {
            Log.e(TAG, "❌ 销售单保存异常: ${e.message}", e)
            val errorMsg = e.message ?: "保存失败"
            _saveResult.value = SaveResult.Error(errorMsg)
            kotlin.Result.failure(e)
        }
    }

    // === 移除商品 ===
    fun removeItem(index: Int) {
        val newList = _saleItems.value.toMutableList()
        newList.removeAt(index)
        _saleItems.value = newList
    }

    // === 清空表单 ===
    fun clearAll() {
        _selectedCustomer.value = null
        _selectedLocation.value = null
        _selectedOperator.value = null
        _saleItems.value = emptyList()
        _remark.value = ""
        _productStocks.value = emptyMap()
        _productsWithStock.value = emptyList()
        _saveResult.value = null
    }

    // 在 SaleOutViewModel 中添加
// ===== 继续开单 =====
    fun clearForContinue() {
        // 清空客户和商品明细，但保留默认值（库位和经手人）
        try {
            // 清空客户
            _selectedCustomer.value = null

            // 清空销售商品明细
            _saleItems.value = emptyList()

            // 清空备注
            _remark.value = ""

            // 使用缓存的默认值恢复库位和经手人
            if (cachedDefaultLocation != null) {
                _selectedLocation.value = cachedDefaultLocation
            } else {
                _selectedLocation.value = null
            }

            if (cachedDefaultOperator != null) {
                _selectedOperator.value = cachedDefaultOperator
            } else {
                _selectedOperator.value = null
            }

            Log.d(TAG, "🧹 继续开单: 已清空客户、商品明细，保留默认库位/经手人")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 继续开单清空失败: ${e.message}", e)
        }
    }
    // === 释放已预留的库存 ===
    private suspend fun releaseReservedStocks(
        items: List<SaleItem>,
        customer: Customer,
        location: Location
    ) {
        customerInboundStockService.releaseMultipleReservations(
            customerNo = customer.customerNo,
            items = items,
            locationId = location.id
        )
    }

    // === 根据库位加载商品 ===
    fun loadProductsByLocation(locationId: Long) {
        viewModelScope.launch {
            loadProductsWithStock(locationId)
        }
    }

    // === 清空商品列表 ===
    fun clearProducts() {
        _productsWithStock.value = emptyList()
        _productStocks.value = emptyMap()
    }

    // === 获取销售单详情 ===
    suspend fun getSaleBill(billId: Long): SaleBill? {
        return try {
            database.saleBillDao().getById(billId)
        } catch (e: Exception) {
            Log.e(TAG, "获取销售单详情失败: ${e.message}")
            null
        }
    }

    // === 获取销售单明细 ===
    suspend fun getSaleItems(billId: Long): List<SaleItem> {
        return try {
            database.saleItemDao().getItemsByBillId(billId)
        } catch (e: Exception) {
            Log.e(TAG, "获取销售单明细失败: ${e.message}")
            emptyList()
        }
    }

    // ==============================================
    // 新增：默认值功能全部方法
    // ==============================================
    // 初始化配置管理器
    fun initConfigManager(context: Context) {
        configManager = ConfigManager(context)
        loadDefaultValues()
    }

    // 修改 loadDefaultValues 方法，增加缓存
    private fun loadDefaultValues() {
        if (!::configManager.isInitialized) {
            Log.w(TAG, "⚠️ ConfigManager未初始化，跳过默认值加载")
            return
        }
        viewModelScope.launch {
            try {
                // 加载默认库位
                defaultLocationId = configManager.defaultLocationId
                if (defaultLocationId != -1L) {
                    val location = database.locationDao().getLocationById(defaultLocationId)
                    if (location != null) {
                        _selectedLocation.value = location
                        cachedDefaultLocation = location // 更新缓存
                        Log.d(TAG, "✅ 已加载默认库位: ${location.locationName}")
                    } else {
                        Log.w(TAG, "⚠️ 默认库位ID不存在: $defaultLocationId，重置无效ID")
                        configManager.defaultLocationId = -1L
                        cachedDefaultLocation = null
                    }
                } else {
                    Log.d(TAG, "ℹ️ 未设置默认库位")
                    cachedDefaultLocation = null
                }

                // 加载默认经手人
                defaultHandlerId = configManager.defaultHandlerId
                if (defaultHandlerId != -1L) {
                    val operator = database.operatorDao().getOperatorById(defaultHandlerId)
                    if (operator != null) {
                        _selectedOperator.value = operator
                        cachedDefaultOperator = operator // 更新缓存
                        Log.d(TAG, "✅ 已加载默认经手人: ${operator.name}")
                    } else {
                        Log.w(TAG, "⚠️ 默认经手人ID不存在: $defaultHandlerId，重置无效ID")
                        configManager.defaultHandlerId = -1L
                        cachedDefaultOperator = null
                    }
                } else {
                    Log.d(TAG, "ℹ️ 未设置默认经手人")
                    cachedDefaultOperator = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载默认值失败: ${e.message}", e)
            }
        }
    }

    // 获取默认值ID（用于UI显示星标）
    fun getDefaultLocationId(): Long = defaultLocationId
    fun getDefaultHandlerId(): Long = defaultHandlerId

    // 保存为默认值时更新缓存
    fun saveAsDefaultLocation() {
        _selectedLocation.value?.let { location ->
            configManager.defaultLocationId = location.id
            defaultLocationId = location.id
            cachedDefaultLocation = location // 更新缓存
            Log.d(TAG, "💾 已保存默认库位: ${location.locationName}")
        }
    }

    fun saveAsDefaultHandler() {
        _selectedOperator.value?.let { operator ->
            configManager.defaultHandlerId = operator.id
            defaultHandlerId = operator.id
            cachedDefaultOperator = operator // 更新缓存
            Log.d(TAG, "💾 已保存默认经手人: ${operator.name}")
        }
    }

    // 清除默认值
    fun clearDefaultLocation() {
        configManager.defaultLocationId = -1L
        defaultLocationId = -1L
        Log.d(TAG, "🗑️ 已清除默认库位")
    }

    fun clearDefaultHandler() {
        configManager.defaultHandlerId = -1L
        defaultHandlerId = -1L
        Log.d(TAG, "🗑️ 已清除默认经手人")
    }

}
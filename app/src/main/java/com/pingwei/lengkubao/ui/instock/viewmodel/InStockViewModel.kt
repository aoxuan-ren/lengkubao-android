package com.pingwei.lengkubao.ui.instock.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.*
import com.pingwei.lengkubao.sync.TcpSyncManager
import com.pingwei.lengkubao.ui.common.ConfigManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// 产品输入项数据类 - 仅保留唯一定义，入库单价默认0
data class ProductInputItem(
    val product: Product,
    var quantity: Int = 0,
    var unitPrice: Double = 0.0
) {
    fun calculateAmount(): Double = quantity * unitPrice
}

class InStockViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val TAG = "InStockViewModel"

    private val database by lazy { AppDatabase.getInstance(application.applicationContext) }
    private lateinit var configManager: ConfigManager

    // 【修复】TCP同步管理器判空，避免单例获取失败
    private val tcpSyncManager by lazy {
        val manager = LengKuBaoApplication.getSyncManager()
        if (manager == null) {
            Log.e(TAG, "❌ TCP同步管理器单例获取失败，未初始化")
        }
        manager
    }

    // ===== 状态管理 =====
    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    private val _selectedLocation = MutableStateFlow<Location?>(null)
    val selectedLocation: StateFlow<Location?> = _selectedLocation.asStateFlow()

    private val _selectedOperator = MutableStateFlow<Operator?>(null)
    val selectedOperator: StateFlow<Operator?> = _selectedOperator.asStateFlow()

    private val _productInputs = MutableStateFlow<List<ProductInputItem>>(emptyList())
    val productInputs: StateFlow<List<ProductInputItem>> = _productInputs.asStateFlow()

    private val _remark = MutableStateFlow("")
    val remark: StateFlow<String> = _remark.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 默认值ID
    private var defaultLocationId: Long = -1L
    private var defaultHandlerId: Long = -1L

    // ===== 缓存默认值，避免数据库查询延迟 =====
    private var cachedDefaultLocation: Location? = null
    private var cachedDefaultOperator: Operator? = null

    // ===== 计算属性 =====
    val totalAmount: StateFlow<Double>
        get() = _productInputs.map { inputs ->
            inputs.sumOf { it.calculateAmount() }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val totalQuantity: StateFlow<Int>
        get() = _productInputs.map { inputs ->
            inputs.sumOf { it.quantity }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )

    // ===== 数据流 =====
    val allLocations = database.locationDao().getAllLocations()
    val allOperators = database.operatorDao().getAllOperators()
    val allCustomers = database.customerDao().getAllCustomers()

    // ===== 初始化 =====
    init {
        Log.d(TAG, "InStockViewModel初始化")
        loadProducts()
    }

    // 【修复】初始化配置管理器 + 初始化完成后主动加载默认值，保证时序
    fun initConfigManager(context: Context) {
        if (!::configManager.isInitialized) {
            configManager = ConfigManager(context)
            Log.d(TAG, "✅ ConfigManager初始化完成")
            loadDefaultValues() // 主动触发，避免时序问题
        } else {
            Log.d(TAG, "ℹ️ ConfigManager已初始化，无需重复创建")
        }
    }

    // 加载商品数据 - 仅加载启用商品
    private fun loadProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val products = database.productDao().getAllEnabled()
                _productInputs.value = products.map { ProductInputItem(product = it) }
                Log.d(TAG, "✅ 加载了 ${_productInputs.value.size} 个启用商品")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载商品失败: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ===== 默认值相关方法 =====
    // 【修复】增加ConfigManager未初始化判空，避免空指针
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

    // 保存为默认值
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

    // ===== 商品输入操作方法 =====
    fun updateProductQuantity(productId: Long, quantity: Int) {
        val currentInputs = _productInputs.value.toMutableList()
        val index = currentInputs.indexOfFirst { it.product.id == productId }
        if (index != -1) {
            currentInputs[index] = currentInputs[index].copy(quantity = quantity)
            _productInputs.value = currentInputs
            Log.d(TAG, "✅ 更新商品ID $productId 数量为 $quantity")
        } else {
            Log.w(TAG, "⚠️ 未找到商品ID $productId，跳过数量更新")
        }
    }

    // ===== 其他操作方法 =====
    fun setRemark(text: String) {
        _remark.value = text
        Log.d(TAG, "ℹ️ 设置备注: $text")
    }

    fun selectCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
        Log.d(TAG, "👤 选择客户: ${customer?.customerName ?: "清空客户"}")
    }

    fun selectLocation(location: Location?) {
        _selectedLocation.value = location
        Log.d(TAG, "📍 选择库位: ${location?.locationName ?: "清空库位"}")
    }

    fun selectOperator(operator: Operator?) {
        _selectedOperator.value = operator
        Log.d(TAG, "👷 选择经手人: ${operator?.name ?: "清空经手人"}")
    }

    // ===== 清空表单 =====
    fun clearAll() {
        // 直接在主线程更新状态，不使用协程，确保立即刷新
        try {
            // 清空客户
            _selectedCustomer.value = null

            // 清空商品数量 - 立即更新
            val currentInputs = _productInputs.value
            val clearedInputs = currentInputs.map { inputItem ->
                inputItem.copy(quantity = 0, unitPrice = 0.0)
            }
            _productInputs.value = clearedInputs

            // 清空备注
            _remark.value = ""

            // 使用缓存的默认值立即恢复库位和经手人，避免数据库查询延迟
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

            Log.d(TAG, "🧹 已清空客户、商品数量和备注，使用缓存恢复库位/经手人")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 清空表单失败: ${e.message}", e)
        }
    }

    // ===== 保存入库单 =====
    suspend fun saveBill(): Result<Pair<Long, String>> {
        return try {
            // 1. 验证必填项
            val validationError = when {
                _selectedCustomer.value == null -> "请选择客户"
                _selectedLocation.value == null -> "请选择库位"
                _selectedOperator.value == null -> "请选择经手人"
                _productInputs.value.all { it.quantity == 0 } -> "请至少输入一种商品的数量"
                else -> null
            }
            if (validationError != null) {
                Log.w(TAG, "⚠️ 入库单保存验证失败: $validationError")
                return Result.failure(IllegalStateException(validationError))
            }

            // 2. 生成单据号和时间
            val billNo = generateBillNo()
            val now = System.currentTimeMillis()
            val customer = _selectedCustomer.value!!
            val location = _selectedLocation.value!!
            val operator = _selectedOperator.value!!

            // 3. 创建入库单主表，新增syncStatus=0标记为未同步
            val bill = InStockBill(
                billNo = billNo,
                customerNo = customer.customerNo,
                customerName = customer.customerName,
                locationId = location.id,
                locationName = location.locationName,
                operatorId = operator.id,
                operatorName = operator.name,
                totalAmount = totalAmount.value,
                totalQuantity = totalQuantity.value,
                remark = _remark.value,
                createTime = now,
                status = "COMPLETED",
                syncStatus = 0 // 0=未同步，1=已同步，标记单据待同步
            )

            // 4. 保存主表并校验ID
            val billId = database.inStockBillDao().insert(bill)
            if (billId <= 0) {
                Log.e(TAG, "❌ 入库单主表保存失败，返回ID: $billId")
                return Result.failure(Exception("入库单保存失败，数据库返回无效ID"))
            }

            // 5. 保存入库单明细
            val items = _productInputs.value
                .filter { it.quantity > 0 }
                .map { inputItem ->
                    InStockItem(
                        billId = billId,
                        productId = inputItem.product.id,
                        productNo = inputItem.product.productNo,
                        productName = inputItem.product.productName,
                        quantity = inputItem.quantity,
                        unitPrice = inputItem.unitPrice,
                        amount = inputItem.calculateAmount(),
                        unit = inputItem.product.unit
                    )
                }
            if (items.isNotEmpty()) {
                database.inStockItemDao().insertAll(items)
                Log.d(TAG, "✅ 保存了 ${items.size} 条入库单明细")
            } else {
                Log.w(TAG, "⚠️ 无有效商品明细，跳过明细保存")
            }

            // 6. 更新库存
            updateStock(items, location.id, billNo, now)

            Log.d(TAG, "✅ 入库单本地保存成功: 单号=$billNo，ID=$billId，标记为未同步状态")
            Result.success(Pair(billId, billNo))
        } catch (e: Exception) {
            Log.e(TAG, "❌ 入库单保存异常", e)
            Result.failure(e)
        }
    }

    // 生成单据号：RK + 年月日 + 4位序号（简化版，可根据业务优化）
    private fun generateBillNo(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
        val dateStr = dateFormat.format(Date())
        val sequence = (System.currentTimeMillis() % 10000).toInt()
        return "RK$dateStr${String.format("%04d", sequence)}"
    }

    // 更新库存 - 新增/累加库存
    private suspend fun updateStock(
        items: List<InStockItem>,
        locationId: Long,
        billNo: String,
        timestamp: Long
    ) {
        try {
            items.forEach { item ->
                val existingStock = database.stockDao().getStock(item.productId, locationId)
                if (existingStock != null) {
                    // 已有库存，累加数量
                    database.stockDao().addStockQuantity(
                        productId = item.productId,
                        locationId = locationId,
                        addQuantity = item.quantity,
                        timestamp = timestamp,
                        billNo = billNo
                    )
                    Log.d(TAG, "✅ 累加商品ID ${item.productId} 库存，数量+${item.quantity}")
                } else {
                    // 无库存，创建新记录
                    val product = database.productDao().getByNo(item.productNo)
                    val location = database.locationDao().getLocationById(locationId)
                    if (product != null && location != null) {
                        val newStock = Stock(
                            productId = item.productId,
                            productNo = product.productNo,
                            productName = product.productName,
                            locationId = locationId,
                            locationNo = location.locationNo,
                            currentQuantity = item.quantity,
                            reservedQuantity = 0,
                            lastUpdated = timestamp,
                            lastBillNo = billNo
                        )
                        database.stockDao().insert(newStock)
                        Log.d(TAG, "✅ 为商品ID ${item.productId} 创建新库存记录，数量${item.quantity}")
                    } else {
                        Log.w(TAG, "⚠️ 更新库存失败，商品/库位不存在：商品ID=${item.productId}，库位ID=$locationId")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 更新库存失败: ${e.message}", e)
        }
    }

    // ===== 清除默认值 =====
    fun clearDefaultLocation() {
        if (::configManager.isInitialized) {
            configManager.defaultLocationId = -1L
            defaultLocationId = -1L
            cachedDefaultLocation = null
            Log.d(TAG, "🗑️ 已清除默认库位")
        }
    }

    fun clearDefaultHandler() {
        if (::configManager.isInitialized) {
            configManager.defaultHandlerId = -1L
            defaultHandlerId = -1L
            cachedDefaultOperator = null
            Log.d(TAG, "🗑️ 已清除默认经手人")
        }
    }

    fun clearAllDefaults() {
        if (::configManager.isInitialized) {
            configManager.clearAllDefaults()
            defaultLocationId = -1L
            defaultHandlerId = -1L
            cachedDefaultLocation = null
            cachedDefaultOperator = null
            Log.d(TAG, "🗑️ 已清除所有默认值")
        }
    }

    // ===== 获取单据详情 =====
    suspend fun getInStockBill(billId: Long): InStockBill? {
        return try {
            database.inStockBillDao().getById(billId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取入库单详情失败，ID：$billId", e)
            null
        }
    }

    suspend fun getInStockItems(billId: Long): List<InStockItem> {
        return try {
            database.inStockItemDao().getItemsByBillId(billId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取入库单明细失败，ID：$billId", e)
            emptyList()
        }
    }
}
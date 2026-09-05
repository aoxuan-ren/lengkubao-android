package com.pingwei.lengkubao.ui.packaging.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.*
import com.pingwei.lengkubao.sync.TcpSyncManager
import com.pingwei.lengkubao.ui.common.ConfigManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.database.sqlite.SQLiteConstraintException

// 包装项数据类
data class PackagingInputItem(
    val packagingType: PackagingType,
    var quantity: Int = 0,
    var unitPrice: Double = 0.0,
    var subtotal: Double = 0.0
) {
    fun calculateSubtotal(): Double {
        return if (quantity > 0 && unitPrice >= 0) {
            quantity * unitPrice
        } else {
            0.0
        }
    }
}

class PackagingViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val TAG = "PackagingViewModel"

    private val database by lazy { AppDatabase.getInstance(application.applicationContext) }
    private lateinit var configManager: ConfigManager

    // 获取全局TCP同步管理器单例
    private val tcpSyncManager by lazy {
        LengKuBaoApplication.getSyncManager()
    }

    // ===== 状态管理 =====
    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    private val _selectedOperator = MutableStateFlow<Operator?>(null)
    val selectedOperator: StateFlow<Operator?> = _selectedOperator.asStateFlow()

    // 【新增】包装类型标记（出包装/进包装）
    private val _packagingTypeFlag = MutableStateFlow("TAKE") // TAKE-出包装, RETURN-进包装
    val packagingTypeFlag: StateFlow<String> = _packagingTypeFlag.asStateFlow()

    // 包装项输入列表
    private val _packagingInputs = MutableStateFlow<List<PackagingInputItem>>(emptyList())
    val packagingInputs: StateFlow<List<PackagingInputItem>> = _packagingInputs.asStateFlow()

    // 备注
    private val _remark = MutableStateFlow("")
    val remark: StateFlow<String> = _remark.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 默认值ID
    private var defaultHandlerId: Long = -1L

    // ===== 计算属性 =====
    val totalAmount: StateFlow<Double>
        get() = combine(
            _packagingInputs,
            _packagingTypeFlag
        ) { inputs, flag ->
            val rawTotal = inputs.sumOf { it.calculateSubtotal() }
            // 进包装时总金额为负值
            if (flag == "RETURN") -rawTotal else rawTotal
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    // ===== 数据流 =====
    val allOperators = database.operatorDao().getAllOperators()
    val allCustomers = database.customerDao().getCustomersByType(CustomerType.SELLER)

    // ===== 初始化 =====
    init {
        Log.d(TAG, "PackagingViewModel初始化")
        loadPackagingTypes()
    }

    // 初始化配置管理器
    fun initConfigManager(context: Context) {
        configManager = ConfigManager(context)
        loadDefaultValues()
    }

    // 加载包装类型数据
    private fun loadPackagingTypes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 只加载启用的包装类型
                val packagingTypes = database.packagingTypeDao().getAllEnabled()
                _packagingInputs.value = packagingTypes.map {
                    PackagingInputItem(
                        packagingType = it,
                        unitPrice = it.unitPrice // 使用默认单价
                    )
                }
                Log.d(TAG, "加载了 ${_packagingInputs.value.size} 个包装类型")
            } catch (e: Exception) {
                Log.e(TAG, "加载包装类型失败: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ===== 包装类型标记设置 =====
    fun setPackagingTypeFlag(flag: String) {
        if (flag == "TAKE" || flag == "RETURN") {
            _packagingTypeFlag.value = flag
            Log.d(TAG, "设置包装类型标记: $flag")
        }
    }

    // ===== 默认值相关方法 =====
    private fun loadDefaultValues() {
        viewModelScope.launch {
            try {
                // 获取默认经手人ID
                defaultHandlerId = configManager.defaultHandlerId
                if (defaultHandlerId != -1L) {
                    val operator = database.operatorDao().getOperatorById(defaultHandlerId)
                    if (operator != null) {
                        _selectedOperator.value = operator
                        Log.d(TAG, "✅ 已加载默认经手人: ${operator.name}")
                    } else {
                        Log.w(TAG, "⚠️ 默认经手人ID不存在: $defaultHandlerId")
                        configManager.defaultHandlerId = -1L // 重置无效ID
                    }
                } else {
                    Log.d(TAG, "ℹ️ 未设置默认经手人")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载默认值失败: ${e.message}", e)
            }
        }
    }

    // 获取默认值ID（用于UI显示星标）
    fun getDefaultHandlerId(): Long = defaultHandlerId

    // 保存为默认经手人
    fun saveAsDefaultHandler() {
        _selectedOperator.value?.let { operator ->
            configManager.defaultHandlerId = operator.id
            defaultHandlerId = operator.id
            Log.d(TAG, "💾 已保存默认经手人: ${operator.name}")
        }
    }

    // ===== 包装项操作方法 =====
    fun addPackagingItem(packagingType: PackagingType) {
        val currentInputs = _packagingInputs.value.toMutableList()

        // 检查是否已存在相同的包装类型
        val existingIndex = currentInputs.indexOfFirst {
            it.packagingType.id == packagingType.id
        }

        if (existingIndex != -1) {
            // 如果已存在，增加数量
            val currentQuantity = currentInputs[existingIndex].quantity
            currentInputs[existingIndex] = currentInputs[existingIndex].copy(
                quantity = currentQuantity + 1,
                subtotal = (currentQuantity + 1) * currentInputs[existingIndex].unitPrice
            )
        } else {
            // 添加新的包装项
            val newItem = PackagingInputItem(
                packagingType = packagingType,
                quantity = 1,
                unitPrice = packagingType.unitPrice,
                subtotal = packagingType.unitPrice
            )
            currentInputs.add(newItem)
        }

        _packagingInputs.value = currentInputs
        Log.d(TAG, "添加包装类型: ${packagingType.typeName}")
    }

    fun updatePackagingQuantity(typeId: Long, quantity: Int) {
        val currentInputs = _packagingInputs.value.toMutableList()
        val index = currentInputs.indexOfFirst { it.packagingType.id == typeId }
        if (index != -1) {
            val item = currentInputs[index]
            currentInputs[index] = item.copy(
                quantity = quantity,
                subtotal = quantity * item.unitPrice
            )
            _packagingInputs.value = currentInputs
            Log.d(TAG, "更新包装类型ID $typeId 数量为 $quantity")
        }
    }

    fun updatePackagingPrice(typeId: Long, price: Double) {
        val currentInputs = _packagingInputs.value.toMutableList()
        val index = currentInputs.indexOfFirst { it.packagingType.id == typeId }
        if (index != -1) {
            val item = currentInputs[index]
            currentInputs[index] = item.copy(
                unitPrice = price,
                subtotal = item.quantity * price
            )
            _packagingInputs.value = currentInputs
            Log.d(TAG, "更新包装类型ID $typeId 单价为 $price")
        }
    }

    fun removePackagingItem(typeId: Long) {
        val currentInputs = _packagingInputs.value.toMutableList()
        val index = currentInputs.indexOfFirst { it.packagingType.id == typeId }
        if (index != -1) {
            currentInputs.removeAt(index)
            _packagingInputs.value = currentInputs
            Log.d(TAG, "移除包装类型ID $typeId")
        }
    }

    // ===== 其他操作方法 =====
    fun setRemark(text: String) {
        _remark.value = text
    }

    fun selectCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
        Log.d(TAG, "👤 选择客户: ${customer?.customerName}")
    }

    fun selectOperator(operator: Operator?) {
        _selectedOperator.value = operator
        Log.d(TAG, "👷 选择经手人: ${operator?.name}")
    }

    // ===== 清空表单 =====
    fun clearAll() {
        _selectedCustomer.value = null
        // 不清空经手人，保留当前选择的经手人
        _remark.value = ""

        // 重置包装项数量，但保留包装类型列表
        val clearedInputs = _packagingInputs.value.map { inputItem ->
            inputItem.copy(quantity = 0, unitPrice = inputItem.packagingType.unitPrice, subtotal = 0.0)
        }
        _packagingInputs.value = clearedInputs

        Log.d(TAG, "🧹 已清空所有输入，保留经手人: ${_selectedOperator.value?.name}")
    }

    // ===== 保存包装单 =====
    suspend fun saveBill(): Result<Pair<Long, String>> {
        return try {
            // 验证必填项
            val validationError = when {
                _selectedCustomer.value == null -> "请选择客户"
                _selectedOperator.value == null -> "请选择经手人"
                _packagingInputs.value.all { it.quantity == 0 } -> "请至少输入一种包装类型的数量"
                else -> null
            }

            if (validationError != null) {
                return Result.failure(IllegalStateException(validationError))
            }

            val customer = _selectedCustomer.value!!
            val operator = _selectedOperator.value!!
            val flag = _packagingTypeFlag.value

            // 计算总金额（根据标记调整）
            val rawTotal = _packagingInputs.value.sumOf { it.calculateSubtotal() }
            val finalTotalAmount = if (flag == "RETURN") -rawTotal else rawTotal

            val itemInputs = _packagingInputs.value.filter { it.quantity > 0 }

            val (billId, billNo) = insertBillInTransaction(
                customer = customer,
                operator = operator,
                flag = flag,
                finalTotalAmount = finalTotalAmount,
                remark = _remark.value,
                itemInputs = itemInputs,
            )

            Log.d(TAG, "✅ 包装单保存成功: $billNo, ID: $billId, 类型: $flag, 金额: $finalTotalAmount")
            Result.success(Pair(billId, billNo))
        } catch (e: Exception) {
            Log.e(TAG, "❌ 包装单保存失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 在事务内生成单号并写入主表/明细，配合 bill_no 唯一索引避免并发重复单号。
     */
    private suspend fun insertBillInTransaction(
        customer: Customer,
        operator: Operator,
        flag: String,
        finalTotalAmount: Double,
        remark: String,
        itemInputs: List<PackagingInputItem>,
    ): Pair<Long, String> {
        var lastError: Exception? = null
        for (attempt in 0 until MAX_BILL_NO_INSERT_ATTEMPTS) {
            try {
                return database.withTransaction {
                    val now = System.currentTimeMillis()
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                    val billDate = dateFormat.format(Date(now))
                    val billNo = generateBillNoInTransaction(now)

                    val bill = PackagingBill(
                        billNo = billNo,
                        customerId = customer.id,
                        customerNo = customer.customerNo,
                        customerName = customer.customerName,
                        operatorId = operator.id,
                        operatorName = operator.name,
                        totalAmount = finalTotalAmount,
                        createTime = now,
                        billDate = billDate,
                        packagingTypeFlag = flag,
                        remark = remark,
                        isSynced = false,
                    )

                    val billId = database.packagingBillDao().insert(bill)
                    if (billId <= 0) {
                        throw IllegalStateException("包装单保存失败")
                    }

                    if (itemInputs.isNotEmpty()) {
                        val items = itemInputs.map { inputItem ->
                            val calculatedAmount = inputItem.calculateSubtotal()
                            val finalItemAmount = if (flag == "RETURN") -calculatedAmount else calculatedAmount
                            PackagingItem(
                                billId = billId,
                                packagingTypeFlag = flag,
                                packagingType = inputItem.packagingType.typeName,
                                packagingTypeId = inputItem.packagingType.id,
                                packagingTypeName = inputItem.packagingType.typeName,
                                unitPrice = inputItem.unitPrice,
                                quantity = inputItem.quantity,
                                subtotal = finalItemAmount,
                                amount = finalItemAmount,
                                unit = inputItem.packagingType.unit,
                            )
                        }
                        database.packagingItemDao().insertAll(items)
                    }

                    Pair(billId, billNo)
                }
            } catch (e: Exception) {
                if (!isBillNoUniqueViolation(e)) {
                    throw e
                }
                lastError = e
                Log.w(TAG, "⚠️ 包装单号冲突，重试 (${attempt + 1}/$MAX_BILL_NO_INSERT_ATTEMPTS): ${e.message}")
            }
        }
        throw lastError ?: IllegalStateException("包装单保存失败：单号冲突")
    }

    private fun isBillNoUniqueViolation(e: Exception): Boolean {
        if (e is SQLiteConstraintException) return true
        val cause = e.cause
        if (cause is SQLiteConstraintException) return true
        val message = e.message.orEmpty()
        return message.contains("UNIQUE constraint failed", ignoreCase = true) &&
            message.contains("bill_no", ignoreCase = true)
    }

    private suspend fun generateBillNoInTransaction(todayMillis: Long): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(todayMillis))
        val prefix = "BZ$date"
        val maxSeq = database.packagingBillDao().getBillNosWithPrefix(prefix)
            .mapNotNull { billNo ->
                val suffix = billNo.removePrefix(prefix)
                suffix.takeWhile { it.isDigit() }.toIntOrNull()
            }
            .maxOrNull() ?: 0
        val sequence = maxSeq + 1
        return "$prefix${String.format(Locale.CHINA, "%04d", sequence)}"
    }

    companion object {
        private const val MAX_BILL_NO_INSERT_ATTEMPTS = 5
    }

    // 重新加载默认经手人
    fun loadDefaultOperator() {
        viewModelScope.launch {
            try {
                defaultHandlerId = configManager.defaultHandlerId
                if (defaultHandlerId != -1L) {
                    val operator = database.operatorDao().getOperatorById(defaultHandlerId)
                    if (operator != null) {
                        _selectedOperator.value = operator
                        Log.d(TAG, "✅ 已重新加载默认经手人: ${operator.name}")
                    } else {
                        Log.w(TAG, "⚠️ 默认经手人ID不存在: $defaultHandlerId")
                        configManager.defaultHandlerId = -1L
                        _selectedOperator.value = null
                    }
                } else {
                    Log.d(TAG, "ℹ️ 未设置默认经手人")
                    _selectedOperator.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 重新加载默认经手人失败: ${e.message}", e)
                _selectedOperator.value = null
            }
        }
    }

    // ===== 清除默认值 =====
    fun clearDefaultHandler() {
        configManager.defaultHandlerId = -1L
        defaultHandlerId = -1L
        Log.d(TAG, "🗑️ 已清除默认经手人")
    }

    // ===== 获取单据详情相关方法 =====
    suspend fun getPackagingBill(billId: Long): PackagingBill? {
        return database.packagingBillDao().getById(billId)
    }

    suspend fun getPackagingItems(billId: Long): List<PackagingItem> {
        return database.packagingItemDao().getItemsByBillId(billId)
    }
}
package com.pingwei.lengkubao.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.pingwei.lengkubao.service.model.InStockItemPrint
import com.pingwei.lengkubao.service.model.PackagingItemPrint
import com.pingwei.lengkubao.service.model.PreSaleItemPrint
import com.pingwei.lengkubao.service.model.SaleItemPrint
import com.pingwei.lengkubao.ui.common.ConfigManager
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 商米打印服务 - 使用远程依赖方式
 * 基于 com.sunmi:printerLibrary:1.0.18
 */
class SunmiPrintService(private val context: Context) {

    private var sunmiPrinterService: SunmiPrinterService? = null
    private var isConnected = false

    // 持久化状态管理 - SharedPreferences
    private val prefs by lazy {
        context.getSharedPreferences("printer_state", Context.MODE_PRIVATE)
    }

    companion object {
        private var instance: SunmiPrintService? = null

        fun getInstance(context: Context): SunmiPrintService {
            if (instance == null) {
                instance = SunmiPrintService(context.applicationContext)
            }
            return instance!!
        }
    }

    /**
     * 确保打印机连接（打印前调用）
     */
    suspend fun ensureConnectedForPrint(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 检查当前连接状态
            if (isConnected && sunmiPrinterService != null) {
                Log.d("SunmiPrintService", "打印机已连接，检查状态")
                return@withContext verifyPrinterConnection()
            }

            Log.d("SunmiPrintService", "打印机未连接，尝试自动连接")

            // 尝试从保存状态恢复
            if (loadConnectionState()) {
                Log.d("SunmiPrintService", "尝试恢复上次连接状态")
                val recovered = initializeWithStateRestore()
                if (recovered) {
                    Log.d("SunmiPrintService", "恢复连接成功")
                    return@withContext true
                }
            }

            // 恢复失败，进行完整初始化
            Log.d("SunmiPrintService", "进行完整初始化")
            val initialized = initialize()
            if (initialized) {
                Log.d("SunmiPrintService", "初始化成功")
                return@withContext true
            }

            Log.e("SunmiPrintService", "所有连接尝试都失败")
            return@withContext false

        } catch (e: Exception) {
            Log.e("SunmiPrintService", "确保连接失败: ${e.message}", e)
            false
        }
    }

    /**
     * 打印机连接回调（已修改：增加状态保存）
     */
    private val innerPrinterCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService) {
            sunmiPrinterService = service
            isConnected = true
            saveConnectionState(true) // 保存连接状态
            Log.d("SunmiPrintService", "打印机已连接，状态已保存")
        }

        override fun onDisconnected() {
            sunmiPrinterService = null
            isConnected = false
            saveConnectionState(false) // 保存断开状态
            Log.d("SunmiPrintService", "打印机已断开，状态已保存")
        }
    }

    val printerCallback: InnerPrinterCallback
        get() = innerPrinterCallback // 自定义getter，返回私有回调实例，不开放修改权限

    /**
     * 保存连接状态到SharedPreferences
     */
    private fun saveConnectionState(isConnected: Boolean) {
        prefs.edit()
            .putBoolean("is_printer_connected", isConnected)
            .apply()
    }

    /**
     * 从SharedPreferences加载连接状态
     */
    private fun loadConnectionState(): Boolean {
        return prefs.getBoolean("is_printer_connected", false)
    }

    /**
     * 增强版初始化方法 - 支持状态恢复
     */
    suspend fun initializeWithStateRestore(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. 检查并恢复上次的连接状态
            val lastConnected = loadConnectionState()

            // 2. 如果上次是连接状态，尝试恢复连接
            if (lastConnected) {
                Log.d("SunmiPrintService", "尝试恢复上次的连接状态")

                // 先尝试绑定服务
                val bindResult = InnerPrinterManager.getInstance().bindService(context, innerPrinterCallback)
                if (bindResult) {
                    // 等待连接建立
                    var waitTime = 0
                    while (!isConnected && waitTime < 3000) { // 最多等待3秒
                        delay(100)
                        waitTime += 100
                    }

                    if (isConnected) {
                        // 验证打印机是否可用
                        if (verifyPrinterConnection()) {
                            Log.d("SunmiPrintService", "成功恢复打印机连接")
                            return@withContext true
                        }
                    }
                }

                Log.d("SunmiPrintService", "恢复连接失败，进行完整初始化")
            }

            // 3. 如果恢复失败或上次未连接，进行完整初始化
            return@withContext initialize()

        } catch (e: Exception) {
            Log.e("SunmiPrintService", "初始化失败: ${e.message}", e)
            saveConnectionState(false)
            return@withContext false
        }
    }

    /**
     * 初始化打印机连接（增强稳定版）
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 先检查是否已经连接
            if (isConnected && sunmiPrinterService != null) {
                return@withContext true
            }

            // 重置连接状态
            isConnected = false
            sunmiPrinterService = null

            // 如果之前有连接，先断开
            try {
                InnerPrinterManager.getInstance().unBindService(context, innerPrinterCallback)
            } catch (e: Exception) {
                // 忽略断开时的异常
            }

            // 等待一小段时间
            delay(100)

            // 绑定打印机服务
            val bindResult = InnerPrinterManager.getInstance().bindService(context, innerPrinterCallback)
            if (!bindResult) {
                Log.w("SunmiPrintService", "绑定服务失败")
                return@withContext false
            }

            // 等待连接建立 - 使用协程的 delay
            var waitTime = 0
            while (!isConnected && waitTime < 5000) { // 最多等待 5 秒
                delay(100)
                waitTime += 100
            }

            // 检查最终连接状态
            if (!isConnected) {
                Log.w("SunmiPrintService", "连接超时，isConnected=$isConnected")
                // 绑定超时，取消绑定
                try {
                    InnerPrinterManager.getInstance().unBindService(context, innerPrinterCallback)
                } catch (e: Exception) {
                    // 忽略解绑错误
                }
                return@withContext false
            }

            // 等待服务完全初始化
            delay(200)

            // 验证打印机是否真的可用
            val verified = verifyPrinterConnection()
            Log.d("SunmiPrintService", "打印机验证结果: $verified")
            return@withContext verified

        } catch (e: Exception) {
            Log.e("SunmiPrintService", "初始化失败: ${e.message}", e)
            saveConnectionState(false)
            false
        }
    }

    /**
     * 检查打印机是否已连接
     */
    fun isPrinterConnected(): Boolean {
        return isConnected && sunmiPrinterService != null
    }

    /**
     * 强制重新连接打印机
     */
    suspend fun reconnect(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 先断开现有连接
            disconnect()
            delay(500)
            // 重新初始化
            return@withContext initialize()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取详细的连接状态
     */
    suspend fun getConnectionStatus(): Map<String, Any> = withContext(Dispatchers.IO) {
        val status = mutableMapOf<String, Any>()

        status["isConnected"] = isConnected
        status["hasService"] = (sunmiPrinterService != null)

        if (sunmiPrinterService != null) {
            try {
                val printerStatus = sunmiPrinterService!!.updatePrinterState()
                status["printerStatusCode"] = printerStatus
                status["printerStatusText"] = getPrinterStatusText(printerStatus)
            } catch (e: Exception) {
                status["printerStatusCode"] = -1
                status["printerStatusText"] = "获取状态失败: ${e.message}"
            }
        }

        return@withContext status
    }

    /**
     * 获取当前连接状态（包含持久化状态）
     */
    fun getConnectionState(): Map<String, Any> {
        val memoryState = isConnected && sunmiPrinterService != null
        val savedState = loadConnectionState()

        return mapOf(
            "memory_connected" to memoryState,
            "saved_connected" to savedState,
            "service_exists" to (sunmiPrinterService != null),
            "can_print" to (isConnected && sunmiPrinterService != null)
        )
    }

    /**
     * 检查并自动恢复连接
     */
    suspend fun checkAndAutoRecover(): Boolean = withContext(Dispatchers.IO) {
        // 如果当前已连接，直接返回
        if (isConnected && sunmiPrinterService != null) {
            return@withContext verifyPrinterConnection()
        }

        // 否则尝试恢复连接
        return@withContext initializeWithStateRestore()
    }

    private fun getPrinterStatusText(status: Int): String {
        return when (status) {
            0 -> "正常"
            1 -> "准备中"
            2 -> "缺纸"
            3 -> "打印头过热"
            4 -> "打印机开盖"
            5 -> "切刀异常"
            6 -> "切刀恢复"
            505 -> "未检测到打印机"
            else -> "未知状态: $status"
        }
    }

    /**
     * 验证打印机连接状态
     */
    private suspend fun verifyPrinterConnection(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (sunmiPrinterService == null) {
                false
            } else {
                // 尝试获取打印机状态
                val status = sunmiPrinterService!!.updatePrinterState()
                // 状态码 0 表示正常，505 表示未检测到打印机
                status != 505
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查打印机状态
     */
    suspend fun checkPrinterStatus(): String = withContext(Dispatchers.IO) {
        if (!isConnected || sunmiPrinterService == null) {
            return@withContext "打印机未连接"
        }

        try {
            val status = sunmiPrinterService!!.updatePrinterState()
            when (status) {
                0 -> "打印机正常"
                1 -> "打印机准备中"
                2 -> "缺纸"
                3 -> "打印头过热"
                4 -> "打印机开盖"
                5 -> "切刀异常"
                6 -> "切刀恢复"
                505 -> "未检测到打印机"
                else -> "打印机状态码: $status"
            }
        } catch (e: Exception) {
            "检查状态失败: ${e.message}"
        }
    }

    /**
     * 获取打印机信息
     */
    suspend fun getPrinterInfo(): String = withContext(Dispatchers.IO) {
        if (!isConnected || sunmiPrinterService == null) {
            return@withContext "打印机未连接"
        }

        try {
            val serialNo = sunmiPrinterService!!.printerSerialNo
            val model = sunmiPrinterService!!.printerModal
            val version = sunmiPrinterService!!.printerVersion
            "型号: $model\n序列号: $serialNo\n固件版本: $version"
        } catch (e: Exception) {
            "获取信息失败: ${e.message}"
        }
    }

    /**
     * 打印入库单（简化版）
     */
    @SuppressLint("DefaultLocale")
    suspend fun printInStockBill(
        billNo: String,
        customerName: String,
        location: String,
        operator: String,
        items: List<InStockItemPrint>,
        totalItems: Int,
        totalQuantity: Double
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d("SunmiPrintService", "开始打印入库单，检查打印机连接")
        // 核心修改1：基于传入的打印明细重新计算总数量，彻底解决传参错误问题
        val realTotalQuantity = items.sumOf { it.quantity }
        Log.d("SunmiPrintService", "传入的总数量：$totalQuantity | 明细实际合计总数量：$realTotalQuantity")

        // 确保打印机已连接
        val connected = ensureConnectedForPrint()
        if (!connected) {
            Log.e("SunmiPrintService", "打印失败：打印机未连接")
            throw Exception("打印机未连接，请先初始化")
        }

        try {
            sunmiPrinterService!!.enterPrinterBuffer(true)

            // 企业名称
            val configManager = ConfigManager(context)
            val companyName = configManager.getCompanyName() ?: "平伟冷藏库"
            printText(companyName, 1)

            // 单据标题
            printText("入库单", 1)
            printSeparator()

            // 基础信息 - 紧凑布局
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            printText("单据号: $billNo", 0)
            printText("时间: $currentTime", 0)
            printText("客户: $customerName", 0)
            printText("库位: $location", 0)
            printText("经手人: $operator", 0)

            printSeparator()

            // 表头 - 商品型号和入库数量使用大字体
            printTextWithFont("商品型号       入库数量", 0, 32)
            printSeparator()

            // 明细 - 商品型号使用大字体，入库数量使用大字体
            items.forEach { item ->
                // 合并：商品名 + 固定空格 + 保留2位小数的数量，一次调用函数
                val printContent = item.productName + "          " + String.format("%.2f", item.quantity)
                printTextWithFont(printContent, 0, 32)
            }

            printSeparator()

            // 合计 - 使用大字体
            printText("合计项: $totalItems 项", 0)
            // 核心修改2：使用实际计算的realTotalQuantity，对齐方式改为0避免文本遮挡
            printTextWithFont("总数量: ${String.format("%.2f", realTotalQuantity)}", 0, 32)

            // 底部信息
            printText("---", 1)
            printText("冷库宝管理系统", 1)

            // 提交打印
            val success = commitPrintWithCallback()

            if (success) {
                try {
                    sunmiPrinterService!!.cutPaper(null)
                } catch (e: Exception) {
                    // 忽略切刀错误
                }
            }

            success
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                sunmiPrinterService!!.exitPrinterBuffer(false)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            false
        }
    }

    /**
     * 打印包装单（修正版 - 解决金额为0问题）
     */
    suspend fun printPackagingBill(
        billNo: String,
        customerName: String,
        operator: String,
        items: List<PackagingItemPrint>,
        totalAmount: Double
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d("SunmiPrintService", "开始打印包装单，检查打印机连接")

        // 确保打印机已连接
        val connected = ensureConnectedForPrint()
        if (!connected) {
            Log.e("SunmiPrintService", "打印失败：打印机未连接")
            throw Exception("打印机未连接，请先初始化")
        }

        try {
            sunmiPrinterService!!.enterPrinterBuffer(true)

            // 企业名称
            val configManager = ConfigManager(context)
            val companyName = configManager.getCompanyName() ?: "平伟冷藏库"
            printText(companyName, 1)

            val normalizedFlag = items.firstOrNull()?.packagingTypeFlag?.uppercase(Locale.ROOT)
            val packagingFlagLabel = when (normalizedFlag) {
                "RETURN" -> "进包装"
                "TAKE" -> "出包装"
                else -> if (totalAmount < 0) "进包装" else "出包装"
            }

            // 单据标题
            val billTitle = if (packagingFlagLabel == "进包装") "（进）包装记账单" else "（出）包装记账单"
            printText(billTitle, 1)
            printSeparator()

            // 基础信息 - 紧凑布局
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            printText("单据号: $billNo", 0)
            printText("时间: $currentTime", 0)
            printText("客户: $customerName", 0)
            printText("经手人: $operator", 0)
            printText("包装类型: $packagingFlagLabel", 0)

            printSeparator()

            // 表头 - 参照入库单格式：类型 + 固定空格 + 数量 + 单价 + 金额
            printTextWithFont("类型   数量   单价", 0, 32)
            printSeparator()

// 明细 - 计算实际金额并格式化打印
            items.forEach { item ->
                // 安全计算金额
                val actualAmount = if (item.amount > 0) item.amount else item.quantity * item.unitPrice

                // 修正格式：减少字符宽度，更紧凑
                val printContent = String.format(
                    "%-4s  %-4d  ¥%-4.1f",
                    item.packagingType.take(8), // 限制类型长度最多8个字符
                    item.quantity,
                    item.unitPrice
                )
                printTextWithFont(printContent, 0, 32)

                // 调试日志
                Log.d("SunmiPrintService", "包装项: ${item.packagingType}, 数量: ${item.quantity}, 单价: ${item.unitPrice}, 金额: $actualAmount")
            }

            printSeparator()

            // 重新计算总金额，确保正确
            val recalculatedTotal = if (totalAmount > 0) {
                totalAmount
            } else {
                items.sumOf {
                    if (it.amount > 0) it.amount else it.quantity * it.unitPrice
                }
            }

            // 调试日志
            Log.d("SunmiPrintService", "传入总金额: $totalAmount, 重新计算总金额: $recalculatedTotal")

            // 合计 - 使用大字体
            printText("合计项: ${items.size} 项", 0)
            printTextWithFont("总金额: ¥${String.format("%.2f", recalculatedTotal)}", 0, 32)

            // 底部信息
            printText("---", 1)
            printText("冷库宝管理系统", 1)

            // 提交打印
            val success = commitPrintWithCallback()

            if (success) {
                try {
                    sunmiPrinterService!!.cutPaper(null)
                } catch (e: Exception) {
                    // 忽略切刀错误
                }
            }

            success
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                sunmiPrinterService!!.exitPrinterBuffer(false)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            false
        }
    }

    /**
     * 打印预售/出库销售单
     */
    @SuppressLint("DefaultLocale")
    suspend fun printPreSaleBill(
        billNo: String,
        buyerName: String,
        locationName: String,
        operatorName: String,
        saleMode: String,
        items: List<PreSaleItemPrint>,
        totalAmount: Double,
        paidAmount: Double,
        remark: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d("SunmiPrintService", "开始打印预售单，检查打印机连接")

        val connected = ensureConnectedForPrint()
        if (!connected) {
            Log.e("SunmiPrintService", "打印失败：打印机未连接")
            throw Exception("打印机未连接，请先初始化")
        }

        try {
            sunmiPrinterService!!.enterPrinterBuffer(true)

            val configManager = ConfigManager(context)
            val companyName = configManager.getCompanyName() ?: "平伟冷藏库"
            printText(companyName, 1)

            val title = if (saleMode == "PRESALE") "预售单" else "出库销售单"
            printText(title, 1)
            printSeparator()

            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            printText("单据号: $billNo", 0)
            printText("时间: $currentTime", 0)
            printText("买家: $buyerName", 0)
            printText("库位: $locationName", 0)
            printText("经手人: $operatorName", 0)
            if (remark.isNotBlank()) {
                printText("备注: $remark", 0)
            }

            printSeparator()
            printTextWithFont("型号   数量  单价", 0, 32)
            printSeparator()

            items.forEach { item ->
                val line = String.format(
                    "%-6s %-4d ¥%.1f",
                    item.productName.take(6),
                    item.quantity,
                    item.salePrice
                )
                printTextWithFont(line, 0, 32)
                printText("      小计: ¥${String.format("%.2f", item.amount)}", 0)
            }

            printSeparator()
            val unpaid = (totalAmount - paidAmount).coerceAtLeast(0.0)
            printText("合计项: ${items.size} 项", 0)
            printTextWithFont("应收: ¥${String.format("%.2f", totalAmount)}", 0, 32)
            printText("已收: ¥${String.format("%.2f", paidAmount)}", 0)
            printTextWithFont("欠款: ¥${String.format("%.2f", unpaid)}", 0, 32)

            printText("---", 1)
            printText("冷库宝管理系统", 1)

            val success = commitPrintWithCallback()
            if (success) {
                try {
                    sunmiPrinterService!!.cutPaper(null)
                } catch (_: Exception) {
                }
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                sunmiPrinterService!!.exitPrinterBuffer(false)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            false
        }
    }

    /**
     * 打印报账单（销售出库单）
     */
    @SuppressLint("DefaultLocale")
    suspend fun printSaleBill(
        billNo: String,
        customerName: String,
        locationName: String,
        operatorName: String,
        items: List<SaleItemPrint>,
        totalAmount: Double,
        totalQuantity: Int,
        remark: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d("SunmiPrintService", "开始打印报账单，检查打印机连接")

        val connected = ensureConnectedForPrint()
        if (!connected) {
            throw Exception("打印机未连接，请先初始化")
        }

        try {
            sunmiPrinterService!!.enterPrinterBuffer(true)

            val configManager = ConfigManager(context)
            val companyName = configManager.getCompanyName() ?: "平伟冷藏库"
            printText(companyName, 1)
            printText("报账单", 1)
            printSeparator()

            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            printText("单据号: $billNo", 0)
            printText("时间: $currentTime", 0)
            printText("客户: $customerName", 0)
            printText("库位: $locationName", 0)
            printText("经手人: $operatorName", 0)
            if (remark.isNotBlank()) {
                printText("备注: $remark", 0)
            }

            printSeparator()
            printTextWithFont("型号   数量  单价", 0, 32)
            printSeparator()

            items.forEach { item ->
                val line = String.format(
                    "%-6s %-4d ¥%.1f",
                    item.productName.take(6),
                    item.quantity,
                    item.unitPrice
                )
                printTextWithFont(line, 0, 32)
                printText("      小计: ¥${String.format("%.2f", item.amount)}", 0)
            }

            printSeparator()
            printText("合计项: ${items.size} 项", 0)
            printTextWithFont("总数量: $totalQuantity", 0, 32)
            printTextWithFont("总金额: ¥${String.format("%.2f", totalAmount)}", 0, 32)

            printText("---", 1)
            printText("冷库宝管理系统", 1)

            val success = commitPrintWithCallback()
            if (success) {
                try {
                    sunmiPrinterService!!.cutPaper(null)
                } catch (_: Exception) {
                }
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                sunmiPrinterService!!.exitPrinterBuffer(false)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            false
        }
    }

    /**
     * 分割文本以适应打印宽度
     */
    private fun splitTextForPrint(text: String, maxLength: Int): List<String> {
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (char in text) {
            if (currentLine.length >= maxLength) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder()
            }
            currentLine.append(char)
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }

    /**
     * 打印分隔线
     */
    private suspend fun printSeparator() {
        printText("--------------------------------", 1)
    }

    /**
     * 打印文本
     * @param alignment 0=左对齐, 1=居中, 2=右对齐
     */
    private suspend fun printText(text: String, alignment: Int) {
        try {
            // 设置对齐方式
            sunmiPrinterService?.setAlignment(alignment, null)
            // 打印文本（需要换行符才会立即打印）
            sunmiPrinterService?.printText("$text\n", null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 打印带有字体大小设置的文本
     * @param text 文本内容
     * @param alignment 对齐方式：0=左对齐, 1=居中, 2=右对齐
     * @param fontSize 字体大小：24=标准, 32=大号, 48=特大
     */
    private suspend fun printTextWithFont(text: String, alignment: Int, fontSize: Int = 32) {
        try {
            // 设置对齐方式
            sunmiPrinterService?.setAlignment(alignment, null)
            // 设置字体大小
            sunmiPrinterService?.setFontSize(fontSize.toFloat(), null)
            // 打印文本
            sunmiPrinterService?.printText("$text\n", null)
            // 恢复默认字体大小（24）
            sunmiPrinterService?.setFontSize(24F, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 打印表格行
     */
    private suspend fun printColumnsText(
        columns: Array<String>,
        widths: IntArray,
        aligns: IntArray
    ) {
        try {
            sunmiPrinterService?.printColumnsText(columns, widths, aligns, null)
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果列打印失败，回退到普通文本
            val line = columns.joinToString("  ")
            printText(line, 0)
        }
    }

    /**
     * 提交打印并等待回调
     */
    private suspend fun commitPrintWithCallback(): Boolean = withContext(Dispatchers.IO) {
        // 1. 正确创建CompletableDeferred（已导入协程API）
        val result = CompletableDeferred<Boolean>()

        // 2. 正确实现InnerResultCallback，移除多余的.Stub()（客户端直接实现接口即可）
        val callback = object : InnerResultCallback() {
            override fun onRunResult(isSuccess: Boolean) {
                // 命令执行结果（不是实际打印结果）
            }

            override fun onReturnString(resultMsg: String) {
                // 查询结果返回
            }

            override fun onRaiseException(code: Int, msg: String) {
                // 异常信息
                if (!result.isCompleted) {
                    result.complete(false)
                }
            }

            override fun onPrintResult(code: Int, msg: String) {
                // 实际打印结果
                if (!result.isCompleted) {
                    result.complete(code == 0)
                }
            }
        }

        try {
            // 3. 传入正确的回调实例，无类型不匹配问题
            sunmiPrinterService!!.commitPrinterBufferWithCallback(callback)
            // 4. 使用协程内置withTimeoutOrNull，无需自定义（已导入）
            withTimeoutOrNull(5000) {
                result.await()
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 测试打印
     */
    suspend fun testPrint(): Boolean = withContext(Dispatchers.IO) {
        Log.d("SunmiPrintService", "开始测试打印，检查打印机连接")

        // 确保打印机已连接
        val connected = ensureConnectedForPrint()
        if (!connected) {
            Log.e("SunmiPrintService", "测试打印失败：打印机未连接")
            throw Exception("打印机未连接")
        }

        try {
            sunmiPrinterService!!.enterPrinterBuffer(true)

            printText("打印机测试页", 1)
            printSeparator()

            // 测试大字体
            printTextWithFont("大字体测试", 1, 32)
            printText("标准字体测试", 1)

            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            printText("时间: $currentTime", 0)

            printSeparator()

            // 测试紧凑布局
            printText("企业名称", 1)
            printText("入库单", 1)
            printSeparator()
            printText("单据号: TEST123", 0)
            printText("客户: 测试客户", 0)
            printText("库位: A-01", 0)
            printSeparator()
            printTextWithFont("商品A", 0, 32)
            printTextWithFont("100.00", 2, 32)
            printSeparator()
            printTextWithFont("合计: ¥500.00", 2, 32)

            val success = commitPrintWithCallback()

            if (success) {
                try {
                    sunmiPrinterService!!.cutPaper(null)
                } catch (e: Exception) {
                    // 忽略切刀错误
                }
            }

            success
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                sunmiPrinterService!!.exitPrinterBuffer(false)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            false
        }
    }

    /**
     * 断开打印机连接（安全版）
     */
    fun disconnect() {
        try {
            // 只有在已连接的情况下才尝试断开
            if (isConnected || sunmiPrinterService != null) {
                InnerPrinterManager.getInstance().unBindService(context, innerPrinterCallback)
            }
            sunmiPrinterService = null
            isConnected = false
            saveConnectionState(false) // 保存断开状态
            Log.d("SunmiPrintService", "打印机已断开，状态已保存")
        } catch (e: IllegalArgumentException) {
            // 忽略 "Service not registered" 错误
            Log.w("SunmiPrintService", "断开连接时出现异常（可能是重复断开）: ${e.message}")
        } catch (e: Exception) {
            Log.e("SunmiPrintService", "断开连接失败: ${e.message}", e)
        }
    }

    /**
     * 打印扣款单
     */
    suspend fun printDeductionBill(
        customerName: String,
        customerNo: String,
        quantity: Int,
        unitPrice: Double,
        amount: Double,
        reason: String,
        handler: String,
        deductDate: String
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d("SunmiPrintService", "开始打印扣款单: $customerName ($customerNo)")

        val connected = ensureConnectedForPrint()
        if (!connected) {
            Log.e("SunmiPrintService", "打印失败：打印机未连接")
            throw Exception("打印机未连接，请先初始化")
        }

        try {
            sunmiPrinterService!!.enterPrinterBuffer(true)

            val configManager = ConfigManager(context)
            val companyName = configManager.getCompanyName() ?: "平伟冷藏库"
            printText(companyName, 1)
            printText("扣款单", 1)
            printSeparator()

            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            printText("时间: $currentTime", 0)
            printText("日期: $deductDate", 0)
            printText("客户: $customerName", 0)
            printText("编号: $customerNo", 0)
            printText("经手人: $handler", 0)

            printSeparator()
            printTextWithFont("数量   单价   金额", 0, 32)
            printSeparator()

            val lineContent = String.format(
                "%-4d  ¥%-5.2f  ¥%-6.2f",
                quantity,
                unitPrice,
                amount
            )
            printTextWithFont(lineContent, 0, 32)

            if (reason.isNotBlank()) {
                printSeparator()
                printText("事由: $reason", 0)
            }

            printSeparator()
            printTextWithFont("扣款金额: ¥${String.format("%.2f", amount)}", 0, 32)
            printText("---", 1)
            printText("冷库宝管理系统", 1)

            val success = commitPrintWithCallback()
            if (success) {
                try {
                    sunmiPrinterService!!.cutPaper(null)
                } catch (e: Exception) {
                    // 忽略切刀错误
                }
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                sunmiPrinterService!!.exitPrinterBuffer(false)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            false
        }
    }

    /**
     * 打印简单的文本标签（备用方案，不依赖图片）
     */
    suspend fun printSimpleCustomerLabel(
        customerName: String,
        customerNo: String,
        phone: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d("SunmiPrintService", "开始打印简单客户标签: $customerName ($customerNo)")

        val connected = ensureConnectedForPrint()
        if (!connected) {
            Log.e("SunmiPrintService", "打印失败：打印机未连接")
            throw Exception("打印机未连接")
        }

        try {
            sunmiPrinterService!!.enterPrinterBuffer(true)

            // 企业信息
            val configManager = ConfigManager(context)
            val companyName = configManager.getCompanyName() ?: "平伟冷藏库"
            printText(companyName, 1)

            // 标签标题
            printText("客户标识标签", 1)
            printSeparator()

            // 打印时间
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            printText("时间: $currentTime", 0)

            // 客户信息（大字体）
            printTextWithFont(customerName, 1, 32)
            printText("编号: $customerNo", 0)

            phone?.takeIf { it.isNotBlank() }?.let {
                printText("电话: $it", 0)
            }

            printSeparator()

            // 底部信息
            printText("冷库宝管理系统", 1)

            // 提交打印
            val success = commitPrintWithCallback()

            // 切纸
            if (success) {
                try {
                    sunmiPrinterService!!.cutPaper(null)
                } catch (e: Exception) {
                    // 忽略
                }
            }

            return@withContext success

        } catch (e: Exception) {
            Log.e("SunmiPrintService", "打印简单标签失败: ${e.message}", e)
            try {
                sunmiPrinterService?.exitPrinterBuffer(false)
            } catch (ex: Exception) {
                // 忽略
            }
            return@withContext false
        }
    }
}
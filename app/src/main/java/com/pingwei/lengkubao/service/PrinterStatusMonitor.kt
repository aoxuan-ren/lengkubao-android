package com.pingwei.lengkubao.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 打印机状态监控器
 * 通过广播监听打印机状态变化
 */
class PrinterStatusMonitor(context: Context) {

    private val _printerStatus = MutableStateFlow<PrinterStatus>(PrinterStatus.UNKNOWN)
    val printerStatus: StateFlow<PrinterStatus> = _printerStatus

    private val context = context.applicationContext

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // 打印机正常
                "woyou.aidservice.juiv5.NORMAL_ACTION" -> {
                    _printerStatus.value = PrinterStatus.NORMAL
                }
                // 缺纸
                "woyou.aidservice.juiv5.OUT_OF_PAPER_ACTION" -> {
                    _printerStatus.value = PrinterStatus.OUT_OF_PAPER
                }
                // 过热
                "woyou.aidservice.juiv5.OVER_HEATING_ACTION" -> {
                    _printerStatus.value = PrinterStatus.OVER_HEAT
                }
                // 开盖
                "woyou.aidservice.juiv5.COVER_OPEN_ACTION" -> {
                    _printerStatus.value = PrinterStatus.COVER_OPEN
                }
                // 切刀异常
                "woyou.aidservice.juiv5.KNIFE_ERROR_ACTION_1" -> {
                    _printerStatus.value = PrinterStatus.CUTTER_ERROR
                }
                "woyou.aidservice.juiv5.KNIFE_ERROR_ACTION_2" -> {
                    _printerStatus.value = PrinterStatus.CUTTER_RECOVERED
                }
                // 未发现打印机
                "woyou.aidservice.juiv5.PRINTER_NON_EXISTENT_ACTION" -> {
                    _printerStatus.value = PrinterStatus.NOT_EXIST
                }
                // 准备中
                "woyou.aidservice.juiv5.INIT_ACTION" -> {
                    _printerStatus.value = PrinterStatus.PREPARING
                }
            }
        }
    }

    /**
     * 开始监控
     */
    fun startMonitoring() {
        val filter = IntentFilter().apply {
            addAction("woyou.aidservice.juiv5.NORMAL_ACTION")
            addAction("woyou.aidservice.juiv5.OUT_OF_PAPER_ACTION")
            addAction("woyou.aidservice.juiv5.OVER_HEATING_ACTION")
            addAction("woyou.aidservice.juiv5.COVER_OPEN_ACTION")
            addAction("woyou.aidservice.juiv5.KNIFE_ERROR_ACTION_1")
            addAction("woyou.aidservice.juiv5.KNIFE_ERROR_ACTION_2")
            addAction("woyou.aidservice.juiv5.PRINTER_NON_EXISTENT_ACTION")
            addAction("woyou.aidservice.juiv5.INIT_ACTION")
        }

    }

    /**
     * 停止监控
     */
    fun stopMonitoring() {
        try {
            context.unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            // 忽略未注册的错误
        }
    }

    /**
     * 获取状态描述
     */
    fun getStatusDescription(status: PrinterStatus): String {
        return when (status) {
            PrinterStatus.NORMAL -> "打印机正常"
            PrinterStatus.PREPARING -> "打印机准备中"
            PrinterStatus.OUT_OF_PAPER -> "缺纸"
            PrinterStatus.OVER_HEAT -> "打印头过热"
            PrinterStatus.COVER_OPEN -> "打印机开盖"
            PrinterStatus.CUTTER_ERROR -> "切刀异常"
            PrinterStatus.CUTTER_RECOVERED -> "切刀已恢复"
            PrinterStatus.NOT_EXIST -> "未检测到打印机"
            else -> "未知状态"
        }
    }
}

/**
 * 打印机状态枚举
 */
enum class PrinterStatus {
    UNKNOWN,
    NORMAL,
    PREPARING,
    OUT_OF_PAPER,
    OVER_HEAT,
    COVER_OPEN,
    CUTTER_ERROR,
    CUTTER_RECOVERED,
    NOT_EXIST
}
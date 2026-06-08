package com.pingwei.lengkubao.utils

import com.journeyapps.barcodescanner.ScanOptions

object ScannerUtils {
    // 初始化扫码配置（仅识别二维码）
    fun getQrScanOptions(): ScanOptions {
        return ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("请扫描客户二维码")
            setCameraId(0) // 后置摄像头
            setBeepEnabled(true) // 扫码成功蜂鸣
            setBarcodeImageEnabled(false) // 不保存扫码图片
            setOrientationLocked(true) // 锁定竖屏
        }
    }

    // 解析扫码结果（假设二维码内容为"客户编号,客户名称"格式）
    fun parseCustomerQr(result: String): Pair<String, String>? {
        return try {
            val parts = result.split(",")
            if (parts.size >= 2) Pair(parts[0], parts[1]) else null
        } catch (e: Exception) {
            null
        }
    }
}
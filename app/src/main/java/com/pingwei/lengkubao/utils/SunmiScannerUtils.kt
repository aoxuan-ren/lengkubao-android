// utils/ZXingScannerUtils.kt
package com.pingwei.lengkubao.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pingwei.lengkubao.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ZXing扫码工具类（摄像头扫码）
 * 参考CustomerListActivity的实现
 */
object ZXingScannerUtils {
    private const val TAG = "ZXingScannerUtils"

    /**
     * 创建扫码选项
     */
    fun createScanOptions(
        prompt: String = "请扫描客户二维码",
        beepEnabled: Boolean = true,
        cameraId: Int = 0 // 后置摄像头
    ): ScanOptions {
        return ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(prompt)
            setCameraId(cameraId)
            setBeepEnabled(beepEnabled)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }
    }

    /**
     * 从扫码结果中提取客户编号（适配多种格式）
     * 参考CustomerListActivity.extractCustomerNoFromQrContent方法
     */
    fun extractCustomerNoFromScanResult(scannedContent: String): String? {
        val trimmed = scannedContent.trim()
        if (trimmed.isBlank()) return null

        Log.d(TAG, "处理扫码内容: $trimmed")

        // 格式1：中文括号 "客户名称（KH001）"
        val chinesePattern = Regex("（([^）]+)）")
        val chineseMatch = chinesePattern.find(trimmed)
        if (chineseMatch != null) {
            val result = chineseMatch.groupValues[1].trim()
            Log.d(TAG, "匹配中文括号: $result")
            if (result.startsWith("KH")) {
                return result
            }
        }

        // 格式2：英文括号 "客户名称(KH001)"
        val englishPattern = Regex("\\(([^)]+)\\)")
        val englishMatch = englishPattern.find(trimmed)
        if (englishMatch != null) {
            val result = englishMatch.groupValues[1].trim()
            Log.d(TAG, "匹配英文括号: $result")
            if (result.startsWith("KH")) {
                return result
            }
        }

        // 格式3：纯客户编号（以KH开头）
        if (trimmed.startsWith("KH") && trimmed.length >= 5) { // KH001 长度是5
            Log.d(TAG, "匹配纯编号格式: $trimmed")
            return trimmed
        }

        // 格式4：尝试查找KH开头的部分
        val khPattern = Regex("KH\\d+")
        val khMatch = khPattern.find(trimmed)
        if (khMatch != null) {
            val result = khMatch.value
            Log.d(TAG, "匹配KH开头编号: $result")
            return result
        }

        // 格式5：旧格式兼容 - 尝试查找C开头的部分
        val cPattern = Regex("C\\d+")
        val cMatch = cPattern.find(trimmed)
        if (cMatch != null) {
            val oldNo = cMatch.value
            val newNo = oldNo.replaceFirst("C", "KH")
            Log.d(TAG, "转换旧格式: $oldNo -> $newNo")
            return newNo
        }

        Log.d(TAG, "未匹配到任何格式")
        return null
    }

    /**
     * 根据客户编号查询客户信息
     */
    suspend fun findCustomerByNo(context: Context, customerNo: String) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            db.customerDao().getByCustomerNo(customerNo)
        } catch (e: Exception) {
            Log.e(TAG, "查询客户失败", e)
            null
        }
    }
}
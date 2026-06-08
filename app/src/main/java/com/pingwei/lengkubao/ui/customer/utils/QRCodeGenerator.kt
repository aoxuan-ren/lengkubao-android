package com.pingwei.lengkubao.ui.customer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.util.HashMap

object QRCodeGenerator {
    private const val QR_CODE_SIZE = 400 // 二维码图片大小
    private const val DIRECTORY_NAME = "LengKuBao/QRCode" // 二维码保存目录

    /**
     * 生成客户二维码
     * @param customerName 客户名称
     * @param customerNo 客户编号
     * @param context Context
     * @return 二维码文件路径，如果生成失败返回null
     */
    fun generateCustomerQRCode(
        customerName: String,
        customerNo: String,
        context: Context
    ): String? {
        try {
            // 1. 生成二维码内容：客户名称（编号）
            val qrContent = "$customerName（$customerNo）"

            // 2. 生成二维码Bitmap
            val bitmap = createQRCodeBitmap(qrContent, QR_CODE_SIZE)

            // 3. 保存为文件
            val filePath = saveQRCodeToFile(bitmap, customerNo, context)

            return filePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 重新生成二维码（更新已有的）
     */
    fun regenerateCustomerQRCode(
        customerName: String,
        customerNo: String,
        oldQrCodePath: String?,
        context: Context
    ): String? {
        try {
            // 删除旧二维码文件（如果存在）
            if (!oldQrCodePath.isNullOrBlank()) {
                val oldFile = File(oldQrCodePath)
                if (oldFile.exists()) {
                    oldFile.delete()
                }
            }

            // 生成新二维码
            return generateCustomerQRCode(customerName, customerNo, context)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 创建二维码Bitmap
     */
    private fun createQRCodeBitmap(content: String, size: Int): Bitmap {
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
        hints[EncodeHintType.MARGIN] = 1

        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            bitmap
        } catch (e: WriterException) {
            throw RuntimeException("生成二维码失败", e)
        }
    }

    /**
     * 保存二维码到文件
     */
    private fun saveQRCodeToFile(bitmap: Bitmap, customerNo: String, context: Context): String {
        // 创建保存目录
        val directory = getQRCodeDirectory(context)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // 生成文件名
        val fileName = "Customer_${customerNo}_${System.currentTimeMillis()}.png"
        val file = File(directory, fileName)

        // 保存文件
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        }

        return file.absolutePath
    }

    /**
     * 获取二维码保存目录
     */
    private fun getQRCodeDirectory(context: Context): File {
        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            // 外部存储
            File(context.getExternalFilesDir(null), DIRECTORY_NAME)
        } else {
            // 内部存储
            File(context.filesDir, DIRECTORY_NAME)
        }
    }

    /**
     * 删除二维码文件
     */
    fun deleteQRCodeFile(qrCodePath: String?): Boolean {
        if (qrCodePath.isNullOrBlank()) return true

        return try {
            val file = File(qrCodePath)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
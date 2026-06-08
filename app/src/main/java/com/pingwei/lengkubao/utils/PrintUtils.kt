package com.pingwei.lengkubao.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.pingwei.lengkubao.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 打印工具类
 */
object PrintUtils {

    /**
     * 格式化金额
     */
    fun formatAmount(amount: Double): String {
        val formatter = DecimalFormat("#,##0.00")
        return "¥${formatter.format(amount)}"
    }

    /**
     * 格式化日期
     */
    fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        return formatter.format(date)
    }

    fun formatDateShort(date: Date): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        return formatter.format(date)
    }

    /**
     * 生成单据号
     */
    fun generateBillNo(prefix: String): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
        val randomPart = (100000..999999).random().toString()
        return "$prefix-$datePart-$randomPart"
    }

    /**
     * 创建公司Logo位图
     */
    fun createCompanyLogoBitmap(context: Context): Bitmap? {
        return try {
            // 你可以替换为自己的logo资源
            ResourcesCompat.getDrawable(context.resources, R.mipmap.ic_launcher, null)?.let { drawable ->
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 创建文本位图（用于打印自定义字体）
     */
    fun createTextBitmap(text: String, textSize: Int = 24, textColor: Int = Color.BLACK): Bitmap {
        val paint = Paint().apply {
            this.color = textColor
            this.textSize = textSize.toFloat()
            this.typeface = Typeface.DEFAULT_BOLD
            this.isAntiAlias = true
        }

        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val bitmap = Bitmap.createBitmap(
            bounds.width() + 10,
            bounds.height() + 10,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawText(text, 5f, bounds.height().toFloat() + 5, paint)

        return bitmap
    }

    /**
     * 计算文本宽度（用于表格列宽）
     */
    fun calculateTextWidth(text: String, charWidth: Int = 12): Int {
        // 中文算2个字符宽度
        val chineseCount = text.count { it.toString().matches(Regex("[\u4e00-\u9fa5]")) }
        val otherCount = text.length - chineseCount
        return chineseCount * 2 + otherCount
    }

    /**
     * 分割长文本以适应打印宽度
     */
    fun splitTextForPrint(text: String, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentWidth = 0

        for (char in text) {
            val charWidth = if (char.toString().matches(Regex("[\u4e00-\u9fa5]"))) 2 else 1

            if (currentWidth + charWidth > maxWidth) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder()
                currentWidth = 0
            }

            currentLine.append(char)
            currentWidth += charWidth
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }

    /**
     * 获取单据类型前缀
     */
    fun getBillPrefix(billType: String): String {
        return when (billType) {
            "sale" -> "XS"
            "instock" -> "RK"
            "packaging" -> "BZ"
            else -> "QT"
        }
    }

    /**
     * 验证打印数据
     */
    fun validatePrintData(
        customerName: String,
        items: List<Any>,
        totalAmount: Double? = null
    ): Pair<Boolean, String?> {
        if (customerName.isBlank()) {
            return Pair(false, "客户名称不能为空")
        }

        if (items.isEmpty()) {
            return Pair(false, "明细不能为空")
        }

        totalAmount?.let {
            if (it <= 0) {
                return Pair(false, "金额必须大于0")
            }
        }

        return Pair(true, null)
    }
}
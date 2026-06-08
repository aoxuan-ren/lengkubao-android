package com.pingwei.lengkubao.utils

import android.content.Context
import android.widget.Toast

object ToastUtil {
    fun show(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
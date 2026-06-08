package com.pingwei.lengkubao.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * 权限工具类（统一权限检查逻辑）
 */
object PermissionUtils {
    /**
     * 检查单个权限是否已授予
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查多个权限是否全部授予
     */
    fun areAllPermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { isPermissionGranted(context, it) }
    }

    /**
     * 获取未授予的权限列表
     */
    fun getDeniedPermissions(context: Context, permissions: Array<String>): Array<String> {
        return permissions.filter { !isPermissionGranted(context, it) }.toTypedArray()
    }
}
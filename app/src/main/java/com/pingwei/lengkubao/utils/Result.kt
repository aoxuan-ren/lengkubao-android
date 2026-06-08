//com.pingwei.lengkubao.utils.Result
package com.pingwei.lengkubao.utils

// 原类名 Result → 改为 BizResult（或 AppResult/ApiResult，避免与其他 Result 重名）
sealed class BizResult<out T> { // 重命名此处
    data class Success<out T>(val data: T) : BizResult<T>()
    data class Failure(val exception: Exception) : BizResult<Nothing>()

    val isSuccess: Boolean
        get() = this is Success

    fun exceptionOrNull(): Exception? {
        return (this as? Failure)?.exception
    }

    companion object {
        fun <T> success(data: T): BizResult<T> = Success(data)
        fun failure(exception: Exception): BizResult<Nothing> = Failure(exception)
    }
}
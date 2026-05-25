package com.sovexis.mobile.core.result

/**
 * 统一资源封装，表示操作结果状态
 */
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val code: Int? = null, val message: String, val throwable: Throwable? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}

/**
 * 获取成功数据，如果失败则抛出异常
 */
fun <T> Resource<T>.getOrThrow(): T {
    return when (this) {
        is Resource.Success -> data
        is Resource.Error -> throw throwable ?: RuntimeException(message)
        is Resource.Loading -> throw IllegalStateException("Resource is still loading")
    }
}

/**
 * 获取成功数据，如果失败则返回 null
 */
fun <T> Resource<T>.getOrNull(): T? {
    return when (this) {
        is Resource.Success -> data
        else -> null
    }
}

/**
 * åˆ†é¡µæ•°æ®å°è£…
 */
data class PagedData<T>(
    val items: List<T>,
    val currentPage: Int,
    val totalPages: Int,
    val hasMore: Boolean
)

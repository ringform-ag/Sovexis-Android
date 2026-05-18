package com.sovexis.mobile.core.result

/**
 * 统一资源封装，表示操作结果状�? */
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val code: Int? = null, val message: String, val throwable: Throwable? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}

/**
 * 分页数据封装
 */
data class PagedData<T>(
    val items: List<T>,
    val currentPage: Int,
    val totalPages: Int,
    val hasMore: Boolean
)

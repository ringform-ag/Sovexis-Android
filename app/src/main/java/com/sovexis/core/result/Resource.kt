package com.sovexis.core.result

sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val code: Int? = null, val message: String, val throwable: Throwable? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}

fun <T> Resource<T>.getOrThrow(): T {
    return when (this) {
        is Resource.Success -> data
        is Resource.Error -> throw throwable ?: RuntimeException(message)
        is Resource.Loading -> throw IllegalStateException("Resource is still loading")
    }
}

fun <T> Resource<T>.getOrNull(): T? {
    return when (this) {
        is Resource.Success -> data
        else -> null
    }
}

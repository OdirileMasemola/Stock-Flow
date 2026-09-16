package com.example.stockflow.data.local.cache

/**
 * Result of a READ that may come from the network or the local Room cache.
 * Network (Fresh) always wins when online; Cached is only used on IO/unreachable failures.
 */
sealed class CacheResult<out T> {
    data class Fresh<T>(val data: T) : CacheResult<T>()
    data class Cached<T>(val data: T, val cachedAt: Long) : CacheResult<T>()
    data object Empty : CacheResult<Nothing>()
    data class Error(val message: String) : CacheResult<Nothing>()

    val fromCache: Boolean
        get() = this is Cached

    val cachedAtOrNull: Long?
        get() = (this as? Cached)?.cachedAt

    fun getOrNull(): T? = when (this) {
        is Fresh -> data
        is Cached -> data
        Empty, is Error -> null
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is Error -> Exception(message)
        Empty -> Exception(EMPTY_MESSAGE)
        else -> null
    }

    /** Adapter for call sites that still expect [Result]. Cached/Fresh become success. */
    fun toResult(): Result<@UnsafeVariance T> = when (this) {
        is Fresh -> Result.success(data)
        is Cached -> Result.success(data)
        Empty -> Result.failure(Exception(EMPTY_MESSAGE))
        is Error -> Result.failure(Exception(message))
    }

    companion object {
        const val EMPTY_MESSAGE = "offline_no_cached_data"
    }
}

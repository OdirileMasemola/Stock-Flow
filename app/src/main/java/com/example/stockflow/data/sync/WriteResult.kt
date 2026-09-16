package com.example.stockflow.data.sync

/**
 * Outcome of a mutating repository call that may complete online or be queued offline.
 */
sealed class WriteResult<out T> {
    data class Synced<T>(val data: T) : WriteResult<T>()
    data class Queued<T>(val data: T) : WriteResult<T>()
    data class Failed(val message: String) : WriteResult<Nothing>()

    val isSuccess: Boolean get() = this is Synced || this is Queued
    val savedOffline: Boolean get() = this is Queued

    fun getOrNull(): T? = when (this) {
        is Synced -> data
        is Queued -> data
        is Failed -> null
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is Failed -> Exception(message)
        else -> null
    }
}

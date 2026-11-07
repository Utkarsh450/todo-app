package com.utkarsh.todo.core.common

import kotlinx.coroutines.CancellationException

/**
 * Runs [block] and wraps the outcome in a [Result], but - unlike [runCatching] - rethrows
 * [CancellationException] so coroutine cancellation is never swallowed.
 */
inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

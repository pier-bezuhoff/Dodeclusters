package domain

import kotlinx.coroutines.CancellationException

/** alternative to [runCatching] that catches only exceptions,
 * satisfying [exceptionFilter], non-cancellation [Exception]s by default */
inline fun <T, R> T.runCatchingOnly(
    crossinline exceptionFilter: (Throwable) -> Boolean = { e ->
        e is Exception && e !is CancellationException
    },
    block: T.() -> R,
): Result<R> {
    return try {
        Result.success(block())
    } catch (e: Throwable) {
        if (exceptionFilter(e))
            Result.failure(e)
        else
            throw e
    }
}

/** alternative to [recoverCatching] that catches only exceptions,
 * satisfying [exceptionFilter], non-cancellation [Exception]s by default */
inline fun <R, T : R> Result<T>.recoverCatchingOnly(
    crossinline exceptionFilter: (Throwable) -> Boolean = { e ->
        e is Exception && e !is CancellationException
    },
    transform: (exception: Throwable) -> R
): Result<R> {
    return when (val exception = exceptionOrNull()) {
        null -> this
        else -> runCatchingOnly(exceptionFilter) {
            transform(exception)
        }
    }
}

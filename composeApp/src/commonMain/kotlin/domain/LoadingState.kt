package domain

import androidx.compose.runtime.Immutable

/** InProgress | Completed | Error */
@Immutable // unless T is mutable
sealed interface LoadingState<out T> {
    data class Completed<out T>(val result: T) : LoadingState<T>
    data class InProgress(val message: String? = null) : LoadingState<Nothing>
    data class Error(val exception: Throwable) : LoadingState<Nothing>
}
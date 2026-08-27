package domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.extensions.cached
import io.github.xxfast.kstore.utils.ExperimentalKStoreApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.reflect.KProperty

context(viewModel: ViewModel)
fun <T> Flow<T>.stateInWhileSubscribed(initialValue: T): StateFlow<T> =
    stateIn(
        viewModel.viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        initialValue
    )

@OptIn(ExperimentalKStoreApi::class)
context(viewModel: ViewModel)
fun <T : Any> KStore<T>.updatesStateFlow(default: T): StateFlow<T> =
    this.updates
        .map { it ?: default }
        .stateIn(
            viewModel.viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            this.cached ?: default
        )

// getValue is inline for State and MutableState too.
// this might be suboptimal somehow? not sure
@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> StateFlow<T>.getValue(
    thisObj: Any?,
    property: KProperty<*>,
): T =
    this.value

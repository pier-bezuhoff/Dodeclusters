package domain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withCompositionLocal
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.extensions.cached
import io.github.xxfast.kstore.utils.ExperimentalKStoreApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
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

// reference: https://www.youtube.com/watch?v=njchj9d_Lf8
@Suppress("ComposableNaming")
@Composable
fun <T> Flow<T>?.collectWithLifecycle(
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEvent: suspend (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(this, lifecycleOwner.lifecycle) {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            // switching to Main.immediate prevents losing events in very rare cases
            // during configuration changes, idc tho
//            withContext(Dispatchers.Main.immediate) {
                this@collectWithLifecycle?.collect(onEvent)
//            }
        }
    }
}

@Suppress("ComposableNaming")
@Composable
fun <T> Flow<T>?.collectLatestWithLifecycle(
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEvent: suspend (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(this, lifecycleOwner.lifecycle) {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
//            withContext(Dispatchers.Main.immediate) {
                this@collectLatestWithLifecycle?.collectLatest(onEvent)
//            }
        }
    }
}

// getValue is inline for State and MutableState too.
// this might be suboptimal somehow? not sure
@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> StateFlow<T>.getValue(
    thisObj: Any?,
    property: KProperty<*>,
): T =
    this.value

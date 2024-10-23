package ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

// i had to reimplement TooltipState to
// be able to change tooltip duration... NotLikeThis

/**
 * Create and remember the default [TooltipState] for [TooltipBox].
 *
 * @param initialIsVisible the initial value for the tooltip's visibility when drawn.
 * @param isPersistent [Boolean] that determines if the tooltip associated with this
 * will be persistent or not. If isPersistent is true, then the tooltip will
 * only be dismissed when the user clicks outside the bounds of the tooltip or if
 * [TooltipState.dismiss] is called. When isPersistent is false, the tooltip will dismiss after
 * a short duration. Ideally, this should be set to true when there is actionable content
 * being displayed within a tooltip.
 * @param mutatorMutex [MutatorMutex] used to ensure that for all of the tooltips associated
 * with the mutator mutex, only one will be shown on the screen at any time.
 *
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@ExperimentalMaterial3Api
fun rememberMyTooltipState(
    initialIsVisible: Boolean = false,
    isPersistent: Boolean = false,
    /** Tooltip duration in milliseconds */
    tooltipDuration: Long = androidx.compose.foundation.BasicTooltipDefaults.TooltipDuration,
    mutatorMutex: MutatorMutex = androidx.compose.foundation.BasicTooltipDefaults.GlobalMutatorMutex
): TooltipState =
    remember(
        isPersistent,
        mutatorMutex
    ) {
        MyTooltipStateImpl(
            initialIsVisible = initialIsVisible,
            isPersistent = isPersistent,
            tooltipDuration = tooltipDuration,
            mutatorMutex = mutatorMutex
        )
    }

/**
 * Constructor extension function for [TooltipState]
 *
 * @param initialIsVisible the initial value for the tooltip's visibility when drawn.
 * @param isPersistent [Boolean] that determines if the tooltip associated with this
 * will be persistent or not. If isPersistent is true, then the tooltip will
 * only be dismissed when the user clicks outside the bounds of the tooltip or if
 * [TooltipState.dismiss] is called. When isPersistent is false, the tooltip will dismiss after
 * a short duration. Ideally, this should be set to true when there is actionable content
 * being displayed within a tooltip.
 * @param mutatorMutex [MutatorMutex] used to ensure that for all of the tooltips associated
 * with the mutator mutex, only one will be shown on the screen at any time.
 */
@OptIn(ExperimentalFoundationApi::class)
@ExperimentalMaterial3Api
fun MyTooltipState(
    initialIsVisible: Boolean = false,
    isPersistent: Boolean = true,
    /** Tooltip duration in milliseconds */
    tooltipDuration: Long = androidx.compose.foundation.BasicTooltipDefaults.TooltipDuration,
    mutatorMutex: MutatorMutex = androidx.compose.foundation.BasicTooltipDefaults.GlobalMutatorMutex,
): TooltipState =
    MyTooltipStateImpl(
        initialIsVisible = initialIsVisible,
        isPersistent = isPersistent,
        tooltipDuration = tooltipDuration,
        mutatorMutex = mutatorMutex
    )

@OptIn(ExperimentalMaterial3Api::class)
@Stable
private class MyTooltipStateImpl(
    initialIsVisible: Boolean,
    override val isPersistent: Boolean,
    /** Tooltip duration in milliseconds */
    private val tooltipDuration: Long,
    private val mutatorMutex: MutatorMutex
) : TooltipState {
    override val transition: MutableTransitionState<Boolean> =
        MutableTransitionState(initialIsVisible)

    override val isVisible: Boolean
        get() = transition.currentState || transition.targetState

    /**
     * continuation used to clean up
     */
    private var job: (CancellableContinuation<Unit>)? = null

    /**
     * Show the tooltip associated with the current [BasicTooltipState].
     * When this method is called, all of the other tooltips associated
     * with [mutatorMutex] will be dismissed.
     *
     * @param mutatePriority [MutatePriority] to be used with [mutatorMutex].
     */
    override suspend fun show(
        mutatePriority: MutatePriority
    ) {
        val cancellableShow: suspend () -> Unit = {
            suspendCancellableCoroutine { continuation ->
                transition.targetState = true
                job = continuation
            }
        }

        // Show associated tooltip for [TooltipDuration] amount of time
        // or until tooltip is explicitly dismissed depending on [isPersistent].
        mutatorMutex.mutate(mutatePriority) {
            try {
                if (isPersistent) {
                    cancellableShow()
                } else {
                    withTimeout(tooltipDuration) {
                        cancellableShow()
                    }
                }
            } finally {
                // timeout or cancellation has occurred
                // and we close out the current tooltip.
                dismiss()
            }
        }
    }

    /**
     * Dismiss the tooltip associated with
     * this [TooltipState] if it's currently being shown.
     */
    override fun dismiss() {
        transition.targetState = false
    }

    /**
     * Cleans up [mutatorMutex] when the tooltip associated
     * with this state leaves Composition.
     */
    override fun onDispose() {
        job?.cancel()
    }
}

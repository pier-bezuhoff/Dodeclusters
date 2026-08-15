package ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scrim
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ui.MyDrawerState.Companion.Saver
import kotlin.math.roundToInt

/**
 * The same as DrawerState, but re-exposes internals for CustomModalNavigationDrawer.
 *
 * State of the [ModalNavigationDrawer] and [DismissibleNavigationDrawer] composable.
 *
 * @param initialValue The initial value of the state.
 * @param confirmStateChange Optional callback invoked to confirm or veto a pending state change.
 */
@Suppress("NotCloseable")
@Stable
class MyDrawerState(
    initialValue: DrawerValue,
    internal val confirmStateChange: (DrawerValue) -> Boolean = { true },
) {

    internal var anchoredDraggableMotionSpec: FiniteAnimationSpec<Float> =
        TweenSpec(durationMillis = 256)

    @Suppress("Deprecation")
    internal val anchoredDraggableState =
        AnchoredDraggableState(
            initialValue = initialValue,
            snapAnimationSpec = anchoredDraggableMotionSpec,
            decayAnimationSpec = AnchoredDraggableDefaults.DecayAnimationSpec,
            confirmValueChange = confirmStateChange,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(requireDensity()) { 400.dp.toPx() } },
        )

    /** Whether the drawer is open. */
    val isOpen: Boolean
        get() = currentValue == DrawerValue.Open

    /** Whether the drawer is closed. */
    val isClosed: Boolean
        get() = currentValue == DrawerValue.Closed

    /**
     * The current value of the state.
     *
     * If no swipe or animation is in progress, this corresponds to the start the drawer currently
     * in. If a swipe or an animation is in progress, this corresponds the state drawer was in
     * before the swipe or animation started.
     */
    val currentValue: DrawerValue
        get() {
            return anchoredDraggableState.settledValue
        }

    /** Whether the state is currently animating. */
    val isAnimationRunning: Boolean
        get() {
            return anchoredDraggableState.isAnimationRunning
        }

    /**
     * Open the drawer with animation and suspend until it if fully opened or animation has been
     * cancelled. This method will throw [CancellationException] if the animation is interrupted
     *
     * @return the reason the open animation ended
     */
    suspend fun open() =
        animateTo(targetValue = DrawerValue.Open, animationSpec = openDrawerMotionSpec)

    /**
     * Close the drawer with animation and suspend until it if fully closed or animation has been
     * cancelled. This method will throw [CancellationException] if the animation is interrupted
     *
     * @return the reason the close animation ended
     */
    suspend fun close() =
        animateTo(targetValue = DrawerValue.Closed, animationSpec = closeDrawerMotionSpec)

    /**
     * Set the state of the drawer with specific animation
     *
     * @param targetValue The new value to animate to.
     * @param anim The animation that will be used to animate to the new value.
     */
    @Deprecated(
        message =
            "This method has been replaced by the open and close methods. The animation " +
                    "spec is now an implementation detail of ModalDrawer."
    )
    suspend fun animateTo(targetValue: DrawerValue, anim: AnimationSpec<Float>) {
        animateTo(targetValue = targetValue, animationSpec = anim)
    }

    /**
     * Set the state without any animation and suspend until it's set
     *
     * @param targetValue The new target value
     */
    suspend fun snapTo(targetValue: DrawerValue) {
        anchoredDraggableState.snapTo(targetValue)
    }

    /**
     * The target value of the drawer state.
     *
     * If a swipe is in progress, this is the value that the Drawer would animate to if the swipe
     * finishes. If an animation is running, this is the target value of that animation. Finally, if
     * no swipe or animation is in progress, this is the same as the [currentValue].
     */
    val targetValue: DrawerValue
        get() = anchoredDraggableState.targetValue

    /**
     * The current position (in pixels) of the drawer sheet, or Float.NaN before the offset is
     * initialized.
     *
     * @see [AnchoredDraggableState.offset] for more information.
     */
    @Deprecated(
        message =
            "Please access the offset through currentOffset, which returns the value " +
                    "directly instead of wrapping it in a state object.",
        replaceWith = ReplaceWith("currentOffset"),
    )
    val offset: State<Float> =
        object : State<Float> {
            override val value: Float
                get() = anchoredDraggableState.offset
        }

    /**
     * The current position (in pixels) of the drawer sheet, or Float.NaN before the offset is
     * initialized.
     *
     * @see [AnchoredDraggableState.offset] for more information.
     */
    val currentOffset: Float
        get() = anchoredDraggableState.offset

    internal var density: Density? by mutableStateOf(null)

    internal var openDrawerMotionSpec: FiniteAnimationSpec<Float> = snap()

    internal var closeDrawerMotionSpec: FiniteAnimationSpec<Float> = snap()

    private fun requireDensity() =
        requireNotNull(density) {
            "The density on DrawerState ($this) was not set. Did you use DrawerState" +
                    " with the ModalNavigationDrawer or DismissibleNavigationDrawer composables?"
        }

    internal fun requireOffset(): Float = anchoredDraggableState.requireOffset()

    private suspend fun animateTo(
        targetValue: DrawerValue,
        animationSpec: AnimationSpec<Float>,
        velocity: Float = anchoredDraggableState.lastVelocity,
    ) {
        anchoredDraggableState.anchoredDrag(targetValue = targetValue) { anchors, latestTarget ->
            val targetOffset = anchors.positionOf(latestTarget)
            if (!targetOffset.isNaN()) {
                var prev = if (currentOffset.isNaN()) 0f else currentOffset
                animate(prev, targetOffset, velocity, animationSpec) { value, velocity ->
                    // Our onDrag coerces the value within the bounds, but an animation may
                    // overshoot, for example a spring animation or an overshooting interpolator
                    // We respect the user's intention and allow the overshoot, but still use
                    // DraggableState's drag for its mutex.
                    dragTo(value, velocity)
                    prev = value
                }
            }
        }
    }

    companion object {
        /** The default [Saver] implementation for [DrawerState]. */
        fun Saver(confirmStateChange: (DrawerValue) -> Boolean) =
            androidx.compose.runtime.saveable.Saver<MyDrawerState, DrawerValue>(
                save = { it.currentValue },
                restore = { MyDrawerState(it, confirmStateChange) },
            )
    }
}

/**
 * Create and [remember] a [DrawerState].
 *
 * @param initialValue The initial value of the state.
 * @param confirmStateChange Optional callback invoked to confirm or veto a pending state change.
 */
@Composable
fun rememberMyDrawerState(
    initialValue: DrawerValue,
    confirmStateChange: (DrawerValue) -> Boolean = { true },
): MyDrawerState {
    return rememberSaveable(saver = MyDrawerState.Saver(confirmStateChange)) {
        MyDrawerState(initialValue, confirmStateChange)
    }
}

/**
 * The same as the built-in ModalNavigationDrawer except it's not draggable
 *
 * [Material Design navigation drawer](https://m3.material.io/components/navigation-drawer/overview)
 *
 * Navigation drawers provide ergonomic access to destinations in an app.
 *
 * Modal navigation drawers block interaction with the rest of an app’s content with a scrim. They
 * are elevated above most of the app’s UI and don’t affect the screen’s layout grid.
 *
 * ![Navigation drawer
 * image](https://developer.android.com/images/reference/androidx/compose/material3/navigation-drawer.png)
 *
 * @param drawerContent content inside this drawer
 * @param modifier the [Modifier] to be applied to this drawer
 * @param drawerState state of the drawer
 * @param scrimColor color of the scrim that obscures content when the drawer is open
 * @param content content of the rest of the UI
 */
@Composable
fun CustomModalNavigationDrawer(
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawerState: MyDrawerState = rememberMyDrawerState(DrawerValue.Closed),
    scrimColor: Color = DrawerDefaults.scrimColor,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navigationMenu = "Navigation menu"
    val density = LocalDensity.current
    var anchorsInitialized by remember { mutableStateOf(false) }
    var minValue by remember(density) { mutableFloatStateOf(0f) }
    val maxValue = 0f
    val focusRequester = remember { FocusRequester() }

    val anchoredDraggableMotion: FiniteAnimationSpec<Float> =
        MaterialTheme.motionScheme.defaultSpatialSpec()
    val openMotion: FiniteAnimationSpec<Float> =
        MaterialTheme.motionScheme.defaultSpatialSpec()
    val closeMotion: FiniteAnimationSpec<Float> =
        MaterialTheme.motionScheme.fastEffectsSpec()

    SideEffect {
        drawerState.density = density
        drawerState.openDrawerMotionSpec = openMotion
        drawerState.closeDrawerMotionSpec = closeMotion
        drawerState.anchoredDraggableMotionSpec = anchoredDraggableMotion
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            // Keyboard focus should go to first element of the drawer when it opens
            focusRequester.requestFocus()
        }
    }

//    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            .fillMaxSize()
//            .anchoredDraggable(
//                state = drawerState.anchoredDraggableState,
//                orientation = Orientation.Horizontal,
//                enabled = gesturesEnabled,
//                reverseDirection = isRtl,
//            )
    ) {
        Box { content() }
        val onDismissRequest = {
            if (drawerState.confirmStateChange(DrawerValue.Closed)) {
                scope.launch { drawerState.close() }
            }
        }
        Scrim(
            contentDescription = "Close drawer",
            onClick = if (drawerState.isOpen) onDismissRequest else null,
            alpha = { calculateFraction(minValue, maxValue, drawerState.requireOffset()) },
            color = scrimColor,
        )
        Layout(
            content = drawerContent,
            modifier =
                Modifier.offset {
                    drawerState.currentOffset.let { offset ->
                        val offsetX =
                            when {
                                !offset.isNaN() -> offset.roundToInt()
                                // If offset is NaN, set offset based on open/closed state
                                drawerState.isOpen -> 0
                                else -> -DrawerDefaults.MaximumDrawerWidth.roundToPx()
                            }
                        IntOffset(offsetX, 0)
                    }
                }
                    .semantics {
                        paneTitle = navigationMenu
                        if (drawerState.isOpen) {
                            dismiss {
                                if (drawerState.confirmStateChange(DrawerValue.Closed)) {
                                    scope.launch { drawerState.close() }
                                }
                                true
                            }
                        }
                    }
                    .onKeyEvent {
                        // Drawer should close via escape key.
                        if (
                            drawerState.isOpen &&
                            it.type == KeyEventType.KeyUp &&
                            it.key == Key.Escape
                        ) {
                            scope.launch { drawerState.close() }
                            return@onKeyEvent true
                        }
                        return@onKeyEvent false
                    }
                    .focusRequester(focusRequester),
        ) { measurables, constraints ->
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.fastMap { it.measure(looseConstraints) }
            val width = placeables.fastMaxOfOrNull { it.width } ?: 0
            val height = placeables.fastMaxOfOrNull { it.height } ?: 0

            layout(width, height) {
                val currentClosedAnchor =
                    drawerState.anchoredDraggableState.anchors.positionOf(DrawerValue.Closed)
                val calculatedClosedAnchor = -width.toFloat()

                if (!anchorsInitialized || currentClosedAnchor != calculatedClosedAnchor) {
                    if (!anchorsInitialized) {
                        anchorsInitialized = true
                    }
                    minValue = calculatedClosedAnchor
                    drawerState.anchoredDraggableState.updateAnchors(
                        DraggableAnchors {
                            DrawerValue.Closed at minValue
                            DrawerValue.Open at maxValue
                        }
                    )
                }
                val isDrawerVisible =
                    calculateFraction(minValue, maxValue, drawerState.requireOffset()) != 0f
                if (isDrawerVisible) {
                    // Only place the drawer when it's visible so that keyboard focus doesn't
                    // navigate to an offscreen element.
                    placeables.fastForEach { it.placeRelative(0, 0) }
                }
            }
        }
    }
}

private fun calculateFraction(a: Float, b: Float, pos: Float) =
    ((pos - a) / (b - a)).coerceIn(0f, 1f)


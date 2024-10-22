package domain

import androidx.compose.runtime.Immutable

/** used for grouping VM.UiState changes into batches for history keeping */
@Immutable
enum class Command {
    MOVE,
    CHANGE_RADIUS, SCALE,
    ROTATE,
    DUPLICATE, DELETE,
    CREATE,
    FILL_REGION,
    /** records canvas translations and scaling */
//    CHANGE_POV,
    ;

    /** Used to distinguish/conflate [Command]s depending on their targets */
    sealed interface Tag {
        data class Targets(val targets: List<Ix> = emptyList()) : Tag
        class Unique : Tag {
            override fun equals(other: Any?): Boolean =
                false
            override fun hashCode(): Int {
                return this::class.hashCode()
            }
        }
    }
}
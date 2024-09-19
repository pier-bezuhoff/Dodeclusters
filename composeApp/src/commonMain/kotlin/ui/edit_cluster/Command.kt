package ui.edit_cluster

import domain.Ix

/** used for grouping UiState changes into batches for history keeping */
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
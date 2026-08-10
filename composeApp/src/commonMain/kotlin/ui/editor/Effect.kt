package ui.editor

import androidx.compose.runtime.Immutable
import domain.Ix

@Immutable
sealed interface Effect {
    /** Active whenever the selection is */
    @Immutable
    sealed interface SelectionBound : Effect

    @Immutable
    sealed interface Glow : Effect {
        val index: Ix

        data class Preimage(override val index: Ix) : Glow
        data class Parent(override val index: Ix) : Glow
    }
}
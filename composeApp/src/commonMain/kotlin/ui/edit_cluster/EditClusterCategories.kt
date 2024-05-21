package ui.edit_cluster

sealed interface Category {
    data object Multiselect : Category { // ~mode-like
        // toggle: select all/unselect all (active when everything is selected)

        // mode1: select-by-click
        // mode1: flow-select

        // mode2: xor selection logic
        // mode2: add selection logic
        // mode2: subtract selection logic
    }
    data object Region : Category { // ~mode-like
        // toggle: restrict regions to current selection toggle
        // button: chessboard pattern
        // button: erase all parts
        // buttons: [most used colors as a list (sorted by frequency & recency)]
    }
    data object Visibility : Category { // ~button-like
    }
}
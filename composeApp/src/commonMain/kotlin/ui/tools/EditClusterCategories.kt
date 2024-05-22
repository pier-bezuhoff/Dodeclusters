package ui.tools

sealed interface EditClusterCategory {
    // mode ~ toggle as their both their states are determined by a predicate & action is separated
    data object Multiselect : EditClusterCategory { // ~mode-like
        // toggle: select all/unselect all (active when everything is selected)

        // mode: select-by-click
        // mode: flow-select

        // mode2: xor selection logic
        // mode2: add selection logic
        // mode2: subtract selection logic
    }
    data object Region : EditClusterCategory { // ~mode-like
        // toggle: restrict regions to current selection toggle
        // button: chessboard pattern
        // button: erase all parts
        // buttons: [most used colors as a list (sorted by frequency & recency)]
    }
    data object Visibility : EditClusterCategory { // ~button-like
        // toggle: show/hide circle + mb only select ones
        // toggle: fill/unfill the cluster
        // toggle: show points (potentially)
    }
    data object Colors : EditClusterCategory { // ~button-like
        // buttons: [most used colors]
    }
    data object Attributes : EditClusterCategory { // ~button-like
        // button: copy
        // button: delete
    }
    data object Create : EditClusterCategory { // ~mode-like
        // mode: circle by center&radius
        // mode: circle by 3 points
        // mode: line by 2 points
        // mode: rectangle by top-left & bottom-right
        // mode: polygon
        // button: insert centered cross
    }
}